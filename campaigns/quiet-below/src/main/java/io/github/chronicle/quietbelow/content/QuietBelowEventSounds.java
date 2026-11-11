package io.github.chronicle.quietbelow.content;

import io.github.chronicle.core.server.EventSoundRegistry;
import io.github.chronicle.core.sound.BundledSoundCatalog;
import java.io.*;

/** Quiet Below owns its recordings; Core owns shared validation and Event registration. */
public final class QuietBelowEventSounds {
    private QuietBelowEventSounds(){}
    public static void register(){
        try(var input=QuietBelowEventSounds.class.getResourceAsStream("/data/quiet_below/chronicle/event-sounds.json")){
            var catalog=BundledSoundCatalog.read(input);if(!catalog.namespace().equals("quiet_below"))throw new IllegalArgumentException("Unexpected Quiet Below audio namespace.");catalog.verify(QuietBelowEventSounds.class::getResourceAsStream);EventSoundRegistry.register(catalog);
        }catch(IOException|RuntimeException failure){throw new IllegalStateException("Quiet Below bundled audio is missing or invalid. Install the matching complete mod/pack; existing worlds were not changed.",failure);}
    }
}
