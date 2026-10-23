package org.maltweb.elogbook

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.io.IOException

internal class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val store = SessionStore(applicationContext)
        var session = store.read() ?: return Result.success()
        val database = LogbookDatabase.get(applicationContext)
        val dao = database.entries()
        val weeklyDao = database.weeklies()
        return try {
            val api = SupabaseApi(applicationContext)
            session = api.ensureFreshSession(session)
            api.ensureStudentProfile(session)
            val pending = dao.pendingSync(session.userId)
            for (entry in pending) {
                api.uploadDailyEntry(session, entry)
                dao.markSynced(entry.id)
            }
            val pendingWeekly = weeklyDao.pendingSync(session.userId)
            for (summary in pendingWeekly) {
                api.uploadWeeklySummary(session, summary.weekStart, summary.summary, summary.skillsLearned, summary.challenges)
                weeklyDao.markSynced(summary.id)
            }
            val reviews = api.fetchMyWeeklyReviews(session)
            for (i in 0 until reviews.length()) {
                val review = reviews.getJSONObject(i)
                val reviewedAt = if (review.isNull("supervisor_signed_at")) null else review.optString("supervisor_signed_at")
                weeklyDao.updateReview(
                    session.userId,
                    review.optString("week_start"),
                    review.optString("supervisor_comments", ""),
                    reviewedAt
                )
            }
            Result.success()
        } catch (e: IOException) {
            if (runAttemptCount < 5) Result.retry() else Result.failure()
        } catch (_: Exception) {
            if (runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }
}
