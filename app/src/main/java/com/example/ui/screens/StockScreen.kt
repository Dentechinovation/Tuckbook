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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import java.util.Locale
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.model.CurrencyUtils
import com.example.model.Product
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockScreen(
    products: List<Product>,
    onSaveProduct: (Product) -> Unit,
    onDeleteProduct: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<Product?>(null) }
    var deletingProduct by remember { mutableStateOf<Product?>(null) }
    var newCategoryInput by remember { mutableStateOf("") }

    val defaultCategories = listOf("Drinks", "Fresh Food", "Snacks", "Bakery", "Stationery")
    val allCategories = remember(products) {
        (listOf("All") + (defaultCategories + products.map { it.category }).distinct()).distinct()
    }

    val filtered = remember(products, searchQuery, selectedCategory) {
        products.filter { p ->
            (selectedCategory == "All" || p.category.equals(selectedCategory, ignoreCase = true)) &&
                    (searchQuery.isBlank() || p.name.contains(searchQuery, ignoreCase = true) || (p.barcode != null && p.barcode.contains(searchQuery.trim())))
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
                text = "Products & Inventory",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${products.size} products across ${allCategories.size - 1} categories",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Search Bar & New Category Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search name or barcode...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    modifier = Modifier.weight(1f).testTag("product_search_input"),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Category Chips Row
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(allCategories) { category ->
                    val count = if (category == "All") products.size else products.count { it.category.equals(category, ignoreCase = true) }
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = { Text("$category ($count)") },
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }

            // Products List
            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No products found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtered, key = { it.id }) { product ->
                        val isLow = product.stock <= product.lowAt
                        val stockColor = when {
                            product.stock <= 0 -> MaterialTheme.colorScheme.error
                            isLow -> Color(0xFFD97706)
                            else -> MaterialTheme.colorScheme.secondary
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("product_row_${product.id}"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = product.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${product.category} · Cost: ${CurrencyUtils.formatKwacha(product.cost)} · Margin: ${CurrencyUtils.formatKwacha(product.margin)}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (!product.barcode.isNullOrBlank()) {
                                        Text(
                                            text = "Barcode: ${product.barcode}",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = CurrencyUtils.formatKwacha(product.price),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(stockColor.copy(alpha = 0.15f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = if (product.stock <= 0) "Out of stock" else "${product.stock} in stock",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = stockColor,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(6.dp))

                                    IconButton(
                                        onClick = { editingProduct = product },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
                                    }

                                    IconButton(
                                        onClick = { deletingProduct = product },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Add Product FAB
        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("add_product_fab"),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Product")
        }
    }

    // Add / Edit Product Dialog
    val productUnderEdit = editingProduct ?: if (showAddDialog) Product(category = if (selectedCategory == "All") "Snacks" else selectedCategory) else null
    if (productUnderEdit != null) {
        var name by remember { mutableStateOf(productUnderEdit.name) }
        var category by remember { mutableStateOf(productUnderEdit.category) }
        var priceText by remember { mutableStateOf(if (productUnderEdit.price > 0) String.format(Locale.US, "%.2f", productUnderEdit.price) else "") }
        var costText by remember { mutableStateOf(if (productUnderEdit.cost > 0) String.format(Locale.US, "%.2f", productUnderEdit.cost) else "") }
        var stockText by remember { mutableStateOf("${productUnderEdit.stock}") }
        var lowAtText by remember { mutableStateOf("${productUnderEdit.lowAt}") }
        var barcode by remember { mutableStateOf(productUnderEdit.barcode ?: "") }
        var isMenuOpen by remember { mutableStateOf(false) }

        val pPrice = CurrencyUtils.parseKwachaDouble(priceText)
        val pCost = CurrencyUtils.parseKwachaDouble(costText)
        val margin = pPrice - pCost

        AlertDialog(
            onDismissRequest = {
                showAddDialog = false
                editingProduct = null
            },
            title = { Text(if (editingProduct != null) "Edit Product" else "Add Product", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Product Name") },
                        placeholder = { Text("e.g. Coca-Cola 300ml") },
                        modifier = Modifier.fillMaxWidth().testTag("product_name_input"),
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
                            listOf("Drinks", "Fresh Food", "Snacks", "Bakery", "Stationery", "Other").forEach { c ->
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

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = priceText,
                            onValueChange = { priceText = it },
                            label = { Text("Selling Price (K)") },
                            placeholder = { Text("8.00") },
                            modifier = Modifier.weight(1f).testTag("product_price_input"),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = costText,
                            onValueChange = { costText = it },
                            label = { Text("Cost Price (K)") },
                            placeholder = { Text("5.50") },
                            modifier = Modifier.weight(1f).testTag("product_cost_input"),
                            singleLine = true
                        )
                    }

                    if (pPrice > 0) {
                        Text(
                            text = "Estimated Margin: ${CurrencyUtils.formatKwacha(margin)} per item",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = stockText,
                            onValueChange = { stockText = it },
                            label = { Text("Stock Quantity") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = lowAtText,
                            onValueChange = { lowAtText = it },
                            label = { Text("Low Stock Alert") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    OutlinedTextField(
                        value = barcode,
                        onValueChange = { barcode = it },
                        label = { Text("Barcode / Code (Optional)") },
                        placeholder = { Text("e.g. 6001000000000") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank() && pPrice > 0) {
                            val p = productUnderEdit.copy(
                                name = name.trim(),
                                category = category,
                                price = pPrice,
                                cost = pCost,
                                stock = stockText.toIntOrNull() ?: 0,
                                lowAt = lowAtText.toIntOrNull() ?: 10,
                                barcode = barcode.trim().takeIf { it.isNotBlank() }
                            )
                            onSaveProduct(p)
                            showAddDialog = false
                            editingProduct = null
                        }
                    },
                    modifier = Modifier.testTag("save_product_button")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    showAddDialog = false
                    editingProduct = null
                }) { Text("Cancel") }
            }
        )
    }

    // Delete confirmation dialog
    if (deletingProduct != null) {
        val target = deletingProduct!!
        AlertDialog(
            onDismissRequest = { deletingProduct = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Remove ${target.name}?") },
            text = { Text("Past sales stay in the ledger. This only removes it from your current product catalog.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteProduct(target.id)
                        deletingProduct = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { deletingProduct = null }) { Text("Cancel") }
            }
        )
    }
}
