package util;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import org.testcontainers.containers.output.OutputFrame;

public class LogConsumer implements Consumer<OutputFrame>
{
    private final CountDownLatch complete = new CountDownLatch(1);
    private final StringBuilder logBuilder = new StringBuilder();
    private final boolean silent;

    public LogConsumer()
    {
        this(true);
    }

    public LogConsumer(boolean silent)
    {
        this.silent = silent;
    }

    @Override
    public void accept(OutputFrame outputFrame)
    {
        String logLine = outputFrame.getUtf8String();
        logBuilder.append(logLine);
        if (!silent)
            System.err.print(logLine);
        if (outputFrame.getType() == OutputFrame.OutputType.END)
            complete.countDown();
    }

    public String getLogString()
    {
        return logBuilder.toString();
    }

    public void await(long timeout, TimeUnit unit) throws Exception
    {
        if (!complete.await(timeout, unit))
            throw new TimeoutException();
    }
}
