package com.pollywog.teams

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import java.util.Base64

interface EncryptionProvider {
    fun encrypt(stringToEncrypt: String): String
    fun decrypt(stringToDecrypt: String): String
}

class AESEncryptionProvider(
    private val encryptionSecret: String,
    private val decryptionSecret: String,
) : EncryptionProvider {
    override fun encrypt(stringToEncrypt: String): String {
        val cipher: Cipher = Cipher.getInstance("AES/ECB/PKCS5PADDING")
        val secretKey = SecretKeySpec(encryptionSecret.toByteArray(), "AES")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        return Base64.getEncoder().encodeToString(cipher.doFinal(stringToEncrypt.toByteArray()))
    }

    override fun decrypt(stringToDecrypt: String): String {
        val cipher: Cipher = Cipher.getInstance("AES/ECB/PKCS5PADDING")
        val secretKey = SecretKeySpec(decryptionSecret.toByteArray(), "AES")
        println(decryptionSecret)
        println(stringToDecrypt)
        cipher.init(Cipher.DECRYPT_MODE, secretKey)
        return String(cipher.doFinal(Base64.getDecoder().decode(stringToDecrypt)))
    }
}

