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

import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import kotlinx.coroutines.launch

data class ResultItem(
    val time: String,
    val age: String,
    val gender: String,
    val emotion: String,
    val result: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmartwatchAndroidApplicationTheme {
                val navController = rememberNavController()

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            title = { Text("Smartwatch Manager") }
                        )
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "main",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("main") { MainPage(navController) }
                        composable("profile") { ProfilePage(navController) }
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
            onClick = { navController.navigate("profile") },
            modifier = Modifier
                .width(buttonWidth)
                .height(56.dp),
        ) {
            Text("Profile")
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
fun ProfilePage(navController: NavHostController) {
    CenteredScreen(title = "Profile Page", navController)
}

@Composable
fun ConfigurationPage(navController: NavHostController) {
    CenteredScreen(title = "Configuration Page", navController)
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
