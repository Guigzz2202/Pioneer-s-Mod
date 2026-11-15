package fr.pioneers.mixin;

import fr.pioneers.world.fauna.FaunaAi;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 1.0.5-beta (lot 4) : IA allegee de la faune loin des joueurs ({@link FaunaAi}). {@code Mob#serverAiStep} range dans sa seule variable
 * entiere le compteur « temps du serveur + identifiant » et choisit les buts quand il est pair (sinon il fait seulement tourner les buts en
 * cours). On ne touche qu'a cette valeur, juste apres son calcul : loin de tout joueur, une bete de la faune ne la garde paire qu'un tick
 * pair sur quatre. {@code @ModifyVariable} ne remplace rien et se cumule avec les mixins des autres mods.
 */
@Mixin(Mob.class)
public abstract class MobMixin {

    @ModifyVariable(method = "serverAiStep", at = @At("STORE"), ordinal = 0)
    private int pioneers$lightAi(int counter) {
        return FaunaAi.gate((Mob) (Object) this, counter);
    }
}
