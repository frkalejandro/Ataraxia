package com.ataraxia.ui.exercise

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.ataraxia.domain.model.*
import com.ataraxia.domain.repository.WorkoutRepository
import com.benasher44.uuid.uuid4
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.*

data class ExerciseDraft(val id: String = uuid4().toString(), val area: BodyArea, val name: String = "")
data class ExerciseTarget(val exercise: Exercise, val sets: String = "3", val reps: String = "10")
data class RoutineDraft(
    val id: String = uuid4().toString(),
    val name: String = "",
    val mode: WorkoutMode = WorkoutMode.SETS,
    val targets: List<ExerciseTarget> = emptyList(),
    val minutes: String = "5",
    val seconds: String = "0",
) {
    fun build(): WorkoutRoutine? {
        val mins = if (mode == WorkoutMode.TIMED) minutes.toIntOrNull() ?: return null else 5
        val secs = if (mode == WorkoutMode.TIMED) seconds.toIntOrNull() ?: return null else 0
        if (mode == WorkoutMode.TIMED && (mins !in 0..1440 || secs !in 0..59 || mins * 60 + secs !in 1..86400)) return null
        if (name.isBlank() || targets.isEmpty()) return null
        val exercises = targets.map {
            val sets = if (mode == WorkoutMode.SETS) it.sets.toIntOrNull() ?: return null
                else it.sets.toIntOrNull()?.takeIf { value -> value in 1..999 } ?: 3
            val reps = if (mode == WorkoutMode.SETS) it.reps.toIntOrNull() ?: return null
                else it.reps.toIntOrNull()?.takeIf { value -> value in 1..9999 } ?: 10
            if (sets !in 1..999 || reps !in 1..9999) return null
            WorkoutExercise(it.exercise, sets, reps)
        }
        return WorkoutRoutine(id, name.trim(), mode, exercises, if (mode == WorkoutMode.TIMED) mins * 60 + secs else 300)
    }
}

data class ExerciseUiState(
    val exercises: List<Exercise> = emptyList(),
    val routines: List<WorkoutRoutine> = emptyList(),
    val sessions: List<WorkoutSession> = emptyList(),
    val active: ActiveWorkout? = null,
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val area: BodyArea? = null,
    val exerciseDraft: ExerciseDraft? = null,
    val routineDraft: RoutineDraft? = null,
)

class ExerciseViewModel(private val repo: WorkoutRepository) : ScreenModel {
    private val mutableState = MutableStateFlow(ExerciseUiState())
    val state = mutableState.asStateFlow()
    private var observation: Job? = null

    init { load() }

    fun load() {
        observation?.cancel()
        mutableState.update { it.copy(loading = true, loadFailed = false, error = null) }
        observation = screenModelScope.launch {
            try {
                combine(repo.observeExercises(), repo.observeRoutines(), repo.observeSessions(), repo.observeActive()) {
                    exercises, routines, sessions, active ->
                    ExerciseUiState(exercises, routines, sessions, active, loading = false)
                }.collect { data ->
                    mutableState.update { it.copy(exercises = data.exercises, routines = data.routines,
                        sessions = data.sessions, active = data.active, loading = false) }
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                mutableState.update { it.copy(loading = false, loadFailed = true, error = "No se pudieron cargar los ejercicios. Vuelve a intentarlo.") }
            }
        }
    }

    private fun save(action: suspend () -> Unit) {
        if (state.value.busy) return
        mutableState.update { it.copy(busy = true, error = null) }
        screenModelScope.launch {
            try { action()
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                mutableState.update { it.copy(error = "No se pudo guardar el cambio. Vuelve a intentarlo.") }
            } finally { mutableState.update { it.copy(busy = false) } }
        }
    }

    fun selectArea(area: BodyArea?) { mutableState.update { it.copy(area = area) } }
    fun editExercise(draft: ExerciseDraft?) { mutableState.update { it.copy(exerciseDraft = draft) } }
    fun editRoutine(draft: RoutineDraft?) { mutableState.update { it.copy(routineDraft = draft) } }
    fun clearError() { mutableState.update { it.copy(error = null) } }

    fun saveExercise() {
        val draft = state.value.exerciseDraft ?: return
        if (draft.name.isBlank()) return
        save {
            repo.saveExercise(Exercise(draft.id, draft.name.trim(), draft.area))
            editExercise(null)
        }
    }
    fun deleteExercise(id: String) = save { repo.deleteExercise(id) }
    fun deleteRoutine(id: String) = save { repo.deleteRoutine(id) }
    fun saveRoutine() {
        val routine = state.value.routineDraft?.build() ?: return
        save { repo.saveRoutine(routine); editRoutine(null) }
    }
    fun start(routine: WorkoutRoutine) {
        if (state.value.active != null) return
        save { repo.saveActive(ActiveWorkout(uuid4().toString(), routine, Clock.System.now().toEpochMilliseconds())) }
    }
    fun pauseOrResume() {
        val active = state.value.active ?: return
        val now = Clock.System.now().toEpochMilliseconds()
        save { repo.saveActive(if (active.deadlineMillis == null) active.resume(now) else active.pause(now)) }
    }
    fun discard() = save { repo.discardActive() }
    fun complete() = save {
        val now = Clock.System.now()
        repo.completeActive(now.toEpochMilliseconds(), now.toLocalDateTime(TimeZone.currentSystemDefault()).date.toString())
    }
}

fun WorkoutRoutine.toDraft() = RoutineDraft(id, name, mode,
    exercises.map { ExerciseTarget(it.exercise, it.sets.toString(), it.reps.toString()) },
    (durationSeconds / 60).toString(), (durationSeconds % 60).toString())
