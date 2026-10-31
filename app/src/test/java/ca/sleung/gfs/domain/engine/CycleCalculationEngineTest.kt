package ca.sleung.gfs.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class CycleCalculationEngineTest {

    private lateinit var engine: CycleCalculationEngine

    @Before
    fun setUp() {
        engine = CycleCalculationEngine()
    }

    @Test
    fun calculateCycleDates_refillDay1_startsOnFirstOfCurrentMonth() {
        val today = LocalDate.of(2026, 9, 8)
        val dates = engine.calculateCycleDates(today, refillDayOfMonth = 1)

        assertEquals(LocalDate.of(2026, 9, 1), dates.startDate)
        assertEquals(LocalDate.of(2026, 10, 1), dates.nextRefillDate)
        assertEquals(23, dates.daysRemaining)
        assertEquals("September", dates.cycleName)
        assertEquals("2026-09", dates.cycleKey)
    }

    @Test
    fun calculateCycleDates_refillDay15_beforeRefillDay_startsInPreviousMonth() {
        val today = LocalDate.of(2026, 9, 8)
        val dates = engine.calculateCycleDates(today, refillDayOfMonth = 15)

        assertEquals(LocalDate.of(2026, 8, 15), dates.startDate)
        assertEquals(LocalDate.of(2026, 9, 15), dates.nextRefillDate)
        assertEquals(7, dates.daysRemaining)
        assertEquals("August", dates.cycleName)
        assertEquals("2026-08", dates.cycleKey)
    }

    @Test
    fun calculateCycleDates_refillDay15_afterRefillDay_startsInCurrentMonth() {
        val today = LocalDate.of(2026, 9, 20)
        val dates = engine.calculateCycleDates(today, refillDayOfMonth = 15)

        assertEquals(LocalDate.of(2026, 9, 15), dates.startDate)
        assertEquals(LocalDate.of(2026, 10, 15), dates.nextRefillDate)
        assertEquals(25, dates.daysRemaining)
        assertEquals("September", dates.cycleName)
        assertEquals("2026-09", dates.cycleKey)
    }

    @Test
    fun calculateCycleDates_shortMonthClamping_februaryNonLeapYear() {
        val today = LocalDate.of(2025, 2, 10)
        val dates = engine.calculateCycleDates(today, refillDayOfMonth = 31)

        assertEquals(LocalDate.of(2025, 1, 31), dates.startDate)
        assertEquals(LocalDate.of(2025, 2, 28), dates.nextRefillDate)
    }

    @Test
    fun calculateCycleDates_shortMonthClamping_februaryLeapYear() {
        val today = LocalDate.of(2024, 2, 10)
        val dates = engine.calculateCycleDates(today, refillDayOfMonth = 31)

        assertEquals(LocalDate.of(2024, 1, 31), dates.startDate)
        assertEquals(LocalDate.of(2024, 2, 29), dates.nextRefillDate)
    }

    @Test
    fun calculateCycleDates_shortMonthClamping_thirtyDayMonth() {
        val today = LocalDate.of(2026, 4, 10)
        val dates = engine.calculateCycleDates(today, refillDayOfMonth = 31)

        assertEquals(LocalDate.of(2026, 3, 31), dates.startDate)
        assertEquals(LocalDate.of(2026, 4, 30), dates.nextRefillDate)
        assertEquals(20, dates.daysRemaining)
        assertEquals("2026-03", dates.cycleKey)
    }

    @Test
    fun calculateCycleDates_onExactRefillDay_startsNewCycleToday() {
        val today = LocalDate.of(2026, 9, 15)
        val dates = engine.calculateCycleDates(today, refillDayOfMonth = 15)

        assertEquals(LocalDate.of(2026, 9, 15), dates.startDate)
        assertEquals(LocalDate.of(2026, 10, 15), dates.nextRefillDate)
        assertEquals(30, dates.daysRemaining)
        assertEquals("2026-09", dates.cycleKey)
    }

    @Test
    fun calculateCycleDates_decemberToJanuaryYearBoundary_transitionsCleanly() {
        val today = LocalDate.of(2026, 12, 20)
        val dates = engine.calculateCycleDates(today, refillDayOfMonth = 15)

        assertEquals(LocalDate.of(2026, 12, 15), dates.startDate)
        assertEquals(LocalDate.of(2027, 1, 15), dates.nextRefillDate)
        assertEquals(26, dates.daysRemaining)
        assertEquals("2026-12", dates.cycleKey)
    }

    @Test
    fun calculateCycleDates_outOfRangeRefillDay_clampsDefensively() {
        val today = LocalDate.of(2026, 9, 10)
        assertEquals(LocalDate.of(2026, 9, 1), engine.calculateCycleDates(today, 0).startDate)
        assertEquals(LocalDate.of(2026, 8, 31), engine.calculateCycleDates(today, 99).startDate)
    }

    @Test
    fun computeRollover_underMaxCap_fullAmountRolledOverNoOverflow() {
        val result = engine.computeRollover(
            previousRemainingBalance = 350.0,
            maxRolloverCap = 1000.0,
            currentExtraSaved = 50.0
        )

        assertEquals(350.0, result.rolloverAmount, 0.001)
        assertEquals(0.0, result.overflowAmount, 0.001)
        assertEquals(50.0, result.newTotalExtraSaved, 0.001)
    }

    @Test
    fun computeRollover_exceedsMaxCap_cappedAndTracksExtraSaved() {
        val result = engine.computeRollover(
            previousRemainingBalance = 1250.0,
            maxRolloverCap = 1000.0,
            currentExtraSaved = 100.0
        )

        assertEquals(1000.0, result.rolloverAmount, 0.001)
        assertEquals(250.0, result.overflowAmount, 0.001)
        assertEquals(350.0, result.newTotalExtraSaved, 0.001)
    }

    @Test
    fun computeRollover_negativeBalance_carriesOverDeficit() {
        val result = engine.computeRollover(
            previousRemainingBalance = -75.0,
            maxRolloverCap = 1000.0,
            currentExtraSaved = 200.0
        )

        assertEquals(-75.0, result.rolloverAmount, 0.001)
        assertEquals(0.0, result.overflowAmount, 0.001)
        assertEquals(200.0, result.newTotalExtraSaved, 0.001)
    }

    @Test
    fun resolveCycleRollover_negativeBalanceInPreviousMonth_deductsFromNewMonth() {
        val resolution = engine.resolveCycleRollover(
            currentCycleKey = "2026-10",
            lastProcessedCycleKey = "2026-09",
            firstCycleKey = "2026-09",
            currentRolloverAmount = 0.0,
            currentExtraSaved = 0.0,
            monthlyRefillAmount = 500.0,
            maxRolloverCap = 1000.0,
            spentByCycle = mapOf("2026-09" to 625.0)
        )

        assertEquals(-125.0, resolution.rolloverAmount, 0.001)
        assertEquals(0.0, resolution.overflowAmount, 0.001)
        assertEquals(0.0, resolution.newTotalExtraSaved, 0.001)
        assertEquals("2026-10", resolution.processedCycleKey)
        assertEquals("2026-09", resolution.firstCycleKey)
        assertTrue(resolution.shouldPersist)

        val newMonthBudget = 500.0 + resolution.rolloverAmount
        assertEquals(375.0, newMonthBudget, 0.001)
    }

    @Test
    fun resolveCycleRollover_alreadyPersistedNegativeBalance_isIdempotent() {
        val resolution = engine.resolveCycleRollover(
            currentCycleKey = "2026-10",
            lastProcessedCycleKey = "2026-10",
            firstCycleKey = "2026-09",
            currentRolloverAmount = -125.0,
            currentExtraSaved = 0.0,
            monthlyRefillAmount = 500.0,
            maxRolloverCap = 1000.0,
            spentByCycle = mapOf("2026-09" to 625.0, "2026-10" to 50.0)
        )

        assertEquals(-125.0, resolution.rolloverAmount, 0.001)
        assertEquals(0.0, resolution.newTotalExtraSaved, 0.001)
        assertEquals("2026-10", resolution.processedCycleKey)
        assertEquals("2026-09", resolution.firstCycleKey)
        assertFalse(resolution.shouldPersist)
    }

    @Test
    fun resolveCycleRollover_multiMonthDeficitAndRecovery_tracksAcrossMonths() {
        val resolution = engine.resolveCycleRollover(
            currentCycleKey = "2026-11",
            lastProcessedCycleKey = "2026-10",
            firstCycleKey = "2026-09",
            currentRolloverAmount = -150.0,
            currentExtraSaved = 0.0,
            monthlyRefillAmount = 500.0,
            maxRolloverCap = 1000.0,
            spentByCycle = mapOf(
                "2026-09" to 650.0,
                "2026-10" to 400.0
            )
        )

        assertEquals(-50.0, resolution.rolloverAmount, 0.001)
        assertEquals("2026-11", resolution.processedCycleKey)
        assertTrue(resolution.shouldPersist)
    }

    @Test
    fun resolveCycleRollover_legacyStateWithUnprocessedPriorCycle_recoversNegativeRollover() {
        val resolution = engine.resolveCycleRollover(
            currentCycleKey = "2026-10",
            lastProcessedCycleKey = "2026-10",
            firstCycleKey = "",
            currentRolloverAmount = 0.0,
            currentExtraSaved = 0.0,
            monthlyRefillAmount = 500.0,
            maxRolloverCap = 1000.0,
            spentByCycle = mapOf("2026-09" to 580.0)
        )

        assertEquals(-80.0, resolution.rolloverAmount, 0.001)
        assertEquals("2026-09", resolution.firstCycleKey)
        assertTrue(resolution.shouldPersist)
    }

    @Test
    fun resolveCycleRollover_positiveSurplusExceedingCap_capsRolloverAndAccumulatesExtraSaved() {
        val resolution = engine.resolveCycleRollover(
            currentCycleKey = "2026-11",
            lastProcessedCycleKey = "2026-08",
            firstCycleKey = "2026-08",
            currentRolloverAmount = 0.0,
            currentExtraSaved = 50.0,
            monthlyRefillAmount = 500.0,
            maxRolloverCap = 1000.0,
            spentByCycle = mapOf(
                "2026-08" to 0.0,
                "2026-09" to 0.0,
                "2026-10" to 200.0
            )
        )

        assertEquals(1000.0, resolution.rolloverAmount, 0.001)
        assertEquals(300.0, resolution.overflowAmount, 0.001)
        assertEquals(350.0, resolution.newTotalExtraSaved, 0.001)
        assertEquals("2026-11", resolution.processedCycleKey)
        assertTrue(resolution.shouldPersist)
    }

    @Test
    fun roundToCents_andCycleKeyValidation_handleEdgeCases() {
        assertEquals(0.30, CycleCalculationEngine.roundToCents(0.1 + 0.2), 0.0000001)
        assertEquals(0.0, CycleCalculationEngine.roundToCents(-0.001), 0.0000001)
        assertEquals(0.0, CycleCalculationEngine.roundToCents(Double.NaN), 0.0000001)
        assertTrue(engine.isValidCycleKey("2026-10"))
        assertFalse(engine.isValidCycleKey("2026-13"))
        assertFalse(engine.isValidCycleKey("not-a-cycle"))
    }
}
