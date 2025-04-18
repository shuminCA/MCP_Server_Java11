package modelcontextprotocol.util;

import java.util.Collection;

import reactor.util.annotation.Nullable;

public final class Assert {
    public Assert() {
    }

    public static void notEmpty(@Nullable Collection<?> collection, String message) {
        if (collection == null || collection.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
    }

    public static void notNull(@Nullable Object object, String message) {
        if (object == null) {
            throw new IllegalArgumentException(message);
        }
    }

    public static void hasText(@Nullable String text, String message) {
        if (!hasText(text)) {
            throw new IllegalArgumentException(message);
        }
    }

    public static boolean hasText(@Nullable String str) {
        return str != null && !str.isBlank();
    }
}
