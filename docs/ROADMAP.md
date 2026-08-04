# Roadmap

## Phase 1 — Bandcamp streaming

- [x] Environment set up (fork, GitHub Desktop, SDK syncing)
- [x] Basic app shell running in emulator (`HomeScreen` boots)
- [x] Subsonic login flow
- [x] Credential entry: offer both QR and manual paths (see below)
- [x] Fetch and display collection (artists → albums → tracks) — implemented,
      currently blocked end-to-end by an upstream Bandcamp beta issue (see below)
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

## Fetch and display collection — done

Added `getArtists`/`getArtist`/`getAlbum` to `SubsonicApi` (sharing the salt/token
auth-query building that `ping` already used) and three matching methods on
`SubsonicClient` — `getArtists()`, `getAlbums(artistId)`, `getTracks(albumId)`
— each loading stored credentials internally and mapping Subsonic's DTOs into
the `Artist`/`Album`/`Track` shapes from docs/DATA_SCHEMA.md (new
`com.thelightphone.sample.library` package). `Artist.albumIds` and
`Album.trackIds` are left empty for now — Subsonic's list-level endpoints
don't return child IDs eagerly, so those only get populated by the more
detailed fetch that lazily happens on navigation, which the current
screens don't need.

Three new screens (`ArtistsScreen` → `AlbumsScreen` → `TracksScreen`), each a
`LightScreen` with its own `SubsonicClient` instance and a small
Loading/Loaded/Error `ViewModel`, following the same `navigateTo` +
`LightTopBar`/`LightScrollView` pattern as the SDK's Authenticator example.
Reached from a new **LIBRARY** button next to **LOG OUT** on `HomeScreen`'s
logged-in state. Track rows are the leaf of navigation — tapping one does
nothing yet, since playback doesn't exist until the next roadmap item.

Deliberately text-only, no thumbnails: the thumbnail rule and monochrome
styling from docs/DESIGN.md are scoped to the separate "Design pass" roadmap
item below, and DESIGN.md flags that Bandcamp album art may need a fallback
fetch worth handling in its own pass rather than half-done here.

### Currently blocked: Bandcamp's collection API 500s from the app (not curl)

`getArtists` (and `getIndexes`/`getMusicFolders` — every endpoint beyond
`ping`) returns `HTTP 500` with an empty body when called from the app,
100% reproducible, while the identical request (same credentials, same
salt/token auth, same query params) succeeds from `curl` every time —
verified over 20+ consecutive curl calls with zero failures. `ping` itself
works fine from the app.

One permanent change survived the diagnosis: `SubsonicApi.ensureSuccess()`
now includes response headers in the exception message it throws on a
non-2xx status, not just the truncated body — cheap, generically useful for
any future "why did this Subsonic call fail" without needing to reach for
adb again. Everything else added purely for this investigation (a
request/response-logging OkHttp interceptor, a forced HTTP/1.1 config, a
custom `User-Agent`) was reverted since none of it changed the outcome.

Ruled out via that temporary interceptor, comparing the app's actual
wire-level request/response against matched curl requests at each step:

- Auth style (plain `p=` vs salted `t=`/`s=` token) — not it, both use token
- HTTP/2 vs HTTP/1.1 — forced HTTP/1.1 client-side, still 500s
- `User-Agent` (Ktor's default `ktor-client` vs a custom identifying string)
  — set an explicit UA, still 500s
- `Accept-Encoding: gzip` — tested with/without, not it
- Request headers generally — a network-level interceptor confirmed the
  app's final wire request (`Host`, `Connection`, `Accept-Encoding`,
  `User-Agent`, `Accept`) is byte-identical in shape to what curl sends
- IPv6 — `bandcamp.com` has no `AAAA` record, not reachable either way
- Call sequencing (`ping` immediately before the collection call) — curl
  reproduces this sequence fine too
- Random backend flakiness — 20/20 curl calls succeeded back-to-back, so
  it isn't that
- TLS version/cipher suite — both negotiate `TLS 1.3` /
  `TLS_AES_128_GCM_SHA256`, identical

A packet capture (see conversation history, not preserved in-repo) confirmed
the TLS **ClientHello** fingerprints genuinely differ between curl (LibreSSL,
49 legacy cipher suites, 7 extensions) and the app (BoringSSL/Android, 15
modern cipher suites, 13 extensions including `padding`,
`psk_key_exchange_modes`, `session_ticket`). That's the last remaining
variable — everything else is proven identical — but it's not possible to
confirm the *exact* trigger (a specific extension, ClientHello byte length,
or deliberate client fingerprint allowlisting on Bandcamp's very new beta —
their own announcement names only three "supported" clients) without
Bandcamp-side visibility we don't have. Not attempting to spoof or mimic a
different TLS fingerprint to get past this — that would mean working around
whatever access control or bug is actually in place, deliberate or not.

**Not a bug in this codebase** — the Subsonic client implementation is
correct per spec and works via `ping`; this is external and out of our
control pending either Bandcamp's beta stabilizing or a response from their
side. Revisit this before starting playback, since playback needs track
data from the same blocked endpoints.

Next up from the Phase 1 list above: playback (play/pause/skip, basic queue)
— blocked on the above until collection fetching actually works end-to-end.
