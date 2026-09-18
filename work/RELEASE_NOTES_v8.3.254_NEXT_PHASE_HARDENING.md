# Rovex v8.3.254 — Next-Phase AI/UI Hardening

- Explicit SAVE CONTEXT semantics: selecting a context tab no longer mutates persistent preference state.
- Persisted AI context metadata now records mode, source question id and save timestamp.
- PYQ/PYT/FUTURE RELATED/OTHER OPTIONS remain local-evidence-first and exam-profile-aware.
- Frankenstein and question AI share deterministic Markdown presentation for bold, emphasis, strike, code, lists, quotes, headings and basic links.
- Google Search remains embedded in Rovex, while non-Google result navigation is safely handed to the external browser.
- WebView file/content access and mixed-content loading disabled; JS retained only because Google Search requires it.
- Search chase and Titanic animations are lifecycle-aware and density-correct to avoid detached-view invalidation loops.
- Performance Lab upgraded to animated graded analytical bars with OPTIMAL/STABLE/WATCH/GUARDED states.
- Flashcard Home card upgraded to an animated SRS dashboard with due/review ratios.
- Quiz option HTML sanitization now strips imported foreground-color styling before theme application, strengthening AMOLED readability.
- No parallel business-logic owner introduced. Existing AppManagers/learning/AI ownership remains authoritative.
