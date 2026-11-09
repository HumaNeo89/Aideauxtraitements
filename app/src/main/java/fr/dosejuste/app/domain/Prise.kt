package fr.dosejuste.app.domain

import java.time.Instant
import java.time.LocalDate

/** Statut d'une prise. Une prise annulée est conservée mais n'entre pas dans les totaux. */
enum class StatutPrise { CONFIRMEE, ANNULEE }

/**
 * Prise enregistrée (modèle de domaine, sans persistance).
 *
 * [horodatage] est l'instant réel de la prise et n'est jamais modifié.
 * [journeeForcee], si présente, remplace la journée calculée (réattribution manuelle).
 */
data class Prise(
    val id: String,
    val medicament: String,
    val quantite: Quantite,
    val unite: Unite,
    val horodatage: Instant,
    val statut: StatutPrise = StatutPrise.CONFIRMEE,
    val journeeForcee: LocalDate? = null,
) {
    init {
        require(medicament.isNotBlank()) { "Le libellé du médicament est vide" }
    }

    /** Journée retenue : la journée forcée, sinon celle calculée depuis [horodatage]. */
    fun journeeEffective(calc: JourneeCalculator): LocalDate =
        journeeForcee ?: calc.journeeDe(horodatage)
}

/** Clé d'un total : un même médicament dans une même unité. */
data class CleTotal(val medicament: String, val unite: Unite)

/** Copie de [prise] rattachée à [nouvelleJournee] ; l'horodatage est inchangé. */
fun reattribuer(prise: Prise, nouvelleJournee: LocalDate): Prise =
    prise.copy(journeeForcee = nouvelleJournee)

/** Copie de [prise] sans réattribution : la journée redevient celle calculée. */
fun annulerReattribution(prise: Prise): Prise = prise.copy(journeeForcee = null)

/** Prises (tous statuts) dont la journée effective est [journee], triées par horodatage. */
fun prisesDuJour(prises: Iterable<Prise>, journee: LocalDate, calc: JourneeCalculator): List<Prise> =
    prises
        .filter { it.journeeEffective(calc) == journee }
        .sortedWith(compareBy<Prise> { it.horodatage }.thenBy { it.id })

/**
 * Somme des quantités confirmées de [journee], par médicament et par unité.
 * Les prises annulées sont exclues ; des unités différentes ne sont jamais additionnées.
 */
fun totauxDuJour(prises: Iterable<Prise>, journee: LocalDate, calc: JourneeCalculator): Map<CleTotal, Quantite> {
    val totaux = LinkedHashMap<CleTotal, Quantite>()
    for (prise in prisesDuJour(prises, journee, calc)) {
        if (prise.statut != StatutPrise.CONFIRMEE) continue
        val cle = CleTotal(prise.medicament, prise.unite)
        totaux[cle] = totaux[cle]?.plus(prise.quantite) ?: prise.quantite
    }
    return totaux
}
