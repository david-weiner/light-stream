# Roadmap

## Phase 1 — Bandcamp streaming

- [x] Environment set up (fork, GitHub Desktop, SDK syncing)
- [ ] Basic app shell running in emulator (`HomeScreen` boots)
- [ ] Subsonic login flow
- [ ] Fetch and display collection (artists → albums → tracks)
- [ ] Playback: play/pause/skip, basic queue
- [ ] Search within collection
- [ ] Playlists: view, create, edit (synced back to Bandcamp)
- [ ] Design pass matching docs/DESIGN.md (monochrome, thumbnail rule)

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

## Current sprint

**Goal: get a minimal app shell running and a successful Subsonic login.**

- [ ] `HomeScreen` displays a login form (Bandcamp Subsonic username/password
      or app-specific credentials, per Bandcamp's Fan Settings flow)
- [ ] On submit, call Subsonic `ping` endpoint to confirm credentials work
- [ ] Display success/failure state — nothing fancier yet

Everything else in Phase 1 is backlog until this is working end-to-end.
