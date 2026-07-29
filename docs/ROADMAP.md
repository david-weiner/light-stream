# Roadmap

## Phase 1 — Bandcamp streaming

- [x] Environment set up (fork, GitHub Desktop, SDK syncing)
- [x] Basic app shell running in emulator (`HomeScreen` boots)
- [x] Subsonic login flow
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

Next up from the Phase 1 list above: fetching and displaying the collection.
