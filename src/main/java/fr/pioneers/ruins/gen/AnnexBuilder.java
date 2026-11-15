package fr.pioneers.ruins.gen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 1.9 (MAJ RP, bloc 1) : pose d'une annexe en briques et en pierre ({@link AnnexTemplate}, blocs vanilla seulement) : facade tournee vers le
 * controleur de la ruine, miroir au hasard, plancher au ras du sol (remblai de cobblestone dessous, deblai dessus), coffres avec la table de
 * butin du tier de la ruine voisine (et de sa categorie de scraps dominante), cadres d'objet, lianes, mousse ou neige selon le biome. Refusee
 * sur l'eau ou sur une pente trop forte.
 */
final class AnnexBuilder {

    private final GenWorld w;
    private final AnnexTemplate t;
    private final RuinPlan.Annex a;
    private final RuinPlan plan;
    private final Rng rng;
    private final Rotation rot;
    private final Mirror mirror;

    AnnexBuilder(WorldGenLevel level, ChunkPos chunk, AnnexTemplate t, RuinPlan.Annex a, RuinPlan plan) {
        this.w = new GenWorld(level, chunk);
        this.t = t;
        this.a = a;
        this.plan = plan;
        this.rng = new Rng(a.seed());
        Rotation r = Rotation.NONE;
        for (Rotation o : Rotation.values()) {
            if (o.rotate(Direction.NORTH) == a.front()) {
                r = o;
            }
        }
        this.rot = r;
        this.mirror = a.mirror() ? Mirror.FRONT_BACK : Mirror.NONE;
    }

    /** Position du monde d'une case du gabarit (plancher en y0). */
    private BlockPos at(int x, int y, int z, int y0) {
        int mx = a.mirror() ? t.w() - 1 - x : x;
        return new BlockPos(mx - t.w() / 2, 0, z - t.d() / 2).rotate(rot).offset(a.x(), y0 + y, a.z());
    }

    boolean build() {
        //Sol : ni eau ni pente trop forte
        List<Integer> grounds = new ArrayList<>();
        int wet = 0, n = t.w() * t.d();
        int[] ground = new int[n];
        BlockState[] tops = new BlockState[n];
        for (int z = 0; z < t.d(); z++) {
            for (int x = 0; x < t.w(); x++) {
                BlockPos p = at(x, 0, z, 0);
                if (!w.can(p.getX(), w.minY + 1, p.getZ())) {
                    return false;
                }
                int g = w.ground(p.getX(), p.getZ());
                int water = w.water(p.getX(), g, p.getZ());
                if (water > 1) {
                    return false;
                }
                if (water > 0) {
                    wet++;
                }
                ground[z * t.w() + x] = g;
                tops[z * t.w() + x] = w.get(p.getX(), g, p.getZ());
                grounds.add(g);
            }
        }
        grounds.sort(Integer::compare);
        if (wet * 4 > n || grounds.get(grounds.size() - 1) - grounds.get(0) > 5) {
            return false;
        }
        int y0 = grounds.get(grounds.size() / 2);
        if (y0 + t.h() + 2 > w.maxY || y0 - 12 < w.minY) {
            return false;
        }
        //Fondations et deblai
        for (int z = 0; z < t.d(); z++) {
            for (int x = 0; x < t.w(); x++) {
                BlockPos p = at(x, 0, z, y0);
                int g = ground[z * t.w() + x];
                for (int y = Math.max(g + 1, y0 - 12); y < y0; y++) {
                    Block b = rng.nextDouble() < 0.3 ? Blocks.MOSSY_COBBLESTONE : Blocks.COBBLESTONE;
                    w.set(p.getX(), y, p.getZ(), b.defaultBlockState());
                }
                if (t.cell(x, 0, z) == AnnexTemplate.KEEP && g < y0) {
                    w.set(p.getX(), y0, p.getZ(), GenWorld.fill(tops[z * t.w() + x], 0, y0));
                }
                for (int y = y0 + t.h(); y <= Math.min(g + 8, y0 + t.h() + 8); y++) {
                    w.clear(p.getX(), y, p.getZ());
                }
            }
        }
        //Blocs du gabarit
        List<BlockPos> shaped = new ArrayList<>();
        for (int y = 0; y < t.h(); y++) {
            for (int z = 0; z < t.d(); z++) {
                for (int x = 0; x < t.w(); x++) {
                    short code = t.cell(x, y, z);
                    if (code == AnnexTemplate.KEEP) {
                        continue;
                    }
                    BlockPos p = at(x, y, z, y0);
                    if (code == AnnexTemplate.AIR) {
                        w.clear(p.getX(), p.getY(), p.getZ());
                        continue;
                    }
                    BlockState s = t.palette()[code].mirror(mirror).rotate(rot);
                    w.set(p, s);
                    Block b = s.getBlock();
                    if (b instanceof CrossCollisionBlock || b instanceof WallBlock || b instanceof StairBlock) {
                        shaped.add(p);
                    }
                }
            }
        }
        for (BlockPos p : shaped) {
            BlockState s = w.level.getBlockState(p);
            BlockState u = Block.updateFromNeighbourShapes(s, w.level, p);
            if (u != s && !u.isAir()) {
                w.set(p, u);
            }
        }
        //Coffres : butin du tier de la ruine voisine et de sa categorie dominante
        RandomSource random = RandomSource.create(rng.nextLong());
        String cat = plan.category.hasTradeBlock() ? "_" + plan.category.id() : "";
        ResourceLocation table = new ResourceLocation("pioneers", "chests/annex_mk" + plan.site.tier() + cat);
        for (int[] c : t.chests()) {
            BlockPos p = at(c[0], c[1], c[2], y0);
            if (w.can(p)) {
                RandomizableContainerBlockEntity.setLootTable(w.level, random, p, table);
            }
        }
        //Cadres d'objet (photo, papier colle sur le tableau)
        for (AnnexTemplate.Frame f : t.frames()) {
            BlockPos p = at(f.x(), f.y(), f.z(), y0);
            Direction face = rot.rotate(mirror.mirror(f.facing()));
            BlockPos wall = p.relative(face.getOpposite());
            Item item = BuiltInRegistries.ITEM.get(f.item());
            if (!w.can(p) || !w.level.getBlockState(p).isAir() || !w.level.getBlockState(wall).isSolid() || item == Items.AIR) {
                continue;
            }
            ItemFrame frame = new ItemFrame(w.level.getLevel(), p, face);
            frame.setSilent(true);                                                      //aucun son joue depuis un fil de generation
            frame.setItem(new ItemStack(item), false);
            frame.setSilent(false);
            w.level.addFreshEntity(frame);
        }
        decor(y0);
        return true;
    }

    /** Lianes et mousse (temps doux), neige (froid), rien au sec. */
    private void decor(int y0) {
        BlockPos center = at(t.w() / 2, 1, t.d() / 2, y0);
        Holder<Biome> biome = w.level.getBiome(center);
        boolean cold = biome.value().coldEnoughToSnow(center);
        boolean dry = !cold && !biome.value().hasPrecipitation();
        if (dry) {
            return;
        }
        boolean lush = biome.is(BiomeTags.IS_JUNGLE) || biome.is(Biomes.SWAMP) || biome.is(Biomes.MANGROVE_SWAMP);
        for (int z = -1; z <= t.d(); z++) {
            for (int x = -1; x <= t.w(); x++) {
                boolean inside = x >= 0 && z >= 0 && x < t.w() && z < t.d();
                for (int y = t.h() + 1; y >= 1; y--) {
                    BlockPos p = at(Math.max(0, Math.min(t.w() - 1, x)), y, Math.max(0, Math.min(t.d() - 1, z)), y0);
                    if (!inside) {
                        //Case hors de l'emprise : on decale d'un cran vers l'exterieur
                        BlockPos in = p;
                        BlockPos out = at(x, y, z, y0);
                        p = out;
                        if (!w.can(p) || !w.level.getBlockState(p).isAir() || cold) {
                            continue;
                        }
                        Direction toWall = Direction.getNearest(in.getX() - p.getX(), 0, in.getZ() - p.getZ());
                        BlockState wall = w.level.getBlockState(in);
                        if (wall.isFaceSturdy(w.level, in, toWall.getOpposite()) && rng.nextDouble() < (lush ? 0.16 : 0.06)) {
                            int len = rng.range(1, 3);
                            BlockState v = Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(toWall), true);
                            for (int s = 0; s < len && w.level.getBlockState(p.below(s)).isAir(); s++) {
                                w.set(p.below(s), v);
                            }
                        }
                        continue;
                    }
                    if (!w.can(p) || !w.level.getBlockState(p).isAir()) {
                        continue;
                    }
                    BlockPos below = p.below();
                    if (!w.level.getBlockState(below).isFaceSturdy(w.level, below, Direction.UP)) {
                        continue;
                    }
                    boolean sky = true;
                    for (int k = 1; k <= t.h() + 1 - y && sky; k++) {
                        sky = w.level.getBlockState(p.above(k)).isAir();
                    }
                    if (cold && sky && rng.nextDouble() < 0.7) {
                        w.set(p, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1 + rng.nextInt(2)));
                    } else if (!cold && rng.nextDouble() < (lush ? 0.18 : 0.07)) {
                        w.set(p, Blocks.MOSS_CARPET.defaultBlockState());
                    }
                }
            }
        }
    }
}
