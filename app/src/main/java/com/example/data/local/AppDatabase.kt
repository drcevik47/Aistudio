package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.ExchangeTradeDao
import com.example.data.local.dao.LogDao
import com.example.data.local.dao.OrderDao
import com.example.data.local.entity.ExchangeTradeEntity
import com.example.data.local.entity.LogEntity
import com.example.data.local.entity.OrderEntity

@Database(
    entities = [OrderEntity::class, LogEntity::class, ExchangeTradeEntity::class],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun orderDao(): OrderDao
    abstract fun logDao(): LogDao
    abstract fun exchangeTradeDao(): ExchangeTradeDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Ensure exchange column exists in orders and system_logs
                try {
                    db.execSQL("ALTER TABLE orders ADD COLUMN exchange TEXT NOT NULL DEFAULT 'BYBIT'")
                } catch (e: Exception) {
                    // Column may already exist in some builds
                }
                try {
                    db.execSQL("ALTER TABLE system_logs ADD COLUMN exchange TEXT NOT NULL DEFAULT 'BYBIT'")
                } catch (e: Exception) {
                    // Column may already exist
                }
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create exchange_trades table if not exists
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `exchange_trades` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `exchange` TEXT NOT NULL DEFAULT 'BYBIT',
                        `execId` TEXT NOT NULL,
                        `orderId` TEXT NOT NULL,
                        `orderLinkId` TEXT NOT NULL DEFAULT '',
                        `symbol` TEXT NOT NULL,
                        `side` TEXT NOT NULL,
                        `orderPrice` REAL NOT NULL,
                        `orderQty` REAL NOT NULL,
                        `orderType` TEXT NOT NULL DEFAULT '',
                        `execPrice` REAL NOT NULL,
                        `execQty` REAL NOT NULL,
                        `execValue` REAL NOT NULL,
                        `execFee` REAL NOT NULL DEFAULT 0.0,
                        `feeRate` REAL NOT NULL DEFAULT 0.0,
                        `feeCurrency` TEXT NOT NULL DEFAULT '',
                        `timeMillis` INTEGER NOT NULL,
                        `isMaker` INTEGER NOT NULL DEFAULT 0,
                        `syncedAt` INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_exchange_trades_execId` ON `exchange_trades` (`execId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_exchange_trades_orderId` ON `exchange_trades` (`orderId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_exchange_trades_symbol` ON `exchange_trades` (`symbol`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_exchange_trades_timeMillis` ON `exchange_trades` (`timeMillis`)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Ensure all indexes and columns for exchange_trades table
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_exchange_trades_execId` ON `exchange_trades` (`execId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_exchange_trades_orderId` ON `exchange_trades` (`orderId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_exchange_trades_symbol` ON `exchange_trades` (`symbol`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_exchange_trades_timeMillis` ON `exchange_trades` (`timeMillis`)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Remove duplicates if any exist before adding unique index
                try {
                    db.execSQL(
                        """
                        DELETE FROM orders WHERE id NOT IN (
                            SELECT MIN(id) FROM orders GROUP BY exchange, orderId
                        )
                        """.trimIndent()
                    )
                } catch (e: Exception) {
                    // Ignore if empty or table variance
                }
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_orders_exchange_orderId` ON `orders` (`exchange`, `orderId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_orders_symbol` ON `orders` (`symbol`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_orders_status` ON `orders` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_orders_timestamp` ON `orders` (`timestamp`)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bybit_bot_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
