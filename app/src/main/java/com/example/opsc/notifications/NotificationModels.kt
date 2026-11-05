package com.example.opsc.notifications

/** The three Android notification channels (users can switch each one off in system settings). */
enum class PushChannel(val id: String) {
    FIXTURES("fixtures"),
    REFEREE("referee_requests"),
    LEADERBOARD("leaderboard");

    companion object {
        /** The channel with this id, or [FIXTURES] if unknown (for example a message sent from the console). */
        fun fromId(id: String?): PushChannel = entries.firstOrNull { it.id == id } ?: FIXTURES
    }
}

/**
 * The kinds of event the app tells you about. Only the kind and its values are stored, never the
 * text, so each phone shows the message in its own language.
 */
enum class NotificationType(val wire: String, val channel: PushChannel) {
    /** The league started and fixtures were created. */
    FIXTURES_SCHEDULED("fixtures_scheduled", PushChannel.FIXTURES),
    /** You were chosen as referee for some matches. */
    REFEREE_ASSIGNED("referee_assigned", PushChannel.REFEREE),
    /** A result you played in was confirmed. */
    RESULT_VERIFIED("result_verified", PushChannel.FIXTURES),
    /** A result changed the league table. */
    LEADERBOARD_CHANGED("leaderboard_changed", PushChannel.LEADERBOARD);

    companion object {
        /** The type stored under this name, or null if unknown (so a newer app version's types never crash an older one). */
        fun fromWire(value: String?): NotificationType? = entries.firstOrNull { it.wire == value }
    }
}

/** One notification as stored at `users/{uid}/notifications/{id}`. */
data class InAppNotification(
    val id: String,
    val type: NotificationType,
    val clubId: String,
    val clubName: String,
    /** Values the message needs (names, scores, counts), in the order the text expects. */
    val args: List<String>,
    val read: Boolean,
    /** Milliseconds since 1970, or 0 while the server time is still pending. */
    val createdAt: Long
)

/** Small pure helpers for the notification list. */
object NotificationLogic {
    fun unreadCount(list: List<InAppNotification>): Int = list.count { !it.read }

    /** Newest first; items whose time is still pending (0) count as newest. */
    fun sortNewestFirst(list: List<InAppNotification>): List<InAppNotification> =
        list.sortedByDescending { if (it.createdAt == 0L) Long.MAX_VALUE else it.createdAt }

    /** What a "99+" style badge should show. */
    fun badgeText(unread: Int): String = when {
        unread <= 0 -> ""
        unread > 99 -> "99+"
        else -> unread.toString()
    }
}
