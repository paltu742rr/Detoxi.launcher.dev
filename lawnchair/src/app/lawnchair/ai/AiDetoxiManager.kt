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
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.util.LruCache
import com.android.launcher3.icons.BitmapInfo

class AiDetoxiManager private constructor(private val context: Context) {

    private val prefs get() = context.getSharedPreferences("ai_detoxi_prefs", Context.MODE_PRIVATE)

    private val iconCache = LruCache<Bitmap, Bitmap>(250)

    var isEnabled: Boolean
        get() = prefs.getBoolean("ai_detoxi_enabled", false)
        set(value) {
            prefs.edit().putBoolean("ai_detoxi_enabled", value).apply()
            iconCache.evictAll()
        }

    var styleMode: Int
        get() = prefs.getInt("ai_detoxi_style", STYLE_MONOCHROME)
        set(value) {
            prefs.edit().putInt("ai_detoxi_style", value).apply()
            iconCache.evictAll()
        }

    fun clearCache() {
        iconCache.evictAll()
    }

    /**
     * Hardware-accelerated on-device minimalist icon processor.
     * Takes the app's original bitmap and converts it into a clean monochrome or matte detox glyph.
     */
    fun processBitmap(source: Bitmap): Bitmap {
        val cached = iconCache.get(source)
        if (cached != null && !cached.isRecycled) {
            return cached
        }

        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val colorMatrix = ColorMatrix()
        if (styleMode == STYLE_MONOCHROME) {
            colorMatrix.setSaturation(0f)
            val matrix = colorMatrix.array
            val scale = 1.15f
            val translate = -20f
            for (i in 0..2) {
                matrix[i * 5 + i] = scale
                matrix[i * 5 + 4] = translate
            }
        } else {
            // Matte Detox mode (removes dopamine-inducing saturated colors)
            colorMatrix.setSaturation(0.08f)
        }

        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(source, 0f, 0f, paint)

        iconCache.put(source, output)
        return output
    }

    /**
     * Converts a BitmapInfo to its Detox Minimalist variant.
     */
    fun getDetoxBitmapInfo(original: BitmapInfo): BitmapInfo {
        if (!isEnabled) return original
        val src = original.icon ?: return original
        val processed = processBitmap(src)
        return BitmapInfo.of(processed, original.color)
    }

    fun queueIcon(packageName: String, bitmap: Bitmap) {
        if (!isEnabled) return
        processBitmap(bitmap)
    }

    companion object {
        const val STYLE_MONOCHROME = 0
        const val STYLE_MATTE_DETOX = 1

        @Volatile
        private var instance: AiDetoxiManager? = null

        @JvmStatic
        fun getInstance(context: Context): AiDetoxiManager {
            return instance ?: synchronized(this) {
                instance ?: AiDetoxiManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
