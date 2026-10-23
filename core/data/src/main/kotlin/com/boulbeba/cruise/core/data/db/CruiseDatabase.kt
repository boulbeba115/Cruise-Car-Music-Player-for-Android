package com.boulbeba.cruise.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Database(
    entities = [TrackEntity::class, TrackFts::class, PlayStatEntity::class, PlaylistEntity::class, PlaylistItemEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class CruiseDatabase : RoomDatabase() {
    abstract fun library(): LibraryDao
}

/** 1 → 2: accent- and case-insensitive search. Only the search index is rebuilt; stats and playlists stay. */
internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        listOf("BEFORE_UPDATE", "BEFORE_DELETE", "AFTER_UPDATE", "AFTER_INSERT")
            .forEach { db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_tracks_fts_$it") }
        db.execSQL("DROP TABLE IF EXISTS `tracks_fts`")
        db.execSQL(
            "CREATE VIRTUAL TABLE IF NOT EXISTS `tracks_fts` USING FTS4(`title` TEXT NOT NULL, `artist` TEXT NOT NULL, " +
                "`album` TEXT NOT NULL, `albumArtist` TEXT NOT NULL, `genre` TEXT NOT NULL, `composer` TEXT NOT NULL, " +
                "`fileName` TEXT NOT NULL, `folder` TEXT NOT NULL, tokenize=unicode61 `remove_diacritics=1`, content=`tracks`)",
        )
        val columns = "`title`, `artist`, `album`, `albumArtist`, `genre`, `composer`, `fileName`, `folder`"
        val values = "NEW.`rowid`, NEW.`title`, NEW.`artist`, NEW.`album`, NEW.`albumArtist`, NEW.`genre`, NEW.`composer`, NEW.`fileName`, NEW.`folder`"
        for (timing in listOf("UPDATE", "DELETE")) {
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_tracks_fts_BEFORE_$timing BEFORE $timing ON `tracks` " +
                    "BEGIN DELETE FROM `tracks_fts` WHERE `docid`=OLD.`rowid`; END",
            )
        }
        for (timing in listOf("UPDATE", "INSERT")) {
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_tracks_fts_AFTER_$timing AFTER $timing ON `tracks` " +
                    "BEGIN INSERT INTO `tracks_fts`(`docid`, $columns) VALUES ($values); END",
            )
        }
        db.execSQL("INSERT INTO `tracks_fts`(`tracks_fts`) VALUES('rebuild')")
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    /**
     * WAL keeps reads fast during a scan and survives power cuts (SPEC CAR-4). Schema changes ship with migrations so
     * play counts, favourites and playlists survive updates; the destructive fallback only covers a downgrade.
     */
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): CruiseDatabase =
        Room.databaseBuilder(context, CruiseDatabase::class.java, "cruise.db")
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .addMigrations(MIGRATION_1_2)
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
            .build()

    @Provides
    fun libraryDao(db: CruiseDatabase): LibraryDao = db.library()
}
