# E1 : la minification release n'est pas activée.
# Ne rien mettre ici qui ressemble à un secret ou à une donnée de santé.

# E3 : SQLCipher (sqlcipher-android). Règles recommandées par Zetetic, déjà fournies par
# l'AAR (consumer rules) ; répétées ici pour le jour où la minification release sera activée.
# Les classes appelées depuis le code natif (JNI) ne doivent être ni renommées ni retirées.
-keep,includedescriptorclasses class net.zetetic.database.** { *; }
-keep,includedescriptorclasses interface net.zetetic.database.** { *; }
-keepclasseswithmembernames class net.zetetic.database.** {
    native <methods>;
}
-dontwarn net.zetetic.database.**

# Room fournit ses propres règles (consumer rules) : rien à ajouter.
