package com.moviecatalog.catalog.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration
import org.springframework.context.annotation.Configuration

class SearchPropertiesTest {
    private val context = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration::class.java))
        .withUserConfiguration(PropertiesConfiguration::class.java)

    @Test
    fun `the default allows 50 results`() {
        context.run {
            assertThat(it).hasNotFailed()
            assertThat(it.getBean(SearchProperties::class.java).maxLimit).isEqualTo(50)
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [10, 100])
    fun `a maximum of at least the clients' 10 results starts`(maxLimit: Int) {
        context.withPropertyValues("catalog.search.max-limit=$maxLimit").run {
            assertThat(it).hasNotFailed()
            assertThat(it.getBean(SearchProperties::class.java).maxLimit).isEqualTo(maxLimit)
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [9, 1, 0, -1])
    fun `a maximum below what the clients ask for stops startup, naming the setting`(maxLimit: Int) {
        context.withPropertyValues("catalog.search.max-limit=$maxLimit").run {
            assertThat(it).hasFailed()
            assertThat(it.startupFailure)
                .hasStackTraceContaining("maxLimit")
                .hasStackTraceContaining("must be at least 10, the number of results the web app and the gateway ask for")
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SearchProperties::class)
    class PropertiesConfiguration
}
