package com.quotacheck.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import com.quotacheck.app.core.designsystem.QuotaCheckTheme
import com.quotacheck.app.feature.AppShell

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            QuotaCheckTheme {
                Surface {
                    AppShell()
                }
            }
        }
    }
}
