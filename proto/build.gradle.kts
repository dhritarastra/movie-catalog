// Shared gRPC contracts. Generates Java message classes and gRPC stubs that the
// services depend on, so client and server always compile against the same .proto files.
plugins {
    id("movie-catalog.grpc-contracts")
}

dependencies {
    api(platform(libs.spring.boot.bom))
    api("io.grpc:grpc-protobuf")
    api("io.grpc:grpc-stub")
    api("com.google.protobuf:protobuf-java")
}
