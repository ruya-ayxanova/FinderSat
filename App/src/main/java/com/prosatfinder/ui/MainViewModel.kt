package com.prosatfinder.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.prosatfinder.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Locale

enum class Tab { ALL, FAVORITES, INACTIVE }

data class SatRow(val sat: SatelliteEntity, val favorite: Boolean)
data class Facets(val continents: List<String>, val countries: List<String>)
private data class Filters(val q: String, val tab: Tab, val continent: String?, val country: String?)

fun csv(s: String): List<String> = s.split(',').filter { it.isNotBlank() }

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = CatalogDatabase.get(app).dao()
    val repo = CatalogRepository(app, dao)

    val query = MutableStateFlow("")
    val tab = MutableStateFlow(Tab.ALL)
    val continent = MutableStateFlow<String?>(null)
    val country = MutableStateFlow<String?>(null)

    private val keep = SharingStarted.WhileSubscribed(5000)
    val meta: StateFlow<CatalogMetaEntity?> = dao.meta().stateIn(viewModelScope, keep, null)
    private val favorites = dao.favorites().stateIn(viewModelScope, keep, emptyList())
    private val all = dao.satellites().stateIn(viewModelScope, keep, emptyList())

    val facets: StateFlow<Facets> = all.map { list ->
        Facets(
            list.flatMap { csv(it.continents) }.distinct().sorted(),
            list.flatMap { csv(it.countries) }.distinct().sorted()
        )
    }.stateIn(viewModelScope, keep, Facets(emptyList(), emptyList()))

    private val filters = combine(query, tab, continent, country) { q, t, c, k -> Filters(q, t, c, k) }

    val rows: StateFlow<List<SatRow>> = combine(all, favorites, filters) { sats, favs, f ->
        val favSet = favs.toSet()
        val q = f.q.trim().lowercase()
        sats.asSequence()
            .filter {
                when (f.tab) {
                    Tab.ALL -> it.status != "inactive"
                    Tab.FAVORITES -> it.id in favSet
                    Tab.INACTIVE -> it.status == "inactive"
                }
            }
            .filter { f.continent == null || f.continent in csv(it.continents) }
            .filter { f.country == null || f.country in csv(it.countries) }
            .filter {
                q.isEmpty() || it.name.lowercase().contains(q) ||
                    (it.operatorName?.lowercase()?.contains(q) == true) ||
                    String.format(Locale.ROOT, "%.1f", kotlin.math.abs(it.position)).contains(q) ||
                    csv(it.countries).any { c -> c.lowercase() == q }
            }
            .map { SatRow(it, it.id in favSet) }
            .toList()
    }.stateIn(viewModelScope, keep, emptyList())

    init {
        viewModelScope.launch { runCatching { repo.ensureLoaded() } }
    }

    fun toggleFavorite(id: String) = viewModelScope.launch {
        if (id in favorites.value) dao.removeFavorite(id) else dao.addFavorite(FavoriteEntity(id))
    }

    suspend fun transponders(id: String) = dao.transponders(id)

    suspend fun update(url: String): Boolean {
        repo.updateUrl = url
        return runCatching { repo.update(url) }.isSuccess
    }
}
