package fr.dosejuste.app.prefs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import fr.dosejuste.app.ui.FauxReglages
import fr.dosejuste.app.ui.ReglagesViewModel
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReglagesTest {

    private val contexte: Context = ApplicationProvider.getApplicationContext()

    private fun prefs() = contexte.getSharedPreferences(ReglagesPrefs.NOM_FICHIER, Context.MODE_PRIVATE)

    @Test
    fun valeursParDefaut() {
        val reglages = ReglagesPrefs(contexte)
        assertEquals(5, PIVOT_HEURE_DEFAUT)
        assertEquals(5, reglages.pivotHeure.value)
        assertFalse(reglages.avertissementAccepte.value)
        assertEquals(LocalTime.of(5, 0), pivotVersHeure(reglages.pivotHeure.value))
    }

    @Test
    fun pivotMemoriseEntreDeuxOuvertures() {
        ReglagesPrefs(contexte).definirPivotHeure(1)
        assertEquals(1, ReglagesPrefs(contexte).pivotHeure.value)
        ReglagesPrefs(contexte).definirPivotHeure(0)
        assertEquals(0, ReglagesPrefs(contexte).pivotHeure.value)
        ReglagesPrefs(contexte).definirPivotHeure(23)
        assertEquals(23, ReglagesPrefs(contexte).pivotHeure.value)
    }

    @Test
    fun pivotHorsPlageRefuse() {
        val reglages = ReglagesPrefs(contexte)
        assertThrows(IllegalArgumentException::class.java) { reglages.definirPivotHeure(24) }
        assertThrows(IllegalArgumentException::class.java) { reglages.definirPivotHeure(-1) }
        assertEquals(5, reglages.pivotHeure.value)
    }

    @Test
    fun valeurStockeeInvalideDonneLeDefaut() {
        prefs().edit().putInt(ReglagesPrefs.CLE_PIVOT, 42).commit()
        assertEquals(5, ReglagesPrefs(contexte).pivotHeure.value)
    }

    @Test
    fun acceptationMemoriseeEntreDeuxOuvertures() {
        val reglages = ReglagesPrefs(contexte)
        reglages.accepterAvertissement()
        assertTrue(reglages.avertissementAccepte.value)
        assertTrue(ReglagesPrefs(contexte).avertissementAccepte.value)
    }

    @Test
    fun seulsUnEntierEtUnBooleenSontStockes() {
        val reglages = ReglagesPrefs(contexte)
        reglages.definirPivotHeure(3)
        reglages.accepterAvertissement()
        assertEquals(setOf(ReglagesPrefs.CLE_PIVOT, ReglagesPrefs.CLE_AVERTISSEMENT), prefs().all.keys)
    }

    @Test
    fun viewModelIgnoreUneHeureHorsPlage() {
        val faux = FauxReglages(pivot = 5, accepte = false)
        val vm = ReglagesViewModel(faux)
        vm.choisirPivot(30)
        assertEquals(5, vm.pivotHeure.value)
        vm.choisirPivot(2)
        assertEquals(2, vm.pivotHeure.value)
        assertFalse(vm.avertissementAccepte.value)
        vm.accepterAvertissement()
        assertTrue(vm.avertissementAccepte.value)
    }
}
