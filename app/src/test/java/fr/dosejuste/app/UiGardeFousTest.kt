package fr.dosejuste.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Garde-fous sur les textes affichés (strings.xml) et sur ui/ : aucune phrase de conseil,
 * de limite ou de comparaison de dose.
 *
 * Le mot « trop » est interdit, sauf dans l'unique message de validation « Saisie trop
 * grande » (la quantité saisie dépasse la garde contre les fautes de frappe, voir
 * `Quantite.PLAFOND_SAISIE`). Cette exception est EXPLICITE : [PHRASES_AUTORISEES] est
 * retirée du texte avant la recherche, tout autre « trop » (mot isolé) fait échouer le
 * test. Les identifiants Kotlin comme TROP_GRAND ne sont pas des mots isolés (« _ »).
 */
class UiGardeFousTest {

    private val interdits = listOf(
        "restant", "maximum", "limite", "peut encore", "recommandé", "recommande", "dose conseillée",
        "objectif", "dépass",
    )

    private companion object {
        val PHRASES_AUTORISEES = listOf("saisie trop grande")
    }

    private fun fichier(relatif: String): File =
        listOf(File(relatif), File("app/$relatif")).firstOrNull { it.isFile } ?: error("Introuvable : $relatif")

    private fun sourcesUi(): List<File> {
        val relatif = "src/main/java/fr/dosejuste/app/ui"
        val dossier = listOf(File(relatif), File("app/$relatif")).firstOrNull { it.isDirectory }
            ?: error("Dossier introuvable : $relatif")
        return dossier.walk().filter { it.isFile && it.extension == "kt" }.toList()
    }

    private fun textes(): List<Pair<String, String>> =
        sourcesUi().map { it.name to it.readText().lowercase() } +
            ("strings.xml" to fichier("src/main/res/values/strings.xml").readText().lowercase())

    @Test
    fun aucunVocabulaireDeConseil() {
        assertTrue(sourcesUi().isNotEmpty())
        for ((nom, texte) in textes()) {
            for (mot in interdits) {
                assertFalse("« $mot » trouvé dans $nom", texte.contains(mot))
            }
        }
    }

    @Test
    fun motTropSeulementDansLeMessageDeValidation() {
        val mot = Regex("""\btrop\b""")
        for ((nom, texte) in textes()) {
            val sansExceptions = PHRASES_AUTORISEES.fold(texte) { acc, phrase -> acc.replace(phrase, "") }
            assertFalse("« trop » hors message autorisé dans $nom", mot.containsMatchIn(sansExceptions))
        }
    }

    @Test
    fun lExceptionTropEstBienLeMessageDeValidation() {
        val xml = fichier("src/main/res/values/strings.xml").readText()
        assertEquals(1, Regex("(?i)saisie trop grande").findAll(xml).count())
        assertTrue(xml.contains("""name="erreur_quantite_grande">Saisie trop grande."""))
    }

    @Test
    fun aucunePermissionNiReseauDansUi() {
        for (f in sourcesUi()) {
            val texte = f.readText()
            assertFalse("INTERNET dans ${f.name}", texte.contains("INTERNET"))
            assertFalse("log dans ${f.name}", texte.contains("android.util.Log"))
        }
    }
}
