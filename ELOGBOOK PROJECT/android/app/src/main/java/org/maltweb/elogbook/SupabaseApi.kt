package org.maltweb.elogbook

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

internal class SupabaseApi(private val context: Context) {
    private val sessions = SessionStore(context)

    fun signUp(email: String, password: String, metadata: JSONObject? = null): JSONObject {
        val body = JSONObject().put("email", email).put("password", password)
        if (metadata != null) body.put("data", metadata)
        return request("POST", "/auth/v1/signup", body) as JSONObject
    }

    fun signIn(email: String, password: String): AuthSession {
        val json = request(
            "POST", "/auth/v1/token?grant_type=password",
            JSONObject().put("email", email).put("password", password)
        ) as JSONObject
        val user = json.optJSONObject("user") ?: throw IOException("Sign-in response did not include a user.")
        val access = json.optString("access_token")
        val refresh = json.optString("refresh_token")
        if (access.isBlank() || refresh.isBlank()) throw IOException("Sign-in did not return a session. Confirm the email first if confirmation is enabled.")
        val expiresAt = json.optLong("expires_at", System.currentTimeMillis() / 1000 + json.optLong("expires_in", 3600))
        return AuthSession(user.getString("id"), access, refresh, TimeUnit.SECONDS.toMillis(expiresAt), email)
    }

    fun refresh(old: AuthSession): AuthSession {
        val json = request(
            "POST", "/auth/v1/token?grant_type=refresh_token",
            JSONObject().put("refresh_token", old.refreshToken)
        ) as JSONObject
        val userId = json.optJSONObject("user")?.optString("id").takeUnless { it.isNullOrBlank() } ?: old.userId
        val access = json.optString("access_token")
        val refresh = json.optString("refresh_token", old.refreshToken)
        if (access.isBlank()) throw IOException("Session refresh returned no access token.")
        val expiresAt = json.optLong("expires_at", System.currentTimeMillis() / 1000 + json.optLong("expires_in", 3600))
        return AuthSession(userId, access, refresh, TimeUnit.SECONDS.toMillis(expiresAt), old.email)
    }

    fun ensureFreshSession(session: AuthSession): AuthSession {
        if (session.expiresAt > System.currentTimeMillis() + 120_000) return session
        val renewed = refresh(session)
        sessions.save(renewed)
        return renewed
    }

    fun ensureStudentProfile(session: AuthSession, email: String = session.email) {
        // Profile creation is deliberately handled by the shared registration workflow.
        // Never silently convert a supervisor or organisation account into a student.
        fetchProfile(session)
    }

    fun fetchProfile(session: AuthSession): JSONObject? {
        val result = request(
            "GET",
            "/rest/v1/profiles?select=id,full_name,role,organization_id,school_name,student_number,gender,course,approval_status&id=eq.${session.userId}",
            token = session.accessToken
        ) as? JSONArray ?: throw IOException("Unexpected profile response.")
        return result.optJSONObject(0)
    }

    fun fetchOrganizations(): JSONArray {
        return request(
            "GET", "/rest/v1/organizations?select=id,name&order=name.asc"
        ) as? JSONArray ?: throw IOException("Unexpected organisation list response.")
    }

    fun createOrganization(session: AuthSession, fullName: String, organizationName: String, gender: String): JSONObject? {
        val result = request(
            "POST", "/rest/v1/rpc/create_logbook_organization",
            JSONObject().put("p_full_name", fullName).put("p_organization_name", organizationName).put("p_gender", gender),
            token = session.accessToken
        )
        return if (result is JSONArray) result.optJSONObject(0) else result as? JSONObject
    }

    fun registerMember(
        session: AuthSession, fullName: String, role: String, gender: String, course: String,
        studentNumber: String, organizationId: String, organizationKey: String
    ) {
        request(
            "POST", "/rest/v1/rpc/register_logbook_member",
            JSONObject().put("p_full_name", fullName).put("p_role", role).put("p_gender", gender)
                .put("p_course", course.ifBlank { JSONObject.NULL })
                .put("p_student_number", studentNumber.ifBlank { JSONObject.NULL })
                .put("p_organization_id", organizationId).put("p_organization_key", organizationKey),
            token = session.accessToken
        )
    }

    fun fetchOrganizationMembers(session: AuthSession, organizationId: String): JSONArray {
        return request(
            "GET",
            "/rest/v1/profiles?select=id,full_name,role,gender,course,student_number,approval_status,created_at&organization_id=eq.$organizationId&order=full_name.asc",
            token = session.accessToken
        ) as? JSONArray ?: throw IOException("Unexpected organisation members response.")
    }

    fun reviewOrganizationSupervisor(session: AuthSession, profileId: String, approve: Boolean) {
        request("POST", "/rest/v1/rpc/organization_review_supervisor",
            JSONObject().put("p_supervisor_id", profileId).put("p_approve", approve), token = session.accessToken)
    }

    fun assignOrganizationSupervisor(session: AuthSession, studentId: String, supervisorId: String, assignmentType: String) {
        request("POST", "/rest/v1/rpc/organization_assign_supervisor",
            JSONObject().put("p_student_id", studentId).put("p_supervisor_id", supervisorId).put("p_assignment_type", assignmentType),
            token = session.accessToken)
    }

    fun saveAttendance(session: AuthSession, studentId: String, date: String, status: String, remarks: String) {
        request("POST", "/rest/v1/attendance_records?on_conflict=student_id%2Cattendance_date",
            JSONArray().put(JSONObject().put("student_id",studentId).put("attendance_date",date).put("status",status)
                .put("remarks",remarks).put("recorded_by",session.userId)),
            token=session.accessToken, prefer="resolution=merge-duplicates,return=minimal")
    }

    fun awardMark(session: AuthSession, studentId: String, score: Double, category: String, feedback: String) {
        request("POST", "/rest/v1/performance_marks",
            JSONArray().put(JSONObject().put("student_id",studentId).put("awarded_by",session.userId)
                .put("score",score).put("category",category).put("feedback",feedback)),
            token=session.accessToken, prefer="return=minimal")
    }

    fun fetchOrganizationMarks(session: AuthSession, studentIds: List<String>): JSONArray {
        if (studentIds.isEmpty()) return JSONArray()
        val filter = studentIds.joinToString(",")
        return request(
            "GET", "/rest/v1/performance_marks?select=id,student_id,score,category,feedback,created_at&student_id=in.($filter)&order=created_at.desc",
            token=session.accessToken
        ) as? JSONArray ?: throw IOException("Unexpected performance marks response.")
    }

    fun rotateOrganizationKey(session: AuthSession): JSONObject? {
        val result = request("POST", "/rest/v1/rpc/rotate_logbook_organization_key", JSONObject(), token=session.accessToken)
        return if (result is JSONArray) result.optJSONObject(0) else result as? JSONObject
    }


    fun uploadDailyEntry(session: AuthSession, entry: LogbookEntry) {
        val timestamp = java.time.Instant.ofEpochMilli(entry.updatedAt).toString()
        val payload = JSONArray().put(
            JSONObject()
                .put("id", entry.id)
                .put("student_id", session.userId)
                .put("entry_date", entry.entryDate)
                .put("work_done", entry.workDone)
                .put("tools_used", entry.toolsUsed)
                .put("skills_learned", entry.skillsLearned)
                .put("challenges", entry.challenges)
                .put("recommendations", entry.recommendations)
                .put("client_updated_at", timestamp)
                .put("updated_at", timestamp)
        )
        request(
            "POST", "/rest/v1/daily_entries?on_conflict=student_id%2Centry_date",
            payload, token = session.accessToken,
            prefer = "resolution=merge-duplicates,return=minimal"
        )
    }

 
    fun uploadWeeklySummary(session: AuthSession, weekStart: String, summary: String, skills: String, challenges: String) {
        val timestamp = java.time.Instant.now().toString()
        val payload = JSONArray().put(
            JSONObject()
                .put("student_id", session.userId)
                .put("week_start", weekStart)
                .put("summary", summary)
                .put("skills_learned", skills)
                .put("challenges", challenges)
                .put("client_updated_at", timestamp)
                .put("updated_at", timestamp)
        )
        request(
            "POST", "/rest/v1/weekly_summaries?on_conflict=student_id%2Cweek_start",
            payload, token = session.accessToken,
            prefer = "resolution=merge-duplicates,return=minimal"
        )
    }


    fun fetchProfileRole(session: AuthSession): String {
        val profiles = request(
            "GET", "/rest/v1/profiles?select=role&id=eq.${session.userId}",
            token = session.accessToken
        ) as? JSONArray ?: throw IOException("Unexpected profile-role response.")
        return profiles.optJSONObject(0)?.optString("role")?.ifBlank { "unregistered" } ?: "unregistered"
    }

    fun fetchAssignedWeeklySummaries(session: AuthSession): JSONArray {
        return request(
            "GET",
            "/rest/v1/weekly_summaries?select=id,student_id,week_start,summary,skills_learned,challenges,supervisor_comments,supervisor_signed_at,student:profiles!weekly_summaries_student_id_fkey(full_name)&student_id=neq.${session.userId}&order=week_start.desc&limit=100",
            token = session.accessToken
        ) as? JSONArray ?: throw IOException("Unexpected weekly-review response.")
    }

    fun saveWeeklyReview(session: AuthSession, summaryId: String, comments: String) {
        val now = java.time.Instant.now().toString()
        val payload = JSONObject()
            .put("supervisor_comments", comments)
            .put("supervisor_signed_at", now)
            .put("updated_at", now)
        request(
            "PATCH", "/rest/v1/weekly_summaries?id=eq.$summaryId",
            payload, token = session.accessToken, prefer = "return=minimal"
        )
    }

    fun fetchAssignedDailyEntries(session: AuthSession): JSONArray {
        return request(
            "GET",
            "/rest/v1/daily_entries?select=id,student_id,entry_date,work_done,tools_used,skills_learned,challenges,recommendations,supervisor_comments,supervisor_signed_at,student:profiles!daily_entries_student_id_fkey(full_name)&student_id=neq.${session.userId}&order=entry_date.desc&limit=300",
            token = session.accessToken
        ) as? JSONArray ?: throw IOException("Unexpected daily-review response.")
    }

    fun saveDailyReview(session: AuthSession, entryId: String, comments: String) {
        val now = java.time.Instant.now().toString()
        request(
            "PATCH", "/rest/v1/daily_entries?id=eq.$entryId",
            JSONObject()
                .put("supervisor_comments", comments)
                .put("supervisor_signed_at", now)
                .put("updated_at", now),
            token = session.accessToken, prefer = "return=minimal"
        )
    }

    fun fetchMyWeeklyReviews(session: AuthSession): JSONArray {
        return request(
            "GET",
            "/rest/v1/weekly_summaries?select=week_start,supervisor_comments,supervisor_signed_at&student_id=eq.${session.userId}&limit=100",
            token = session.accessToken
        ) as? JSONArray ?: throw IOException("Unexpected weekly-review status response.")
    }

    fun fetchAssignedStudentProfiles(session: AuthSession, assignmentType: String): JSONArray {
        val assignments = request(
            "GET", "/rest/v1/supervisor_assignments?select=student_id,assignment_type&supervisor_id=eq.${session.userId}",
            token = session.accessToken
        ) as? JSONArray ?: throw IOException("Unexpected supervisor assignment response.")
        val ids = ArrayList<String>()
        for (i in 0 until assignments.length()) {
            val row = assignments.optJSONObject(i) ?: continue
            if (row.optString("assignment_type") == assignmentType) ids.add(row.optString("student_id"))
        }
        if (ids.isEmpty()) return JSONArray()
        return request(
            "GET", "/rest/v1/profiles?select=id,full_name,course,student_number&id=in.(" + ids.joinToString(",") + ")",
            token = session.accessToken
        ) as? JSONArray ?: throw IOException("Unexpected assigned students response.")
    }

    fun fetchReportsForStudents(session: AuthSession, studentIds: List<String>, weekly: Boolean): JSONArray {
        if (studentIds.isEmpty()) return JSONArray()
        val table = if (weekly) "weekly_summaries" else "daily_entries"
        val select = if (weekly) "id,student_id,week_start,summary,skills_learned,challenges,supervisor_comments,supervisor_signed_at" else "id,student_id,entry_date,work_done,tools_used,skills_learned,challenges,recommendations,supervisor_comments,supervisor_signed_at"
        val dateField = if (weekly) "week_start" else "entry_date"
        return request(
            "GET", "/rest/v1/$table?select=$select&student_id=in.(" + studentIds.joinToString(",") + ")&order=$dateField.desc&limit=500",
            token = session.accessToken
        ) as? JSONArray ?: throw IOException("Unexpected report list response.")
    }

    fun fetchAttendanceForStudents(session: AuthSession, studentIds: List<String>): JSONArray {
        if (studentIds.isEmpty()) return JSONArray()
        return request(
            "GET", "/rest/v1/attendance_records?select=student_id,attendance_date,status,remarks&student_id=in.(" + studentIds.joinToString(",") + ")&order=attendance_date.desc&limit=500",
            token = session.accessToken
        ) as? JSONArray ?: throw IOException("Unexpected attendance response.")
    }

    private fun request(
        method: String,
        path: String,
        body: Any? = null,
        token: String? = null,
        prefer: String? = null
    ): Any {
        val connection = (URL(SupabaseConfig.BASE_URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("apikey", SupabaseConfig.PUBLISHABLE_KEY)
            setRequestProperty("Accept", "application/json")
            if (!token.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
            }
            if (!prefer.isNullOrBlank()) setRequestProperty("Prefer", prefer)
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val message = try {
                    val json = JSONObject(response)
                    json.optString("msg").ifBlank { json.optString("message").ifBlank { json.optString("error_description").ifBlank { json.optString("error") } } }
                } catch (_: Exception) { response }
                throw IOException("Server returned HTTP $status" + if (message.isBlank()) "." else ": $message")
            }
            if (response.isBlank()) return JSONObject()
            return if (response.trimStart().startsWith("[")) JSONArray(response) else JSONObject(response)
        } finally {
            connection.disconnect()
        }
    }
}
