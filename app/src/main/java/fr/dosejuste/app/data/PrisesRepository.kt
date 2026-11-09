package fr.dosejuste.app.data

import fr.dosejuste.app.domain.CleTotal
import fr.dosejuste.app.domain.JourneeCalculator
import fr.dosejuste.app.domain.Prise
import fr.dosejuste.app.domain.Quantite
import fr.dosejuste.app.domain.Unite
import fr.dosejuste.app.domain.prisesDuJour
import fr.dosejuste.app.domain.totauxDuJour
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Accès aux données en types du domaine. Mince : la logique (journée, totaux) reste
 * dans `domain/`.
 *
 * Requête par journée : la base sélectionne les prises de l'intervalle
 * `calc.bornes(journee)` non réattribuées, plus celles réattribuées à `journee`
 * ([PriseDao.deLaJournee]). Le domaine ([prisesDuJour]) refiltre ce résultat : les deux
 * règles doivent toujours concorder, le domaine fait foi.
 */
class PrisesRepository(
    private val base: DoseJusteDatabase,
    private val horloge: Clock = Clock.systemUTC(),
) {
    private val medicaments = base.medicamentDao()
    private val prises = base.priseDao()

    private fun maintenant(): Instant = horloge.instant()

    suspend fun ajouterMedicament(libelle: String): Long {
        require(libelle.isNotBlank()) { "Le libellé du médicament est vide" }
        return medicaments.inserer(MedicamentEntity(libelle = libelle.trim(), creeLe = maintenant()))
    }

    suspend fun listerMedicaments(): List<MedicamentEntity> = medicaments.lister()

    fun observerMedicaments(): Flow<List<MedicamentEntity>> = medicaments.observer()

    /** Enregistre une prise confirmée ; retourne son identifiant. */
    suspend fun enregistrerPrise(medicamentId: Long, quantite: Quantite, unite: Unite, horodatage: Instant): Long =
        prises.inserer(nouvellePriseEntity(medicamentId, quantite, unite, horodatage, maintenant()))

    suspend fun prise(id: Long): Prise? = prises.parId(id)?.versDomaine()

    /** Annule une prise confirmée (la ligne reste). Vrai si une ligne a changé. */
    suspend fun annulerPrise(id: Long): Boolean = prises.annuler(id, maintenant()) == 1

    /** Réattribue la prise à [journee] ; `null` rétablit la journée calculée. */
    suspend fun reattribuer(id: Long, journee: LocalDate?): Boolean =
        prises.reattribuer(id, journee, maintenant()) == 1

    suspend fun prisesDuJour(journee: LocalDate, calc: JourneeCalculator): List<Prise> {
        val bornes = calc.bornes(journee)
        val lues = prises.deLaJournee(bornes.debut, bornes.fin, journee).map { it.versDomaine() }
        return prisesDuJour(lues, journee, calc)
    }

    fun observerPrisesDuJour(journee: LocalDate, calc: JourneeCalculator): Flow<List<Prise>> {
        val bornes = calc.bornes(journee)
        return prises.observerDeLaJournee(bornes.debut, bornes.fin, journee)
            .map { lignes -> prisesDuJour(lignes.map { it.versDomaine() }, journee, calc) }
    }

    fun observerTotauxDuJour(journee: LocalDate, calc: JourneeCalculator): Flow<Map<CleTotal, Quantite>> =
        observerPrisesDuJour(journee, calc).map { totauxDuJour(it, journee, calc) }
}
