package com.quotacheck.app.feature.onboarding

sealed interface OnboardingUiState {
    data object Loading : OnboardingUiState
    data object NeedsToken : OnboardingUiState
    data object TokenRequired : OnboardingUiState
    data object Validating : OnboardingUiState
    data object ValidationFailed : OnboardingUiState
    data object InitialSyncing : OnboardingUiState
    data object InitialSyncFailed : OnboardingUiState
    data object Connected : OnboardingUiState
}
