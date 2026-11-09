package fr.dosejuste.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fr.dosejuste.app.DoseJusteTheme
import fr.dosejuste.app.R
import fr.dosejuste.app.domain.JourneeCalculator
import fr.dosejuste.app.domain.Quantite
import fr.dosejuste.app.domain.StatutPrise
import fr.dosejuste.app.domain.Unite
import fr.dosejuste.app.security.DonneesIllisiblesException
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Parcours de saisie avec un faux journal en mémoire (ni la vraie base ni le Keystore).
 * Horloge fixe : 10/11/2026 14:05 à Paris.
 */
@RunWith(AndroidJUnit4::class)
class SaisieUiTest {

    @get:Rule
    val rule = createComposeRule()

    private val contexte = InstrumentationRegistry.getInstrumentation().targetContext
    private val paris = ZoneId.of("Europe/Paris")
    private val maintenant = LocalDateTime.parse("2026-11-10T14:05:30").atZone(paris).toInstant()
    private val horloge = Clock.fixed(maintenant, paris)

    private fun texte(id: Int, vararg args: Any): String = contexte.getString(id, *args)

    private fun attendreTexte(texte: String) {
        rule.waitUntil(5_000) { rule.onAllNodesWithText(texte).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun saisieRelectureEnregistrementEtAnnulationRapide() {
        val journal = FauxJournal()
        val vm = SaisieViewModel(journal, horloge, JourneeCalculator())
        rule.setContent { DoseJusteTheme { EcranSaisie(vm) } }

        // L'en-tête (nom + avertissement) reste visible.
        rule.onNodeWithText(texte(R.string.disclaimer)).assertIsDisplayed()

        rule.onNodeWithTag(Tags.NOUVELLE_PRISE).performClick()
        rule.onNodeWithTag(Tags.CHAMP_LIBELLE).performTextInput("Testoline")
        rule.onNodeWithTag(Tags.AJOUTER_LIBELLE).performClick()
        rule.onNodeWithTag(Tags.CHAMP_QUANTITE).performTextInput("2")
        rule.onNodeWithTag(Tags.RELIRE).performClick()

        // Relecture : rien n'est encore en base.
        rule.onNodeWithText("2 mg de Testoline à 14:05").assertIsDisplayed()
        assertEquals(0, journal.prises.value.size)

        rule.onNodeWithTag(Tags.ENREGISTRER).performClick()
        val message = texte(R.string.prise_enregistree, "2 mg de Testoline à 14:05")
        attendreTexte(message)
        rule.onNodeWithText(message).assertIsDisplayed()
        rule.onNodeWithText(texte(R.string.annuler)).assertIsDisplayed()
        assertEquals(StatutPrise.CONFIRMEE, journal.prises.value.single().statut)

        rule.onNodeWithText(texte(R.string.annuler)).performClick()
        attendreTexte(texte(R.string.prise_annulee))
        rule.onNodeWithText(texte(R.string.prise_annulee)).assertIsDisplayed()
        rule.onNodeWithText("14:05 — 2 mg de Testoline").assertIsDisplayed()
        assertEquals(StatutPrise.ANNULEE, journal.prises.value.single().statut)
    }

    @Test
    fun annulationDepuisLaListeAvecConfirmation() {
        val journal = FauxJournal()
        val vm = SaisieViewModel(journal, horloge, JourneeCalculator())
        kotlinx.coroutines.runBlocking {
            val med = journal.ajouterMedicament("Testoline B")
            journal.enregistrerPrise(med.id, Quantite(25), Unite.MG, maintenant.minusSeconds(3600))
        }
        rule.setContent { DoseJusteTheme { EcranSaisie(vm) } }

        val ligne = "13:05 — 0,25 mg de Testoline B"
        attendreTexte(ligne)
        rule.onNodeWithText(texte(R.string.annuler_cette_prise)).performClick()
        rule.onNodeWithText(texte(R.string.confirmation_titre)).assertIsDisplayed()
        assertEquals(StatutPrise.CONFIRMEE, journal.prises.value.single().statut)

        rule.onNodeWithText(texte(R.string.confirmation_oui)).performClick()
        attendreTexte(texte(R.string.prise_annulee))
        assertEquals(StatutPrise.ANNULEE, journal.prises.value.single().statut)
    }

    @Test
    fun donneesIllisiblesAfficheUnEcranSansBoutonQuiEfface() {
        val ouverture = OuvertureViewModel({ throw DonneesIllisiblesException.CleKeystoreAbsente() })
        rule.setContent { DoseJusteTheme { fr.dosejuste.app.ui.DoseJusteApp(ouverture, onFermer = {}) } }

        attendreTexte(texte(R.string.illisibles_titre))
        rule.onNodeWithText(texte(R.string.illisibles_texte)).assertIsDisplayed()
        rule.onNodeWithText(texte(R.string.fermer)).assertIsDisplayed()
        for (mot in listOf("Effacer", "Réinitialiser", "Supprimer")) {
            assertEquals(mot, 0, rule.onAllNodesWithText(mot, substring = true).fetchSemanticsNodes().size)
        }
    }
}
