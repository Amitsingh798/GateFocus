package com.gatefocus.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

class AppBlockerService : AccessibilityService() {
    private var lastBlockedPackage: String? = null
    private var lastBlockedTelegramTitle: String? = null
    private var lastBlockedShortsTime = 0L

    companion object {
        const val YOUTUBE_PACKAGE = "com.google.android.youtube"
    }

    private val telegramPackages = setOf(
        "org.telegram.messenger",
        "org.telegram.messenger.web",
        "org.thunderdog.challegram"
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        try {
            val pkg = event.packageName?.toString() ?: return
            if (pkg == packageName) return

            val prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
            if (!prefs.getBoolean(MainActivity.KEY_STUDY_MODE, false)) return

            // 1. YouTube Shorts & <=60s videos blocker
            if (pkg == YOUTUBE_PACKAGE) {
                lastBlockedPackage = null
                if (prefs.getBoolean(MainActivity.KEY_BLOCK_YOUTUBE_SHORTS, true)) {
                    guardYouTubeShorts(event, prefs)
                }
                return
            }

            // 2. Telegram filter
            if (pkg in telegramPackages && prefs.getBoolean(MainActivity.KEY_TELEGRAM_FILTER, true)) {
                guardTelegramChannel(prefs)
                return
            }

            // 3. General full-app blocking for other apps
            val blocked = prefs.getStringSet(MainActivity.KEY_BLOCKED, MainActivity.defaultBlockedApps)
                ?: MainActivity.defaultBlockedApps
            if (pkg in blocked && lastBlockedPackage != pkg) {
                lastBlockedPackage = pkg
                kickOutToBlockScreen(pkg)
            } else if (pkg !in blocked) {
                lastBlockedPackage = null
            }
        } catch (_: Exception) {}
    }

    /**
     * Checks if the user clicked on ANY Short or video <= 60 seconds,
     * or entered the full-screen Shorts player.
     * When detected, kicks the user completely out of YouTube to the GateFocus block screen.
     */
    private fun guardYouTubeShorts(event: AccessibilityEvent, prefs: android.content.SharedPreferences) {
        val now = System.currentTimeMillis()
        if (now - lastBlockedShortsTime < 500) return // Throttling

        val blockShortVideos = prefs.getBoolean(MainActivity.KEY_BLOCK_SHORT_VIDEOS_60S, true)

        try {
            // Check 1: Event text / description from accessibility event
            val eventDesc = event.contentDescription?.toString()?.lowercase(Locale.ROOT).orEmpty()
            val eventText = event.text.joinToString(" ").lowercase(Locale.ROOT)
            if (eventDesc.contains("play short") || eventText.contains("play short")) {
                exitShorts(now, "YouTube Shorts is blocked during study mode")
                return
            }

            // If user clicked any view in YouTube
            if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                val source = event.source
                if (source != null) {
                    // Check if it's a Short
                    if (hasShortsIndicator(source)) {
                        exitShorts(now, "YouTube Shorts is blocked during study mode")
                        return
                    }

                    // Check if clicked video duration is <= 60 seconds
                    if (blockShortVideos && isVideoDuration60sOrLess(source)) {
                        exitShorts(now, "Short videos (≤ 60 seconds) are blocked during study mode")
                        return
                    }
                }
            }

            // Check 2: Check all active interactive windows for the full-screen Shorts reel player or <=60s playback
            val windows = windows
            var isShortsActive = false

            fun scanNode(node: AccessibilityNodeInfo?) {
                if (node == null || isShortsActive) return

                val resId = node.viewIdResourceName?.lowercase(Locale.ROOT).orEmpty()
                val desc = node.contentDescription?.toString()?.lowercase(Locale.ROOT).orEmpty()

                // YouTube reel player identifiers (active Shorts player)
                if (resId.contains(":id/reel_") ||
                    resId.contains("shorts_player") ||
                    resId.contains("shorts_container")
                ) {
                    isShortsActive = true
                    return
                }

                // Overlay buttons unique to Shorts playback
                if (desc.contains("create a short with this sound") ||
                    desc.contains("remix this video") ||
                    desc.contains("pause short") ||
                    desc.contains("play short video") ||
                    (desc == "shorts" && (node.isSelected || node.isFocused))
                ) {
                    isShortsActive = true
                    return
                }

                for (i in 0 until node.childCount) {
                    scanNode(node.getChild(i))
                    if (isShortsActive) return
                }
            }

            if (!windows.isNullOrEmpty()) {
                for (w in windows) {
                    scanNode(w.root)
                    if (isShortsActive) break
                }
            } else {
                scanNode(rootInActiveWindow)
            }

            if (isShortsActive) {
                exitShorts(now, "YouTube Shorts is blocked during study mode")
            }
        } catch (_: Exception) {}
    }

    /**
     * Checks if a clicked view or its hierarchy contains a duration badge of <= 60 seconds (1 minute).
     * YouTube badges timestamps like:
     * - "0:45", "0:59", "1:00" in text badges or overlays
     * - "45 seconds", "1 minute" in accessibility descriptions
     */
    private fun isVideoDuration60sOrLess(node: AccessibilityNodeInfo): Boolean {
        try {
            val texts = mutableListOf<String>()

            fun collectText(n: AccessibilityNodeInfo?, depth: Int) {
                if (n == null || depth > 3) return
                val t = n.text?.toString()?.trim().orEmpty()
                if (t.isNotBlank()) texts.add(t)
                val d = n.contentDescription?.toString()?.trim().orEmpty()
                if (d.isNotBlank()) texts.add(d)

                for (i in 0 until n.childCount) {
                    collectText(n.getChild(i), depth + 1)
                }
            }

            // Check node and its parent container (video card)
            collectText(node, 0)
            node.parent?.let { collectText(it, 0) }

            for (raw in texts) {
                val lower = raw.lowercase(Locale.ROOT)

                // Match explicit description like "45 seconds", "50 seconds"
                val secMatch = Regex("""\b(\d{1,2})\s*seconds?\b""").find(lower)
                if (secMatch != null) {
                    val sec = secMatch.groupValues[1].toIntOrNull() ?: 999
                    if (sec in 1..60) return true
                }

                // Match exact "1 minute" with 0 seconds
                if (lower.contains("1 minute") && !lower.contains("minutes") && !lower.contains("hour")) {
                    val secInDesc = Regex("""\b(\d{1,2})\s*seconds?\b""").find(lower)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    if (secInDesc == 0) return true // Exactly 1:00
                }

                // Match timestamp badge pattern like "0:30", "0:55", "1:00" (M:SS)
                val timeMatch = Regex("""\b(\d{1,2}):(\d{2})\b""").find(raw)
                if (timeMatch != null) {
                    val mins = timeMatch.groupValues[1].toIntOrNull() ?: 999
                    val secs = timeMatch.groupValues[2].toIntOrNull() ?: 999
                    val totalSec = mins * 60 + secs
                    if (totalSec in 1..60) return true
                }
            }
        } catch (_: Exception) {}
        return false
    }

    private fun hasShortsIndicator(node: AccessibilityNodeInfo): Boolean {
        val resId = node.viewIdResourceName?.lowercase(Locale.ROOT).orEmpty()
        val desc = node.contentDescription?.toString()?.lowercase(Locale.ROOT).orEmpty()
        val text = node.text?.toString()?.lowercase(Locale.ROOT).orEmpty()

        if (desc.contains("play short") ||
            desc.contains("short") ||
            text.contains("play short") ||
            resId.contains("reel") || resId.contains("shorts") ||
            desc == "shorts" || text == "shorts"
        ) {
            return true
        }

        // Check immediate children
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val cDesc = child.contentDescription?.toString()?.lowercase(Locale.ROOT).orEmpty()
            val cText = child.text?.toString()?.lowercase(Locale.ROOT).orEmpty()
            val cRes = child.viewIdResourceName?.lowercase(Locale.ROOT).orEmpty()
            if (cDesc.contains("play short") || cDesc.contains("short") ||
                cText.contains("play short") || cRes.contains("reel") || cRes.contains("shorts")
            ) {
                return true
            }
        }

        // Check parent
        val parent = node.parent
        if (parent != null) {
            val pDesc = parent.contentDescription?.toString()?.lowercase(Locale.ROOT).orEmpty()
            val pResId = parent.viewIdResourceName?.lowercase(Locale.ROOT).orEmpty()
            if (pDesc.contains("play short") || pResId.contains("reel") || pResId.contains("shorts")) {
                return true
            }
        }
        return false
    }

    private fun exitShorts(now: Long, reason: String) {
        lastBlockedShortsTime = now
        kickOutToBlockScreen(reason)
    }

    private fun kickOutToBlockScreen(reason: String) {
        // 1. Kick out to Home immediately
        performGlobalAction(GLOBAL_ACTION_HOME)
        // 2. Open GateFocus BlockScreenActivity
        val intent = Intent(this, BlockScreenActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("blocked_app", reason)
        }
        startActivity(intent)
    }

    private fun guardTelegramChannel(prefs: android.content.SharedPreferences) {
        try {
            val root = rootInActiveWindow ?: return
            val allowed = prefs.getStringSet(MainActivity.KEY_TELEGRAM_ALLOWED, emptySet())
                ?.map { normalize(it) }?.filter { it.isNotBlank() } ?: emptyList()

            val topLimit = (resources.displayMetrics.heightPixels * 0.30f).toInt()
            val visibleTexts = mutableListOf<Pair<Int, String>>()

            fun collect(node: AccessibilityNodeInfo?) {
                if (node == null) return
                try {
                    val value = node.text?.toString()?.trim().orEmpty()
                    if (value.isNotBlank() && node.isVisibleToUser) {
                        val bounds = Rect()
                        node.getBoundsInScreen(bounds)
                        if (bounds.top >= 0 && bounds.top <= topLimit) visibleTexts.add(bounds.top to value)
                    }
                    for (i in 0 until node.childCount) collect(node.getChild(i))
                } catch (_: Exception) {}
            }

            collect(root)
            val header = visibleTexts.sortedBy { it.first }.firstOrNull()?.second.orEmpty()
            if (header.isBlank()) return

            val normalizedHeader = normalize(header)
            val navigationLabels = setOf("telegram", "chats", "contacts", "calls", "settings", "archived chats")
            if (normalizedHeader in navigationLabels) {
                lastBlockedTelegramTitle = null
                return
            }

            val isAllowed = allowed.any { name ->
                normalizedHeader == name || normalizedHeader.contains(name) || name.contains(normalizedHeader)
            }
            if (isAllowed) {
                lastBlockedTelegramTitle = null
                return
            }

            if (lastBlockedTelegramTitle != header) {
                lastBlockedTelegramTitle = header
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        } catch (_: Exception) {}
    }

    private fun normalize(value: String): String =
        value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

    override fun onInterrupt() = Unit
}
