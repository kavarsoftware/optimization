package optimizer;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.*;

/** Configure Swing scaling before AWT starts. Only this application is affected. */
public final class DisplaySettings {
    private DisplaySettings() {}

    public static void configure(String requested) {
        String scale = requested != null ? requested : System.getenv("SERVICE_PLACEMENT_UI_SCALE");
        if (scale != null) {
            if (!scale.equals("1") && !scale.equals("2"))
                throw new IllegalArgumentException("UI scale must be 1 (100%) or 2 (200%)");
            System.setProperty("sun.java2d.uiScale", scale);
            return;
        }
        // Honor an explicit JVM setting and the native scaling behavior on Windows/macOS.
        if (System.getProperty("sun.java2d.uiScale") != null
                || !System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux")
                || System.getenv("DISPLAY") == null) return;
        try {
            Process process = new ProcessBuilder("xrandr", "--current").redirectErrorStream(true).start();
            if (!process.waitFor(750, TimeUnit.MILLISECONDS)) { process.destroyForcibly(); return; }
            if (process.exitValue() != 0) return;
            String displays = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String primary = monitor(displays, true);
            if (primary == null) primary = monitor(displays, false);
            if (primary != null && needsDoubleScale(primary)) System.setProperty("sun.java2d.uiScale", "2");
        } catch (Exception ignored) {
            // Optional Linux detection: systems without xrandr keep their native Java scaling.
        }
    }
    private static String monitor(String displays, boolean primary) {
        String marker = primary ? " connected primary " : " connected ";
        for (String line : displays.split("\\R"))
            if (line.contains(marker) && Pattern.compile("\\d+x\\d+\\+").matcher(line).find()) return line;
        return null;
    }
    static boolean needsDoubleScale(String monitor) {
        Matcher pixels = Pattern.compile(" (\\d+)x(\\d+)\\+").matcher(monitor);
        if (!pixels.find()) return false;
        int width = Integer.parseInt(pixels.group(1)), height = Integer.parseInt(pixels.group(2));
        Matcher physical = Pattern.compile("(\\d+)mm x (\\d+)mm").matcher(monitor);
        if (physical.find()) {
            int millimeters = Integer.parseInt(physical.group(1));
            if (millimeters > 0) return width * 25.4 / millimeters >= 170;
        }
        return width >= 2800 && height >= 1500;
    }
}
