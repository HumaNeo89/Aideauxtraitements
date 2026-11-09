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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.dosejuste.app.R

/**
 * Totaux (confirmées, par médicament et par unité) puis liste horodatée d'une journée.
 * Les prises annulées restent visibles, marquées. [actions] : réattribution (Historique).
 */
@Composable
fun TotauxEtListe(etat: JourneeUiState, jour: JourneeViewModel, actions: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(PhrasePrise.jour(etat.journee), style = MaterialTheme.typography.titleMedium)

        Text(stringResource(R.string.totaux_titre), style = MaterialTheme.typography.titleMedium)
        Column(modifier = Modifier.fillMaxWidth().testTag(Tags.TOTAUX), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (etat.totaux.isEmpty()) {
                Text(stringResource(R.string.totaux_vide))
            } else {
                etat.totaux.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
            }
        }

        Text(stringResource(R.string.liste_titre), style = MaterialTheme.typography.titleMedium)
        Column(modifier = Modifier.fillMaxWidth().testTag(Tags.LISTE), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (etat.prises.isEmpty()) Text(stringResource(R.string.liste_vide))
            etat.prises.forEach { ligne -> LigneVue(ligne, etat, jour, actions) }
        }
    }
}

@Composable
private fun LigneVue(ligne: LigneJournee, etat: JourneeUiState, jour: JourneeViewModel, actions: Boolean) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(ligne.texte, style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (ligne.annulee) {
                Text(
                    stringResource(R.string.prise_annulee),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (ligne.reattribuee) {
                Text(stringResource(R.string.prise_reattribuee), style = MaterialTheme.typography.labelLarge)
            }
        }
        if (!ligne.annulee) {
            val annulerDescription = stringResource(R.string.annuler_prise_description, ligne.texte)
            TextButton(
                onClick = { jour.demanderAnnulation(ligne.id) },
                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = annulerDescription },
            ) { Text(stringResource(R.string.annuler_cette_prise)) }
            if (actions) {
                val veille = stringResource(R.string.reattribuer_veille)
                TextButton(
                    onClick = { jour.reattribuerALaVeille(ligne.id) },
                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "$veille : ${ligne.texte}" },
                ) { Text(veille) }
                if (!etat.estAujourdhui) {
                    val lendemain = stringResource(R.string.reattribuer_lendemain)
                    TextButton(
                        onClick = { jour.reattribuerAuLendemain(ligne.id) },
                        modifier = Modifier.heightIn(min = 48.dp)
                            .semantics { contentDescription = "$lendemain : ${ligne.texte}" },
                    ) { Text(lendemain) }
                }
                if (ligne.reattribuee) {
                    val retablir = stringResource(R.string.reattribuer_retablir)
                    TextButton(
                        onClick = { jour.retablirJournee(ligne.id) },
                        modifier = Modifier.heightIn(min = 48.dp)
                            .semantics { contentDescription = "$retablir : ${ligne.texte}" },
                    ) { Text(retablir) }
                }
            }
        }
    }
}

/** Dialogue de confirmation d'annulation et message d'erreur de [jour]. */
@Composable
fun DialogueAnnulation(jour: JourneeViewModel) {
    val etat by jour.etat.collectAsStateWithLifecycle()
    etat.priseAAnnuler?.let { ligne ->
        AlertDialog(
            onDismissRequest = jour::abandonnerAnnulation,
            title = { Text(stringResource(R.string.confirmation_titre)) },
            text = { Text(stringResource(R.string.confirmation_texte, ligne.texte)) },
            confirmButton = {
                Button(onClick = jour::confirmerAnnulation, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.confirmation_oui))
                }
            },
            dismissButton = {
                TextButton(onClick = jour::abandonnerAnnulation, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.confirmation_non))
                }
            },
        )
    }
    if (etat.erreurOperation) {
        AlertDialog(
            onDismissRequest = jour::acquitterErreur,
            text = { Text(stringResource(R.string.erreur_operation)) },
            confirmButton = {
                TextButton(onClick = jour::acquitterErreur, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.heure_ok))
                }
            },
        )
    }
}

/** Historique : une journée à la fois, de la plus récente vers le passé, jamais au-delà d'aujourd'hui. */
@Composable
fun EcranHistorique(jour: JourneeViewModel, onRetour: () -> Unit) {
    val etat by jour.etat.collectAsStateWithLifecycle()
    LifecycleResumeEffect(jour) {
        jour.actualiser()
        onPauseOrDispose { }
    }
    EcranSecondaire(titre = stringResource(R.string.historique), onRetour = onRetour) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedButton(
                onClick = jour::journeePrecedente,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag(Tags.JOURNEE_PRECEDENTE),
            ) { Text(stringResource(R.string.journee_precedente_bouton)) }
            OutlinedButton(
                onClick = jour::journeeSuivante,
                enabled = !etat.estAujourdhui,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag(Tags.JOURNEE_SUIVANTE),
            ) { Text(stringResource(R.string.journee_suivante_bouton)) }
        }
        TotauxEtListe(etat, jour, actions = true)
    }
    DialogueAnnulation(jour)
}

/** Cadre commun des écrans secondaires : en-tête, titre, bouton « Retour », contenu défilant. */
@Composable
fun EcranSecondaire(titre: String, onRetour: () -> Unit, contenu: @Composable () -> Unit) {
    androidx.activity.compose.BackHandler(onBack = onRetour)
    androidx.compose.material3.Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EnTete()
            Text(titre, style = MaterialTheme.typography.titleLarge)
            TextButton(
                onClick = onRetour,
                modifier = Modifier.heightIn(min = 48.dp).testTag(Tags.RETOUR_ECRAN),
            ) { Text(stringResource(R.string.retour)) }
            contenu()
        }
    }
}
