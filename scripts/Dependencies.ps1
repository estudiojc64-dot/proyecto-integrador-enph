# Dependencias oficiales para simular la tableta en Windows x64.
# Versión fija y comprobación SHA-256: no se descarga una versión flotante.
param([string]$JavaHome = $env:JAVA_HOME)
. (Join-Path $PSScriptRoot 'Common.ps1') -JavaHome $JavaHome
New-Item -ItemType Directory -Path $EnphLibraries -Force | Out-Null
$EnphHashes = @{
    base='88FD46AA5EF155E3A6AE0504BA0051B7DD295FF1738713B428A86B6B5F690593'
    graphics='59F32A0C04970D0BA03C092E66DF6828DEBE36E514E18E452BED3250117EEC37'
    controls='8EA3BA8BE1B4F9BE0A6564EB557700B7916A1BE7176D9BBB8E810D9FFBF789D8'
}
foreach ($EnphModule in @('base','graphics','controls')) {
    $EnphFilename = "javafx-$EnphModule-17.0.16-win.jar"
    $EnphTarget = Join-Path $EnphLibraries $EnphFilename
    if (-not (Test-Path -LiteralPath $EnphTarget)) {
        Invoke-WebRequest -Uri "https://repo.maven.apache.org/maven2/org/openjfx/javafx-$EnphModule/17.0.16/$EnphFilename" -OutFile $EnphTarget
    }
    if ((Get-FileHash -LiteralPath $EnphTarget -Algorithm SHA256).Hash -ne $EnphHashes[$EnphModule]) {
        throw "La biblioteca $EnphFilename no coincide con la huella documentada. No se ejecutará."
    }
}
Write-Output 'JavaFX 17.0.16 para Windows verificado.'

