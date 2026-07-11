param(
  [string]$DatasetRoot = (Join-Path $PSScriptRoot '..\test-data\gemini-food-benchmark')
)

$ErrorActionPreference = 'Stop'
$cases = @(
  @{ id='simple_apple'; category='simple_food'; expected='one whole red apple'; title='Liat Portal for Foodie Disorder - Red Apple (Whole Fruit).jpg'; author='HaJunkiyada'; license='CC BY-SA 4.0'; licenseUrl='https://creativecommons.org/licenses/by-sa/4.0/'; sourceWidth=3024; sourceHeight=4032 },
  @{ id='mexican_tacos'; category='mexican_food'; expected='three tacos al pastor with a soft drink and lime'; title='Tacos al pastor - taquería Los Güeros de Boturini.jpg'; author='ProtoplasmaKid'; license='CC BY 4.0'; licenseUrl='https://creativecommons.org/licenses/by/4.0/'; sourceWidth=3066; sourceHeight=4088 },
  @{ id='mixed_salad'; category='multiple_ingredients'; expected='mixed vegetable salad with herbs'; title='Liat Portal for Foodie Disorder - Mixed vegetable salad with fresh herbs.jpg'; author='HaJunkiyada'; license='CC BY-SA 4.0'; licenseUrl='https://creativecommons.org/licenses/by-sa/4.0/'; sourceWidth=3024; sourceHeight=4032 },
  @{ id='orange_juice'; category='beverage'; expected='one glass of orange juice'; title='A glass of orange juice (2014-12-23).JPG'; author='Lombroso'; license='CC BY-SA 4.0'; licenseUrl='https://creativecommons.org/licenses/by-sa/4.0/'; sourceWidth=2448; sourceHeight=3264 },
  @{ id='large_pizza'; category='large_portion'; expected='two large slices of Margherita pizza'; title='Margherita pizza on plate.jpg'; author='Shisma'; license='CC BY 4.0'; licenseUrl='https://creativecommons.org/licenses/by/4.0/'; sourceWidth=4032; sourceHeight=3024 },
  @{ id='ambiguous_stew'; category='ambiguous_food'; expected='bowl of Irish stew'; title='-2022-01-06 Bowl of Irish Stew, Trimingham, Norfolk.JPG'; author='Kolforn'; license='CC BY-SA 4.0'; licenseUrl='https://creativecommons.org/licenses/by-sa/4.0/'; sourceWidth=4367; sourceHeight=2859 },
  @{ id='empty_plate'; category='empty_plate'; expected='used plate with no meaningful food portion'; title="The way we like our plates at the end of dinner- Dinner on New Year's Day 2024 - The Eighth Day of Christmas - 53435741068.jpg"; author='Haydn Blackey'; license='CC BY-SA 2.0'; licenseUrl='https://creativecommons.org/licenses/by-sa/2.0/'; sourceWidth=1436; sourceHeight=1448 },
  @{ id='blurry_asparagus'; category='blurry_food'; expected='blurred asparagus'; title='Blurry Asparagus (3714655569).jpg'; author='DGriebeling'; license='CC BY 2.0'; licenseUrl='https://creativecommons.org/licenses/by/2.0/'; sourceWidth=3888; sourceHeight=2592 },
  @{ id='non_food_mug'; category='non_food'; expected='museum shaving mug, not food'; title='Mug, shaving (AM 1965.122-1).jpg'; author='Not supplied in Commons Artist metadata'; license='CC BY 4.0'; licenseUrl='https://creativecommons.org/licenses/by/4.0/'; sourceWidth=2592; sourceHeight=1944 },
  @{ id='fruit_bowl'; category='varied_meal'; expected='fruit salad or fruit bowl'; title='Fruit Salad Or Fruit Bowl.jpg'; author='Manjeshwari poet mysore'; license='CC BY-SA 4.0'; licenseUrl='https://creativecommons.org/licenses/by-sa/4.0/'; sourceWidth=4000; sourceHeight=2411 }
)

if ($cases.Count -ne 10) { throw 'The controlled dataset must contain exactly ten cases.' }
$originals = Join-Path $DatasetRoot 'originals'
New-Item -ItemType Directory -Force -Path $originals | Out-Null
$headers = @{ 'User-Agent' = 'FitPlanBenchmark/1.0 (controlled open-license test dataset)' }

function Download-ControlledFile([string]$Uri, [string]$Destination, [string]$CaseId) {
  for ($attempt = 1; $attempt -le 3; $attempt++) {
    try {
      Invoke-WebRequest -Uri $Uri -OutFile $Destination -Headers $headers -MaximumRedirection 10
      return
    } catch {
      $status = [int]$_.Exception.Response.StatusCode
      if ($status -ne 429 -or $attempt -eq 3) {
        throw "Download failed for $CaseId with HTTP status $status."
      }
      Start-Sleep -Seconds (20 * $attempt)
    }
  }
}

$manifestCases = @()
foreach ($case in $cases) {
  $encodedTitle = [Uri]::EscapeDataString($case.title)
  $sourcePage = 'https://commons.wikimedia.org/wiki/File:' + $encodedTitle
  $downloadUrl = 'https://commons.wikimedia.org/wiki/Special:Redirect/file/' + $encodedTitle + '?width=2400'
  $destination = Join-Path $originals ($case.id + '.jpg')
  Download-ControlledFile -Uri $downloadUrl -Destination $destination -CaseId $case.id
  $file = Get-Item -LiteralPath $destination
  $hash = (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant()
  $manifestCases += [ordered]@{
    id = $case.id
    category = $case.category
    expectedVisibleContent = $case.expected
    fileName = $case.id + '.jpg'
    commonsTitle = 'File:' + $case.title
    sourcePage = $sourcePage
    downloadUrl = $downloadUrl
    author = $case.author
    license = $case.license
    licenseUrl = $case.licenseUrl
    commonsOriginal = [ordered]@{
      width = $case.sourceWidth
      height = $case.sourceHeight
    }
    original = [ordered]@{
      sha256 = $hash
      width = 0
      height = 0
      mimeType = 'image/jpeg'
      bytes = [int64]$file.Length
    }
    prepared = [ordered]@{}
  }
  Write-Output ("Downloaded {0}: {1} bytes, SHA-256 {2}" -f $case.id, $file.Length, $hash)
  Start-Sleep -Seconds 10
}

$manifest = [ordered]@{
  datasetId = 'fitplan-gemini-food-benchmark-v1'
  generatedAtUtc = (Get-Date).ToUniversalTime().ToString('o')
  source = 'Wikimedia Commons licensed 2400 px derivatives'
  caseCount = $manifestCases.Count
  cases = $manifestCases
}
$manifestPath = Join-Path $DatasetRoot 'manifest.json'
$manifest | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $manifestPath -Encoding utf8
Write-Output "Manifest written to $manifestPath"
