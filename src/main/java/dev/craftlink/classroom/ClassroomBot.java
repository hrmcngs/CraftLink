package dev.craftlink.classroom;

import dev.craftlink.input.RemoteState;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.control.JumpControl;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;

/** Server-side controllable NPC, synchronized with the vanilla villager renderer. */
public final class ClassroomBot extends Villager {
    private static final ChatFormatting[] COLORS = {ChatFormatting.RED,ChatFormatting.BLUE,ChatFormatting.GREEN,ChatFormatting.YELLOW,ChatFormatting.LIGHT_PURPLE,ChatFormatting.AQUA,ChatFormatting.GOLD,ChatFormatting.WHITE};
    private static final String[] NAMES = {"あか","あお","みどり","きいろ","むらさき","みずいろ","オレンジ","しろ"};
    private static final VillagerProfession[] JOBS = {VillagerProfession.FARMER,VillagerProfession.FISHERMAN,VillagerProfession.LIBRARIAN,VillagerProfession.CLERIC,VillagerProfession.ARMORER,VillagerProfession.TOOLSMITH,VillagerProfession.WEAPONSMITH,VillagerProfession.NONE};
    private Set<String> held = Set.of();
    private boolean jumpPulse;
    public ClassroomBot(ServerLevel level, int number) {
        super(EntityType.VILLAGER, level);
        goalSelector.removeAllGoals(goal -> true); targetSelector.removeAllGoals(goal -> true);
        setInvulnerable(true); setCanPickUpLoot(false);
        setCustomName(Component.literal(number + "号 " + NAMES[number-1]).withStyle(COLORS[number-1])); setCustomNameVisible(true);
        setVillagerData(getVillagerData().setProfession(JOBS[number-1]));
        moveControl = new MoveControl(this) {
            @Override public void tick() {
                float x = (held.contains("left") ? 1 : 0) - (held.contains("right") ? 1 : 0);
                float z = (held.contains("forward") ? 1 : 0) - (held.contains("back") ? 1 : 0);
                float length = Math.max(1, (float)Math.sqrt(x*x+z*z));
                setSpeed(held.contains("sneak") ? .055f : held.contains("sprint") ? .20f : .12f);
                setXxa(x/length); setZza(z/length);
            }
        };
        jumpControl = new JumpControl(this) {
            @Override public void tick() { setJumping(held.contains("jump") || jumpPulse); jumpPulse = false; }
        };
    }
    public void accept(RemoteState.Snapshot input) {
        held = input.held(); jumpPulse = input.presses().contains("jump");
        setYRot(getYRot() + (float)input.yaw()); setYHeadRot(getYRot());
        if (held.isEmpty()) setDeltaMovement(0, getDeltaMovement().y, 0);
        if (input.presses().contains("use")) ((ServerLevel)level()).sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY()+2, getZ(), 8,.35,.25,.35,0);
    }
    @Override protected void customServerAiStep() { /* Remote intent replaces villager brain behavior. */ }
    @Override public InteractionResult mobInteract(Player player, InteractionHand hand) { return InteractionResult.PASS; }
    @Override public boolean canBeLeashed(Player player) { return false; }
    @Override public boolean shouldBeSaved() { return false; }
    @Override protected void checkFallDamage(double distance, boolean grounded, net.minecraft.world.level.block.state.BlockState state, net.minecraft.core.BlockPos pos) {
        fallDistance = 0; // No farmland trampling or fall-triggered block changes from demo NPCs.
    }
}
