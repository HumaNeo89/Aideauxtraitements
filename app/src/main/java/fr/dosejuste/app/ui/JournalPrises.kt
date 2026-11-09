package fr.dosejuste.app.ui

import fr.dosejuste.app.data.DoseJusteDatabase
import fr.dosejuste.app.data.PrisesRepository
import fr.dosejuste.app.domain.JourneeCalculator
import fr.dosejuste.app.domain.Prise
import fr.dosejuste.app.domain.Quantite
import fr.dosejuste.app.domain.Unite
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** Médicament de la liste personnelle (libellé libre saisi par la personne). */
data class Medicament(val id: Long, val libelle: String)

/**
 * Interface fine au-dessus de [PrisesRepository] : ce dont l'écran de saisie a besoin,
 * remplaçable par un faux en mémoire dans les tests (JVM et instrumentés).
 */
interface JournalPrises {
    suspend fun medicaments(): List<Medicament>

    /** Ajoute un libellé (déjà nettoyé et contrôlé par l'appelant) et le retourne. */
    suspend fun ajouterMedicament(libelle: String): Medicament

    /** Enregistre une prise CONFIRMEE ; retourne son identifiant. */
    suspend fun enregistrerPrise(medicamentId: Long, quantite: Quantite, unite: Unite, horodatage: Instant): Long

    /** Passe la prise en ANNULEE (la ligne reste). Faux si rien n'a changé. */
    suspend fun annulerPrise(id: Long): Boolean

    /** Réattribue la prise à [journee] ; `null` rétablit la journée calculée. Faux si rien n'a changé. */
    suspend fun reattribuer(id: Long, journee: LocalDate?): Boolean

    fun observerPrisesDuJour(journee: LocalDate, calc: JourneeCalculator): Flow<List<Prise>>
}

/** Implémentation de production : le repository Room (base chiffrée). */
class JournalRoom(
    private val depot: PrisesRepository,
    private val base: DoseJusteDatabase,
) : JournalPrises, AutoCloseable {

    override suspend fun medicaments(): List<Medicament> =
        depot.listerMedicaments().map { Medicament(it.id, it.libelle) }

    override suspend fun ajouterMedicament(libelle: String): Medicament {
        val id = depot.ajouterMedicament(libelle)
        return Medicament(id, libelle.trim())
    }

    override suspend fun enregistrerPrise(
        medicamentId: Long,
        quantite: Quantite,
        unite: Unite,
        horodatage: Instant,
    ): Long = depot.enregistrerPrise(medicamentId, quantite, unite, horodatage)

    override suspend fun annulerPrise(id: Long): Boolean = depot.annulerPrise(id)

    override suspend fun reattribuer(id: Long, journee: LocalDate?): Boolean = depot.reattribuer(id, journee)

    override fun observerPrisesDuJour(journee: LocalDate, calc: JourneeCalculator): Flow<List<Prise>> =
        depot.observerPrisesDuJour(journee, calc)

    override fun close() {
        base.close()
    }
}
