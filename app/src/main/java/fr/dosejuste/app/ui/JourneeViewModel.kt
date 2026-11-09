package fr.dosejuste.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.dosejuste.app.domain.JourneeCalculator
import fr.dosejuste.app.domain.Prise
import fr.dosejuste.app.domain.StatutPrise
import fr.dosejuste.app.domain.totauxDuJour
import fr.dosejuste.app.prefs.Reglages
import fr.dosejuste.app.prefs.pivotVersHeure
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Ligne de la liste d'une journée (annulées incluses, marquées). */
data class LigneJournee(
    val id: Long,
    val texte: String,
    val annulee: Boolean,
    /** Vrai si la prise a été rattachée à la main à une autre journée que celle calculée. */
    val reattribuee: Boolean,
    /** Journée que donnerait l'horodatage avec le pivot actuel. */
    val journeeCalculee: LocalDate,
)

data class JourneeUiState(
    val journee: LocalDate,
    /** Vrai si [journee] est la journée en cours (on ne va pas au-delà). */
    val estAujourdhui: Boolean = true,
    /** Une ligne par médicament et par unité, jamais additionnées entre elles. */
    val totaux: List<String> = emptyList(),
    val prises: List<LigneJournee> = emptyList(),
    val priseAAnnuler: LigneJournee? = null,
    val erreurOperation: Boolean = false,
)

private data class VueJournee(
    val journee: LocalDate,
    val estAujourdhui: Boolean,
    val totaux: List<String>,
    val prises: List<LigneJournee>,
)

/**
 * Totaux et liste d'une journée (écrans Aujourd'hui et Historique).
 *
 * La journée se calcule à chaque observation depuis l'horloge, le fuseau et l'heure pivot
 * des [Reglages] : changer le pivot relance l'observation, rien n'est figé. [decalage] = nombre
 * de jours avant la journée en cours (0 = journée en cours, jamais négatif).
 * Les totaux ne comptent que les prises confirmées, par médicament et par unité.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class JourneeViewModel(
    private val journal: JournalPrises,
    private val reglages: Reglages,
    private val horloge: Clock = Clock.systemUTC(),
    private val zone: ZoneId = JourneeCalculator.ZONE_DEFAUT,
) : ViewModel() {

    private val decalage = MutableStateFlow(0)
    private val actualisation = MutableStateFlow(0)

    private val _etat = MutableStateFlow(JourneeUiState(journee = calculateur(reglages.pivotHeure.value).journeeDe(horloge.instant())))
    val etat: StateFlow<JourneeUiState> = _etat.asStateFlow()

    init {
        viewModelScope.launch {
            combine(reglages.pivotHeure, decalage, actualisation) { heure, jours, _ -> heure to jours }
                .flatMapLatest { (heure, jours) -> observer(heure, jours) }
                .catch { _etat.update { it.copy(erreurOperation = true) } }
                .collect { vue ->
                    _etat.update {
                        it.copy(
                            journee = vue.journee,
                            estAujourdhui = vue.estAujourdhui,
                            totaux = vue.totaux,
                            prises = vue.prises,
                        )
                    }
                }
        }
    }

    private fun calculateur(heure: Int) = JourneeCalculator(zone, pivotVersHeure(heure))

    private fun observer(heure: Int, jours: Int) = calculateur(heure).let { calc ->
        val aujourdhui = calc.journeeDe(horloge.instant())
        val journee = aujourdhui.minusDays(jours.toLong())
        journal.observerPrisesDuJour(journee, calc).map { prises -> construire(journee, aujourdhui, calc, prises) }
    }

    private fun construire(journee: LocalDate, aujourdhui: LocalDate, calc: JourneeCalculator, prises: List<Prise>) =
        VueJournee(
            journee = journee,
            estAujourdhui = journee == aujourdhui,
            totaux = totauxDuJour(prises, journee, calc).map { (cle, quantite) ->
                PhrasePrise.total(cle.medicament, quantite, cle.unite)
            },
            prises = prises.mapNotNull { versLigne(it, calc) },
        )

    private fun versLigne(prise: Prise, calc: JourneeCalculator): LigneJournee? {
        val id = prise.id.toLongOrNull() ?: return null
        val heure = prise.horodatage.atZone(calc.zone).toLocalTime()
        return LigneJournee(
            id = id,
            texte = PhrasePrise.ligne(prise.quantite, prise.unite, prise.medicament, heure),
            annulee = prise.statut == StatutPrise.ANNULEE,
            reattribuee = prise.journeeForcee != null,
            journeeCalculee = calc.journeeDe(prise.horodatage),
        )
    }

    // ---- Navigation entre journées ----

    fun journeePrecedente() {
        decalage.update { it + 1 }
    }

    /** Sans effet sur la journée en cours : on ne va pas au-delà d'aujourd'hui. */
    fun journeeSuivante() {
        decalage.update { if (it > 0) it - 1 else 0 }
    }

    /** À rappeler au retour au premier plan : la journée en cours a pu changer. */
    fun actualiser() {
        actualisation.update { it + 1 }
    }

    // ---- Annulation (avec confirmation) ----

    fun demanderAnnulation(id: Long) {
        val ligne = _etat.value.prises.firstOrNull { it.id == id && !it.annulee } ?: return
        _etat.update { it.copy(priseAAnnuler = ligne) }
    }

    fun abandonnerAnnulation() {
        _etat.update { it.copy(priseAAnnuler = null) }
    }

    fun confirmerAnnulation() {
        val ligne = _etat.value.priseAAnnuler ?: return
        _etat.update { it.copy(priseAAnnuler = null) }
        agir { journal.annulerPrise(ligne.id) }
    }

    // ---- Réattribution (prises confirmées seulement) ----

    /** Rattache la prise à la journée précédant celle affichée. */
    fun reattribuerALaVeille(id: Long) = reattribuer(id) { it.minusDays(1) }

    /** Rattache la prise à la journée suivant celle affichée, sauf si elle est dans le futur. */
    fun reattribuerAuLendemain(id: Long) {
        if (_etat.value.estAujourdhui) return
        reattribuer(id) { it.plusDays(1) }
    }

    /** Rétablit la journée calculée depuis l'horodatage. */
    fun retablirJournee(id: Long) {
        val ligne = _etat.value.prises.firstOrNull { it.id == id && !it.annulee && it.reattribuee } ?: return
        agir { journal.reattribuer(ligne.id, null) }
    }

    private fun reattribuer(id: Long, cible: (LocalDate) -> LocalDate) {
        val etat = _etat.value
        val ligne = etat.prises.firstOrNull { it.id == id && !it.annulee } ?: return
        val journee = cible(etat.journee)
        // Revenir à la journée calculée revient à ne rien forcer.
        val forcee = if (journee == ligne.journeeCalculee) null else journee
        agir { journal.reattribuer(ligne.id, forcee) }
    }

    private fun agir(operation: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                operation()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _etat.update { it.copy(erreurOperation = true) }
            }
        }
    }

    fun acquitterErreur() {
        _etat.update { it.copy(erreurOperation = false) }
    }
}
