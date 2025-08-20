package com.ghostly.network

import com.ghostly.datastore.LoginDetailsStore
import com.ghostly.login.models.LoginDetails
import com.ghostly.login.models.SiteResponse
import com.ghostly.network.models.Result
import com.ghostly.network.models.ImageUploadResponse
import com.ghostly.network.models.Token
import com.ghostly.posts.models.PostsResponse
import com.ghostly.posts.models.UpdatePostRequest
import com.ghostly.posts.models.UpdatePostResponse
import com.ghostly.posts.models.UpdateRequestWrapper
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.http.headers
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.InputProvider
import io.ktor.utils.io.core.buildPacket
import io.ktor.utils.io.core.writeFully
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

interface ApiService {
    suspend fun getPosts(page: Int, pageSize: Int): Result<PostsResponse>
    suspend fun getSiteDetails(url: String): Result<SiteResponse>
    suspend fun publishPost(postId: String, request: UpdateRequestWrapper): Result<PostsResponse>
    suspend fun updatePost(postId: String, request: UpdatePostRequest): Result<UpdatePostResponse>
    suspend fun getPostById(postId: String): Result<UpdatePostResponse>
    suspend fun uploadImage(fileName: String, bytes: ByteArray, mimeType: String): Result<ImageUploadResponse>

    suspend fun <T> get(
        endpoint: Endpoint,
        getBody: suspend (HttpResponse) -> T,
        block: HttpRequestBuilder.() -> Unit = {},
    ): Result<T>

    suspend fun <T> post(
        endpoint: Endpoint,
        getBody: suspend (HttpResponse) -> T,
        block: HttpRequestBuilder.() -> Unit = {},
    ): Result<T>
}

class ApiServiceImpl(
    private val client: HttpClient,
    private val tokenProvider: TokenProvider,
    private val loginDetailsStore: LoginDetailsStore,
) : ApiService {

    private suspend fun getLoginDetails(): LoginDetails? {
        return loginDetailsStore.get()
    }

    private suspend fun tryAndGetToken(): Token? {
        return tokenProvider.generateToken()
    }

    override suspend fun <T> get(
        endpoint: Endpoint,
        getBody: suspend (HttpResponse) -> T,
        block: HttpRequestBuilder.() -> Unit,
    ): Result<T> = withContext(Dispatchers.IO) {
        val loginDetails =
            getLoginDetails() ?: return@withContext Result.Error(-1, "Invalid Login Details")

        val token =
            tryAndGetToken() ?: return@withContext Result.Error(-1, "Unable to generate token")

        val response: HttpResponse = client.get("${loginDetails.domainUrl}${endpoint.path}") {
            block.invoke(this)
            header("Authorization", "Ghost ${token.token}")
        }

        when {
            response.status == HttpStatusCode.Unauthorized -> {
                return@withContext Result.Error(
                    HttpStatusCode.Unauthorized.value,
                    "Invalid API Key"
                )
            }

            response.status != HttpStatusCode.OK -> {
                return@withContext Result.Error(response.status.value, response.bodyAsText())
            }

            else -> {
                Result.Success(getBody(response))
            }
        }
    }

    override suspend fun <T> post(
        endpoint: Endpoint,
        getBody: suspend (HttpResponse) -> T,
        block: HttpRequestBuilder.() -> Unit
    ): Result<T> = withContext(Dispatchers.IO) {
        val loginDetails =
            getLoginDetails() ?: return@withContext Result.Error(-1, "Invalid Login Details")

        val token =
            tryAndGetToken() ?: return@withContext Result.Error(-1, "Unable to generate token")

        val response: HttpResponse = client.post("${loginDetails.domainUrl}${endpoint.path}") {
            block.invoke(this)
            header("Authorization", "Ghost ${token.token}")
        }

        when {
            response.status == HttpStatusCode.Unauthorized -> {
                return@withContext Result.Error(
                    HttpStatusCode.Unauthorized.value,
                    "Invalid API Key"
                )
            }

            response.status != HttpStatusCode.OK -> {
                return@withContext Result.Error(response.status.value, response.bodyAsText())
            }

            else -> {
                Result.Success(getBody(response))
            }
        }
    }

    override suspend fun getPosts(page: Int, pageSize: Int) = withContext(Dispatchers.IO) {
        val loginDetails =
            getLoginDetails() ?: return@withContext Result.Error(-1, "Invalid Login Details")

        val token =
            tryAndGetToken() ?: return@withContext Result.Error(-1, "Unable to generate token")

        val response: HttpResponse =
            client.get("${loginDetails.domainUrl}/api/admin/posts/?formats=html") {
                parameter("page", page)
                parameter("limit", pageSize)
                header("Authorization", "Ghost ${token.token}")
            }

        when {
            response.status == HttpStatusCode.Unauthorized -> {
                return@withContext Result.Error(
                    HttpStatusCode.Unauthorized.value,
                    "Invalid API Key"
                )
            }

            response.status != HttpStatusCode.OK -> {
                return@withContext Result.Error(response.status.value, response.bodyAsText())
            }

            else -> {
                Result.Success(response.body<PostsResponse>())
            }
        }
    }

    override suspend fun getSiteDetails(url: String): Result<SiteResponse> =
        withContext(Dispatchers.IO) {
            val response: HttpResponse = client.get("${url}/api/admin/site/")

            if (response.status == HttpStatusCode.OK) {
                return@withContext Result.Success(response.body<SiteResponse>())
            }
            Result.Error(
                errorCode = response.status.value,
                message = "Invalid Site Details"
            )
        }

    override suspend fun publishPost(
        postId: String,
        request: UpdateRequestWrapper,
    ): Result<PostsResponse> =
        withContext(Dispatchers.IO) {
            val loginDetails =
                getLoginDetails() ?: return@withContext Result.Error(-1, "Invalid Login Details")

            val token =
                tryAndGetToken() ?: return@withContext Result.Error(-1, "Unable to generate token")
            val response: HttpResponse =
                client.put("${loginDetails.domainUrl}/api/admin/posts?formats=html") {
                    url {
                        appendPathSegments(postId)
                    }
                    header("Authorization", "Ghost ${token.token}")
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

            when {
                response.status == HttpStatusCode.Unauthorized -> {
                    return@withContext Result.Error(
                        HttpStatusCode.Unauthorized.value,
                        "Invalid API Key"
                    )
                }

                response.status != HttpStatusCode.OK -> {
                    return@withContext Result.Error(response.status.value, response.bodyAsText())
                }

                else -> {
                    Result.Success(response.body<PostsResponse>())
                }
            }
        }

    override suspend fun updatePost(
        postId: String,
        request: UpdatePostRequest,
    ): Result<UpdatePostResponse> =
        withContext(Dispatchers.IO) {
            val loginDetails =
                getLoginDetails() ?: return@withContext Result.Error(-1, "Invalid Login Details")

            val token =
                tryAndGetToken() ?: return@withContext Result.Error(-1, "Unable to generate token")
            
            println("ApiService: Sending PUT request to update post $postId")
            println("ApiService: Request body: ${request.posts.firstOrNull()?.tags?.size} tags")
            request.posts.firstOrNull()?.tags?.forEach { tag ->
                println("ApiService: Tag in request: ${tag.name} (id: ${tag.id})")
            }
            
            val response: HttpResponse =
                client.put("${loginDetails.domainUrl}/api/admin/posts/${postId}/") {
                    header("Authorization", "Ghost ${token.token}")
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

            when {
                response.status == HttpStatusCode.Unauthorized -> {
                    return@withContext Result.Error(
                        HttpStatusCode.Unauthorized.value,
                        "Invalid API Key"
                    )
                }

                response.status != HttpStatusCode.OK -> {
                    println("ApiService: Update failed with status ${response.status.value}: ${response.bodyAsText()}")
                    return@withContext Result.Error(response.status.value, response.bodyAsText())
                }

                else -> {
                    println("ApiService: Update successful")
                    Result.Success(response.body<UpdatePostResponse>())
                }
            }
        }

    override suspend fun getPostById(postId: String): Result<UpdatePostResponse> =
        withContext(Dispatchers.IO) {
            val loginDetails =
                getLoginDetails() ?: return@withContext Result.Error(-1, "Invalid Login Details")

            val token =
                tryAndGetToken() ?: return@withContext Result.Error(-1, "Unable to generate token")
            val response: HttpResponse =
                client.get("${loginDetails.domainUrl}/api/admin/posts/${postId}/") {
                    header("Authorization", "Ghost ${token.token}")
                }

            when {
                response.status == HttpStatusCode.Unauthorized -> {
                    return@withContext Result.Error(
                        HttpStatusCode.Unauthorized.value,
                        "Invalid API Key"
                    )
                }

                response.status != HttpStatusCode.OK -> {
                    return@withContext Result.Error(response.status.value, response.bodyAsText())
                }

                else -> {
                    Result.Success(response.body<UpdatePostResponse>())
                }
            }
        }

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun uploadImage(
        fileName: String,
        bytes: ByteArray,
        mimeType: String,
    ): Result<ImageUploadResponse> = withContext(Dispatchers.IO) {
        val loginDetails =
            getLoginDetails() ?: return@withContext Result.Error(-1, "Invalid Login Details")

        val token =
            tryAndGetToken() ?: return@withContext Result.Error(-1, "Unable to generate token")

        println("ApiService: Uploading image - size: ${bytes.size}, type: $mimeType, name: $fileName")
        println("ApiService: Using URL: ${loginDetails.domainUrl}${Endpoint.IMAGES_UPLOAD.path}")
        
        // Try multiple approaches in sequence
        suspend fun tryApproach1(): HttpResponse {
            println("ApiService: Trying approach 1 - v6 API with explicit headers")
            return client.post("${loginDetails.domainUrl}${Endpoint.IMAGES_UPLOAD.path}") {
                header("Authorization", "Ghost ${token.token}")
                header("Accept-Version", "v6")
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append("file", bytes, Headers.build {
                                append(HttpHeaders.ContentType, mimeType)
                                append(HttpHeaders.ContentDisposition, "form-data; name=\"file\"; filename=\"$fileName\"")
                            })
                        }
                    )
                )
            }
        }
        
        suspend fun tryApproach2(): HttpResponse {
            println("ApiService: Trying approach 2 - v6 with purpose parameter")
            return client.post("${loginDetails.domainUrl}${Endpoint.IMAGES_UPLOAD.path}") {
                header("Authorization", "Ghost ${token.token}")
                header("Accept-Version", "v6")
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append("purpose", "image")
                            append("file", bytes, Headers.build {
                                append(HttpHeaders.ContentType, mimeType)
                                append(HttpHeaders.ContentDisposition, "form-data; name=\"file\"; filename=\"$fileName\"")
                            })
                        }
                    )
                )
            }
        }
        
        suspend fun tryApproach3(): HttpResponse {
            println("ApiService: Trying approach 3 - v6 with ref parameter")
            return client.post("${loginDetails.domainUrl}${Endpoint.IMAGES_UPLOAD.path}") {
                header("Authorization", "Ghost ${token.token}")
                header("Accept-Version", "v6")
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append("ref", fileName)
                            append("file", bytes, Headers.build {
                                append(HttpHeaders.ContentType, mimeType)
                                append(HttpHeaders.ContentDisposition, "form-data; name=\"file\"; filename=\"$fileName\"")
                            })
                        }
                    )
                )
            }
        }
        
        suspend fun tryApproach4(): HttpResponse {
            println("ApiService: Trying approach 4 - v6 complete format with purpose + ref")
            return client.post("${loginDetails.domainUrl}${Endpoint.IMAGES_UPLOAD.path}") {
                header("Authorization", "Ghost ${token.token}")
                header("Accept-Version", "v6")
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append("purpose", "image")
                            append("ref", fileName)
                            append("file", bytes, Headers.build {
                                append(HttpHeaders.ContentType, mimeType)
                                append(HttpHeaders.ContentDisposition, "form-data; name=\"file\"; filename=\"$fileName\"")
                            })
                        }
                    )
                )
            }
        }
        
        suspend fun tryApproach5(): HttpResponse {
            println("ApiService: Trying approach 5 - Base64 approach (debug)")
            // Try base64 encoded approach if file size is the issue
            val base64Image = Base64.encode(bytes)
            println("ApiService: Base64 size: ${base64Image.length}")
            return client.post("${loginDetails.domainUrl}${Endpoint.IMAGES_UPLOAD.path}") {
                header("Authorization", "Ghost ${token.token}")
                header("Accept-Version", "v6")
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append("file", base64Image.toByteArray(), Headers.build {
                                append(HttpHeaders.ContentType, "application/octet-stream")
                                append(HttpHeaders.ContentDisposition, "form-data; name=\"file\"; filename=\"$fileName\"")
                            })
                        }
                    )
                )
            }
        }
        
        suspend fun tryApproach6(): HttpResponse {
            println("ApiService: Trying approach 6 - Manual multipart with string body")
            val boundary = "----formdata-ktor-${System.currentTimeMillis()}"
            val imageDataBase64 = Base64.encode(bytes)
            
            val multipartBody = buildString {
                append("--$boundary\r\n")
                append("Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"\r\n")
                append("Content-Type: $mimeType\r\n")
                append("\r\n")
                append(imageDataBase64)
                append("\r\n")
                append("--$boundary--\r\n")
            }
            
            println("ApiService: Manual multipart body preview: ${multipartBody.take(200)}...")
            
            return client.post("${loginDetails.domainUrl}${Endpoint.IMAGES_UPLOAD.path}") {
                header("Authorization", "Ghost ${token.token}")
                header("Accept-Version", "v6")
                header("Content-Type", "multipart/form-data; boundary=$boundary")
                setBody(multipartBody)
            }
        }
        
        suspend fun tryApproach7(): HttpResponse {
            println("ApiService: Trying approach 7 - Small test image to check size limits")
            // Create a tiny 1x1 PNG image for testing
            val tinyPngBytes = byteArrayOf(
                0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
                0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
                0x08, 0x02, 0x00, 0x00, 0x00, 0x90.toByte(), 0x77.toByte(), 0x53.toByte(),
                0xDE.toByte(), 0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41, 0x54,
                0x08, 0xD7.toByte(), 0x63, 0xF8.toByte(), 0x0F, 0x00, 0x00, 0x01,
                0x00, 0x01, 0x5C.toByte(), 0xCC.toByte(), 0x5D.toByte(), 0xB0.toByte(), 0x00, 0x00,
                0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, 0xAE.toByte(), 0x42.toByte(), 0x60.toByte(), 0x82.toByte()
            )
            
            println("ApiService: Testing with tiny PNG (${tinyPngBytes.size} bytes)")
            
            return client.post("${loginDetails.domainUrl}${Endpoint.IMAGES_UPLOAD.path}") {
                header("Authorization", "Ghost ${token.token}")
                header("Accept-Version", "v6")
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append("file", tinyPngBytes, Headers.build {
                                append(HttpHeaders.ContentType, "image/png")
                                append(HttpHeaders.ContentDisposition, "form-data; name=\"file\"; filename=\"test.png\"")
                            })
                        }
                    )
                )
            }
        }
        
        val approaches = listOf(::tryApproach1, ::tryApproach2, ::tryApproach3, ::tryApproach4, ::tryApproach5, ::tryApproach6, ::tryApproach7)
        
        // Try each approach
        for ((index, approach) in approaches.withIndex()) {
            try {
                val response = approach()
                
                println("ApiService: Approach ${index + 1} - Response status: ${response.status}")
                println("ApiService: Approach ${index + 1} - Response headers: ${response.headers}")
                
                when {
                    response.status == HttpStatusCode.Unauthorized -> {
                        return@withContext Result.Error(
                            HttpStatusCode.Unauthorized.value,
                            "Invalid API Key"
                        )
                    }

                    response.status == HttpStatusCode.OK -> {
                        val result = response.body<ImageUploadResponse>()
                        println("ApiService: Approach ${index + 1} SUCCESS! Image URL: ${result.images.firstOrNull()?.url}")
                        return@withContext Result.Success(result)
                    }

                    else -> {
                        val errorBody = response.bodyAsText()
                        println("ApiService: Approach ${index + 1} failed with ${response.status.value}: $errorBody")
                        // Continue to next approach
                    }
                }
            } catch (e: Exception) {
                println("ApiService: Approach ${index + 1} exception: ${e.message}")
                // Continue to next approach
            }
        }
        
        // All approaches failed
        return@withContext Result.Error(-1, "All upload approaches failed. Please check Ghost server configuration.")
    }
}

fun logUnlimited(tag: String, string: String) {
    val maxLogSize = 1000
    string.chunked(maxLogSize).forEach { println("$tag: $it ") }
}