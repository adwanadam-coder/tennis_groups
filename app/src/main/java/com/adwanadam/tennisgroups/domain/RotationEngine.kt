package com.adwanadam.tennisgroups.domain

import kotlin.math.abs

/** Continuous rotation engine. Previously played groups of four are avoided whenever possible. */
class RotationEngine {
    fun generate(players: List<Player>, courtCount: Int, levelsEnabled: Boolean, previousGroups: Set<String>, previousPartners: Set<String>): RotationResult {
        require(courtCount > 0)
        require(players.map { it.id }.distinct().size == players.size)
        val available = players.sortedWith(compareBy<Player> { it.matchesPlayed }.thenBy { it.id }).toMutableList()
        val matches = mutableListOf<DoublesMatch>()
        var exhausted = false
        repeat(courtCount) { courtIndex ->
            if (available.size < 4) return@repeat
            val candidate = chooseGroup(available, previousGroups, levelsEnabled)
            if (candidate == null) { exhausted = true; return@repeat }
            val (group, repeated) = candidate
            if (repeated) exhausted = true
            val teams = splitTeams(group, levelsEnabled, previousPartners)
            matches += DoublesMatch(courtIndex + 1, group, teams.first, teams.second, repeated)
            available.removeAll(group.toSet())
        }
        return RotationResult(matches, available, exhausted)
    }

    private fun chooseGroup(pool: List<Player>, history: Set<String>, levelsEnabled: Boolean): Pair<List<Player>, Boolean>? {
        var best: List<Player>? = null
        var bestScore = Double.POSITIVE_INFINITY
        var repeatedBest: List<Player>? = null
        var repeatedScore = Double.POSITIVE_INFINITY
        for (a in 0 until pool.size - 3) for (b in a + 1 until pool.size - 2)
            for (c in b + 1 until pool.size - 1) for (d in c + 1 until pool.size) {
                val group = listOf(pool[a], pool[b], pool[c], pool[d])
                val key = group.map { it.id }.sorted().joinToString("|")
                val score = group.sumOf { it.matchesPlayed } * 100.0 + if (levelsEnabled) levelSpread(group) * 2.0 else 0.0
                if (key !in history && score < bestScore) { best = group; bestScore = score }
                else if (key in history && score < repeatedScore) { repeatedBest = group; repeatedScore = score }
            }
        return when { best != null -> best to false; repeatedBest != null -> repeatedBest to true; else -> null }
    }

    private fun splitTeams(group: List<Player>, levelsEnabled: Boolean, previousPartners: Set<String>): Pair<List<Player>, List<Player>> {
        val partitions = listOf(
            listOf(listOf(group[0], group[1]), listOf(group[2], group[3])),
            listOf(listOf(group[0], group[2]), listOf(group[1], group[3])),
            listOf(listOf(group[0], group[3]), listOf(group[1], group[2]))
        )
        fun key(team: List<Player>) = team.map { it.id }.sorted().joinToString("|")
        fun score(partition: List<List<Player>>): Double {
            val balance = if (levelsEnabled && group.all { it.level != null }) abs(partition[0].sumOf { it.level!! } - partition[1].sumOf { it.level!! }) * 100.0 else 0.0
            val repeats = partition.count { key(it) in previousPartners }
            return balance + repeats * 10.0
        }
        val chosen = partitions.minBy { score(it) }
        return chosen[0] to chosen[1]
    }

    private fun levelSpread(group: List<Player>): Double {
        val levels = group.mapNotNull { it.level }
        return if (levels.size == 4) (levels.maxOrNull()!! - levels.minOrNull()!!).toDouble() else 0.0
    }
}
