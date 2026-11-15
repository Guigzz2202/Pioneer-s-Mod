package fr.pioneers.tech.kinetics.rope;

import fr.pioneers.core.tooltip.Tooltips;
import fr.pioneers.tech.kinetics.KineticNetwork;
import fr.pioneers.tech.kinetics.KineticNetworks;
import fr.pioneers.tech.kinetics.KineticsConfig;
import fr.pioneers.core.registries.PiKinetics;
import fr.pioneers.logistics.warehouse.WarehouseLines;
import fr.pioneers.logistics.warehouse.WarehouseRopes;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Corde ou courroie : clic droit sur une premiere poulie, puis sur une seconde (Maj : croisee). Les poulies doivent avoir des axes paralleles,
 * etre a portee, et rien de plein ne doit barrer le trajet ; 1.0.5-beta (lot 15) : elles peuvent etre decalees le long de leur axe
 * ({@link RopeRules} : corde oblique), avant elles devaient etre dans un meme plan perpendiculaire a cet axe. 1.0.4-beta (lot 27, Guillaume :
 * « On doit pouvoir connecter plus d'une corde a une poulie a condition que le systeme tourne dans le meme sens ») : jusqu'a
 * {@link PulleyTile#MAX_ROPES} cordes par poulie, tous les axes ; une corde est refusee si les deux systemes qu'elle relie tournent deja en
 * sens contraire (au rapport de la corde, croisee comprise) ou si elle boucle un reseau a un autre rapport ({@link #directionProblem}). Les
 * lignes d'entrepot (axe vertical) sont des cordes comme les autres (plus de poulie d'entrepot : {@code WarehouseRopes}).
 */
public class RopeItem extends Item {

    public final LinkType type;

    public RopeItem(LinkType type, Properties properties) {
        super(properties);
        this.type = type;
    }

    public static ItemStack stack(LinkType type) {
        return new ItemStack(switch (type) {
            case FIBER_ROPE -> PiKinetics.FIBER_ROPE.get();
            case TARRED_ROPE -> PiKinetics.TARRED_ROPE.get();
            case LEATHER_BELT -> PiKinetics.LEATHER_BELT.get();
        });
    }

    @Override
    public boolean isFoil(@NotNull ItemStack stack) {
        return stack.hasTag() && stack.getTag().contains("from");
    }

    @NotNull
    @Override
    public InteractionResult useOn(@NotNull UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        if (!(level.getBlockEntity(context.getClickedPos()) instanceof PulleyTile pulley) || player == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        BlockPos here = context.getClickedPos();
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains("from")) {
            if (!pulley.hasRoom()) {
                player.displayClientMessage(Component.translatable("kinetics.pioneers.rope.busy", pulley.capacity()).withStyle(ChatFormatting.YELLOW), true);
                return InteractionResult.CONSUME;
            }
            stack.getOrCreateTag().putLong("from", here.asLong());
            player.displayClientMessage(Component.translatable("kinetics.pioneers.rope.first"), true);
            return InteractionResult.CONSUME;
        }
        BlockPos from = BlockPos.of(tag.getLong("from"));
        stack.removeTagKey("from");
        boolean crossed = player.isShiftKeyDown();
        Component error = check(level, from, here, crossed);
        if (error != null) {
            player.displayClientMessage(error.copy().withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }
        PulleyTile first = (PulleyTile) level.getBlockEntity(from);
        first.connect(here, type, crossed, true);
        pulley.connect(from, type, crossed, false);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        level.playSound(null, here, SoundEvents.LEASH_KNOT_PLACE, SoundSource.BLOCKS, 1F, 1F);
        Component done = Component.translatable(crossed ? "kinetics.pioneers.rope.done_crossed" : "kinetics.pioneers.rope.done",
              Math.round(Math.sqrt(from.distSqr(here))));
        //1.0.4-beta (lots 2 et 27) : deux poulies a axe vertical : la corde fait partie d'une ligne d'entrepot si les colis peuvent la suivre
        if (first.lineNode() && pulley.lineNode() && !crossed) {
            BlockPos hit = WarehouseLines.alongRope(level, from, here, type);
            if (hit == null) {
                hit = WarehouseLines.underWheel(level, from);
            }
            if (hit == null) {
                hit = WarehouseLines.underWheel(level, here);
            }
            done = hit != null ? done.copy().append(" · ").append(Component.translatable("warehouse.pioneers.rope.no_parcels", hit.getX(), hit.getY(),
                  hit.getZ()).withStyle(ChatFormatting.YELLOW)) : WarehouseRopes.done(level, from, here, done);
        }
        player.displayClientMessage(done, true);
        return InteractionResult.CONSUME;
    }

    /** Raison du refus d'une corde droite entre deux poulies, ou null (aussi pour l'apercu de pose, cote client). */
    @Nullable
    public static Component check(Level level, BlockPos a, BlockPos b) {
        return check(level, a, b, false);
    }

    /** Raison du refus d'une corde (droite ou croisee) entre deux poulies, ou null (aussi pour l'apercu de pose, cote client). */
    @Nullable
    public static Component check(Level level, BlockPos a, BlockPos b, boolean crossed) {
        if (a.equals(b)) {
            return Component.translatable("kinetics.pioneers.rope.same");
        }
        if (!(level.getBlockEntity(a) instanceof PulleyTile pa) || !(level.getBlockEntity(b) instanceof PulleyTile pb)) {
            return Component.translatable("kinetics.pioneers.rope.gone");
        }
        if (pa.linkedTo(b)) {
            return Component.translatable("warehouse.pioneers.rope.already");
        }
        if (!pa.hasRoom() || !pb.hasRoom()) {
            return Component.translatable("kinetics.pioneers.rope.busy", pa.capacity());
        }
        if (pa.axis() != pb.axis()) {
            return Component.translatable("kinetics.pioneers.rope.axis");
        }
        //1.0.5-beta (lot 15) : poulies decalees le long de leur axe permises (RopeRules : 2 blocs au plus, moitie de l'ecart dans le plan)
        int dx = b.getX() - a.getX(), dy = b.getY() - a.getY(), dz = b.getZ() - a.getZ();
        if (!RopeRules.offsetAllowed(RopeRules.along(pa.axis(), dx, dy, dz), RopeRules.inPlane(pa.axis(), dx, dy, dz))) {
            return Component.translatable("kinetics.pioneers.rope.plane", RopeRules.MAX_OFFSET);
        }
        double distance = Math.sqrt(a.distSqr(b));
        if (distance > KineticsConfig.ropeRange()) {
            return Component.translatable("kinetics.pioneers.rope.far", KineticsConfig.ropeRange());
        }
        Vec3 from = Vec3.atCenterOf(a), to = Vec3.atCenterOf(b);
        Vec3 dir = to.subtract(from).normalize();
        BlockHitResult hit = level.clip(new ClipContext(from.add(dir.scale(0.8)), to.subtract(dir.scale(0.8)), ClipContext.Block.COLLIDER,
              ClipContext.Fluid.NONE, null));
        if (hit.getType() == HitResult.Type.BLOCK && !hit.getBlockPos().equals(a) && !hit.getBlockPos().equals(b)) {
            return Component.translatable("kinetics.pioneers.rope.blocked");
        }
        return directionProblem(level, pa, pb, crossed);
    }

    /**
     * 1.0.4-beta (lot 27) : la corde a -> b (rapport r = +-taille_a / taille_b, negatif si croisee) est-elle compatible avec le sens de
     * rotation des systemes deja relies ?
     * <ul>
     *     <li>meme reseau (une autre transmission les relie deja, serveur) : le rapport existant doit etre r (sens ET valeur), sinon la corde
     *     bloquerait le reseau (« deux chemins, deux rapports ») ;</li>
     *     <li>deux systemes qui tournent (vitesses lues sur les poulies, aussi au client pour l'apercu) : la vitesse de b doit avoir le signe
     *     de r x vitesse de a, sinon ils tournent en sens contraire ;</li>
     *     <li>un des deux a l'arret : rien a verifier (il sera entraine).</li>
     * </ul>
     * Renvoie le message du refus, ou null.
     */
    @Nullable
    public static Component directionProblem(Level level, PulleyTile pa, PulleyTile pb, boolean crossed) {
        double ratio = (crossed ? -1 : 1) * pa.size() / (double) pb.size();
        if (!level.isClientSide) {
            KineticNetwork na = KineticNetworks.network(level, pa.getBlockPos()), nb = KineticNetworks.network(level, pb.getBlockPos());
            if (na != null && na == nb) {
                double ea = na.effectiveRatio(pa), eb = na.effectiveRatio(pb);
                if (Math.abs(ea) > 1e-9) {
                    double existing = eb / ea;
                    if (Math.signum(existing) != Math.signum(ratio)) {
                        return Component.translatable("kinetics.pioneers.rope.direction", crossed ? Component.translatable("kinetics.pioneers.rope.hint_straight")
                              : Component.translatable("kinetics.pioneers.rope.hint_crossed"));
                    }
                    if (Math.abs(existing - ratio) > 1e-3 * Math.max(1, Math.abs(ratio))) {
                        return Component.translatable("kinetics.pioneers.rope.ratio");
                    }
                }
                return null;
            }
        }
        double sa = pa.getSpeed(), sb = pb.getSpeed();
        if (Math.abs(sa) > 0.05 && Math.abs(sb) > 0.05 && Math.signum(sb) != Math.signum(ratio * sa)) {
            return Component.translatable("kinetics.pioneers.rope.direction", crossed ? Component.translatable("kinetics.pioneers.rope.hint_straight")
                  : Component.translatable("kinetics.pioneers.rope.hint_crossed"));
        }
        return null;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        tips.desc("item.pioneers.rope.desc", Math.round(type.maxTorque), Math.round(type.efficiency * 100));
        tips.spec("kinetics.pioneers.spec.ropes", PulleyTile.MAX_ROPES);
    }
}
