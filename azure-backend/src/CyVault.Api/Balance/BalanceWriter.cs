using System.Globalization;
using System.Text.Json;
using Azure;
using Azure.Data.Tables;

namespace CyVault.Api.Balance;

/// <summary>
/// One user's total per currency and oldest balance date: a row in their own partition, so a single
/// batch can change it together with a balance. Balance IDs can't contain '~', so no route can reach
/// this row.
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

    /// <summary>When the user's oldest balance happened; null when they have none.</summary>
    public DateTimeOffset? OldestDate { get; set; }

    /// <summary>
    /// How these totals were made. One older than <see cref="CurrentVersion"/> was started before
    /// the balances already stored were counted, so it's rebuilt from them.
    /// </summary>
    public int Version { get; set; }

    public const int CurrentVersion = 1;
}

/// <summary>What a user's balances add up to per currency, and when the oldest one happened.</summary>
public sealed record BalanceTotals(IReadOnlyDictionary<string, decimal> Amounts, DateTimeOffset? OldestDate);

/// <summary>
/// The one place that changes a user's balance rows, so their totals can't fall behind. Every write
/// is a batch of the balance and the totals, with the difference worked out from the row already
/// stored, so sending the same save twice changes nothing the second time.
/// </summary>
public sealed class BalanceWriter(TableClient balances)
{
    // Enough for the sample balances, which save 10 at a time for one user.
    private const int MaxAttempts = 15;

    /// <summary>A batch holds 100 rows: these and the totals.</summary>
    public const int MaxDeletesPerBatch = 99;

    /// <summary>Everything in the partition except the totals row, as the table orders it.</summary>
    public static string RowsFilter(string oid) =>
        TableClient.CreateQueryFilter($"PartitionKey eq {oid} and RowKey lt {"~"}");

    public async Task<BalanceTotals> GetTotalsAsync(string oid, CancellationToken cancellationToken)
    {
        var stored = await balances.GetEntityIfExistsAsync<BalanceTotalsEntity>(
            oid, BalanceTotalsEntity.Key, cancellationToken: cancellationToken);
        var row = stored.HasValue ? stored.Value : null;
        if (row?.Version >= BalanceTotalsEntity.CurrentVersion) return new BalanceTotals(Parse(row), row.OldestDate);

        var rebuilt = await RebuildAsync(oid, cancellationToken);
        try
        {
            await StoreAsync(oid, rebuilt, row, cancellationToken);
        }
        // A write got in first and stored totals of its own; the next read finds them.
        catch (RequestFailedException e) when (e.Status is 409 or 412)
        {
        }
        return rebuilt;
    }

    /// <summary>Stores the balance, removing the row <paramref name="replacesRowKey"/> when it moved.</summary>
    public Task SaveAsync(BalanceEntity entity, CancellationToken cancellationToken, string? replacesRowKey = null) =>
        WriteAsync(entity.PartitionKey, entity, replacesRowKey is null ? [] : [replacesRowKey], cancellationToken);

    /// <summary>Deleting a row that isn't there does nothing.</summary>
    public Task DeleteAsync(string oid, string rowKey, CancellationToken cancellationToken) =>
        WriteAsync(oid, null, [rowKey], cancellationToken);

    /// <summary>
    /// Deletes up to <see cref="MaxDeletesPerBatch"/> rows in one batch with the totals: all of them
    /// or none. Rows that aren't there are skipped.
    /// </summary>
    public Task DeleteManyAsync(string oid, IReadOnlyCollection<string> rowKeys, CancellationToken cancellationToken)
    {
        ArgumentOutOfRangeException.ThrowIfGreaterThan(rowKeys.Count, MaxDeletesPerBatch);
        return WriteAsync(oid, null, rowKeys, cancellationToken);
    }

    private async Task WriteAsync(
        string oid, BalanceEntity? saved, IReadOnlyCollection<string> removedRowKeys, CancellationToken cancellationToken)
    {
        // A batch can't name a row twice, and the saved row isn't removed.
        var removedKeys = removedRowKeys.Where(key => key != saved?.RowKey).Distinct().ToList();

        for (var attempt = 1; ; attempt++)
        {
            // The totals are read first: a write landing after this changes their ETag, so the
            // batch below fails rather than building on rows that have since changed.
            var stored = await balances.GetEntityIfExistsAsync<BalanceTotalsEntity>(
                oid, BalanceTotalsEntity.Key, cancellationToken: cancellationToken);
            var storedRow = stored.HasValue ? stored.Value : null;
            // Totals missing or made before every balance was counted start from the rows instead.
            // The batch still checks the stored row, so a write landing during the scan is caught.
            var current = storedRow?.Version >= BalanceTotalsEntity.CurrentVersion
                ? new BalanceTotals(Parse(storedRow), storedRow.OldestDate)
                : await RebuildAsync(oid, cancellationToken);
            var amounts = new Dictionary<string, decimal>(current.Amounts);
            var actions = new List<TableTransactionAction>();
            var replacedRows = new List<BalanceEntity>();

            foreach (var removedKey in removedKeys)
            {
                var removed = await ReadRowAsync(oid, removedKey, cancellationToken);
                if (removed is not null)
                {
                    Add(amounts, removed, -1);
                    replacedRows.Add(removed);
                    actions.Add(new TableTransactionAction(TableTransactionActionType.Delete, removed, ETag.All));
                }
            }
            if (saved is not null)
            {
                var previous = await ReadRowAsync(oid, saved.RowKey, cancellationToken);
                if (previous is not null)
                {
                    Add(amounts, previous, -1);
                    replacedRows.Add(previous);
                }
                Add(amounts, saved, 1);
                actions.Add(new TableTransactionAction(TableTransactionActionType.UpsertReplace, saved));
            }
            if (actions.Count == 0) return;

            var oldest = await OldestDateAfterAsync(oid, current.OldestDate, saved, replacedRows, cancellationToken);
            actions.Add(TotalsAction(ToEntity(oid, new BalanceTotals(amounts, oldest)), storedRow));

            try
            {
                await balances.SubmitTransactionAsync(actions, cancellationToken);
                return;
            }
            // Another write got in first: the totals changed or were just created (412, 409), or a
            // row to delete has gone (404). Reading again gives this write the current state.
            catch (RequestFailedException e) when (attempt < MaxAttempts && e.Status is 404 or 409 or 412)
            {
                await Task.Delay(Random.Shared.Next(10, 50 * attempt), cancellationToken);
            }
        }
    }

    private async Task<DateTimeOffset?> OldestDateAfterAsync(
        string oid,
        DateTimeOffset? oldest,
        BalanceEntity? saved,
        List<BalanceEntity> replacedRows,
        CancellationToken cancellationToken)
    {
        // Nothing can be older than a date at or before the current oldest.
        if (saved?.Date is { } savedDate && (oldest is null || savedDate <= oldest)) return savedDate;
        if (!replacedRows.Any(row => row.Date <= oldest)) return oldest;

        // The oldest balance went or moved later. Only a scan finds the next one, as the table reads
        // newest first; it's the one write that reads the user's rows.
        var nextOldest = saved?.Date;
        var replacedKeys = replacedRows.Select(row => row.RowKey).ToHashSet();
        await foreach (var row in balances.QueryAsync<BalanceEntity>(
            RowsFilter(oid), select: ["RowKey", "Date"], cancellationToken: cancellationToken))
        {
            if (row.Date is not { } date || replacedKeys.Contains(row.RowKey)) continue;
            if (nextOldest is null || date < nextOldest) nextOldest = date;
        }
        return nextOldest;
    }

    /// <summary>The totals worked out from every balance row the user has.</summary>
    private async Task<BalanceTotals> RebuildAsync(string oid, CancellationToken cancellationToken)
    {
        var amounts = new Dictionary<string, decimal>();
        DateTimeOffset? oldest = null;
        await foreach (var row in balances.QueryAsync<BalanceEntity>(RowsFilter(oid), cancellationToken: cancellationToken))
        {
            Add(amounts, row, 1);
            if (row.Date is { } date && (oldest is null || date < oldest)) oldest = date;
        }
        return new BalanceTotals(amounts, oldest);
    }

    private Task StoreAsync(
        string oid, BalanceTotals totals, BalanceTotalsEntity? storedRow, CancellationToken cancellationToken) =>
        balances.SubmitTransactionAsync([TotalsAction(ToEntity(oid, totals), storedRow)], cancellationToken);

    private static BalanceTotalsEntity ToEntity(string oid, BalanceTotals totals) => new()
    {
        PartitionKey = oid,
        Totals = JsonSerializer.Serialize(totals.Amounts.ToDictionary(
            t => t.Key, t => t.Value.ToString(CultureInfo.InvariantCulture))),
        OldestDate = totals.OldestDate,
        Version = BalanceTotalsEntity.CurrentVersion,
    };

    // Replaces only the row that was read, or adds one only if none was there.
    private static TableTransactionAction TotalsAction(BalanceTotalsEntity totals, BalanceTotalsEntity? storedRow) =>
        storedRow is not null
            ? new TableTransactionAction(TableTransactionActionType.UpdateReplace, totals, storedRow.ETag)
            : new TableTransactionAction(TableTransactionActionType.Add, totals);

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
    private static void Add(Dictionary<string, decimal> amounts, BalanceEntity row, int sign)
    {
        var total = amounts.GetValueOrDefault(row.Currency) + sign * BalanceTypes.SignedAmount(row);
        if (total == 0) amounts.Remove(row.Currency);
        else amounts[row.Currency] = total;
    }
}
