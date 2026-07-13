use tauri::WebviewWindow;
use windows::core::BOOL;
use windows::Win32::Foundation::{HWND, LPARAM, WPARAM};
use windows::Win32::UI::WindowsAndMessaging::{
    FindWindowW, FindWindowExW, SendMessageTimeoutW, EnumWindows, GetClassNameW,
    SetParent, SetWindowLongPtrW, GetWindowLongPtrW, GWL_STYLE, GWL_EXSTYLE,
    WS_CHILD, WS_POPUP, WS_CAPTION, WS_THICKFRAME, WS_SYSMENU,
    WS_EX_TRANSPARENT, WS_EX_LAYERED, SMTO_NORMAL, GetWindow, GW_HWNDNEXT,
    SetWindowPos, SWP_NOZORDER, SWP_FRAMECHANGED,
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
            if let Ok(shell_view) = FindWindowExW(
                Some(hwnd),
                None,
                windows::core::w!("SHELLDLL_DefView"),
                None
            ) {
                if !shell_view.0.is_null() {
                    // This is the WorkerW containing the desktop icons.
                    // The wallpaper WorkerW sits immediately behind it in Z-order.
                    if let Ok(next_window) = GetWindow(hwnd, GW_HWNDNEXT) {
                        let mut next_class = [0u16; 256];
                        let next_len = GetClassNameW(next_window, &mut next_class);
                        if next_len > 0 {
                            let next_name = String::from_utf16_lossy(&next_class[..next_len as usize]);
                            if next_name == "WorkerW" {
                                context.workerw_hwnd = Some(next_window);
                            }
                        }
                    }
                }
            }
        }
    }
    BOOL(1)
}

pub fn setup_wallpaper_widget(window: &WebviewWindow) -> Result<(), String> {
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

        // 4. Strip all decorations (caption, thick frame, popup, sysmenu) and make it a child window
        let style = GetWindowLongPtrW(tauri_hwnd, GWL_STYLE);
        let _ = SetWindowLongPtrW(
            tauri_hwnd,
            GWL_STYLE,
            (style & !(WS_POPUP.0 | WS_CAPTION.0 | WS_THICKFRAME.0 | WS_SYSMENU.0) as isize) | WS_CHILD.0 as isize,
        );

        // 5. Make the window Click-through and Layered (transparent background support)
        let ex_style = GetWindowLongPtrW(tauri_hwnd, GWL_EXSTYLE);
        let _ = SetWindowLongPtrW(
            tauri_hwnd,
            GWL_EXSTYLE,
            ex_style | (WS_EX_TRANSPARENT.0 | WS_EX_LAYERED.0) as isize,
        );

        // 6. Calculate position based on Config
        let config = crate::config::load_config();
        let monitors = window.available_monitors().map_err(|e| e.to_string())?;
        let monitor = monitors.get(config.monitor_index)
            .or_else(|| monitors.first())
            .ok_or_else(|| "No monitors found".to_string())?;

        let pos = monitor.position();
        let size = monitor.size();

        let widget_w = 150;
        let widget_h = 80;

        let mut x = pos.x;
        let mut y = pos.y;

        match config.position_corner.as_str() {
            "top-left" => {
                x += config.offset_x;
                y += config.offset_y;
            }
            "top-right" => {
                x += size.width as i32 - widget_w - config.offset_x;
                y += config.offset_y;
            }
            "bottom-left" => {
                x += config.offset_x;
                y += size.height as i32 - widget_h - config.offset_y;
            }
            _ => { // "bottom-right"
                x += size.width as i32 - widget_w - config.offset_x;
                y += size.height as i32 - widget_h - config.offset_y;
            }
        }

        // 7. Position, resize, and trigger frame changed update
        let _ = SetWindowPos(
            tauri_hwnd,
            None,
            x,
            y,
            widget_w,
            widget_h,
            SWP_NOZORDER | SWP_FRAMECHANGED,
        );

        Ok(())
    }
}
