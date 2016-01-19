package cofh.tweak.asmhooks;

import net.minecraft.entity.passive.EntitySquid;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;


public class EventHandler {

	//@SubscribeEvent
	public void onEntitySpawn(LivingSpawnEvent.CheckSpawn evt) {

		EntityPlayer player = evt.world.getClosestPlayerToEntity(evt.entity, -1.0D);
		if (evt.entity instanceof EntitySquid) {

		}
	}

}
