package no.nav.soknad.arkivering.soknadsarkiverer.supervision

import io.prometheus.client.CollectorRegistry
import no.nav.security.token.support.client.spring.ClientConfigurationProperties
import no.nav.soknad.arkivering.soknadsarkiverer.config.AppConfiguration
import no.nav.soknad.arkivering.soknadsarkiverer.service.arkivservice.JournalpostClientInterface
import no.nav.soknad.arkivering.soknadsarkiverer.service.fileservice.FileserviceInterface
import no.nav.soknad.arkivering.soknadsarkiverer.utils.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("test")
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ConfigurationPropertiesScan("no.nav.soknad.arkivering", "no.nav.security.token")
@EnableConfigurationProperties(ClientConfigurationProperties::class)
class HealthCheckTests {

	@Value("\${application.mocked-port-for-external-services}")
	private val portToExternalServices: Int? = null

	@Suppress("unused")
	@MockBean
	private lateinit var clientConfigurationProperties: ClientConfigurationProperties

	@Suppress("unused")
	@MockBean
	private lateinit var collectorRegistry: CollectorRegistry

	@Autowired
	private lateinit var filestorage: FileserviceInterface

	@Autowired
	private lateinit var journalpostClient: JournalpostClientInterface

	@Autowired
	private lateinit var metrics: ArchivingMetrics

	private val appConfiguration = AppConfiguration()
	private lateinit var healthCheck: HealthCheck

	@BeforeEach
	fun setup() {
		setupMockedNetworkServices(portToExternalServices!!, appConfiguration.config.joarkUrl, appConfiguration.config.filestorageUrl)

		mockFilestoragePingIsWorking()
		mockFilestorageIsReadyIsWorking()
		mockJoarkIsAliveIsWorking()

		healthCheck = HealthCheck(appConfiguration, filestorage, journalpostClient, metrics)
	}

	@AfterEach
	fun cleanup() {
		stopMockedNetworkServices()
	}


	@Test
	fun `isAlive returns Ok when application is well`() {
		appConfiguration.state.alive = true

		val response = healthCheck.isAlive()

		assertEquals(ResponseEntity<String>(HttpStatus.OK), response)
	}

	@Test
	fun `isAlive returns Status 500 when application is unwell`() {
		appConfiguration.state.alive = false

		val response = healthCheck.isAlive()

		assertEquals(ResponseEntity<String>("Application is not alive", HttpStatus.INTERNAL_SERVER_ERROR), response)
	}


	@Test
	fun `isReady returns Ok when application and dependencies are well`() {
		appConfiguration.state.ready = true

		val response = healthCheck.isReady()

		assertEquals(ResponseEntity<String>(HttpStatus.OK), response)
	}

	@Test
	fun `isReady returns Status 500 when application is unwell`() {
		appConfiguration.state.ready = false

		val response = healthCheck.isReady()

		assertEquals(ResponseEntity<String>("Application is not ready", HttpStatus.INTERNAL_SERVER_ERROR), response)
	}

	@Test
	fun `isReady returns Status 500 when application is stopping`() {
		healthCheck.stop()

		val response = healthCheck.isReady()

		assertEquals(ResponseEntity<String>("Application is not ready", HttpStatus.INTERNAL_SERVER_ERROR), response)
	}

	@Test
	fun `isReady returns Status 500 when Filestorage is unwell`() {
		mockFilestorageIsReadyIsNotWorking()

		val response = healthCheck.isReady()

		val expected = ResponseEntity<String>(
			"Application is not ready: Server error : 500 Server Error",
			HttpStatus.INTERNAL_SERVER_ERROR
		)
		assertEquals(expected, response)
	}

	@Test
	fun `isReady returns Status 500 when Joark is unwell`() {
		mockJoarkIsAliveIsNotWorking()

		val response = healthCheck.isReady()

		val expected = ResponseEntity<String>(
			"Application is not ready: 500 Internal Server Error from GET http://localhost:2908/isAlive",
			HttpStatus.INTERNAL_SERVER_ERROR
		)
		assertEquals(expected, response)
	}


	@Test
	fun `ping returns Pong when dependencies are well`() {
		assertEquals(ResponseEntity<String>("pong", HttpStatus.OK), healthCheck.ping())
	}

	@Test
	fun `ping returns Status 500 when Filestorage is unwell`() {
		mockFilestoragePingIsNotWorking()

		val response = healthCheck.ping()

		val expected = ResponseEntity<String>(
			"Ping failed: Server error : 500 Server Error",
			HttpStatus.INTERNAL_SERVER_ERROR
		)
		assertEquals(expected, response)
	}

	@Test
	fun `ping returns Status 500 when Joark is unwell`() {
		mockJoarkIsAliveIsNotWorking()

		val response = healthCheck.ping()

		val expected = ResponseEntity<String>(
			"Ping failed: 500 Internal Server Error from GET http://localhost:2908/isAlive",
			HttpStatus.INTERNAL_SERVER_ERROR
		)
		assertEquals(expected, response)
	}
}
