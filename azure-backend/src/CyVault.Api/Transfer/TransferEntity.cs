using Azure;
using Azure.Data.Tables;

namespace CyVault.Api.Transfer;

/// <summary>
/// One row of the transfers table: PartitionKey is the sender's oid and RowKey the transfer ID the
/// app picked, so a retry finds the same row and nobody else's transfer can collide with it.
/// </summary>
public sealed class TransferEntity : ITableEntity
{
    public string PartitionKey { get; set; } = "";
    public string RowKey { get; set; } = "";
    public DateTimeOffset? Timestamp { get; set; }
    public ETag ETag { get; set; }

    /// <summary>The sender's card the money leaves.</summary>
    public string CardId { get; set; } = "";

    /// <summary>The receiver's phone number (E.164), as looked up in the phones table.</summary>
    public string PhoneNumber { get; set; } = "";

    public string ReceiverOid { get; set; } = "";
    public string ReceiverCardId { get; set; } = "";

    /// <summary>Above zero, as an invariant decimal string like the balances table's.</summary>
    public string Amount { get; set; } = "0";

    public string Currency { get; set; } = "";

    /// <summary>One of <see cref="TransferStatus"/>.</summary>
    public string Status { get; set; } = "";

    public DateTimeOffset CreatedAt { get; set; }
}

/// <summary>
/// A transfer is Pending from the moment it's recorded until both balance rows are written, then
/// Completed. A Pending one is finished by the next request with its ID.
/// </summary>
public static class TransferStatus
{
    public const string Pending = "Pending";
    public const string Completed = "Completed";
}

/// <summary>The transfers table, so DI can tell it from the balances table.</summary>
public sealed record TransferTable(TableClient Client);

/// <summary>What the client sends to move money from one of its cards to a phone number.</summary>
public sealed record TransferRequest(
    string? TransferId, string? CardId, string? PhoneNumber, decimal? Amount, string? Currency);
