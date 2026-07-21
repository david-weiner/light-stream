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
