package fr.pioneers.mixin;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.RegistryManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.0.6 (17/11/2026, formes d'extrusion de la plaque, du fil et de l'ebauche absentes du monde de Guillaume) : un objet du mod retire puis
 * REMIS ne revenait pas dans un monde existant. Quand un objet manque, notre remap ({@code PiRemaps}, evenement des correspondances
 * manquantes) devient un ALIAS que Forge garde pour toujours dans le monde ({@code level.dat}, « aliases »). Au chargement suivant, Forge
 * n'ajoute un objet nouveau du jeu que si son nom n'est pas deja connu ({@code GameData.loadFrozenDataToStagingRegistry} :
 * {@code containsKey}, qui suit les alias) : l'alias perime cachait l'objet remis.
 * <p>On refuse donc un alias dont la source est un objet du mod bel et bien enregistre (registre fige) : il ne peut etre que perime. Les
 * alias des autres mods et ceux des objets vraiment retires ne changent pas. Code de Forge ({@code remap = false}) : pas de refmap.
 */
@Mixin(value = ForgeRegistry.class, remap = false)
public abstract class ForgeRegistryMixin {

    @Inject(method = "addAlias", at = @At("HEAD"), cancellable = true)
    private void pioneers$staleAlias(ResourceLocation src, ResourceLocation dst, CallbackInfo ci) {
        if (!"pioneers".equals(src.getNamespace())) {
            return;
        }
        ForgeRegistry<?> self = (ForgeRegistry<?>) (Object) this;
        ForgeRegistry<?> frozen = RegistryManager.FROZEN.getRegistry(self.getRegistryName());
        if (frozen != null && frozen != self && frozen.getKeys().contains(src)) {
            ci.cancel();
        }
    }
}
