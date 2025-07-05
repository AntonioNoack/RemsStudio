package me.anno.remsstudio.objects.documents

import me.anno.cache.CacheSection
import me.anno.utils.types.Strings.isBlank2

object SiteSelection : CacheSection<String, List<IntRange>>("SiteSelection") {
    fun parseSites(sites: String): List<IntRange> {
        if (sites.isBlank2()) return listOf(0 until maxSite)
        return getEntry(sites, timeout) { sites, result ->
            val delta = -1
            result.value = sites
                .replace('+', ',')
                .replace(';', ',')
                .split(',')
                .mapNotNull {
                    val fi = it.indexOf('-')
                    if (fi < 0) {
                        it.trim().toIntOrNull()?.run { (this + delta)..(this + delta) }
                    } else {
                        it.substring(0, fi).trim().toIntOrNull()?.run {
                            val a = this
                            val b = it.substring(fi + 1).trim().toIntOrNull() ?: maxSite
                            (a + delta)..(b + delta)
                        }
                    }
                }
                .filter { !it.isEmpty() }
        }.waitFor() ?: emptyList()
    }

    private const val timeout = 1000L
    private const val maxSite = 100_000_000
}