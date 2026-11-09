package fr.dosejuste.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.dosejuste.app.R

/** Racine : chargement, erreur d'ouverture, ou écran de saisie. */
@Composable
fun DoseJusteApp(ouverture: OuvertureViewModel, onFermer: () -> Unit) {
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
            val vm: SaisieViewModel = viewModel(
                factory = viewModelFactory { initializer { SaisieViewModel(e.journal) } },
            )
            EcranSaisie(vm)
        }
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
            CircularProgressIndicator()
            Text(stringResource(R.string.chargement))
        }
    }
}

/** Écran d'erreur : un seul bouton, « Fermer ». Aucune action qui efface. */
@Composable
fun EcranErreur(titre: String, texte: String, onFermer: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            EnTete()
            Text(titre, style = MaterialTheme.typography.headlineSmall)
            Text(texte, style = MaterialTheme.typography.bodyLarge)
            Button(onClick = onFermer, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.fermer))
            }
        }
    }
}
