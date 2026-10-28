# Déployer le SIRH gratuitement pour la recette client

Objectif : mettre en ligne tout le SIRH (7 services Java, PostgreSQL, Kafka, Plateforme RH web) sur une
machine **gratuite à vie** d'Oracle Cloud, en **HTTPS**, et fournir l'appli **Android** au client.
Le client pourra ensuite installer exactement le même kit sur son propre serveur (section 12).

Durée : environ 1 h la première fois, dont 15 à 20 min de construction des images.

## Contenu du kit (`deploy/`)

| Fichier | Rôle |
|---|---|
| `docker-compose.yml` | Toute la plateforme : PostgreSQL 17, Kafka, Eureka, gateway, 5 services, web, Caddy (HTTPS), Mailpit |
| `Dockerfile.services` | Construit les 7 services Spring Boot en une passe Maven (x86 et ARM) |
| `Dockerfile.web` + `nginx.conf` | Plateforme RH ; nginx relaie aussi `/api` et `/ws` vers la gateway |
| `Caddyfile` | Certificat HTTPS Let's Encrypt automatique pour votre domaine |
| `.env.example` | Secrets et réglages à copier en `.env` (jamais commité) |
| `postgres/init/01-bases.sql` | Crée les 4 bases au premier démarrage |
| `scripts/exporter-donnees-locales.sh` | Sur votre PC : exporte comptes, organigramme, évaluations, pointage |
| `scripts/importer-donnees.sh` | Sur le serveur : recharge ces données |

```
Internet ──HTTPS──▶ Caddy :443 ──▶ web (nginx) ──┬── Plateforme RH (fichiers statiques)
                                                └── /api, /ws ──▶ gateway ──▶ services ──▶ PostgreSQL, Kafka
RH Connect (Android / iPhone) ──HTTPS──▶ même domaine
```

Seuls les ports 80 et 443 sont ouverts ; bases, Kafka et services restent sur le réseau interne Docker.

Consommation mesurée (kit testé en local le 28/10/2026) : **environ 3,5 Go de mémoire** pour l'ensemble
(7 services Java ≈ 300–410 Mo chacun, Kafka ≈ 430 Mo, PostgreSQL ≈ 100 Mo). La machine gratuite Oracle
(24 Go) est donc très confortable ; 2 OCPU / 12 Go suffisent aussi.

---

## 1. Ce qu'il faut avant de commencer

- Un compte **Oracle Cloud Free Tier** : <https://www.oracle.com/cloud/free/>. Une carte bancaire est demandée
  pour vérifier l'identité ; rien n'est débité tant qu'on reste sur les ressources « Always Free ».
- Un compte **DuckDNS** (sous-domaine gratuit, connexion avec un compte Google ou GitHub) : <https://www.duckdns.org>.
- Sur votre PC : le dépôt à jour et Docker (pour exporter les données de recette).
- Le compte **Codemagic** déjà utilisé pour l'iPhone (build Android).

## 2. Créer la machine Oracle (gratuite)

1. Console Oracle → **Compute → Instances → Create instance**.
2. **Région** : la plus proche des testeurs (ex. *France South – Marseille* ou *Germany Central – Frankfurt*).
   La région d'origine choisie à l'inscription ne peut plus être changée.
3. **Image** : *Canonical Ubuntu 24.04* (version *aarch64*).
4. **Shape** : *Ampere* → `VM.Standard.A1.Flex`, **4 OCPU et 24 Go de mémoire** (le maximum gratuit).
   Si le message *Out of capacity* apparaît : réessayer plus tard, changer de *availability domain*,
   ou prendre 2 OCPU / 12 Go (suffisant pour la recette).
5. **SSH** : *Generate a key pair* et **télécharger la clé privée** (ou coller votre clé publique).
6. **Boot volume** : 100 Go (jusqu'à 200 Go gratuits).
7. Créer, puis noter l'**adresse IP publique** de l'instance.

## 3. Ouvrir les ports 80 et 443

Deux pare-feu à ouvrir :

**a) Dans Oracle Cloud** : Instance → *Subnet* → *Default Security List* → **Add Ingress Rules** :
- Source `0.0.0.0/0`, protocole TCP, port de destination `80`
- Source `0.0.0.0/0`, protocole TCP, port de destination `443`

**b) Sur la machine** (les images Ubuntu d'Oracle bloquent tout par défaut) :

```bash
ssh -i cle-privee.key ubuntu@<IP_PUBLIQUE>
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
sudo netfilter-persistent save
```

## 4. Nom de domaine gratuit (DuckDNS)

1. Sur duckdns.org, créer un sous-domaine, par exemple `sirh-demo` → `sirh-demo.duckdns.org`.
2. Dans le champ *current ip*, mettre l'**IP publique** de la machine Oracle, puis *update ip*.
3. Vérifier depuis votre PC : `nslookup sirh-demo.duckdns.org` doit renvoyer cette IP.

L'IP d'une instance Oracle ne change pas tant que l'instance n'est pas supprimée
(pour plus de sécurité : *Reserved public IP*, également gratuite).

## 5. Installer Docker sur la machine

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu
exit        # puis se reconnecter en SSH pour que le groupe docker soit pris en compte
docker run --rm hello-world
```

## 6. Copier le code sur la machine

Le dépôt GitHub est privé. Deux possibilités :

- **Avec Git** (recommandé, mises à jour faciles) : créer sur GitHub un *Fine-grained personal access token*
  en lecture seule sur le dépôt, puis :
  ```bash
  git clone https://<TOKEN>@github.com/khaledmejbri/SIRH-Plateform-Back-Front.git ~/sirh
  cd ~/sirh && git checkout delivery
  ```
- **Sans Git** : depuis votre PC, `git archive --format=tar.gz -o sirh.tar.gz delivery`, puis
  `scp -i cle-privee.key sirh.tar.gz ubuntu@<IP>:~` et sur le serveur `mkdir ~/sirh && tar xzf sirh.tar.gz -C ~/sirh`.

## 7. Configurer les secrets

```bash
cd ~/sirh/deploy
cp .env.example .env
for v in POSTGRES_PASSWORD INTERNAL_API_TOKEN PRESENCE_QR_HMAC_SECRET; do
  sed -i "s/^$v=.*/$v=$(openssl rand -hex 24)/" .env
done
sed -i "s/^DOMAINE=.*/DOMAINE=sirh-demo.duckdns.org/" .env   # votre sous-domaine
nano .env    # relire ; ne jamais committer ce fichier
```

## 8. Lancer la plateforme

```bash
cd ~/sirh/deploy
docker compose up -d --build     # 15 à 20 min la première fois (construction Maven et npm)
docker compose ps                # tous les services « running » ; Kafka et PostgreSQL « healthy »
docker compose logs -f gateway   # Ctrl+C pour quitter
```

Caddy obtient le certificat HTTPS tout seul dans la minute (`docker compose logs caddy`).
Comptez **1 à 2 minutes** après le démarrage : tant que les services ne sont pas inscrits dans l'annuaire
(Eureka, rafraîchi toutes les 30 s), l'API répond « 503 » ; c'est normal.
Ouvrir **https://sirh-demo.duckdns.org** : la page de connexion de la Plateforme RH s'affiche.

## 9. Charger les données de recette (comptes, organigramme, évaluations, sites)

Sans cette étape, la base est vide et **aucun compte ne peut se connecter**.

Sur **votre PC**, à la racine du dépôt :

```bash
bash deploy/scripts/exporter-donnees-locales.sh
scp -i cle-privee.key -r deploy/donnees ubuntu@<IP>:~/sirh/deploy/
```

Sur le **serveur** :

```bash
cd ~/sirh/deploy
bash scripts/importer-donnees.sh     # répondre « oui »
```

Les comptes et mots de passe sont ceux du fichier `recette/comptes_recette_SIRH.xlsx`
(à transmettre au client par un canal privé, jamais par e-mail en clair avec le lien).

> Les exports contiennent des données personnelles et des empreintes de mots de passe :
> ne les publiez pas, supprimez-les du serveur après l'import (`rm -r ~/sirh/deploy/donnees`).

**Après l'import, côté Plateforme RH (compte RH)** :
- **Sites de pointage → régénérer le QR** de chaque site : le secret de signature du serveur est nouveau,
  les QR imprimés en local ne sont plus valables.
- Reposer l'emplacement GPS de chaque site sur place depuis RH Connect (et valider le rayon avec « Test terrain »).

## 10. Appli Android pour le client

1. Codemagic → application RH Connect → workflow **RH Connect Android (manuel)** → *Start new build*.
2. Dans *Environment variables*, vérifier `API_BASE_URL=https://sirh-demo.duckdns.org` (votre domaine).
3. À la fin du build, télécharger `app-release.apk` (*Artifacts*), puis le déposer sur Google Drive
   et partager le lien au client.
4. Sur le téléphone : ouvrir le lien, autoriser *Installer des applis inconnues* pour le navigateur ou Drive,
   installer. Au premier lancement, l'adresse du serveur est déjà la bonne ; elle reste modifiable
   depuis l'écran de connexion (icône crayon à côté de « Serveur »).

L'APK est signé avec la clé de debug : parfait pour des tests, mais il faudra une clé de signature
dédiée pour publier sur le Play Store.

## 11. Et l'iPhone ?

Apple n'autorise la distribution qu'avec un **compte Apple Developer (99 $ / an)**, même pour des testeurs
(TestFlight). Le client en aura besoin de toute façon pour publier : le plus simple est qu'il crée **son**
compte, vous ajoute à son équipe, et vous envoyez les builds Codemagic (`rh-connect-ios-signed`) sur son TestFlight.

En attendant :
- les testeurs iPhone utilisent la **Plateforme RH web** (rôles RH, Direction, Admin) ;
- l'appli collaborateur se teste sur Android ;
- la version web de RH Connect existe mais **sans pointage** (le GPS passe par le code natif du téléphone).

## 12. Passer la main au client

Le même dossier `deploy/` fonctionne sur n'importe quel serveur Linux avec Docker (VPS, serveur interne) :

1. Copier le code, créer un `.env` avec **de nouveaux secrets** et son propre domaine.
2. Renseigner son serveur de messagerie : `SMTP_HOST`, `SMTP_PORT`, `MAIL_FROM` (puis supprimer le service `mailpit`).
3. `docker compose up -d --build`, puis importer ses données ou partir d'une base vierge.
4. **Changer tous les mots de passe de recette** avant toute utilisation réelle.
5. Mettre en place des sauvegardes (section 14).

## 13. Mettre à jour la démo après un nouveau développement

```bash
cd ~/sirh && git pull
cd deploy && docker compose up -d --build
```

Seules les images modifiées sont reconstruites ; les données sont conservées (volumes Docker).
Redémarrer `svc-identite-acces` déconnecte les utilisateurs (clé de signature des jetons régénérée) :
il suffit de se reconnecter.

## 14. Sauvegardes

```bash
# Sauvegarde complète chaque nuit à 2 h, conservée 7 jours
mkdir -p ~/sauvegardes
( crontab -l 2>/dev/null; echo '0 2 * * * cd ~/sirh/deploy && docker compose exec -T postgres pg_dumpall -U sirh | gzip > ~/sauvegardes/sirh-$(date +\%F).sql.gz && find ~/sauvegardes -mtime +7 -delete' ) | crontab -
```

## 15. Alternative sans serveur : votre PC + Cloudflare Tunnel

Pour une démonstration ponctuelle, sans créer de machine Oracle :

1. Dans `.env` : `DOMAINE=:80` et `HTTP_PORT=8090`, puis `docker compose up -d --build` sur votre PC.
2. Installer `cloudflared` (<https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/downloads/>),
   puis `cloudflared tunnel --url http://localhost:8090`.
3. Une adresse `https://xxxx.trycloudflare.com` s'affiche : la donner au client (web) et la saisir dans RH Connect.

Limites : l'application n'est accessible que lorsque votre PC est allumé, et l'adresse change à chaque lancement.

## 16. Dépannage

| Symptôme | Cause probable / solution |
|---|---|
| *Out of capacity* à la création de la machine | Réessayer plus tard, autre *availability domain*, ou 2 OCPU / 12 Go |
| Le site ne répond pas | Ports 80/443 : vérifier les deux pare-feu (section 3) ; `docker compose ps` |
| Pas de certificat HTTPS | Le domaine doit pointer sur l'IP (section 4) et le port 80 être ouvert ; `docker compose logs caddy` |
| Page de connexion OK mais « erreur serveur » | Services encore en démarrage (1 à 2 min) ; `docker compose logs svc-identite-acces` |
| Aucun compte ne fonctionne | Données non importées (section 9) |
| Pointage refusé « QR invalide » | QR créé avant l'import : le régénérer (section 9) |
| Mémoire saturée | `docker stats` ; réduire `JAVA_XMX=384m` dans `.env` puis `docker compose up -d` |
| Notifications absentes | `docker compose logs kafka svc-notification` |
