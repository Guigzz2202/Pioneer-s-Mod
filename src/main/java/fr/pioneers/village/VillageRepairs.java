package fr.pioneers.village;

import com.google.gson.JsonObject;
import fr.pioneers.Pioneers;
import fr.pioneers.core.profile.PioneerApi;
import fr.pioneers.core.profile.RpActions;
import fr.pioneers.core.profile.RpNet;
import fr.pioneers.core.profile.RpSounds;
import fr.pioneers.economy.EconomyApi;
import fr.pioneers.economy.Values;
import fr.pioneers.economy.peddler.PeddlerEntity;
import fr.pioneers.economy.peddler.PeddlerService;
import fr.pioneers.reputation.PeopleApi;
import fr.pioneers.reputation.ReputationApi;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.9.1 (retours de Guillaume) : regagner la confiance d'un peuple qui vous deteste, par des ACTES (le temps ne pardonne toujours rien) :
 * <ul>
 *   <li>l'amende honorable au Tableau des besoins (jetons, ou les marchandises que le village demande), meme au palier Hostile ;</li>
 *   <li>l'emissaire Colporteur (Maj + clic droit sur un Colporteur) qui porte une offrande a un peuple fache, effet apres quelques jours ;</li>
 *   <li>les taches de reparation du Tableau : livrer, tenir la place une nuit, remettre les champs en etat, repousser un raid.</li>
 * </ul>
 * Regles et valeurs : {@link RepairRules} ({@code data/pioneers/village/repairs.json}) ; donnees : {@link RepairData}. Tout gain de
 * reputation passe par {@link ReputationApi#add} (plafond de +30 par peuple et par jour) et s'arrete a 0 ; rien ne cree ni ne rend de jetons
 * ou d'objets. Le serveur recalcule tout ; le client n'envoie que des intentions. Aucun calcul par tick : une file toutes les 100 ticks.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class VillageRepairs {

    /** Rayon autour de la place pour la veille (presence) et les monstres qui comptent. */
    public static final int WATCH_PRESENCE = 32, WATCH_KILLS = 48, FIELD_RANGE = 24, BOARD_REACH = 8;
    private static int clock;
    /** Parcelles vides par village (jour du releve, nombre), pour ne pas relire les champs a chaque ouverture du Tableau. */
    private static final Map<Long, long[]> FIELDS = new HashMap<>();

    private VillageRepairs() {
    }

    static void init() {
        RpNet.onServer(RpActions.BOARD_AMENDS, VillageRepairs::amends);
        RpNet.onServer(RpActions.BOARD_QUEST, VillageRepairs::quest);
        RpNet.onServer(RpActions.EMISSARY_SEND, VillageRepairs::send);
    }

    // ------------------------------------------------------------------ lecture des reglages (data/pioneers/village/repairs.json)
    static RepairRules.Settings parse(JsonObject o) {
        RepairRules.Settings d = RepairRules.DEFAULTS;
        JsonObject h = o.has("hostility") ? o.getAsJsonObject("hostility") : new JsonObject();
        JsonObject a = o.has("amends") ? o.getAsJsonObject("amends") : new JsonObject();
        JsonObject e = o.has("emissary") ? o.getAsJsonObject("emissary") : new JsonObject();
        JsonObject q = o.has("quests") ? o.getAsJsonObject("quests") : new JsonObject();
        RepairRules.Hostility dh = d.hostility();
        RepairRules.Hostility hh = new RepairRules.Hostility(i(h, "flee_affinity", dh.fleeAffinity()), i(h, "distance_affinity", dh.distanceAffinity()),
              i(h, "golem_village_average", dh.golemVillageAverage()), i(h, "golem_call_affinity", dh.golemCallAffinity()), i(h, "flee_range", dh.fleeRange()),
              i(h, "keep_distance", dh.keepDistance()), i(h, "call_range", dh.callRange()), i(h, "hit_witness_range", dh.hitWitnessRange()),
              i(h, "call_seconds", dh.callSeconds()), i(h, "truce_board_range", dh.truceBoardRange()), i(h, "truce_break_days", dh.truceBreakDays()),
              i(h, "message_seconds", dh.messageSeconds()));
        RepairRules.Amends da = d.amends();
        RepairRules.Amends aa = new RepairRules.Amends(i(a, "base_tokens", da.baseTokens()), i(a, "depth_divisor", da.depthDivisor()),
              f(a, "goods_factor", da.goodsFactor()), i(a, "goods_max", da.goodsMax()), i(a, "per_day", da.perDay()), i(a, "people_gain", da.peopleGain()),
              i(a, "villager_gain", da.villagerGain()));
        RepairRules.Emissary de = d.emissary();
        RepairRules.Emissary ee = new RepairRules.Emissary(f(e, "fee_factor", de.feeFactor()), i(e, "days", de.days()), i(e, "people_gain", de.peopleGain()),
              i(e, "per_day", de.perDay()), i(e, "reach", de.reach()));
        List<RepairRules.Quest> quests = new ArrayList<>();
        for (RepairRules.Quest dq : d.quests()) {
            JsonObject x = q.has(dq.id()) ? q.getAsJsonObject(dq.id()) : new JsonObject();
            quests.add(new RepairRules.Quest(dq.id(), i(x, "people_gain", dq.peopleGain()), i(x, "villager_gain", dq.villagerGain()), i(x, "days", dq.days()),
                  f(x, "factor", dq.factor()), i(x, "count", dq.count()), f(x, "presence", dq.presence())));
        }
        return new RepairRules.Settings(hh, aa, ee, List.copyOf(quests), i(o, "village_factor", d.villageFactor()), i(o, "window_days", d.windowDays()),
              i(o, "max_depth", d.maxDepth()));
    }

    private static int i(JsonObject o, String k, int def) {
        return o.has(k) ? o.get(k).getAsInt() : def;
    }

    private static double f(JsonObject o, String k, double def) {
        return o.has(k) ? o.get(k).getAsDouble() : def;
    }

    // ------------------------------------------------------------------ outils
    static RepairData.Entry entry(ServerPlayer p) {
        RepairData.Entry e = RepairData.get(p.getServer()).of(p.getUUID());
        e.today(PioneerApi.day(p.getServer()));
        return e;
    }

    static int depth(VillageData.Village v, ServerPlayer p) {
        return RepairRules.depth(RepairRules.get(), ReputationApi.reputation(p, v.people), v.average(p.getUUID()));
    }

    static int count(Inventory inv, Item item) {
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item) && !s.isDamaged()) {
                n += s.getCount();
            }
        }
        return n;
    }

    static void take(Inventory inv, Item item, int n) {
        for (int i = 0; i < inv.getContainerSize() && n > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item) && !s.isDamaged()) {
                int k = Math.min(n, s.getCount());
                s.shrink(k);
                n -= k;
            }
        }
        inv.setChanged();
    }

    /** Ce que le village achete (catalogue de son peuple), jusqu'a l'age Bronze, hors composants : les marchandises qu'il peut demander. */
    static List<Item> wants(ServerLevel level, VillageData.Village v) {
        VillageCatalog.Doctrine d = VillageCatalog.doctrine(PeopleApi.doctrine(level.getServer(), v.people));
        List<Item> pool = new ArrayList<>();
        for (String prof : Villagers.KNOWN) {
            VillageCatalog.Profession p = VillageCatalog.profession(prof);
            if (p == null) {
                continue;
            }
            for (Item it : p.version(d.version()).buys()) {
                Values.Entry e = Values.entry(it);
                if (e != null && e.value() > 0 && e.age() <= 1 && !"component".equals(e.quota()) && !pool.contains(it)) {
                    pool.add(it);
                }
            }
        }
        pool.sort(Comparator.comparing(it -> BuiltInRegistries.ITEM.getKey(it).toString()));
        return pool;
    }

    record Goods(Item item, int count) {
    }

    /**
     * Marchandise demandee : l'objet de la Penurie en cours s'il convient, sinon un objet du catalogue tire de facon reproductible (village,
     * jour, rang) parmi ceux dont la quantite reste raisonnable.
     */
    @Nullable
    static Goods goods(ServerLevel level, VillageData.Village v, long tokens, long seed, boolean deliver, int depth) {
        RepairRules.Settings s = RepairRules.get();
        List<Item> pool = wants(level, v);
        String shortage = Villages.eventItem(v, "shortage", PioneerApi.day(level));
        if (!shortage.isEmpty()) {
            Item it = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(shortage));
            pool.remove(it);
            pool.add(0, it);
        }
        List<Goods> ok = new ArrayList<>();
        for (Item it : pool) {
            Values.Entry e = Values.entry(it);
            if (e == null) {
                continue;
            }
            int n = deliver ? RepairRules.deliverCount(s, depth, e.value()) : RepairRules.goodsCount(s, tokens, e.value());
            if (n > 0) {
                ok.add(new Goods(it, n));
            }
        }
        if (ok.isEmpty()) {
            return null;
        }
        if (!shortage.isEmpty() && ok.get(0).item() == BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(shortage))) {
            return ok.get(0);
        }
        return ok.get((int) Math.floorMod(seed, ok.size()));
    }

    /** Parcelles vides (terre labouree sans culture) des champs du village. */
    static List<BlockPos> emptyPlots(ServerLevel level, VillageData.Village v) {
        List<BlockPos> out = new ArrayList<>();
        BlockPos c = v.center;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -FIELD_RANGE; dx <= FIELD_RANGE; dx++) {
            for (int dz = -FIELD_RANGE; dz <= FIELD_RANGE; dz++) {
                for (int dy = -6; dy <= 3; dy++) {
                    m.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                    if (!level.isLoaded(m)) {
                        continue;
                    }
                    if (level.getBlockState(m).getBlock() instanceof FarmBlock && level.getBlockState(m.above()).isAir()) {
                        out.add(m.immutable());
                    }
                }
            }
        }
        out.sort(Comparator.comparingDouble(p -> p.distSqr(c)));
        return out;
    }

    static int plotsCount(ServerLevel level, VillageData.Village v) {
        long day = PioneerApi.day(level);
        long[] known = FIELDS.get(v.id);
        if (known == null || known[0] != day) {
            known = new long[]{day, emptyPlots(level, v).size()};
            FIELDS.put(v.id, known);
        }
        return (int) known[1];
    }

    static String taskKey(long village, String type, long window) {
        return village + ":" + type + ":" + window;
    }

    /** Rachat applique : reputation du peuple (plafond du jour, jamais au-dessus de 0) et affinite des villageois du village (jusqu'a 0). */
    static int reward(ServerLevel level, VillageData.Village v, ServerPlayer p, int people, int villager, String cause) {
        int rep = ReputationApi.reputation(p, v.people);
        int g = RepairRules.peopleGain(people, rep);
        int done = g > 0 ? ReputationApi.add(p, v.people, g, cause, false) : 0;
        if (villager > 0) {
            Villagers.mendVillage(level, v, p, villager, cause);
        }
        return done;
    }

    @Nullable
    static String voice(String section, long seed) {
        List<String> l = VillageCatalog.lines("common/repair/" + section);
        return l.isEmpty() ? null : l.get((int) Math.floorMod(seed, l.size()));
    }

    // ------------------------------------------------------------------ section « Reparations » du Tableau
    static CompoundTag section(ServerLevel level, VillageData.Village v, ServerPlayer p) {
        RepairRules.Settings s = RepairRules.get();
        long day = PioneerApi.day(level), window = RepairRules.window(s, day);
        int rep = ReputationApi.reputation(p, v.people), avg = v.average(p.getUUID());
        int depth = RepairRules.depth(s, rep, avg);
        RepairData.Entry e = entry(p);
        CompoundTag t = new CompoundTag();
        t.putInt("rep", rep);
        t.putString("tier", ReputationApi.tierOf(rep).id());
        t.putInt("avg", avg);
        t.putInt("depth", depth);
        t.putBoolean("need", RepairRules.needed(rep, avg));
        t.putLong("balance", EconomyApi.balance(p));
        //amende honorable
        int done = e.amends.getOrDefault(v.people, 0);
        long cost = RepairRules.amendsCost(s, depth, done);
        CompoundTag a = new CompoundTag();
        a.putLong("tokens", cost);
        a.putInt("done", done);
        a.putInt("max", s.amends().perDay());
        a.putString("refusal", RepairRules.amends(s, depth, done, EconomyApi.balance(p) >= cost).name());
        Goods g = depth > 0 ? goods(level, v, cost, v.id * 31 + day * 7 + done, false, depth) : null;
        if (g != null) {
            a.putString("item", BuiltInRegistries.ITEM.getKey(g.item()).toString());
            a.putInt("count", g.count());
            a.putInt("have", count(p.getInventory(), g.item()));
        }
        a.putInt("people", s.amends().peopleGain());
        a.putInt("villager", s.amends().villagerGain());
        t.put("amends", a);
        //taches : celle en cours, sinon les deux proposees par le village
        ListTag quests = new ListTag();
        if (e.task != null) {
            quests.add(taskView(level, v, p, e.task, s, day));
        } else if (depth > 0) {
            for (String type : new String[]{"deliver", RepairRules.actionQuest(v.id, window, plotsCount(level, v) >= questCount(s, "replant"))}) {
                RepairRules.Quest q = s.quest(type);
                if (q == null) {
                    continue;
                }
                CompoundTag c = new CompoundTag();
                c.putString("type", type);
                c.putString("state", e.done.contains(taskKey(v.id, type, window)) ? "done" : "offer");
                c.putInt("people", q.peopleGain());
                c.putInt("villager", q.villagerGain());
                c.putInt("days", q.days());
                c.putInt("count", q.count());
                if ("deliver".equals(type)) {
                    Goods dg = goods(level, v, 0, v.id * 17 + window * 13, true, depth);
                    if (dg == null) {
                        continue;
                    }
                    c.putString("item", BuiltInRegistries.ITEM.getKey(dg.item()).toString());
                    c.putInt("count", dg.count());
                    c.putInt("have", count(p.getInventory(), dg.item()));
                }
                quests.add(c);
            }
        }
        t.put("quests", quests);
        for (RepairData.Envoy env : e.envoys) {
            if (env.people().equals(v.people)) {
                t.putLong("envoy", Math.max(0, env.arrive() - day));
            }
        }
        t.putBoolean("truce", VillageHostility.truce(level, v, p));
        return t;
    }

    static int questCount(RepairRules.Settings s, String id) {
        RepairRules.Quest q = s.quest(id);
        return q == null ? 0 : q.count();
    }

    static CompoundTag taskView(ServerLevel level, VillageData.Village v, ServerPlayer p, RepairData.Task task, RepairRules.Settings s, long day) {
        RepairRules.Quest q = s.quest(task.type);
        CompoundTag c = new CompoundTag();
        c.putString("type", task.type);
        c.putString("state", task.village == v.id ? "active" : "elsewhere");
        c.putInt("people", q == null ? 0 : q.peopleGain());
        c.putInt("villager", q == null ? 0 : q.villagerGain());
        c.putLong("left", Math.max(0, task.until - day));
        c.putString("item", task.item);
        c.putInt("count", task.count);
        switch (task.type) {
            case "deliver" -> {
                Item it = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(task.item));
                c.putInt("have", count(p.getInventory(), it));
            }
            case "watch" -> {
                c.putInt("kills", task.kills);
                c.putInt("need", q == null ? 3 : q.count());
                c.putInt("present", task.samples == 0 ? 0 : 100 * task.present / task.samples);
                c.putBoolean("night", task.night);
            }
            case "replant" -> {
                c.putInt("planted", task.kills);
                c.putInt("need", task.count);
            }
            default -> {
            }
        }
        return c;
    }

    // ------------------------------------------------------------------ actions du Tableau
    @Nullable
    static VillageData.Village boardVillage(ServerPlayer player, CompoundTag t) {
        BlockPos pos = BlockPos.of(t.getLong("pos"));
        if (player.distanceToSqr(pos.getCenter()) > BOARD_REACH * BOARD_REACH || !Villagers.active()) {
            return null;
        }
        return VillageData.get(player.serverLevel()).nearest(pos, Villages.DISCOVERY);
    }

    static void reply(ServerPlayer player, CompoundTag t, @Nullable Component msg) {
        CompoundTag again = new CompoundTag();
        again.putLong("pos", t.getLong("pos"));
        Villages.boardRequest(player, again, msg);
    }

    static void amends(ServerPlayer player, CompoundTag t) {
        VillageData.Village v = boardVillage(player, t);
        if (v == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        RepairRules.Settings s = RepairRules.get();
        RepairData.Entry e = entry(player);
        long day = PioneerApi.day(level);
        int depth = depth(v, player);
        int done = e.amends.getOrDefault(v.people, 0);
        long cost = RepairRules.amendsCost(s, depth, done);
        boolean goods = "goods".equals(t.getString("mode"));
        RepairRules.Refusal r = RepairRules.amends(s, depth, done, goods || EconomyApi.balance(player) >= cost);
        if (r != RepairRules.Refusal.NONE) {
            reply(player, t, Component.translatable("village.pioneers.repair.refusal." + r.name().toLowerCase(java.util.Locale.ROOT)));
            return;
        }
        if (goods) {
            Goods g = goods(level, v, cost, v.id * 31 + day * 7 + done, false, depth);
            if (g == null || count(player.getInventory(), g.item()) < g.count()) {
                reply(player, t, Component.translatable("village.pioneers.repair.refusal.goods"));
                return;
            }
            take(player.getInventory(), g.item(), g.count());
        } else if (!EconomyApi.debit(player, cost, EconomyApi.Entry.of("amends", v.people)).ok()) {
            reply(player, t, Component.translatable("village.pioneers.repair.refusal.tokens"));
            return;
        }
        e.amends.merge(v.people, 1, Integer::sum);
        RepairData.get(level.getServer()).changed();
        int gained = reward(level, v, player, s.amends().peopleGain(), s.amends().villagerGain(), "amends");
        Villages.deed(level, v, player, cost);
        level.playSound(null, player.blockPosition(), RpSounds.QUILL.get(), SoundSource.PLAYERS, 0.8F, 0.9F);
        String line = voice("amends", v.id + day + done);
        reply(player, t, line == null ? Component.translatable("village.pioneers.repair.amends_done", gained)
              : Component.translatable("village.pioneers.repair.voice", Component.translatable(line), gained));
    }

    static void quest(ServerPlayer player, CompoundTag t) {
        VillageData.Village v = boardVillage(player, t);
        if (v == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        RepairRules.Settings s = RepairRules.get();
        RepairData data = RepairData.get(level.getServer());
        RepairData.Entry e = entry(player);
        long day = PioneerApi.day(level), window = RepairRules.window(s, day);
        String op = t.getString("op"), type = t.getString("type");
        int depth = depth(v, player);
        switch (op) {
            case "accept" -> {
                RepairRules.Quest q = s.quest(type);
                String act = RepairRules.actionQuest(v.id, window, plotsCount(level, v) >= questCount(s, "replant"));
                if (q == null || !(type.equals("deliver") || type.equals(act))) {
                    reply(player, t, null);
                    return;
                }
                RepairRules.Refusal r = RepairRules.accept(depth, e.task != null, e.done.contains(taskKey(v.id, type, window)));
                if (r != RepairRules.Refusal.NONE) {
                    reply(player, t, Component.translatable("village.pioneers.repair.refusal." + r.name().toLowerCase(java.util.Locale.ROOT)));
                    return;
                }
                RepairData.Task task = new RepairData.Task();
                task.village = v.id;
                task.type = type;
                task.started = day;
                task.until = day + q.days();
                if (type.equals("deliver")) {
                    Goods g = goods(level, v, 0, v.id * 17 + window * 13, true, depth);
                    if (g == null) {
                        reply(player, t, null);
                        return;
                    }
                    task.item = BuiltInRegistries.ITEM.getKey(g.item()).toString();
                    task.count = g.count();
                } else if (type.equals("replant")) {
                    List<BlockPos> plots = emptyPlots(level, v);
                    if (plots.size() < q.count()) {
                        reply(player, t, Component.translatable("village.pioneers.repair.refusal.nothing"));
                        return;
                    }
                    task.plots.addAll(plots.subList(0, Math.min(plots.size(), q.count() * 2)));
                    task.count = q.count();
                }
                e.task = task;
                data.changed();
                String line = voice("accept", v.id + day);
                reply(player, t, line == null ? null : Component.translatable(line));
            }
            case "abandon" -> {
                if (e.task != null) {
                    e.task = null;
                    data.changed();
                }
                reply(player, t, Component.translatable("village.pioneers.repair.abandoned"));
            }
            case "report" -> {
                RepairData.Task task = e.task;
                if (task == null || task.village != v.id) {
                    reply(player, t, null);
                    return;
                }
                if (task.type.equals("deliver")) {
                    Item it = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(task.item));
                    if (count(player.getInventory(), it) < task.count) {
                        reply(player, t, Component.translatable("village.pioneers.repair.refusal.goods"));
                        return;
                    }
                    take(player.getInventory(), it, task.count);
                    complete(level, v, player, e, s);
                    reply(player, t, doneLine(v, task, day));
                } else if (task.type.equals("replant")) {
                    //seules comptent les parcelles que le joueur a lui-meme semees (un fermier du village ne fait pas la tache a sa place)
                    if (task.kills < task.count) {
                        reply(player, t, Component.translatable("village.pioneers.repair.replant_left", task.count - task.kills));
                        return;
                    }
                    complete(level, v, player, e, s);
                    reply(player, t, doneLine(v, task, day));
                } else {
                    reply(player, t, Component.translatable("village.pioneers.repair.wait." + task.type));
                }
            }
            default -> reply(player, t, null);
        }
    }

    static Component doneLine(VillageData.Village v, RepairData.Task task, long day) {
        String line = voice("done", v.id + day + task.type.hashCode());
        return line == null ? Component.translatable("village.pioneers.repair.task_done") : Component.translatable(line);
    }

    /** Tache reussie : recompense, marquee faite pour sa fenetre, retiree. */
    static void complete(ServerLevel level, VillageData.Village v, ServerPlayer player, RepairData.Entry e, RepairRules.Settings s) {
        RepairData.Task task = e.task;
        if (task == null) {
            return;
        }
        RepairRules.Quest q = s.quest(task.type);
        e.task = null;
        e.done.add(taskKey(task.village, task.type, RepairRules.window(s, task.started)));
        RepairData.get(level.getServer()).changed();
        if (q != null) {
            reward(level, v, player, q.peopleGain(), q.villagerGain(), "repair_quest");
        }
        Villages.deed(level, v, player, 500);
        level.playSound(null, player.blockPosition(), RpSounds.TRUST.get(), SoundSource.PLAYERS, 0.7F, 1.0F);
        player.displayClientMessage(Component.translatable("village.pioneers.repair.task_done").withStyle(ChatFormatting.GOLD), true);
    }

    /** Raid repousse (le joueur recoit Heros du village) : la tache « raid » de ce village est faite. */
    static void raidWon(ServerLevel level, VillageData.Village v, ServerPlayer player) {
        RepairData.Entry e = entry(player);
        if (e.task != null && e.task.type.equals("raid") && e.task.village == v.id) {
            complete(level, v, player, e, RepairRules.get());
        }
    }

    // ------------------------------------------------------------------ veille, emissaires, delais (file toutes les 100 ticks)
    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++clock % 100 != 0 || !Villagers.active()) {
            return;
        }
        var server = event.getServer();
        RepairData data = RepairData.get(server);
        ServerLevel overworld = server.overworld();
        long day = PioneerApi.day(server);
        RepairRules.Settings s = RepairRules.get();
        for (Map.Entry<UUID, RepairData.Entry> me : data.all().entrySet()) {
            RepairData.Entry e = me.getValue();
            ServerPlayer p = server.getPlayerList().getPlayer(me.getKey());
            //emissaires arrives (le joueur doit etre la pour que la reputation change)
            if (p != null && !e.envoys.isEmpty()) {
                List<RepairData.Envoy> arrived = new ArrayList<>();
                for (RepairData.Envoy env : e.envoys) {
                    if (env.arrive() <= day) {
                        arrived.add(env);
                    }
                }
                for (RepairData.Envoy env : arrived) {
                    e.envoys.remove(env);
                    int g = RepairRules.peopleGain(env.gain(), ReputationApi.reputation(p, env.people()));
                    int got = g > 0 ? ReputationApi.add(p, env.people(), g, "emissary", false) : 0;
                    p.displayClientMessage(Component.translatable("village.pioneers.emissary.arrived", ReputationApi.peopleName(server, env.people()), got)
                          .withStyle(ChatFormatting.GOLD), true);
                    data.changed();
                }
            }
            RepairData.Task task = e.task;
            if (task == null) {
                continue;
            }
            if (day > task.until) {
                e.task = null;
                data.changed();
                if (p != null) {
                    p.displayClientMessage(Component.translatable("village.pioneers.repair.expired"), true);
                }
                continue;
            }
            if (task.type.equals("watch")) {
                VillageData.Village v = VillageData.get(overworld).village(task.village);
                if (v == null) {
                    continue;
                }
                if (overworld.isNight()) {
                    task.night = true;
                    task.samples++;
                    BlockPos anchor = v.boards.isEmpty() ? v.center : v.boards.get(0);
                    if (p != null && p.level() == overworld && p.blockPosition().distSqr(anchor) <= WATCH_PRESENCE * WATCH_PRESENCE && p.isAlive()) {
                        task.present++;
                    }
                    data.changed();
                } else if (task.night) {
                    //l'aube : la veille est jugee
                    boolean ok = RepairRules.watchDone(s, task.present, task.samples, task.kills);
                    if (ok && p != null) {
                        complete(overworld, v, p, e, s);
                    } else if (!ok) {
                        e.task = null;
                        data.changed();
                        if (p != null) {
                            p.displayClientMessage(Component.translatable("village.pioneers.repair.watch_failed"), true);
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Enemy) || !(event.getSource().getEntity() instanceof ServerPlayer p) || !(p.level() instanceof ServerLevel level)) {
            return;
        }
        RepairData.Entry e = RepairData.get(level.getServer()).peek(p.getUUID());
        if (e == null || e.task == null || !e.task.type.equals("watch") || !level.isNight()) {
            return;
        }
        VillageData.Village v = VillageData.get(level).village(e.task.village);
        if (v != null && event.getEntity().blockPosition().distSqr(v.center) <= WATCH_KILLS * WATCH_KILLS) {
            e.task.kills++;
            RepairData.get(level.getServer()).changed();
        }
    }

    /** Champs : une parcelle relevee a l'acceptation, semee par le joueur lui-meme, compte une fois. */
    @SubscribeEvent
    public static void onPlant(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        RepairData.Entry e = RepairData.get(level.getServer()).peek(p.getUUID());
        if (e == null || e.task == null || !e.task.type.equals("replant")) {
            return;
        }
        BlockState placed = event.getPlacedBlock();
        if (!(placed.is(BlockTags.CROPS) || placed.getBlock() instanceof BushBlock) || !e.task.plots.remove(event.getPos().below())) {
            return;
        }
        e.task.kills++;
        RepairData.get(level.getServer()).changed();
    }

    // ------------------------------------------------------------------ emissaire Colporteur (Maj + clic droit)
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide || !(event.getTarget() instanceof PeddlerEntity peddler) || !(event.getEntity() instanceof ServerPlayer player)
            || !player.isShiftKeyDown() || event.getHand() != InteractionHand.MAIN_HAND || !Villagers.active()) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        emissaryView(player, peddler, null);
    }

    static boolean peddlersHostile(ServerPlayer player) {
        return ReputationApi.tier(player, PeopleApi.PEDDLERS).refuse() || PeddlerService.banned(player);
    }

    static void emissaryView(ServerPlayer player, PeddlerEntity peddler, @Nullable Component msg) {
        RepairRules.Settings s = RepairRules.get();
        RepairData.Entry e = entry(player);
        long day = PioneerApi.day(player.getServer());
        boolean hostile = peddlersHostile(player);
        CompoundTag t = new CompoundTag();
        t.putInt("entity", peddler.getId());
        t.putString("name", Component.Serializer.toJson(peddler.getDisplayName()));
        t.putBoolean("refused", hostile);
        t.putLong("balance", EconomyApi.balance(player));
        t.putInt("days", s.emissary().days());
        t.putInt("sent", e.envoysToday);
        t.putInt("max", s.emissary().perDay());
        ListTag list = new ListTag();
        for (PeopleApi.People people : PeopleApi.all(player.getServer())) {
            int rep = ReputationApi.reputation(player, people.id());
            int depth = RepairRules.depth(s, rep, 0);
            boolean self = people.id().equals(PeopleApi.PEDDLERS);
            if (depth <= 0) {
                continue;
            }
            long cost = RepairRules.emissaryCost(s, depth);
            RepairData.Envoy flying = null;
            for (RepairData.Envoy env : e.envoys) {
                if (env.people().equals(people.id())) {
                    flying = env;
                }
            }
            CompoundTag c = new CompoundTag();
            c.putString("id", people.id());
            c.putString("icon", PeopleApi.model(player.getServer(), people.id()));
            c.putString("name", Component.Serializer.toJson(ReputationApi.peopleName(player.getServer(), people.id())));
            c.putInt("rep", rep);
            c.putString("tier", ReputationApi.tierOf(rep).id());
            c.putLong("cost", cost);
            c.putInt("gain", RepairRules.peopleGain(s.emissary().peopleGain(), rep));
            if (flying != null) {
                c.putLong("arrive", Math.max(0, flying.arrive() - day));
            }
            c.putString("refusal", RepairRules.emissary(s, hostile, self, depth, flying != null, e.envoysToday, EconomyApi.balance(player) >= cost).name());
            list.add(c);
        }
        t.put("peoples", list);
        if (msg != null) {
            t.putString("msg", Component.Serializer.toJson(msg));
        }
        String key = hostile ? "refuse" : "greet";
        String v = PeddlerVoice.line(key, peddler.getId() + day);
        if (v != null) {
            t.putString("voice", v);
        }
        RpNet.send(player, RpActions.EMISSARY_VIEW, t);
    }

    static void send(ServerPlayer player, CompoundTag t) {
        if (!(player.serverLevel().getEntity(t.getInt("entity")) instanceof PeddlerEntity peddler) || !peddler.isAlive() || !Villagers.active()) {
            return;
        }
        RepairRules.Settings s = RepairRules.get();
        if (player.distanceToSqr(peddler) > s.emissary().reach() * s.emissary().reach()) {
            return;
        }
        String people = t.getString("people");
        if (PeopleApi.get(player.getServer(), people) == null) {
            return;
        }
        RepairData.Entry e = entry(player);
        long day = PioneerApi.day(player.getServer());
        int rep = ReputationApi.reputation(player, people);
        int depth = RepairRules.depth(s, rep, 0);
        long cost = RepairRules.emissaryCost(s, depth);
        boolean flying = e.envoys.stream().anyMatch(x -> x.people().equals(people));
        RepairRules.Refusal r = RepairRules.emissary(s, peddlersHostile(player), people.equals(PeopleApi.PEDDLERS), depth, flying, e.envoysToday,
              EconomyApi.balance(player) >= cost);
        if (r != RepairRules.Refusal.NONE) {
            emissaryView(player, peddler, Component.translatable("village.pioneers.repair.refusal." + r.name().toLowerCase(java.util.Locale.ROOT)));
            return;
        }
        if (!EconomyApi.debit(player, cost, EconomyApi.Entry.of("emissary", people)).ok()) {
            emissaryView(player, peddler, Component.translatable("village.pioneers.repair.refusal.tokens"));
            return;
        }
        e.envoys.add(new RepairData.Envoy(people, day + s.emissary().days(), s.emissary().peopleGain()));
        e.envoysToday++;
        RepairData.get(player.getServer()).changed();
        player.level().playSound(null, peddler.blockPosition(), RpSounds.LLAMA_BELLS.get(), SoundSource.NEUTRAL, 0.6F, 1.0F);
        emissaryView(player, peddler, Component.translatable("village.pioneers.emissary.sent", ReputationApi.peopleName(player.getServer(), people),
              s.emissary().days()));
    }

    /** Repliques des Colporteurs pour l'ecran de l'emissaire (section « peddler » des paroles). */
    static final class PeddlerVoice {

        private PeddlerVoice() {
        }

        @Nullable
        static String line(String key, long seed) {
            List<String> l = VillageCatalog.lines("common/repair/emissary_" + key);
            return l.isEmpty() ? null : l.get((int) Math.floorMod(seed, l.size()));
        }
    }

    /** Le joueur porte-t-il le drapeau blanc (banniere blanche en main ou en main gauche) ? */
    static boolean whiteFlag(ServerPlayer p) {
        return p.getMainHandItem().is(Items.WHITE_BANNER) || p.getOffhandItem().is(Items.WHITE_BANNER);
    }
}
