package io.github.chronicle.core.lore;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** Expands literal bindings once; binding values are never interpreted as templates. */
public final class TextTemplate {
    public static final int MAX_CHARACTERS = 16_384;
    public static final int MAX_BINDINGS = 128;
    private static final Pattern KEY = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*");
    private TextTemplate() {}

    public static String render(String template, Map<String, String> bindings) {
        validateText(template, "template");
        Objects.requireNonNull(bindings, "bindings");
        if (bindings.size() > MAX_BINDINGS) throw new IllegalArgumentException("too many bindings");
        bindings.forEach((key, value) -> {
            if (key == null || !KEY.matcher(key).matches()) throw new IllegalArgumentException("invalid binding key");
            validateText(value, "binding value");
        });
        StringBuilder result = new StringBuilder();
        int pos = 0;
        int characters = 0;
        while (pos < template.length()) {
            int start = template.indexOf("${", pos);
            int end = start < 0 ? template.length() : start;
            String literal = template.substring(pos, end);
            characters = append(result, literal, characters);
            if (start < 0) break;
            int close = template.indexOf('}', start + 2);
            if (close < 0) throw new IllegalArgumentException("unclosed placeholder");
            String key = template.substring(start + 2, close);
            if (!KEY.matcher(key).matches()) throw new IllegalArgumentException("invalid placeholder");
            String value = bindings.get(key);
            if (value == null) throw new IllegalArgumentException("missing binding: " + key);
            characters = append(result, value, characters);
            pos = close + 1;
        }
        return result.toString();
    }
    private static int append(StringBuilder result, String value, int characters) {
        int count = value.codePointCount(0, value.length());
        if (characters + count > MAX_CHARACTERS) throw new IllegalArgumentException("rendered text exceeds bounds");
        result.append(value);
        return characters + count;
    }
    private static void validateText(String text, String field) {
        Objects.requireNonNull(text, field);
        if (text.codePointCount(0, text.length()) > MAX_CHARACTERS) throw new IllegalArgumentException(field + " exceeds bounds");
        if (text.codePoints().anyMatch(cp -> (Character.isISOControl(cp) && cp != '\n' && cp != '\t') || cp == '\u00a7'))
            throw new IllegalArgumentException(field + " contains control or formatting characters");
    }
}
