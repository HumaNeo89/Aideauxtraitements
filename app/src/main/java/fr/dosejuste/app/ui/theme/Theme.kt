package fr.dosejuste.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/** Thème de l'appli : palette fixe (pas de couleur dynamique) pour une identité visuelle stable. */
@Composable
fun DoseJusteTheme(
    sombre: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val schema = if (sombre) SchemaSombre else SchemaClair
    MaterialTheme(colorScheme = schema, typography = TypographieDoseJuste, shapes = FormesDoseJuste, content = content)
}
