# light-stream

A minimal music player for the Light Phone III. Streams a Bandcamp collection via
the Subsonic API, with local file support planned for a later phase.

Built as a "tool" for [LightOS](https://github.com/lightphone/light-sdk), following
Light's own design principles: no infinite feeds, no social features, no clutter.

## Status

**Phase 1 in progress: Bandcamp streaming.** Development environment is set up;
implementation has not started yet. See [docs/ROADMAP.md](docs/ROADMAP.md) for the
current sprint.

## What this app does

- Logs into a user's Bandcamp collection via Subsonic credentials
- Browses purchased music: artists, albums, playlists
- Plays music, with basic queue/search
- (Phase 2) Plays locally-uploaded music in the same interface

## What this app deliberately does not do

- No account system, no social features, no recommendations/discovery feed
- No offline caching in phase 1
- Nothing that resembles an "infinite scroll" experience — this is a hard requirement
  from Light's own submission guidelines, not just a style preference

## Documentation

| Doc | Purpose |
|---|---|
| [docs/SETUP.md](docs/SETUP.md) | Getting the dev environment running |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | How the app is structured, and known platform constraints |
| [docs/DESIGN.md](docs/DESIGN.md) | Look and feel rules |
| [docs/DATA_SCHEMA.md](docs/DATA_SCHEMA.md) | Data models used across both music sources |
| [docs/ROADMAP.md](docs/ROADMAP.md) | Phase breakdown and current sprint |

## Bug tracking

Bugs are tracked as [GitHub Issues](../../issues) using the bug report template
under `.github/ISSUE_TEMPLATE/`, not in a document. This keeps status, history, and
assignment in one place instead of a list that goes stale.

## Tech stack

- Kotlin, Jetpack Compose, MVVM, Coroutines (required by the Light SDK)
- [light-sdk](https://github.com/lightphone/light-sdk) (forked, kept in sync via
  GitHub's "Sync fork" feature — see docs/SETUP.md)
- [Subsonic / OpenSubsonic API](https://opensubsonic.netlify.app/docs/) for Bandcamp

## License

TBD — see note in docs/ROADMAP.md. Light requires community tools submitted for
approval to be open source.
