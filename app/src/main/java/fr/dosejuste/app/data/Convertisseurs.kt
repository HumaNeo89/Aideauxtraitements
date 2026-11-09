package fr.dosejuste.app.data

import androidx.room.TypeConverter
import fr.dosejuste.app.domain.StatutPrise
import fr.dosejuste.app.domain.Unite
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Une valeur lue en base ne correspond à aucune valeur connue du domaine.
 * Le message ne cite jamais un libellé de médicament ni une quantité.
 */
class ValeurStockeeInvalideException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

/**
 * Conversions Room. Les énumérations sont stockées par leur nom (texte stable),
 * les instants en millisecondes UTC, les journées en ISO yyyy-MM-dd.
 */
class Convertisseurs {

    @TypeConverter
    fun uniteVersTexte(unite: Unite): String = unite.name

    @TypeConverter
    fun texteVersUnite(texte: String): Unite =
        Unite.entries.firstOrNull { it.name == texte }
            ?: throw ValeurStockeeInvalideException("Unité inconnue en base : « $texte »")

    @TypeConverter
    fun statutVersTexte(statut: StatutPrise): String = statut.name

    @TypeConverter
    fun texteVersStatut(texte: String): StatutPrise =
        StatutPrise.entries.firstOrNull { it.name == texte }
            ?: throw ValeurStockeeInvalideException("Statut de prise inconnu en base : « $texte »")

    @TypeConverter
    fun instantVersMillis(instant: Instant): Long = instant.toEpochMilli()

    @TypeConverter
    fun millisVersInstant(millis: Long): Instant = Instant.ofEpochMilli(millis)

    @TypeConverter
    fun journeeVersTexte(journee: LocalDate?): String? = journee?.toString()

    @TypeConverter
    fun texteVersJournee(texte: String?): LocalDate? =
        texte?.let {
            try {
                LocalDate.parse(it)
            } catch (e: DateTimeParseException) {
                throw ValeurStockeeInvalideException("Journée invalide en base : « $it »", e)
            }
        }
}
