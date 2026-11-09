package fr.dosejuste.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicamentDao {

    /** Insère un médicament ; un libellé déjà présent lève une SQLiteConstraintException. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun inserer(medicament: MedicamentEntity): Long

    @Query("SELECT * FROM medicament ORDER BY libelle, id")
    suspend fun lister(): List<MedicamentEntity>

    @Query("SELECT * FROM medicament ORDER BY libelle, id")
    fun observer(): Flow<List<MedicamentEntity>>
}

/**
 * Accès aux prises. Aucune suppression en phase 1 : une prise annulée reste en base.
 */
@Dao
interface PriseDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun inserer(prise: PriseEntity): Long

    @Query("$SELECT_PRISE WHERE prise.id = :id")
    suspend fun parId(id: Long): PriseAvecMedicament?

    /** Passe une prise confirmée en ANNULEE ; la ligne reste. Retourne 1 si modifiée, 0 sinon. */
    @Query("UPDATE prise SET statut = 'ANNULEE', maj_le = :majLe WHERE id = :id AND statut = 'CONFIRMEE'")
    suspend fun annuler(id: Long, majLe: Instant): Int

    /**
     * Rattache la prise à [journee] sans toucher à son horodatage ; `null` rétablit la
     * journée calculée. Retourne le nombre de lignes modifiées.
     */
    @Query("UPDATE prise SET journee_forcee = :journee, maj_le = :majLe WHERE id = :id")
    suspend fun reattribuer(id: Long, journee: LocalDate?, majLe: Instant): Int

    /** Prises (tous statuts, journée forcée ou non) dont l'horodatage est dans [debut, fin). */
    @Query("$SELECT_PRISE WHERE prise.horodatage_utc_ms >= :debut AND prise.horodatage_utc_ms < :fin $ORDRE")
    suspend fun entre(debut: Instant, fin: Instant): List<PriseAvecMedicament>

    /**
     * Prises de la journée [journee] dont les bornes [debut, fin) viennent de
     * `JourneeCalculator.bornes(journee)` :
     * - prises non réattribuées dont l'horodatage est dans l'intervalle ;
     * - prises réattribuées à [journee], quel que soit leur horodatage.
     * Une prise de l'intervalle réattribuée à une autre journée est exclue.
     */
    @Query("$SELECT_PRISE WHERE $FILTRE_JOURNEE $ORDRE")
    suspend fun deLaJournee(debut: Instant, fin: Instant, journee: LocalDate): List<PriseAvecMedicament>

    /** Version observable de [deLaJournee]. */
    @Query("$SELECT_PRISE WHERE $FILTRE_JOURNEE $ORDRE")
    fun observerDeLaJournee(debut: Instant, fin: Instant, journee: LocalDate): Flow<List<PriseAvecMedicament>>

    companion object {
        const val SELECT_PRISE =
            "SELECT prise.*, medicament.libelle AS medicament_libelle FROM prise " +
                "INNER JOIN medicament ON medicament.id = prise.medicament_id"
        const val FILTRE_JOURNEE =
            "((prise.journee_forcee IS NULL AND prise.horodatage_utc_ms >= :debut " +
                "AND prise.horodatage_utc_ms < :fin) OR prise.journee_forcee = :journee)"
        const val ORDRE = "ORDER BY prise.horodatage_utc_ms, prise.id"
    }
}
