#Requires -Version 7.0

param(
    [ValidateSet('Validate', 'Pilots', 'Full', 'Models', 'All')]
    [string]$Phase = 'Validate',
    [int]$PauseSeconds = 4,
    [string]$OutputDirectory = 'test-results/gemini'
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$root = Split-Path -Parent $PSScriptRoot
$datasetRoot = Join-Path $root 'test-data/gemini-food-benchmark'
$manifest = Get-Content -Raw (Join-Path $datasetRoot 'manifest.json') | ConvertFrom-Json
$caseBook = Get-Content -Raw (Join-Path $datasetRoot 'benchmark-cases.json') | ConvertFrom-Json

function Read-DotEnvValue([string]$Name) {
    $line = Get-Content (Join-Path $root '.env') | Where-Object { $_ -match "^\s*$([regex]::Escape($Name))=" } | Select-Object -First 1
    if (-not $line) { return $null }
    return ($line -split '=', 2)[1].Trim().Trim('"').Trim("'")
}

function Assert-Hash([string]$Path, [string]$Expected) {
    if (-not (Test-Path -LiteralPath $Path)) { throw "Falta el archivo de prueba: $Path" }
    $actual = (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash.ToLowerInvariant()
    if ($actual -ne $Expected.ToLowerInvariant()) { throw "Hash distinto para $Path" }
}

function Get-Percentile([double[]]$Values, [double]$Percentile) {
    if (-not $Values -or $Values.Count -eq 0) { return $null }
    $sorted = @($Values | Sort-Object)
    $index = [math]::Ceiling(($Percentile / 100.0) * $sorted.Count) - 1
    if ($index -lt 0) { $index = 0 }
    return [math]::Round($sorted[$index], 0)
}

function Test-Quality($Nutrition, $Rule) {
    if (-not $Nutrition) {
        return [ordered]@{ identificationPass = $false; plausibilityPass = $false; qualityPass = $false }
    }
    $searchable = ((@($Nutrition.foodName) + @($Nutrition.ingredients)) -join ' ').ToLowerInvariant()
    $identified = $false
    foreach ($keyword in $Rule.keywords) {
        if ($searchable.Contains(([string]$keyword).ToLowerInvariant())) { $identified = $true; break }
    }
    $calories = [double]$Nutrition.calories
    $plausible = $calories -ge [double]$Rule.caloriesMin -and $calories -le [double]$Rule.caloriesMax
    return [ordered]@{ identificationPass = $identified; plausibilityPass = $plausible; qualityPass = ($identified -and $plausible) }
}

function Get-SafeError($Decoded, [int]$Status) {
    $code = if ($Decoded -and $Decoded.code) { [string]$Decoded.code } else { "HTTP_$Status" }
    return [ordered]@{ code = $code; retryable = [bool]($Decoded -and $Decoded.retryable); attempt = if ($Decoded -and $Decoded.attempt) { [int]$Decoded.attempt } else { 0 } }
}

function Invoke-ControlledRequest([string]$Endpoint, $Body, $Metadata, $Rule, [string]$ApiKey, [string]$BaseUrl) {
    $jsonBody = $Body | ConvertTo-Json -Depth 12 -Compress
    $watch = [Diagnostics.Stopwatch]::StartNew()
    $status = 0
    $decoded = $null
    $transportCategory = 'other'
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Method Post -Uri "$BaseUrl/api/gemini/$Endpoint" `
            -Headers @{ Authorization = "Bearer $ApiKey" } -ContentType 'application/json' `
            -Body $jsonBody -TimeoutSec 60 -SkipHttpErrorCheck
        $status = [int]$response.StatusCode
        try { $decoded = $response.Content | ConvertFrom-Json } catch { $transportCategory = 'json_invalid' }
    } catch {
        if ($_.Exception.Message -match 'timed out|tiempo de espera|timeout') {
            $transportCategory = 'timeout'
            $status = 0
        } else {
            $transportCategory = 'network_error'
            $status = 0
        }
    } finally {
        $watch.Stop()
        $jsonBody = $null
        if ($Body -is [Collections.IDictionary] -and $Body.Contains('imageBase64')) { $Body.imageBase64 = $null }
    }

    if ($status -eq 200 -and $decoded -and $decoded.nutrition) { $transportCategory = 'success' }
    elseif ($status -eq 429) { $transportCategory = '429' }
    elseif ($status -eq 503) { $transportCategory = '503' }
    elseif ($status -eq 504) { $transportCategory = 'timeout' }
    elseif ($decoded -and $decoded.code -in @('INVALID_PROVIDER_JSON', 'PROVIDER_INVALID_JSON')) { $transportCategory = 'json_invalid' }
    elseif ($status -eq 422) { $transportCategory = 'invalid_result' }

    $quality = Test-Quality $decoded.nutrition $Rule
    $result = [ordered]@{}
    foreach ($property in $Metadata.GetEnumerator()) { $result[$property.Key] = $property.Value }
    $result.timestampUtc = [DateTime]::UtcNow.ToString('o')
    $result.httpStatus = $status
    $result.outcome = $transportCategory
    $result.latencyMs = [math]::Round($watch.Elapsed.TotalMilliseconds, 0)
    $result.modelVersion = if ($decoded -and $decoded.modelVersion) { [string]$decoded.modelVersion } else { $null }
    $result.metrics = if ($decoded -and $decoded.metrics) { $decoded.metrics } else { $null }
    $result.nutrition = if ($decoded -and $decoded.nutrition) { $decoded.nutrition } else { $null }
    $result.quality = $quality
    $result.error = if ($transportCategory -eq 'success') { $null } else { Get-SafeError $decoded $status }
    return [pscustomobject]$result
}

function New-TextBody($Case, [string]$Model, [string]$Thinking) {
    return [ordered]@{ requestId = [guid]::NewGuid().ToString(); description = $Case.description; model = $Model; thinkingLevel = $Thinking }
}

function New-VisionBody($Case, [string]$Profile, [string]$Model, [string]$Thinking) {
    $prepared = $Case.prepared.$Profile
    $path = Join-Path $datasetRoot $prepared.file
    Assert-Hash $path $prepared.sha256
    $bytes = [IO.File]::ReadAllBytes($path)
    return [ordered]@{
        requestId = [guid]::NewGuid().ToString()
        imageBase64 = [Convert]::ToBase64String($bytes)
        mimeType = 'image/jpeg'
        originalBytes = [int]$Case.original.bytes
        preparedBytes = [int]$prepared.bytes
        originalWidth = [int]$Case.original.width
        originalHeight = [int]$Case.original.height
        preparedWidth = [int]$prepared.width
        preparedHeight = [int]$prepared.height
        quality = [int]$prepared.quality
        model = $Model
        thinkingLevel = $Thinking
    }
}

foreach ($case in $manifest.cases) {
    Assert-Hash (Join-Path $datasetRoot "originals/$($case.fileName)") $case.original.sha256
    foreach ($profile in @('conservative', 'balanced', 'aggressive', 'realistic')) {
        Assert-Hash (Join-Path $datasetRoot $case.prepared.$profile.file) $case.prepared.$profile.sha256
    }
}
Write-Host "Dataset verificado: $($manifest.caseCount) originales y 40 variantes preparadas; hashes SHA-256 correctos."
if ($Phase -eq 'Validate') { return }

$baseUrl = (Read-DotEnvValue 'BACKEND_BASE_URL').TrimEnd('/')
$apiKey = Read-DotEnvValue 'BACKEND_API_KEY'
if (-not $baseUrl -or -not $apiKey) { throw 'Faltan BACKEND_BASE_URL o BACKEND_API_KEY en .env.' }

$runId = [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss')
$outputRoot = Join-Path $root $OutputDirectory
New-Item -ItemType Directory -Force -Path $outputRoot | Out-Null
$jsonlPath = Join-Path $outputRoot "$runId-$($Phase.ToLowerInvariant()).jsonl"
$summaryPath = Join-Path $outputRoot "$runId-$($Phase.ToLowerInvariant())-summary.json"
$results = New-Object Collections.Generic.List[object]

function Save-Result($Result) {
    $script:results.Add($Result)
    $Result | ConvertTo-Json -Depth 12 -Compress | Add-Content -Encoding UTF8 $script:jsonlPath
    Write-Host ("{0,-12} {1,-20} HTTP {2,-3} {3,7} ms {4}" -f $Result.group, $Result.caseId, $Result.httpStatus, $Result.latencyMs, $Result.outcome)
    if ($PauseSeconds -gt 0) { Start-Sleep -Seconds $PauseSeconds }
}

$continueAfterPilots = $true
if ($Phase -in @('Pilots', 'All')) {
    $pilotCase = $manifest.cases | Where-Object id -eq 'simple_apple'
    $rule = $caseBook.imageCases.simple_apple
    foreach ($profile in @('conservative', 'balanced', 'aggressive')) {
        $p = $pilotCase.prepared.$profile
        $body = New-VisionBody $pilotCase $profile 'gemini-3.5-flash' 'minimal'
        Save-Result (Invoke-ControlledRequest 'benchmark/vision' $body ([ordered]@{
            group = 'pilot'; caseId = $pilotCase.id; profile = $profile; model = 'gemini-3.5-flash'; thinkingLevel = 'minimal'
            imageSha256 = $p.sha256; originalBytes = $pilotCase.original.bytes; preparedBytes = $p.bytes
            originalWidth = $pilotCase.original.width; originalHeight = $pilotCase.original.height
            preparedWidth = $p.width; preparedHeight = $p.height; quality = $p.quality
        }) $rule $apiKey $baseUrl)
    }
    if (@($results | Where-Object outcome -ne 'success').Count -gt 0) {
        $continueAfterPilots = $false
        Write-Host 'Al menos un piloto fallo; el modo All no ejecutara las pruebas completas ni la comparacion de modelos.'
    }
}

if ($Phase -eq 'Full' -or ($Phase -eq 'All' -and $continueAfterPilots)) {
    foreach ($case in $caseBook.textCases) {
        Save-Result (Invoke-ControlledRequest 'benchmark/text' (New-TextBody $case 'gemini-3.5-flash' 'minimal') ([ordered]@{
            group = 'text'; caseId = $case.id; profile = $null; model = 'gemini-3.5-flash'; thinkingLevel = 'minimal'; imageSha256 = $null
            originalBytes = 0; preparedBytes = 0; originalWidth = 0; originalHeight = 0; preparedWidth = 0; preparedHeight = 0; quality = 0
        }) $case $apiKey $baseUrl)
    }
    foreach ($profile in @('balanced', 'realistic')) {
        foreach ($case in $manifest.cases) {
            $rule = $caseBook.imageCases.($case.id)
            $p = $case.prepared.$profile
            Save-Result (Invoke-ControlledRequest 'benchmark/vision' (New-VisionBody $case $profile 'gemini-3.5-flash' 'minimal') ([ordered]@{
                group = if ($profile -eq 'balanced') { 'image_optimized' } else { 'image_realistic' }
                caseId = $case.id; profile = $profile; model = 'gemini-3.5-flash'; thinkingLevel = 'minimal'; imageSha256 = $p.sha256
                originalBytes = $case.original.bytes; preparedBytes = $p.bytes; originalWidth = $case.original.width; originalHeight = $case.original.height
                preparedWidth = $p.width; preparedHeight = $p.height; quality = $p.quality
            }) $rule $apiKey $baseUrl)
        }
    }
}

if ($Phase -eq 'Models' -or ($Phase -eq 'All' -and $continueAfterPilots)) {
    $configs = @(
        [pscustomobject]@{ name = 'current_medium'; model = 'gemini-3.5-flash'; thinking = 'medium' },
        [pscustomobject]@{ name = 'flash_lite'; model = 'gemini-3.1-flash-lite'; thinking = 'minimal' },
        [pscustomobject]@{ name = 'current_minimal'; model = 'gemini-3.5-flash'; thinking = 'minimal' }
    )
    foreach ($caseId in @('simple_apple', 'mexican_tacos', 'non_food_mug')) {
        $case = $manifest.cases | Where-Object id -eq $caseId
        $rule = $caseBook.imageCases.$caseId
        $p = $case.prepared.balanced
        foreach ($config in $configs) {
            Save-Result (Invoke-ControlledRequest 'benchmark/vision' (New-VisionBody $case 'balanced' $config.model $config.thinking) ([ordered]@{
                group = "model_$($config.name)"; caseId = $case.id; profile = 'balanced'; model = $config.model; thinkingLevel = $config.thinking
                imageSha256 = $p.sha256; originalBytes = $case.original.bytes; preparedBytes = $p.bytes
                originalWidth = $case.original.width; originalHeight = $case.original.height
                preparedWidth = $p.width; preparedHeight = $p.height; quality = $p.quality
            }) $rule $apiKey $baseUrl)
        }
    }
}

$summaries = @()
foreach ($group in @($results | Group-Object group)) {
    $successful = @($group.Group | Where-Object outcome -eq 'success')
    $latencies = @($successful | ForEach-Object { [double]$_.latencyMs })
    $summaries += [pscustomobject][ordered]@{
        group = $group.Name
        count = $group.Count
        success = $successful.Count
        status429 = @($group.Group | Where-Object outcome -eq '429').Count
        status503 = @($group.Group | Where-Object outcome -eq '503').Count
        timeout = @($group.Group | Where-Object outcome -eq 'timeout').Count
        jsonInvalid = @($group.Group | Where-Object outcome -eq 'json_invalid').Count
        p50Ms = Get-Percentile $latencies 50
        p95Ms = Get-Percentile $latencies 95
        meanPreparedBytes = [math]::Round((($group.Group | Measure-Object preparedBytes -Average).Average), 0)
        qualityPass = @($group.Group | Where-Object { $_.quality.qualityPass }).Count
    }
}
[pscustomobject][ordered]@{
    runId = $runId
    phase = $Phase
    datasetId = $manifest.datasetId
    sequential = $true
    pauseSeconds = $PauseSeconds
    resultCount = $results.Count
    rubricVersion = $caseBook.rubricVersion
    groups = $summaries
} | ConvertTo-Json -Depth 8 | Set-Content -Encoding UTF8 $summaryPath

Write-Host "Resultados sanitizados: $jsonlPath"
Write-Host "Resumen: $summaryPath"
