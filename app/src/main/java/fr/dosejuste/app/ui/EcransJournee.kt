package fr.dosejuste.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.dosejuste.app.R
import fr.dosejuste.app.ui.theme.CHIFFRES_TABULAIRES

/** Largeur utile du contenu, centré sur tablette. */
internal val LARGEUR_CONTENU = 600.dp

/**
 * Totaux (confirmées, par médicament et par unité) puis liste horodatée d'une journée.
 * Les prises annulées restent visibles, marquées. [actions] : réattribution (Historique).
 */
@Composable
fun TotauxEtListe(etat: JourneeUiState, jour: JourneeViewModel, actions: Boolean) {
    val styleChiffres = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = CHIFFRES_TABULAIRES)
    Column(verticalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            PhrasePrise.jour(etat.journee),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().semantics { heading() },
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.totaux_titre),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            CarteSection {
                Column(
                    modifier = Modifier.fillMaxWidth().testTag(Tags.TOTAUX),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (etat.totaux.isEmpty()) {
                        Text(
                            stringResource(R.string.totaux_vide),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        etat.totaux.forEach { Text(it, style = styleChiffres) }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.liste_titre),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            CarteSection {
                Column(modifier = Modifier.fillMaxWidth().testTag(Tags.LISTE)) {
                    if (etat.prises.isEmpty()) {
                        EtatVide()
                    }
                    etat.prises.forEachIndexed { index, ligne ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                        LigneVue(ligne, etat, jour, actions)
                    }
                }
            }
        }
    }
}

@Composable
private fun CarteSection(contenu: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Box(modifier = Modifier.padding(16.dp)) { contenu() }
    }
}

@Composable
private fun EtatVide() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_carnet),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.outline,
        )
        Text(
            stringResource(R.string.liste_vide),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LigneVue(ligne: LigneJournee, etat: JourneeUiState, jour: JourneeViewModel, actions: Boolean) {
    val base = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = CHIFFRES_TABULAIRES)
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            ligne.texte,
            style = if (ligne.annulee) base.copy(textDecoration = TextDecoration.LineThrough) else base,
            color = if (ligne.annulee) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        if (ligne.annulee || ligne.reattribuee) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (ligne.annulee) {
                    Text(
                        stringResource(R.string.prise_annulee),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                if (ligne.reattribuee) {
                    Text(
                        stringResource(R.string.prise_reattribuee),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.shapes.extraSmall)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }
        if (!ligne.annulee) {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                val annulerDescription = stringResource(R.string.annuler_prise_description, ligne.texte)
                TextButton(
                    onClick = { jour.demanderAnnulation(ligne.id) },
                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = annulerDescription },
                ) { Text(stringResource(R.string.annuler_cette_prise)) }
                if (actions) {
                    val veille = stringResource(R.string.reattribuer_veille)
                    TextButton(
                        onClick = { jour.reattribuerALaVeille(ligne.id) },
                        modifier = Modifier.heightIn(min = 48.dp)
                            .semantics { contentDescription = "$veille : ${ligne.texte}" },
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
}

/** Dialogue de confirmation d'annulation et message d'erreur de [jour]. */
@Composable
fun DialogueAnnulation(jour: JourneeViewModel) {
    val etat by jour.etat.collectAsStateWithLifecycle()
    etat.priseAAnnuler?.let { ligne ->
        AlertDialog(
            onDismissRequest = jour::abandonnerAnnulation,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLarge,
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
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLarge,
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
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(
                    onClick = jour::journeePrecedente,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag(Tags.JOURNEE_PRECEDENTE),
                ) { Text(stringResource(R.string.journee_precedente_bouton), textAlign = TextAlign.Center) }
                OutlinedButton(
                    onClick = jour::journeeSuivante,
                    enabled = !etat.estAujourdhui,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag(Tags.JOURNEE_SUIVANTE),
                ) { Text(stringResource(R.string.journee_suivante_bouton), textAlign = TextAlign.Center) }
            }
            TotauxEtListe(etat, jour, actions = true)
        }
    }
    DialogueAnnulation(jour)
}

/** Cadre commun des écrans secondaires : barre du haut avec « Retour », en-tête, contenu défilant. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcranSecondaire(titre: String, onRetour: () -> Unit, contenu: @Composable () -> Unit) {
    androidx.activity.compose.BackHandler(onBack = onRetour)
    val defilement = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(defilement.nestedScrollConnection),
        topBar = {
            // Le titre est dans le contenu défilant (pas dans la barre de hauteur fixe) :
            // il passe à la ligne au lieu d'être rogné avec une grande taille de texte.
            TopAppBar(
                title = {},
                navigationIcon = {
                    TextButton(
                        onClick = onRetour,
                        modifier = Modifier.heightIn(min = 48.dp).testTag(Tags.RETOUR_ECRAN),
                    ) {
                        Icon(painter = painterResource(R.drawable.ic_retour), contentDescription = null)
                        Text(stringResource(R.string.retour), modifier = Modifier.padding(start = 8.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = defilement,
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = LARGEUR_CONTENU)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                EnTete()
                Text(
                    titre,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
                contenu()
            }
        }
    }
}
