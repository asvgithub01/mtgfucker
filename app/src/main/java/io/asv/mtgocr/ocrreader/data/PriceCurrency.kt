package io.asv.mtgocr.ocrreader.data

import android.content.Context
import io.asv.mtgocr.ocrreader.model.CardCondition
import io.asv.mtgocr.ocrreader.model.CardInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import java.util.concurrent.TimeUnit

internal object PriceMath {
    fun parse(raw: String?): Double? {
        var value = raw.orEmpty().trim().replace("\u00a0", "").replace(" ", "")
            .replace(Regex("[^0-9,.-]"), "")
        if (value.isBlank() || value == "-" || value == "." || value == ",") return null
        val comma = value.lastIndexOf(',')
        val dot = value.lastIndexOf('.')
        value = when {
            comma >= 0 && dot >= 0 && comma > dot -> value.replace(".", "").replace(',', '.')
            comma >= 0 && dot >= 0 -> value.replace(",", "")
            comma >= 0 -> value.replace(',', '.')
            else -> value
        }
        return value.toDoubleOrNull()
    }

    fun convert(amount: Double, source: String, target: String, eurToUsd: Double): Double = when {
        source.equals(target, true) -> amount
        source.equals("EUR", true) && target.equals("USD", true) -> amount * eurToUsd
        source.equals("USD", true) && target.equals("EUR", true) -> amount / eurToUsd
        else -> amount
    }
}

/** One presentation currency for every price, backed by a daily cached EUR/USD reference rate. */
object PriceCurrency {
    const val EUR = "EUR"
    const val USD = "USD"

    private const val PREFS = "price_currency_preferences"
    private const val KEY_CURRENCY = "display_currency"
    private const val KEY_EUR_USD = "eur_usd_rate"
    private const val KEY_RATE_TIME = "rate_time"
    private const val DEFAULT_EUR_USD = 1.10f
    private const val MAX_RATE_AGE_MS = 24L * 60L * 60L * 1000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    @JvmStatic
    fun preferred(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY_CURRENCY, EUR)
        .takeIf { it == EUR || it == USD } ?: EUR

    @JvmStatic
    fun select(context: Context, currency: String) {
        val normalized = currency.uppercase(Locale.ROOT).takeIf { it == EUR || it == USD } ?: EUR
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_CURRENCY, normalized)
            .apply()
    }

    @JvmStatic
    fun amount(context: Context, card: CardInfo): Double = amountOrNull(context, card) ?: 0.0

    /** Parsed, condition-adjusted display amount, or null when this card has no usable price. */
    @JvmStatic
    fun amountOrNull(context: Context, card: CardInfo): Double? {
        val rawAmount = rawAmount(card) ?: return null
        val adjusted = CardCondition.adjustedAmount(rawAmount, card.condition)
        return convert(context, adjusted, sourceCurrency(card.basePrice))
    }

    /** True only when the same value used by [amount] can actually be parsed. */
    @JvmStatic
    fun hasAmount(card: CardInfo): Boolean = rawAmount(card) != null

    @JvmStatic
    fun format(context: Context, card: CardInfo): String {
        if (!hasAmount(card)) return ""
        return formatAmount(amount(context, card), preferred(context))
    }

    @JvmStatic
    fun format(context: Context, amount: Double, sourceCurrency: String): String =
        formatAmount(convert(context, amount, sourceCurrency), preferred(context))

    @JvmStatic
    fun convert(context: Context, amount: Double, sourceCurrency: String): Double = PriceMath.convert(
        amount,
        sourceCurrency.ifBlank { EUR },
        preferred(context),
        eurToUsd(context)
    )

    @JvmStatic
    fun sourceCurrency(label: String?): String = when {
        label.orEmpty().uppercase(Locale.ROOT).contains(USD) -> USD
        else -> EUR
    }

    @JvmStatic
    fun refreshRateIfNeeded(context: Context, callback: (Boolean) -> Unit = {}) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (System.currentTimeMillis() - prefs.getLong(KEY_RATE_TIME, 0L) < MAX_RATE_AGE_MS) {
            callback(true)
            return
        }
        scope.launch {
            val updated = runCatching {
                val request = Request.Builder()
                    .url("https://api.frankfurter.dev/v2/rate/EUR/USD")
                    .build()
                client.newCall(request).execute().use { response ->
                    check(response.isSuccessful) { "HTTP ${response.code}" }
                    val rate = JSONObject(response.body?.string().orEmpty()).getDouble("rate")
                    check(rate > 0.0)
                    prefs.edit()
                        .putFloat(KEY_EUR_USD, rate.toFloat())
                        .putLong(KEY_RATE_TIME, System.currentTimeMillis())
                        .apply()
                }
            }.isSuccess
            withContext(Dispatchers.Main) { callback(updated) }
        }
    }

    private fun eurToUsd(context: Context): Double = context
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getFloat(KEY_EUR_USD, DEFAULT_EUR_USD)
        .toDouble()
        .coerceAtLeast(0.0001)

    private fun rawAmount(card: CardInfo): Double? =
        PriceMath.parse(card.priceM).orElse { PriceMath.parse(card.basePrice) }

    private fun formatAmount(amount: Double, currencyCode: String): String {
        return runCatching {
            NumberFormat.getCurrencyInstance(Locale.getDefault()).apply {
                currency = Currency.getInstance(currencyCode)
                minimumFractionDigits = 2
                maximumFractionDigits = 2
            }.format(amount)
        }.getOrElse { String.format(Locale.getDefault(), "%.2f %s", amount, currencyCode) }
    }

    private inline fun <T> T?.orElse(block: () -> T?): T? = this ?: block()
}
