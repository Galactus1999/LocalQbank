# Rovex v8.3.253 — AI Context + Home Visual/Theme Hardening

- Reworked the question AI context surface into explicit **PYQ CONTEXT**, **PYT CONTEXT**, **FUTURE RELATED**, and **OTHER OPTIONS** tasks with bounded embedded prompts.
- Added persistent save/restore of the selected question-AI context mode through the existing Ren memory store.
- Added in-app **RUN FREE AI** for the selected context and kept Google Search as a separate evidence path.
- Replaced Google Search `ACTION_VIEW` launches with an in-app `GoogleSearchActivity` WebView so user-initiated searches remain inside Rovex.
- Added deterministic Markdown presentation for Ben/AI output, including `**bold**`, `__bold__`, `*italic*`, inline code and simple bullets.
- Applied foreground-color sanitization to quiz option HTML spans so dark/AMOLED option text cannot be hidden by imported HTML colors.
- Replaced the decorative header bird with a lightweight animated ocean-liner scene and added a non-interactive deer/tiger chase animation over the Home search bar.
- Redesigned Home Flashcards and Performance Lab cards with compact analytical infographic views and theme-aware graded resource colors.
- No external artwork or network dependency was added for the animations.
