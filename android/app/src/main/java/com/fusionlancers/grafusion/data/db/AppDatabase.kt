package com.fusionlancers.grafusion.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        AccountEntity::class,
        DashboardEntity::class,
        NotificationHistoryEntity::class,
        LocalScheduleEntity::class,
        LocalShiftEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun dashboardDao(): DashboardDao
    abstract fun notificationHistoryDao(): NotificationHistoryDao
    abstract fun localOnCallDao(): LocalOnCallDao
}
