package fr.pioneers.lighting;

import fr.pioneers.core.Wrenches;
import fr.pioneers.network.PacketLighting;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 5) : LIAISONS INTERRUPTEUR -> LUMIERES d'une dimension (SavedData {@value #NAME}) : la verite des liaisons et de l'etat
 * de chaque interrupteur, meme quand son troncon ou celui de ses lumieres est decharge.
 * <ul>
 *     <li>un interrupteur -> jusqu'a {@link #MAX_LIGHTS} lumieres a {@link #RANGE} blocs au plus (meme dimension) ; une lumiere -> UN
 *     interrupteur (la lier a un autre la detache du premier) ;</li>
 *     <li>liaison : accroupi + clic droit main vide sur l'interrupteur (memorise {@link #MEMORY} ticks, par joueur), puis accroupi + clic droit
 *     sur chaque lumiere (lie / delie, message avec le nombre de lumieres liees) ; re-clic accroupi sur l'interrupteur : termine ;</li>
 *     <li>marche / arret ({@link #switchChanged}) : les lumieres chargees changent tout de suite, les autres au chargement de leur troncon
 *     ({@link #chunkLoaded} puis {@link #tick}) ;</li>
 *     <li>interrupteur casse : ses lumieres redeviennent libres (toujours allumees) ; lumiere cassee : retiree de son interrupteur ; une case
 *     qui n'est plus une lumiere (chargee) est oubliee au passage.</li>
 * </ul>
 * Sauvegarde : {@code switches[{pos, on, lights[]}]}.
 */
public final class LightLinks extends SavedData {

    public static final String NAME = "pioneers_light_links";
    /** Portee d'une liaison (blocs, distance euclidienne entre l'interrupteur et la lumiere), lumieres par interrupteur au plus. */
    public static final int RANGE = 32, MAX_LIGHTS = 64;
    /** Duree de la memoire d'un interrupteur pendant la liaison (ticks : 5 minutes). */
    public static final int MEMORY = 6_000;

    /**
     * 1.0.4-beta (lot 33, Guillaume : « faire en sorte que les liens avec les lumieres et interrupteurs ne soient visibles qu'avec la cle a
     * molette ») : les traits des liaisons ({@code J:client/lighting/LightLinkPreview}) ne se montrent QUE si le joueur tient une cle a
     * molette ({@link Wrenches#isWrench}) en main principale OU secondaire ; sans cle, le client ne demande meme plus les liaisons. Meme regle
     * que la vue a travers les caches ({@code J:client/render/CoverXRay}).
     */
    public static boolean showsLinks(ItemStack main, ItemStack off) {
        return Wrenches.isWrench(main) || Wrenches.isWrench(off);
    }

    /** 1.0.4-beta (lot 33) : main qui peut lier (accroupi + clic droit) : vide (lot 5) ou une cle a molette (on voit alors les traits). */
    public static boolean linkingHand(ItemStack held) {
        return held.isEmpty() || Wrenches.isWrench(held);
    }
    /** Troncons charges traites par tick au plus (les lumieres d'un troncon qui vient de charger). */
    static final int CHUNKS_PER_TICK = 32;

    /** Un interrupteur : son etat et ses lumieres (positions empaquetees). */
    static final class Switch {
        boolean on;
        final LinkedHashSet<Long> lights = new LinkedHashSet<>();
    }

    final Map<Long, Switch> switches = new HashMap<>();
    /** Lumiere -> interrupteur ; troncon -> lumieres reliees (recalcule au chargement). */
    final Map<Long, Long> owner = new HashMap<>();
    final Map<Long, Set<Long>> byChunk = new HashMap<>();
    /** Troncons a resynchroniser (charges depuis le dernier tick). */
    final ArrayDeque<Long> pending = new ArrayDeque<>();
    final Set<Long> pendingSet = new HashSet<>();

    /** Interrupteur memorise par joueur : dimension, position, fin. */
    record Memory(ResourceKey<Level> dim, BlockPos pos, long until) {
    }

    private static final Map<UUID, Memory> MEMORIES = new HashMap<>();

    public LightLinks() {
    }

    public static LightLinks get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(LightLinks::load, LightLinks::new, NAME);
    }

    // ------------------------------------------------------------------ regle d'allumage

    /** Interrupteur d'une lumiere, ou null (lumiere libre). */
    @Nullable
    public BlockPos switchOf(BlockPos light) {
        Long s = owner.get(light.asLong());
        return s == null ? null : BlockPos.of(s);
    }

    /** Lumieres d'un interrupteur (vide s'il n'en a pas). */
    public List<BlockPos> lightsOf(BlockPos sw) {
        Switch s = switches.get(sw.asLong());
        List<BlockPos> out = new ArrayList<>();
        if (s != null) {
            for (long l : s.lights) {
                out.add(BlockPos.of(l));
            }
        }
        return out;
    }

    /** Etat memorise d'un interrupteur (faux s'il n'est pas connu). */
    public boolean isOn(BlockPos sw) {
        Switch s = switches.get(sw.asLong());
        return s != null && s.on;
    }

    /** Doit-elle etre allumee ? Libre : oui ; reliee : interrupteur sur « marche » OU signal de redstone recu. */
    public boolean desired(ServerLevel level, BlockPos light) {
        Long s = owner.get(light.asLong());
        if (s == null) {
            return true;
        }
        Switch sw = switches.get(s);
        return sw != null && sw.on || level.hasNeighborSignal(light);
    }

    /** Applique la regle a une lumiere chargee (rien si la case n'est pas chargee ; oubliee si ce n'est plus une lumiere). */
    public static void apply(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return;
        }
        LightLinks data = get(level);
        BlockState st = level.getBlockState(pos);
        if (!(st.getBlock() instanceof LightFixtureBlock)) {
            data.forgetLight(pos.asLong());
            return;
        }
        boolean lit = data.desired(level, pos);
        if (st.getValue(LightFixtureBlock.LIT) != lit) {
            level.setBlock(pos, st.setValue(LightFixtureBlock.LIT, lit), Block.UPDATE_CLIENTS);
        }
    }

    // ------------------------------------------------------------------ evenements des blocs

    /** Marche / arret d'un interrupteur (bloc deja change). */
    public static void switchChanged(ServerLevel level, BlockPos pos, boolean on) {
        LightLinks data = get(level);
        Switch s = data.switches.computeIfAbsent(pos.asLong(), k -> new Switch());
        s.on = on;
        data.setDirty();
        for (Long l : new ArrayList<>(s.lights)) {
            apply(level, BlockPos.of(l));
        }
    }

    /** Interrupteur casse : ses lumieres redeviennent libres (allumees). */
    public static void switchRemoved(ServerLevel level, BlockPos pos) {
        LightLinks data = get(level);
        Switch s = data.switches.remove(pos.asLong());
        if (s == null) {
            return;
        }
        data.setDirty();
        for (Long l : s.lights) {
            data.owner.remove(l);
            data.unindex(l);
        }
        for (Long l : s.lights) {
            apply(level, BlockPos.of(l));
        }
    }

    /** Lumiere cassee : retiree de son interrupteur. */
    public static void lightRemoved(ServerLevel level, BlockPos pos) {
        get(level).forgetLight(pos.asLong());
    }

    void forgetLight(long l) {
        Long s = owner.remove(l);
        if (s != null) {
            Switch sw = switches.get(s);
            if (sw != null) {
                sw.lights.remove(l);
            }
            unindex(l);
            setDirty();
        }
    }

    // ------------------------------------------------------------------ liaison

    /**
     * Lie ({@code true}) ou delie une lumiere d'un interrupteur ; regles de portee et de nombre. Renvoie la cle du message (resultat) ;
     * utilise aussi par les GameTests.
     */
    public String toggleLink(ServerLevel level, BlockPos sw, BlockPos light) {
        if (sw.distSqr(light) > (double) RANGE * RANGE) {
            return "light.pioneers.link.far";
        }
        long l = light.asLong();
        Long current = owner.get(l);
        if (current != null && current == sw.asLong()) {
            forgetLight(l);
            apply(level, light);
            return "light.pioneers.link.removed";
        }
        Switch s = switches.computeIfAbsent(sw.asLong(), k -> new Switch());
        if (s.lights.size() >= MAX_LIGHTS) {
            return "light.pioneers.link.full";
        }
        boolean moved = current != null;
        if (moved) {
            forgetLight(l);
        }
        BlockState st = level.isLoaded(sw) ? level.getBlockState(sw) : null;
        if (st != null && st.getBlock() instanceof LightSwitchBlock) {
            s.on = st.getValue(LightSwitchBlock.POWERED);
        }
        s.lights.add(l);
        owner.put(l, sw.asLong());
        index(l);
        setDirty();
        apply(level, light);
        return moved ? "light.pioneers.link.moved" : "light.pioneers.link.added";
    }

    /** Accroupi + clic droit main vide sur un interrupteur : memorise (ou termine si deja memorise). */
    public static void switchClicked(ServerLevel level, ServerPlayer player, BlockPos pos) {
        Memory m = MEMORIES.get(player.getUUID());
        LightLinks data = get(level);
        if (m != null && m.dim.equals(level.dimension()) && m.pos.equals(pos) && level.getGameTime() < m.until) {
            MEMORIES.remove(player.getUUID());
            player.displayClientMessage(Component.translatable("light.pioneers.link.done", data.lightsOf(pos).size()), true);
            return;
        }
        BlockState st = level.getBlockState(pos);
        if (st.getBlock() instanceof LightSwitchBlock) {
            data.switches.computeIfAbsent(pos.asLong(), k -> new Switch()).on = st.getValue(LightSwitchBlock.POWERED);
            data.setDirty();
        }
        MEMORIES.put(player.getUUID(), new Memory(level.dimension(), pos.immutable(), level.getGameTime() + MEMORY));
        level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.4F, 1.6F);
        player.displayClientMessage(Component.translatable("light.pioneers.link.memorized", data.lightsOf(pos).size(), RANGE)
              .withStyle(ChatFormatting.GOLD), true);
        PacketLighting.send(player, pos, data);
    }

    /** Accroupi + clic droit main vide sur une lumiere : lie / delie a l'interrupteur memorise. */
    public static void lightClicked(ServerLevel level, ServerPlayer player, BlockPos pos) {
        Memory m = MEMORIES.get(player.getUUID());
        LightLinks data = get(level);
        if (m == null || level.getGameTime() >= m.until) {
            MEMORIES.remove(player.getUUID());
            BlockPos sw = data.switchOf(pos);
            player.displayClientMessage(sw == null ? Component.translatable("light.pioneers.link.no_switch")
                  : Component.translatable("light.pioneers.link.linked_to", sw.getX(), sw.getY(), sw.getZ()), true);
            if (sw != null) {
                PacketLighting.send(player, sw, data);
            }
            return;
        }
        if (!m.dim.equals(level.dimension())) {
            player.displayClientMessage(Component.translatable("light.pioneers.link.dimension").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!(level.getBlockState(m.pos).getBlock() instanceof LightSwitchBlock) && level.isLoaded(m.pos)) {
            MEMORIES.remove(player.getUUID());
            player.displayClientMessage(Component.translatable("light.pioneers.link.no_switch"), true);
            return;
        }
        String key = data.toggleLink(level, m.pos, pos);
        int n = data.lightsOf(m.pos).size();
        boolean bad = key.endsWith(".far") || key.endsWith(".full");
        Component msg = key.endsWith(".far") ? Component.translatable(key, RANGE) : key.endsWith(".full") ? Component.translatable(key, MAX_LIGHTS)
              : Component.translatable(key, n);
        player.displayClientMessage(bad ? msg.copy().withStyle(ChatFormatting.RED) : msg, true);
        if (!bad) {
            level.playSound(null, pos, SoundEvents.CHAIN_HIT, SoundSource.BLOCKS, 0.4F, key.endsWith(".removed") ? 0.8F : 1.4F);
            //la memoire repart pour 5 minutes a chaque lumiere
            MEMORIES.put(player.getUUID(), new Memory(m.dim, m.pos, level.getGameTime() + MEMORY));
        }
        PacketLighting.send(player, m.pos, data);
    }

    /** Interrupteur memorise par ce joueur dans cette dimension (encore valable), ou null : apercu des liaisons. */
    @Nullable
    public static BlockPos memorized(ServerLevel level, ServerPlayer player) {
        Memory m = MEMORIES.get(player.getUUID());
        return m != null && m.dim.equals(level.dimension()) && level.getGameTime() < m.until ? m.pos : null;
    }

    static void forget(UUID player) {
        MEMORIES.remove(player);
    }

    // ------------------------------------------------------------------ troncons decharges

    private void index(long l) {
        byChunk.computeIfAbsent(ChunkPos.asLong(BlockPos.getX(l) >> 4, BlockPos.getZ(l) >> 4), k -> new HashSet<>()).add(l);
    }

    private void unindex(long l) {
        long c = ChunkPos.asLong(BlockPos.getX(l) >> 4, BlockPos.getZ(l) >> 4);
        Set<Long> set = byChunk.get(c);
        if (set != null) {
            set.remove(l);
            if (set.isEmpty()) {
                byChunk.remove(c);
            }
        }
    }

    /** Un troncon vient de charger : ses lumieres reliees seront remises a l'etat de leur interrupteur au prochain tick. */
    void chunkLoaded(long chunk) {
        if (byChunk.containsKey(chunk) && pendingSet.add(chunk)) {
            pending.add(chunk);
        }
    }

    /** Tick de la dimension : resynchronise les lumieres des troncons charges depuis le dernier tick. */
    void tick(ServerLevel level) {
        for (int i = 0; i < CHUNKS_PER_TICK && !pending.isEmpty(); i++) {
            long c = pending.poll();
            pendingSet.remove(c);
            Set<Long> lights = byChunk.get(c);
            if (lights == null || !level.hasChunk(ChunkPos.getX(c), ChunkPos.getZ(c))) {
                continue;
            }
            for (Long l : new ArrayList<>(lights)) {
                apply(level, BlockPos.of(l));
            }
        }
    }

    // ------------------------------------------------------------------ sauvegarde

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<Long, Switch> e : switches.entrySet()) {
            Switch s = e.getValue();
            if (s.lights.isEmpty() && !s.on) {
                continue;
            }
            CompoundTag t = new CompoundTag();
            t.putLong("pos", e.getKey());
            t.putBoolean("on", s.on);
            long[] ls = new long[s.lights.size()];
            int i = 0;
            for (long l : s.lights) {
                ls[i++] = l;
            }
            t.put("lights", new LongArrayTag(ls));
            list.add(t);
        }
        tag.put("switches", list);
        return tag;
    }

    public static LightLinks load(CompoundTag tag) {
        LightLinks data = new LightLinks();
        for (Tag t0 : tag.getList("switches", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) t0;
            long pos = t.getLong("pos");
            Switch s = new Switch();
            s.on = t.getBoolean("on");
            for (long l : t.getLongArray("lights")) {
                if (!data.owner.containsKey(l)) {
                    s.lights.add(l);
                    data.owner.put(l, pos);
                    data.index(l);
                }
            }
            data.switches.put(pos, s);
        }
        return data;
    }
}
