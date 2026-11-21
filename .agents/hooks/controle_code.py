"""Contrôles qualité rapides sur un fichier modifié (Java, Dart, tout fichier texte).

Deux usages :
- hook PostToolUse d'Antigravity : lit l'événement JSON (stdin), contrôle les fichiers cités et renvoie
  `{"reason": ..., "additionalContext": ...}` si un problème est trouvé (sinon `{}`) ;
- module importé par `tools/git-hooks/pre-commit` (fonction `constats(chemin, texte)`).
"""
import json
import re
import sys
from pathlib import Path

UUID = re.compile(r"\"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\"", re.I)
SECRET_EN_DUR = re.compile(
    r"(password|passwd|secret|token|api[_-]?key)\s*[:=]\s*\"(?![^\"]*(change-me|dev-only|a-generer))[^\"$<{]{6,}\"", re.I)
JETONS_CONNUS = re.compile(r"(ghp_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{40,}|AKIA[0-9A-Z]{16}|AIza[0-9A-Za-z_-]{35}"
                           r"|xox[abp]-[A-Za-z0-9-]{20,}|-----BEGIN (RSA |EC )?PRIVATE KEY-----)")
MAPPING = re.compile(r"@(Get|Post|Put|Patch|Delete|Request)Mapping")
TUNNEL = re.compile(r"https://[a-z0-9]+(-[a-z0-9]+)+\.trycloudflare\.com")
FLUTTER_RECENT = re.compile(r"DropdownButtonFormField[\s\S]{0,400}?\binitialValue\s*:")
IGNORES = ("recette", ".claude", ".agents", "node_modules", "build", "target", "graphify-out")


def constats(chemin: Path, texte: str) -> list[str]:
    if any(p in IGNORES for p in chemin.parts):
        return []
    resultat = []
    if TUNNEL.search(texte):
        resultat.append("adresse de tunnel Cloudflare en dur : elle change à chaque relance")
    if JETONS_CONNUS.search(texte):
        resultat.append("jeton ou clé privée reconnaissable dans le fichier")
    if chemin.suffix == ".java" and "/src/test/" not in chemin.as_posix():
        if UUID.search(texte):
            resultat.append("UUID écrit en dur : configuration ou base")
        if SECRET_EN_DUR.search(texte):
            resultat.append("secret apparemment écrit en dur : propriété + variable d'environnement")
        if ("@RestController" in texte and MAPPING.search(texte) and "@PreAuthorize" not in texte
                and not any(m in chemin.name for m in ("Intern", "Borne", "Auth"))):
            resultat.append("contrôleur REST sans @PreAuthorize : chaque endpoint déclare ses rôles")
    if chemin.suffix == ".dart" and FLUTTER_RECENT.search(texte):
        resultat.append("DropdownButtonFormField(initialValue:) exige Flutter 3.33+ ; Codemagic est en 3.29.2 → value:")
    return resultat


def chemins_cites(valeur):
    if isinstance(valeur, dict):
        for v in valeur.values():
            yield from chemins_cites(v)
    elif isinstance(valeur, list):
        for v in valeur:
            yield from chemins_cites(v)
    elif isinstance(valeur, str) and len(valeur) < 400 and re.search(r"\.(java|dart|ts|tsx|yml|yaml|properties|md|json)$", valeur):
        yield valeur


def main() -> None:
    sortie = {}
    try:
        sys.stdout.reconfigure(encoding="utf-8")
        evenement = json.loads(sys.stdin.read() or "{}")
        messages = []
        for brut in set(chemins_cites(evenement)):
            chemin = Path(brut)
            if chemin.is_file():
                for c in constats(chemin, chemin.read_text(encoding="utf-8", errors="replace")):
                    messages.append(f"{chemin.name} : {c}")
        if messages:
            texte = "Contrôle qualité (.agents/hooks/controle_code.py), à corriger maintenant :\n- " + "\n- ".join(messages)
            sortie = {"reason": texte, "additionalContext": texte}
    except Exception:
        sortie = {}
    print(json.dumps(sortie, ensure_ascii=False))


if __name__ == "__main__":
    main()
