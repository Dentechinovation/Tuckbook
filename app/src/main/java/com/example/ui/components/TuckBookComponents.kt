package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.example.ui.theme.BadgeAmberBg
import com.example.ui.theme.BadgeAmberText
import com.example.ui.theme.BadgeGreenBg
import com.example.ui.theme.BadgeGreenText
import com.example.ui.theme.BadgeRoseBg
import com.example.ui.theme.BadgeRoseText
import com.example.ui.theme.Emerald600
import com.example.ui.theme.InfoBlue
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate900

enum class TuckScreen(val label: String) {
    TODAY("Home"),
    SELL("Sell"),
    PRODUCTS("Products"),
    BOOK("Book"),
    LEDGER("Ledger"),
    REPORTS("Reports"),
    SETTINGS("Settings"),
    ADVISOR("AI Advisor")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TuckBookTopBar(
    currentScreen: TuckScreen,
    userEmail: String?,
    todayDate: String,
    activeShiftName: String?,
    onOpenAdvisor: () -> Unit,
    onSignOutClick: () -> Unit
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // TuckBook Logo branding (exact match to school-store-flow.lovable.app)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Emerald600),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "T",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "Tuck",
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleLarge,
                        color = Slate900
                    )
                    Text(
                        text = "Book",
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleLarge,
                        color = InfoBlue
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                if (!activeShiftName.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(BadgeGreenBg)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = activeShiftName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = BadgeGreenText
                        )
                    }
                }
            }
        },
        actions = {
            // Quick AI Advisor button
            IconButton(
                onClick = onOpenAdvisor,
                modifier = Modifier.testTag("topbar_advisor_button")
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "AI Retail Advisor",
                    tint = Emerald600
                )
            }

            if (userEmail != null) {
                IconButton(
                    onClick = onSignOutClick,
                    modifier = Modifier.testTag("sign_out_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = "Sign Out",
                        tint = Slate500
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
fun TuckBookBottomNav(
    currentScreen: TuckScreen,
    onScreenSelected: (TuckScreen) -> Unit,
    cartItemCount: Int = 0
) {
    var showMoreMenu by remember { mutableStateOf(false) }

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    ) {
        // 1. Home Tab (Today Overview)
        NavigationBarItem(
            selected = currentScreen == TuckScreen.TODAY,
            onClick = { onScreenSelected(TuckScreen.TODAY) },
            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
            label = { Text("Home", fontSize = 12.sp, fontWeight = if (currentScreen == TuckScreen.TODAY) FontWeight.Bold else FontWeight.Normal) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Emerald600,
                selectedTextColor = Emerald600,
                indicatorColor = BadgeGreenBg
            ),
            modifier = Modifier.testTag("nav_home")
        )

        // 2. Sell Tab (POS / Till)
        NavigationBarItem(
            selected = currentScreen == TuckScreen.SELL,
            onClick = { onScreenSelected(TuckScreen.SELL) },
            icon = {
                if (cartItemCount > 0) {
                    BadgedBox(
                        badge = {
                            Badge(
                                containerColor = Emerald600,
                                contentColor = Color.White
                            ) {
                                Text("$cartItemCount")
                            }
                        }
                    ) {
                        Icon(Icons.Default.PointOfSale, contentDescription = "Sell")
                    }
                } else {
                    Icon(Icons.Default.PointOfSale, contentDescription = "Sell")
                }
            },
            label = { Text("Sell", fontSize = 12.sp, fontWeight = if (currentScreen == TuckScreen.SELL) FontWeight.Bold else FontWeight.Normal) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Emerald600,
                selectedTextColor = Emerald600,
                indicatorColor = BadgeGreenBg
            ),
            modifier = Modifier.testTag("nav_sell")
        )

        // 3. Products Tab (Inventory)
        NavigationBarItem(
            selected = currentScreen == TuckScreen.PRODUCTS,
            onClick = { onScreenSelected(TuckScreen.PRODUCTS) },
            icon = { Icon(Icons.Default.Inventory2, contentDescription = "Products") },
            label = { Text("Products", fontSize = 12.sp, fontWeight = if (currentScreen == TuckScreen.PRODUCTS) FontWeight.Bold else FontWeight.Normal) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Emerald600,
                selectedTextColor = Emerald600,
                indicatorColor = BadgeGreenBg
            ),
            modifier = Modifier.testTag("nav_products")
        )

        // 4. Book Tab (Credit Ledger)
        NavigationBarItem(
            selected = currentScreen == TuckScreen.BOOK,
            onClick = { onScreenSelected(TuckScreen.BOOK) },
            icon = { Icon(Icons.Default.MenuBook, contentDescription = "Book") },
            label = { Text("Book", fontSize = 12.sp, fontWeight = if (currentScreen == TuckScreen.BOOK) FontWeight.Bold else FontWeight.Normal) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Emerald600,
                selectedTextColor = Emerald600,
                indicatorColor = BadgeGreenBg
            ),
            modifier = Modifier.testTag("nav_book")
        )

        // 5. More Tab (Ledger, Reports, Settings, Advisor)
        val isMoreSelected = currentScreen in listOf(TuckScreen.LEDGER, TuckScreen.REPORTS, TuckScreen.SETTINGS, TuckScreen.ADVISOR)
        NavigationBarItem(
            selected = isMoreSelected,
            onClick = { showMoreMenu = true },
            icon = {
                Box {
                    Icon(Icons.Default.MoreHoriz, contentDescription = "More")
                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false }
                    ) {
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = Emerald600) },
                            text = { Text("Sales & Expense Ledger") },
                            onClick = {
                                showMoreMenu = false
                                onScreenSelected(TuckScreen.LEDGER)
                            }
                        )
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Default.Assessment, contentDescription = null, tint = Emerald600) },
                            text = { Text("Reports & Daily Cash-Up") },
                            onClick = {
                                showMoreMenu = false
                                onScreenSelected(TuckScreen.REPORTS)
                            }
                        )
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null, tint = Emerald600) },
                            text = { Text("Store Settings") },
                            onClick = {
                                showMoreMenu = false
                                onScreenSelected(TuckScreen.SETTINGS)
                            }
                        )
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Emerald600) },
                            text = { Text("Gemini AI Retail Advisor") },
                            onClick = {
                                showMoreMenu = false
                                onScreenSelected(TuckScreen.ADVISOR)
                            }
                        )
                    }
                }
            },
            label = { Text("More", fontSize = 12.sp, fontWeight = if (isMoreSelected) FontWeight.Bold else FontWeight.Normal) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Emerald600,
                selectedTextColor = Emerald600,
                indicatorColor = BadgeGreenBg
            ),
            modifier = Modifier.testTag("nav_more")
        )
    }
}

/**
 * Soft Pill Badges for TuckBook status indicators:
 * (Paid/In-stock -> Soft Emerald, Low stock/Pending -> Soft Amber, Overdue/Voided -> Soft Rose)
 */
@Composable
fun SoftBadge(
    text: String,
    type: SoftBadgeType = SoftBadgeType.SUCCESS,
    modifier: Modifier = Modifier
) {
    val (bg, textColor) = when (type) {
        SoftBadgeType.SUCCESS -> BadgeGreenBg to BadgeGreenText
        SoftBadgeType.WARNING -> BadgeAmberBg to BadgeAmberText
        SoftBadgeType.ERROR -> BadgeRoseBg to BadgeRoseText
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

enum class SoftBadgeType {
    SUCCESS,
    WARNING,
    ERROR
}
