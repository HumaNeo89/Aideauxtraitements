package fr.dosejuste.app.ui

import fr.dosejuste.app.prefs.HEURES_PIVOT
import fr.dosejuste.app.prefs.PIVOT_HEURE_DEFAUT
import fr.dosejuste.app.prefs.Reglages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Réglages en mémoire pour les tests Compose : jamais les vraies préférences de la personne. */
class FauxReglages(pivot: Int = PIVOT_HEURE_DEFAUT, accepte: Boolean = true) : Reglages {
    private val _pivot = MutableStateFlow(pivot)
    private val _accepte = MutableStateFlow(accepte)
    override val pivotHeure: StateFlow<Int> = _pivot.asStateFlow()
    override val avertissementAccepte: StateFlow<Boolean> = _accepte.asStateFlow()

    override fun definirPivotHeure(heure: Int) {
        require(heure in HEURES_PIVOT)
        _pivot.value = heure
    }

    override fun accepterAvertissement() {
        _accepte.value = true
    }
}
