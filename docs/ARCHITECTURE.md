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

Local sync (phase 2) will need its own equivalent auth/connection check
against our backend; it should follow the same shape (data source owns the
check, logic layer just asks) rather than being designed as a special case.

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
