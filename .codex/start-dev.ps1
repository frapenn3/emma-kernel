$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$jdkRoot = Join-Path $PSScriptRoot "jdks\jdk-21"
$java = Join-Path $jdkRoot "bin\java.exe"
$settings = Join-Path $PSScriptRoot "maven-settings.xml"
$mongoOut = Join-Path $PSScriptRoot "mongo-dev.log"
$mongoErr = Join-Path $PSScriptRoot "mongo-dev.err.log"

if (-not (Test-Path $java)) {
  throw "Project-local JDK 21 not found at $jdkRoot"
}

$env:JAVA_HOME = $jdkRoot
$env:PATH = (Join-Path $jdkRoot "bin") + [IO.Path]::PathSeparator + $env:PATH

Push-Location $repoRoot
try {
  $mongoUp = Test-NetConnection -ComputerName localhost -Port 27017 -InformationLevel Quiet
  if (-not $mongoUp) {
    $m2 = Join-Path $PSScriptRoot "m2\repository"
    $mongoJars = @(
      "de\bwaldvogel\mongo-java-server-memory-backend\1.46.0\mongo-java-server-memory-backend-1.46.0.jar",
      "de\bwaldvogel\mongo-java-server-core\1.46.0\mongo-java-server-core-1.46.0.jar",
      "io\netty\netty-transport\4.1.108.Final\netty-transport-4.1.108.Final.jar",
      "io\netty\netty-common\4.1.108.Final\netty-common-4.1.108.Final.jar",
      "io\netty\netty-buffer\4.1.108.Final\netty-buffer-4.1.108.Final.jar",
      "io\netty\netty-resolver\4.1.108.Final\netty-resolver-4.1.108.Final.jar",
      "io\netty\netty-codec\4.1.108.Final\netty-codec-4.1.108.Final.jar",
      "io\netty\netty-handler\4.1.108.Final\netty-handler-4.1.108.Final.jar",
      "io\netty\netty-transport-native-unix-common\4.1.108.Final\netty-transport-native-unix-common-4.1.108.Final.jar",
      "org\slf4j\slf4j-api\2.0.6\slf4j-api-2.0.6.jar"
    ) | ForEach-Object { Join-Path $m2 $_ }
    $classpath = [string]::Join([IO.Path]::PathSeparator, $mongoJars)
    Start-Process -FilePath $java `
      -ArgumentList @("-cp", "`"$classpath`"", "de.bwaldvogel.mongo.InMemoryMongoServer") `
      -WorkingDirectory $repoRoot `
      -RedirectStandardOutput $mongoOut `
      -RedirectStandardError $mongoErr `
      -WindowStyle Hidden

    $deadline = (Get-Date).AddSeconds(20)
    do {
      Start-Sleep -Milliseconds 500
      $mongoUp = Test-NetConnection -ComputerName localhost -Port 27017 -InformationLevel Quiet
    } while (-not $mongoUp -and (Get-Date) -lt $deadline)

    if (-not $mongoUp) {
      throw "In-memory Mongo did not start on localhost:27017. See $mongoErr"
    }
  }

  & mvn -s $settings quarkus:dev
  exit $LASTEXITCODE
}
finally {
  Pop-Location
}
