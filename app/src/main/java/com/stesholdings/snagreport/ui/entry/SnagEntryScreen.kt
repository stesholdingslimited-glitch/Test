package com.stesholdings.snagreport.ui.entry

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.stesholdings.snagreport.R
import com.stesholdings.snagreport.data.Rating
import com.stesholdings.snagreport.data.SnagEntity
import com.stesholdings.snagreport.ui.NEW_ENTRY_ID
import com.stesholdings.snagreport.ui.SnagViewModel
import com.stesholdings.snagreport.util.ImageStorage
import java.io.File
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnagEntryScreen(
    entryId: Long,
    viewModel: SnagViewModel,
    onDone: () -> Unit
) {
    val isNew = entryId == NEW_ENTRY_ID

    var buildingName by remember { mutableStateOf("") }
    var level by remember { mutableStateOf("") }
    var roomLocation by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var rating by remember { mutableStateOf<Rating?>(null) }
    var imagePath by remember { mutableStateOf<String?>(null) }
    var sessionCreatedPath by remember { mutableStateOf<String?>(null) }
    var loadedEntry by remember { mutableStateOf<SnagEntity?>(null) }
    var showValidationError by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(entryId) {
        if (!isNew) {
            val existing = viewModel.getEntry(entryId)
            loadedEntry = existing
            existing?.let {
                buildingName = it.buildingName
                level = it.level
                roomLocation = it.roomLocation
                description = it.description
                rating = it.rating
                imagePath = it.imagePath
            }
        }
    }

    // If the screen is left without saving, drop any photo that was captured/picked this
    // session but never persisted to an entry, so it doesn't linger as an orphan file.
    DisposableEffect(Unit) {
        onDispose { sessionCreatedPath?.let { ImageStorage.deleteStoredImage(it) } }
    }

    fun replaceImage(newPath: String) {
        sessionCreatedPath?.let { ImageStorage.deleteStoredImage(it) }
        sessionCreatedPath = newPath
        imagePath = newPath
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            coroutineScope.launch { replaceImage(viewModel.processImage(uri)) }
        }
    }

    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch { replaceImage(viewModel.processImage(uri)) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "New Snag" else "Edit Snag") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (imagePath != null) {
                    AsyncImage(
                        model = File(imagePath!!),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Image(
                        painter = painterResource(R.drawable.ic_photo_placeholder),
                        contentDescription = null,
                        modifier = Modifier.size(64.dp)
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = {
                    val uri = viewModel.createCameraCaptureUri()
                    pendingCameraUri = uri
                    cameraLauncher.launch(uri)
                }) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Camera")
                }
                OutlinedButton(onClick = { pickLauncher.launch("image/*") }) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Choose Photo")
                }
                if (imagePath != null) {
                    TextButton(onClick = {
                        if (sessionCreatedPath == imagePath) {
                            ImageStorage.deleteStoredImage(imagePath)
                            sessionCreatedPath = null
                        }
                        imagePath = null
                    }) {
                        Text("Remove")
                    }
                }
            }

            OutlinedTextField(
                value = buildingName,
                onValueChange = { buildingName = it },
                label = { Text("Building Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = level,
                onValueChange = { level = it },
                label = { Text("Level") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = roomLocation,
                onValueChange = { roomLocation = it },
                label = { Text("Room / Location") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Rating", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Rating.values().forEach { r ->
                    FilterChip(
                        selected = rating == r,
                        onClick = { rating = r },
                        label = { Text(r.label) },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(ratingColor(r))
                            )
                        }
                    )
                }
            }

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description of snag") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp)
            )

            if (showValidationError) {
                Text(
                    "Please enter a building name and select a rating.",
                    color = MaterialTheme.colorScheme.error
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (buildingName.isBlank() || rating == null) {
                            showValidationError = true
                        } else {
                            viewModel.saveEntry(
                                id = entryId,
                                imagePath = imagePath,
                                buildingName = buildingName.trim(),
                                level = level.trim(),
                                roomLocation = roomLocation.trim(),
                                rating = rating!!,
                                description = description.trim()
                            ) {
                                sessionCreatedPath = null
                                onDone()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save")
                }

                if (!isNew) {
                    OutlinedButton(onClick = {
                        // Deletes the entry's original photo (if any); a not-yet-saved
                        // replacement picked this session is cleaned up by onDispose below.
                        loadedEntry?.let { entry ->
                            viewModel.deleteEntry(entry, onDone)
                        }
                    }) {
                        Text("Delete")
                    }
                }
            }
        }
    }
}

private fun ratingColor(rating: Rating): Color = when (rating) {
    Rating.RED -> Color(0xFFC00000)
    Rating.AMBER -> Color(0xFFE2A100)
    Rating.GREEN -> Color(0xFF1E7B34)
}
