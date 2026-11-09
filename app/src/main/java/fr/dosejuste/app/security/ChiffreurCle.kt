package fr.dosejuste.app.security

/**
 * Résultat d'un chiffrement AES-GCM : vecteur d'initialisation et texte chiffré
 * (étiquette d'authentification GCM incluse à la fin de [donnees]).
 *
 * Rien de secret ici : ces octets sont illisibles sans la clé du Keystore.
 */
class BlobChiffre(val iv: ByteArray, val donnees: ByteArray)

/**
 * Chiffre et déchiffre la phrase de passe de la base avec une clé qui ne quitte jamais
 * son coffre (Android Keystore en production, voir [KeystoreChiffreur]).
 *
 * Interface séparée pour tester sur la JVM la logique de fichiers et d'états de
 * [DbKeyManager] avec un faux chiffreur.
 */
interface ChiffreurCle {

    /** Vrai si la clé de chiffrement existe dans son coffre. */
    fun cleExiste(): Boolean

    /** Crée la clé ; remplace une éventuelle clé du même alias. Appelé seulement si aucun blob n'existe. */
    fun genererCle()

    /** Chiffre [clair] avec un IV aléatoire choisi par l'implémentation. */
    fun chiffrer(clair: ByteArray): BlobChiffre

    /**
     * Déchiffre et authentifie [blob]. Lève une [java.security.GeneralSecurityException]
     * (ex. AEADBadTagException, KeyPermanentlyInvalidatedException) si c'est impossible.
     * L'appelant doit effacer le tableau retourné après usage.
     */
    fun dechiffrer(blob: BlobChiffre): ByteArray
}
