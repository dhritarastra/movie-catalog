package com.moviecatalog.catalog.config

import org.springframework.boot.validation.autoconfigure.ValidationConfigurationCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.ZoneOffset

@Configuration
class ValidationConfiguration {
    // "Today" in UTC+14, so a date that is today anywhere in the world is never in the future.
    @Bean
    fun earliestTimeZoneClock(): ValidationConfigurationCustomizer =
        ValidationConfigurationCustomizer { configuration ->
            configuration.clockProvider { Clock.system(ZoneOffset.ofHours(14)) }
        }
}
