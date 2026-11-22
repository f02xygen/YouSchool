# Arch Linux and student positions: validation

The Linux client is a JVM/Compose Desktop application. It shares the eSchool network client,
DTOs, domain models, repository synchronization, notification rules, theme palettes,
grade badges and calculator with Android. Android-specific services and Linux-specific
SQLite/Secret Service/desktop notifications are separate adapters.

## Verified locally (2026-11-22, Arch Linux x86_64)

- Shared tests: 10 passed, including student-role filtering, required selection, per-account
  remembered choice, unavailable positions, login restoration, HTTP 401 restoration,
  ambiguous switch failure and serialized requests/cache writes.
- Desktop tests: 5 passed. SQLite queues/read flags survive reopening and stay separate
  between schools; grade replacement preserves other periods and precise weights;
  an old-school request waiting during a switch is cancelled before using the new session.
  Compose tests cover all main screens, settings navigation, switch/selection semantics,
  theme persistence and light/dark fixture captures.
- Android unit tests: 30 passed. `lintDebug`, debug and release APK builds passed.
- Android instrumentation: 8 discovered, 7 passed and 1 assumed/skipped (new-grade
  notification integration requires a logged-in account). After granting notification
  permission, the silent homework notification check passed. Migration checks preserve
  the queued homework action through versions 6, 7, 8 and 9 into version 10.
- `makepkg` built `youschool-0.3.11-1-x86_64.pkg.tar.zst`; package metadata and paths were
  inspected. The bundled runtime launcher displayed the login screen on the actual Arch
  desktop. A tiling-window resize issue under Niri was reproduced and addressed by the
  packaged `start-youschool` launcher setting `_JAVA_AWT_WM_NONREPARENTING=1`.
- A separate fixture/source UI finish review found accessibility issues; named controls,
  single selectable/toggleable rows and 48dp target heights were fixed and rechecked.
  Final review disposition: ship at the fixture/source scope.

## Release gates and limits

- Keep the PR as a draft until switching is exercised on a real account with two student
  organizations. Fixtures are not evidence of server-side switching. Check both schools,
  restart, session expiry and pending homework isolation; avoid changing live school data
  or sending messages without a specifically authorized test.
- reCAPTCHA uses JavaFX WebView on the real eSchool origin and its token callback;
  completing a live challenge has not been verified.
- Secret Service storage and Linux notification delivery are integrated, but a full
  keyring-unlock/notification-daemon end-to-end session has not been independently checked.
- The package was built and its runtime launched; system-wide `pacman -U` installation,
  an AUR publication, native screen-reader behavior and other desktop environments were
  not tested. Packaging includes the Java runtime; Android SDK is unnecessary for a
  `-PdesktopOnly=true` build.
- Runtime stderr on this desktop emitted `pure virtual method called` / `terminate called
  without an active exception`; the JVM stayed alive and the login window rendered and
  accepted input. The origin of that diagnostic remains unlocalized. Treat this as a
  remaining runtime validation item rather than claiming a clean launch log.
- Notifications stop after the Linux application exits; there is no background service
  or automatic startup. Android retains WorkManager scheduling.

Linux caches and pending queues use separate database files per account/position rather
than adding scope columns to every table. Android retains its previously owned legacy
file; an unowned legacy file is preserved and never assigned to a later login. Android
migration paths avoid destructive migration for supported versions 6–10.
