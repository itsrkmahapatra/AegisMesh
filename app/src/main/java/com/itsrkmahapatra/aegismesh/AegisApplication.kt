package com.itsrkmahapatra.aegismesh

import android.app.Application
import com.itsrkmahapatra.aegismesh.battery.CellOutageMonitor
import com.itsrkmahapatra.aegismesh.data.local.AegisDatabase
import com.itsrkmahapatra.aegismesh.data.remote.NdmaDirectRssFetcher
import com.itsrkmahapatra.aegismesh.data.remote.OpenMeteoDirectFetcher
import com.itsrkmahapatra.aegismesh.data.repository.AlertRepository
import com.itsrkmahapatra.aegismesh.data.repository.WeatherRepository
import org.maplibre.android.MapLibre

class AegisApplication : Application() {

    lateinit var database: AegisDatabase
        private set

    lateinit var alertRepository: AlertRepository
        private set

    lateinit var weatherRepository: WeatherRepository
        private set

    lateinit var cellOutageMonitor: CellOutageMonitor
        private set

    override fun onCreate() {
        super.onCreate()

        // Initialize MapLibre Native SDK with custom user agent & keyless open styles
        MapLibre.getInstance(this)

        database = AegisDatabase.getDatabase(this)
        alertRepository = AlertRepository(database.alertDao())
        weatherRepository = WeatherRepository(database.weatherDao())
        cellOutageMonitor = CellOutageMonitor(this)
    }
}

