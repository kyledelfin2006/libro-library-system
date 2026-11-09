package app.info.service;

import app.info.dto.BuildInfoDTO;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;

/** Reads only the safe build fields intentionally exposed to library staff. */
@Service
public class BuildInfoService {
    private final ObjectProvider<BuildProperties> builds;
    private final String environment;

    /** Accepts missing metadata for IDE launches and un-packaged tests. */
    public BuildInfoService(ObjectProvider<BuildProperties> builds,
                            @Value("${libro.environment-name:local}") String environment) {
        this.builds = builds;
        this.environment = environment;
    }

    /** Returns stable placeholders if Maven build metadata is unavailable. */
    public BuildInfoDTO current() {
        BuildProperties build = builds.getIfAvailable();
        if (build == null) {
            return new BuildInfoDTO("unknown", "unknown", "unknown", environment);
        }
        String commit = build.get("commit");
        // Never expose a branch, remote URL, or full build properties map.
        String shortCommit = commit != null && commit.matches("[0-9a-fA-F]{7,40}")
                ? commit.substring(0, Math.min(12, commit.length())) : "unknown";
        return new BuildInfoDTO(build.getVersion(),
                build.getTime() == null ? "unknown" : build.getTime().toString(), shortCommit, environment);
    }
}
