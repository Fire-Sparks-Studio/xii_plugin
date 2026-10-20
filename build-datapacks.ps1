# Regénère les deux datapacks XII-Days à partir de xii-datapack/.
#
#   xii-datapack/                       <- sources (éditer ici)
#   xii-datapack/pack-worldgen/         -> xii-plaines-1-worldgen.zip
#   xii-datapack/pack-structures/       -> xii-plaines-2-structures.zip
#
# Utilisation :  pwsh -File .\build-datapacks.ps1
#
# Le pack 2 doit être chargé AU-DESSUS du pack 1 dans le monde
# (datapacks chargés dans l'ordre alphabétique : le nom du zip s'en charge).

$ErrorActionPreference = 'Stop'

$root  = $PSScriptRoot
$packs = @(
    @{ Src = 'xii-datapack\pack-worldgen';    Zip = 'xii-plaines-1-worldgen.zip' },
    @{ Src = 'xii-datapack\pack-structures';  Zip = 'xii-plaines-2-structures.zip' }
)

foreach ($pack in $packs) {
    $srcDir = Join-Path $root $pack.Src
    $zipPath = Join-Path $root $pack.Zip

    if (-not (Test-Path $srcDir)) { throw "Source introuvable : $srcDir" }
    if (Test-Path $zipPath) { Remove-Item $zipPath -Force }

    # pack.mcmeta doit être à la racine de l'archive.
    $entries = Get-ChildItem -Path $srcDir -Recurse -File
    $staging = Join-Path ([System.IO.Path]::GetTempPath()) ("xii_" + [System.Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $staging | Out-Null
    try {
        foreach ($f in $entries) {
            $rel = $f.FullName.Substring($srcDir.Length + 1)
            $dest = Join-Path $staging $rel
            New-Item -ItemType Directory -Force -Path (Split-Path $dest) | Out-Null
            Copy-Item $f.FullName $dest
        }
        Compress-Archive -Path (Join-Path $staging '*') -DestinationPath $zipPath -CompressionLevel Optimal
    } finally {
        Remove-Item -Recurse -Force $staging -ErrorAction SilentlyContinue
    }

    $count = $entries.Count
    Write-Host ("OK  {0}  ({1} fichiers)" -f $pack.Zip, $count)
}
