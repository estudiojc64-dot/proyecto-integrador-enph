# Instala en el esquema NUEVO que el operador configure. No crea cuentas Oracle.
param([string]$JavaHome = $env:JAVA_HOME)
. (Join-Path $PSScriptRoot 'Common.ps1') -JavaHome $JavaHome
$EnphDriver = Get-EnphDriver
& $EnphJava '-Dfile.encoding=UTF-8' -cp "$EnphClasses;$EnphDriver" co.edu.udi.enph.DbSetup (Join-Path $EnphRoot 'db/01_esquema.sql')
Assert-EnphExit

