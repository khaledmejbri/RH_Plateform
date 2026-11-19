# ADR-IMPORT-CHAMPS — Champs de l'import Excel paramétrables (D-I14 à D-I26, D-R11)

| | |
|---|---|
| **Statut** | proposé (13/11/2026) |
| **Date** | 2026-11-13 |
| **Module** | M00 / M01 — import Excel (Plateforme RH), écran Paramètres |
| **Tickets** | Partie 1 : T1 (API Paramètres), T2 (application à l'import), T3 (web Paramètres), T4 (web assistant). Partie 2 (champs personnalisés, D-I27 à D-I41) : T5 à T12 — fiche `docs/jira/fiche_import_champs_parametrables.md` |
| **Références** | `docs/sirh_basefonctionnelle.md` § 5.5 bis (D-I14 à D-I26), § 5.5 (D-I03 à D-I13), § 3.5 (D-R01, D-R02, D-R10, D-R11), C-IMP-02, C-UX-01, C-UX-04 ; `ADR-soldes-conge-import.md` (import livré fdb20cf + 3824068) ; `ADR-UAT-lot3-parametrage.md` (cadre Paramètres) |

---

## Contexte

L'import livré (fdb20cf) porte ses champs **en dur** dans `importation/ChampImport.java` : deux enums `Fiche` (14
champs) et `Solde` (3 champs), chacun avec libellé, booléen « obligatoire à la création » et synonymes. Relevé dans le
code (13/11/2026) :

- `ReconnaissanceEnTetes(List<? extends ChampImport>)` construit une table `forme normalisée → champ` depuis les
  synonymes de l'enum ; `normaliser()` (statique) applique D-I03. Le libellé n'est reconnu que parce qu'il est aussi
  dans les synonymes (vrai pour les 17 champs).
- `ImportService` instancie `ReconnaissanceEnTetes` depuis les enums à **quatre** endroits : `evaluer` (détection de
  la ligne d'en-tête et correspondance automatique), `erreursFichier` (message `COLONNE_EN_DOUBLE`), `versAnalyse`
  (`colonnes[].champ_libelle`, `champs_disponibles`), et indirectement `modeleCollaborateurs` / `modeleSoldes`
  (`FichiersImportXlsx.modele(champs)` = libellés de tous les champs, ordre de l'enum).
- L'analyse garde son résultat dans `rh_import_contenu.resultat` (record `ResultatStocke`, JSON gzip), relu par
  `corriger` (I5), `confirmer` (I6, réanalyse `reevaluerFiches` / `reevaluerSoldes` qui **réutilise la correspondance et
  les erreurs de fichier stockées**) et `fichierErreurs` (I7).
- Exigences en dur : `ImportCollaborateursService.evaluerLigne` boucle sur `List.of(PRENOM, NOM, UNITE)` pour une ligne
  Création (`CHAMP_OBLIGATOIRE`, message « Champ obligatoire : Prénom. ») ; `COURRIEL_OBLIGATOIRE` si « Créer les
  comptes » ; `ImportSoldesService` : `MATRICULE_OBLIGATOIRE`, `SOLDE_OBLIGATOIRE`, motif facultatif avec repli sur le
  motif global ; `ImportService.erreursFichier` : `COLONNE_MATRICULE_ABSENTE`, `COLONNE_SOLDE_ABSENTE`.
- Cadre Paramètres du référentiel (lot 3) : `ParametreController` sous `/api/referentiel/v1/parametres`
  (`BACKOFFICE_LECTURE` / `BACKOFFICE_ECRITURE`), `ParametresCache` (Caffeine 30 s, `app.parametres.cache-ttl-secondes`,
  invalidation après commit), `ParametresAmorceRunner` (`SmartInitializingSingleton`, verrou consultatif, ne réécrit
  jamais une ligne existante), `AuteurParametreResolver` (auteur tiré du JWT), journal `rh_parametre_journal`
  (`action` en `@Enumerated` sur 7 valeurs, `avant_json` / `apres_json` au format des valeurs de référentiel).
- Gateway : `/api/referentiel/**` et `/api/rh/v1/**` déjà routés vers `svc-referentiel-rh` ; aucune route à ajouter.

Besoin tranché (utilisateur, 13/11/2026) : pour chaque type d'import, la RH règle (1) colonnes du modèle et ordre,
(2) exigence (matricule toujours obligatoire), (3) synonymes, (4) en-têtes du modèle. Le reste = D-I14 à D-I26 (choix par
défaut de l'expert, à confirmer client).

## Décision

### 1. Tout dans `svc-referentiel-rh`, sans Kafka ni appel entre services

Le paramétrage ne sert qu'à l'import, qui vit dans le référentiel. Aucun événement, aucun `/internal`. Base
`referentiel_rh_db`. Pas de mobile.

### 2. Le code garde la liste fermée et les règles ; la base garde les réglages

`ChampImport` reste la **source des champs** (codes de comportement, D-I14) et de leurs **valeurs par défaut et
verrous**. Il gagne (et perd `obligatoireCreation()`, remplacé) :

```java
/** Niveau d'exigence d'un champ d'import (D-I15). Ordre = force croissante. */
public enum ExigenceChamp {
    FACULTATIF("Facultatif"), CREATION("Obligatoire à la création"), TOUJOURS("Toujours obligatoire");
    // libelle(), auMoins(ExigenceChamp autre)
}

public interface ChampImport {
    String name();
    String libelle();                          // nom du champ de la fiche = en-tête par défaut (inchangé)
    List<String> synonymes();                  // synonymes PAR DÉFAUT (amorçage seulement)
    ExigenceChamp exigenceDefaut();
    ExigenceChamp exigenceMinimale();          // verrou D-I16
    /** Libellé du cadenas affiché à l'écran ; null si le champ n'est pas verrouillé. */
    String verrouLibelle();
    default boolean retirableDuModele() { return exigenceMinimale() == ExigenceChamp.FACULTATIF; }
}
```

| Type | Champ | Défaut | Minimum | Exigences possibles | Cadenas (`verrou_libelle`) |
|---|---|---|---|---|---|
| collaborateurs | MATRICULE | TOUJOURS | TOUJOURS | TOUJOURS | « Toujours obligatoire : sert à retrouver la fiche » |
| collaborateurs | PRENOM, NOM, UNITE | CREATION | CREATION | CREATION, TOUJOURS | « Exigé pour créer une fiche » |
| collaborateurs | 10 autres (COURRIEL compris) | FACULTATIF | FACULTATIF | FACULTATIF, CREATION, TOUJOURS | — |
| soldes | MATRICULE | TOUJOURS | TOUJOURS | TOUJOURS | « Toujours obligatoire : sert à retrouver la fiche » |
| soldes | SOLDE | TOUJOURS | TOUJOURS | TOUJOURS | « Exigé pour chaque ligne » |
| soldes | MOTIF | FACULTATIF | FACULTATIF | FACULTATIF, TOUJOURS | — |

Exigences autorisées par type : collaborateurs = les trois ; soldes = `FACULTATIF`, `TOUJOURS` (D-I15).
« Exigences possibles » d'un champ = exigences autorisées par le type, **au moins** égales au minimum.

Un enum `TypeImportParametrable` fait le lien chemin ↔ type ↔ champs :
`COLLABORATEURS("collaborateurs", "Collaborateurs", ChampImport.Fiche.values())`,
`SOLDES("soldes", "Soldes de congé", ChampImport.Solde.values())` ; `name()` = valeur de `rh_import.type_import`
(`ImportAnalyse.TYPE_COLLABORATEURS` / `TYPE_SOLDES`). Chemin inconnu → 404 `TYPE_IMPORT_INCONNU`.

### 3. Modèle de données (`ddl-auto=update`, nouvelles tables seulement)

Quatre tables neuves (créées par Hibernate), une colonne nullable ajoutée à `rh_import`. **Aucun `@Enumerated`** :
`exigence` et `origine` sont des `varchar` avec un `AttributeConverter` (`ExigenceChampConverter`,
`OrigineSynonymeConverter`), donc Hibernate ne génère **pas** de contrainte CHECK et aucun runner de CHECK n'est
nécessaire (même choix que le lot 3 : colonnes varchar, contrôle applicatif). Une valeur inconnue lue en base
(impossible hors SQL manuel) est traitée comme le défaut du champ, avec un WARN.

```
rh_import_parametrage                        -- une ligne par type d'import : porte la VERSION (D-I23)
  type_import        varchar(20)  PK         -- COLLABORATEURS | SOLDES
  version            bigint       NOT NULL   -- @Version, 0 à l'amorçage
  modifie_le         timestamptz  NULL
  modifie_par_nom    varchar(160) NULL

rh_import_champ                              -- réglage d'un champ
  id                 uuid         PK
  type_import        varchar(20)  NOT NULL
  champ_code         varchar(40)  NOT NULL   -- name() de ChampImport.Fiche / Solde
  en_tete            varchar(80)  NOT NULL   -- texte saisi (affiché, écrit dans le modèle)
  dans_modele        boolean      NOT NULL
  exigence           varchar(16)  NOT NULL   -- FACULTATIF | CREATION | TOUJOURS (converter)
  ordre              integer      NOT NULL   -- 0..n-1, dense
  UNIQUE (type_import, champ_code)           -- uk_rh_import_champ_type_code

rh_import_synonyme
  id                 uuid         PK
  type_import        varchar(20)  NOT NULL
  champ_code         varchar(40)  NOT NULL
  texte              varchar(80)  NOT NULL   -- texte saisi, conservé pour l'affichage
  forme_normalisee   varchar(80)  NOT NULL   -- ReconnaissanceEnTetes.normaliser(texte)
  origine            varchar(8)   NOT NULL   -- DEFAUT | RH (converter)
  actif              boolean      NOT NULL
  cree_le            timestamptz  NOT NULL
  cree_par_nom       varchar(160) NULL       -- null pour DEFAUT ; auteur (JWT) de P3, ou de P2 pour un ancien en-tête
  UNIQUE (type_import, champ_code, forme_normalisee)   -- uk_rh_import_synonyme_forme
  INDEX  (type_import, forme_normalisee)                -- idx_rh_import_synonyme_forme

rh_import_champ_journal                      -- ajout seul (@Immutable), sans purge (≥ 5 ans, C-L-02)
  id                 uuid         PK
  type_import        varchar(20)  NOT NULL
  version            bigint       NOT NULL   -- version APRÈS l'écriture (regroupe les lignes d'un même P2)
  champ_code         varchar(40)  NULL       -- null pour « Ordre » et « Réinitialisation »
  attribut           varchar(32)  NOT NULL   -- code, voir A.1 (pas de CHECK)
  avant              varchar(2000) NULL      -- texte EN CLAIR, déjà rendu (« Facultatif », « Oui »)
  apres              varchar(2000) NULL
  auteur_compte_id   uuid         NULL
  auteur_nom         varchar(160) NOT NULL
  horodatage         timestamptz  NOT NULL
  INDEX (type_import, horodatage)            -- idx_rh_import_champ_journal_type

rh_import (existante)
  + parametrage_version bigint NULL          -- version figée par l'analyse (traçabilité, D-I25)
```

Règles de données :
- **Unicité d'une forme sur un même champ, active ou non** (contrainte `uk_rh_import_synonyme_forme`) : saisir une forme
  déjà présente, même désactivée, sur le même champ → 400 `SYNONYME_EN_DOUBLE` « … déjà présent pour ce champ
  (désactivé) : réactivez-le ». Cela rend l'amorçage idempotent par simple `ON CONFLICT DO NOTHING`.
- **Unicité entre champs parmi les noms actifs** (en-têtes réglés + synonymes actifs, D-I20 / D-I21) : non exprimable
  en contrainte (deux tables, filtre `actif`) → contrôle applicatif **sous verrou** de la ligne
  `rh_import_parametrage` du type (§ 5), qui sérialise toutes les écritures d'un type.
- Les valeurs par défaut (en-tête par défaut, exigence minimale, exigences possibles, cadenas) ne sont **pas** stockées :
  lues dans `ChampImport` à chaque réponse.

### 4. Amorçage idempotent : `ImportChampsAmorceRunner` (package `config`)

`SmartInitializingSingleton`, comme `ParametresAmorceRunner` ; `JdbcTemplate` + `TransactionTemplate` ;
`pg_try_advisory_xact_lock(7_001_903_002)` (clé propre, ≠ `VERROU_AMORCAGE` du lot 3) ; une instance qui n'obtient pas
le verrou n'amorce pas (l'autre le fait). Pour chaque `TypeImportParametrable` :

1. `INSERT INTO rh_import_parametrage (type_import, version) VALUES (?, 0) ON CONFLICT DO NOTHING`.
2. Pour chaque champ du code, dans l'ordre de l'enum : s'il n'a pas de ligne `rh_import_champ`, l'insérer avec
   `en_tete = libelle()`, `dans_modele = true`, `exigence = exigenceDefaut()`, `ordre = ` (max(ordre) du type + 1, ou
   l'ordinal sur une base neuve). **Une ligne existante n'est jamais modifiée.**
3. Pour chaque synonyme par défaut du code : forme = `ReconnaissanceEnTetes.normaliser(s)` ; si `(type, champ, forme)`
   existe déjà (quelle que soit l'origine) → rien ; sinon insertion `origine = DEFAUT`, `texte = s` (texte du code,
   déjà normalisé), `actif = true`, **sauf** si la forme est déjà un nom actif d'un **autre** champ du type (en-tête ou
   synonyme actif, cas d'un synonyme ajouté au code après qu'une RH a pris ce nom) → insertion avec `actif = false` et
   un WARN (jamais de conflit créé par l'amorçage).
4. Contrôle de cohérence au démarrage (log ERROR, pas d'arrêt) : `normaliser(libelle())` figure dans les synonymes par
   défaut de chaque champ (vrai aujourd'hui pour les 17 champs), et deux champs d'un même type n'ont pas de synonyme
   par défaut de même forme (vrai aujourd'hui ; garde-fou pour une évolution du code). Un test unitaire vérifie les deux
   propriétés sur les enums.
5. Les codes de champ présents en base mais absents du code (champ retiré d'une version future) sont ignorés à la
   lecture, jamais supprimés.

L'amorçage **n'incrémente pas** la version et n'écrit pas au journal (rien de ce que la RH a réglé ne change). Il
invalide le cache en fin d'exécution.

L'amorçage reprend **la liste du code livré** (fiche, « État actuel ») et non le tableau D-I03 du doc.

### 5. Écritures : `ImportParametrageService` (verrou, version, journal)

Toutes les écritures (P2 à P6) suivent le même gabarit, dans une transaction :

1. Résoudre le type (404 `TYPE_IMPORT_INCONNU`).
2. `SELECT … FOR UPDATE` sur la ligne `rh_import_parametrage` du type (`findByIdForUpdate`,
   `@Lock(PESSIMISTIC_WRITE)`) : toutes les écritures d'un type sont sérialisées, les contrôles d'unicité portent sur un
   état stable.
3. Résoudre le champ / synonyme visé (404 `CHAMP_INCONNU`, 404 `SYNONYME_INTROUVABLE`, y compris s'il appartient à
   l'autre type), puis contrôler le corps (400), puis les règles (422) sur l'état relu sous verrou, puis **en dernier**
   comparer la `version` reçue à la version en base → 409 `PARAMETRE_MODIFIE_ENTRE_TEMPS` (ordre G2). Toute exception
   annule la transaction : rien n'est écrit.
4. Appliquer, puis `modifie_le` / `modifie_par_nom` mis à jour sur la ligne `rh_import_parametrage` (entité sale ⇒
   `@Version` incrémentée au flush) : **toute** écriture P2 à P6 incrémente la version d'exactement 1, y compris une
   écriture sur un synonyme.
5. Journal dans la même transaction (§ 7), auteur = `AuteurParametreResolver.depuis(jwt)` (jamais le corps).
6. `ParametrageImportCache.invaliderApresCommit(type)`.

Une écriture sans changement effectif (P2 identique, désactiver un synonyme déjà inactif) : 200, **pas** de version
incrémentée, **pas** de journal (mais la version reçue est quand même contrôlée).

**P2 — enregistrer le tableau** (ordre des contrôles, premier échec renvoyé, `champ` dans le corps d'erreur) :
1. 400 `CORPS_INVALIDE` : corps, `version` (→ 400 `VERSION_OBLIGATOIRE` si seule la version manque, comme le lot 3),
   `champs`, `dans_modele` ou `exigence` absents ; code de champ inconnu du type.
2. 400 `ORDRE_INCOMPLET` : un champ du type omis ou répété (la taille doit être exactement 14 / 3).
3. 400 `EXIGENCE_INVALIDE` : valeur hors `FACULTATIF | CREATION | TOUJOURS`, ou `CREATION` sur le type `soldes`.
4. 400 `LIBELLE_INVALIDE` (D-I20) : `en_tete` rogné absent, < 2 ou > 80 caractères, forme normalisée < 2 caractères, ou
   premier caractère dans `= + - @ \t \r` (contrôle sur le texte **rogné**).
5. 400 `LIBELLE_EN_DOUBLE` : deux en-têtes du corps de même forme, ou forme d'un en-tête = synonyme **actif** d'un
   autre champ ; message « « Nom » est déjà reconnu pour le champ Nom. » (nom du champ = `libelle()` du code).
6. 422 `CHAMP_VERROUILLE` : exigence < minimum, ou `dans_modele=false` sur un champ non retirable ; message qui nomme
   le champ.
7. 422 `CHAMP_OBLIGATOIRE_HORS_MODELE` : `dans_modele=false` avec exigence ≠ `FACULTATIF`.
8. 422 `SYNONYMES_MAX` : l'ancien en-tête à conserver comme synonyme ferait dépasser 30 (cf. 9).
9. 409 `PARAMETRE_MODIFIE_ENTRE_TEMPS`.

Effets : `ordre` = position dans le tableau reçu (0..n-1). **Ancien en-tête → synonyme** (D-I20, T1 règle 4) : pour
chaque champ dont l'en-tête change, forme `f` de l'ancien en-tête ; si un synonyme actif de ce champ a la forme `f`, rien ;
s'il en existe un **inactif** de forme `f`, il est réactivé (journal « Synonyme réactivé ») ; sinon insertion
`origine = RH`, `texte` = ancien en-tête. Aucun conflit possible avec un autre champ (l'ancien en-tête était un nom
actif de ce champ seul). Le nouvel en-tête peut avoir la forme d'un synonyme (actif ou non) **du même champ** : accepté.

**P3 — ajouter un synonyme** : 400 `VERSION_OBLIGATOIRE` / `CORPS_INVALIDE` → 400 `SYNONYME_INVALIDE` (texte rogné
2–80, forme ≥ 2) → 400 `SYNONYME_EN_DOUBLE` (forme = en-tête réglé ou synonyme actif d'un champ quelconque du type, ou
synonyme inactif **du même champ**) → 422 `SYNONYMES_MAX` (30, actifs et inactifs) → 409. Le texte n'est jamais écrit
dans un fichier : pas de contrôle de premier caractère (React échappe l'affichage).

**P4 — désactiver / réactiver** (les deux origines) : réactivation soumise au contrôle d'unicité (400
`SYNONYME_EN_DOUBLE`). Désactiver un synonyme dont la forme est celle de l'en-tête réglé du champ est permis : l'en-tête
reste reconnu par lui-même.

**P5 — supprimer** : origine `DEFAUT` → 409 `PARAMETRE_SYSTEME` (contrôle 422/409 d'état placé **avant** la
comparaison de version), origine `RH` → suppression physique, 204.

**P6 — réinitialiser** : champs remis aux valeurs du code (en-tête = `libelle()`, `dans_modele = true`, exigence par
défaut, ordre de l'enum), synonymes `DEFAUT` tous réactivés, synonymes `RH` supprimés ; une seule entrée de journal
« Réinitialisation » (`avant` = « 3 synonymes ajoutés par la RH supprimés », `apres` = « Valeurs par défaut ») ; l'autre
type n'est pas touché. Les synonymes par défaut manquants sont réinsérés (même logique que l'amorçage).

Taille : `ImportParametrageService` ≤ 400 lignes ; si nécessaire, sortir les contrôles dans
`ImportParametrageValidation` (pur, testable sans base).

### 6. Lecture par l'import : instantané `ParametrageImport` figé dans l'analyse (D-I25)

**Instantané** (record immuable, sérialisable Jackson, package `importation.parametrage`) :

```java
public record ParametrageImport(String typeImport, long version, List<ChampRegle> champs /* ordre réglé */) {
    public record ChampRegle(String code, String enTete, boolean dansModele, ExigenceChamp exigence, int ordre,
                             List<String> formesActives /* formes normalisées des synonymes actifs */) {}
    public static ParametrageImport parDefaut(TypeImportParametrable type);   // valeurs du code (repli)
    public ChampRegle champ(String code);
    public String enTete(String code);                                        // repli : libelle() du code
    public ExigenceChamp exigence(String code);
    public List<ChampRegle> champsDuModele();                                 // dansModele, triés par ordre
}
```

**Cache** : `ParametrageImportCache.courant(TypeImportParametrable)` lit un cache Caffeine dédié
`CacheConfig.CACHE_IMPORT_PARAMETRAGE` (clé = type, `registerCustomCache`, même durée
`app.parametres.cache-ttl-secondes`, défaut 30 s), chargé par 3 requêtes (parametrage, champs, synonymes actifs du
type). `invaliderApresCommit(type)` reprend le motif de `ParametresCache` (`TransactionSynchronization.afterCommit`).
Autre instance : changement visible ≤ 30 s (D-I25 / T2 règle 8 : < 1 min). **P1 ne lit pas le cache** (lecture directe
en base, pour donner la version exacte à l'écran).

**Reconnaissance** : `ReconnaissanceEnTetes` gagne un constructeur `ReconnaissanceEnTetes(ParametrageImport p)` :
table `forme → code` = forme de l'**en-tête réglé** de chaque champ + ses `formesActives` ; synonymes inactifs ignorés ;
champ hors modèle compris (D-I19). Normalisation inchangée : **la même méthode statique `normaliser()`** sert au calcul
de `forme_normalisee` (P3, P2, amorçage) et à la reconnaissance (exigence T1-25). Collision (impossible si les invariants
tiennent) : l'en-tête l'emporte sur un synonyme, puis le premier champ dans l'ordre réglé ; WARN. L'ancien constructeur
`ReconnaissanceEnTetes(List<? extends ChampImport>)` est conservé et délègue à `ParametrageImport.parDefaut` (tests
existants inchangés). `parNom(code)` reste fondé sur la liste du code. La ligne d'en-tête exige toujours 2 en-têtes
reconnus parmi les 10 premières lignes.

**Figé dans l'analyse** :
- `ResultatStocke` gagne un dernier composant `ParametrageImport parametrage`. `analyser` le prend dans le cache **une
  fois**, le passe à `evaluer`, l'écrit dans le résultat et `rh_import.parametrage_version`.
- `corriger` (I5), `confirmer` (I6 : `reevaluerFiches` / `reevaluerSoldes`), `fichierErreurs` (I7) et `versAnalyse`
  (I3, I4) utilisent `lireResultat(a).parametrage()` ; **chaque** `new ResultatStocke(…)` recopie le paramétrage reçu
  (4 sites : `evaluer`, `reevaluerFiches`, `reevaluerSoldes`, et la construction finale). Résultat stocké avant le
  déploiement (champ absent → `null`) : repli `ParametrageImport.parDefaut(type)` (analyses de 30 min au plus).
- I5 avec une `ligne_en_tete` absente refait la détection avec la reconnaissance **figée** ; les choix explicites de
  colonnes restent validés par `parNom` (un champ hors modèle est attribuable).

**Erreurs de fichier** (`erreursFichier(colonnes, parametrage)`, calculées à l'analyse et à I5, stockées, reprises telles
quelles par la confirmation — comportement actuel) :
- `COLONNE_EN_DOUBLE` : message avec l'en-tête réglé figé (« … désignent toutes deux le champ « E-mail ». »).
- `COLONNE_MATRICULE_ABSENTE`, `COLONNE_SOLDE_ABSENTE` : inchangés (code et texte).
- **Nouveau** `COLONNE_OBLIGATOIRE_ABSENTE`, un par champ `TOUJOURS` autre que MATRICULE / SOLDE sans colonne non
  ambiguë : message « Aucune colonne ne désigne le champ « Statut ». » (en-tête réglé figé ; texte fixé ici, la fiche
  donnait « … le statut » à titre indicatif). `confirmable = false`, I6 → 422 `ERREUR_DE_FICHIER` (D-I13.c inchangé).

**Erreurs de ligne** : `ImportCollaborateursService.evaluer(…, ParametrageImport p)` et
`ImportSoldesService.evaluer(…, ParametrageImport p)` reçoivent l'instantané (signature élargie ; `Contexte` inchangé) :
- Collaborateurs : la boucle `List.of(PRENOM, NOM, UNITE)` est remplacée par : pour chaque champ ≠ MATRICULE, exigence
  `TOUJOURS` (toute ligne) ou `CREATION` (ligne Création) et valeur absente → `CHAMP_OBLIGATOIRE`, `colonne` = en-tête
  du fichier (ou en-tête réglé si colonne absente), message « <en-tête réglé> obligatoire. » (« E-mail obligatoire. »).
  MATRICULE garde `MATRICULE_OBLIGATOIRE`. Ces contrôles ne touchent **que** `evaluerLigne` : `appliquer` n'est pas
  modifié.
- Courriel : `COURRIEL_OBLIGATOIRE` (« Créer les comptes » cochée, ligne Création sans courriel) reste ; s'il est déjà
  couvert par un `CHAMP_OBLIGATOIRE` sur le courriel pour la même ligne, il n'est pas ajouté (une seule erreur).
  L'avertissement `COMPTE_NON_CREE` est inchangé.
- Soldes : `SOLDE_OBLIGATOIRE` inchangé ; MOTIF `TOUJOURS` et cellule vide → `CHAMP_OBLIGATOIRE` « <en-tête réglé>
  obligatoire. », **sans** repli sur le motif global (D-I18) ; MOTIF `FACULTATIF` → comportement D-S09.g inchangé.
- Les autres messages (`VALEUR_TROP_LONGUE`, `VALEUR_INCONNUE`…) gardent l'en-tête du fichier ; leur repli, quand la
  colonne est inconnue, devient l'en-tête réglé (`libelleColonne` / `col` reçoivent `p`).
- `Modification(champ, champLibelle, …)` garde `libelle()` du code : l'historique (I12) nomme les champs par le nom de
  la fiche, indépendamment du paramétrage (D-I26).

**Réponse d'analyse** (`versAnalyse`) : `colonnes[].champ_libelle` = en-tête réglé figé ; `champs_disponibles` = tous les
champs dans l'ordre réglé figé, avec `libelle` = en-tête réglé, `exigence`, `exigence_libelle`, `dans_modele`, et
`obligatoire_creation` conservé (= `exigence ≠ FACULTATIF`, déprécié) pour ne pas casser le web avant le lot WEB.

**Modèle** (I8, I9) : `ImportService.modele(type)` → `FichiersImportXlsx.modele(List<String> enTetes)` avec les
en-têtes de `parametrageCache.courant(type).champsDuModele()`. Une ligne, rien d'autre ; `reconditionner` (3824068)
inchangé. Les en-têtes passent par `EchappementFormule.proteger` (défense en profondeur : D-I20 les refuse déjà).

**Fichier des lignes en erreur** (I7) : en-têtes d'origine du fichier, ordre d'origine, + « Erreurs » (inchangé) ; les
messages viennent de l'évaluation stockée, donc de l'en-tête réglé figé. Échappement D-I11 inchangé.

**Gardes conservées** : lecture bornée (`LectureFichier`, `GardeArchiveXlsx`, 10 Mo, 500 000 cellules, 200 colonnes,
5 000 lignes, XXE), traitement par lot (`SoldeLotService`, `hibernate.jdbc.batch_size`), verrou unique de confirmation,
réanalyse D-I13.d : **aucune modification** de ces classes. Coût de la reconnaissance : table de ≤ 14 × 31 entrées,
consultée une fois par en-tête ; nul sur les 5 000 lignes.

### 7. Journal

Table dédiée `rh_import_champ_journal` (pas `rh_parametre_journal`, cf. alternatives). Écrit dans la transaction de
l'écriture, `avant` / `apres` **déjà en clair** (« Facultatif » → « Obligatoire à la création », « Oui » / « Non »,
liste d'en-têtes séparés par des virgules pour l'ordre, texte du synonyme). Une ligne par attribut modifié :
- P2 : `EN_TETE`, `DANS_MODELE`, `EXIGENCE` par champ modifié ; **une seule** ligne `ORDRE` (champ null) si l'ordre a
  changé, `avant` / `apres` = liste ordonnée des en-têtes ; `SYNONYME_AJOUTE` / `SYNONYME_REACTIVE` pour l'ancien
  en-tête conservé.
- P3 `SYNONYME_AJOUTE`, P4 `SYNONYME_DESACTIVE` / `SYNONYME_REACTIVE`, P5 `SYNONYME_SUPPRIME`, P6 `REINITIALISATION`.

Lecture (P7) : `champ_libelle` = `libelle()` du code (« Courriel », jamais l'en-tête réglé ni le code) ou « Tous les
champs » ; `attribut_libelle` depuis une table fixe du code (A.1). Tri `horodatage DESC`, puis `id`. Pas de purge (≥ 5
ans).

### 8. Contrat d'API

Annexe A (définitif, G1). Nouvelle classe `web/ImportParametrageController`, base
`/api/referentiel/v1/parametres/imports/{type}` ; **aucune route gateway à ajouter** (`/api/referentiel/**`).
`@PreAuthorize` : `BACKOFFICE_LECTURE` sur P1 / P7, `BACKOFFICE_ECRITURE` sur P2 à P6 et sur les deux 405.

### 9. Configuration et déploiement

- Aucune variable d'environnement nouvelle (TTL commun `app.parametres.cache-ttl-secondes`). Rien dans `deploy/`.
- Mise à jour d'un serveur : reconstruire `svc-referentiel-rh` et `web`, redémarrer la gateway puis `sirh-web-1`,
  supprimer les instances Eureka `DOWN` (procédure habituelle, CONTEXTE § 8). L'amorçage crée le paramétrage par défaut
  au premier démarrage ; les analyses en cours pendant la bascule utilisent le repli par défaut.

## Alternatives écartées

- **Réutiliser `rh_parametre_valeur` / `rh_parametre_journal` du lot 3** : un synonyme n'est pas une valeur de
  référentiel (pas de code, couleur, SLA), la clé (type, champ, forme) ne tient pas dans `code varchar(40)`, et
  l'`action` du journal est un `@Enumerated` à 7 valeurs (CHECK à recréer) avec un format `avant_json` différent.
- **Paramétrage en un seul document JSON par type** (une colonne `jsonb`) : plus simple à écrire, mais perd les
  contraintes d'unicité, les identifiants de synonymes (P4 / P5) et rend le journal par attribut plus fragile.
- **Version optimiste par ligne (`@Version` sur chaque champ / synonyme)** : D-I23 veut une version par type ; le
  verrou pessimiste sur une ligne par type sérialise aussi les contrôles d'unicité entre tables.
- **Relire le paramétrage courant à la confirmation** : contraire à D-I25 (une confirmation ferait apparaître des erreurs
  absentes de l'aperçu).
- **Endpoint d'assistant « retenir la colonne comme synonyme » sur l'analyse** : logique serveur en double ; P1 + P3
  suffisent.
- **Version facultative sur P3 pour l'assistant** : affaiblit D-I23 ; l'assistant lit P1 juste avant.

## Conséquences

- La liste des champs reste dans le code (un nouveau champ importable = développement + amorçage automatique de son
  réglage par défaut, ajouté en fin d'ordre).
- `ChampImport.obligatoireCreation()` disparaît ; `ImportDtos.ChampDisponible` s'élargit (compatible).
- Le web ne code plus aucune liste d'exigences ni de cadenas : tout vient de P1 (`exigences_possibles`,
  `verrou_libelle`).
- Une analyse est reproductible : elle porte son paramétrage. Le fichier de résultat grossit de quelques Ko.
- `ImportService` (826 lignes) ne doit pas grossir : la résolution du paramétrage va dans `ParametrageImportCache`, le
  calcul des erreurs de fichier peut sortir dans une classe `ControleColonnes` (pure).

## Sécurité / RGPD (STRIDE léger)

| Menace | Analyse | Parade |
|---|---|---|
| Usurpation | Auteur du journal falsifié par le corps | Auteur tiré du JWT (`AuteurParametreResolver`) ; aucun champ auteur accepté en entrée |
| Altération | Écrasement concurrent entre deux RH | Version par type + `FOR UPDATE` ; 409 sans écriture |
| Altération | Injection de formule par un en-tête écrit dans le modèle | D-I20 refuse `= + - @ \t \r` en tête ; `EchappementFormule.proteger` à l'écriture |
| Altération | Sabotage de l'import (synonymes désactivés, exigences relevées) par un compte RH | Droit RH / ADMIN seulement ; en-tête réglé toujours reconnu (le modèle reste réimportable) ; journal 5 ans ; réinitialisation en un geste |
| Répudiation | « Ce n'est pas moi » | Journal ajout seul (`@Immutable`), auteur + compte + horodatage |
| Divulgation | Données personnelles | Aucune dans le paramétrage ; le journal expose le nom de l'auteur (RH, ADMIN, DIRECTION, comme le lot 3) ; aucune valeur de cellule ajoutée aux journaux INFO |
| Déni de service | Paramétrage énorme qui ralentit l'analyse | 30 synonymes par champ, 80 caractères, corps P2 de taille fixe (14 / 3) ; reconnaissance en O(en-têtes) |
| Élévation | DIRECTION ou collaborateur qui écrit | `@PreAuthorize` sur chaque méthode, y compris les 405 ; COL / RO → 403, DIRECTION → 403 en écriture ; aucun `/internal` |
| Élévation | Colonne détournée vers `PROFIL_ACCES` par un synonyme | Pouvoir identique à la correspondance manuelle existante ; profils sensibles déjà refusés à l'import (`PROFIL_NON_IMPORTABLE`) |

## Risques et dettes

- **Création de fiche dupliquée** entre `ImportCollaborateursService` et `ReferentielRhService` (dette connue) : **ne pas
  l'aggraver** — aucune modification de `appliquer()` ni de `ReferentielRhService` ; les nouvelles règles ne vivent que
  dans `evaluerLigne`.
- Signatures élargies (`evaluer(…, p)`, `FichiersImportXlsx.modele(List<String>)`) : tests existants à adapter
  (`ImportCollaborateursServiceTest`, `ImportSoldesServiceTest`, `FichiersImportXlsxTest`, `ReconnaissanceEnTetesTest`) ;
  les 364 tests doivent rester verts avec le paramétrage par défaut.
- Oubli de recopier `parametrage` dans un des `new ResultatStocke(…)` → la confirmation retomberait sur les défauts :
  test dédié (T2-10).
- Message `COLONNE_OBLIGATOIRE_ABSENTE` fixé ici (« … le champ « Statut ». ») : critère T2-7 / T4-2 à aligner par le
  rédacteur Jira.
- Décisions prises ici, à faire confirmer (§ 23) : forme déjà présente mais désactivée sur le même champ → refus avec
  invitation à réactiver ; ancien en-tête refusé (422 `SYNONYMES_MAX`) quand le champ a déjà 30 synonymes (le renommage
  est alors bloqué) ; une seule erreur quand `CHAMP_OBLIGATOIRE` et `COURRIEL_OBLIGATOIRE` visent le même courriel.
- Plusieurs instances : jusqu'à 30 s d'écart entre modèle téléchargé et paramétrage enregistré (accepté, < 1 min).
- Dettes existantes non traitées : `ImportService` au-delà de 400 lignes ; liste des soldes filtrée en mémoire ; exports
  CSV antérieurs sans échappement.

## Plan d'implémentation

Ordre : lot BACK (T1 puis T2, un seul agent, même service) ; lot WEB (T3 + T4) **en parallèle**, codé sur l'annexe A.
Dossiers disjoints : `svc-referentiel-rh/**` (dev-backend) / `rh-admin-web/**` (dev-web). Aucun fichier de `gateway/`,
`deploy/`, `rh_mobile_app/`, `docs/` n'est modifié par les développeurs.

### Lot BACK — `dev-backend` : `svc-referentiel-rh/**`

Nouveaux fichiers (package `com.hr.referentiel.importation.parametrage` sauf mention) :
- `ExigenceChamp`, `OrigineSynonyme`, `TypeImportParametrable`, `ExigenceChampConverter`, `OrigineSynonymeConverter`.
- Entités `ImportParametrage`, `ImportChampRegle`, `ImportSynonyme`, `ImportChampJournal` (+ repositories ;
  `ImportParametrageRepository.findByIdForUpdate`).
- `ParametrageImport` (instantané), `ParametrageImportCache`, `ImportParametrageService`
  (+ `ImportParametrageValidation` si > 400 lignes), `ImportChampsJournal` (écriture / lecture, libellés d'attribut).
- `ImportParametrageDtos` (records snake_case de l'annexe A).
- `config/ImportChampsAmorceRunner`.
- `web/ImportParametrageController`.

Fichiers modifiés :
- `importation/ChampImport.java` (exigences, verrous ; `obligatoireCreation` retiré).
- `importation/ReconnaissanceEnTetes.java` (constructeur depuis `ParametrageImport`).
- `importation/ImportService.java` (instantané figé, `ResultatStocke.parametrage`, `erreursFichier`, `versAnalyse`,
  `modele(type)`) ; éventuellement `importation/ControleColonnes.java` (extraction).
- `importation/ImportCollaborateursService.java`, `importation/ImportSoldesService.java` (exigences, messages).
- `importation/FichiersImportXlsx.java` (`modele(List<String>)`, protection des en-têtes).
- `importation/ImportDtos.java` (`ChampDisponible`), `importation/ImportAnalyse.java` (`parametrage_version`).
- `web/ImportController.java` (I8 / I9 appellent `modele(type)`).
- `config/CacheConfig.java` (`CACHE_IMPORT_PARAMETRAGE`, 30 s).

Tests attendus (JUnit 5 / Mockito / `@DataJpaTest` / `@WebMvcTest`) :
- `ImportParametrageServiceTest` : critères T1-3 à T1-22 (verrous, exigences, ordre, libellés, ancien en-tête → synonyme
  dont la réactivation d'un inactif, unicité synonymes / en-têtes, 30 max, désactiver / réactiver / supprimer,
  réinitialisation, journal en clair, version incrémentée d'exactement 1, 409 sans écriture, écriture sans effet).
- `ImportChampsAmorceRunnerTest` (`@DataJpaTest`, style `ParametresAmorceRunnerTest`) : base neuve = valeurs du code
  (T1-1) ; double passage sans doublon ; valeur réglée conservée (T1-2) ; synonyme par défaut en conflit inséré inactif.
- `ChampImportTest` : libellé normalisé présent dans les synonymes par défaut ; aucune forme par défaut partagée entre
  deux champs d'un type ; exigences possibles / minimum conformes au tableau § 2.
- `ImportParametrageControllerSecurityTest` (`@WebMvcTest`) : matrice A.3 sur P1 à P7 et les 405 (sans JWT, COLLABORATEUR,
  RESPONSABLE, RH, ADMIN, DIRECTION), 404 `TYPE_IMPORT_INCONNU`, corps d'erreur `code` / `message` / `champ`.
- `ReconnaissanceEnTetesTest` : en-tête réglé reconnu, synonyme inactif ignoré, champ hors modèle reconnu, même
  `normaliser()` que P3.
- `ImportServiceTest` (ajouts) : modèle conforme au paramétrage (relu en `ZipInputStream` strict, comme
  `FichiersImportXlsxTest`) ; paramétrage figé à travers I5 / I6 / I7 (T2-10) ; résultat stocké sans `parametrage` →
  défauts ; `COLONNE_OBLIGATOIRE_ABSENTE` puis levée après I5 (T2-7) ; `champs_disponibles` (T2-11).
- `ImportCollaborateursServiceTest` / `ImportSoldesServiceTest` (ajouts) : T2-6, T2-8, T2-9, messages « <en-tête>
  obligatoire. ».
- Non-régression : `mvn -B -pl svc-referentiel-rh test` vert (364 + nouveaux) ; analyse de 5 000 lignes avec 30
  synonymes par champ ≤ 10 s (T2-15).

### Lot WEB — `dev-web` : `rh-admin-web/**`

- `src/api/importParametrageApi.ts` (nouveau) : types et appels P1 à P7 (via `apiFetch`, base
  `/api/referentiel/v1/parametres/imports`), messages d'erreur serveur rendus tels quels, `champ` de l'erreur pour placer
  le message sous la bonne ligne.
- `src/pages/parametres/ImportExcelPanel.tsx` (nouveau) : onglets « Collaborateurs », « Soldes de congé »,
  « Historique » ; onglet initial lu dans `?onglet=collaborateurs|soldes|historique`.
- `src/pages/parametres/ImportChampsTable.tsx` (nouveau) : tableau (Monter / Descendre au clavier, case « Dans le
  modèle », en-tête, liste Exigence = `exigences_possibles`, cadenas + `verrou_libelle`, options interdites grisées avec
  raison), « Enregistrer » (P2, tout le tableau), « Annuler les modifications », garde de sortie, contrôle local de D-I17
  avant envoi, lien « Télécharger le modèle » (`telechargerModele` existant).
- `src/pages/parametres/SynonymesDialog.tsx` (nouveau) : liste, ajout (P3), désactiver / réactiver (P4), supprimer (P5,
  RH seulement), forme reconnue affichée.
- `src/pages/parametres/ImportJournalPanel.tsx` (nouveau) : P7 paginé (format `{contenu, page, taille, total}`, comme
  `JournalPanel`).
- `src/pages/ParametresPage.tsx` : entrée `{ genre: 'imports', cle: 'import-excel', domaine: 'ORGANISATION',
  titre: 'Import Excel' }` ; lecture seule via `useLectureSeule` (bandeau « Consultation uniquement », aucun bouton).
- `src/api/importsApi.ts` : `ChampDisponible` élargi (`exigence`, `exigence_libelle`, `dans_modele`).
- `src/pages/imports/AssistantImport.tsx` : libellés de la liste (« Poste (obligatoire à la création) ») dans l'ordre de
  `champs_disponibles`, « Ignorer » en premier ; case « Retenir « X » comme synonyme de Y » pour une colonne
  **initialement non reconnue** attribuée à la main (jamais pour « Ignorer » ni une colonne reconnue) ; à l'enregistrement :
  P1 (version) → P3 → I5, I5 fait même si P1 / P3 échouent (message non bloquant) ; lien « Paramétrer les colonnes »
  vers `/app/parametres?element=import-excel&onglet=<type>` en nouvel onglet (`target="_blank" rel="noopener"`).
- Fenêtre « Êtes-vous sûr ? » (`ConfirmDialog`) pour réinitialiser, supprimer / désactiver un synonyme, enregistrer un
  retrait du modèle ; 409 `PARAMETRE_MODIFIE_ENTRE_TEMPS` → message, rechargement, saisie perdue rappelée.
- Vérifier : `npx tsc --noEmit -p .` et `npx vite build` verts ; 1024 px sans débordement.

### Recette (rappel pour le testeur)

Matrice A.3 avec DRH01, RH02, ADM01, DG01, CS-WEB, COL-WEB ; concurrence de version DRH01 / RH02 ; synonymes de test
préfixés « [RECETTE-IMPCOL] » ; **réinitialiser les deux types en fin de recette** (P6) ; ne supprimer que ses propres
données.

---

## Annexe A — Contrat d'API (G1, définitif)

### A.0 Conventions

- Base `P = /api/referentiel/v1/parametres/imports/{type}`, `type` ∈ `collaborateurs` | `soldes` (minuscules) ; autre
  valeur → 404 `TYPE_IMPORT_INCONNU`. Route gateway existante `/api/referentiel/**`.
- JSON snake_case. Instants ISO-8601 UTC. Erreur : `{ "code": "…", "erreur": "<message>", "message": "<message>" }`
  + `"champ": "<CODE>"` quand l'erreur vise un champ (P2, P3). 401 sans JWT : corps vide.
- Rôles : `BO_LECTURE` = `PreAuthorizeExpressions.BACKOFFICE_LECTURE` (RH, DIRECTION, ADMIN) ; `BO_ECRITURE` =
  `BACKOFFICE_ECRITURE` (RH, ADMIN).
- Ordre des contrôles (G2) : 401 → 403 rôle → 404 → 400 corps → 422 règle → 409 état / version.
- Concurrence : `version` (entier) du type d'import, dans le corps (ou `?version=` pour DELETE) ; absente → 400
  `VERSION_OBLIGATOIRE` ; périmée → 409 `PARAMETRE_MODIFIE_ENTRE_TEMPS`. P1 renvoie aussi `ETag: "<version>"` et
  `Cache-Control: no-cache` ; `If-None-Match` égal → 304 (facultatif côté web).
- Les codes (`MATRICULE`, `CREATION`, `DEFAUT`…) servent à la logique du client ; l'écran affiche les `*_libelle`
  (C-UX-01, G4).

### A.1 Énumérations

| Énumération | Valeurs (libellé) |
|---|---|
| `exigence` | `FACULTATIF` (Facultatif), `CREATION` (Obligatoire à la création), `TOUJOURS` (Toujours obligatoire) |
| `origine` | `DEFAUT` (Par défaut), `RH` (Ajouté par la RH) |
| `attribut` (journal) | `EN_TETE` (En-tête), `DANS_MODELE` (Dans le modèle), `ORDRE` (Ordre), `EXIGENCE` (Exigence), `SYNONYME_AJOUTE` (Synonyme ajouté), `SYNONYME_DESACTIVE` (Synonyme désactivé), `SYNONYME_REACTIVE` (Synonyme réactivé), `SYNONYME_SUPPRIME` (Synonyme supprimé), `REINITIALISATION` (Réinitialisation) |
| Champs `collaborateurs` (ordre par défaut) | `MATRICULE`, `PRENOM`, `NOM`, `COURRIEL`, `UNITE`, `POSTE`, `FONCTION`, `QUALITE`, `AFFECTATION`, `DATE_RECRUTEMENT`, `STATUT`, `PROFIL_ACCES`, `FAMILLE_METIER`, `NIVEAU_SENIORITE` |
| Champs `soldes` | `MATRICULE`, `SOLDE`, `MOTIF` |

### A.2 Endpoints

| # | Méthode, chemin | Rôle | Entrée | 2xx | Erreurs |
|---|---|---|---|---|---|
| P1 | `GET P` | BO_LECTURE | — | 200 `Parametrage` | 404 `TYPE_IMPORT_INCONNU` |
| P2 | `PUT P` | BO_ECRITURE | `{ "version": 5, "champs": [ { "code": "MATRICULE", "en_tete": "Matricule", "dans_modele": true, "exigence": "TOUJOURS" }, … ] }` — **tous** les champs, dans le nouvel ordre | 200 `Parametrage` | 404 → 400 `VERSION_OBLIGATOIRE`, `CORPS_INVALIDE`, `ORDRE_INCOMPLET`, `EXIGENCE_INVALIDE`, `LIBELLE_INVALIDE`, `LIBELLE_EN_DOUBLE` → 422 `CHAMP_VERROUILLE`, `CHAMP_OBLIGATOIRE_HORS_MODELE`, `SYNONYMES_MAX` → 409 `PARAMETRE_MODIFIE_ENTRE_TEMPS` |
| P3 | `POST P/champs/{code}/synonymes` | BO_ECRITURE | `{ "texte": "Mle", "version": 5 }` | 201 `Parametrage` | 404 `TYPE_IMPORT_INCONNU`, `CHAMP_INCONNU` → 400 `VERSION_OBLIGATOIRE`, `CORPS_INVALIDE`, `SYNONYME_INVALIDE`, `SYNONYME_EN_DOUBLE` → 422 `SYNONYMES_MAX` → 409 `PARAMETRE_MODIFIE_ENTRE_TEMPS` |
| P4a | `POST P/synonymes/{id}/desactiver` | BO_ECRITURE | `{ "version": 6 }` | 200 `Parametrage` | 404 `SYNONYME_INTROUVABLE` → 400 `VERSION_OBLIGATOIRE` → 409 `PARAMETRE_MODIFIE_ENTRE_TEMPS` |
| P4b | `POST P/synonymes/{id}/reactiver` | BO_ECRITURE | `{ "version": 6 }` | 200 `Parametrage` | 404 `SYNONYME_INTROUVABLE` → 400 `VERSION_OBLIGATOIRE`, `SYNONYME_EN_DOUBLE` → 409 `PARAMETRE_MODIFIE_ENTRE_TEMPS` |
| P5 | `DELETE P/synonymes/{id}?version=6` | BO_ECRITURE | — | 204 | 404 `SYNONYME_INTROUVABLE` → 400 `VERSION_OBLIGATOIRE` → 409 `PARAMETRE_SYSTEME` (origine `DEFAUT`) → 409 `PARAMETRE_MODIFIE_ENTRE_TEMPS` |
| P6 | `POST P/reinitialiser` | BO_ECRITURE | `{ "version": 7 }` | 200 `Parametrage` (valeurs par défaut) | 404 → 400 `VERSION_OBLIGATOIRE` → 409 `PARAMETRE_MODIFIE_ENTRE_TEMPS` |
| P7 | `GET P/journal?page=0&taille=20` | BO_LECTURE | `taille` 1–100 (défaut 20) | 200 `PageJournalImport` | 404 `TYPE_IMPORT_INCONNU` |
| P8 | `POST P/champs` · `DELETE P/champs/{code}` | BO_ECRITURE | — | — | 405 `PARAMETRE_COMPORTEMENT` « La liste des champs d'import est fixe. » |

`{id}` d'un synonyme d'un autre type → 404 `SYNONYME_INTROUVABLE`. `{code}` inconnu du type → 404 `CHAMP_INCONNU`.
Le web relit P1 après P5 (204, sans corps) pour obtenir la nouvelle version.

```json
Parametrage {
  "type": "collaborateurs", "type_libelle": "Collaborateurs",
  "version": 6, "modifie_le": "2026-11-13T10:12:00Z", "modifie_par_nom": "Amel Trabelsi",
  "champs": [   // ordre réglé
    { "code": "MATRICULE", "libelle": "Matricule",            // nom du champ de la fiche (= en-tête par défaut)
      "en_tete": "Mle", "en_tete_defaut": "Matricule",
      "dans_modele": true, "retirable_du_modele": false,
      "exigence": "TOUJOURS", "exigence_libelle": "Toujours obligatoire",
      "exigence_minimale": "TOUJOURS",
      "exigences_possibles": [ { "code": "TOUJOURS", "libelle": "Toujours obligatoire" } ],
      "verrouille": true, "verrou_libelle": "Toujours obligatoire : sert à retrouver la fiche",
      "ordre": 0,
      "synonymes": [
        { "id": "…", "texte": "matricule", "forme_normalisee": "matricule", "origine": "DEFAUT",
          "origine_libelle": "Par défaut", "actif": true, "supprimable": false },
        { "id": "…", "texte": "Mle", "forme_normalisee": "mle", "origine": "RH",
          "origine_libelle": "Ajouté par la RH", "actif": true, "supprimable": true } ] },
    …
  ] }

PageJournalImport {
  "contenu": [ { "id": "…", "horodatage": "2026-11-13T10:12:00Z", "auteur_nom": "Amel Trabelsi",
                 "champ": "COURRIEL", "champ_libelle": "Courriel",
                 "attribut": "EN_TETE", "attribut_libelle": "En-tête",
                 "avant": "Courriel", "apres": "E-mail" } ],
  "page": 0, "taille": 20, "total": 1 }
```

`synonymes` triés : `DEFAUT` puis `RH`, puis texte. `champ` / `champ_libelle` = `null` / « Tous les champs » pour
`ORDRE` et `REINITIALISATION`.

#### Imports existants (modifiés)

| # | Changement |
|---|---|
| I1, I2 (analyse) | Paramétrage courant (cache ≤ 30 s) figé dans l'analyse ; nouvelle erreur de fichier `COLONNE_OBLIGATOIRE_ABSENTE` (`confirmable = false`) ; erreurs de ligne `CHAMP_OBLIGATOIRE` selon l'exigence |
| I3, I4, I5 | `champs_disponibles` et `colonnes[].champ_libelle` depuis le paramétrage figé ; I5 utilise la reconnaissance figée |
| I6 | Réanalyse avec le paramétrage figé ; 422 `ERREUR_DE_FICHIER` aussi pour `COLONNE_OBLIGATOIRE_ABSENTE` |
| I7 | Inchangé (en-têtes d'origine + « Erreurs ») ; messages avec l'en-tête réglé figé |
| I8, I9 (modèle) | Champs `dans_modele`, ordre et en-têtes réglés (paramétrage courant) |
| I12 | Inchangé : `champ_libelle` = nom du champ de la fiche |

```json
ChampDisponible { "champ": "POSTE", "libelle": "Poste", "exigence": "CREATION",
  "exigence_libelle": "Obligatoire à la création", "dans_modele": false,
  "obligatoire_creation": true }   // obligatoire_creation : déprécié (= exigence ≠ FACULTATIF)

ProblemeVue (fichier) { "code": "COLONNE_OBLIGATOIRE_ABSENTE", "message": "Aucune colonne ne désigne le champ « Statut »." }
ProblemeVue (ligne)   { "code": "CHAMP_OBLIGATOIRE", "colonne": "E-mail", "message": "E-mail obligatoire." }
```

### A.3 Matrice des rôles (G3)

| Endpoint | Sans JWT | COLLABORATEUR (COL-WEB) | RO / RESPONSABLE (CS-WEB) | RH (DRH01, RH02) | ADMIN (ADM01) | DIRECTION (DG01) | `@PreAuthorize` |
|---|---|---|---|---|---|---|---|
| P1, P7 | 401 | 403 | 403 | 200 | 200 | 200 | `BACKOFFICE_LECTURE` |
| P2, P4a, P4b, P6 | 401 | 403 | 403 | 200 | 200 | 403 | `BACKOFFICE_ECRITURE` |
| P3 | 401 | 403 | 403 | 201 | 201 | 403 | `BACKOFFICE_ECRITURE` |
| P5 | 401 | 403 | 403 | 204 | 204 | 403 | `BACKOFFICE_ECRITURE` |
| P8 (405) | 401 | 403 | 403 | 405 | 405 | 403 | `BACKOFFICE_ECRITURE` |
| I8, I9 (modèle, inchangé) | 401 | 403 | 403 | 200 | 200 | 403 | `BACKOFFICE_ECRITURE` |

---
---

# Partie 2 — Champs personnalisés (D-I27 à D-I41, tickets T5 à T12)

| | |
|---|---|
| **Statut** | proposé (13/11/2026) ; partie 1 développée, recette 164/164 sur 8090, non commitée |
| **Références** | doc § 5.5 ter (D-I27 à D-I41), § 5.5 bis, D-S05, D-S08, D-S09, C-L-02, C-L-03 ; fiche partie 2 (G6 à G8, matrice partie 2, API indicative C1 à C6, V1 à V6) |
| **Décisions de l'utilisateur (13/11/2026)** | périmètre complet (champs personnalisés, nouvel export CSV de la liste, journal des valeurs, mobile « Mon dossier » en lecture, champs sur les ajustements de solde) ; RH / ADMIN ne modifient jamais leurs propres valeurs, import compris (403 / erreur de ligne `AUTO_MODIFICATION_INTERDITE`) |

## Contexte (relevé dans le code le 13/11/2026)

- `entity/Collaborateur` (`rh_collaborateur`) : colonnes fixes seulement ; enregistré en JPA **sans** `@DynamicUpdate` par
  `ReferentielRhService` (PUT de la fiche) et par `ImportCollaborateursService.appliquer` (`saveAll`) : toute colonne
  ajoutée à cette entité est réécrite par une entité chargée plus tôt (perte de mise à jour possible).
- `entity/MouvementSoldeConge` : `@Immutable`, colonnes `updatable = false` ; inséré par `SoldeCongeService.enregistrer`
  (saisie) et en JDBC groupé par `SoldeLotService.ajuster` (import), dont le `Resultat` ne rend pas l'identifiant du
  mouvement.
- Liste des collaborateurs : `GET /api/referentiel/v1/collaborateurs` ne filtre côté serveur que `statut` et
  `unite_identifiant` ; la page web `CollaborateursPage` charge **toutes** les fiches (`getTousCollaborateurs`, pages de
  200) et applique ses filtres (recherche sans accents, direction / département / service, profil, statut, famille,
  niveau, « profil métier à compléter ») **dans le navigateur**. Aucun export CSV de la liste, aucun journal de fiche.
- Import : champs indexés par `EnumMap<ChampImport.Fiche, …>` / `ChampImport.Solde.valueOf(c.champ())` dans
  `ImportCollaborateursService` et `ImportSoldesService` ; `ControleColonnes` et `ReconnaissanceEnTetes` partent de
  `type.champs()` (enums). `LigneResultat.valeurs` est une `Map<String, String>` (clé = code) : elle peut porter d'autres
  codes sans changer le record. `ImportService` = 824 lignes, `ImportParametrageService` = 531 lignes,
  `ImportCollaborateursService` = 668 lignes.
- `ReconnaissanceEnTetes.normaliser()` coupe l'en-tête à son **premier mot entièrement numérique** (D-I03, voulu pour
  « Solde au 30/11 » → « solde au », D-S09.g) : « Adresse 1 » = « Adresse 2 » = « adresse », « Identifiant 10 » =
  « identifiant » (synonyme par défaut de MATRICULE). Relevé en recette de la partie 1.
- Tests : H2 en `MODE=PostgreSQL` (`ImportServiceTest`, `ImportPerformanceTest`, `SoldesCongeH2Test`…), production
  PostgreSQL 17 ; `on conflict do nothing` déjà employé et compatible ; aucune dépendance Testcontainers.
- Gateway : `/api/referentiel/**` et `/api/rh/v1/**` déjà routés vers `svc-referentiel-rh`. Mobile : `MonDossierScreen`
  lit `/collaborateurs/moi/hierarchie` ; `LigneMouvement` (`mon_solde_screen.dart`) affiche les mouvements de
  `/api/rh/v1/conges/solde/moi/mouvements`.

## Décision

### P2-1. Tout dans `svc-referentiel-rh`, sans Kafka ni `/internal`

Définitions, valeurs, filtre, export et journal vivent dans le référentiel (base `referentiel_rh_db`). Aucun événement,
aucun appel entre services, aucune route gateway nouvelle, aucune variable d'environnement nouvelle.

### P2-2. Définitions : deux tables + le paramétrage d'import de la partie 1

Un champ personnalisé est **un champ d'import comme un autre** : il a une ligne `rh_import_champ` (en-tête, dans le
modèle, exigence, ordre) et ses synonymes dans `rh_import_synonyme` (origine `RH` seulement). Ce qui lui est propre va
dans deux tables neuves. Même **version par type** (`rh_import_parametrage`), même verrou `FOR UPDATE`, même journal
(`rh_import_champ_journal`), même cache 30 s, même instantané figé dans l'analyse.

```
rh_champ_perso                                   -- définition (ddl-auto=update, table neuve)
  id                     uuid          PK
  type_import            varchar(20)   NOT NULL  -- COLLABORATEURS | SOLDES
  code                   varchar(40)   NOT NULL  -- PERSO_…, généré, immuable ; UNIQUE (type_import, code)
  libelle                varchar(80)   NOT NULL
  finalite               varchar(200)  NOT NULL
  type_valeur            varchar(10)   NOT NULL  -- TEXTE | NOMBRE | DATE | LISTE (AttributeConverter, pas de CHECK)
  longueur_max           integer       NULL      -- TEXTE : 1..500
  decimales              integer       NULL      -- NOMBRE : 0..4
  minimum, maximum       numeric(15,4) NULL      -- NOMBRE : dans ±999 999 999
  date_min, date_max     date          NULL      -- DATE : dans 1900-01-01..2100-12-31
  visible_collaborateur  boolean       NOT NULL
  actif                  boolean       NOT NULL
  confirme_non_sensible_le   timestamptz  NOT NULL  -- D-I35
  confirme_non_sensible_par  varchar(160) NOT NULL
  cree_le                timestamptz   NOT NULL  -- ordre de création (réinitialisation D-I34)
  cree_par_nom           varchar(160)  NOT NULL

rh_champ_perso_valeur_liste
  id                     uuid          PK
  champ_perso_id         uuid          NOT NULL  -- même base, même service : @ManyToOne permis
  code                   varchar(40)   NOT NULL  -- généré depuis le libellé (A-Z 0-9 _), immuable ; UNIQUE (champ_perso_id, code)
  libelle                varchar(80)   NOT NULL
  ordre                  integer       NOT NULL  -- 0..n-1, dense
  actif                  boolean       NOT NULL

rh_import_champ_journal (existante)
  + champ_libelle        varchar(80)   NULL      -- libellé figé d'un champ personnalisé (lisible après suppression)
```

Règles :
- **Aucun `@Enumerated`** (type de valeur, origine, attributs du journal) : colonnes `varchar` + converter → aucune
  contrainte CHECK générée, aucun runner de CHECK (même choix que la partie 1).
- **Code** : `PERSO_` + libellé en majuscules ASCII (accents retirés, tout caractère hors `A-Z0-9` → `_`, `_` multiples
  réduits, bords retirés), tronqué à 40 ; s'il est pris, base tronquée pour loger `_2`, `_3`… Un code n'est **jamais
  réattribué** : la génération l'évite parmi les définitions **et** les `champ_code` du journal P7 du type (un champ
  supprimé puis recréé sous le même libellé reçoit `…_2`). Codes de valeurs de liste : même règle sans préfixe (« XL »
  → `XL`, « 2026 » → `2026`, vide → `VALEUR`).
- **Inactif** : `actif = false`, et sa ligne `rh_import_champ` passe à `dans_modele = false`, `exigence = FACULTATIF`
  (ligne conservée). L'instantané `ParametrageImport` **n'inclut pas** les champs personnalisés inactifs (non reconnus à
  l'import, absents du modèle, de P2, des filtres et de l'export).
- **Utilisations** (`utilisations` de P1, `PARAMETRE_UTILISE`) : `count` groupé par `champ_code` sur
  `rh_valeur_perso_fiche` + `rh_valeur_perso_mouvement` ; pour une valeur de liste, `count` groupé sur
  `valeur_liste_code`. Requêtes groupées par lecture de P1, jamais une par champ.

**Instantané étendu** (`ParametrageImport`, partie 1 § 6) :

```java
public record ParametrageImport(String typeImport, long version, List<ChampRegle> champs,
                                List<DefinitionPerso> definitionsPerso /* actives seulement ; null lu = vide */) {
    public record ChampRegle(String code, String enTete, boolean dansModele, ExigenceChamp exigence, int ordre,
                             List<String> formesActives, boolean personnalise) {}
    public record DefinitionPerso(String code, String libelle, TypeValeurPerso typeValeur, ReglesPerso regles,
                                  List<ValeurListe> valeursListe /* actives ET inactives, dans l'ordre */) {}
    public record ReglesPerso(Integer longueurMax, Integer decimales, BigDecimal minimum, BigDecimal maximum,
                              LocalDate dateMin, LocalDate dateMax) {}
    public record ValeurListe(String code, String libelle, boolean actif) {}
}
```

`ParametrageImportCache` charge en plus les définitions actives et leurs valeurs de liste (2 requêtes). Un résultat
stocké avant le déploiement (composant absent) se lit avec une liste vide. `parDefaut(type)` → liste vide.

### P2-3. Valeurs : **tables de valeurs typées** (pas de JSONB)

```
rh_valeur_perso_fiche                            -- valeur courante d'un champ sur une fiche (modifiable)
  id                 uuid          PK
  collaborateur_id   uuid          NOT NULL
  champ_code         varchar(40)   NOT NULL      -- UNIQUE (collaborateur_id, champ_code)
  valeur_texte       varchar(500)  NULL          -- TEXTE (rogné, tel que saisi : jamais échappé en base)
  valeur_nombre      numeric(15,4) NULL          -- NOMBRE (valeur exacte, jamais arrondie)
  valeur_date        date          NULL          -- DATE
  valeur_liste_code  varchar(40)   NULL          -- LISTE (code de la valeur)
  texte_recherche    varchar(500)  NULL          -- TEXTE : minuscules, sans accents, espaces réduits (filtre « contient »)
  modifie_le         timestamptz   NOT NULL
  INDEX (champ_code, valeur_liste_code) · (champ_code, valeur_nombre) · (champ_code, valeur_date)

rh_valeur_perso_fiche_etat                       -- version optimiste des valeurs d'une fiche
  collaborateur_id   uuid          PK
  version            bigint        NOT NULL      -- ligne absente = version 0
  modifie_le         timestamptz   NOT NULL
  modifie_par_nom    varchar(160)  NOT NULL

rh_valeur_perso_journal                          -- ajout seul (@Immutable), sans purge (≥ 5 ans, C-L-02)
  id                 uuid          PK
  collaborateur_id   uuid          NOT NULL
  champ_code         varchar(40)   NOT NULL
  champ_libelle      varchar(80)   NOT NULL      -- libellé au moment du changement
  avant              varchar(600)  NULL          -- texte EN CLAIR déjà formaté (« 42 », « 15/11/2026 », « M ») ; null = vide
  apres              varchar(600)  NULL
  origine            varchar(12)   NOT NULL      -- SAISIE | IMPORT | CREATION (converter)
  import_id          uuid          NULL
  auteur_compte_id   uuid          NULL
  auteur_nom         varchar(160)  NOT NULL
  horodatage         timestamptz   NOT NULL
  INDEX (collaborateur_id, horodatage)

rh_valeur_perso_mouvement                        -- valeur d'un champ « soldes » sur un mouvement d'ajustement
  id                 uuid          PK            -- @Immutable, colonnes updatable=false, comme le mouvement
  mouvement_id       uuid          NOT NULL      -- UNIQUE (mouvement_id, champ_code)
  champ_code         varchar(40)   NOT NULL
  valeur_texte, valeur_nombre, valeur_date, valeur_liste_code   (mêmes types)
  INDEX (champ_code)
```

Une valeur vide n'a **pas de ligne** (effacer = supprimer la ligne) : « Vide » = `NOT EXISTS`, « Renseigné » = `EXISTS`.
Exactement une colonne typée renseignée par ligne, celle du type courant (le type n'est modifiable que sans aucune
valeur, D-I30).

Pourquoi une table plutôt qu'une colonne JSONB sur `rh_collaborateur` / `rh_mouvement_solde_conge` :

| Critère | Table de valeurs typées (retenu) | Colonne JSONB |
|---|---|---|
| Filtre serveur paginé (D-I40) | `EXISTS` portable (JPA Criteria), indexé par type, comparaison numérique / date native | opérateurs `->>` + cast propres à PostgreSQL : SQL natif **non exécutable sur H2** ; index GIN peu utile pour min / max |
| Version optimiste des valeurs | table d'état dédiée, indépendante de la fiche | colonne sur `rh_collaborateur` réécrite par tout `save` JPA d'une entité chargée plus tôt (PUT fiche, import) : **perte de mise à jour** |
| Journal 5 ans | indépendant du stockage (table `rh_valeur_perso_journal` dans les deux cas) | idem |
| `MouvementSoldeConge` `@Immutable` | entité inchangée ; table annexe `@Immutable` insérée dans la même transaction | colonne à ajouter à l'entité et aux deux chemins d'insertion (JPA et JDBC groupé) |
| Import 5 000 × 30 | ≤ 150 000 lignes en JDBC groupé (`batchUpdate`, lots de 1 000) ; insertions et mises à jour séparées, portables | 5 000 mises à jour, plus légères, mais fusion JSON en SQL natif |
| Tests H2 / prod PostgreSQL 17 | SQL standard, mêmes tests partout | Testcontainers à introduire, ou chemins non testés |

Écritures portables : pas d'`on conflict … do update` (non pris en charge par H2) ; l'état courant est relu sous verrou
puis séparé en `insert` et `update` groupés. `on conflict do nothing` (déjà utilisé) sert seulement à créer la ligne
d'état. Le filtre texte utilise `texte_recherche like ? escape '\'` (motif normalisé en Java, `%` `_` `\` échappés).

### P2-4. Normalisation des noms : chiffres **gardés pour les champs personnalisés seulement**

- `ReconnaissanceEnTetes` gagne `normaliserComplet(String)` : mêmes opérations que `normaliser()` (minuscules, accents,
  « (obligatoire) », astérisques, ponctuation → espace, espaces réduits) **sans** la coupure au premier mot numérique, et
  `forme(boolean personnalise, String texte)` qui choisit l'une ou l'autre.
- **Champs fixes : inchangés** (`normaliser()` avec coupure). « Solde au 30/11 » reste reconnu comme SOLDE (D-S09.g),
  aucune forme stockée n'est recalculée, aucun test de la partie 1 ne change. Effet documenté : un synonyme saisi sur un
  champ fixe perd toujours ses chiffres (« Identifiant 10 » → « identifiant », refusé car déjà à MATRICULE) ; l'écran
  montre déjà la forme reconnue.
- **Champs personnalisés** : en-tête et synonymes stockés en forme **complète** (`forme_normalisee` =
  `normaliserComplet`) ; libellé comparé en forme complète pour l'unicité D-I28 (« Adresse 1 » ≠ « Adresse 2 » ;
  « Prime » = « Primé » ; « E-mail » = « E mail »). Longueur minimale (2) contrôlée sur la forme complète.
- **Unicité** (D-I20, D-I21, D-I34) inchangée dans son principe : dans un type, toutes les formes **stockées actives**
  (formes coupées des champs fixes, formes complètes des champs personnalisés) sont distinctes, par égalité de chaîne.
  « Mail » (personnalisé, forme « mail ») est refusé car « mail » est à COURRIEL.
- **Reconnaissance en deux temps** : (1) `normaliserComplet(en-tête du fichier)` cherché parmi les formes des champs
  personnalisés actifs ; (2) sinon `normaliser(en-tête)` cherché parmi les formes des champs fixes. Un champ personnalisé
  n'est reconnu que par **égalité exacte**, chiffres compris (« Taille 2 » ne désigne pas « Taille »).
- Conséquence acceptée : un champ personnalisé nommé exactement « Solde au 30/11 » capterait la colonne de ce nom (nom
  exact choisi par la RH) ; « Solde au 31/12 » reste à SOLDE. Noté dans les risques.

### P2-5. Écritures des définitions (C1 à C6) : `ChampPersoDefinitionService`

Classe neuve (package `com.hr.referentiel.champsperso`), **pas** dans `ImportParametrageService` (déjà 531 lignes). Le
gabarit commun de la partie 1 (§ 5 : résoudre le type, `FOR UPDATE` sur `rh_import_parametrage`, contrôles, version en
dernier, `modifie_le` → version +1, journal, invalidation après commit) est **extrait** dans
`importation/parametrage/ParametrageEcriture` et réutilisé par les deux services (refactorisation sans changement de
comportement, couverte par les tests existants de la partie 1).

Ordre des contrôles (G2), premier échec renvoyé :
- **C1 créer** : 404 `TYPE_IMPORT_INCONNU` → 400 `VERSION_OBLIGATOIRE`, `CORPS_INVALIDE` (`type_valeur` hors liste,
  `finalite` rognée hors 5–200, libellé absent) → 400 `CONFIRMATION_OBLIGATOIRE` (case absente ou `false`) → 400
  `LIBELLE_INVALIDE` (2–80, forme complète ≥ 2, premier caractère `= + - @ \t \r`) → 400 `REGLE_INVALIDE` (règle d'un
  autre type, bornes inversées ou hors limites, liste vide ou > 100 ; libellé de valeur hors 1–80 ou commençant par un
  caractère interdit → `LIBELLE_INVALIDE`) → 400 `LIBELLE_EN_DOUBLE` (libellé = libellé d'un champ fixe ou personnalisé,
  actif ou non, en forme complète ; en-tête = libellé en conflit avec une forme active ; deux valeurs de liste de même
  forme) → 422 `CHAMPS_PERSO_MAX` (30 par type, actifs et inactifs) → 409 `PARAMETRE_MODIFIE_ENTRE_TEMPS`.
  Effet : définition, valeurs de liste, ligne `rh_import_champ` (`en_tete` = libellé, `dans_modele = true`,
  `FACULTATIF`, `ordre` = max + 1) ; journal `CHAMP_CREE` (« Liste de valeurs ; finalité : … ; visible par le
  collaborateur : Non ; Confirmation : aucune donnée sensible »).
- **C2 modifier** (libellé, finalité, type, règles, visibilité ; `valeurs_liste` accepté **seulement** si le type
  devient `LISTE`) : 404 `CHAMP_INCONNU` (code fixe compris) → 400 comme C1 → 409 `PARAMETRE_UTILISE` (type changé et au
  moins une valeur ; « 1 fiche renseignée » / « 2 mouvements renseignés ») → 409 version. Le libellé changé ne change pas
  l'en-tête. Passage de `LISTE` à un autre type : valeurs de liste supprimées (journal). Une ligne de journal par
  attribut changé (`LIBELLE`, `FINALITE`, `TYPE_VALEUR`, `REGLE`, `VISIBLE_COLLABORATEUR`).
- **C3 désactiver / réactiver** : désactiver → `actif = false`, hors modèle, `FACULTATIF`. Réactiver → en-tête contrôlé
  contre les formes actives des autres champs (400 `LIBELLE_EN_DOUBLE`, champ laissé inactif) ; les synonymes du champ
  devenus en conflit pendant l'inactivité sont **désactivés** automatiquement (journal `SYNONYME_DESACTIVE`) ;
  `dans_modele = true`, `FACULTATIF`, `ordre` = max + 1.
- **C4 supprimer** : 409 `PARAMETRE_UTILISE` si une valeur existe (fiches ou mouvements, champ inactif compris), recompté
  sous verrou ; sinon suppression de la définition, des valeurs de liste, de la ligne `rh_import_champ` et des synonymes ;
  journal `CHAMP_SUPPRIME` avec `champ_libelle`. 204.
- **C5 / C6 valeurs de liste** : 404 `VALEUR_INTROUVABLE` ; 400 `LIBELLE_INVALIDE` / `LIBELLE_EN_DOUBLE` ; 422
  `REGLE_INVALIDE` au-delà de 100 ; 422 `LISTE_SANS_VALEUR_ACTIVE` (désactiver ou supprimer la dernière active) ; 409
  `PARAMETRE_UTILISE` (supprimer une valeur utilisée) ; `ordre` = nouvelle position 0..n-1, les autres décalées.
- **P2 (partie 1) adapté** : ensemble attendu = champs fixes + personnalisés **actifs** (taille variable) ;
  `ORDRE_INCOMPLET` calculé sur cet ensemble ; code d'un personnalisé inactif → 422 `CHAMP_INACTIF` (après les 400) ;
  personnalisés sans verrou, exigences possibles = 3 (collaborateurs) / 2 (soldes). Ancien en-tête d'un personnalisé →
  synonyme en forme complète.
- **P3 à P5** sur un personnalisé : forme complète ; origine `RH`.
- **P6 réinitialiser** : champs fixes comme en partie 1 ; personnalisés **actifs** : `en_tete` = libellé, dans le modèle,
  `FACULTATIF`, placés après les fixes par `cree_le` ; inactifs : hors modèle ; tous leurs synonymes supprimés ;
  définitions et valeurs jamais touchées.

Toute écriture de définition incrémente la version du type d'exactement 1. Attributs de journal nouveaux : annexe
A bis.1.

### P2-6. Valeurs d'une fiche (V1 à V4) : `ValeursPersoFicheService`

- **Contrôle d'une valeur** : classe pure `ControleValeurPerso.lire(DefinitionPerso, String brut, Mode)` →
  `ValeurTypee` ou `Probleme`, partagée par la fiche, la création, l'ajustement manuel et l'import (modes `SAISIE` /
  `IMPORT`). Texte : rogné, caractères de contrôle refusés (`VALEUR_INVALIDE`), ≤ `longueur_max`
  (`VALEUR_TROP_LONGUE`). Nombre : espaces (insécables compris) retirés, une seule virgule ou un seul point décimal,
  aucun séparateur de milliers (`FORMAT_INVALIDE`), `stripTrailingZeros().scale() ≤ decimales` sinon `VALEUR_INVALIDE`
  (jamais arrondi), bornes (`VALEUR_HORS_BORNES`) ; en mode `IMPORT`, un nombre Excel est arrondi à 9 décimales avant
  contrôle. Date : `jj/mm/aaaa` ou `aaaa-mm-jj` en mode strict (`DATE_INVALIDE`), date Excel en mode `IMPORT` (même
  lecture que `DATE_RECRUTEMENT`), bornes (`VALEUR_HORS_BORNES`). Liste : en `SAISIE` le **code** d'une valeur active ;
  en `IMPORT` le libellé ou le code, sans casse ni accents (`normaliserComplet`), d'une valeur active ; inconnue ou
  désactivée → `VALEUR_INCONNUE` (« … valeur « S » désactivée »). Messages : « <libellé> : … » (fiche, ajustement) ou
  « <en-tête réglé figé> : … » (import).
- **Formatage** : `FormatValeurPerso` (canonique : texte, `BigDecimal.stripTrailingZeros().toPlainString()`, ISO,
  code ; affichage : texte, virgule décimale, `jj/mm/aaaa`, libellé de liste). Le journal stocke l'affichage.
- **V2 écrire** (une transaction) : 404 `COLLABORATEUR_INTROUVABLE` → 403 `AUTO_MODIFICATION_INTERDITE` (fiche de
  l'appelant résolue par `CollaborateurConnecteService.resoudre(jwt)` ; RH sans fiche : autorisé) → 400
  `CORPS_INVALIDE` / `VERSION_OBLIGATOIRE` / `CHAMP_INCONNU` → 400 erreurs de valeur (**toutes** listées dans
  `erreurs[]`, rien n'est écrit) → 422 `CHAMP_INACTIF` → 409 `VALEURS_MODIFIEES_ENTRE_TEMPS`. Verrous, dans cet ordre :
  ligne `rh_import_parametrage` du type en **`PESSIMISTIC_READ`** (`FOR SHARE` : empêche un C2 / C4 concurrent de
  changer le type ou de supprimer le champ pendant l'écriture), puis ligne d'état de la fiche (`insert … on conflict do
  nothing` + `select … for update`). Les définitions sont relues **en base** sous ce verrou (pas le cache 30 s).
  Écriture partielle : seuls les codes envoyés ; `null` ou chaîne vide = effacer. Comparaison typée ; aucun changement
  réel → 200 sans journal ni version. Sinon une ligne de journal par champ changé (`origine = SAISIE`), version +1.
- **Création manuelle** (T6 règle 5) : façade `CreationCollaborateurService.creer(req, jwt)` `@Transactional` : contrôle
  des valeurs (champs actifs, facultatifs) **avant** la création, puis `ReferentielRhService.creerCollaborateur`
  (inchangé, rejoint la transaction), puis écriture des valeurs (`origine = CREATION`, version 1). Valeur invalide → 400,
  aucune fiche créée. `ReferentielController.creerCollaborateur` appelle la façade.
- **V1 lire** (back-office) : champs actifs dans l'ordre réglé de l'import (renseignés ou non, avec type, règles,
  valeurs de liste actives + la valeur inactive actuelle), puis `champs_desactives` (renseignés seulement), `version`,
  `modifiable` / `raison_non_modifiable` calculés depuis le JWT (DIRECTION, ou sa propre fiche). Le web ne décide pas
  seul du bouton « Modifier ».
- **V3 journal** : 20 par page, `horodatage DESC, id`.
- **V4 « moi »** : fiche **résolue depuis le JWT uniquement**, aucun paramètre lu (un `?collaborateurId=` est ignoré) ;
  seulement les champs **actifs**, **visibles par le collaborateur** et **renseignés**, ordre réglé, `valeur_affichee`
  formatée ; jamais de règle, de finalité ni de `champs_desactives`. Appelant sans fiche → 200 `{ "champs": [] }`.
  Filtrage fait dans le service avant la construction du DTO (G7 : la valeur n'est jamais sérialisée).

### P2-7. Liste filtrée (V5) et export CSV (V6) : un seul filtre serveur

Pour que la pagination, le total et l'export portent exactement sur ce que l'écran montre, **tous** les filtres de la
page « Fiches collaborateurs » passent côté serveur : record `FiltreCollaborateurs` (`q`, `direction`, `departement`,
`service`, `profil`, `statut`, `famille`, `niveau`, `metier`, `unite_identifiant` conservé, critères personnalisés) et
`CollaborateurSpecifications` (`CollaborateurRepository` étend `JpaSpecificationExecutor`). Sans paramètre nouveau, la
réponse est identique à aujourd'hui (les autres écrans qui appellent `getTousCollaborateurs` ne changent pas).

- `q` : chaque mot (normalisé comme `utils/filtres.ts` : sans accents, minuscules) doit figurer dans l'un des champs
  prénom, nom, matricule, courriel, poste, fonction, libellé / code d'unité, libellé de département ; SQL
  `translate(lower(champ), 'àâäáãåçéèêëíìîïñóòôöõúùûüýÿ', 'aaaaaaceeeeiiiinooooouuuuyy') like ? escape '\'`
  (`translate` existe sur PostgreSQL et H2). Au plus 10 mots.
- `direction` / `departement` / `service` : identifiants d'unité ; le serveur calcule leurs sous-arbres (unités en
  mémoire, quelques dizaines) et retient l'intersection → `unite.id in (…)`.
- `metier=A_COMPLETER` : statut ACTIF et famille ou niveau vide (D-E13.c).
- Critères personnalisés (annexe A bis, V5) : un `EXISTS` corrélé sur `rh_valeur_perso_fiche` par critère (`NOT EXISTS`
  pour « Vide ») ; 30 critères au plus ; champ inconnu, inactif, critère incompatible avec le type, nombre ou date
  illisible, `etat` combiné à une valeur → 400 `FILTRE_INVALIDE` (message qui nomme le champ).
- Tri inchangé (nom, prénom) **+ identifiant** en dernier critère (tri stable entre pages).
- **V6** : mêmes paramètres (sans `page` / `taille`), parcours par pages de 500 (mémoire bornée), CSV `;`, UTF-8 avec BOM,
  `CsvFrancais` + `EchappementFormule.proteger` sur **chaque** cellule (valeurs fixes comprises) ; colonnes fixes dans
  l'ordre de T6 règle 8 avec leurs libellés (statut, profil, famille, niveau en libellé), puis champs personnalisés
  actifs dans l'ordre réglé de l'import (en-tête de colonne = **libellé** du champ, pas l'en-tête d'import) ; valeur de
  liste inactive avec son libellé ; valeurs chargées par lot (`collaborateur_id in (…)` par page de 500). Nom
  `collaborateurs_AAAAMMJJ.csv` (date de Tunis).

### P2-8. Import des collaborateurs (T7)

- `importation/ChampPersoImport` : record qui implémente `ChampImport` (`name()` = code, `libelle()`, synonymes vides,
  `FACULTATIF` minimum, pas de verrou) ; `ReconnaissanceEnTetes(ParametrageImport)` ajoute les personnalisés actifs de
  l'instantané à sa liste de champs (`parNom` les accepte donc pour I5) et applique la reconnaissance en deux temps
  (P2-4).
- `ControleColonnes` itère sur `parametrage.champs()` (et non plus `type.champs()`) : `COLONNE_OBLIGATOIRE_ABSENTE`
  vaut pour un personnalisé `TOUJOURS`.
- `ImportCollaborateursService` : `valeurs()` et la table `enTetes` **ignorent** les codes qui ne sont pas des
  `ChampImport.Fiche` (plus de `valueOf` sur un code `PERSO_…`) ; l'évaluation des colonnes personnalisées d'une ligne
  est déléguée à une classe neuve `ChampsPersoLigne` (pure : exigence `CHAMP_OBLIGATOIRE`, `ControleValeurPerso` en mode
  `IMPORT`, comparaison typée avec la valeur actuelle, `Modification(code, libellé du champ, avant, après)` formatée,
  valeurs canoniques ajoutées à `LigneResultat.valeurs` sous leur code). Cellule vide : rien. Ligne de l'auteur
  (`auteurFicheId`) qui **changerait** une valeur personnalisée → erreur de ligne `AUTO_MODIFICATION_INTERDITE`.
  `Contexte.charger()` charge en une requête les valeurs actuelles des champs actifs du type (≤ 150 000 lignes).
- **Confirmation** : dans la transaction existante, après `appliquer` (identifiants des fiches connus),
  `ValeursPersoLot.ecrireFiches(…)` : `FOR SHARE` sur `rh_import_parametrage` du type ; relecture **en base** des codes
  existants et de leur type ; valeur d'un champ **supprimé**, dont le **type a changé**, ou d'une valeur de liste
  **supprimée** depuis l'analyse → non écrite, avertissement de ligne `CHAMP_PERSO_MODIFIE` ; champ ou valeur de liste
  seulement **désactivés** → écrite (paramétrage figé, D-I25) ; lignes d'état créées puis verrouillées par lots triés de
  500 (comme `SoldeLotService`) ; valeurs relues sous verrou, différences calculées, `insert` / `update` / journal
  (`origine = IMPORT`, `import_id`) en `batchUpdate` par 1 000 ; version +1 par fiche changée.
- Historique des imports : la `Modification` porte le libellé du champ (D-I26). Lignes en erreur (I7) : inchangé,
  formules neutralisées par le mécanisme existant.
- `ImportService` : seulement `champs_disponibles` (`personnalise`, `type_valeur`, `type_valeur_libelle`) et le passage
  des avertissements de confirmation ; **+ 30 lignes au plus**, aucune logique nouvelle dans la classe.

### P2-9. Soldes (T8) : valeurs sur le mouvement « Ajustement »

- **Import** : `ImportSoldesService` ignore les codes non `ChampImport.Solde` dans ses tables, délègue à
  `ChampsPersoLigne` (sans valeur actuelle : pas d'« Inchangée » par champ). `SoldeLotService.Resultat` gagne
  `UUID mouvementId` (null si aucun mouvement) ; après `ajuster`, `ValeursPersoLot.ecrireMouvements(…)` insère en lot
  dans `rh_valeur_perso_mouvement` (mêmes exclusions « supprimé / retypé » que P2-8). Ligne devenue « Inchangé » avec
  des valeurs → avertissement `VALEURS_PERSO_NON_ENREGISTREES`. `AUTO_AJUSTEMENT_INTERDIT` inchangé.
- **Ajustement manuel** : `AjustementRequest` gagne `champs_personnalises` (facultatif, D-I33) ; `AjustementSoldeService`
  contrôle ces valeurs **après** les contrôles D-S08.a et avant les 422 existants, sous `FOR SHARE` du type ; insertion
  dans la même transaction que le mouvement (`SoldeCongeService.enregistrer` rend le mouvement).
- **Immuabilité** : aucune méthode d'écriture sur `rh_valeur_perso_mouvement` hors insertion ; aucun endpoint `PUT` /
  `DELETE` de mouvement (405) ; un champ `soldes` utilisé ne se supprime pas (409).
- **Lecture** : `ValeursPersoMouvementLecture.pour(List<UUID> mouvementIds, Vue)` (une requête par page) ; vue
  `BACKOFFICE` → `champs_personnalises` (actifs, ordre réglé) + `champs_desactives` ; vue `COLLABORATEUR` → actifs et
  visibles seulement, clé `champs_desactives` **absente**. Export des mouvements : une colonne par champ `soldes` actif,
  ordre réglé, cellules protégées.

### P2-10. Configuration et déploiement

- Tables neuves et une colonne nullable (`rh_import_champ_journal.champ_libelle`) créées par `ddl-auto=update` ; aucun
  runner de CHECK ; aucun amorçage (aucun champ personnalisé par défaut).
- Mise à jour d'un serveur : sauvegarde de `referentiel_rh_db`, reconstruire `svc-referentiel-rh` et `web`, redémarrer
  la gateway puis `sirh-web-1`, supprimer les instances Eureka `DOWN` (procédure habituelle). Mobile : build Codemagic.

## Alternatives écartées (partie 2)

- **Colonne JSONB sur la fiche et le mouvement** : SQL natif non testable sur H2, perte de mise à jour par les `save` JPA
  existants, filtre min / max mal indexé (tableau P2-3).
- **Une seule table de valeurs pour fiches et mouvements** (`objet_type`) : mélange une donnée modifiable et une donnée
  immuable ; l'`@Immutable` ne peut pas porter sur la moitié des lignes.
- **EAV avec une seule colonne texte** : comparaisons numériques et de dates impossibles en SQL sans cast fragile.
- **`@Version` sur `Collaborateur`** : imposerait une version au PUT de fiche existant et ferait échouer l'import sur une
  fiche modifiée en parallèle pour une raison étrangère aux champs personnalisés.
- **Chiffres gardés pour tous les champs** : casse « Solde au 30/11 » (D-S09.g) et les formes déjà stockées de la
  partie 1 (recalcul et conflits à migrer).
- **Filtre de liste gardé dans le navigateur, seuls les critères personnalisés côté serveur** : l'export ne pourrait pas
  reproduire la recherche et la cascade d'unités ; total et pagination incohérents.
- **Export en `POST` avec la liste des identifiants affichés** : corps sans limite, contournement du filtre serveur.
- **Valeurs personnalisées dans `CollaborateurResponse`** (`GET /collaborateurs/{id}`) : réponse mise en cache et lue par
  d'autres écrans ; risque de fuite G7. Endpoint dédié V1.

## Conséquences (partie 2)

- La liste des champs fixes reste dans le code ; la RH ajoute jusqu'à 30 champs par type sans développement.
- La page « Fiches collaborateurs » passe à la pagination serveur (V5) ; `getTousCollaborateurs` reste pour les autres
  écrans.
- `ParametrageImport` et `ChampDisponible` s'élargissent (champs ajoutés). `SoldeLotService.Resultat` gagne un composant
  (appelants et tests à adapter).
- `ImportParametrageService` **diminue** (extraction de `ParametrageEcriture`) ; `ImportService` ne grossit que de la
  glue (≤ 30 lignes).
- Tout fichier produit (export liste, export mouvements, lignes en erreur) neutralise les formules à l'écriture ; la
  base garde la valeur saisie.

## Sécurité / RGPD (STRIDE, partie 2)

| Menace | Analyse | Parade |
|---|---|---|
| Usurpation | Un collaborateur appelle V4 avec l'identifiant d'un autre (IDOR) | V4 sans paramètre : fiche tirée du JWT (`CollaborateurConnecteService`) ; paramètre ignoré ; test dédié |
| Usurpation | Auteur du journal falsifié | Auteur tiré du JWT (`AuteurParametreResolver`) pour C1–C6, V2, création, ajustement, import ; aucun champ auteur en entrée |
| Altération | RH / ADMIN qui modifie ses propres valeurs (fiche ou import) | 403 `AUTO_MODIFICATION_INTERDITE` (V2), erreur de ligne (import) ; comparaison fiche du JWT / fiche visée côté serveur |
| Altération | Écrasement concurrent RH / RH ou import / fiche | Version des valeurs par fiche + verrou de ligne d'état ; import : verrou par lots triés et relecture sous verrou |
| Altération | Changement de type ou suppression pendant une écriture de valeur | `FOR SHARE` sur `rh_import_parametrage` côté valeurs, `FOR UPDATE` côté définitions ; à la confirmation, valeurs d'un champ supprimé / retypé non écrites |
| Altération | Injection de formule (CSV / xlsx) | Libellés de champ et de valeur de liste refusés s'ils commencent par `= + - @ \t \r` ; `EchappementFormule.proteger` sur chaque cellule de l'export liste, de l'export mouvements et des lignes en erreur ; valeurs stockées telles quelles |
| Altération | Valeur d'un mouvement modifiée après coup | Entité `@Immutable`, colonnes `updatable=false`, aucun endpoint d'écriture (405) |
| Répudiation | « Ce n'est pas moi qui ai changé cette valeur » | Journal des valeurs ajout seul, 5 ans, auteur + compte + origine + import ; journal P7 des définitions, confirmation « aucune donnée sensible » datée et nominative |
| Divulgation | Valeur non visible renvoyée au collaborateur | Filtrage `actif && visible_collaborateur && renseigné` avant le DTO (V4, mouvements « moi ») ; tests sur l'absence de clé |
| Divulgation | Responsable (RO) qui lit les valeurs de son équipe | V1, V3, V5, V6 en `BACKOFFICE_LECTURE` : RO / collaborateur → 403 (D-I36) |
| Divulgation | Données sensibles (art. 9 RGPD, loi 2004-63) | Case obligatoire, finalité 5–200 (minimisation), journal ; pas de filtre automatique (décision client, juriste à consulter) ; aucune valeur dans les logs applicatifs |
| Divulgation | Export massif par DIRECTION | Autorisé par la matrice (D-I37) ; log d'accès sans valeur (utilisateur, filtres, nombre de lignes) |
| Divulgation | Valeur effacée encore présente | Le journal garde avant / après 5 ans (C-L-02) : à présenter au client avec Q-P4 (droit à l'oubli = besoin séparé) |
| Déni de service | Filtres ou exports coûteux | 30 critères, 10 mots de recherche, `taille` ≤ 200, export par pages de 500, index par type ; 30 champs × 100 valeurs par type |
| Élévation | DIRECTION / RO / collaborateur qui écrit | `@PreAuthorize` sur chaque méthode (`BACKOFFICE_ECRITURE` pour C1–C6, V2 ; `BACKOFFICE_LECTURE` pour V1, V3, V5, V6 ; `isAuthenticated()` pour V4) ; aucun `/internal` |

## Risques et dettes (partie 2)

- **Création de fiche dupliquée** (`ImportCollaborateursService` / `ReferentielRhService`) : **ne pas l'aggraver**. Les
  valeurs personnalisées passent par `ValeursPersoLot` (import) et la façade `CreationCollaborateurService` (saisie),
  qui partagent `ControleValeurPerso` ; ni `appliquerChamps` ni `ReferentielRhService.creerCollaborateur` ne reçoivent de
  logique personnalisée.
- **Taille des classes** : `ImportService` (824 lignes) ≤ 30 lignes ajoutées, aucune règle nouvelle ;
  `ImportCollaborateursService` (668) délègue à `ChampsPersoLigne`, ≤ 40 lignes ajoutées ; `ImportParametrageService`
  réduit par l'extraction de `ParametrageEcriture` ; chaque classe neuve ≤ 400 lignes.
- Le passage de la page « Fiches collaborateurs » à la pagination serveur change son fonctionnement : recette dédiée
  (filtres actuels conservés dans l'URL, compteur « profil métier à compléter » par `metier=A_COMPLETER&taille=1`).
- Recherche `q` côté serveur : la table d'accents de `translate` couvre le français (pas `œ`, `æ`) ; écart mineur
  possible avec l'ancienne recherche du navigateur (NFD).
- Un champ personnalisé nommé exactement comme un en-tête habituel avec chiffres (« Solde au 30/11 ») capte cette colonne
  (P2-4) : rare, nom choisi par la RH ; à signaler dans l'aide de l'écran.
- `FOR SHARE` du type pendant une confirmation d'import (quelques secondes) retarde les écritures de paramétrage de ce
  type : accepté.
- Volumétrie : confirmation de 5 000 × 30 valeurs nouvelles = 150 000 insertions + 150 000 lignes de journal en JDBC
  groupé (quelques secondes sur PostgreSQL, objectif de confirmation existant ≤ 60 s) ; l'analyse reste ≤ 10 s (T7-12).
- Décisions prises ici, à faire confirmer (§ 23) : code jamais réattribué ; synonymes en conflit désactivés à la
  réactivation d'un champ ; valeur d'un champ retypé ou d'une valeur de liste supprimée depuis l'analyse non écrite ; RH
  sans fiche autorisé à modifier les valeurs ; V4 renvoie une liste vide pour un appelant sans fiche ; colonne d'export =
  libellé du champ (pas l'en-tête d'import) ; message `CHAMP_PERSO_MODIFIE` (le libellé de T7-9 dit « supprimé depuis
  l'analyse » : à aligner par le rédacteur Jira).
- Dettes existantes non traitées : liste des soldes filtrée en mémoire ; mot de passe initial en clair sur
  `rh.notifications` ; exports CSV antérieurs sans échappement (l'export des mouvements est corrigé au passage).

## Plan d'implémentation (partie 2)

**Règle graphify (obligatoire pour chaque développeur)** : avant tout grep ou lecture de code, lancer
`graphify query "<question>"` (ex. `graphify query "Collaborateur entity MouvementSoldeConge ajustement"`,
`graphify query "ParametrageImport ReconnaissanceEnTetes normaliser"`, `graphify query "MonDossier mobile collaborateur
moi"`), puis `graphify explain "<concept>"` / `graphify path "<A>" "<B>"` ; ne lire les fichiers bruts que pour les
lignes localisées par le graphe ou une lecture précédente. Après modification du code : `graphify update .`.

Ordre : lot BACK (T5 → T6 → T7 → T8, un seul agent) ; lots WEB et MOBILE **en parallèle**, codés sur l'annexe A bis
sans attendre le BACK. Dossiers **disjoints** : `svc-referentiel-rh/**` (dev-backend) / `rh-admin-web/**` (dev-web) /
`rh_mobile_app/**` (dev-mobile). Personne ne modifie `gateway/`, `deploy/`, `docs/`, `rh_mobile_app_temp/`.

### Lot BACK — `dev-backend` : `svc-referentiel-rh/**` (T5, T6, T7, T8 serveur)

Étape 0 (refactorisation sans changement de comportement, tests de la partie 1 verts) : extraire
`importation/parametrage/ParametrageEcriture` (verrou du type, contrôle de version, `modifie_le`, journal, invalidation)
depuis `ImportParametrageService`.

Nouveaux fichiers, package `com.hr.referentiel.champsperso` :
- `TypeValeurPerso` (+ `TypeValeurPersoConverter`), `OrigineValeurPerso` (+ converter) ; entités `ChampPerso`,
  `ChampPersoValeurListe`, `ValeurPersoFiche`, `ValeurPersoFicheEtat`, `ValeurPersoMouvement` (`@Immutable`),
  `ValeurPersoJournal` (`@Immutable`) et leurs repositories.
- `CodesChampPerso` (génération des codes), `ChampPersoValidation` (pure : corps, règles, libellés, unicité en forme
  complète), `ChampPersoDefinitionService` (C1–C6).
- `ValeurTypee`, `ControleValeurPerso` (pur, modes `SAISIE` / `IMPORT`), `FormatValeurPerso`.
- `ValeursPersoFicheService` (V1–V4, valeurs initiales de la création), `ValeursPersoLot` (JDBC groupé : fiches et
  mouvements), `ValeursPersoMouvementLecture`, `ValeursPersoDtos` (records snake_case de l'annexe A bis).
- `web/ChampPersoController` (C1–C6), `web/ValeursPersoController` (V1–V4).
- `service/FiltreCollaborateurs` (record + lecture des paramètres `cp.*`), `service/CollaborateurSpecifications`,
  `service/RechercheCollaborateursService` (V5), `service/ExportCollaborateursService` (V6),
  `service/CreationCollaborateurService` (façade de création).
- `importation/ChampPersoImport`, `importation/ChampsPersoLigne`.

Fichiers modifiés :
- `importation/ReconnaissanceEnTetes` (`normaliserComplet`, `forme`, personnalisés de l'instantané, reconnaissance en
  deux temps) ; `importation/ControleColonnes` (itère `parametrage.champs()`).
- `importation/parametrage/ParametrageImport` (`personnalise`, `definitionsPerso`), `ParametrageImportCache`,
  `ImportParametrageService` (P2 ensemble attendu, `CHAMP_INACTIF`, P3–P5 forme par genre, P6), `ImportParametrageDtos`
  / `ImportParametrageVues` (P1 élargi), `ImportChampJournal` (+ `champ_libelle`), `ImportChampsJournal` (attributs et
  libellés A bis.1).
- `importation/ImportCollaborateursService` (codes non fixes ignorés, délégation, contexte des valeurs, appel de
  `ValeursPersoLot` dans `appliquer`), `importation/ImportSoldesService`, `importation/ImportService` (glue ≤ 30 lignes),
  `importation/ImportDtos` (`ChampDisponible`).
- `service/SoldeLotService` (`Resultat.mouvementId`), `service/AjustementSoldeService`, `service/SoldeCongeLectureService`
  (valeurs des mouvements, colonnes d'export), DTO `AjustementRequest` et `Mouvement`.
- `repository/CollaborateurRepository` (`JpaSpecificationExecutor`), `web/ReferentielController` (V5 : nouveaux
  paramètres ; V6 ; création via la façade), `dto/CollaborateurCreationRequest` (`champs_personnalises`).

Tests attendus :
- `ChampPersoDefinitionServiceTest` : T5-1 à T5-16 (code généré et jamais réattribué, règles par type, espace de noms
  commun en forme complète, « Adresse 1 » ≠ « Adresse 2 », « Mail » refusé, 30 max, type figé dès une valeur,
  désactivation / réactivation avec synonymes en conflit, suppression, valeurs de liste, réinitialisation, journal en
  clair, version +1, 409 sans écriture).
- `ReconnaissanceEnTetesTest` (ajouts) : « Solde au 30/11 » → SOLDE inchangé ; personnalisé reconnu par égalité exacte ;
  « Taille 2 » non reconnu pour « Taille » ; personnalisé inactif ignoré.
- `ControleValeurPersoTest` : T6-2, T6-3 (« 4 5 », « 1 000 », « 42,0 », « 42,01 », « 31/02/2026 », date Excel en
  import, valeur de liste désactivée).
- `ValeursPersoFicheServiceTest` (`@DataJpaTest` H2) : écriture partielle, effacement, aucun changement sans journal,
  version / 409, `AUTO_MODIFICATION_INTERDITE` (RH, ADMIN), champ inactif / inconnu, règle resserrée sans revalidation
  (T6-10), création avec valeurs.
- `RechercheCollaborateursServiceTest` (H2) : critères par type, « Vide » / « Renseigné », combinaison avec unité et `q`
  sans accents, total exact sur plusieurs pages, `FILTRE_INVALIDE`.
- `ExportCollaborateursServiceTest` : BOM, `;`, ordre des colonnes, formats, « =1+1 » → « '=1+1 », aucun UUID ni code.
- `ValeursPersoControllerSecurityTest`, `ChampPersoControllerSecurityTest` (`@WebMvcTest`) : matrice A bis.3 complète
  (sans JWT, COLLABORATEUR, RESPONSABLE, RH, ADMIN, DIRECTION) ; V4 qui ignore `collaborateurId` et n'émet pas la clé d'un
  champ non visible.
- `ImportServiceTest` / `ImportCollaborateursServiceTest` / `ImportSoldesServiceTest` (ajouts) : T7-1 à T7-11, T8-1 à
  T8-4 (modèle, colonne d'un champ inactif ignorée, exigences, comparaison typée, auto-modification, champ supprimé /
  retypé depuis l'analyse, valeur de liste désactivée écrite, journal « Import du … », ligne « Inchangé »).
- `AjustementSoldeServiceTest` (ajouts) : T8-5, T8-6 ; lecture des mouvements : vue collaborateur sans champ non visible
  ni `champs_desactives`, export des mouvements.
- `ImportPerformanceTest` (ajout) : 5 000 lignes × 30 champs personnalisés analysées ≤ 10 s.
- Non-régression : `mvn -B -pl svc-referentiel-rh test` vert (tests actuels + nouveaux) ; sans champ personnalisé, la
  partie 1 et l'import fdb20cf sont inchangés.

### Lot WEB — `dev-web` : `rh-admin-web/**` (T8 écran, T9, T10, T11)

- `src/api/champsPersoApi.ts` (nouveau) : C1–C6, types `ChampPerso`, `ReglesPerso`, `ValeurListe`.
- `src/api/importParametrageApi.ts` : champs de P1 élargis (`personnalise`, `actif`, `type_valeur`, `regles`,
  `valeurs_liste`, `utilisations`, `supprimable`, `type_modifiable`…).
- `src/api/valeursPersoApi.ts` (nouveau) : V1–V3, construction des paramètres `cp.*` de V5, téléchargement V6.
- `src/api/rhClient.ts` : `getCollaborateursPageFiltree(filtre, page, taille)` (V5) ; `getTousCollaborateurs` inchangé.
- `src/api/congesApi.ts` : `champs_personnalises` de l'ajustement et des mouvements.
- `src/api/importsApi.ts` : `ChampDisponible.personnalise`, `type_valeur_libelle`.
- `src/components/SaisieValeurPerso.tsx` (nouveau) : saisie selon le type (texte, nombre à virgule, `SaisieDate`, liste
  des valeurs actives + valeur inactive actuelle « (inactive) »), « Effacer », erreur serveur sous le champ.
- `src/pages/parametres/ChampsPersoPanel.tsx`, `ChampPersoFormDialog.tsx`, `ValeursListeEditor.tsx` (nouveaux, T9) ;
  `src/pages/ParametresPage.tsx` (entrée « Champs personnalisés », domaine ORGANISATION, onglets Collaborateurs / Soldes
  de congé / Historique) ; `src/pages/parametres/ImportChampsTable.tsx` (pastille « Personnalisé », pas de cadenas,
  inactifs absents).
- `src/detail/contenus/ChampsPersoCarte.tsx`, `ChampsPersoJournalDialog.tsx` (nouveaux, T10), branchés dans
  `CollaborateurContenu.tsx` (4e carte « Informations complémentaires », bouton selon `modifiable` de V1, bloc
  « Champs désactivés », historique V3).
- `src/pages/CollaborateursPage.tsx` : pagination et filtres **serveur** (V5) avec les filtres actuels gardés dans l'URL ;
  `src/components/FiltresChampsPerso.tsx` (nouveau) dans `BarreFiltres` ; bouton « Exporter (CSV) » (RH / ADMIN /
  DIRECTION) ; section repliable « Informations complémentaires » du formulaire de création.
- `src/pages/conges/AjusterSoldeDialog.tsx` et `src/detail/contenus/CongesOnglet.tsx` (T8 écran : champs du formulaire,
  valeurs et « Champs désactivés » dans la liste des mouvements).
- `src/pages/imports/AssistantImport.tsx` (T11 : « (personnalisé) », avertissements nouveaux, « Retenir comme synonyme »).
- Aucun code `PERSO_…` ni UUID à l'écran ; 409 → message, rechargement, saisie rappelée ; `ConfirmDialog` pour
  désactiver / supprimer / réinitialiser ; `npx tsc --noEmit -p .` et `npx vite build` verts ; 1024 px sans débordement.

### Lot MOBILE — `dev-mobile` : `rh_mobile_app/**` (T12)

- `lib/features/dossier/data/champs_perso_models.dart` (nouveau) : `ChampPersoAffiche.fromJson` tolérant (clé absente =
  liste vide).
- `lib/features/dossier/data/dossier_repository.dart` : `mesChampsPerso()` →
  `GET {prefixReferentiel}/collaborateurs/moi/champs-personnalises` (aucun identifiant envoyé).
- `lib/features/dossier/providers/dossier_providers.dart` : `mesChampsPersoProvider` (`FutureProvider.autoDispose` :
  rien gardé au-delà de l'écran).
- `lib/features/dossier/presentation/informations_complementaires_section.dart` (nouveau) et `mon_dossier_screen.dart` :
  section « Informations complémentaires » absente si la liste est vide, erreur réseau → message + « Réessayer »,
  lecture seule, `Semantics` sur chaque ligne.
- `lib/features/conges/data/solde_models.dart` (`MouvementSolde.champsPersonnalises`) et
  `lib/features/conges/presentation/mon_solde_screen.dart` (`LigneMouvement` : lignes « Libellé : valeur » sous un
  ajustement).
- Flutter 3.29.2 (Codemagic) : aucune API plus récente (pas d'`initialValue` de `DropdownButtonFormField`, etc.).
- Tests : `test/features/dossier/champs_perso_models_test.dart`,
  `test/features/dossier/informations_complementaires_section_test.dart` (360 × 640 sans débordement, section absente si
  vide), `test/features/conges/` (mouvement avec valeurs) ; `flutter analyze` et `flutter test` verts sur Codemagic
  (Flutter absent du poste).

### Recette (rappel pour le testeur)

Matrice A bis.3 avec DRH01, RH02, ADM01, DG01, CS-WEB, COL-WEB ; champs préfixés « [RECETTE-PERSO] » (G8) ; en fin de
recette : supprimer les champs jamais utilisés, désactiver les autres, **réinitialiser** les deux types (P6) ; ne
toucher qu'à ses propres données ; V4 avec un `collaborateurId` d'un autre ; export ouvert dans Excel ; 5 000 lignes ×
30 champs.

---

## Annexe A bis — Contrat d'API des champs personnalisés (définitif)

### A bis.0 Conventions

Celles de A.0 (snake_case, erreurs `{ code, erreur, message, champ? }`, ordre G2, 401 corps vide). En plus : une erreur
de valeur porte `champ` = code du champ personnalisé, et V2 / création / ajustement renvoient **toutes** les erreurs de
valeur : `"erreurs": [ { "champ": "PERSO_POINTURE", "code": "VALEUR_INVALIDE", "message": "Pointure : 0 décimale autorisée." } ]`
(le premier élément est repris en `code` / `message` / `champ` de tête). Nombres et dates **en chaîne** dans les
réponses (`"valeur": "42.5"`, `"2026-11-15"`) pour éviter tout arrondi flottant ; `valeur_affichee` déjà formatée
(« 42,5 », « 15/11/2026 », libellé de liste). Bases : `P = /api/referentiel/v1/parametres/imports/{type}` ;
`F = /api/referentiel/v1/collaborateurs`. Routes gateway existantes, aucune à ajouter.

### A bis.1 Énumérations

| Énumération | Valeurs (libellé) |
|---|---|
| `type_valeur` | `TEXTE` (Texte), `NOMBRE` (Nombre), `DATE` (Date), `LISTE` (Liste de valeurs) |
| `origine` (journal des valeurs) | `SAISIE` (Saisie sur la fiche), `IMPORT` (Import du jj/mm/aaaa), `CREATION` (Création de la fiche) |
| `attribut` (journal P7, ajouts) | `CHAMP_CREE` (Champ créé), `LIBELLE` (Libellé), `FINALITE` (Finalité), `TYPE_VALEUR` (Type), `REGLE` (Règle), `VISIBLE_COLLABORATEUR` (Visible par le collaborateur), `VALEUR_LISTE_AJOUTEE` (Valeur de liste ajoutée), `VALEUR_LISTE_MODIFIEE` (Valeur de liste modifiée), `VALEUR_LISTE_DESACTIVEE` (Valeur de liste désactivée), `VALEUR_LISTE_REACTIVEE` (Valeur de liste réactivée), `VALEUR_LISTE_SUPPRIMEE` (Valeur de liste supprimée), `CHAMP_DESACTIVE` (Champ désactivé), `CHAMP_REACTIVE` (Champ réactivé), `CHAMP_SUPPRIME` (Champ supprimé) |

### A bis.2 Endpoints

| # | Méthode, chemin | `@PreAuthorize` | Entrée | 2xx | Erreurs (ordre G2) |
|---|---|---|---|---|---|
| C1 | `POST P/champs-personnalises` | `BACKOFFICE_ECRITURE` | `CreerChampPerso` | 201 `Parametrage` | 404 `TYPE_IMPORT_INCONNU` → 400 `VERSION_OBLIGATOIRE`, `CORPS_INVALIDE`, `CONFIRMATION_OBLIGATOIRE`, `LIBELLE_INVALIDE`, `REGLE_INVALIDE`, `LIBELLE_EN_DOUBLE` → 422 `CHAMPS_PERSO_MAX` → 409 `PARAMETRE_MODIFIE_ENTRE_TEMPS` |
| C2 | `PUT P/champs-personnalises/{code}` | `BACKOFFICE_ECRITURE` | `ModifierChampPerso` | 200 `Parametrage` | 404 `CHAMP_INCONNU` → 400 (comme C1, sans la confirmation) → 409 `PARAMETRE_UTILISE` (type changé avec valeurs) → 409 version |
| C3a | `POST P/champs-personnalises/{code}/desactiver` | `BACKOFFICE_ECRITURE` | `{ "version": 9 }` | 200 `Parametrage` | 404 → 400 `VERSION_OBLIGATOIRE` → 409 version |
| C3b | `POST P/champs-personnalises/{code}/reactiver` | `BACKOFFICE_ECRITURE` | `{ "version": 9 }` | 200 `Parametrage` | 404 → 400 `VERSION_OBLIGATOIRE`, `LIBELLE_EN_DOUBLE` → 409 version |
| C4 | `DELETE P/champs-personnalises/{code}?version=9` | `BACKOFFICE_ECRITURE` | — | 204 | 404 → 400 `VERSION_OBLIGATOIRE` → 409 `PARAMETRE_UTILISE` (« 3 fiches renseignées » / « 2 mouvements renseignés ») → 409 version |
| C5a | `POST P/champs-personnalises/{code}/valeurs` | `BACKOFFICE_ECRITURE` | `{ "libelle": "XXL", "version": 9 }` | 201 `Parametrage` | 404 `CHAMP_INCONNU` → 400 `VERSION_OBLIGATOIRE`, `CORPS_INVALIDE` (champ non `LISTE`), `LIBELLE_INVALIDE`, `LIBELLE_EN_DOUBLE` → 422 `REGLE_INVALIDE` (> 100) → 409 version |
| C5b | `PUT P/champs-personnalises/{code}/valeurs/{code_valeur}` | `BACKOFFICE_ECRITURE` | `{ "libelle": "XL", "ordre": 2, "version": 10 }` | 200 `Parametrage` | 404 `VALEUR_INTROUVABLE` → 400 `VERSION_OBLIGATOIRE`, `CORPS_INVALIDE` (ordre hors 0..n-1), `LIBELLE_INVALIDE`, `LIBELLE_EN_DOUBLE` → 409 version |
| C6a | `POST P/champs-personnalises/{code}/valeurs/{code_valeur}/desactiver` · `…/reactiver` | `BACKOFFICE_ECRITURE` | `{ "version": 10 }` | 200 `Parametrage` | 404 → 400 `VERSION_OBLIGATOIRE` → 422 `LISTE_SANS_VALEUR_ACTIVE` → 409 version |
| C6b | `DELETE P/champs-personnalises/{code}/valeurs/{code_valeur}?version=10` | `BACKOFFICE_ECRITURE` | — | 204 | 404 → 400 → 422 `LISTE_SANS_VALEUR_ACTIVE` → 409 `PARAMETRE_UTILISE` → 409 version |
| P1–P7 | (partie 1) | inchangé | P2 : tous les champs **actifs**, fixes et personnalisés | inchangé | P2 : + 422 `CHAMP_INACTIF` |
| P8 | `POST P/champs` · `DELETE P/champs/{code}` | `BACKOFFICE_ECRITURE` | — | — | 405 `PARAMETRE_COMPORTEMENT` **inchangé** (champs fixes) |
| V1 | `GET F/{id}/champs-personnalises` | `BACKOFFICE_LECTURE` | — | 200 `ValeursFiche` | 404 `COLLABORATEUR_INTROUVABLE` |
| V2 | `PUT F/{id}/champs-personnalises` | `BACKOFFICE_ECRITURE` | `{ "version": 3, "valeurs": { "PERSO_POINTURE": "42", "PERSO_CODE_VESTIAIRE": null } }` | 200 `ValeursFiche` | 404 → 403 `AUTO_MODIFICATION_INTERDITE` → 400 `CORPS_INVALIDE`, `VERSION_OBLIGATOIRE`, `CHAMP_INCONNU`, `FORMAT_INVALIDE`, `DATE_INVALIDE`, `VALEUR_INVALIDE`, `VALEUR_TROP_LONGUE`, `VALEUR_HORS_BORNES`, `VALEUR_INCONNUE` → 422 `CHAMP_INACTIF` → 409 `VALEURS_MODIFIEES_ENTRE_TEMPS` |
| V3 | `GET F/{id}/champs-personnalises/journal?page=0&taille=20` | `BACKOFFICE_LECTURE` | `taille` 1–100 | 200 `PageJournalValeurs` | 404 |
| V4 | `GET F/moi/champs-personnalises` | `isAuthenticated()` | aucun paramètre lu | 200 `MesChamps` | — (sans fiche : `{ "champs": [] }`) |
| V5 | `GET F?page=&taille=&q=&direction=&departement=&service=&profil=&statut=&famille=&niveau=&metier=A_COMPLETER&unite_identifiant=&cp.<CODE>.<critère>=` | `BACKOFFICE_LECTURE` (inchangé) | voir ci-dessous | 200 `PageReferentielResponse<CollaborateurResponse>` (format actuel) | 400 `FILTRE_INVALIDE` |
| V6 | `GET F/export?…` (mêmes filtres que V5, sans `page` / `taille`) | `BACKOFFICE_LECTURE` | — | 200 `text/csv;charset=UTF-8`, `Content-Disposition: attachment; filename="collaborateurs_AAAAMMJJ.csv"` | 400 `FILTRE_INVALIDE` |
| CR | `POST F` (création, existant) | `BACKOFFICE_ECRITURE` | + `"champs_personnalises": { "PERSO_TAILLE_DE_TENUE": "L" }` (facultatif) | 201 (inchangé) | + 400 erreurs de valeur, `CHAMP_INCONNU` ; 422 `CHAMP_INACTIF` |
| AJ | `POST /api/rh/v1/admin/conges/soldes/{id}/ajustements` (existant) | `BACKOFFICE_ECRITURE` | + `"champs_personnalises": { "PERSO_REFERENCE_DECISION": "DG-2026-15" }` (facultatif) | 200 (inchangé) | après les 400 existants : 400 erreurs de valeur, `CHAMP_INCONNU` ; 422 `CHAMP_INACTIF` ; puis 422 / 409 existants |
| MV | `GET /api/rh/v1/admin/conges/soldes/{id}/mouvements` · `…/mouvements/export` (existants) | `BACKOFFICE_LECTURE` | — | + `champs_personnalises`, `champs_desactives` par mouvement ; export : + une colonne par champ `soldes` actif | inchangé |
| MM | `GET /api/rh/v1/conges/solde/moi/mouvements` (existant) | `hasRole('USER')` (inchangé) | — | + `champs_personnalises` (actifs **et** visibles) ; **pas** de clé `champs_desactives` | inchangé |
| — | `PUT` / `DELETE` sur un mouvement | — | — | — | 405 (aucun endpoint) |

Critères `cp.<CODE>.<critère>` de V5 / V6 (champ actif du type `collaborateurs`) :

| Type | Critères | Exemple |
|---|---|---|
| TEXTE | `contient` (sans casse ni accents, 1–100 caractères) | `cp.PERSO_CODE_VESTIAIRE.contient=b-1` |
| NOMBRE | `min`, `max` (inclusifs, virgule ou point) | `cp.PERSO_POINTURE.min=30&cp.PERSO_POINTURE.max=45` |
| DATE | `du`, `au` (inclusifs, `aaaa-mm-jj`) | `cp.PERSO_DATE_DE_BADGE.du=2026-11-01&cp.PERSO_DATE_DE_BADGE.au=2026-11-30` |
| LISTE | `valeurs` (codes séparés par des virgules, OU ; valeur inactive acceptée) | `cp.PERSO_TAILLE_DE_TENUE.valeurs=M,L` |
| tous | `etat` = `RENSEIGNE` \| `VIDE` (exclusif des autres critères du champ) | `cp.PERSO_TAILLE_DE_TENUE.etat=VIDE` |

`FILTRE_INVALIDE` : code inconnu ou inactif, critère incompatible avec le type, valeur illisible, code de valeur de liste
inconnu, `etat` combiné, plus de 30 critères, `q` de plus de 10 mots. Un filtre fixe d'égalité sans correspondance
(`profil=XYZ`) renvoie simplement une liste vide.

Corps :

```json
CreerChampPerso {
  "version": 8, "libelle": "[RECETTE-PERSO] Taille de tenue", "finalite": "Commande des tenues de l'événement",
  "type_valeur": "LISTE",
  "regles": { "longueur_max": null, "decimales": null, "minimum": null, "maximum": null, "date_min": null, "date_max": null },
  "valeurs_liste": [ { "libelle": "S" }, { "libelle": "M" }, { "libelle": "L" }, { "libelle": "XL" } ],
  "visible_collaborateur": false, "confirmation_donnees_non_sensibles": true }

ModifierChampPerso {   // état voulu de tous les attributs ; valeurs_liste seulement si type_valeur devient LISTE
  "version": 9, "libelle": "Taille de tenue", "finalite": "…", "type_valeur": "LISTE", "regles": { … },
  "visible_collaborateur": true }

Parametrage.champs[] (P1, élargi ; champ fixe : "personnalise": false, "actif": true, attributs personnalisés à null)
  { "code": "PERSO_RECETTE_PERSO_TAILLE_DE_TENUE", "libelle": "[RECETTE-PERSO] Taille de tenue",
    "personnalise": true, "actif": true,
    "en_tete": "[RECETTE-PERSO] Taille de tenue", "en_tete_defaut": "[RECETTE-PERSO] Taille de tenue",
    "dans_modele": true, "retirable_du_modele": true,
    "exigence": "FACULTATIF", "exigence_libelle": "Facultatif", "exigence_minimale": "FACULTATIF",
    "exigences_possibles": [ … 3 (collaborateurs) ou 2 (soldes) … ], "verrouille": false, "verrou_libelle": null,
    "ordre": 14, "synonymes": [ … ],
    "type_valeur": "LISTE", "type_valeur_libelle": "Liste de valeurs",
    "regles": { "longueur_max": null, "decimales": null, "minimum": null, "maximum": null, "date_min": null, "date_max": null },
    "regles_libelle": "4 valeurs",
    "valeurs_liste": [ { "code": "S", "libelle": "S", "ordre": 0, "actif": true, "utilisations": 1, "supprimable": false } ],
    "finalite": "Commande des tenues de l'événement", "visible_collaborateur": false,
    "utilisations": 3, "utilisations_libelle": "3 fiches renseignées",
    "type_modifiable": false, "supprimable": false,
    "cree_le": "2026-11-13T10:12:00Z", "cree_par_nom": "Amel Trabelsi" }
// Ordre : actifs dans l'ordre réglé, puis inactifs ("ordre": null, "dans_modele": false) par cree_le.
// Nombres des règles en chaîne ("minimum": "30"), dates ISO. en_tete_defaut d'un personnalisé = libellé.

ValeursFiche (V1, V2) {
  "collaborateur_id": "…", "version": 4, "modifiable": true, "raison_non_modifiable": null,
  "champs": [ { "code": "PERSO_POINTURE", "libelle": "Pointure", "type_valeur": "NOMBRE",
                "type_valeur_libelle": "Nombre", "regles": { "decimales": 0, "minimum": "30", "maximum": "50" },
                "valeurs_liste": null, "valeur": "42", "valeur_affichee": "42", "valeur_inactive": false } ],
  "champs_desactives": [ { "code": "PERSO_CODE_VESTIAIRE", "libelle": "Code vestiaire", "type_valeur": "TEXTE",
                           "valeur": "B-12", "valeur_affichee": "B-12" } ] }
// LISTE : "valeur" = code, "valeur_affichee" = libellé, "valeur_inactive": true si la valeur de liste est désactivée ;
// "valeurs_liste" = valeurs actives + la valeur inactive actuelle. Champ non renseigné : "valeur": null.
// raison_non_modifiable : « Vous ne pouvez pas modifier vos propres informations. » | « Consultation uniquement. »

PageJournalValeurs (V3) {
  "contenu": [ { "id": "…", "horodatage": "2026-11-13T10:12:00Z", "auteur_nom": "Amel Trabelsi",
                 "champ_code": "PERSO_POINTURE", "champ_libelle": "Pointure", "avant": null, "apres": "42",
                 "origine": "SAISIE", "origine_libelle": "Saisie sur la fiche" } ],
  "page": 0, "taille": 20, "total": 1 }
// origine IMPORT : "origine_libelle": "Import du 13/11/2026" ; null = « (vide) » à l'écran.

MesChamps (V4) { "champs": [ { "code": "PERSO_RECETTE_PERSO_TAILLE_DE_TENUE", "libelle": "[RECETTE-PERSO] Taille de tenue",
                               "type_valeur": "LISTE", "valeur_affichee": "M" } ] }

ValeurAffichee (mouvements) { "code": "…", "libelle": "Période de reprise", "type_valeur": "LISTE",
                              "valeur": "2026", "valeur_affichee": "2026" }
```

Imports (I1 à I12, modifiés) :

| # | Changement |
|---|---|
| I1–I5 | Champs personnalisés actifs reconnus (P2-4) ; `champs_disponibles[]` + `personnalise`, `type_valeur`, `type_valeur_libelle` ; erreurs de ligne `FORMAT_INVALIDE`, `DATE_INVALIDE`, `VALEUR_INVALIDE`, `VALEUR_TROP_LONGUE`, `VALEUR_HORS_BORNES`, `VALEUR_INCONNUE`, `CHAMP_OBLIGATOIRE`, `AUTO_MODIFICATION_INTERDITE` ; erreur de fichier `COLONNE_OBLIGATOIRE_ABSENTE` ; `modifications[]` avec le libellé du champ |
| I6 | Avertissements de ligne `CHAMP_PERSO_MODIFIE` (« Champ « X » supprimé ou modifié depuis l'analyse : valeur non enregistrée. ») et, pour les soldes, `VALEURS_PERSO_NON_ENREGISTREES` (« Solde inchangé : informations complémentaires non enregistrées. ») |
| I7 | Inchangé (en-têtes d'origine + « Erreurs », formules neutralisées) |
| I8, I9 | Modèle : champs fixes et personnalisés actifs `dans_modele`, ordre et en-têtes réglés |
| I12 | `champ_libelle` = libellé du champ personnalisé |

### A bis.3 Matrice des rôles

| Endpoint | Sans JWT | COLLABORATEUR (COL-WEB) | RO / RESPONSABLE (CS-WEB) | RH (DRH01, RH02) | ADMIN (ADM01) | DIRECTION (DG01) |
|---|---|---|---|---|---|---|
| P1, P7 (définitions, journal) | 401 | 403 | 403 | 200 | 200 | 200 |
| C1, C5a | 401 | 403 | 403 | 201 | 201 | 403 |
| C2, C3, C5b, C6a | 401 | 403 | 403 | 200 | 200 | 403 |
| C4, C6b | 401 | 403 | 403 | 204 | 204 | 403 |
| V1, V3 | 401 | 403 | 403 | 200 | 200 | 200 |
| V2 | 401 | 403 | 403 | 200 ; 403 `AUTO_MODIFICATION_INTERDITE` sur sa fiche | idem RH | 403 |
| CR (création avec valeurs) | 401 | 403 | 403 | 201 | 201 | 403 |
| V5, V6 | 401 | 403 | 403 | 200 | 200 | 200 |
| V4 | 401 | 200 (les siennes) | 200 (les siennes) | 200 (les siennes) | 200 (les siennes) | 200 (les siennes) |
| AJ (ajustement avec valeurs) | 401 | 403 | 403 | 200 ; 403 `AUTO_AJUSTEMENT_INTERDIT` sur soi (inchangé) | idem RH | 403 |
| MV (mouvements RH, export) | 401 | 403 | 403 | 200 | 200 | 200 |
| MM (mes mouvements) | 401 | 200 (champs visibles) | idem | idem | idem | idem |
| I1–I9 (import) | inchangé : RH, ADMIN ; DIRECTION 403 | | | | | |
