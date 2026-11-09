package fr.dosejuste.app.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * [ChiffreurCle] adossé à l'Android Keystore : clé AES-256-GCM non exportable.
 *
 * - StrongBox (puce de sécurité dédiée, Titan M2 sur Pixel 7 Pro) demandé en premier,
 *   repli sur le Keystore matériel standard (TEE) si StrongBox est indisponible.
 * - IV imposé aléatoire par le Keystore (setRandomizedEncryptionRequired).
 * - Pas d'authentification utilisateur (pas d'empreinte en phase 1) : la clé n'est donc
 *   pas invalidée par un changement de code de verrouillage.
 * - La clé est effacée par le système à la désinstallation et à l'effacement des
 *   données de l'appli : la base devient alors irrécupérable (voir [DbKeyManager]).
 */
class KeystoreChiffreur(private val alias: String = ALIAS_PAR_DEFAUT) : ChiffreurCle {

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    override fun cleExiste(): Boolean = keyStore().containsAlias(alias)

    override fun genererCle() {
        val ks = keyStore()
        if (ks.containsAlias(alias)) ks.deleteEntry(alias)
        try {
            generer(strongBox = true)
        } catch (_: StrongBoxUnavailableException) {
            generer(strongBox = false)
        }
    }

    private fun generer(strongBox: Boolean) {
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(false)
            .setIsStrongBoxBacked(strongBox)
            .build()
        val generateur = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generateur.init(spec)
        generateur.generateKey()
    }

    private fun cle(): SecretKey =
        keyStore().getKey(alias, null) as? SecretKey
            ?: throw java.security.UnrecoverableKeyException("Clé Keystore absente")

    override fun chiffrer(clair: ByteArray): BlobChiffre {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, cle())
        val donnees = cipher.doFinal(clair)
        return BlobChiffre(cipher.iv, donnees)
    }

    override fun dechiffrer(blob: BlobChiffre): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, cle(), GCMParameterSpec(TAG_BITS, blob.iv))
        return cipher.doFinal(blob.donnees)
    }

    /** Supprime la clé (tests instrumentés uniquement, avec un alias de test). */
    fun supprimerCle() {
        val ks = keyStore()
        if (ks.containsAlias(alias)) ks.deleteEntry(alias)
    }

    companion object {
        /** Alias de la clé qui protège la phrase de passe de la base. */
        const val ALIAS_PAR_DEFAUT = "dosejuste_db_kek_v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_BITS = 128
    }
}
