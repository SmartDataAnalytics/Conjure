package org.aksw.conjure.datasource;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.PathMatcher;

import org.aksw.commons.io.util.FileUtils;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphWrapper;

public class DatasetGraphWrapperWithSize
    extends DatasetGraphWrapper
    implements HasByteSize
{
    protected Path path;

    // Matcher used to only consider the size of specific files located at the given path
    protected PathMatcher fileMatcher;

    public DatasetGraphWrapperWithSize(DatasetGraph dsg, Path path, PathMatcher fileMatcher) {
        super(dsg);
        this.path = path;
        this.fileMatcher = fileMatcher;
    }

    @Override
    public long getByteSize() {
        long result;
        try {
            result = FileUtils.sizeOfDirectory(path, fileMatcher);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return result;
    }
}
