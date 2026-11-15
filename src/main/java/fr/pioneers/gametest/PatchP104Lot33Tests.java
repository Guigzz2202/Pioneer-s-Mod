package fr.pioneers.gametest;

import fr.pioneers.farm.herd.GeneratedHerd;
import fr.pioneers.farm.herd.HerdBreeds;
import fr.pioneers.farm.herd.HutchLife;
import fr.pioneers.home.beam.PiBeams;
import fr.pioneers.home.beam.TimberBeamBlock;
import fr.pioneers.lighting.LightLinks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 1.0.4-beta (lot 33, GameTests) : retours de Guillaume du 09/11 (13 h 03). Liens de l'eclairage visibles SEULEMENT cle a molette en main
 * (principale ou secondaire) et liaison main vide ou cle ; poutres : tenon vers une poutre d'un autre axe (croix, poteau qui traverse,
 * croisement a deux hauteurs), rien vers une poutre parallele, cale sur une face pleine qui disparait avec elle ; clapier : vie des lapins
 * (places dans le clapier, pas deux lapins a la meme place au repos, on ne mange que s'il reste de la nourriture, foin a la hauteur de la
 * reserve, rien de calcule quand il est vide) ; races : boite de collision au gabarit de la race.
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP104Lot33Tests {

    private PatchP104Lot33Tests() {
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_33_links", timeoutTicks = 20)
    public static void p104_33_liens_a_la_cle(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ItemStack wrench = new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation("pioneers", "wrench")));
        Gt.check(!wrench.isEmpty(), "cle a molette du mod");
        ItemStack stick = new ItemStack(Items.STICK);
        Gt.check(LightLinks.showsLinks(wrench, ItemStack.EMPTY), "cle en main principale : liens visibles");
        Gt.check(LightLinks.showsLinks(ItemStack.EMPTY, wrench), "cle en main secondaire : liens visibles");
        Gt.check(LightLinks.showsLinks(stick, wrench), "baton + cle secondaire : liens visibles");
        Gt.check(!LightLinks.showsLinks(ItemStack.EMPTY, ItemStack.EMPTY), "mains vides : aucun lien");
        Gt.check(!LightLinks.showsLinks(stick, stick), "autre objet : aucun lien");
        Gt.check(LightLinks.linkingHand(ItemStack.EMPTY), "lier main vide");
        Gt.check(LightLinks.linkingHand(wrench), "lier cle en main");
        Gt.check(!LightLinks.linkingHand(stick), "pas avec un autre objet");
        s.done();
    }

    private static BlockState beam(Direction.Axis axis) {
        return PiBeams.BEAMS.get(0).getBlock().defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
    }

    /** Pose des poutres (axe par case) puis recalcule leurs raccords, comme la pose d'un joueur. */
    private static void place(ServerLevel level, BlockPos[] at, Direction.Axis[] axes) {
        for (int i = 0; i < at.length; i++) {
            level.setBlock(at[i], beam(axes[i]), Block.UPDATE_ALL);
        }
        for (BlockPos p : at) {
            level.setBlock(p, TimberBeamBlock.connect(level.getBlockState(p), level, p), Block.UPDATE_ALL);
        }
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_33_beams", timeoutTicks = 20)
    public static void p104_33_poutres_croisees(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerLevel level = h.getLevel();
        //croix a y 4 : poutre X au centre, poutres Z de part et d'autre ; poteau Y au-dessus du centre
        BlockPos c = h.absolutePos(new BlockPos(4, 4, 4));
        place(level, new BlockPos[]{c, c.north(), c.south(), c.above(), c.east()},
              new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Z, Direction.Axis.Z, Direction.Axis.Y, Direction.Axis.X});
        BlockState mid = level.getBlockState(c);
        Gt.check(mid.getValue(TimberBeamBlock.NORTH) && mid.getValue(TimberBeamBlock.SOUTH), "croix : tenons vers les deux poutres Z");
        Gt.check(!mid.getValue(TimberBeamBlock.EAST) && !mid.getValue(TimberBeamBlock.WEST), "bout a bout avec la poutre X : pas de tenon");
        Gt.equal(TimberBeamBlock.Joint.JOINT, mid.getValue(TimberBeamBlock.UP), "poteau au-dessus : tenon vers lui");
        BlockState n = level.getBlockState(c.north());
        Gt.check(!n.getValue(TimberBeamBlock.SOUTH), "la poutre qui arrive en bout n'a pas de tenon");
        Gt.equal(TimberBeamBlock.Joint.NONE, level.getBlockState(c.above()).getValue(TimberBeamBlock.DOWN), "le poteau arrive en bout");
        //forme : la poutre du centre touche la limite de la case vers le nord (tenon)
        var box = mid.getShape(level, c).bounds();
        Gt.check(box.minZ < 1e-6 && box.maxZ > 1 - 1e-6, "tenons jusqu'aux limites de la case");
        //croisement a deux hauteurs : poutre X a y 6, poutre Z a y 7 juste au-dessus : les deux tenons se rejoignent
        BlockPos lo = h.absolutePos(new BlockPos(2, 6, 2));
        place(level, new BlockPos[]{lo, lo.above()}, new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Z});
        Gt.equal(TimberBeamBlock.Joint.JOINT, level.getBlockState(lo).getValue(TimberBeamBlock.UP), "deux hauteurs : tenon vers le haut");
        Gt.equal(TimberBeamBlock.Joint.JOINT, level.getBlockState(lo.above()).getValue(TimberBeamBlock.DOWN), "deux hauteurs : tenon vers le bas");
        //poutres paralleles cote a cote : rien
        BlockPos pa = h.absolutePos(new BlockPos(6, 6, 2));
        place(level, new BlockPos[]{pa, pa.south()}, new Direction.Axis[]{Direction.Axis.X, Direction.Axis.X});
        Gt.check(!level.getBlockState(pa).getValue(TimberBeamBlock.SOUTH), "poutres paralleles : pas de tenon");
        //poutre horizontale posee sur une pierre : cale ; pierre cassee : la cale disparait
        BlockPos stone = h.absolutePos(new BlockPos(6, 3, 6));
        level.setBlock(stone, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        place(level, new BlockPos[]{stone.above()}, new Direction.Axis[]{Direction.Axis.Z});
        Gt.equal(TimberBeamBlock.Joint.SEAT, level.getBlockState(stone.above()).getValue(TimberBeamBlock.DOWN), "cale sur une face pleine");
        Gt.check(level.getBlockState(stone.above()).getShape(level, stone.above()).bounds().minY < 1e-6, "la cale descend jusqu'a la pierre");
        level.setBlock(stone, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        Gt.equal(TimberBeamBlock.Joint.NONE, level.getBlockState(stone.above()).getValue(TimberBeamBlock.DOWN), "pierre retiree : plus de cale");
        //regle pure : un poteau n'a jamais de tenon en haut ou en bas (ce sont ses bouts)
        Gt.equal(TimberBeamBlock.Joint.NONE, TimberBeamBlock.joint(Direction.Axis.Y, Direction.UP, true, Direction.Axis.X, false), "poteau : bout");
        s.done();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_33_hutch", timeoutTicks = 20)
    public static void p104_33_vie_du_clapier(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        boolean[] adult = {true, true, true, true, false, false, false, true};
        for (boolean dbl : new boolean[]{true, false}) {
            float[][] spots = HutchLife.spots(dbl);
            Gt.check(spots.length >= GeneratedHerd.HUTCH_CAPACITY, "au moins une place par lapin");
            float x0 = dbl ? -14F : 2F, x1 = 14F;
            for (int t = 0; t < HutchLife.WINDOW * HutchLife.PERIOD * 2; t += 7) {
                HutchLife.Pose[] ps = HutchLife.poses(42L, adult, 200, dbl, t);
                Gt.equal((long) adult.length, (long) ps.length, "un lapin dessine par lapin du clapier");
                for (HutchLife.Pose p : ps) {
                    Gt.check(p.x() >= x0 && p.x() <= x1 && p.z() >= 2F && p.z() <= 14.25F, "lapin dans le clapier (t " + t + ")");
                    Gt.check(p.hop() >= 0F && p.hop() <= HutchLife.HOP_H + 1e-3F, "saut borne");
                }
                for (int i = 0; i < ps.length; i++) {
                    for (int j = i + 1; j < ps.length; j++) {
                        boolean resting = ps[i].act() != HutchLife.Act.HOP && ps[j].act() != HutchLife.Act.HOP;
                        Gt.check(!resting || Math.abs(ps[i].x() - ps[j].x()) > 1e-3F || Math.abs(ps[i].z() - ps[j].z()) > 1e-3F,
                              "deux lapins au repos jamais a la meme place");
                    }
                }
                for (HutchLife.Pose p : HutchLife.poses(42L, adult, 0, dbl, t)) {
                    Gt.check(p.act() != HutchLife.Act.EAT, "on ne mange pas au ratelier vide");
                }
            }
        }
        Gt.equal(0L, (long) HutchLife.poses(1L, new boolean[0], 100, true, 50F).length, "clapier vide : aucun lapin");
        Gt.check(HutchLife.hayHeight(0) == 0F, "reserve vide : ratelier vide");
        Gt.check(HutchLife.hayHeight(GeneratedHerd.HUTCH_FOOD_MAX) == GeneratedHerd.HUTCH_HAY_H, "reserve pleine : foin en haut");
        Gt.check(HutchLife.hayHeight(30) < HutchLife.hayHeight(200), "le foin baisse avec la reserve");
        s.done();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_33_breeds", timeoutTicks = 20)
    public static void p104_33_gabarit_des_races(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        for (GeneratedHerd.HerdType t : GeneratedHerd.TYPES) {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(new ResourceLocation("pioneers", t.id()));
            Gt.check(HerdBreeds.typeOf(type) == t, "type de race : " + t.id());
            Gt.check(Math.abs(type.getWidth() - t.width()) < 1e-4F && Math.abs(type.getHeight() - t.height()) < 1e-4F, "boite au gabarit : " + t.id());
            Gt.check(type.getWidth() < 2F && type.getHeight() < 2.2F, "passe une porte de 2 : " + t.id());
        }
        EntityType<?> pony = BuiltInRegistries.ENTITY_TYPE.get(new ResourceLocation("pioneers", "pony_horse"));
        EntityType<?> perch = BuiltInRegistries.ENTITY_TYPE.get(new ResourceLocation("pioneers", "percheron_horse"));
        Gt.check(pony.getHeight() < perch.getHeight() && pony.getWidth() < perch.getWidth(), "poney plus petit que le percheron");
        s.done();
    }
}
