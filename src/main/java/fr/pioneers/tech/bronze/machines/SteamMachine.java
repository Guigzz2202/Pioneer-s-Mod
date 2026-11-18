package fr.pioneers.tech.bronze.machines;

import java.util.Locale;

/**
 * Les machines de traitement a la vapeur de l'age du bronze. Chacune a ses recettes {@code pioneers:steam_processing} (champ
 * {@code machine}), sauf le four qui reprend les cuissons vanilla. {@code inputs} = cases d'entree (4 pour le melangeur).
 * <p>
 * 1.0.4-beta (lot 11, fusion des machines) : le fondeur d'alliages a vapeur est SUPPRIME (meme age, meme role que le fourneau a creuset, qui
 * fait deja ses trois alliages : bronze, laiton, soudure) ; son bloc est remappe vers le fourneau a creuset ({@code PiRemaps.p104Block}).
 * <p>
 * 1.0.5-beta (lot 38, retours 49, 52, 53) : chaque machine est le TIER 1 de sa famille ({@link #family}) : elle fait toutes les recettes de sa
 * famille de tier minimal <= 1 (moteur {@code api.machine.tier.TierRecipes}), plus vite et a meilleur rendement que la machine mecanique.
 */
public enum SteamMachine {
    //1.0.5-beta (lot 38) : le compacteur a 2 entrees (matiere + liant, specification §2.3)
    COMPRESSOR(2, 0x9FB8C8),
    EXTRACTOR(1, 0x7FA24A),
    MACERATOR(1, 0x9A9488),
    FURNACE(1, 0xE0631E),
    WIREMILL(1, 0xD88A4E),
    ROLLING_MILL(1, 0xB8C2CC),
    SAW(1, 0x9C7A52),
    /**
     * 1.0.3-beta (lot 11b, Guillaume : « un melangeur a vapeur pour automatiser la production de beton ») : gravier + sable + chaux vive
     * (+ colorant) + eau du reservoir -> beton ; sans eau -> poudre de beton ; poudre + eau -> beton. Seule machine a vapeur a reservoir
     * ({@link SteamProcess#water()}), recettes {@code pioneers:steam_processing} avec le champ {@code water}.
     */
    MIXER(4, 0xA8A49A),
    /**
     * 1.0.5-beta (lot 38, specification age du bronze §3.5 ; EN FIN d'enum : l'ordinal part dans les recettes synchronisees) : crible a
     * vapeur, T1 de la famille « tamisage » : recettes du tamis a secousses (x1,5, deux fois plus vite) ; 1 entree, 6 sorties (celles du
     * tamis a secousses).
     */
    SIFTER(1, 6, 0xC2A86A),
    /** 1.0.5-beta (lot 38, §3.4) : tour a vapeur, T1 de la famille « tournage » : tiges, anneaux, ressorts, boulons, vis, billes d'acier. */
    LATHE(1, 2, 0x8FA6B8),
    /**
     * 1.0.5-beta (lot 38, §3.6) : presse a vapeur, T1 de la famille « presse » : elle APLATIT (lingot + matrice -> plaque, plaque -> feuilles,
     * stratifies) ; avant : etabli automatique du metallier et du chaudronnier (SteamWorkshopTile, retire). Meme bloc, meme type de tile.
     */
    PRESS(2, 2, 0x6E82A0),
    /**
     * 1.0.5-beta (lot 39, retour 51 ; specification age du bronze §4.1 ; EN FIN d'enum) : four a induction a vapeur, T1 de la famille
     * « induction » : alliages DIRECTS solide + solide (+ solide) = solide (bronze, laiton, soudure, alliage rouge, acier rouge, carters en
     * fonte) ; 3 entrees, 1 sortie, ni fluide ni gaz ; 6 mB/t de vapeur ({@link #steamUse}, le double des autres).
     */
    INDUCTION_FURNACE(3, 1, 0xE0783C);

    public final int inputs;
    /** 1.0.5-beta (lot 38) : cases de sortie (produit, sous-produit ; 6 pour le crible). */
    public final int outputs;
    public final int accent;

    SteamMachine(int inputs, int accent) {
        this(inputs, 2, accent);
    }

    SteamMachine(int inputs, int outputs, int accent) {
        this.inputs = inputs;
        this.outputs = outputs;
        this.accent = accent;
    }

    /**
     * 1.0.5-beta (lot 38) : cases d'entree d'avant le lot 38 (sauvegardes sans le format {@code pfmt}) : le compacteur passe de 1 a 2 entrees
     * (matiere + liant, specification §2.3) ; les autres sont inchangees.
     */
    public int legacyInputs() {
        return this == COMPRESSOR ? 1 : inputs;
    }

    /**
     * 1.0.5-beta (lot 39) : vapeur prise par tick de travail (mB) : 3 ({@code SteamRules.PROCESS_USE}), 6 pour le four a induction
     * (source unique : tools/gc/api/tier_families.py STEAM_USE_OF).
     */
    public double steamUse() {
        return fr.pioneers.api.machine.tier.GeneratedTierFamilies.steamUse(id());
    }

    /** 1.0.5-beta (lot 38) : famille de tiers de la machine (tools/gc/api/tier_families.py STEAM_FAMILY). */
    public String family() {
        return fr.pioneers.api.machine.tier.GeneratedTierFamilies.STEAM_FAMILY.get("steam:" + key());   // cle = source « steam:<machine> »
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Identifiant du bloc. */
    public String id() {
        return "steam_" + key();
    }

    public static SteamMachine byKey(String key) {
        for (SteamMachine m : values()) {
            if (m.key().equals(key)) {
                return m;
            }
        }
        throw new IllegalArgumentException("machine a vapeur inconnue : " + key);
    }
}
