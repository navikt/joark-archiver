package no.nav.soknad.arkivering.soknadsarkiverer

import com.fasterxml.jackson.databind.ObjectMapper
import com.nhaarman.mockitokotlin2.*
import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry
import no.nav.security.token.support.client.spring.ClientConfigurationProperties
import no.nav.soknad.arkivering.avroschemas.EventTypes
import no.nav.soknad.arkivering.avroschemas.ProcessingEvent
import no.nav.soknad.arkivering.avroschemas.Soknadarkivschema
import no.nav.soknad.arkivering.soknadsarkiverer.config.AppConfiguration
import no.nav.soknad.arkivering.soknadsarkiverer.kafka.KafkaPublisher
import no.nav.soknad.arkivering.soknadsarkiverer.service.TaskListService
import no.nav.soknad.arkivering.soknadsarkiverer.supervision.ArchivingMetrics
import no.nav.soknad.arkivering.soknadsarkiverer.utils.*
import org.junit.jupiter.api.*
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.ActiveProfiles
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.properties.Delegates

//@Disabled
@ActiveProfiles("test")
@SpringBootTest
@ConfigurationPropertiesScan("no.nav.soknad.arkivering", "no.nav.security.token")
@EnableConfigurationProperties(ClientConfigurationProperties::class)
class Application2Tests: TopologyTestDriverTests() {


	@Value("\${application.mocked-port-for-external-services}")
	private val portToExternalServices: Int? = null

	@Autowired
	private lateinit var appConfiguration: AppConfiguration

	@Autowired
	private lateinit var taskListService: TaskListService

	@Autowired
	private lateinit var objectMapper: ObjectMapper

	@Autowired
	private lateinit var metrics: ArchivingMetrics

	@MockBean
	private lateinit var kafkaPublisherMock: KafkaPublisher

	@MockBean
	private lateinit var clientConfigurationProperties: ClientConfigurationProperties

	private var maxNumberOfAttempts by Delegates.notNull<Int>()

	private val fileUuid = UUID.randomUUID().toString()
	private val key = UUID.randomUUID().toString()

	@BeforeEach
	fun setup() {
		setupMockedNetworkServices(portToExternalServices!!, appConfiguration.config.joarkUrl, appConfiguration.config.filestorageUrl)

		maxNumberOfAttempts = appConfiguration.config.retryTime.size
		Mockito.`when`(kafkaPublisherMock.putProcessingEventOnTopic(any(), eq(ProcessingEvent(EventTypes.STARTED)), any())).doAnswer {putDataOnProcessingTopic(key, ProcessingEvent(
			EventTypes.STARTED
		)
		)}
		Mockito.`when`(kafkaPublisherMock.putProcessingEventOnTopic(any(), eq(ProcessingEvent(EventTypes.ARCHIVED)), any())).doAnswer {putDataOnProcessingTopic(key, ProcessingEvent(
			EventTypes.ARCHIVED
		)
		)}
		Mockito.`when`(kafkaPublisherMock.putProcessingEventOnTopic(any(), eq(ProcessingEvent(EventTypes.FINISHED)), any())).doAnswer {putDataOnProcessingTopic(key, ProcessingEvent(
			EventTypes.FINISHED
		)
		)}
		Mockito.`when`(kafkaPublisherMock.putProcessingEventOnTopic(any(), eq(ProcessingEvent(EventTypes.FAILURE)), any())).doAnswer {putDataOnProcessingTopic(key, ProcessingEvent(
			EventTypes.FAILURE
		)
		)}


		setupKafkaTopologyTestDriver()
			.withAppConfiguration(appConfiguration)
			.withTaskListService(taskListService)
			.withKafkaPublisher(kafkaPublisherMock)
			.putProcessingEventLogsOnTopic()
			.setup(metrics)
	}

	@AfterEach
	fun teardown() {
		stopMockedNetworkServices()
		closeTestDriver()
		MockSchemaRegistry.dropScope(schemaRegistryScope)

		reset(kafkaPublisherMock)
		clearInvocations(kafkaPublisherMock)
	}

	@Test
	fun `First attempt to Joark fails, the second succeeds`() {
		val tasksBefore = metrics.getTasks()
		val tasksGivenUpOnBefore = metrics.getTasksGivenUpOn()
		val getFilestorageSuccessesBefore = metrics.getGetFilestorageSuccesses()
		val delFilestorageSuccessesBefore = metrics.getDelFilestorageSuccesses()
		val joarkSuccessesBefore = metrics.getJoarkSuccesses()
		val joarkErrorsBefore = metrics.getJoarkErrors()

		mockFilestorageIsWorking(fileUuid)
		mockJoarkRespondsAfterAttempts(1)

		putDataOnKafkaTopic(createSoknadarkivschema())
		TimeUnit.SECONDS.sleep(8)

		verifyProcessingEvents(1, EventTypes.STARTED)
		verifyProcessingEvents(1, EventTypes.ARCHIVED)
		verifyProcessingEvents(1, EventTypes.FINISHED)
		verifyMockedPostRequests(2, appConfiguration.config.joarkUrl)
		verifyDeleteRequestsToFilestorage(1)
		verifyMessageStartsWith(1, "Exception")
		verifyMessageStartsWith(1, "ok")
		verifyMetric(2, "get files from filestorage")
		verifyMetric(1, "send files to archive")
		verifyMetric(1, "delete files from filestorage")

		Assertions.assertEquals(getFilestorageSuccessesBefore + 2, metrics.getGetFilestorageSuccesses())
		Assertions.assertEquals(delFilestorageSuccessesBefore + 1, metrics.getDelFilestorageSuccesses())
		Assertions.assertEquals(joarkErrorsBefore + 1, metrics.getJoarkErrors())
		Assertions.assertEquals(joarkSuccessesBefore + 1, metrics.getJoarkSuccesses())
		Assertions.assertEquals(tasksBefore + 0, metrics.getTasks(), "Should have created and finished task")
		Assertions.assertEquals(
			tasksGivenUpOnBefore + 0,
			metrics.getTasksGivenUpOn(),
			"Should not have given up on any task"
		)
	}

	@Disabled // TODO finn ut hvorfor testen ikke kjører på GHA sammen med øvrige tester
	@Test
	fun `First attempt to Joark fails, the fourth succeeds`() {
		val attemptsToFail = 3
		mockFilestorageIsWorking(fileUuid)
		mockJoarkRespondsAfterAttempts(attemptsToFail)

		putDataOnKafkaTopic(createSoknadarkivschema())
		TimeUnit.SECONDS.sleep(8)

		verifyProcessingEvents(1, EventTypes.STARTED)
		verifyProcessingEvents(1, EventTypes.ARCHIVED)
		verifyProcessingEvents(1, EventTypes.FINISHED)
		verifyMockedPostRequests(attemptsToFail + 1, appConfiguration.config.joarkUrl)
		verifyDeleteRequestsToFilestorage(1)
		verifyMessageStartsWith(1, "ok")
		verifyMessageStartsWith(attemptsToFail, "Exception")
		verifyMetric(4, "get files from filestorage")
		verifyMetric(1, "send files to archive")
		verifyMetric(1, "delete files from filestorage")
	}

	@Disabled // TODO finn ut hvorfor testen ikke kjører på GHA sammen med øvrige tester
	@Test
	fun `Everything works, but Filestorage cannot delete files -- Message is nevertheless marked as finished`() {
		val getFilestorageSuccessesBefore = metrics.getGetFilestorageSuccesses()
		val delFilestorageSuccessesBefore = metrics.getDelFilestorageSuccesses()
		val delFilestorageErrorsBefore = metrics.getDelFilestorageErrors()
		val joarkSuccessesBefore = metrics.getJoarkSuccesses()
		val joarkErrorsBefore = metrics.getJoarkErrors()

		mockFilestorageIsWorking(fileUuid)
		mockFilestorageDeletionIsNotWorking()
		mockJoarkIsWorking()

		putDataOnKafkaTopic(createSoknadarkivschema())
		TimeUnit.SECONDS.sleep(8)

		verifyProcessingEvents(1, EventTypes.STARTED)
		verifyProcessingEvents(1, EventTypes.ARCHIVED)
		verifyProcessingEvents(1, EventTypes.FINISHED)
		verifyMockedPostRequests(1, appConfiguration.config.joarkUrl)
		verifyDeleteRequestsToFilestorage(1)
		verifyMessageStartsWith(1, "ok")
		verifyMessageStartsWith(0, "Exception")
		verifyMetric(1, "get files from filestorage")
		verifyMetric(1, "send files to archive")
		verifyMetric(1, "delete files from filestorage") // Metric succeeds even if the operation fails

		Assertions.assertEquals(getFilestorageSuccessesBefore + 1, metrics.getGetFilestorageSuccesses())
		Assertions.assertEquals(delFilestorageSuccessesBefore + 0, metrics.getDelFilestorageSuccesses())
		Assertions.assertEquals(delFilestorageErrorsBefore + 1, metrics.getDelFilestorageErrors())
		Assertions.assertEquals(joarkErrorsBefore + 0, metrics.getJoarkErrors())
		Assertions.assertEquals(joarkSuccessesBefore + 1, metrics.getJoarkSuccesses())
	}

	@Disabled // TODO finn ut hvorfor testen ikke kjører på GHA sammen med øvrige tester
	@Test
	fun `Joark responds with status OK but invalid body -- will retry`() {
		mockFilestorageIsWorking(fileUuid)
		mockJoarkIsWorkingButGivesInvalidResponse()

		putDataOnKafkaTopic(createSoknadarkivschema())
		TimeUnit.SECONDS.sleep(8)

		verifyProcessingEvents(1, EventTypes.RECEIVED)
		verifyProcessingEvents(1, EventTypes.STARTED)
		verifyProcessingEvents(0, EventTypes.ARCHIVED)
		verifyProcessingEvents(0, EventTypes.FINISHED)
		verifyProcessingEvents(1, EventTypes.FAILURE)
		verifyMockedPostRequests(maxNumberOfAttempts, appConfiguration.config.joarkUrl)
		verifyDeleteRequestsToFilestorage(0)
		verifyMessageStartsWith(maxNumberOfAttempts, "Exception")
		verifyMessageStartsWith(0, "ok")
		verifyMetric(maxNumberOfAttempts, "get files from filestorage")
		verifyMetric(0, "send files to archive")
		verifyMetric(0, "delete files from filestorage")
	}



	@Test
	fun `Application already archived will cause finishing archiving`() {
		val tasksBefore = metrics.getTasks()
		val tasksGivenUpOnBefore = metrics.getTasksGivenUpOn()
		val getFilestorageErrorsBefore = metrics.getGetFilestorageErrors()
		val getFilestorageSuccessesBefore = metrics.getGetFilestorageSuccesses()
		val delFilestorageSuccessesBefore = metrics.getDelFilestorageSuccesses()
		val joarkSuccessesBefore = metrics.getJoarkSuccesses()
		val joarkErrorsBefore = metrics.getJoarkErrors()

		mockFilestorageIsWorking(fileUuid)
		mockAlreadyArchivedResponse(1)

		putDataOnKafkaTopic(createSoknadarkivschema())
		TimeUnit.SECONDS.sleep(8)
		verifyProcessingEvents(1, EventTypes.STARTED)
		verifyProcessingEvents(1, EventTypes.ARCHIVED)
		verifyProcessingEvents(1, EventTypes.FINISHED)
		verifyProcessingEvents(0, EventTypes.FAILURE)
		verifyDeleteRequestsToFilestorage(1)
		verifyMessageStartsWith(1, "ok")
		verifyMetric(1, "get files from filestorage")
		verifyMetric(0, "send files to archive")
		verifyMetric(1, "delete files from filestorage")

		Assertions.assertEquals(getFilestorageErrorsBefore + 0, metrics.getGetFilestorageErrors())
		Assertions.assertEquals(getFilestorageSuccessesBefore + 1, metrics.getGetFilestorageSuccesses())
		Assertions.assertEquals(delFilestorageSuccessesBefore + 1, metrics.getDelFilestorageSuccesses())
		Assertions.assertEquals(joarkErrorsBefore + 0, metrics.getJoarkErrors())
		Assertions.assertEquals(joarkSuccessesBefore + 0, metrics.getJoarkSuccesses())
		Assertions.assertEquals(tasksBefore, metrics.getTasks())
		Assertions.assertEquals(tasksGivenUpOnBefore, metrics.getTasksGivenUpOn())

	}

	private fun verifyMessageStartsWith(expectedCount: Int, message: String, key: String = this.key) {
		verifyMessageStartsWithSupport(kafkaPublisherMock, expectedCount, message, key)
	}

	private fun verifyMetric(expectedCount: Int, metric: String, key: String = this.key) {
		verifyMetricSupport(kafkaPublisherMock, expectedCount, metric, key)
	}

	private fun verifyProcessingEvents(expectedCount: Int, eventType: EventTypes) {
		verifyProcessingEventsSupport(kafkaPublisherMock, expectedCount, eventType, key)
	}

	private fun putDataOnKafkaTopic(data: Soknadarkivschema) {
		putDataOnInputTopic(key, data)
	}

	private fun verifyDeleteRequestsToFilestorage(expectedCount: Int) {
		verifyMockedDeleteRequests(expectedCount, appConfiguration.config.filestorageUrl.replace("?", "\\?") + ".*")
	}

	private fun createSoknadarkivschema() = createSoknadarkivschema(fileUuid)

}
