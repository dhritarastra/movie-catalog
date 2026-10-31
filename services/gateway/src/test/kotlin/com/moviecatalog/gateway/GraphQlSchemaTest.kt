package com.moviecatalog.gateway

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.graphql.ExecutionGraphQlService
import org.springframework.graphql.test.tester.ExecutionGraphQlServiceTester

@SpringBootTest
class GraphQlSchemaTest(@Autowired graphQlService: ExecutionGraphQlService) {
    private val tester = ExecutionGraphQlServiceTester.create(graphQlService)

    @Test
    fun `exposes the query and mutation root types`() {
        tester.document("{ __schema { queryType { name } mutationType { name } } }")
            .execute()
            .path("__schema.queryType.name").entity(String::class.java).isEqualTo("Query")
            .path("__schema.mutationType.name").entity(String::class.java).isEqualTo("Mutation")
    }

    @Test
    fun `declares Upload as a scalar`() {
        tester.document("""{ __type(name: "Upload") { kind } }""")
            .execute()
            .path("__type.kind").entity(String::class.java).isEqualTo("SCALAR")
    }
}
