package no.nav.soknad.arkivering.soknadsarkiverer.config

import no.nav.soknad.arkivering.dto.ArchivalData
import no.nav.soknad.arkivering.soknadsarkiverer.converter.MessageConverter
import no.nav.soknad.arkivering.soknadsarkiverer.service.FileStorageRetrievingService
import no.nav.soknad.arkivering.soknadsarkiverer.service.JoarkArchiver
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.Topology
import org.apache.kafka.streams.errors.DeserializationExceptionHandler
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.processor.ProcessorContext
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafkaStreams
import java.util.*
import java.util.concurrent.TimeUnit

@EnableKafkaStreams
@Configuration
class KafkaConsumerConfig(val applicationProperties: ApplicationProperties,
													val fileStorageRetrievingService: FileStorageRetrievingService,
													val messageConverter: MessageConverter,
													val joarkArchiver: JoarkArchiver) {

	@Bean
	fun kafkaConfig() = Properties().also {
//		it[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
//		it[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = JsonDeserializer::class.java

		it[StreamsConfig.APPLICATION_ID_CONFIG] = "soknadsarkiverer"
		it[StreamsConfig.BOOTSTRAP_SERVERS_CONFIG] = applicationProperties.kafkaBootstrapServers
		it[StreamsConfig.DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG] = KafkaExceptionHandler::class.java
	}

	@Bean
	fun mainTopology(streamsBuilder: StreamsBuilder): Topology {
		kafkaStreamTopology(streamsBuilder)
//		kafkaRetryTopology(streamsBuilder)
		return streamsBuilder.build()
	}

	fun kafkaStreamTopology(streamsBuilder: StreamsBuilder) {
		val stream = streamsBuilder.stream<String, ArchivalData>(applicationProperties.kafkaTopic, Consumed.with(Serdes.String(), ArchivalDataSerde()))
		setupTopology(stream)
	}

	fun kafkaRetryTopology(streamsBuilder: StreamsBuilder) {
		val stream = streamsBuilder.stream<String, ArchivalData>("retry", Consumed.with(Serdes.String(), ArchivalDataSerde()))
		setupTopology(stream)
	}

	private fun setupTopology(ksteam: KStream<String, ArchivalData>) {

//		streamsBuilder.stream<String, ArchivalData>(topic, Consumed.with(Serdes.String(), ArchivalDataSerde()))
//		.peek( sleep())
		ksteam
			.mapValues { archivalData -> archivalData to fileStorageRetrievingService.getFilesFromFileStorage(archivalData) }
			.mapValues { (archivalData, files) -> messageConverter.createJoarkData(archivalData, files) }
			.foreach { _, joarkData -> joarkArchiver.putDataInJoark(joarkData) }
	}

	@Bean
	fun kStream(topology: Topology, kafkaExceptionHandler: KafkaExceptionHandler): KafkaStreams {
		val kafkaStreams = KafkaStreams(topology, kafkaConfig())
		kafkaStreams.setUncaughtExceptionHandler(kafkaExceptionHandler)
		kafkaStreams.start()
		Runtime.getRuntime().addShutdownHook(Thread(kafkaStreams::close))
		return kafkaStreams
	}

	@Bean
	fun kafkaExceptionHandler() = KafkaExceptionHandler()
}

class KafkaExceptionHandler : Thread.UncaughtExceptionHandler, DeserializationExceptionHandler {
	private val topic = "dlq"
	private val logger = LoggerFactory.getLogger(javaClass)

	override fun handle(context: ProcessorContext, record: ConsumerRecord<ByteArray, ByteArray>, exception: Exception): DeserializationExceptionHandler.DeserializationHandlerResponse {
		logger.error("Exception when deserializing Kafka message", exception)

		try {
			val metadata = kafkaProducer().use { it.send(ProducerRecord(topic, record.key(), record.value())).get(1000, TimeUnit.MILLISECONDS) }
			logger.info("Put message on DLQ on offset ${metadata.offset()}")

		} catch (e: Exception) {
			logger.error("Exception when trying to message that could not be deserialised to topic '$topic'", exception)
			return DeserializationExceptionHandler.DeserializationHandlerResponse.FAIL
		}

		return DeserializationExceptionHandler.DeserializationHandlerResponse.CONTINUE
	}

	override fun configure(configs: MutableMap<String, *>) {
	}

	override fun uncaughtException(t: Thread, e: Throwable) {
		// TODO: Put on retry topic. How to get the event, though?
		logger.error("uncaughtException '$e'")
	}


	@Bean
	fun kafkaProducer() = KafkaProducer<ByteArray, ByteArray>(kafkaConfigMap())

	private fun kafkaConfigMap(): MutableMap<String, Any> {
		return HashMap<String, Any>().also {
			it[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = "localhost:3333"
			it[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = ByteArraySerializer::class.java
			it[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = ByteArraySerializer::class.java
		}
	}
}
