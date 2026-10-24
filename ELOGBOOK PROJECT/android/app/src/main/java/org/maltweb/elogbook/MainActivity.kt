package org.maltweb.elogbook

import androidx.activity.ComponentActivity
import android.os.Bundle
import android.app.AlertDialog
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
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    private lateinit var db: LogbookDatabase
    private lateinit var screenHost: LinearLayout
    private lateinit var scrollContainer: ScrollView
    private lateinit var navigationOverlay: LinearLayout
    private lateinit var navigationLoadingLabel: TextView
    private var navigationGeneration = 0
    private var navigationStartedAt = 0L
    private val navigationHandler by lazy { android.os.Handler(android.os.Looper.getMainLooper()) }
    private lateinit var status: TextView
    private lateinit var authMessage: TextView
    private lateinit var emailField: EditText
    private lateinit var passwordField: EditText
    private lateinit var registrationFields: LinearLayout
    private lateinit var registrationName: EditText
    private lateinit var registrationCourse: EditText
    private lateinit var registrationStudentNumber: EditText
    private lateinit var registrationOrganizationName: EditText
    private lateinit var registrationOrganizationKey: EditText
    private lateinit var registrationRole: Spinner
    private lateinit var registrationGender: Spinner
    private lateinit var registrationOrganization: Spinner
    private lateinit var registrationAction: Button
    private lateinit var signInButton: Button
    private lateinit var forgotPasswordButton: Button
    private lateinit var switchToLoginButton: Button
    private var organizations: JSONArray = JSONArray()
    private var currentProfile: JSONObject? = null
    private lateinit var dateField: EditText
    private lateinit var workField: EditText
    private lateinit var toolsField: EditText
    private lateinit var skillsField: EditText
    private lateinit var challengesField: EditText
    private lateinit var recommendationsField: EditText
    private lateinit var sessionStore: SessionStore
    private var currentSession: AuthSession? = null
    private var currentRole: String = "student"
    private var entriesJob: Job? = null
    private var weeklyJob: Job? = null
    private lateinit var weeklyList: LinearLayout
    private var activeScreen = "home"

    private val navy = Color.rgb(18, 35, 61)
    private val blue = Color.rgb(39, 91, 166)
    private val gold = Color.rgb(229, 169, 52)
    private val pageBackground = Color.rgb(244, 247, 252)
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
            setBackgroundColor(pageBackground)
        }
        screenHost = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
        }
        scrollContainer = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(0, 0, 0, dp(28))
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        }
        scrollContainer.addView(screenHost)
        val frame = FrameLayout(this).apply { setBackgroundColor(pageBackground) }
        frame.addView(scrollContainer, FrameLayout.LayoutParams(-1, -1))
        navigationOverlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            visibility = View.GONE
            alpha = 0f
            isClickable = true
            isFocusable = true
            setBackgroundColor(Color.argb(222, 244, 247, 252))
        }
        val loadingCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(dp(28), dp(22), dp(28), dp(22))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.WHITE)
                setStroke(dp(1), Color.rgb(225, 231, 240))
                cornerRadius = dp(20).toFloat()
            }
        }
        loadingCard.addView(ImageView(this).apply {
            setImageResource(R.drawable.elogbook_logo)
            contentDescription = "E-Logbook logo"
        }, LinearLayout.LayoutParams(dp(58), dp(58)))
        loadingCard.addView(ProgressBar(this).apply {
            isIndeterminate = true
            indeterminateTintList = android.content.res.ColorStateList.valueOf(blue)
        }, LinearLayout.LayoutParams(dp(34), dp(34)).apply {
            topMargin = dp(12)
            bottomMargin = dp(10)
        })
        navigationLoadingLabel = label("Opening E-Logbook…", 13f, navy, true)
        loadingCard.addView(navigationLoadingLabel)
        navigationOverlay.addView(loadingCard, LinearLayout.LayoutParams(-2, -2))
        frame.addView(navigationOverlay, FrameLayout.LayoutParams(-1, -1))
        setContentView(frame)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        if (currentSession == null) {
            showAuth()
        } else {
            showHome()
            val restoredSession = currentSession!!
            lifecycleScope.launch {
                try {
                    val roleResult = withContext(Dispatchers.IO) {
                        val api = SupabaseApi(this@MainActivity)
                        val fresh = api.ensureFreshSession(restoredSession)
                        Pair(fresh, api.fetchProfile(fresh))
                    }
                    currentSession = roleResult.first
                    currentProfile = roleResult.second
                    sessionStore.save(roleResult.first)
                    currentRole = currentProfile?.optString("role")?.ifBlank { "unregistered" } ?: "unregistered"
                    if (currentSession?.userId == restoredSession.userId) {
                        if (currentRole == "unregistered") {
                            showAuth()
                            registrationFields.visibility = View.VISIBLE
                            registrationAction.text = "Complete registration"
                            signInButton.visibility = View.GONE
                            forgotPasswordButton.visibility = View.GONE
                            switchToLoginButton.visibility = View.VISIBLE
                            authMessage.text = "Choose your account type and complete your organisation details."
                            loadOrganizations()
                        } else showHome()
                    }
                } catch (_: Exception) {
                    // Keep existing local records available when opening without a connection.
                }
            }
        }
        observeEntries(currentSession?.userId ?: "local")
        if (currentSession != null) {
            observeWeekly(currentSession!!.userId)
            SyncScheduler.syncNow(this)
        }
    }

    private fun showNavigationLoading(message: String) {
        if (!::navigationOverlay.isInitialized || !::navigationLoadingLabel.isInitialized) return
        navigationGeneration += 1
        navigationStartedAt = android.os.SystemClock.elapsedRealtime()
        navigationLoadingLabel.text = message
        navigationOverlay.animate().cancel()
        navigationOverlay.visibility = View.VISIBLE
        navigationOverlay.alpha = 0f
        navigationOverlay.animate().alpha(1f).setDuration(140).start()
    }

    private fun hideNavigationLoading() {
        if (!::navigationOverlay.isInitialized) return
        val generation = navigationGeneration
        val elapsed = android.os.SystemClock.elapsedRealtime() - navigationStartedAt
        val wait = (320L - elapsed).coerceAtLeast(0L)
        navigationHandler.postDelayed({
            if (generation != navigationGeneration || !::navigationOverlay.isInitialized) return@postDelayed
            navigationOverlay.animate().cancel()
            navigationOverlay.animate().alpha(0f).setDuration(180).withEndAction {
                if (generation == navigationGeneration) {
                    navigationOverlay.visibility = View.GONE
                    navigationOverlay.alpha = 0f
                }
            }.start()
        }, wait)
    }

    private fun navigateTo(message: String, action: () -> Unit) {
        showNavigationLoading(message)
        try { action() } finally { hideNavigationLoading() }
    }

    private fun showAuth() {
        activeScreen = "auth"
        screenHost.removeAllViews()
        screenHost.setPadding(dp(22), dp(34), dp(22), dp(22))
        screenHost.addView(ImageView(this).apply {
            setImageResource(R.drawable.elogbook_logo)
            contentDescription = "E-Logbook logo"
        }, LinearLayout.LayoutParams(dp(70), dp(70)))
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
        signInButton = actionButton("Sign in", blue)
        signInButton.setOnClickListener { signIn() }
        card.addView(signInButton, matchWrap())
        forgotPasswordButton = actionButton("Forgot password?", navy).apply { setOnClickListener { requestPasswordReset() } }
        card.addView(forgotPasswordButton, matchWrap())
        registrationFields = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        registrationRole = Spinner(this)
        registrationRole.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            listOf("Student", "Field supervisor", "Institution supervisor", "Organisation account"))
        registrationFields.addView(label("Account type", 13f, ink, true))
        registrationFields.addView(registrationRole, matchWrap())
        registrationName = addField(registrationFields, "Full names", "", false)
        registrationGender = Spinner(this)
        registrationGender.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            listOf("Choose gender", "Female", "Male", "Prefer not to say"))
        registrationFields.addView(label("Gender", 13f, ink, true).apply { setPadding(0, dp(10), 0, dp(5)) })
        registrationFields.addView(registrationGender, matchWrap())
        registrationCourse = addField(registrationFields, "Course / subject", "", false)
        registrationStudentNumber = addField(registrationFields, "Student / index number", "", false)
        registrationOrganizationName = addField(registrationFields, "Organisation name (school / institution)", "", false)
        registrationOrganization = Spinner(this)
        registrationFields.addView(label("Select your organisation", 13f, ink, true).apply { setPadding(0, dp(10), 0, dp(5)) })
        registrationFields.addView(registrationOrganization, matchWrap())
        registrationOrganizationKey = addField(registrationFields, "Organisation key", "", false)
        registrationRole.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { updateRegistrationVisibility() }
        }
        card.addView(registrationFields, matchWrap())
        registrationAction = actionButton("Register new account", navy)
        registrationAction.setOnClickListener {
            if (registrationFields.visibility != View.VISIBLE) {
                registrationFields.visibility = View.VISIBLE
                registrationAction.text = if (currentSession == null) "Create account" else "Complete registration"
                signInButton.visibility = View.GONE
                forgotPasswordButton.visibility = View.GONE
                switchToLoginButton.visibility = View.VISIBLE
                loadOrganizations()
                updateRegistrationVisibility()
            } else signUp()
        }
        card.addView(registrationAction, matchWrap())
        switchToLoginButton = actionButton("Already have an account? Sign in", navy).apply {
            visibility = View.GONE
            setOnClickListener { showAuth() }
        }
        card.addView(switchToLoginButton, matchWrap())
        authMessage = label("", 13f, green).apply { setPadding(0, dp(10), 0, 0) }
        card.addView(authMessage)
        screenHost.addView(card, matchWrap())
        screenHost.addView(label("Your saved offline entries stay on this device.", 12f, ink).apply {
            gravity = android.view.Gravity.CENTER
            setPadding(0, dp(24), 0, 0)
        })
    }

    private fun setRegistrationFieldVisible(field: EditText, visible: Boolean) {
        field.visibility = if (visible) View.VISIBLE else View.GONE
        val parent = field.parent as? LinearLayout ?: return
        val index = parent.indexOfChild(field)
        if (index > 0) parent.getChildAt(index - 1).visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun updateRegistrationVisibility() {
        if (!::registrationRole.isInitialized) return
        val role = registrationRole.selectedItemPosition
        val organizationAccount = role == 3
        setRegistrationFieldVisible(registrationCourse, !organizationAccount)
        setRegistrationFieldVisible(registrationStudentNumber, role == 0)
        setRegistrationFieldVisible(registrationOrganizationName, organizationAccount)
        registrationOrganization.visibility = if (organizationAccount) View.GONE else View.VISIBLE
        if (registrationOrganization.parent is LinearLayout) {
            val parent = registrationOrganization.parent as LinearLayout
            val index = parent.indexOfChild(registrationOrganization)
            if (index > 0) parent.getChildAt(index - 1).visibility = if (organizationAccount) View.GONE else View.VISIBLE
        }
        setRegistrationFieldVisible(registrationOrganizationKey, role == 1 || role == 2)
    }

    private fun loadOrganizations() {
        if (!::registrationOrganization.isInitialized) return
        lifecycleScope.launch {
            try {
                organizations = withContext(Dispatchers.IO) { SupabaseApi(this@MainActivity).fetchOrganizations() }
                val names = ArrayList<String>()
                for (i in 0 until organizations.length()) {
                    names.add(organizations.optJSONObject(i)?.optString("name") ?: "Organisation")
                }
                if (names.isEmpty()) names.add("No organisations registered yet")
                registrationOrganization.adapter = ArrayAdapter(this@MainActivity,
                    android.R.layout.simple_spinner_dropdown_item, names)
            } catch (_: Exception) {
                if (::authMessage.isInitialized) authMessage.text = "Could not load organisations. Check your connection and try again."
            }
        }
    }

    private fun showHome() {
        activeScreen = "home"
        screenHost.setPadding(dp(18), dp(18), dp(18), dp(22))
        screenHost.removeAllViews()
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        top.addView(ImageView(this).apply {
            setImageResource(R.drawable.elogbook_logo)
            contentDescription = "E-Logbook logo"
        }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { rightMargin = dp(12) })
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(label("E-LOGBOOK", 12f, blue, true))
        titles.addView(label(when (currentRole) {
            "organization_admin" -> "Organisation workspace"
            "field_supervisor", "institution_supervisor" -> "Supervisor workspace"
            else -> "Student workspace"
        }, 24f, navy, true))
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

        if (currentRole in listOf("field_supervisor", "institution_supervisor") &&
            currentProfile?.optString("approval_status", "approved") != "approved") {
            screenHost.addView(panel().apply {
                addView(label("Awaiting organisation approval", 18f, navy, true))
                addView(label("Your organisation must approve this account before reviews, attendance or marks become available.", 13f, ink))
            }, matchWrap())
            return
        }
        when (currentRole) {
            "organization_admin" -> {
                screenHost.addView(label("ORGANISATION MANAGEMENT", 12f, blue, true).apply { setPadding(0, dp(22), 0, dp(10)) })
                addNavigationCard("▦", "Organisation dashboard", "Approve supervisors, link students, view reports and award marks", "organization")
            }
            "field_supervisor", "institution_supervisor" -> {
                screenHost.addView(label("SUPERVISOR TOOLS", 12f, blue, true).apply { setPadding(0, dp(22), 0, dp(10)) })
                addNavigationCard("✓", "Supervisor reviews", "Review weekly reports from assigned students", "reviews")
                addNavigationCard("★", "Attendance and marks", "Record attendance and award performance marks", "supervisorTools")
            }
            "unregistered" -> {
                screenHost.addView(label("Finish registering to access your workspace.", 14f, ink))
                screenHost.addView(actionButton("Complete registration", blue).apply {
                    setOnClickListener { showAuth(); registrationFields.visibility = View.VISIBLE; registrationAction.text = "Complete registration"; loadOrganizations() }
                }, matchWrap())
            }
            else -> {
                screenHost.addView(label("YOUR LOGBOOK", 12f, blue, true).apply { setPadding(0, dp(22), 0, dp(10)) })
                addNavigationCard("＋", "New daily record", "Capture today's work, tools and skills", "entry")
                addNavigationCard("▤", "Saved records", "View your daily entries in a table", "records")
                addNavigationCard("▦", "Weekly reports", "Weekly summaries and supervisor review", "weekly")
                addNavigationCard("★", "Performance and rankings", "View attendance, marks and course ranking", "performance")
            }
        }
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
        card.setOnClickListener {
            navigateTo("Opening " + title + "…") {
                when (destination) {
                    "entry" -> showEntry()
                    "records" -> showRecords()
                    "weekly" -> showWeekly()
                    "performance" -> showStudentPerformance()
                    "reviews" -> showSupervisorReviews()
                    "organization" -> showOrganizationWorkspace()
                    "supervisorTools" -> showSupervisorTools()
                }
            }
        }
        screenHost.addView(card, matchWrap())
    }

    private fun showEntry(existing: LogbookEntry? = null) {
        activeScreen = "entry"
        screenHost.removeAllViews()
        addPageHeader(if (existing == null) "New daily record" else "Edit daily record",
            if (existing == null) "Record what you worked on today." else "Update your saved entry. Changing report content clears its previous review.")
        val card = panel()
        dateField = addField(card, "Date (YYYY-MM-DD)", existing?.entryDate ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()), false)
        workField = addField(card, "Work done today *", existing?.workDone ?: "", true)
        toolsField = addField(card, "Tools / equipment used", existing?.toolsUsed ?: "", false)
        skillsField = addField(card, "Skills or lessons learned", existing?.skillsLearned ?: "", true)
        challengesField = addField(card, "Challenges encountered", existing?.challenges ?: "", true)
        recommendationsField = addField(card, "Recommendations", existing?.recommendations ?: "", true)
        card.addView(actionButton(if (existing == null) "Save daily record" else "Save changes", blue).apply {
            setOnClickListener { saveEntry() }
        }, matchWrap())
        screenHost.addView(card, matchWrap())
        screenHost.addView(actionButton("Back to saved records", navy).apply {
            setOnClickListener { navigateTo("Returning to saved records…") { showRecords() } }
        }, matchWrap())
    }

    private fun showRecords() {
        activeScreen = "records"
        screenHost.removeAllViews()
        addPageHeader("Saved records", "Tap a row to view and edit its saved fields and supervisor feedback.")
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
                    if (entry.supervisorComments.isNotBlank()) details.addView(label("Supervisor feedback: " + entry.supervisorComments, 12f, ink).apply { setPadding(0, dp(4), 0, 0) })
                    if (!entry.supervisorReviewedAt.isNullOrBlank()) details.addView(label("Reviewed: " + entry.supervisorReviewedAt, 10f, green, true).apply { setPadding(0, dp(4), 0, 0) })
                    details.addView(label(if (entry.syncStatus == "synced") "SYNCED" else "PENDING SYNC", 10f, if (entry.syncStatus == "synced") green else Color.rgb(174, 105, 20), true).apply { setPadding(0, dp(4), 0, 0) })
                    details.addView(label("Tap to view/edit", 10f, blue, true).apply { setPadding(0, dp(4), 0, 0) })
                    row.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
                    row.setOnClickListener { navigateTo("Opening saved record…") { showEntryDetails(entry) } }
                    row.isClickable = true
                    table.addView(row)
                    table.addView(View(this@MainActivity).apply { setBackgroundColor(Color.rgb(225, 230, 238)) }, LinearLayout.LayoutParams(-1, dp(1)))
                }
            }
        }
        screenHost.addView(actionButton("＋ New daily record", blue).apply {
            setOnClickListener { navigateTo("Opening new daily record…") { showEntry() } }
        }, matchWrap())
        screenHost.addView(actionButton("Back to dashboard", navy).apply {
            setOnClickListener { navigateTo("Returning to dashboard…") { showHome() } }
        }, matchWrap())
    }

    private fun showEntryDetails(entry: LogbookEntry) {
        activeScreen = "entryDetails"
        screenHost.removeAllViews()
        addPageHeader("Saved daily record", "Review the saved information in a table. Choose Edit only when you want to change it.")
        val detailCard = panel()
        detailCard.addView(label("DAILY RECORD DETAILS", 12f, blue, true).apply { setPadding(0, 0, 0, dp(10)) })
        val reviewStatus = if (!entry.supervisorReviewedAt.isNullOrBlank()) "Reviewed on " + entry.supervisorReviewedAt else "Awaiting supervisor review"
        val rows = listOf(
            listOf("Date", entry.entryDate),
            listOf("Work done today", entry.workDone.ifBlank { "—" }),
            listOf("Tools / equipment used", entry.toolsUsed.ifBlank { "—" }),
            listOf("Skills or lessons learned", entry.skillsLearned.ifBlank { "—" }),
            listOf("Challenges encountered", entry.challenges.ifBlank { "—" }),
            listOf("Recommendations", entry.recommendations.ifBlank { "—" }),
            listOf("Supervisor feedback", entry.supervisorComments.ifBlank { "No supervisor feedback yet" }),
            listOf("Review status", reviewStatus),
            listOf("Sync status", if (entry.syncStatus == "synced") "Synced to account" else "Saved on device • Pending sync"),
            listOf("Last saved", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(entry.updatedAt)))
        )
        addDataTable(detailCard, listOf("FIELD", "SAVED INFORMATION"), rows)
        screenHost.addView(detailCard, matchWrap())
        screenHost.addView(actionButton("Edit this record", blue).apply {
            setOnClickListener { navigateTo("Opening record editor…") { showEntry(entry) } }
        }, matchWrap())
        screenHost.addView(actionButton("Back to saved records", navy).apply {
            setOnClickListener { navigateTo("Returning to saved records…") { showRecords() } }
        }, matchWrap())
    }

    private fun showWeekly(existing: WeeklySummary? = null) {
        activeScreen = "weekly"
        screenHost.removeAllViews()
        addPageHeader(if (existing == null) "Weekly reports" else "Edit weekly summary", if (existing == null) "Capture your week and track supervisor review." else "Update your saved report. Changing its content clears the previous review.")

        val form = panel()
        form.addView(label("WEEKLY ACTIVITY SUMMARY", 12f, blue, true))
        form.addView(label("What did you accomplish this week?", 19f, navy, true).apply { setPadding(0, dp(8), 0, dp(4)) })
        val weekStart = addField(form, "Week starting (Monday, YYYY-MM-DD)", existing?.weekStart ?: mondayDate(), false)
        val summaryField = addField(form, "Work completed this week *", existing?.summary ?: "", true)
        val skillsField = addField(form, "Skills or lessons learned", existing?.skillsLearned ?: "", true)
        val challengesField = addField(form, "Challenges and solutions", existing?.challenges ?: "", true)
        val message = label("", 13f, green).apply { setPadding(0, dp(8), 0, dp(4)) }
        form.addView(message)
        form.addView(actionButton(if (existing == null) "Save weekly summary" else "Save changes", blue).apply {
            setOnClickListener {
                val startDate = weekStart.text.toString().trim()
                val summary = summaryField.text.toString().trim()
                if (!startDate.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")) || summary.isBlank()) {
                    message.text = "Enter a valid week-start date and describe the work completed."
                    message.setTextColor(Color.rgb(170, 55, 40))
                    return@setOnClickListener
                }
                val owner = currentSession?.userId ?: "local"
                lifecycleScope.launch {
                    try {
                        val skills = skillsField.text.toString().trim()
                        val challenges = challengesField.text.toString().trim()
                        val previous = db.weeklies().findByOwnerAndWeek(owner, startDate)
                        val changed = previous == null || previous.summary != summary ||
                            previous.skillsLearned != skills || previous.challenges != challenges
                        val updated = if (previous == null) {
                            WeeklySummary(weekStart = startDate, summary = summary, skillsLearned = skills,
                                challenges = challenges, ownerUserId = owner)
                        } else previous.copy(
                            summary = summary,
                            skillsLearned = skills,
                            challenges = challenges,
                            syncStatus = if (changed) "pending" else previous.syncStatus,
                            supervisorComments = if (changed) "" else previous.supervisorComments,
                            supervisorReviewedAt = if (changed) null else previous.supervisorReviewedAt,
                            updatedAt = if (changed) System.currentTimeMillis() else previous.updatedAt
                        )
                        db.weeklies().save(updated)
                        if (currentSession != null && changed) SyncScheduler.syncNow(this@MainActivity)
                        summaryField.text.clear()
                        skillsField.text.clear()
                        challengesField.text.clear()
                        message.text = when {
                            !changed -> "No changes detected; previous feedback was preserved."
                            currentSession != null -> "Saved on this device. Sync has been queued."
                            else -> "Saved on this device. Sign in to sync it later."
                        }
                        message.setTextColor(green)
                        Toast.makeText(this@MainActivity, "Weekly summary saved.", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        message.text = e.message ?: "Could not save the weekly summary."
                        message.setTextColor(Color.rgb(170, 55, 40))
                    }
                }
            }
        }, matchWrap())
        screenHost.addView(form, matchWrap())

        val saved = panel()
        saved.addView(label("SAVED WEEKLY SUMMARIES", 12f, blue, true))
        saved.addView(label("Review status and supervisor comments appear with each saved report.", 13f, ink).apply { setPadding(0, dp(4), 0, dp(10)) })
        weeklyList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        saved.addView(weeklyList)
        screenHost.addView(saved, matchWrap())
        screenHost.addView(actionButton("View saved daily records", blue).apply {
            setOnClickListener { navigateTo("Opening saved records…") { showRecords() } }
        }, matchWrap())
        screenHost.addView(actionButton("Back to dashboard", navy).apply {
            setOnClickListener { navigateTo("Returning to dashboard…") { showHome() } }
        }, matchWrap())
        observeWeekly(currentSession?.userId ?: "local")
    }

    private fun observeWeekly(ownerId: String) {
        weeklyJob?.cancel()
        weeklyJob = lifecycleScope.launch {
            db.weeklies().observeAllForOwner(ownerId).collect { summaries ->
                if (activeScreen != "weekly" || !::weeklyList.isInitialized) return@collect
                weeklyList.removeAllViews()
                if (summaries.isEmpty()) {
                    weeklyList.addView(label("No weekly summaries yet. Your saved reports will appear here.", 13f, ink).apply {
                        setPadding(0, dp(8), 0, dp(8))
                    })
                } else {
                    summaries.forEachIndexed { index, item ->
                        val row = LinearLayout(this@MainActivity).apply {
                            orientation = LinearLayout.VERTICAL
                            setPadding(dp(12), dp(12), dp(12), dp(12))
                            background = android.graphics.drawable.GradientDrawable().apply {
                                setColor(if (index % 2 == 0) Color.WHITE else Color.rgb(247, 249, 253))
                                setStroke(dp(1), Color.rgb(225, 230, 238))
                                cornerRadius = dp(10).toFloat()
                            }
                        }
                        row.addView(label("Week starting " + item.weekStart, 15f, navy, true))
                        row.addView(label(item.summary, 13f, ink).apply { setPadding(0, dp(6), 0, dp(5)) })
                        if (item.skillsLearned.isNotBlank()) row.addView(label("Skills learned: " + item.skillsLearned, 12f, ink))
                        if (item.challenges.isNotBlank()) row.addView(label("Challenges: " + item.challenges, 12f, ink).apply { setPadding(0, dp(4), 0, 0) })
                        row.addView(label(if (item.syncStatus == "synced") "SYNCED • Awaiting supervisor review" else "SAVED OFFLINE • Pending sync", 11f, if (item.syncStatus == "synced") green else Color.rgb(174, 105, 20), true).apply {
                            setPadding(0, dp(8), 0, dp(4))
                        })
                        if (item.supervisorComments.isNotBlank()) {
                            row.addView(label("Supervisor comments: " + item.supervisorComments, 12f, ink))
                        }
                        if (!item.supervisorReviewedAt.isNullOrBlank()) {
                            row.addView(label("Reviewed on " + item.supervisorReviewedAt, 11f, green, true).apply { setPadding(0, dp(5), 0, 0) })
                        }
                        row.addView(actionButton("View / edit summary", blue).apply {
                            setOnClickListener { showWeekly(item) }
                        }, matchWrap())
                        weeklyList.addView(row, matchWrap())
                    }
                }
            }
        }
    }

    private fun showSupervisorReviews() {
        activeScreen = "reviews"
        screenHost.removeAllViews()
        addPageHeader("Supervisor reviews", "Review assigned daily records and weekly summaries.")
        val message = label("Loading assigned student reports…", 13f, ink).apply { setPadding(0, 0, 0, dp(10)) }
        screenHost.addView(message)
        val queue = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        screenHost.addView(queue, matchWrap())
        screenHost.addView(actionButton("Refresh review queue", blue).apply {
            setOnClickListener { showSupervisorReviews() }
        }, matchWrap())

        val session = currentSession
        if (session == null) {
            message.text = "Please sign in as an assigned supervisor."
            return
        }
        lifecycleScope.launch {
            try {
                val reports = withContext(Dispatchers.IO) {
                    val api = SupabaseApi(this@MainActivity)
                    val fresh = api.ensureFreshSession(session)
                    Triple(fresh, api.fetchAssignedDailyEntries(fresh), api.fetchAssignedWeeklySummaries(fresh))
                }
                currentSession = reports.first
                sessionStore.save(reports.first)
                val daily = reports.second
                val weekly = reports.third
                queue.removeAllViews()
                if (daily.length() == 0 && weekly.length() == 0) {
                    message.text = "No reports are assigned to this supervisor account yet."
                    return@launch
                }
                message.text = daily.length().toString() + " daily report(s) • " +
                    weekly.length().toString() + " weekly report(s)"
                queue.addView(label("DAILY REPORTS", 13f, blue, true).apply {
                    setPadding(0, dp(8), 0, dp(8))
                })
                for (i in 0 until daily.length()) {
                    val item = daily.getJSONObject(i)
                    val entryId = item.optString("id")
                    val student = item.optJSONObject("student")
                    val studentName = student?.optString("full_name").orEmpty()
                        .ifBlank { "Student " + item.optString("student_id").take(8) }
                    val card = panel()
                    card.addView(label(studentName + " • " + item.optString("entry_date"), 16f, navy, true))
                    card.addView(label("Work done", 12f, blue, true).apply { setPadding(0, dp(6), 0, dp(2)) })
                    card.addView(label(item.optString("work_done"), 14f, ink))
                    for ((field, caption) in listOf(
                        "tools_used" to "Tools",
                        "skills_learned" to "Skills learned",
                        "challenges" to "Challenges",
                        "recommendations" to "Recommendations"
                    )) {
                        val value = item.optString(field)
                        if (value.isNotBlank()) card.addView(label("$caption: $value", 12f, ink).apply {
                            setPadding(0, dp(4), 0, 0)
                        })
                    }
                    val comments = addField(card, "Supervisor feedback", item.optString("supervisor_comments"), true)
                    card.addView(label(
                        if (item.isNull("supervisor_signed_at")) "Awaiting review" else "Reviewed " + item.optString("supervisor_signed_at"),
                        11f, if (item.isNull("supervisor_signed_at")) Color.rgb(174, 105, 20) else green, true
                    ).apply { setPadding(0, dp(7), 0, dp(5)) })
                    card.addView(actionButton("Save daily feedback and mark reviewed", blue).apply {
                        setOnClickListener {
                            isEnabled = false
                            lifecycleScope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        val api = SupabaseApi(this@MainActivity)
                                        val fresh = api.ensureFreshSession(currentSession ?: session)
                                        api.saveDailyReview(fresh, entryId, comments.text.toString().trim())
                                        sessionStore.save(fresh)
                                        currentSession = fresh
                                    }
                                    Toast.makeText(this@MainActivity, "Daily report review saved.", Toast.LENGTH_LONG).show()
                                    showSupervisorReviews()
                                } catch (e: Exception) {
                                    isEnabled = true
                                    Toast.makeText(this@MainActivity, e.message ?: "Could not save daily feedback.", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }, matchWrap())
                    queue.addView(card, matchWrap())
                }

                queue.addView(label("WEEKLY SUMMARIES", 13f, blue, true).apply {
                    setPadding(0, dp(16), 0, dp(8))
                })
                for (i in 0 until weekly.length()) {
                    val item = weekly.getJSONObject(i)
                    val summaryId = item.optString("id")
                    val student = item.optJSONObject("student")
                    val studentName = student?.optString("full_name").orEmpty()
                        .ifBlank { "Student " + item.optString("student_id").take(8) }
                    val card = panel()
                    card.addView(label(studentName + " • week of " + item.optString("week_start"), 16f, navy, true))
                    card.addView(label("Work completed", 12f, blue, true).apply { setPadding(0, dp(6), 0, dp(2)) })
                    card.addView(label(item.optString("summary"), 14f, ink))
                    val skills = item.optString("skills_learned")
                    val challenges = item.optString("challenges")
                    if (skills.isNotBlank()) card.addView(label("Skills learned: " + skills, 12f, ink).apply {
                        setPadding(0, dp(5), 0, 0)
                    })
                    if (challenges.isNotBlank()) card.addView(label("Challenges: " + challenges, 12f, ink).apply {
                        setPadding(0, dp(5), 0, 0)
                    })
                    val comments = addField(card, "Supervisor feedback", item.optString("supervisor_comments"), true)
                    card.addView(label(
                        if (item.isNull("supervisor_signed_at")) "Awaiting review" else "Reviewed " + item.optString("supervisor_signed_at"),
                        11f, if (item.isNull("supervisor_signed_at")) Color.rgb(174, 105, 20) else green, true
                    ).apply { setPadding(0, dp(7), 0, dp(5)) })
                    card.addView(actionButton("Save weekly feedback and mark reviewed", blue).apply {
                        setOnClickListener {
                            isEnabled = false
                            lifecycleScope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        val api = SupabaseApi(this@MainActivity)
                                        val fresh = api.ensureFreshSession(currentSession ?: session)
                                        api.saveWeeklyReview(fresh, summaryId, comments.text.toString().trim())
                                        sessionStore.save(fresh)
                                        currentSession = fresh
                                    }
                                    Toast.makeText(this@MainActivity, "Weekly review saved.", Toast.LENGTH_LONG).show()
                                    showSupervisorReviews()
                                } catch (e: Exception) {
                                    isEnabled = true
                                    Toast.makeText(this@MainActivity, e.message ?: "Could not save weekly review.", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }, matchWrap())
                    queue.addView(card, matchWrap())
                }
            } catch (e: Exception) {
                message.text = e.message ?: "Could not load assigned reviews. Check the organisation and supervisor assignments."
            }
        }
    }

    private fun addDataTable(parent: LinearLayout, headers: List<String>, rows: List<List<String>>) {
        val horizontal = HorizontalScrollView(this).apply { isFillViewport = true }
        val table = TableLayout(this).apply { isStretchAllColumns = false }
        val head = TableRow(this).apply { setBackgroundColor(navy) }
        headers.forEach { value ->
            head.addView(tableCell(value, true), TableRow.LayoutParams(dp(160), -2))
        }
        table.addView(head)
        if (rows.isEmpty()) {
            val empty = TableRow(this)
            empty.addView(tableCell("No data available yet.", false), TableRow.LayoutParams(dp(250), -2))
            table.addView(empty)
        } else rows.forEachIndexed { index, values ->
            val row = TableRow(this).apply { setBackgroundColor(if (index % 2 == 0) Color.WHITE else Color.rgb(247, 249, 253)) }
            headers.indices.forEach { column ->
                row.addView(tableCell(values.getOrElse(column) { "" }, false), TableRow.LayoutParams(dp(160), -2))
            }
            table.addView(row)
            table.addView(View(this).apply { setBackgroundColor(Color.rgb(225, 230, 238)) },
                TableLayout.LayoutParams(-1, dp(1)))
        }
        horizontal.addView(table)
        parent.addView(horizontal, matchWrap())
    }

    private fun showStudentPerformance() {
        activeScreen = "performance"
        screenHost.removeAllViews()
        addPageHeader("Performance and rankings", "Track your attendance, awarded marks and position in your course.")
        val session = currentSession
        val profile = currentProfile
        if (session == null || profile == null) {
            screenHost.addView(label("Please sign in to view your performance.", 14f, ink))
            return
        }
        val organizationId = profile.optString("organization_id").trim().takeUnless {
            it.isBlank() || it.equals("null", ignoreCase = true) || it.equals("undefined", ignoreCase = true)
        }
        val course = profile.optString("course").trim().takeUnless {
            it.isBlank() || it.equals("null", ignoreCase = true) || it.equals("undefined", ignoreCase = true)
        }
        if (organizationId == null || course == null) {
            screenHost.addView(label(
                "Your account is not linked to a valid organisation and course yet. Complete your profile registration or ask your organisation administrator to fix the link, then reopen Performance and rankings.",
                14f, ink
            ))
            return
        }
        val rankingCard = panel()
        rankingCard.addView(label("COURSE PERFORMANCE RANKING", 12f, blue, true))
        rankingCard.addView(label("Organisation course: " + course, 13f, ink).apply { setPadding(0, dp(4), 0, dp(8)) })
        rankingCard.addView(label("Loading marks and attendance…", 13f, ink))
        screenHost.addView(rankingCard, matchWrap())
        lifecycleScope.launch {
            try {
                val data = withContext(Dispatchers.IO) {
                    val api = SupabaseApi(this@MainActivity)
                    val fresh = api.ensureFreshSession(session)
                    val students = api.fetchCourseStudents(fresh, organizationId, course)
                    val studentIds = ArrayList<String>()
                    for (i in 0 until students.length()) {
                        studentIds.add(students.getJSONObject(i).optString("id"))
                    }
                    val marks = api.fetchOrganizationMarks(fresh, studentIds)
                    val attendance = api.fetchAttendanceForStudents(fresh, listOf(session.userId))
                    Triple(students, marks, attendance)
                }
                rankingCard.removeAllViews()
                rankingCard.addView(label("COURSE PERFORMANCE RANKING", 12f, blue, true))
                val students = data.first
                val marks = data.second
                val attendance = data.third
                if (students.length() == 0) {
                    rankingCard.addView(label("No other student records are available in this course yet.", 14f, ink))
                } else {
                    val scores = HashMap<String, MutableList<Double>>()
                    for (i in 0 until marks.length()) {
                        val mark = marks.getJSONObject(i)
                        val id = mark.optString("student_id")
                        val score = mark.optDouble("score", Double.NaN)
                        if (!score.isNaN()) scores.getOrPut(id) { mutableListOf() }.add(score)
                    }
                    val sorted = ArrayList<JSONObject>()
                    for (i in 0 until students.length()) sorted.add(students.getJSONObject(i))
                    sorted.sortByDescending { person ->
                        val values = scores[person.optString("id")].orEmpty()
                        if (values.isEmpty()) -1.0 else values.average()
                    }
                    val rows = sorted.mapIndexed { index, person ->
                        val id = person.optString("id")
                        val values = scores[id].orEmpty()
                        val avg = if (values.isEmpty()) "No marks" else String.format(Locale.US, "%.1f", values.average())
                        listOf((index + 1).toString(), person.optString("full_name") + if (id == session.userId) " (You)" else "", values.size.toString(), avg)
                    }
                    addDataTable(rankingCard, listOf("Rank", "Student", "Marks", "Average /100"), rows)
                }
                val attendanceCard = panel()
                attendanceCard.addView(label("MY ATTENDANCE", 12f, blue, true))
                val attendanceRows = ArrayList<List<String>>()
                for (i in 0 until attendance.length()) {
                    val item = attendance.getJSONObject(i)
                    attendanceRows.add(listOf(item.optString("attendance_date"), item.optString("status"), item.optString("remarks")))
                }
                addDataTable(attendanceCard, listOf("Date", "Status", "Remarks"), attendanceRows)
                screenHost.addView(attendanceCard, matchWrap())
            } catch (e: Exception) {
                rankingCard.removeAllViews()
                rankingCard.addView(label("Could not load course performance: " + (e.message ?: "Check your connection and account setup."), 13f, Color.rgb(170, 55, 40)))
            }
        }
    }

    private fun showOrganizationWorkspace() {
        activeScreen = "organization"
        screenHost.removeAllViews()
        addPageHeader("Organisation dashboard", "Manage members, review reports, assign supervisors and award marks.")
        val session = currentSession
        val organizationId = currentProfile?.optString("organization_id").orEmpty()
        if (session == null || organizationId.isBlank()) {
            screenHost.addView(label("This account is not linked to an organisation.", 14f, Color.rgb(170, 55, 40)))
            return
        }
        val keyCard = panel()
        keyCard.addView(label("ORGANISATION KEY", 12f, blue, true))
        val savedKey = getSharedPreferences("elogbook_org_keys", MODE_PRIVATE).getString(organizationId, null)
        keyCard.addView(label(if (savedKey.isNullOrBlank()) "If your original key is lost, generate a new one. Old keys stop working." else "Save this key and share it only with verified organisation members.", 13f, ink).apply { setPadding(0, dp(5), 0, dp(8)) })
        if (!savedKey.isNullOrBlank()) keyCard.addView(label(savedKey, 19f, navy, true))
        keyCard.addView(actionButton("Generate a new organisation key", blue).apply {
            setOnClickListener {
                isEnabled = false
                lifecycleScope.launch {
                    try {
                        val result = withContext(Dispatchers.IO) { SupabaseApi(this@MainActivity).rotateOrganizationKey(currentSession ?: session) }
                            ?: throw java.io.IOException("No key returned.")
                        val key = result.optString("organization_key")
                        getSharedPreferences("elogbook_org_keys", MODE_PRIVATE).edit().putString(organizationId, key).apply()
                        AlertDialog.Builder(this@MainActivity).setTitle("New organisation key")
                            .setMessage("Save this key and share it only with verified members. Previous key no longer works:\n\n" + key)
                            .setPositiveButton("Saved", null).show()
                        showOrganizationWorkspace()
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, e.message ?: "Could not rotate key.", Toast.LENGTH_LONG).show()
                        isEnabled = true
                    }
                }
            }
        }, matchWrap())
        screenHost.addView(keyCard, matchWrap())

        val membersCard = panel()
        membersCard.addView(label("ORGANISATION MEMBERS", 12f, blue, true))
        screenHost.addView(membersCard, matchWrap())
        lifecycleScope.launch {
            try {
                val memberData = withContext(Dispatchers.IO) { SupabaseApi(this@MainActivity).fetchOrganizationMembers(session, organizationId) }
                val students = ArrayList<JSONObject>()
                val supervisors = ArrayList<JSONObject>()
                val supervisorRows = ArrayList<List<String>>()
                val studentRows = ArrayList<List<String>>()
                for (i in 0 until memberData.length()) {
                    val p = memberData.getJSONObject(i)
                    when (p.optString("role")) {
                        "student" -> {
                            students.add(p)
                            studentRows.add(listOf(p.optString("full_name"), p.optString("student_number"), p.optString("course"), p.optString("gender"), p.optString("approval_status")))
                        }
                        "field_supervisor", "institution_supervisor" -> {
                            supervisors.add(p)
                            supervisorRows.add(listOf(p.optString("full_name"), p.optString("role").replace("_", " "), p.optString("course"), p.optString("approval_status")))
                        }
                    }
                }
                membersCard.addView(label("Registered supervisors", 17f, navy, true).apply { setPadding(0, dp(12), 0, dp(5)) })
                addDataTable(membersCard, listOf("Name", "Role", "Course", "Approval"), supervisorRows)
                supervisors.filter { it.optString("approval_status") == "pending" }.forEach { supervisor ->
                    val row = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
                    row.addView(label(supervisor.optString("full_name"), 13f, ink).apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
                    row.addView(actionButton("Approve", blue).apply {
                        setOnClickListener { reviewSupervisor(supervisor.optString("id"), true) }
                    })
                    row.addView(actionButton("Reject", navy).apply {
                        setOnClickListener { reviewSupervisor(supervisor.optString("id"), false) }
                    })
                    membersCard.addView(row, matchWrap())
                }
                membersCard.addView(label("Registered students", 17f, navy, true).apply { setPadding(0, dp(16), 0, dp(5)) })
                addDataTable(membersCard, listOf("Student", "Index number", "Course", "Gender", "Status"), studentRows)

                val assignCard = panel()
                assignCard.addView(label("ASSIGN A SUPERVISOR", 12f, blue, true))
                val studentSpinner = Spinner(this@MainActivity)
                studentSpinner.adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                    students.map { it.optString("full_name") + " • " + it.optString("student_number") })
                assignCard.addView(label("Student", 13f, ink, true))
                assignCard.addView(studentSpinner, matchWrap())
                val approved = supervisors.filter { it.optString("approval_status") == "approved" }
                val supervisorSpinner = Spinner(this@MainActivity)
                supervisorSpinner.adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                    approved.map { it.optString("full_name") + " • " + it.optString("role").replace("_", " ") })
                assignCard.addView(label("Approved supervisor", 13f, ink, true))
                assignCard.addView(supervisorSpinner, matchWrap())
                val typeSpinner = Spinner(this@MainActivity)
                typeSpinner.adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                    listOf("Field supervisor", "Institution supervisor"))
                assignCard.addView(label("Assignment type", 13f, ink, true))
                assignCard.addView(typeSpinner, matchWrap())
                assignCard.addView(actionButton("Assign supervisor", blue).apply {
                    setOnClickListener {
                        val studentId = students.getOrNull(studentSpinner.selectedItemPosition)?.optString("id").orEmpty()
                        val supervisor = approved.getOrNull(supervisorSpinner.selectedItemPosition)
                        if (studentId.isBlank() || supervisor == null) {
                            Toast.makeText(this@MainActivity, "Choose a student and an approved supervisor.", Toast.LENGTH_LONG).show()
                            return@setOnClickListener
                        }
                        val type = if (typeSpinner.selectedItemPosition == 0) "field_supervisor" else "institution_supervisor"
                        lifecycleScope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    SupabaseApi(this@MainActivity).assignOrganizationSupervisor(currentSession ?: session, studentId, supervisor.optString("id"), type)
                                }
                                Toast.makeText(this@MainActivity, "Supervisor assigned.", Toast.LENGTH_LONG).show()
                            } catch (e: Exception) {
                                Toast.makeText(this@MainActivity, e.message ?: "Assignment failed.", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }, matchWrap())
                screenHost.addView(assignCard, matchWrap())

                val reportsCard = panel()
                reportsCard.addView(label("STUDENT DAILY REPORTS", 12f, blue, true))
                val ids = students.map { it.optString("id") }
                val (daily, weekly, attendance, marks) = withContext(Dispatchers.IO) {
                    val api = SupabaseApi(this@MainActivity)
                    QuadReports(
                        api.fetchReportsForStudents(session, ids, false),
                        api.fetchReportsForStudents(session, ids, true),
                        api.fetchAttendanceForStudents(session, ids),
                        api.fetchOrganizationMarks(session, ids)
                    )
                }
                val studentNames = students.associate { it.optString("id") to it.optString("full_name") }
                val dailyRows = ArrayList<List<String>>()
                for (i in 0 until daily.length()) {
                    val r = daily.getJSONObject(i)
                    dailyRows.add(listOf(studentNames[r.optString("student_id")].orEmpty(), r.optString("entry_date"), r.optString("work_done"), r.optString("tools_used"), r.optString("skills_learned"), r.optString("supervisor_comments")))
                }
                addDataTable(reportsCard, listOf("Student", "Date", "Work done", "Tools", "Skills", "Supervisor feedback"), dailyRows)
                reportsCard.addView(label("WEEKLY SUMMARIES", 12f, blue, true).apply { setPadding(0, dp(16), 0, dp(5)) })
                val weeklyRows = ArrayList<List<String>>()
                for (i in 0 until weekly.length()) {
                    val r = weekly.getJSONObject(i)
                    weeklyRows.add(listOf(studentNames[r.optString("student_id")].orEmpty(), r.optString("week_start"), r.optString("summary"), r.optString("skills_learned"), r.optString("supervisor_comments"), if (r.isNull("supervisor_signed_at")) "Pending" else "Reviewed"))
                }
                addDataTable(reportsCard, listOf("Student", "Week starting", "Summary", "Skills", "Feedback", "Review"), weeklyRows)
                reportsCard.addView(label("ATTENDANCE", 12f, blue, true).apply { setPadding(0, dp(16), 0, dp(5)) })
                val attendanceRows = ArrayList<List<String>>()
                for (i in 0 until attendance.length()) {
                    val r = attendance.getJSONObject(i)
                    attendanceRows.add(listOf(studentNames[r.optString("student_id")].orEmpty(), r.optString("attendance_date"), r.optString("status"), r.optString("remarks")))
                }
                addDataTable(reportsCard, listOf("Student", "Date", "Status", "Remarks"), attendanceRows)
                reportsCard.addView(label("PERFORMANCE MARKS", 12f, blue, true).apply { setPadding(0, dp(16), 0, dp(5)) })
                val marksRows = ArrayList<List<String>>()
                for (i in 0 until marks.length()) {
                    val r = marks.getJSONObject(i)
                    marksRows.add(listOf(studentNames[r.optString("student_id")].orEmpty(), r.optString("score"), r.optString("category"), r.optString("feedback"), r.optString("created_at").take(10)))
                }
                addDataTable(reportsCard, listOf("Student", "Score /100", "Category", "Feedback", "Date"), marksRows)
                screenHost.addView(reportsCard, matchWrap())

                val markCard = panel()
                markCard.addView(label("AWARD STUDENT MARKS", 12f, blue, true))
                val markStudent = Spinner(this@MainActivity)
                markStudent.adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, students.map { it.optString("full_name") })
                markCard.addView(markStudent, matchWrap())
                val score = addField(markCard, "Score (0–100)", "", false)
                val category = addField(markCard, "Category", "General performance", false)
                val feedback = addField(markCard, "Feedback", "", true)
                markCard.addView(actionButton("Save performance marks", blue).apply {
                    setOnClickListener {
                        val person = students.getOrNull(markStudent.selectedItemPosition)
                        val scoreValue = score.text.toString().toDoubleOrNull()
                        if (person == null || scoreValue == null || scoreValue < 0 || scoreValue > 100) {
                            Toast.makeText(this@MainActivity, "Choose a student and enter a score from 0 to 100.", Toast.LENGTH_LONG).show()
                            return@setOnClickListener
                        }
                        lifecycleScope.launch {
                            try {
                                withContext(Dispatchers.IO) { SupabaseApi(this@MainActivity).awardMark(currentSession ?: session, person.optString("id"), scoreValue, category.text.toString().ifBlank { "General performance" }, feedback.text.toString()) }
                                Toast.makeText(this@MainActivity, "Performance marks awarded.", Toast.LENGTH_LONG).show()
                                showOrganizationWorkspace()
                            } catch (e: Exception) {
                                Toast.makeText(this@MainActivity, e.message ?: "Could not award marks.", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }, matchWrap())
                screenHost.addView(markCard, matchWrap())
            } catch (e: Exception) {
                membersCard.addView(label(e.message ?: "Could not load organisation data.", 13f, Color.rgb(170, 55, 40)))
            }
        }
    }

    private data class QuadReports(
        val daily: JSONArray,
        val weekly: JSONArray,
        val attendance: JSONArray,
        val marks: JSONArray
    )

    private fun reviewSupervisor(profileId: String, approve: Boolean) {
        val session = currentSession ?: return
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { SupabaseApi(this@MainActivity).reviewOrganizationSupervisor(session, profileId, approve) }
                Toast.makeText(this@MainActivity, if (approve) "Supervisor approved." else "Supervisor rejected.", Toast.LENGTH_LONG).show()
                showOrganizationWorkspace()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, e.message ?: "Could not update supervisor status.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showSupervisorTools() {
        activeScreen = "supervisorTools"
        screenHost.removeAllViews()
        addPageHeader("Attendance and marks", "Record attendance and reward performance for assigned students.")
        val session = currentSession
        if (session == null) return
        val container = panel()
        container.addView(label("Loading assigned students…", 13f, ink))
        screenHost.addView(container, matchWrap())
        lifecycleScope.launch {
            try {
                val students = withContext(Dispatchers.IO) {
                    SupabaseApi(this@MainActivity).fetchAssignedStudentProfiles(session, currentRole)
                }
                container.removeAllViews()
                if (students.length() == 0) {
                    container.addView(label("No students are assigned to you yet. Ask your organisation to make an assignment.", 14f, ink))
                    return@launch
                }
                val studentIds = ArrayList<String>()
                val studentNames = ArrayList<String>()
                for (i in 0 until students.length()) {
                    val s = students.getJSONObject(i)
                    studentIds.add(s.optString("id"))
                    studentNames.add(s.optString("full_name") + " • " + s.optString("course"))
                }
                val choice = Spinner(this@MainActivity)
                choice.adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, studentNames)
                container.addView(label("Student", 13f, ink, true))
                container.addView(choice, matchWrap())
                val day = addField(container, "Attendance date (YYYY-MM-DD)", SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()), false)
                val attendanceState = Spinner(this@MainActivity)
                attendanceState.adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Present", "Late", "Absent", "Excused"))
                container.addView(label("Attendance status", 13f, ink, true))
                container.addView(attendanceState, matchWrap())
                val remarks = addField(container, "Attendance remarks", "", true)
                container.addView(actionButton("Save attendance", blue).apply {
                    setOnClickListener {
                        val id = studentIds.getOrNull(choice.selectedItemPosition).orEmpty()
                        val date = day.text.toString().trim()
                        if (id.isBlank() || !date.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}"))) {
                            Toast.makeText(this@MainActivity, "Choose a student and valid date.", Toast.LENGTH_LONG).show()
                            return@setOnClickListener
                        }
                        val state = listOf("present", "late", "absent", "excused")[attendanceState.selectedItemPosition]
                        lifecycleScope.launch {
                            try {
                                withContext(Dispatchers.IO) { SupabaseApi(this@MainActivity).saveAttendance(currentSession ?: session, id, date, state, remarks.text.toString()) }
                                Toast.makeText(this@MainActivity, "Attendance saved.", Toast.LENGTH_LONG).show()
                            } catch (e: Exception) {
                                Toast.makeText(this@MainActivity, e.message ?: "Could not save attendance.", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }, matchWrap())
                container.addView(label("Performance marks", 16f, navy, true).apply { setPadding(0, dp(18), 0, dp(5)) })
                val score = addField(container, "Score (0–100)", "", false)
                val category = addField(container, "Category", "General performance", false)
                val feedback = addField(container, "Feedback", "", true)
                container.addView(actionButton("Award marks", blue).apply {
                    setOnClickListener {
                        val id = studentIds.getOrNull(choice.selectedItemPosition).orEmpty()
                        val n = score.text.toString().toDoubleOrNull()
                        if (id.isBlank() || n == null || n < 0 || n > 100) {
                            Toast.makeText(this@MainActivity, "Choose a student and score from 0 to 100.", Toast.LENGTH_LONG).show()
                            return@setOnClickListener
                        }
                        lifecycleScope.launch {
                            try {
                                withContext(Dispatchers.IO) { SupabaseApi(this@MainActivity).awardMark(currentSession ?: session, id, n, category.text.toString().ifBlank { "General performance" }, feedback.text.toString()) }
                                Toast.makeText(this@MainActivity, "Marks saved.", Toast.LENGTH_LONG).show()
                            } catch (e: Exception) {
                                Toast.makeText(this@MainActivity, e.message ?: "Could not save marks.", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }, matchWrap())
            } catch (e: Exception) {
                container.removeAllViews()
                container.addView(label(e.message ?: "Could not load assigned students.", 13f, Color.rgb(170, 55, 40)))
            }
        }
    }

    private fun addPageHeader(title: String, subtitle: String) {
        screenHost.addView(actionButton("‹ Dashboard", navy).apply {
            setOnClickListener { navigateTo("Returning to dashboard…") { showHome() } }
        })
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
                    db.weeklies().claimLocalSummaries(session.userId)
                }
                currentProfile = withContext(Dispatchers.IO) { SupabaseApi(this@MainActivity).fetchProfile(session) }
                currentRole = currentProfile?.optString("role")?.ifBlank { "unregistered" } ?: "unregistered"
                val needsOrganization = currentRole == "student" &&
                    currentProfile?.optString("organization_id").isNullOrBlank()
                if (currentRole == "unregistered" || needsOrganization) {
                    showAuth()
                    registrationFields.visibility = View.VISIBLE
                    registrationAction.text = "Complete registration"
                    signInButton.visibility = View.GONE
                    forgotPasswordButton.visibility = View.GONE
                    switchToLoginButton.visibility = View.VISIBLE
                    if (needsOrganization) {
                        registrationRole.setSelection(0)
                        registrationName.setText(currentProfile?.optString("full_name").orEmpty())
                        registrationCourse.setText(currentProfile?.optString("course").orEmpty())
                        registrationStudentNumber.setText(currentProfile?.optString("student_number").orEmpty())
                        registrationGender.setSelection(when (currentProfile?.optString("gender")) {
                            "female" -> 1
                            "male" -> 2
                            "prefer_not_to_say" -> 3
                            else -> 0
                        })
                        authMessage.text = "This account is not linked to an organisation yet. Complete the details below to connect it."
                    } else {
                        authMessage.text = "Sign-in succeeded. Complete your organisation and account details below."
                    }
                    authMessage.setTextColor(green)
                    loadOrganizations()
                    updateRegistrationVisibility()
                } else {
                    showHome()
                    observeEntries(session.userId)
                    observeWeekly(session.userId)
                    SyncScheduler.syncNow(this@MainActivity)
                }
            } catch (e: Exception) {
                authMessage.text = e.message ?: "Sign-in failed. Check your connection and account details."
                authMessage.setTextColor(Color.rgb(170, 55, 40))
            }
        }
    }

    private fun requestPasswordReset() {
        val email = emailField.text.toString().trim()
        if (email.isBlank()) {
            authMessage.text = "Enter your email address first, then tap Forgot password."
            authMessage.setTextColor(Color.rgb(170, 55, 40))
            return
        }
        authMessage.text = "Requesting password reset…"
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { SupabaseApi(this@MainActivity).resetPassword(email) }
                authMessage.text = "If the account exists, a password reset email has been sent. Open the link to finish resetting your password."
                authMessage.setTextColor(green)
            } catch (e: Exception) {
                authMessage.text = e.message ?: "Could not request password reset."
                authMessage.setTextColor(Color.rgb(170, 55, 40))
            }
        }
    }

    private fun signUp() {
        val fullName = registrationName.text.toString().trim()
        val role = when (registrationRole.selectedItemPosition) {
            1 -> "field_supervisor"
            2 -> "institution_supervisor"
            3 -> "organization_admin"
            else -> "student"
        }
        val gender = when (registrationGender.selectedItemPosition) {
            1 -> "female"
            2 -> "male"
            3 -> "prefer_not_to_say"
            else -> ""
        }
        val course = registrationCourse.text.toString().trim()
        val studentNumber = registrationStudentNumber.text.toString().trim()
        val organizationName = registrationOrganizationName.text.toString().trim()
        val organizationKey = registrationOrganizationKey.text.toString().trim()
        val organizationId = if (registrationOrganization.selectedItemPosition in 0 until organizations.length()) {
            organizations.optJSONObject(registrationOrganization.selectedItemPosition)?.optString("id").orEmpty()
        } else ""
        if (fullName.isBlank() || gender.isBlank()) {
            authMessage.text = "Enter your full names and choose a gender."
            return
        }
        if (role == "student" && (course.isBlank() || studentNumber.isBlank())) {
            authMessage.text = "Students must enter their course and student/index number."
            return
        }
        if ((role == "field_supervisor" || role == "institution_supervisor") && course.isBlank()) {
            authMessage.text = "Enter the course or subject you supervise."
            return
        }
        if (role == "organization_admin" && organizationName.isBlank()) {
            authMessage.text = "Enter your school or institution name."
            return
        }
        if (role != "organization_admin" && organizationId.isBlank()) {
            authMessage.text = "Choose your organisation."
            return
        }
        if ((role == "field_supervisor" || role == "institution_supervisor") && organizationKey.isBlank()) {
            authMessage.text = "Enter the organisation key provided by your organisation."
            return
        }
        val email = emailField.text.toString().trim()
        val password = passwordField.text.toString()
        if (currentSession == null && (email.isBlank() || password.length < 6)) {
            authMessage.text = "Enter a valid email and password with at least 6 characters."
            return
        }
        authMessage.text = "Submitting registration…"
        lifecycleScope.launch {
            try {
                if (currentSession == null) {
                    val metadata = JSONObject().put("full_name", fullName).put("role", role).put("gender", gender)
                        .put("course", course).put("student_number", studentNumber)
                        .put("organization_name", organizationName).put("organization_id", organizationId)
                    val response = withContext(Dispatchers.IO) {
                        SupabaseApi(this@MainActivity).signUp(email, password, metadata)
                    }
                    val user = response.optJSONObject("user")
                    val access = response.optString("access_token")
                    val refresh = response.optString("refresh_token")
                    if (user != null && access.isNotBlank() && refresh.isNotBlank()) {
                        val expiry = response.optLong("expires_at", System.currentTimeMillis() / 1000 + response.optLong("expires_in", 3600))
                        currentSession = AuthSession(user.getString("id"), access, refresh,
                            java.util.concurrent.TimeUnit.SECONDS.toMillis(expiry), email)
                        sessionStore.save(currentSession!!)
                    } else {
                        showAuth()
                        emailField.setText(email)
                        authMessage.text = "Account created. Confirm your email, then sign in to finish registration."
                        authMessage.setTextColor(green)
                        return@launch
                    }
                }
                val session = currentSession ?: throw java.io.IOException("Sign in before completing registration.")
                withContext(Dispatchers.IO) {
                    val api = SupabaseApi(this@MainActivity)
                    val fresh = api.ensureFreshSession(session)
                    if (role == "organization_admin") {
                        val result = api.createOrganization(fresh, fullName, organizationName, gender)
                            ?: throw java.io.IOException("The organisation service did not return a key.")
                        getSharedPreferences("elogbook_org_keys", MODE_PRIVATE).edit()
                            .putString(result.optString("organization_id"), result.optString("organization_key")).apply()
                        currentSession = fresh
                        sessionStore.save(fresh)
                        getSharedPreferences("elogbook_org_keys", MODE_PRIVATE).edit()
                            .putString("new_key", result.optString("organization_key")).apply()
                    } else {
                        api.registerMember(fresh, fullName, role, gender, course, studentNumber, organizationId, organizationKey)
                        currentSession = fresh
                        sessionStore.save(fresh)
                    }
                }
                val registeredEmail = currentSession?.email?.ifBlank { email } ?: email
                currentSession = null
                sessionStore.clear()
                currentProfile = null
                currentRole = "student"
                showAuth()
                emailField.setText(registeredEmail)
                authMessage.text = when {
                    role == "organization_admin" -> "Organisation registered. Sign in to open your workspace and retrieve the generated organisation key."
                    role == "student" -> "Student registration completed successfully. You can now sign in."
                    else -> "Supervisor registration submitted. Sign in after registration; your organisation must approve your account."
                }
                authMessage.setTextColor(green)
            } catch (e: Exception) {
                authMessage.text = e.message ?: "Could not complete registration."
                authMessage.setTextColor(Color.rgb(170, 55, 40))
            }
        }
    }

    private fun signOut() {
        sessionStore.clear()
        currentSession = null
        currentProfile = null
        currentRole = "student"
        observeEntries("local")
        observeWeekly("local")
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
        val tools = toolsField.text.toString().trim()
        val skills = skillsField.text.toString().trim()
        val challenges = challengesField.text.toString().trim()
        val recommendations = recommendationsField.text.toString().trim()
        val ownerId = currentSession?.userId ?: "local"
        lifecycleScope.launch {
            val existing = db.entries().findByOwnerAndDate(ownerId, date)
            val changed = existing == null || existing.workDone != work || existing.toolsUsed != tools ||
                existing.skillsLearned != skills || existing.challenges != challenges ||
                existing.recommendations != recommendations
            val record = if (existing == null) {
                LogbookEntry(
                    entryDate = date, workDone = work, toolsUsed = tools,
                    skillsLearned = skills, challenges = challenges,
                    recommendations = recommendations, ownerUserId = ownerId
                )
            } else existing.copy(
                workDone = work, toolsUsed = tools, skillsLearned = skills,
                challenges = challenges, recommendations = recommendations,
                syncStatus = if (changed) "pending" else existing.syncStatus,
                supervisorComments = if (changed) "" else existing.supervisorComments,
                supervisorReviewedAt = if (changed) null else existing.supervisorReviewedAt,
                updatedAt = if (changed) System.currentTimeMillis() else existing.updatedAt
            )
            db.entries().save(record)
            if (currentSession != null && changed) SyncScheduler.syncNow(this@MainActivity)
            Toast.makeText(this@MainActivity, when {
                !changed -> "No changes detected; the saved record and review were preserved."
                currentSession != null -> "Daily record saved. Sync queued."
                else -> "Daily record saved offline."
            }, Toast.LENGTH_LONG).show()
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
            field.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus && ::scrollContainer.isInitialized) {
                    field.post {
                        val visibleRect = android.graphics.Rect(0, 0, field.width, field.height)
                        field.requestRectangleOnScreen(visibleRect, true)
                        scrollContainer.postDelayed({
                            if (field.hasFocus()) field.requestRectangleOnScreen(visibleRect, true)
                        }, 220L)
                    }
                }
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

    private fun mondayDate(): String {
        val calendar = java.util.Calendar.getInstance()
        val day = calendar.get(java.util.Calendar.DAY_OF_WEEK)
        val daysSinceMonday = (day + 5) % 7
        calendar.add(java.util.Calendar.DAY_OF_MONTH, -daysSinceMonday)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
    }

    private fun matchWrap() = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
