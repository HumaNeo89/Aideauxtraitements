package fr.dosejuste.app.data

import fr.dosejuste.app.domain.Prise
import fr.dosejuste.app.domain.Quantite
import fr.dosejuste.app.domain.StatutPrise
import fr.dosejuste.app.domain.Unite
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class MappingTest {

    private val horodatage = Instant.parse("2026-11-10T07:00:00Z")
    private val maintenant = Instant.parse("2026-11-10T07:01:00Z")

    private fun ligne(
        centiemes: Long,
        unite: Unite = Unite.MG,
        statut: StatutPrise = StatutPrise.CONFIRMEE,
        journeeForcee: LocalDate? = null,
        libelle: String = "Testoline",
    ) = PriseAvecMedicament(
        PriseEntity(
            id = 7,
            medicamentId = 3,
            quantiteCentiemes = centiemes,
            unite = unite,
            horodatage = horodatage,
            statut = statut,
            journeeForcee = journeeForcee,
            creeLe = maintenant,
            majLe = maintenant,
        ),
        libelle,
    )

    @Test
    fun quantitesEtUnitesVersLeDomaine() {
        val cas = listOf(
            25L to Unite.MG,
            40L to Unite.COMPRIME,
            100L to Unite.GOUTTE,
            25L to Unite.ML,
        )
        for ((centiemes, unite) in cas) {
            assertEquals(
                Prise("7", "Testoline", Quantite(centiemes), unite, horodatage),
                ligne(centiemes, unite).versDomaine(),
            )
        }
        assertEquals("0,25", ligne(25).versDomaine().quantite.formater())
        assertEquals("0,4", ligne(40).versDomaine().quantite.formater())
        assertEquals("1", ligne(100).versDomaine().quantite.formater())
    }

    @Test
    fun statutEtJourneeForceeConserves() {
        val journee = LocalDate.parse("2026-11-09")
        val prise = ligne(40, statut = StatutPrise.ANNULEE, journeeForcee = journee, libelle = "Testoline B")
            .versDomaine()
        assertEquals(StatutPrise.ANNULEE, prise.statut)
        assertEquals(journee, prise.journeeForcee)
        assertEquals("Testoline B", prise.medicament)
        assertEquals(horodatage, prise.horodatage)
    }

    @Test
    fun domaineVersEntite() {
        val entite = nouvellePriseEntity(3, Quantite(25), Unite.COMPRIME, horodatage, maintenant)
        assertEquals(0L, entite.id)
        assertEquals(3L, entite.medicamentId)
        assertEquals(25L, entite.quantiteCentiemes)
        assertEquals(Unite.COMPRIME, entite.unite)
        assertEquals(horodatage, entite.horodatage)
        assertEquals(StatutPrise.CONFIRMEE, entite.statut)
        assertNull(entite.journeeForcee)
        assertEquals(maintenant, entite.creeLe)
        assertEquals(maintenant, entite.majLe)
    }

    @Test
    fun allerRetourEntiteDomaine() {
        val entite = nouvellePriseEntity(3, Quantite(100), Unite.ML, horodatage, maintenant).copy(id = 12)
        val prise = PriseAvecMedicament(entite, "Testoline").versDomaine()
        assertEquals(12L, prise.idBase())
        assertEquals(Quantite(100), prise.quantite)
        assertEquals(Unite.ML, prise.unite)
    }

    @Test
    fun valeursInvalidesLuesRefusees() {
        assertThrows(ValeurStockeeInvalideException::class.java) { ligne(0).versDomaine() }
        assertThrows(ValeurStockeeInvalideException::class.java) { ligne(-25).versDomaine() }
        assertThrows(ValeurStockeeInvalideException::class.java) { ligne(25, libelle = " ").versDomaine() }
    }

    @Test
    fun identifiantNonNumeriqueRefuse() {
        val prise = Prise("a", "Testoline", Quantite(25), Unite.MG, horodatage)
        assertThrows(IllegalArgumentException::class.java) { prise.idBase() }
    }
}
