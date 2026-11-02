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

### Check the tenant before every deploy

The azd environment files (`.azure/<env>/.env`) are git-ignored, so each machine has its own copy,
and nothing stops a `debug` environment from holding production's Entra values. When it does,
`azd up -e debug` changes the **production** tenant's app registrations and points the debug Function
App at the production API, so every request from a debug build fails with `401`. This happened once:
the debug env file had lost its debug values.

Before `azd up` or `azd provision`, check the environment's Entra values:

```bash
azd env get-values -e debug | grep ENTRA_
```

They must match the environment's row in the table above, and the Android build that uses it:

| Value | `debug` | `dev` (production) |
|---|---|---|
| `ENTRA_TENANT_ID` | `716c5a4a-3127-449c-8f78-fc715bd7ac06` | `e5cb5788-11bc-499b-82f2-095c6f201b40` |
| `ENTRA_INSTANCE` | `https://cyvaultdebug.ciamlogin.com/` | `https://cyvault.ciamlogin.com/` |
| `ENTRA_API_CLIENT_ID` | `7b114a03-…` (debug build's `appIdUri` in `androidApp/src/debug`) | `61a46c01-…` |
| `ENTRA_MOBILE_CLIENT_ID` | `990b363b-…` (`client_id` in `androidApp/src/debug/res/raw`) | `63b77443-…` (`client_id` in `androidApp/src/main/res/raw`) |

Then dry-run the Entra hook: its first line names the tenant it will change, and it lists every
change without making it:

```bash
pwsh ./infra/entra/Set-EntraApps.ps1 -EnvironmentName debug -WhatIf
```

If a value is wrong, fix it with `azd env set -e <env> <NAME> <value>` before deploying. After a
deploy, the Function App's `AzureAd__TenantId` and `AzureAd__ClientId` settings show which tenant and
API it trusts.

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

## Push notifications

When a transfer completes, the Function sends a push to each phone of the receiver that saved its
token, through Firebase Cloud Messaging (HTTP v1). The push carries data only (`type`, `transferId`,
`amount`, `currency`, `sender`); the app turns it into a notification. After each sign-in the app
saves its FCM token with `PUT /me/push-tokens` (`{ "token": "…" }`), and on sign-out removes it
with `DELETE` on the same route and body. Tokens live in the `pushtokens` table (`PartitionKey` =
the user's `oid`, `RowKey` = the token's SHA-256), apart from the devices table, so a phone without
a fingerprint or face unlock, which has no signing key, still gets pushes. A token belongs to one
user at a time: saving it removes it from any other account that used the same phone. A push never
fails the transfer, and a token FCM no longer knows is dropped.

No Google key exists: Google trusts the Function's Managed Identity through **workload identity
federation**. How the flow works, with a diagram, is in the
[main README](../README.md#push-notifications-workload-identity-federation-with-google). The Google
project is shared by all environments (`GOOGLE_PROJECT_ID` and `GOOGLE_PROJECT_NUMBER` in the azd
environment override it); without a project, no push is sent, as in local runs. Set up once:

1. In Google Cloud Shell, in the Firebase project:

   ```bash
   gcloud services enable fcm.googleapis.com iamcredentials.googleapis.com sts.googleapis.com
   gcloud iam service-accounts create fcm-sender --display-name "CyVault push"
   gcloud projects add-iam-policy-binding <PROJECT_ID> --member "serviceAccount:fcm-sender@<PROJECT_ID>.iam.gserviceaccount.com" --role roles/firebasecloudmessaging.admin --condition None
   gcloud iam workload-identity-pools create azure --location global --display-name "Azure"
   gcloud iam workload-identity-pools providers create-oidc cyvault --workload-identity-pool azure --location global --issuer-uri "https://sts.windows.net/<AZURE_TENANT_ID>/" --allowed-audiences "api://<AZURE_TENANT_ID>/cyvault-fcm" --attribute-mapping "google.subject=assertion.sub"
   ```

2. In the Azure subscription's tenant (`AZURE_TENANT_ID`, not the External ID tenant), an app
   registration whose only job is to be the audience of the token Google checks. Entra only
   accepts identifier URIs that contain the tenant ID:

   ```bash
   az ad app create --display-name "CyVault FCM audience" --identifier-uris api://<AZURE_TENANT_ID>/cyvault-fcm --sign-in-audience AzureADMyOrg
   az ad sp create --id <its appId>
   ```

3. For each environment, after its first `azd up`, let its Function act as `fcm-sender`
   (`FUNCTION_PRINCIPAL_ID` is in `azd env get-values`):

   ```bash
   gcloud iam service-accounts add-iam-policy-binding fcm-sender@<PROJECT_ID>.iam.gserviceaccount.com --role roles/iam.workloadIdentityUser --member "principal://iam.googleapis.com/projects/<PROJECT_NUMBER>/locations/global/workloadIdentityPools/azure/subject/<FUNCTION_PRINCIPAL_ID>"
   ```
