package io.github.chronicle.testing.release;

import java.lang.reflect.*;
import java.util.*;
import net.fabricmc.api.ModInitializer;

/** Read-only diagnostic in disposable release fixtures; never part of a shipped mod. */
public final class ScreenTrace implements ModInitializer {
    private final Map<Object,String> snapshots=new IdentityHashMap<>();
    private final Map<Object,Object> lastOwner=new IdentityHashMap<>();
    public void onInitialize() {
        if(!"true".equals(System.getenv("CHRONICLE_FORGE_NATIVE_TRIAL")))return;
        try {
            var events=Class.forName("net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents");
            var eventType=Class.forName("net.fabricmc.fabric.api.event.Event");
            for(String phase:List.of("Start","End")) {
                var type=Class.forName(events.getName()+"$"+phase+"Tick");
                Object listener=Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(proxy,method,args)->{
                    if(method.getDeclaringClass()==Object.class)return switch(method.getName()){case "hashCode"->System.identityHashCode(proxy);case "equals"->proxy==args[0];default->"Release screen trace";};
                    inspect(args[0],phase);return null;
                });
                Object event=events.getField(phase.equals("Start")?"START_SERVER_TICK":"END_SERVER_TICK").get(null);
                eventType.getMethod("register",Object.class).invoke(event,listener);
            }
        }catch(Exception failure){throw new IllegalStateException("Could not register release diagnostic",failure);}
    }
    private static Object field(Object object,String name)throws Exception {
        Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);
    }
    private static Object call(Class<?> type,Object receiver,String name,Object... args)throws Exception {
        for(Method m:type.getMethods())if(m.getName().equals(name)&&m.getParameterCount()==args.length)return m.invoke(receiver,args);
        throw new NoSuchMethodException(name);
    }
    private static String current(Object owner)throws Exception {
        for(Field f:owner.getClass().getFields())if(Set.of("net.minecraft.class_1703","net.minecraft.screen.ScreenHandler").contains(f.getType().getName())){
            Object value=f.get(owner);return value.getClass().getSimpleName()+"@"+System.identityHashCode(value);
        }
        return "unknown";
    }
    private void inspect(Object server,String phase) {
        try {
            var runtime=Class.forName("io.github.chronicle.core.server.ItemEnchantingRuntime");Field worlds=runtime.getDeclaredField("SERVERS");worlds.setAccessible(true);
            Object world=((Map<?,?>)worlds.get(null)).get(server);if(world==null)return;
            StringBuilder text=new StringBuilder("phase=").append(phase).append(" hold=").append(call(Class.forName("io.github.chronicle.core.server.RuntimeHolds"),null,"reason",server));
            Object service=call(Class.forName("io.github.chronicle.core.server.ChronicleRuntime"),null,"service",server);
            Object state=call(service.getClass(),service,"state");text.append(" session=").append(call(state.getClass(),state,"phase"));
            for(Object binding:((Map<?,?>)field(world,"open")).values()) {
                Object handler=field(binding,"handler"),owner=call(handler.getClass(),handler,"owner"),gate=field(binding,"gate");lastOwner.put(server,owner);
                var type=Class.forName("io.github.chronicle.core.server.ItemEnchantingRuntime$Gate");
                text.append(" binding@").append(System.identityHashCode(handler)).append(" ritual=").append(field(binding,"phase"))
                    .append(" current=").append(current(owner)).append(" present=").append(call(type,gate,"stillPresent",owner)).append(" gate=").append(call(type,gate,"state",owner));
            }
            if(lastOwner.containsKey(server))text.append(" lastOwnerCurrent=").append(current(lastOwner.get(server)));
            String value=text.toString();String previous=snapshots.put(server,value.substring(value.indexOf(" hold=")));
            if(!Objects.equals(previous,value.substring(value.indexOf(" hold="))))System.out.println("[RELEASE_SCREEN_TRACE] "+value);
        }catch(Exception failure){System.out.println("[RELEASE_SCREEN_TRACE] failed "+failure);}
    }
}
