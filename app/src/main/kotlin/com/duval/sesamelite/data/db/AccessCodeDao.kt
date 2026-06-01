package com.duval.sesamelite.data.db

import androidx.room.*
import com.duval.sesamelite.data.model.AccessCode
import kotlinx.coroutines.flow.Flow

@Dao
interface AccessCodeDao {
    @Query("SELECT * FROM access_codes ORDER BY label ASC")
    fun getAllFlow(): Flow<List<AccessCode>>

    @Query("SELECT * FROM access_codes ORDER BY label ASC")
    suspend fun getAll(): List<AccessCode>

    @Query("SELECT * FROM access_codes WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): AccessCode?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(code: AccessCode)

    @Update
    suspend fun update(code: AccessCode)

    @Delete
    suspend fun delete(code: AccessCode)

    @Query("DELETE FROM access_codes")
    suspend fun deleteAll()
}
