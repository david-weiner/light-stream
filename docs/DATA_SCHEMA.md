# Data Schema

These are the shapes the logic layer works with. Both data sources (Subsonic
and, later, local sync) are responsible for translating their own native
format into these — the rest of the app should never need to know which
source a track came from.

## Track

| Field | Type | Notes |
|---|---|---|
| id | string | Unique within this app, not necessarily the source's native ID |
| title | string | |
| artist | string | |
| albumId | string | References Album.id |
| trackNumber | int? | Optional — local files may lack this |
| durationSeconds | int | |
| streamUrl | string | How to fetch/stream the audio; source-specific under the hood |
| source | enum: `bandcamp` \| `local` | Used for things like error messaging, not for UI branching |

## Album

| Field | Type | Notes |
|---|---|---|
| id | string | |
| title | string | |
| artist | string | |
| artworkUrl | string? | May require a fallback fetch for Bandcamp — see docs/DESIGN.md open question |
| trackIds | list\<string\> | Ordered |

## Artist

| Field | Type | Notes |
|---|---|---|
| id | string | |
| name | string | |
| albumIds | list\<string\> | |

## Playlist

| Field | Type | Notes |
|---|---|---|
| id | string | |
| name | string | |
| trackIds | list\<string\> | Ordered; may span multiple albums/artists |
| syncsToBandcamp | bool | True only for playlists created against the Bandcamp source |

## Open questions to resolve during implementation

- Exact ID strategy: do we prefix source-native IDs (e.g. `bandcamp:1234`) to
  guarantee uniqueness once local tracks are added in phase 2? Recommended,
  but not yet implemented.
- Local sync (phase 2) will need its own metadata model for the upload/sync
  process itself (upload status, conversion status, sync timestamp) — not yet
  designed. Add a `docs/LOCAL_SYNC_SCHEMA.md` when phase 2 design starts
  rather than overloading this doc.
