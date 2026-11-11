package fr.dosejuste.app.prefs

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Heure pivot par défaut : 5 h (même valeur que `JourneeCalculator.PIVOT_DEFAUT`). */
const val PIVOT_HEURE_DEFAUT: Int = 5

/** Heures pivot proposées : 0 h à 23 h. */
val HEURES_PIVOT: IntRange = 0..23

/** Pivot sous forme d'heure locale pleine (ex. 5 → 05:00). */
fun pivotVersHeure(heure: Int): LocalTime = LocalTime.of(heure, 0)

/**
 * Réglages locaux de l'appli : heure pivot et acceptation de l'avertissement.
 * Interface fine pour être remplacée par un faux dans les tests.
 */
interface Reglages {
    /** Heure pivot, de 0 à 23. */
    val pivotHeure: StateFlow<Int>

    /** Hors de 0..23 : [IllegalArgumentException]. */
    fun definirPivotHeure(heure: Int)

    val avertissementAccepte: StateFlow<Boolean>

    fun accepterAvertissement()
}

/**
 * Réglages en SharedPreferences privées (`shared_prefs/dosejuste_reglages.xml`).
 *
 * Ce fichier est dans le domaine de sauvegarde « sharedpref », exclu par
 * `data_extraction_rules.xml` et `backup_rules.xml` (vérifié par EmplacementsSauvegardeTest).
 * Il ne contient ni médicament, ni quantité, ni clé : seulement un entier et un booléen.
 */
class ReglagesPrefs(contexte: Context) : Reglages {

    private val prefs: SharedPreferences =
        contexte.applicationContext.getSharedPreferences(NOM_FICHIER, Context.MODE_PRIVATE)

    private val _pivot = MutableStateFlow(lirePivot())
    override val pivotHeure: StateFlow<Int> = _pivot.asStateFlow()

    private val _accepte = MutableStateFlow(prefs.getBoolean(CLE_AVERTISSEMENT, false))
    override val avertissementAccepte: StateFlow<Boolean> = _accepte.asStateFlow()

    private fun lirePivot(): Int =
        prefs.getInt(CLE_PIVOT, PIVOT_HEURE_DEFAUT).takeIf { it in HEURES_PIVOT } ?: PIVOT_HEURE_DEFAUT

    override fun definirPivotHeure(heure: Int) {
        require(heure in HEURES_PIVOT) { "Heure pivot hors de 0..23 : $heure" }
        prefs.edit().putInt(CLE_PIVOT, heure).apply()
        _pivot.value = heure
    }

    override fun accepterAvertissement() {
        prefs.edit().putBoolean(CLE_AVERTISSEMENT, true).apply()
        _accepte.value = true
    }

    companion object {
        const val NOM_FICHIER = "dosejuste_reglages"
        const val CLE_PIVOT = "pivot_heure"
        const val CLE_AVERTISSEMENT = "avertissement_accepte"
    }
}
