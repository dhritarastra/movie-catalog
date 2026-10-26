package com.moviecatalog.gateway.grpc

import com.moviecatalog.catalog.v1.MovieServiceGrpc
import com.moviecatalog.media.v1.MediaServiceGrpc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.grpc.client.GrpcChannelFactory

/** One channel per downstream service, configured under spring.grpc.client.channel.<name>. */
@Configuration
class GrpcClientConfiguration {
    @Bean
    fun movieStub(channels: GrpcChannelFactory): MovieServiceGrpc.MovieServiceBlockingStub =
        MovieServiceGrpc.newBlockingStub(channels.createChannel("catalog"))

    @Bean
    fun mediaStub(channels: GrpcChannelFactory): MediaServiceGrpc.MediaServiceStub =
        MediaServiceGrpc.newStub(channels.createChannel("media"))

    @Bean
    fun mediaBlockingStub(channels: GrpcChannelFactory): MediaServiceGrpc.MediaServiceBlockingStub =
        MediaServiceGrpc.newBlockingStub(channels.createChannel("media"))
}
