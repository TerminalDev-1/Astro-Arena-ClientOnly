package io.github.projectwip

import io.github.projectwip.data.Balance
import io.github.projectwip.data.BotDifficulty
import io.github.projectwip.data.CapsuleTier
import io.github.projectwip.data.CupTrack
import io.github.projectwip.data.Currency
import io.github.projectwip.data.CustomOffer
import io.github.projectwip.data.Economy
import io.github.projectwip.data.FighterId
import io.github.projectwip.data.FighterProgress
import io.github.projectwip.data.GameMode
import io.github.projectwip.data.MatchOutcome
import io.github.projectwip.data.MatchReport
import io.github.projectwip.data.Progression
import io.github.projectwip.data.Refused
import io.github.projectwip.data.Reward
import io.github.projectwip.data.SaveData
import io.github.projectwip.data.Shop
import io.github.projectwip.data.SparkCapsules
import io.github.projectwip.data.SparkPass
import io.github.projectwip.data.SparkRoad
import io.github.projectwip.data.Trophies
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.random.Random

/** The rules that used to be the game server's, now run on the device. */
class EconomyTest {

    private fun refused(status: Int, block: () -> Unit) {
        try {
            block()
            fail("expected the request to be refused with $status")
        } catch (e: Refused) {
            assertEquals(status, e.status)
        }
    }

    private fun unlocked(vararg ids: FighterId, level: Int = 1) =
        SaveData.defaultFighters() + ids.associateWith { FighterProgress(unlocked = true, level = level) }

    private fun report(mode: GameMode, outcome: MatchOutcome, placement: Int = 0, kos: Int = 2, mvp: Boolean = false, d: BotDifficulty = BotDifficulty.NORMAL, fighter: FighterId = FighterId.BYTE) =
        MatchReport(outcome = outcome, mode = mode, placement = placement, fighter = fighter, kos = kos, deaths = 1, damageDealt = 1000, mvp = mvp,
            difficulty = d, blueScore = 10, redScore = 5)

    // ---------------------------------------------------------------- upgrades

    @Test fun upgradingCostsBoltsAndStopsAtTheCap() {
        val s = SaveData(bolts = 100)
        val done = Economy.upgrade(s, FighterId.BYTE)
        assertEquals(Balance.upgradeCost[0], done.value)
        assertEquals(100 - Balance.upgradeCost[0], done.save.bolts)
        assertEquals(2, done.save.progress(FighterId.BYTE).level)
        refused(402) { Economy.upgrade(SaveData(bolts = 0), FighterId.BYTE) }
        refused(409) { Economy.upgrade(s, FighterId.KITO) }
        val top = SaveData(bolts = 10_000, fighters = unlocked(FighterId.BYTE, level = Balance.MAX_LEVEL))
        refused(409) { Economy.upgrade(top, FighterId.BYTE) }
        assertEquals("the debug menu lifts the cap", Balance.MAX_LEVEL + 1, Economy.upgrade(top, FighterId.BYTE, noCap = true).save.progress(FighterId.BYTE).level)
        assertEquals("and can make it free", 0, Economy.upgrade(s, FighterId.BYTE, factor = 0f).value)
        assertEquals("rounding goes up at the half", 3, Economy.roundHalfUp(2.5))
    }

    // ---------------------------------------------------------------- rewards

    @Test fun ownedThingsArePaidOutInstead() {
        val s = SaveData(fighters = unlocked(FighterId.BYTE, FighterId.KITO))
        assertEquals(Reward.Bolts(300), Economy.grant(s, Reward.UnlockFighter(FighterId.KITO)).value)
        assertEquals(Reward.Prisms(30), Economy.grant(s, Reward.SkinReward(FighterId.BYTE, 0)).value)
        val gained = Economy.grant(s, Reward.SkinReward(FighterId.BYTE, 1))
        assertEquals(Reward.SkinReward(FighterId.BYTE, 1), gained.value)
        assertEquals(setOf(0, 1), gained.save.progress(FighterId.BYTE).ownedSkins)
        // Inside a bundle each item is checked in turn, so the same colourway twice pays out the second time.
        val twice = Economy.grant(s, Reward.Bundle(listOf(Reward.SkinReward(FighterId.BYTE, 2), Reward.SkinReward(FighterId.BYTE, 2), Reward.Bolts(5))))
        assertEquals(Reward.Bundle(listOf(Reward.SkinReward(FighterId.BYTE, 2), Reward.Prisms(30), Reward.Bolts(5))), twice.value)
        assertEquals(s.bolts + 5, twice.save.bolts)
        assertEquals(s.prisms + 30, twice.save.prisms)
    }

    @Test fun creditsFillTheRoadAndBecomeGloryOnceItIsFinished() {
        val first = SparkRoad.steps.first()
        assertEquals(Reward.Credits(10), Economy.grant(SaveData(), Reward.Credits(10)).value)
        refused(402) { Economy.roadUnlock(SaveData(credits = first.cost - 1)) }
        val unlocked = Economy.roadUnlock(SaveData(credits = first.cost + 7))
        assertEquals(Reward.UnlockFighter(first.fighter), unlocked.value)
        assertTrue(unlocked.save.progress(first.fighter).unlocked)
        assertEquals("what is left stays on the road", 7, unlocked.save.credits)
        // Everyone unlocked: the road is finished, and Credits are Glory from then on.
        val all = SaveData(fighters = FighterId.entries.associateWith { FighterProgress(unlocked = true) })
        refused(409) { Economy.roadUnlock(all) }
        val glory = Economy.grant(all, Reward.Credits(40))
        assertEquals(Reward.Glory(40), glory.value)
        assertEquals(40, glory.save.glory)
        assertEquals(0, glory.save.credits)
        // Unlocking the last fighter turns the credits left over into Glory.
        val lastStep = SparkRoad.steps.last()
        val nearly = SaveData(credits = lastStep.cost + 25, fighters = FighterId.entries.associateWith { FighterProgress(unlocked = it != lastStep.fighter) })
        val finished = Economy.roadUnlock(nearly)
        assertEquals(0 to 25, finished.save.credits to finished.save.glory)
    }

    // ---------------------------------------------------------------- the shop

    @Test fun theShopChargesCrystalsAndRefusesWhatIsOwned() {
        val s = SaveData(prisms = 1000)
        val crate = Economy.buy(s, "crate_s")
        assertEquals(Reward.Bolts(400), crate.value)
        assertEquals(990, crate.save.prisms)
        assertEquals(s.bolts + 400, crate.save.bolts)
        refused(404) { Economy.buy(s, "nonsense") }
        refused(402) { Economy.buy(SaveData(prisms = 1), "crate_s") }
        val kito = Economy.buy(s, "fighter_KITO")
        assertTrue(kito.save.progress(FighterId.KITO).unlocked)
        refused(409) { Economy.buy(kito.save, "fighter_KITO") }
        refused(409) { Economy.buy(s, "skin_MIRA_1") }   // the fighter has to be unlocked first
        assertTrue(Economy.buy(kito.save, "skin_KITO_1").save.progress(FighterId.KITO).ownedSkins.contains(1))
        assertEquals("every shop item can be found by its key", 0, (Shop.boltCrates + Shop.creditPacks + Shop.fighterOffers + Shop.skinOffers).count { Economy.shopItem(it.key) == null })
        assertEquals(20, Economy.shopItem("skin_BYTE_1")!!.second)
    }

    @Test fun theDailyGiftAlternatesAndComesOncePerDay() {
        val s = SaveData()
        val even = Economy.claimGift(s, 10)
        assertEquals(Reward.Bolts(40), even.value)
        assertEquals(Reward.Prisms(8), Economy.claimGift(s, 11).value)
        refused(409) { Economy.claimGift(even.save, 10) }
        assertEquals(Reward.Prisms(8), Economy.claimGift(even.save, 11).value)
    }

    @Test fun cupTrackRewardsAreClaimedOnceAndOnlyWhenReached() {
        val s = SaveData(cups = 30, bestCups = 30)
        refused(404) { Economy.claimMilestone(s, 11) }
        refused(409) { Economy.claimMilestone(s, 40) }
        val first = Economy.claimMilestone(s, 10)
        assertEquals(Reward.Bolts(40), first.value)
        refused(409) { Economy.claimMilestone(first.save, 10) }
        // Losing Cups doesn't revoke what was reached.
        assertEquals(Reward.Prisms(10), Economy.claimMilestone(s.copy(cups = 0), 25).value)
        assertEquals(CupTrack.milestones.size, CupTrack.milestones.map { it.cups }.distinct().size)
    }

    // ---------------------------------------------------------------- the Spark Pass

    @Test fun theSparkPassFillsClaimsAndStartsAgainEachSeason() {
        val day = 100L
        var s = Economy.addPassPoints(SaveData(), 250, day)
        assertEquals(250, Economy.passView(s, day).points)
        refused(409) { Economy.claimPass(s, 3, day) }
        refused(404) { Economy.claimPass(s, 0, day) }
        val claimed = Economy.claimPass(s, 2, day)
        assertEquals(SparkPass.reward(2), claimed.value)
        refused(409) { Economy.claimPass(claimed.save, 2, day) }
        assertEquals(setOf(2), Economy.passView(claimed.save, day).claimed)
        // The next season starts from nothing.
        val later = day + SparkPass.SEASON_DAYS
        assertEquals(0, Economy.passView(claimed.save, later).points)
        assertEquals(emptySet<Int>(), Economy.passView(claimed.save, later).claimed)
        // Points stop at the top tier.
        s = Economy.addPassPoints(s, 1_000_000, day)
        assertEquals(SparkPass.TIERS * SparkPass.TIER_POINTS, s.passPoints)
        assertEquals(SparkPass.endDay(day), (SparkPass.season(day) + 1) * SparkPass.SEASON_DAYS)
        assertEquals(Reward.Credits(150), SparkPass.reward(10))
        assertEquals(Reward.Prisms(30), SparkPass.reward(5))
        assertEquals(Reward.Credits(30), SparkPass.reward(1))
        assertEquals(Reward.Bolts(240), SparkPass.reward(2))
    }

    // ---------------------------------------------------------------- deals and daily offers

    @Test fun dealsAreBoughtWithinTheirLimitAndExpiry() {
        val deal = CustomOffer(id = 1, title = "Deal", bolts = 100, currency = Currency.PRISMS, price = 5, limit = 2)
        val s = SaveData(prisms = 20, customOffers = listOf(deal))
        val one = Economy.buyDeal(s, 1, now = 0)
        assertEquals(Reward.Bolts(100), one.value)
        assertEquals(15, one.save.prisms)
        assertEquals(1, one.save.customOffers.single().purchased)
        val two = Economy.buyDeal(one.save, 1, now = 0)
        refused(409) { Economy.buyDeal(two.save, 1, now = 0) }          // sold out
        refused(402) { Economy.buyDeal(SaveData(prisms = 1, customOffers = listOf(deal)), 1, now = 0) }
        refused(404) { Economy.buyDeal(s, 9, now = 0) }
        val ending = SaveData(prisms = 20, customOffers = listOf(deal.copy(expiresAt = 1000)))
        refused(409) { Economy.buyDeal(ending, 1, now = 1000) }
        assertEquals(Reward.Bolts(100), Economy.buyDeal(ending, 1, now = 999).value)
        // A developer's deal is made and taken away again.
        val made = Economy.createDeal(SaveData(), deal.copy(id = 0, title = "  Mine  ", purchased = 4))
        assertEquals(1L, made.value)
        assertEquals("Mine" to 0, made.save.customOffers.single().let { it.title to it.purchased })
        refused(400) { Economy.createDeal(SaveData(), CustomOffer(id = 0, title = "Empty")) }
        refused(400) { Economy.createDeal(SaveData(), CustomOffer(id = 0, title = "Skin", skinFighter = FighterId.BYTE, skinIndex = 9)) }
        assertTrue(Economy.deleteDeal(made.save, 1).value)
        assertTrue(Economy.deleteDeal(made.save, 1).save.customOffers.isEmpty())
        assertFalse(Economy.deleteDeal(made.save, 7).value)
    }

    @Test fun theDaysOffersAreTheSameAllDayAndOnceEach() {
        val day = 20_000L
        val offers = Economy.dailyOffers(day)
        assertEquals(Economy.OFFERS_PER_DAY, offers.size)
        assertEquals(offers, Economy.dailyOffers(day))
        assertEquals(listOf(0L, 1L, 2L), offers.map { it.id })
        assertEquals("titles are what tells them apart", offers.size, offers.map { it.title }.distinct().size)
        assertTrue("another day, another line-up", (day + 1..day + 30).any { Economy.dailyOffers(it).map { o -> o.title } != offers.map { o -> o.title } })
        val s = SaveData(bolts = 10_000, prisms = 10_000)
        refused(404) { Economy.buyDaily(s, 3, day) }
        val first = Economy.buyDaily(s, 0, day)
        assertEquals(offers[0].reward, first.value)
        assertTrue(Economy.boughtToday(first.save, offers[0].title, day))
        refused(409) { Economy.buyDaily(first.save, 0, day) }
        assertFalse("the next day it can be bought again", Economy.boughtToday(first.save, offers[0].title, day + 1))
        assertTrue(Economy.buyDaily(first.save, 0, day + 1).save.dailyBought.isNotEmpty())
    }

    // ---------------------------------------------------------------- what a match pays

    @Test fun cupsFollowTheModesTable() {
        val c = Trophies::cupDelta
        // Last Spark pays by place and nothing else.
        assertEquals(25, c(GameMode.LAST_SPARK, MatchOutcome.VICTORY, 1, 0, false))
        assertEquals(3, c(GameMode.LAST_SPARK, MatchOutcome.DEFEAT, 8, 500, false))
        assertEquals(0, c(GameMode.LAST_SPARK, MatchOutcome.DEFEAT, 10, 500, false))
        // Knockout Rush: a win, an MVP bonus, a draw, and a defeat that costs 1 Cup per 80 held, up to 6.
        assertEquals(8, c(GameMode.KNOCKOUT_RUSH, MatchOutcome.VICTORY, 0, 0, false))
        assertEquals(10, c(GameMode.KNOCKOUT_RUSH, MatchOutcome.VICTORY, 0, 0, true))
        assertEquals(1, c(GameMode.KNOCKOUT_RUSH, MatchOutcome.DRAW, 0, 0, false))
        assertEquals("beginners lose nothing", 0, c(GameMode.KNOCKOUT_RUSH, MatchOutcome.DEFEAT, 0, 79, false))
        assertEquals(-1, c(GameMode.KNOCKOUT_RUSH, MatchOutcome.DEFEAT, 0, 80, false))
        assertEquals(-6, c(GameMode.KNOCKOUT_RUSH, MatchOutcome.DEFEAT, 0, 5000, false))
        assertEquals(5, c(GameMode.BOSS, MatchOutcome.VICTORY, 0, 0, true))
        assertEquals(0, c(GameMode.BOSS, MatchOutcome.DEFEAT, 0, 900, false))
        assertEquals("the Training Area never pays", 0, c(GameMode.TRAINING, MatchOutcome.VICTORY, 0, 0, true))
    }

    @Test fun matchPayDependsOnTheFinishAndTheBotDifficulty() {
        assertEquals(0, Economy.matchBolts(GameMode.TRAINING, MatchOutcome.VICTORY, 0, 5, BotDifficulty.ELITE))
        assertEquals(32, Economy.matchBolts(GameMode.LAST_SPARK, MatchOutcome.VICTORY, 1, 6, BotDifficulty.EASY))   // (30 + 12) * 0.75, rounded up
        assertEquals(28, Economy.matchBolts(GameMode.KNOCKOUT_RUSH, MatchOutcome.VICTORY, 0, 2, BotDifficulty.NORMAL))
        assertEquals("a defeat still pays, and the knockout bonus stops at six", 22, Economy.matchBolts(GameMode.KNOCKOUT_RUSH, MatchOutcome.DEFEAT, 0, 7, BotDifficulty.NORMAL))
        assertEquals(10, Economy.firstWinPrisms(GameMode.KNOCKOUT_RUSH, MatchOutcome.VICTORY, SaveData(), 5))
        assertEquals(0, Economy.firstWinPrisms(GameMode.KNOCKOUT_RUSH, MatchOutcome.VICTORY, SaveData(lastFirstWinDay = 5), 5))
        assertEquals(0, Economy.firstWinPrisms(GameMode.BOSS, MatchOutcome.VICTORY, SaveData(), 5))
        assertEquals(0, Economy.firstWinPrisms(GameMode.KNOCKOUT_RUSH, MatchOutcome.DEFEAT, SaveData(), 5))
        assertEquals(6, Economy.matchCredits(GameMode.LAST_SPARK, MatchOutcome.DEFEAT, 4))
        assertEquals(2, Economy.matchCredits(GameMode.LAST_SPARK, MatchOutcome.DEFEAT, 5))
        assertEquals(1, Economy.matchCredits(GameMode.BOSS, MatchOutcome.DEFEAT, 0))
        assertEquals(40, Economy.passPoints(GameMode.KNOCKOUT_RUSH, MatchOutcome.VICTORY, 0))
        assertEquals(8, Economy.passPoints(GameMode.BOSS, MatchOutcome.DEFEAT, 0))
        assertTrue(Economy.earnsDrop(GameMode.LAST_SPARK, MatchOutcome.DEFEAT, 4))
        assertFalse(Economy.earnsDrop(GameMode.LAST_SPARK, MatchOutcome.VICTORY, 5))
        assertTrue(Economy.earnsDrop(GameMode.KNOCKOUT_RUSH, MatchOutcome.VICTORY, 0))
        assertFalse(Economy.earnsDrop(GameMode.BOSS, MatchOutcome.VICTORY, 0))
    }

    @Test fun settlingAMatchPaysEverythingAndTheSaveShowsIt() {
        val day = 50L
        val before = SaveData(cups = 100, bestCups = 100, capsules = 1)
        val done = Economy.settleMatch(before, report(GameMode.KNOCKOUT_RUSH, MatchOutcome.VICTORY, mvp = true), day)
        val v = done.value
        assertEquals(10, v.cupDelta)
        assertEquals(110, v.cups)
        assertEquals(2, v.mvpCups)
        assertTrue(v.drop)
        assertEquals(2, v.drops)
        assertEquals(SparkCapsules.PER_DAY - 1, v.dropsLeftToday)
        assertEquals(28, v.bolts)
        assertEquals(10, v.firstWinPrisms)
        assertEquals(6, v.credits)
        assertEquals(40, v.passPoints)
        assertEquals(0 to 10, v.fighterCupsBefore to v.fighterCups)
        assertEquals(before.bolts + 28, done.save.bolts)
        assertEquals(before.prisms + 10, done.save.prisms)
        assertEquals(day, done.save.lastFirstWinDay)
        assertEquals(6, done.save.credits)
        assertEquals(40, Economy.passView(done.save, day).points)
        assertEquals(10, done.save.progress(FighterId.BYTE).cups)
        // The same day's second win pays no first-win Crystals.
        assertEquals(0, Economy.settleMatch(done.save, report(GameMode.KNOCKOUT_RUSH, MatchOutcome.VICTORY), day).value.firstWinPrisms)
        // Applying it: Cups and drops are taken on, and the Cup Track reward that was just reached shows up.
        val (after, rewards) = Progression.applyMatch(done.save, report(GameMode.KNOCKOUT_RUSH, MatchOutcome.VICTORY, mvp = true), day, v)
        assertEquals(110, after.cups)
        assertEquals(110, after.bestCups)
        assertEquals(2, after.capsules)
        assertEquals(listOf<Int>(), rewards.newlyReachedMilestones.map { it.cups })
        // Training changes nothing but the count.
        val training = Economy.settleMatch(before, report(GameMode.TRAINING, MatchOutcome.VICTORY), day)
        assertEquals(before, training.save)
        assertEquals(0, training.value.cupDelta)
    }

    @Test fun aDayHoldsAtMostThreeDropsAndLosingNeverGoesBelowZero() {
        val day = 9L
        val full = SaveData(capsuleDay = day, capsulesEarnedToday = SparkCapsules.PER_DAY, capsules = 2)
        val won = Economy.settleMatch(full, report(GameMode.KNOCKOUT_RUSH, MatchOutcome.VICTORY), day)
        assertFalse(won.value.drop)
        assertEquals(0, won.value.dropsLeftToday)
        assertEquals(2, won.value.drops)
        val yesterday = Economy.settleMatch(full, report(GameMode.KNOCKOUT_RUSH, MatchOutcome.VICTORY), day + 1)
        assertTrue("a new day opens the cap again", yesterday.value.drop)
        val beginner = Economy.settleMatch(SaveData(cups = 3, bestCups = 3), report(GameMode.KNOCKOUT_RUSH, MatchOutcome.DEFEAT), day)
        assertEquals("beginners lose nothing", 3 to 0, beginner.value.cups to beginner.value.cupDelta)
        val veteran = Economy.settleMatch(SaveData(cups = 500, bestCups = 500), report(GameMode.KNOCKOUT_RUSH, MatchOutcome.DEFEAT), day)
        assertEquals(494 to -6, veteran.value.cups to veteran.value.cupDelta)
        assertEquals("a lost fighter's Cups follow the player's", 0, veteran.save.progress(FighterId.BYTE).cups)
    }

    // ---------------------------------------------------------------- Spark Drops

    @Test fun dropsAreOpenedOneByOneAndAddTheirRewardToTheSave() {
        val s = SaveData(capsules = 3)
        val done = Economy.openDrops(s, 0f, false, 1, Random(1))
        val result = done.value.single()
        assertEquals(2 + (result.pieces - 1), done.save.capsules)
        assertEquals(1, done.save.capsulesOpened)
        assertEquals("split pieces are the boosted ones", result.pieces - 1, done.save.boostedCapsules)
        assertTrue(Economy.openDrops(SaveData(capsules = 0), 0f, false, 1, Random(1)).value.isEmpty())
        // Debug: a free drop isn't used up, even with none held.
        val free = Economy.openDrops(SaveData(capsules = 0), 0f, true, 1, Random(1))
        assertEquals(1, free.value.size)
        assertEquals(free.value.single().pieces - 1, free.save.capsules)
    }

    @Test fun openingEveryDropLeavesTheSplitsForNext() {
        val done = Economy.openDrops(SaveData(capsules = 40), 0f, false, null, Random(7))
        assertEquals("exactly the drops held at the start are opened", 40, done.value.size)
        val extra = done.value.sumOf { it.pieces - 1 }
        assertEquals(extra, done.save.capsules)
        assertTrue("pieces are opened as soon as they split off, so only some are left", done.save.boostedCapsules in 0..extra)
        assertEquals(40, done.save.capsulesOpened)
        assertTrue("every roll gave something", done.value.all { it.reward != Reward.Bundle(emptyList()) })
        // Luck pushes the tiers up: with max luck most drops are Ultra, and no tier is ever missing from the table.
        val lucky = Economy.openDrops(SaveData(capsules = 200), SparkCapsules.MAX_LUCK, false, null, Random(3)).value
        assertTrue(lucky.count { it.tier == CapsuleTier.ULTRA } > 100)
        // (A plain drop, one at a time: pieces that split off roll better and are never Scrap.)
        val rng = Random(4)
        val plain = (0 until 2000).map { Economy.openDrops(SaveData(capsules = 1), 0f, false, 1, rng).value.single() }
        assertEquals(CapsuleTier.entries.toSet(), plain.map { it.tier }.toSet())
        assertTrue("scrap is the common one", plain.count { it.tier == CapsuleTier.SCRAP } > plain.count { it.tier == CapsuleTier.TUNED })
    }

    @Test fun aDropNeverGivesAColourwayAlreadyOwnedAndSkinsNeedTheFighter() {
        // Only Byte is unlocked, so only Byte's two other colourways can come out as skins.
        val seen = HashSet<Reward.SkinReward>()
        var s = SaveData(capsules = 3000)
        val results = Economy.openDrops(s, SparkCapsules.MAX_LUCK, false, null, Random(11))
        s = results.save
        fun skins(r: Reward): List<Reward.SkinReward> = when (r) { is Reward.SkinReward -> listOf(r); is Reward.Bundle -> r.items.flatMap { skins(it) }; else -> emptyList() }
        results.value.flatMap { skins(it.reward) }.forEach { seen += it }
        assertTrue(seen.all { it.fighter == FighterId.BYTE })
        assertTrue(seen.size <= 2)
        assertEquals(setOf(0, 1, 2), s.progress(FighterId.BYTE).ownedSkins)
    }

    @Test fun theDebugMenusHandOutsChangeTheSave() {
        val s = Economy.devGrant(SaveData(cups = 10, bestCups = 10), cups = 500, drops = 5, bolts = 1000, prisms = 100, credits = 100)
        assertEquals(510, s.cups)
        assertEquals(510, s.bestCups)
        assertEquals(SparkCapsules.STARTING + 5, s.capsules)
        assertEquals(Balance.STARTING_BOLTS + 1000, s.bolts)
        assertEquals(100, s.prisms)
        assertEquals(100, s.credits)
        assertEquals("nothing goes below zero", 0, Economy.devGrant(SaveData(cups = 10), cups = -50).cups)
        assertEquals(Balance.upgradeCostFrom(1), Progression.upgradeCost(SaveData(), FighterId.BYTE))
    }
}
