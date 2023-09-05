import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.eclipse.jetty.client.HttpClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Testcontainers;
import util.ImageUtil;
import util.LogConsumer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

@Testcontainers
@SuppressWarnings("resource")
public class JavaOptionsTest
{
    private static final Logger LOG = LoggerFactory.getLogger(JavaOptionsTest.class);
    private static List<String> imageTags;
    private static HttpClient httpClient;

    public static Stream<Arguments> getImageTags()
    {
        return imageTags.stream()
            .map(tag -> Arguments.of(Named.of(tag, tag)));
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

    @ParameterizedTest(name = "{0}")
    @MethodSource("getImageTags")
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
    @MethodSource("getImageTags")
    public void testMultiLineJavaOpts(String imageTag) throws Exception
    {
        LogConsumer logConsumer = new LogConsumer();
        ImageFromDockerfile image = new ImageFromDockerfile()
            .withDockerfileFromBuilder(builder ->
            {
                builder.from("jetty:" + imageTag);
                builder.entryPoint("chmod 755 /run.sh && /run.sh");
                builder.user("root");
            });

        try (GenericContainer<?> container = new GenericContainer<>(image)
            .withClasspathResourceMapping("multi-line-test/run.sh", "/run.sh", BindMode.READ_ONLY)
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
