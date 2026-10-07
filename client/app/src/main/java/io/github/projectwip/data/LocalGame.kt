package io.github.projectwip.data

import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/** The shop, the Spark Pass and the day's clock as the menus show them right now. */
data class Account(
    val cups: Int,
    /** Unopened Spark Drops. */
    val drops: Int,
    val dropsLeftToday: Int,
    /** The deals made with the Offer Creator that are still running. */
    val deals: List<CustomOffer>,
    /** Today's offers; their [CustomOffer.id] is their place in the list. */
    val dailyOffers: List<CustomOffer>,
    /** Days since 1970. */
    val day: Long,
    /** When today ends and the shop changes (ms since 1970). */
    val dayEndsAt: Long,
    val giftAvailable: Boolean,
    val pass: PassState,
)

/**
 * Everything the menus ask the game to do: buy, upgrade, claim, open. Each one is a rule from [Economy] applied to
 * the player's save. A request the rules refuse returns null, with the reason in [lastError].
 */
class LocalGame(private val repo: GameRepository) {
    /** Why the last request was refused, in words for the player ("" if it wasn't). */
    @Volatile var lastError = ""
        private set

    private fun <T : Any> act(change: (SaveData) -> Done<T>): T? {
        lastError = ""
        return try {
            repo.transact(change)
        } catch (refused: Refused) {
            lastError = refused.message.orEmpty()
            null
        }
    }

    /** Buys one of today's offers ([index] in [Account.dailyOffers]). */
    fun buyDaily(index: Int): Reward? = act { Economy.buyDaily(it, index, repo.today) }

    /** Levels a fighter up. Returns what it cost. [costFactor] and [noCap] are the debug menu's. */
    fun upgrade(fighter: FighterId, costFactor: Float = 1f, noCap: Boolean = false): Int? = act { Economy.upgrade(it, fighter, costFactor, noCap) }

    /** Buys a standing shop item (see [ShopItem.key]) and returns what was received. */
    fun buy(itemKey: String): Reward? = act { Economy.buy(it, itemKey) }

    fun claimGift(): Reward? = act { Economy.claimGift(it, repo.today) }

    /** Claims the Cup Track reward at [cups]. What comes back is what was actually given (owned things are paid out instead). */
    fun claimMilestone(cups: Int): Reward? = act { Economy.claimMilestone(it, cups) }

    /** Claims the Spark Road fighter the Credits have covered. */
    fun roadUnlock(): Reward? = act { Economy.roadUnlock(it) }

    /** Claims the reward at Spark Pass tier [tier] (1-based). */
    fun claimPass(tier: Int): Reward? = act { Economy.claimPass(it, tier, repo.today) }

    fun buyDeal(id: Long): Reward? = act { Economy.buyDeal(it, id, System.currentTimeMillis()) }

    /** Puts a deal in the shop (the Offer Creator). */
    fun createDeal(offer: CustomOffer): Long? = act { Economy.createDeal(it, offer) }

    /** Takes a deal out of the shop. */
    fun deleteDeal(id: Long): Boolean? = act { Economy.deleteDeal(it, id) }

    /** Starts the player's progress over. */
    fun reset(): Boolean {
        repo.resetProgress()
        return true
    }

    /** The debug menu's hand-outs. */
    fun devGrant(cups: Int = 0, drops: Int = 0, bolts: Int = 0, prisms: Int = 0, credits: Int = 0): Boolean {
        repo.transact { Done(Economy.devGrant(it, cups, drops, bolts, prisms, credits), Unit) }
        return true
    }

    /** Opens one Spark Drop. [luck] and [free] are the debug menu's. Null if there is none to open. */
    fun openDrop(luck: Float = 0f, free: Boolean = false): CapsuleResult? =
        act { Economy.openDrops(it, luck, free, 1, Random.Default) }?.firstOrNull()

    /** Opens every Spark Drop held; pieces that split off on the way are left to open next. Null if there were none. */
    fun openAllDrops(luck: Float = 0f): List<CapsuleResult>? =
        act { Economy.openDrops(it, luck, false, null, Random.Default) }?.takeIf { it.isNotEmpty() }

    /** The menus' view of the shop, the pass and the clock for [save]. */
    fun account(save: SaveData): Account {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val day = today.toEpochDay()
        val dayEndsAt = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val seasonEndsAt = LocalDate.ofEpochDay(SparkPass.endDay(day)).atStartOfDay(zone).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()
        val pass = Economy.passView(save, day)
        return Account(
            cups = save.cups, drops = save.capsules, dropsLeftToday = Progression.capsulesLeftToday(save, day),
            deals = save.customOffers.filter { !it.expired(now) },
            dailyOffers = Economy.dailyOffers(day, dayEndsAt).map { it.copy(purchased = if (Economy.boughtToday(save, it.title, day)) 1 else 0) },
            day = day, dayEndsAt = dayEndsAt, giftAvailable = save.lastDailyGiftDay != day,
            pass = PassState(
                season = pass.season, endsAt = seasonEndsAt, points = pass.points, tierPoints = SparkPass.TIER_POINTS, claimed = pass.claimed,
                tiers = (1..SparkPass.TIERS).map { SparkPass.reward(it) },
            ),
        )
    }
}
