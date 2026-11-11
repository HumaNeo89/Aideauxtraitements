package fr.dosejuste.app.ui

import fr.dosejuste.app.domain.Quantite
import fr.dosejuste.app.domain.Unite
import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhrasePriseEtLicencesTest {

    @Test
    fun totalEtJourEnFrancais() {
        assertEquals("Testoline : 2 mg", PhrasePrise.total("Testoline", Quantite(200), Unite.MG))
        assertEquals("Exemple : 0,5 comprimé", PhrasePrise.total("Exemple", Quantite(50), Unite.COMPRIME))
        assertEquals("Testoline B : 2,25 gouttes", PhrasePrise.total("Testoline B", Quantite(225), Unite.GOUTTE))
        assertEquals("mardi 10 novembre 2026", PhrasePrise.jour(LocalDate.parse("2026-11-10")))
    }

    @Test
    fun laCasseDuLibelleEstConserveeDansLesPhrases() {
        val q = Quantite(200)
        assertEquals("2 mg de Testoline", PhrasePrise.quantiteEtMedicament(q, Unite.MG, "Testoline"))
        assertEquals("2 mg de testoline", PhrasePrise.quantiteEtMedicament(q, Unite.MG, "testoline"))
        assertEquals("2 mg d'EXEMPLE", PhrasePrise.quantiteEtMedicament(q, Unite.MG, "EXEMPLE"))
        assertEquals(
            "2 mg de Testoline à 14:05",
            PhrasePrise.relecture(q, Unite.MG, "Testoline", JourSaisie.AUJOURDHUI, java.time.LocalTime.of(14, 5)),
        )
    }

    private fun assets(): File =
        listOf(File("src/main/assets/licences"), File("app/src/main/assets/licences")).first { it.isDirectory }

    @Test
    fun chaqueNoticeADeLaMatiereEtLesFichiersExistent() {
        for (notice in Licences.notices) {
            val fichier = notice.fichierTexte ?: continue
            assertTrue("$fichier manquant", File(assets(), fichier).isFile)
        }
        assertEquals(setOf("sqlcipher.txt", "libtomcrypt.txt", "apache-2.0.txt"), Licences.notices.mapNotNull { it.fichierTexte }.toSet())
    }

    @Test
    fun sqlcipherEtLibtomcryptEtApacheSontComplets() {
        val sqlcipher = File(assets(), "sqlcipher.txt").readText()
        assertTrue(sqlcipher.contains("ZETETIC LLC"))
        assertTrue(sqlcipher.contains("Redistribution and use in source and binary forms"))
        assertTrue(sqlcipher.contains("SUCH DAMAGE"))
        assertTrue(File(assets(), "libtomcrypt.txt").readText().contains("unlicense.org"))
        val apache = File(assets(), "apache-2.0.txt").readText()
        assertTrue(apache.contains("Apache License"))
        assertTrue(apache.contains("limitations under the License"))
    }

    @Test
    fun listeCiteLesComposantsAttendus() {
        val titres = Licences.notices.joinToString("\n") { it.titre }
        for (attendu in listOf("SQLCipher", "4.20.0", "LibTomCrypt", "SQLite", "AndroidX", "Kotlin", "kotlinx.coroutines")) {
            assertTrue(attendu, titres.contains(attendu))
        }
        assertEquals(6, Licences.notices.size)
        assertTrue(Licences.notices.any { it.titre.contains("AndroidX") && it.titre.contains("androidx.sqlite") })
        assertFalse(Licences.notices.first { it.titre.contains("AndroidX") }.titre.contains("SQLite"))
        // 3 boutons : SQLCipher, LibTomCrypt, groupe Apache.
        assertEquals(3, Licences.notices.mapNotNull { it.fichierTexte }.toSet().size)
        // SQLite : une seule ligne « Domaine public ».
        val sqlite = Licences.notices.first { it.titre == "SQLite" }
        assertEquals(1, listOf(sqlite.copyright, sqlite.licence).count { it.contains("Domaine public") })
        assertTrue(Licences.notices.first().copyright.contains("2008-2023 ZETETIC LLC"))
    }
}
