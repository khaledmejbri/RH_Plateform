# Modèle de reconnaissance faciale (MobileFaceNet)

Ce dossier doit contenir `mobilefacenet.tflite` (non versionné ici : le fichier est à déposer après validation).

- Source : https://huggingface.co/qualcomm/MobileFaceNet (export `.tflite` officiel Qualcomm AI Hub, poids Apache-2.0).
- Caractéristiques attendues : entrée 112 x 112 x 3 (float32 ; normalisation des pixels réglée dans `FaceModelInfo`, valeur de départ `(p - 127,5) / 128`, **à valider avec le modèle réellement déposé**), sortie : vecteur de 128 valeurs. Taille voisine de 4 Mo.
- Identifiant de version envoyé au serveur : `mfn-qaihub-1` (constante `FaceModelInfo.version`). Tout autre modèle = nouvelle version et nouveau calibrage du point `t`.
- Licence : risque lié aux données d'entraînement (MS-Celeb-1M), voir l'ADR-M09-FACE, ticket SCRUM-43 / C-L-07. Activation subordonnée à l'avis du juriste.

## Vérification SHA-256

1. Télécharger le fichier depuis la page ci-dessus.
2. Calculer la somme : `sha256sum mobilefacenet.tflite` (Windows : `certutil -hashfile mobilefacenet.tflite SHA256`).
3. Comparer avec la somme consignée dans le dépôt (à renseigner ici une fois le modèle validé) :
   `SHA-256 : (à renseigner)`.
4. Ne déposer le fichier dans `assets/models/` qu'en cas d'égalité.

## Garde-fou de livraison (codemagic.yaml)

Étape « Verifier le modele facial », avant chaque build (iOS, iOS signé, Android) :

- modèle **absent** : avertissement et le build continue (vérification faciale indisponible dans ce build), **sauf** si la
  variable d'environnement Codemagic `MODELE_FACIAL_OBLIGATOIRE` vaut `true` : le build échoue ;
- modèle **présent** : le build échoue si sa SHA-256 diffère de `assets/models/mobilefacenet.sha256` ; tant que ce fichier
  contient `A_RENSEIGNER`, l'étape avertit sans échouer.

Quand poser `MODELE_FACIAL_OBLIGATOIRE=true` : une fois le modèle déposé et validé (somme écrite dans
`mobilefacenet.sha256`) **et** l'avis du juriste obtenu (C-L-07), avant d'activer le réglage en production : un build de
production sans modèle ne doit pas partir.

## Comportement à l'exécution

- Modèle **absent** du paquet : défaut d'empaquetage, pas une incompatibilité de l'appareil. L'appli n'appelle **pas**
  `/appareil-incompatible`, n'affiche aucun écran de consentement et indique « vérification faciale indisponible dans
  cette version » (journal de debug). Le pointage reste inchangé.
- Modèle présent mais refusé par l'interpréteur, ou formes inattendues : `MODELE_NON_CHARGEABLE` signalé au serveur.
- Aucune caméra frontale (la liste des caméras est lue sans erreur) : `SANS_CAMERA_FRONTALE`. Une erreur ponctuelle de
  lecture des caméras ne signale rien : nouvel essai au prochain lancement.

## À vérifier sur téléphone (TODO terrain)

- TODO-TERRAIN-1 : vivacité, visage réapparu avec un **autre `trackingId`** pendant la tolérance de perte de 300 ms
  (`LivenessStateMachine.perteVisageToleree`) : aujourd'hui = échec immédiat ; confirmer en conditions réelles que ML Kit
  ne change pas d'identifiant sur une simple image ratée, sinon assouplir.
- TODO-TERRAIN-2 : signe de l'angle de tête (`inverserSensRotation`), normalisation des pixels, calibrage de `t`.

