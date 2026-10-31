package ca.sleung.gfs.ui.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Consistent, cent-accurate currency formatting and input validation utilities.
 */
object CurrencyFormatter {

    const val MAX_INPUT_AMOUNT = 9_999_999.99

    /** Accepts either `.` or `,` as the decimal separator (many locales' keyboards emit `,`). */
    private val DECIMAL_AMOUNT_REGEX = Regex("^\\d{0,7}([.,]\\d{0,2})?$")
    private val INTEGER_DIGITS_REGEX = Regex("^\\d{0,2}$")

    /**
     * Validates user keystrokes/pastes for currency text fields (up to 7 integer digits
     * and optional 2 decimal digits). Either `.` or `,` is accepted as the decimal separator
     * and preserved as typed. Returns the sanitized string if valid, or `null` if rejected.
     */
    fun sanitizeAmountInput(input: String): String? {
        if (input.isEmpty()) return ""
        val cleaned = input.trim()
        if (!DECIMAL_AMOUNT_REGEX.matches(cleaned)) return null
        val parsed = parseAmount(cleaned)
        if (parsed != null && parsed > MAX_INPUT_AMOUNT) return null
        return cleaned
    }

    /**
     * Parses user-entered amount text accepting either `.` or `,` as the decimal separator.
     * Always use this instead of [String.toDoubleOrNull] for amount fields.
     */
    fun parseAmount(input: String): Double? =
        input.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

    /**
     * Validates day-of-month input (up to 2 digits).
     */
    fun sanitizeDayOfMonthInput(input: String): String? {
        if (input.isEmpty()) return ""
        val cleaned = input.trim()
        return if (INTEGER_DIGITS_REGEX.matches(cleaned)) cleaned else null
    }

    /**
     * Formats [amount] with grouping commas (e.g., `$1,250` or `$493.50`).
     *
     * @param alwaysShowCents When `true`, always formats 2 decimal places (`$35.00`).
     *                        When `false`, omits `.00` on exact whole dollars (`$35`) while
     *                        preserving fractional cents (`$35.50`).
     * @param showPlusSign    When `true`, prefixes strictly positive or zero amounts with `+`.
     * @param hideDecimals    When `true`, rounds half-up to whole dollars and never shows cents
     *                        (takes precedence over [alwaysShowCents]). Display-only; stored
     *                        amounts keep full cent precision.
     */
    fun formatCurrency(
        amount: Double,
        alwaysShowCents: Boolean = false,
        showPlusSign: Boolean = false,
        hideDecimals: Boolean = false
    ): String {
        val safeValue = if (amount.isFinite()) amount else 0.0
        val scaled = BigDecimal.valueOf(safeValue)
            .setScale(if (hideDecimals) 0 else 2, RoundingMode.HALF_UP)
        val isNegative = scaled.signum() < 0
        val absValue = scaled.abs()
        val hasCents = absValue.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) != 0

        val symbols = DecimalFormatSymbols(Locale.US)
        val pattern = when {
            hideDecimals -> "#,##0"
            alwaysShowCents || hasCents -> "#,##0.00"
            else -> "#,##0"
        }
        val formattedDigits = DecimalFormat(pattern, symbols).format(absValue)

        return when {
            isNegative -> "-$$formattedDigits"
            showPlusSign -> "+$$formattedDigits"
            else -> "$$formattedDigits"
        }
    }

    /**
     * Formats a logged expense line item with a leading minus sign and 2 decimal places
     * (e.g., `-$35.00`, `-$6.50`), matching the product design specification.
     * When [hideDecimals] is `true`, rounds to whole dollars instead (e.g., `-$35`, `-$7`).
     */
    fun formatExpenseItem(amount: Double, hideDecimals: Boolean = false): String {
        val safeAbs = if (amount.isFinite()) kotlin.math.abs(amount) else 0.0
        return "-${formatCurrency(safeAbs, alwaysShowCents = true, hideDecimals = hideDecimals)}"
    }

    /**
     * Formats an existing numeric amount for pre-filling an editable text field
     * (`35.0` -> `"35"`, `6.5` -> `"6.50"`), avoiding floating-point `.toString()` artifacts.
     */
    fun formatEditableAmount(amount: Double): String {
        if (!amount.isFinite() || amount <= 0.0) return ""
        val scaled = BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP)
        val hasCents = scaled.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) != 0
        return if (hasCents) scaled.toPlainString() else scaled.toBigInteger().toString()
    }
}
