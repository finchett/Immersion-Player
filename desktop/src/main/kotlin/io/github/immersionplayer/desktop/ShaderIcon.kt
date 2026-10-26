package io.github.immersionplayer.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The shader button's icon: an anime face, from desktop/icons/anime-face.svg. It's drawn as a
 * stencil in [color]: the SVG's dark shapes are filled, and its white ones (the glints in the
 * eyes, a mark by the right one) are cut out, so they still show whatever the tint.
 */
@Composable
fun ShaderIcon(color: Color, height: Dp = 20.dp) {
    Canvas(
        Modifier.size(height * VIEW_WIDTH / VIEW_HEIGHT, height)
            // the cut-outs clear this layer only, not what's behind the icon
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen),
    ) {
        val scale = size.height / VIEW_HEIGHT
        withTransform({ scale(scale, scale, pivot = Offset.Zero) }) {
            for (path in INK) drawPath(path, color)
            cutOuts()
        }
    }
}

private fun DrawScope.cutOuts() {
    val clear = Color.Black
    drawPath(MARK, clear, blendMode = BlendMode.Clear)
    for ((x, y) in listOf(346f to 615f, 607f to 614f)) {
        rotate(-20f, pivot = Offset(x, y)) {
            drawOval(clear, Offset(x - 10f, y - 13f), Size(20f, 26f), blendMode = BlendMode.Clear)
        }
    }
    drawCircle(clear, 4f, Offset(329f, 644f), blendMode = BlendMode.Clear)
    drawCircle(clear, 4f, Offset(625f, 642f), blendMode = BlendMode.Clear)
}

private const val VIEW_WIDTH = 970f
private const val VIEW_HEIGHT = 1000f

private fun path(data: String): Path = PathParser().parsePathString(data).toPath()

/** The SVG's dark shapes, as it has them: everything but the left eye in one path, that eye in another. */
private val INK = listOf(
    path(
        "M28 773C32 651 51 548 70 451C83 348 94 271 133 214C193 124 312 45 413 29C510 13 602 28 680 77C744 103 790 143 822 192" +
            "C865 254 883 345 896 439C910 549 936 647 942 778L891 666C902 737 900 782 881 831C860 892 815 945 763 980" +
            "C809 912 818 846 805 749C795 867 764 944 713 976C719 941 728 900 728 862C694 890 661 902 620 904" +
            "C614 904 629 890 638 880C614 892 595 902 575 909C571 911 571 914 571 927C571 940 583 941 665 978" +
            "C619 968 550 946 539 936C536 933 536 930 537 926L483 944L435 929C431 928 432 933 423 937" +
            "C385 953 347 966 305 979C310 972 375 945 389 936C397 931 397 925 396 912C372 902 352 894 334 884" +
            "C334 891 352 905 348 905C304 903 270 891 239 861C241 909 254 954 279 978C207 947 177 857 166 761" +
            "C164 729 162 742 160 767C150 855 165 922 206 980C115 929 52 813 79 662Z" +
            "M671 213C755 267 807 345 821 431Q824 441 830 431C822 334 767 256 682 205Q666 197 671 213Z" +
            "M718 133L729 124L791 187L780 198Z" +
            "M696 156L707 147L763 204L752 215Z" +
            "M106 415C120 407 125 414 137 422C141 426 141 409 144 396C169 288 250 217 369 167C364 160 360 153 355 155" +
            "C217 203 147 253 106 415Z" +
            "M184 554C188 664 220 769 265 817C300 855 410 908 483 930C562 905 663 857 696 821C750 759 778 660 784 550" +
            "L731 550C655 549 598 525 545 477C547 502 566 548 583 565C588 564 594 560 599 558L652 558" +
            "C695 558 718 569 773 600C744 619 736 647 721 672C734 623 728 593 692 585C681 580 695 597 695 613" +
            "L695 647C678 688 659 704 634 704C592 705 582 659 598 597C601 584 607 574 600 578L578 585L583 594" +
            "C531 574 473 541 448 514C422 487 407 449 391 420C388 452 404 501 435 541C369 542 309 501 295 470" +
            "C284 445 294 410 305 377C278 417 253 457 241 493C235 515 206 540 184 554Z" +
            "M142 571C137 572 126 592 126 600C126 632 154 672 175 694C159 650 149 610 142 571Z" +
            "M825 574C819 614 807 655 796 689C816 662 835 631 838 608C840 593 831 577 825 574Z",
    ),
    path(
        "M195 601C252 568 272 556 319 557C359 557 374 558 401 574L377 572C386 578 396 585 403 594" +
            "C388 587 374 581 360 576C374 611 379 650 369 677C359 703 335 708 314 700C278 685 259 633 276 589" +
            "C279 581 273 586 268 586C235 594 235 634 248 674C226 643 225 622 195 601Z",
    ),
)

/** The SVG's one white path: a small mark by the right eye. */
private val MARK = path("M571 576L584 573C591 572 586 577 574 582C571 584 569 578 571 576Z")
