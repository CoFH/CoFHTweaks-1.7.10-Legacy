package cofh.tweak.asm;

import cofh.tweak.asmhooks.Config;
import cpw.mods.fml.common.versioning.DefaultArtifactVersion;
import cpw.mods.fml.common.versioning.VersionParser;
import cpw.mods.fml.relauncher.FMLInjectionData;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

import java.awt.Desktop;
import java.io.File;
import java.util.Map;
import javax.swing.JEditorPane;
import javax.swing.JOptionPane;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

@IFMLLoadingPlugin.TransformerExclusions({ "cofh.tweak.asm." })
@IFMLLoadingPlugin.SortingIndex(1002)
public class LoadingPlugin implements IFMLLoadingPlugin {

	public static final String MC_VERSION = "[1.7.10]";
	public static boolean runtimeDeobfEnabled = false;

	public static final File minecraftHome;

	static {

		minecraftHome = (File) FMLInjectionData.data()[6];
		versionCheck(MC_VERSION, "CoFHTweaks");
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

		return "cofh.tweak.asm.CoFHAccessTransformer";
	}

	@Override
	public String[] getASMTransformerClass() {

		return new String[] { "cofh.tweak.asm.CoFHClassTransformer" };
	}

	@Override
	public String getModContainerClass() {

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
	}

	public File myLocation;

}
