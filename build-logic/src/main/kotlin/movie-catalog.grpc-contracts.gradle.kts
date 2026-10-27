// Generates Java message classes and gRPC stubs from the module's .proto files, with protoc and
// the gRPC plugin pinned to the versions Spring Boot manages for the runtime libraries.
plugins {
    `java-library`
    id("com.google.protobuf")
}

val libs = the<VersionCatalogsExtension>().named("libs")

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.findVersion("protoc").get()}"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:${libs.findVersion("grpc-java").get()}"
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
