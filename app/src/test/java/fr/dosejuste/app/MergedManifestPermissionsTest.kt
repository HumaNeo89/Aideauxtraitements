package fr.dosejuste.app

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Lit le manifeste fusionné de debug (application + dépendances).
 * Échoue si android.permission.INTERNET est présent, ou si une permission
 * hors liste autorisée est déclarée.
 *
 * Seule permission autorisée : la permission de signature qu'AndroidX
 * (androidx.core) ajoute pour ses receivers internes,
 * `<applicationId>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`.
 * Ce n'est pas une autorisation système et elle n'est pas demandée à la personne.
 */
class MergedManifestPermissionsTest {

    @Test
    fun mergedManifestDeclaresNoUnauthorizedPermission() {
        val manifest = locateMergedDebugManifest()
        val text = manifest.readText()
        val document = parse(manifest)
        val packageName = document.documentElement.getAttribute("package")
        val signaturePermission = "$packageName.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
        val declared = permissionNames(document)

        assertFalse(
            "android.permission.INTERNET est présent dans ${manifest.path}",
            text.contains("android.permission.INTERNET"),
        )
        assertEquals(
            "Permissions uses-permission inattendues dans ${manifest.path} : $declared. " +
                "Retirer la permission avec tools:node=\"remove\".",
            setOf(signaturePermission),
            declared.toSet(),
        )
        declared.forEach { name ->
            assertFalse(
                "Permission système non autorisée : $name",
                name.startsWith("android.permission."),
            )
        }

        val defined = document.getElementsByTagName("permission")
        assertEquals(1, defined.length)
        val definition = defined.item(0) as Element
        assertEquals(signaturePermission, attribute(definition, "name"))
        assertEquals("signature", attribute(definition, "protectionLevel"))
    }

    @Test
    fun backupIsDisabled() {
        val application = parse(locateMergedDebugManifest())
            .getElementsByTagName("application")
            .item(0) as Element

        assertEquals("false", attribute(application, "allowBackup"))
        assertTrue(
            "dataExtractionRules absent",
            attribute(application, "dataExtractionRules").contains("data_extraction_rules"),
        )
        assertTrue(
            "fullBackupContent absent",
            attribute(application, "fullBackupContent").contains("backup_rules"),
        )
    }

    private fun permissionNames(document: org.w3c.dom.Document): List<String> {
        val names = mutableListOf<String>()
        for (tag in listOf("uses-permission", "uses-permission-sdk-23")) {
            val nodes = document.getElementsByTagName(tag)
            for (index in 0 until nodes.length) {
                val element = nodes.item(index) as Element
                names += attribute(element, "name").ifBlank { "$tag sans android:name" }
            }
        }
        return names
    }

    private fun attribute(element: Element, localName: String): String {
        val prefixed = element.getAttribute("android:$localName")
        if (prefixed.isNotBlank()) {
            return prefixed
        }
        return element.getAttributeNS(ANDROID_NAMESPACE, localName)
    }

    private fun locateMergedDebugManifest(): File {
        val root = File("build")
        check(root.isDirectory) {
            "Répertoire build/ introuvable (cwd=${File(".").absolutePath}). " +
                "La tâche processDebugManifest doit avoir tourné."
        }
        val normalized = { file: File -> file.path.replace('\\', '/') }
        val candidates = root.walkTopDown()
            .filter { it.isFile && it.name == "AndroidManifest.xml" }
            .filter { file ->
                val path = normalized(file)
                "merged_manifest" in path && "/debug/" in path
            }
            .toList()
        val processed = candidates.filter { "/processDebugManifest/" in normalized(it) }
        val chosen = when {
            processed.size == 1 -> processed.single()
            candidates.size == 1 -> candidates.single()
            else -> error(
                "Manifeste fusionné debug introuvable ou ambigu : " +
                    candidates.map { it.path },
            )
        }
        return chosen
    }

    private fun parse(file: File): org.w3c.dom.Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        return factory.newDocumentBuilder().parse(file)
    }

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    }
}
