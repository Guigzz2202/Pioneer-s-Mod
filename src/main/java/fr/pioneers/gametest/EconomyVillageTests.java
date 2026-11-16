package fr.pioneers.gametest;

import fr.pioneers.core.profile.PioneerCap;
import fr.pioneers.core.profile.RpActions;
import fr.pioneers.core.profile.RpNet;
import fr.pioneers.society.economy.EconomyApi;
import fr.pioneers.society.economy.PriceService;
import fr.pioneers.society.economy.Trade;
import fr.pioneers.society.economy.Values;
import fr.pioneers.society.reputation.PeopleApi;
import fr.pioneers.society.reputation.ReputationApi;
import fr.pioneers.society.village.VillageData;
import fr.pioneers.society.village.VillagerLore;
import fr.pioneers.society.village.Villagers;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 1.9 (MAJ RP, GameTests) : bloc 3 (economie, villages, reputation) : achat atomique par le vrai traitement du paquet, credit d'un
 * proprietaire hors ligne applique a sa connexion, reputation d'un village = moyenne de son registre (naissance et mort comprises), aucune
 * remontee avec le temps.
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class EconomyVillageTests {

    private EconomyVillageTests() {
    }

    // ================================================================== achat
    /** Partenaire d'echange de banc d'essai (Colporteur sans affinite, peuple des Colporteurs). */
    static final class Stand implements Trade.TradePartner {

        final BlockPos pos;
        final ItemStack lot;
        boolean valid = true;
        int sold, traded;

        Stand(BlockPos pos, ItemStack lot) {
            this.pos = pos;
            this.lot = lot;
        }

        @Override
        public boolean valid(ServerPlayer player) {
            return valid;
        }

        @Override
        public Component tradeName() {
            return Component.literal("GameTest");
        }

        @Override
        public PriceService.Partner pricing(ServerPlayer player) {
            return new PriceService.Partner(PriceService.Kind.PEDDLER, PeopleApi.PEDDLERS, 0, 1, 1, 1);
        }

        @Nullable
        @Override
        public String refusal(ServerPlayer player) {
            return null;
        }

        @Override
        public List<Trade.Offer> offers(ServerPlayer player) {
            return List.of(new Trade.Offer(lot.copy(), -1));
        }

        @Override
        public boolean buys(ServerPlayer player, ItemStack stack) {
            return false;
        }

        @Override
        public List<Item> buyList(ServerPlayer player) {
            return List.of();
        }

        @Override
        public int boughtToday(Item item) {
            return 0;
        }

        @Override
        public void recordBought(Item item, int count) {
        }

        @Override
        public void recordSold(int offer, int lots) {
            sold += lots;
        }

        @Override
        public void traded(ServerPlayer player, PriceService.Side side, ItemStack stack, int count, long total) {
            traded++;
        }

        @Override
        public BlockPos tradePos() {
            return pos;
        }
    }

    /** Un objet qui a une valeur dans {@code values.json}. */
    static Item valued() {
        for (String id : List.of("minecraft:bread", "minecraft:wheat", "minecraft:oak_log", "minecraft:cobblestone", "pioneers:steel_plate",
              "minecraft:iron_ingot")) {
            Item it = BuiltInRegistries.ITEM.get(new ResourceLocation(id));
            if (it != Items.AIR && Values.value(new ItemStack(it)) > 0) {
                return it;
            }
        }
        throw new net.minecraft.gametest.framework.GameTestAssertException("aucun objet de test n'a de valeur (data/pioneers/economy/values.json)");
    }

    static CompoundTag buy(int lots) {
        CompoundTag t = new CompoundTag();
        t.putInt("i", 0);
        t.putInt("n", lots);
        return t;
    }

    static int count(ServerPlayer p, Item it) {
        return p.getInventory().countItem(it);
    }

    /**
     * Achat atomique (vrai traitement du paquet) : refus a chaque etape (solde, place, reputation, partenaire parti) sans rien changer ; sinon
     * le debit exact du devis et les objets exacts, une seule fois.
     */
    @GameTest(template = Gt.SMALL, batch = "pioneers_achat_atomique", timeoutTicks = 60)
    public static void achat_atomique(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Gt.check(EconomyApi.enabled(), "economie coupee (interrupteur economy)");
        ServerPlayer p = s.player("gtbuy", "Acheteuse", new BlockPos(4, 2, 4));
        Item it = valued();
        Stand stand = new Stand(h.absolutePos(new BlockPos(5, 2, 4)), new ItemStack(it, 4));
        Trade.open(p, stand);
        Gt.check(Trade.session(p) == stand, "session d'echange non ouverte");
        PriceService.Quote q = PriceService.quote(p, stand.pricing(p), stand.lot, 8, PriceService.Side.BUY, 0);
        Gt.check(q.ok() && q.total() > 0, "devis refuse : " + q.refusal());
        //1. solde insuffisant
        p.getInventory().clearContent();
        PioneerCap.get(p).balance = q.total() - 1;
        RpNet.dispatch(p, RpActions.TRADE_BUY, buy(2));
        Gt.equal(q.total() - 1, EconomyApi.balance(p), "solde insuffisant : rien n'est debite");
        Gt.equal(0, count(p, it), "solde insuffisant : rien n'est donne");
        Gt.equal(0, stand.sold, "solde insuffisant : stock intact");
        //2. inventaire plein
        PioneerCap.get(p).balance = q.total() * 3;
        Item filler = it == Items.DIRT ? Items.STONE : Items.DIRT;
        for (int i = 0; i < p.getInventory().items.size(); i++) {
            p.getInventory().items.set(i, new ItemStack(filler, 64));
        }
        RpNet.dispatch(p, RpActions.TRADE_BUY, buy(2));
        Gt.equal(q.total() * 3, EconomyApi.balance(p), "sans place : rien n'est debite");
        Gt.equal(0, count(p, it), "sans place : rien n'est donne");
        Gt.equal(0, stand.sold, "sans place : stock intact");
        //3. reputation hostile chez le peuple du partenaire : refus du devis
        p.getInventory().clearContent();
        PioneerCap.get(p).reputation.put(PeopleApi.PEDDLERS, -250);
        RpNet.dispatch(p, RpActions.TRADE_BUY, buy(2));
        Gt.equal(q.total() * 3, EconomyApi.balance(p), "reputation hostile : rien n'est debite");
        Gt.equal(0, count(p, it), "reputation hostile : rien n'est donne");
        PioneerCap.get(p).reputation.put(PeopleApi.PEDDLERS, 0);
        //4. achat reussi : debit exact, objets exacts, une seule fois
        RpNet.dispatch(p, RpActions.TRADE_BUY, buy(2));
        Gt.equal(q.total() * 2, EconomyApi.balance(p), "debit de l'achat");
        Gt.equal(8, count(p, it), "objets achetes");
        Gt.equal(2, stand.sold, "lots vendus");
        Gt.equal(1, stand.traded, "un seul echange");
        //5. partenaire parti : la session se ferme, rien ne change
        stand.valid = false;
        RpNet.dispatch(p, RpActions.TRADE_BUY, buy(2));
        Gt.equal(q.total() * 2, EconomyApi.balance(p), "partenaire parti : rien n'est debite");
        Gt.equal(8, count(p, it), "partenaire parti : rien n'est donne");
        Gt.check(Trade.session(p) == null, "session gardee apres le depart du partenaire");
        s.done();
    }

    // ================================================================== hors ligne
    /** Un credit d'un proprietaire hors ligne (vente d'etal) est mis de cote, puis applique une seule fois a sa connexion. */
    @GameTest(template = Gt.SMALL, batch = "pioneers_credit_hors_ligne", timeoutTicks = 60)
    public static void credit_hors_ligne_applique_a_la_connexion(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Gt.check(EconomyApi.enabled(), "economie coupee (interrupteur economy)");
        BlockPos at = new BlockPos(4, 2, 4);
        ServerPlayer p = s.player("gtoff", "Absente", at);
        UUID id = p.getUUID();
        String account = p.getGameProfile().getName();
        long before = EconomyApi.balance(p);
        Gt.disconnect(s.server, id);
        Gt.check(s.server.getPlayerList().getPlayer(id) == null, "joueur encore connecte");
        EconomyApi.creditLater(s.server, id, 250, EconomyApi.Entry.of("stall_sale"));
        EconomyApi.creditLater(s.server, id, 100, EconomyApi.Entry.of("stall_sale"));
        ServerPlayer q = s.reconnect(id, account, at);
        Gt.equal(before + 350, EconomyApi.balance(q), "credits hors ligne appliques a la connexion");
        CompoundTag last = PioneerCap.get(q).ledger.peekLast();
        Gt.check(last != null && "stall_offline".equals(last.getString("k")) && last.getLong("a") == 350, "ligne du journal du joueur");
        //une deuxieme connexion ne recredite rien
        Gt.disconnect(s.server, id);
        ServerPlayer r = s.reconnect(id, account, at);
        Gt.equal(before + 350, EconomyApi.balance(r), "credit hors ligne applique deux fois");
        s.done();
    }

    // ================================================================== villages
    /** Village de test : cloche, puis {@code n} villageois immobiles identifies (registre du village). */
    static VillageData.Village village(Gt.Scope s, BlockPos bellRel, List<Villager> out, int n) {
        for (int i = 0; i < n; i++) {
            Villager v = s.h.spawnWithNoFreeWill(EntityType.VILLAGER, bellRel.offset(2 + i * 2, 0, 2));
            Villagers.identify(s.level, v);
            out.add(v);
        }
        VillageData.Village village = VillageData.get(s.level).village(s.h.absolutePos(bellRel).asLong());
        Gt.check(village != null, "village de la cloche non cree");
        for (Villager v : out) {
            Gt.equal(village.id, VillagerLore.get(v).village, "villageois rattache a son village");
            Gt.check(village.register.containsKey(v.getUUID()), "villageois absent du registre");
        }
        return village;
    }

    static void bell(Gt.Scope s, BlockPos bellRel) {
        s.h.setBlock(bellRel, Blocks.BELL.defaultBlockState());
        BlockPos abs = s.h.absolutePos(bellRel);
        s.onCleanup(() -> s.level.removeBlock(abs, false));
    }

    /** Moyenne attendue : somme du registre / taille, un villageois qui ne connait pas le joueur compte pour 0. */
    static int mean(VillageData.Village v, UUID player) {
        long sum = 0;
        for (Map<UUID, Integer> m : v.register.values()) {
            sum += m.getOrDefault(player, 0);
        }
        return v.register.isEmpty() ? 0 : (int) Math.round(sum / (double) v.register.size());
    }

    /**
     * Reputation d'un village = moyenne de son registre (jamais une lecture des entites chargees) : coup, naissance (nouveau venu a 0), mort
     * (le defunt quitte le registre), villageois decharge (il compte toujours).
     */
    @GameTest(template = Gt.LARGE, batch = "pioneers_village_moyenne", timeoutTicks = 100)
    public static void reputation_village_moyenne_du_registre(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos bellRel = new BlockPos(10, 2, 10);
        bell(s, bellRel);
        //le point d'interet de la cloche est inscrit par une tache du serveur : on attend avant d'identifier les villageois
        h.runAfterDelay(5, () -> {
            List<Villager> vs = new ArrayList<>();
            VillageData.Village village = village(s, bellRel, vs, 3);
            ServerPlayer p = s.player("gtvil", "Brute", new BlockPos(10, 2, 6));
            UUID me = p.getUUID();
            Gt.equal(0, village.average(me), "inconnu : moyenne nulle");
            vs.get(0).hurt(s.level.damageSources().playerAttack(p), 0.5F);
            Gt.equal(-30, Villagers.affinity(s.level, vs.get(0), me), "affinite du villageois frappe");
            Gt.equal(mean(village, me), village.average(me), "moyenne apres le coup");
            Gt.check(village.average(me) < 0, "la reputation du village n'a pas baisse");
            //naissance : un nouveau venu (petit) entre au registre a 0, la moyenne s'en ressent
            int beforeBirth = village.average(me);
            Villager baby = h.spawnWithNoFreeWill(EntityType.VILLAGER, bellRel.offset(-3, 0, 2));
            baby.setAge(-24000);
            Villagers.identify(s.level, baby);
            Gt.check(village.register.containsKey(baby.getUUID()), "le nouveau-ne n'est pas au registre");
            Gt.equal(0, village.affinity(baby.getUUID(), me), "le nouveau-ne ne connait pas le joueur");
            Gt.equal(4, village.register.size(), "registre apres la naissance");
            Gt.equal(mean(village, me), village.average(me), "moyenne apres la naissance");
            Gt.check(village.average(me) >= beforeBirth, "la naissance devait diluer la reputation negative");
            //mort (sans joueur) : le defunt quitte le registre
            vs.get(0).kill();
            Gt.check(!village.register.containsKey(vs.get(0).getUUID()), "le defunt est reste au registre");
            Gt.equal(3, village.register.size(), "registre apres la mort");
            Gt.equal(mean(village, me), village.average(me), "moyenne apres la mort");
            //villageois decharge (retire sans mourir) : il compte toujours, la moyenne ne lit pas les entites chargees
            int beforeUnload = village.average(me);
            vs.get(1).discard();
            Gt.equal(3, village.register.size(), "registre apres le dechargement");
            Gt.equal(beforeUnload, village.average(me), "moyenne apres le dechargement");
            s.done();
        });
    }

    /**
     * Aucune remontee dans le temps : 10 jours de jeu plus tard, l'affinite du villageois frappe, la moyenne du village et la reputation du
     * peuple sont inchangees ; tuer un villageois fait chuter tout le village.
     */
    @GameTest(template = Gt.LARGE, batch = "pioneers_village_sans_pardon", timeoutTicks = 400)
    public static void affinite_ne_remonte_jamais_avec_le_temps(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos bellRel = new BlockPos(10, 2, 10);
        bell(s, bellRel);
        h.runAfterDelay(5, () -> {
            List<Villager> vs = new ArrayList<>();
            VillageData.Village village = village(s, bellRel, vs, 3);
            ServerPlayer p = s.player("gtgrudge", "Rancune", new BlockPos(10, 2, 6));
            UUID me = p.getUUID();
            vs.get(0).hurt(s.level.damageSources().playerAttack(p), 0.5F);
            int a0 = Villagers.affinity(s.level, vs.get(0), me), avg = village.average(me);
            int rep = ReputationApi.reputation(p, village.people);
            Gt.check(a0 < 0, "le coup n'a rien change");
            s.level.setDayTime(s.level.getDayTime() + 10L * 24_000L);
            h.runAfterDelay(250, () -> {
                Gt.equal(a0, Villagers.affinity(s.level, vs.get(0), me), "l'affinite est remontee avec le temps");
                Gt.equal(avg, village.average(me), "la reputation du village est remontee avec le temps");
                Gt.equal(rep, ReputationApi.reputation(p, village.people), "la reputation du peuple est remontee avec le temps");
                //meurtre : tout le village chute (registre entier, villageois charges ou non)
                Map<UUID, Map<UUID, Integer>> before = Gt.register(village);
                //mort donnee par le joueur (evenement de mort seul : le coup et ses temoins sont verifies plus haut)
                vs.get(1).die(s.level.damageSources().playerAttack(p));
                Gt.check(!village.register.containsKey(vs.get(1).getUUID()), "le defunt est reste au registre");
                for (UUID v : village.register.keySet()) {
                    int was = before.get(v).getOrDefault(me, 0);
                    Gt.equal(Math.max(-100, was - 100), village.affinity(v, me), "chute de tout le village apres un meurtre");
                }
                s.done();
            });
        });
    }
}
