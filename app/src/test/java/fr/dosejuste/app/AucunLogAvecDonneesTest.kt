package fr.dosejuste.app

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Aucun journal dans app/src/main : un médicament, une quantité ou un texte saisi ne doivent
 * jamais atteindre logcat, la sortie standard ou une trace d'exception imprimée.
 * Le test lit les sources en texte brut, commentaires compris : ne pas citer ces appels.
 */
class AucunLogAvecDonneesTest {

    private val motifs = listOf(
        "android.util.Log" to Regex("""android\.util\.Log"""),
        "Log." to Regex("""(?<![\p{L}\p{N}_])Log\."""),
        "println(" to Regex("""println\s*\("""),
        "printStackTrace(" to Regex("""printStackTrace\s*\("""),
        "Timber" to Regex("""Timber"""),
    )

    private fun fautes(texte: String): List<String> = motifs.filter { it.second.containsMatchIn(texte) }.map { it.first }

    private fun main(): File =
        listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }

    @Test
    fun aucunLogDansLesSourcesDeProduction() {
        val sources = main().walk().filter { it.isFile && it.extension in setOf("kt", "java", "xml") }.toList()
        assertTrue("sources introuvables", sources.size > 20)
        for (f in sources) {
            val trouves = fautes(f.readText())
            assertTrue("${f.path} contient ${trouves}", trouves.isEmpty())
        }
    }

    @Test
    fun leDetecteurReconnaitLesAppelsInterdits() {
        for (mauvais in listOf(
            "import android.util.Log", "Log.d(\"x\", y)", "  println(valeur)", "e.printStackTrace()", "Timber.d(x)",
            "println (x)",
        )) {
            assertFalse(mauvais, fautes(mauvais).isEmpty())
        }
        for (bon in listOf("AlertDialog.Builder", "dialog.dismiss()", "val catalogue = 1", "Dialogue")) {
            assertTrue(bon, fautes(bon).isEmpty())
        }
    }
}
