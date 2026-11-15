package fr.pioneers.logistics.transport.logistic;

import fr.pioneers.Pioneers;
import fr.pioneers.society.clan.ClanApi;
import fr.pioneers.core.network.PacketLogistic;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * 1.11 (D2b, spec §5.1, §5.4) : sessions du diagramme cote serveur. Une session = un joueur, la dimension et le cable clique ; le reseau est
 * retrouve a chaque fois par ce cable (il peut etre reconstruit entre deux paquets).
 * <ul>
 *     <li><b>Voir</b> : {@code ClanApi.canModify(joueur, cable, INTERACT)} ; <b>modifier</b> : {@code canModify(..., MACHINE)} sur le cable clique
 *     et, pour un point, sur chaque machine reglee (repasser en auto et coller : sur toutes les machines du reseau). Creatif + op : tout.</li>
 *     <li>Toute edition sans session, d'un spectateur, dans une autre dimension ou a plus de {@link #RANGE} blocs du cable est refusee ; au plus
 *     {@link #EDITS_PER_SECOND} editions par seconde.</li>
 *     <li>Une fois par seconde : l'instantane complet si le reseau a change (empreinte : nid, revisions, raccords, cables), sinon les debits ;
 *     joueur parti trop loin ou reseau disparu : la session se ferme (l'ecran aussi).</li>
 *     <li>Une edition renvoie l'instantane au joueur et a tous ceux qui regardent le meme reseau.</li>
 * </ul>
 * Aucune donnee d'un autre reseau n'est envoyee (l'instantane ne lit que le reseau du cable clique).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DiagramService {

    public static final double RANGE = 64;
    public static final int EDITS_PER_SECOND = 20;
    /** 1.12.1 (F1) : editions gardees pendant une reconstruction du reseau, et nombre de fins de tick ou on les rejoue. */
    static final int PENDING_MAX = 8, PENDING_TRIES = 3;

    /** Session : dimension, cable clique, dernier reseau et empreinte envoyes, compteur d'editions. */
    static final class Session {

        final ResourceKey<Level> dim;
        final BlockPos cable;
        int nid;
        long version;
        long editSecond;
        int edits;
        /** 1.12.1 (F1) : editions recues pendant que le reseau etait en reconstruction (rejouees a la fin du tick, au plus {@link #PENDING_MAX}). */
        final List<CompoundTag> pending = new ArrayList<>();
        int pendingTries;

        Session(ResourceKey<Level> dim, BlockPos cable) {
            this.dim = dim;
            this.cable = cable;
        }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private DiagramService() {
    }

    // ------------------------------------------------------------------
    // Ouverture (clic droit sur un cable : LogisticCableBlock.DIAGRAM)
    // ------------------------------------------------------------------

    public static void open(ServerPlayer player, BlockPos cable, @Nullable Direction focus) {
        ServerLevel level = player.serverLevel();
        LogisticNetwork network = LogisticNetworks.network(level, cable);
        if (network == null) {
            player.displayClientMessage(Component.translatable("logistic.pioneers.status.none").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (player.isSpectator() || !ClanApi.canModify(player, level, cable, ClanApi.Action.INTERACT)) {
            player.displayClientMessage(Component.translatable("logistic.pioneers.diagram.denied_view").withStyle(ChatFormatting.RED), true);
            return;
        }
        Session session = new Session(level.dimension(), cable.immutable());
        SESSIONS.put(player.getUUID(), session);
        long focusKey = DiagramSnapshot.NO_FOCUS;
        if (focus != null && network.attach(cable.relative(focus).asLong()) != null) {
            focusKey = cable.relative(focus).asLong();
        }
        sendSnapshot(player, session, network, focusKey, true);
    }

    /** Session ouverte (GameTests). */
    public static boolean hasSession(UUID player) {
        return SESSIONS.containsKey(player);
    }

    public static void close(ServerPlayer player) {
        SESSIONS.remove(player.getUUID());
    }

    public static void forget(UUID player) {
        SESSIONS.remove(player);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SESSIONS.clear();
    }

    // ------------------------------------------------------------------
    // Instantane
    // ------------------------------------------------------------------

    /** Instantane du reseau pour un joueur (droit d'edition compris). Public pour les GameTests. */
    public static DiagramSnapshot snapshot(ServerPlayer player, BlockPos cable, LogisticNetwork network, long focus) {
        ServerLevel level = player.serverLevel();
        boolean canEdit = !player.isSpectator() && ClanApi.canModify(player, level, cable, ClanApi.Action.MACHINE);
        return DiagramSnapshot.of(network, new WorldInfo(level, network), cable.asLong(), focus, canEdit);
    }

    /** Ce que le monde dit d'un raccord : objet du bloc, facade, patron du cube. */
    private record WorldInfo(ServerLevel level, LogisticNetwork network) implements DiagramSnapshot.MachineInfo {

        @Override
        public String item(long key) {
            return machineType(level, key);
        }

        @Override
        public int facing(long key) {
            //1.0.5-beta (lot 45) : machine d'un chunk non charge : facade gardee dans l'index
            return LogisticNetworks.machineFacing(level, key);
        }

        @Override
        public int faces(long key, ChannelKind kind) {
            LogisticNetwork.Attach attach = network.attach(key);
            return attach == null ? 0 : attach.endpoint.faces(kind);
        }
    }

    /**
     * Type d'une machine : identifiant de l'objet de son bloc (sinon du bloc). 1.0.5-beta (lot 45, retour 66) : machine d'un chunk non charge :
     * type garde dans l'index (jamais de lecture du chunk).
     */
    static String machineType(ServerLevel level, long key) {
        return LogisticNetworks.machineItem(level, key);
    }

    private static long version(LogisticNetwork network, boolean canEdit) {
        long states = 0;
        for (int uid : network.channelUids()) {
            LogisticNetwork.State s = network.state(uid);
            states = states * 31 + uid * 7L + (s == null ? 0 : s.ordinal());
        }
        //1.0.5-beta (lot 45) : machines en pause (chunk non charge) et deconnectees : l'instantane suit
        return Objects.hash(network.nid, network.config().maxRev(), network.isAuto(), network.attaches().size(), network.graph.size, canEdit,
              states, network.config().layout.size(), network.pausedCount(), network.detachedCount());
    }

    private static void sendSnapshot(ServerPlayer player, Session session, LogisticNetwork network, long focus, boolean open) {
        DiagramSnapshot snap = snapshot(player, session.cable, network, focus);
        CompoundTag tag = snap.save();
        tag.putBoolean("open", open);
        session.nid = network.nid;
        session.version = version(network, snap.canEdit);
        PacketLogistic.send(player, PacketLogistic.Snapshot.of(tag));
    }

    private static void sendGone(ServerPlayer player) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("gone", true);
        PacketLogistic.send(player, PacketLogistic.Snapshot.of(tag));
    }

    // ------------------------------------------------------------------
    // Edition
    // ------------------------------------------------------------------

    /** Session valide pour ce joueur (meme dimension, a portee, cable charge), sinon null. */
    @Nullable
    private static Session valid(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || player.isSpectator() || player.level().dimension() != session.dim || !player.level().isLoaded(session.cable)) {
            return null;
        }
        if (player.distanceToSqr(session.cable.getX() + 0.5, session.cable.getY() + 0.5, session.cable.getZ() + 0.5) > RANGE * RANGE) {
            return null;
        }
        return session;
    }

    /** Applique une edition recue du client (paquet {@code Edit}) ; rend le resultat (GameTests). */
    public static DiagramEdits.Result edit(ServerPlayer player, CompoundTag op) {
        Session session = valid(player);
        if (session == null) {
            return DiagramEdits.Result.INVALID;
        }
        long second = player.level().getGameTime() / 20;
        if (session.editSecond != second) {
            session.editSecond = second;
            session.edits = 0;
        }
        if (++session.edits > EDITS_PER_SECOND) {
            return DiagramEdits.Result.INVALID;
        }
        ServerLevel level = player.serverLevel();
        LogisticNetwork network = LogisticNetworks.network(level, session.cable);
        if (network == null) {
            //1.12.1 (F1) : une coupure, une fiche ou une machine voisine qui change dissout le reseau tout de suite, il n'est reconstruit qu'en
            //fin de tick : l'edition qui arrive entre les deux etait perdue (et l'ecran se fermait). Le cable est encore la : on la garde et
            //on la rejoue apres la reconstruction.
            if (level.getBlockState(session.cable).getBlock() instanceof LogisticCableBlock && session.pending.size() < PENDING_MAX) {
                session.pending.add(op.copy());
                return DiagramEdits.Result.INVALID;
            }
            SESSIONS.remove(player.getUUID());
            sendGone(player);
            return DiagramEdits.Result.INVALID;
        }
        if (!ClanApi.canModify(player, level, session.cable, ClanApi.Action.MACHINE)) {
            player.displayClientMessage(Component.translatable("logistic.pioneers.diagram.denied_edit").withStyle(ChatFormatting.RED), true);
            return DiagramEdits.Result.DENIED;
        }
        DiagramEdits.Outcome outcome = DiagramEdits.apply(network, op, new DiagramEdits.Context() {
            @Override
            public boolean canEditPoint(long key) {
                return ClanApi.canModify(player, level, BlockPos.of(key), ClanApi.Action.MACHINE);
            }

            @Override
            public String machineType(long key) {
                return DiagramService.machineType(level, key);
            }
        });
        if (outcome.result() == DiagramEdits.Result.DENIED) {
            player.displayClientMessage(Component.translatable("logistic.pioneers.diagram.denied_machine").withStyle(ChatFormatting.RED), true);
        }
        if (outcome.result() == DiagramEdits.Result.INVALID) {
            //1.12.1 (F1) : refus explique et ecran remis a l'etat du serveur (avant : rien ne se passait, l'ecran gardait son brouillon)
            player.displayClientMessage(Component.translatable("logistic.pioneers.diagram.refused").withStyle(ChatFormatting.GOLD), true);
            sendSnapshot(player, session, network, DiagramSnapshot.NO_FOCUS, false);
        } else {
            LogisticNetworks.pushConfig(level, network);
            //Instantane au joueur (avec l'uid touche : l'ecran le selectionne) et a tous ceux qui regardent le meme reseau
            for (Map.Entry<UUID, Session> e : new ArrayList<>(SESSIONS.entrySet())) {
                ServerPlayer other = level.getServer().getPlayerList().getPlayer(e.getKey());
                if (other == null || e.getValue().dim != session.dim || other.level() != level) {
                    continue;
                }
                LogisticNetwork theirs = LogisticNetworks.network(level, e.getValue().cable);
                if (theirs != network) {
                    continue;
                }
                DiagramSnapshot snap = snapshot(other, e.getValue().cable, network, DiagramSnapshot.NO_FOCUS);
                CompoundTag tag = snap.save();
                if (other == player) {
                    tag.putInt("selUid", outcome.uid());
                    tag.putString("lastOp", op.getString("op"));
                }
                e.getValue().nid = network.nid;
                e.getValue().version = version(network, snap.canEdit);
                PacketLogistic.send(other, PacketLogistic.Snapshot.of(tag));
            }
        }
        return outcome.result();
    }

    // ------------------------------------------------------------------
    // Rafraichissement (une fois par seconde)
    // ------------------------------------------------------------------

    /** 1.12.1 (F1) : rejoue les editions recues pendant une reconstruction (les mondes ont tique : le reseau est reconstruit). */
    private static void replayPending(MinecraftServer server) {
        for (Map.Entry<UUID, Session> e : new ArrayList<>(SESSIONS.entrySet())) {
            Session session = e.getValue();
            if (session.pending.isEmpty()) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            List<CompoundTag> ops = new ArrayList<>(session.pending);
            session.pending.clear();
            if (player == null) {
                continue;
            }
            if (++session.pendingTries > PENDING_TRIES) {
                //Toujours pas de reseau : il n'existe plus (plus aucune fiche)
                SESSIONS.remove(e.getKey());
                sendGone(player);
                continue;
            }
            session.edits = 0;
            for (CompoundTag op : ops) {
                edit(player, op);
            }
            if (session.pending.isEmpty()) {
                session.pendingTries = 0;
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || SESSIONS.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        replayPending(server);
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        List<UUID> drop = new ArrayList<>();
        for (Map.Entry<UUID, Session> e : SESSIONS.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            Session session = e.getValue();
            if (player == null) {
                drop.add(e.getKey());
                continue;
            }
            LogisticNetwork network = valid(player) == null ? null : LogisticNetworks.network(player.serverLevel(), session.cable);
            if (network == null) {
                drop.add(e.getKey());
                sendGone(player);
                continue;
            }
            boolean canEdit = ClanApi.canModify(player, player.serverLevel(), session.cable, ClanApi.Action.MACHINE);
            if (network.nid != session.nid || version(network, canEdit) != session.version) {
                sendSnapshot(player, session, network, DiagramSnapshot.NO_FOCUS, false);
            } else {
                PacketLogistic.send(player, new PacketLogistic.Stats(DiagramSnapshot.stats(network, network::body)));
            }
        }
        drop.forEach(SESSIONS::remove);
    }
}
