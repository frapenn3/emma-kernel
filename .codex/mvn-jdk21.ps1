param(
  [Parameter(ValueFromRemainingArguments = $true)]
  [string[]] $MavenArgs
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$jdkRoot = Join-Path $PSScriptRoot "jdks\jdk-21"
$settings = Join-Path $PSScriptRoot "maven-settings.xml"

if (-not (Test-Path (Join-Path $jdkRoot "bin\java.exe"))) {
  throw "Project-local JDK 21 not found at $jdkRoot"
}

$env:JAVA_HOME = $jdkRoot
$env:PATH = (Join-Path $jdkRoot "bin") + [IO.Path]::PathSeparator + $env:PATH

if (-not $MavenArgs -or $MavenArgs.Count -eq 0) {
  $MavenArgs = @("test")
}

Push-Location $repoRoot
try {
  & mvn -s $settings @MavenArgs
  exit $LASTEXITCODE
}
finally {
  Pop-Location
}
