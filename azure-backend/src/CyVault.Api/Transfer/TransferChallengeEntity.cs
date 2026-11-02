using Azure;
using Azure.Data.Tables;

namespace CyVault.Api.Transfer;

/// <summary>
/// One row of the challenges table: a one-time nonce the app's device key signs to approve
/// exactly one transfer (PSD2 dynamic linking). PartitionKey is the sender's oid and RowKey the
/// nonce. The row keeps the details it was issued for, so a signature can't approve any others.
/// </summary>
public sealed class TransferChallengeEntity : ITableEntity
{
    public string PartitionKey { get; set; } = "";
    public string RowKey { get; set; } = "";
    public DateTimeOffset? Timestamp { get; set; }
    public ETag ETag { get; set; }

    public string TransferId { get; set; } = "";
    public string CardId { get; set; } = "";

    /// <summary>The receiver's phone number, E.164.</summary>
    public string PhoneNumber { get; set; } = "";

    /// <summary>An invariant decimal string, like the transfers table's.</summary>
    public string Amount { get; set; } = "0";

    public string Currency { get; set; } = "";

    /// <summary>The exact text the device signs: these details and the nonce, one per line.</summary>
    public string Payload { get; set; } = "";

    public DateTimeOffset ExpiresAt { get; set; }

    /// <summary>
    /// When a transfer used it. A used challenge still approves its own transfer again, so a retry
    /// after a lost response finishes it, but never another one.
    /// </summary>
    public DateTimeOffset? UsedAt { get; set; }
}

/// <summary>
/// The caller's failed signatures, in the challenges table under RowKey <see cref="Key"/>. After
/// <see cref="TransferSigning.MaxFailedAttempts"/> in a row, signing is blocked until
/// <see cref="LockedUntil"/> (PSD2 RTS Art. 4(3)(b)).
/// </summary>
public sealed class SigningLockEntity : ITableEntity
{
    /// <summary>Can't clash with a nonce: those are 43 base64url characters.</summary>
    public const string Key = "signing-lock";

    public string PartitionKey { get; set; } = "";
    public string RowKey { get; set; } = Key;
    public DateTimeOffset? Timestamp { get; set; }
    public ETag ETag { get; set; }

    public int FailedAttempts { get; set; }
    public DateTimeOffset? LockedUntil { get; set; }
}

/// <summary>The challenges table, so DI can tell it from the other tables.</summary>
public sealed record ChallengeTable(TableClient Client);

/// <summary>What the API returns for a challenge: the app signs <paramref name="Payload"/> as UTF-8.</summary>
public sealed record TransferChallengeResponse(string Nonce, string Payload, DateTimeOffset ExpiresAt);

/// <summary>A transfer's checked details: what a challenge is issued for and a signature approves.</summary>
public sealed record TransferDetails(string TransferId, string CardId, string PhoneNumber, decimal Amount, string Currency);
