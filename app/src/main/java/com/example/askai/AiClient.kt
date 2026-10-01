package com.example.askai

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Talks to a single AI API call per question, using whatever key/URL/model
 * the user entered in MainActivity. No data is stored anywhere except this
 * one request/response — there is no background logging or history.
 */
object AiClient {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun ask(
        apiKey: String,
        baseUrl: String,
        model: String,
        question: String,
        bitmap: Bitmap,
        callback: (answer: String?, error: String?) -> Unit
    ) {
        executor.execute {
            try {
                val base64Image = bitmapToBase64(bitmap)

                val payload = JSONObject().apply {
                    put("model", model)
                    put("messages", JSONArray().put(
                        JSONObject().apply {
                            put("role", "user")
                            put("content", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("type", "text")
                                    put("text", question)
                                })
                                put(JSONObject().apply {
                                    put("type", "image_url")
                                    put("image_url", JSONObject().apply {
                                        put("url", "data:image/jpeg;base64,$base64Image")
                                    })
                                })
                            })
                        }
                    ))
                    put("max_tokens", 600)
                }

                val url = URL(baseUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Authorization", "Bearer $apiKey")
                conn.doOutput = true
                conn.connectTimeout = 20000
                conn.readTimeout = 30000

                conn.outputStream.use { it.write(payload.toString().toByteArray()) }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val responseText = stream.bufferedReader().use { it.readText() }

                if (code !in 200..299) {
                    postResult(callback, null, "API error ($code): $responseText")
                    return@execute
                }

                val answer = parseAnswer(responseText)
                postResult(callback, answer, null)
            } catch (e: Exception) {
                postResult(callback, null, e.message ?: "Unknown error")
            }
        }
    }

    private fun parseAnswer(responseText: String): String {
        val json = JSONObject(responseText)
        val choices = json.optJSONArray("choices")
        if (choices != null && choices.length() > 0) {
            val message = choices.getJSONObject(0).optJSONObject("message")
            val content = message?.opt("content")
            return when (content) {
                is String -> content
                is JSONArray -> {
                    val sb = StringBuilder()
                    for (i in 0 until content.length()) {
                        val part = content.optJSONObject(i)
                        val text = part?.optString("text")
                        if (!text.isNullOrBlank()) sb.append(text)
                    }
                    sb.toString()
                }
                else -> responseText
            }
        }
        return responseText
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    private fun postResult(callback: (String?, String?) -> Unit, answer: String?, error: String?) {
        mainHandler.post { callback(answer, error) }
    }
}
