use tauri::{plugin::TauriPlugin, Runtime};

pub fn init<R: Runtime>() -> TauriPlugin<R> {
    tauri::plugin::Builder::new("quota")
        .setup(|_app, _api| {
            #[cfg(target_os = "android")]
            {
                _api.register_android_plugin("com.antigravity.quota.widget", "QuotaPlugin")?;
            }

            Ok(())
        })
        .build()
}
