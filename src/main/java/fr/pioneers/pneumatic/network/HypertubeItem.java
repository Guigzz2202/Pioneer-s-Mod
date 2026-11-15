package fr.pioneers.pneumatic.network;

import fr.pioneers.clan.ClanApi;
import fr.pioneers.core.block.DescribedBlockItem;
import fr.pioneers.core.tooltip.Tooltips;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 27, Guillaume : « Supprime [...] l'encrage d'hypertube. Les hypertubes doivent toujours avoir une hitbox pour les
 * detruire ») : plus d'ancrage physique ; les bouts sont des stations ou des bouts libres (case libre visee : le tube s'y arrete, sans bloc) ;
 * apres une pose, le bout d'arrivee devient le depart suivant ; un tube vise se demonte d'un clic gauche, quoi qu'on tienne
 * ({@code client/pneumatic/HypertubeClient}).
 * <p>
 * 0.20.43 : le trace se deforme un peu pour eviter un obstacle ({@link HypertubeCurve#fit}).
 * <p>
 * 0.20.39 : objet hypertube. Le tube n'est plus fait de blocs : c'est une courbe ({@link HypertubeCurve}) tiree d'un point A a un point B, comme
 * les voies de Create. Clic droit = depart (face d'une station, bout d'un tube deja pose pour le prolonger, ou n'importe quelle case visee) ;
 * second clic droit = arrivee : le troncon est cree d'un coup s'il ne heurte rien et ne tourne pas trop serre, et consomme un tube par bloc de
 * longueur. Maj + clic droit = annuler ; clic gauche sur un tube (ou la cle) = le demonter (tubes rendus). Les clics sont lus par le client
 * ({@code client/pneumatic/HypertubeClient}, qui voit les tubes et dessine l'apercu) puis verifies par le serveur ({@link #click}).
 */
public class HypertubeItem extends DescribedBlockItem {

    public static final String TAG = "hypertube";
    /** Portee de visee (blocs) ; marge acceptee par le serveur. */
    public static final double REACH = 7, SERVER_REACH = 12;

    public HypertubeItem(Block block, Properties properties, String description) {
        super(block, properties, description);
    }

    /** Bout de troncon visible : position, direction de sortie, troncon, raccorde a un autre bout. */
    public record End(Vec3 pos, Vec3 out, int segment, boolean joined) {
    }

    /** Troncon vu (serveur ou client) : id, courbe, bouts raccordes a une station. */
    public record Seen(int id, HypertubeCurve curve, boolean stationA, boolean stationB) {
    }

    /** Bouts sans station d'une liste de troncons, avec leurs raccords. */
    public static List<End> ends(Iterable<Seen> segments) {
        List<End> raw = new ArrayList<>();
        for (Seen s : segments) {
            HypertubeCurve c = s.curve();
            if (!s.stationA()) {
                raw.add(new End(c.a, c.ta.scale(-1), s.id(), false));
            }
            if (!s.stationB()) {
                raw.add(new End(c.b, c.tb, s.id(), false));
            }
        }
        List<End> out = new ArrayList<>(raw.size());
        for (End e : raw) {
            boolean joined = false;
            for (End o : raw) {
                if (o != e && o.pos.distanceTo(e.pos) < HypertubeWorld.JOIN) {
                    joined = true;
                    break;
                }
            }
            out.add(new End(e.pos, e.out, e.segment, joined));
        }
        return out;
    }

    /**
     * Ancre visee par le joueur : bout libre d'un tube proche du regard (avant le bloc vise), sinon face d'une station (pas sa bouche), sinon
     * la case devant la face visee (ou le bloc lui-meme s'il est remplacable), ou le tube s'arretera. Null : rien a portee.
     */
    @Nullable
    public static HypertubeAnchor aim(Level level, Player player, List<End> ends) {
        Vec3 eye = player.getEyePosition();
        Vec3 dir = player.getViewVector(1);
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(dir.scale(REACH)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        boolean block = hit.getType() == HitResult.Type.BLOCK;
        double hitT = block ? hit.getLocation().distanceTo(eye) : REACH;
        End best = null;
        double bestScore = Double.MAX_VALUE;
        for (End e : ends) {
            double t = e.pos.subtract(eye).dot(dir);
            if (t < 0 || t > Math.min(REACH, hitT + 0.8)) {
                continue;
            }
            double d = eye.add(dir.scale(t)).distanceTo(e.pos);
            double score = d + (e.joined ? 0.25 : 0);
            if (d <= 0.7 && score < bestScore) {
                bestScore = score;
                best = e;
            }
        }
        if (best != null) {
            return new HypertubeAnchor(best.pos, best.out, null, best.segment);
        }
        if (!block) {
            return null;
        }
        BlockPos pos = hit.getBlockPos();
        Direction face = hit.getDirection();
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof TubeStationBlock && face != state.getValue(TubeStationBlock.FACING)) {
            return stationAnchor(pos, face);
        }
        BlockPos cell = state.canBeReplaced() && !(state.getBlock() instanceof TubeStationBlock) ? pos : pos.relative(face);
        return level.getBlockState(cell).canBeReplaced() ? new HypertubeAnchor(Vec3.atCenterOf(cell), null, null, -1) : null;
    }

    /** Ancre sur la face {@code face} d'une station : le tube en sort perpendiculairement. */
    public static HypertubeAnchor stationAnchor(BlockPos station, Direction face) {
        Vec3 n = Vec3.atLowerCornerOf(face.getNormal());
        return new HypertubeAnchor(Vec3.atCenterOf(station).add(n.scale(0.5)), n, station, -1);
    }

    /** Depart choisi (dans cette dimension), ou null. */
    @Nullable
    public static HypertubeAnchor start(ItemStack stack, Level level) {
        CompoundTag tag = stack.getTagElement(TAG);
        if (tag == null || !tag.contains("start") || !level.dimension().location().toString().equals(tag.getString("dim"))) {
            return null;
        }
        return HypertubeAnchor.load(tag.getCompound("start"));
    }

    /** Pile d'hypertubes tenue (main principale d'abord), ou vide. */
    public static ItemStack held(Player player) {
        for (ItemStack s : new ItemStack[]{player.getMainHandItem(), player.getOffhandItem()}) {
            if (s.getItem() instanceof HypertubeItem) {
                return s;
            }
        }
        return ItemStack.EMPTY;
    }

    /** Objets {@code item} dans l'inventaire du joueur (toutes les piles). */
    public static int count(Player player, Item item) {
        int n = 0;
        for (ItemStack s : player.getInventory().items) {
            if (s.is(item)) {
                n += s.getCount();
            }
        }
        for (ItemStack s : player.getInventory().offhand) {
            if (s.is(item)) {
                n += s.getCount();
            }
        }
        return n;
    }

    /** Tubes disponibles dans l'inventaire du joueur (toutes les piles de cet objet). */
    public static int count(Player player, ItemStack held) {
        return count(player, held.getItem());
    }

    /** Trace de {@code a} a {@code b} : la courbe (deformee si un obstacle la gene) et ce qui empeche de la poser (null : posable). */
    public record Plan(HypertubeCurve curve, @Nullable Component problem) {
    }

    /** Trace et raison qui l'empeche (cle de langue + arguments). Code commun : l'apercu du client l'utilise aussi. */
    public static Plan plan(Level level, HypertubeAnchor a, HypertubeAnchor b) {
        Vec3[] t = HypertubeCurve.tangents(a, b);
        if (a.pos().distanceTo(b.pos()) < 1 || a.station() != null && a.station().equals(b.station())
            || a.block() != null && a.block().equals(b.block())) {
            return new Plan(new HypertubeCurve(a.pos(), b.pos(), t[0], t[1]), Component.translatable("message.pioneers.hypertube.too_short"));
        }
        if (a.pos().distanceTo(b.pos()) > HypertubeCurve.MAX_LENGTH) {
            HypertubeCurve c = new HypertubeCurve(a.pos(), b.pos(), t[0], t[1]);
            return new Plan(c, Component.translatable("message.pioneers.hypertube.too_long", (int) Math.ceil(c.length()), (int) HypertubeCurve.MAX_LENGTH));
        }
        HypertubeCurve.Fit fit = HypertubeCurve.fit(level, a.pos(), b.pos(), t[0], t[1]);
        HypertubeCurve c = fit.curve();
        if (c.length() > HypertubeCurve.MAX_LENGTH) {
            return new Plan(c, Component.translatable("message.pioneers.hypertube.too_long", (int) Math.ceil(c.length()), (int) HypertubeCurve.MAX_LENGTH));
        }
        double radius = c.minRadius();
        if (radius < HypertubeCurve.MIN_RADIUS) {
            return new Plan(c, Component.translatable("message.pioneers.hypertube.too_tight", String.format(Locale.ROOT, "%.1f", radius),
                  String.format(Locale.ROOT, "%.1f", HypertubeCurve.MIN_RADIUS)));
        }
        BlockPos o = fit.obstacle();
        if (o != null) {
            return new Plan(c, Component.translatable("message.pioneers.hypertube.obstacle", o.getX(), o.getY(), o.getZ()));
        }
        return new Plan(c, null);
    }

    /** Le clic n'est jamais traite par le bloc : tout passe par {@code HypertubeClient} puis {@link #click}. */
    @NotNull
    @Override
    public InteractionResult useOn(@NotNull UseOnContext context) {
        return InteractionResult.FAIL;
    }

    @NotNull
    @Override
    public InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        return InteractionResultHolder.fail(player.getItemInHand(hand));
    }

    /** Serveur : ancre envoyee par le client, revue d'apres le monde (ou null si elle ne tient pas). */
    @Nullable
    static HypertubeAnchor verify(ServerLevel level, ServerPlayer player, HypertubeAnchor anchor) {
        if (!Double.isFinite(anchor.pos().lengthSqr()) || anchor.pos().distanceTo(player.getEyePosition()) > SERVER_REACH) {
            return null;
        }
        if (anchor.segment() >= 0) {
            HypertubeWorld.Segment s = HypertubeWorld.get(level).segment(anchor.segment());
            if (s == null) {
                return null;
            }
            if (s.stationA() == null && s.a().distanceTo(anchor.pos()) < HypertubeWorld.JOIN) {
                return new HypertubeAnchor(s.a(), s.out(true), null, s.id());
            }
            if (s.stationB() == null && s.b().distanceTo(anchor.pos()) < HypertubeWorld.JOIN) {
                return new HypertubeAnchor(s.b(), s.out(false), null, s.id());
            }
            return null;
        }
        if (anchor.block() != null) {
            return null;                                         //1.0.4-beta (lot 27) : plus d'ancrage physique
        }
        if (anchor.station() != null) {
            BlockState state = level.getBlockState(anchor.station());
            if (anchor.out() == null || !(state.getBlock() instanceof TubeStationBlock)) {
                return null;
            }
            Direction face = Direction.getNearest(anchor.out().x, anchor.out().y, anchor.out().z);
            return face == state.getValue(TubeStationBlock.FACING) ? null : stationAnchor(anchor.station(), face);
        }
        BlockPos cell = BlockPos.containing(anchor.pos());
        return level.getBlockState(cell).canBeReplaced() ? new HypertubeAnchor(Vec3.atCenterOf(cell), null, null, -1) : null;
    }

    /** Serveur : clic droit avec l'hypertube (ancre visee par le client) ou annulation. */
    static void click(ServerPlayer player, @Nullable HypertubeAnchor sent, boolean cancel) {
        ItemStack stack = held(player);
        if (stack.isEmpty() || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (cancel) {
            if (stack.getTagElement(TAG) != null) {
                stack.removeTagKey(TAG);
                player.displayClientMessage(Component.translatable("message.pioneers.hypertube.cancel").withStyle(ChatFormatting.GRAY), true);
            }
            return;
        }
        HypertubeAnchor anchor = sent == null ? null : verify(level, player, sent);
        if (anchor == null) {
            player.displayClientMessage(Component.translatable("message.pioneers.hypertube.no_anchor").withStyle(ChatFormatting.RED), true);
            return;
        }
        HypertubeAnchor start = start(stack, level);
        if (start != null) {
            start = relaxed(level, start);
        }
        if (start == null) {
            setStart(stack, level, anchor);
            BlockPos p = BlockPos.containing(anchor.pos());
            player.displayClientMessage(Component.translatable("message.pioneers.hypertube.start", p.getX(), p.getY(), p.getZ())
                  .withStyle(ChatFormatting.AQUA), true);
            level.playSound(null, p, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.6F, 1.4F);
            return;
        }
        build(level, player, stack, start, anchor);
    }

    private static void setStart(ItemStack stack, Level level, HypertubeAnchor anchor) {
        CompoundTag tag = stack.getOrCreateTagElement(TAG);
        tag.put("start", anchor.save());
        tag.putString("dim", level.dimension().location().toString());
    }

    /** Depart garde dans l'objet : revu d'apres le monde sans condition de portee (le joueur a pu s'eloigner). */
    @Nullable
    private static HypertubeAnchor relaxed(ServerLevel level, HypertubeAnchor start) {
        if (start.segment() >= 0) {
            HypertubeWorld.Segment s = HypertubeWorld.get(level).segment(start.segment());
            return s != null && (s.a().distanceTo(start.pos()) < HypertubeWorld.JOIN || s.b().distanceTo(start.pos()) < HypertubeWorld.JOIN) ? start : null;
        }
        if (start.block() != null) {
            return null;                                         //1.0.4-beta (lot 27) : depart garde sur un ancrage d'avant : oublie
        }
        if (start.station() != null) {
            return level.getBlockState(start.station()).getBlock() instanceof TubeStationBlock ? start : null;
        }
        return level.getBlockState(BlockPos.containing(start.pos())).canBeReplaced() ? start : null;
    }

    /** Serveur : demontage du troncon {@code id} (vise par le client, a portee) ; tubes rendus au joueur hors creatif. */
    static void dismantle(ServerPlayer player, int id) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        HypertubeWorld world = HypertubeWorld.get(level);
        HypertubeWorld.Segment s = world.segment(id);
        if (s == null || world.curve(s).distanceTo(player.getEyePosition()) > SERVER_REACH) {
            return;
        }
        Vec3 near = s.a();
        for (Vec3 p : world.curve(s).points()) {
            if (p.distanceToSqr(player.getEyePosition()) < near.distanceToSqr(player.getEyePosition())) {
                near = p;
            }
        }
        //0.20.46 (audit) : ni spectateur ni zone protegee (protection du point d'apparition, bordure du monde)
        if (player.isSpectator() || !level.mayInteract(player, BlockPos.containing(near))) {
            return;
        }
        world.remove(level, id);
        if (!player.getAbilities().instabuild) {
            Item item = PiPneumaticNetwork.TUBE.asItem();
            for (int left = s.tubes(); left > 0; left -= 64) {
                player.getInventory().placeItemBackInInventory(new ItemStack(item, Math.min(64, left)));
            }
        }
        level.playSound(null, near.x, near.y, near.z, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 0.9F, 1.1F);
        player.displayClientMessage(Component.translatable("message.pioneers.hypertube.removed", s.tubes()).withStyle(ChatFormatting.GRAY), true);
    }

    /**
     * Pose du troncon de {@code a} a {@code b} si tout est libre et si le joueur a assez de tubes. Le bout d'arrivee (libre) devient le depart
     * suivant : on prolonge la ligne clic apres clic (Maj + clic : arreter).
     */
    private static void build(ServerLevel level, ServerPlayer player, ItemStack stack, HypertubeAnchor a, HypertubeAnchor b) {
        Plan plan = plan(level, a, b);
        if (plan.problem() != null) {
            player.displayClientMessage(plan.problem().copy().withStyle(ChatFormatting.RED), true);
            return;
        }
        HypertubeCurve curve = plan.curve();
        //1.9 (MAJ RP, bloc 4) : un tube ne part ni n'arrive dans un claim ou le joueur n'a pas le droit de batir
        if (!ClanApi.canModify(player, level, BlockPos.containing(a.pos()), ClanApi.Action.PLACE)
            || !ClanApi.canModify(player, level, BlockPos.containing(b.pos()), ClanApi.Action.PLACE)) {
            player.displayClientMessage(Component.translatable("clan.pioneers.protect.build").withStyle(ChatFormatting.RED), true);
            return;
        }
        HypertubeWorld world = HypertubeWorld.get(level);
        if (a.station() != null && world.faceUsed(a.pos()) || b.station() != null && world.faceUsed(b.pos())) {
            player.displayClientMessage(Component.translatable("message.pioneers.hypertube.face_used").withStyle(ChatFormatting.RED), true);
            return;
        }
        int need = curve.tubes();
        boolean creative = player.getAbilities().instabuild;
        int have = count(player, stack);
        if (!creative && have < need) {
            player.displayClientMessage(Component.translatable("message.pioneers.hypertube.missing", need, have).withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!creative) {
            consume(player, stack, need);
        }
        HypertubeWorld.Segment made = world.add(level, a, b, curve);
        BlockPos p = BlockPos.containing(b.pos());
        //1.0.4-beta (lot 27) : bout libre d'arrivee (case libre) = depart suivant, dans le prolongement du tube
        HypertubeAnchor next = b.fresh() && made != null ? new HypertubeAnchor(made.b(), made.out(false), null, made.id()) : null;
        if (next != null && !stack.isEmpty()) {
            setStart(stack, level, next);
        } else if (!stack.isEmpty()) {
            stack.removeTagKey(TAG);
        }
        level.playSound(null, p, SoundEvents.GLASS_PLACE, SoundSource.BLOCKS, 1, 0.9F);
        player.displayClientMessage(Component.translatable(next != null ? "message.pioneers.hypertube.built_next" : "message.pioneers.hypertube.built",
              need, String.format(Locale.ROOT, "%.1f", curve.length())).withStyle(ChatFormatting.GREEN), true);
    }

    /** Retire {@code n} tubes : la pile en main d'abord, puis le reste de l'inventaire. */
    private static void consume(Player player, ItemStack held, int n) {
        int take = Math.min(n, held.getCount());
        held.shrink(take);
        n -= take;
        List<ItemStack> all = new ArrayList<>(player.getInventory().items);
        all.addAll(player.getInventory().offhand);
        for (ItemStack s : all) {
            if (n <= 0) {
                break;
            }
            if (s != held && s.is(held.getItem())) {
                take = Math.min(n, s.getCount());
                s.shrink(take);
                n -= take;
            }
        }
    }

    @Override
    public boolean isFoil(@NotNull ItemStack stack) {
        return stack.getTagElement(TAG) != null || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        tips.desc("item.pioneers.hypertube.how");
        CompoundTag tag = stack.getTagElement(TAG);
        if (tag != null && tag.contains("start")) {
            BlockPos p = BlockPos.containing(HypertubeAnchor.load(tag.getCompound("start")).pos());
            tips.state("item.pioneers.hypertube.from", p.getX(), p.getY(), p.getZ());
        }
        super.appendHoverText(stack, level, tooltip, flag);
    }
}
