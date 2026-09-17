# Ren Clinical Cognition Architecture

## Pipeline

`User language -> Query normalization -> Clinical concept recognition -> Intent/domain inference -> Bounded concept expansion -> Local QBank retrieval -> Clinical reranking -> Evidence-grounded response`

## Layers

1. **ClinicalKnowledgeLayer**
   - Owns clinical terminology, aliases, abbreviations/eponyms, domains, relationships and exam-intent vocabulary.
   - Data is externalized to `app/src/main/assets/ren/clinical_lexicon.tsv` so terminology can grow without turning the cognitive engine into a large chain of `if` statements.

2. **RenCognitiveEngine**
   - Owns conversation routing and summarisation.
   - Delegates medical language understanding to `ClinicalKnowledgeLayer`.
   - Never writes executable code or changes source QBank content at runtime.

3. **QBankDb**
   - Remains the local retrieval/index layer and source of imported question content.

4. **Clinical reranker**
   - Gives substantially more weight to the actual question stem/content than QBank/source labels.
   - Uses recognized clinical concepts and exam intent.
   - Applies a bounded penalty to clearly unrelated specialty metadata without hard-excluding cross-disciplinary questions.

## Coverage strategy

The initial lexicon covers foundational and clinical subjects plus cross-specialty clinical concepts. It includes approximately 240 structured entries and more than 550 aliases/terms, including common abbreviations, eponyms, British/American spellings and exam-intent phrases.

The architecture is intentionally extensible. Future terminology growth should be data additions or versioned terminology modules, not a return to hard-coded synonym branches.

## Grounding rule

Ren's terminology knowledge is not treated as evidence that a QBank question exists. A concept can be recognized while the local QBank still returns no strong match. In that case Ren must say so rather than inventing a question or answer.
