package fr.dosejuste.app.ui

import fr.dosejuste.app.domain.Quantite
import fr.dosejuste.app.domain.StatutPrise
import fr.dosejuste.app.domain.Unite
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Totaux, liste, navigation, pivot et réattribution : horloge fixe 10/11/2026 14:05:30 (Paris), faux journal. */
@OptIn(ExperimentalCoroutinesApi::class)
class JourneeViewModelTest {

    private val paris = ZoneId.of("Europe/Paris")
    private val horloge = Clock.fixed(aParis("2026-11-10T14:05:30"), paris)
    private val journal = FauxJournal()
    private val reglages = FauxReglages(pivot = 5)
    private val aujourdhui = LocalDate.parse("2026-11-10")
    private val veille = LocalDate.parse("2026-11-09")

    private fun aParis(texte: String): Instant = LocalDateTime.parse(texte).atZone(ZoneId.of("Europe/Paris")).toInstant()

    @Before
    fun installer() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun retirer() {
        Dispatchers.resetMain()
    }

    private fun vm() = JourneeViewModel(journal, reglages, horloge)

    private fun ajouter(
        libelle: String,
        centiemes: Long,
        unite: Unite,
        quand: String,
        statut: StatutPrise = StatutPrise.CONFIRMEE,
    ): Long = runBlocking { journal.ajouterPrise(libelle, Quantite(centiemes), unite, aParis(quand), statut) }

    @Test
    fun totalDuJourExclutLesAnnulees() {
        ajouter("Testoline", 200, Unite.MG, "2026-11-10T08:00")
        ajouter("Testoline", 100, Unite.MG, "2026-11-10T09:00", StatutPrise.ANNULEE)
        val etat = vm().etat.value
        assertEquals(listOf("Testoline : 2 mg"), etat.totaux)
        // La liste garde les deux lignes ; l'annulée est marquée.
        assertEquals(listOf(false, true), etat.prises.map { it.annulee })
        assertEquals(listOf("08:00 — 2 mg de Testoline", "09:00 — 1 mg de Testoline"), etat.prises.map { it.texte })
    }

    @Test
    fun medicamentsEtUnitesDistinctsJamaisAdditionnes() {
        ajouter("Testoline", 25, Unite.MG, "2026-11-10T08:00")
        ajouter("Testoline", 10, Unite.MG, "2026-11-10T09:00")
        ajouter("Testoline B", 100, Unite.MG, "2026-11-10T10:00")
        ajouter("Testoline", 50, Unite.COMPRIME, "2026-11-10T11:00")
        ajouter("Testoline", 100, Unite.COMPRIME, "2026-11-10T12:00")
        val totaux = vm().etat.value.totaux
        assertEquals(
            listOf("Testoline : 0,35 mg", "Testoline B : 1 mg", "Testoline : 1,5 comprimé"),
            totaux,
        )
        assertEquals(3, totaux.size)
    }

    @Test
    fun journeeSansPriseConfirmeeAffichePasDeTotal() {
        ajouter("Exemple", 100, Unite.GOUTTE, "2026-11-10T08:00", StatutPrise.ANNULEE)
        val etat = vm().etat.value
        assertTrue(etat.totaux.isEmpty())
        assertEquals(1, etat.prises.size)
    }

    @Test
    fun laCasseDuLibelleEstConservee() {
        ajouter("Testoline", 100, Unite.MG, "2026-11-10T08:00")
        val etat = vm().etat.value
        assertEquals(listOf("Testoline : 1 mg"), etat.totaux)
        assertEquals("08:00 — 1 mg de Testoline", etat.prises.single().texte)
    }

    @Test
    fun changerLePivotRecalculeImmediatementLaJournee() {
        // 01:30 le 10/11 : pivot 5 h -> journée du 09/11 ; pivot 1 h -> journée du 10/11.
        ajouter("Testoline", 100, Unite.MG, "2026-11-10T01:30")
        val vm = vm()
        assertEquals(aujourdhui, vm.etat.value.journee)
        assertTrue(vm.etat.value.prises.isEmpty())

        reglages.definirPivotHeure(1)
        assertEquals(listOf("01:30 — 1 mg de Testoline"), vm.etat.value.prises.map { it.texte })
        assertEquals(listOf("Testoline : 1 mg"), vm.etat.value.totaux)

        reglages.definirPivotHeure(5)
        assertTrue(vm.etat.value.prises.isEmpty())
        vm.journeePrecedente()
        assertEquals(veille, vm.etat.value.journee)
        assertEquals(listOf("Testoline : 1 mg"), vm.etat.value.totaux)
    }

    @Test
    fun lePivotChangeAussiLaJourneeEnCours() {
        // 14:05 : avec un pivot à 15 h, la journée en cours est celle commencée la veille à 15 h.
        val vm = vm()
        assertEquals(aujourdhui, vm.etat.value.journee)
        reglages.definirPivotHeure(15)
        assertEquals(veille, vm.etat.value.journee)
        assertTrue(vm.etat.value.estAujourdhui)
    }

    @Test
    fun navigationJourParJourSansDepasserAujourdhui() {
        ajouter("Testoline", 100, Unite.MG, "2026-11-09T12:00")
        val vm = vm()
        assertTrue(vm.etat.value.estAujourdhui)
        vm.journeeSuivante()
        assertEquals(aujourdhui, vm.etat.value.journee)

        vm.journeePrecedente()
        assertEquals(veille, vm.etat.value.journee)
        assertFalse(vm.etat.value.estAujourdhui)
        assertEquals(listOf("Testoline : 1 mg"), vm.etat.value.totaux)

        vm.journeePrecedente()
        assertEquals(LocalDate.parse("2026-11-08"), vm.etat.value.journee)
        vm.journeeSuivante()
        vm.journeeSuivante()
        vm.journeeSuivante()
        assertEquals(aujourdhui, vm.etat.value.journee)
    }

    @Test
    fun reattribuerALaVeilleDeplaceLaPriseEtLesTotauxDesDeuxJournees() {
        val id = ajouter("Testoline", 200, Unite.MG, "2026-11-10T09:00")
        ajouter("Testoline", 100, Unite.MG, "2026-11-10T10:00")
        val vm = vm()
        assertEquals(listOf("Testoline : 3 mg"), vm.etat.value.totaux)

        vm.reattribuerALaVeille(id)
        assertEquals(listOf("Testoline : 1 mg"), vm.etat.value.totaux)
        assertEquals(1, vm.etat.value.prises.size)

        vm.journeePrecedente()
        assertEquals(listOf("Testoline : 2 mg"), vm.etat.value.totaux)
        val ligne = vm.etat.value.prises.single()
        assertTrue(ligne.reattribuee)
        assertEquals(aParis("2026-11-10T09:00"), journal.prise(id).horodatage) // horodatage inchangé
        assertEquals(veille, journal.prise(id).journeeForcee)
    }

    @Test
    fun reattribuerAuLendemainSeulementSiPasDansLeFutur() {
        val id = ajouter("Testoline", 100, Unite.MG, "2026-11-09T12:00")
        val vm = vm()
        vm.journeePrecedente() // 09/11
        vm.reattribuerAuLendemain(id)
        assertTrue(vm.etat.value.prises.isEmpty())
        // Le lendemain de la journée calculée est la journée en cours : forcée au 10/11.
        assertEquals(aujourdhui, journal.prise(id).journeeForcee)

        // Depuis la journée en cours : pas de « lendemain » (ce serait le futur).
        vm.journeeSuivante()
        assertEquals(aujourdhui, vm.etat.value.journee)
        vm.reattribuerAuLendemain(id)
        assertEquals(aujourdhui, journal.prise(id).journeeForcee)
        assertEquals(1, vm.etat.value.prises.size)
    }

    @Test
    fun retablirLaJourneeCalculee() {
        val id = ajouter("Testoline", 100, Unite.MG, "2026-11-10T09:00")
        val vm = vm()
        vm.retablirJournee(id) // pas réattribuée : sans effet
        assertNull(journal.prise(id).journeeForcee)

        vm.reattribuerALaVeille(id)
        vm.journeePrecedente()
        assertTrue(vm.etat.value.prises.single().reattribuee)
        vm.retablirJournee(id)
        assertNull(journal.prise(id).journeeForcee)
        assertTrue(vm.etat.value.prises.isEmpty())
    }

    @Test
    fun reattribuerVersLaJourneeCalculeeRevientAuCalcul() {
        val id = ajouter("Testoline", 100, Unite.MG, "2026-11-10T09:00")
        val vm = vm()
        vm.reattribuerALaVeille(id)
        vm.journeePrecedente()
        vm.reattribuerAuLendemain(id) // 09/11 + 1 = 10/11 = journée calculée
        assertNull(journal.prise(id).journeeForcee)
    }

    @Test
    fun priseAnnuleeNeSeReattribuePas() {
        val id = ajouter("Testoline", 100, Unite.MG, "2026-11-10T09:00", StatutPrise.ANNULEE)
        val vm = vm()
        vm.reattribuerALaVeille(id)
        assertNull(journal.prise(id).journeeForcee)
    }

    @Test
    fun annulationAvecConfirmation() {
        val id = ajouter("Testoline", 200, Unite.MG, "2026-11-10T09:00")
        val vm = vm()
        val ligne = vm.etat.value.prises.single()

        vm.demanderAnnulation(ligne.id)
        assertEquals(ligne, vm.etat.value.priseAAnnuler)
        assertEquals("rien avant confirmation", StatutPrise.CONFIRMEE, journal.statut(id))

        vm.abandonnerAnnulation()
        assertNull(vm.etat.value.priseAAnnuler)
        vm.confirmerAnnulation() // plus de demande en cours : sans effet
        assertEquals(StatutPrise.CONFIRMEE, journal.statut(id))

        vm.demanderAnnulation(ligne.id)
        vm.confirmerAnnulation()
        assertEquals(StatutPrise.ANNULEE, journal.statut(id))
        assertTrue(vm.etat.value.prises.single().annulee)
        assertTrue(vm.etat.value.totaux.isEmpty())

        vm.demanderAnnulation(ligne.id) // déjà annulée : pas de nouvelle demande
        assertNull(vm.etat.value.priseAAnnuler)
    }

    @Test
    fun uneErreurDuJournalEstSignaleeSansDetail() {
        val id = ajouter("Testoline", 100, Unite.MG, "2026-11-10T09:00")
        val casse = object : JournalPrises by journal {
            override suspend fun reattribuer(id: Long, journee: LocalDate?): Boolean = error("échec")
        }
        val vm = JourneeViewModel(casse, reglages, horloge)
        vm.reattribuerALaVeille(id)
        assertTrue(vm.etat.value.erreurOperation)
        vm.acquitterErreur()
        assertFalse(vm.etat.value.erreurOperation)
    }
}
