import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.jetty.client.HttpClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import util.ImageUtil;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

@Testcontainers
@SuppressWarnings("resource")
public class VersionUpdateTest
{
    private static List<String> imageTags;
    private static HttpClient httpClient;

    public static Stream<Arguments> getImageTags()
    {
        return imageTags.stream().map(Arguments::of);
    }

    @BeforeAll
    public static void beforeAll() throws Exception
    {
        imageTags = ImageUtil.getImageList();
        httpClient = new HttpClient();
        httpClient.start();
    }

    @AfterAll
    public static void afterAll() throws Exception
    {
        if (httpClient != null)
            httpClient.stop();
    }

    @DisplayName("testJettyDockerImage")
    @ParameterizedTest(name = "{displayName}: {0}")
    @MethodSource("getImageTags")
    public void testJettyDockerImage(String imageTag, @TempDir Path jettyBase) throws Exception
    {
        boolean alpine = imageTag.contains("alpine");
        Path src = Path.of("src/test/resources/old-jetty-base");
        Files.copy(src.resolve("jetty.start"), jettyBase.resolve("jetty.start"));

        // The alpine image needs some additional config to allow to run with a 1000 UID.
        String startCommand = "java -jar $JETTY_HOME/start.jar --add-to-start=http && /docker-entrypoint.sh";
        if (alpine)
            startCommand = "addgroup -g 1000 -S hostuser && " +
                "adduser  -u 1000 -S -G hostuser hostuser && " +
                "exec su -s /bin/sh hostuser -c \"" +
                "java -jar $JETTY_HOME/start.jar --add-to-start=http && " +
                "ls -la && " +
                "/docker-entrypoint.sh" +
                "\"";

        // Verify the jetty.start file is regenerated if there is a different jetty version.
        try (GenericContainer<?> container = new GenericContainer<>("jetty:" + imageTag)
            .withExposedPorts(8080)
            .withCreateContainerCmdModifier(cmd -> cmd.withUser(alpine ? "0:0" : "1000:1000"))
            .withFileSystemBind(jettyBase.toString(), "/var/lib/jetty", BindMode.READ_WRITE)
            .withCommand("sh", "-c", startCommand))
        {
            container.start();

            String logs = container.getLogs();
            assertThat(logs, containsString("Jetty version mismatch (old-version -> "));
            assertThat(logs, containsString("regenerating jetty.start"));
        }

        // Verify the jetty.start file not modified since the jetty version is not updated.
        try (GenericContainer<?> container = new GenericContainer<>("jetty:" + imageTag)
            .withExposedPorts(8080)
            .withCreateContainerCmdModifier(cmd -> cmd.withUser(alpine ? "0:0" : "1000:1000"))
            .withFileSystemBind(jettyBase.toString(), "/var/lib/jetty", BindMode.READ_WRITE)
            .withCommand("sh", "-c", startCommand))
        {
            container.start();

            // The jetty.start file should be regenerated if there is a different jetty version.
            String logs = container.getLogs();
            assertThat(logs, not(containsString("Jetty version mismatch (old-version -> ")));
            assertThat(logs, not(containsString("regenerating jetty.start")));
        }
    }
}
