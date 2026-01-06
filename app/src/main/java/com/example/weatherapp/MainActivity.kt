package com.example.weatherapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

// --- 1. DEFINISI SCREEN (RUTE) ---
sealed class Screen(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Home : Screen("home", "Cuaca", Icons.Default.Home)
    object Forecast : Screen("forecast", "Ramalan", Icons.Default.DateRange)
    object Favorite : Screen("favorite", "Favorit", Icons.Default.Favorite) // Halaman Baru
    object About : Screen("about", "Tentang", Icons.Default.Info)
}

// --- 2. HELPER BACKGROUND GRADIENT ---
fun getWeatherGradient(weatherMain: String?): Brush {
    val colorClear = listOf(Color(0xFF4FA3F7), Color(0xFFB3E5FC))
    val colorRain = listOf(Color(0xFF546E7A), Color(0xFFCFD8DC))
    val colorClouds = listOf(Color(0xFF90A4AE), Color(0xFFECEFF1))
    val colorDefault = listOf(Color(0xFF81D4FA), Color(0xFFE1F5FE))
    val colors = when (weatherMain) {
        "Clear" -> colorClear
        "Rain", "Drizzle", "Thunderstorm" -> colorRain
        "Clouds" -> colorClouds
        else -> colorDefault
    }
    return Brush.verticalGradient(colors)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MainApp()
            }
        }
    }
}

// --- 3. MAIN APP (NAVIGASI) ---
@Composable
fun MainApp() {
    val navController = rememberNavController()
    // ViewModel di-share ke semua halaman
    val viewModel: WeatherViewModel = viewModel()

    val items = listOf(Screen.Home, Screen.Forecast, Screen.Favorite, Screen.About)

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentRoute == screen.route,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
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
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) { WeatherScreen(viewModel) }
            composable(Screen.Forecast.route) { ForecastScreen(viewModel) }
            composable(Screen.Favorite.route) { FavoriteScreen(viewModel, navController) }
            composable(Screen.About.route) { AboutScreen() }
        }
    }
}

// --- 4. HOME SCREEN (CUACA) ---
@Composable
fun WeatherScreen(viewModel: WeatherViewModel) {
    var city by remember { mutableStateOf("") }
    val weatherState by viewModel.weatherState.collectAsState()

    // Ambil status cuaca untuk background
    val weatherMain = if (weatherState is WeatherState.Success) {
        (weatherState as WeatherState.Success).data.weather.firstOrNull()?.main
    } else null
    val backgroundBrush = getWeatherGradient(weatherMain)

    // Setup GPS
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            getCurrentLocation(context, fusedLocationClient, viewModel)
        } else {
            Toast.makeText(context, "Izin GPS ditolak", Toast.LENGTH_SHORT).show()
        }
    }

    // Auto-Run GPS
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            getCurrentLocation(context, fusedLocationClient, viewModel)
        } else {
            locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(brush = backgroundBrush)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Search Bar
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it },
                    label = { Text("Cari Kota...") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White.copy(alpha = 0.5f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.5f)
                    ),
                    trailingIcon = {
                        IconButton(onClick = {
                            viewModel.fetchWeather(city)
                            viewModel.fetchForecast(city)
                        }) {
                            Icon(imageVector = Icons.Default.Search, contentDescription = "Cari")
                        }
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                            getCurrentLocation(context, fusedLocationClient, viewModel)
                        } else {
                            locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        }
                    },
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.width(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(imageVector = Icons.Default.LocationOn, contentDescription = "GPS")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            when (val state = weatherState) {
                is WeatherState.Loading -> CircularProgressIndicator(color = Color.White)
                is WeatherState.Error -> Text(text = state.message, color = MaterialTheme.colorScheme.error)
                is WeatherState.Success -> {
                    // Cek apakah kota ini ada di favorit
                    val isFavorite = viewModel.savedCities.contains(state.data.name)

                    WeatherDetail(
                        data = state.data,
                        isFavorite = isFavorite,
                        onFavoriteClick = { viewModel.toggleFavorite(state.data.name) }
                    )
                }
            }
        }
    }
}

// --- 5. FORECAST SCREEN ---
@Composable
fun ForecastScreen(viewModel: WeatherViewModel) {
    val forecastState by viewModel.forecastState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Ramalan 5 Hari (per 3 Jam)", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 16.dp))

        when (val state = forecastState) {
            is ForecastState.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is ForecastState.Error -> Text(text = "Belum ada data / Error: ${state.message}", color = MaterialTheme.colorScheme.error)
            is ForecastState.Success -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.data.list) { item -> ForecastItemRow(item) }
                }
            }
        }
    }
}

// --- 6. FAVORITE SCREEN (HALAMAN BARU) ---
@Composable
fun FavoriteScreen(viewModel: WeatherViewModel, navController: androidx.navigation.NavController) {
    val savedCities = viewModel.savedCities // Mengambil list dari ViewModel

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Kota Favorit ❤️", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 16.dp))

        if (savedCities.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Belum ada kota disimpan.", color = Color.Gray)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(savedCities) { city ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            // Klik kota -> Load cuaca & Pindah ke Home
                            viewModel.fetchWeather(city)
                            viewModel.fetchForecast(city)
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Home.route) { inclusive = true }
                            }
                        },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(text = city, style = MaterialTheme.typography.titleMedium)
                            }
                            IconButton(onClick = { viewModel.toggleFavorite(city) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- 7. ABOUT SCREEN ---
@Composable
fun AboutScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(80.dp).padding(bottom = 16.dp), tint = MaterialTheme.colorScheme.primary)
        Text("Weather App", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("Versi 1.1 (Final)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
        Spacer(modifier = Modifier.height(24.dp))
        Text("Aplikasi Cuaca Lengkap dengan fitur GPS, Favorit, dan UI Dinamis.", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(modifier = Modifier.height(24.dp))
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Fitur:", fontWeight = FontWeight.Bold)
                Text("- Pencarian Kota & GPS")
                Text("- Ramalan 5 Hari")
                Text("- Simpan Kota Favorit")
                Text("- Background Berubah Warna")
            }
        }
    }
}

// --- 8. UI COMPONENTS HELPER ---

@Composable
fun WeatherDetail(
    data: WeatherResponse,
    isFavorite: Boolean,
    onFavoriteClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Judul Kota + Icon Love
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = data.name, style = MaterialTheme.typography.headlineLarge)
            IconButton(onClick = onFavoriteClick) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorit",
                    tint = if (isFavorite) Color.Red else Color.DarkGray
                )
            }
        }

        Text(text = "Update: ${DateHelper.formatTime(data.dt)}", style = MaterialTheme.typography.labelMedium)
        Text(text = "${data.main.temp}°C", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold)

        val iconCode = data.weather.firstOrNull()?.icon
        val iconUrl = "https://openweathermap.org/img/wn/$iconCode@4x.png"
        AsyncImage(model = iconUrl, contentDescription = null, modifier = Modifier.size(120.dp))

        Text(text = data.weather.firstOrNull()?.description?.capitalize() ?: "", style = MaterialTheme.typography.headlineSmall)

        Spacer(modifier = Modifier.height(24.dp))

        // Info Grid (Transparan)
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.6f))) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceAround) {
                InfoItem("Terasa", "${data.main.feelsLike}°C")
                InfoItem("Lembap", "${data.main.humidity}%")
                InfoItem("Angin", "${data.wind.speed} m/s")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Info Matahari (Transparan)
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.6f))) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(painter = painterResource(android.R.drawable.ic_menu_day), contentDescription = null)
                    Text("Terbit", style = MaterialTheme.typography.labelSmall)
                    Text(DateHelper.formatTime(data.sys.sunrise), style = MaterialTheme.typography.titleMedium)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = Icons.Default.Info, contentDescription = null) // Bisa ganti icon bulan
                    Text("Terbenam", style = MaterialTheme.typography.labelSmall)
                    Text(DateHelper.formatTime(data.sys.sunset), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
fun ForecastItemRow(item: ForecastItem) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                val formattedDate = DateHelper.formatForecastDate(item.dt_txt)
                val parts = formattedDate.split(", ")
                if (parts.size == 2) {
                    Text(parts[0], style = MaterialTheme.typography.bodySmall)
                    Text(parts[1], style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                } else {
                    Text(formattedDate, style = MaterialTheme.typography.titleMedium)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val iconUrl = "https://openweathermap.org/img/wn/${item.weather.firstOrNull()?.icon}.png"
                AsyncImage(model = iconUrl, contentDescription = null, modifier = Modifier.size(40.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("${item.main.temp}°C", style = MaterialTheme.typography.titleLarge)
            }
            Text(item.weather.firstOrNull()?.main ?: "", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun InfoItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@SuppressLint("MissingPermission")
fun getCurrentLocation(context: android.content.Context, fusedLocationClient: com.google.android.gms.location.FusedLocationProviderClient, viewModel: WeatherViewModel) {
    Toast.makeText(context, "Mencari lokasi...", Toast.LENGTH_SHORT).show()
    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
        .addOnSuccessListener { location ->
            if (location != null) {
                viewModel.fetchWeatherByLocation(location.latitude, location.longitude)
            } else {
                Toast.makeText(context, "Lokasi tidak ditemukan", Toast.LENGTH_SHORT).show()
            }
        }
        .addOnFailureListener {
            Toast.makeText(context, "Gagal GPS: ${it.message}", Toast.LENGTH_SHORT).show()
        }
}

fun String.capitalize() = replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }