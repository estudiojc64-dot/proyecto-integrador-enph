# Inicia JavaFX; ENPH_TOKEN y ENPH_SERVER_URL proceden del entorno del operador.
param([string]$JavaHome = $env:JAVA_HOME)
. (Join-Path $PSScriptRoot 'Common.ps1') -JavaHome $JavaHome
& $EnphJava '-Dfile.encoding=UTF-8' --enable-native-access=javafx.graphics --module-path $EnphLibraries --add-modules javafx.controls -cp $EnphClasses co.edu.udi.enph.EnphApp
Assert-EnphExit

