package fr.dosejuste.app.security

import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Logique de fichiers et d'états de [DbKeyManager], avec un faux chiffreur logiciel
 * AES-GCM (le Keystore réel est testé en instrumenté).
 */
class DbKeyManagerTest {

    @get:Rule
    val dossier = TemporaryFolder()

    /** Chiffreur logiciel : même comportement GCM que le Keystore, clé en mémoire. */
    private class FauxChiffreur : ChiffreurCle {
        var cle: SecretKey? = null
        var generations = 0

        override fun cleExiste() = cle != null

        override fun genererCle() {
            generations++
            cle = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        }

        override fun chiffrer(clair: ByteArray): BlobChiffre {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, cle!!)
            return BlobChiffre(cipher.iv, cipher.doFinal(clair))
        }

        override fun dechiffrer(blob: BlobChiffre): ByteArray {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, cle!!, GCMParameterSpec(128, blob.iv))
            return cipher.doFinal(blob.donnees)
        }
    }

    private val chiffreur = FauxChiffreur()
    private val blob: File get() = File(dossier.root, "no_backup/${DbKeyManager.NOM_FICHIER_BLOB}")
    private val base: File get() = File(dossier.root, "databases/dosejuste.db")

    private fun gestionnaire(c: ChiffreurCle = chiffreur) = DbKeyManager(blob, base, c, SecureRandom())

    private fun creerBase(suffixe: String = "") {
        val fichier = File(base.path + suffixe)
        fichier.parentFile!!.mkdirs()
        fichier.writeBytes(byteArrayOf(1, 2, 3))
    }

    private fun contient(botte: ByteArray, aiguille: ByteArray): Boolean =
        (0..botte.size - aiguille.size).any { debut ->
            aiguille.indices.all { botte[debut + it] == aiguille[it] }
        }

    @Test
    fun premierLancementCreeCleEtBlobChiffre() {
        val phrase = gestionnaire().phraseDePasse()

        assertEquals(DbKeyManager.TAILLE_PHRASE, phrase.size)
        assertFalse("phrase nulle", phrase.all { it == 0.toByte() })
        assertEquals(1, chiffreur.generations)
        assertTrue(blob.isFile)
        assertFalse("fichier temporaire restant", File(blob.path + ".tmp").exists())
        val octets = blob.readBytes()
        assertEquals(DbKeyManager.VERSION_FORMAT, octets[0])
        assertEquals(12, octets[1].toInt())
        assertEquals(2 + 12 + 32 + 16, octets.size)
        assertFalse("phrase en clair dans le blob", contient(octets, phrase))
    }

    @Test
    fun relanceRelitLaMemePhraseSansNouvelleCle() {
        val premiere = gestionnaire().phraseDePasse()
        creerBase()
        val blobAvant = blob.readBytes()

        val seconde = gestionnaire().phraseDePasse()

        assertArrayEquals(premiere, seconde)
        assertEquals(1, chiffreur.generations)
        assertArrayEquals(blobAvant, blob.readBytes())
    }

    @Test
    fun deuxPremiersLancementsDistinctsDonnentDesPhrasesDifferentes() {
        val a = gestionnaire().phraseDePasse()
        val autreDossier = dossier.newFolder("autre")
        val b = DbKeyManager(File(autreDossier, "k.bin"), File(autreDossier, "x.db"), FauxChiffreur()).phraseDePasse()
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun blobAltereDonneErreurSansRienEcraser() {
        gestionnaire().phraseDePasse()
        creerBase()
        val octets = blob.readBytes()
        octets[octets.size - 1] = (octets[octets.size - 1].toInt() xor 0x01).toByte()
        blob.writeBytes(octets)
        val baseAvant = base.readBytes()

        val erreur = assertThrows(DonneesIllisiblesException.DechiffrementImpossible::class.java) {
            gestionnaire().phraseDePasse()
        }

        assertNotNull(erreur.cause)
        assertTrue(erreur.message!!.startsWith("Données illisibles"))
        assertArrayEquals(octets, blob.readBytes())
        assertArrayEquals(baseAvant, base.readBytes())
        assertEquals(1, chiffreur.generations)
    }

    @Test
    fun blobTronqueOuInconnuDonneBlobCorrompu() {
        gestionnaire().phraseDePasse()
        creerBase()
        for (contenu in listOf(byteArrayOf(), byteArrayOf(1), byteArrayOf(9, 12) + ByteArray(60), byteArrayOf(1, 12) + ByteArray(12))) {
            blob.writeBytes(contenu)
            assertThrows(DonneesIllisiblesException.BlobCorrompu::class.java) { gestionnaire().phraseDePasse() }
            assertArrayEquals(contenu, blob.readBytes())
        }
        assertEquals(1, chiffreur.generations)
    }

    @Test
    fun blobPresentMaisCleAbsenteDonneErreurSansRegenerer() {
        gestionnaire().phraseDePasse()
        creerBase()
        val blobAvant = blob.readBytes()
        chiffreur.cle = null

        assertThrows(DonneesIllisiblesException.CleKeystoreAbsente::class.java) { gestionnaire().phraseDePasse() }

        assertFalse("clé régénérée", chiffreur.cleExiste())
        assertEquals(1, chiffreur.generations)
        assertArrayEquals(blobAvant, blob.readBytes())
        assertTrue(base.isFile)
    }

    @Test
    fun cleRemplaceeDonneDechiffrementImpossible() {
        gestionnaire().phraseDePasse()
        chiffreur.genererCle() // autre clé sous le même alias
        assertThrows(DonneesIllisiblesException.DechiffrementImpossible::class.java) { gestionnaire().phraseDePasse() }
    }

    @Test
    fun baseSansBlobDonneErreurSansCreerDeCle() {
        for (suffixe in listOf("", "-wal", "-journal")) {
            dossier.root.listFiles()!!.forEach { it.deleteRecursively() }
            creerBase(suffixe)

            assertThrows(DonneesIllisiblesException.BlobAbsentBasePresente::class.java) {
                gestionnaire().phraseDePasse()
            }

            assertFalse(blob.exists())
            assertEquals(0, chiffreur.generations)
        }
    }

    @Test
    fun fichierTemporaireResiduelIgnore() {
        val temporaire = File(blob.path + ".tmp")
        temporaire.parentFile!!.mkdirs()
        temporaire.writeBytes(ByteArray(10))

        val phrase = gestionnaire().phraseDePasse()

        assertFalse(temporaire.exists())
        assertArrayEquals(phrase, gestionnaire().phraseDePasse())
    }

    @Test
    fun phraseDeTailleInattendueRefusee() {
        val chiffreurCourt = object : ChiffreurCle by chiffreur {
            override fun dechiffrer(blob: BlobChiffre): ByteArray = ByteArray(16)
        }
        gestionnaire().phraseDePasse()
        assertThrows(DonneesIllisiblesException.BlobCorrompu::class.java) { gestionnaire(chiffreurCourt).phraseDePasse() }
    }

    @Test
    fun formatEncoderDecoder() {
        val iv = ByteArray(12) { it.toByte() }
        val donnees = ByteArray(48) { (100 + it).toByte() }
        val decode = DbKeyManager.decoder(DbKeyManager.encoder(BlobChiffre(iv, donnees)))
        assertArrayEquals(iv, decode.iv)
        assertArrayEquals(donnees, decode.donnees)
    }
}
