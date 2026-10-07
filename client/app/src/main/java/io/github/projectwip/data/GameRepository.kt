package io.github.projectwip.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.util.concurrent.Executors

/**
 * Single source of truth for the player's progress. The UI observes [save];
 * every mutation goes through [Economy] / [Progression] and is persisted immediately on a background thread.
 */
class GameRepository(private val store: SaveStore) {
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "save-io").apply { isDaemon = true } }
    private val _save = MutableStateFlow(store.load())
    val save: StateFlow<SaveData> = _save.asStateFlow()

    /** Today's number: days since 1970 on this device's calendar. The daily gift, the day's drops and offers all count by it. */
    val today: Long get() = LocalDate.now().toEpochDay()

    private fun commit(next: SaveData) {
        _save.value = next
        io.execute { store.write(next) }
    }

    /** Runs [change] on the current save and keeps the result. A [Refused] change leaves the save as it was. */
    @Synchronized
    fun <T> transact(change: (SaveData) -> Done<T>): T {
        val done = change(_save.value)
        if (done.save != _save.value) commit(done.save)
        return done.value
    }

    /** Settles a finished match (what it paid, its Cups and any Glitch Drop) and returns what the result screen shows. */
    @Synchronized
    fun applyMatch(report: MatchReport): MatchRewards {
        val settled = Economy.settleMatch(_save.value, report, today)
        val (next, rewards) = Progression.applyMatch(settled.save, report, today, settled.value)
        commit(next)
        return rewards
    }

    val capsulesLeftToday: Int get() = Progression.capsulesLeftToday(_save.value, today)

    fun selectFighter(id: FighterId) = commit(Progression.selectFighter(_save.value, id))

    fun selectMode(mode: GameMode) = commit(_save.value.copy(selectedMode = mode))

    /** Boss Mode: which boss to fight (null = a random one each time). */
    fun selectBoss(boss: BossKind?) = commit(_save.value.copy(selectedBoss = boss))

    fun selectSkin(id: FighterId, skin: Int) = commit(Progression.selectSkin(_save.value, id, skin))

    fun updateSettings(transform: (Settings) -> Settings) = commit(_save.value.copy(settings = transform(_save.value.settings)))

    /** Wipes progress (Cups, levels, currencies, claims), keeping settings. */
    fun resetProgress() = commit(SaveData(settings = _save.value.settings, capsuleSeed = System.nanoTime()))
}
