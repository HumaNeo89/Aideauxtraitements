package fr.dosejuste.app.security

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.ProviderException
import java.security.SecureRandom

/**
 * Fournit la phrase de passe SQLCipher de la base locale.
 *
 * **Principe.** Au premier lancement, une phrase de passe aléatoire de 32 octets
 * ([SecureRandom]) est générée, chiffrée par une clé AES-256-GCM du Keystore
 * ([ChiffreurCle]), et seul le résultat (IV + texte chiffré) est écrit dans [fichierBlob]
 * (dossier privé `no_backup` de l'appli). La phrase n'est jamais écrite en clair, ni en
 * SharedPreferences, ni dans un journal. Aux lancements suivants, le blob est relu et
 * déchiffré.
 *
 * **États** (seul le cas « ni blob ni base » crée une clé) :
 * | blob | base | clé Keystore | résultat |
 * |---|---|---|---|
 * | absent | absente | — | nouvelle clé, nouveau blob |
 * | présent | — | présente, déchiffrement OK | phrase relue |
 * | présent | — | absente | [DonneesIllisiblesException.CleKeystoreAbsente] |
 * | présent | — | déchiffrement refusé | [DonneesIllisiblesException.DechiffrementImpossible] |
 * | illisible (format) | — | — | [DonneesIllisiblesException.BlobCorrompu] |
 * | absent | présente | — | [DonneesIllisiblesException.BlobAbsentBasePresente] |
 *
 * Dans les cas d'erreur, rien n'est régénéré ni écrasé : ni le blob, ni la clé, ni la
 * base. L'interface affichera « données illisibles ».
 *
 * **Perte de clé = données perdues.** Il n'y a aucune sauvegarde en phase 1 : si la clé
 * du Keystore ou le blob disparaît (désinstallation, effacement des données, réinitialisation
 * du téléphone), la base chiffrée ne peut plus être ouverte. C'est le comportement attendu.
 *
 * **Mémoire.** Les copies intermédiaires sont effacées (`fill(0)`) dès que possible. Le
 * tableau retourné par [phraseDePasse] appartient à l'appelant ; SQLCipher le conserve tant
 * que la base est ouverte (il en a besoin pour rouvrir des connexions).
 */
class DbKeyManager(
    private val fichierBlob: File,
    private val fichierBase: File,
    private val chiffreur: ChiffreurCle,
    private val aleatoire: SecureRandom = SecureRandom(),
) {

    private val fichierTemporaire = File(fichierBlob.parentFile, fichierBlob.name + ".tmp")

    /**
     * Phrase de passe de la base (32 octets). Crée la clé au tout premier lancement.
     *
     * @throws DonneesIllisiblesException si la base existante ne peut plus être déverrouillée.
     */
    @Synchronized
    fun phraseDePasse(): ByteArray {
        // Reste d'une écriture interrompue : jamais utilisé, le blob est renommé atomiquement.
        if (fichierTemporaire.exists()) fichierTemporaire.delete()

        if (fichierBlob.exists()) return relire()
        if (baseExiste()) throw DonneesIllisiblesException.BlobAbsentBasePresente()
        return creer()
    }

    private fun baseExiste(): Boolean =
        SUFFIXES_BASE.any { File(fichierBase.path + it).exists() }

    private fun relire(): ByteArray {
        val blob = decoder(lireFichier())
        val cleExiste = try {
            chiffreur.cleExiste()
        } catch (e: GeneralSecurityException) {
            throw DonneesIllisiblesException.DechiffrementImpossible(e)
        } catch (e: ProviderException) {
            throw DonneesIllisiblesException.DechiffrementImpossible(e)
        }
        if (!cleExiste) throw DonneesIllisiblesException.CleKeystoreAbsente()
        val clair = try {
            chiffreur.dechiffrer(blob)
        } catch (e: GeneralSecurityException) {
            throw DonneesIllisiblesException.DechiffrementImpossible(e)
        } catch (e: ProviderException) {
            throw DonneesIllisiblesException.DechiffrementImpossible(e)
        }
        if (clair.size != TAILLE_PHRASE) {
            clair.fill(0)
            throw DonneesIllisiblesException.BlobCorrompu()
        }
        return clair
    }

    private fun creer(): ByteArray {
        chiffreur.genererCle()
        val phrase = ByteArray(TAILLE_PHRASE)
        aleatoire.nextBytes(phrase)
        try {
            val blob = chiffreur.chiffrer(phrase)
            // Vérifie l'aller-retour avant d'écrire, pour ne jamais persister un blob inutilisable.
            val verification = chiffreur.dechiffrer(blob)
            val identique = MessageDigest.isEqual(phrase, verification)
            verification.fill(0)
            check(identique) { "Vérification du chiffrement de la clé de base échouée" }
            ecrireAtomiquement(encoder(blob))
        } catch (e: Throwable) {
            phrase.fill(0)
            throw e
        }
        return phrase
    }

    private fun lireFichier(): ByteArray {
        val taille = fichierBlob.length()
        if (taille <= 0 || taille > TAILLE_FICHIER_PLAFOND) throw DonneesIllisiblesException.BlobCorrompu()
        return try {
            fichierBlob.readBytes()
        } catch (e: IOException) {
            throw DonneesIllisiblesException.BlobCorrompu(e)
        }
    }

    private fun ecrireAtomiquement(octets: ByteArray) {
        fichierBlob.parentFile?.mkdirs()
        FileOutputStream(fichierTemporaire).use { flux ->
            flux.write(octets)
            flux.fd.sync()
        }
        Files.move(fichierTemporaire.toPath(), fichierBlob.toPath(), StandardCopyOption.ATOMIC_MOVE)
    }

    companion object {
        /** Taille de la phrase de passe SQLCipher, en octets. */
        const val TAILLE_PHRASE = 32

        /** Nom du fichier qui contient IV + phrase chiffrée, dans `noBackupFilesDir`. */
        const val NOM_FICHIER_BLOB = "dosejuste_db_key.bin"

        internal const val VERSION_FORMAT: Byte = 1
        private const val TAILLE_FICHIER_PLAFOND = 4096L
        private val SUFFIXES_BASE = listOf("", "-wal", "-journal")

        /** Gestionnaire de production : Keystore, blob dans `noBackupFilesDir`, base [nomBase]. */
        fun pourContexte(context: Context, nomBase: String): DbKeyManager =
            DbKeyManager(
                fichierBlob = fichierBlob(context),
                fichierBase = context.getDatabasePath(nomBase),
                chiffreur = KeystoreChiffreur(),
            )

        /** Emplacement du blob : dossier privé jamais sauvegardé par Auto Backup. */
        fun fichierBlob(context: Context): File = File(context.noBackupFilesDir, NOM_FICHIER_BLOB)

        /** Format : version (1 octet), taille IV (1 octet), IV, texte chiffré. */
        internal fun encoder(blob: BlobChiffre): ByteArray {
            require(blob.iv.size in 1..255)
            return byteArrayOf(VERSION_FORMAT, blob.iv.size.toByte()) + blob.iv + blob.donnees
        }

        internal fun decoder(octets: ByteArray): BlobChiffre {
            if (octets.size < 2 || octets[0] != VERSION_FORMAT) throw DonneesIllisiblesException.BlobCorrompu()
            val tailleIv = octets[1].toInt() and 0xFF
            if (tailleIv !in 12..16 || octets.size <= 2 + tailleIv) throw DonneesIllisiblesException.BlobCorrompu()
            return BlobChiffre(
                iv = octets.copyOfRange(2, 2 + tailleIv),
                donnees = octets.copyOfRange(2 + tailleIv, octets.size),
            )
        }
    }
}

/**
 * La base existante ne peut pas être déverrouillée. Les données sont considérées comme
 * illisibles ; rien n'a été modifié sur le disque. Les messages ne contiennent aucun secret.
 */
sealed class DonneesIllisiblesException(message: String, cause: Throwable? = null) :
    Exception("Données illisibles : $message", cause) {

    /** Le blob existe mais la clé du Keystore a disparu. */
    class CleKeystoreAbsente : DonneesIllisiblesException("clé de chiffrement absente du Keystore")

    /** Le Keystore refuse de déchiffrer le blob (clé invalidée, blob altéré…). */
    class DechiffrementImpossible(cause: Throwable) :
        DonneesIllisiblesException("déchiffrement de la clé de base impossible", cause)

    /** Le fichier blob est tronqué ou d'un format inconnu. */
    class BlobCorrompu(cause: Throwable? = null) :
        DonneesIllisiblesException("fichier de clé corrompu", cause)

    /** Une base existe mais son fichier de clé a disparu. */
    class BlobAbsentBasePresente : DonneesIllisiblesException("fichier de clé absent alors que la base existe")
}
