package com.covaimetertaxi.driver.util

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.MutableStateFlow

object LanguageManager {
    val currentLanguage = MutableStateFlow("EN")

    fun init(context: Context) {
        val prefs = context.getSharedPreferences("CovaiMeterTaxiPrefs", Context.MODE_PRIVATE)
        currentLanguage.value = prefs.getString("app_language", "EN") ?: "EN"
    }

    fun setLanguage(context: Context, lang: String) {
        currentLanguage.value = lang
        context.getSharedPreferences("CovaiMeterTaxiPrefs", Context.MODE_PRIVATE)
            .edit().putString("app_language", lang).apply()
    }
}

val String.tr: String
    @Composable
    get() {
        val lang = LanguageManager.currentLanguage.collectAsState().value
        if (lang == "EN") return this
        return TamilDictionary[this] ?: this
    }

fun String.trString(lang: String): String {
    if (lang == "EN") return this
    return TamilDictionary[this] ?: this
}

val TamilDictionary = mapOf(
    "HOME" to "முகப்பு",
    "HISTORY" to "வரலாறு",
    "SETTINGS" to "அமைப்புகள்",
    "REGULAR" to "வழக்கமான",
    "PACKAGE" to "பேக்கேஜ்",
    "SWIPE TO START TRIP" to "பயணத்தை தொடங்க ஸ்வைப் செய்யவும்",
    "SWIPE TO END TRIP" to "பயணத்தை முடிக்க ஸ்வைப் செய்யவும்",
    "Per Hour Fare (₹)" to "மணிநேர கட்டணம் (₹)",
    "KMS Fare (₹/KM)" to "கிலோமீட்டர் கட்டணம் (₹)",
    "Base Fare (₹)" to "அடிப்படை கட்டணம் (₹)",
    "Passenger Mobile Number" to "பயணிகளின் மொபைல் எண்",
    "Enter 10-digit mobile number" to "10 இலக்க மொபைல் எண்ணை உள்ளிடவும்",
    "Passenger Ride OTP" to "பயணிகளின் OTP",
    "Enter 4-digit OTP" to "4 இலக்க OTP ஐ உள்ளிடவும்",
    "Driver Name" to "ஓட்டுநர் பெயர்",
    "Vehicle Number" to "வாகன எண்",
    "Category" to "வகை",
    "Logout" to "வெளியேறு",
    "Call Office" to "அலுவலகத்தை அழை",
    "Registration No." to "பதிவு எண்",
    "Verified Account" to "சரிபார்க்கப்பட்ட கணக்கு",
    "TOTAL NET DUE" to "மொத்த தொகை",
    "Share Invoice" to "ரசீதை பகிர்",
    "Print Receipt" to "ரசீதை அச்சிடு",
    "CLEAR ALL" to "அனைத்தையும் அழி",
    "Clear All History?" to "அனைத்து வரலாற்றையும் அழிக்கவா?",
    "CANCEL" to "ரத்து செய்",
    "END TRIP" to "பயணத்தை முடி",
    "End Trip?" to "பயணத்தை முடிக்கவா?",
    "Are you sure you want to end and close the trip? This action cannot be undone." to "பயணத்தை முடித்து மூட விரும்புகிறீர்களா? இந்த செயலை செயல்தவிர்க்க முடியாது.",
    "Are you sure you want to permanently delete all trip records? This cannot be undone." to "அனைத்து பயண பதிவுகளையும் நிரந்தரமாக நீக்க விரும்புகிறீர்களா? இதை செயல்தவிர்க்க முடியாது.",
    "SAVE FARE" to "கட்டணத்தை சேமி",
    "VEHICLE CATEGORY" to "வாகன வகை",
    "RE-TAKE" to "மீண்டும் எடு",
    "ROTATE" to "சுழற்று",
    "RE-CHECK SETUP STATUS" to "அமைப்பை மீண்டும் சரிபார்க்கவும்",
    "OPEN APP SETTINGS PAGE" to "ஆப் அமைப்புகள் பக்கத்தை திற",
    "DISTANCE" to "தூரம்",
    "DURATION" to "கால அளவு",
    "WAITING" to "காத்திருப்பு",
    "FARE" to "கட்டணம்",
    "Trip Id" to "பயண ஐடி",
    "Start Timing" to "தொடக்க நேரம்",
    "End Timing" to "முடிவு நேரம்",
    "Base Fare Minimum" to "குறைந்தபட்ச அடிப்படை கட்டணம்",
    "Total Net Fare" to "மொத்த நிகர கட்டணம்",
    "Language" to "மொழி",
    "English" to "English",
    "Tamil (தமிழ்)" to "தமிழ் (Tamil)",
    "Call" to "அழை",
    "PICKUP LOCATION" to "புறப்படும் இடம்",
    "DROP LOCATION" to "சென்று சேரும் இடம்",
    "No billing logs currently saved" to "தற்போது எந்த பில்லிங் பதிவுகளும் சேமிக்கப்படவில்லை",
    "Mobile Number (10 Digits)" to "மொபைல் எண் (10 இலக்கங்கள்)",
    "e.g. TN 66 AB 1234" to "உதாரணம்: TN 66 AB 1234",
    "Driver name" to "ஓட்டுநர் பெயர்",
    "9876543210" to "9876543210",
    "+91 " to "+91 ",
    "Language Setting" to "மொழி அமைப்பு",
    "Select App Language" to "பயன்பாட்டு மொழியைத் தேர்ந்தெடுக்கவும்",
    "Vehicle Registration" to "வாகன பதிவு",
    "Package Active" to "பேக்கேஜ் செயலில் உள்ளது",
    "Package Base Fare" to "பேக்கேஜ் அடிப்படை கட்டணம்",
    "Standby Waiting Time" to "காத்திருப்பு நேரம்",
    "ACTIONS" to "செயல்கள்",
    "TRIPS RECORD JOURNAL" to "பயண பதிவு இதழ்",
    "0" to "0",
    "App Preferences" to "பயன்பாட்டு விருப்பங்கள்",
    "English (Default)" to "English (இயல்புநிலை)",
    "Tamil" to "தமிழ்",
    "Driver Login" to "ஓட்டுநர் உள்நுழைவு",
    "Driver ID / Number" to "ஓட்டுநர் ஐடி / எண்",
    "Driver ID" to "ஓட்டுநர் ஐடி",
    "PIN" to "ரகசிய எண் (PIN)",
    "Enter PIN" to "PIN ஐ உள்ளிடவும்",
    "LOGIN TO METER" to "மீட்டரில் உள்நுழைக",
    "Verifying..." to "சரிபார்க்கிறது...",
    "Enter Driver ID and PIN to login." to "உள்நுழைய ஓட்டுநர் ஐடி மற்றும் PIN ஐ உள்ளிடவும்.",
    "Enter your Driver ID and PIN to access the taxi meter." to "டாக்ஸி மீட்டரை அணுக உங்கள் ஓட்டுநர் ஐடி மற்றும் PIN ஐ உள்ளிடவும்.",
    "NEED HELP?" to "உதவி தேவையா?",
    "Office Support Helpline" to "அலுவலக உதவி எண்",
    "Office Call" to "அலுவலக அழைப்பு",
    "Live Algorithm Calculations" to "நேரடி வழிமுறை கணக்கீடுகள்",
    "Full Mobile Number" to "முழு கைபேசி எண்",
    "First 4 Digits Selected" to "முதல் 4 இலக்கங்கள்",
    "Reversed Digits" to "தலைகீழ் இலக்கங்கள்",
    "Current IST Hour" to "தற்போதைய IST மணிநேரம்",
    "Final Generated OTP" to "உருவாக்கப்பட்ட இறுதி OTP",
    "Share via WhatsApp" to "வாட்ஸ்அப் மூலம் பகிர்",
    "Send SMS" to "SMS அனுப்புக",
    "Copy OTP" to "OTP நகலெடு",
    "This Driver ID is already logged in on another device. Please log out from that device first or contact admin." to "இந்த ஓட்டுநர் ஐடி ஏற்கனவே வேறொரு சாதனத்தில் உள்நுழைந்துள்ளது. முதலில் அந்த சாதனத்திலிருந்து வெளியேறவும் அல்லது நிர்வாகியைத் தொடர்பு கொள்ளவும்.",
    "This Driver ID is already logged in on another device. Please log out from that device first." to "இந்த ஓட்டுநர் ஐடி ஏற்கனவே வேறொரு சாதனத்தில் உள்நுழைந்துள்ளது. முதலில் அந்த சாதனத்திலிருந்து வெளியேறவும்.",
    "Driver session expired or logged in on another device." to "ஓட்டுநர் அமர்வு காலாவதியானது அல்லது வேறொரு சாதனத்தில் உள்நுழைந்துள்ளது.",
    "Logged out successfully" to "வெற்றிகரமாக வெளியேற்றப்பட்டது"
)
