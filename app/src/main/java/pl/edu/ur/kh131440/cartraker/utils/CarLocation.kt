package pl.edu.ur.kh131440.cartraker.utils

import android.annotation.SuppressLint
import kotlinx.serialization.Serializable

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class CarLocation(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val note: String? = null,
    val photoPath: String? = null
)