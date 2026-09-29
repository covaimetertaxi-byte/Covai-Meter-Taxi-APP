package com.covaimetertaxi.driver.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.util.Calendar

data class OtpCalculationDetails(
    val fullMobileNumber: String,
    val first4Digits: String,
    val reversedDigits: String,
    val currentIstHour: Int,
    val stepCalculations: List<String>,
    val finalOtp: String,
    val isNetworkTimeSynced: Boolean
)

object RideOtpManager {
    /**
     * Obtains the current IST hour (0–23) using Asia/Kolkata timezone with accurate network time compensation.
     * Works offline and across all devices even if the user's mobile device has an incorrect clock or date.
     */
    fun getCurrentIstHour(): Int {
        return NetworkTimeHelper.getIstHour()
    }

    /**
     * Calculates the 4-digit ride OTP using the Hour-Based OTP algorithm:
     * 1. Extract the FIRST 4 digits of the mobile number.
     * 2. Reverse those 4 digits.
     * 3. Obtain current IST hour (0–23) in Asia/Kolkata timezone.
     * 4. For each reversed digit: OTP Digit = (Digit + CurrentHour) % 10.
     * 5. Combine the 4 digits to form the final OTP.
     *
     * Example:
     * Mobile: 6385765142
     * First 4 digits: 6385
     * Reversed: 5836
     * Current IST Hour: 14
     * (5 + 14) % 10 = 9
     * (8 + 14) % 10 = 2
     * (3 + 14) % 10 = 7
     * (6 + 14) % 10 = 0
     * Result: 9270
     */
    fun calculateOtp(mobileNumber: String, hour: Int = getCurrentIstHour()): String {
        val sanitized = mobileNumber.filter { it.isDigit() }
        if (sanitized.length < 4) return ""
        val first4 = sanitized.take(4)
        val reversed = first4.reversed()
        val sb = StringBuilder()
        for (char in reversed) {
            val digit = char.digitToIntOrNull() ?: 0
            val otpDigit = (digit + hour) % 10
            sb.append(otpDigit)
        }
        return sb.toString()
    }

    /**
     * Returns full breakdown for the "Live Algorithm Calculations" UI display.
     */
    fun getCalculationDetails(mobileNumber: String, hour: Int = getCurrentIstHour()): OtpCalculationDetails? {
        val sanitized = mobileNumber.filter { it.isDigit() }
        if (sanitized.length < 4) return null
        val first4 = sanitized.take(4)
        val reversed = first4.reversed()
        val steps = mutableListOf<String>()
        val sb = StringBuilder()
        for (char in reversed) {
            val digit = char.digitToIntOrNull() ?: 0
            val sum = digit + hour
            val otpDigit = sum % 10
            steps.add("($digit + $hour) % 10 = $otpDigit")
            sb.append(otpDigit)
        }
        return OtpCalculationDetails(
            fullMobileNumber = sanitized,
            first4Digits = first4,
            reversedDigits = reversed,
            currentIstHour = hour,
            stepCalculations = steps,
            finalOtp = sb.toString(),
            isNetworkTimeSynced = NetworkTimeHelper.isTimeOffsetAvailable()
        )
    }

    /**
     * Shares the OTP message with the passenger via WhatsApp.
     */
    fun shareViaWhatsApp(context: Context, mobileNumber: String, otp: String) {
        val message = "Hello! Your taxi ride verification OTP is: $otp (Valid for the current hour). Please share this OTP with the driver to start your trip."
        try {
            val cleanPhone = if (mobileNumber.startsWith("91") || mobileNumber.startsWith("+91")) {
                mobileNumber.replace("+", "")
            } else {
                "91$mobileNumber"
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone&text=${Uri.encode(message)}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open WhatsApp: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Sends the OTP message to the passenger via SMS.
     */
    fun sendViaSms(context: Context, mobileNumber: String, otp: String) {
        val message = "Your taxi ride OTP is $otp. Share with driver to start ride."
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$mobileNumber")
                putExtra("sms_body", message)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open SMS: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Copies OTP to clipboard.
     */
    fun copyToClipboard(context: Context, otp: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Taxi Ride OTP", otp)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "OTP $otp copied to clipboard", Toast.LENGTH_SHORT).show()
    }
}
