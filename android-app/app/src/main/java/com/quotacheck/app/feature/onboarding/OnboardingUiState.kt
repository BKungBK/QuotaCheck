package com.quotacheck.app.feature.onboarding

sealed interface OnboardingUiState {
    data object Loading : OnboardingUiState
    data object NeedsToken : OnboardingUiState
    data object TokenRequired : OnboardingUiState
    data object Validating : OnboardingUiState
    /** OAuth returned 401/403 — the token itself is invalid or revoked. */
    data object ValidationFailed : OnboardingUiState
    /** Network/timeout/DNS error — token may be valid but couldn't reach server. */
    data object NetworkError : OnboardingUiState
    /** Server returned 429 rate-limit. */
    data object RateLimited : OnboardingUiState
    data object InitialSyncing : OnboardingUiState
    data object InitialSyncFailed : OnboardingUiState
    data object Connected : OnboardingUiState
}
