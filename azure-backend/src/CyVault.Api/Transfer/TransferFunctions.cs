using System.Globalization;
using System.Text.Json;
using System.Text.RegularExpressions;
using Azure;
using Azure.Data.Tables;
using CyVault.Api.Auth;
using CyVault.Api.Balance;
using CyVault.Api.Card;
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
    ILogger<TransferFunctions> logger)
{
    private const string BodyShape =
        "The body must be JSON: { \"transferId\", \"cardId\", \"phoneNumber\", \"amount\", \"currency\" }.";

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

        TransferRequest? body;
        try
        {
            body = await JsonSerializer.DeserializeAsync<TransferRequest>(
                req.Body, JsonSerializerOptions.Web, cancellationToken);
        }
        catch (JsonException)
        {
            return new BadRequestObjectResult(BodyShape);
        }

        var error = Validate(body);
        if (error is not null) return new BadRequestObjectResult(error);
        var phoneNumber = CardFunctions.NormalizePhoneNumber(body!.PhoneNumber);
        if (phoneNumber is null) return new BadRequestObjectResult("phoneNumber isn't a valid phone number.");

        // The money leaves one of the caller's own cards: their partition is the only one looked in.
        var senderCard = await cardTables.Cards.GetEntityIfExistsAsync<CardEntity>(
            senderOid, body.CardId!, cancellationToken: cancellationToken);
        if (!senderCard.HasValue) return new BadRequestObjectResult("cardId must be one of your cards.");

        // The receiver is whoever linked this phone number to a card.
        var link = await cardTables.Phones.GetEntityIfExistsAsync<PhoneEntity>(
            phoneNumber, "", cancellationToken: cancellationToken);
        if (!link.HasValue) return new NotFoundObjectResult("No card is linked to this phone number.");
        if (link.Value!.Oid == senderOid && link.Value.CardId == body.CardId)
            return new BadRequestObjectResult("The money can't go to the card it comes from.");

        var transfer = await RecordAsync(new TransferEntity
        {
            PartitionKey = senderOid,
            RowKey = body.TransferId!,
            CardId = body.CardId!,
            PhoneNumber = phoneNumber,
            ReceiverOid = link.Value.Oid,
            ReceiverCardId = link.Value.CardId,
            Amount = body.Amount!.Value.ToString(CultureInfo.InvariantCulture),
            Currency = body.Currency!.ToUpperInvariant(),
            Status = TransferStatus.Pending,
            CreatedAt = DateTimeOffset.UtcNow,
        }, cancellationToken);
        if (transfer is null)
            return new ConflictObjectResult("This transferId was already used for a different transfer.");

        var (sent, received) = Rows(transfer, senderPhone: senderCard.Value!.PhoneNumber);
        // Already finished: return what it wrote, without bringing back a row the user deleted.
        if (transfer.Status == TransferStatus.Completed) return new OkObjectResult(BalanceFunctions.ToResponse(sent));

        // Upserts with fixed RowKeys, so finishing a Pending transfer again writes the same rows.
        await balances.UpsertEntityAsync(sent, TableUpdateMode.Replace, cancellationToken);
        await balances.UpsertEntityAsync(received, TableUpdateMode.Replace, cancellationToken);
        transfer.Status = TransferStatus.Completed;
        await transfers.Client.UpdateEntityAsync(transfer, ETag.All, TableUpdateMode.Merge, cancellationToken);

        logger.LogInformation(
            "Transfer {Id} from {Sender} to {Receiver} completed", transfer.RowKey, senderOid, transfer.ReceiverOid);
        var saved = await balances.GetEntityAsync<BalanceEntity>(sent.PartitionKey, sent.RowKey, cancellationToken: cancellationToken);
        return new OkObjectResult(BalanceFunctions.ToResponse(saved.Value));
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
