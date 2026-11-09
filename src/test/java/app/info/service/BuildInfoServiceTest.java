package app.info.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Local launches without generated metadata still return an envelope-friendly value. */
class BuildInfoServiceTest {
    @Test
    void missingMetadataUsesStablePlaceholders() {
        @SuppressWarnings("unchecked") ObjectProvider<BuildProperties> builds = mock(ObjectProvider.class);
        var result = new BuildInfoService(builds, "local").current();
        assertEquals("unknown", result.version());
        assertEquals("unknown", result.buildTime());
        assertEquals("unknown", result.commit());
        assertEquals("local", result.environment());
    }

    @Test
    void validBuildMetadataExposesOnlyShortCommit() {
        @SuppressWarnings("unchecked") ObjectProvider<BuildProperties> builds = mock(ObjectProvider.class);
        var properties = new java.util.Properties();
        properties.setProperty("version", "1.0");
        properties.setProperty("commit", "abcdef0123456789abcdef0123456789abcdef01");
        when(builds.getIfAvailable()).thenReturn(new BuildProperties(properties));
        var result = new BuildInfoService(builds, "production").current();
        assertEquals("abcdef012345", result.commit());
        assertEquals("unknown", result.buildTime());
        assertEquals("production", result.environment());
    }
}
