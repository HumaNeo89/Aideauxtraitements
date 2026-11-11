package fr.dosejuste.app.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.dosejuste.app.R
import fr.dosejuste.app.prefs.HEURES_PIVOT

/** Avertissement de premier lancement : un seul bouton, rien d'autre n'est accessible avant. */
@Composable
fun EcranAvertissement(onAccepter: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp, androidx.compose.ui.Alignment.CenterVertically),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.avertissement_titre), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.avertissement_texte), style = MaterialTheme.typography.bodyLarge)
            Button(
                onClick = onAccepter,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(Tags.AVERTISSEMENT_OK),
            ) { Text(stringResource(R.string.avertissement_ok)) }
        }
    }
}

/** Réglages : heure pivot (0 h à 23 h). Le changement s'applique tout de suite partout. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EcranReglages(vm: ReglagesViewModel, onRetour: () -> Unit) {
    val pivot by vm.pivotHeure.collectAsStateWithLifecycle()
    EcranSecondaire(titre = stringResource(R.string.reglages), onRetour = onRetour) {
        Text(stringResource(R.string.pivot_titre), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.pivot_explication), style = MaterialTheme.typography.bodyMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (heure in HEURES_PIVOT) {
                FilterChip(
                    selected = heure == pivot,
                    onClick = { vm.choisirPivot(heure) },
                    label = { Text(stringResource(R.string.pivot_valeur, heure)) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag(Tags.PIVOT_PREFIXE + heure),
                )
            }
        }
    }
}

/** À propos : nom, version et accès aux licences. */
@Composable
fun EcranAPropos(onLicences: () -> Unit, onRetour: () -> Unit) {
    val contexte = LocalContext.current
    val version = remember {
        contexte.packageManager.getPackageInfo(contexte.packageName, 0).versionName.orEmpty()
    }
    EcranSecondaire(titre = stringResource(R.string.a_propos), onRetour = onRetour) {
        Text(stringResource(R.string.a_propos_version, version), style = MaterialTheme.typography.bodyLarge)
        OutlinedButton(
            onClick = onLicences,
            modifier = Modifier.heightIn(min = 48.dp).testTag(Tags.LICENCES),
        ) { Text(stringResource(R.string.licences_ouvrir)) }
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
        Text(stringResource(R.string.licences_intro), style = MaterialTheme.typography.bodyMedium)
        // Notices regroupées par fichier de texte : un seul bouton par fichier.
        Licences.notices.groupBy { it.fichierTexte ?: it.titre }.values.forEach { NoticeVue(it, lecteur) }
    }
}

@Composable
private fun NoticeVue(notices: List<NoticeTierce>, lecteur: LecteurLicence) {
    var deplie by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        notices.forEach { notice ->
            Text(notice.titre, style = MaterialTheme.typography.titleMedium)
            Text(notice.copyright, style = MaterialTheme.typography.bodyMedium)
            Text(notice.licence, style = MaterialTheme.typography.bodyMedium)
        }
        val fichier = notices.first().fichierTexte
        if (fichier != null) {
            if (notices.size > 1) {
                Text(stringResource(R.string.licence_texte_groupe, notices.size), style = MaterialTheme.typography.bodyMedium)
            }
            TextButton(onClick = { deplie = !deplie }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(if (deplie) R.string.licence_texte_masquer else R.string.licence_texte_afficher))
            }
            if (deplie) {
                Text(
                    text = remember(fichier) { lecteur(fichier) } ?: stringResource(R.string.licence_illisible),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
