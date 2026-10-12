package dev.kinoko.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// Kinoko, the app's mushroom (the launcher icon's shapes, 108 units), as each profile shows it: the
// same mushroom with its own face. Amanita goes super saiyan, Psilocybe is somewhere else entirely,
// Shiitake codes in glasses, Morchella reviews through round gold ones, Cordyceps conducts the
// orchestra and Mycena is gone before you blink. No profile (or one of the user's own): the plain smile.

private val CapRed = Color(0xFFE82424)
private val Cream = Color(0xFFEEDEBA)
private val Ink = Color(0xFF15161E)
private val Blush = Color(0xFFF28B8B)
private val Gold = Color(0xFFE5B94A)

private fun path(d: String): Path = PathParser().parsePathString(d).toPath()

private val STEM = path("M43,55 L65,55 Q67,71 69,80 Q54,86 39,80 Q41,71 43,55 Z")
private val CAP = path("M26,58 Q26,29 54,27 Q82,29 82,58 Q54,64 26,58 Z")
private val SPOTS = listOf(Triple(41f, 40f, 4f), Triple(59f, 35f, 3f), Triple(69f, 48f, 3.5f), Triple(52f, 48f, 2.5f), Triple(34f, 52f, 2f))
/** Psilocybe's spots: every colour it is seeing. */
private val TRIP = listOf(Color(0xFFFF7AD9), Color(0xFF6EE7F9), Color(0xFFFDE047), Color(0xFFB794F6), Color(0xFF86EFAC))
private val EYES = listOf(49f, 59f)
private const val FRAME = 76f

@Composable
fun Mushroom(profile: String?, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        // Framed on the mushroom (16..92 of the icon's 108), so its face reads at a small size.
        val k = min(size.width, size.height) / FRAME
        withTransform({
            translate((size.width - FRAME * k) / 2 - 16 * k, (size.height - FRAME * k) / 2 - 12 * k)
            scale(k, k, Offset.Zero)
        }) {
            when (profile) {
                "amanita" -> aura()
                "psilocybe" -> sparkles()
                "mycena" -> speed()
            }
            drawPath(STEM, Cream)
            drawPath(CAP, CapRed)
            SPOTS.forEachIndexed { i, (x, y, r) -> drawCircle(if (profile == "psilocybe") TRIP[i] else Cream, r, Offset(x, y)) }
            when (profile) {
                "amanita" -> rage()
                "psilocybe" -> narnia()
                "shiitake" -> coder()
                "morchella" -> reviewer()
                "cordyceps" -> orchestrator()
                "mycena" -> quick()
                else -> plain()
            }
        }
    }
}

private fun DrawScope.line(d: String, width: Float = 1.4f, color: Color = Ink) =
    drawPath(path(d), color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))

private fun DrawScope.eyes(ry: Float = 2.6f, y: Float = 67f, only: List<Float> = EYES) {
    for (x in only) {
        drawOval(Ink, Offset(x - 2.2f, y - ry), Size(4.4f, 2 * ry))
        drawCircle(Color.White, 0.8f, Offset(x + 0.8f, y - ry * 0.4f))
    }
}

private fun DrawScope.cheeks(color: Color = Blush.copy(alpha = 0.75f), grow: Float = 1f) {
    for (x in listOf(45.5f, 62.5f)) drawOval(color, Offset(x - 2.6f * grow, 71.5f - 1.6f * grow), Size(5.2f * grow, 3.2f * grow))
}

private fun DrawScope.smile() = line("M51.5,72.5 Q54,75.2 56.5,72.5")

private fun DrawScope.plain() {
    cheeks()
    eyes()
    smile()
}

/** Super saiyan: a golden aura flaring up behind it, in flames. */
private val TONGUES = floatArrayOf(0.6f, 1f, 0.75f, 0.95f, 0.7f, 1f, 0.8f, 0.9f, 0.65f, 1f, 0.7f, 0.85f)

private fun DrawScope.aura() {
    val c = Offset(54f, 56f)
    val polar = { a: Double, r: Double -> Offset(c.x + (r * cos(a)).toFloat(), c.y + (r * sin(a)).toFloat()) }
    for ((grow, brush) in listOf(
        1f to Brush.verticalGradient(listOf(Color(0xE6FFF176), Color(0xF2FFC400), Color(0xFFFFB300)), startY = 12f, endY = 84f),
        0.8f to Brush.verticalGradient(listOf(Color(0xCCFFFDE7), Color(0xF2FFF59D)), startY = 18f, endY = 84f),
    )) {
        val flame = Path()
        val n = TONGUES.size
        // Around from the bottom: a smooth base under the mushroom, flames growing taller upwards.
        for (i in 0..n) {
            val a = PI / 2 + (i.toDouble() / n) * 2 * PI
            val up = max(0.0, -sin(a))
            val base = 30.0 * grow
            if (i == 0) flame.moveTo(polar(a, base).x, polar(a, base).y)
            if (i == n) break
            val next = PI / 2 + ((i + 1).toDouble() / n) * 2 * PI
            val tipAngle = (a + next) / 2 - 0.12 // the flames lean, as if rising
            val tipUp = max(0.0, -sin(tipAngle))
            val tip = base + (4 + 22 * tipUp * tipUp) * TONGUES[i] * grow
            val p = polar(tipAngle, tip)
            val c1 = polar(a + (next - a) * 0.25, base + (tip - base) * 0.55)
            val c2 = polar(a + (next - a) * 0.75, base + (tip - base) * 0.35)
            flame.quadraticTo(c1.x, c1.y, p.x, p.y)
            val end = polar(next, base + 0 * up)
            flame.quadraticTo(c2.x, c2.y, end.x, end.y)
        }
        flame.close()
        drawPath(flame, brush)
    }
}

/** Angry: brows down, eyes narrowed, teeth clenched, a vein on its cap. */
private fun DrawScope.rage() {
    line("M45.4,62.4 L51.6,64.8", 1.9f)
    line("M62.6,62.4 L56.4,64.8", 1.9f)
    eyes(ry = 1.7f, y = 67.4f)
    drawRoundRect(Color.White, Offset(50.3f, 71.4f), Size(7.4f, 3.2f), CornerRadius(1f))
    drawRoundRect(Ink, Offset(50.3f, 71.4f), Size(7.4f, 3.2f), CornerRadius(1f), style = Stroke(0.8f))
    line("M52.8,71.6 L52.8,74.4", 0.6f)
    line("M55.2,71.6 L55.2,74.4", 0.6f)
    line("M50.6,73 L57.4,73", 0.5f)
    // 💢 on its cap
    val vein = Color(0xFF6A0000)
    line("M74.6,34.4 Q74.6,36.4 76.6,36.4", 1.5f, vein)
    line("M71.4,34.4 Q71.4,36.4 69.4,36.4", 1.5f, vein)
    line("M74.6,41.6 Q74.6,39.6 76.6,39.6", 1.5f, vein)
    line("M71.4,41.6 Q71.4,39.6 69.4,39.6", 1.5f, vein)
}

/** Narnia: little stars of every colour around it. */
private fun DrawScope.sparkles() {
    val stars = listOf(Triple(Offset(23f, 26f), 5f, TRIP[0]), Triple(Offset(85f, 22f), 4.5f, TRIP[1]), Triple(Offset(86f, 70f), 4f, TRIP[3]), Triple(Offset(22f, 72f), 3.5f, TRIP[2]), Triple(Offset(70f, 17f), 2.8f, TRIP[4]), Triple(Offset(37f, 17f), 2.4f, TRIP[1]))
    for ((p, r, color) in stars) {
        val star = Path().apply {
            moveTo(p.x, p.y - r)
            quadraticTo(p.x, p.y, p.x + r, p.y)
            quadraticTo(p.x, p.y, p.x, p.y + r)
            quadraticTo(p.x, p.y, p.x - r, p.y)
            quadraticTo(p.x, p.y, p.x, p.y - r)
            close()
        }
        drawPath(star, color)
    }
}

/** Heavy lids, a huge grin, the cheeks on fire: somewhere past the wardrobe. */
private fun DrawScope.narnia() {
    cheeks(Color(0xFFFF8FB1), grow = 1.25f)
    for (x in EYES) {
        drawOval(Ink, Offset(x - 2.4f, 64.6f), Size(4.8f, 5f))
        drawCircle(Color.White, 0.75f, Offset(x + 0.9f, 68.3f))
        // The lid, half down.
        drawRect(Cream, Offset(x - 2.8f, 64f), Size(5.6f, 3.1f))
        line("M${x - 2.9f},67.1 Q$x,66.4 ${x + 2.9f},67.1", 1.1f)
    }
    drawPath(path("M49.8,71.4 Q54,72.8 58.2,71.4 Q57.8,77 54,77.1 Q50.2,77 49.8,71.4 Z"), Ink)
    drawOval(Color(0xFFFF7AA8), Offset(52f, 74.6f), Size(4f, 2.2f))
}

/** The coder: glasses on, its laptop beside it. */
private fun DrawScope.coder() {
    cheeks()
    eyes()
    smile()
    laptop()
    for (x in EYES) {
        drawRoundRect(Color.White.copy(alpha = 0.12f), Offset(x - 3.7f, 63.4f), Size(7.4f, 7f), CornerRadius(2f))
        drawRoundRect(Ink, Offset(x - 3.7f, 63.4f), Size(7.4f, 7f), CornerRadius(2f), style = Stroke(0.9f))
    }
    line("M52.7,66.4 Q54,65.4 55.3,66.4", 0.9f)
}

private fun DrawScope.laptop() {
    drawRoundRect(Color(0xFF3A3F55), Offset(66f, 64.5f), Size(19.5f, 13.5f), CornerRadius(1.4f))
    drawRect(Ink, Offset(67.3f, 65.8f), Size(16.9f, 10.9f))
    // Code on its screen.
    for ((y, x2, color) in listOf(Triple(68f, 76f, Color(0xFFA6D189)), Triple(70.3f, 81f, TRIP[1]), Triple(72.6f, 79f, Color(0xFFE5B94A)), Triple(74.9f, 74f, TRIP[0]))) {
        val indent = if (y == 70.3f || y == 72.6f) 71f else 68.6f
        line("M$indent,$y L$x2,$y", 0.9f, color)
    }
    drawPath(path("M63.6,78 L87.9,78 L86.2,81.4 L65.3,81.4 Z"), Color(0xFF6B7194))
    line("M73.4,79.7 L78.1,79.7", 0.6f, Color(0xFF3A3F55))
}

/** The reviewer: round gold glasses, one brow up, not convinced. */
private fun DrawScope.reviewer() {
    cheeks(Blush.copy(alpha = 0.4f))
    eyes()
    for (x in EYES) drawCircle(Gold, 3.6f, Offset(x, 67f), style = Stroke(0.9f))
    line("M52.6,66.6 Q54,65.6 55.4,66.6", 0.9f, Gold)
    line("M45.8,62 Q49,59.4 52.2,61.4", 1.2f)
    line("M56.2,62.6 L61.8,62.6", 1.2f)
    line("M51.4,73.6 Q54,73 56.9,73.9", 1.2f)
}

/** The orchestrator: conducting, baton up, a bow tie, the music around it. */
private fun DrawScope.orchestrator() {
    cheeks()
    eyes()
    smile()
    // The bow tie, under the smile.
    val bow = Color(0xFFC62828)
    drawPath(path("M49.6,76.6 L54,78.6 L49.6,80.6 Z"), bow)
    drawPath(path("M58.4,76.6 L54,78.6 L58.4,80.6 Z"), bow)
    drawCircle(Color(0xFF8B0000), 1.1f, Offset(54f, 78.6f))
    // The baton, up in the air.
    line("M67.5,76 L70,72.8", 2.2f, Color(0xFF6B7194))
    line("M70,72.8 L84,56", 0.9f, Color.White)
    // The music.
    for ((p, color) in listOf(Offset(83f, 36f) to TRIP[1], Offset(24f, 30f) to TRIP[3], Offset(74f, 22f) to TRIP[0])) {
        drawOval(color, Offset(p.x - 2.2f, p.y - 1.5f), Size(4.4f, 3f))
        line("M${p.x + 1.9f},${p.y} L${p.x + 1.9f},${p.y - 7.5f}", 0.9f, color)
        line("M${p.x + 1.9f},${p.y - 7.5f} Q${p.x + 5.2f},${p.y - 5.8f} ${p.x + 4.4f},${p.y - 2.8f}", 0.9f, color)
    }
}

/** Light and fast: eyes shut with joy, already moving. */
private fun DrawScope.speed() {
    val trail = Cream.copy(alpha = 0.55f)
    line("M18,36 L25,36", 1.6f, trail)
    line("M17,46 L23,46", 1.6f, trail)
    line("M19,64 L33,64", 1.6f, trail)
    line("M22,73 L35,73", 1.6f, trail)
}

private fun DrawScope.quick() {
    cheeks()
    line("M46.8,67.8 Q49,65.2 51.2,67.8", 1.3f)
    line("M56.8,67.8 Q59,65.2 61.2,67.8", 1.3f)
    line("M50.6,72 Q54,76.4 57.4,72", 1.4f)
}
