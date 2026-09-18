package com.stesholdings.snagreport.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SnagDao {

    @Query("SELECT * FROM snags ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SnagEntity>>

    @Query("SELECT * FROM snags WHERE id = :id")
    suspend fun getById(id: Long): SnagEntity?

    @Insert
    suspend fun insert(entity: SnagEntity): Long

    @Update
    suspend fun update(entity: SnagEntity)

    @Delete
    suspend fun delete(entity: SnagEntity)
}
