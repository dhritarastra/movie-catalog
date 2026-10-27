package com.moviecatalog.gateway.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration
import org.springframework.context.annotation.Configuration

class GatewayPropertiesTest {
    private val context = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration::class.java))
        .withUserConfiguration(PropertiesConfiguration::class.java)
        .withPropertyValues(
            "gateway.max-upload-bytes=5242880",
            "gateway.catalog-deadline=2s",
            "gateway.media-upload-deadline=30s",
            "gateway.media-download-deadline=30s",
            "gateway.multipart-timeout=60s",
        )

    @ParameterizedTest
    @ValueSource(ints = [1, 65536, 1048576])
    fun `positive chunks up to one MiB bind successfully`(size: Int) {
        context.withPropertyValues("gateway.media-upload-chunk-bytes=$size").run {
            assertThat(it).hasNotFailed().hasSingleBean(GatewayProperties::class.java)
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [-1, 0, 1048577, 4194304])
    fun `invalid chunks fail at startup instead of during a transfer`(size: Int) {
        context.withPropertyValues("gateway.media-upload-chunk-bytes=$size").run {
            assertThat(it).hasFailed()
            assertThat(it.startupFailure).hasStackTraceContaining("mediaUploadChunkBytes")
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(GatewayProperties::class)
    class PropertiesConfiguration
}
