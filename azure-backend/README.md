# CyVault Azure backend

The Azure Functions API behind the app's profile photo and balances: `.NET 10` isolated Functions on
Flex Consumption, Blob and Table Storage reached through a Managed Identity, and Microsoft Entra
External ID for sign-in. Everything is code (Bicep and PowerShell) and deploys with the Azure
Developer CLI (`azd`). How the API, its tokens and its storage access work is described in the
[main README](../README.md#profile-photo-api-function-app-access-to-blob-storage).

## Requirements

- .NET 10 SDK
- Azure Functions Core Tools 4
- Azure CLI and Azure Developer CLI (`azd`)
- Azurite, the local storage emulator (`npm install -g azurite`)
- PowerShell 7 (`pwsh`) for the Entra hooks, and for Google sign-in the Microsoft Graph module
  (`Install-Module Microsoft.Graph.Authentication -Scope CurrentUser`)

## Environments

Each azd environment is a full copy of the backend: its own Entra External ID tenant (so its own
users), app registrations, user flow, Function App and storage.

| azd environment | Used by | Tenant (users) | Entra apps | Azure resources |
|---|---|---|---|---|
| `dev` | release builds (production) | `cyvault.onmicrosoft.com` | `CyVault API`, `CyVault Mobile`, user flow `SignUpSignIn` | `rg-cyvault-dev` |
| `debug` | debug builds | `cyvaultdebug.onmicrosoft.com` | `CyVault API (debug)`, `CyVault Mobile (debug)`, user flow `SignUpSignIn (debug)` | `rg-cyvault-debug` |

The Android app picks its environment by build type. Debug builds sign in to the debug tenant
(`androidApp/src/debug/res/raw/auth_config_native_auth.json`) and call the debug API (the
`CyVaultApiConfig` that `androidApp/src/debug` provides through Koin); release builds use
production (`src/main`'s MSAL config and `CyVaultApiConfig.Production`).

Each environment's backend is all code:

- **Tenant:** [`infra/tenant/tenant.bicep`](infra/tenant/tenant.bicep), one `.bicepparam` per tenant.
  Deployed once, outside azd; azd never deletes a tenant.
- **Google sign-in in the tenant:** [`Set-GoogleProvider.ps1`](infra/entra/Set-GoogleProvider.ps1),
  once per tenant. It asks for the Google client secret and sends it only to Entra.
- **App registrations, consent and user flow:** [`entra.json`](infra/entra/entra.json), applied by
  [`Set-EntraApps.ps1`](infra/entra/Set-EntraApps.ps1), which azd runs before every `azd provision`
  and `azd up`. It creates what's missing, updates what differs, and saves the app IDs for Bicep.
  `-WhatIf` shows what it would change without changing anything.
- **Function App and storage:** [`infra/main.bicep`](infra/main.bicep), wired to the environment's
  tenant through the Function App's `AzureAd__*` settings.

## Run locally

From `src/CyVault.Api`:

```bash
cp local.settings.sample.json local.settings.json
```

Put the API's client ID in `AzureAd__ClientId` in `local.settings.json`, which is git-ignored. To try
sign-out locally, create a client secret in the CyVault API registration (**Certificates & secrets**) and
put it in `Graph__ClientSecret`. Start Azurite (the local Blob Storage emulator) in one terminal and the Function in another:

```bash
azurite --silent --location ../../.azurite
```

```bash
func start
```

On Windows PowerShell, use `azurite.cmd` if script execution is disabled. The API listens on
`http://localhost:7158/api/me/avatar` and answers `401` without a valid access token.

## Deploy to Azure

From this folder (`azure-backend/`). To create the **debug** environment from nothing:

1. Create its tenant (prints `tenantId` and `instance`):

   ```bash
   az deployment group create -g cyvault-rg -f infra/tenant/tenant.bicep -p infra/tenant/debug.bicepparam
   ```

2. Sign the Azure CLI in to the new tenant, and tell azd about it:

   ```bash
   az login --tenant <tenantId> --allow-no-subscriptions
   ```

   ```bash
   azd env new debug
   ```

   ```bash
   azd env set ENTRA_TENANT_ID <tenantId>
   ```

   ```bash
   azd env set ENTRA_INSTANCE https://cyvaultdebug.ciamlogin.com/
   ```

3. Add Google sign-in with your Google OAuth client ID. It asks for the secret, then prints the
   redirect URIs to add to that client in the Google Cloud console:

   ```bash
   pwsh ./infra/entra/Set-GoogleProvider.ps1 -EnvironmentName debug -ClientId <Google client ID>
   ```

4. Set the region (some, like West Europe, refuse new resources), create the Entra apps once on their
   own (on a new environment `azd up` asks for `entraApiClientId` before it runs hooks), then deploy:

   ```bash
   azd env set AZURE_LOCATION northeurope
   ```

   ```bash
   azd hooks run preprovision
   ```

   ```bash
   azd up
   ```

An environment whose Entra apps already exist (like `dev`) has their IDs saved in the azd environment
(`ENTRA_API_CLIENT_ID`, `ENTRA_MOBILE_CLIENT_ID`), so the hook updates them instead of creating new
ones; without `ENTRA_TENANT_ID` it uses the production tenant.

`azd up` asks for a subscription and region, creates the resources in `infra/` (Flex Consumption
Function App, two storage accounts with shared keys disabled, Application Insights, the Function's
Managed Identity and its role assignments) and deploys the code. It prints `API_BASE_URL` at the end.
The signed-in developer also gets blob access to the `avatars` container, so the Function can run
locally against the real storage through `az login`.

## Server-side sign-out

`POST /me/signout` revokes the user's Entra refresh tokens on **all** devices through Microsoft Graph's
`revokeSignInSessions`. Access tokens already issued stay valid until they expire (about an hour). The
Function calls Graph as the CyVault API app; the mobile app gets no Graph permission. How the flow works,
with a diagram, is in the [main README](../README.md#server-side-sign-out-revoking-the-users-entra-sessions).
These steps are manual for now, once per environment, in its tenant:

1. In the **CyVault API** registration: **API permissions → Add a permission → Microsoft Graph →
   Application permissions → `User.RevokeSessions.All`**, then grant admin consent.
   `Set-EntraApps.ps1` doesn't know about this permission yet, so after it runs the portal may list it
   under "Other permissions granted"; the consent, which is what counts, stays.
2. After the first `azd up`, let the Function call Graph without a secret: in the same registration,
   **Certificates & secrets → Federated credentials → Add credential → Other issuer**:

   - Issuer: `https://login.microsoftonline.com/<AZURE_TENANT_ID>/v2.0`
   - Subject identifier: `<FUNCTION_PRINCIPAL_ID>`
   - Audience: `api://AzureADTokenExchange`

   Both values are in `azd env get-values`. The Function's Managed Identity then signs in as the
   CyVault API app. If the tenant doesn't accept the federated credential, set the
   `Graph__ClientSecret` app setting on the Function App instead.
