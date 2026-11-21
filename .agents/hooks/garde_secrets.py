"""Hook PreToolUse d'Antigravity : refuse l'accès aux secrets et les commandes Git dangereuses.

Entrée : JSON (stdin) décrivant l'appel d'outil. Le schéma exact peut évoluer : on parcourt donc toutes les chaînes
courtes de l'événement (commande, chemin…), en ignorant les longs contenus (texte d'un fichier écrit).
Sortie : `{"decision": "deny", "reason": "..."}` pour refuser, `{}` sinon. En cas d'erreur : `{}` (ne bloque rien).
Le pre-commit `tools/git-hooks/pre-commit` reste la garantie finale côté Git.
"""
import json
import re
import sys

SECRETS = [
    re.compile(r"gittoken\.txt", re.I),
    re.compile(r"recette_pw\.txt", re.I),
    re.compile(r"deploy[\\/]+\.env(?![\w.])", re.I),
]
AFFICHAGE_VARIABLE = re.compile(
    r"(echo|print|Write-(Host|Output)|cat|type|printenv|Get-ChildItem\s+env:|\bset\b|\benv\b)[^\n]*"
    r"(CODEMAGIC_TOKEN|DEMO_PW)", re.I)
GIT_INTERDIT = [
    (re.compile(r"git\s+add\b[^\n;&|]*(recette|deploy[\\/]+\.env(?![\w.])|deploy[\\/]+donnees|gittoken)", re.I),
     "ces chemins ne se versionnent jamais (recette/, deploy/.env, deploy/donnees/, gittoken.txt)"),
    (re.compile(r"git\s+add\s+(-A|--all|\.)(\s|$)", re.I), "ajoute les fichiers un par un (git add <fichier>)"),
    (re.compile(r"git\s+push\b[^\n;&|]*(--force(?!-with-lease)|\s-f\b)", re.I), "push forcé interdit"),
    (re.compile(r"--no-verify\b", re.I), "les vérifications Git ne se contournent pas"),
    (re.compile(r"git\s+reset\s+--hard", re.I), "git reset --hard perd du travail : demande à l'utilisateur"),
    (re.compile(r"git\s+(clean\s+-[a-z]*f|checkout\s+--\s+\.)", re.I), "suppression de travail non committé : demande à l'utilisateur"),
]
ECRITURE_OBSOLETE = re.compile(r"rh_mobile_app_temp", re.I)
TAILLE_MAX = 4000  # au-delà : contenu de fichier, pas une commande ni un chemin


def chaines(valeur, cle=""):
    if isinstance(valeur, dict):
        for k, v in valeur.items():
            yield from chaines(v, str(k))
    elif isinstance(valeur, list):
        for v in valeur:
            yield from chaines(v, cle)
    elif isinstance(valeur, str) and len(valeur) <= TAILLE_MAX:
        yield cle.lower(), valeur


def appel_outil(evenement: dict):
    """Partie de l'événement qui décrit l'appel d'outil. Antigravity l'envoie dans `toolCall` ; le reste
    (dernier message de l'utilisateur, transcription…) ne doit jamais être analysé."""
    for cle in ("toolCall", "tool_call", "toolInput", "tool_input"):
        if isinstance(evenement.get(cle), dict):
            return evenement[cle]
    return {}


def nom_outil(evenement: dict) -> str:
    appel = appel_outil(evenement)
    return str(evenement.get("toolName") or evenement.get("tool_name") or appel.get("name")
               or appel.get("toolName") or appel.get("tool") or "").lower()


def verdict(evenement: dict) -> str | None:
    outil = nom_outil(evenement)
    cible = appel_outil(evenement)
    if "toolName" in evenement or "tool_name" in evenement:
        cible = {"outil": outil, "entree": cible}
    for cle, texte in chaines(cible):
        if cle in ("content", "contents", "code", "text", "newstring", "new_string", "replacement", "instruction",
                   "description", "codecontent", "replacementcontent", "targetcontent"):
            continue
        for motif in SECRETS:
            if motif.search(texte):
                return "fichier secret : ni lecture, ni écriture, ni copie (les scripts et le serveur MCP sirh le lisent eux-mêmes)"
        if AFFICHAGE_VARIABLE.search(texte):
            return "CODEMAGIC_TOKEN / DEMO_PW ne s'affichent jamais (outils MCP codemagic_* et kit_sante)"
        for motif, raison in GIT_INTERDIT:
            if motif.search(texte):
                return raison
        if ECRITURE_OBSOLETE.search(texte) and any(m in outil for m in ("write", "edit", "replace", "create")):
            return "rh_mobile_app_temp est obsolète : travaille dans rh_mobile_app"
    return None


def main() -> None:
    evenement = {}
    try:
        sys.stdout.reconfigure(encoding="utf-8")
        evenement = json.loads(sys.stdin.read() or "{}")
        raison = verdict(evenement) if isinstance(evenement, dict) else None
    except Exception:  # un hook défaillant ne doit jamais bloquer le travail
        raison = None
    reponse = ({"decision": "deny", "reason": "Bloqué par .agents/hooks/garde_secrets.py : " + raison}
               if raison else {"decision": "allow"})
    journaliser(evenement, reponse["decision"])
    print(json.dumps(reponse, ensure_ascii=False))


def journaliser(evenement, decision: str) -> None:
    """Diagnostic : outil, noms des champs reçus et décision (jamais les valeurs)."""
    try:
        import os
        import tempfile
        import time
        dico = evenement if isinstance(evenement, dict) else {}
        outil = nom_outil(dico) or "?"
        champs_appel = sorted(appel_outil(dico))
        with open(os.path.join(tempfile.gettempdir(), "sirh-hook.log"), "a", encoding="utf-8") as f:
            f.write(f"{time.strftime('%H:%M:%S')} outil={outil} champs_appel={champs_appel} decision={decision}\n")
    except Exception:
        pass


if __name__ == "__main__":
    main()
