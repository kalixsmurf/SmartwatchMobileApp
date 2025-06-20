@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.smartwatchandroidapplication

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
import android.os.Build
import androidx.compose.ui.platform.LocalContext


import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat


fun showNotification(context: Context, title: String, message: String) {
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        putExtra("destination", "notification") // 👈 pass your target screen
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
            "smartwatch_channel", // ID
            "Smartwatch Alerts",  // Name shown to user
            NotificationManager.IMPORTANCE_DEFAULT
        )
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        enableEdgeToEdge()

        val startDestination = intent?.getStringExtra("destination") ?: "main" // 👈 pick destination

        setContent {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()

            LaunchedEffect(Unit) {
                while (true) {
                    val results = fetchResultsFromApi()
                    val lastSeen = getLastSeenTimestamp(context)

                    // Find the latest abnormal result
                    val latestAbnormal = results
                        .filter { it.result.equals("abnormal", ignoreCase = true) }
                        .maxByOrNull { it.time }

                    if (latestAbnormal != null && (lastSeen == null || latestAbnormal.time > lastSeen)) {
                        showNotification(
                            context = context,
                            title = "⚠️ Abnormal Result Detected",
                            message = "Age: ${latestAbnormal.age}, Gender: ${latestAbnormal.gender}, Emotion: ${latestAbnormal.emotion}"
                        )

                        // Update the last seen timestamp immediately after showing notification
                        setLastSeenTimestamp(context, latestAbnormal.time)
                    }

                    delay(30_000) // check every 30 seconds
                }
            }


            SmartwatchAndroidApplicationTheme {
                val navController = rememberNavController()

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(title = { Text("Smartwatch Manager") })
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = startDestination, // 👈 jump to "notification"
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("main") { MainPage(navController) }
                        composable("notification") { NotificationPage(navController) }
                        composable("configuration") { ConfigurationPage(navController) }
                        composable("results") { ResultsPage(navController) }
                    }
                }
            }
        }
    }
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
fun NotificationPage(navController: NavHostController) {
    val context = LocalContext.current
    var results by remember { mutableStateOf<List<ResultItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val newResults = fetchResultsFromApi()
            results = newResults
            loading = false

            // Update last seen timestamp to latest time in newResults
            val latestTime = newResults.maxByOrNull { it.time }?.time
            if (latestTime != null) {
                setLastSeenTimestamp(context, latestTime)
            }

            // Remove this block so notification is not created here:
            /*
            val abnormalItem = newResults.firstOrNull { it.result.equals("abnormal", ignoreCase = true) }
            if (abnormalItem != null) {
                showNotification(
                    context = context,
                    title = "⚠️ Abnormal Result Detected",
                    message = "Time: ${abnormalItem.time}\nAge: ${abnormalItem.age}, Gender: ${abnormalItem.gender}, Emotion: ${abnormalItem.emotion}"
                )
            }
            */

        } catch (e: Exception) {
            errorMessage = e.message
            loading = false
        }
    }


    val lastSeen = getLastSeenTimestamp(context)
    val (unread, read) = results.partition { lastSeen == null || it.time > lastSeen }

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
                verticalArrangement = Arrangement.spacedBy(12.dp) // ✅ spacing between cards
            ) {
            if (unread.isNotEmpty()) {
                    item {
                        Text("Unread", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    items(unread) { item ->
                        NotificationCard(item, isUnread = true)
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(16.dp)) // between Unread and Read
                }

                if (read.isNotEmpty()) {
                    item {
                        Text("Read", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(top = 16.dp))
                    }
                    items(read) { item ->
                        NotificationCard(item)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = { navController.popBackStack() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Back")
        }
    }
}


@Composable
fun NotificationCard(item: ResultItem, isUnread: Boolean = false) {
    val bgColor = if (isUnread) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant

    Card(
        modifier = Modifier.fillMaxWidth(),
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
