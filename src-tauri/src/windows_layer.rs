use tauri::WebviewWindow;
use windows::core::BOOL;
use windows::Win32::Foundation::{HWND, LPARAM, WPARAM, RECT, LRESULT};
use windows::Win32::UI::WindowsAndMessaging::{
    FindWindowW, FindWindowExW, SendMessageTimeoutW, EnumWindows, GetClassNameW,
    SetParent, SetWindowLongPtrW, GetWindowLongPtrW, GWL_STYLE, GWL_EXSTYLE,
    GWLP_WNDPROC, WNDPROC, CallWindowProcW,
    WS_CHILD, WS_VISIBLE, WS_CLIPSIBLINGS, WS_CLIPCHILDREN,
    WS_EX_TRANSPARENT, SMTO_NORMAL, GetWindow, GW_HWNDNEXT,
    SetWindowPos, SWP_NOZORDER, SWP_FRAMECHANGED,
};
use windows::Win32::Graphics::Gdi::{
    MonitorFromWindow, GetMonitorInfoW, EnumDisplayMonitors, HDC, HMONITOR, MONITORINFO, MONITOR_DEFAULTTOPRIMARY,
};
use windows::Win32::UI::HiDpi::GetDpiForWindow;
use std::sync::OnceLock;
use tokio::sync::mpsc;

pub const WM_DISPLAYCHANGE: u32 = 0x007E;
pub const WM_DPICHANGED: u32 = 0x02E0;

static PREV_WNDPROC: std::sync::atomic::AtomicIsize = std::sync::atomic::AtomicIsize::new(0);
static REPOSITION_TX: OnceLock<mpsc::Sender<()>> = OnceLock::new();

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

struct MonitorListContext {
    monitors: Vec<MONITORINFO>,
    primary_idx: usize,
}

unsafe extern "system" fn enum_monitors_callback(
    hmonitor: HMONITOR,
    _hdc: HDC,
    _rect: *mut RECT,
    lparam: LPARAM,
) -> BOOL {
    let context = &mut *(lparam.0 as *mut MonitorListContext);
    let mut info = MONITORINFO {
        cbSize: std::mem::size_of::<MONITORINFO>() as u32,
        rcMonitor: RECT::default(),
        rcWork: RECT::default(),
        dwFlags: 0,
    };
    if GetMonitorInfoW(hmonitor, &mut info).as_bool() {
        if (info.dwFlags & 1) != 0 { // MONITORINFOF_PRIMARY = 1
            context.primary_idx = context.monitors.len();
        }
        context.monitors.push(info);
    }
    BOOL(1)
}

pub fn get_target_monitor_info(target_index: usize) -> MONITORINFO {
    unsafe {
        let mut context = MonitorListContext {
            monitors: Vec::new(),
            primary_idx: 0,
        };
        let context_ptr = &mut context as *mut MonitorListContext as isize;
        let _ = EnumDisplayMonitors(None, None, Some(enum_monitors_callback), LPARAM(context_ptr));

        if context.monitors.is_empty() {
            let mut info = MONITORINFO {
                cbSize: std::mem::size_of::<MONITORINFO>() as u32,
                rcMonitor: RECT::default(),
                rcWork: RECT::default(),
                dwFlags: 0,
            };
            let hmon = MonitorFromWindow(HWND::default(), MONITOR_DEFAULTTOPRIMARY);
            let _ = GetMonitorInfoW(hmon, &mut info);
            return info;
        }

        if target_index < context.monitors.len() {
            context.monitors[target_index]
        } else {
            context.monitors[context.primary_idx]
        }
    }
}

unsafe extern "system" fn wallpaper_wndproc(
    hwnd: HWND,
    msg: u32,
    wparam: WPARAM,
    lparam: LPARAM,
) -> LRESULT {
    let prev_isize = PREV_WNDPROC.load(std::sync::atomic::Ordering::Relaxed);
    let prev_proc: WNDPROC = std::mem::transmute(prev_isize);

    if msg == WM_DISPLAYCHANGE || msg == WM_DPICHANGED {
        if let Some(tx) = REPOSITION_TX.get() {
            let _ = tx.try_send(());
        }
    }

    CallWindowProcW(prev_proc, hwnd, msg, wparam, lparam)
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
        let mut ex_style = GetWindowLongPtrW(tauri_hwnd, GWL_EXSTYLE);
        ex_style &= !(windows::Win32::UI::WindowsAndMessaging::WS_EX_CLIENTEDGE.0
            | windows::Win32::UI::WindowsAndMessaging::WS_EX_WINDOWEDGE.0
            | windows::Win32::UI::WindowsAndMessaging::WS_EX_DLGMODALFRAME.0
            | windows::Win32::UI::WindowsAndMessaging::WS_EX_STATICEDGE.0) as isize;
        let _ = SetWindowLongPtrW(
            tauri_hwnd,
            GWL_EXSTYLE,
            ex_style | WS_EX_TRANSPARENT.0 as isize,
        );

        // 6. Calculate position based on Config and GDI Work Area for selected monitor_index
        let config = crate::config::load_config();
        let monitor_info = get_target_monitor_info(config.monitor_index);

        let work_area = monitor_info.rcWork;
        let work_w = work_area.right - work_area.left;
        let work_h = work_area.bottom - work_area.top;

        let dpi = GetDpiForWindow(tauri_hwnd);
        let scale_factor = dpi as f64 / 96.0;
        let widget_w = (300.0 * scale_factor) as i32;
        let widget_h = (200.0 * scale_factor) as i32;
        let offset_x = (config.offset_x as f64 * scale_factor) as i32;
        let offset_y = (config.offset_y as f64 * scale_factor) as i32;

        let mut x = work_area.left;
        let mut y = work_area.top;

        match config.position_corner.as_str() {
            "top-left" => {
                x += offset_x;
                y += offset_y;
            }
            "top-right" => {
                x += work_w - widget_w - offset_x;
                y += offset_y;
            }
            "bottom-left" => {
                x += offset_x;
                y += work_h - widget_h - offset_y;
            }
            _ => { // "bottom-right"
                x += work_w - widget_w - offset_x;
                y += work_h - widget_h - offset_y;
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

pub async fn setup_with_retry(window: &WebviewWindow) {
    for attempt in 0..5 {
        match setup_wallpaper_widget(window) {
            Ok(_) => break,
            Err(e) => {
                eprintln!("setup attempt {} failed: {}", attempt + 1, e);
                tokio::time::sleep(std::time::Duration::from_millis(500 * (attempt + 1) as u64)).await;
            }
        }
    }
}

pub fn init_wallpaper_widget(window: WebviewWindow) {
    let (tx, mut rx) = mpsc::channel::<()>(5);
    let _ = REPOSITION_TX.set(tx);

    let window_clone = window.clone();
    tauri::async_runtime::spawn(async move {
        // Initial setup with retry
        setup_with_retry(&window_clone).await;

        // Subclass window procedure for display change events
        if let Ok(hwnd) = window_clone.hwnd() {
            unsafe {
                let prev = GetWindowLongPtrW(hwnd, GWLP_WNDPROC);
                if prev != 0 && PREV_WNDPROC.load(std::sync::atomic::Ordering::Relaxed) == 0 {
                    PREV_WNDPROC.store(prev, std::sync::atomic::Ordering::Relaxed);
                    SetWindowLongPtrW(hwnd, GWLP_WNDPROC, wallpaper_wndproc as *const () as isize);
                }
            }
        }

        // Debounced event handler loop
        while let Some(_) = rx.recv().await {
            tokio::time::sleep(std::time::Duration::from_millis(300)).await;
            setup_with_retry(&window_clone).await;
        }
    });
}
