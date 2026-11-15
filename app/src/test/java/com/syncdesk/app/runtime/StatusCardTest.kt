package com.syncdesk.app.runtime

import android.app.Application
import android.content.Context
import android.graphics.Rect
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.syncdesk.app.MainActivity
import com.syncdesk.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Draws the status card the way a widget host does: from its RemoteViews alone.
 *
 * `gradlew :app:recordRoborazziDebug` writes the images to `app/build/outputs/roborazzi`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "ko-rKR")
class StatusCardTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val capturing = StatusText("못 들은 부분을 표시하는 중", "통화가 끝나면 Gleo가 짚어 드립니다")

    private fun card(status: StatusText): View =
        StatusCardProvider.views(context, status).apply(context, FrameLayout(context))

    @Test
    fun `the card shows both lines`() {
        val card = card(capturing)
        assertEquals(capturing.headline, card.findViewById<TextView>(R.id.status_headline).text.toString())
        val detail = card.findViewById<TextView>(R.id.status_detail)
        assertEquals(capturing.detail, detail.text.toString())
        assertEquals(View.VISIBLE, detail.visibility)
    }

    @Test
    fun `a status without detail leaves the second line out`() {
        val card = card(StatusText("통화를 기다리는 중", ""))
        assertEquals(View.GONE, card.findViewById<View>(R.id.status_detail).visibility)
        assertEquals("통화를 기다리는 중", card.contentDescription.toString())
    }

    @Test
    fun `the card exposes both lines as one accessible label`() {
        val card = card(capturing)
        assertEquals("${capturing.headline}\n${capturing.detail}", card.contentDescription.toString())
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_YES, card.importantForAccessibility)
        assertTrue(card.isScreenReaderFocusable)
    }

    @Test
    fun `touching the card opens the app`() {
        assertTrue(card(capturing).performClick())
        val application = ApplicationProvider.getApplicationContext<Application>()
        assertEquals(MainActivity::class.java.name, shadowOf(application).nextStartedActivity?.component?.className)
    }

    @Test
    fun `both lines remain inside a compact card and overflow is ellipsized`() {
        RuntimeEnvironment.setQualifiers("ko-rKR-w841dp-h1210dp-port-notnight-mdpi")
        val card = card(capturing)
        card.measure(
            View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY),
        )
        card.layout(0, 0, 320, 100)
        val cardBounds = Rect().also(card::getDrawingRect)
        listOf(R.id.status_headline, R.id.status_detail).forEach { id ->
            val line = card.findViewById<TextView>(id)
            assertEquals(1, line.maxLines)
            assertEquals(TextUtils.TruncateAt.END, line.ellipsize)
            assertEquals(1, line.layout.lineCount)
            assertTrue(line.layout.getEllipsisCount(0) > 0)
            val bounds = Rect().also(line::getDrawingRect)
            (card as ViewGroup).offsetDescendantRectToMyCoords(line, bounds)
            assertTrue("The text line must fit inside the 100 dp card", cardBounds.contains(bounds))
        }
    }

    @Test
    fun `the card by day`() = capture(night = false)

    @Test
    fun `the card by night`() = capture(night = true)

    /** In a box the size of a card under the Pleos Connect driving view (2560 x 1440, 160 dpi). */
    private fun capture(night: Boolean) {
        RuntimeEnvironment.setQualifiers("ko-rKR-w841dp-h1210dp-port-${if (night) "night" else "notnight"}-mdpi")
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val host = FrameLayout(activity).apply { setBackgroundColor(activity.getColor(R.color.ground)) }
                host.addView(StatusCardProvider.views(activity, capturing).apply(activity, host))
                activity.setContentView(host, FrameLayout.LayoutParams(CARD_WIDTH, CARD_HEIGHT))
                host.captureRoboImage("build/outputs/roborazzi/status_card_${if (night) "night" else "day"}.png")
            }
        }
    }

    private companion object {
        const val CARD_WIDTH = 760
        const val CARD_HEIGHT = 170
    }
}
