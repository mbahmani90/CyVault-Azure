using Azure;
using Azure.Data.Tables;

namespace CyVault.Api.Balance;

/// <summary>
/// One row of the balances table. PartitionKey is the owner's oid and RowKey the balance ID, so
/// all of a user's balances sit in one partition that only their own token can reach.
/// </summary>
public sealed class BalanceEntity : ITableEntity
{
    public string PartitionKey { get; set; } = "";
    public string RowKey { get; set; } = "";
    public DateTimeOffset? Timestamp { get; set; }
    public ETag ETag { get; set; }

    public string Name { get; set; } = "";
    public string Description { get; set; } = "";

    /// <summary>
    /// The amount as an invariant decimal string: Table Storage has no decimal type, and a
    /// double would round money. Above zero, with <see cref="Type"/> giving the direction; only
    /// rows stored before the Type column are signed.
    /// </summary>
    public string Amount { get; set; } = "0";

    public string Currency { get; set; } = "";

    /// <summary>
    /// When the purchase or income happened. Unlike Timestamp, it doesn't move when the row is
    /// edited. Null only on rows stored before this column existed.
    /// </summary>
    public DateTimeOffset? Date { get; set; }

    /// <summary>
    /// The caller's card the purchase or income belongs to: a RowKey in the cards table. Empty
    /// only on rows stored before this column existed.
    /// </summary>
    public string CardId { get; set; } = "";

    /// <summary>One of <see cref="BalanceTypes"/>; empty only on rows stored before this column.</summary>
    public string Type { get; set; } = "";

    /// <summary>On a transfer or receive row, the other side's phone number (E.164); else empty.</summary>
    public string Counterparty { get; set; } = "";

    /// <summary>On a transfer or receive row, the transfer it came from; else empty.</summary>
    public string TransferId { get; set; } = "";
}

/// <summary>
/// What a balance row is. The user adds purchases and income; transfers and receives are written
/// only by POST /me/transfers, and can be deleted but not edited.
/// </summary>
public static class BalanceTypes
{
    public const string Purchase = "Purchase";
    public const string Income = "Income";
    public const string Transfer = "Transfer";
    public const string Receive = "Receive";

    /// <summary>Money going out of the card: a purchase or a transfer.</summary>
    public static bool IsOutgoing(string type) => type is Purchase or Transfer;

    public static bool IsFromTransfer(string type) => type is Transfer or Receive;
}

/// <summary>What the client sends to create or replace a balance.</summary>
/// Without a date, a new balance gets the current time and an edited one keeps its date.
/// Without a type, the amount's sign gives it (negative is a purchase), as older apps send it.
public sealed record BalanceRequest(
    string? Name,
    string? Description,
    decimal? Amount,
    string? Currency,
    DateTimeOffset? Date,
    string? CardId,
    string? Type);

/// <summary>What the API returns for a balance: the amount is always above zero.</summary>
public sealed record BalanceResponse(
    string Id,
    string Name,
    string Description,
    decimal Amount,
    string Currency,
    DateTimeOffset? Date,
    DateTimeOffset? UpdatedAt,
    string CardId,
    string Type,
    string Counterparty,
    string TransferId);
