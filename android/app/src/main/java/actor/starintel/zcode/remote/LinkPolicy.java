package actor.starintel.zcode.remote;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** Parse once, compare complete origins, never include a bearer URL in an error. */
public final class LinkPolicy {
    public static final int MAX_URL_LENGTH = 8192;
    private LinkPolicy() {}

    public static final class Link {
        public final String url;
        public final String origin;
        private Link(String url, String origin) { this.url = url; this.origin = origin; }
        @Override public String toString() { return origin; }
    }

    public static Link parse(String input) {
        if (input == null || input.length() > MAX_URL_LENGTH) throw invalid();
        String value = input.trim();
        if (value.isEmpty()) throw invalid();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c <= 32 || c >= 127 || c == '\\') throw invalid();
        }
        final URI uri;
        try { uri = new URI(value); } catch (URISyntaxException e) { throw invalid(); }
        String host = uri.getHost();
        int port = uri.getPort();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.isOpaque()
                || host == null || host.isEmpty() || uri.getRawUserInfo() != null
                || port == 0 || port < -1 || port > 65535) throw invalid();
        // Reject ambiguous empty ports and IPv6 zone identifiers as well.
        String authority = uri.getRawAuthority();
        if (authority.endsWith(":") || authority.indexOf('%') >= 0) throw invalid();
        String origin = "https://" + host.toLowerCase(Locale.ROOT)
                + (port == -1 || port == 443 ? "" : ":" + port);
        return new Link(uri.toASCIIString(), origin);
    }

    public static boolean sameOrigin(Link approved, String candidate) {
        if (approved == null) return false;
        try { return approved.origin.equals(parse(candidate).origin); }
        catch (IllegalArgumentException e) { return false; }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Use one complete HTTPS link, without a username or password in the host.");
    }
}
