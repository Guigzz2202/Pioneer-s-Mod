package fr.pioneers.village;

import fr.pioneers.Pioneers;
import fr.pioneers.core.profile.PioneerApi;
import fr.pioneers.core.profile.PioneerCap;
import fr.pioneers.core.profile.PioneerProfile;
import fr.pioneers.core.profile.ProfileSync;
import fr.pioneers.reputation.PeopleApi;
import fr.pioneers.ruins.RuinSites;
import fr.pioneers.ruins.gen.RuinLocator;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * 1.9 (MAJ RP, bloc 3) : les Confidences (palier 5, une seule fois par villageois et par joueur) : l'emplacement d'une ruine proche, le code
 * de la Console de la ruine la plus proche (ecrit dans le Carnet), ou une cache enfouie a moins de 200 blocs (scraps ; semences rares chez
 * les Mains Nues). Rien n'est verrouille : ces secrets existent aussi ailleurs, l'affinite les rend seulement plus faciles a trouver.
 */
final class Confidences {

    private Confidences() {
    }

    /** Une confidence : cle de la replique et ses arguments. */
    record Secret(String line, Object[] args) {
    }

    @Nullable
    static Secret give(ServerLevel level, ServerPlayer player, Villager v, VillagerLore lore, @Nullable String forced) {
        RandomSource r = v.getRandom();
        boolean rural = VillageCatalog.doctrine(PeopleApi.doctrine(level.getServer(), lore.people)).rural();
        List<String> kinds = new ArrayList<>(forced != null ? List.of(forced) : List.of("ruin", "code", "cache"));
        java.util.Collections.shuffle(kinds, new java.util.Random(r.nextLong()));
        for (String kind : kinds) {
            Secret s = switch (kind) {
                case "ruin" -> ruin(level, player, v, r);
                case "code" -> code(level, player, v, r);
                default -> cache(level, player, v, r, rural);
            };
            if (s != null) {
                return s;
            }
        }
        return null;
    }

    private static String line(String section, RandomSource r) {
        List<String> l = VillageCatalog.lines("common/confidence/" + section);
        return l.isEmpty() ? "" : l.get(r.nextInt(l.size()));
    }

    /** « vers le nord-est, a environ 300 blocs » */
    static Component direction(BlockPos from, BlockPos to) {
        double dx = to.getX() - from.getX(), dz = to.getZ() - from.getZ();
        int dist = (int) (Math.round(Math.sqrt(dx * dx + dz * dz) / 50.0) * 50);
        double ang = Math.toDegrees(Math.atan2(dx, -dz));
        String[] dirs = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};
        String d = dirs[(int) Math.floorMod(Math.round(ang / 45.0), 8)];
        return Component.translatable("village.pioneers.direction", Component.translatable("reputation.pioneers.dir." + d), Math.max(50, dist));
    }

    private static void journal(ServerPlayer player, String key, String... args) {
        PioneerProfile p = PioneerCap.get(player);
        p.journal(PioneerApi.day(player.getServer()), key, args);
        p.touch();
        ProfileSync.full(player);
    }

    @Nullable
    private static Secret ruin(ServerLevel level, ServerPlayer player, Villager v, RandomSource r) {
        for (RuinLocator.Spot s : RuinLocator.near(level, v.blockPosition(), 1200)) {
            //une ruine que le joueur n'a pas deja entamee
            RuinSites.Site site = RuinSites.get(level).siteAt(s.pos().atY(v.getBlockY()), 16);
            if (site != null && site.state != RuinSites.State.INTACT) {
                continue;
            }
            journal(player, "journal.pioneers.confidence_ruin", "MK" + "I".repeat(s.tier()), String.valueOf(s.pos().getX()), String.valueOf(s.pos().getZ()));
            return new Secret(line("ruin", r), new Object[]{direction(v.blockPosition(), s.pos())});
        }
        return null;
    }

    @Nullable
    private static Secret code(ServerLevel level, ServerPlayer player, Villager v, RandomSource r) {
        RuinSites.Site best = null;
        double bd = 1500 * 1500;
        for (RuinSites.Site s : RuinSites.get(level).all()) {
            double d = s.controller().distSqr(v.blockPosition());
            if (d < bd && s.state != RuinSites.State.RESTORED) {
                bd = d;
                best = s;
            }
        }
        if (best == null) {
            return null;
        }
        String code = String.format("%04d", best.code);
        journal(player, "journal.pioneers.confidence_code", code, String.valueOf(best.controller().getX()), String.valueOf(best.controller().getZ()));
        return new Secret(line("code", r), new Object[]{code});
    }

    @Nullable
    private static Secret cache(ServerLevel level, ServerPlayer player, Villager v, RandomSource r, boolean rural) {
        for (int tries = 0; tries < 12; tries++) {
            double ang = r.nextDouble() * Math.PI * 2;
            int dist = 60 + r.nextInt(140);
            int x = v.getBlockX() + (int) (Math.cos(ang) * dist), z = v.getBlockZ() + (int) (Math.sin(ang) * dist);
            //le tronçon est charge (ou genere) a la demande : une seule fois, au moment de la confidence
            int y = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
            BlockPos top = new BlockPos(x, y - 1, z);
            if (!level.getBlockState(top).isSolidRender(level, top) || level.getFluidState(top.above()).isSource()) {
                continue;
            }
            BlockPos chest = top.below(1);
            level.setBlock(chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, net.minecraft.core.Direction.NORTH), 2);
            if (level.getBlockEntity(chest) instanceof ChestBlockEntity be) {
                be.setLootTable(new ResourceLocation(Pioneers.MODID, rural ? "chests/village_cache_seeds" : "chests/village_cache_scraps"),
                      level.getRandom().nextLong());
            }
            journal(player, rural ? "journal.pioneers.confidence_seeds" : "journal.pioneers.confidence_cache", String.valueOf(x), String.valueOf(z));
            return new Secret(line(rural ? "seeds" : "cache", r), new Object[]{direction(v.blockPosition(), chest)});
        }
        return null;
    }
}
