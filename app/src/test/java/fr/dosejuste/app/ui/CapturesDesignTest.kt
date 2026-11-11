package fr.dosejuste.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import fr.dosejuste.app.R
import fr.dosejuste.app.domain.Quantite
import fr.dosejuste.app.domain.StatutPrise
import fr.dosejuste.app.domain.Unite
import fr.dosejuste.app.ui.theme.DoseJusteTheme
import java.io.File
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Captures PNG du design (clair et sombre), pour voir les écrans sans téléphone.
 * Aucune comparaison avec une image de référence : ces tests écrivent seulement les fichiers
 * dans app/build/captures-design/. Faux journal et faux réglages, horloge fixe, exemples fictifs.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class CapturesDesignTest {

    @get:Rule
    val rule = createComposeRule()

    private val paris = ZoneId.of("Europe/Paris")
    private val horloge = Clock.fixed(LocalDateTime.parse("2026-11-10T14:05:30").atZone(paris).toInstant(), paris)
    private val dossier = File("build/captures-design").apply { mkdirs() }

    private var sombre by mutableStateOf(false)

    private fun journalAvecPrises(): FauxJournal = FauxJournal().also { j ->
        runBlocking {
            j.ajouterPrise("Testoline", Quantite(200), Unite.MG, aParis("2026-11-10T08:00"))
            j.ajouterPrise("Testoline", Quantite(100), Unite.MG, aParis("2026-11-10T09:30"), StatutPrise.ANNULEE)
            j.ajouterPrise("Testoline B", Quantite(50), Unite.COMPRIME, aParis("2026-11-10T12:15"))
        }
    }

    private fun aParis(texte: String) = LocalDateTime.parse(texte).atZone(paris).toInstant()

    private fun texte(id: Int): String = ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)

    private fun attendreTexte(texte: String) {
        rule.waitUntil(5_000) { rule.onAllNodesWithText(texte, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    /** Affiche [contenu] dans le thème de l'appli, puis capture en clair et en sombre. */
    private fun capturer(nom: String, cible: () -> SemanticsNodeInteraction = { rule.onRoot() }, avant: () -> Unit = {}, contenu: @Composable () -> Unit) {
        rule.setContent { DoseJusteTheme(sombre = sombre) { contenu() } }
        avant()
        for (mode in listOf(false, true)) {
            sombre = mode
            rule.waitForIdle()
            ecrire(cible().captureToImage().asAndroidBitmap(), "$nom-${if (mode) "sombre" else "clair"}.png")
        }
    }

    private fun ecrire(image: Bitmap, nom: String) {
        File(dossier, nom).outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun avertissement() = capturer("avertissement") { EcranAvertissement(onAccepter = {}) }

    @Test
    fun journee() {
        val journal = journalAvecPrises()
        val vm = SaisieViewModel(journal, horloge)
        val jour = JourneeViewModel(journal, FauxReglages(), horloge)
        capturer("journee", avant = { attendreTexte("Testoline : 2 mg") }) {
            EcranAujourdhui(vm, jour, Navigation({}, {}, {}))
        }
    }

    @Test
    fun journeeVide() {
        val journal = FauxJournal()
        val vm = SaisieViewModel(journal, horloge)
        val jour = JourneeViewModel(journal, FauxReglages(), horloge)
        capturer("journee-vide", avant = { attendreTexte(texte(R.string.liste_vide)) }) {
            EcranAujourdhui(vm, jour, Navigation({}, {}, {}))
        }
    }

    private fun formulaireRempli(): Pair<SaisieViewModel, JourneeViewModel> {
        val journal = FauxJournal()
        runBlocking { journal.ajouterMedicament("Testoline") }
        val vm = SaisieViewModel(journal, horloge)
        val jour = JourneeViewModel(journal, FauxReglages(), horloge)
        vm.ouvrirFormulaire()
        return vm to jour
    }

    @Test
    fun saisie() {
        val (vm, jour) = formulaireRempli()
        capturer("saisie", avant = {
            attendreTexte("Testoline")
            rule.waitUntil(5_000) { vm.etat.value.medicaments.isNotEmpty() }
            vm.choisirMedicament(vm.etat.value.medicaments.first().id)
            vm.saisirQuantite("2")
        }) { EcranAujourdhui(vm, jour, Navigation({}, {}, {})) }
    }

    @Test
    fun dialogueConfirmation() {
        val (vm, jour) = formulaireRempli()
        capturer(
            "dialogue-confirmation",
            cible = { rule.onNode(isDialog()) },
            avant = {
                rule.waitUntil(5_000) { vm.etat.value.medicaments.isNotEmpty() }
                vm.choisirMedicament(vm.etat.value.medicaments.first().id)
                vm.saisirQuantite("2")
                vm.relire()
                attendreTexte("2 mg de Testoline")
            },
        ) { EcranAujourdhui(vm, jour, Navigation({}, {}, {})) }
    }

    @Test
    fun historique() {
        val jour = JourneeViewModel(journalAvecPrises(), FauxReglages(), horloge)
        capturer("historique", avant = { attendreTexte("Testoline : 2 mg") }) { EcranHistorique(jour, onRetour = {}) }
    }

    @Test
    fun reglages() {
        val vm = ReglagesViewModel(FauxReglages())
        capturer("reglages") { EcranReglages(vm, onRetour = {}) }
    }

    @Test
    fun licences() = capturer("licences") { EcranLicences(onRetour = {}) }

    @Test
    fun iconeLanceur() {
        val contexte = ApplicationProvider.getApplicationContext<android.content.Context>()
        val icone = contexte.getDrawable(R.mipmap.ic_launcher) as AdaptiveIconDrawable
        val taille = 512

        val couleur = Bitmap.createBitmap(taille, taille, Bitmap.Config.ARGB_8888)
        icone.setBounds(0, 0, taille, taille)
        icone.draw(Canvas(couleur))
        ecrire(couleur, "ic_launcher.png")

        // Icône thématique (Android 13+) : la couche monochrome teintée sur un fond uni.
        val mono = Bitmap.createBitmap(taille, taille, Bitmap.Config.ARGB_8888)
        val toile = Canvas(mono)
        val demi = taille / 2f
        toile.clipPath(android.graphics.Path().apply { addCircle(demi, demi, demi, android.graphics.Path.Direction.CW) })
        toile.drawColor(Color.parseColor("#DDE5E3"))
        icone.monochrome!!.mutate().apply {
            setTint(Color.parseColor("#1F3B37"))
            // La couche fait 108 dp dont 72 dp visibles : on agrandit comme le lanceur.
            val marge = taille / 4
            setBounds(-marge, -marge, taille + marge, taille + marge)
            draw(toile)
        }
        ecrire(mono, "ic_launcher-monochrome.png")
    }
}
