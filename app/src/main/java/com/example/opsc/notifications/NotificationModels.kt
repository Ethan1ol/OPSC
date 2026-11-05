package com.example.opsc.notifications

/** The three notification channels (users can turn each off in system settings). */
enum class PushChannel(val id: String) {
    FIXTURES("fixtures"),
    REFEREE("referee_requests"),
    LEADERBOARD("leaderboard");

    companion object {
        /** The channel with this id, or FIXTURES if unknown. */
        fun fromId(id: String?): PushChannel = entries.firstOrNull { it.id == id } ?: FIXTURES
    }
}

/** The kinds of event the app tells you about. Only the kind and values are stored, so each phone shows the text in its own language. */
enum class NotificationType(val wire: String, val channel: PushChannel) {
    /** The league started. */
    FIXTURES_SCHEDULED("fixtures_scheduled", PushChannel.FIXTURES),
    /** You were chosen as referee. */
    REFEREE_ASSIGNED("referee_assigned", PushChannel.REFEREE),
    /** A result you played in was confirmed. */
    RESULT_VERIFIED("result_verified", PushChannel.FIXTURES),
    /** A result changed the table. */
    LEADERBOARD_CHANGED("leaderboard_changed", PushChannel.LEADERBOARD);

    companion object {
        /** The type with this name, or null if unknown. */
        fun fromWire(value: String?): NotificationType? = entries.firstOrNull { it.wire == value }
    }
}

/** One notification, stored at users/{uid}/notifications/{id}. */
data class InAppNotification(
    val id: String,
    val type: NotificationType,
    val clubId: String,
    val clubName: String,
    /** Values for the message text, in order. */
    val args: List<String>,
    val read: Boolean,
    /** Time in milliseconds, or 0 while the server time is pending. */
    val createdAt: Long
)

/** Small helpers for the notification list. */
object NotificationLogic {
    fun unreadCount(list: List<InAppNotification>): Int = list.count { !it.read }

    /** Newest first. Items with time 0 count as newest. */
    fun sortNewestFirst(list: List<InAppNotification>): List<InAppNotification> =
        list.sortedByDescending { if (it.createdAt == 0L) Long.MAX_VALUE else it.createdAt }

    /** The text for the unread badge, such as 99+. */
    fun badgeText(unread: Int): String = when {
        unread <= 0 -> ""
        unread > 99 -> "99+"
        else -> unread.toString()
    }
}
