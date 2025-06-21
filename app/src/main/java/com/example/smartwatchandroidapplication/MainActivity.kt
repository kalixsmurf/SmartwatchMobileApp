@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.smartwatchandroidapplication

import android.app.Notification
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartwatchandroidapplication.ui.theme.SmartwatchAndroidApplicationTheme

import android.util.Log
import android.widget.Toast

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import kotlinx.coroutines.launch
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.os.Build
import androidx.compose.ui.platform.LocalContext


import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.*

import android.media.MediaPlayer

import androidx.compose.foundation.clickable
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder


class AbnormalResultService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val notification = Notification.Builder(this, "smartwatch_channel")
            .setContentTitle("Child is Safe")
            .setContentText("Child Safety Monitoring is Enabled")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(Notification.PRIORITY_MIN) // Lower priority
            .setOngoing(false)
            .build()

        startForeground(1, notification)

        startPollingLoop()
    }

    private fun startPollingLoop() {
        val context = this
        serviceScope.launch {
            while (true) {
                try {
                    val results = fetchResultsFromApi()

                    val latestAbnormal = results
                        .filter { it.result.equals("abnormal", ignoreCase = true) }
                        .maxByOrNull { it.time }

                    val lastSeen = getLastSeenTimestamp(context)

                    if (latestAbnormal != null && (lastSeen == null || latestAbnormal.time > lastSeen)) {
                        showNotification(
                            context = context,
                            title = "⚠️ Abnormal Result Detected",
                            message = "Age: ${latestAbnormal.age}, Gender: ${latestAbnormal.gender}, Emotion: ${latestAbnormal.emotion}",
                            timestamp = latestAbnormal.time // 👈 add this
                        )

                        // ❗ Optional: Uncomment to auto-mark as seen
                        // setLastSeenTimestamp(context, latestAbnormal.time)
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                }

                delay(30_000)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "smartwatch_channel",
                "Smartwatch Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}

fun showNotification(context: Context, title: String, message: String, timestamp: String) {
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        putExtra("destination", "notification")
        putExtra("abnormal_timestamp", timestamp) // 👈 Add timestamp
    }

    val pendingIntent = PendingIntent.getActivity(
        context,
        0,
        intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    val builder = NotificationCompat.Builder(context, "smartwatch_channel")
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(title)
        .setContentText(message)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_HIGH)

    val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.notify(1001, builder.build())
}


fun getLastSeenTimestamp(context: Context): String? {
    val prefs = context.getSharedPreferences("notif_storage", Context.MODE_PRIVATE)
    return prefs.getString("last_seen_timestamp", null)
}

fun setLastSeenTimestamp(context: Context, timestamp: String) {
    val prefs = context.getSharedPreferences("notif_storage", Context.MODE_PRIVATE)
    prefs.edit().putString("last_seen_timestamp", timestamp).apply()
}

data class ResultItem(
    val time: String,
    val age: String,
    val gender: String,
    val emotion: String,
    val result: String
)

@Serializable
data class ConfigurationPayload(
    val primaryPhone: String,
    val secondaryPhone: String,
    val filters: Map<String, Int>
)

fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            "smartwatch_channel",
            "Smartwatch Alerts",
            NotificationManager.IMPORTANCE_LOW // instead of IMPORTANCE_DEFAULT
        )
        channel.setShowBadge(false)
        channel.setSound(null, null)

        channel.description = "Shows notifications from the Smartwatch App"

        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel(this)

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(Intent(this, AbnormalResultService::class.java))
        } else {
            startService(Intent(this, AbnormalResultService::class.java))
        }
        enableEdgeToEdge()

        val launchDestination = intent?.getStringExtra("destination") ?: "main"
        val abnormalTimestamp = intent?.getStringExtra("abnormal_timestamp") // 👈 Added line

        setContent {
            val context = LocalContext.current
            val navController = rememberNavController()
            var startOnce by remember { mutableStateOf(true) }

            SmartwatchAndroidApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(title = { Text("Smartwatch Manager") })
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = if (startOnce) launchDestination else "main",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("main") { MainPage(navController) }
                        composable("notification") { NotificationPage(navController, abnormalTimestamp) }
                        composable("configuration") { ConfigurationPage(navController) }
                        composable("results") { ResultsPage(navController) }
                    }
                }

                // Only redirect to notification page ONCE
                LaunchedEffect(Unit) {
                    startOnce = false
                }
            }
        }
    }
}

fun cancelNotification(context: Context, notificationId: Int = 1001) {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.cancel(notificationId)
}

@Composable
fun MainPage(navController: NavHostController) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val buttonWidth = if (screenWidth < 400.dp) screenWidth * 0.7f else 300.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Welcome!",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        ElevatedButton(
            onClick = { navController.navigate("notification") },
            modifier = Modifier
                .width(buttonWidth)
                .height(56.dp),
        ) {
            Text("Notification")
        }

        Spacer(modifier = Modifier.height(24.dp))

        ElevatedButton(
            onClick = { navController.navigate("configuration") },
            modifier = Modifier
                .width(buttonWidth)
                .height(56.dp),
        ) {
            Text("Configuration")
        }

        Spacer(modifier = Modifier.height(24.dp))

        ElevatedButton(
            onClick = { navController.navigate("results") },
            modifier = Modifier
                .width(buttonWidth)
                .height(56.dp),
        ) {
            Text("Results")
        }
    }
}



@Composable
fun NotificationPage(navController: NavHostController, abnormalTimestamp: String?) {
    val context = LocalContext.current
    var results by remember { mutableStateOf<List<ResultItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var lastSeen by remember { mutableStateOf(getLastSeenTimestamp(context)) }

    LaunchedEffect(Unit) {
        try {
            val newResults = fetchResultsFromApi()
            results = newResults.filter { it.result.equals("abnormal", ignoreCase = true) }
            loading = false
            // Do NOT update lastSeen here, so unread notifications stay visible until user marks them read
            // lastSeen = newResults.maxByOrNull { it.time }?.time // removed
            cancelNotification(context, 1001)
        } catch (e: Exception) {
            errorMessage = e.message
            loading = false
        }
    }

    //val mediaPlayer = remember { MediaPlayer() }
    var audioUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(abnormalTimestamp) {
        if (!abnormalTimestamp.isNullOrEmpty()) {
            playAudioForTimestamp(context, abnormalTimestamp)
        }
    }



    // Automatically mark notifications as read when leaving the page
    DisposableEffect(Unit) {
        onDispose {
            val latestTime = results.maxByOrNull { it.time }?.time
            if (latestTime != null) {
                setLastSeenTimestamp(context, latestTime)
            }
        }
    }

    // Partition notifications based on lastSeen (null means all unread)
    val unread = if (lastSeen == null) results else results.filter { it.time > lastSeen!! }
    val read = if (lastSeen == null) emptyList() else results.filter { it.time <= lastSeen!! }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Notifications",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Debug info
        Text("Last seen timestamp: ${lastSeen ?: "None"}")
        Text("Unread notifications: ${unread.size}")
        Text("Read notifications: ${read.size}")

        Spacer(modifier = Modifier.height(16.dp))

        if (loading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else if (!errorMessage.isNullOrEmpty()) {
            Text(
                text = errorMessage ?: "An error occurred",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (unread.isNotEmpty()) {
                    item {
                        Text("Unread", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    items(unread) { item ->
                        NotificationCard(item, isUnread = true)
                    }
                }
                if (read.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Read", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    items(read) { item ->
                        NotificationCard(item)
                    }
                }
                if (unread.isEmpty() && read.isEmpty()) {
                    item {
                        Text("No notifications found.", modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Button to mark all unread as read (update lastSeen timestamp)
        if (unread.isNotEmpty()) {
            Button(
                onClick = {
                    val latestTime = results.maxByOrNull { it.time }?.time
                    if (latestTime != null) {
                        setLastSeenTimestamp(context, latestTime)
                        lastSeen = latestTime
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Mark all as read")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        Button(onClick = { navController.popBackStack() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Back")
        }
    }
}


@Composable
fun NotificationCard(item: ResultItem, isUnread: Boolean = false) {
    val context = LocalContext.current
    val bgColor = if (isUnread) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                playAudioForTimestamp(context, item.time)
            },
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Time: ${item.time}", fontWeight = FontWeight.Bold)
            Text("Age: ${item.age}")
            Text("Gender: ${item.gender}")
            Text("Emotion: ${item.emotion}")
            Text("Result: ${item.result}")
        }
    }
}


fun playAudioForTimestamp(context: Context, timestamp: String) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val url = URL("https://smartwatchforchildsafety-bffrfaahgtahb9bg.italynorth-01.azurewebsites.net/api/smartwatch/audio?timestamp=${URLEncoder.encode(timestamp, "UTF-8")}")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.connect()

            if (conn.responseCode != 200) {
                Log.e("AudioDownload", "Failed to fetch audio. HTTP ${conn.responseCode}")
                return@launch
            }

            // Save to a temporary file
            val tempFile = File.createTempFile("abnormal_audio", ".wav", context.cacheDir)
            conn.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            // Play the audio on the main thread
            withContext(Dispatchers.Main) {
                val mediaPlayer = MediaPlayer()
                mediaPlayer.setDataSource(tempFile.absolutePath)
                mediaPlayer.prepare()
                mediaPlayer.start()
            }

        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Failed to play audio", Toast.LENGTH_SHORT).show()
            }
        }
    }
}



@Composable
fun ConfigurationPage(navController: NavHostController) {
    val genderFilters = listOf("Male", "Female")
    val emotionFilters = listOf("Angry", "Sad", "Neutral", "Calm", "Happy", "Fear", "Disgust", "Surprised")
    val ageFilters = listOf("20s", "30s", "40s", "50s", "60s", "70s", "80s")

    val genderState = remember { mutableStateMapOf<String, Int>().apply { genderFilters.forEach { put(it, 0) } } }
    val emotionState = remember { mutableStateMapOf<String, Int>().apply { emotionFilters.forEach { put(it, 0) } } }
    val ageState = remember { mutableStateMapOf<String, Int>().apply { ageFilters.forEach { put(it, 0) } } }

    var saveStatus by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var debugInfo by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()

    suspend fun getCurrentConfig(): ConfigurationPayload? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://smartwatchforchildsafety-bffrfaahgtahb9bg.italynorth-01.azurewebsites.net/api/smartwatch/config")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/json")
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.connect()

            val response = conn.inputStream.bufferedReader().readText()
            debugInfo = "GET Response:\n$response"

            Json.decodeFromString<ConfigurationPayload>(response)
        } catch (e: Exception) {
            debugInfo = "GET Exception:\n${e.stackTraceToString()}"
            null
        }
    }

    suspend fun postConfig(payload: ConfigurationPayload): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://smartwatchforchildsafety-bffrfaahgtahb9bg.italynorth-01.azurewebsites.net/api/smartwatch/config")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")

            val json = Json.encodeToString(payload)
            conn.outputStream.bufferedWriter().use { it.write(json) }
            conn.connect()

            val responseCode = conn.responseCode
            val responseMsg = conn.inputStream.bufferedReader().readText()
            debugInfo = "POST Response ($responseCode):\n$responseMsg\n\nPayload:\n$json"

            responseCode in 200..299
        } catch (e: Exception) {
            debugInfo = "POST Exception:\n${e.stackTraceToString()}"
            false
        }
    }

    // Fetch current config on page load
    LaunchedEffect(Unit) {
        loading = true
        debugInfo = "Fetching configuration from server..."
        val config = getCurrentConfig()
        loading = false

        config?.let {
            it.filters.forEach { (key, value) ->
                when {
                    genderState.containsKey(key) -> genderState[key] = value
                    emotionState.containsKey(key) -> emotionState[key] = value
                    ageState.containsKey(key) -> ageState[key] = value
                }
            }
            saveStatus = "Loaded configuration from server."
        } ?: run {
            saveStatus = "Error: Failed to load configuration."
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Configuration", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 24.dp))

        FilterGroup("Gender Filters", genderState)
        Spacer(modifier = Modifier.height(16.dp))
        FilterGroup("Emotion Filters", emotionState)
        Spacer(modifier = Modifier.height(16.dp))
        FilterGroup("Age Filters", ageState)
        Spacer(modifier = Modifier.height(24.dp))

        if (loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        saveStatus?.let {
            Text(
                text = it,
                color = if (it.startsWith("Success")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        debugInfo?.let {
            Text(
                text = it,
                fontSize = 12.sp,
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }

        Button(
            onClick = {
                val allFilters = genderState + emotionState + ageState
                val payload = ConfigurationPayload(
                    primaryPhone = "-1",
                    secondaryPhone = "-1",
                    filters = allFilters
                )

                loading = true
                saveStatus = null
                debugInfo = null

                coroutineScope.launch {
                    val success = postConfig(payload)
                    loading = false
                    saveStatus = if (success) "Success: Configuration saved!" else "Error: Failed to save configuration."
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading
        ) {
            Text("Save")
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { navController.popBackStack() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back")
        }
    }
}

@Composable
fun FilterGroup(
    title: String,
    filters: MutableMap<String, Int>
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        filters.forEach { (name, value) ->
            FilterCheckbox(name, value == 1) { checked ->
                filters[name] = if (checked) 1 else 0
            }
        }
    }
}

@Composable
fun FilterCheckbox(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label)
    }
}

@Composable
fun ResultsPage(navController: NavHostController) {
    var results by remember { mutableStateOf<List<ResultItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Fetching results...") }
    var lastUpdated by remember { mutableStateOf<String?>(null) }
    var debugInfo by remember { mutableStateOf<String>("") }

    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        refreshResultsAndUpdateState(
            setLoading = { loading = it },
            setStatus = { statusMessage = it },
            setResults = { results = it },
            setLastUpdated = { lastUpdated = it },
            setDebugInfo = { debugInfo = it }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "Results",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 16.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = statusMessage,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            ElevatedButton(
                onClick = {
                    scope.launch {
                        refreshResultsAndUpdateState(
                            setLoading = { loading = it },
                            setStatus = { statusMessage = it },
                            setResults = { results = it },
                            setLastUpdated = { lastUpdated = it },
                            setDebugInfo = { debugInfo = it }
                        )
                    }
                },
                enabled = !loading
            ) {
                Text("Refresh")
            }

        }

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        if (loading) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Loading data...", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            if (results.isEmpty()) {
                Text(
                    statusMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                ResultTable(results)

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Back")
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Debug Info",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .padding(top = 8.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                .padding(8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = debugInfo.ifBlank { "No debug information yet." },
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

suspend fun refreshResultsAndUpdateState(
    setLoading: (Boolean) -> Unit,
    setStatus: (String) -> Unit,
    setResults: (List<ResultItem>) -> Unit,
    setLastUpdated: (String?) -> Unit,
    setDebugInfo: (String) -> Unit
) {
    setLoading(true)
    setStatus("Fetching results...")
    setDebugInfo("")

    withContext(Dispatchers.IO) {
        try {
            val url = URL("https://smartwatchforchildsafety-bffrfaahgtahb9bg.italynorth-01.azurewebsites.net/api/smartwatch/data")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.connect()

            val responseCode = connection.responseCode
            val responseText = connection.inputStream.bufferedReader().readText()

            setDebugInfo("Response Code: $responseCode\nResponse Body:\n$responseText")

            if (responseCode == 200) {
                val jsonArray = JSONArray(responseText)
                val resultList = mutableListOf<ResultItem>()

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    resultList.add(
                        ResultItem(
                            time = obj.getString("timestamp"),
                            age = obj.getString("age_prediction"),
                            gender = obj.getString("gender_prediction"),
                            emotion = obj.getString("emotion_prediction"),
                            result = obj.getString("result")
                        )
                    )
                }

                setResults(resultList)
                if (resultList.isEmpty()) {
                    setStatus("No data received from the server.")
                } else {
                    val lastTime = resultList.lastOrNull()?.time
                    setLastUpdated(lastTime)
                    setStatus("Last updated at $lastTime")
                }
            } else {
                setStatus("Error: Server responded with code $responseCode")
            }

        } catch (e: Exception) {
            e.printStackTrace()
            setStatus("Error: ${e.message ?: "Unknown exception"}")
            setDebugInfo("Exception:\n${e.stackTraceToString()}")
            setResults(emptyList())
        } finally {
            setLoading(false)
        }
    }
}

@Composable
fun ResultTable(results: List<ResultItem>) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            HeaderCell("Time", modifier = Modifier.weight(1f))
            HeaderCell("Age", modifier = Modifier.weight(1f))
            HeaderCell("Gender", modifier = Modifier.weight(1f))
            HeaderCell("Emotion", modifier = Modifier.weight(1f))
            HeaderCell("Result", modifier = Modifier.weight(1f))
        }

        Divider()

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp)
        ) {
            items(results) { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    DataCell(item.time, modifier = Modifier.weight(1f))
                    DataCell(item.age, modifier = Modifier.weight(1f))
                    DataCell(item.gender, modifier = Modifier.weight(1f))
                    DataCell(item.emotion, modifier = Modifier.weight(1f))
                    DataCell(item.result, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}


@Composable
fun HeaderCell(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontWeight = FontWeight.Bold,
        modifier = modifier,
        fontSize = 14.sp
    )
}

@Composable
fun DataCell(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        fontSize = 14.sp
    )
}


suspend fun fetchResultsFromApi(): List<ResultItem> {
    return withContext(Dispatchers.IO) {
        val url = URL("https://smartwatchforchildsafety-bffrfaahgtahb9bg.italynorth-01.azurewebsites.net/api/smartwatch/data")
        val connection = url.openConnection() as HttpURLConnection

        return@withContext try {
            connection.requestMethod = "GET"
            connection.connect()

            if (connection.responseCode == 200) {
                val responseText = connection.inputStream.bufferedReader().readText()
                val jsonArray = JSONArray(responseText)
                val resultList = mutableListOf<ResultItem>()

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    resultList.add(
                        ResultItem(
                            time = obj.getString("timestamp"),
                            age = obj.getString("age_prediction"),
                            gender = obj.getString("gender_prediction"),
                            emotion = obj.getString("emotion_prediction"),
                            result = obj.getString("result")
                        )
                    )
                }
                resultList
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        } finally {
            connection.disconnect()
        }
    }
}

@Composable
fun CenteredScreen(title: String, navController: NavHostController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = title, fontSize = 24.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = { navController.popBackStack() }) {
            Text("Back")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainPagePreview() {
    SmartwatchAndroidApplicationTheme {
        val navController = rememberNavController()
        MainPage(navController = navController)
    }
}