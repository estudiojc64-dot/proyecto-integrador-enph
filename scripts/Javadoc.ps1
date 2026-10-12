# Genera documentación navegable desde los comentarios dentro de los .java.
param([string]$JavaHome = $env:JAVA_HOME)
. (Join-Path $PSScriptRoot 'Common.ps1') -JavaHome $JavaHome
$EnphFiles = Get-ChildItem -LiteralPath (Join-Path $EnphRoot 'src/main/java') -Filter '*.java' -Recurse
& $EnphJavadoc -quiet -encoding UTF-8 -docencoding UTF-8 '-Xdoclint:none' --module-path $EnphLibraries --add-modules javafx.controls,jdk.httpserver -d (Join-Path $EnphRoot 'build/javadoc') @($EnphFiles.FullName)
Assert-EnphExit
Write-Output 'Documentación disponible en build/javadoc/index.html.'

