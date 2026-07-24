package com.antigravity.quota.widget

import android.os.Bundle
import androidx.activity.enableEdgeToEdge

class MainActivity : TauriActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    try {
      pluginManager.register(QuotaPlugin(this))
    } catch (_: Throwable) {
      // Plugin already registered or managed by Tauri
    }
  }
}
