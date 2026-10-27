package com.moviecatalog.gateway.grpc

import com.moviecatalog.catalog.v1.MovieServiceGrpc
import com.moviecatalog.catalog.v1.PersonServiceGrpc
import com.moviecatalog.media.v1.MediaServiceGrpc
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/** Stubs for one service share a channel, so the gateway keeps one connection per downstream service. */
@SpringBootTest
class GrpcClientConfigurationTest(
    @Autowired private val movies: MovieServiceGrpc.MovieServiceBlockingStub,
    @Autowired private val people: PersonServiceGrpc.PersonServiceBlockingStub,
    @Autowired private val media: MediaServiceGrpc.MediaServiceStub,
    @Autowired private val mediaBlocking: MediaServiceGrpc.MediaServiceBlockingStub,
) {
    @Test
    fun `catalog stubs share one channel and media stubs share another`() {
        assertThat(people.channel).isSameAs(movies.channel)
        assertThat(mediaBlocking.channel).isSameAs(media.channel)
        assertThat(media.channel).isNotSameAs(movies.channel)
    }
}
