package com.moviecatalog.gateway.graphql

import com.moviecatalog.gateway.config.GatewayProperties
import org.springframework.graphql.server.WebGraphQlHandler
import org.springframework.graphql.server.WebGraphQlRequest
import org.springframework.http.HttpCookie
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.multipart.MultipartHttpServletRequest
import org.springframework.web.servlet.function.ServerRequest
import org.springframework.web.servlet.function.ServerResponse
import tools.jackson.core.JacksonException
import tools.jackson.databind.json.JsonMapper
import java.net.InetSocketAddress
import java.net.URI
import java.util.UUID

/**
 * Handles the GraphQL multipart request spec (operations, map and file parts), which Spring for
 * GraphQL does not support. It puts the files into the variables and runs the operation through
 * the normal WebGraphQlHandler.
 */
@Component
class GraphQlMultipartHandler(
    private val graphQlHandler: WebGraphQlHandler,
    private val jsonMapper: JsonMapper,
    private val properties: GatewayProperties,
) {
    fun handle(request: ServerRequest): ServerResponse {
        val multipart = request.servletRequest() as? MultipartHttpServletRequest
            ?: return badRequest("Expected a multipart/form-data request")
        return try {
            val body = parseOperations(multipart.getParameter("operations"))
            @Suppress("UNCHECKED_CAST")
            val variables = (body["variables"] as MutableMap<String, Any?>?) ?: mutableMapOf<String, Any?>().also { body["variables"] = it }

            for ((part, paths) in parseObject(multipart.getParameter("map"), "map")) {
                val file = multipart.getFile(part) ?: throw MultipartRequestException("The map refers to file part '$part', which was not sent")
                val pathList = paths as? List<*> ?: throw MultipartRequestException("The map entry for '$part' must be a list of paths")
                pathList.forEach { insertFile(variables, it as? String ?: throw MultipartRequestException("Map paths must be strings"), file) }
            }
            execute(multipart, body)
        } catch (e: MultipartRequestException) {
            badRequest(e.message)
        }
    }

    private fun execute(request: MultipartHttpServletRequest, body: Map<String, Any?>): ServerResponse {
        val graphQlRequest = WebGraphQlRequest(
            URI.create(request.requestURL.toString()),
            HttpHeaders().apply { request.headerNames.asIterator().forEach { name -> addAll(name, request.getHeaders(name).toList()) } },
            LinkedMultiValueMap<String, HttpCookie>(),
            request.remoteAddr?.let { InetSocketAddress(it, request.remotePort) },
            mutableMapOf<String, Any>(),
            @Suppress("UNCHECKED_CAST") (body as Map<String, Any>),
            UUID.randomUUID().toString(),
            request.locale,
        )
        val response = graphQlHandler.handleRequest(graphQlRequest).block(properties.multipartTimeout)
            ?: throw IllegalStateException("No GraphQL response")
        return ServerResponse.ok()
            .headers { it.addAll(response.responseHeaders) }
            .contentType(MediaType.APPLICATION_JSON)
            .body(response.toMap())
    }

    /** Checks the operations JSON like a normal request body. Batches are not supported. */
    private fun parseOperations(json: String?): MutableMap<String, Any?> {
        if (parseJson(json, "operations") is List<*>) {
            throw MultipartRequestException("Batched operations are not supported; send one operation per request")
        }
        val body = parseObject(json, "operations")
        if ((body["query"] as? String).isNullOrBlank()) throw MultipartRequestException("The 'operations' part must include a query")
        if (body["variables"] != null && body["variables"] !is MutableMap<*, *>) {
            throw MultipartRequestException("'variables' must be a JSON object")
        }
        if (body["operationName"] != null && body["operationName"] !is String) {
            throw MultipartRequestException("'operationName' must be a string")
        }
        return body
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseObject(json: String?, part: String): MutableMap<String, Any?> =
        parseJson(json, part) as? MutableMap<String, Any?> ?: throw MultipartRequestException("The '$part' part must be a JSON object")

    private fun parseJson(json: String?, part: String): Any? {
        if (json == null) throw MultipartRequestException("The '$part' part is required")
        return try {
            jsonMapper.readValue(json, Any::class.java)
        } catch (e: JacksonException) {
            throw MultipartRequestException("The '$part' part must be a JSON object")
        }
    }

    /** Puts [file] at a path like `variables.file`; every step but the last must already exist. */
    @Suppress("UNCHECKED_CAST")
    private fun insertFile(variables: MutableMap<String, Any?>, path: String, file: MultipartFile) {
        val segments = path.split(".")
        if (segments.size < 2 || segments.first() != "variables") {
            throw MultipartRequestException("Map path '$path' must start with 'variables.'")
        }
        var container: Any? = variables
        for (segment in segments.drop(1).dropLast(1)) {
            container = when (container) {
                is Map<*, *> -> container[segment]
                is List<*> -> segment.toIntOrNull()?.let { container.getOrNull(it) }
                else -> null
            } ?: throw MultipartRequestException("Map path '$path' does not match the operation's variables")
        }
        val last = segments.last()
        when (container) {
            is MutableMap<*, *> -> {
                if (!container.containsKey(last)) throw MultipartRequestException("Map path '$path' does not match the operation's variables")
                (container as MutableMap<String, Any?>)[last] = file
            }
            is MutableList<*> -> {
                val index = last.toIntOrNull()?.takeIf { it in container.indices }
                    ?: throw MultipartRequestException("Map path '$path' does not match the operation's variables")
                (container as MutableList<Any?>)[index] = file
            }
            else -> throw MultipartRequestException("Map path '$path' does not match the operation's variables")
        }
    }

    private fun badRequest(message: String?): ServerResponse =
        ServerResponse.badRequest().contentType(MediaType.APPLICATION_JSON).body(errorBody(ErrorCode.BAD_REQUEST, message))

    class MultipartRequestException(message: String) : RuntimeException(message)

    companion object {
        fun errorBody(code: ErrorCode, message: String?): Map<String, Any?> =
            mapOf("errors" to listOf(mapOf("message" to message, "extensions" to mapOf("code" to code.name))))
    }
}
