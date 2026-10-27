# Effects in CyVault (MVI)

## What is an `Effect`?

In this codebase's MVI setup, each feature has three viewmodel artifacts:

- **State** — the data rendered by the screen.
- **Intent** — user actions sent into the ViewModel.
- **Effect** — a one-off event emitted by the ViewModel that the screen consumes exactly once (as opposed to `State`, which is observed continuously).

Effects are modeled as a `sealed class` and exposed from the ViewModel as a `Flow<XxxEffect>` backed by a `Channel`:

```kotlin
private val _effect = Channel<XxxEffect>(Channel.BUFFERED)
val effect = _effect.receiveAsFlow()
```

The screen collects them inside a `LaunchedEffect(Unit)` block and reacts with a `when` expression.

```kotlin
LaunchedEffect(Unit) {
    effects.collect { effect ->
        when (effect) {
            is XxxEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)
            XxxEffect.NavigateBack -> onBack() // callback from app, which owns navigation
            ...
        }
    }
}
```

## Why `Channel` and not `SharedFlow`

`Channel` is hot, unicast, and buffered: an emitted effect is delivered to exactly one collector, held in the buffer until collected, and never replayed. That matches the "fire exactly once" requirement of an effect.

`MutableSharedFlow` is multicast and, with `replay = 0`, drops effects emitted before a collector subscribes (e.g. during a recomposition gap); with `replay ≥ 1` it re-delivers the last effect to new subscribers, which risks a snackbar or navigation firing again after rotation. It can be made to behave like `Channel`, but that requires extra config (`extraBufferCapacity`, `onBufferOverflow`, manual replay-cache resets) that `Channel` gives for free — so this codebase sticks with `Channel`.

## The three common cases

Across `AuthEffect`, `VaultListEffect`, `VaultHomeEffect`, and `AddCardEffect`, effects fall into three recurring categories:

### 1. Show a snackbar / toast

Used for transient feedback: errors, confirmations, status messages.

```kotlin
data class ShowError(val message: String) : AddCardEffect()
data object CardDeleted : VaultListEffect()
```

Consumed via `snackbarHostState.showSnackbar(...)`.

### 2. Redirect to an external URL

Used when the app needs to hand off to an external destination (e.g. a bank's web page).

```kotlin
data class LaunchBankApp(val bankUrl: String) : AddCardEffect()
```

Consumed via a platform launcher abstraction, e.g. `bankAppLauncher.openBankUrl(effect.bankUrl)`.

### 3. Navigate to another screen

Used for one-off navigation triggered by a ViewModel decision (login success, save completion, back navigation).

```kotlin
data object NavigateToLogin : VaultHomeEffect() // after sign-out → onSignedOut()
data object NavigateBack : VaultHomeEffect()    // → onBack()
data object CardSaved : AddCardEffect()          // → onCardSaved()
```

Consumed by calling a callback passed into the screen. Features never touch a `NavController`:
the `app` module owns the only `NavHost` and decides where each callback leads (see `App.kt`).

## A fourth, less common case: transient UI-widget control

`VaultHomeEffect` also has `OpenDrawer` / `CloseDrawer`, which don't fit snackbar, URL, or screen-navigation — they toggle a `DrawerState` directly:

```kotlin
data object OpenDrawer : VaultHomeEffect()
data object CloseDrawer : VaultHomeEffect()
```

```kotlin
VaultHomeEffect.OpenDrawer -> drawerState.open()
VaultHomeEffect.CloseDrawer -> drawerState.close()
```

Treat this as a variant of "control a piece of transient UI state that isn't worth putting in `State`" — same reasoning as the other three cases (fire-once, not persisted), just not a snackbar/URL/nav-screen target.

## When to use `Effect` vs `State`

- Use **`State`** for anything the screen should render persistently and that survives recomposition/rotation (form fields, loading flags, list contents).
- Use **`Effect`** for anything that should happen exactly once and should NOT replay on recomposition or process restart (showing a snackbar twice on rotation would be a bug). If it's "do something," it's an effect; if it's "display something," it's state.

## Reference implementations

- [`AddCardEffect.kt`](../feature/vault/src/commonMain/kotlin/com/cypressit/cyvault/feature/vault/addcard/presentation/viewmodel/AddCardEffect.kt)
- [`VaultListEffect.kt`](../feature/vault/src/commonMain/kotlin/com/cypressit/cyvault/feature/vault/vaultlist/presentation/viewmodel/VaultListEffect.kt)
- [`VaultHomeEffect.kt`](../feature/vault/src/commonMain/kotlin/com/cypressit/cyvault/feature/vault/home/presentation/viewmodel/VaultHomeEffect.kt) / [`VaultScaffoldEffect.kt`](../feature/vault/src/commonMain/kotlin/com/cypressit/cyvault/feature/vault/home/presentation/ui/VaultScaffoldEffect.kt)
- [`AuthEffect.kt`](../feature/authentication/src/commonMain/kotlin/com/cypressit/cyvault/feature/authentication/presentation/viewmodel/AuthEffect.kt)
