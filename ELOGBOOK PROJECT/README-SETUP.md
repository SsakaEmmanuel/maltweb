# E-Logbook setup and operation

E-Logbook is maintained under this repository but has its own web portal and dedicated Supabase project. Daily and weekly records from the original version are preserved.

## Live portal and source
- Web portal: https://ssakaemmanuel.github.io/maltweb/ELOGBOOK%20PROJECT/online/index.html
- Native Android source: `android/`
- Organisation workflow SQL: `online/organization-management.sql`
- Base database SQL: `online/schema.sql`
- Assigned-supervisor review SQL: `online/supervisor-review.sql`

## Current role-based flow
1. **Organisation administrator:** registers the school / institution and receives an organisation key. Store the key securely; it is only shown when created or rotated.
2. **Supervisor:** registers with names, gender, course/subject, organisation and organisation key. The account stays pending until that organisation approves it.
3. **Student:** registers with names, gender, course, student/index number, organisation and its key.
4. **Organisation administrator:** approves/rejects supervisors, assigns approved supervisors to students, reviews reporting progress, records attendance and awards marks.
5. **Supervisor:** sees explicitly assigned students, reviews their daily and weekly reporting, records attendance, adds review feedback and awards marks.
6. **Student:** saves daily and weekly records, sees supervisor feedback, attendance records, marks, and ranking within the same organisation and course.

The website and Android app use the same Supabase Auth accounts and the same online data. The Android app additionally keeps daily records and weekly summaries in a Room database, then queues them for network sync.

## Dedicated online database
The project already connected to this E-Logbook is:
- Project ref: `qjzftbgahxkolljpmkmn`
- URL: `https://qjzftbgahxkolljpmkmn.supabase.co`

Only the public publishable key belongs in web or Android client code. Never copy a service-role/secret key to GitHub.

### Fresh project installation
In a **new, dedicated E-Logbook Supabase project**, run these files in this order in the SQL Editor:
1. `online/schema.sql`
2. `online/supervisor-review.sql`
3. `online/organization-management.sql`

Do not run these E-Logbook schema files against the main MALTWEB database. The organisation migration adds tables/columns and protected RPC functions; it does not delete daily or weekly reports.

### Authentication configuration
In Supabase, open Authentication → URL Configuration and set the Site URL to the live portal URL above. Add the portal URL to any confirmation/reset redirect URL list used by your project. Under Authentication → Sign In / Providers → Email, decide whether email confirmation is required. When confirmation is enabled, a newly created user must confirm email and then sign in to complete the organisation-linked profile.

## Important security behaviour
- Organisation keys are stored as SHA-256 hashes; only the newly generated or rotated key is returned to the organisation administrator.
- A key is a membership check, not a substitute for account authentication.
- Public sign-up cannot create an approved supervisor or organisation administrator by changing a client-side role value.
- Supervisor registration is pending until an organisation administrator approves it.
- Only an organisation administrator can approve supervisors and assign supervisors to students in the same organisation.
- Row-level security limits report access to the record owner, assigned approved supervisors, or the student's own organisation administrator.
- Database triggers prevent students from writing supervisor review/signature fields and prevent users from directly editing their role, organisation or approval status.
- Performance marks are append-only from the client; scores must be between 0 and 100 and identify the awarding account.
- Do not store a service-role/secret key in the website, repository or APK.

## Build and test
GitHub Actions builds a debug APK whenever Android source files change:
https://github.com/SsakaEmmanuel/maltweb/actions/workflows/elogbook-android-build.yml

Test with disposable accounts before using real placement data:
1. Register an organisation and copy its key.
2. Register a supervisor and a student using the organisation selector and key.
3. Sign into the organisation account and approve the supervisor.
4. Assign the approved supervisor to the student.
5. Save student daily and weekly reports; review them as the supervisor.
6. Record attendance and marks, then inspect student and organisation tables.
7. Test Android offline daily/weekly saving, then reconnect and use Sync now.

## Current implementation notes
- Web reports use responsive row/column tables.
- Android daily and weekly entries are saved locally; WorkManager uploads pending entries when online.
- Organisation reports, student/supervisor registration, supervisor approval, student assignments, attendance and mark-awarding are implemented in the current source.
- The native app build must pass and a real device/account round-trip must be tested before treating the complete workflow as production-ready.
- Daily and weekly PDF print/save controls are implemented for student, supervisor and organisation workspaces on the website and Android app. Verify the print/save flow on target browsers and devices before relying on it for official reports.
- CSV export, conflict resolution, attendance correction and mark-adjustment audit history, and a signed production APK remain future hardening work.
