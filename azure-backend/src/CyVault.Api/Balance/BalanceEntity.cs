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

    /// <summary>
    /// The amount as an invariant decimal string: Table Storage has no decimal type, and a
    /// double would round money.
    /// </summary>
    public string Amount { get; set; } = "0";

    public string Currency { get; set; } = "";
}

/// <summary>What the client sends to create or replace a balance.</summary>
public sealed record BalanceRequest(string? Name, decimal? Amount, string? Currency);

/// <summary>What the API returns for a balance.</summary>
public sealed record BalanceResponse(string Id, string Name, decimal Amount, string Currency, DateTimeOffset? UpdatedAt);
