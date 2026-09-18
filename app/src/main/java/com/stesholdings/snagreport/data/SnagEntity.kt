package com.stesholdings.snagreport.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "snags")
data class SnagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val imagePath: String?,
    val buildingName: String,
    val level: String,
    val roomLocation: String,
    val rating: Rating,
    val description: String,
    val createdAt: Long,
    val updatedAt: Long
)
