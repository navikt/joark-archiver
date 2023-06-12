package no.nav.soknad.arkivering.soknadsarkiverer.service

import io.mockk.*
import io.prometheus.client.CollectorRegistry
import no.nav.soknad.arkivering.soknadsarkiverer.config.ArchivingException
import no.nav.soknad.arkivering.soknadsarkiverer.kafka.KafkaPublisher
import no.nav.soknad.arkivering.soknadsarkiverer.service.arkivservice.JournalpostClientInterface
import no.nav.soknad.arkivering.soknadsarkiverer.service.fileservice.FetchFileResponse
import no.nav.soknad.arkivering.soknadsarkiverer.service.fileservice.FilestorageService
import no.nav.soknad.arkivering.soknadsarkiverer.service.fileservice.InnsendingService
import no.nav.soknad.arkivering.soknadsarkiverer.supervision.ArchivingMetrics
import no.nav.soknad.arkivering.soknadsarkiverer.utils.createSoknadarkivschema
import no.nav.soknad.arkivering.soknadsfillager.model.FileData
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import java.time.OffsetDateTime.now
import java.util.*

class ArchiverServiceTests {

	private val filestorage = mockk<FilestorageService>().also {
		every {
		it.getFilesFromFilestorage(any(), any()) } returns FetchFileResponse(status = "ok",
			listOf(FileData("id", "content".toByteArray(), now(), "ok")), exception = null)
	}
	private val filestorageNotFound = mockk<FilestorageService>().also {
		every {
			it.getFilesFromFilestorage(any(), any()) } returns FetchFileResponse(status = "not-found",
			files = null, exception = null)
	}
	private val filestorageDeleted = mockk<FilestorageService>().also {
		every {
			it.getFilesFromFilestorage(any(), any()) } returns FetchFileResponse(status = "deleted",
			files = null, exception = null)
	}
	private val filestorageException = mockk<FilestorageService>().also {
		every {
			it.getFilesFromFilestorage(any(), any()) } returns FetchFileResponse(status = "exception",
			files = null, exception = RuntimeException("En feil har oppstått"))
	}
	private val innsendingApi = mockk<InnsendingService>().also {
		every {
			it.getFilesFromFilestorage(any(), any()) } returns FetchFileResponse(status = "ok",
			listOf(FileData("id", "content".toByteArray(), now(), "ok")), exception = null)
	}
	private val innsendingApiNotFound = mockk<InnsendingService>().also {
		every {
			it.getFilesFromFilestorage(any(), any()) } returns FetchFileResponse(status = "not-found",
			files = null, exception = null)
	}
	private val innsendingApiDeleted = mockk<InnsendingService>().also {
		every {
			it.getFilesFromFilestorage(any(), any()) } returns FetchFileResponse(status = "deleted",
			files = null, exception = null)
	}
	private val innsendingApiException = mockk<InnsendingService>().also {
		every {
			it.getFilesFromFilestorage(any(), any()) } returns FetchFileResponse(status = "exception",
			files = null, exception = RuntimeException("En feil har oppstått"))
	}


	private val journalpostClient = mockk<JournalpostClientInterface>().also {
		every { it.opprettJournalpost(any(), any(), any()) } returns UUID.randomUUID().toString()
	}
	@Autowired
	private lateinit var metrics: ArchivingMetrics

	private val kafkaPublisher = mockk<KafkaPublisher>().also {
		every { it.putMetricOnTopic(any(), any(), any()) } just Runs
		every { it.putMessageOnTopic(any(), any(), any()) } just Runs
	}

	private val key = UUID.randomUUID().toString()

	private lateinit var archiverService: ArchiverService

	@BeforeEach
	fun setup() {
		metrics = ArchivingMetrics(CollectorRegistry())
	}

	@Test
	fun `Archiving already archived application throws exception`() {
		archiverService = ArchiverService(filestorage, innsendingApiNotFound, journalpostClient, metrics,  kafkaPublisher)

		val key2 = UUID.randomUUID().toString()
		mockAlreadyArchivedException(key2)

		val soknadschema = createSoknadarkivschema()
		assertThrows<ApplicationAlreadyArchivedException> {
			archiverService.archive(key2, soknadschema, archiverService.fetchFiles(key, soknadschema))
		}
	}

	@Test
	fun `Archiving succeeds when all is up and running`() {
		archiverService = ArchiverService(filestorageNotFound, innsendingApi, journalpostClient, metrics, kafkaPublisher)
		val key = UUID.randomUUID().toString()
		val soknadschema = createSoknadarkivschema()

		archiverService.archive(key, soknadschema, archiverService.fetchFiles(key, soknadschema))

		verify(exactly = 1) { filestorageNotFound.getFilesFromFilestorage(eq(key), eq(soknadschema)) }
		verify(exactly = 1) { innsendingApi.getFilesFromFilestorage(eq(key), eq(soknadschema)) }
		verify(exactly = 1) { journalpostClient.opprettJournalpost(eq(key), eq(soknadschema), any()) }
	}

	@Test
	fun `Archiving fails when no files is found`() {
		archiverService = ArchiverService(filestorageNotFound, innsendingApiNotFound, journalpostClient, metrics, kafkaPublisher)

		val key = UUID.randomUUID().toString()
		val soknadschema = createSoknadarkivschema()
		assertThrows<ArchivingException> {
			archiverService.archive(key, soknadschema, archiverService.fetchFiles(key, soknadschema))
		}

	}

	private fun mockAlreadyArchivedException(key: String) {
		every { journalpostClient.opprettJournalpost(eq(key), any(), any()) } throws ApplicationAlreadyArchivedException("Already archived")
	}
}
