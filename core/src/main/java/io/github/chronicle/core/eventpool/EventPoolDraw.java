package io.github.chronicle.core.eventpool;

import io.github.chronicle.core.play.*;
import java.util.*;

/** The exact private selection and execution inputs persisted before dispatch. */
public record EventPoolDraw(String key,String poolId,String poolFingerprint,UUID operationId,EventConfiguration configuration,int session,UUID actor,List<UUID> recipients) {
    public EventPoolDraw {
        if(key==null||key.isBlank()||key.length()>256||key.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Invalid draw key");
        EventPoolDefinition.identifier(poolId);
        if(poolFingerprint==null||!poolFingerprint.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Invalid pool fingerprint");
        Objects.requireNonNull(operationId);Objects.requireNonNull(configuration);
        if(session<1||configuration.mode()!=PlayState.Mode.MANUAL||configuration.occurrencePolicy()!=EventOccurrencePolicy.ONCE||configuration.delayTicks()!=0||!configuration.enabled())throw new IllegalArgumentException("Invalid pool Event configuration");
        recipients=List.copyOf(recipients);
        if((recipients.isEmpty()&&!configuration.definition().progressOnly())||recipients.size()>PlayState.MAX_PARTICIPANTS||new HashSet<>(recipients).size()!=recipients.size())throw new IllegalArgumentException("Invalid recipients");
    }
}
