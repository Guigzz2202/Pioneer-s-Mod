# Pioneer's — consignes pour Claude (dépôt du mod)

Ce dépôt contient **seulement le code du mod** (Forge 1.20.1, 47.4.20). La référence complète du projet (`CLAUDE.md` détaillé, `ANNUAIRE.md`), les générateurs Python, les contrôles et les docs internes sont dans le dépôt privé **Guigzz2202/Pioneer-s-Mod-tools**, cloné dans `tools/` (dossier ignoré ici).

## Utilisateur
- Guillaume ne code pas : Claude fait tout (git, build, code, ressources). Guillaume teste en jeu, valide le ressenti et décide du contenu.
- Réponses en français, concises. Aperçus en images quand c'est visuel.

## Git
- `main` est toujours stable et jouable. Une branche par lot ou MAJ : `maj/<version>-<sujet>`, `refactor/<sujet>`, `fix/<sujet>`.
- Les commits sont faits par étape logique. Le merge dans `main` se fait seulement après validation en jeu par Guillaume, et c'est la seule chose dont on lui rend compte côté git.
- Messages de commit au format **Conventional Commits** : `type(portée): description` (`feat`, `fix`, `refactor`, `perf`, `docs`, `test`, `build`, `ci`, `chore`, `style`), description courte en français sans point final. Ex. `chore(doc): découper CLAUDE.md`, `fix(logistique): câble qui ne se connecte pas`. Même règle pour les titres de merge et de PR.
- Étiquette de version sur `main` à chaque version publiée : `v<mod_version>`.

## Build
- Avant de rendre un lot : `./gradlew build`, puis `./gradlew runGameTestServer`, et un démarrage du jeu sans écran (Xvfb) quand c'est utile. Corriger toute erreur sans solliciter Guillaume.
- GitHub Actions (`.github/workflows/build.yml`) compile et lance les GameTests à chaque push.

## Code
- Paquet `fr.pioneers`, modid `pioneers`. L'organisation par domaine est décrite dans `docs/architecture.md`.
- Commentaires et Javadoc en français, sans accents dans le Java (ASCII). Les textes du jeu (langue, guide) ont leurs accents ; en_us en anglais américain, placeholders `%s` uniquement.
- Les fichiers produits par un générateur (classes `Generated*`, modèles, textures, recettes, langue…) ne se modifient jamais à la main : on change le générateur dans le dépôt d'outils et on le relance.
