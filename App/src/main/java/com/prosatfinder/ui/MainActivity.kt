package com.prosatfinder.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.lifecycle.viewmodel.compose.viewModel
import com.prosatfinder.R
import com.prosatfinder.data.SatelliteEntity
import com.prosatfinder.data.TransponderEntity
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SatApp() }
    }
}

@Composable
fun SatApp(vm: MainViewModel = viewModel()) {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val dir = if (isRtl(locale)) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides dir) {
        MaterialTheme { Surface(Modifier.fillMaxSize()) { MainScreen(vm, locale) } }
    }
}

@Composable
private fun MainScreen(vm: MainViewModel, locale: Locale) {
    val rows by vm.rows.collectAsState()
    val q by vm.query.collectAsState()
    val tab by vm.tab.collectAsState()
    val continent by vm.continent.collectAsState()
    val country by vm.country.collectAsState()
    val facets by vm.facets.collectAsState()
    val meta by vm.meta.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<SatelliteEntity?>(null) }
    val all = stringResource(R.string.filter_all)
    val east = stringResource(R.string.pos_east)
    val west = stringResource(R.string.pos_west)

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = q, onValueChange = { vm.query.value = it },
                placeholder = { Text(stringResource(R.string.search_hint)) },
                singleLine = true, modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { showSettings = true }) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
            }
        }
        TabRow(selectedTabIndex = tab.ordinal) {
            listOf(Tab.ALL to R.string.tab_all, Tab.FAVORITES to R.string.tab_favorites, Tab.INACTIVE to R.string.tab_inactive)
                .forEach { (t, label) ->
                    Tab(selected = tab == t, onClick = { vm.tab.value = t }, text = { Text(stringResource(label)) })
                }
        }
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterMenu(
                options = listOf<Pair<String?, String>>(null to all) + facets.continents.map { it to regionName(it, locale) },
                selected = continent, onSelect = { vm.continent.value = it }, modifier = Modifier.weight(1f)
            )
            FilterMenu(
                options = listOf<Pair<String?, String>>(null to all) +
                    facets.countries.map { it to regionName(it, locale) }.sortedBy { it.second },
                selected = country, onSelect = { vm.country.value = it }, modifier = Modifier.weight(1f)
            )
        }
        if (rows.isEmpty()) {
            Text(stringResource(R.string.no_results), Modifier.padding(16.dp))
        }
        LazyColumn(Modifier.weight(1f)) {
            // Aynı yörünge konumundaki uydular ayrı satırlar; konum başlığı altında gruplanır.
            rows.groupBy { it.sat.position }.forEach { (pos, group) ->
                item(key = "h$pos") {
                    Text(
                        positionLabel(pos, east, west, locale), fontWeight = FontWeight.Bold, fontSize = 18.sp,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
                items(group, key = { it.sat.id }) { row ->
                    Row(
                        Modifier.fillMaxWidth().clickable { selected = row.sat }.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(row.sat.name, fontWeight = FontWeight.Medium)
                            Text(
                                listOfNotNull(row.sat.operatorName, stringResource(statusRes(row.sat.status))).joinToString(" · "),
                                fontSize = 13.sp
                            )
                        }
                        TextButton(onClick = { vm.toggleFavorite(row.sat.id) }) {
                            Text(if (row.favorite) "★" else "☆", fontSize = 22.sp)
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
        meta?.let {
            Column(Modifier.padding(8.dp)) {
                Text(stringResource(R.string.data_updated, it.updatedAt), fontSize = 12.sp)
                Text(stringResource(R.string.data_source, it.source), fontSize = 12.sp)
            }
        }
    }

    selected?.let { DetailDialog(vm, it, locale, east, west) { selected = null } }
    if (showSettings) SettingsDialog(vm) { showSettings = false }
}

private fun statusRes(status: String) = when (status) {
    "active" -> R.string.status_active
    "inactive" -> R.string.status_inactive
    else -> R.string.status_unverified
}

@Composable
private fun FilterMenu(
    options: List<Pair<String?, String>>, selected: String?, onSelect: (String?) -> Unit, modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(options.firstOrNull { it.first == selected }?.second ?: options.first().second, maxLines = 1)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (code, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(code); open = false })
            }
        }
    }
}

@Composable
private fun DetailDialog(vm: MainViewModel, sat: SatelliteEntity, locale: Locale, east: String, west: String, onDismiss: () -> Unit) {
    val tps by produceState(emptyList<TransponderEntity>(), sat.id) { value = vm.transponders(sat.id) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) } },
        title = { Text(sat.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(listOfNotNull(sat.operatorName, positionLabel(sat.position, east, west, locale),
                    stringResource(statusRes(sat.status))).joinToString(" · "))
                Spacer(Modifier.height(8.dp))
                if (tps.isEmpty()) Text(stringResource(R.string.transponders_none))
                // Teknik gösterim: MHz, SR (ksym/s), FEC uluslararası semboller; LRI/PDI ile sıra korunur (RTL'de bozulmaz).
                tps.forEach {
                    Text("\u2066${it.band}  ${it.freqMhz} MHz ${it.pol}  SR ${it.symbolRate ?: "—"}  FEC ${it.fec ?: "—"}\u2069",
                        fontSize = 14.sp, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }
    )
}

@Composable
private fun SettingsDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    val tags = stringArrayResource(R.array.language_tags)
    val names = stringArrayResource(R.array.language_names)
    val current = remember { currentLanguageTag() }
    var url by remember { mutableStateOf(vm.repo.updateUrl) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val done = stringResource(R.string.update_done)
    val failed = stringResource(R.string.update_failed)

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) } },
        title = { Text(stringResource(R.string.settings)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.language), fontWeight = FontWeight.Bold)
                val options = listOf("" to stringResource(R.string.language_system)) + tags.zip(names)
                options.forEach { (tag, name) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { setAppLanguage(tag) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = tag == current, onClick = { setAppLanguage(tag) })
                        Text(name)
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = url, onValueChange = { url = it }, singleLine = true,
                    placeholder = { Text("https://…/catalog.json") }, modifier = Modifier.fillMaxWidth()
                )
                Button(
                    enabled = !busy && url.isNotBlank(),
                    onClick = {
                        busy = true
                        scope.launch {
                            val ok = vm.update(url)
                            busy = false
                            Toast.makeText(ctx, if (ok) done else failed, Toast.LENGTH_LONG).show()
                        }
                    }
                ) { Text(stringResource(R.string.update_catalog)) }
            }
        }
    )
}
