# Inicia el servidor en primer plano; Ctrl+C termina el proceso.
param([string]$JavaHome = $env:JAVA_HOME)
. (Join-Path $PSScriptRoot 'Common.ps1') -JavaHome $JavaHome
$EnphDriver = Get-EnphDriver
& $EnphJava '-Dfile.encoding=UTF-8' --add-modules jdk.httpserver -cp "$EnphClasses;$EnphDriver" co.edu.udi.enph.Server
Assert-EnphExit

