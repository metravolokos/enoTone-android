package app.enotone.database

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory

/**
 * Test-only [SupportSQLiteOpenHelper.Factory] that works around an `androidx.sqlite` defect which
 * breaks [androidx.room.testing.MigrationTestHelper] on Windows.
 *
 * `SupportSQLiteDriver.open()` (androidx.sqlite 2.6.2; the same code is present in 2.7.1 and
 * 2.8.0-alpha03) accepts a requested file name only if it equals the configured database name or
 * if both share the same final segment after the last `/`:
 *
 * ```kotlin
 * require(
 *     openHelperDatabaseName == fileName ||
 *         openHelperDatabaseName.substringAfterLast('/') ==
 *             fileName.substringAfterLast('/')
 * ) { "This driver is configured to open a database named ..." }
 * ```
 *
 * `room-testing` (`SupportTestConnectionManager.openConnection()`) requests
 * `configuration.context.getDatabasePath(name).absolutePath`, while the helper is configured with
 * the short database name. On Linux the absolute path contains `/`, so the final-segment
 * comparison succeeds. On Windows the path uses `\`, so `substringAfterLast('/')` returns the whole
 * path and both checks fail with `IllegalArgumentException`.
 *
 * The decorator reports exactly the same string the driver will be asked to open
 * (`absolutePath` of the same `context` and `name`), so the equality branch of the check holds on
 * every platform. Opening is still delegated to [FrameworkSQLiteOpenHelperFactory] with the original
 * short name, so the database file, its schema and the migrations under test are untouched.
 */
internal class PathAwareSQLiteOpenHelperFactory(
    private val delegate: SupportSQLiteOpenHelper.Factory = FrameworkSQLiteOpenHelperFactory(),
) : SupportSQLiteOpenHelper.Factory {

    override fun create(
        configuration: SupportSQLiteOpenHelper.Configuration,
    ): SupportSQLiteOpenHelper {
        val openHelper = delegate.create(configuration)
        val name = configuration.name ?: return openHelper
        // Must stay byte-identical to what room-testing passes to SupportSQLiteDriver.open().
        val requestedPath = configuration.context.getDatabasePath(name).absolutePath
        return PathAwareOpenHelper(openHelper, requestedPath)
    }

    private class PathAwareOpenHelper(
        private val delegate: SupportSQLiteOpenHelper,
        override val databaseName: String,
    ) : SupportSQLiteOpenHelper {

        override fun setWriteAheadLoggingEnabled(enabled: Boolean) =
            delegate.setWriteAheadLoggingEnabled(enabled)

        override val writableDatabase: SupportSQLiteDatabase
            get() = delegate.writableDatabase

        override val readableDatabase: SupportSQLiteDatabase
            get() = delegate.readableDatabase

        override fun close() = delegate.close()
    }
}
