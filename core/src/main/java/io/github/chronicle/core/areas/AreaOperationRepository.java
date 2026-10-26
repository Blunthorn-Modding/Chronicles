package io.github.chronicle.core.areas;
import java.io.IOException;public interface AreaOperationRepository{AreaOperationJournal load()throws IOException;void save(AreaOperationJournal journal)throws IOException;}
