package fr.dosejuste.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Garde-fous de la build release : rien de secret dans le dépôt, R8 activé. */
class ReleaseGardeFousTest {

    private val racine: File = listOf(File("."), File(".."))
        .map { it.absoluteFile.normalize() }
        .firstOrNull { File(it, "settings.gradle.kts").isFile }
        ?: error("Racine du dépôt introuvable")

    private fun lire(chemin: String): String = File(racine, chemin).also {
        assertTrue("Fichier absent : $chemin", it.isFile)
    }.readText()

    @Test
    fun gitignoreProtegeLesSecrets() {
        val lignes = lire(".gitignore").lines().map { it.trim() }
        for (motif in listOf("keystore.properties", "*.jks", "*.keystore")) {
            assertTrue("$motif absent de .gitignore", motif in lignes)
        }
        assertTrue(lignes.none { it.contains("keystore.properties.example") || it == "keystore.properties*" })
    }

    @Test
    fun exempleNeContientQueDesValeursFictives() {
        val valeurs = lire("keystore.properties.example").lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .associate { it.substringBefore("=") to it.substringAfter("=") }
        assertEquals(setOf("storeFile", "storePassword", "keyAlias", "keyPassword"), valeurs.keys)
        assertTrue(valeurs.getValue("storePassword").startsWith("A_REMPLIR"))
        assertTrue(valeurs.getValue("keyPassword").startsWith("A_REMPLIR"))
        assertTrue(!valeurs.getValue("storeFile").contains('\\'))
    }

    // keystore.properties est local et protégé par .gitignore (vérifié par gitignoreProtegeLesSecrets) :
    // il n'est donc pas cherché ici, seuls les fichiers de clé (jks, keystore) le sont.
    @Test
    fun aucunKeystoreDansLArbre() {
        val trouves = racine.walk()
            .onEnter { it.name != "build" && it.name != ".git" && it.name != ".gradle" && it.name != ".kotlin" }
            .filter { it.isFile }
            .filter { it.extension == "jks" || it.extension == "keystore" }
            .map { it.relativeTo(racine).path }
            .toList()
        assertTrue("Fichiers de clé dans l'arbre : $trouves", trouves.isEmpty())
    }

    @Test
    fun releaseMinifieEtSansMotDePasseEnClair() {
        val script = lire("app/build.gradle.kts")
        assertTrue(script.contains("isMinifyEnabled = true"))
        assertTrue(!script.contains("isMinifyEnabled = false"))
        val litteral = Regex("""(storePassword|keyPassword)\s*=\s*"[^"]""")
        assertTrue("Mot de passe littéral dans build.gradle.kts", !litteral.containsMatchIn(script))
    }
}
