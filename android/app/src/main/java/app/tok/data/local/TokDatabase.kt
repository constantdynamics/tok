package app.tok.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [BulletEntity::class, LabelEntity::class, BulletLabelCrossRef::class],
    version = 1,
    exportSchema = false,
)
abstract class TokDatabase : RoomDatabase() {
    abstract fun bulletDao(): BulletDao
    abstract fun labelDao(): LabelDao
    abstract fun crossRefDao(): CrossRefDao

    companion object {
        @Volatile private var instance: TokDatabase? = null

        fun get(context: Context): TokDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                TokDatabase::class.java,
                "tok.db",
            ).fallbackToDestructiveMigration().build().also { instance = it }
        }
    }
}
