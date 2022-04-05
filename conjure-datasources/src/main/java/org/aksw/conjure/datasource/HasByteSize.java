package org.aksw.conjure.datasource;

/** Interface which RdfDataSources can implement to indicate that they can
 * report their size in bytes */
public interface HasByteSize {
    long getByteSize();
}
