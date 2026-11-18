package fr.pioneers.world.ruins;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.pioneers.Pioneers;
import fr.pioneers.core.common.PiCommonConfig;
import fr.pioneers.core.common.projector.ProjectedStructure;
import fr.pioneers.core.profile.PioneerApi;
import fr.pioneers.core.profile.RpActions;
import fr.pioneers.core.profile.RpConfig;
import fr.pioneers.core.profile.RpNet;
import fr.pioneers.core.profile.RpSounds;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.multi.IndustrialBlueprint;
import fr.pioneers.tech.industry.PiIndustry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.ToDoubleBiFunction;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;

/**
 * 1.9 (MAJ RP, bloc 1) : restauration d'une ruine, projet long, collectif et tres couteux. Quatre etapes : 1 Releve (gratuit : composants,
 * duree, cout), 2 Degagement (blocs proteges ; gravats et Sentinelles a retirer), 3 Remise en etat (le chantier consomme sa reserve jour
 * apres jour, meme chunk decharge : un tick serveur espace), 4 Mise en service (le vrai multibloc du tier, une plaque des contributeurs, un
 * service pour le village le plus proche). Cout : 3 x la nomenclature ({@code data/pioneers/ruins/restoration/<cle>.json}) et des lingots
 * (des jetons quand l'economie est active). Un projet annule rend 50 % de la reserve. Tout est decide par le serveur.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class Restoration {

    public static final int SURVEY = 0, CLEARING = 1, REPAIR = 2, DONE = 3;
    /** Niveau de Restauration requis par tier (MKI, MKII, MKIII). */
    public static final int[] MIN_LEVEL = {0, 3, 6};
    public static final double RANGE = 64;
    /**
     * Ouvriers du village (bloc 3) : part de la duree restante en moins pour ce jour de chantier (0..0,6, plafond commun avec le skill),
     * appelee une fois par jour ecoule (le village preleve alors le salaire du jour).
     */
    public interface Workers {

        double fraction(ServerLevel level, RuinSites.Site site, long day);
    }

    public static Workers workers = (l, s, d) -> 0;
    /** Paiement en jetons quand l'economie est active (bloc 3) ; null = lingots. */
    @Nullable
    public static Payment tokens;

    /**
     * Branchement de la monnaie (bloc 3) : part en argent d'un chantier (10 % de la valeur de la nomenclature, en jetons), debit (vrai si le
     * joueur a paye) et remboursement d'une annulation.
     */
    public interface Payment {

        long price(List<Need> items);

        boolean debit(ServerPlayer player, long amount, String reason);

        void refund(ServerPlayer player, long amount, String reason);
    }

    private static boolean money() {
        return tokens != null && PioneerApi.hasFeature("economy");
    }

    private Restoration() {
    }

    static {
        RpNet.onServer(RpActions.RESTORATION_ACTION, Restoration::action);
    }

    static void init() {
    }

    // ------------------------------------------------------------------ nomenclatures (donnees)
    public record Cost(int days, List<Need> items, Need ingots) {
    }

    public record Need(String id, int count) {
    }

    public static final class Catalog extends SimpleJsonResourceReloadListener {

        public static final Catalog INSTANCE = new Catalog();
        static volatile Map<String, Map<Integer, Cost>> costs = Map.of();

        private Catalog() {
            super(new com.google.gson.Gson(), "ruins/restoration");
        }

        @Override
        protected void apply(@NotNull Map<ResourceLocation, JsonElement> map, @NotNull ResourceManager manager, @NotNull ProfilerFiller profiler) {
            Map<String, Map<Integer, Cost>> out = new HashMap<>();
            map.forEach((id, json) -> {
                try {
                    Map<Integer, Cost> byTier = new HashMap<>();
                    for (Map.Entry<String, JsonElement> e : json.getAsJsonObject().entrySet()) {
                        JsonObject o = e.getValue().getAsJsonObject();
                        List<Need> items = new ArrayList<>();
                        o.getAsJsonArray("items").forEach(x -> items.add(new Need(x.getAsJsonObject().get("id").getAsString(),
                              x.getAsJsonObject().get("count").getAsInt())));
                        JsonObject ing = o.getAsJsonObject("ingots");
                        byTier.put(Integer.parseInt(e.getKey()), new Cost(o.get("days").getAsInt(), items, new Need(ing.get("id").getAsString(),
                              ing.get("count").getAsInt())));
                    }
                    out.put(id.getPath(), byTier);
                } catch (RuntimeException ex) {
                    Pioneers.LOGGER.warn("Nomenclature de restauration illisible ({}) : {}", id, ex.toString());
                }
            });
            costs = out;
        }

        @Nullable
        static Cost cost(String machine, int tier) {
            Map<Integer, Cost> m = costs.get(machine);
            return m == null ? null : m.get(tier);
        }
    }

    // ------------------------------------------------------------------ projet
    private static boolean matches(String id, ItemStack s) {
        if (id.startsWith("#")) {
            return s.is(TagKey.create(Registries.ITEM, new ResourceLocation(id.substring(1))));
        }
        Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(id));
        return s.is(item);
    }

    private static CompoundTag project(RuinSites.Site s) {
        if (s.project == null) {
            s.project = new CompoundTag();
            s.project.putInt("stage", SURVEY);
        }
        return s.project;
    }

    /** Ouvre le chantier (etape 2) : verifie les conditions, fixe le cout du joueur qui l'ouvre (skills), protege les blocs. */
    private static Component start(ServerPlayer player, ServerLevel level, RuinSites.Site s) {
        if (s.state == RuinSites.State.DISMANTLED) {
            return Component.translatable("ruins.pioneers.restore.dismantled");
        }
        if (s.state != RuinSites.State.INTACT) {
            return null;
        }
        int need = MIN_LEVEL[Math.max(0, Math.min(2, s.tier - 1))];
        if (PioneerApi.level(player, "restoration") < need) {
            return Component.translatable("ruins.pioneers.restore.level", need);
        }
        if (awakeSentinels(level, s) > 0) {
            return Component.translatable("ruins.pioneers.restore.sentinels");
        }
        Cost c = Catalog.cost(s.machine, s.tier);
        if (c == null) {
            return Component.translatable("ruins.pioneers.restore.unknown");
        }
        double costFactor = PioneerApi.factor(player, "restoration.cost") * PioneerApi.factor(player, "scout.restoration_cost");
        double daysFactor = PioneerApi.factor(player, "restoration.duration");
        CompoundTag p = project(s);
        ListTag needs = new ListTag();
        for (Need n : c.items()) {
            CompoundTag e = new CompoundTag();
            e.putString("id", n.id());
            e.putInt("need", (int) Math.ceil(n.count() * costFactor));
            e.putInt("have", 0);
            needs.add(e);
        }
        boolean money = money();
        CompoundTag e = new CompoundTag();
        e.putString("id", money ? "tokens" : c.ingots().id());
        e.putInt("need", (int) Math.ceil((money ? tokens.price(c.items()) : c.ingots().count()) * costFactor));
        e.putInt("have", 0);
        needs.add(e);
        p.put("needs", needs);
        p.putInt("days", Math.max(1, (int) Math.round(c.days() * daysFactor)));
        //part de duree deja retiree par le skill du joueur qui ouvre (plafond commun de 60 % avec les ouvriers du village)
        p.putDouble("cut", Math.max(0, 1 - daysFactor));
        p.putInt("done", 0);
        p.putInt("stage", CLEARING);
        p.putUUID("opener", player.getUUID());
        s.state = RuinSites.State.RESTORING;
        s.contributors.merge(player.getUUID(), 0L, Long::sum);
        RuinSites.get(level).changed();
        PioneerApi.gain(player, "restoration.stage", 1, s.controller());
        level.playSound(null, s.controller(), RpSounds.QUILL.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
        return null;
    }

    private static int awakeSentinels(ServerLevel level, RuinSites.Site s) {
        return fr.pioneers.world.ruins.sentinel.Sentinels.alive(level, s);
    }

    private static int rubble(ServerLevel level, RuinSites.Site s) {
        int n = 0;
        for (BlockPos p : BlockPos.betweenClosed(s.min, s.max)) {
            if (level.isLoaded(p) && level.getBlockState(p).getBlock() instanceof TechRubbleBlock) {
                n++;
            }
        }
        return n;
    }

    /** Depose dans la reserve ce que le joueur porte et que le chantier demande encore. */
    private static int deposit(ServerPlayer player, ServerLevel level, RuinSites.Site s) {
        CompoundTag p = project(s);
        ListTag needs = p.getList("needs", Tag.TAG_COMPOUND);
        Inventory inv = player.getInventory();
        int moved = 0;
        for (int i = 0; i < needs.size(); i++) {
            CompoundTag e = needs.getCompound(i);
            String id = e.getString("id");
            int missing = e.getInt("need") - e.getInt("have");
            if (missing <= 0 || "tokens".equals(id)) {
                if ("tokens".equals(id) && missing > 0 && tokens != null && tokens.debit(player, missing, "restoration")) {
                    e.putInt("have", e.getInt("need"));
                    moved += missing;
                }
                continue;
            }
            for (int k = 0; k < inv.getContainerSize() && missing > 0; k++) {
                ItemStack st = inv.getItem(k);
                if (!st.isEmpty() && matches(id, st)) {
                    int n = Math.min(missing, st.getCount());
                    CompoundTag got = e.getCompound("got");
                    String key = BuiltInRegistries.ITEM.getKey(st.getItem()).toString();
                    got.putInt(key, got.getInt(key) + n);
                    e.put("got", got);
                    st.shrink(n);
                    missing -= n;
                    e.putInt("have", e.getInt("have") + n);
                    moved += n;
                }
            }
        }
        if (moved > 0) {
            inv.setChanged();
            s.contributors.merge(player.getUUID(), (long) moved, Long::sum);
            RuinSites.get(level).changed();
            level.playSound(null, s.controller(), RpSounds.PAPER_PIN.get(), SoundSource.BLOCKS, 0.7F, 1.0F);
        }
        return moved;
    }

    /** Annulation : la moitie de ce qui reste dans la reserve est rendue au joueur. */
    private static void cancel(ServerPlayer player, ServerLevel level, RuinSites.Site s) {
        CompoundTag p = project(s);
        ListTag needs = p.getList("needs", Tag.TAG_COMPOUND);
        int days = Math.max(1, p.getInt("days")), done = p.getInt("done");
        for (int i = 0; i < needs.size(); i++) {
            CompoundTag e = needs.getCompound(i);
            String id = e.getString("id");
            int consumed = (int) Math.ceil(e.getInt("need") * (double) done / days);
            int back = Math.max(0, e.getInt("have") - consumed) / 2;
            if (back <= 0) {
                continue;
            }
            if ("tokens".equals(id)) {
                if (tokens != null) {
                    tokens.refund(player, back, "restoration_cancel");
                }
                continue;
            }
            //objets rendus parmi ceux reellement deposes (une ligne de tag a pu recevoir plusieurs sortes)
            CompoundTag got = e.getCompound("got");
            for (String key : got.getAllKeys()) {
                if (back <= 0) {
                    break;
                }
                Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(key));
                int give = Math.min(back, got.getInt(key));
                back -= give;
                while (give > 0) {
                    int n = Math.min(give, item.getMaxStackSize());
                    ItemStack st = new ItemStack(item, n);
                    if (!player.getInventory().add(st)) {
                        player.drop(st, false);
                    }
                    give -= n;
                }
            }
        }
        s.project = null;
        s.state = RuinSites.State.INTACT;
        RuinSites.get(level).changed();
    }

    /** Un jour de chantier : avance si la reserve couvre la part cumulee du jour suivant (projet = NBT du site). */
    public static boolean advanceDay(@Nullable CompoundTag p) {
        if (p == null || p.getInt("stage") != REPAIR) {
            return false;
        }
        int days = Math.max(1, p.getInt("days")), done = p.getInt("done");
        if (done >= days) {
            return false;
        }
        ListTag needs = p.getList("needs", Tag.TAG_COMPOUND);
        for (int i = 0; i < needs.size(); i++) {
            CompoundTag e = needs.getCompound(i);
            if (e.getInt("have") < Math.ceil(e.getInt("need") * (done + 1) / (double) days)) {
                return false;
            }
        }
        p.putInt("done", done + 1);
        return true;
    }

    // ------------------------------------------------------------------ jours (meme chunk decharge)
    private static int clock;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++clock % 600 != 0 || event.getServer() == null) {
            return;
        }
        MinecraftServer server = event.getServer();
        for (ServerLevel level : server.getAllLevels()) {
            RuinSites sites = RuinSites.get(level);
            long day = PioneerApi.day(level);
            for (RuinSites.Site s : sites.all()) {
                CompoundTag p = s.project;
                if (p == null || p.getInt("stage") != REPAIR) {
                    continue;
                }
                long last = p.getLong("lastDay");
                //Ouvriers du village : la duree baisse (jusqu'a 50 %) : un jour compte double par moments
                boolean changed = false;
                while (last < day) {
                    last++;
                    double bonus = Math.max(0, Math.min(0.6, workers.fraction(level, s, last)));
                    changed |= advanceDay(p);
                    if (bonus > 0 && level.random.nextDouble() < bonus / (1 - bonus)) {
                        changed |= advanceDay(p);
                    }
                }
                p.putLong("lastDay", last);
                sites.changed();
                if (p.getInt("done") >= p.getInt("days") && level.isLoaded(s.controller())) {
                    complete(level, s, null);
                } else if (changed) {
                    sites.changed();
                }
            }
        }
    }

    // ------------------------------------------------------------------ mise en service
    private static final String[] METAL = {"steel", "aluminium", "titanium", "tungsten"};

    /** Bloc d'un plan de multibloc au tier de la ruine (carcasses, verre, cadres et toles du tier). */
    private static BlockState tiered(BlockState state, int tier) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        String path = id.getPath(), want = path;
        String tierName = tier == 1 ? "basic" : tier == 2 ? "advanced" : "elite";
        if (path.startsWith("industrial_casing_")) {
            want = "industrial_casing_" + tierName;
        } else if (path.startsWith("industrial_glass")) {
            want = PiIndustry.GLASS_IDS[Math.max(0, Math.min(3, tier - 1))];
        } else {
            for (String m : METAL) {
                if (path.equals(m + "_frame") || path.equals(m + "_sheet_metal")) {
                    want = METAL[Math.max(0, Math.min(3, tier - 1))] + path.substring(m.length());
                }
            }
        }
        if (want.equals(path)) {
            return state;
        }
        Block b = BuiltInRegistries.BLOCK.get(new ResourceLocation(id.getNamespace(), want));
        if (b == Blocks.AIR) {
            return state;
        }
        BlockState out = b.defaultBlockState();
        for (Property<?> prop : state.getProperties()) {
            if (out.hasProperty(prop)) {
                out = copy(state, out, prop);
            }
        }
        return out;
    }

    private static <T extends Comparable<T>> BlockState copy(BlockState from, BlockState to, Property<T> p) {
        return to.setValue(p, from.getValue(p));
    }

    static void complete(ServerLevel level, RuinSites.Site s, @Nullable ServerPlayer player) {
        IndustryMachine machine = IndustryMachine.byKey(s.machine);
        if (machine == null || !(level.getBlockEntity(s.controller()) instanceof DerelictControllerTile tile)) {
            return;
        }
        Direction forward = Direction.from2DDataValue(tile.forward());
        IndustrialBlueprint bp = IndustrialBlueprint.of(machine);
        Map<BlockPos, ProjectedStructure.Ghost> rel = bp.layout(0, BlockPos.ZERO, forward);
        Block ctrl = bp.controller();
        BlockPos at = null;
        for (Map.Entry<BlockPos, ProjectedStructure.Ghost> e : rel.entrySet()) {
            if (e.getValue().state() != null && e.getValue().state().getBlock() == ctrl) {
                at = e.getKey();
            }
        }
        if (at == null) {
            return;
        }
        BlockPos anchor = s.controller().subtract(at);
        //Les restes delabres de la boite disparaissent, puis le plan est pose au tier de la ruine
        for (BlockPos p : BlockPos.betweenClosed(s.min.offset(-2, -2, -2), s.max.offset(2, 2, 2))) {
            if (level.getBlockState(p).getBlock() instanceof DerelictBlock && !p.equals(s.controller())) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        level.setBlock(s.controller(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        for (Map.Entry<BlockPos, ProjectedStructure.Ghost> e : bp.layout(0, anchor, forward).entrySet()) {
            //case vide du plan (null) : vide, sauf si ce qui s'y trouve convient deja
            ProjectedStructure.Ghost gh = e.getValue();
            if (gh.state() != null) {
                level.setBlock(e.getKey(), tiered(gh.state(), s.tier), Block.UPDATE_ALL);
            } else if (!gh.isSatisfiedBy(level.getBlockState(e.getKey()))) {
                level.setBlock(e.getKey(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        //Plaque des contributeurs, contre la facade du controleur
        BlockPos plaque = s.controller().relative(level.getBlockState(s.controller()).hasProperty(BlockStateProperties.HORIZONTAL_FACING)
              ? level.getBlockState(s.controller()).getValue(BlockStateProperties.HORIZONTAL_FACING) : forward.getOpposite()).above();
        if (level.getBlockState(plaque).isAir()) {
            Direction out = level.getBlockState(s.controller()).hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                  ? level.getBlockState(s.controller()).getValue(BlockStateProperties.HORIZONTAL_FACING) : forward.getOpposite();
            level.setBlock(plaque, RuinBlocks.PLAQUE.getBlock().defaultBlockState().setValue(RestorationPlaqueBlock.FACING, out), Block.UPDATE_ALL);
            if (level.getBlockEntity(plaque) instanceof RestorationPlaqueBlock.Tile pt) {
                pt.setNames(names(level.getServer(), s), s.tier);
            }
        }
        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(s.controller());
        if (be != null) {
            ListTag by = new ListTag();
            s.contributors.keySet().forEach(u -> by.add(net.minecraft.nbt.NbtUtils.createUUID(u)));
            be.getPersistentData().put(RuinApi.RESTORED_BY, by);
            if (be instanceof fr.pioneers.core.block.PiBlockEntity pe && s.project != null && s.project.hasUUID("opener")) {
                pe.setOwner(s.project.getUUID("opener"));
            }
            be.setChanged();
        }
        CompoundTag p = project(s);
        p.putInt("stage", DONE);
        p.putString("service", RuinCatalog.service(s.machine));
        s.state = RuinSites.State.RESTORED;
        RuinSites.get(level).changed();
        level.playSound(null, s.controller(), RpSounds.SERVO_WAKE.get(), SoundSource.BLOCKS, 1.5F, 0.8F);
        fr.pioneers.tech.industry.IndustryEvents.award(level, s.controller(), "ruin_restored_mk" + s.tier);
        for (UUID u : s.contributors.keySet()) {
            ServerPlayer c = level.getServer().getPlayerList().getPlayer(u);
            if (c != null) {
                PioneerApi.gain(c, "restoration.done", 1, s.controller());
                codex(c, s.tier);
                c.sendSystemMessage(Component.translatable("ruins.pioneers.restore.done", ctrl.getName()).withStyle(ChatFormatting.GOLD));
            }
        }
        MinecraftForge.EVENT_BUS.post(new RuinEvents.Restored(player, s, level));
    }

    /** Une entree du Codex du palier de la ruine, pas encore lue, est versee au Carnet de chaque contributeur. */
    private static void codex(ServerPlayer player, int tier) {
        fr.pioneers.core.profile.PioneerProfile prof = fr.pioneers.core.profile.PioneerCap.get(player);
        String id = LoreIndex.pick("mk" + tier, prof.loreRead, player.getRandom());
        if (id != null && prof.loreRead.add(id)) {
            prof.touch();
            fr.pioneers.core.profile.ProfileSync.full(player);
            MinecraftForge.EVENT_BUS.post(new RuinEvents.LoreRead(player, id, LoreIndex.tier(id)));
            player.sendSystemMessage(Component.translatable("ruins.pioneers.restore.codex", Component.translatable("codex.pioneers." + id + ".title"))
                  .withStyle(ChatFormatting.GOLD));
        }
    }

    private static List<String> names(MinecraftServer server, RuinSites.Site s) {
        List<Map.Entry<UUID, Long>> list = new ArrayList<>(s.contributors.entrySet());
        list.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        List<String> out = new ArrayList<>();
        for (Map.Entry<UUID, Long> e : list) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            String name = p != null ? fr.pioneers.core.profile.PioneerCap.get(p).name : server.getProfileCache() == null ? null
                  : server.getProfileCache().get(e.getKey()).map(com.mojang.authlib.GameProfile::getName).orElse(null);
            if (name != null && !name.isBlank()) {
                out.add(name);
            }
            if (out.size() >= 12) {
                break;
            }
        }
        return out;
    }

    /** Services rendus par les ruines restaurees a moins de {@code radius} blocs (le bloc 3 les donne aux villages). */
    public static List<String> servicesNear(ServerLevel level, BlockPos pos, double radius) {
        List<String> out = new ArrayList<>();
        for (RuinSites.Site s : RuinSites.get(level).all()) {
            if (s.state == RuinSites.State.RESTORED && s.project != null && s.controller().distSqr(pos) <= radius * radius) {
                out.add(s.project.getString("service"));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ interface
    static void open(ServerPlayer player, ServerLevel level, RuinSites.Site site) {
        RpNet.send(player, RpActions.RESTORATION_VIEW, view(level, site, player, null));
    }

    private static CompoundTag view(ServerLevel level, RuinSites.Site s, ServerPlayer player, @Nullable Component message) {
        CompoundTag t = new CompoundTag();
        t.putLong("site", s.id);
        t.putString("machine", s.machine);
        t.putInt("tier", s.tier);
        t.putString("state", s.state.name());
        IndustryMachine m = IndustryMachine.byKey(s.machine);
        if (m != null) {
            t.putString("name", Component.Serializer.toJson(IndustrialBlueprint.of(m).controller().getName()));
        }
        t.putInt("minLevel", MIN_LEVEL[Math.max(0, Math.min(2, s.tier - 1))]);
        t.putInt("level", PioneerApi.level(player, "restoration"));
        if (s.project != null) {
            t.put("project", s.project.copy());
        } else {
            Cost c = Catalog.cost(s.machine, s.tier);
            if (c != null) {
                CompoundTag p = new CompoundTag();
                p.putInt("stage", SURVEY);
                ListTag needs = new ListTag();
                double f = PioneerApi.factor(player, "restoration.cost") * PioneerApi.factor(player, "scout.restoration_cost");
                for (Need n : c.items()) {
                    CompoundTag e = new CompoundTag();
                    e.putString("id", n.id());
                    e.putInt("need", (int) Math.ceil(n.count() * f));
                    needs.add(e);
                }
                CompoundTag e = new CompoundTag();
                e.putString("id", money() ? "tokens" : c.ingots().id());
                e.putInt("need", (int) Math.ceil((money() ? tokens.price(c.items()) : c.ingots().count()) * f));
                needs.add(e);
                p.put("needs", needs);
                p.putInt("days", Math.max(1, (int) Math.round(c.days() * PioneerApi.factor(player, "restoration.duration"))));
                t.put("project", p);
            }
        }
        t.putInt("sentinels", awakeSentinels(level, s));
        t.putInt("rubble", s.project != null && s.project.getInt("stage") == CLEARING ? rubble(level, s) : 0);
        if (message != null) {
            t.putString("message", Component.Serializer.toJson(message));
        }
        return t;
    }

    private static void action(ServerPlayer player, CompoundTag t) {
        ServerLevel level = player.serverLevel();
        RuinSites.Site s = RuinSites.get(level).site(t.getLong("site"));
        if (s == null || player.distanceToSqr(s.controller().getCenter()) > RANGE * RANGE) {
            return;
        }
        Component msg = null;
        switch (t.getString("do")) {
            case "open" -> msg = start(player, level, s);
            case "deposit" -> {
                if (s.project != null && s.project.getInt("stage") >= CLEARING && s.project.getInt("stage") < DONE) {
                    int n = deposit(player, level, s);
                    msg = Component.translatable(n > 0 ? "ruins.pioneers.restore.deposited" : "ruins.pioneers.restore.nothing", n);
                }
            }
            case "repair" -> {
                if (s.project != null && s.project.getInt("stage") == CLEARING) {
                    if (awakeSentinels(level, s) > 0) {
                        msg = Component.translatable("ruins.pioneers.restore.sentinels");
                    } else if (rubble(level, s) > 0) {
                        msg = Component.translatable("ruins.pioneers.restore.rubble", rubble(level, s));
                    } else {
                        s.project.putInt("stage", REPAIR);
                        s.project.putLong("lastDay", PioneerApi.day(level));
                        RuinSites.get(level).changed();
                        PioneerApi.gain(player, "restoration.stage", 1, s.controller().above());
                    }
                }
            }
            case "cancel" -> {
                if (s.project != null && s.project.getInt("stage") >= CLEARING && s.project.getInt("stage") < DONE
                    && (player.getUUID().equals(s.project.hasUUID("opener") ? s.project.getUUID("opener") : null) || player.hasPermissions(2))) {
                    cancel(player, level, s);
                    msg = Component.translatable("ruins.pioneers.restore.cancelled");
                }
            }
            default -> {
            }
        }
        RpNet.send(player, RpActions.RESTORATION_VIEW, view(level, s, player, msg));
    }
}
