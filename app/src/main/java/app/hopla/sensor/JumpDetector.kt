package app.hopla.sensor

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Détecteur de sauts à partir de l'accéléromètre (gravité incluse).
 *
 * Principe : à chaque saut, le téléphone (poche, brassard ou main) subit un pic
 * d'accélération à la réception, précédé d'une phase de quasi-apesanteur.
 *
 *  1. |a| = norme du vecteur (insensible à l'orientation du téléphone)
 *  2. Gravité estimée par un passe-bas très lent → composante dynamique = |a| - g
 *  3. Lissage rapide pour gommer le bruit haute fréquence
 *  4. Détection de pic avec hystérésis + seuil adaptatif (suit l'intensité moyenne
 *     des sauts de la personne) + période réfractaire (pas plus de ~4,3 sauts/s)
 *
 * Classe pure Kotlin, sans dépendance Android → testable unitairement.
 */
class JumpDetector(
    sensitivity: Float = 1f,
) {
    /** 0.8 = douce (moins de faux positifs), 1.0 = normale, 1.3 = vive. */
    var sensitivity: Float = sensitivity
        set(value) {
            field = value.coerceIn(0.5f, 2f)
        }

    private var gravity = Float.NaN
    private var smooth = 0f
    private var armed = true
    private var peak = 0f
    private var lastJumpNs = Long.MIN_VALUE / 2
    private var avgPeak = INITIAL_AVG_PEAK

    fun reset() {
        gravity = Float.NaN
        smooth = 0f
        armed = true
        peak = 0f
        lastJumpNs = Long.MIN_VALUE / 2
        avgPeak = INITIAL_AVG_PEAK
    }

    /** Seuil courant (m/s²), exposé pour le debug / l'UI. */
    val threshold: Float
        get() = max(MIN_THRESHOLD, avgPeak * ADAPTIVE_RATIO) / sensitivity

    /**
     * @return true si un saut vient d'être validé sur cet échantillon.
     */
    fun onSample(x: Float, y: Float, z: Float, timestampNs: Long): Boolean {
        val magnitude = sqrt(x * x + y * y + z * z)
        if (gravity.isNaN()) {
            gravity = magnitude
            return false
        }
        gravity += GRAVITY_ALPHA * (magnitude - gravity)
        val dynamic = magnitude - gravity
        smooth += SMOOTH_ALPHA * (dynamic - smooth)

        val t = threshold
        if (armed) {
            if (smooth > t) {
                armed = false
                peak = smooth
            }
            return false
        }

        if (smooth > peak) peak = smooth
        if (smooth < t * RELEASE_RATIO) {
            armed = true
            val validPeak = peak < MAX_PLAUSIBLE_PEAK
            if (validPeak && timestampNs - lastJumpNs >= MIN_INTERVAL_NS) {
                lastJumpNs = timestampNs
                avgPeak += PEAK_ALPHA * (min(peak, MAX_PLAUSIBLE_PEAK) - avgPeak)
                return true
            }
        }
        return false
    }

    companion object {
        const val MIN_THRESHOLD = 2.6f // m/s²
        const val ADAPTIVE_RATIO = 0.42f
        const val RELEASE_RATIO = 0.35f
        const val INITIAL_AVG_PEAK = 7f
        const val MAX_PLAUSIBLE_PEAK = 70f // chute du téléphone, choc...
        const val GRAVITY_ALPHA = 0.02f
        const val SMOOTH_ALPHA = 0.35f
        const val PEAK_ALPHA = 0.15f
        const val MIN_INTERVAL_NS = 230_000_000L // 230 ms
    }
}
