# Rovex v8.3.76 — Ben Cognitive Expansion II

Version code: 174
Package: com.localqbank.library

## Ben improvements
- Added bounded episodic experience memory for recent routing/verification outcomes.
- Planner now routes association, epidemiology and safety intents explicitly.
- Added misconception-check routing from repeated local wrong-answer signals.
- Added exam-trap detection routing for negative/EXCEPT/incorrect stems.
- Added evidence-caution routing for dose, cutoff, criteria and guideline-sensitive questions.
- Added learner-aware confidence adjustment using local mastery signals.
- Expanded Ben tool registry without introducing a new business-logic owner.

## Safety
- No neural model dependency added.
- Experience memory is compact, bounded and local.
- No QBank source mutation.
- No startup model loading.
- Existing Ben resource governor and emergency STOP remain authoritative.
