package ru.ilyakirollov.messenger.ui.call

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import ru.ilyakirollov.messenger.R
import ru.ilyakirollov.messenger.data.model.CallSession
import ru.ilyakirollov.messenger.ui.components.Avatar

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CallScreen(
    callId: String,
    isVideo: Boolean,
    isCaller: Boolean,
    onFinished: () -> Unit,
    viewModel: CallViewModel = hiltViewModel(),
) {
    val perms = rememberMultiplePermissionsState(
        buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (isVideo) add(Manifest.permission.CAMERA)
        }
    )
    val session by viewModel.session.collectAsStateWithLifecycle()
    val localTrack by viewModel.localTrack.collectAsStateWithLifecycle()
    val remoteTrack by viewModel.remoteTrack.collectAsStateWithLifecycle()
    val micEnabled by viewModel.micEnabled.collectAsStateWithLifecycle()
    val camEnabled by viewModel.cameraEnabled.collectAsStateWithLifecycle()
    val speakerOn by viewModel.speakerOn.collectAsStateWithLifecycle()

    LaunchedEffect(perms.allPermissionsGranted) {
        if (perms.allPermissionsGranted) viewModel.start()
        else perms.launchMultiplePermissionRequest()
    }

    LaunchedEffect(session?.status) {
        val status = session?.status ?: return@LaunchedEffect
        if (status == CallSession.STATUS_DECLINED ||
            status == CallSession.STATUS_ENDED ||
            status == CallSession.STATUS_MISSED
        ) {
            onFinished()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.release()
        }
    }

    val displayName = if (isCaller) session?.calleeNickname.orEmpty() else session?.callerNickname.orEmpty()
    val ringing = session?.status == CallSession.STATUS_RINGING
    val incomingNotAnswered = ringing && !isCaller

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (isVideo) {
            VideoSurface(track = remoteTrack, viewModel = viewModel, modifier = Modifier.fillMaxSize())
            VideoSurface(
                track = localTrack,
                viewModel = viewModel,
                mirror = true,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(width = 110.dp, height = 160.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Avatar(nickname = displayName, color = 0xFF455A64, size = 140.dp)
                Spacer(Modifier.size(16.dp))
                Text(
                    displayName.ifBlank { "—" },
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    statusLabel(session?.status),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 36.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                if (isCaller) stringResource(R.string.outgoing_call) else stringResource(R.string.incoming_call),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (incomingNotAnswered) {
            // Incoming pre-answer controls
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(48.dp),
            ) {
                Button(
                    onClick = { viewModel.decline() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                    ),
                    shape = CircleShape,
                ) {
                    Icon(Icons.Default.CallEnd, contentDescription = stringResource(R.string.call_decline))
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.call_decline))
                }
                Button(
                    onClick = { viewModel.answer() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = CircleShape,
                ) {
                    Text(stringResource(R.string.call_accept))
                }
            }
        } else {
            // Active-call controls
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleButton(
                    icon = if (micEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                    background = Color.White.copy(alpha = 0.15f),
                    onClick = viewModel::toggleMic,
                )
                if (isVideo) {
                    CircleButton(
                        icon = if (camEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                        background = Color.White.copy(alpha = 0.15f),
                        onClick = viewModel::toggleCamera,
                    )
                    CircleButton(
                        icon = Icons.Default.Cameraswitch,
                        background = Color.White.copy(alpha = 0.15f),
                        onClick = viewModel::switchCamera,
                    )
                } else {
                    CircleButton(
                        icon = Icons.Default.VolumeUp,
                        background = if (speakerOn) MaterialTheme.colorScheme.primary
                            else Color.White.copy(alpha = 0.15f),
                        onClick = viewModel::toggleSpeaker,
                    )
                }
                FloatingActionButton(
                    onClick = { viewModel.end() },
                    containerColor = MaterialTheme.colorScheme.tertiary,
                ) {
                    Icon(Icons.Default.CallEnd, contentDescription = stringResource(R.string.call_end))
                }
            }
        }
    }
}

private fun statusLabel(status: String?): String = when (status) {
    CallSession.STATUS_RINGING -> "Гудки…"
    CallSession.STATUS_ANSWERED -> "В разговоре"
    CallSession.STATUS_ENDED -> "Завершён"
    CallSession.STATUS_DECLINED -> "Отклонён"
    CallSession.STATUS_MISSED -> "Пропущен"
    else -> "Подключение…"
}

@Composable
private fun CircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    background: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = null, tint = Color.White)
        }
    }
}

@Composable
private fun VideoSurface(
    track: VideoTrack?,
    viewModel: CallViewModel,
    mirror: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val renderer = remember {
        SurfaceViewRenderer(context).apply {
            init(viewModel.eglContext, null)
            setEnableHardwareScaler(true)
            setMirror(mirror)
            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
        }
    }

    DisposableEffect(track) {
        track?.addSink(renderer)
        onDispose {
            track?.removeSink(renderer)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            renderer.release()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { renderer },
    )
}
