# Rovex v8.1.8 — Stability Audit

Base: v8.1.7 Dashboard/SRS repair.

## CI failure reviewed
The supplied CI log for v8.1.7 reports:
`QuizActivity.kt:274:21 Unresolved reference 'selectAllOnFocus'`.
The current source must use Android's `setSelectAllOnFocus(true)` method on EditText, not an unresolved property/reference.

## Regression checks
- SQLite foreign-key initialization uses `SQLiteDatabase.setForeignKeyConstraintsEnabled(true)`.
- Flashcard database enables WAL through `enableWriteAheadLogging()`.
- No unsafe PRAGMA execution through `execSQL()` for foreign keys/WAL.
- AdaptiveTypographyManager is a no-op; it does not globally shrink designed text.
- Native auto-size calls are absent.
- Legacy SharedPreferences numeric reads use PrefsCompat where migration is required.
- SRS settings remain string-backed in SQLite and are editable through EditText fields.
- Dashboard color-flow labels use RovexColorFlowTextView.
- AliveMotion remains bounded and does not change layout bounds.
- XML ID references were scanned; no missing IDs were found.
- ZIP integrity checked before release packaging.

## Build gate
Final Android/Gradle compilation must be run by CI before device installation. This environment does not have the Gradle distribution cached and cannot reach services.gradle.org, so no local compilation claim is made.
