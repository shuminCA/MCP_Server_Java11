package modelcontextprotocol.util;

import java.util.Collection;
import java.util.Map;

import reactor.util.annotation.Nullable;

public final class Utils {
    public Utils() {
    }

    public static boolean hasText(@Nullable String str) {
        return str != null && !str.isBlank();
    }

    public static boolean isEmpty(@Nullable Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

    public static boolean isEmpty(@Nullable Map<?, ?> map) {
        return map == null || map.isEmpty();
    }
}
