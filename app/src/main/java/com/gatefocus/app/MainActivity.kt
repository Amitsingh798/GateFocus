package com.gatefocus.app

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityManager
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class MainActivity : Activity() {
    companion object {
        const val PREFS = "gate_focus_prefs"
        const val KEY_STUDY_MODE = "study_mode"
        const val KEY_BLOCKED = "blocked_packages"
        const val KEY_VIDEOS = "curated_videos"
        const val KEY_TELEGRAM_FILTER = "telegram_channel_filter"
        const val KEY_TELEGRAM_ALLOWED = "telegram_allowed_channels"
        const val KEY_BLOCK_YOUTUBE_SHORTS = "block_youtube_shorts"
        const val KEY_BLOCK_SHORT_VIDEOS_60S = "block_short_videos_60s"
        val defaultBlockedApps = setOf(
            "com.instagram.android", "com.instagram.barcelona", "com.threads.android",
            "com.facebook.katana", "com.snapchat.android"
        )
    }

    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }
    private lateinit var status: TextView
    private var secondsLeft = 0
    private var timerRunning = false
    private val handler by lazy { android.os.Handler(mainLooper) }
    private var stopwatchStart = 0L
    private var stopwatchRunning = false
    private var stopwatchElapsed = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            render()
        } catch (e: Exception) {
            val fallback = TextView(this).apply {
                text = "Failed to load GATE FOCUS. Please restart the app.\n\nError: ${e.localizedMessage}"
                setPadding(40, 80, 40, 40)
            }
            setContentView(fallback)
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            if (::status.isInitialized) {
                status.text = blockerStatus()
            }
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        timerRunning = false
        stopwatchRunning = false
        handler.removeCallbacksAndMessages(null)
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 24)
            setBackgroundColor(0xFFF4F6FC.toInt())
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        content.addView(TextView(this).apply {
            text = "GATE FOCUS"
            textSize = 30f
            setTextColor(0xFF14213D.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        content.addView(TextView(this).apply {
            text = "Computer Science Engineering · Study system"
            textSize = 14f
            setTextColor(0xFF58647A.toInt())
            setPadding(0, 4, 0, 20)
        })

        val study = Button(this).apply {
            text = if (prefs.getBoolean(KEY_STUDY_MODE, false)) "Turn study mode OFF" else "Start strict study mode"
            setOnClickListener {
                val active = !prefs.getBoolean(KEY_STUDY_MODE, false)
                prefs.edit().putBoolean(KEY_STUDY_MODE, active).apply()
                if (active && !isAccessibilityEnabled()) {
                    try {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    } catch (_: Exception) {}
                }
                render()
            }
        }
        content.addView(study)

        status = TextView(this).apply {
            text = blockerStatus()
            textSize = 13f
            setTextColor(0xFF46536A.toInt())
            setPadding(0, 8, 0, 16)
        }
        content.addView(status)

        content.addView(section("FOCUS TIMER"))
        val timerText = TextView(this).apply {
            text = "45:00"
            textSize = 34f
            setTextColor(0xFF14213D.toInt())
            gravity = android.view.Gravity.CENTER
            setPadding(0, 8, 0, 8)
        }
        content.addView(timerText)

        val timerButtons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(25, 45, 60).forEach { mins ->
            timerButtons.addView(
                Button(this).apply {
                    text = "${mins}m"
                    setOnClickListener {
                        secondsLeft = mins * 60
                        timerRunning = false
                        timerText.text = format(secondsLeft)
                    }
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
        }
        content.addView(timerButtons)
        content.addView(Button(this).apply {
            text = "Start / Pause timer"
            setOnClickListener {
                timerRunning = !timerRunning
                if (timerRunning) tickTimer(timerText)
            }
        })

        content.addView(section("STOPWATCH"))
        val watchText = TextView(this).apply {
            text = "00:00:00"
            textSize = 28f
            gravity = android.view.Gravity.CENTER
            setTextColor(0xFF14213D.toInt())
        }
        content.addView(watchText)
        val watchRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        watchRow.addView(
            Button(this).apply {
                text = "Start / Pause"
                setOnClickListener {
                    if (stopwatchRunning) {
                        stopwatchElapsed += System.currentTimeMillis() - stopwatchStart
                        stopwatchRunning = false
                    } else {
                        stopwatchStart = System.currentTimeMillis()
                        stopwatchRunning = true
                        tickWatch(watchText)
                    }
                }
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        watchRow.addView(
            Button(this).apply {
                text = "Reset"
                setOnClickListener {
                    stopwatchRunning = false
                    stopwatchElapsed = 0
                    watchText.text = "00:00:00"
                }
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        content.addView(watchRow)

        content.addView(section("YOUTUBE STUDY FOCUS"))
        content.addView(TextView(this).apply {
            text = "Keep YouTube open for lectures & study videos, but instantly kick out when Shorts or short distraction clips are clicked."
            textSize = 13f
            setTextColor(0xFF58647A.toInt())
            setPadding(0, 0, 0, 8)
        })
        content.addView(CheckBox(this).apply {
            text = "Block YouTube Shorts (Kick out to study screen)"
            isChecked = prefs.getBoolean(KEY_BLOCK_YOUTUBE_SHORTS, true)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(KEY_BLOCK_YOUTUBE_SHORTS, checked).apply()
            }
        })
        content.addView(CheckBox(this).apply {
            text = "Block videos ≤ 60 seconds (Kick out if duration ≤ 1:00)"
            isChecked = prefs.getBoolean(KEY_BLOCK_SHORT_VIDEOS_60S, true)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(KEY_BLOCK_SHORT_VIDEOS_60S, checked).apply()
            }
        })

        content.addView(section("DISTRACTION BLOCKLIST (ENTIRE APPS)"))
        content.addView(TextView(this).apply {
            text = "Entire apps blocked only while study mode is ON. (YouTube is kept allowed for your studies)."
            textSize = 13f
            setTextColor(0xFF58647A.toInt())
            setPadding(0, 0, 0, 8)
        })

        val selected = getSavedBlockedApps().toMutableSet()
        selected.remove("com.google.android.youtube")
        prefs.edit().putStringSet(KEY_BLOCKED, selected).apply()

        val labels = linkedMapOf(
            "Instagram" to "com.instagram.android",
            "Threads" to "com.threads.android",
            "Facebook" to "com.facebook.katana",
            "Snapchat" to "com.snapchat.android"
        )
        labels.forEach { (label, pkg) ->
            content.addView(CheckBox(this).apply {
                text = label
                isChecked = pkg in selected
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selected.add(pkg) else selected.remove(pkg)
                    try {
                        prefs.edit().putStringSet(KEY_BLOCKED, selected).apply()
                    } catch (_: Exception) {}
                }
            })
        }

        content.addView(section("TELEGRAM CHANNEL ALLOWLIST"))
        content.addView(TextView(this).apply {
            text = "During strict study mode, keep notifications from your approved GATE channels and chats. Other Telegram notifications are dismissed. The app also makes a best-effort attempt to leave non-allowed channel screens."
            textSize = 13f
            setTextColor(0xFF58647A.toInt())
            setPadding(0, 0, 0, 8)
        })
        content.addView(CheckBox(this).apply {
            text = "Filter Telegram during strict study mode"
            isChecked = prefs.getBoolean(KEY_TELEGRAM_FILTER, true)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(KEY_TELEGRAM_FILTER, checked).apply()
            }
        })
        val allowedTelegramInput = EditText(this).apply {
            hint = "Allowed channel/chat names — one per line"
            minLines = 3
            maxLines = 6
            setSingleLine(false)
            setText(getSavedAllowedTelegram().joinToString("\n"))
        }
        content.addView(allowedTelegramInput)
        content.addView(Button(this).apply {
            text = "Save Telegram allowlist"
            setOnClickListener {
                val names = allowedTelegramInput.text.toString()
                    .lines().map { it.trim() }.filter { it.isNotBlank() }.toSet()
                try {
                    prefs.edit().putStringSet(KEY_TELEGRAM_ALLOWED, names).apply()
                    Toast.makeText(this@MainActivity, "Saved ${names.size} allowed Telegram name(s)", Toast.LENGTH_LONG).show()
                } catch (_: Exception) {}
            }
        })
        content.addView(Button(this).apply {
            text = "Enable Telegram notification access"
            setOnClickListener {
                try {
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                } catch (_: Exception) {
                    Toast.makeText(this@MainActivity, "Open Android Settings > Special app access > Notification access", Toast.LENGTH_LONG).show()
                }
            }
        })
        content.addView(TextView(this).apply {
            text = "Important: add exact visible channel/chat names before starting strict mode. If the allowlist is empty, all Telegram notifications are dismissed while filtering is active. Matching depends on Telegram's notification format."
            textSize = 12f
            setTextColor(0xFF7A5360.toInt())
            setPadding(0, 8, 0, 12)
        })

        content.addView(section("CURATED CSE VIDEO LIBRARY"))
        content.addView(TextView(this).apply {
            text = "Add a YouTube video ID from a GATE lecture. Playback uses an embedded player, not the YouTube home feed. Add verified lecture IDs yourself; no sample videos are fabricated."
            textSize = 13f
            setTextColor(0xFF58647A.toInt())
            setPadding(0, 0, 0, 8)
        })
        val titleInput = EditText(this).apply { hint = "Lecture title (e.g. OS — Process Scheduling)" }
        val idInput = EditText(this).apply { hint = "YouTube video ID (not a Shorts URL)" }
        content.addView(titleInput)
        content.addView(idInput)
        content.addView(Button(this).apply {
            text = "Add to my GATE library"
            setOnClickListener {
                val title = titleInput.text.toString().trim()
                val id = idInput.text.toString().trim()
                if (title.isBlank() || !id.matches(Regex("[A-Za-z0-9_-]{11}"))) {
                    Toast.makeText(this@MainActivity, "Enter a title and valid 11-character YouTube video ID", Toast.LENGTH_LONG).show()
                } else {
                    val arr = readVideos()
                    arr.put(JSONObject().put("title", title).put("id", id))
                    prefs.edit().putString(KEY_VIDEOS, arr.toString()).apply()
                    titleInput.text.clear()
                    idInput.text.clear()
                    render()
                }
            }
        })
        val videos = readVideos()
        for (i in 0 until videos.length()) {
            val item = videos.optJSONObject(i) ?: continue
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            row.addView(
                TextView(this).apply {
                    text = item.optString("title")
                    textSize = 14f
                    setTextColor(0xFF14213D.toInt())
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
            row.addView(Button(this).apply {
                text = "Watch"
                setOnClickListener {
                    startActivity(
                        Intent(this@MainActivity, PlayerActivity::class.java)
                            .putExtra("title", item.optString("title"))
                            .putExtra("video_id", item.optString("id"))
                    )
                }
            })
            row.addView(Button(this).apply {
                text = "×"
                setOnClickListener {
                    val updated = readVideos()
                    updated.remove(i)
                    prefs.edit().putString(KEY_VIDEOS, updated.toString()).apply()
                    render()
                }
            })
            content.addView(row)
        }

        content.addView(section("SETUP"))
        content.addView(Button(this).apply {
            text = "Open accessibility settings"
            setOnClickListener {
                try {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                } catch (_: Exception) {}
            }
        })
        content.addView(TextView(this).apply {
            text = "Important: Android requires you to explicitly enable the accessibility service. Blocking is best-effort and can be bypassed by disabling the service or force-stopping the app. This is a personal productivity tool, not tamper-proof parental control."
            textSize = 12f
            setTextColor(0xFF7A5360.toInt())
            setPadding(0, 12, 0, 20)
        })

        scroll.addView(content)
        root.addView(scroll)
        setContentView(root)
    }

    private fun section(s: String) = TextView(this).apply {
        text = s
        textSize = 12f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        setTextColor(0xFF4568E8.toInt())
        setPadding(0, 22, 0, 8)
    }

    private fun blockerStatus(): String {
        return try {
            if (prefs.getBoolean(KEY_STUDY_MODE, false)) {
                if (isAccessibilityEnabled()) {
                    "Study mode is ON · App blocker & YouTube Shorts blocker active"
                } else {
                    "Study mode is ON · Enable accessibility service to enforce blocking"
                }
            } else {
                "Study mode is OFF · Your blocklist is saved"
            }
        } catch (_: Exception) {
            "Study mode is OFF"
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        return try {
            val expectedComponent = ComponentName(this, AppBlockerService::class.java)
            val expectedShort = expectedComponent.flattenToShortString()
            val expectedLong = expectedComponent.flattenToString()

            // 1. Check via Settings.Secure
            val enabledServices = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            if (!enabledServices.isNullOrEmpty()) {
                val splitter = TextUtils.SimpleStringSplitter(':')
                splitter.setString(enabledServices)
                while (splitter.hasNext()) {
                    val service = splitter.next()
                    if (service.equals(expectedShort, ignoreCase = true) ||
                        service.equals(expectedLong, ignoreCase = true) ||
                        service.contains(packageName) && service.contains(AppBlockerService::class.java.simpleName)
                    ) {
                        return true
                    }
                }
            }

            // 2. Fallback via AccessibilityManager
            val manager = getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            val list = manager?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            if (list != null) {
                for (info in list) {
                    val id = info.id
                    if (id != null && (
                        id.equals(expectedShort, ignoreCase = true) ||
                        id.equals(expectedLong, ignoreCase = true) ||
                        (id.contains(packageName) && id.contains(AppBlockerService::class.java.simpleName))
                    )) {
                        return true
                    }
                    val sInfo = info.resolveInfo?.serviceInfo
                    if (sInfo != null && sInfo.packageName == packageName && sInfo.name == AppBlockerService::class.java.name) {
                        return true
                    }
                }
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun getSavedBlockedApps(): Set<String> {
        return try {
            prefs.getStringSet(KEY_BLOCKED, defaultBlockedApps) ?: defaultBlockedApps
        } catch (_: Exception) {
            defaultBlockedApps
        }
    }

    private fun getSavedAllowedTelegram(): Set<String> {
        return try {
            prefs.getStringSet(KEY_TELEGRAM_ALLOWED, emptySet()) ?: emptySet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun readVideos(): JSONArray = try {
        JSONArray(prefs.getString(KEY_VIDEOS, "[]"))
    } catch (_: Exception) {
        JSONArray()
    }

    private fun format(s: Int) = String.format(Locale.US, "%02d:%02d", s / 60, s % 60)

    private fun tickTimer(view: TextView) {
        handler.postDelayed({
            if (timerRunning) {
                if (secondsLeft > 0) secondsLeft--
                view.text = format(secondsLeft)
                if (secondsLeft == 0) {
                    timerRunning = false
                    Toast.makeText(this, "Focus session complete! Take a short break.", Toast.LENGTH_LONG).show()
                } else {
                    tickTimer(view)
                }
            }
        }, 1000)
    }

    private fun tickWatch(view: TextView) {
        handler.postDelayed({
            if (stopwatchRunning) {
                val ms = stopwatchElapsed + System.currentTimeMillis() - stopwatchStart
                val sec = (ms / 1000).toInt()
                view.text = String.format(Locale.US, "%02d:%02d:%02d", sec / 3600, (sec / 60) % 60, sec % 60)
                tickWatch(view)
            }
        }, 1000)
    }
}
