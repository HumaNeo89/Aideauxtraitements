package fr.dosejuste.app.data

import fr.dosejuste.app.domain.StatutPrise
import fr.dosejuste.app.domain.Unite
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ConvertisseursTest {

    private val conv = Convertisseurs()

    @Test
    fun unitesAllerRetourParLeNom() {
        assertEquals(listOf("MG", "COMPRIME", "GOUTTE", "ML"), Unite.entries.map(conv::uniteVersTexte))
        for (unite in Unite.entries) {
            assertEquals(unite, conv.texteVersUnite(conv.uniteVersTexte(unite)))
        }
    }

    @Test
    fun uniteInconnueDonneUneErreurClaire() {
        for (texte in listOf("LITRE", "mg", "", "COMPRIMÉ")) {
            val erreur = assertThrows(ValeurStockeeInvalideException::class.java) { conv.texteVersUnite(texte) }
            assertTrue(erreur.message!!.startsWith("Unité inconnue en base"))
        }
    }

    @Test
    fun statutsAllerRetour() {
        assertEquals("CONFIRMEE", conv.statutVersTexte(StatutPrise.CONFIRMEE))
        assertEquals("ANNULEE", conv.statutVersTexte(StatutPrise.ANNULEE))
        for (statut in StatutPrise.entries) {
            assertEquals(statut, conv.texteVersStatut(conv.statutVersTexte(statut)))
        }
        assertThrows(ValeurStockeeInvalideException::class.java) { conv.texteVersStatut("SUPPRIMEE") }
    }

    @Test
    fun instantsEnMillisecondesUtc() {
        val instant = Instant.parse("2026-11-10T00:30:00.123Z")
        assertEquals(1_794_270_600_123L, conv.instantVersMillis(instant))
        assertEquals(instant, conv.millisVersInstant(conv.instantVersMillis(instant)))
        assertEquals(Instant.EPOCH, conv.millisVersInstant(0))
    }

    @Test
    fun journeesIso() {
        val journee = LocalDate.parse("2026-11-10")
        assertEquals("2026-11-10", conv.journeeVersTexte(journee))
        assertEquals(journee, conv.texteVersJournee("2026-11-10"))
        assertNull(conv.journeeVersTexte(null))
        assertNull(conv.texteVersJournee(null))
        for (texte in listOf("10/11/2026", "2026-13-01", "2026-02-30", "")) {
            assertThrows(ValeurStockeeInvalideException::class.java) { conv.texteVersJournee(texte) }
        }
    }
}
