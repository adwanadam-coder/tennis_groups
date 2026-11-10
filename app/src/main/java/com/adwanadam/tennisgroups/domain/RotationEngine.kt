package com.adwanadam.tennisgroups.domain

import kotlin.math.abs

/** Rotation engine for doubles (groups of four) and singles (pairs of two). */
class RotationEngine {
    fun generate(
        players: List<Player>,
        courtCount: Int,
        levelsEnabled: Boolean,
        previousGroups: Set<String>,
        previousPartners: Set<String>,
        singles: Boolean = false
    ): RotationResult {
        require(courtCount > 0)
        require(players.map { it.id }.distinct().size == players.size)

        val available = players.sortedWith(compareBy<Player> { it.matchesPlayed }.thenBy { it.id }).toMutableList()
        val matches = mutableListOf<DoublesMatch>()
        var exhausted = false
        val playersPerMatch = if (singles) 2 else 4

        repeat(courtCount) { courtIndex ->
            if (available.size < playersPerMatch) return@repeat
            if (singles) {
                val candidate = chooseSinglesPair(available, previousGroups, levelsEnabled)
                if (candidate == null) {
                    exhausted = true
                    return@repeat
                }
                val (pair, repeated) = candidate
                if (repeated) exhausted = true
                matches += DoublesMatch(courtIndex + 1, pair, listOf(pair[0]), listOf(pair[1]), repeated)
                available.removeAll(pair.toSet())
            } else {
                val candidate = chooseGroup(available, previousGroups, levelsEnabled)
                if (candidate == null) {
                    exhausted = true
                    return@repeat
                }
                val (group, repeated) = candidate
                if (repeated) exhausted = true
                val teams = splitTeams(group, levelsEnabled, previousPartners)
                matches += DoublesMatch(courtIndex + 1, group, teams.first, teams.second, repeated)
                available.removeAll(group.toSet())
            }
        }
        return RotationResult(matches, available, exhausted)
    }

    private fun comparePlayerLoads(first: List<Player>, second: List<Player>): Int {
        val firstLoads = first.map { it.matchesPlayed }.sortedDescending()
        val secondLoads = second.map { it.matchesPlayed }.sortedDescending()
        for (index in firstLoads.indices) {
            val comparison = firstLoads[index].compareTo(secondLoads[index])
            if (comparison != 0) return comparison
        }
        return 0
    }

    private fun compareSinglesPairs(first: List<Player>, second: List<Player>, levelsEnabled: Boolean): Int {
        val loadComparison = comparePlayerLoads(first, second)
        if (loadComparison != 0) return loadComparison
        if (levelsEnabled && first.all { it.level != null } && second.all { it.level != null }) {
            val firstGap = abs(first[0].level!! - first[1].level!!)
            val secondGap = abs(second[0].level!! - second[1].level!!)
            val levelComparison = firstGap.compareTo(secondGap)
            if (levelComparison != 0) return levelComparison
        }
        return first.map { it.id }.sorted().joinToString("|")
            .compareTo(second.map { it.id }.sorted().joinToString("|"))
    }

    private fun compareGroups(first: List<Player>, second: List<Player>, levelsEnabled: Boolean): Int {
        val loadComparison = comparePlayerLoads(first, second)
        if (loadComparison != 0) return loadComparison
        if (levelsEnabled) {
            val levelComparison = levelSpread(first).compareTo(levelSpread(second))
            if (levelComparison != 0) return levelComparison
        }
        return first.map { it.id }.sorted().joinToString("|")
            .compareTo(second.map { it.id }.sorted().joinToString("|"))
    }

    private fun chooseSinglesPair(pool: List<Player>, history: Set<String>, levelsEnabled: Boolean): Pair<List<Player>, Boolean>? {
        var best: List<Player>? = null
        var repeatedBest: List<Player>? = null
        for (a in 0 until pool.size - 1) for (b in a + 1 until pool.size) {
            val pair = listOf(pool[a], pool[b])
            val key = pair.map { it.id }.sorted().joinToString("|")
            if (key !in history) {
                if (best == null || compareSinglesPairs(pair, best!!, levelsEnabled) < 0) best = pair
            } else {
                if (repeatedBest == null || compareSinglesPairs(pair, repeatedBest!!, levelsEnabled) < 0) repeatedBest = pair
            }
        }
        return when {
            best != null -> best to false
            repeatedBest != null -> repeatedBest to true
            else -> null
        }
    }

    private fun chooseGroup(pool: List<Player>, history: Set<String>, levelsEnabled: Boolean): Pair<List<Player>, Boolean>? {
        var best: List<Player>? = null
        var repeatedBest: List<Player>? = null
        for (a in 0 until pool.size - 3) for (b in a + 1 until pool.size - 2)
            for (c in b + 1 until pool.size - 1) for (d in c + 1 until pool.size) {
                val group = listOf(pool[a], pool[b], pool[c], pool[d])
                val key = group.map { it.id }.sorted().joinToString("|")
                if (key !in history) {
                    if (best == null || compareGroups(group, best!!, levelsEnabled) < 0) best = group
                } else {
                    if (repeatedBest == null || compareGroups(group, repeatedBest!!, levelsEnabled) < 0) repeatedBest = group
                }
            }
        return when {
            best != null -> best to false
            repeatedBest != null -> repeatedBest to true
            else -> null
        }
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
        val values = group.mapNotNull { it.level }
        return if (values.size == 4) (values.maxOrNull()!! - values.minOrNull()!!).toDouble() else 0.0
    }
}
