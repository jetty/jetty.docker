import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.util.StringUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

@Testcontainers
public class DockerTests
{
    private static final Logger LOG = LoggerFactory.getLogger(DockerTests.class);
    private static final String USER_DIR = System.getProperty("user.dir");
    private static List<String> imageTags;
    private static HttpClient httpClient;

    public static Stream<Arguments> getImageTags()
    {
        return imageTags.stream().map(Arguments::of);
    }

    @BeforeAll
    public static void beforeAll() throws Exception
    {
        LOG.info("Running tests with user directory: {}", USER_DIR);

        // Assemble a list of all the jetty image tags we need to test.
        imageTags = Files.walk(Paths.get(USER_DIR), 4)
            .filter(path -> path.endsWith("Dockerfile"))
            .filter(path ->
            {
                String version = path.getParent().getParent().getFileName().toString();
                return !StringUtil.isEmpty(version) && Character.isDigit(version.charAt(0));
            })
            .map(path ->
            {
                String version = path.getParent().getParent().getFileName().toString();
                String tag = path.getParent().getFileName().toString();
                return version + "-" + tag;
            })
            .collect(Collectors.toList());
        LOG.info("{} jetty.docker image tags: {}", imageTags.size(), imageTags);

        httpClient = new HttpClient();
        httpClient.start();
    }

    @AfterAll
    public static void afterAll() throws Exception
    {
        if (httpClient != null)
            httpClient.stop();
    }

    @ParameterizedTest
    @MethodSource("getImageTags")
    public void testJettyDockerImage(String imageTag) throws Exception
    {
        // Start a jetty docker image with this imageTag, binding the directory of a simple webapp.
        String bindDir = "/var/lib/jetty/webapps/test-webapp";
        try (GenericContainer<?> container = new GenericContainer<>("jetty:" + imageTag)
            .withExposedPorts(8080)
            .withClasspathResourceMapping("test-webapp", bindDir, BindMode.READ_ONLY))
        {
            // Start the docker container and the server.
            container.start();

            // We should be able to get a 200 response from the test-webapp on the running jetty server.
            String uri = "http://" + container.getHost() + ":" + container.getMappedPort(8080) + "/test-webapp/index.html";
            ContentResponse response = httpClient.newRequest(uri)
                .method(HttpMethod.GET)
                .send();

            // We get the correct index.html for the test webapp.
            assertThat(response.getStatus(), is(HttpStatus.OK_200));
            String content = response.getContentAsString();

            assertThat(content, containsString("test-webapp"));
            assertThat(content, containsString("success"));
            assertThat(content, containsString("It works!"));
        }
    }
}
