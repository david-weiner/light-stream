# Task 3 — Playback engine + local dev source

**Type:** Feature (permanent code, lands on main via a feature branch)
**Repo:** github.com/david-weiner/light-stream
**Branch:** `feature/playback-local-source` off main
**Context docs to read first:** docs/ARCHITECTURE.md (layers, data sources,
"Local dev source" entry), docs/DATA_SCHEMA.md, docs/ROADMAP.md ("Pivot:
playback via local files"), docs/DESIGN.md (Now Playing spec — but see scope
note below).

## Goal

A working playback engine (play/pause/skip, basic queue) built against local
MP3 files, structured so Bandcamp streaming plugs in unchanged once its
endpoints unblock. Two deliverables, in dependency order:

1. **`LocalDevSource`** — a data source that reads MP3s from the app's
   private storage (populated via `adb push` in the emulator) and exposes
   them through the same shapes the Subsonic client uses
   (docs/DATA_SCHEMA.md: `Track`/`Album`/`Artist`, `source = local`).
2. **Playback engine + minimal Now Playing screen** — media3-based
   play/pause/skip/queue, driven by `Track` objects regardless of source.

## Constraints

- **Allowed libraries:** `androidx.media3` is on the SDK allowlist
  (`ALLOWED_DEPENDENCIES` in the SDK's `LightSdkPlugin.kt`). Use media3
  (ExoPlayer) — do not use the legacy `MediaPlayer` API or anything not
  allowlisted.
- Do not modify `sdk/`, `plugin/`, `gradle/`.
- Do not touch `SubsonicClient`'s existing public API or the login flow.
- Kotlin, Compose, MVVM, per the SDK's `LightScreen`/`LightScreenViewModel`
  pattern — follow the existing screens (`ArtistsScreen` etc.) as reference.

## Part 1 — LocalDevSource

- Reads audio files from a fixed directory inside the app's private storage
  (e.g. `filesDir/music/`). Document the exact path and the `adb push`
  command in a new short doc: `docs/LOCAL_DEV_SOURCE.md`.
- Extract metadata (title/artist/album/duration) from ID3 tags via
  `MediaMetadataRetriever` (framework API, no new dependency). Fall back to
  filename for missing titles; group tagless files under an "Unknown Album".
- Map into the DATA_SCHEMA.md shapes. **ID strategy:** this is the moment the
  deferred prefix decision becomes real — use `local:<stable-id>` for local
  tracks and leave Subsonic IDs untouched for now (a `bandcamp:` prefix
  migration is out of scope; just don't create collisions).
- `streamUrl` for local tracks is a file URI. The playback engine must not
  care which form it gets — that's the whole point.
- Implement the same conceptual interface the logic layer expects
  (fetch artists/albums/tracks). If no common Kotlin interface exists yet
  between SubsonicClient and this source, extract one *minimally* — only
  the methods both actually share. Note in ARCHITECTURE.md if you do.
- The library screens (`ArtistsScreen` → `AlbumsScreen` → `TracksScreen`)
  should be able to display the local library. Simplest acceptable approach:
  a dev toggle or automatic fallback (if Subsonic collection fetch fails,
  show local). Flag whatever you choose in the PR description — this
  source-selection UX is temporary and will be revisited.

## Part 2 — Playback engine

- media3 `ExoPlayer` wrapped in a playback manager owned by the logic layer
  (a `PlayerViewModel` or similar) — screens observe state, never touch the
  player directly. Follow MVVM as everywhere else.
- Queue semantics: tapping a track in `TracksScreen` plays it and queues the
  rest of that album in order. Skip forward/back moves within the queue.
  Nothing fancier (no shuffle, no repeat, no manual queue editing) — those
  are not in the Phase 1 roadmap item.
- Minimal Now Playing screen: track title, artist, play/pause, skip
  forward/back, and a scrub bar. **Album art and the full DESIGN.md
  monochrome pass are explicitly out of scope** — that's the separate
  "Design pass" roadmap item. Plain functional UI is correct here.
- Background behaviour: playback should survive navigating between screens.
  Full background-audio/notification integration with LightOS is likely to
  have platform quirks — timebox it; if it fights back, in-app-only playback
  is acceptable for this task with a note in the PR.

## Testing

- Emulator only (see docs/SETUP.md for the AVD spec).
- Push 2–3 test MP3s including at least one with full ID3 tags and one with
  none, to exercise the fallback paths.
- Verify: browse local library → play → pause → skip → scrub → navigate away
  and back → still playing.

## Deliverables

1. `LocalDevSource` + playback engine + minimal Now Playing screen on the
   feature branch, PR against main for review (do not self-merge).
2. `docs/LOCAL_DEV_SOURCE.md` (path convention, adb push instructions,
   what metadata handling to expect).
3. Doc updates in the same PR: ROADMAP.md (tick/annotate the playback item),
   ARCHITECTURE.md (only if the interface extraction warrants it),
   DATA_SCHEMA.md (record the `local:` ID prefix decision as implemented).

## Suggested sub-agent split

- **Agent 1:** LocalDevSource + metadata extraction + docs/LOCAL_DEV_SOURCE.md.
- **Agent 2:** playback manager + Now Playing screen (can begin against a
  hardcoded file URI before Agent 1 finishes; converge at the end).
- **Orchestrator:** interface extraction decision, integration, doc updates,
  PR assembly.
