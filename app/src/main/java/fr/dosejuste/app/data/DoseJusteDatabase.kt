package fr.dosejuste.app.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import fr.dosejuste.app.domain.StatutPrise
import fr.dosejuste.app.domain.Unite

/**
 * Base locale de Dose Juste, chiffrée par SQLCipher en production (voir [BaseChiffree]).
 *
 * Schéma exporté dans `app/schemas/` (versionné). Toute évolution du schéma passe par
 * une [Migration] ajoutée à [MIGRATIONS] et un test de migration ; la migration
 * destructive est proscrite (elle effacerait les prises).
 */
@Database(
    entities = [MedicamentEntity::class, PriseEntity::class, AliasEntity::class],
    version = DoseJusteDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Convertisseurs::class)
abstract class DoseJusteDatabase : RoomDatabase() {

    abstract fun medicamentDao(): MedicamentDao

    abstract fun priseDao(): PriseDao

    companion object {
        const val VERSION = 1

        /** Nom du fichier de base dans le dossier `databases/` de l'appli. */
        const val NOM_FICHIER = "dosejuste.db"

        /** Migrations de schéma, dans l'ordre. Vide : seule la version 1 existe. */
        val MIGRATIONS: Array<Migration> = emptyArray()
    }
}

/**
 * Réglages communs à la base de production et aux bases de test : migrations et
 * contraintes SQL. Pas de migration destructive.
 */
fun RoomDatabase.Builder<DoseJusteDatabase>.configurationCommune(): RoomDatabase.Builder<DoseJusteDatabase> =
    addMigrations(*DoseJusteDatabase.MIGRATIONS)
        .addCallback(ContraintesSql)

/**
 * Contraintes SQL posées par déclencheurs (Room ne génère pas de CHECK). Créées à chaque
 * ouverture avec IF NOT EXISTS : une base issue d'une migration les reçoit aussi. Une
 * migration qui change leur définition doit les supprimer (DROP TRIGGER) au préalable.
 * Une violation lève une SQLiteConstraintException (RAISE(ABORT)).
 */
object ContraintesSql : RoomDatabase.Callback() {

    private fun listeSql(noms: List<String>): String = noms.joinToString(", ") { "'$it'" }

    private val priseInvalide: String
        get() = "NEW.quantite_centiemes IS NULL OR typeof(NEW.quantite_centiemes) != 'integer' " +
            "OR NEW.quantite_centiemes <= 0 " +
            "OR NEW.unite NOT IN (${listeSql(Unite.entries.map { it.name })}) " +
            "OR NEW.statut NOT IN (${listeSql(StatutPrise.entries.map { it.name })}) " +
            // Passage par julianday : normalise 2026-02-30 en 2026-03-02, donc refusé.
            "OR (NEW.journee_forcee IS NOT NULL " +
            "AND date(julianday(NEW.journee_forcee)) IS NOT NEW.journee_forcee)"

    private const val MEDICAMENT_INVALIDE = "NEW.libelle IS NULL OR trim(NEW.libelle) = ''"

    val instructions: List<String>
        get() = listOf(
            declencheur("prise_valide_insertion", "INSERT", "prise", priseInvalide, "prise invalide"),
            declencheur("prise_valide_modification", "UPDATE", "prise", priseInvalide, "prise invalide"),
            declencheur("medicament_valide_insertion", "INSERT", "medicament", MEDICAMENT_INVALIDE, "libellé vide"),
            declencheur("medicament_valide_modification", "UPDATE", "medicament", MEDICAMENT_INVALIDE, "libellé vide"),
            declencheur("alias_valide_insertion", "INSERT", "alias", "trim(NEW.texte) = ''", "alias vide"),
        )

    private fun declencheur(nom: String, evenement: String, table: String, condition: String, message: String) =
        "CREATE TRIGGER IF NOT EXISTS $nom BEFORE $evenement ON $table " +
            "WHEN $condition BEGIN SELECT RAISE(ABORT, '$message'); END"

    override fun onOpen(db: SupportSQLiteDatabase) {
        instructions.forEach(db::execSQL)
    }
}
