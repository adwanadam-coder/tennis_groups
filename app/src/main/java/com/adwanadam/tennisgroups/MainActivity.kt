package com.adwanadam.tennisgroups

import android.os.Bundle
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalUriHandler
import com.adwanadam.tennisgroups.domain.DoublesMatch
import com.adwanadam.tennisgroups.domain.Player
import com.adwanadam.tennisgroups.domain.RotationEngine
import kotlinx.coroutines.delay
import kotlin.random.Random

private data class AppLanguage(val name: String, val flag: String)
private val languages = listOf(
    AppLanguage("Français", "🇫🇷"), AppLanguage("English", "🇬🇧"), AppLanguage("Español", "🇪🇸"),
    AppLanguage("Italiano", "🇮🇹"), AppLanguage("Português", "🇵🇹"), AppLanguage("Deutsch", "🇩🇪"),
    AppLanguage("中文（简体）", "🇨🇳"), AppLanguage("日本語", "🇯🇵"), AppLanguage("हिन्दी", "🇮🇳"),
    AppLanguage("Nederlands", "🇳🇱"), AppLanguage("Русский", "🇷🇺"), AppLanguage("Čeština", "🇨🇿"),
    AppLanguage("Српски", "🇷🇸")
)
private val levels = mapOf(1 to "Débutant", 2 to "Intermédiaire", 3 to "Avancé")
private data class RoundHistory(val number: Int, val matches: List<DoublesMatch>, val waiting: List<Player>, val scores: Map<Int, Pair<String, String>> = emptyMap())

private data class TournamentArchive(
    val id: Long,
    val players: List<Player>,
    val history: List<RoundHistory>,
    val ranking: List<PlayerRanking>,
    val startedAt: Long,
    val endedAt: Long,
    val format: String,
    val levelsEnabled: Boolean
)
@Suppress("DEPRECATION")
private fun vibrateThreeTimes(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    vibrator?.let {
        val pattern = longArrayOf(0, 150, 120, 150, 120, 150)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            it.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            it.vibrate(pattern, -1)
        }
    }
}

private data class PlayerRanking(val player: Player, val wins: Int, val pointDifference: Double, val matchesPlayed: Int)

private fun buildIndividualRanking(players: List<Player>, history: List<RoundHistory>): List<PlayerRanking> {
    val stats = players.associate { it.id to doubleArrayOf(0.0, 0.0, 0.0) }.toMutableMap()
    history.forEach { round ->
        round.matches.forEach { match ->
            val a = round.scores[match.court]?.first?.trim()?.replace(',', '.')?.toDoubleOrNull()
            val b = round.scores[match.court]?.second?.trim()?.replace(',', '.')?.toDoubleOrNull()
            (match.teamA + match.teamB).forEach { player ->
                stats.getOrPut(player.id) { doubleArrayOf(0.0, 0.0, 0.0) }[2] += 1.0
            }
            if (a != null && b != null) {
                match.teamA.forEach { player ->
                    val values = stats.getOrPut(player.id) { doubleArrayOf(0.0, 0.0, 0.0) }
                    values[1] += a - b
                    if (a > b) values[0] += 1.0
                }
                match.teamB.forEach { player ->
                    val values = stats.getOrPut(player.id) { doubleArrayOf(0.0, 0.0, 0.0) }
                    values[1] += b - a
                    if (b > a) values[0] += 1.0
                }
            }
        }
    }
    return players.map { player ->
        val values = stats[player.id] ?: doubleArrayOf(0.0, 0.0, 0.0)
        PlayerRanking(player, values[0].roundToInt(), values[1], values[2].roundToInt())
    }.sortedWith(compareByDescending<PlayerRanking> { it.wins }
        .thenByDescending { it.pointDifference }
        .thenBy { it.player.name.lowercase() })
}
private fun formatTournamentDate(timestamp: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

private fun csvCell(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""

private fun tournamentCsv(archive: TournamentArchive): String = buildString {
    appendLine("BILAN DU TOURNOI")
    appendLine("Format,${csvCell(archive.format)}")
    appendLine("Début,${csvCell(formatTournamentDate(archive.startedAt))}")
    appendLine("Fin,${csvCell(formatTournamentDate(archive.endedAt))}")
    appendLine()
    appendLine("CLASSEMENT FINAL")
    appendLine("Rang,Joueur,Victoires,Différence de points,Matchs joués")
    archive.ranking.forEachIndexed { index, item ->
        appendLine("${index + 1},${csvCell(item.player.name)},${item.wins},${item.pointDifference.roundToInt()},${item.matchesPlayed}")
    }
    appendLine()
    appendLine("HISTORIQUE DES MATCHS")
    appendLine("Tour,Terrain,Équipe/Joueur A,Score A,Équipe/Joueur B,Score B,Joueurs en attente")
    archive.history.forEach { round ->
        round.matches.forEach { match ->
            val score = round.scores[match.court]
            val waiting = round.waiting.joinToString(" / ") { it.name }
            appendLine("${round.number},${match.court},${csvCell(match.teamA.joinToString(" + ") { it.name })},${csvCell(score?.first.orEmpty())},${csvCell(match.teamB.joinToString(" + ") { it.name })},${csvCell(score?.second.orEmpty())},${csvCell(waiting)}")
        }
    }
}


private fun playerArrayToJson(players: List<Player>): org.json.JSONArray =
    org.json.JSONArray().apply {
        players.forEach { player ->
            put(org.json.JSONObject().apply {
                put("id", player.id)
                put("name", player.name)
                put("level", player.level ?: org.json.JSONObject.NULL)
                put("matchesPlayed", player.matchesPlayed)
            })
        }
    }

private fun playerArrayFromJson(array: org.json.JSONArray?): List<Player> {
    if (array == null) return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        runCatching {
            val item = array.getJSONObject(index)
            Player(
                id = item.optString("id"),
                name = item.optString("name"),
                level = if (item.isNull("level")) null else item.optInt("level"),
                matchesPlayed = item.optInt("matchesPlayed")
            )
        }.getOrNull()
    }
}

private fun saveTournamentArchives(context: Context, archives: List<TournamentArchive>) {
    val root = org.json.JSONArray()
    archives.forEach { archive ->
        val rounds = org.json.JSONArray()
        archive.history.forEach { round ->
            val matches = org.json.JSONArray()
            round.matches.forEach { match ->
                matches.put(org.json.JSONObject().apply {
                    put("court", match.court)
                    put("repeatedGroup", match.repeatedGroup)
                    put("players", playerArrayToJson(match.players))
                    put("teamA", playerArrayToJson(match.teamA))
                    put("teamB", playerArrayToJson(match.teamB))
                })
            }
            val scores = org.json.JSONObject()
            round.scores.forEach { (court, score) ->
                scores.put(court.toString(), org.json.JSONArray().put(score.first).put(score.second))
            }
            rounds.put(org.json.JSONObject().apply {
                put("number", round.number)
                put("matches", matches)
                put("waiting", playerArrayToJson(round.waiting))
                put("scores", scores)
            })
        }
        root.put(org.json.JSONObject().apply {
            put("id", archive.id)
            put("players", playerArrayToJson(archive.players))
            put("history", rounds)
            put("startedAt", archive.startedAt)
            put("endedAt", archive.endedAt)
            put("format", archive.format)
            put("levelsEnabled", archive.levelsEnabled)
        })
    }
    context.getSharedPreferences("tournament_archives", Context.MODE_PRIVATE)
        .edit().putString("archives", root.toString()).apply()
}

private fun loadTournamentArchives(context: Context): List<TournamentArchive> {
    val raw = context.getSharedPreferences("tournament_archives", Context.MODE_PRIVATE)
        .getString("archives", "[]") ?: "[]"
    return runCatching {
        val root = org.json.JSONArray(raw)
        (0 until root.length()).mapNotNull { archiveIndex ->
            runCatching {
                val item = root.getJSONObject(archiveIndex)
                val snapshotPlayers = playerArrayFromJson(item.optJSONArray("players"))
                val roundsJson = item.optJSONArray("history") ?: org.json.JSONArray()
                val snapshotHistory = (0 until roundsJson.length()).mapNotNull { roundIndex ->
                    runCatching {
                        val roundJson = roundsJson.getJSONObject(roundIndex)
                        val matchesJson = roundJson.optJSONArray("matches") ?: org.json.JSONArray()
                        val roundMatches = (0 until matchesJson.length()).mapNotNull { matchIndex ->
                            runCatching {
                                val matchJson = matchesJson.getJSONObject(matchIndex)
                                DoublesMatch(
                                    court = matchJson.optInt("court"),
                                    players = playerArrayFromJson(matchJson.optJSONArray("players")),
                                    teamA = playerArrayFromJson(matchJson.optJSONArray("teamA")),
                                    teamB = playerArrayFromJson(matchJson.optJSONArray("teamB")),
                                    repeatedGroup = matchJson.optBoolean("repeatedGroup")
                                )
                            }.getOrNull()
                        }
                        val scoreMap = mutableMapOf<Int, Pair<String, String>>()
                        val scoresJson = roundJson.optJSONObject("scores") ?: org.json.JSONObject()
                        val scoreKeys = scoresJson.keys()
                        while (scoreKeys.hasNext()) {
                            val key = scoreKeys.next()
                            val values = scoresJson.optJSONArray(key) ?: continue
                            if (values.length() >= 2) scoreMap[key.toIntOrNull() ?: continue] =
                                values.optString(0) to values.optString(1)
                        }
                        RoundHistory(
                            number = roundJson.optInt("number"),
                            matches = roundMatches,
                            waiting = playerArrayFromJson(roundJson.optJSONArray("waiting")),
                            scores = scoreMap
                        )
                    }.getOrNull()
                }
                val endedAt = item.optLong("endedAt")
                TournamentArchive(
                    id = item.optLong("id", endedAt),
                    players = snapshotPlayers,
                    history = snapshotHistory,
                    ranking = buildIndividualRanking(snapshotPlayers, snapshotHistory),
                    startedAt = item.optLong("startedAt", endedAt),
                    endedAt = endedAt,
                    format = item.optString("format", "Doubles"),
                    levelsEnabled = item.optBoolean("levelsEnabled")
                )
            }.getOrNull()
        }
    }.getOrElse { emptyList() }
}

private fun shareTournamentReport(context: Context, archive: TournamentArchive, format: String) {
    val reportsDir = File(context.cacheDir, "tournament_reports").apply { mkdirs() }
    val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date(archive.endedAt))
    val file = if (format == "CSV") File(reportsDir, "tournoi_${stamp}.csv") else File(reportsDir, "tournoi_${stamp}.pdf")
    if (format == "CSV") {
        file.writeText("\uFEFF" + tournamentCsv(archive), Charsets.UTF_8)
    } else {
        val document = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; color = android.graphics.Color.BLACK }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 18f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = android.graphics.Color.BLACK }
        val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 14f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = android.graphics.Color.BLACK }
        val lines = mutableListOf<Pair<String, Int>>()
        lines.add("BILAN DU TOURNOI" to 2)
        lines.add("Format : ${archive.format}" to 0)
        lines.add("Début : ${formatTournamentDate(archive.startedAt)}" to 0)
        lines.add("Fin : ${formatTournamentDate(archive.endedAt)}" to 0)
        lines.add("" to 0)
        lines.add("CLASSEMENT FINAL" to 1)
        archive.ranking.forEachIndexed { index, item ->
            lines.add("${index + 1}. ${item.player.name} — ${item.wins} victoire(s) — ${if (item.pointDifference > 0) "+" else ""}${item.pointDifference.roundToInt()} pts — ${item.matchesPlayed} match(s)" to 0)
        }
        lines.add("" to 0)
        lines.add("HISTORIQUE DES MATCHS" to 1)
        archive.history.forEach { round ->
            lines.add("TOUR ${round.number}" to 1)
            round.matches.forEach { match ->
                val score = round.scores[match.court]
                val scoreText = if (score == null) "Score non renseigné" else "${score.first.ifBlank { "–" }} - ${score.second.ifBlank { "–" }}"
                lines.add("Terrain ${match.court} : ${match.teamA.joinToString(" + ") { it.name }} / ${match.teamB.joinToString(" + ") { it.name }} ($scoreText)" to 0)
            }
            if (round.waiting.isNotEmpty()) lines.add("En attente : ${round.waiting.joinToString(", ") { it.name }}" to 0)
        }
        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas
        var y = 48f
        lines.forEach { (line, kind) ->
            val chosenPaint = when (kind) { 2 -> titlePaint; 1 -> sectionPaint; else -> bodyPaint }
            if (y > pageHeight - 48f) {
                document.finishPage(page)
                pageNumber += 1
                page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                canvas = page.canvas
                y = 48f
            }
            if (line.isNotEmpty()) {
                val maxWidth = pageWidth - 72f
                var remainingText = line
                while (remainingText.isNotEmpty()) {
                    var count = remainingText.length
                    while (count > 1 && chosenPaint.measureText(remainingText.substring(0, count)) > maxWidth) count--
                    canvas.drawText(remainingText.substring(0, count), 36f, y, chosenPaint)
                    remainingText = remainingText.substring(count)
                    y += if (kind == 2) 30f else if (kind == 1) 23f else 18f
                    if (y > pageHeight - 48f && remainingText.isNotEmpty()) {
                        document.finishPage(page)
                        pageNumber += 1
                        page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                        canvas = page.canvas
                        y = 48f
                    }
                }
            } else y += 10f
        }
        document.finishPage(page)
        file.outputStream().use { document.writeTo(it) }
        document.close()
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val mimeType = if (format == "CSV") "text/csv" else "application/pdf"
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "Bilan du tournoi de tennis")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(sendIntent, "Partager le bilan du tournoi"))
}


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent { TennisGroupsApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TennisGroupsApp() {
    val players = remember { mutableStateListOf<Player>() }
    val previousGroups = remember { mutableStateListOf<String>() }
    val previousPartners = remember { mutableStateListOf<String>() }
    val history = remember { mutableStateListOf<RoundHistory>() }
    var entry by remember { mutableStateOf("") }
    var levelEnabled by remember { mutableStateOf(false) }
    var courts by remember { mutableIntStateOf(2) }
    var duration by remember { mutableIntStateOf(20) }
    var scoreEntryEnabled by remember { mutableStateOf(false) }
    var scoreDialogOpen by remember { mutableStateOf(false) }
    var scoreDialogShown by remember { mutableStateOf(false) }
    val scoreInputs = remember { mutableStateMapOf<Int, Pair<String, String>>() }
    var language by remember { mutableStateOf("Français") }
    var menuOpen by remember { mutableStateOf(false) }
    var languageOpen by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var page by remember { mutableStateOf("players") }
    var round by remember { mutableIntStateOf(0) }
    var matches by remember { mutableStateOf(emptyList<DoublesMatch>()) }
    var waiting by remember { mutableStateOf(emptyList<Player>()) }
    var warning by remember { mutableStateOf(false) }
    var remaining by remember { mutableIntStateOf(1200) }
    var running by remember { mutableStateOf(false) }
    var timerEndHandled by remember { mutableStateOf(false) }
    var finishDialogOpen by remember { mutableStateOf(false) }
    var tournamentFinished by remember { mutableStateOf(false) }
    val tournamentArchives = remember { mutableStateListOf<TournamentArchive>().apply { addAll(loadTournamentArchives(context)) } }
    var tournamentStartedAt by remember { mutableLongStateOf(0L) }
    var tournamentResetDialogOpen by remember { mutableStateOf(false) }
    var archiveToDelete by remember { mutableStateOf<TournamentArchive?>(null) }
    var editing by remember { mutableStateOf<Player?>(null) }
    var tournamentFormat by remember { mutableStateOf("Doubles") }

    fun generateRound() {
        if (round == 0) tournamentStartedAt = System.currentTimeMillis()
        val result = RotationEngine().generate(players.toList(), courts, levelEnabled, previousGroups.toSet(), previousPartners.toSet(), tournamentFormat == "Simples")
        matches = result.matches
        waiting = result.waiting
        warning = result.hasExhaustedUniqueGroups
        matches.forEach { match ->
            if (!match.repeatedGroup) previousGroups.add(match.groupKey)
            listOf(match.teamA, match.teamB).forEach { team -> previousPartners.add(team.map { it.id }.sorted().joinToString("|")) }
            match.players.forEach { p ->
                val i = players.indexOfFirst { it.id == p.id }
                if (i >= 0) players[i] = p.copy(matchesPlayed = p.matchesPlayed + 1)
            }
        }
        round += 1
        remaining = duration * 60
        running = false
        timerEndHandled = false
        tournamentFinished = false
        scoreDialogOpen = false
        scoreDialogShown = false
        scoreInputs.clear()
        history.add(RoundHistory(round, matches, waiting))
        page = "matches"
    }

    MaterialTheme(colorScheme = lightColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFF176447),
        onPrimary = androidx.compose.ui.graphics.Color.White,
        primaryContainer = androidx.compose.ui.graphics.Color(0xFFD5EBDD),
        onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF123D2B),
        secondary = androidx.compose.ui.graphics.Color(0xFF527568),
        background = androidx.compose.ui.graphics.Color(0xFFF5F7F4),
        surfaceVariant = androidx.compose.ui.graphics.Color(0xFFE8EEE9)
    )) {
        if (languageOpen) {
            AlertDialog(
                onDismissRequest = { languageOpen = false },
                title = { Text("Langue de l’application", fontWeight = FontWeight.Bold) },
                text = {
                    LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(languages) { option ->
                            val selected = language == option.name
                            Surface(
                                onClick = { language = option.name; languageOpen = false },
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(option.flag, style = MaterialTheme.typography.titleLarge)
                                    Spacer(Modifier.width(12.dp))
                                    Text(option.name, modifier = Modifier.weight(1f), fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                                    if (selected) Icon(Icons.Default.Check, contentDescription = "Sélectionnée", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { languageOpen = false }) { Text("Fermer") } }
            )
        }
        LaunchedEffect(running) {
            while (running && remaining > 0) {
                delay(1000)
                remaining -= 1
            }
            if (remaining == 0 && !timerEndHandled) {
                timerEndHandled = true
                val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 75)
                repeat(3) { beepIndex ->
                    tone.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
                    if (beepIndex < 2) delay(300)
                }
                vibrateThreeTimes(context)
                delay(180)
                tone.release()
                running = false
                if (scoreEntryEnabled && matches.isNotEmpty() && !scoreDialogShown) {
                    scoreDialogShown = true
                    scoreDialogOpen = true
                }
            }
        }

        if (finishDialogOpen) {
            AlertDialog(
                onDismissRequest = { finishDialogOpen = false },
                title = { Text("Terminer le tournoi ?", fontWeight = FontWeight.Bold) },
                text = { Text("Le tournoi sera clôturé et le classement individuel sera disponible dans l’historique.") },
                confirmButton = {
                    TextButton(onClick = {
                        finishDialogOpen = false
                        running = false
                        scoreDialogOpen = false
                        val endedAt = System.currentTimeMillis()
                        val startedAt = if (tournamentStartedAt > 0L) tournamentStartedAt else endedAt
                        val snapshotPlayers = players.toList()
                        val snapshotHistory = history.toList()
                        tournamentArchives.add(
                            TournamentArchive(
                                id = endedAt,
                                players = snapshotPlayers,
                                history = snapshotHistory,
                                ranking = buildIndividualRanking(snapshotPlayers, snapshotHistory),
                                startedAt = startedAt,
                                endedAt = endedAt,
                                format = tournamentFormat,
                                levelsEnabled = levelEnabled
                            )
                        )
                        saveTournamentArchives(context, tournamentArchives)
                        tournamentFinished = true
                        page = "history"
                    }) { Text("Terminer") }
                },
                dismissButton = { TextButton(onClick = { finishDialogOpen = false }) { Text("Annuler") } }
            )
        }

        if (tournamentResetDialogOpen) {
            AlertDialog(
                onDismissRequest = { tournamentResetDialogOpen = false },
                title = { Text("Nouveau tournoi ?", fontWeight = FontWeight.Bold) },
                text = { Text("Le classement et l'historique du tournoi terminé resteront archivés. Les tours et les résultats actifs seront remis à zéro, en conservant les participants et les paramètres.") },
                confirmButton = {
                    TextButton(onClick = {
                        tournamentResetDialogOpen = false
                        round = 0
                        matches = emptyList()
                        waiting = emptyList()
                        warning = false
                        history.clear()
                        previousGroups.clear()
                        previousPartners.clear()
                        players.indices.forEach { index -> players[index] = players[index].copy(matchesPlayed = 0) }
                        scoreInputs.clear()
                        remaining = duration * 60
                        running = false
                        timerEndHandled = false
                        scoreDialogOpen = false
                        scoreDialogShown = false
                        tournamentFinished = false
                        tournamentStartedAt = 0L
                        page = "matches"
                    }) { Text("Nouveau tournoi") }
                },
                dismissButton = { TextButton(onClick = { tournamentResetDialogOpen = false }) { Text("Plus tard") } }
            )
        }

        archiveToDelete?.let { archive ->
            AlertDialog(
                onDismissRequest = { archiveToDelete = null },
                title = { Text("Supprimer cette archive ?", fontWeight = FontWeight.Bold) },
                text = { Text("Le classement et l'historique de ce tournoi seront supprimés définitivement de l'application.") },
                confirmButton = {
                    TextButton(onClick = {
                        tournamentArchives.removeAll { it.id == archive.id }
                        saveTournamentArchives(context, tournamentArchives)
                        archiveToDelete = null
                    }) { Text("Supprimer") }
                },
                dismissButton = { TextButton(onClick = { archiveToDelete = null }) { Text("Annuler") } }
            )
        }

        if (scoreDialogOpen) {
            MatchScoresDialog(
                matches = matches,
                tournamentFormat = tournamentFormat,
                initialScores = scoreInputs.toMap(),
                onSave = { enteredScores ->
                    scoreInputs.clear()
                    scoreInputs.putAll(enteredScores)
                    if (history.isNotEmpty()) history[history.lastIndex] = history.last().copy(scores = enteredScores)
                    scoreDialogOpen = false
                },
                onSkip = { scoreDialogOpen = false }
            )
        }

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = {
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val adaptiveTitleSize = (maxWidth.value * 0.09f).coerceIn(11.5f, 18f)
                            Text(
                                "Tennis Tournament",
                                fontSize = adaptiveTitleSize.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    },
                    navigationIcon = {
                        Box {
                            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.Menu, contentDescription = "Ouvrir le menu") }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(text = { Text("Joueurs") }, leadingIcon = { Icon(Icons.Default.People, null) }, onClick = { page = "players"; menuOpen = false })
                                DropdownMenuItem(text = { Text("Matchs") }, leadingIcon = { Icon(Icons.Default.SportsTennis, null) }, onClick = { if (tournamentFinished) tournamentResetDialogOpen = true else page = "matches"; menuOpen = false })
                                DropdownMenuItem(text = { Text("Historique") }, leadingIcon = { Icon(Icons.Default.History, null) }, onClick = { page = "history"; menuOpen = false })
                                HorizontalDivider()
                                DropdownMenuItem(text = { Text("Payer moi un café") }, leadingIcon = { Icon(Icons.Default.Coffee, null) }, onClick = { menuOpen = false; uriHandler.openUri("https://ko-fi.com/ada7875") })
                                DropdownMenuItem(text = { Text("Langue : " + (languages.firstOrNull { it.name == language }?.flag ?: "🌐") + " " + language) }, leadingIcon = { Icon(Icons.Default.Language, null) }, onClick = { languageOpen = true; menuOpen = false })
                                DropdownMenuItem(text = { Text("À propos") }, leadingIcon = { Icon(Icons.Default.Info, null) }, onClick = { page = "about"; menuOpen = false })
                            }
                        }
                    },
                    actions = {
                        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.padding(end = 12.dp)) {
                            Text(quantityLabel(players.size, "joueur") , modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar(windowInsets = WindowInsets.navigationBars) {
                    NavigationBarItem(selected = page == "players" || page == "settings", onClick = { page = "players" }, icon = { Icon(Icons.Default.People, null) }, label = { Text("Joueurs") })
                    NavigationBarItem(selected = page == "matches", onClick = { if (tournamentFinished) tournamentResetDialogOpen = true else page = "matches" }, icon = { Icon(Icons.Default.SportsTennis, null) }, label = { Text("Matchs") })
                    NavigationBarItem(selected = page == "history", onClick = { page = "history" }, icon = { Icon(Icons.Default.History, null) }, label = { Text("Historique") })
                }
            }
        ) { padding ->
            when (page) {
                "matches" -> MatchesPage(round, matches, waiting, warning, remaining, running, duration, levelEnabled, players.size, tournamentFormat,
                    { running = !running }, {
                        running = false
                        remaining = duration * 60
                        timerEndHandled = false
                        scoreDialogOpen = false
                        scoreDialogShown = false
                    }, { if (players.size >= (if (tournamentFormat == "Simples") 2 else 4)) generateRound() },
                    { finishDialogOpen = true }, scoreInputs.toMap(), Modifier.padding(padding))
                "history" -> HistoryPage(
                    history.toList(), players.toList(), levelEnabled, tournamentFinished,
                    tournamentArchives.toList(),
                    onExport = { archive, format -> shareTournamentReport(context, archive, format) },
                    onDeleteArchive = { archiveToDelete = it },
                    modifier = Modifier.padding(padding)
                )
                "about" -> AboutPage(Modifier.padding(padding))
                else -> PlayersPage(
                    players, entry, { entry = it }, tournamentFormat, { tournamentFormat = it }, levelEnabled, { levelEnabled = it },
                    courts, { courts = it.coerceIn(1, 20) }, duration, { duration = it.coerceIn(1, 99); remaining = duration * 60 }, scoreEntryEnabled, { scoreEntryEnabled = it },
                    { val n = entry.trim(); if (n.isNotEmpty() && players.none { it.name.equals(n, true) }) { players.add(Player(Random.nextLong().toString(), n, if (levelEnabled) 2 else null)); entry = "" } },
                    { editing = it }, { players.remove(it) }, { generateRound() }, Modifier.padding(padding)
                )
            }
        }
        editing?.let { player ->
            EditPlayerDialog(player, levelEnabled, { editing = null }) { name, level ->
                val i = players.indexOfFirst { it.id == player.id }
                if (i >= 0) players[i] = players[i].copy(name = name.trim(), level = if (levelEnabled) level else null)
                editing = null
            }
        }
    }
}

@Composable
private fun PlayersPage(
    players: List<Player>, entry: String, onEntry: (String) -> Unit,
    tournamentFormat: String, onTournamentFormat: (String) -> Unit,
    levelsEnabled: Boolean, onLevels: (Boolean) -> Unit,
    courts: Int, onCourts: (Int) -> Unit, duration: Int, onDuration: (Int) -> Unit,
    scoreEntryEnabled: Boolean, onScoreEntryEnabled: (Boolean) -> Unit,
    onAdd: () -> Unit, onEdit: (Player) -> Unit, onRemove: (Player) -> Unit, onGenerate: () -> Unit, modifier: Modifier
) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Format du tournoi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(selected = tournamentFormat == "Doubles", onClick = { onTournamentFormat("Doubles") }, label = { Text("Doubles") }, leadingIcon = { Icon(Icons.Default.Groups, contentDescription = null) }, modifier = Modifier.weight(1f))
                    FilterChip(selected = tournamentFormat == "Simples", onClick = { onTournamentFormat("Simples") }, label = { Text("Simples") }, leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }, modifier = Modifier.weight(1f))
                }
            }
        }
        item {
            Text("Les joueurs", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(entry, onEntry, Modifier.weight(1f), label = { Text("Prénom ou numéro") }, singleLine = true, shape = RoundedCornerShape(16.dp))
                FilledIconButton(onClick = onAdd, enabled = entry.isNotBlank(), modifier = Modifier.size(54.dp)) { Icon(Icons.Default.Add, contentDescription = "Ajouter un joueur") }
            }
        }
        item {
            Card(shape = RoundedCornerShape(20.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Niveau des joueurs",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Switch(levelsEnabled, onLevels)
                }
            }
        }
        if (players.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Participants inscrits", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text(players.size.toString(), color = MaterialTheme.colorScheme.primary)
                }
            }
            items(players, key = { it.id }) { player ->
                Card(shape = RoundedCornerShape(18.dp)) {
                    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                            Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(10.dp))
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(player.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                if (levelsEnabled) {
                                    val levelColor = when (player.level) {
                                        1 -> Color(0xFF287A50)
                                        2 -> Color(0xFFB36A13)
                                        3 -> Color(0xFF3567A8)
                                        else -> MaterialTheme.colorScheme.outline
                                    }
                                    Surface(shape = RoundedCornerShape(50), color = levelColor.copy(alpha = 0.13f)) {
                                        Text(levels[player.level] ?: "À définir", modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = levelColor, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                        IconButton(onClick = { onEdit(player) }) { Icon(Icons.Default.Edit, contentDescription = "Modifier " + player.name) }
                        IconButton(onClick = { onRemove(player) }) { Icon(Icons.Default.Close, contentDescription = "Supprimer " + player.name) }
                    }
                }
            }
        } else {
            item {
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.GroupAdd, null)
                        Spacer(Modifier.width(12.dp))
                        Text(if (tournamentFormat == "Simples") "Ajoutez au moins deux joueurs pour créer votre premier tour." else "Ajoutez au moins quatre joueurs pour créer votre premier tour.")
                    }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Organisation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        OrganizationInput("Terrains disponibles", courts, "terrain", { TennisCourtIcon() }, Modifier.weight(1f)) { onCourts(it.coerceIn(1, 20)) }
                        OrganizationInput("Durée du match", duration, "min", { Icon(Icons.Default.AccessTime, null, tint = MaterialTheme.colorScheme.primary) }, Modifier.weight(1f)) { onDuration(it.coerceIn(1, 99)) }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Renseignement des scores", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text("Saisir les résultats à la fin de chaque tour", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = scoreEntryEnabled, onCheckedChange = onScoreEntryEnabled)
                    }
                }
            }
        }
        item {
            Button(onGenerate, enabled = players.size >= (if (tournamentFormat == "Simples") 2 else 4), modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp)) {
                Icon(Icons.Default.Shuffle, null)
                Spacer(Modifier.width(8.dp))
                Text("Générer les matchs", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

private fun quantityLabel(count: Int, singular: String): String =
    "$count ${quantityUnit(count, singular)}"

private fun quantityUnit(count: Int, singular: String): String =
    if (count == 1) singular else singular + "s"

@Composable
private fun LevelSelector(selected: Int, onSelected: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        levels.forEach { (value, label) ->
            FilterChip(selected == value, { onSelected(value) }, label = { Text(label, maxLines = 1) }, modifier = Modifier.weight(1f))
        }
    }
}


@Composable
private fun TennisCourtIcon() {
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(24.dp)) {
        val stroke = Stroke(width = 1.7.dp.toPx())
        drawRect(color = color, style = stroke)
        drawLine(color, Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), stroke.width)
        drawLine(color, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), stroke.width)
        drawLine(color, Offset(size.width * 0.22f, 0f), Offset(size.width * 0.22f, size.height), stroke.width)
        drawLine(color, Offset(size.width * 0.78f, 0f), Offset(size.width * 0.78f, size.height), stroke.width)
    }
}

@Composable
private fun OrganizationInput(
    label: String,
    value: Int,
    unit: String,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onValueChange: (Int) -> Unit
) {
    var textValue by remember(label, value) { mutableStateOf(value.toString()) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            icon()
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 2)
        }
        OutlinedTextField(
            value = textValue,
            onValueChange = { raw ->
                val maxDigits = if (unit == "min") 2 else 3
                val digits = raw.filter(Char::isDigit).take(maxDigits)
                textValue = digits
                digits.toIntOrNull()?.let(onValueChange)
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            suffix = { Text(if (unit == "terrain") quantityUnit(value, "terrain") else unit, style = MaterialTheme.typography.labelSmall) }
        )
    }
}

@Composable
private fun SettingStepper(label: String, value: Int, unit: String, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        IconButton(onClick = { onChange(value - 1) }, enabled = value > min) { Icon(Icons.Default.Remove, contentDescription = "Diminuer " + label) }
        Text(value.toString() + " " + unit, modifier = Modifier.widthIn(min = 74.dp), fontWeight = FontWeight.Bold)
        IconButton(onClick = { onChange(value + 1) }, enabled = value < max) { Icon(Icons.Default.Add, contentDescription = "Augmenter " + label) }
    }
}

@Composable
private fun EditPlayerDialog(player: Player, levelsEnabled: Boolean, onDismiss: () -> Unit, onSave: (String, Int) -> Unit) {
    var name by remember(player.id) { mutableStateOf(player.name) }
    var level by remember(player.id) { mutableIntStateOf(player.level ?: 2) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modifier le joueur") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Prénom ou numéro") }, singleLine = true)
                if (levelsEnabled) {
                    Text("Niveau individuel", style = MaterialTheme.typography.labelLarge)
                    LevelSelector(level, { level = it })
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, level) }, enabled = name.isNotBlank()) { Text("Enregistrer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
private fun MatchesPage(
    round: Int, matches: List<DoublesMatch>, waiting: List<Player>, warning: Boolean,
    remaining: Int, running: Boolean, duration: Int, levelsEnabled: Boolean, playerCount: Int, tournamentFormat: String,
    onToggle: () -> Unit, onReset: () -> Unit, onNext: () -> Unit, onFinishTournament: () -> Unit,
    scores: Map<Int, Pair<String, String>>, modifier: Modifier
) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (round == 0) "Matchs" else "Tour " + round, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (round > 0) {
                    Button(
                        onClick = onFinishTournament,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Fin du tournoi", fontSize = MaterialTheme.typography.labelMedium.fontSize, maxLines = 1, softWrap = false)
                    }
                }
            }
            Text(if (round == 0) "Les rencontres apparaîtront ici." else quantityLabel(matches.size, "terrain") + " en jeu", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (round == 0) {
            item {
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.SportsTennis, null, modifier = Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("Aucun match généré", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(if (tournamentFormat == "Simples") "Ajoutez au moins deux joueurs, puis générez les matchs depuis l'écran Joueurs." else "Ajoutez au moins quatre joueurs, puis générez les matchs depuis l'écran Joueurs.")
                    }
                }
            }
        } else {
            item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("CHRONOMÈTRE DU TOUR", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Text("%02d:%02d".format(remaining / 60, remaining % 60), fontSize = 48.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("Durée prévue : " + quantityLabel(duration, "minute"))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onToggle) {
                                Icon(if (running) Icons.Default.Pause else Icons.Default.PlayArrow, null)
                                Spacer(Modifier.width(6.dp))
                                Text(if (running) "Pause" else "Démarrer")
                            }
                            OutlinedButton(onReset) {
                                Icon(Icons.Default.Refresh, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Réinitialiser", fontSize = 10.sp, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }
            }
            if (warning) item { Text("Certaines combinaisons inédites sont épuisées. Les groupes répétés sont signalés.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            items(matches, key = { it.court }) { match ->
                Card(shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Terrain " + match.court, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer) {
                                Text("En jeu", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                            TeamPlayerColumn(if (tournamentFormat == "Simples") "JOUEUR A" else "ÉQUIPE A", match.teamA, Modifier.weight(1f), levelsEnabled)
                            Text("VS", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                            TeamPlayerColumn(if (tournamentFormat == "Simples") "JOUEUR B" else "ÉQUIPE B", match.teamB, Modifier.weight(1f), levelsEnabled)
                        }
                        if (match.repeatedGroup) Text("Groupe déjà rencontré", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                        scores[match.court]?.let { score ->
                            Text("Score : ${score.first.ifBlank { "–" }}  –  ${score.second.ifBlank { "–" }}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            if (waiting.isNotEmpty()) {
                item {
                    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.HourglassEmpty, null)
                            Spacer(Modifier.width(10.dp))
                            Text(quantityLabel(waiting.size, "joueur") + " en attente : " + waiting.joinToString(", ") { it.name })
                        }
                    }
                }
            }
            item {
                Button(onNext, enabled = playerCount >= (if (tournamentFormat == "Simples") 2 else 4), modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(18.dp)) {
                    Icon(Icons.Default.Autorenew, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Générer le tour suivant")
                }
            }
        }
    }
}

@Composable
private fun MatchScoresDialog(
    matches: List<DoublesMatch>,
    tournamentFormat: String,
    initialScores: Map<Int, Pair<String, String>>,
    onSave: (Map<Int, Pair<String, String>>) -> Unit,
    onSkip: () -> Unit
) {
    val enteredScores = remember(matches) {
        mutableStateMapOf<Int, Pair<String, String>>().apply { putAll(initialScores) }
    }
    AlertDialog(
        onDismissRequest = onSkip,
        title = { Text("Résultats du tour", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(matches, key = { it.court }) { match ->
                    var scoreA by remember(match.court) { mutableStateOf(enteredScores[match.court]?.first.orEmpty()) }
                    var scoreB by remember(match.court) { mutableStateOf(enteredScores[match.court]?.second.orEmpty()) }
                    Card(shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Terrain ${match.court}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text((if (tournamentFormat == "Simples") "Joueur A : " else "Équipe A : ") + match.teamA.joinToString(", ") { it.name }, style = MaterialTheme.typography.bodySmall)
                            OutlinedTextField(
                                value = scoreA,
                                onValueChange = { scoreA = it; enteredScores[match.court] = scoreA to scoreB },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(if (tournamentFormat == "Simples") "Score joueur A" else "Score équipe A") },
                                singleLine = true
                            )
                            Text((if (tournamentFormat == "Simples") "Joueur B : " else "Équipe B : ") + match.teamB.joinToString(", ") { it.name }, style = MaterialTheme.typography.bodySmall)
                            OutlinedTextField(
                                value = scoreB,
                                onValueChange = { scoreB = it; enteredScores[match.court] = scoreA to scoreB },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(if (tournamentFormat == "Simples") "Score joueur B" else "Score équipe B") },
                                singleLine = true
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(enteredScores.toMap()) }) { Text("Enregistrer") } },
        dismissButton = { TextButton(onClick = onSkip) { Text("Ignorer") } }
    )
}

@Composable
private fun TeamPlayerColumn(title: String, players: List<Player>, modifier: Modifier, levelsEnabled: Boolean) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        players.forEach { player ->
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(player.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    if (levelsEnabled && player.level != null) Text(levels[player.level] ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
    }
}

@Composable
private fun SettingsPage(levelsEnabled: Boolean, onLevels: (Boolean) -> Unit, courts: Int, onCourts: (Int) -> Unit, duration: Int, onDuration: (Int) -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Options du tournoi", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Card(shape = RoundedCornerShape(20.dp)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Niveau des joueurs", fontWeight = FontWeight.SemiBold)
                    Text("Débutant, Intermédiaire ou Avancé.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(levelsEnabled, onLevels)
            }
        }
        Card(shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Organisation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    OrganizationInput("Terrains disponibles", courts, "terrain", { TennisCourtIcon() }, Modifier.weight(1f)) { onCourts(it.coerceIn(1, 20)) }
                    OrganizationInput("Durée du match", duration, "min", { Icon(Icons.Default.AccessTime, null, tint = MaterialTheme.colorScheme.primary) }, Modifier.weight(1f)) { onDuration(it.coerceIn(5, 180)) }
                }
            }
        }
    }
}

@Composable
private fun HistoryPage(
    history: List<RoundHistory>,
    players: List<Player>,
    levelsEnabled: Boolean,
    tournamentFinished: Boolean,
    archives: List<TournamentArchive>,
    onExport: (TournamentArchive, String) -> Unit,
    onDeleteArchive: (TournamentArchive) -> Unit,
    modifier: Modifier
) {
    val ranking = remember(history, players) { buildIndividualRanking(players, history) }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Historique", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Retrouvez les rencontres et les partenaires de chaque tour.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (tournamentFinished) {
            item {
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Classement individuel final", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text("Classement par victoires, puis différence de points lorsque les deux scores numériques sont renseignés.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        ranking.forEachIndexed { index, item ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("${index + 1}.", modifier = Modifier.width(30.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                Text(item.player.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                Text("${item.wins} V  •  ${if (item.pointDifference > 0) "+" else ""}${item.pointDifference.roundToInt()} pts", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                        val currentArchive = archives.maxByOrNull { it.endedAt }
                        if (currentArchive != null) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { onExport(currentArchive, "PDF") }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.PictureAsPdf, null)
                                    Spacer(Modifier.width(5.dp))
                                    Text("Exporter PDF")
                                }
                                OutlinedButton(onClick = { onExport(currentArchive, "CSV") }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.TableChart, null)
                                    Spacer(Modifier.width(5.dp))
                                    Text("Exporter CSV")
                                }
                            }
                        }
                    }
                }
            }
        }
        if (history.isEmpty() && !tournamentFinished) {
            item {
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.History, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("Aucun historique pour le moment", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Les tours générés pendant cette session apparaîtront ici.")
                    }
                }
            }
        } else {
            items(history.reversed(), key = { it.number }) { item ->
                Card(shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Tour " + item.number, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(quantityLabel(item.matches.size, "terrain"), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                        }
                        item.matches.forEach { match ->
                            Text("Terrain " + match.court, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                HistoryTeam(match.teamA, levelsEnabled, Modifier.weight(1f))
                                Text("VS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                HistoryTeam(match.teamB, levelsEnabled, Modifier.weight(1f))
                            }
                            if (match.repeatedGroup) Text("Groupe déjà rencontré", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            item.scores[match.court]?.let { score ->
                                Text("Score : ${score.first.ifBlank { "–" }}  –  ${score.second.ifBlank { "–" }}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        if (item.waiting.isNotEmpty()) Text(quantityLabel(item.waiting.size, "joueur") + " en attente : " + item.waiting.joinToString(", ") { it.name }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (archives.isNotEmpty()) {
            item {
                Text("Tournois précédents", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Les bilans archivés restent disponibles après la remise à zéro.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(archives.sortedByDescending { it.endedAt }, key = { it.id }) { archive ->
                Card(shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Tournoi du ${formatTournamentDate(archive.endedAt)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Début : ${formatTournamentDate(archive.startedAt)}", style = MaterialTheme.typography.bodySmall)
                        Text("Fin : ${formatTournamentDate(archive.endedAt)} • ${archive.history.size} tour(s) • ${archive.players.size} joueur(s)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Classement : " + archive.ranking.take(3).joinToString(" • ") { it.player.name }, style = MaterialTheme.typography.bodySmall)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onExport(archive, "PDF") }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.PictureAsPdf, null)
                                Spacer(Modifier.width(5.dp))
                                Text("PDF")
                            }
                            OutlinedButton(onClick = { onExport(archive, "CSV") }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.TableChart, null)
                                Spacer(Modifier.width(5.dp))
                                Text("CSV")
                            }
                            IconButton(onClick = { onDeleteArchive(archive) }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Supprimer l'archive", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryTeam(players: List<Player>, levelsEnabled: Boolean, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        players.forEach { player ->
            Text(player.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            if (levelsEnabled && player.level != null) Text(levels[player.level] ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AboutPage(modifier: Modifier) {
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("À propos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Tennis tournament aide les clubs à organiser des rencontres amicales en simple et en double.")
        Text("L'application renouvelle les adversaires en simple et les groupes en double, fait varier les partenaires et peut équilibrer les équipes grâce aux trois niveaux individuels facultatifs.")
        Text("Version 1.0.0", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
