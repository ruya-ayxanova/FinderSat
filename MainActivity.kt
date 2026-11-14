@file:OptIn(ExperimentalMaterial3Api::class)

package com.prosatfinder

import android.app.Application
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.prosatfinder.data.*
import com.prosatfinder.i18n.AppLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Locale

private data class Filters(
    val q: String, val inactive: Boolean, val cont: String, val country: String, val fav: Boolean
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val dao = db.dao()
    private val repo = CatalogRepository(app, db)

    val query = MutableStateFlow("")
    val inactive = MutableStateFlow(false)
    val continent = MutableStateFlow("")
    val country = MutableStateFlow("")
    val favOnly = MutableStateFlow(false)
    val meta = MutableStateFlow<CatalogMeta?>(null)
    val status = MutableStateFlow<Int?>(null)
    val note = MutableStateFlow<String?>(null)

    val tpFreq = MutableStateFlow("")
    val tpPol = MutableStateFlow("")
    val tpBand = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val items = combine(query, inactive, continent, country, favOnly) { q, i, c, k, f ->
        Filters(q.trim(), i, c, k, f)
    }.flatMapLatest { f ->
        dao.search(f.q, if (f.inactive) 1 else 0, f.cont, f.country, if (f.fav) 1 else 0)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val tpHits = combine(tpFreq, tpPol, tpBand) { f, p, b -> Triple(f.toIntOrNull(), p, b) }
        .flatMapLatest { (f, p, b) ->
            dao.searchTransponders(f?.minus(3), f?.plus(3), p, b)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favorites = dao.favoriteIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val countries = dao.allRegions().map { rows ->
        rows.flatMap { it.split(",") }
            .filter { it.isNotBlank() && !it.startsWith("cont:") }.distinct()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun transponders(id: String) = dao.transponders(id)

    init {
        viewModelScope.launch {
            repo.ensureLoaded()
            meta.value = repo.meta()
        }
    }

    fun toggleFavorite(id: String, on: Boolean) = viewModelScope.launch {
        if (on) dao.addFavorite(Favorite(id)) else dao.removeFavorite(id)
    }

    fun update() = viewModelScope.launch {
        val r = repo.update(Config.CATALOG_URL)
        meta.value = repo.meta()
        note.value = null
        status.value = if (r.isSuccess) R.string.update_ok else R.string.update_failed
    }

    fun restore() = viewModelScope.launch {
        val r = repo.restoreLatestBackup()
        meta.value = repo.meta()
        note.value = null
        status.value = if (r.isSuccess) R.string.restore_ok else R.string.restore_failed
    }

    fun importCsv(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        val ctx = getApplication<Application>()
        val text = runCatching {
            ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "csv"
        val r = if (text == null) Result.failure(Exception("cannot read file"))
                else repo.importCsv(text, name)
        meta.value = repo.meta()
        note.value = r.exceptionOrNull()?.message
        status.value = if (r.isSuccess) R.string.import_ok else R.string.import_failed
    }
}

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface { SatApp() } } }
    }
}

private val CONTINENTS = listOf(
    "cont:EU" to R.string.cont_eu, "cont:AS" to R.string.cont_as, "cont:AF" to R.string.cont_af,
    "cont:NA" to R.string.cont_na, "cont:SA" to R.string.cont_sa, "cont:OC" to R.string.cont_oc
)

@Composable
fun SatApp(vm: MainViewModel = viewModel()) {
    val items by vm.items.collectAsState()
    val favs by vm.favorites.collectAsState()
    val q by vm.query.collectAsState()
    val inactive by vm.inactive.collectAsState()
    val cont by vm.continent.collectAsState()
    val country by vm.country.collectAsState()
    val favOnly by vm.favOnly.collectAsState()
    val countries by vm.countries.collectAsState()
    val meta by vm.meta.collectAsState()
    val status by vm.status.collectAsState()
    val note by vm.note.collectAsState()
    var expanded by remember { mutableStateOf<String?>(null) }
    var tpMode by remember { mutableStateOf(false) }
    val grouped = remember(items) { items.groupBy { it.position } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importCsv(uri)
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = q, onValueChange = { vm.query.value = it }, singleLine = true,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                modifier = Modifier.weight(1f)
            )
            LanguageMenu()
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(!inactive, { vm.inactive.value = false }, { Text(stringResource(R.string.tab_active)) })
            FilterChip(inactive, { vm.inactive.value = true }, { Text(stringResource(R.string.tab_inactive)) })
            FilterChip(favOnly, { vm.favOnly.value = !favOnly }, { Text("★ " + stringResource(R.string.favorites_only)) })
            FilterChip(tpMode, { tpMode = !tpMode }, { Text(stringResource(R.string.transponder_search)) })
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(cont.isEmpty(), { vm.continent.value = "" }, { Text(stringResource(R.string.filter_all)) })
            CONTINENTS.forEach { (code, res) ->
                FilterChip(cont == code, { vm.continent.value = if (cont == code) "" else code },
                    { Text(stringResource(res)) })
            }
            CountryMenu(countries, country) { vm.country.value = it }
        }
        if (tpMode) {
            TransponderSearch(vm, Modifier.weight(1f))
        } else {
            if (items.isEmpty()) Text(stringResource(R.string.no_results))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                grouped.forEach { (pos, list) ->
                    item(key = "h$pos") {
                        Text(
                            posText(pos), style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    items(list, key = { it.id }) { s ->
                        val fav = s.id in favs
                        Card(
                            onClick = { expanded = if (expanded == s.id) null else s.id },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(s.name, style = MaterialTheme.typography.titleMedium)
                                        Text("${s.operator ?: "—"} · ${posText(s.position)}")
                                        Text(statusText(s.status), style = MaterialTheme.typography.bodySmall)
                                    }
                                    TextButton({ vm.toggleFavorite(s.id, !fav) }) { Text(if (fav) "★" else "☆") }
                                }
                                if (expanded == s.id) TransponderList(vm, s.id)
                            }
                        }
                    }
                }
            }
        }
        meta?.let {
            Text(
                stringResource(R.string.catalog_info, it.version.toString(), it.date, it.source),
                style = MaterialTheme.typography.bodySmall
            )
            val age = daysOld(it.date)
            if (age != null && age > 90)
                Text(
                    stringResource(R.string.data_stale, age.toInt()),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error
                )
        }
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            TextButton({ vm.update() }) { Text(stringResource(R.string.update_catalog)) }
            TextButton({ picker.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.import_csv)) }
            TextButton({ vm.restore() }) { Text(stringResource(R.string.restore_backup)) }
        }
        status?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall) }
        note?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun TransponderSearch(vm: MainViewModel, modifier: Modifier = Modifier) {
    val f by vm.tpFreq.collectAsState()
    val pol by vm.tpPol.collectAsState()
    val band by vm.tpBand.collectAsState()
    val hits by vm.tpHits.collectAsState()

    Column(modifier) {
        OutlinedTextField(
            value = f, onValueChange = { vm.tpFreq.value = it.filter(Char::isDigit).take(5) },
            singleLine = true, label = { Text(stringResource(R.string.freq_hint)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("H", "V", "L", "R").forEach { p ->
                FilterChip(pol == p, { vm.tpPol.value = if (pol == p) "" else p }, { Text(p) })
            }
            listOf("Ku", "C", "Ka").forEach { b ->
                FilterChip(band == b, { vm.tpBand.value = if (band == b) "" else b }, { Text(b) })
            }
        }
        if (hits.isEmpty()) Text(stringResource(R.string.no_results))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(hits) { h ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${h.satName} · ${posText(h.position)}", style = MaterialTheme.typography.titleMedium)
                        Text(tpLine(Transponder(0, h.satId, h.frequencyMhz, h.polarization, h.symbolRate, h.fec, h.band)))
                        Text(statusText(h.status), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
fun TransponderList(vm: MainViewModel, id: String) {
    val list by remember(id) { vm.transponders(id) }.collectAsState(initial = emptyList())
    Spacer(Modifier.height(8.dp))
    if (list.isEmpty()) Text(stringResource(R.string.no_transponders), style = MaterialTheme.typography.bodySmall)
    list.forEach { Text(tpLine(it), style = MaterialTheme.typography.bodySmall) }
}

fun tpLine(t: Transponder): String = listOf(
    (t.frequencyMhz?.let { "$it MHz" } ?: "—") + " " + (t.polarization ?: "—"),
    "SR " + (t.symbolRate?.let { "$it ksym/s" } ?: "—"),
    "FEC " + (t.fec ?: "—"),
    t.band ?: "—"
).joinToString(" · ")

fun daysOld(date: String): Long? = runCatching {
    java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.parse(date), java.time.LocalDate.now())
}.getOrNull()

@Composable
fun posText(p: Double?): String = when {
    p == null -> "—"
    p >= 0 -> stringResource(R.string.position_east, p)
    else -> stringResource(R.string.position_west, -p)
}

@Composable
fun statusText(s: String): String = stringResource(
    when (s) {
        "VERIFIED_ACTIVE" -> R.string.status_verified
        "INACTIVE" -> R.string.status_inactive
        else -> R.string.status_unverified
    }
)

@Composable
fun CountryMenu(countries: List<String>, selected: String, onSelect: (String) -> Unit) {
    val loc: Locale = LocalConfiguration.current.locales[0]
    fun name(code: String) = Locale("", code).getDisplayCountry(loc)
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(selected.isNotEmpty(), { open = true }, {
            Text("🌐 " + if (selected.isEmpty()) stringResource(R.string.filter_all) else name(selected))
        })
        DropdownMenu(open, { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.filter_all)) },
                onClick = { open = false; onSelect("") })
            countries.sortedBy { name(it) }.forEach { c ->
                DropdownMenuItem(text = { Text(name(c)) }, onClick = { open = false; onSelect(c) })
            }
        }
    }
}

@Composable
fun LanguageMenu() {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton({ open = true }) { Text(stringResource(R.string.language)) }
        DropdownMenu(open, { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.language_system)) },
                onClick = { open = false; AppLanguage.apply(null) })
            AppLanguage.all.forEach { l ->
                DropdownMenuItem(
                    text = { Text(l.nativeName) },
                    onClick = { open = false; AppLanguage.apply(l.tag) })
            }
        }
    }
}
