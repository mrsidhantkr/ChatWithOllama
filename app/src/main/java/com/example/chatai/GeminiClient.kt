package com.example.chatai

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class GeminiClient(
    private val apiKey: String,
    private val model: String = "models/gemini-1.5-flash" // default model
) {

    // ✅ Correct Google Gemini API base URL
    private val baseUrl = "https://generativelanguage.googleapis.com/v1beta"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    interface ChatCallback {
        fun onResponse(response: String)
        fun onError(error: String)
        fun onPartialResponse(partialResponse: String) // used if streaming later
    }

    fun sendMessage(
        message: String,
        callback: ChatCallback,
        useStreaming: Boolean = false
    ) {
        val json = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", message)
                        })
                    })
                })
            })

            // ✅ fixed spelling + structure
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.9)
                put("topK", 1) // must be Int
                put("topP", 1.0)
                put("maxOutputTokens", 2048)
                put("stopSequences", JSONArray())
            })

            put("safetySettings", JSONArray().apply {
                put(JSONObject().apply {
                    put("category", "HARM_CATEGORY_HARASSMENT")
                    put("threshold", "BLOCK_MEDIUM_AND_ABOVE")
                })
                put(JSONObject().apply {
                    put("category", "HARM_CATEGORY_HATE_SPEECH")
                    put("threshold", "BLOCK_MEDIUM_AND_ABOVE")
                })
                put(JSONObject().apply {
                    put("category", "HARM_CATEGORY_SEXUALLY_EXPLICIT")
                    put("threshold", "BLOCK_MEDIUM_AND_ABOVE")
                })
                put(JSONObject().apply {
                    put("category", "HARM_CATEGORY_DANGEROUS_CONTENT")
                    put("threshold", "BLOCK_MEDIUM_AND_ABOVE")
                })
            })
        }

        val requestBody = json.toString()
            .toRequestBody("application/json".toMediaType())

        // ✅ fixed typo: "streamGenerateContent"
        val url = if (useStreaming) {
            "$baseUrl/$model:streamGenerateContent?key=$apiKey"
        } else {
            "$baseUrl/$model:generateContent?key=$apiKey"
        }

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback.onError("Network error: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    callback.onError("HTTP ${response.code}: $errorBody")
                    return
                }

                response.body?.let { responseBody ->
                    try {
                        val responseText = responseBody.string()
                        val jsonResponse = JSONObject(responseText)

                        if (jsonResponse.has("candidates")) {
                            val candidates = jsonResponse.getJSONArray("candidates")
                            if (candidates.length() > 0) {
                                val candidate = candidates.getJSONObject(0)

                                // ✅ handle "content" being either object or array
                                val parts = when {
                                    candidate.has("content") && candidate.get("content") is JSONObject -> {
                                        candidate.getJSONObject("content").getJSONArray("parts")
                                    }
                                    candidate.has("content") && candidate.get("content") is JSONArray -> {
                                        candidate.getJSONArray("content").getJSONObject(0).getJSONArray("parts")
                                    }
                                    else -> JSONArray()
                                }

                                if (parts.length() > 0) {
                                    val text = parts.getJSONObject(0).optString("text", "")
                                    if (text.isNotEmpty()) {
                                        callback.onResponse(text)
                                    } else {
                                        callback.onError("No response text found")
                                    }
                                } else {
                                    callback.onError("No parts in response")
                                }
                            } else {
                                callback.onError("No candidates in response")
                            }
                        } else if (jsonResponse.has("error")) {
                            val error = jsonResponse.getJSONObject("error")
                            val errorMessage = error.optString("message", "Unknown API error")
                            callback.onError("Gemini API Error: $errorMessage")
                        } else {
                            callback.onError("Unexpected response format: $responseText")
                        }

                    } catch (e: Exception) {
                        callback.onError("Parse error: ${e.message}")
                    }
                } ?: callback.onError("Empty response")
            }
        })
    }

    // ✅ Check API key validity
    fun checkConnection(callback: (Boolean, String) -> Unit) {
        val json = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", "Hi")
                        })
                    })
                })
            })
        }

        val requestBody = json.toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$baseUrl/$model:generateContent?key=$apiKey")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false, "Cannot connect to Gemini: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    callback(true, "Connected to Gemini API")
                } else {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    callback(false, "Gemini API error: ${response.code} - $errorBody")
                }
            }
        })
    }

    // ✅ Get model info
    fun getModelInfo(callback: (String) -> Unit) {
        val request = Request.Builder()
            .url("$baseUrl/$model?key=$apiKey")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback("Unable to get model info: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    response.body?.let { body ->
                        val json = JSONObject(body.string())
                        val displayName = json.optString("displayName", model)
                        val description = json.optString("description", "Google's Gemini AI model")
                        callback("Model: $displayName\n$description")
                    } ?: callback("Model: $model")
                } catch (e: Exception) {
                    callback("Model: $model (Info unavailable)")
                }
            }
        })
    }
}
