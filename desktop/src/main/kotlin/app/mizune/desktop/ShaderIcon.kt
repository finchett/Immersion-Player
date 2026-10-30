package app.mizune.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The shader button's icon: Mizune's face from the app icon (desktop/icons/icon.svg), drawn in
 * [color]. The face itself is a hole in the hair, so it shows whatever is behind the button.
 */
@Composable
fun ShaderIcon(color: Color, height: Dp = 20.dp) {
    Canvas(Modifier.size(height * VIEW_WIDTH / VIEW_HEIGHT, height)) {
        val scale = size.height / VIEW_HEIGHT
        withTransform({ scale(scale, scale, pivot = Offset.Zero) }) {
            for (path in INK) drawPath(path, color)
        }
    }
}

private const val VIEW_WIDTH = 970f
private const val VIEW_HEIGHT = 1000f

private fun path(data: String): Path = PathParser().parsePathString(data).toPath()

/** The icon's dark shapes, as icon.svg has them: the hair around the face, then the eyes. */
private val INK = listOf(
    path(
        "M70 451C83 348 94 271 133 214C193 124 312 45 413 29C510 13 602 28 680 77C744 103 790 143 822 192" +
            "C865 254 883 345 896 439C907 540 918 635 898 744C881 837 835 907 747 949C757 922 762 894 760 864" +
            "C683 907 585 923 483 949C381 923 284 906 211 864C208 896 214 926 225 949C137 906 91 836 75 744" +
            "C56 635 65 540 70 451Z" +
            "M184 554C188 664 220 769 265 817C300 855 410 908 483 930C562 905 663 857 696 821" +
            "C750 759 778 660 784 550L731 550C655 549 598 525 545 477C548 499 558 518 570 533" +
            "C521 522 474 499 448 475C422 450 407 423 391 394C388 426 404 475 435 515C369 516 309 475 295 444" +
            "C284 419 294 384 305 351C278 391 253 431 241 467C235 494 206 529 184 554Z",
    ),
    path(
        "M195 601C252 568 272 556 319 557C351 557 379 568 403 594C388 587 374 581 360 576" +
            "C374 611 379 650 369 677C359 703 335 708 314 700C278 685 259 633 276 589C279 581 273 586 268 586" +
            "C235 594 235 634 248 674C226 643 225 622 195 601Z" +
            "M583 579C604 565 625 558 652 558C695 558 718 569 773 600C744 619 736 647 721 672" +
            "C734 623 728 593 692 585C681 580 695 597 695 613L695 647C678 688 659 704 634 704" +
            "C592 705 582 659 598 597C601 584 607 574 600 578Z",
    ),
)
