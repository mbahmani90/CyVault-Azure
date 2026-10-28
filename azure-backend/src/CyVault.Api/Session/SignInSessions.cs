using System.Net.Http.Headers;
using Azure.Core;

namespace CyVault.Api.Session;

/// <summary>
/// Revokes a user's Entra sign-in sessions through Microsoft Graph, as the CyVault API app itself
/// (application permission User.RevokeSessions.All). The mobile app holds no Graph permission.
/// </summary>
public sealed class SignInSessions(IHttpClientFactory httpClients, TokenCredential graphCredential)
{
    private static readonly string[] GraphScopes = ["https://graph.microsoft.com/.default"];

    /// <summary>
    /// Invalidates every refresh token the user holds, on all devices. Access tokens already
    /// issued stay valid until they expire.
    /// </summary>
    public async Task RevokeAsync(string objectId, CancellationToken cancellationToken)
    {
        var token = await graphCredential.GetTokenAsync(new TokenRequestContext(GraphScopes), cancellationToken);

        using var request = new HttpRequestMessage(
            HttpMethod.Post, $"https://graph.microsoft.com/v1.0/users/{objectId}/revokeSignInSessions");
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", token.Token);

        using var response = await httpClients.CreateClient().SendAsync(request, cancellationToken);
        response.EnsureSuccessStatusCode();
    }
}
