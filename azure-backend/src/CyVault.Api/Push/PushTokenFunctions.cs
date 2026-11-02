using System.Text.Json;
using Azure;
using Azure.Data.Tables;
using CyVault.Api.Auth;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Azure.Functions.Worker;
using Microsoft.Extensions.Logging;

namespace CyVault.Api.Push;

/// <summary>
/// The phones that receive the signed-in user's pushes. The app saves its token after each sign-in
/// and removes it on sign-out. Like the other routes, no user ID is in any route: the partition is
/// always the oid from the access token.
/// </summary>
public sealed class PushTokenFunctions(PushTokenTable pushTokens, ILogger<PushTokenFunctions> logger)
{
    // FCM tokens are about 160 characters; this leaves room without taking anything.
    private const int MaxTokenLength = 4096;

    /// <summary>
    /// Sends the caller's pushes to this phone. A token belongs to one app install: when another
    /// account used the phone before, its row for the token is removed, or that account's pushes
    /// would show here.
    /// </summary>
    [Function("PutPushToken")]
    public async Task<IActionResult> Put(
        [HttpTrigger(AuthorizationLevel.Anonymous, "put", Route = "me/push-tokens")] HttpRequest req,
        CancellationToken cancellationToken)
    {
        var caller = await CallerAuthentication.AuthenticateAsync(req.HttpContext, CallerAuthentication.ProfileScope);
        if (caller.Failure is not null) return caller.Failure;
        var (token, error) = await ReadTokenAsync(req, cancellationToken);
        if (error is not null) return error;

        var key = PushTokenEntity.KeyFor(token!);
        // Looks in every user's partition, which scans the table; the RowKey filter keeps it cheap.
        await foreach (var other in pushTokens.Client.QueryAsync<PushTokenEntity>(
            e => e.RowKey == key, cancellationToken: cancellationToken))
        {
            if (other.PartitionKey == caller.ObjectId) continue;
            await pushTokens.Client.DeleteEntityAsync(other.PartitionKey, other.RowKey, ETag.All, cancellationToken);
            logger.LogInformation("Moved a push token away from {ObjectId}", other.PartitionKey);
        }

        await pushTokens.Client.UpsertEntityAsync(new PushTokenEntity
        {
            PartitionKey = caller.ObjectId!,
            RowKey = key,
            Token = token!,
            UpdatedAt = DateTimeOffset.UtcNow,
        }, TableUpdateMode.Replace, cancellationToken);
        return new NoContentResult();
    }

    /// <summary>Stops the caller's pushes to this phone, when the user signs out on it.</summary>
    [Function("DeletePushToken")]
    public async Task<IActionResult> Delete(
        [HttpTrigger(AuthorizationLevel.Anonymous, "delete", Route = "me/push-tokens")] HttpRequest req,
        CancellationToken cancellationToken)
    {
        var caller = await CallerAuthentication.AuthenticateAsync(req.HttpContext, CallerAuthentication.ProfileScope);
        if (caller.Failure is not null) return caller.Failure;
        var (token, error) = await ReadTokenAsync(req, cancellationToken);
        if (error is not null) return error;

        // Removing a token that isn't there is still a success.
        await pushTokens.Client.DeleteEntityAsync(
            caller.ObjectId!, PushTokenEntity.KeyFor(token!), ETag.All, cancellationToken);
        return new NoContentResult();
    }

    private static async Task<(string? Token, IActionResult? Error)> ReadTokenAsync(
        HttpRequest req, CancellationToken cancellationToken)
    {
        PushTokenRequest? body;
        try
        {
            body = await JsonSerializer.DeserializeAsync<PushTokenRequest>(
                req.Body, JsonSerializerOptions.Web, cancellationToken);
        }
        catch (JsonException)
        {
            return (null, new BadRequestObjectResult("The body must be JSON: { \"token\" }."));
        }

        var token = body?.Token?.Trim();
        if (string.IsNullOrEmpty(token) || token.Length > MaxTokenLength)
            return (null, new BadRequestObjectResult($"token must be 1 to {MaxTokenLength} characters."));
        return (token, null);
    }
}
