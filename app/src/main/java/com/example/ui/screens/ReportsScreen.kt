package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Closing
import com.example.model.CurrencyUtils
import com.example.model.Expense
import com.example.model.Sale
import com.example.model.Settings
import com.example.model.Shift
import com.example.model.tenderLabel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Composable
fun ReportsScreen(
    sales: List<Sale>,
    expenses: List<Expense>,
    shifts: List<Shift>,
    closings: List<Closing>,
    settings: Settings,
    onSaveClosing: (Closing) -> Unit
) {
    val context = LocalContext.current
    var selectedPeriod by remember { mutableStateOf("day") } // "day", "week", "month"
    var selectedScope by remember { mutableStateOf("Whole day") }
    var showClosingDialog by remember { mutableStateOf(false) }

    val now = System.currentTimeMillis()
    val periodStartMs = remember(selectedPeriod, now) {
        val cal = java.util.Calendar.getInstance()
        when (selectedPeriod) {
            "week" -> { cal.add(java.util.Calendar.DAY_OF_YEAR, -7); cal.timeInMillis }
            "month" -> { cal.add(java.util.Calendar.DAY_OF_YEAR, -30); cal.timeInMillis }
            else -> { cal.set(java.util.Calendar.HOUR_OF_DAY, 0); cal.set(java.util.Calendar.MINUTE, 0); cal.timeInMillis }
        }
    }

    val liveSales = sales.filter { !it.reversed && it.at >= periodStartMs }
        .filter { selectedPeriod != "day" || selectedScope == "Whole day" || it.shift == selectedScope }
    val liveExpenses = expenses.filter { !it.reversed && it.at >= periodStartMs }

    val revenue = liveSales.sumOf { it.total - (it.refunded ?: 0.0) }
    val cogs = liveSales.sumOf { s -> s.items.sumOf { it.qty * it.cost } }
    val totalExpenses = liveExpenses.sumOf { it.amount }
    val estProfit = (revenue - cogs - totalExpenses)

    // Top items
    val itemMap = mutableMapOf<String, Pair<Int, Double>>()
    for (s in liveSales) {
        for (i in s.items) {
            val curr = itemMap.getOrDefault(i.name, 0 to 0.0)
            itemMap[i.name] = (curr.first + i.qty) to (curr.second + (i.qty * i.price))
        }
    }
    val topItems = itemMap.toList().sortedByDescending { it.second.second }.take(5)

    // Tender breakdown
    val tenders = listOf("cash", "airtel", "mtn", "zamtel", "credit")
    val byTender = tenders.map { t ->
        t to liveSales.filter { it.tender == t }.sumOf { it.total - (it.refunded ?: 0.0) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Reports & Shift Close",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Daily, weekly and monthly summaries and cash-up",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = { showClosingDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.testTag("open_close_shift_button")
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Close Shift")
                }
            }
        }

        // Period filter buttons
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("day" to "Today", "week" to "Last 7 days", "month" to "Last 30 days").forEach { (code, label) ->
                    FilterChip(
                        selected = selectedPeriod == code,
                        onClick = { selectedPeriod = code },
                        label = { Text(label) },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }

        // Shift scope buttons (when period is today)
        if (selectedPeriod == "day") {
            item {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val shiftNames = listOf("Whole day") + shifts.map { it.name }
                    items(shiftNames) { name ->
                        FilterChip(
                            selected = selectedScope == name,
                            onClick = { selectedScope = name },
                            label = { Text(name) },
                            shape = RoundedCornerShape(20.dp)
                        )
                    }
                }
            }
        }

        // Report Main Card with Tekzor Branding
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Tekzor Branding Header
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Tuck",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp
                                )
                                Text(
                                    text = "Book",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "by TEKZOR",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "Media House & Graphics",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(text = settings.shopName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(text = "Register: ${settings.cashier}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // 4 Stat Cards
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Revenue", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    Text(CurrencyUtils.formatKwacha(revenue), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    Text("${liveSales.size} sales", fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                                }
                            }

                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Cost of Goods", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(CurrencyUtils.formatKwacha(cogs), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text("Est. wholesale cost", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Expenses", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(CurrencyUtils.formatKwacha(totalExpenses), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text("${liveExpenses.size} outlays", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                }
                            }

                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Est. Net Profit", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                    Text(CurrencyUtils.formatKwacha(estProfit), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                    Text("After stock & costs", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Top Items & By Tender
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Top Items", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            if (topItems.isEmpty()) {
                                Text("No sales.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                topItems.forEach { (name, pair) ->
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("${pair.first}x $name", fontSize = 11.sp, modifier = Modifier.weight(1f))
                                        Text(CurrencyUtils.formatKwacha(pair.second), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text("By Payment", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            byTender.forEach { (tender, amt) ->
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(tenderLabel(tender), fontSize = 11.sp, modifier = Modifier.weight(1f))
                                    Text(CurrencyUtils.formatKwacha(amt), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Sharing actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { Toast.makeText(context, "PDF Report Exported (simulated)", Toast.LENGTH_SHORT).show() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("PDF", fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = { Toast.makeText(context, "Report sent via WhatsApp (simulated)", Toast.LENGTH_SHORT).show() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Message, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("WhatsApp", fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = { Toast.makeText(context, "Report sent via SMS (simulated)", Toast.LENGTH_SHORT).show() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Mail, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("SMS", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Shift Closings History
        if (closings.isNotEmpty()) {
            item {
                Text("Past Shift Closings (${closings.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }

            items(closings.take(10), key = { it.id }) { c ->
                val sdf = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.US) }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("${c.shift} · ${c.cashier}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(sdf.format(Date(c.at)) + if (c.note.isNotBlank()) " · ${c.note}" else "", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Text(
                            text = if (kotlin.math.abs(c.diff) < 0.01) "Balanced"
                            else (if (c.diff < 0) "Short " else "Over ") + CurrencyUtils.formatKwacha(kotlin.math.abs(c.diff)),
                            fontWeight = FontWeight.Bold,
                            color = if (kotlin.math.abs(c.diff) < 0.01) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }

    // Close Shift Dialog
    if (showClosingDialog) {
        val tendersList = listOf("cash", "airtel", "mtn", "zamtel")
        val expected = tendersList.associateWith { t ->
            liveSales.filter { it.tender == t }.sumOf { it.total - (it.refunded ?: 0.0) }
        }

        val counted = remember { mutableStateMapOf<String, String>() }
        var closeNote by remember { mutableStateOf("") }

        val diffs = tendersList.associateWith { t ->
            val c = counted[t]?.toDoubleOrNull() ?: 0.0
            val exp = expected[t] ?: 0.0
            c - exp
        }
        val totalDiff = diffs.values.sum()

        AlertDialog(
            onDismissRequest = { showClosingDialog = false },
            title = { Text("Close Shift · $selectedScope", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Count physical cash and mobile balances, then compare with recorded sales.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    tendersList.forEach { t ->
                        val exp = expected[t] ?: 0.0
                        val cntStr = counted[t] ?: ""
                        val diff = diffs[t] ?: 0.0
                        val hasEntered = cntStr.isNotBlank()

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1.2f)) {
                                Text(tenderLabel(t), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                Text("Recorded ${CurrencyUtils.formatKwacha(exp)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            OutlinedTextField(
                                value = cntStr,
                                onValueChange = { counted[t] = it },
                                placeholder = { Text("Counted") },
                                modifier = Modifier.weight(1f).height(46.dp),
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            Text(
                                text = if (!hasEntered) "—" else if (kotlin.math.abs(diff) < 0.01) "Matches" else (if (diff < 0) "−" else "+") + CurrencyUtils.formatKwacha(kotlin.math.abs(diff)),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (!hasEntered) MaterialTheme.colorScheme.onSurfaceVariant else if (kotlin.math.abs(diff) < 0.01) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.width(65.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Balance status pill
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (kotlin.math.abs(totalDiff) < 0.01) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.errorContainer,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (kotlin.math.abs(totalDiff) < 0.01) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (kotlin.math.abs(totalDiff) < 0.01) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (kotlin.math.abs(totalDiff) < 0.01) "Everything balances"
                            else "Difference: " + (if (totalDiff < 0) "Short " else "Over ") + CurrencyUtils.formatKwacha(kotlin.math.abs(totalDiff)),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (kotlin.math.abs(totalDiff) < 0.01) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer
                        )
                    }

                    OutlinedTextField(
                        value = closeNote,
                        onValueChange = { closeNote = it },
                        label = { Text("Explain any difference (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val countedMap = tendersList.associateWith { t -> counted[t]?.toDoubleOrNull() ?: 0.0 }
                        val closing = Closing(
                            id = UUID.randomUUID().toString(),
                            at = System.currentTimeMillis(),
                            shift = selectedScope,
                            cashier = settings.cashier,
                            expected = expected,
                            counted = countedMap,
                            diff = totalDiff,
                            note = closeNote.trim()
                        )
                        onSaveClosing(closing)
                        showClosingDialog = false
                    }
                ) {
                    Text("Confirm & Close Shift")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClosingDialog = false }) { Text("Cancel") }
            }
        )
    }
}
