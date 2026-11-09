package fr.dosejuste.app

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Garde-fous sur data/ et security/ (domain/ a son propre test, plus strict).
 *
 * Même vocabulaire de conseil interdit que pour domain/, sauf « max », trop courant
 * dans les API Android et SQL pour être cherché ici sans faux positifs.
 */
class SourcesGardeFousTest {

    private val interdits = listOf(
        "restant", "maximum", "limite", "peut encore", "recommandé", "recommande", "dose conseillée",
    )

    private fun sources(sousDossier: String): List<File> {
        val relatif = "src/main/java/fr/dosejuste/app/$sousDossier"
        val dossier = listOf(File(relatif), File("app/$relatif")).firstOrNull { it.isDirectory }
            ?: error("Dossier introuvable : $relatif")
        return dossier.walk().filter { it.isFile && it.extension == "kt" }.toList()
    }

    private val couches get() = sources("data") + sources("security") + sources("ui")

    @Test
    fun aucunVocabulaireDeConseil() {
        assertTrue(couches.isNotEmpty())
        for (fichier in couches) {
            val texte = fichier.readText().lowercase()
            for (mot in interdits) {
                assertFalse("« $mot » trouvé dans ${fichier.name}", texte.contains(mot))
            }
        }
    }

    @Test
    fun aucunJournalNiReseauNiMigrationDestructive() {
        val toutes = sources("")
        for (fichier in toutes) {
            val texte = fichier.readText()
            assertFalse("android.util.Log dans ${fichier.name}", texte.contains("android.util.Log"))
            assertFalse("Log. dans ${fichier.name}", Regex("""\bLog\.[a-z]+\(""").containsMatchIn(texte))
            assertFalse("println dans ${fichier.name}", texte.contains("println("))
            assertFalse("printStackTrace dans ${fichier.name}", texte.contains("printStackTrace"))
            assertFalse("migration destructive dans ${fichier.name}", texte.contains("fallbackToDestructiveMigration"))
            assertFalse("réseau dans ${fichier.name}", texte.contains("java.net.") || texte.contains("HttpURLConnection"))
        }
    }

    @Test
    fun phraseDePasseJamaisEnPreferences() {
        for (fichier in couches) {
            val texte = fichier.readText()
            assertFalse("SharedPreferences dans ${fichier.name}", texte.contains("getSharedPreferences"))
            assertFalse("DataStore dans ${fichier.name}", texte.contains("androidx.datastore"))
            assertFalse("toString de phrase dans ${fichier.name}", texte.contains("phrase.toString"))
        }
    }
}
