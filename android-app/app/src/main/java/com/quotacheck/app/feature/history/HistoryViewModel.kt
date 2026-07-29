package com.quotacheck.app.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.quotacheck.app.core.database.HistoryDao
import com.quotacheck.app.core.model.QuotaRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    repository: QuotaRepository,
    private val historyDao: HistoryDao,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val locale: Locale = Locale.getDefault(),
) : ViewModel() {
    private val selectedPoolId = MutableStateFlow<String?>(null)
    private val selectedPeriod = MutableStateFlow(HistoryPeriod.Day)

    private val selection = combine(repository.currentPools, selectedPoolId, selectedPeriod) { pools, selected, period ->
        Triple(pools, selected?.takeIf { id -> pools.any { it.poolId == id } } ?: pools.firstOrNull()?.poolId, period)
    }

    val uiState = selection.flatMapLatest { (pools, poolId, period) ->
        if (poolId == null) return@flatMapLatest flowOf(HistoryUiState(pools = pools, period = period))
        val bounds = period.bounds(clock, locale = locale)
        historyDao.observeDailyAggregates(poolId, bounds.from.toEpochMilli(), bounds.until.toEpochMilli())
            .combine(flowOf(Unit)) { daily, _ -> HistoryUiState(pools, poolId, period, HistoryUiState.bars(period, daily, clock.zone, locale)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun selectPool(poolId: String) { selectedPoolId.value = poolId }
    fun selectPeriod(period: HistoryPeriod) { selectedPeriod.value = period }
}

class HistoryViewModelFactory(private val repository: QuotaRepository, private val historyDao: HistoryDao) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T {
        check(modelClass.isAssignableFrom(HistoryViewModel::class.java))
        return HistoryViewModel(repository, historyDao) as T
    }
}
