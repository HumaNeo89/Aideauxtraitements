package fr.dosejuste.app.domain

/**
 * Unités de quantité, en liste fermée.
 *
 * [libelle] est la forme au singulier ; [libellePluriel] la forme utilisée à partir de 2
 * (règle française). mg et ml sont invariables.
 */
enum class Unite(val libelle: String, val libellePluriel: String) {
    MG("mg", "mg"),
    COMPRIME("comprimé", "comprimés"),
    GOUTTE("goutte", "gouttes"),
    ML("ml", "ml");

    /** Libellé accordé à [quantite] : pluriel si la valeur est supérieure ou égale à 2. */
    fun libellePour(quantite: Quantite): String =
        if (quantite.centiemes >= 200) libellePluriel else libelle
}
