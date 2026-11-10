package com.adwanadam.tennisgroups

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.compose.foundation.layout.*
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
private data class RoundHistory(val number: Int, val matches: List<DoublesMatch>, val waiting: List<Player>)

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
    var language by remember { mutableStateOf("Français") }
    var menuOpen by remember { mutableStateOf(false) }
    var languageOpen by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    var page by remember { mutableStateOf("players") }
    var round by remember { mutableIntStateOf(0) }
    var matches by remember { mutableStateOf(emptyList<DoublesMatch>()) }
    var waiting by remember { mutableStateOf(emptyList<Player>()) }
    var warning by remember { mutableStateOf(false) }
    var remaining by remember { mutableIntStateOf(1200) }
    var running by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Player?>(null) }
    var tournamentFormat by remember { mutableStateOf("Doubles") }

    fun generateRound() {
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
            if (remaining == 0) running = false
        }

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Tennis tournament", fontWeight = FontWeight.Bold)
                        }
                    },
                    navigationIcon = {
                        Box {
                            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.Menu, contentDescription = "Ouvrir le menu") }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(text = { Text("Joueurs") }, leadingIcon = { Icon(Icons.Default.People, null) }, onClick = { page = "players"; menuOpen = false })
                                DropdownMenuItem(text = { Text("Matchs") }, leadingIcon = { Icon(Icons.Default.SportsTennis, null) }, onClick = { page = "matches"; menuOpen = false })
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
                            Text(players.size.toString() + " joueurs", modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar(windowInsets = WindowInsets.navigationBars) {
                    NavigationBarItem(selected = page == "players" || page == "settings", onClick = { page = "players" }, icon = { Icon(Icons.Default.People, null) }, label = { Text("Joueurs") })
                    NavigationBarItem(selected = page == "matches", onClick = { page = "matches" }, icon = { Icon(Icons.Default.SportsTennis, null) }, label = { Text("Matchs") })
                    NavigationBarItem(selected = page == "history", onClick = { page = "history" }, icon = { Icon(Icons.Default.History, null) }, label = { Text("Historique") })
                }
            }
        ) { padding ->
            when (page) {
                "matches" -> MatchesPage(round, matches, waiting, warning, remaining, running, duration, levelEnabled, players.size, tournamentFormat,
                    { running = !running }, { running = false; remaining = duration * 60 }, { if (players.size >= (if (tournamentFormat == "Simples") 2 else 4)) generateRound() }, Modifier.padding(padding))
                "history" -> HistoryPage(history.toList(), levelEnabled, Modifier.padding(padding))
                "about" -> AboutPage(Modifier.padding(padding))
                else -> PlayersPage(
                    players, entry, { entry = it }, tournamentFormat, { tournamentFormat = it }, levelEnabled, { levelEnabled = it },
                    courts, { courts = it.coerceIn(1, 20) }, duration, { duration = it.coerceIn(5, 180); remaining = duration * 60 },
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
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("TOURNOI " + tournamentFormat.uppercase(), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge)
                    Text("Organisez vos rotations simplement.", color = MaterialTheme.colorScheme.onPrimary)
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
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Niveau des joueurs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("Chaque joueur possède son propre niveau.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(levelsEnabled, onLevels)
                    }
                    if (levelsEnabled) {
                        if (players.isEmpty()) {
                            Text("Les nouveaux joueurs auront le niveau Intermédiaire par défaut.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            players.forEach { player ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(player.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    LevelSelector(player.level ?: 2) { selected ->
                                        val index = players.indexOfFirst { it.id == player.id }
                                        if (index >= 0) players[index] = players[index].copy(level = selected)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Organisation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        OrganizationInput("Terrains disponibles", courts, "terrains", { Icon(Icons.Default.SportsTennis, null, tint = MaterialTheme.colorScheme.primary) }, Modifier.weight(1f)) { onCourts(it.coerceIn(1, 20)) }
                        OrganizationInput("Durée du match", duration, "min", { Icon(Icons.Default.AccessTime, null, tint = MaterialTheme.colorScheme.primary) }, Modifier.weight(1f)) { onDuration(it.coerceIn(5, 180)) }
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

@Composable
private fun LevelSelector(selected: Int, onSelected: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        levels.forEach { (value, label) ->
            FilterChip(selected == value, { onSelected(value) }, label = { Text(label, maxLines = 1) }, modifier = Modifier.weight(1f))
        }
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
                val digits = raw.filter(Char::isDigit).take(3)
                textValue = digits
                digits.toIntOrNull()?.let(onValueChange)
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            suffix = { Text(unit, style = MaterialTheme.typography.labelSmall) }
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
    onToggle: () -> Unit, onReset: () -> Unit, onNext: () -> Unit, modifier: Modifier
) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text(if (round == 0) "Matchs" else "Tour " + round, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(if (round == 0) "Les rencontres apparaîtront ici." else matches.size.toString() + (if (matches.size == 1) " terrain en jeu" else " terrains en jeu"), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        Text("Durée prévue : " + duration + " minutes")
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onToggle) {
                                Icon(if (running) Icons.Default.Pause else Icons.Default.PlayArrow, null)
                                Spacer(Modifier.width(6.dp))
                                Text(if (running) "Pause" else "Démarrer")
                            }
                            OutlinedButton(onReset) {
                                Icon(Icons.Default.Refresh, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Réinitialiser")
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
                    }
                }
            }
            if (waiting.isNotEmpty()) {
                item {
                    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.HourglassEmpty, null)
                            Spacer(Modifier.width(10.dp))
                            Text(waiting.size.toString() + (if (waiting.size == 1) " joueur en attente : " else " joueurs en attente : ") + waiting.joinToString(", ") { it.name })
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
                    OrganizationInput("Terrains disponibles", courts, "terrains", { Icon(Icons.Default.SportsTennis, null, tint = MaterialTheme.colorScheme.primary) }, Modifier.weight(1f)) { onCourts(it.coerceIn(1, 20)) }
                    OrganizationInput("Durée du match", duration, "min", { Icon(Icons.Default.AccessTime, null, tint = MaterialTheme.colorScheme.primary) }, Modifier.weight(1f)) { onDuration(it.coerceIn(5, 180)) }
                }
            }
        }
    }
}

@Composable
private fun HistoryPage(history: List<RoundHistory>, levelsEnabled: Boolean, modifier: Modifier) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Historique", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Retrouvez les rencontres et les partenaires de chaque tour.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (history.isEmpty()) {
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
                            Text(item.matches.size.toString() + (if (item.matches.size == 1) " terrain" else " terrains"), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                        }
                        item.matches.forEach { match ->
                            Text("Terrain " + match.court, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                HistoryTeam(match.teamA, levelsEnabled, Modifier.weight(1f))
                                Text("VS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                HistoryTeam(match.teamB, levelsEnabled, Modifier.weight(1f))
                            }
                            if (match.repeatedGroup) Text("Groupe déjà rencontré", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                        if (item.waiting.isNotEmpty()) Text(item.waiting.size.toString() + (if (item.waiting.size == 1) " joueur en attente : " else " joueurs en attente : ") + item.waiting.joinToString(", ") { it.name }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
