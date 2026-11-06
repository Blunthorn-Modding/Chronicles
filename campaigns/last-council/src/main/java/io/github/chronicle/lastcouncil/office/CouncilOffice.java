package io.github.chronicle.lastcouncil.office;

import java.util.UUID;

/** The single, explicitly appointed office holder; null means vacant. */
public record CouncilOffice(long revision, UUID chancellor) {
    public CouncilOffice {
        if (revision < 0) throw new IllegalArgumentException("negative office revision");
    }
    public static CouncilOffice empty() { return new CouncilOffice(0, null); }
}
