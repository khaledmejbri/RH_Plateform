"""Hook PostToolUse (Edit / Write) : contrôles rapides sur le fichier qui vient d'être modifié.

Ne bloque pas l'écriture (elle a déjà eu lieu) : en cas de problème, sortie 2 et message renvoyé à Claude
pour qu'il corrige tout de suite.
"""
import json
import re
import sys
from pathlib import Path

UUID = re.compile(r"\"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\"", re.I)
SECRET_EN_DUR = re.compile(
    r"(password|passwd|secret|token|api[_-]?key)\s*[:=]\s*\"(?![^\"]*(change-me|dev-only|a-generer))[^\"$<{]{6,}\"", re.I)
MAPPING = re.compile(r"@(Get|Post|Put|Patch|Delete|Request)Mapping")
API_FLUTTER_RECENTES = [
    (re.compile(r"DropdownButtonFormField[\s\S]{0,400}?\binitialValue\s*:"),
     "DropdownButtonFormField(initialValue:) exige Flutter 3.33+ ; Codemagic est en 3.29.2 → utiliser value:"),
    (re.compile(r"\.withValues\s*\(\s*alpha"), None),  # disponible en 3.27+, toléré
]


def problemes_java(chemin: Path, texte: str) -> list[str]:
    if "/src/test/" in chemin.as_posix():
        return []
    constats = []
    if UUID.search(texte):
        constats.append("UUID écrit en dur : le passer en configuration ou le lire en base.")
    if SECRET_EN_DUR.search(texte):
        constats.append("secret apparemment écrit en dur : utiliser une propriété + variable d'environnement.")
    if "@RestController" in texte and MAPPING.search(texte) and "@PreAuthorize" not in texte \
            and "Intern" not in chemin.name and "Borne" not in chemin.name and "Auth" not in chemin.name:
        constats.append("contrôleur REST sans @PreAuthorize : chaque endpoint doit déclarer ses rôles.")
    return constats


def problemes_dart(texte: str) -> list[str]:
    return [message for motif, message in API_FLUTTER_RECENTES if message and motif.search(texte)]


def problemes_communs(texte: str) -> list[str]:
    constats = []
    if re.search(r"https://[a-z0-9]+(-[a-z0-9]+)+\.trycloudflare\.com", texte):
        constats.append("adresse de tunnel Cloudflare écrite en dur : elle change à chaque relance.")
    return constats


def main() -> None:
    for flux in (sys.stdin, sys.stdout, sys.stderr):
        flux.reconfigure(encoding="utf-8")  # Windows : cp1252 par défaut
    try:
        evenement = json.load(sys.stdin)
    except json.JSONDecodeError:
        return
    entree = evenement.get("tool_input", {}) or {}
    chemin = Path(entree.get("file_path", ""))
    if not chemin.is_file() or "recette" in chemin.parts or ".claude" in chemin.parts:
        return
    try:
        texte = chemin.read_text(encoding="utf-8")
    except (OSError, UnicodeDecodeError):
        return

    constats = problemes_communs(texte)
    if chemin.suffix == ".java":
        constats += problemes_java(chemin, texte)
    elif chemin.suffix == ".dart":
        constats += problemes_dart(texte)

    if constats:
        print(f"Contrôle qualité ({chemin.name}) :\n- " + "\n- ".join(constats), file=sys.stderr)
        sys.exit(2)


if __name__ == "__main__":
    main()
