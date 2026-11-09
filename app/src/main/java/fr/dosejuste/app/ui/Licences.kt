package fr.dosejuste.app.ui

/**
 * Notice d'un composant tiers. [fichierTexte] est le texte complet, sous
 * `app/src/main/assets/licences/` ; `null` quand la notice se résume à la mention (SQLite).
 */
data class NoticeTierce(
    val titre: String,
    val copyright: String,
    val licence: String,
    val fichierTexte: String?,
)

object Licences {
    const val DOSSIER = "licences"

    val notices: List<NoticeTierce> = listOf(
        NoticeTierce(
            titre = "SQLCipher for Android Community Edition 4.20.0",
            copyright = "Copyright (c) 2008-2023 ZETETIC LLC",
            licence = "Licence BSD (style Zetetic)",
            fichierTexte = "sqlcipher.txt",
        ),
        NoticeTierce(
            titre = "LibTomCrypt (bibliothèque cryptographique embarquée dans libsqlcipher.so)",
            copyright = "Auteurs de LibTomCrypt",
            licence = "The LibTom license / Unlicense (domaine public)",
            fichierTexte = "libtomcrypt.txt",
        ),
        NoticeTierce(
            titre = "SQLite",
            copyright = "Domaine public : les auteurs ont renoncé à leurs droits",
            licence = "Domaine public",
            fichierTexte = null,
        ),
        NoticeTierce(
            titre = "AndroidX (Room, SQLite, Activity, Lifecycle, Compose)",
            copyright = "Copyright The Android Open Source Project",
            licence = "Apache License 2.0",
            fichierTexte = "apache-2.0.txt",
        ),
        NoticeTierce(
            titre = "Kotlin",
            copyright = "Copyright JetBrains s.r.o. and Kotlin Programming Language contributors",
            licence = "Apache License 2.0",
            fichierTexte = "apache-2.0.txt",
        ),
        NoticeTierce(
            titre = "kotlinx.coroutines",
            copyright = "Copyright JetBrains s.r.o. and contributors",
            licence = "Apache License 2.0",
            fichierTexte = "apache-2.0.txt",
        ),
    )

    fun chemin(fichier: String): String = "$DOSSIER/$fichier"
}
