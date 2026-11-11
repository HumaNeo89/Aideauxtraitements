package fr.dosejuste.app.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.dosejuste.app.R
import fr.dosejuste.app.prefs.HEURES_PIVOT
import fr.dosejuste.app.ui.theme.StyleLicence

/** Carte de contenu commune aux écrans annexes. */
@Composable
private fun CarteAnnexe(
    couleur: androidx.compose.ui.graphics.Color,
    contenu: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = couleur),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            contenu()
        }
    }
}

/** Avertissement de premier lancement : un seul bouton, rien d'autre n'est accessible avant. */
@Composable
fun EcranAvertissement(onAccepter: () -> Unit) {
    Scaffold { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .widthIn(max = LARGEUR_CONTENU)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            ) {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                CarteAnnexe(MaterialTheme.colorScheme.surfaceContainerLow) {
                    Text(
                        stringResource(R.string.avertissement_titre),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(stringResource(R.string.avertissement_texte), style = MaterialTheme.typography.bodyLarge)
                }
                Button(
                    onClick = onAccepter,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(Tags.AVERTISSEMENT_OK),
                ) { Text(stringResource(R.string.avertissement_ok)) }
            }
        }
    }
}

/** Réglages : heure pivot (0 h à 23 h). Le changement s'applique tout de suite partout. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EcranReglages(vm: ReglagesViewModel, onRetour: () -> Unit) {
    val pivot by vm.pivotHeure.collectAsStateWithLifecycle()
    EcranSecondaire(titre = stringResource(R.string.reglages), onRetour = onRetour) {
        CarteAnnexe(MaterialTheme.colorScheme.surfaceContainer) {
            Text(
                stringResource(R.string.pivot_titre),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.pivot_explication),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (heure in HEURES_PIVOT) {
                    FilterChip(
                        selected = heure == pivot,
                        onClick = { vm.choisirPivot(heure) },
                        label = { Text(stringResource(R.string.pivot_valeur, heure)) },
                        border = bordureChip(selected = heure == pivot),
                        modifier = Modifier.heightIn(min = 48.dp).testTag(Tags.PIVOT_PREFIXE + heure),
                    )
                }
            }
        }
    }
}

/** Bordure des FilterChip en `outline` (contraste ≥ 3:1 sur les cartes) au lieu de `outlineVariant`. */
@Composable
internal fun bordureChip(selected: Boolean) = androidx.compose.material3.FilterChipDefaults.filterChipBorder(
    enabled = true,
    selected = selected,
    borderColor = MaterialTheme.colorScheme.outline,
    // Puce choisie : contour primary 2 dp, le fond secondaryContainer seul se distingue mal de la carte.
    selectedBorderColor = MaterialTheme.colorScheme.primary,
    selectedBorderWidth = 2.dp,
)

/** À propos : nom, version et accès aux licences. */
@Composable
fun EcranAPropos(onLicences: () -> Unit, onRetour: () -> Unit) {
    val contexte = LocalContext.current
    val version = remember {
        contexte.packageManager.getPackageInfo(contexte.packageName, 0).versionName.orEmpty()
    }
    EcranSecondaire(titre = stringResource(R.string.a_propos), onRetour = onRetour) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CarteAnnexe(MaterialTheme.colorScheme.surfaceContainer) {
                Text(stringResource(R.string.a_propos_version, version), style = MaterialTheme.typography.bodyLarge)
            }
            OutlinedButton(
                onClick = onLicences,
                modifier = Modifier.heightIn(min = 48.dp).testTag(Tags.LICENCES),
            ) { Text(stringResource(R.string.licences_ouvrir)) }
        }
    }
}

/** Texte d'une licence, lu dans les assets ; `null` si illisible. */
typealias LecteurLicence = (fichier: String) -> String?

private fun lireAsset(contexte: Context): LecteurLicence = { fichier ->
    try {
        contexte.assets.open(Licences.chemin(fichier)).bufferedReader(Charsets.UTF_8).use { it.readText() }
    } catch (e: java.io.IOException) {
        null
    }
}

/** Licences tierces : titre, copyright, licence ; texte complet déroulant. */
@Composable
fun EcranLicences(onRetour: () -> Unit, lecteur: LecteurLicence = lireAsset(LocalContext.current)) {
    EcranSecondaire(titre = stringResource(R.string.licences_ouvrir), onRetour = onRetour) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                stringResource(R.string.licences_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Notices regroupées par fichier de texte : un seul bouton par fichier.
            Licences.notices.groupBy { it.fichierTexte ?: it.titre }.values.forEach { NoticeVue(it, lecteur) }
        }
    }
}

@Composable
private fun NoticeVue(notices: List<NoticeTierce>, lecteur: LecteurLicence) {
    var deplie by remember { mutableStateOf(false) }
    CarteAnnexe(MaterialTheme.colorScheme.surfaceContainerLow) {
        notices.forEach { notice ->
            Text(
                notice.titre,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(notice.copyright, style = MaterialTheme.typography.bodyMedium)
            Text(notice.licence, style = MaterialTheme.typography.bodyMedium)
        }
        val fichier = notices.first().fichierTexte
        if (fichier != null) {
            if (notices.size > 1) {
                Text(
                    stringResource(R.string.licence_texte_groupe, notices.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { deplie = !deplie }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(if (deplie) R.string.licence_texte_masquer else R.string.licence_texte_afficher))
                Icon(
                    painter = painterResource(R.drawable.ic_chevron),
                    contentDescription = null,
                    modifier = Modifier.padding(start = 8.dp).rotate(if (deplie) 180f else 0f),
                )
            }
            if (deplie) {
                Text(
                    text = remember(fichier) { lecteur(fichier) } ?: stringResource(R.string.licence_illisible),
                    style = StyleLicence,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
