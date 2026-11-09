package fr.dosejuste.app.ui

import fr.dosejuste.app.domain.Quantite
import fr.dosejuste.app.domain.Unite
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Jour choisi dans le formulaire : jour civil (pas la « journée » à pivot). */
enum class JourSaisie { AUJOURDHUI, HIER }

/**
 * Phrases françaises de relecture et de liste, en Kotlin pur (testables sur la JVM).
 * Elles suivent le même principe que `Quantite.formater` : le français est dans le code
 * du formatage ; les libellés de l'interface, eux, sont dans strings.xml.
 */
object PhrasePrise {

    private const val VOYELLES = "aeiouyhàâäéèêëîïôöùûü"

    /** « 2 mg de Testoline », « 1 comprimé d'Exemple » (élision devant voyelle ou h). */
    fun quantiteEtMedicament(quantite: Quantite, unite: Unite, libelle: String): String {
        val de = if (libelle.firstOrNull()?.lowercaseChar()?.let { it in VOYELLES } == true) "d'" else "de "
        return "${quantite.formater(unite)} $de$libelle"
    }

    fun heure(heure: LocalTime): String = String.format(Locale.ROOT, "%02d:%02d", heure.hour, heure.minute)

    /** « 2 mg de Testoline à 14:05 » ou « 0,5 mg de Testoline hier à 01:30 ». */
    fun relecture(quantite: Quantite, unite: Unite, libelle: String, jour: JourSaisie, heure: LocalTime): String {
        val hier = if (jour == JourSaisie.HIER) "hier " else ""
        return "${quantiteEtMedicament(quantite, unite, libelle)} ${hier}à ${heure(heure)}"
    }

    /** Ligne de liste : « 14:05 — 0,25 mg de Testoline ». */
    fun ligne(quantite: Quantite, unite: Unite, libelle: String, heure: LocalTime): String =
        "${heure(heure)} — ${quantiteEtMedicament(quantite, unite, libelle)}"

    /** Total d'un médicament dans une unité : « Testoline : 2 mg ». Jamais d'addition entre unités. */
    fun total(libelle: String, quantite: Quantite, unite: Unite): String = "$libelle : ${quantite.formater(unite)}"

    /** « mardi 10 novembre 2026 ». */
    fun jour(date: LocalDate): String = date.format(FORMAT_JOUR)

    private val FORMAT_JOUR: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH)
}
