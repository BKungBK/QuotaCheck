# Tauri and Project ProGuard Rules
-keep class com.antigravity.quota.widget.** { *; }
-keepclassmembers class com.antigravity.quota.widget.** { *; }
-keep class app.tauri.** { *; }
-keepclassmembers class app.tauri.** { *; }
-dontwarn app.tauri.**
-dontwarn com.antigravity.quota.widget.**