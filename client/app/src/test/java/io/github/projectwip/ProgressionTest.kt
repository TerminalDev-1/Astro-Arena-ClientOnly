package io.github.projectwip

import io.github.projectwip.data.Balance
import io.github.projectwip.data.BotDifficulty
import io.github.projectwip.data.CupTrack
import io.github.projectwip.data.FighterId
import io.github.projectwip.data.FighterProgress
import io.github.projectwip.data.GameMode
import io.github.projectwip.data.MatchOutcome
import io.github.projectwip.data.MatchReport
import io.github.projectwip.data.MatchVerdict
import io.github.projectwip.data.Progression
import io.github.projectwip.data.Reward
import io.github.projectwip.data.SaveData
import io.github.projectwip.data.Settings
import io.github.projectwip.data.SparkCapsules
import io.github.projectwip.data.SparkRoad
import io.github.projectwip.data.StatLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressionTest {

    private fun report(outcome: MatchOutcome, kos: Int = 2, d: BotDifficulty = BotDifficulty.NORMAL) =
        MatchReport(outcome = outcome, fighter = FighterId.BYTE, kos = kos, deaths = 1, damageDealt = 1000,
            mvp = false, difficulty = d, blueScore = 10, redScore = 5)

    @Test fun statLineIsLinear() {
        val s = StatLine(100, 5)
        assertEquals(100, s.at(1))
        assertEquals(120, s.at(5))
        assertEquals(125, s.at(6))
        assertEquals("no level cap", 100 + 5 * 98, s.at(99))
    }

    @Test fun upgradePreviewShowsExactDelta() {
        val byte = Balance.fighter(FighterId.BYTE)
        val rows = Progression.statPreview(byte, 4)
        val dmg = rows[1]
        assertEquals(byte.attackDamage.at(4), dmg.current)
        assertEquals(byte.attackDamage.perLevel, dmg.delta)
        assertNull("no next level once capped", Progression.statPreview(byte, Balance.MAX_LEVEL, capped = true)[0].next)
        assertNotNull("with the cap off there is always a next level", Progression.statPreview(byte, 250)[0].next)
    }

    @Test fun aVerdictsTotalsAreAdopted() {
        val verdict = MatchVerdict(8, 8, false, 1, 3, bolts = 28, firstWinPrisms = 10)
        val (a, r1) = Progression.applyMatch(SaveData(), report(MatchOutcome.VICTORY), today = 100, verdict = verdict)
        assertEquals(8, a.cups)
        assertEquals(8, a.bestCups)
        assertEquals(0, r1.cupsBefore)
        assertEquals(8, r1.cupDelta)
        assertEquals(28, r1.bolts)
        assertEquals(10, r1.firstWinPrisms)
        assertEquals("the Bolts are put into the save by Economy.settleMatch, not added up here", SaveData().bolts, a.bolts)
        assertEquals(1, a.victories)
        // A defeat that costs Cups, and a drop earned.
        val lost = MatchVerdict(cupDelta = -6, cups = 494, drop = true, drops = 4, dropsLeftToday = 1)
        val (after, rewards) = Progression.applyMatch(SaveData(cups = 500, bestCups = 500, capsules = 3), report(MatchOutcome.DEFEAT), today = 7, verdict = lost)
        assertEquals(494, after.cups)
        assertEquals(500, after.bestCups)
        assertEquals(4, after.capsules)
        assertEquals(500, rewards.cupsBefore)
        assertTrue(rewards.capsuleEarned)
        assertEquals(1, rewards.capsulesLeftToday)
        assertEquals(1, Progression.capsulesLeftToday(after, 7))
        assertEquals("a new day resets the cap", SparkCapsules.PER_DAY, Progression.capsulesLeftToday(after, 8))
    }

    @Test fun cupTrackRewardsStayClaimableAfterLosingCups() {
        val s = SaveData(cups = 30, bestCups = 30)
        assertEquals(listOf(10, 25), Progression.claimable(s).map { it.cups })
        // A claim is recorded; losing Cups doesn't revoke what was reached.
        assertEquals(listOf(25), Progression.claimable(s.copy(cups = 5, claimedMilestones = setOf(10))).map { it.cups })
    }

    @Test fun dailyGiftOncePerDay() {
        assertTrue(Progression.dailyGiftAvailable(SaveData(), 10))
        assertFalse(Progression.dailyGiftAvailable(SaveData(lastDailyGiftDay = 10), 10))
        assertTrue(Progression.dailyGiftAvailable(SaveData(lastDailyGiftDay = 10), 11))
    }

    @Test fun trackIsSortedAndUnique() {
        val cups = CupTrack.milestones.map { it.cups }
        assertEquals(cups.sorted().distinct(), cups)
    }

    @Test fun dropOddsShownInTheDebugMenu() {
        val normal = SparkCapsules.odds(0f)
        val lucky = SparkCapsules.odds(SparkCapsules.MAX_LUCK)
        assertEquals(1f, normal.sum(), 1e-4f)
        assertEquals(1f, lucky.sum(), 1e-4f)
        assertEquals("Ultra is the rarest tier", 0.02f, normal.last(), 1e-4f)
        assertEquals(normal.min(), normal.last(), 0f)
        assertTrue("max luck makes Ultra the most likely tier", lucky.last() > 0.5f && lucky.last() == lucky.max())
        assertTrue(SparkCapsules.splitChance(SparkCapsules.MAX_LUCK) > SparkCapsules.splitChance(0f))
        assertTrue("chances never exceed 100%", SparkCapsules.splitChance(SparkCapsules.MAX_LUCK) <= 1f && SparkCapsules.resplitChance(SparkCapsules.MAX_LUCK) <= 1f)
        assertEquals("the luck slider tops out at x15", 14f, SparkCapsules.MAX_LUCK, 0f)
    }

    @Test fun levelsNeverRunOut() {
        // The price follows the table, then keeps climbing by a fixed step, and never drops.
        assertEquals(Balance.upgradeCost[0], Balance.upgradeCostFrom(1))
        assertEquals(Balance.upgradeCost.last(), Balance.upgradeCostFrom(Balance.upgradeCost.size))
        assertEquals(Balance.upgradeCost.last() + Balance.UPGRADE_COST_STEP, Balance.upgradeCostFrom(Balance.upgradeCost.size + 1))
        assertTrue((1..300).zipWithNext().all { (a, b) -> Balance.upgradeCostFrom(b) >= Balance.upgradeCostFrom(a) })
        // Normally the cap holds at MAX_LEVEL, and the dev toggle lifts it.
        val top = SaveData.defaultFighters() + (FighterId.BYTE to FighterProgress(true, Balance.MAX_LEVEL))
        val capped = SaveData(bolts = 10_000_000, fighters = top)
        assertTrue(Progression.levelCapped(capped, FighterId.BYTE))
        assertFalse(Progression.canUpgrade(capped, FighterId.BYTE))
        assertTrue(Progression.canUpgrade(capped.copy(settings = Settings(debugNoLevelCap = true)), FighterId.BYTE))
        val byte = Balance.fighter(FighterId.BYTE)
        assertEquals(byte.health.base + byte.health.perLevel * 60, byte.health.at(61))
    }

    @Test fun debugUpgradeCostScalesThePriceShown() {
        val normal = SaveData(bolts = 1000)
        assertEquals(Balance.upgradeCost[0], Progression.upgradeCost(normal, FighterId.BYTE))
        val free = normal.copy(bolts = 0, settings = Settings(debugUpgradeCost = 0f))
        assertEquals(0, Progression.upgradeCost(free, FighterId.BYTE))
        assertTrue(Progression.canUpgrade(free, FighterId.BYTE))
        assertEquals(Balance.upgradeCost[0] * 3, Progression.upgradeCost(normal.copy(settings = Settings(debugUpgradeCost = 3f)), FighterId.BYTE))
    }

    @Test fun walkingOutOfAFreeForAllIsLastPlaceNotFirst() {
        val config = io.github.projectwip.sim.MatchConfig(FighterId.BYTE, 1, 0, "Me", BotDifficulty.EASY, mode = GameMode.LAST_SPARK)
        val match = io.github.projectwip.sim.Match(config)
        assertEquals("still standing reads as first...", 1, match.report().placement)
        val left = match.forfeit()
        assertEquals("...but leaving puts you behind everyone still in", GameMode.LAST_SPARK.players, left.placement)
        assertEquals(MatchOutcome.DEFEAT, left.outcome)
        val team = io.github.projectwip.sim.Match(config.copy(mode = GameMode.KNOCKOUT_RUSH)).forfeit()
        assertEquals(MatchOutcome.DEFEAT, team.outcome)
        assertEquals(0, team.placement)
    }

    @Test fun rosterHasFourFightersAtGenreScale() {
        assertEquals(FighterId.entries.size, Balance.fighters.size)
        assertEquals("the roster lists fighters in id order", FighterId.entries.toList(), Balance.fighters.map { it.id })
        val kito = Balance.fighter(FighterId.KITO)
        assertEquals("Kito", kito.name)
        assertFalse("new fighters start locked", SaveData().progress(FighterId.KITO).unlocked)
        assertNotNull(Balance.unlockPrismPrice(FighterId.KITO))
        // Fighters are unlocked on the Spark Road; the Cup Track pays Credits towards it instead of handing one out.
        assertTrue(SparkRoad.steps.any { it.fighter == FighterId.KITO })
        val varun = Balance.fighter(FighterId.VARUN)
        assertEquals("six rockets a shot, eight in the super", 6 to 8, varun.attack.projectiles to varun.superSpec.projectiles)
        assertEquals("the rarest fighter is the last one on the road", FighterId.VARUN, SparkRoad.steps.last().fighter)
        assertTrue(CupTrack.milestones.none { it.reward is Reward.UnlockFighter })
        assertEquals(SparkRoad.steps.first(), SparkRoad.next(SaveData()))
        // The floor every fighter stands on: enough health, and enough damage from one ammo when it all lands.
        for (f in Balance.fighters) {
            assertTrue("${f.name} health ${f.health.base}", f.health.base >= Balance.MIN_HEALTH)
            assertTrue("${f.name} damage an ammo ${f.attackDamage.base * f.attack.projectiles}", f.attackDamage.base * f.attack.projectiles >= Balance.MIN_AMMO_DAMAGE)
        }
        assertEquals("Varun stands above it", 6500 to 2502, varun.health.base to varun.attackDamage.base * varun.attack.projectiles)
    }
}
