package fr.pioneers.home.line;

import fr.pioneers.farm.kitchen.KitchenRecipe;
import fr.pioneers.farm.kitchen.Station;
import fr.pioneers.home.PiHome;
import fr.pioneers.kinetics.rope.LinkType;
import fr.pioneers.kinetics.rope.RopeItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Patch 1.0.3-beta, lot 7 : un crochet et, s'il tient le bout « maitre » d'une corde, la corde et ce qui y pend. Le maitre (premier crochet
 * clique) porte la corde ({@link LinkType}), les pieces ({@link Hung} : place, objet, avancement du sechage / fumage) et le rendu ; l'autre ne
 * garde que l'adresse du maitre. Sauvegarde : {@code partner}, {@code master}, {@code link}, {@code hung} [{@code place}, {@code item},
 * {@code work}]. Les maitres charges sont tenus par dimension ({@link #lines}) pour viser une corde en l'air.
 */
public class HookTile extends BlockEntity {

    /** Une piece accrochee : place sur la corde, objet (1), avancement en ticks de recette du fil. */
    public static final class Hung {

        public final int place;
        public ItemStack stack;
        public double work;

        Hung(int place, ItemStack stack, double work) {
            this.place = place;
            this.stack = stack;
            this.work = work;
        }
    }

    private static final Map<Level, Set<HookTile>> LINES = new WeakHashMap<>();

    @Nullable
    private BlockPos partner;
    private boolean master;
    private LinkType link = LinkType.FIBER_ROPE;
    private final List<Hung> hung = new ArrayList<>();

    public HookTile(BlockPos pos, BlockState state) {
        super(PiHome.HOOK_TILE.get(), pos, state);
    }

    // ------------------------------------------------------------------ registre des cordes chargees (visee en l'air)
    public static Set<HookTile> lines(Level level) {
        synchronized (LINES) {
            return new java.util.HashSet<>(LINES.getOrDefault(level, Collections.emptySet()));
        }
    }

    private void track(boolean on) {
        if (level == null) {
            return;
        }
        synchronized (LINES) {
            Set<HookTile> s = LINES.computeIfAbsent(level, k -> Collections.newSetFromMap(new WeakHashMap<>()));
            if (on && master && partner != null) {
                s.add(this);
            } else {
                s.remove(this);
            }
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        track(true);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        track(false);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        track(false);
    }

    // ------------------------------------------------------------------ geometrie
    public Direction facing() {
        return getBlockState().getBlock() instanceof HookBlock ? getBlockState().getValue(HookBlock.FACING) : Direction.UP;
    }

    public Vec3 eye() {
        return HookBlock.eye(worldPosition, facing());
    }

    /** Oeil du crochet d'en face (le maitre le lit dans son voisin ; sinon le centre du bloc). */
    public Vec3 otherEye() {
        if (partner == null) {
            return eye();
        }
        if (level != null && level.getBlockEntity(partner) instanceof HookTile o) {
            return o.eye();
        }
        return Vec3.atCenterOf(partner);
    }

    public double length() {
        return eye().distanceTo(otherEye());
    }

    public int places() {
        return Clothesline.places(length());
    }

    public double sag() {
        return Clothesline.sag(length(), hung.size());
    }

    /** Point de la place {@code i}. */
    public Vec3 placePoint(int i) {
        return Clothesline.point(eye(), otherEye(), sag(), Clothesline.t(i, places()));
    }

    @Nullable
    public BlockPos partner() {
        return partner;
    }

    public boolean isMaster() {
        return master && partner != null;
    }

    public LinkType link() {
        return link;
    }

    public List<Hung> hung() {
        return hung;
    }

    /** Le maitre de la corde de ce crochet (lui-meme s'il l'est), ou null sans corde. */
    @Nullable
    public HookTile master() {
        if (partner == null) {
            return null;
        }
        if (master) {
            return this;
        }
        return level != null && level.getBlockEntity(partner) instanceof HookTile o && o.master ? o : null;
    }

    // ------------------------------------------------------------------ corde
    /** Clic avec une corde : premier crochet retenu dans la corde ({@code hook_from}), puis tension vers le second. */
    void ropeClick(Player player, ItemStack rope, LinkType type) {
        CompoundTag tag = rope.getTag();
        if (partner != null) {
            say(player, "busy", ChatFormatting.YELLOW);
            return;
        }
        if (tag == null || !tag.contains("hook_from")) {
            rope.getOrCreateTag().putLong("hook_from", worldPosition.asLong());
            say(player, "first", ChatFormatting.GRAY);
            return;
        }
        BlockPos from = BlockPos.of(tag.getLong("hook_from"));
        rope.removeTagKey("hook_from");
        if (rope.getTag() != null && rope.getTag().isEmpty()) {
            rope.setTag(null);
        }
        String error = check(level, from, worldPosition);
        if (error != null) {
            say(player, error, ChatFormatting.RED);
            return;
        }
        HookTile first = (HookTile) level.getBlockEntity(from);
        first.link(worldPosition, type, true);
        link(from, type, false);
        if (!player.getAbilities().instabuild) {
            rope.shrink(1);
        }
        level.playSound(null, worldPosition, SoundEvents.LEASH_KNOT_PLACE, SoundSource.BLOCKS, 1F, 1F);
        player.displayClientMessage(Component.translatable("home.pioneers.line.done", Math.round(first.length()), first.places()), true);
    }

    /** Pourquoi deux crochets ne peuvent pas etre relies (cle de langue courte), ou null. */
    @Nullable
    public static String check(Level level, BlockPos a, BlockPos b) {
        if (a.equals(b)) {
            return "same";
        }
        if (!(level.getBlockEntity(a) instanceof HookTile ha) || !(level.getBlockEntity(b) instanceof HookTile hb)) {
            return "gone";
        }
        if (ha.partner != null || hb.partner != null) {
            return "busy";
        }
        Vec3 ea = ha.eye(), eb = hb.eye();
        if (ea.distanceTo(eb) > Clothesline.MAX_LENGTH) {
            return "far";
        }
        if (ea.distanceTo(eb) < 1.0) {
            return "near";
        }
        Vec3 dir = eb.subtract(ea).normalize();
        BlockHitResult hit = level.clip(new ClipContext(ea.add(dir.scale(0.3)), eb.subtract(dir.scale(0.3)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
              null));
        if (hit.getType() == HitResult.Type.BLOCK && !hit.getBlockPos().equals(a) && !hit.getBlockPos().equals(b)) {
            return "blocked";
        }
        return null;
    }

    /** Relie ce crochet a {@code other} (code commun, aussi pour les GameTests). */
    public void link(BlockPos other, LinkType type, boolean master) {
        track(false);
        this.partner = other;
        this.link = type;
        this.master = master;
        track(true);
        changed();
    }

    /** Corde detendue a la main (Maj + main vide) : la corde et les pieces vont au joueur. */
    void unlink(Player player) {
        HookTile m = master();
        if (m == null) {
            return;
        }
        List<ItemStack> back = m.release();
        for (ItemStack s : back) {
            if (!player.getInventory().add(s)) {
                player.drop(s, false);
            }
        }
        level.playSound(null, worldPosition, SoundEvents.LEASH_KNOT_BREAK, SoundSource.BLOCKS, 1F, 1F);
    }

    /** Defait la corde (maitre) : renvoie la corde et les pieces ; l'autre crochet est libere. */
    public List<ItemStack> release() {
        List<ItemStack> out = new ArrayList<>();
        if (partner == null) {
            return out;
        }
        HookTile m = master();
        if (m == null) {
            clear();
            return out;
        }
        out.add(RopeItem.stack(m.link));
        for (Hung h : m.hung) {
            out.add(h.stack);
        }
        m.hung.clear();
        BlockPos other = m.partner;
        m.clear();
        if (other != null && level.getBlockEntity(other) instanceof HookTile o) {
            o.clear();
        }
        return out;
    }

    private List<ItemStack> hungStacksAndRope() {
        List<ItemStack> out = new ArrayList<>();
        out.add(RopeItem.stack(link));
        for (Hung h : hung) {
            out.add(h.stack);
        }
        return out;
    }

    private void clear() {
        track(false);
        partner = null;
        master = false;
        hung.clear();
        changed();
    }

    /** Crochet casse : la corde et les pieces tombent au sol. */
    void broken() {
        BlockPos at = worldPosition;
        for (ItemStack s : release()) {
            Containers.dropItemStack(level, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, s);
        }
    }

    // ------------------------------------------------------------------ pieces
    @Nullable
    Hung at(int place) {
        for (Hung h : hung) {
            if (h.place == place) {
                return h;
            }
        }
        return null;
    }

    /** Accroche un objet (1) a la place libre la plus proche de {@code t} ; vrai si c'est fait. */
    public boolean hang(@Nullable Player player, ItemStack held, double t) {
        int n = places();
        int want = Clothesline.nearestPlace(t, n), best = -1;
        for (int d = 0; d < n && best < 0; d++) {
            for (int s : new int[]{want - d, want + d}) {
                if (s >= 0 && s < n && at(s) == null) {
                    best = s;
                    break;
                }
            }
        }
        if (best < 0) {
            if (player != null) {
                say(player, "full", ChatFormatting.YELLOW);
            }
            return false;
        }
        hung.add(new Hung(best, held.copyWithCount(1), 0));
        if (player == null || !player.getAbilities().instabuild) {
            held.shrink(1);
        }
        level.playSound(null, BlockPos.containing(placePoint(best)), SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.6F, 1.2F);
        changed();
        return true;
    }

    /** Reprend la piece la plus proche de {@code t} ; vrai si une piece est venue. */
    public boolean take(@Nullable Player player, double t) {
        if (hung.isEmpty()) {
            return false;
        }
        int n = places();
        Hung best = null;
        double d = Double.MAX_VALUE;
        for (Hung h : hung) {
            double e = Math.abs(Clothesline.t(h.place, n) - t);
            if (e < d) {
                d = e;
                best = h;
            }
        }
        hung.remove(best);
        if (player != null) {
            if (!player.getInventory().add(best.stack)) {
                player.drop(best.stack, false);
            }
        }
        level.playSound(null, BlockPos.containing(placePoint(best.place)), SoundEvents.WOOL_BREAK, SoundSource.BLOCKS, 0.6F, 1.3F);
        changed();
        return true;
    }

    // ------------------------------------------------------------------ sechage
    void serverTick() {
        if (!isMaster() || level == null || level.getGameTime() % Clothesline.STEP != (worldPosition.asLong() & 15)) {
            return;
        }
        //L'autre crochet a disparu sans prevenir (troncon decharge au moment de la casse) : la corde et les pieces tombent ici
        if (level.isLoaded(partner) && !(level.getBlockEntity(partner) instanceof HookTile o && worldPosition.equals(o.partner))) {
            for (ItemStack s : List.copyOf(hungStacksAndRope())) {
                Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, s);
            }
            clear();
            return;
        }
        if (hung.isEmpty()) {
            return;
        }
        boolean dirty = false;
        for (Hung h : hung) {
            Clothesline.Air air = Clothesline.Air.at(level, placePoint(h.place));
            KitchenRecipe dry = Clothesline.recipe(level, Station.DRYING, h.stack), smoke = Clothesline.recipe(level, Station.SMOKING, h.stack);
            Clothesline.Mode mode = Clothesline.mode(dry != null, dry != null && dry.smoke(), smoke != null, air);
            double rate = Clothesline.rate(mode, air);
            if (rate <= 0) {
                continue;
            }
            KitchenRecipe r = mode == Clothesline.Mode.SMOKE ? smoke : dry;
            h.work += rate * Clothesline.STEP;
            if (h.work >= Clothesline.lineTime(r)) {
                List<ItemStack> out = r.roll(level.random, h.stack);
                h.stack = out.isEmpty() ? ItemStack.EMPTY : out.get(0).copyWithCount(1);
                h.work = 0;
                dirty = true;
                if (level instanceof net.minecraft.server.level.ServerLevel sl) {
                    Vec3 p = placePoint(h.place);
                    sl.sendParticles(mode == Clothesline.Mode.SMOKE ? net.minecraft.core.particles.ParticleTypes.CAMPFIRE_COSY_SMOKE
                          : net.minecraft.core.particles.ParticleTypes.WHITE_ASH, p.x, p.y - 0.2, p.z, 4, 0.1, 0.1, 0.1, 0.01);
                }
            }
        }
        hung.removeIf(h -> h.stack.isEmpty());
        setChanged();
        if (dirty) {
            changed();
        }
    }

    /** Avancement de la piece (0..1) pour l'infobulle et les GameTests ; -1 si elle ne seche pas. */
    public double progress(Hung h) {
        KitchenRecipe dry = Clothesline.recipe(level, Station.DRYING, h.stack), smoke = Clothesline.recipe(level, Station.SMOKING, h.stack);
        KitchenRecipe r = smoke != null ? smoke : dry;
        return r == null ? -1 : Math.min(1, h.work / Clothesline.lineTime(r));
    }

    // ------------------------------------------------------------------ outils
    private void say(Player player, String key, ChatFormatting color) {
        player.displayClientMessage(Component.translatable("home.pioneers.line." + key, Clothesline.MAX_LENGTH).withStyle(color), true);
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public AABB getRenderBoundingBox() {
        if (isMaster() && partner != null) {
            return new AABB(worldPosition, partner).expandTowards(1, 1, 1).inflate(1.5);
        }
        return new AABB(worldPosition).inflate(0.5);
    }

    // ------------------------------------------------------------------ sauvegarde et synchro
    private void write(CompoundTag tag) {
        if (partner != null) {
            tag.putLong("partner", partner.asLong());
        }
        tag.putBoolean("master", master);
        tag.putInt("link", link.ordinal());
        ListTag list = new ListTag();
        for (Hung h : hung) {
            CompoundTag t = new CompoundTag();
            t.putInt("place", h.place);
            t.put("item", h.stack.save(new CompoundTag()));
            t.putDouble("work", h.work);
            list.add(t);
        }
        tag.put("hung", list);
    }

    private void read(CompoundTag tag) {
        track(false);
        partner = tag.contains("partner") ? BlockPos.of(tag.getLong("partner")) : null;
        master = tag.getBoolean("master");
        link = LinkType.byOrdinal(tag.getInt("link"));
        hung.clear();
        for (Tag e : tag.getList("hung", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) e;
            ItemStack s = ItemStack.of(t.getCompound("item"));
            if (!s.isEmpty()) {
                hung.add(new Hung(t.getInt("place"), s, t.getDouble("work")));
            }
        }
        track(true);
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        write(tag);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        read(tag);
    }

    @NotNull
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        write(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        read(tag);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet) {
        if (packet.getTag() != null) {
            read(packet.getTag());
        }
    }
}
