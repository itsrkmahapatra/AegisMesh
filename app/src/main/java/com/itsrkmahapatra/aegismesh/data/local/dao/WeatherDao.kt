package com.itsrkmahapatra.aegismesh.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.itsrkmahapatra.aegismesh.data.local.entity.WeatherGridPointEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WeatherDao {

    @Query("SELECT * FROM weather_grid_points WHERE hourOffset = :hourOffset ORDER BY latitude ASC, longitude ASC")
    fun getWeatherForHour(hourOffset: Int): Flow<List<WeatherGridPointEntity>>

    @Query("SELECT * FROM weather_grid_points ORDER BY hourOffset ASC")
    fun getAllForecastPoints(): Flow<List<WeatherGridPointEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatch(points: List<WeatherGridPointEntity>)

    @Query("DELETE FROM weather_grid_points")
    suspend fun clearOldForecast()

    @Query("SELECT COUNT(*) FROM weather_grid_points")
    suspend fun getCount(): Int
}
