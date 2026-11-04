package io.github.chronicle.core.server;

import io.github.chronicle.core.sound.BundledSoundCatalog;
import java.util.*;
import net.minecraft.registry.*;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/** Common-side registry and readable labels for validated packaged Event audio. */
public final class EventSoundRegistry {
    private static final Map<String,BundledSoundCatalog.Sound> SOUNDS=new TreeMap<>();
    private static final Map<String,String> CATALOGS=new TreeMap<>();
    private EventSoundRegistry(){}
    public static synchronized void register(BundledSoundCatalog catalog){
        Objects.requireNonNull(catalog);if(CATALOGS.containsKey(catalog.namespace())||SOUNDS.size()+catalog.sounds().size()>512)throw new IllegalStateException("Bundled sound namespace is already registered or the 512-sound limit is reached.");
        for(var sound:catalog.sounds())if(Registries.SOUND_EVENT.containsId(Identifier.of(sound.id()))||SOUNDS.containsKey(sound.id()))throw new IllegalArgumentException("Bundled sound ID is already registered: "+sound.id());
        for(var sound:catalog.sounds()){var id=Identifier.of(sound.id());Registry.register(Registries.SOUND_EVENT,id,SoundEvent.of(id));SOUNDS.put(sound.id(),sound);}CATALOGS.put(catalog.namespace(),catalog.fingerprint());
    }
    public static synchronized Optional<BundledSoundCatalog.Sound> metadata(String id){return Optional.ofNullable(SOUNDS.get(id));}
    public static synchronized String fingerprint(){var bytes=new StringBuilder();CATALOGS.forEach((namespace,digest)->bytes.append(namespace).append(':').append(digest).append('\n'));return BundledSoundCatalog.sha256(bytes.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
}
