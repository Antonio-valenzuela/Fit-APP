# Exercise catalog import

Requires Node.js 20 or newer. Run `node import.mjs` from this directory. Use `node import.mjs --benchmark` only for a non-canonical timing measurement emitted on stdout; capture that measurement externally because runtime duration is never persisted in canonical outputs. Tests: `node --test import.test.mjs`.

The source repository, exact commit, source SHA-256, generation date, record count, and normalization version are pinned in `config.json`. The tool downloads only `data/exercises.json` into memory and writes `out/exercises.compact.json`, deterministic gzip (`mtime=0`), provenance, and JSON/Markdown audit reports. Repeated runs with identical input/config produce identical bytes. Multimedia is outside scope and is neither downloaded nor emitted.

`spanish-aliases.json` is a separate, deterministic twelve-entry alias manifest; aliases are not added to the compact dataset. `manual-resolutions.json` keeps `0697`, `0716`, `1403`, `1496`, and `1766` in `REVIEW` with no catalog row. `BODY_WEIGHT` is always emitted with an empty `equipment_requirements` list.
