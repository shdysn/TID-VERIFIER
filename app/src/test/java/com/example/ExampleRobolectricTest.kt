package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.PaymentProvider
import com.example.forensics.SampleScenario
import com.example.forensics.SampleSlipGenerator
import com.example.sms.SmsInboxReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("TID Verifier", appName)
    }

    @Test
    fun `test sms tid extraction for EasyPaisa and JazzCash`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val smsReader = SmsInboxReader(context)

        val easyPaisaMsg = "You have received Rs. 2,500 from 03001234567. New Balance is Rs. 15,200. Trx ID: EP8391028471 on 18-Oct."
        val extractedTid = smsReader.extractTid(easyPaisaMsg)
        val extractedAmount = smsReader.extractAmount(easyPaisaMsg)
        val provider = smsReader.identifyProvider("3737", easyPaisaMsg)

        assertEquals("EP8391028471", extractedTid)
        assertEquals(2500.0, extractedAmount ?: 0.0, 0.01)
        assertEquals(PaymentProvider.EASYPAISA, provider)
    }

    @Test
    fun `test slip bitmap generation for genuine and fake scenarios`() {
        val genuineBitmap = SampleSlipGenerator.generateSlipBitmap(SampleScenario.GENUINE_EASYPAISA)
        assertNotNull(genuineBitmap)
        assertTrue(genuineBitmap.width > 0)
        assertTrue(genuineBitmap.height > 0)

        val fakeBitmap = SampleSlipGenerator.generateSlipBitmap(SampleScenario.FAKE_EASYPAISA_NO_SMS)
        assertNotNull(fakeBitmap)
    }
}
