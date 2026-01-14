@file:OptIn(InternalSerializationApi::class)

package pl.edu.ur.kh131440.cartraker.utils

import android.content.Context
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object SharedPrefsHelper {
    private const val PREFS_NAME = "car_locator_prefs"
    private const val KEY_ACTIVE_LOCATION = "car_active_location"
    private const val KEY_HISTORY_LOCATIONS = "car_history_locations"

    // --- Metody dla AKTYWNEJ lokalizacji ---

    fun saveActiveCarLocation(context: Context, lat: Double, lng: Double, note: String?, photoPath: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val newLocation = CarLocation(
            latitude = lat,
            longitude = lng,
            timestamp = System.currentTimeMillis(),
            note = note,
            photoPath = photoPath
        )

        // Zapisz jako aktywną lokalizację
        val activeLocationJson = Json.encodeToString(newLocation)
        prefs.edit().putString(KEY_ACTIVE_LOCATION, activeLocationJson).apply()

        // Dodaj również do historii
        addLocationToHistory(context, newLocation)
    }


    fun getActiveCarLocation(context: Context): CarLocation? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_ACTIVE_LOCATION, null)
        return if (jsonString != null) {
            try {
                Json.decodeFromString<CarLocation>(jsonString)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    fun clearActiveCarLocation(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_ACTIVE_LOCATION).apply()
    }

    // --- Metody dla HISTORII lokalizacji ---

    private fun getHistory(context: Context): MutableList<CarLocation> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_HISTORY_LOCATIONS, null)
        return if (jsonString != null) {
            try {
                Json.decodeFromString(ListSerializer(CarLocation.serializer()), jsonString).toMutableList()
            } catch (e: Exception) {
                mutableListOf()
            }
        } else {
            mutableListOf()
        }
    }

    private fun saveHistory(context: Context, locations: List<CarLocation>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = Json.encodeToString(ListSerializer(CarLocation.serializer()), locations)
        prefs.edit().putString(KEY_HISTORY_LOCATIONS, jsonString).apply()
    }

    private fun addLocationToHistory(context: Context, location: CarLocation) {
        val history = getHistory(context)
        history.add(0, location) // Dodaj na początek listy
        saveHistory(context, history)
    }

    fun getHistoryLocations(context: Context): List<CarLocation> {
        return getHistory(context)
    }

    fun deleteLocationFromHistory(context: Context, location: CarLocation) {
        val history = getHistory(context)
        history.remove(location)
        saveHistory(context, history)
    }

    fun clearHistory(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_HISTORY_LOCATIONS).apply()
    }
}