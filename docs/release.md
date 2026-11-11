# Build release signée

Dépôt public : aucune clé, aucun mot de passe, aucun keystore ne doit y entrer. La CI ne reçoit jamais la clé.

## 1. Créer la clé (une seule fois, sur le PC de JR, hors dépôt)

Dans `D:\humaneo\cles\` :

```
keytool -genkeypair -v -keystore D:\humaneo\cles\dosejuste-release.jks -alias dosejuste -keyalg RSA -keysize 2048 -validity 10000 -storetype PKCS12
```

`keytool` demande les mots de passe et l'identité de façon interactive. Ne jamais ajouter `-storepass` ni `-keypass`, ni écrire un mot de passe dans une commande, la doc, un chat ou le dépôt. Avec PKCS12, utiliser le même mot de passe pour le magasin et la clé.

## 2. Sauvegarder la clé

Copier le `.jks` et les mots de passe dans un endroit sûr et **séparé** du PC. Perdre la clé empêche toute mise à jour de l'appli installée : il faudrait la désinstaller et perdre ses données.

## 3. Configurer la signature

Au choix (le fichier a priorité sur l'environnement) :

- copier `keystore.properties.example` en `keystore.properties` à la racine du dépôt (ignoré par git) et le remplir ;
- ou définir `DOSEJUSTE_STORE_FILE`, `DOSEJUSTE_STORE_PASSWORD`, `DOSEJUSTE_KEY_ALIAS`, `DOSEJUSTE_KEY_PASSWORD`.

`storeFile` vaut par défaut `D:/humaneo/cles/dosejuste-release.jks` (barres obliques). La signature n'est appliquée que si le mot de passe du magasin, l'alias et le mot de passe de la clé sont non vides et que le fichier existe ; sinon Gradle affiche un avertissement (sans valeur secrète) et l'APK reste non signé.

## 4. Compiler

```
.\gradlew.bat assembleRelease
```

- Signé : `app/build/outputs/apk/release/app-release.apk`.
- Sans configuration : `app-release-unsigned.apk`, non installable tel quel.

La minification et la réduction des ressources (R8) sont actives en release.

## 5. Vérifier la signature

```
apksigner verify --print-certs app\build\outputs\apk\release\app-release.apk
```

Aucun mot de passe n'est demandé ni affiché.

## À savoir

Un APK signé avec cette clé ne peut pas remplacer l'appli debug déjà installée (autre signature) sans la désinstaller, ce qui efface ses données.
