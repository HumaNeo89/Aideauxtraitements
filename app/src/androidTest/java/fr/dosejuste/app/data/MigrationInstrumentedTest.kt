package fr.dosejuste.app.data

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.security.SecureRandom
import java.util.UUID
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Crée la version 1 depuis le schéma exporté (`app/schemas/`, ajouté aux assets de test)
 * et vérifie que Room l'ouvre et la valide. Les futures migrations s'ajouteront ici
 * (createDatabase(n) puis runMigrationsAndValidate(n + 1, …)).
 */
@RunWith(AndroidJUnit4::class)
class MigrationInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val nom = "test-migration-${UUID.randomUUID()}.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        DoseJusteDatabase::class.java,
    )

    @After
    fun nettoyer() {
        context.deleteDatabase(nom)
    }

    @Test
    fun versionUnCreeeDepuisLeSchemaExporte() {
        helper.createDatabase(nom, 1).apply {
            execSQL("INSERT INTO medicament (id, libelle, cree_le) VALUES (1, 'Testoline', 0)")
            execSQL(
                "INSERT INTO prise (medicament_id, quantite_centiemes, unite, horodatage_utc_ms, statut, " +
                    "journee_forcee, cree_le, maj_le) VALUES (1, 40, 'COMPRIME', 0, 'CONFIRMEE', NULL, 0, 0)",
            )
            close()
        }

        helper.runMigrationsAndValidate(nom, DoseJusteDatabase.VERSION, true, *DoseJusteDatabase.MIGRATIONS).close()

        val base = Room.databaseBuilder(context, DoseJusteDatabase::class.java, nom)
            .configurationCommune()
            .build()
        try {
            val prises = runBlocking { base.priseDao().entre(java.time.Instant.EPOCH, java.time.Instant.ofEpochMilli(1)) }
            assertEquals(1, prises.size)
            assertEquals(40L, prises.single().versDomaine().quantite.centiemes)
        } finally {
            base.close()
        }
    }

    @Test
    fun versionUnChiffreeCreeeEtValidee() {
        BaseChiffree.chargerBibliotheque()
        val phrase = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val helperChiffre = MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            DoseJusteDatabase::class.java,
            emptyList(),
            SupportOpenHelperFactory(phrase),
        )
        val nomChiffre = "test-migration-chiffree-${UUID.randomUUID()}.db"
        try {
            helperChiffre.createDatabase(nomChiffre, 1).close()
            helperChiffre.runMigrationsAndValidate(nomChiffre, DoseJusteDatabase.VERSION, true).close()
        } finally {
            context.deleteDatabase(nomChiffre)
            phrase.fill(0)
        }
    }
}
