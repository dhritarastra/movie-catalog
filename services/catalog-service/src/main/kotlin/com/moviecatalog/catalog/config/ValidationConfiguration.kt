package com.moviecatalog.catalog.config

import org.springframework.boot.validation.autoconfigure.ValidationConfigurationCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.ZoneOffset

@Configuration
class ValidationConfiguration {
    /**
     * Bean Validation's "today" is the date in UTC+14, the first time zone to reach each new day.
     * A date that is today for the user, wherever they are, is then never rejected as the future.
     */
    @Bean
    fun earliestTimeZoneClock(): ValidationConfigurationCustomizer =
        ValidationConfigurationCustomizer { configuration ->
            configuration.clockProvider { Clock.system(ZoneOffset.ofHours(14)) }
        }
}
