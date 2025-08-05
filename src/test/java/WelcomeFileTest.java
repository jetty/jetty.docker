import java.util.List;
import java.util.stream.Stream;

import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import util.ImageUtil;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

@Testcontainers
@SuppressWarnings("resource")
public class WelcomeFileTest
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
    public void testJettyDockerImage(String imageTag) throws Exception
    {
        String commandPrefix = "";
        if (imageTag.contains("12.0"))
            commandPrefix = "java -jar $JETTY_HOME/start.jar --add-to-start=core-deploy ; ";

        // Start a jetty docker image with this imageTag, binding the directory of a simple webapp.
        String bindDir = "/var/lib/jetty/webapps/test-webapp";
        try (GenericContainer<?> container = new GenericContainer<>("jetty:" + imageTag)
            .withExposedPorts(8080)
            .withClasspathResourceMapping("test-webapp", bindDir, BindMode.READ_ONLY)
            .withCommand("sh", "-c", commandPrefix + "/docker-entrypoint.sh"))
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
