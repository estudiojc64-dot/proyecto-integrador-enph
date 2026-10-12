# Compila código principal y pruebas. No necesita Maven ni Gradle.
param([string]$JavaHome = $env:JAVA_HOME,[switch]$CoreOnly)
. (Join-Path $PSScriptRoot 'Common.ps1') -JavaHome $JavaHome
New-Item -ItemType Directory -Path $EnphClasses -Force | Out-Null
$EnphFiles = Get-ChildItem -LiteralPath (Join-Path $EnphRoot 'src') -Filter '*.java' -Recurse
$EnphArguments = @('--release','17','-encoding','UTF-8','-d',$EnphClasses)
if ($CoreOnly) { $EnphFiles = $EnphFiles | Where-Object Name -NotIn @('EnphApp.java','UiSmoke.java') }
else {
    foreach ($EnphModule in @('base','graphics','controls')) {
        if (-not (Test-Path -LiteralPath (Join-Path $EnphLibraries "javafx-$EnphModule-17.0.16-win.jar"))) {
            throw 'Ejecute scripts/Dependencies.ps1 antes de compilar la interfaz.'
        }
    }
    $EnphArguments += @('--module-path',$EnphLibraries,'--add-modules','javafx.controls,jdk.httpserver')
}
& $EnphJavac @EnphArguments @($EnphFiles.FullName)
Assert-EnphExit
Write-Output 'Compilación completa.'

