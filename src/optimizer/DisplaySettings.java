package optimizer;

/** Native desktop scaling by default, with an optional manual override. */
public final class DisplaySettings {
    public static void configure(String scale) {
        if (scale == null) return;
        if (!scale.equals("1") && !scale.equals("2")) throw new IllegalArgumentException("UI scale must be 1 or 2");
        System.setProperty("sun.java2d.uiScale", scale);
    }
}
