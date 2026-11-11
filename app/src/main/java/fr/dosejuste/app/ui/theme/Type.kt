package fr.dosejuste.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private fun style(taille: Int, interligne: Int, poids: FontWeight, espacement: Float) = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = taille.sp,
    lineHeight = interligne.sp,
    fontWeight = poids,
    letterSpacing = espacement.sp,
)

/** Échelle typographique de docs/design.md (§4), polices système uniquement. */
internal val TypographieDoseJuste = Typography(
    displayLarge = style(57, 64, FontWeight.W400, -0.25f),
    displayMedium = style(45, 52, FontWeight.W400, 0f),
    displaySmall = style(36, 44, FontWeight.W400, 0f),
    headlineLarge = style(32, 40, FontWeight.W500, 0f),
    headlineMedium = style(28, 36, FontWeight.W500, 0f),
    headlineSmall = style(24, 32, FontWeight.W500, 0f),
    titleLarge = style(22, 28, FontWeight.W500, 0f),
    titleMedium = style(18, 26, FontWeight.W500, 0.1f),
    titleSmall = style(15, 22, FontWeight.W500, 0.1f),
    bodyLarge = style(17, 26, FontWeight.W400, 0.15f),
    bodyMedium = style(16, 24, FontWeight.W400, 0.2f),
    bodySmall = style(14, 20, FontWeight.W400, 0.3f),
    labelLarge = style(16, 22, FontWeight.W500, 0.1f),
    labelMedium = style(14, 20, FontWeight.W500, 0.4f),
    labelSmall = style(12, 16, FontWeight.W500, 0.5f),
)

/** Chiffres tabulaires : les heures et quantités s'alignent. */
internal const val CHIFFRES_TABULAIRES = "tnum"

/** Style des textes de licence : monospace 13 sp / 19 sp. */
internal val StyleLicence = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 13.sp,
    lineHeight = 19.sp,
)
