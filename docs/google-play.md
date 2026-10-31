# Publishing to Google Play

## Step 1: Create the app in Play Console

In [Play Console](https://play.google.com/console), **Create app** asks for:

| Field | CyVault | Note |
|---|---|---|
| Package name | `com.cypressit.cyvault` | `applicationId` in `androidApp/build.gradle.kts`. Set by the first upload and can never change. It's also in the Entra redirect URI, the MSAL config and the manifest. |
| App name | `CyVault` | Up to 30 characters; can be changed later. |
| Default language | English (United States) | |
| App or game | App | |
| Free or paid | **Free** | A free app can never become paid. Premium is sold later as an in-app subscription or one-time product through Google Play Billing. |
| Declarations | Tick both | The Developer Program Policies, and US export laws. |

### Store listing text

**Short description** (80 characters max):

> Track your purchases and income and see your balance in every currency.

**Full description** (4000 characters max):

> CyVault is a simple, private way to keep track of your money.
>
> • Record purchases and income with a title, description, amount, currency, and date
> • See your total balance for each currency at a glance
> • Tap an entry to edit it; long-press to select and delete several at once
> • Sign in with email and password, or with Google
> • Add a profile photo
>
> Your balances are stored securely in your own account and synced to the cloud, so they're there
> when you sign in again. Profile photos are stripped of location data before they're saved.
