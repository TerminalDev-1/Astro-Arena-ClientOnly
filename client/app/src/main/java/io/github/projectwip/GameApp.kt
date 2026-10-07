package io.github.projectwip

import android.app.Application
import io.github.projectwip.data.GameRepository
import io.github.projectwip.data.LocalGame
import io.github.projectwip.data.SaveStore

/** Holds the single [GameRepository] and [LocalGame] so they survive activity recreation. */
class GameApp : Application() {
    val repository: GameRepository by lazy { GameRepository(SaveStore(this)) }
    val game: LocalGame by lazy { LocalGame(repository) }
}
