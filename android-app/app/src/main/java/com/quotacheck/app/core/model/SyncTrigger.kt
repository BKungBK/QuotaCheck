package com.quotacheck.app.core.model

/** The entry point that requested a quota synchronization. */
enum class SyncTrigger {
    MANUAL,
    PERIODIC,
    ONBOARDING,
    NOTIFICATION,
}
