package fr.pioneers.api.machine;

import fr.pioneers.api.machine.MachineSpec.Kind;
import fr.pioneers.api.machine.MachineSpec.Role;
import fr.pioneers.api.machine.MachineSpec.TierMode;
import fr.pioneers.api.machine.tier.GeneratedTierFamilies;
import fr.pioneers.core.Texts;
import fr.pioneers.core.side.SideType;
import fr.pioneers.core.tooltip.Tooltips;
import fr.pioneers.tech.energy.GeneratorBlock;
import fr.pioneers.tech.industry.GeneratorOutput;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.atelier.AtelierBlock;
import fr.pioneers.tech.industry.atelier.DynamoBlock;
import fr.pioneers.tech.industry.atelier.DynamoTile;
import fr.pioneers.tech.industry.multi.Cleanroom;
import fr.pioneers.tech.industry.multi.ControllerBlock;
import fr.pioneers.tech.machine.KitMachineBlock;
import fr.pioneers.tech.machine.MachineKind;
import fr.pioneers.logistics.transport.buffer.BufferBlock;
import fr.pioneers.logistics.transport.buffer.BufferTile;
import fr.pioneers.logistics.transport.buffer.BufferType;
import fr.pioneers.logistics.transport.heat.ElectricHeaterTile;
import fr.pioneers.logistics.transport.pressure.AirCompressorTile;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * API Machine (lot 1) : L'INFOBULLE STANDARD de l'objet de chaque machine, tiree de sa fiche ({@link MachineSpec}). Remplace les
 * {@code appendHoverText} des blocs de machines (atelier, kit, generateurs, buffers, turbine et echappement compacts, dynamo, radiateur
 * electrique, compresseur d'air) et les cas machine de {@code client.PiTooltips}, qui l'appelle pour tout objet de bloc
 * ({@link #forBlock}). Roles et couleurs de {@link Tooltips} (1.11.2) inchanges ; l'ordre d'appel ne compte pas (chaque ligne va dans sa
 * section).
 * <ul>
 *     <li>lignes chiffrees de la famille ({@link MachineSpec#stats()}) : les chiffres restent dans leur source Java ({@link PowerRules},
 *     {@code EnergyKind}, {@code BufferType}, {@code MachineKind}, {@code GeneratorOutput}, constantes des tiles) ;</li>
 *     <li>vitesse au-dessus du MKI, entrees et sorties (resume des ports) ;</li>
 *     <li>tier (MK, creatif) ou age ;</li>
 *     <li>remarque : le multibloc frere ou la version compacte ;</li>
 *     <li>sous Maj : faces reglables ; multibloc : tier des carcasses, et pour un multibloc industriel ordinaire (pas un reacteur, une
 *     fusion ni un calcul) parallelisme par tier, regles industrielles, rendement d'un generateur.</li>
 * </ul>
 * Code commun (sans classe du client) : le banc des registres (tools/api) l'appelle hors du jeu.
 */
public final class MachineTooltips {

    private static final String K = "tooltip.pioneers.api.";
    private static final int MECHANICAL = 0xC8945A, BRONZE = 0xD89A50, INDUSTRIAL = 0x8FA6B8;

    private MachineTooltips() {
    }

    /** Infobulle d'un objet de bloc ({@code ItemTooltipEvent} du client, ou le banc) : rien si le bloc n'est pas une machine de l'API. */
    public static boolean forBlock(Block block, ItemStack stack, @Nullable Level level, List<Component> lines) {
        MachineRegistry.Entry entry = MachineRegistry.of(block);
        if (entry != null) {
            append(Tooltips.event(lines), entry, block, stack, level);
            return true;
        }
        //Lot 7 : postes hors API (etablis, forge primaire) : pastille d'age seulement
        String age = MachineRegistry.stationAge(block);
        if (age != null) {
            station(Tooltips.event(lines), age);
            return true;
        }
        //API Multibloc (lot 5) : pieces et ports des multiblocs (role, tier, multiblocs qui les utilisent)
        return fr.pioneers.api.multiblock.MultiblockTooltips.piece(block, lines);
    }

    /**
     * 1.0.5-beta (lot 38) : ligne du tier global « Tier 1 — Bronze · famille Broyage » (couleur du tier MK pour les machines MK, sinon couleur
     * de la table des tiers globaux).
     */
    public static void globalTier(Tooltips tips, int global, @Nullable String family, int mk) {
        int g = Math.max(0, Math.min(GeneratedTierFamilies.COLORS.length - 1, global));
        int color = mk >= 0 && g >= 2 ? Tooltips.tierColor(mk) : GeneratedTierFamilies.COLORS[g];
        MutableComponent line = Component.translatable("tier.pioneers.global." + g).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(color)));
        if (family != null) {
            line.append(Component.literal(" · ").withStyle(Tooltips.LABEL)).append(Component.translatable("tooltip.pioneers.tier.family_short",
                  Component.translatable("tier.pioneers.family." + family)).withStyle(Tooltips.LABEL));
        }
        tips.add(Tooltips.Section.TIER, line);
    }

    /** Lot 7 : couleur d'un age (mecanique, bronze, industriel). */
    public static int ageColor(String age) {
        return switch (age) {
            case "mechanical" -> MECHANICAL;
            case "bronze" -> BRONZE;
            default -> INDUSTRIAL;
        };
    }

    /** Lot 7 : infobulle standard d'un poste HORS API (etabli, forge primaire) : son age, et sous Maj ce qu'il est. */
    public static void station(Tooltips tips, String age) {
        tips.add(Tooltips.Section.TIER, Component.translatable("tooltip.pioneers.stat", Component.translatable(K + "age").withStyle(Tooltips.LABEL),
              Component.translatable(K + "age." + age).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(ageColor(age))))).withStyle(Tooltips.LABEL));
        tips.details(t -> t.hint(K + "station"));
    }

    public static void append(Tooltips tips, MachineRegistry.Entry entry, Block block, ItemStack stack, @Nullable Level level) {
        MachineSpec spec = entry.spec();
        int tier = entry.tier();
        stats(tips, spec, tier, block, stack, level);
        if (spec.kind() == Kind.PROCESS && spec.tiers() == TierMode.MK && tier > 0 && !"kit".equals(spec.stats())) {
            tips.spec(K + "speed", PowerRules.speed(tier, 0));
        }
        if (spec.kind() == Kind.PROCESS && !spec.isMultiblock()) {
            Component in = summary(spec, Role.IN), out = summary(spec, Role.OUT);
            if (in != null) {
                tips.spec(Component.translatable(K + "inputs", in));
            }
            if (out != null) {
                tips.spec(Component.translatable(K + "outputs", out));
            }
        }
        //1.0.5-beta (lot 38, retour 49) : tier GLOBAL « Tier n — Nom » (T0 mecanique, T1 bronze, T2 MKI .. T5 MKIV) et famille de tiers ;
        //le creatif garde sa ligne
        String id = entry.index() >= 0 && entry.index() < spec.blocks().size() ? spec.blocks().get(entry.index()) : null;
        String family = id == null ? null : GeneratedTierFamilies.FAMILY_OF.get(id.contains(":") ? id.substring(id.indexOf(':') + 1) : id);
        int global = spec.globalTier(tier);
        switch (spec.tiers()) {
            case MK, MK_CREATIVE, SINGLE_MKI -> {
                if (global >= 2) {
                    globalTier(tips, global, family, spec.tiers() == TierMode.SINGLE_MKI ? 0 : tier);
                } else {
                    tips.tier(tier);
                }
            }
            case AGE, TIER -> {
                if (global >= 0) {
                    globalTier(tips, global, family, -1);
                } else {
                    boolean mech = "mechanical".equals(spec.age());
                    tips.add(Tooltips.Section.TIER, Component.translatable("tooltip.pioneers.stat", Component.translatable(K + "age").withStyle(Tooltips.LABEL),
                          Component.translatable(K + "age." + spec.age()).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(mech ? MECHANICAL : BRONZE))))
                          .withStyle(Tooltips.LABEL));
                }
                if (spec.tiers() == TierMode.TIER && spec.kind() == Kind.PROCESS) {
                    //1.0.5-beta (lot 39) : le four a induction a vapeur prend 6 mB/t (GeneratedTierFamilies.steamUse)
                    tips.spec("tooltip.pioneers.tier.steam_use", id == null ? GeneratedTierFamilies.STEAM_USE
                                : GeneratedTierFamilies.steamUse(id.contains(":") ? id.substring(id.indexOf(':') + 1) : id),
                          Texts.format(Math.round(fr.pioneers.tech.bronze.steam.SteamRules.PROCESS_RESERVE)));
                }
            }
            default -> {
            }
        }
        MachineSpec multi = MachineRegistry.byKey(spec.multiblock()), compact = MachineRegistry.byKey(spec.compact());
        if (multi != null) {
            tips.note(K + "multiblock", name(multi));
        }
        //1.0.4-beta (lot 32, Guillaume : « Garder, masquer partout ») : controleur de salle blanche : jamais un tier (ses vraies valeurs)
        IndustryMachine cleanroom = block instanceof ControllerBlock cb && Cleanroom.is(cb.machine) ? cb.machine : null;
        if (compact != null && compact.tiers() == TierMode.MK) {
            tips.note(cleanroom != null ? K + "compact_cleanroom" : K + "compact", name(compact));
        }
        boolean controller = spec.isMultiblock() && spec.tiers() == TierMode.STRUCTURE;
        Component faces = spec.faces() ? faces(spec) : null;
        if (controller || faces != null) {
            tips.details(t -> {
                if (controller && cleanroom != null) {
                    Cleanroom.lines(cleanroom).forEach(t::spec);
                } else if (controller && "controller".equals(spec.ui())) {
                    StringBuilder par = new StringBuilder();
                    for (int i = 0; i < 4; i++) {
                        par.append(i == 0 ? "" : " / ").append(PowerRules.parallelCap(i));
                    }
                    t.spec(K + "parallel", par.toString());
                    t.spec(K + "industrial", pct(PowerRules.INDUSTRIAL_TIME), pct(PowerRules.INDUSTRIAL_ENERGY));
                    if (spec.kind() == Kind.GENERATOR && "controller_generator".equals(spec.stats())) {
                        t.spec(K + "yield", pct(PowerRules.INDUSTRIAL_YIELD));
                    }
                }
                if (controller) {
                    t.hint(cleanroom != null ? K + "cleanroom" : K + "casing_tier");
                    //API Multibloc (lot 5) : taille du plan, ou le voir
                    fr.pioneers.api.multiblock.MultiblockTooltips.controller(t, block);
                }
                if (faces != null) {
                    t.hint(Component.translatable(K + "faces", faces));
                }
            });
        }
    }

    // ------------------------------------------------------------------ lignes chiffrees (une par fournisseur de la fiche)

    private static void stats(Tooltips tips, MachineSpec spec, int tier, Block block, ItemStack stack, @Nullable Level level) {
        switch (spec.stats()) {
            case "industry" -> {
                if (spec.kind() != Kind.GENERATOR) {
                    tips.energy("tooltip.pioneers.stat.consumption", Component.translatable("energy.pioneers.fe.per_tick", Texts.grouped(PowerRules.power(tier))));
                }
            }
            case "combustion" -> tips.energy("tooltip.pioneers.stat.power", Component.translatable("energy.pioneers.fe.nominal",
                  Texts.grouped(GeneratorOutput.solidPerTick(tier))));
            //1.0.3-beta (lot 5) : generateur a combustion liquide et turbine a gaz compacte : la plus forte puissance de leurs recettes au tier
            case "fuel_generator" -> {
                if (level != null && block instanceof AtelierBlock atelier) {
                    long best = GeneratorOutput.bestPerTick(level, atelier.machine, tier, false);
                    if (best > 0) {
                        tips.energy("tooltip.pioneers.stat.power", Component.translatable("energy.pioneers.fe.per_op_max", Texts.grouped(best)));
                    }
                }
            }
            case "kit" -> {
                if (block instanceof KitMachineBlock kit) {
                    if (kit.kind.isGenerator()) {
                        tips.desc("machine.pioneers.generator.rate", Texts.format(kit.kind.power(kit.tier)), kit.kind.burnRate(kit.tier));
                    } else {
                        tips.spec("machine.pioneers.processing.rate", String.format(Locale.ROOT, "%.2f", kit.kind.ticks(kit.tier) / 20.0),
                              Texts.format(kit.kind.power(kit.tier)));
                    }
                    if (kit.tier.ordinal() > 0) {
                        tips.spec("machine.pioneers.tier", MachineKind.speed(kit.tier), MachineKind.cost(kit.tier));
                    }
                }
            }
            case "renewable" -> {
                if (block instanceof GeneratorBlock gen) {
                    tips.energy("tooltip.pioneers.stat.power", Component.translatable("energy.pioneers.tip.peak", Texts.energy(gen.kind.peak[gen.tier()])));
                    tips.desc("energy.pioneers.tip." + gen.kind.key);
                }
            }
            case "dynamo" -> {
                if (block instanceof DynamoBlock dynamo) {
                    DynamoTile.specs(dynamo.tier, tips);
                }
            }
            case "buffer" -> {
                if (block instanceof BufferBlock buffer) {
                    buffer(tips, buffer, stack);
                }
            }
            case "air_compressor" -> {
                tips.spec("block.pioneers.air_compressor.rate", String.format(Locale.ROOT, "%.0f", AirCompressorTile.RATE), AirCompressorTile.ENERGY_PER_TICK);
                tips.spec("cable.pioneers.pressure.max", String.format(Locale.ROOT, "%.0f", AirCompressorTile.MAX_PRESSURE));
            }
            case "electric_heater" -> tips.spec("block.pioneers.electric_heater.rate", ElectricHeaterTile.ENERGY_PER_TICK,
                  String.format(Locale.ROOT, "%.0f", ElectricHeaterTile.MAX_TEMPERATURE));
            case "controller_generator" -> {
                if (level != null && block instanceof ControllerBlock controller) {
                    long best = GeneratorOutput.bestPerTick(level, controller.machine, 0, true);
                    if (best > 0) {
                        tips.energy("tooltip.pioneers.stat.power", Component.translatable("energy.pioneers.fe.per_op_max", Texts.grouped(best)));
                    }
                }
            }
            default -> {
            }
        }
    }

    private static void buffer(Tooltips tips, BufferBlock buffer, ItemStack stack) {
        boolean creative = buffer.isCreative();
        BufferType type = buffer.type;
        if (creative) {
            tips.note("tooltip.pioneers.creative_buffer");
            tips.note("tooltip.pioneers.creative_buffer.flow");
        }
        long capacity = type.capacity(buffer.tier, creative);
        switch (type) {
            case ITEM -> {
                tips.stat("tooltip.pioneers.stat.capacity", Component.translatable("buffer.pioneers.capacity.item", capacity));
                //1.12.3 (lot F3) : plusieurs piles normales par case
                int multiplier = type.stackMultiplier(buffer.tier, creative);
                tips.spec("buffer.pioneers.capacity.stack", Texts.format(multiplier), Texts.format(64L * multiplier));
            }
            case FLUID, GAS -> tips.spec("buffer.pioneers.capacity.fluid", Texts.format(capacity));
            case ENERGY -> tips.spec("buffer.pioneers.capacity.energy", Texts.format(capacity));
            case PRESSURE -> {
                tips.spec("buffer.pioneers.capacity.pressure", Texts.format(capacity));
                tips.spec("cable.pioneers.pressure.max", String.format(Locale.ROOT, "%.0f", type.maxPressure(buffer.tier)));
            }
            case HEAT -> {
                tips.spec("buffer.pioneers.capacity.heat", Texts.format(capacity));
                tips.spec("cable.pioneers.heat.conductance", creative ? "\u221E" : String.format(Locale.ROOT, "%.0f", type.heatConductance(buffer.tier)));
            }
        }
        CompoundTag data = stack.isEmpty() ? null : BlockItem.getBlockEntityData(stack);
        if (data != null && data.contains(BufferTile.NBT_CONTENTS)) {
            tips.state("buffer.pioneers.keeps");
        }
    }

    // ------------------------------------------------------------------ morceaux

    /** « 2 objets · 1 fluide · 2 gaz » : ressources d'un sens (l'energie n'est pas comptee : elle a sa ligne). */
    @Nullable
    private static Component summary(MachineSpec spec, Role role) {
        List<Component> parts = new ArrayList<>();
        //1.0.6 : jauges partagees (autant de liquides que de gaz) : « 2 liquides ou gaz »
        int nf = spec.count(SideType.FLUIDS, role), ng = spec.count(SideType.GASES, role);
        boolean mixed = nf > 0 && nf == ng;
        for (SideType res : new SideType[]{SideType.ITEMS, SideType.FLUIDS, SideType.GASES}) {
            int n = spec.count(res, role);
            if (mixed && res != SideType.ITEMS) {
                if (res == SideType.FLUIDS) {
                    parts.add(n == 1 ? Component.translatable(K + "res.mixed.one") : Component.translatable(K + "res.mixed.many", n));
                }
                continue;
            }
            if (n > 0) {
                String base = K + "res." + res.name().toLowerCase(Locale.ROOT);
                parts.add(n == 1 ? Component.translatable(base + ".one") : Component.translatable(base + ".many", n));
            }
        }
        for (SideType res : new SideType[]{SideType.HEAT, SideType.PRESSURE, SideType.DATA}) {
            if (spec.count(res, role) > 0) {
                parts.add(Component.translatable(K + "res." + res.name().toLowerCase(Locale.ROOT)));
            }
        }
        return parts.isEmpty() ? null : join(parts);
    }

    /** Ressources des faces reglables, dans l'ordre des ports. */
    @Nullable
    private static Component faces(MachineSpec spec) {
        Set<SideType> seen = new LinkedHashSet<>();
        for (MachineSpec.PortSpec p : spec.ports()) {
            seen.add(p.resource());
        }
        List<Component> parts = new ArrayList<>();
        for (SideType t : seen) {
            parts.add(Component.translatable(K + "side." + t.name().toLowerCase(Locale.ROOT)));
        }
        return parts.isEmpty() ? null : join(parts);
    }

    private static Component join(List<Component> parts) {
        MutableComponent out = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                out.append(" · ");
            }
            out.append(parts.get(i));
        }
        return out;
    }

    /** Nom de la fiche : celui de son premier bloc, sans le suffixe « MKI » d'une machine a tiers (la ligne dit « MKI a MKIV »). */
    private static Component name(MachineSpec spec) {
        List<Block> blocks = MachineRegistry.blocks(spec);
        Component name = blocks.isEmpty() ? Component.translatable("block.pioneers." + spec.blocks().get(0)) : blocks.get(0).getName();
        String s = name.getString();
        return spec.tiers() == TierMode.MK && s.endsWith(" MKI") ? Component.literal(s.substring(0, s.length() - 4)) : name;
    }

    private static String pct(double fraction) {
        return Math.round(fraction * 100) + " %";
    }
}
