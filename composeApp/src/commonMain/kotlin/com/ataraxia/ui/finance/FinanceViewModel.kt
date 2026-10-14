package com.ataraxia.ui.finance

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.ataraxia.domain.model.*
import com.ataraxia.domain.repository.FinanceRepository
import com.benasher44.uuid.uuid4
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

data class FinanceUiState(
    val summary: FinanceSummary = FinanceSummary(),
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
)

class FinanceViewModel(private val repository: FinanceRepository) : ScreenModel {
    private val mutableState = MutableStateFlow(FinanceUiState())
    val state = mutableState.asStateFlow()
    private var observation: Job? = null

    init { reload() }

    fun reload() {
        observation?.cancel()
        mutableState.update { it.copy(loading = true, error = null) }
        observation = screenModelScope.launch {
            repository.observeEntries().catch {
                mutableState.update { it.copy(loading = false, error = "No se pudieron cargar las finanzas.") }
            }.collect { entries ->
                mutableState.update { it.copy(summary = FinanceSummary(entries), loading = false) }
            }
        }
    }

    fun clearError() { mutableState.update { it.copy(error = null) } }

    fun add(name: String, category: FinanceCategory, amount: Long, onSaved: () -> Unit) = mutate(onSaved) {
        repository.add(FinanceEntry(uuid4().toString(), name.trim(), category, amount, Clock.System.now().toEpochMilliseconds()))
    }

    fun update(entry: FinanceEntry, onSaved: () -> Unit) = mutate(onSaved) { repository.update(entry) }
    fun delete(id: String, onDeleted: () -> Unit) = mutate(onDeleted) { repository.delete(id) }

    private fun mutate(onSaved: () -> Unit, action: suspend () -> Unit) {
        if (state.value.saving) return
        mutableState.update { it.copy(saving = true, error = null) }
        screenModelScope.launch {
            try {
                action()
                mutableState.update { it.copy(saving = false) }
                onSaved()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.update { it.copy(saving = false, error = "No se pudo guardar. Tus datos siguen aquí; vuelve a intentarlo.") }
            }
        }
    }
}
