---
paths:
  - "**/src/test/**"
  - "rh_mobile_app/test/**"
  - "recette/**"
---

# Tests et recette

- Un test par règle du ticket, nommé en français d'après la règle (`refuseSortieDePlusDeDeuxHeures`).
- Backend : JUnit 5 + Mockito pour le service, `@WebMvcTest` + JWT simulé pour le RBAC (200 / 403, autre collaborateur).
- Pas de test qui dépend de l'heure réelle : injecter un `Clock` ou passer l'instant en paramètre.
- Ne jamais désactiver (`@Disabled`, `skip`) un test pour faire passer une build ; seul échec ancien toléré :
  `EvaluationTemplateServiceTest.listerTemplatesRetourneListe`.
- Recette : scripts dans `recette/outils/` (`seed_lib.py`, `stomp_listen.py`), résultats et captures dans `recette/`
  (hors Git). Le mot de passe de recette est lu depuis `recette/outils/recette_pw.txt` par les scripts, jamais écrit.
- Un critère non exécuté s'écrit « non testé (raison) », jamais « OK ».
