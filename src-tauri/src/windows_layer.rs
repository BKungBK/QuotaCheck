use tauri::WebviewWindow;
use windows::core::BOOL;
use windows::Win32::Foundation::{HWND, LPARAM, WPARAM, RECT};
use windows::Win32::UI::WindowsAndMessaging::{
    FindWindowW, FindWindowExW, SendMessageTimeoutW, EnumWindows, GetClassNameW,
    SetParent, SetWindowLongPtrW, GetWindowLongPtrW, GWL_STYLE, GWL_EXSTYLE,
    WS_CHILD, WS_VISIBLE, WS_CLIPSIBLINGS, WS_CLIPCHILDREN,
    WS_EX_TRANSPARENT, WS_EX_LAYERED, SMTO_NORMAL, GetWindow, GW_HWNDNEXT,
    SetWindowPos, SWP_NOZORDER, SWP_FRAMECHANGED,
};
use windows::Win32::Graphics::Gdi::{
    MonitorFromWindow, GetMonitorInfoW, MONITORINFO, MONITOR_DEFAULTTOPRIMARY,
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

        // 4. Force pure borderless child window styles with no decorations
        let new_style = WS_CHILD.0 | WS_VISIBLE.0 | WS_CLIPSIBLINGS.0 | WS_CLIPCHILDREN.0;
        let _ = SetWindowLongPtrW(
            tauri_hwnd,
            GWL_STYLE,
            new_style as isize,
        );

        // 5. Make the window Click-through and Layered (transparent/overlay support)
        let ex_style = GetWindowLongPtrW(tauri_hwnd, GWL_EXSTYLE);
        let _ = SetWindowLongPtrW(
            tauri_hwnd,
            GWL_EXSTYLE,
            ex_style | (WS_EX_TRANSPARENT.0 | WS_EX_LAYERED.0) as isize,
        );

        // 6. Calculate position based on Config and GDI Work Area (ignoring taskbar)
        let config = crate::config::load_config();
        
        let mut monitor_info = MONITORINFO {
            cbSize: std::mem::size_of::<MONITORINFO>() as u32,
            rcMonitor: RECT::default(),
            rcWork: RECT::default(),
            dwFlags: 0,
        };

        let hmonitor = MonitorFromWindow(tauri_hwnd, MONITOR_DEFAULTTOPRIMARY);
        let _ = GetMonitorInfoW(hmonitor, &mut monitor_info);

        let work_area = monitor_info.rcWork;
        let work_w = work_area.right - work_area.left;
        let work_h = work_area.bottom - work_area.top;

        let widget_w = 220;
        let widget_h = 220;

        let mut x = work_area.left;
        let mut y = work_area.top;

        match config.position_corner.as_str() {
            "top-left" => {
                x += config.offset_x;
                y += config.offset_y;
            }
            "top-right" => {
                x += work_w - widget_w - config.offset_x;
                y += config.offset_y;
            }
            "bottom-left" => {
                x += config.offset_x;
                y += work_h - widget_h - config.offset_y;
            }
            _ => { // "bottom-right"
                x += work_w - widget_w - config.offset_x;
                y += work_h - widget_h - config.offset_y;
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
