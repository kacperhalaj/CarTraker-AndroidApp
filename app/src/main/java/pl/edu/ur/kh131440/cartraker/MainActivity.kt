package pl.edu.ur.kh131440.cartraker

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.location.LocationManagerCompat
import coil.compose.rememberAsyncImagePainter
import pl.edu.ur.kh131440.cartraker.ui.theme.HelloWorldTheme
import pl.edu.ur.kh131440.cartraker.utils.CarLocation
import pl.edu.ur.kh131440.cartraker.utils.SharedPrefsHelper
import com.google.android.gms.location.LocationServices
import androidx.activity.compose.rememberLauncherForActivityResult
import pl.edu.ur.kh131440.cartraker.utils.ThemeManager
import com.google.android.gms.location.Priority
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Objects
import kotlinx.coroutines.*
import pl.edu.ur.kh131440.cartraker.utils.NetworkUtils

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.applyTheme(ThemeManager.getTheme(this))

        if (!isTaskRoot && intent.hasCategory(Intent.CATEGORY_LAUNCHER) && intent.action == Intent.ACTION_MAIN) {
            finish()
            return
        }
        setContent {
            val currentTheme = remember { mutableStateOf(ThemeManager.getTheme(this)) }
            HelloWorldTheme(
                darkTheme = when (currentTheme.value) {
                    ThemeManager.THEME_DARK -> true
                    ThemeManager.THEME_LIGHT -> false
                    else -> isSystemInDarkTheme()
                }
            ) {
                AppNavigator(
                    currentTheme = currentTheme.value,
                    onThemeChange = { theme ->
                        ThemeManager.setTheme(this, theme)
                        currentTheme.value = theme
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigator(currentTheme: String, onThemeChange: (String) -> Unit) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Lokalizator Samochodu", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = {
                        val newTheme = if (currentTheme == ThemeManager.THEME_DARK) ThemeManager.THEME_LIGHT else ThemeManager.THEME_DARK
                        onThemeChange(newTheme)
                    }) {
                        Icon(
                            imageVector = if (currentTheme == ThemeManager.THEME_DARK) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                            contentDescription = "Zmień motyw"
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Rounded.Home, contentDescription = "Główna") },
                    label = { Text("Główna") },
                    selected = currentScreen == Screen.Home,
                    onClick = { currentScreen = Screen.Home }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Rounded.History, contentDescription = "Historia") },
                    label = { Text("Historia") },
                    selected = currentScreen == Screen.History,
                    onClick = { currentScreen = Screen.History }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Rounded.Info, contentDescription = "O aplikacji") },
                    label = { Text("O aplikacji") },
                    selected = currentScreen == Screen.About,
                    onClick = { currentScreen = Screen.About }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (currentScreen) {
                is Screen.Home -> MainScreen()
                is Screen.History -> HistoryScreen()
                is Screen.About -> AboutScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    var isCarLocationSaved by remember { mutableStateOf(SharedPrefsHelper.getActiveCarLocation(context) != null) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf<Location?>(null) }
    var showEnableLocationDialog by remember { mutableStateOf(false) }
    var showEnableInternetDialog by remember { mutableStateOf(false) } // dialog dla internetu
    var isGettingLocation by remember { mutableStateOf(false) } // pokażemy loader podczas pobierania

    val onLocationSaved = {
        isCarLocationSaved = true
        showSaveDialog = null
        Toast.makeText(context, "Lokalizacja samochodu zapisana!", Toast.LENGTH_SHORT).show()
    }

    if (showEnableLocationDialog) {
        AlertDialog(
            onDismissRequest = { showEnableLocationDialog = false },
            title = { Text("Lokalizacja jest wyłączona") },
            text = { Text("Aby zapisać pozycję samochodu, musisz włączyć usługi lokalizacyjne w ustawieniach telefonu.") },
            confirmButton = {
                TextButton(onClick = {
                    showEnableLocationDialog = false
                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }) { Text("Idź do ustawień") }
            },
            dismissButton = {
                TextButton(onClick = { showEnableLocationDialog = false }) { Text("Anuluj") }
            }
        )
    }

    if (showEnableInternetDialog) {
        // Jeden przycisk otwierający ustawienia sieci — prostsze dla użytkownika
        AlertDialog(
            onDismissRequest = { showEnableInternetDialog = false },
            title = { Text("Brak połączenia z internetem") },
            text = {
                Column {
                    Text("Aby korzystać z pełnej funkcjonalności (mapy, zdjęcia, nawigacja), włącz połączenie z internetem.")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Po naciśnięciu przycisku otworzą się ustawienia sieci, gdzie możesz włączyć Wi‑Fi lub dane komórkowe.")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showEnableInternetDialog = false
                    openNetworkSettings(context)
                }) { Text("Otwórz ustawienia sieci") }
            },
            dismissButton = {
                TextButton(onClick = { showEnableInternetDialog = false }) { Text("Anuluj") }
            }
        )
    }

    showSaveDialog?.let { location ->
        SaveLocationDialog(
            location = location,
            onDismiss = { showSaveDialog = null },
            onSave = onLocationSaved
        )
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) {
                if (isLocationEnabled(context)) {
                    // PRZED zapisem sprawdzamy internet
                    if (!NetworkUtils.isInternetAvailable(context)) {
                        showEnableInternetDialog = true
                    } else {
                        isGettingLocation = true
                        saveCurrentLocation(
                            context,
                            onLocationReady = { location ->
                                isGettingLocation = false
                                if (location != null) showSaveDialog = location
                                else Toast.makeText(context, "Nie udało się pobrać lokalizacji.", Toast.LENGTH_LONG).show()
                            },
                            onTimeout = {
                                isGettingLocation = false
                                Toast.makeText(context, "Operacja trwała zbyt długo. Spróbuj ponownie.", Toast.LENGTH_LONG).show()
                            }
                        )
                    }
                } else {
                    showEnableLocationDialog = true
                }
            } else {
                Toast.makeText(context, "Brak uprawnień do lokalizacji", Toast.LENGTH_SHORT).show()
            }
        }
    )

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Potwierdzenie") },
            text = { Text("Czy na pewno chcesz zresetować aktywną lokalizację samochodu? Zostanie ona w historii.") },
            confirmButton = {
                TextButton(onClick = {
                    SharedPrefsHelper.clearActiveCarLocation(context)
                    isCarLocationSaved = false
                    showResetDialog = false
                    Toast.makeText(context, "Aktywna lokalizacja została zresetowana", Toast.LENGTH_SHORT).show()
                }) { Text("Resetuj") }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Anuluj") }
            }
        )
    }

    // Główny kontener z pionowym scrollowaniem — pozwala przewinąć zawartość gdy ekran jest poziomy
    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(imageVector = Icons.Rounded.DirectionsCar, contentDescription = "Ikona samochodu", modifier = Modifier.size(100.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Zaparkowane? Zapisz pozycję!", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(40.dp))

            ActionCard(
                title = "Zapisz lokalizację samochodu",
                icon = Icons.Rounded.GpsFixed,
                onClick = {
                    val permission = Manifest.permission.ACCESS_FINE_LOCATION
                    // PRZED sprawdzeniem uprawnienia: sprawdźmy internet
                    if (!NetworkUtils.isInternetAvailable(context)) {
                        showEnableInternetDialog = true
                        return@ActionCard
                    }

                    if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                        if (isLocationEnabled(context)) {
                            isGettingLocation = true
                            saveCurrentLocation(
                                context,
                                onLocationReady = { location ->
                                    isGettingLocation = false
                                    if (location != null) showSaveDialog = location
                                    else Toast.makeText(context, "Nie udało się pobrać lokalizacji.", Toast.LENGTH_LONG).show()
                                },
                                onTimeout = {
                                    isGettingLocation = false
                                    Toast.makeText(context, "Operacja trwała zbyt długo. Spróbuj ponownie.", Toast.LENGTH_LONG).show()
                                }
                            )
                        } else {
                            showEnableLocationDialog = true
                        }
                    } else {
                        locationPermissionLauncher.launch(permission)
                    }
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
            ActionCard(
                title = "Znajdź mój samochód",
                icon = Icons.Rounded.Map,
                onClick = {
                    val carLocation = SharedPrefsHelper.getActiveCarLocation(context)
                    carLocation?.let { navigateToLocation(context, it.latitude, it.longitude) }
                },
                isEnabled = isCarLocationSaved
            )
            Spacer(modifier = Modifier.height(24.dp))

            AnimatedVisibility(visible = isCarLocationSaved) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(imageVector = Icons.Rounded.CheckCircle, contentDescription = "Zapisano", tint = MaterialTheme.colorScheme.primary)
                        Text("Aktywna lokalizacja jest zapisana!", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { showResetDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Resetuj")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Resetuj lokalizację")
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            // Dodajemy trochę przestrzeni na dole żeby przy przewijaniu nie ciąć zawartości
            Spacer(modifier = Modifier.height(48.dp))
        }

        // Overlay - prosty loader gdy pobieramy lokalizację
        if (isGettingLocation) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)) {
                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Pobieranie lokalizacji… Proszę czekać", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}


@Composable
fun SaveLocationDialog(location: Location, onDismiss: () -> Unit, onSave: () -> Unit) {
    val context = LocalContext.current
    var note by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var tempImageUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            if (success) {
                imageUri = tempImageUri
            }
        }
    )

    // dialog content może być długi — robimy pionowy scroll wewnątrz
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Zapisz szczegóły lokalizacji") },
        text = {
            Column(modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(end = 4.dp) // mały padding, by pasek przewijania nie nachodził
            ) {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Notatka (np. Poziom -2, sektor C)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                if (imageUri != null) {
                    Image(
                        painter = rememberAsyncImagePainter(imageUri),
                        contentDescription = "Zdjęcie miejsca parkingowego",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Button(
                    onClick = {
                        val file = createImageFile(context)
                        tempImageUri = FileProvider.getUriForFile(Objects.requireNonNull(context), context.packageName + ".provider", file)
                        cameraLauncher.launch(tempImageUri)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AddAPhoto, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (imageUri == null) "Dodaj zdjęcie" else "Zmień zdjęcie")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                SharedPrefsHelper.saveActiveCarLocation(context, location.latitude, location.longitude, note.takeIf { it.isNotBlank() }, imageUri?.toString())
                onSave()
            }) {
                Text("Zapisz")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Anuluj") }
        }
    )
}


@Composable
fun HistoryScreen() {
    val context = LocalContext.current
    var locations by remember { mutableStateOf(SharedPrefsHelper.getHistoryLocations(context)) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text("Potwierdzenie") },
            text = { Text("Czy na pewno chcesz usunąć całą historię lokalizacji? Ta operacja jest nieodwracalna.") },
            confirmButton = {
                TextButton(onClick = {
                    SharedPrefsHelper.clearHistory(context)
                    locations = emptyList()
                    showDeleteAllDialog = false
                    Toast.makeText(context, "Historia została usunięta", Toast.LENGTH_SHORT).show()
                }) { Text("Usuń wszystko") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) { Text("Anuluj") }
            }
        )
    }

    // IMPORTANT: don't put LazyColumn inside a verticalScroll parent (causes crashes / measurement issues).
    // Use a normal Column and let LazyColumn handle scrolling itself.
    Column(modifier = Modifier.fillMaxSize()) {
        if (locations.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = { showDeleteAllDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = "Usuń wszystko")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Wyczyść historię")
                }
            }
        }

        if (locations.isEmpty()) {
            // gdy brak pozycji — pokaż komunikat zajmujący dostępne miejsce
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Brak zapisanych lokalizacji w historii.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                items(locations) { location ->
                    HistoryItem(
                        location = location,
                        onNavigate = {
                            navigateToLocation(context, location.latitude, location.longitude)
                        },
                        onDelete = {
                            SharedPrefsHelper.deleteLocationFromHistory(context, location)
                            locations = SharedPrefsHelper.getHistoryLocations(context)
                            Toast.makeText(context, "Lokalizacja usunięta z historii", Toast.LENGTH_SHORT).show()
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

// --- NOWY KOMPONENT DIALOGU ---
@Composable
fun FullScreenImageDialog(imageUri: Uri, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onDismiss), // Zamknij po kliknięciu na tło
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = rememberAsyncImagePainter(model = imageUri),
                contentDescription = "Powiększone zdjęcie miejsca parkingowego",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp) // Dodaj marginesy
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Fit
            )
        }
    }
}


@Composable
fun HistoryItem(location: CarLocation, onNavigate: () -> Unit, onDelete: () -> Unit) {
    val date = remember {
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
        sdf.format(Date(location.timestamp))
    }
    // --- NOWY STAN DO OBSŁUGI DIALOGU ---
    var showFullScreenImage by remember { mutableStateOf<Uri?>(null) }

    showFullScreenImage?.let { uri ->
        FullScreenImageDialog(imageUri = uri, onDismiss = { showFullScreenImage = null })
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            // --- GÓRNA CZĘŚĆ KLIKALNA (NAWIGACJA) ---
            Row(
                modifier = Modifier
                    .clickable(onClick = onNavigate)
                    .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = if (location.note == null && location.photoPath == null) 16.dp else 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = date, fontWeight = FontWeight.Bold)
                    Text(text = "Szer: ${"%.4f".format(location.latitude)}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "Dł: ${"%.4f".format(location.longitude)}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Usuń lokalizację", tint = MaterialTheme.colorScheme.error)
                }
            }

            // --- DOLNA CZĘŚĆ (NOTATKA I ZDJĘCIE) ---
            location.note?.let {
                Text(
                    text = "Notatka: $it",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            location.photoPath?.let {
                val imageUri = remember { Uri.parse(it) }
                Image(
                    painter = rememberAsyncImagePainter(model = imageUri),
                    contentDescription = "Zdjęcie miejsca parkingowego",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp)
                        .height(180.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showFullScreenImage = imageUri }, // Kliknięcie powiększa zdjęcie
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionCard(title: String, icon: ImageVector, onClick: () -> Unit, isEnabled: Boolean = true) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth().height(80.dp), enabled = isEnabled, shape = RoundedCornerShape(16.dp)) {
        Row(modifier = Modifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(imageVector = icon, contentDescription = title, modifier = Modifier.size(40.dp))
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun AboutScreen() {
    val context = LocalContext.current
    // pobierz nazwę aplikacji i wersję bez rzucania wyjątkami
    val appName = try {
        context.applicationInfo.loadLabel(context.packageManager).toString()
    } catch (e: Exception) {
        "Lokalizator Samochodu"
    }
    val versionName = try {
        val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        pInfo.versionName ?: "1.0"
    } catch (e: Exception) {
        "1.0"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Top
    ) {
        Text(text = appName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Wersja: $versionName", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Opis:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Prosta aplikacja do zapamiętywania lokalizacji zaparkowanego samochodu. Możesz zapisać pozycję, dodać notatkę i zdjęcie miejsca oraz korzystać z nawigacji do zapisanej lokalizacji.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Autor:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Kacper Hałaj", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(24.dp))
    }
}

fun isLocationEnabled(context: Context): Boolean {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return LocationManagerCompat.isLocationEnabled(locationManager)
}

/**
 * Zmienione zachowanie pobierania lokalizacji:
 * 1) najpierw próbujemy pobrać lastLocation (szybkie, jeśli dostępne);
 * 2) jeśli brak lastLocation lub jest przestarzałe, wywołujemy getCurrentLocation z dłuższym timeoutem (15s);
 * 3) onTimeout wywoływany, gdy przekroczono limit czasu.
 */
@SuppressLint("MissingPermission")
fun saveCurrentLocation(context: Context, onLocationReady: (Location?) -> Unit, onTimeout: (() -> Unit)? = null) {
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    CoroutineScope(Dispatchers.Main).launch {
        try {
            // 1) szybka próba: lastLocation
            val last = withTimeoutOrNull(3000L) {
                fusedLocationClient.lastLocation.await()
            }
            if (last != null) {
                // jeśli znaleźliśmy ostatnią lokalizację, zwracamy ją od razu
                onLocationReady(last)
                return@launch
            }

            // 2) jeśli brak lastLocation, próbujemy getCurrentLocation z dłuższym timeoutem
            val location = withTimeoutOrNull(15000L) {
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
            }

            if (location != null) {
                onLocationReady(location)
            } else {
                // timeout - dajemy callback i null
                onTimeout?.invoke()
                onLocationReady(null)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Błąd podczas pobierania lokalizacji: ${e.message}", Toast.LENGTH_LONG).show()
            onLocationReady(null)
        }
    }
}

fun createImageFile(context: Context): File {
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val imageFileName = "JPEG_" + timeStamp + "_"
    val storageDir = File(context.cacheDir, "images")
    if (!storageDir.exists()) storageDir.mkdirs()
    return File.createTempFile(imageFileName, ".jpg", storageDir)
}


fun navigateToLocation(context: Context, lat: Double, lng: Double) {
    val gmmIntentUri = Uri.parse("google.navigation:q=$lat,$lng")
    val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).setPackage("com.google.android.apps.maps")
    if (mapIntent.resolveActivity(context.packageManager) != null) {
        context.startActivity(mapIntent)
    } else {
        Toast.makeText(context, "Nie znaleziono aplikacji Google Maps.", Toast.LENGTH_LONG).show()
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng"))
        context.startActivity(browserIntent)
    }
}

/**
 * Otwiera ustawienia sieci. Dla Android 10+ użyjemy panelu internetu (użytkownik szybko włączy Wi‑Fi lub dane),
 * dla starszych wersji przejdziemy do ogólnych ustawień bezprzewodowych.
 */
fun openNetworkSettings(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val panelIntent = Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
            context.startActivity(panelIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } else {
            context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    } catch (e: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) { /* ignore */ }
    } catch (_: Exception) { /* ignore */ }
}

sealed class Screen {
    object Home : Screen()
    object History : Screen()
    object About : Screen()
}