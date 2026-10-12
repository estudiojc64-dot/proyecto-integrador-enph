# Utilidades compartidas. El JDK se configura mediante JAVA_HOME o -JavaHome.
# Nunca cambia la instalación Java del equipo ni las variables persistentes.
param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$EnphRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if (-not $JavaHome) {
    $EnphJdks = Get-ChildItem -LiteralPath 'C:/Program Files/Java' -Directory -ErrorAction SilentlyContinue |
        Where-Object Name -Like 'jdk*' | Sort-Object Name -Descending
    if ($EnphJdks) { $JavaHome = $EnphJdks[0].FullName }
}
if (-not $JavaHome -or -not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin/javac.exe'))) {
    throw 'Configure JAVA_HOME con un JDK 17 o posterior.'
}
$EnphJava = Join-Path $JavaHome 'bin/java.exe'
$EnphJavac = Join-Path $JavaHome 'bin/javac.exe'
$EnphJavadoc = Join-Path $JavaHome 'bin/javadoc.exe'
$EnphClasses = Join-Path $EnphRoot 'build/classes'
$EnphLibraries = Join-Path $EnphRoot 'lib'
function Assert-EnphExit { if ($LASTEXITCODE -ne 0) { throw "La operación Java terminó con código $LASTEXITCODE." } }
function Get-EnphDriver {
    # OJDBC_JAR debe apuntar a un driver oficial compatible con el Oracle utilizado.
    if ($env:OJDBC_JAR -and (Test-Path -LiteralPath $env:OJDBC_JAR)) { return $env:OJDBC_JAR }
    $EnphLocalDriver = 'C:/Oracle/dbhomeXE/jdbc/lib/ojdbc11.jar'
    if (Test-Path -LiteralPath $EnphLocalDriver) { return $EnphLocalDriver }
    throw 'Configure OJDBC_JAR con la ruta absoluta del controlador oficial Oracle JDBC.'
}

