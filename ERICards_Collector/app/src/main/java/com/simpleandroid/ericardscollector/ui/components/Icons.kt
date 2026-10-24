package com.simpleandroid.ericardscollector.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Simplified label colours for ADR classes: (fill colour, text colour)
private class LabelStyle(val fill: Color, val text: Color)

private val BLACK = Color.Black
private val WHITE = Color.White
private val DEFAULT_LABEL_STYLE = LabelStyle(Color(0xFFCCCCCC), BLACK)

private val ADR_LABEL_STYLES = mapOf(
    "1" to LabelStyle(Color(0xFFF28C28), BLACK),
    "2.1" to LabelStyle(Color(0xFFD6342C), WHITE),
    "2.2" to LabelStyle(Color(0xFF2E8B57), WHITE),
    "2.3" to LabelStyle(Color(0xFFFFFFFF), BLACK),
    "3" to LabelStyle(Color(0xFFD6342C), WHITE),
    "4.1" to LabelStyle(Color(0xFFF5F5F5), BLACK),
    "4.2" to LabelStyle(Color(0xFFF5F5F5), BLACK),
    "4.3" to LabelStyle(Color(0xFF1E63B5), WHITE),
    "5.1" to LabelStyle(Color(0xFFF2D230), BLACK),
    "5.2" to LabelStyle(Color(0xFFF2D230), BLACK),
    "6.1" to LabelStyle(Color(0xFFFFFFFF), BLACK),
    "6.2" to LabelStyle(Color(0xFFFFFFFF), BLACK),
    "7" to LabelStyle(Color(0xFFF2D230), BLACK),
    "8" to LabelStyle(Color(0xFFFFFFFF), WHITE),
    "9" to LabelStyle(Color(0xFFFFFFFF), BLACK),
)


/**
 * Picture files for the labels, taken from the app's `assets/icons` folder with the same names the
 * desktop program uses: adr_<class>.png (for example adr_2.1.png) and ghs<nn>.png (for example ghs06.png).
 * Upper and lower case do not matter. Labels without a picture are drawn instead.
 */
object IconAssets {
    private const val FOLDER = "icons"
    private const val TARGET_PIXELS = 400 // larger pictures are loaded smaller to save memory

    private var names: Map<String, String>? = null // lower case name -> real file name
    private val cache = HashMap<String, ImageBitmap?>()

    fun safeName(text: String): String = text.replace(Regex("[^0-9A-Za-z.]"), "")

    @Synchronized
    fun load(context: Context, fileName: String): ImageBitmap? {
        val available = names ?: (context.assets.list(FOLDER) ?: emptyArray()).associateBy { it.lowercase() }.also { names = it }
        val real = available[fileName.lowercase()] ?: return null
        if (cache.containsKey(real)) return cache[real]
        val picture = try {
            decode(context, "$FOLDER/$real")
        } catch (error: Exception) {
            null // a damaged file: the drawn label is used instead
        }
        cache[real] = picture
        return picture
    }

    private fun decode(context: Context, path: String): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.assets.open(path).use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= TARGET_PIXELS) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.assets.open(path).use { BitmapFactory.decodeStream(it, null, options) }?.asImageBitmap()
    }
}

/** An ADR hazard label: the picture from assets/icons, or a diamond in the colour of its class. */
@Composable
fun AdrIcon(adrClass: String, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val picture = remember(adrClass) { IconAssets.load(context, "adr_${IconAssets.safeName(adrClass)}.png") }
    if (picture != null) {
        Image(bitmap = picture, contentDescription = "ADR class $adrClass", modifier = modifier.size(size))
        return
    }

    val mainClass = adrClass.substringBefore(".")
    val style = ADR_LABEL_STYLES[adrClass] ?: ADR_LABEL_STYLES[mainClass] ?: DEFAULT_LABEL_STYLE
    val fontSize = with(LocalDensity.current) { (size * 0.2f).toSp() }

    Box(modifier.size(size)) {
        Canvas(Modifier.fillMaxSize()) {
            val side = this.size.minDimension
            val pad = side * 0.02f
            val center = side / 2f
            val diamond = Path().apply {
                moveTo(center, pad)
                lineTo(side - pad, center)
                lineTo(center, side - pad)
                lineTo(pad, center)
                close()
            }
            drawPath(diamond, style.fill)
            if (mainClass == "8") {
                // Class 8 labels are white on top and black at the bottom
                val lower = Path().apply {
                    moveTo(pad, center)
                    lineTo(side - pad, center)
                    lineTo(center, side - pad)
                    close()
                }
                drawPath(lower, BLACK)
            }
            drawPath(diamond, BLACK, style = Stroke(width = side * 0.014f, join = StrokeJoin.Miter))
        }
        Text(
            text = adrClass,
            color = style.text,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = size * 0.15f),
        )
    }
}

/** A GHS pictogram: the picture from assets/icons, or a white diamond with a red border and the code. */
@Composable
fun GhsIcon(code: String, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val picture = remember(code) { IconAssets.load(context, IconAssets.safeName(code).lowercase() + ".png") }
    if (picture != null) {
        Image(bitmap = picture, contentDescription = code, modifier = modifier.size(size))
        return
    }

    val fontSize = with(LocalDensity.current) { (size * 0.14f).toSp() }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val side = this.size.minDimension
            val pad = side * 0.04f
            val center = side / 2f
            val diamond = Path().apply {
                moveTo(center, pad)
                lineTo(side - pad, center)
                lineTo(center, side - pad)
                lineTo(pad, center)
                close()
            }
            drawPath(diamond, WHITE)
            drawPath(diamond, Color(0xFFD6342C), style = Stroke(width = side * 0.035f, join = StrokeJoin.Miter))
        }
        Text(text = code, color = BLACK, fontSize = fontSize, fontWeight = FontWeight.Bold)
    }
}

/** A small laboratory flask for the "Substances" navigation item. */
@Composable
fun FlaskIcon(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    Canvas(modifier.size(24.dp)) {
        val unit = this.size.minDimension / 24f
        val path = Path().apply {
            moveTo(9f * unit, 3f * unit)
            lineTo(15f * unit, 3f * unit)
            moveTo(10f * unit, 3f * unit)
            lineTo(10f * unit, 9.5f * unit)
            lineTo(4.5f * unit, 19f * unit)
            lineTo(19.5f * unit, 19f * unit)
            lineTo(14f * unit, 9.5f * unit)
            lineTo(14f * unit, 3f * unit)
            moveTo(7.5f * unit, 14f * unit)
            lineTo(16.5f * unit, 14f * unit)
        }
        drawPath(path, tint, style = Stroke(width = 1.8f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
