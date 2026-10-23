# Independent E-Logbook — setup notes

This system is separate from the MALTWEB listings database. Existing paper-style forms are preserved while this first online portal is added alongside them.

## Online portal address

After GitHub Pages publishes the repository, the portal should be available at: `https://ssakaemmanuel.github.io/maltweb/ELOGBOOK%20PROJECT/online/`.

## 1. Database already created

A dedicated project named **E-Logbook** has been created in the MALTWEB Supabase organization in the Frankfurt region. The initial five-table schema and row-level security policies have been applied and checked. The website config uses the public publishable key; no service-role key is exposed.

## 2. Database setup reference
1. Create a **new Supabase project specifically for E-Logbook**. Do not use the MALTWEB project.
2. In that new project's SQL Editor, run the complete file `online/schema.sql`.
3. Copy the project's Project URL and publishable key (or legacy anon key) into `online/config.js`.
4. Never place a service-role/secret key in the website or Android app.
5. Configure email confirmation and password-reset settings before real student use.

The starter SQL limits student records to the authenticated student. Supervisor/admin role provisioning and supervisor review policies must be completed before those features are opened; never let a public sign-up form choose privileged roles.

## 2. Online website
The web portal is in `online/`. It is static HTML/JS and can be served by GitHub Pages. It requires the dedicated project configuration and the schema above before authentication and saving can work. GitHub Pages does not execute PHP.

## 3. Android app — next implementation stage
The native app should use Kotlin, Room for local offline entries, and WorkManager for retryable synchronization. A local entry should have a UUID and pending/synced state; upload must be idempotent and must never discard unsynced entries. First online sign-in is required before offline use can be enabled. Keep the local database and online tables aligned.

## 4. Current limitations
- This is a first working portal foundation, not a completed replacement for every old form.
- Offline entry creation is a native Android feature and is not implemented by this web portal.
- Supervisor assignments, approvals, evaluations, PDF export, and conflict-resolution screens are planned follow-up work.
- Test using a dedicated project and test accounts before adding real student records.
