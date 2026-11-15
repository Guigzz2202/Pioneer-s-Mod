package fr.pioneers.economy.peddler;

import fr.pioneers.Pioneers;
import fr.pioneers.core.profile.PioneerApi;
import fr.pioneers.core.profile.PioneerCap;
import fr.pioneers.core.profile.PioneerProfile;
import fr.pioneers.economy.EconomyApi;
import fr.pioneers.economy.PriceService;
import fr.pioneers.economy.SpaceGoods;
import fr.pioneers.economy.Trade;
import fr.pioneers.economy.Values;
import fr.pioneers.reputation.PeopleApi;
import fr.pioneers.reputation.ReputationApi;
import fr.pioneers.ruins.sentinel.SentinelEntity;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.animal.horse.TraderLlama;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * 1.9 (MAJ RP, bloc 3) : le Colporteur, marchand ambulant (remplace le marchand errant de Minecraft). Trois tenues ({@link #variant()}),
 * un surnom tire des tables, deux lamas de marchand en laisse. Trois roles : de route (repart au bout de trois jours de jeu), de caravane
 * (repart a la fin de sa caravane), de Halte (reste). Son stock de douze emplacements est tire a l'arrivee ({@code peddler_stock}) et sauve
 * dans l'entite ; il achete presque tout ({@link Values#peddlerBuys}) dans la limite des quotas du jour. Tout passe par l'ecran d'echange
 * commun ({@link Trade}) : le serveur decide de tout. Les emplacements sont choisis par {@link PeddlerService}.
 */
public class PeddlerEntity extends PathfinderMob implements Trade.TradePartner {

    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(PeddlerEntity.class, EntityDataSerializers.INT);
    /** Vrai pendant un echange (le client tourne la tete vers le joueur, sac ouvert). */
    private static final EntityDataAccessor<Boolean> TRADING = SynchedEntityData.defineId(PeddlerEntity.class, EntityDataSerializers.BOOLEAN);

    /** Role : de route, de caravane, de Halte. */
    public enum Role {
        ROUTE, CARAVAN, HALT;

        /** Rayon de promenade autour du point d'ancrage. */
        int radius() {
            return this == ROUTE ? 10 : this == CARAVAN ? 6 : 5;
        }
    }

    /**
     * Categories du stock et emplacements par defaut (si {@code peddler_stock.json} ne les donne pas). MAJ Recettes (lot 9a) : objets de luxe
     * (engrenages, ressorts et anneaux d'or et d'argent).
     */
    static final String[] CATEGORIES = {"resources", "tools", "scraps", "relics", "luxury"};
    static final int[] DEFAULT_SLOTS = {6, 3, 1, 2, 1};
    static final TagKey<Item> SCRAP = TagKey.create(Registries.ITEM, Pioneers.rl("scrap"));
    static final TagKey<Item> SCRAP_MK3 = TagKey.create(Registries.ITEM, Pioneers.rl("scrap/mk3"));
    /** Familles de la table de valeurs qui comptent comme produits agricoles ou animaliers (acte "sell_farm"). */
    static final Set<String> FARM = Set.of("crop", "seed", "meat_raw", "fish", "egg", "wool", "milk", "honey", "leather", "hide", "feather",
          "fertilizer", "manure");
    /** Distance d'echange, de salut, d'annonce du depart. */
    static final double TRADE_REACH = 8, GREET_REACH = 6, SAY_REACH = 16;

    /** Un emplacement du stock : lot (objet et quantite par lot), lots restants. */
    static final class Lot {

        final ItemStack stack;
        int lots;

        Lot(ItemStack stack, int lots) {
            this.stack = stack;
            this.lots = lots;
        }
    }

    private Role role = Role.ROUTE;
    /** Village (caravane) ou Halte. */
    private long group;
    /** Instant (temps de jeu) de l'arrivee ou du debut de la caravane. */
    private long since;
    /** Instant du depart (-1 : jamais). */
    private long leaveAt = -1;
    private int nameIndex = -1;
    @Nullable
    private UUID target;
    private BlockPos anchor = BlockPos.ZERO;
    private final List<Lot> stock = new ArrayList<>();
    private long stockDay = -1;
    private long quotaDay = -1;
    private final Map<Item, Integer> bought = new HashMap<>();
    private final List<UUID> llamas = new ArrayList<>();
    private final Set<UUID> traders = new HashSet<>();
    private final Set<UUID> greeted = new HashSet<>();
    private boolean departing;

    public PeddlerEntity(EntityType<? extends PeddlerEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(VARIANT, 0);
        entityData.define(TRADING, false);
    }

    /** Tenue 0..2 (texture {@code textures/entity/peddler/peddler_<n>.png}). */
    public int variant() {
        return Math.floorMod(entityData.get(VARIANT), 3);
    }

    public void setVariant(int v) {
        entityData.set(VARIANT, Math.floorMod(v, 3));
    }

    public boolean isTrading() {
        return entityData.get(TRADING);
    }

    public void setTrading(boolean trading) {
        entityData.set(TRADING, trading);
    }

    public Role role() {
        return role;
    }

    public long group() {
        return group;
    }

    public long since() {
        return since;
    }

    @Nullable
    public UUID target() {
        return target;
    }

    public List<UUID> llamas() {
        return llamas;
    }

    // ------------------------------------------------------------------ mise en place
    /**
     * Installe le Colporteur (appele par {@link PeddlerService} avant de l'ajouter au monde) : role, groupe, ancrage, depart, joueur
     * vise ; surnom, tenue et stock tires.
     */
    public void setup(Role role, long group, BlockPos anchor, long since, long leaveAt, @Nullable UUID target) {
        this.role = role;
        this.group = group;
        this.anchor = anchor.immutable();
        this.since = since;
        this.leaveAt = leaveAt;
        this.target = target;
        restrictTo(this.anchor, role.radius());
        if (nameIndex < 0) {
            nameIndex = random.nextInt(Math.max(1, PeddlerLines.NAMES));
            setCustomName(Component.translatable("peddler.pioneers.name." + nameIndex));
        }
        setVariant(random.nextInt(3));
        setPersistenceRequired();
        rollStock();
    }

    /** Tire le stock : pour chaque categorie, ses emplacements au poids, sans remise ; lots d'au moins un jeton. */
    void rollStock() {
        stock.clear();
        int total = 0;
        for (String c : CATEGORIES) {
            total += Values.peddlerSlots(c);
        }
        Random rnd = new Random(random.nextLong());
        for (int c = 0; c < CATEGORIES.length; c++) {
            String cat = CATEGORIES[c];
            int slots = total > 0 ? Values.peddlerSlots(cat) : DEFAULT_SLOTS[c];
            boolean scraps = "scraps".equals(cat);
            List<PeddlerRules.Weighted<Item>> pool = new ArrayList<>();
            for (Values.Stock s : Values.peddlerStock(cat)) {
                if (forSale(s.item())) {
                    pool.add(new PeddlerRules.Weighted<>(s.item(), s.weight(), s.min(), s.max()));
                }
            }
            for (PeddlerRules.Pick<Item> p : PeddlerRules.draw(pool, slots, scraps ? PeddlerRules.MAX_SCRAPS : 0, rnd)) {
                ItemStack one = new ItemStack(p.value());
                int lot = scraps ? 1 : PeddlerRules.lotSize(Values.value(one), one.getMaxStackSize());
                stock.add(new Lot(one.copyWithCount(lot), PeddlerRules.lots(p.count(), lot)));
            }
        }
        stockDay = PioneerApi.day(level());
    }

    /** Vendable par un Colporteur : une valeur, jamais un composant du catalogue ni un scrap MkIII ; 1.0.4-beta (lot 4) : ni un objet de l'espace. */
    static boolean forSale(Item item) {
        ItemStack s = new ItemStack(item);
        Values.Entry e = Values.entry(s);
        return e != null && !"component".equals(e.quota()) && !"scrap_mk3".equals(e.quota()) && !s.is(SCRAP_MK3) && !SpaceGoods.is(s);
    }

    // ------------------------------------------------------------------ IA
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new PanicGoal(this, 0.55));
        goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Monster.class, 10.0F, 0.5, 0.55));
        goalSelector.addGoal(1, new AvoidEntityGoal<>(this, SentinelEntity.class, 12.0F, 0.5, 0.55));
        goalSelector.addGoal(2, new TradeGoal(this));
        goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 0.4));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.35));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
    }

    /** Pendant un echange : il s'arrete et regarde le joueur (la fuite devant un monstre ou une Sentinelle passe avant). */
    static final class TradeGoal extends Goal {

        private final PeddlerEntity peddler;

        TradeGoal(PeddlerEntity peddler) {
            this.peddler = peddler;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return peddler.isTrading() && peddler.isAlive() && !peddler.isInWater();
        }

        @Override
        public void start() {
            peddler.getNavigation().stop();
        }

        @Override
        public void tick() {
            Player p = peddler.level().getNearestPlayer(peddler, TRADE_REACH);
            if (p != null) {
                peddler.getLookControl().setLookAt(p, 30.0F, 30.0F);
            }
        }
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean canBeLeashed(@NotNull Player player) {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return isTrading() ? SoundEvents.WANDERING_TRADER_TRADE : SoundEvents.WANDERING_TRADER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(@NotNull DamageSource source) {
        return SoundEvents.WANDERING_TRADER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WANDERING_TRADER_DEATH;
    }

    // ------------------------------------------------------------------ comptes espaces (jamais de calcul par tick)
    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        int phase = (tickCount + getId()) % 100;
        if (phase % 20 != 0 || !(level() instanceof ServerLevel sl)) {
            return;
        }
        checkTraders(sl);
        if (phase % 40 == 0) {
            greet(sl);
        }
        if (phase == 0) {
            lifecycle(sl);
        }
    }

    /** Oublie les joueurs dont la session est fermee ; ferme celle d'un joueur parti trop loin. */
    private void checkTraders(ServerLevel sl) {
        for (UUID u : List.copyOf(traders)) {
            ServerPlayer p = sl.getServer().getPlayerList().getPlayer(u);
            if (p == null || Trade.session(p) != this) {
                traders.remove(u);
            } else if (!valid(p)) {
                Trade.close(p);
                traders.remove(u);
            }
        }
        if (traders.isEmpty() && isTrading()) {
            setTrading(false);
        }
    }

    /** Salue une fois chaque joueur qui s'approche (bonjour du peuple : "Bon chemin."). */
    private void greet(ServerLevel sl) {
        if (!EconomyApi.enabled()) {
            return;
        }
        for (ServerPlayer p : sl.players()) {
            if (!p.isSpectator() && p.distanceToSqr(this) <= GREET_REACH * GREET_REACH && greeted.add(p.getUUID())
                  && !ReputationApi.tier(p, PeopleApi.PEDDLERS).refuse()) {
                String line = voice("greet");
                if (line != null) {
                    p.displayClientMessage(say(line), false);
                }
            }
        }
    }

    /** Mise en place par defaut (invocation par commande), stock renouvele (Halte), depart. */
    private void lifecycle(ServerLevel sl) {
        long now = sl.getGameTime();
        if (nameIndex < 0) {
            setup(Role.ROUTE, 0, blockPosition(), now, now + PeddlerService.stayTicks(), null);
            PeddlerService.spawnLlamas(sl, this);
        }
        long day = PioneerApi.day(sl);
        boolean idle = traders.isEmpty();
        if (stockDay < 0 || idle && stock.isEmpty() && Values.size() > 0
              || role == Role.HALT && idle && day - stockDay >= PeddlerService.stayDays()) {
            rollStock();
        }
        if (!idle || departing) {
            return;
        }
        if (!EconomyApi.enabled()) {
            depart(sl);
            return;
        }
        boolean due = switch (role) {
            case ROUTE -> leaveAt >= 0 && now >= leaveAt;
            case CARAVAN -> now >= leaveAt || !PeddlerService.caravanActive(sl, group, since);
            case HALT -> !PeddlerService.haltKeeps(sl, group, getUUID());
        };
        if (due) {
            depart(sl);
        }
    }

    /** Ligne dite par le Colporteur : "Surnom : texte". */
    Component say(String lineKey) {
        return Component.translatable("peddler.pioneers.say", getDisplayName(), Component.translatable(lineKey));
    }

    /** Depart propre : "Bon chemin." aux joueurs proches, un nuage, les lamas retires avec lui. */
    public void depart(ServerLevel sl) {
        if (departing) {
            return;
        }
        departing = true;
        for (ServerPlayer p : sl.players()) {
            if (p.distanceToSqr(this) <= SAY_REACH * SAY_REACH) {
                p.displayClientMessage(say("peddler.pioneers.voice.leave"), false);
            }
        }
        sl.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.8, getZ(), 14, 0.4, 0.6, 0.4, 0.02);
        for (Mob llama : llamaEntities(sl)) {
            sl.sendParticles(ParticleTypes.POOF, llama.getX(), llama.getY() + 0.8, llama.getZ(), 10, 0.5, 0.6, 0.5, 0.02);
            llama.dropLeash(true, false);
            llama.discard();
        }
        llamas.clear();
        PeddlerService.departed(sl, this);
        discard();
    }

    /** Lamas du Colporteur charges : par identifiant, et tout lama de marchand proche qu'il tient en laisse. */
    List<Mob> llamaEntities(ServerLevel sl) {
        Set<Mob> out = new LinkedHashSet<>();
        for (UUID u : llamas) {
            if (sl.getEntity(u) instanceof Mob m && m.isAlive()) {
                out.add(m);
            }
        }
        for (TraderLlama l : sl.getEntitiesOfClass(TraderLlama.class, getBoundingBox().inflate(16), l -> l.getLeashHolder() == this)) {
            out.add(l);
        }
        return new ArrayList<>(out);
    }

    void addLlama(UUID id) {
        if (!llamas.contains(id)) {
            llamas.add(id);
        }
    }

    // ------------------------------------------------------------------ mort
    @Override
    public void die(@NotNull DamageSource source) {
        boolean was = dead;
        super.die(source);
        if (was || !dead || !(level() instanceof ServerLevel sl)) {
            return;
        }
        for (Mob llama : llamaEntities(sl)) {
            llama.dropLeash(true, false);
        }
        Entity killer = source.getEntity();
        PeddlerService.killed(sl, this, killer instanceof ServerPlayer p ? p : null);
    }

    // ------------------------------------------------------------------ echange
    @Override
    protected @NotNull InteractionResult mobInteract(@NotNull Player player, @NotNull InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !isAlive() || departing) {
            return InteractionResult.PASS;
        }
        if (level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer sp) {
            Trade.open(sp, this);
            if (Trade.session(sp) == this) {
                traders.add(sp.getUUID());
                setTrading(true);
                getNavigation().stop();
                getLookControl().setLookAt(sp, 30.0F, 30.0F);
            }
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean valid(ServerPlayer player) {
        return isAlive() && !isRemoved() && !departing && player.level() == level() && player.distanceToSqr(this) <= TRADE_REACH * TRADE_REACH;
    }

    @Override
    public Component tradeName() {
        return getDisplayName();
    }

    @Override
    public PriceService.Partner pricing(ServerPlayer player) {
        return new PriceService.Partner(PriceService.Kind.PEDDLER, PeopleApi.PEDDLERS, 0, 1, 1, 1);
    }

    @Override
    @Nullable
    public String refusal(ServerPlayer player) {
        if (!EconomyApi.enabled()) {
            return "economy.pioneers.disabled";
        }
        if (ReputationApi.tier(player, PeopleApi.PEDDLERS).refuse()) {
            String v = voice("refuse");
            return v != null ? v : "economy.pioneers.refused_reputation";
        }
        return null;
    }

    @Override
    public List<Trade.Offer> offers(ServerPlayer player) {
        if (stockDay < 0) {
            rollStock();
        }
        List<Trade.Offer> out = new ArrayList<>();
        for (Lot l : stock) {
            out.add(new Trade.Offer(l.stack.copy(), Math.max(0, l.lots)));
        }
        return out;
    }

    @Override
    public boolean buys(ServerPlayer player, ItemStack stack) {
        return !stack.isEmpty() && Values.peddlerBuys(stack.getItem());
    }

    /** Il achete presque tout : la liste montre ce que le joueur porte et qu'il achete (au plus 64 objets). */
    @Override
    public List<Item> buyList(ServerPlayer player) {
        Set<Item> out = new LinkedHashSet<>();
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize() && out.size() < 64; i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && !s.isDamaged() && !s.hasCustomHoverName() && !s.isEnchanted() && Values.peddlerBuys(s.getItem())) {
                out.add(s.getItem());
            }
        }
        return new ArrayList<>(out);
    }

    private void refreshQuota() {
        long day = PioneerApi.day(level());
        if (quotaDay != day) {
            quotaDay = day;
            bought.clear();
        }
    }

    @Override
    public int boughtToday(Item item) {
        refreshQuota();
        return bought.getOrDefault(item, 0);
    }

    @Override
    public void recordBought(Item item, int count) {
        refreshQuota();
        bought.merge(item, count, Integer::sum);
    }

    @Override
    public void recordSold(int offer, int lots) {
        if (offer >= 0 && offer < stock.size()) {
            Lot l = stock.get(offer);
            l.lots = Math.max(0, l.lots - lots);
        }
    }

    /** Groupe de ce Colporteur pour l'XP de caravane : sa route, sa caravane, ou sa Halte du jour. */
    String tradeGroup() {
        return switch (role) {
            case ROUTE -> "r:" + getStringUUID();
            case CARAVAN -> "c:" + group + ":" + since;
            case HALT -> "h:" + group + ":" + PioneerApi.day(level());
        };
    }

    @Override
    public void traded(ServerPlayer player, PriceService.Side side, ItemStack stack, int count, long total) {
        if (total <= 0 || !(level() instanceof ServerLevel sl)) {
            return;
        }
        //XP "trade.caravan" : une fois par groupe, quand le cumul du joueur atteint le seuil
        String g = tradeGroup();
        long sum = PeddlerData.get(sl).addTotal(g, player.getUUID(), total);
        if (sum >= PeddlerService.caravanTotal()) {
            PioneerProfile p = PioneerCap.get(player);
            if (p.marks.add(PeddlerRules.caravanMark(g))) {
                pruneMarks(p);
                PioneerApi.gain(player, "trade.caravan");
            }
        }
        //actes de reputation : vente de scraps, livraison de produits de la terre ou de l'elevage
        if (side == PriceService.Side.SELL && total >= PeddlerService.actTotal()) {
            if (isScrap(stack)) {
                ReputationApi.applyAct(player, "sell_scraps", null, null);
            } else if (isFarm(stack.getItem())) {
                ReputationApi.applyAct(player, "sell_farm", null, null);
            }
        }
    }

    /** Garde au plus 32 marques de caravane (les plus anciennes sont des groupes partis depuis longtemps). */
    static void pruneMarks(PioneerProfile p) {
        int n = 0;
        for (String m : p.marks) {
            if (m.startsWith("cv:")) {
                n++;
            }
        }
        Iterator<String> it = p.marks.iterator();
        while (n > 32 && it.hasNext()) {
            if (it.next().startsWith("cv:")) {
                it.remove();
                n--;
            }
        }
    }

    static boolean isScrap(ItemStack s) {
        return s.is(SCRAP) || Values.has(s.getItem(), "scrap");
    }

    static boolean isFarm(Item item) {
        for (String f : FARM) {
            if (Values.has(item, f)) {
                return true;
            }
        }
        return false;
    }

    @Override
    @Nullable
    public String voice(String moment) {
        int n = PeddlerLines.voices(moment);
        return n <= 0 ? null : "peddler.pioneers.voice." + moment + "." + random.nextInt(n);
    }

    @Override
    public BlockPos tradePos() {
        return blockPosition();
    }

    @Override
    public void closed(ServerPlayer player) {
        traders.remove(player.getUUID());
        if (traders.isEmpty()) {
            setTrading(false);
        }
    }

    // ------------------------------------------------------------------ NBT
    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Variant", variant());
        tag.putString("Role", role.name());
        tag.putLong("Group", group);
        tag.putLong("Since", since);
        tag.putLong("LeaveAt", leaveAt);
        tag.putInt("NameIndex", nameIndex);
        if (target != null) {
            tag.putUUID("Target", target);
        }
        tag.putLong("Anchor", anchor.asLong());
        ListTag st = new ListTag();
        for (Lot l : stock) {
            CompoundTag t = new CompoundTag();
            t.put("s", l.stack.save(new CompoundTag()));
            t.putInt("n", l.lots);
            st.add(t);
        }
        tag.put("Stock", st);
        tag.putLong("StockDay", stockDay);
        tag.putLong("QuotaDay", quotaDay);
        CompoundTag b = new CompoundTag();
        bought.forEach((item, n) -> b.putInt(BuiltInRegistries.ITEM.getKey(item).toString(), n));
        tag.put("Bought", b);
        ListTag ll = new ListTag();
        for (UUID u : llamas) {
            CompoundTag t = new CompoundTag();
            t.putUUID("u", u);
            ll.add(t);
        }
        tag.put("Llamas", ll);
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setVariant(tag.getInt("Variant"));
        try {
            role = Role.valueOf(tag.getString("Role"));
        } catch (IllegalArgumentException ex) {
            role = Role.ROUTE;
        }
        group = tag.getLong("Group");
        since = tag.getLong("Since");
        leaveAt = tag.contains("LeaveAt") ? tag.getLong("LeaveAt") : -1;
        nameIndex = tag.contains("NameIndex") ? tag.getInt("NameIndex") : -1;
        target = tag.hasUUID("Target") ? tag.getUUID("Target") : null;
        anchor = tag.contains("Anchor") ? BlockPos.of(tag.getLong("Anchor")) : blockPosition();
        restrictTo(anchor, role.radius());
        stock.clear();
        ListTag st = tag.getList("Stock", Tag.TAG_COMPOUND);
        for (int i = 0; i < st.size(); i++) {
            CompoundTag t = st.getCompound(i);
            ItemStack s = ItemStack.of(t.getCompound("s"));
            if (!s.isEmpty()) {
                stock.add(new Lot(s, t.getInt("n")));
            }
        }
        stockDay = tag.contains("StockDay") ? tag.getLong("StockDay") : -1;
        quotaDay = tag.contains("QuotaDay") ? tag.getLong("QuotaDay") : -1;
        bought.clear();
        CompoundTag b = tag.getCompound("Bought");
        for (String k : b.getAllKeys()) {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(k));
            if (item != Items.AIR) {
                bought.put(item, b.getInt(k));
            }
        }
        llamas.clear();
        ListTag ll = tag.getList("Llamas", Tag.TAG_COMPOUND);
        for (int i = 0; i < ll.size(); i++) {
            llamas.add(ll.getCompound(i).getUUID("u"));
        }
    }
}
