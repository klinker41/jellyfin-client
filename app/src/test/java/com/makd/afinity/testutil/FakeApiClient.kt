package com.makd.afinity.testutil

import io.mockk.mockk
import java.util.concurrent.CopyOnWriteArrayList
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.HttpClientOptions
import org.jellyfin.sdk.api.client.HttpMethod
import org.jellyfin.sdk.api.client.RawResponse
import org.jellyfin.sdk.api.client.util.ApiSerializer
import org.jellyfin.sdk.api.sockets.SocketApi
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo

data class RecordedRequest(
    val method: HttpMethod,
    val pathTemplate: String,
    val pathParameters: Map<String, Any?>,
    val queryParameters: Map<String, Any?>,
    val requestBody: Any?,
)

/**
 * In-memory [ApiClient] test double backed by [ApiSerializer] and [RawResponse] so SDK operations
 * (`MediaInfoApi`, `LibraryApi`, `SessionApi`, `UserViewApi`, etc.) execute their real
 * serialization and URL-building code without `mockkConstructor`.
 */
class FakeApiClient(
    baseUrl: String = "https://jf.example.com",
    accessToken: String? = "test-token",
    clientInfo: ClientInfo = ClientInfo(name = "AFinity", version = "1.0.0"),
    deviceInfo: DeviceInfo = DeviceInfo(id = "device-1", name = "TestDevice"),
    override val httpClientOptions: HttpClientOptions = HttpClientOptions(),
) : ApiClient() {

    private var _baseUrl: String? = baseUrl
    private var _accessToken: String? = accessToken
    private var _clientInfo: ClientInfo = clientInfo
    private var _deviceInfo: DeviceInfo = deviceInfo

    override val baseUrl: String?
        get() = _baseUrl

    override val accessToken: String?
        get() = _accessToken

    override val clientInfo: ClientInfo
        get() = _clientInfo

    override val deviceInfo: DeviceInfo
        get() = _deviceInfo

    override val webSocket: SocketApi = mockk(relaxed = true)

    private val _requests = CopyOnWriteArrayList<RecordedRequest>()
    val requests: List<RecordedRequest>
        get() = _requests

    private data class Route(
        val predicate: (RecordedRequest) -> Boolean,
        val handler: suspend (RecordedRequest) -> RawResponse,
    )

    private val routes = CopyOnWriteArrayList<Route>()

    override fun update(
        baseUrl: String?,
        accessToken: String?,
        clientInfo: ClientInfo,
        deviceInfo: DeviceInfo,
    ) {
        _baseUrl = baseUrl
        _accessToken = accessToken
        _clientInfo = clientInfo
        _deviceInfo = deviceInfo
    }

    fun on(
        method: HttpMethod,
        pathTemplate: String,
        handler: suspend (RecordedRequest) -> RawResponse,
    ) {
        onMatch({ it.method == method && it.pathTemplate == pathTemplate }, handler)
    }

    fun onGet(pathTemplate: String, handler: suspend (RecordedRequest) -> RawResponse) =
        on(HttpMethod.GET, pathTemplate, handler)

    fun onPost(pathTemplate: String, handler: suspend (RecordedRequest) -> RawResponse) =
        on(HttpMethod.POST, pathTemplate, handler)

    fun onDelete(pathTemplate: String, handler: suspend (RecordedRequest) -> RawResponse) =
        on(HttpMethod.DELETE, pathTemplate, handler)

    fun onMatch(
        predicate: (RecordedRequest) -> Boolean,
        handler: suspend (RecordedRequest) -> RawResponse,
    ) {
        // Prepend so test-specific overrides shadow earlier defaults.
        routes.add(0, Route(predicate, handler))
    }

    fun clearRequests() {
        _requests.clear()
    }

    override suspend fun request(
        method: HttpMethod,
        pathTemplate: String,
        pathParameters: Map<String, Any?>,
        queryParameters: Map<String, Any?>,
        requestBody: Any?,
    ): RawResponse {
        val recorded =
            RecordedRequest(
                method = method,
                pathTemplate = pathTemplate,
                pathParameters = pathParameters,
                queryParameters = queryParameters,
                requestBody = requestBody,
            )
        _requests.add(recorded)
        val route =
            routes.firstOrNull { it.predicate(recorded) }
                ?: error("FakeApiClient: no route registered for $method $pathTemplate")
        return route.handler(recorded)
    }

    companion object {
        inline fun <reified T : Any> jsonResponse(
            body: T,
            status: Int = 200,
            headers: Map<String, List<String>> = emptyMap(),
        ): RawResponse {
            val encoded = ApiSerializer.encodeRequestBody(body) ?: "{}"
            return RawResponse(encoded.toByteArray(Charsets.UTF_8), status, headers)
        }

        fun rawJsonResponse(
            json: String,
            status: Int = 200,
            headers: Map<String, List<String>> = emptyMap(),
        ): RawResponse = RawResponse(json.toByteArray(Charsets.UTF_8), status, headers)

        inline fun <reified T> decodeJson(json: String): T = ApiSerializer.decodeResponseBody(json)

        fun bytesResponse(
            bytes: ByteArray,
            status: Int = 200,
            headers: Map<String, List<String>> = emptyMap(),
        ): RawResponse = RawResponse(bytes, status, headers)

        fun emptyResponse(
            status: Int = 204,
            headers: Map<String, List<String>> = emptyMap(),
        ): RawResponse = RawResponse(ByteArray(0), status, headers)

        fun okHttpResponse(
            request: Request,
            code: Int,
            body: ByteArray = ByteArray(0),
            contentType: String? = null,
            message: String =
                when (code) {
                    200 -> "OK"
                    206 -> "Partial Content"
                    404 -> "Not Found"
                    416 -> "Range Not Satisfiable"
                    500 -> "Server Error"
                    else -> "HTTP $code"
                },
        ): Response =
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(message)
                .body(body.toResponseBody(contentType?.toMediaTypeOrNull()))
                .build()

        fun mediaSourceJson(
            id: String = "src-1",
            name: String? = null,
            container: String? = null,
            size: Long? = null,
            supportsDirectPlay: Boolean = true,
            supportsDirectStream: Boolean = true,
            supportsTranscoding: Boolean = false,
            transcodingUrl: String? = null,
            transcodingSubProtocol: String = "http",
            eTag: String? = null,
        ): String {
            val optionalFields =
                listOfNotNull(
                        name?.let { "\"Name\":\"$it\"" },
                        container?.let { "\"Container\":\"$it\"" },
                        size?.let { "\"Size\":$it" },
                        eTag?.let { "\"ETag\":\"$it\"" },
                        transcodingUrl?.let { "\"TranscodingUrl\":\"$it\"" },
                    )
                    .joinToString(",")
                    .let { if (it.isEmpty()) "" else "$it," }
            return """{
                "Protocol":"Http",
                "Id":"$id",
                "Type":"Default",
                $optionalFields
                "TranscodingSubProtocol":"$transcodingSubProtocol",
                "SupportsDirectPlay":$supportsDirectPlay,
                "SupportsDirectStream":$supportsDirectStream,
                "SupportsTranscoding":$supportsTranscoding,
                "IsRemote":false,
                "IsInfiniteStream":false,
                "UseMostCompatibleTranscodingProfile":false,
                "HasSegments":false,
                "ReadAtNativeFramerate":false,
                "IgnoreDts":false,
                "IgnoreIndex":false,
                "GenPtsInput":false,
                "SupportsProbing":true,
                "RequiresOpening":false,
                "RequiresClosing":false,
                "RequiresLooping":false
            }"""
                .trimIndent()
        }
    }
}
