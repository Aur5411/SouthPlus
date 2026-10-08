package android.net;

/**
 * 仅为在 PC 上跑 UrlMapper 单测而写的最小替身，不是 Android 实现。
 * 只覆盖 UrlMapper 用到的 getHost() / getPath() / getEncodedQuery()。
 */
public final class Uri {

    private final String scheme;
    private final String host;
    private final String path;
    private final String encodedQuery;

    private Uri(String scheme, String host, String path) {
        this(scheme, host, path, null);
    }

    private Uri(String scheme, String host, String path, String encodedQuery) {
        this.scheme = scheme;
        this.host = host;
        this.path = path;
        this.encodedQuery = encodedQuery;
    }

    public static Uri parse(String s) {
        if (s == null) return new Uri(null, null, null);
        String rest = s;
        String scheme = null;
        int schemeEnd = rest.indexOf("://");
        if (schemeEnd > 0) {
            scheme = rest.substring(0, schemeEnd);
            rest = rest.substring(schemeEnd + 3);
        } else if (rest.startsWith("//")) {
            rest = rest.substring(2);
        } else {
            return new Uri(null, null, rest);
        }
        String host;
        String path;
        int slash = rest.indexOf('/');
        if (slash < 0) {
            host = rest;
            path = "";
        } else {
            host = rest.substring(0, slash);
            path = rest.substring(slash);
        }
        String query = null;
        int q = path.indexOf('?');
        if (q >= 0) {
            query = path.substring(q + 1);
            path = path.substring(0, q);
        }
        int hash = host.indexOf('#');
        if (hash >= 0) host = host.substring(0, hash);
        int ph = path.indexOf('#');
        if (ph >= 0) path = path.substring(0, ph);
        return new Uri(scheme, host, path, query);
    }

    public String getScheme() {
        return scheme;
    }

    public String getHost() {
        return host;
    }

    public String getPath() {
        return path;
    }

    public String getEncodedQuery() {
        return encodedQuery;
    }
}
