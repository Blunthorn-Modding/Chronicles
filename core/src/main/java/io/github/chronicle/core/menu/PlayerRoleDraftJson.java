package io.github.chronicle.core.menu;

import com.google.gson.*;
import io.github.chronicle.core.books.BookConfig;
import io.github.chronicle.core.roles.PlayerRole;
import io.github.chronicle.core.roles.PlayerRoleConfig;
import java.util.Set;

/** Strict menu record decoding; reuses the authoritative YAML schema without Gson scalar coercion. */
public final class PlayerRoleDraftJson {
    private PlayerRoleDraftJson() { }
    public static PlayerRole decode(String source) {
        if(source==null||source.length()>131072)throw new IllegalArgumentException("Role draft exceeds bounds");
        var role=BookConfig.strictObject(source);
        for(String nullable:new String[]{"starting","respawn"})if(!role.has(nullable))role.add(nullable,JsonNull.INSTANCE);
        if(!role.keySet().equals(Set.of("id","name","description","selectable","capacity","starting","respawn","items","books")))throw new IllegalArgumentException("Role fields do not match the editor schema");
        if(!role.get("items").isJsonArray())throw new IllegalArgumentException("Starting items must be a list");
        var items=new JsonArray();
        for(var item:role.getAsJsonArray("items")){
            if(!item.isJsonObject()||!item.getAsJsonObject().keySet().equals(Set.of("stackJson")))throw new IllegalArgumentException("Invalid starting item fields");
            var value=item.getAsJsonObject().get("stackJson");
            if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Starting item JSON must be text");
            items.add(value);
        }
        role.add("items",items);
        var root=new JsonObject();root.addProperty("schemaVersion",1);var roles=new JsonArray();roles.add(role);root.add("roles",roles);
        return PlayerRoleConfig.decode(root.toString()).values().iterator().next();
    }
}
