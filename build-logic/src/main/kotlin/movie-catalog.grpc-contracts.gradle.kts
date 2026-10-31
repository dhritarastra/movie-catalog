// Generates Java messages and gRPC stubs from the .proto files.
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
