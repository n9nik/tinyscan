package com.n9nik.documentscanner.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.awaitEachGesture
import androidx.compose.ui.input.pointer.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.LifecycleOwner
import com.n9nik.documentscanner.ads.BannerAd
import com.n9nik.documentscanner.domain.DocumentDetector
import com.n9nik.documentscanner.domain.DocumentProcessor
import com.n9nik.documentscanner.domain.DocumentProcessor.EnhanceMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.hypot

private enum class Screen { CAMERA, ADJUST, PAGES }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerApp(
    adsReady: Boolean,
    privacyOptionsAvailable: Boolean,
    onPrivacyOptions: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var screen by remember { mutableStateOf(Screen.CAMERA) }
    val pages = remember { mutableStateListOf<Bitmap>() }
    var captureBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var captureQuad by remember { mutableStateOf<FloatArray?>(null) }
    var enhanceMode by remember { mutableStateOf(EnhanceMode.COLOR) }
    var isWorking by remember { mutableStateOf(false) }

    fun showMessage(msg: String) {
        scope.launch { snackbarHostState.showSnackbar(msg) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TinyScan") },
                actions = {
                    if (privacyOptionsAvailable) {
                        TextButton(onClick = onPrivacyOptions) { Text("Ad privacy") }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (adsReady) BannerAd()
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (screen) {
                Screen.CAMERA -> CameraScreen(
                    pagesCount = pages.size,
                    isWorking = isWorking,
                    onCaptured = { bmp ->
                        val quad = DocumentDetector.detectQuad(bmp)
                            ?: DocumentDetector.fullImageQuad(bmp.width, bmp.height)
                        captureBitmap?.recycle()
                        captureBitmap = bmp
                        captureQuad = quad
                        enhanceMode = EnhanceMode.COLOR
                        screen = Screen.ADJUST
                    },
                    onOpenPages = { screen = Screen.PAGES },
                    onMessage = ::showMessage
                )
                Screen.ADJUST -> {
                    val bmp = captureBitmap
                    val quad = captureQuad
                    if (bmp != null && quad != null) {
                        AdjustScreen(
                            bitmap = bmp,
                            quad = quad,
                            enhanceMode = enhanceMode,
                            onEnhanceMode = { enhanceMode = it },
                            onQuadChange = { captureQuad = it },
                            onRetake = {
                                captureBitmap?.recycle()
                                captureBitmap = null
                                captureQuad = null
                                screen = Screen.CAMERA
                            },
                            onAddPage = { finalBitmap ->
                                if (pages.size >= 20) {
                                    showMessage("Max 20 pages per PDF")
                                } else {
                                    pages.add(finalBitmap)
                                    captureBitmap = null
                                    captureQuad = null
                                    screen = Screen.CAMERA
                                    showMessage("Page ${pages.size} added")
                                }
                            }
                        )
                    }
                }
                Screen.PAGES -> PagesScreen(
                    pages = pages,
                    isWorking = isWorking,
                    onRemovePage = { index ->
                        pages.getOrNull(index)?.recycle()
                        pages.removeAt(index)
                    },
                    onBack = { screen = Screen.CAMERA },
                    onSavePdf = { after ->
                        if (pages.isEmpty()) {
                            showMessage("Add at least one page first")
                        } else {
                            isWorking = true
                            scope.launch(Dispatchers.Default) {
                                try {
                                    val pdf = DocumentProcessor.createPdf(context, pages.toList())
                                    val uri = DocumentProcessor.savePdfToDownloads(context, pdf)
                                    withContext(Dispatchers.Main) {
                                        isWorking = false
                                        if (uri != null) {
                                            showMessage("Saved to Downloads/TinyScan")
                                            if (after == "share") DocumentProcessor.sharePdf(context, uri)
                                        } else {
                                            showMessage("Save failed — try again")
                                        }
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        isWorking = false
                                        showMessage("PDF failed: ${e.message}")
                                    }
                                }
                            }
                        }
                    }
                )
            }
            if (isWorking) {
                LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Camera
// ---------------------------------------------------------------------------

@Composable
private fun CameraScreen(
    pagesCount: Int,
    isWorking: Boolean,
    onCaptured: (Bitmap) -> Unit,
    onOpenPages: () -> Unit,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        permissionDenied = !granted
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(android.Manifest.permission.CAMERA)
    }

    // Gallery import works without camera permission.
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.Default) {
                val bmp = decodeUriOriented(context, uri, maxDim = 2048)
                withContext(Dispatchers.Main) {
                    if (bmp != null) onCaptured(bmp)
                    else onMessage("Couldn't read that image")
                }
            }
        }
    }

    // Bind CameraX when permission is granted.
    LaunchedEffect(hasCameraPermission, previewView) {
        val pv = previewView ?: return@LaunchedEffect
        if (!hasCameraPermission) return@LaunchedEffect
        try {
            val provider = getCameraProvider(context)
            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(pv.surfaceProvider)
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            imageCapture = capture
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture
            )
        } catch (e: Exception) {
            onMessage("Camera unavailable: ${e.message}")
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                ProcessCameraProvider.getInstance(context).get().unbindAll()
            }
        }
    }

    fun takePhoto() {
        val capture = imageCapture ?: run {
            onMessage("Camera not ready yet")
            return
        }
        val tmp = try {
            File.createTempFile("scan-", ".jpg", context.cacheDir)
        } catch (e: Exception) {
            onMessage("Couldn't create temp file")
            return
        }
        val output = ImageCapture.OutputFileOptions.Builder(tmp).build()
        capture.takePicture(
            output,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                    scope.launch(Dispatchers.Default) {
                        val bmp = decodeFileOriented(tmp, maxDim = 2048)
                        tmp.delete()
                        withContext(Dispatchers.Main) {
                            if (bmp != null) onCaptured(bmp)
                            else onMessage("Couldn't read that photo")
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    onMessage("Capture failed")
                }
            }
        )
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Point at a document and capture",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(12.dp))

        Card(
            Modifier.fillMaxWidth().weight(1f),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (hasCameraPermission) {
                    AndroidView(
                        factory = { ctx ->
                            PreviewView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                                previewView = this
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (permissionDenied) {
                    Column(
                        Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Camera permission was denied.")
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "You can still import document photos from your gallery below.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text("Requesting camera permission…")
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    importLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier.size(56.dp).background(
                    MaterialTheme.colorScheme.surfaceVariant, CircleShape
                )
            ) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = "Import from gallery")
            }

            IconButton(
                onClick = ::takePhoto,
                enabled = hasCameraPermission && !isWorking,
                modifier = Modifier.size(76.dp).background(
                    MaterialTheme.colorScheme.primary, CircleShape
                )
            ) {
                Icon(
                    Icons.Filled.PhotoCamera,
                    contentDescription = "Capture document",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            IconButton(
                onClick = onOpenPages,
                modifier = Modifier.size(56.dp).background(
                    MaterialTheme.colorScheme.surfaceVariant, CircleShape
                )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Add, contentDescription = "Pages")
                    if (pagesCount > 0) {
                        Text(
                            "$pagesCount",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            if (pagesCount == 0) "No pages yet — capture or import to begin"
            else "$pagesCount page${if (pagesCount == 1) "" else "s"} ready",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (pagesCount > 0) {
            Spacer(Modifier.height(8.dp))
            Button(onClick = onOpenPages) { Text("Review pages & make PDF") }
        }
    }
}

private suspend fun getCameraProvider(context: Context): ProcessCameraProvider =
    kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            {
                try {
                    cont.resume(future.get(), null)
                } catch (e: Exception) {
                    cont.resumeWith(Result.failure(e))
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

// ---------------------------------------------------------------------------
// Adjust (corner dragging + enhance)
// ---------------------------------------------------------------------------

@Composable
private fun AdjustScreen(
    bitmap: Bitmap,
    quad: FloatArray,
    enhanceMode: EnhanceMode,
    onEnhanceMode: (EnhanceMode) -> Unit,
    onQuadChange: (FloatArray) -> Unit,
    onRetake: () -> Unit,
    onAddPage: (Bitmap) -> Unit
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isRendering by remember { mutableStateOf(false) }
    var dragIndex by remember { mutableStateOf(-1) }

    // Re-render the cropped + enhanced preview whenever corners or mode change.
    LaunchedEffect(quad, enhanceMode) {
        isRendering = true
        val rendered = withContext(Dispatchers.Default) {
            val cropped = DocumentProcessor.perspectiveCrop(bitmap, quad)
            val enhanced = DocumentProcessor.enhance(cropped, enhanceMode)
            if (enhanced !== cropped) cropped.recycle()
            enhanced
        }
        previewBitmap?.recycle()
        previewBitmap = rendered
        isRendering = false
    }
    DisposableEffect(Unit) {
        onDispose { previewBitmap?.recycle() }
    }

    val touchRadiusPx = with(density) { 48.dp.toPx() }

    // Corner dragging must win over the screen's vertical scroll: freeze scrolling the
    // moment a finger lands on the canvas, restore it on lift. Also note the drag
    // gesture detector is keyed on Unit (not quad): re-keying on quad restarted gesture
    // detection on every corner move, which cancelled the drag immediately.
    var scrollEnabled by remember { mutableStateOf(true) }
    val scrollState = rememberScrollState()
    val quadNow by rememberUpdatedState(quad)

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(scrollState, enabled = scrollEnabled),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Drag the corners to fit the document", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown()
                        scrollEnabled = false
                        try {
                            do {
                                val event = awaitPointerEvent()
                            } while (event.changes.any { it.pressed })
                        } finally {
                            scrollEnabled = true
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val q = quadNow
                            val scale = size.width / bitmap.width
                            var best = -1
                            var bestD = touchRadiusPx
                            for (i in 0 until 4) {
                                val cx = q[i * 2] * scale
                                val cy = q[i * 2 + 1] * scale
                                val d = hypot((offset.x - cx).toDouble(), (offset.y - cy).toDouble()).toFloat()
                                if (d < bestD) {
                                    bestD = d
                                    best = i
                                }
                            }
                            dragIndex = best
                        },
                        onDragEnd = { dragIndex = -1 },
                        onDragCancel = { dragIndex = -1 },
                        onDrag = { change, _ ->
                            val idx = dragIndex
                            if (idx >= 0) {
                                val q = quadNow
                                val scale = size.width / bitmap.width
                                val updated = q.copyOf()
                                updated[idx * 2] =
                                    (change.position.x / scale).coerceIn(0f, bitmap.width.toFloat())
                                updated[idx * 2 + 1] =
                                    (change.position.y / scale).coerceIn(0f, bitmap.height.toFloat())
                                onQuadChange(updated)
                                change.consume()
                            }
                        }
                    )
                }
        ) {
            val scale = size.width / bitmap.width
            drawImage(
                bitmap.asImageBitmap(),
                dstSize = IntSize(size.width.toInt(), size.height.toInt())
            )
            val path = Path().apply {
                moveTo(quad[0] * scale, quad[1] * scale)
                lineTo(quad[2] * scale, quad[3] * scale)
                lineTo(quad[4] * scale, quad[5] * scale)
                lineTo(quad[6] * scale, quad[7] * scale)
                close()
            }
            drawPath(path, Color(0xFF4F46E5), style = Stroke(width = 3.dp.toPx()))
            for (i in 0 until 4) {
                val c = Offset(quad[i * 2] * scale, quad[i * 2 + 1] * scale)
                drawCircle(Color.White, 15.dp.toPx(), c)
                drawCircle(Color(0xFF4F46E5), 9.dp.toPx(), c)
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = enhanceMode == EnhanceMode.COLOR,
                onClick = { onEnhanceMode(EnhanceMode.COLOR) },
                label = { Text("Color") }
            )
            FilterChip(
                selected = enhanceMode == EnhanceMode.GRAYSCALE,
                onClick = { onEnhanceMode(EnhanceMode.GRAYSCALE) },
                label = { Text("Grayscale") }
            )
            FilterChip(
                selected = enhanceMode == EnhanceMode.BW,
                onClick = { onEnhanceMode(EnhanceMode.BW) },
                label = { Text("B&W") }
            )
        }

        Spacer(Modifier.height(12.dp))
        val preview = previewBitmap
        if (preview != null) {
            Card(elevation = CardDefaults.cardElevation(4.dp)) {
                androidx.compose.foundation.Image(
                    bitmap = preview.asImageBitmap(),
                    contentDescription = "Cropped preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().height(220.dp).padding(8.dp)
                )
            }
        } else if (isRendering) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onRetake,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Retake")
            }
            Button(
                onClick = {
                    val final = preview
                    if (final != null) {
                        scope.launch(Dispatchers.Default) {
                            // Hand off a copy; the screen's preview gets recycled on dispose.
                            val copy = final.copy(final.config ?: Bitmap.Config.ARGB_8888, false)
                            withContext(Dispatchers.Main) { onAddPage(copy) }
                        }
                    }
                },
                enabled = preview != null,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add page")
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Pages -> PDF
// ---------------------------------------------------------------------------

@Composable
private fun PagesScreen(
    pages: List<Bitmap>,
    isWorking: Boolean,
    onRemovePage: (Int) -> Unit,
    onBack: () -> Unit,
    onSavePdf: (String) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "${pages.size} page${if (pages.size == 1) "" else "s"}",
                style = MaterialTheme.typography.titleMedium
            )
            TextButton(onClick = onBack, enabled = !isWorking) { Text("Add more") }
        }
        Spacer(Modifier.height(8.dp))

        if (pages.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    "No pages yet. Go back and capture a document.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(pages, key = { index, _ -> index }) { index, bmp ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.foundation.Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Page ${index + 1}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(64.dp).background(
                                    MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Page ${index + 1}",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { onRemovePage(index) },
                                enabled = !isWorking
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = "Remove page")
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { onSavePdf("save") },
                enabled = pages.isNotEmpty() && !isWorking,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.Close, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Save PDF")
            }
            Button(
                onClick = { onSavePdf("share") },
                enabled = pages.isNotEmpty() && !isWorking,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.Share, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Share")
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Saved PDFs land in Downloads/TinyScan. No sign-in, no cloud, no watermark.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ---------------------------------------------------------------------------
// Bitmap decoding with EXIF orientation (camera + gallery)
// ---------------------------------------------------------------------------

private fun decodeFileOriented(file: File, maxDim: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth / sample, bounds.outHeight / sample) > maxDim) sample *= 2
    val bmp = BitmapFactory.decodeFile(
        file.absolutePath,
        BitmapFactory.Options().apply { inSampleSize = sample }
    ) ?: return null
    val orientation = runCatching {
        ExifInterface(file.absolutePath)
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    return applyOrientation(bmp, orientation)
}

private fun decodeUriOriented(context: Context, uri: Uri, maxDim: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, bounds)
    }
    var sample = 1
    while (maxOf(bounds.outWidth / sample, bounds.outHeight / sample) > maxDim) sample *= 2
    val bmp = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return null
    val orientation = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            ExifInterface(stream)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    return applyOrientation(bmp, orientation)
}

private fun applyOrientation(bmp: Bitmap, orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.postRotate(90f); matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.postRotate(270f); matrix.postScale(-1f, 1f)
        }
    }
    if (matrix.isIdentity) return bmp
    val out = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
    bmp.recycle()
    return out
}
