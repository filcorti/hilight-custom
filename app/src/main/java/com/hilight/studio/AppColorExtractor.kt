package com.hilight.studio

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AppColorExtractor {

    private val colorCache = mutableMapOf<String, Int>()

    suspend fun getDominantColorForPackage(context: Context, packageName: String): Int = withContext(Dispatchers.IO) {
        colorCache[packageName]?.let { return@withContext it }

        val pm = context.packageManager
        val drawable = try {
            pm.getApplicationIcon(packageName)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } ?: return@withContext 0xFF00E5FF.toInt()

        val bitmap = drawableToBitmap(drawable)
        val palette = Palette.from(bitmap).generate()

        val extractedColor = palette.vibrantSwatch?.rgb
            ?: palette.lightVibrantSwatch?.rgb
            ?: palette.dominantSwatch?.rgb
            ?: 0xFF00E5FF.toInt()

        colorCache[packageName] = extractedColor
        extractedColor
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }

        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth.coerceAtMost(96) else 96
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight.coerceAtMost(96) else 96

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}