package org.fireworkrocket.lookup.ui.wallpaper;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.win32.StdCallLibrary;
import org.fireworkrocket.lookup.kernel.Untested;
import org.fireworkrocket.lookup.kernel.process.Download;
import org.fireworkrocket.lookup.kernel.process.PicProcessing;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

import static org.fireworkrocket.lookup.kernel.config.DefaultConfig.tempDownloadPath;
import static org.fireworkrocket.lookup.kernel.process.PicProcessing.picNum;
import static org.fireworkrocket.lookup.kernel.exception.ExceptionHandler.handleDebug;
import static org.fireworkrocket.lookup.ui.exception.ExceptionForwarder.handleException;
import static org.fireworkrocket.lookup.kernel.exception.ExceptionHandler.handleInfo;

/**
 * WallpaperChanger 类，用于更改桌面壁纸。
 */
public class WallpaperChanger {

    private final String[] wallpapers;

    /**
     * 构造函数，初始化壁纸数组。
     *
     * @param wallpapers 壁纸数组
     */
    public WallpaperChanger(String[] wallpapers) {
        this.wallpapers = wallpapers;
    }

    /**
     * 根据操作系统类型开始更改壁纸。
     *
     * @param wallpaper 壁纸路径
     */
    public static void startChanging(String wallpaper) {
        int osType = getOSType();
        if (osType == 1) {
            MicrosoftWindowsChangeWallpaper(wallpaper);
            handleDebug("操作系统类型: Windows");
        } else if (osType == 2) {
            LinuxChangeWallpaper(wallpaper);
            handleDebug("操作系统类型: Linux");
        } else if (osType == 3) {
            MacOSChangeWallpaper(wallpaper);
            handleDebug("操作系统类型: MacOS");
        }
    }

    /**
     * User32 接口，用于调用 Windows API。
     */
    public interface User32 extends StdCallLibrary {
        User32 INSTANCE = Native.load("user32", User32.class);

        boolean SystemParametersInfoW(int uiAction, int uiParam, Memory pvParam, int fWinIni);
    }

    /**
     * 更改 Windows 系统的壁纸。
     *
     * @param wallpaper 壁纸路径
     */
    public static void MicrosoftWindowsChangeWallpaper(String wallpaper) {
        int SPI_SETDESKWALLPAPER = 0x14;
        int SPIF_UPDATEINIFILE = 0x01;
        int SPIF_SENDCHANGE = 0x02;

        Memory memory = new Memory((wallpaper.length() + 1) * 2L);
        memory.setWideString(0, wallpaper);

        User32.INSTANCE.SystemParametersInfoW(
                SPI_SETDESKWALLPAPER,
                0,
                memory,
                SPIF_UPDATEINIFILE | SPIF_SENDCHANGE
        );
    }

    /**
     * 更改 Linux 系统的壁纸。
     *
     * @param wallpaper 壁纸路径
     */
    @Untested("此方法尚未测试")
    private static void LinuxChangeWallpaper(String wallpaper) {
        try {
            Runtime.getRuntime().exec(new String[]{"gsettings", "set", "org.gnome.desktop.background", "picture-uri", "file://" + wallpaper});
        } catch (IOException e) {
            handleException(e);
        }
    }

    static {
        System.loadLibrary("user32");
    }

    /**
     * 更改 MacOS 系统的壁纸。
     *
     * @param wallpaper 壁纸路径
     */
    @Untested("此方法尚未测试")
    private static void MacOSChangeWallpaper(String wallpaper) {
        String[] cmd = {"/usr/bin/osascript", "-e", "tell application \"Finder\" to set desktop picture to POSIX file \"" + wallpaper + "\""};
        try {
            Runtime.getRuntime().exec(cmd);
        } catch (IOException e) {
            handleException(e);
        }
    }

    /**
     * 获取操作系统类型。
     *
     * @return 操作系统类型
     */
    public static int getOSType() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            return 1;
        } else if (osName.contains("nix") || osName.contains("nux")) {
            return 2;
        } else if (osName.contains("mac")) {
            return 3;
        } else {
            return -1;
        }
    }

    static int retryCount;

    /**
     * 获取今日壁纸并更改。
     */
    public static void getTodayWallpaper() {
        ListeningWallpaper.setAppChangingWallpaper(true);
        try {
            picNum = 1;
            Download.filePath = Objects.requireNonNull(PicProcessing.getPic()).toString();
            Download.filePath = Download.filePath.replace("[", "").replace("]", "");
            if (!tempDownloadPath.exists()) {
                tempDownloadPath.mkdirs();
            }
            Download.savePath = tempDownloadPath.getPath();
            handleInfo("开始更改壁纸...");
            startChanging(Download.downLoadByUrl(Download.filePath, Download.savePath, true));
            checkAndClearFolder();
        } catch (Exception e) {
            retryCount++;
            if (retryCount < 3) {
                handleException(e);
                handleInfo("重试获取壁纸...");
                getTodayWallpaper();
            } else {
                retryCount = 0;
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * 检查并清理临时文件夹。
     */
    public static void checkAndClearFolder() {
        String folderPath = "/WallpaperTemp";
        Path path = Paths.get(folderPath);
        long sizeInBytes = 0;

        try {
            sizeInBytes = Files.walk(path)
                    .filter(p -> p.toFile().isFile())
                    .mapToLong(p -> p.toFile().length())
                    .sum();
        } catch (IOException e) {
            handleException(e);
        }

        long sizeInMegabytes = sizeInBytes / (1024 * 1024);

        if (sizeInMegabytes > 200) {
            handleDebug("文件夹大小为 " + sizeInMegabytes + "MB，正在清理文件夹...");
            File folder = new File(folderPath);
            File[] files = folder.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (!file.delete()) {
                        handleException(new Exception("无法清理临时文件: " + file));
                    }
                }
            }
        } else {
            handleInfo("文件夹大小为 " + sizeInMegabytes + "MB，无需清理文件夹");
        }
        handleInfo("文件夹大小检查完成");
    }
}