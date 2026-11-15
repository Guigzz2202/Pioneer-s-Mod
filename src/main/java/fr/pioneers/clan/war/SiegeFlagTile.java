package fr.pioneers.clan.war;

import com.mojang.datafixers.util.Pair;
import fr.pioneers.clan.ClaimIndex;
import fr.pioneers.clan.Clan;
import fr.pioneers.clan.ClanApi;
import fr.pioneers.clan.ClanData;
import fr.pioneers.clan.core.ClanClock;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;

/**
 * 1.9 (MAJ RP, bloc 4) : la tuile du Drapeau de siege. Elle ne tourne que tant que le drapeau est pose : toutes les {@code check_ticks}
 * (20) ticks, elle compte les joueurs a {@code radius} (8) blocs : la capture avance tant qu'un attaquant reste, s'arrete si les defenseurs
 * sont au moins aussi nombreux, recule s'ils sont seuls (le drapeau tombe a 0) ; 10 minutes si un defenseur est connecte, 30 sinon. A 100 %,
 * le chunk change de mains ({@link Siege#capture}) et le drapeau reste 15 minutes. Il tombe aussi hors creneau, en fin de guerre, ou sans
 * attaquant pendant {@code idle_seconds}. Le client ne recoit que l'avancement, l'etat et la banniere de l'agresseur (jamais un identifiant
 * de joueur).
 */
public class SiegeFlagTile extends BlockEntity {

    public static final int SIEGE = 0, HOLD = 1;

    @Nullable
    private UUID war, attacker, defender, placer;
    private double progress;
    private int state;
    private long holdUntil, lastAttacker, lastCheck;
    private int counter, syncAge;
    private double synced = -1;
    private int syncedState = -1;
    private String step = "idle";
    private boolean registered;
    // ------------------------------------------------------------------ apparence (envoyee au client)
    private int base = DyeColor.WHITE.getId();
    private ListTag patterns = new ListTag();
    @Nullable
    private List<Pair<Holder<BannerPattern>, DyeColor>> patternCache;
    /** Avancement affiche (client, lisse par le rendu). */
    public float shown = -1;

    public SiegeFlagTile(BlockPos pos, BlockState state) {
        super(WarBlocks.SIEGE_FLAG_TILE.get(), pos, state);
    }

    // ------------------------------------------------------------------ demarrage
    void start(Siege.Plan plan, UUID placer) {
        this.war = plan.war().id();
        this.attacker = plan.attacker().id;
        this.defender = plan.defender().id;
        this.placer = placer;
        long now = ClanClock.now();
        this.lastAttacker = now;
        this.lastCheck = now;
        this.progress = 0;
        this.state = SIEGE;
        banner(plan.attacker());
        if (level != null) {
            Siege.register(ClanApi.dim(level), worldPosition, war);
            registered = true;
        }
        sync();
    }

    /** Couleurs de l'agresseur : teinture du clan, motif de la banniere de sa Mairie (embleme). */
    private void banner(Clan c) {
        base = c.color & 15;
        ListTag src = c.banner.contains("Patterns", Tag.TAG_LIST) ? c.banner.getList("Patterns", Tag.TAG_COMPOUND)
              : c.banner.getCompound("BlockEntityTag").getList("Patterns", Tag.TAG_COMPOUND);
        patterns = new ListTag();
        for (int i = 0; i < src.size() && i < 16; i++) {
            patterns.add(src.getCompound(i).copy());
        }
        if (c.banner.contains("Base")) {
            base = c.banner.getInt("Base") & 15;
        }
        patternCache = null;
    }

    // ------------------------------------------------------------------ controle (serveur)
    public static void serverTick(Level level, BlockPos pos, BlockState state, SiegeFlagTile t) {
        if (!(level instanceof ServerLevel sl)) {
            return;
        }
        if (!t.registered && t.war != null) {
            Siege.register(ClanApi.dim(level), pos, t.war);
            t.registered = true;
        }
        if (++t.counter < Math.max(1, WarRules.get().checkTicks())) {
            return;
        }
        t.counter = 0;
        t.check(sl);
    }

    private void check(ServerLevel level) {
        MinecraftServer server = level.getServer();
        WarRules.Values v = WarRules.get();
        long now = ClanClock.now();
        long dt = WarRules.checkDelta(lastCheck <= 0 ? v.checkTicks() * 50L : now - lastCheck, v.checkTicks());
        lastCheck = now;
        War w = war == null ? null : ClanWar.byId(server, war);
        if (w == null || w.ended()) {
            fall("clan.pioneers.siege.fell_war");
            return;
        }
        if (state == HOLD) {
            if (now >= holdUntil) {
                retire();
            }
            return;
        }
        ClanData data = ClanData.get(server);
        String dim = ClanApi.dim(level);
        long chunk = ChunkPos.asLong(worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
        UUID ownerId = ClaimIndex.owner(dim, chunk);
        Clan owner = ownerId == null ? null : data.clan(ownerId);
        if (owner == null || !w.defender(owner)) {
            fall("clan.pioneers.siege.fell_owner");
            return;
        }
        if (!ClanWar.inWindow(owner, now)) {
            fall("clan.pioneers.siege.fell_window");
            return;
        }
        int att = 0, def = 0;
        double r = v.radius(), r2 = r * r;
        double cx = worldPosition.getX() + 0.5, cy = worldPosition.getY() + 0.5, cz = worldPosition.getZ() + 0.5;
        for (ServerPlayer p : level.players()) {
            if (!p.isAlive() || p.isSpectator() || p.distanceToSqr(cx, cy, cz) > r2) {
                continue;
            }
            Clan c = data.clanOf(p.getUUID());
            if (c == null) {
                continue;
            }
            if (w.attacker(c)) {
                att++;
            } else if (w.defender(c)) {
                def++;
            }
        }
        boolean defOnline = false;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Clan c = data.clanOf(p.getUUID());
            if (c != null && w.defender(c)) {
                defOnline = true;
                break;
            }
        }
        WarRules.Step s = WarRules.step(att, def);
        step = s.name().toLowerCase(java.util.Locale.ROOT);
        if (att > 0) {
            lastAttacker = now;
        } else if (now - lastAttacker >= v.idleMs()) {
            fall("clan.pioneers.siege.fell_idle");
            return;
        }
        progress = WarRules.advance(progress, s, dt, WarRules.captureDuration(v, defOnline));
        if (s == WarRules.Step.RETREAT && progress <= 0) {
            fall("clan.pioneers.siege.fell_pushed");
            return;
        }
        if (WarRules.complete(progress)) {
            Clan taker = attacker == null ? null : data.clan(attacker);
            if (taker == null || !w.attacker(taker)) {
                fall("clan.pioneers.siege.fell_war");
                return;
            }
            if (w.capturesOn(ClanWar.realDay(now)) >= v.capturesPerDay()) {
                fall("clan.pioneers.siege.fell_daily");
                return;
            }
            Siege.capture(server, w, taker, owner, dim, chunk, worldPosition);
            progress = 1;
            state = HOLD;
            holdUntil = now + v.holdMs();
        }
        //avancement montre aux joueurs proches (barre d'action) : le chiffre ne vit que la
        Component bar = Component.translatable("clan.pioneers.siege.progress", (int) Math.floor(progress * 100),
              Component.translatable("clan.pioneers.siege.step." + (state == HOLD ? "taken" : step)));
        double show2 = (r * 2) * (r * 2);
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(cx, cy, cz) <= show2) {
                p.displayClientMessage(bar, true);
            }
        }
        setChanged();
        if (++syncAge >= 5 || Math.abs(progress - synced) >= 0.005 || state != syncedState) {
            sync();
        }
    }

    /** Le drapeau tombe : le bloc disparait, l'objet reste au sol (a qui le ramasse), les deux camps sont prevenus. */
    void fall(String reasonKey) {
        if (!(level instanceof ServerLevel sl)) {
            return;
        }
        MinecraftServer server = sl.getServer();
        War w = war == null ? null : ClanWar.byId(server, war);
        Clan d = defender == null ? null : ClanData.get(server).clan(defender);
        BlockPos pos = worldPosition;
        String coords = pos.getX() + " " + pos.getY() + " " + pos.getZ();
        remove(sl);
        if (w != null) {
            Siege.tellBoth(server, w, Component.translatable(reasonKey, d == null ? "?" : d.name, coords));
        }
    }

    /** Fin de la garde de 15 minutes apres une capture : le drapeau est rendu (objet au sol). */
    private void retire() {
        if (level instanceof ServerLevel sl) {
            remove(sl);
        }
    }

    private void remove(ServerLevel sl) {
        BlockPos pos = worldPosition;
        Siege.unregister(ClanApi.dim(sl), pos);
        registered = false;
        sl.removeBlock(pos, false);
        Block.popResource(sl, pos, new ItemStack(WarBlocks.SIEGE_FLAG.asItem()));
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide) {
            Siege.unregister(ClanApi.dim(level), worldPosition);
        }
        super.setRemoved();
    }

    // ------------------------------------------------------------------ lecture
    public double progress() {
        return progress;
    }

    public boolean holding() {
        return state == HOLD;
    }

    public int baseColor() {
        return base;
    }

    /** Motifs de la banniere de l'agresseur (calcules une fois, cote client). */
    public List<Pair<Holder<BannerPattern>, DyeColor>> patterns() {
        if (patternCache == null) {
            patternCache = BannerBlockEntity.createPatterns(DyeColor.byId(base), patterns);
        }
        return patternCache;
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition, worldPosition.offset(1, 2, 1));
    }

    // ------------------------------------------------------------------ sauvegarde et synchronisation
    private void sync() {
        synced = progress;
        syncedState = state;
        syncAge = 0;
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        if (war != null) {
            tag.putUUID("war", war);
        }
        if (attacker != null) {
            tag.putUUID("att", attacker);
        }
        if (defender != null) {
            tag.putUUID("def", defender);
        }
        if (placer != null) {
            tag.putUUID("placer", placer);
        }
        tag.putLong("hold", holdUntil);
        tag.putLong("lastAtt", lastAttacker);
        visuals(tag);
    }

    private void visuals(CompoundTag tag) {
        tag.putDouble("p", progress);
        tag.putInt("st", state);
        tag.putInt("base", base);
        tag.put("pat", patterns.copy());
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        war = tag.hasUUID("war") ? tag.getUUID("war") : war;
        attacker = tag.hasUUID("att") ? tag.getUUID("att") : attacker;
        defender = tag.hasUUID("def") ? tag.getUUID("def") : defender;
        placer = tag.hasUUID("placer") ? tag.getUUID("placer") : placer;
        if (tag.contains("hold")) {
            holdUntil = tag.getLong("hold");
            lastAttacker = tag.getLong("lastAtt");
        }
        progress = tag.getDouble("p");
        state = tag.getInt("st");
        base = tag.getInt("base") & 15;
        patterns = tag.getList("pat", Tag.TAG_COMPOUND);
        patternCache = null;
    }

    /** Ce que voit le client : avancement, etat, banniere. */
    @Override
    public @NotNull CompoundTag getUpdateTag() {
        CompoundTag t = new CompoundTag();
        visuals(t);
        return t;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
