plugins {
    id("movie-catalog.grpc-contracts")
}

dependencies {
    api(platform(libs.spring.boot.bom))
    api("io.grpc:grpc-protobuf")
    api("io.grpc:grpc-stub")
    api("com.google.protobuf:protobuf-java")
}
