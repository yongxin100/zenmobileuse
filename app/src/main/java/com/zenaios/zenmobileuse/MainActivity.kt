package com.zenaios.zenmobileuse

import android.app.AppOpsManager
import android.app.DownloadManager
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Process
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.zenaios.zenmobileuse.ui.theme.ZenmobileuseTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import java.net.Inet4Address
import java.net.InetAddress
import java.util.Calendar
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.net.URI
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import kotlinx.coroutines.delay
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.compose.ui.viewinterop.AndroidView
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.text.KeyboardOptions

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching {
            ContextCompat.startForegroundService(
                this,
                Intent(this, UsageMonitorService::class.java)
            )
        }
        enableEdgeToEdge()
        setContent {
            ZenmobileuseTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    var selectedItem by remember { mutableIntStateOf(0) }
    val items = listOf("时间", "设置")
    val icons = listOf(Icons.Filled.Schedule, Icons.Filled.Settings)

    Scaffold(
        bottomBar = {
            NavigationBar {
                items.forEachIndexed { index, item ->
                    NavigationBarItem(
                        icon = { Icon(icons[index], contentDescription = item) },
                        label = { Text(item) },
                        selected = selectedItem == index,
                        onClick = {
                            selectedItem = index
                            navController.navigate(item) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "时间",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("时间") {
                AppUsageScreen(onOpenHistory = {
                    navController.navigate("history")
                })
            }
            composable("设置") {
                SettingsScreen(onOpenScanner = {
                    navController.navigate("scan_qr")
                })
            }
            composable("history") {
                HistoryScreen(onBack = {
                    navController.popBackStack()
                })
            }
            composable("scan_qr") {
                val context = LocalContext.current
                QRCodeScannerScreen(
                    onResult = { result ->
                        // Parse result
                        var url = result
                        try {
                            // Try parsing as JSON first
                            val json = JSONObject(result)
                            if (json.has("ip") && json.has("port")) {
                                val ip = json.getString("ip")
                                val port = json.getInt("port")
                                url = "http://$ip:$port"
                            }
                        } catch (e: Exception) {
                            // Not JSON, assume simple string format
                            if (!url.startsWith("http")) {
                                url = "http://$url"
                            }
                        }

                        // Save to prefs
                        val sharedPreferences = context.getSharedPreferences("zen_prefs", Context.MODE_PRIVATE)
                        sharedPreferences.edit().putString("service_url", url).apply()
                        
                        // Show toast
                        android.widget.Toast.makeText(context, "已连接: $url", android.widget.Toast.LENGTH_SHORT).show()
                        
                        navController.popBackStack()
                    },
                    onBack = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}






// Network Scanner Log
enum class LogType {
    INFO, SUCCESS, FAILURE
}

data class ScanLog(
    val message: String,
    val type: LogType = LogType.INFO
)

// Network Scanner
object NetworkScanner {
    private val client = OkHttpClient.Builder()
        .connectTimeout(200, TimeUnit.MILLISECONDS) // Reduced timeout for faster scanning
        .readTimeout(200, TimeUnit.MILLISECONDS)
        .build()

    fun scanLocalNetwork(context: Context, port: Int = 35126): Flow<ScanLog> = flow {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val linkProperties: LinkProperties? = connectivityManager.getLinkProperties(connectivityManager.activeNetwork)
        
        val ipAddress = linkProperties?.linkAddresses?.firstOrNull { it.address is Inet4Address }?.address?.hostAddress
        
        if (ipAddress == null) {
            emit(ScanLog("Error: Could not determine local IP address", LogType.FAILURE))
            return@flow
        }

        val subnet = ipAddress.substringBeforeLast(".")
        emit(ScanLog("Local IP: $ipAddress. Scanning subnet $subnet.1-254...", LogType.INFO))
        
        // Scan 1-254
        for (i in 1..254) {
            val targetIp = "$subnet.$i"
            val url = "http://$targetIp:$port/api/heartbeat?message=hello"
            
            try {
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    response.close()
                    
                    var isSuccess = false
                    if (!body.isNullOrEmpty()) {
                        try {
                            val json = JSONObject(body)
                            if (json.optString("status") == "ok") {
                                isSuccess = true
                            }
                        } catch (e: Exception) {
                            // Not a valid JSON or parsing error
                        }
                    }

                    if (isSuccess) {
                        emit(ScanLog("$targetIp:$port OK", LogType.SUCCESS))
                        return@flow // Stop scanning after finding a service
                    } else {
                        emit(ScanLog("$targetIp:$port NG", LogType.FAILURE))
                    }
                } else {
                    response.close()
                    emit(ScanLog("$targetIp:$port NG", LogType.FAILURE))
                }
            } catch (e: Exception) {
                emit(ScanLog("$targetIp:$port NG", LogType.FAILURE))
            }
        }
        emit(ScanLog("Scan complete.", LogType.INFO))
    }.flowOn(Dispatchers.IO)

    fun syncUsageTime(baseUrl: String, minutes: Double, dateStr: String): Flow<ScanLog> = flow {
        emit(ScanLog("Starting sync for $dateStr: ${String.format("%.1f", minutes)} minutes", LogType.INFO))
        
        // 1. Check existing records
        val listUrl = "$baseUrl/api/water_drop/phone_time/usage_days?days=5"
        var eventIdToUpdate: Int? = null
        
        try {
            emit(ScanLog("Checking existing records on server...", LogType.INFO))
            val request = Request.Builder().url(listUrl).build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                response.close()
                
                if (!body.isNullOrEmpty()) {
                    val jsonArray = org.json.JSONArray(body)
                    for (i in 0 until jsonArray.length()) {
                        val dayObj = jsonArray.getJSONObject(i)
                        if (dayObj.optString("ymd") == dateStr) {
                            val events = dayObj.optJSONArray("events")
                            if (events != null) {
                                for (j in 0 until events.length()) {
                                    val event = events.getJSONObject(j)
                                    if (event.optString("title") == "ZenMobileUse Sync") {
                                        eventIdToUpdate = event.optInt("id")
                                        break
                                    }
                                }
                            }
                            break
                        }
                    }
                }
            } else {
                response.close()
                emit(ScanLog("Failed to fetch records: ${response.code}", LogType.FAILURE))
                return@flow
            }
        } catch (e: Exception) {
            emit(ScanLog("Error checking records: ${e.message}", LogType.FAILURE))
            return@flow
        }

        // 2. Create or Update
        val jsonMediaType = "application/json; charset=utf-8".toMediaType()
        
        if (eventIdToUpdate != null) {
            emit(ScanLog("Found existing record (ID: $eventIdToUpdate). Updating...", LogType.INFO))
            val updateUrl = "$baseUrl/api/water_drop/phone_time/usage_events/$eventIdToUpdate"
            val json = JSONObject().apply {
                put("minutes", minutes)
                put("source", "phone_sync")
            }
            val body = json.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder().url(updateUrl).put(body).build()
            
            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    emit(ScanLog("Update successful! Record ID: $eventIdToUpdate", LogType.SUCCESS))
                } else {
                    emit(ScanLog("Update failed: ${response.code} ${response.message}", LogType.FAILURE))
                }
                response.close()
            } catch (e: Exception) {
                emit(ScanLog("Update error: ${e.message}", LogType.FAILURE))
            }
        } else {
            emit(ScanLog("No existing record found. Creating new...", LogType.INFO))
            val createUrl = "$baseUrl/api/water_drop/phone_time/usage_events"
            val json = JSONObject().apply {
                put("title", "ZenMobileUse Sync")
                put("minutes", minutes)
                put("ymd", dateStr)
                put("source", "phone_sync")
            }
            val body = json.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder().url(createUrl).post(body).build()
            
            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    emit(ScanLog("Create successful!", LogType.SUCCESS))
                } else {
                    emit(ScanLog("Create failed: ${response.code} ${response.message}", LogType.FAILURE))
                }
                response.close()
            } catch (e: Exception) {
                emit(ScanLog("Create error: ${e.message}", LogType.FAILURE))
            }
        }
    }.flowOn(Dispatchers.IO)

    fun syncAppUsage(baseUrl: String, dateStr: String, apps: List<AppUsageInfo>): Flow<ScanLog> = flow {
        emit(ScanLog("Syncing app usage details...", LogType.INFO))
        val url = "$baseUrl/api/water_drop/phone_time/app_usage"
        val jsonMediaType = "application/json; charset=utf-8".toMediaType()

        val appsArray = org.json.JSONArray()
        apps.forEach { app ->
            val minutes = app.usageTime / (1000.0 * 60.0)
            if (minutes > 0 && app.packageName.isNotEmpty()) {
                val appObj = JSONObject().apply {
                    put("app_key", app.packageName)
                    put("name", app.appName)
                    put("minutes", minutes)
                }
                appsArray.put(appObj)
            }
        }

        if (appsArray.length() == 0) {
            emit(ScanLog("No valid app usage data to sync.", LogType.INFO))
            return@flow
        }

        val json = JSONObject().apply {
            put("source", "phone_sync")
            put("ymd", dateStr)
            put("apps", appsArray)
        }

        val body = json.toString().toRequestBody(jsonMediaType)
        val request = Request.Builder().url(url).post(body).build()

        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                 emit(ScanLog("App usage sync successful!", LogType.SUCCESS))
            } else {
                 emit(ScanLog("App usage sync failed: ${response.code} ${response.message}", LogType.FAILURE))
            }
            response.close()
        } catch (e: Exception) {
            emit(ScanLog("App usage sync error: ${e.message}", LogType.FAILURE))
        }
    }.flowOn(Dispatchers.IO)
}

data class UpdateInfo(
    val versionName: String,
    val versionCode: Long,
    val notes: String,
    val forceUpdate: Boolean,
    val downloadUrl: String
)

data class UpdateCheckResult(
    val hasUpdate: Boolean,
    val updateInfo: UpdateInfo?,
    val message: String
)

object MobileUpdateManager {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun normalizeServiceUrl(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "http://$trimmed"
    }

    private fun buildOrigin(serviceUrl: String): String? {
        val uri = URI(serviceUrl)
        val scheme = uri.scheme ?: return null
        val host = uri.host ?: return null
        val portPart = if (uri.port > 0) ":${uri.port}" else ""
        return "$scheme://$host$portPart"
    }

    private fun resolveDownloadUrl(serviceUrl: String, rawUrl: String): String {
        if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
            return rawUrl
        }
        val origin = buildOrigin(serviceUrl) ?: serviceUrl.trimEnd('/')
        return "$origin/${rawUrl.trimStart('/')}"
    }

    private fun getCurrentVersionInfo(context: Context): Pair<String, Long> {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionName = packageInfo.versionName ?: "0.0.0"
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        return versionName to versionCode
    }

    suspend fun checkForUpdate(context: Context, serviceUrlRaw: String): UpdateCheckResult = withContext(Dispatchers.IO) {
        val serviceUrl = normalizeServiceUrl(serviceUrlRaw)
        val checkUrl = "${serviceUrl.trimEnd('/')}/api/mobile/version/check"
        val (currentVersionName, currentVersionCode) = getCurrentVersionInfo(context)
        val requestJson = JSONObject().apply {
            put("platform", "android")
            put("app_id", "zenA+")
            put("version_name", currentVersionName)
            put("version_code", currentVersionCode)
            put("os_name", "Android")
            put("os_version", Build.VERSION.RELEASE ?: "")
            put("device_id", Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "")
            put("channel", "official")
            put("arch", Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown")
        }
        val request = Request.Builder()
            .url(checkUrl)
            .post(requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        val response = client.newCall(request).execute()
        response.use {
            if (!it.isSuccessful) {
                return@withContext UpdateCheckResult(
                    hasUpdate = false,
                    updateInfo = null,
                    message = "检查更新失败：${it.code} ${it.message}"
                )
            }
            val body = it.body?.string().orEmpty()
            if (body.isBlank()) {
                return@withContext UpdateCheckResult(false, null, "服务端返回为空")
            }
            val root = JSONObject(body)
            val hasUpdateFlag = root.optBoolean("has_update", root.optBoolean("update_available", false))
            val dataObj = root.optJSONObject("data")
            val packageObj = root.optJSONObject("package")
                ?: root.optJSONObject("latest")
                ?: dataObj?.optJSONObject("package")
                ?: dataObj?.optJSONObject("latest")
                ?: root.optJSONArray("packages")?.optJSONObject(0)
                ?: dataObj?.optJSONArray("packages")?.optJSONObject(0)
            if (packageObj == null) {
                return@withContext if (hasUpdateFlag) {
                    UpdateCheckResult(false, null, "检测到更新标记，但缺少安装包信息")
                } else {
                    UpdateCheckResult(false, null, "当前已是最新版本")
                }
            }
            val latestVersionName = packageObj.optString("version_name").ifBlank { root.optString("version_name") }
            val latestVersionCode = packageObj.optLong("version_code", root.optLong("version_code", -1))
            val relativePath = packageObj.optString("relative_path")
            val rawDownloadUrl = packageObj.optString("download_url")
                .ifBlank { packageObj.optString("url") }
                .ifBlank { packageObj.optString("package_url") }
                .ifBlank { relativePath }
            if (rawDownloadUrl.isBlank()) {
                return@withContext UpdateCheckResult(false, null, "服务端未返回可下载地址")
            }
            val resolvedUrl = resolveDownloadUrl(serviceUrl, rawDownloadUrl)
            val notes = packageObj.optString("notes")
            val forceUpdate = packageObj.optBoolean("force_update", false)
            val versionIsNewer = latestVersionCode > currentVersionCode
            val shouldUpdate = hasUpdateFlag || versionIsNewer
            return@withContext if (shouldUpdate) {
                UpdateCheckResult(
                    hasUpdate = true,
                    updateInfo = UpdateInfo(
                        versionName = latestVersionName.ifBlank { "unknown" },
                        versionCode = latestVersionCode,
                        notes = notes,
                        forceUpdate = forceUpdate,
                        downloadUrl = resolvedUrl
                    ),
                    message = "发现新版本：${latestVersionName.ifBlank { "unknown" }}"
                )
            } else {
                UpdateCheckResult(false, null, "当前已是最新版本")
            }
        }
    }

    fun startApkDownload(context: Context, downloadUrl: String, fileName: String): Long {
        val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
            setTitle("下载更新")
            setDescription(fileName)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setMimeType("application/vnd.android.package-archive")
            setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
        }
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return downloadManager.enqueue(request)
    }

    fun installDownloadedApk(context: Context, downloadId: Long): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return false
        }
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val fileUri = downloadManager.getUriForDownloadedFile(downloadId) ?: return false
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(installIntent)
        return true
    }

    fun downloadFailedReason(context: Context, downloadId: Long): String {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)
        cursor.use {
            if (!it.moveToFirst()) return "下载任务不存在"
            val statusIndex = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val reasonIndex = it.getColumnIndex(DownloadManager.COLUMN_REASON)
            val status = if (statusIndex >= 0) it.getInt(statusIndex) else -1
            val reason = if (reasonIndex >= 0) it.getInt(reasonIndex) else -1
            return if (status == DownloadManager.STATUS_FAILED) {
                "下载失败，错误码：$reason"
            } else {
                "下载未成功完成"
            }
        }
    }
}

@Composable
fun SettingsScreen(onOpenScanner: () -> Unit = {}) {
    val context = LocalContext.current
    val sharedPreferences = remember { context.getSharedPreferences("zen_prefs", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var isScanning by remember { mutableStateOf(false) }
    var scanLogs by remember { mutableStateOf(listOf<ScanLog>()) }
    var foundServiceUrl by remember { mutableStateOf(sharedPreferences.getString("service_url", null)) }
    var scanJob by remember { mutableStateOf<Job?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var updateCheckMessage by remember { mutableStateOf("") }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var activeDownloadId by remember { mutableStateOf<Long?>(null) }
    var accessibilityEnabled by remember { mutableStateOf(isLockAccessibilityEnabled(context)) }
    var showAdminPasswordDialog by remember { mutableStateOf(false) }
    var oldAdminPassword by remember { mutableStateOf("") }
    var newAdminPassword by remember { mutableStateOf("") }
    var confirmAdminPassword by remember { mutableStateOf("") }
    var adminPasswordError by remember { mutableStateOf("") }
    var showLimitPasswordDialog by remember { mutableStateOf(false) }
    var showEditLimitDialog by remember { mutableStateOf(false) }
    var limitPasswordInput by remember { mutableStateOf("") }
    var limitPasswordError by remember { mutableStateOf("") }
    var dailyLimitInput by remember { mutableStateOf("") }
    var dailyLimitError by remember { mutableStateOf("") }
    val fontScale = LocalConfiguration.current.fontScale
    val isLargeFont = fontScale >= 1.15f
    val visibleLogs = if (scanLogs.size > 5) scanLogs.takeLast(5) else scanLogs
    
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                foundServiceUrl = sharedPreferences.getString("service_url", null)
                accessibilityEnabled = isLockAccessibilityEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val trackedDownloadId by rememberUpdatedState(activeDownloadId)
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
                val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                val targetId = trackedDownloadId ?: return
                if (completedId != targetId) return
                activeDownloadId = null
                isDownloadingUpdate = false
                val downloadManager = receiverContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val cursor = downloadManager.query(DownloadManager.Query().setFilterById(completedId))
                cursor.use {
                    if (!it.moveToFirst()) {
                        scanLogs = scanLogs + ScanLog("更新包下载失败：任务不存在", LogType.FAILURE)
                        android.widget.Toast.makeText(receiverContext, "更新包下载失败：任务不存在", android.widget.Toast.LENGTH_LONG).show()
                        return
                    }
                    val statusIndex = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val status = if (statusIndex >= 0) it.getInt(statusIndex) else -1
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        val startedInstall = MobileUpdateManager.installDownloadedApk(receiverContext, completedId)
                        if (startedInstall) {
                            scanLogs = scanLogs + ScanLog("更新包下载完成，正在安装", LogType.SUCCESS)
                            android.widget.Toast.makeText(receiverContext, "下载完成，正在安装", android.widget.Toast.LENGTH_LONG).show()
                        } else {
                            scanLogs = scanLogs + ScanLog("请先允许安装未知来源应用后重试", LogType.INFO)
                            android.widget.Toast.makeText(receiverContext, "请先允许安装未知来源应用", android.widget.Toast.LENGTH_LONG).show()
                        }
                    } else {
                        val reason = MobileUpdateManager.downloadFailedReason(receiverContext, completedId)
                        scanLogs = scanLogs + ScanLog(reason, LogType.FAILURE)
                        android.widget.Toast.makeText(receiverContext, reason, android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        val filter = android.content.IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }
    
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    LaunchedEffect(scanLogs.size) {
        if (visibleLogs.isNotEmpty()) {
            val totalItems = 9 + visibleLogs.size
            if (totalItems > 0) {
                listState.animateScrollToItem(totalItems - 1)
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Text(
                "设置", 
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 24.dp, top = 32.dp, bottom = 16.dp)
            )
        }
        
        item {
            Text(
                "数据同步",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 24.dp, bottom = 8.dp)
            )
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (foundServiceUrl != null) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    if (isLargeFont) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "同步今日数据",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "上传应用使用时长统计",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "同步今日数据",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "上传应用使用时长统计",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (foundServiceUrl == null) "请先连接服务端后再同步" else "已连接服务端，可立即同步",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (foundServiceUrl == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (isLargeFont) 3 else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (foundServiceUrl == null) {
                                scanLogs = scanLogs + ScanLog("Error: No service URL found. Please connect first.", LogType.FAILURE)
                                return@Button
                            }
                            if (!checkUsageStatsPermission(context)) {
                                scanLogs = scanLogs + ScanLog("Error: Usage stats permission not granted.", LogType.FAILURE)
                                return@Button
                            }

                            scope.launch {
                                val usageStats = getDailyUsageStats(context)
                                val totalMinutes = usageStats.totalUsageTime / (1000.0 * 60.0)
                                val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

                                val baseUrl = if (foundServiceUrl!!.startsWith("http")) foundServiceUrl!! else "http://$foundServiceUrl"

                                NetworkScanner.syncUsageTime(baseUrl, totalMinutes, dateStr).collect { log ->
                                    scanLogs = scanLogs + log
                                }
                                NetworkScanner.syncAppUsage(baseUrl, dateStr, usageStats.topApps).collect { log ->
                                    scanLogs = scanLogs + log
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = foundServiceUrl != null
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("立即同步", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        item {
            Text(
                "连接服务端",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 8.dp)
            )
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (foundServiceUrl != null) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = if (foundServiceUrl != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "连接状态",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = if (isLargeFont) 2 else 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                if (foundServiceUrl != null) "已连接" else "未连接",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (foundServiceUrl != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = foundServiceUrl ?: "未配置服务端地址",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (isLargeFont) 3 else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        item {
            Text(
                "应用更新",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 8.dp)
            )
        }

        item {
            FilledTonalButton(
                onClick = {
                    val serviceUrl = foundServiceUrl
                    if (serviceUrl.isNullOrBlank()) {
                        val message = "请先连接服务端后再检查更新"
                        scanLogs = scanLogs + ScanLog(message, LogType.FAILURE)
                        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
                        return@FilledTonalButton
                    }
                    showUpdateDialog = true
                    isCheckingUpdate = true
                    isDownloadingUpdate = false
                    updateInfo = null
                    updateCheckMessage = ""
                    scope.launch {
                        val result = runCatching { MobileUpdateManager.checkForUpdate(context, serviceUrl) }
                        if (result.isSuccess) {
                            val checkResult = result.getOrThrow()
                            updateInfo = checkResult.updateInfo
                            updateCheckMessage = checkResult.message
                            scanLogs = scanLogs + ScanLog(checkResult.message, if (checkResult.hasUpdate) LogType.SUCCESS else LogType.INFO)
                        } else {
                            val error = "检查更新失败：${result.exceptionOrNull()?.message ?: "未知错误"}"
                            updateInfo = null
                            updateCheckMessage = error
                            scanLogs = scanLogs + ScanLog(error, LogType.FAILURE)
                        }
                        isCheckingUpdate = false
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
            ) {
                if (isCheckingUpdate) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("检查中...", maxLines = if (isLargeFont) 2 else 1, overflow = TextOverflow.Ellipsis)
                } else {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("检查更新", maxLines = if (isLargeFont) 2 else 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        item {
            Text(
                "拦截控制",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 8.dp)
            )
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (accessibilityEnabled) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    }
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = if (accessibilityEnabled) "无障碍拦截已开启" else "无障碍拦截未开启",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (accessibilityEnabled) {
                            "超额后会拦截除 zenA+ 外的应用"
                        } else {
                            "请先开启无障碍服务，否则超额后无法拦截其他应用"
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    FilledTonalButton(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (accessibilityEnabled) "去查看无障碍设置" else "去开启无障碍服务")
                    }
                }
            }
        }

        item {
            Text(
                "连接方式",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 8.dp)
            )
        }

        item {
            FilledTonalButton(
                onClick = onOpenScanner,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("扫码连接", maxLines = if (isLargeFont) 2 else 1, overflow = TextOverflow.Ellipsis)
            }
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            FilledTonalButton(
                onClick = {
                    if (isScanning) {
                        scanJob?.cancel()
                        isScanning = false
                        scanLogs = scanLogs + ScanLog("Scan stopped by user.", LogType.INFO)
                    } else {
                        isScanning = true
                        foundServiceUrl = null
                        scanLogs = listOf(ScanLog("Starting manual scan...", LogType.INFO))
                        scanJob = scope.launch {
                            try {
                                NetworkScanner.scanLocalNetwork(context).collect { log ->
                                    scanLogs = scanLogs + log
                                    if (log.type == LogType.SUCCESS) {
                                        val url = log.message.substringBefore(" OK")
                                        foundServiceUrl = url
                                        sharedPreferences.edit().putString("service_url", url).apply()
                                    }
                                }
                            } finally {
                                isScanning = false
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (isScanning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = if (isScanning) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                if (isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("停止扫描", maxLines = if (isLargeFont) 2 else 1, overflow = TextOverflow.Ellipsis)
                } else {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("局域网搜索", maxLines = if (isLargeFont) 2 else 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        item {
            Text(
                "手机时间额度",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 8.dp)
            )
        }

        item {
            FilledTonalButton(
                onClick = {
                    showLimitPasswordDialog = true
                    limitPasswordInput = ""
                    limitPasswordError = ""
                    dailyLimitInput = UsageLimitManager.getDailyLimitMinutes(context).toString()
                    dailyLimitError = ""
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("修改今日限额", maxLines = if (isLargeFont) 2 else 1, overflow = TextOverflow.Ellipsis)
            }
        }

        item {
            Text(
                "管理员密码",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 8.dp)
            )
        }

        item {
            FilledTonalButton(
                onClick = {
                    showAdminPasswordDialog = true
                    oldAdminPassword = ""
                    newAdminPassword = ""
                    confirmAdminPassword = ""
                    adminPasswordError = ""
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("修改管理员密码", maxLines = if (isLargeFont) 2 else 1, overflow = TextOverflow.Ellipsis)
            }
        }
        
        item {
            Text(
                "操作日志",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 8.dp)
            )
        }
        
        if (visibleLogs.isEmpty()) {
            item {
                Text(
                    "暂无日志记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 24.dp, bottom = 24.dp)
                )
            }
        } else {
            items(visibleLogs) { log ->
                val color = when (log.type) {
                    LogType.SUCCESS -> Color(0xFF4CAF50)
                    LogType.FAILURE -> Color(0xFFEF5350)
                    LogType.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Text(
                            text = ">",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.width(16.dp)
                        )
                        Text(
                            text = log.message,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            ),
                            color = color
                        )
                    }
                }
            }
        }
    }

    if (showUpdateDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isDownloadingUpdate) {
                    showUpdateDialog = false
                }
            },
            title = { Text("检查更新") },
            text = {
                when {
                    isCheckingUpdate -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("正在检查新版本...")
                        }
                    }
                    updateInfo != null -> {
                        Column {
                            Text("发现新版本：${updateInfo!!.versionName}")
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("版本号：${updateInfo!!.versionCode}")
                            if (updateInfo!!.notes.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(updateInfo!!.notes)
                            }
                        }
                    }
                    else -> {
                        Text(updateCheckMessage.ifBlank { "当前已是最新版本" })
                    }
                }
            },
            confirmButton = {
                if (updateInfo != null) {
                    Button(
                        onClick = {
                            val latest = updateInfo ?: return@Button
                            val fileName = "zenA+${latest.versionName}.apk"
                            runCatching {
                                val downloadId = MobileUpdateManager.startApkDownload(context, latest.downloadUrl, fileName)
                                activeDownloadId = downloadId
                                isDownloadingUpdate = true
                                updateCheckMessage = "正在下载更新包..."
                                scanLogs = scanLogs + ScanLog("开始下载更新：$fileName", LogType.INFO)
                                android.widget.Toast.makeText(context, "开始下载更新", android.widget.Toast.LENGTH_SHORT).show()
                            }.onFailure {
                                val error = "下载启动失败：${it.message ?: "未知错误"}"
                                scanLogs = scanLogs + ScanLog(error, LogType.FAILURE)
                                android.widget.Toast.makeText(context, error, android.widget.Toast.LENGTH_LONG).show()
                            }
                        },
                        enabled = !isDownloadingUpdate
                    ) {
                        if (isDownloadingUpdate) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("下载中...")
                        } else {
                            Text(if (updateInfo!!.forceUpdate) "立即更新" else "更新")
                        }
                    }
                } else {
                    TextButton(onClick = { showUpdateDialog = false }) {
                        Text("确定")
                    }
                }
            },
            dismissButton = {
                if (updateInfo != null) {
                    TextButton(
                        onClick = { showUpdateDialog = false },
                        enabled = !isDownloadingUpdate
                    ) {
                        Text("取消")
                    }
                }
            }
        )
    }

    if (showLimitPasswordDialog) {
        val limitPasswordFocusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            limitPasswordFocusRequester.requestFocus()
        }
        AlertDialog(
            onDismissRequest = { showLimitPasswordDialog = false },
            title = { Text("输入管理员密码") },
            text = {
                Column {
                    OutlinedTextField(
                        value = limitPasswordInput,
                        onValueChange = { limitPasswordInput = it },
                        singleLine = true,
                        isError = limitPasswordError.isNotBlank(),
                        modifier = Modifier.focusRequester(limitPasswordFocusRequester),
                        label = { Text("密码") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    if (limitPasswordError.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = limitPasswordError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (limitPasswordInput == UsageLimitManager.getAdminPassword(context)) {
                            limitPasswordError = ""
                            showLimitPasswordDialog = false
                            showEditLimitDialog = true
                        } else {
                            limitPasswordError = "密码错误"
                        }
                    }
                ) {
                    Text("确认")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLimitPasswordDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showEditLimitDialog) {
        val dailyLimitFocusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            dailyLimitFocusRequester.requestFocus()
        }
        AlertDialog(
            onDismissRequest = { showEditLimitDialog = false },
            title = { Text("修改今日限额") },
            text = {
                Column {
                    OutlinedTextField(
                        value = dailyLimitInput,
                        onValueChange = { dailyLimitInput = it.filter { char -> char.isDigit() } },
                        singleLine = true,
                        isError = dailyLimitError.isNotBlank(),
                        modifier = Modifier.focusRequester(dailyLimitFocusRequester),
                        label = { Text("限额（分钟）") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "例如：180 表示 3 小时",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (dailyLimitError.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = dailyLimitError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val minutes = dailyLimitInput.toIntOrNull()
                        dailyLimitError = when {
                            minutes == null -> "请输入正确的分钟数"
                            minutes < 1 -> "分钟数必须大于 0"
                            minutes > 1440 -> "分钟数不能超过 1440"
                            else -> ""
                        }
                        if (dailyLimitError.isBlank()) {
                            UsageLimitManager.updateDailyLimitMinutes(context, minutes!!)
                            val usageDataNow = getDailyUsageStats(context)
                            UsageLimitManager.updateLockState(context, usageDataNow.totalUsageTime)
                            showEditLimitDialog = false
                            scanLogs = scanLogs + ScanLog("今日限额已更新为 ${minutes} 分钟", LogType.SUCCESS)
                            android.widget.Toast.makeText(context, "今日限额已更新", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditLimitDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showAdminPasswordDialog) {
        val adminPasswordFocusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            adminPasswordFocusRequester.requestFocus()
        }
        AlertDialog(
            onDismissRequest = { showAdminPasswordDialog = false },
            title = { Text("修改管理员密码") },
            text = {
                Column {
                    OutlinedTextField(
                        value = oldAdminPassword,
                        onValueChange = { oldAdminPassword = it },
                        singleLine = true,
                        modifier = Modifier.focusRequester(adminPasswordFocusRequester),
                        label = { Text("当前密码") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newAdminPassword,
                        onValueChange = { newAdminPassword = it },
                        singleLine = true,
                        label = { Text("新密码") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmAdminPassword,
                        onValueChange = { confirmAdminPassword = it },
                        singleLine = true,
                        label = { Text("确认新密码") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    if (adminPasswordError.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = adminPasswordError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val current = UsageLimitManager.getAdminPassword(context)
                        adminPasswordError = when {
                            oldAdminPassword != current -> "当前密码不正确"
                            newAdminPassword.isBlank() -> "新密码不能为空"
                            newAdminPassword != confirmAdminPassword -> "两次输入的新密码不一致"
                            else -> ""
                        }
                        if (adminPasswordError.isBlank()) {
                            UsageLimitManager.updateAdminPassword(context, newAdminPassword)
                            showAdminPasswordDialog = false
                            scanLogs = scanLogs + ScanLog("管理员密码已更新", LogType.SUCCESS)
                            android.widget.Toast.makeText(context, "管理员密码已更新", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAdminPasswordDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val usageTime: Long,
    val icon: Drawable?
)

data class UsageStatsData(
    val topApps: List<AppUsageInfo>,
    val totalUsageTime: Long
)

enum class PasswordAction {
    TEMP_EXTEND,
    UNLOCK_TODAY
}



@Composable
fun AppUsageScreen(modifier: Modifier = Modifier, onOpenHistory: () -> Unit = {}) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    var hasPermission by remember { mutableStateOf(checkUsageStatsPermission(context)) }
    var usageData by remember { mutableStateOf(UsageStatsData(emptyList(), 0L)) }
    var remainingMillis by remember { mutableLongStateOf(UsageLimitManager.getRemainingMillis(context, usageData.totalUsageTime)) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showDurationDialog by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf(false) }
    var selectedExtraMinutes by remember { mutableIntStateOf(30) }
    var passwordAction by remember { mutableStateOf(PasswordAction.TEMP_EXTEND) }
    var unlockedToday by remember { mutableStateOf(UsageLimitManager.isUnlockedToday(context)) }

    LaunchedEffect(Unit) {
        UsageLimitManager.ensureToday(context)
        unlockedToday = UsageLimitManager.isUnlockedToday(context)
    }

    // Use DisposableEffect to observe lifecycle changes (resume) to auto-refresh data
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasPermission = checkUsageStatsPermission(context)
                if (hasPermission) {
                    usageData = getDailyUsageStats(context)
                    remainingMillis = UsageLimitManager.getRemainingMillis(context, usageData.totalUsageTime)
                    UsageLimitManager.updateLockState(context, usageData.totalUsageTime)
                    unlockedToday = UsageLimitManager.isUnlockedToday(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Auto-refresh every minute
    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            usageData = getDailyUsageStats(context)
            remainingMillis = UsageLimitManager.getRemainingMillis(context, usageData.totalUsageTime)
            UsageLimitManager.updateLockState(context, usageData.totalUsageTime)
            unlockedToday = UsageLimitManager.isUnlockedToday(context)
            while (true) {
                delay(60000) // 1 minute
                usageData = getDailyUsageStats(context)
                remainingMillis = UsageLimitManager.getRemainingMillis(context, usageData.totalUsageTime)
                UsageLimitManager.updateLockState(context, usageData.totalUsageTime)
                unlockedToday = UsageLimitManager.isUnlockedToday(context)
            }
        }
    }

    if (hasPermission) {
        val baseQuotaMinutes = UsageLimitManager.getDailyLimitMinutes(context) + UsageLimitManager.getTempExtraMinutes(context)
        val baseQuotaMillis = baseQuotaMinutes * 60_000L
        val exceededMillis = (usageData.totalUsageTime - baseQuotaMillis).coerceAtLeast(0L)
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (remainingMillis >= 0) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    }
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "今日手机剩余使用额度",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (unlockedToday) "今日已解除限制" else formatRemainingDisplay(remainingMillis),
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (unlockedToday || remainingMillis >= 0) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        },
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    if (unlockedToday) {
                        Text(
                            text = "今天已解除限制，你可以正常使用手机。",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "今天额度：${formatTime(baseQuotaMillis)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "今天已使用：${formatTime(usageData.totalUsageTime)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = if (exceededMillis > 0) "今天已超出：${formatTime(exceededMillis)}" else "今天还未超出额度",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                val refreshedUsage = getDailyUsageStats(context)
                                usageData = refreshedUsage
                                UsageLimitManager.relockToday(context, refreshedUsage.totalUsageTime)
                                remainingMillis = UsageLimitManager.getRemainingMillis(context, refreshedUsage.totalUsageTime)
                                unlockedToday = UsageLimitManager.isUnlockedToday(context)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("重新限制")
                        }
                    } else {
                        Text(
                            text = "今天额度：${formatTime(baseQuotaMillis)}（含临时增加 ${UsageLimitManager.getTempExtraMinutes(context)} 分钟）",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                passwordAction = PasswordAction.TEMP_EXTEND
                                showPasswordDialog = true
                                passwordInput = ""
                                passwordError = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("临时解除")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                passwordAction = PasswordAction.UNLOCK_TODAY
                                showPasswordDialog = true
                                passwordInput = ""
                                passwordError = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("今天解除")
                        }
                    }
                }
            }
            TotalUsageHeader(usageData.totalUsageTime, onOpenHistory)
            Spacer(modifier = Modifier.height(16.dp))
            AppUsageList(usageData.topApps)
        }
    } else {
        PermissionRequestScreen(
            onGrantPermission = {
                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            },
            onRefresh = {
                hasPermission = checkUsageStatsPermission(context)
                if (hasPermission) {
                    usageData = getDailyUsageStats(context)
                }
            },
            modifier = modifier
        )
    }

    if (showPasswordDialog) {
        val limitPasswordFocusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            limitPasswordFocusRequester.requestFocus()
        }
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            title = { Text(if (passwordAction == PasswordAction.TEMP_EXTEND) "输入临时解除密码" else "输入今日解除密码") },
            text = {
                Column {
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        singleLine = true,
                        isError = passwordError,
                        modifier = Modifier.focusRequester(limitPasswordFocusRequester),
                        label = { Text("密码") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    if (passwordError) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "密码错误",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (passwordInput == UsageLimitManager.getAdminPassword(context)) {
                            passwordError = false
                            showPasswordDialog = false
                            if (passwordAction == PasswordAction.TEMP_EXTEND) {
                                showDurationDialog = true
                            } else {
                                UsageLimitManager.unlockToday(context)
                                val refreshedUsage = getDailyUsageStats(context)
                                usageData = refreshedUsage
                                remainingMillis = UsageLimitManager.getRemainingMillis(context, refreshedUsage.totalUsageTime)
                                UsageLimitManager.updateLockState(context, refreshedUsage.totalUsageTime)
                                unlockedToday = UsageLimitManager.isUnlockedToday(context)
                            }
                        } else {
                            passwordError = true
                        }
                    }
                ) {
                    Text("确认")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showDurationDialog) {
        AlertDialog(
            onDismissRequest = { showDurationDialog = false },
            title = { Text("选择临时增加时长") },
            text = {
                Column {
                    listOf(30, 60, 120).forEach { minutes ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedExtraMinutes == minutes,
                                onClick = { selectedExtraMinutes = minutes }
                            )
                            Text(
                                text = when (minutes) {
                                    30 -> "30分钟"
                                    60 -> "1个小时"
                                    else -> "2个小时"
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        UsageLimitManager.addTempExtraMinutes(context, selectedExtraMinutes)
                        val refreshedUsage = getDailyUsageStats(context)
                        usageData = refreshedUsage
                        remainingMillis = UsageLimitManager.getRemainingMillis(context, refreshedUsage.totalUsageTime)
                        UsageLimitManager.updateLockState(context, refreshedUsage.totalUsageTime)
                        unlockedToday = UsageLimitManager.isUnlockedToday(context)
                        showDurationDialog = false
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDurationDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}


@Composable
fun TotalUsageHeader(totalTime: Long, onDoubleTap: () -> Unit = {}) {
    val animatedTotalTime by animateIntAsState(
        targetValue = totalTime.toInt(),
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "TotalTimeAnimation"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { offset ->
                    onDoubleTap()
                })
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "掌控力",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            AutoSizeSingleLineText(
                text = formatMinutesOnly(animatedTotalTime.toLong()),
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxFontSize = MaterialTheme.typography.displayMedium.fontSize,
                minFontSize = 18.sp
            )
        }
    }
}

@Composable
fun AppUsageList(usageList: List<AppUsageInfo>, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(usageList) { index, appUsage ->
            AppUsageItem(appUsage, index + 1)
        }
    }
}

@Composable
fun AppUsageItem(appUsage: AppUsageInfo, rank: Int) {
    var showDetailedTime by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    showDetailedTime = !showDetailedTime
                })
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 0.5.dp, 
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rank
            Text(
                text = "$rank",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = "tnum"
                ),
                color = when (rank) {
                    1 -> Color(0xFFFFD700) // Gold
                    2 -> Color(0xFFC0C0C0) // Silver
                    3 -> Color(0xFFCD7F32) // Bronze
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                },
                modifier = Modifier.width(24.dp),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.width(8.dp))

            // Icon
            if (appUsage.icon != null) {
                Image(
                    bitmap = appUsage.icon.toBitmap().asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Apps,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            
            // Content
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = appUsage.appName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (showDetailedTime) formatTime(appUsage.usageTime) else formatMinutesOnly(appUsage.usageTime),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun AutoSizeSingleLineText(
    text: String,
    style: TextStyle,
    color: Color,
    maxFontSize: TextUnit,
    minFontSize: TextUnit,
    modifier: Modifier = Modifier
) {
    val resolvedMaxFontSize =
        if (style.fontSize != TextUnit.Unspecified) style.fontSize else maxFontSize
    var fontSize by remember(text, style, resolvedMaxFontSize) { mutableStateOf(resolvedMaxFontSize) }

    Text(
        text = text,
        style = style.copy(fontSize = fontSize),
        color = color,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        modifier = modifier,
        onTextLayout = { result ->
            if (result.hasVisualOverflow && fontSize.value > minFontSize.value) {
                fontSize = (fontSize.value - 1).sp
            }
        }
    )
}

fun formatTime(millis: Long): String {
    val seconds = millis / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    return if (hours > 0) {
        "${hours}小时${remainingMinutes}分钟"
    } else {
        "${remainingMinutes}分钟"
    }
}

fun formatMinutesOnly(millis: Long): String {
    val minutes = millis / 60000
    return "${minutes}分钟"
}

fun formatRemainingDisplay(millis: Long): String {
    val absMinutes = kotlin.math.abs(millis) / 60000
    val hours = absMinutes / 60
    val minutes = absMinutes % 60
    val value = if (hours > 0) "${hours}小时${minutes}分钟" else "${minutes}分钟"
    return if (millis >= 0) value else "已超时$value"
}

fun checkUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = appOps.checkOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        Process.myUid(),
        context.packageName
    )
    return mode == AppOpsManager.MODE_ALLOWED
}

fun isLockAccessibilityEnabled(context: Context): Boolean {
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    val expected = ComponentName(context, LockAccessibilityService::class.java).flattenToString()
    return enabledServices.split(':').any { it.equals(expected, ignoreCase = true) }
}

fun calculateUsageTimeWithEvents(context: Context, startTime: Long, endTime: Long): Map<String, Long> {
    val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
    val usageMap = mutableMapOf<String, Long>()
    val lastEventMap = mutableMapOf<String, Long>()

    while (usageEvents.hasNextEvent()) {
        val event = android.app.usage.UsageEvents.Event()
        usageEvents.getNextEvent(event)

        when (event.eventType) {
            android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                lastEventMap[event.packageName] = event.timeStamp
            }
            android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                val start = lastEventMap[event.packageName]
                if (start != null) {
                    val duration = event.timeStamp - start
                    if (duration > 0) {
                        usageMap[event.packageName] = usageMap.getOrDefault(event.packageName, 0L) + duration
                    }
                    lastEventMap.remove(event.packageName)
                }
            }
        }
    }

    // Process apps that are still in foreground
    lastEventMap.forEach { (packageName, start) ->
        val duration = endTime - start
        if (duration > 0) {
            usageMap[packageName] = usageMap.getOrDefault(packageName, 0L) + duration
        }
    }

    return usageMap
}

fun getAppUsageListFromMap(context: Context, usageMap: Map<String, Long>): List<AppUsageInfo> {
    val packageManager = context.packageManager
    val appUsageList = mutableListOf<AppUsageInfo>()

    val isUserApp: (android.content.pm.ApplicationInfo) -> Boolean = { appInfo ->
        (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 ||
            (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    }

    val excludedPackages = setOf(context.packageName, "com.android.settings")

    usageMap.forEach { (packageName, time) ->
        if (time > 0 && packageName !in excludedPackages) {
            try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                if (isUserApp(appInfo)) {
                    val appName = packageManager.getApplicationLabel(appInfo).toString()
                    val icon = packageManager.getApplicationIcon(appInfo)
                    appUsageList.add(AppUsageInfo(packageName, appName, time, icon))
                }
            } catch (e: PackageManager.NameNotFoundException) {
                appUsageList.add(AppUsageInfo(packageName, packageName, time, null))
            }
        }
    }
    return appUsageList.sortedByDescending { it.usageTime }
}

fun getDailyUsageStats(context: Context): UsageStatsData {
    val calendar = Calendar.getInstance()
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    val startTime = calendar.timeInMillis
    val endTime = System.currentTimeMillis()

    // Use queryEvents for more accurate calculation
    val usageMap = calculateUsageTimeWithEvents(context, startTime, endTime)
    val totalTime = usageMap.values.sum()

    val appUsageList = getAppUsageListFromMap(context, usageMap)
    val targetCount = 20
    val sortedList = appUsageList.take(targetCount).toMutableList()
    
    val packageManager = context.packageManager
    val isUserApp: (android.content.pm.ApplicationInfo) -> Boolean = { appInfo ->
        (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 ||
            (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    }
    val excludedPackages = setOf(context.packageName, "com.android.settings")

    if (sortedList.size < targetCount) {
        val existingPackages = sortedList.map { it.packageName }.toHashSet()
        val installedApps = packageManager.getInstalledApplications(0)
        val nonSystemApps = installedApps.filter {
            it.packageName !in excludedPackages &&
                it.packageName !in existingPackages &&
                isUserApp(it)
        }
        fun addApps(apps: List<android.content.pm.ApplicationInfo>) {
            for (appInfo in apps) {
                if (sortedList.size >= targetCount) {
                    break
                }
                val appName = packageManager.getApplicationLabel(appInfo).toString()
                val icon = packageManager.getApplicationIcon(appInfo)
                sortedList.add(AppUsageInfo(appInfo.packageName, appName, 0L, icon))
                existingPackages.add(appInfo.packageName)
            }
        }
        addApps(nonSystemApps)
    }

    return UsageStatsData(sortedList, totalTime)
}


@Composable
fun PermissionRequestScreen(
    onGrantPermission: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "需要访问使用情况权限",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "为了统计应用使用时间，请授予权限。",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onGrantPermission) {
            Text("去授权")
        }
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(onClick = onRefresh) {
            Text("我已授权，刷新")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var historyItems by remember { mutableStateOf(listOf<Pair<String, Long>>()) }
    var page by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var isEndReached by remember { mutableStateOf(false) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    // Load function
    suspend fun loadMore() {
        if (isLoading || isEndReached) return
        isLoading = true
        val newItems = getHistoryData(context, page)
        if (newItems.isEmpty()) {
            isEndReached = true
        } else {
            historyItems = historyItems + newItems
            page++
        }
        isLoading = false
    }

    // Initial load
    LaunchedEffect(Unit) {
        loadMore()
    }

    // Infinite scroll
    val layoutInfo by remember { derivedStateOf { listState.layoutInfo } }
    LaunchedEffect(layoutInfo) {
        val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        if (lastVisibleItemIndex >= historyItems.size - 2) {
            loadMore()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("历史使用时间") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(historyItems) { (date, time) ->
                HistoryItem(date, time)
            }

            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryItem(dateStr: String, time: Long) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sharedPreferences = remember { context.getSharedPreferences("zen_prefs", Context.MODE_PRIVATE) }
    
    // Parse dateStr "yyyy-MM-dd" to display format
    val (datePart, weekdayPart) = remember(dateStr) {
        try {
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateStr) ?: Date()
            val d = SimpleDateFormat("MM/dd", Locale.CHINA).format(date)
            // Use "E" for "周x" in CHINA locale, replace "星期" just in case to ensure "周" format
            val w = SimpleDateFormat("E", Locale.CHINA).format(date).replace("星期", "周")
            d to w
        } catch (e: Exception) {
            dateStr to ""
        }
    }

    var showDetailedTime by remember { mutableStateOf(false) }
    var showSyncButton by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 0.5.dp, 
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Content: Date
            Column(
                modifier = Modifier
                    .weight(1f)
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = {
                            showSyncButton = !showSyncButton
                        })
                    },
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Weekday (Top)
                Text(
                    text = weekdayPart,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.secondary
                )

                // Date (Below Weekday)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = datePart,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
            
            // Sync Button
            if (showSyncButton) {
                IconButton(
                    onClick = {
                        val serviceUrl = sharedPreferences.getString("service_url", null)
                        if (serviceUrl == null) {
                            android.widget.Toast.makeText(context, "请先在设置中扫描服务", android.widget.Toast.LENGTH_SHORT).show()
                            return@IconButton
                        }
                        
                        isSyncing = true
                        scope.launch {
                            val totalMinutes = time / (1000.0 * 60.0)
                            val baseUrl = if (serviceUrl.startsWith("http")) serviceUrl else "http://$serviceUrl"
                            
                            var isTotalSuccess = false
                            NetworkScanner.syncUsageTime(baseUrl, totalMinutes, dateStr).collect { log ->
                                if (log.type == LogType.SUCCESS) {
                                    isTotalSuccess = true
                                } else if (log.type == LogType.FAILURE) {
                                    withContext(Dispatchers.Main) {
                                        android.widget.Toast.makeText(context, "总时间同步失败: ${log.message}", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }

                            val calendar = Calendar.getInstance()
                            val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateStr)
                            if (date != null) {
                                calendar.time = date
                                calendar.set(Calendar.HOUR_OF_DAY, 0)
                                calendar.set(Calendar.MINUTE, 0)
                                calendar.set(Calendar.SECOND, 0)
                                calendar.set(Calendar.MILLISECOND, 0)
                                val startTime = calendar.timeInMillis
                                val endOfDay = startTime + 24 * 60 * 60 * 1000 - 1
                                val currentTime = System.currentTimeMillis()
                                val endTime = if (endOfDay > currentTime) currentTime else endOfDay
                                
                                val usageMap = calculateUsageTimeWithEvents(context, startTime, endTime)
                                val appList = getAppUsageListFromMap(context, usageMap)
                                
                                NetworkScanner.syncAppUsage(baseUrl, dateStr, appList).collect { log ->
                                    if (log.type == LogType.SUCCESS) {
                                        withContext(Dispatchers.Main) {
                                            android.widget.Toast.makeText(context, "同步成功", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                        showSyncButton = false 
                                    } else if (log.type == LogType.FAILURE) {
                                        withContext(Dispatchers.Main) {
                                            android.widget.Toast.makeText(context, "应用详情同步失败: ${log.message}", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                            isSyncing = false
                        }
                    },
                    enabled = !isSyncing
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = "Sync",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(8.dp))

            // Right Content: Time
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .padding(start = 4.dp) // Minimum padding
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = {
                            showDetailedTime = !showDetailedTime
                        })
                    }
            ) {
                AutoSizeSingleLineText(
                    text = if (showDetailedTime) formatTime(time) else formatMinutesOnly(time),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum"
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    maxFontSize = MaterialTheme.typography.titleLarge.fontSize,
                    minFontSize = 12.sp,
                    modifier = Modifier.widthIn(max = 120.dp)
                )
            }
        }
    }
}

suspend fun getHistoryData(context: Context, page: Int, pageSize: Int = 10): List<Pair<String, Long>> = withContext(Dispatchers.IO) {
    val list = mutableListOf<Pair<String, Long>>()
    val calendar = Calendar.getInstance()
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)

    // Offset by page * pageSize
    calendar.add(Calendar.DAY_OF_YEAR, -(page * pageSize))

    for (i in 0 until pageSize) {
        val startTime = calendar.timeInMillis
        // End of day, but cap at current time for today to avoid inflating usage time with future hours
        val endOfDay = startTime + 24 * 60 * 60 * 1000 - 1
        val currentTime = System.currentTimeMillis()
        val endTime = if (endOfDay > currentTime) currentTime else endOfDay

        // Skip future
        if (startTime > currentTime) {
            calendar.add(Calendar.DAY_OF_YEAR, -1)
            continue
        }
        
        // Use queryEvents logic for consistency
        val usageMap = calculateUsageTimeWithEvents(context, startTime, endTime)
        val totalTime = usageMap.values.sum()

        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(startTime))
        list.add(dateStr to totalTime)

        calendar.add(Calendar.DAY_OF_YEAR, -1)
    }
    list
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRCodeScannerScreen(onResult: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    var isScanned by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
        }
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("扫描二维码") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (hasCameraPermission) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            
                            val preview = Preview.Builder().build()
                            preview.setSurfaceProvider(previewView.surfaceProvider)

                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()

                            imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                                processImageProxy(imageProxy) { result ->
                                    if (!isScanned) {
                                        isScanned = true
                                        onResult(result)
                                    }
                                }
                            }

                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    imageAnalysis
                                )
                            } catch (exc: Exception) {
                                // Handle exceptions
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    }
                )
                
                // Overlay
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                     androidx.compose.foundation.Canvas(modifier = Modifier.size(250.dp)) {
                         drawRect(
                             color = Color.White,
                             style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx())
                         )
                     }
                     Text(
                         "将二维码对准框内", 
                         color = Color.White, 
                         modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp)
                     )
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("需要相机权限来扫描二维码")
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
fun processImageProxy(imageProxy: ImageProxy, onResult: (String) -> Unit) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        val scanner = BarcodeScanning.getClient()
        
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    barcode.rawValue?.let { value ->
                        onResult(value)
                        // Close image proxy is handled in onCompleteListener
                        return@addOnSuccessListener 
                    }
                }
            }
            .addOnFailureListener {
                // Task failed with an exception
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    } else {
        imageProxy.close()
    }
}
