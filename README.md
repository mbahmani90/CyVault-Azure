<p align="center">
  <img src="cyvault_icon.png" width="96" alt="CyVault icon" />
</p>

<h1 align="center">CyVault</h1>

<p align="center">
  A Kotlin Multiplatform (Android + iOS) wallet app for managing bank cards, with open-banking account linking.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-2.4-7F52FF?logo=kotlin&logoColor=white" />
  <img src="https://img.shields.io/badge/Compose%20Multiplatform-1.11-4285F4?logo=jetpackcompose&logoColor=white" />
  <img src="https://img.shields.io/badge/platforms-Android%20%7C%20iOS-3DDC84" />
  <img src="https://img.shields.io/badge/status-work%20in%20progress-orange" />
</p>

---

## Overview

CyVault lets a user sign in, link a bank account through **open banking** (via the [Yapily](https://www.yapily.com/) API), and store and manage their cards in a vault.

The app is built with **Kotlin Multiplatform** and **Compose Multiplatform**. UI, ViewModels, business logic and networking are shared between Android and iOS. Only a small set of platform-specific pieces use `expect`/`actual` or native bridges.

## Features

- **Authentication**
  - **Android:** Microsoft Entra External ID with MSAL native authentication: the app's own Compose screens, no browser page
  - **iOS:** AWS Cognito via Amplify (not migrated to Entra yet)
  - Email/password sign-up with email verification code
  - Sign-in, sign-out, forgot password (sends the reset code; entering the code and a new password isn't built yet)
  - **Sign in with Google:** on Android through Entra External ID with Google as an identity provider (MSAL browser flow in a browser tab); on iOS through the Amplify hosted UI (deep-link callback `cyvault://callback`)
- **Add card via open banking** (multi-step flow)
  1. Pick a country and search for your bank (Yapily institutions API)
  2. Authorise access in the bank's page (Chrome Custom Tabs on Android, `SFSafariViewController` on iOS)
  3. Return through a deep link, then choose one of the linked accounts
  4. Enter card details and save
- **Vault**: list and delete saved cards; the drawer shows the signed-in user (avatar, name, email) and sign-out
- **Profile photo** (Android): tap the drawer avatar to pick a photo with the system Photo Picker. It's uploaded to the Azure backend with the user's Entra access token, cached in app-private storage, and removed from the device on sign-out

## Architecture

The project uses **Clean Architecture** with **MVI**, split into feature modules:

```
CyVault
├── androidApp       Android entry point (Application, MainActivity, the app's MSAL config)
├── iosApp           iOS entry point (SwiftUI host + Amplify Swift auth bridge)
├── app              Composition root: root Compose App, NavHost, Koin setup, the only iOS framework
├── core             Shared non-UI code: Ktor client, platform DI, I/O dispatcher
├── auth             Sign-in and session library: AuthClient (MSAL native auth / iOS bridge), reusable
├── systemdesign     Design system: theme, colors, typography, shapes, reusable components
├── feature
│   ├── authentication   Feature: sign-in / sign-up / Google / password reset
│   └── vault            Feature: vault home, card list, add-card (open banking)
├── azure-backend    Azure Functions API (.NET 10) + Bicep infrastructure, deployed with azd
└── amplify-backend  AWS Amplify Gen 2 backend (Cognito + Google IdP), TypeScript
```

`:auth` is a library, not a feature: it has no screens and no CyVault types or IDs, and it doesn't
depend on `:core`, so another app can use it as it is. It exposes `AuthClient` (sign-in, sign-up,
Google, password reset, current user, access tokens, sign-out) and `AuthUser`. The features call it
from their data layer and map `AuthUser` to their own models: `feature:authentication` in
`AuthRepositoryImpl`, the vault in `home/data/AccountRepositoryImpl` and `ProfilePhotoApiService`.
The app supplies the Entra registration: `androidApp` passes `MsalConfig(R.raw.auth_config_native_auth)`
to Koin, and on iOS Swift sets `IosAuthBridgeHolder.bridge`.

Features never depend on each other and know nothing about navigation: they don't depend on
Navigation Compose and have no routes. Each feature exposes plain screen composables
(`AuthScreen`, `VaultHomeScreen`, `AddCardScreen`) that only report events through callbacks
(`onAuthSuccess`, `onAddCardClick`, `onCardSaved`, `onSignedOut`, …). `app` defines every route
(`navigation/Routes.kt`), owns the only `NavHost` and decides where every event leads, and it
loads each feature's public Koin module.

Each feature module has its own layers. A single-flow feature (authentication) keeps them at
the module root; a multi-screen feature (vault) has one package per screen, each with its
own layers and Koin module, plus a small shared package:

```
feature/vault/…/feature/vault/
├── card/           shared by the screens: Card model, CardRepository, card API
├── home/           card list + total assets, drawer with sign-out and the profile photo
                    (Azure photo API client, on-device cache, photo picker)
├── addcard/        add a card via open banking (Yapily)
│   ├── data/           remote services + DTOs, mappers, repository implementations
│   ├── domain/         models, repository interfaces, use cases
│   ├── presentation/   Compose screens + ViewModel (State / Intent / Effect)
│   └── di/             the screen's internal Koin module
├── VaultScreens.kt the public screens: VaultHomeScreen, AddCardScreen (events out)
└── di/             vaultFeatureModule: the feature's only public Koin module
```

### Architecture diagram

The diagram below shows the modules as they are today: the two feature modules (authentication, and vault with its screen packages), the shared modules, the dependency rules, navigation, how vault signs out through `:auth`'s `AuthClient`, and the external services.

![CyVault modular architecture](docs/architecture.svg)

### Google sign-in flow

On Android, Google sign-in goes through Entra External ID, with Google set up as an identity provider in the tenant. Native auth only covers the in-app email screens, so for Google the app uses MSAL's browser flow (`acquireToken` with the `google.com` domain hint): it opens Entra's sign-in page in a browser tab, Entra hands the user over to Google, and Google sends a one-time code back to Entra's redirect URI. Entra exchanges that code for the user's identity with the Google client secret, then issues its own token to the app. The app never talks to Google directly, and only Entra holds the client secret.

![Sign in with Google through Entra External ID](docs/google-sign-in-flow.svg)

### Profile photo API: Function App access to Blob Storage

Profile photos live in a private Blob Storage container behind the Azure Functions API in `azure-backend/` (`PUT`, `GET` and `DELETE /api/me/avatar`). On Android, the app asks MSAL for an access token with the API's `Profile.ReadWrite` scope and sends it as `Authorization: Bearer`, only on these requests. No route takes a user ID. The Function validates the upload by decoding it, turns it upright, downscales it to at most 512 px and re-encodes it as JPEG, which also drops EXIF metadata such as GPS location. The Function App reaches Storage through its **Managed Identity**, so no storage keys or connection strings exist anywhere. The SDK (`DefaultAzureCredential`) gets a token for that identity from Entra ID and sends it with each blob request. Blob Storage validates the token and checks the identity's **IAM role assignment** (`Storage Blob Data Contributor` on the `avatars` container) before allowing the write. Users get no Storage permissions at all: the Function builds every blob path from the `oid` in the user's validated access token, so each account can only reach its own photo.

Balances use the same pattern with Table Storage (`GET /api/me/balances`, and `GET`, `PUT` and `DELETE /api/me/balances/{id}`), behind the `Balance.ReadWrite` scope. Each row's `PartitionKey` is the caller's `oid` and its `RowKey` the balance ID, and the Function's identity has `Storage Table Data Contributor` on the `balances` table only. A `PUT` body is `{ "name": "Main account", "description": "Daily spending", "amount": 1250.50, "currency": "EUR" }` (description and an ISO 8601 `date` optional; without a date, a new balance is dated now and an edit keeps its date); the amount is kept as a decimal string so money is never rounded.

The app gets the access token from Entra's token endpoint, which MSAL discovers from the tenant's metadata, and the Function checks it against Entra's published signing keys:

![Getting an access token and calling the profile photo API](docs/api-token-flow.svg)

Inside the Function App, the validated token's `oid` decides the blob, and the Function's own identity reaches Storage:

![Inside the Function App: user check, per-user blob path, Managed Identity](docs/function-app-overview.svg)

The sequence below shows the outgoing step, from the Function to Blob Storage, in detail.

![Function App writes to Blob Storage with its Managed Identity](docs/managed-identity-blob-flow.svg)

### MVI

Every screen has three types:

- **State**: an immutable data class that the screen renders, exposed as `StateFlow`.
- **Intent**: a sealed class of user actions sent to the ViewModel.
- **Effect**: one-off events (snackbar, navigation, opening an external URL), delivered through a `Channel` so each one is consumed **exactly once** and never replayed after recomposition or rotation.

See [`docs/Effect.md`](docs/Effect.md) for why `Channel` is used instead of `SharedFlow`.

### Platform-specific code

| Concern | Shared API | Android | iOS |
|---|---|---|---|
| Authentication (`:auth`) | `AuthClient` interface, bound in `authClientModule` | `MsalAuthClient`: MSAL native auth (Entra External ID); `ActivityProvider` gives MSAL's browser sign-in the on-screen activity | `BridgeAuthClient`: Amplify Swift (Cognito), through the `IosAuthBridge` callback interface implemented in Swift |
| Open bank authorisation page | `BankAppLauncher` (`expect`/`actual`) | Chrome Custom Tabs | `SFSafariViewController` |
| Profile photo | `rememberProfilePhotoPicker` (`expect`/`actual`); `PickedPhotoReader` and `AvatarFileStore` interfaces bound in `homePlatformModule` | Photo Picker; `AndroidPickedPhotoReader` turns the photo upright from EXIF and scales it to 1024 px; `AndroidAvatarFileStore` caches it in `filesDir` | Not implemented yet (iOS can't reach the Azure API without Entra sign-in) |
| DI | `platformModule` | Ktor OkHttp engine | Ktor Darwin engine |

## Tech stack

| Area | Library |
|---|---|
| Language | Kotlin 2.4 (Multiplatform) |
| UI | Compose Multiplatform 1.11, Material 3 |
| Navigation | Navigation Compose (KMP), type-safe `@Serializable` routes |
| Async | Kotlin Coroutines & Flow |
| DI | Koin 4 |
| Networking | Ktor 3 (OkHttp / Darwin), kotlinx.serialization |
| Auth | Android: Microsoft Entra External ID + MSAL 8 (native auth) · iOS: AWS Amplify (Cognito) + Google federated sign-in |
| Open banking | Yapily REST API |
| Backend | Azure Functions (.NET 10 isolated, Flex Consumption), Microsoft.Identity.Web, Blob Storage, SkiaSharp, Bicep + Azure Developer CLI · AWS Amplify Gen 2 (TypeScript), for the iOS auth backend |

## Getting started

### Requirements

- Android Studio (latest) with the Kotlin Multiplatform plugin
- Xcode (for iOS)
- JDK 17+
- An Azure account (only needed to use your own Entra tenant for Android)
- Node.js 18+ and an AWS account (only needed to deploy your own iOS auth backend)
- For the Azure backend: .NET 10 SDK, Azure Functions Core Tools 4, Azure CLI, Azure Developer CLI (`azd`) and Azurite (`npm install -g azurite`)

### 1. Authentication

**Android: Microsoft Entra External ID (optional: use your own tenant)**

1. In the [Entra admin center](https://entra.microsoft.com), create an **external** tenant.
2. In that tenant, register an app (single tenant) and add the **Android** platform with the package
   name `com.cypressit.cyvault` and your signing key's hash:

   ```bash
   keytool -exportcert -alias androiddebugkey -keystore ~/.android/debug.keystore -storepass android | openssl sha1 -binary | openssl base64
   ```

3. Under **Authentication → Settings**, turn on **Allow public client flows** and **Enable native authentication**.
4. Create a user flow with **Email with password** and the **Display Name** attribute, and add the app to it.
5. Under **API permissions**, add `openid` and `offline_access`, then grant admin consent.
6. Put your client ID, tenant subdomain and redirect URI in
   `androidApp/src/main/res/raw/auth_config_native_auth.json`, and your signature
   hash in the MSAL `BrowserTabActivity` entry of `androidApp/src/main/AndroidManifest.xml`.
7. Optional, for **Sign in with Google**:
   - In the [Google Cloud Console](https://console.cloud.google.com), create a project, set up the
     OAuth consent screen (Google Auth Platform) as **External**, and add your Gmail address as a
     test user while the app is in Testing mode.
   - Create an OAuth client of type **Web application** with these redirect URIs, where `<tenant-ID>`
     is your tenant's ID and `<tenant>` its subdomain:

     ```
     https://login.microsoftonline.com
     https://login.microsoftonline.com/te/<tenant-ID>/oauth2/authresp
     https://login.microsoftonline.com/te/<tenant>.onmicrosoft.com/oauth2/authresp
     https://<tenant-ID>.ciamlogin.com/<tenant-ID>/federation/oidc/accounts.google.com
     https://<tenant-ID>.ciamlogin.com/<tenant>.onmicrosoft.com/federation/oidc/accounts.google.com
     https://<tenant>.ciamlogin.com/<tenant-ID>/federation/oauth2
     https://<tenant>.ciamlogin.com/<tenant>.onmicrosoft.com/federation/oauth2
     ```

   - In Entra, go to **External Identities → All identity providers → Google** and enter the
     Google client ID and secret, then tick **Google** under the user flow's **Identity providers**.
     The secret stays in Entra; the app never needs it.

**iOS: AWS Amplify (optional: deploy your own backend)**

```bash
cd amplify-backend
npm install
npx ampx sandbox secret set GOOGLE_CLIENT_ID
npx ampx sandbox secret set GOOGLE_CLIENT_SECRET
npx ampx sandbox
```

Copy the generated `amplify_outputs.json` to `iosApp/iosApp/amplify_outputs.json`.

### 2. Open banking

Create a Yapily sandbox application and put your credentials in
`feature/vault/src/commonMain/kotlin/com/cypressit/cyvault/feature/vault/addcard/data/remote/YapilyConfig.kt`.

### 3. Azure backend (profile photo and balances API)

**Environments.** Each azd environment is a full copy of the backend: its own Entra External ID
tenant (so its own users), app registrations, user flow, Function App and storage.

| azd environment | Used by | Tenant (users) | Entra apps | Azure resources |
|---|---|---|---|---|
| `dev` | release builds (production) | `cyvault.onmicrosoft.com` | `CyVault API`, `CyVault Mobile`, user flow `SignUpSignIn` | `rg-cyvault-dev` |
| `debug` | debug builds | `cyvaultdebug.onmicrosoft.com` | `CyVault API (debug)`, `CyVault Mobile (debug)`, user flow `SignUpSignIn (debug)` | `rg-cyvault-debug` |

All of it is code:

- **Tenant:** [`infra/tenant/tenant.bicep`](azure-backend/infra/tenant/tenant.bicep), one `.bicepparam`
  per tenant. Deployed once, outside azd; azd never deletes a tenant.
- **Google sign-in in the tenant:** [`Set-GoogleProvider.ps1`](azure-backend/infra/entra/Set-GoogleProvider.ps1),
  once per tenant. It asks for the Google client secret and sends it only to Entra.
- **App registrations, consent and user flow:** [`entra.json`](azure-backend/infra/entra/entra.json),
  applied by [`Set-EntraApps.ps1`](azure-backend/infra/entra/Set-EntraApps.ps1), which azd runs before
  every `azd provision` and `azd up`. It creates what's missing, updates what differs, and saves the app
  IDs for Bicep. `-WhatIf` shows what it would change without changing anything.
- **Function App and storage:** `infra/main.bicep`, wired to the environment's tenant through the
  Function App's `AzureAd__*` settings.

**Run locally** (from `azure-backend/src/CyVault.Api`):

```bash
cp local.settings.sample.json local.settings.json
```

Put the API's client ID in `AzureAd__ClientId` in `local.settings.json`, which is git-ignored. Start
Azurite (the local Blob Storage emulator) in one terminal and the Function in another:

```bash
azurite --silent --location ../../.azurite
```

```bash
func start
```

On Windows PowerShell, use `azurite.cmd` if script execution is disabled. The API listens on
`http://localhost:7158/api/me/avatar` and answers `401` without a valid access token.

**Deploy to Azure** (from `azure-backend/`):

Needs PowerShell 7 (`pwsh`) for the Entra hooks, and for Google sign-in the Microsoft Graph module
(`Install-Module Microsoft.Graph.Authentication -Scope CurrentUser`).

To create the **debug** environment from nothing:

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

### 4. Run

- **Android:** `./gradlew :androidApp:assembleDebug`, or use the run configuration in Android Studio
- **iOS:** open `iosApp/` in Xcode and run

## Security notes & roadmap

CyVault is a portfolio / learning project and **not production-ready**. The known gaps and the planned fixes are:

- **Yapily app secret** is currently read on the client for simplicity. In production, all Yapily calls should go through a backend proxy (for example an AWS Lambda) so the secret never ships in the app.
- **Card data**: the demo model holds a card number and CVV. A real app must never store the CVV and should keep only a **tokenised** card reference from a PCI DSS-compliant provider.
- **Card API**: `CardApiService` points to a placeholder endpoint. Planned: move it to the Azure Functions API, with every card keyed by the caller's `oid`.
- **Profile photo on iOS**: needs the move to Entra first; then a `PHPicker` picker and an Application Support cache.
- **Local storage**: planned: SQLDelight for offline caching, with platform keystore/keychain encryption for sensitive data.
- **Auth**: move iOS from Amplify/Cognito to MSAL and Entra External ID, and finish the forgot-password flow (code + new password).
- More unit and UI tests for use cases and ViewModels.

## Author

**Majid Bahmani** · [GitHub](https://github.com/mbahmani90)
