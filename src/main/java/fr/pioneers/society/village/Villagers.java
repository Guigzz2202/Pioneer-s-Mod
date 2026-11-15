package fr.pioneers.society.village;

import fr.pioneers.Pioneers;
import fr.pioneers.core.common.PiCommonConfig;
import fr.pioneers.core.profile.PioneerApi;
import fr.pioneers.core.profile.PioneerCap;
import fr.pioneers.core.profile.PioneerProfile;
import fr.pioneers.core.profile.ProfileSync;
import fr.pioneers.core.profile.RpActions;
import fr.pioneers.core.profile.RpConfig;
import fr.pioneers.core.profile.RpItems;
import fr.pioneers.core.profile.RpNet;
import fr.pioneers.core.profile.RpSounds;
import fr.pioneers.society.economy.EconomyApi;
import fr.pioneers.society.economy.Trade;
import fr.pioneers.society.reputation.PeopleApi;
import fr.pioneers.society.reputation.ReputationApi;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingConversionEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 1.9 (MAJ RP, bloc 3) : les villageois refondus. Identite donnee au premier chargement (nom tire des tables du peuple de son village),
 * affinite par joueur dans le registre du village (gains et pertes du tableau du §8, jamais avec le temps), ecran du villageois (Echanger et
 * Parler dans la meme fiche depuis 1.9.1 : la replique s'ecrit dans le panneau de dialogue de la fiche), Confidences au palier 5, Niais et
 * Desoeuvre. Les metiers d'autres mods gardent leur ecran d'origine.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class Villagers {

    /** Les treize metiers du mod, plus le Desoeuvre et le Niais. */
    public static final List<String> KNOWN = List.of("minecraft:armorer", "minecraft:butcher", "minecraft:cartographer", "minecraft:cleric",
          "minecraft:farmer", "minecraft:fisherman", "minecraft:fletcher", "minecraft:leatherworker", "minecraft:librarian", "minecraft:mason",
          "minecraft:shepherd", "minecraft:toolsmith", "minecraft:weaponsmith", "minecraft:none", "minecraft:nitwit");
    public static final double RANGE = 8.0;

    private static final Deque<UUID> PENDING = new ArrayDeque<>();

    private Villagers() {
    }

    static void init() {
        RpNet.onServer(RpActions.VILLAGER_TALK, (p, t) -> withVillager(p, t, Villagers::talk));
        RpNet.onServer(RpActions.VILLAGER_TRADE, (p, t) -> withVillager(p, t, Villagers::trade));
        RpNet.onServer(RpActions.VILLAGER_SHOW_BOOK, (p, t) -> withVillager(p, t, Villagers::showBook));
    }

    public static String profession(Villager v) {
        VillagerProfession pr = v.getVillagerData().getProfession();
        var key = ForgeRegistries.VILLAGER_PROFESSIONS.getKey(pr);
        return key == null ? "minecraft:none" : key.toString();
    }

    public static boolean known(Villager v) {
        return KNOWN.contains(profession(v));
    }

    public static boolean active() {
        return PiCommonConfig.get(RpConfig.VILLAGERS, true) && EconomyApi.enabled();
    }

    // ------------------------------------------------------------------ identite
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof Villager v && !VillagerLore.get(v).identified) {
            //differe : l'identite demande le gestionnaire de points d'interet, jamais pendant le chargement d'un tronçon
            PENDING.add(v.getUUID());
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) {
            return;
        }
        for (int i = 0; i < 8 && !PENDING.isEmpty(); i++) {
            UUID id = PENDING.poll();
            for (ServerLevel level : event.getServer().getAllLevels()) {
                Entity e = level.getEntity(id);
                if (e instanceof Villager v && v.isAlive()) {
                    identify(level, v);
                    break;
                }
            }
        }
    }

    /** Donne son identite a un villageois (une fois) : village, peuple, nom. */
    public static void identify(ServerLevel level, Villager v) {
        VillagerLore lore = VillagerLore.get(v);
        if (lore.identified) {
            return;
        }
        VillageData.Village village = Villages.villageFor(level, v.blockPosition());
        lore.village = village == null ? 0 : village.id;
        lore.people = village == null ? PeopleApi.BARE_HANDS : village.people;
        //1.9 (MAJ RP, bloc 4) : un peuple de joueurs reprend les noms du peuple modele de sa doctrine
        String voice = PeopleApi.model(level.getServer(), lore.people);
        List<String> names = VillageCatalog.names(voice);
        if (!names.isEmpty()) {
            String n = names.get(Math.floorMod(v.getUUID().hashCode(), names.size()));
            lore.name = VillageCatalog.namesAreKeys(voice) ? "#" + n : n;
        }
        lore.identified = true;
        if (!v.hasCustomName() && !lore.name.isEmpty()) {
            v.setCustomName(lore.displayName());
        }
        if (village != null) {
            village.register.computeIfAbsent(v.getUUID(), k -> new java.util.LinkedHashMap<>());
            VillageData.get(level).changed();
        }
        Villages.syncPeople(v);
    }

    // ------------------------------------------------------------------ affinite
    @Nullable
    static VillageData.Village village(ServerLevel level, Villager v) {
        VillagerLore lore = VillagerLore.get(v);
        return lore.village == 0 ? null : VillageData.get(level).village(lore.village);
    }

    public static int affinity(ServerLevel level, Villager v, UUID player) {
        VillageData.Village village = village(level, v);
        if (village != null) {
            return village.affinity(v.getUUID(), player);
        }
        VillagerLore.Bond b = VillagerLore.get(v).peek(player);
        return b == null ? 0 : b.affinity;
    }

    /** Change l'affinite d'un villageois charge (registre du village, sinon le villageois). */
    public static int change(ServerLevel level, Villager v, ServerPlayer player, int delta, String cause) {
        VillageData.Village village = village(level, v);
        VillagerLore lore = VillagerLore.get(v);
        int old = affinity(level, v, player.getUUID());
        int now;
        //1.0.3-beta (lot 11b) : gains x1,5 pour le fondateur de ce village
        delta = VillageFounding.repDelta(village, player.getUUID(), delta);
        if (village != null) {
            now = village.set(v.getUUID(), player.getUUID(), old + delta);
            VillageData.get(level).changed();
        } else {
            VillagerLore.Bond b = lore.bond(player.getUUID());
            b.affinity = Math.max(-100, Math.min(100, old + delta));
            now = b.affinity;
        }
        changed(level, player, village, v.getUUID(), lore, old, now, cause);
        return now;
    }

    /** Change l'affinite de tous les villageois d'un village (charges ou non), meme regle de borne. */
    public static void changeVillage(ServerLevel level, VillageData.Village village, ServerPlayer player, int delta, String cause) {
        delta = VillageFounding.repDelta(village, player.getUUID(), delta);
        for (UUID villager : List.copyOf(village.register.keySet())) {
            int old = village.affinity(villager, player.getUUID());
            int now = village.set(villager, player.getUUID(), old + delta);
            Entity e = level.getEntity(villager);
            changed(level, player, village, villager, e instanceof Villager v ? VillagerLore.get(v) : null, old, now, cause);
        }
        VillageData.get(level).changed();
    }

    /**
     * 1.9.1 (retours de Guillaume) : un rachat (amende, tache) : chaque villageois du village qui en veut au joueur regagne jusqu'a
     * {@code gain} points, sans jamais depasser 0 ; ceux qui ne lui en veulent pas ne bougent pas.
     */
    public static void mendVillage(ServerLevel level, VillageData.Village village, ServerPlayer player, int gain, String cause) {
        gain = VillageFounding.repDelta(village, player.getUUID(), gain);
        for (UUID villager : List.copyOf(village.register.keySet())) {
            int old = village.affinity(villager, player.getUUID());
            int g = RepairRules.villagerGain(gain, old);
            if (g <= 0) {
                continue;
            }
            int now = village.set(villager, player.getUUID(), old + g);
            Entity e = level.getEntity(villager);
            changed(level, player, village, villager, e instanceof Villager v ? VillagerLore.get(v) : null, old, now, cause);
        }
        VillageData.get(level).changed();
    }

    private static void changed(ServerLevel level, ServerPlayer player, @Nullable VillageData.Village village, UUID villager,
          @Nullable VillagerLore lore, int old, int now, String cause) {
        if (old == now) {
            return;
        }
        int t0 = Dialogue.trust(old), t1 = Dialogue.trust(now);
        if (t1 > t0 && t1 >= 2 && lore != null) {
            player.displayClientMessage(Component.translatable("village.pioneers.trust_up", lore.displayName()).withStyle(ChatFormatting.GOLD), true);
            level.playSound(null, player.blockPosition(), RpSounds.TRUST.get(), SoundSource.PLAYERS, 0.6F, 1.0F + 0.08F * t1);
        }
        if (t1 >= 4 && lore != null) {
            VillagerLore.Bond b = lore.bond(player.getUUID());
            if (!b.trust4) {
                b.trust4 = true;
                ReputationApi.applyAct(player, "trust_4", lore.people, "villager" + villager);
            }
        }
        MinecraftForge.EVENT_BUS.post(new VillageAffinityChangedEvent(player, village == null ? 0 : village.id, villager, old, now, cause));
    }

    /** Affinite d'un villageois envers un joueur qui a change ; la reputation du village (moyenne du registre) suit d'elle-meme. */
    public static class VillageAffinityChangedEvent extends Event {

        public final ServerPlayer player;
        public final long village;
        public final UUID villager;
        public final int before, after;
        public final String cause;

        public VillageAffinityChangedEvent(ServerPlayer player, long village, UUID villager, int before, int after, String cause) {
            this.player = player;
            this.village = village;
            this.villager = villager;
            this.before = before;
            this.after = after;
            this.cause = cause;
        }
    }

    // ------------------------------------------------------------------ ecran du villageois
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide || !(event.getTarget() instanceof Villager v) || !(event.getEntity() instanceof ServerPlayer player)
            || !active() || !known(v) || v.isBaby() || v.isSleeping()) {
            return;
        }
        //objets qui agissent sur le villageois (etiquette, oeuf...) : comportement d'origine
        if (event.getItemStack().is(net.minecraft.world.item.Items.NAME_TAG)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        ServerLevel level = player.serverLevel();
        identify(level, v);
        open(level, player, v, null);
    }

    static void open(ServerLevel level, ServerPlayer player, Villager v, @Nullable String message) {
        open(level, player, v, message, null);
    }

    /**
     * 1.9.1 (retours de Guillaume) : la fiche reste ouverte et porte la reponse du villageois ({@code reply}, ecrite dans le panneau de
     * dialogue de la fiche : rien dans le chat, plus de bulle). Un villageois Hostile envers le joueur refuse de parler : la fiche devient un
     * court ecran de refus avec son insulte.
     */
    static void open(ServerLevel level, ServerPlayer player, Villager v, @Nullable String message, @Nullable Component reply) {
        VillagerLore lore = VillagerLore.get(v);
        int a = affinity(level, v, player.getUUID());
        String prof = profession(v);
        VillageData.Village village = village(level, v);
        CompoundTag t = new CompoundTag();
        t.putInt("entity", v.getId());
        t.putString("name", Component.Serializer.toJson(lore.displayName()));
        t.putString("profession", prof);
        t.putString("people", lore.people);
        //1.9 (MAJ RP, bloc 4) : icone du peuple modele et nom du peuple (un peuple de joueurs n'a ni texture ni cle de langue)
        t.putString("icon", PeopleApi.model(level.getServer(), lore.people));
        t.putString("peopleName", Component.Serializer.toJson(ReputationApi.peopleName(level.getServer(), lore.people)));
        t.putInt("affinity", a);
        t.putInt("trust", Dialogue.trust(a));
        String refusal = VillagerTrade.refusal(level, player, v);
        t.putBoolean("trade", refusal == null);
        if (refusal != null) {
            t.putString("refusal", refusal);
        }
        VillagerLore.Bond b = lore.peek(player.getUUID());
        boolean bookShown = b != null && b.book;
        t.putBoolean("book", !bookShown && a >= 20 && hasNotebook(player));
        if (village != null) {
            t.putInt("village", village.average(player.getUUID()));
        }
        if (message != null) {
            t.putString("message", message);
        }
        //1.9.1 : palier Hostile du villageois (-60 ou moins) : il refuse de parler, la fiche montre son insulte et il s'enfuit
        boolean hostile = a <= RepairRules.get().hostility().fleeAffinity();
        t.putBoolean("hostile", hostile);
        if (hostile && reply == null) {
            String insult = pickLine(level, player, v, lore, a);
            if (insult != null && !insult.isEmpty()) {
                reply = Component.translatable(insult);
            }
            VillageHostility.panic(v, player);
        }
        if (reply != null) {
            t.putString("reply", Component.Serializer.toJson(reply));
        }
        RpNet.send(player, RpActions.VILLAGER_VIEW, t);
    }

    /** Replique de Parler (registre du palier d'affinite effective, evenement, services, vocation) ; retenue dans les dernieres. */
    @Nullable
    static String pickLine(ServerLevel level, ServerPlayer player, Villager v, VillagerLore lore, int a) {
        VillageData.Village village = village(level, v);
        long day = PioneerApi.day(level);
        PioneerProfile p = PioneerCap.get(player);
        String event = village == null ? null : Villages.activeEvent(village, day);
        boolean niais = "minecraft:nitwit".equals(profession(v));
        //1.9 (MAJ RP, bloc 4) : le ton suit l'affinite effective (paliers du clan) ; l'affinite elle-meme ne change pas
        int tone = fr.pioneers.society.clan.rep.ClanReputation.effective(player, a, lore.people, v.blockPosition());
        //1.9 (MAJ RP, bloc 4) : un village rallie parle avec les repliques du peuple modele de sa doctrine
        String line = Dialogue.pick(new Dialogue.Context(PeopleApi.model(level.getServer(), lore.people), tone, niais, event,
              village == null ? java.util.Set.of() : village.services, p.vocation.isEmpty() ? null : p.vocation, lore.recent, VillageCatalog::lines,
              VillageCatalog::dictons), v.getRandom());
        lore.remember(line);
        return line;
    }

    private static boolean hasNotebook(ServerPlayer p) {
        return p.getInventory().contains(new net.minecraft.world.item.ItemStack(RpItems.NOTEBOOK.get()));
    }

    private interface Action {

        void run(ServerLevel level, ServerPlayer player, Villager v);
    }

    private static void withVillager(ServerPlayer player, CompoundTag t, Action a) {
        ServerLevel level = player.serverLevel();
        Entity e = level.getEntity(t.getInt("entity"));
        if (e instanceof Villager v && v.isAlive() && player.distanceToSqr(v) <= RANGE * RANGE && active() && known(v)) {
            a.run(level, player, v);
        }
    }

    // ------------------------------------------------------------------ Parler
    static void talk(ServerLevel level, ServerPlayer player, Villager v) {
        //1.9 (MAJ RP, lot 19) : progres « Le monde habite » (racine de l'onglet des villages)
        fr.pioneers.core.registries.PiTriggers.MECHANICS.trigger(player, "village_talk");
        VillagerLore lore = VillagerLore.get(v);
        VillagerLore.Bond b = lore.bond(player.getUUID());
        long day = PioneerApi.day(level);
        int a = affinity(level, v, player.getUUID());
        VillageData.Village village = village(level, v);
        //+2 une fois par jour (palier Reserve ou plus) ; service Archives : +1 de plus
        if (a > -20 && b.talkDay != day) {
            b.talkDay = day;
            int gain = 2 + (village != null && village.services.contains("archives") ? 1 : 0);
            a = change(level, v, player, gain, "talk");
        }
        boolean niais = "minecraft:nitwit".equals(profession(v));
        String line = null;
        Object[] args = new Object[0];
        //Confidences : au palier 5, une seule fois (le Niais en donne une de plus, toujours une ruine)
        if (a >= 80 && !b.confided) {
            Confidences.Secret s = Confidences.give(level, player, v, lore, niais ? "ruin" : null);
            if (s != null) {
                b.confided = true;
                line = s.line();
                args = s.args();
                //1.9 (MAJ RP, lot 19) : progres « Confidences »
                fr.pioneers.core.registries.PiTriggers.MECHANICS.trigger(player, "village_confidence");
            }
        } else if (a >= 80 && niais && !b.extraSecret) {
            Confidences.Secret s = Confidences.give(level, player, v, lore, "ruin");
            if (s != null) {
                b.extraSecret = true;
                line = s.line();
                args = s.args();
            }
        }
        if (line == null) {
            line = pickLine(level, player, v, lore, a);
        }
        if (a <= RepairRules.get().hostility().fleeAffinity()) {
            //il insulte, puis il fuit le joueur (vraie panique vanilla : VillageHostility.panic)
            VillageHostility.panic(v, player);
        }
        //1.9.1 (retours de Guillaume) : la fiche reste ouverte, la replique s'ecrit dans son panneau de dialogue (rien dans le chat)
        open(level, player, v, null, line == null || line.isEmpty() ? Component.empty() : Component.translatable(line, args));
    }

    // ------------------------------------------------------------------ Echanger, Montrer le Carnet
    static void trade(ServerLevel level, ServerPlayer player, Villager v) {
        String refusal = VillagerTrade.refusal(level, player, v);
        if (refusal != null) {
            open(level, player, v, refusal);
            return;
        }
        Trade.open(player, new VillagerTrade(level, v));
    }

    static void showBook(ServerLevel level, ServerPlayer player, Villager v) {
        VillagerLore lore = VillagerLore.get(v);
        VillagerLore.Bond b = lore.bond(player.getUUID());
        int a = affinity(level, v, player.getUUID());
        if (b.book || a < 20 || !hasNotebook(player)) {
            open(level, player, v, null);
            return;
        }
        b.book = true;
        VillageCatalog.Doctrine d = VillageCatalog.doctrine(PeopleApi.doctrine(level.getServer(), lore.people));
        int gain = "minecraft:librarian".equals(profession(v)) ? d.librarianNotebookAffinity() : d.notebookAffinity();
        if (gain > 0) {
            change(level, v, player, gain, "notebook");
        }
        level.playSound(null, v.blockPosition(), RpSounds.PAGE.get(), SoundSource.NEUTRAL, 0.8F, 1.0F);
        open(level, player, v, gain > 0 ? "village.pioneers.book_shown" : "village.pioneers.book_ignored");
    }

    // ------------------------------------------------------------------ gestes qui comptent
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Villager v) || !(event.getSource().getEntity() instanceof ServerPlayer player)
            || !(v.level() instanceof ServerLevel level)) {
            return;
        }
        identify(level, v);
        change(level, v, player, -30, "hit");
        //1.9.1 (retours de Guillaume) : un golem qui voit le coup charge ; la treve du drapeau blanc est rompue
        VillageHostility.struck(level, v, player);
        //les temoins a moins de 16 blocs
        for (Villager w : level.getEntitiesOfClass(Villager.class, v.getBoundingBox().inflate(16), x -> x != v && x.isAlive() && x.hasLineOfSight(v))) {
            identify(level, w);
            change(level, w, player, -10, "witness");
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        if (event.getEntity() instanceof Villager v) {
            VillageData.Village village = village(level, v);
            if (event.getSource().getEntity() instanceof ServerPlayer player) {
                if (village != null) {
                    changeVillage(level, village, player, -100, "kill");
                    //la nouvelle circule : -20 dans les villages du meme peuple a moins de 200 blocs
                    for (VillageData.Village other : VillageData.get(level).within(village.center, 200)) {
                        if (other != village && other.people.equals(village.people)) {
                            changeVillage(level, other, player, -20, "rumor");
                        }
                    }
                }
                ReputationApi.applyAct(player, "kill_villager", VillagerLore.get(v).people, null);
            }
            forget(level, v);
        } else if (event.getEntity() instanceof IronGolem golem && !golem.isPlayerCreated()
                   && event.getSource().getEntity() instanceof ServerPlayer player) {
            VillageData.Village village = VillageData.get(level).nearest(golem.blockPosition(), 64);
            if (village != null) {
                changeVillage(level, village, player, -40, "golem");
            }
        }
    }

    @SubscribeEvent
    public static void onConvert(LivingConversionEvent.Post event) {
        if (event.getEntity() instanceof Villager v && v.level() instanceof ServerLevel level) {
            forget(level, v);
        }
    }

    /** Un villageois mort ou parti quitte le registre de son village. */
    static void forget(ServerLevel level, Villager v) {
        VillageData.Village village = village(level, v);
        if (village != null && village.register.remove(v.getUUID()) != null) {
            VillageData.get(level).changed();
        }
    }

    @SubscribeEvent
    public static void onBreak(net.minecraftforge.event.level.BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        BlockPos pos = event.getPos();
        boolean bed = event.getState().is(net.minecraft.tags.BlockTags.BEDS);
        PoiManager poi = level.getPoiManager();
        boolean job = !bed && poi.getType(pos).map(h -> h.is(net.minecraft.tags.PoiTypeTags.ACQUIRABLE_JOB_SITE)).orElse(false);
        if (!bed && !job) {
            return;
        }
        GlobalPos target = GlobalPos.of(level.dimension(), pos);
        for (Villager v : level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(pos).inflate(64))) {
            Optional<GlobalPos> home = v.getBrain().getMemory(bed ? MemoryModuleType.HOME : MemoryModuleType.JOB_SITE);
            if (home.isPresent() && sameBlock(home.get(), target, bed)) {
                identify(level, v);
                change(level, v, player, -10, bed ? "bed" : "workstation");
            }
        }
    }

    private static boolean sameBlock(GlobalPos a, GlobalPos b, boolean bed) {
        if (!a.dimension().equals(b.dimension())) {
            return false;
        }
        //un lit occupe deux blocs : la tete ou le pied
        return bed ? a.pos().distManhattan(b.pos()) <= 1 : a.pos().equals(b.pos());
    }

    /** Raid gagne (le joueur recoit Heros du village) : +15 chez tous ceux du village, une fois par raid. */
    @SubscribeEvent
    public static void onEffect(net.minecraftforge.event.entity.living.MobEffectEvent.Added event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getEffectInstance().getEffect() != net.minecraft.world.effect.MobEffects.HERO_OF_THE_VILLAGE) {
            return;
        }
        ServerLevel level = player.serverLevel();
        VillageData.Village village = VillageData.get(level).nearest(player.blockPosition(), 96);
        long day = PioneerApi.day(level);
        if (village != null && village.raidDay != day) {
            village.raidDay = day;
            changeVillage(level, village, player, 15, "raid");
            //1.9.1 (retours de Guillaume) : tache de reparation « repousser un raid »
            VillageRepairs.raidWon(level, village, player);
        }
    }

    /** Synchronise le peuple des villageois suivis (calque de la marque). */
    @SubscribeEvent
    public static void onTrack(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof Villager v && event.getEntity() instanceof ServerPlayer p) {
            Villages.sendPeople(p, v);
        }
    }

    /** Toutes les affinites du joueur, par village (onglet Reputation). */
    static Map<Long, Integer> averages(ServerLevel level, Player player) {
        Map<Long, Integer> out = new java.util.HashMap<>();
        for (VillageData.Village v : VillageData.get(level).all()) {
            out.put(v.id, v.average(player.getUUID()));
        }
        return out;
    }

    static void sync(ServerPlayer player) {
        ProfileSync.full(player);
    }
}
