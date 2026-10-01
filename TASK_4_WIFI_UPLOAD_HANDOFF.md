# Task 4 — Wi-Fi upload page (optional follow-on to Task 3)

**Type:** Feature, exploratory (permanent code if it works well; keep it
cleanly removable)
**Repo:** github.com/david-weiner/light-stream
**Branch:** `feature/wifi-upload` off main, **after Task 3 is merged** — this
depends on `LocalDevSource` existing.
**Context docs to read first:** docs/ARCHITECTURE.md, docs/ROADMAP.md
("Pivot" section), `docs/LOCAL_DEV_SOURCE.md` (from Task 3).

## Goal

Replace `adb push` as the way to get MP3s into the app: the app runs a small
HTTP server on the local Wi-Fi network; the user browses to the phone's
address from a computer and uploads files through a simple web page. Files
land in the same private-storage directory `LocalDevSource` already reads.

This doubles as a dry run for Phase 2's upload flow, and may inform whether
Phase 2 even needs its own cloud backend for users who'd accept
same-network-only transfers.

## Constraints

- **Allowed libraries:** Ktor server artifacts (`io.ktor` is allowlisted
  wholesale — `ktor-server-core` etc. are used by the SDK itself). No other
  new dependencies.
- Do not modify `sdk/`, `plugin/`, `gradle/`.
- The upload page is served *by the app* — plain HTML/JS embedded as an
  asset (`.html`/`.css` are on the asset allowlist). It is unrelated to
  `web/pair.html`, which is deployed off-device; do not merge the two.
- Keep the whole feature behind one entry point (a screen or dev toggle) so
  it can be cleanly removed or reworked if Light's review dislikes an app
  that runs a server.

## Behaviour

- A screen (e.g. "ADD MUSIC") that starts the server and displays the URL to
  visit (`http://<phone-ip>:<port>`) as text — and, since the QR scanner
  exists, consider whether showing it makes sense here too. Note: LightOS
  devices are the *scanner* in our existing flow, not the displayer; the
  computer has no scanner. Plain text URL is the baseline; don't overbuild.
- Server runs **only while that screen is open** — stopped on navigation
  away. No background server, ever: battery, privacy, and Light-ethos
  reasons all point the same way.
- Upload page: choose file(s) → upload → per-file success/failure. Accept
  `.mp3` only for now (validate content-type and extension server-side;
  reject everything else politely).
- Uploaded files go to the `LocalDevSource` directory; the library should
  reflect new files next time it loads (a manual "rescan" is fine — no
  file-watching needed).
- Bind to the Wi-Fi interface. If no Wi-Fi, say so on-screen rather than
  binding to nothing.

## Security notes (proportionate, not paranoid)

- LAN-only by design; never expose beyond the local network.
- Cap upload size (e.g. 100 MB/file) and sanitise filenames (strip path
  separators; generate a safe name if needed) so a crafted request can't
  write outside the music directory.
- No credentials or personal data pass through this server; it only accepts
  audio files. State this plainly in the on-screen text.

## Testing

- Emulator note: reaching a server *inside* the emulator from the host
  requires `adb forward tcp:<port> tcp:<port>` — document the command on
  the screen's dev notes and in the doc update. On real hardware it's just
  the phone's LAN IP.
- Test: upload a valid MP3 (appears in library), upload a non-MP3
  (rejected), kill Wi-Fi mid-upload (fails gracefully, partial file cleaned
  up).

## Deliverables

1. The feature on its branch, PR against main for review (no self-merge).
2. Doc updates in the same PR: `docs/LOCAL_DEV_SOURCE.md` (upload page as
   the second way in), ROADMAP.md (note under the Pivot section), and a
   short ARCHITECTURE.md paragraph on the in-app server and its
   only-while-screen-open lifecycle.
3. A one-paragraph written assessment: does this pattern look viable as the
   basis for Phase 2 (perhaps minus the cloud backend), or is it dev-only?

## Suggested sub-agent split

Small enough for a single agent plus orchestrator review. If splitting:
one agent on the Ktor server + file handling, one on the screen + embedded
HTML page.
