/*
 * Copyright 2026, Lawnchair / Detoxi Launcher
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class AiDetoxiManager private constructor(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val queue = ConcurrentLinkedQueue<Pair<String, Bitmap>>()
    private val isRunning = AtomicBoolean(false)
    private val totalAppsCount = AtomicInteger(0)
    private val processedCount = AtomicInteger(0)

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "AiDetoxiManager"

        @Volatile
        private var instance: AiDetoxiManager? = null

        fun getInstance(context: Context): AiDetoxiManager {
            return instance ?: synchronized(this) {
                instance ?: AiDetoxiManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs get() = context.getSharedPreferences("ai_detoxi_prefs", Context.MODE_PRIVATE)

    var isEnabled: Boolean
        get() = prefs.getBoolean("ai_detoxi_enabled", false)
        set(value) {
            prefs.edit().putBoolean("ai_detoxi_enabled", value).apply()
            if (value) startProcessingLoop()
        }

    var endpointUrl: String
        get() = prefs.getString(
            "ai_detoxi_endpoint",
            "https://api.cloudflare.com/client/v4/accounts/77ac2d21a01a5f50ff8d487ed477d639/ai/run/",
        ) ?: "https://api.cloudflare.com/client/v4/accounts/77ac2d21a01a5f50ff8d487ed477d639/ai/run/"
        set(value) = prefs.edit().putString("ai_detoxi_endpoint", value).apply()

    var apiKey: String
        get() = prefs.getString(
            "ai_detoxi_apikey",
            "cfut_gvoTLkkxDZjO8I4zaAvX3C5NSEoS2FYCZwEniQ1za8536415",
        ) ?: "cfut_gvoTLkkxDZjO8I4zaAvX3C5NSEoS2FYCZwEniQ1za8536415"
        set(value) = prefs.edit().putString("ai_detoxi_apikey", value).apply()

    var modelName: String
        get() = prefs.getString("ai_detoxi_model", "@cf/meta/llama-3.1-8b-instruct")
            ?: "@cf/meta/llama-3.1-8b-instruct"
        set(value) = prefs.edit().putString("ai_detoxi_model", value).apply()

    val progressState = MutableStateFlow(Triple(0, 0, ""))


    fun queueIcon(packageName: String, bitmap: Bitmap) {
        if (!isEnabled) return
        val cacheFile = File(context.cacheDir, "ai_detoxi_$packageName.png")
        if (cacheFile.exists()) return

        if (!queue.any { it.first == packageName }) {
            queue.offer(Pair(packageName, bitmap))
            totalAppsCount.incrementAndGet()
            startProcessingLoop()
        }
    }

    private fun startProcessingLoop() {
        if (!isRunning.compareAndSet(false, true)) return
        scope.launch {
            try {
                while (isEnabled && !queue.isEmpty()) {
                    val batch = mutableListOf<Pair<String, Bitmap>>()
                    while (batch.size < 50 && !queue.isEmpty()) {
                        queue.poll()?.let { batch.add(it) }
                    }

                    if (batch.isEmpty()) break

                    val startTime = System.currentTimeMillis()
                    for ((packageName, bitmap) in batch) {
                        if (!isEnabled) break
                        try {
                            processWithCloudflareAi(packageName, bitmap)
                            val currentProcessed = processedCount.incrementAndGet()
                            val total = totalAppsCount.get()
                            progressState.value = Triple(currentProcessed, total, packageName)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing icon for $packageName", e)
                        }
                    }

                    val elapsed = System.currentTimeMillis() - startTime
                    if (elapsed < 60000 && !queue.isEmpty()) {
                        delay(60000 - elapsed)
                    }
                }
            } finally {
                isRunning.set(false)
            }
        }
    }

    private fun processWithCloudflareAi(packageName: String, bitmap: Bitmap) {
        val url = endpointUrl + modelName
        val jsonBody = JSONObject().apply {
            put("prompt", "Generate clean minimalist vector glyph representation icon description for app $packageName")
        }

        val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        try {
            client.newCall(request).execute().use { _ ->
                val cacheFile = File(context.cacheDir, "ai_detoxi_$packageName.png")
                FileOutputStream(cacheFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            }
        } catch (e: Exception) {
            val cacheFile = File(context.cacheDir, "ai_detoxi_$packageName.png")
            FileOutputStream(cacheFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }
    }
}
