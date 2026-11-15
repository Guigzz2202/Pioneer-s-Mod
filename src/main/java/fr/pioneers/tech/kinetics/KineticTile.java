package fr.pioneers.tech.kinetics;

import fr.pioneers.core.Directions;
import fr.pioneers.core.RenderClock;
import fr.pioneers.core.block.PiBlockEntity;
import fr.pioneers.farm.FarmCard;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base des blocs-entites du reseau de force rotative : inscription au reseau, liens par bouts d'arbre, contraintes (avertissement puis casse),
 * synchronisation de la vitesse vers le client (seulement quand elle change nettement : aucun paquet en regime etabli) et angle d'affichage
 * integre cote client, en phase avec l'heure serveur estimee ({@link RenderClock} ; deux engrenages charges a des moments differents restent
 * engrenes).
 */
public abstract class KineticTile extends PiBlockEntity implements KineticNode {

    /** Degres par tick pour 1 tr/min. */
    public static final double DEG_PER_TICK = 360.0 / 60.0 / 20.0;
    private static final int BREAK_TICKS = 40;

    @Nullable
    protected KineticNetwork network;
    /** Vitesse du noeud (tr/min, son repere), couple transmis (N.m). */
    protected double speed, torque;
    protected boolean jammed;
    private int overload;
    /**
     * 0.20.42 : phase (degres) tenue par le reseau ; etat envoye au client (vitesse, phase, instant) : le client extrapole l'angle depuis cet
     * instant. Toutes les pieces d'un reseau envoient au meme tick, et un renvoi isole (chargement du troncon, objet affiche) repete l'etat
     * deja envoye : les voisins extrapolent donc toujours depuis le meme instant et restent alignes.
     */
    private double phase = Double.NaN;
    private boolean syncRequested;
    private double sentSpeed = Double.NaN, sentPhase;
    private boolean sentJam;
    private long sentTime, sentTick;
    // Client : etat recu (1.0.5-beta lot 29 : reference + raccord sans saut, voir KineticAngle ; heure = RenderClock, plus l'heure du monde)
    private final KineticAngle view = new KineticAngle();

    protected KineticTile(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // ------------------------------------------------------------------
    // Reseau
    // ------------------------------------------------------------------

    @Override
    public BlockPos nodePos() {
        return worldPosition;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            KineticNetworks.invalidate(level, worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide) {
            KineticNetworks.remove(level, worldPosition);
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null && !level.isClientSide) {
            KineticNetworks.remove(level, worldPosition);
        }
    }

    /** Liens ou orientation modifies : reconstruire le reseau. */
    public void invalidateNetwork() {
        if (level != null && !level.isClientSide) {
            KineticNetworks.invalidate(level, worldPosition);
        }
    }

    // 0.20.46 (audit) : empreinte des liens a la derniere construction du reseau
    private long builtLinks;
    private boolean linksKnown;

    @Override
    public void linksBuilt(long signature) {
        builtLinks = signature;
        linksKnown = true;
    }

    /**
     * 0.20.46 (audit) : vrai si les liens de la piece ne sont plus ceux de la construction de son reseau (ou si elle n'est dans aucun reseau
     * construit). Un voisin qui change sans toucher aux liens (redstone, LIT d'une chaudiere, ACTIVE d'une machine, signal du regulateur, eau
     * posee par la noria...) ne fait plus reconstruire tout le reseau. Les liens etant reciproques, tout lien qui apparait, disparait ou change
     * de rapport change l'empreinte d'au moins une des deux pieces.
     */
    public boolean linksChanged() {
        if (level == null || !linksKnown || KineticNetworks.network(level, worldPosition) == null) {
            return true;
        }
        long[] h = {KineticNode.LINKS_SEED};
        collectLinks((other, r) -> h[0] = KineticNode.linkHash(h[0], other, r));
        return h[0] != builtLinks;
    }

    @Override
    public void collectLinks(LinkSink sink) {
        shaftLinks(sink);
    }

    /** Liens par les bouts d'arbre : un voisin qui presente aussi un bout d'arbre sur la face commune. */
    protected final void shaftLinks(LinkSink sink) {
        if (level == null) {
            return;
        }
        for (Direction face : Directions.ALL) {
            double mine = shaftFactor(face);
            if (mine == 0) {
                continue;
            }
            BlockPos other = worldPosition.relative(face);
            if (level.isLoaded(other) && level.getBlockEntity(other) instanceof KineticNode node) {
                double theirs = node.shaftFactor(face.getOpposite());
                if (theirs != 0) {
                    sink.link(other, mine / theirs);
                }
            }
        }
    }

    @Override
    public void leaveNetwork() {
        network = null;
    }

    @Override
    public double lastSpeed() {
        return speed;
    }

    @Override
    public double lastPhase() {
        return phase;
    }

    @Override
    public void networkPhase(double phase, boolean sync) {
        this.phase = phase;
        syncRequested |= sync;
    }

    public double getSpeed() {
        return speed;
    }

    /** Materiau de la piece (limites) ; null : limites propres ({@link #maxTorque()}). */
    @Nullable
    public KineticMaterial material() {
        return null;
    }

    @Override
    public double maxTorque() {
        KineticMaterial material = material();
        return material == null ? 1e9 : material.maxTorque;
    }

    @Override
    public double maxSpeed() {
        KineticMaterial material = material();
        return material == null ? 1e9 : material.maxSpeed;
    }

    @Override
    public double friction() {
        KineticMaterial material = material();
        return material == null ? 0.01 : material.friction;
    }

    @Override
    public void onNetworkTick(KineticNetwork network, double speed, double torque, boolean jammed) {
        this.network = network;
        this.speed = speed;
        this.torque = torque;
        this.jammed = jammed;
        if (level instanceof ServerLevel server) {
            stress(server);
            if (isRemoved()) {
                return;                                          //Piece cassee par la surcharge
            }
            serverTick(server);
            sync(server);
        }
    }

    /** Travail du noeud a chaque tick (machines, sources) ; appele par le reseau. */
    protected void serverTick(ServerLevel level) {
    }

    // ------------------------------------------------------------------
    // Contraintes
    // ------------------------------------------------------------------

    private void stress(ServerLevel level) {
        double limit = maxTorque(), vmax = maxSpeed();
        boolean warn = torque > limit * 0.9 || Math.abs(speed) > vmax;
        boolean over = torque > limit || Math.abs(speed) > vmax * 1.5;
        if ((warn || jammed) && level.getGameTime() % 12 == (worldPosition.asLong() & 7)) {
            level.sendParticles(jammed ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE, worldPosition.getX() + 0.5, worldPosition.getY() + 0.7,
                  worldPosition.getZ() + 0.5, 2, 0.25, 0.15, 0.25, 0.01);
            if (warn) {
                level.playSound(null, worldPosition, SoundEvents.WOOD_STEP, SoundSource.BLOCKS, 0.4F, 0.5F + level.random.nextFloat() * 0.2F);
            }
        }
        overload = over ? overload + 1 : Math.max(0, overload - 2);
        if (overload > BREAK_TICKS && KineticsConfig.breakage() == KineticsConfig.Breakage.ON) {
            breakPart(level);
        }
    }

    /** Objets laisses par la piece quand elle casse sous la contrainte. */
    protected List<ItemStack> brokenDrops() {
        return List.of();
    }

    protected void breakPart(ServerLevel level) {
        BlockState state = getBlockState();
        for (ItemStack stack : brokenDrops()) {
            Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, stack.copy());
        }
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
              worldPosition.getZ() + 0.5, 30, 0.3, 0.3, 0.3, 0.1);
        level.playSound(null, worldPosition, SoundEvents.ITEM_BREAK, SoundSource.BLOCKS, 1.0F, 0.6F);
        level.playSound(null, worldPosition, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.BLOCKS, 0.6F, 1.2F);
        KineticEvents.broken(level, worldPosition);
        level.removeBlock(worldPosition, false);
    }

    // ------------------------------------------------------------------
    // Synchronisation et affichage
    // ------------------------------------------------------------------

    private void sync(ServerLevel level) {
        long now = level.getGameTime();
        if (syncRequested || Double.isNaN(sentSpeed)) {
            boolean byNetwork = syncRequested;
            syncRequested = false;
            sentSpeed = speed;
            sentPhase = Double.isFinite(phase) ? phase : 0;
            sentTime = now;
            sentJam = jammed;
            sentTick = now;
            //0.20.46 (audit) : la rotation part dans le paquet du reseau (KineticSync, un par joueur ; chunks marques a sauver) ; le paquet du
            //bloc seulement pour une piece qui a des donnees propres (avant : un paquet et un setChanged par piece a chaque synchronisation)
            if (!byNetwork) {
                level.blockEntityChanged(worldPosition);         //Envoi isole (hors reseau) : comme avant
                sendClientUpdate();
            } else if (hasOwnClientData()) {
                sendClientUpdate();
            }
        } else if (now - sentTick >= 4 && clientDirty()) {
            sentTick = now;
            //Meme etat de rotation : seules les donnees propres changent (sauvegarde du chunk marquee, sans prevenir les comparateurs)
            level.blockEntityChanged(worldPosition);
            sendClientUpdate();
        }
    }

    /** 0.20.46 (audit) : envoie l'etat au client (paquet du bloc), sans marquer le bloc a sauvegarder. */
    private void sendClientUpdate() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** 0.20.46 (audit) : classes qui ont des donnees propres pour le client ({@link #writeClient} redefini). */
    private static final ClassValue<Boolean> OWN_CLIENT_DATA = new ClassValue<>() {
        @Override
        protected Boolean computeValue(@NotNull Class<?> type) {
            for (Class<?> c = type; c != null && c != KineticTile.class; c = c.getSuperclass()) {
                try {
                    c.getDeclaredMethod("writeClient", CompoundTag.class);
                    return true;
                } catch (NoSuchMethodException ignored) {
                }
            }
            return false;
        }
    };

    /**
     * 0.20.46 (audit) : vrai si la piece envoie des donnees propres au client ({@link #writeClient}) : elle garde le paquet du bloc a chaque
     * synchronisation de la rotation (bras du manege recales, avancement et objets des machines...), comme avant.
     */
    protected boolean hasOwnClientData() {
        return OWN_CLIENT_DATA.get(getClass());
    }

    // 0.20.46 (audit) : etat envoye (reseau : reprise a la reconstruction, paquet de synchronisation)

    /** 1.0.5-beta (lot 29) : instant serveur du dernier etat de rotation envoye aux clients (GameTests : aucun envoi en regime stable). */
    public long syncedAt() {
        return sentTime;
    }

    boolean hasSentState() {
        return !Double.isNaN(sentSpeed);
    }

    double sentSpeedValue() {
        return sentSpeed;
    }

    double sentPhaseValue() {
        return sentPhase;
    }

    long sentTimeValue() {
        return sentTime;
    }

    boolean sentJamValue() {
        return sentJam;
    }

    /** 0.20.46 (audit) : etat de rotation recu du paquet du reseau (client) : comme {@link #handleUpdateTag}, sans les donnees propres. */
    public void receiveRotation(double speed, double phase, long time, boolean jammed) {
        double previous = this.speed;
        this.speed = speed;
        receiveView(speed, phase, time);
        this.jammed = jammed;
        rotationReceived(previous);
    }

    /** 1.0.5-beta (lot 29) : nouvelle reference de l'angle affiche, raccordee sans saut a l'angle de la derniere image. */
    private void receiveView(double speed, double phase, long time) {
        view.receive(speed, phase, time, RenderClock.now(), RenderClock.started());
    }

    /**
     * 0.20.46 (audit) : client : la vitesse vient de changer par le paquet du reseau ; les donnees propres (paquet du bloc) suivent au tick
     * suivant. Une piece qui extrapole un etat propre avec la vitesse (bras du manege) s'y recale ici pour rester continue.
     */
    protected void rotationReceived(double previousSpeed) {
    }

    /** Donnees propres au client changees (etat d'une source, objet affiche...). */
    protected boolean clientDirty() {
        return false;
    }

    /** Envoie l'etat au client et marque le bloc a sauvegarder. */
    public void markClientsDirty() {
        if (level != null && !level.isClientSide) {
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /**
     * Angle d'affichage (degres) au moment {@code partial} du tick : phase envoyee par le serveur, extrapolee a la vitesse envoyee depuis
     * l'instant d'envoi, a l'heure serveur ESTIMEE par {@link RenderClock} (1.0.5-beta lot 29 : avant, l'heure du monde du client, remise a
     * l'heure du serveur chaque seconde : sur un serveur a moins de 20 ticks / s, toutes les pieces reculaient d'un coup). La meme horloge
     * pour toutes les pieces : elles restent en phase entre elles ; un nouvel etat se raccorde sans saut ({@link KineticAngle}).
     */
    public float angle(float partial) {
        if (level == null) {
            return 0;
        }
        return (float) (view.angle(RenderClock.time(partial)) % 360.0);
    }

    /** 1.0.5-beta (lot 29) : angle affiche a une heure serveur estimee donnee (bras du manege : meme horloge). */
    protected double viewAngle(double serverTime) {
        return view.angle(serverTime);
    }

    /** Donnees du client propres au bloc. */
    protected void writeClient(CompoundTag tag) {
    }

    protected void readClient(CompoundTag tag) {
    }

    @NotNull
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        boolean sent = !Double.isNaN(sentSpeed);
        tag.putDouble("s", sent ? sentSpeed : speed);
        tag.putDouble("p", sent ? sentPhase : Double.isFinite(phase) ? phase : 0);
        tag.putLong("pt", sent ? sentTime : level == null ? 0 : level.getGameTime());
        tag.putBoolean("j", sent ? sentJam : jammed);
        writeClient(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        speed = tag.getDouble("s");
        receiveView(speed, tag.getDouble("p"), tag.getLong("pt"));
        jammed = tag.getBoolean("j");
        readClient(tag);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) {
        CompoundTag tag = packet.getTag();
        if (tag != null) {
            handleUpdateTag(tag);
        }
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putDouble("speed", speed);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        if (level == null || !level.isClientSide) {
            speed = tag.getDouble("speed");
        }
    }

    /** Boite de rendu (pieces qui debordent du bloc : roues, ailes). */
    protected double renderRadius() {
        return 0;
    }

    /** 0.20.46 (audit) : debord de la piece hors de son bloc (blocs) ; > 0 : rendue hors de sa section (voir {@code KineticRenderer}). */
    public double renderReach() {
        return renderRadius();
    }

    // 0.20.46 (audit) : boite de rendu gardee tant que le rayon ne change pas (demandee a chaque image)
    @Nullable
    private AABB renderBox;
    private double renderBoxRadius;

    @Override
    public AABB getRenderBoundingBox() {
        double r = renderRadius();
        AABB box = renderBox;
        if (box == null || r != renderBoxRadius) {
            box = new AABB(worldPosition);
            box = r <= 0 ? box : box.inflate(r);
            renderBox = box;
            renderBoxRadius = r;
        }
        return box;
    }

    // ------------------------------------------------------------------
    // Fiche du reseau (cle + Maj)
    // ------------------------------------------------------------------

    public void describe(List<Component> lines) {
        KineticNetwork net = network;
        lines.add(Component.translatable("kinetics.pioneers.info.speed", fmt(speed)).withStyle(ChatFormatting.AQUA));
        lines.add(Component.translatable("kinetics.pioneers.info.torque", fmt(torque), fmt(maxTorque())).withStyle(
              torque > maxTorque() * 0.9 ? ChatFormatting.RED : ChatFormatting.GRAY));
        if (net != null) {
            lines.add(Component.translatable("kinetics.pioneers.info.network", net.size(), net.sources(), net.machines()).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("kinetics.pioneers.info.power", watts(net.power()), watts(net.capacity())).withStyle(ChatFormatting.GOLD));
            lines.add(Component.translatable("kinetics.pioneers.info.load", Math.round(net.loadRatio() * 100)).withStyle(
                  net.loadRatio() > 1 ? ChatFormatting.RED : ChatFormatting.GRAY));
        }
        if (jammed) {
            lines.add(Component.translatable("kinetics.pioneers.info.jammed").withStyle(ChatFormatting.RED));
        }
    }

    /**
     * 1.4 : fiche au regard (serveur, {@code common/look/LookCards}) : vitesse et couple en jauges, charge du reseau, puis les lignes propres
     * a la piece ({@link #describe} des sous-classes, apres les lignes communes) et une aide ({@link #lookHint}).
     * <p>
     * 1.6 (fiches synthetiques) : debout = vitesse, couple, charge et la premiere ligne propre a la piece (ou le blocage) ; accroupi =
     * puissance, reseau et toutes les lignes propres.
     */
    public FarmCard lookCard(boolean sneaking) {
        List<FarmCard.Line> lines = new ArrayList<>();
        KineticNetwork net = network;
        List<Component> all = new ArrayList<>();
        describe(all);
        int base = 2 + (net != null ? 3 : 0) + (jammed ? 1 : 0);
        List<Component> own = all.size() > base ? all.subList(base, all.size()) : List.of();
        if (!sneaking) {
            double vmax = maxSpeed(), v = Math.abs(speed);
            lines.add(FarmCard.Line.gauge(FarmCard.label("speed"), (float) (v / vmax), Component.translatable("card.pioneers.rpm", fmt(v)),
                  jammed || v > vmax ? 0xF05050 : 0x5AC8E6));
            double tmax = maxTorque(), r = torque / tmax;
            lines.add(FarmCard.Line.gauge(FarmCard.label("torque"), (float) r, Component.translatable("card.pioneers.nm", fmt(torque), fmt(tmax)),
                  r > 0.9 ? 0xF05050 : r > 0.7 ? 0xF0B040 : 0x6EDC5A));
            if (net != null) {
                double load = net.loadRatio();
                lines.add(FarmCard.Line.gauge(FarmCard.label("load"), (float) load, Component.translatable("card.pioneers.percent", Math.round(load * 100)),
                      load > 1 ? 0xF05050 : load > 0.85 ? 0xF0B040 : 0x6EDC5A));
            } else {
                lines.add(FarmCard.Line.pair(FarmCard.label("network"), FarmCard.word("none"), FarmCard.DIM));
            }
            if (jammed) {
                lines.add(FarmCard.Line.text(FarmCard.word("jammed"), FarmCard.BAD));
            } else if (!own.isEmpty()) {
                lines.add(FarmCard.Line.text(own.get(0), FarmCard.INFO));
            }
        } else {
            if (net != null) {
                lines.add(FarmCard.Line.pair(FarmCard.label("power"), Component.translatable("card.pioneers.of", watts(net.power()), watts(net.capacity())), 0xE8C060));
                lines.add(FarmCard.Line.pair(FarmCard.label("network"), Component.translatable("card.pioneers.parts", net.size(), net.sources(), net.machines()),
                      FarmCard.DIM));
            }
            for (Component c : own) {
                lines.add(FarmCard.Line.text(c, FarmCard.INFO));
            }
            if (jammed) {
                lines.add(FarmCard.Line.text(FarmCard.word("jammed"), FarmCard.BAD));
            }
        }
        Component hint = lookHint();
        if (hint != null) {
            lines.add(FarmCard.Line.hint(hint));
        } else if (!sneaking) {
            lines.add(FarmCard.Line.hint(FarmCard.word("hint.details")));
        }
        BlockState state = getBlockState();
        return new FarmCard(new ItemStack(state.getBlock()), state.getBlock().getName(), 0xC89A5A, lines);
    }

    /** 1.4 : aide en bas de la fiche au regard (manivelle : comment tourner...), ou null. */
    @Nullable
    protected Component lookHint() {
        return null;
    }

    public static String fmt(double value) {
        double a = Math.abs(value);
        return String.format(Locale.ROOT, a >= 100 ? "%.0f" : a >= 10 ? "%.1f" : "%.2f", value);
    }

    public static String watts(double w) {
        return Math.abs(w) >= 1000 ? String.format(Locale.ROOT, "%.2f kW", w / 1000) : String.format(Locale.ROOT, "%.0f W", w);
    }
}
