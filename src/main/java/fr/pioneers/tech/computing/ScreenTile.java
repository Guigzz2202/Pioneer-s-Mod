package fr.pioneers.tech.computing;

import fr.pioneers.core.block.PiBlockEntity;
import fr.pioneers.core.data.NetworkPlug;
import fr.pioneers.core.network.PacketTileAction;
import fr.pioneers.tech.computing.PiComputing;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Ecran de supervision (0.17.0). Seul le bloc maitre du mur (en bas a gauche) travaille : il lit la machine liee chaque seconde, garde les
 * 60 dernieres valeurs de la grandeur choisie pour le graphe et envoie le tout au client (rendu {@code RenderSupervisionScreen}).
 * Actions : {@link #UNLINK}, {@link #GRAPH_NEXT}, {@link #GRAPH_PREV}.
 */
public class ScreenTile extends PiBlockEntity implements MenuProvider, PacketTileAction.Handler {

    public static final int UNLINK = 100, GRAPH_NEXT = 101, GRAPH_PREV = 102, HISTORY = 60;

    @Nullable
    private BlockPos link;
    private String graphKey = "";
    private final double[] history = new double[HISTORY];
    private int samples;
    private List<ProbeValue> values = List.of();
    private Component name = Component.empty();
    private int clock;
    /** 0.17.1 : texte ecrit par un ordinateur lie (mode texte ; vide = affichage de la machine liee). */
    private List<String> text = List.of();
    /** 0.20.46 (audit) : ce que le client a recu en dernier (serveur), pour n'envoyer que ce qui change. */
    @Nullable
    private List<ProbeValue> sentValues;
    @Nullable
    private Component sentName;
    @Nullable
    private double[] sentHistory;
    /** Client : augmente a chaque etat recu (le rendu garde ses textes mis en page tant qu'il ne bouge pas). */
    private int clientVersion;
    /** 1.0.4-beta (lot 26) : ecran rectangulaire de ce bloc (decoupage de l'assemblage) et instant du calcul. */
    @Nullable
    private ScreenWall.Rect wall;
    private long wallAt = Long.MIN_VALUE / 2;
    private boolean healed;
    static final int WALL_TTL = 10;

    public ScreenTile(BlockPos pos, BlockState state) {
        super(PiComputing.SCREEN_TILE.get(), pos, state);
    }

    public Direction facing() {
        return getBlockState().getBlock() instanceof ScreenBlock ? getBlockState().getValue(ScreenBlock.FACING) : Direction.NORTH;
    }

    public boolean isMaster() {
        if (level == null) {
            return false;
        }
        ScreenWall.Rect r = wall();
        return r != null && r.master().equals(worldPosition);
    }

    /** Taille de l'ecran (largeur, hauteur) dont ce bloc fait partie (1.0.4-beta lot 26 : son rectangle, {@link ScreenWall}). */
    public int[] size() {
        ScreenWall.Rect r = level == null ? null : wall();
        return r == null ? new int[]{1, 1} : new int[]{r.w(), r.h()};
    }

    /**
     * 1.0.4-beta (lot 26) : ecran rectangulaire de ce bloc ({@link ScreenWall}) ; le decoupage de tout l'assemblage est calcule une fois et
     * range dans chaque dalle, refait au plus toutes les {@link #WALL_TTL} ticks ou quand l'etat de ce bloc change (appele a chaque image
     * par le rendu).
     */
    @Nullable
    public ScreenWall.Rect wall() {
        long now = level.getGameTime();
        if (wall == null || now - wallAt >= WALL_TTL || now < wallAt) {
            Direction f = facing();
            for (Map.Entry<BlockPos, ScreenWall.Rect> e : ScreenWall.partition(level, worldPosition, f, null).entrySet()) {
                if (level.getBlockEntity(e.getKey()) instanceof ScreenTile t) {
                    t.wall = e.getValue();
                    t.wallAt = now;
                }
            }
            if (wallAt != now) {
                wall = null;
                wallAt = now;
            }
        }
        return wall;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void setBlockState(@NotNull BlockState state) {
        super.setBlockState(state);
        wallAt = Long.MIN_VALUE / 2;
    }

    @Nullable
    public BlockPos link() {
        return link;
    }

    public String graphKey() {
        return graphKey;
    }

    public List<ProbeValue> values() {
        return values;
    }

    public Component name() {
        return name;
    }

    /** Valeurs du graphe, de la plus ancienne a la plus recente. */
    public double[] history() {
        int n = Math.min(samples, HISTORY);
        double[] out = new double[n];
        for (int i = 0; i < n; i++) {
            out[i] = history[(samples - n + i) % HISTORY];
        }
        return out;
    }

    /** Texte du mode terminal (0.17.1). */
    public List<String> text() {
        return text;
    }

    /** Un ordinateur ecrit sur l'ecran (lignes deja coupees) ; liste vide : retour a l'affichage de la machine liee. */
    public void showText(List<String> lines) {
        if (!lines.equals(text)) {
            text = List.copyOf(lines);
            sync();
        }
    }

    public boolean setLink(BlockPos target) {
        if (target.equals(worldPosition) || !LogicTile.reachable(level, worldPosition, target)) {
            return false;
        }
        link = target.immutable();
        graphKey = "";
        samples = 0;
        sample();
        sync();
        return true;
    }

    public void tickServer() {
        if (!healed && level != null) {
            //1.0.4-beta (lot 26) : raccords d'un monde d'avant (toute dalle voisine) recalcules en ecrans rectangulaires
            healed = true;
            ScreenBlock.refresh(level, worldPosition, facing());
        }
        if (++clock < 20) {
            return;
        }
        clock = 0;
        if (isMaster()) {
            sample();
        }
    }

    private void sample() {
        String graphBefore = graphKey;
        if (link == null) {
            values = List.of();
            name = Component.empty();
        } else {
            BlockEntity tile = Probes.at(level, link);
            values = Probes.read(tile);
            name = Probes.name(tile);
            if (graphKey.isEmpty() || ProbeValue.find(values, graphKey) == null) {
                ProbeValue first = values.stream().filter(v -> v.enumPrefix().isEmpty()).findFirst().orElse(null);
                graphKey = first == null ? "" : first.key();
            }
            ProbeValue v = ProbeValue.find(values, graphKey);
            if (v != null) {
                history[samples % HISTORY] = v.value();
                samples++;
            }
        }
        if (!graphKey.equals(graphBefore)) {
            //Seuls le lien, la grandeur du graphe et le texte sont sauvegardes
            sync();
            return;
        }
        //0.20.46 (audit) : renvoi seulement si ce que l'ecran affiche a change (valeurs, nom, graphe tel qu'il est dessine) ; un ecran
        //au repos ou sans lien n'envoie plus rien (avant : tout l'etat chaque seconde)
        double[] shown = history();
        if (!values.equals(sentValues) || !name.equals(sentName) || !Arrays.equals(shown, sentHistory)) {
            send(shown);
        }
    }

    private void sync() {
        setChanged();
        send(history());
    }

    private void send(double[] shown) {
        sentValues = values;
        sentName = name;
        sentHistory = shown;
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Client : numero de l'etat recu (change a chaque mise a jour). */
    public int clientVersion() {
        return clientVersion;
    }

    @Override
    public void handleAction(ServerPlayer player, int action) {
        if (action == UNLINK) {
            text = List.of();
            link = null;
            graphKey = "";
            samples = 0;
        } else if (action == GRAPH_NEXT || action == GRAPH_PREV) {
            List<String> keys = new ArrayList<>();
            values.stream().filter(v -> v.enumPrefix().isEmpty()).forEach(v -> keys.add(v.key()));
            if (!keys.isEmpty()) {
                int i = keys.indexOf(graphKey);
                graphKey = keys.get(Math.floorMod(i + (action == GRAPH_NEXT ? 1 : -1), keys.size()));
                samples = 0;
            }
        }
        sample();
        //Lien, texte ou grandeur du graphe changes : sauvegarde et renvoi au client
        sync();
    }

    // ------------------------------------------------------------------
    // Interface
    // ------------------------------------------------------------------

    @NotNull
    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int id, @NotNull Inventory inventory, @NotNull Player player) {
        return new ComputingMenu(id, inventory, this);
    }

    // ------------------------------------------------------------------
    // Sauvegarde et synchronisation
    // ------------------------------------------------------------------

    private void write(CompoundTag tag, boolean client) {
        if (link != null) {
            tag.put("link", NbtUtils.writeBlockPos(link));
        }
        tag.putString("graph", graphKey);
        if (!text.isEmpty()) {
            ListTag t = new ListTag();
            text.forEach(line -> t.add(StringTag.valueOf(line)));
            tag.put("text", t);
        }
        if (client) {
            tag.putLongArray("hist", Arrays.stream(history()).mapToLong(Double::doubleToLongBits).toArray());
            tag.put("values", ProbeValue.saveAll(values));
            tag.putString("name", Component.Serializer.toJson(name));
        }
    }

    private void read(CompoundTag tag, boolean client) {
        link = tag.contains("link") ? NbtUtils.readBlockPos(tag.getCompound("link")) : null;
        graphKey = tag.getString("graph");
        ListTag t = tag.getList("text", Tag.TAG_STRING);
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < t.size(); i++) {
            lines.add(t.getString(i));
        }
        text = List.copyOf(lines);
        if (client) {
            clientVersion++;
            long[] h = tag.getLongArray("hist");
            samples = h.length;
            for (int i = 0; i < h.length && i < HISTORY; i++) {
                history[i] = Double.longBitsToDouble(h[i]);
            }
            values = ProbeValue.loadAll(tag, "values");
            Component n = Component.Serializer.fromJson(tag.getString("name"));
            name = n == null ? Component.empty() : n;
        }
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        write(tag, false);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        read(tag, false);
    }

    @NotNull
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        write(tag, true);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        read(tag, true);
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) {
        if (packet.getTag() != null) {
            read(packet.getTag(), true);
        }
    }

    /** Le maitre dessine tout son ecran : boite de rendu elargie (0.20.46 (audit) : les autres blocs, qui ne dessinent rien, gardent la leur). */
    @Override
    public AABB getRenderBoundingBox() {
        return level != null && isMaster() ? new AABB(worldPosition).inflate(Math.max(ScreenBlock.MAX_W, ScreenBlock.MAX_H)) : new AABB(worldPosition);
    }

    /** 0.20.46 : la fibre optique se branche sur ce bloc (il fait partie du reseau numerique ; rien n'y entre). */
    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap,
          @Nullable Direction side) {
        LazyOptional<T> plug = NetworkPlug.of(cap);
        return plug != null ? plug : super.getCapability(cap, side);
    }
}
