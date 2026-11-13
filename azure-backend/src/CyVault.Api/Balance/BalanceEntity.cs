using System.Globalization;
using Azure;
using Azure.Data.Tables;

namespace CyVault.Api.Balance;

/// <summary>
/// One row of the balances table. PartitionKey is the owner's oid, so all of a user's balances sit
/// in one partition that only their own token can reach. RowKey is the balance ID, built by
/// <see cref="BalanceKey"/> so the table itself keeps each partition newest first.
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
/// Builds a balance's RowKey: inverted ticks, then a unique ID. Table Storage sorts RowKeys as text
/// from low to high, so with the ticks at a fixed width and a later date giving a smaller number,
/// a partition reads newest first. Balances dated the same instant keep a stable order by ID.
/// </summary>
public static class BalanceKey
{
    public static string For(DateTimeOffset date, string uniqueId)
    {
        var invertedTicks = DateTimeOffset.MaxValue.UtcTicks - date.UtcTicks;
        return $"{invertedTicks.ToString("D19", CultureInfo.InvariantCulture)}_{uniqueId}";
    }

    /// <summary>The unique ID a RowKey was built from: what's kept when the date changes.</summary>
    public static string UniqueIdOf(string rowKey) => rowKey[(rowKey.IndexOf('_') + 1)..];
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

    /// <summary>What the row adds to a total. A row stored before the Type column is already signed.</summary>
    public static decimal SignedAmount(BalanceEntity row)
    {
        var amount = decimal.Parse(row.Amount, CultureInfo.InvariantCulture);
        if (row.Type.Length == 0) return amount;
        return IsOutgoing(row.Type) ? -Math.Abs(amount) : Math.Abs(amount);
    }
}

/// <summary>What the client sends to create or replace a balance.</summary>
/// Creating needs the ID and date: the row's key is built from them, so sending the same ones again
/// reaches the same row instead of adding another. Without a date, an edit keeps the row's date.
/// Without a type, the amount's sign gives it (negative is a purchase), as older apps send it.
public sealed record BalanceRequest(
    string? Name,
    string? Description,
    decimal? Amount,
    string? Currency,
    DateTimeOffset? Date,
    string? CardId,
    string? Type,
    string? Id = null);

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

/// <summary>What the user's balances add up to in one currency.</summary>
public sealed record BalanceTotalResponse(string Currency, decimal Total);

/// <summary>The user's totals per currency, and when their oldest balance happened; null with none.</summary>
public sealed record BalanceTotalsResponse(DateTimeOffset? OldestDate, List<BalanceTotalResponse> Totals);

/// <summary>The balances to delete in one request.</summary>
public sealed record DeleteBalancesRequest(List<string>? Ids);

/// <summary>One page of balances, newest first; a null token means there are no more.</summary>
public sealed record BalancePageResponse(List<BalanceResponse> Items, string? ContinuationToken);
