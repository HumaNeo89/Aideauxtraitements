package fr.dosejuste.app.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.dosejuste.app.data.BaseChiffree
import fr.dosejuste.app.data.DoseJusteDatabase
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DbKeyManager avec le vrai Android Keystore. Alias et fichiers propres au test : la clé
 * et la base de l'appli ne sont jamais touchées.
 */
@RunWith(AndroidJUnit4::class)
class DbKeyManagerInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val suffixe = UUID.randomUUID().toString()
    private val alias = "dosejuste_test_kek_$suffixe"
    private val dossier = File(context.noBackupFilesDir, "test-$suffixe")
    private val blob = File(dossier, "cle.bin")
    private val nomBase = "test-cle-$suffixe.db"

    private fun gestionnaire() =
        DbKeyManager(blob, context.getDatabasePath(nomBase), KeystoreChiffreur(alias))

    @After
    fun nettoyer() {
        KeystoreChiffreur(alias).supprimerCle()
        dossier.deleteRecursively()
        context.deleteDatabase(nomBase)
    }

    private fun contient(botte: ByteArray, aiguille: ByteArray): Boolean =
        (0..botte.size - aiguille.size).any { i -> aiguille.indices.all { botte[i + it] == aiguille[it] } }

    @Test
    fun phraseStableEntreDeuxInstancesEtBlobSansPhraseEnClair() {
        val premiere = gestionnaire().phraseDePasse()
        val seconde = gestionnaire().phraseDePasse()

        assertEquals(DbKeyManager.TAILLE_PHRASE, premiere.size)
        assertArrayEquals(premiere, seconde)
        assertTrue(KeystoreChiffreur(alias).cleExiste())

        val octets = blob.readBytes()
        assertFalse("phrase en clair dans le blob", contient(octets, premiere))
        assertFalse(contient(octets, premiere.copyOfRange(0, 8)))
        premiere.fill(0)
        seconde.fill(0)
    }

    @Test
    fun chaineCompleteCleEtBase() {
        val phrase = gestionnaire().phraseDePasse()
        val base = BaseChiffree.construire(context, nomBase, phrase)
        runBlocking { fr.dosejuste.app.data.PrisesRepository(base).ajouterMedicament("Testoline") }
        base.close()
        phrase.fill(0)

        val relue = gestionnaire().phraseDePasse()
        val rouverte = BaseChiffree.construire(context, nomBase, relue)
        try {
            assertEquals(listOf("Testoline"), runBlocking { rouverte.medicamentDao().lister() }.map { it.libelle })
        } finally {
            rouverte.close()
            relue.fill(0)
        }
    }

    @Test
    fun cleSupprimeeDonneErreurSansEcraser() {
        gestionnaire().phraseDePasse().fill(0)
        val avant = blob.readBytes()
        KeystoreChiffreur(alias).supprimerCle()

        assertThrows(DonneesIllisiblesException.CleKeystoreAbsente::class.java) { gestionnaire().phraseDePasse() }
        assertArrayEquals(avant, blob.readBytes())
        assertFalse(KeystoreChiffreur(alias).cleExiste())
    }

    @Test
    fun blobAltereRefuseParLeKeystore() {
        gestionnaire().phraseDePasse().fill(0)
        val octets = blob.readBytes()
        octets[octets.size - 1] = (octets[octets.size - 1].toInt() xor 0x01).toByte()
        blob.writeBytes(octets)

        assertThrows(DonneesIllisiblesException.DechiffrementImpossible::class.java) { gestionnaire().phraseDePasse() }
        assertArrayEquals(octets, blob.readBytes())
    }

    @Test
    fun aliasDeProductionInchange() {
        // Le test ne doit jamais utiliser la clé de l'appli.
        assertFalse(alias == KeystoreChiffreur.ALIAS_PAR_DEFAUT)
        assertFalse(nomBase == DoseJusteDatabase.NOM_FICHIER)
    }
}
