package actor.starintel.zcode.remote;

import java.util.UUID;

public final class Profile {
    public enum Mode { DESKTOP_ATTACH, DIRECT_WEB }
    public final String id;
    public final String name;
    public final Mode mode;
    public final LinkPolicy.Link link;

    public Profile(String id, String name, Mode mode, String url) {
        UUID.fromString(id);
        if (name == null || name.trim().isEmpty() || name.length() > 64 || mode == null) {
            throw new IllegalArgumentException("Use a desktop name of 1 to 64 characters.");
        }
        for (int i = 0; i < name.length(); i++) {
            if (Character.isISOControl(name.charAt(i))) throw new IllegalArgumentException("Invalid desktop name");
        }
        this.id = id;
        this.name = name.trim();
        this.mode = mode;
        this.link = LinkPolicy.parse(url);
    }
    public String modeLabel() { return mode == Mode.DESKTOP_ATTACH ? "Desktop attach" : "Direct web server"; }
    @Override public String toString() { return modeLabel() + " @ " + link.origin; }
}
