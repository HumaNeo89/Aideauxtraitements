package fr.dosejuste.app.data

import fr.dosejuste.app.domain.Prise
import fr.dosejuste.app.domain.Quantite
import fr.dosejuste.app.domain.StatutPrise
import fr.dosejuste.app.domain.Unite
import java.time.Instant

/**
 * Prise du domaine. L'identifiant Room (Long) devient l'identifiant texte du domaine.
 *
 * @throws ValeurStockeeInvalideException si la ligne viole une règle du domaine
 *   (quantité nulle ou négative, libellé vide).
 */
fun PriseAvecMedicament.versDomaine(): Prise {
    if (prise.quantiteCentiemes <= 0) {
        throw ValeurStockeeInvalideException("Quantité non positive en base (prise ${prise.id})")
    }
    if (medicamentLibelle.isBlank()) {
        throw ValeurStockeeInvalideException("Libellé de médicament vide en base (prise ${prise.id})")
    }
    return Prise(
        id = prise.id.toString(),
        medicament = medicamentLibelle,
        quantite = Quantite(prise.quantiteCentiemes),
        unite = prise.unite,
        horodatage = prise.horodatage,
        statut = prise.statut,
        journeeForcee = prise.journeeForcee,
    )
}

/** Nouvelle ligne de prise confirmée (identifiant attribué par Room à l'insertion). */
fun nouvellePriseEntity(
    medicamentId: Long,
    quantite: Quantite,
    unite: Unite,
    horodatage: Instant,
    maintenant: Instant,
): PriseEntity = PriseEntity(
    medicamentId = medicamentId,
    quantiteCentiemes = quantite.centiemes,
    unite = unite,
    horodatage = horodatage,
    statut = StatutPrise.CONFIRMEE,
    journeeForcee = null,
    creeLe = maintenant,
    majLe = maintenant,
)

/** Identifiant Room d'une prise du domaine issue de [versDomaine]. */
fun Prise.idBase(): Long =
    id.toLongOrNull() ?: throw IllegalArgumentException("Identifiant de prise non issu de la base")
