package fr.dosejuste.app

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Les règles d'extraction et d'Auto Backup excluent chaque domaine de données.
 */
class BackupRulesTest {

    @Test
    fun dataExtractionRulesExcludeEveryDomain() {
        val document = parse(resource("src/main/res/xml/data_extraction_rules.xml"))
        assertNoInclude(document)
        for (section in listOf("cloud-backup", "device-transfer")) {
            val nodes = document.getElementsByTagName(section)
            assertEquals("Section $section", 1, nodes.length)
            assertExcludesEverything(nodes.item(0) as Element, section)
        }
    }

    @Test
    fun legacyFullBackupExcludesEveryDomain() {
        val document = parse(resource("src/main/res/xml/backup_rules.xml"))
        assertEquals("full-backup-content", document.documentElement.tagName)
        assertNoInclude(document)
        assertExcludesEverything(document.documentElement, "full-backup-content")
    }

    private fun assertExcludesEverything(parent: Element, label: String) {
        val excludes = parent.getElementsByTagName("exclude")
        assertEquals("$label : nombre d'exclusions", DOMAINS.size, excludes.length)
        val found = mutableSetOf<String>()
        for (index in 0 until excludes.length) {
            val exclude = excludes.item(index) as Element
            val domain = exclude.getAttribute("domain")
            val path = exclude.getAttribute("path")
            assertEquals("$label domaine $domain", ".", path)
            found += domain
        }
        assertEquals(DOMAINS, found)
    }

    private fun assertNoInclude(document: org.w3c.dom.Document) {
        assertEquals(0, document.getElementsByTagName("include").length)
    }

    private fun resource(relative: String): File {
        val direct = File(relative)
        if (direct.isFile) {
            return direct
        }
        val fromRepoRoot = File("app/$relative")
        if (fromRepoRoot.isFile) {
            return fromRepoRoot
        }
        error("Fichier introuvable : $relative (cwd=${File(".").absolutePath})")
    }

    private fun parse(file: File): org.w3c.dom.Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        return factory.newDocumentBuilder().parse(file)
    }

    private companion object {
        val DOMAINS = setOf("root", "file", "database", "sharedpref", "external")
    }
}
