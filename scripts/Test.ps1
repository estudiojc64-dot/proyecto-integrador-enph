# Reglas/local/HTTP siempre; integración REAL Oracle solo con -Oracle.
param([string]$JavaHome = $env:JAVA_HOME,[switch]$Oracle)
. (Join-Path $PSScriptRoot 'Common.ps1') -JavaHome $JavaHome
$EnphTmp = Join-Path $EnphRoot 'build/tmp'
New-Item -ItemType Directory -Path $EnphTmp -Force | Out-Null
& $EnphJava "-Djava.io.tmpdir=$EnphTmp" '-Dfile.encoding=UTF-8' --add-modules jdk.httpserver -cp $EnphClasses co.edu.udi.enph.ProjectTests
Assert-EnphExit
if ($Oracle) {
    $EnphDriver = Get-EnphDriver
    & $EnphJava '-Dfile.encoding=UTF-8' --add-modules jdk.httpserver -cp "$EnphClasses;$EnphDriver" co.edu.udi.enph.OracleIntegrationTest
    Assert-EnphExit
}

