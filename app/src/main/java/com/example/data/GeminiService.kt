package com.example.data

import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private const val TAG = "GeminiService"

@JsonClass(generateAdapter = true)
data class GeminiPart(
    @Json(name = "text") val text: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    @Json(name = "role") val role: String? = null,
    @Json(name = "parts") val parts: List<GeminiPart> = emptyList()
)

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    @Json(name = "contents") val contents: List<GeminiContent>,
    @Json(name = "systemInstruction") val systemInstruction: GeminiContent? = null
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    @Json(name = "content") val content: GeminiContent?
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    @Json(name = "candidates") val candidates: List<GeminiCandidate>? = null
)

class GeminiService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val requestAdapter = moshi.adapter(GeminiRequest::class.java)
    private val responseAdapter = moshi.adapter(GeminiResponse::class.java)

    private val systemInstruction = GeminiContent(
        parts = listOf(
            GeminiPart(
                text = "You are TuckBook AI Advisor, a dedicated school tuck-shop business assistant in Zambia. " +
                        "You advise teachers and shop owners on sales records, pricing in Zambian Kwacha (K and ngwee), " +
                        "inventory management for popular school snacks (Freezits, Maheu, crisps, biscuits, stationery), " +
                        "preventing stock shrinkage, and maximizing profit. Provide clear, concise, actionable advice."
            )
        )
    )

    suspend fun generateChatReply(
        history: List<Pair<String, String>>, // sender ("user" or "gemini") to message text
        prompt: String,
        modelName: String = "gemini-3.5-flash"
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val apiKey = BuildConfig.GEMINI_API_KEY
            if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
                return@withContext Result.failure(
                    IllegalStateException("Gemini API key is missing. Please set GEMINI_API_KEY in the Secrets panel.")
                )
            }

            val contents = mutableListOf<GeminiContent>()
            for ((sender, text) in history.takeLast(10)) {
                val role = if (sender == "user") "user" else "model"
                contents.add(
                    GeminiContent(
                        role = role,
                        parts = listOf(GeminiPart(text = text))
                    )
                )
            }
            contents.add(
                GeminiContent(
                    role = "user",
                    parts = listOf(GeminiPart(text = prompt))
                )
            )

            val geminiRequest = GeminiRequest(
                contents = contents,
                systemInstruction = systemInstruction
            )

            val jsonBody = requestAdapter.toJson(geminiRequest)
            val requestBody = jsonBody.toRequestBody("application/json".toMediaType())

            val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful || responseBody == null) {
                Log.e(TAG, "Gemini API error: HTTP ${response.code} - $responseBody")
                return@withContext Result.failure(
                    Exception("Gemini API returned error code ${response.code}: ${response.message}")
                )
            }

            val parsed = responseAdapter.fromJson(responseBody)
            val replyText = parsed?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "I could not generate a response. Please try again."

            Result.success(replyText.trim())
        } catch (e: Exception) {
            Log.e(TAG, "Error in generateChatReply", e)
            Result.failure(e)
        }
    }
}
