package fr.dosejuste.app.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class JourneeCalculatorTest {

    private val paris = ZoneId.of("Europe/Paris")

    /** Instant à Paris, un jour sans changement d'heure. */
    private fun aParis(texte: String): Instant = LocalDateTime.parse(texte).atZone(paris).toInstant()

    private fun calc(pivot: String) = JourneeCalculator(paris, LocalTime.parse(pivot))

    private fun utc(texte: String): Instant = Instant.parse(texte)

    private fun date(texte: String): LocalDate = LocalDate.parse(texte)

    @Test
    fun valeursParDefaut() {
        val c = JourneeCalculator()
        assertEquals(paris, c.zone)
        assertEquals(LocalTime.of(5, 0), c.pivot)
    }

    @Test
    fun pivotCinqHeures() {
        val c = calc("05:00")
        assertEquals(date("2026-11-09"), c.journeeDe(aParis("2026-11-10T00:59")))
        assertEquals(date("2026-11-09"), c.journeeDe(aParis("2026-11-10T04:59")))
        assertEquals(date("2026-11-09"), c.journeeDe(aParis("2026-11-10T04:59:59.999")))
        assertEquals(date("2026-11-10"), c.journeeDe(aParis("2026-11-10T05:00")))
        assertEquals(date("2026-11-10"), c.journeeDe(aParis("2026-11-10T23:59")))
    }

    @Test
    fun pivotMinuit() {
        val c = calc("00:00")
        assertEquals(date("2026-11-10"), c.journeeDe(aParis("2026-11-10T00:00")))
        assertEquals(date("2026-11-10"), c.journeeDe(aParis("2026-11-10T23:59")))
        assertEquals(date("2026-11-09"), c.journeeDe(aParis("2026-11-09T23:59:59")))
    }

    @Test
    fun pivotVingtTroisHeures() {
        // La journée D va du jour D à 23:00 au jour D+1 à 22:59:59.
        val c = calc("23:00")
        assertEquals(date("2026-11-09"), c.journeeDe(aParis("2026-11-10T22:59")))
        assertEquals(date("2026-11-10"), c.journeeDe(aParis("2026-11-10T23:00")))
        assertEquals(
            IntervalleJournee(aParis("2026-11-10T23:00"), aParis("2026-11-11T23:00")),
            c.bornes(date("2026-11-10")),
        )
    }

    @Test
    fun pivotAvecMinutes() {
        val c = calc("01:30")
        assertEquals(date("2026-11-09"), c.journeeDe(aParis("2026-11-10T01:29")))
        assertEquals(date("2026-11-10"), c.journeeDe(aParis("2026-11-10T01:30")))
    }

    @Test
    fun pivotAvecSecondesRefuse() {
        assertThrows(IllegalArgumentException::class.java) {
            JourneeCalculator(paris, LocalTime.of(5, 0, 30))
        }
    }

    @Test
    fun fuseauInjectable() {
        val c = JourneeCalculator(ZoneId.of("UTC"), LocalTime.of(5, 0))
        assertEquals(date("2026-11-09"), c.journeeDe(utc("2026-11-10T04:59:00Z")))
        assertEquals(date("2026-11-10"), c.journeeDe(utc("2026-11-10T05:00:00Z")))
    }

    @Test
    fun journeeNormaleDureVingtQuatreHeures() {
        val c = calc("05:00")
        val b = c.bornes(date("2026-11-10"))
        assertEquals(utc("2026-11-10T04:00:00Z"), b.debut)
        assertEquals(utc("2026-11-11T04:00:00Z"), b.fin)
        assertEquals(Duration.ofHours(24), b.duree)
    }

    // --- Passage à l'heure d'été : 29/03/2026, 02:00 CET → 03:00 CEST (01:00Z) ---

    @Test
    fun heureEtePivotCinqHeures() {
        val c = calc("05:00")
        val b28 = c.bornes(date("2026-03-28"))
        assertEquals(utc("2026-03-28T04:00:00Z"), b28.debut)
        assertEquals(utc("2026-03-29T03:00:00Z"), b28.fin)
        assertEquals(Duration.ofHours(23), b28.duree)
        assertEquals(Duration.ofHours(24), c.bornes(date("2026-03-29")).duree)

        assertEquals(date("2026-03-28"), c.journeeDe(utc("2026-03-29T00:59:59Z"))) // 01:59:59 CET
        assertEquals(date("2026-03-28"), c.journeeDe(utc("2026-03-29T01:00:00Z"))) // 03:00 CEST
        assertEquals(date("2026-03-28"), c.journeeDe(utc("2026-03-29T02:59:59Z"))) // 04:59:59 CEST
        assertEquals(date("2026-03-29"), c.journeeDe(utc("2026-03-29T03:00:00Z"))) // 05:00 CEST
    }

    @Test
    fun heureEtePivotDansLeTrou() {
        // 02:30 n'existe pas le 29/03 : la journée commence au saut (03:00 CEST = 01:00Z).
        val c = calc("02:30")
        val b28 = c.bornes(date("2026-03-28"))
        val b29 = c.bornes(date("2026-03-29"))
        assertEquals(utc("2026-03-28T01:30:00Z"), b28.debut)
        assertEquals(utc("2026-03-29T01:00:00Z"), b28.fin)
        assertEquals(b28.fin, b29.debut)
        assertEquals(utc("2026-03-30T00:30:00Z"), b29.fin)
        assertEquals(Duration.ofMinutes(23 * 60 + 30), b28.duree)
        assertEquals(Duration.ofMinutes(23 * 60 + 30), b29.duree)

        assertEquals(date("2026-03-28"), c.journeeDe(utc("2026-03-29T00:59:59Z"))) // 01:59:59 CET
        assertEquals(date("2026-03-29"), c.journeeDe(utc("2026-03-29T01:00:00Z"))) // 03:00 CEST
        assertEquals(date("2026-03-29"), c.journeeDe(utc("2026-03-29T01:15:00Z"))) // 03:15 CEST
    }

    // --- Retour à l'heure d'hiver : 25/10/2026, 03:00 CEST → 02:00 CET (01:00Z) ---

    @Test
    fun heureHiverPivotCinqHeures() {
        val c = calc("05:00")
        val b24 = c.bornes(date("2026-10-24"))
        assertEquals(utc("2026-10-24T03:00:00Z"), b24.debut)
        assertEquals(utc("2026-10-25T04:00:00Z"), b24.fin)
        assertEquals(Duration.ofHours(25), b24.duree)
        assertEquals(Duration.ofHours(24), c.bornes(date("2026-10-25")).duree)

        assertEquals(date("2026-10-24"), c.journeeDe(utc("2026-10-25T00:30:00Z"))) // 02:30 CEST
        assertEquals(date("2026-10-24"), c.journeeDe(utc("2026-10-25T01:30:00Z"))) // 02:30 CET
        assertEquals(date("2026-10-24"), c.journeeDe(utc("2026-10-25T03:59:59Z"))) // 04:59:59 CET
        assertEquals(date("2026-10-25"), c.journeeDe(utc("2026-10-25T04:00:00Z"))) // 05:00 CET
    }

    @Test
    fun heureHiverPivotDansLeChevauchement() {
        // 02:30 existe deux fois : la journée commence à la première (02:30 CEST = 00:30Z).
        val c = calc("02:30")
        val b24 = c.bornes(date("2026-10-24"))
        val b25 = c.bornes(date("2026-10-25"))
        assertEquals(utc("2026-10-25T00:30:00Z"), b24.fin)
        assertEquals(b24.fin, b25.debut)
        assertEquals(utc("2026-10-26T01:30:00Z"), b25.fin)
        assertEquals(Duration.ofHours(24), b24.duree)
        assertEquals(Duration.ofHours(25), b25.duree)

        assertEquals(date("2026-10-24"), c.journeeDe(utc("2026-10-25T00:29:59Z"))) // 02:29:59 CEST
        assertEquals(date("2026-10-25"), c.journeeDe(utc("2026-10-25T00:30:00Z"))) // 02:30 CEST
        // Seconde occurrence de 02:00–02:29 (CET) : on ne revient pas à la veille.
        assertEquals(date("2026-10-25"), c.journeeDe(utc("2026-10-25T01:00:00Z"))) // 02:00 CET
        assertEquals(date("2026-10-25"), c.journeeDe(utc("2026-10-25T01:29:59Z"))) // 02:29:59 CET
        assertEquals(date("2026-10-25"), c.journeeDe(utc("2026-10-25T01:30:00Z"))) // 02:30 CET
    }
}
