# Rovex v8.1.7 — Dashboard/SRS controlled UX repair

- Added red→yellow animated flow to requested dashboard labels only.
- Increased bounded touch movement without layout reflow.
- Added accuracy hero count-up + scale animation; zero animates from zero.
- Restored visible/editable SRS daily target fields and prevented dialog auto-scroll/focus regression.
- Warmed AMOLED panel contrast while retaining black background.
- Preserved SQLite, SharedPreferences, typography and XML/runtime safety fixes.

Stability gate: compile in CI before installation.
