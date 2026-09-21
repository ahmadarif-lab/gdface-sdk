package com.ahmadarif.gdface.sample.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun GdLogo(fontSize: TextUnit, modifier: Modifier = Modifier) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = Gd.Primary, fontWeight = FontWeight.ExtraBold)) { append("GD") }
            withStyle(SpanStyle(color = Gd.TextPrimary, fontWeight = FontWeight.Medium)) { append("Face") }
        },
        fontSize = fontSize,
        modifier = modifier
    )
}

@Composable
fun GdCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = Gd.Surface,
        border = BorderStroke(1.dp, Gd.Outline)
    ) { Column(content = content) }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Gd.Primary,
            contentColor = Color.White,
            disabledContainerColor = Gd.Primary.copy(alpha = 0.35f),
            disabledContentColor = Color.White.copy(alpha = 0.6f)
        )
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
        } else {
            Text(text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            if (icon != null) {
                Spacer(Modifier.width(8.dp))
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Gd.Outline),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Gd.Surface, contentColor = Gd.TextPrimary)
    ) { Text(text, fontWeight = FontWeight.Medium, fontSize = 16.sp) }
}

@Composable
fun SegmentedTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Gd.Surface)
            .padding(4.dp)
    ) {
        options.forEachIndexed { index, label ->
            val active = index == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (active) Gd.Primary else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (active) Color.White else Gd.TextSecondary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun ScreenTitle(
    title: String,
    onBack: (() -> Unit)?,
    contentColor: Color = Gd.TextPrimary,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = contentColor)
            }
        } else {
            Spacer(Modifier.width(16.dp))
        }
        Text(
            title,
            color = contentColor,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        trailing()
    }
}

@Composable
fun Badge(text: String, color: Color) {
    Text(
        text,
        color = Color.White,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    )
}

@Composable
fun gdTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Gd.Primary,
    unfocusedBorderColor = Gd.Outline,
    disabledBorderColor = Gd.Outline,
    focusedContainerColor = Gd.Surface,
    unfocusedContainerColor = Gd.Surface,
    disabledContainerColor = Gd.Surface,
    focusedTextColor = Gd.TextPrimary,
    unfocusedTextColor = Gd.TextPrimary,
    disabledTextColor = Gd.TextSecondary,
    focusedPlaceholderColor = Gd.TextSecondary,
    unfocusedPlaceholderColor = Gd.TextSecondary,
    cursorColor = Gd.Primary
)

/** The round shutter button of the camera screens; shows a refresh icon once a photo is taken. */
@Composable
fun CaptureButton(retake: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(76.dp)
            .clip(CircleShape)
            .border(3.dp, Gd.Primary, CircleShape)
            .clickable(onClick = onClick)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(Gd.Primary.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            if (retake) Icon(Icons.Default.Refresh, contentDescription = "Retake", tint = Color.White)
        }
    }
}

/** A photo from a file, decoded at a size no larger than needed. */
@Composable
fun FaceImageView(file: File?, modifier: Modifier = Modifier, shape: Shape = CircleShape, maxSizePx: Int = 256) {
    val bitmap by produceState<ImageBitmap?>(null, file) {
        value = file?.let { withContext(Dispatchers.IO) { decodeSampled(it, maxSizePx)?.asImageBitmap() } }
    }
    Box(modifier.clip(shape).background(Gd.SurfaceHigh), contentAlignment = Alignment.Center) {
        val image = bitmap
        if (image != null) {
            Image(image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Default.Face, contentDescription = null, tint = Gd.TextSecondary)
        }
    }
}

/** A photo that is already in memory. */
@Composable
fun BitmapView(bitmap: Bitmap?, modifier: Modifier = Modifier, shape: Shape = CircleShape) {
    Box(modifier.clip(shape).background(Gd.SurfaceHigh), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(
                bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(Icons.Default.Face, contentDescription = null, tint = Gd.TextSecondary)
        }
    }
}

private fun decodeSampled(file: File, maxSizePx: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSizePx) sample *= 2
    return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
}

/**
 * Corner brackets, and with [oval] a dimmed frame with an oval opening, drawn over the
 * camera to show where the face should be.
 */
@Composable
fun FaceGuideOverlay(oval: Boolean, modifier: Modifier = Modifier, color: Color = Gd.Success) {
    Canvas(
        modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    ) {
        val w = size.width
        val h = size.height
        if (oval) {
            val ovalSize = Size(w * 0.62f, h * 0.78f)
            val topLeft = Offset((w - ovalSize.width) / 2, (h - ovalSize.height) / 2 - h * 0.04f)
            drawRect(Color.Black.copy(alpha = 0.35f))
            drawOval(Color.Transparent, topLeft, ovalSize, blendMode = BlendMode.Clear)
            drawOval(Color.White, topLeft, ovalSize, style = Stroke(width = 3.dp.toPx()))
        }
        val length = 34.dp.toPx()
        val inset = 18.dp.toPx()
        val stroke = 4.dp.toPx()
        fun corner(x: Float, y: Float, dx: Float, dy: Float) {
            drawLine(color, Offset(x, y), Offset(x + dx * length, y), stroke, StrokeCap.Round)
            drawLine(color, Offset(x, y), Offset(x, y + dy * length), stroke, StrokeCap.Round)
        }
        corner(inset, inset, 1f, 1f)
        corner(w - inset, inset, -1f, 1f)
        corner(inset, h - inset, 1f, -1f)
        corner(w - inset, h - inset, -1f, -1f)
    }
}

/** The text pill that sits at the bottom of a camera frame. */
@Composable
fun HintPill(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = Color.White,
        fontSize = 13.sp,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 14.dp, vertical = 6.dp)
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = Gd.TextPrimary,
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
    )
}

@Composable
fun Gap(height: Int) = Spacer(Modifier.height(height.dp))

@Composable
fun Centered(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, content = content)
}
