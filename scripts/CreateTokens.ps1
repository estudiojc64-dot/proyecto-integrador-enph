# Genera cinco credenciales privadas para las referencias ficticias de DbSetup.
# No imprime tokens. El archivo se crea fuera del código publicado y no sustituye
# uno existente. Copie solo el token del operador al ENPH_TOKEN del cliente.
param([string]$Destination = (Join-Path $PSScriptRoot '../.private/tokens.properties'))
$ErrorActionPreference = 'Stop'
$EnphDestination = [IO.Path]::GetFullPath($Destination)
if (Test-Path -LiteralPath $EnphDestination) { throw 'El archivo de tokens ya existe; no se sobrescribe.' }
New-Item -ItemType Directory -Path (Split-Path -Parent $EnphDestination) -Force | Out-Null
$EnphLines = foreach ($EnphNumber in 1..5) {
    $EnphId = '00000000-0000-0000-0000-' + $EnphNumber.ToString('D12')
    $EnphBytes = New-Object byte[] 32
    $EnphRandom = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $EnphRandom.GetBytes($EnphBytes) } finally { $EnphRandom.Dispose() }
    $EnphToken = [BitConverter]::ToString($EnphBytes).Replace('-','')
    "$EnphId=$EnphToken"
}
[IO.File]::WriteAllLines($EnphDestination,$EnphLines,[Text.UTF8Encoding]::new($false))
Write-Output 'Tokens generados en el archivo privado solicitado. No publique este archivo.'

