package com.stesholdings.snagreport.data

import java.io.File
import kotlinx.coroutines.flow.Flow

class SnagRepository(private val dao: SnagDao) {

    fun observeAll(): Flow<List<SnagEntity>> = dao.observeAll()

    suspend fun getById(id: Long): SnagEntity? = dao.getById(id)

    suspend fun insert(entity: SnagEntity): Long = dao.insert(entity)

    suspend fun update(entity: SnagEntity) = dao.update(entity)

    /** Deletes the row and, if it is no longer referenced, the image file it points to. */
    suspend fun delete(entity: SnagEntity) {
        dao.delete(entity)
        deleteImageFile(entity.imagePath)
    }

    fun deleteImageFile(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).delete() }
    }
}
