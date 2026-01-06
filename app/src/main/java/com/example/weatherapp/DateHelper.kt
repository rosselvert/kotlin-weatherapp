package com.example.weatherapp

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateHelper {
    // Fungsi mengubah timestamp (Long) ke Jam (Contoh: 06:00)
    fun formatTime(timestamp: Long): String {
        // API OpenWeatherMap pakai satuan detik, Java pakai milidetik, jadi dikali 1000
        val date = Date(timestamp * 1000)
        val format = SimpleDateFormat("HH:mm", Locale("id", "ID"))
        return format.format(date)
    }

    // Fungsi mengubah teks tanggal API ke format Hari (Contoh: Senin, 14:00)
    fun formatForecastDate(dateString: String): String {
        // Format asli dari API: "2024-01-01 15:00:00"
        val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        // Format yang kita mau: "Senin, 15:00"
        val outputFormat = SimpleDateFormat("EEEE, HH:mm", Locale("id", "ID"))

        return try {
            val date = inputFormat.parse(dateString)
            outputFormat.format(date ?: Date())
        } catch (e: Exception) {
            dateString // Jika error, kembalikan teks aslinya
        }
    }
}