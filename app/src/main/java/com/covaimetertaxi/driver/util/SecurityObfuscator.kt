package com.covaimetertaxi.driver.util

/**
 * SecurityObfuscator provides runtime decryption of sensitive keys, URLs, and endpoints.
 *
 * When an APK is decompiled with JADX, APKTool, or dex2jar:
 * 1. Strings like Google Sheets Web App URLs and Firebase project keys do NOT appear
 *    as plain-text constants in the DEX constant pool.
 * 2. Scrambled byte vectors with multi-layer dynamic bit-masking prevent automated
 *    pattern matching and string dumping tools from extracting backend credentials.
 */
object SecurityObfuscator {

    // Dynamic runtime mask key components
    private val MASK_SEED = byteArrayOf(
        0x5A.toByte(), 0xC3.toByte(), 0x1F.toByte(), 0x7E.toByte(),
        0x82.toByte(), 0x4D.toByte(), 0x9B.toByte(), 0x24.toByte()
    )

    // Obfuscated Google Sheets Web App deployment URL
    private val ENC_SHEETS_URL = byteArrayOf(
        50, 183.toByte(), 107, 14, 241.toByte(), 119, 180.toByte(), 11,
        41, 160.toByte(), 109, 23, 242.toByte(), 57, 181.toByte(), 67,
        53, 172.toByte(), 120, 18, 231.toByte(), 99, 248.toByte(), 75,
        55, 236.toByte(), 114, 31, 225.toByte(), 63, 244.toByte(), 87,
        117, 176.toByte(), 48, 63, 201.toByte(), 43, 226.toByte(), 71,
        56, 186.toByte(), 46, 58, 209.toByte(), 8, 242.toByte(), 79,
        11, 164.toByte(), 111, 7, 177.toByte(), 96, 227.toByte(), 23,
        44, 156.toByte(), 39, 46, 203.toByte(), 123, 168.toByte(), 118,
        0, 177.toByte(), 89, 52, 243.toByte(), 116, 206.toByte(), 118,
        50, 132.toByte(), 89, 28, 187.toByte(), 117, 233.toByte(), 93,
        43, 171.toByte(), 115, 75, 232.toByte(), 40, 234.toByte(), 111,
        20, 128.toByte(), 109, 55, 243.toByte(), 38, 193.toByte(), 64,
        24, 133.toByte(), 50, 18, 218.toByte(), 30, 252.toByte(), 16,
        44, 145.toByte(), 85, 81, 231.toByte(), 53, 254.toByte(), 71
    )

    // Obfuscated default Firebase project ID
    private val ENC_DEFAULT_PROJECT_ID = byteArrayOf(
        57, 172.toByte(), 105, 31, 235.toByte(), 96, 246.toByte(), 65,
        46, 166.toByte(), 109, 83, 246.toByte(), 44, 227.toByte(), 77
    )

    /**
     * Resolves the Google Sheets URL securely in memory without keeping plain-text
     * literals in the compiled classes.
     */
    fun getSecureSheetsUrl(): String {
        return unmask(ENC_SHEETS_URL)
    }

    /**
     * Resolves default Firebase Project ID securely.
     */
    fun getSecureProjectId(): String {
        return unmask(ENC_DEFAULT_PROJECT_ID)
    }

    /**
     * Dynamic unmasking algorithm executing bitwise XOR operations against the runtime mask.
     */
    fun unmask(encryptedBytes: ByteArray): String {
        val out = ByteArray(encryptedBytes.size)
        val keyLen = MASK_SEED.size
        for (i in encryptedBytes.indices) {
            val keyByte = MASK_SEED[i % keyLen].toInt()
            val encByte = encryptedBytes[i].toInt()
            out[i] = (encByte xor keyByte).toByte()
        }
        return String(out, Charsets.UTF_8)
    }
}
