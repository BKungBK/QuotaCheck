# THIS FILE IS AUTO-GENERATED. DO NOT MODIFY!!

# Copyright 2020-2023 Tauri Programme within The Commons Conservancy
# SPDX-License-Identifier: Apache-2.0
# SPDX-License-Identifier: MIT

-keep class com.antigravity.quota.widget.* {
  native <methods>;
}

-keep class com.antigravity.quota.widget.WryActivity {
  public <init>(...);

  void setWebView(com.antigravity.quota.widget.RustWebView);
  java.lang.Class getAppClass(...);
  int getId();
  java.lang.String getVersion();
  int startActivity(...);
}

-keep class com.antigravity.quota.widget.Ipc {
  public <init>(...);

  @android.webkit.JavascriptInterface public <methods>;
}

-keep class com.antigravity.quota.widget.RustWebView {
  public <init>(...);

  void loadUrlMainThread(...);
  void loadHTMLMainThread(...);
  void evalScript(...);
}

-keep class com.antigravity.quota.widget.RustWebChromeClient,com.antigravity.quota.widget.RustWebViewClient {
  public <init>(...);
}
