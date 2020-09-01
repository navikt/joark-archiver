package no.nav.soknad.arkivering.soknadsarkiverer.config

import no.nav.security.token.support.client.spring.oauth2.EnableOAuth2Client
import no.nav.security.token.support.spring.api.EnableJwtTokenValidation
import org.springframework.context.annotation.Configuration

@EnableOAuth2Client(cacheEnabled = true)
@EnableJwtTokenValidation
@Configuration
class SecurityConfiguration