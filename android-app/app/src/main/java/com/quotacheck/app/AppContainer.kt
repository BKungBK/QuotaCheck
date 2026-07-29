package com.quotacheck.app

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.quotacheck.app.core.database.AlertDao
import com.quotacheck.app.core.database.HistoryDao
import com.quotacheck.app.core.database.QuotaDao
import com.quotacheck.app.core.database.QuotaDatabase
import com.quotacheck.app.core.database.SyncDao
import com.quotacheck.app.core.preferences.DataStoreUserPreferencesRepository
import com.quotacheck.app.core.preferences.UserPreferencesRepository

/**
 * Application-scoped dependency root. Feature dependencies are added here as
 * explicit lazy properties in later tasks; no generated dependency framework
 * is used in this project.
 */
class AppContainer(context: Context) {
    private val applicationContext = context.applicationContext

    val quotaDatabase: QuotaDatabase by lazy {
        Room.databaseBuilder(applicationContext, QuotaDatabase::class.java, "quota-check.db").build()
    }
    val quotaDao: QuotaDao by lazy { quotaDatabase.quotaDao() }
    val historyDao: HistoryDao by lazy { quotaDatabase.historyDao() }
    val syncDao: SyncDao by lazy { quotaDatabase.syncDao() }
    val alertDao: AlertDao by lazy { quotaDatabase.alertDao() }

    val userPreferencesRepository: UserPreferencesRepository by lazy {
        DataStoreUserPreferencesRepository(
            PreferenceDataStoreFactory.create(
                produceFile = { applicationContext.preferencesDataStoreFile("user_preferences") },
            ),
        )
    }
}
