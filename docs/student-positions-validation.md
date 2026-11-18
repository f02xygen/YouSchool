# Student organization switching: validation

The implementation targets upstream `main` at `c6a8e818367291f5943039c12c3d8f59bf473efd` (0.3.10).
Only positions with `posTypeCode = S` are selectable. Parent and teacher features are outside this change.

## Local checks

Verified using Gradle with JDK 17 and Android SDK Build Tools 34.0.0:

- `testDebugUnitTest`: all 40 tests pass, including 13 position filtering, restoration, and cancellation regressions.
- `lintDebug`: passes.
- `assembleDebug`: produces `app/build/outputs/apk/debug/youschool_0.3.10.apk`.
- `assembleDebugAndroidTest`: builds the Room migration/isolation, synthetic session protocol, and Compose chooser tests.
- Room KSP generation and compilation complete successfully.
- `git diff --check` and the UI source detector pass.

The earlier sandbox socket and GitHub publishing restrictions have been resolved. The branch is published and draft PR #5 is open. The `Validate student position switching` workflow is running on GitHub, including emulator instrumentation tests; their execution is not yet confirmed by these local checks.

## Synthetic coverage

- Two organizations with the same student role, one student position, empty or non-student lists, invalid IDs, and a removed saved position.
- Restoring the chosen position after restart and automatic login after HTTP 401, before repeating school requests.
- Offline restart using only an account-specific previously confirmed snapshot.
- Failed PUT before the server changes and an ambiguous PUT after it changes; unavailable reconciliation hides all school data.
- Same lesson/grade/homework/upload IDs in different accounts and positions; replacement/deletion cannot affect another context.
- Non-destructive migrations from database versions 6–9 and quarantined legacy rows with unknown ownership.
- Pending homework survives a switch and replays only in its original position; drafts and grade notification baselines remain separate.
- Old in-flight work is cancelled; queued old work and a late response cannot publish into the new context.
- The chooser identifies schools separately, blocks repeated choices during switching, and offers recovery for an empty/error list.

## Real-account verification required

No real eSchool account with two student organizations is available in this session.
Fixtures are not confirmation that the production server switches organizations successfully.
Keep the PR in draft until the following are verified on an authorized real account:

1. Select each organization and verify school name, person ID, year, timetable, grades, homework, conversations, and recipients.
2. Restart and force session expiry; confirm the selected school is restored before any school data request.
3. Switch during a slow load and an offline homework change; verify old content stays hidden and queued changes return to their original school.
4. Check per-school drafts and notification baselines, including first-load suppression of historical grades.
