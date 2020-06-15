import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@Testcontainers
public class DockerTests
{
    private static final Logger LOG = LoggerFactory.getLogger(DockerTests.class);
    private static final Pattern PATTERN = Pattern.compile("^[0-9]+\\.[0-9]*-.*");
    private static List<String> imageTags;
    private static HttpClient httpClient;

    public static Stream<Arguments> getImageTags()
    {
        return imageTags.stream().map(Arguments::of);
    }

    @BeforeAll
    public static void beforeAll() throws Exception
    {
        // Assemble a list of all the jetty image tags we need to test.
        imageTags = Files.list(Paths.get(System.getProperty("user.dir")))
            .map(path -> path.getFileName().toString())
            .filter(fileName -> PATTERN.matcher(fileName).matches())
            .collect(Collectors.toList());

        // Use a docker image to run the Makefile to build all the jetty docker images.
        GenericContainer buildContainer = new GenericContainer("docker:latest")
            .withFileSystemBind("/var/run/docker.sock", "/var/run/docker.sock", BindMode.READ_WRITE)
            .withFileSystemBind(System.getProperty("user.dir"), "/work", BindMode.READ_WRITE)
            .withWorkingDirectory("/work")
            .withCommand("sh", "-c", "apk update && apk add make && make")
            .withLogConsumer(new Slf4jLogConsumer(LOG))
            .withStartupCheckStrategy(new OneShotStartupCheckStrategy().withTimeout(Duration.ofMinutes(10)));

        // Because we use the OneShotStartupCheckStrategy, the call to start blocks until the container exits.
        // This will throw if the container does not exit within the stop timeout or if returns exit code other than 0.
        buildContainer.start();

        httpClient = new HttpClient();
        httpClient.start();
    }

    @AfterAll
    public static void afterAll() throws Exception
    {
        if (httpClient != null)
            httpClient.stop();
    }

    private GenericContainer container;

    @ParameterizedTest
    @MethodSource("getImageTags")
    public void testJettyDockerImage(String imageTag) throws Exception
    {
        // Start a jetty docker image with this imageTag.
        container = new GenericContainer("jetty:" + imageTag).withExposedPorts(8080);
        container.start();

        // We should be able to get a 404 response from the running jetty server.
        ContentResponse response = httpClient.newRequest("http://localhost:" + container.getFirstMappedPort())
            .method(HttpMethod.GET)
            .send();
        assertThat(response.getStatus(), is(HttpStatus.NOT_FOUND_404));
    }

    @AfterEach
    public void afterEach()
    {
        if (container != null)
        {
            container.stop();
            container = null;
        }
    }
}
