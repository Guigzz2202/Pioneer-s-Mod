package fr.pioneers.space.research;

import fr.pioneers.core.network.PacketResearch;
import fr.pioneers.core.network.PiNetwork;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Etat de la recherche (commun au monde) : recherches terminees, recherche de laboratoire en cours, progression de chacune,
 * file d'attente, laboratoires actifs, echantillons deja fournis (geologie, biologie, asteroides) et biomes analyses (surface).
 */
public class ResearchState extends SavedData {

    private static final String NAME = "pioneers_research";
    public static final int MAX_QUEUE = 5;

    private final Set<ResearchNode> completed = EnumSet.noneOf(ResearchNode.class);
    /** 0.18.0 : jalons industriels atteints (identifiants de progres). */
    private final Set<String> milestones = new TreeSet<>();
    private final Map<ResearchNode, Integer> progress = new EnumMap<>(ResearchNode.class);
    /** Echantillons fournis, par type (4 types pour les asteroides, 1 sinon). */
    private final Map<ResearchNode, int[]> samples = new EnumMap<>(ResearchNode.class);
    /** Biomes analyses (recherches de surface). */
    private final Map<ResearchNode, Set<String>> scans = new EnumMap<>(ResearchNode.class);
    @Nullable
    private ResearchNode active;
    private final List<ResearchNode> queue = new ArrayList<>();
    /** Derniere activite de chaque laboratoire (pour afficher le nombre de laboratoires au travail). */
    private final Map<Long, Long> labHeartbeats = new HashMap<>();
    private int lastLabCount = -1;

    public static ResearchState get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(ResearchState::load, ResearchState::create, NAME);
    }

    private static ResearchState create() {
        ResearchState state = new ResearchState();
        ResearchEffects.update(state.completed);
        return state;
    }

    // ------------------------------------------------------------------
    // Lecture
    // ------------------------------------------------------------------

    public boolean isCompleted(ResearchNode node) {
        return completed.contains(node);
    }

    public Set<ResearchNode> getCompleted() {
        return completed;
    }

    @Nullable
    public ResearchNode getActive() {
        return active;
    }

    public int getProgress(ResearchNode node) {
        return progress.getOrDefault(node, 0);
    }

    public List<ResearchNode> getQueue() {
        return queue;
    }

    /** Echantillons deja fournis pour un type (scans pour la surface). */
    public int getSamples(ResearchNode node, int type) {
        if (node.kind == ResearchNode.Kind.SURFACE) {
            Set<String> set = scans.get(node);
            return set == null ? 0 : set.size();
        }
        int[] values = samples.get(node);
        return values == null || type >= values.length ? 0 : values[type];
    }

    /** Biome deja analyse pour cette recherche de surface. */
    public boolean isScanned(ResearchNode node, String biome) {
        Set<String> set = scans.get(node);
        return set != null && set.contains(biome);
    }

    /** Recherche non terminee dont tous les prerequis sont termines. */
    public boolean isAvailable(ResearchNode node) {
        return !completed.contains(node) && node.prerequisitesMet(completed::contains) && ResearchMilestones.met(node, this::hasMilestone);
    }

    // ------------------------------------------------------------------
    // 0.18.0 : jalons industriels (progres obtenus par un joueur du monde)
    // ------------------------------------------------------------------

    public boolean hasMilestone(ResourceLocation id) {
        return milestones.contains(id.toString());
    }

    /** Note un jalon atteint (progres) ; renvoie vrai s'il est nouveau. */
    public boolean addMilestone(MinecraftServer server, ResourceLocation id) {
        if (ResearchMilestones.all().contains(id) && milestones.add(id.toString())) {
            changed(server);
            return true;
        }
        return false;
    }

    /** Recherche de laboratoire qu'on peut mettre en file (prerequis termines, en cours ou deja en file). */
    private boolean isPlannable(ResearchNode node) {
        if (!node.isLab() || completed.contains(node) || node == active || queue.contains(node)) {
            return false;
        }
        return node.prerequisitesMet(prerequisite -> completed.contains(prerequisite) || prerequisite == active || queue.contains(prerequisite))
               && ResearchMilestones.met(node, this::hasMilestone);
    }

    // ------------------------------------------------------------------
    // Commandes des joueurs
    // ------------------------------------------------------------------

    public void start(MinecraftServer server, ResearchNode node) {
        if (node.isLab() && isAvailable(node)) {
            if (active != null && active != node && !queue.contains(active)) {
                //L'ancienne recherche passe en tete de file (sa progression est conservee)
                queue.add(0, active);
            }
            queue.remove(node);
            active = node;
            trimQueue();
            changed(server);
        }
    }

    public void enqueue(MinecraftServer server, ResearchNode node) {
        if (queue.size() < MAX_QUEUE && isPlannable(node)) {
            if (active == null && isAvailable(node) && node.isLab()) {
                active = node;
            } else {
                queue.add(node);
            }
            changed(server);
        }
    }

    public void dequeue(MinecraftServer server, ResearchNode node) {
        if (queue.remove(node)) {
            changed(server);
        } else if (active == node) {
            active = null;
            nextFromQueue();
            changed(server);
        }
    }

    private void trimQueue() {
        while (queue.size() > MAX_QUEUE) {
            queue.remove(queue.size() - 1);
        }
    }

    private void nextFromQueue() {
        Iterator<ResearchNode> iterator = queue.iterator();
        while (iterator.hasNext()) {
            ResearchNode next = iterator.next();
            if (completed.contains(next) || !next.isLab()) {
                iterator.remove();
            } else if (isAvailable(next)) {
                iterator.remove();
                active = next;
                return;
            }
        }
    }

    // ------------------------------------------------------------------
    // Laboratoires
    // ------------------------------------------------------------------

    public void heartbeat(MinecraftServer server, BlockPos pos, long now) {
        labHeartbeats.put(pos.asLong(), now);
        if (now % 20 == 0) {
            int count = getWorkingLabs(now);
            if (count != lastLabCount) {
                lastLabCount = count;
                broadcast(server);
            }
        }
    }

    public int getWorkingLabs(long now) {
        labHeartbeats.values().removeIf(last -> now - last > 60);
        return labHeartbeats.size();
    }

    /**
     * Une unite terminee par un laboratoire. Retourne la recherche si elle vient d'etre achevee.
     */
    @Nullable
    public ResearchNode addUnit(MinecraftServer server, ResearchNode node) {
        if (node != active || completed.contains(node)) {
            return null;
        }
        int done = getProgress(node) + 1;
        progress.put(node, done);
        if (done < node.units) {
            setDirty();
            //0.20.46 (audit) : plus une synchro complete a tous les joueurs pour chaque unite de chaque laboratoire (au plus 2 par seconde)
            broadcastSoon(server);
            return null;
        }
        complete(server, node, false);
        return node;
    }

    // ------------------------------------------------------------------
    // Echantillons (machines d'analyse) et analyses de surface
    // ------------------------------------------------------------------

    /**
     * Ajoute des echantillons a une recherche disponible. Retourne le nombre accepte (0 si la recherche est verrouillee,
     * terminee, ou si ce type est deja complet). La recherche s'acheve quand tous les types sont complets.
     */
    public int addSamples(MinecraftServer server, ResearchNode node, int type, int amount) {
        if (!node.kind.isSample() || node.kind == ResearchNode.Kind.SURFACE || type < 0 || type >= node.sampleTypes() || !isAvailable(node)) {
            return 0;
        }
        int[] values = samples.computeIfAbsent(node, n -> new int[n.sampleTypes()]);
        int accepted = Math.max(0, Math.min(amount, node.units - values[type]));
        if (accepted == 0) {
            return 0;
        }
        values[type] += accepted;
        for (int value : values) {
            if (value < node.units) {
                setDirty();
                broadcastSoon(server);
                return accepted;
            }
        }
        complete(server, node, false);
        return accepted;
    }

    /** Resultat d'une analyse de biome au scanner de surface. */
    public enum ScanResult {NEW, ALREADY, LOCKED, DONE, COMPLETED}

    public ScanResult scan(MinecraftServer server, ResearchNode node, String biome) {
        if (completed.contains(node)) {
            return ScanResult.DONE;
        } else if (!isAvailable(node)) {
            return ScanResult.LOCKED;
        }
        Set<String> set = scans.computeIfAbsent(node, n -> new HashSet<>());
        if (!set.add(biome)) {
            return ScanResult.ALREADY;
        }
        if (set.size() >= node.units) {
            complete(server, node, false);
            return ScanResult.COMPLETED;
        }
        changed(server);
        return ScanResult.NEW;
    }

    private void complete(MinecraftServer server, ResearchNode node, boolean silent) {
        if (!completed.add(node)) {
            return;
        }
        progress.remove(node);
        samples.remove(node);
        scans.remove(node);
        queue.remove(node);
        if (active == node) {
            active = null;
        }
        if (active == null) {
            nextFromQueue();
        }
        ResearchEffects.update(completed);
        changed(server);
        if (!silent) {
            announce(server, node);
        }
    }

    /** Les machines d'analyse ajoutent des echantillons a chaque tick : on limite les synchronisations. */
    private long lastSampleBroadcast;
    /** 0.20.46 (audit) : synchro retenue par {@link #broadcastSoon} (la derniere valeur part quand meme, au plus 10 ticks apres). */
    private boolean syncPending;

    private void broadcastSoon(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        if (now - lastSampleBroadcast >= 10) {
            lastSampleBroadcast = now;
            broadcast(server);
        } else {
            syncPending = true;
        }
    }

    /** 0.20.46 (audit) : envoie la synchro retenue (appele par {@link ResearchEvents} toutes les 5 ticks du serveur). */
    void flushPending(MinecraftServer server) {
        if (syncPending && server.overworld().getGameTime() - lastSampleBroadcast >= 10) {
            lastSampleBroadcast = server.overworld().getGameTime();
            broadcast(server);
        }
    }

    // ------------------------------------------------------------------
    // Commandes d'administration
    // ------------------------------------------------------------------

    /** Termine une recherche immediatement (sans annonce si silent). */
    public void forceComplete(MinecraftServer server, ResearchNode node, boolean silent) {
        complete(server, node, silent);
    }

    public void completeAll(MinecraftServer server) {
        for (ResearchNode node : ResearchNode.values()) {
            completed.add(node);
        }
        progress.clear();
        samples.clear();
        scans.clear();
        queue.clear();
        active = null;
        ResearchEffects.update(completed);
        changed(server);
    }

    public void reset(MinecraftServer server) {
        completed.clear();
        progress.clear();
        samples.clear();
        scans.clear();
        queue.clear();
        active = null;
        ResearchEffects.update(completed);
        changed(server);
    }

    private void announce(MinecraftServer server, ResearchNode node) {
        Component name = Component.translatable(node.getTranslationKey());
        Component message = (node.isDiscovery() ? Component.translatable("research.pioneers.discovered", name)
                                                 : Component.translatable("research.pioneers.completed", name))
              .withStyle(style -> style.withColor(node.isDiscovery() ? 0x55FFFF : 0xFFD27A));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(message);
            player.playNotifySound(node.isDiscovery() ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 0.7F, 1.0F);
        }
        PiNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), new PacketResearch.Completed(node.ordinal()));
    }

    private void changed(MinecraftServer server) {
        setDirty();
        broadcast(server);
    }

    // ------------------------------------------------------------------
    // Synchronisation
    // ------------------------------------------------------------------

    public PacketResearch.Sync toPacket(long now) {
        int[] progressArray = new int[ResearchNode.values().length];
        for (Map.Entry<ResearchNode, Integer> entry : progress.entrySet()) {
            progressArray[entry.getKey().ordinal()] = entry.getValue();
        }
        int[] completedArray = completed.stream().mapToInt(Enum::ordinal).toArray();
        //4 cases par recherche : echantillons de chaque type (biomes analyses pour la surface)
        int[] sampleArray = new int[ResearchNode.values().length * 4];
        for (ResearchNode node : ResearchNode.values()) {
            if (node.kind.isSample()) {
                for (int type = 0; type < node.sampleTypes(); type++) {
                    sampleArray[node.ordinal() * 4 + type] = getSamples(node, type);
                }
            }
        }
        int[] queueArray = queue.stream().mapToInt(Enum::ordinal).toArray();
        return new PacketResearch.Sync(completedArray, active == null ? -1 : active.ordinal(), progressArray, sampleArray, queueArray, getWorkingLabs(now),
              milestones.toArray(new String[0]));
    }

    public void broadcast(MinecraftServer server) {
        syncPending = false;
        PiNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), toPacket(server.overworld().getGameTime()));
    }

    public void sendTo(ServerPlayer player) {
        PiNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), toPacket(player.server.overworld().getGameTime()));
    }

    // ------------------------------------------------------------------
    // Sauvegarde
    // ------------------------------------------------------------------

    private static ResearchState load(CompoundTag tag) {
        ResearchState state = new ResearchState();
        for (int index : tag.getIntArray("completed")) {
            state.completed.add(ResearchNode.byIndex(index));
        }
        int[] progressArray = tag.getIntArray("progress");
        for (int i = 0; i < progressArray.length && i < ResearchNode.values().length; i++) {
            if (progressArray[i] > 0) {
                state.progress.put(ResearchNode.values()[i], progressArray[i]);
            }
        }
        CompoundTag sampleTag = tag.getCompound("samples");
        for (String key : sampleTag.getAllKeys()) {
            int index = Integer.parseInt(key);
            if (index >= 0 && index < ResearchNode.values().length) {
                ResearchNode node = ResearchNode.values()[index];
                int[] values = new int[node.sampleTypes()];
                int[] saved = sampleTag.getIntArray(key);
                System.arraycopy(saved, 0, values, 0, Math.min(saved.length, values.length));
                state.samples.put(node, values);
            }
        }
        CompoundTag scanTag = tag.getCompound("scans");
        for (String key : scanTag.getAllKeys()) {
            int index = Integer.parseInt(key);
            if (index >= 0 && index < ResearchNode.values().length) {
                Set<String> set = new HashSet<>();
                for (Tag biome : scanTag.getList(key, Tag.TAG_STRING)) {
                    set.add(biome.getAsString());
                }
                state.scans.put(ResearchNode.values()[index], set);
            }
        }
        state.active = tag.contains("active") ? ResearchNode.byIndex(tag.getInt("active")) : null;
        if (state.active != null && !state.active.isLab()) {
            state.active = null;
        }
        for (int index : tag.getIntArray("queue")) {
            ResearchNode node = ResearchNode.byIndex(index);
            if (node.isLab()) {
                state.queue.add(node);
            }
        }
        for (Tag id : tag.getList("milestones", Tag.TAG_STRING)) {
            state.milestones.add(id.getAsString());
        }
        ResearchEffects.update(state.completed);
        return state;
    }

    @NotNull
    @Override
    public CompoundTag save(@NotNull CompoundTag tag) {
        tag.put("completed", new IntArrayTag(completed.stream().mapToInt(Enum::ordinal).toArray()));
        int[] progressArray = new int[ResearchNode.values().length];
        for (Map.Entry<ResearchNode, Integer> entry : progress.entrySet()) {
            progressArray[entry.getKey().ordinal()] = entry.getValue();
        }
        tag.putIntArray("progress", progressArray);
        if (active != null) {
            tag.putInt("active", active.ordinal());
        }
        tag.putIntArray("queue", queue.stream().mapToInt(Enum::ordinal).toArray());
        CompoundTag sampleTag = new CompoundTag();
        for (Map.Entry<ResearchNode, int[]> entry : samples.entrySet()) {
            sampleTag.putIntArray(Integer.toString(entry.getKey().ordinal()), entry.getValue());
        }
        tag.put("samples", sampleTag);
        CompoundTag scanTag = new CompoundTag();
        for (Map.Entry<ResearchNode, Set<String>> entry : scans.entrySet()) {
            ListTag list = new ListTag();
            for (String biome : entry.getValue()) {
                list.add(StringTag.valueOf(biome));
            }
            scanTag.put(Integer.toString(entry.getKey().ordinal()), list);
        }
        tag.put("scans", scanTag);
        ListTag milestoneTag = new ListTag();
        milestones.forEach(id -> milestoneTag.add(StringTag.valueOf(id)));
        tag.put("milestones", milestoneTag);
        return tag;
    }
}
