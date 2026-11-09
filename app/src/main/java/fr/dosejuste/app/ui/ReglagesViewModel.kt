package fr.dosejuste.app.ui

import androidx.lifecycle.ViewModel
import fr.dosejuste.app.prefs.HEURES_PIVOT
import fr.dosejuste.app.prefs.Reglages
import kotlinx.coroutines.flow.StateFlow

/** Heure pivot et avertissement de premier lancement, devant les [Reglages] locaux. */
class ReglagesViewModel(private val reglages: Reglages) : ViewModel() {

    val pivotHeure: StateFlow<Int> = reglages.pivotHeure
    val avertissementAccepte: StateFlow<Boolean> = reglages.avertissementAccepte

    /** Ignore une heure hors de 0..23. */
    fun choisirPivot(heure: Int) {
        if (heure in HEURES_PIVOT) reglages.definirPivotHeure(heure)
    }

    fun accepterAvertissement() = reglages.accepterAvertissement()
}
