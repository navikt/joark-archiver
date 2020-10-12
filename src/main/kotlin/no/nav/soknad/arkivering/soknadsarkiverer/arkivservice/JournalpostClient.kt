package no.nav.soknad.arkivering.soknadsarkiverer.arkivservice

import no.nav.soknad.arkivering.avroschemas.Soknadarkivschema
import no.nav.soknad.arkivering.soknadsarkiverer.arkivservice.api.OpprettJournalpostRequest
import no.nav.soknad.arkivering.soknadsarkiverer.arkivservice.api.OpprettJournalpostResponse
import no.nav.soknad.arkivering.soknadsarkiverer.arkivservice.converter.createOpprettJournalpostRequest
import no.nav.soknad.arkivering.soknadsarkiverer.config.AppConfiguration
import no.nav.soknad.arkivering.soknadsarkiverer.config.ArchivingException
import no.nav.soknad.arkivering.soknadsarkiverer.dto.FilElementDto
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient

@Service
class JournalpostClient(private val appConfiguration: AppConfiguration,
												@Qualifier("archiveWebClient") private val webClient: WebClient): JournalpostClientInterface {

	private val logger = LoggerFactory.getLogger(javaClass)

	override fun ping(): String {
		return webClient
			.get()
			.uri("${appConfiguration.config.joarkUrl}/ping")
			.retrieve()
			.bodyToMono(String::class.java)
			.block()!!
	}

	override fun opprettJournalpost(key: String, soknadarkivschema: Soknadarkivschema, attachedFiles: List<FilElementDto>): String {
		try {
			logger.info("$key: Creating journalpost with behandlingsId=${soknadarkivschema.getBehandlingsid()}")
			val request: OpprettJournalpostRequest = createOpprettJournalpostRequest(soknadarkivschema, attachedFiles)

			val url = appConfiguration.config.joarkHost + appConfiguration.config.joarkUrl

			val response = sendDataToJoark(request, url)
			val journalpostId = response?.journalpostId ?: "-1"

			logger.info("$key: Saved to Joark, got the following journalpostId: '$journalpostId'")
			return journalpostId

		} catch (e: Exception) {
			logger.error("$key: Error sending to Joark", e)
			throw ArchivingException(e)
		}
	}

	private fun sendDataToJoark(data: OpprettJournalpostRequest, uri: String) =
		webClient
			.post()
			.uri(uri)
			.contentType(APPLICATION_JSON)
			.accept(APPLICATION_JSON)
			.body(BodyInserters.fromValue(data)) // TODO: Add test that checks that the body is as expected
			.retrieve()
			.bodyToMono(OpprettJournalpostResponse::class.java)
			.block()
}
