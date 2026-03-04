package io.synadia.utils;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.Properties;

public abstract class ArgumentUtils {

    public static String getProperty(Properties props, String dflt, String key) {
        String val = props.getProperty(key);
        return val == null ? dflt : val;
    }

    public static int getIntProperty(Properties props, int dflt, String key) {
        String val = props.getProperty(key);
        return val == null ? dflt : parseInt(val);
    }

    public static long getLongProperty(Properties props, long dflt, String key) {
        String val = props.getProperty(key);
        return val == null ? dflt : parseLong(val);
    }

    public static String getArg(String[] args, Object dflt, String... keys) {
        for (String key : keys) {
            String val = _getArg(args, key);
            if (val != null) {
                return val;
            }
        }
        return dflt == null ? null : dflt.toString();
    }

    private static String _getArg(String[] args, String key) {
        String query = key + "=";
        for (String arg : args) {
            if (arg.startsWith(query)) {
                int at = arg.indexOf('=');
                if (at == -1 || (at + 1 == arg.length())) {
                    return arg;
                }
                return arg.substring(at + 1);
            }
        }
        return null;
    }

    public static int getIntArg(String[] args, int dflt, String... keys) {
        String val = getArg(args, null, keys);
        return val == null ? dflt : parseInt(val);
    }

    public static long getLongArg(String[] args, long dflt, String... keys) {
        String val = getArg(args, null, keys);
        return val == null ? dflt : parseLong(val);
    }

    public static int parseInt(String val) {
        long l = parseLong(val);
        if (l > (long)Integer.MAX_VALUE || l < (long)Integer.MIN_VALUE) {
            throw new NumberFormatException(
                "Input string outside results in a number outside of the range for an int: \"" + val + "\"");
        }
        return (int)l;
    }

    public static long parseLong(String val) {
        String vl = prepareParseLong(val);

        long factor = 1;
        int fl = 1;
        if (vl.endsWith("k")) {
            factor = 1000;
        }
        else if (vl.endsWith("ki")) {
            factor = 1024;
            fl = 2;
        }
        else if (vl.endsWith("m")) {
            factor = 1_000_000;
        }
        else if (vl.endsWith("mi")) {
            factor = 1024 * 1024;
            fl = 2;
        }
        else if (vl.endsWith("g")) {
            factor = 1_000_000_000;
        }
        else if (vl.endsWith("gi")) {
            factor = 1024 * 1024 * 1024;
            fl = 2;
        }
        if (factor > 1) {
            vl = vl.substring(0, vl.length() - fl);
        }
        return Long.parseLong(vl) * factor;
    }

    public static String prepareParseLong(String val) {
        return val
            .trim()
            .toLowerCase()
            .replaceAll("_", "")
            .replaceAll(",", "")
            .replaceAll("\\.", "");
    }

    public static final NumberFormat FORMATTER = NumberFormat.getNumberInstance(Locale.getDefault());

    public static String format(Number n) {
        return FORMATTER.format(n);
    }

    public static String formatRight(Number n, int width) {
        return String.format("%" + width + "s", FORMATTER.format(n));
    }
}
