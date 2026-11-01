package com.moviecatalog.gateway.grpc

import com.moviecatalog.catalog.v1.CreditServiceGrpc
import com.moviecatalog.catalog.v1.MovieServiceGrpc
import com.moviecatalog.catalog.v1.PersonServiceGrpc
import com.moviecatalog.catalog.v1.SearchServiceGrpc
import com.moviecatalog.media.v1.MediaServiceGrpc
import io.grpc.ManagedChannel
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.grpc.client.GrpcChannelFactory

/** The channel factory opens a new channel on every call, so each service gets one shared channel. */
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
    fun searchStub(): SearchServiceGrpc.SearchServiceBlockingStub = SearchServiceGrpc.newBlockingStub(catalog)

    @Bean
    fun mediaStub(): MediaServiceGrpc.MediaServiceStub = MediaServiceGrpc.newStub(media)

    @Bean
    fun mediaBlockingStub(): MediaServiceGrpc.MediaServiceBlockingStub = MediaServiceGrpc.newBlockingStub(media)
}
