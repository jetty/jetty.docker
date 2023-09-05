import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.eclipse.jetty.client.HttpClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Testcontainers;
import util.ImageUtil;
import util.LogConsumer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Disabled("https://github.com/eclipse/jetty.docker/issues/153")
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

    @Disabled("https://github.com/eclipse/jetty.docker/issues/160")
    @ParameterizedTest()
    @MethodSource("getImageTags")
    public void testRemoteJvmDebug(String imageTag) throws Exception
    {
        LogConsumer logConsumer = new LogConsumer(false);
        ImageFromDockerfile image = new ImageFromDockerfile()
            .withDockerfileFromBuilder(builder ->
            {
                builder.from("jetty:" + imageTag);
                builder.env("JAVA_OPTIONS", "-agentlib:jdwp=transport=dt_socket,server=y,address=*:33333,suspend=n");
            });

        try (GenericContainer<?> container = new GenericContainer<>(image)
            .withLogConsumer(logConsumer))
        {
            container.start();
            assertTrue(logConsumer.awaitString("Listening for transport dt_socket at address: 33333"));
            assertTrue(logConsumer.awaitString("Server:main: Started"));

            Container.ExecResult ps = container.execInContainer("ps");
            assertThat(ps.getExitCode(), equalTo(0));
            String[] output = ps.getStdout().split("\n");
            assertThat(output.length, equalTo(3));

            // First line lists the different columns.
            String header = output[0].trim();
            assertThat(header, startsWith("PID"));
            assertThat(header, endsWith("CMD"));

            // First line is the java process should be on PID 1.
            String line1 = output[1].trim();
            assertThat(line1, startsWith("1"));
            assertThat(line1, endsWith("java"));

            // Second line is the call to ps that we are currently doing.
            String line2 = output[2].trim();
            assertThat(line2, endsWith("ps"));
        }
    }
}
