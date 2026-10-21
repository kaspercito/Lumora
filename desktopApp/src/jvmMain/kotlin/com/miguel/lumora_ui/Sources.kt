package com.lagradost.cloudstream4

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream4.compose.WhiteButton
import com.lagradost.cloudstream4.generated.resources.Res
import com.lagradost.cloudstream4.generated.resources.installed_tap_remove
import com.lagradost.cloudstream4.generated.resources.installing
import com.lagradost.cloudstream4.generated.resources.plugin_error
import com.lagradost.cloudstream4.generated.resources.refresh
import com.lagradost.cloudstream4.generated.resources.sources_experimental
import com.lagradost.cloudstream4.generated.resources.sources_repo
import com.lagradost.cloudstream4.generated.resources.sources_title
import com.lagradost.cloudstream4.generated.resources.tap_install
import com.mihon.presentation.settings.widget.TextPreferenceWidget
import org.jetbrains.compose.resources.stringResource

/** Settings > Sources: pick the repository and install/remove its plugins. */
@Composable
fun SourcesScreen(onBack: () -> Unit) {
    var repo by remember { mutableStateOf(PluginManager.repoUrl) }
    LaunchedEffect(Unit) { if (PluginManager.available.isEmpty()) PluginManager.refreshAsync() }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackButton(onBack)
            Text(stringResource(Res.string.sources_title), style = MaterialTheme.typography.titleMedium)
        }
        Text(
            stringResource(Res.string.sources_experimental),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        OutlinedTextField(
            value = repo,
            onValueChange = { repo = it },
            label = { Text(stringResource(Res.string.sources_repo)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
        WhiteButton(stringResource(Res.string.refresh), Modifier.padding(horizontal = 16.dp)) {
            PluginManager.repoUrl = repo
            PluginManager.refreshAsync()
        }
        val error = PluginManager.status.value
        if (error.isNotBlank()) {
            Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
        }
        LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp)) {
            items(PluginManager.available.toList()) { plugin ->
                val state = PluginManager.installed[plugin.internalName]
                val summary = when (state) {
                    null -> stringResource(Res.string.tap_install)
                    "" -> stringResource(Res.string.installed_tap_remove)
                    "…" -> stringResource(Res.string.installing)
                    else -> stringResource(Res.string.plugin_error, state)
                }
                TextPreferenceWidget(
                    title = plugin.name,
                    subtitle = listOfNotNull(
                        plugin.language,
                        plugin.tvTypes.joinToString(", ").takeIf { it.isNotBlank() },
                        summary,
                    ).joinToString(" · "),
                    onPreferenceClick = {
                        if (state == null) PluginManager.install(plugin) else if (state != "…") PluginManager.uninstall(plugin)
                    },
                )
            }
        }
    }
}
