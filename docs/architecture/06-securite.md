# Architecture — Sécurité

**Rôle** : architecte sécurité  
**Périmètre** : microservices RH-Évènement, mobile et web.

---

## 1. Modèle de menaces (rappel)

| Menace | Mitigation architecturale |
|--------|---------------------------|
| Usurpation | JWT signés, rotation clés, durée de vie courte, refresh sécurisé |
| Élévation de privilège | RBAC par service ; validation côté serveur systématique |
| Fuite de données | TLS partout, chiffrement au repos, cloisonnement DB |
| Injection | Validation entrées, requêtes paramétrées, pas de SQL dynamique non contrôlé |
| Abus API | Rate limiting gateway, WAF optionnel |
| Fichiers malveillants | Scan antivirus sur `svc-fichiers`, types MIME contrôlés, taille max |

---

## 2. Authentification (AuthN)

| Composant | Rôle |
|-----------|------|
| **svc-identite-acces** | Point d’émission des jetons ; aligné OAuth2 / OIDC (JWKS public) |
| **Clients** | Mobile / Web : flux mot de passe (phase 1) ou authorization code + PKCE (recommandé long terme) |

| Jeton | Usage |
|-------|--------|
| **Access token** | Bearer API, courte durée |
| **Refresh token** | Stockage sécurisé côté client ; rotation (à implémenter si absent) |

---

## 3. Autorisation (AuthZ)

| Niveau | Mécanisme |
|--------|-----------|
| **Gateway** | Filtrage grossier (JWT valide, audience) |
| **Service** | `@PreAuthorize`, policies par ressource (ex. propriétaire = `sub`) |
| **Données** | Filtre par `employe_id` / service pour éviter IDOR |

**Mapping conception → technique** : profils métier (`RH`, `CHEF_SERVICE`, …) portés en claims (`roles` ou `scope`) de manière cohérente sur tous les resource servers.

---

## 4. Données sensibles

| Donnée | Mesure |
|--------|--------|
| Mots de passe | Hash fort (bcrypt, argon2) — jamais en clair |
| JWT | Ne pas stocker secrets dans le payload ; HTTPS obligatoire |
| Plaintes | Accès journalisé ; visibilité par rôle strictement |
| Présence | Localisation **uniquement** au moment du scan (principe CDC) ; politique de rétention |

---

## 5. Réseau

| Zone | Règle |
|------|--------|
| **DMZ** | Gateway exposée |
| **App** | Services sans IP publique ; mTLS mesh optionnel (Istio, Linkerd) |
| **Data** | Bases accessibles uniquement depuis namespaces services |

---

## 6. Conformité

| Référence | Application |
|-----------|-------------|
| **RGPD** | Registre traitements, DPA, droit accès/effacement sur données RH |
| **Loi travail locale** | Conservation pointage, preuves des demandes |

---

## 7. Gestion des incidents

| Élément | Pratique |
|---------|----------|
| Journal d’audit | Qui a validé / consulté quoi (horodatage, IP si pertinent) |
| Réponse | Runbooks révocation JWT (blacklist courte durée ou rotation clé) |

---

## 8. Alignement implémentation actuelle

| Élément | Projet `authenctication` |
|---------|--------------------------|
| JWKS | Doit rester **public** pour les resource servers (ex. `humain_resource`) |
| CORS | À configurer en prod pour origines web/mobile connues |

| Élément | Projet `humain_resource` |
|---------|--------------------------|
| Resource Server | `issuer-uri` + `jwk-set-uri` vers le service identité |

---

*Réviser ce document après audit sécurité externe ou changement réglementaire.*
