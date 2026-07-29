package com.quotacheck.app.core.notifications

import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class NotificationChannelTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val manager = context.getSystemService(NotificationManager::class.java)

    @After fun removeChannels() {
        manager.deleteNotificationChannel(NotificationChannels.QUOTA_ALERTS)
        manager.deleteNotificationChannel(NotificationChannels.SYNC_STATUS)
    }

    @Test fun createsStableBadgeFreeChannelsIdempotently() {
        NotificationChannels.create(context)
        NotificationChannels.create(context)

        val quota = requireNotNull(manager.getNotificationChannel(NotificationChannels.QUOTA_ALERTS))
        val sync = requireNotNull(manager.getNotificationChannel(NotificationChannels.SYNC_STATUS))
        assertEquals(NotificationChannels.QUOTA_ALERTS, quota.id)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, quota.importance)
        assertFalse(quota.canShowBadge())
        assertEquals(NotificationChannels.SYNC_STATUS, sync.id)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, sync.importance)
        assertFalse(sync.canShowBadge())
    }

    @Test fun quotaNotificationsArePrivateWithGenericPublicVersion() {
        val notification = AndroidNotificationPublisher(context).notificationFor(
            AlertCommand.Critical("pool", 1L, 10),
        )

        assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility)
        assertNotNull(notification.publicVersion)
        assertEquals("QuotaCheck notification", notification.publicVersion.extras.getString(Notification.EXTRA_TITLE))
    }
}
