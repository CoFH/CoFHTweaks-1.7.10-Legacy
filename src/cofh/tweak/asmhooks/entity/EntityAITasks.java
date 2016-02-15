package cofh.tweak.asmhooks.entity;

import cofh.tweak.asmhooks.Config;
import cofh.tweak.util.IdentityArrayHashList;

import java.util.ArrayDeque;
import java.util.List;

import net.minecraft.profiler.Profiler;
import net.minecraft.server.MinecraftServer;

public class EntityAITasks extends net.minecraft.entity.ai.EntityAITasks {

	public EntityAITasks(Profiler p_i1628_1_) {

		super(p_i1628_1_);
		executingTaskEntries = new IdentityArrayHashList<EntityAITaskEntry>();
		if (Config.agressiveAICulling)
			tickRate += 10;
	}

	ArrayDeque<EntityAITaskEntry> taskEntriesToStart = new ArrayDeque<EntityAITaskEntry>();

	@Override
	public void onUpdateTasks() {

		if (theProfiler.profilingEnabled & Config.allowProfilingAI) {
			onUpdateTasksDebug();
			return;
		}
		List<EntityAITaskEntry> executingTaskEntries = this.executingTaskEntries;
		EntityAITaskEntry entityaitaskentry;

		if (this.tickCount++ % this.tickRate == 0) {

			for (int i = 0, e = this.taskEntries.size(); i < e; ++i) {
				entityaitaskentry = (EntityAITaskEntry) this.taskEntries.get(i);

				boolean flag = executingTaskEntries.contains(entityaitaskentry);

				if (flag) {
					if (this.canContinue(entityaitaskentry) && this.canUse(entityaitaskentry)) {
						continue;
					}

					entityaitaskentry.action.resetTask();
					if (!entityaitaskentry.action.shouldExecute())
						executingTaskEntries.remove(entityaitaskentry);
					else
						taskEntriesToStart.add(entityaitaskentry);
				} else {
					l: if (this.canUse(entityaitaskentry)) {
						if (!entityaitaskentry.action.shouldExecute()) {
							break l;
						}
						taskEntriesToStart.add(entityaitaskentry);
						executingTaskEntries.add(entityaitaskentry);
					}
				}
			}
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

			for (int i = 0, e = executingTaskEntries.size(); i < e; ++i) {
				entityaitaskentry = executingTaskEntries.get(i);

				if (!entityaitaskentry.action.continueExecuting()) {
					entityaitaskentry.action.resetTask();
					executingTaskEntries.remove(i);
					--i;
					--e;
				}
			}
		}

		for (int i = 0, e = taskEntriesToStart.size(); i < e; ++i) {
			entityaitaskentry = taskEntriesToStart.poll();
			entityaitaskentry.action.startExecuting();
		}

		for (int i = 0, e = executingTaskEntries.size(); i < e; ++i) {
			entityaitaskentry = executingTaskEntries.get(i);
			entityaitaskentry.action.updateTask();
		}
	}

	public void onUpdateTasksDebug() {

		Profiler theProfiler = this.theProfiler;
		List<EntityAITaskEntry> executingTaskEntries = this.executingTaskEntries;
		theProfiler.startSection("processing");
		EntityAITaskEntry entityaitaskentry;

		if (this.tickCount++ % this.tickRate == 0) {
			theProfiler.startSection("full");

			for (int i = 0, e = this.taskEntries.size(); i < e; ++i) {
				entityaitaskentry = (EntityAITaskEntry) this.taskEntries.get(i);
				String name = entityaitaskentry.action.getClass().getSimpleName();
				theProfiler.startSection(name);

				boolean flag = executingTaskEntries.contains(entityaitaskentry);

				if (flag) {
					if (this.canContinue(entityaitaskentry) && this.canUse(entityaitaskentry)) {
						theProfiler.endSection();
						continue;
					}

					theProfiler.startSection("reset");
					entityaitaskentry.action.resetTask();
					theProfiler.endStartSection("should_execute");
					if (!entityaitaskentry.action.shouldExecute())
						executingTaskEntries.remove(entityaitaskentry);
					else
						taskEntriesToStart.add(entityaitaskentry);
					theProfiler.endSection();
				} else {
					l: if (this.canUse(entityaitaskentry)) {
						theProfiler.startSection("should_execute");
						if (!entityaitaskentry.action.shouldExecute()) {
							theProfiler.endSection();
							break l;
						}
						theProfiler.endSection();
						taskEntriesToStart.add(entityaitaskentry);
						executingTaskEntries.add(entityaitaskentry);
					}
				}
				theProfiler.endSection();
			}
			theProfiler.endSection();
		}
		else
		{
			if (Config.agressiveAICulling && MinecraftServer.getServer() != null) {
				long[] data = MinecraftServer.getServer().tickTimeArray;
				int t = (MinecraftServer.getServer().getTickCounter() % 100) - 1;
				if (t < 0) t = data.length - 1;
				long p = data[t];
				if (p > 40000000L) {
					theProfiler.endSection();
					return;
				}
			}
			theProfiler.startSection("tick");

			for (int i = 0, e = executingTaskEntries.size(); i < e; ++i) {
				entityaitaskentry = executingTaskEntries.get(i);
				String name = entityaitaskentry.action.getClass().getSimpleName();
				theProfiler.startSection(name);

				if (!entityaitaskentry.action.continueExecuting()) {
					entityaitaskentry.action.resetTask();
					executingTaskEntries.remove(i);
					--i;
					--e;
				}
				theProfiler.endSection();
			}
			theProfiler.endSection();
		}
		theProfiler.endSection();

		theProfiler.startSection("goalStart");

		for (int i = 0, e = taskEntriesToStart.size(); i < e; ++i) {
			entityaitaskentry = taskEntriesToStart.poll();
			String name = entityaitaskentry.action.getClass().getSimpleName();
			theProfiler.startSection(name);
			entityaitaskentry.action.startExecuting();
			theProfiler.endSection();
		}

		theProfiler.endSection();
		theProfiler.startSection("goalTick");

		for (int i = 0, e = executingTaskEntries.size(); i < e; ++i) {
			entityaitaskentry = executingTaskEntries.get(i);
			entityaitaskentry.action.updateTask();
		}

		theProfiler.endSection();
	}

	@Override
	protected boolean canUse(EntityAITaskEntry task) {

		this.theProfiler.startSection("canUse");

		for (int i = 0, e = this.taskEntries.size(); i < e; ++i) {
			EntityAITaskEntry entityaitaskentry = (EntityAITaskEntry) this.taskEntries.get(i);

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
