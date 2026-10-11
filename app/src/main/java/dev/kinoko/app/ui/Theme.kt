package dev.kinoko.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import dev.kinoko.app.R
import androidx.compose.ui.unit.sp

// kinoko's own colours, as in the terminal: amanita red, cream, a deep blue-grey night.
object Kinoko {
    val night = Color(0xFF15161E)
    val panel = Color(0xFF1F2130)
    val raised = Color(0xFF282A3B)
    val line = Color(0xFF343750)
    val text = Color(0xFFD6DAE8)
    val muted = Color(0xFF8A90AE)
    val red = Color(0xFFE82424)
    val softRed = Color(0xFFEC6868)
    val cream = Color(0xFFEEDEBA)
    val blue = Color(0xFF82AAFF)
    val green = Color(0xFFA6D189)
    val amber = Color(0xFFEF9F76)
    val yellow = Color(0xFFF2CC60)
    val code = Color(0xFF89DCEB)
}

/** Noto Sans Mono, subset (Latin, punctuation, arrows, box drawing, blocks, shapes): Android's own
 *  monospace font has no box drawing, so diagrams and tables fell apart. OFL-licensed. */
val Mono = FontFamily(Font(R.font.kinoko_mono_regular), Font(R.font.kinoko_mono_bold, FontWeight.Bold))

private val scheme = darkColorScheme(
    primary = Kinoko.softRed,
    onPrimary = Kinoko.night,
    secondary = Kinoko.cream,
    onSecondary = Kinoko.night,
    tertiary = Kinoko.blue,
    background = Kinoko.night,
    onBackground = Kinoko.text,
    surface = Kinoko.night,
    onSurface = Kinoko.text,
    surfaceVariant = Kinoko.panel,
    onSurfaceVariant = Kinoko.muted,
    surfaceContainer = Kinoko.panel,
    surfaceContainerHigh = Kinoko.raised,
    surfaceContainerLow = Kinoko.panel,
    outline = Kinoko.line,
    outlineVariant = Kinoko.line,
    error = Kinoko.red,
)

@Composable
fun KinokoTheme(content: @Composable () -> Unit) {
    val base = Typography()
    MaterialTheme(
        colorScheme = scheme,
        typography = base.copy(
            bodyLarge = base.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
            bodyMedium = base.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
            labelSmall = TextStyle(fontFamily = Mono, fontSize = 12.sp, lineHeight = 16.sp),
        ),
        content = content,
    )
}
