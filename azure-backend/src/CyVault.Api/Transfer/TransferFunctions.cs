using System.Globalization;
using System.Text.Json;
using System.Text.RegularExpressions;
using Azure;
using Azure.Data.Tables;
using CyVault.Api.Auth;
using CyVault.Api.Balance;
using CyVault.Api.Card;
using CyVault.Api.Push;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Azure.Functions.Worker;
using Microsoft.Extensions.Logging;

namespace CyVault.Api.Transfer;

/// <summary>
/// Sends money from one of the caller's cards to the card linked to a phone number, MB WAY style.
/// It writes a Transfer row in the caller's balances and a Receive row in the receiver's: the one
/// place where a request writes another user's partition. The server picks everything about that
/// row; the caller only names a phone number.
/// </summary>
public sealed partial class TransferFunctions(
    TransferTable transfers,
    TableClient balances,
    CardTables cardTables,
    TransferSigning signing,
    PushNotifications push,
    ILogger<TransferFunctions> logger)
{
    private const string BodyShape =
        "The body must be JSON: { \"transferId\", \"cardId\", \"phoneNumber\", \"amount\", \"currency\" }, " +
        "plus \"deviceId\", \"nonce\" and \"signature\" for the transfer itself.";

    /// <summary>
    /// Issues the one-time challenge the app's device key signs to approve this exact transfer.
    /// The body is the transfer's: the same checks run now, so a transfer that can't go through
    /// fails before the user is asked for a biometric.
    /// </summary>
    [Function("PostTransferChallenge")]
    public async Task<IActionResult> PostChallenge(
        [HttpTrigger(AuthorizationLevel.Anonymous, "post", Route = "me/transfers/challenge")] HttpRequest req,
        CancellationToken cancellationToken)
    {
        var caller = await CallerAuthentication.AuthenticateAsync(req.HttpContext, CallerAuthentication.BalanceScope);
        if (caller.Failure is not null) return caller.Failure;

        var (body, bodyError) = await ReadAsync(req, cancellationToken);
        if (bodyError is not null) return bodyError;
        var (resolved, error) = await ResolveAsync(caller.ObjectId!, body!, cancellationToken);
        if (error is not null) return error;
        if (await signing.LockedAsync(caller.ObjectId!, cancellationToken) is { } locked) return locked;

        return new OkObjectResult(await signing.IssueAsync(caller.ObjectId!, resolved!.Details, cancellationToken));
    }

    /// <summary>
    /// Returns the caller's Transfer row. The app picks the transfer ID once per Send, so a retry
    /// after a lost response finishes or repeats the same transfer instead of sending twice.
    /// </summary>
    [Function("PostTransfer")]
    public async Task<IActionResult> Post(
        [HttpTrigger(AuthorizationLevel.Anonymous, "post", Route = "me/transfers")] HttpRequest req,
        CancellationToken cancellationToken)
    {
        var caller = await CallerAuthentication.AuthenticateAsync(req.HttpContext, CallerAuthentication.BalanceScope);
        if (caller.Failure is not null) return caller.Failure;
        var senderOid = caller.ObjectId!;

        var (body, bodyError) = await ReadAsync(req, cancellationToken);
        if (bodyError is not null) return bodyError;
        var (resolved, error) = await ResolveAsync(senderOid, body!, cancellationToken);
        if (error is not null) return error;
        var (details, senderCard, link) = resolved!;

        // Strong customer authentication: no money moves without the device's signature.
        var signingError = await signing.VerifyAsync(
            senderOid, details, body!.DeviceId, body.Nonce, body.Signature, cancellationToken);
        if (signingError is not null) return signingError;

        var transfer = await RecordAsync(new TransferEntity
        {
            PartitionKey = senderOid,
            RowKey = details.TransferId,
            CardId = details.CardId,
            PhoneNumber = details.PhoneNumber,
            ReceiverOid = link.Oid,
            ReceiverCardId = link.CardId,
            Amount = details.Amount.ToString(CultureInfo.InvariantCulture),
            Currency = details.Currency,
            DeviceId = body.DeviceId!,
            Nonce = body.Nonce!,
            Signature = body.Signature!,
            Status = TransferStatus.Pending,
            CreatedAt = DateTimeOffset.UtcNow,
        }, cancellationToken);
        if (transfer is null)
            return new ConflictObjectResult("This transferId was already used for a different transfer.");

        var (sent, received) = Rows(transfer, senderPhone: senderCard.PhoneNumber);
        // Already finished: return what it wrote, without bringing back a row the user deleted.
        if (transfer.Status == TransferStatus.Completed) return new OkObjectResult(BalanceFunctions.ToResponse(sent));

        // Upserts with fixed RowKeys, so finishing a Pending transfer again writes the same rows.
        await balances.UpsertEntityAsync(sent, TableUpdateMode.Replace, cancellationToken);
        await balances.UpsertEntityAsync(received, TableUpdateMode.Replace, cancellationToken);
        transfer.Status = TransferStatus.Completed;
        await transfers.Client.UpdateEntityAsync(transfer, ETag.All, TableUpdateMode.Merge, cancellationToken);

        logger.LogInformation(
            "Transfer {Id} from {Sender} to {Receiver} completed", transfer.RowKey, senderOid, transfer.ReceiverOid);

        // Only when the money first moves: a retry of a finished transfer returned above.
        await push.TransferReceivedAsync(
            transfer.ReceiverOid, transfer.RowKey, transfer.Amount, transfer.Currency, senderCard.PhoneNumber,
            cancellationToken);
        var saved = await balances.GetEntityAsync<BalanceEntity>(sent.PartitionKey, sent.RowKey, cancellationToken: cancellationToken);
        return new OkObjectResult(BalanceFunctions.ToResponse(saved.Value));
    }

    private static async Task<(TransferRequest? Body, IActionResult? Error)> ReadAsync(
        HttpRequest req, CancellationToken cancellationToken)
    {
        try
        {
            var body = await JsonSerializer.DeserializeAsync<TransferRequest>(
                req.Body, JsonSerializerOptions.Web, cancellationToken);
            var error = Validate(body);
            return error is null ? (body, null) : (null, new BadRequestObjectResult(error));
        }
        catch (JsonException)
        {
            return (null, new BadRequestObjectResult(BodyShape));
        }
    }

    private sealed record Resolved(TransferDetails Details, CardEntity SenderCard, PhoneEntity Link);

    /// <summary>
    /// The transfer's details once checked: the card is the caller's, and a card other than it is
    /// linked to the phone number. Otherwise the response to send.
    /// </summary>
    private async Task<(Resolved? Resolved, IActionResult? Error)> ResolveAsync(
        string senderOid, TransferRequest body, CancellationToken cancellationToken)
    {
        var phoneNumber = CardFunctions.NormalizePhoneNumber(body.PhoneNumber);
        if (phoneNumber is null) return (null, new BadRequestObjectResult("phoneNumber isn't a valid phone number."));

        // The money leaves one of the caller's own cards: their partition is the only one looked in.
        var senderCard = await cardTables.Cards.GetEntityIfExistsAsync<CardEntity>(
            senderOid, body.CardId!, cancellationToken: cancellationToken);
        if (!senderCard.HasValue) return (null, new BadRequestObjectResult("cardId must be one of your cards."));

        // The receiver is whoever linked this phone number to a card.
        var link = await cardTables.Phones.GetEntityIfExistsAsync<PhoneEntity>(
            phoneNumber, "", cancellationToken: cancellationToken);
        if (!link.HasValue) return (null, new NotFoundObjectResult("No card is linked to this phone number."));
        if (link.Value!.Oid == senderOid && link.Value.CardId == body.CardId)
            return (null, new BadRequestObjectResult("The money can't go to the card it comes from."));

        var details = new TransferDetails(
            body.TransferId!, body.CardId!, phoneNumber, body.Amount!.Value, body.Currency!.ToUpperInvariant());
        return (new Resolved(details, senderCard.Value!, link.Value), null);
    }

    // Adds the Pending row, or returns the one a retry already added; null when that one is a
    // different transfer under the same ID.
    private async Task<TransferEntity?> RecordAsync(TransferEntity transfer, CancellationToken cancellationToken)
    {
        try
        {
            await transfers.Client.AddEntityAsync(transfer, cancellationToken);
            return transfer;
        }
        catch (RequestFailedException e) when (e.Status == StatusCodes.Status409Conflict)
        {
            var existing = (await transfers.Client.GetEntityAsync<TransferEntity>(
                transfer.PartitionKey, transfer.RowKey, cancellationToken: cancellationToken)).Value;
            var same = existing.CardId == transfer.CardId
                && existing.PhoneNumber == transfer.PhoneNumber
                && decimal.Parse(existing.Amount, CultureInfo.InvariantCulture)
                    == decimal.Parse(transfer.Amount, CultureInfo.InvariantCulture)
                && existing.Currency == transfer.Currency;
            return same ? existing : null;
        }
    }

    // The sender's Transfer row and the receiver's Receive row, dated when the transfer was made.
    private static (BalanceEntity Sent, BalanceEntity Received) Rows(TransferEntity transfer, string senderPhone)
    {
        BalanceEntity Row(string oid, string rowKey, string type, string cardId, string counterparty) => new()
        {
            PartitionKey = oid,
            RowKey = rowKey,
            Amount = transfer.Amount,
            Currency = transfer.Currency,
            Date = transfer.CreatedAt,
            CardId = cardId,
            Type = type,
            Counterparty = counterparty,
            TransferId = transfer.RowKey,
        };

        return (
            Row(transfer.PartitionKey, $"{transfer.RowKey}-sent", BalanceTypes.Transfer, transfer.CardId, transfer.PhoneNumber),
            Row(transfer.ReceiverOid, $"{transfer.RowKey}-received", BalanceTypes.Receive, transfer.ReceiverCardId, senderPhone));
    }

    private static string? Validate(TransferRequest? body)
    {
        if (body is null) return BodyShape;
        // Short enough that "-received" still fits a 64-character RowKey.
        if (body.TransferId is null || !TransferIdPattern().IsMatch(body.TransferId))
            return "transferId must be 1-50 letters, digits, '-' or '_'.";
        if (body.CardId is null || !IdPattern().IsMatch(body.CardId)) return "cardId is required.";
        if (body.Amount is null || body.Amount <= 0) return "amount must be above zero.";
        if (body.Currency is null || !CurrencyPattern().IsMatch(body.Currency))
            return "currency must be a three-letter ISO 4217 code, such as EUR.";
        return null;
    }

    [GeneratedRegex("^[A-Za-z0-9_-]{1,50}$")]
    private static partial Regex TransferIdPattern();

    [GeneratedRegex("^[A-Za-z0-9_-]{1,64}$")]
    private static partial Regex IdPattern();

    [GeneratedRegex("^[A-Za-z]{3}$")]
    private static partial Regex CurrencyPattern();
}
