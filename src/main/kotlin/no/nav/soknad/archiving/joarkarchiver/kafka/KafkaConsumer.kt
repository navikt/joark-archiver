package no.nav.soknad.archiving.joarkarchiver.kafka

import no.nav.soknad.archiving.dto.ArchivalData
import no.nav.soknad.archiving.joarkarchiver.service.JoarkArchiver
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service

@Service
class KafkaConsumer(private val joarkArchiver: JoarkArchiver) {
	private val logger = LoggerFactory.getLogger(javaClass)

	@KafkaListener(topics = ["archival"])
	fun receiveFromKafka(archivalData: ArchivalData) {
		logger.info("Received message: '$archivalData'")
		joarkArchiver.archive(archivalData)
	}
}
