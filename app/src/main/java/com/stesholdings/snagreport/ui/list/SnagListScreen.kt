package com.stesholdings.snagreport.ui.list

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.stesholdings.snagreport.R
import com.stesholdings.snagreport.data.Rating
import com.stesholdings.snagreport.data.SnagEntity
import com.stesholdings.snagreport.ui.ExportState
import com.stesholdings.snagreport.ui.SnagViewModel
import com.stesholdings.snagreport.util.DocxExporter
import androidx.core.content.ContextCompat
import java.io.File
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnagListScreen(
    viewModel: SnagViewModel,
    onAddEntry: () -> Unit,
    onOpenEntry: (Long) -> Unit
) {
    val entries by viewModel.entries.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val exportState by viewModel.exportState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // Only needed on API 26-28: from API 29 the export goes through MediaStore, which
    // doesn't require this legacy permission.
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.exportToDocx()
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Storage permission is needed to export the report")
            }
        }
    }

    fun requestExport() {
        val needsLegacyPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        if (needsLegacyPermission) {
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            viewModel.exportToDocx()
        }
    }

    LaunchedEffect(exportState) {
        when (val state = exportState) {
            is ExportState.Success -> {
                val result = snackbarHostState.showSnackbar(
                    message = "Report exported to Downloads",
                    actionLabel = "Open"
                )
                if (result == SnackbarResult.ActionPerformed) {
                    openDocx(context, state.uri)
                }
                viewModel.consumeExportState()
            }
            is ExportState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.consumeExportState()
            }
            else -> Unit
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Snagging Report") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ListFabs(
                sortOrder = sortOrder,
                isExporting = exportState is ExportState.Loading,
                onSortChanged = viewModel::setSortOrder,
                onExport = { requestExport() },
                onAdd = onAddEntry
            )
        }
    ) { padding ->
        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No snags yet. Tap + to add one.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 12.dp,
                    vertical = 8.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(entries, key = { it.id }) { entry ->
                    SnagListItem(entry = entry, onClick = { onOpenEntry(entry.id) })
                }
                item { Spacer(modifier = Modifier.height(72.dp)) }
            }
        }
    }
}

@Composable
private fun ListFabs(
    sortOrder: SortOrder,
    isExporting: Boolean,
    onSortChanged: (SortField, SortDirection) -> Unit,
    onExport: () -> Unit,
    onAdd: () -> Unit
) {
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SortFab(sortOrder = sortOrder, onSortChanged = onSortChanged)

        SmallFloatingActionButton(onClick = { if (!isExporting) onExport() }) {
            Icon(Icons.Filled.Description, contentDescription = "Export to Word")
        }

        FloatingActionButton(onClick = onAdd) {
            Icon(Icons.Filled.Add, contentDescription = "Add snag")
        }
    }
}

@Composable
private fun SortFab(
    sortOrder: SortOrder,
    onSortChanged: (SortField, SortDirection) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var pendingField by remember { mutableStateOf<SortField?>(null) }

    Box {
        SmallFloatingActionButton(onClick = { menuExpanded = true }) {
            Icon(Icons.Filled.Sort, contentDescription = "Sort order")
        }

        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            SortField.values().forEach { field ->
                DropdownMenuItem(
                    text = { Text(field.label) },
                    trailingIcon = {
                        if (sortOrder.field == field) {
                            Icon(
                                if (sortOrder.direction == SortDirection.ASCENDING)
                                    Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                                contentDescription = null
                            )
                        }
                    },
                    onClick = {
                        menuExpanded = false
                        pendingField = field
                    }
                )
            }
        }

        DropdownMenu(expanded = pendingField != null, onDismissRequest = { pendingField = null }) {
            DropdownMenuItem(
                text = { Text("Ascending") },
                onClick = {
                    pendingField?.let { onSortChanged(it, SortDirection.ASCENDING) }
                    pendingField = null
                }
            )
            DropdownMenuItem(
                text = { Text("Descending") },
                onClick = {
                    pendingField?.let { onSortChanged(it, SortDirection.DESCENDING) }
                    pendingField = null
                }
            )
        }
    }
}

@Composable
private fun SnagListItem(entry: SnagEntity, onClick: () -> Unit) {
    androidx.compose.material3.Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(6.dp))
            ) {
                if (entry.imagePath != null) {
                    AsyncImage(
                        model = File(entry.imagePath),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        error = painterResource(R.drawable.ic_photo_placeholder)
                    )
                } else {
                    androidx.compose.foundation.Image(
                        painter = painterResource(R.drawable.ic_photo_placeholder),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(entry.buildingName.ifBlank { "(No building name)" }, fontWeight = FontWeight.Bold)
                val subtitle = listOf(entry.level, entry.roomLocation).filter { it.isNotBlank() }.joinToString(" • ")
                if (subtitle.isNotBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall)
                }
                if (entry.description.isNotBlank()) {
                    Text(
                        firstWords(entry.description, 15),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(ratingColor(entry.rating))
            )
        }
    }
}

private fun firstWords(text: String, wordCount: Int): String {
    val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return if (words.size <= wordCount) words.joinToString(" ")
    else words.take(wordCount).joinToString(" ") + "…"
}

private fun ratingColor(rating: Rating): Color = when (rating) {
    Rating.RED -> Color(0xFFC00000)
    Rating.AMBER -> Color(0xFFE2A100)
    Rating.GREEN -> Color(0xFF1E7B34)
}

private fun openDocx(context: android.content.Context, uri: android.net.Uri) {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, DocxExporter.MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No app found to open Word documents", Toast.LENGTH_LONG).show()
    }
}
