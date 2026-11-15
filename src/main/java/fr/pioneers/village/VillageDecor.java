package fr.pioneers.village;

import fr.pioneers.clan.ClanApi;
import fr.pioneers.reputation.PeopleApi;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * 1.9.1 (retours de Guillaume) : decor de la place. Le plan vient de {@link PlazaPlanner} (logique pure, verifiee hors jeu sur de vrais
 * villages vanilla par {@code tools/rptest/PlazaBench}) ; ici on lit le monde une fois (instantane de la zone), on classe les blocs (sol
 * naturel, chemin, champ, eau, construction ; un chunk revendique par un clan compte comme construction), puis on pose : Tableau face a la
 * cloche, fontaine, lampadaires, bannieres sur des mats (barriere ou muret selon le biome). Les places posees par l'ancienne regle (1.9.0)
 * sont retirees puis refaites une fois ({@link #VERSION}).
 */
final class VillageDecor {

    /** Version du decor : 2 = place d'un seul tenant (1.9.1). */
    static final int VERSION = 2;

    private VillageDecor() {
    }

    /**
     * Pose (ou refait) le decor du village. Faux si la zone n'est pas chargee : on reessaiera a la visite suivante. Vrai sinon, meme si
     * rien n'a pu etre pose (le village n'a alors pas de place).
     */
    static boolean refresh(ServerLevel level, VillageData.Village v) {
        BlockPos bell = v.center;
        int r = PlazaPlanner.RADIUS + 8;
        if (!level.hasChunksAt(bell.getX() - r, bell.getZ() - r, bell.getX() + r, bell.getZ() + r)) {
            return false;
        }
        if (v.decor && !removeOld(level, v)) {
            return false;
        }
        Snapshot snap = new Snapshot(level, bell, r);
        PlazaPlanner.Plan plan = PlazaPlanner.plan(snap, bell.getX(), bell.getY(), bell.getZ());
        for (PlazaPlanner.Placed p : plan.pieces()) {
            place(level, v, p);
        }
        v.bannerPeople = v.people;
        return true;
    }

    /** Retire la place d'avant (seulement nos blocs encore a leur place) ; faux si une position n'est pas chargee. */
    static boolean removeOld(ServerLevel level, VillageData.Village v) {
        for (List<BlockPos> list : List.of(v.boards, v.fountains, v.lamps, v.banners, v.masts)) {
            for (BlockPos p : list) {
                if (!level.isLoaded(p)) {
                    return false;
                }
            }
        }
        for (BlockPos p : v.boards) {
            if (level.getBlockState(p).getBlock() instanceof NeedsBoardBlock) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        for (BlockPos p : v.fountains) {
            if (level.getBlockState(p).getBlock() instanceof FountainBlock) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        for (BlockPos p : v.lamps) {
            if (level.getBlockState(p.above()).getBlock() instanceof LamppostBlock) {
                level.setBlock(p.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            if (level.getBlockState(p).getBlock() instanceof LamppostBlock) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        for (BlockPos p : v.banners) {
            if (level.getBlockState(p).getBlock() instanceof BannerBlock) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        for (BlockPos p : v.masts) {
            BlockState s = level.getBlockState(p);
            if (s.is(BlockTags.FENCES) || s.is(BlockTags.WALLS)) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        v.boards.clear();
        v.fountains.clear();
        v.lamps.clear();
        v.banners.clear();
        v.masts.clear();
        return true;
    }

    static void place(ServerLevel level, VillageData.Village v, PlazaPlanner.Placed p) {
        BlockPos pos = new BlockPos(p.x(), p.y(), p.z());
        //herbes et neige de la colonne : retirees proprement (une herbe haute ne reste pas en l'air)
        for (int h = 0; h < 3; h++) {
            BlockPos q = pos.above(h);
            BlockState s = level.getBlockState(q);
            if (!s.isAir() && s.canBeReplaced()) {
                level.setBlock(q, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        Direction face = Direction.from2DDataValue(p.facing());
        switch (p.piece()) {
            case BOARD -> {
                level.setBlock(pos, VillageBlocks.NEEDS_BOARD.getBlock().defaultBlockState().setValue(NeedsBoardBlock.FACING, face), Block.UPDATE_ALL);
                v.boards.add(pos);
            }
            case FOUNTAIN -> {
                level.setBlock(pos, VillageBlocks.VILLAGE_FOUNTAIN.getBlock().defaultBlockState(), Block.UPDATE_ALL);
                v.fountains.add(pos);
            }
            case LAMP -> {
                BlockState low = VillageBlocks.VILLAGE_LAMPPOST.getBlock().defaultBlockState();
                level.setBlock(pos, low, Block.UPDATE_ALL);
                level.setBlock(pos.above(), low.setValue(LamppostBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
                v.lamps.add(pos);
            }
            case MAST -> {
                level.setBlock(pos, mast(level, pos), Block.UPDATE_ALL);
                v.masts.add(pos);
                BlockPos top = pos.above();
                banner(level, top, v, p.facing() * 4);
                v.banners.add(top);
            }
        }
    }

    /** Mat d'une banniere : la barriere ou le muret du biome du village. */
    static BlockState mast(ServerLevel level, BlockPos pos) {
        var biome = level.getBiome(pos);
        if (biome.is(BiomeTags.HAS_VILLAGE_DESERT)) {
            return Blocks.SANDSTONE_WALL.defaultBlockState();
        }
        if (biome.is(BiomeTags.HAS_VILLAGE_SAVANNA)) {
            return Blocks.ACACIA_FENCE.defaultBlockState();
        }
        if (biome.is(BiomeTags.HAS_VILLAGE_TAIGA) || biome.is(BiomeTags.HAS_VILLAGE_SNOWY)) {
            return Blocks.SPRUCE_FENCE.defaultBlockState();
        }
        return Blocks.OAK_FENCE.defaultBlockState();
    }

    /** 1.9 (MAJ RP, bloc 4) : refait les bannieres encore debout de la place aux couleurs du peuple ; vrai quand toutes sont chargees. */
    static boolean redress(ServerLevel level, VillageData.Village v) {
        boolean all = true;
        for (BlockPos b : v.banners) {
            if (!level.isLoaded(b)) {
                all = false;
                continue;
            }
            BlockState st = level.getBlockState(b);
            //une banniere abattue ou remplacee par un joueur n'est jamais reposee
            if (st.getBlock() instanceof BannerBlock) {
                banner(level, b, v, st.getValue(BannerBlock.ROTATION));
            }
        }
        if (all) {
            v.bannerPeople = v.people;
        }
        return all;
    }

    /** Pose (ou refait) une banniere du decor aux couleurs et motifs du peuple du village. */
    static void banner(ServerLevel level, BlockPos b, VillageData.Village v, int rot) {
        PeopleApi.People people = PeopleApi.get(level.getServer(), v.people);
        DyeColor dye = people == null ? DyeColor.WHITE : DyeColor.byName(people.dye(), DyeColor.WHITE);
        Block banner = BannerBlock.byColor(dye);
        level.setBlock(b, banner.defaultBlockState().setValue(BannerBlock.ROTATION, rot & 15), Block.UPDATE_ALL);
        if (level.getBlockEntity(b) instanceof BannerBlockEntity be) {
            ItemStack item = new ItemStack(banner.asItem());
            CompoundTag tag = new CompoundTag();
            ListTag patterns = people != null && people.player() ? Villages.playerBanner.apply(level, people.id()) : null;
            if (patterns == null || patterns.isEmpty()) {
                String model = PeopleApi.model(level.getServer(), v.people);
                patterns = new ListTag();
                CompoundTag p1 = new CompoundTag();
                p1.putString("Pattern", people != null && people.player() ? "tt" : patternFor(model, 0));
                p1.putInt("Color", DyeColor.WHITE.getId());
                patterns.add(p1);
                CompoundTag p2 = new CompoundTag();
                p2.putString("Pattern", people != null && people.player() ? "mr" : patternFor(model, 1));
                p2.putInt("Color", DyeColor.BLACK.getId());
                patterns.add(p2);
            }
            tag.put("Patterns", patterns);
            net.minecraft.world.item.BlockItem.setBlockEntityData(item, be.getType(), tag);
            be.fromItem(item, dye);
            be.setChanged();
        }
    }

    /** Motifs d'etoffe vanilla par peuple (codes de motif de banniere). */
    private static String patternFor(String people, int layer) {
        return switch (people) {
            case PeopleApi.RESTORERS -> layer == 0 ? "bo" : "flo";
            case PeopleApi.PEDDLERS -> layer == 0 ? "cbo" : "ts";
            default -> layer == 0 ? "gra" : "bri";
        };
    }

    // ------------------------------------------------------------------ lecture du monde
    /** Classe d'un bloc pour le planificateur (meme regle que le banc : {@code PlazaBench.kind}). */
    static PlazaPlanner.Kind kind(ServerLevel level, BlockPos p, BlockState s) {
        if (s.isAir() || s.is(Blocks.STRUCTURE_VOID)) {
            return PlazaPlanner.Kind.AIR;
        }
        if (!s.getFluidState().isEmpty()) {
            //une marche ou une barriere noyee reste une construction ; l'eau, la lave, les algues sont du fluide
            return s.hasProperty(BlockStateProperties.WATERLOGGED) ? PlazaPlanner.Kind.STRUCTURE : PlazaPlanner.Kind.FLUID;
        }
        if (s.is(Blocks.DIRT_PATH) || s.is(Blocks.GRAVEL)) {
            return PlazaPlanner.Kind.PATH;
        }
        if (s.getBlock() instanceof FarmBlock || s.is(BlockTags.CROPS) || s.getBlock() instanceof StemBlock || s.getBlock() instanceof AttachedStemBlock) {
            return PlazaPlanner.Kind.FARM;
        }
        if (s.canBeReplaced()) {
            return PlazaPlanner.Kind.PLANT;
        }
        if (s.getBlock() instanceof BushBlock || s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS)) {
            return PlazaPlanner.Kind.SOFT;
        }
        boolean natural = s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.CLAY)
                          || s.is(BlockTags.BASE_STONE_OVERWORLD);
        if (natural && s.isCollisionShapeFullBlock(level, p)) {
            return PlazaPlanner.Kind.GROUND;
        }
        return PlazaPlanner.Kind.STRUCTURE;
    }

    /** Instantane de la zone de la place (lue une fois) ; un chunk revendique par un clan compte comme construction. */
    static final class Snapshot implements PlazaPlanner.World {

        private final int x0, y0, z0, sx, sy, sz;
        private final PlazaPlanner.Kind[] kinds;

        Snapshot(ServerLevel level, BlockPos bell, int r) {
            x0 = bell.getX() - r;
            z0 = bell.getZ() - r;
            y0 = bell.getY() - PlazaPlanner.BELOW - 3;
            sx = 2 * r + 1;
            sz = 2 * r + 1;
            sy = PlazaPlanner.BELOW + PlazaPlanner.ABOVE + 8;
            kinds = new PlazaPlanner.Kind[sx * sy * sz];
            Map<Long, Boolean> claimed = new HashMap<>();
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            for (int i = 0; i < sx; i++) {
                for (int k = 0; k < sz; k++) {
                    int x = x0 + i, z = z0 + k;
                    long chunk = ((long) (x >> 4) << 32) ^ ((z >> 4) & 0xFFFFFFFFL);
                    boolean owned = claimed.computeIfAbsent(chunk, c -> ClanApi.enabled() && ClanApi.owner(level.getServer(), level, new BlockPos(x, bell.getY(), z)) != null);
                    for (int j = 0; j < sy; j++) {
                        m.set(x, y0 + j, z);
                        kinds[(i * sz + k) * sy + j] = owned ? PlazaPlanner.Kind.STRUCTURE : kind(level, m, level.getBlockState(m));
                    }
                }
            }
        }

        @Override
        public PlazaPlanner.Kind at(int x, int y, int z) {
            int i = x - x0, j = y - y0, k = z - z0;
            if (i < 0 || j < 0 || k < 0 || i >= sx || j >= sy || k >= sz) {
                return PlazaPlanner.Kind.STRUCTURE;
            }
            return kinds[(i * sz + k) * sy + j];
        }
    }
}
