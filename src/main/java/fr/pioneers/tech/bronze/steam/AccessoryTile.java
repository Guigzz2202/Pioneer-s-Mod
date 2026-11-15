package fr.pioneers.tech.bronze.steam;

import fr.pioneers.tech.bronze.BronzeEvents;
import fr.pioneers.core.block.PiBlockEntity;
import fr.pioneers.core.registries.PiBronze;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Organe de vapeur : lit (ou soulage) la reserve de son support ; valeurs envoyees au client pour l'aiguille, le niveau, le panache.
 * 1.0.2-beta (lot 3, plus de pression) : le manometre devient une JAUGE DE VAPEUR (remplissage de la reserve, 0 a 100 %), la soupape
 * s'ouvre au-dela du remplissage regle (5 a 100 %, par pas de 5), le sifflet en alarme siffle quand l'eau de la chaudiere est basse
 * (le seul vrai danger). Ids et cles de sauvegarde inchanges ({@code setting} garde ses valeurs 1..20 : x 5 %).
 * 1.0.3-beta (lot 7, Guillaume : « son role est de detecter si le niveau d'eau est trop faible (20 %) ; si c'est le cas il stoppe la
 * chaudiere ») : la soupape devient la SURETE DE CHAUDIERE (id {@code safety_valve} garde) : posee sur une piece de la chaudiere (ou sur le
 * foyer), elle lit le niveau d'eau ; sous le seuil ({@link #cutoff}, 20 % par defaut, 5 a 50 % a la cle) elle COUPE le foyer
 * ({@link FireboxTile#cutOff}) : plus de chaleur ni de combustible brule, donc plus de chauffe a sec ; reprise a seuil + {@link #RESUME_GAP}.
 * Plus d'evacuation de vapeur : sans pression (1.0.2-beta, lot 3) une reserve pleine ne casse rien ({@code FireboxTile.dangers}).
 */
public class AccessoryTile extends PiBlockEntity implements SteamNode {

    private int setting = 5;
    /** 1.0.3-beta (lot 7) : seuil de coupure de la surete (% d'eau, 5..50 par pas de 5) ; coupee (eau sous le seuil, pas encore remontee). */
    private int cutoff = DEFAULT_CUTOFF;
    private boolean tripped;
    public static final int DEFAULT_CUTOFF = 20, MIN_CUTOFF = 5, MAX_CUTOFF = 50, RESUME_GAP = 5;
    private boolean alarm, powered;
    private float shownFill, shownWater, venting;
    private float clientFill, clientWater;
    private int whistle, sent;
    private boolean pending;

    public AccessoryTile(BlockPos pos, BlockState state) {
        super(PiBronze.ACCESSORY_TILE.get(), pos, state);
    }

    public SteamAccessory kind() {
        return getBlockState().getBlock() instanceof AccessoryBlock block ? block.kind : SteamAccessory.PRESSURE_GAUGE;
    }

    public Direction facing() {
        return getBlockState().getValue(AccessoryBlock.FACING);
    }

    public BlockPos supportPos() {
        return worldPosition.relative(facing().getOpposite());
    }

    @Nullable
    SteamVolume supportVolume() {
        if (level == null) {
            return null;
        }
        BlockEntity be = level.getBlockEntity(supportPos());
        if (be instanceof BoilerPartTile part) {
            FireboxTile firebox = part.controller();
            return firebox == null ? null : firebox.steamVolume();
        }
        if (be instanceof SteamNode node && !(be instanceof AccessoryTile)) {
            return node.steam(facing());
        }
        return null;
    }

    @Override
    public SteamVolume steam(Direction side) {
        return side == facing().getOpposite() ? supportVolume() : null;
    }

    @Override
    public boolean active() {
        return false;
    }

    @Override
    public BlockPos nodePos() {
        return worldPosition;
    }

    // ------------------------------------------------------------------
    // Comportements
    // ------------------------------------------------------------------

    /** Remplissage d'ouverture de la soupape (0..1) : reglage 1..20 x 5 %. */
    double valveOpen() {
        return setting * 0.05;
    }

    /** Part d'eau de la chaudiere qui porte l'organe (-1 si le support n'est pas une chaudiere). */
    double boilerWater(ServerLevel level) {
        FireboxTile f = boilerOf(level);
        return f == null ? -1 : f.water() / Math.max(1, f.waterCapacity());
    }

    /** 1.0.3-beta (lot 7) : foyer de la chaudiere qui porte l'organe (piece de chaudiere ou foyer lui-meme), sinon null. */
    @Nullable
    FireboxTile boilerOf(ServerLevel level) {
        BlockEntity be = level.getBlockEntity(supportPos());
        if (be instanceof BoilerPartTile part) {
            return part.controller();
        }
        return be instanceof FireboxTile f ? f : null;
    }

    /**
     * 1.0.3-beta (lot 7) : etat de la surete (pur, lu par les GameTests) : coupee sous {@code cutoff} % d'eau, rendue a {@code cutoff} +
     * {@link #RESUME_GAP} % (pas de clignotement autour du seuil).
     */
    public static boolean trip(boolean tripped, double water, int cutoff) {
        return tripped ? water < (cutoff + RESUME_GAP) / 100.0 : water < cutoff / 100.0;
    }

    /** Seuil de coupure (% d'eau). */
    public int cutoff() {
        return cutoff;
    }

    public boolean tripped() {
        return tripped;
    }

    void tick(ServerLevel level) {
        SteamVolume volume = supportVolume();
        double p = volume == null ? 0 : volume.fill();
        double boiler = boilerWater(level);
        float water = kind() == SteamAccessory.WATER_GAUGE ? (float) Math.max(0, boiler) : 0;
        venting = 0;
        switch (kind()) {
            case SAFETY_VALVE -> {
                //1.0.3-beta (lot 7) : surete de chaudiere : coupe le foyer quand l'eau passe sous le seuil, le rend a seuil + 5 %
                FireboxTile box = boilerOf(level);
                if (box != null && box.formed()) {
                    boolean was = tripped;
                    tripped = trip(tripped, box.water() / Math.max(1, box.waterCapacity()), cutoff);
                    if (tripped) {
                        box.cutOff(level.getGameTime());
                        if (!was) {
                            level.playSound(null, worldPosition, PiBronze.SOUND_HISS.get(), SoundSource.BLOCKS, 0.6F, 1.4F);
                            BronzeEvents.award(level, worldPosition, "safety_valve");
                            for (net.minecraft.server.level.ServerPlayer player : level.getEntitiesOfClass(net.minecraft.server.level.ServerPlayer.class,
                                  new net.minecraft.world.phys.AABB(worldPosition).inflate(16))) {
                                player.displayClientMessage(Component.translatable("steam.pioneers.cutoff.tripped", cutoff + " %").withStyle(ChatFormatting.GOLD), true);
                            }
                        }
                    }
                    if (was != tripped) {
                        setChanged();
                    }
                } else {
                    tripped = false;
                }
                venting = tripped ? 1 : 0;
            }
            case STEAM_WHISTLE -> {
                if (alarm && boiler >= 0 && boiler < SteamRules.LOW_WATER && level.getGameTime() % 60 == 0) {
                    blow(level, volume);
                }
                if (whistle > 0) {
                    whistle--;
                    if (volume != null) {
                        volume.take(3);
                    }
                    if (whistle % 3 == 0) {
                        level.sendParticles(ParticleTypes.CLOUD, worldPosition.getX() + 0.5, worldPosition.getY() + 0.9, worldPosition.getZ() + 0.5, 2, 0.03, 0.05, 0.03, 0.08);
                    }
                }
            }
            default -> {
            }
        }
        //Envoi au client (aiguille, niveau) : seulement si ca bouge assez
        if (Math.abs(p - shownFill) > 0.01 || Math.abs(water - shownWater) > 0.01 || (venting > 0) != (sent == 1)) {
            shownFill = (float) p;
            shownWater = water;
            sent = venting > 0 ? 1 : 0;
            pending = true;
        }
        if (pending && level.getGameTime() % 5 == 0) {
            pending = false;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }

    private void blow(ServerLevel level, @Nullable SteamVolume volume) {
        if (volume == null || volume.steam() < 30) {
            return;
        }
        whistle = 30;
        float pitch = (float) (0.85 + volume.fill() * 0.3);
        level.playSound(null, worldPosition, PiBronze.SOUND_WHISTLE.get(), SoundSource.BLOCKS, 3F, pitch);
        BronzeEvents.award(level, worldPosition, "whistle");
    }

    void redstone(ServerLevel level, boolean signal) {
        if (signal && !powered && kind() == SteamAccessory.STEAM_WHISTLE) {
            blow(level, supportVolume());
        }
        powered = signal;
    }

    void clientTick() {
        clientFill += (shownFill - clientFill) * 0.15F;
        clientWater += (shownWater - clientWater) * 0.1F;
        //1.0.3-beta (lot 7) : surete coupee : voyant rouge qui clignote sur le chapeau (plus de panache de vapeur)
        if (kind() == SteamAccessory.SAFETY_VALVE && sent == 1 && level != null && level.getGameTime() % 10 == 0) {
            level.addParticle(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1F, 0.15F, 0.1F), 0.8F),
                  worldPosition.getX() + 0.5, worldPosition.getY() + 0.72, worldPosition.getZ() + 0.5, 0, 0.01, 0);
        }
    }

    /** Remplissage de la reserve montre par l'aiguille (0..1). */
    public float clientFill() {
        return clientFill;
    }

    public float clientWater() {
        return clientWater;
    }

    int comparator() {
        if (kind() == SteamAccessory.WATER_GAUGE) {
            return Math.round(shownWater * 15);
        }
        return (int) Math.max(0, Math.min(15, Math.round(shownFill * 15)));
    }

    Component configure(boolean back) {
        if (kind() == SteamAccessory.SAFETY_VALVE) {
            //1.0.3-beta (lot 7) : seuil de coupure 5 a 50 % par pas de 5
            cutoff = back ? (cutoff <= MIN_CUTOFF ? MAX_CUTOFF : cutoff - 5) : (cutoff >= MAX_CUTOFF ? MIN_CUTOFF : cutoff + 5);
            setChanged();
            return Component.translatable("steam.pioneers.cutoff.set", cutoff + " %", cutoff + RESUME_GAP + " %").withStyle(ChatFormatting.AQUA);
        }
        if (kind() == SteamAccessory.STEAM_WHISTLE) {
            alarm = !alarm;
            setChanged();
            return Component.translatable(alarm ? "steam.pioneers.whistle_alarm" : "steam.pioneers.whistle_manual").withStyle(ChatFormatting.AQUA);
        }
        return reading();
    }

    Component reading() {
        SteamVolume v = supportVolume();
        String fill = (v == null ? 0 : Math.round(v.fill() * 100)) + " %";
        return switch (kind()) {
            case SAFETY_VALVE -> level instanceof ServerLevel server && boilerOf(server) == null
                  ? Component.translatable("steam.pioneers.cutoff.no_boiler")
                  : Component.translatable(tripped ? "steam.pioneers.cutoff.info_tripped" : "steam.pioneers.cutoff.info",
                        Math.round(Math.max(0, level instanceof ServerLevel server2 ? boilerWater(server2) : 0) * 100) + " %", cutoff + " %");
            case WATER_GAUGE -> Component.translatable("steam.pioneers.water_info", Math.round(shownWater * 100) + " %");
            case STEAM_WHISTLE -> Component.translatable(alarm ? "steam.pioneers.whistle_alarm" : "steam.pioneers.whistle_manual");
            default -> Component.translatable("steam.pioneers.gauge_info", fr.pioneers.core.Texts.format(Math.round(v == null ? 0 : v.steam())),
                  fr.pioneers.core.Texts.format(Math.round(v == null ? 0 : v.capacity())), fill);
        };
    }

    // ------------------------------------------------------------------
    // Sauvegarde et synchro
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("setting", setting);
        tag.putInt("cutoff", cutoff);
        tag.putBoolean("tripped", tripped);
        tag.putBoolean("alarm", alarm);
        tag.putBoolean("powered", powered);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        setting = tag.contains("setting") ? tag.getInt("setting") : 5;
        //1.0.3-beta (lot 7) : une soupape d'avant (sans « cutoff ») devient une surete a 20 %
        cutoff = tag.contains("cutoff") ? Math.max(MIN_CUTOFF, Math.min(MAX_CUTOFF, tag.getInt("cutoff"))) : DEFAULT_CUTOFF;
        tripped = tag.getBoolean("tripped");
        alarm = tag.getBoolean("alarm");
        powered = tag.getBoolean("powered");
    }

    @NotNull
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("f", shownFill);
        tag.putFloat("w", shownWater);
        tag.putInt("v", sent);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        shownFill = tag.getFloat("f");
        shownWater = tag.getFloat("w");
        sent = tag.getInt("v");
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet) {
        if (packet.getTag() != null) {
            handleUpdateTag(packet.getTag());
        }
    }
}
