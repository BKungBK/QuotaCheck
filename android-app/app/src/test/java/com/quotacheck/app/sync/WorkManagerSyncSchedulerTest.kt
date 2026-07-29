package com.quotacheck.app.sync

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkRequest
import com.quotacheck.app.core.model.UserPreferences
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class WorkManagerSyncSchedulerTest {
    @Test fun `periodic work uses connected network update policy and selected interval`() {
        val gateway = RecordingWorkManagerGateway()
        val scheduler = WorkManagerSyncScheduler(gateway)

        scheduler.schedulePeriodic(UserPreferences(syncIntervalMinutes = 60, wifiOnly = false))

        assertEquals(WorkManagerSyncScheduler.PERIODIC_WORK_NAME, gateway.periodicName)
        assertEquals(ExistingPeriodicWorkPolicy.UPDATE, gateway.periodicPolicy)
        val request = requireNotNull(gateway.periodicRequest)
        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
        assertEquals(TimeUnit.MINUTES.toMillis(60), request.workSpec.intervalDuration)
        assertEquals(BackoffPolicy.EXPONENTIAL, request.workSpec.backoffPolicy)
        assertEquals(TimeUnit.SECONDS.toMillis(30), request.workSpec.backoffDelayDuration)
    }

    @Test fun `periodic work uses unmetered network when wifi only is enabled`() {
        val gateway = RecordingWorkManagerGateway()

        WorkManagerSyncScheduler(gateway).schedulePeriodic(UserPreferences(syncIntervalMinutes = 30, wifiOnly = true))

        assertEquals(NetworkType.UNMETERED, requireNotNull(gateway.periodicRequest).workSpec.constraints.requiredNetworkType)
    }

    @Test fun `periodic work supports every allowed interval`() {
        listOf(30, 60, 120, 240).forEach { interval ->
            val gateway = RecordingWorkManagerGateway()
            WorkManagerSyncScheduler(gateway).schedulePeriodic(UserPreferences(syncIntervalMinutes = interval))

            assertEquals(TimeUnit.MINUTES.toMillis(interval.toLong()), requireNotNull(gateway.periodicRequest).workSpec.intervalDuration)
        }
    }

    @Test fun `cancel periodic uses its unique work name`() {
        val gateway = RecordingWorkManagerGateway()

        WorkManagerSyncScheduler(gateway).cancelPeriodic()

        assertEquals(WorkManagerSyncScheduler.PERIODIC_WORK_NAME, gateway.cancelledName)
    }

    @Test fun `manual refresh is unique keep work with connected network and exponential backoff`() {
        val gateway = RecordingWorkManagerGateway()

        WorkManagerSyncScheduler(gateway).refreshNow()

        assertEquals(WorkManagerSyncScheduler.MANUAL_WORK_NAME, gateway.manualName)
        assertEquals(ExistingWorkPolicy.KEEP, gateway.manualPolicy)
        val request = requireNotNull(gateway.manualRequest)
        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
        assertEquals(BackoffPolicy.EXPONENTIAL, request.workSpec.backoffPolicy)
        assertEquals(TimeUnit.SECONDS.toMillis(30), request.workSpec.backoffDelayDuration)
    }

    private class RecordingWorkManagerGateway : WorkManagerGateway {
        var periodicName: String? = null
        var periodicPolicy: ExistingPeriodicWorkPolicy? = null
        var periodicRequest: PeriodicWorkRequest? = null
        var cancelledName: String? = null
        var manualName: String? = null
        var manualPolicy: ExistingWorkPolicy? = null
        var manualRequest: OneTimeWorkRequest? = null

        override fun enqueueUniquePeriodicWork(name: String, policy: ExistingPeriodicWorkPolicy, request: PeriodicWorkRequest) {
            periodicName = name
            periodicPolicy = policy
            periodicRequest = request
        }

        override fun cancelUniqueWork(name: String) {
            cancelledName = name
        }

        override fun enqueueUniqueWork(name: String, policy: ExistingWorkPolicy, request: OneTimeWorkRequest) {
            manualName = name
            manualPolicy = policy
            manualRequest = request
        }
    }
}
