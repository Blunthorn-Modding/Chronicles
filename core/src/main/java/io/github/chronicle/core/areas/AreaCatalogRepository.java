package io.github.chronicle.core.areas;
import java.io.IOException;
public interface AreaCatalogRepository { AreaCatalog load() throws IOException; void save(AreaCatalog catalog) throws IOException; }
