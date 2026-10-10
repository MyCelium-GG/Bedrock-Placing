package my.celium.org.bedrockplacing;

import java.nio.file.Path;

import my.celium.org.platform.Platform;
import my.celium.org.platform.PlatformService;

/** Headless {@link PlatformService} stub so config tests never touch loader APIs. */
public final class BedrockPlacingTestPlatform implements PlatformService {
    private final Path configDir;

    public BedrockPlacingTestPlatform(Path configDir) {
        this.configDir = configDir;
    }

    @Override
    public Platform platform() {
        return Platform.UNKNOWN;
    }

    @Override
    public String loaderVersion() {
        return "test";
    }

    @Override
    public String minecraftVersion() {
        return "26.3-test";
    }

    @Override
    public boolean isClient() {
        return false;
    }

    @Override
    public Path configDir() {
        return configDir;
    }

    @Override
    public boolean isModLoaded(String modId) {
        return false;
    }

    @Override
    public String getModVersion(String modId) {
        return null;
    }
}
