param([switch]$Test)
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    # Prefer a complete JDK over an outdated Java launcher on PATH.
    $jdk = $env:JAVA_HOME
    if (-not $jdk -or -not (Test-Path (Join-Path $jdk 'bin/javac.exe'))) {
        $installed = Get-ChildItem "$env:ProgramFiles/Java" -Directory -Filter 'jdk*' -ErrorAction SilentlyContinue |
            Sort-Object LastWriteTime -Descending | Select-Object -First 1
        $jdk = if ($installed) { $installed.FullName } else { $null }
    }
    $compiler = if ($jdk) { Join-Path $jdk 'bin/javac.exe' } else { 'javac' }
    $runtime = if ($jdk) { Join-Path $jdk 'bin/java.exe' } else { 'java' }
    New-Item -ItemType Directory -Force build | Out-Null
    $sources = @(Get-ChildItem src -Recurse -Filter *.java | ForEach-Object FullName)
    if ($Test) { $sources += @(Get-ChildItem test -Recurse -Filter *.java | ForEach-Object FullName) }
    & $compiler --release 17 -encoding UTF-8 -d build @sources
    if ($LASTEXITCODE -ne 0) { throw 'Compilation failed. JDK 17 or newer is required.' }
    if ($Test) {
        & $runtime -cp build shop.ShopTest
        if ($LASTEXITCODE -ne 0) { throw 'Shopping tests failed.' }
        & $runtime -cp build shop.AuthTest
        if ($LASTEXITCODE -ne 0) { throw 'Auth tests failed.' }
        & $runtime -cp build shop.CommerceTest
        if ($LASTEXITCODE -ne 0) { throw 'Commerce tests failed.' }
        & $runtime -cp build shop.CartStockTest
        if ($LASTEXITCODE -ne 0) { throw 'Cart stock tests failed.' }
        & $runtime -cp build shop.SellerToolsTest
        if ($LASTEXITCODE -ne 0) { throw 'Seller tools tests failed.' }
        & $runtime -cp build shop.NumberInputTest
        if ($LASTEXITCODE -ne 0) { throw 'Number input tests failed.' }
        & $runtime -cp build shop.ProductEditTest
        if ($LASTEXITCODE -ne 0) { throw 'Product edit tests failed.' }
        & $runtime -cp build shop.SelectionCheckoutTest
        if ($LASTEXITCODE -ne 0) { throw 'Selection checkout tests failed.' }
        & $runtime -cp build shop.CardActionsTest
        if ($LASTEXITCODE -ne 0) { throw 'Card action tests failed.' }
        & $runtime -cp build shop.MerchantOrderTest
        if ($LASTEXITCODE -ne 0) { throw 'Merchant order tests failed.' }
        & $runtime -cp build shop.ProductImageTest
        if ($LASTEXITCODE -ne 0) { throw 'Product image tests failed.' }
        & $runtime -cp build shop.StudentDiscountTest
    }
    else { & $runtime -cp build shop.Main }
    if ($LASTEXITCODE -ne 0) { throw 'Java program failed.' }
} finally { Pop-Location }
