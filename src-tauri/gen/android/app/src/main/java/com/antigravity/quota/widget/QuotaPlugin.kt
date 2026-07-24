package com.antigravity.quota.widget

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
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
            } catch (e: Exception) {
                context.applicationContext.getSharedPreferences("quotacheck_prefs", Context.MODE_PRIVATE)
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
}
