package app.tok.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * Lokale spiegeling van `tok_bullets`. Tijden zijn epoch-millis (UTC).
 * `dirty` = lokaal gewijzigd, nog niet gepusht. `deleted` = tombstone:
 * lokaal verborgen, moet nog als delete naar de server, daarna opgeruimd.
 */
@Entity(tableName = "bullets")
data class BulletEntity(
    @PrimaryKey val id: String,
    val text: String,
    val createdAt: Long,
    val updatedAt: Long,
    val sortOrder: Double,
    val isArchived: Boolean,
    val dirty: Boolean = false,
    val deleted: Boolean = false,
)

@Entity(tableName = "labels")
data class LabelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: String,
    val createdAt: Long,
    val dirty: Boolean = false,
    val deleted: Boolean = false,
)

/** Many-to-many koppeling bullet <-> label (lokaal afgeleid van de bullet-staat). */
@Entity(
    tableName = "bullet_labels",
    primaryKeys = ["bulletId", "labelId"],
    indices = [Index("labelId")],
)
data class BulletLabelCrossRef(
    val bulletId: String,
    val labelId: String,
)

/** Lees-model voor de UI: een bullet met zijn gekoppelde labels. */
data class BulletWithLabels(
    @Embedded val bullet: BulletEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = BulletLabelCrossRef::class,
            parentColumn = "bulletId",
            entityColumn = "labelId",
        ),
    )
    val labels: List<LabelEntity>,
)
