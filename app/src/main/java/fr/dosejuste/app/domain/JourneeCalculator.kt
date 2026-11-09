package fr.dosejuste.app.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Intervalle d'une journée : [debut] inclus, [fin] exclu. */
data class IntervalleJournee(val debut: Instant, val fin: Instant) {
    init {
        require(debut < fin) { "Intervalle vide : $debut .. $fin" }
    }

    val duree: Duration get() = Duration.between(debut, fin)

    fun contient(instant: Instant): Boolean = instant >= debut && instant < fin
}

/**
 * Rattache un instant à une « journée » de prises.
 *
 * La journée D commence le jour D à l'heure [pivot] (heure locale de [zone]) et finit
 * juste avant le pivot du jour D+1. Ex. pivot 05:00 : 00:59 compte pour la veille ;
 * pivot 23:00 : la journée D va du jour D à 23:00 au jour D+1 à 22:59:59.
 *
 * Règle de début (changements d'heure) : la journée D commence au premier instant où
 * l'heure locale du jour D atteint ou dépasse le pivot.
 * - Pivot dans un trou (heure qui n'existe pas, ex. 02:30 le 29/03/2026 à Paris) : la
 *   journée commence à l'instant du saut (03:00 heure d'été).
 * - Pivot dans un chevauchement (heure vécue deux fois, ex. 02:30 le 25/10/2026) : la
 *   journée commence à la première occurrence (heure d'été) ; la seconde occurrence de
 *   02:00–02:29 reste dans la journée D, sans retour à la veille.
 * Ainsi [journeeDe] et [bornes] sont cohérents : chaque instant appartient à exactement
 * une journée. Durée normale 24 h, 23 h ou 25 h autour d'un changement d'heure (ou
 * une valeur intermédiaire si le pivot tombe dans le trou).
 *
 * Le pivot est exprimé à la minute (secondes et nanosecondes nulles).
 */
class JourneeCalculator(
    val zone: ZoneId = ZONE_DEFAUT,
    val pivot: LocalTime = PIVOT_DEFAUT,
) {
    init {
        require(pivot.second == 0 && pivot.nano == 0) { "Le pivot doit être à la minute : $pivot" }
    }

    /** Journée à laquelle appartient [instant]. */
    fun journeeDe(instant: Instant): LocalDate {
        val dateLocale = instant.atZone(zone).toLocalDate()
        return if (instant < debut(dateLocale)) dateLocale.minusDays(1) else dateLocale
    }

    /** Bornes de [journee] : journeeDe(i) == journee ⇔ bornes(journee).contient(i). */
    fun bornes(journee: LocalDate): IntervalleJournee =
        IntervalleJournee(debut(journee), debut(journee.plusDays(1)))

    private fun debut(journee: LocalDate): Instant {
        val local = journee.atTime(pivot)
        val transition = zone.rules.getTransition(local)
        return when {
            transition == null -> local.atZone(zone).toInstant()
            transition.isGap -> transition.instant
            else -> local.toInstant(transition.offsetBefore)
        }
    }

    companion object {
        val ZONE_DEFAUT: ZoneId = ZoneId.of("Europe/Paris")
        val PIVOT_DEFAUT: LocalTime = LocalTime.of(5, 0)
    }
}
