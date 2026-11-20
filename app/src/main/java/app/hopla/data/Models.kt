package app.hopla.data

import kotlinx.serialization.Serializable

@Serializable
data class JumpSession(
    val id: String,
    val startedAt: Long,
    val durationSec: Int,
    val activeSec: Int,
    val jumps: Int,
    val kcal: Double,
    val mode: String,
    val peakCadence: Int,
    /** false tant que la séance n'a pas été envoyée au backend. */
    val synced: Boolean = false,
)

enum class PaletteId(val label: String) {
    BRAISE("Braise"), LAGON("Lagon"), ACIDE("Acide"), PRUNE("Prune"), DYNAMIC("Toi")
}

enum class TitleStyleId(val label: String, val uppercase: Boolean) {
    BOLIDE("Bolide", true),
    AFFICHE("Affiche", true),
    ARCADE("Arcade", true),
    ELASTIQUE("Élastique", false),
}

enum class DarkMode(val label: String) { SYSTEM("Auto"), LIGHT("Clair"), DARK("Sombre") }

enum class Sensitivity(val label: String, val factor: Float) {
    SOFT("Douce", 0.8f), NORMAL("Normale", 1f), SHARP("Vive", 1.3f)
}

data class UserSettings(
    val name: String = "Alex",
    val handle: String = "alex.hop",
    val weightKg: Int = 70,
    val dailyGoal: Int = 1000,
    val palette: PaletteId = PaletteId.BRAISE,
    val titleStyle: TitleStyleId = TitleStyleId.BOLIDE,
    val darkMode: DarkMode = DarkMode.SYSTEM,
    val sensitivity: Sensitivity = Sensitivity.NORMAL,
    val following: Set<String> = setOf("ines", "malik", "lea"),
)

/** Un·e sportif·ve visible dans l'onglet social (toi y compris). */
data class Athlete(
    val id: String,
    val name: String,
    val handle: String,
    val shapeIndex: Int,
    val colorIndex: Int,
    val todayJumps: Int,
    val weekJumps: List<Int>,
    val lastWeekJumps: List<Int>,
    val weekKcal: Int,
    val weekMinutes: Int,
    val bestSession: Int,
    val streak: Int,
    val recent: List<RecentActivity>,
    val isMe: Boolean = false,
) {
    val weekTotal: Int get() = weekJumps.sum()
    val initials: String
        get() = name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
}

data class RecentActivity(val label: String, val jumps: Int, val minutes: Int, val whenLabel: String)
