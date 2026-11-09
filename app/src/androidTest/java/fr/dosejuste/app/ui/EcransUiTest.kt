package fr.dosejuste.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fr.dosejuste.app.DoseJusteTheme
import fr.dosejuste.app.R
import fr.dosejuste.app.domain.Quantite
import fr.dosejuste.app.domain.StatutPrise
import fr.dosejuste.app.domain.Unite
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Écrans E5 avec un faux journal et de faux réglages (ni la vraie base, ni les vraies
 * préférences). Horloge fixe : 10/11/2026 14:05 à Paris.
 */
@RunWith(AndroidJUnit4::class)
class EcransUiTest {

    @get:Rule
    val rule = createComposeRule()

    private val contexte = InstrumentationRegistry.getInstrumentation().targetContext
    private val paris = ZoneId.of("Europe/Paris")
    private val maintenant = LocalDateTime.parse("2026-11-10T14:05:30").atZone(paris).toInstant()
    private val horloge = Clock.fixed(maintenant, paris)
    private val journal = FauxJournal()

    private fun texte(id: Int, vararg args: Any): String = contexte.getString(id, *args)

    private fun aParis(texte: String): Instant = LocalDateTime.parse(texte).atZone(paris).toInstant()

    private fun ajouter(libelle: String, centiemes: Long, unite: Unite, quand: String, statut: StatutPrise = StatutPrise.CONFIRMEE) =
        runBlocking { journal.ajouterPrise(libelle, Quantite(centiemes), unite, aParis(quand), statut) }

    private fun lancer(reglages: FauxReglages) {
        val ouverture = OuvertureViewModel({ journal })
        rule.setContent {
            DoseJusteTheme {
                DoseJusteApp(ouverture, reglages, onFermer = {}, horloge = horloge)
            }
        }
    }

    private fun attendreTexte(texte: String) {
        rule.waitUntil(5_000) { rule.onAllNodesWithText(texte).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun existe(texte: String, substring: Boolean = false): Boolean =
        rule.onAllNodesWithText(texte, substring = substring).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun avertissementBloquantPuisJaiCompris() {
        val reglages = FauxReglages(accepte = false)
        lancer(reglages)

        attendreTexte(texte(R.string.avertissement_texte))
        rule.onNodeWithText(
            "Journal personnel, sans garantie. Ce n'est pas un avis médical. Cette application ne donne aucun conseil de dose.",
        ).assertIsDisplayed()
        // Rien d'autre n'est accessible.
        assertFalse(existe(texte(R.string.nouvelle_prise)))
        assertFalse(existe(texte(R.string.historique)))
        assertFalse(existe(texte(R.string.reglages)))
        assertFalse(reglages.avertissementAccepte.value)

        rule.onNodeWithText(texte(R.string.avertissement_ok)).performClick()
        attendreTexte(texte(R.string.nouvelle_prise))
        rule.onNodeWithText(texte(R.string.nouvelle_prise)).assertIsDisplayed()
        assertTrue(reglages.avertissementAccepte.value)
        assertFalse(existe(texte(R.string.avertissement_ok)))
    }

    @Test
    fun totalDuJourExclutLesPrisesAnnulees() {
        ajouter("Testoline", 200, Unite.MG, "2026-11-10T08:00")
        ajouter("Testoline", 100, Unite.MG, "2026-11-10T09:00", StatutPrise.ANNULEE)
        lancer(FauxReglages())

        attendreTexte("Testoline : 2 mg")
        rule.onNodeWithText("Testoline : 2 mg").assertIsDisplayed()
        assertFalse(existe("Testoline : 3 mg"))
        // La liste montre les deux prises, l'annulée est marquée.
        rule.onNodeWithText("08:00 — 2 mg de Testoline").assertIsDisplayed()
        rule.onNodeWithText("09:00 — 1 mg de Testoline").assertIsDisplayed()
        rule.onNodeWithText(texte(R.string.prise_annulee)).assertIsDisplayed()
    }

    @Test
    fun reglagesPivotFaitPasserUnePriseDUneJourneeALAutre() {
        ajouter("Testoline", 100, Unite.MG, "2026-11-10T01:30")
        lancer(FauxReglages(pivot = 5))

        attendreTexte(texte(R.string.nouvelle_prise))
        // Pivot 5 h : 01:30 compte pour la veille, rien aujourd'hui.
        assertFalse(existe("01:30 — 1 mg de Testoline"))

        rule.onNodeWithTag(Tags.REGLAGES).performClick()
        rule.onNodeWithText(texte(R.string.pivot_explication)).assertIsDisplayed()
        rule.onNodeWithTag(Tags.PIVOT_PREFIXE + 1).performScrollTo().performClick()
        rule.onNodeWithTag(Tags.RETOUR_ECRAN).performClick()

        // Pivot 1 h : 01:30 compte pour la journée en cours.
        attendreTexte("01:30 — 1 mg de Testoline")
        rule.onNodeWithText("Testoline : 1 mg").assertIsDisplayed()

        rule.onNodeWithTag(Tags.REGLAGES).performClick()
        rule.onNodeWithTag(Tags.PIVOT_PREFIXE + 5).performScrollTo().performClick()
        rule.onNodeWithTag(Tags.RETOUR_ECRAN).performClick()
        rule.waitUntil(5_000) { !existe("01:30 — 1 mg de Testoline") }
    }

    @Test
    fun historiqueReattribueUnePriseALaVeille() {
        ajouter("Testoline", 100, Unite.MG, "2026-11-10T09:00")
        lancer(FauxReglages())
        attendreTexte("Testoline : 1 mg")

        rule.onNodeWithTag(Tags.HISTORIQUE).performClick()
        // Journée en cours : « Journée suivante » est inactive, pas de réattribution vers le futur.
        attendreTexte(texte(R.string.reattribuer_veille))
        assertFalse(existe(texte(R.string.reattribuer_lendemain)))
        rule.onNodeWithText(texte(R.string.reattribuer_veille)).performClick()
        rule.waitUntil(5_000) { !existe("09:00 — 1 mg de Testoline") }
        assertEquals(LocalDate.parse("2026-11-09"), journal.prises.value.single().journeeForcee)

        rule.onNodeWithTag(Tags.JOURNEE_PRECEDENTE).performClick()
        attendreTexte("09:00 — 1 mg de Testoline")
        rule.onNodeWithText(texte(R.string.prise_reattribuee)).assertIsDisplayed()
        rule.onNodeWithText("Testoline : 1 mg").assertIsDisplayed()
        rule.onNodeWithText(texte(R.string.reattribuer_retablir)).performClick()
        rule.waitUntil(5_000) { !existe("09:00 — 1 mg de Testoline") }
    }

    @Test
    fun aProposEtLicencesAffichentLesNotices() {
        lancer(FauxReglages())
        attendreTexte(texte(R.string.nouvelle_prise))
        rule.onNodeWithTag(Tags.A_PROPOS).performClick()
        rule.onNodeWithText("Dose Juste, version", substring = true).assertIsDisplayed()
        rule.onNodeWithTag(Tags.LICENCES).performClick()

        rule.waitUntil(5_000) { existe("ZETETIC LLC", substring = true) }
        assertTrue(existe("ZETETIC LLC", substring = true))
        assertTrue(existe("LibTomCrypt", substring = true))
        assertTrue(existe("SQLite"))
        assertTrue(existe("AndroidX", substring = true))
        assertTrue(existe("kotlinx.coroutines"))

        // Texte complet de SQLCipher (premier bouton « Afficher le texte complet »).
        rule.onAllNodesWithText(texte(R.string.licence_texte_afficher))[0].performScrollTo().performClick()
        rule.waitUntil(5_000) { existe("Redistribution and use in source and binary forms", substring = true) }
        assertTrue(existe("SUCH DAMAGE", substring = true))
    }

    @Test
    fun relireEtRetourResteVisiblesQuandUneErreurApparait() {
        val vm = SaisieViewModel(journal, horloge)
        val jour = JourneeViewModel(journal, FauxReglages(), horloge)
        rule.setContent { DoseJusteTheme { EcranAujourdhui(vm, jour, Navigation({}, {}, {})) } }

        rule.onNodeWithTag(Tags.NOUVELLE_PRISE).performClick()
        rule.onNodeWithTag(Tags.CHAMP_QUANTITE).performTextInput("abc")
        // Clavier encore ouvert : la barre d'actions doit rester à l'écran.
        rule.onNodeWithTag(Tags.RELIRE).assertIsDisplayed()
        rule.onNodeWithTag(Tags.RELIRE).performClick()

        // Erreurs affichées (quantité au format invalide, aucun médicament choisi).
        rule.onNodeWithText(texte(R.string.erreur_quantite_format)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag(Tags.RELIRE).assertIsDisplayed()
        rule.onNodeWithTag(Tags.RETOUR).assertIsDisplayed()

        // Et sans clavier.
        Espresso.closeSoftKeyboard()
        rule.waitForIdle()
        rule.onNodeWithText(texte(R.string.erreur_medicament_manquant)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag(Tags.RELIRE).assertIsDisplayed()
        rule.onNodeWithTag(Tags.RETOUR).assertIsDisplayed()
    }

    @Test
    fun relireResteVisibleAvecErreurDHeureFuture() {
        val vm = SaisieViewModel(journal, horloge)
        val jour = JourneeViewModel(journal, FauxReglages(), horloge)
        runBlocking { journal.ajouterMedicament("Exemple") }
        rule.setContent { DoseJusteTheme { EcranAujourdhui(vm, jour, Navigation({}, {}, {})) } }

        rule.onNodeWithTag(Tags.NOUVELLE_PRISE).performClick()
        vm.choisirHeure(java.time.LocalTime.of(23, 59)) // plus tard que 14:05
        rule.onNodeWithTag(Tags.CHAMP_QUANTITE).performTextInput("1,5")
        Espresso.closeSoftKeyboard()
        rule.waitForIdle()
        rule.onNodeWithTag(Tags.RELIRE).performClick()
        rule.onNodeWithText(texte(R.string.erreur_heure_futur)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag(Tags.RELIRE).assertIsDisplayed()
        rule.onNodeWithTag(Tags.RETOUR).assertIsDisplayed()
    }
}
