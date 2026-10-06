package dev.maces;

/** Tiny legacy-text helpers (no Adventure / MiniMessage needed). */
final class Txt {
    private Txt() {}

    /** Translates '&' color codes to section-sign codes. */
    static String c(String s) {
        return s.replace('&', '\u00a7');
    }

    private static String hexColor(int r, int g, int b) {
        String h = String.format("%02x%02x%02x", r, g, b);
        StringBuilder sb = new StringBuilder("\u00a7x");
        for (char ch : h.toCharArray()) sb.append('\u00a7').append(ch);
        return sb.toString();
    }

    private static int lerp(int a, int b, double t) {
        return (int) Math.round(a + (b - a) * t);
    }

    /** Per-letter gradient across the given hex stops, e.g. "#fff176:#ff4fd8:#4df3ff". */
    static String gradient(String text, String stops, boolean bold) {
        String[] hex = stops.split(":");
        int n = text.length();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            double t = n == 1 ? 0 : (double) i / (n - 1);
            int seg = Math.min(hex.length - 2, (int) (t * (hex.length - 1)));
            double local = t * (hex.length - 1) - seg;
            int c1 = Integer.parseInt(hex[seg].substring(1), 16);
            int c2 = Integer.parseInt(hex[seg + 1].substring(1), 16);
            int r = lerp((c1 >> 16) & 255, (c2 >> 16) & 255, local);
            int g = lerp((c1 >> 8) & 255, (c2 >> 8) & 255, local);
            int b = lerp(c1 & 255, c2 & 255, local);
            sb.append(hexColor(r, g, b));
            if (bold) sb.append("\u00a7l");
            sb.append(text.charAt(i));
        }
        return sb.toString();
    }
}
