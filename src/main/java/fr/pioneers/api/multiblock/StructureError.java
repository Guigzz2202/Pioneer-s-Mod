package fr.pioneers.api.multiblock;

import fr.pioneers.industry.IndustryMachine;
import fr.pioneers.industry.multi.NbtRoles;
import fr.pioneers.industry.multi.PartBlock;
import fr.pioneers.industry.multi.Signature;
import fr.pioneers.industry.multi.Structure;
import fr.pioneers.registries.PiIndustry;
import fr.pioneers.transport.CableTier;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * API Multibloc (lot 5) : ERREUR COMMUNE d'une structure non formee : QUOI (code {@link Structure.Error}), OU (case fautive relative au
 * controleur : a droite, en haut, en profondeur ; null si la faute n'a pas de case), le ROLE de la case et le bloc ATTENDU (cle de langue).
 * Calculee de la meme facon sur le serveur et chez le client (code, case du monde et detail sont deja synchronises par les valeurs
 * d'interface) ; ecran du controleur, onglet Structure, banc des multiblocs.
 *
 * @param code     raison du refus
 * @param at       case fautive dans le monde, ou null
 * @param rel      case fautive relative au controleur {droite, haut, profondeur}, ou null
 * @param role     role de la case ({@link Role#name()}), ou null
 * @param expected cle de langue du bloc attendu (ou de la consigne), ou null
 */
public record StructureError(Structure.Error code, @Nullable BlockPos at, @Nullable int[] rel, @Nullable String role, @Nullable String expected) {

    public static final StructureError NONE = new StructureError(Structure.Error.NONE, null, null, null, null);

    /** Erreur d'un resultat de validation (ou d'un code, d'une case et d'un detail synchronises). */
    public static StructureError of(Structure.Error code, @Nullable BlockPos at, int detail, BlockPos controller, Direction front, IndustryMachine machine) {
        if (code == Structure.Error.NONE) {
            return NONE;
        }
        int[] rel = at == null ? null : StructureMatcher.relative(controller, front, at);
        String role = null, expected = null;
        switch (code) {
            case BAD_SHELL, NO_CASING, SCORCHED, TOO_SMALL, TOO_BIG -> {
                role = Role.CASING.name();
                expected = id(() -> PiIndustry.CASINGS.get(CableTier.BASIC).getBlock().getDescriptionId());
            }
            case MISSING_SHEET -> {
                role = Role.SHEET.name();
                expected = id(() -> PiIndustry.SHEETS.get(CableTier.BASIC).getBlock().getDescriptionId());
            }
            case MISSING_FRAME -> {
                role = Role.FRAME.name();
                expected = id(() -> PiIndustry.FRAMES.get(CableTier.BASIC).getBlock().getDescriptionId());
            }
            case BAD_INTERIOR, WRONG_MODULE, NO_MODULE, INCOMPLETE, DRY_RISER -> {
                role = Role.MODULE.name();
                expected = machine.module == null ? "industry.pioneers.reactor.empty" : "block.pioneers." + machine.module.id();
            }
            case MISSING_PART -> {
                role = Role.PART.name();
                if (detail > 0) {
                    int d = detail - 1;
                    PartBlock.Kind part = PartBlock.Kind.values()[Math.min(d / 16, PartBlock.Kind.values().length - 1)];
                    expected = "block.pioneers." + part.id();
                }
            }
            case MISSING_REACTOR -> {
                role = Role.REACTOR.name();
                expected = Structure.reactorKey(detail);
            }
            case MISSING_BLOCK -> {
                //1.0.4-beta (lot 24) : role propre d'une structure de Guillaume (NBT)
                role = NbtRoles.roleName(detail);
                expected = NbtRoles.expectedKey(detail);
            }
            case PORT_NOT_HERE -> {
                //1.0.4-beta (lot 26) : un port ne remplace qu'une carcasse (jamais une tole, une charpente, un verre...)
                role = Role.PORT.name();
                expected = null;
            }
            case OTHER_CONTROLLER -> {
                role = Role.CONTROLLER.name();
                expected = "block.pioneers." + machine.multiId();
            }
            default -> {
            }
        }
        return new StructureError(code, at, rel, role, expected);
    }

    public static StructureError of(Structure.Result r, BlockPos controller, Direction front, IndustryMachine machine) {
        return of(r.error(), r.at(), r.detail(), controller, front, machine);
    }

    private static String id(java.util.function.Supplier<String> s) {
        try {
            return s.get();
        } catch (Throwable t) {
            return null;
        }
    }

    public boolean formed() {
        return code == Structure.Error.NONE;
    }

    /** QUOI : texte de l'erreur (pieces signature : laquelle et dans quelle zone ; reacteur : le bloc attendu). */
    public Component what(int detail) {
        if (detail > 0 && code == Structure.Error.MISSING_PART) {
            int d = detail - 1;
            PartBlock.Kind part = PartBlock.Kind.values()[Math.min(d / 16, PartBlock.Kind.values().length - 1)];
            Signature.Zone zone = Signature.Zone.values()[Math.min(d % 16, Signature.Zone.values().length - 1)];
            return Component.translatable("industry.pioneers.error.missing_part.detail", Component.translatable("block.pioneers." + part.id()),
                  Component.translatable("industry.pioneers.zone." + zone.key()));
        }
        if (detail > 0 && code == Structure.Error.MISSING_REACTOR) {
            return Component.translatable("industry.pioneers.error.missing_reactor.detail", Component.translatable(Structure.reactorKey(detail)));
        }
        if (detail > 0 && code == Structure.Error.MISSING_BLOCK) {
            return Component.translatable("industry.pioneers.error.missing_block.detail", Component.translatable(NbtRoles.expectedKey(detail)));
        }
        return Component.translatable("industry.pioneers.error." + code.name().toLowerCase(Locale.ROOT));
    }

    /** OU : « 2 à droite, 1 en haut, 3 en profondeur » (ou « au contrôleur »), null sans case. */
    @Nullable
    public Component where() {
        return where(rel);
    }

    /** Lot 6 : OU d'une case relative {droite, haut, profondeur} (multiblocs maison : meme texte que les industriels), null sans case. */
    @Nullable
    public static Component where(@Nullable int[] rel) {
        if (rel == null) {
            return null;
        }
        if (rel[0] == 0 && rel[1] == 0 && rel[2] == 0) {
            return Component.translatable("gui.pioneers.multiblock.at_controller");
        }
        Component out = null;
        String[] keys = {rel[0] >= 0 ? "right" : "left", rel[1] >= 0 ? "up" : "down", "deep"};
        for (int i = 0; i < 3; i++) {
            if (rel[i] == 0) {
                continue;
            }
            Component part = Component.translatable("gui.pioneers.multiblock.where." + keys[i], Math.abs(rel[i]));
            out = out == null ? part : Component.translatable("gui.pioneers.multiblock.where.and", out, part);
        }
        return out;
    }

    /** ATTENDU : le bloc (ou la consigne), null si inconnu. */
    @Nullable
    public Component expectedText() {
        return expected == null ? null : Component.translatable(expected);
    }
}
