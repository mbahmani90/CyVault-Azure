using System.Globalization;
using System.Text.Json;
using System.Text.RegularExpressions;
using Azure;
using Azure.Data.Tables;
using CyVault.Api.Auth;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Azure.Functions.Worker;
using Microsoft.Extensions.Logging;

namespace CyVault.Api.Balance;

/// <summary>
/// The signed-in user's balances. Like the avatar routes, no user ID is in any route: the
/// partition is always the oid from the validated access token, so a caller can only ever read
/// or change their own rows.
/// </summary>
public sealed partial class BalanceFunctions(TableClient balances, ILogger<BalanceFunctions> logger)
{
    private const int MaxNameLength = 100;

    [Function("ListBalances")]
    public async Task<IActionResult> List(
        [HttpTrigger(AuthorizationLevel.Anonymous, "get", Route = "me/balances")] HttpRequest req,
        CancellationToken cancellationToken)
    {
        var caller = await CallerAuthentication.AuthenticateAsync(req.HttpContext, CallerAuthentication.BalanceScope);
        if (caller.Failure is not null) return caller.Failure;

        var result = new List<BalanceResponse>();
        await foreach (var entity in balances.QueryAsync<BalanceEntity>(
            e => e.PartitionKey == caller.ObjectId, cancellationToken: cancellationToken))
        {
            result.Add(ToResponse(entity));
        }
        return new OkObjectResult(result);
    }

    [Function("GetBalance")]
    public async Task<IActionResult> Get(
        [HttpTrigger(AuthorizationLevel.Anonymous, "get", Route = "me/balances/{id}")] HttpRequest req,
        string id,
        CancellationToken cancellationToken)
    {
        var caller = await CallerAuthentication.AuthenticateAsync(req.HttpContext, CallerAuthentication.BalanceScope);
        if (caller.Failure is not null) return caller.Failure;
        if (!IsValidId(id)) return InvalidId();

        var entity = await balances.GetEntityIfExistsAsync<BalanceEntity>(
            caller.ObjectId!, id, cancellationToken: cancellationToken);
        return entity.HasValue ? new OkObjectResult(ToResponse(entity.Value!)) : new NotFoundResult();
    }

    /// <summary>Creates the balance, or replaces it when the ID already exists.</summary>
    [Function("PutBalance")]
    public async Task<IActionResult> Put(
        [HttpTrigger(AuthorizationLevel.Anonymous, "put", Route = "me/balances/{id}")] HttpRequest req,
        string id,
        CancellationToken cancellationToken)
    {
        var caller = await CallerAuthentication.AuthenticateAsync(req.HttpContext, CallerAuthentication.BalanceScope);
        if (caller.Failure is not null) return caller.Failure;
        if (!IsValidId(id)) return InvalidId();

        BalanceRequest? body;
        try
        {
            body = await JsonSerializer.DeserializeAsync<BalanceRequest>(
                req.Body, JsonSerializerOptions.Web, cancellationToken);
        }
        catch (JsonException)
        {
            return new BadRequestObjectResult("The body must be JSON: { \"name\", \"amount\", \"currency\" }.");
        }

        var error = Validate(body);
        if (error is not null) return new BadRequestObjectResult(error);

        var entity = new BalanceEntity
        {
            PartitionKey = caller.ObjectId!,
            RowKey = id,
            Name = body!.Name!.Trim(),
            Amount = body.Amount!.Value.ToString(CultureInfo.InvariantCulture),
            Currency = body.Currency!.ToUpperInvariant(),
        };
        await balances.UpsertEntityAsync(entity, TableUpdateMode.Replace, cancellationToken);

        // Read it back for the server's Timestamp.
        var saved = await balances.GetEntityAsync<BalanceEntity>(entity.PartitionKey, entity.RowKey, cancellationToken: cancellationToken);
        logger.LogInformation("Stored balance {Id} for {ObjectId}", id, caller.ObjectId);
        return new OkObjectResult(ToResponse(saved.Value));
    }

    [Function("DeleteBalance")]
    public async Task<IActionResult> Delete(
        [HttpTrigger(AuthorizationLevel.Anonymous, "delete", Route = "me/balances/{id}")] HttpRequest req,
        string id,
        CancellationToken cancellationToken)
    {
        var caller = await CallerAuthentication.AuthenticateAsync(req.HttpContext, CallerAuthentication.BalanceScope);
        if (caller.Failure is not null) return caller.Failure;
        if (!IsValidId(id)) return InvalidId();

        // Deleting a row that isn't there is still a success: the result is the same.
        await balances.DeleteEntityAsync(caller.ObjectId!, id, ETag.All, cancellationToken);
        return new NoContentResult();
    }

    private static BalanceResponse ToResponse(BalanceEntity entity) => new(
        entity.RowKey,
        entity.Name,
        decimal.Parse(entity.Amount, CultureInfo.InvariantCulture),
        entity.Currency,
        entity.Timestamp);

    private static string? Validate(BalanceRequest? body)
    {
        if (body is null) return "The body must be JSON: { \"name\", \"amount\", \"currency\" }.";
        if (string.IsNullOrWhiteSpace(body.Name) || body.Name.Trim().Length > MaxNameLength)
            return $"name is required and must be at most {MaxNameLength} characters.";
        if (body.Amount is null) return "amount is required.";
        if (body.Currency is null || !CurrencyPattern().IsMatch(body.Currency))
            return "currency must be a three-letter ISO 4217 code, such as EUR.";
        return null;
    }

    // RowKey can't contain / \ # ? or control characters; a narrow pattern keeps IDs safe and simple.
    private static bool IsValidId(string id) => IdPattern().IsMatch(id);

    private static BadRequestObjectResult InvalidId() =>
        new("The balance ID must be 1-64 letters, digits, '-' or '_'.");

    [GeneratedRegex("^[A-Za-z0-9_-]{1,64}$")]
    private static partial Regex IdPattern();

    [GeneratedRegex("^[A-Za-z]{3}$")]
    private static partial Regex CurrencyPattern();
}
