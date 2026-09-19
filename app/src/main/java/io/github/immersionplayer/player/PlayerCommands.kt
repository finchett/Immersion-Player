package io.github.immersionplayer.player

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class PlayerCommand { PreviousLine, NextLine }

/** Commands from outside the player UI (e.g. hardware keys handled by the activity). */
object PlayerCommands {
    private val _events = MutableSharedFlow<PlayerCommand>(extraBufferCapacity = 8)
    val events: SharedFlow<PlayerCommand> = _events.asSharedFlow()

    fun send(command: PlayerCommand) {
        _events.tryEmit(command)
    }
}
