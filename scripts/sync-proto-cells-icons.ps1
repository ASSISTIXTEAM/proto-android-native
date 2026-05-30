$ErrorActionPreference = "Stop"
$root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$src = Join-Path $root "PROTO\cells.png"
$srcLight = Join-Path $root "PROTO\cells-light.png"
$drawable = Join-Path $PSScriptRoot "..\app\src\main\res\drawable"
if (-not (Test-Path $src)) { throw "Missing PROTO/cells.png at $src" }
New-Item -ItemType Directory -Path $drawable -Force | Out-Null
Copy-Item $src (Join-Path $drawable "proto_cells_icon.png") -Force
if (Test-Path $srcLight) {
    Copy-Item $srcLight (Join-Path $drawable "proto_cells_icon_light.png") -Force
} else {
    Copy-Item $src (Join-Path $drawable "proto_cells_icon_light.png") -Force
}
Write-Host "[syncProtoCellsIcons] cells.png -> drawable (cells-light synced as fallback copy)"
