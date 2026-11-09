package fr.dosejuste.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class QuantiteTest {

    private fun ok(texte: String): Long {
        val resultat = Quantite.analyser(texte)
        check(resultat is ResultatAnalyse.Ok) { "Attendu Ok pour « $texte », obtenu $resultat" }
        return resultat.quantite.centiemes
    }

    private fun erreur(texte: String): RaisonErreur {
        val resultat = Quantite.analyser(texte)
        check(resultat is ResultatAnalyse.Erreur) { "Attendu Erreur pour « $texte », obtenu $resultat" }
        return resultat.raison
    }

    @Test
    fun saisiesAcceptees() {
        assertEquals(25L, ok("0,25"))
        assertEquals(40L, ok("0.4"))
        assertEquals(100L, ok("1"))
        assertEquals(150L, ok("  1,5 "))
        assertEquals(150L, ok("\t1.5\n"))
        assertEquals(1050L, ok("10,50"))
        assertEquals(50L, ok("0,50"))
        assertEquals(1L, ok("0,01"))
        assertEquals(700L, ok("007"))
        assertEquals(Quantite.PLAFOND_SAISIE, ok("100000"))
        assertEquals(Quantite.PLAFOND_SAISIE, ok("100000,00"))
    }

    @Test
    fun saisieVide() {
        assertEquals(RaisonErreur.VIDE, erreur(""))
        assertEquals(RaisonErreur.VIDE, erreur("   "))
    }

    @Test
    fun saisiesMalFormees() {
        val refus = listOf(
            ",5", "5,", ".5", "5.", "+1", "--1", "1e3", "1E3", "1,000,5", "1.000,5", "1 000",
            "1 5", "1,5 mg", "abc", "1,,5", "1,5,", "0x10", "١", "１", "1 000", "-", "NaN",
        )
        for (texte in refus) {
            assertEquals("« $texte »", RaisonErreur.FORMAT, erreur(texte))
        }
    }

    @Test
    fun tropDeDecimales() {
        assertEquals(RaisonErreur.TROP_DE_DECIMALES, erreur("0,125"))
        assertEquals(RaisonErreur.TROP_DE_DECIMALES, erreur("1.000"))
        assertEquals(RaisonErreur.TROP_DE_DECIMALES, erreur("0,001"))
    }

    @Test
    fun nulOuNegatif() {
        assertEquals(RaisonErreur.NUL_OU_NEGATIF, erreur("0"))
        assertEquals(RaisonErreur.NUL_OU_NEGATIF, erreur("0,00"))
        assertEquals(RaisonErreur.NUL_OU_NEGATIF, erreur("-1"))
        assertEquals(RaisonErreur.NUL_OU_NEGATIF, erreur("-0,5"))
    }

    @Test
    fun tropGrand() {
        assertEquals(RaisonErreur.TROP_GRAND, erreur("100000,01"))
        assertEquals(RaisonErreur.TROP_GRAND, erreur("999999"))
        assertEquals(RaisonErreur.TROP_GRAND, erreur("99999999999999999999999999"))
    }

    @Test
    fun constructionRefuseNulEtNegatif() {
        assertThrows(IllegalArgumentException::class.java) { Quantite(0) }
        assertThrows(IllegalArgumentException::class.java) { Quantite(-5) }
    }

    @Test
    fun formatageSansZerosInutiles() {
        assertEquals("0,25", Quantite(25).formater())
        assertEquals("0,4", Quantite(40).formater())
        assertEquals("1", Quantite(100).formater())
        assertEquals("10,5", Quantite(1050).formater())
        assertEquals("0,05", Quantite(5).formater())
        assertEquals("100000", Quantite(Quantite.PLAFOND_SAISIE).formater())
    }

    @Test
    fun formatageAvecUnite() {
        assertEquals("0,25 mg", Quantite(25).formater(Unite.MG))
        assertEquals("0,4 mg", Quantite(40).formater(Unite.MG))
        assertEquals("1 mg", Quantite(100).formater(Unite.MG))
        assertEquals("2 ml", Quantite(200).formater(Unite.ML))
        assertEquals("1 comprimé", Quantite(100).formater(Unite.COMPRIME))
        assertEquals("0,5 comprimé", Quantite(50).formater(Unite.COMPRIME))
        assertEquals("1,99 comprimé", Quantite(199).formater(Unite.COMPRIME))
        assertEquals("2 comprimés", Quantite(200).formater(Unite.COMPRIME))
        assertEquals("1 goutte", Quantite(100).formater(Unite.GOUTTE))
        assertEquals("3 gouttes", Quantite(300).formater(Unite.GOUTTE))
    }

    @Test
    fun allerRetourAnalyseFormatage() {
        for (c in listOf(1L, 5L, 10L, 25L, 40L, 99L, 100L, 101L, 150L, 1050L, 123456L, Quantite.PLAFOND_SAISIE)) {
            assertEquals(c, ok(Quantite(c).formater()))
        }
    }

    @Test
    fun additionExacteEtDebordement() {
        assertEquals(Quantite(65), Quantite(25) + Quantite(40))
        // Une somme peut dépasser la garde de saisie.
        assertEquals(Quantite(2 * Quantite.PLAFOND_SAISIE), Quantite(Quantite.PLAFOND_SAISIE) + Quantite(Quantite.PLAFOND_SAISIE))
        val tresGrand = Quantite(9_223_372_036_854_775_807L)
        assertThrows(ArithmeticException::class.java) { tresGrand + Quantite(1) }
    }
}
