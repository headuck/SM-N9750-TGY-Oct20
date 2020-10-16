package com.android.server.wifi;

import android.app.AlarmManager;
import android.net.wifi.IApInterface;
import android.net.wifi.IApInterfaceEventCallback;
import android.net.wifi.IClientInterface;
import android.net.wifi.IPnoScanEvent;
import android.net.wifi.IScanEvent;
import android.net.wifi.ISendMgmtFrameEvent;
import android.net.wifi.IWifiScannerImpl;
import android.net.wifi.IWificond;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiSsid;
import android.os.Binder;
import android.os.Debug;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;
import com.android.server.wifi.WifiNative;
import com.android.server.wifi.WificondControl;
import com.android.server.wifi.hotspot2.NetworkDetail;
import com.android.server.wifi.util.InformationElementUtil;
import com.android.server.wifi.util.NativeUtil;
import com.android.server.wifi.util.ScanResultUtil;
import com.android.server.wifi.wificond.ChannelSettings;
import com.android.server.wifi.wificond.HiddenNetwork;
import com.android.server.wifi.wificond.NativeScanResult;
import com.android.server.wifi.wificond.PnoNetwork;
import com.android.server.wifi.wificond.PnoSettings;
import com.android.server.wifi.wificond.RadioChainInfo;
import com.android.server.wifi.wificond.SingleScanSettings;
import com.samsung.android.net.wifi.OpBrandingLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class WificondControl implements IBinder.DeathRecipient {
    private static final boolean DBG = Debug.semIsProductDev();
    public static final int SCAN_TYPE_PNO_SCAN = 1;
    public static final int SCAN_TYPE_SINGLE_SCAN = 0;
    public static final int SEND_MGMT_FRAME_TIMEOUT_MS = 1000;
    private static final String TAG = "WificondControl";
    private static final String TIMEOUT_ALARM_TAG = "WificondControl Send Management Frame Timeout";
    private static final OpBrandingLoader.Vendor mOpBranding = OpBrandingLoader.getInstance().getOpBranding();
    private AlarmManager mAlarmManager;
    private HashMap<String, IApInterfaceEventCallback> mApInterfaceListeners = new HashMap<>();
    private HashMap<String, IApInterface> mApInterfaces = new HashMap<>();
    private final CarrierNetworkConfig mCarrierNetworkConfig;
    private HashMap<String, IClientInterface> mClientInterfaces = new HashMap<>();
    private Clock mClock;
    private WifiNative.WificondDeathEventHandler mDeathEventHandler;
    private Handler mEventHandler;
    private boolean mIsEnhancedOpenSupported;
    private boolean mIsEnhancedOpenSupportedInitialized = false;
    private HashMap<String, IPnoScanEvent> mPnoScanEventHandlers = new HashMap<>();
    private HashMap<String, IScanEvent> mScanEventHandlers = new HashMap<>();
    private AtomicBoolean mSendMgmtFrameInProgress = new AtomicBoolean(false);
    private boolean mVerboseLoggingEnabled = false;
    private WifiInjector mWifiInjector;
    private WifiMonitor mWifiMonitor;
    private WifiNative mWifiNative = null;
    private IWificond mWificond;
    private HashMap<String, IWifiScannerImpl> mWificondScanners = new HashMap<>();

    /* access modifiers changed from: private */
    public class ScanEventHandler extends IScanEvent.Stub {
        private String mIfaceName;

        ScanEventHandler(String ifaceName) {
            this.mIfaceName = ifaceName;
        }

        @Override // android.net.wifi.IScanEvent
        public void OnScanResultReady() {
            Log.d(WificondControl.TAG, "Scan result ready event");
            WificondControl.this.mWifiMonitor.broadcastScanResultEvent(this.mIfaceName);
        }

        @Override // android.net.wifi.IScanEvent
        public void OnScanFailed() {
            Log.d(WificondControl.TAG, "Scan failed event");
            WificondControl.this.mWifiMonitor.broadcastScanFailedEvent(this.mIfaceName);
        }
    }

    WificondControl(WifiInjector wifiInjector, WifiMonitor wifiMonitor, CarrierNetworkConfig carrierNetworkConfig, AlarmManager alarmManager, Looper looper, Clock clock) {
        this.mWifiInjector = wifiInjector;
        this.mWifiMonitor = wifiMonitor;
        this.mCarrierNetworkConfig = carrierNetworkConfig;
        this.mAlarmManager = alarmManager;
        this.mEventHandler = new Handler(looper);
        this.mClock = clock;
    }

    /* access modifiers changed from: private */
    public class PnoScanEventHandler extends IPnoScanEvent.Stub {
        private String mIfaceName;

        PnoScanEventHandler(String ifaceName) {
            this.mIfaceName = ifaceName;
        }

        @Override // android.net.wifi.IPnoScanEvent
        public void OnPnoNetworkFound() {
            Log.d(WificondControl.TAG, "Pno scan result event");
            WificondControl.this.mWifiMonitor.broadcastPnoScanResultEvent(this.mIfaceName);
            WificondControl.this.mWifiInjector.getWifiMetrics().incrementPnoFoundNetworkEventCount();
        }

        @Override // android.net.wifi.IPnoScanEvent
        public void OnPnoScanFailed() {
            Log.d(WificondControl.TAG, "Pno Scan failed event");
            WificondControl.this.mWifiInjector.getWifiMetrics().incrementPnoScanFailedCount();
        }

        @Override // android.net.wifi.IPnoScanEvent
        public void OnPnoScanOverOffloadStarted() {
            Log.d(WificondControl.TAG, "Pno scan over offload started");
            WificondControl.this.mWifiInjector.getWifiMetrics().incrementPnoScanStartedOverOffloadCount();
        }

        @Override // android.net.wifi.IPnoScanEvent
        public void OnPnoScanOverOffloadFailed(int reason) {
            Log.d(WificondControl.TAG, "Pno scan over offload failed");
            WificondControl.this.mWifiInjector.getWifiMetrics().incrementPnoScanFailedOverOffloadCount();
        }
    }

    /* access modifiers changed from: private */
    public class ApInterfaceEventCallback extends IApInterfaceEventCallback.Stub {
        private WifiNative.SoftApListener mSoftApListener;

        ApInterfaceEventCallback(WifiNative.SoftApListener listener) {
            this.mSoftApListener = listener;
        }

        @Override // android.net.wifi.IApInterfaceEventCallback
        public void onNumAssociatedStationsChanged(int numStations) {
            this.mSoftApListener.onNumAssociatedStationsChanged(numStations);
        }

        @Override // android.net.wifi.IApInterfaceEventCallback
        public void onSoftApChannelSwitched(int frequency, int bandwidth) {
            this.mSoftApListener.onSoftApChannelSwitched(frequency, bandwidth);
        }
    }

    /* access modifiers changed from: private */
    public class SendMgmtFrameEvent extends ISendMgmtFrameEvent.Stub {
        private WifiNative.SendMgmtFrameCallback mCallback;
        private AlarmManager.OnAlarmListener mTimeoutCallback = new AlarmManager.OnAlarmListener() {
            /* class com.android.server.wifi.C0355x51a54758 */

            public final void onAlarm() {
                WificondControl.SendMgmtFrameEvent.this.lambda$new$1$WificondControl$SendMgmtFrameEvent();
            }
        };
        private boolean mWasCalled = false;

        private void runIfFirstCall(Runnable r) {
            if (!this.mWasCalled) {
                this.mWasCalled = true;
                WificondControl.this.mSendMgmtFrameInProgress.set(false);
                r.run();
            }
        }

        SendMgmtFrameEvent(WifiNative.SendMgmtFrameCallback callback) {
            this.mCallback = callback;
            WificondControl.this.mAlarmManager.set(2, WificondControl.this.mClock.getElapsedSinceBootMillis() + 1000, WificondControl.TIMEOUT_ALARM_TAG, this.mTimeoutCallback, WificondControl.this.mEventHandler);
        }

        public /* synthetic */ void lambda$new$1$WificondControl$SendMgmtFrameEvent() {
            runIfFirstCall(new Runnable() {
                /* class com.android.server.wifi.RunnableC0359x4bf7fd2e */

                public final void run() {
                    WificondControl.SendMgmtFrameEvent.this.lambda$new$0$WificondControl$SendMgmtFrameEvent();
                }
            });
        }

        public /* synthetic */ void lambda$new$0$WificondControl$SendMgmtFrameEvent() {
            if (WificondControl.this.mVerboseLoggingEnabled) {
                Log.e(WificondControl.TAG, "Timed out waiting for ACK");
            }
            this.mCallback.onFailure(4);
        }

        @Override // android.net.wifi.ISendMgmtFrameEvent
        public void OnAck(int elapsedTimeMs) {
            WificondControl.this.mEventHandler.post(new Runnable(elapsedTimeMs) {
                /* class com.android.server.wifi.RunnableC0360xf3be5c06 */
                private final /* synthetic */ int f$1;

                {
                    this.f$1 = r2;
                }

                public final void run() {
                    WificondControl.SendMgmtFrameEvent.this.lambda$OnAck$3$WificondControl$SendMgmtFrameEvent(this.f$1);
                }
            });
        }

        public /* synthetic */ void lambda$OnAck$3$WificondControl$SendMgmtFrameEvent(int elapsedTimeMs) {
            runIfFirstCall(new Runnable(elapsedTimeMs) {
                /* class com.android.server.wifi.RunnableC0356xd8708192 */
                private final /* synthetic */ int f$1;

                {
                    this.f$1 = r2;
                }

                public final void run() {
                    WificondControl.SendMgmtFrameEvent.this.lambda$OnAck$2$WificondControl$SendMgmtFrameEvent(this.f$1);
                }
            });
        }

        public /* synthetic */ void lambda$OnAck$2$WificondControl$SendMgmtFrameEvent(int elapsedTimeMs) {
            WificondControl.this.mAlarmManager.cancel(this.mTimeoutCallback);
            this.mCallback.onAck(elapsedTimeMs);
        }

        @Override // android.net.wifi.ISendMgmtFrameEvent
        public void OnFailure(int reason) {
            WificondControl.this.mEventHandler.post(new Runnable(reason) {
                /* class com.android.server.wifi.RunnableC0358xcb583963 */
                private final /* synthetic */ int f$1;

                {
                    this.f$1 = r2;
                }

                public final void run() {
                    WificondControl.SendMgmtFrameEvent.this.lambda$OnFailure$5$WificondControl$SendMgmtFrameEvent(this.f$1);
                }
            });
        }

        public /* synthetic */ void lambda$OnFailure$5$WificondControl$SendMgmtFrameEvent(int reason) {
            runIfFirstCall(new Runnable(reason) {
                /* class com.android.server.wifi.RunnableC0357x2509201a */
                private final /* synthetic */ int f$1;

                {
                    this.f$1 = r2;
                }

                public final void run() {
                    WificondControl.SendMgmtFrameEvent.this.lambda$OnFailure$4$WificondControl$SendMgmtFrameEvent(this.f$1);
                }
            });
        }

        public /* synthetic */ void lambda$OnFailure$4$WificondControl$SendMgmtFrameEvent(int reason) {
            WificondControl.this.mAlarmManager.cancel(this.mTimeoutCallback);
            this.mCallback.onFailure(reason);
        }
    }

    public void binderDied() {
        this.mEventHandler.post(new Runnable() {
            /* class com.android.server.wifi.$$Lambda$WificondControl$JpHuQX0ohyDoa8WT39rjqI0Ce_Q */

            public final void run() {
                WificondControl.this.lambda$binderDied$0$WificondControl();
            }
        });
    }

    public /* synthetic */ void lambda$binderDied$0$WificondControl() {
        Log.e(TAG, "Wificond died!");
        clearState();
        this.mWificond = null;
        WifiNative.WificondDeathEventHandler wificondDeathEventHandler = this.mDeathEventHandler;
        if (wificondDeathEventHandler != null) {
            wificondDeathEventHandler.onDeath();
        }
    }

    public void enableVerboseLogging(boolean enable) {
        this.mVerboseLoggingEnabled = enable;
    }

    public boolean initialize(WifiNative.WificondDeathEventHandler handler) {
        if (this.mDeathEventHandler != null) {
            Log.e(TAG, "Death handler already present");
        }
        this.mDeathEventHandler = handler;
        tearDownInterfaces();
        return true;
    }

    private boolean retrieveWificondAndRegisterForDeath() {
        if (this.mWificond != null) {
            if (this.mVerboseLoggingEnabled) {
                Log.d(TAG, "Wificond handle already retrieved");
            }
            return true;
        }
        this.mWificond = this.mWifiInjector.makeWificond();
        IWificond iWificond = this.mWificond;
        if (iWificond == null) {
            Log.e(TAG, "Failed to get reference to wificond");
            return false;
        }
        try {
            iWificond.asBinder().linkToDeath(this, 0);
            return true;
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to register death notification for wificond");
            return false;
        }
    }

    public IClientInterface setupInterfaceForClientMode(String ifaceName) {
        Log.d(TAG, "Setting up interface for client mode");
        if (!retrieveWificondAndRegisterForDeath()) {
            return null;
        }
        try {
            IClientInterface clientInterface = this.mWificond.createClientInterface(ifaceName);
            if (clientInterface == null) {
                Log.e(TAG, "Could not get IClientInterface instance from wificond");
                return null;
            }
            Binder.allowBlocking(clientInterface.asBinder());
            this.mClientInterfaces.put(ifaceName, clientInterface);
            try {
                IWifiScannerImpl wificondScanner = clientInterface.getWifiScannerImpl();
                if (wificondScanner == null) {
                    Log.e(TAG, "Failed to get WificondScannerImpl");
                    return null;
                }
                this.mWificondScanners.put(ifaceName, wificondScanner);
                Binder.allowBlocking(wificondScanner.asBinder());
                ScanEventHandler scanEventHandler = new ScanEventHandler(ifaceName);
                this.mScanEventHandlers.put(ifaceName, scanEventHandler);
                wificondScanner.subscribeScanEvents(scanEventHandler);
                PnoScanEventHandler pnoScanEventHandler = new PnoScanEventHandler(ifaceName);
                this.mPnoScanEventHandlers.put(ifaceName, pnoScanEventHandler);
                wificondScanner.subscribePnoScanEvents(pnoScanEventHandler);
                return clientInterface;
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to refresh wificond scanner due to remote exception");
            }
        } catch (RemoteException e2) {
            Log.e(TAG, "Failed to get IClientInterface due to remote exception");
            return null;
        }
    }

    public boolean tearDownClientInterface(String ifaceName) {
        if (getClientInterface(ifaceName) == null) {
            Log.e(TAG, "No valid wificond client interface handler");
            return false;
        }
        try {
            IWifiScannerImpl scannerImpl = this.mWificondScanners.get(ifaceName);
            if (scannerImpl != null) {
                scannerImpl.unsubscribeScanEvents();
                scannerImpl.unsubscribePnoScanEvents();
            }
            try {
                if (!this.mWificond.tearDownClientInterface(ifaceName)) {
                    Log.e(TAG, "Failed to teardown client interface");
                    return false;
                }
                this.mClientInterfaces.remove(ifaceName);
                this.mWificondScanners.remove(ifaceName);
                this.mScanEventHandlers.remove(ifaceName);
                this.mPnoScanEventHandlers.remove(ifaceName);
                return true;
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to teardown client interface due to remote exception");
                return false;
            }
        } catch (RemoteException e2) {
            Log.e(TAG, "Failed to unsubscribe wificond scanner due to remote exception");
            return false;
        }
    }

    public IApInterface setupInterfaceForSoftApMode(String ifaceName) {
        Log.d(TAG, "Setting up interface for soft ap mode");
        if (!retrieveWificondAndRegisterForDeath()) {
            return null;
        }
        try {
            IApInterface apInterface = this.mWificond.createApInterface(ifaceName);
            if (apInterface == null) {
                Log.e(TAG, "Could not get IApInterface instance from wificond");
                return null;
            }
            Binder.allowBlocking(apInterface.asBinder());
            this.mApInterfaces.put(ifaceName, apInterface);
            return apInterface;
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to get IApInterface due to remote exception");
            return null;
        }
    }

    public boolean tearDownSoftApInterface(String ifaceName) {
        if (getApInterface(ifaceName) == null) {
            Log.e(TAG, "No valid wificond ap interface handler");
            return false;
        }
        try {
            if (!this.mWificond.tearDownApInterface(ifaceName)) {
                Log.e(TAG, "Failed to teardown AP interface");
                return false;
            }
            this.mApInterfaces.remove(ifaceName);
            this.mApInterfaceListeners.remove(ifaceName);
            return true;
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to teardown AP interface due to remote exception");
            return false;
        }
    }

    public boolean tearDownInterfaces() {
        Log.d(TAG, "tearing down interfaces in wificond");
        if (!retrieveWificondAndRegisterForDeath()) {
            return false;
        }
        try {
            for (Map.Entry<String, IWifiScannerImpl> entry : this.mWificondScanners.entrySet()) {
                entry.getValue().unsubscribeScanEvents();
                entry.getValue().unsubscribePnoScanEvents();
            }
            this.mWificond.tearDownInterfaces();
            clearState();
            return true;
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to tear down interfaces due to remote exception");
            return false;
        }
    }

    private IClientInterface getClientInterface(String ifaceName) {
        return this.mClientInterfaces.get(ifaceName);
    }

    public WifiNative.SignalPollResult signalPoll(String ifaceName) {
        IClientInterface iface = getClientInterface(ifaceName);
        if (iface == null) {
            Log.e(TAG, "No valid wificond client interface handler");
            return null;
        }
        try {
            int[] resultArray = iface.signalPoll();
            if (resultArray == null || resultArray.length != 4) {
                Log.e(TAG, "Invalid signal poll result from wificond");
                return null;
            }
            WifiNative.SignalPollResult pollResult = new WifiNative.SignalPollResult();
            pollResult.currentRssi = resultArray[0];
            pollResult.txBitrate = resultArray[1];
            pollResult.associationFrequency = resultArray[2];
            pollResult.rxBitrate = resultArray[3];
            return pollResult;
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to do signal polling due to remote exception");
            return null;
        }
    }

    public WifiNative.TxPacketCounters getTxPacketCounters(String ifaceName) {
        IClientInterface iface = getClientInterface(ifaceName);
        if (iface == null) {
            Log.e(TAG, "No valid wificond client interface handler");
            return null;
        }
        try {
            int[] resultArray = iface.getPacketCounters();
            if (resultArray == null || resultArray.length != 2) {
                Log.e(TAG, "Invalid signal poll result from wificond");
                return null;
            }
            WifiNative.TxPacketCounters counters = new WifiNative.TxPacketCounters();
            counters.txSucceeded = resultArray[0];
            counters.txFailed = resultArray[1];
            return counters;
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to do signal polling due to remote exception");
            return null;
        }
    }

    private IWifiScannerImpl getScannerImpl(String ifaceName) {
        return this.mWificondScanners.get(ifaceName);
    }

    /* JADX WARNING: Removed duplicated region for block: B:66:0x01da  */
    public ArrayList<ScanDetail> getScanResults(String ifaceName, int scanType) {
        NativeScanResult[] nativeResults;
        NativeScanResult[] nativeResults2;
        String str;
        IWifiScannerImpl scannerImpl;
        NetworkDetail networkDetail;
        String flags;
        ArrayList<ScanDetail> results = new ArrayList<>();
        IWifiScannerImpl scannerImpl2 = getScannerImpl(ifaceName);
        String str2 = TAG;
        if (scannerImpl2 == null) {
            Log.e(str2, "No valid wificond scanner interface handler");
            return results;
        }
        if (scanType == 0) {
            try {
                nativeResults = scannerImpl2.getScanResults();
            } catch (RemoteException e) {
                Log.e(str2, "Failed to create ScanDetail ArrayList");
                if (this.mVerboseLoggingEnabled) {
                }
                return results;
            }
        } else {
            try {
                nativeResults = scannerImpl2.getPnoScanResults();
            } catch (RemoteException e2) {
                Log.e(str2, "Failed to create ScanDetail ArrayList");
                if (this.mVerboseLoggingEnabled) {
                }
                return results;
            }
        }
        int length = nativeResults.length;
        int i = 0;
        while (i < length) {
            NativeScanResult result = nativeResults[i];
            WifiSsid wifiSsid = WifiSsid.createFromByteArray(result.ssid);
            try {
                String bssid = NativeUtil.macAddressFromByteArray(result.bssid);
                if (bssid == null) {
                    Log.e(str2, "Illegal null bssid");
                    scannerImpl = scannerImpl2;
                    nativeResults2 = nativeResults;
                } else {
                    ScanResult.InformationElement[] ies = InformationElementUtil.parseInformationElements(result.infoElement);
                    InformationElementUtil.Capabilities capabilities = new InformationElementUtil.Capabilities();
                    capabilities.from(ies, result.capability, isEnhancedOpenSupported());
                    String flags2 = capabilities.generateCapabilitiesString();
                    try {
                        networkDetail = new NetworkDetail(bssid, ies, null, result.frequency);
                        scannerImpl = scannerImpl2;
                        if (OpBrandingLoader.Vendor.KTT == mOpBranding) {
                            try {
                                if (networkDetail.getChannelWidth() > 1) {
                                    flags2 = flags2 + "[VHT]";
                                }
                                if (DBG && capabilities.getKTVsd() != null) {
                                    Log.d(str2, "get scan results with KT OUI: VSD = " + capabilities.getKTVsd() + ", ssid = " + wifiSsid.toString() + ", bssid = " + bssid + ", freq = " + result.frequency + ", flags = " + flags2);
                                }
                                flags = flags2;
                            } catch (RemoteException e3) {
                                Log.e(str2, "Failed to create ScanDetail ArrayList");
                                if (this.mVerboseLoggingEnabled) {
                                }
                                return results;
                            }
                        } else {
                            flags = flags2;
                        }
                        str = str2;
                    } catch (IllegalArgumentException e4) {
                        scannerImpl = scannerImpl2;
                        str = str2;
                        nativeResults2 = nativeResults;
                        str2 = str;
                        Log.e(str2, "Illegal argument for scan result with bssid: " + bssid, e4);
                    }
                    try {
                        nativeResults2 = nativeResults;
                        ScanDetail scanDetail = new ScanDetail(networkDetail, wifiSsid, bssid, flags, result.signalMbm / 100, result.frequency, result.tsf, ies, null);
                        ScanResult scanResult = scanDetail.getScanResult();
                        if (ScanResultUtil.isScanResultForEapNetwork(scanDetail.getScanResult()) && this.mCarrierNetworkConfig.isCarrierNetwork(wifiSsid.toString())) {
                            scanResult.isCarrierAp = true;
                            scanResult.carrierApEapType = this.mCarrierNetworkConfig.getNetworkEapType(wifiSsid.toString());
                            scanResult.carrierName = this.mCarrierNetworkConfig.getCarrierName(wifiSsid.toString());
                        }
                        if (result.radioChainInfos != null) {
                            scanResult.radioChainInfos = new ScanResult.RadioChainInfo[result.radioChainInfos.size()];
                            int idx = 0;
                            Iterator<RadioChainInfo> it = result.radioChainInfos.iterator();
                            while (it.hasNext()) {
                                RadioChainInfo nativeRadioChainInfo = it.next();
                                scanResult.radioChainInfos[idx] = new ScanResult.RadioChainInfo();
                                scanResult.radioChainInfos[idx].id = nativeRadioChainInfo.chainId;
                                scanResult.radioChainInfos[idx].level = nativeRadioChainInfo.level;
                                idx++;
                            }
                        }
                        results.add(scanDetail);
                        str2 = str;
                    } catch (RemoteException e5) {
                        str2 = str;
                        Log.e(str2, "Failed to create ScanDetail ArrayList");
                        if (this.mVerboseLoggingEnabled) {
                        }
                        return results;
                    }
                }
            } catch (IllegalArgumentException e6) {
                scannerImpl = scannerImpl2;
                nativeResults2 = nativeResults;
                Log.e(str2, "Illegal argument " + result.bssid, e6);
            }
            i++;
            scannerImpl2 = scannerImpl;
            nativeResults = nativeResults2;
        }
        if (this.mVerboseLoggingEnabled) {
            Log.d(str2, "get " + results.size() + " scan results from wificond");
        }
        return results;
    }

    public NativeScanResult[] getRawScanResults(String ifaceName) {
        IWifiScannerImpl mWificondScanner = getScannerImpl(ifaceName);
        if (mWificondScanner == null) {
            Log.e(TAG, "No valid wificond scanner interface handler");
            return null;
        }
        try {
            return mWificondScanner.getScanResults();
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to get native scan results");
            return null;
        }
    }

    private static int getScanType(int scanType) {
        if (scanType == 0) {
            return 0;
        }
        if (scanType == 1) {
            return 1;
        }
        if (scanType == 2) {
            return 2;
        }
        throw new IllegalArgumentException("Invalid scan type " + scanType);
    }

    public boolean scan(String ifaceName, int scanType, Set<Integer> freqs, List<String> hiddenNetworkSSIDs) {
        IWifiScannerImpl scannerImpl = getScannerImpl(ifaceName);
        if (scannerImpl == null) {
            Log.e(TAG, "No valid wificond scanner interface handler");
            return false;
        }
        SingleScanSettings settings = new SingleScanSettings();
        try {
            settings.scanType = getScanType(scanType);
            settings.channelSettings = new ArrayList<>();
            settings.hiddenNetworks = new ArrayList<>();
            if (freqs != null) {
                for (Integer freq : freqs) {
                    ChannelSettings channel = new ChannelSettings();
                    channel.frequency = freq.intValue();
                    settings.channelSettings.add(channel);
                }
            }
            if (hiddenNetworkSSIDs != null) {
                for (String ssid : hiddenNetworkSSIDs) {
                    HiddenNetwork network = new HiddenNetwork();
                    try {
                        network.ssid = NativeUtil.byteArrayFromArrayList(NativeUtil.decodeSsid(ssid));
                        if (!settings.hiddenNetworks.contains(network)) {
                            settings.hiddenNetworks.add(network);
                        }
                    } catch (IllegalArgumentException e) {
                        Log.e(TAG, "Illegal argument " + ssid, e);
                    }
                }
            }
            try {
                return scannerImpl.scan(settings);
            } catch (RemoteException e2) {
                Log.e(TAG, "Failed to request scan due to remote exception");
                return false;
            }
        } catch (IllegalArgumentException e3) {
            Log.e(TAG, "Invalid scan type ", e3);
            return false;
        }
    }

    public int getMaxNumScanSsids(String ifaceName) {
        IWifiScannerImpl mWificondScanner = getScannerImpl(ifaceName);
        if (mWificondScanner == null) {
            Log.e(TAG, "No valid wificond scanner interface handler, getMaxNumScanSsids");
            return 0;
        }
        try {
            return mWificondScanner.getMaxNumScanSsids();
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to getMaxNumScanSsids due to remote exception");
            return 0;
        }
    }

    public void disableRandomMac(String ifaceName) {
        IWifiScannerImpl mWificondScanner = getScannerImpl(ifaceName);
        if (mWificondScanner == null) {
            Log.e(TAG, "No valid wificond scanner interface handler, disableRandomMac");
        }
        try {
            mWificondScanner.disableRandomMac();
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to request disable random mac due to remote exception");
        }
    }

    public boolean startPnoScan(String ifaceName, WifiNative.PnoSettings pnoSettings) {
        IWifiScannerImpl scannerImpl = getScannerImpl(ifaceName);
        if (scannerImpl == null) {
            Log.e(TAG, "No valid wificond scanner interface handler");
            return false;
        }
        PnoSettings settings = new PnoSettings();
        settings.pnoNetworks = new ArrayList<>();
        settings.intervalMs = pnoSettings.periodInMs;
        settings.min2gRssi = pnoSettings.min24GHzRssi;
        settings.min5gRssi = pnoSettings.min5GHzRssi;
        if (pnoSettings.networkList != null) {
            WifiNative.PnoNetwork[] pnoNetworkArr = pnoSettings.networkList;
            for (WifiNative.PnoNetwork network : pnoNetworkArr) {
                PnoNetwork condNetwork = new PnoNetwork();
                boolean z = true;
                if ((network.flags & 1) == 0) {
                    z = false;
                }
                condNetwork.isHidden = z;
                try {
                    condNetwork.ssid = NativeUtil.byteArrayFromArrayList(NativeUtil.decodeSsid(network.ssid));
                    condNetwork.frequencies = network.frequencies;
                    settings.pnoNetworks.add(condNetwork);
                } catch (IllegalArgumentException e) {
                    Log.e(TAG, "Illegal argument " + network.ssid, e);
                }
            }
        }
        try {
            boolean success = scannerImpl.startPnoScan(settings);
            this.mWifiInjector.getWifiMetrics().incrementPnoScanStartAttempCount();
            if (!success) {
                this.mWifiInjector.getWifiMetrics().incrementPnoScanFailedCount();
            }
            return success;
        } catch (RemoteException e2) {
            Log.e(TAG, "Failed to start pno scan due to remote exception");
            return false;
        }
    }

    public boolean stopPnoScan(String ifaceName) {
        IWifiScannerImpl scannerImpl = getScannerImpl(ifaceName);
        if (scannerImpl == null) {
            Log.e(TAG, "No valid wificond scanner interface handler");
            return false;
        }
        try {
            return scannerImpl.stopPnoScan();
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to stop pno scan due to remote exception");
            return false;
        }
    }

    public void abortScan(String ifaceName) {
        IWifiScannerImpl scannerImpl = getScannerImpl(ifaceName);
        if (scannerImpl == null) {
            Log.e(TAG, "No valid wificond scanner interface handler");
            return;
        }
        try {
            scannerImpl.abortScan();
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to request abortScan due to remote exception");
        }
    }

    public int[] getChannelsForBand(int band) {
        IWificond iWificond = this.mWificond;
        if (iWificond == null) {
            Log.e(TAG, "No valid wificond scanner interface handler");
            return null;
        } else if (band == 1) {
            return iWificond.getAvailable2gChannels();
        } else {
            if (band == 2) {
                return iWificond.getAvailable5gNonDFSChannels();
            }
            if (band == 4) {
                try {
                    return iWificond.getAvailableDFSChannels();
                } catch (RemoteException e) {
                    Log.e(TAG, "Failed to request getChannelsForBand due to remote exception");
                    return null;
                }
            } else {
                throw new IllegalArgumentException("unsupported band " + band);
            }
        }
    }

    private IApInterface getApInterface(String ifaceName) {
        return this.mApInterfaces.get(ifaceName);
    }

    public boolean registerApListener(String ifaceName, WifiNative.SoftApListener listener) {
        IApInterface iface = getApInterface(ifaceName);
        if (iface == null) {
            Log.e(TAG, "No valid ap interface handler");
            return false;
        }
        try {
            IApInterfaceEventCallback callback = new ApInterfaceEventCallback(listener);
            this.mApInterfaceListeners.put(ifaceName, callback);
            if (iface.registerCallback(callback)) {
                return true;
            }
            Log.e(TAG, "Failed to register ap callback.");
            return false;
        } catch (RemoteException e) {
            Log.e(TAG, "Exception in registering AP callback: " + e);
            return false;
        }
    }

    public void sendMgmtFrame(String ifaceName, byte[] frame, WifiNative.SendMgmtFrameCallback callback, int mcs) {
        if (callback == null) {
            Log.e(TAG, "callback cannot be null!");
        } else if (frame == null) {
            Log.e(TAG, "frame cannot be null!");
            callback.onFailure(1);
        } else {
            IClientInterface clientInterface = getClientInterface(ifaceName);
            if (clientInterface == null) {
                Log.e(TAG, "No valid wificond client interface handler");
                callback.onFailure(1);
            } else if (!this.mSendMgmtFrameInProgress.compareAndSet(false, true)) {
                Log.e(TAG, "An existing management frame transmission is in progress!");
                callback.onFailure(5);
            } else {
                SendMgmtFrameEvent sendMgmtFrameEvent = new SendMgmtFrameEvent(callback);
                try {
                    clientInterface.SendMgmtFrame(frame, sendMgmtFrameEvent, mcs);
                } catch (RemoteException e) {
                    Log.e(TAG, "Exception while starting link probe: " + e);
                    sendMgmtFrameEvent.OnFailure(1);
                }
            }
        }
    }

    private void clearState() {
        this.mClientInterfaces.clear();
        this.mWificondScanners.clear();
        this.mPnoScanEventHandlers.clear();
        this.mScanEventHandlers.clear();
        this.mApInterfaces.clear();
        this.mApInterfaceListeners.clear();
        this.mSendMgmtFrameInProgress.set(false);
    }

    private boolean isEnhancedOpenSupported() {
        if (this.mIsEnhancedOpenSupportedInitialized) {
            return this.mIsEnhancedOpenSupported;
        }
        boolean z = false;
        if (this.mWifiNative == null) {
            this.mWifiNative = this.mWifiInjector.getWifiNative();
            if (this.mWifiNative == null) {
                return false;
            }
        }
        String iface = this.mWifiNative.getClientInterfaceName();
        if (iface == null) {
            return false;
        }
        this.mIsEnhancedOpenSupportedInitialized = true;
        if ((this.mWifiNative.getSupportedFeatureSet(iface) & 536870912) != 0) {
            z = true;
        }
        this.mIsEnhancedOpenSupported = z;
        return this.mIsEnhancedOpenSupported;
    }
}
