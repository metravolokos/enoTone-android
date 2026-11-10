package app.enotone.database

import java.io.File
import org.junit.Assert.fail
import org.junit.Test

/**
 * Temporary CI-only audit: emit the exact Room KSP schema into a test failure.
 * No personal data is present in Room's schema, and no guesswork on identityHash.
 * This test MUST be deleted before the PR can be merged.
 */
class RoomV3SchemaExportTest {
    @Test fun captureGeneratedSchemaForCheckedInMigrationSnapshot() {
        val file = File("schemas/app.enotone.database.EnoToneDatabase/3.json")
        if (!file.isFile) fail("ENOTONE_V3_SCHEMA_NOT_EXPORTED")
        // Deliberately fail once so standard Gradle CI includes the schema
        // verbatim without modifying the signed-APK workflow.
        fail("ENOTONE_SCHEMA_V3_JSON=" + file.readText()
            .replace("\n", "").replace("\r", ""))
    }
}
