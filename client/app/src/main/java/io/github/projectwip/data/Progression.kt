package io.github.projectwip.data

/** What the player brought out of a match. Built by the match, applied by [Progression.applyMatch]. */
data class MatchReport(
    val outcome: MatchOutcome,
    val mode: GameMode = GameMode.KNOCKOUT_RUSH,
    /** 1-based finishing place in free-for-all modes; 0 for team modes. */
    val placement: Int = 0,
    val players: Int = 6,
    val fighter: FighterId,
    val kos: Int,
    val deaths: Int,
    val damageDealt: Int,
    val mvp: Boolean,
    val difficulty: BotDifficulty,
    val blueScore: Int,
    val redScore: Int,
)

/** What the result screen shows. Before/after values let it animate the change. */
data class MatchRewards(
    val cupsBefore: Int,
    val cupDelta: Int,
    val bolts: Int,
    val firstWinPrisms: Int,
    val newlyReachedMilestones: List<Milestone>,
    val capsuleEarned: Boolean = false,
    /** Spark Capsules that can still be earned today, after this match. */
    val capsulesLeftToday: Int = 0,
    /** Credits for the Spark Road (Glory once it is finished), and points for the Spark Pass. */
    val credits: Int = 0,
    val passPoints: Int = 0,
    val glory: Int = 0,
    /** The Cups of the fighter that was played, before the match and what it changed them by: its rank follows them. */
    val fighterCupsBefore: Int = 0,
    val fighterCupDelta: Int = 0,
    /** How many of the Cups were the MVP's bonus (0 if there was none). */
    val mvpCups: Int = 0,
)

/** What a match was worth (see [Economy.settleMatch]). Cups and Spark Drops are totals to adopt, not amounts to add up. */
data class MatchVerdict(
    val cupDelta: Int,
    /** The player's Cups after this match. */
    val cups: Int,
    /** This match earned a Spark Drop. */
    val drop: Boolean,
    /** Unopened Spark Drops after this match. */
    val drops: Int,
    val dropsLeftToday: Int,
    /** Bolts this match paid. */
    val bolts: Int = 0,
    /** Prisms for the first win of the day (0 if this wasn't it). */
    val firstWinPrisms: Int = 0,
    val credits: Int = 0,
    val passPoints: Int = 0,
    val glory: Int = 0,
    /** The Cups of the fighter that was played, before and after this match. */
    val fighterCupsBefore: Int = 0,
    val fighterCups: Int = 0,
    /** How many of the Cups were the MVP's bonus. */
    val mvpCups: Int = 0,
)

/** One stat row on the upgrade screen. */
data class StatPreview(val label: String, val current: Int, val next: Int?, val suffix: String = "") {
    val delta: Int? get() = next?.let { it - current }
}

/** Progression rules the game needs for showing things. Everything that earns, spends or grants is in [Economy]. */
object Progression {

    /**
     * Records a finished match. Cups and Spark Drops come from the [verdict] ([Economy.settleMatch] has already put
     * the Bolts, Crystals and Credits it paid into [save]).
     */
    fun applyMatch(save: SaveData, report: MatchReport, today: Long, verdict: MatchVerdict): Pair<SaveData, MatchRewards> {
        val newCups = (verdict.cups).coerceAtLeast(0)
        val cupDelta = verdict.cupDelta
        val reached = CupTrack.milestones.filter { it.cups in (save.bestCups + 1)..newCups }
        val leftToday = verdict.dropsLeftToday
        val next = save.copy(
            capsules = verdict.drops,
            capsuleDay = today,
            capsulesEarnedToday = SparkCapsules.PER_DAY - leftToday,
            cups = newCups,
            bestCups = maxOf(save.bestCups, newCups),
            matchesPlayed = save.matchesPlayed + 1,
            victories = save.victories + if (report.outcome == MatchOutcome.VICTORY) 1 else 0,
            totalKos = save.totalKos + report.kos,
        )
        val rewards = MatchRewards(newCups - cupDelta, cupDelta, verdict.bolts, verdict.firstWinPrisms, reached, verdict.drop, leftToday, credits = verdict.credits, passPoints = verdict.passPoints, glory = verdict.glory,
            fighterCupsBefore = verdict.fighterCupsBefore, fighterCupDelta = verdict.fighterCups - verdict.fighterCupsBefore, mvpCups = verdict.mvpCups)
        return next to rewards
    }

    // ---------------- Spark Capsules ----------------

    fun capsulesLeftToday(save: SaveData, today: Long): Int =
        SparkCapsules.PER_DAY - if (save.capsuleDay == today) save.capsulesEarnedToday else 0

    // ---------------- Upgrades ----------------

    /** At [Balance.MAX_LEVEL] (or beyond) with the cap in force. */
    fun levelCapped(save: SaveData, id: FighterId): Boolean =
        save.progress(id).level >= Balance.MAX_LEVEL && !save.settings.debugNoLevelCap

    /** The debug menu's upgrade-cost slider goes from free up to this many times the normal price. */
    const val MAX_COST_FACTOR = 3f

    /** What the next upgrade of [id] costs right now (the balance table, times the debug cost factor). */
    fun upgradeCost(save: SaveData, id: FighterId): Int = Economy.upgradeCost(save.progress(id).level, save.settings.debugUpgradeCost)

    fun canUpgrade(save: SaveData, id: FighterId): Boolean {
        val p = save.progress(id)
        val cost = upgradeCost(save, id)
        return p.unlocked && !levelCapped(save, id) && save.bolts >= cost
    }

    fun statPreview(def: FighterDef, level: Int, capped: Boolean = false): List<StatPreview> {
        val next: Int? = if (capped) null else level + 1
        fun line(label: String, s: StatLine, suffix: String = "") = StatPreview(label, s.at(level), next?.let { s.at(it) }, suffix)
        val shots = def.attack.projectiles
        return listOf(
            line("Health", def.health),
            line(if (shots > 1) "${def.attackName} (×$shots)" else def.attackName, def.attackDamage),
            line(def.superSpec.name, def.superDamage, if (def.superSpec.projectiles > 1) " ×${def.superSpec.projectiles}" else ""),
        )
    }

    // ---------------- Cup Track ----------------

    fun claimable(save: SaveData): List<Milestone> =
        CupTrack.milestones.filter { it.cups <= save.bestCups && it.cups !in save.claimedMilestones }

    fun owns(save: SaveData, reward: Reward): Boolean = when (reward) {
        is Reward.Bundle -> false
        is Reward.UnlockFighter -> save.progress(reward.fighter).unlocked
        is Reward.SkinReward -> reward.skinIndex in save.progress(reward.fighter).ownedSkins
        else -> false
    }

    fun dailyGiftAvailable(save: SaveData, today: Long) = save.lastDailyGiftDay != today

    fun selectFighter(save: SaveData, id: FighterId): SaveData =
        if (save.progress(id).unlocked) save.copy(selectedFighter = id) else save

    fun selectSkin(save: SaveData, id: FighterId, skin: Int): SaveData {
        val p = save.progress(id)
        return if (skin in p.ownedSkins) save.copy(fighters = save.fighters + (id to p.copy(skin = skin))) else save
    }
}
