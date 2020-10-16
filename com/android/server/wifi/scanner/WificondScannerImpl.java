package com.android.server.wifi.scanner;

import android.app.AlarmManager;
import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiScanner;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.util.LocalLog;
import android.util.Log;
import com.android.server.wifi.Clock;
import com.android.server.wifi.ScanDetail;
import com.android.server.wifi.WifiMonitor;
import com.android.server.wifi.WifiNative;
import com.android.server.wifi.scanner.ChannelHelper;
import com.android.server.wifi.util.ScanResultUtil;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.concurrent.GuardedBy;

public class WificondScannerImpl extends WifiScannerImpl implements Handler.Callback {
    private static final boolean DBG = false;
    private static final int MAX_APS_PER_SCAN = 32;
    public static final int MAX_HIDDEN_NETWORK_IDS_PER_SCAN = 16;
    private static final int MAX_SCAN_BUCKETS = 16;
    private static final int RETURN_CACHED_SCAN_RESULTS_EVENT = 1;
    private static final int SCAN_BUFFER_CAPACITY = 10;
    private static final long SCAN_TIMEOUT_MS = 15000;
    private static final String TAG = "WificondScannerImpl";
    public static final String TIMEOUT_ALARM_TAG = "WificondScannerImpl Scan Timeout";
    private final AlarmManager mAlarmManager;
    private final ChannelHelper mChannelHelper;
    private final Clock mClock;
    private final Context mContext;
    private final Handler mEventHandler;
    private final boolean mHwPnoScanSupported;
    private final String mIfaceName;
    private ArrayList<ScanDetail> mLastNativeResults;
    private LastPnoScanSettings mLastPnoScanSettings = null;
    private final int[] mLastRssiDiff = {0, 0};
    private LastScanSettings mLastScanSettings = null;
    private WifiScanner.ScanData mLatestSingleScanResult = new WifiScanner.ScanData(0, 0, new ScanResult[0]);
    private final LocalLog mLocalLog = new LocalLog(128);
    private int mMaxNumScanSsids = -1;
    private ArrayList<ScanDetail> mNativePnoScanResults;
    private ArrayList<ScanDetail> mNativeScanResults;
    private int mNextHiddenNetworkScanId = 0;
    @GuardedBy("mSettingsLock")
    private AlarmManager.OnAlarmListener mScanTimeoutListener;
    private final Object mSettingsLock = new Object();
    private final WifiMonitor mWifiMonitor;
    private final WifiNative mWifiNative;

    public WificondScannerImpl(Context context, String ifaceName, WifiNative wifiNative, WifiMonitor wifiMonitor, ChannelHelper channelHelper, Looper looper, Clock clock) {
        this.mContext = context;
        this.mIfaceName = ifaceName;
        this.mWifiNative = wifiNative;
        this.mWifiMonitor = wifiMonitor;
        this.mChannelHelper = channelHelper;
        this.mAlarmManager = (AlarmManager) this.mContext.getSystemService("alarm");
        this.mEventHandler = new Handler(looper, this);
        this.mClock = clock;
        this.mHwPnoScanSupported = this.mContext.getResources().getBoolean(17891593);
        wifiMonitor.registerHandler(this.mIfaceName, WifiMonitor.SCAN_FAILED_EVENT, this.mEventHandler);
        wifiMonitor.registerHandler(this.mIfaceName, WifiMonitor.PNO_SCAN_RESULTS_EVENT, this.mEventHandler);
        wifiMonitor.registerHandler(this.mIfaceName, WifiMonitor.SCAN_RESULTS_EVENT, this.mEventHandler);
    }

    @Override // com.android.server.wifi.scanner.WifiScannerImpl
    public void cleanup() {
        synchronized (this.mSettingsLock) {
            stopHwPnoScan();
            this.mLastScanSettings = null;
            this.mLastPnoScanSettings = null;
            this.mWifiMonitor.deregisterHandler(this.mIfaceName, WifiMonitor.SCAN_FAILED_EVENT, this.mEventHandler);
            this.mWifiMonitor.deregisterHandler(this.mIfaceName, WifiMonitor.PNO_SCAN_RESULTS_EVENT, this.mEventHandler);
            this.mWifiMonitor.deregisterHandler(this.mIfaceName, WifiMonitor.SCAN_RESULTS_EVENT, this.mEventHandler);
            this.mNextHiddenNetworkScanId = 0;
        }
    }

    @Override // com.android.server.wifi.scanner.WifiScannerImpl
    public boolean getScanCapabilities(WifiNative.ScanCapabilities capabilities) {
        capabilities.max_scan_cache_size = Integer.MAX_VALUE;
        capabilities.max_scan_buckets = 16;
        capabilities.max_ap_cache_per_scan = 32;
        capabilities.max_rssi_sample_size = 8;
        capabilities.max_scan_reporting_threshold = 10;
        return true;
    }

    @Override // com.android.server.wifi.scanner.WifiScannerImpl
    public ChannelHelper getChannelHelper() {
        return this.mChannelHelper;
    }

    @Override // com.android.server.wifi.scanner.WifiScannerImpl
    public boolean startSingleScan(WifiNative.ScanSettings settings, WifiNative.ScanEventHandler eventHandler) {
        int id;
        if (eventHandler == null || settings == null) {
            Log.w(TAG, "Invalid arguments for startSingleScan: settings=" + settings + ",eventHandler=" + eventHandler);
            return false;
        }
        synchronized (this.mSettingsLock) {
            if (this.mLastScanSettings != null) {
                Log.w(TAG, "A single scan is already running");
                return false;
            }
            ChannelHelper.ChannelCollection allFreqs = this.mChannelHelper.createChannelCollection();
            boolean reportFullResults = false;
            for (int i = 0; i < settings.num_buckets; i++) {
                WifiNative.BucketSettings bucketSettings = settings.buckets[i];
                if ((bucketSettings.report_events & 2) != 0) {
                    reportFullResults = true;
                }
                allFreqs.addChannels(bucketSettings);
            }
            List<String> hiddenNetworkSSIDSet = new ArrayList<>();
            WifiNative.HiddenNetwork[] hiddenNetworks = settings.hiddenNetworks;
            if (hiddenNetworks != null) {
                if (this.mMaxNumScanSsids < 0) {
                    this.mMaxNumScanSsids = this.mWifiNative.getMaxNumScanSsids(this.mIfaceName);
                    Log.e(TAG, "mMaxNumScanSsids : " + this.mMaxNumScanSsids);
                    if (this.mMaxNumScanSsids < 1) {
                        Log.e(TAG, "driver supported max scan ssids num is " + this.mMaxNumScanSsids + ". so reset to zero");
                        this.mMaxNumScanSsids = 0;
                    } else {
                        this.mMaxNumScanSsids--;
                        Log.d(TAG, "max hidden network ids per scan is " + this.mMaxNumScanSsids);
                    }
                }
                int numHiddenNetworks = Math.min(hiddenNetworks.length, this.mMaxNumScanSsids);
                localLog("processPendingScans: hiddenNetworks length = " + hiddenNetworks.length + ", next HiddenNetwork scanId = " + this.mNextHiddenNetworkScanId + ", numHiddenNetworks = " + numHiddenNetworks);
                if (numHiddenNetworks == hiddenNetworks.length || this.mNextHiddenNetworkScanId >= hiddenNetworks.length) {
                    this.mNextHiddenNetworkScanId = 0;
                }
                int id2 = this.mNextHiddenNetworkScanId;
                int i2 = 0;
                while (id < hiddenNetworks.length && i2 < numHiddenNetworks) {
                    hiddenNetworkSSIDSet.add(hiddenNetworks[id].ssid);
                    i2++;
                    id2 = id + 1;
                }
                int size = hiddenNetworkSSIDSet.size();
                if (size < numHiddenNetworks && id >= hiddenNetworks.length) {
                    id = 0;
                    while (size < numHiddenNetworks) {
                        hiddenNetworkSSIDSet.add(hiddenNetworks[id].ssid);
                        id++;
                        size++;
                    }
                }
                this.mNextHiddenNetworkScanId = id;
            }
            this.mLastScanSettings = new LastScanSettings(this.mClock.getElapsedSinceBootMillis(), reportFullResults, allFreqs, eventHandler);
            this.mLastScanSettings.useCachedScan = settings.use_cached_scan == 1;
            boolean success = false;
            if (!allFreqs.isEmpty()) {
                Set<Integer> freqs = allFreqs.getScanFreqs();
                if (this.mLastScanSettings.useCachedScan) {
                    success = true;
                    this.mEventHandler.sendMessageDelayed(this.mEventHandler.obtainMessage(1), 1500);
                } else {
                    localLog("processPendingScans: freqs = " + freqs + ", hNetworkSSIDSet = " + hiddenNetworkSSIDSet);
                    success = this.mWifiNative.scan(this.mIfaceName, settings.scanType, freqs, hiddenNetworkSSIDSet);
                }
                if (!success) {
                    Log.e(TAG, "Failed to start scan, freqs=" + freqs);
                }
            } else {
                Log.e(TAG, "Failed to start scan because there is no available channel to scan");
            }
            if (success) {
                this.mScanTimeoutListener = new AlarmManager.OnAlarmListener() {
                    /* class com.android.server.wifi.scanner.WificondScannerImpl.C05641 */

                    public void onAlarm() {
                        WificondScannerImpl.this.handleScanTimeout();
                    }
                };
                this.mAlarmManager.set(2, this.mClock.getElapsedSinceBootMillis() + SCAN_TIMEOUT_MS, TIMEOUT_ALARM_TAG, this.mScanTimeoutListener, this.mEventHandler);
            } else {
                this.mEventHandler.post(new Runnable() {
                    /* class com.android.server.wifi.scanner.WificondScannerImpl.RunnableC05652 */

                    public void run() {
                        WificondScannerImpl.this.reportScanFailure();
                    }
                });
            }
            return true;
        }
    }

    @Override // com.android.server.wifi.scanner.WifiScannerImpl
    public WifiScanner.ScanData getLatestSingleScanResults() {
        return this.mLatestSingleScanResult;
    }

    @Override // com.android.server.wifi.scanner.WifiScannerImpl
    public boolean startBatchedScan(WifiNative.ScanSettings settings, WifiNative.ScanEventHandler eventHandler) {
        Log.w(TAG, "startBatchedScan() is not supported");
        return false;
    }

    @Override // com.android.server.wifi.scanner.WifiScannerImpl
    public void stopBatchedScan() {
        Log.w(TAG, "stopBatchedScan() is not supported");
    }

    @Override // com.android.server.wifi.scanner.WifiScannerImpl
    public void pauseBatchedScan() {
        Log.w(TAG, "pauseBatchedScan() is not supported");
    }

    @Override // com.android.server.wifi.scanner.WifiScannerImpl
    public void restartBatchedScan() {
        Log.w(TAG, "restartBatchedScan() is not supported");
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void handleScanTimeout() {
        synchronized (this.mSettingsLock) {
            Log.e(TAG, "Timed out waiting for scan result from wificond");
            reportScanFailure();
            this.mScanTimeoutListener = null;
        }
    }

    public boolean handleMessage(Message msg) {
        int i = msg.what;
        if (i != 1 && i != 147461) {
            switch (i) {
                case WifiMonitor.SCAN_FAILED_EVENT:
                    Log.w(TAG, "Scan failed");
                    cancelScanTimeout();
                    reportScanFailure();
                    break;
                case WifiMonitor.PNO_SCAN_RESULTS_EVENT:
                    pollLatestScanDataForPno();
                    break;
            }
        } else {
            cancelScanTimeout();
            pollLatestScanData();
        }
        return true;
    }

    private void cancelScanTimeout() {
        synchronized (this.mSettingsLock) {
            if (this.mScanTimeoutListener != null) {
                this.mAlarmManager.cancel(this.mScanTimeoutListener);
                this.mScanTimeoutListener = null;
            }
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void reportScanFailure() {
        synchronized (this.mSettingsLock) {
            if (this.mLastScanSettings != null) {
                if (this.mLastScanSettings.singleScanEventHandler != null) {
                    this.mLastScanSettings.singleScanEventHandler.onScanStatus(3);
                }
                this.mLastScanSettings = null;
            }
        }
    }

    private void reportPnoScanFailure() {
        synchronized (this.mSettingsLock) {
            if (this.mLastPnoScanSettings != null) {
                if (this.mLastPnoScanSettings.pnoScanEventHandler != null) {
                    this.mLastPnoScanSettings.pnoScanEventHandler.onPnoScanFailed();
                }
                this.mLastPnoScanSettings = null;
            }
        }
    }

    private void pollLatestScanDataForPno() {
        synchronized (this.mSettingsLock) {
            if (this.mLastPnoScanSettings != null) {
                this.mNativePnoScanResults = this.mWifiNative.getPnoScanResults(this.mIfaceName);
                List<ScanResult> hwPnoScanResults = new ArrayList<>();
                int numFilteredScanResults = 0;
                for (int i = 0; i < this.mNativePnoScanResults.size(); i++) {
                    ScanResult result = this.mNativePnoScanResults.get(i).getScanResult();
                    if (result.timestamp / 1000 > this.mLastPnoScanSettings.startTime) {
                        hwPnoScanResults.add(result);
                    } else {
                        numFilteredScanResults++;
                    }
                }
                if (numFilteredScanResults != 0) {
                    Log.d(TAG, "Filtering out " + numFilteredScanResults + " pno scan results.");
                }
                if (this.mLastPnoScanSettings.pnoScanEventHandler != null) {
                    this.mLastPnoScanSettings.pnoScanEventHandler.onPnoNetworkFound((ScanResult[]) hwPnoScanResults.toArray(new ScanResult[hwPnoScanResults.size()]));
                }
            }
        }
    }

    public void updateTimeStampForCachedScanResult(ArrayList<ScanDetail> scanResults) {
        int index = 0;
        long currentTime = System.currentTimeMillis() + 800;
        int rssiDiff = ((int) (currentTime % 7)) - 3;
        if (rssiDiff == this.mLastRssiDiff[0]) {
            rssiDiff--;
        }
        int rssiDiff2 = ((int) ((currentTime / 2) % 7)) - 3;
        if (rssiDiff2 == this.mLastRssiDiff[1]) {
            rssiDiff2++;
        }
        Iterator<ScanDetail> it = scanResults.iterator();
        while (it.hasNext()) {
            ScanResult result = it.next().getScanResult();
            if (result != null) {
                result.timestamp = SystemClock.elapsedRealtime() * 1000;
                result.seen = currentTime;
                if (index % 2 == 0) {
                    result.level = (result.level - this.mLastRssiDiff[0]) + rssiDiff;
                } else {
                    result.level = (result.level - this.mLastRssiDiff[1]) + rssiDiff2;
                }
                index++;
            }
        }
        int[] iArr = this.mLastRssiDiff;
        iArr[0] = rssiDiff;
        iArr[1] = rssiDiff2;
    }

    private static int getBandScanned(ChannelHelper.ChannelCollection channelCollection) {
        if (channelCollection.containsBand(7)) {
            return 7;
        }
        if (channelCollection.containsBand(3)) {
            return 3;
        }
        if (channelCollection.containsBand(6)) {
            return 6;
        }
        if (channelCollection.containsBand(2)) {
            return 2;
        }
        if (channelCollection.containsBand(4)) {
            return 4;
        }
        if (channelCollection.containsBand(1)) {
            return 1;
        }
        return 0;
    }

    private void pollLatestScanData() {
        synchronized (this.mSettingsLock) {
            if (this.mLastScanSettings != null) {
                if (this.mLastNativeResults == null || !this.mLastScanSettings.useCachedScan) {
                    this.mLastNativeResults = this.mWifiNative.getScanResults(this.mIfaceName);
                } else {
                    updateTimeStampForCachedScanResult(this.mLastNativeResults);
                }
                List<ScanResult> singleScanResults = new ArrayList<>();
                int numFilteredScanResults = 0;
                for (int i = 0; i < this.mLastNativeResults.size(); i++) {
                    ScanResult result = this.mLastNativeResults.get(i).getScanResult();
                    if (result.timestamp / 1000 <= this.mLastScanSettings.startTime) {
                        numFilteredScanResults++;
                    } else if (this.mLastScanSettings.singleScanFreqs.containsChannel(result.frequency)) {
                        singleScanResults.add(result);
                    }
                }
                if (numFilteredScanResults != 0) {
                    Log.d(TAG, "Filtering out " + numFilteredScanResults + " scan results.");
                }
                if (this.mLastScanSettings.singleScanEventHandler != null) {
                    if (this.mLastScanSettings.reportSingleScanFullResults) {
                        for (ScanResult scanResult : singleScanResults) {
                            this.mLastScanSettings.singleScanEventHandler.onFullScanResult(scanResult, 0);
                        }
                    }
                    Collections.sort(singleScanResults, SCAN_RESULT_SORT_COMPARATOR);
                    this.mLatestSingleScanResult = new WifiScanner.ScanData(0, 0, 0, getBandScanned(this.mLastScanSettings.singleScanFreqs), (ScanResult[]) singleScanResults.toArray(new ScanResult[singleScanResults.size()]));
                    this.mLastScanSettings.singleScanEventHandler.onScanStatus(0);
                }
                this.mLastScanSettings = null;
            }
        }
    }

    @Override // com.android.server.wifi.scanner.WifiScannerImpl
    public WifiScanner.ScanData[] getLatestBatchedScanResults(boolean flush) {
        return null;
    }

    private void localLog(String log) {
        this.mLocalLog.log(log);
    }

    private boolean startHwPnoScan(WifiNative.PnoSettings pnoSettings) {
        return this.mWifiNative.startPnoScan(this.mIfaceName, pnoSettings);
    }

    private void stopHwPnoScan() {
        this.mWifiNative.stopPnoScan(this.mIfaceName);
    }

    private boolean isHwPnoScanRequired(boolean isConnectedPno) {
        return !isConnectedPno && this.mHwPnoScanSupported;
    }

    @Override // com.android.server.wifi.scanner.WifiScannerImpl
    public boolean setHwPnoList(WifiNative.PnoSettings settings, WifiNative.PnoEventHandler eventHandler) {
        synchronized (this.mSettingsLock) {
            if (this.mLastPnoScanSettings != null) {
                Log.w(TAG, "Already running a PNO scan");
                return false;
            } else if (!isHwPnoScanRequired(settings.isConnected)) {
                return false;
            } else {
                if (startHwPnoScan(settings)) {
                    this.mLastPnoScanSettings = new LastPnoScanSettings(this.mClock.getElapsedSinceBootMillis(), settings.networkList, eventHandler);
                } else {
                    Log.e(TAG, "Failed to start PNO scan");
                    reportPnoScanFailure();
                }
                return true;
            }
        }
    }

    @Override // com.android.server.wifi.scanner.WifiScannerImpl
    public boolean resetHwPnoList() {
        synchronized (this.mSettingsLock) {
            if (this.mLastPnoScanSettings == null) {
                Log.w(TAG, "No PNO scan running");
                return false;
            }
            this.mLastPnoScanSettings = null;
            stopHwPnoScan();
            return true;
        }
    }

    @Override // com.android.server.wifi.scanner.WifiScannerImpl
    public boolean isHwPnoSupported(boolean isConnectedPno) {
        return isHwPnoScanRequired(isConnectedPno);
    }

    /* access modifiers changed from: protected */
    @Override // com.android.server.wifi.scanner.WifiScannerImpl
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        synchronized (this.mSettingsLock) {
            long nowMs = this.mClock.getElapsedSinceBootMillis();
            pw.println("Latest native scan results:");
            if (this.mLastNativeResults != null) {
                ScanResultUtil.dumpScanResults(pw, (List) this.mLastNativeResults.stream().map($$Lambda$WificondScannerImpl$CSjtYSyNiQ_mC6mOyQ4GpkylqY.INSTANCE).collect(Collectors.toList()), nowMs);
            }
            pw.println("Latest native pno scan results:");
            if (this.mNativePnoScanResults != null) {
                ScanResultUtil.dumpScanResults(pw, (List) this.mNativePnoScanResults.stream().map($$Lambda$WificondScannerImpl$VfxaUtYlcuU7Z28abhvk42O2k.INSTANCE).collect(Collectors.toList()), nowMs);
            }
            pw.println("WificondScannerImpl - Log Begin ----");
            this.mLocalLog.dump(fd, pw, args);
            pw.println("WificondScannerImpl - Log End ----");
        }
    }

    /* access modifiers changed from: private */
    public static class LastScanSettings {
        public boolean reportSingleScanFullResults;
        public WifiNative.ScanEventHandler singleScanEventHandler;
        public ChannelHelper.ChannelCollection singleScanFreqs;
        public long startTime;
        public boolean useCachedScan;

        LastScanSettings(long startTime2, boolean reportSingleScanFullResults2, ChannelHelper.ChannelCollection singleScanFreqs2, WifiNative.ScanEventHandler singleScanEventHandler2) {
            this.startTime = startTime2;
            this.reportSingleScanFullResults = reportSingleScanFullResults2;
            this.singleScanFreqs = singleScanFreqs2;
            this.singleScanEventHandler = singleScanEventHandler2;
        }
    }

    /* access modifiers changed from: private */
    public static class LastPnoScanSettings {
        public WifiNative.PnoNetwork[] pnoNetworkList;
        public WifiNative.PnoEventHandler pnoScanEventHandler;
        public long startTime;

        LastPnoScanSettings(long startTime2, WifiNative.PnoNetwork[] pnoNetworkList2, WifiNative.PnoEventHandler pnoScanEventHandler2) {
            this.startTime = startTime2;
            this.pnoNetworkList = pnoNetworkList2;
            this.pnoScanEventHandler = pnoScanEventHandler2;
        }
    }
}
