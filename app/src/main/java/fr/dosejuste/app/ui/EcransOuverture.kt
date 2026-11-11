package fr.dosejuste.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.dosejuste.app.R
import fr.dosejuste.app.prefs.Reglages
import java.time.Clock

/** Écrans de l'appli, par état (pas de bibliothèque de navigation). */
enum class Ecran { AUJOURDHUI, HISTORIQUE, REGLAGES, A_PROPOS, LICENCES }

/**
 * Racine : chargement, erreur d'ouverture (prioritaire), avertissement de premier lancement,
 * puis les écrans. Tant que l'avertissement n'est pas accepté, rien d'autre n'est accessible.
 */
@Composable
fun DoseJusteApp(
    ouverture: OuvertureViewModel,
    reglages: Reglages,
    onFermer: () -> Unit,
    horloge: Clock = Clock.systemUTC(),
) {
    val etat by ouverture.etat.collectAsStateWithLifecycle()
    when (val e = etat) {
        EtatOuverture.Chargement -> EcranChargement()
        EtatOuverture.DonneesIllisibles -> EcranErreur(
            titre = stringResource(R.string.illisibles_titre),
            texte = stringResource(R.string.illisibles_texte),
            onFermer = onFermer,
        )
        EtatOuverture.ErreurGenerique -> EcranErreur(
            titre = stringResource(R.string.ouverture_erreur_titre),
            texte = stringResource(R.string.ouverture_erreur_texte),
            onFermer = onFermer,
        )
        is EtatOuverture.Pret -> {
            val reglagesVm: ReglagesViewModel = viewModel(
                factory = viewModelFactory { initializer { ReglagesViewModel(reglages) } },
            )
            val accepte by reglagesVm.avertissementAccepte.collectAsStateWithLifecycle()
            if (!accepte) {
                EcranAvertissement(onAccepter = reglagesVm::accepterAvertissement)
            } else {
                Ecrans(e.journal, reglages, reglagesVm, horloge)
            }
        }
    }
}

@Composable
private fun Ecrans(journal: JournalPrises, reglages: Reglages, reglagesVm: ReglagesViewModel, horloge: Clock) {
    var ecran by rememberSaveable { mutableStateOf(Ecran.AUJOURDHUI) }
    val saisie: SaisieViewModel = viewModel(
        key = "saisie",
        factory = viewModelFactory { initializer { SaisieViewModel(journal, horloge) } },
    )
    // Deux instances : « Aujourd'hui » reste sur la journée en cours, « Historique » navigue.
    val aujourdhui: JourneeViewModel = viewModel(
        key = "aujourdhui",
        factory = viewModelFactory { initializer { JourneeViewModel(journal, reglages, horloge) } },
    )
    when (ecran) {
        Ecran.AUJOURDHUI -> EcranAujourdhui(
            vm = saisie,
            jour = aujourdhui,
            navigation = Navigation(
                onHistorique = { ecran = Ecran.HISTORIQUE },
                onReglages = { ecran = Ecran.REGLAGES },
                onAPropos = { ecran = Ecran.A_PROPOS },
            ),
        )
        Ecran.HISTORIQUE -> {
            val historique: JourneeViewModel = viewModel(
                key = "historique",
                factory = viewModelFactory { initializer { JourneeViewModel(journal, reglages, horloge) } },
            )
            EcranHistorique(historique, onRetour = { ecran = Ecran.AUJOURDHUI })
        }
        Ecran.REGLAGES -> EcranReglages(reglagesVm, onRetour = { ecran = Ecran.AUJOURDHUI })
        Ecran.A_PROPOS -> EcranAPropos(
            onLicences = { ecran = Ecran.LICENCES },
            onRetour = { ecran = Ecran.AUJOURDHUI },
        )
        Ecran.LICENCES -> EcranLicences(onRetour = { ecran = Ecran.A_PROPOS })
    }
}

@Composable
fun EcranChargement() {
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.chargement), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** Écran d'erreur : un seul bouton, « Fermer ». Aucune action qui efface. */
@Composable
fun EcranErreur(titre: String, texte: String, onFermer: () -> Unit) {
    Scaffold { padding ->
        Column(
            // Défilant : avec une grande taille de texte, « Fermer » reste atteignable.
            modifier = Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            EnTete()
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(painter = painterResource(R.drawable.ic_attention), contentDescription = null)
                        Text(
                            titre,
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.semantics { heading() },
                        )
                    }
                    Text(texte, style = MaterialTheme.typography.bodyLarge)
                }
            }
            Button(onClick = onFermer, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.fermer))
            }
        }
    }
}
