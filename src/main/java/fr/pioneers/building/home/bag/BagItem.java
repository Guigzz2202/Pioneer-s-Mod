package fr.pioneers.building.home.bag;

import fr.pioneers.society.clan.ClanApi;
import fr.pioneers.core.tooltip.Tooltips;
import fr.pioneers.farm.crop.Genes;
import fr.pioneers.farm.crop.GeneticSeedItem;
import fr.pioneers.farm.fish.FishItem;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Patch 1.0.3-beta, lot 7 : un sac ({@link BagKind}). Clic droit : ouvre son inventaire ({@link BagMenu}) ; sac a graines, Maj + clic droit
 * sur une terre labouree : seme la MEILLEURE graine du sac (genes de plus haut total, la graine garde ses genes). Infobulle : cases remplies,
 * puis le contenu resume par sorte (nombre et meilleure stat : etoiles des genes, plus grande taille de poisson).
 */
public class BagItem extends Item {

    public final BagKind kind;

    public BagItem(BagKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    @Override
    public boolean canFitInsideContainerItems() {
        return false;
    }

    @NotNull
    @Override
    public InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            open(sp, hand, stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    public void open(ServerPlayer player, InteractionHand hand, ItemStack stack) {
        if (kind.storage()) {
            //1.0.5-beta (lot 31) : sac de rangement : ecran a cases virtuelles (le registre est migre et ecrit avant l'ouverture)
            BagLedger l = BagStore.read(stack, kind);
            if (l.migrated) {
                BagStore.write(stack, l);
            }
            int tier = l.tier;
            NetworkHooks.openScreen(player, new SimpleMenuProvider((id, inv, p) -> new StorageBagMenu(id, inv, hand, kind), stack.getHoverName()),
                  buf -> {
                      buf.writeEnum(hand);
                      buf.writeByte(kind.ordinal());
                      buf.writeByte(tier);
                  });
            player.level().playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 0.6F, 1.2F);
            return;
        }
        NetworkHooks.openScreen(player, new SimpleMenuProvider((id, inv, p) -> new BagMenu(id, inv, hand, kind), stack.getHoverName()), buf -> {
            buf.writeEnum(hand);
            buf.writeByte(kind.ordinal());
        });
        player.level().playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 0.6F, 1.2F);
    }

    /**
     * 1.0.5-beta (lot 31, retour 42) : USAGE DIRECT des sacs de rangement :
     * <ul>
     *   <li>sac a graines, clic droit sur une terre labouree : seme une zone de 3 x 3 centree sur la case visee (Maj : la seule case visee) avec
     *   la sorte CHOISIE dans le sac (clic droit sur sa case a l'ecran ; sinon la premiere sorte qui se seme), genes les plus forts d'abord ;</li>
     *   <li>sac a pousses, clic droit sur un bloc : pose une pousse de la sorte choisie (meilleurs genes d'abord) ;</li>
     *   <li>Maj + clic droit sur un conteneur (coffre, panier, caisse... : capacite d'objets) : y VIDE le sac ({@link BagDeposit}).</li>
     * </ul>
     * Le panier de peche alimente aussi l'aquarium (clic droit : le poisson choisi), le casier a crabes (appat) et la canne (appat en main
     * secondaire) : voir {@code Aquariums}, {@code TrapBlock}, {@code PiRodItem}.
     */
    @NotNull
    @Override
    public InteractionResult useOn(@NotNull UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (!kind.storage() || player == null) {
            return InteractionResult.PASS;
        }
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        boolean farmland = level.getBlockState(pos).getBlock() instanceof FarmBlock;
        if (kind == BagKind.SEED_BAG && farmland && ctx.getClickedFace() == Direction.UP) {
            if (level.isClientSide) {
                return InteractionResult.SUCCESS;
            }
            return sow(level, player, ctx.getHand(), ctx.getItemInHand(), pos, player.isShiftKeyDown() ? 0 : SOW_RADIUS) > 0 ? InteractionResult.CONSUME
                                                                                                                          : InteractionResult.FAIL;
        }
        if (player.isShiftKeyDown()) {
            BlockEntity be = level.getBlockEntity(pos);
            IItemHandler handler = be == null ? null : be.getCapability(ForgeCapabilities.ITEM_HANDLER, ctx.getClickedFace()).resolve().orElse(null);
            if (handler != null) {
                if (!level.isClientSide) {
                    //protection : le joueur doit pouvoir ouvrir ce conteneur, et le transfert ne franchit pas une frontiere de claim refusee
                    boolean allowed = ClanApi.canModify(player, level, pos, ClanApi.Action.CONTAINER) && ClanApi.canTransfer(level, player.blockPosition(), pos);
                    BagDeposit.tell(player, allowed ? BagDeposit.intoHandler(handler, ctx.getItemInHand(), kind) : 0);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        if (kind == BagKind.SAPLING_BAG && !player.isShiftKeyDown()) {
            if (level.isClientSide) {
                return InteractionResult.SUCCESS;
            }
            BlockHitResult hit = new BlockHitResult(ctx.getClickLocation(), ctx.getClickedFace(), pos, ctx.isInside());
            return plant(level, player, ctx.getHand(), ctx.getItemInHand(), hit) ? InteractionResult.CONSUME : InteractionResult.FAIL;
        }
        return InteractionResult.PASS;
    }

    /** Semis : demi-cote de la zone (1 = 3 x 3). */
    public static final int SOW_RADIUS = 1;

    /** Objet qui se pose (graine, pousse). */
    static boolean plantable(ItemStack s) {
        return !s.isEmpty() && s.getItem() instanceof BlockItem;
    }

    /** Index de la meilleure graine genetique (lecture a l'ancienne, GameTests d'avant) : plus haut total des genes, ou -1. */
    public static int bestSeed(ItemStackHandler h) {
        int best = -1, score = -1;
        for (int i = 0; i < h.getSlots(); i++) {
            ItemStack s = h.getStackInSlot(i);
            if (s.getItem() instanceof GeneticSeedItem) {
                int sc = Genes.of(s).score();
                if (sc > score) {
                    score = sc;
                    best = i;
                }
            }
        }
        return best;
    }

    /**
     * Seme autour de {@code farmland} (serveur ; {@code radius} 0 = une case) la sorte choisie du sac, genes les plus forts d'abord ; rend le
     * nombre de graines posees. Une case n'est semee que si c'est une terre labouree au-dessus de laquelle la graine se pose (place libre).
     */
    public static int sow(Level level, Player player, InteractionHand hand, ItemStack bag, BlockPos farmland, int radius) {
        BagLedger l = BagStore.read(bag, BagKind.SEED_BAG);
        int i = l.selectedIndex(e -> plantable(BagStore.display(l, e)));
        if (i < 0) {
            player.displayClientMessage(Component.translatable("home.pioneers.bag.no_seed").withStyle(ChatFormatting.YELLOW), true);
            return 0;
        }
        String id = l.entries.get(i).id;
        int cases = BagKind.SEED_BAG.cases(l.tier), planted = 0;
        for (int ring = 0; ring <= radius; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    BlockPos p = farmland.offset(dx, 0, dz);
                    if (!(level.getBlockState(p).getBlock() instanceof FarmBlock) || !level.getBlockState(p.above()).canBeReplaced()) {
                        continue;
                    }
                    int idx = StorageBagMenu.resolve(l, i, id);
                    if (idx < 0) {
                        break;
                    }
                    i = idx;
                    BagLedger.Taken t = l.take(idx, 1);
                    ItemStack one = BagStore.toStack(t);
                    if (one.isEmpty()) {
                        l.giveBack(t, t.count(), cases);
                        continue;
                    }
                    BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(p).add(0, 0.5, 0), Direction.UP, p, false);
                    InteractionResult r = ((BlockItem) one.getItem()).place(new BlockPlaceContext(level, player, hand, one, hit));
                    if (r.consumesAction()) {
                        planted++;
                        if (player.getAbilities().instabuild) {
                            l.giveBack(t, 1, cases);
                        }
                    } else {
                        l.giveBack(t, 1, cases);
                    }
                }
            }
        }
        BagStore.write(bag, l);
        if (planted == 0) {
            player.displayClientMessage(Component.translatable("home.pioneers.bag.no_room").withStyle(ChatFormatting.YELLOW), true);
        }
        return planted;
    }

    /** Pose une pousse de la sorte choisie du sac a pousses (serveur) ; vrai si elle est posee. */
    public static boolean plant(Level level, Player player, InteractionHand hand, ItemStack bag, BlockHitResult hit) {
        BagLedger l = BagStore.read(bag, BagKind.SAPLING_BAG);
        int i = l.selectedIndex(e -> plantable(BagStore.display(l, e)));
        if (i < 0) {
            player.displayClientMessage(Component.translatable("home.pioneers.bag.no_sapling").withStyle(ChatFormatting.YELLOW), true);
            return false;
        }
        int cases = BagKind.SAPLING_BAG.cases(l.tier);
        BagLedger.Taken t = l.take(i, 1);
        ItemStack one = BagStore.toStack(t);
        if (one.isEmpty()) {
            l.giveBack(t, t.count(), cases);
            return false;
        }
        boolean ok = ((BlockItem) one.getItem()).place(new BlockPlaceContext(level, player, hand, one, hit)).consumesAction();
        if (!ok || player.getAbilities().instabuild) {
            l.giveBack(t, 1, cases);
        }
        BagStore.write(bag, l);
        return ok;
    }

    // ------------------------------------------------------------------ infobulle
    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        tips.desc("item.pioneers." + kind.id + ".desc");
        if (kind.storage()) {
            storageTooltip(stack, tips);
            return;
        }
        ItemStackHandler h = BagContents.read(stack, kind);
        int used = 0;
        for (int i = 0; i < h.getSlots(); i++) {
            if (!h.getStackInSlot(i).isEmpty()) {
                used++;
            }
        }
        tips.spec("home.pioneers.bag.slots", used, kind.slots);
        for (Component line : summary(h, 6)) {
            tips.custom(line);
        }
    }

    /** 1.0.5-beta (lot 31) : infobulle d'un sac de rangement : niveau et capacite, contenu resume, choix, ramassage, gestes. */
    void storageTooltip(ItemStack stack, Tooltips tips) {
        BagLedger l = BagStore.read(stack, kind);
        int cases = kind.cases(l.tier), per = l.perCase();
        tips.spec("home.pioneers.bag.tier", ROMAN[l.tier], cases, per);
        tips.spec("home.pioneers.bag.content", l.total(), (long) cases * per, l.entries.size());
        for (Component line : ledgerSummary(l, 6)) {
            tips.custom(line);
        }
        if (!l.selected.isEmpty()) {
            Item sel = BagStore.item(l.selected);
            if (sel != null) {
                tips.state("home.pioneers.bag.selected", Component.translatable(sel.getDescriptionId()));
            }
        }
        tips.note(l.pickup ? "home.pioneers.bag.pickup" : "home.pioneers.bag.pickup_off");
        if (!l.filter.isEmpty()) {
            tips.note("home.pioneers.bag.filtered", l.filter.size());
        }
        tips.hint("home.pioneers.bag.open");
        if (kind == BagKind.SEED_BAG) {
            tips.hint("home.pioneers.bag.sow");
        } else if (kind == BagKind.SAPLING_BAG) {
            tips.hint("home.pioneers.bag.plant");
        } else {
            tips.hint("home.pioneers.bag.fish_use");
        }
        tips.hint("home.pioneers.bag.dump");
    }

    public static final String[] ROMAN = {"I", "II", "III"};

    /** Contenu du registre resume : les sortes les plus nombreuses, « 312 × Ble · ★★★ » (meilleures etoiles), « 3 × Truite · 54 cm ». */
    public static List<Component> ledgerSummary(BagLedger l, int max) {
        List<BagLedger.Entry> sorted = new ArrayList<>(l.entries);
        sorted.sort((a, b) -> Integer.compare(b.total, a.total));
        List<Component> out = new ArrayList<>();
        for (BagLedger.Entry e : sorted) {
            if (out.size() >= max) {
                out.add(Component.translatable("home.pioneers.bag.more", sorted.size() - max).withStyle(ChatFormatting.DARK_GRAY));
                break;
            }
            Item it = BagStore.item(e.id);
            MutableComponent line = Component.literal(e.total + " × ").withStyle(ChatFormatting.GRAY)
                  .append((it == null ? Component.literal(e.id) : Component.translatable(it.getDescriptionId())).withStyle(ChatFormatting.WHITE));
            if (l.mode == BagLedger.Mode.GENES && it instanceof GeneticSeedItem) {
                line.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY)).append(GeneticSeedItem.stars(BagLedger.stars(e.best())));
            }
            if (l.mode == BagLedger.Mode.SIZE && e.size > 0) {
                line.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                      .append(Component.translatable("home.pioneers.bag.biggest", e.size).withStyle(ChatFormatting.AQUA));
            }
            out.add(line);
        }
        return out;
    }

    /** Contenu resume par sorte d'objet : « 12 × Ble · ★★★ » (meilleures etoiles), « 3 × Truite · 54 cm » (plus grand) ; au plus {@code max} lignes. */
    public static List<Component> summary(ItemStackHandler h, int max) {
        Map<Item, int[]> sorts = new LinkedHashMap<>();
        Map<Item, ItemStack> first = new LinkedHashMap<>();
        for (int i = 0; i < h.getSlots(); i++) {
            ItemStack s = h.getStackInSlot(i);
            if (s.isEmpty()) {
                continue;
            }
            int[] v = sorts.computeIfAbsent(s.getItem(), k -> new int[]{0, -1, -1});
            first.putIfAbsent(s.getItem(), s);
            v[0] += s.getCount();
            if (Genes.has(s) || s.getItem() instanceof GeneticSeedItem) {
                v[1] = Math.max(v[1], Genes.of(s).stars());
            }
            if (s.getItem() instanceof FishItem && s.hasTag() && s.getTag().contains("Size")) {
                v[2] = Math.max(v[2], s.getTag().getInt("Size"));
            }
        }
        List<Component> out = new ArrayList<>();
        for (Map.Entry<Item, int[]> e : sorts.entrySet()) {
            if (out.size() >= max) {
                out.add(Component.translatable("home.pioneers.bag.more", sorts.size() - max).withStyle(ChatFormatting.DARK_GRAY));
                break;
            }
            int[] v = e.getValue();
            MutableComponent line = Component.literal(v[0] + " × ").withStyle(ChatFormatting.GRAY)
                  .append(Component.translatable(first.get(e.getKey()).getDescriptionId()).withStyle(ChatFormatting.WHITE));
            if (v[1] > 0) {
                line.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY)).append(GeneticSeedItem.stars(v[1]));
            }
            if (v[2] > 0) {
                line.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                      .append(Component.translatable("home.pioneers.bag.biggest", v[2]).withStyle(ChatFormatting.AQUA));
            }
            out.add(line);
        }
        return out;
    }

    @Override
    public boolean isBarVisible(@NotNull ItemStack stack) {
        return stack.hasTag() && (stack.getTag().contains(BagContents.TAG) || stack.getTag().contains(BagLedger.TAG));
    }

    /** Barre de remplissage (cases occupees ; sac de rangement : objets / capacite, lu sans tout relire). */
    @Override
    public int getBarWidth(@NotNull ItemStack stack) {
        if (kind.storage()) {
            int[] q = BagLedger.quick(stack.getTag());
            long cap = (long) kind.cases(q[1]) * BagLedger.capacity(q[1]);
            return (int) Math.min(13, Math.max(q[0] > 0 ? 1 : 0, Math.round(13.0 * q[0] / cap)));
        }
        ItemStackHandler h = BagContents.read(stack, kind);
        int used = 0;
        for (int i = 0; i < h.getSlots(); i++) {
            if (!h.getStackInSlot(i).isEmpty()) {
                used++;
            }
        }
        return Math.round(13F * used / kind.slots);
    }

    @Override
    public int getBarColor(@NotNull ItemStack stack) {
        return 0xC89048;
    }
}
