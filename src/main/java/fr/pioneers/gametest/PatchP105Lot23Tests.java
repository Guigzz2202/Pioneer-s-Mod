package fr.pioneers.gametest;

import fr.pioneers.api.multiblock.HouseNbt;
import fr.pioneers.api.multiblock.HouseShapes;
import fr.pioneers.tech.bronze.HeatPalette;
import fr.pioneers.tech.bronze.HouseControllerBlock;
import fr.pioneers.tech.bronze.blast.BlastFurnaceTile;
import fr.pioneers.tech.bronze.blast.FireBricksBlock;
import fr.pioneers.tech.bronze.foundry.KilnMasonry;
import fr.pioneers.tech.bronze.foundry.KilnMasonryBlock;
import fr.pioneers.tech.bronze.foundry.KilnTile;
import fr.pioneers.core.block.Covers;
import fr.pioneers.core.block.PiCoverFrame;
import fr.pioneers.tech.forge.smithing.ForgeHearthTile;
import fr.pioneers.tech.industry.bronze.CokeOvenTile;
import fr.pioneers.tech.kinetics.AxisKineticBlock;
import fr.pioneers.tech.kinetics.parts.CogTile;
import fr.pioneers.tech.bronze.PiBronze;
import fr.pioneers.tech.forge.PiForge;
import fr.pioneers.tech.kinetics.PiKinetics;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

/**
 * Patch 1.0.5-beta, lot 23 (retours de jeu 2e serie 3 : age primitif, forge et mecanique, GameTests) : FOYER DE FORGE (le clic droit ne
 * pose ni ne prend rien, quel que soit l'objet en main ; Maj + clic droit avec de quoi allumer : allume s'il y a du charbon, use le briquet,
 * consomme la boule de feu, la torche reste ; un lingot n'allume rien) ; ENGRENAGES (sans axe par defaut, axe vers un arbre branche ; cadre
 * de cache pose : axe coupe, lien par l'axe perdu, engrenement avec le voisin garde ; lime : cadre rendu, axe revenu) ; CHAUFFE HOMOGENE
 * (palier de chaque bloc chauffable = palier peint, borne a sa propriete ; haut fourneau et four a coke : meme palier pour tous les roles ;
 * angles du four a coke orientes) ; FOUR DE POTIER (maconnerie finie a la formation, memes formes, roles ; aller-retour vanilla exact ;
 * bouche cassee : briques, escaliers et dalle rendus).
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP105Lot23Tests {

    private PatchP105Lot23Tests() {
    }

    // ================================================================== 17. foyer de forge
    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_23_hearth", timeoutTicks = 20)
    public static void p105_23_foyer_clic_droit(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos rel = new BlockPos(4, 2, 4);
        h.setBlock(rel, PiForge.FORGE_HEARTH.getBlock().defaultBlockState());
        ForgeHearthTile hearth = (ForgeHearthTile) h.getBlockEntity(rel);
        Gt.check(hearth != null, "foyer de forge");
        Player p = h.makeMockPlayer();
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(h.absolutePos(rel)), Direction.UP, h.absolutePos(rel), false);
        //Clic droit avec du charbon, un lingot, un briquet : rien ne se pose (PASS : le bloc ouvre l'interface)
        for (ItemStack held : new ItemStack[]{new ItemStack(Items.CHARCOAL, 8), new ItemStack(Items.IRON_INGOT, 4), new ItemStack(Items.FLINT_AND_STEEL),
              ItemStack.EMPTY}) {
            p.setItemInHand(InteractionHand.MAIN_HAND, held.copy());
            Gt.equal(InteractionResult.PASS, hearth.use(p, InteractionHand.MAIN_HAND, hit), "clic droit avec " + held + " : interface seulement");
            Gt.equal(held.getCount(), (long) p.getItemInHand(InteractionHand.MAIN_HAND).getCount(), "rien pris dans la main (" + held + ")");
        }
        Gt.check(hearth.fuelStack().isEmpty() && hearth.waiting().isEmpty() && hearth.hotPiece().isEmpty(), "aucun objet pose au clic droit");
        //Allumage : liste d'avant + allumettes + torche ; un lingot n'est pas de quoi allumer
        Gt.check(ForgeHearthTile.isIgniter(new ItemStack(Items.FLINT_AND_STEEL)) && ForgeHearthTile.isIgniter(new ItemStack(Items.FIRE_CHARGE))
                 && ForgeHearthTile.isIgniter(new ItemStack(Items.TORCH)), "briquet, boule de feu, torche allument");
        Gt.check(!ForgeHearthTile.isIgniter(new ItemStack(Items.IRON_INGOT)) && !ForgeHearthTile.isIgniter(new ItemStack(Items.CHARCOAL)), "lingot, charbon : non");
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.TORCH));
        Gt.check(!hearth.igniteBy(p, InteractionHand.MAIN_HAND) && !hearth.isLit(), "sans charbon : pas d'allumage");
        //Charbon par un entonnoir (cote) : le chemin d'automatisation reste
        IItemHandler side = hearth.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.NORTH).orElse(null);
        Gt.check(side != null, "entonnoir lateral");
        side.insertItem(0, new ItemStack(Items.CHARCOAL, 4), false);
        Gt.check(!hearth.fuelStack().isEmpty(), "charbon entre par l'entonnoir");
        ItemStack flint = new ItemStack(Items.FLINT_AND_STEEL);
        p.setItemInHand(InteractionHand.MAIN_HAND, flint);
        Gt.check(hearth.igniteBy(p, InteractionHand.MAIN_HAND) && hearth.isLit(), "Maj + clic droit au briquet : le foyer s'allume");
        Gt.equal(1L, (long) p.getItemInHand(InteractionHand.MAIN_HAND).getDamageValue(), "le briquet s'use d'un point");
        s.done();
    }

    // ================================================================== 21 / 22. engrenages : axe, cadre
    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_23_cogs", timeoutTicks = 40)
    public static void p105_23_engrenages_axe_et_cadre(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockState cog = PiKinetics.WOODEN_COG.getBlock().defaultBlockState().setValue(AxisKineticBlock.AXIS, Direction.Axis.Z);
        BlockPos a = new BlockPos(3, 2, 4), b = new BlockPos(4, 2, 4), shaft = new BlockPos(3, 2, 3);
        h.setBlock(a, cog);
        h.setBlock(b, cog);
        CogTile ta = (CogTile) h.getBlockEntity(a), tb = (CogTile) h.getBlockEntity(b);
        //22. sans arbre : pas d'axe dessine
        Gt.check(!ta.axleTowards(Direction.NORTH) && !ta.axleTowards(Direction.SOUTH), "engrenage seul : pas d'axe");
        h.setBlock(shaft, PiKinetics.WOODEN_SHAFT.getBlock().defaultBlockState().setValue(AxisKineticBlock.AXIS, Direction.Axis.Z));
        Gt.check(ta.axleTowards(Direction.NORTH) && !ta.axleTowards(Direction.SOUTH), "arbre au nord : axe vers le nord seulement");
        Gt.check(!tb.axleTowards(Direction.NORTH), "voisin sans arbre : pas d'axe");
        List<BlockPos> links = links(ta);
        Gt.check(links.contains(h.absolutePos(shaft)) && links.contains(h.absolutePos(b)), "liens : arbre et pignon voisin");
        //21. cadre de cache pose : axe coupe, engrenement garde
        Player p = h.makeMockPlayer();
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PiCoverFrame.FRAME.asItem(), 2));
        InteractionResult r = Covers.useFrameOnly(h.getBlockState(a), h.getLevel(), h.absolutePos(a), p, InteractionHand.MAIN_HAND);
        Gt.check(r != null && r.consumesAction(), "cadre pose sur l'engrenage");
        Gt.check(Covers.framed(h.getBlockState(a)), "etat encadre");
        Gt.equal(0.0, ta.shaftFactor(Direction.NORTH), "encadre : plus de bout d'arbre");
        Gt.check(!ta.axleTowards(Direction.NORTH), "encadre : pas d'axe dessine");
        links = links(ta);
        Gt.check(!links.contains(h.absolutePos(shaft)), "encadre : plus de lien par l'axe vers l'arbre");
        Gt.check(links.contains(h.absolutePos(b)), "encadre : engrenement avec le pignon voisin garde");
        Gt.check(Covers.useFrameOnly(h.getBlockState(a), h.getLevel(), h.absolutePos(a), p, InteractionHand.MAIN_HAND) == null,
              "pas de second cadre");
        //Lime : cadre rendu
        net.minecraft.world.item.Item file = fileItem();
        if (file != null) {
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(file));
            Gt.check(Covers.useFrameOnly(h.getBlockState(a), h.getLevel(), h.absolutePos(a), p, InteractionHand.MAIN_HAND) != null, "lime : cadre retire");
            Gt.check(!Covers.framed(h.getBlockState(a)) && ta.axleTowards(Direction.NORTH), "sans cadre : l'axe revient");
        }
        s.done();
    }

    private static net.minecraft.world.item.Item fileItem() {
        for (net.minecraft.world.item.Item i : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            if (new ItemStack(i).is(Covers.FILES)) {
                return i;
            }
        }
        return null;
    }

    private static List<BlockPos> links(CogTile t) {
        List<BlockPos> out = new ArrayList<>();
        t.collectLinks((other, ratio) -> out.add(other));
        return out;
    }

    // ================================================================== 13 / 12. chauffe homogene, angles orientes
    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_23_heat", timeoutTicks = 20)
    public static void p105_23_chauffe_homogene(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockState[] heated = {PiBronze.FIRE_BRICKS.getBlock().defaultBlockState(), PiBronze.FIRE_BRICK_FIREBOX.getBlock().defaultBlockState(),
              PiBronze.FOUNDRY_VAT.getBlock().defaultBlockState(), PiBronze.TUYERE.getBlock().defaultBlockState(),
              PiBronze.BLAST_FURNACE.getBlock().defaultBlockState(), PiBronze.AUTO_FOUNDRY.getBlock().defaultBlockState(),
              fr.pioneers.tech.industry.PiIndustry.COKE_OVEN.getBlock().defaultBlockState(), PiBronze.POTTERY_KILN.getBlock().defaultBlockState(),
              PiBronze.KILN_MASONRY.get().defaultBlockState()};
        for (BlockState st : heated) {
            for (int k = 0; k <= HeatPalette.MAX; k++) {
                Gt.equal(k, (long) HeatPalette.shown(HeatPalette.heated(st, k)), st.getBlock() + " : palier " + k);
            }
        }
        for (int k = 0; k <= HeatPalette.MAX; k++) {
            for (FireBricksBlock.Part part : FireBricksBlock.Part.values()) {
                Gt.equal(k, (long) BlastFurnaceTile.heatOf(part, k), "haut fourneau : meme palier pour " + part);
                Gt.equal(k, (long) CokeOvenTile.heatOf(part, k), "four a coke : meme palier pour " + part);
            }
        }
        //Angles orientes : la brique d'angle (x 1, z 0) d'un four dont la facade regarde le sud a ses faces exterieures au sud et a l'est
        Gt.equal(FireBricksBlock.Part.COKE_CORNER_SE, CokeOvenTile.cornerOf(1, 0, Direction.SOUTH), "angle avant droit, facade au sud");
        Gt.equal(FireBricksBlock.Part.COKE_CORNER_NW, CokeOvenTile.cornerOf(1, 0, Direction.NORTH), "angle avant droit, facade au nord");
        s.done();
    }

    // ================================================================== 25. four de potier fini
    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_23_kiln_rules", timeoutTicks = 20)
    public static void p105_23_maconnerie_aller_retour(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        List<BlockState> vanilla = new ArrayList<>();
        vanilla.add(Blocks.BRICKS.defaultBlockState());
        for (Direction d : Direction.Plane.HORIZONTAL) {
            for (Half half : Half.values()) {
                vanilla.add(Blocks.BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, d).setValue(StairBlock.HALF, half));
            }
        }
        for (SlabType t : SlabType.values()) {
            vanilla.add(Blocks.BRICK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, t));
        }
        for (BlockState v : vanilla) {
            BlockState d = KilnMasonry.dressed(v, KilnMasonryBlock.Role.BODY, 2);
            Gt.check(d != null && d.getBlock() instanceof KilnMasonryBlock, "habillable : " + v);
            BlockState back = KilnMasonry.vanilla(d);
            Gt.check(back.getBlock() == v.getBlock(), "meme bloc rendu : " + v);
            if (v.hasProperty(StairBlock.FACING)) {
                Gt.check(back.getValue(StairBlock.FACING) == v.getValue(StairBlock.FACING) && back.getValue(StairBlock.HALF) == v.getValue(StairBlock.HALF),
                      "meme escalier rendu : " + v);
            }
            if (v.hasProperty(SlabBlock.TYPE)) {
                Gt.equal(v.getValue(SlabBlock.TYPE), back.getValue(SlabBlock.TYPE), "meme dalle rendue");
            }
            //Meme forme que le bloc d'origine (collision)
            Gt.check(KilnMasonryBlock.shapeOf(d).bounds().equals(v.getShape(h.getLevel(), BlockPos.ZERO).bounds()), "meme forme : " + v);
        }
        //Roles du plan de Guillaume : rangee du bas = socle ; colonne de la brique la plus haute = cheminee
        int base = 0, top = 0;
        for (HouseNbt.Cell c : HouseNbt.cells(KilnTile.SHAPE)) {
            if (!KilnMasonry.masonry(c.letter())) {
                continue;
            }
            KilnMasonryBlock.Role r = KilnMasonry.roleOf(KilnTile.SHAPE, c);
            base += r == KilnMasonryBlock.Role.BASE ? 1 : 0;
            top += r == KilnMasonryBlock.Role.CHIMNEY_TOP ? 1 : 0;
        }
        Gt.equal(9L, (long) base, "socle de 3 x 3");
        Gt.equal(1L, (long) top, "un seul haut de cheminee");
        Gt.check(KilnMasonry.stepOf(20) == 0 && KilnMasonry.stepOf(500) == 1 && KilnMasonry.stepOf(900) == 2, "paliers du four de potier");
        s.done();
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_p105_23_kiln", timeoutTicks = 240)
    public static void p105_23_four_de_potier_fini(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos c = MultiblockApi6Tests.buildHouse(h, HouseShapes.POTTERY_KILN, Direction.SOUTH);
        h.runAfterDelay(80, () -> {
            Gt.check(h.getBlockEntity(c) instanceof KilnTile, "pas de four de potier en " + c);
            KilnTile kiln = (KilnTile) h.getBlockEntity(c);
            BlockPos ctrl = h.absolutePos(c);
            Gt.check(kiln.formed(), "le four de potier construit au plan ne se forme pas");
            Gt.check(h.getLevel().getBlockState(ctrl).getValue(HouseControllerBlock.FORMED), "bouche formee");
            int dressed = 0, total = 0;
            BlockPos chimneyTop = null;
            for (HouseNbt.Cell cell : HouseNbt.cells(KilnTile.SHAPE)) {
                if (!KilnMasonry.masonry(cell.letter())) {
                    continue;
                }
                total++;
                BlockPos p = HouseNbt.at(ctrl, kiln.facing(), cell);
                BlockState st = h.getLevel().getBlockState(p);
                if (st.getBlock() instanceof KilnMasonryBlock) {
                    dressed++;
                    Gt.equal(KilnMasonry.roleOf(KilnTile.SHAPE, cell), st.getValue(KilnMasonryBlock.ROLE), "role en " + p);
                    Gt.check(HouseNbt.fits(cell.letter(), st), "la lettre accepte sa maconnerie finie en " + p);
                    if (st.getValue(KilnMasonryBlock.ROLE) == KilnMasonryBlock.Role.CHIMNEY_TOP) {
                        chimneyTop = p;
                    }
                }
            }
            Gt.equal(total, (long) dressed, "toute la maconnerie est finie");
            Gt.check(chimneyTop != null, "haut de cheminee");
            //Bouche cassee : briques, escaliers et dalle rendus
            Direction front = kiln.facing();
            h.getLevel().destroyBlock(ctrl, false);
            Gt.later(h, 2, () -> {
                for (HouseNbt.Cell cell : HouseNbt.cells(KilnTile.SHAPE)) {
                    if (KilnMasonry.masonry(cell.letter())) {
                        BlockState st = h.getLevel().getBlockState(HouseNbt.at(ctrl, front, cell));
                        Gt.check(!(st.getBlock() instanceof KilnMasonryBlock) && HouseNbt.fits(cell.letter(), st), "maconnerie vanilla rendue : " + st);
                    }
                }
                s.done();
            });
        });
    }
}
