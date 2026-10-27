package com.moviecatalog.catalog.config

import jakarta.validation.Validation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.ZoneOffset

class ValidationConfigurationTest {
    @Test
    fun `today is the date in UTC+14, the first time zone to reach each day`() {
        val configuration = Validation.byDefaultProvider().configure()

        ValidationConfiguration().earliestTimeZoneClock().customize(configuration)

        assertThat(configuration.buildValidatorFactory().clockProvider.clock.zone).isEqualTo(ZoneOffset.ofHours(14))
    }
}
