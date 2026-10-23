# Independent E-Logbook

This folder contains the E-Logbook web portal and native Android app source. The old paper-style forms are retained in the existing folders.

## Online portal
After the repository's GitHub Pages deployment finishes, open:
`https://ssakaemmanuel.github.io/maltweb/ELOGBOOK%20PROJECT/online/index.html`

The portal is static HTML/CSS/JavaScript. GitHub Pages does not execute PHP.

## Dedicated online database
A separate project named **E-Logbook** is already created in the MALTWEB Supabase organization (Frankfurt region).
- Project ref: `qjzftbgahxkolljpmkmn`
- Project URL: `https://qjzftbgahxkolljpmkmn.supabase.co`
- Schema and initial row-level security policies have been applied and verified.
- Authenticated Data API grants and supporting foreign-key indexes have been applied.

The website and Android app use only the public publishable key. Do not add a service-role/secret key to GitHub.

## Authentication settings to review
In the Supabase dashboard, open **Authentication → URL Configuration** and set the Site URL to the GitHub Pages portal URL above. Add any additional confirmation/reset redirect URLs you intend to use. Under **Authentication → Sign In / Providers → Email**, review whether email confirmation is required. The apps handle the confirmation-required flow by asking the student to check their inbox and sign in after confirming.

## Native Android
Open `android/` in Android Studio with JDK 17. The app includes:
- Student email sign-up and sign-in.
- AES/GCM encrypted session storage backed by Android Keystore.
- Room local storage so daily entries can be created offline and survive restarts.
- WorkManager one-time and periodic network-constrained sync for daily entries.
- Pending entries remain local until the server accepts their upload.

GitHub Actions builds a debug APK and uploads it as an artifact. Open **Actions → E-Logbook Android build → latest successful run → Artifacts** to download it.

## How to test end-to-end
1. Confirm the URL and Email settings above.
2. Open the portal and create a test student account with an email inbox you can access.
3. Confirm the email if prompted, sign in, and save a daily work entry.
4. On the Android app, sign in with the same student account and tap **Sync now**; the daily entry should upload, while entries added offline should stay marked pending until connectivity returns.
5. Save a fresh daily entry on Android, wait for it to show **SYNCED**, then refresh the online portal and verify it appears.
6. Test with a disposable account before entering real student data.

## Scope still to complete
The initial native sync worker currently handles daily entries. Weekly summaries, placements, supervisor approvals/reviews, signatures, PDF exports, and conflict-resolution UI remain follow-up work. Public sign-up is fixed to the student role; supervisor/admin roles require trusted provisioning before their policies and screens are enabled. This is a debug build, not a signed production release.
