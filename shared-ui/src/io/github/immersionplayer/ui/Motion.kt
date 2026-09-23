package io.github.immersionplayer.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.unit.IntOffset

/**
 * How screens give way to each other, the same on the phone and on the desktop. Every transition
 * passes `sizeTransform = null`: the default one springs, and a screen whose width changes (the
 * settings sidebar appearing) then bounces as it animates.
 */
object Motion {
    val fade = tween<Float>(220, easing = FastOutSlowInEasing)
    val slide = tween<IntOffset>(260, easing = FastOutSlowInEasing)

    /** Opening a video: it settles in over what you opened it from. */
    fun intoPlayer() = ContentTransform(
        targetContentEnter = fadeIn(fade) + scaleIn(fade, initialScale = 0.97f),
        initialContentExit = fadeOut(fade),
        sizeTransform = null,
    )

    /** Leaving a video: it pulls back out of the way. */
    fun outOfPlayer() = ContentTransform(
        targetContentEnter = fadeIn(fade),
        initialContentExit = fadeOut(fade) + scaleOut(fade, targetScale = 0.97f),
        sizeTransform = null,
    )

    /** Library and settings are laid out differently, so one dissolves into the other. */
    fun crossfade() = ContentTransform(fadeIn(fade), fadeOut(fade), sizeTransform = null)

    /** Moving through the library: a folder comes in from the side you are heading towards. */
    fun intoFolder(deeper: Boolean) = ContentTransform(
        targetContentEnter = slideInHorizontally(slide) { if (deeper) it / 6 else -it / 6 } + fadeIn(fade),
        initialContentExit = slideOutHorizontally(slide) { if (deeper) -it / 6 else it / 6 } + fadeOut(fade),
        sizeTransform = null,
    )
}
