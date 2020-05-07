package no.nav.soknad.arkivering.soknadsarkiverer.config

import com.natpryce.konfig.*
import com.natpryce.konfig.ConfigurationProperties.Companion.systemProperties
import org.springframework.context.annotation.Bean
import java.io.File

private val defaultProperties = ConfigurationMap(mapOf(
	"APP_VERSION" to "",
	"SOKNADSARKIVERER_USERNAME" to "arkiverer",
	"SOKNADSARKIVERER_PASSWORD" to "",
	"SCHEMA_REGISTRY_URL" to "http://localhost:8081",
	"KAFKA_BOOTSTRAP_SERVERS" to "localhost:29092",
	"KAFKA_CLIENTID" to "arkiverer",
	"KAFKA_SECURITY" to "",
	"KAFKA_SECPROT" to "",
	"KAFKA_SASLMEC" to "",
	"KAFKA_INPUT_TOPIC" to "privat-soknadInnsendt-v1-default",
	"KAFKA_PROCESSING_TOPIC" to "privat-soknadInnsendt-processingEventLog-v1-default",

	"JOARK_HOST" to "http://localhost:8092",
	"JOARK_URL" to "/joark/save",
	"FILESTORAGE_HOST" to "http://localhost:9042",
	"FILESTORAGE_URL" to "/filer?ids="
//,
//	"RETRY_TIME" to listOf(5, 25, 60, 120, 600) // TODO
))

val appConfig =
	EnvironmentVariables() overriding
		systemProperties() overriding
		ConfigurationProperties.fromResource(Configuration::class.java, "/application.yml") overriding
		ConfigurationProperties.fromResource(Configuration::class.java, "/local.properties") overriding
		defaultProperties

private fun String.configProperty(): String = appConfig[Key(this, stringType)]

fun readFileAsText(fileName: String, default: String = "") = try { File(fileName).readText(Charsets.UTF_8) } catch (e: Exception ) { default }

data class AppConfiguration(val kafkaConfig: KafkaConfig = KafkaConfig()) {
	data class KafkaConfig(
		val version: String = "APP_VERSION".configProperty(),
		val username: String = readFileAsText("/var/run/secrets/nais.io/serviceuser/username", "SOKNADSARKIVERER_USERNAME".configProperty()),
		val password: String = readFileAsText("/var/run/secrets/nais.io/serviceuser/password", "SOKNADSARKIVERER_PASSWORD".configProperty()),
		val servers: String = readFileAsText("/var/run/secrets/nais.io/kv/kafkaBootstrapServers", "KAFKA_BOOTSTRAP_SERVERS".configProperty()),
		val schemaRegistryUrl: String = "SCHEMA_REGISTRY_URL".configProperty(),
		val clientId: String = readFileAsText("/var/run/secrets/nais.io/serviceuser/username","KAFKA_CLIENTID".configProperty()),
		val secure: String = "KAFKA_SECURITY".configProperty(),
		val protocol: String = "KAFKA_SECPROT".configProperty(), // SASL_PLAINTEXT | SASL_SSL
		val salsmec: String = "KAFKA_SASLMEC".configProperty(), // PLAIN
		val inputTopic: String = "KAFKA_INPUT_TOPIC".configProperty(),
		val processingTopic: String = "KAFKA_PROCESSING_TOPIC".configProperty(),
		val saslJaasConfig: String = "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"$username\" password=\"$password\";"
	)

	data class Config(
		val joarkHost: String = "JOARK_HOST".configProperty(),
		val joarkUrl: String = "JOARK_URL".configProperty(),
		val filestorageHost: String = "FILESTORAGE_HOST".configProperty(),
		val filestorageUrl: String = "FILESTORAGE_URL".configProperty(),
		val retryTime: String = "RETRY_TIME".configProperty()
	)
}

@Bean
fun appConfiguration() = AppConfiguration()