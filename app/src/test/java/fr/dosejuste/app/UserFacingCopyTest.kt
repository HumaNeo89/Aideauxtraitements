package fr.dosejuste.app

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class UserFacingCopyTest {

    @Test
    fun screenCopyNamesTheAppAndRefusesDoseAdvice() {
        val xml = resource("src/main/res/values/strings.xml").readText()
        assertTrue(xml.contains("Dose Juste"))
        assertTrue(xml.contains("aucun conseil médical ni de dose"))
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
}
