package com.example.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Audit
import com.example.model.CurrencyUtils
import com.example.model.Expense
import com.example.model.Sale
import com.example.model.tenderLabel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(
    sales: List<Sale>,
    expenses: List<Expense>,
    auditEntries: List<Audit>,
    onVoidSale: (saleId: String, reason: String) -> Unit,
    onRefundSale: (saleId: String, amount: Double, reason: String) -> Unit,
    onAddSaleNote: (saleId: String, note: String) -> Unit,
    onAddExpense: (Expense) -> Unit,
    onVoidExpense: (expenseId: String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Sales, 1: Expenses, 2: Audit
    var searchQuery by remember { mutableStateOf("") }

    // Dialog states
    var voidingSale by remember { mutableStateOf<Sale?>(null) }
    var refundingSale by remember { mutableStateOf<Sale?>(null) }
    var notingSale by remember { mutableStateOf<Sale?>(null) }
    var showAddExpenseDialog by remember { mutableStateOf(false) }

    val filteredSales = remember(sales, searchQuery) {
        if (searchQuery.isBlank()) sales
        else sales.filter { s ->
            s.id.contains(searchQuery, ignoreCase = true) ||
                    s.items.any { it.name.contains(searchQuery, ignoreCase = true) }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Ledger & History",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Every sale, void, refund, expense and full audit trail",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Sales (${sales.size})") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Expenses (${expenses.size})") }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Audit Trail") }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            when (selectedTab) {
                // TAB 0: SALES
                0 -> {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search item name or receipt #") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("ledger_sales_search"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (filteredSales.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "No sales recorded yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(bottom = 88.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filteredSales, key = { it.id }) { sale ->
                                val sdf = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.US) }
                                val formattedDate = sdf.format(Date(sale.at))

                                Card(
                                    modifier = Modifier.fillMaxWidth().testTag("sale_card_${sale.id}"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (sale.reversed) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                        else MaterialTheme.colorScheme.surface
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "#${sale.id.take(6).uppercase()}",
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.titleSmall
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = formattedDate,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .clip(CircleShape)
                                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(text = tenderLabel(sale.tender), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                                }
                                                if (sale.shift != null) {
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(text = "· ${sale.shift}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }

                                            Text(
                                                text = CurrencyUtils.formatKwacha(sale.total),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                textDecoration = if (sale.reversed) TextDecoration.LineThrough else TextDecoration.None,
                                                color = if (sale.reversed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Text(
                                            text = sale.itemsSummary.ifBlank { "Sale receipt" },
                                            style = MaterialTheme.typography.bodyMedium,
                                            textDecoration = if (sale.reversed) TextDecoration.LineThrough else TextDecoration.None
                                        )

                                        if (sale.reversed) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Reversed${if (!sale.reversedBy.isNullOrBlank()) " by ${sale.reversedBy}" else ""}: ${sale.voidReason ?: "Voided"}",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.error,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }

                                        if ((sale.refunded ?: 0.0) > 0.0) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Refunded ${CurrencyUtils.formatKwacha(sale.refunded!!)}",
                                                fontSize = 12.sp,
                                                color = Color(0xFFD97706),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }

                                        if (!sale.note.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Note: ${sale.note}",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.tertiary
                                            )
                                        }

                                        // Actions for non-reversed sales
                                        if (!sale.reversed) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                IconButton(
                                                    onClick = { notingSale = sale },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(Icons.Default.EditNote, contentDescription = "Add Note", modifier = Modifier.size(18.dp))
                                                }

                                                val canRefund = (sale.refunded ?: 0.0) < sale.total
                                                IconButton(
                                                    onClick = { refundingSale = sale },
                                                    enabled = canRefund,
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        Icons.AutoMirrored.Filled.Undo,
                                                        contentDescription = "Refund",
                                                        tint = if (canRefund) Color(0xFFD97706) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }

                                                IconButton(
                                                    onClick = { voidingSale = sale },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(Icons.Default.Block, contentDescription = "Void Sale", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // TAB 1: EXPENSES
                1 -> {
                    if (expenses.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "No expenses logged. Tap + to record shop outlays.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(bottom = 88.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(expenses, key = { it.id }) { expense ->
                                val sdf = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.US) }
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (expense.reversed) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(text = sdf.format(Date(expense.at)), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Box(
                                                    modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(text = expense.category, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                                }
                                                if (expense.reversed) {
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(text = "REVERSED", fontSize = 10.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = expense.description,
                                                fontWeight = FontWeight.SemiBold,
                                                textDecoration = if (expense.reversed) TextDecoration.LineThrough else TextDecoration.None
                                            )
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = CurrencyUtils.formatKwacha(expense.amount),
                                                fontWeight = FontWeight.Bold,
                                                color = if (expense.reversed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                                                textDecoration = if (expense.reversed) TextDecoration.LineThrough else TextDecoration.None
                                            )
                                            if (!expense.reversed) {
                                                IconButton(onClick = { onVoidExpense(expense.id) }) {
                                                    Icon(Icons.Default.Block, contentDescription = "Reverse Expense", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // TAB 2: AUDIT TRAIL
                2 -> {
                    if (auditEntries.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "Audit trail is empty.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(bottom = 88.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(auditEntries, key = { it.id }) { a ->
                                val sdf = remember { SimpleDateFormat("dd MMM HH:mm:ss", Locale.US) }
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text(text = sdf.format(Date(a.at)), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(105.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = "${a.action} · ${a.actor}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text(text = a.detail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Add Expense FAB on Expenses tab
        if (selectedTab == 1) {
            FloatingActionButton(
                onClick = { showAddExpenseDialog = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).testTag("add_expense_fab"),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Expense")
            }
        }
    }

    // Void Sale Dialog
    if (voidingSale != null) {
        val target = voidingSale!!
        var reasonText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { voidingSale = null },
            icon = { Icon(Icons.Default.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Void Sale #${target.id.take(6).uppercase()} (${CurrencyUtils.formatKwacha(target.total)})?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Stock goes back on the shelf. The sale stays in history, marked void.")
                    OutlinedTextField(
                        value = reasonText,
                        onValueChange = { reasonText = it },
                        label = { Text("Reason (required)") },
                        placeholder = { Text("e.g. Rang up twice, Wrong item") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (reasonText.isNotBlank()) {
                            onVoidSale(target.id, reasonText.trim())
                            voidingSale = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Confirm Void")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { voidingSale = null }) { Text("Cancel") }
            }
        )
    }

    // Refund Dialog
    if (refundingSale != null) {
        val target = refundingSale!!
        val maxRefund = target.total - (target.refunded ?: 0.0)
        var refundAmtText by remember { mutableStateOf("") }
        var refundReason by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { refundingSale = null },
            title = { Text("Record Refund #${target.id.take(6).uppercase()}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Max refundable: ${CurrencyUtils.formatKwacha(maxRefund)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = refundAmtText,
                        onValueChange = { refundAmtText = it },
                        label = { Text("Refund Amount (K)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = refundReason,
                        onValueChange = { refundReason = it },
                        label = { Text("Reason for refund") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val a = CurrencyUtils.parseKwachaDouble(refundAmtText)
                        if (a in 0.01..maxRefund) {
                            onRefundSale(target.id, a, refundReason.trim())
                            refundingSale = null
                        }
                    }
                ) {
                    Text("Record Refund")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { refundingSale = null }) { Text("Cancel") }
            }
        )
    }

    // Clarification Note Dialog
    if (notingSale != null) {
        val target = notingSale!!
        var noteText by remember { mutableStateOf(target.note ?: "") }
        AlertDialog(
            onDismissRequest = { notingSale = null },
            title = { Text("Clarification Note") },
            text = {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Note") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddSaleNote(target.id, noteText.trim())
                        notingSale = null
                    }
                ) {
                    Text("Save Note")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { notingSale = null }) { Text("Cancel") }
            }
        )
    }

    // Add Expense Dialog
    if (showAddExpenseDialog) {
        var desc by remember { mutableStateOf("") }
        var category by remember { mutableStateOf("Stock purchase") }
        var amountText by remember { mutableStateOf("") }
        var isMenuOpen by remember { mutableStateOf(false) }

        val expCats = listOf("Stock purchase", "Transport", "Utilities", "Wages", "Rent / levy", "Other")

        AlertDialog(
            onDismissRequest = { showAddExpenseDialog = false },
            title = { Text("Add Expense", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = desc,
                        onValueChange = { desc = it },
                        label = { Text("Description") },
                        placeholder = { Text("e.g. Bread from Soweto Market") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    ExposedDropdownMenuBox(
                        expanded = isMenuOpen,
                        onExpandedChange = { isMenuOpen = it }
                    ) {
                        OutlinedTextField(
                            value = category,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Category") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isMenuOpen) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(
                            expanded = isMenuOpen,
                            onDismissRequest = { isMenuOpen = false }
                        ) {
                            expCats.forEach { c ->
                                DropdownMenuItem(
                                    text = { Text(c) },
                                    onClick = {
                                        category = c
                                        isMenuOpen = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Amount (K)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val a = CurrencyUtils.parseKwachaDouble(amountText)
                        if (desc.isNotBlank() && a > 0) {
                            val exp = Expense(
                                id = UUID.randomUUID().toString(),
                                at = System.currentTimeMillis(),
                                category = category,
                                amount = a,
                                description = desc.trim()
                            )
                            onAddExpense(exp)
                            showAddExpenseDialog = false
                        }
                    }
                ) {
                    Text("Save Expense")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAddExpenseDialog = false }) { Text("Cancel") }
            }
        )
    }
}
