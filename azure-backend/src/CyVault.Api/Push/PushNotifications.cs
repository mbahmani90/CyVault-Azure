using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using Azure;
using Azure.Data.Tables;
using CyVault.Api.Device;
using Microsoft.Extensions.Logging;

namespace CyVault.Api.Push;

/// <summary>
/// Sends pushes through Firebase Cloud Messaging (HTTP v1) to every enrolled phone of a user.
/// Pushes carry data only: the app turns them into a notification in the phone's language, so
/// no user-facing text lives on the server. A push is best effort and never fails the request
/// that caused it. Without a Google project configured (local runs), nothing is sent.
/// </summary>
public sealed class PushNotifications(
    IHttpClientFactory httpClients,
    DeviceTable devices,
    GoogleAccessToken? googleToken,
    PushOptions? options,
    ILogger<PushNotifications> logger)
{
    public const string TransferReceivedType = "transfer_received";

    /// <summary>Tells the receiver's phones that money arrived from <paramref name="senderPhone"/>.</summary>
    public Task TransferReceivedAsync(
        string receiverOid, string transferId, string amount, string currency, string senderPhone,
        CancellationToken cancellationToken) =>
        SendToUserAsync(receiverOid, new Dictionary<string, string>
        {
            ["type"] = TransferReceivedType,
            ["transferId"] = transferId,
            ["amount"] = amount,
            ["currency"] = currency,
            ["from"] = senderPhone,
        }, cancellationToken);

    private async Task SendToUserAsync(string oid, Dictionary<string, string> data, CancellationToken cancellationToken)
    {
        if (googleToken is null || options is null) return;
        try
        {
            string? accessToken = null;
            await foreach (var device in devices.Client.QueryAsync<DeviceEntity>(
                e => e.PartitionKey == oid, cancellationToken: cancellationToken))
            {
                if (device.RevokedAt is not null || device.PushToken.Length == 0) continue;
                accessToken ??= await googleToken.GetAsync(cancellationToken);
                await SendAsync(device, accessToken, data, cancellationToken);
            }
        }
        catch (Exception e) when (e is not OperationCanceledException)
        {
            logger.LogWarning(e, "Couldn't send a {Type} push to {ObjectId}", data["type"], oid);
        }
    }

    private async Task SendAsync(
        DeviceEntity device, string accessToken, Dictionary<string, string> data, CancellationToken cancellationToken)
    {
        using var request = new HttpRequestMessage(HttpMethod.Post,
            $"https://fcm.googleapis.com/v1/projects/{options!.ProjectId}/messages:send")
        {
            // High priority wakes the app to show the notification at once, even in Doze.
            Content = JsonContent.Create(new
            {
                message = new { token = device.PushToken, data, android = new { priority = "high" } },
            }),
        };
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", accessToken);
        using var response = await httpClients.CreateClient().SendAsync(request, cancellationToken);
        if (response.IsSuccessStatusCode) return;

        // 404 (UNREGISTERED): the app was uninstalled or the token replaced. Stop sending to it.
        if (response.StatusCode == HttpStatusCode.NotFound)
        {
            device.PushToken = "";
            await devices.Client.UpdateEntityAsync(device, ETag.All, TableUpdateMode.Merge, cancellationToken);
            logger.LogInformation("Dropped the expired push token of device {Id}", device.RowKey);
            return;
        }
        var error = await response.Content.ReadAsStringAsync(cancellationToken);
        logger.LogWarning("FCM returned {Status} for device {Id}: {Error}", (int)response.StatusCode, device.RowKey, error);
    }
}
