package com.antigravity.quota.widget

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import app.tauri.annotation.Command
import app.tauri.annotation.InvokeArg
import app.tauri.annotation.TauriPlugin
import app.tauri.plugin.JSObject
import app.tauri.plugin.Plugin
import app.tauri.plugin.Invoke

@InvokeArg
class SaveTokenArgs {
    var token: String = ""
}

@InvokeArg
class SaveConfigArgs {
    var configJson: String = ""
}

@TauriPlugin
class QuotaPlugin(private val activity: Activity) : Plugin(activity) {

    companion object {
        fun getSecurePreferences(context: Context): SharedPreferences {
            return try {
                val masterKey = MasterKey.Builder(context.applicationContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

                EncryptedSharedPreferences.create(
                    context.applicationContext,
                    "quotacheck_secure_prefs",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (_: Throwable) {
                try {
                    context.applicationContext.getSharedPreferences("quotacheck_prefs", Context.MODE_PRIVATE)
                } catch (_: Throwable) {
                    context.getSharedPreferences("quotacheck_prefs", Context.MODE_PRIVATE)
                }
            }
        }
    }

    private val sharedPreferences: SharedPreferences by lazy {
        getSecurePreferences(activity.applicationContext)
    }

    @Command
    fun saveRefreshToken(invoke: Invoke) {
        val args = invoke.parseArgs(SaveTokenArgs::class.java)
        sharedPreferences.edit().putString("refresh_token", args.token).apply()

        // Schedule periodic sync and trigger immediate sync when token is saved
        QuotaSyncWorker.schedulePeriodicSync(activity.applicationContext)
        QuotaSyncWorker.triggerImmediateSync(activity.applicationContext)

        val ret = JSObject()
        ret.put("success", true)
        invoke.resolve(ret)
    }

    @Command
    fun saveConfig(invoke: Invoke) {
        val args = invoke.parseArgs(SaveConfigArgs::class.java)
        sharedPreferences.edit().putString("config_json", args.configJson).apply()
        val ret = JSObject()
        ret.put("success", true)
        invoke.resolve(ret)
    }

    @Command
    fun getQuotaCache(invoke: Invoke) {
        val cache = sharedPreferences.getString("quota_cache", "") ?: ""
        val ret = JSObject()
        ret.put("cache", cache)
        invoke.resolve(ret)
    }

    @Command
    fun triggerManualSync(invoke: Invoke) {
        QuotaSyncWorker.triggerImmediateSync(activity.applicationContext)
        val ret = JSObject()
        ret.put("success", true)
        invoke.resolve(ret)
    }

    @Command
    fun checkNotificationPermission(invoke: Invoke) {
        val hasPostNotif = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(activity, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val granted = NotificationManagerCompat.from(activity).areNotificationsEnabled() && hasPostNotif
        val ret = JSObject()
        ret.put("granted", granted)
        invoke.resolve(ret)
    }

    @Command
    fun requestNotificationPermission(invoke: Invoke) {
        activity.runOnUiThread {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(activity, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(
                        activity,
                        arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                        1001
                    )
                }
            }
        }
        val hasPostNotif = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(activity, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val granted = NotificationManagerCompat.from(activity).areNotificationsEnabled() && hasPostNotif
        val ret = JSObject()
        ret.put("granted", granted)
        invoke.resolve(ret)
    }

    @Command
    fun openNotificationSettings(invoke: Invoke) {
        val intent = Intent().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
            } else {
                action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                data = Uri.fromParts("package", activity.packageName, null)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        activity.startActivity(intent)
        val ret = JSObject()
        ret.put("success", true)
        invoke.resolve(ret)
    }

    @Command
    fun triggerTestNotification(invoke: Invoke) {
        QuotaNotificationManager.sendTestNotification(activity.applicationContext)
        val ret = JSObject()
        ret.put("success", true)
        invoke.resolve(ret)
    }
}
