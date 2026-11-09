package fr.dosejuste.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import fr.dosejuste.app.data.DoseJusteDatabase
import fr.dosejuste.app.prefs.ReglagesPrefs
import fr.dosejuste.app.security.DbKeyManager
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.w3c.dom.Element

/**
 * La base (domaine « database ») et le blob de clé (dossier `no_backup`, domaine « root »,
 * jamais copié par Auto Backup) sont exclus de la sauvegarde cloud et du transfert
 * d'appareil. Complète BackupRulesTest et MergedManifestPermissionsTest.
 */
@RunWith(RobolectricTestRunner::class)
class EmplacementsSauvegardeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun blobDansNoBackupEtBaseDansDatabases() {
        val blob = DbKeyManager.fichierBlob(context)
        assertEquals(context.noBackupFilesDir.canonicalFile, blob.parentFile!!.canonicalFile)
        assertEquals("no_backup", blob.parentFile!!.name)

        val base = context.getDatabasePath(DoseJusteDatabase.NOM_FICHIER)
        assertEquals("dosejuste.db", base.name)
        assertEquals("databases", base.parentFile!!.name)
        assertEquals(context.dataDir.canonicalFile, base.parentFile!!.parentFile!!.canonicalFile)
        assertEquals(context.dataDir.canonicalFile, blob.parentFile!!.parentFile!!.canonicalFile)
    }

    @Test
    fun preferencesDansSharedPrefsDomaineExclu() {
        ReglagesPrefs(context).definirPivotHeure(3)
        val fichier = File(context.dataDir, "shared_prefs/${ReglagesPrefs.NOM_FICHIER}.xml")
        assertTrue("fichier de préférences attendu : $fichier", fichier.isFile)
        assertEquals("shared_prefs", fichier.parentFile!!.name)
        assertEquals(context.dataDir.canonicalFile, fichier.parentFile!!.parentFile!!.canonicalFile)
        // Ce dossier est le domaine « sharedpref » : exclu (cloud, transfert, ancien format).
        val extraction = parse("src/main/res/xml/data_extraction_rules.xml")
        for (section in listOf("cloud-backup", "device-transfer")) {
            val element = extraction.getElementsByTagName(section).item(0) as Element
            assertEquals(".", exclusDe(element)["sharedpref"])
        }
        assertEquals(".", exclusDe(parse("src/main/res/xml/backup_rules.xml").documentElement)["sharedpref"])
        // Seule ReglagesPrefs ouvre des SharedPreferences (une seule porte, un seul nom de fichier).
        val racine = listOf(File("src/main/java/fr/dosejuste/app"), File("app/src/main/java/fr/dosejuste/app"))
            .first { it.isDirectory }
        val sources = racine.walk().filter { it.extension == "kt" }
            .filter { it.readText().contains("getSharedPreferences") }.map { it.name }.toList()
        assertEquals(listOf("Reglages.kt"), sources)
    }

    private fun exclusDe(parent: Element): Map<String, String> {
        val noeuds = parent.getElementsByTagName("exclude")
        return (0 until noeuds.length).associate {
            val e = noeuds.item(it) as Element
            e.getAttribute("domain") to e.getAttribute("path")
        }
    }

    @Test
    fun reglesExcluentBaseEtBlob() {
        val extraction = parse("src/main/res/xml/data_extraction_rules.xml")
        for (section in listOf("cloud-backup", "device-transfer")) {
            val element = extraction.getElementsByTagName(section).item(0) as Element
            assertCouvre(element, section)
        }
        assertCouvre(parse("src/main/res/xml/backup_rules.xml").documentElement, "full-backup-content")
    }

    private fun assertCouvre(parent: Element, libelle: String) {
        val exclus = mutableMapOf<String, String>()
        val noeuds = parent.getElementsByTagName("exclude")
        for (i in 0 until noeuds.length) {
            val e = noeuds.item(i) as Element
            exclus[e.getAttribute("domain")] = e.getAttribute("path")
        }
        assertEquals(0, parent.getElementsByTagName("include").length)
        // Base : domaine database entier. Blob : no_backup est sous root (et jamais sauvegardé).
        // Préférences (heure pivot, avertissement accepté) : domaine sharedpref.
        for (domaine in listOf("database", "root", "file", "sharedpref")) {
            assertEquals("$libelle : domaine $domaine", ".", exclus[domaine])
        }
    }

    @Test
    fun manifesteSansSauvegarde() {
        val manifeste = fichier("src/main/AndroidManifest.xml").readText()
        assertTrue(manifeste.contains("android:allowBackup=\"false\""))
        assertTrue(manifeste.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""))
        assertTrue(manifeste.contains("android:fullBackupContent=\"@xml/backup_rules\""))
    }

    private fun parse(relatif: String) =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(fichier(relatif))

    private fun fichier(relatif: String): File =
        listOf(File(relatif), File("app/$relatif")).firstOrNull { it.isFile }
            ?: error("Fichier introuvable : $relatif")
}
