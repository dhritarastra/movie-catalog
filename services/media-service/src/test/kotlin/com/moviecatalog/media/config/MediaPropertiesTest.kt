package com.moviecatalog.media.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration
import org.springframework.context.annotation.Configuration

class MediaPropertiesTest {
    private val context = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration::class.java))
        .withUserConfiguration(PropertiesConfiguration::class.java)
        .withPropertyValues(
            "media.storage-dir=./test-media",
            "media.max-upload-bytes=5242880",
            "media.max-width=8000",
            "media.max-height=8000",
            "media.thumbnail-width=400",
        )

    @ParameterizedTest
    @ValueSource(ints = [1, 65536, 1048576])
    fun `positive chunks up to one MiB bind successfully`(size: Int) {
        context.withPropertyValues("media.download-chunk-bytes=$size").run {
            assertThat(it).hasNotFailed().hasSingleBean(MediaProperties::class.java)
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [-1, 0, 1048577, 4194304])
    fun `invalid chunks fail at startup instead of during a transfer`(size: Int) {
        context.withPropertyValues("media.download-chunk-bytes=$size").run {
            assertThat(it).hasFailed()
            assertThat(it.startupFailure).hasStackTraceContaining("downloadChunkBytes")
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(MediaProperties::class)
    class PropertiesConfiguration
}
