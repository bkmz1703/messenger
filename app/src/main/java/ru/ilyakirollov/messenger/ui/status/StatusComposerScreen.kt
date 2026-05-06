package ru.ilyakirollov.messenger.ui.status

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import ru.ilyakirollov.messenger.R

private val PALETTE = listOf(
    0xFF1976D2, 0xFFE53935, 0xFF8E24AA, 0xFFFB8C00, 0xFF00897B,
    0xFF3949AB, 0xFFD81B60, 0xFF6D4C41, 0xFF455A64, 0xFF0F9D58,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusComposerScreen(
    onBack: () -> Unit,
    viewModel: StatusViewModel = hiltViewModel(),
) {
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val posted by viewModel.posted.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    var text by remember { mutableStateOf("") }
    var bg by remember { mutableStateOf(PALETTE.first()) }
    var pickedImage by remember { mutableStateOf<Uri?>(null) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? -> pickedImage = uri }

    LaunchedEffect(posted) { if (posted) onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringRes(R.string.status_add)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(
                        enabled = !busy && (text.isNotBlank() || pickedImage != null),
                        onClick = {
                            val image = pickedImage
                            if (image != null) viewModel.postImage(image, text)
                            else viewModel.postText(text, bg)
                        },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .background(if (pickedImage == null) Color(bg) else Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                val image = pickedImage
                if (image != null) {
                    AsyncImage(
                        model = image,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (text.isBlank()) {
                    Text(
                        "Введите текст статуса",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 22.sp,
                    )
                } else {
                    Text(
                        text,
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }

            Spacer(Modifier.size(12.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringRes(R.string.status_caption_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            )

            Spacer(Modifier.size(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) {
                    Icon(Icons.Default.Image, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Фото")
                }
                Spacer(Modifier.width(12.dp))
                if (pickedImage == null) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(PALETTE) { color ->
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(color))
                                    .clickable { bg = color }
                                    .border(
                                        width = if (bg == color) 3.dp else 0.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = CircleShape,
                                    ),
                            )
                        }
                    }
                }
            }

            if (!error.isNullOrBlank()) {
                Spacer(Modifier.size(8.dp))
                Text(
                    error.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun stringRes(@androidx.annotation.StringRes id: Int): String =
    androidx.compose.ui.res.stringResource(id)
