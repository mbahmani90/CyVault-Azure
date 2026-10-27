using Azure;
using Azure.Storage.Blobs;
using Azure.Storage.Blobs.Models;
using CyVault.Api.Auth;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Azure.Functions.Worker;
using Microsoft.Extensions.Logging;
using Microsoft.Net.Http.Headers;

namespace CyVault.Api.Profile;

/// <summary>
/// The signed-in user's profile photo. There is no user ID in any route: the blob is always
/// avatars/{oid}/avatar.jpg, with oid taken from the validated access token, so a caller can
/// only ever reach their own photo.
/// </summary>
public sealed class AvatarFunctions(BlobContainerClient avatars, ILogger<AvatarFunctions> logger)
{
    private const long MaxUploadBytes = 5 * 1024 * 1024;

    [Function("PutAvatar")]
    public async Task<IActionResult> Put(
        [HttpTrigger(AuthorizationLevel.Anonymous, "put", Route = "me/avatar")] HttpRequest req,
        CancellationToken cancellationToken)
    {
        var caller = await CallerAuthentication.AuthenticateAsync(req.HttpContext, CallerAuthentication.ProfileScope);
        if (caller.Failure is not null) return caller.Failure;

        if (req.ContentLength > MaxUploadBytes) return TooLarge();
        var body = await ReadBodyAsync(req.Body, MaxUploadBytes, cancellationToken);
        if (body is null) return TooLarge();

        // Whatever name or type the client claims is ignored: the bytes must decode as an image.
        var jpeg = AvatarImage.Normalize(body);
        if (jpeg is null)
        {
            return new ObjectResult("The body must be an image (JPEG, PNG, WebP, ...).")
            {
                StatusCode = StatusCodes.Status415UnsupportedMediaType,
            };
        }

        await BlobFor(caller.ObjectId!).UploadAsync(
            BinaryData.FromBytes(jpeg),
            new BlobUploadOptions { HttpHeaders = new BlobHttpHeaders { ContentType = "image/jpeg" } },
            cancellationToken);

        logger.LogInformation("Stored avatar ({Bytes} bytes) for {ObjectId}", jpeg.Length, caller.ObjectId);
        return new NoContentResult();
    }

    [Function("GetAvatar")]
    public async Task<IActionResult> Get(
        [HttpTrigger(AuthorizationLevel.Anonymous, "get", Route = "me/avatar")] HttpRequest req,
        CancellationToken cancellationToken)
    {
        var caller = await CallerAuthentication.AuthenticateAsync(req.HttpContext, CallerAuthentication.ProfileScope);
        if (caller.Failure is not null) return caller.Failure;

        try
        {
            var download = await BlobFor(caller.ObjectId!).DownloadContentAsync(cancellationToken);
            // The photo is personal: shared caches must not keep it.
            req.HttpContext.Response.Headers.CacheControl = "private, no-cache";
            return new FileContentResult(download.Value.Content.ToArray(), "image/jpeg")
            {
                EntityTag = EntityTagHeaderValue.Parse(download.Value.Details.ETag.ToString("H")),
            };
        }
        catch (RequestFailedException e) when (e.Status == StatusCodes.Status404NotFound)
        {
            return new NotFoundResult();
        }
    }

    [Function("DeleteAvatar")]
    public async Task<IActionResult> Delete(
        [HttpTrigger(AuthorizationLevel.Anonymous, "delete", Route = "me/avatar")] HttpRequest req,
        CancellationToken cancellationToken)
    {
        var caller = await CallerAuthentication.AuthenticateAsync(req.HttpContext, CallerAuthentication.ProfileScope);
        if (caller.Failure is not null) return caller.Failure;

        await BlobFor(caller.ObjectId!).DeleteIfExistsAsync(cancellationToken: cancellationToken);
        return new NoContentResult();
    }

    private BlobClient BlobFor(string objectId) => avatars.GetBlobClient($"{objectId}/avatar.jpg");

    private static ObjectResult TooLarge() =>
        new($"The image must be at most {MaxUploadBytes / (1024 * 1024)} MB.") { StatusCode = StatusCodes.Status413PayloadTooLarge };

    /// <returns>The body, or null when it is larger than <paramref name="maxBytes"/>.</returns>
    private static async Task<byte[]?> ReadBodyAsync(Stream body, long maxBytes, CancellationToken cancellationToken)
    {
        using var buffer = new MemoryStream();
        var chunk = new byte[81920];
        int read;
        while ((read = await body.ReadAsync(chunk, cancellationToken)) > 0)
        {
            if (buffer.Length + read > maxBytes) return null;
            buffer.Write(chunk, 0, read);
        }
        return buffer.ToArray();
    }
}
