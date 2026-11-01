using Azure;
using Azure.Data.Tables;

namespace CyVault.Api.Card;

/// <summary>
/// One row of the cards table. PartitionKey is the owner's oid and RowKey the card ID, so all of
/// a user's cards sit in one partition that only their own token can reach. Only the last four
/// digits are kept: the full card number is never stored.
/// </summary>
public sealed class CardEntity : ITableEntity
{
    public string PartitionKey { get; set; } = "";
    public string RowKey { get; set; } = "";
    public DateTimeOffset? Timestamp { get; set; }
    public ETag ETag { get; set; }

    public string Last4 { get; set; } = "";

    /// <summary>
    /// Visa, Mastercard, Maestro or Other, worked out from the card number when it's added. Empty
    /// on cards stored before this column existed.
    /// </summary>
    public string Brand { get; set; } = "";

    /// <summary>The bank the user named for the card; empty when they didn't name one.</summary>
    public string BankName { get; set; } = "";

    /// <summary>E.164, such as +351912345678: the same number always has the same form.</summary>
    public string PhoneNumber { get; set; } = "";

    /// <summary>When the card was added. Unlike Timestamp, it doesn't move when the row is edited.</summary>
    public DateTimeOffset CreatedAt { get; set; }
}

/// <summary>
/// One row of the phones table: which user and card a phone number is linked to, so a transfer to
/// a phone number finds its owner without scanning every user's cards. PartitionKey is the E.164
/// phone number and RowKey is empty, so inserting a number that's already linked fails: one phone
/// number, one card.
/// </summary>
public sealed class PhoneEntity : ITableEntity
{
    public string PartitionKey { get; set; } = "";
    public string RowKey { get; set; } = "";
    public DateTimeOffset? Timestamp { get; set; }
    public ETag ETag { get; set; }

    public string Oid { get; set; } = "";
    public string CardId { get; set; } = "";
}

/// <summary>The two tables a card is written to, so DI can tell them apart.</summary>
public sealed record CardTables(TableClient Cards, TableClient Phones);

/// <summary>What the client sends to add a card or change its phone number.</summary>
public sealed record CardRequest(string? CardNumber, string? PhoneNumber, string? BankName);

/// <summary>What the API returns for a card.</summary>
public sealed record CardResponse(
    string Id,
    string Last4,
    string Brand,
    string BankName,
    string PhoneNumber,
    DateTimeOffset CreatedAt);
