# Architecture de Pioneer's (1.0.6)

Ce document décrit comment le projet est organisé depuis la 1.0.6 : les dépôts, le flux de travail git, le build et les tests, puis le découpage du code par domaine. Une copie est dans le Projet claude.ai (`claude/architecture.md`).

## 1. Les deux dépôts
| Dépôt | Contenu | Où |
|---|---|---|
| **Pioneer-s-Mod** | Le code du mod seulement : `src/`, fichiers Gradle, `CHANGELOG.md`, `README.md`, ce document, CI | GitHub, clone de Guillaume `C:\Users\guill\Desktop\Pioneer-s-Mod`, conteneur `/home/claude/pioneer-s-mod` |
| **Pioneer-s-Mod-tools** (privé) | Générateurs Python, contrôles, bancs Java, docs internes, `CLAUDE.md` complet, `ANNUAIRE.md` | GitHub, conteneur `/home/claude/pioneer-s-mod-tools` |

**Espace de travail de Claude (conteneur).**
- `/home/claude/work/MS` est un lien vers le clone du mod.
- `MS/tools` est un lien vers le clone des outils ; il est ignoré par git dans le dépôt du mod.
- Les données de travail des bancs sont dans `/home/claude/work/scratch` : le classpath Gradle exporté, la police du jeu, les références.

Ainsi, tous les chemins `/home/claude/work/MS/src/...` et `/home/claude/work/MS/tools/...` des générateurs restent valables.

L'ancien dossier `MekanizedStar` du PC est une archive : on n'y touche plus.

## 2. Git
- **`main`** est toujours stable et jouable. Chaque version publiée y reçoit l'étiquette `v<mod_version>` ; `v1.0.5-beta` est le point de départ.
- **Une branche par lot ou par MAJ :** `maj/<version>-<sujet>`, `refactor/<sujet>`, `fix/<sujet>`. Les deux dépôts utilisent le même nom de branche quand un lot touche aussi les outils.
- **Commits** par étape logique, en français, avec les lignes d'attribution de Claude.
- **Merge dans `main`** seulement après validation en jeu par Guillaume. C'est le seul événement git dont Claude lui rend compte.
- **Clone de Guillaume :** Claude y récupère lui-même la branche à tester avant chaque test en jeu.

## 3. Build et tests
| Étape | Commande | Quand |
|---|---|---|
| Compilation | `./gradlew build` | à chaque étape |
| GameTests (serveur sans écran) | `./gradlew runGameTestServer` | avant de rendre un lot |
| Démarrage du jeu | `./gradlew runClient` sous Xvfb (affichage virtuel, Mesa) | avant de rendre un lot qui touche le client |
| Contrôles du projet | suite des outils, par domaine (rapide / complète) | rapide à chaque commit, complète avant merge |
| CI | GitHub Actions : build et GameTests à chaque push | automatique |

Java 17 vient de la toolchain Gradle ; le conteneur a un accès réseau aux dépôts Maven de Forge, Mojang et Gradle.

## 4. Découpage du code par domaine
Le paquet racine reste `fr.pioneers` (modid `pioneers`).
- Chaque domaine regroupe le code commun et le code client de ses fonctionnalités : `<domaine>/<fonction>/client`. Avant, tout le client était dans un `client/` global.
- Les domaines suivent les onglets créatifs.

### 4.1 Les domaines
| Domaine | Onglet(s) créatif(s) | Contenu |
|---|---|---|
| `api` | — | API publique (inchangée : les addons s'y branchent) |
| `core` | Core, Fluides et gaz | Socle technique : primitives, machines de base, gaz, chaleur, pression, données numériques, réseau, config, compatibilité Mekanism, recettes communes, registres |
| `client` | — | Infrastructure client partagée : dessin des interfaces, modèles connectés, rendus communs, minimap, fiche au regard |
| `integration` | — | JEI |
| `mixin` | — | Mixins (`pioneers.mixins.json`) |
| `gametest` | — | GameTests |
| `guide` | Équipement | Le guide (touche H) et la partie |
| `tech` | Tech, Composants | Âge mécanique, cinétique, bronze et vapeur, forge, industrie, chimie, énergie, nucléaire, fusion, calcul et informatique, matériaux |
| `logistics` | Logistique | Câble logistique et buffers, entrepôt, air comprimé et hypertubes, rail, marine, caravanes, robotique |
| `space` | Espace | Programme spatial, recherche, bases, géantes gazeuses, simulateur, stabilisateur, absorbeur, machines scientifiques |
| `world` | Mondes | Saisons, intempéries, faune, gibier, chiens et chats, chasse, nature, géologie, ruines |
| `farm` | Tech (ferme et cuisine), Core (nourriture) | Ferme, cuisine, élevage, serre, aquarium, pêche |
| `society` | Équipement (Carnet, clans) | Clans, économie, réputation, documents et courrier, villages, Carnet (RP) |
| `building` | Construction | Mobilier, poutres, paniers, cordes à linge, sacs, éclairage |
| `equipment` | Équipement | Équipement modulaire, armures, cosmétiques, musique |

### 4.2 Correspondance des anciens paquets (refactor/structure-domaines)
Chaque ancien paquet devient **exactement un** nouveau paquet, sans fusion. Les accès « paquet » entre classes restent donc identiques, et le comportement du jeu ne change pas. Les sous-paquets suivent leur parent (par exemple `bronze.steam` devient `tech.bronze.steam`).

| Ancien | Nouveau |
|---|---|
| `network` | `core.network` |
| `common` | `core.common` |
| `compat` | `core.compat` |
| `data` | `core.data` |
| `recipe` | `core.recipes` (`core.recipe` existe déjà : on ne fusionne jamais deux paquets) |
| `registries` | `core.registries` |
| `guide`, `client.guide` | `guide`, `guide.client` |
| `mechanics`, `kinetics`, `bronze`, `forge`, `industry`, `chemistry`, `machine`, `energy`, `nuclear`, `fusion`, `compute`, `computing`, `materials` | `tech.<même nom>` |
| `client.kinetics`, `client.bronze`, `client.forge`, `client.industry`, `client.energy`, `client.nuclear` | `tech.<nom>.client` |
| `client.particle` | `tech.bronze.client.particle` |
| `client.tools` | `tech.materials.client` |
| `transport`, `warehouse`, `pneumatic`, `rail`, `naval`, `caravan`, `robotics` | `logistics.<même nom>` |
| `client.transport`, `client.warehouse`, `client.pneumatic`, `client.rail`, `client.naval`, `client.caravan`, `client.robotics` | `logistics.<nom>.client` |
| `client.logistic` | `logistics.transport.logistic.client` |
| `space` | `space` (inchangé) |
| `client.space`, `client.giant`, `client.research`, `client.sky` | `space.client`, `space.giant.client`, `space.research.client`, `space.sky.client` |
| `content`, `tile`, `simulation`, `item` | `space.content`, `space.tile`, `space.simulation`, `space.item` |
| `season`, `weather`, `fauna`, `wild`, `pet`, `hunt`, `nature`, `geology`, `ruins` | `world.<même nom>` |
| `client.season`, `client.wild`, `client.pet`, `client.hunt`, `client.ruins` | `world.<nom>.client` |
| `farm` | `farm` (inchangé) |
| `client.farm` | `farm.client` |
| `clan`, `economy`, `reputation`, `paperwork`, `village` | `society.<même nom>` |
| `client.clan`, `client.economy`, `client.paperwork`, `client.village` | `society.<nom>.client` |
| `client.rp` | `society.rp.client` |
| `home`, `lighting` | `building.home`, `building.lighting` |
| `client.home`, `client.lighting` | `building.home.client`, `building.lighting.client` |
| `gear`, `cosmetic`, `music` | `equipment.<même nom>` |
| `client.gear`, `client.cosmetic`, `client.music`, `client.armor` | `equipment.<nom>.client` |
| `client` (racine), `client.gui`, `client.gui.core`, `client.model`, `client.render`, `client.look`, `client.minimap` | inchangés (infrastructure client partagée) |
| `core`, `api`, `integration`, `mixin`, `gametest` | inchangés |

### 4.3 Règles
- Une nouvelle fonctionnalité va dans le domaine de son onglet créatif : `fr.pioneers.<domaine>.<fonction>`, et son code client dans `fr.pioneers.<domaine>.<fonction>.client`.
- `client.*` ne reçoit que de l'infrastructure partagée par plusieurs domaines.
- Le code client n'est appelé depuis le code commun que derrière `DistExecutor` ou un test `FMLEnvironment.dist`, comme avant.
- Une classe produite par un générateur reste à la place où le générateur l'écrit : on change le générateur, jamais la classe.

### 4.4 Étapes suivantes (après validation en jeu du découpage)
- **Registres** (`core.registries`) : rapprocher chaque registre de son domaine (`PiBronze` vers `tech.bronze`, `PiRail` vers `logistics.rail`…). Le paquet de registres partagé ne garderait que l'infrastructure et les onglets créatifs.
- **Rendus communs** (`client.render`) : ranger les rendus propres à un domaine dans ce domaine (champ de force, stabilisateur, simulateur et absorbeur vers `space`).
- **GameTests** : les ranger par domaine.
