package fr.pioneers.core.registries;

import fr.pioneers.Pioneers;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.common.SoundActions;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.NotNull;

/**
 * Fluides du mod : l'azote liquide (77 K), obtenu en liquefiant l'azote du collecteur de gaz au condensateur rotatif.
 * Il gele ce qu'il touche.
 */
public final class PiFluids {

    private PiFluids() {
    }

    public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES, Pioneers.MODID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(ForgeRegistries.FLUIDS, Pioneers.MODID);
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Pioneers.MODID);

    public static final RegistryObject<FluidType> LIQUID_NITROGEN_TYPE = FLUID_TYPES.register("liquid_nitrogen", () -> new FluidType(FluidType.Properties.create()
          .descriptionId("fluid.pioneers.liquid_nitrogen").density(808).viscosity(160).temperature(77).canExtinguish(true).supportsBoating(false)
          .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL_POWDER_SNOW).sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY_POWDER_SNOW)) {
        @Override
        public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
            consumer.accept(new IClientFluidTypeExtensions() {
                private static final ResourceLocation STILL = Pioneers.rl("block/liquid_nitrogen_still");
                private static final ResourceLocation FLOW = Pioneers.rl("block/liquid_nitrogen_flow");

                @Override
                public ResourceLocation getStillTexture() {
                    return STILL;
                }

                @Override
                public ResourceLocation getFlowingTexture() {
                    return FLOW;
                }

                @Override
                public int getTintColor() {
                    return 0xE0FFFFFF;
                }
            });
        }
    });

    public static final RegistryObject<FlowingFluid> LIQUID_NITROGEN = FLUIDS.register("liquid_nitrogen", () -> new ForgeFlowingFluid.Source(properties()));
    public static final RegistryObject<FlowingFluid> FLOWING_LIQUID_NITROGEN = FLUIDS.register("flowing_liquid_nitrogen",
          () -> new ForgeFlowingFluid.Flowing(properties()));
    public static final RegistryObject<LiquidBlock> LIQUID_NITROGEN_BLOCK = BLOCKS.register("liquid_nitrogen", () -> new CryogenicLiquidBlock(LIQUID_NITROGEN,
          BlockBehaviour.Properties.of().mapColor(MapColor.ICE).noCollission().strength(100.0F).noLootTable().replaceable().liquid()
                .pushReaction(PushReaction.DESTROY)));
    public static final RegistryObject<Item> LIQUID_NITROGEN_BUCKET = PiItems.ITEMS.register("liquid_nitrogen_bucket",
          () -> new BucketItem(LIQUID_NITROGEN, new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));

    private static ForgeFlowingFluid.Properties properties() {
        return new ForgeFlowingFluid.Properties(LIQUID_NITROGEN_TYPE, LIQUID_NITROGEN, FLOWING_LIQUID_NITROGEN)
              .bucket(LIQUID_NITROGEN_BUCKET).block(LIQUID_NITROGEN_BLOCK).slopeFindDistance(3).levelDecreasePerBlock(2).tickRate(8);
    }

    // ------------------------------------------------------------------
    // Fluides de base (standard items v2) : petrole brut et ses coupes. 1.6 : textures still/flow et seaux de TOUS les fluides du mod faits par
    // tools/gc/unify/fluid_style.py (un seul style, un seul seau ; couleur affichee = displayColor).
    // ------------------------------------------------------------------

    /** Un fluide simple : type + source + ecoulement + bloc + seau. */
    public static final class SimpleFluid {

        public final String name;
        public final RegistryObject<FluidType> type;
        public final RegistryObject<FlowingFluid> source;
        public final RegistryObject<FlowingFluid> flowing;
        public final RegistryObject<LiquidBlock> block;
        public final RegistryObject<Item> bucket;
        private final int tickRate;
        private final int viscosity;

        /** Couleur moyenne de la texture (barres d'interface). */
        public final int average;
        /** 1.0 (lot 3) : tous les fluides simples du mod (couche de rendu translucide). */
        public static final List<SimpleFluid> ALL = new ArrayList<>();

        private SimpleFluid(String name, int density, int viscosity, MapColor color, int average) {
            this(name, density, viscosity, color, average, 300);
        }

        private SimpleFluid(String name, int density, int viscosity, MapColor color, int average, int temperature) {
            //1.6 (unification) : couleur AFFICHEE (celle des textures et du seau : tools/gc/unify/fluid_style.py display)
            this.average = displayColor(average, temperature);
            this.name = name;
            //1.4 : les fluides visqueux (petrole lourd, goudron, melasse...) coulent bien plus lentement, plafond 60 ticks (lave : 30)
            this.tickRate = Math.max(10, Math.min(60, viscosity / 200));
            this.viscosity = viscosity;
            ResourceLocation still = Pioneers.rl("block/" + name + "_still");
            ResourceLocation flow = Pioneers.rl("block/" + name + "_flow");
            type = FLUID_TYPES.register(name, () -> new FluidType(FluidType.Properties.create().descriptionId("fluid_type.pioneers." + name)
                  .density(density).viscosity(viscosity).temperature(temperature).lightLevel(temperature > 900 ? 12 : 0).canExtinguish(false).supportsBoating(false)
                  .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL).sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)) {
                @Override
                public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
                    consumer.accept(new IClientFluidTypeExtensions() {
                        @Override
                        public ResourceLocation getStillTexture() {
                            return still;
                        }

                        @Override
                        public ResourceLocation getFlowingTexture() {
                            return flow;
                        }

                        /** 1.0 (lot 3) : fluides translucides (on voit a travers ; couche translucide posee au demarrage du client). */
                        @Override
                        public int getTintColor() {
                            return 0xC8FFFFFF;
                        }
                    });
                }
            });
            ALL.add(this);
            source = FLUIDS.register(name, () -> new ForgeFlowingFluid.Source(props()));
            flowing = FLUIDS.register("flowing_" + name, () -> new ForgeFlowingFluid.Flowing(props()));
            block = BLOCKS.register(name, () -> new LiquidBlock(source, BlockBehaviour.Properties.of().mapColor(color).noCollission().strength(100.0F)
                  .noLootTable().replaceable().liquid().pushReaction(PushReaction.DESTROY)));
            bucket = PiItems.ITEMS.register(name + "_bucket", () -> new BucketItem(source, new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));
        }

        /** Fluide d'un autre registre (age industriel) : densite (negative = plus leger que l'air), viscosite, couleur, temperature (K). */
        public static SimpleFluid create(String name, int density, int viscosity, MapColor color, int average, int temperature) {
            return new SimpleFluid(name, density, viscosity, color, average, temperature);
        }

        private ForgeFlowingFluid.Properties props() {
            //Fluides visqueux : s'etalent peu et lentement (tickRate ~ viscosite)
            //1.4 : tres visqueux (>= 3 000) : s'etale sur deux blocs a peine
            boolean thick = viscosity >= 3_000;
            return new ForgeFlowingFluid.Properties(type, source, flowing).bucket(bucket).block(block).slopeFindDistance(thick ? 1 : 2)
                  .levelDecreasePerBlock(thick ? 3 : 2).tickRate(tickRate);
        }
    }

    public static final SimpleFluid RAW_OIL = new SimpleFluid("raw_oil", 900, 3000, MapColor.COLOR_BLACK, 0x281E11);
    public static final SimpleFluid HEAVY_OIL = new SimpleFluid("heavy_oil", 980, 6000, MapColor.COLOR_BROWN, 0x1B140C);
    public static final SimpleFluid LIGHT_OIL = new SimpleFluid("light_oil", 800, 1500, MapColor.COLOR_ORANGE, 0xAC7125);
    public static final SimpleFluid DIESEL = new SimpleFluid("diesel", 830, 1000, MapColor.COLOR_YELLOW, 0xBDAD4E);
    /** Creosote (age mecanique) : recueillie sous les fosses a charbon ; goudronne les cordes, traite le bois, brule en chaudiere. */
    public static final SimpleFluid CREOSOTE = new SimpleFluid("creosote", 1080, 2500, MapColor.COLOR_BROWN, 0x2E1D0E);
    /** Ordre de l'onglet creatif. */
    public static final List<SimpleFluid> BASIC = List.of(RAW_OIL, HEAVY_OIL, LIGHT_OIL, DIESEL, CREOSOTE);

    /** Couleur affichee de l'azote liquide (= displayColor(0xC8EBFF, 77)). */
    public static final int LIQUID_NITROGEN_COLOR = 0xD8F0FF;
    /** Couleur du lait (fluide de Forge active par la cuisine). */
    public static final int MILK_COLOR = 0xF4F2EC;

    /**
     * 1.6 (unification des fluides) : couleur AFFICHEE d'un fluide a partir de sa couleur et de sa temperature, meme formule que
     * {@code tools/gc/unify/fluid_style.py display} (textures, seaux) : incandescent au-dela de 750 K (metaux et sels fondus), pale et givre sous
     * 150 K (cryogenie), sinon la couleur telle quelle.
     */
    public static int displayColor(int rgb, int kelvin) {
        if (kelvin >= 750) {
            float k = Math.min(1F, (kelvin - 750) / 650F);
            return mix(rgb, 0xFF7418, 0.5F + 0.28F * k);
        }
        if (kelvin <= 150) {
            return mix(rgb, 0xECF6FF, 0.45F);
        }
        return rgb & 0xFFFFFF;
    }

    private static int mix(int a, int b, float t) {
        int out = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            int ca = (a >> shift) & 0xFF, cb = (b >> shift) & 0xFF;
            out |= Math.max(0, Math.min(255, Math.round(ca + (cb - ca) * t))) << shift;
        }
        return out;
    }

    /** Couleurs des fluides (source et ecoulement), remplie au premier appel (registre fige). */
    private static Map<Fluid, Integer> colors;

    /**
     * Couleur moyenne d'un fluide (0 si inconnue) : 1.6, TOUS les fluides du mod (couleur affichee, = textures et seaux), l'azote liquide
     * et le lait ; l'eau et la lave gardent leur teinte (interface).
     */
    public static int averageColor(Fluid fluid) {
        Map<Fluid, Integer> map = colors;
        if (map == null) {
            map = new IdentityHashMap<>();
            for (SimpleFluid simple : SimpleFluid.ALL) {
                map.put(simple.source.get(), simple.average);
                map.put(simple.flowing.get(), simple.average);
            }
            map.put(LIQUID_NITROGEN.get(), LIQUID_NITROGEN_COLOR);
            map.put(FLOWING_LIQUID_NITROGEN.get(), LIQUID_NITROGEN_COLOR);
            colors = map;
        }
        Integer c = map.get(fluid);
        if (c != null) {
            return c;
        }
        ResourceLocation id = ForgeRegistries.FLUIDS.getKey(fluid);
        return id != null && "minecraft".equals(id.getNamespace()) && id.getPath().endsWith("milk") ? MILK_COLOR : 0;
    }

    public static void init() {
    }

    /** Azote liquide : gele et ralentit ce qui s'y baigne, eteint le feu. */
    private static class CryogenicLiquidBlock extends LiquidBlock {

        CryogenicLiquidBlock(Supplier<? extends FlowingFluid> fluid, Properties properties) {
            super(fluid, properties);
        }

        @Override
        @SuppressWarnings("deprecation")
        public void entityInside(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Entity entity) {
            super.entityInside(state, level, pos, entity);
            if (!level.isClientSide && entity instanceof LivingEntity living && entity.canFreeze()) {
                living.setTicksFrozen(Math.min(living.getTicksRequiredToFreeze() + 60, living.getTicksFrozen() + 6));
                living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2, false, false));
                living.clearFire();
            }
        }
    }
}
