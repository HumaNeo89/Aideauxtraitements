package fr.dosejuste.app.domain

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Garde-fous sur les sources du domaine : JVM pur, aucun vocabulaire de conseil de dose. */
class DomaineSourcesTest {

    private val interdits = listOf(
        "restant", "maximum", "max", "limite", "peut encore", "recommandé", "recommande", "dose conseillée",
    )

    private fun sources(): List<File> {
        val relatif = "src/main/java/fr/dosejuste/app/domain"
        val dossier = listOf(File(relatif), File("app/$relatif")).firstOrNull { it.isDirectory }
            ?: error("Dossier introuvable : $relatif")
        return dossier.walk().filter { it.isFile && it.extension == "kt" }.toList()
    }

    @Test
    fun aucunVocabulaireDeConseil() {
        val fichiers = sources()
        assertTrue(fichiers.isNotEmpty())
        for (fichier in fichiers) {
            val texte = fichier.readText().lowercase()
            for (mot in interdits) {
                assertTrue("« $mot » trouvé dans ${fichier.name}", !texte.contains(mot))
            }
        }
    }

    @Test
    fun aucuneDependanceAndroid() {
        for (fichier in sources()) {
            assertTrue("android.* importé dans ${fichier.name}", !fichier.readText().contains("import android."))
        }
    }
}
