using System.Security.Cryptography;
using System.Text;
using Azure;
using Azure.Data.Tables;

namespace CyVault.Api.Push;

/// <summary>
/// One row of the pushtokens table: a phone that receives the user's pushes. PartitionKey is the
/// user's oid and RowKey a hash of the token, so a user's phones sit in one partition. It's kept
/// apart from the devices table: every signed-in phone gets pushes, also one without a fingerprint
/// or face unlock, which has no signing key and so no device.
/// </summary>
public sealed class PushTokenEntity : ITableEntity
{
    public string PartitionKey { get; set; } = "";
    public string RowKey { get; set; } = "";
    public DateTimeOffset? Timestamp { get; set; }
    public ETag ETag { get; set; }

    /// <summary>The phone's Firebase Cloud Messaging registration token.</summary>
    public string Token { get; set; } = "";

    public DateTimeOffset UpdatedAt { get; set; }

    /// <summary>
    /// The RowKey for a token: its SHA-256 in hex. A token may hold characters a RowKey can't, and
    /// the same token always gives the same key.
    /// </summary>
    public static string KeyFor(string token) =>
        Convert.ToHexStringLower(SHA256.HashData(Encoding.UTF8.GetBytes(token)));
}

/// <summary>The pushtokens table, so DI can tell it from the other tables.</summary>
public sealed record PushTokenTable(TableClient Client);

/// <summary>What the app sends to start or stop pushes to this phone.</summary>
public sealed record PushTokenRequest(string? Token);
