// Shared gRPC contracts. Generates Java message classes and gRPC stubs that the
// services depend on, so client and server always compile against the same .proto files.
plugins {
    `java-library`
    alias(libs.plugins.protobuf)
}

dependencies {
    api(platform(libs.spring.boot.bom))
    api("io.grpc:grpc-protobuf")
    api("io.grpc:grpc-stub")
    api("com.google.protobuf:protobuf-java")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.versions.protoc.get()}"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:${libs.versions.grpc.java.get()}"
        }
    }
    generateProtoTasks {
        all().configureEach {
            plugins {
                create("grpc")
            }
        }
    }
}
