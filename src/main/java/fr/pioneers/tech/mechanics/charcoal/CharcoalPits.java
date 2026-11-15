package fr.pioneers.tech.mechanics.charcoal;

import fr.pioneers.Pioneers;
import fr.pioneers.core.Directions;
import fr.pioneers.core.vanilla.PiVanillaMaterials;
import fr.pioneers.farm.FarmCard;
import fr.pioneers.tech.kinetics.KineticEvents;
import fr.pioneers.tech.kinetics.KineticsConfig;
import fr.pioneers.tech.mechanics.PiMechanics;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fosses a charbon de chaque monde (meules de charbonnier). Une fosse = un tas plein de buches (3x3x2 a 7x7x4), recouvert de terre (tag
 * {@code pioneers:charcoal_pit_cover}), avec UNE ouverture a la base (l'event), allume au briquet (ou a la charge de feu) par l'event.
 * <ul>
 *     <li>combustion : 2 min + 6 s par buche (reglage {@code charcoalPitSpeed}) ; fumees blanche puis bleue ;</li>
 *     <li>une face de buche a l'air (hors event) est une fuite : la buche prend feu, et le feu gagne ses voisines ; on colmate a la pelle ;</li>
 *     <li>event bouche : la fosse etouffe (pause), puis s'eteint au bout de 2 min ;</li>
 *     <li>fin : les buches restantes deviennent du charbon carbonise ; rendement 80 % + 5 % par couche au-dessus de 2, moins les fuites ;</li>
 *     <li>drains d'argile cuite sous la fosse : recuperent la creosote (20 mB par buche).</li>
 * </ul>
 */
public final class CharcoalPits extends SavedData {

    public static final TagKey<Block> COVER = TagKey.create(Registries.BLOCK, Pioneers.rl("charcoal_pit_cover"));
    private static final String NAME = "pioneers_charcoal_pits";
    private static final int CHECK = 20, SMOTHER_LIMIT = 2_400;

    static final class Pit {

        final Set<BlockPos> logs = new HashSet<>();
        BlockPos vent;
        long start;
        int duration, initial, height, leaks, paused;
        double creosote;

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.put("logs", new LongArrayTag(logs.stream().mapToLong(BlockPos::asLong).toArray()));
            tag.putLong("vent", vent.asLong());
            tag.putLong("start", start);
            tag.putInt("duration", duration);
            tag.putInt("initial", initial);
            tag.putInt("height", height);
            tag.putInt("leaks", leaks);
            tag.putInt("paused", paused);
            tag.putDouble("creosote", creosote);
            return tag;
        }

        static Pit load(CompoundTag tag) {
            Pit pit = new Pit();
            for (long l : tag.getLongArray("logs")) {
                pit.logs.add(BlockPos.of(l));
            }
            pit.vent = BlockPos.of(tag.getLong("vent"));
            pit.start = tag.getLong("start");
            pit.duration = tag.getInt("duration");
            pit.initial = tag.getInt("initial");
            pit.height = tag.getInt("height");
            pit.leaks = tag.getInt("leaks");
            pit.paused = tag.getInt("paused");
            pit.creosote = tag.getDouble("creosote");
            return pit;
        }

        double progress(long now) {
            return Math.max(0, Math.min(1, (now - start) / (double) Math.max(1, duration)));
        }

        double expectedYield() {
            return Math.max(0.3, Math.min(0.95, 0.80 + 0.05 * (height - 2) - 0.03 * leaks));
        }
    }

    private final List<Pit> pits = new ArrayList<>();
    private final Map<BlockPos, Pit> index = new HashMap<>();

    public static CharcoalPits get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(CharcoalPits::load, CharcoalPits::new, NAME);
    }

    private static CharcoalPits load(CompoundTag tag) {
        CharcoalPits data = new CharcoalPits();
        for (Tag t : tag.getList("pits", Tag.TAG_COMPOUND)) {
            data.add(Pit.load((CompoundTag) t));
        }
        return data;
    }

    @NotNull
    @Override
    public CompoundTag save(@NotNull CompoundTag tag) {
        ListTag list = new ListTag();
        for (Pit pit : pits) {
            list.add(pit.save());
        }
        tag.put("pits", list);
        return tag;
    }

    private void add(Pit pit) {
        pits.add(pit);
        for (BlockPos p : pit.logs) {
            index.put(p, pit);
        }
    }

    private void remove(Pit pit) {
        pits.remove(pit);
        index.values().removeIf(p -> p == pit);
        setDirty();
    }

    static boolean isLog(BlockState state) {
        return state.is(BlockTags.LOGS_THAT_BURN);
    }

    static boolean isCover(BlockState state) {
        return state.is(COVER);
    }

    /** Une buche entouree au moins en partie de couverture : tentative de fosse (sinon le briquet garde son comportement). */
    public static boolean looksLikePit(ServerLevel level, BlockPos pos) {
        if (!isLog(level.getBlockState(pos))) {
            return false;
        }
        for (Direction d : Directions.ALL) {
            if (isCover(level.getBlockState(pos.relative(d)))) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Allumage
    // ------------------------------------------------------------------

    /** Allume la fosse dont fait partie la buche {@code pos} ; renvoie le message pour le joueur. */
    public Component ignite(ServerLevel level, BlockPos pos) {
        if (index.containsKey(pos)) {
            return Component.translatable("charcoal.pioneers.already").withStyle(ChatFormatting.YELLOW);
        }
        Object checked = check(level, pos);
        if (checked instanceof Component error) {
            return error;
        }
        Pit pit = (Pit) checked;
        pit.start = level.getGameTime();
        add(pit);
        setDirty();
        level.playSound(null, pit.vent, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1F, 0.8F);
        return Component.translatable("charcoal.pioneers.lit", pit.logs.size(), Math.round(pit.duration / 1200.0 * 10) / 10.0).withStyle(ChatFormatting.GOLD);
    }

    /**
     * 1.4 : verification d'un tas (commune a l'allumage et a la fiche au regard) : la fosse prete (pas encore inscrite, {@code start} a 0) ou
     * le message d'erreur.
     */
    private static Object check(ServerLevel level, BlockPos pos) {
        return check(level, pile(level, pos));
    }

    /** Plus grand tas accepte (7 x 7 x 4). */
    private static final int MAX_LOGS = 196;

    /** 1.6 : buches reliees a {@code pos} (par les faces), au plus {@link #MAX_LOGS} + 1 (au-dela : tas trop grand). */
    private static Set<BlockPos> pile(ServerLevel level, BlockPos pos) {
        Set<BlockPos> logs = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(pos.immutable());
        logs.add(pos.immutable());
        while (!queue.isEmpty() && logs.size() <= MAX_LOGS) {
            BlockPos p = queue.poll();
            for (Direction d : Directions.ALL) {
                BlockPos n = p.relative(d);
                if (logs.size() <= MAX_LOGS && !logs.contains(n) && isLog(level.getBlockState(n))) {
                    logs.add(n.immutable());
                    queue.add(n.immutable());
                }
            }
        }
        return logs;
    }

    private static Object check(ServerLevel level, Set<BlockPos> logs) {
        if (logs.size() > MAX_LOGS) {
            return Component.translatable("charcoal.pioneers.too_big").withStyle(ChatFormatting.RED);
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos p : logs) {
            minX = Math.min(minX, p.getX());
            minY = Math.min(minY, p.getY());
            minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX());
            maxY = Math.max(maxY, p.getY());
            maxZ = Math.max(maxZ, p.getZ());
        }
        int dx = maxX - minX + 1, dy = maxY - minY + 1, dz = maxZ - minZ + 1;
        if (dx < 3 || dz < 3 || dy < 2 || logs.size() < 18) {
            return Component.translatable("charcoal.pioneers.too_small").withStyle(ChatFormatting.RED);
        }
        if (dx > 7 || dz > 7 || dy > 4) {
            return Component.translatable("charcoal.pioneers.too_big").withStyle(ChatFormatting.RED);
        }
        if (logs.size() < 0.85 * dx * dy * dz) {
            return Component.translatable("charcoal.pioneers.not_full").withStyle(ChatFormatting.RED);
        }
        List<BlockPos> openings = openings(level, logs, null);
        if (openings.isEmpty()) {
            return Component.translatable("charcoal.pioneers.no_vent").withStyle(ChatFormatting.RED);
        }
        if (openings.size() > 1) {
            for (BlockPos o : openings) {
                level.sendParticles(ParticleTypes.ANGRY_VILLAGER, o.getX() + 0.5, o.getY() + 0.5, o.getZ() + 0.5, 1, 0, 0, 0, 0);
            }
            return Component.translatable("charcoal.pioneers.openings", openings.size() - 1).withStyle(ChatFormatting.RED);
        }
        BlockPos vent = openings.get(0);
        if (vent.getY() != minY) {
            return Component.translatable("charcoal.pioneers.vent_low").withStyle(ChatFormatting.RED);
        }
        Pit pit = new Pit();
        pit.logs.addAll(logs);
        pit.vent = vent;
        pit.initial = logs.size();
        pit.height = dy;
        pit.duration = (int) Math.round((2_400 + 120 * logs.size()) / Math.max(0.1, KineticsConfig.charcoalSpeed()));
        return pit;
    }

    /**
     * 1.4 : fiche au regard d'une buche : fosse allumee (cuisson, temps restant, rendement, fuites) ou meule pas encore allumee (prete, ou ce
     * qui ne va pas). Null pour une buche ordinaire.
     * <p>
     * 1.6 (Guillaume : « quand on regarde un arbre on a l'info-bulle de la fosse a charbon ») : un tronc pose sur l'herbe touchait de la
     * « couverture » (la terre dessous) et passait pour une meule. La fiche d'une meule froide n'apparait plus que sur une VRAIE meule
     * ({@link #isPile}) : tas plein de la bonne taille (le briquet le prendrait aussi pour une meule), ou tas couvert pour l'essentiel.
     */
    @Nullable
    public static FarmCard card(ServerLevel level, BlockPos pos) {
        CharcoalPits data = get(level);
        Pit pit = data.index.get(pos);
        List<FarmCard.Line> lines = new ArrayList<>();
        ItemStack icon = new ItemStack(PiVanillaMaterials.item("charcoal"));
        if (pit != null) {
            long now = level.getGameTime();
            double progress = pit.progress(now);
            int leaks = openings(level, pit.logs, pit.vent).size();
            lines.add(FarmCard.Line.gauge(FarmCard.label("charring"), (float) progress, FarmCard.percent((float) progress), 0xF09040));
            lines.add(FarmCard.Line.pair(FarmCard.label("left"), FarmCard.duration(Math.max(0, (pit.duration - (now - pit.start)) / 20)), FarmCard.INFO));
            lines.add(FarmCard.Line.pair(FarmCard.label("yield"), FarmCard.percent((float) pit.expectedYield()), FarmCard.OK));
            if (pit.paused > 0) {
                lines.add(FarmCard.Line.pair(FarmCard.label("vent"), FarmCard.word("pit.blocked"), FarmCard.WARN));
            } else {
                lines.add(leaks > 0 ? FarmCard.Line.pair(FarmCard.label("leaks"), FarmCard.word("pit.leaks", leaks), FarmCard.BAD)
                                    : FarmCard.Line.pair(FarmCard.label("leaks"), FarmCard.word("none_f"), FarmCard.OK));
            }
            return new FarmCard(icon, Component.translatable("charcoal.pioneers.look.title"), 0xF09040, lines);
        }
        if (!looksLikePit(level, pos)) {
            return null;
        }
        Set<BlockPos> logs = pile(level, pos);
        Object checked = check(level, logs);
        if (checked instanceof Component error) {
            String key = error.getContents() instanceof TranslatableContents t ? t.getKey() : "";
            if (!STRUCTURAL.contains(key) && !isPile(level, logs)) {
                return null;                                         //Tronc d'arbre, mur de rondins... : pas de fiche
            }
            String shortKey = key.startsWith("charcoal.pioneers.") ? "card.pioneers.pit." + key.substring("charcoal.pioneers.".length()) : key;
            Object[] args = error.getContents() instanceof TranslatableContents t ? t.getArgs() : new Object[0];
            lines.add(FarmCard.Line.text(Component.translatable(shortKey, args), FarmCard.BAD));
            lines.add(FarmCard.Line.hint(FarmCard.word("pit.rules")));
        } else {
            Pit draft = (Pit) checked;
            lines.add(FarmCard.Line.pair(FarmCard.label("logs"), Component.literal(String.valueOf(draft.logs.size())), FarmCard.OK));
            lines.add(FarmCard.Line.pair(FarmCard.label("duration"), FarmCard.duration(draft.duration / 20), FarmCard.INFO));
            lines.add(FarmCard.Line.hint(FarmCard.word("pit.light")));
        }
        return new FarmCard(icon, Component.translatable("charcoal.pioneers.look.title_cold"), 0x909090, lines);
    }

    /** Erreurs d'un tas plein et de bonne taille (comme le briquet : c'est bien une meule, mal fermee). */
    private static final Set<String> STRUCTURAL = Set.of("charcoal.pioneers.no_vent", "charcoal.pioneers.openings", "charcoal.pioneers.vent_low");

    /**
     * 1.6 : tas de buches couvert pour l'essentiel : le dessus de la couche haute touche la couverture et au moins 60 % des faces exposees du
     * tas sont couvertes (un arbre n'a que sa base dans la terre ; un mur de rondins a ses deux faces a l'air).
     */
    private static boolean isPile(ServerLevel level, Set<BlockPos> logs) {
        if (logs.size() < 8) {
            return false;
        }
        int top = Integer.MIN_VALUE;
        for (BlockPos p : logs) {
            top = Math.max(top, p.getY());
        }
        int exposed = 0, covered = 0, topCovered = 0;
        for (BlockPos p : logs) {
            for (Direction d : Directions.ALL) {
                BlockPos n = p.relative(d);
                if (logs.contains(n) || !level.isLoaded(n)) {
                    continue;
                }
                BlockState state = level.getBlockState(n);
                if (isLog(state)) {
                    continue;                                        //Tas trop grand lu en partie : la suite du tas
                }
                exposed++;
                if (isCover(state)) {
                    covered++;
                    if (d == Direction.UP && p.getY() == top) {
                        topCovered++;
                    }
                }
            }
        }
        return exposed > 0 && covered >= 0.6 * exposed && topCovered > 0;
    }

    /** Faces de buches qui ne touchent ni une buche du tas, ni la couverture (air, autre bloc) ; {@code vent} exclu. */
    private static List<BlockPos> openings(ServerLevel level, Set<BlockPos> logs, @Nullable BlockPos vent) {
        List<BlockPos> out = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        for (BlockPos p : logs) {
            for (Direction d : Directions.ALL) {
                BlockPos n = p.relative(d);
                if (logs.contains(n) || n.equals(vent) || !seen.add(n) || !level.isLoaded(n)) {
                    continue;
                }
                BlockState state = level.getBlockState(n);
                if (!isCover(state) && !isLog(state)) {
                    out.add(n);
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Combustion
    // ------------------------------------------------------------------

    public void tick(ServerLevel level) {
        long now = level.getGameTime();
        if (pits.isEmpty() || now % CHECK != 0) {
            return;
        }
        for (Pit pit : List.copyOf(pits)) {
            //Buches enlevees a la main : perdues pour la fosse
            pit.logs.removeIf(p -> level.isLoaded(p) && !isLog(level.getBlockState(p)));
            if (pit.logs.isEmpty()) {
                remove(pit);
                continue;
            }
            if (!level.isLoaded(pit.vent)) {
                pit.start += CHECK;
                continue;
            }
            //Event bouche : la fosse etouffe
            BlockState ventState = level.getBlockState(pit.vent);
            if (!ventState.isAir() && !(ventState.getBlock() instanceof BaseFireBlock)) {
                pit.start += CHECK;
                pit.paused += CHECK;
                if (pit.paused > SMOTHER_LIMIT) {
                    remove(pit);
                    level.playSound(null, pit.vent, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1F, 0.6F);
                }
                setDirty();
                continue;
            }
            pit.paused = 0;
            List<BlockPos> leaks = openings(level, pit.logs, pit.vent);
            for (BlockPos leak : leaks) {
                if (level.random.nextFloat() < 0.25F) {
                    burnNear(level, pit, leak);
                }
            }
            smoke(level, pit, now, leaks);
            creosote(level, pit);
            if (now - pit.start >= pit.duration) {
                finish(level, pit);
            }
            setDirty();
        }
    }

    /** La buche qui donne sur la fuite prend feu (le feu vanilla gagne ensuite ses voisines). */
    private void burnNear(ServerLevel level, Pit pit, BlockPos leak) {
        for (Direction d : Directions.ALL) {
            BlockPos log = leak.relative(d);
            if (pit.logs.remove(log)) {
                index.remove(log);
                pit.leaks++;
                level.setBlock(log, BaseFireBlock.getState(level, log), Block.UPDATE_ALL);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, leak.getX() + 0.5, leak.getY() + 0.5, leak.getZ() + 0.5, 6, 0.2, 0.2, 0.2, 0.02);
                return;
            }
        }
    }

    private static void smoke(ServerLevel level, Pit pit, long now, List<BlockPos> leaks) {
        double progress = pit.progress(now);
        //Blanche (sechage), bleue (carbonisation), presque invisible a la fin
        var particle = progress < 0.35 ? ParticleTypes.CAMPFIRE_COSY_SMOKE : progress < 0.9 ? ParticleTypes.SMOKE : ParticleTypes.WHITE_ASH;
        int top = Integer.MIN_VALUE;
        for (BlockPos p : pit.logs) {
            top = Math.max(top, p.getY());
        }
        int i = 0;
        for (BlockPos p : pit.logs) {
            if (p.getY() == top && (i++ + now / CHECK) % 5 == 0) {
                level.sendParticles(particle, p.getX() + 0.5, p.getY() + 2.1, p.getZ() + 0.5, 1, 0.3, 0.1, 0.3, 0.01);
            }
        }
        level.sendParticles(ParticleTypes.SMOKE, pit.vent.getX() + 0.5, pit.vent.getY() + 0.5, pit.vent.getZ() + 0.5, 2, 0.1, 0.1, 0.1, 0.01);
        for (BlockPos leak : leaks) {
            level.sendParticles(ParticleTypes.LARGE_SMOKE, leak.getX() + 0.5, leak.getY() + 0.5, leak.getZ() + 0.5, 2, 0.2, 0.2, 0.2, 0.02);
        }
    }

    /** Creosote : 20 mB par buche sur toute la combustion, versee dans les drains d'argile sous la fosse. */
    private static void creosote(ServerLevel level, Pit pit) {
        pit.creosote += 20.0 * pit.initial * CHECK / Math.max(1, pit.duration);
        int whole = (int) pit.creosote;
        if (whole <= 0) {
            return;
        }
        int minY = Integer.MAX_VALUE;
        for (BlockPos p : pit.logs) {
            minY = Math.min(minY, p.getY());
        }
        for (BlockPos p : pit.logs) {
            if (p.getY() == minY && level.getBlockEntity(p.below()) instanceof ClayDrainBlock.Tile drain) {
                int took = drain.collect(whole);
                whole -= took;
                pit.creosote -= took;
                if (whole <= 0) {
                    return;
                }
            }
        }
        pit.creosote = Math.min(pit.creosote, 1);                   //Sans drain : la creosote s'evapore
    }

    private void finish(ServerLevel level, Pit pit) {
        double yield = pit.expectedYield();
        BlockState charred = PiMechanics.CHARRED_LOG_PILE.getBlock().defaultBlockState();
        //1.1.3 : de bas en haut ; une cendre posee sur une cendre se tasse dans celle du dessous (plus de couches qui flottent)
        BlockState ash = PiMechanics.ASH_LAYER.getBlock().defaultBlockState();
        List<BlockPos> order = new ArrayList<>(pit.logs);
        order.sort(Comparator.comparingInt(BlockPos::getY));
        for (BlockPos p : order) {
            boolean coal = level.random.nextDouble() < yield;
            if (!coal && level.getBlockState(p.below()).is(ash.getBlock())) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            } else {
                level.setBlock(p, coal ? charred : ash, Block.UPDATE_ALL);
            }
        }
        level.playSound(null, pit.vent, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1F, 0.9F);
        KineticEvents.award(level, pit.vent, "charcoal_pit");
        remove(pit);
    }

    // ------------------------------------------------------------------
    // Pique de charbonnier
    // ------------------------------------------------------------------

    @Nullable
    public static Component report(ServerLevel level, BlockPos pos) {
        CharcoalPits data = get(level);
        Pit pit = data.index.get(pos);
        if (pit == null) {
            for (Direction d : Directions.ALL) {
                pit = data.index.get(pos.relative(d));
                if (pit != null) {
                    break;
                }
            }
        }
        if (pit == null) {
            return null;
        }
        long now = level.getGameTime();
        int leaks = openings(level, pit.logs, pit.vent).size();
        return Component.translatable("charcoal.pioneers.report", Math.round(pit.progress(now) * 100), Math.max(0, (pit.duration - (now - pit.start)) / 20),
              leaks, Math.round(pit.expectedYield() * 100)).withStyle(leaks > 0 ? ChatFormatting.RED : ChatFormatting.GOLD);
    }
}
