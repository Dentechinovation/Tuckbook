package com.example.model

import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID

object CurrencyUtils {
    /**
     * Formats whole ngwee or float kwacha into Zambian Kwacha string.
     * Example: 1234050L ngwee -> "K12,340.50"
     * Example: 15.50 double kwacha -> "K15.50"
     */
    fun formatKwacha(ngwee: Long): String {
        val kwacha = ngwee / 100.0
        val formatter = NumberFormat.getNumberInstance(Locale.US) as DecimalFormat
        formatter.applyPattern("#,##0.00")
        return "K" + formatter.format(kwacha)
    }

    fun formatKwacha(kwacha: Double): String {
        val formatter = NumberFormat.getNumberInstance(Locale.US) as DecimalFormat
        formatter.applyPattern("#,##0.00")
        return "K" + formatter.format(kwacha)
    }

    /**
     * Parses a string like "12.50" or "K12.50" or "12" into whole ngwee (Long).
     */
    fun parseKwacha(text: String): Long {
        val cleaned = text.trim().removePrefix("K").removePrefix("k").replace(",", "").trim()
        val value = cleaned.toDoubleOrNull() ?: 0.0
        return kotlin.math.round(value * 100.0).toLong()
    }

    fun parseKwachaDouble(text: String): Double {
        val cleaned = text.trim().removePrefix("K").removePrefix("k").replace(",", "").trim()
        return cleaned.toDoubleOrNull() ?: 0.0
    }
}

data class Product(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val category: String = "Snacks",
    val price: Double = 0.0, // Selling price in Kwacha (e.g. 8.0 = K8.00)
    val cost: Double = 0.0,  // Cost price in Kwacha (e.g. 5.5 = K5.50)
    val stock: Int = 0,
    val lowAt: Int = 10,
    val barcode: String? = null,
    val isActive: Boolean = true
) {
    val unitPriceNgwee: Long get() = kotlin.math.round(price * 100.0).toLong()
    val costPriceNgwee: Long get() = kotlin.math.round(cost * 100.0).toLong()
    val margin: Double get() = price - cost
}

data class SaleItem(
    val productId: String = "",
    val name: String = "",
    val qty: Int = 1,
    val price: Double = 0.0,
    val cost: Double = 0.0
) {
    val total: Double get() = qty * price
}

data class Sale(
    val id: String = UUID.randomUUID().toString(),
    val at: Long = System.currentTimeMillis(),
    val items: List<SaleItem> = emptyList(),
    val total: Double = 0.0,
    val tender: String = "cash", // "cash", "airtel", "mtn", "zamtel", "credit"
    val creditorId: String? = null,
    val shift: String? = null,
    val note: String? = null,
    val discount: Double? = null,
    val reversed: Boolean = false,
    val reversedAt: Long? = null,
    val reversedBy: String? = null,
    val voidReason: String? = null,
    val refunded: Double? = null,
    val cashier: String? = null,
    val businessDate: String = ""
) {
    val totalNgwee: Long get() = kotlin.math.round(total * 100.0).toLong()
    val discountNgwee: Long get() = kotlin.math.round((discount ?: 0.0) * 100.0).toLong()
    val isVoided: Boolean get() = reversed
    val paymentMethod: String get() = tenderLabel(tender)
    val itemsSummary: String get() = items.joinToString(", ") { "${it.qty}x ${it.name}" }
}

data class Creditor(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val kind: String = "student", // "student" or "teacher"
    val detail: String = "",      // e.g. "Grade 10B" or "Mathematics Dept"
    val phone: String = "",
    val dueDays: Int = 7
)

data class Repayment(
    val id: String = UUID.randomUUID().toString(),
    val creditorId: String = "",
    val amount: Double = 0.0,
    val at: Long = System.currentTimeMillis(),
    val tender: String = "cash"
)

data class Shift(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val start: String = "10:00", // "HH:mm"
    val end: String = "10:30",
    val enabled: Boolean = true
)

data class Closing(
    val id: String = UUID.randomUUID().toString(),
    val at: Long = System.currentTimeMillis(),
    val shift: String = "",
    val cashier: String = "",
    val expected: Map<String, Double> = emptyMap(),
    val counted: Map<String, Double> = emptyMap(),
    val diff: Double = 0.0,
    val note: String = ""
)

data class Expense(
    val id: String = UUID.randomUUID().toString(),
    val at: Long = System.currentTimeMillis(),
    val category: String = "Stock purchase", // "Stock purchase", "Transport", "Utilities", "Wages", "Rent / levy", "Other"
    val amount: Double = 0.0,
    val description: String = "",
    val note: String? = null,
    val reversed: Boolean = false,
    val businessDate: String = ""
) {
    val amountNgwee: Long get() = kotlin.math.round(amount * 100.0).toLong()
    val title: String get() = description
    val isVoided: Boolean get() = reversed
}

data class Audit(
    val id: String = UUID.randomUUID().toString(),
    val at: Long = System.currentTimeMillis(),
    val actor: String = "Cashier",
    val action: String = "",
    val detail: String = ""
)

data class Settings(
    val shopName: String = "Kabulonga Tuck Shop",
    val school: String = "Kabulonga Girls Secondary, Lusaka",
    val owner: String = "Dennis",
    val email: String = "dennis@example.zm",
    val phone: String = "+260 977 000 111",
    val cashier: String = "Dennis",
    val businessType: String = "School tuck shop",
    val location: String = "Kabulonga, Lusaka",
    val notifyPhone: Boolean = true,
    val notifyEmail: Boolean = true,
    val enforceStock: Boolean = true,
    val rushMode: Boolean = false
)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: String = "user", // "user" or "gemini"
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

fun tenderLabel(tender: String): String = when (tender.lowercase()) {
    "cash" -> "Cash"
    "airtel" -> "Airtel Money"
    "mtn" -> "MTN MoMo"
    "zamtel" -> "Zamtel Kwacha"
    "credit" -> "On Book"
    else -> tender.replaceFirstChar { it.uppercase() }
}
