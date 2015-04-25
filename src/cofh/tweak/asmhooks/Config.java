package cofh.tweak.asmhooks;

import com.google.common.base.Throwables;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

import org.apache.logging.log4j.core.helpers.Loader;

public class Config {

	static Properties config;

	public static void loadConfig(File file) {

		try {
			new File(file, "config/cofh/tweak").mkdirs();
			file = new File(file, "config/cofh/tweak/common.prop");
			if (file.createNewFile()) {
				copyFileUsingStream("assets/cofh/tweak/default.prop", file);
			}
		} catch (IOException e) {
			Throwables.propagate(e);
		}

		config = new Properties();
		try {
			config.load(new FileReader(file));
		} catch (Throwable e) {
			Throwables.propagate(e);
		}

		stackItems = Boolean.parseBoolean(config.getProperty("StackItems", "true"));
		collideEntities = Boolean.parseBoolean(config.getProperty("EntityCollisions", "true"));
		animateTextures = Boolean.parseBoolean(config.getProperty("AnimatedTextures", "true"));
		lightChunks = Boolean.parseBoolean(config.getProperty("ChunkLighting", "true"));
		agressiveCulling = Boolean.parseBoolean(config.getProperty("AgressiveCulling", "false"));
		distantCulling = Boolean.parseBoolean(config.getProperty("DistantCulling", "false"));
		fastBlocks = Boolean.parseBoolean(config.getProperty("FastClientSetBlock", "false"));
	}

	public static boolean stackItems;
	public static boolean collideEntities;
	public static boolean animateTextures;
	public static boolean fastBlocks;
	public static boolean lightChunks;
	public static boolean agressiveCulling;
	public static boolean distantCulling;

	@SuppressWarnings("resource")
	public static void copyFileUsingStream(String source, File dest) throws IOException {

		InputStream is = null;
		OutputStream os = null;
		try {
			is = Loader.getResource(source, null).openStream();
			os = new FileOutputStream(dest);

			byte[] buffer = new byte[1024];
			int length;
			while ((length = is.read(buffer)) > 0) {
				os.write(buffer, 0, length);
			}
		} finally {
			if (is != null) {
				is.close();
			}
			if (os != null) {
				os.close();
			}
		}
	}

}
