package com.moviecatalog.gateway.grpc

import com.moviecatalog.catalog.v1.CreditServiceGrpc
import com.moviecatalog.catalog.v1.MovieServiceGrpc
import com.moviecatalog.catalog.v1.PersonServiceGrpc
import com.moviecatalog.media.v1.MediaServiceGrpc
import io.grpc.ManagedChannel
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.grpc.client.GrpcChannelFactory

/**
 * Stubs for the downstream services, configured under spring.grpc.client.channel.<name>.
 * The factory opens a new channel on every createChannel call, so each service gets one
 * channel here, shared by all of its stubs. The factory shuts the channels down on exit.
 */
@Configuration
class GrpcClientConfiguration(channels: GrpcChannelFactory) {
    private val catalog: ManagedChannel = channels.createChannel("catalog")
    private val media: ManagedChannel = channels.createChannel("media")

    @Bean
    fun movieStub(): MovieServiceGrpc.MovieServiceBlockingStub = MovieServiceGrpc.newBlockingStub(catalog)

    @Bean
    fun personStub(): PersonServiceGrpc.PersonServiceBlockingStub = PersonServiceGrpc.newBlockingStub(catalog)

    @Bean
    fun creditStub(): CreditServiceGrpc.CreditServiceBlockingStub = CreditServiceGrpc.newBlockingStub(catalog)

    @Bean
    fun mediaStub(): MediaServiceGrpc.MediaServiceStub = MediaServiceGrpc.newStub(media)

    @Bean
    fun mediaBlockingStub(): MediaServiceGrpc.MediaServiceBlockingStub = MediaServiceGrpc.newBlockingStub(media)
}
