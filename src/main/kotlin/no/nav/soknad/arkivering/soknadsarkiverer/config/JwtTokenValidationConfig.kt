package no.nav.soknad.arkivering.soknadsarkiverer.config

import no.nav.security.token.support.spring.api.EnableJwtTokenValidation
import org.springframework.context.annotation.Configuration

@EnableJwtTokenValidation
@Configuration
class SecurityConfiguration
