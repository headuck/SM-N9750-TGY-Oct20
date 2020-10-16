package com.android.server.wifi;

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiScanner;
import android.os.Handler;
import android.os.UserHandle;
import android.os.WorkSource;
import android.provider.Settings;
import android.util.ArrayMap;
import android.util.Log;
import android.util.Pair;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.wifi.util.WifiPermissionsUtil;
import com.samsung.android.feature.SemCscFeature;
import com.samsung.android.net.wifi.OpBrandingLoader;
import com.samsung.android.server.wifi.AutoWifiController;
import com.sec.android.app.CscFeatureTagCOMMON;
import com.sec.android.app.CscFeatureTagWifi;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import javax.annotation.concurrent.NotThreadSafe;

@NotThreadSafe
public class ScanRequestProxy {
    @VisibleForTesting
    public static final int SCAN_REQUEST_THROTTLE_INTERVAL_BG_APPS_MS = 1800000;
    @VisibleForTesting
    public static final int SCAN_REQUEST_THROTTLE_MAX_IN_TIME_WINDOW_FG_APPS = 4;
    @VisibleForTesting
    public static final int SCAN_REQUEST_THROTTLE_TIME_WINDOW_FG_APPS_MS = 120000;
    private static final String TAG = "WifiScanRequestProxy";
    private static final OpBrandingLoader.Vendor mOpBranding = OpBrandingLoader.getInstance().getOpBranding();
    private static Set<String> mWhiteListForNLPBackgroundScanApp = new HashSet();
    private static Set<String> mWhiteListForNLPForegroundScanApp = new HashSet();
    private final ActivityManager mActivityManager;
    private final AppOpsManager mAppOps;
    private final Clock mClock;
    private final Context mContext;
    private final int mFirstScanResultIndex = 0;
    private final FrameworkFacade mFrameworkFacade;
    private final List<ScanResult> mLastScanResults = new ArrayList();
    private long mLastScanTimestampForBgApps = 0;
    private final ArrayMap<Pair<Integer, String>, LinkedList<Long>> mLastScanTimestampsForFgApps = new ArrayMap<>();
    private List<String> mScanPolicyHistoricalDumpLogs = new ArrayList();
    private final Object mScanPolicyLock = new Object();
    private boolean mScanningEnabled = false;
    private String mScanningEnabledReason = "default";
    private boolean mScanningForHiddenNetworksEnabled = false;
    private final ThrottleEnabledSettingObserver mThrottleEnabledSettingObserver;
    private boolean mVerboseLoggingEnabled = false;
    private final Object mWhitelistLock = new Object();
    private final WifiConfigManager mWifiConfigManager;
    private final WifiInjector mWifiInjector;
    private final WifiMetrics mWifiMetrics;
    private final WifiPermissionsUtil mWifiPermissionsUtil;
    private WifiScanner mWifiScanner;

    /* access modifiers changed from: private */
    public class GlobalScanListener implements WifiScanner.ScanListener {
        private GlobalScanListener() {
        }

        public void onSuccess() {
        }

        public void onFailure(int reason, String description) {
        }

        public void onResults(WifiScanner.ScanData[] scanDatas) {
            if (ScanRequestProxy.this.mVerboseLoggingEnabled) {
                Log.d(ScanRequestProxy.TAG, "Scan results received");
            }
            if (scanDatas.length != 1) {
                Log.wtf(ScanRequestProxy.TAG, "Found more than 1 batch of scan results, Failing...");
                ScanRequestProxy.this.sendScanResultBroadcast(false, false);
                return;
            }
            WifiScanner.ScanData scanData = scanDatas[0];
            ScanResult[] scanResults = scanData.getResults();
            if (ScanRequestProxy.this.mVerboseLoggingEnabled) {
                Log.d(ScanRequestProxy.TAG, "Received " + scanResults.length + " scan results");
            }
            ScanRequestProxy.this.mLastScanResults.clear();
            ScanRequestProxy.this.mLastScanResults.addAll(Arrays.asList(scanResults));
            if (scanData.getBandScanned() == 7) {
                ScanRequestProxy.this.sendScanResultBroadcast(true, false);
                return;
            }
            if (!ScanRequestProxy.this.mLastScanResults.isEmpty()) {
                ((ScanResult) ScanRequestProxy.this.mLastScanResults.get(0)).capabilities = ((ScanResult) ScanRequestProxy.this.mLastScanResults.get(0)).capabilities.concat("[PARTIAL]");
            }
            ScanRequestProxy.this.sendScanResultBroadcast(true, true);
        }

        public void onFullResult(ScanResult fullScanResult) {
        }

        public void onPeriodChanged(int periodInMs) {
        }
    }

    /* access modifiers changed from: private */
    public class ScanRequestProxyScanListener implements WifiScanner.ScanListener {
        private ScanRequestProxyScanListener() {
        }

        public void onSuccess() {
            if (ScanRequestProxy.this.mVerboseLoggingEnabled) {
                Log.d(ScanRequestProxy.TAG, "Scan request succeeded");
            }
        }

        public void onFailure(int reason, String description) {
            Log.e(ScanRequestProxy.TAG, "Scan failure received. reason: " + reason + ",description: " + description);
            ScanRequestProxy.this.sendScanResultBroadcast(false, false);
        }

        public void onResults(WifiScanner.ScanData[] scanDatas) {
        }

        public void onFullResult(ScanResult fullScanResult) {
        }

        public void onPeriodChanged(int periodInMs) {
        }
    }

    /* access modifiers changed from: private */
    public class ThrottleEnabledSettingObserver extends ContentObserver {
        private boolean mThrottleEnabled = true;

        ThrottleEnabledSettingObserver(Handler handler) {
            super(handler);
        }

        public void initialize() {
            ScanRequestProxy.this.mFrameworkFacade.registerContentObserver(ScanRequestProxy.this.mContext, Settings.Global.getUriFor("wifi_scan_throttle_enabled"), true, this);
            this.mThrottleEnabled = getValue();
            if (ScanRequestProxy.this.mVerboseLoggingEnabled) {
                Log.v(ScanRequestProxy.TAG, "Scan throttle enabled " + this.mThrottleEnabled);
            }
        }

        public boolean isEnabled() {
            return this.mThrottleEnabled;
        }

        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            this.mThrottleEnabled = getValue();
            Log.i(ScanRequestProxy.TAG, "Scan throttle enabled " + this.mThrottleEnabled);
        }

        private boolean getValue() {
            return ScanRequestProxy.this.mFrameworkFacade.getIntegerSetting(ScanRequestProxy.this.mContext, "wifi_scan_throttle_enabled", 1) == 1;
        }
    }

    ScanRequestProxy(Context context, AppOpsManager appOpsManager, ActivityManager activityManager, WifiInjector wifiInjector, WifiConfigManager configManager, WifiPermissionsUtil wifiPermissionUtil, WifiMetrics wifiMetrics, Clock clock, FrameworkFacade frameworkFacade, Handler handler) {
        this.mContext = context;
        this.mAppOps = appOpsManager;
        this.mActivityManager = activityManager;
        this.mWifiInjector = wifiInjector;
        this.mWifiConfigManager = configManager;
        this.mWifiPermissionsUtil = wifiPermissionUtil;
        this.mWifiMetrics = wifiMetrics;
        this.mClock = clock;
        this.mFrameworkFacade = frameworkFacade;
        this.mThrottleEnabledSettingObserver = new ThrottleEnabledSettingObserver(handler);
        if (OpBrandingLoader.Vendor.LGU == mOpBranding || OpBrandingLoader.Vendor.KOO == mOpBranding) {
            mWhiteListForNLPBackgroundScanApp.add(new String("com.lguplus.lgugpsnwps"));
        } else if (OpBrandingLoader.Vendor.SKT == mOpBranding) {
            mWhiteListForNLPBackgroundScanApp.add(new String("com.skt.hps20client"));
        }
        mWhiteListForNLPForegroundScanApp.add(new String("com.samsung.android.ipsgeofence"));
        mWhiteListForNLPBackgroundScanApp.add(new String("com.samsung.android.ipsgeofence"));
        mWhiteListForNLPForegroundScanApp.add(new String("com.samsung.android.samsungpositioning"));
        mWhiteListForNLPBackgroundScanApp.add(new String("com.samsung.android.samsungpositioning"));
        mWhiteListForNLPBackgroundScanApp.add(new String("com.samsung.android.networkdiagnostic"));
        mWhiteListForNLPBackgroundScanApp.add(new String("com.samsung.android.rubin.app"));
    }

    public void enableVerboseLogging(int verbose) {
        this.mVerboseLoggingEnabled = verbose > 0;
    }

    private boolean retrieveWifiScannerIfNecessary() {
        if (this.mWifiScanner == null) {
            this.mWifiScanner = this.mWifiInjector.getWifiScanner();
            this.mThrottleEnabledSettingObserver.initialize();
            WifiScanner wifiScanner = this.mWifiScanner;
            if (wifiScanner != null) {
                wifiScanner.registerScanListener(new GlobalScanListener());
            }
        }
        return this.mWifiScanner != null;
    }

    private void sendScanAvailableBroadcast(Context context, boolean available) {
        Log.d(TAG, "Sending scan available broadcast: " + available);
        Intent intent = new Intent("wifi_scan_available");
        intent.addFlags(67108864);
        if (available) {
            intent.putExtra("scan_enabled", 3);
        } else {
            intent.putExtra("scan_enabled", 1);
        }
        context.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
    }

    private void enableScanningInternal(boolean enable) {
        if (!retrieveWifiScannerIfNecessary()) {
            Log.e(TAG, "Failed to retrieve wifiscanner");
            return;
        }
        this.mWifiScanner.setScanningEnabled(enable);
        sendScanAvailableBroadcast(this.mContext, enable);
        if (!enable) {
            clearScanResults();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Scanning is ");
        sb.append(enable ? "enabled" : "disabled");
        Log.i(TAG, sb.toString());
    }

    public void enableScanning(boolean enable, boolean enableScanningForHiddenNetworks) {
        this.mScanningEnabled = enable;
        if (enable) {
            enableScanningInternal(true);
            this.mScanningForHiddenNetworksEnabled = enableScanningForHiddenNetworks;
            StringBuilder sb = new StringBuilder();
            sb.append("Scanning for hidden networks is ");
            sb.append(enableScanningForHiddenNetworks ? "enabled" : "disabled");
            Log.i(TAG, sb.toString());
            return;
        }
        enableScanningInternal(false);
    }

    public void enableScanning(boolean enable, boolean enableScanningForHiddenNetworks, String reason) {
        this.mScanningEnabledReason = reason;
        enableScanning(enable, enableScanningForHiddenNetworks);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void sendScanResultBroadcast(boolean scanSucceeded, boolean isPartialScan) {
        Intent intent = new Intent("android.net.wifi.SCAN_RESULTS");
        intent.addFlags(67108864);
        intent.putExtra("resultsUpdated", scanSucceeded);
        intent.putExtra("isPartialScan", isPartialScan);
        this.mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
        if ("VZW".equals(SemCscFeature.getInstance().getString(CscFeatureTagCOMMON.TAG_CSCFEATURE_COMMON_CONFIGIMPLICITBROADCASTS))) {
            Intent cloneIntent = (Intent) intent.clone();
            cloneIntent.setPackage("com.verizon.mips.services");
            this.mContext.sendBroadcastAsUser(cloneIntent, UserHandle.ALL);
        }
        if ("WeChatWiFi".equals(SemCscFeature.getInstance().getString(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_CONFIGSOCIALSVCINTEGRATIONN))) {
            Intent cloneIntent2 = (Intent) intent.clone();
            cloneIntent2.setPackage("com.samsung.android.wechatwifiservice");
            this.mContext.sendBroadcastAsUser(cloneIntent2, UserHandle.ALL);
        }
    }

    private void sendScanResultFailureBroadcastToPackage(String packageName) {
        Intent intent = new Intent("android.net.wifi.SCAN_RESULTS");
        intent.addFlags(67108864);
        intent.putExtra("resultsUpdated", false);
        intent.setPackage(packageName);
        this.mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
    }

    private void trimPastScanRequestTimesForForegroundApp(List<Long> scanRequestTimestamps, long currentTimeMillis) {
        Iterator<Long> timestampsIter = scanRequestTimestamps.iterator();
        while (timestampsIter.hasNext() && currentTimeMillis - timestampsIter.next().longValue() > 120000) {
            timestampsIter.remove();
        }
    }

    private LinkedList<Long> getOrCreateScanRequestTimestampsForForegroundApp(int callingUid, String packageName) {
        Pair<Integer, String> uidAndPackageNamePair = Pair.create(Integer.valueOf(callingUid), packageName);
        LinkedList<Long> scanRequestTimestamps = this.mLastScanTimestampsForFgApps.get(uidAndPackageNamePair);
        if (scanRequestTimestamps != null) {
            return scanRequestTimestamps;
        }
        LinkedList<Long> scanRequestTimestamps2 = new LinkedList<>();
        this.mLastScanTimestampsForFgApps.put(uidAndPackageNamePair, scanRequestTimestamps2);
        return scanRequestTimestamps2;
    }

    private boolean shouldScanRequestBeThrottledForForegroundApp(int callingUid, String packageName) {
        LinkedList<Long> scanRequestTimestamps = getOrCreateScanRequestTimestampsForForegroundApp(callingUid, packageName);
        long currentTimeMillis = this.mClock.getElapsedSinceBootMillis();
        trimPastScanRequestTimesForForegroundApp(scanRequestTimestamps, currentTimeMillis);
        if (scanRequestTimestamps.size() >= 4) {
            return true;
        }
        scanRequestTimestamps.addLast(Long.valueOf(currentTimeMillis));
        return false;
    }

    private boolean shouldScanRequestBeThrottledForBackgroundApp() {
        long lastScanMs = this.mLastScanTimestampForBgApps;
        long elapsedRealtime = this.mClock.getElapsedSinceBootMillis();
        if (lastScanMs != 0 && elapsedRealtime - lastScanMs < 1800000) {
            return true;
        }
        this.mLastScanTimestampForBgApps = elapsedRealtime;
        return false;
    }

    private boolean isRequestFromBackground(int callingUid, String packageName) {
        this.mAppOps.checkPackage(callingUid, packageName);
        try {
            return this.mActivityManager.getPackageImportance(packageName) > 125;
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to check the app state", e);
            return true;
        }
    }

    private boolean shouldScanRequestBeThrottledForApp(int callingUid, String packageName) {
        boolean isThrottled;
        boolean isThrottled2;
        if (isRequestFromBackground(callingUid, packageName)) {
            if (mWhiteListForNLPBackgroundScanApp.contains(packageName)) {
                isThrottled = false;
            } else {
                isThrottled = shouldScanRequestBeThrottledForBackgroundApp();
            }
            if (isThrottled) {
                if (this.mVerboseLoggingEnabled) {
                    Log.v(TAG, "Background scan app request [" + callingUid + ", " + packageName + "]");
                }
                this.mWifiMetrics.incrementExternalBackgroundAppOneshotScanRequestsThrottledCount();
            }
        } else {
            if (mWhiteListForNLPForegroundScanApp.contains(packageName)) {
                isThrottled2 = false;
            } else {
                isThrottled2 = shouldScanRequestBeThrottledForForegroundApp(callingUid, packageName);
            }
            if (isThrottled) {
                if (this.mVerboseLoggingEnabled) {
                    Log.v(TAG, "Foreground scan app request [" + callingUid + ", " + packageName + "]");
                }
                this.mWifiMetrics.incrementExternalForegroundAppOneshotScanRequestsThrottledCount();
            }
        }
        this.mWifiMetrics.incrementExternalAppOneshotScanRequestsCount();
        return isThrottled;
    }

    public boolean startScan(int callingUid, String packageName) {
        return startScan(callingUid, packageName, null);
    }

    public boolean startScan(int callingUid, String packageName, Set<Integer> freqs) {
        if (!this.mScanningEnabled) {
            Log.w(TAG, "Scanning is disabled. Skip this startScan!");
            return true;
        } else if (!retrieveWifiScannerIfNecessary()) {
            Log.e(TAG, "Failed to retrieve wifiscanner");
            sendScanResultFailureBroadcastToPackage(packageName);
            return false;
        } else {
            boolean fromSettingsOrSetupWizard = this.mWifiPermissionsUtil.checkNetworkSettingsPermission(callingUid) || this.mWifiPermissionsUtil.checkNetworkSetupWizardPermission(callingUid);
            if (fromSettingsOrSetupWizard || !this.mThrottleEnabledSettingObserver.isEnabled() || !shouldScanRequestBeThrottledForApp(callingUid, packageName)) {
                WorkSource workSource = new WorkSource(callingUid, packageName);
                WifiScanner.ScanSettings settings = new WifiScanner.ScanSettings();
                if (fromSettingsOrSetupWizard) {
                    settings.type = 2;
                }
                if (freqs == null || freqs.size() == 0) {
                    settings.band = 7;
                } else {
                    settings.band = 0;
                    int index = 0;
                    settings.channels = new WifiScanner.ChannelSpec[freqs.size()];
                    for (Integer freq : freqs) {
                        settings.channels[index] = new WifiScanner.ChannelSpec(freq.intValue());
                        index++;
                    }
                }
                settings.reportEvents = 3;
                settings.semPackageName = packageName;
                if (this.mScanningForHiddenNetworksEnabled || (packageName != null && AutoWifiController.AUTO_WIFI_PACKAGE_NAME.equals(packageName))) {
                    List<WifiScanner.ScanSettings.HiddenNetwork> hiddenNetworkList = this.mWifiConfigManager.retrieveHiddenNetworkList();
                    settings.hiddenNetworks = (WifiScanner.ScanSettings.HiddenNetwork[]) hiddenNetworkList.toArray(new WifiScanner.ScanSettings.HiddenNetwork[hiddenNetworkList.size()]);
                }
                this.mWifiScanner.startScan(settings, new ScanRequestProxyScanListener(), workSource);
                return true;
            }
            Log.i(TAG, "Scan request from " + packageName + " throttled");
            sendScanResultFailureBroadcastToPackage(packageName);
            return false;
        }
    }

    public List<ScanResult> getScanResults() {
        return this.mLastScanResults;
    }

    private void clearScanResults() {
        this.mLastScanResults.clear();
        this.mLastScanTimestampForBgApps = 0;
        this.mLastScanTimestampsForFgApps.clear();
    }

    public void clearScanRequestTimestampsForApp(String packageName, int uid) {
        if (this.mVerboseLoggingEnabled) {
            Log.v(TAG, "Clearing scan request timestamps for uid=" + uid + ", packageName=" + packageName);
        }
        this.mLastScanTimestampsForFgApps.remove(Pair.create(Integer.valueOf(uid), packageName));
    }

    public void setScanningEnabled(boolean enable, String reason) {
        this.mScanningEnabled = enable;
        if (!retrieveWifiScannerIfNecessary()) {
            Log.e(TAG, "setScanningEnabled: Failed to retrieve wifiscanner");
            return;
        }
        Log.d(TAG, "setScanningEnabled: " + enable + ", " + reason);
        this.mWifiScanner.setEnableScan(this.mScanningEnabled, reason);
    }

    public String semGetScanCounterForBigData(boolean resetCounter) {
        if (retrieveWifiScannerIfNecessary()) {
            return this.mWifiScanner.semGetScanCounterForBigData(resetCounter);
        }
        Log.e(TAG, "semGetScanCounterForBigData: Failed to retrieve wifiscanner");
        return null;
    }

    public void semSetCustomScanPolicy(String packageName, int scanType, int delayTime) {
        if (!retrieveWifiScannerIfNecessary()) {
            Log.e(TAG, "semSetCustomScanPolicy: Failed to retrieve wifiscanner");
        } else if (scanType <= 7) {
            this.mWifiScanner.semSetCustomScanPolicy(packageName, scanType, delayTime);
        } else {
            synchronized (this.mWhitelistLock) {
                if (scanType == 100) {
                    try {
                        if (!mWhiteListForNLPForegroundScanApp.contains(packageName)) {
                            mWhiteListForNLPForegroundScanApp.add(new String(packageName));
                        }
                    } catch (Throwable th) {
                        throw th;
                    }
                } else if (scanType == 101) {
                    if (mWhiteListForNLPForegroundScanApp.contains(packageName)) {
                        mWhiteListForNLPForegroundScanApp.remove(new String(packageName));
                    }
                } else if (scanType == 200) {
                    if (!mWhiteListForNLPBackgroundScanApp.contains(packageName)) {
                        mWhiteListForNLPBackgroundScanApp.add(new String(packageName));
                    }
                } else if (scanType != 201) {
                    Log.e(TAG, "semSetCustomScanPolicy, packageName : " + packageName + ", scanType : " + scanType);
                } else if (mWhiteListForNLPBackgroundScanApp.contains(packageName)) {
                    mWhiteListForNLPBackgroundScanApp.remove(new String(packageName));
                }
            }
            synchronized (this.mScanPolicyLock) {
                try {
                    if (this.mScanPolicyHistoricalDumpLogs.size() > 50) {
                        this.mScanPolicyHistoricalDumpLogs.remove(0);
                    }
                    List<String> list = this.mScanPolicyHistoricalDumpLogs;
                    list.add(scanType + ", package : " + packageName);
                } catch (ArrayIndexOutOfBoundsException e) {
                    Log.e(TAG, "Scan Policy Historical log ArrayIndexOutOfBoundsException !!");
                }
            }
        }
    }

    public void semSetMaxDurationForCachedScan(int duration) {
        if (!retrieveWifiScannerIfNecessary()) {
            Log.e(TAG, "semSetMaxDurationForCachedScan: Failed to retrieve wifiscanner");
        } else {
            this.mWifiScanner.semSetMaxDurationForCachedScan(duration);
        }
    }

    public void semUseSMDForCachedScan(boolean use) {
        if (!retrieveWifiScannerIfNecessary()) {
            Log.e(TAG, "semUseSMDForCachedScan: Failed to retrieve wifiscanner");
        } else {
            this.mWifiScanner.semUseSMDForCachedScan(use);
        }
    }

    public String semDumpCachedScanController() {
        if (!retrieveWifiScannerIfNecessary()) {
            Log.e(TAG, "semDumpCachedScanController: Failed to retrieve wifiscanner");
            return null;
        }
        String stringResult = this.mWifiScanner.semDumpCachedScanController();
        if (stringResult != null) {
            return stringResult;
        }
        return null;
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("dump of WifiScanRequestProxy");
        pw.println("mScanningEnabled " + this.mScanningEnabled);
        pw.println("mScanningEnabledReason " + this.mScanningEnabledReason);
        pw.println("Foreground whitelist :");
        synchronized (this.mWhitelistLock) {
            for (String fgWhiteList : mWhiteListForNLPForegroundScanApp) {
                pw.println(fgWhiteList);
            }
            pw.println();
            pw.println("Background Whitelist :");
            for (String bgWhiteList : mWhiteListForNLPBackgroundScanApp) {
                pw.println(bgWhiteList);
            }
            pw.println();
        }
        pw.println("Scan Policy call history : ");
        synchronized (this.mScanPolicyLock) {
            for (String scanPolicyDump : this.mScanPolicyHistoricalDumpLogs) {
                pw.println(scanPolicyDump);
            }
        }
        pw.println();
    }
}
