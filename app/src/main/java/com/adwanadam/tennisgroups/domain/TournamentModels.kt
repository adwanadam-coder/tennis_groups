package com.adwanadam.tennisgroups.domain

data class Player(val id: String, val name: String, val level: Int? = null, val matchesPlayed: Int = 0)
data class DoublesMatch(val court: Int, val players: List<Player>, val teamA: List<Player>, val teamB: List<Player>, val repeatedGroup: Boolean = false) {
    val groupKey: String = players.map { it.id }.sorted().joinToString("|")
}
data class RotationResult(val matches: List<DoublesMatch>, val waiting: List<Player>, val hasExhaustedUniqueGroups: Boolean)
