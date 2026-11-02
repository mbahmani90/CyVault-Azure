using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text.Json.Serialization;
using Azure.Core;

namespace CyVault.Api.Push;

/// <summary>
/// Where the Function's Google identity comes from: the Google project, its workload identity
/// pool provider that trusts Azure, the service account it acts as, and the audience Google
/// expects on the Azure token.
/// </summary>
public sealed record PushOptions(
    string ProjectId,
    string ProjectNumber,
    string PoolId,
    string ProviderId,
    string ServiceAccount,
    string Audience);

/// <summary>
/// Gets an access token for FCM without any Google key, through workload identity federation:
/// the Function's Managed Identity token from Entra is exchanged at Google STS for a federated
/// token, which then acts as the FCM service account. Google only trusts this one identity (see
/// the README). The token lasts an hour and is reused until shortly before it ends.
/// </summary>
public sealed class GoogleAccessToken(IHttpClientFactory httpClients, TokenCredential azureCredential, PushOptions options)
{
    private const string MessagingScope = "https://www.googleapis.com/auth/firebase.messaging";
    private static readonly TimeSpan RefreshMargin = TimeSpan.FromMinutes(5);

    private readonly SemaphoreSlim _lock = new(1, 1);
    private string? _token;
    private DateTimeOffset _expiresAt;

    public async Task<string> GetAsync(CancellationToken cancellationToken)
    {
        if (_token is not null && DateTimeOffset.UtcNow < _expiresAt - RefreshMargin) return _token;

        await _lock.WaitAsync(cancellationToken);
        try
        {
            // Another call may have refreshed it while this one waited.
            if (_token is not null && DateTimeOffset.UtcNow < _expiresAt - RefreshMargin) return _token;

            var http = httpClients.CreateClient();
            var azureToken = await azureCredential.GetTokenAsync(
                new TokenRequestContext([$"{options.Audience}/.default"]), cancellationToken);
            var federated = await ExchangeAsync(http, azureToken.Token, cancellationToken);
            (_token, _expiresAt) = await ImpersonateAsync(http, federated, cancellationToken);
            return _token;
        }
        finally
        {
            _lock.Release();
        }
    }

    // STS checks the Azure token against the pool provider (issuer, audience) and returns a
    // short-lived federated token for the identity in its subject.
    private async Task<string> ExchangeAsync(HttpClient http, string azureToken, CancellationToken cancellationToken)
    {
        var provider = $"//iam.googleapis.com/projects/{options.ProjectNumber}/locations/global/" +
            $"workloadIdentityPools/{options.PoolId}/providers/{options.ProviderId}";
        using var response = await http.PostAsJsonAsync("https://sts.googleapis.com/v1/token", new
        {
            audience = provider,
            grantType = "urn:ietf:params:oauth:grant-type:token-exchange",
            requestedTokenType = "urn:ietf:params:oauth:token-type:access_token",
            scope = "https://www.googleapis.com/auth/cloud-platform",
            subjectTokenType = "urn:ietf:params:oauth:token-type:jwt",
            subjectToken = azureToken,
        }, cancellationToken);
        await EnsureSuccessAsync(response, "Google STS", cancellationToken);

        var body = await response.Content.ReadFromJsonAsync<StsResponse>(cancellationToken);
        return body?.AccessToken ?? throw new InvalidOperationException("Google STS returned no token.");
    }

    // The federated token may act as the service account, which alone may send pushes.
    private async Task<(string Token, DateTimeOffset ExpiresAt)> ImpersonateAsync(
        HttpClient http, string federatedToken, CancellationToken cancellationToken)
    {
        using var request = new HttpRequestMessage(HttpMethod.Post,
            $"https://iamcredentials.googleapis.com/v1/projects/-/serviceAccounts/{options.ServiceAccount}:generateAccessToken")
        {
            Content = JsonContent.Create(new { scope = new[] { MessagingScope }, lifetime = "3600s" }),
        };
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", federatedToken);
        using var response = await http.SendAsync(request, cancellationToken);
        await EnsureSuccessAsync(response, "Google IAM Credentials", cancellationToken);

        var body = await response.Content.ReadFromJsonAsync<GenerateAccessTokenResponse>(cancellationToken);
        if (body?.AccessToken is null) throw new InvalidOperationException("Google IAM Credentials returned no token.");
        return (body.AccessToken, body.ExpireTime);
    }

    // Google's error body says which check failed; it holds no token.
    private static async Task EnsureSuccessAsync(HttpResponseMessage response, string service, CancellationToken cancellationToken)
    {
        if (response.IsSuccessStatusCode) return;
        var error = await response.Content.ReadAsStringAsync(cancellationToken);
        throw new HttpRequestException($"{service} returned {(int)response.StatusCode}: {error}", null, response.StatusCode);
    }

    private sealed record StsResponse([property: JsonPropertyName("access_token")] string? AccessToken);

    private sealed record GenerateAccessTokenResponse(string? AccessToken, DateTimeOffset ExpireTime);
}
