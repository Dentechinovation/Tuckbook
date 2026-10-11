package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Creditor
import com.example.model.CurrencyUtils
import com.example.model.Expense
import com.example.model.Product
import com.example.model.Repayment
import com.example.model.Sale
import com.example.model.Settings
import com.example.model.Shift
import com.example.model.tenderLabel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun TodayScreen(
    sales: List<Sale>,
    expenses: List<Expense>,
    products: List<Product>,
    creditors: List<Creditor>,
    repayments: List<Repayment>,
    shifts: List<Shift>,
    settings: Settings,
    onNavigateToSell: () -> Unit,
    onNavigateToBook: () -> Unit,
    onNavigateToLedger: () -> Unit,
    onNavigateToProducts: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToAdvisor: () -> Unit
) {
    // Current time greeting
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = remember(hour) {
        when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    // Active & Next shift calculation
    val currentMinutes = remember {
        val cal = Calendar.getInstance()
        cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    fun toMinutes(timeStr: String): Int {
        val parts = timeStr.split(":").mapNotNull { it.toIntOrNull() }
        return if (parts.size >= 2) parts[0] * 60 + parts[1] else 0
    }

    val activeShift = shifts.find { s ->
        s.enabled && currentMinutes >= toMinutes(s.start) && currentMinutes < toMinutes(s.end)
    }
    val nextShift = shifts.filter { s -> s.enabled && toMinutes(s.start) > currentMinutes }
        .minByOrNull { toMinutes(it.start) }

    val minutesLeftInActive = if (activeShift != null) (toMinutes(activeShift.end) - currentMinutes) else 0

    // Today's Sales & Financial stats
    val todayStartMs = remember {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.timeInMillis
    }

    val todaySales = sales.filter { !it.reversed && it.at >= todayStartMs }
    val todayExpenses = expenses.filter { !it.reversed && it.at >= todayStartMs }

    val todayRevenue = todaySales.sumOf { it.total - (it.refunded ?: 0.0) }
    val todayCogs = todaySales.sumOf { s -> s.items.sumOf { it.qty * it.cost } }
    val todayExpTotal = todayExpenses.sumOf { it.amount }
    val todayNetProfit = todayRevenue - todayCogs - todayExpTotal

    // Credit Book totals
    fun getCreditorBalance(cId: String): Double {
        val owed = sales.filter { it.creditorId == cId && !it.reversed }.sumOf { it.total }
        val paid = repayments.filter { it.creditorId == cId }.sumOf { it.amount }
        return (owed - paid).coerceAtLeast(0.0)
    }

    val totalOwedOnBook = creditors.sumOf { getCreditorBalance(it.id) }
    val overdueCount = creditors.count { c ->
        val bal = getCreditorBalance(c.id)
        if (bal <= 0.0) false
        else {
            val oldest = sales.filter { it.creditorId == c.id && !it.reversed }.minByOrNull { it.at }
            if (oldest == null) false
            else (System.currentTimeMillis() - oldest.at) > (c.dueDays * 86_400_000L)
        }
    }

    val lowStockItems = products.filter { it.stock <= it.lowAt }

    // Last 7 days chart data
    val last7Days = remember(sales) {
        val cal = Calendar.getInstance()
        (6 downTo 0).map { dayOffset ->
            cal.timeInMillis = System.currentTimeMillis()
            cal.add(Calendar.DAY_OF_YEAR, -dayOffset)
            val dStart = cal.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.timeInMillis
            val dEnd = cal.apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59) }.timeInMillis
            val dayName = SimpleDateFormat("EEE", Locale.US).format(Date(dStart))
            val dayTotal = sales.filter { !it.reversed && it.at in dStart..dEnd }.sumOf { it.total - (it.refunded ?: 0.0) }
            dayName to dayTotal
        }
    }

    val max7DaySales = last7Days.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: 100.0

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Greeting Banner
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "$greeting${if (settings.owner.isNotBlank()) ", ${settings.owner}" else ""}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${settings.shopName} · Lusaka",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedButton(
                    onClick = onNavigateToAdvisor,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("AI Advisor", fontSize = 12.sp)
                }
            }
        }

        // Active Shift / Break Banner
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (activeShift != null) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier.size(36.dp).clip(CircleShape).background(
                                if (activeShift != null) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                            ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PointOfSale, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = if (activeShift != null) "${activeShift.name} is on now" else "No break running",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = if (activeShift != null) "${activeShift.start}–${activeShift.end} · $minutesLeftInActive min left"
                                else if (nextShift != null) "Next: ${nextShift.name} at ${nextShift.start}"
                                else "Outside scheduled break windows",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = onNavigateToSell,
                            modifier = Modifier.height(36.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Text("Sell", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 4 Stat Cards Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TodayStatCard(
                        modifier = Modifier.weight(1f),
                        label = "Sales today",
                        value = CurrencyUtils.formatKwacha(todayRevenue),
                        hint = "${todaySales.size} transactions",
                        color = MaterialTheme.colorScheme.primaryContainer
                    )
                    TodayStatCard(
                        modifier = Modifier.weight(1f),
                        label = "Est. profit today",
                        value = CurrencyUtils.formatKwacha(todayNetProfit),
                        hint = "After stock & expenses",
                        color = MaterialTheme.colorScheme.secondaryContainer
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TodayStatCard(
                        modifier = Modifier.weight(1f),
                        label = "Owed on Book",
                        value = CurrencyUtils.formatKwacha(totalOwedOnBook),
                        hint = if (overdueCount > 0) "$overdueCount overdue" else "All up to date",
                        color = if (overdueCount > 0) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surfaceVariant
                    )
                    TodayStatCard(
                        modifier = Modifier.weight(1f),
                        label = "Low stock",
                        value = "${lowStockItems.size}",
                        hint = "Items to restock",
                        color = if (lowStockItems.isNotEmpty()) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }

        // 7-Day Sales Trend Bar Chart
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Last 7 Days Sales Trend", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().height(110.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        last7Days.forEach { (day, amt) ->
                            val heightFraction = (amt / max7DaySales).toFloat().coerceIn(0.08f, 1f)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(22.dp)
                                        .fillMaxWidth(heightFraction)
                                        .height((100 * heightFraction).dp)
                                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(day, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        // Restock Soon Banner
        if (lowStockItems.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Restock Soon", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "View all",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { onNavigateToProducts() }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        lowStockItems.take(4).forEach { p ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(p.name, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                }
                                Text("${p.stock} left", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD97706))
                            }
                        }
                    }
                }
            }
        }

        // Quick Actions Grid (Add product, Record payment, Add expense, Reports)
        item {
            Text("Quick Actions", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickActionCard(modifier = Modifier.weight(1f), icon = Icons.Default.PointOfSale, label = "Sell", onClick = onNavigateToSell)
                QuickActionCard(modifier = Modifier.weight(1f), icon = Icons.Default.Inventory2, label = "Stock", onClick = onNavigateToProducts)
                QuickActionCard(modifier = Modifier.weight(1f), icon = Icons.Default.Book, label = "The Book", onClick = onNavigateToBook)
                QuickActionCard(modifier = Modifier.weight(1f), icon = Icons.Default.ReceiptLong, label = "Ledger", onClick = onNavigateToLedger)
            }
        }
    }
}

@Composable
private fun TodayStatCard(modifier: Modifier, label: String, value: String, hint: String, color: Color) {
    Card(modifier = modifier, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = color)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(hint, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun QuickActionCard(modifier: Modifier, icon: ImageVector, label: String, onClick: () -> Unit) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}
