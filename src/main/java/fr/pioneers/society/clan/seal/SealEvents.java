package fr.pioneers.society.clan.seal;

import fr.pioneers.Pioneers;
import fr.pioneers.society.clan.Clan;
import fr.pioneers.society.clan.ClanApi;
import fr.pioneers.society.clan.ClanData;
import fr.pioneers.society.clan.Permission;
import fr.pioneers.society.clan.core.ClanMessages;
import fr.pioneers.society.clan.territory.ClanTerritory;
import fr.pioneers.society.clan.territory.TerritoryDefs;
import fr.pioneers.core.profile.PioneerCap;
import fr.pioneers.core.profile.PioneerProfile;
import fr.pioneers.core.profile.ProfileSync;
import fr.pioneers.world.ruins.LoreItem;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.PistonEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 1.9 (MAJ RP, bloc 4) : effets des Sceaux dans le monde. Mairie (clic droit : interface du clan ; banniere posee dessus : embleme ;
 * apparition des membres), Baraquement (apparition de secours, sommeil plus reparateur), Archives (depot et lecture des pages et notes),
 * Tour de guet (alerte des gardes, controle espace). Une designation tombe des que son bloc change (notification du bloc, piston).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class SealEvents {

    /** Cle de {@code Clan.extra} : ce que les membres ont depose aux Archives. */
    static final String ARCHIVES = "t_archives";
    /** Dernieres alertes de Tour de guet : clan + intrus -> instant (ms). */
    private static final Map<String, Long> ALERTED = new HashMap<>();
    /** Dernier clan dans le territoire duquel chaque joueur se tenait (null : hors territoire). */
    private static final Map<UUID, UUID> WHERE = new HashMap<>();

    private SealEvents() {
    }

    // ------------------------------------------------------------------ chute
    @SubscribeEvent
    public static void onNotify(BlockEvent.NeighborNotifyEvent event) {
        if (event.getLevel() instanceof ServerLevel level && ClanSeals.designated(level, event.getPos())) {
            check(level, event.getPos());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!event.isCanceled() && event.getLevel() instanceof ServerLevel level && ClanSeals.designated(level, event.getPos())) {
            ClanSeals.fall(level.getServer(), level, event.getPos());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPiston(PistonEvent.Pre event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        PistonStructureResolver s = event.getStructureHelper();
        if (s == null || !s.resolve()) {
            return;
        }
        for (BlockPos p : s.getToPush()) {
            if (ClanSeals.designated(level, p)) {
                ClanSeals.fall(level.getServer(), level, p);
            }
        }
        for (BlockPos p : s.getToDestroy()) {
            if (ClanSeals.designated(level, p)) {
                ClanSeals.fall(level.getServer(), level, p);
            }
        }
    }

    private static void check(ServerLevel level, BlockPos pos) {
        Map.Entry<Clan, String> e = ClanSeals.at(level.getServer(), level, pos);
        if (e == null) {
            return;
        }
        String was = e.getKey().extra.getCompound(ClanSeals.BLOCKS).getString(ClanSeals.key(level, pos));
        String now = String.valueOf(ForgeRegistries.BLOCKS.getKey(level.getBlockState(pos).getBlock()));
        if (level.getBlockState(pos).isAir() || !was.isEmpty() && !was.equals(now)) {
            ClanSeals.fall(level.getServer(), level, pos);
        }
    }

    // ------------------------------------------------------------------ Mairie et Archives
    /** Avant la protection : un membre qui utilise SA Mairie ou SES Archives n'a pas besoin du droit de batir. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof ServerPlayer p) || event.isCanceled()
            || !ClanSeals.designated(event.getLevel(), event.getPos())) {
            return;
        }
        ItemStack held = event.getItemStack();
        if (held.is(SealItems.FUNCTION_SEAL.get())) {
            return;
        }
        Map.Entry<Clan, String> e = ClanSeals.at(p.getServer(), p.level(), event.getPos());
        if (e == null || !e.getKey().members.containsKey(p.getUUID())) {
            return;
        }
        Clan c = e.getKey();
        switch (e.getValue()) {
            case ClanSeals.TOWN_HALL -> {
                if (held.isEmpty() && !p.isShiftKeyDown()) {
                    openClan(p, c);
                    event.setCanceled(true);
                }
            }
            case ClanSeals.ARCHIVES -> {
                if (held.getItem() instanceof LoreItem lore) {
                    deposit(p, c, held, lore);
                    event.setCanceled(true);
                } else if (held.isEmpty() && !p.isShiftKeyDown()) {
                    read(p, c);
                    event.setCanceled(true);
                }
            }
            default -> {
            }
        }
    }

    /** Interface du clan : celle de l'onglet Clan quand il est branche, sinon la fiche courte du territoire. */
    static void openClan(ServerPlayer p, Clan c) {
        var hook = ClanSeals.clanScreen;
        if (hook != null) {
            hook.accept(p);
            return;
        }
        long now = fr.pioneers.society.clan.core.ClanClock.now();
        p.sendSystemMessage(ClanMessages.tagged(c, Component.translatable("clan.pioneers.seal.hall_sheet", ClanMessages.name(p.getServer(), c),
              c.level, c.points, c.claimCount(), ClanTerritory.budget(c, now), c.chunkload.size(),
              fr.pioneers.society.clan.territory.ClanChunkloading.slots(c), c.functions.size())));
    }

    private static CompoundTag archives(Clan c) {
        if (!c.extra.contains(ARCHIVES)) {
            CompoundTag t = new CompoundTag();
            t.put("pages", new ListTag());
            t.put("notes", new ListTag());
            c.extra.put(ARCHIVES, t);
        }
        return c.extra.getCompound(ARCHIVES);
    }

    /** Un membre depose une page (ou une note) : l'original reste aux Archives, chacun en lit une copie. */
    static void deposit(ServerPlayer p, Clan c, ItemStack stack, LoreItem lore) {
        String id = LoreItem.id(stack);
        if (id == null || LoreItem.SERVICE_CODE.equals(id)) {
            ClanMessages.refuse(p, "clan.pioneers.archives.not_lore");
            return;
        }
        ListTag list = archives(c).getList(lore.kind == LoreItem.Kind.NOTE ? "notes" : "pages", Tag.TAG_STRING);
        for (Tag t : list) {
            if (t.getAsString().equals(id)) {
                ClanMessages.refuse(p, "clan.pioneers.archives.already");
                return;
            }
        }
        list.add(StringTag.valueOf(id));
        archives(c).put(lore.kind == LoreItem.Kind.NOTE ? "notes" : "pages", list);
        if (!p.isCreative()) {
            stack.shrink(1);
        }
        c.log(fr.pioneers.core.profile.PioneerApi.day(p.getServer()), "clan.pioneers.log.archives", ClanMessages.character(p));
        ClanData.get(p.getServer()).changed();
        ClanMessages.tell(p, "clan.pioneers.archives.deposited", archives(c).getList("pages", Tag.TAG_STRING).size(),
              archives(c).getList("notes", Tag.TAG_STRING).size());
    }

    /** Lecture des Archives : chaque page ou note qui manque au Codex du membre y entre (une copie, sans effet de decouverte). */
    static void read(ServerPlayer p, Clan c) {
        PioneerProfile prof = PioneerCap.get(p);
        int fresh = 0;
        for (Tag t : archives(c).getList("pages", Tag.TAG_STRING)) {
            if (prof.loreRead.add(t.getAsString())) {
                fresh++;
            }
        }
        for (Tag t : archives(c).getList("notes", Tag.TAG_STRING)) {
            if (prof.notesRead.add(t.getAsString())) {
                fresh++;
            }
        }
        if (fresh > 0) {
            prof.touch();
            ProfileSync.full(p);
        }
        p.level().playSound(null, p.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8F, 1.0F);
        ClanMessages.tell(p, fresh > 0 ? "clan.pioneers.archives.read" : "clan.pioneers.archives.nothing", fresh);
    }

    /** Banniere posee sur la Mairie : elle devient l'embleme du clan. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level) || !(event.getPlacedBlock().getBlock() instanceof AbstractBannerBlock)
            || !ClanSeals.designated(level, event.getPos().below())) {
            return;
        }
        BlockPos hall = event.getPos().below();
        level.getServer().execute(() -> {
            Map.Entry<Clan, String> e = ClanSeals.at(level.getServer(), level, hall);
            if (e != null && ClanSeals.TOWN_HALL.equals(e.getValue()) && ClanSeals.bannerAbove(level, hall, e.getKey())) {
                ClanData.get(level.getServer()).changed();
                if (event.getEntity() instanceof ServerPlayer p) {
                    ClanMessages.tell(p, "clan.pioneers.seal.emblem");
                }
            }
        });
    }

    // ------------------------------------------------------------------ apparition et repos
    /** Sans lit ni ancre : le membre revient a la Mairie de son clan, sinon a un Baraquement. */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.isEndConquered() || !(event.getEntity() instanceof ServerPlayer p) || p.getRespawnPosition() != null || !ClanApi.enabled()) {
            return;
        }
        Clan c = ClanData.get(p.getServer()).clanOf(p.getUUID());
        if (c == null) {
            return;
        }
        for (String fn : new String[]{ClanSeals.TOWN_HALL, ClanSeals.BARRACKS}) {
            for (String key : ClanSeals.all(c, fn)) {
                ServerLevel level = ClanTerritory.level(p.getServer(), ClanSeals.dimOf(key));
                BlockPos spot = level == null ? null : safeSpot(level, ClanSeals.posOf(key));
                if (spot != null) {
                    p.teleportTo(level, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, p.getYRot(), p.getXRot());
                    ClanMessages.tell(p, "clan.pioneers.seal.spawned", Component.translatable("clan.pioneers.function." + fn));
                    return;
                }
            }
        }
    }

    /** Deux cases libres au-dessus (ou autour) du bloc designe, sur un sol porteur. */
    @Nullable
    static BlockPos safeSpot(ServerLevel level, BlockPos pos) {
        level.getChunk(pos);
        BlockPos[] tries = {pos.above(), pos.north(), pos.south(), pos.east(), pos.west(), pos.above(2)};
        for (BlockPos t : tries) {
            if (level.getBlockState(t).getCollisionShape(level, t).isEmpty() && level.getBlockState(t.above()).getCollisionShape(level, t.above()).isEmpty()
                && !level.getBlockState(t.below()).getCollisionShape(level, t.below()).isEmpty() && level.getFluidState(t).isEmpty()) {
                return t;
            }
        }
        return null;
    }

    /** Reveil apres une nuit pres d'un Baraquement du clan : sommeil plus reparateur (effets des donnees). */
    @SubscribeEvent
    public static void onWake(PlayerWakeUpEvent event) {
        if (event.wakeImmediately() || !(event.getEntity() instanceof ServerPlayer p) || !ClanApi.enabled()) {
            return;
        }
        Clan c = ClanData.get(p.getServer()).clanOf(p.getUUID());
        if (c == null || !ClanSeals.near(c, ClanApi.dim(p.level()), p.blockPosition(), ClanSeals.BARRACKS)) {
            return;
        }
        TerritoryDefs.Function f = TerritoryDefs.get().function(ClanSeals.BARRACKS);
        if (f == null) {
            return;
        }
        p.setHealth(p.getMaxHealth());
        if (f.extra().has("rest_effects")) {
            for (var el : f.extra().getAsJsonArray("rest_effects")) {
                var o = el.getAsJsonObject();
                MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(ResourceLocation.tryParse(o.get("effect").getAsString()));
                if (effect != null) {
                    p.addEffect(new MobEffectInstance(effect, 20 * o.get("seconds").getAsInt(), o.has("amplifier") ? o.get("amplifier").getAsInt() : 0));
                }
            }
        }
        ClanMessages.tell(p, "clan.pioneers.seal.rested");
    }

    // ------------------------------------------------------------------ Tour de guet (controle espace, appele par le territoire)
    /** Chaque joueur connecte : s'il entre dans un territoire qui a une Tour de guet sans en etre membre ni allie, les gardes sont alertes. */
    public static void watch(MinecraftServer server, long now) {
        ClanData data = ClanData.get(server);
        TerritoryDefs.Function f = TerritoryDefs.get().function(ClanSeals.WATCHTOWER);
        long cooldown = 1000L * (f == null ? 300 : f.extraInt("alert_cooldown_seconds", 300));
        Permission guard = Permission.byId(f == null || !f.extra().has("alert_permission") ? "siege" : f.extra().get("alert_permission").getAsString());
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.isSpectator()) {
                continue;
            }
            Clan owner = fr.pioneers.society.clan.territory.ClanAccess.owner(server, p.level(), p.blockPosition());
            UUID before = WHERE.put(p.getUUID(), owner == null ? null : owner.id);
            if (owner == null || owner.id.equals(before) || !owner.functions.containsValue(ClanSeals.WATCHTOWER)) {
                continue;
            }
            ClanApi.Relation rel = ClanApi.relation(server, data.clanOf(p.getUUID()), owner);
            if (rel == ClanApi.Relation.SAME || rel == ClanApi.Relation.ALLY) {
                continue;
            }
            String k = owner.id + "|" + p.getUUID();
            Long last = ALERTED.get(k);
            if (last != null && now - last < cooldown) {
                continue;
            }
            ALERTED.put(k, now);
            Component msg = ClanMessages.tagged(owner, Component.translatable("clan.pioneers.seal.intruder",
                  ClanMessages.arg(ClanMessages.character(p)), p.blockPosition().getX() + " " + p.blockPosition().getZ()));
            for (UUID u : owner.members.keySet()) {
                ServerPlayer g = server.getPlayerList().getPlayer(u);
                if (g != null && (guard == null || ClanTerritory.may(owner, u, guard))) {
                    g.sendSystemMessage(msg);
                    g.playNotifySound(SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 0.6F, 0.5F);
                }
            }
        }
        if (ALERTED.size() > 2048) {
            ALERTED.entrySet().removeIf(e -> now - e.getValue() > cooldown);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        WHERE.remove(event.getEntity().getUUID());
    }
}
