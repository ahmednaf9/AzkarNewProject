package com.nafady.azkarmuslim

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image as ImageSymbol
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
    var textItems by remember { mutableStateOf(loadTextItems(prefs)) }
    var photoItems by remember { mutableStateOf(loadPhotoItems(prefs)) }
    var tab by remember { mutableIntStateOf(0) }
    var selectedText by remember { mutableIntStateOf(0) }
    var selectedPhoto by remember { mutableIntStateOf(0) }
    var editorOpen by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var editText by remember { mutableStateOf("") }
    var editTarget by remember { mutableStateOf("33") }
    var photoTargetDialog by remember { mutableStateOf(false) }
    var pendingPhotoPath by remember { mutableStateOf<String?>(null) }
    var targetPhotoValue by remember { mutableStateOf("33") }
    var showList by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var vibration by remember { mutableStateOf(prefs.getBoolean("vibration", true)) }
    var autoRestart by remember { mutableStateOf(prefs.getBoolean("auto_restart", true)) }
    var message by remember { mutableStateOf<String?>(null) }

    fun saveTexts(list: List<Dhikr>) {
        textItems = list
        prefs.edit().putString("texts", JSONArray().apply { list.forEach { put(JSONObject().put("id",it.id).put("text",it.text).put("target",it.target).put("count",it.count)) } }.toString()).apply()
        selectedText = selectedText.coerceIn(0, (list.size - 1).coerceAtLeast(0))
    }
    fun savePhotos(list: List<PhotoDhikr>) {
        photoItems = list
        prefs.edit().putString("photos", JSONArray().apply { list.forEach { put(JSONObject().put("id",it.id).put("path",it.path).put("target",it.target).put("count",it.count)) } }.toString()).apply()
        selectedPhoto = selectedPhoto.coerceIn(0, (list.size - 1).coerceAtLeast(0))
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try {
                val destination = File(context.filesDir, "dhikr_${UUID.randomUUID()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input -> destination.outputStream().use { output -> input.copyTo(output) } }
                if (destination.exists() && destination.length() > 0) {
                    pendingPhotoPath = destination.absolutePath
                    targetPhotoValue = "33"
                    photoTargetDialog = true
                } else message = "تعذر حفظ الصورة"
            } catch (_: Exception) { message = "تعذر قراءة الصورة" }
        }
    }

    MaterialTheme(colorScheme = lightColorScheme(primary = Green, secondary = DeepGreen, background = AppBackground, surface = Color(0xFFFFFBF1))) {
        Scaffold(containerColor = AppBackground, snackbarHost = { SnackbarHost(remember { SnackbarHostState() }) }) { padding ->
            Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(DeepGreen, Color(0xFF176B49), AppBackground))).padding(padding).padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth().background(DeepGreen, RoundedCornerShape(22.dp)).padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, "الإعدادات", tint = Gold) }
                    Text("أذكار المسلم", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, color = Gold)
                    IconButton(onClick = { showList = true }) { Icon(Icons.Default.Home, "القائمة", tint = Gold) }
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
                                Text("✦  ${index + 1} من ${textItems.size}  ✦", color = DeepGreen, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                Text("وَاذْكُرُوا اللَّهَ كَثِيرًا", color = DeepGreen, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.background(Color(0xFFF4E4BD), RoundedCornerShape(18.dp)).border(1.dp, Gold, RoundedCornerShape(18.dp)).padding(horizontal = 18.dp, vertical = 11.dp))
                                Spacer(Modifier.height(22.dp))
                                Text(item.text, modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp), textAlign = TextAlign.Center, fontSize = 27.sp, lineHeight = 43.sp, fontWeight = FontWeight.Bold, color = Color(0xFF151515))
                                Spacer(Modifier.height(22.dp))
                                Counter(item.count, item.target) {
                                    if (vibration) vibrateDevice(context)
                                    val next = textItems.toMutableList()
                                    val count = item.count + 1
                                    if (count >= item.target && autoRestart) { next[index] = item.copy(count = 0); message = "اكتمل الذكر، وعاد العداد إلى الصفر" }
                                    else next[index] = item.copy(count = count.coerceAtMost(item.target))
                                    saveTexts(next)
                                }
                                Spacer(Modifier.weight(1f))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    OutlinedButton(onClick = { val n=textItems.toMutableList(); n[index]=item.copy(count=0); saveTexts(n) }, modifier=Modifier.weight(1f)) { Text("إعادة") }
                                    OutlinedButton(onClick = { selectedText = (index - 1 + textItems.size) % textItems.size }, modifier=Modifier.weight(1f)) { Text("السابق") }
                                    OutlinedButton(onClick = { selectedText = (index + 1) % textItems.size }, modifier=Modifier.weight(1f)) { Text("التالي") }
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = { editingId = null; editText = ""; editTarget = "33"; editorOpen = true }) { Icon(Icons.Default.Add, null); Text("إضافة ذكر") }
                                    TextButton(onClick = { editingId = item.id; editText = item.text; editTarget = item.target.toString(); editorOpen = true }) { Icon(Icons.Default.Edit, null); Text("تعديل") }
                                    TextButton(onClick = { saveTexts(textItems.filterNot { it.id == item.id }) }) { Icon(Icons.Default.Delete, null); Text("حذف") }
                                }
                            }
                        } else {
                            if (photoItems.isEmpty()) {
                                EmptyState("لا توجد صور أذكار", "اختر صورة من هاتفك وأدخل عدد التكرار")
                                Button(onClick = { photoPicker.launch("image/*") }) { Icon(Icons.Default.Add, null); Text("اختيار صورة") }
                            } else {
                                val index = selectedPhoto.coerceIn(photoItems.indices)
                                val item = photoItems[index]
                                Text("${index + 1} من ${photoItems.size}", color = Green, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(6.dp))
                                StoredPhoto(item.path, Modifier.weight(1f).fillMaxWidth())
                                Spacer(Modifier.height(8.dp))
                                Counter(item.count, item.target) {
                                    if (vibration) vibrateDevice(context)
                                    val next = photoItems.toMutableList(); val count = item.count + 1
                                    if (count >= item.target && autoRestart) { next[index] = item.copy(count = 0); message = "اكتمل الذكر، وعاد العداد إلى الصفر" }
                                    else next[index] = item.copy(count = count.coerceAtMost(item.target))
                                    savePhotos(next)
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    OutlinedButton(onClick = { val n=photoItems.toMutableList(); n[index]=item.copy(count=0); savePhotos(n) }, modifier=Modifier.weight(1f)) { Text("إعادة") }
                                    OutlinedButton(onClick = { selectedPhoto=(index-1+photoItems.size)%photoItems.size }, modifier=Modifier.weight(1f)) { Text("السابق") }
                                    OutlinedButton(onClick = { selectedPhoto=(index+1)%photoItems.size }, modifier=Modifier.weight(1f)) { Text("التالي") }
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = { photoPicker.launch("image/*") }) { Icon(Icons.Default.Add, null); Text("إضافة صورة") }
                                    TextButton(onClick = { targetPhotoValue=item.target.toString(); pendingPhotoPath=item.path; photoTargetDialog=true }) { Icon(Icons.Default.Edit, null); Text("العدد") }
                                    TextButton(onClick = { File(item.path).delete(); savePhotos(photoItems.filterNot { it.id == item.id }) }) { Icon(Icons.Default.Delete, null); Text("حذف") }
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
                OutlinedTextField(value = editTarget, onValueChange = { editTarget = it.filter(Char::isDigit).take(7) }, label = { Text("عدد التكرار") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            }
        }, confirmButton = { TextButton(onClick = {
            val target = editTarget.toIntOrNull()?.coerceAtLeast(1)
            if (editText.isNotBlank() && target != null) {
                val list = textItems.toMutableList()
                if (editingId == null) { list.add(Dhikr(UUID.randomUUID().toString(), editText.trim(), target)); selectedText = list.lastIndex }
                else { val i=list.indexOfFirst { it.id == editingId }; if(i >= 0) list[i]=list[i].copy(text=editText.trim(), target=target, count=0) }
                saveTexts(list); editorOpen = false
            }
        }) { Text("حفظ") } }, dismissButton = { TextButton(onClick = { editorOpen = false }) { Text("إلغاء") } })

        if (photoTargetDialog) AlertDialog(onDismissRequest = { photoTargetDialog = false; pendingPhotoPath = null }, title = { Text("عدد مرات تكرار الصورة") }, text = {
            OutlinedTextField(value = targetPhotoValue, onValueChange = { targetPhotoValue=it.filter(Char::isDigit).take(7) }, label = { Text("العدد المطلوب") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        }, confirmButton = { TextButton(onClick = {
            val value=targetPhotoValue.toIntOrNull()?.coerceAtLeast(1)
            val path=pendingPhotoPath
            if(value != null && path != null) {
                val list=photoItems.toMutableList(); val existing=list.indexOfFirst { it.path == path }
                if(existing >= 0) list[existing]=list[existing].copy(target=value,count=0)
                else { list.add(PhotoDhikr(UUID.randomUUID().toString(),path,value)); selectedPhoto=list.lastIndex }
                savePhotos(list); photoTargetDialog=false; pendingPhotoPath=null
            }
        }) { Text("حفظ") } }, dismissButton = { TextButton(onClick = { photoTargetDialog=false; pendingPhotoPath=null }) { Text("إلغاء") } })

        if (showList) AlertDialog(onDismissRequest = { showList=false }, title = { Text("قائمة الأذكار المكتوبة") }, text = {
            LazyColumn { itemsIndexed(textItems) { i, item -> Row(Modifier.fillMaxWidth().clickable { selectedText=i; tab=0; showList=false }.padding(vertical=8.dp), verticalAlignment=Alignment.CenterVertically) { Text(item.text, Modifier.weight(1f), maxLines=2); Text("${item.target}", color=Green, fontWeight=FontWeight.Bold) } } }
        }, confirmButton = { TextButton(onClick = { showList=false }) { Text("إغلاق") } })

        if (showSettings) AlertDialog(onDismissRequest = { showSettings=false }, title = { Text("الإعدادات") }, text = {
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) { Text("إعادة العداد تلقائياً بعد اكتمال العدد", Modifier.weight(1f)); Switch(autoRestart, { autoRestart=it; prefs.edit().putBoolean("auto_restart",it).apply() }) }
                Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) { Text("اهتزاز عند الضغط", Modifier.weight(1f)); Switch(vibration, { vibration=it; prefs.edit().putBoolean("vibration",it).apply() }) }
            }
        }, confirmButton = { TextButton(onClick = { showSettings=false }) { Text("تم") } })

        message?.let { msg -> AlertDialog(onDismissRequest={message=null}, title={Text("أذكار المسلم")}, text={Text(msg)}, confirmButton={TextButton(onClick={message=null}){Text("حسناً")}}) }
    }
}

@Composable
private fun Counter(count: Int, target: Int, onIncrement: () -> Unit) {
    Box(
        modifier = Modifier
            .size(128.dp)
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
                text = count.toString(),
                fontSize = 40.sp,
                color = DeepGreen,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "/ $target",
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
    if(bitmap != null) Image(bitmap!!.asImageBitmap(), contentDescription="صورة الذكر", modifier=modifier, contentScale=ContentScale.Fit)
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

private fun loadTextItems(prefs: android.content.SharedPreferences): List<Dhikr> {
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

private fun loadPhotoItems(prefs: android.content.SharedPreferences): List<PhotoDhikr> = try {
    prefs.getString("photos", null)?.let { raw -> val a=JSONArray(raw); (0 until a.length()).map { val o=a.getJSONObject(it); PhotoDhikr(o.getString("id"),o.getString("path"),o.getInt("target"),o.optInt("count",0)) } } ?: emptyList()
} catch (_: Exception) { emptyList() }
