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

class PagingPropertiesTest {
    private val context = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration::class.java))
        .withUserConfiguration(PropertiesConfiguration::class.java)

    @Test
    fun `the default allows pages of 100`() {
        context.run {
            assertThat(it).hasNotFailed()
            assertThat(it.getBean(PagingProperties::class.java).maxLimit).isEqualTo(100)
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [20, 500])
    fun `a maximum of at least the clients' 20 starts`(maxLimit: Int) {
        context.withPropertyValues("catalog.paging.max-limit=$maxLimit").run {
            assertThat(it).hasNotFailed()
            assertThat(it.getBean(PagingProperties::class.java).maxLimit).isEqualTo(maxLimit)
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [19, 12, 0, -1])
    fun `a maximum below the largest page the clients ask for stops startup, naming the setting`(maxLimit: Int) {
        context.withPropertyValues("catalog.paging.max-limit=$maxLimit").run {
            assertThat(it).hasFailed()
            assertThat(it.startupFailure)
                .hasStackTraceContaining("maxLimit")
                .hasStackTraceContaining("must be at least 20, the largest page the web app and the gateway ask for")
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PagingProperties::class)
    class PropertiesConfiguration
}
