package com.example.opsc.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Unit tests for notification types, channels and the list helpers. */
class NotificationLogicTest {
    private fun note(id: String, read: Boolean, at: Long, type: NotificationType = NotificationType.RESULT_VERIFIED) =
        InAppNotification(id, type, "club", "Club", emptyList(), read, at)

    @Test fun everyTypeRoundTripsThroughItsWireName() {
        NotificationType.entries.forEach { assertEquals(it, NotificationType.fromWire(it.wire)) }
    }

    @Test fun unknownTypeIsNullNotACrash() {
        assertNull(NotificationType.fromWire("from_the_future"))
        assertNull(NotificationType.fromWire(null))
    }

    @Test fun typesMapToTheRightChannels() {
        assertEquals(PushChannel.REFEREE, NotificationType.REFEREE_ASSIGNED.channel)
        assertEquals(PushChannel.LEADERBOARD, NotificationType.LEADERBOARD_CHANGED.channel)
        assertEquals(PushChannel.FIXTURES, NotificationType.FIXTURES_SCHEDULED.channel)
        assertEquals(PushChannel.FIXTURES, NotificationType.RESULT_VERIFIED.channel)
    }

    @Test fun unknownChannelFallsBackToFixtures() {
        assertEquals(PushChannel.FIXTURES, PushChannel.fromId("nonsense"))
        assertEquals(PushChannel.FIXTURES, PushChannel.fromId(null))
        assertEquals(PushChannel.REFEREE, PushChannel.fromId("referee_requests"))
    }

    @Test fun unreadCountOnlyCountsUnread() {
        assertEquals(2, NotificationLogic.unreadCount(listOf(note("a", false, 1), note("b", true, 2), note("c", false, 3))))
        assertEquals(0, NotificationLogic.unreadCount(emptyList()))
    }

    @Test fun newestFirstWithPendingTimesOnTop() {
        val sorted = NotificationLogic.sortNewestFirst(listOf(note("old", false, 100), note("new", false, 300), note("pending", false, 0)))
        assertEquals(listOf("pending", "new", "old"), sorted.map { it.id })
    }

    @Test fun badgeText() {
        assertEquals("", NotificationLogic.badgeText(0))
        assertEquals("7", NotificationLogic.badgeText(7))
        assertEquals("99+", NotificationLogic.badgeText(100))
    }
}
