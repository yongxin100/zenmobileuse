package com.zenaios.zenmobileuse

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import kotlinx.coroutines.delay
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                SettingsScreen()
            }
            composable("history") {
                HistoryScreen(onBack = {
                    navController.popBackStack()
                })
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
}

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val sharedPreferences = remember { context.getSharedPreferences("zen_prefs", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var isScanning by remember { mutableStateOf(false) }
    var scanLogs by remember { mutableStateOf(listOf<ScanLog>()) }
    var foundServiceUrl by remember { mutableStateOf(sharedPreferences.getString("service_url", null)) }
    var scanJob by remember { mutableStateOf<Job?>(null) }
    
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    LaunchedEffect(scanLogs.size) {
        if (scanLogs.isNotEmpty()) {
            listState.animateScrollToItem(scanLogs.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "设置 & 工具", 
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        
        // Sync Action Card
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
             colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "数据同步",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Button(
                    onClick = {
                        if (foundServiceUrl == null) {
                            scanLogs = scanLogs + ScanLog("Error: No service URL found. Please scan first.", LogType.FAILURE)
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
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = foundServiceUrl != null
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("同步今日使用时间")
                }
            }
        }

        // Service Status Card
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = if (foundServiceUrl != null) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = if (foundServiceUrl != null) Color.Green else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "服务连接状态",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                if (foundServiceUrl != null) {
                    Text(
                        text = "已连接到: $foundServiceUrl",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    Text(
                        text = "未连接服务",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
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
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isScanning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("停止探测")
                    } else {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("扫描局域网服务")
                    }
                }
            }
        }
        
        // Logs Area
        Text(
            "操作日志",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.align(Alignment.Start).padding(bottom = 8.dp)
        )
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E1E1E) // Dark terminal-like background
            ),
            shape = MaterialTheme.shapes.small
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
                items(scanLogs) { log ->
                    val color = when (log.type) {
                        LogType.SUCCESS -> Color(0xFF4CAF50) // Green
                        LogType.FAILURE -> Color(0xFFEF5350) // Red
                        LogType.INFO -> Color(0xFFE0E0E0)    // Light Gray
                    }
                    
                    Text(
                        text = "> ${log.message}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        ),
                        color = color,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
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



@Composable
fun AppUsageScreen(modifier: Modifier = Modifier, onOpenHistory: () -> Unit = {}) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    var hasPermission by remember { mutableStateOf(checkUsageStatsPermission(context)) }
    var usageData by remember { mutableStateOf(UsageStatsData(emptyList(), 0L)) }

    // Use DisposableEffect to observe lifecycle changes (resume) to auto-refresh data
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasPermission = checkUsageStatsPermission(context)
                if (hasPermission) {
                    usageData = getDailyUsageStats(context)
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
            while (true) {
                delay(60000) // 1 minute
                usageData = getDailyUsageStats(context)
            }
        }
    }

    if (hasPermission) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            TotalUsageHeader(usageData.totalUsageTime, onOpenHistory)
            Spacer(modifier = Modifier.height(16.dp))
            AppUsageList(usageData.topApps, usageData.totalUsageTime)
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
                text = formatTime(animatedTotalTime.toLong()),
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
fun AppUsageList(usageList: List<AppUsageInfo>, totalUsageTime: Long, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(usageList) { index, appUsage ->
            AppUsageItem(appUsage, totalUsageTime, index + 1)
        }
    }
}

@Composable
fun AppUsageItem(appUsage: AppUsageInfo, totalUsageTime: Long, rank: Int) {
    val percentage = if (totalUsageTime > 0) appUsage.usageTime.toFloat() / totalUsageTime.toFloat() else 0f
    
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
                        text = formatMinutesOnly(appUsage.usageTime),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { percentage },
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${(percentage * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(32.dp),
                        textAlign = TextAlign.End
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

fun checkUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = appOps.checkOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        Process.myUid(),
        context.packageName
    )
    return mode == AppOpsManager.MODE_ALLOWED
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

    val packageManager = context.packageManager
    val appUsageList = mutableListOf<AppUsageInfo>()

    val isUserApp: (android.content.pm.ApplicationInfo) -> Boolean = { appInfo ->
        (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 ||
            (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    }

    // Exclude self and system settings
    val excludedPackages = setOf(context.packageName, "com.android.settings")

    usageMap.forEach { (packageName, time) ->
        if (time >= 0 && packageName !in excludedPackages) {
            try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                if (isUserApp(appInfo)) {
                    val appName = packageManager.getApplicationLabel(appInfo).toString()
                    val icon = packageManager.getApplicationIcon(appInfo)
                    appUsageList.add(AppUsageInfo(packageName, appName, time, icon))
                }
            } catch (e: PackageManager.NameNotFoundException) {
                // Fallback for apps that are installed but not fully resolved (should happen less with QUERY_ALL_PACKAGES)
                // Or uninstalled apps that still have usage stats
                // We show them to ensure data completeness
                appUsageList.add(AppUsageInfo(packageName, packageName, time, null))
            }
        }
    }

    val targetCount = 20
    val sortedList = appUsageList.sortedByDescending { it.usageTime }.take(targetCount).toMutableList()
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
        if (sortedList.size < targetCount) {
            val systemApps = installedApps.filter {
                it.packageName !in excludedPackages &&
                    it.packageName !in existingPackages &&
                    !isUserApp(it)
            }
            addApps(systemApps)
        }
    }
    val totalTime = sortedList.sumOf { it.usageTime }

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
    // Parse dateStr "yyyy-MM-dd" to display format
    val dateDisplay = remember(dateStr) {
        try {
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateStr)
            SimpleDateFormat("MM/dd EEEE", Locale.CHINESE).format(date ?: Date())
        } catch (e: Exception) {
            dateStr
        }
    }

    // Calculate percentage based on 10 hours (600 minutes) as a "full day" usage benchmark
    // You can adjust this benchmark
    val maxMinutes = 10 * 60
    val currentMinutes = time / (1000 * 60)
    val percentage = (currentMinutes.toFloat() / maxMinutes.toFloat()).coerceIn(0f, 1f)
    
    var showDetailedTime by remember { mutableStateOf(false) }

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
            // Left Content: Date and Progress
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = dateDisplay,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }

                // Progress Bar with dynamic color
                val progressColor = when {
                    percentage > 0.8f -> MaterialTheme.colorScheme.error // > 8 hours (red)
                    percentage > 0.5f -> Color(0xFFFF9800) // > 5 hours (orange)
                    else -> MaterialTheme.colorScheme.primary // (primary)
                }
                
                LinearProgressIndicator(
                    progress = { percentage },
                    modifier = Modifier
                        .fillMaxWidth(0.9f) // Leave some space
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = progressColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))

            // Right Content: Time and Percentage
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
                
                Text(
                    text = "${(percentage * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
        val endTime = startTime + 24 * 60 * 60 * 1000 - 1 // End of day

        // Skip future
        if (startTime > System.currentTimeMillis()) {
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
