package fr.pioneers.core.profile;

import fr.pioneers.Pioneers;
import fr.pioneers.core.common.PiCommonConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.9 (MAJ RP, bloc 2) : reconversion de vocation au Pupitre, tres chere. Cout materiel (Carnet vierge, scraps du palier III repartis sur
 * plusieurs categories, composants du plus haut age atteint) ou, quand l'economie est active, Carnet vierge + jetons ; -25 % d'XP sur les deux
 * skills de l'ancienne vocation ; 30 jours de jeu entre deux reconversions. Ceremonie de 10 s : s'eloigner ou etre blesse annule tout,
 * rien n'est consomme. Le serveur verifie tout puis retire tout en une fois (ou rien).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class Reconversion {

    public static final int CEREMONY_TICKS = 200;
    public static final double LEAVE = 4.0;

    /** Cout lu dans {@code pioneer/reconversion.json} par {@link PioneerDefsLoader} (age 0 = Bronze ou avant, 1 = MKI ... 4 = MKIV). */
    public record Cost(String notebook, int notebooks, String scrapTag, int scraps, int categories, List<String> categoryTags,
                       List<String> componentsByAge, int components, List<String> ageMarkers) {
    }

    static volatile Cost cost = new Cost("pioneers:blank_notebook", 1, "pioneers:scrap/mk3", 16, 4, List.of(), List.of(), 4, List.of());

    private record Ceremony(BlockPos desk, String vocation, int startTick) {
    }

    private static final Map<UUID, Ceremony> ACTIVE = new HashMap<>();

    /** Paiement en jetons (branche par l'economie, bloc 3) : renvoie vrai si le debit a eu lieu. */
    public interface TokenPayer {

        long balance(ServerPlayer player);

        boolean debit(ServerPlayer player, long amount, String reason);
    }

    @Nullable
    public static TokenPayer tokens;

    private Reconversion() {
    }

    static {
        RpNet.onServer(RpActions.RECONVERT, Reconversion::request);
        RpNet.onServer(RpActions.CANCEL_CEREMONY, (p, t) -> cancel(p, true));
    }

    static void init() {
    }

    // ------------------------------------------------------------------ cout
    static boolean economy() {
        return tokens != null && PioneerApi.hasFeature("economy");
    }

    /** Age le plus avance atteint par le joueur : circuits fabriques ou ramasses (0 = Bronze ou avant, 1..4 = MKI..MKIV). */
    static int ageReached(ServerPlayer player) {
        List<String> markers = cost.ageMarkers();
        for (int i = markers.size() - 1; i >= 0; i--) {
            Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(markers.get(i)));
            if (player.getStats().getValue(Stats.ITEM_CRAFTED.get(item)) > 0 || player.getStats().getValue(Stats.ITEM_PICKED_UP.get(item)) > 0) {
                return i + 1;
            }
        }
        return 0;
    }

    private record Line(String id, Predicate<ItemStack> test, int count, int have) {
    }

    private static Predicate<ItemStack> matcher(String id) {
        if (id.startsWith("#")) {
            TagKey<Item> tag = TagKey.create(Registries.ITEM, new ResourceLocation(id.substring(1)));
            return s -> s.is(tag);
        }
        Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(id));
        return s -> s.is(item);
    }

    private static int count(Inventory inv, Predicate<ItemStack> test) {
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && test.test(s)) {
                n += s.getCount();
            }
        }
        return n;
    }

    private static List<Line> lines(ServerPlayer player) {
        Cost c = cost;
        Inventory inv = player.getInventory();
        List<Line> out = new ArrayList<>();
        Predicate<ItemStack> nb = matcher(c.notebook());
        out.add(new Line(c.notebook(), nb, c.notebooks(), count(inv, nb)));
        if (!economy()) {
            Predicate<ItemStack> sc = matcher("#" + c.scrapTag());
            out.add(new Line("#" + c.scrapTag(), sc, c.scraps(), count(inv, sc)));
            int age = Math.min(ageReached(player), c.componentsByAge().size() - 1);
            if (age >= 0 && !c.componentsByAge().isEmpty()) {
                String comp = c.componentsByAge().get(age);
                Predicate<ItemStack> cp = matcher(comp);
                out.add(new Line(comp, cp, c.components(), count(inv, cp)));
            }
        }
        return out;
    }

    /** Nombre de categories de scraps distinctes dans l'inventaire (parmi les scraps du palier demande). */
    private static int categories(ServerPlayer player) {
        Set<String> seen = new HashSet<>();
        TagKey<Item> tier = TagKey.create(Registries.ITEM, new ResourceLocation(cost.scrapTag()));
        for (ItemStack s : player.getInventory().items) {
            if (s.is(tier)) {
                for (String cat : cost.categoryTags()) {
                    if (s.is(TagKey.create(Registries.ITEM, new ResourceLocation(cat)))) {
                        seen.add(cat);
                    }
                }
            }
        }
        return seen.size();
    }

    static long cooldownLeft(ServerPlayer player) {
        PioneerProfile p = PioneerCap.get(player);
        if (p.lastReconversion < 0) {
            return 0;
        }
        long wait = PiCommonConfig.get(RpConfig.RECONVERSION_DAYS, 30);
        return Math.max(0, p.lastReconversion + wait - PioneerApi.day(player.getServer()));
    }

    // ------------------------------------------------------------------ ecran
    static void open(ServerPlayer player, BlockPos desk) {
        PioneerProfile p = PioneerCap.get(player);
        CompoundTag t = new CompoundTag();
        t.putLong("desk", desk.asLong());
        t.putString("vocation", p.vocation);
        t.putLong("cooldown", cooldownLeft(player));
        ListTag ls = new ListTag();
        for (Line l : lines(player)) {
            CompoundTag c = new CompoundTag();
            c.putString("id", l.id());
            c.putInt("need", l.count());
            c.putInt("have", Math.min(l.have(), l.count()));
            ls.add(c);
        }
        t.put("cost", ls);
        if (!economy()) {
            t.putInt("categories", Math.min(categories(player), cost.categories()));
            t.putInt("categoriesNeed", cost.categories());
        } else {
            long price = PiCommonConfig.get(RpConfig.RECONVERSION_TOKENS, 5000L);
            t.putLong("tokens", price);
            t.putLong("balance", tokens.balance(player));
        }
        RpNet.send(player, RpActions.OPEN_DESK, t);
    }

    private static boolean affordable(ServerPlayer player) {
        for (Line l : lines(player)) {
            if (l.have() < l.count()) {
                return false;
            }
        }
        if (economy()) {
            return tokens.balance(player) >= PiCommonConfig.get(RpConfig.RECONVERSION_TOKENS, 5000L);
        }
        return categories(player) >= cost.categories();
    }

    private static void request(ServerPlayer player, CompoundTag t) {
        BlockPos desk = BlockPos.of(t.getLong("desk"));
        String vocation = t.getString("vocation");
        PioneerProfile p = PioneerCap.get(player);
        BlockState st = player.level().getBlockState(desk);
        if (!(st.getBlock() instanceof RestorerDeskBlock) || player.distanceToSqr(desk.getCenter()) > LEAVE * LEAVE || !t.getBoolean("confirm")
            || !p.initiated || ACTIVE.containsKey(player.getUUID())) {
            return;
        }
        if (vocation.equals(p.vocation) || !ProfileEvents.choosable(vocation) || cooldownLeft(player) > 0 || !affordable(player)) {
            refuse(player);
            return;
        }
        ACTIVE.put(player.getUUID(), new Ceremony(desk.immutable(), vocation, player.getServer().getTickCount()));
        player.level().setBlock(desk, st.setValue(RestorerDeskBlock.CEREMONY, true), 3);
        CompoundTag c = new CompoundTag();
        c.putInt("ticks", CEREMONY_TICKS);
        RpNet.send(player, RpActions.CEREMONY, c);
    }

    private static void refuse(ServerPlayer player) {
        CompoundTag r = new CompoundTag();
        r.putString("kind", "closed");
        RpNet.send(player, RpActions.TOAST, r);
    }

    static void cancel(ServerPlayer player, boolean tell) {
        Ceremony c = ACTIVE.remove(player.getUUID());
        if (c != null) {
            reset(player.serverLevel(), c.desk());
            if (tell) {
                refuse(player);
            }
        }
    }

    private static void reset(ServerLevel level, BlockPos desk) {
        BlockState st = level.getBlockState(desk);
        if (st.getBlock() instanceof RestorerDeskBlock) {
            level.setBlock(desk, st.setValue(RestorerDeskBlock.CEREMONY, false), 3);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty() || event.getServer() == null) {
            return;
        }
        int now = event.getServer().getTickCount();
        for (UUID id : new ArrayList<>(ACTIVE.keySet())) {
            Ceremony c = ACTIVE.get(id);
            ServerPlayer p = event.getServer().getPlayerList().getPlayer(id);
            if (p == null) {
                ACTIVE.remove(id);
                continue;
            }
            if (!(p.level().getBlockState(c.desk()).getBlock() instanceof RestorerDeskBlock) || p.distanceToSqr(c.desk().getCenter()) > LEAVE * LEAVE) {
                cancel(p, true);
            } else if (now - c.startTick() >= CEREMONY_TICKS) {
                ACTIVE.remove(id);
                reset(p.serverLevel(), c.desk());
                complete(p, c.vocation());
            }
        }
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && ACTIVE.containsKey(p.getUUID())) {
            cancel(p, true);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            cancel(p, false);
        }
    }

    /** Fin de la ceremonie : tout est reverifie, puis retire en une seule fois. */
    private static void complete(ServerPlayer player, String vocation) {
        if (cooldownLeft(player) > 0 || !affordable(player) || !ProfileEvents.choosable(vocation)) {
            refuse(player);
            return;
        }
        Inventory inv = player.getInventory();
        if (economy()) {
            long price = PiCommonConfig.get(RpConfig.RECONVERSION_TOKENS, 5000L);
            //Le Carnet vierge d'abord (verifie), puis le debit : si le debit echoue, rien n'est retire
            Line nb = lines(player).get(0);
            if (!tokens.debit(player, price, "reconversion")) {
                refuse(player);
                return;
            }
            take(inv, nb.test(), nb.count());
        } else {
            for (Line l : lines(player)) {
                if (l.id().startsWith("#" + cost.scrapTag())) {
                    takeScraps(player, l.count());
                } else {
                    take(inv, l.test(), l.count());
                }
            }
        }
        PioneerProfile p = PioneerCap.get(player);
        String before = p.vocation;
        PioneerDefs.Vocation old = PioneerDefs.get().vocation(before);
        double penalty = PiCommonConfig.get(RpConfig.RECONVERSION_PENALTY, 0.25);
        if (old != null) {
            for (String s : old.affinities()) {
                p.xp.put(s, p.xp(s) * (1 - penalty));
            }
        }
        long day = PioneerApi.day(player.getServer());
        p.vocation = vocation;
        p.lastReconversion = day;
        p.journal(day, "pioneer.pioneers.journal.reconversion", "#vocation.pioneers." + vocation);
        p.touch();
        MinecraftForge.EVENT_BUS.post(new VocationChangedEvent(player, before, vocation));
        //1.9 (MAJ RP, lot 19) : progres « Tourner la page »
        PioneerApi.advancements(player, p);
        ProfileSync.full(player);
        ProfileSync.identity(player);
        player.level().playSound(null, player.blockPosition(), RpSounds.SEAL.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
        CompoundTag r = new CompoundTag();
        r.putString("kind", "reconverted");
        r.putString("vocation", vocation);
        RpNet.send(player, RpActions.TOAST, r);
    }

    private static void take(Inventory inv, Predicate<ItemStack> test, int count) {
        for (int i = 0; i < inv.getContainerSize() && count > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && test.test(s)) {
                int n = Math.min(count, s.getCount());
                s.shrink(n);
                count -= n;
            }
        }
        inv.setChanged();
    }

    /** Retire les scraps en prenant d'abord une unite de chaque categorie (la repartition demandee reste vraie). */
    private static void takeScraps(ServerPlayer player, int count) {
        Inventory inv = player.getInventory();
        TagKey<Item> tier = TagKey.create(Registries.ITEM, new ResourceLocation(cost.scrapTag()));
        for (String cat : cost.categoryTags()) {
            TagKey<Item> ct = TagKey.create(Registries.ITEM, new ResourceLocation(cat));
            if (count > 0 && count(inv, s -> s.is(tier) && s.is(ct)) > 0) {
                take(inv, s -> s.is(tier) && s.is(ct), 1);
                count--;
            }
        }
        take(inv, s -> s.is(tier), count);
    }
}
