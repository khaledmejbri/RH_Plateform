"""Serveur MCP « sirh » : outils d'exploitation du SIRH RH-Évènement pour Antigravity (ou tout client MCP).

Lancement (stdio) : `uv run --with "mcp>=1.2" python tools/mcp-sirh/server.py`

Principes :
- Aucun secret n'est jamais renvoyé : le jeton Codemagic et le mot de passe de recette sont lus ici
  (variables d'environnement utilisateur Windows) et retirés de toute sortie.
- Les actions qui changent l'état du kit de démo ou lancent un build exigent `confirmer=True` :
  l'agent doit d'abord avoir l'accord de l'utilisateur (le kit sert souvent au client).
- La base n'est lue qu'en transaction READ ONLY.
"""
from __future__ import annotations

import json
import os
import re
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
from datetime import datetime
from pathlib import Path

from mcp.server.fastmcp import FastMCP

RACINE = Path(__file__).resolve().parents[2]
DEPLOY = RACINE / "deploy"
DOSSIER_RECETTE = Path(os.environ.get("SIRH_RECETTE_DIR", str(RACINE / "recette")))
OUTILS_RECETTE = DOSSIER_RECETTE / "outils"
FICHIER_ADRESSE = OUTILS_RECETTE / "adresse_publique.txt"
MESSAGE_CLIENT = DOSSIER_RECETTE / "message_client.md"
JOURNAL_TUNNEL = Path(tempfile.gettempdir()) / "sirh-tunnel.log"
CLOUDFLARED = Path(r"C:\Program Files (x86)\cloudflared\cloudflared.exe")
DOCKER_DESKTOP = Path(r"C:\Program Files\Docker\Docker\Docker Desktop.exe")
KIT = os.environ.get("SIRH_KIT_URL", "http://localhost:8090")
CODEMAGIC_API = "https://api.codemagic.io"
CODEMAGIC_APP = os.environ.get("SIRH_CODEMAGIC_APP", "6a7f0915506a25c33389a606")
SERVICES = ["eureka-server", "gateway", "svc-identite-acces", "svc-referentiel-rh", "svc-evaluation",
            "svc-notification", "svc-presence", "web"]
BASES = ["authentication_db", "referentiel_rh_db", "evaluation_rh_db", "presence_db", "notification_db"]
CHEMINS_SANTE = ["/api/referentiel/v1/collaborateurs/moi", "/api/rh/v1/admin/evaluations/campaigns",
                 "/api/rh/v1/admin/presence/sites", "/api/rh/v1/notifications/compteur",
                 "/api/rh/v1/demandes-administratives?page=0&size=1"]
ADRESSE_TUNNEL = re.compile(r"https://[a-z0-9-]+\.trycloudflare\.com")

mcp = FastMCP("sirh")


# ---------------------------------------------------------------- utilitaires

def _variable_secrete(nom: str) -> str | None:
    """Variable d'environnement, sinon variable utilisateur Windows (registre HKCU\\Environment)."""
    valeur = os.environ.get(nom)
    if valeur:
        return valeur
    if sys.platform == "win32":
        try:
            import winreg
            with winreg.OpenKey(winreg.HKEY_CURRENT_USER, "Environment") as cle:
                return str(winreg.QueryValueEx(cle, nom)[0]) or None
        except OSError:
            return None
    return None


def _mot_de_passe_recette() -> str | None:
    valeur = _variable_secrete("DEMO_PW")
    if valeur:
        return valeur
    fichier = OUTILS_RECETTE / ("recette" + "_pw.txt")
    try:
        return fichier.read_text(encoding="utf-8").strip() or None
    except OSError:
        return None


def _masquer(texte: str) -> str:
    for nom in ("CODEMAGIC_TOKEN", "DEMO_PW"):
        secret = _variable_secrete(nom)
        if secret and len(secret) >= 4:
            texte = texte.replace(secret, "***")
    return texte


def _lancer(commande: list[str], delai: int = 120, dossier: Path | None = None) -> tuple[int, str]:
    try:
        r = subprocess.run(commande, capture_output=True, text=True, encoding="utf-8", errors="replace",
                           timeout=delai, cwd=str(dossier or RACINE))
        return r.returncode, _masquer((r.stdout or "") + (r.stderr or "")).strip()
    except FileNotFoundError:
        return 127, f"commande introuvable : {commande[0]}"
    except subprocess.TimeoutExpired:
        return 124, f"délai dépassé ({delai} s) : {' '.join(commande[:3])}…"


def _http(methode: str, url: str, corps: dict | None = None, jeton: str | None = None,
          entetes: dict | None = None, delai: int = 20) -> tuple[int, object]:
    donnees = json.dumps(corps).encode() if corps is not None else None
    requete = urllib.request.Request(url, data=donnees, method=methode)
    requete.add_header("Content-Type", "application/json")
    if jeton:
        requete.add_header("Authorization", "Bearer " + jeton)
    for cle, valeur in (entetes or {}).items():
        requete.add_header(cle, valeur)
    try:
        with urllib.request.urlopen(requete, timeout=delai) as r:
            texte = r.read().decode("utf-8", "replace")
            return r.status, (json.loads(texte) if texte.strip().startswith(("{", "[")) else texte)
    except urllib.error.HTTPError as e:
        texte = e.read().decode("utf-8", "replace")
        try:
            return e.code, json.loads(texte)
        except ValueError:
            return e.code, texte[:300]
    except (urllib.error.URLError, TimeoutError, OSError) as e:
        return 0, str(e)[:200]


def _statut_signin(base: str = KIT) -> int:
    return _http("POST", base + "/api/auth/signin", {}, delai=15)[0]


def _attendre(condition, delai: int, pas: float = 5.0) -> bool:
    fin = time.time() + delai
    while time.time() < fin:
        if condition():
            return True
        time.sleep(pas)
    return False


def _docker_pret() -> bool:
    return _lancer(["docker", "info"], delai=20)[0] == 0


# ---------------------------------------------------------------- kit de démo

@mcp.tool()
def kit_etat() -> str:
    """État du kit de démo : Docker, conteneurs sirh-* (statut + image), tunnel Cloudflare, adresse publique enregistrée."""
    lignes = [f"Docker : {'OK' if _docker_pret() else 'ARRÊTÉ (outil kit_demarrer_docker)'}"]
    code, sortie = _lancer(["docker", "ps", "-a", "--filter", "name=sirh-", "--format",
                            "{{.Names}}\t{{.Status}}\t{{.Image}}"], delai=30)
    lignes.append("Conteneurs :\n" + (sortie if code == 0 and sortie else "  (aucun ou Docker indisponible)"))
    code, sortie = _lancer(["tasklist", "/FI", "IMAGENAME eq cloudflared.exe", "/NH"], delai=15)
    lignes.append("Tunnel cloudflared : " + ("en cours" if "cloudflared.exe" in sortie else "arrêté"))
    adresse = FICHIER_ADRESSE.read_text(encoding="utf-8-sig").strip() if FICHIER_ADRESSE.exists() else "(inconnue)"
    statut = _http("GET", adresse + "/", delai=15)[0] if adresse.startswith("https://") else 0
    lignes.append(f"Adresse publique : {adresse} → HTTP {statut} (0 = tunnel mort)")
    lignes.append(f"Connexion locale ({KIT}) : HTTP {_statut_signin()} (400 = prêt, 503 = services en cours d'enregistrement)")
    return "\n".join(lignes)


@mcp.tool()
def kit_demarrer_docker(attendre_secondes: int = 240) -> str:
    """Démarre Docker Desktop s'il est arrêté et attend qu'il réponde ; les conteneurs sirh-* repartent seuls."""
    if _docker_pret():
        return "Docker répond déjà."
    if not DOCKER_DESKTOP.exists():
        return f"Docker Desktop introuvable : {DOCKER_DESKTOP}"
    subprocess.Popen([str(DOCKER_DESKTOP)], creationflags=getattr(subprocess, "DETACHED_PROCESS", 0))
    if not _attendre(_docker_pret, attendre_secondes):
        return f"Docker ne répond pas après {attendre_secondes} s."
    pret = _attendre(lambda: _statut_signin() == 400, 240)
    return "Docker démarré. " + ("Services prêts (signin 400)." if pret else
                                  "Services pas encore prêts : relancer kit_etat dans 1 à 2 min.")


@mcp.tool()
def kit_sante(base: str = KIT) -> str:
    """Contrôle de santé : connexion DRH01 puis 5 GET représentatifs (attendu : 200 partout). Aucun secret renvoyé."""
    mot_de_passe = _mot_de_passe_recette()
    if not mot_de_passe:
        return "Mot de passe de recette introuvable (variable utilisateur DEMO_PW) : demander à l'utilisateur."
    statut, reponse = _http("POST", base + "/api/auth/signin", {"username": "DRH01", "password": mot_de_passe})
    if statut != 200 or not isinstance(reponse, dict):
        return f"Connexion DRH01 : KO (HTTP {statut}). Services démarrés ? (kit_etat)"
    jeton = reponse.get("jeton_acces") or reponse.get("accessToken") or reponse.get("access_token")
    lignes = ["Connexion DRH01 : OK"]
    for chemin in CHEMINS_SANTE:
        lignes.append(f"{chemin} → {_http('GET', base + chemin, jeton=jeton)[0]}")
    return "\n".join(lignes)


@mcp.tool()
def kit_logs(service: str, lignes: int = 80) -> str:
    """Dernières lignes du journal d'un conteneur du kit (ex. svc-referentiel-rh, gateway, web)."""
    nom = service if service.startswith("sirh-") else f"sirh-{service}-1"
    code, sortie = _lancer(["docker", "logs", "--tail", str(max(1, min(lignes, 400))), nom], delai=30)
    return sortie[-12000:] if sortie else f"(rien, code {code})"


@mcp.tool()
def kit_relancer_tunnel(confirmer: bool = False) -> str:
    """Relance le tunnel Cloudflare rapide vers le kit (8090). L'ADRESSE CHANGE : à faire avec l'accord de
    l'utilisateur (confirmer=True). Met à jour recette/outils/adresse_publique.txt et recette/message_client.md."""
    if not confirmer:
        return "Action non lancée : l'adresse publique va changer. Demande l'accord de l'utilisateur puis rappelle avec confirmer=True."
    if not CLOUDFLARED.exists():
        return f"cloudflared introuvable : {CLOUDFLARED}"
    _lancer(["taskkill", "/IM", "cloudflared.exe", "/F"], delai=15)
    JOURNAL_TUNNEL.unlink(missing_ok=True)
    journal = open(JOURNAL_TUNNEL, "w", encoding="utf-8")
    drapeaux = getattr(subprocess, "DETACHED_PROCESS", 0) | getattr(subprocess, "CREATE_NO_WINDOW", 0)
    subprocess.Popen([str(CLOUDFLARED), "tunnel", "--no-autoupdate", "--url", KIT],
                     stdout=subprocess.DEVNULL, stderr=journal, creationflags=drapeaux)
    adresse: list[str] = []

    def trouvee() -> bool:
        m = ADRESSE_TUNNEL.search(JOURNAL_TUNNEL.read_text(encoding="utf-8", errors="replace")) if JOURNAL_TUNNEL.exists() else None
        if m:
            adresse.append(m.group(0))
        return bool(m)

    if not _attendre(trouvee, 60, 2):
        return f"Aucune adresse dans le journal du tunnel après 60 s ({JOURNAL_TUNNEL})."
    url = adresse[0]
    FICHIER_ADRESSE.parent.mkdir(parents=True, exist_ok=True)
    FICHIER_ADRESSE.write_text(url + "\n", encoding="utf-8")
    if MESSAGE_CLIENT.exists():
        MESSAGE_CLIENT.write_text(ADRESSE_TUNNEL.sub(url, MESSAGE_CLIENT.read_text(encoding="utf-8")), encoding="utf-8")
    joignable = _attendre(lambda: _statut_signin(url) == 400, 120)
    return f"Nouvelle adresse : {url}\n" + ("Elle répond (signin 400)." if joignable else
                                            "Elle ne répond pas encore (DNS) : revérifier avec kit_etat dans 1 min.")


@mcp.tool()
def kit_redeployer(service: str, image_tag: str, confirmer: bool = False) -> str:
    """Remplace l'image d'un service du kit par sirh/<service>:<image_tag> (déjà construite), en gardant
    l'ancienne sous :avant-<date>. Enchaîne : up --no-deps --no-build, attente Eureka, redémarrage gateway puis web,
    attente signin 400. Le kit sert au client : confirmer=True seulement avec l'accord de l'utilisateur."""
    if service not in SERVICES:
        return f"Service inconnu. Valeurs : {', '.join(SERVICES)}"
    if not confirmer:
        return "Action non lancée : redéploiement du kit de démo. Demande l'accord de l'utilisateur puis confirmer=True."
    image = f"sirh/{service}"
    if _lancer(["docker", "image", "inspect", f"{image}:{image_tag}"], delai=20)[0] != 0:
        return f"Image {image}:{image_tag} introuvable : la construire d'abord."
    sauvegarde = "avant-" + datetime.now().strftime("%Y%m%d-%H%M")
    journal = [f"Sauvegarde : {image}:latest → {image}:{sauvegarde}"]
    _lancer(["docker", "tag", f"{image}:latest", f"{image}:{sauvegarde}"])
    _lancer(["docker", "tag", f"{image}:{image_tag}", f"{image}:latest"])
    code, sortie = _lancer(["docker", "compose", "-p", "sirh", "up", "-d", "--no-deps", "--no-build", service],
                           delai=180, dossier=DEPLOY)
    journal.append(sortie[-800:])
    if code != 0:
        return "\n".join(journal + [f"Échec du up (code {code}). Retour arrière : kit_redeployer('{service}', '{sauvegarde}', True)"])
    if service not in ("web", "gateway", "eureka-server"):
        inscrit = _attendre(lambda: "registration status: 204" in _lancer(
            ["docker", "logs", "--tail", "300", f"sirh-{service}-1"], delai=30)[1], 300, 10)
        journal.append("Inscription Eureka : " + ("OK" if inscrit else "non vue en 5 min (voir kit_logs)"))
    if service != "web":
        _lancer(["docker", "restart", "sirh-gateway-1"], delai=120)
        time.sleep(30)
    _lancer(["docker", "restart", "sirh-web-1"], delai=120)
    pret = _attendre(lambda: _statut_signin() == 400, 300)
    journal.append("Signin : " + ("400, kit prêt" if pret else "pas prêt après 5 min (instances Eureka DOWN ?)"))
    journal.append(f"Retour arrière possible : kit_redeployer('{service}', '{sauvegarde}', True)")
    return "\n".join(journal)


# ---------------------------------------------------------------- Codemagic

def _codemagic(methode: str, chemin: str, corps: dict | None = None) -> tuple[int, object]:
    jeton = _variable_secrete("CODEMAGIC_TOKEN")
    if not jeton:
        return 0, "Variable utilisateur CODEMAGIC_TOKEN absente."
    entetes = {"x-auth-token": jeton}
    if methode == "POST" and corps is None:
        entetes["Content-Length"] = "0"
    return _http(methode, CODEMAGIC_API + chemin, corps, entetes=entetes, delai=30)


@mcp.tool()
def codemagic_lancer_build(workflow: str = "rh-connect-android", branche: str = "delivery",
                           confirmer: bool = False) -> str:
    """Lance un build Codemagic (le push ne le déclenche pas). Codemagic construit le dépôt `sirh` : pousser avant.
    confirmer=True avec l'accord de l'utilisateur (minutes de build facturées)."""
    if not confirmer:
        return "Action non lancée : un build consomme des minutes Codemagic. Demande l'accord puis confirmer=True."
    statut, reponse = _codemagic("POST", "/builds", {"appId": CODEMAGIC_APP, "workflowId": workflow, "branch": branche})
    return _masquer(f"HTTP {statut} : {json.dumps(reponse, ensure_ascii=False)[:500]}")


@mcp.tool()
def codemagic_etat_build(build_id: str) -> str:
    """État d'un build Codemagic : statut, commit, étapes (Analyze, Unit tests, APK) et artefacts."""
    statut, reponse = _codemagic("GET", f"/builds/{build_id}")
    if statut != 200 or not isinstance(reponse, dict):
        return f"HTTP {statut} : {str(reponse)[:300]}"
    build = reponse.get("build", reponse)
    lignes = [f"Statut : {build.get('status')} · branche {build.get('branch')} · commit {str(build.get('commit', {}).get('hash', ''))[:8]}"]
    for etape in build.get("buildActions", []) or []:
        lignes.append(f"  - {etape.get('name')} : {etape.get('status')}")
    for artefact in build.get("artefacts", []) or []:
        lignes.append(f"  artefact {artefact.get('name')} ({artefact.get('size', '?')} o) : {artefact.get('url')}")
    return _masquer("\n".join(lignes))


@mcp.tool()
def codemagic_annuler_build(build_id: str, confirmer: bool = False) -> str:
    """Annule un build Codemagic en cours (confirmer=True)."""
    if not confirmer:
        return "Action non lancée : confirmer=True requis."
    statut, reponse = _codemagic("POST", f"/builds/{build_id}/cancel")
    return f"HTTP {statut} : {str(reponse)[:200]}"


# ---------------------------------------------------------------- base de données (lecture seule)

LECTURE = re.compile(r"^\s*(select|with|explain|show)\b", re.I)
INTERDIT = re.compile(r"\b(insert|update|delete|drop|alter|truncate|create|grant|revoke|copy|call|do|vacuum)\b", re.I)


@mcp.tool()
def db_lecture(base: str, sql: str, max_lignes: int = 200) -> str:
    """Requête SQL en LECTURE SEULE sur une base du kit (conteneur sirh-postgres-1). Bases : authentication_db,
    referentiel_rh_db, evaluation_rh_db, presence_db, notification_db. Seuls SELECT / WITH / EXPLAIN / SHOW passent."""
    if base not in BASES:
        return f"Base inconnue. Valeurs : {', '.join(BASES)}"
    if not LECTURE.match(sql) or INTERDIT.search(sql) or ";" in sql.strip().rstrip(";"):
        return "Refusé : une seule requête de lecture (SELECT / WITH / EXPLAIN / SHOW)."
    script = f"BEGIN TRANSACTION READ ONLY;\n{sql.strip().rstrip(';')};\nROLLBACK;\n"
    code, sortie = _lancer(["docker", "exec", "-i", "sirh-postgres-1", "psql", "-U", "postgres", "-d", base,
                            "-v", "ON_ERROR_STOP=1", "-P", "pager=off", "-c", script], delai=60)
    lignes = sortie.splitlines()
    return "\n".join(lignes[: max(5, min(max_lignes, 1000))]) or f"(vide, code {code})"


# ---------------------------------------------------------------- graphify

def _graphify(*arguments: str) -> str:
    code, sortie = _lancer(["graphify", *arguments], delai=120)
    return sortie[-15000:] if sortie else f"(rien, code {code})"


@mcp.tool()
def graphify_query(question: str) -> str:
    """Interroge le graphe de connaissance du code (graphify-out/graph.json) : fichiers et relations utiles à une question.
    À utiliser AVANT tout grep ou lecture de fichier source."""
    return _graphify("query", question)


@mcp.tool()
def graphify_explain(concept: str) -> str:
    """Explication ciblée d'un concept / d'une classe du code à partir du graphe."""
    return _graphify("explain", concept)


@mcp.tool()
def graphify_path(depart: str, arrivee: str) -> str:
    """Chemin de dépendances entre deux éléments du code (classes, fichiers, concepts)."""
    return _graphify("path", depart, arrivee)


@mcp.tool()
def graphify_mettre_a_jour() -> str:
    """Met le graphe à jour après une modification de code (analyse AST, sans coût d'API)."""
    return _graphify("update", ".")


if __name__ == "__main__":
    mcp.run()
