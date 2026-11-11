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

/** Faux journal en mémoire : aucune base, aucune dépendance Android. */
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

    override suspend fun reattribuer(id: Long, journee: LocalDate?): Boolean {
        val courante = prises.value.firstOrNull { it.id == id.toString() } ?: return false
        prises.value = prises.value.map { if (it === courante) it.copy(journeeForcee = journee) else it }
        return true
    }

    /** Ajoute directement une prise (données de test), quel que soit son statut. */
    suspend fun ajouterPrise(
        libelle: String,
        quantite: Quantite,
        unite: Unite,
        horodatage: Instant,
        statut: StatutPrise = StatutPrise.CONFIRMEE,
    ): Long {
        val medicament = listeMedicaments.firstOrNull { it.libelle == libelle } ?: ajouterMedicament(libelle)
        val id = enregistrerPrise(medicament.id, quantite, unite, horodatage)
        if (statut == StatutPrise.ANNULEE) annulerPrise(id)
        return id
    }

    fun prise(id: Long): Prise = prises.value.first { it.id == id.toString() }

    override fun observerPrisesDuJour(journee: LocalDate, calc: JourneeCalculator): Flow<List<Prise>> =
        prises.map { prisesDuJour(it, journee, calc) }

    fun statut(id: Long): StatutPrise = prises.value.first { it.id == id.toString() }.statut
}
