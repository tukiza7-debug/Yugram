package com.telegram.clone.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.telegram.clone.data.repository.TelegramRepository
import kotlinx.coroutines.flow.collectLatest
import org.drinkless.tdlib.TdApi
import java.io.File

/**
 * Image composable backed by a TDLib [TdApi.File]. Triggers the download if
 * missing and reacts to [TdApi.UpdateFile] events via [TelegramRepository.fileFlow].
 */
@Composable
fun TdFileImage(
    file: TdApi.File?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    priority: Int = 1,
    placeholder: @Composable () -> Unit = {}
) {
    val repository = remember { TelegramRepository.getInstance() }
    val fileId = file?.id ?: 0
    var localPath by remember(fileId) { mutableStateOf(file?.local?.path?.takeIf { it.isNotEmpty() }) }

    LaunchedEffect(fileId, localPath) {
        if (file != null && localPath.isNullOrEmpty()) repository.downloadFile(file.id, priority)
    }
    LaunchedEffect(fileId) {
        repository.fileFlow.collectLatest { updated ->
            if (updated.id == fileId) {
                val p = updated.local?.path
                if (!p.isNullOrEmpty() && File(p).exists()) localPath = p
            }
        }
    }
    val p = localPath
    if (!p.isNullOrEmpty() && File(p).exists()) {
        AsyncImage(model = File(p), contentDescription = contentDescription, modifier = modifier, contentScale = contentScale)
    } else {
        placeholder()
    }
}
