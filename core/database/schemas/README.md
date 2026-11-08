# Room schema history

Room exports versioned JSON under `app.enotone.database.EnoToneDatabase/` at build time using KSP. Generated JSON for schema v1 must be reviewed and committed before release; **never** add destructive-fallback migrations.

The current PR checks annotated Room compilation, but does not claim an on-device migration has been tested. The DB is not yet opened from the Android UI.
