# Rovex v8.1.0 — Ren Clinical Cognition Architecture

## Purpose
Strengthen Ren from a small synonym matcher into a versioned, offline-first clinical language and retrieval layer spanning first-year foundational subjects through final-year clinical specialties.

## Architecture
- Added `ClinicalKnowledgeLayer` as a dedicated clinical-language boundary.
- Added local `assets/ren/clinical_lexicon.tsv` with structured concepts, aliases, medical domains and related concepts.
- Ren now performs concept normalization, abbreviation/eponym recognition, exam-intent detection and bounded concept expansion before QBank retrieval.
- Retrieval remains QBank-grounded and local; no network model or runtime source-code/QBank mutation is introduced.
- Clinical content is ranked above provenance metadata; unrelated subject labels are penalized rather than allowed to dominate results.
- The terminology data is externalized from executable Kotlin so it can be expanded/versioned without rewriting the cognitive engine.

## Coverage
The initial lexicon spans anatomy, physiology, biochemistry, pathology, pharmacology, microbiology, community medicine, medicine, surgery, pediatrics, obstetrics/gynecology, radiology, anesthesia, psychiatry, dermatology, ENT, ophthalmology, orthopedics, neurology, cardiology, nephrology, endocrinology, gastroenterology, hematology, oncology, rheumatology, urology, nuclear medicine and emergency medicine.

## Safety/grounding
- Ren does not invent QBank facts.
- Ren does not rewrite executable source code or source QBank content at runtime.
- Ambiguous abbreviations are resolved through context and content ranking rather than blind substitution.
- A terminology match without a strong local QBank content match is reported as such.

## Verification limitation
Static/source audits are performed before release. Actual Gradle/APK compilation remains a GitHub Actions release gate because the current local environment cannot download the required Gradle distribution.
