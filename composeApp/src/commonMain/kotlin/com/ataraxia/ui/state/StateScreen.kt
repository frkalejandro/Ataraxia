package com.ataraxia.ui.state

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import com.ataraxia.domain.model.DailyState
import com.ataraxia.domain.model.MorningErection
import com.ataraxia.domain.model.SleepEntry
import com.ataraxia.domain.repository.DailyStateRepository
import com.ataraxia.domain.repository.SleepRepository
import com.benasher44.uuid.uuid4
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.*
import kotlin.math.roundToInt

private val StatePurple = Color(0xFF6C63FF)
private val StateGreen = Color(0xFF40B88E)
private const val MILLILITERS_PER_GLASS = 250

enum class MetricsPeriod { WEEK, MONTH }

data class PeriodMetrics(
    val period: MetricsPeriod,
    val from: LocalDate,
    val to: LocalDate,
    val stateDays: Int,
    val sleepDays: Int,
    val averageEnergy: Float?,
    val averageMood: Float?,
    val averageStress: Float?,
    val averageSleepMinutes: Float?,
    val trainedDays: Int,
    val averageWaterLiters: Float?,
    val averageMusclePain: Float?,
    val averageFatigue: Float?,
    val averageLibido: Float?,
    val libidoDays: Int,
    val morningErections: Int,
    val answeredMorningErectionDays: Int,
    val averageDeepStudyMinutes: Float?,
    val averageConcentration: Float?,
)

// ─────────────────────────────────────────────
//  Estado y ViewModel
// ─────────────────────────────────────────────

data class StateForm(
    val energy: Int = 5,
    val mood: Int = 5,
    val stress: Int = 5,
    val trained: Boolean = false,
    val breakfast: String = "",
    val lunch: String = "",
    val tea: String = "",
    val dinner: String = "",
    val otherFood: String = "",
    val waterGlasses: Int = 0,
    val musclePain: Int = 1,
    val fatigue: Int = 5,
    val libido: Int? = null,
    val morningErection: MorningErection = MorningErection.NOT_ANSWERED,
    val deepStudyMinutes: Int = 0,
    val concentration: Int = 5,
)

data class StateUiState(
    val date: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()),
    val today: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()),
    val existingEntry: DailyState? = null,
    val sleepEntry: SleepEntry? = null,
    val form: StateForm = StateForm(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val savedMessageVisible: Boolean = false,
    val allEntries: List<DailyState> = emptyList(),
    val metricsPeriod: MetricsPeriod = MetricsPeriod.WEEK,
    val periodMetrics: PeriodMetrics? = null,
    val errorMessage: String? = null,
)

class StateViewModel(
    private val stateRepository: DailyStateRepository,
    private val sleepRepository: SleepRepository,
) : ScreenModel {

    private val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    private val selectedDate = MutableStateFlow(today)
    private val metricsPeriod = MutableStateFlow(MetricsPeriod.WEEK)
    private val _state = MutableStateFlow(StateUiState(date = today, today = today))
    val state: StateFlow<StateUiState> = _state.asStateFlow()

    init {
        observeSelectedDay()
        observePeriodMetrics()
    }

    private fun observeSelectedDay() {
        screenModelScope.launch {
            selectedDate
                .flatMapLatest { date ->
                    combine(
                        stateRepository.observeStateForDate(date),
                        sleepRepository.observeSleepEntriesInRange(date, date),
                        stateRepository.observeAllStates(),
                    ) { dailyState, sleepEntries, allEntries ->
                        LoadedDay(
                            date = date,
                            dailyState = dailyState,
                            sleepEntry = sleepEntries.firstOrNull(),
                            allEntries = allEntries,
                        )
                    }
                }
                .catch { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isSaving = false,
                            errorMessage = error.message ?: "No se pudo abrir el registro de Estado.",
                        )
                    }
                }
                .collect { loaded ->
                    _state.update { current ->
                        current.copy(
                            date = loaded.date,
                            today = today,
                            existingEntry = loaded.dailyState,
                            sleepEntry = loaded.sleepEntry,
                            form = loaded.dailyState?.toForm() ?: StateForm(),
                            isLoading = false,
                            isSaving = false,
                            savedMessageVisible = false,
                            allEntries = loaded.allEntries,
                            errorMessage = null,
                        )
                    }
                }
        }
    }

    private fun observePeriodMetrics() {
        screenModelScope.launch {
            combine(selectedDate, metricsPeriod) { date, period -> date to period }
                .flatMapLatest { (date, period) ->
                    val range = metricsRange(date, period)
                    combine(
                        stateRepository.observeStatesInRange(range.first, range.second),
                        sleepRepository.observeSleepEntriesInRange(range.first, range.second),
                    ) { states, sleepEntries ->
                        calculatePeriodMetrics(
                            period = period,
                            from = range.first,
                            to = range.second,
                            states = states,
                            sleepEntries = sleepEntries,
                        )
                    }
                }
                .catch { error ->
                    _state.update {
                        it.copy(
                            errorMessage = error.message ?: "No se pudieron calcular los promedios.",
                        )
                    }
                }
                .collect { metrics ->
                    _state.update { current ->
                        current.copy(
                            metricsPeriod = metrics.period,
                            periodMetrics = metrics,
                        )
                    }
                }
        }
    }

    fun selectDate(date: LocalDate) {
        if (date == _state.value.date) return
        _state.update { it.copy(isLoading = true, savedMessageVisible = false) }
        selectedDate.value = date
    }

    fun goToToday() = selectDate(today)

    fun setMetricsPeriod(period: MetricsPeriod) {
        metricsPeriod.value = period
    }

    fun updateForm(transform: (StateForm) -> StateForm) {
        _state.update { it.copy(form = transform(it.form), savedMessageVisible = false) }
    }

    fun setWaterFromLiters(liters: Float) {
        val glasses = (liters * 1000f / MILLILITERS_PER_GLASS).roundToInt()
        updateForm { it.copy(waterGlasses = glasses.coerceAtLeast(0)) }
    }

    fun saveSelectedDate() {
        val snapshot = _state.value
        if (snapshot.isSaving) return

        screenModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            val now = Clock.System.now()
            val existing = snapshot.existingEntry
            val form = snapshot.form

            runCatching {
                stateRepository.saveState(
                    DailyState(
                        id = existing?.id ?: uuid4().toString(),
                        date = snapshot.date,
                        energy = form.energy,
                        mood = form.mood,
                        stress = form.stress,
                        trained = form.trained,
                        breakfast = form.breakfast.trim(),
                        lunch = form.lunch.trim(),
                        tea = form.tea.trim(),
                        dinner = form.dinner.trim(),
                        otherFood = form.otherFood.trim(),
                        waterGlasses = form.waterGlasses,
                        musclePain = form.musclePain,
                        fatigue = form.fatigue,
                        libido = form.libido,
                        morningErection = form.morningErection,
                        deepStudyMinutes = form.deepStudyMinutes,
                        concentration = form.concentration,
                        createdAt = existing?.createdAt ?: now,
                        updatedAt = now,
                    )
                )
            }.onSuccess {
                _state.update {
                    it.copy(
                        isSaving = false,
                        savedMessageVisible = true,
                        errorMessage = null,
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isSaving = false,
                        savedMessageVisible = false,
                        errorMessage = error.message ?: "No se pudo guardar el registro.",
                    )
                }
            }
        }
    }

    private data class LoadedDay(
        val date: LocalDate,
        val dailyState: DailyState?,
        val sleepEntry: SleepEntry?,
        val allEntries: List<DailyState>,
    )

    private fun DailyState.toForm() = StateForm(
        energy = energy,
        mood = mood,
        stress = stress,
        trained = trained,
        breakfast = breakfast,
        lunch = lunch,
        tea = tea,
        dinner = dinner,
        otherFood = otherFood,
        waterGlasses = waterGlasses,
        musclePain = musclePain,
        fatigue = fatigue,
        libido = libido,
        morningErection = morningErection,
        deepStudyMinutes = deepStudyMinutes,
        concentration = concentration,
    )
}

// ─────────────────────────────────────────────
//  Pantalla
// ─────────────────────────────────────────────

class StateScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val vm: StateViewModel = koinScreenModel()
        val state by vm.state.collectAsState()

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Estado", fontWeight = FontWeight.SemiBold) },
                )
            },
        ) { padding ->
            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = StatePurple)
                }
            } else {
                StateContent(
                    modifier = Modifier.padding(padding),
                    state = state,
                    onUpdateForm = vm::updateForm,
                    onSetWaterFromLiters = vm::setWaterFromLiters,
                    onSave = vm::saveSelectedDate,
                    onSelectDate = vm::selectDate,
                    onGoToToday = vm::goToToday,
                    onSetMetricsPeriod = vm::setMetricsPeriod,
                )
            }
        }
    }
}

@Composable
private fun StateContent(
    modifier: Modifier,
    state: StateUiState,
    onUpdateForm: ((StateForm) -> StateForm) -> Unit,
    onSetWaterFromLiters: (Float) -> Unit,
    onSave: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onGoToToday: () -> Unit,
    onSetMetricsPeriod: (MetricsPeriod) -> Unit,
) {
    val form = state.form
    val scrollState = rememberScrollState()
    var historyOpen by rememberSaveable { mutableStateOf(false) }
    var metricsOpen by rememberSaveable { mutableStateOf(false) }

    if (historyOpen) {
        StateHistorySheet(
            entries = state.allEntries,
            selectedDate = state.date,
            onDismiss = { historyOpen = false },
            onSelectDate = { date ->
                historyOpen = false
                onSelectDate(date)
            },
        )
    }

    if (metricsOpen) {
        StateMetricsSheet(
            metrics = state.periodMetrics,
            selectedPeriod = state.metricsPeriod,
            onPeriodChange = onSetMetricsPeriod,
            onDismiss = { metricsOpen = false },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DayHeader(
            date = state.date,
            today = state.today,
            alreadySaved = state.existingEntry != null,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = { historyOpen = true },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Default.CalendarMonth, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Historial")
            }
            FilledTonalButton(
                onClick = { metricsOpen = true },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Default.Insights, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Promedios")
            }
        }

        if (state.date != state.today) {
            TextButton(
                onClick = onGoToToday,
                modifier = Modifier.align(Alignment.End),
            ) {
                Icon(Icons.Default.Today, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Volver a hoy")
            }
        }

        ExpandableStateCard(
            title = "Estado general",
            subtitle = "Cómo te sentiste hoy",
            icon = Icons.Default.Favorite,
            accent = Color(0xFFE66D8A),
            initiallyExpanded = true,
        ) {
            RatingField("Energía", form.energy) {
                onUpdateForm { current -> current.copy(energy = it) }
            }
            RatingField("Estado de ánimo", form.mood) {
                onUpdateForm { current -> current.copy(mood = it) }
            }
            RatingField("Estrés", form.stress, lowIsGood = true) {
                onUpdateForm { current -> current.copy(stress = it) }
            }
        }

        ExpandableStateCard(
            title = "Sueño",
            subtitle = "Datos registrados en Sueño",
            icon = Icons.Default.Bedtime,
            accent = Color(0xFF4D8EDB),
        ) {
            SleepSummary(state.sleepEntry)
        }

        ExpandableStateCard(
            title = "Actividad física",
            subtitle = if (form.trained) "Entrenamiento registrado" else "¿Entrenaste hoy?",
            icon = Icons.Default.FitnessCenter,
            accent = Color(0xFFF08A5D),
        ) {
            BooleanChoice(
                label = "¿Entrenaste hoy?",
                selected = form.trained,
                onSelected = {
                    onUpdateForm { current -> current.copy(trained = it) }
                },
            )
        }

        ExpandableStateCard(
            title = "Alimentación",
            subtitle = foodSummary(form),
            icon = Icons.Default.Restaurant,
            accent = Color(0xFFE5A83B),
        ) {
            FoodTextField("Desayuno", form.breakfast) {
                onUpdateForm { current -> current.copy(breakfast = it) }
            }
            FoodTextField("Almuerzo", form.lunch) {
                onUpdateForm { current -> current.copy(lunch = it) }
            }
            FoodTextField("Once", form.tea) {
                onUpdateForm { current -> current.copy(tea = it) }
            }
            FoodTextField("Cena", form.dinner) {
                onUpdateForm { current -> current.copy(dinner = it) }
            }
            FoodTextField("Otros", form.otherFood, "Colaciones, picoteos u otras comidas") {
                onUpdateForm { current -> current.copy(otherFood = it) }
            }
        }

        ExpandableStateCard(
            title = "Agua",
            subtitle = waterSummary(form.waterGlasses),
            icon = Icons.Default.WaterDrop,
            accent = Color(0xFF3BA7D8),
        ) {
            WaterCounter(
                glasses = form.waterGlasses,
                onGlassesChange = { glasses ->
                    onUpdateForm { current -> current.copy(waterGlasses = glasses.coerceAtLeast(0)) }
                },
                onBottleSelected = onSetWaterFromLiters,
            )
        }

        ExpandableStateCard(
            title = "Recuperación",
            subtitle = "Fatiga y dolor muscular",
            icon = Icons.Default.SelfImprovement,
            accent = StateGreen,
        ) {
            RatingField("Dolor muscular", form.musclePain, lowIsGood = true) {
                onUpdateForm { current -> current.copy(musclePain = it) }
            }
            RatingField("Fatiga", form.fatigue, lowIsGood = true) {
                onUpdateForm { current -> current.copy(fatigue = it) }
            }
        }

        ExpandableStateCard(
            title = "Indicadores hormonales",
            subtitle = "Opcional y privado",
            icon = Icons.Default.Lock,
            accent = Color(0xFF9C6ADE),
        ) {
            OptionalRatingField(
                label = "Libido",
                value = form.libido,
                onValueChange = {
                    onUpdateForm { current -> current.copy(libido = it) }
                },
            )
            MorningErectionSelector(
                value = form.morningErection,
                onValueChange = {
                    onUpdateForm { current -> current.copy(morningErection = it) }
                },
            )
        }

        ExpandableStateCard(
            title = "Concentración",
            subtitle = studySummary(form.deepStudyMinutes),
            icon = Icons.Default.Psychology,
            accent = StatePurple,
        ) {
            StudyTimeSelector(
                minutes = form.deepStudyMinutes,
                onMinutesChange = {
                    onUpdateForm { current -> current.copy(deepStudyMinutes = it.coerceAtLeast(0)) }
                },
            )
            RatingField("Facilidad para concentrarte", form.concentration) {
                onUpdateForm { current -> current.copy(concentration = it) }
            }
        }

        state.errorMessage?.let { message ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(14.dp),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        Button(
            onClick = onSave,
            enabled = !state.isSaving,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = StatePurple),
        ) {
            if (state.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    if (state.existingEntry == null) Icons.Default.Check else Icons.Default.Save,
                    contentDescription = null,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        state.existingEntry != null && state.date == state.today -> "Actualizar registro de hoy"
                        state.existingEntry == null && state.date == state.today -> "Guardar registro de hoy"
                        state.existingEntry != null -> "Actualizar este registro"
                        else -> "Guardar este registro"
                    },
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        AnimatedVisibility(state.savedMessageVisible) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = StateGreen.copy(alpha = 0.12f),
                shape = RoundedCornerShape(14.dp),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.CheckCircle, null, tint = StateGreen)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (state.date == state.today) "Tu día quedó guardado."
                        else "El registro quedó actualizado.",
                        color = StateGreen,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        Text(
            "Existe un solo registro por día. Los días anteriores pueden consultarse y corregirse cuando quieras.",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
    }
}

// ─────────────────────────────────────────────
//  Componentes visuales
// ─────────────────────────────────────────────

@Composable
private fun DayHeader(
    date: LocalDate,
    today: LocalDate,
    alreadySaved: Boolean,
) {
    val isToday = date == today
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = StatePurple.copy(alpha = 0.10f),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (isToday) "Estado de hoy" else "Estado registrado",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    formatDate(date),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = if (alreadySaved) StateGreen.copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.surface,
            ) {
                Text(
                    if (alreadySaved) "Guardado" else "Pendiente",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    color = if (alreadySaved) StateGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StateHistorySheet(
    entries: List<DailyState>,
    selectedDate: LocalDate,
    onDismiss: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Días con registro",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Solo aparecen fechas que ya tienen un Estado guardado.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (entries.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                ) {
                    Text(
                        "Todavía no hay registros diarios guardados.",
                        modifier = Modifier.padding(18.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                entries.forEach { entry ->
                    val selected = entry.date == selectedDate
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectDate(entry.date) },
                        shape = RoundedCornerShape(16.dp),
                        color = if (selected) StatePurple.copy(alpha = 0.14f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, StatePurple)
                        else null,
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.EventNote,
                                contentDescription = null,
                                tint = if (selected) StatePurple else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(formatDate(entry.date), fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Energía ${entry.energy}/10 · Ánimo ${entry.mood}/10 · Estrés ${entry.stress}/10",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null)
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StateMetricsSheet(
    metrics: PeriodMetrics?,
    selectedPeriod: MetricsPeriod,
    onPeriodChange: (MetricsPeriod) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 680.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Resumen de Estado",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MetricsPeriod.entries.forEach { period ->
                    FilterChip(
                        selected = selectedPeriod == period,
                        onClick = { onPeriodChange(period) },
                        label = { Text(if (period == MetricsPeriod.WEEK) "Semana" else "Mes") },
                        leadingIcon = if (selectedPeriod == period) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else null,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (metrics == null) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = StatePurple,
                )
            } else {
                Text(
                    "${formatShortDate(metrics.from)} – ${formatShortDate(metrics.to)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (metrics.stateDays == 0 && metrics.sleepDays == 0) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    ) {
                        Text(
                            "No hay registros para este período.",
                            modifier = Modifier.padding(18.dp),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    MetricsSection("Estado general", Icons.Default.Favorite) {
                        MetricTile("Energía promedio", formatScore(metrics.averageEnergy), Modifier.weight(1f))
                        MetricTile("Ánimo promedio", formatScore(metrics.averageMood), Modifier.weight(1f))
                        MetricTile("Estrés promedio", formatScore(metrics.averageStress), Modifier.weight(1f))
                    }

                    MetricsSection("Sueño y actividad", Icons.Default.Bedtime) {
                        MetricTile("Sueño promedio", formatAverageDuration(metrics.averageSleepMinutes), Modifier.weight(1f))
                        MetricTile("Días entrenados", "${metrics.trainedDays}", Modifier.weight(1f))
                    }

                    MetricsSection("Agua y recuperación", Icons.Default.WaterDrop) {
                        MetricTile("Agua por día", formatAverageLiters(metrics.averageWaterLiters), Modifier.weight(1f))
                        MetricTile("Dolor muscular", formatScore(metrics.averageMusclePain), Modifier.weight(1f))
                        MetricTile("Fatiga", formatScore(metrics.averageFatigue), Modifier.weight(1f))
                    }

                    MetricsSection("Indicadores hormonales", Icons.Default.Lock) {
                        MetricTile("Libido promedio", formatScore(metrics.averageLibido), Modifier.weight(1f))
                        MetricTile("Erecciones matutinas", "${metrics.morningErections}", Modifier.weight(1f))
                    }

                    MetricsSection("Concentración", Icons.Default.Psychology) {
                        MetricTile("Estudio profundo", formatAverageDuration(metrics.averageDeepStudyMinutes), Modifier.weight(1f))
                        MetricTile("Facilidad promedio", formatScore(metrics.averageConcentration), Modifier.weight(1f))
                    }

                    Text(
                        "Promedios calculados con ${metrics.stateDays} días de Estado y ${metrics.sleepDays} días de Sueño. " +
                            "La libido usa ${metrics.libidoDays} días respondidos; las erecciones matutinas se cuentan sobre ${metrics.answeredMorningErectionDays} días respondidos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun MetricsSection(
    title: String,
    icon: ImageVector,
    content: @Composable RowScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = StatePurple, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, fontWeight = FontWeight.SemiBold)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.heightIn(min = 82.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ExpandableStateCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f)

    Surface(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = accent)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.SemiBold)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Cerrar" else "Abrir",
                    modifier = Modifier.graphicsLayer(rotationZ = rotation),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AnimatedVisibility(expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    content = content,
                )
            }
        }
    }
}

@Composable
private fun RatingField(
    label: String,
    value: Int,
    lowIsGood: Boolean = false,
    onValueChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
            RatingBadge(value, lowIsGood)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt().coerceIn(1, 10)) },
            valueRange = 1f..10f,
            steps = 8,
            colors = SliderDefaults.colors(activeTrackColor = StatePurple, thumbColor = StatePurple),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("1", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("10", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun OptionalRatingField(
    label: String,
    value: Int?,
    onValueChange: (Int?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
            TextButton(onClick = { onValueChange(if (value == null) 5 else null) }) {
                Text(if (value == null) "Registrar" else "Omitir")
            }
        }
        AnimatedVisibility(value != null) {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    RatingBadge(value ?: 5, false)
                }
                Slider(
                    value = (value ?: 5).toFloat(),
                    onValueChange = { onValueChange(it.roundToInt().coerceIn(1, 10)) },
                    valueRange = 1f..10f,
                    steps = 8,
                    colors = SliderDefaults.colors(activeTrackColor = StatePurple, thumbColor = StatePurple),
                )
            }
        }
        if (value == null) {
            Text(
                "Sin registrar",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RatingBadge(value: Int, lowIsGood: Boolean) {
    val normalized = if (lowIsGood) 11 - value else value
    val color = when {
        normalized >= 8 -> StateGreen
        normalized >= 5 -> Color(0xFFE5A83B)
        else -> Color(0xFFE66D6D)
    }
    Surface(shape = CircleShape, color = color.copy(alpha = 0.14f)) {
        Text(
            "$value/10",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun BooleanChoice(
    label: String,
    selected: Boolean,
    onSelected: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, fontWeight = FontWeight.Medium)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ChoiceChip("Sí", selected, Modifier.weight(1f)) { onSelected(true) }
            ChoiceChip("No", !selected, Modifier.weight(1f)) { onSelected(false) }
        }
    }
}

@Composable
private fun ChoiceChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) StatePurple.copy(alpha = 0.14f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, StatePurple)
        else null,
    ) {
        Text(
            text,
            modifier = Modifier.padding(vertical = 12.dp),
            textAlign = TextAlign.Center,
            color = if (selected) StatePurple else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun FoodTextField(
    label: String,
    value: String,
    placeholder: String = "Escribe lo que comiste",
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        minLines = 1,
        maxLines = 3,
        shape = RoundedCornerShape(14.dp),
    )
}

@Composable
private fun WaterCounter(
    glasses: Int,
    onGlassesChange: (Int) -> Unit,
    onBottleSelected: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            FilledTonalIconButton(onClick = { onGlassesChange(glasses - 1) }) {
                Icon(Icons.Default.Remove, "Restar vaso")
            }
            Column(
                modifier = Modifier.width(140.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("$glasses", fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (glasses == 1) "vaso" else "vasos",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalIconButton(onClick = { onGlassesChange(glasses + 1) }) {
                Icon(Icons.Default.Add, "Sumar vaso")
            }
        }

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF3BA7D8).copy(alpha = 0.10f),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("1 vaso = 250 mL", fontWeight = FontWeight.SemiBold)
                Text(
                    "$glasses vasos = ${formatLiters(glasses * 0.25f)} L",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Text("Atajos por botella", style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(1f, 1.5f, 1.6f, 2f).forEach { liters ->
                AssistChip(
                    onClick = { onBottleSelected(liters) },
                    label = { Text("${formatLiters(liters)} L") },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Text(
            "Una botella de 1,6 L equivale aproximadamente a 6,4 vasos; el atajo la redondea a 6 vasos.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MorningErectionSelector(
    value: MorningErection,
    onValueChange: (MorningErection) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Erección matutina", fontWeight = FontWeight.Medium)
        listOf(
            MorningErection.YES to "Sí",
            MorningErection.NO to "No",
            MorningErection.NOT_ANSWERED to "Prefiero no responder",
        ).forEach { (option, label) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onValueChange(option) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = value == option,
                    onClick = { onValueChange(option) },
                )
                Text(label)
            }
        }
    }
}

@Composable
private fun StudyTimeSelector(
    minutes: Int,
    onMinutesChange: (Int) -> Unit,
) {
    val hours = minutes / 60
    val remainingMinutes = minutes % 60

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Estudio profundo", fontWeight = FontWeight.Medium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberStepper(
                label = "Horas",
                value = hours,
                onMinus = { onMinutesChange(((hours - 1).coerceAtLeast(0) * 60) + remainingMinutes) },
                onPlus = { onMinutesChange(((hours + 1).coerceAtMost(16) * 60) + remainingMinutes) },
                modifier = Modifier.weight(1f),
            )
            NumberStepper(
                label = "Minutos",
                value = remainingMinutes,
                onMinus = {
                    val next = when {
                        remainingMinutes >= 15 -> minutes - 15
                        hours > 0 -> (hours - 1) * 60 + 45
                        else -> 0
                    }
                    onMinutesChange(next)
                },
                onPlus = {
                    val next = minutes + 15
                    onMinutesChange(next.coerceAtMost(16 * 60))
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun NumberStepper(
    label: String,
    value: Int,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onMinus) { Icon(Icons.Default.Remove, null) }
                Text("$value", modifier = Modifier.width(34.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                IconButton(onClick = onPlus) { Icon(Icons.Default.Add, null) }
            }
        }
    }
}

@Composable
private fun SleepSummary(entry: SleepEntry?) {
    if (entry == null) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Sueño aún no registrado", fontWeight = FontWeight.Medium)
                    Text(
                        "Añádelo desde la pestaña Sueño.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    } else {
        val localBed = entry.bedtime.toLocalDateTime(TimeZone.currentSystemDefault()).time
        val localWake = entry.wakeTime.toLocalDateTime(TimeZone.currentSystemDefault()).time
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SleepMetric("Duración", formatDuration(entry.durationMinutes), Modifier.weight(1f))
            SleepMetric("Calidad", "${entry.quality}/5", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SleepMetric("Te acostaste", formatTime(localBed), Modifier.weight(1f))
            SleepMetric("Despertaste", formatTime(localWake), Modifier.weight(1f))
        }
        Text(
            "Estos datos vienen directamente de Sueño y no se vuelven a pedir aquí.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SleepMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF4D8EDB).copy(alpha = 0.10f),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(value, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ─────────────────────────────────────────────
//  Utilidades
// ─────────────────────────────────────────────

private fun metricsRange(date: LocalDate, period: MetricsPeriod): Pair<LocalDate, LocalDate> =
    when (period) {
        MetricsPeriod.WEEK -> {
            val start = date.minus(DatePeriod(days = date.dayOfWeek.isoDayNumber - 1))
            start to start.plus(DatePeriod(days = 6))
        }
        MetricsPeriod.MONTH -> {
            val start = LocalDate(date.year, date.monthNumber, 1)
            val nextMonth = if (date.monthNumber == 12) {
                LocalDate(date.year + 1, 1, 1)
            } else {
                LocalDate(date.year, date.monthNumber + 1, 1)
            }
            start to nextMonth.minus(DatePeriod(days = 1))
        }
    }

private fun calculatePeriodMetrics(
    period: MetricsPeriod,
    from: LocalDate,
    to: LocalDate,
    states: List<DailyState>,
    sleepEntries: List<SleepEntry>,
): PeriodMetrics {
    fun average(values: List<Int>): Float? =
        values.takeIf { it.isNotEmpty() }?.average()?.toFloat()

    val libidoValues = states.mapNotNull { it.libido }
    val answeredErections = states.filter { it.morningErection != MorningErection.NOT_ANSWERED }

    return PeriodMetrics(
        period = period,
        from = from,
        to = to,
        stateDays = states.size,
        sleepDays = sleepEntries.size,
        averageEnergy = average(states.map { it.energy }),
        averageMood = average(states.map { it.mood }),
        averageStress = average(states.map { it.stress }),
        averageSleepMinutes = average(sleepEntries.map { it.durationMinutes }),
        trainedDays = states.count { it.trained },
        averageWaterLiters = states.takeIf { it.isNotEmpty() }
            ?.map { it.waterLiters }
            ?.average()
            ?.toFloat(),
        averageMusclePain = average(states.map { it.musclePain }),
        averageFatigue = average(states.map { it.fatigue }),
        averageLibido = average(libidoValues),
        libidoDays = libidoValues.size,
        morningErections = answeredErections.count { it.morningErection == MorningErection.YES },
        answeredMorningErectionDays = answeredErections.size,
        averageDeepStudyMinutes = average(states.map { it.deepStudyMinutes }),
        averageConcentration = average(states.map { it.concentration }),
    )
}

private fun formatScore(value: Float?): String =
    value?.let { "${formatDecimal(it)}/10" } ?: "Sin datos"

private fun formatAverageLiters(value: Float?): String =
    value?.let { "${formatDecimal(it)} L" } ?: "Sin datos"

private fun formatAverageDuration(value: Float?): String {
    if (value == null) return "Sin datos"
    val totalMinutes = value.roundToInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours == 0 -> "${minutes} min"
        minutes == 0 -> "${hours} h"
        else -> "${hours} h ${minutes} min"
    }
}

private fun formatDecimal(value: Float): String {
    val rounded = (value * 10f).roundToInt() / 10f
    return if (rounded % 1f == 0f) rounded.toInt().toString()
    else rounded.toString().replace('.', ',')
}

private fun formatShortDate(date: LocalDate): String =
    "${date.dayOfMonth.toString().padStart(2, '0')}/${date.monthNumber.toString().padStart(2, '0')}/${date.year}"

private fun foodSummary(form: StateForm): String {
    val count = listOf(form.breakfast, form.lunch, form.tea, form.dinner, form.otherFood)
        .count { it.isNotBlank() }
    return when (count) {
        0 -> "Registra tus comidas libremente"
        1 -> "1 sección registrada"
        else -> "$count secciones registradas"
    }
}

private fun waterSummary(glasses: Int): String =
    if (glasses == 0) "Registra vasos o usa una botella"
    else "$glasses vasos · ${formatLiters(glasses * 0.25f)} L"

private fun studySummary(minutes: Int): String = when {
    minutes <= 0 -> "Horas y facilidad para concentrarte"
    minutes % 60 == 0 -> "${minutes / 60} h de estudio profundo"
    else -> "${minutes / 60} h ${minutes % 60} min de estudio profundo"
}

private fun formatLiters(value: Float): String {
    val roundedTenths = (value * 10).roundToInt() / 10f
    return if (roundedTenths % 1f == 0f) roundedTenths.toInt().toString()
    else roundedTenths.toString().replace('.', ',')
}

private fun formatDuration(minutes: Int): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return if (mins == 0) "${hours}h" else "${hours}h ${mins}m"
}

private fun formatTime(time: LocalTime): String =
    "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"

private fun formatDate(date: LocalDate): String {
    val weekdays = listOf("", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
    val months = listOf("", "enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre")
    return "${weekdays[date.dayOfWeek.isoDayNumber]}, ${date.dayOfMonth} de ${months[date.monthNumber]}"
}
