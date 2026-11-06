package io.github.chronicle.core.ballots;

import java.util.*;
import java.io.IOException;

final class BallotValidation {
    private BallotValidation() {}
    static void id(String s) { if (s == null || !s.matches("[A-Za-z0-9_.:/-]{1,128}")) throw new IllegalArgumentException("Invalid identifier"); }
    static void text(String s, int max, boolean newline) {
        if (s != null) for (int i=0; i<s.length(); i++) {
            char c=s.charAt(i);
            if (Character.isHighSurrogate(c)) { if (i+1>=s.length() || !Character.isLowSurrogate(s.charAt(++i))) throw new IllegalArgumentException("Malformed Unicode"); }
            else if (Character.isLowSurrogate(c)) throw new IllegalArgumentException("Malformed Unicode");
        }
        if (s == null || s.isBlank() || s.codePointCount(0, s.length()) > max || s.codePoints().anyMatch(c -> c == 0xa7 || (Character.isISOControl(c) && !(newline && c == '\n')))) throw new IllegalArgumentException("Invalid text");
    }
}
