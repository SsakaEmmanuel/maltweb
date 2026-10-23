package org.maltweb.elogbook

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.ViewGroup
import android.widget.*
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
 private lateinit var db: LogbookDatabase
 private lateinit var entryList: LinearLayout
 private lateinit var status: TextView
 private lateinit var dateField: EditText
 private lateinit var workField: EditText
 private lateinit var toolsField: EditText
 private lateinit var skillsField: EditText
 private lateinit var challengesField: EditText
 private lateinit var recommendationsField: EditText

 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  db = LogbookDatabase.get(this)
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
   text = "Daily attachment record"
   textSize = 27f
   setTextColor(Color.rgb(16, 34, 61))
   setPadding(0, 4, 0, 12)
  })
  status = TextView(this).apply {
   text = "Offline-ready • Entries are saved on this device"
   textSize = 13f
   setTextColor(Color.rgb(35, 91, 70))
  }
  content.addView(status)
  dateField = addField(content, "Date (YYYY-MM-DD)", SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
  workField = addField(content, "Work done today", "", true)
  toolsField = addField(content, "Tools / equipment used", "")
  skillsField = addField(content, "Skills or lessons learned", "", true)
  challengesField = addField(content, "Challenges encountered", "", true)
  recommendationsField = addField(content, "Recommendations", "", true)
  content.addView(Button(this).apply {
   text = "Save on this device"
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
  lifecycleScope.launch {
   db.entries().observeAll().collect { entries ->
    entryList.removeAllViews()
    entries.forEach { entry ->
     val card = TextView(this@MainActivity).apply {
      text = entry.entryDate + " • " + entry.syncStatus.uppercase() + "\n" + entry.workDone
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
    status.text = if (pending > 0) "Offline-ready • " + pending + " record(s) awaiting online sync"
                 else "Offline-ready • Entries are saved on this device"
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
  lifecycleScope.launch {
   db.entries().save(LogbookEntry(
    entryDate = date,
    workDone = work,
    toolsUsed = toolsField.text.toString().trim(),
    skillsLearned = skillsField.text.toString().trim(),
    challenges = challengesField.text.toString().trim(),
    recommendations = recommendationsField.text.toString().trim()
   ))
   workField.text.clear()
   toolsField.text.clear()
   skillsField.text.clear()
   challengesField.text.clear()
   recommendationsField.text.clear()
   Toast.makeText(this@MainActivity, "Saved on this device. Online sync needs setup.", Toast.LENGTH_LONG).show()
  }
 }
}
