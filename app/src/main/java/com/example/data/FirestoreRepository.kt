package com.example.data

import android.content.Context
import android.util.Log
import com.example.R
import com.example.model.Audit
import com.example.model.ChatMessage
import com.example.model.Closing
import com.example.model.Creditor
import com.example.model.Expense
import com.example.model.Product
import com.example.model.Repayment
import com.example.model.Sale
import com.example.model.SaleItem
import com.example.model.Settings
import com.example.model.Shift
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

private const val TAG = "FirestoreRepository"

class FirestoreRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    fun requireUserId(): String {
        return auth.currentUser?.uid ?: throw IllegalStateException("User is not authenticated")
    }

    fun getTodayBusinessDate(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("Africa/Lusaka")
        return sdf.format(Date())
    }

    // --- PRODUCTS ---

    fun getProductsFlow(): Flow<List<Product>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val registration: ListenerRegistration = db.collection("users")
            .document(uid)
            .collection("products")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to products", error)
                    return@addSnapshotListener
                }
                val products = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        Product(
                            id = doc.getString("id") ?: doc.id,
                            name = doc.getString("name") ?: "",
                            category = doc.getString("category") ?: "Snacks",
                            price = doc.getDouble("price") ?: (doc.getLong("unitPriceNgwee")?.toDouble()?.div(100.0) ?: 0.0),
                            cost = doc.getDouble("cost") ?: (doc.getLong("costPriceNgwee")?.toDouble()?.div(100.0) ?: 0.0),
                            stock = (doc.getLong("stock") ?: 0L).toInt(),
                            lowAt = (doc.getLong("lowAt") ?: 10L).toInt(),
                            barcode = doc.getString("barcode"),
                            isActive = doc.getBoolean("active") ?: true
                        )
                    } catch (e: Exception) {
                        null
                    }
                }?.sortedBy { it.name } ?: emptyList()
                trySend(products)
            }

        awaitClose { registration.remove() }
    }

    suspend fun saveProduct(product: Product) {
        val uid = requireUserId()
        val productId = if (product.id.isBlank()) UUID.randomUUID().toString() else product.id
        val data = hashMapOf(
            "id" to productId,
            "name" to product.name,
            "category" to product.category,
            "price" to product.price,
            "cost" to product.cost,
            "stock" to product.stock,
            "lowAt" to product.lowAt,
            "barcode" to product.barcode,
            "active" to product.isActive,
            "updatedAt" to System.currentTimeMillis()
        )
        db.collection("users")
            .document(uid)
            .collection("products")
            .document(productId)
            .set(data)
            .await()
    }

    suspend fun deleteProduct(productId: String) {
        val uid = requireUserId()
        db.collection("users")
            .document(uid)
            .collection("products")
            .document(productId)
            .delete()
            .await()
    }

    suspend fun updateStock(productId: String, delta: Int) {
        val uid = requireUserId()
        val docRef = db.collection("users")
            .document(uid)
            .collection("products")
            .document(productId)

        db.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)
            val currentStock = (snapshot.getLong("stock") ?: 0L).toInt()
            val newStock = (currentStock + delta).coerceAtLeast(0)
            transaction.update(docRef, "stock", newStock, "updatedAt", System.currentTimeMillis())
        }.await()
    }

    // --- SALES ---

    fun getSalesFlow(businessDate: String = ""): Flow<List<Sale>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val query = if (businessDate.isNotBlank()) {
            db.collection("users")
                .document(uid)
                .collection("sales")
                .whereEqualTo("businessDate", businessDate)
                .orderBy("at", Query.Direction.DESCENDING)
        } else {
            db.collection("users")
                .document(uid)
                .collection("sales")
                .orderBy("at", Query.Direction.DESCENDING)
                .limit(200)
        }

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Error listening to sales", error)
                return@addSnapshotListener
            }
            val sales = snapshot?.documents?.mapNotNull { doc ->
                try {
                    val rawItems = doc.get("items") as? List<Map<String, Any>> ?: emptyList()
                    val items = rawItems.map { map ->
                        SaleItem(
                            productId = map["productId"] as? String ?: "",
                            name = map["name"] as? String ?: "",
                            qty = (map["qty"] as? Long ?: 1L).toInt(),
                            price = (map["price"] as? Number)?.toDouble() ?: 0.0,
                            cost = (map["cost"] as? Number)?.toDouble() ?: 0.0
                        )
                    }

                    Sale(
                        id = doc.getString("id") ?: doc.id,
                        at = doc.getLong("at") ?: doc.getLong("createdAt") ?: System.currentTimeMillis(),
                        items = items,
                        total = doc.getDouble("total") ?: (doc.getLong("totalNgwee")?.toDouble()?.div(100.0) ?: 0.0),
                        tender = doc.getString("tender") ?: doc.getString("paymentMethod") ?: "cash",
                        creditorId = doc.getString("creditorId"),
                        shift = doc.getString("shift"),
                        note = doc.getString("note"),
                        discount = doc.getDouble("discount") ?: (doc.getLong("discountNgwee")?.toDouble()?.div(100.0)),
                        reversed = doc.getBoolean("reversed") ?: doc.getBoolean("isVoided") ?: false,
                        reversedAt = doc.getLong("reversedAt"),
                        reversedBy = doc.getString("reversedBy"),
                        voidReason = doc.getString("voidReason"),
                        refunded = doc.getDouble("refunded"),
                        cashier = doc.getString("cashier"),
                        businessDate = doc.getString("businessDate") ?: ""
                    )
                } catch (e: Exception) {
                    null
                }
            } ?: emptyList()
            trySend(sales)
        }

        awaitClose { registration.remove() }
    }

    suspend fun recordSale(sale: Sale) {
        val uid = requireUserId()
        val saleId = if (sale.id.isBlank()) UUID.randomUUID().toString() else sale.id
        val saleRef = db.collection("users")
            .document(uid)
            .collection("sales")
            .document(saleId)

        db.runTransaction { transaction ->
            if (transaction.get(saleRef).exists()) return@runTransaction

            // Deduct stock for each line item
            for (item in sale.items) {
                if (item.productId.isNotBlank()) {
                    val prodRef = db.collection("users").document(uid).collection("products").document(item.productId)
                    val prodSnap = transaction.get(prodRef)
                    if (prodSnap.exists()) {
                        val currentStock = (prodSnap.getLong("stock") ?: 0L).toInt()
                        val newStock = (currentStock - item.qty).coerceAtLeast(0)
                        transaction.update(prodRef, "stock", newStock, "updatedAt", System.currentTimeMillis())
                    }
                }
            }

            val itemsData = sale.items.map {
                hashMapOf(
                    "productId" to it.productId,
                    "name" to it.name,
                    "qty" to it.qty,
                    "price" to it.price,
                    "cost" to it.cost
                )
            }

            val saleData = hashMapOf(
                "id" to saleId,
                "at" to sale.at,
                "items" to itemsData,
                "total" to sale.total,
                "tender" to sale.tender,
                "creditorId" to sale.creditorId,
                "shift" to sale.shift,
                "note" to sale.note,
                "discount" to sale.discount,
                "reversed" to false,
                "cashier" to sale.cashier,
                "businessDate" to if (sale.businessDate.isBlank()) getTodayBusinessDate() else sale.businessDate
            )
            transaction.set(saleRef, saleData)
        }.await()

        addAuditEntry("Sale", "#${saleId.take(6).uppercase()} K${String.format(Locale.US, "%.2f", sale.total)} · ${sale.tender.uppercase()}")
    }

    suspend fun voidSale(saleId: String, reason: String = "Voided by cashier", actor: String = "Cashier") {
        val uid = requireUserId()
        val saleRef = db.collection("users").document(uid).collection("sales").document(saleId)

        db.runTransaction { transaction ->
            val snapshot = transaction.get(saleRef)
            if (!snapshot.exists()) return@runTransaction
            if (snapshot.getBoolean("reversed") == true) return@runTransaction

            // Put stock back
            val rawItems = snapshot.get("items") as? List<Map<String, Any>> ?: emptyList()
            for (map in rawItems) {
                val prodId = map["productId"] as? String ?: ""
                val qty = (map["qty"] as? Long ?: 0L).toInt()
                if (prodId.isNotBlank() && qty > 0) {
                    val prodRef = db.collection("users").document(uid).collection("products").document(prodId)
                    val prodSnap = transaction.get(prodRef)
                    if (prodSnap.exists()) {
                        val currentStock = (prodSnap.getLong("stock") ?: 0L).toInt()
                        transaction.update(prodRef, "stock", currentStock + qty, "updatedAt", System.currentTimeMillis())
                    }
                }
            }

            transaction.update(
                saleRef,
                "reversed", true,
                "reversedAt", System.currentTimeMillis(),
                "reversedBy", actor,
                "voidReason", reason
            )
        }.await()

        addAuditEntry("Reverse sale", "#${saleId.take(6).uppercase()} voided: $reason (stock restored)")
    }

    suspend fun recordRefund(saleId: String, amount: Double, reason: String, actor: String = "Cashier") {
        val uid = requireUserId()
        val saleRef = db.collection("users").document(uid).collection("sales").document(saleId)
        db.runTransaction { transaction ->
            val snap = transaction.get(saleRef)
            val currentRefund = snap.getDouble("refunded") ?: 0.0
            val existingNote = snap.getString("note") ?: ""
            val newNote = if (existingNote.isBlank()) "Refund K${String.format(Locale.US, "%.2f", amount)}: $reason" else "$existingNote · Refund K${String.format(Locale.US, "%.2f", amount)}: $reason"
            transaction.update(saleRef, "refunded", currentRefund + amount, "note", newNote)
        }.await()

        addAuditEntry("Refund", "#${saleId.take(6).uppercase()} K${String.format(Locale.US, "%.2f", amount)}: $reason")
    }

    suspend fun updateSaleNote(saleId: String, note: String) {
        val uid = requireUserId()
        db.collection("users").document(uid).collection("sales").document(saleId).update("note", note).await()
        addAuditEntry("Note", "#${saleId.take(6).uppercase()}: $note")
    }

    // --- CREDIT BOOK (CREDITORS & REPAYMENTS) ---

    fun getCreditorsFlow(): Flow<List<Creditor>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val reg = db.collection("users").document(uid).collection("creditors")
            .addSnapshotListener { snap, err ->
                if (err != null) return@addSnapshotListener
                val list = snap?.documents?.mapNotNull { doc ->
                    try {
                        Creditor(
                            id = doc.getString("id") ?: doc.id,
                            name = doc.getString("name") ?: "",
                            kind = doc.getString("kind") ?: "student",
                            detail = doc.getString("detail") ?: "",
                            phone = doc.getString("phone") ?: "",
                            dueDays = (doc.getLong("dueDays") ?: 7L).toInt()
                        )
                    } catch (e: Exception) { null }
                }?.sortedBy { it.name } ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    suspend fun saveCreditor(creditor: Creditor) {
        val uid = requireUserId()
        val id = if (creditor.id.isBlank()) UUID.randomUUID().toString() else creditor.id
        val data = hashMapOf(
            "id" to id,
            "name" to creditor.name,
            "kind" to creditor.kind,
            "detail" to creditor.detail,
            "phone" to creditor.phone,
            "dueDays" to creditor.dueDays
        )
        db.collection("users").document(uid).collection("creditors").document(id).set(data).await()
        addAuditEntry("Book", "Added ${creditor.name} (${creditor.kind})")
    }

    fun getRepaymentsFlow(): Flow<List<Repayment>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val reg = db.collection("users").document(uid).collection("repayments")
            .orderBy("at", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) return@addSnapshotListener
                val list = snap?.documents?.mapNotNull { doc ->
                    try {
                        Repayment(
                            id = doc.getString("id") ?: doc.id,
                            creditorId = doc.getString("creditorId") ?: "",
                            amount = doc.getDouble("amount") ?: 0.0,
                            at = doc.getLong("at") ?: System.currentTimeMillis(),
                            tender = doc.getString("tender") ?: "cash"
                        )
                    } catch (e: Exception) { null }
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    suspend fun recordRepayment(repayment: Repayment, creditorName: String = "") {
        val uid = requireUserId()
        val id = if (repayment.id.isBlank()) UUID.randomUUID().toString() else repayment.id
        val data = hashMapOf(
            "id" to id,
            "creditorId" to repayment.creditorId,
            "amount" to repayment.amount,
            "at" to repayment.at,
            "tender" to repayment.tender
        )
        db.collection("users").document(uid).collection("repayments").document(id).set(data).await()
        addAuditEntry("Repayment", "$creditorName paid K${String.format(Locale.US, "%.2f", repayment.amount)} via ${repayment.tender}")
    }

    // --- EXPENSES ---

    fun getExpensesFlow(): Flow<List<Expense>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val reg = db.collection("users").document(uid).collection("expenses")
            .orderBy("at", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) return@addSnapshotListener
                val list = snap?.documents?.mapNotNull { doc ->
                    try {
                        Expense(
                            id = doc.getString("id") ?: doc.id,
                            at = doc.getLong("at") ?: doc.getLong("createdAt") ?: System.currentTimeMillis(),
                            category = doc.getString("category") ?: "Other",
                            amount = doc.getDouble("amount") ?: (doc.getLong("amountNgwee")?.toDouble()?.div(100.0) ?: 0.0),
                            description = doc.getString("description") ?: doc.getString("title") ?: "",
                            note = doc.getString("note"),
                            reversed = doc.getBoolean("reversed") ?: doc.getBoolean("isVoided") ?: false,
                            businessDate = doc.getString("businessDate") ?: ""
                        )
                    } catch (e: Exception) { null }
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    suspend fun addExpense(expense: Expense) {
        val uid = requireUserId()
        val id = if (expense.id.isBlank()) UUID.randomUUID().toString() else expense.id
        val data = hashMapOf(
            "id" to id,
            "at" to expense.at,
            "category" to expense.category,
            "amount" to expense.amount,
            "description" to expense.description,
            "note" to expense.note,
            "reversed" to false,
            "businessDate" to if (expense.businessDate.isBlank()) getTodayBusinessDate() else expense.businessDate
        )
        db.collection("users").document(uid).collection("expenses").document(id).set(data).await()
        addAuditEntry("Expense", "${expense.description} K${String.format(Locale.US, "%.2f", expense.amount)} (${expense.category})")
    }

    suspend fun voidExpense(expenseId: String) {
        val uid = requireUserId()
        db.collection("users").document(uid).collection("expenses").document(expenseId).update("reversed", true).await()
        addAuditEntry("Reverse expense", "#${expenseId.take(6).uppercase()} reversed")
    }

    // --- SHIFTS & CLOSINGS ---

    fun getShiftsFlow(): Flow<List<Shift>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(defaultShifts())
            close()
            return@callbackFlow
        }

        val reg = db.collection("users").document(uid).collection("shifts")
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null || snap.isEmpty) {
                    trySend(defaultShifts())
                    return@addSnapshotListener
                }
                val list = snap.documents.mapNotNull { doc ->
                    try {
                        Shift(
                            id = doc.getString("id") ?: doc.id,
                            name = doc.getString("name") ?: "",
                            start = doc.getString("start") ?: "10:00",
                            end = doc.getString("end") ?: "10:30",
                            enabled = doc.getBoolean("enabled") ?: true
                        )
                    } catch (e: Exception) { null }
                }
                trySend(if (list.isEmpty()) defaultShifts() else list)
            }
        awaitClose { reg.remove() }
    }

    private fun defaultShifts(): List<Shift> = listOf(
        Shift("s1", "Morning Break", "10:00", "10:30", true),
        Shift("s2", "Lunch Break", "12:30", "13:30", true),
        Shift("s3", "After-School", "15:30", "17:00", true)
    )

    fun getClosingsFlow(): Flow<List<Closing>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val reg = db.collection("users").document(uid).collection("closings")
            .orderBy("at", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snap, err ->
                if (err != null) return@addSnapshotListener
                val list = snap?.documents?.mapNotNull { doc ->
                    try {
                        val exp = doc.get("expected") as? Map<String, Number> ?: emptyMap()
                        val cnt = doc.get("counted") as? Map<String, Number> ?: emptyMap()
                        Closing(
                            id = doc.getString("id") ?: doc.id,
                            at = doc.getLong("at") ?: System.currentTimeMillis(),
                            shift = doc.getString("shift") ?: "",
                            cashier = doc.getString("cashier") ?: "",
                            expected = exp.mapValues { it.value.toDouble() },
                            counted = cnt.mapValues { it.value.toDouble() },
                            diff = doc.getDouble("diff") ?: 0.0,
                            note = doc.getString("note") ?: ""
                        )
                    } catch (e: Exception) { null }
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    suspend fun saveClosing(closing: Closing) {
        val uid = requireUserId()
        val id = if (closing.id.isBlank()) UUID.randomUUID().toString() else closing.id
        val data = hashMapOf(
            "id" to id,
            "at" to closing.at,
            "shift" to closing.shift,
            "cashier" to closing.cashier,
            "expected" to closing.expected,
            "counted" to closing.counted,
            "diff" to closing.diff,
            "note" to closing.note
        )
        db.collection("users").document(uid).collection("closings").document(id).set(data).await()
        addAuditEntry("Shift close", "${closing.shift} closed by ${closing.cashier}: diff K${String.format(Locale.US, "%.2f", closing.diff)}")
    }

    // --- AUDIT TRAIL ---

    fun getAuditFlow(): Flow<List<Audit>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val reg = db.collection("users").document(uid).collection("audit")
            .orderBy("at", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snap, err ->
                if (err != null) return@addSnapshotListener
                val list = snap?.documents?.mapNotNull { doc ->
                    try {
                        Audit(
                            id = doc.getString("id") ?: doc.id,
                            at = doc.getLong("at") ?: System.currentTimeMillis(),
                            actor = doc.getString("actor") ?: "Cashier",
                            action = doc.getString("action") ?: "",
                            detail = doc.getString("detail") ?: ""
                        )
                    } catch (e: Exception) { null }
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    suspend fun addAuditEntry(action: String, detail: String, actor: String = "Cashier") {
        try {
            val uid = auth.currentUser?.uid ?: return
            val id = UUID.randomUUID().toString()
            val data = hashMapOf(
                "id" to id,
                "at" to System.currentTimeMillis(),
                "actor" to actor,
                "action" to action,
                "detail" to detail
            )
            db.collection("users").document(uid).collection("audit").document(id).set(data).await()
        } catch (e: Exception) {
            Log.e(TAG, "Error writing audit log", e)
        }
    }

    // --- SETTINGS ---

    suspend fun getSettings(): Settings {
        val uid = auth.currentUser?.uid ?: return Settings()
        return try {
            val doc = db.collection("users").document(uid).collection("meta").document("settings").get().await()
            if (doc.exists()) {
                Settings(
                    shopName = doc.getString("shopName") ?: "Kabulonga Tuck Shop",
                    school = doc.getString("school") ?: "Kabulonga Girls Secondary, Lusaka",
                    owner = doc.getString("owner") ?: "Dennis",
                    email = doc.getString("email") ?: "dennis@example.zm",
                    phone = doc.getString("phone") ?: "+260 977 000 111",
                    cashier = doc.getString("cashier") ?: "Dennis",
                    businessType = doc.getString("businessType") ?: "School tuck shop",
                    location = doc.getString("location") ?: "Kabulonga, Lusaka",
                    notifyPhone = doc.getBoolean("notifyPhone") ?: true,
                    notifyEmail = doc.getBoolean("notifyEmail") ?: true,
                    enforceStock = doc.getBoolean("enforceStock") ?: true,
                    rushMode = doc.getBoolean("rushMode") ?: false
                )
            } else {
                Settings()
            }
        } catch (e: Exception) {
            Settings()
        }
    }

    suspend fun saveSettings(s: Settings) {
        val uid = requireUserId()
        val data = hashMapOf(
            "shopName" to s.shopName,
            "school" to s.school,
            "owner" to s.owner,
            "email" to s.email,
            "phone" to s.phone,
            "cashier" to s.cashier,
            "businessType" to s.businessType,
            "location" to s.location,
            "notifyPhone" to s.notifyPhone,
            "notifyEmail" to s.notifyEmail,
            "enforceStock" to s.enforceStock,
            "rushMode" to s.rushMode
        )
        db.collection("users").document(uid).collection("meta").document("settings").set(data).await()
    }

    // --- SEED LUSAKA DEMO DATA ---

    suspend fun seedDefaultProductsIfEmpty() {
        val uid = requireUserId()
        val snap = db.collection("users").document(uid).collection("products").limit(1).get().await()
        if (snap.isEmpty) {
            loadLusakaDemoData()
        }
    }

    suspend fun loadLusakaDemoData() {
        val uid = requireUserId()

        val products = listOf(
            Product(name = "Coca-Cola 300ml", category = "Drinks", price = 8.0, cost = 5.5, stock = 46, lowAt = 10, barcode = "6001000000000"),
            Product(name = "Fanta Orange 300ml", category = "Drinks", price = 8.0, cost = 5.5, stock = 32, lowAt = 10, barcode = "6001000000137"),
            Product(name = "Maheu 500ml", category = "Drinks", price = 7.0, cost = 4.5, stock = 6, lowAt = 10, barcode = "6001000000274"),
            Product(name = "Mosi Water 500ml", category = "Drinks", price = 5.0, cost = 3.0, stock = 60, lowAt = 10, barcode = "6001000000411"),
            Product(name = "Cerelac Juice Box", category = "Drinks", price = 6.0, cost = 4.0, stock = 24, lowAt = 10, barcode = "6001000000548"),
            Product(name = "Beef Sausage Roll", category = "Fresh Food", price = 15.0, cost = 9.0, stock = 18, lowAt = 10, barcode = "6001000000685"),
            Product(name = "Chicken & Chips", category = "Fresh Food", price = 35.0, cost = 22.0, stock = 12, lowAt = 5, barcode = "6001000000822"),
            Product(name = "Samosa (2 pcs)", category = "Fresh Food", price = 10.0, cost = 5.0, stock = 4, lowAt = 10, barcode = "6001000000959"),
            Product(name = "Egg Sandwich", category = "Fresh Food", price = 12.0, cost = 7.0, stock = 15, lowAt = 10, barcode = "6001000001096"),
            Product(name = "Jiggies", category = "Snacks", price = 3.0, cost = 1.8, stock = 80, lowAt = 20, barcode = "6001000001233"),
            Product(name = "Ziggy Popcorn", category = "Snacks", price = 4.0, cost = 2.5, stock = 55, lowAt = 15, barcode = "6001000001370"),
            Product(name = "Freshpikt Biscuits", category = "Snacks", price = 6.0, cost = 4.0, stock = 9, lowAt = 10, barcode = "6001000001507"),
            Product(name = "Willards Chips", category = "Snacks", price = 10.0, cost = 7.0, stock = 28, lowAt = 10, barcode = "6001000001644"),
            Product(name = "Vitumbuwa (3 pcs)", category = "Bakery", price = 5.0, cost = 2.5, stock = 30, lowAt = 10, barcode = "6001000001781"),
            Product(name = "Buns (each)", category = "Bakery", price = 4.0, cost = 2.0, stock = 40, lowAt = 10, barcode = "6001000001918"),
            Product(name = "Muffin", category = "Bakery", price = 8.0, cost = 4.5, stock = 3, lowAt = 6, barcode = "6001000002055"),
            Product(name = "Exercise Book 96pg", category = "Stationery", price = 12.0, cost = 8.0, stock = 25, lowAt = 10, barcode = "6001000002192"),
            Product(name = "BIC Pen", category = "Stationery", price = 5.0, cost = 3.0, stock = 50, lowAt = 10, barcode = "6001000002329")
        )
        for (p in products) saveProduct(p)

        val creditors = listOf(
            Creditor(id = "creditor-banda", name = "Mr. Chanda Mwila", kind = "teacher", detail = "Mathematics Dept", phone = "+260 977 123 456", dueDays = 30),
            Creditor(id = "creditor-phiri", name = "Mrs. Banda", kind = "teacher", detail = "Grade 9 Class Teacher", phone = "+260 966 222 018", dueDays = 30),
            Creditor(id = "creditor-mutale", name = "Mutale Phiri", kind = "student", detail = "Grade 10B", phone = "+260 955 404 771", dueDays = 7),
            Creditor(id = "creditor-natasha", name = "Natasha Zulu", kind = "student", detail = "Grade 8A", phone = "+260 971 880 120", dueDays = 7)
        )
        for (c in creditors) saveCreditor(c)

        for (s in defaultShifts()) {
            db.collection("users").document(uid).collection("shifts").document(s.id).set(
                hashMapOf("id" to s.id, "name" to s.name, "start" to s.start, "end" to s.end, "enabled" to s.enabled)
            ).await()
        }

        val now = System.currentTimeMillis()
        val todayBusiness = getTodayBusinessDate()

        // Sample sales for today
        val demoSales = listOf(
            Sale(
                id = UUID.randomUUID().toString(),
                at = now - (2 * 3600 * 1000), // 2 hours ago
                items = listOf(
                    SaleItem(name = "Samosa (2 pcs)", qty = 2, price = 10.0, cost = 5.0),
                    SaleItem(name = "Coca-Cola 300ml", qty = 1, price = 8.0, cost = 5.5)
                ),
                total = 28.0,
                tender = "cash",
                shift = "Morning Tea Break",
                cashier = "Dennis",
                businessDate = todayBusiness
            ),
            Sale(
                id = UUID.randomUUID().toString(),
                at = now - (90 * 60 * 1000), // 1.5 hours ago
                items = listOf(
                    SaleItem(name = "Beef Sausage Roll", qty = 1, price = 15.0, cost = 9.0),
                    SaleItem(name = "Maheu 500ml", qty = 1, price = 7.0, cost = 4.5)
                ),
                total = 22.0,
                tender = "mtn",
                shift = "Morning Tea Break",
                cashier = "Dennis",
                businessDate = todayBusiness
            ),
            Sale(
                id = UUID.randomUUID().toString(),
                at = now - (45 * 60 * 1000), // 45 min ago
                items = listOf(
                    SaleItem(name = "Chicken & Chips", qty = 1, price = 35.0, cost = 22.0),
                    SaleItem(name = "Fanta Orange 300ml", qty = 1, price = 8.0, cost = 5.5)
                ),
                total = 43.0,
                tender = "airtel",
                shift = "Lunch Break",
                cashier = "Dennis",
                businessDate = todayBusiness
            ),
            Sale(
                id = UUID.randomUUID().toString(),
                at = now - (30 * 60 * 1000), // 30 min ago
                items = listOf(
                    SaleItem(name = "Exercise Book 96pg", qty = 2, price = 12.0, cost = 8.0),
                    SaleItem(name = "BIC Pen", qty = 2, price = 5.0, cost = 3.0)
                ),
                total = 34.0,
                tender = "credit",
                creditorId = "creditor-banda",
                shift = "Lunch Break",
                cashier = "Dennis",
                businessDate = todayBusiness
            ),
            Sale(
                id = UUID.randomUUID().toString(),
                at = now - (10 * 60 * 1000), // 10 min ago
                items = listOf(
                    SaleItem(name = "Jiggies", qty = 2, price = 3.0, cost = 1.8),
                    SaleItem(name = "Mosi Water 500ml", qty = 1, price = 5.0, cost = 3.0)
                ),
                total = 11.0,
                tender = "cash",
                shift = "Lunch Break",
                cashier = "Dennis",
                businessDate = todayBusiness
            ),
            // Yesterday sale for 7-day trend chart
            Sale(
                id = UUID.randomUUID().toString(),
                at = now - (24 * 3600 * 1000),
                items = listOf(SaleItem(name = "Chicken & Chips", qty = 3, price = 35.0, cost = 22.0)),
                total = 105.0,
                tender = "cash",
                shift = "Lunch Break",
                cashier = "Dennis",
                businessDate = todayBusiness
            )
        )
        for (sale in demoSales) {
            recordSale(sale)
        }

        // Sample expenses for today
        val demoExpenses = listOf(
            Expense(
                id = UUID.randomUUID().toString(),
                at = now - (3 * 3600 * 1000),
                category = "Utilities",
                amount = 20.0,
                description = "Ice blocks for soda cooler",
                businessDate = todayBusiness
            ),
            Expense(
                id = UUID.randomUUID().toString(),
                at = now - (4 * 3600 * 1000),
                category = "Transport",
                amount = 35.0,
                description = "Minibus transport from City Market",
                businessDate = todayBusiness
            )
        )
        for (exp in demoExpenses) {
            addExpense(exp)
        }

        saveSettings(Settings())
        addAuditEntry("System", "Lusaka demo data loaded")
    }

    // --- CHAT MESSAGES ---

    fun getChatMessagesFlow(): Flow<List<ChatMessage>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val registration = db.collection("users")
            .document(uid)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to messages", error)
                    return@addSnapshotListener
                }
                val messages = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        ChatMessage(
                            id = doc.getString("id") ?: doc.id,
                            sender = doc.getString("sender") ?: "user",
                            text = doc.getString("text") ?: "",
                            timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                        )
                    } catch (e: Exception) {
                        null
                    }
                } ?: emptyList()
                trySend(messages)
            }

        awaitClose { registration.remove() }
    }

    suspend fun saveChatMessage(message: ChatMessage) {
        val uid = requireUserId()
        val msgId = if (message.id.isBlank()) UUID.randomUUID().toString() else message.id
        val data = hashMapOf(
            "id" to msgId,
            "sender" to message.sender,
            "text" to message.text,
            "timestamp" to message.timestamp
        )
        db.collection("users").document(uid).collection("messages").document(msgId).set(data).await()
    }

    suspend fun clearChatMessages() {
        val uid = requireUserId()
        val docs = db.collection("users").document(uid).collection("messages").get().await()
        for (doc in docs.documents) {
            doc.reference.delete().await()
        }
    }
}
