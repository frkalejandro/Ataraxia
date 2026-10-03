package com.ataraxia.ui.focus

import com.ataraxia.domain.model.FocusTimer
import com.ataraxia.notifications.*
import kotlin.test.*

class FocusNotificationsTest {
    private class Notifications : TimerNotificationScheduler {
        val updates = mutableListOf<TimerNotice?>()
        var saved: FocusTimer? = null
        override fun update(kind: TimerKind, notice: TimerNotice?) { updates += notice }
        override fun checkDue() = Unit
        override fun restoreFocus() = saved
        override fun saveFocus(timer: FocusTimer) { saved = timer }
    }

    @Test fun startPauseResumeResetSynchronizeWithoutReschedulingEveryTick() {
        val notifications = Notifications()
        val controller = FocusController(FocusTimer(), notifications)
        controller.timer = controller.timer.start(1000)
        controller.timer = controller.timer.tick(2000)
        assertEquals(1, notifications.updates.size)
        controller.timer = controller.timer.pause(31000)
        assertNull(notifications.updates.last())
        assertEquals(1470, notifications.saved?.remainingSeconds)
        controller.timer = controller.timer.start(100000)
        assertEquals(1570000L, notifications.updates.last()?.deadlineMillis)
        controller.timer = controller.timer.reset()
        assertNull(notifications.updates.last())
        assertNull(notifications.saved?.deadlineMillis)
        assertEquals(4, notifications.updates.size)
    }

    @Test fun completionCancelsAlarmAndPersistsNextPhaseOnce() {
        val notifications = Notifications()
        val completed = mutableListOf<FocusTimer>()
        val controller = FocusController(FocusTimer(), notifications, completed::add)
        controller.timer = controller.timer.start(0)
        controller.timer = controller.timer.tick(1500000)
        controller.timer = controller.timer.tick(1500100)
        assertEquals(2, notifications.updates.size)
        assertNull(notifications.updates.last())
        assertTrue(notifications.saved!!.awaitingNext)
        assertEquals(1, notifications.saved?.completedSessions)
        assertEquals(1, completed.size)
        assertEquals(1500000L, completed.single().deadlineMillis)
        controller.timer = controller.timer.start(2000000)
        controller.timer = controller.timer.tick(2300000)
        assertEquals(1, completed.size)
    }
}
