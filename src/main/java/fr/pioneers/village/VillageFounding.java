package fr.pioneers.village;

import fr.pioneers.Pioneers;
import fr.pioneers.reputation.PeopleApi;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.0.3-beta (lot 11b) : VILLAGE FONDE PAR UN JOUEUR (regles pures : {@link FoundingRules}). Le Tableau des besoins est le bloc coeur : sa
 * pose ({@link NeedsBoardBlock#setPlacedBy}) fonde « Village de &lt;joueur&gt; » hors de tout village, fait d'un village sans fondateur celui
 * du joueur, agrandit le sien, est refusee dans le village d'un autre. Les fontaines, lampadaires et autres tableaux poses dans le rayon
 * s'inscrivent au village et l'agrandissent ({@link #onPlace}). Les villageois qui habitent le rayon (lit, sinon poste de travail, sinon la
 * ou ils sont) sont rattaches au village a la fondation puis toutes les 10 s pres d'un joueur ({@link #attach}) ; ceux qui naissent ou
 * arrivent ensuite aussi ({@link Villages#villageFor} prefere le village fonde). Effets pour le fondateur dans son village : prix
 * ({@code PriceService.Partner.founder}), reputation ({@link #repDelta}). Donnees : {@link VillageData.Village#owner} et suivants
 * (SavedData du monde). Un seul fondateur par village ; serveur seulement.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class VillageFounding {

    private static int clock;

    private VillageFounding() {
    }

    // ------------------------------------------------------------------ lecture

    public static boolean isFounder(@Nullable VillageData.Village v, @Nullable UUID player) {
        return v != null && player != null && player.equals(v.owner);
    }

    /** Variation d'affinite appliquee dans ce village (gain x1,5 pour son fondateur). */
    public static int repDelta(@Nullable VillageData.Village v, UUID player, int delta) {
        return FoundingRules.repDelta(delta, isFounder(v, player));
    }

    /** Nom du village : celui donne, sinon « Village de &lt;fondateur&gt; » ; null pour un village de PNJ. */
    @Nullable
    public static Component name(VillageData.Village v) {
        if (v.owner == null) {
            return null;
        }
        return v.name.isEmpty() ? Component.translatable("village.pioneers.founded.default", v.ownerName) : Component.literal(v.name);
    }

    // ------------------------------------------------------------------ fondation

    /** Pose d'un Tableau des besoins par un joueur : fondation, revendication, agrandissement ou refus. Renvoie la decision. */
    public static FoundingRules.Decision onBoardPlaced(ServerLevel level, BlockPos pos, ServerPlayer player) {
        VillageData data = VillageData.get(level);
        VillageData.Village near = data.owned(pos);
        if (near == null) {
            near = data.nearest(pos, FoundingRules.RADIUS);
        }
        if (near == null) {
            //village de PNJ pas encore decouvert : sa cloche
            Optional<BlockPos> bell = level.getPoiManager().findClosest(h -> h.is(PoiTypes.MEETING), pos, FoundingRules.RADIUS,
                  PoiManager.Occupancy.ANY);
            if (bell.isPresent()) {
                near = Villages.create(level, bell.get());
            }
        }
        FoundingRules.Decision d = FoundingRules.decide(near != null, near == null ? null : near.owner, player.getUUID());
        VillageData.Village v = near;
        switch (d) {
            case FOUND -> {
                long h = level.getSeed() * 31L + pos.asLong() * 0x9E3779B97F4A7C15L;
                v = data.create(pos, PeopleApi.pick(h ^ (h >>> 29)));
                v.founded = true;
                v.decor = true;
                v.decorVersion = VillageDecor.VERSION;
                own(v, player);
                v.boards.add(pos.immutable());
                String people = majority(level, v);
                if (people != null) {
                    v.people = people;
                }
                int n = attach(level, v);
                player.sendSystemMessage(Component.translatable("village.pioneers.founded.new", name(v), VillageData.radius(v), n)
                      .withStyle(ChatFormatting.GOLD));
            }
            case CLAIM -> {
                own(v, player);
                if (!v.boards.contains(pos)) {
                    v.boards.add(pos.immutable());
                }
                int n = attach(level, v);
                player.sendSystemMessage(Component.translatable("village.pioneers.founded.claim", name(v), VillageData.radius(v), n)
                      .withStyle(ChatFormatting.GOLD));
            }
            case GROW -> {
                if (!v.boards.contains(pos)) {
                    v.boards.add(pos.immutable());
                }
                attach(level, v);
                player.displayClientMessage(Component.translatable("village.pioneers.founded.grow", name(v), VillageData.radius(v))
                      .withStyle(ChatFormatting.GOLD), true);
            }
            case REFUSED -> player.displayClientMessage(Component.translatable("village.pioneers.founded.refused", name(v))
                  .withStyle(ChatFormatting.RED), true);
            default -> {
            }
        }
        if (d != FoundingRules.Decision.REFUSED) {
            level.playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 0.8F, 1.2F);
        }
        data.changed();
        return d;
    }

    private static void own(VillageData.Village v, ServerPlayer player) {
        v.owner = player.getUUID();
        v.ownerName = player.getGameProfile().getName();
    }

    /** Peuple le plus courant des villageois du rayon (le village fonde garde leur peuple), ou null. */
    @Nullable
    private static String majority(ServerLevel level, VillageData.Village v) {
        Map<String, Integer> n = new HashMap<>();
        int r = VillageData.radius(v);
        for (Villager e : level.getEntitiesOfClass(Villager.class, new AABB(v.center).inflate(r, 32, r))) {
            VillagerLore lore = VillagerLore.get(e);
            if (lore.identified && !lore.people.isEmpty()) {
                n.merge(lore.people, 1, Integer::sum);
            }
        }
        return n.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
    }

    /** Ou habite un villageois : son lit, sinon son poste de travail (meme dimension), sinon sa position. */
    static BlockPos home(ServerLevel level, Villager e) {
        for (MemoryModuleType<GlobalPos> m : java.util.List.of(MemoryModuleType.HOME, MemoryModuleType.JOB_SITE)) {
            Optional<GlobalPos> g = e.getBrain().getMemory(m);
            if (g.isPresent() && g.get().dimension() == level.dimension()) {
                return g.get().pos();
            }
        }
        return e.blockPosition();
    }

    /** Rattache au village les villageois charges qui habitent son rayon (et dont c'est le village fonde le plus proche) ; renvoie leur nombre. */
    public static int attach(ServerLevel level, VillageData.Village v) {
        VillageData data = VillageData.get(level);
        int r = VillageData.radius(v);
        int n = 0;
        for (Villager e : level.getEntitiesOfClass(Villager.class, new AABB(v.center).inflate(r, 48, r))) {
            if (data.owned(home(level, e)) != v) {
                continue;
            }
            VillagerLore lore = VillagerLore.get(e);
            if (!lore.identified) {
                Villagers.identify(level, e);
            }
            if (lore.village == v.id) {
                v.register.computeIfAbsent(e.getUUID(), k -> new LinkedHashMap<>());
                n++;
                continue;
            }
            VillageData.Village old = data.village(lore.village);
            LinkedHashMap<UUID, Integer> reg = old == null ? null : old.register.remove(e.getUUID());
            v.register.put(e.getUUID(), reg == null ? new LinkedHashMap<>() : reg);
            lore.village = v.id;
            lore.people = v.people;
            Villages.syncPeople(e);
            n++;
        }
        data.changed();
        return n;
    }

    // ------------------------------------------------------------------ autres blocs de village, nom, suivi

    /** Fontaine, lampadaire ou tableau pose dans le rayon d'un village fonde : il s'y inscrit (et l'agrandit). */
    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Block b = event.getPlacedBlock().getBlock();
        if (!(b instanceof FountainBlock) && !(b instanceof LamppostBlock)) {
            return;
        }
        VillageData data = VillageData.get(level);
        VillageData.Village v = data.owned(event.getPos());
        if (v == null) {
            return;
        }
        java.util.List<BlockPos> list = b instanceof FountainBlock ? v.fountains : v.lamps;
        if (!list.contains(event.getPos())) {
            list.add(event.getPos().immutable());
            data.changed();
            if (event.getEntity() instanceof ServerPlayer p && isFounder(v, p.getUUID())) {
                p.displayClientMessage(Component.translatable("village.pioneers.founded.grow", name(v), VillageData.radius(v))
                      .withStyle(ChatFormatting.GOLD), true);
            }
        }
    }

    /** Etiquette nommee sur un Tableau de son village : le fondateur renomme le village. Vrai si l'etiquette a servi. */
    public static boolean rename(ServerLevel level, BlockPos pos, ServerPlayer player, ItemStack held) {
        if (!held.is(Items.NAME_TAG) || !held.hasCustomHoverName()) {
            return false;
        }
        VillageData.Village v = VillageData.get(level).owned(pos);
        if (v == null) {
            return false;
        }
        if (!isFounder(v, player.getUUID())) {
            player.displayClientMessage(Component.translatable("village.pioneers.founded.refused", name(v)).withStyle(ChatFormatting.RED), true);
            return true;
        }
        v.name = held.getHoverName().getString().strip();
        if (v.name.length() > 40) {
            v.name = v.name.substring(0, 40);
        }
        VillageData.get(level).changed();
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        player.displayClientMessage(Component.translatable("village.pioneers.founded.renamed", name(v)).withStyle(ChatFormatting.GOLD), true);
        return true;
    }

    /** Toutes les 10 s : tableaux, fontaines et lampadaires disparus retires ; villageois du rayon rattaches (villages pres d'un joueur). */
    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++clock % 200 != 0) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (level.players().isEmpty()) {
                continue;
            }
            VillageData data = VillageData.get(level);
            for (VillageData.Village v : data.all()) {
                if (v.owner == null || level.getNearestPlayer(v.center.getX(), v.center.getY(), v.center.getZ(), 160, false) == null) {
                    continue;
                }
                boolean pruned = v.boards.removeIf(p -> level.isLoaded(p) && !(level.getBlockState(p).getBlock() instanceof NeedsBoardBlock))
                      | v.fountains.removeIf(p -> level.isLoaded(p) && !(level.getBlockState(p).getBlock() instanceof FountainBlock))
                      | v.lamps.removeIf(p -> level.isLoaded(p) && !(level.getBlockState(p).getBlock() instanceof LamppostBlock));
                if (pruned) {
                    data.changed();
                }
                attach(level, v);
            }
        }
    }
}
