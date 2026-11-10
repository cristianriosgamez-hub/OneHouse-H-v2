package com.onehouse.app.feature.more

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onehouse.app.design.AzulClaro
import com.onehouse.app.design.AzulOneHouse
import com.onehouse.app.design.FondoInferior
import com.onehouse.app.design.FondoSuperior
import com.onehouse.app.design.TextoPrincipal
import com.onehouse.app.design.TextoSecundario
import java.io.Closeable
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Visor nativo sin WebView, apps externas, servidor ni permisos de almacenamiento. */
@Composable
fun DocumentPdfScreen(document: OneHouseDocument, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val readerResult = remember(document) { runCatching { BundledPdfReader(context, document.assetFile) } }
    val reader = readerResult.getOrNull()
    DisposableEffect(reader) { onDispose { reader?.close() } }

    var pageIndex by rememberSaveable(document.name) { mutableIntStateOf(0) }
    var seekPosition by remember(document) { mutableIntStateOf(0) }
    var zoomLevel by remember(document) { mutableIntStateOf(0) }
    val zoom = when (zoomLevel) { 1 -> 1.5f; 2 -> 2f; else -> 1f }
    val pages = reader?.pageCount ?: 0
    val maxIndex = (pages - 1).coerceAtLeast(0)
    LaunchedEffect(maxIndex) { if (pageIndex > maxIndex) pageIndex = maxIndex }

    val vScroll = rememberScrollState()
    val hScroll = rememberScrollState()
    LaunchedEffect(pageIndex, zoomLevel, document) {
        vScroll.scrollTo(0)
        hScroll.scrollTo(0)
    }
    LaunchedEffect(pageIndex) { seekPosition = pageIndex }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(FondoSuperior, FondoInferior)))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = TextoPrincipal)
            }
            Column(modifier = Modifier.weight(1f).padding(start = 5.dp)) {
                Text(document.heading, color = TextoPrincipal, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Text("Documento PDF · lectura sin conexión", color = TextoSecundario, fontSize = 12.sp)
            }
        }

        if (reader == null) {
            Text(
                "No se ha podido abrir el PDF: ${readerResult.exceptionOrNull()?.message ?: "Error de archivo"}",
                color = TextoPrincipal,
                modifier = Modifier.padding(22.dp)
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { pageIndex = (pageIndex - 1).coerceAtLeast(0) }, enabled = pageIndex > 0) {
                    Icon(Icons.Rounded.ChevronLeft, contentDescription = "Página anterior", tint = if (pageIndex > 0) AzulClaro else TextoSecundario)
                }
                Text("Página ${pageIndex + 1} de $pages", color = TextoPrincipal, fontWeight = FontWeight.Medium)
                IconButton(onClick = { pageIndex = (pageIndex + 1).coerceAtMost(maxIndex) }, enabled = pageIndex < maxIndex) {
                    Icon(Icons.Rounded.ChevronRight, contentDescription = "Página siguiente", tint = if (pageIndex < maxIndex) AzulClaro else TextoSecundario)
                }
                IconButton(onClick = { zoomLevel = (zoomLevel - 1).coerceAtLeast(0) }, enabled = zoomLevel > 0) {
                    Icon(Icons.Rounded.ZoomOut, contentDescription = "Reducir", tint = AzulClaro)
                }
                Text("${(zoom * 100).roundToInt()}%", color = TextoSecundario, fontSize = 12.sp)
                IconButton(onClick = { zoomLevel = (zoomLevel + 1).coerceAtMost(2) }, enabled = zoomLevel < 2) {
                    Icon(Icons.Rounded.ZoomIn, contentDescription = "Ampliar", tint = AzulClaro)
                }
            }

            if (pages > 1) {
                Slider(
                    value = seekPosition.toFloat(),
                    onValueChange = { seekPosition = it.roundToInt().coerceIn(0, maxIndex) },
                    onValueChangeFinished = { pageIndex = seekPosition },
                    valueRange = 0f..maxIndex.toFloat(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(24.dp)
                )
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .background(Color(0xFF0B1A28), RoundedCornerShape(15.dp))
            ) {
                val density = LocalDensity.current
                val requestedPixels = with(density) { (maxWidth.toPx() * zoom).roundToInt() }.coerceIn(320, 2300)
                var picture by remember(document, pageIndex, requestedPixels) { mutableStateOf<Bitmap?>(null) }
                var renderError by remember(document, pageIndex, requestedPixels) { mutableStateOf<String?>(null) }
                LaunchedEffect(reader, pageIndex, requestedPixels) {
                    renderError = null
                    picture = null
                    val result = withContext(Dispatchers.IO) {
                        runCatching { reader.render(pageIndex, requestedPixels) }
                    }
                    result.onSuccess { picture = it }
                        .onFailure { renderError = it.message ?: "No se pudo representar la página" }
                }

                Box(
                    modifier = Modifier.fillMaxSize().horizontalScroll(hScroll).verticalScroll(vScroll),
                    contentAlignment = Alignment.TopCenter
                ) {
                    when {
                        renderError != null -> Text(renderError.orEmpty(), color = TextoPrincipal, modifier = Modifier.padding(20.dp))
                        picture == null -> CircularProgressIndicator(color = AzulClaro, modifier = Modifier.padding(30.dp))
                        else -> {
                            val bitmap = picture!!
                            val bitmapWidth = with(density) { bitmap.width.toDp() }
                            val bitmapHeight = with(density) { bitmap.height.toDp() }
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Página ${pageIndex + 1} del documento ${document.heading}",
                                modifier = Modifier.width(bitmapWidth).height(bitmapHeight)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** PdfRenderer requiere un descriptor seekable; los assets se copian temporalmente al caché privado. */
private class BundledPdfReader(context: Context, assetName: String) : Closeable {
    private val tempFile: File = File.createTempFile("onehouse_pdf_", ".pdf", context.cacheDir)
    private val descriptor: ParcelFileDescriptor
    private val renderer: PdfRenderer
    private var closed = false

    init {
        try {
            context.assets.open(assetName).use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }
            descriptor = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = try {
                PdfRenderer(descriptor)
            } catch (error: Exception) {
                descriptor.close()
                throw error
            }
        } catch (error: Exception) {
            tempFile.delete()
            throw error
        }
    }

    val pageCount: Int get() = renderer.pageCount

    @Synchronized
    fun render(index: Int, widthPixels: Int): Bitmap {
        check(!closed) { "El documento ya está cerrado" }
        renderer.openPage(index).use { page ->
            val width = widthPixels.coerceIn(320, 2300)
            val height = (width.toFloat() * page.height / page.width).roundToInt().coerceIn(320, 3300)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bitmap
        }
    }

    @Synchronized
    override fun close() {
        if (!closed) {
            closed = true
            renderer.close()
            descriptor.close()
            tempFile.delete()
        }
    }
}
