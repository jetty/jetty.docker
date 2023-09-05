package util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.jetty.util.StringUtil;

public class ImageUtil
{
    private static final String USER_DIR = System.getProperty("user.dir");

    public static List<String> getImageList() throws Exception
    {
        // Assemble a list of all the jetty image tags we need to test.
        Path userDir = Paths.get(USER_DIR);
        return Files.walk(userDir, 5)
            .filter(path -> path.endsWith("Dockerfile"))
            .filter(path ->
            {
                String baseImage = getBaseImage(path);
                String version = path.getParent().getParent().getFileName().toString();
                String tag = path.getParent().getFileName().toString();
                return !StringUtil.isEmpty(baseImage)
                    && !StringUtil.isEmpty(version)
                    && !StringUtil.isEmpty(tag)
                    && Character.isDigit(version.charAt(0));
            })
            .map(path ->
            {
                String baseImage = getBaseImage(path);
                String version = path.getParent().getParent().getFileName().toString();
                String tag = path.getParent().getFileName().toString();
                return version + "-" + tag + "-" + baseImage;
            })
            .collect(Collectors.toList());
    }

    private static String getBaseImage(Path path)
    {
        Path userDir = Paths.get(USER_DIR);
        if (userDir.equals(path.getParent().getParent().getParent().getParent()))
            return path.getParent().getParent().getParent().getFileName().toString();
        else if (userDir.equals(path.getParent().getParent().getParent().getParent().getParent()))
            return path.getParent().getParent().getParent().getParent().getFileName().toString()
                + "-" + path.getParent().getParent().getParent().getFileName().toString();
        return null;
    }
}
