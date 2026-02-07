package mnfu.clantag;

public class CacheEntry<T> {
    private final T value;
    private final long expiresAt;

    public CacheEntry(final T value, final long ttlMillis) {
        this.value = value;
        this.expiresAt = System.currentTimeMillis() + ttlMillis;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }

    public T getValue() {
        return value;
    }
}
