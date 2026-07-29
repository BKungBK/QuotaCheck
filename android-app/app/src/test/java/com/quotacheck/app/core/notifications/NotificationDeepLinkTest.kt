package com.quotacheck.app.core.notifications

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationDeepLinkTest {
    @Test fun quotaAlertsOpenAlertsAndSyncStatusOpensHome() {
        assertEquals(NotificationDeepLink.ROUTE_ALERTS, NotificationDeepLink.routeFor(AlertCommand.Low("pool", 1L, 20)))
        assertEquals(NotificationDeepLink.ROUTE_ALERTS, NotificationDeepLink.routeFor(AlertCommand.Critical("pool", 1L, 10)))
        assertEquals(NotificationDeepLink.ROUTE_ALERTS, NotificationDeepLink.routeFor(AlertCommand.Reset("pool", 1L)))
        assertEquals(NotificationDeepLink.ROUTE_HOME, NotificationDeepLink.routeFor(AlertCommand.SyncFailure(3, 1L)))
        assertEquals(NotificationDeepLink.ROUTE_HOME, NotificationDeepLink.routeFor(AlertCommand.SyncSuccess(0, 1L)))
    }
}
