package fr.pioneers.client.season;

import fr.pioneers.Pioneers;
import fr.pioneers.season.BiomeClimates;
import fr.pioneers.season.GeneratedLeafTints;
import fr.pioneers.season.SeasonClimate;
import fr.pioneers.season.SeasonTint;
import fr.pioneers.season.Seasons;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.FoliageColor;
import net.minecraft.world.level.GrassColor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 1.0.5-beta (lot 2, specification §1.2) : TEINTES SAISONNIERES cote client, par evenements Forge seulement (aucun mixin) :
 * <ul>
 *     <li>resolveurs de couleur ({@code RegisterColorHandlersEvent.ColorResolvers}) : herbe et feuillage du biome, bouleau, epicea et
 *     nenuphar (couleurs fixes en vanilla), et (lot 14) chene et chene noir, chacun passe par {@link SeasonTint#apply} avec la famille du
 *     biome (et le marais) ; le jeu les melange entre biomes voisins et les garde en cache comme ses propres couleurs ;</li>
 *     <li>les gestionnaires de couleur des blocs que le vanilla colore par biome (herbe, fougeres, hautes herbes, canne a sucre, petales,
 *     feuillus, lianes, bouleau, epicea, nenuphar) et des feuilles fruitieres du mod ({@link GeneratedLeafTints}) lisent ces resolveurs ;
 *     l'eau, la redstone, les tiges et toutes les cultures et pousses du mod gardent leur couleur ;</li>
 *     <li>la teinte avance par PALIERS (16 par saison, {@link SeasonTint#phase}) : a chaque changement de palier, les caches de teinte sont
 *     vides et les troncons redessines PROGRESSIVEMENT, par colonnes, du plus proche au plus lointain, en {@link #WAVE_TICKS} ticks au plus
 *     (pas d'a-coup) ; entre deux paliers, rien n'est recalcule.</li>
 * </ul>
 */
public final class SeasonTints {

    /** Duree d'une vague de re-rendu (ticks) ; au moins {@link #MIN_COLUMNS} colonnes de troncons par tick. */
    public static final int WAVE_TICKS = 60, MIN_COLUMNS = 4;
    /** Nenuphar pose (couleur fixe du vanilla) et dans l'inventaire. */
    public static final int LILY_PLACED = 2129968, LILY_ITEM = 7455580;

    private static volatile SeasonTint.State state;
    private static SeasonTint.Phase phase;
    private static final ArrayDeque<long[]> WAVE = new ArrayDeque<>();
    private static int perTick = MIN_COLUMNS;

    public static final ColorResolver GRASS = (biome, x, z) -> tint(biome.getGrassColor(x, z), SeasonTint.Kind.GRASS, biome);
    public static final ColorResolver FOLIAGE = (biome, x, z) -> tint(biome.getFoliageColor(), SeasonTint.Kind.FOLIAGE, biome);
    public static final ColorResolver BIRCH = (biome, x, z) -> tint(FoliageColor.getBirchColor(), SeasonTint.Kind.BIRCH, biome);
    public static final ColorResolver SPRUCE = (biome, x, z) -> tint(FoliageColor.getEvergreenColor(), SeasonTint.Kind.CONIFER, biome);
    public static final ColorResolver LILY = (biome, x, z) -> tint(LILY_PLACED, SeasonTint.Kind.LILY, biome);
    /** 1.0.5-beta (lot 14) : chene (roux a l'automne) et chene noir (rouge), sur la couleur de feuillage du biome. */
    public static final ColorResolver OAK = (biome, x, z) -> tint(biome.getFoliageColor(), SeasonTint.Kind.OAK, biome);
    public static final ColorResolver DARK_OAK = (biome, x, z) -> tint(biome.getFoliageColor(), SeasonTint.Kind.DARK_OAK, biome);

    private SeasonTints() {
    }

    /** Etat de la teinte en cours (null : pas de saison ici, couleurs vanilla). */
    @Nullable
    public static SeasonTint.State state() {
        return state;
    }

    static int tint(int rgb, SeasonTint.Kind kind, Biome biome) {
        SeasonTint.State st = state;
        if (st == null) {
            return rgb;
        }
        BiomeClimates.Info info = info(biome);
        //1.0.5-beta (lot 14) : marais = herbe a la teinte des marais du vanilla (marais, marais de mangroves) : contraste adouci, sans rouge
        boolean wetland = biome.getSpecialEffects().getGrassColorModifier() == net.minecraft.world.level.biome.BiomeSpecialEffects.GrassColorModifier.SWAMP;
        return info == null ? rgb : SeasonTint.apply(rgb, kind, info.family(), info.savanna(), wetland, st);
    }

    @Nullable
    static BiomeClimates.Info info(Biome biome) {
        BiomeClimates.Info info = BiomeClimates.cached(biome);
        if (info != null) {
            return info;
        }
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? null : BiomeClimates.of(level.registryAccess().registryOrThrow(Registries.BIOME).wrapAsHolder(biome));
    }

    static int at(@Nullable BlockAndTintGetter level, @Nullable BlockPos pos, ColorResolver resolver, int fallback) {
        return level != null && pos != null ? level.getBlockTint(pos, resolver) : fallback;
    }

    /** Couleur d'une feuille fruitiere du mod (teinte de l'essence, passee par la saison ; famille temperee). */
    public static int fruit(int index, boolean inWorld) {
        int rgb = GeneratedLeafTints.TINT[index];
        SeasonTint.State st = state;
        return inWorld && st != null ? SeasonTint.apply(rgb, GeneratedLeafTints.KIND[index], SeasonClimate.Family.TEMPERATE, false, st) : rgb;
    }

    // ------------------------------------------------------------------ enregistrements (bus du mod)

    @Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {

        private ModEvents() {
        }

        @SubscribeEvent
        public static void resolvers(RegisterColorHandlersEvent.ColorResolvers event) {
            for (ColorResolver r : new ColorResolver[]{GRASS, FOLIAGE, BIRCH, SPRUCE, LILY, OAK, DARK_OAK}) {
                event.register(r);
            }
        }

        /** Apres le vanilla (memes regles que BlockColors.createDefault 1.20.1), a la place de ses gestionnaires. */
        @SubscribeEvent(priority = EventPriority.LOW)
        public static void blocks(RegisterColorHandlersEvent.Block event) {
            event.register((s, l, p, t) -> at(l, p == null ? null : s.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER ? p.below() : p, GRASS,
                  GrassColor.getDefaultColor()), Blocks.LARGE_FERN, Blocks.TALL_GRASS);
            event.register((s, l, p, t) -> at(l, p, GRASS, GrassColor.getDefaultColor()), Blocks.GRASS_BLOCK, Blocks.FERN, Blocks.GRASS, Blocks.POTTED_FERN);
            event.register((s, l, p, t) -> t != 0 ? at(l, p, GRASS, GrassColor.getDefaultColor()) : -1, Blocks.PINK_PETALS);
            event.register((s, l, p, t) -> at(l, p, GRASS, -1), Blocks.SUGAR_CANE);
            event.register((s, l, p, t) -> at(l, p, FOLIAGE, FoliageColor.getDefaultColor()), Blocks.JUNGLE_LEAVES, Blocks.ACACIA_LEAVES, Blocks.VINE,
                  Blocks.MANGROVE_LEAVES);
            //1.0.5-beta (lot 14) : une couleur d'automne par essence (chene roux, chene noir rouge)
            event.register((s, l, p, t) -> at(l, p, OAK, FoliageColor.getDefaultColor()), Blocks.OAK_LEAVES);
            event.register((s, l, p, t) -> at(l, p, DARK_OAK, FoliageColor.getDefaultColor()), Blocks.DARK_OAK_LEAVES);
            event.register((s, l, p, t) -> at(l, p, BIRCH, FoliageColor.getBirchColor()), Blocks.BIRCH_LEAVES);
            event.register((s, l, p, t) -> at(l, p, SPRUCE, FoliageColor.getEvergreenColor()), Blocks.SPRUCE_LEAVES);
            event.register((s, l, p, t) -> at(l, p, LILY, LILY_ITEM), Blocks.LILY_PAD);
            for (int i = 0; i < GeneratedLeafTints.IDS.length; i++) {
                Block b = ForgeRegistries.BLOCKS.getValue(Pioneers.rl(GeneratedLeafTints.IDS[i]));
                if (b != null && b != Blocks.AIR) {
                    int index = i;
                    event.register((s, l, p, t) -> t == 0 ? fruit(index, l != null && p != null) : -1, b);
                }
            }
        }

        /** Feuilles fruitieres dans l'inventaire : teinte d'ete de l'essence (le feuillage est en gris). */
        @SubscribeEvent
        public static void items(RegisterColorHandlersEvent.Item event) {
            for (int i = 0; i < GeneratedLeafTints.IDS.length; i++) {
                ResourceLocation id = Pioneers.rl(GeneratedLeafTints.IDS[i]);
                var item = ForgeRegistries.ITEMS.getValue(id);
                if (item != null) {
                    int index = i;
                    event.register((stack, t) -> t == 0 ? fruit(index, false) : -1, item);
                }
            }
        }
    }

    // ------------------------------------------------------------------ paliers et vague de re-rendu (bus Forge)

    @Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class TintForgeEvents {

        private TintForgeEvents() {
        }

        @SubscribeEvent
        public static void onTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            ClientLevel level = mc.level;
            Seasons.Date d = Seasons.now(level);
            SeasonTint.Phase now = d == null ? null : SeasonTint.phase(d);
            if (!java.util.Objects.equals(now, phase)) {
                phase = now;
                state = now == null ? null : SeasonTint.state(now);
                if (level != null && mc.player != null) {
                    level.clearTintCaches();
                    startWave(mc, level);
                }
            }
            if (level != null && !WAVE.isEmpty()) {
                int minY = level.getMinSection(), maxY = level.getMaxSection();
                for (int k = 0; k < perTick && !WAVE.isEmpty(); k++) {
                    long[] c = WAVE.poll();
                    for (int y = minY; y < maxY; y++) {
                        mc.levelRenderer.setSectionDirty((int) c[0], y, (int) c[1]);
                    }
                }
            }
        }

        @SubscribeEvent
        public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
            phase = null;
            state = null;
            WAVE.clear();
        }
    }

    /** Colonnes de troncons a redessiner, des plus proches aux plus lointaines ; toutes en {@link #WAVE_TICKS} ticks au plus. */
    static void startWave(Minecraft mc, ClientLevel level) {
        WAVE.clear();
        int r = mc.options.getEffectiveRenderDistance() + 1;
        int cx = mc.player.chunkPosition().x, cz = mc.player.chunkPosition().z;
        List<long[]> cols = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz <= r * r) {
                    cols.add(new long[]{cx + dx, cz + dz, dx * dx + dz * dz});
                }
            }
        }
        cols.sort(Comparator.comparingLong(c -> c[2]));
        WAVE.addAll(cols);
        perTick = Math.max(MIN_COLUMNS, (cols.size() + WAVE_TICKS - 1) / WAVE_TICKS);
    }
}
