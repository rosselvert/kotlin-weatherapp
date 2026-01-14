package com.example.weatherapp

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class WeatherState {
    object Loading : WeatherState()
    data class Success(val data: WeatherResponse) : WeatherState()
    data class Error(val message: String) : WeatherState()
}

sealed class ForecastState {
    object Loading : ForecastState()
    data class Success(val data: ForecastResponse) : ForecastState()
    data class Error(val message: String) : ForecastState()
}

class WeatherViewModel : ViewModel() {

    private val _weatherState = MutableStateFlow<WeatherState>(WeatherState.Loading)
    val weatherState: StateFlow<WeatherState> = _weatherState

    private val _forecastState = MutableStateFlow<ForecastState>(ForecastState.Loading)
    val forecastState: StateFlow<ForecastState> = _forecastState

    // --- FITUR FAVORIT (INI YANG SEBELUMNYA MUNGKIN HILANG) ---
    private val _savedCities = mutableStateListOf<String>()
    val savedCities: List<String> get() = _savedCities

    fun toggleFavorite(city: String) {
        if (_savedCities.contains(city)) {
            _savedCities.remove(city)
        } else {
            _savedCities.add(city)
        }
    }
    // -----------------------------------------------------------

    private val apiKey = "f673b995bd16d6b3d2063f331eabfcf9" // <--- JANGAN LUPA API KEY

    fun fetchWeather(city: String) {
        viewModelScope.launch {
            _weatherState.value = WeatherState.Loading
            _forecastState.value = ForecastState.Loading
            try {
                val response = RetrofitInstance.api.getWeather(city, apiKey)
                _weatherState.value = WeatherState.Success(response)
                fetchForecast(city)
            } catch (e: Exception) {
                _weatherState.value = WeatherState.Error("Gagal: ${e.localizedMessage}")
                _forecastState.value = ForecastState.Error("Gagal memuat forecast")
            }
        }
    }

    fun fetchForecast(city: String) {
        viewModelScope.launch {
            try {
                val response = RetrofitInstance.api.getForecast(city, apiKey)
                _forecastState.value = ForecastState.Success(response)
            } catch (e: Exception) {
                _forecastState.value = ForecastState.Error("Gagal Forecast: ${e.localizedMessage}")
            }
        }
    }

    fun fetchWeatherByLocation(lat: Double, lon: Double) {
        viewModelScope.launch {
            _weatherState.value = WeatherState.Loading
            _forecastState.value = ForecastState.Loading
            try {
                val response = RetrofitInstance.api.getWeatherByCoordinates(lat, lon, apiKey)
                _weatherState.value = WeatherState.Success(response)
                fetchForecast(response.name)
            } catch (e: Exception) {
                _weatherState.value = WeatherState.Error("Gagal GPS: ${e.localizedMessage}")
            }
        }
    }
}