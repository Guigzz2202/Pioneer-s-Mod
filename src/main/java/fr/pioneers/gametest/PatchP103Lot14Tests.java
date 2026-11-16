package fr.pioneers.gametest;

import fr.pioneers.tech.bronze.steam.AccessoryBlock;
import fr.pioneers.tech.bronze.steam.BoilerPart;
import fr.pioneers.tech.bronze.steam.FireboxTile;
import fr.pioneers.tech.bronze.steam.SteamAccessory;
import fr.pioneers.tech.bronze.steam.SteamRules;
import fr.pioneers.farm.food.DishShelfBlock;
import fr.pioneers.farm.food.ShelfJoints;
import fr.pioneers.building.home.basket.BasketHeap;
import fr.pioneers.building.home.basket.BasketTile;
import fr.pioneers.tech.forge.smithing.ToolRackBlock;
import fr.pioneers.tech.forge.smithing.ToolRackLayout;
import fr.pioneers.tech.forge.smithing.ToolRackTile;
import fr.pioneers.core.registries.PiForge;
import net.minecraft.world.level.block.Blocks;
import fr.pioneers.core.registries.PiBronze;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Patch 1.0.3-beta, lot 14 (GameTests) : une chaudiere DEMARRE de bout en bout (Guillaume, 06/11 : « il est desormais impossible de demarrer
 * une chaudiere » ; cause : debordement de {@code now - Long.MIN_VALUE} dans la coupure de surete du lot 7, toute chaudiere formee etait
 * « coupee ») : sans surete et avec surete au-dessus de son seuil, le feu prend, l'eau chauffe, bout et la vapeur monte ; avec surete et une
 * chaudiere vide, le foyer reste coupe, puis repart quand l'eau monte.
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP103Lot14Tests {

    private PatchP103Lot14Tests() {
    }

    /** Foyer + corps de cuivre + dome, cheminee de 8 (tirage x1,6), 16 charbons ; surete sur le flanc est du corps si demandee. */
    private static FireboxTile boiler(GameTestHelper h, BlockPos at, boolean safety) {
        //1.0.6 : forme de Guillaume du lot 24 (l'ancienne colonne n'est plus formee) ; at = coin de la forme
        FireboxTile f = MultiblockApi6Tests.boiler(h, at, BoilerPart.MAX_CHIMNEY, safety ? PiBronze.ACCESSORIES.get(SteamAccessory.SAFETY_VALVE)
              .getBlock().defaultBlockState().setValue(AccessoryBlock.FACING, Direction.EAST) : null);
        Gt.equal(BoilerPart.MAX_CHIMNEY, f.guiValue(11), "cheminee de 8 comptee");
        //1.0.6 : charbon mis APRES le premier tick de la surete (comme en jeu : l'accessoire est deja la quand on charge le foyer) ; avant,
        //le foyer pose avant la surete brulait un charbon a son premier tick
        h.runAfterDelay(2, () -> f.guiSlots().get(0).setStack(new ItemStack(Items.COAL, 16)));
        return f;
    }

    @GameTest(template = Gt.BORDER, batch = "pioneers_p103_14_boiler_start", timeoutTicks = 1600)
    public static void p103_14_chaudiere_demarre(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Gt.check(!SteamRules.cutActive(h.getLevel().getGameTime(), Long.MIN_VALUE), "jamais coupee : le foyer n'est pas coupe");
        FireboxTile free = boiler(h, new BlockPos(3, 1, 3), false), safe = boiler(h, new BlockPos(14, 1, 14), true);
        //1.0.6 : quantites en part de la contenance et temps de chauffe de la chaudiere de Guillaume (11 corps : 11 fois la masse de
        //l'ancienne colonne, ebullition vers 1 250 ticks au lieu de 800 avec une cheminee de 8)
        int cap = free.waterCapacity();
        free.fillWater(cap / 4, false);                              // 25 %
        safe.fillWater(cap / 4, false);                              // au-dessus du seuil de 20 %
        h.runAtTickTime(40, () -> {
            for (FireboxTile f : new FireboxTile[]{free, safe}) {
                Gt.check(!f.cut(), "eau a 25 % : le foyer n'est pas coupe");
                Gt.check(f.guiValue(13) == 1, "etat d'interface : en marche (pas « coupee »)");
                Gt.check(f.guiSlots().get(0).getStack().getCount() < 16, "le feu a pris : un charbon est entame");
                Gt.check(f.guiValue(5) > 0, "foyer allume");
                Gt.check(f.temperature() > 20.5, "l'eau chauffe");
            }
        });
        h.runAtTickTime(1_500, () -> {
            for (FireboxTile f : new FireboxTile[]{free, safe}) {
                Gt.check(f.temperature() >= SteamRules.BOIL_C - 0.01, "ebullition atteinte");
                Gt.check(f.steamVolume().steam() > 100, "la vapeur monte dans la reserve");
                Gt.check(f.water() < cap / 4, "l'eau se vaporise");
            }
            s.done();
        });
    }

    @GameTest(template = Gt.BORDER, batch = "pioneers_p103_14_boiler_refill", timeoutTicks = 200)
    public static void p103_14_surete_vide_puis_eau_qui_monte(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        FireboxTile f = boiler(h, new BlockPos(3, 1, 3), true);
        h.runAtTickTime(30, () -> {
            Gt.check(f.cut(), "chaudiere vide + surete : foyer coupe (pas de chauffe a sec)");
            Gt.check(f.guiValue(13) == 2, "etat d'interface : coupee par la surete");
            Gt.equal(16, f.guiSlots().get(0).getStack().getCount(), "foyer coupe : aucun charbon brule");
            f.fillWater(f.waterCapacity() / 2, false);               // l'eau monte a 50 %
        });
        h.runAtTickTime(90, () -> {
            Gt.check(!f.cut(), "eau a 50 % : la chaudiere repart seule");
            Gt.check(f.guiSlots().get(0).getStack().getCount() < 16, "le feu a pris");
            s.done();
        });
    }

    // ================================================================== etageres en escalier
    private static void settle(GameTestHelper h, Gt.Scope s, BlockPos... cells) {
        for (int pass = 0; pass < 3; pass++) {
            for (BlockPos p : cells) {
                BlockState st = h.getBlockState(p);
                if (st.getBlock() instanceof DishShelfBlock) {
                    h.setBlock(p, DishShelfBlock.connect(st, s.level, h.absolutePos(p)));
                }
            }
        }
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_14_shelves", timeoutTicks = 20)
    public static void p103_14_etageres_en_escalier(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Gt.check(!ShelfJoints.beside(new ShelfJoints.Cell(2, false, false, true), new ShelfJoints.Cell(2, false, false, false)),
              "regle pure : pas de liaison entre un module surmonte et un module seul");
        BlockState floor = DishShelfBlock.VARIANTS.get("oak").getBlock().defaultBlockState().setValue(DishShelfBlock.FACING, Direction.NORTH);
        BlockPos a0 = new BlockPos(3, 1, 3), a1 = a0.above(), b0 = a0.east(), b1 = b0.above();
        //1.0.6 : sol pose sous les etageres (le gabarit n'a pas de sol plein en y = 0 a cet endroit : l'etagere au sol tombait)
        h.setBlock(a0.below(), Blocks.STONE.defaultBlockState());
        h.setBlock(b0.below(), Blocks.STONE.defaultBlockState());
        h.setBlock(a0, floor);
        h.setBlock(a1, floor);
        h.setBlock(b0, floor);
        settle(h, s, a0, a1, b0);
        Gt.check(h.getBlockState(a0).getBlock() instanceof DishShelfBlock && h.getBlockState(a1).getBlock() instanceof DishShelfBlock
              && h.getBlockState(b0).getBlock() instanceof DishShelfBlock, "etageres posees : " + h.getBlockState(a0) + " / " + h.getBlockState(a1)
              + " / " + h.getBlockState(b0) + " ; sol " + h.getBlockState(a0.below()));
        //facade nord : la droite du modele = est
        Gt.check(h.getBlockState(a0).getValue(DishShelfBlock.UP) && !h.getBlockState(b0).getValue(DishShelfBlock.UP), "colonne de 2 a cote d'une seule");
        Gt.check(!h.getBlockState(a0).getValue(DishShelfBlock.RIGHT) && !h.getBlockState(b0).getValue(DishShelfBlock.LEFT),
              "roles differents (planche haute ou non) : chacun garde ses montants (capture de Guillaume)");
        h.setBlock(b1, floor);
        settle(h, s, a0, a1, b0, b1);
        Gt.check(h.getBlockState(a0).getValue(DishShelfBlock.RIGHT) && h.getBlockState(b0).getValue(DishShelfBlock.LEFT),
              "deux colonnes de 2 : liees en bas");
        Gt.check(h.getBlockState(a1).getValue(DishShelfBlock.RIGHT) && h.getBlockState(b1).getValue(DishShelfBlock.LEFT), "et en haut");
        s.done();
    }

    // ================================================================== paniers
    private static double top(java.util.List<BasketHeap.Place> places) {
        return places.stream().mapToDouble(BasketHeap.Place::y).max().orElse(0);
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_14_baskets", timeoutTicks = 20)
    public static void p103_14_paniers_hauteur_du_remplissage(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        for (boolean tall : new boolean[]{false, true}) {
            int cap = tall ? BasketTile.TALL_ITEMS : BasketTile.ITEMS;
            double rim = tall ? BasketHeap.TALL_RIM : BasketHeap.HALF_RIM, prev = 0;
            for (int n = 1; n <= cap; n++) {
                int pct = 100 * n / cap;
                var flat = BasketHeap.surface(tall, false, n, cap);
                double t = top(flat);
                Gt.check(t >= prev - 0.001, "le tas ne descend jamais quand on remplit (" + pct + " %)");
                prev = t;
                Gt.check(flat.size() <= BasketHeap.SURFACE_MAX, "13 objets dessines au plus");
                for (BasketHeap.Place p : flat) {
                    Gt.check(p.x() >= 3 && p.x() <= 13 && p.z() >= 3 && p.z() <= 13 && p.tilt() == 0 && p.yaw() % 90 == 0,
                          "dans les parois, a plat, quarts de tour");
                }
            }
            Gt.check(prev >= rim - 0.3, "plein : a ras bord (" + rim + " px)");
            Gt.check(BasketHeap.surface(tall, false, 1, cap).size() >= 9, "decoration : couche complete meme avec un objet");
            Gt.check(BasketHeap.surface(tall, true, cap, cap).stream().allMatch(p -> p.y() + 2.3 <= rim + 0.01), "cubes pleins : sous le bord");
        }
        s.done();
    }

    // ================================================================== porte-outils mural
    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_14_racks", timeoutTicks = 40)
    public static void p103_14_porte_outils_mural(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        float[] pick = {-7.8F, 7.8F, -7.8F, 7F};
        for (boolean wall : new boolean[]{false, true}) {
            ToolRackLayout.Place a = ToolRackLayout.place(wall, 0, pick), b = ToolRackLayout.place(wall, 1, pick);
            Gt.check(a.scale() * 7.8F < 5 - 0.9F, "la tete tient entre les manches voisins");
            Gt.check(a.y() - b.y() >= ToolRackLayout.STAGGER - 0.01F, "l'outil du milieu est plus bas (tetes jamais cote a cote)");
            Gt.check(a.lean() < 0, "outil penche vers le fond (avant : vers le joueur)");
        }
        BlockPos w0 = new BlockPos(2, 2, 3), w1 = w0.east();
        h.setBlock(w0.north(), Blocks.STONE.defaultBlockState());
        h.setBlock(w1.north(), Blocks.STONE.defaultBlockState());
        BlockState rack = PiForge.TOOL_RACK.getBlock().defaultBlockState().setValue(ToolRackBlock.FACING, Direction.SOUTH).setValue(ToolRackBlock.WALL, true);
        Gt.check(rack.canSurvive(s.level, h.absolutePos(w0)), "mural : tient sur le flanc d'un bloc solide");
        h.setBlock(w0, rack);
        h.setBlock(w1, PiForge.TOOL_RACKS.get("spruce").getBlock().defaultBlockState().setValue(ToolRackBlock.FACING, Direction.SOUTH)
              .setValue(ToolRackBlock.WALL, true));
        for (BlockPos p : new BlockPos[]{w0, w1, w0}) {
            h.setBlock(p, ToolRackBlock.connect(h.getBlockState(p), s.level, h.absolutePos(p)));
        }
        //facade au sud : x bas du modele = ouest ; w1 est a l'est de w0
        Gt.check(h.getBlockState(w0).getValue(ToolRackBlock.RIGHT) && h.getBlockState(w1).getValue(ToolRackBlock.LEFT), "deux muraux voisins : relies");
        ((ToolRackTile) h.getBlockEntity(w0)).guiSlots().get(0).setStack(new ItemStack(Items.IRON_PICKAXE));
        h.setBlock(w0.north(), Blocks.AIR.defaultBlockState());
        h.runAtTickTime(10, () -> {
            Gt.check(!(h.getBlockState(w0).getBlock() instanceof ToolRackBlock), "mur retire : le porte-outils tombe");
            Gt.check(!h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(h.absolutePos(w0))
                  .inflate(2), e -> e.getItem().is(Items.IRON_PICKAXE)).isEmpty(), "son outil est rendu");
            s.done();
        });
    }
}
