package fr.dosejuste.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PriseTest {

    private val paris = ZoneId.of("Europe/Paris")
    private val calc = JourneeCalculator(paris, LocalTime.of(5, 0))
    private val hier = LocalDate.parse("2026-11-09")
    private val aujourdhui = LocalDate.parse("2026-11-10")

    private fun aParis(texte: String): Instant = LocalDateTime.parse(texte).atZone(paris).toInstant()

    private fun prise(
        id: String,
        heure: String,
        centiemes: Long,
        unite: Unite = Unite.MG,
        statut: StatutPrise = StatutPrise.CONFIRMEE,
        medicament: String = "Testoline",
    ) = Prise(id, medicament, Quantite(centiemes), unite, aParis(heure), statut)

    @Test
    fun reattributionChangeLaJourneeEtLeTotalSansToucherLHorodatage() {
        val nuit = prise("a", "2026-11-10T01:30", 25)
        val matin = prise("b", "2026-11-10T08:00", 50)
        val cle = CleTotal("Testoline", Unite.MG)

        assertEquals(hier, nuit.journeeEffective(calc))
        assertEquals(Quantite(50), totauxDuJour(listOf(nuit, matin), aujourdhui, calc)[cle])

        val deplacee = reattribuer(nuit, aujourdhui)
        assertEquals(nuit.horodatage, deplacee.horodatage)
        assertEquals(aujourdhui, deplacee.journeeEffective(calc))
        assertEquals(Quantite(75), totauxDuJour(listOf(deplacee, matin), aujourdhui, calc)[cle])
        assertNull(totauxDuJour(listOf(deplacee, matin), hier, calc)[cle])

        val retablie = annulerReattribution(deplacee)
        assertNull(retablie.journeeForcee)
        assertEquals(nuit, retablie)
        assertEquals(hier, retablie.journeeEffective(calc))
    }

    @Test
    fun changementDePivotRecalculeLaJournee() {
        val nuit = prise("a", "2026-11-10T01:30", 25)
        assertEquals(hier, nuit.journeeEffective(JourneeCalculator(paris, LocalTime.of(5, 0))))
        assertEquals(aujourdhui, nuit.journeeEffective(JourneeCalculator(paris, LocalTime.of(1, 0))))
    }

    @Test
    fun journeeForceeIgnoreLePivot() {
        val forcee = reattribuer(prise("a", "2026-11-10T01:30", 25), aujourdhui)
        assertEquals(aujourdhui, forcee.journeeEffective(JourneeCalculator(paris, LocalTime.of(5, 0))))
        assertEquals(aujourdhui, forcee.journeeEffective(JourneeCalculator(paris, LocalTime.of(0, 0))))
    }

    @Test
    fun prisesDuJourFiltreesEtTriees() {
        val p1 = prise("1", "2026-11-10T20:00", 100)
        val p2 = prise("2", "2026-11-10T06:00", 100, statut = StatutPrise.ANNULEE)
        val p3 = prise("3", "2026-11-11T04:59", 100)
        val p4 = prise("4", "2026-11-11T05:00", 100)
        val p5 = prise("5", "2026-11-10T04:00", 100)
        assertEquals(listOf(p2, p1, p3), prisesDuJour(listOf(p1, p2, p3, p4, p5), aujourdhui, calc))
    }

    @Test
    fun priseAnnuleeExclueDesTotaux() {
        val prises = listOf(
            prise("1", "2026-11-10T08:00", 40),
            prise("2", "2026-11-10T12:00", 25, statut = StatutPrise.ANNULEE),
            prise("3", "2026-11-10T18:00", 40),
        )
        assertEquals(mapOf(CleTotal("Testoline", Unite.MG) to Quantite(80)), totauxDuJour(prises, aujourdhui, calc))
    }

    @Test
    fun toutAnnuleDonneAucunTotal() {
        val prises = listOf(prise("1", "2026-11-10T08:00", 40, statut = StatutPrise.ANNULEE))
        assertTrue(totauxDuJour(prises, aujourdhui, calc).isEmpty())
    }

    @Test
    fun unitesDifferentesNonAdditionnees() {
        val prises = listOf(
            prise("1", "2026-11-10T08:00", 100, Unite.COMPRIME),
            prise("2", "2026-11-10T09:00", 50, Unite.MG),
            prise("3", "2026-11-10T10:00", 50, Unite.COMPRIME),
            prise("4", "2026-11-10T11:00", 300, Unite.GOUTTE),
        )
        assertEquals(
            mapOf(
                CleTotal("Testoline", Unite.COMPRIME) to Quantite(150),
                CleTotal("Testoline", Unite.MG) to Quantite(50),
                CleTotal("Testoline", Unite.GOUTTE) to Quantite(300),
            ),
            totauxDuJour(prises, aujourdhui, calc),
        )
    }

    @Test
    fun libelleVideRefuse() {
        assertThrows(IllegalArgumentException::class.java) { prise("1", "2026-11-10T08:00", 100, medicament = " ") }
    }
}
