package appeng.api.util;

public class AEUtils {
    public static String formatNumber(long number) {
        if (number < 1000) {
            return Long.toString(number);
        } else if (number < 1_000_000) {
            return (number / 1000) + "k";
        } else if (number < 1_000_000_000) {
            return (number / 1_000_000) + "M";
        } else if (number < 1_000_000_000_000L) {
            return (number / 1_000_000_000) + "G";
        } else {
            return (number / 1_000_000_000_000L) + "T";
        }
    }
}
