package nallar.tickprofiler;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@SuppressWarnings({"UnusedDeclaration", "UseOfSystemOutOrSystemErr"})
public class Log {
	public static final Logger LOGGER = LogManager.getLogger("TickProfiler");

	public static void error(String msg) {
		LOGGER.error(msg);
	}

	public static void warn(String msg) {
		LOGGER.warn(msg);
	}

	public static void info(String msg) {
		LOGGER.info(msg);
	}

	public static void trace(String msg) {
		LOGGER.trace(msg);
	}

	public static void error(String msg, Throwable t) {
		LOGGER.log(Level.ERROR, msg, t);
	}

	public static void warn(String msg, Throwable t) {
		LOGGER.log(Level.WARN, msg, t);
	}

	public static void info(String msg, Throwable t) {
		LOGGER.log(Level.INFO, msg, t);
	}

	public static void trace(String msg, Throwable t) {
		LOGGER.log(Level.TRACE, msg, t);
	}

	public static String name(World world) {
		if (world.provider == null) {
			return "Broken world with null world.provider";
		}
		return world.provider.getDimensionName() + '/' + world.provider.dimensionId;
	}

	public static String classString(Object o) {
		return "c " + o.getClass().getName() + ' ';
	}

	public static void log(Level level, Throwable throwable, String s) {
		LOGGER.log(level, s, throwable);
	}

	public static String toString(Object o) {
		if (o instanceof World) {
			return name((World) o);
		}
		String cS = classString(o);
		String s = o.toString();
		if (!s.startsWith(cS)) {
			s = cS + s;
		}
		if (o instanceof TileEntity) {
			TileEntity tE = (TileEntity) o;
			if (!s.contains(" x, y, z: ")) {
				s += " x, y, z: " + tE.xCoord + ", " + tE.yCoord + ", " + tE.zCoord;
			}
		}
		return s;
	}
}
