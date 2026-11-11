package io.github.chronicle.lastcouncil.content;

import java.util.*;

/** Trusted committed cast; only the addressed HousePapers should cross a player boundary. */
public record HouseCast(UUID castId, long rosterRevision, long seed, Map<UUID,HousePapers> papers, Set<String> usedSecretIds) {
    /** Legacy casts did not retain source IDs; their rendered papers remain authoritative. */
    public HouseCast(UUID castId,long rosterRevision,long seed,Map<UUID,HousePapers> papers){this(castId,rosterRevision,seed,papers,null);}
    public HouseCast {
        Objects.requireNonNull(castId); Objects.requireNonNull(papers);
        if (rosterRevision < 0 || papers.size() > 32) throw new IllegalArgumentException("invalid cast");
        papers = Map.copyOf(papers);
        if(usedSecretIds!=null){if(usedSecretIds.size()>256||usedSecretIds.stream().anyMatch(id->id==null||id.isBlank()))throw new IllegalArgumentException("invalid used secret IDs");usedSecretIds=Set.copyOf(usedSecretIds);}
        var houses = new HashSet<String>();
        papers.values().forEach(p -> { if (!houses.add(p.houseId())) throw new IllegalArgumentException("duplicate house"); });
        for (HousePapers p : papers.values()) {
            for (KnownSecret k : p.knownSecrets()) {
                HousePapers target = papers.values().stream().filter(x -> x.houseId().equals(k.targetHouseId())).findFirst().orElseThrow(() -> new IllegalArgumentException("unknown target"));
                if (target == p || !target.secrets().contains(k.body())) throw new IllegalArgumentException("invalid known secret");
            }
        }
    }
    public record HousePapers(String houseId, List<String> secrets, List<String> goals, List<KnownSecret> knownSecrets) {
        public HousePapers {
            if (houseId == null || houseId.length() > 128 || !houseId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException("invalid house id");
            secrets = List.copyOf(secrets); goals = List.copyOf(goals); knownSecrets = List.copyOf(knownSecrets);
            if (secrets.size() > 4 || goals.size() > 4 || knownSecrets.size() > 124) throw new IllegalArgumentException("papers exceed bounds");
            secrets.forEach(CouncilContent::text); goals.forEach(CouncilContent::text);
        }
    }
    public record KnownSecret(String targetHouseId, String body) {
        public KnownSecret { Objects.requireNonNull(targetHouseId); CouncilContent.text(body); }
    }
}
