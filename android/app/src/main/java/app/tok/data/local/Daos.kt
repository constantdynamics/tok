package app.tok.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BulletDao {
    @Transaction
    @Query("SELECT * FROM bullets WHERE deleted = 0 ORDER BY sortOrder ASC, createdAt DESC")
    fun observeWithLabels(): Flow<List<BulletWithLabels>>

    @Query("SELECT * FROM bullets WHERE id = :id")
    suspend fun getById(id: String): BulletEntity?

    @Upsert
    suspend fun upsert(bullet: BulletEntity)

    @Upsert
    suspend fun upsertAll(bullets: List<BulletEntity>)

    @Query("SELECT * FROM bullets WHERE dirty = 1 AND deleted = 0")
    suspend fun getDirty(): List<BulletEntity>

    @Query("SELECT * FROM bullets WHERE deleted = 1")
    suspend fun getDeleted(): List<BulletEntity>

    @Query("SELECT * FROM bullets WHERE deleted = 0")
    suspend fun getAllActive(): List<BulletEntity>

    @Query("UPDATE bullets SET dirty = 0 WHERE id = :id")
    suspend fun clearDirty(id: String)

    @Query("DELETE FROM bullets WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("SELECT max(sortOrder) FROM bullets")
    suspend fun maxSortOrder(): Double?
}

@Dao
interface LabelDao {
    @Query("SELECT * FROM labels WHERE deleted = 0 ORDER BY createdAt ASC")
    fun observe(): Flow<List<LabelEntity>>

    @Upsert
    suspend fun upsert(label: LabelEntity)

    @Upsert
    suspend fun upsertAll(labels: List<LabelEntity>)

    @Query("SELECT * FROM labels WHERE dirty = 1 AND deleted = 0")
    suspend fun getDirty(): List<LabelEntity>

    @Query("SELECT * FROM labels WHERE deleted = 1")
    suspend fun getDeleted(): List<LabelEntity>

    @Query("SELECT * FROM labels")
    suspend fun getAll(): List<LabelEntity>

    @Query("UPDATE labels SET dirty = 0 WHERE id = :id")
    suspend fun clearDirty(id: String)

    @Query("DELETE FROM labels WHERE id = :id")
    suspend fun hardDelete(id: String)
}

@Dao
interface CrossRefDao {
    @Query("SELECT * FROM bullet_labels WHERE bulletId = :bulletId")
    suspend fun forBullet(bulletId: String): List<BulletLabelCrossRef>

    @Query("SELECT * FROM bullet_labels")
    suspend fun getAll(): List<BulletLabelCrossRef>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(refs: List<BulletLabelCrossRef>)

    @Query("DELETE FROM bullet_labels WHERE bulletId = :bulletId")
    suspend fun clearForBullet(bulletId: String)

    @Query("DELETE FROM bullet_labels WHERE labelId = :labelId")
    suspend fun clearForLabel(labelId: String)

    /** Vervang de volledige labelset van één bullet (idempotent). */
    @Transaction
    suspend fun setForBullet(bulletId: String, labelIds: List<String>) {
        clearForBullet(bulletId)
        if (labelIds.isNotEmpty()) {
            insertAll(labelIds.map { BulletLabelCrossRef(bulletId, it) })
        }
    }
}
