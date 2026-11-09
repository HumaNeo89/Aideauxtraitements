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
 * ViewModel + vraie base Room en mémoire NON chiffrée (comme en E3) : l'annulation est
 * persistée et la prise annulée sort des totaux du jour.
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

    @Test
    fun annulationPersisteeEtExclueDesTotaux() {
        val vm = SaisieViewModel(JournalRoom(depot, base), horloge, calc)
        vm.ouvrirFormulaire()
        vm.saisirLibelle("Testoline")
        vm.ajouterMedicament()
        attendre(vm) { it.formulaire?.medicamentId != null }
        vm.saisirQuantite("2")
        vm.relire()
        assertEquals("2 mg de Testoline à 14:05", vm.etat.value.relecture!!.phrase)
        vm.enregistrer()
        val apres = attendre(vm) { it.annulationRapide != null && it.prises.size == 1 }
        val id = apres.annulationRapide!!.priseId

        // Lue en base : confirmée, comptée dans le total.
        val cle = CleTotal("Testoline", Unite.MG)
        assertEquals(StatutPrise.CONFIRMEE, runBlocking { depot.prise(id) }!!.statut)
        assertEquals(mapOf(cle to Quantite(200)), runBlocking { depot.observerTotauxDuJour(aujourdhui, calc).first() })

        vm.annulerRapide()
        attendre(vm) { it.prises.singleOrNull()?.annulee == true }

        // Persistée : la ligne reste, le statut est ANNULEE, le total est vide.
        val relue = runBlocking { depot.prise(id) }!!
        assertEquals(StatutPrise.ANNULEE, relue.statut)
        assertEquals(Quantite(200), relue.quantite)
        assertEquals(1, runBlocking { depot.prisesDuJour(aujourdhui, calc) }.size)
        assertTrue(runBlocking { depot.observerTotauxDuJour(aujourdhui, calc).first() }.isEmpty())
    }

    @Test
    fun annulationDepuisLaListe() {
        val journal = JournalRoom(depot, base)
        val id = runBlocking {
            val med = depot.ajouterMedicament("Testoline B")
            depot.enregistrerPrise(med, Quantite(40), Unite.COMPRIME, horloge.instant().minusSeconds(3600))
        }
        val vm = SaisieViewModel(journal, horloge, calc)
        val ligne = attendre(vm) { it.prises.size == 1 }.prises.single()
        assertEquals("13:05 — 0,4 comprimé de Testoline B", ligne.texte)
        vm.demanderAnnulation(ligne.id)
        vm.confirmerAnnulation()
        attendre(vm) { it.prises.singleOrNull()?.annulee == true }
        assertEquals(StatutPrise.ANNULEE, runBlocking { depot.prise(id) }!!.statut)
        assertTrue(runBlocking { depot.observerTotauxDuJour(aujourdhui, calc).first() }.isEmpty())
    }
}
