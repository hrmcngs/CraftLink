package dev.craftlink.classroom;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** A distinct entity type ensures the client also receives a humanoid rather than a villager. */
public final class ClassroomEntities {
    private static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, "craftlink");
    public static final RegistryObject<EntityType<ClassroomBot>> BOT = TYPES.register("classroom_bot", () ->
        EntityType.Builder.<ClassroomBot>of(ClassroomBot::new, MobCategory.MISC).sized(.6f, 1.8f)
            .clientTrackingRange(10).updateInterval(1).build("craftlink:classroom_bot"));
    public static void register(IEventBus bus) {
        TYPES.register(bus);
        bus.addListener(ClassroomEntities::attributes);
    }
    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(BOT.get(), ClassroomBot.createMobAttributes().build());
    }
    private ClassroomEntities() {}
}
