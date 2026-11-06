package com.gatefocus.app

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.Locale

class TelegramNotificationFilterService : NotificationListenerService() {
    private val telegramPackages = setOf(
        "org.telegram.messenger",
        "org.telegram.messenger.web",
        "org.thunderdog.challegram"
    )

    override fun onListenerConnected() {
        super.onListenerConnected()
        try {
            activeNotifications?.forEach(::filterIfNeeded)
        } catch (_: Exception) {}
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        try {
            filterIfNeeded(sbn)
        } catch (_: Exception) {}
    }

    private fun filterIfNeeded(sbn: StatusBarNotification) {
        try {
            if (sbn.packageName !in telegramPackages) return

            val prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
            if (!prefs.getBoolean(MainActivity.KEY_STUDY_MODE, false)) return
            if (!prefs.getBoolean(MainActivity.KEY_TELEGRAM_FILTER, true)) return

            val allowed = prefs.getStringSet(MainActivity.KEY_TELEGRAM_ALLOWED, emptySet())
                ?.map(::normalize)?.filter { it.isNotBlank() } ?: emptyList()

            val extras = sbn.notification?.extras ?: return
            val candidates = listOf(
                extras.getCharSequence("android.title")?.toString().orEmpty(),
                extras.getCharSequence("android.conversationTitle")?.toString().orEmpty(),
                extras.getCharSequence("android.title.big")?.toString().orEmpty()
            ).map(::normalize).filter { it.isNotBlank() }

            val isAllowed = allowed.any { name ->
                candidates.any { candidate ->
                    candidate == name || candidate.contains(name) || name.contains(candidate)
                }
            }

            // Fail closed for Telegram notifications while the user has explicitly enabled filtering.
            if (!isAllowed) cancelNotification(sbn.key)
        } catch (_: Exception) {}
    }

    private fun normalize(value: String): String =
        value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
}
