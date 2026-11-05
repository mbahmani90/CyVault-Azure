using System.Globalization;
using System.Text.Json;
using Azure;
using Azure.Data.Tables;

namespace CyVault.Api.Balance;

/// <summary>
/// One user's total per currency: a row in their own partition, so a single batch can change it
/// together with a balance. Balance IDs can't contain '~', so no route can reach this row.
/// </summary>
public sealed class BalanceTotalsEntity : ITableEntity
{
    /// <summary>Sorts after every balance's RowKey, which starts with a digit.</summary>
    public const string Key = "~totals";

    public string PartitionKey { get; set; } = "";
    public string RowKey { get; set; } = Key;
    public DateTimeOffset? Timestamp { get; set; }
    public ETag ETag { get; set; }

    /// <summary>Currency to invariant decimal string, as JSON: a double would round money.</summary>
    public string Totals { get; set; } = "{}";
}

/// <summary>
/// The one place that changes a user's balance rows, so their total can't fall behind. Every write
/// is a batch of the balance and the total, with the difference worked out from the row already
/// stored, so sending the same save twice changes nothing the second time.
/// </summary>
public sealed class BalanceWriter(TableClient balances)
{
    // Enough for the sample balances, which save 10 at a time for one user.
    private const int MaxAttempts = 15;

    /// <summary>Everything in the partition except the totals row, as the table orders it.</summary>
    public static string RowsFilter(string oid) =>
        TableClient.CreateQueryFilter($"PartitionKey eq {oid} and RowKey lt {"~"}");

    public async Task<IReadOnlyDictionary<string, decimal>> GetTotalsAsync(
        string oid, CancellationToken cancellationToken)
    {
        var stored = await balances.GetEntityIfExistsAsync<BalanceTotalsEntity>(
            oid, BalanceTotalsEntity.Key, cancellationToken: cancellationToken);
        return Parse(stored.HasValue ? stored.Value : null);
    }

    /// <summary>Stores the balance, removing the row <paramref name="replacesRowKey"/> when it moved.</summary>
    public Task SaveAsync(BalanceEntity entity, CancellationToken cancellationToken, string? replacesRowKey = null) =>
        WriteAsync(entity.PartitionKey, entity, replacesRowKey, cancellationToken);

    /// <summary>Deleting a row that isn't there does nothing.</summary>
    public Task DeleteAsync(string oid, string rowKey, CancellationToken cancellationToken) =>
        WriteAsync(oid, null, rowKey, cancellationToken);

    private async Task WriteAsync(
        string oid, BalanceEntity? saved, string? removedRowKey, CancellationToken cancellationToken)
    {
        if (removedRowKey == saved?.RowKey) removedRowKey = null;

        for (var attempt = 1; ; attempt++)
        {
            // The total is read first: a write landing after this changes its ETag, so the batch
            // below fails rather than building on rows that have since changed.
            var stored = await balances.GetEntityIfExistsAsync<BalanceTotalsEntity>(
                oid, BalanceTotalsEntity.Key, cancellationToken: cancellationToken);
            var totals = new Dictionary<string, decimal>(Parse(stored.HasValue ? stored.Value : null));
            var actions = new List<TableTransactionAction>();

            if (removedRowKey is not null)
            {
                var removed = await ReadRowAsync(oid, removedRowKey, cancellationToken);
                if (removed is not null)
                {
                    Add(totals, removed, -1);
                    actions.Add(new TableTransactionAction(TableTransactionActionType.Delete, removed, ETag.All));
                }
            }
            if (saved is not null)
            {
                var previous = await ReadRowAsync(oid, saved.RowKey, cancellationToken);
                if (previous is not null) Add(totals, previous, -1);
                Add(totals, saved, 1);
                actions.Add(new TableTransactionAction(TableTransactionActionType.UpsertReplace, saved));
            }
            if (actions.Count == 0) return;

            var totalsRow = new BalanceTotalsEntity
            {
                PartitionKey = oid,
                Totals = JsonSerializer.Serialize(totals.ToDictionary(
                    t => t.Key, t => t.Value.ToString(CultureInfo.InvariantCulture))),
            };
            actions.Add(stored.HasValue
                ? new TableTransactionAction(TableTransactionActionType.UpdateReplace, totalsRow, stored.Value!.ETag)
                : new TableTransactionAction(TableTransactionActionType.Add, totalsRow));

            try
            {
                await balances.SubmitTransactionAsync(actions, cancellationToken);
                return;
            }
            // Another write got in first: the total changed or was just created (412, 409), or a
            // row to delete has gone (404). Reading again gives this write the current state.
            catch (RequestFailedException e) when (attempt < MaxAttempts && e.Status is 404 or 409 or 412)
            {
                await Task.Delay(Random.Shared.Next(10, 50 * attempt), cancellationToken);
            }
        }
    }

    private async Task<BalanceEntity?> ReadRowAsync(string oid, string rowKey, CancellationToken cancellationToken)
    {
        var row = await balances.GetEntityIfExistsAsync<BalanceEntity>(
            oid, rowKey, cancellationToken: cancellationToken);
        return row.HasValue ? row.Value : null;
    }

    private static Dictionary<string, decimal> Parse(BalanceTotalsEntity? stored) =>
        stored is null
            ? []
            : JsonSerializer.Deserialize<Dictionary<string, string>>(stored.Totals)!.ToDictionary(
                t => t.Key, t => decimal.Parse(t.Value, CultureInfo.InvariantCulture));

    // A currency at zero is dropped, so one with no balances left doesn't stay in the total.
    private static void Add(Dictionary<string, decimal> totals, BalanceEntity row, int sign)
    {
        var total = totals.GetValueOrDefault(row.Currency) + sign * BalanceTypes.SignedAmount(row);
        if (total == 0) totals.Remove(row.Currency);
        else totals[row.Currency] = total;
    }
}
