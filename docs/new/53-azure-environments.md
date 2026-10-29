# Azure environments: production and debug

How CyVault's backend is split into a **production** and a **debug** environment, what each Azure
and Entra building block does, and how to create (or copy) an environment from code. The last
section is a checklist for reusing the same setup in another project.

## The building blocks

| Block | What it holds | AWS counterpart | CyVault |
|---|---|---|---|
| **Directory** (workforce tenant) | the admin accounts that manage Azure; owns the subscription | AWS Organization / IAM Identity Center | `Default Directory` (`264a3bf1…`) |
| **Subscription** | billing and limits; contains resource groups | AWS account | `Azure subscription 1` |
| **Resource group** | the Azure resources of one environment | a CloudFormation stack | `rg-cyvault-dev`, `rg-cyvault-debug` |
| **External ID tenant** (CIAM directory) | the **app users**, app registrations, user flows, identity providers | Cognito User Pool | `cyvault.onmicrosoft.com`, `cyvaultdebug.onmicrosoft.com` |
| **App registration** | one app's identity: client ID, scopes, redirect URIs, permissions | User Pool app client / resource server | `CyVault API`, `CyVault Mobile` |
| **User flow** | how users sign up and sign in: methods, attributes | Cognito sign-up settings, hosted UI | `SignUpSignIn` |
| **Identity provider** | an outside sign-in, set up once per tenant | Cognito federated IdP | Google |

Two rules make the rest simple:

- **Users live in a tenant; data lives in a resource group.** They're only connected by three
  settings on the Function App (`AzureAd__Instance`, `AzureAd__TenantId`, `AzureAd__ClientId`), which
  decide whose tokens it accepts.
- **One directory and one subscription are enough.** A directory can hold many subscriptions, and a
  subscription many resource groups. Separate subscriptions (billing) or directories (admins) are only
  worth it for large organizations.

## The architecture

```
Default Directory (workforce tenant: who administers Azure)
└── Azure subscription 1
    ├── cyvault-rg                       the External ID tenants, as Azure resources
    │   ├── cyvault.onmicrosoft.com
    │   └── cyvaultdebug.onmicrosoft.com
    ├── rg-cyvault-dev     (production) ──wired to──▶ cyvault.onmicrosoft.com
    │   ├── Function App  func-cyvault-<token>      (.NET 10 isolated, Flex Consumption)
    │   ├── storage account stcyvav<token>          balances table + avatars container (user data)
    │   ├── storage account stcyvrt<token>          Functions runtime state, deployment package
    │   └── Application Insights + Log Analytics
    └── rg-cyvault-debug   (debug)      ──wired to──▶ cyvaultdebug.onmicrosoft.com
        └── (the same resources, from the same Bicep)

cyvault.onmicrosoft.com (External ID tenant)        cyvaultdebug.onmicrosoft.com
├── users                                           ├── users (separate)
├── Google identity provider                        ├── Google identity provider
├── CyVault API          scopes Balance/Profile     ├── CyVault API (debug)
├── CyVault Mobile       Android redirect URI       ├── CyVault Mobile (debug)
└── SignUpSignIn         email + password, Google   └── SignUpSignIn (debug)
```

| azd environment | Used by | Tenant (users) | Resource group |
|---|---|---|---|
| `dev` | release builds (**production**; named before the split) | `cyvault.onmicrosoft.com` | `rg-cyvault-dev` |
| `debug` | debug builds | `cyvaultdebug.onmicrosoft.com` | `rg-cyvault-debug` |

What's shared and what isn't:

| | Production and debug |
|---|---|
| Directory, subscription | shared |
| Tenant, users, Google provider | **separate** |
| App registrations, consent, user flow | **separate** |
| Function App, storage, table, blob container | **separate** |
| API code | **the same**: only settings differ |
| Data | separate; a new environment starts empty |

### CyVault's environments

| | Production (azd `dev`) | Debug (azd `debug`) |
|---|---|---|
| Tenant | `cyvault.onmicrosoft.com` (`e5cb5788-11bc-499b-82f2-095c6f201b40`) | `cyvaultdebug.onmicrosoft.com` (`716c5a4a-3127-449c-8f78-fc715bd7ac06`) |
| Sign-in host (`ENTRA_INSTANCE`) | `https://cyvault.ciamlogin.com/` | `https://cyvaultdebug.ciamlogin.com/` |
| API app (`ENTRA_API_CLIENT_ID`) | `CyVault API`, `61a46c01-e17b-43da-8630-17d521ef888b` | `CyVault API (debug)`, `7b114a03-12d1-4c35-842f-861308111286` |
| Mobile app (`ENTRA_MOBILE_CLIENT_ID`) | `CyVault Mobile`, `63b77443-9d6c-43ae-8385-50a9fa42f241` | `CyVault Mobile (debug)`, `990b363b-ef2e-4d6b-ba0e-53957631370b` |
| Google provider (`ENTRA_GOOGLE_PROVIDER_ID`) | `3dea3585-ce74-4f0d-abf9-dba6621a275c` | `b7b2271b-b7c3-4996-ab5f-266f33c41cdd` |
| User flow | `SignUpSignIn` | `SignUpSignIn (debug)` |
| API (`API_BASE_URL`) | `https://func-cyvault-24xpnyg5tfwui.azurewebsites.net/api` | `https://func-cyvault-5zvntav5rsyeq.azurewebsites.net/api` |
| User data storage | `stcyvav24xpnyg5tfwui` | `stcyvav5zvntav5rsyeq` |
| Region | North Europe | North Europe |

These are identifiers, not secrets. `azd env get-values -e <env>` in `azure-backend/` shows an
environment's current values.

### How a request flows

1. The app signs the user in against **its** environment's tenant (MSAL native auth, or Google
   through the user flow) and gets an access token for that tenant's `CyVault API`.
2. It calls **its** environment's Function App with `Authorization: Bearer <token>`.
3. The Function App checks the token's issuer, audience and scope against its `AzureAd__*` settings,
   so a token from the other environment is rejected.
4. It reads and writes **its own** storage through its Managed Identity, with the user's `oid` as
   the partition key or blob path.

## Everything is code

| What | File | Applied by | When |
|---|---|---|---|
| External ID tenant | `azure-backend/infra/tenant/tenant.bicep` + one `<env>.bicepparam` per tenant | `az deployment group create` | once per tenant |
| Google identity provider | `azure-backend/infra/entra/Set-GoogleProvider.ps1` | you, by hand | once per tenant |
| App registrations, scopes, permissions, consent, user flow | `azure-backend/infra/entra/entra.json` | `Set-EntraApps.ps1`, as azd's `preprovision` hook | every `azd provision` / `azd up` |
| Resource group, Function App, storage, monitoring, role assignments | `azure-backend/infra/main.bicep`, `resources.bicep` | `azd provision` | every `azd provision` / `azd up` |
| API code | `azure-backend/src/CyVault.Api` | `azd deploy` | every `azd deploy` / `azd up` |

How they fit together:

- `azure.yaml` declares the `preprovision` hook, so azd runs `Set-EntraApps.ps1` before Bicep.
- `Set-EntraApps.ps1` creates or updates the environment's Entra objects in the tenant named by
  `ENTRA_TENANT_ID`, then saves `ENTRA_API_CLIENT_ID`, `ENTRA_MOBILE_CLIENT_ID`, `ENTRA_TENANT_ID` and
  `ENTRA_INSTANCE` in the azd environment (`azure-backend/.azure/<env>/.env`, git-ignored).
- `main.parameters.json` hands those values to Bicep, which writes them into the Function App's
  `AzureAd__*` settings.
- Every Azure resource name is derived from the environment name (`rg-cyvault-<env>`,
  `func-cyvault-<hash of subscription, group, env>`), so environments never collide.
- New Entra objects are named after the environment (`CyVault API (debug)`); existing ones keep their
  names. Scope IDs are kept on updates, because Entra won't change the ID of an enabled scope.

No secrets are stored anywhere: storage has shared keys disabled and is reached through the Function
App's Managed Identity, the mobile app is a public client, and the Google client secret is only typed
into `Set-GoogleProvider.ps1`, which sends it straight to Entra.

## Tools

- Azure CLI (`az`) and Azure Developer CLI (`azd`)
- PowerShell 7: `winget install Microsoft.PowerShell` (the azd hook runs with `pwsh`)
- Microsoft Graph PowerShell, for Google: `Install-Module Microsoft.Graph.Authentication -Scope CurrentUser`.
  The Azure CLI's sign-in isn't allowed to manage identity providers.
- .NET 10 SDK, to build the API

Run everything below from `azure-backend/`, in `pwsh`. Start it by typing `pwsh` in any terminal;
the banner shows `PowerShell 7.x`. In the older Windows PowerShell 5.1, scripts fail with "running
scripts is disabled on this system"; `pwsh -File ./infra/entra/<script>.ps1 …` runs one through
PowerShell 7 from any shell.

`Install-Module` asks twice the first time: answer **Y** to install the NuGet provider, then **A**
(Yes to All) for PSGallery, which is only marked "untrusted" by default. Its default answer there is
**N**, so pressing Enter cancels the install.

## Create an environment

The same steps create production in a new project, or debug here. Replace `<env>` with the azd
environment name (`debug`) and `<prefix>` with the tenant's domain prefix (`cyvaultdebug`).

### 1. Create the tenant

Make `infra/tenant/<env>.bicepparam` (copy `debug.bicepparam`) with the domain prefix, display name,
country and data location, then:

```bash
az deployment group create -g cyvault-rg -f infra/tenant/tenant.bicep -p infra/tenant/<env>.bicepparam --query properties.outputs
```

It takes a few minutes and prints `tenantId` and `instance`. Your account becomes the tenant's
administrator.

- The domain prefix must be unused across all of Entra. `what-if` doesn't check that; the deployment
  fails if it's taken.
- The country and data location can't be changed later.
- `az deployment group what-if …` with the same arguments shows the change without making it.

### 2. Sign in to the tenant and point azd at it

```bash
az login --tenant <tenantId> --allow-no-subscriptions
```

```bash
az account set --subscription "Azure subscription 1"
```

```bash
azd env new <env>
```

```bash
azd env set ENTRA_TENANT_ID <tenantId>
```

```bash
azd env set ENTRA_INSTANCE https://<prefix>.ciamlogin.com/
```

`az login` adds the tenant to the Azure CLI; `az account set` makes the subscription the default
again, so azd and later `az` commands still find it. `azd env new` also makes `<env>` the default
environment of this folder: `azd env select <other env>` switches back.

### 3. Add Google sign-in

```bash
./infra/entra/Set-GoogleProvider.ps1 -EnvironmentName <env> -ClientId <Google OAuth client ID>
```

It asks for the client secret, adds (or updates) Google in the tenant, and saves its ID as
`ENTRA_GOOGLE_PROVIDER_ID`. It opens a Microsoft sign-in to consent to `IdentityProvider.ReadWrite.All`;
if that window never appears (Windows' account manager can hide it behind other windows), stop the
script with Ctrl+C, switch the module to the normal browser, and run it again:

```bash
pwsh -Command 'Set-MgGraphOption -DisableLoginByWAM $true'
```

**Or in the portal instead:** in the Entra admin center, switch to the tenant (gear icon →
**Directories + subscriptions**), then **External Identities → All identity providers → Built-in →
Google → Configure**, and enter the client ID and secret.

**Redirect URIs in the Google Cloud console.** Google only sends users back to addresses it knows,
and each tenant has its own, so add these six to the OAuth client's **Authorized redirect URIs** (one
Google OAuth client can serve several tenants; keep the other tenants' entries):

```
https://<tenantId>.ciamlogin.com/<tenantId>/federation/oidc/accounts.google.com
https://<tenantId>.ciamlogin.com/<prefix>.onmicrosoft.com/federation/oidc/accounts.google.com
https://<prefix>.ciamlogin.com/<tenantId>/federation/oauth2
https://<prefix>.ciamlogin.com/<prefix>.onmicrosoft.com/federation/oauth2
https://login.microsoftonline.com/te/<tenantId>/oauth2/authresp
https://login.microsoftonline.com/te/<prefix>.onmicrosoft.com/oauth2/authresp
```

They're the same tenant written three ways (by tenant-ID host, by `<prefix>` host, and the older
`login.microsoftonline.com/te` endpoint), each with the tenant's ID and with its domain. Which one Entra
uses depends on how the sign-in started; a missing one fails with Google's `redirect_uri_mismatch`.
New URIs can take a few minutes to work.

**Google must also be in the user flow.** Adding Google to the tenant doesn't add it to a user flow.
`Set-EntraApps.ps1` puts it in when it **creates** the flow, but only if `ENTRA_GOOGLE_PROVIDER_ID` is
set by then; it never changes an existing flow. Otherwise tick it in the portal: **External Identities
→ User flows → <flow> → Identity providers → Google → Save**. Then save the provider's ID, so the hook
stops warning; read it from the flow with the Azure CLI's Graph token:

```bash
az rest --method get --url https://graph.microsoft.com/beta/identity/authenticationEventsFlows --resource https://graph.microsoft.com
```

(`az rest` uses the CLI's default tenant; when that's another one, get a token with
`az account get-access-token --tenant <tenantId> --resource https://graph.microsoft.com` and call Graph
with it.) The Google entry under `onAuthenticationMethodLoadStart.identityProviders` has the ID:

```bash
azd env set ENTRA_GOOGLE_PROVIDER_ID <id>
```

Skipping Google is fine: the user flow then gets email and password only, with a warning.

### 4. Create everything else and deploy

Set the region first. azd would otherwise ask, and some regions (West Europe, for CyVault's
subscription) refuse new resources with "The selected region is currently not accepting new customers":

```bash
azd env set AZURE_LOCATION northeurope
```

Run the Entra hook once on its own. On a new environment, `azd up` asks for every Bicep parameter
before it runs hooks, so it would prompt for `entraApiClientId`, which the hook hasn't created yet:

```bash
azd hooks run preprovision
```

```bash
azd up
```

Pick the subscription if asked. In order, azd:

1. runs `Set-EntraApps.ps1`: the API and mobile app registrations, scopes, permissions, admin consent
   and the user flow, in the environment's tenant
2. provisions `infra/main.bicep`: resource group, Function App, storage, monitoring, role assignments
3. deploys the API, and prints `API_BASE_URL`

**Check the new environment.** The API should refuse a request without a token, and its settings
should name the environment's own tenant and storage:

```bash
curl -s -o /dev/null -w "%{http_code}\n" <API_BASE_URL>/me/balances
```

```bash
az functionapp config appsettings list -g rg-cyvault-<env> -n <AZURE_FUNCTION_APP_NAME> --query "[?starts_with(name,'AzureAd') || ends_with(name,'ServiceUri')]" -o table
```

The first prints `401`. A new tenant has no users yet, so the first sign-in in the app is a sign-up,
even with an address that already has a production account.

Run `azd up` (or `azd provision`) again after any change to `entra.json` or the Bicep files; every
environment converges to the same code. To check first without changing anything:

```bash
pwsh ./infra/entra/Set-EntraApps.ps1 -WhatIf
```

```bash
azd provision --preview
```

### Adopting an environment that already exists

An environment created by hand (like `dev` was) is taken over by saving its IDs before the first
`azd up`, so the hook updates the existing objects instead of creating new ones:

```bash
azd env set ENTRA_API_CLIENT_ID <API app's client ID> -e dev
```

```bash
azd env set ENTRA_MOBILE_CLIENT_ID <mobile app's client ID> -e dev
```

Then `Set-EntraApps.ps1 -WhatIf` should report no changes. Without `ENTRA_TENANT_ID`, an environment
uses the tenant in `entra.json`'s `tenant` block (production).

## The mobile app per environment

The app must sign in to, and call, the environment that matches its build:

| | Debug build | Release build |
|---|---|---|
| MSAL config (`auth_config_native_auth.json`) | debug tenant, `CyVault Mobile (debug)` client ID | production tenant and client ID |
| API scopes | `api://<debug API client ID>/…` | `api://<production API client ID>/…` |
| API base URL | debug `API_BASE_URL` | production `API_BASE_URL` |

In a multi-module Android app only the app module has build types, so these values belong in
`androidApp`, the same way `:feature:vault-debug` is added with `debugImplementation`. In CyVault
(branch `debug-build-environment`, not on `main` yet):

- **Sign-in:** `androidApp/src/debug/res/raw/auth_config_native_auth.json` has the debug tenant's
  authority and client ID. Android uses a build type's resource instead of the one in `src/main`,
  so release builds keep `src/main`'s production file.
- **API:** `:feature:vault`'s `CyVaultApiConfig` holds the base URL and the API's Application ID URI
  (`api://<API client ID>`); the API services build their URLs and scopes from it.
  `CyVaultApiConfig.Production` is the default, used by release builds and iOS. `androidApp`'s
  `src/debug/…/BuildTypeModules.kt` provides the debug environment's through Koin, and `homeModule`
  takes it with `getOrNull()`.
- The tenant and the API go together: the debug API only accepts debug-tenant tokens, so a
  mismatch fails with `401`. Both debug values live side by side in `androidApp/src/debug/`.

To check a build, look inside the APKs: the debug one's `res/raw` config names the debug client ID
and authority, and its code contains the debug `API_BASE_URL`; the release one has neither.

The redirect URI contains the **signing key's** hash, so each key needs its own entry in
`entra.json`'s `mobile.redirectUris`. Get the hash of a keystore with:

```bash
keytool -exportcert -alias <alias> -keystore <keystore> | openssl sha1 -binary | openssl base64
```

The redirect URI is `msauth://<applicationId>/<hash, URL-encoded>`. The debug keystore
(`~/.android/debug.keystore`, alias `androiddebugkey`, password `android`) differs per machine, and
the release (or Play App Signing) key needs its own entry before a release build can sign in.

## One sign-in method per email

Each user is one object in the tenant, with a list of sign-in identities (email and password, Google,
…), and the API keys all data by the user's `oid`. Entra doesn't link accounts on its own:

- A user who signed up with **Google** and then tries to **sign up with email and password** for the
  same address gets `AADSTS1003037` ("It looks like you may already have an account with us using
  this email address. Try signing in again with another identity provider."). No second user is
  created; they keep using Google.
- An administrator could add an email-and-password identity to the same user through Microsoft Graph
  (the user's `identities` list plus a `passwordProfile`), so both methods reach one `oid`. CyVault
  doesn't do this: one method per email address.

The app could turn `AADSTS1003037` into a friendlier message, such as "This email is registered with
Google. Please use Continue with Google."

## Day-to-day

| Task | Command |
|---|---|
| Which environment am I on? | `azd env list` |
| Switch environment | `azd env select debug` |
| See an environment's values | `azd env get-values` |
| Apply Bicep and Entra changes | `azd provision` |
| Deploy API code only | `azd deploy` |
| Preview Entra changes | `pwsh ./infra/entra/Set-EntraApps.ps1 -WhatIf` |
| Delete an environment's Azure resources | `azd down` (asks first; the data is gone) |

`azd down` doesn't touch Entra or the tenant. To remove those, delete the app registrations and user
flow, then the tenant itself, in the Entra admin center.

Cost: Flex Consumption, Storage, Application Insights and an External ID tenant all bill per use, so
an idle environment costs close to nothing. External ID is free up to its monthly-active-user limit.

## Gotchas

- **Two places to sign in.** `azd auth login` is for azd (Azure resources); `az login --tenant …` is
  for the Entra hook. Each tenant needs its own `az login`: run it again if the hook says it can't
  get a token.
- **Replication delay.** A new app registration takes a few seconds to appear everywhere; the hook
  retries "does not exist" errors for that reason.
- **Scopes can't be renamed or removed in one step.** Entra requires disabling a scope before deleting
  or changing it; the hook leaves scopes that `entra.json` doesn't list alone, with a warning.
- **One user flow per app.** An app can be in only one user flow, so each environment's mobile app has
  its own flow.
- **The tenant API is a preview** (`Microsoft.AzureActiveDirectory/ciamDirectories@2023-05-17-preview`).
  It's only used once per tenant; creating the tenant in the Entra admin center works just as well.
- **`azd env new` changes the default environment**, so a plain `azd up` afterwards targets the new
  one.
- **A new environment asks for `entraApiClientId`.** `azd up` asks for missing Bicep parameters
  before it runs hooks; run `azd hooks run preprovision` once first (step 4).
- **Some regions refuse new resources.** West Europe failed with "The selected region is currently
  not accepting new customers"; set `AZURE_LOCATION` before the first `azd up`. A failed provision
  also shows "package output is empty" for the API: that's only a side effect.
- **`az login --tenant … --allow-no-subscriptions`** lists the tenant as `N/A(tenant level account)`;
  press Enter. An External ID tenant has no subscription of its own.

## Reusing this in another project

1. Copy `azure-backend/infra/` (Bicep, `tenant/`, `entra/`) and the `hooks` block of `azure.yaml`.
2. Rename the resource prefixes in `resources.bicep` (`func-cyvault-`, `stcyvav`, `stcyvrt`, …) and
   the resource group in `main.bicep` (`rg-cyvault-`). Storage account names allow only 3–24
   lowercase letters and digits.
3. Rewrite `entra.json`:
   - `tenant`: your production tenant's ID, `https://<prefix>.ciamlogin.com/` and Google provider ID
     (or leave the Google ID out and run `Set-GoogleProvider.ps1`)
   - `api`: display name and scopes
   - `mobile`: display name, redirect URIs (package name + signing key hash), Graph scopes, claims
   - `userFlow`: name, sign-in methods, sign-up attributes
4. Make one `.bicepparam` per tenant in `infra/tenant/`, and pick the resource group that holds the
   tenants (`cyvault-rg` here) in the `az deployment group create` command.
5. Point the API's token check at the `AzureAd__*` settings (CyVault: `CallerAuthentication.cs`), and
   the app's MSAL config and scopes at each environment's values.
6. Create each environment with the four steps above, then check with
   `Set-EntraApps.ps1 -WhatIf` and `azd provision --preview` that nothing is left to change.
