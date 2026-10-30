---
paths:
  - "svc-*/**/*.java"
  - "gateway/**"
  - "eureka-server/**"
  - "**/application*.properties"
  - "**/application*.yml"
  - "pom.xml"
  - "**/pom.xml"
---

# Backend Java / Spring Boot 4

## Code propre, SOLID, sécurisé
- Couches : `web` (contrôleur mince, pas de logique) → `service` (`@Transactional`, règles) → `repository`.
  Une classe = une responsabilité ; un service qui dépasse ~400 lignes se découpe (ex. `CampagneCalendrier`).
- Dépendances injectées par constructeur (`final`), interfaces pour les clients externes (`ReferentielClient`).
- DTO en `record`, validés (`@Valid`, `@NotNull`, `@Size`…) ; jamais d'entité JPA dans une réponse.
- Erreurs : exceptions métier traduites par le `@ControllerAdvice` du service ; 400 entrée invalide, 403 droits,
  404 absent, 409 conflit d'état, 422 règle métier.
- Nommage métier en français, comme le code voisin. Constantes nommées pour les seuils (`DUREE_MAX_SORTIE_MINUTES`).

## Pièges connus du projet
- `ddl-auto=update` : un enum stocké qui gagne une valeur exige un `ApplicationRunner` qui recrée la contrainte
  CHECK (modèle : `PointageStatutConstraintRunner`, `CampagneTypeConstraintRunner`).
- Une colonne ajoutée à une table existante doit être nullable ou avoir une valeur par défaut.
- Kafka : Boot 4 n'auto-configure pas `KafkaTemplate` ; déclarer le producteur (`Kafka*Config`) et envoyer du JSON
  texte (`KafkaTemplate<String, String>`). Topic `rh.notifications`.
- Notifications adressées au **compte** (`compte_utilisateur_id`), repli sur la fiche ; destinataire `RH` = tous les RH.
- Lecture paresseuse hors transaction (scheduler) : `LEFT JOIN FETCH` dans la requête.
- Scheduler : cron en propriété (`${...:valeur}`), zone `Africa/Tunis`, jamais d'UUID ni de `fixedRate` de test.
- Nouvel endpoint public : route dans `gateway/src/main/resources/application.yml`.
- Cache Caffeine du référentiel : redémarrer après une modification directe en base.

## Vérifier
`export JAVA_HOME="/c/Program Files/Java/jdk-21"` puis `mvn -B -q -pl <module> -am test`.
Arrêter le service avant de reconstruire le jar (fichier verrouillé).
