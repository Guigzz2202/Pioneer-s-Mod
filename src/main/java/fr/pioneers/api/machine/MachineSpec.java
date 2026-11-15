package fr.pioneers.api.machine;

import fr.pioneers.core.side.SideType;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * API Machine (lot 1) : FICHE d'une machine. Une seule declaration par machine, cote Python (tools/gc/api/machines.py, et
 * tools/gc/api/catalog_adapter.py pour le catalogue industriel), qui produit {@link GeneratedMachines} : ne jamais ecrire une fiche a la
 * main en Java. Champs detailles dans tools/gc/api/schema.py.
 * <p>
 * Au lot 1, la fiche DECRIT la machine (ids, tile, ports, recettes, JEI, interface, infobulle) sans encore la faire tourner : le comportement
 * reste dans la tile d'aujourd'hui ({@link #logic}), qui migre vers les unites de l'API au lot {@link #lot}. L'infobulle standard
 * ({@link MachineTooltips}) et les catalyseurs JEI ({@code PiJeiPlugin.apiCatalysts}) sont deja tires des fiches.
 *
 * @param key        identifiant unique de la fiche
 * @param family     famille (onglet, section, pastille)
 * @param kind       genre
 * @param form       compacte ou controleur de multibloc
 * @param tiers      maniere de compter les tiers des blocs
 * @param age        age (machines {@link TierMode#AGE} : {@code mechanical}, {@code bronze})
 * @param power      energie motrice
 * @param blocks     ids des blocs dans l'ordre des tiers (inchanges : les mondes en dependent)
 * @param tiles      id du type de tile (un seul, ou un par bloc)
 * @param ports      ressources echangees (role et nombre de cases ou de reservoirs)
 * @param battery    case de batterie (recharge de la reserve d'energie ; canal ITEMS des faces)
 * @param faces      vrai : les canaux des faces de la tile sont les ressources des ports (+ objets pour la batterie ; controle par
 *                   l'instantane du lot 0)
 * @param recipes    source des recettes ({@code industrial:<cle>}, {@code mechanical:<sorte>}...) ou null
 * @param selector   la machine a le selecteur de recette
 * @param jei        types de recettes JEI dont tous les blocs de la fiche sont catalyseurs
 * @param multiblock cle de la fiche du multibloc frere (machine compacte), ou null
 * @param compact    cle de la fiche de la machine compacte soeur (multibloc), ou null
 * @param ui         vue de l'interface ({@code standard} ou vue propre)
 * @param special    ecran a parties speciales (reacteurs, fusion, calcul, simulateur, recherche, rail, ordinateur)
 * @param stats      fournisseur des lignes chiffrees de l'infobulle ({@link MachineTooltips})
 * @param logic      classe de la tile d'aujourd'hui (relative a {@code fr.pioneers})
 * @param migrated   la tile est la tile generique de l'API (lot 2 : ateliers et kit sur AtelierTile + unite de procede)
 * @param lot        lot du plan ou la logique migre
 * @param tier       1.0.5-beta (lot 38) : tier GLOBAL du bloc pour une fiche {@link TierMode#TIER} (T0 mecanique, T1 bronze, T2 MKI ... ;
 *                   -1 sinon : {@link #globalTier} le deduit du mode)
 */
public record MachineSpec(String key, String family, Kind kind, Form form, TierMode tiers, @Nullable String age, Power power, List<String> blocks,
                          List<String> tiles, List<PortSpec> ports, boolean battery, boolean faces, @Nullable String recipes, boolean selector, List<String> jei,
                          @Nullable String multiblock, @Nullable String compact, String ui, boolean special, String stats, @Nullable String logic,
                          boolean migrated, int lot, int tier) {

    public enum Kind { PROCESS, GENERATOR, BUFFER, EXTRACTOR, SERVICE, STATION }

    public enum Form { COMPACT, MULTIBLOCK }

    /**
     * MK : 4 blocs MKI..MKIV ; MK_CREATIVE : 4 + creatif ; SINGLE : un bloc sans tier affiche ; SINGLE_MKI : un bloc, tier MKI affiche ;
     * AGE : age mecanique ou du bronze (pastille d'age) ; STRUCTURE : multibloc dont le tier est celui des carcasses ; 1.0.5-beta (lot 38,
     * EN FIN d'enum) : TIER : un bloc d'un tier global donne ({@link #tier} : machines de traitement a vapeur, T1).
     */
    public enum TierMode { MK, MK_CREATIVE, SINGLE, SINGLE_MKI, AGE, STRUCTURE, TIER }

    public enum Power { FE_IN, FE_OUT, KINETIC_IN, KINETIC_OUT, STEAM_IN, FUEL, DATA, NONE }

    public enum Role { IN, OUT, BOTH }

    /** Une ressource echangee par la machine : type (celui des faces), sens, nombre de cases ou de reservoirs. */
    public record PortSpec(SideType resource, Role role, int count) {}

    /** Tier affiche d'un bloc de la fiche (0 = MKI ... 3 = MKIV, 4 = creatif), ou -1 sans tier. */
    public int tierOf(int blockIndex) {
        return switch (tiers) {
            case MK, MK_CREATIVE -> blockIndex;
            case SINGLE_MKI -> 0;
            default -> -1;
        };
    }

    /**
     * 1.0.5-beta (lot 38) : TIER GLOBAL du bloc d'indice {@code blockIndex} (T0 mecanique, T1 bronze, T2 MKI .. T5 MKIV ; -1 : sans tier,
     * creatif, ou multibloc dont le tier est celui de ses carcasses) : MK -> indice + 2, TIER -> {@link #tier}, AGE -> 0 (mecanique) ou 1
     * (bronze), SINGLE_MKI -> 2.
     */
    public int globalTier(int blockIndex) {
        return switch (tiers) {
            case MK, MK_CREATIVE -> blockIndex <= 3 ? blockIndex + 2 : -1;
            case SINGLE_MKI -> 2;
            case TIER -> tier;
            case AGE -> "mechanical".equals(age) ? 0 : "bronze".equals(age) ? 1 : -1;
            default -> -1;
        };
    }

    /** Type de tile du bloc d'indice {@code blockIndex}. */
    public String tileOf(int blockIndex) {
        return tiles.size() == 1 ? tiles.get(0) : tiles.get(blockIndex);
    }

    /** Nombre de cases ou de reservoirs de cette ressource dans ce sens (BOTH compte pour les deux). */
    public int count(SideType resource, Role role) {
        int n = 0;
        for (PortSpec p : ports) {
            if (p.resource() == resource && (p.role() == role || p.role() == Role.BOTH)) {
                n += p.count();
            }
        }
        return n;
    }

    public boolean isMultiblock() {
        return form == Form.MULTIBLOCK;
    }
}
