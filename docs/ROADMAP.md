# Roadmap

## Phase 1 — Bandcamp streaming

- [x] Environment set up (fork, GitHub Desktop, SDK syncing)
- [x] Basic app shell running in emulator (`HomeScreen` boots)
- [x] Subsonic login flow
- [x] Credential entry: offer both QR and manual paths (see below)
- [ ] Fetch and display collection (artists → albums → tracks)
- [ ] Playback: play/pause/skip, basic queue
- [ ] Search within collection
- [ ] Playlists: view, create, edit (synced back to Bandcamp)
- [ ] Design pass matching docs/DESIGN.md (monochrome, thumbnail rule)

### Decision: credential entry offers a choice, not a single path

Bandcamp's Subsonic username/password are 32-character generated strings,
and LightOS has no browser, email, or easy self-messaging to lean on for
copy-paste. Rather than pick one entry method, the login screen will offer
the user a choice:

- **Manual entry** — type the strings on-device. Always available, no extra
  infrastructure, nothing new for Light's review to look at.
- **Scan QR** — a companion webpage (static, client-side only, no backend)
  turns pasted-in credentials into a QR code; the app scans it using the
  Light SDK's camera component. Faster, but depends on a second device and
  an SDK camera feature that's early-stage like the rest of the SDK.

See docs/ARCHITECTURE.md ("Credential entry: QR and manual paths") for the
implementation shape, including why this doesn't reuse or depend on the
SDK's Authenticator example app despite using the same underlying camera
component.

## Phase 2 — Local files

Treated as its own milestone, not a quick follow-on to phase 1 — it requires a
real backend, not just app changes. See docs/ARCHITECTURE.md for why.

- [ ] Design upload/sync backend (separate small project)
- [ ] Web upload interface for users
- [ ] Server-side conversion/storage
- [ ] Sync-to-device flow (private app storage, matching Light's own Music Tool
      pattern)
- [ ] Local tracks appear in the same library/playback interface as Bandcamp
      tracks, distinguished only internally (see docs/DATA_SCHEMA.md)

## Later / unscheduled

- Offline caching for streamed Bandcamp tracks (deliberately deferred — see
  docs/ARCHITECTURE.md)
- Investigate whether Light's own dashboard could eventually handle local
  file sync for approved tools, which would remove the need for our own
  backend. Unconfirmed; not a current dependency.
- Formal open-source license selection, ahead of submitting for Light's
  approval process (required for community tools)

## Current sprint — done

**Goal: get a minimal app shell running and a successful Subsonic login.**

- [x] On launch, Subsonic client checks for stored credentials (Android
      Keystore-backed encrypted storage) and attempts `ping` automatically
      — see docs/ARCHITECTURE.md for the credential storage/ownership design
- [x] `HomeScreen` displays a login form (Bandcamp Subsonic username/password
      or app-specific credentials, per Bandcamp's Fan Settings flow) only if
      no stored credentials exist, or a stored login attempt fails
- [x] On submit, call Subsonic `ping` endpoint to confirm credentials work,
      then store them via the Subsonic client
- [x] Display success/failure state — nothing fancier yet

Implemented in `tool/src/main/kotlin/com/thelightphone/sample/subsonic/`
(`SubsonicClient`, `SubsonicApi`, `SubsonicCredentialStore` +
`SubsonicKeystore`/`SubsonicCredentialCipher` for the Keystore-backed
encryption) and `HomeScreen.kt`. Verified end-to-end in an emulator against
real Bandcamp Fan Settings credentials.

## Credential entry — done

Implemented the choice from "Decision: credential entry offers a choice, not
a single path" above. `HomeScreen` now shows a `ChooseEntryMethod` screen
(instead of jumping straight to the manual form) with **MANUAL ENTRY** and
**SCAN QR** buttons:

- **Manual entry** — unchanged text-entry form, now reachable via a button
  and with a back arrow to return to the choice screen.
- **Scan QR** — `QrLoginScreen` (a `SimpleLightScreen`, since scanning is a
  full-screen camera view, not something that fits inside `HomeScreen`'s own
  state machine) wraps the SDK's `LightQrCodeScanner` and hands the decoded
  string to `QrCredentialsParser`, which matches `web/pair.html`'s
  `{"username": ..., "password": ...}` JSON shape. A parse failure returns
  the user to the choice screen with an error message instead of crashing or
  silently doing nothing.

Both paths converge on the same `SubsonicClient.login()` call (ping +
Keystore-backed storage), so entry method has no effect on what happens
afterward — matching the architecture doc's intent.

## Logout — done

Added so credential entry could be re-tested from within the app instead of
clearing app data via adb between runs. `SubsonicClient.logout()` calls the
`SubsonicCredentialStore.clear()` that already existed (written alongside the
original login flow, but unused until now). `HomeScreenViewModel.logout()`
calls it and drops the state back to `ChooseEntryMethod`; the logged-in
screen gets a **LOG OUT** button in its bottom bar to trigger it. No UI
currently surfaces this as an account-management feature — it's a thin
affordance for development/testing, not a designed "settings" flow.

Next up from the Phase 1 list above: fetching and displaying the collection.
