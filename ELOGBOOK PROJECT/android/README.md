# E-Logbook Android (native Kotlin)

This native Android companion stores daily logbook records locally first, then uploads them to the dedicated E-Logbook online project when a signed-in student has a connection.

## Included in this starter
- Native email sign-up and sign-in through Supabase Auth.
- Room local database; daily records are retained across app restarts and can be created without internet.
- Account-scoped local records; locally created entries are assigned to the first account that signs in on that device.
- Session tokens encrypted using AES/GCM with a key held by Android Keystore.
- WorkManager one-off and periodic sync requests that require network connectivity.
- Retryable uploads to the online daily-entry table; local records stay pending until a successful server response.
- Never embeds a service-role/secret key; the app uses a public publishable key.

## Open and build
Open this `android` folder in Android Studio with JDK 17 and let Gradle sync. Use **Build > Build APK(s)**. A GitHub Actions workflow also builds a debug APK and stores it as an artifact when the Android source changes.

## Setup and testing
1. Make sure the dedicated E-Logbook project is active and its schema/grants are applied.
2. Test student registration with a valid email address. If email confirmation is enabled, open the verification link and then sign in.
3. Save a daily entry while online; confirm its state changes from **PENDING SYNC** to **SYNCED** once WorkManager completes.
4. Turn off internet and save another entry. It should remain **PENDING SYNC**. Restore internet and use **Sync now**; confirm the entry appears in the online web portal.
5. Test on a disposable account before recording real student information.

## Known scope limits
- The Android uploader currently synchronizes daily entries only. Weekly summaries, placements, supervisor reviews, signatures, conflict UI, and PDF export still need implementation.
- Supervisor/admin roles must be provisioned by a trusted process; a public sign-up is always a student.
- This is a debug-build workflow, not a signed release APK workflow.
