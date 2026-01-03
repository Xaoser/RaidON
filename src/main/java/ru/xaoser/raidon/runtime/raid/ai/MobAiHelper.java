package ru.xaoser.raidon.runtime.raid.ai;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import ru.xaoser.raidon.api.sup.SpawnBehavior;

public final class MobAiHelper {
    private MobAiHelper() {}

    public static void applyBehavior(Mob mob, SpawnBehavior behavior) {
        if (!(mob instanceof PathfinderMob pathfinder)) {
            // Не все Mob поддерживают pathfinding (например, некоторые особые сущности).
            // В этом случае просто не трогаем AI или делаем минимальный набор.
            return;
        }

        if (behavior == SpawnBehavior.HOSTILE) {
            setupHostile(pathfinder);
        } else {
            setupNeutral(pathfinder);
        }
    }

    private static void setupHostile(PathfinderMob mob) {
        mob.goalSelector.getAvailableGoals().clear();
        mob.targetSelector.getAvailableGoals().clear();

        mob.goalSelector.addGoal(0, new FloatGoal(mob));
        mob.goalSelector.addGoal(1, new MeleeAttackGoal(mob, 1.2D, false));
        mob.goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(mob, 1.0D));
        mob.goalSelector.addGoal(3, new LookAtPlayerGoal(mob, Player.class, 16.0F));
        mob.goalSelector.addGoal(4, new RandomLookAroundGoal(mob));

        mob.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(mob, Player.class, true));
    }

    private static void setupNeutral(PathfinderMob mob) {
        // Если вы хотите ограничить нейтральное поведение только “животным” — ок.
        // Но лучше проверять по интерфейсу/классу, а не по конкретным типам.
        if (mob.getType() == EntityType.COW || mob.getType() == EntityType.SHEEP || mob.getType() == EntityType.PIG) {
            mob.goalSelector.getAvailableGoals().clear();
            mob.targetSelector.getAvailableGoals().clear();

            mob.goalSelector.addGoal(0, new FloatGoal(mob));
            mob.goalSelector.addGoal(1, new WaterAvoidingRandomStrollGoal(mob, 1.0D));
            mob.goalSelector.addGoal(2, new LookAtPlayerGoal(mob, Player.class, 8.0F));
            mob.goalSelector.addGoal(3, new RandomLookAroundGoal(mob));
        }
    }
}