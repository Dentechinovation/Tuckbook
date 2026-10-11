package com.example

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.data.AuthManager
import com.example.data.FirestoreRepository
import com.example.data.GeminiService
import com.example.model.ChatMessage
import com.example.model.Closing
import com.example.model.Creditor
import com.example.model.Expense
import com.example.model.Product
import com.example.model.Repayment
import com.example.model.Sale
import com.example.model.Settings
import com.example.model.Shift
import com.example.ui.components.TuckBookBottomNav
import com.example.ui.components.TuckBookTopBar
import com.example.ui.components.TuckScreen
import com.example.ui.screens.AdvisorScreen
import com.example.ui.screens.CreditBookScreen
import com.example.ui.screens.LedgerScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SignInScreen
import com.example.ui.screens.StockScreen
import com.example.ui.screens.TillScreen
import com.example.ui.screens.TodayScreen
import com.example.ui.theme.MyApplicationTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import kotlinx.coroutines.launch
import java.util.Calendar

private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {

    private lateinit var authManager: AuthManager
    private lateinit var repository: FirestoreRepository
    private val geminiService = GeminiService()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)

        authManager = AuthManager(this)
        repository = FirestoreRepository(this)

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val currentUser by authManager.currentUser.collectAsState()
                val isAuthLoading by authManager.isLoading.collectAsState()
                val authErrorMessage by authManager.errorMessage.collectAsState()
                val coroutineScope = rememberCoroutineScope()

                // Composable Auth Gate
                if (currentUser == null) {
                    SignInScreen(
                        isLoading = isAuthLoading,
                        errorMessage = authErrorMessage,
                        onSignInClick = {
                            coroutineScope.launch {
                                authManager.signInWithGoogle()
                            }
                        }
                    )
                } else {
                    // Seed initial products if needed
                    LaunchedEffect(currentUser?.uid) {
                        try {
                            repository.seedDefaultProductsIfEmpty()
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed seeding products", e)
                        }
                    }

                    TuckBookAppContent(
                        context = this@MainActivity,
                        userEmail = currentUser?.email,
                        repository = repository,
                        geminiService = geminiService,
                        onSignOut = { authManager.signOut() }
                    )
                }
            }
        }
    }

    private fun configureAppCheck(context: Context, intent: Intent?) {
        try {
            FirebaseApp.initializeApp(context)
            val appCheck = FirebaseAppCheck.getInstance()
            val providerFactory = DebugAppCheckProviderFactory.getInstance()
            appCheck.installAppCheckProviderFactory(providerFactory)
        } catch (e: Exception) {
            Log.w(TAG, "AppCheck initialization note: ${e.message}")
        }
    }
}

@Composable
fun TuckBookAppContent(
    context: Context,
    userEmail: String?,
    repository: FirestoreRepository,
    geminiService: GeminiService,
    onSignOut: () -> Unit
) {
    var currentScreen by remember { mutableStateOf(TuckScreen.TODAY) }
    val coroutineScope = rememberCoroutineScope()

    // Handle back button: return to Home (Today) screen if on a sub-screen
    BackHandler(enabled = currentScreen != TuckScreen.TODAY) {
        currentScreen = TuckScreen.TODAY
    }

    val todayDate = remember { repository.getTodayBusinessDate() }

    // Live data flows from Firestore
    val products by repository.getProductsFlow().collectAsState(initial = emptyList())
    val sales by repository.getSalesFlow().collectAsState(initial = emptyList())
    val expenses by repository.getExpensesFlow().collectAsState(initial = emptyList())
    val creditors by repository.getCreditorsFlow().collectAsState(initial = emptyList())
    val repayments by repository.getRepaymentsFlow().collectAsState(initial = emptyList())
    val shifts by repository.getShiftsFlow().collectAsState(initial = emptyList())
    val closings by repository.getClosingsFlow().collectAsState(initial = emptyList())
    val auditEntries by repository.getAuditFlow().collectAsState(initial = emptyList())
    val messages by repository.getChatMessagesFlow().collectAsState(initial = emptyList())

    var settings by remember { mutableStateOf(Settings()) }
    LaunchedEffect(Unit) {
        settings = repository.getSettings()
    }

    // Active shift detection
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

    Scaffold(
        topBar = {
            TuckBookTopBar(
                currentScreen = currentScreen,
                userEmail = userEmail,
                todayDate = todayDate,
                activeShiftName = activeShift?.name,
                onOpenAdvisor = { currentScreen = TuckScreen.ADVISOR },
                onSignOutClick = onSignOut
            )
        },
        bottomBar = {
            TuckBookBottomNav(
                currentScreen = currentScreen,
                onScreenSelected = { currentScreen = it }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                TuckScreen.SELL -> {
                    TillScreen(
                        products = products,
                        creditors = creditors,
                        activeShiftName = activeShift?.name,
                        settings = settings,
                        onCompleteSale = { sale ->
                            repository.recordSale(sale)
                        },
                        onUndoLastSale = { saleId ->
                            repository.voidSale(saleId, "Undone at till", settings.cashier)
                        }
                    )
                }

                TuckScreen.TODAY -> {
                    TodayScreen(
                        sales = sales,
                        expenses = expenses,
                        products = products,
                        creditors = creditors,
                        repayments = repayments,
                        shifts = shifts,
                        settings = settings,
                        onNavigateToSell = { currentScreen = TuckScreen.SELL },
                        onNavigateToBook = { currentScreen = TuckScreen.BOOK },
                        onNavigateToLedger = { currentScreen = TuckScreen.LEDGER },
                        onNavigateToProducts = { currentScreen = TuckScreen.PRODUCTS },
                        onNavigateToReports = { currentScreen = TuckScreen.REPORTS },
                        onNavigateToAdvisor = { currentScreen = TuckScreen.ADVISOR }
                    )
                }

                TuckScreen.BOOK -> {
                    CreditBookScreen(
                        creditors = creditors,
                        sales = sales,
                        repayments = repayments,
                        onAddCreditor = { c ->
                            coroutineScope.launch { repository.saveCreditor(c) }
                        },
                        onRecordRepayment = { r, name ->
                            coroutineScope.launch { repository.recordRepayment(r, name) }
                        },
                        onSendReminder = { c, bal ->
                            Toast.makeText(context, "SMS reminder sent to ${c.name} (${c.phone}) (simulated)", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                TuckScreen.LEDGER -> {
                    LedgerScreen(
                        sales = sales,
                        expenses = expenses,
                        auditEntries = auditEntries,
                        onVoidSale = { saleId, reason ->
                            coroutineScope.launch { repository.voidSale(saleId, reason, settings.cashier) }
                        },
                        onRefundSale = { saleId, amt, reason ->
                            coroutineScope.launch { repository.recordRefund(saleId, amt, reason, settings.cashier) }
                        },
                        onAddSaleNote = { saleId, note ->
                            coroutineScope.launch { repository.updateSaleNote(saleId, note) }
                        },
                        onAddExpense = { exp ->
                            coroutineScope.launch { repository.addExpense(exp) }
                        },
                        onVoidExpense = { expId ->
                            coroutineScope.launch { repository.voidExpense(expId) }
                        }
                    )
                }

                TuckScreen.PRODUCTS -> {
                    StockScreen(
                        products = products,
                        onSaveProduct = { p ->
                            coroutineScope.launch { repository.saveProduct(p) }
                        },
                        onDeleteProduct = { pId ->
                            coroutineScope.launch { repository.deleteProduct(pId) }
                        }
                    )
                }

                TuckScreen.REPORTS -> {
                    ReportsScreen(
                        sales = sales,
                        expenses = expenses,
                        shifts = shifts,
                        closings = closings,
                        settings = settings,
                        onSaveClosing = { c ->
                            coroutineScope.launch { repository.saveClosing(c) }
                        }
                    )
                }

                TuckScreen.SETTINGS -> {
                    SettingsScreen(
                        settings = settings,
                        shifts = shifts,
                        userEmail = userEmail,
                        onSaveSettings = { s ->
                            settings = s
                            coroutineScope.launch { repository.saveSettings(s) }
                        },
                        onLoadDemoData = {
                            coroutineScope.launch { repository.loadLusakaDemoData() }
                        },
                        onClearData = {
                            coroutineScope.launch {
                                for (p in products) repository.deleteProduct(p.id)
                            }
                        },
                        onOpenAdvisor = { currentScreen = TuckScreen.ADVISOR },
                        onOpenReports = { currentScreen = TuckScreen.REPORTS },
                        onSignOut = onSignOut
                    )
                }

                TuckScreen.ADVISOR -> {
                    AdvisorScreen(
                        messages = messages,
                        products = products,
                        sales = sales,
                        expenses = expenses,
                        geminiService = geminiService,
                        onSendMessage = { chatMessage ->
                            coroutineScope.launch { repository.saveChatMessage(chatMessage) }
                        },
                        onClearChat = {
                            coroutineScope.launch { repository.clearChatMessages() }
                        }
                    )
                }
            }
        }
    }
}
