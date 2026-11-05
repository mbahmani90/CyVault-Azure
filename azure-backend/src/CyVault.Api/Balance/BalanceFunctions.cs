using System.Globalization;
using System.Text.Json;
using System.Text.RegularExpressions;
using Azure.Data.Tables;
using CyVault.Api.Auth;
using CyVault.Api.Card;
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
public sealed partial class BalanceFunctions(
    TableClient balances, BalanceWriter writer, CardTables cardTables, ILogger<BalanceFunctions> logger)
{
    private const int MaxNameLength = 100;
    private const int MaxDescriptionLength = 500;

    // Table Storage returns at most 1000 rows per request.
    private const int MaxPageSize = 1000;

    private const string BodyShape =
        "The body must be JSON: { \"id\", \"name\", \"description\", \"amount\", \"currency\", \"date\", \"cardId\", \"type\" }.";

    /// <summary>
    /// The caller's balances, newest first: the order the table keeps them in. With pageSize, one
    /// page and the token for the next; without it, all of them.
    /// </summary>
    [Function("ListBalances")]
    public async Task<IActionResult> List(
        [HttpTrigger(AuthorizationLevel.Anonymous, "get", Route = "me/balances")] HttpRequest req,
        CancellationToken cancellationToken)
    {
        var caller = await CallerAuthentication.AuthenticateAsync(req.HttpContext, CallerAuthentication.BalanceScope);
        if (caller.Failure is not null) return caller.Failure;

        if (req.Query.TryGetValue("pageSize", out var pageSizeValue))
        {
            if (!int.TryParse(pageSizeValue, CultureInfo.InvariantCulture, out var pageSize)
                || pageSize is < 1 or > MaxPageSize)
                return new BadRequestObjectResult($"pageSize must be 1-{MaxPageSize}.");

            // The filter stays the caller's partition whatever the token says, so a changed token
            // can't reach another user's rows.
            string? continuationToken = req.Query["continuationToken"];
            await foreach (var page in balances
                .QueryAsync<BalanceEntity>(BalanceWriter.RowsFilter(caller.ObjectId!), pageSize, cancellationToken: cancellationToken)
                .AsPages(continuationToken, pageSize))
            {
                return new OkObjectResult(new BalancePageResponse(
                    page.Values.Select(ToResponse).ToList(), page.ContinuationToken));
            }
            return new OkObjectResult(new BalancePageResponse([], null));
        }

        var result = new List<BalanceResponse>();
        await foreach (var entity in balances.QueryAsync<BalanceEntity>(
            BalanceWriter.RowsFilter(caller.ObjectId!), cancellationToken: cancellationToken))
        {
            result.Add(ToResponse(entity));
        }
        return new OkObjectResult(result);
    }

    /// <summary>The caller's total per currency, kept up to date by every balance write.</summary>
    [Function("GetBalanceTotals")]
    public async Task<IActionResult> GetTotals(
        [HttpTrigger(AuthorizationLevel.Anonymous, "get", Route = "me/balance-totals")] HttpRequest req,
        CancellationToken cancellationToken)
    {
        var caller = await CallerAuthentication.AuthenticateAsync(req.HttpContext, CallerAuthentication.BalanceScope);
        if (caller.Failure is not null) return caller.Failure;

        var totals = await writer.GetTotalsAsync(caller.ObjectId!, cancellationToken);
        return new OkObjectResult(totals
            .OrderBy(t => t.Key, StringComparer.Ordinal)
            .Select(t => new BalanceTotalResponse(t.Key, t.Value))
            .ToList());
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

    /// <summary>
    /// Creates the balance. The client picks the unique ID once per new balance, so a retry after a
    /// lost response replaces the row it already wrote instead of adding another.
    /// </summary>
    [Function("PostBalance")]
    public async Task<IActionResult> Post(
        [HttpTrigger(AuthorizationLevel.Anonymous, "post", Route = "me/balances")] HttpRequest req,
        CancellationToken cancellationToken)
    {
        var caller = await CallerAuthentication.AuthenticateAsync(req.HttpContext, CallerAuthentication.BalanceScope);
        if (caller.Failure is not null) return caller.Failure;

        var (body, bodyError) = await ReadAsync(req, caller.ObjectId!, cancellationToken);
        if (bodyError is not null) return bodyError;
        if (body!.Id is null || !NewIdPattern().IsMatch(body.Id))
            return new BadRequestObjectResult("id must be 1-50 letters, digits, '-' or '_'.");
        if (body.Date is null) return new BadRequestObjectResult("date is required.");

        var rowKey = BalanceKey.For(body.Date.Value, body.Id);
        var existing = await balances.GetEntityIfExistsAsync<BalanceEntity>(
            caller.ObjectId!, rowKey, cancellationToken: cancellationToken);
        if (existing.HasValue && BalanceTypes.IsFromTransfer(existing.Value!.Type))
            return new ConflictObjectResult("A transfer or receive can't be edited.");

        var entity = ToEntity(caller.ObjectId!, rowKey, body, body.Date.Value);
        await writer.SaveAsync(entity, cancellationToken);
        logger.LogInformation("Stored balance {Id} for {ObjectId}", rowKey, caller.ObjectId);
        return await ReadBackAsync(entity, cancellationToken);
    }

    /// <summary>
    /// Replaces an existing balance. The date is part of the ID, so a new date moves the row and
    /// the response carries its new ID.
    /// </summary>
    [Function("PutBalance")]
    public async Task<IActionResult> Put(
        [HttpTrigger(AuthorizationLevel.Anonymous, "put", Route = "me/balances/{id}")] HttpRequest req,
        string id,
        CancellationToken cancellationToken)
    {
        var caller = await CallerAuthentication.AuthenticateAsync(req.HttpContext, CallerAuthentication.BalanceScope);
        if (caller.Failure is not null) return caller.Failure;
        if (!IsValidId(id)) return InvalidId();

        var (body, bodyError) = await ReadAsync(req, caller.ObjectId!, cancellationToken);
        if (bodyError is not null) return bodyError;

        var existing = await balances.GetEntityIfExistsAsync<BalanceEntity>(
            caller.ObjectId!, id, cancellationToken: cancellationToken);
        if (!existing.HasValue) return new NotFoundResult();
        // Transfers and receives are a record of money that moved: they can be deleted, not changed.
        if (BalanceTypes.IsFromTransfer(existing.Value!.Type))
            return new ConflictObjectResult("A transfer or receive can't be edited.");

        // Without a date in the body, an edit keeps the row's date (or, on a row stored before
        // the Date column, when it was last written).
        var date = body!.Date ?? existing.Value.Date ?? existing.Value.Timestamp!.Value;
        var rowKey = BalanceKey.For(date, BalanceKey.UniqueIdOf(id));
        var entity = ToEntity(caller.ObjectId!, rowKey, body, date);

        // A RowKey can't change, so a new date is a new row, replacing the old one in one batch.
        await writer.SaveAsync(entity, cancellationToken, replacesRowKey: id);
        logger.LogInformation("Stored balance {Id} for {ObjectId}", rowKey, caller.ObjectId);
        return await ReadBackAsync(entity, cancellationToken);
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
        await writer.DeleteAsync(caller.ObjectId!, id, cancellationToken);
        return new NoContentResult();
    }

    // The body once checked, with a card of the caller's own; otherwise the response to send.
    private async Task<(BalanceRequest? Body, IActionResult? Error)> ReadAsync(
        HttpRequest req, string oid, CancellationToken cancellationToken)
    {
        BalanceRequest? body;
        try
        {
            body = await JsonSerializer.DeserializeAsync<BalanceRequest>(
                req.Body, JsonSerializerOptions.Web, cancellationToken);
        }
        catch (JsonException)
        {
            return (null, new BadRequestObjectResult(BodyShape));
        }

        var error = Validate(body);
        if (error is not null) return (null, new BadRequestObjectResult(error));

        // The card must be one of the caller's own: their partition is the only one looked in.
        var card = await cardTables.Cards.GetEntityIfExistsAsync<CardEntity>(
            oid, body!.CardId!, select: ["RowKey"], cancellationToken: cancellationToken);
        if (!card.HasValue) return (null, new BadRequestObjectResult("cardId must be one of your cards."));

        return (body, null);
    }

    private static BalanceEntity ToEntity(string oid, string rowKey, BalanceRequest body, DateTimeOffset date) => new()
    {
        PartitionKey = oid,
        RowKey = rowKey,
        Name = body.Name!.Trim(),
        Description = body.Description?.Trim() ?? "",
        // Stored above zero: the type gives the direction.
        Amount = Math.Abs(body.Amount!.Value).ToString(CultureInfo.InvariantCulture),
        Currency = body.Currency!.ToUpperInvariant(),
        Date = date.ToUniversalTime(),
        CardId = body.CardId!,
        Type = TypeOf(body),
    };

    // Read it back for the server's Timestamp.
    private async Task<IActionResult> ReadBackAsync(BalanceEntity entity, CancellationToken cancellationToken)
    {
        var saved = await balances.GetEntityAsync<BalanceEntity>(
            entity.PartitionKey, entity.RowKey, cancellationToken: cancellationToken);
        return new OkObjectResult(ToResponse(saved.Value));
    }

    /// <summary>
    /// The row as the API returns it. Rows stored before the Type column hold a signed amount:
    /// negative was a purchase, the rest income.
    /// </summary>
    internal static BalanceResponse ToResponse(BalanceEntity entity)
    {
        var amount = decimal.Parse(entity.Amount, CultureInfo.InvariantCulture);
        var type = entity.Type.Length > 0
            ? entity.Type
            : amount < 0 ? BalanceTypes.Purchase : BalanceTypes.Income;
        return new BalanceResponse(
            entity.RowKey,
            entity.Name,
            entity.Description,
            Math.Abs(amount),
            entity.Currency,
            entity.Date ?? entity.Timestamp,
            entity.Timestamp,
            entity.CardId,
            type,
            entity.Counterparty,
            entity.TransferId);
    }

    // A request without a type is from an older app, which signs the amount instead.
    private static string TypeOf(BalanceRequest body) =>
        body.Type ?? (body.Amount < 0 ? BalanceTypes.Purchase : BalanceTypes.Income);

    private static string? Validate(BalanceRequest? body)
    {
        if (body is null) return BodyShape;
        if (string.IsNullOrWhiteSpace(body.Name) || body.Name.Trim().Length > MaxNameLength)
            return $"name is required and must be at most {MaxNameLength} characters.";
        if (body.Description?.Trim().Length > MaxDescriptionLength)
            return $"description must be at most {MaxDescriptionLength} characters.";
        if (body.Amount is null) return "amount is required.";
        if (body.Type is not null)
        {
            if (body.Type is not (BalanceTypes.Purchase or BalanceTypes.Income))
                return "type must be Purchase or Income: transfers are made with POST /me/transfers.";
            if (body.Amount <= 0) return "amount must be above zero: the type gives the direction.";
        }
        else if (body.Amount == 0)
        {
            return "amount can't be zero.";
        }
        if (body.Currency is null || !CurrencyPattern().IsMatch(body.Currency))
            return "currency must be a three-letter ISO 4217 code, such as EUR.";
        if (body.CardId is null || !IsValidId(body.CardId)) return "cardId is required.";
        return null;
    }

    // RowKey can't contain / \ # ? or control characters; a narrow pattern keeps IDs safe and simple.
    private static bool IsValidId(string id) => IdPattern().IsMatch(id);

    private static BadRequestObjectResult InvalidId() =>
        new("The balance ID must be 1-80 letters, digits, '-' or '_'.");

    // Long enough for the inverted ticks (20 with the '_') and a 60-character unique ID, the
    // longest being a transfer's: its 50-character ID and "-received".
    [GeneratedRegex("^[A-Za-z0-9_-]{1,80}$")]
    private static partial Regex IdPattern();

    [GeneratedRegex("^[A-Za-z0-9_-]{1,50}$")]
    private static partial Regex NewIdPattern();

    [GeneratedRegex("^[A-Za-z]{3}$")]
    private static partial Regex CurrencyPattern();
}
