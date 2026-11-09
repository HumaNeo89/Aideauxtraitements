package fr.dosejuste.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.dosejuste.app.security.DonneesIllisiblesException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** État de l'ouverture de la base au démarrage. */
sealed interface EtatOuverture {
    data object Chargement : EtatOuverture

    data class Pret(val journal: JournalPrises) : EtatOuverture

    /** Clé de chiffrement perdue ou invalide. Aucune réparation ni effacement automatique. */
    data object DonneesIllisibles : EtatOuverture

    /** Toute autre erreur : écran générique, sans détail technique. */
    data object ErreurGenerique : EtatOuverture
}

/**
 * Ouvre la base hors du fil principal ([dispatcher], `Dispatchers.IO` par défaut) :
 * déchiffrement Keystore et dérivation de clé SQLCipher peuvent être lents.
 *
 * Ne journalise rien et n'expose aucun message d'exception : ni détail technique ni secret.
 */
class OuvertureViewModel(
    private val ouvrir: suspend () -> JournalPrises,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _etat = MutableStateFlow<EtatOuverture>(EtatOuverture.Chargement)
    val etat: StateFlow<EtatOuverture> = _etat.asStateFlow()

    init {
        viewModelScope.launch {
            _etat.value = try {
                EtatOuverture.Pret(withContext(dispatcher) { ouvrir() })
            } catch (e: CancellationException) {
                throw e
            } catch (e: DonneesIllisiblesException) {
                EtatOuverture.DonneesIllisibles
            } catch (e: Exception) {
                EtatOuverture.ErreurGenerique
            } catch (e: LinkageError) {
                // Bibliothèque native SQLCipher introuvable : même écran générique.
                EtatOuverture.ErreurGenerique
            }
        }
    }

    override fun onCleared() {
        val pret = _etat.value as? EtatOuverture.Pret
        (pret?.journal as? AutoCloseable)?.close()
    }
}
