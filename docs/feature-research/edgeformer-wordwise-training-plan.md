# EdgeFormer WordWise Training Plan (Deferred)

**Status:** Deferred until Harper feasibility results are measured and suitable training hardware is available.  
**Purpose:** Preserve a reproducible path for training a small, WordWise-specific neural grammar editor without pretending that the current laptop is a training machine.  
**Last source review:** 2026-09-25

## Decision Summary

WordWise should not attempt full EdgeFormer training on the current 10-year-old laptop. That machine is still useful for:

- corpus cleaning and deduplication;
- tokenizer and Unicode-span tests;
- CPU inference and latency sampling;
- small export/smoke tests;
- reviewing generated edits.

Actual fine-tuning should wait for either a future workstation or a rented/cloud GPU. The first training run should be a small, reproducible pilot, not an open-ended attempt to train a model from scratch.

## Why EdgeFormer Is Worth Preserving

Microsoft's official EdgeFormer repository publishes an approximately 11M-parameter Adapter-LA pretrained checkpoint. Its downstream table reports a 9.4M-parameter pretrained EdgeFormer variant with F0.5 52.7 on CoNLL-14 after task fine-tuning. The difference between the approximately 11M checkpoint description and the 9.4M downstream row is a reason to record the exact checkpoint/configuration rather than treat the numbers as interchangeable.

Microsoft's Editor grammar-checker article describes EdgeLM as the foundation of a client grammar model and discusses aggressive decoding plus ONNX Runtime optimizations. The public EdgeFormer checkpoint is a pretrained foundation, not a ready-to-ship WordWise grammar model.

Sources:

- [Official EdgeFormer repository](https://github.com/microsoft/unilm/tree/master/edgelm)
- [Official EdgeFormer README](https://github.com/microsoft/unilm/blob/master/edgelm/README.md)
- [Pretrained checkpoint URL](https://msranlp.blob.core.windows.net/edgeformer/v1/edgeformer_lora32_pretrain_checkpoint_250k.pt)
- [EdgeFormer publication](https://www.microsoft.com/en-us/research/publication/edgeformer-a-parameter-efficient-transformer-for-on-device-seq2seq-generation/)
- [Microsoft Editor neural grammar checker article](https://www.microsoft.com/en-us/research/blog/achieving-zero-cogs-with-microsoft-editor-neural-grammar-checker/)
- [EdgeFormer paper](https://aclanthology.org/2022.emnlp-main.741/)

## Trigger Conditions

Do not start a training run until all of the following are true:

1. The Harper Android pilot has measured coverage, false positives, and real-device latency.
2. Harper's remaining misses are material enough that a local neural rewrite tier would improve the product.
3. A versioned correction-pair dataset exists with explicit licensing and deduplication rules.
4. A training machine or rental GPU is available with enough memory for a measured smoke run.
5. The team has agreed on an evaluation set that is not used for tuning.

If Harper handles the target corpus with acceptable precision and the cloud fallback rate is acceptable, keep EdgeFormer as a future option and do not train it yet.

## Hardware Strategy

### Current laptop

Use the current laptop for CPU-only preparation and inference, not full training:

- download and checksum the official checkpoint;
- inspect tokenizer/vocabulary files;
- normalize and deduplicate correction pairs;
- run a one-batch forward pass if memory allows;
- benchmark candidate exports on CPU;
- validate Unicode offsets and edit application.

If a training attempt is needed to estimate memory, cap it to a tiny subset and a short run. Treat that as a measurement, not a training milestone.

### First rented GPU pilot

The EdgeFormer paper does not publish a WordWise-specific VRAM requirement. Before committing to a long run:

1. measure peak memory for the smallest useful batch and sequence length;
2. increase batch/sequence gradually;
3. record whether the run uses FP32, FP16, or another precision mode;
4. stop if memory becomes unstable or the run thrashes;
5. use a 16 GB-class GPU only as an initial rental hypothesis, not a guarantee;
6. prefer a larger GPU only if the measured workload needs it.

A 16–24 GB GPU class is a reasonable planning range for an 11M model, but the actual requirement depends on the Fairseq configuration, vocabulary, optimizer, sequence length, and batch size. Do not encode that estimate as a guarantee in CI or product documentation.

### Future workstation

A future workstation should be selected after the rental pilot has produced:

- peak GPU memory;
- tokens/sec;
- wall time per 1,000 examples;
- checkpoint size;
- export time;
- energy/thermal behavior;
- expected annual training volume.

This avoids buying hardware based only on parameter count.

## Dataset Plan

### Sources

Start with a small, legally reviewable mixture of:

- WordWise-owned correction pairs;
- correctly written sentences as explicit no-change examples;
- public GEC corpora only after checking each corpus's license and redistribution terms;
- domain-specific examples from names, technical terminology, abbreviations, URLs, and app UI text.

The initial WordWise-owned set should be curated rather than copied wholesale from a cloud service. Preserve the original input, corrected target, source, dialect, and review status.

### Required splits

- **Training:** correction pairs and no-change examples.
- **Validation:** held-out authors/sources, not random near-duplicates.
- **Test:** frozen, human-reviewed, and never used for prompt or threshold tuning.
- **Stress set:** emoji, combining marks, curly punctuation, multilingual text that should remain untouched, technical identifiers, and long fields.

Deduplicate by normalized text plus edit signature. Split by source/user/session where possible so near-identical sentences do not leak across splits.

### Label policy

For every pair, record:

```json
{
  "input": "I has completed the project.",
  "target": "I have completed the project.",
  "source": "wordwise",
  "dialect": "en-US",
  "review_status": "approved",
  "notes": "subject-verb agreement"
}
```

Do not train on unreviewed model rewrites. If multiple corrections are acceptable, retain a primary target and record alternates rather than silently mixing them.

## Training Procedure

### Phase A: Reproduce the baseline

1. Pin the `microsoft/unilm` commit used for the experiment.
2. Download the official EdgeFormer Adapter-LA checkpoint and vocabulary files.
3. Record SHA-256 checksums for every artifact.
4. Create a pinned Python/Fairseq environment separately from the Android project.
5. Run the upstream example on a tiny sample before adding WordWise data.
6. Save the baseline output and exact command.

Suggested experiment manifest fields:

```yaml
source_repo: microsoft/unilm
source_commit: "record the exact commit when training starts"
checkpoint_url: https://msranlp.blob.core.windows.net/edgeformer/v1/edgeformer_lora32_pretrain_checkpoint_250k.pt
checkpoint_sha256: "record after download"
tokenizer_sha256: "record after download"
dataset_manifest_sha256: "record after dataset freeze"
seed: "choose and record before the first run"
precision: "record the measured training mode"
max_source_tokens: "record the measured sequence limit"
max_target_tokens: "record the measured sequence limit"
```

Do not use a mutable `master` URL or a generic `latest` model name in the training manifest.

### Phase B: Small fine-tune

Fine-tune only the English grammar task first:

1. use the frozen test set;
2. start with a small subset and short maximum sequence length;
3. use greedy decoding for the first comparison;
4. log loss, exact match, edit precision/recall, and no-change suppression;
5. inspect regressions by error category;
6. stop early if the model changes correct text or names frequently.

The model should learn to preserve valid text. A high BLEU-like score is not sufficient if it rewrites names, formatting, or intentional informal language.

### Phase C: Domain adaptation

Only after the baseline is stable:

- add WordWise correction pairs;
- add technical vocabulary and user-name handling;
- add no-change examples from real field categories;
- compare American/British and user-dialect behavior;
- calibrate conservative suppression rather than maximizing corrections.

### Phase D: Decoding and editing adapter

EdgeFormer is a seq2seq generator, so the Android adapter should:

1. generate a candidate corrected sentence;
2. reject empty, truncated, or overlong output;
3. compute a deterministic Unicode-safe diff;
4. convert the diff to WordWise's edit representation;
5. reject changes that touch protected names, URLs, code, or formatting;
6. route uncertain rewrites to cloud fallback.

Do not ask the model to emit raw character offsets. Generate text, validate it, then derive offsets locally.

## Export Plan

1. Export the fine-tuned PyTorch/Fairseq checkpoint to ONNX or a LiteRT-compatible graph.
2. Verify tokenizer parity against the training vocabulary.
3. Compare FP32 and quantized outputs on the frozen test set.
4. Export an INT8/FP16 candidate only after output parity is measured.
5. Package the model as an optional downloadable asset if bundling would exceed the APK budget.
6. Record model, tokenizer, graph, and runtime versions separately.

Potential runtimes:

- ONNX Runtime Mobile for an ONNX graph;
- LiteRT/LiteRT-LM where the converted model is supported;
- a custom runtime only if the standard runtime cannot represent the graph.

The model is not ready to ship merely because a desktop PyTorch checkpoint can generate text.

## Evaluation Gate

Do not promote the model to WordWise's default local tier unless it beats the simpler providers on the product corpus.

Required metrics:

- exact correction acceptance;
- edit precision and recall;
- false-positive rate on correct controls;
- no-change suppression rate;
- performance by error category;
- names, technical terms, URLs, and app-specific text;
- UTF-16 span correctness around emoji and combining marks;
- startup time, warm p50/p95 latency, peak memory, and battery impact;
- APK/AAB size and optional-download size;
- offline behavior with network disabled.

Use human review for a stratified sample of every model version. Keep the cloud path available until the local quality gate is passed.

## Failure Modes and Responses

### Model changes correct text

Increase no-change examples, add a conservative rewrite gate, and compare against Harper. Do not solve this by raising the decode temperature.

### Model is accurate but too large

Distill or quantize, then re-run the full corpus. Do not silently switch to a general model with unknown grammar quality.

### Model hallucinates names or technical terms

Protect spans before rewriting, add those terms to the stress set, and route protected-span changes to cloud fallback.

### Training does not fit the available GPU

Reduce the pilot scope, use gradient accumulation/checkpointing only after measuring, or rent a larger GPU. Do not modify the model architecture casually during the first run.

### Export changes outputs

Return to FP32 parity testing, inspect tokenizer/decoder configuration, and reject the quantized artifact until parity is understood.

## Licensing and Supply Chain

- Keep the EdgeFormer repository commit and license text with the experiment.
- Record the checkpoint URL and checksum separately from the source repository.
- Review the license of every training corpus and derived checkpoint.
- Do not upload WordWise user text to a training service without explicit product and privacy approval.
- Keep model training artifacts outside the APK until licensing and privacy review is complete.

## Deferred Milestones

- [ ] Harper Android pilot completed.
- [ ] Harper false-positive and Unicode results reviewed.
- [ ] WordWise correction-pair dataset schema frozen.
- [ ] Dataset licenses and consent/privacy status recorded.
- [ ] Baseline EdgeFormer checkpoint downloaded and checksummed.
- [ ] Tiny subset training run completed on rented hardware.
- [ ] Peak memory and throughput measured.
- [ ] FP32 export parity verified.
- [ ] Quantized candidate evaluated.
- [ ] Production decision recorded.

## Research Status

- EdgeFormer source and official claims reviewed: **complete**.
- WordWise-specific dataset prepared: **not started**.
- Training performed: **not started**.
- Current laptop training attempt: **intentionally deferred**.
- Export/mobile pilot: **not started**.
