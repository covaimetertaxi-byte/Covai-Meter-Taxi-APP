package com.covaimetertaxi.driver

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    assertEquals("Covai Meter Taxi", appName)
  }

  @Test
  fun `verify hour-based OTP algorithm with user example`() {
    val mobile = "6385765142"
    val hour = 14
    val otp = com.covaimetertaxi.driver.util.RideOtpManager.calculateOtp(mobile, hour)
    assertEquals("9270", otp)

    val details = com.covaimetertaxi.driver.util.RideOtpManager.getCalculationDetails(mobile, hour)
    assertNotNull(details)
    assertEquals("6385", details?.first4Digits)
    assertEquals("5836", details?.reversedDigits)
    assertEquals(14, details?.currentIstHour)
    assertEquals("9270", details?.finalOtp)
    assertEquals(4, details?.stepCalculations?.size)
    assertEquals("(5 + 14) % 10 = 9", details?.stepCalculations?.get(0))
    assertEquals("(8 + 14) % 10 = 2", details?.stepCalculations?.get(1))
    assertEquals("(3 + 14) % 10 = 7", details?.stepCalculations?.get(2))
    assertEquals("(6 + 14) % 10 = 0", details?.stepCalculations?.get(3))
  }
}
