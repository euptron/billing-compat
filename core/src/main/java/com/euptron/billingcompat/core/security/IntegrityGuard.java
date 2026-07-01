package com.euptron.billingcompat.core.security;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Debug;
import android.util.Log;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * {@code IntegrityGuard} provides comprehensive runtime security checks to protect the application
 * against unauthorized modification, instrumentation, and execution in compromised environments.
 *
 * <p>This utility implements multi-layered detection strategies to identify threats that could
 * compromise the integrity of billing operations, sensitive data, or intellectual property.
 *
 * <h2>Detection Capabilities</h2>
 *
 * <ul>
 *   <li><b>Patching Tools:</b> Scans for known package names associated with tools like Lucky
 *       Patcher, Game Guardian, and Freedom.
 *   <li><b>Instrumentation Frameworks:</b> Detects Frida server artifacts, open Frida ports, and
 *       Xposed Bridge class loading or stack trace hooks.
 *   <li><b>Debugging:</b> Identifies if the application is marked as {@code debuggable} in the
 *       manifest, if a debugger is actively attached, or if JDWP threads are present.
 *   <li><b>Environment Integrity:</b> Detects rooted devices (via {@code su} binary heuristics and
 *       root management apps) and identifies emulated environments.
 * </ul>
 *
 * <h2>Runtime Enforcement</h2>
 *
 * When a violation is detected via {@link #enforce(Activity)}, a non-cancellable system dialog is
 * displayed. To prevent bypass, the application will terminate using {@link
 * Activity#finishAffinity()} after redirecting the user to the official store page.
 */
public final class IntegrityGuard {

  private static final String TAG = "IntegrityGuard";

  // ====================================================
  // Known package names of patching, cheat and root tools
  // ====================================================
  private static final List<String> PATCHER_PACKAGES =
      Arrays.asList(
          // Lucky Patcher variants
          "com.chelpus.lackypatch",
          "com.chelpus.luckypatcher",
          "ru.wiabfaoi.llxyigdym",
          "com.dimonvideo.luckypatcher",
          "com.forpda.lp",
          "com.android.vending.billing.InAppBillingService.LUCK",
          "com.android.vending.billing.InAppBillingService.LOCK",
          // Game Guardian
          "org.cheatengine.cegui",
          "org.cheatengine.cegui.x86",
          "org.cheatengine.cegui.x64",
          "catch_.me_.if_.you_.can_",
          // Freedom / iAP Cracker equivalents
          "cc.madkite.freedom",
          "jase.freedom",
          // Uret Patcher
          "uret.jasi2169.patcher",
          "com.appsara.app",
          // SB Game Hacker
          "com.SBGameHacker",
          "com.sbgamehacker",
          // HappyMod
          "com.happymod.app",
          // Parallel Space (used to clone and patch)
          "com.lbe.parallel.intl",
          // Root management app packages
          "com.topjohnwu.magisk",
          "com.koushikdutta.superuser",
          "eu.chainfire.supersu",
          "com.noshufou.android.su",
          "com.thirdparty.superuser",
          "com.yellowes.su");

  // =============================================================
  // Frida artifacts commonly found on rooted/frida-server devices
  // =============================================================
  @SuppressLint("SdCardPath")
  private static final String[] FRIDA_FILES = {
    "/data/local/tmp/frida-server",
    "/data/local/tmp/re.frida.server",
    "/sdcard/frida-server",
    "/system/bin/frida-server"
  };

  // =============================================================
  // Common su binary locations
  // =============================================================
  private static final String[] SU_PATHS = {
    "/system/bin/su",
    "/system/xbin/su",
    "/sbin/su",
    "/su/bin/su",
    "/data/local/su",
    "/system/app/Superuser.apk",
    "/data/local/xbin/su",
    "/data/local/bin/su"
  };

  private IntegrityGuard() {
    // NO-OP
  }

  /**
   * Executes a comprehensive suite of runtime integrity checks to protect the application.
   *
   * <p>This method scans for unauthorized environment modifications, including rooting,
   * instrumentation frameworks (Frida, Xposed), debugging tools, and patching software. If a
   * violation is detected, a non-cancellable security dialog is displayed and the application
   * process is terminated.
   *
   * <p><b>Integration Note:</b> This should be called from {@see Activity#onCreate(Bundle)} before
   * {@code setContentView} to ensure the environment is secure before the UI is loaded.
   *
   * @param activity The current activity context required to display the security dialog.
   */
  public static void enforce(Activity activity) {
    ViolationType violation = detectViolation(activity);
    if (violation != null) {
      Log.e(TAG, "Integrity violation detected: " + violation);
      showViolationDialog(activity, violation);
    }
  }

  /**
   * Executes a comprehensive suite of runtime integrity checks to protect the application.
   *
   * <p>This method scans for unauthorized environment modifications, including rooting,
   * instrumentation frameworks (Frida, Xposed), debugging tools, and patching software. If a
   * violation is detected, a non-cancellable security dialog is displayed and the application
   * process is terminated.
   *
   * <p><b>Integration Note:</b> This should be called from {@see Activity#onCreate(Bundle)} before
   * {@code setContentView} to ensure the environment is secure before the UI is loaded.
   *
   * @param activity The current activity context required to display the security dialog.
   * @param ignoredViolations Optional set of violation types to ignore (pass null to check all)
   */
  public static void enforce(Activity activity, ViolationType... ignoredViolations) {
    List<ViolationType> ignoreList =
        ignoredViolations != null ? Arrays.asList(ignoredViolations) : Collections.emptyList();
    ViolationType violation = detectViolation(activity, ignoreList);
    if (violation != null) {
      Log.e(TAG, "Integrity violation detected: " + violation);
      showViolationDialog(activity, violation);
    }
  }

  /**
   * Scans the environment for security threats and returns the first detected integrity violation.
   *
   * <p>This method sequentially evaluates the device for unauthorized modification tools,
   * instrumentation frameworks (Frida, Xposed), active debuggers, root access, and emulated
   * environments.
   *
   * @param context The application context used for package manager and manifest lookups.
   * @return The {@link ViolationType} corresponding to the first failed check, or {@code null} if
   *     no security threats are identified.
   */
  public static ViolationType detectViolation(Context context) {
    if (isPatcherInstalled(context)) return ViolationType.PATCHER_INSTALLED;
    if (isFridaDetected()) return ViolationType.FRIDA_DETECTED;
    if (isXposedDetected()) return ViolationType.XPOSED_DETECTED;
    if (isDebuggable(context)) return ViolationType.APP_DEBUGGABLE;
    if (isDebuggerAttached()) return ViolationType.DEBUGGER_ATTACHED;
    if (isRooted()) return ViolationType.DEVICE_ROOTED;
    if (isEmulator()) return ViolationType.EMULATOR_DETECTED;
    return null;
  }

  /**
   * Scans the environment for security threats and returns the first detected integrity violation.
   *
   * <p>This method sequentially evaluates the device for unauthorized modification tools,
   * instrumentation frameworks (Frida, Xposed), active debuggers, root access, and emulated
   * environments.
   *
   * @param context The application context used for package manager and manifest lookups.
   * @param ignoreList List of violation types to ignore
   * @return The {@link ViolationType} corresponding to the first failed check, or {@code null} if
   *     no security threats are identified.
   */
  public static ViolationType detectViolation(Context context, List<ViolationType> ignoreList) {
    if (!ignoreList.contains(ViolationType.PATCHER_INSTALLED) && isPatcherInstalled(context))
      return ViolationType.PATCHER_INSTALLED;
    if (!ignoreList.contains(ViolationType.FRIDA_DETECTED) && isFridaDetected())
      return ViolationType.FRIDA_DETECTED;
    if (!ignoreList.contains(ViolationType.XPOSED_DETECTED) && isXposedDetected())
      return ViolationType.XPOSED_DETECTED;
    if (!ignoreList.contains(ViolationType.APP_DEBUGGABLE) && isDebuggable(context))
      return ViolationType.APP_DEBUGGABLE;
    if (!ignoreList.contains(ViolationType.DEBUGGER_ATTACHED) && isDebuggerAttached())
      return ViolationType.DEBUGGER_ATTACHED;
    if (!ignoreList.contains(ViolationType.DEVICE_ROOTED) && isRooted())
      return ViolationType.DEVICE_ROOTED;
    if (!ignoreList.contains(ViolationType.EMULATOR_DETECTED) && isEmulator())
      return ViolationType.EMULATOR_DETECTED;
    return null;
  }

  /**
   * Scans the device for known package names associated with patching tools, cheat engines, and
   * root management applications.
   *
   * <p>This method iterates through a predefined list of blacklisted packages (e.g., Lucky Patcher,
   * Game Guardian, Freedom) and attempts to retrieve their package information. If a match is
   * found, it indicates a high risk of unauthorized application modification or billing bypass.
   *
   * @param context The application context used to access the {@link PackageManager}.
   * @return {@code true} if any blacklisted package is currently installed, {@code false}
   *     otherwise.
   */
  public static boolean isPatcherInstalled(Context context) {
    PackageManager pm = context.getPackageManager();
    for (String pkg : PATCHER_PACKAGES) {
      try {
        pm.getPackageInfo(pkg, 0);
        Log.e(TAG, "Patcher/cheat tool detected: " + pkg);
        return true;
      } catch (PackageManager.NameNotFoundException ignored) {
        // Expected since app is not installed
      }
    }
    return false;
  }

  /**
   * Detects the presence of the Frida instrumentation framework on the device.
   *
   * <p>This method employs two detection strategies:
   *
   * <ul>
   *   <li><b>File-based detection:</b> Searches for common Frida server binaries and artifacts at
   *       known filesystem locations (e.g., {@code /data/local/tmp/frida-server}).
   *   <li><b>Network-based detection:</b> Scans the system's TCP network table via {@link
   *       #isFridaPortOpen()} to check if the default Frida server port (27042) is active and
   *       listening.
   * </ul>
   *
   * @return {@code true} if Frida artifacts or its listening port are detected, {@code false}
   *     otherwise.
   */
  public static boolean isFridaDetected() {
    for (String path : FRIDA_FILES) {
      if (new File(path).exists()) {
        Log.e(TAG, "Frida artifact found: " + path);
        return true;
      }
    }
    // Also check /proc/net/tcp for Frida's default port 27042
    return isFridaPortOpen();
  }

  /**
   * Detects the presence of the Xposed Framework using multiple verification techniques.
   *
   * <p>This method employs two primary detection strategies:
   *
   * <ul>
   *   <li><b>Class Loading:</b> Attempts to load {@code de.robv.android.xposed.XposedBridge} to
   *       determine if the framework's core classes are available in the current classpath.
   *   <li><b>Stack Trace Analysis:</b> Generates a synthetic exception and inspects the resulting
   *       stack trace for package names associated with Xposed, which indicates that the framework
   *       has hooked into the execution flow.
   * </ul>
   *
   * @return {@code true} if Xposed Bridge or active hooks are detected, {@code false} otherwise.
   */
  public static boolean isXposedDetected() {
    try {
      // If this class exists in the classpath, Xposed is active
      ClassLoader cl = IntegrityGuard.class.getClassLoader();
      if (cl != null) {
        cl.loadClass("de.robv.android.xposed.XposedBridge");
        Log.e(TAG, "Xposed Bridge detected.");
        return true;
      }
    } catch (ClassNotFoundException ignored) {
      // Xposed is not present
    }
    // Check the stack trace for Xposed method hooking artifacts
    try {
      throw new RuntimeException("Xposed stack trace check");
    } catch (Exception e) {
      for (StackTraceElement el : e.getStackTrace()) {
        if (el.getClassName().startsWith("de.robv.android.xposed")) {
          Log.e(TAG, "Xposed hook found in stack trace.");
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Checks if the application is currently running with the {@code debuggable} flag enabled.
   *
   * <p>This check inspects the {@link ApplicationInfo#flags} to determine if the {@code
   * android:debuggable} attribute is set to {@code true} in the manifest. Production release builds
   * should always have this flag disabled to prevent unauthorized instrumentation and information
   * leakage.
   *
   * @param context The application context used to retrieve package metadata.
   * @return {@code true} if the application is marked as debuggable, {@code false} otherwise.
   */
  public static boolean isDebuggable(Context context) {
    boolean debuggable =
        (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    if (debuggable) Log.e(TAG, "App is running as debuggable — NOT safe for production.");
    return debuggable;
  }

  /**
   * Detects if a debugger is currently attached or waiting to attach to the application process.
   *
   * <p>This method employs a two-tiered detection strategy:
   *
   * <ul>
   *   <li><b>Standard API Check:</b> Utilizes {@link Debug#isDebuggerConnected()} and {@link
   *       Debug#waitingForDebugger()} to identify active JDWP connections.
   *   <li><b>Thread Inspection:</b> Iterates through all active threads in the process to identify
   *       background threads associated with debugging protocols (e.g., "JDWP" or "Debugger"
   *       threads), which can signal an attachment even if standard APIs are bypassed.
   * </ul>
   */
  public static boolean isDebuggerAttached() {
    if (Debug.isDebuggerConnected() || Debug.waitingForDebugger()) {
      Log.e(TAG, "Debugger is attached.");
      return true;
    }
    // Additional check: look for the JDWP thread
    for (Thread t : Thread.getAllStackTraces().keySet()) {
      if (t.getName().contains("JDWP") || t.getName().contains("Debugger")) {
        Log.e(TAG, "JDWP / debugger thread found: " + t.getName());
        return true;
      }
    }
    return false;
  }

  /**
   * Performs heuristic checks to determine if the device is rooted.
   *
   * <p>This method employs multiple detection strategies, including searching for known {@code su}
   * binary paths, executing shell commands to locate the superuser executable, and inspecting
   * system build tags.
   *
   * <p>Note: Root detection is not 100% reliable, as sophisticated root management tools can
   * effectively hide their presence from the application.
   *
   * @return {@code true} if the device shows characteristics of being rooted, {@code false}
   *     otherwise.
   */
  public static boolean isRooted() {
    int score = 0;

    // 1. su binary paths
    for (String path : SU_PATHS) {
      if (new File(path).exists()) {
        Log.e(TAG, "su binary found: " + path);
        score += 3; // high confidence
        break;
      }
    }

    // 2. which su
    Process process = null;
    try {
      process = Runtime.getRuntime().exec(new String[] {"/system/xbin/which", "su"});
      BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
      String readLine = reader.readLine();
      if (readLine != null && !readLine.isEmpty()) {
        Log.e(TAG, "'which su' returned: " + readLine);
        score += 3; // // high confidence
      }
    } catch (Throwable t) {
      // can not execute, not conclusive
    } finally {
      if (process != null) {
        process.destroy();
      }
    }

    // 3. Build tags
    if (!isEmulator()) {
      String buildTags = Build.TAGS;
      if (buildTags != null && buildTags.contains("test-keys")) {
        Log.w(TAG, "Build tags contain test-keys");
        score += 1; // low confidence
      }
    }

    boolean rooted = score >= 3;
    if (rooted) {
      Log.e(TAG, "Device appears to be ROOTED (score: " + score + ")");
    }
    return rooted;
  }

  /**
   * Performs a heuristic check to determine if the application is running in an emulated
   * environment.
   *
   * <p>This method examines various system properties in {@link Build} (such as Fingerprint, Model,
   * Brand, and Hardware) for strings commonly associated with the Android SDK emulator, Genymotion,
   * or other virtualization tools.
   *
   * @return {@code true} if the environment displays characteristics of an emulator, {@code false}
   *     otherwise.
   */
  public static boolean isEmulator() {
    boolean suspicious =
        Build.FINGERPRINT.startsWith("generic")
            || Build.FINGERPRINT.startsWith("unknown")
            || Build.MODEL.contains("google_sdk")
            || Build.MODEL.contains("Emulator")
            || Build.MODEL.contains("Android SDK built for x86")
            || Build.MANUFACTURER.contains("Genymotion")
            || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
            || "google_sdk".equals(Build.PRODUCT)
            || Build.PRODUCT.contains("sdk")
            || Build.HARDWARE.contains("goldfish")
            || Build.HARDWARE.contains("ranchu");

    if (suspicious) Log.w(TAG, "Emulator environment detected.");
    return suspicious;
  }

  /**
   * Scans {@code /proc/net/tcp} to detect if Frida's default listening port (27042) is active.
   *
   * <p>This heuristic approach allows for the detection of a running Frida server even if the
   * binary has been renamed to evade file-based detection. It parses the system's TCP network table
   * to find matches for the hex-encoded port {@code 699A}.
   *
   * @return {@code true} if the Frida port is detected in a listening state, {@code false}
   *     otherwise or if the system file is inaccessible.
   */
  private static boolean isFridaPortOpen() {
    // Frida default port 27042 in hex (little-endian as shown in /proc/net/tcp) = 699A
    final String FRIDA_PORT_HEX = "699A";
    try (BufferedReader reader = new BufferedReader(new FileReader("/proc/net/tcp"))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.trim().startsWith("sl")) continue; // header
        String[] parts = line.trim().split("\\s+");
        if (parts.length > 2) {
          String localAddress = parts[1];
          // localAddress format: IPADDRESS:PORT — port is the part after ':'
          String[] addrPort = localAddress.split(":");
          if (addrPort.length == 2 && FRIDA_PORT_HEX.equalsIgnoreCase(addrPort[1])) {
            Log.e(TAG, "Frida port 27042 is open — Frida server is running.");
            return true;
          }
        }
      }
    } catch (IOException | SecurityException ignored) {
      // /proc/net/tcp not readable on some devices — treat as safe
    }
    return false;
  }

  private static void showViolationDialog(Activity activity, ViolationType type) {
    activity.runOnUiThread(
        () -> {
          String message = buildMessage(type);
          new android.app.AlertDialog.Builder(activity)
              .setTitle("Security Error")
              .setMessage(message)
              .setCancelable(false)
              .setPositiveButton(
                  "Download from Play Store",
                  (dialog, which) -> {
                    try {
                      activity.startActivity(
                          new android.content.Intent(
                              android.content.Intent.ACTION_VIEW,
                              android.net.Uri.parse(
                                  "market://details?id=" + activity.getPackageName())));
                    } catch (Exception ignored) {
                      // ignored
                    }
                    activity.finishAffinity();
                    System.exit(0);
                  })
              .show();
        });
  }

  private static String buildMessage(ViolationType type) {
    switch (type) {
      case PATCHER_INSTALLED:
        return "A software modification tool has been detected on your device. "
            + "This app cannot run in this environment.\n\n"
            + "Please download the original, unmodified version from the Google Play Store.";
      case FRIDA_DETECTED:
      case XPOSED_DETECTED:
        return "A runtime hooking framework has been detected. "
            + "This app cannot run in this environment.\n\n"
            + "Please download the official version from the Google Play Store.";
      case APP_DEBUGGABLE:
        return "This copy of the app has been modified and is running in debug mode. "
            + "Please download the official version from the Google Play Store.";
      case DEBUGGER_ATTACHED:
        return "A debugger is attached to this app. "
            + "This is not permitted. "
            + "Please download the official version from the Google Play Store.";
      case DEVICE_ROOTED:
        return "Your device appears to be rooted. "
            + "For your security, this app cannot run on rooted devices.\n\n"
            + "Please use an unrooted device.";
      case EMULATOR_DETECTED:
        return "This app cannot run in an emulated environment. "
            + "Please install it on a real device from the Google Play Store.";
      default:
        return "A security issue was detected. "
            + "Please download the official version from the Google Play Store.";
    }
  }

  /**
   * Describes the specific category of integrity violation detected by the guard.
   *
   * @see #enforce(Activity)
   */
  public enum ViolationType {
    PATCHER_INSTALLED,
    FRIDA_DETECTED,
    XPOSED_DETECTED,
    APP_DEBUGGABLE,
    DEBUGGER_ATTACHED,
    DEVICE_ROOTED,
    EMULATOR_DETECTED
  }
}
