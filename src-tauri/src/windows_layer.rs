use tauri::Window;
use windows::core::BOOL;
use windows::Win32::Foundation::{HWND, LPARAM, WPARAM};
use windows::Win32::UI::WindowsAndMessaging::{
    FindWindowW, SendMessageTimeoutW, EnumWindows, GetClassNameW,
    SetParent, SetWindowLongPtrW, GetWindowLongPtrW, GWL_EXSTYLE,
    WS_EX_TRANSPARENT, WS_EX_LAYERED, SMTO_NORMAL,
};

struct EnumContext {
    workerw_hwnd: Option<HWND>,
}

unsafe extern "system" fn enum_windows_callback(hwnd: HWND, lparam: LPARAM) -> BOOL {
    let context = &mut *(lparam.0 as *mut EnumContext);
    let mut class_name = [0u16; 256];
    let len = GetClassNameW(hwnd, &mut class_name);
    if len > 0 {
        let name = String::from_utf16_lossy(&class_name[..len as usize]);
        if name == "WorkerW" {
            // Find the WorkerW that has SHELLDLL_DefView child (under desktop icons)
            if let Ok(shell_view) = FindWindowW(
                windows::core::w!("SHELLDLL_DefView"),
                None
            ) {
                if !shell_view.0.is_null() {
                    context.workerw_hwnd = Some(hwnd);
                }
            }
        }
    }
    BOOL(1)
}

pub fn setup_wallpaper_widget(window: &Window) -> Result<(), String> {
    unsafe {
        let tauri_hwnd = window.hwnd().map_err(|e| e.to_string())?;

        // 1. Force Windows to create a WorkerW window behind desktop icons
        let progman = FindWindowW(windows::core::w!("Progman"), None)
            .map_err(|e| format!("Failed to find Progman window: {}", e))?;

        if progman.0.is_null() {
            return Err("Failed to find Progman window (null handle)".to_string());
        }

        let mut result: usize = 0;
        let _ = SendMessageTimeoutW(
            progman,
            0x052C, // Message to Progman to spawn WorkerW
            WPARAM(0),
            LPARAM(0),
            SMTO_NORMAL,
            1000,
            Some(&mut result),
        );

        // 2. Enumerate windows to find the spawned WorkerW
        let mut context = EnumContext { workerw_hwnd: None };
        let context_ptr = &mut context as *mut EnumContext as isize;
        let _ = EnumWindows(Some(enum_windows_callback), LPARAM(context_ptr));

        let workerw = context.workerw_hwnd.ok_or_else(|| "Failed to find WorkerW window".to_string())?;

        // 3. Reparent the Tauri window to WorkerW
        let _ = SetParent(tauri_hwnd, Some(workerw));

        // 4. Make the window Click-through and Layered (transparent background support)
        let ex_style = GetWindowLongPtrW(tauri_hwnd, GWL_EXSTYLE);
        let _ = SetWindowLongPtrW(
            tauri_hwnd,
            GWL_EXSTYLE,
            ex_style | (WS_EX_TRANSPARENT.0 | WS_EX_LAYERED.0) as isize,
        );

        Ok(())
    }
}
