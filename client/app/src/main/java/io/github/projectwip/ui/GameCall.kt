package io.github.projectwip.ui

import androidx.compose.runtime.staticCompositionLocalOf
import io.github.projectwip.audio.Sfx
import io.github.projectwip.audio.Sound
import io.github.projectwip.data.LocalGame

/**
 * How the menus ask the game to do something (buy, upgrade, claim...). [then] runs with the answer if the rules
 * agreed. If they didn't, the player hears the "no" sound and is told why.
 *
 *     ask({ buy(item.key) }) { reward -> showReward(reward) }
 */
class GameCall(
    private val game: LocalGame,
    private val sfx: Sfx?,
    private val say: (String) -> Unit,
) {
    operator fun <T : Any> invoke(request: LocalGame.() -> T?, then: (T) -> Unit = {}) {
        val answer = game.request()
        if (answer != null) {
            then(answer)
        } else {
            sfx?.play(Sound.DENIED)
            game.lastError.takeIf { it.isNotBlank() }?.let { say("Can't do that: $it.") }
        }
    }
}

val LocalGameCall = staticCompositionLocalOf<GameCall> { error("No GameCall provided") }
