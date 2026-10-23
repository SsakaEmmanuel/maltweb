package org.maltweb.elogbook

import androidx.activity.ComponentActivity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var db: LogbookDatabase
    private lateinit var screenHost: LinearLayout
    private lateinit var status: TextView
    private lateinit var authMessage: TextView
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
    private var activeScreen = "home"

    private val navy = Color.rgb(18, 35, 61)
    private val blue = Color.rgb(39, 91, 166)
    private val gold = Color.rgb(229, 169, 52)
    private val background = Color.rgb(244, 247, 252)
    private val ink = Color.rgb(36, 51, 72)
    private val green = Color.rgb(32, 118, 82)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = LogbookDatabase.get(this)
        sessionStore = SessionStore(this)
        currentSession = sessionStore.read()
        SyncScheduler.schedulePeriodic(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(background)
        }
        screenHost = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
        }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        scroll.addView(screenHost)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        if (currentSession == null) showAuth() else showHome()
        observeEntries(currentSession?.userId ?: "local")
        if (currentSession != null) SyncScheduler.syncNow(this)
    }

    private fun showAuth() {
        activeScreen = "auth"
        screenHost.removeAllViews()
        screenHost.setPadding(dp(22), dp(34), dp(22), dp(22))
        screenHost.addView(TextView(this).apply {
            text = "E"
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            setTextColor(navy)
            setBackgroundColor(gold)
        }, LinearLayout.LayoutParams(dp(62), dp(62)))
        screenHost.addView(label("E-LOGBOOK", 13f, gold).apply { setPadding(0, dp(24), 0, dp(4)) })
        screenHost.addView(label("Your attachment.\nYour progress.", 30f, navy, true).apply { setPadding(0, 0, 0, dp(10)) })
        screenHost.addView(label("Keep daily records organized, even when you are offline.", 15f, ink).apply { setPadding(0, 0, 0, dp(26)) })

        val card = panel()
        card.addView(label("Welcome back", 22f, navy, true))
        card.addView(label("Sign in to access your logbook", 14f, ink).apply { setPadding(0, dp(5), 0, dp(12)) })
        emailField = addField(card, "Email address", "", false)
        emailField.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        passwordField = addField(card, "Password", "", false)
        passwordField.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        val signInButton = actionButton("Sign in", blue)
        signInButton.setOnClickListener { signIn() }
        card.addView(signInButton, matchWrap())
        val createButton = actionButton("Create student account", navy)
        createButton.setOnClickListener { signUp() }
        card.addView(createButton, matchWrap())
        authMessage = label("", 13f, green).apply { setPadding(0, dp(10), 0, 0) }
        card.addView(authMessage)
        screenHost.addView(card, matchWrap())
        screenHost.addView(label("Your saved offline entries stay on this device.", 12f, ink).apply {
            gravity = android.view.Gravity.CENTER
            setPadding(0, dp(24), 0, 0)
        })
    }

    private fun showHome() {
        activeScreen = "home"
        screenHost.setPadding(dp(18), dp(18), dp(18), dp(22))
        screenHost.removeAllViews()
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(label("E-LOGBOOK", 12f, blue, true))
        titles.addView(label("Student workspace", 24f, navy, true))
        top.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(actionButton("Sign out", navy).apply { setOnClickListener { signOut() } })
        screenHost.addView(top)
        screenHost.addView(label("Welcome back. Keep your attachment records up to date.", 14f, ink).apply {
            setPadding(0, dp(8), 0, dp(18))
        })

        val session = currentSession
        val accountCard = panel()
        accountCard.addView(label("ACCOUNT STATUS", 11f, blue, true))
        accountCard.addView(label(if (session != null) "Signed in and ready to sync" else "Offline mode", 18f, navy, true).apply { setPadding(0, dp(5), 0, dp(3)) })
        status = label("Your records are saved on this device when offline.", 13f, green)
        accountCard.addView(status)
        val sync = actionButton("Sync records now", blue)
        sync.setOnClickListener {
            if (currentSession != null) {
                SyncScheduler.syncNow(this)
                status.text = "Sync requested. Pending records remain on this device until upload succeeds."
            } else {
                Toast.makeText(this, "Sign in to sync your records.", Toast.LENGTH_LONG).show()
            }
        }
        accountCard.addView(sync, matchWrap())
        screenHost.addView(accountCard, matchWrap())

        screenHost.addView(label("YOUR LOGBOOK", 12f, blue, true).apply { setPadding(0, dp(22), 0, dp(10)) })
        addNavigationCard("＋", "New daily record", "Capture today's work, tools and skills", "entry")
        addNavigationCard("▤", "Saved records", "View your daily entries in a table", "records")
        addNavigationCard("▦", "Weekly reports", "Weekly summaries and supervisor review", "weekly")
    }

    private fun addNavigationCard(symbol: String, title: String, subtitle: String, destination: String) {
        val card = panel()
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        row.addView(TextView(this).apply {
            text = symbol
            textSize = 25f
            gravity = android.view.Gravity.CENTER
            setTextColor(blue)
            setBackgroundColor(Color.rgb(231, 239, 251))
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        val textBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
        textBox.addView(label(title, 17f, navy, true))
        textBox.addView(label(subtitle, 12f, ink).apply { setPadding(0, dp(4), 0, 0) })
        row.addView(textBox, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(label("›", 28f, blue, true))
        card.addView(row)
        card.setOnClickListener { when (destination) {
            "entry" -> showEntry()
            "records" -> showRecords()
            "weekly" -> showWeekly()
        } }
        screenHost.addView(card, matchWrap())
    }

    private fun showEntry() {
        activeScreen = "entry"
        screenHost.removeAllViews()
        addPageHeader("New daily record", "Record what you worked on today.")
        val card = panel()
        dateField = addField(card, "Date (YYYY-MM-DD)", SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()), false)
        workField = addField(card, "Work done today *", "", true)
        toolsField = addField(card, "Tools / equipment used", "", false)
        skillsField = addField(card, "Skills or lessons learned", "", true)
        challengesField = addField(card, "Challenges encountered", "", true)
        recommendationsField = addField(card, "Recommendations", "", true)
        card.addView(actionButton("Save daily record", blue).apply { setOnClickListener { saveEntry() } }, matchWrap())
        screenHost.addView(card, matchWrap())
        screenHost.addView(actionButton("Back to dashboard", navy).apply { setOnClickListener { showHome() } }, matchWrap())
    }

    private fun showRecords() {
        activeScreen = "records"
        screenHost.removeAllViews()
        addPageHeader("Saved records", "Your saved daily entries and sync status.")
        val tableCard = panel()
        val table = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setBackgroundColor(navy) }
        header.addView(tableCell("DATE", true), LinearLayout.LayoutParams(dp(96), -2))
        header.addView(tableCell("WORK DONE / STATUS", true), LinearLayout.LayoutParams(0, -2, 1f))
        table.addView(header)
        tableCard.addView(table)
        screenHost.addView(tableCard, matchWrap())
        val owner = currentSession?.userId ?: "local"
        lifecycleScope.launch {
            db.entries().observeAllForOwner(owner).collect { entries ->
                if (activeScreen != "records") return@collect
                table.removeViews(1, (table.childCount - 1).coerceAtLeast(0))
                if (entries.isEmpty()) {
                    table.addView(label("No saved records yet. Add your first daily record.", 14f, ink).apply { setPadding(dp(8), dp(14), dp(8), dp(14)) })
                } else entries.forEachIndexed { index, entry ->
                    val row = LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setBackgroundColor(if (index % 2 == 0) Color.WHITE else Color.rgb(247, 249, 253))
                    }
                    row.addView(tableCell(entry.entryDate, false), LinearLayout.LayoutParams(dp(96), -2))
                    val details = LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(dp(8), dp(10), dp(6), dp(10))
                    }
                    details.addView(label(entry.workDone, 13f, ink, true))
                    details.addView(label(if (entry.syncStatus == "synced") "SYNCED" else "PENDING SYNC", 10f, if (entry.syncStatus == "synced") green else Color.rgb(174, 105, 20), true).apply { setPadding(0, dp(4), 0, 0) })
                    row.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
                    table.addView(row)
                    table.addView(View(this@MainActivity).apply { setBackgroundColor(Color.rgb(225, 230, 238)) }, LinearLayout.LayoutParams(-1, dp(1)))
                }
            }
        }
        screenHost.addView(actionButton("＋ New daily record", blue).apply { setOnClickListener { showEntry() } }, matchWrap())
        screenHost.addView(actionButton("Back to dashboard", navy).apply { setOnClickListener { showHome() } }, matchWrap())
    }

    private fun showWeekly() {
        activeScreen = "weekly"
        screenHost.removeAllViews()
        addPageHeader("Weekly reports", "A dedicated space for weekly summaries and review.")
        val card = panel()
        card.addView(label("WEEKLY SUMMARY", 12f, blue, true))
        card.addView(label("Weekly reporting is the next stage.", 19f, navy, true).apply { setPadding(0, dp(8), 0, dp(6)) })
        card.addView(label("This section is reserved for captured weekly activities, student reflection, supervisor comments and review status. Your existing daily records remain available in Saved records.", 14f, ink))
        screenHost.addView(card, matchWrap())
        screenHost.addView(actionButton("View saved daily records", blue).apply { setOnClickListener { showRecords() } }, matchWrap())
        screenHost.addView(actionButton("Back to dashboard", navy).apply { setOnClickListener { showHome() } }, matchWrap())
    }

    private fun addPageHeader(title: String, subtitle: String) {
        screenHost.addView(actionButton("‹ Dashboard", navy).apply { setOnClickListener { showHome() } })
        screenHost.addView(label(title, 25f, navy, true).apply { setPadding(0, dp(16), 0, dp(4)) })
        screenHost.addView(label(subtitle, 14f, ink).apply { setPadding(0, 0, 0, dp(16)) })
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
                withContext(Dispatchers.IO) {
                    db.entries().claimLocalEntries(session.userId)
                    SupabaseApi(this@MainActivity).ensureStudentProfile(session, email)
                }
                showHome()
                observeEntries(session.userId)
                SyncScheduler.syncNow(this@MainActivity)
            } catch (e: Exception) {
                authMessage.text = e.message ?: "Sign-in failed. Check your connection and account details."
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
                withContext(Dispatchers.IO) { SupabaseApi(this@MainActivity).signUp(email, password) }
                authMessage.text = "Account request submitted. Check your email for a confirmation link if required, then sign in."
                authMessage.setTextColor(green)
            } catch (e: Exception) {
                authMessage.text = e.message ?: "Could not create account. Please try again."
                authMessage.setTextColor(Color.rgb(170, 55, 40))
            }
        }
    }

    private fun signOut() {
        sessionStore.clear()
        currentSession = null
        observeEntries("local")
        showAuth()
    }

    private fun observeEntries(ownerId: String) {
        entriesJob?.cancel()
        entriesJob = lifecycleScope.launch {
            db.entries().observeAllForOwner(ownerId).collect { entries ->
                val pending = entries.count { it.syncStatus == "pending" }
                val synced = entries.count { it.syncStatus == "synced" }
                if (::status.isInitialized) {
                    status.text = when {
                        pending > 0 -> "Offline-safe • $pending record(s) awaiting upload"
                        synced > 0 -> "Sync complete • $synced record(s) uploaded"
                        else -> "Your records are saved on this device when offline."
                    }
                }
            }
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
            db.entries().save(LogbookEntry(
                entryDate = date,
                workDone = work,
                toolsUsed = toolsField.text.toString().trim(),
                skillsLearned = skillsField.text.toString().trim(),
                challenges = challengesField.text.toString().trim(),
                recommendations = recommendationsField.text.toString().trim(),
                ownerUserId = ownerId
            ))
            if (currentSession != null) SyncScheduler.syncNow(this@MainActivity)
            Toast.makeText(this@MainActivity, if (currentSession != null) "Daily record saved. Sync queued." else "Daily record saved offline.", Toast.LENGTH_LONG).show()
            showRecords()
        }
    }

    private fun addField(parent: LinearLayout, fieldLabel: String, initial: String, multiline: Boolean): EditText {
        parent.addView(label(fieldLabel, 13f, ink, true).apply { setPadding(0, dp(12), 0, dp(5)) })
        return EditText(this).also { field ->
            field.setText(initial)
            field.textSize = 15f
            field.setSingleLine(!multiline)
            if (multiline) field.minLines = 2
            field.setTextColor(navy)
            field.setHintTextColor(Color.rgb(135, 145, 160))
            field.setPadding(dp(12), dp(10), dp(12), dp(10))
            field.background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.WHITE)
                setStroke(dp(1), Color.rgb(220, 226, 236))
                cornerRadius = dp(10).toFloat()
            }
            parent.addView(field, matchWrap())
        }
    }

    private fun panel(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.WHITE)
            setStroke(dp(1), Color.rgb(231, 235, 243))
            cornerRadius = dp(16).toFloat()
        }
    }

    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun actionButton(caption: String, color: Int): Button = Button(this).apply {
        text = caption
        textSize = 14f
        isAllCaps = false
        setTextColor(Color.WHITE)
        backgroundTintList = android.content.res.ColorStateList.valueOf(color)
        minHeight = dp(46)
    }

    private fun tableCell(value: String, header: Boolean) = TextView(this).apply {
        text = value
        textSize = if (header) 11f else 12f
        typeface = if (header) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        setTextColor(if (header) Color.WHITE else ink)
        setPadding(dp(8), dp(12), dp(5), dp(12))
    }

    private fun matchWrap() = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
