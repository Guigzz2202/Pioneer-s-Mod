package fr.pioneers.robotics.works;

import fr.pioneers.core.PiTags;
import fr.pioneers.farm.kitchen.BigStationBlock;
import fr.pioneers.rail.track.TrackBlock;
import fr.pioneers.transport.legacy.LegacyCableBlock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Plan de travaux : une suite ordonnee d'operations (vider un volume, poser des blocs : route, collage d'un plan). L'etat voulu d'une
 * position est celui de la derniere operation qui la touche ; les unites executent le tout dans le bon ordre (d'abord les retraits, du
 * haut vers le bas, puis les poses, du bas vers le haut), quel que soit l'ordre de saisie.
 */
public final class WorksPlan {

    /** Limites : volume vide (blocs), blocs poses, operations. */
    public static final int MAX_CLEAR_VOLUME = 96 * 96 * 64, MAX_PLACED = 32_768, MAX_OPS = 64, MAX_SPAN = 192;
    /** 1.9 (MAJ RP) : plafond absolu des operations (skill Robotique : jusqu'a +20 % au-dessus de {@link #MAX_OPS}). */
    public static final int MAX_OPS_CAP = 96;

    /** Operations permises a ce joueur (skill Robotique, « Instructions du programmeur »). */
    public static int maxOps(@javax.annotation.Nullable net.minecraft.world.entity.player.Player player) {
        if (player == null) {
            return MAX_OPS;
        }
        return Math.min(MAX_OPS_CAP, (int) Math.floor(MAX_OPS * fr.pioneers.core.profile.PioneerApi.factor(player, "robotics.programmer_slots") + 1e-6));
    }

    public enum Kind {CLEAR, ROAD, PASTE}

    /** Une operation : un volume a vider, ou des blocs a poser (l'air compris : il vide la case). */
    public static final class Op {

        public final Kind kind;
        @Nullable
        public final BlockPos min, max;
        /** Blocs poses (position -> etat), ordre d'insertion conserve. */
        public final Map<BlockPos, BlockState> blocks;
        /** Collage : reglages logistiques des points copies (position -> reglages), appliques a la pose du bloc. */
        public final Map<BlockPos, CompoundTag> configs;
        /** Collage : zones de travail copiees (ordres complets, bornes absolues), creees a la fin du chantier. */
        public final List<CompoundTag> zones;

        private Op(Kind kind, @Nullable BlockPos min, @Nullable BlockPos max, Map<BlockPos, BlockState> blocks, Map<BlockPos, CompoundTag> configs,
              List<CompoundTag> zones) {
            this.kind = kind;
            this.min = min;
            this.max = max;
            this.blocks = blocks;
            this.configs = configs;
            this.zones = zones;
        }

        public static Op clear(BlockPos a, BlockPos b) {
            return new Op(Kind.CLEAR, new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ())),
                  new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ())), Map.of(), Map.of(), List.of());
        }

        public static Op place(Kind kind, Map<BlockPos, BlockState> blocks) {
            return place(kind, blocks, Map.of(), List.of());
        }

        public static Op place(Kind kind, Map<BlockPos, BlockState> blocks, Map<BlockPos, CompoundTag> configs, List<CompoundTag> zones) {
            int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
            for (BlockPos pos : blocks.keySet()) {
                x0 = Math.min(x0, pos.getX());
                y0 = Math.min(y0, pos.getY());
                z0 = Math.min(z0, pos.getZ());
                x1 = Math.max(x1, pos.getX());
                y1 = Math.max(y1, pos.getY());
                z1 = Math.max(z1, pos.getZ());
            }
            boolean empty = blocks.isEmpty();
            return new Op(kind, empty ? null : new BlockPos(x0, y0, z0), empty ? null : new BlockPos(x1, y1, z1), new LinkedHashMap<>(blocks),
                  new LinkedHashMap<>(configs), new ArrayList<>(zones));
        }

        @Nullable
        BlockState target(BlockPos pos) {
            if (min == null || max == null || pos.getX() < min.getX() || pos.getY() < min.getY() || pos.getZ() < min.getZ() || pos.getX() > max.getX()
                || pos.getY() > max.getY() || pos.getZ() > max.getZ()) {
                return null;
            }
            return kind == Kind.CLEAR ? Blocks.AIR.defaultBlockState() : blocks.get(pos);
        }

        public long size() {
            if (kind == Kind.CLEAR && min != null && max != null) {
                return (long) (max.getX() - min.getX() + 1) * (max.getY() - min.getY() + 1) * (max.getZ() - min.getZ() + 1);
            }
            return blocks.size();
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putByte("kind", (byte) kind.ordinal());
            if (kind == Kind.CLEAR && min != null && max != null) {
                tag.putLong("min", min.asLong());
                tag.putLong("max", max.asLong());
                return tag;
            }
            //Palette d'etats, puis une position et un indice par bloc
            List<BlockState> palette = new ArrayList<>();
            Map<BlockState, Integer> index = new HashMap<>();
            long[] positions = new long[blocks.size()];
            int[] states = new int[blocks.size()];
            int i = 0;
            for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
                positions[i] = entry.getKey().asLong();
                states[i] = index.computeIfAbsent(entry.getValue(), state -> {
                    palette.add(state);
                    return palette.size() - 1;
                });
                i++;
            }
            ListTag list = new ListTag();
            for (BlockState state : palette) {
                list.add(NbtUtils.writeBlockState(state));
            }
            tag.put("palette", list);
            tag.putLongArray("pos", positions);
            tag.putIntArray("state", states);
            if (!configs.isEmpty()) {
                ListTag points = new ListTag();
                for (Map.Entry<BlockPos, CompoundTag> entry : configs.entrySet()) {
                    CompoundTag point = new CompoundTag();
                    point.putLong("pos", entry.getKey().asLong());
                    point.put("config", entry.getValue());
                    points.add(point);
                }
                tag.put("configs", points);
            }
            if (!zones.isEmpty()) {
                ListTag areas = new ListTag();
                areas.addAll(zones);
                tag.put("zones", areas);
            }
            return tag;
        }

        @Nullable
        static Op load(CompoundTag tag, HolderGetter<Block> blocks) {
            Kind kind = Kind.values()[Math.floorMod(tag.getByte("kind"), Kind.values().length)];
            if (kind == Kind.CLEAR) {
                return tag.contains("min") ? clear(BlockPos.of(tag.getLong("min")), BlockPos.of(tag.getLong("max"))) : null;
            }
            ListTag list = tag.getList("palette", Tag.TAG_COMPOUND);
            BlockState[] palette = new BlockState[list.size()];
            for (int i = 0; i < palette.length; i++) {
                palette[i] = LegacyCableBlock.upgrade(NbtUtils.readBlockState(blocks, list.getCompound(i)));   //1.11 : ancien cable -> cable logistique
            }
            long[] positions = tag.getLongArray("pos");
            int[] states = tag.getIntArray("state");
            Map<BlockPos, BlockState> map = new LinkedHashMap<>();
            for (int i = 0; i < Math.min(positions.length, states.length) && map.size() < MAX_PLACED; i++) {
                if (states[i] >= 0 && states[i] < palette.length) {
                    map.put(BlockPos.of(positions[i]), palette[states[i]]);
                }
            }
            Map<BlockPos, CompoundTag> configs = new LinkedHashMap<>();
            for (Tag entry : tag.getList("configs", Tag.TAG_COMPOUND)) {
                CompoundTag point = (CompoundTag) entry;
                if (configs.size() < 256) {
                    configs.put(BlockPos.of(point.getLong("pos")), point.getCompound("config"));
                }
            }
            List<CompoundTag> zones = new ArrayList<>();
            for (Tag entry : tag.getList("zones", Tag.TAG_COMPOUND)) {
                if (zones.size() < 32) {
                    zones.add((CompoundTag) entry);
                }
            }
            return place(kind, map, configs, zones);
        }
    }

    public final List<Op> ops = new ArrayList<>();

    public WorksPlan copy() {
        WorksPlan plan = new WorksPlan();
        plan.ops.addAll(ops);
        return plan;
    }

    public boolean isEmpty() {
        return ops.isEmpty();
    }

    /** Etat voulu a cette position (derniere operation qui la touche), ou null si le plan n'y touche pas. */
    @Nullable
    public BlockState target(BlockPos pos) {
        for (int i = ops.size() - 1; i >= 0; i--) {
            BlockState state = ops.get(i).target(pos);
            if (state != null) {
                return state;
            }
        }
        return null;
    }

    /** Reglages logistiques a appliquer au bloc pose ici (derniere operation qui touche la case), ou null. */
    @Nullable
    public CompoundTag configAt(BlockPos pos) {
        for (int i = ops.size() - 1; i >= 0; i--) {
            Op op = ops.get(i);
            if (op.target(pos) != null) {
                return op.configs.get(pos);
            }
        }
        return null;
    }

    /** Zones de travail a creer une fois le chantier fini. */
    public List<CompoundTag> zones() {
        List<CompoundTag> list = new ArrayList<>();
        for (Op op : ops) {
            list.addAll(op.zones);
        }
        return list;
    }

    /** Etat d'une position une fois le plan applique (le monde sinon). */
    public BlockState after(BlockGetter level, BlockPos pos) {
        BlockState target = target(pos);
        return target != null ? target : level.getBlockState(pos);
    }

    /** Volume englobant de toutes les operations, ou null. */
    @Nullable
    public AABB bounds() {
        AABB box = null;
        for (Op op : ops) {
            if (op.min == null || op.max == null) {
                continue;
            }
            AABB b = new AABB(op.min, op.max.offset(1, 1, 1));
            box = box == null ? b : box.minmax(b);
        }
        return box;
    }

    @Nullable
    public BlockPos min() {
        AABB box = bounds();
        return box == null ? null : BlockPos.containing(box.minX, box.minY, box.minZ);
    }

    @Nullable
    public BlockPos max() {
        AABB box = bounds();
        return box == null ? null : BlockPos.containing(box.maxX - 1, box.maxY - 1, box.maxZ - 1);
    }

    /** Le plan respecte-t-il les limites (taille, nombre de blocs) ? */
    public boolean isValid() {
        if (ops.isEmpty() || ops.size() > MAX_OPS_CAP) {
            return false;
        }
        long cleared = 0, placed = 0;
        for (Op op : ops) {
            if (op.kind == Kind.CLEAR) {
                cleared += op.size();
            } else {
                placed += op.size();
            }
        }
        AABB box = bounds();
        return box != null && cleared <= MAX_CLEAR_VOLUME * 4L && placed <= MAX_PLACED && box.getXsize() <= MAX_SPAN && box.getZsize() <= MAX_SPAN
               && box.getYsize() <= 128;
    }

    // ------------------------------------------------------------------
    // Execution
    // ------------------------------------------------------------------

    /** Terre qu'une unite transforme sur place en chemin (pelle) ou en terre labouree (houe), sans rien apporter. */
    public static boolean convertible(BlockState current, BlockState target) {
        //0.20.7 : une voie deja posee change de forme sur place (aiguillage, courbe, diagonale, pente)
        if (current.getBlock() == target.getBlock() && target.getBlock() instanceof TrackBlock) {
            return true;
        }
        boolean shaped = target.is(Blocks.DIRT_PATH) || target.is(Blocks.FARMLAND);
        return shaped && current.is(PiTags.Blocks.ROBOT_SHAPEABLE);
    }

    /** Objet a apporter pour obtenir cet etat ici, compte tenu de ce qu'il y a deja (la terre se faconne sur place). */
    public static ItemStack requiredItem(Level level, BlockPos pos, BlockState state, BlockState current) {
        return convertible(current, state) ? ItemStack.EMPTY : requiredItem(level, pos, state);
    }

    /** Ce qu'une unite pose pour obtenir cet etat (une graine pour une culture...) ; vide si rien (moitie haute d'une porte). */
    public static ItemStack requiredItem(Level level, BlockPos pos, BlockState state) {
        if (state.isAir() || state.getBlock() instanceof LiquidBlock) {
            return ItemStack.EMPTY;
        }
        //Chemin et terre labouree : de la terre, faconnee a la pose
        if (state.is(Blocks.DIRT_PATH) || state.is(Blocks.FARMLAND)) {
            return new ItemStack(Items.DIRT);
        }
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
            return ItemStack.EMPTY;
        }
        if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD) {
            return ItemStack.EMPTY;
        }
        try {
            @SuppressWarnings("deprecation")
            ItemStack stack = state.getBlock().getCloneItemStack(level, pos, state);
            return stack.copyWithCount(1);
        } catch (RuntimeException e) {
            return new ItemStack(state.getBlock().asItem());
        }
    }

    /** Moitie haute d'une porte, tete d'un lit, case secondaire d'un grand poste de cuisine (1.6)... : posee avec le reste, sans objet. */
    public static boolean isFreeHalf(BlockState state) {
        return state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER
               || state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD
               || state.getBlock() instanceof BigStationBlock && state.getValue(BigStationBlock.PART) != 0;
    }

    /** Etat effectivement pose (une culture repart de zero). */
    public static BlockState placedState(BlockState state) {
        if (state.getBlock() instanceof CropBlock crop) {
            return crop.getStateForAge(0);
        }
        return state;
    }

    /** Proprietes que le monde fait evoluer seul (connexions, age, eclairage...) : on ne les compare pas. */
    private static final Set<String> LOOSE = Set.of("age", "north", "south", "east", "west", "up", "down", "shape", "waterlogged",
          "distance", "persistent", "power", "powered", "snowy", "in_wall", "occupied", "lit", "moisture", "stage", "bites", "level", "note", "triggered",
          //0.20.7 : etats que le chemin de fer fait evoluer seul (lames, feux, barrieres)
          "route", "aspect", "closed", "blink");

    /** L'etat du monde correspond-il au plan (aux proprietes changeantes pres) ? */
    public static boolean matches(BlockState current, BlockState target) {
        if (current == target) {
            return true;
        }
        if (target.isAir()) {
            return current.isAir();
        }
        if (current.getBlock() != target.getBlock()) {
            return false;
        }
        //0.20.7 : la forme d'une voie compte (« shape » est sinon une propriete changeante : escaliers)
        if (target.getBlock() instanceof TrackBlock tb && tb.shape(current) != tb.shape(target)) {
            return false;
        }
        for (Property<?> property : target.getProperties()) {
            if (!LOOSE.contains(property.getName()) && current.hasProperty(property) && !current.getValue(property).equals(target.getValue(property))) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Sauvegarde
    // ------------------------------------------------------------------

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Op op : ops) {
            list.add(op.save());
        }
        tag.put("ops", list);
        return tag;
    }

    public static WorksPlan load(CompoundTag tag, HolderGetter<Block> blocks) {
        WorksPlan plan = new WorksPlan();
        for (Tag entry : tag.getList("ops", Tag.TAG_COMPOUND)) {
            Op op = Op.load((CompoundTag) entry, blocks);
            if (op != null && plan.ops.size() < MAX_OPS_CAP) {
                plan.ops.add(op);
            }
        }
        return plan;
    }

    public static HolderGetter<Block> blocks(Level level) {
        return level.holderLookup(Registries.BLOCK);
    }
}
