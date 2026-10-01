package dev.lgonnet.nat2j.lowering;

import javax.lang.model.SourceVersion;

public final class Names {

    private Names() {
    }

    public static String toFieldName(String naturalName) {
        String camel = camelCase(stripHash(naturalName));
        if (camel.isEmpty()) {
            throw new IllegalArgumentException("not a usable identifier: " + naturalName);
        }
        return SourceVersion.isName(camel) ? camel : camel + "_";
    }

    public static String toMethodName(String naturalName) {
        return toFieldName(naturalName);
    }

    public static String toTypeName(String naturalName) {
        String camel = camelCase(stripHash(naturalName));
        if (camel.isEmpty()) {
            throw new IllegalArgumentException("not a usable identifier: " + naturalName);
        }
        return Character.toUpperCase(camel.charAt(0)) + camel.substring(1);
    }

    private static String stripHash(String name) {
        return name.startsWith("#") ? name.substring(1) : name;
    }

    private static String camelCase(String name) {
        StringBuilder out = new StringBuilder();
        boolean upperNext = false;
        for (char c : name.toCharArray()) {
            if (c == '-' || c == '_' || c == '#' || c == '.') {
                upperNext = !out.isEmpty();
                continue;
            }
            if (!Character.isLetterOrDigit(c)) {
                continue;
            }
            if (out.isEmpty()) {
                out.append(Character.toLowerCase(c));
            } else if (upperNext) {
                out.append(Character.toUpperCase(c));
                upperNext = false;
            } else {
                out.append(Character.toLowerCase(c));
            }
        }
        return out.toString();
    }
}
