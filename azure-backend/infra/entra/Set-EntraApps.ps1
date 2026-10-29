<#
.SYNOPSIS
Applies entra.json to the Entra External ID tenant of one azd environment.

.DESCRIPTION
Creates what is missing and updates what differs: the "CyVault API" app registration with its
scopes, the "CyVault Mobile" app registration (redirect URI, native authentication, email claim,
API permissions), their service principals, the admin consent grants and the sign-up/sign-in
user flow. Each environment gets its own copies; objects that already exist keep their names.

The tenant is ENTRA_TENANT_ID in the azd environment (the production tenant when it's empty);
the debug environment has its own tenant, so its users are separate from production's. Google
sign-in uses the tenant's Google identity provider, ENTRA_GOOGLE_PROVIDER_ID, which
Set-GoogleProvider.ps1 adds and saves.

The apps are found through ENTRA_API_CLIENT_ID and ENTRA_MOBILE_CLIENT_ID in the azd
environment. When they are empty, new apps are created and their IDs saved there, so the next
`azd provision` hands the API's ID to the Function App.

azd runs this before every `azd provision` and `azd up` (see azure.yaml). Run it by hand with
-WhatIf to see what it would change without changing anything.

Needs the Azure CLI signed in to the environment's tenant:
    az login --tenant <ENTRA_TENANT_ID> --allow-no-subscriptions

.EXAMPLE
./infra/entra/Set-EntraApps.ps1 -WhatIf
#>
[CmdletBinding()]
param(
    # Report the changes without making them.
    [switch] $WhatIf,
    [string] $EnvironmentName = $env:AZURE_ENV_NAME,
    [string] $ApiClientId = $env:ENTRA_API_CLIENT_ID,
    [string] $MobileClientId = $env:ENTRA_MOBILE_CLIENT_ID,
    [string] $TenantId = $env:ENTRA_TENANT_ID,
    [string] $Instance = $env:ENTRA_INSTANCE,
    [string] $GoogleProviderId = $env:ENTRA_GOOGLE_PROVIDER_ID
)

$ErrorActionPreference = 'Stop'

$config = Get-Content -Raw -Path (Join-Path $PSScriptRoot 'entra.json') | ConvertFrom-Json
# An environment without its own tenant uses production's, with production's Google provider.
if (-not $TenantId) { $TenantId = $config.tenant.id }
if (-not $Instance) {
    if ($TenantId -ne $config.tenant.id) { throw 'Set ENTRA_INSTANCE too, e.g. https://cyvaultdebug.ciamlogin.com/' }
    $Instance = $config.tenant.instance
}
if (-not $GoogleProviderId -and $TenantId -eq $config.tenant.id) { $GoogleProviderId = $config.tenant.googleProviderId }
$tenantId = $TenantId
$graph = 'https://graph.microsoft.com'
# Microsoft Graph's own app ID: the same in every tenant.
$graphAppId = '00000003-0000-0000-c000-000000000000'

if (-not $EnvironmentName) {
    throw 'No azd environment: run through azd, or pass -EnvironmentName.'
}

$token = az account get-access-token --tenant $tenantId --resource $graph --query accessToken -o tsv
if ($LASTEXITCODE -ne 0 -or -not $token) {
    throw "Can't get a Microsoft Graph token for tenant $tenantId. Run: az login --tenant $tenantId --allow-no-subscriptions"
}

function Invoke-Graph {
    param([string] $Method, [string] $Path, $Body)

    $request = @{
        Method  = $Method
        Uri     = "$graph/$Path"
        Headers = @{ Authorization = "Bearer $token" }
    }
    if ($null -ne $Body) {
        $request.Body = [Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 20))
        $request.ContentType = 'application/json'
    }
    # A new app or service principal takes a few seconds to reach every replica; until then,
    # requests that refer to it fail with "does not exist".
    for ($attempt = 1; ; $attempt++) {
        try {
            return Invoke-RestMethod @request
        } catch {
            $message = "$($_.ErrorDetails.Message) $($_.Exception.Message)"
            if ($attempt -ge 6 -or $message -notmatch 'does not exist|not found|NotFound') { throw }
            Start-Sleep -Seconds (5 * $attempt)
        }
    }
}

# Writes go through here, so -WhatIf reports them instead.
function Invoke-Change {
    param([string] $Description, [string] $Method, [string] $Path, $Body)

    if ($WhatIf) {
        Write-Host "  [what-if] $Description"
        return $null
    }
    Write-Host "  $Description"
    return Invoke-Graph $Method $Path $Body
}

function Test-SameSet($a, $b) {
    ((@($a) | Sort-Object) -join '|') -eq ((@($b) | Sort-Object) -join '|')
}

function Get-ResourceAccessKey($requiredResourceAccess) {
    (@($requiredResourceAccess) | Sort-Object resourceAppId | ForEach-Object {
        "$($_.resourceAppId):" + ((@($_.resourceAccess) | ForEach-Object { $_.id } | Sort-Object) -join ',')
    }) -join ';'
}

function Get-App([string] $AppId) {
    if (-not $AppId) { return $null }
    Invoke-Graph GET "beta/applications(appId='$AppId')"
}

# New objects are named after the environment, e.g. "CyVault API (debug)".
function New-App([string] $BaseName) {
    $name = "$BaseName ($EnvironmentName)"
    Invoke-Change "Create app registration '$name'" POST 'v1.0/applications' @{
        displayName    = $name
        signInAudience = 'AzureADMyOrg'
    }
}

function Get-ServicePrincipal([string] $AppId) {
    $found = Invoke-Graph GET "v1.0/servicePrincipals?`$filter=appId eq '$AppId'"
    if (@($found.value).Count -gt 0) { return @($found.value)[0] }
    return Invoke-Change "Create the service principal for $AppId" POST 'v1.0/servicePrincipals' @{ appId = $AppId }
}

function Set-ConsentGrant($Client, $Resource, [string[]] $Scopes) {
    $grants = Invoke-Graph GET "v1.0/oauth2PermissionGrants?`$filter=clientId eq '$($Client.id)'"
    $grant = @($grants.value) | Where-Object { $_.resourceId -eq $Resource.id -and $_.consentType -eq 'AllPrincipals' } |
        Select-Object -First 1
    $scope = ($Scopes | Sort-Object) -join ' '
    if (-not $grant) {
        Invoke-Change "Grant admin consent for $($Resource.displayName): $scope" POST 'v1.0/oauth2PermissionGrants' @{
            clientId    = $Client.id
            consentType = 'AllPrincipals'
            resourceId  = $Resource.id
            scope       = $scope
        } | Out-Null
    } elseif (-not (Test-SameSet ($grant.scope -split ' ' | Where-Object { $_ }) $Scopes)) {
        Invoke-Change "Update admin consent for $($Resource.displayName): $scope" PATCH "v1.0/oauth2PermissionGrants/$($grant.id)" @{
            scope = $scope
        } | Out-Null
    }
}

Write-Host "Entra setup for azd environment '$EnvironmentName' in tenant $tenantId$(if ($WhatIf) { ' (what-if)' })"

# The user flow's sign-in methods: email and password is built in; Google is the tenant's own provider.
$providerIds = @(foreach ($type in $config.userFlow.identityProviders) {
    switch ($type) {
        'EmailPassword' { 'EmailPassword-OAUTH' }
        'Google' {
            if ($GoogleProviderId) { $GoogleProviderId }
            else { Write-Warning 'No Google identity provider in this tenant yet: run Set-GoogleProvider.ps1. The user flow gets email and password only.' }
        }
        default { throw "Unknown identity provider '$type' in entra.json." }
    }
})

$graphSp = Get-ServicePrincipal $graphAppId
function Get-GraphScopeAccess([string[]] $Values) {
    foreach ($value in $Values) {
        $scope = @($graphSp.oauth2PermissionScopes) | Where-Object { $_.value -eq $value }
        if (-not $scope) { throw "Microsoft Graph has no delegated permission '$value'." }
        @{ id = $scope.id; type = 'Scope' }
    }
}

# ---------- CyVault API ----------

Write-Host "CyVault API"
$api = Get-App $ApiClientId
if (-not $api) {
    $api = New-App $config.api.displayName
    # Read it back through beta, like an existing app, for the same properties.
    if ($api) { $api = Get-App $api.appId }
}

$apiSp = $null
if ($api) {
    # Keep each scope's ID: Entra won't change the ID of an enabled scope.
    $scopes = @(foreach ($wanted in $config.api.scopes) {
        $existing = @($api.api.oauth2PermissionScopes) | Where-Object { $_.value -eq $wanted.value } | Select-Object -First 1
        @{
            id                      = $(if ($existing) { $existing.id } else { [guid]::NewGuid().ToString() })
            value                   = $wanted.value
            type                    = 'Admin'
            isEnabled               = $true
            adminConsentDisplayName = $wanted.adminConsentDisplayName
            adminConsentDescription = $wanted.adminConsentDescription
        }
    })
    # Scopes that entra.json doesn't list stay as they are: removing one means disabling it first.
    $extraScopes = @(@($api.api.oauth2PermissionScopes) | Where-Object { $config.api.scopes.value -notcontains $_.value })
    foreach ($extra in $extraScopes) { Write-Warning "CyVault API has scope '$($extra.value)', which entra.json doesn't list; left as it is." }

    $apiAccess = @(@{ resourceAppId = $graphAppId; resourceAccess = @(Get-GraphScopeAccess $config.api.graphScopes) })
    $scopesMatch = @($scopes | Where-Object {
        $s = $_
        $current = @($api.api.oauth2PermissionScopes) | Where-Object { $_.value -eq $s.value }
        -not $current -or -not $current.isEnabled -or $current.type -ne 'Admin' -or
            $current.adminConsentDisplayName -ne $s.adminConsentDisplayName -or
            $current.adminConsentDescription -ne $s.adminConsentDescription
    }).Count -eq 0

    if (-not $scopesMatch -or
        -not (Test-SameSet $api.identifierUris "api://$($api.appId)") -or
        $api.api.requestedAccessTokenVersion -ne 2 -or
        (Get-ResourceAccessKey $api.requiredResourceAccess) -ne (Get-ResourceAccessKey $apiAccess)) {
        Invoke-Change 'Update the identifier URI, scopes and permissions' PATCH "v1.0/applications/$($api.id)" @{
            identifierUris         = @("api://$($api.appId)")
            api                    = @{
                requestedAccessTokenVersion = 2
                oauth2PermissionScopes      = @($scopes + $extraScopes)
            }
            requiredResourceAccess = $apiAccess
        } | Out-Null
        # Read it back for the scope IDs the mobile app's permissions refer to.
        if (-not $WhatIf) { $api = Get-App $api.appId }
    }
    $apiSp = Get-ServicePrincipal $api.appId
}

# ---------- CyVault Mobile ----------

Write-Host "CyVault Mobile"
$mobile = Get-App $MobileClientId
if (-not $mobile) {
    $mobile = New-App $config.mobile.displayName
    if ($mobile) { $mobile = Get-App $mobile.appId }
}

if ($mobile -and $api) {
    $apiScopeAccess = @(foreach ($s in $config.api.scopes) {
        $scope = @($api.api.oauth2PermissionScopes) | Where-Object { $_.value -eq $s.value }
        if ($scope) { @{ id = $scope.id; type = 'Scope' } }
    })
    $mobileAccess = @(
        @{ resourceAppId = $api.appId; resourceAccess = $apiScopeAccess },
        @{ resourceAppId = $graphAppId; resourceAccess = @(Get-GraphScopeAccess $config.mobile.graphScopes) }
    )
    $idTokenClaims = @(if ($mobile.optionalClaims) { @($mobile.optionalClaims.idToken) | ForEach-Object { $_.name } })

    if ($mobile.isFallbackPublicClient -ne $true -or
        $mobile.nativeAuthenticationApisEnabled -ne 'all' -or
        -not (Test-SameSet $mobile.publicClient.redirectUris $config.mobile.redirectUris) -or
        -not (Test-SameSet $idTokenClaims $config.mobile.idTokenOptionalClaims) -or
        (Get-ResourceAccessKey $mobile.requiredResourceAccess) -ne (Get-ResourceAccessKey $mobileAccess)) {
        # nativeAuthenticationApisEnabled is only in the beta API.
        Invoke-Change 'Update the redirect URI, native authentication, email claim and permissions' PATCH "beta/applications/$($mobile.id)" @{
            isFallbackPublicClient          = $true
            nativeAuthenticationApisEnabled = 'all'
            publicClient                    = @{ redirectUris = @($config.mobile.redirectUris) }
            optionalClaims                  = @{
                idToken = @($config.mobile.idTokenOptionalClaims | ForEach-Object { @{ name = $_; essential = $false } })
            }
            requiredResourceAccess          = $mobileAccess
        } | Out-Null
    }

    $mobileSp = Get-ServicePrincipal $mobile.appId
    if ($mobileSp -and $apiSp) {
        Set-ConsentGrant $mobileSp $apiSp @($config.api.scopes.value)
        Set-ConsentGrant $mobileSp $graphSp @($config.mobile.graphScopes)
    }
} elseif ($WhatIf) {
    Write-Host '  [what-if] Configure it once the app registrations exist'
}

# ---------- User flow ----------

Write-Host 'Sign-up and sign-in user flow'
$flows = @((Invoke-Graph GET 'beta/identity/authenticationEventsFlows').value)
$flow = $null
if ($mobile) {
    $flow = $flows | Where-Object {
        @($_.conditions.applications.includeApplications) | Where-Object { $_.appId -eq $mobile.appId }
    } | Select-Object -First 1
}

if ($flow) {
    # An app is in exactly one user flow; check it matches entra.json, but leave changes to the portal.
    $providers = @($flow.onAuthenticationMethodLoadStart.identityProviders | ForEach-Object { $_.id })
    $attributes = @($flow.onAttributeCollection.attributes | ForEach-Object { $_.id })
    if (-not (Test-SameSet $providers $providerIds) -or
        -not (Test-SameSet $attributes $config.userFlow.attributes.id) -or
        $flow.onInteractiveAuthFlowStart.isSignUpAllowed -ne $config.userFlow.isSignUpAllowed) {
        Write-Warning "User flow '$($flow.displayName)' differs from entra.json; update one to match the other."
    }
} else {
    $name = "$($config.userFlow.displayName) ($EnvironmentName)"
    $created = Invoke-Change "Create user flow '$name'" POST 'beta/identity/authenticationEventsFlows' @{
        '@odata.type'                   = '#microsoft.graph.externalUsersSelfServiceSignUpEventsFlow'
        displayName                     = $name
        onInteractiveAuthFlowStart      = @{
            '@odata.type'   = '#microsoft.graph.onInteractiveAuthFlowStartExternalUsersSelfServiceSignUp'
            isSignUpAllowed = $config.userFlow.isSignUpAllowed
        }
        onAuthenticationMethodLoadStart = @{
            '@odata.type'     = '#microsoft.graph.onAuthenticationMethodLoadStartExternalUsersSelfServiceSignUp'
            identityProviders = @($providerIds | ForEach-Object { @{ id = $_ } })
        }
        onAttributeCollection           = @{
            '@odata.type'           = '#microsoft.graph.onAttributeCollectionExternalUsersSelfServiceSignUp'
            attributes              = @($config.userFlow.attributes | ForEach-Object { @{ id = $_.id } })
            attributeCollectionPage = @{
                views = @(@{
                    inputs = @($config.userFlow.attributes | ForEach-Object {
                        @{
                            attribute        = $_.id
                            label            = $_.label
                            inputType        = 'text'
                            hidden           = $_.hidden
                            editable         = $_.editable
                            writeToDirectory = $true
                            required         = $_.required
                            validationRegEx  = $_.validationRegEx
                        }
                    })
                })
            }
        }
        onUserCreateStart               = @{
            '@odata.type'    = '#microsoft.graph.onUserCreateStartExternalUsersSelfServiceSignUp'
            userTypeToCreate = 'member'
        }
    }
    if ($created -and $mobile) {
        Invoke-Change "Add CyVault Mobile to '$name'" POST "beta/identity/authenticationEventsFlows/$($created.id)/conditions/applications/includeApplications" @{
            '@odata.type' = '#microsoft.graph.authenticationConditionApplication'
            appId         = $mobile.appId
        } | Out-Null
    } elseif ($WhatIf) {
        Write-Host "  [what-if] Add CyVault Mobile to '$name'"
    }
}

# ---------- azd environment ----------

if (-not $WhatIf) {
    # Bicep reads these through main.parameters.json; the app's MSAL config needs the mobile ID.
    azd env set ENTRA_TENANT_ID $tenantId | Out-Null
    azd env set ENTRA_INSTANCE $Instance | Out-Null
    azd env set ENTRA_API_CLIENT_ID $api.appId | Out-Null
    azd env set ENTRA_MOBILE_CLIENT_ID $mobile.appId | Out-Null
    Write-Host "Saved to azd environment '$EnvironmentName': API $($api.appId), mobile $($mobile.appId)"
}
