package com.quotacheck.app.core.network

/** Non-secret compatibility constants captured in the approved API contract. */
object PrivateApiContract {
    const val OAUTH_BASE_URL = "https://oauth2.googleapis.com/"
    const val CLOUD_CODE_BASE_URL = "https://cloudcode-pa.googleapis.com/"
    const val RESOURCE_MANAGER_BASE_URL = "https://cloudresourcemanager.googleapis.com/"
    const val LOAD_CODE_ASSIST_PATH = "/v1internal:loadCodeAssist"
    const val QUOTA_SUMMARY_PATH = "/v1internal:retrieveUserQuotaSummary"
    const val AVAILABLE_MODELS_PATH = "/v1internal:fetchAvailableModels"

    const val USER_AGENT = "antigravity/1.104.0 windows/amd64"
    const val CLIENT_METADATA = "{\"ideType\":\"ANTIGRAVITY\",\"platform\":\"WINDOWS\",\"pluginType\":\"GEMINI\"}"
}
