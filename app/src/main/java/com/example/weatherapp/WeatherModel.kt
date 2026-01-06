package com.example.weatherapp

import com.google.gson.annotations.SerializedName

data class WeatherResponse(
    val name: String,
    val main: Main,
    val weather: List<Weather>,
    val wind: Wind,
    val sys: Sys,
    val dt: Long
)
data class Sys(
    val sunrise: Long,
    val sunset: Long
)
data class Main(
    val temp: Double,
    val humidity: Int,
    @SerializedName("feels_like")
    val feelsLike: Double
)

data class Weather(
    val main: String,
    val description: String,
    val icon: String
)

data class Wind(
    val speed: Double
)

// --- Model untuk Forecast (Ramalan) ---

data class ForecastResponse(
    val list: List<ForecastItem>
)

data class ForecastItem(
    val dt_txt: String, // Waktu prediksi, misal "2024-01-01 15:00:00"
    val main: Main,     // Menggunakan ulang class Main yang lama
    val weather: List<Weather>, // Menggunakan ulang class Weather yang lama
    val wind: Wind      // Menggunakan ulang class Wind yang lama
)