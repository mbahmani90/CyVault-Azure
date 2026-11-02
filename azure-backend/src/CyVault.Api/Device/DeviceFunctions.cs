using System.Security.Cryptography;
using System.Text.Json;
using Azure;
using Azure.Data.Tables;
using CyVault.Api.Auth;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Azure.Functions.Worker;
using Microsoft.Extensions.Logging;

namespace CyVault.Api.Device;

/// <summary>
/// The signed-in user's enrolled phones: each holds a private key in its Keystore or Secure
/// Enclave, and the server keeps the matching public key to check the transfers it signs. Like the
/// other routes, no user ID is in any route: the partition is always the oid from the access token.
/// </summary>
public sealed class DeviceFunctions(
    DeviceTable devices,
    ILogger<DeviceFunctions> logger)
{
    private const int MaxDeviceNameLength = 50;

    // A P-256 SubjectPublicKeyInfo is 91 bytes (124 base64 characters); anything far longer isn't one.
    private const int MaxPublicKeyLength = 1024;

    private const string NistP256Oid = "1.2.840.10045.3.1.7";

    // FCM tokens are about 160 characters; this leaves room without taking anything.
    private const int MaxPushTokenLength = 4096;

    [Function("ListDevices")]
    public async Task<IActionResult> List(
        [HttpTrigger(AuthorizationLevel.Anonymous, "get", Route = "me/devices")] HttpRequest req,
        CancellationToken cancellationToken)
    {
        var caller = await CallerAuthentication.AuthenticateAsync(req.HttpContext, CallerAuthentication.ProfileScope);
        if (caller.Failure is not null) return caller.Failure;

        var result = new List<DeviceResponse>();
        await foreach (var entity in devices.Client.QueryAsync<DeviceEntity>(
            e => e.PartitionKey == caller.ObjectId, cancellationToken: cancellationToken))
        {
            if (entity.RevokedAt is null) result.Add(ToResponse(entity));
        }
        // Newest first; the ID breaks ties so the order is stable.
        return new OkObjectResult(result
            .OrderByDescending(d => d.CreatedAt)
            .ThenBy(d => d.Id, StringComparer.Ordinal)
            .ToList());
    }

    /// <summary>
    /// Enrolls the phone that holds the private key for this public key, and returns its device ID.
    /// The server picks the ID; a retry with the same key returns the same device instead of
    /// enrolling it twice.
    /// </summary>
    [Function("PostDevice")]
    public async Task<IActionResult> Post(
        [HttpTrigger(AuthorizationLevel.Anonymous, "post", Route = "me/devices")] HttpRequest req,
        CancellationToken cancellationToken)
    {
        var caller = await CallerAuthentication.AuthenticateAsync(req.HttpContext, CallerAuthentication.ProfileScope);
        if (caller.Failure is not null) return caller.Failure;

        DeviceRequest? body;
        try
        {
            body = await JsonSerializer.DeserializeAsync<DeviceRequest>(
                req.Body, JsonSerializerOptions.Web, cancellationToken);
        }
        catch (JsonException)
        {
            return new BadRequestObjectResult("The body must be JSON: { \"publicKey\", \"deviceName\" }.");
        }

        var publicKey = CanonicalP256PublicKey(body?.PublicKey);
        if (publicKey is null)
            return new BadRequestObjectResult("publicKey must be an EC P-256 public key as base64 DER SubjectPublicKeyInfo.");
        var deviceName = body?.DeviceName?.Trim() ?? "";
        if (deviceName.Length > MaxDeviceNameLength)
            return new BadRequestObjectResult($"deviceName must be at most {MaxDeviceNameLength} characters.");

        // TODO: a stolen password alone can enroll a phone until a second check (an SMS or email
        // code) is required here, before the key is stored.

        await foreach (var existing in devices.Client.QueryAsync<DeviceEntity>(
            e => e.PartitionKey == caller.ObjectId && e.PublicKey == publicKey, cancellationToken: cancellationToken))
        {
            // A removed device stays removed: the app must make a new key to enroll again.
            if (existing.RevokedAt is not null)
                return new ConflictObjectResult("This key belongs to a removed device. Create a new key.");
            return new OkObjectResult(ToResponse(existing));
        }

        var entity = new DeviceEntity
        {
            PartitionKey = caller.ObjectId!,
            RowKey = Guid.NewGuid().ToString(),
            PublicKey = publicKey,
            Algorithm = DeviceKeyAlgorithm.Es256,
            DeviceName = deviceName,
            CreatedAt = DateTimeOffset.UtcNow,
        };
        await devices.Client.AddEntityAsync(entity, cancellationToken);

        logger.LogInformation("Enrolled device {Id} for {ObjectId}", entity.RowKey, caller.ObjectId);
        return new ObjectResult(ToResponse(entity)) { StatusCode = StatusCodes.Status201Created };
    }

    /// <summary>
    /// Removes the device: it can't sign anything after this. The row is kept with its removal
    /// time, so a signature it made earlier can still be checked.
    /// </summary>
    [Function("DeleteDevice")]
    public async Task<IActionResult> Delete(
        [HttpTrigger(AuthorizationLevel.Anonymous, "delete", Route = "me/devices/{id}")] HttpRequest req,
        string id,
        CancellationToken cancellationToken)
    {
        var caller = await CallerAuthentication.AuthenticateAsync(req.HttpContext, CallerAuthentication.ProfileScope);
        if (caller.Failure is not null) return caller.Failure;
        if (!Guid.TryParse(id, out _)) return new BadRequestObjectResult("The device ID must be a GUID.");

        // Removing a device that isn't there, or is already removed, is still a success.
        var existing = await devices.Client.GetEntityIfExistsAsync<DeviceEntity>(
            caller.ObjectId!, id, cancellationToken: cancellationToken);
        if (!existing.HasValue || existing.Value!.RevokedAt is not null) return new NoContentResult();

        var entity = existing.Value;
        entity.RevokedAt = DateTimeOffset.UtcNow;
        try
        {
            await devices.Client.UpdateEntityAsync(entity, entity.ETag, cancellationToken: cancellationToken);
        }
        catch (RequestFailedException e) when (e.Status is StatusCodes.Status404NotFound or StatusCodes.Status412PreconditionFailed)
        {
            // Changed or removed by another request since it was read: that one already did the work.
        }

        logger.LogInformation("Removed device {Id} for {ObjectId}", id, caller.ObjectId);
        return new NoContentResult();
    }

    /// <summary>
    /// Saves the phone's FCM token on the caller's device, so pushes reach it. The app sends it
    /// after enrolling and whenever Firebase gives it a new one.
    /// </summary>
    [Function("PutDevicePushToken")]
    public async Task<IActionResult> PutPushToken(
        [HttpTrigger(AuthorizationLevel.Anonymous, "put", Route = "me/devices/{id}/push-token")] HttpRequest req,
        string id,
        CancellationToken cancellationToken)
    {
        var caller = await CallerAuthentication.AuthenticateAsync(req.HttpContext, CallerAuthentication.ProfileScope);
        if (caller.Failure is not null) return caller.Failure;
        if (!Guid.TryParse(id, out _)) return new BadRequestObjectResult("The device ID must be a GUID.");

        PushTokenRequest? body;
        try
        {
            body = await JsonSerializer.DeserializeAsync<PushTokenRequest>(
                req.Body, JsonSerializerOptions.Web, cancellationToken);
        }
        catch (JsonException)
        {
            return new BadRequestObjectResult("The body must be JSON: { \"token\" }.");
        }
        var token = body?.Token?.Trim();
        if (string.IsNullOrEmpty(token) || token.Length > MaxPushTokenLength)
            return new BadRequestObjectResult($"token must be 1 to {MaxPushTokenLength} characters.");

        var existing = await devices.Client.GetEntityIfExistsAsync<DeviceEntity>(
            caller.ObjectId!, id, cancellationToken: cancellationToken);
        if (!existing.HasValue || existing.Value!.RevokedAt is not null)
            return new NotFoundObjectResult("No enrolled device has this ID.");

        // A token belongs to one app install. When another account used this phone before, its
        // device still holds the token: clear it there, or that account's pushes would show here.
        await ClearPushTokenAsync(token, keep: (caller.ObjectId!, id), cancellationToken);

        var entity = existing.Value;
        if (entity.PushToken != token)
        {
            entity.PushToken = token;
            await devices.Client.UpdateEntityAsync(entity, ETag.All, TableUpdateMode.Merge, cancellationToken);
        }
        return new NoContentResult();
    }

    /// <summary>Stops pushes to the caller's device, for example when the user signs out on it.</summary>
    [Function("DeleteDevicePushToken")]
    public async Task<IActionResult> DeletePushToken(
        [HttpTrigger(AuthorizationLevel.Anonymous, "delete", Route = "me/devices/{id}/push-token")] HttpRequest req,
        string id,
        CancellationToken cancellationToken)
    {
        var caller = await CallerAuthentication.AuthenticateAsync(req.HttpContext, CallerAuthentication.ProfileScope);
        if (caller.Failure is not null) return caller.Failure;
        if (!Guid.TryParse(id, out _)) return new BadRequestObjectResult("The device ID must be a GUID.");

        var existing = await devices.Client.GetEntityIfExistsAsync<DeviceEntity>(
            caller.ObjectId!, id, cancellationToken: cancellationToken);
        if (existing.HasValue && existing.Value!.PushToken.Length > 0)
        {
            var entity = existing.Value;
            entity.PushToken = "";
            await devices.Client.UpdateEntityAsync(entity, ETag.All, TableUpdateMode.Merge, cancellationToken);
        }
        return new NoContentResult();
    }

    // Looks in every user's partition, which scans the table; it runs only when a token is saved.
    private async Task ClearPushTokenAsync(
        string token, (string Oid, string Id) keep, CancellationToken cancellationToken)
    {
        await foreach (var other in devices.Client.QueryAsync<DeviceEntity>(
            e => e.PushToken == token, cancellationToken: cancellationToken))
        {
            if (other.PartitionKey == keep.Oid && other.RowKey == keep.Id) continue;
            other.PushToken = "";
            await devices.Client.UpdateEntityAsync(other, ETag.All, TableUpdateMode.Merge, cancellationToken);
            logger.LogInformation("Moved a push token away from device {Id}", other.RowKey);
        }
    }

    private static DeviceResponse ToResponse(DeviceEntity entity) =>
        new(entity.RowKey, entity.Algorithm, entity.DeviceName, entity.CreatedAt);

    /// <summary>
    /// The key re-encoded as base64 DER SubjectPublicKeyInfo, or null when it isn't an EC P-256
    /// public key. Re-encoding gives one form per key, so the retry check compares like with like.
    /// </summary>
    internal static string? CanonicalP256PublicKey(string? base64)
    {
        if (string.IsNullOrWhiteSpace(base64) || base64.Length > MaxPublicKeyLength) return null;

        byte[] der;
        try
        {
            der = Convert.FromBase64String(base64.Trim());
        }
        catch (FormatException)
        {
            return null;
        }

        using var key = ECDsa.Create();
        try
        {
            key.ImportSubjectPublicKeyInfo(der, out var bytesRead);
            if (bytesRead != der.Length) return null;
            // Linux gives the curve's OID; Windows (local runs) may give only its friendly name.
            var curve = key.ExportParameters(false).Curve.Oid;
            if (curve.Value != NistP256Oid && curve.FriendlyName is not ("nistP256" or "ECDSA_P256")) return null;
        }
        catch (CryptographicException)
        {
            return null;
        }

        return Convert.ToBase64String(key.ExportSubjectPublicKeyInfo());
    }
}
