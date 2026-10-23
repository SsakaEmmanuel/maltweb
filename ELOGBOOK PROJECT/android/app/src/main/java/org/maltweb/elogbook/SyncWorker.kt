package org.maltweb.elogbook

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.io.IOException

internal class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val store = SessionStore(applicationContext)
        var session = store.read() ?: return Result.success() // Keep local pending items until sign-in.
        val dao = LogbookDatabase.get(applicationContext).entries()
        return try {
            val api = SupabaseApi(applicationContext)
            session = api.ensureFreshSession(session)
            val pending = dao.pendingSync(session.userId)
            if (pending.isEmpty()) return Result.success()
            for (entry in pending) {
                api.uploadDailyEntry(session, entry)
                dao.markSynced(entry.id)
            }
            Result.success()
        } catch (e: IOException) {
            if (runAttemptCount < 5) Result.retry() else Result.failure()
        } catch (_: Exception) {
            if (runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }
}
