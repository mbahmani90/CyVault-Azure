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
    /// double would round money.
    /// </summary>
    public string Amount { get; set; } = "0";

    public string Currency { get; set; } = "";

    /// <summary>
    /// When the purchase or income happened. Unlike Timestamp, it doesn't move when the row is
    /// edited. Null only on rows stored before this column existed.
    /// </summary>
    public DateTimeOffset? Date { get; set; }
}

/// <summary>What the client sends to create or replace a balance.</summary>
/// Without a date, a new balance gets the current time and an edited one keeps its date.
public sealed record BalanceRequest(
    string? Name, string? Description, decimal? Amount, string? Currency, DateTimeOffset? Date);

/// <summary>What the API returns for a balance.</summary>
public sealed record BalanceResponse(
    string Id,
    string Name,
    string Description,
    decimal Amount,
    string Currency,
    DateTimeOffset? Date,
    DateTimeOffset? UpdatedAt);
