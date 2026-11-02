using System.Buffers.Text;
using System.Globalization;
using System.Security.Cryptography;
using System.Text;
using Azure;
using Azure.Data.Tables;
using CyVault.Api.Device;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;

namespace CyVault.Api.Transfer;

/// <summary>
/// Strong customer authentication for transfers: the server issues a one-time challenge for the
/// exact transfer, the phone's device key signs it after a biometric check (possession and
/// inherence), and the transfer goes ahead only when the enrolled device's public key verifies
/// that signature over those same details.
/// </summary>
public sealed class TransferSigning(ChallengeTable challenges, DeviceTable devices)
{
    /// <summary>Long enough to read the amount and touch the sensor; the RTS allows at most 5 minutes.</summary>
    public static readonly TimeSpan ChallengeLifetime = TimeSpan.FromMinutes(2);

    public const int MaxFailedAttempts = 5;

    public static readonly TimeSpan LockDuration = TimeSpan.FromMinutes(30);

    /// <summary>Issues a challenge for these details and returns the text the device must sign.</summary>
    public async Task<TransferChallengeResponse> IssueAsync(
        string senderOid, TransferDetails details, CancellationToken cancellationToken)
    {
        var nonce = Base64Url.EncodeToString(RandomNumberGenerator.GetBytes(32));
        var challenge = new TransferChallengeEntity
        {
            PartitionKey = senderOid,
            RowKey = nonce,
            TransferId = details.TransferId,
            CardId = details.CardId,
            PhoneNumber = details.PhoneNumber,
            Amount = details.Amount.ToString(CultureInfo.InvariantCulture),
            Currency = details.Currency,
            Payload = Payload(nonce, details),
            ExpiresAt = DateTimeOffset.UtcNow + ChallengeLifetime,
        };
        await challenges.Client.AddEntityAsync(challenge, cancellationToken);
        return new TransferChallengeResponse(nonce, challenge.Payload, challenge.ExpiresAt);
    }

    /// <summary>The response to send while signing is blocked for the caller, or null.</summary>
    public async Task<IActionResult?> LockedAsync(string senderOid, CancellationToken cancellationToken)
    {
        var row = await challenges.Client.GetEntityIfExistsAsync<SigningLockEntity>(
            senderOid, SigningLockEntity.Key, cancellationToken: cancellationToken);
        return row.HasValue && row.Value!.LockedUntil > DateTimeOffset.UtcNow ? Locked(row.Value.LockedUntil.Value) : null;
    }

    /// <summary>
    /// Checks that the caller's enrolled device signed a challenge issued for exactly these details,
    /// and marks the challenge used. Returns the response to send when it didn't, or null.
    /// </summary>
    public async Task<IActionResult?> VerifyAsync(
        string senderOid,
        TransferDetails details,
        string? deviceId,
        string? nonce,
        string? signature,
        CancellationToken cancellationToken)
    {
        if (!Guid.TryParse(deviceId, out _) || string.IsNullOrEmpty(nonce) || nonce.Length > 64 || string.IsNullOrEmpty(signature))
            return new BadRequestObjectResult("deviceId, nonce and signature are required: sign the challenge from POST /me/transfers/challenge.");

        if (await LockedAsync(senderOid, cancellationToken) is { } locked) return locked;

        var device = await devices.Client.GetEntityIfExistsAsync<DeviceEntity>(
            senderOid, deviceId!, cancellationToken: cancellationToken);
        if (!device.HasValue || device.Value!.RevokedAt is not null)
            return new ObjectResult("This device isn't enrolled, or was removed.") { StatusCode = StatusCodes.Status403Forbidden };

        var found = await challenges.Client.GetEntityIfExistsAsync<TransferChallengeEntity>(
            senderOid, nonce, cancellationToken: cancellationToken);
        if (!found.HasValue) return Gone("This challenge doesn't exist. Ask for a new one.");
        var challenge = found.Value!;
        // Dynamic linking: the signature only approves the details the challenge was issued for.
        if (!Matches(challenge, details))
            return new BadRequestObjectResult("The transfer doesn't match the challenge that was signed.");
        if (challenge.UsedAt is null && challenge.ExpiresAt <= DateTimeOffset.UtcNow)
            return Gone("This challenge expired. Ask for a new one.");

        if (!IsValidSignature(device.Value.PublicKey, challenge.Payload, signature!))
            return await RecordFailureAsync(senderOid, cancellationToken);
        await ClearFailuresAsync(senderOid, cancellationToken);

        if (challenge.UsedAt is null)
        {
            challenge.UsedAt = DateTimeOffset.UtcNow;
            try
            {
                await challenges.Client.UpdateEntityAsync(challenge, challenge.ETag, TableUpdateMode.Merge, cancellationToken);
            }
            catch (RequestFailedException e) when (e.Status == StatusCodes.Status412PreconditionFailed)
            {
                // A concurrent request used it first. It can only have been for this same transfer,
                // which the transfers table records once, so carrying on is safe.
            }
        }
        return null;
    }

    /// <summary>The text the device signs. Both sides use the server's copy, so it's never rebuilt.</summary>
    internal static string Payload(string nonce, TransferDetails details) => string.Join('\n',
        "CyVault transfer v1",
        $"nonce:{nonce}",
        $"transferId:{details.TransferId}",
        $"cardId:{details.CardId}",
        $"to:{details.PhoneNumber}",
        $"amount:{details.Amount.ToString(CultureInfo.InvariantCulture)}",
        $"currency:{details.Currency}");

    /// <summary>
    /// True when <paramref name="signature"/> (base64 DER, as the Android Keystore's SHA256withECDSA
    /// and iOS's ecdsaSignatureMessageX962SHA256 make it) signs the payload's UTF-8 bytes.
    /// </summary>
    internal static bool IsValidSignature(string publicKey, string payload, string signature)
    {
        try
        {
            using var key = ECDsa.Create();
            key.ImportSubjectPublicKeyInfo(Convert.FromBase64String(publicKey), out _);
            return key.VerifyData(
                Encoding.UTF8.GetBytes(payload),
                Convert.FromBase64String(signature),
                HashAlgorithmName.SHA256,
                DSASignatureFormat.Rfc3279DerSequence);
        }
        catch (Exception e) when (e is FormatException or CryptographicException)
        {
            return false;
        }
    }

    private static bool Matches(TransferChallengeEntity challenge, TransferDetails details) =>
        challenge.TransferId == details.TransferId
        && challenge.CardId == details.CardId
        && challenge.PhoneNumber == details.PhoneNumber
        && decimal.Parse(challenge.Amount, CultureInfo.InvariantCulture) == details.Amount
        && challenge.Currency == details.Currency;

    private async Task<IActionResult> RecordFailureAsync(string senderOid, CancellationToken cancellationToken)
    {
        var existing = await challenges.Client.GetEntityIfExistsAsync<SigningLockEntity>(
            senderOid, SigningLockEntity.Key, cancellationToken: cancellationToken);
        var row = existing.HasValue ? existing.Value! : new SigningLockEntity { PartitionKey = senderOid };

        row.FailedAttempts++;
        if (row.FailedAttempts >= MaxFailedAttempts)
        {
            // The count starts again once the block ends.
            row.FailedAttempts = 0;
            row.LockedUntil = DateTimeOffset.UtcNow + LockDuration;
        }
        await challenges.Client.UpsertEntityAsync(row, TableUpdateMode.Replace, cancellationToken);

        if (row.LockedUntil > DateTimeOffset.UtcNow) return Locked(row.LockedUntil.Value);
        return new UnprocessableEntityObjectResult(
            $"The signature doesn't match. Tries left before signing is blocked: {MaxFailedAttempts - row.FailedAttempts}.");
    }

    private async Task ClearFailuresAsync(string senderOid, CancellationToken cancellationToken)
    {
        var existing = await challenges.Client.GetEntityIfExistsAsync<SigningLockEntity>(
            senderOid, SigningLockEntity.Key, cancellationToken: cancellationToken);
        if (!existing.HasValue || existing.Value!.FailedAttempts == 0) return;

        try
        {
            await challenges.Client.DeleteEntityAsync(senderOid, SigningLockEntity.Key, existing.Value.ETag, cancellationToken);
        }
        catch (RequestFailedException e) when (e.Status is StatusCodes.Status404NotFound or StatusCodes.Status412PreconditionFailed)
        {
            // Changed meanwhile: the next failure or success sorts it out.
        }
    }

    private static ObjectResult Gone(string message) => new(message) { StatusCode = StatusCodes.Status410Gone };

    private static ObjectResult Locked(DateTimeOffset until) =>
        new($"Too many failed signatures. Signing is blocked until {until.ToString("u", CultureInfo.InvariantCulture)}.")
        {
            StatusCode = StatusCodes.Status423Locked,
        };
}
