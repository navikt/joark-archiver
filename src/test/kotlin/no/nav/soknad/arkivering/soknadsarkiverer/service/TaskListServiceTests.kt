package no.nav.soknad.arkivering.soknadsarkiverer.service

import com.nhaarman.mockitokotlin2.*
import no.nav.soknad.arkivering.soknadsarkiverer.utils.createSoknadarkivschema
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import java.util.*

class TaskListServiceTests {

	private val schedulerMock = mock<SchedulerService>().also {
		whenever(it.schedule(anyString(), any(), anyInt(), any())).thenReturn(mock())
	}
	private val taskListService = TaskListService(schedulerMock, mock())

	@Test
	fun `Can list Tasks when there are none`() {
		assertTrue(taskListService.listTasks().isEmpty())
	}

	@Test
	fun `Can create task - will schedule`() {
		val uuid = UUID.randomUUID().toString()
		val value = createSoknadarkivschema()
		val count = 0

		taskListService.addOrUpdateTask(uuid, value, count)

		val tasks = taskListService.listTasks()
		assertEquals(1, tasks.size)
		assertEquals(value, tasks.fetch(uuid).first)
		assertEquals(count, tasks.fetch(uuid).second)

		verify(schedulerMock, times(1)).schedule(eq(uuid), eq(value), eq(count), any())
	}

	@Test
	fun `Can update Task`() {
		val uuid = UUID.randomUUID().toString()
		val value = createSoknadarkivschema()
		val countOriginal = 1
		val countUpdated = 2

		taskListService.addOrUpdateTask(uuid, value, countOriginal)
		assertEquals(countOriginal, taskListService.listTasks().fetch(uuid).second)

		taskListService.addOrUpdateTask(uuid, value, countUpdated)
		assertEquals(countUpdated, taskListService.listTasks().fetch(uuid).second)

		verify(schedulerMock, times(1)).schedule(eq(uuid), eq(value), eq(countOriginal), any())
		verify(schedulerMock, times(1)).schedule(eq(uuid), eq(value), eq(countUpdated), any())
	}

	@Test
	fun `Can create, update and finish task`() {
		val uuid = UUID.randomUUID().toString()
		val value = createSoknadarkivschema()
		val countOriginal = 1
		val countUpdated = 2

		taskListService.addOrUpdateTask(uuid, value, countOriginal)
		assertEquals(countOriginal, taskListService.listTasks().fetch(uuid).second)

		taskListService.addOrUpdateTask(uuid, value, countUpdated)
		assertEquals(countUpdated, taskListService.listTasks().fetch(uuid).second)

		taskListService.finishTask(uuid)
		assertTrue(taskListService.listTasks().isEmpty())

		verify(schedulerMock, times(1)).schedule(eq(uuid), eq(value), eq(countOriginal), any())
	}

	@Test
	fun `Finishing non-existent task will not produce exception`() {
		val nonExistentUuid = UUID.randomUUID().toString()

		taskListService.finishTask(nonExistentUuid)
		assertTrue(taskListService.listTasks().isEmpty())

		verify(schedulerMock, times(0)).schedule(anyString(), any(), anyInt(), any())
	}


	/**
	 * Infix helper function used to silence warnings that values could be null.
	 */
	private infix fun <K, V> Map<K, V>.fetch(key: K) = this[key] ?: error("Expected value")
}
