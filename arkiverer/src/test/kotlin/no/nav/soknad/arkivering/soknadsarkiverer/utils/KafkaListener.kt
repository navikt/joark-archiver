package no.nav.soknad.arkivering.soknadsarkiverer.utils

import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde
import no.nav.soknad.arkivering.avroschemas.InnsendingMetrics
import no.nav.soknad.arkivering.avroschemas.ProcessingEvent
import no.nav.soknad.arkivering.soknadsarkiverer.config.AppConfiguration
import org.apache.avro.specific.SpecificRecord
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.errors.LogAndContinueExceptionHandler
import org.apache.kafka.streams.kstream.Consumed
import org.slf4j.LoggerFactory
import java.util.*

class KafkaListener(private val kafkaConfig: AppConfiguration.KafkaConfig) {

	private val logger = LoggerFactory.getLogger(javaClass)
	private val verbose = true

	private val metricsReceived          = mutableListOf<Pair<Key, InnsendingMetrics>>()
	private val messagesReceived         = mutableListOf<Pair<Key, String>>()
	private val processingEventsReceived = mutableListOf<Pair<Key, ProcessingEvent>>()

	private val kafkaStreams: KafkaStreams

	private val stringSerde = Serdes.StringSerde()

	init {
		val streamsBuilder = StreamsBuilder()
		kafkaStreams(streamsBuilder)
		val topology = streamsBuilder.build()

		val kafkaConfig = kafkaConfig()
		kafkaStreams = KafkaStreams(topology, kafkaConfig)
		kafkaStreams.start()
		Runtime.getRuntime().addShutdownHook(Thread(kafkaStreams::close))
	}


	private fun kafkaStreams(streamsBuilder: StreamsBuilder) {
		val metricsStream              = streamsBuilder.stream(kafkaConfig.metricsTopic,    Consumed.with(stringSerde, createInnsendingMetricsSerde()))
		val processingEventTopicStream = streamsBuilder.stream(kafkaConfig.processingTopic, Consumed.with(stringSerde, createProcessingEventSerde()))
		val messagesStream             = streamsBuilder.stream(kafkaConfig.messageTopic,    Consumed.with(stringSerde, stringSerde))


		metricsStream
			.peek { key, metrics -> log("$key: Metrics received  - $metrics") }
			.foreach { key, metrics -> metricsReceived.add(key to metrics) }

		messagesStream
			.peek { key, message -> log("$key: Message received  - $message") }
			.foreach { key, message -> messagesReceived.add(key to message) }

		processingEventTopicStream
			.peek { key, entity -> log("$key: Processing Events - $entity") }
			.foreach { key, entity -> processingEventsReceived.add(key to entity) }
	}

	private fun log(message: String) {
		if (verbose)
			logger.info(message)
	}

	private fun kafkaConfig() = Properties().also {
		it[AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG] = kafkaConfig.schemaRegistryUrl
		it[StreamsConfig.APPLICATION_ID_CONFIG] = "soknadarkiverer-tests"
		it[StreamsConfig.BOOTSTRAP_SERVERS_CONFIG] = kafkaConfig.servers
		it[StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG] = Serdes.StringSerde::class.java
		it[StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG] = SpecificAvroSerde::class.java
		it[StreamsConfig.DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG] = LogAndContinueExceptionHandler::class.java
		it[StreamsConfig.COMMIT_INTERVAL_MS_CONFIG] = 1000

		if (kafkaConfig.secure == "TRUE") {
			it[CommonClientConfigs.SECURITY_PROTOCOL_CONFIG] = kafkaConfig.protocol
			it[SaslConfigs.SASL_JAAS_CONFIG] = kafkaConfig.saslJaasConfig
			it[SaslConfigs.SASL_MECHANISM] = kafkaConfig.salsmec
		}
	}

	private fun createProcessingEventSerde(): SpecificAvroSerde<ProcessingEvent> = createAvroSerde()
	private fun createInnsendingMetricsSerde(): SpecificAvroSerde<InnsendingMetrics> = createAvroSerde()

	private fun <T : SpecificRecord> createAvroSerde(): SpecificAvroSerde<T> {
		val serdeConfig =
			hashMapOf(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG to kafkaConfig.schemaRegistryUrl)
		return SpecificAvroSerde<T>().also { it.configure(serdeConfig, false) }
	}


	fun close() {
		kafkaStreams.close()
		kafkaStreams.cleanUp()
	}


	fun getMetrics() = metricsReceived.map { Record(it.first, it.second) }
	fun getMessages() = messagesReceived.map { Record(it.first, it.second) }
	fun getProcessingEvents() = processingEventsReceived.map { Record(it.first, it.second) }

	data class Record<T>(val key: Key, val value: T)
}

typealias Key = String