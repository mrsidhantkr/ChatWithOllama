package com.example.chatai

import android.icu.util.TimeUnit
import okhttp3.OkHttpClient

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
//import java.util.concurrent.TimeUnit


class GeminiClient (
    private val apiKey: String,
    private val model: String = "gemini"
    ){

    private val baseUrl = ""


    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()


    interface ChatCallback{
        fun OnResponse(response: String)
        fun onError(error:String)
        fun onPartialResponse(partialResponse:String)
    }

    fun sendMessage(
        message: String,
        callback: ChatCallback,
        useStreaming: Boolean = true

    ){
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

            put("generateconfig", JSONObject().apply {
                put("temprature",0.9)
                put("topK",1.0)
                put("topP",1.0)
                put("maxOutputTokens",2048)
                put("stopSequence", JSONArray() )
            })
            put("safetySettings", JSONArray().apply {
                // Add safety settings if needed
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

        val url = if(useStreaming){
            "$baseUrl/$model:stramGenerateContent?key=$apiKey"
        }else{
            "$baseUrl/$model:generateContent?key=$apiKey"

        }


        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback{
            override fun onFailure(call: Call, e: IOException) {
                callback.onError("Network error: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                
            }
        })

    }
}