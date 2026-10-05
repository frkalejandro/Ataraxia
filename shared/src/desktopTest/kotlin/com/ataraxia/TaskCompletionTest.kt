package com.ataraxia

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.ataraxia.data.repository.TaskRepositoryImpl
import com.ataraxia.db.AtaraxiaDatabase
import com.ataraxia.domain.model.*
import com.ataraxia.domain.usecase.CompleteTaskUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.*
import java.nio.file.Files
import kotlin.test.*

class TaskCompletionTest {
    @Test fun canUncheckCompletedTaskAndKeepsItsDetailsAfterReopening() = runBlocking {
        val file = Files.createTempFile("ataraxia-task-test", ".db").toFile()
        val now = Instant.parse("2026-10-05T12:00:00Z")
        val task = Task(id = "task", title = "Entrenar", description = "Mi rutina",
            dueDate = LocalDate(2026, 10, 5), dueTime = LocalTime(18, 0), createdAt = now, updatedAt = now)
        try {
            JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}", schema = AtaraxiaDatabase.Schema).use { driver ->
                val repo = TaskRepositoryImpl(AtaraxiaDatabase(driver))
                val complete = CompleteTaskUseCase(repo)
                repo.saveTask(task)
                complete(task.id)
                assertTrue(repo.getTaskById(task.id)!!.isCompleted)
                assertNotNull(repo.getTaskById(task.id)!!.completedAt)
                assertTrue(repo.observeActiveTasks().first().isEmpty())
                complete(task.id, false)
                val reopened = repo.getTaskById(task.id)!!
                assertEquals(TaskStatus.TODO, reopened.status)
                assertNull(reopened.completedAt)
                assertEquals(task.title, reopened.title)
                assertEquals(task.description, reopened.description)
                assertEquals(task.dueTime, reopened.dueTime)
                assertEquals(task.id, repo.observeActiveTasks().first().single().id)
            }
            JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}", schema = AtaraxiaDatabase.Schema).use { driver ->
                val repo = TaskRepositoryImpl(AtaraxiaDatabase(driver))
                assertFalse(repo.observeTasksByDate(task.dueDate!!).first().single().isCompleted)
                CompleteTaskUseCase(repo)(task.id, true)
                assertTrue(repo.getTaskById(task.id)!!.isCompleted)
            }
        } finally { file.delete() }
    }
}
