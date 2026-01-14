package pl.edu.ur.kh131440.cartraker

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Vibrator
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import pl.edu.ur.kh131440.cartraker.ui.theme.HelloWorldTheme
import pl.edu.ur.kh131440.cartraker.utils.SharedPrefsHelper
import com.google.android.gms.location.LocationServices
import com.google.maps.android.compose.*
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class MapActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HelloWorldTheme {
                MapScreen(this)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(activity: ComponentActivity) {
    val context = activity
    var showToast by remember { mutableStateOf<String?>(null) }
    var vibrate by remember { mutableStateOf(false) }

    val carLocation = SharedPrefsHelper.getActiveCarLocation(context)
    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()?.let {
                userLocation = LatLng(it.latitude, it.longitude)
            }
        }
        isLoading = false
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Mapa samochodu") }) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator()
            } else if (carLocation != null && userLocation != null) {
                val start = userLocation!!
                val end = LatLng(carLocation.latitude, carLocation.longitude)
                val cameraPositionState = rememberCameraPositionState {
                    position = CameraPosition.fromLatLngZoom(start, 16f)
                }
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState
                ) {
                    Marker(state = MarkerState(position = start), title = "Tu jesteś")
                    Marker(state = MarkerState(position = end), title = "Samochód")
                    Polyline(points = listOf(start, end), color = MaterialTheme.colorScheme.primary, width = 8f)
                }

                val distance = FloatArray(1)
                Location.distanceBetween(start.latitude, start.longitude, end.latitude, end.longitude, distance)
                if (distance[0] < 30 && !vibrate) {
                    vibrate = true
                    @Suppress("DEPRECATION")
                    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(500)
                    showToast = "Jesteś blisko samochodu!"
                }
            } else {
                Text("Brak zapisanej lokalizacji lub lokalizacji użytkownika.")
            }

            showToast?.let {
                LaunchedEffect(it) {
                    Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                    showToast = null
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
suspend fun Task<Location?>.await(): Location? =
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resume(null) }
        addOnCanceledListener { cont.cancel() }
    }