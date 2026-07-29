package com.quotacheck.app

import android.app.Application

class QuotaCheckApp : Application() {
    val appContainer: AppContainer by lazy { AppContainer() }
}
