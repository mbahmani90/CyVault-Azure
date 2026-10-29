<#
.SYNOPSIS
Adds Google as an identity provider to an Entra External ID tenant, or updates its client ID and secret.

.DESCRIPTION
Run once per tenant, after the tenant exists and before `azd up`. Google is a tenant-wide setting,
so every environment in that tenant shares it. The script saves the provider's ID as
ENTRA_GOOGLE_PROVIDER_ID in the azd environment, so Set-EntraApps.ps1 can put Google in the
environment's user flow. It also prints the redirect URIs to add to the Google OAuth client in the
Google Cloud console.

The client secret is asked for when not passed and is only sent to Entra: it is never written to
disk or to the azd environment.

The Azure CLI's sign-in can't manage identity providers, so this uses the Microsoft Graph
PowerShell module, which asks for consent to IdentityProvider.ReadWrite.All on first use:
    Install-Module Microsoft.Graph.Authentication -Scope CurrentUser

.EXAMPLE
./infra/entra/Set-GoogleProvider.ps1 -EnvironmentName debug -ClientId 1234-abc.apps.googleusercontent.com
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string] $EnvironmentName,
    # The Google OAuth client ID, e.g. 1234-abc.apps.googleusercontent.com.
    [Parameter(Mandatory)]
    [string] $ClientId,
    # Asked for when left out, so it doesn't end up in the shell history.
    [securestring] $ClientSecret
)

$ErrorActionPreference = 'Stop'

$tenantId = azd env get-value ENTRA_TENANT_ID -e $EnvironmentName 2>$null
if ($LASTEXITCODE -ne 0 -or -not $tenantId) {
    throw "Set the tenant first: azd env set ENTRA_TENANT_ID <tenant ID> -e $EnvironmentName"
}

if (-not (Get-Module -ListAvailable Microsoft.Graph.Authentication)) {
    throw 'Install the Microsoft Graph module first: Install-Module Microsoft.Graph.Authentication -Scope CurrentUser'
}
if (-not $ClientSecret) {
    $ClientSecret = Read-Host -AsSecureString 'Google client secret'
}
$secret = [Runtime.InteropServices.Marshal]::PtrToStringBSTR(
    [Runtime.InteropServices.Marshal]::SecureStringToBSTR($ClientSecret))

Connect-MgGraph -TenantId $tenantId -Scopes 'IdentityProvider.ReadWrite.All', 'Organization.Read.All' -NoWelcome

$body = @{
    '@odata.type'        = '#microsoft.graph.socialIdentityProvider'
    displayName          = 'Google'
    identityProviderType = 'Google'
    clientId             = $ClientId
    clientSecret         = $secret
}

$providers = (Invoke-MgGraphRequest GET 'v1.0/identity/identityProviders').value
$google = @($providers) | Where-Object { $_.identityProviderType -eq 'Google' } | Select-Object -First 1
if ($google) {
    Write-Host "Updating Google ($($google.id)) in tenant $tenantId"
    $body.Remove('identityProviderType')
    Invoke-MgGraphRequest PATCH "v1.0/identity/identityProviders/$($google.id)" -Body ($body | ConvertTo-Json) | Out-Null
} else {
    Write-Host "Adding Google to tenant $tenantId"
    $google = Invoke-MgGraphRequest POST 'v1.0/identity/identityProviders' -Body ($body | ConvertTo-Json)
}

azd env set ENTRA_GOOGLE_PROVIDER_ID $google.id -e $EnvironmentName | Out-Null
Write-Host "Saved ENTRA_GOOGLE_PROVIDER_ID=$($google.id) to azd environment '$EnvironmentName'"

# Google only sends users back to redirect URIs it knows; Entra uses these four.
$organization = (Invoke-MgGraphRequest GET 'v1.0/organization?$select=verifiedDomains').value[0]
$domain = (@($organization.verifiedDomains) | Where-Object { $_.isInitial }).name
$prefix = $domain -replace '\.onmicrosoft\.com$', ''
Write-Host ''
Write-Host 'In the Google Cloud console, add these to the OAuth client''s "Authorized redirect URIs":'
@(
    "https://$tenantId.ciamlogin.com/$tenantId/federation/oidc/accounts.google.com"
    "https://$tenantId.ciamlogin.com/$domain/federation/oidc/accounts.google.com"
    "https://$prefix.ciamlogin.com/$tenantId/federation/oauth2"
    "https://$prefix.ciamlogin.com/$domain/federation/oauth2"
) | ForEach-Object { Write-Host "  $_" }
