package com.syncdesk.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.syncdesk.app.R
import com.syncdesk.app.ui.theme.Design
import com.syncdesk.app.ui.theme.Materials
import com.syncdesk.app.ui.theme.SyncDeskShapes

/** How a hand-drawn avatar looks; ported from `PEOPLE` in `chabun.js`. */
@Immutable
data class AvatarLook(
    val background: Color,
    val cloth: Color,
    val skin: Color,
    val hair: Color,
    val hairStyle: HairStyle,
    val glasses: Boolean = false,
    val collar: Boolean = false,
)

enum class HairStyle { Short, Part, Bob, Perm }

/** The people of the design's example data. Anyone else gets the plain default person. */
object Avatars {
    val known: Map<String, AvatarLook> = mapOf(
        "김 팀장" to AvatarLook(Color(0xFFC9D8FF), Color(0xFF1F2A44), Color(0xFFF1C9A5), Color(0xFF2A2320), HairStyle.Short, glasses = true, collar = true),
        "최 수석" to AvatarLook(Color(0xFFE4DDF8), Color(0xFF3B3F55), Color(0xFFEEC4A0), Color(0xFF9C9CA6), HairStyle.Part, collar = true),
        "박 대리" to AvatarLook(Color(0xFFFFE1B8), Color(0xFF2F7F73), Color(0xFFF6D5BA), Color(0xFF3B2A22), HairStyle.Bob),
        "이 과장" to AvatarLook(Color(0xFFD3EEDD), Color(0xFF4B5D7A), Color(0xFFEDC29C), Color(0xFF1F1B1A), HairStyle.Part, collar = true),
        "어머니" to AvatarLook(Color(0xFFFFDDE8), Color(0xFFA9507A), Color(0xFFF3CDB0), Color(0xFF4A3A36), HairStyle.Perm),
    )

    fun lookFor(name: String): AvatarLook? = known[name.trim()]
}

/**
 * A round avatar for [name]: the hand-drawn person of the design for a known name, otherwise the
 * default person shape of `chabun.js` (grey with a light silhouette). 64 by default (60 in the
 * unseen-contacts stack, 72 next to the caller's name). Read by TalkBack as [contentDescription].
 */
@Composable
fun Avatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = Design.avatar,
    contentDescription: String? = name,
) {
    val look = Avatars.lookFor(name)
    Canvas(
        modifier
            .size(size)
            .clip(SyncDeskShapes.circle)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics {
                        this.contentDescription = contentDescription
                        role = Role.Image
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        scale(this.size.width / GRID, this.size.height / GRID, pivot = Offset.Zero) {
            if (look == null) drawDefaultPerson() else drawPerson(look)
        }
    }
}

/**
 * The Gleo AI logo (`res/drawable-nodpi/gleo.webp`, the official artwork as supplied) with its
 * corners rounded to a quarter of its size and the spec's blue shadow. [contentDescription] is
 * null by default because the name "Gleo" usually stands next to it.
 */
@Composable
fun GleoIcon(size: Dp, modifier: Modifier = Modifier, contentDescription: String? = null) {
    Image(
        painter = painterResource(R.drawable.gleo),
        contentDescription = contentDescription,
        modifier = modifier
            .size(size)
            .dropShadow(SyncDeskShapes.gleo, Materials.gleoShadow)
            .clip(SyncDeskShapes.gleo),
    )
}

private const val GRID = 64f

private fun DrawScope.drawDefaultPerson() {
    val light = Color(0xFFE9ECF3)
    drawRect(Color(0xFF8D97AD), size = Size(GRID, GRID))
    drawCircle(light, radius = 11f, center = Offset(32f, 25f))
    drawPath(DefaultBody, light)
}

private fun DrawScope.drawPerson(look: AvatarLook) {
    drawRect(look.background, size = Size(GRID, GRID))
    if (look.hairStyle == HairStyle.Bob) drawPath(BobBack, look.hair)
    drawPath(Body, look.cloth)
    if (look.collar) drawPath(Collar, Color.White)
    drawRoundRect(
        color = look.skin.dimmed(0.93f),
        topLeft = Offset(27.5f, 36f),
        size = Size(9f, 10f),
        cornerRadius = CornerRadius(4f),
    )
    drawOval(look.skin, topLeft = Offset(32f - 11f, 27.5f - 12.5f), size = Size(22f, 25f))
    when (look.hairStyle) {
        HairStyle.Short -> drawPath(HairShort, look.hair)
        HairStyle.Part -> drawPath(HairPart, look.hair)
        HairStyle.Bob -> drawPath(HairBob, look.hair)
        HairStyle.Perm -> PermCurls.forEach { (x, y, r) -> drawCircle(look.hair, radius = r, center = Offset(x, y)) }
    }
    if (look.glasses) {
        val frame = Color(0xFF26262B)
        val stroke = Stroke(width = 1.3f)
        drawRoundRect(frame, Offset(23.5f, 26f), Size(7f, 5.2f), CornerRadius(2.2f), style = stroke)
        drawRoundRect(frame, Offset(33.5f, 26f), Size(7f, 5.2f), CornerRadius(2.2f), style = stroke)
        drawLine(frame, Offset(30.5f, 28f), Offset(33.5f, 28f), strokeWidth = 1.3f)
    }
}

/** CSS `filter: brightness(.93)`. */
private fun Color.dimmed(factor: Float) = Color(red * factor, green * factor, blue * factor, alpha)

private fun path(data: String): Path = PathParser().parsePathString(data).toPath()

private val DefaultBody = path("M10 66C10 49 20 42 32 42s22 7 22 24Z")
private val Body = path("M8 68C8 50 19 44 32 44s24 6 24 24Z")
private val Collar = path("M26.5 44L32 51l5.5-7Z")
private val BobBack = path("M17 42C13.5 20 21 9 32 9s18.5 11 15 33Z")
private val HairShort = path("M20 28C17.5 13 25 9.5 32 9.5S46.5 13 44 28C42.5 22 39.5 19 32 19s-10.5 3-12 9Z")
private val HairPart = path("M20 28.5C17 13 26 9.5 33 9.5c8.5 0 13.5 5 11 19C43 22.5 40.5 20 36.5 19.5 30 20 23.5 20 20 28.5Z")
private val HairBob = path(
    "M17.5 40C14.5 20 21.5 9.5 32 9.5S49.5 20 46.5 40L41.5 40C43.5 31 43.5 24.5 40.5 20.5 35.5 23 28 22.5 23.5 20.5 20.5 24.5 20.5 31 22.5 40Z",
)
private val PermCurls = listOf(
    Triple(23f, 18f, 6f), Triple(30f, 13.5f, 6.5f), Triple(38f, 14.5f, 6.5f),
    Triple(43f, 21f, 5.5f), Triple(20.5f, 25f, 5f), Triple(44.5f, 27f, 4.5f),
)
