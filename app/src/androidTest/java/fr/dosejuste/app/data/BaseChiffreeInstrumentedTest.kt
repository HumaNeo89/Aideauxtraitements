package fr.dosejuste.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.dosejuste.app.domain.Quantite
import fr.dosejuste.app.domain.Unite
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Base SQLCipher réelle (bibliothèque native) sur le téléphone. Fichier de test distinct
 * de la base de l'appli ; phrase de passe de test aléatoire, générée à chaque test.
 */
@RunWith(AndroidJUnit4::class)
class BaseChiffreeInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val nom = "test-chiffree-${UUID.randomUUID()}.db"
    private val phrase = ByteArray(32).also { SecureRandom().nextBytes(it) }

    @After
    fun nettoyer() {
        context.deleteDatabase(nom)
        phrase.fill(0)
    }

    private fun remplir(base: DoseJusteDatabase): Long = runBlocking {
        val depot = PrisesRepository(base)
        val med = depot.ajouterMedicament("Testoline")
        depot.enregistrerPrise(med, Quantite(25), Unite.MG, Instant.parse("2026-11-10T07:00:00Z"))
    }

    @Test
    fun fichierSansEnTeteSqliteEnClair() {
        val base = BaseChiffree.construire(context, nom, phrase)
        remplir(base)
        base.close()

        val fichier = context.getDatabasePath(nom)
        assertTrue(fichier.isFile)
        val entete = fichier.inputStream().use { it.readNBytes(16) }
        assertEquals(16, entete.size)
        assertFalse(
            "en-tête SQLite en clair",
            entete.contentEquals("SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)),
        )
        val contenu = fichier.readBytes()
        val libelle = "Testoline".toByteArray(Charsets.UTF_8)
        val trouve = (0..contenu.size - libelle.size).any { i -> libelle.indices.all { contenu[i + it] == libelle[it] } }
        assertFalse("libellé lisible en clair dans le fichier", trouve)
    }

    @Test
    fun reouvertureAvecLaMemeCle() {
        val premiere = BaseChiffree.construire(context, nom, phrase)
        val id = remplir(premiere)
        premiere.close()

        val seconde = BaseChiffree.construire(context, nom, phrase)
        try {
            val prise = runBlocking { PrisesRepository(seconde).prise(id) }!!
            assertEquals("Testoline", prise.medicament)
            assertEquals(Quantite(25), prise.quantite)
            assertEquals(Unite.MG, prise.unite)
        } finally {
            seconde.close()
        }
    }

    @Test
    fun mauvaisePhraseDePasseEchoue() {
        val base = BaseChiffree.construire(context, nom, phrase)
        remplir(base)
        base.close()
        val tailleAvant = context.getDatabasePath(nom).length()

        val autre = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val mauvaise = BaseChiffree.construire(context, nom, autre)
        val resultat = runCatching { mauvaise.openHelper.writableDatabase }
        runCatching { mauvaise.close() }
        autre.fill(0)

        assertTrue("ouverture avec une mauvaise phrase acceptée", resultat.isFailure)
        assertEquals(tailleAvant, context.getDatabasePath(nom).length())

        // La bonne phrase ouvre toujours la base : rien n'a été écrasé.
        val bonne = BaseChiffree.construire(context, nom, phrase)
        try {
            assertEquals(1, runBlocking { bonne.medicamentDao().lister() }.size)
        } finally {
            bonne.close()
        }
    }
}
