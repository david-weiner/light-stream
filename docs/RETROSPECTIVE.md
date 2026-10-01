# Retrospective

Written 30 September 2026, when the project was paused.

## Why we stopped

Other music apps for the Light Phone III now exist, and two of them connect to
Bandcamp and Subsonic servers. Finishing ours would add a fourth option to a
market of three, none of whom were asking. The remaining open question (why
Bandcamp 500'd our app) had stopped being blocking and become merely interesting.

One honest caveat: the existing apps are feature-rich (shuffle, lyrics, offline
downloads). If a deliberately bare-bones player is ever wanted, that gap is real.

## What worked

- **Docs before code.** The architecture, schema and design docs meant each new
  session started from shared facts rather than guesswork.
- **Handoff files** (`TASK_1` to `TASK_4`) gave each piece of work a clear goal,
  constraints, and a definition of done.
- **Time-boxed spikes.** The Task 1 spike answered a question in one run, then
  we threw the code away. That is what a spike is for.
- **Refusing to spoof.** We chose not to disguise our app as another client to get
  past Bandcamp's block. Correct call, and as it turned out unnecessary.

## What went wrong

1. **A check that could not fail.** We treated a successful `ping` as proof that
   login worked. Another developer's code notes that Bandcamp's `ping` answers OK
   even with no credentials. Our "login works" milestone proved nothing, and we
   built the library screens on top of it. Lesson: before trusting a test, ask
   what a failure would look like. If it cannot fail, it is not a test.
2. **Two variables mistaken for one.** We tested "Ktor versus raw OkHttp" and
   concluded the HTTP library was not the problem. But both sit on the same
   underlying Android networking layer, so we only changed the top layer.
   Lesson: change one variable at a time, and check that it really is one.
3. **The decisive clue came from outside.** A working app by another developer
   was the most useful diagnostic tool we had, and we found it last. Lesson:
   look for a known-working example early, then compare it with yours.
4. **Expensive tests deferred.** The real-device test was skipped as costly. It
   turned out another app was already running on a real device. Lesson: re-check
   the cost of a test as circumstances change.

## What we found out (and did not)

Established:
- Raw OkHttp and Ktor/OkHttp fail identically.
- Changing the client name (`c=`) to `Tempus` makes no difference.
- Request lines, headers and auth style are not the cause.

Not established, but the best lead:
- The `musicplus` app (queueingqt/musicplus) reaches Bandcamp successfully and
  uses Ktor with the **CIO** engine, not OkHttp. It chose CIO to allow plain
  `http://` servers, not to get round Bandcamp, so this is a happy accident.
  CIO does its own encryption handshake, so it would present differently to
  Bandcamp. This is a theory, not a result. **We never tested it.**

## If this is ever resumed

1. Re-run spike cell A with the Ktor **CIO** engine, everything else unchanged.
   Roughly a one-line change.
2. If it works, replace the `ping`-based login check with a real request such as
   `getAlbumList2`, as `musicplus` does.
3. Report the finding to Bandcamp ("works with one engine, 500s with another").
4. Check the Light SDK for changes first. It was early-stage and moving quickly.

## Dev-cycle terms used in this project

- **Spike:** a short, throwaway experiment to answer one question.
- **Handoff:** a written brief for the next person (or session) to pick up work.
- **Retrospective:** a look back at what worked and what did not.
- **Sunsetting:** retiring a project deliberately, with notes, rather than letting
  it quietly rot.
