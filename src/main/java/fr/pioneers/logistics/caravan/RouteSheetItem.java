package fr.pioneers.logistics.caravan;

import fr.pioneers.core.tooltip.Tooltips;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 10) : FEUILLE DE ROUTE d'une caravane. Clic sur un relais : il s'ajoute en derniere etape (proprietaire ou son clan ; meme
 * dimension ; {@code MAX_LEG} blocs au plus de l'etape precedente ; {@code MAX_STOPS} etapes) ; clic en l'air : l'ecran de l'itineraire
 * (ordre, retrait, boucle ou aller-retour : {@link RouteMenu}, {@link PacketCaravanRoute}) ; clic sur la bete de tete : la caravane prend
 * l'itineraire (Maj : elle s'arrete) - {@link Caravans#interact}. NBT {@code route} : {dim, loop, stops[{pos, name}]}.
 */
public class RouteSheetItem extends Item {

    public RouteSheetItem(Properties properties) {
        super(properties);
    }

    /** Itineraire lu dans l'objet. */
    public record Route(String dim, boolean loop, List<BlockPos> stops, List<String> names) {

        public static Route of(ItemStack stack) {
            CompoundTag t = stack.getTagElement("route");
            List<BlockPos> stops = new ArrayList<>();
            List<String> names = new ArrayList<>();
            if (t == null) {
                return new Route("", true, stops, names);
            }
            ListTag ls = t.getList("stops", Tag.TAG_COMPOUND);
            for (int i = 0; i < ls.size(); i++) {
                stops.add(BlockPos.of(ls.getCompound(i).getLong("pos")));
                names.add(ls.getCompound(i).getString("name"));
            }
            return new Route(t.getString("dim"), !t.contains("loop") || t.getBoolean("loop"), stops, names);
        }

        public void write(ItemStack stack) {
            CompoundTag t = new CompoundTag();
            t.putString("dim", dim);
            t.putBoolean("loop", loop);
            ListTag ls = new ListTag();
            for (int i = 0; i < stops.size(); i++) {
                CompoundTag s = new CompoundTag();
                s.putLong("pos", stops.get(i).asLong());
                s.putString("name", names.get(i));
                ls.add(s);
            }
            t.put("stops", ls);
            stack.addTagElement("route", t);
        }

        /** Longueur (blocs, a l'horizontale) de l'etape i-1 -> i, -1 pour la premiere (ou, en boucle, la derniere -> la premiere). */
        public int leg(int i) {
            if (stops.size() < 2) {
                return -1;
            }
            BlockPos a = stops.get(i == 0 ? stops.size() - 1 : i - 1), b = stops.get(i);
            if (i == 0 && !loop) {
                return -1;
            }
            return CaravanRules.legLength(a.getX(), a.getZ(), b.getX(), b.getZ());
        }
    }

    /** Resultats de {@link #addStop}. */
    public static final int ADDED = 0, NOT_YOURS = 1, ALREADY = 2, TOO_FAR = 3, FULL = 4, OTHER_DIM = 5;

    /** Ajoute le relais en derniere etape (sans message) ; code de resultat. */
    public static int addStop(Player player, ItemStack stack, RelayTile relay) {
        if (!Caravans.mayEdit(player, relay.relay().owner)) {
            return NOT_YOURS;
        }
        Route r = Route.of(stack);
        String dim = relay.getLevel() == null ? "" : relay.getLevel().dimension().location().toString();
        if (!r.stops().isEmpty() && !r.dim().equals(dim)) {
            return OTHER_DIM;
        }
        BlockPos p = relay.getBlockPos();
        if (!r.stops().isEmpty() && r.stops().get(r.stops().size() - 1).equals(p)) {
            return ALREADY;
        }
        if (r.stops().size() >= GeneratedCaravan.MAX_STOPS) {
            return FULL;
        }
        if (!r.stops().isEmpty()) {
            BlockPos q = r.stops().get(r.stops().size() - 1);
            if (!CaravanRules.legOk(p.getX(), p.getZ(), q.getX(), q.getZ())) {
                return TOO_FAR;
            }
        }
        r.stops().add(p);
        r.names().add(relay.stopName());
        new Route(dim, r.loop(), r.stops(), r.names()).write(stack);
        return ADDED;
    }

    @NotNull
    @Override
    public InteractionResult useOn(@NotNull UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (!(level.getBlockEntity(ctx.getClickedPos()) instanceof RelayTile relay) || ctx.getPlayer() == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        Player player = ctx.getPlayer();
        ItemStack stack = ctx.getItemInHand();
        int r = addStop(player, stack, relay);
        Route route = Route.of(stack);
        BlockPos q = route.stops().isEmpty() ? relay.getBlockPos() : route.stops().get(route.stops().size() - 1);
        Component msg = switch (r) {
            case ADDED -> Component.translatable("caravan.pioneers.msg.added", route.stops().size(), relay.stopName()).withStyle(ChatFormatting.GREEN);
            case NOT_YOURS -> Component.translatable("caravan.pioneers.msg.not_yours", relay.relay().ownerName).withStyle(ChatFormatting.RED);
            case ALREADY -> Component.translatable("caravan.pioneers.msg.already").withStyle(ChatFormatting.GOLD);
            case FULL -> Component.translatable("caravan.pioneers.msg.full_route").withStyle(ChatFormatting.GOLD);
            case OTHER_DIM -> Component.translatable("caravan.pioneers.msg.other_dim").withStyle(ChatFormatting.RED);
            default -> Component.translatable("caravan.pioneers.msg.too_far", CaravanRules.legLength(q.getX(), q.getZ(),
                  relay.getBlockPos().getX(), relay.getBlockPos().getZ())).withStyle(ChatFormatting.RED);
        };
        player.displayClientMessage(msg, true);
        return InteractionResult.CONSUME;
    }

    @NotNull
    @Override
    public InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            NetworkHooks.openScreen(sp, new SimpleMenuProvider((id, inv, p) -> new RouteMenu(id, inv, hand),
                  Component.translatable("gui.pioneers.route.title")), buf -> buf.writeEnum(hand));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips.of(tooltip).desc("description.pioneers.caravan_route");
        Route r = Route.of(stack);
        if (!r.stops().isEmpty()) {
            tooltip.add(Component.translatable("gui.pioneers.route.stops", r.stops().size()).append(" \u00B7 ")
                  .append(Component.translatable(r.loop() ? "gui.pioneers.route.loop" : "gui.pioneers.route.back_forth")).withStyle(ChatFormatting.GOLD));
        }
    }
}
