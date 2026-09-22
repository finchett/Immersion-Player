package io.github.immersionplayer.desktop

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Test

class CornersTest {
    @Test
    fun geometryIsNeverNegative() {
        // full screen passes 0: no window corner to be concentric with
        for (radius in listOf(0.0, 5.0, 10.0, 16.0, 26.0)) {
            val c = Corners(radius)
            assertTrue("gap at $radius", c.cardGap >= 0.dp)
            assertTrue("radius at $radius", c.cardRadius >= 0.dp)
        }
    }
}
