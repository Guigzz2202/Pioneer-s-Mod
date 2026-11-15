# Pioneer's

Mod Minecraft **Forge 1.20.1 (47.4.20)** : bâtir une usine à travers les âges, des engrenages en bois, du bronze et de la vapeur jusqu'à l'industrie, la chimie, le nucléaire, l'informatique, la robotique et le rail, puis partir dans l'espace (fusées modulaires, recherche, bases sur d'autres mondes, géantes gazeuses, étoiles artificielles).

- **modid :** `pioneers` (paquet Java `fr.pioneers`)
- **Version :** voir `mod_version` dans `gradle.properties` ; les notes de version sont dans `CHANGELOG.md`.
- **Mekanism :** compatibilité facultative (10.4.16).

## Compiler et lancer
```
./gradlew build              # compile et produit build/libs/pioneers-<version>.jar
./gradlew runClient          # lance le jeu
./gradlew runServer          # lance un serveur
./gradlew runGameTestServer  # lance les GameTests du mod sur un serveur sans écran
```
Java 17 est nécessaire : Gradle le télécharge tout seul si besoin.

## Branches
- `main` est toujours stable et jouable.
- Chaque mise à jour ou lot a sa branche (`maj/…`, `refactor/…`, `fix/…`). Une branche n'est fusionnée dans `main` qu'après avoir été validée en jeu.
- Chaque push est compilé et testé par GitHub Actions (`.github/workflows/build.yml`).

## Organisation
- Les ressources générées (modèles, textures, recettes, langue…) sont produites par les générateurs du dépôt privé **Pioneer-s-Mod-tools**. Il faut les changer par ces générateurs, jamais à la main.
- L’architecture du code (domaines, règles de rangement) est décrite dans `docs/architecture.md`.
