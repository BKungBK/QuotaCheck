package com.quotacheck.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.quotacheck.app.core.model.QuotaRepository
import com.quotacheck.app.core.preferences.UserPreferencesRepository
import com.quotacheck.app.sync.SyncScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(
    repository: QuotaRepository,
    preferences: UserPreferencesRepository,
    private val scheduler: SyncScheduler,
) : ViewModel() {
    val uiState: StateFlow<HomeUiState> = combine(
        repository.currentPools,
        repository.syncState,
        preferences.preferences,
    ) { pools, syncState, preferences ->
        HomeUiState.from(pools, syncState, preferences.syncIntervalMinutes)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.InitialLoading)

    fun refresh() = scheduler.refreshNow()
}

class HomeViewModelFactory(
    private val repository: QuotaRepository,
    private val preferences: UserPreferencesRepository,
    private val scheduler: SyncScheduler,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        check(modelClass.isAssignableFrom(HomeViewModel::class.java))
        return HomeViewModel(repository, preferences, scheduler) as T
    }
}
