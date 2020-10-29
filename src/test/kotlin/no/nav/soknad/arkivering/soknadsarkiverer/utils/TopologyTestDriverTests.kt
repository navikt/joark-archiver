package no.nav.soknad.arkivering.soknadsarkiverer.utils

import com.nhaarman.mockitokotlin2.*
import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde
import no.nav.soknad.arkivering.avroschemas.EventTypes
import no.nav.soknad.arkivering.avroschemas.ProcessingEvent
import no.nav.soknad.arkivering.avroschemas.Soknadarkivschema
import no.nav.soknad.arkivering.soknadsarkiverer.config.AppConfiguration
import no.nav.soknad.arkivering.soknadsarkiverer.config.Scheduler
import no.nav.soknad.arkivering.soknadsarkiverer.kafka.KAFKA_PUBLISHER
import no.nav.soknad.arkivering.soknadsarkiverer.kafka.KafkaConfig
import no.nav.soknad.arkivering.soknadsarkiverer.kafka.KafkaExceptionHandler
import no.nav.soknad.arkivering.soknadsarkiverer.kafka.KafkaPublisher
import no.nav.soknad.arkivering.soknadsarkiverer.service.TaskListService
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.TestInputTopic
import org.apache.kafka.streams.TopologyTestDriver
import org.mockito.ArgumentCaptor
import java.util.*

open class TopologyTestDriverTests {

	private lateinit var testDriver: TopologyTestDriver

	private lateinit var inputTopic: TestInputTopic<String, Soknadarkivschema>
	private lateinit var inputTopicForBadData: TestInputTopic<String, String>
	private lateinit var processingEventTopic: TestInputTopic<String, ProcessingEvent>

	private fun setupKafkaTopologyTestDriver(appConfiguration: AppConfiguration, taskListService: TaskListService, kafkaPublisher: KafkaPublisher) {
		val builder = StreamsBuilder()
		KafkaConfig(appConfiguration, taskListService, kafkaPublisher).kafkaStreams(builder)
		val topology = builder.build()

		// Dummy properties needed for test diver
		val props = Properties().also {
			it[StreamsConfig.APPLICATION_ID_CONFIG] = "test"
			it[StreamsConfig.BOOTSTRAP_SERVERS_CONFIG] = "dummy:1234"
			it[StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG] = Serdes.StringSerde::class.java
			it[StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG] = SpecificAvroSerde::class.java
			it[StreamsConfig.DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG] = KafkaExceptionHandler::class.java
			it[AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG] = appConfiguration.kafkaConfig.schemaRegistryUrl
			it[KAFKA_PUBLISHER] = kafkaPublisher
		}

		// Create test driver
		testDriver = TopologyTestDriver(topology, props)
		val schemaRegistry = MockSchemaRegistry.getClientForScope(schemaRegistryScope)

		// Create Serdes used for test record keys and values
		val stringSerde = Serdes.String()
		val avroSoknadarkivschemaSerde = SpecificAvroSerde<Soknadarkivschema>(schemaRegistry)
		val avroProcessingEventSerde = SpecificAvroSerde<ProcessingEvent>(schemaRegistry)

		// Configure Serdes to use the same mock schema registry URL
		val config = hashMapOf(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG to appConfiguration.kafkaConfig.schemaRegistryUrl)
		avroSoknadarkivschemaSerde.configure(config, false)
		avroProcessingEventSerde.configure(config, false)

		// Define input and output topics to use in tests
		inputTopic = testDriver.createInputTopic(appConfiguration.kafkaConfig.inputTopic, stringSerde.serializer(), avroSoknadarkivschemaSerde.serializer())
		inputTopicForBadData = testDriver.createInputTopic(appConfiguration.kafkaConfig.inputTopic, stringSerde.serializer(), stringSerde.serializer())
		processingEventTopic = testDriver.createInputTopic(appConfiguration.kafkaConfig.processingTopic, stringSerde.serializer(), avroProcessingEventSerde.serializer())
	}

	fun closeTestDriver() {
		testDriver.close()
	}


	fun putDataOnInputTopic(key: String, value: Soknadarkivschema) {
		inputTopic.pipeInput(key, value)
	}

	fun putBadDataOnInputTopic(key: String, value: String) {
		inputTopicForBadData.pipeInput(key, value)
	}

	fun putDataOnProcessingTopic(key: String, value: ProcessingEvent) {
		processingEventTopic.pipeInput(key, value)
	}


	inner class TopologyTestDriverBuilder {
		private var appConfiguration = createAppConfiguration()
		private lateinit var taskListService: TaskListService
		private lateinit var kafkaPublisher: KafkaPublisher

		fun withAppConfiguration(appConfiguration: AppConfiguration): TaskListServiceStep {
			this.appConfiguration = appConfiguration
			return TaskListServiceStep()
		}

		inner class TaskListServiceStep {
			fun withTaskListService(theTaskListService: TaskListService): KafkaPublisherStep {
				taskListService = theTaskListService
				return KafkaPublisherStep()
			}

			fun withMockedTaskListService(): KafkaPublisherStep {
				taskListService = mock()
				return KafkaPublisherStep()
			}
		}

		inner class KafkaPublisherStep {
			fun withKafkaPublisher(theKafkaPublisher: KafkaPublisher): BehaviourStep {
				kafkaPublisher = theKafkaPublisher
				return BehaviourStep()
			}

			fun withMockedKafkaPublisher(): BehaviourStep {
				kafkaPublisher = mock()
				return BehaviourStep()
			}
		}

		inner class BehaviourStep {
			fun runScheduledTasksOnScheduling(scheduler: Scheduler): BehaviourStep {
				val captor = argumentCaptor<() -> Unit>()
				whenever(scheduler.schedule(capture(captor), any()))
					.then { captor.value.invoke() }
				return this
			}

			fun putProcessingEventLogsOnTopic(): BehaviourStep {
				putProcessingEventLogOnTopic(EventTypes.RECEIVED)
				putProcessingEventLogOnTopic(EventTypes.STARTED)
				putProcessingEventLogOnTopic(EventTypes.FINISHED)
				return this
			}

			private fun putProcessingEventLogOnTopic(eventType: EventTypes) {
				val captor = argumentCaptor<String>()
				whenever(kafkaPublisher.putProcessingEventOnTopic(capture(captor), eq(ProcessingEvent(eventType)), any()))
					.then { processingEventTopic.pipeInput(captor.value, ProcessingEvent(eventType)) }
			}

			fun setup() {
				setupKafkaTopologyTestDriver(appConfiguration, taskListService, kafkaPublisher)
			}
		}

		private inline fun <reified T> argumentCaptor(): ArgumentCaptor<T> = ArgumentCaptor.forClass(T::class.java)
	}

	fun setupKafkaTopologyTestDriver() = TopologyTestDriverBuilder()
}

fun createAppConfiguration() = AppConfiguration(AppConfiguration.KafkaConfig(
		inputTopic = "inputTopic",
		processingTopic = "processingTopic",
		schemaRegistryUrl = schemaRegistryUrl,
		servers = "bootstrapServers"))


const val schemaRegistryScope: String = "mocked-scope"
const val schemaRegistryUrl = "mock://$schemaRegistryScope"
