using System.Text.Json;
using System.Text.RegularExpressions;
using Azure;
using Azure.Data.Tables;
using CyVault.Api.Auth;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Azure.Functions.Worker;
using Microsoft.Extensions.Logging;

namespace CyVault.Api.Card;

/// <summary>
/// The signed-in user's cards, each linked to a phone number. Like the balance routes, no user ID
/// is in any route: the partition is always the oid from the validated access token.
/// </summary>
public sealed partial class CardFunctions(CardTables tables, ILogger<CardFunctions> logger)
{
    [Function("ListCards")]
    public async Task<IActionResult> List(
        [HttpTrigger(AuthorizationLevel.Anonymous, "get", Route = "me/cards")] HttpRequest req,
        CancellationToken cancellationToken)
    {
        var caller = await CallerAuthentication.AuthenticateAsync(req.HttpContext, CallerAuthentication.CardScope);
        if (caller.Failure is not null) return caller.Failure;

        var result = new List<CardResponse>();
        await foreach (var entity in tables.Cards.QueryAsync<CardEntity>(
            e => e.PartitionKey == caller.ObjectId, cancellationToken: cancellationToken))
        {
            result.Add(ToResponse(entity));
        }
        // Newest first; the ID breaks ties so the order is stable.
        return new OkObjectResult(result
            .OrderByDescending(c => c.CreatedAt)
            .ThenBy(c => c.Id, StringComparer.Ordinal)
            .ToList());
    }

    /// <summary>
    /// Adds the card, or changes the phone number of the card with this ID. The client picks the
    /// ID, so a retried request updates the same card instead of adding a second one.
    /// </summary>
    [Function("PutCard")]
    public async Task<IActionResult> Put(
        [HttpTrigger(AuthorizationLevel.Anonymous, "put", Route = "me/cards/{id}")] HttpRequest req,
        string id,
        CancellationToken cancellationToken)
    {
        var caller = await CallerAuthentication.AuthenticateAsync(req.HttpContext, CallerAuthentication.CardScope);
        if (caller.Failure is not null) return caller.Failure;
        if (!IsValidId(id)) return InvalidId();

        CardRequest? body;
        try
        {
            body = await JsonSerializer.DeserializeAsync<CardRequest>(
                req.Body, JsonSerializerOptions.Web, cancellationToken);
        }
        catch (JsonException)
        {
            return new BadRequestObjectResult("The body must be JSON: { \"cardNumber\", \"phoneNumber\" }.");
        }

        var cardNumber = Digits(body?.CardNumber);
        if (!IsValidCardNumber(cardNumber))
            return new BadRequestObjectResult("cardNumber must be 16 to 19 digits and pass the Luhn check.");
        var phoneNumber = NormalizePhoneNumber(body?.PhoneNumber);
        if (phoneNumber is null)
            return new BadRequestObjectResult("phoneNumber must be a Portuguese mobile number or an E.164 number, such as +351912345678.");

        var existing = await tables.Cards.GetEntityIfExistsAsync<CardEntity>(
            caller.ObjectId!, id, cancellationToken: cancellationToken);

        // Link the phone number first: inserting its row fails if another card already has it.
        // The cards and phones tables can't be written in one transaction, so a crash between the
        // two writes can leave a phone row for a card that wasn't saved; retrying with the same
        // ID completes it, because a link to the same card counts as already done.
        try
        {
            await tables.Phones.AddEntityAsync(
                new PhoneEntity { PartitionKey = phoneNumber, RowKey = "", Oid = caller.ObjectId!, CardId = id },
                cancellationToken);
        }
        catch (RequestFailedException e) when (e.Status == StatusCodes.Status409Conflict)
        {
            var link = await tables.Phones.GetEntityAsync<PhoneEntity>(phoneNumber, "", cancellationToken: cancellationToken);
            if (link.Value.Oid != caller.ObjectId || link.Value.CardId != id)
                return new ConflictObjectResult("This phone number is already linked to a card.");
        }

        var entity = new CardEntity
        {
            PartitionKey = caller.ObjectId!,
            RowKey = id,
            Last4 = cardNumber[^4..],
            PhoneNumber = phoneNumber,
            CreatedAt = existing.HasValue ? existing.Value!.CreatedAt : DateTimeOffset.UtcNow,
        };
        await tables.Cards.UpsertEntityAsync(entity, TableUpdateMode.Replace, cancellationToken);

        // The card's old phone number is free again.
        if (existing.HasValue && existing.Value!.PhoneNumber != phoneNumber)
        {
            await UnlinkPhoneAsync(existing.Value.PhoneNumber, caller.ObjectId!, id, cancellationToken);
        }

        logger.LogInformation("Stored card {Id} for {ObjectId}", id, caller.ObjectId);
        return new OkObjectResult(ToResponse(entity));
    }

    [Function("DeleteCard")]
    public async Task<IActionResult> Delete(
        [HttpTrigger(AuthorizationLevel.Anonymous, "delete", Route = "me/cards/{id}")] HttpRequest req,
        string id,
        CancellationToken cancellationToken)
    {
        var caller = await CallerAuthentication.AuthenticateAsync(req.HttpContext, CallerAuthentication.CardScope);
        if (caller.Failure is not null) return caller.Failure;
        if (!IsValidId(id)) return InvalidId();

        // Deleting a card that isn't there is still a success: the result is the same.
        var existing = await tables.Cards.GetEntityIfExistsAsync<CardEntity>(
            caller.ObjectId!, id, cancellationToken: cancellationToken);
        if (!existing.HasValue) return new NoContentResult();

        await tables.Cards.DeleteEntityAsync(caller.ObjectId!, id, ETag.All, cancellationToken);
        await UnlinkPhoneAsync(existing.Value!.PhoneNumber, caller.ObjectId!, id, cancellationToken);
        return new NoContentResult();
    }

    /// <summary>Removes the phone number's link, but only while it still points to this card.</summary>
    private async Task UnlinkPhoneAsync(string phoneNumber, string objectId, string cardId, CancellationToken cancellationToken)
    {
        var link = await tables.Phones.GetEntityIfExistsAsync<PhoneEntity>(
            phoneNumber, "", cancellationToken: cancellationToken);
        if (!link.HasValue || link.Value!.Oid != objectId || link.Value.CardId != cardId) return;

        try
        {
            // The ETag makes sure the row is deleted as read, not after someone else re-linked it.
            await tables.Phones.DeleteEntityAsync(phoneNumber, "", link.Value.ETag, cancellationToken);
        }
        catch (RequestFailedException e) when (e.Status is StatusCodes.Status404NotFound or StatusCodes.Status412PreconditionFailed)
        {
            // Already removed or re-linked: nothing left to undo.
        }
    }

    private static CardResponse ToResponse(CardEntity entity) =>
        new(entity.RowKey, entity.Last4, entity.PhoneNumber, entity.CreatedAt);

    private static string Digits(string? value) =>
        new((value ?? "").Where(c => c is not (' ' or '-')).ToArray());

    internal static bool IsValidCardNumber(string digits)
    {
        if (!CardNumberPattern().IsMatch(digits)) return false;

        // Luhn: from the right, double every second digit (subtracting 9 above 9); the sum must end in 0.
        var sum = 0;
        for (var i = 0; i < digits.Length; i++)
        {
            var digit = digits[digits.Length - 1 - i] - '0';
            if (i % 2 == 1)
            {
                digit *= 2;
                if (digit > 9) digit -= 9;
            }
            sum += digit;
        }
        return sum % 10 == 0;
    }

    /// <summary>
    /// The number in E.164, or null when it isn't valid. A 9-digit Portuguese mobile number
    /// (starting with 9) gets the +351 prefix, and 00 is read as +.
    /// </summary>
    internal static string? NormalizePhoneNumber(string? value)
    {
        var number = Digits(value);
        if (number.StartsWith("00")) number = "+" + number[2..];
        if (PortugueseMobilePattern().IsMatch(number)) number = "+351" + number;
        return E164Pattern().IsMatch(number) ? number : null;
    }

    // RowKey can't contain / \ # ? or control characters; a narrow pattern keeps IDs safe and simple.
    private static bool IsValidId(string id) => IdPattern().IsMatch(id);

    private static BadRequestObjectResult InvalidId() =>
        new("The card ID must be 1-64 letters, digits, '-' or '_'.");

    [GeneratedRegex("^[A-Za-z0-9_-]{1,64}$")]
    private static partial Regex IdPattern();

    [GeneratedRegex("^[0-9]{16,19}$")]
    private static partial Regex CardNumberPattern();

    [GeneratedRegex("^9[0-9]{8}$")]
    private static partial Regex PortugueseMobilePattern();

    [GeneratedRegex(@"^\+[1-9][0-9]{7,14}$")]
    private static partial Regex E164Pattern();
}
