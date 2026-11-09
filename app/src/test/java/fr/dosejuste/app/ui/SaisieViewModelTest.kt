package fr.dosejuste.app.ui

import fr.dosejuste.app.domain.JourneeCalculator
import fr.dosejuste.app.domain.Quantite
import fr.dosejuste.app.domain.RaisonErreur
import fr.dosejuste.app.domain.StatutPrise
import fr.dosejuste.app.domain.Unite
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Écran de saisie : horloge fixe (10/11/2026 14:05:30, Europe/Paris), faux journal, temps virtuel. */
@OptIn(ExperimentalCoroutinesApi::class)
class SaisieViewModelTest {

    private val paris = ZoneId.of("Europe/Paris")
    private val maintenant: Instant = LocalDateTime.parse("2026-11-10T14:05:30").atZone(paris).toInstant()
    private val horloge = Clock.fixed(maintenant, paris)
    private val calc = JourneeCalculator()
    private val journal = FauxJournal()
    private val planificateur = kotlinx.coroutines.test.TestCoroutineScheduler()

    @Before
    fun installer() {
        Dispatchers.setMain(StandardTestDispatcher(planificateur))
    }

    @After
    fun retirer() {
        Dispatchers.resetMain()
    }

    private fun test(bloc: suspend TestScope.() -> Unit) = runTest(StandardTestDispatcher(planificateur), testBody = bloc)

    private fun aParis(texte: String): Instant = LocalDateTime.parse(texte).atZone(paris).toInstant()

    private suspend fun TestScope.vm(): SaisieViewModel {
        val vm = SaisieViewModel(journal, horloge, calc, 10.seconds)
        runCurrent()
        return vm
    }

    /** Formulaire ouvert avec « Testoline » créé et sélectionné. */
    private suspend fun TestScope.formulaire(vm: SaisieViewModel, libelle: String = "Testoline") {
        vm.ouvrirFormulaire()
        vm.saisirLibelle(libelle)
        vm.ajouterMedicament()
        runCurrent()
    }

    private val SaisieViewModel.f get() = etat.value.formulaire!!

    // ---- Quantités ----

    @Test
    fun quantitesAcceptees() = test {
        val vm = vm()
        formulaire(vm)
        for (texte in listOf("0,25", "0.25", "0,4", "1")) {
            vm.saisirQuantite(texte)
            vm.relire()
            assertNotNull("« $texte » devrait être acceptée", vm.etat.value.relecture)
            vm.modifier()
        }
    }

    @Test
    fun quantitesRefuseesAvecLaBonneRaison() = test {
        val vm = vm()
        formulaire(vm)
        val cas = mapOf(
            "0,125" to RaisonErreur.TROP_DE_DECIMALES,
            "0" to RaisonErreur.NUL_OU_NEGATIF,
            "-1" to RaisonErreur.NUL_OU_NEGATIF,
            "" to RaisonErreur.VIDE,
            "abc" to RaisonErreur.FORMAT,
            "100000,01" to RaisonErreur.TROP_GRAND,
        )
        for ((texte, raison) in cas) {
            vm.saisirQuantite(texte)
            vm.relire()
            assertEquals("« $texte »", raison, vm.f.erreurQuantite)
            assertNull(vm.etat.value.relecture)
        }
        assertTrue(journal.prises.value.isEmpty())
    }

    @Test
    fun medicamentNonChoisiRefuse() = test {
        val vm = vm()
        vm.ouvrirFormulaire()
        vm.saisirQuantite("1")
        vm.relire()
        assertTrue(vm.f.erreurMedicamentManquant)
        assertNull(vm.etat.value.relecture)
    }

    // ---- Heure et jour ----

    @Test
    fun heureParDefautEstMaintenantSansSecondes() = test {
        val vm = vm()
        vm.ouvrirFormulaire()
        assertEquals(LocalTime.of(14, 5), vm.f.heure)
        assertEquals(JourSaisie.AUJOURDHUI, vm.f.jour)
        assertEquals(Unite.MG, vm.f.unite)
    }

    @Test
    fun heureFutureRefusee() = test {
        val vm = vm()
        formulaire(vm)
        vm.saisirQuantite("1")
        vm.choisirHeure(LocalTime.of(14, 6))
        vm.relire()
        assertTrue(vm.f.erreurHeureFuture)
        assertNull(vm.etat.value.relecture)

        vm.choisirHeure(LocalTime.of(14, 5)) // minute courante : acceptée
        assertFalse(vm.f.erreurHeureFuture)
        vm.relire()
        assertNotNull(vm.etat.value.relecture)
    }

    @Test
    fun hier0130CompteraPourAvantHier() = test {
        val vm = vm()
        formulaire(vm)
        vm.saisirQuantite("0,5")
        vm.choisirJour(JourSaisie.HIER)
        vm.choisirHeure(LocalTime.of(1, 30))
        vm.relire()
        val relecture = vm.etat.value.relecture!!
        assertEquals(aParis("2026-11-09T01:30"), relecture.instant)
        assertEquals("0,5 mg de Testoline hier à 01:30", relecture.phrase)

        vm.enregistrer()
        runCurrent()
        assertEquals(aParis("2026-11-09T01:30"), journal.prises.value.single().horodatage)
        // Pivot 05:00 : 01:30 le 09/11 compte pour la journée du 08/11 ; la liste « précédente » (09/11) ne l'a pas.
        assertEquals(java.time.LocalDate.parse("2026-11-08"), calc.journeeDe(journal.prises.value.single().horodatage))
    }

    @Test
    fun aujourdhui0130ComptePourLaVeille() = test {
        val vm = vm()
        formulaire(vm)
        vm.saisirQuantite("1")
        vm.choisirHeure(LocalTime.of(1, 30))
        vm.relire()
        vm.enregistrer()
        runCurrent()
        // Pas dans la journée courante (10/11) ; retrouvée dans la journée précédente (09/11).
        assertTrue(vm.etat.value.prises.isEmpty())
        vm.choisirJourneeAffichee(JourneeAffichee.PRECEDENTE)
        runCurrent()
        assertEquals(listOf("01:30 — 1 mg de Testoline"), vm.etat.value.prises.map { it.texte })
    }

    // ---- Relecture ----

    @Test
    fun relectureExacte() = test {
        val vm = vm()
        formulaire(vm)
        vm.saisirQuantite("0,25")
        vm.relire()
        assertEquals("0,25 mg de Testoline à 14:05", vm.etat.value.relecture!!.phrase)
        vm.modifier()

        vm.saisirQuantite("2")
        vm.choisirUnite(Unite.COMPRIME)
        vm.choisirHeure(LocalTime.of(8, 0))
        vm.relire()
        assertEquals("2 comprimés de Testoline à 08:00", vm.etat.value.relecture!!.phrase)
    }

    @Test
    fun elisionDevantVoyelle() {
        assertEquals("1 comprimé d'Exemple", PhrasePrise.quantiteEtMedicament(Quantite(100), Unite.COMPRIME, "Exemple"))
        assertEquals("2 mg de Testoline B", PhrasePrise.quantiteEtMedicament(Quantite(200), Unite.MG, "Testoline B"))
    }

    @Test
    fun rienEstEcritAvantEnregistrer() = test {
        val vm = vm()
        formulaire(vm)
        vm.saisirQuantite("2")
        vm.relire()
        runCurrent()
        assertTrue(journal.prises.value.isEmpty())
        vm.modifier()
        assertNull(vm.etat.value.relecture)
        assertNotNull(vm.etat.value.formulaire)
        assertEquals("2", vm.f.quantiteTexte)
        runCurrent()
        assertTrue(journal.prises.value.isEmpty())

        vm.relire()
        vm.enregistrer()
        runCurrent()
        val prise = journal.prises.value.single()
        assertEquals(StatutPrise.CONFIRMEE, prise.statut)
        assertEquals(Quantite(200), prise.quantite)
        assertNull(vm.etat.value.formulaire)
        assertEquals("2 mg de Testoline à 14:05", vm.etat.value.annulationRapide!!.phrase)
    }

    @Test
    fun enregistrerDeuxFoisNeCreeQuUnePrise() = test {
        val vm = vm()
        formulaire(vm)
        vm.saisirQuantite("2")
        vm.relire()
        vm.enregistrer()
        vm.enregistrer()
        runCurrent()
        assertEquals(1, journal.prises.value.size)
    }

    // ---- Annulation ----

    private suspend fun TestScope.enregistrerUnePrise(vm: SaisieViewModel): Long {
        formulaire(vm)
        vm.saisirQuantite("2")
        vm.relire()
        vm.enregistrer()
        runCurrent()
        return vm.etat.value.annulationRapide!!.priseId
    }

    @Test
    fun annulerA9_9SecondesAnnule() = test {
        val vm = vm()
        val id = enregistrerUnePrise(vm)
        advanceTimeBy(9_900)
        runCurrent()
        assertNotNull(vm.etat.value.annulationRapide)
        vm.annulerRapide()
        runCurrent()
        assertEquals(StatutPrise.ANNULEE, journal.statut(id))
        assertEquals(1, journal.prises.value.size) // la ligne reste
        assertNull(vm.etat.value.annulationRapide)
        assertTrue(vm.etat.value.prises.single().annulee)
    }

    @Test
    fun annulerA10SecondesOuApresEstImpossible() = test {
        val vm = vm()
        val id = enregistrerUnePrise(vm)
        advanceTimeBy(9_999)
        runCurrent()
        assertNotNull("encore offert à 9,999 s", vm.etat.value.annulationRapide)

        advanceTimeBy(1)
        runCurrent()
        assertNull("retiré à 10,0 s", vm.etat.value.annulationRapide)
        vm.annulerRapide()
        runCurrent()
        assertEquals(StatutPrise.CONFIRMEE, journal.statut(id))

        advanceTimeBy(5_000)
        runCurrent()
        vm.annulerRapide()
        runCurrent()
        assertEquals(StatutPrise.CONFIRMEE, journal.statut(id))
    }

    @Test
    fun annulationDepuisLaListeApres10SecondesAvecConfirmation() = test {
        val vm = vm()
        val id = enregistrerUnePrise(vm)
        advanceTimeBy(10_000)
        runCurrent()
        assertNull(vm.etat.value.annulationRapide)

        val ligne = vm.etat.value.prises.single()
        assertEquals("14:05 — 2 mg de Testoline", ligne.texte)
        assertFalse(ligne.annulee)

        vm.demanderAnnulation(ligne.id)
        assertEquals(ligne, vm.etat.value.priseAAnnuler)
        runCurrent()
        assertEquals("rien avant confirmation", StatutPrise.CONFIRMEE, journal.statut(id))

        vm.abandonnerAnnulation()
        assertNull(vm.etat.value.priseAAnnuler)
        vm.confirmerAnnulation() // plus de demande en cours : sans effet
        runCurrent()
        assertEquals(StatutPrise.CONFIRMEE, journal.statut(id))

        vm.demanderAnnulation(ligne.id)
        vm.confirmerAnnulation()
        runCurrent()
        assertEquals(StatutPrise.ANNULEE, journal.statut(id))
        assertTrue(vm.etat.value.prises.single().annulee)

        vm.demanderAnnulation(ligne.id) // déjà annulée : pas de nouvelle demande
        assertNull(vm.etat.value.priseAAnnuler)
    }

    @Test
    fun nouvelEnregistrementRemplaceLAnnulationRapide() = test {
        val vm = vm()
        val premiere = enregistrerUnePrise(vm)
        vm.ouvrirFormulaire()
        vm.choisirMedicament(journal.listeMedicaments.single().id)
        vm.saisirQuantite("1")
        vm.relire()
        vm.enregistrer()
        runCurrent()
        val seconde = vm.etat.value.annulationRapide!!.priseId
        assertTrue(seconde != premiere)
        vm.annulerRapide()
        runCurrent()
        assertEquals(StatutPrise.ANNULEE, journal.statut(seconde))
        assertEquals(StatutPrise.CONFIRMEE, journal.statut(premiere))
    }

    // ---- Médicaments ----

    @Test
    fun libelleVideOuDoublonRefuse() = test {
        val vm = vm()
        vm.ouvrirFormulaire()
        vm.saisirLibelle("   ")
        vm.ajouterMedicament()
        runCurrent()
        assertEquals(ErreurLibelle.VIDE, vm.f.erreurLibelle)
        assertTrue(journal.listeMedicaments.isEmpty())

        vm.saisirLibelle(" Testoline ")
        vm.ajouterMedicament()
        runCurrent()
        assertEquals(listOf("Testoline"), journal.listeMedicaments.map { it.libelle })
        assertEquals(journal.listeMedicaments.single().id, vm.f.medicamentId)
        assertNull(vm.f.erreurLibelle)
        assertEquals("", vm.f.libelleTexte)

        for (doublon in listOf("Testoline", "testoline", "  TESTOLINE ")) {
            vm.saisirLibelle(doublon)
            vm.ajouterMedicament()
            runCurrent()
            assertEquals(doublon, ErreurLibelle.DOUBLON, vm.f.erreurLibelle)
        }
        assertEquals(1, journal.listeMedicaments.size)

        vm.saisirLibelle("Testoline B")
        vm.ajouterMedicament()
        runCurrent()
        assertEquals(2, journal.listeMedicaments.size)
        assertEquals(2, vm.etat.value.medicaments.size)
    }

    @Test
    fun listeDeMedicamentsVideAuDepart() = test {
        val vm = vm()
        assertTrue(vm.etat.value.medicaments.isEmpty())
        assertTrue(vm.etat.value.prises.isEmpty())
        assertNull(vm.etat.value.formulaire)
    }
}
