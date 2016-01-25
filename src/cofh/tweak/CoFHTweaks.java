package cofh.tweak;

import cofh.tweak.asm.LoadingPlugin.CoFHDummyContainer;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.LoaderState;


public class CoFHTweaks {

	public static final String version = "1.7.10R1.1.0";

	public static boolean canHaveWorld() {

		return CoFHDummyContainer.onServer || Loader.instance().hasReachedState(LoaderState.SERVER_ABOUT_TO_START);
	}

}
