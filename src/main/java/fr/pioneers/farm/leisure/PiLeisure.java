package fr.pioneers.farm.leisure;

import fr.pioneers.Pioneers;
import fr.pioneers.core.registry.BlockEntry;
import fr.pioneers.core.registry.PiCoreRegistries;
import fr.pioneers.space.item.ItemWithDescription;
import fr.pioneers.core.registries.PiParticles;
import fr.pioneers.core.registries.PiSounds;
import fr.pioneers.core.registries.PiTileEntityTypes;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;

/**
 * 1.5 : loisirs de la ferme (demande de Guillaume : « des features fun mais soft comme le tabac pour introduire la pipe et le cigare » ;
 * « instruments de musique »). Purement cosmetique : aucun effet sur le joueur. Tabac : culture et cuisine (tools/gc/farm2/crops.py, foods.py) ;
 * ici les pipes ({@link SmokingPipeItem}), le cigare ({@link CigarItem}, {@link LitCigarItem}), la boite d'allumettes, le cendrier
 * ({@link AshtrayBlock}) et les instruments ({@link InstrumentItem}). Donnees, modeles et sons : {@code tools/gc/farm2/leisure.py}.
 */
public final class PiLeisure {

    private PiLeisure() {
    }

    public static void init() {
    }

    /** De quoi allumer une pipe ou un cigare (allumettes, briquet a silex, briquet). */
    public static final TagKey<Item> LIGHTERS = ItemTags.create(Pioneers.rl("smoking_lighters"));
    public static final TagKey<Item> PIPE_TOBACCO = ItemTags.create(Pioneers.rl("pipe_tobacco"));

    public static final RegistryObject<Item> CORNCOB_PIPE = PiCoreRegistries.ITEMS.register("corncob_pipe",
          () -> new SmokingPipeItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> BRIAR_PIPE = PiCoreRegistries.ITEMS.register("briar_pipe",
          () -> new SmokingPipeItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> CLAY_PIPE = PiCoreRegistries.ITEMS.register("clay_pipe",
          () -> new SmokingPipeItem(new Item.Properties().stacksTo(1)));
    public static final List<RegistryObject<Item>> PIPES = List.of(CORNCOB_PIPE, BRIAR_PIPE, CLAY_PIPE);

    public static final RegistryObject<Item> CIGAR = PiCoreRegistries.ITEMS.register("cigar", () -> new CigarItem(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> LIT_CIGAR = PiCoreRegistries.ITEMS.register("lit_cigar",
          () -> new LitCigarItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> MATCHBOX = PiCoreRegistries.ITEMS.register("matchbox",
          () -> new ItemWithDescription(new Item.Properties().durability(24)));

    public static final Map<Instrument, RegistryObject<Item>> INSTRUMENTS = new LinkedHashMap<>();

    static {
        for (Instrument instrument : Instrument.values()) {
            INSTRUMENTS.put(instrument, PiCoreRegistries.ITEMS.register(instrument.id, () -> new InstrumentItem(instrument,
                  new Item.Properties().stacksTo(1))));
        }
    }

    public static final BlockEntry<AshtrayBlock> ASHTRAY = PiCoreRegistries.block("ashtray", () -> new AshtrayBlock(BlockBehaviour.Properties.of()
          .mapColor(MapColor.COLOR_CYAN).strength(0.5F).sound(SoundType.DECORATED_POT).noOcclusion()));

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<AshtrayTile>> ASHTRAY_TILE = PiTileEntityTypes.VANILLA_TILES.register("ashtray",
          () -> BlockEntityType.Builder.of(AshtrayTile::new, ASHTRAY.getBlock()).build(null));

    /** Rond de fumee (accroupi en soufflant) et volute de fumee (pipe, cigare, cendrier). */
    public static final RegistryObject<SimpleParticleType> SMOKE_RING = PiParticles.PARTICLES.register("smoke_ring", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> PIPE_SMOKE = PiParticles.PARTICLES.register("pipe_smoke", () -> new SimpleParticleType(false));

    private static final Map<String, RegistryObject<SoundEvent>> SOUNDS = new LinkedHashMap<>();

    private static RegistryObject<SoundEvent> sound(String id) {
        return SOUNDS.computeIfAbsent(id, k -> PiSounds.SOUNDS.register(k, () -> SoundEvent.createVariableRangeEvent(Pioneers.rl(k))));
    }

    public static final RegistryObject<SoundEvent> PIPE_PUFF = sound("leisure.pipe_puff");
    public static final RegistryObject<SoundEvent> EXHALE = sound("leisure.exhale");
    public static final RegistryObject<SoundEvent> MATCH = sound("leisure.match");
    public static final RegistryObject<SoundEvent> PIPE_FILL = sound("leisure.pipe_fill");

    static {
        for (Instrument instrument : Instrument.values()) {
            if (instrument.drum) {
                for (String pad : Instrument.PAD_IDS) {
                    sound("leisure.drum_" + pad);
                }
            } else {
                sound("leisure." + instrument.id);
            }
        }
    }

    /** Son d'une touche de l'instrument (frappe du tambour, ou note jouee a partir du son de base). */
    public static SoundEvent sound(Instrument instrument, int key) {
        return SOUNDS.get(instrument.drum ? "leisure.drum_" + Instrument.PAD_IDS[key] : "leisure." + instrument.id).get();
    }
}
