package com.example.chatai
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class OllamaClient(
    private val baseUrl: String = "http://192.168.29.55:11434", // Try your actual IP
    // If above doesn't work, try: "http://10.0.2.2:11434" for emulator
    private val model: String = "llama2"
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
        useStreaming: Boolean = true,
        useChatApi: Boolean = true // 🔑 choose chat vs generate
    ) {
        val requestBody: RequestBody
        val endpoint: String

        if (useChatApi) {
            // ✅ Chat API expects "messages": [...]
            val messagesArray = org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", message)
                })
            }

            val json = JSONObject().apply {
                put("model", model)
                put("messages", messagesArray)
                put("stream", useStreaming)
            }

            requestBody = json.toString()
                .toRequestBody("application/json".toMediaType())
            endpoint = "$baseUrl/api/chat"

        } else {
            // ✅ Generate API expects "prompt": "..."
            val json = JSONObject().apply {
                put("model", model)
                put("prompt", message)
                put("stream", useStreaming)
            }

            requestBody = json.toString()
                .toRequestBody("application/json".toMediaType())
            endpoint = "$baseUrl/api/generate"
        }

        val request = Request.Builder()
            .url(endpoint)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback.onError("Network error: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    callback.onError("HTTP ${response.code}: ${response.message}")
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

        println("🔍 Trying to connect to: $baseUrl/api/version")

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                val errorMsg = "Cannot connect to Ollama: ${e.message}"
                println("❌ Connection failed: $errorMsg")
                callback(false, errorMsg)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: ""
                    println("✅ Connected to Ollama: $responseBody")
                    callback(true, "Connected to Ollama")
                } else {
                    val errorMsg = "Ollama server error: ${response.code}"
                    println("❌ Server error: $errorMsg")
                    callback(false, errorMsg)
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