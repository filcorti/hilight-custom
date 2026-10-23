package com.hilight.studio

import android.widget.Toast
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

val PRESET_COLORS = listOf(
    0xFFFF1744, 0xFFFF6D00, 0xFFFFD600, 0xFF00E676, 0xFF00E5FF,
    0xFF2979FF, 0xFF7C4DFF, 0xFFFF4081, 0xFFFFFFFF, 0xFFFF80AB,
).map { it.toInt() }

internal class PreviewLauncher(
    private val launch: (Pattern, Int, Int, Float, Int, Ambient?) -> Unit,
) {
    operator fun invoke(
        pattern: Pattern, color: Int, speedMs: Int, brightness: Float, durationMs: Int,
        look: Ambient? = null,
    ) = launch(pattern, color, speedMs, brightness, durationMs, look)
}

/** Launches every in-app preview through one truthful guard check without adding UI controls. */
@Composable
internal fun rememberPreviewLauncher(store: Store): PreviewLauncher {
    val context = LocalContext.current.applicationContext
    val resources = LocalResources.current
    return remember(store, context, resources) {
        PreviewLauncher { pattern, color, speedMs, brightness, durationMs, look ->
            val reason = store.previewSuppressionReason()
            if (reason == null) {
                if (look != null) store.previewLook(look, durationMs)
                else store.preview(pattern, color, speedMs, brightness, durationMs)
            } else {
                Toast.makeText(
                    context,
                    resources.getString(
                        R.string.test_blocked_by_guard,
                        resources.getString(reason.shortRes),
                    ),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
}

/**
 * Swatches plus hue / saturation / intensity.
 *
 * The selected swatch grows on a spring and keeps a ring, the way Pixel's wallpaper and theme
 * pickers behave, and the hue track is the gradient itself rather than a tinted bar.
 */
@Composable
fun ColorPicker(
    color: Int,
    onColor: (Int) -> Unit,
    // Callers editing a named colour pass their own heading, so this stays a String. The default is
    // read from resources, which a composable default expression is allowed to do.
    label: String = stringResource(R.string.widget_colour),
) {
    val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(color, it) }
    val haptics = LocalHapticFeedback.current
    var editingHex by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val clipboardManager = remember { context.getSystemService(android.content.ClipboardManager::class.java) }
    val hex = "#%06X".format(0xFFFFFF and color)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Box(
                Modifier
                    .size(30.dp)
                    .background(Color(color), CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            )
        }

        // HEX Row + Copy/Paste
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { editingHex = true }
            ) {
                Box(
                    Modifier
                        .size(24.dp)
                        .background(Color(color), CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                )
                Text(
                    hex,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = {
                        clipboardManager?.setPrimaryClip(android.content.ClipData.newPlainText("color", hex))
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        Toast.makeText(context, "Copiato: $hex", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Rounded.ContentCopy,
                        contentDescription = "Copia HEX",
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = {
                        val clip = clipboardManager?.primaryClip
                        if (clip != null && clip.itemCount > 0) {
                            val text = clip.getItemAt(0).text?.toString()?.trim()
                            runCatching {
                                val targetHex = if (text?.startsWith("#") == true) text else "#$text"
                                val parsed = android.graphics.Color.parseColor(targetHex)
                                onColor(0xFF000000.toInt() or parsed)
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                Toast.makeText(context, "Incollato: $targetHex", Toast.LENGTH_SHORT).show()
                            }.onFailure {
                                Toast.makeText(context, "Codice HEX non valido dagli appunti", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Rounded.ContentPaste,
                        contentDescription = "Incolla HEX",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PRESET_COLORS.forEach { c ->
                val selected = c == color
                val scale by animateFloatAsState(
                    if (selected) 1.16f else 1f,
                    spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMedium),
                    label = "swatch",
                )
                Box(
                    Modifier
                        .scale(scale)
                        .size(34.dp)
                        .background(Color(c), CircleShape)
                        .border(
                            if (selected) 3.dp else 1.dp,
                            if (selected) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                            CircleShape,
                        )
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onColor(c)
                        }
                )
            }
        }

        // hue header + degrees indicator
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Tonalità (Hue)", style = MaterialTheme.typography.bodyLarge)
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(
                    "${hsv[0].toInt()}°",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }

        // hue: the track is the spectrum, the thumb rides on top
        Box(
            Modifier
                .fillMaxWidth()
                .height(46.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(22.dp)
                    .padding(horizontal = 6.dp)
                    .background(
                        Brush.horizontalGradient((0..6).map { Color(Renderer.hsv(it * 60f)) }),
                        CircleShape,
                    )
            )
            Slider(
                value = hsv[0],
                valueRange = 0f..359f,
                onValueChange = { h ->
                    onColor(android.graphics.Color.HSVToColor(floatArrayOf(h, hsv[1], hsv[2])))
                },
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                ),
            )
        }

        PixelSlider(stringResource(R.string.widget_saturation), hsv[1], 0f..1f, { s ->
            onColor(android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], s, hsv[2])))
        }) { stringResource(R.string.widget_percent, (it * 100).toInt()) }
        PixelSlider(stringResource(R.string.widget_intensity), hsv[2], 0.05f..1f, { v ->
            onColor(android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], hsv[1], v)))
        }) { stringResource(R.string.widget_percent, (it * 100).toInt()) }
    }

    if (editingHex) {
        var textInput by remember { mutableStateOf(hex) }
        AlertDialog(
            onDismissRequest = { editingHex = false },
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text("Inserisci Codice HEX") },
            text = {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    singleLine = true,
                    label = { Text("HEX (es. #FF5722)") },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        runCatching {
                            val targetHex = if (textInput.startsWith("#")) textInput else "#$textInput"
                            val parsed = android.graphics.Color.parseColor(targetHex)
                            onColor(0xFF000000.toInt() or parsed)
                            editingHex = false
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }.onFailure {
                            Toast.makeText(context, "Formato HEX non valido", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) { ButtonLabel("Salva") }
            },
            dismissButton = {
                TextButton(onClick = { editingHex = false }) {
                    ButtonLabel("Annulla")
                }
            }
        )
    }
}

/**
 * Eight saturated LED colours derived from the app's current Material You scheme — which, with
 * wallpaper colours on, is derived from the wallpaper itself.
 *
 * The scheme's key hues are taken and their saturation and value pushed up, because container tones
 * are pale by design and pale is nearly invisible on an LED.
 */
@Composable
fun wallpaperLedColours(): List<Int> {
    val scheme = MaterialTheme.colorScheme
    val seeds = listOf(scheme.primary, scheme.secondary, scheme.tertiary)
        .map { android.graphics.Color.valueOf(it.red, it.green, it.blue).toArgb() }
    val hues = seeds.map { c ->
        FloatArray(3).also { android.graphics.Color.colorToHSV(c, it) }[0]
    }
    // walk between the key hues so all eight LEDs differ but stay in the wallpaper's family
    return (0 until LED_COUNT).map { i ->
        val t = i.toFloat() / LED_COUNT * hues.size
        val a = hues[t.toInt().coerceAtMost(hues.lastIndex)]
        val b = hues[(t.toInt() + 1).coerceAtMost(hues.lastIndex)]
        val hue = a + (b - a) * (t - t.toInt())
        Renderer.hsv(hue, 1f, 1f)
    }
}
