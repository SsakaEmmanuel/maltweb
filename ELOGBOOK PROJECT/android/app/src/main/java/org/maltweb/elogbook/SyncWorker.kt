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
            val dailyReviews = api.fetchMyDailyReviews(session)
            for (i in 0 until dailyReviews.length()) {
                val review = dailyReviews.getJSONObject(i)
                val reviewedAt = if (review.isNull("supervisor_signed_at")) null else review.optString("supervisor_signed_at")
                dao.updateReview(
                    session.userId,
                    review.optString("entry_date"),
                    review.optString("supervisor_comments", ""),
                    reviewedAt
                )
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
            val remoteDaily = api.fetchMyDailyEntries(session)
            val syncedDaily = ArrayList<LogbookEntry>()
            for (i in 0 until remoteDaily.length()) {
                val row = remoteDaily.getJSONObject(i)
                val reviewedAt = if (row.isNull("supervisor_signed_at")) null else row.optString("supervisor_signed_at")
                val updated = row.optString("updated_at")
                syncedDaily.add(LogbookEntry(
                    id = row.optString("id"), entryDate = row.optString("entry_date"),
                    workDone = row.optString("work_done"), toolsUsed = row.optString("tools_used"),
                    skillsLearned = row.optString("skills_learned"), challenges = row.optString("challenges"),
                    recommendations = row.optString("recommendations"), ownerUserId = session.userId,
                    syncStatus = "synced", supervisorComments = row.optString("supervisor_comments", ""),
                    supervisorReviewedAt = reviewedAt,
                    updatedAt = try { java.time.Instant.parse(updated).toEpochMilli() } catch (_: Exception) { System.currentTimeMillis() }
                ))
            }
            val pendingDaily = dao.pendingSync(session.userId)
            val pendingDates = pendingDaily.map { it.entryDate }.toSet()
            dao.saveSynced(syncedDaily.filter { it.entryDate !in pendingDates })

            val remoteWeekly = api.fetchMyWeeklySummaries(session)
            val syncedWeekly = ArrayList<WeeklySummary>()
            for (i in 0 until remoteWeekly.length()) {
                val row = remoteWeekly.getJSONObject(i)
                val reviewedAt = if (row.isNull("supervisor_signed_at")) null else row.optString("supervisor_signed_at")
                val updated = row.optString("updated_at")
                syncedWeekly.add(WeeklySummary(
                    id = row.optString("id"), weekStart = row.optString("week_start"),
                    summary = row.optString("summary"), skillsLearned = row.optString("skills_learned", ""),
                    challenges = row.optString("challenges", ""), ownerUserId = session.userId,
                    syncStatus = "synced", supervisorComments = row.optString("supervisor_comments", ""),
                    supervisorReviewedAt = reviewedAt,
                    updatedAt = try { java.time.Instant.parse(updated).toEpochMilli() } catch (_: Exception) { System.currentTimeMillis() }
                ))
            }
            val pendingWeekly = weeklyDao.pendingSync(session.userId)
            val pendingWeeks = pendingWeekly.map { it.weekStart }.toSet()
            weeklyDao.saveSynced(syncedWeekly.filter { it.weekStart !in pendingWeeks })
            Result.success()
        } catch (e: IOException) {
            if (runAttemptCount < 5) Result.retry() else Result.failure()
        } catch (_: Exception) {
            if (runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }
}
