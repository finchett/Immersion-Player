package app.mizune.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The shader button's icon: an anime face, from desktop/icons/anime-face.svg. It's drawn as a
 * stencil in [color]: the SVG's dark shapes are filled, and its white one (a mark by the right
 * eye) is cut out, so it still shows whatever the tint.
 */
@Composable
fun ShaderIcon(color: Color, height: Dp = 20.dp) {
    Canvas(
        Modifier.size(height * VIEW_WIDTH / VIEW_HEIGHT, height)
            // the cut-out clears this layer only, not what's behind the icon
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen),
    ) {
        val scale = size.height / VIEW_HEIGHT
        withTransform({ scale(scale, scale, pivot = Offset.Zero) }) {
            for (path in INK) drawPath(path, color)
            drawPath(MARK, Color.Black, blendMode = BlendMode.Clear)
        }
    }
}

private const val VIEW_WIDTH = 970f
private const val VIEW_HEIGHT = 1000f

private fun path(data: String): Path = PathParser().parsePathString(data).toPath()

/** The SVG's dark shapes, as it has them: everything but the left eye in one path, that eye in another. */
private val INK = listOf(
    path(
        "M70 451C83 348 94 271 133 214C193 124 312 45 413 29C510 13 602 28 680 77C744 103 790 143 822 192" +
            "C865 254 883 345 896 439C907 540 918 635 898 744C881 837 835 907 747 949C757 922 762 894 760 864" +
            "C683 907 585 923 483 949C381 923 284 906 211 864C208 896 214 926 225 949C137 906 91 836 75 744" +
            "C56 635 65 540 70 451Z" +
            "M718 133L729 124L791 187L780 198Z" +
            "M696 156L707 147L763 204L752 215Z" +
            "M184 554C188 664 220 769 265 817C300 855 410 908 483 930C562 905 663 857 696 821C750 759 778 660 784 550" +
            "L731 550C655 549 598 525 545 477C547 502 566 548 583 565C588 564 594 560 599 558L652 558" +
            "C695 558 718 569 773 600C744 619 736 647 721 672C734 623 728 593 692 585C681 580 695 597 695 613" +
            "L695 647C678 688 659 704 634 704C592 705 582 659 598 597C601 584 607 574 600 578L578 585L583 594" +
            "C531 574 473 541 448 514C422 487 407 449 391 420C388 452 404 501 435 541C369 542 309 501 295 470" +
            "C284 445 294 410 305 377C278 417 253 457 241 493C235 515 206 540 184 554Z",
    ),
    path(
        "M195 601C252 568 272 556 319 557C359 557 374 558 401 574L377 572C386 578 396 585 403 594" +
            "C388 587 374 581 360 576C374 611 379 650 369 677C359 703 335 708 314 700C278 685 259 633 276 589" +
            "C279 581 273 586 268 586C235 594 235 634 248 674C226 643 225 622 195 601Z",
    ),
)

/** The SVG's one white path: a small mark by the right eye. */
private val MARK = path("M571 576L584 573C591 572 586 577 574 582C571 584 569 578 571 576Z")
