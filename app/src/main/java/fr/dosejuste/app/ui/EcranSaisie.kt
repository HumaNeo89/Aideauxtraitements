package fr.dosejuste.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.dosejuste.app.R
import fr.dosejuste.app.domain.RaisonErreur
import fr.dosejuste.app.domain.Unite
import java.time.LocalTime

/** Identifiants de test (tests instrumentés). */
object Tags {
    const val NOUVELLE_PRISE = "nouvelle_prise"
    const val CHAMP_LIBELLE = "champ_libelle"
    const val AJOUTER_LIBELLE = "ajouter_libelle"
    const val CHAMP_QUANTITE = "champ_quantite"
    const val RELIRE = "relire"
    const val ENREGISTRER = "enregistrer"
    const val MODIFIER = "modifier"
    const val LISTE = "liste_prises"
    const val RETOUR = "retour_formulaire"
    const val BARRE_ACTIONS = "barre_actions"
    const val HISTORIQUE = "ouvrir_historique"
    const val REGLAGES = "ouvrir_reglages"
    const val A_PROPOS = "ouvrir_a_propos"
    const val LICENCES = "ouvrir_licences"
    const val TOTAUX = "totaux"
    const val AVERTISSEMENT_OK = "avertissement_ok"
    const val JOURNEE_PRECEDENTE = "journee_precedente"
    const val JOURNEE_SUIVANTE = "journee_suivante"
    const val RETOUR_ECRAN = "retour_ecran"
    const val PIVOT_PREFIXE = "pivot_"
}

/** Accès aux autres écrans depuis « Aujourd'hui ». */
class Navigation(val onHistorique: () -> Unit, val onReglages: () -> Unit, val onAPropos: () -> Unit)

/**
 * Écran « Aujourd'hui » : en-tête, totaux et liste de la journée en cours ([jour]), formulaire
 * de saisie et dialogues de [vm].
 */
@Composable
fun EcranAujourdhui(
    vm: SaisieViewModel,
    jour: JourneeViewModel,
    navigation: Navigation,
    modifier: Modifier = Modifier,
) {
    val etat by vm.etat.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    // Snackbar : durée pilotée par le ViewModel (10 s), pas par SnackbarDuration.
    val annulation = etat.annulationRapide
    if (annulation != null) {
        val message = stringResource(R.string.prise_enregistree, annulation.phrase)
        val action = stringResource(R.string.annuler)
        LaunchedEffect(annulation) {
            val resultat = snackbar.showSnackbar(
                message = message,
                actionLabel = action,
                duration = SnackbarDuration.Indefinite,
            )
            if (resultat == SnackbarResult.ActionPerformed) vm.annulerRapide()
        }
    }

    BackHandler(enabled = etat.formulaire != null) { vm.fermerFormulaire() }

    Scaffold(modifier = modifier, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        // consumeWindowInsets + imePadding : le clavier remonte le contenu sans doubler la marge
        // de la barre système déjà appliquée par le Scaffold.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EnTete()
            val formulaire = etat.formulaire
            if (formulaire == null) {
                Principal(vm, jour, navigation)
            } else {
                FormulaireNouvellePrise(formulaire, etat.medicaments, vm)
            }
        }
    }

    DialogueAnnulation(jour)

    etat.relecture?.let { relecture ->
        AlertDialog(
            onDismissRequest = vm::modifier,
            title = { Text(stringResource(R.string.relecture_titre)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.relecture_intro))
                    Text(relecture.phrase, style = MaterialTheme.typography.titleMedium)
                }
            },
            confirmButton = {
                Button(
                    onClick = vm::enregistrer,
                    modifier = Modifier.heightIn(min = 48.dp).testTag(Tags.ENREGISTRER),
                ) { Text(stringResource(R.string.enregistrer)) }
            },
            dismissButton = {
                TextButton(
                    onClick = vm::modifier,
                    modifier = Modifier.heightIn(min = 48.dp).testTag(Tags.MODIFIER),
                ) { Text(stringResource(R.string.modifier)) }
            },
        )
    }

    if (etat.erreurOperation) {
        AlertDialog(
            onDismissRequest = vm::acquitterErreur,
            text = { Text(stringResource(R.string.erreur_operation)) },
            confirmButton = {
                TextButton(onClick = vm::acquitterErreur, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.heure_ok))
                }
            },
        )
    }
}

/** Nom de l'application et phrase d'avertissement, toujours visibles. */
@Composable
fun EnTete() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 8.dp)) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.disclaimer), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Principal(vm: SaisieViewModel, jour: JourneeViewModel, navigation: Navigation) {
    val etatJour by jour.etat.collectAsStateWithLifecycle()
    // Au retour au premier plan, la journée en cours a pu changer (passage du pivot).
    LifecycleResumeEffect(jour) {
        jour.actualiser()
        onPauseOrDispose { }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.ecran_titre), style = MaterialTheme.typography.titleLarge)
        Button(
            onClick = vm::ouvrirFormulaire,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(Tags.NOUVELLE_PRISE),
        ) { Text(stringResource(R.string.nouvelle_prise)) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = navigation.onHistorique,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag(Tags.HISTORIQUE),
            ) { Text(stringResource(R.string.historique)) }
            OutlinedButton(
                onClick = navigation.onReglages,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag(Tags.REGLAGES),
            ) { Text(stringResource(R.string.reglages)) }
            OutlinedButton(
                onClick = navigation.onAPropos,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag(Tags.A_PROPOS),
            ) { Text(stringResource(R.string.a_propos)) }
        }

        TotauxEtListe(etatJour, jour, actions = false)
    }
}

@Composable
private fun FormulaireNouvellePrise(formulaire: Formulaire, medicaments: List<Medicament>, vm: SaisieViewModel) {
    var choixHeure by remember { mutableStateOf(false) }

    // Champs dans une zone défilable ; « Relire » et « Retour » dans une barre fixe en dessous,
    // toujours visible, même quand des messages d'erreur allongent le formulaire. La barre
    // reste au-dessus du clavier (imePadding posé par l'écran parent).
    Column(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.formulaire_titre), style = MaterialTheme.typography.titleLarge)

        // Médicament
        Text(stringResource(R.string.medicament_titre), style = MaterialTheme.typography.titleMedium)
        if (medicaments.isEmpty()) Text(stringResource(R.string.medicament_aucun))
        medicaments.forEach { m ->
            val description = stringResource(R.string.medicament_choisir, m.libelle)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .selectable(
                        selected = formulaire.medicamentId == m.id,
                        onClick = { vm.choisirMedicament(m.id) },
                        role = Role.RadioButton,
                    )
                    .semantics { contentDescription = description },
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                RadioButton(selected = formulaire.medicamentId == m.id, onClick = null)
                Text(m.libelle, modifier = Modifier.padding(start = 8.dp))
            }
        }
        if (formulaire.erreurMedicamentManquant) Erreur(stringResource(R.string.erreur_medicament_manquant))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = formulaire.libelleTexte,
                onValueChange = vm::saisirLibelle,
                label = { Text(stringResource(R.string.libelle_champ)) },
                isError = formulaire.erreurLibelle != null,
                singleLine = true,
                // Majuscule initiale proposée par le clavier ; la casse saisie est conservée telle quelle.
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.weight(1f).testTag(Tags.CHAMP_LIBELLE),
            )
            OutlinedButton(
                onClick = vm::ajouterMedicament,
                modifier = Modifier.heightIn(min = 48.dp).testTag(Tags.AJOUTER_LIBELLE),
            ) { Text(stringResource(R.string.libelle_ajouter)) }
        }
        when (formulaire.erreurLibelle) {
            ErreurLibelle.VIDE -> Erreur(stringResource(R.string.erreur_libelle_vide))
            ErreurLibelle.DOUBLON -> Erreur(stringResource(R.string.erreur_libelle_doublon))
            null -> Unit
        }

        // Quantité
        OutlinedTextField(
            value = formulaire.quantiteTexte,
            onValueChange = vm::saisirQuantite,
            label = { Text(stringResource(R.string.quantite_champ)) },
            isError = formulaire.erreurQuantite != null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().testTag(Tags.CHAMP_QUANTITE),
        )
        formulaire.erreurQuantite?.let { Erreur(messageQuantite(it)) }

        // Unité (liste fermée)
        Text(stringResource(R.string.unite_titre), style = MaterialTheme.typography.titleMedium)
        Unite.entries.forEach { unite ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .selectable(
                        selected = formulaire.unite == unite,
                        onClick = { vm.choisirUnite(unite) },
                        role = Role.RadioButton,
                    ),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                RadioButton(selected = formulaire.unite == unite, onClick = null)
                Text(libelleUnite(unite), modifier = Modifier.padding(start = 8.dp))
            }
        }

        // Jour et heure
        Text(stringResource(R.string.heure_titre), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = formulaire.jour == JourSaisie.AUJOURDHUI,
                onClick = { vm.choisirJour(JourSaisie.AUJOURDHUI) },
                label = { Text(stringResource(R.string.jour_aujourdhui)) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
            FilterChip(
                selected = formulaire.jour == JourSaisie.HIER,
                onClick = { vm.choisirJour(JourSaisie.HIER) },
                label = { Text(stringResource(R.string.jour_hier)) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
        OutlinedButton(
            onClick = { choixHeure = true },
            modifier = Modifier.heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.heure_modifier, PhrasePrise.heure(formulaire.heure))) }
        if (formulaire.erreurHeureFuture) Erreur(stringResource(R.string.erreur_heure_futur))
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag(Tags.BARRE_ACTIONS),
    ) {
        Button(
            onClick = vm::relire,
            modifier = Modifier.heightIn(min = 48.dp).testTag(Tags.RELIRE),
        ) { Text(stringResource(R.string.relire)) }
        TextButton(
            onClick = vm::fermerFormulaire,
            modifier = Modifier.heightIn(min = 48.dp).testTag(Tags.RETOUR),
        ) { Text(stringResource(R.string.retour)) }
    }
    }

    if (choixHeure) {
        DialogueHeure(
            initiale = formulaire.heure,
            onConfirmer = {
                vm.choisirHeure(it)
                choixHeure = false
            },
            onFermer = { choixHeure = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogueHeure(initiale: LocalTime, onConfirmer: (LocalTime) -> Unit, onFermer: () -> Unit) {
    val etat = rememberTimePickerState(initialHour = initiale.hour, initialMinute = initiale.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text(stringResource(R.string.heure_dialogue_titre)) },
        text = { TimePicker(state = etat) },
        confirmButton = {
            TextButton(
                onClick = { onConfirmer(LocalTime.of(etat.hour, etat.minute)) },
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.heure_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onFermer, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.retour))
            }
        },
    )
}

@Composable
private fun Erreur(message: String) {
    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun messageQuantite(raison: RaisonErreur): String = stringResource(
    when (raison) {
        RaisonErreur.VIDE -> R.string.erreur_quantite_vide
        RaisonErreur.FORMAT -> R.string.erreur_quantite_format
        RaisonErreur.TROP_DE_DECIMALES -> R.string.erreur_quantite_decimales
        RaisonErreur.NUL_OU_NEGATIF -> R.string.erreur_quantite_nulle
        RaisonErreur.TROP_GRAND -> R.string.erreur_quantite_grande
    },
)

@Composable
private fun libelleUnite(unite: Unite): String = stringResource(
    when (unite) {
        Unite.MG -> R.string.unite_mg
        Unite.COMPRIME -> R.string.unite_comprime
        Unite.GOUTTE -> R.string.unite_goutte
        Unite.ML -> R.string.unite_ml
    },
)
