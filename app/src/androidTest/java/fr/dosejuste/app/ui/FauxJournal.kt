package fr.dosejuste.app.ui

import fr.dosejuste.app.domain.JourneeCalculator
import fr.dosejuste.app.domain.Prise
import fr.dosejuste.app.domain.Quantite
import fr.dosejuste.app.domain.StatutPrise
import fr.dosejuste.app.domain.Unite
import fr.dosejuste.app.domain.prisesDuJour
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Faux journal en mémoire pour les tests Compose : jamais la vraie base de la personne
 * ni l'alias Keystore de production.
 */
class FauxJournal : JournalPrises {
    val listeMedicaments = mutableListOf<Medicament>()
    val prises = MutableStateFlow<List<Prise>>(emptyList())
    private var prochainId = 1L

    override suspend fun medicaments(): List<Medicament> = listeMedicaments.toList()

    override suspend fun ajouterMedicament(libelle: String): Medicament {
        val m = Medicament(prochainId++, libelle)
        listeMedicaments += m
        return m
    }

    override suspend fun enregistrerPrise(
        medicamentId: Long,
        quantite: Quantite,
        unite: Unite,
        horodatage: Instant,
    ): Long {
        val id = prochainId++
        val libelle = listeMedicaments.first { it.id == medicamentId }.libelle
        prises.value += Prise(id.toString(), libelle, quantite, unite, horodatage)
        return id
    }

    override suspend fun annulerPrise(id: Long): Boolean {
        val courante = prises.value.firstOrNull { it.id == id.toString() }
        if (courante == null || courante.statut != StatutPrise.CONFIRMEE) return false
        prises.value = prises.value.map { if (it === courante) it.copy(statut = StatutPrise.ANNULEE) else it }
        return true
    }

    override fun observerPrisesDuJour(journee: LocalDate, calc: JourneeCalculator): Flow<List<Prise>> =
        prises.map { prisesDuJour(it, journee, calc) }
}
