# Gemini food benchmark dataset

This folder is test-only and is never packaged into the Android application or backend deployment.

The ten source photographs are downloaded from Wikimedia Commons by `scripts/download-gemini-benchmark-dataset.ps1`. `manifest.json` records the Commons page, author, license, original dimensions, original bytes, and SHA-256 for every image. The binary `originals/` and `prepared/` folders are intentionally ignored by Git because they are reproducible and large; they remain in this local test folder after the audit.

## Cases

| Case | Required category | Wikimedia Commons file |
|---|---|---|
| simple_apple | Simple food | Liat Portal for Foodie Disorder - Red Apple (Whole Fruit).jpg |
| mexican_tacos | Mexican food | Tacos al pastor - taquería Los Güeros de Boturini.jpg |
| mixed_salad | Several ingredients | Liat Portal for Foodie Disorder - Mixed vegetable salad with fresh herbs.jpg |
| orange_juice | Beverage | A glass of orange juice (2014-12-23).JPG |
| large_pizza | Large portion | Margherita pizza on plate.jpg |
| ambiguous_stew | Ambiguous dish | -2022-01-06 Bowl of Irish Stew, Trimingham, Norfolk.JPG |
| empty_plate | Empty plate | The way we like our plates at the end of dinner- Dinner on New Year's Day 2024 - The Eighth Day of Christmas - 53435741068.jpg |
| blurry_asparagus | Blurry food | Blurry Asparagus (3714655569).jpg |
| non_food_mug | Non-food object | Mug, shaving (AM 1965.122-1).jpg |
| fruit_bowl | Additional varied meal | Fruit Salad Or Fruit Bowl.jpg |

## Reproduction

```powershell
.\scripts\download-gemini-benchmark-dataset.ps1
& 'C:\Users\yahir\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' .\scripts\prepare-gemini-benchmark-images.py
```

Preparation uses the same source hashes for all profiles:

- conservative: maximum side 1600 px, JPEG quality 85;
- balanced: maximum side 1280 px, JPEG quality 75;
- aggressive: maximum side 960 px, JPEG quality 65;
- realistic: maximum side 2400 px, JPEG quality 90, used only for the realistic-size benchmark group.

All prepared files are re-encoded once, stripped of EXIF metadata, and assigned their own SHA-256 in `manifest.json`.

`benchmark-cases.json` contains the ten text cases and the explicit quality rubric. The rubric is intentionally broad: it checks identification keywords and calorie plausibility, and must not be interpreted as clinical or nutritional ground truth.

The benchmark runner is sequential and never writes the API key, request body, Base64 image, prompt, or raw provider error to disk. It requires PowerShell 7 or newer so non-success HTTP responses can be captured without emitting raw response bodies:

```powershell
.\scripts\run-gemini-benchmark.ps1 -Phase Validate
.\scripts\run-gemini-benchmark.ps1 -Phase Pilots
.\scripts\run-gemini-benchmark.ps1 -Phase Full
.\scripts\run-gemini-benchmark.ps1 -Phase Models
```
