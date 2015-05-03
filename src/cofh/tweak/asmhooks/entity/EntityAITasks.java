package cofh.tweak.asmhooks.entity;

import cofh.tweak.IdentityArrayHashList;
import cofh.tweak.asmhooks.Config;

import java.util.ArrayList;

import net.minecraft.profiler.Profiler;
import net.minecraft.server.MinecraftServer;

public class EntityAITasks extends net.minecraft.entity.ai.EntityAITasks {

	public EntityAITasks(Profiler p_i1628_1_) {

		super(p_i1628_1_);
		executingTaskEntries = new IdentityArrayHashList<EntityAITaskEntry>();
		if (Config.agressiveAICulling)
			tickRate = 10;
	}

    ArrayList<EntityAITaskEntry> arraylist = new ArrayList<EntityAITaskEntry>();

    @Override
	public void onUpdateTasks() {

        this.theProfiler.startSection("processing");
        EntityAITaskEntry entityaitaskentry;

        if (this.tickCount++ % this.tickRate == 0) {
            this.theProfiler.startSection("full");

            for (int i = 0, e = this.taskEntries.size(); i < e; ++i) {
                entityaitaskentry = (EntityAITaskEntry) this.taskEntries.get(i);
                String name = this.theProfiler.profilingEnabled ? entityaitaskentry.action.getClass().getSimpleName() : "$";
                this.theProfiler.startSection(name);

                boolean flag = this.executingTaskEntries.contains(entityaitaskentry);

                if (flag) {
                    if (this.canContinue(entityaitaskentry) && this.canUse(entityaitaskentry)) {
                        this.theProfiler.endSection();
                        continue;
                    }

                    entityaitaskentry.action.resetTask();
                    if (!entityaitaskentry.action.shouldExecute())
                    	this.executingTaskEntries.remove(entityaitaskentry);
                    else
                    	arraylist.add(entityaitaskentry);
                } else {
	                if (entityaitaskentry.action.shouldExecute() && this.canUse(entityaitaskentry)) {
	                    arraylist.add(entityaitaskentry);
	                    this.executingTaskEntries.add(entityaitaskentry);
	                }
                }
                this.theProfiler.endSection();
            }
            this.theProfiler.endSection();
        }
        else
        {
        	if (Config.agressiveAICulling && MinecraftServer.getServer() != null) {
        		long[] data = MinecraftServer.getServer().tickTimeArray;
        		int t = (MinecraftServer.getServer().getTickCounter() % 100) - 1;
        		if (t < 0) t = data.length - 1;
        		long p = data[t];
        		if (p > 40000000L) return;
        	}
            this.theProfiler.startSection("tick");

            for (int i = 0, e = this.executingTaskEntries.size(); i < e; ++i) {
                entityaitaskentry = (EntityAITaskEntry) this.executingTaskEntries.get(i);

                if (!entityaitaskentry.action.continueExecuting()) {
                    entityaitaskentry.action.resetTask();
                    executingTaskEntries.remove(i);
                    --i;
                    --e;
                }
            }
            this.theProfiler.endSection();
        }
        this.theProfiler.endSection();

        this.theProfiler.startSection("goalStart");

        for (int i = 0, e = arraylist.size(); i < e; ++i) {
            entityaitaskentry = arraylist.get(i);
            String name = this.theProfiler.profilingEnabled ? entityaitaskentry.action.getClass().getSimpleName() : "$";
            this.theProfiler.startSection(name);
            entityaitaskentry.action.startExecuting();
            this.theProfiler.endSection();
        }
        arraylist.clear();

        this.theProfiler.endSection();
        this.theProfiler.startSection("goalTick");

        for (int i = 0, e = this.executingTaskEntries.size(); i < e; ++i) {
            entityaitaskentry = (EntityAITaskEntry) this.executingTaskEntries.get(i);
            entityaitaskentry.action.updateTask();
        }

        this.theProfiler.endSection();
    }

    @Override
	protected boolean canUse(EntityAITaskEntry task) {

        this.theProfiler.startSection("canUse");

        for (int i = 0, e = this.taskEntries.size(); i < e; ++i) {
            EntityAITaskEntry entityaitaskentry = (EntityAITaskEntry)this.taskEntries.get(i);

            if (entityaitaskentry != task) {
                if (task.priority >= entityaitaskentry.priority) {
                    if (!this.areTasksCompatible(task, entityaitaskentry) && this.executingTaskEntries.contains(entityaitaskentry)) {
                        this.theProfiler.endSection();
                        return false;
                    }
                }
                else if (!entityaitaskentry.action.isInterruptible() && this.executingTaskEntries.contains(entityaitaskentry)) {
                    this.theProfiler.endSection();
                    return false;
                }
            }
        }

        this.theProfiler.endSection();
        return true;
    }

}
