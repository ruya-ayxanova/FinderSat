@file:OptIn(ExperimentalMaterial3Api::class)

package com.prosatfinder

import android.app.Application
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.prosatfinder.data.*
import com.prosatfinder.i18n.AppLanguage
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

    @OptIn(ExperimentalCoroutinesApi::class)
    val items = combine(query, inactive, continent, country, favOnly) { q, i, c, k, f ->
        Filters(q.trim(), i, c, k, f)
    }.flatMapLatest { f ->
        dao.search(f.q, if (f.inactive) 1 else 0, f.cont, f.country, if (f.fav) 1 else 0)
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
        status.value = if (r.isSuccess) R.string.update_ok else R.string.update_failed
    }

    fun restore() = viewModelScope.launch {
        val r = repo.restoreLatestBackup()
        meta.value = repo.meta()
        status.value = if (r.isSuccess) R.string.restore_ok else R.string.restore_failed
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
    var expanded by remember { mutableStateOf<String?>(null) }
    val grouped = remember(items) { items.groupBy { it.position } }

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
            TextButton({ vm.restore() }) { Text(stringResource(R.string.restore_backup)) }
        }
        status?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall) }
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
