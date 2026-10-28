using Azure.Identity;
using CyVault.Api.Auth;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Azure.Functions.Worker;
using Microsoft.Extensions.Logging;

namespace CyVault.Api.Session;

/// <summary>
/// Server-side sign-out. The app calls this before clearing its local tokens, so a refresh token
/// copied off the device stops working too. Like the avatar routes, the user comes from the
/// validated access token, so a caller can only ever sign out themselves.
/// </summary>
public sealed class SessionFunctions(SignInSessions sessions, ILogger<SessionFunctions> logger)
{
    [Function("SignOut")]
    public async Task<IActionResult> SignOut(
        [HttpTrigger(AuthorizationLevel.Anonymous, "post", Route = "me/signout")] HttpRequest req,
        CancellationToken cancellationToken)
    {
        // Profile.ReadWrite is the only scope the app requests; any valid CyVault API token
        // is enough to prove who is signing out.
        var caller = await CallerAuthentication.AuthenticateAsync(req.HttpContext, CallerAuthentication.ProfileScope);
        if (caller.Failure is not null) return caller.Failure;

        try
        {
            await sessions.RevokeAsync(caller.ObjectId!, cancellationToken);
        }
        catch (Exception e) when (e is HttpRequestException or AuthenticationFailedException)
        {
            logger.LogError(e, "Couldn't revoke sign-in sessions for {ObjectId}", caller.ObjectId);
            return new StatusCodeResult(StatusCodes.Status502BadGateway);
        }

        logger.LogInformation("Revoked sign-in sessions for {ObjectId}", caller.ObjectId);
        return new NoContentResult();
    }
}
