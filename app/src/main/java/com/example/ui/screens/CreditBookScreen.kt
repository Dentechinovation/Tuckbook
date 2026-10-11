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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Creditor
import com.example.model.CurrencyUtils
import com.example.model.Repayment
import com.example.model.Sale
import com.example.model.tenderLabel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditBookScreen(
    creditors: List<Creditor>,
    sales: List<Sale>,
    repayments: List<Repayment>,
    onAddCreditor: (Creditor) -> Unit,
    onRecordRepayment: (Repayment, String) -> Unit,
    onSendReminder: (Creditor, Double) -> Unit
) {
    var selectedFilter by remember { mutableStateOf("Everyone") } // "Everyone", "Students", "Teachers", "Overdue"
    var showAddDialog by remember { mutableStateOf(false) }
    var payingCreditor by remember { mutableStateOf<Creditor?>(null) }
    var expandedCreditorId by remember { mutableStateOf<String?>(null) }

    // Helper functions for balance & overdue calculation
    fun getBalance(creditorId: String): Double {
        val owed = sales.filter { it.creditorId == creditorId && !it.reversed }.sumOf { it.total }
        val paid = repayments.filter { it.creditorId == creditorId }.sumOf { it.amount }
        return (owed - paid).coerceAtLeast(0.0)
    }

    fun isOverdue(creditor: Creditor): Boolean {
        val bal = getBalance(creditor.id)
        if (bal <= 0.0) return false
        val creditSales = sales.filter { it.creditorId == creditor.id && !it.reversed }.sortedBy { it.at }
        val oldest = creditSales.firstOrNull() ?: return false
        val overdueThresholdMs = creditor.dueDays * 86_400_000L
        return (System.currentTimeMillis() - oldest.at) > overdueThresholdMs
    }

    val totalOwed = creditors.sumOf { getBalance(it.id) }
    val overdueCount = creditors.count { isOverdue(it) }

    val filteredList = creditors.filter { c ->
        when (selectedFilter) {
            "Students" -> c.kind == "student"
            "Teachers" -> c.kind == "teacher"
            "Overdue" -> isOverdue(c)
            else -> true
        }
    }.sortedByDescending { getBalance(it.id) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Header summary
            Text(
                text = "Credit Book",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Track student & teacher credit balances and repayments",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Stat Cards Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Total Owed",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = CurrencyUtils.formatKwacha(totalOwed),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (overdueCount > 0) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Overdue",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (overdueCount > 0) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "$overdueCount people",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (overdueCount > 0) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Chips
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(listOf("Everyone", "Students", "Teachers", "Overdue")) { f ->
                    FilterChip(
                        selected = selectedFilter == f,
                        onClick = { selectedFilter = f },
                        label = { Text(f) },
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Creditors List
            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Book,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Nobody on the Book in this category",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredList, key = { it.id }) { creditor ->
                        val bal = getBalance(creditor.id)
                        val overdue = isOverdue(creditor)
                        val isExpanded = expandedCreditorId == creditor.id

                        val personSales = sales.filter { it.creditorId == creditor.id && !it.reversed }
                        val personRepayments = repayments.filter { it.creditorId == creditor.id }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("creditor_card_${creditor.id}"),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            expandedCreditorId = if (isExpanded) null else creditor.id
                                        },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = creditor.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (overdue) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .clip(CircleShape)
                                                        .background(MaterialTheme.colorScheme.errorContainer)
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "OVERDUE",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }

                                        Text(
                                            text = "${creditor.kind.replaceFirstChar { it.uppercase() }} · ${creditor.detail} · due in ${creditor.dueDays}d",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = if (bal > 0) CurrencyUtils.formatKwacha(bal) else "Paid up",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = if (bal > 0) (if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) else MaterialTheme.colorScheme.secondary
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Button(
                                            onClick = { payingCreditor = creditor },
                                            enabled = bal > 0,
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.height(34.dp).testTag("repay_button_${creditor.id}")
                                        ) {
                                            Text("Repay", fontSize = 12.sp)
                                        }

                                        Icon(
                                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = "Expand",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(start = 4.dp)
                                        )
                                    }
                                }

                                // Expanded transaction history
                                if (isExpanded) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                            .padding(10.dp)
                                    ) {
                                        if (creditor.phone.isNotBlank()) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Phone: ${creditor.phone}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                if (overdue) {
                                                    OutlinedButton(
                                                        onClick = { onSendReminder(creditor, bal) },
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                        modifier = Modifier.height(28.dp)
                                                    ) {
                                                        Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(14.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Remind SMS", fontSize = 11.sp)
                                                    }
                                                }
                                            }
                                        }

                                        Text(text = "Transaction History:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                        Spacer(modifier = Modifier.height(4.dp))

                                        val allEvents = (personSales.map { s -> "sale" to (s.at to s) } +
                                                personRepayments.map { r -> "repayment" to (r.at to r) })
                                            .sortedByDescending { it.second.first }

                                        if (allEvents.isEmpty()) {
                                            Text(text = "No history recorded yet.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        } else {
                                            allEvents.take(10).forEach { event ->
                                                val sdf = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.US) }
                                                val dateStr = sdf.format(Date(event.second.first))
                                                if (event.first == "sale") {
                                                    val s = event.second.second as Sale
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text(text = "$dateStr • ${s.itemsSummary.ifBlank { "Credit sale" }}", fontSize = 11.sp, modifier = Modifier.weight(1f))
                                                        Text(text = "+${CurrencyUtils.formatKwacha(s.total)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                                    }
                                                } else {
                                                    val r = event.second.second as Repayment
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text(text = "$dateStr • Repayment (${tenderLabel(r.tender)})", fontSize = 11.sp, modifier = Modifier.weight(1f))
                                                        Text(text = "−${CurrencyUtils.formatKwacha(r.amount)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Add Person FAB
        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("add_creditor_fab"),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Person")
        }
    }

    // Add Person Dialog
    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var kind by remember { mutableStateOf("student") }
        var detail by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var dueDaysText by remember { mutableStateOf("7") }
        var isKindMenuOpen by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add to Credit Book", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Full Name") },
                        placeholder = { Text("e.g. Mr. Chanda / Natasha") },
                        modifier = Modifier.fillMaxWidth().testTag("creditor_name_input"),
                        singleLine = true
                    )

                    ExposedDropdownMenuBox(
                        expanded = isKindMenuOpen,
                        onExpandedChange = { isKindMenuOpen = it }
                    ) {
                        OutlinedTextField(
                            value = if (kind == "student") "Student (7 days)" else "Teacher (30 days)",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Type") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isKindMenuOpen) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(
                            expanded = isKindMenuOpen,
                            onDismissRequest = { isKindMenuOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Student (7 days default)") },
                                onClick = {
                                    kind = "student"
                                    dueDaysText = "7"
                                    isKindMenuOpen = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Teacher (30 days default)") },
                                onClick = {
                                    kind = "teacher"
                                    dueDaysText = "30"
                                    isKindMenuOpen = false
                                }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = detail,
                        onValueChange = { detail = it },
                        label = { Text(if (kind == "student") "Grade / Class" else "Department") },
                        placeholder = { Text(if (kind == "student") "Grade 10B" else "Science Dept") },
                        modifier = Modifier.fillMaxWidth().testTag("creditor_detail_input"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Phone Number") },
                        placeholder = { Text("+260 97...") },
                        modifier = Modifier.fillMaxWidth().testTag("creditor_phone_input"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = dueDaysText,
                        onValueChange = { dueDaysText = it },
                        label = { Text("Pay within (days)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            val c = Creditor(
                                id = UUID.randomUUID().toString(),
                                name = name.trim(),
                                kind = kind,
                                detail = detail.trim(),
                                phone = phone.trim(),
                                dueDays = dueDaysText.toIntOrNull() ?: (if (kind == "teacher") 30 else 7)
                            )
                            onAddCreditor(c)
                            showAddDialog = false
                        }
                    },
                    modifier = Modifier.testTag("save_creditor_button")
                ) {
                    Text("Save to Book")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Record Repayment Dialog
    if (payingCreditor != null) {
        val target = payingCreditor!!
        val balance = getBalance(target.id)
        var amountText by remember { mutableStateOf("") }
        var tender by remember { mutableStateOf("cash") }

        AlertDialog(
            onDismissRequest = { payingCreditor = null },
            title = { Text("Repayment · ${target.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Owes: ${CurrencyUtils.formatKwacha(balance)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Amount in Kwacha (K)") },
                        placeholder = { Text("e.g. 50.00") },
                        modifier = Modifier.fillMaxWidth().testTag("repay_amount_input"),
                        singleLine = true
                    )

                    // Quick shortcut buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { amountText = String.format(Locale.US, "%.2f", balance) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Full (${CurrencyUtils.formatKwacha(balance)})", fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = { amountText = String.format(Locale.US, "%.2f", balance / 2.0) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Half", fontSize = 11.sp)
                        }
                    }

                    Text("Payment Method:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("cash" to "Cash", "airtel" to "Airtel", "mtn" to "MTN", "zamtel" to "Zamtel").forEach { (code, label) ->
                            FilterChip(
                                selected = tender == code,
                                onClick = { tender = code },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = CurrencyUtils.parseKwachaDouble(amountText)
                        if (amt > 0) {
                            val r = Repayment(
                                id = UUID.randomUUID().toString(),
                                creditorId = target.id,
                                amount = amt,
                                at = System.currentTimeMillis(),
                                tender = tender
                            )
                            onRecordRepayment(r, target.name)
                            payingCreditor = null
                        }
                    },
                    modifier = Modifier.testTag("confirm_repay_button")
                ) {
                    Text("Record Payment")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { payingCreditor = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
