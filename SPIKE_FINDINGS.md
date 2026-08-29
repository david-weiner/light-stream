# Spike findings — Bandcamp 500s vs HTTP stack

Branch: `spike/bandcamp-500-http-stack`. Run against real Bandcamp Fan Settings
credentials, on the `Light_Phone_III(AVD) - 14` emulator, via a temporary
in-app harness (`SubsonicSpikeHarness` + `SpikeMatrixScreen`, see that
screen's commit for how it was wired). All four cells hit
`getArtists` back-to-back in one run (2026-08-29 03:56 UTC).

## Tempus's real values (from source, not guessed)

Read directly from `eddyizm/tempus` (`SubsonicPreferences.java`,
`Subsonic.java`):

- `c=` → **`Tempus`** (capital T, literal) — the handoff doc's placeholder of
  lowercase `tempus` was a guess; the real value is `Tempus`.
- `v=` → `1.15.0` (already ruled out separately in ROADMAP.md's 2026-08-17
  entry, using our own `v=1.16.1` vs `1.15.0`; not re-tested here since this
  spike is scoped to HTTP stack + client name).
- Nothing unusual in request construction: plain query-param map, default
  OkHttp `User-Agent`, no custom headers, no ALPN/protocol restrictions.
- The "fix bandcamp crashing on login" changelog line traces to commit
  `52d0d84a` — a client-side fix for an uninitialized-property crash when
  Gson couldn't populate the response from a malformed/incomplete JSON body.
  Not a 500-with-empty-body scenario; doesn't look related to what we're
  seeing.

## Matrix results

| # | HTTP stack | `c=` value | Status | Body | Backend pool | `x-bc-app-id` |
|---|---|---|---|---|---|---|
| A | Ktor/OkHttp | `light-stream` | **500** | empty | lindacentral16-collection-public-apis1 | 3429613 |
| B | Ktor/OkHttp | `Tempus` | **500** | empty | lindacentral18-collection-public-apis1 | 3429615 |
| C | raw OkHttp | `light-stream` | **500** | empty | lindacentral16-collection-public-apis1 | 3429613 |
| D | raw OkHttp | `Tempus` | **500** | empty | lindacentral18-collection-public-apis1 | 3429615 |

All four: `fastly-restarts=1`, `server=nginx`, no distinguishing response
header beyond the pool round-robin already documented in ROADMAP.md (A/C
landing on one backend instance, B/D on another — consistent with normal
round-robin between separate requests, not a routing difference caused by
`c=`).

## A vs C — exact request-line diff

**A (Ktor/OkHttp):**
```
GET https://bandcamp.com/api/subsonic/rest/getArtists?u=<user>&t=<token>&s=<salt>&v=1.16.1&c=light-stream&f=json
Accept: application/json
User-Agent: ktor-client
Host: bandcamp.com
Connection: Keep-Alive
Accept-Encoding: gzip
```

**C (raw OkHttp):**
```
GET https://bandcamp.com/api/subsonic/rest/getArtists?u=<user>&t=<token>&s=<salt>&v=1.16.1&c=light-stream&f=json
Host: bandcamp.com
Connection: Keep-Alive
Accept-Encoding: gzip
User-Agent: okhttp/5.3.2
```

Diff:
- **Query string**: byte-identical structure and encoding (`u`/`t`/`s`/`v`/`c`/`f`,
  same escaping — none of these values needed percent-encoding either way).
  Query-string encoding is not the differentiator.
- **Headers**: Ktor adds `Accept: application/json` (from the
  `ContentNegotiation` plugin) and sends `User-Agent: ktor-client`; raw OkHttp
  sends no `Accept` header and its own default `User-Agent: okhttp/5.3.2`.
  Header order also differs. Despite these differences, both got the
  byte-identical outcome (500, empty body, same backend pool) — reconfirms
  ROADMAP.md's earlier finding that neither `Accept`/`Accept-Encoding` nor
  `User-Agent` are the differentiator, this time with a live side-by-side
  request-line diff rather than inference.

## Interpretation

**All four cells failed identically.** Per the handoff doc's interpretation
guide, this means:

- The Ktor-vs-raw-OkHttp theory is dead — reconfirms the 2026-08-17
  bare-`OkHttpClient()` finding already in ROADMAP.md, this time with c=Tempus
  also tested on both stacks.
- **The client-name filtering theory is also dead.** Cell B used Tempus's
  *actual* `c=Tempus` value (verified from source, not guessed) on the exact
  same Ktor/OkHttp stack that fails as `light-stream` — no difference.
  Bandcamp is not allowlisting `getArtists` by `c=`, at least not by
  admitting `Tempus` while rejecting `light-stream`.

This leaves ROADMAP.md's existing conclusion as the only remaining, untested
lead: the TLS **ClientHello** fingerprint (BoringSSL/Android vs
LibreSSL/curl), and specifically the one variable ROADMAP.md already flagged
as deliberately deferred — **real device vs. emulator**. Everything in this
spike, like everything before it, ran on the emulator only.

## Recommendation

- Do not proceed to Task 3 (migrating `SubsonicApi` to raw OkHttp) — cell C
  already disproves that this would help.
- Do not spoof `c=Tempus` or any other client name in a shipped build — the
  test above shows it wouldn't fix anything anyway, so the question of
  whether to impersonate another client doesn't even arise here.
- Next real diagnostic step is still the one ROADMAP.md already deferred:
  repeat this exact matrix (or just cell A) on a real physical Android
  device instead of the emulator. If that succeeds, this was always an
  emulator-TLS-provider artifact, not a Bandcamp block. If it still 500s,
  the TLS-fingerprint theory itself is likely wrong (or the allowlist is
  narrower than "any non-emulator device"), and the next move is external:
  ask Bandcamp support directly what they allowlist, and/or file an issue on
  `eddyizm/tempus` asking whether the maintainer has ever seen this specific
  500-with-empty-body behavior (their one Bandcamp-related fix was for a
  different, client-side crash — doesn't look like the same bug).
- No permanent changes made to `main`; this branch (`SubsonicApi` visibility
  tweaks, `SubsonicSpikeHarness.kt`, `SpikeMatrixScreen.kt`, the temporary
  `@InitialScreen` swap in `HomeScreen.kt`) is throwaway and should not be
  merged.
