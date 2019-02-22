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
            System.out.println(reportingPath.toString() + " not exists.");
        }
    }

    private static void start(Path reportingPath) {
        File[] allReporting = reportingPath.toFile().listFiles();
        if (allReporting == null || allReporting.length == 0) {
            System.out.println("No files.");
            return;
        }

        System.out.println("Directories number : " + allReporting.length);

        int dayCount = 2;  // storing daily data for this many days
        int hourCount = 24 * dayCount;  // storing hourly data for the same as daily
        int minuteCount = 24 * 60 * 2; // storing minute points for fewer days
        // int dayCount = 180;  // storing daily data for this many days
        // int hourCount = 24 * dayCount;  // storing hourly data for the same as daily
        // int minuteCount = 24 * 60 * 32; // storing minute points for fewer days

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
                    if (file.getPath().endsWith("minute.bin")) {
                        truncateFileIfAbove(file, minuteCount);
                    } else if (file.getPath().endsWith("hourly.bin")) {
                        truncateFileIfAbove(file, hourCount);
                    } else if (file.getPath().endsWith("daily.bin")) {
                        truncateFileIfAbove(file, dayCount);
                    }
                }
            }
        }

        System.out.println("Visited files: " + filesCount + ". removed bytes: " + overrideCount);
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
                System.out.println("Error reading file " + file.getAbsolutePath() + "; skipping.");
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
