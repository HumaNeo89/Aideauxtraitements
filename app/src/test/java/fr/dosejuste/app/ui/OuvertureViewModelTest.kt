package fr.dosejuste.app.ui

import fr.dosejuste.app.security.DonneesIllisiblesException
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OuvertureViewModelTest {

    private val es = Executors.newSingleThreadExecutor { Thread(it, "es-test") }
    private val dispatcher = es.asCoroutineDispatcher()

    @Before
    fun installer() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun retirer() {
        Dispatchers.resetMain()
        es.shutdownNow()
    }

    private fun attendre(vm: OuvertureViewModel): EtatOuverture = runBlocking {
        withTimeout(5_000) { vm.etat.first { it != EtatOuverture.Chargement } }
    }

    @Test
    fun ouvertureReussieHorsDuFilAppelant() {
        var fil: String? = null
        val journal = FauxJournal()
        val vm = OuvertureViewModel({ fil = Thread.currentThread().name; journal }, dispatcher)
        val etat = attendre(vm)
        assertSame(journal, (etat as EtatOuverture.Pret).journal)
        assertTrue("fil : $fil", fil!!.startsWith("es-test")) // le débogueur de coroutines ajoute un suffixe
        assertNotEquals(Thread.currentThread().name, fil)
    }

    @Test
    fun etatChargementTantQueLOuvertureDure() {
        val verrou = java.util.concurrent.CountDownLatch(1)
        val vm = OuvertureViewModel({ verrou.await(); FauxJournal() }, dispatcher)
        assertEquals(EtatOuverture.Chargement, vm.etat.value)
        verrou.countDown()
        assertTrue(attendre(vm) is EtatOuverture.Pret)
    }

    @Test
    fun donneesIllisiblesDonnentLEcranDedie() {
        val causes = listOf(
            DonneesIllisiblesException.CleKeystoreAbsente(),
            DonneesIllisiblesException.DechiffrementImpossible(IllegalStateException("x")),
            DonneesIllisiblesException.BlobCorrompu(),
            DonneesIllisiblesException.BlobAbsentBasePresente(),
        )
        for (cause in causes) {
            val vm = OuvertureViewModel({ throw cause }, dispatcher)
            assertEquals(cause::class.simpleName, EtatOuverture.DonneesIllisibles, attendre(vm))
        }
    }

    @Test
    fun autreErreurDonneEcranGenerique() {
        val vm = OuvertureViewModel({ throw IllegalStateException("détail technique") }, dispatcher)
        assertEquals(EtatOuverture.ErreurGenerique, attendre(vm))
        val lien = OuvertureViewModel({ throw UnsatisfiedLinkError("sqlcipher") }, dispatcher)
        assertEquals(EtatOuverture.ErreurGenerique, attendre(lien))
    }
}
