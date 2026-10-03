package com.ataraxia.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.ataraxia.db.AtaraxiaDatabase
import com.ataraxia.domain.model.DailyState
import com.ataraxia.domain.model.MorningErection
import com.ataraxia.domain.repository.DailyStateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

class DailyStateRepositoryImpl(
    private val db: AtaraxiaDatabase,
) : DailyStateRepository {

    private val q get() = db.stateQueries

    override fun observeStateForDate(date: LocalDate): Flow<DailyState?> =
        q.selectStateForDate(date.toString())
            .asFlow()
            .mapToOneOrNull(Dispatchers.Default)
            .map { it?.toDomain() }

    override fun observeAllStates(): Flow<List<DailyState>> =
        q.selectAllStates()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }

    override fun observeStatesInRange(
        from: LocalDate,
        to: LocalDate,
    ): Flow<List<DailyState>> =
        q.selectStatesInRange(from.toString(), to.toString())
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun getStateForDate(date: LocalDate): DailyState? =
        withContext(Dispatchers.Default) {
            q.selectStateForDate(date.toString()).executeAsOneOrNull()?.toDomain()
        }

    override suspend fun saveState(state: DailyState) =
        withContext(Dispatchers.Default) {
            q.upsertDailyState(
                id = state.id,
                date = state.date.toString(),
                energy = state.energy.toLong(),
                mood = state.mood.toLong(),
                stress = state.stress.toLong(),
                trained = if (state.trained) 1L else 0L,
                breakfast = state.breakfast,
                lunch = state.lunch,
                tea = state.tea,
                dinner = state.dinner,
                otherFood = state.otherFood,
                waterGlasses = state.waterGlasses.toLong(),
                musclePain = state.musclePain.toLong(),
                fatigue = state.fatigue.toLong(),
                libido = state.libido?.toLong(),
                morningErection = state.morningErection.name,
                deepStudyMinutes = state.deepStudyMinutes.toLong(),
                concentration = state.concentration.toLong(),
                createdAt = state.createdAt.toEpochMilliseconds(),
                updatedAt = state.updatedAt.toEpochMilliseconds(),
            )
        }

    override suspend fun deleteState(id: String) =
        withContext(Dispatchers.Default) {
            q.deleteDailyState(id)
        }

    private fun com.ataraxia.db.DailyStateEntry.toDomain() = DailyState(
        id = id,
        date = LocalDate.parse(date),
        energy = energy.toInt(),
        mood = mood.toInt(),
        stress = stress.toInt(),
        trained = trained != 0L,
        breakfast = breakfast,
        lunch = lunch,
        tea = tea,
        dinner = dinner,
        otherFood = otherFood,
        waterGlasses = waterGlasses.toInt(),
        musclePain = musclePain.toInt(),
        fatigue = fatigue.toInt(),
        libido = libido?.toInt(),
        morningErection = runCatching {
            MorningErection.valueOf(morningErection)
        }.getOrDefault(MorningErection.NOT_ANSWERED),
        deepStudyMinutes = deepStudyMinutes.toInt(),
        concentration = concentration.toInt(),
        createdAt = Instant.fromEpochMilliseconds(createdAt),
        updatedAt = Instant.fromEpochMilliseconds(updatedAt),
    )
}
