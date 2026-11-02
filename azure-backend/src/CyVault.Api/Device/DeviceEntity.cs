using Azure;
using Azure.Data.Tables;

namespace CyVault.Api.Device;

/// <summary>
/// One row of the devices table: a phone the user enrolled for signing transfers. PartitionKey is
/// the owner's oid and RowKey the device ID the server picked, so a user's devices sit in one
/// partition that only their own token can reach. Only the public key is stored: the private key
/// never leaves the phone's Keystore or Secure Enclave.
/// </summary>
public sealed class DeviceEntity : ITableEntity
{
    public string PartitionKey { get; set; } = "";
    public string RowKey { get; set; } = "";
    public DateTimeOffset? Timestamp { get; set; }
    public ETag ETag { get; set; }

    /// <summary>The EC P-256 public key as base64 DER SubjectPublicKeyInfo, in canonical form.</summary>
    public string PublicKey { get; set; } = "";

    /// <summary>How the device signs: always <see cref="DeviceKeyAlgorithm.Es256"/> for now.</summary>
    public string Algorithm { get; set; } = "";

    /// <summary>A label the app sends, such as "Pixel 8", for the user's device list. May be empty.</summary>
    public string DeviceName { get; set; } = "";

    public DateTimeOffset CreatedAt { get; set; }

    /// <summary>
    /// When the user removed the device. The row is kept, so a signature it made can still be
    /// checked later, but a revoked device can't sign anything new.
    /// </summary>
    public DateTimeOffset? RevokedAt { get; set; }}

public static class DeviceKeyAlgorithm
{
    /// <summary>ECDSA on P-256 with SHA-256, the key type both Android StrongBox and the Secure Enclave support.</summary>
    public const string Es256 = "ES256";
}

/// <summary>The devices table, so DI can tell it from the other tables.</summary>
public sealed record DeviceTable(TableClient Client);

/// <summary>What the app sends to enroll a phone.</summary>
public sealed record DeviceRequest(string? PublicKey, string? DeviceName);

/// <summary>What the API returns for a device. The app keeps the ID and sends it with every signature.</summary>
public sealed record DeviceResponse(
    string Id,
    string Algorithm,
    string DeviceName,
    DateTimeOffset CreatedAt);
