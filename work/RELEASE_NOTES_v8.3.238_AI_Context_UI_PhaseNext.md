# Rovex v8.3.239 — AI Context / UI Phase Next

- Fixed `RenActivity` Free AI menu compilation failure caused by an out-of-scope `rounded()` helper.
- Unified the question context packet so AI receives the exam profile, full question, all options, student-selected option, keyed answer and QBank explanation.
- Added the same Ben + AI context action to the answer/explanation panel.
- Restyled the question counter/settings/timer rail to use the same textured theme atmosphere as the question header instead of the option-panel fill.
- Added warm-only EmbeddingGemma semantic reranking to bounded Frankenstein local evidence; cold native model initialization is never triggered just to open a context panel.
- Preserved deterministic QBank truth as authoritative and kept cloud AI optional/fallback-only.
