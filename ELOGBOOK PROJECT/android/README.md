# E-Logbook Android (native Kotlin)

The native Android app shares Supabase Auth and online records with the E-Logbook website. Daily records and weekly summaries are stored in Room locally first, then synchronized when the device has a network connection.

## Accounts and roles
- **Organisation administrator:** creates an organisation, receives a one-time organisation key, approves/rejects supervisor registrations, assigns approved supervisors to students, reviews organisation reports and attendance, and awards marks.
- **Field / institution supervisor:** registers with course or subject, gender, organisation and organisation key. The account remains pending until approved by its organisation. Approved supervisors see only students assigned to them; they can review daily and weekly reports, add feedback, record attendance and award performance marks.
- **Student:** registers with names, course, gender, student/index number, organisation and its key. Students create/edit daily and weekly reports, view supervisor feedback, attendance, awarded marks and course ranking.
- Roles and membership are enforced by database policies/RPC checks; the Android UI alone is not treated as the security boundary.

## Offline and sync
- Room database stores daily and weekly records locally, including sync status and supervisor comments/review timestamps.
- WorkManager schedules sync only when network connectivity is available.
- Pending daily and weekly reports remain on the device if upload fails.
- After the device reconnects, use **Sync records now** or let WorkManager retry.
- Session credentials are encrypted with AES/GCM using a key held in Android Keystore.

## Build
Open this `android` directory in Android Studio with JDK 17 and let Gradle sync. Select **Build → Build APK(s)**. GitHub Actions builds a debug APK and uploads it as an artifact:
https://github.com/SsakaEmmanuel/maltweb/actions/workflows/elogbook-android-build.yml

The latest organisation-management build is generated from the native Android source on branch `main`. It is a debug APK, not a signed production release.

## Backend setup
The connected dedicated E-Logbook project is:
- Project ref: `qjzftbgahxkolljpmkmn`
- URL: `https://qjzftbgahxkolljpmkmn.supabase.co`

For a new dedicated project, run SQL files from `../online` in this order:
1. `schema.sql`
2. `supervisor-review.sql`
3. `organization-management.sql`

Do not run these E-Logbook schema files in the main MALTWEB database. Never put a service-role/secret key into the repository, website or APK.

## Test checklist
Use disposable test accounts first:
1. Register an organisation and copy its key.
2. Register a supervisor with the organisation key and wait for approval.
3. Register a student with the same organisation key, course and student/index number.
4. Approve the supervisor and assign that supervisor to the student from the organisation dashboard.
5. Save a daily and weekly report as the student, then review both as the supervisor without changing student-submitted content.
6. Record attendance and award marks as the supervisor or organisation.
7. Verify feedback, attendance and course rankings from the student account.
8. Save daily/weekly reports offline and verify that they sync when connectivity returns.

## Known remaining work
- The latest APK compiles successfully, but the complete multi-role flow still needs a real-device end-to-end test using the roles above.
- The Android APK must be tested end-to-end on a real device with disposable organisation, supervisor and student accounts before using it for official records. PDF/CSV exports, audit history for attendance corrections/mark adjustments, conflict-resolution UI, password-recovery polish, and a signed production release remain future hardening work.
