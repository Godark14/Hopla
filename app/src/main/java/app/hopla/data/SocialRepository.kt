package app.hopla.data

import app.hopla.domain.Stats
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.random.Random

/**
 * Contrat du réseau social. Deux implémentations :
 *  - [SupabaseSocialRepository] : le vrai backend (clés dans local.properties)
 *  - [FakeSocialRepository]     : données simulées, utilisée quand aucune clé n'est fournie
 */
interface SocialRepository {
    /** true = backend réel, false = démo hors ligne. */
    val isRemote: Boolean

    /** Toi + les personnes que tu suis (accueil, classement, onglet « Suivis »). */
    suspend fun circle(): List<Athlete>

    /** Autres athlètes correspondant à la recherche (vide = suggestions récentes). */
    suspend fun search(query: String): List<Athlete>

    suspend fun athlete(id: String): Athlete?

    val following: Flow<Set<String>>
    suspend fun setFollowing(id: String, follow: Boolean)

    /** Envoie les séances pas encore synchronisées. Renvoie le nombre envoyé. */
    suspend fun syncPending(): Int
}

class FakeSocialRepository(
    private val settings: SettingsRepository,
    private val sessions: SessionRepository,
) : SocialRepository {

    override val isRemote = false

    private data class Seed(val id: String, val name: String, val handle: String, val level: Int)

    private val seeds = listOf(
        Seed("ines", "Inès Morel", "ines.saute", 9),
        Seed("malik", "Malik Benali", "malik_hop", 8),
        Seed("lea", "Léa Garnier", "lea.rope", 6),
        Seed("theo", "Théo Fontaine", "theo_tabata", 7),
        Seed("chloe", "Chloé Lambert", "chlochlo", 4),
        Seed("yanis", "Yanis Petit", "yanisjump", 5),
        Seed("sarah", "Sarah Nguyen", "sarahskips", 10),
        Seed("hugo", "Hugo Marchand", "hugo.double", 3),
        Seed("camille", "Camille Roux", "camroux", 6),
        Seed("nadia", "Nadia Haddad", "nadia.boxe", 8),
        Seed("jules", "Jules Robin", "jules_crossrope", 2),
    )

    private val labels = listOf("Libre", "1 min chrono", "Tabata ×8", "Objectif 500", "10 min chrono")
    private val whens = listOf("il y a 2 h", "ce matin", "hier", "hier soir", "il y a 2 j", "il y a 3 j")

    private fun generate(seed: Seed, index: Int): Athlete {
        val rnd = Random(seed.id.hashCode())
        fun week(): List<Int> = List(7) {
            if (rnd.nextFloat() < 0.25f) 0 else (seed.level * 90 + rnd.nextInt(0, seed.level * 120 + 1))
        }
        val weekJumps = week()
        val minutes = weekJumps.sum() / 110
        return Athlete(
            id = seed.id,
            name = seed.name,
            handle = seed.handle,
            shapeIndex = index,
            colorIndex = index % 4,
            todayJumps = weekJumps[Stats.dashboard(emptyList()).todayIndex],
            weekJumps = weekJumps,
            lastWeekJumps = week(),
            weekKcal = (minutes * 12.5).toInt(),
            weekMinutes = minutes,
            bestSession = seed.level * 160 + rnd.nextInt(50, 400),
            streak = rnd.nextInt(0, seed.level * 3 + 2),
            recent = List(4) {
                val jumps = seed.level * 60 + rnd.nextInt(80, 500)
                RecentActivity(labels[rnd.nextInt(labels.size)], jumps, (jumps / 110).coerceAtLeast(1), whens[it + rnd.nextInt(0, 2)])
            },
        )
    }

    private val others: List<Athlete> by lazy { seeds.mapIndexed { i, s -> generate(s, i + 1) } }

    private suspend fun me(): Athlete {
        val s = settings.settings.first()
        val all = sessions.sessions.first()
        val d = Stats.dashboard(all)
        return Athlete(
            id = ME_ID, name = s.name, handle = s.handle, shapeIndex = 0, colorIndex = 0,
            todayJumps = d.todayJumps, weekJumps = d.weekJumps, lastWeekJumps = d.lastWeekJumps,
            weekKcal = d.weekKcal, weekMinutes = d.weekMinutes, bestSession = d.bestSession, streak = d.streak,
            recent = all.take(4).map { RecentActivity(it.mode, it.jumps, (it.durationSec / 60).coerceAtLeast(1), relativeTime(it.startedAt)) },
            isMe = true,
        )
    }

    override suspend fun circle(): List<Athlete> {
        delay(Random.nextLong(400, 800)) // latence simulée → on voit les loaders expressifs
        val f = following.first()
        return listOf(me()) + others.filter { it.id in f }
    }

    override suspend fun search(query: String): List<Athlete> {
        delay(Random.nextLong(250, 500))
        val q = query.trim().removePrefix("@")
        return others.filter { q.isBlank() || it.name.contains(q, true) || it.handle.contains(q, true) }
    }

    override suspend fun athlete(id: String): Athlete? {
        delay(Random.nextLong(300, 600))
        return if (id == ME_ID) me() else others.firstOrNull { it.id == id }
    }

    override val following: Flow<Set<String>> = settings.settings.map { it.following }

    override suspend fun setFollowing(id: String, follow: Boolean) {
        delay(250)
        settings.update { s -> s.copy(following = if (follow) s.following + id else s.following - id) }
    }

    override suspend fun syncPending(): Int {
        val pending = sessions.pending()
        sessions.markSynced(pending.map { it.id })
        return pending.size
    }

    companion object {
        const val ME_ID = "me"
    }
}

/** « il y a 2 h », « hier », « il y a 3 j »… */
fun relativeTime(epochMs: Long, now: Long = System.currentTimeMillis()): String {
    val min = (now - epochMs) / 60_000
    return when {
        min < 1 -> "à l'instant"
        min < 60 -> "il y a $min min"
        min < 24 * 60 -> "il y a ${min / 60} h"
        min < 48 * 60 -> "hier"
        else -> "il y a ${min / (24 * 60)} j"
    }
}
