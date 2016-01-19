package cofh.tweak.asm;

import cofh.tweak.CoFHTweaks;
import cofh.tweak.asmhooks.Config;
import cofh.tweak.asmhooks.EventHandler;
import cofh.tweak.asmhooks.render.RenderGlobal;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import cpw.mods.fml.common.DummyModContainer;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.LoadController;
import cpw.mods.fml.common.ModMetadata;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;
import cpw.mods.fml.common.gameevent.TickEvent.RenderTickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.ServerTickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent.ClientConnectedToServerEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent.ClientDisconnectionFromServerEvent;
import cpw.mods.fml.common.versioning.DefaultArtifactVersion;
import cpw.mods.fml.common.versioning.VersionParser;
import cpw.mods.fml.relauncher.FMLInjectionData;
import cpw.mods.fml.relauncher.FMLLaunchHandler;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import cpw.mods.fml.relauncher.Side;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Map;
import javax.swing.JEditorPane;
import javax.swing.JOptionPane;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.profiler.Profiler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.opengl.GL11;

@IFMLLoadingPlugin.TransformerExclusions({ "cofh.tweak.asm." })
@IFMLLoadingPlugin.SortingIndex(1002)
public class LoadingPlugin implements IFMLLoadingPlugin {

	public static final String MC_VERSION = "[1.7.10]";
	public static boolean runtimeDeobfEnabled = false;
	public static final boolean obfuscated;

	public static final File minecraftHome;

	static {

		minecraftHome = (File) FMLInjectionData.data()[6];
		versionCheck(MC_VERSION, "CoFHTweaks");
		boolean obf = true;
		try {
			obf = Launch.classLoader.getClassBytes("net.minecraft.world.World") == null;
		} catch (IOException e) {
		}
		obfuscated = obf;
		Config.loadConfig(minecraftHome);
	}

	public static void versionCheck(String reqVersion, String mod) {

		String mcVersion = (String) FMLInjectionData.data()[4];
		if (!VersionParser.parseRange(reqVersion).containsVersion(new DefaultArtifactVersion(mcVersion))) {
			String err = "This version of " + mod + " does not support Minecraft version " + mcVersion;
			System.err.println(err);

			JEditorPane ep = new JEditorPane("text/html", "<html>" + err
					+ "<br>Remove it from your coremods or mods folder and check <a href=\"http://teamcofh.com/\">here</a> for updates" + "</html>");

			ep.setEditable(false);
			ep.setOpaque(false);
			ep.addHyperlinkListener(new HyperlinkListener() {

				@Override
				public void hyperlinkUpdate(HyperlinkEvent event) {

					try {
						if (event.getEventType().equals(HyperlinkEvent.EventType.ACTIVATED)) {
							Desktop.getDesktop().browse(event.getURL().toURI());
						}
					} catch (Exception e) {
						// pokemon!
					}
				}
			});
			JOptionPane.showMessageDialog(null, ep, "Fatal error", JOptionPane.ERROR_MESSAGE);
			System.exit(1);
		}
	}

	@Override
	public String getAccessTransformerClass() {

		return null;
	}

	@Override
	public String[] getASMTransformerClass() {

		return new String[] { "cofh.tweak.asm.CoFHClassTransformer" };
	}

	@Override
	public String getModContainerClass() {

		if (FMLLaunchHandler.side() == Side.CLIENT)
			return CoFHDummyContainer.class.getName();
		return null;
	}

	@Override
	public String getSetupClass() {

		return null;
	}

	@Override
	public void injectData(Map<String, Object> data) {

		runtimeDeobfEnabled = (Boolean) data.get("runtimeDeobfuscationEnabled");
		if (data.containsKey("coremodLocation")) {
			myLocation = (File) data.get("coremodLocation");
		}
		EventHandler handler = new EventHandler();
		FMLCommonHandler.instance().bus().register(handler);
		MinecraftForge.EVENT_BUS.register(handler);
	}

	private static File myLocation;

	public final static class CoFHDummyContainer extends DummyModContainer {

		public static boolean onServer;

		public CoFHDummyContainer() {

			super(new ModMetadata());
			ModMetadata md = getMetadata();
			md.autogenerated = true;
			md.modId = "<CoFH Tweak>";
			md.name = md.description = "CoFH Tweak ASM";
			md.version = CoFHTweaks.version;
		}

		@Override
		public File getSource() {

			return myLocation;
		}

		@Override
		public boolean registerBus(EventBus bus, LoadController controller) {

			bus.register(this);
			return true;
		}

		@Subscribe
		@SuppressWarnings("rawtypes")
		public void init(FMLInitializationEvent evt) {

			Minecraft.getMinecraft().renderGlobal = new RenderGlobal(Minecraft.getMinecraft());
			FMLCommonHandler.instance().bus().register(this);
			MinecraftForge.EVENT_BUS.register(this);
		}

		@SubscribeEvent
		public void onRenderHud(RenderGameOverlayEvent.Text evt) {

			if (evt.left.size() > 1 && evt.right.size() > 1) {
				String t = evt.left.get(1);
				String t2 = evt.right.get(1);

				if (t != null && t2 != null) {
					int width = evt.resolution.getScaledWidth();
					FontRenderer fr = Minecraft.getMinecraft().fontRenderer;

					if (width - fr.getStringWidth(t) < (fr.getStringWidth(t2) + fr.getStringWidth("QQQ"))) {
						evt.right.add(1, null);
					}
				}
			} else if (Config.showFPS) {
				evt.left.add(0, Minecraft.getMinecraft().debug);
			}
		}

		// { PROFILING

		@Subscribe
		public void serverStarting(FMLServerStartingEvent evt) {

			toggleProfiling = profiling = false;
			evt.registerServerCommand(new CommandBase() {

				@Override
				public String getCommandName() {

					return "tweaks_profile";
				}

				@Override
				public boolean canCommandSenderUseCommand(ICommandSender sender) {

					return true;
				}

				@Override
				public String getCommandUsage(ICommandSender p_71518_1_) {

					return '/' + getCommandName();
				}

				@Override
				public void processCommand(ICommandSender user, String[] args) {

					if (args.length == 0) {
						toggleProfiling = true;
					} else {
						int p_71383_1_ = parseInt(user, args[0]);
						List<Profiler.Result> list = profilerResults;

						if (list != null && !list.isEmpty()) {
							Profiler.Result result = list.get(0);

							if (p_71383_1_ == 0) {
								if (result.field_76331_c.length() > 0) {
									int j = debugProfilerName.lastIndexOf(".");

									if (j >= 0) {
										debugProfilerName = debugProfilerName.substring(0, j);
									}
								}
							} else {

								if (p_71383_1_ < list.size() && !list.get(p_71383_1_).field_76331_c.equals("unspecified")) {
									if (debugProfilerName.length() > 0) {
										debugProfilerName = debugProfilerName + ".";
									}

									debugProfilerName = debugProfilerName + list.get(p_71383_1_).field_76331_c;
								}
							}
						}
					}
				}

			});
		}

		protected String debugProfilerName = "root";
		protected List<Profiler.Result> profilerResults = null;
		protected volatile boolean toggleProfiling = false, profiling = false;

		@SubscribeEvent
		public void onServerTick(ServerTickEvent evt) {

			if (evt.phase == Phase.START & toggleProfiling) {
				MinecraftServer.getServer().theProfiler.clearProfiling();
				profiling = MinecraftServer.getServer().theProfiler.profilingEnabled ^= true;
				toggleProfiling = false;
				profilerResults = null;
			} else if (evt.phase == Phase.END & profiling) {
				profilerResults = MinecraftServer.getServer().theProfiler.getProfilingData(debugProfilerName);
			}
		}

		@SubscribeEvent
		public void onRenderTick(RenderTickEvent evt) {

			GameSettings gameSettings = Minecraft.getMinecraft().gameSettings;
			if (!gameSettings.showDebugProfilerChart) {
				List<Profiler.Result> list = profilerResults;
				if (profiling & list != null && list.size() > 0) {
			        GL11.glFlush();

			        FontRenderer fontRenderer = Minecraft.getMinecraft().fontRenderer;
			        int displayWidth = Minecraft.getMinecraft().displayWidth;
			        int displayHeight = Minecraft.getMinecraft().displayHeight;
			        Profiler.Result result = list.get(0);
		            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
		            GL11.glMatrixMode(GL11.GL_PROJECTION);
		            GL11.glEnable(GL11.GL_COLOR_MATERIAL);
		            GL11.glLoadIdentity();
		            GL11.glOrtho(0.0D, displayWidth, displayHeight, 0.0D, 1000.0D, 3000.0D);
		            GL11.glMatrixMode(GL11.GL_MODELVIEW);
		            GL11.glLoadIdentity();
		            GL11.glTranslatef(0.0F, 0.0F, -2000.0F);
		            GL11.glLineWidth(1.0F);
		            GL11.glDisable(GL11.GL_TEXTURE_2D);
		            Tessellator tessellator = Tessellator.instance;
		            short short1 = 160;
		            int j = displayWidth - short1 - 10;
		            int k = displayHeight - short1 * 2;
		            GL11.glEnable(GL11.GL_BLEND);
		            tessellator.startDrawingQuads();
		            tessellator.setColorRGBA_I(0, 200);
		            tessellator.addVertex(j - short1 * 1.1F, k - short1 * 0.6F - 16.0F, 0.0D);
		            tessellator.addVertex(j - short1 * 1.1F, k + short1 * 2, 0.0D);
		            tessellator.addVertex(j + short1 * 1.1F, k + short1 * 2, 0.0D);
		            tessellator.addVertex(j + short1 * 1.1F, k - short1 * 0.6F - 16.0F, 0.0D);
		            tessellator.draw();
		            GL11.glDisable(GL11.GL_BLEND);
		            double d0 = 0.0D;
		            int i1;

		            for (int l = 1; l < list.size(); ++l) {
		                Profiler.Result result1 = list.get(l);
		                i1 = MathHelper.floor_double(result1.field_76332_a / 4.0D) + 1;
		                tessellator.startDrawing(6);
		                tessellator.setColorOpaque_I(result1.func_76329_a());
		                tessellator.addVertex(j, k, 0.0D);
		                int j1;
		                float f;
		                float f1;
		                float f2;

		                for (j1 = i1; j1 >= 0; --j1) {
		                    f = (float)((d0 + result1.field_76332_a * j1 / i1) * Math.PI * 2.0D / 100.0D);
		                    f1 = MathHelper.sin(f) * short1;
		                    f2 = MathHelper.cos(f) * short1 * 0.5F;
		                    tessellator.addVertex(j + f1, k - f2, 0.0D);
		                }

		                tessellator.draw();
		                tessellator.startDrawing(5);
		                tessellator.setColorOpaque_I((result1.func_76329_a() & 16711422) >> 1);

		                for (j1 = i1; j1 >= 0; --j1) {
		                    f = (float)((d0 + result1.field_76332_a * j1 / i1) * Math.PI * 2.0D / 100.0D);
		                    f1 = MathHelper.sin(f) * short1;
		                    f2 = MathHelper.cos(f) * short1 * 0.5F;
		                    tessellator.addVertex(j + f1, k - f2, 0.0D);
		                    tessellator.addVertex(j + f1, k - f2 + 10.0F, 0.0D);
		                }

		                tessellator.draw();
		                d0 += result1.field_76332_a;
		            }

		            DecimalFormat decimalformat = new DecimalFormat("##0.00");
		            GL11.glEnable(GL11.GL_TEXTURE_2D);
		            String s = "";

		            if (!result.field_76331_c.equals("unspecified")) {
		                s = s + "[0] ";
		            }

		            if (result.field_76331_c.length() == 0) {
		                s = s + "ROOT ";
		            } else {
		                s = s + result.field_76331_c + " ";
		            }

		            i1 = 16777215;
		            fontRenderer.drawStringWithShadow(s, j - short1, k - short1 / 2 - 16, i1);
		            fontRenderer.drawStringWithShadow(s = decimalformat.format(result.field_76330_b) + "%", j + short1 - fontRenderer.getStringWidth(s),
		            		k - short1 / 2 - 14 + fontRenderer.FONT_HEIGHT, i1);

		            for (int k1 = 1; k1 < list.size(); ++k1) {
		                Profiler.Result result2 = list.get(k1);
		                String s1 = "";

		                if (result2.field_76331_c.equals("unspecified")) {
		                    s1 = s1 + "[?] ";
		                } else {
		                    s1 = s1 + "[" + (k1) + "] ";
		                }

		                s1 = s1 + result2.field_76331_c;
		                fontRenderer.drawStringWithShadow(s1, j - short1, k + short1 / 2 + k1 * 8 + 20, result2.func_76329_a());
		                fontRenderer.drawStringWithShadow(s1 = decimalformat.format(result2.field_76332_a) + "%", j + short1 - 50 - fontRenderer.getStringWidth(s1), k + short1 / 2 + k1 * 8 + 20, result2.func_76329_a());
		                fontRenderer.drawStringWithShadow(s1 = decimalformat.format(result2.field_76330_b) + "%", j + short1 - fontRenderer.getStringWidth(s1), k + short1 / 2 + k1 * 8 + 20, result2.func_76329_a());
		            }
				}
			}
		}

		// }

		@SubscribeEvent
		public void connect(ClientConnectedToServerEvent evt) {

			onServer = true;
			profiling = false;
		}

		@SubscribeEvent
		public void disconnect(ClientDisconnectionFromServerEvent evt) {

			onServer = false;
			profiling = false;
		}

	}

}
