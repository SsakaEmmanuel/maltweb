package org.maltweb.elogbook

import androidx.activity.ComponentActivity
import android.os.Bundle
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var db: LogbookDatabase
    private lateinit var entryList: LinearLayout
    private lateinit var status: TextView
    private lateinit var authMessage: TextView
    private lateinit var authPanel: LinearLayout
    private lateinit var accountPanel: LinearLayout
    private lateinit var accountLabel: TextView
    private lateinit var emailField: EditText
    private lateinit var passwordField: EditText
    private lateinit var dateField: EditText
    private lateinit var workField: EditText
    private lateinit var toolsField: EditText
    private lateinit var skillsField: EditText
    private lateinit var challengesField: EditText
    private lateinit var recommendationsField: EditText
    private lateinit var sessionStore: SessionStore
    private var currentSession: AuthSession? = null
    private var entriesJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = LogbookDatabase.get(this)
        sessionStore = SessionStore(this)
        currentSession = sessionStore.read()
        SyncScheduler.schedulePeriodic(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 22, 22, 22)
            setBackgroundColor(Color.rgb(243, 246, 250))
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)
        root.addView(scroll, ViewGroup.LayoutParams(-1, -1))
        setContentView(root)

        content.addView(TextView(this).apply {
            text = "E-LOGBOOK"
            textSize = 12f
            setTextColor(Color.rgb(180, 122, 8))
        })
        content.addView(TextView(this).apply {
            text = "Your attachment, organized."
            textSize = 27f
            setTextColor(Color.rgb(16, 34, 61))
            setPadding(0, 4, 0, 8)
        })
        status = TextView(this).apply {
            text = "Entries are stored on this phone when offline."
            textSize = 13f
            setTextColor(Color.rgb(35, 91, 70))
        }
        content.addView(status)

        authPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 10, 0, 8)
        }
        authPanel.addView(TextView(this).apply {
            text = "Sign in to sync your logbook"
            textSize = 18f
            setTextColor(Color.rgb(16, 34, 61))
            setPadding(0, 8, 0, 4)
        })
        emailField = addField(authPanel, "Email address", "")
        emailField.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        passwordField = addField(authPanel, "Password", "")
        passwordField.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        val authButtons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        authButtons.addView(Button(this).apply {
            text = "Sign in"
            setOnClickListener { signIn() }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        authButtons.addView(Button(this).apply {
            text = "Create account"
            setOnClickListener { signUp() }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        authPanel.addView(authButtons)
        authMessage = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.rgb(153, 73, 20))
            setPadding(0, 6, 0, 6)
        }
        authPanel.addView(authMessage)
        content.addView(authPanel)

        accountPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 12)
        }
        accountLabel = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.rgb(35, 91, 70))
        }
        accountPanel.addView(accountLabel)
        val accountButtons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        accountButtons.addView(Button(this).apply {
            text = "Sync now"
            setOnClickListener {
                SyncScheduler.syncNow(this@MainActivity)
                status.text = "Sync requested. Pending records remain on this phone until upload succeeds."
            }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        accountButtons.addView(Button(this).apply {
            text = "Sign out"
            setOnClickListener { signOut() }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        accountPanel.addView(accountButtons)
        content.addView(accountPanel)

        dateField = addField(content, "Date (YYYY-MM-DD)", SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
        workField = addField(content, "Work done today", "", true)
        toolsField = addField(content, "Tools / equipment used", "")
        skillsField = addField(content, "Skills or lessons learned", "", true)
        challengesField = addField(content, "Challenges encountered", "", true)
        recommendationsField = addField(content, "Recommendations", "", true)
        content.addView(Button(this).apply {
            text = "Save daily entry on this device"
            setOnClickListener { saveEntry() }
        })
        content.addView(TextView(this).apply {
            text = "Saved entries"
            textSize = 20f
            setTextColor(Color.rgb(16, 34, 61))
            setPadding(0, 24, 0, 8)
        })
        entryList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(entryList)

        updateAccountUi()
        observeEntries(currentSession?.userId ?: "local")
        if (currentSession != null) SyncScheduler.syncNow(this)
    }

    private fun signIn() {
        val email = emailField.text.toString().trim()
        val password = passwordField.text.toString()
        if (email.isBlank() || password.isBlank()) {
            authMessage.text = "Enter your email address and password."
            return
        }
        authMessage.text = "Signing in…"
        lifecycleScope.launch {
            try {
                val session = withContext(Dispatchers.IO) {
                    SupabaseApi(this@MainActivity).signIn(email, password).also { sessionStore.save(it) }
                }
                currentSession = session
                updateAccountUi()
                observeEntries(session.userId)
                withContext(Dispatchers.IO) {
                    db.entries().claimLocalEntries(session.userId)
                    SupabaseApi(this@MainActivity).ensureStudentProfile(session, email)
                }
                SyncScheduler.syncNow(this@MainActivity)
                authMessage.text = "Signed in. Pending entries will sync when internet is available."
                authMessage.setTextColor(Color.rgb(35, 91, 70))
            } catch (e: Exception) {
                authMessage.text = e.message ?: "Sign-in failed. Check the connection and account details."
                authMessage.setTextColor(Color.rgb(170, 55, 40))
            }
        }
    }

    private fun signUp() {
        val email = emailField.text.toString().trim()
        val password = passwordField.text.toString()
        if (email.isBlank() || password.length < 6) {
            authMessage.text = "Enter a valid email and password with at least 6 characters."
            return
        }
        authMessage.text = "Creating account…"
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    SupabaseApi(this@MainActivity).signUp(email, password)
                }
                authMessage.text = "Account request submitted. Check your email for a confirmation link if required, then sign in."
                authMessage.setTextColor(Color.rgb(35, 91, 70))
            } catch (e: Exception) {
                authMessage.text = e.message ?: "Could not create account. Please try again."
                authMessage.setTextColor(Color.rgb(170, 55, 40))
            }
        }
    }

    private fun signOut() {
        sessionStore.clear()
        currentSession = null
        updateAccountUi()
        observeEntries("local")
        authMessage.text = "Signed out. Your saved records remain on this device."
        authMessage.setTextColor(Color.rgb(35, 91, 70))
    }

    private fun updateAccountUi() {
        val session = currentSession
        authPanel.visibility = if (session == null) View.VISIBLE else View.GONE
        accountPanel.visibility = if (session == null) View.GONE else View.VISIBLE
        if (session != null) {
            accountLabel.text = "Signed in • " + session.userId.take(8) + "…"
        }
    }

    private fun observeEntries(ownerId: String) {
        entriesJob?.cancel()
        entriesJob = lifecycleScope.launch {
            db.entries().observeAllForOwner(ownerId).collect { entries ->
                entryList.removeAllViews()
                entries.forEach { entry ->
                    val syncLabel = if (entry.syncStatus == "synced") "SYNCED" else "PENDING SYNC"
                    val card = TextView(this@MainActivity).apply {
                        text = entry.entryDate + " • " + syncLabel + "\n" + entry.workDone
                        textSize = 14f
                        setTextColor(Color.rgb(35, 52, 76))
                        setPadding(14, 14, 14, 14)
                        setBackgroundColor(Color.WHITE)
                    }
                    val params = LinearLayout.LayoutParams(-1, -2)
                    params.setMargins(0, 0, 0, 10)
                    entryList.addView(card, params)
                }
                val pending = entries.count { it.syncStatus == "pending" }
                val synced = entries.count { it.syncStatus == "synced" }
                status.text = when {
                    pending > 0 -> "Offline-safe • " + pending + " record(s) awaiting upload"
                    synced > 0 -> "Sync complete • " + synced + " record(s) uploaded"
                    else -> "Entries are stored on this phone when offline."
                }
            }
        }
    }

    private fun addField(parent: LinearLayout, label: String, initial: String, multiline: Boolean = false): EditText {
        parent.addView(TextView(this).apply {
            text = label
            textSize = 13f
            setTextColor(Color.rgb(41, 58, 84))
            setPadding(0, 12, 0, 4)
        })
        return EditText(this).also { field ->
            field.setText(initial)
            field.textSize = 15f
            field.setSingleLine(!multiline)
            if (multiline) field.minLines = 2
            field.setPadding(12, 10, 12, 10)
            parent.addView(field, ViewGroup.LayoutParams(-1, -2))
        }
    }

    private fun saveEntry() {
        val date = dateField.text.toString().trim()
        val work = workField.text.toString().trim()
        if (!date.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")) || work.isBlank()) {
            Toast.makeText(this, "Enter a valid date and the work done.", Toast.LENGTH_LONG).show()
            return
        }
        val ownerId = currentSession?.userId ?: "local"
        lifecycleScope.launch {
            db.entries().save(
                LogbookEntry(
                    entryDate = date,
                    workDone = work,
                    toolsUsed = toolsField.text.toString().trim(),
                    skillsLearned = skillsField.text.toString().trim(),
                    challenges = challengesField.text.toString().trim(),
                    recommendations = recommendationsField.text.toString().trim(),
                    ownerUserId = ownerId
                )
            )
            workField.text.clear()
            toolsField.text.clear()
            skillsField.text.clear()
            challengesField.text.clear()
            recommendationsField.text.clear()
            if (currentSession != null) {
                SyncScheduler.syncNow(this@MainActivity)
                Toast.makeText(this@MainActivity, "Saved locally. Upload queued.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this@MainActivity, "Saved offline. Sign in later to sync this record.", Toast.LENGTH_LONG).show()
            }
        }
    }
}
