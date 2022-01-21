package no.nav.soknad.arkivering.soknadsarkiverer

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SoknadsarkivererApplication

fun main(args: Array<String>) {
	runApplication<SoknadsarkivererApplication>(*args)
}