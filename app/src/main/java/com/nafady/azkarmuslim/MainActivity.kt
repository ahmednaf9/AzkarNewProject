package com.nafady.azkarmuslim

import android.content.Context
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Image as ImageSymbol
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

private val Green = Color(0xFF087A45)
private val DeepGreen = Color(0xFF063D2B)
private val PaleGreen = Color(0xFFFFF4D9)
private val Gold = Color(0xFFD8AE52)
private val AppBackground = Color(0xFFF3E8CF)

private fun String.toArabicDigits(): String = map { ch ->
    if (ch in '0'..'9') ('٠'.code + (ch - '0')).toChar() else ch
}.joinToString("")

private fun String.toEnglishDigits(): String = map { ch ->
    if (ch in '٠'..'٩') ('0'.code + (ch - '٠')).toChar() else ch
}.joinToString("")

private fun Int.toArabicDigits(): String = toString().toArabicDigits()

private data class Dhikr(val id: String, val text: String, val target: Int, val count: Int = 0)
private data class PhotoDhikr(val id: String, val path: String, val target: Int, val count: Int = 0)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AzkarMuslimApp() }
    }
}

@Composable
private fun AzkarMuslimApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("azkar_muslim_v1", Context.MODE_PRIVATE) }
    val database = remember { AzkarDatabase(context) }
    var textItems by remember { mutableStateOf(database.loadTextItems(prefs)) }
    var photoItems by remember { mutableStateOf(database.loadPhotoItems(prefs)) }
    var tab by remember { mutableIntStateOf(0) }
    var selectedText by remember { mutableIntStateOf(0) }
    var selectedPhoto by remember { mutableIntStateOf(0) }
    var editorOpen by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var editText by remember { mutableStateOf("") }
    var editTarget by remember { mutableStateOf("٣٣") }
    var photoTargetDialog by remember { mutableStateOf(false) }
    var pendingPhotoPath by remember { mutableStateOf<String?>(null) }
    var targetPhotoValue by remember { mutableStateOf("٣") }
    var showSettings by remember { mutableStateOf(false) }
    var vibration by remember { mutableStateOf(prefs.getBoolean("vibration", true)) }
    var autoRestart by remember { mutableStateOf(prefs.getBoolean("auto_restart", true)) }
    var message by remember { mutableStateOf<String?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    var photoFullscreen by remember { mutableStateOf(false) }

    fun saveTexts(list: List<Dhikr>) {
        textItems = list
        database.saveTexts(list)
        selectedText = selectedText.coerceIn(0, (list.size - 1).coerceAtLeast(0))
    }
    fun savePhotos(list: List<PhotoDhikr>) {
        photoItems = list
        database.savePhotos(list)
        selectedPhoto = selectedPhoto.coerceIn(0, (list.size - 1).coerceAtLeast(0))
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri: Uri? ->
        if (treeUri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(treeUri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val folder = DocumentFile.fromTreeUri(context, treeUri)
                val imageFiles = folder?.listFiles()?.filter { file ->
                    file.isFile && (file.type?.startsWith("image/") == true || file.name?.substringAfterLast('.', "")?.lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic"))
                }.orEmpty()
                val importedItems = mutableListOf<PhotoDhikr>()
                imageFiles.forEach { imageFile ->
                    try {
                        val destination = File(context.filesDir, "dhikr_${UUID.randomUUID()}.img")
                        context.contentResolver.openInputStream(imageFile.uri)?.use { input -> destination.outputStream().use { output -> input.copyTo(output) } }
                        if (destination.exists() && destination.length() > 0) importedItems.add(PhotoDhikr(UUID.randomUUID().toString(), destination.absolutePath, 3)) else destination.delete()
                    } catch (_: Exception) { }
                }
                if (importedItems.isNotEmpty()) {
                    val updated = photoItems + importedItems
                    savePhotos(updated)
                    selectedPhoto = updated.size - importedItems.size
                    message = "تم تحميل ${importedItems.size.toString().toArabicDigits()} صورة بنجاح"
                } else message = "لم يتم العثور على صور في المجلد المحدد"
            } catch (_: Exception) { message = "تعذر تحميل صور المجلد" }
        }
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try {
                val destination = File(context.filesDir, "dhikr_${UUID.randomUUID()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input -> destination.outputStream().use { output -> input.copyTo(output) } }
                if (destination.exists() && destination.length() > 0) {
                    pendingPhotoPath = destination.absolutePath
                    targetPhotoValue = "٣"
                    photoTargetDialog = true
                } else message = "تعذر حفظ الصورة"
            } catch (_: Exception) { message = "تعذر قراءة الصورة" }
        }
    }

    val rootView = LocalView.current
    DisposableEffect(photoFullscreen) {
        val activityWindow = (context as? ComponentActivity)?.window
        val decorView = activityWindow?.decorView
        val previousVisibility = decorView?.systemUiVisibility ?: 0
        if (photoFullscreen && decorView != null) {
            decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
        }
        onDispose {
            if (decorView != null) decorView.systemUiVisibility = previousVisibility
        }
    }
    BackHandler(enabled = photoFullscreen) { photoFullscreen = false }

    MaterialTheme(colorScheme = lightColorScheme(primary = Green, secondary = DeepGreen, background = AppBackground, surface = Color(0xFFFFFBF1))) {
        Scaffold(containerColor = if (photoFullscreen) Color.Black else AppBackground, snackbarHost = { SnackbarHost(remember { SnackbarHostState() }) }) { padding ->
            if (photoFullscreen && tab == 1 && photoItems.isNotEmpty()) {
                val fullIndex = selectedPhoto.coerceIn(photoItems.indices)
                val fullItem = photoItems[fullIndex]
                Box(
                    Modifier.fillMaxSize().background(Color.Black)
                        .pointerInput(selectedPhoto, photoItems.size) {
                            detectTapGestures(onDoubleTap = { photoFullscreen = false })
                        }
                        .pointerInput(selectedPhoto, photoItems.size) {
                            var dragDistance = 0f
                            detectVerticalDragGestures(
                                onVerticalDrag = { change, amount ->
                                    change.consume()
                                    dragDistance += amount
                                },
                                onDragEnd = {
                                    if (dragDistance < -60f && photoItems.isNotEmpty()) selectedPhoto = (selectedPhoto + 1) % photoItems.size
                                    else if (dragDistance > 60f && photoItems.isNotEmpty()) selectedPhoto = (selectedPhoto - 1 + photoItems.size) % photoItems.size
                                    dragDistance = 0f
                                },
                                onDragCancel = { dragDistance = 0f }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    StoredPhoto(fullItem.path, Modifier.fillMaxSize())
                }
            } else {
            Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(DeepGreen, Color(0xFF176B49), AppBackground))).padding(padding).padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().background(DeepGreen, RoundedCornerShape(22.dp)).padding(horizontal = 8.dp, vertical = 5.dp)) {
                    IconButton(onClick = { showSettings = true }, modifier = Modifier.align(Alignment.CenterStart)) { Icon(Icons.Default.Settings, "الإعدادات", tint = Gold) }
                    Text("أذكاري المفضلة", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, color = Gold, modifier = Modifier.align(Alignment.Center))
                }
                Spacer(Modifier.height(10.dp))
                Card(Modifier.fillMaxSize(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBF1)), border = BorderStroke(1.dp, Gold), elevation = CardDefaults.cardElevation(8.dp)) {
                    Column(Modifier.fillMaxSize().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("ذكر مكتوب") }, leadingIcon = { Icon(Icons.Default.TextFields, null) }, modifier = Modifier.weight(1f))
                            FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("ذكر بالصورة") }, leadingIcon = { Icon(Icons.Default.ImageSymbol, null) }, modifier = Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(10.dp))
                        if (tab == 0) {
                            if (textItems.isEmpty()) EmptyState("لا توجد أذكار مكتوبة", "أضف ذكراً جديداً للبدء")
                            else {
                                val index = selectedText.coerceIn(textItems.indices)
                                val item = textItems[index]
                                Box(Modifier.fillMaxWidth()) {
                                    Text("✦  الذكر ${(index + 1).toArabicDigits()} / ${textItems.size.toArabicDigits()}  ✦", color = DeepGreen, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center))
                                    Box(Modifier.align(Alignment.CenterEnd)) {
                                        IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "خيارات الذكر", tint = DeepGreen) }
                                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                            DropdownMenuItem(text = { Text("إضافة ذكر") }, leadingIcon = { Icon(Icons.Default.Add, null) }, onClick = { menuExpanded = false; editingId = null; editText = ""; editTarget = "٣٣"; editorOpen = true })
                                            DropdownMenuItem(text = { Text("تعديل الذكر") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menuExpanded = false; editingId = item.id; editText = item.text; editTarget = item.target.toString().toArabicDigits(); editorOpen = true })
                                            DropdownMenuItem(text = { Text("حذف الذكر") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { menuExpanded = false; saveTexts(textItems.filterNot { it.id == item.id }) })
                                        }
                                    }
                                }
                                Spacer(Modifier.weight(1f))
                                Box(
                                    modifier = Modifier.fillMaxWidth().weight(2f).pointerInput(selectedText, textItems.size) {
                                        var dragDistance = 0f
                                        detectVerticalDragGestures(
                                            onVerticalDrag = { change, amount -> change.consume(); dragDistance += amount },
                                            onDragEnd = {
                                                if (dragDistance < -60f && textItems.isNotEmpty()) selectedText = (selectedText + 1) % textItems.size
                                                else if (dragDistance > 60f && textItems.isNotEmpty()) selectedText = (selectedText - 1 + textItems.size) % textItems.size
                                                dragDistance = 0f
                                            },
                                            onDragCancel = { dragDistance = 0f }
                                        )
                                    },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        item.text,
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                                        textAlign = TextAlign.Center,
                                        fontSize = 25.sp,
                                        lineHeight = 38.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF151515)
                                    )
                                }
                                Counter(item.count, item.target) {
                                    if (vibration) vibrateDevice(context)
                                    val next = textItems.toMutableList()
                                    val count = item.count + 1
                                    if (count >= item.target && autoRestart) {
                                        next[index] = item.copy(count = 0)
                                        saveTexts(next)
                                        selectedText = (index + 1) % textItems.size
                                    } else {
                                        next[index] = item.copy(count = count.coerceAtMost(item.target))
                                        saveTexts(next)
                                    }
                                }
                                Spacer(Modifier.weight(1f))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    OutlinedButton(onClick = { selectedText = (index - 1 + textItems.size) % textItems.size }, modifier=Modifier.weight(1f)) { Text("السابق") }
                                    OutlinedButton(onClick = { selectedText = (index + 1) % textItems.size }, modifier=Modifier.weight(1f)) { Text("التالي") }
                                }
                                OutlinedButton(
                                    onClick = { saveTexts(textItems.mapIndexed { i, dhikr -> if (i == index) dhikr.copy(count = 0) else dhikr }) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("تصفير العداد")
                                }
                            }
                        } else {
                            if (photoItems.isEmpty()) {
                                EmptyState("لا توجد صور أذكار", "اختر صورة من هاتفك وأدخل عدد التكرار")
                                Button(onClick = { photoPicker.launch("image/*") }) { Icon(Icons.Default.Add, null); Text("اختيار صورة") }
                                OutlinedButton(onClick = { folderPicker.launch(null) }) { Icon(Icons.Default.ImageSymbol, null); Text("تحميل مجلد صور كامل") }
                            } else {
                                val index = selectedPhoto.coerceIn(photoItems.indices)
                                val item = photoItems[index]
                                Box(Modifier.fillMaxWidth()) {
                                    Text("✦  الذكر ${(index + 1).toArabicDigits()} / ${photoItems.size.toArabicDigits()}  ✦", color = Green, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center))
                                    Box(Modifier.align(Alignment.CenterEnd)) {
                                        IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "خيارات الصورة", tint = Green) }
                                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                            DropdownMenuItem(text = { Text("تحميل مجلد صور كامل") }, leadingIcon = { Icon(Icons.Default.ImageSymbol, null) }, onClick = { menuExpanded = false; folderPicker.launch(null) })
                                            DropdownMenuItem(text = { Text("إضافة صورة") }, leadingIcon = { Icon(Icons.Default.Add, null) }, onClick = { menuExpanded = false; photoPicker.launch("image/*") })
                                            DropdownMenuItem(text = { Text("تعديل العدد") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menuExpanded = false; targetPhotoValue = item.target.toString().toArabicDigits(); pendingPhotoPath = item.path; photoTargetDialog = true })
                                            DropdownMenuItem(text = { Text("حذف الصورة") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { menuExpanded = false; File(item.path).delete(); savePhotos(photoItems.filterNot { it.id == item.id }) })
                                        }
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                Box(
                                    Modifier.weight(1f).fillMaxWidth().pointerInput(selectedPhoto, photoItems.size) {
                                        awaitEachGesture {
                                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                            val pointerId = down.id
                                            var totalDrag = 0f
                                            var lastY = down.position.y
                                            var tracking = true
                                            while (tracking) {
                                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                                if (event.changes.count { it.pressed } > 1) { tracking = false; continue }
                                                val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                                                totalDrag += change.position.y - lastY
                                                lastY = change.position.y
                                                if (!change.pressed) {
                                                    if (totalDrag < -60f && photoItems.isNotEmpty()) selectedPhoto = (selectedPhoto + 1) % photoItems.size
                                                    else if (totalDrag > 60f && photoItems.isNotEmpty()) selectedPhoto = (selectedPhoto - 1 + photoItems.size) % photoItems.size
                                                    tracking = false
                                                }
                                            }
                                        }
                                    },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(Modifier.fillMaxSize().pointerInput(item.path) {
                                        detectTapGestures(onDoubleTap = { photoFullscreen = true })
                                    }) {
                                        StoredPhoto(item.path, Modifier.fillMaxSize())
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Counter(item.count, item.target) {
                                    if (vibration) vibrateDevice(context)
                                    val next = photoItems.toMutableList(); val count = item.count + 1
                                    if (count >= item.target && autoRestart) {
                                        next[index] = item.copy(count = 0)
                                        savePhotos(next)
                                        selectedPhoto = (index + 1) % photoItems.size
                                    } else {
                                        next[index] = item.copy(count = count.coerceAtMost(item.target))
                                        savePhotos(next)
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    OutlinedButton(onClick = { selectedPhoto=(index-1+photoItems.size)%photoItems.size }, modifier=Modifier.weight(1f)) { Text("السابق") }
                                    OutlinedButton(onClick = { selectedPhoto=(index+1)%photoItems.size }, modifier=Modifier.weight(1f)) { Text("التالي") }
                                }
                                OutlinedButton(
                                    onClick = { savePhotos(photoItems.mapIndexed { i, dhikr -> if (i == index) dhikr.copy(count = 0) else dhikr }) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("تصفير العداد")
                                }
                            }
                        }
                    }
                }
            }
            }
        }

        if (editorOpen) AlertDialog(onDismissRequest = { editorOpen = false }, title = { Text(if (editingId == null) "إضافة ذكر جديد" else "تعديل الذكر") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = editText, onValueChange = { editText = it }, label = { Text("نص الذكر") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                OutlinedTextField(value = editTarget, onValueChange = { editTarget = it.filter(Char::isDigit).toArabicDigits().take(7) }, label = { Text("عدد التكرار") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            }
        }, confirmButton = { TextButton(onClick = {
            val target = editTarget.toEnglishDigits().toIntOrNull()?.coerceAtLeast(1)
            if (editText.isNotBlank() && target != null) {
                val list = textItems.toMutableList()
                if (editingId == null) { list.add(Dhikr(UUID.randomUUID().toString(), editText.trim(), target)); selectedText = list.lastIndex }
                else { val i=list.indexOfFirst { it.id == editingId }; if(i >= 0) list[i]=list[i].copy(text=editText.trim(), target=target, count=0) }
                saveTexts(list); editorOpen = false
            }
        }) { Text("حفظ") } }, dismissButton = { TextButton(onClick = { editorOpen = false }) { Text("إلغاء") } })

        if (photoTargetDialog) AlertDialog(onDismissRequest = { photoTargetDialog = false; pendingPhotoPath = null }, title = { Text("عدد مرات تكرار الصورة") }, text = {
            OutlinedTextField(value = targetPhotoValue, onValueChange = { targetPhotoValue=it.filter(Char::isDigit).toArabicDigits().take(7) }, label = { Text("العدد المطلوب") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        }, confirmButton = { TextButton(onClick = {
            val value=targetPhotoValue.toEnglishDigits().toIntOrNull()?.coerceAtLeast(1)
            val path=pendingPhotoPath
            if(value != null && path != null) {
                val list=photoItems.toMutableList(); val existing=list.indexOfFirst { it.path == path }
                if(existing >= 0) list[existing]=list[existing].copy(target=value,count=0)
                else { list.add(PhotoDhikr(UUID.randomUUID().toString(),path,value)); selectedPhoto=list.lastIndex }
                savePhotos(list); photoTargetDialog=false; pendingPhotoPath=null
            }
        }) { Text("حفظ") } }, dismissButton = { TextButton(onClick = { photoTargetDialog=false; pendingPhotoPath=null }) { Text("إلغاء") } })

        if (showSettings) AlertDialog(onDismissRequest = { showSettings=false }, title = { Text("الإعدادات") }, text = {
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) { Text("إعادة العداد تلقائياً بعد اكتمال العدد", Modifier.weight(1f)); Switch(autoRestart, { autoRestart=it; prefs.edit().putBoolean("auto_restart",it).apply() }) }
                Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) { Text("اهتزاز عند الضغط", Modifier.weight(1f)); Switch(vibration, { vibration=it; prefs.edit().putBoolean("vibration",it).apply() }) }
            }
        }, confirmButton = { TextButton(onClick = { showSettings=false }) { Text("تم") } })

        message?.let { msg -> AlertDialog(onDismissRequest={message=null}, title={Text("أذكاري المفضلة")}, text={Text(msg)}, confirmButton={TextButton(onClick={message=null}){Text("حسناً")}}) }
    }
}

@Composable
private fun Counter(count: Int, target: Int, onIncrement: () -> Unit) {
    Box(
        modifier = Modifier
            .size(116.dp)
            .clickable(onClick = onIncrement)
            .background(Gold, CircleShape)
            .padding(4.dp)
            .background(DeepGreen, CircleShape)
            .padding(3.dp)
            .background(Color(0xFFFFF6DF), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = count.toString().toArabicDigits(),
                fontSize = 35.sp,
                color = DeepGreen,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "/ ${target.toString().toArabicDigits()}",
                fontSize = 19.sp,
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().heightIn(min = 180.dp), horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.Center) {
        Text(title, fontSize=22.sp, color=Green, fontWeight=FontWeight.Bold)
        Spacer(Modifier.height(8.dp)); Text(subtitle, color=Color.Gray, textAlign=TextAlign.Center)
    }
}

@Composable
private fun StoredPhoto(path: String, modifier: Modifier = Modifier) {
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue=null, key1=path) {
        value=withContext(Dispatchers.IO) { try { BitmapFactory.decodeFile(path) } catch (_: Exception) { null } }
    }
    var scale by remember(path) { mutableFloatStateOf(1f) }
    var offsetX by remember(path) { mutableFloatStateOf(0f) }
    var offsetY by remember(path) { mutableFloatStateOf(0f) }
    val zoomState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offsetX += panChange.x
        offsetY += panChange.y
        if (scale <= 1f) { offsetX = 0f; offsetY = 0f }
    }
    if(bitmap != null) Image(
        bitmap!!.asImageBitmap(),
        contentDescription = "صورة الذكر - يمكن تكبيرها بإصبعين",
        modifier = modifier.graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY).transformable(zoomState),
        contentScale = ContentScale.Fit
    )
    else Box(modifier.background(Color(0xFFF4F4F4), RoundedCornerShape(16.dp)), contentAlignment=Alignment.Center) { Text("تعذر عرض الصورة", color=Color.Gray) }
}

private fun vibrateDevice(context: Context) {
    try {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(25L, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(25L)
        }
    } catch (_: Exception) {
        // Vibration is optional; counting must continue if the device has no vibrator.
    }
}

private fun loadLegacyTextItems(prefs: android.content.SharedPreferences): List<Dhikr> {
    val defaults = listOf(
        Dhikr("1", "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ", 100),
        Dhikr("2", "سُبْحَانَ اللَّهِ الْعَظِيمِ", 100),
        Dhikr("3", "لَا إِلَهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ", 100),
        Dhikr("4", "الْحَمْدُ لِلَّهِ", 33),
        Dhikr("5", "اللَّهُ أَكْبَرُ", 33),
        Dhikr("6", "أَسْتَغْفِرُ اللَّهَ وَأَتُوبُ إِلَيْهِ", 100),
        Dhikr("7", "اللَّهُمَّ صَلِّ وَسَلِّمْ عَلَى نَبِيِّنَا مُحَمَّدٍ", 10),
        Dhikr("8", "لَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِاللَّهِ", 100)
    )
    return try { prefs.getString("texts", null)?.let { raw -> val a=JSONArray(raw); (0 until a.length()).map { val o=a.getJSONObject(it); Dhikr(o.optString("id",UUID.randomUUID().toString()),o.getString("text"),o.getInt("target"),o.optInt("count",0)) } } ?: defaults } catch (_: Exception) { defaults }
}

private fun loadLegacyPhotoItems(prefs: android.content.SharedPreferences): List<PhotoDhikr> = try {
    prefs.getString("photos", null)?.let { raw -> val a=JSONArray(raw); (0 until a.length()).map { val o=a.getJSONObject(it); PhotoDhikr(o.getString("id"),o.getString("path"),o.getInt("target"),o.optInt("count",0)) } } ?: emptyList()
} catch (_: Exception) { emptyList() }


/**
 * SQLite-backed storage. Each dhikr is stored as its own row instead of one
 * growing SharedPreferences JSON value. Existing installations are migrated.
 */
private class AzkarDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "azkar_library.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE text_dhikr (
                id TEXT PRIMARY KEY NOT NULL,
                text TEXT NOT NULL,
                target INTEGER NOT NULL,
                count INTEGER NOT NULL
            )""".trimIndent()
        )
        db.execSQL(
            """CREATE TABLE photo_dhikr (
                id TEXT PRIMARY KEY NOT NULL,
                path TEXT NOT NULL,
                target INTEGER NOT NULL,
                count INTEGER NOT NULL
            )""".trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Add explicit schema migrations here if the database structure changes.
    }

    fun loadTextItems(prefs: android.content.SharedPreferences): List<Dhikr> {
        val rows = mutableListOf<Dhikr>()
        readableDatabase.query("text_dhikr", arrayOf("id", "text", "target", "count"),
            null, null, null, null, "rowid ASC").use { c ->
            while (c.moveToNext()) rows.add(Dhikr(c.getString(0), c.getString(1), c.getInt(2), c.getInt(3)))
        }
        if (rows.isEmpty() && !prefs.getBoolean("texts_migrated_to_sqlite", false)) {
            val oldItems = loadLegacyTextItems(prefs)
            saveTexts(oldItems)
            prefs.edit().putBoolean("texts_migrated_to_sqlite", true).apply()
            return oldItems
        }
        return rows
    }

    fun loadPhotoItems(prefs: android.content.SharedPreferences): List<PhotoDhikr> {
        val rows = mutableListOf<PhotoDhikr>()
        readableDatabase.query("photo_dhikr", arrayOf("id", "path", "target", "count"),
            null, null, null, null, "rowid ASC").use { c ->
            while (c.moveToNext()) rows.add(PhotoDhikr(c.getString(0), c.getString(1), c.getInt(2), c.getInt(3)))
        }
        if (rows.isEmpty() && !prefs.getBoolean("photos_migrated_to_sqlite", false)) {
            val oldItems = loadLegacyPhotoItems(prefs)
            savePhotos(oldItems)
            prefs.edit().putBoolean("photos_migrated_to_sqlite", true).apply()
            return oldItems
        }
        return rows
    }

    fun saveTexts(items: List<Dhikr>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val keep = items.mapTo(HashSet()) { it.id }
            val existing = mutableListOf<String>()
            db.query("text_dhikr", arrayOf("id"), null, null, null, null, null).use { c ->
                while (c.moveToNext()) existing.add(c.getString(0))
            }
            existing.filterNot { it in keep }.forEach { db.delete("text_dhikr", "id = ?", arrayOf(it)) }
            items.forEach { item ->
                val values = ContentValues().apply {
                    put("id", item.id); put("text", item.text)
                    put("target", item.target); put("count", item.count)
                }
                db.insertWithOnConflict("text_dhikr", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun savePhotos(items: List<PhotoDhikr>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val keep = items.mapTo(HashSet()) { it.id }
            val existing = mutableListOf<String>()
            db.query("photo_dhikr", arrayOf("id"), null, null, null, null, null).use { c ->
                while (c.moveToNext()) existing.add(c.getString(0))
            }
            existing.filterNot { it in keep }.forEach { db.delete("photo_dhikr", "id = ?", arrayOf(it)) }
            items.forEach { item ->
                val values = ContentValues().apply {
                    put("id", item.id); put("path", item.path)
                    put("target", item.target); put("count", item.count)
                }
                db.insertWithOnConflict("photo_dhikr", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
}
