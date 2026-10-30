"""Hook PreToolUse : bloque l'accès aux secrets et les commandes Git dangereuses.

Code de sortie 2 = action refusée, le message (stderr) est renvoyé à Claude.
"""
import json
import re
import sys

# Fichiers secrets : jamais lus, affichés, copiés ni modifiés par un agent.
SECRETS = [
    re.compile(r"gittoken\.txt", re.I),
    re.compile(r"recette_pw\.txt", re.I),
    re.compile(r"deploy[\\/]+\.env(?![\w.])", re.I),  # deploy/.env, pas deploy/.env.example
]
VARIABLES_SECRETES = re.compile(r"\$\{?CODEMAGIC_TOKEN|\$env:CODEMAGIC_TOKEN|%CODEMAGIC_TOKEN%", re.I)
# Utilisation légitime de la variable : passée en en-tête à l'API Codemagic, sans l'afficher.
USAGE_CODEMAGIC = re.compile(r"x-auth-token:\s*\$\{?CODEMAGIC_TOKEN", re.I)

GIT_INTERDIT = [
    (re.compile(r"git\s+add\b[^\n;&|]*(recette|deploy[\\/]+\.env(?![\w.])|deploy[\\/]+donnees|gittoken)", re.I),
     "ces chemins ne doivent jamais être versionnés (recette/, deploy/.env, deploy/donnees/, gittoken.txt)"),
    (re.compile(r"git\s+add\s+(-A|--all|\.)(\s|$)", re.I),
     "ajoute les fichiers un par un (git add <fichier>) pour ne pas embarquer de secret"),
    (re.compile(r"git\s+push\b[^\n;&|]*(--force(?!-with-lease)|\s-f\b)", re.I),
     "push forcé interdit sur les dépôts partagés"),
    (re.compile(r"--no-verify\b", re.I), "les vérifications Git ne se contournent pas"),
    (re.compile(r"git\s+reset\s+--hard", re.I), "git reset --hard perd du travail : demande à l'utilisateur"),
]

DOSSIER_OBSOLETE = re.compile(r"rh_mobile_app_temp", re.I)


def refuser(message: str) -> None:
    print(f"Bloqué par .claude/hooks/proteger_secrets.py : {message}", file=sys.stderr)
    sys.exit(2)


def main() -> None:
    for flux in (sys.stdin, sys.stdout, sys.stderr):
        flux.reconfigure(encoding="utf-8")  # Windows : cp1252 par défaut
    try:
        evenement = json.load(sys.stdin)
    except json.JSONDecodeError:
        return
    outil = evenement.get("tool_name", "")
    entree = evenement.get("tool_input", {}) or {}

    if outil in ("Bash", "PowerShell"):
        commande = entree.get("command", "")
        for motif in SECRETS:
            if motif.search(commande):
                refuser("cette commande touche un fichier secret ; les scripts de recette le lisent eux-mêmes.")
        if VARIABLES_SECRETES.search(commande) and not USAGE_CODEMAGIC.search(commande):
            refuser("CODEMAGIC_TOKEN ne s'utilise qu'en en-tête x-auth-token, jamais affiché.")
        for motif, raison in GIT_INTERDIT:
            if motif.search(commande):
                refuser(raison)
        return

    cibles = [str(entree.get(cle, "")) for cle in ("file_path", "path", "pattern", "glob", "notebook_path")]
    texte = " ".join(cibles)
    for motif in SECRETS:
        if motif.search(texte):
            refuser("fichier secret : ni lecture ni écriture par un agent.")
    if outil in ("Edit", "Write", "NotebookEdit") and DOSSIER_OBSOLETE.search(texte):
        refuser("rh_mobile_app_temp est obsolète : travaille dans rh_mobile_app.")


if __name__ == "__main__":
    main()
