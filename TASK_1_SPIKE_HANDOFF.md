# Task 1 — Diagnostic Spike: Bandcamp 500s vs HTTP stack

**Type:** Spike (throwaway code; the deliverable is an *answer*, not shippable code)
**Repo:** github.com/david-weiner/light-stream
**Branch:** create `spike/bandcamp-500-http-stack` off main. Do not merge without review.
**Context docs to read first:** docs/ROADMAP.md ("Currently blocked" section), docs/ARCHITECTURE.md

## Background

Every Subsonic endpoint beyond `ping` (`getArtists`, `getIndexes`, `getMusicFolders`)
returns HTTP 500 with an empty body when called from the app, 100% reproducible,
while identical requests succeed from curl. Prior diagnosis (see ROADMAP.md) ruled
out auth style, HTTP version, User-Agent, Accept-Encoding, header shape, IPv6,
sequencing, and backend flakiness. TLS ClientHello fingerprints differ between
curl and the app, but that comparison is curl-vs-Android, not app-vs-working-Android-app.

New information: **Tempus** (github.com/eddyizm/tempus) is a native Android Subsonic
client using Retrofit + OkHttp that demonstrably works against Bandcamp. Its base
params are the standard `u/t/s/v/c/f=json`. So Android per se is not blocked.

Our `SubsonicApi.kt` already uses **Ktor with the OkHttp engine**, so the TLS layer
should match Tempus. Remaining suspects:

1. What Ktor does above OkHttp: query-string encoding (the 32-char salt/token and
   any user params may be percent-encoded differently), OkHttpClient configuration
   Ktor applies (protocols/ALPN), header construction details.
2. The `c=` client-name parameter. Bandcamp's beta announcement names specific
   "supported clients"; server-side allowlisting on `c` is plausible.

## Constraints

- **Allowed libraries only.** The Light SDK build plugin allowlists dependencies
  (see `ALLOWED_DEPENDENCIES` in the SDK's `LightSdkPlugin.kt`). Relevant here:
  `com.squareup.okhttp3:okhttp` (allowed), anything `io.ktor` (allowed).
  **Retrofit is NOT allowed** — use raw OkHttp, not Retrofit.
- Do not modify anything under `sdk/`, `plugin/`, or `gradle/`.
- Do not touch `SubsonicClient`'s public API, credential storage, or any screen.
  All spike code lives inside/alongside `SubsonicApi` or a temporary test harness.
- Real Bandcamp credentials are required (user will supply at runtime — never
  hardcode or commit them).

## The experiment matrix

Run `getArtists` under each combination and record status code + body/headers:

| # | HTTP stack | `c=` value |
|---|---|---|
| A | Ktor/OkHttp (current, baseline) | current light-stream value |
| B | Ktor/OkHttp | `tempus` (Tempus's client name — verify its exact value in Tempus source, `SubsonicPreferences`/`Subsonic.java`) |
| C | Raw OkHttp (hand-built request) | current light-stream value |
| D | Raw OkHttp | `tempus` |

For the raw-OkHttp cells, build the URL string manually with explicit, minimal
percent-encoding — mirror what curl sends (capture curl's exact request line
with `curl -v` for reference). Reuse the existing salt/token generation from
`SubsonicApi`.

Also capture, for A vs C, the exact final request line (full encoded URL) via
an OkHttp network interceptor, and diff them. If they differ at all, that
difference is a finding even before looking at status codes.

## Interpreting results

- **C succeeds, A fails** → Ktor's request construction is the problem. Proceed
  to Task 3 (migrate `SubsonicApi` internals to raw OkHttp; keep its public
  surface identical).
- **B/D succeed where A/C fail** → Bandcamp is filtering on client name. STOP and
  report back — do not ship with a spoofed client name. The likely path is
  contacting Bandcamp about registering light-stream as a client. (Diagnosing
  this is fine; impersonating another client in a release is not — same
  principle as the earlier decision not to spoof TLS fingerprints.)
- **All four fail** → the stack theory is dead. Document the request-line diff
  findings, revert, and report. Next step is external (issue on Tempus repo
  asking the maintainer what they hit with "fix bandcamp crashing on login",
  and/or contacting Bandcamp).
- **All four succeed** → the upstream beta has been fixed in the meantime.
  Delete the spike, unblock the ROADMAP item, celebrate modestly.

## Deliverables

1. A short findings write-up appended to the spike branch as `SPIKE_FINDINGS.md`:
   the matrix with results, the A-vs-C request-line diff, and a recommendation.
2. No permanent code changes on main from this task. Any keeper changes happen
   in Task 3 with their own commits and doc updates (ARCHITECTURE.md, ROADMAP.md).

## Suggested sub-agent split

- **Agent 1:** read Tempus source; report its exact `c=` value, API version
  string, and anything unusual in request construction or the login-crash fix
  commit. Output: short notes for Agent 2.
- **Agent 2:** implement the matrix harness on the spike branch and run cells
  A–D in the emulator with user-supplied credentials.
- **Orchestrator:** diff analysis, interpretation, SPIKE_FINDINGS.md.
