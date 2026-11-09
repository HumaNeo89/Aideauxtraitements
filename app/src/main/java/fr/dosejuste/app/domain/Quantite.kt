package fr.dosejuste.app.domain

/**
 * Quantité exacte, exprimée en centièmes d'unité (100 = 1).
 *
 * Toujours strictement positive. Aucun nombre à virgule flottante n'est utilisé.
 * [PLAFOND_SAISIE] n'est qu'une garde technique contre les fautes de frappe lors de la
 * saisie ([analyser]) ; ce n'est pas une valeur médicale. Une somme ([plus]) peut le
 * dépasser ; seul le débordement d'un Long est refusé.
 */
@JvmInline
value class Quantite(val centiemes: Long) {

    init {
        require(centiemes > 0) { "Une quantité doit être strictement positive : $centiemes" }
    }

    /** Somme exacte ; lève [ArithmeticException] en cas de débordement. */
    operator fun plus(autre: Quantite): Quantite = Quantite(Math.addExact(centiemes, autre.centiemes))

    /** Affichage français sans zéro inutile : 25 → "0,25", 40 → "0,4", 100 → "1", 1050 → "10,5". */
    fun formater(): String {
        val entier = centiemes / 100
        val reste = (centiemes % 100).toInt()
        if (reste == 0) return entier.toString()
        val decimales = reste.toString().padStart(2, '0').trimEnd('0')
        return "$entier,$decimales"
    }

    /** Affichage avec unité accordée : "0,25 mg", "2 comprimés", "0,5 comprimé". */
    fun formater(unite: Unite): String = "${formater()} ${unite.libellePour(this)}"

    companion object {
        /** Garde de saisie : 100 000,00 (en centièmes). Au-delà, la saisie est refusée. */
        const val PLAFOND_SAISIE: Long = 10_000_000L

        private val NOMBRE = Regex("^([0-9]+)(?:[.,]([0-9]+))?$")

        /**
         * Analyse une saisie utilisateur.
         *
         * Accepté : chiffres ASCII, au plus un séparateur décimal (virgule ou point) suivi
         * d'1 ou 2 chiffres, espaces autour ignorés ("0,25", "0.4", " 1,5 ", "0,50").
         * Refusé en [RaisonErreur.FORMAT] : ",5", "5,", signe "+", exposant, séparateur
         * de milliers, espace interne, lettres, chiffres non ASCII.
         * Un signe "-" devant un nombre bien formé donne [RaisonErreur.NUL_OU_NEGATIF].
         */
        fun analyser(texte: String): ResultatAnalyse {
            val saisie = texte.trim()
            if (saisie.isEmpty()) return ResultatAnalyse.Erreur(RaisonErreur.VIDE)

            val negatif = saisie.startsWith('-')
            val corps = if (negatif) saisie.substring(1) else saisie
            val correspondance = NOMBRE.matchEntire(corps)
                ?: return ResultatAnalyse.Erreur(RaisonErreur.FORMAT)
            if (negatif) return ResultatAnalyse.Erreur(RaisonErreur.NUL_OU_NEGATIF)

            val partieEntiere = correspondance.groupValues[1].trimStart('0')
            val partieDecimale = correspondance.groupValues[2]
            if (partieDecimale.length > 2) return ResultatAnalyse.Erreur(RaisonErreur.TROP_DE_DECIMALES)
            // 7 chiffres entiers ou plus dépassent forcément le plafond : on évite tout débordement.
            if (partieEntiere.length > 6) return ResultatAnalyse.Erreur(RaisonErreur.TROP_GRAND)

            val entier = if (partieEntiere.isEmpty()) 0L else partieEntiere.toLong()
            val centiemes = entier * 100 + partieDecimale.padEnd(2, '0').toLong()
            return when {
                centiemes == 0L -> ResultatAnalyse.Erreur(RaisonErreur.NUL_OU_NEGATIF)
                centiemes > PLAFOND_SAISIE -> ResultatAnalyse.Erreur(RaisonErreur.TROP_GRAND)
                else -> ResultatAnalyse.Ok(Quantite(centiemes))
            }
        }
    }
}

/** Résultat de [Quantite.analyser]. */
sealed interface ResultatAnalyse {
    data class Ok(val quantite: Quantite) : ResultatAnalyse
    data class Erreur(val raison: RaisonErreur) : ResultatAnalyse
}

/** Motif de refus d'une saisie de quantité. */
enum class RaisonErreur { VIDE, FORMAT, TROP_DE_DECIMALES, NUL_OU_NEGATIF, TROP_GRAND }
