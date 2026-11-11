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

        /**
         * Exceptions EXPLICITES du balayage E5, retirées du texte avant la recherche :
         * - la phrase d'avertissement dit qu'il n'y a « aucun conseil de dose » (négation, pas un conseil) ;
         * - « Saisie trop grande » : message de validation d'une faute de frappe (voir Quantite.PLAFOND_SAISIE).
         * Toute autre occurrence fait échouer le test.
         */
        val EXCEPTIONS_AFFICHEES = listOf("aucun conseil de dose", "saisie trop grande")
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

    // ---- E5 : chaînes affichées (res/values*/strings.xml) et littéraux Kotlin de ui/ ----

    private val motsInterditsAffiches = listOf(
        "maximum", "max", "encore", "recommandé", "conseillé", "restant", "reste à", "limite",
        "dépasse", "autorisé", "vous pouvez prendre", "dose suggérée", "objectif",
    )

    /** Minuscules, sans accents, apostrophes typographiques ramenées à « ' ». */
    private fun normaliser(texte: String): String =
        java.text.Normalizer.normalize(texte.lowercase().replace('’', '\''), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")

    /** Mots interdits trouvés dans [texte]. « max » : mot entier seulement ; les autres : début de mot. */
    private fun interditsDans(texte: String): List<String> {
        val propre = EXCEPTIONS_AFFICHEES.fold(normaliser(texte)) { acc, phrase -> acc.replace(normaliser(phrase), " ") }
        return motsInterditsAffiches.filter { mot ->
            val m = Regex.escape(normaliser(mot))
            val fin = if (mot == "max") "(?![\\p{L}\\p{N}_])" else ""
            Regex("(?<![\\p{L}\\p{N}_])$m$fin").containsMatchIn(propre)
        }
    }

    private fun dossier(relatif: String): File =
        listOf(File(relatif), File("app/$relatif")).firstOrNull { it.isDirectory } ?: error("Introuvable : $relatif")

    // Contenu de chaque balise string de tous les fichiers strings.xml des dossiers res/values (et variantes).
    private fun chainesXml(): List<Pair<String, String>> =
        dossier("src/main/res").listFiles { f -> f.isDirectory && f.name.startsWith("values") }!!
            .map { File(it, "strings.xml") }
            .filter { it.isFile }
            .flatMap { fichier ->
                val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(fichier)
                val noeuds = doc.getElementsByTagName("string")
                (0 until noeuds.length).map { i ->
                    val n = noeuds.item(i)
                    "${fichier.parentFile!!.name}/${(n as org.w3c.dom.Element).getAttribute("name")}" to n.textContent
                }
            }

    /** Littéraux "..." des sources Kotlin de ui/ (commentaires retirés). */
    private fun litterauxKotlin(): List<Pair<String, String>> =
        sourcesUi().flatMap { f ->
            val code = f.readText().replace(Regex("(?s)/\\*.*?\\*/"), "").replace(Regex("(?m)//.*$"), "")
            Regex("\"((?:[^\"\\\\\\n]|\\\\.)*)\"").findAll(code).map { f.name to it.groupValues[1] }.toList()
        }

    @Test
    fun aucuneChaineAfficheeNeContientDeMotInterdit() {
        val xml = chainesXml()
        val kotlin = litterauxKotlin()
        assertTrue("strings.xml introuvable ou vide", xml.size > 40)
        assertTrue("aucun littéral trouvé dans ui/", kotlin.size > 20)
        for ((origine, texte) in xml + kotlin) {
            val trouves = interditsDans(texte)
            assertTrue("$trouves dans $origine : « $texte »", trouves.isEmpty())
        }
    }

    @Test
    fun leDetecteurReconnaitLesFormulationsInterdites() {
        for (mauvais in listOf(
            "Dose maximum atteinte", "Max 3 par jour", "Encore 2 mg", "Dose recommandée", "Dose conseillée",
            "Quantité restante", "Il vous reste à prendre 1 mg", "Limite du jour", "Vous dépassez", "Objectif du jour",
            "Dose autorisée", "Vous pouvez prendre 1 comprimé", "Dose suggérée", "ENCORE", "reste a prendre",
        )) {
            assertTrue(mauvais, interditsDans(mauvais).isNotEmpty())
        }
        for (bon in listOf(
            "Journal personnel, sans garantie. Ce n'est pas un avis médical. Cette application ne donne aucun conseil de dose.",
            "Saisie trop grande. Vérifiez le nombre.", "Testoline : 2 mg", "fillMaxWidth", "Heure pivot : 5 h",
            "Une prise entre minuit et l'heure pivot compte pour la journée précédente.",
        )) {
            assertTrue(bon, interditsDans(bon).isEmpty())
        }
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
