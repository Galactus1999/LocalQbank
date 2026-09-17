# Rovex v8.1.2 — UX + Media Stability Hotfix

## Requested changes
- Supplied REVEX artwork is used as the launcher icon.
- Question-media extraction now recognizes `src`, `data-src`, `data-original`, `data-lazy-src`, and `srcset`.
- Question image decoding accepts HTTP/HTTPS media as well as data/content/file sources and keeps memory-bounded decoding.
- Today’s Revision uses scheduled-due questions first, then falls back to wrong/bookmarked questions so the dashboard action remains useful.
- Flashcard Mark/Bookmark retain the original compact button dimensions; active state changes the whole button colour rather than only the inner glyph.
- SRS settings restored/expanded: daily new cards, daily review target, maximum daily cards, learning delay, initial interval, easy/hard multipliers, leech threshold.
- Right swipe threshold for previous flashcard reduced from 115dp to 72dp with direction-dominance protection.
- Import progress/detail text uses high-contrast theme text in dark mode.
- Question header given independent vertical breathing room and a separate progress rail to prevent title/QBank-name clipping.

## Deliberately unchanged
- **No Brushability font change** was made to the home-screen app name, per the latest instruction.
- No unrelated UI/features were changed.

## Stability
- v8.1.1 SQLite, SharedPreferences, and adaptive-typography hardening retained.
