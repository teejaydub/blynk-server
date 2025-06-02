package cc.blynk.server.tools;

import cc.blynk.utils.FileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 05.02.17.
 */
public final class ReportingDataCleaner {

    private static int filesCount = 0;
    private static int overrideCount = 0;

    private ReportingDataCleaner() {
    }

    public static void main(String[] args) {
        String reportingFolder = args[0];
        Path reportingPath = Paths.get(reportingFolder);
        if (Files.exists(reportingPath)) {
            System.out.println("Starting processing " + reportingPath.toString());
            start(reportingPath);
        } else {
            System.err.println(reportingPath.toString() + " doesn't exist.");
        }
    }

    private static void start(Path reportingPath) {
        int dayCount = 32;  // storing daily data for this many days
        int hourCount = 24 * dayCount;
        int minuteCount = 60 * hourCount;
        int secondsCount = 60 * minuteCount;

        FileTime oldestTimeToKeep = FileTime.from(Instant.now().minusSeconds(secondsCount));
        long oldestFileMillisToKeep = oldestTimeToKeep.toMillis();

        File[] allReporting = reportingPath.toFile().listFiles();
        if (allReporting == null || allReporting.length == 0) {
            System.err.println("No files.");
            return;
        }

        System.out.println("Directory count: " + allReporting.length);

        for (File userDirectory : allReporting) {
            if (userDirectory.isDirectory()) {
                File[] userFiles = userDirectory.listFiles();
                if (userFiles == null || userFiles.length == 0) {
                    System.out.println(userDirectory + " is empty.");
                    try {
                        if (userDirectory.delete()) {
                            System.out.println(userDirectory + " deleted.");
                        }
                    } catch (Exception e) {
                        //ignore
                    }
                    continue;
                }
                for (File file : userFiles) {
                    if (filesCount != 0 && filesCount % 1000 == 0) {
                        System.out.println("Visited " + filesCount + " files.");
                    }
                    if (deleteFileIfTooOld(file, oldestFileMillisToKeep)) {
                        System.out.println("Deleted " + file.getPath() + "; too old.");
                    } else if (file.getPath().endsWith("minute.bin")) {
                        truncateFileIfAbove(file, minuteCount);
                    } else if (file.getPath().endsWith("hourly.bin")) {
                        truncateFileIfAbove(file, hourCount);
                    } else if (file.getPath().endsWith("daily.bin")) {
                        truncateFileIfAbove(file, dayCount);
                    }
                }
            }
        }

        System.out.println("Visited files: " + filesCount + ".  Truncated files: " + overrideCount);
    }

    private static boolean deleteFileIfTooOld(File file, long oldestFileMillisToKeep) {
        if (file.lastModified() < oldestFileMillisToKeep) {
            if (file.delete()) {
                return true;
            } else {
                System.err.println("Couldn't delete " + file.getPath() + ".");
            }
        }
        return false;
    }

    private static void truncateFileIfAbove(File file, int limit) {
        long fileSize = file.length();
        if (fileSize > limit * 16) {
            System.out.println("Found " + file.getPath() + ". Size: " + fileSize);
            try {
                Path path = file.toPath();
                ByteBuffer userReportingData = FileUtils.read(path, limit);
                ((Buffer) userReportingData).flip();
                write(file, userReportingData);
                System.out.println("Successfully copied. Freed bytes: "
                        + (fileSize - userReportingData.position()));
                overrideCount++;
            } catch (Exception e) {
                System.err.println("Error reading file " + file.getAbsolutePath() + "; skipping.");
            }
        }

        filesCount++;
    }

    private static void write(File file, ByteBuffer data) throws Exception {
        try (FileChannel channel = new FileOutputStream(file, false).getChannel()) {
            channel.write(data);
        }
    }

}
