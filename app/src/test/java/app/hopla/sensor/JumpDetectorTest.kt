package app.hopla.sensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

class JumpDetectorTest {

    private val g = 9.81f

    /** Signal synthétique : impact à la réception, phase d'apesanteur, retour. */
    private fun jumpSignal(cadenceHz: Double, seconds: Double, impact: Float, noise: Float, seed: Int): Pair<List<Float>, Int> {
        val rnd = Random(seed)
        val out = ArrayList<Float>()
        var t = 0.0
        while (t < seconds) {
            val period = 1.0 / cadenceHz * (1 + rnd.nextDouble(-0.05, 0.05))
            val n = (period * 100).toInt()
            for (i in 0 until n) {
                val p = i.toDouble() / n
                val m = when {
                    p < 0.12 -> g + impact * sin(PI * p / 0.12).toFloat()
                    p < 0.55 -> g * 0.25f
                    else -> g * 0.25f + g * 0.75f * ((p - 0.55) / 0.45).toFloat()
                }
                out += m + (rnd.nextFloat() - 0.5f) * 2f * noise
            }
            t += period
        }
        return out to Math.round(seconds * cadenceHz).toInt()
    }

    private fun count(samples: List<Float>, sensitivity: Float = 1f): Int {
        val d = JumpDetector(sensitivity)
        return samples.withIndex().count { (i, m) -> d.onSample(0f, 0f, m, i * 10_000_000L) }
    }

    @Test
    fun countsJumpsAcrossCadences() {
        for (cadence in listOf(1.3, 2.0, 2.7, 3.5)) {
            for (impact in listOf(9f, 16f, 28f)) {
                val (signal, expected) = jumpSignal(cadence, 30.0, impact, 1.5f, seed = (cadence * 100).toInt())
                val counted = count(signal)
                assertTrue("cadence=$cadence impact=$impact counted=$counted expected=$expected",
                    abs(counted - expected) <= maxOf(2, expected / 30))
            }
        }
    }

    @Test
    fun ignoresStillPhone() {
        val rnd = Random(7)
        val idle = List(6000) { g + (rnd.nextFloat() - 0.5f) * 1.2f }
        assertEquals(0, count(idle))
    }
}
