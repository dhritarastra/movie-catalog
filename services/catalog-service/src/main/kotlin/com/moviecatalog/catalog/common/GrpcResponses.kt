package com.moviecatalog.catalog.common

import com.google.protobuf.Timestamp
import io.grpc.stub.StreamObserver
import java.time.Instant

/** Sends the single response of a unary call. */
fun <T> StreamObserver<T>.complete(value: T) {
    onNext(value)
    onCompleted()
}

fun Instant.toTimestamp(): Timestamp = Timestamp.newBuilder().setSeconds(epochSecond).setNanos(nano).build()
