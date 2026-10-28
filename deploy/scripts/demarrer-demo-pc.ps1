# Démo sur ce PC (sans serveur) : démarre la plateforme Docker puis ouvre un tunnel Cloudflare HTTPS.
# Usage (PowerShell, depuis la racine du dépôt) :  .\deploy\scripts\demarrer-demo-pc.ps1
# Prérequis : Docker Desktop lancé, deploy\.env créé avec DOMAINE=:80 et HTTP_PORT=8090,
#             cloudflared installé (winget install --id Cloudflare.cloudflared).
# L'adresse publique change à chaque lancement : la renvoyer au client (Plateforme RH + écran « Serveur » de RH Connect).

$ErrorActionPreference = 'Stop'
$deploy = Split-Path -Parent $PSScriptRoot
$port = ((Get-Content "$deploy\.env" | Where-Object { $_ -match '^HTTP_PORT=' }) -replace 'HTTP_PORT=', '').Trim()
if (-not $port) { $port = '8090' }

Push-Location $deploy
docker compose up -d
Pop-Location

$cloudflared = 'C:\Program Files (x86)\cloudflared\cloudflared.exe'
if (-not (Test-Path $cloudflared)) { $cloudflared = 'cloudflared' }
$log = Join-Path $env:TEMP 'sirh-tunnel.log'
Remove-Item $log -ErrorAction SilentlyContinue
Start-Process -FilePath $cloudflared -ArgumentList "tunnel --no-autoupdate --url http://localhost:$port" `
  -RedirectStandardError $log -WindowStyle Minimized

Write-Host "Ouverture du tunnel…"
for ($i = 0; $i -lt 60; $i++) {
  Start-Sleep -Seconds 2
  $m = Select-String -Path $log -Pattern 'https://[a-z0-9-]+\.trycloudflare\.com' -ErrorAction SilentlyContinue | Select-Object -First 1
  if ($m) { break }
}
if (-not $m) { Write-Host "Adresse introuvable, voir $log"; exit 1 }
$url = $m.Matches[0].Value
Write-Host ""
Write-Host "Adresse publique de la démo : $url" -ForegroundColor Green
Write-Host "Les services Java sont prêts 1 à 2 minutes après le démarrage de Docker."
Write-Host "Laisser ce PC allumé, sans mise en veille. Fermer la fenêtre « cloudflared » coupe l'accès."
