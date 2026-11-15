package fr.pioneers.farm.crustacean;

/** GENERE par tools/gc/farm2/crustacean_java.py depuis crustaceans.py : especes de crustaces. Ne pas modifier a la main. */
public final class GeneratedCrustaceans {

    private GeneratedCrustaceans() {
    }

    public static final String[] SHAPES = {"crab", "king", "hermit", "lobster", "shrimp"};

    public static final CrustaceanDef[] SPECIES = {
        new CrustaceanDef("green_crab", 0, 1.0F, false, 96, 5, 9, 0x546C38, "raw_green_crab", 6, 100, true, 2, 0.2F, 6, 0.6F),
        new CrustaceanDef("hermit_crab", 2, 0.9F, false, 192, 2, 5, 0xD6683E, "", 4, 100, false, 2, 0.2F, 6, 0.6F),
        new CrustaceanDef("lobster", 3, 1.0F, true, 96, 25, 50, 0x283A5C, "raw_lobster", 10, 60, false, 3, 0.3F, 8, 0.8F),
        new CrustaceanDef("crayfish", 3, 0.55F, true, 15, 8, 15, 0x704228, "raw_crayfish", 4, 100, false, 1, 0.1F, 4, 0.5F),
        new CrustaceanDef("shrimp", 4, 0.6F, true, 192, 5, 12, 0xE2AC96, "raw_shrimp", 3, 120, false, 1, 0.1F, 4, 0.5F),
        new CrustaceanDef("king_crab", 1, 1.25F, true, 288, 60, 150, 0xA43428, "raw_king_crab", 14, 30, true, 3, 0.3F, 10, 0.9F)
    };
}
