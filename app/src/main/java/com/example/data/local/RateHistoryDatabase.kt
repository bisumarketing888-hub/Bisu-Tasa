package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "rate_history")
data class RateHistoryEntity(
    @PrimaryKey val dateKey: String, // yyyy-MM-dd (one consolidated record per day)
    val dateFormatted: String,       // dd/MM/yyyy
    val bcvRate: Double,
    val binanceRate: Double,
    val binanceMerchant: String,
    val differentialPercent: Int,
    val imageSent: Boolean = false,
    val differentialSent: Boolean = false,
    val updatedAtMillis: Long = System.currentTimeMillis(),
)

@Dao
interface RateHistoryDao {
    @Query("SELECT * FROM rate_history ORDER BY dateKey DESC LIMIT 60")
    fun observeAll(): Flow<List<RateHistoryEntity>>

    @Query("SELECT * FROM rate_history WHERE dateKey = :dateKey LIMIT 1")
    suspend fun getByDateKey(dateKey: String): RateHistoryEntity?

    @Query("SELECT * FROM rate_history WHERE dateKey < :todayKey AND bcvRate > 0 ORDER BY dateKey DESC LIMIT 1")
    suspend fun getLatestBeforeDate(todayKey: String): RateHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: RateHistoryEntity)

    @Query("UPDATE rate_history SET imageSent = 1, updatedAtMillis = :ts WHERE dateKey = :dateKey")
    suspend fun markImageSent(dateKey: String, ts: Long = System.currentTimeMillis())

    @Query("UPDATE rate_history SET differentialSent = 1, updatedAtMillis = :ts WHERE dateKey = :dateKey")
    suspend fun markDifferentialSent(dateKey: String, ts: Long = System.currentTimeMillis())

    @Query("DELETE FROM rate_history WHERE dateKey = :dateKey")
    suspend fun deleteByDateKey(dateKey: String)
}

@Database(
    entities = [RateHistoryEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun rateHistoryDao(): RateHistoryDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tasa_bisu_history.db",
                ).build().also { instance = it }
            }
        }
    }
}
