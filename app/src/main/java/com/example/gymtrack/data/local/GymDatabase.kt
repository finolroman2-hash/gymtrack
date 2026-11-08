package com.example.gymtrack.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.gymtrack.data.local.dao.GymDao
import com.example.gymtrack.data.local.dao.OffCatalogDao
import com.example.gymtrack.data.local.entities.ExerciseEntity
import com.example.gymtrack.data.local.entities.ForecastPointEntity
import com.example.gymtrack.data.local.entities.TrainingCycleEntity
import com.example.gymtrack.data.local.entities.TrainingCycleGroupEntity
import com.example.gymtrack.data.local.entities.TrainingMacrocycleEntity
import com.example.gymtrack.data.local.entities.FoodLogEntity
import com.example.gymtrack.data.local.entities.NutritionGoalsEntity
import com.example.gymtrack.data.local.entities.BodyMetricEntity
import com.example.gymtrack.data.local.entities.OffCatalogEntity
import com.example.gymtrack.data.local.entities.ProfileEntity
import com.example.gymtrack.data.local.entities.SessionEntity
import com.example.gymtrack.data.local.entities.SetEntity

@Database(
    entities = [
        ExerciseEntity::class,
        SessionEntity::class,
        SetEntity::class,
        ProfileEntity::class,
        ForecastPointEntity::class,
        TrainingMacrocycleEntity::class,
        TrainingCycleEntity::class,
        TrainingCycleGroupEntity::class,
        FoodLogEntity::class,
        NutritionGoalsEntity::class,
        BodyMetricEntity::class,
        OffCatalogEntity::class,
    ],
    version = 14,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class GymDatabase : RoomDatabase() {
    abstract fun gymDao(): GymDao
    abstract fun offCatalogDao(): OffCatalogDao

    companion object {
        @Volatile private var INSTANCE: GymDatabase? = null

        fun getInstance(context: Context): GymDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    GymDatabase::class.java,
                    "gymtrack.db"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14)
                .build()
                .also { INSTANCE = it }
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exercises ADD COLUMN userName TEXT NOT NULL DEFAULT 'default'")
                db.execSQL("ALTER TABLE sessions ADD COLUMN userName TEXT NOT NULL DEFAULT 'default'")
            }
        }

        // Crea la tabla profiles y siembra los usuarios existentes para no perder datos
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS profiles (" +
                    "name TEXT NOT NULL PRIMARY KEY, " +
                    "createdAt INTEGER NOT NULL DEFAULT 0)"
                )
                db.execSQL(
                    "INSERT OR IGNORE INTO profiles (name, createdAt) " +
                    "SELECT DISTINCT userName, 0 FROM sessions"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exercises ADD COLUMN muscleTags TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sets ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    UPDATE sets SET sortOrder = (
                      SELECT COUNT(*) FROM sets s2
                      WHERE s2.sessionId = sets.sessionId
                      AND (
                        s2.createdAt < sets.createdAt
                        OR (s2.createdAt = sets.createdAt AND s2.id < sets.id)
                      )
                    )
                    """.trimIndent(),
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS forecast_points (
                        userName TEXT NOT NULL,
                        exerciseId INTEGER NOT NULL,
                        dateMillis INTEGER NOT NULL,
                        predictedOneRm REAL NOT NULL,
                        PRIMARY KEY(userName, exerciseId, dateMillis)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_forecast_points_exerciseId ON forecast_points(exerciseId)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_forecast_points_userName_exerciseId " +
                        "ON forecast_points(userName, exerciseId)",
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS training_macrocycles (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        userName TEXT NOT NULL,
                        name TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS training_cycles (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        macrocycleId INTEGER NOT NULL,
                        userName TEXT NOT NULL,
                        sortOrder INTEGER NOT NULL,
                        months INTEGER NOT NULL,
                        weeks INTEGER NOT NULL,
                        days INTEGER NOT NULL,
                        label TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY(macrocycleId) REFERENCES training_macrocycles(id) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_training_cycles_macrocycleId ON training_cycles(macrocycleId)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_training_cycles_userName ON training_cycles(userName)",
                )
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS training_cycles_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        macrocycleId INTEGER NOT NULL,
                        userName TEXT NOT NULL,
                        sortOrder INTEGER NOT NULL,
                        weeks INTEGER NOT NULL,
                        avgRir REAL NOT NULL,
                        weeklyVolume REAL NOT NULL,
                        label TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY(macrocycleId) REFERENCES training_macrocycles(id) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO training_cycles_new (id, macrocycleId, userName, sortOrder, weeks, avgRir, weeklyVolume, label)
                    SELECT id, macrocycleId, userName, sortOrder,
                        MAX(1, MIN(48, months * 4 + weeks + ((days + 6) / 7))),
                        2.0, 0.0, label
                    FROM training_cycles
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE training_cycles")
                db.execSQL("ALTER TABLE training_cycles_new RENAME TO training_cycles")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_training_cycles_macrocycleId ON training_cycles(macrocycleId)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS training_cycle_groups (
                        cycleId INTEGER NOT NULL,
                        muscleGroupKey TEXT NOT NULL,
                        avgRir REAL NOT NULL,
                        weeklyVolume REAL NOT NULL,
                        PRIMARY KEY(cycleId, muscleGroupKey),
                        FOREIGN KEY(cycleId) REFERENCES training_cycles(id) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_training_cycle_groups_cycleId ON training_cycle_groups(cycleId)",
                )
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE training_macrocycles ADD COLUMN isActive INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE training_macrocycles ADD COLUMN activatedAt INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE training_macrocycles ADD COLUMN startCycleId INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE training_macrocycles ADD COLUMN startWeekInCycle INTEGER NOT NULL DEFAULT 1",
                )
                db.execSQL(
                    "ALTER TABLE training_macrocycles ADD COLUMN startDayInWeek INTEGER NOT NULL DEFAULT 1",
                )
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS food_logs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        userName TEXT NOT NULL,
                        dayStartMillis INTEGER NOT NULL,
                        offCode TEXT NOT NULL,
                        name TEXT NOT NULL,
                        brand TEXT NOT NULL,
                        quantity REAL NOT NULL,
                        unit TEXT NOT NULL,
                        grams REAL NOT NULL,
                        kcal REAL NOT NULL,
                        protein REAL NOT NULL,
                        carbs REAL NOT NULL,
                        fat REAL NOT NULL,
                        fiber REAL NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_food_logs_userName ON food_logs(userName)")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_food_logs_userName_dayStartMillis ON food_logs(userName, dayStartMillis)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS nutrition_goals (
                        userName TEXT NOT NULL PRIMARY KEY,
                        sex TEXT NOT NULL,
                        age INTEGER NOT NULL,
                        heightCm REAL NOT NULL,
                        weightKg REAL NOT NULL,
                        activity TEXT NOT NULL,
                        goal TEXT NOT NULL,
                        tdee INTEGER NOT NULL,
                        calories INTEGER NOT NULL,
                        proteinG REAL NOT NULL,
                        carbsG REAL NOT NULL,
                        fatG REAL NOT NULL,
                        fiberG REAL NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS body_metrics (
                        userName TEXT NOT NULL,
                        dayStartMillis INTEGER NOT NULL,
                        weightKg REAL NOT NULL,
                        bodyFatPercent REAL NOT NULL,
                        PRIMARY KEY(userName, dayStartMillis)
                    )
                    """.trimIndent(),
                )
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS off_catalog (
                        code TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        nameNormalized TEXT NOT NULL,
                        brand TEXT NOT NULL,
                        brandNormalized TEXT NOT NULL,
                        kcal100g REAL NOT NULL,
                        protein100g REAL NOT NULL,
                        carbs100g REAL NOT NULL,
                        fat100g REAL NOT NULL,
                        fiber100g REAL NOT NULL,
                        kcal100ml REAL,
                        servingGrams REAL,
                        servingLabel TEXT,
                        quantityHint TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_off_catalog_nameNormalized ON off_catalog(nameNormalized)",
                )
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE off_catalog ADD COLUMN useCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE off_catalog ADD COLUMN lastUsedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("DELETE FROM off_catalog")
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE off_catalog ADD COLUMN isCustom INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
