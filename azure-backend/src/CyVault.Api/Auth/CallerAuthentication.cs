using Microsoft.AspNetCore.Authentication;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Identity.Web;

namespace CyVault.Api.Auth;

/// <summary>The signed-in user behind a request, or the response to send when there isn't a valid one.</summary>
public sealed record CallerResult(string? ObjectId, IActionResult? Failure);

public static class CallerAuthentication
{
    public const string ProfileScope = "Profile.ReadWrite";
    public const string BalanceScope = "Balance.ReadWrite";

    /// <summary>
    /// Validates the request's bearer token and returns the user's Entra object ID (oid).
    /// Every per-user path or key must be built from this value, never from request input.
    /// </summary>
    public static async Task<CallerResult> AuthenticateAsync(HttpContext http, string requiredScope)
    {
        var result = await http.AuthenticateAsync(JwtBearerDefaults.AuthenticationScheme);
        if (!result.Succeeded || result.Principal is null)
        {
            return new CallerResult(null, new UnauthorizedResult());
        }

        var user = result.Principal;
        var scopes = (user.FindFirst(ClaimConstants.Scp) ?? user.FindFirst(ClaimConstants.Scope))?.Value
            .Split(' ', StringSplitOptions.RemoveEmptyEntries) ?? [];
        if (!scopes.Contains(requiredScope))
        {
            return new CallerResult(null, new ObjectResult($"The token lacks the '{requiredScope}' scope.") { StatusCode = StatusCodes.Status403Forbidden });
        }

        // oid is a GUID in a validated token; checking keeps it safe to use in blob paths.
        var objectId = user.GetObjectId();
        if (!Guid.TryParse(objectId, out _))
        {
            return new CallerResult(null, new UnauthorizedResult());
        }

        return new CallerResult(objectId, null);
    }
}
