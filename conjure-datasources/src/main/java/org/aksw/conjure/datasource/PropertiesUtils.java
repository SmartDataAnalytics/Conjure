package org.aksw.conjure.datasource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

import com.google.common.collect.Maps;

public class PropertiesUtils {
    public static Properties read(Path path) throws IOException {
        return read(path, new Properties());
    }

    public static Properties read(Path path, Properties props) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
        }
        return props;
    }

    public static Properties write(Path path, Properties props) throws IOException {
        try (OutputStream out = Files.newOutputStream(path)) {
            props.store(out, null);
        }
        return props;
    }

    public static Map<String, Object> toStringObjectMap(Properties props) {
        Map<String, Object> result = Maps.transformValues(Maps.fromProperties(props), v -> (Object)v);
        return result;
    }
}
