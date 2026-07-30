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

    const val DEFAULT_OAUTH_CLIENT_ID = "1071006060591-tmhssin2h21lcre235vtolojh4g403ep.apps.googleusercontent.com"
    const val DEFAULT_OAUTH_CLIENT_SECRET = "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"
}
