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

    fun signUp(email: String, password: String): JSONObject =
        request("POST", "/auth/v1/signup", JSONObject().put("email", email).put("password", password)) as JSONObject

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
        val current = request(
            "GET", "/rest/v1/profiles?select=id&id=eq.${session.userId}",
            token = session.accessToken
        ) as? JSONArray ?: throw IOException("Unexpected profile response.")
        if (current.length() > 0) return
        val name = email.substringBefore("@").ifBlank { "Student" }
        request(
            "POST", "/rest/v1/profiles",
            JSONArray().put(JSONObject()
                .put("id", session.userId)
                .put("full_name", name)
                .put("role", "student")),
            token = session.accessToken,
            prefer = "return=minimal"
        )
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
