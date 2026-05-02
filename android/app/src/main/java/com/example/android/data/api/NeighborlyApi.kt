package com.example.android.data.api

import com.example.android.BuildConfig
import com.example.android.data.model.OptimizedRoute
import com.example.android.data.model.OptimizeRouteRequest
import com.example.android.data.model.Product
import com.example.android.data.model.ProductSearchResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.path
import io.ktor.http.takeFrom
import io.ktor.serialization.JsonConvertException
import io.ktor.serialization.kotlinx.json.json
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

interface NeighborlyApi {
    suspend fun searchProducts(
        query: String,
        page: Int = 1,
        pageSize: Int = 20
    ): Result<ProductSearchResponse>

    suspend fun getProduct(id: String): Result<Product>

    suspend fun optimizeRoute(
        productIds: List<String>,
        userLat: Double? = null,
        userLng: Double? = null,
        mode: String? = null,
        maxStops: Int? = null,
        maxRadiusMiles: Double? = null
    ): Result<OptimizedRoute>

    suspend fun getAlternatives(productId: String): Result<List<Product>>
}

sealed class NeighborlyApiError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    data object MissingBaseUrl : NeighborlyApiError(
        "API_BASE_URL is missing. Add API_BASE_URL=http://10.0.2.2:8000 to android/.env."
    )

    data class Server(val statusCode: Int, val responseBody: String?) : NeighborlyApiError(
        "Server error ($statusCode)${responseBody?.let { ": $it" }.orEmpty()}"
    )

    data class Network(override val cause: Throwable) : NeighborlyApiError(
        "Network request failed: ${cause.message ?: cause::class.java.simpleName}",
        cause
    )

    data class Decoding(override val cause: Throwable) : NeighborlyApiError(
        "Failed to decode API response: ${cause.message ?: cause::class.java.simpleName}",
        cause
    )

    data class Unexpected(override val cause: Throwable) : NeighborlyApiError(
        "Unexpected API error: ${cause.message ?: cause::class.java.simpleName}",
        cause
    )
}

class KtorNeighborlyApi(
    baseUrl: String = BuildConfig.API_BASE_URL,
    private val client: HttpClient = defaultHttpClient()
) : NeighborlyApi {
    private val normalizedBaseUrl = baseUrl.trim().trimEnd('/')

    override suspend fun searchProducts(
        query: String,
        page: Int,
        pageSize: Int
    ): Result<ProductSearchResponse> = request {
        get {
            neighborlyUrl("products")
            url.parameters.append("q", query)
            url.parameters.append("page", page.toString())
            url.parameters.append("page_size", pageSize.toString())
        }
    }

    override suspend fun getProduct(id: String): Result<Product> = request {
        get {
            neighborlyUrl("products", id)
        }
    }

    override suspend fun optimizeRoute(
        productIds: List<String>,
        userLat: Double?,
        userLng: Double?,
        mode: String?,
        maxStops: Int?,
        maxRadiusMiles: Double?
    ): Result<OptimizedRoute> = request {
        post {
            neighborlyUrl("routes", "optimize")
            contentType(ContentType.Application.Json)
            setBody(
                OptimizeRouteRequest(
                    productIds = productIds,
                    userLat = userLat,
                    userLng = userLng,
                    mode = mode,
                    maxStops = maxStops,
                    maxRadiusMiles = maxRadiusMiles
                )
            )
        }
    }

    override suspend fun getAlternatives(productId: String): Result<List<Product>> = request {
        get {
            neighborlyUrl("products", productId, "alternatives")
        }
    }

    private suspend inline fun <reified T> request(
        crossinline call: suspend HttpClient.() -> HttpResponse
    ): Result<T> {
        if (normalizedBaseUrl.isBlank()) {
            return Result.failure(NeighborlyApiError.MissingBaseUrl)
        }

        return try {
            val response = client.call()
            if (!response.status.isSuccess()) {
                return Result.failure(
                    NeighborlyApiError.Server(
                        statusCode = response.status.value,
                        responseBody = response.safeErrorBody()
                    )
                )
            }
            Result.success(response.body())
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            Result.failure(NeighborlyApiError.Network(error))
        } catch (error: JsonConvertException) {
            Result.failure(NeighborlyApiError.Decoding(error))
        } catch (error: SerializationException) {
            Result.failure(NeighborlyApiError.Decoding(error))
        } catch (error: Throwable) {
            Result.failure(NeighborlyApiError.Unexpected(error))
        }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.neighborlyUrl(vararg segments: String) {
        url {
            takeFrom(normalizedBaseUrl)
            path(*segments)
        }
    }

    private suspend fun HttpResponse.safeErrorBody(): String? {
        return runCatching { bodyAsText().takeIf { it.isNotBlank() } }.getOrNull()
    }

    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        fun defaultHttpClient(): HttpClient = HttpClient(Android) {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        explicitNulls = false
                    }
                )
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 15_000
            }
            install(Logging) {
                level = if (BuildConfig.DEBUG) LogLevel.INFO else LogLevel.NONE
            }
            expectSuccess = false
        }
    }
}
