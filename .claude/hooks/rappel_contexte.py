"""Hook Stop : rappelle de tenir CONTEXTE.md à jour après un commit (règle de CLAUDE.md).

Si le dernier commit a moins de 2 heures et ne modifie pas CONTEXTE.md, Claude est relancé une seule fois
avec un rappel (pas de boucle : on s'arrête si stop_hook_active est vrai).
"""
import json
import subprocess
import sys
import time

FRAICHEUR_SECONDES = 2 * 3600


def git(*arguments: str) -> str:
    resultat = subprocess.run(["git", *arguments], capture_output=True, text=True, encoding="utf-8")
    return resultat.stdout.strip() if resultat.returncode == 0 else ""


def main() -> None:
    for flux in (sys.stdin, sys.stdout, sys.stderr):
        flux.reconfigure(encoding="utf-8")  # Windows : cp1252 par défaut
    try:
        evenement = json.load(sys.stdin)
    except json.JSONDecodeError:
        evenement = {}
    if evenement.get("stop_hook_active"):
        return

    horodatage = git("log", "-1", "--format=%ct")
    if not horodatage or time.time() - int(horodatage) > FRAICHEUR_SECONDES:
        return
    fichiers = git("show", "--name-only", "--format=", "HEAD").splitlines()
    if "CONTEXTE.md" in fichiers:
        return
    if "CONTEXTE.md" in git("status", "--porcelain", "CONTEXTE.md"):
        return  # déjà en cours de mise à jour

    print(json.dumps({
        "decision": "block",
        "reason": "Le dernier commit ne met pas à jour CONTEXTE.md. Si c'est un lot livré, ajoute la ligne "
                  "du Journal (avec le hash) et mets à jour « Reste à faire » et la date, puis propose à "
                  "l'utilisateur de le committer. Sinon, ignore ce rappel.",
    }, ensure_ascii=False))


if __name__ == "__main__":
    main()
