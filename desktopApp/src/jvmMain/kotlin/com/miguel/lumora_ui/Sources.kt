package com.lagradost.cloudstream4

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream4.generated.resources.Res
import com.lagradost.cloudstream4.generated.resources.close
import com.lagradost.cloudstream4.generated.resources.extension
import com.lagradost.cloudstream4.generated.resources.info
import com.lagradost.cloudstream4.generated.resources.install
import com.lagradost.cloudstream4.generated.resources.installed_badge
import com.lagradost.cloudstream4.generated.resources.installing
import com.lagradost.cloudstream4.generated.resources.refresh
import com.lagradost.cloudstream4.generated.resources.source_working
import com.lagradost.cloudstream4.generated.resources.sources_add
import com.lagradost.cloudstream4.generated.resources.sources_add_hint
import com.lagradost.cloudstream4.generated.resources.sources_count
import com.lagradost.cloudstream4.generated.resources.sources_empty
import com.lagradost.cloudstream4.generated.resources.sources_experimental
import com.lagradost.cloudstream4.generated.resources.sources_filter
import com.lagradost.cloudstream4.generated.resources.sources_installed
import com.lagradost.cloudstream4.generated.resources.sources_installed_none
import com.lagradost.cloudstream4.generated.resources.sources_refresh_failed
import com.lagradost.cloudstream4.generated.resources.sources_remove_repo
import com.lagradost.cloudstream4.generated.resources.sources_repos
import com.lagradost.cloudstream4.generated.resources.sources_title
import com.lagradost.cloudstream4.generated.resources.uninstall
import com.lagradost.cloudstream4.generated.resources.update_available
import com.lagradost.cloudstream4.generated.resources.update_plugin
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Settings > Sources: manage repositories and install / update / remove their plugins. */
@Composable
fun SourcesScreen(onBack: () -> Unit) {
    var newRepo by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { if (PluginManager.available.isEmpty()) PluginManager.refreshAsync() }

    val addRepo = {
        if (newRepo.isNotBlank()) {
            PluginManager.addRepo(newRepo)
            newRepo = ""
        }
    }
    val shown = PluginManager.available.filter {
        filter.isBlank() || it.name.contains(filter.trim(), ignoreCase = true) ||
            it.language.orEmpty().contains(filter.trim(), ignoreCase = true)
    }
    val problems = PluginManager.status.value
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        cursorColor = MaterialTheme.colorScheme.primary,
    )

    CenteredContent(maxWidth = 920.dp) {
        ScreenHeader(
            title = stringResource(Res.string.sources_title),
            onBack = onBack,
            subtitle = PluginManager.available.size.takeIf { it > 0 }?.let { stringResource(Res.string.sources_count, it) },
        )
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(
                    Modifier
                        .padding(horizontal = ScreenPadding)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(Res.drawable.info), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    Text(stringResource(Res.string.sources_experimental), style = MaterialTheme.typography.bodySmall)
                }
            }

            // ---- Installed ----
            item { SectionTitle(stringResource(Res.string.sources_installed), Modifier.padding(top = 14.dp)) }
            if (PluginManager.installed.isEmpty()) {
                item {
                    Text(
                        stringResource(Res.string.sources_installed_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 4.dp),
                    )
                }
            }
            items(PluginManager.installed.keys.sorted()) { key ->
                val state = PluginManager.installed[key] ?: ""
                val label = PluginManager.available.firstOrNull { it.internalName == key }?.name ?: key
                Panel(Modifier.padding(horizontal = ScreenPadding).fillMaxWidth(), RoundedCornerShape(14.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PluginAvatar(label)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            SelectionContainer {
                                Text(
                                    when (state) {
                                        "" -> stringResource(Res.string.source_working)
                                        "…" -> stringResource(Res.string.installing)
                                        else -> state
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (state == "" || state == "…") MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        if (state != "…") {
                            PillButton(stringResource(Res.string.uninstall), onClick = {
                                PluginManager.available.firstOrNull { it.internalName == key }
                                    ?.let { PluginManager.uninstall(it) } ?: PluginManager.uninstallByName(key)
                            })
                        } else {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }

            // ---- Repositories ----
            item { SectionTitle(stringResource(Res.string.sources_repos), Modifier.padding(top = 18.dp)) }
            items(PluginManager.repos.toList()) { repo ->
                Panel(Modifier.padding(horizontal = ScreenPadding).fillMaxWidth(), RoundedCornerShape(14.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            repo,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { PluginManager.removeRepo(repo) }, modifier = Modifier.handCursor()) {
                            Icon(
                                painterResource(Res.drawable.close),
                                contentDescription = stringResource(Res.string.sources_remove_repo),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = newRepo,
                        onValueChange = { newRepo = it },
                        placeholder = { Text(stringResource(Res.string.sources_add_hint)) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = fieldColors,
                        modifier = Modifier.weight(1f),
                    )
                    PillButton(stringResource(Res.string.sources_add), onClick = { addRepo() }, filled = true)
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = ScreenPadding),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PillButton(
                        stringResource(Res.string.refresh),
                        onClick = { PluginManager.refreshAsync() },
                        icon = Res.drawable.refresh,
                        enabled = !PluginManager.busy.value,
                    )
                    if (PluginManager.busy.value) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            }
            if (problems.isNotBlank()) {
                item {
                    SelectionContainer {
                        Text(
                            stringResource(Res.string.sources_refresh_failed) + "\n\n" + problems,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .padding(horizontal = ScreenPadding)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.10f))
                                .padding(14.dp),
                        )
                    }
                }
            }

            // ---- Available plugins ----
            if (PluginManager.available.isNotEmpty()) {
                item {
                    OutlinedTextField(
                        value = filter,
                        onValueChange = { filter = it },
                        placeholder = { Text(stringResource(Res.string.sources_filter)) },
                        singleLine = true,
                        shape = RoundedCornerShape(28.dp),
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = 14.dp),
                    )
                }
            } else if (!PluginManager.busy.value && problems.isBlank()) {
                item {
                    Text(
                        stringResource(Res.string.sources_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(ScreenPadding),
                    )
                }
            }
            items(shown, key = { it.internalName }) { plugin -> PluginRow(plugin) }
        }
    }
}

@Composable
private fun PluginAvatar(name: String) {
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        val initial = name.trim().take(1).uppercase()
        if (initial.isNotEmpty()) {
            Text(initial, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        } else {
            Icon(painterResource(Res.drawable.extension), null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun PluginRow(plugin: RemotePlugin) {
    val state = PluginManager.installed[plugin.internalName]
    val hasUpdate = state == "" && plugin.version > (PluginManager.installedVersions[plugin.internalName] ?: plugin.version)
    Panel(Modifier.padding(horizontal = ScreenPadding).fillMaxWidth(), RoundedCornerShape(14.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PluginAvatar(plugin.name)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        plugin.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    plugin.language?.takeIf { it.isNotBlank() }?.let { Pill(it.uppercase()) }
                }
                val types = plugin.tvTypes.joinToString(" · ")
                if (types.isNotBlank()) {
                    Text(
                        types,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                when {
                    hasUpdate -> Text(
                        stringResource(Res.string.update_available),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    state == "" -> Text(
                        stringResource(Res.string.installed_badge),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    state != null && state != "…" -> SelectionContainer {
                        Text(state, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            when (state) {
                null -> PillButton(stringResource(Res.string.install), onClick = { PluginManager.install(plugin) }, filled = true)
                "…" -> {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                }
                else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (hasUpdate) {
                        PillButton(stringResource(Res.string.update_plugin), onClick = { PluginManager.install(plugin) }, filled = true)
                    }
                    PillButton(stringResource(Res.string.uninstall), onClick = { PluginManager.uninstall(plugin) })
                }
            }
        }
    }
}
