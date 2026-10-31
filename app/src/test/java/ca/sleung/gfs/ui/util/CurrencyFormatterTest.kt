package ca.sleung.gfs.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CurrencyFormatterTest {

    @Test
    fun formatCurrency_wholeAndFractionalDollars_formatsWithGroupingAndSmartCents() {
        assertEquals("$500", CurrencyFormatter.formatCurrency(500.0))
        assertEquals("$1,250", CurrencyFormatter.formatCurrency(1250.0))
        assertEquals("$493.50", CurrencyFormatter.formatCurrency(493.5))
        assertEquals("$0.49", CurrencyFormatter.formatCurrency(0.49))
        assertEquals("-$140", CurrencyFormatter.formatCurrency(-140.0))
        assertEquals("-$12.25", CurrencyFormatter.formatCurrency(-12.25))
        assertEquals("$0", CurrencyFormatter.formatCurrency(-0.001))
    }

    @Test
    fun formatCurrency_withPlusSignAndAlwaysShowCents_formatsAccurately() {
        assertEquals("+$320", CurrencyFormatter.formatCurrency(320.0, showPlusSign = true))
        assertEquals("+$0", CurrencyFormatter.formatCurrency(0.0, showPlusSign = true))
        assertEquals("-$50", CurrencyFormatter.formatCurrency(-50.0, showPlusSign = true))
        assertEquals("$35.00", CurrencyFormatter.formatCurrency(35.0, alwaysShowCents = true))
    }

    @Test
    fun formatCurrency_hideDecimals_roundsToWholeDollarsAcrossValues() {
        assertEquals("$500", CurrencyFormatter.formatCurrency(500.0, hideDecimals = true))
        assertEquals("$494", CurrencyFormatter.formatCurrency(493.50, hideDecimals = true))
        assertEquals("$493", CurrencyFormatter.formatCurrency(493.49, hideDecimals = true))
        assertEquals("$1", CurrencyFormatter.formatCurrency(0.50, hideDecimals = true))
        assertEquals("$0", CurrencyFormatter.formatCurrency(0.49, hideDecimals = true))
        assertEquals("-$140", CurrencyFormatter.formatCurrency(-140.0, hideDecimals = true))
        assertEquals("-$12", CurrencyFormatter.formatCurrency(-12.25, hideDecimals = true))
        assertEquals("+$320", CurrencyFormatter.formatCurrency(320.4, showPlusSign = true, hideDecimals = true))
    }

    @Test
    fun formatExpenseItem_alwaysShowsMinusSignAndTwoDecimals() {
        assertEquals("-$35.00", CurrencyFormatter.formatExpenseItem(35.0))
        assertEquals("-$6.50", CurrencyFormatter.formatExpenseItem(6.5))
        assertEquals("-$0.49", CurrencyFormatter.formatExpenseItem(0.49))
        assertEquals("-$1,200.00", CurrencyFormatter.formatExpenseItem(1200.0))
        // With hideDecimals
        assertEquals("-$35", CurrencyFormatter.formatExpenseItem(35.0, hideDecimals = true))
        assertEquals("-$7", CurrencyFormatter.formatExpenseItem(6.5, hideDecimals = true))
        assertEquals("-$0", CurrencyFormatter.formatExpenseItem(0.49, hideDecimals = true))
    }

    @Test
    fun formatEditableAmount_omitsTrailingZeroesForWholeNumbersAndPreservesCents() {
        assertEquals("35", CurrencyFormatter.formatEditableAmount(35.0))
        assertEquals("6.50", CurrencyFormatter.formatEditableAmount(6.5))
        assertEquals("19.99", CurrencyFormatter.formatEditableAmount(19.99))
        assertEquals("", CurrencyFormatter.formatEditableAmount(0.0))
        assertEquals("", CurrencyFormatter.formatEditableAmount(-10.0))
    }

    @Test
    fun sanitizeInputs_enforcesDecimalPrecisionAndUpperBounds() {
        assertEquals("", CurrencyFormatter.sanitizeAmountInput(""))
        assertEquals("42.50", CurrencyFormatter.sanitizeAmountInput("42.50"))
        assertEquals("42,50", CurrencyFormatter.sanitizeAmountInput("42,50"))
        assertEquals(".5", CurrencyFormatter.sanitizeAmountInput(".5"))
        assertEquals(",5", CurrencyFormatter.sanitizeAmountInput(",5"))
        assertNull(CurrencyFormatter.sanitizeAmountInput("42.555"))
        assertNull(CurrencyFormatter.sanitizeAmountInput("42,555"))
        assertNull(CurrencyFormatter.sanitizeAmountInput("10000000")) // Exceeds 7 digits / MAX_INPUT_AMOUNT
        assertNull(CurrencyFormatter.sanitizeAmountInput("abc"))

        assertEquals("15", CurrencyFormatter.sanitizeDayOfMonthInput("15"))
        assertNull(CurrencyFormatter.sanitizeDayOfMonthInput("315"))
        assertNull(CurrencyFormatter.sanitizeDayOfMonthInput("-1"))
    }

    @Test
    fun parseAmount_handlesDotsCommasAndEdgeCases() {
        assertEquals(42.50, CurrencyFormatter.parseAmount("42.50")!!, 0.001)
        assertEquals(42.50, CurrencyFormatter.parseAmount("42,50")!!, 0.001)
        assertEquals(0.5, CurrencyFormatter.parseAmount(",5")!!, 0.001)
        assertEquals(0.5, CurrencyFormatter.parseAmount(".5")!!, 0.001)
        assertEquals(500.0, CurrencyFormatter.parseAmount("500")!!, 0.001)
        assertNull(CurrencyFormatter.parseAmount(""))
        assertNull(CurrencyFormatter.parseAmount("abc"))
    }
}
