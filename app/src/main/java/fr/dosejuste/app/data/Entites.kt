package fr.dosejuste.app.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import fr.dosejuste.app.domain.StatutPrise
import fr.dosejuste.app.domain.Unite
import java.time.Instant
import java.time.LocalDate

/*
 * Pas de table « journée » : la journée d'une prise se recalcule depuis son horodatage
 * réel et l'heure pivot (JourneeCalculator, choix E2). La stocker figerait le pivot du
 * moment de la saisie : changer le pivot ou corriger un fuseau laisserait des journées
 * fausses en base. Seule la réattribution manuelle est persistée (journee_forcee).
 *
 * Contraintes vérifiées en SQL par des déclencheurs (voir ContraintesSql), Room ne
 * générant pas de CHECK : libellé non vide, quantité > 0, unité et statut connus,
 * journée forcée au format yyyy-MM-dd.
 */

/** Médicament saisi par la personne. Le libellé est unique. */
@Entity(
    tableName = "medicament",
    indices = [Index(value = ["libelle"], unique = true)],
)
data class MedicamentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val libelle: String,
    @ColumnInfo(name = "cree_le") val creeLe: Instant,
)

/**
 * Prise enregistrée. [horodatage] est l'instant réel (UTC, millisecondes) et n'est jamais
 * modifié ; une prise n'est jamais supprimée en phase 1 (annulation = statut ANNULEE).
 */
@Entity(
    tableName = "prise",
    foreignKeys = [
        ForeignKey(
            entity = MedicamentEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicament_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["horodatage_utc_ms"]),
        Index(value = ["medicament_id"]),
        Index(value = ["journee_forcee"]),
    ],
)
data class PriseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "medicament_id") val medicamentId: Long,
    @ColumnInfo(name = "quantite_centiemes") val quantiteCentiemes: Long,
    val unite: Unite,
    @ColumnInfo(name = "horodatage_utc_ms") val horodatage: Instant,
    val statut: StatutPrise,
    @ColumnInfo(name = "journee_forcee") val journeeForcee: LocalDate?,
    @ColumnInfo(name = "cree_le") val creeLe: Instant,
    @ColumnInfo(name = "maj_le") val majLe: Instant,
)

/** Autre nom d'un médicament. Table prévue par le plan, sans usage en phase 1. */
@Entity(
    tableName = "alias",
    foreignKeys = [
        ForeignKey(
            entity = MedicamentEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicament_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index(value = ["medicament_id"])],
)
data class AliasEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "medicament_id") val medicamentId: Long,
    val texte: String,
)

/** Résultat de jointure : une prise et le libellé de son médicament. */
data class PriseAvecMedicament(
    @Embedded val prise: PriseEntity,
    @ColumnInfo(name = "medicament_libelle") val medicamentLibelle: String,
)
