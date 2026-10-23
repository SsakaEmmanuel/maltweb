# E-Logbook Android (native Kotlin)

This is the native/offline companion to the independent online portal.

- Native Android screen for daily attachment records.
- Room stores entries on the device and keeps them after app restarts.
- Entries are marked pending until secure online sync is configured.
- No data is sent anywhere yet; the online connection is not faked.

Open this folder in Android Studio with JDK 17 and allow Gradle to sync. Build > Build APK(s).

Next: connect to the dedicated E-Logbook Supabase project using a publishable key and authenticated session, then implement a WorkManager sync worker with retries and idempotent upserts. Never embed a service-role/secret key. Offline entry creation works independently of network access, but shared web/app sync is not active yet.
