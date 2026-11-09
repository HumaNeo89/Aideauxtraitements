package fr.dosejuste.app.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Invariant : journeeDe(i) == d ⇔ bornes(d).debut <= i < bornes(d).fin.
 * Aucun instant n'appartient à deux journées ni à aucune.
 */
class JourneeBalayageTest {

    private val pivots = listOf("00:00", "01:00", "02:00", "02:30", "03:00", "05:00", "23:00", "23:59")
        .map { LocalTime.parse(it) }

    private fun verifier(zone: ZoneId, debut: Instant, fin: Instant, pas: Duration) {
        for (pivot in pivots) {
            val c = JourneeCalculator(zone, pivot)
            var i = debut
            while (i < fin) {
                val d = c.journeeDe(i)
                val b = c.bornes(d)
                assertTrue("$zone pivot $pivot : $i hors de $b (journée $d)", b.contient(i))
                assertTrue("$zone pivot $pivot : $i aussi dans la veille", !c.bornes(d.minusDays(1)).contient(i))
                assertTrue("$zone pivot $pivot : $i aussi dans le lendemain", !c.bornes(d.plusDays(1)).contient(i))
                assertEquals("$zone pivot $pivot : journées non contiguës", b.fin, c.bornes(d.plusDays(1)).debut)
                i = i.plus(pas)
            }
        }
    }

    @Test
    fun parisDeMarsAFinOctobre2026ParQuartDHeure() {
        verifier(
            ZoneId.of("Europe/Paris"),
            Instant.parse("2026-03-01T00:00:00Z"),
            Instant.parse("2026-11-01T00:00:00Z"),
            Duration.ofMinutes(15),
        )
    }

    @Test
    fun parisAutourDesChangementsDHeureMinuteParMinute() {
        val zone = ZoneId.of("Europe/Paris")
        verifier(zone, Instant.parse("2026-03-27T00:00:00Z"), Instant.parse("2026-03-31T00:00:00Z"), Duration.ofMinutes(1))
        verifier(zone, Instant.parse("2026-10-23T00:00:00Z"), Instant.parse("2026-10-27T00:00:00Z"), Duration.ofMinutes(1))
    }

    @Test
    fun parisSecondesAutourDesSauts() {
        val zone = ZoneId.of("Europe/Paris")
        verifier(zone, Instant.parse("2026-03-29T00:50:00Z"), Instant.parse("2026-03-29T03:10:00Z"), Duration.ofSeconds(1))
        verifier(zone, Instant.parse("2026-10-25T00:20:00Z"), Instant.parse("2026-10-25T04:10:00Z"), Duration.ofSeconds(1))
    }

    @Test
    fun fuseauAvecSautAMinuit() {
        // Santiago du Chili : changements d'heure à minuit (le trou peut couvrir 00:00).
        verifier(
            ZoneId.of("America/Santiago"),
            Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2027-01-01T00:00:00Z"),
            Duration.ofMinutes(15),
        )
    }
}
