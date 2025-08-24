package com.example.chatai
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class OllamaClient(
    private val baseUrl: String = "http://192.168.29.55:11434",
    private val model: String = "phi" // Try with :latest tag
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    interface ChatCallback {
        fun onResponse(response: String)
        fun onError(error: String)
        fun onPartialResponse(partialResponse: String) // For streaming
    }

    fun sendMessage(
        message: String,
        callback: ChatCallback,
        useStreaming: Boolean = true
    ) {
        val json = JSONObject().apply {
            put("model", model)
            put("prompt", message)
            put("stream", useStreaming)
        }

        val requestBody = json.toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$baseUrl/api/generate")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback.onError("Network error: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    callback.onError("HTTP ${response.code}: ${response.message}\nDetails: $errorBody")
                    return
                }

                response.body?.let { responseBody ->
                    if (useStreaming) {
                        handleStreamingResponse(responseBody, callback)
                    } else {
                        handleSingleResponse(responseBody, callback)
                    }
                } ?: callback.onError("Empty response")
            }
        })
    }

    private fun handleStreamingResponse(responseBody: ResponseBody, callback: ChatCallback) {
        val fullResponse = StringBuilder()

        try {
            responseBody.source().use { source ->
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (line.isNotEmpty()) {
                        try {
                            val json = JSONObject(line)
                            val responsePart = json.optString("response", "")

                            if (responsePart.isNotEmpty()) {
                                fullResponse.append(responsePart)
                                callback.onPartialResponse(fullResponse.toString())
                            }

                            // Check if this is the final response
                            if (json.optBoolean("done", false)) {
                                callback.onResponse(fullResponse.toString())
                                break
                            }
                        } catch (e: Exception) {
                            // Skip malformed JSON lines
                            continue
                        }
                    }
                }
            }
        } catch (e: Exception) {
            callback.onError("Streaming error: ${e.message}")
        }
    }

    private fun handleSingleResponse(responseBody: ResponseBody, callback: ChatCallback) {
        try {
            val jsonResponse = JSONObject(responseBody.string())
            val response = jsonResponse.optString("response", "")

            if (response.isNotEmpty()) {
                callback.onResponse(response)
            } else {
                callback.onError("Empty response from AI")
            }
        } catch (e: Exception) {
            callback.onError("Parse error: ${e.message}")
        }
    }

    // Check if Ollama server is running
    fun checkConnection(callback: (Boolean, String) -> Unit) {
        val request = Request.Builder()
            .url("$baseUrl/api/version")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false, "Cannot connect to Ollama: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    callback(true, "Connected to Ollama")
                } else {
                    callback(false, "Ollama server error: ${response.code}")
                }
            }
        })
    }

    // Get available models
    fun getAvailableModels(callback: (List<String>) -> Unit) {
        val request = Request.Builder()
            .url("$baseUrl/api/tags")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(emptyList())
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    response.body?.let { body ->
                        val json = JSONObject(body.string())
                        val models = json.getJSONArray("models")
                        val modelList = mutableListOf<String>()

                        for (i in 0 until models.length()) {
                            val model = models.getJSONObject(i)
                            modelList.add(model.getString("name"))
                        }

                        callback(modelList)
                    } ?: callback(emptyList())
                } catch (e: Exception) {
                    callback(emptyList())
                }
            }
        })
    }
}