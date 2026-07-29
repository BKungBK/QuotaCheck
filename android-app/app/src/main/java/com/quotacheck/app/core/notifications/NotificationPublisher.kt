package com.quotacheck.app.core.notifications

interface NotificationPublisher {
    /** False leaves the durable event pending for a later retry. */
    fun publish(command: AlertCommand): Boolean
}
