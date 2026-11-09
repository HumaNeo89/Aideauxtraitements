package fr.dosejuste.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.dosejuste.app.domain.JourneeCalculator
import fr.dosejuste.app.domain.Quantite
import fr.dosejuste.app.domain.RaisonErreur
import fr.dosejuste.app.domain.ResultatAnalyse
import fr.dosejuste.app.domain.Unite
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Refus d'un libellé de médicament. */
enum class ErreurLibelle { VIDE, DOUBLON }

/** Champs du formulaire « Nouvelle prise » et erreurs de validation (montrées à « Relire »). */
data class Formulaire(
    val medicamentId: Long? = null,
    val quantiteTexte: String = "",
    val unite: Unite = Unite.MG,
    val jour: JourSaisie = JourSaisie.AUJOURDHUI,
    val heure: LocalTime = LocalTime.MIDNIGHT,
    val libelleTexte: String = "",
    val erreurMedicamentManquant: Boolean = false,
    val erreurLibelle: ErreurLibelle? = null,
    val erreurQuantite: RaisonErreur? = null,
    val erreurHeureFuture: Boolean = false,
)

/** Prise validée, présentée pour relecture. Rien n'est écrit en base à ce stade. */
data class Relecture(
    val medicament: Medicament,
    val quantite: Quantite,
    val unite: Unite,
    val instant: Instant,
    val phrase: String,
)

/** Annulation rapide offerte pendant le délai (10 s) après un enregistrement. */
data class AnnulationRapide(val priseId: Long, val phrase: String)

data class SaisieUiState(
    val medicaments: List<Medicament> = emptyList(),
    val formulaire: Formulaire? = null,
    val relecture: Relecture? = null,
    val annulationRapide: AnnulationRapide? = null,
    val erreurOperation: Boolean = false,
)

/**
 * Logique du formulaire de saisie et de l'annulation rapide (les totaux et la liste d'une
 * journée sont dans [JourneeViewModel]). Dépendances injectables (journal, horloge, fuseau du
 * calculateur de journée, délai d'annulation) pour être testée sur la JVM avec
 * kotlinx-coroutines-test.
 *
 * Délai d'annulation : [delaiAnnulation] (10 s), piloté par `delay` ; à l'échéance
 * [SaisieUiState.annulationRapide] repasse à `null` et [annulerRapide] ne fait plus rien.
 * Un nouvel enregistrement remplace l'annulation rapide précédente (l'ancienne prise reste
 * annulable depuis la liste).
 */
class SaisieViewModel(
    private val journal: JournalPrises,
    private val horloge: Clock = Clock.systemUTC(),
    private val calc: JourneeCalculator = JourneeCalculator(),
    private val delaiAnnulation: Duration = 10.seconds,
) : ViewModel() {

    private val _etat = MutableStateFlow(SaisieUiState())
    val etat: StateFlow<SaisieUiState> = _etat.asStateFlow()

    private var minuteur: Job? = null
    private var enregistrementEnCours = false

    init {
        viewModelScope.launch { rafraichirMedicaments() }
    }

    // ---- Formulaire ----

    fun ouvrirFormulaire() {
        _etat.update { it.copy(formulaire = Formulaire(heure = maintenantLocal()), relecture = null) }
    }

    fun fermerFormulaire() {
        _etat.update { it.copy(formulaire = null, relecture = null) }
    }

    private fun maintenantLocal(): LocalTime =
        horloge.instant().atZone(calc.zone).toLocalTime().truncatedTo(ChronoUnit.MINUTES)

    private fun modifierFormulaire(transformation: (Formulaire) -> Formulaire) {
        _etat.update { s -> s.formulaire?.let { s.copy(formulaire = transformation(it)) } ?: s }
    }

    fun choisirMedicament(id: Long) =
        modifierFormulaire { it.copy(medicamentId = id, erreurMedicamentManquant = false) }

    fun saisirQuantite(texte: String) =
        modifierFormulaire { it.copy(quantiteTexte = texte, erreurQuantite = null) }

    fun choisirUnite(unite: Unite) = modifierFormulaire { it.copy(unite = unite) }

    fun choisirJour(jour: JourSaisie) = modifierFormulaire { it.copy(jour = jour, erreurHeureFuture = false) }

    fun choisirHeure(heure: LocalTime) =
        modifierFormulaire { it.copy(heure = heure.truncatedTo(ChronoUnit.MINUTES), erreurHeureFuture = false) }

    fun saisirLibelle(texte: String) = modifierFormulaire { it.copy(libelleTexte = texte, erreurLibelle = null) }

    /** Ajoute le libellé saisi à la liste personnelle et le sélectionne. Refus : vide ou doublon. */
    fun ajouterMedicament() {
        val formulaire = _etat.value.formulaire ?: return
        val libelle = formulaire.libelleTexte.trim()
        val erreur = when {
            libelle.isEmpty() -> ErreurLibelle.VIDE
            _etat.value.medicaments.any { it.libelle.equals(libelle, ignoreCase = true) } -> ErreurLibelle.DOUBLON
            else -> null
        }
        if (erreur != null) {
            modifierFormulaire { it.copy(erreurLibelle = erreur) }
            return
        }
        viewModelScope.launch {
            try {
                val ajoute = journal.ajouterMedicament(libelle)
                rafraichirMedicaments()
                modifierFormulaire {
                    it.copy(
                        medicamentId = ajoute.id,
                        libelleTexte = "",
                        erreurLibelle = null,
                        erreurMedicamentManquant = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _etat.update { it.copy(erreurOperation = true) }
            }
        }
    }

    private suspend fun rafraichirMedicaments() {
        try {
            val liste = journal.medicaments()
            _etat.update { it.copy(medicaments = liste) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _etat.update { it.copy(erreurOperation = true) }
        }
    }

    // ---- Relecture et enregistrement ----

    /** Valide le formulaire ; s'il est correct, ouvre la relecture (rien n'est écrit). */
    fun relire() {
        val etat = _etat.value
        val f = etat.formulaire ?: return
        val medicament = etat.medicaments.firstOrNull { it.id == f.medicamentId }
        val analyse = Quantite.analyser(f.quantiteTexte)
        val instant = instantChoisi(f)
        val futur = instant > horloge.instant()

        val erreurQuantite = (analyse as? ResultatAnalyse.Erreur)?.raison
        if (medicament == null || analyse !is ResultatAnalyse.Ok || futur) {
            modifierFormulaire {
                it.copy(
                    erreurMedicamentManquant = medicament == null,
                    erreurQuantite = erreurQuantite,
                    erreurHeureFuture = futur,
                )
            }
            return
        }
        val phrase = PhrasePrise.relecture(analyse.quantite, f.unite, medicament.libelle, f.jour, f.heure)
        _etat.update {
            it.copy(
                formulaire = f.copy(erreurMedicamentManquant = false, erreurQuantite = null, erreurHeureFuture = false),
                relecture = Relecture(medicament, analyse.quantite, f.unite, instant, phrase),
            )
        }
    }

    /** « Modifier » : retour au formulaire, valeurs conservées. */
    fun modifier() {
        _etat.update { it.copy(relecture = null) }
    }

    private fun instantChoisi(f: Formulaire): Instant {
        val aujourdhui = horloge.instant().atZone(calc.zone).toLocalDate()
        val date = if (f.jour == JourSaisie.HIER) aujourdhui.minusDays(1) else aujourdhui
        return date.atTime(f.heure).atZone(calc.zone).toInstant()
    }

    /** « Enregistrer » : seule étape qui écrit en base. */
    fun enregistrer() {
        val relecture = _etat.value.relecture ?: return
        if (enregistrementEnCours) return
        enregistrementEnCours = true
        viewModelScope.launch {
            try {
                val id = journal.enregistrerPrise(
                    relecture.medicament.id,
                    relecture.quantite,
                    relecture.unite,
                    relecture.instant,
                )
                _etat.update { it.copy(formulaire = null, relecture = null) }
                demarrerAnnulationRapide(id, relecture.phrase)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _etat.update { it.copy(relecture = null, erreurOperation = true) }
            } finally {
                enregistrementEnCours = false
            }
        }
    }

    // ---- Annulation ----

    private fun demarrerAnnulationRapide(priseId: Long, phrase: String) {
        minuteur?.cancel()
        _etat.update { it.copy(annulationRapide = AnnulationRapide(priseId, phrase)) }
        minuteur = viewModelScope.launch {
            delay(delaiAnnulation)
            _etat.update { it.copy(annulationRapide = null) }
        }
    }

    /** Action « Annuler » du Snackbar : sans effet une fois le délai écoulé. */
    fun annulerRapide() {
        val annulation = _etat.value.annulationRapide ?: return
        minuteur?.cancel()
        _etat.update { it.copy(annulationRapide = null) }
        viewModelScope.launch { annuler(annulation.priseId) }
    }

    /** Le Snackbar a été fermé (glissé) : l'action rapide n'est plus offerte. */
    fun fermerAnnulationRapide() {
        minuteur?.cancel()
        _etat.update { it.copy(annulationRapide = null) }
    }

    private suspend fun annuler(id: Long) {
        try {
            journal.annulerPrise(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _etat.update { it.copy(erreurOperation = true) }
        }
    }

    fun acquitterErreur() {
        _etat.update { it.copy(erreurOperation = false) }
    }
}
