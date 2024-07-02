package dev.corruptedark.diditakemymeds.activities.main

enum class SortBy(val key: String) {
    TIME("time"), NAME("name"), TYPE("type");

    companion object {
        private val values = values()
        fun getByKeyOrDefault(key: String, default: SortBy) : SortBy {
            return values.firstOrNull { it.key == key } ?: default
        }
    }

}