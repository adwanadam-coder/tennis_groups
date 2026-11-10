package com.adwanadam.tennisgroups

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adwanadam.tennisgroups.domain.Player
import com.adwanadam.tennisgroups.domain.RotationEngine
import kotlinx.coroutines.delay
import kotlin.random.Random

private val supportedLanguages = listOf(
    "Français", "English", "Español", "Italiano", "Português", "Deutsch",
    "中文（简体）", "日本語", "हिन्दी", "Nederlands", "Русский", "Čeština", "Српски"
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TennisGroupsApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TennisGroupsApp() {
    val players = remember { mutableStateListOf<Player>() }
    val playedGroups = remember { mutableStateListOf<String>() }
    val playedPartners = remember { mutableStateListOf<String>() }
    var playerName by remember { mutableStateOf("") }
    var levelEnabled by remember { mutableStateOf(false) }
    var chosenLevel by remember { mutableIntStateOf(3) }
    var courtCount by remember { mutableIntStateOf(2) }
    var matchMinutes by remember { mutableIntStateOf(20) }
    var language by remember { mutableStateOf("Français") }
    var menuExpanded by remember { mutableStateOf(false) }
    var languageExpanded by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf("home") }
    var roundNumber by remember { mutableIntStateOf(0) }
    var matches by remember { mutableStateOf(emptyList<com.adwanadam.tennisgroups.domain.DoublesMatch>()) }
    var waiting by remember { mutableStateOf(emptyList<Player>()) }
    var warning by remember { mutableStateOf(false) }
    var secondsLeft by remember { mutableIntStateOf(20 * 60) }
    var timerRunning by remember { mutableStateOf(false) }

    MaterialTheme(colorScheme = lightColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFF176447),
        secondary = androidx.compose.ui.graphics.Color(0xFF77A88A),
        background = androidx.compose.ui.graphics.Color(0xFFF5F7F4)
    )) {
        LaunchedEffect(timerRunning) {
            while (timerRunning && secondsLeft > 0) {
                delay(1000)
                secondsLeft -= 1
            }
            if (secondsLeft == 0) timerRunning = false
        }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Tennis Groups", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        Box {
                            IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.Menu, contentDescription = "Menu") }
                            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                DropdownMenuItem(text = { Text("Participants") }, leadingIcon = { Icon(Icons.Default.People, null) }, onClick = { page = "home"; menuExpanded = false })
                                DropdownMenuItem(text = { Text("Options du tournoi") }, leadingIcon = { Icon(Icons.Default.Settings, null) }, onClick = { page = "settings"; menuExpanded = false })
                                DropdownMenuItem(text = { Text("Chronomètre") }, leadingIcon = { Icon(Icons.Default.Timer, null) }, onClick = { page = "timer"; menuExpanded = false })
                                DropdownMenuItem(text = { Text("Historique") }, leadingIcon = { Icon(Icons.Default.History, null) }, onClick = { page = "history"; menuExpanded = false })
                                HorizontalDivider()
                                DropdownMenuItem(text = { Text("Payez-moi un café · Ko-fi") }, leadingIcon = { Icon(Icons.Default.Coffee, null) }, onClick = { menuExpanded = false })
                                Box {
                                    DropdownMenuItem(text = { Text("Langue : " + language) }, leadingIcon = { Icon(Icons.Default.Language, null) }, onClick = { languageExpanded = true; menuExpanded = false })
                                    DropdownMenu(expanded = languageExpanded, onDismissRequest = { languageExpanded = false }) {
                                        supportedLanguages.forEach { item ->
                                            DropdownMenuItem(text = { Text(item) }, onClick = { language = item; languageExpanded = false })
                                        }
                                    }
                                }
                                DropdownMenuItem(text = { Text("À propos") }, leadingIcon = { Icon(Icons.Default.Info, null) }, onClick = { page = "about"; menuExpanded = false })
                            }
                        }
                    },
                    actions = { Text(players.size.toString() + " joueurs", modifier = Modifier.padding(end = 16.dp)) }
                )
            }
        ) { padding ->
            when (page) {
                "settings" -> SettingsPage(levelEnabled, { levelEnabled = it }, courtCount, { courtCount = it.coerceIn(1, 20) }, matchMinutes, {
                    matchMinutes = it.coerceIn(5, 180)
                    secondsLeft = matchMinutes * 60
                }, Modifier.padding(padding))
                "timer" -> TimerPage(secondsLeft, timerRunning, { timerRunning = it }, {
                    timerRunning = false
                    secondsLeft = matchMinutes * 60
                }, Modifier.padding(padding))
                "history" -> HistoryPage(roundNumber, playedGroups.size, Modifier.padding(padding))
                "about" -> AboutPage(Modifier.padding(padding))
                else -> HomePage(
                    players = players, name = playerName, onName = { playerName = it },
                    levelEnabled = levelEnabled, selectedLevel = chosenLevel, onLevel = { chosenLevel = it },
                    onAdd = {
                        val clean = playerName.trim()
                        if (clean.isNotEmpty() && players.none { it.name.equals(clean, true) }) {
                            players.add(Player(Random.nextLong().toString(), clean, if (levelEnabled) chosenLevel else null))
                            playerName = ""
                        }
                    },
                    onRemove = { players.remove(it) },
                    onGenerate = {
                        val result = RotationEngine().generate(players.toList(), courtCount, levelEnabled, playedGroups.toSet(), playedPartners.toSet())
                        matches = result.matches
                        waiting = result.waiting
                        warning = result.hasExhaustedUniqueGroups
                        matches.forEach { match ->
                            if (!match.repeatedGroup) playedGroups.add(match.groupKey)
                            listOf(match.teamA, match.teamB).forEach { team ->
                                playedPartners.add(team.map { it.id }.sorted().joinToString("|"))
                            }
                            match.players.forEach { p ->
                                val index = players.indexOfFirst { it.id == p.id }
                                if (index >= 0) players[index] = p.copy(matchesPlayed = p.matchesPlayed + 1)
                            }
                        }
                        roundNumber += 1
                        secondsLeft = matchMinutes * 60
                        timerRunning = false
                    },
                    matches = matches, waiting = waiting, warning = warning, roundNumber = roundNumber,
                    secondsLeft = secondsLeft, timerRunning = timerRunning,
                    onTimerToggle = { timerRunning = !timerRunning },
                    onTimerReset = { timerRunning = false; secondsLeft = matchMinutes * 60 },
                    matchMinutes = matchMinutes, modifier = Modifier.padding(padding)
                )
            }
        }
    }
}

@Composable
private fun HomePage(
    players: List<Player>, name: String, onName: (String) -> Unit,
    levelEnabled: Boolean, selectedLevel: Int, onLevel: (Int) -> Unit,
    onAdd: () -> Unit, onRemove: (Player) -> Unit, onGenerate: () -> Unit,
    matches: List<com.adwanadam.tennisgroups.domain.DoublesMatch>, waiting: List<Player>,
    warning: Boolean, roundNumber: Int, secondsLeft: Int, timerRunning: Boolean,
    onTimerToggle: () -> Unit, onTimerReset: () -> Unit, matchMinutes: Int, modifier: Modifier
) {
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("TOURNOI DE DOUBLE", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                    Text("Mélangez les équipes, profitez du jeu.", color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.height(12.dp))
                    Text(players.size.toString() + " participants", color = MaterialTheme.colorScheme.onPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            Text("Ajouter des participants", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = name, onValueChange = onName, modifier = Modifier.weight(1f), label = { Text("Prénom ou numéro") }, singleLine = true)
                IconButton(onClick = onAdd, enabled = name.isNotBlank()) { Icon(Icons.Default.AddCircle, contentDescription = "Ajouter") }
            }
            if (levelEnabled) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Niveau")
                    (1..5).forEach { level ->
                        FilterChip(selected = selectedLevel == level, onClick = { onLevel(level) }, label = { Text(level.toString()) })
                    }
                }
            }
        }
        items(players, key = { it.id }) { player ->
            ListItem(
                headlineContent = { Text(player.name) },
                supportingContent = { Text(if (levelEnabled) "Niveau " + (player.level?.toString() ?: "—") + " · " + player.matchesPlayed + " matchs" else player.matchesPlayed.toString() + " matchs joués") },
                trailingContent = { IconButton(onClick = { onRemove(player) }) { Icon(Icons.Default.Close, contentDescription = "Supprimer") } }
            )
        }
        item {
            Button(onClick = onGenerate, enabled = players.size >= 4, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                Icon(Icons.Default.Shuffle, null); Spacer(Modifier.width(8.dp)); Text("GÉNÉRER LA ROTATION")
            }
        }
        if (roundNumber > 0) {
            item { Text("Rotation " + roundNumber, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
            if (warning) item { Text("Attention : toutes les combinaisons inédites ne sont plus disponibles. Les répétitions éventuelles sont signalées.", color = MaterialTheme.colorScheme.error) }
            items(matches, key = { it.court }) { match ->
                Card {
                    Column(Modifier.padding(14.dp)) {
                        Text("TERRAIN " + match.court, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Column { Text("ÉQUIPE A", fontWeight = FontWeight.Bold); match.teamA.forEach { Text(it.name) } }
                            Text("VS", fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterVertically))
                            Column { Text("ÉQUIPE B", fontWeight = FontWeight.Bold); match.teamB.forEach { Text(it.name) } }
                        }
                        if (match.repeatedGroup) Text("Groupe déjà rencontré", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (waiting.isNotEmpty()) item { Text("En attente : " + waiting.joinToString { it.name }) }
            item { TimerCard(secondsLeft, timerRunning, onTimerToggle, onTimerReset, matchMinutes) }
        }
    }
}

@Composable
private fun SettingsPage(levels: Boolean, onLevels: (Boolean) -> Unit, courts: Int, onCourts: (Int) -> Unit, minutes: Int, onMinutes: (Int) -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Options du tournoi", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Card {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Gestion des niveaux", fontWeight = FontWeight.SemiBold)
                    Text("Niveaux facultatifs de 1 à 5 pour équilibrer les équipes.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = levels, onCheckedChange = onLevels)
            }
        }
        StepperCard("Nombre de terrains", courts, 1, 20, onCourts)
        StepperCard("Durée des matchs (minutes)", minutes, 5, 180, onMinutes)
    }
}

@Composable
private fun StepperCard(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Card {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f))
            IconButton(onClick = { onChange(value - 1) }, enabled = value > min) { Icon(Icons.Default.Remove, "Diminuer") }
            Text(value.toString(), fontWeight = FontWeight.Bold)
            IconButton(onClick = { onChange(value + 1) }, enabled = value < max) { Icon(Icons.Default.Add, "Augmenter") }
        }
    }
}

@Composable
private fun TimerPage(seconds: Int, running: Boolean, onRunning: (Boolean) -> Unit, onReset: () -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        TimerCard(seconds, running, { onRunning(!running) }, onReset, seconds / 60)
    }
}

@Composable
private fun TimerCard(seconds: Int, running: Boolean, onToggle: () -> Unit, onReset: () -> Unit, minutes: Int) {
    Card(Modifier.fillMaxWidth().padding(12.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("CHRONOMÈTRE", fontWeight = FontWeight.Bold)
            Text("%02d:%02d".format(seconds / 60, seconds % 60), fontSize = 42.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onToggle) { Text(if (running) "Pause" else "Démarrer") }
                OutlinedButton(onClick = onReset) { Text("Réinitialiser") }
            }
            Text("Durée du match : " + minutes + " min", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun HistoryPage(rounds: Int, groups: Int, modifier: Modifier) {
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Historique", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Rotations générées : " + rounds)
        Text("Groupes de quatre mémorisés : " + groups)
        Text("L'historique détaillé et sa sauvegarde locale seront consolidés dans la prochaine étape.")
    }
}

@Composable
private fun AboutPage(modifier: Modifier) {
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("À propos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Tennis Groups aide les clubs à organiser des rencontres amicales en double.")
        Text("L'application fonctionne hors ligne. Elle renouvelle les groupes de quatre, fait varier les partenaires et peut équilibrer les équipes grâce aux niveaux facultatifs de 1 à 5.")
        Text("Version 1.0.0")
    }
}
