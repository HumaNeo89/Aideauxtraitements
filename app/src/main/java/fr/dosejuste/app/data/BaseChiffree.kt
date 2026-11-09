package fr.dosejuste.app.data

import android.content.Context
import androidx.room.Room
import fr.dosejuste.app.security.DbKeyManager
import fr.dosejuste.app.security.DonneesIllisiblesException
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Fabrique de la base chiffrée (Room + SQLCipher).
 *
 * Tout le fichier `databases/dosejuste.db` (et son journal) est chiffré par SQLCipher
 * (AES-256) avec la phrase de passe fournie par [DbKeyManager]. Sans cette phrase, le
 * fichier n'est pas lisible, pas même son en-tête SQLite.
 *
 * Ne pas appeler sur le fil principal (déchiffrement Keystore + dérivation de clé SQLCipher).
 * Garder une seule instance ouverte pour toute l'appli.
 */
object BaseChiffree {

    @Volatile
    private var bibliothequeChargee = false

    /** Charge la bibliothèque native de SQLCipher (une fois par processus). */
    @Synchronized
    fun chargerBibliotheque() {
        if (!bibliothequeChargee) {
            System.loadLibrary("sqlcipher")
            bibliothequeChargee = true
        }
    }

    /**
     * Ouvre la base de production, en créant la clé au tout premier lancement.
     *
     * @throws DonneesIllisiblesException si la clé de la base existante est perdue ou
     *   inutilisable. Rien n'est effacé ni recréé dans ce cas.
     */
    @Throws(DonneesIllisiblesException::class)
    fun ouvrir(context: Context): DoseJusteDatabase {
        val appContext = context.applicationContext
        val phrase = DbKeyManager.pourContexte(appContext, DoseJusteDatabase.NOM_FICHIER).phraseDePasse()
        return construire(appContext, DoseJusteDatabase.NOM_FICHIER, phrase)
    }

    /**
     * Construit une base chiffrée [nomFichier] avec [phrase].
     *
     * SQLCipher garde une référence à [phrase] pour ouvrir ses connexions : le tableau ne
     * doit pas être effacé tant que la base est ouverte. Il peut l'être après `close()`.
     */
    fun construire(context: Context, nomFichier: String, phrase: ByteArray): DoseJusteDatabase {
        chargerBibliotheque()
        return Room.databaseBuilder(context.applicationContext, DoseJusteDatabase::class.java, nomFichier)
            .openHelperFactory(SupportOpenHelperFactory(phrase))
            .configurationCommune()
            .build()
    }
}
