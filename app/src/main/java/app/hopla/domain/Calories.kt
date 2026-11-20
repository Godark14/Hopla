package app.hopla.domain

/**
 * Dépense énergétique de la corde à sauter selon le Compendium of Physical Activities :
 *  - < 100 sauts/min  → MET 8.8
 *  - 100–120 sauts/min → MET 11.8
 *  - > 120 sauts/min  → MET 12.3
 * kcal/min = MET × 3.5 × poids(kg) / 200
 */
object Calories {
    fun met(cadencePerMin: Int): Double = when {
        cadencePerMin <= 0 -> 1.3 // repos debout
        cadencePerMin < 100 -> 8.8
        cadencePerMin <= 120 -> 11.8
        else -> 12.3
    }

    fun kcal(cadencePerMin: Int, weightKg: Int, seconds: Double): Double =
        met(cadencePerMin) * 3.5 * weightKg / 200.0 * (seconds / 60.0)

    /** Estimation rapide pour afficher "~90 kcal" sur une séance. */
    fun estimate(seconds: Int, weightKg: Int, cadence: Int = 110): Int =
        kcal(cadence, weightKg, seconds.toDouble()).toInt()
}
