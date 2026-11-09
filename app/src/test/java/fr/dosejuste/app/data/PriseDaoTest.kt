package fr.dosejuste.app.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import fr.dosejuste.app.domain.CleTotal
import fr.dosejuste.app.domain.JourneeCalculator
import fr.dosejuste.app.domain.Quantite
import fr.dosejuste.app.domain.StatutPrise
import fr.dosejuste.app.domain.Unite
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * DAO et repository sur une base Room en mémoire NON chiffrée (la bibliothèque native
 * SQLCipher ne tourne pas sur la JVM). Le chiffrement est testé en instrumenté.
 */
@RunWith(RobolectricTestRunner::class)
class PriseDaoTest {

    private val paris = ZoneId.of("Europe/Paris")
    private val calc = JourneeCalculator()
    private val maintenant = Instant.parse("2026-11-10T12:00:00Z")
    private val hier = LocalDate.parse("2026-11-09")
    private val aujourdhui = LocalDate.parse("2026-11-10")

    private lateinit var base: DoseJusteDatabase
    private lateinit var depot: PrisesRepository
    private lateinit var dao: PriseDao

    private fun aParis(texte: String): Instant = LocalDateTime.parse(texte).atZone(paris).toInstant()

    @Before
    fun ouvrir() {
        base = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DoseJusteDatabase::class.java)
            .allowMainThreadQueries()
            .configurationCommune()
            .build()
        depot = PrisesRepository(base, Clock.fixed(maintenant, ZoneOffset.UTC))
        dao = base.priseDao()
    }

    @After
    fun fermer() {
        base.close()
    }

    private fun sql(instruction: String, vararg args: Any?) {
        base.openHelper.writableDatabase.execSQL(instruction, args)
    }

    @Test
    fun medicamentsInseresEtListes() = runBlocking {
        val b = depot.ajouterMedicament("Testoline B")
        val a = depot.ajouterMedicament("Testoline")
        val liste = depot.listerMedicaments()
        assertEquals(listOf("Testoline", "Testoline B"), liste.map { it.libelle })
        assertEquals(listOf(a, b), liste.map { it.id })
        assertEquals(maintenant, liste.first().creeLe)
        assertEquals(liste, depot.observerMedicaments().first())
    }

    @Test
    fun libelleUniqueEtNonVide() {
        runBlocking { depot.ajouterMedicament("Testoline") }
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { base.medicamentDao().inserer(MedicamentEntity(libelle = "Testoline", creeLe = maintenant)) }
        }
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { base.medicamentDao().inserer(MedicamentEntity(libelle = "  ", creeLe = maintenant)) }
        }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { depot.ajouterMedicament("") } }
    }

    @Test
    fun priseInsereeRelueEnTypesDuDomaine() = runBlocking {
        val med = depot.ajouterMedicament("Testoline")
        val cas = listOf(25L to Unite.MG, 40L to Unite.COMPRIME, 100L to Unite.GOUTTE, 25L to Unite.ML)
        for ((centiemes, unite) in cas) {
            val horodatage = aParis("2026-11-10T08:00")
            val id = depot.enregistrerPrise(med, Quantite(centiemes), unite, horodatage)
            val prise = depot.prise(id)!!
            assertEquals(id.toString(), prise.id)
            assertEquals("Testoline", prise.medicament)
            assertEquals(Quantite(centiemes), prise.quantite)
            assertEquals(unite, prise.unite)
            assertEquals(horodatage, prise.horodatage)
            assertEquals(StatutPrise.CONFIRMEE, prise.statut)
            assertNull(prise.journeeForcee)
        }
        assertNull(depot.prise(999))
    }

    @Test
    fun annulationConserveLaLigneEtLExclutDesTotaux() = runBlocking {
        val med = depot.ajouterMedicament("Testoline")
        val a = depot.enregistrerPrise(med, Quantite(40), Unite.MG, aParis("2026-11-10T08:00"))
        val b = depot.enregistrerPrise(med, Quantite(25), Unite.MG, aParis("2026-11-10T12:00"))
        val cle = CleTotal("Testoline", Unite.MG)
        assertEquals(Quantite(65), depot.observerTotauxDuJour(aujourdhui, calc).first()[cle])

        assertTrue(depot.annulerPrise(b))
        assertFalse("déjà annulée", depot.annulerPrise(b))
        assertFalse("inconnue", depot.annulerPrise(999))

        val annulee = depot.prise(b)!!
        assertEquals(StatutPrise.ANNULEE, annulee.statut)
        assertEquals(Quantite(25), annulee.quantite)
        assertEquals(2, depot.prisesDuJour(aujourdhui, calc).size)
        assertEquals(mapOf(cle to Quantite(40)), depot.observerTotauxDuJour(aujourdhui, calc).first())
        assertEquals(StatutPrise.CONFIRMEE, depot.prise(a)!!.statut)
    }

    @Test
    fun reattributionDeplaceLaPriseSansToucherLHorodatage() = runBlocking {
        val med = depot.ajouterMedicament("Testoline")
        val nuit = aParis("2026-11-10T01:30") // avant le pivot 05:00 : journée de la veille
        val id = depot.enregistrerPrise(med, Quantite(25), Unite.MG, nuit)
        depot.enregistrerPrise(med, Quantite(50), Unite.MG, aParis("2026-11-10T08:00"))
        val cle = CleTotal("Testoline", Unite.MG)

        assertEquals(listOf(id.toString()), depot.prisesDuJour(hier, calc).map { it.id })
        assertEquals(Quantite(50), depot.observerTotauxDuJour(aujourdhui, calc).first()[cle])

        assertTrue(depot.reattribuer(id, aujourdhui))
        val deplacee = depot.prise(id)!!
        assertEquals(nuit, deplacee.horodatage)
        assertEquals(aujourdhui, deplacee.journeeForcee)
        assertTrue(depot.prisesDuJour(hier, calc).isEmpty())
        assertEquals(Quantite(75), depot.observerTotauxDuJour(aujourdhui, calc).first()[cle])

        // Réattribution vers une journée lointaine : retrouvée par sa journée forcée.
        val lointaine = LocalDate.parse("2026-11-20")
        assertTrue(depot.reattribuer(id, lointaine))
        assertEquals(listOf(id.toString()), depot.prisesDuJour(lointaine, calc).map { it.id })
        assertEquals(1, depot.prisesDuJour(aujourdhui, calc).size)

        assertTrue(depot.reattribuer(id, null))
        assertNull(depot.prise(id)!!.journeeForcee)
        assertEquals(listOf(id.toString()), depot.prisesDuJour(hier, calc).map { it.id })
        assertFalse(depot.reattribuer(999, aujourdhui))
    }

    @Test
    fun requeteParIntervalleDemiOuvert() = runBlocking {
        val med = depot.ajouterMedicament("Testoline")
        val bornes = calc.bornes(aujourdhui)
        val avant = depot.enregistrerPrise(med, Quantite(100), Unite.MG, bornes.debut.minusMillis(1))
        val debut = depot.enregistrerPrise(med, Quantite(100), Unite.MG, bornes.debut)
        val dernier = depot.enregistrerPrise(med, Quantite(100), Unite.MG, bornes.fin.minusMillis(1))
        val fin = depot.enregistrerPrise(med, Quantite(100), Unite.MG, bornes.fin)

        val ids = dao.entre(bornes.debut, bornes.fin).map { it.prise.id }
        assertEquals(listOf(debut, dernier), ids)
        assertEquals(listOf(debut, dernier).map(Long::toString), depot.prisesDuJour(aujourdhui, calc).map { it.id })

        // La requête brute par intervalle ignore la journée forcée ; celle par journée non.
        dao.reattribuer(debut, hier, maintenant)
        assertEquals(listOf(debut, dernier), dao.entre(bornes.debut, bornes.fin).map { it.prise.id })
        assertEquals(listOf(dernier), dao.deLaJournee(bornes.debut, bornes.fin, aujourdhui).map { it.prise.id })
        val bornesHier = calc.bornes(hier)
        assertEquals(listOf(avant, debut), dao.deLaJournee(bornesHier.debut, bornesHier.fin, hier).map { it.prise.id })
        assertNotNull(fin)
    }

    @Test
    fun observationEmetApresModification() = runBlocking {
        val med = depot.ajouterMedicament("Testoline")
        val flux = depot.observerPrisesDuJour(aujourdhui, calc)
        assertTrue(flux.first().isEmpty())
        depot.enregistrerPrise(med, Quantite(40), Unite.COMPRIME, aParis("2026-11-10T09:00"))
        assertEquals(1, flux.first().size)
    }

    @Test
    fun majLeMiseAJour() = runBlocking {
        val med = depot.ajouterMedicament("Testoline")
        val id = depot.enregistrerPrise(med, Quantite(40), Unite.MG, aParis("2026-11-10T09:00"))
        val plusTard = maintenant.plusSeconds(60)
        dao.annuler(id, plusTard)
        val ligne = dao.parId(id)!!.prise
        assertEquals(maintenant, ligne.creeLe)
        assertEquals(plusTard, ligne.majLe)
    }

    @Test
    fun cleEtrangereVersMedicament() {
        val prise = nouvellePriseEntity(999, Quantite(25), Unite.MG, maintenant, maintenant)
        assertThrows(SQLiteConstraintException::class.java) { runBlocking { dao.inserer(prise) } }

        val med = runBlocking { depot.ajouterMedicament("Testoline") }
        runBlocking { depot.enregistrerPrise(med, Quantite(25), Unite.MG, maintenant) }
        // ON DELETE RESTRICT : un médicament qui a des prises ne peut pas être supprimé.
        assertThrows(SQLiteConstraintException::class.java) { sql("DELETE FROM medicament WHERE id = ?", med) }
    }

    @Test
    fun contraintesSqlSurLaPrise() {
        val med = runBlocking { depot.ajouterMedicament("Testoline") }
        val valide = nouvellePriseEntity(med, Quantite(25), Unite.MG, maintenant, maintenant)
        for (invalide in listOf(valide.copy(quantiteCentiemes = 0), valide.copy(quantiteCentiemes = -25))) {
            assertThrows(SQLiteConstraintException::class.java) { runBlocking { dao.inserer(invalide) } }
        }
        val colonnes = "medicament_id, quantite_centiemes, unite, horodatage_utc_ms, statut, journee_forcee, cree_le, maj_le"
        val insertion = "INSERT INTO prise ($colonnes) VALUES (?, ?, ?, 0, ?, ?, 0, 0)"
        val cas = listOf(
            arrayOf<Any?>(med, 25, "LITRE", "CONFIRMEE", null),
            arrayOf<Any?>(med, 25, "MG", "SUPPRIMEE", null),
            arrayOf<Any?>(med, 25, "MG", "CONFIRMEE", "10/11/2026"),
            arrayOf<Any?>(med, 25, "MG", "CONFIRMEE", "2026-02-30"),
            arrayOf<Any?>(med, "0,25", "MG", "CONFIRMEE", null),
        )
        for (valeurs in cas) {
            assertThrows(valeurs.joinToString(), SQLiteConstraintException::class.java) { sql(insertion, *valeurs) }
        }
        sql(insertion, med, 25, "MG", "CONFIRMEE", "2026-11-10")

        val id = runBlocking { dao.inserer(valide) }
        assertThrows(SQLiteConstraintException::class.java) {
            sql("UPDATE prise SET quantite_centiemes = 0 WHERE id = ?", id)
        }
        assertThrows(SQLiteConstraintException::class.java) {
            sql("UPDATE prise SET statut = 'EFFACEE' WHERE id = ?", id)
        }
    }

    @Test
    fun valeurInconnueLueDonneUneErreurClaire() {
        val med = runBlocking { depot.ajouterMedicament("Testoline") }
        val id = runBlocking { depot.enregistrerPrise(med, Quantite(25), Unite.MG, maintenant) }
        // Simule une base écrite par une version future : déclencheur retiré, unité inconnue.
        sql("DROP TRIGGER prise_valide_modification")
        sql("UPDATE prise SET unite = 'LITRE' WHERE id = ?", id)
        assertThrows(ValeurStockeeInvalideException::class.java) { runBlocking { depot.prise(id) } }
    }

    @Test
    fun aucuneTableJournee() {
        val tables = mutableListOf<String>()
        base.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { c ->
            while (c.moveToNext()) tables += c.getString(0)
        }
        assertTrue(tables.containsAll(listOf("medicament", "prise", "alias")))
        assertFalse(tables.any { it.contains("journee") })
    }
}
