# Development Setup

## Prerequisites

- [Android Studio](https://developer.android.com/studio)
- [GitHub Desktop](https://desktop.github.com/)
- A GitHub account with a personal access token that has **package read** access
  (Light hosts SDK library builds on GitHub Packages, which requires
  authentication even though the code is public)

## Getting the code

This project is a **fork** of [lightphone/light-sdk](https://github.com/lightphone/light-sdk),
not a plain clone. Forking (rather than just downloading a copy) keeps a live link
back to Light's repo, which matters because the SDK is early-stage and changing
frequently.

1. Fork `lightphone/light-sdk` into your own GitHub account, named `light-stream`.
2. In GitHub Desktop: **File → Clone Repository** → select your fork → choose a
   local folder → Clone.

Do not download as a ZIP — that strips the Git history and the link back to
Light's repo, making future updates and pull requests impossible.

## Staying in sync with Light's SDK

GitHub Desktop does not support adding a second remote (`upstream`) through its
UI — that's a command-line-only step. Instead, use GitHub's built-in fork sync:

1. On github.com, go to your fork's page.
2. Click **Sync fork → Update branch**. This pulls Light's latest changes into
   your fork on GitHub.
3. Back in GitHub Desktop, click **Fetch origin**, then **Pull**, to bring those
   changes down to your computer.

Do this periodically — Light's README explicitly warns that things are moving
fast right now.

**Convention to reduce merge conflicts:** keep app-specific code inside the
`tool/` folder, which is where Light's docs say it belongs. Avoid modifying
`sdk/`, `plugin/`, or `gradle/` unless necessary — those are SDK internals, and
touching them increases the odds of a conflict on the next sync.

**One deliberate exception:** `web/` — static, non-Android files meant to be
deployed to a URL rather than compiled (e.g. `web/pair.html`, the Bandcamp
credential-pairing page — see docs/ARCHITECTURE.md). These don't belong in
`tool/` since they're not part of the app build at all, and keeping them
separate means the Gradle build never has to know they exist.

## Authenticating with GitHub Packages

Pick one:

**Option A — environment variables** (per terminal session):
```
GITHUB_ACTOR=your_username
GITHUB_TOKEN=your_token
```

**Option B — `local.properties` file** in the project root:
```
gpr.user=your_username
gpr.key=your_token
```
This file must never be committed to Git. Confirm it's listed in `.gitignore`
before doing anything else.

## Running the app

You don't need physical Light Phone hardware to start. Use an Android emulator
configured to approximate the Light Phone III:

- Resolution: 1080 × 1240, 3.92" screen
- Android API 34
- **No Google Play Services** — the Light SDK restricts which Android APIs and
  libraries are available, partly because of this. If a library assumes Play
  Services exists, it likely won't work here.

A more complete LightOS emulator (for testing push notifications and other
OS-level behavior) is available but more involved to set up — worth doing once
a basic build is running, not before. See Light's own docs for that.

## Building from the command line

Android Studio bundles its own JDK for Gradle, so this normally doesn't come
up inside the IDE. But if you run `./gradlew` directly from a terminal, make
sure `JAVA_HOME` points at a **JDK 17** install, not whatever your system
default is. A too-new JDK (JDK 26 has been observed to fail) breaks the
Android Gradle Plugin's `core-for-system-modules.jar` transform with a jlink
error, unrelated to anything in this project's own code:

```
export JAVA_HOME=/path/to/your/jdk-17
./gradlew :tool:compileDebugKotlin
```

## Known platform constraints to keep in mind

- **No general file-system access.** LightOS does not expose the standard
  Android storage/file-picker layer to tools. This is why phase 2 (local files)
  is designed around a web upload flow rather than an in-app file picker — see
  docs/ARCHITECTURE.md.
- **No finished distribution path yet.** As of this writing, there's no
  streamlined way to get a signed build onto a real device; sideloading via ADB
  is the only option. This may change — check Light's SDK repo for updates.
- **Library allowlist.** Light restricts which third-party libraries can be
  used. If we want to bring in a library that isn't currently allowed, Light's
  docs say to ask them directly.
