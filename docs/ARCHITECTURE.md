# Architecture

## Guiding constraint

Two data sources — Bandcamp (via Subsonic) now, local files later — need to feel
like one library to the user. The architecture exists mainly to make that true
without a rewrite when phase 2 starts.

## Layers

```
Screens (Compose UI)
    |
Logic layer (ViewModels)
    |
Data sources (interchangeable)
    |-- Subsonic client (Bandcamp)
    |-- Local sync client (phase 2)
```

**Screens** — Now Playing, Library, Search. Follow the Light SDK's
`LightScreen` / `LightScreenViewModel` pattern (MVVM: screens display state,
ViewModels own the logic and expose it via Compose `State` or `Flow`).

**Logic layer** — decides what the screens show, drives playback, and talks to
whichever data source(s) are active. This layer should never know or care
whether a given track came from Bandcamp or local sync — see docs/DATA_SCHEMA.md
for how that's normalized.

**Data sources** — both implement the same interface from the logic layer's point
of view (fetch library, fetch stream URL, etc.), even though what happens behind
that interface is completely different per source:

- *Subsonic client:* talks directly to `bandcamp.com/api/subsonic` per the
  OpenSubsonic spec.
- *Local sync client (phase 2):* talks to our own backend, which a user uploads
  files to via a web dashboard we build. Tracks sync down into the app's own
  private storage on the phone, then get read locally — same shape as Light's
  own built-in Music Tool, just running on our infrastructure instead of theirs.
- *Local dev source (interim, phase 1):* reads MP3s from the app's private
  storage, populated by `adb push` in the emulator. Exists so the playback
  engine can be built while Bandcamp's collection endpoints are blocked (see
  docs/ROADMAP.md, "Pivot: playback via local files"). Deliberately minimal —
  no upload flow, no sync state, not user-facing. It is, however, the seed of
  the phase 2 local sync client's "read tracks from private storage" half, so
  it should implement the same data-source interface properly rather than
  being a hack in the playback code.

### Credential storage and ownership

Each data source owns its own authentication state — the logic layer never
touches credential storage directly, it only asks a data source "are we
logged in?" and reacts to yes/no. This keeps the same "logic layer doesn't
care which source it's talking to" principle intact for login/auth, not just
for library data.

For the Subsonic client specifically:

- Bandcamp credentials are stored using **Android's Keystore-backed encrypted
  storage**, scoped to our app's own private sandbox. This is standard
  per-app Android storage — LightOS runs as a full OS (an Android fork), not
  as a sandbox-within-an-app, so our tool is sandboxed the same way any
  Android app is, and system-level LightOS services (permissions, push,
  background jobs) aren't involved in this at all.
- The Light SDK's own **Authenticator example app** demonstrates this exact
  pattern (Keystore-backed encrypted storage) and is a useful reference when
  implementing this.
- On app launch, the Subsonic client checks for stored credentials and
  attempts the Subsonic `ping` endpoint automatically, before the logic layer
  decides whether to show a login form. The logic layer only sees a
  success/failure result — it never reads or writes credentials itself.
- The same ownership shape applies to clearing credentials: `SubsonicClient.
  logout()` is the only way to clear stored Bandcamp credentials, and it's a
  thin wrapper over `SubsonicCredentialStore.clear()` (which existed from the
  start but had no caller until logout was added). The logic layer calls
  `logout()` and reacts to the state change; it doesn't touch the credential
  store directly, same as login. Currently only exposed as a **LOG OUT**
  button on the logged-in screen for re-testing credential entry — not a
  designed account-management feature yet.

Local sync (phase 2) will need its own equivalent auth/connection check
against our backend; it should follow the same shape (data source owns the
check, logic layer just asks) rather than being designed as a special case.

### Credential entry: QR and manual paths

Bandcamp's Subsonic username and password (from `bandcamp.com/settings?pane=fan`)
are 32-character generated strings, not human-typed ones. Because LightOS
exposes no browser, email client, or easy self-messaging, getting these two
strings onto the device is a real UX problem, not a minor inconvenience — see
docs/ROADMAP.md for the decision to support two entry paths rather than one.

**Manual entry.** The baseline path: a plain text-entry login form on-device.
Slow and unpleasant given the string length, but requires no extra
infrastructure and nothing new for Light's review process to evaluate. Always
available as a fallback.

**QR entry.** A faster path for users willing to use a second device:

- A small **companion webpage** (static, no backend) where the user pastes
  their Subsonic username and password. The page generates a QR code
  client-side, in the browser, using a JS QR-encoding library. Nothing is
  transmitted anywhere — the QR is just those two strings re-drawn as a
  barcode. Not part of the phase 2 backend; this needs no server at all.
  Lives at `web/pair.html` in this repo — kept outside `tool/`, `sdk/`,
  `plugin/`, and `gradle/` since it's a static file to be deployed
  somewhere (e.g. GitHub Pages), not something the Android build should
  ever touch.
- In light-stream, the login screen invokes the **Light SDK's camera
  component** — the same underlying building block used by the SDK's
  Authenticator example — to scan the code. This is our own scan screen and
  parser, built against the raw camera component directly. It does not call
  into or depend on the Authenticator app itself; Authenticator is only a
  reference for how to drive the camera component, not something we
  integrate with.
- Our parser expects our own format (plain JSON containing the username and
  password), not the `otpauth://` format Authenticator's own parser expects.
  These are two independent tools sharing one SDK primitive, not two tools
  that talk to each other.
- **Exact QR payload shape**, as produced by `web/pair.html`, for the
  on-device parser to match:
  ```json
  { "username": "<32-char subsonic username>", "password": "<32-char subsonic password>" }
  ```
  Field names are `username` and `password` (not `user`/`pass` or anything
  else) — if the parser is built to expect different keys, either update
  the parser or update `web/pair.html` so the two stay in sync. No other
  fields are present in the payload.
- The QR encodes the actual login credentials in plain text, not a rotating
  secret (contrast with TOTP/2FA-style QR codes, which encode a seed used to
  generate a new code on each use rather than a fixed one). Anyone who
  captures the code image has the underlying Bandcamp login, so the companion
  page should carry a clear warning not to screenshot or share it, and should
  not cache or persist what the user pastes in.

Both paths land in the same place: the Subsonic client's existing
Keystore-backed credential storage (see above). The entry method only affects
how the two strings arrive on-device, not how they're stored afterward.

## Why local files aren't a simple "point at a folder" feature

LightOS does not expose Android's standard file-picker/storage layer to tools,
so there is no way for a user to browse their phone's file system and hand us a
folder path. The only viable pattern (matching Light's own Music Tool) is:
upload via a web dashboard → server-side processing → sync down into the app's
private storage. This makes phase 2 a real second project (a backend + upload
flow), not just an in-app feature. See docs/ROADMAP.md.

There is a possibility that Light's own dashboard could eventually support
third-party tool uploads directly, which would remove the need for us to build
our own backend — but this is unconfirmed and not something to design around
yet.

## Known technical constraints (see docs/SETUP.md for detail)

- No Google Play Services on-device
- Restricted/allowlisted third-party libraries
- No finished distribution pipeline as of this writing
- Bandcamp's Subsonic collection endpoints (`getArtists`/`getIndexes`/
  `getMusicFolders`) currently 500 when called from the app specifically
  (not from curl, not from other tooling) — external, beta-API-side issue,
  not something in our control. A diagnostic spike has since ruled out the
  HTTP client stack (Ktor vs raw OkHttp) and the `c=` client name; the only
  untested variable is emulator vs real hardware, and the hardware test is
  deferred by choice. See docs/ROADMAP.md ("Currently blocked" and the
  sections following it) and `SPIKE_FINDINGS.md` on the spike branch.
