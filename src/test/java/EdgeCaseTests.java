import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.eclipse.jetty.client.HttpClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

@Testcontainers
@SuppressWarnings("resource")
public class EdgeCaseTests
{
    private static final Logger LOG = LoggerFactory.getLogger(EdgeCaseTests.class);
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
        imageTags = ImageUtil.getImageList();
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

    @ParameterizedTest()
    @ValueSource(strings = {"evalexec", "echoexec", "experiment"})
    public void testSystemPropertyWithSpaces(String imageTag) throws Exception
    {
        LogConsumer logConsumer = new LogConsumer();

        try (GenericContainer<?> container = new GenericContainer<>("jetty:" + imageTag)
            .withCommand("-DtestProp=foo   bar", "--list-config")
            .withLogConsumer(logConsumer))
        {
            container.start();
            logConsumer.await(5, TimeUnit.SECONDS);
        }

        String logString = logConsumer.getLogString();
        assertThat(logString, containsString("testProp = foo   bar (<command-line>)"));
    }

    @ParameterizedTest()
    @ValueSource(strings = {"evalexec", "echoexec", "experiment"})
    public void testMultiLineJavaOpts(String imageTag) throws Exception
    {
        LogConsumer logConsumer = new LogConsumer();

        ImageFromDockerfile image = new ImageFromDockerfile()
            .withFileFromClasspath("run.sh", "multi-line-test/run.sh")
            .withFileFromClasspath("Dockerfile", "multi-line-test/Dockerfile_" + imageTag);

        try (GenericContainer<?> container = new GenericContainer<>(image)
            .withLogConsumer(logConsumer))
        {
            container.start();
            logConsumer.await(5, TimeUnit.SECONDS);
        }

        String log = logConsumer.getLogString();
        assertThat(log, containsString("logback.configurationFile = /var/lib/jetty/conf/logback.xml (<command-line>)"));
        assertThat(log, containsString("file.encoding = ISO-8859-1 (<command-line>)"));
        assertThat(log, containsString("user.country = NL (<command-line>)"));
        assertThat(log, containsString("user.language = nl (<command-line>)"));
        assertThat(log, containsString("database.type = postgres (<command-line>)"));
        assertThat(log, containsString("wicket.configuration = deployment (<command-line>)"));
        assertThat(log, containsString("logback.statusListenerClass = ch.qos.logback.core.status.OnConsoleStatusListener (<command-line>)"));
        assertThat(log, containsString("org.eclipse.jetty.server.Request.maxFormContentSize = 2000000 (<command-line>)"));
    }
}
