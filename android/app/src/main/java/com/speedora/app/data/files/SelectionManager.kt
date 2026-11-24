package com.speedora.app.data.files

import com.speedora.app.core.model.LocalFileItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SelectionSnapshot(
    val items: List<LocalFileItem> = emptyList(),
    val totalCount: Int = 0,
    val totalBytes: Long = 0L
) {
    val selectedIds: Set<String> get() = items.mapTo(HashSet()) { it.id }
}

/**
 * Persistent cross-category selection tray preserving user selections across Photos, Videos,
 * Music, Apps, Documents, Archives, Downloads, Folders, and Recent tabs.
 */
class SelectionManager {
    private val _selection = MutableStateFlow(SelectionSnapshot())
    val selection: StateFlow<SelectionSnapshot> = _selection.asStateFlow()

    fun toggle(item: LocalFileItem) {
        _selection.update { current ->
            val map = LinkedHashMap<String, LocalFileItem>(current.items.size + 1)
            current.items.forEach { map[it.id] = it }
            if (map.containsKey(item.id)) {
                map.remove(item.id)
            } else {
                map[item.id] = item
            }
            buildSnapshot(map.values.toList())
        }
    }

    fun addAll(items: List<LocalFileItem>) {
        if (items.isEmpty()) return
        _selection.update { current ->
            val map = LinkedHashMap<String, LocalFileItem>(current.items.size + items.size)
            current.items.forEach { map[it.id] = it }
            items.filter { !it.isDirectory }.forEach { map[it.id] = it }
            buildSnapshot(map.values.toList())
        }
    }

    fun removeById(id: String) {
        _selection.update { current ->
            val updated = current.items.filterNot { it.id == id }
            buildSnapshot(updated)
        }
    }

    fun clear() {
        _selection.value = SelectionSnapshot()
    }

    fun isSelected(id: String): Boolean = _selection.value.items.any { it.id == id }

    private fun buildSnapshot(items: List<LocalFileItem>): SelectionSnapshot =
        SelectionSnapshot(
            items = items,
            totalCount = items.size,
            totalBytes = items.sumOf { it.sizeBytes.coerceAtLeast(0L) }
        )
}
