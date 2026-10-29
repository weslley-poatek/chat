package br.com.weslleycampos.chat.data.fuelix

import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import io.ktor.client.HttpClient
import org.koin.core.annotation.Single

/** How long connecting to Fuelix may take. */
private const val CONNECT_TIMEOUT_MILLIS = 15_000L

/** How long a reply may go silent: a model can think a while before its first words. */
private const val SOCKET_TIMEOUT_MILLIS = 120_000L

/**
 * The app's one [FuelixClient]: Koog's OpenAI client at [FuelixClient.BASE_URL], on Ktor with the
 * platform's engine (OkHttp on Android, NSURLSession on iOS), and the key `:data:settings` provides.
 * Connecting and a silent connection time out sooner than Koog's defaults; a whole reply keeps
 * Koog's 15 minutes.
 */
@Single
internal fun fuelixClient(keys: FuelixKeySource): FuelixClient = KoogFuelixClient(
    keys = keys,
    settings = OpenAIClientSettings(
        baseUrl = FuelixClient.BASE_URL,
        timeoutConfig = ConnectionTimeoutConfig(
            connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS,
            socketTimeoutMillis = SOCKET_TIMEOUT_MILLIS,
        ),
    ),
    http = KtorKoogHttpClient.Factory(baseClient = HttpClient()),
)
