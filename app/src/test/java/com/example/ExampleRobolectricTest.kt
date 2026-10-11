package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("TuckBook", appName)
  }

  @Test
  fun `verify kwacha currency formatting`() {
    val formatted = com.example.model.CurrencyUtils.formatKwacha(1234050L)
    assertEquals("K12,340.50", formatted)

    val parsed = com.example.model.CurrencyUtils.parseKwacha("K12.50")
    assertEquals(1250L, parsed)

    val formattedDouble = com.example.model.CurrencyUtils.formatKwacha(8.50)
    assertEquals("K8.50", formattedDouble)
  }

  @Test
  fun `verify product margin calculation and tender labels`() {
    val prod = com.example.model.Product(name = "Coca-Cola 300ml", price = 8.0, cost = 5.5)
    assertEquals(2.5, prod.margin, 0.001)

    assertEquals("Cash", com.example.model.tenderLabel("cash"))
    assertEquals("Airtel Money", com.example.model.tenderLabel("airtel"))
    assertEquals("MTN MoMo", com.example.model.tenderLabel("mtn"))
    assertEquals("Zamtel Kwacha", com.example.model.tenderLabel("zamtel"))
    assertEquals("On Book", com.example.model.tenderLabel("credit"))
  }
}
