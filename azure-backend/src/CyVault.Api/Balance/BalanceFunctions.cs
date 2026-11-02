using System.Globalization;
using System.Text.Json;
using System.Text.RegularExpressions;
using Azure;
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
    TableClient balances, CardTables cardTables, ILogger<BalanceFunctions> logger)
{
    private const int MaxNameLength = 100;
    private const int MaxDescriptionLength = 500;

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
        // Table Storage returns rows in RowKey order, and RowKeys are random IDs; newest first is
        // what a list of purchases and income needs. The ID breaks ties so the order is stable.
        return new OkObjectResult(result
            .OrderByDescending(b => b.Date)
            .ThenBy(b => b.Id, StringComparer.Ordinal)
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
            return new BadRequestObjectResult("The body must be JSON: { \"name\", \"description\", \"amount\", \"currency\", \"date\", \"cardId\", \"type\" }.");
        }

        var error = Validate(body);
        if (error is not null) return new BadRequestObjectResult(error);

        // The card must be one of the caller's own: their partition is the only one looked in.
        var card = await cardTables.Cards.GetEntityIfExistsAsync<CardEntity>(
            caller.ObjectId!, body!.CardId!, select: ["RowKey"], cancellationToken: cancellationToken);
        if (!card.HasValue) return new BadRequestObjectResult("cardId must be one of your cards.");

        var existing = await balances.GetEntityIfExistsAsync<BalanceEntity>(
            caller.ObjectId!, id, cancellationToken: cancellationToken);
        // Transfers and receives are a record of money that moved: they can be deleted, not changed.
        if (existing.HasValue && BalanceTypes.IsFromTransfer(existing.Value!.Type))
            return new ConflictObjectResult("A transfer or receive can't be edited.");

        // Without a date in the body, an edit keeps the row's date (or, on a row stored before
        // the Date column, when it was last written) and a new row is dated now.
        var date = body.Date
            ?? (existing.HasValue ? existing.Value!.Date ?? existing.Value.Timestamp : DateTimeOffset.UtcNow);

        var entity = new BalanceEntity
        {
            PartitionKey = caller.ObjectId!,
            RowKey = id,
            Name = body.Name!.Trim(),
            Description = body.Description?.Trim() ?? "",
            // Stored above zero: the type gives the direction.
            Amount = Math.Abs(body.Amount!.Value).ToString(CultureInfo.InvariantCulture),
            Currency = body.Currency!.ToUpperInvariant(),
            Date = date!.Value.ToUniversalTime(),
            CardId = body.CardId!,
            Type = TypeOf(body),
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
        if (body is null) return "The body must be JSON: { \"name\", \"description\", \"amount\", \"currency\", \"date\", \"cardId\", \"type\" }.";
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
        new("The balance ID must be 1-64 letters, digits, '-' or '_'.");

    [GeneratedRegex("^[A-Za-z0-9_-]{1,64}$")]
    private static partial Regex IdPattern();

    [GeneratedRegex("^[A-Za-z]{3}$")]
    private static partial Regex CurrencyPattern();
}
