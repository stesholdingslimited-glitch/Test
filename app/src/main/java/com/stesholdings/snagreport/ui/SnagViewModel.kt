package com.stesholdings.snagreport.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stesholdings.snagreport.data.Rating
import com.stesholdings.snagreport.data.SnagDatabase
import com.stesholdings.snagreport.data.SnagEntity
import com.stesholdings.snagreport.data.SnagRepository
import com.stesholdings.snagreport.ui.list.SortDirection
import com.stesholdings.snagreport.ui.list.SortField
import com.stesholdings.snagreport.ui.list.SortOrder
import com.stesholdings.snagreport.util.DocxExporter
import com.stesholdings.snagreport.util.ImageStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** New, unsaved entries use this sentinel id instead of a real Room row id. */
const val NEW_ENTRY_ID = -1L

class SnagViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SnagRepository(SnagDatabase.getInstance(application).snagDao())

    private val _sortOrder = MutableStateFlow(SortOrder())
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    val entries: StateFlow<List<SnagEntity>> = combine(
        repository.observeAll(),
        _sortOrder
    ) { list, order -> sortEntries(list, order) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    fun setSortOrder(field: SortField, direction: SortDirection) {
        _sortOrder.value = SortOrder(field, direction)
    }

    private fun sortEntries(list: List<SnagEntity>, order: SortOrder): List<SnagEntity> {
        val comparator = when (order.field) {
            SortField.BUILDING -> compareBy(String.CASE_INSENSITIVE_ORDER) { e: SnagEntity -> e.buildingName }
            SortField.LEVEL -> compareBy(String.CASE_INSENSITIVE_ORDER) { e: SnagEntity -> e.level }
            SortField.ROOM -> compareBy(String.CASE_INSENSITIVE_ORDER) { e: SnagEntity -> e.roomLocation }
            SortField.RATING -> compareBy { e: SnagEntity -> e.rating.ordinal }
            SortField.DESCRIPTION -> compareBy(String.CASE_INSENSITIVE_ORDER) { e: SnagEntity -> e.description }
        }
        val sorted = list.sortedWith(comparator)
        return if (order.direction == SortDirection.DESCENDING) sorted.asReversed() else sorted
    }

    suspend fun getEntry(id: Long): SnagEntity? =
        if (id == NEW_ENTRY_ID) null else repository.getById(id)

    fun saveEntry(
        id: Long,
        imagePath: String?,
        buildingName: String,
        level: String,
        roomLocation: String,
        rating: Rating,
        description: String,
        onSaved: () -> Unit
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            if (id == NEW_ENTRY_ID) {
                repository.insert(
                    SnagEntity(
                        imagePath = imagePath,
                        buildingName = buildingName,
                        level = level,
                        roomLocation = roomLocation,
                        rating = rating,
                        description = description,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            } else {
                val existing = repository.getById(id)
                repository.update(
                    SnagEntity(
                        id = id,
                        imagePath = imagePath,
                        buildingName = buildingName,
                        level = level,
                        roomLocation = roomLocation,
                        rating = rating,
                        description = description,
                        createdAt = existing?.createdAt ?: now,
                        updatedAt = now
                    )
                )
                // If the photo was replaced or removed, drop the file that's no longer referenced.
                if (existing != null && existing.imagePath != imagePath) {
                    repository.deleteImageFile(existing.imagePath)
                }
            }
            onSaved()
        }
    }

    fun deleteEntry(entry: SnagEntity, onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.delete(entry)
            onDeleted()
        }
    }

    fun exportToDocx() {
        val context = getApplication<Application>()
        val list = entries.value
        if (list.isEmpty()) {
            _exportState.value = ExportState.Error("Add at least one snag before exporting.")
            return
        }
        _exportState.value = ExportState.Loading
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { DocxExporter.export(context, list) }
            }
            _exportState.value = result.fold(
                onSuccess = { uri: Uri -> ExportState.Success(uri) },
                onFailure = { e -> ExportState.Error(e.message ?: "Export failed") }
            )
        }
    }

    fun consumeExportState() {
        _exportState.value = ExportState.Idle
    }

    suspend fun processImage(sourceUri: Uri): String = withContext(Dispatchers.IO) {
        ImageStorage.processAndStore(getApplication<Application>(), sourceUri)
    }

    fun createCameraCaptureUri(): Uri = ImageStorage.createCameraCaptureUri(getApplication<Application>())

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SnagViewModel(application) as T
    }
}
