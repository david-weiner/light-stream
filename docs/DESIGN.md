# Design

## Principles

Follows Light's own design ethos: minimal, intentional, non-addictive. This
isn't just a style preference — Light explicitly reviews community tool
submissions for fit with this ethos, and bans "infinite feed"-style patterns
outright.

- No account creation, social features, or recommendation/discovery feeds
- No badges, unread counts, or "new for you" prompts
- No color used decoratively

## Color

**Color appears nowhere except album thumbnails.** Everything else — text,
backgrounds, selection state, the "currently playing" indicator — is
monochrome. Selection/playing state is communicated through weight, an
underline, or a marker glyph, never color.

## Now Playing screen

- Large album art
- Track / artist name
- Minimal transport controls: play/pause, skip, scrub bar
- No lyrics, no visualizer, no social elements

## List views — thumbnail rule

Thumbnails are **not** tied to a specific screen (e.g. "Album view" vs.
"Playlist view"). The actual rule is:

> A thumbnail appears once per unique album grouping. If the list can no
> longer guarantee grouping-by-album (e.g. a playlist mixing multiple albums),
> each row gets its own thumbnail instead.

Concretely, as of this writing:

- **Sorted by Album:** one thumbnail shown once, on the album's header row.
  Track rows underneath are plain text, no art.
- **Playlist (not grouped by album):** each row gets its own small thumbnail,
  since adjacent tracks may belong to different albums.

This is written as a general rule rather than two hardcoded cases so it
extends cleanly to future views (Artist, Search results, etc.) without
re-deciding this each time.

## Known open question

Bandcamp's Subsonic API reportedly does not return album art directly on
album-list endpoints in all cases — cover art may need to be fetched via the
artist/band endpoint instead. Needs verification once we're actually building
against the live API; may affect how eagerly thumbnails can be loaded.
