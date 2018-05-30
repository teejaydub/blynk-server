package cc.blynk.utils;

import java.io.InputStream;
import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.commons.io.FileUtils;

/**
 * Utility class to work with jar file. Used in order to find all static resources
 * within jar file and helps extract them into file system.
 *
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 11.12.15.
 */
public final class JarUtil {

    private JarUtil() {
    }

    /**
     * Unpacks all files from staticFolder of jar and puts them to current folder within staticFolder path.
     *
     * @param staticFolder - path to resources
     */
    public static boolean unpackStaticFiles(String jarPath, String staticFolder) {
        try {
            ArrayList<String> staticResources = find(staticFolder);

            if (staticResources.size() == 0) {
                return false;
            }

            for (String staticFile : staticResources) {
                try (InputStream is = JarUtil.class.getResourceAsStream("/" + staticFile)) {
                    Path newStaticFile = Paths.get(jarPath, staticFile);

                    Files.deleteIfExists(newStaticFile);
                    Files.createDirectories(newStaticFile);

                    Files.copy(is, newStaticFile, StandardCopyOption.REPLACE_EXISTING);
                }
            }

            // Now override the static files with installation-specific override files.
            File overrides = new File("static-override");
            if (overrides.exists()) {
                File staticDir = new File("static");
                FileUtils.copyDirectory(overrides, staticDir);
            }

            return true;
        } catch (Exception e) {
            throw new RuntimeException("Error unpacking static files.", e);
        }
    }

    /**
     * Returns list of resources that were found in staticResourcesFolder
     *
     * @param staticResourcesFolder - resource folder
     * @return - absolute path to resources within staticResourcesFolder
     */
    private static ArrayList<String> find(String staticResourcesFolder) throws Exception {
        if (!staticResourcesFolder.endsWith("/")) {
            staticResourcesFolder = staticResourcesFolder + "/";
        }
        CodeSource src = JarUtil.class.getProtectionDomain().getCodeSource();
        ArrayList<String> staticResources = new ArrayList<>();

        if (src != null) {
            URL jar = src.getLocation();
            try (ZipInputStream zip = new ZipInputStream(jar.openStream())) {
                ZipEntry ze;

                while ((ze = zip.getNextEntry()) != null) {
                    String entryName = ze.getName();
                    if (!ze.isDirectory() && entryName.startsWith(staticResourcesFolder)) {
                        //logging is disabled as logger is created later
                        //log.debug("Unpacking : {}", entryName);
                        staticResources.add(entryName);
                    }
                }
            }
        }

        return staticResources;
    }

    /**
     * Gets server version from jar file.
     *
     * @return server version
     */
    public static String getServerVersion() {
        try (InputStream is = JarUtil.class.getResourceAsStream("/META-INF/MANIFEST.MF")) {
            Properties properties = new Properties();
            properties.load(is);
            return properties.getProperty("Build-Number", "");
        } catch (Exception e) {
            return "";
        }
    }
}
