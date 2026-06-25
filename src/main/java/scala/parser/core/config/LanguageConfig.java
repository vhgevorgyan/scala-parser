package scala.parser.core.config;

import java.util.EnumSet;
import java.util.Set;

public class LanguageConfig {

    private final Set<LanguageFeature> enabled;

    public LanguageConfig(Set<LanguageFeature> enabled) {
        this.enabled = EnumSet.copyOf(enabled);
    }

    public static LanguageConfig allEnabled() {
        return new LanguageConfig(EnumSet.allOf(LanguageFeature.class));
    }

    public static LanguageConfig allDisabled() {
        return new LanguageConfig(EnumSet.noneOf(LanguageFeature.class));
    }

    public LanguageConfig with(LanguageFeature feature, boolean enable) {
        Set<LanguageFeature> copy = EnumSet.copyOf(this.enabled);
        if (enable) copy.add(feature);
        else copy.remove(feature);
        return new LanguageConfig(copy);
    }

    public boolean isEnabled(LanguageFeature feature) {
        return enabled.contains(feature);
    }

    public Set<LanguageFeature> enabled() {
        return EnumSet.copyOf(enabled);
    }

    /** Bottom-up build order for the master thesis demonstration. */
    public static LanguageConfig upTo(LanguageFeature top) {
        Set<LanguageFeature> set = EnumSet.noneOf(LanguageFeature.class);
        for (LanguageFeature f : LanguageFeature.values()) {
            set.add(f);
            if (f == top) break;
        }
        return new LanguageConfig(set);
    }
}
