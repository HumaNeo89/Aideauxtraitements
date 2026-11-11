package fr.dosejuste.app.ui

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import fr.dosejuste.app.data.DoseJusteDatabase
import fr.dosejuste.app.data.PrisesRepository
import fr.dosejuste.app.data.configurationCommune
import fr.dosejuste.app.domain.CleTotal
import fr.dosejuste.app.domain.JourneeCalculator
import fr.dosejuste.app.domain.Quantite
import fr.dosejuste.app.domain.StatutPrise
import fr.dosejuste.app.domain.Unite
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * ViewModels + vraie base Room en mémoire NON chiffrée (comme en E3) : l'annulation est
 * persistée, la prise annulée sort des totaux, la réattribution déplace la prise d'une
 * journée à l'autre et le libellé garde sa casse jusqu'à la liste et au total.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SaisieIntegrationTest {

    private val paris = ZoneId.of("Europe/Paris")
    private val horloge = Clock.fixed(LocalDateTime.parse("2026-11-10T14:05:30").atZone(paris).toInstant(), paris)
    private val calc = JourneeCalculator()
    private val aujourdhui = LocalDate.parse("2026-11-10")

    private lateinit var base: DoseJusteDatabase
    private lateinit var depot: PrisesRepository

    @Before
    fun ouvrir() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        base = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DoseJusteDatabase::class.java)
            .allowMainThreadQueries()
            .configurationCommune()
            .build()
        depot = PrisesRepository(base, horloge)
    }

    @After
    fun fermer() {
        base.close()
        Dispatchers.resetMain()
    }

    private fun attendre(vm: SaisieViewModel, condition: (SaisieUiState) -> Boolean): SaisieUiState =
        runBlocking { withTimeout(10_000) { vm.etat.first(condition) } }

    private fun attendreJour(vm: JourneeViewModel, condition: (JourneeUiState) -> Boolean): JourneeUiState =
        runBlocking { withTimeout(10_000) { vm.etat.first(condition) } }

    @Test
    fun annulationPersisteeEtExclueDesTotaux() {
        val journal = JournalRoom(depot, base)
        val vm = SaisieViewModel(journal, horloge, calc)
        val jour = JourneeViewModel(journal, FauxReglages(), horloge)
        vm.ouvrirFormulaire()
        vm.saisirLibelle("Testoline")
        vm.ajouterMedicament()
        attendre(vm) { it.formulaire?.medicamentId != null }
        vm.saisirQuantite("2")
        vm.relire()
        assertEquals("2 mg de Testoline à 14:05", vm.etat.value.relecture!!.phrase)
        vm.enregistrer()
        val id = attendre(vm) { it.annulationRapide != null }.annulationRapide!!.priseId

        // Lue en base : confirmée, comptée dans le total ; casse conservée dans la liste et le total.
        val cle = CleTotal("Testoline", Unite.MG)
        assertEquals(StatutPrise.CONFIRMEE, runBlocking { depot.prise(id) }!!.statut)
        assertEquals(mapOf(cle to Quantite(200)), runBlocking { depot.observerTotauxDuJour(aujourdhui, calc).first() })
        val avant = attendreJour(jour) { it.prises.size == 1 }
        assertEquals(listOf("Testoline : 2 mg"), avant.totaux)
        assertEquals("14:05 — 2 mg de Testoline", avant.prises.single().texte)

        vm.annulerRapide()
        val apres = attendreJour(jour) { it.prises.singleOrNull()?.annulee == true }

        // Persistée : la ligne reste, le statut est ANNULEE, le total est vide.
        val relue = runBlocking { depot.prise(id) }!!
        assertEquals(StatutPrise.ANNULEE, relue.statut)
        assertEquals(Quantite(200), relue.quantite)
        assertEquals(1, runBlocking { depot.prisesDuJour(aujourdhui, calc) }.size)
        assertTrue(apres.totaux.isEmpty())
        assertTrue(runBlocking { depot.observerTotauxDuJour(aujourdhui, calc).first() }.isEmpty())
    }

    @Test
    fun annulationDepuisLaListe() {
        val journal = JournalRoom(depot, base)
        val id = runBlocking {
            val med = depot.ajouterMedicament("Testoline B")
            depot.enregistrerPrise(med, Quantite(40), Unite.COMPRIME, horloge.instant().minusSeconds(3600))
        }
        val jour = JourneeViewModel(journal, FauxReglages(), horloge)
        val ligne = attendreJour(jour) { it.prises.size == 1 }.prises.single()
        assertEquals("13:05 — 0,4 comprimé de Testoline B", ligne.texte)
        jour.demanderAnnulation(ligne.id)
        jour.confirmerAnnulation()
        attendreJour(jour) { it.prises.singleOrNull()?.annulee == true }
        assertEquals(StatutPrise.ANNULEE, runBlocking { depot.prise(id) }!!.statut)
        assertTrue(runBlocking { depot.observerTotauxDuJour(aujourdhui, calc).first() }.isEmpty())
    }

    @Test
    fun reattributionDeplaceLaPriseEtLesTotauxDesDeuxJournees() {
        val journal = JournalRoom(depot, base)
        val id = runBlocking {
            val med = depot.ajouterMedicament("Testoline")
            depot.enregistrerPrise(med, Quantite(100), Unite.MG, horloge.instant().minusSeconds(3600))
        }
        val jour = JourneeViewModel(journal, FauxReglages(), horloge)
        attendreJour(jour) { it.totaux == listOf("Testoline : 1 mg") }

        jour.reattribuerALaVeille(id)
        val vide = attendreJour(jour) { it.prises.isEmpty() }
        assertTrue(vide.totaux.isEmpty())

        jour.journeePrecedente()
        val veille = attendreJour(jour) { it.journee == LocalDate.parse("2026-11-09") && it.prises.size == 1 }
        assertEquals(listOf("Testoline : 1 mg"), veille.totaux)
        assertTrue(veille.prises.single().reattribuee)
        assertEquals(LocalDate.parse("2026-11-09"), runBlocking { depot.prise(id) }!!.journeeForcee)

        jour.retablirJournee(id)
        attendreJour(jour) { it.prises.isEmpty() }
        assertEquals(null, runBlocking { depot.prise(id) }!!.journeeForcee)
    }
}
