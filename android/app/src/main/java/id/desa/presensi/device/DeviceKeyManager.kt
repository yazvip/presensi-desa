package id.desa.presensi.device

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * Kunci perangkat: EC P-256 di Android Keystore.
 *
 * Sengaja TIDAK memakai setUserAuthenticationRequired(true): mengganti PIN layar kunci
 * akan menghapus kunci dan mengunci petugas di luar aplikasi.
 */
class DeviceKeyManager(private val alias: String = "presensi_device_key_v1") {

    var strongbox: Boolean = false
        private set

    /** Buat kunci bila belum ada, lalu kembalikan public key-nya. */
    fun ensureKey(): PublicKey? {
        loadPublicKey()?.let { return it }
        // StrongBox tidak ada di semua perangkat (dan bisa error di OEM tertentu);
        // bila gagal, kunci TEE biasa tetap sah dipakai.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            generate(useStrongBox = true)?.let { return it }
        }
        return generate(useStrongBox = false)
    }

    private fun generate(useStrongBox: Boolean): PublicKey? {
        return try {
            val gen = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE)
            val builder = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
            if (useStrongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) builder.setIsStrongBoxBacked(true)
            gen.initialize(builder.build())
            val pair = gen.generateKeyPair()
            strongbox = useStrongBox
            pair.public
        } catch (e: Exception) {
            null
        }
    }

    private fun keyStore(): KeyStore =
        KeyStore.getInstance(KEYSTORE).apply { load(null) }

    fun loadPublicKey(): PublicKey? {
        return try {
            keyStore().getCertificate(alias)?.publicKey
        } catch (e: Exception) {
            null
        }
    }

    /** Android mengirim SubjectPublicKeyInfo (DER) sebagai base64 — cocok dengan backend. */
    fun publicKeyBase64(): String? =
        loadPublicKey()?.encoded?.let { Base64.encodeToString(it, Base64.NO_WRAP) }

    fun isBound(): Boolean = loadPublicKey() != null

    /** Tanda tangani nonce. Hasilnya DER ECDSA dalam base64. */
    fun sign(data: String): String? {
        return try {
            val private = keyStore().getKey(alias, null) as PrivateKey
            val sig = Signature.getInstance("SHA256withECDSA")
            sig.initSign(private)
            sig.update(data.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(sig.sign(), Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    fun deleteKey() {
        try {
            keyStore().deleteEntry(alias)
        } catch (e: Exception) {
            // kunci memang sudah tidak ada
        }
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
    }
}
