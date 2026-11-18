// FICHIER GENERE par tools/gc/industry/nbt_shapes.py (1.0.4-beta, lot 24) depuis les NBT de Guillaume : NE PAS MODIFIER A LA MAIN.
package fr.pioneers.tech.industry.multi;

import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 24) : GABARITS DES MULTIBLOCS INDUSTRIELS d'apres les structures dessinees par Guillaume (NBT copies dans
 * {@code tools/gc/industry/nbt/dev_v2} ; table fichier -> machine : {@code INDUSTRIAL} de {@code T:industry/nbt_shapes.py}). Une fiche par machine : boite dans le repere du controleur (x a droite en regardant la facade, y en
 * haut, z vers l'interieur ; la facade est en z = 0), case du controleur, enveloppe et volume d'AVANT le lot (capacites et vitesses qui en
 * dependaient : valeurs gardees), tier fixe (salle blanche seule, -1 sinon), et une lettre par case (index ((y * sz) + z) * sx + x) :
 * K controleur, C carcasse, S tole, F cadre, G verre, B paroi de foyer, H bobine de chauffe, R rotor, D train de forage, P pompe a colonne,
 * W paroi de salle blanche, V vitre de salle blanche, L plafond filtrant, M module (baie de serveurs), U substrat, Z refrigerateur a dilution,
 * I air interieur, . case libre. Lue par {@link ShapeTemplate#of}. 1.0.4-beta (lot 26) : derogations documentees au NBT ({@code DEROGATIONS} :
 * colonne de rotors des turbines, baies de la cascade de centrifugeuses en air).
 */
public final class GeneratedNbtShapes {

    /** Une structure : cle de la machine, taille, controleur, valeurs d'avant, tier fixe, lettres. */
    public record Shape(String key, int sx, int sy, int sz, int cx, int cy, int cz, int legacyShell, int legacyVolume, int fixedTier,
                        String layout) {

        /** Lettre d'une case de la boite (0 <= x < sx...). */
        public char at(int x, int y, int z) {
            return layout.charAt((y * sz + z) * sx + x);
        }

        /**
         * 1.0.4-beta (lot 26) : machine de salle blanche (structure faite de parois de salle blanche) : controleur blanc, interface claire
         * ({@code IndustrialBodyModel}, {@code ControllerTile#guiTier}).
         */
        public boolean cleanroom() {
            return layout.indexOf('W') >= 0;
        }
    }

    private static final Map<String, Shape> BY_KEY = new HashMap<>();

    static {
        put("aquafarm", 5, 6, 5, 2, 0, 0, 223, 182, -1,
              "CCKCCCCCCCCCCCCCCCCCCCCCCCCCCCCSSSCCSSSCCSSSCCCCCCFSSSFSIIISSIIISSIIISFSSSFFSSSFSIIISSIIISSIIISFSSSFFSSSFSIIIS"
              + "SIIISSIIISFSSSF.FFF.FIIIFFIIIFFIIIF.FFF.");
        put("arc_furnace", 7, 6, 8, 3, 0, 0, 143, 114, -1,
              "..CKC...CCCCC..CCCCC.CCCCCCCCCCCCCCCCCCCCC.CCCCCC.CCCCC.............................F.FFF.F...F......F....CCCC"
              + "C...........S.....SSS....SSS..FFSSSFF..SSS....SSS...CCCCC....S.....SSS...SIIIS..SIIIS.FSIIISF.SIIIS..SSSSS..CC"
              + "CCC...........S.....SSS....SSS...FSSSF...SSS....SCS...CCCCC..................F......F.....FFF.....F......C...."
              + ".CCC..");
        put("assembler", 3, 3, 7, 1, 0, 0, 101, 33, -1,
              "CKCCCCCCCCCCCCCCCCCCCF.F......F.F......F.FFFFFIFFIFFFFFIFFIFFFF");
        put("autoclave", 5, 7, 5, 2, 0, 0, 174, 99, -1,
              ".CKC.CCCCCCCCCCCCCCC.CCC..SSS.SIIISSIIISSIIIS.SSS..SSS.SIIISSIIISSIIIS.SSS..SSS.SIIISSIIISSIIIS.SSS..SSS.SIIIS"
              + "SIIISSIIIS.SSS..SSS.SIIISSIIISSIIIS.SSS..CCC.CCCCCCCCCCCCCCC.CCC.");
        put("bayer_digester", 7, 9, 7, 3, 0, 0, 142, 114, -1,
              "..CKC...CCCCC.CCCCCCCCCCCCCCCCCCCCC.CCCCC...CCC....CCC...CCCCC.CCCCCCCCCCCCCCCCCCCCC.CCCCC...CCC....SSS...SSSS"
              + "S.SSSSSSSSSSSSSSSSSSSSS.SSSSS...SSS....SSS...SIIIS.SIIIIISSIIIIISSIIIIIS.SIIIS...SSS....SSS...SIIIS.SIIIIISSII"
              + "IIISSIIIIIS.SIIIS...SSS....SSS...SIIIS.SIIIIISSIIIIISSIIIIIS.SIIIS...SSS.....S....SSISS..SIIIS.SIIIIIS.SIIIS.."
              + "SSISS....S............SSS...SIIIS..SIIIS..SIIIS...SSS...................S.....SSS...SSSSS...SSS.....S........."
              + ".");
        put("bender", 3, 3, 5, 1, 0, 0, 65, 18, -1,
              "CKCCCCCCCCCCCCCFSFSISSISSISFSF.F.FSFFSFFSF.F.");
        put("blast_furnace", 5, 9, 5, 2, 0, 0, 383, 566, -1,
              ".BKB.BCCCBBCCCBBCCCB.BBB..HHH.HIIIHHIIIHHIIIH.HHH..HHH.HIIIHHIIIHHIIIH.HHH..HHH.HIIIHHIIIHHIIIH.HHH..CCC.CCICC"
              + "CIIICCCICC.CCC...C...CCC.CCICC.CCC...C........CCC..CIC..CCC.............C...CIC...C..............C...CIC...C.."
              + ".....");
        put("boiler", 3, 5, 3, 1, 0, 0, 283, 273, -1,
              "BKBBCBBBBSSSSISSSSSSSSISSSSSSSSISSSSSSSSSSSSS");
        put("bottler", 3, 3, 7, 1, 0, 0, 130, 45, -1,
              "CKCCCCCCCCCCCCCCCCCCCF.F......F.F......F.FFFFFIFFIFFFFFIFFIFFFF");
        put("casting_machine", 3, 4, 7, 1, 0, 0, 130, 45, -1,
              "CKCCCCCCCCCCCCCCCCCCCF.F......F.F......F.FF.F......F.F......F.FFFFFIFFIFFFFFIFFIFFFF");
        put("centrifuge", 5, 7, 5, 2, 0, 0, 114, 54, -1,
              ".CKC.CCCCCCCCCCCCCCC.CCC...F...SSS.FSSSF.SSS...F....F...SSS.FSISF.SSS...F....F...SSS.FSISF.SSS...F....F...SSS."
              + "FSISF.SSS...F....F...SSS.FSSSF.SSS...F....F....F..FFFFF..F....F..");
        put("centrifuge_cascade", 3, 6, 9, 1, 0, 0, 572, 793, -1,
              "CKCCCCCCCCCCCCCCCCCCCCCCCCCF.F...I.I...I.I...I.I...F.FF.F...I.I...I.I...I.I...F.FF.F...I.I...I.I...I.I...F.FF."
              + "F...I.I...I.I...I.I...F.FFFFFIFFFFFIFFFFFIFFFFFIFFFF");
        put("chemical_bath", 3, 3, 5, 1, 0, 0, 141, 90, -1,
              "CKCCCCCCCCCCCCCFSFSISSISSISFSFFSFSISSISSISFSF");
        put("chemical_reactor", 5, 5, 5, 2, 0, 0, 142, 114, -1,
              ".CKC.CCCCCCCCCCCCCCC.CCC..SSS.SSSSSSSSSSSSSSS.SSS..SSS.SIIISSIIISSIIIS.SSS..SSS.SIIISSIIISSIIIS.SSS...S...SSS."
              + "SSSSS.SSS...S..");
        put("coke_oven", 3, 4, 3, 1, 0, 0, 379, 315, -1,
              "BKBBCBBBBCCCCICCCCCCCCICCCC.C.CIC.C.");
        put("compressor", 5, 4, 4, 2, 0, 0, 45, 6, -1,
              "CCKCCCCCCCCCCCCCCCCCFSSSFSIIICSIIICFSSSFFSSSFSIIICSIIICFSSSF.FFF.FSSSFFSSSF.FFF.");
        put("converter", 5, 7, 5, 2, 0, 0, 178, 176, -1,
              ".BKB.BCCCBBCCCBBCCCB.BBB..CCC.CIIICCIIICCIIIC.CCC..CCC.CIIICCIIICCIIIC.CCC...C...CIC.CIIIC.CIC...C....C...CCC."
              + "CCICC.CCC...C.........C...CIC...C..............C...CIC...C.......");
        put("cracker", 5, 15, 5, 2, 0, 0, 243, 150, -1,
              "CCKCCCCCCCCCCCCCCCCCCCCCCF...F.SSS..SSS..SSS.F...FF...F.SSS..SIS..SSS.F...FF...F.SSS..SIS..SSS.F...FFFFFFFSSSF"
              + "FSISFFSSSFFFFFFF...F.SSS..SIS..SSS.F...FF...F.SSS..SIS..SSS.F...FF...F.SSS..SIS..SSS.F...FFFFFFFSSSFFSISFFSSSF"
              + "FFFFFF...F.SSS..SIS..SSS.F...FF...F.SSS..SIS..SSS.F...FF...F.SSS..SIS..SSS.F...F.F.F.FSSSF.SIS.FSSSF.F.F..F.F."
              + "FSSSF.SIS.FSSSF.F.F...F....S..FSSSF..S....F..");
        put("crusher", 5, 4, 7, 2, 0, 0, 141, 90, -1,
              "CCKCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCFSSSFSIIISSIIISSIIISSIIISSIIISFSSSFFSSSFSIIISSIIISSIIISSIIISSIIISFSSSF.FFF."
              + "FIIIFFIIIFFIIIFFIIIFFIIIF.FFF.");
        put("cryogenic_unit", 5, 7, 5, 2, 0, 0, 126, 63, -1,
              "CCKCCCCCCCCCCCCCCCCCCCCCCFSSSFSIIISSIIISSIIISFSSSFFSSSFSIIISSIIISSIIISFSSSFFSSSFSIIISSIIISSIIISFSSSFFSSSFSIIIS"
              + "SIIISSIIISFSSSFFSSSFSIIISSIIISSIIISFSSSF.FFF.FSSSFFSSSFFSSSF.FFF.");
        put("crystallizer", 5, 5, 5, 2, 0, 0, 142, 114, -1,
              "CCKCCCCCCCCCCCCCCCCCCCCCCCSSSCSIIISSIIISSIIISCSSSCCSSSCSIIISSIIISSIIISCSSSCCSSSCSIIISSIIISSIIISCSSSC.CCC.CCCCC"
              + "CCCCCCCCCC.CCC.");
        put("data_center", 5, 5, 8, 2, 0, 0, 342, 252, 2,
              "WWKWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWVVVWWMIMWWMIMWWMIMWWMIMWWMIMWWMIMWWWWWWWVVVWWMIMWWMIMWWMIMWWMIMWWMIMW"
              + "WMIMWWWWWWWVVVWWMIMWWMIMWWMIMWWMIMWWMIMWWMIMWWWWWWWWWWWWWLWWWWLWWWWLWWWWLWWWWLWWWWLWWWWWWW");
        put("distillation_column", 5, 24, 5, 2, 0, 0, 458, 504, -1,
              "CCKCCCCCCCCCCCCCCCCCCCCCCF...F.SSS..SSS..SSS.F...FF...F.SSS..SIS..SSS.F...FF...F.SSS..SIS..SSS.F...FFFFFFFSSSF"
              + "FSISFFSSSFFFFFFF...F.SSS..SIS..SSS.F...FF...F.SSS..SIS..SSS.F...FF...F.SSS..SIS..SSS.F...FFFFFFFSSSFFSISFFSSSF"
              + "FFFFFF...F.SSS..SIS..SSS.F...FF...F.SSS..SIS..SSS.F...FF...F.SSS..SIS..SSS.F...FFFFFFFSSSFFSISFFSSSFFFFFF.F.F."
              + "FSSSF.SIS.FSSSF.F.F..F.F.FSSSF.SIS.FSSSF.F.F..F.F.FSSSF.SIS.FSSSF.F.F..FFF.FSSSFFSISFFSSSF.FFF...F....S..FSISF"
              + "..S....F....F....S..FSISF..S....F....F....S..FSISF..S....F....F...FSF.FSISF.FSF...F.........S...SIS...S......."
              + ".......S...SIS...S..............S...SIS...S.......");
        put("drill", 5, 10, 5, 2, 0, 0, 147, 14, -1,
              ".CKC.CCCCCCCDCCCCCCC.CCC...F.......F.D.F.......F....F.......F.D.F.......F....F.......F.D.F.......F.........F.."
              + ".FDF...F..............F...FDF...F..............F...FDF...F...................F........................F......."
              + ".................F............");
        put("dryer", 6, 6, 3, 3, 0, 0, 283, 273, -1,
              ".CBKBC.CCCCC.CBBBC..SSS..FSSSF..SSS...SSS..FSISF..SSS...SSS..FSISF..SSS...SSS..FSSSF..SSS........FFFFF......");
        put("electric_crucible", 7, 7, 6, 3, 0, 0, 78, 36, -1,
              "..BKB...BCCCB.CCCCCCC.BCCCB...BCB.....C.....SSS...SSSSS.FSSSSSF.SSSSS...SSS.....F.....SSS...SIIIS.FSIIISF.SIII"
              + "S...SSS.....F.....SSS...SIIIS.FSIIISF.SIIIS...SSS.....F.....SSS...SIIIS.FSIIISF.SIIIS...SSS.....F.....SSS...SI"
              + "IIS.FSIIISF.SIIIS...SSS.....F.....SSS...SIIIS.FSIIISF.SIIIS...SSS.....F...");
        put("electrolyzer", 3, 3, 5, 1, 0, 0, 141, 90, -1,
              "CKCCCCCCCCCCCCCFSFSISSISSISFSFFSFSISSISSISFSF");
        put("exhaust_stack", 3, 15, 3, 1, 0, 0, 74, 7, -1,
              "CKCCCCCCCCCCCICCCCFCFCICFCFFCFCICFCFFCFCICFCFFCFCICFCFFCFCICFCF.C.CIC.C..C.CIC.C..C.CIC.C..C.CIC.C..C.CIC.C..C"
              + ".CIC.C..S.SIS.S..S.SIS.S.");
        put("extractor", 5, 6, 5, 2, 0, 0, 126, 63, -1,
              ".CKC.CCCCCCCCCCCCCCC.CCC...F...SSS.FSISF.SSS...F....F...SSS.FSISF.SSS...F....F...SSS.FSISF.SSS...F....F...SSS."
              + "FSSSF.SSS...F...CCC.CCCCCCCCCCCCCCC.CCC.");
        put("extruder", 3, 3, 5, 1, 0, 0, 174, 99, -1,
              "CKCCCCCCCCCCCCCFSFSISSISSISFSF.F.FSFFSFFSF.F.");
        put("fermenter", 6, 6, 5, 3, 0, 0, 162, 90, -1,
              "..CKC..CCCCC.CCCCC.CCCCC..CCC...SSS..SSSSS.SSSSS.SSSSS..SSS...SSS..SIIIS.SIIIS.SIIIS..SSS...SSS..SIIIS.SIIIS.S"
              + "IIIS..SSS....S....SSS..SSISS..SSS....S...........S....SSS....S........");
        put("fluid_pump", 5, 5, 5, 2, 0, 0, 86, 9, -1,
              ".CKC.CCCCCCCCCCCCCCC.CCC...F.......F.P.F.......F....F.......F.P.F.......F....F.......F.P.F.......F.........F.."
              + ".FFF...F.......");
        put("food_processor", 3, 5, 3, 1, 0, 0, 142, 114, -1,
              "CKCCCCCCCFSFSISFSFFSFSISFSFFSFSISFSF.S.SSS.S.");
        put("furnace", 3, 4, 3, 1, 0, 0, 143, 114, -1,
              "BKBBCBBBBSSSSISSSSSSSSISSSSSSSSSSSSS");
        put("gas_holder", 5, 8, 5, 2, 0, 0, 294, 349, -1,
              "CCKCCCCCCCCCCCCCCCCCCCCCCFSSSFSSSSSSSSSSSSSSSFSSSFFSSSFSIIISSIIISSIIISFSSSFFSSSFSIIISSIIISSIIISFSSSFFSSSFSIIIS"
              + "SIIISSIIISFSSSFFSSSFSIIISSIIISSIIISFSSSF.FSF.FSSSFSSISSFSSSF.FSF...F....S..FSSSF..S....F..");
        put("gas_separator", 5, 10, 5, 2, 0, 0, 174, 99, -1,
              "CCKCCCCCCCCCCCCCCCCCCCCCC..F...SSS.FSSSF.SSS...F....F...SSS.FSSSF.SSS...F....F...FFF.FFSFF.FFF...F....F...SSS."
              + "FSSSF.SSS...F....F...SSS.FSSSF.SSS...F....F...FFF.FFSFF.FFF...F....F...SSS.FSSSF.SSS...F....F...SSS.FSSSF.SSS."
              + "..F....F....F..FFFFF..F....F..");
        put("gas_turbine", 5, 9, 5, 2, 0, 0, 175, 99, -1,
              "CCKCCCCCCCCCCCCCCCCCCCCCCFGGGFGIIIGGIRIGGIIIGFGGGFFGGGFGIIIGGIRIGGIIIGFGGGFFGGGFGIIIGGIRIGGIIIGFGGGFFGGGFGIIIG"
              + "GIRIGGIIIGFGGGFFGGGFGIIIGGIRIGGIIIGFGGGFFGGGFGIIIGGIRIGGIIIGFGGGFFGGGFGIIIGGIRIGGIIIGFGGGFCCCCCCCCCCCCCCCCCCCC"
              + "CCCCC");
        put("hot_blast_stove", 5, 7, 5, 2, 0, 0, 222, 219, -1,
              ".CKC.CCCCCCCCCCCCCCC.CCC...F...SSS.FSSSF.SSS...F....F...SSS.FSSSF.SSS...F....F...SSS.FSSSF.SSS...F....F...FFF."
              + "FFSFF.FFF...F....F...SSS.FSSSF.SSS...F.........F...FFF...F.......");
        put("hot_cell", 7, 7, 7, 3, 0, 0, 194, 100, -1,
              ".CCKCC.CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC.CCCCC..SSSSS.SIIIIISSIIIIISSIIIIISSIIIIISSIIIIIS.SSSSS..SSSSS.SIIII"
              + "ISSIIIIISSIIIIISSIIIIISSIIIIIS.SSSSS..SSSSS.SIIIIISSIIIIISSIIIIISSIIIIISSIIIIIS.SSSSS..SSSSS.SIIIIISSIIIIISSII"
              + "IIISSIIIIISSIIIIIS.SSSSS..SSSSS.SIIIIISSIIIIISSIIIIISSIIIIISSIIIIIS.SSSSS..CCCCC.CCCCCCCCCCCCCCCCCCCCCCCCCCCCC"
              + "CCCCCC.CCCCC.");
        put("hydroponic_farm", 5, 6, 5, 2, 0, 0, 572, 793, -1,
              "CCKCCCCCCCCCCCCCCCCCCCCCCCCCCCCUUUCCUUUCCUUUCCCCCCFGGGFGIIIGGIIIGGIIIGFGGGFFGGGFGIIIGGIIIGGIIIGFGGGFFGGGFGIIIG"
              + "GIIIGGIIIGFGGGF.FFF.FGGGFFGGGFFGGGF.FFF.");
        put("item_silo", 5, 11, 5, 2, 0, 0, 102, 45, -1,
              "CCKCCCCCCCCCCCCCCCCCCCCCCFSSSFSIIISSIIISSIIISFSSSFFSSSFSIIISSIIISSIIISFSSSFFSSSFSIIISSIIISSIIISFSSSFFSSSFSIIIS"
              + "SIIISSIIISFSSSFFSSSFSIIISSIIISSIIISFSSSFFSSSFSIIISSIIISSIIISFSSSFFSSSFSIIISSIIISSIIISFSSSF.FSF.FSISFSIIISFSISF"
              + ".FSF...F...SSS.FSISF.SSS...F.........S...SSS...S.......");
        put("kroll_reactor", 7, 7, 11, 3, 0, 0, 114, 54, -1,
              "CCCKCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC...F....FSSSF...SSS....SSS....SSS"
              + "...FSSSF...SSS....SSS....SSS...FSSSF....F......F...FSSSSSF.SIIIS..SIIIS..SIIIS.FSIIISF.SIIIS..SIIIS..SIIIS.FSS"
              + "SSSF...F...FFFFFFFFSSSSSFFSIIISFFSIIISFFSIIISFFSIIISFFSIIISFFSIIISFFSIIISFFSSSSSFFFFFFFF...F...FSSSSSF.SIIIS.."
              + "SIIIS..SIIIS.FSIIISF.SIIIS..SIIIS..SIIIS.FSSSSSF...F......F....FSSSF...SSS....SSS....SSS...FSSSF...SSS....SSS."
              + "...SSS...FSSSF....F......F.....FFF.....F......F......F.....FFF.....F......F......F.....FFF.....F...");
        put("liquid_combustion_generator", 5, 3, 2, 2, 0, 0, 175, 99, -1,
              "CCKCCCCCCCSS.SSSSCSSSS.SSSS.SS");
        put("machining_center", 3, 3, 7, 1, 0, 0, 130, 45, -1,
              "CKCCCCCCCCCCCCCCCCCCCF.F......F.F......F.FFFFFIFFIFFFFFIFFIFFFF");
        put("mixer", 7, 5, 3, 3, 0, 0, 296, 4, -1,
              "CCCKCCCCCCCCCCCCCCCCCFSF.FSFSSS.SSSFSF.FSFFSF.FSFSIS.SISFSF.FSFFSF.FSFSSS.SSSFSF.FSF.F...F.FFFFFFF.F...F.");
        put("offshore_platform", 5, 1, 5, 2, 0, 0, 25, 25, -1,
              "CCKCCCCCCCCCCCCCCCCCCCCCC");
        put("ore_separator", 5, 3, 7, 2, 0, 0, 141, 90, -1,
              "CCKCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCFSSSFSIIISSIIISSIIISSIIISSIIISFSSSFFSSSFSIIISSIIISSIIISSIIISSIIISFSSSF");
        put("photolithograph", 5, 5, 5, 2, 0, 0, 386, 407, 2,
              "WWKWWWWWWWWWWWWWWWWWWWWWWWVVVWVIIIVVIIIVVIIIVWVVVWWVVVWVIIIVVIIIVVIIIVWVVVWWVVVWVIIIVVIIIVVIIIVWVVVWWWWWWWLLLW"
              + "WLLLWWLLLWWWWWW");
        put("pick_and_place", 3, 3, 7, 1, 0, 0, 130, 45, -1,
              "CKCCCCCCCCCCCCCCCCCCCF.F......F.F......F.FFFFFIFFIFFFFFIFFIFFFF");
        put("power_hammer", 5, 4, 3, 2, 0, 0, 89, 45, -1,
              "CCKCCCCCCCCCCCCF...F.....F...FF...F.....F...F.FFF.FCCCF.FFF.");
        put("press", 5, 4, 3, 2, 0, 0, 89, 45, -1,
              "CCKCCCCCCCCCCCC.....F...F..........F...F..........FFFFF.....");
        put("pumpjack", 3, 7, 11, 1, 0, 0, 62, 5, -1,
              "CKCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC......F.F................P..C..C.......F.F................P.....F......"
              + ".F.F................P.....F.......F.F................P.....F........F.................F.....F..F..F..F..F..F.."
              + "F..F..F..F.");
        put("pyrolysis_oven", 3, 4, 5, 1, 1, 0, 175, 99, -1,
              "BBBBCBBCBBCBBBBCKCHCHHCHHCHCCCCCCHCHHCHHCHCCC.C..H..H..H..C.");
        put("quantum_computer", 7, 7, 7, 3, 0, 0, 143, 114, 3,
              "..WKW...WWWWW.WWWWWWWWWWWWWWWWWWWWW.WWWWW...WWW....VVV...VIIIV.VIIIIIVVIIIIIVVIIIIIV.VIIIV...VVV....VVV...VIII"
              + "V.VIIIIIVVIIIIIVVIIIIIV.VIIIV...VVV....VVV...VIIIV.VIIIIIVVIIIIIVVIIIIIV.VIIIV...VVV....VVV...VIIIV.VIIIIIVVII"
              + "IIIVVIIIIIV.VIIIV...VVV....VVV...VIIIV.VIIIIIVVIIZIIVVIIIIIV.VIIIV...VVV....WWW...WLLLW.WLWWWLWWLWWWLWWLWWWLW."
              + "WLLLW...WWW..");
        put("recycler", 5, 6, 5, 2, 0, 0, 141, 90, -1,
              "CCKCCCCCCCCCCCCCCCCCCCCCCFSSSFSIIISSIIISSIIISFSSSFFSSSFSIIISSIIISSIIISFSSSFFSSSFSIIISSIIISSIIISFSSSFFSSSFSIIIS"
              + "SIIISSIIISFSSSF.FFF.FIIIFFIIIFFIIIF.FFF.");
        put("rolling_mill", 3, 3, 7, 1, 0, 0, 159, 57, -1,
              "CKCCCCCCCCCCCCCCCCCCCF.F......F.F......F.FFFFFIFFIFFFFFIFFIFFFF");
        put("sawmill", 3, 3, 7, 1, 0, 0, 130, 45, -1,
              "CKCCCCCCCCCCCCCCCCCCCF.F......F.F......F.FFFFFIFFIFFFFFIFFIFFFF");
        put("semiconductor_fab", 5, 5, 9, 2, 0, 0, 572, 793, 2,
              "WWKWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWVVVWWIIIWWIIIWWIIIWWIIIWWIIIWWIIIWWIIIWWWWWWWVVVWVIIIVVIIIVVIIIV"
              + "VIIIVVIIIVVIIIVVIIIVWVVVWWVVVWWIIIWWIIIWWIIIWWIIIWWIIIWWIIIWWIIIWWWWWWWWWWWWWWWWWLWLWWLWLWWLWLWWLWLWWLWLWWWWWW"
              + "WWWWW");
        put("solid_combustion_generator", 5, 4, 3, 2, 0, 0, 175, 99, -1,
              "CBKBCCCCCCCBBBCCSSSCCIIICCSSSCCSSSCCIIICCSSSC.....CSSSC.....");
        put("spent_fuel_pool", 5, 4, 7, 2, 0, 0, 277, 308, -1,
              "CCKCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCSSSCCSSSCCSSSCCSSSCCSSSCCCCCCFSSSFSIIISSIIISSIIISSIIISSIIISFSSSFFSSSF"
              + "SIIISSIIISSIIISSIIISSIIISFSSSF");
        put("steam_turbine", 5, 9, 5, 2, 0, 0, 282, 273, -1,
              "CCKCCCCCCCCCCCCCCCCCCCCCCFGGGFGIIIGGIRIGGIIIGFGGGFFGGGFGIIIGGIRIGGIIIGFGGGFFGGGFGIIIGGIRIGGIIIGFGGGFFGGGFGIIIG"
              + "GIRIGGIIIGFGGGFFGGGFGIIIGGIRIGGIIIGFGGGFFGGGFGIIIGGIRIGGIIIGFGGGFFGGGFGIIIGGIRIGGIIIGFGGGFCCCCCCCCCCCCCCCCCCCC"
              + "CCCCC");
        put("storage_tank", 5, 13, 5, 2, 0, 0, 202, 168, -1,
              "CCKCCCCCCCCCCCCCCCCCCCCCCF...F...............F...FF...F..S...SSS...S..F...FF...F.SSS..SIS..SSS.F...FF.S.F.SSS."
              + "SSISS.SSS.F.S.FFSSSFSIIISSIIISSIIISFSSSFFSSSFSIIISSIIISSIIISFSSSFFSSSFSIIISSIIISSIIISFSSSFFSSSFSIIISSIIISSIIIS"
              + "FSSSFFSSSFSIIISSIIISSIIISFSSSF.FSF.FSSSFSSISSFSSSF.FSF...F...SSS.FSISF.SSS...F.........S...SSS...S.......");
        put("tree_plantation", 7, 8, 7, 3, 0, 0, 668, 1027, -1,
              "..CKC...CCCCC.CCCCCCCCCCCCCCCCCCCCC.CCCCC...CCC....CCC...CCCCC.CCUUUCCCCUUUCCCCUUUCC.CCCCC...CCC....GGG...GIII"
              + "G.GIIIIIGGIIIIIGGIIIIIG.GIIIG...GGG....GGG...GIIIG.GIIIIIGGIIIIIGGIIIIIG.GIIIG...GGG....GGG...GIIIG.GIIIIIGGII"
              + "IIIGGIIIIIG.GIIIG...GGG....GGG...GIIIG.GIIIIIGGIIIIIGGIIIIIG.GIIIG...GGG....GGG...GIIIG.GIIIIIGGIIIIIGGIIIIIG."
              + "GIIIG...GGG....CCC...CCCCC.CCGGGCCCCGGGCCCCGGGCC.CCCCC...CCC..");
        put("wiremill", 3, 3, 7, 1, 0, 0, 130, 45, -1,
              "CKCCCCCCCCCCCCCCCCCCCF.F......F.F......F.FFFFFIFFIFFFFFIFFIFFFF");
    }

    private GeneratedNbtShapes() {
    }

    private static void put(String key, int sx, int sy, int sz, int cx, int cy, int cz, int shell, int volume, int fixedTier, String layout) {
        BY_KEY.put(key, new Shape(key, sx, sy, sz, cx, cy, cz, shell, volume, fixedTier, layout));
    }

    /** Structure de la machine (cle du catalogue), ou null (multibloc sans NBT : gabarit d'avant). */
    @Nullable
    public static Shape of(String key) {
        return BY_KEY.get(key);
    }

    /** Nombre de structures. */
    public static int count() {
        return BY_KEY.size();
    }
}
