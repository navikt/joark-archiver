package no.nav.soknad.arkivering.soknadsarkiverer.supervision

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.security.token.support.core.api.Unprotected
import no.nav.soknad.arkivering.soknadsarkiverer.config.AppConfiguration
import no.nav.soknad.arkivering.soknadsarkiverer.config.isBusy
import no.nav.soknad.arkivering.soknadsarkiverer.config.stop
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(value = ["/internal"])
class HealthCheck(private val appConfiguration: AppConfiguration) {

	private val logger = LoggerFactory.getLogger(javaClass)

	@GetMapping("/isAlive")
	@Unprotected
	fun isAlive() = "Ok"

	@GetMapping("/ping")
	@Unprotected
	fun ping() = "pong"

	@GetMapping("/isReady")
	@Unprotected
	fun isReady() = "Ready for actions"

	@GetMapping("/stop")
	@Unprotected
	fun stop() = runBlocking {
		launch {
			while (isBusy(appConfiguration)) {
				logger.info("Waiting for shutdown")
				delay(1000L)
			}
			logger.info("POD is ready for shutdown")
		}
		logger.info("Get ready for shutdown")
	}

}
