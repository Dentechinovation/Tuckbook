package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Creditor
import com.example.model.CurrencyUtils
import com.example.model.Product
import com.example.model.Sale
import com.example.model.SaleItem
import com.example.model.Settings
import com.example.model.Shift
import com.example.model.tenderLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TillScreen(
    products: List<Product>,
    creditors: List<Creditor>,
    activeShiftName: String?,
    settings: Settings,
    onCompleteSale: suspend (Sale) -> Unit,
    onUndoLastSale: suspend (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var isRushMode by remember { mutableStateOf(settings.rushMode) }
    var enforceStock by remember { mutableStateOf(settings.enforceStock) }

    // Cart state: Map of Product ID to Quantity
    val cart = remember { mutableStateMapOf<String, Int>() }
    var discountText by remember { mutableStateOf("") }

    // Dialog & Sheet States
    var showCartSheet by remember { mutableStateOf(false) }
    var showCashDialog by remember { mutableStateOf(false) }
    var showCreditDialog by remember { mutableStateOf(false) }
    var receivedAmountText by remember { mutableStateOf("") }
    var selectedCreditorId by remember { mutableStateOf("") }

    var isSavingSale by remember { mutableStateOf(false) }
    var lastCompletedSale by remember { mutableStateOf<Sale?>(null) }
    var showReceiptDialog by remember { mutableStateOf(false) }

    val categories = remember(products) {
        listOf("All") + products.map { it.category }.distinct().sorted()
    }

    val filteredProducts = remember(products, searchQuery, selectedCategory) {
        products.filter { p ->
            (selectedCategory == "All" || p.category.equals(selectedCategory, ignoreCase = true)) &&
                    (searchQuery.isBlank() || p.name.contains(searchQuery, ignoreCase = true) || (p.barcode != null && p.barcode.contains(searchQuery.trim())))
        }
    }

    // Cart lines & Totals
    val cartLines = cart.mapNotNull { (prodId, qty) ->
        val prod = products.find { it.id == prodId }
        if (prod != null && qty > 0) prod to qty else null
    }

    val subtotal = cartLines.sumOf { (prod, qty) -> prod.price * qty }
    val discount = CurrencyUtils.parseKwachaDouble(discountText)
    val total = (subtotal - discount).coerceAtLeast(0.0)
    val totalCount = cartLines.sumOf { it.second }

    // Add function
    fun addProduct(prod: Product, delta: Int) {
        val current = cart[prod.id] ?: 0
        val target = current + delta
        if (delta > 0 && enforceStock && prod.stock <= current) {
            Toast.makeText(context, "${prod.name} is out of stock / at stock limit", Toast.LENGTH_SHORT).show()
            return
        }
        if (target <= 0) cart.remove(prod.id)
        else {
            val finalQty = if (enforceStock) target.coerceAtMost(prod.stock) else target
            cart[prod.id] = finalQty
        }
    }

    // Checkout helper
    fun checkout(tender: String, creditorId: String? = null) {
        if (isSavingSale || totalCount <= 0) return
        isSavingSale = true
        coroutineScope.launch {
            val saleItems = cartLines.map { (prod, qty) ->
                SaleItem(
                    productId = prod.id,
                    name = prod.name,
                    qty = qty,
                    price = prod.price,
                    cost = prod.cost
                )
            }

            val sale = Sale(
                id = UUID.randomUUID().toString(),
                at = System.currentTimeMillis(),
                items = saleItems,
                total = total,
                tender = tender,
                creditorId = creditorId,
                shift = activeShiftName,
                discount = if (discount > 0) discount else null,
                cashier = settings.cashier
            )

            onCompleteSale(sale)
            lastCompletedSale = sale
            cart.clear()
            discountText = ""
            showCartSheet = false
            showCashDialog = false
            showCreditDialog = false
            receivedAmountText = ""
            selectedCreditorId = ""
            showReceiptDialog = true

            delay(600)
            isSavingSale = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Search Bar & Controls
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search name or barcode, Enter to add...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("till_search_input"),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Toggles Row: Rush Mode & Limit to Stock
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = isRushMode,
                        onCheckedChange = { isRushMode = it }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(Icons.Default.FlashOn, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(16.dp))
                    Text("Rush Mode (+1/+5)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = enforceStock,
                        onCheckedChange = { enforceStock = it }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Limit to stock", fontSize = 12.sp)
                }
            }

            // Category Chips Row
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = { Text(category) },
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }

            // Products Grid
            if (filteredProducts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No products found in this category",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(bottom = 88.dp, top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(filteredProducts, key = { it.id }) { product ->
                        val qtyInCart = cart[product.id] ?: 0
                        val isOut = enforceStock && product.stock <= 0
                        val isAtMax = enforceStock && qtyInCart >= product.stock && !isOut

                        val stockColor = when {
                            product.stock <= 0 -> MaterialTheme.colorScheme.error
                            product.stock <= product.lowAt -> Color(0xFFD97706)
                            else -> MaterialTheme.colorScheme.secondary
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isOut) { addProduct(product, 1) }
                                .testTag("product_card_${product.id}"),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (qtyInCart > 0) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                else MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = product.category,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(stockColor.copy(alpha = 0.15f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (product.stock <= 0) "Out" else "${product.stock} left",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = stockColor,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = product.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = CurrencyUtils.formatKwacha(product.price),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Quantity Controls
                                if (qtyInCart > 0 || isRushMode) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = { addProduct(product, -1) },
                                                modifier = Modifier.size(30.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
                                            ) {
                                                Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(16.dp))
                                            }

                                            Text(
                                                text = "$qtyInCart",
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp)
                                            )

                                            IconButton(
                                                onClick = { addProduct(product, 1) },
                                                enabled = !isAtMax,
                                                modifier = Modifier.size(30.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = "Increase", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                                            }
                                        }

                                        if (isRushMode) {
                                            OutlinedButton(
                                                onClick = { addProduct(product, 5) },
                                                enabled = !isAtMax,
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                                modifier = Modifier.height(30.dp),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text("+5", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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

        // Sticky Bottom Cart Bar
        if (totalCount > 0) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(18.dp),
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.clickable { showCartSheet = true }.weight(1f)
                    ) {
                        Text(
                            text = "$totalCount items in cart",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = CurrencyUtils.formatKwacha(total),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Row {
                        OutlinedButton(
                            onClick = { cart.clear(); discountText = "" },
                            modifier = Modifier.padding(end = 8.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Clear")
                        }

                        Button(
                            onClick = { showCartSheet = true },
                            modifier = Modifier.testTag("view_cart_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Checkout")
                        }
                    }
                }
            }
        }
    }

    // Checkout Bottom Sheet
    if (showCartSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCartSheet = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Current Sale", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("$totalCount items", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Cart lines
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((prod, qty) in cartLines) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(prod.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${CurrencyUtils.formatKwacha(prod.price)} x $qty", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { addProduct(prod, -1) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Remove, contentDescription = "Decrease")
                                }
                                Text("$qty", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp))
                                IconButton(onClick = { addProduct(prod, 1) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Add, contentDescription = "Increase")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(CurrencyUtils.formatKwacha(prod.price * qty), fontWeight = FontWeight.Bold, modifier = Modifier.width(72.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = discountText,
                    onValueChange = { discountText = it },
                    label = { Text("Discount in Kwacha (K)") },
                    placeholder = { Text("e.g. 5.00") },
                    modifier = Modifier.fillMaxWidth().testTag("cart_discount_input"),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("TOTAL DUE", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = CurrencyUtils.formatKwacha(total),
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 1. Big Cash Button
                Button(
                    onClick = { showCashDialog = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("pay_cash_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Payments, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pay Cash (${CurrencyUtils.formatKwacha(total)})", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 2. Mobile Money Buttons (Airtel, MTN, Zamtel)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("airtel" to "Airtel", "mtn" to "MTN", "zamtel" to "Zamtel").forEach { (code, label) ->
                        OutlinedButton(
                            onClick = { checkout(code) },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Smartphone, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 3. Put on Book (Credit)
                OutlinedButton(
                    onClick = { showCreditDialog = true },
                    modifier = Modifier.fillMaxWidth().height(44.dp).testTag("pay_credit_button"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Put on Book (Credit)", fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Cash Checkout Dialog
    if (showCashDialog) {
        val recv = CurrencyUtils.parseKwachaDouble(receivedAmountText)
        val change = (recv - total).coerceAtLeast(0.0)

        AlertDialog(
            onDismissRequest = { showCashDialog = false },
            title = { Text("Cash Payment · ${CurrencyUtils.formatKwacha(total)}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = receivedAmountText,
                        onValueChange = { receivedAmountText = it },
                        label = { Text("Amount Received (K)") },
                        placeholder = { Text("e.g. 50") },
                        modifier = Modifier.fillMaxWidth().testTag("cash_received_input"),
                        singleLine = true
                    )

                    // Shortcut buttons: Exact, K10, K20, K50, K100, K200
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            OutlinedButton(onClick = { receivedAmountText = String.format(Locale.US, "%.2f", total) }) {
                                Text("Exact", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        items(listOf(10, 20, 50, 100, 200)) { note ->
                            OutlinedButton(onClick = { receivedAmountText = "$note" }) {
                                Text("K$note", fontSize = 11.sp)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Change Due", fontWeight = FontWeight.SemiBold)
                        Text(CurrencyUtils.formatKwacha(change), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { checkout("cash") },
                    enabled = recv >= total || receivedAmountText.isBlank(),
                    modifier = Modifier.testTag("confirm_cash_sale_button")
                ) {
                    Text("Complete Sale")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showCashDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Put on Book (Credit) Dialog
    if (showCreditDialog) {
        var isMenuOpen by remember { mutableStateOf(false) }
        val selectedPerson = creditors.find { it.id == selectedCreditorId }

        AlertDialog(
            onDismissRequest = { showCreditDialog = false },
            title = { Text("Put ${CurrencyUtils.formatKwacha(total)} on Book", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (creditors.isEmpty()) {
                        Text("No students or teachers on the Book yet. Add them in the Credit Book screen.", color = MaterialTheme.colorScheme.error)
                    } else {
                        Text("Select student or teacher to charge:", fontSize = 12.sp)

                        ExposedDropdownMenuBox(
                            expanded = isMenuOpen,
                            onExpandedChange = { isMenuOpen = it }
                        ) {
                            OutlinedTextField(
                                value = selectedPerson?.let { "${it.name} (${it.detail})" } ?: "Choose student / teacher",
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isMenuOpen) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            )
                            ExposedDropdownMenu(
                                expanded = isMenuOpen,
                                onDismissRequest = { isMenuOpen = false }
                            ) {
                                creditors.forEach { c ->
                                    DropdownMenuItem(
                                        text = { Text("${c.name} · ${c.detail} (${c.kind})") },
                                        onClick = {
                                            selectedCreditorId = c.id
                                            isMenuOpen = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { checkout("credit", selectedCreditorId) },
                    enabled = selectedCreditorId.isNotBlank(),
                    modifier = Modifier.testTag("confirm_credit_sale_button")
                ) {
                    Text("Record Credit Sale")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showCreditDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Receipt Summary with Undo option
    if (showReceiptDialog && lastCompletedSale != null) {
        val sale = lastCompletedSale!!
        AlertDialog(
            onDismissRequest = { showReceiptDialog = false },
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp)) },
            title = { Text("Sale Saved!", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Total: ${CurrencyUtils.formatKwacha(sale.total)}", fontWeight = FontWeight.Bold)
                    Text("Tender: ${tenderLabel(sale.tender)}")
                    if (sale.cashier != null) Text("Cashier: ${sale.cashier}", fontSize = 12.sp)
                    Text("Items: ${sale.itemsSummary}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Button(onClick = { showReceiptDialog = false }) { Text("New Sale") }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            onUndoLastSale(sale.id)
                            showReceiptDialog = false
                        }
                    }
                ) {
                    Icon(Icons.Default.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Undo Sale")
                }
            }
        )
    }
}
