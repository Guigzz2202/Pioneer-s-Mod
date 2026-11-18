package fr.pioneers.gametest;

import fr.pioneers.tech.bronze.autofoundry.FoundryPartBlock;
import fr.pioneers.tech.bronze.autofoundry.FoundryVatBlock;
import fr.pioneers.core.common.PiRemaps;
import fr.pioneers.core.common.block.Connections;
import fr.pioneers.tech.industry.IndustryModule;
import fr.pioneers.tech.industry.construction.FireboxWallBlock;
import fr.pioneers.tech.industry.construction.PiConstruction;
import fr.pioneers.tech.industry.construction.StackedColumnBlock;
import fr.pioneers.tech.industry.construction.StackedColumnBlock.Part;
import fr.pioneers.tech.bronze.PiBronze;
import fr.pioneers.tech.industry.PiIndustry;
import fr.pioneers.logistics.transport.CableTier;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 1.0.4-beta (lot 23) : textures connectees reparees (un mur pose PAR UN CODE, dans l'ordre, sans joueur : chaque joint interieur raccorde
 * des deux cotes, aucun cote exterieur), suppressions (modules metiers sauf la baie de serveurs, barre de commande, pompe primaire a sodium,
 * echangeur intermediaire, injecteur de neutres, porte de salle blanche) remappees vers un bloc qui existe, ids renommes (anciens ids des
 * blocs, objets et recettes verrouillees -> nouveaux), parois de foyer eteintes par defaut et allumees par l'etat {@code lit}, cuve de
 * fonderie ouverte et connectee, train de forage en bronze.
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP104Lot23Tests {

    private PatchP104Lot23Tests() {
    }

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath("pioneers", path);
    }

    private static Block block(String id) {
        Block b = ForgeRegistries.BLOCKS.getValue(rl(id));
        Gt.check(b != null && ForgeRegistries.BLOCKS.containsKey(rl(id)), "bloc absent : " + id);
        return b;
    }

    /** Pose un mur n x n (plan x / y, a partir de x0) de l'etat PAR DEFAUT, comme un code (plan creatif, migration) ; positions absolues. */
    private static List<BlockPos> wall(GameTestHelper h, Block b, int x0, int z, int n) {
        List<BlockPos> out = new java.util.ArrayList<>();
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                BlockPos rel = new BlockPos(x0 + x, 1 + y, z);
                h.setBlock(rel, b.defaultBlockState());
                out.add(h.absolutePos(rel));
            }
        }
        return out;
    }

    /** Six cotes de chaque bloc = « le voisin est de la famille » (joints interieurs vrais des deux cotes, exterieur faux). */
    private static void connected(GameTestHelper h, List<BlockPos> blocks, String what) {
        ServerLevel level = h.getLevel();
        for (BlockPos p : blocks) {
            BlockState s = level.getBlockState(p);
            for (Direction d : Direction.values()) {
                boolean want = blocks.contains(p.relative(d));
                Gt.equal(want, s.getValue(Connections.property(d)), what + " " + p + " cote " + d);
            }
        }
    }

    // ================================================================== bug des textures connectees

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_23_connected", timeoutTicks = 20)
    public static void p104_23_murs_connectes_poses_par_un_code(GameTestHelper h) {
        Gt.begin(h);
        String[] ids = {"industrial_casing_basic", "industrial_casing_ultimate", "industrial_glass", "cleanroom_casing", "cleanroom_glass",
              "firebox_basic", "heating_coil_elite"};
        //un bloc d'air entre deux murs (deux familles voisines se raccorderaient entre elles) : deux colonnes de murs, un rang sur deux
        for (int i = 0; i < ids.length; i++) {
            Block b = block(ids[i]);
            Gt.check(b instanceof Connections.Connected, ids[i] + " : pas Connections.Connected");
            connected(h, wall(h, b, i % 2 == 0 ? 1 : 5, 1 + 2 * (i / 2), 3), ids[i]);
        }
        h.succeed();
    }

    // ================================================================== suppressions

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_23_removed", timeoutTicks = 20)
    public static void p104_23_suppressions_remappees(GameTestHelper h) {
        Gt.begin(h);
        Gt.check(PiRemaps.P104_LOT23.size() >= 32, "table des retraits du lot 23 : " + PiRemaps.P104_LOT23.size());
        for (Map.Entry<String, String> e : PiRemaps.P104_LOT23.entrySet()) {
            Gt.check(!ForgeRegistries.BLOCKS.containsKey(rl(e.getKey())), e.getKey() + " encore enregistre");
            Gt.check(!ForgeRegistries.ITEMS.containsKey(rl(e.getKey())), e.getKey() + " (objet) encore enregistre");
            Gt.check(ForgeRegistries.BLOCKS.containsKey(rl(e.getValue())), e.getKey() + " : remplacant absent " + e.getValue());
            Gt.equal(e.getValue(), PiRemaps.simplifiedBlock(e.getKey()), e.getKey() + " (bloc) -> " + e.getValue());
            Gt.equal(e.getValue(), PiRemaps.simplifiedItem(e.getKey()), e.getKey() + " (objet) -> " + e.getValue());
        }
        Gt.equal("industrial_casing_basic", PiRemaps.simplifiedBlock("control_rod_assembly"), "barre de commande -> carcasse MKI");
        Gt.equal("cleanroom_casing", PiRemaps.simplifiedBlock("cleanroom_door"), "porte de salle blanche -> paroi");
        //un module absorbe au lot 12 (-> module de cuve) finit sur la carcasse MKI
        Gt.equal("industrial_casing_basic", PiRemaps.simplifiedBlock("catalyst_bed_module"), "chaine lot 12 -> lot 23");
        //gardes : la baie de serveurs, le canal de combustible et le canal rapide
        Gt.equal(1, PiIndustry.MODULES.size(), "un seul module enregistre (baie de serveurs)");
        Gt.check(PiIndustry.MODULES.containsKey(IndustryModule.SERVER_RACK), "baie de serveurs");
        block("server_rack_module");
        block("fuel_channel");
        block("fast_fuel_channel");
        h.succeed();
    }

    // ================================================================== renommages

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_23_renamed", timeoutTicks = 20)
    public static void p104_23_anciens_ids_renommes(GameTestHelper h) {
        Gt.begin(h);
        int seen = 0;
        for (Map.Entry<String, String> e : PiRemaps.P104_LOT23_KEYS.entrySet()) {
            for (String pre : new String[]{"basic_", "advanced_", "elite_", "ultimate_", "industrial_", ""}) {
                String now = pre + e.getValue();
                if (!ForgeRegistries.BLOCKS.containsKey(rl(now))) {
                    continue;
                }
                String old = pre + e.getKey();
                seen++;
                Gt.check(!ForgeRegistries.BLOCKS.containsKey(rl(old)), old + " encore enregistre");
                Gt.equal(now, PiRemaps.simplifiedBlock(old), old + " (bloc) -> " + now);
                Gt.equal(now, PiRemaps.simplifiedItem(old), old + " (objet) -> " + now);
            }
        }
        Gt.check(seen >= 40, "machines renommees trouvees : " + seen);
        Gt.equal("fire_brick_firebox", PiRemaps.simplifiedBlock("foundry_firebox"), "foyer de fonderie -> foyer en briques refractaires");
        Gt.check(!ForgeRegistries.BLOCKS.containsKey(rl("foundry_firebox")), "foundry_firebox encore enregistre");
        Gt.equal(PiBronze.FIRE_BRICK_FIREBOX.getBlock(), block("fire_brick_firebox"), "foyer en briques refractaires");
        //recettes verrouillees d'avant (machines, objets) : nouvel identifiant ; jamais les machines de l'age mecanique
        Gt.equal(rl("industry/furnace/steel"), PiRemaps.recipeId(rl("industry/electric_furnace/steel")), "verrou : four industriel");
        Gt.equal(rl("industry/crafting/elite_press"), PiRemaps.recipeId(rl("industry/crafting/elite_hydraulic_press")), "verrou : presse MKIII");
        Gt.equal(rl("bronze/bench/fire_brick_firebox"), PiRemaps.recipeId(rl("bronze/bench/foundry_firebox")), "verrou : foyer");
        Gt.equal(rl("mechanics/sifter/gravel"), PiRemaps.recipeId(rl("mechanics/sifter/gravel")), "crible de l'age mecanique inchange");
        h.succeed();
    }

    // ================================================================== foyers

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_23_firebox", timeoutTicks = 20)
    public static void p104_23_foyers_eteints_puis_allumes(GameTestHelper h) {
        Gt.begin(h);
        ServerLevel level = h.getLevel();
        int x = 1;
        for (CableTier t : CableTier.MK) {
            FireboxWallBlock b = PiConstruction.FIREBOXES.get(t).getBlock();
            BlockPos rel = new BlockPos(x, 1, 1), up = rel.above();
            h.setBlock(rel, b.defaultBlockState());
            h.setBlock(up, b.defaultBlockState());
            BlockPos abs = h.absolutePos(rel);
            BlockState s = level.getBlockState(abs);
            Gt.check(!s.getValue(FireboxWallBlock.LIT), "foyer eteint par defaut : " + t);
            Gt.equal(0, s.getLightEmission(level, abs), "aucune lumiere eteint : " + t);
            //le controleur peint LIT : lumiere, raccords gardes
            level.setBlock(abs, s.setValue(FireboxWallBlock.LIT, true), Block.UPDATE_CLIENTS);
            BlockState lit = level.getBlockState(abs);
            Gt.equal(PiConstruction.FIREBOX_LIGHT, lit.getLightEmission(level, abs), "lumiere allume : " + t);
            Gt.check(lit.getValue(Connections.property(Direction.UP)), "raccord du dessus garde allume : " + t);
            level.setBlock(abs, lit.setValue(FireboxWallBlock.LIT, false), Block.UPDATE_CLIENTS);
            Gt.equal(0, level.getBlockState(abs).getLightEmission(level, abs), "eteint de nouveau : " + t);
            x += 2;
        }
        h.succeed();
    }

    // ================================================================== age du bronze

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_23_bronze", timeoutTicks = 20)
    public static void p104_23_cuve_connectee_et_train_bronze(GameTestHelper h) {
        Gt.begin(h);
        ServerLevel level = h.getLevel();
        //cuve 2 x 2 x 2 posee par un code : une seule cuve (joints raccordes, dessus et dessous libres)
        Block vat = PiBronze.FOUNDRY_VAT.getBlock();
        Gt.check(vat instanceof FoundryVatBlock && vat instanceof Connections.Connected, "cuve : FoundryVatBlock connecte");
        List<BlockPos> cells = new java.util.ArrayList<>();
        for (int y = 0; y < 2; y++) {
            for (int z = 0; z < 2; z++) {
                for (int x = 0; x < 2; x++) {
                    BlockPos rel = new BlockPos(1 + x, 1 + y, 1 + z);
                    h.setBlock(rel, vat.defaultBlockState());
                    cells.add(h.absolutePos(rel));
                }
            }
        }
        connected(h, cells, "cuve");
        //la chaleur peinte par le controleur garde les raccords
        BlockPos top = cells.get(cells.size() - 1);
        FoundryPartBlock.paint(level, top, 2, true);
        BlockState hot = level.getBlockState(top);
        Gt.equal(2, (int) hot.getValue(FoundryPartBlock.HEAT), "chaleur peinte");
        Gt.check(hot.getValue(Connections.property(Direction.WEST)) && !hot.getValue(Connections.property(Direction.UP)), "raccords gardes a la peinture");
        //train de forage en bronze : bloc empilable, ne se raccorde qu'a ses jumeaux (pas au MKI)
        StackedColumnBlock bronze = PiConstruction.DRILL_STRING_BRONZE.getBlock();
        Gt.check(bronze.tier == null && bronze.family.equals("drill_string"), "train de forage en bronze : famille, sans tier MK");
        Block mk1 = PiConstruction.STACKED.get("drill_string").get(CableTier.BASIC).getBlock();
        for (int y = 0; y < 3; y++) {
            h.setBlock(new BlockPos(6, 1 + y, 6), bronze.defaultBlockState());
        }
        h.setBlock(new BlockPos(6, 4, 6), mk1.defaultBlockState());
        Gt.equal(Part.BOTTOM, h.getBlockState(new BlockPos(6, 1, 6)).getValue(StackedColumnBlock.PART), "bronze bas");
        Gt.equal(Part.MIDDLE, h.getBlockState(new BlockPos(6, 2, 6)).getValue(StackedColumnBlock.PART), "bronze milieu");
        Gt.equal(Part.TOP, h.getBlockState(new BlockPos(6, 3, 6)).getValue(StackedColumnBlock.PART), "bronze haut (MKI au-dessus : autre bloc)");
        Gt.equal(Part.SINGLE, h.getBlockState(new BlockPos(6, 4, 6)).getValue(StackedColumnBlock.PART), "MKI seul");
        Gt.check(level.getRecipeManager().byKey(rl("building_blocks/drill_string_bronze")).isPresent(), "recette du train de forage en bronze");
        h.succeed();
    }
}
