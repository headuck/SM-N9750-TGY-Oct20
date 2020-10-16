package com.android.server.wifi;

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.admin.DevicePolicyManagerInternal;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ParceledListSlice;
import android.database.ContentObserver;
import android.hardware.wifi.supplicant.V1_0.ISupplicantStaIfaceCallback;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.DhcpResults;
import android.net.INetd;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.NetworkUtils;
import android.net.Uri;
import android.net.wifi.IDppCallback;
import android.net.wifi.INetworkRequestMatchCallback;
import android.net.wifi.IOnWifiUsabilityStatsListener;
import android.net.wifi.ISoftApCallback;
import android.net.wifi.ITrafficStateCallback;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiActivityEnergyInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSuggestion;
import android.net.wifi.WifiScanner;
import android.net.wifi.WifiSsid;
import android.net.wifi.hotspot2.IProvisioningCallback;
import android.net.wifi.hotspot2.OsuProvider;
import android.net.wifi.hotspot2.PasspointConfiguration;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.os.FactoryTest;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.PersistableBundle;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.os.ServiceManager;
import android.os.ShellCallback;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.WorkSource;
import android.provider.Settings;
import android.sec.enterprise.EnterpriseDeviceManager;
import android.telephony.CarrierConfigManager;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.LocalLog;
import android.util.Log;
import android.util.MutableInt;
import android.util.Slog;
import android.util.SparseArray;
import android.widget.Toast;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.os.PowerProfile;
import com.android.internal.util.AsyncChannel;
import com.android.server.wifi.LocalOnlyHotspotRequestInfo;
import com.android.server.wifi.WifiConnectivityMonitor;
import com.android.server.wifi.WifiServiceImpl;
import com.android.server.wifi.hotspot2.PasspointManager;
import com.android.server.wifi.hotspot2.PasspointProvider;
import com.android.server.wifi.iwc.IWCEventManager;
import com.android.server.wifi.util.ExternalCallbackTracker;
import com.android.server.wifi.util.GeneralUtil;
import com.android.server.wifi.util.TelephonyUtil;
import com.android.server.wifi.util.WifiHandler;
import com.android.server.wifi.util.WifiPermissionsUtil;
import com.samsung.android.feature.SemCscFeature;
import com.samsung.android.feature.SemFloatingFeature;
import com.samsung.android.net.wifi.ISemWifiApSmartCallback;
import com.samsung.android.net.wifi.ISharedPasswordCallback;
import com.samsung.android.net.wifi.OpBrandingLoader;
import com.samsung.android.net.wifi.SemWifiApBleScanResult;
import com.samsung.android.server.wifi.AutoWifiController;
import com.samsung.android.server.wifi.CscParser;
import com.samsung.android.server.wifi.HotspotMobileDataLimit;
import com.samsung.android.server.wifi.SemWifiFrameworkUxUtils;
import com.samsung.android.server.wifi.SwitchBoardService;
import com.samsung.android.server.wifi.WifiControlHistoryProvider;
import com.samsung.android.server.wifi.WifiDefaultApController;
import com.samsung.android.server.wifi.WifiDevicePolicyManager;
import com.samsung.android.server.wifi.WifiEnableWarningPolicy;
import com.samsung.android.server.wifi.WifiGuiderFeatureController;
import com.samsung.android.server.wifi.WifiGuiderManagementService;
import com.samsung.android.server.wifi.WifiGuiderPackageMonitor;
import com.samsung.android.server.wifi.WifiMobileDeviceManager;
import com.samsung.android.server.wifi.WifiRoamingAssistant;
import com.samsung.android.server.wifi.bigdata.WifiBigDataLogManager;
import com.samsung.android.server.wifi.bigdata.WifiChipInfo;
import com.samsung.android.server.wifi.dqa.ReportIdKey;
import com.samsung.android.server.wifi.dqa.ReportUtil;
import com.samsung.android.server.wifi.hotspot2.PasspointDefaultProvider;
import com.samsung.android.server.wifi.mobilewips.framework.MobileWipsFrameworkService;
import com.samsung.android.server.wifi.mobilewips.framework.MobileWipsScanResult;
import com.samsung.android.server.wifi.share.SemWifiProfileAndQoSProvider;
import com.samsung.android.server.wifi.softap.DhcpPacket;
import com.samsung.android.server.wifi.softap.SemSoftapConfig;
import com.samsung.android.server.wifi.softap.SemWifiApBroadcastReceiver;
import com.samsung.android.server.wifi.softap.SemWifiApClientInfo;
import com.samsung.android.server.wifi.softap.SemWifiApTrafficPoller;
import com.samsung.android.server.wifi.softap.smarttethering.SemWifiApSmartBleScanner;
import com.samsung.android.server.wifi.softap.smarttethering.SemWifiApSmartClient;
import com.samsung.android.server.wifi.softap.smarttethering.SemWifiApSmartD2DClient;
import com.samsung.android.server.wifi.softap.smarttethering.SemWifiApSmartD2DGattClient;
import com.samsung.android.server.wifi.softap.smarttethering.SemWifiApSmartD2DMHS;
import com.samsung.android.server.wifi.softap.smarttethering.SemWifiApSmartGattClient;
import com.samsung.android.server.wifi.softap.smarttethering.SemWifiApSmartGattServer;
import com.samsung.android.server.wifi.softap.smarttethering.SemWifiApSmartMHS;
import com.samsung.android.server.wifi.softap.smarttethering.SemWifiApSmartUtil;
import com.samsung.android.server.wifi.wlansniffer.WlanSnifferController;
import com.sec.android.app.CscFeatureTagCOMMON;
import com.sec.android.app.CscFeatureTagSetting;
import com.sec.android.app.CscFeatureTagWifi;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.ksoap2.SoapEnvelope;

public class WifiServiceImpl extends BaseWifiService {
    private static final int BACKGROUND_IMPORTANCE_CUTOFF = 125;
    public static final String CONFIGOPBRANDINGFORMOBILEAP = SemCscFeature.getInstance().getString(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_CONFIGOPBRANDINGFORMOBILEAP, "ALL");
    private static final boolean CSC_DISABLE_EMERGENCYCALLBACK_TRANSITION = SemCscFeature.getInstance().getBoolean(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_DISABLEEMERGENCYCALLBACKTRANSITION);
    private static final boolean CSC_SEND_DHCP_RELEASE = SemCscFeature.getInstance().getBoolean(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_SENDSIGNALDURINGPOWEROFF);
    private static final boolean DBG = true;
    private static final long DEFAULT_SCAN_BACKGROUND_THROTTLE_INTERVAL_MS = 1800000;
    private static final String DESKTOP_SYSTEM_UI_PACKAGE_NAME = "com.samsung.desktopsystemui";
    private static final boolean MHSDBG = ("eng".equals(Build.TYPE) || Debug.semIsProductDev());
    private static final int MHS_SAR_BACKOFF_5G_DISABLED = 4;
    private static final int MHS_SAR_BACKOFF_5G_ENABLED = 3;
    private static final int NUM_SOFT_AP_CALLBACKS_WARN_LIMIT = 10;
    private static final int NUM_SOFT_AP_CALLBACKS_WTF_LIMIT = 20;
    private static final String P2P_PACKAGE_NAME = "com.android.server.wifi.p2p";
    private static final int RUN_WITH_SCISSORS_TIMEOUT_MILLIS = 4000;
    static final int SECURITY_EAP = 3;
    static final int SECURITY_NONE = 0;
    static final int SECURITY_PSK = 2;
    static final int SECURITY_WEP = 1;
    private static final String SETTINGS_PACKAGE_NAME = "com.android.settings";
    public static final boolean SUPPORTMOBILEAPENHANCED = true;
    public static final boolean SUPPORTMOBILEAPENHANCED_D2D = "d2d".equals("d2d");
    public static final boolean SUPPORTMOBILEAPENHANCED_LITE = "d2d".equals("lite");
    public static final boolean SUPPORTMOBILEAPENHANCED_WIFI_ONLY_LITE = "d2d".equals("wifi_only_lite");
    private static final String SYSTEM_UI_PACKAGE_NAME = "com.android.systemui";
    private static final String TAG = "WifiService";
    private static final boolean VDBG = false;
    private static final boolean WIFI_STOP_SCAN_FOR_ETWS = SemCscFeature.getInstance().getBoolean(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_STOPSCANFORETWS);
    private static Set<Integer> mAllFreqArray = new HashSet();
    private static SparseArray<Integer> mFreq2ChannelNum = new SparseArray<>();
    private static int[] mFreqArr = {2412, 2417, 2422, 2427, 2432, 2437, 2442, 2447, 2452, 2457, 2462, 2467, 2472, 2484, 5170, 5180, 5190, 5200, 5210, 5220, 5230, 5240, 5260, 5280, 5300, 5320, 5500, 5520, 5540, 5560, 5580, 5600, 5620, 5640, 5660, 5680, 5700, 5745, 5765, 5785, 5805, 5825};
    private static final String mHotspotActionForSimStatus = SemCscFeature.getInstance().getString(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_CONFIGHOTSPOTACTIONFORSIMSTATUS);
    public static final String mIndoorChannelCountry = "GH,GG,GR,GL,ZA,NL,NO,NF,NZ,NU,KR,DK,DE,LV,RO,LU,LY,LT,LI,MK,IM,MC,MA,ME,MV,MT,BH,BB,VA,VE,VN,BE,BA,BG,BR,SA,SM,PM,RS,SE,CH,ES,SK,SI,AE,AR,IS,IE,AL,EE,GB,IO,OM,AU,AT,UY,UA,IL,EG,IT,IN,JP,JE,GE,CN,GI,CZ,CL,CA,CC,CO,KW,CK,HR,CY,TH,TR,TK,PA,FO,PT,PL,FR,TF,PF,FJ,FI,PN,HM,HU,HK";
    private static List<String> mMHSChannelHistoryLogs = new ArrayList();
    private static final OpBrandingLoader.Vendor mOpBranding = OpBrandingLoader.getInstance().getOpBranding();
    private int isProvisionSuccessful = 0;
    final ActiveModeWarden mActiveModeWarden;
    private final ActivityManager mActivityManager;
    private final AppOpsManager mAppOps;
    private AsyncChannelExternalClientHandler mAsyncChannelExternalClientHandler;
    private AutoWifiController mAutoWifiController;
    private boolean mBlockScanFromOthers = false;
    private String mCSCRegion = "";
    private boolean mChameleonEnabled = false;
    final ClientModeImpl mClientModeImpl;
    @VisibleForTesting
    AsyncChannel mClientModeImplChannel;
    ClientModeImplHandler mClientModeImplHandler;
    private final Clock mClock;
    private final Context mContext;
    Map<String, String> mCountryChannel = new HashMap(5);
    Map<String, String> mCountryChannelList = new HashMap();
    private final WifiCountryCode mCountryCode;
    private String mCountryIso = "";
    private WifiDefaultApController mDefaultApController;
    private final WifiDevicePolicyManager mDevicePolicyManager;
    private int mDomRoamMaxUser = 10;
    private final DppManager mDppManager;
    private final FrameworkFacade mFacade;
    private final FrameworkFacade mFrameworkFacade;
    private int mGsmMaxUser = 1;
    private List<String> mHistoricalDumpLogs = new ArrayList();
    private HotspotMobileDataLimit mHotspotMobileDataLimit;
    private Messenger mIWCMessenger = null;
    private IWCMonitor mIWCMonitor;
    @GuardedBy({"mLocalOnlyHotspotRequests"})
    private final ConcurrentHashMap<String, Integer> mIfaceIpModes;
    boolean mInIdleMode;
    private final String mIndoorChannelFilePath = "/vendor/etc/wifi/indoorchannel.info";
    private int mIntRoamMaxUser = 10;
    @GuardedBy({"mLocalOnlyHotspotRequests"})
    private WifiConfiguration mLocalOnlyHotspotConfig = null;
    @GuardedBy({"mLocalOnlyHotspotRequests"})
    private final HashMap<Integer, LocalOnlyHotspotRequestInfo> mLocalOnlyHotspotRequests;
    private WifiLog mLog;
    private int mMaxUser = WifiApConfigStore.MAX_CLIENT;
    private MobileWipsFrameworkService mMobileWipsFrameworkService;
    private INetd mNetdService = null;
    private final PasspointDefaultProvider mPasspointDefaultProvider;
    private final PasspointManager mPasspointManager;
    private PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        /* class com.android.server.wifi.WifiServiceImpl.C047514 */

        public void onDataConnectionStateChanged(int state, int networkType) {
            int maxClientNum;
            Slog.d(WifiServiceImpl.TAG, "onDataConnectionStateChanged: state -" + String.valueOf(state) + ", networkType - " + TelephonyManager.getNetworkTypeName(networkType));
            WifiServiceImpl.this.setMaxClientVzwBasedOnNetworkType(networkType);
            if ("SPRINT".equals(WifiServiceImpl.CONFIGOPBRANDINGFORMOBILEAP)) {
                int wifiApState = WifiServiceImpl.this.getWifiApEnabledState();
                if (wifiApState == 12 || wifiApState == 13) {
                    ContentResolver cr = WifiServiceImpl.this.mContext.getContentResolver();
                    int i = WifiApConfigStore.MAX_CLIENT;
                    if (1 == networkType || 2 == networkType || 16 == networkType || networkType == 0) {
                        maxClientNum = Settings.System.getInt(cr, "chameleon_gsmmaxuser", 1);
                    } else {
                        try {
                            maxClientNum = Settings.System.getInt(cr, "chameleon_maxuser");
                        } catch (Settings.SettingNotFoundException e) {
                            maxClientNum = WifiApConfigStore.MAX_CLIENT;
                        }
                    }
                    WifiConfiguration mWifiConfig = WifiServiceImpl.this.getWifiApConfiguration();
                    if (mWifiConfig != null) {
                        int maxClientNum2 = maxClientNum < mWifiConfig.maxclient ? maxClientNum : mWifiConfig.maxclient;
                        Log.i(WifiServiceImpl.TAG, "maxClientNum = " + maxClientNum2);
                        Message msg = new Message();
                        msg.what = 14;
                        Bundle b = new Bundle();
                        b.putInt("maxClient", maxClientNum2);
                        msg.obj = b;
                        WifiServiceImpl.this.callSECApi(msg);
                    }
                }
            }
        }

        /* JADX WARNING: Code restructure failed: missing block: B:5:0x002c, code lost:
            if (r0 != 2) goto L_0x005d;
         */
        public void onServiceStateChanged(ServiceState serviceState) {
            super.onServiceStateChanged(serviceState);
            Slog.d(WifiServiceImpl.TAG, "onServiceStateChanged : " + serviceState.getState());
            WifiServiceImpl.this.checkAndSarBackOffFor5G(serviceState);
            int state = serviceState.getState();
            if (state != 0) {
                if (state == 1) {
                    if (!WifiServiceImpl.this.mSettingsStore.isAirplaneModeOn()) {
                        Slog.d(WifiServiceImpl.TAG, "[FCC]STATE_OUT_OF_SERVICE & Airplane off, Enable setFccChannel()");
                        WifiServiceImpl.this.mClientModeImpl.syncSetFccChannel(true);
                    }
                }
                WifiServiceImpl.this.setMaxClientVzwBasedOnNetworkType(serviceState.getDataNetworkType());
            }
            if (!WifiServiceImpl.this.mSettingsStore.isAirplaneModeOn()) {
                Slog.d(WifiServiceImpl.TAG, "[FCC]NOT IN STATE_OUT_OF_SERVICE & Airplane off, Disable setFccChannel()");
                WifiServiceImpl.this.mClientModeImpl.syncSetFccChannel(false);
            }
            WifiServiceImpl.this.setMaxClientVzwBasedOnNetworkType(serviceState.getDataNetworkType());
        }
    };
    private final PowerManager mPowerManager;
    PowerProfile mPowerProfile;
    private int mPrevNrState = 0;
    private int mQoSTestCounter;
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        /* class com.android.server.wifi.WifiServiceImpl.C047211 */

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals("android.intent.action.USER_REMOVED")) {
                WifiServiceImpl.this.mClientModeImpl.removeUserConfigs(intent.getIntExtra("android.intent.extra.user_handle", 0));
            } else if (action.equals("android.bluetooth.adapter.action.CONNECTION_STATE_CHANGED")) {
                WifiServiceImpl.this.mClientModeImpl.sendBluetoothAdapterStateChange(intent.getIntExtra("android.bluetooth.adapter.extra.CONNECTION_STATE", 0));
            } else if (action.equals("android.intent.action.EMERGENCY_CALLBACK_MODE_CHANGED")) {
                boolean emergencyMode = intent.getBooleanExtra("phoneinECMState", false);
                WifiServiceImpl.this.mWifiController.sendMessage(155649, emergencyMode ? 1 : 0, 0);
                WifiServiceImpl.this.mClientModeImpl.report(9, ReportUtil.getReportDataForChangeState(emergencyMode));
            } else if (action.equals("android.intent.action.EMERGENCY_CALL_STATE_CHANGED")) {
                boolean inCall = intent.getBooleanExtra("phoneInEmergencyCall", false);
                WifiServiceImpl.this.mWifiController.sendMessage(155662, inCall ? 1 : 0, 0);
                WifiServiceImpl.this.mClientModeImpl.report(9, ReportUtil.getReportDataForChangeState(inCall));
            } else if (action.equals("android.os.action.DEVICE_IDLE_MODE_CHANGED")) {
                WifiServiceImpl.this.handleIdleModeChanged();
            } else if (action.equals("android.provider.Telephony.SMS_CB_WIFI_RECEIVED")) {
                WifiServiceImpl.this.mBlockScanFromOthers = true;
                Log.e(WifiServiceImpl.TAG, "received broadcast ETWS, Scanning will be blocked");
            }
        }
    };
    private final ConcurrentHashMap<Integer, ISemWifiApSmartCallback> mRegisteredSemWifiApSmartCallbacks;
    private final ExternalCallbackTracker<ISoftApCallback> mRegisteredSoftApCallbacks;
    boolean mScanPending;
    final ScanRequestProxy mScanRequestProxy;
    private SemWifiProfileAndQoSProvider mSemQoSProfileShareProvider;
    private SemWifiApBroadcastReceiver mSemWifiApBroadcastReceiver;
    private SemWifiApClientInfo mSemWifiApClientInfo;
    private SemWifiApSmartBleScanner mSemWifiApSmartBleScanner = null;
    private SemWifiApSmartClient mSemWifiApSmartClient = null;
    private SemWifiApSmartD2DClient mSemWifiApSmartD2DClient = null;
    private SemWifiApSmartD2DGattClient mSemWifiApSmartD2DGattClient = null;
    private SemWifiApSmartD2DMHS mSemWifiApSmartD2DMHS = null;
    private SemWifiApSmartGattClient mSemWifiApSmartGattClient = null;
    private SemWifiApSmartGattServer mSemWifiApSmartGattServer = null;
    private LocalLog mSemWifiApSmartLocalLog = null;
    private SemWifiApSmartMHS mSemWifiApSmartMHS = null;
    private String mSemWifiApSmartMhsMac = null;
    private int mSemWifiApSmartState = 0;
    private SemWifiApSmartUtil mSemWifiApSmartUtil = null;
    private SemWifiApTrafficPoller mSemWifiApTrafficPoller = null;
    final WifiSettingsStore mSettingsStore;
    private int mSoftApNumClients = 0;
    private int mSoftApState = 11;
    private String mSsid = "";
    private int mSupportedFeaturesOfHal = -1;
    private SwitchBoardService mSwitchBoardService;
    private TelephonyManager mTelephonyManager;
    private int mTetheredData = 0;
    private final UserManager mUserManager;
    private boolean mVerboseLoggingEnabled = false;
    private WifiApConfigStore mWifiApConfigStore;
    private int mWifiApState = 11;
    private final WifiBackupRestore mWifiBackupRestore;
    private WifiConnectivityMonitor mWifiConnectivityMonitor;
    private WifiController mWifiController;
    private WifiEnableWarningPolicy mWifiEnableWarningPolicy;
    private WifiGuiderManagementService mWifiGuiderManagementService;
    private WifiGuiderPackageMonitor mWifiGuiderPackageMonitor;
    private final WifiInjector mWifiInjector;
    private final WifiLockManager mWifiLockManager;
    private final WifiMetrics mWifiMetrics;
    private final WifiMulticastLockManager mWifiMulticastLockManager;
    private WifiNative mWifiNative;
    private final WifiNetworkSuggestionsManager mWifiNetworkSuggestionsManager;
    private WifiPermissionsUtil mWifiPermissionsUtil;
    private WifiRoamingAssistant mWifiRoamingAssistant;
    private boolean mWifiSharingLitePopup = false;
    private WifiTrafficPoller mWifiTrafficPoller;
    private WlanSnifferController mWlanSnifferController;
    private int scanRequestCounter = 0;
    private boolean semIsShutdown = true;
    private boolean semIsTestMode = false;

    static {
        int i = 0;
        while (true) {
            int[] iArr = mFreqArr;
            if (i < iArr.length) {
                mAllFreqArray.add(Integer.valueOf(iArr[i]));
                i++;
            } else {
                mFreq2ChannelNum.append(2412, 1);
                mFreq2ChannelNum.append(2417, 2);
                mFreq2ChannelNum.append(2422, 3);
                mFreq2ChannelNum.append(2427, 4);
                mFreq2ChannelNum.append(2432, 5);
                mFreq2ChannelNum.append(2437, 6);
                mFreq2ChannelNum.append(2442, 7);
                mFreq2ChannelNum.append(2447, 8);
                mFreq2ChannelNum.append(2452, 9);
                mFreq2ChannelNum.append(2457, 10);
                mFreq2ChannelNum.append(2462, 11);
                mFreq2ChannelNum.append(2467, 12);
                mFreq2ChannelNum.append(2472, 13);
                mFreq2ChannelNum.append(2484, 14);
                mFreq2ChannelNum.append(5170, 34);
                mFreq2ChannelNum.append(5180, 36);
                mFreq2ChannelNum.append(5190, 38);
                mFreq2ChannelNum.append(5200, 40);
                mFreq2ChannelNum.append(5210, 42);
                mFreq2ChannelNum.append(5220, 44);
                mFreq2ChannelNum.append(5230, 46);
                mFreq2ChannelNum.append(5240, 48);
                mFreq2ChannelNum.append(5260, 52);
                mFreq2ChannelNum.append(5280, 56);
                mFreq2ChannelNum.append(5300, 60);
                mFreq2ChannelNum.append(5320, 64);
                mFreq2ChannelNum.append(5500, 100);
                mFreq2ChannelNum.append(5520, 104);
                mFreq2ChannelNum.append(5540, 108);
                mFreq2ChannelNum.append(5560, Integer.valueOf((int) ISupplicantStaIfaceCallback.StatusCode.FILS_AUTHENTICATION_FAILURE));
                mFreq2ChannelNum.append(5580, 116);
                mFreq2ChannelNum.append(5600, Integer.valueOf((int) SoapEnvelope.VER12));
                mFreq2ChannelNum.append(5620, 124);
                mFreq2ChannelNum.append(5640, 128);
                mFreq2ChannelNum.append(5660, 132);
                mFreq2ChannelNum.append(5680, 136);
                mFreq2ChannelNum.append(5700, 140);
                mFreq2ChannelNum.append(5720, 144);
                mFreq2ChannelNum.append(5745, 149);
                mFreq2ChannelNum.append(5765, 153);
                mFreq2ChannelNum.append(5785, 157);
                mFreq2ChannelNum.append(5805, 161);
                mFreq2ChannelNum.append(5825, 165);
                return;
            }
        }
    }

    public final class LocalOnlyRequestorCallback implements LocalOnlyHotspotRequestInfo.RequestingApplicationDeathCallback {
        public LocalOnlyRequestorCallback() {
        }

        @Override // com.android.server.wifi.LocalOnlyHotspotRequestInfo.RequestingApplicationDeathCallback
        public void onLocalOnlyHotspotRequestorDeath(LocalOnlyHotspotRequestInfo requestor) {
            WifiServiceImpl.this.unregisterCallingAppAndStopLocalOnlyHotspot(requestor);
        }
    }

    /* access modifiers changed from: private */
    public class AsyncChannelExternalClientHandler extends WifiHandler {
        AsyncChannelExternalClientHandler(String tag, Looper looper) {
            super(tag, looper);
        }

        @Override // com.android.server.wifi.util.WifiHandler
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case 69633:
                    WifiServiceImpl.this.mFrameworkFacade.makeWifiAsyncChannel(WifiServiceImpl.TAG).connect(WifiServiceImpl.this.mContext, this, msg.replyTo);
                    return;
                case 151553:
                    if (checkPrivilegedPermissionsAndReplyIfNotAuthorized(msg, 151554)) {
                        WifiConfiguration config = (WifiConfiguration) msg.obj;
                        int networkId = msg.arg1;
                        String nameForUid = WifiServiceImpl.this.mContext.getPackageManager().getNameForUid(msg.sendingUid);
                        Slog.d(WifiServiceImpl.TAG, "CONNECT  nid=" + Integer.toString(networkId) + " config=" + config + " uid=" + msg.sendingUid + " name=" + nameForUid);
                        boolean hasPassword = false;
                        if (config != null) {
                            if (config.preSharedKey != null) {
                                hasPassword = config.preSharedKey.length() > 8;
                            } else if (!(config.wepKeys == null || config.wepKeys[0] == null)) {
                                hasPassword = config.wepKeys[0].length() >= 4;
                            }
                            WifiServiceImpl.this.mClientModeImpl.sendMessage(Message.obtain(msg));
                        } else if (config != null || networkId == -1) {
                            Slog.e(WifiServiceImpl.TAG, "AsyncChannelExternalClientHandler.handleMessage ignoring invalid msg=" + msg);
                            replyFailed(msg, 151554, 8);
                        } else {
                            WifiServiceImpl.this.mClientModeImpl.sendMessage(Message.obtain(msg));
                        }
                        if (config == null && WifiServiceImpl.this.mClientModeImplChannel != null) {
                            config = WifiServiceImpl.this.mClientModeImpl.syncGetSpecificNetwork(WifiServiceImpl.this.mClientModeImplChannel, networkId);
                        }
                        if (config != null) {
                            WifiServiceImpl.this.mClientModeImpl.report(103, ReportUtil.getReportDataForWifiManagerConnectApi(true, config.networkId, config.SSID, "connect", nameForUid, hasPassword));
                            return;
                        }
                        return;
                    }
                    return;
                case 151556:
                    if (checkPrivilegedPermissionsAndReplyIfNotAuthorized(msg, 151557)) {
                        if (WifiServiceImpl.this.mAutoWifiController != null) {
                            WifiServiceImpl.this.mAutoWifiController.forgetNetwork(msg.arg1);
                        }
                        WifiServiceImpl.this.mClientModeImpl.sendMessage(Message.obtain(msg));
                        if (WifiServiceImpl.this.mWifiConnectivityMonitor != null) {
                            WifiServiceImpl.this.mWifiConnectivityMonitor.networkRemoved(msg.arg1);
                        }
                        WifiServiceImpl.this.mClientModeImpl.report(102, ReportUtil.getReportDataForWifiManagerApi(msg.arg1, "forget", WifiServiceImpl.this.mContext.getPackageManager().getNameForUid(msg.sendingUid), "unknown"));
                        return;
                    }
                    return;
                case 151559:
                    if (checkPrivilegedPermissionsAndReplyIfNotAuthorized(msg, 151560)) {
                        WifiConfiguration config2 = (WifiConfiguration) msg.obj;
                        int networkId2 = msg.arg1;
                        Slog.d(WifiServiceImpl.TAG, "SAVE nid=" + Integer.toString(networkId2) + " config=" + config2 + " uid=" + msg.sendingUid + " name=" + WifiServiceImpl.this.mContext.getPackageManager().getNameForUid(msg.sendingUid));
                        if (config2 != null) {
                            boolean hasPassword2 = false;
                            if (config2.preSharedKey != null) {
                                hasPassword2 = config2.preSharedKey.length() > 8;
                            } else if (!(config2.wepKeys == null || config2.wepKeys[0] == null)) {
                                hasPassword2 = config2.wepKeys[0].length() >= 4;
                            }
                            WifiServiceImpl.this.mClientModeImpl.sendMessage(Message.obtain(msg));
                            WifiServiceImpl.this.mClientModeImpl.report(105, ReportUtil.getReportDataForWifiManagerAddOrUpdateApi(config2.networkId, hasPassword2, "save", WifiServiceImpl.this.mContext.getPackageManager().getNameForUid(msg.sendingUid), WifiServiceImpl.this.getPackageName(Binder.getCallingPid())));
                            return;
                        }
                        Slog.e(WifiServiceImpl.TAG, "AsyncChannelExternalClientHandler.handleMessage ignoring invalid msg=" + msg);
                        replyFailed(msg, 151560, 8);
                        return;
                    }
                    return;
                case 151569:
                    if (checkPrivilegedPermissionsAndReplyIfNotAuthorized(msg, 151570)) {
                        WifiServiceImpl.this.mClientModeImpl.sendMessage(Message.obtain(msg));
                        WifiServiceImpl.this.mClientModeImpl.report(101, ReportUtil.getReportDataForWifiManagerApi(msg.arg1, "disable", WifiServiceImpl.this.mContext.getPackageManager().getNameForUid(msg.sendingUid), "unknown"));
                        return;
                    }
                    return;
                case 151572:
                    if (checkChangePermissionAndReplyIfNotAuthorized(msg, 151574)) {
                        WifiServiceImpl.this.mClientModeImpl.sendMessage(Message.obtain(msg));
                        return;
                    }
                    return;
                case 151752:
                    if (WifiServiceImpl.this.mIWCMonitor != null) {
                        WifiServiceImpl.this.mIWCMonitor.sendMessage(msg.what, msg.sendingUid, msg.arg1, msg.obj);
                        Log.e(WifiServiceImpl.TAG, "nid: " + msg.arg1 + " uid: " + msg.sendingUid);
                        return;
                    }
                    return;
                case 151753:
                    if (WifiServiceImpl.this.mIWCMonitor != null) {
                        WifiServiceImpl.this.mIWCMonitor.sendMessage(msg.what, msg.sendingUid, msg.arg1, msg.obj);
                        Log.e(WifiServiceImpl.TAG, "nid: " + msg.arg1 + " uid: " + msg.sendingUid);
                        return;
                    }
                    return;
                default:
                    Slog.d(WifiServiceImpl.TAG, "AsyncChannelExternalClientHandler.handleMessage ignoring msg=" + msg);
                    return;
            }
        }

        private boolean checkChangePermissionAndReplyIfNotAuthorized(Message msg, int replyWhat) {
            if (WifiServiceImpl.this.mWifiPermissionsUtil.checkChangePermission(msg.sendingUid)) {
                return true;
            }
            Slog.e(WifiServiceImpl.TAG, "AsyncChannelExternalClientHandler.handleMessage ignoring unauthorized msg=" + msg);
            replyFailed(msg, replyWhat, 9);
            return false;
        }

        private boolean checkPrivilegedPermissionsAndReplyIfNotAuthorized(Message msg, int replyWhat) {
            if (WifiServiceImpl.this.isPrivileged(-1, msg.sendingUid)) {
                return true;
            }
            Slog.e(WifiServiceImpl.TAG, "ClientHandler.handleMessage ignoring unauthorized msg=" + msg);
            replyFailed(msg, replyWhat, 9);
            return false;
        }

        private void replyFailed(Message msg, int what, int why) {
            if (msg.replyTo != null) {
                Message reply = Message.obtain();
                reply.what = what;
                reply.arg1 = why;
                try {
                    msg.replyTo.send(reply);
                } catch (RemoteException e) {
                }
            }
        }
    }

    /* access modifiers changed from: private */
    public class ClientModeImplHandler extends WifiHandler {
        private AsyncChannel mCmiChannel;

        ClientModeImplHandler(String tag, Looper looper, AsyncChannel asyncChannel) {
            super(tag, looper);
            this.mCmiChannel = asyncChannel;
            this.mCmiChannel.connect(WifiServiceImpl.this.mContext, this, WifiServiceImpl.this.mClientModeImpl.getHandler());
        }

        @Override // com.android.server.wifi.util.WifiHandler
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            int i = msg.what;
            if (i != 69632) {
                if (i != 69636) {
                    Slog.d(WifiServiceImpl.TAG, "ClientModeImplHandler.handleMessage ignoring msg=" + msg);
                    return;
                }
                Slog.e(WifiServiceImpl.TAG, "ClientModeImpl channel lost, msg.arg1 =" + msg.arg1);
                WifiServiceImpl wifiServiceImpl = WifiServiceImpl.this;
                wifiServiceImpl.mClientModeImplChannel = null;
                this.mCmiChannel.connect(wifiServiceImpl.mContext, this, WifiServiceImpl.this.mClientModeImpl.getHandler());
            } else if (msg.arg1 == 0) {
                WifiServiceImpl.this.mClientModeImplChannel = this.mCmiChannel;
            } else {
                Slog.e(WifiServiceImpl.TAG, "ClientModeImpl connection failure, error=" + msg.arg1);
                WifiServiceImpl.this.mClientModeImplChannel = null;
            }
        }
    }

    public WifiServiceImpl(Context context, WifiInjector wifiInjector, AsyncChannel asyncChannel) {
        this.mContext = context;
        this.mWifiInjector = wifiInjector;
        this.mClock = wifiInjector.getClock();
        this.mFacade = this.mWifiInjector.getFrameworkFacade();
        this.mWifiMetrics = this.mWifiInjector.getWifiMetrics();
        this.mWifiTrafficPoller = this.mWifiInjector.getWifiTrafficPoller();
        this.mWifiTrafficPoller.setService(this);
        this.mUserManager = this.mWifiInjector.getUserManager();
        this.mCountryCode = this.mWifiInjector.getWifiCountryCode();
        this.mClientModeImpl = this.mWifiInjector.getClientModeImpl();
        this.mActiveModeWarden = this.mWifiInjector.getActiveModeWarden();
        this.mClientModeImpl.enableRssiPolling(true);
        this.mScanRequestProxy = this.mWifiInjector.getScanRequestProxy();
        this.mSettingsStore = this.mWifiInjector.getWifiSettingsStore();
        this.mPowerManager = (PowerManager) this.mContext.getSystemService(PowerManager.class);
        this.mAppOps = (AppOpsManager) this.mContext.getSystemService("appops");
        this.mActivityManager = (ActivityManager) this.mContext.getSystemService("activity");
        this.mWifiLockManager = this.mWifiInjector.getWifiLockManager();
        this.mWifiMulticastLockManager = this.mWifiInjector.getWifiMulticastLockManager();
        HandlerThread wifiServiceHandlerThread = this.mWifiInjector.getWifiServiceHandlerThread();
        this.mAsyncChannelExternalClientHandler = new AsyncChannelExternalClientHandler(TAG, wifiServiceHandlerThread.getLooper());
        this.mClientModeImplHandler = new ClientModeImplHandler(TAG, wifiServiceHandlerThread.getLooper(), asyncChannel);
        this.mWifiController = this.mWifiInjector.getWifiController();
        this.mWifiBackupRestore = this.mWifiInjector.getWifiBackupRestore();
        this.mWifiPermissionsUtil = this.mWifiInjector.getWifiPermissionsUtil();
        this.mLog = this.mWifiInjector.makeLog(TAG);
        this.mFrameworkFacade = wifiInjector.getFrameworkFacade();
        this.mIfaceIpModes = new ConcurrentHashMap<>();
        this.mLocalOnlyHotspotRequests = new HashMap<>();
        enableVerboseLoggingInternal(getVerboseLoggingLevel());
        this.mRegisteredSoftApCallbacks = new ExternalCallbackTracker<>(this.mClientModeImplHandler);
        this.mWifiInjector.getActiveModeWarden().registerSoftApCallback(new SoftApCallbackImpl());
        this.mPowerProfile = this.mWifiInjector.getPowerProfile();
        this.mWifiNative = this.mWifiInjector.getWifiNative();
        this.mWifiNetworkSuggestionsManager = this.mWifiInjector.getWifiNetworkSuggestionsManager();
        this.mDppManager = this.mWifiInjector.getDppManager();
        this.mDefaultApController = WifiDefaultApController.init(this.mContext, this, this.mClientModeImpl, this.mWifiInjector);
        this.mMobileWipsFrameworkService = MobileWipsFrameworkService.init(this.mContext);
        this.mPasspointManager = this.mWifiInjector.getPasspointManager();
        this.mPasspointDefaultProvider = new PasspointDefaultProvider(this.mContext, this);
        this.mWifiGuiderManagementService = new WifiGuiderManagementService(this.mContext, this.mWifiInjector);
        this.mWifiGuiderPackageMonitor = new WifiGuiderPackageMonitor();
        this.mWifiGuiderPackageMonitor.registerApi("android.bluetooth.BluetoothSocket.connect()", ReportIdKey.ID_DHCP_FAIL, 20);
        this.mWifiEnableWarningPolicy = new WifiEnableWarningPolicy(this.mContext);
        this.mWifiRoamingAssistant = WifiRoamingAssistant.init(this.mContext);
        this.mDevicePolicyManager = WifiDevicePolicyManager.init(this.mContext, this, this.mClientModeImpl);
        if (!FactoryTest.isFactoryBinary()) {
            boolean z = SUPPORTMOBILEAPENHANCED_WIFI_ONLY_LITE;
            this.mSemWifiApSmartLocalLog = new LocalLog(ActivityManager.isLowRamDeviceStatic() ? 256 : 1024);
            this.mSemWifiApSmartUtil = new SemWifiApSmartUtil(this.mContext, this.mSemWifiApSmartLocalLog);
            this.mSemWifiApSmartGattServer = new SemWifiApSmartGattServer(this.mContext, this.mSemWifiApSmartUtil, this.mSemWifiApSmartLocalLog);
            this.mWifiInjector.setSemWifiApSmartGattServer(this.mSemWifiApSmartGattServer);
            this.mSemWifiApSmartMHS = new SemWifiApSmartMHS(this.mContext, this.mSemWifiApSmartUtil, this.mDevicePolicyManager, this.mSemWifiApSmartLocalLog);
            this.mWifiInjector.setSemWifiApSmartMHS(this.mSemWifiApSmartMHS);
            this.mSemWifiApSmartClient = new SemWifiApSmartClient(this.mContext, this.mSemWifiApSmartUtil, this.mSemWifiApSmartLocalLog);
            this.mWifiInjector.setSemWifiApSmartClient(this.mSemWifiApSmartClient);
            this.mSemWifiApSmartGattClient = new SemWifiApSmartGattClient(this.mContext, this.mSemWifiApSmartUtil, this.mSemWifiApSmartLocalLog);
            this.mWifiInjector.setSemWifiApSmartGattClient(this.mSemWifiApSmartGattClient);
            this.mSemWifiApSmartGattClient.registerSemWifiApSmartCallback(new SemWifiApSmartCallbackImpl());
            this.mSemWifiApSmartBleScanner = new SemWifiApSmartBleScanner(this.mContext, this.mSemWifiApSmartUtil, this.mSemWifiApSmartLocalLog);
            this.mWifiInjector.setSemWifiApSmartBleScanner(this.mSemWifiApSmartBleScanner);
            if (SUPPORTMOBILEAPENHANCED_D2D || SUPPORTMOBILEAPENHANCED_WIFI_ONLY_LITE) {
                this.mSemWifiApSmartD2DGattClient = new SemWifiApSmartD2DGattClient(this.mContext, this.mSemWifiApSmartUtil, this.mSemWifiApSmartLocalLog);
                this.mSemWifiApSmartD2DMHS = new SemWifiApSmartD2DMHS(this.mContext, this.mSemWifiApSmartUtil, this.mSemWifiApSmartLocalLog);
                this.mSemWifiApSmartD2DClient = new SemWifiApSmartD2DClient(this.mContext, this.mSemWifiApSmartUtil, this.mSemWifiApSmartLocalLog);
                this.mWifiInjector.setSemWifiApSmartD2DMHS(this.mSemWifiApSmartD2DMHS);
                this.mWifiInjector.setSemWifiApSmartD2DClient(this.mSemWifiApSmartD2DClient);
                this.mWifiInjector.setSemWifiApSmartD2DGattClient(this.mSemWifiApSmartD2DGattClient);
            }
        }
        this.mSemWifiApTrafficPoller = new SemWifiApTrafficPoller(this.mContext, this.mWifiNative);
        this.mWifiInjector.setSemWifiApTrafficPoller(this.mSemWifiApTrafficPoller);
        this.mRegisteredSemWifiApSmartCallbacks = new ConcurrentHashMap<>();
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.intent.action.BOOT_COMPLETED");
        filter.setPriority(1000);
        this.mContext.registerReceiver(new BroadcastReceiver() {
            /* class com.android.server.wifi.WifiServiceImpl.C04701 */

            public void onReceive(Context context, Intent intent) {
                Slog.i(WifiServiceImpl.TAG, "android.intent.action.BOOT_COMPLETED");
                if ("SPRINT".equals(WifiServiceImpl.CONFIGOPBRANDINGFORMOBILEAP)) {
                    try {
                        WifiServiceImpl.this.mTetheredData = Settings.Secure.getInt(WifiServiceImpl.this.mContext.getContentResolver(), "chameleon_tethereddata");
                        Slog.d(WifiServiceImpl.TAG, "Boot_completed, mTetheredData = " + WifiServiceImpl.this.mTetheredData);
                    } catch (Settings.SettingNotFoundException e) {
                        Slog.d(WifiServiceImpl.TAG, "Settings.SettingNotFoundException for CHAMELEON_TETHEREDDATA");
                        int wifiApState = WifiServiceImpl.this.getWifiApEnabledState();
                        if (wifiApState == 12 && wifiApState == 13) {
                            WifiServiceImpl.this.stopSoftAp();
                            try {
                                Thread.sleep(600);
                            } catch (InterruptedException e2) {
                                e2.printStackTrace();
                            }
                            WifiServiceImpl.this.mTetheredData = SystemProperties.getInt("persist.sys.tether_data", -1);
                            Settings.Secure.putInt(WifiServiceImpl.this.mContext.getContentResolver(), "chameleon_tethereddata", WifiServiceImpl.this.mTetheredData);
                            return;
                        }
                        WifiServiceImpl.this.mTetheredData = SystemProperties.getInt("persist.sys.tether_data", -1);
                        Settings.Secure.putInt(WifiServiceImpl.this.mContext.getContentResolver(), "chameleon_tethereddata", WifiServiceImpl.this.mTetheredData);
                    }
                }
            }
        }, filter);
        if ("SPRINT".equals(CONFIGOPBRANDINGFORMOBILEAP)) {
            this.mTetheredData = 2;
            this.mContext.registerReceiver(new BroadcastReceiver() {
                /* class com.android.server.wifi.WifiServiceImpl.C04782 */

                public void onReceive(Context context, Intent intent) {
                    WifiServiceImpl.this.mChameleonEnabled = true;
                    String mTempTetheredData = intent.getStringExtra("chameleon_wifi_tetheredData");
                    String mTempSSid = intent.getStringExtra("chameleon_wifi_ssid");
                    if (mTempSSid != null) {
                        Slog.d(WifiServiceImpl.TAG, "[onReceive] CHAMELEON Tethering.SSID : " + mTempSSid);
                        WifiConfiguration wifiConfig = WifiServiceImpl.this.getWifiApConfiguration();
                        wifiConfig.SSID = mTempSSid;
                        WifiServiceImpl.this.setWifiApConfiguration(wifiConfig, WifiServiceImpl.SETTINGS_PACKAGE_NAME);
                    }
                    if (mTempTetheredData != null) {
                        WifiServiceImpl.this.mTetheredData = Integer.parseInt(mTempTetheredData);
                        Slog.d(WifiServiceImpl.TAG, "[onReceive] CHAMELEON mTetheredData : " + WifiServiceImpl.this.mTetheredData);
                    }
                    if (0 != 0) {
                        WifiServiceImpl.this.mMaxUser = Integer.parseInt(null);
                        Slog.d(WifiServiceImpl.TAG, "[onReceive] mMaxUser : " + WifiServiceImpl.this.mMaxUser);
                    }
                    WifiServiceImpl.this.mGsmMaxUser = Integer.parseInt("1");
                    Slog.d(WifiServiceImpl.TAG, "[onReceive] mGsmMaxUser : " + WifiServiceImpl.this.mGsmMaxUser);
                    WifiServiceImpl.this.mDomRoamMaxUser = Integer.parseInt("8");
                    Slog.d(WifiServiceImpl.TAG, "[onReceive] mDomRoamMaxUser : " + WifiServiceImpl.this.mDomRoamMaxUser);
                    WifiServiceImpl.this.mIntRoamMaxUser = Integer.parseInt("8");
                    Slog.d(WifiServiceImpl.TAG, "[onReceive] mIntRoamMaxUser : " + WifiServiceImpl.this.mIntRoamMaxUser);
                    Slog.d(WifiServiceImpl.TAG, "[setValue] mTetheredData = " + WifiServiceImpl.this.mTetheredData + ", mMaxUser = " + WifiServiceImpl.this.mMaxUser + ", mGsmMaxUser = " + WifiServiceImpl.this.mGsmMaxUser + ", mDomRoamMaxUser = " + WifiServiceImpl.this.mDomRoamMaxUser + ", mIntRoamMaxUser = " + WifiServiceImpl.this.mIntRoamMaxUser);
                    ContentResolver cr = WifiServiceImpl.this.mContext.getContentResolver();
                    Settings.Secure.putInt(cr, "chameleon_tethereddata", WifiServiceImpl.this.mTetheredData);
                    Settings.System.putInt(cr, "chameleon_maxuser", WifiServiceImpl.this.mMaxUser);
                    Settings.System.putInt(cr, "chameleon_gsmmaxuser", WifiServiceImpl.this.mGsmMaxUser);
                    Settings.System.putInt(cr, "chameleon_domroammaxuser", WifiServiceImpl.this.mDomRoamMaxUser);
                    Settings.System.putInt(cr, "chameleon_introammaxuser", WifiServiceImpl.this.mIntRoamMaxUser);
                    Settings.System.putString(cr, "chameleon_ssid", mTempSSid);
                }
            }, new IntentFilter("com.samsung.sec.android.application.csc.chameleon_wifi"));
        }
        this.mContext.registerReceiver(new BroadcastReceiver() {
            /* class com.android.server.wifi.WifiServiceImpl.C04793 */

            public void onReceive(Context context, Intent intent) {
                if (WifiServiceImpl.this.getWifiApEnabledState() == 13) {
                    WifiServiceImpl.this.stopSoftAp();
                }
            }
        }, new IntentFilter("android.intent.action.USER_SWITCHED"));
        this.mContext.registerReceiver(new BroadcastReceiver() {
            /* class com.android.server.wifi.WifiServiceImpl.C04804 */

            public void onReceive(Context context, Intent intent) {
                Slog.d(WifiServiceImpl.TAG, "[onReceive] action : " + intent.getAction());
                int bandwidth = intent.getIntExtra("bandwidth", 1);
                Slog.d(WifiServiceImpl.TAG, "[onReceive] BANDWIDTH : " + bandwidth);
                WifiServiceImpl.this.setWifiApRttFeatureBandwidth(bandwidth);
            }
        }, new IntentFilter("com.android.cts.verifier.wifiaware.action.BANDWIDTH"));
    }

    public void setWifiApRttFeatureBandwidth(int bandwidth) {
        Log.d(TAG, "setWifiApRttFeatureBandwidth request:" + bandwidth);
        WifiNative wifiNative = this.mWifiNative;
        if (wifiNative == null) {
            return;
        }
        if (wifiNative.setWifiApRttFeatureBandwidth(bandwidth)) {
            Log.d(TAG, "setWifiApRttFeature set successfully");
        } else {
            Log.e(TAG, "Failed to set/reset the feature");
        }
    }

    @VisibleForTesting
    public void setWifiHandlerLogForTest(WifiLog log) {
        this.mAsyncChannelExternalClientHandler.setWifiLog(log);
    }

    public static String removeDoubleQuotes(String string) {
        if (string == null) {
            return null;
        }
        int length = string.length();
        if (length > 1 && string.charAt(0) == '\"' && string.charAt(length - 1) == '\"') {
            return string.substring(1, length - 1);
        }
        return string;
    }

    public void checkAndStartWifi() {
        Log.d(TAG, "checkAndStartWifi start");
        long startAt = System.currentTimeMillis();
        if (this.mFrameworkFacade.inStorageManagerCryptKeeperBounce()) {
            Log.d(TAG, "Device still encrypted. Need to restart SystemServer.  Do not start wifi.");
            return;
        }
        boolean wifiEnabled = false;
        if (FactoryTest.isFactoryBinary()) {
            Slog.i(TAG, "It's factory binary, do not enable Wi-Fi");
            this.mSettingsStore.handleWifiToggled(false);
        } else {
            wifiEnabled = this.mSettingsStore.isWifiToggleEnabled();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("WifiService starting up with Wi-Fi ");
        sb.append(wifiEnabled ? "enabled" : "disabled");
        Slog.i(TAG, sb.toString());
        registerForScanModeChange();
        getTelephonyManager().listen(this.mPhoneStateListener, 65);
        this.mContext.registerReceiver(new BroadcastReceiver() {
            /* class com.android.server.wifi.WifiServiceImpl.C04815 */

            public void onReceive(Context context, Intent intent) {
                if (WifiServiceImpl.this.mSettingsStore.handleAirplaneModeToggled()) {
                    WifiServiceImpl.this.mWifiController.sendMessage(155657);
                }
                boolean isAirplaneModeOn = WifiServiceImpl.this.mSettingsStore.isAirplaneModeOn();
                if (isAirplaneModeOn) {
                    Log.d(WifiServiceImpl.TAG, "resetting country code because Airplane mode is ON");
                    WifiServiceImpl.this.mCountryCode.airplaneModeEnabled();
                }
                if (WifiServiceImpl.this.mAutoWifiController != null) {
                    WifiServiceImpl.this.mAutoWifiController.setAirplainMode(isAirplaneModeOn);
                }
                WifiServiceImpl.this.mClientModeImpl.report(8, ReportUtil.getReportDataForChangeState(isAirplaneModeOn));
                WifiServiceImpl.this.mClientModeImpl.setFccChannel();
            }
        }, new IntentFilter("android.intent.action.AIRPLANE_MODE"));
        this.mContext.registerReceiver(new BroadcastReceiver() {
            /* class com.android.server.wifi.WifiServiceImpl.C04826 */

            public void onReceive(Context context, Intent intent) {
                String state = intent.getStringExtra("ss");
                if ("ABSENT".equals(state)) {
                    Log.d(WifiServiceImpl.TAG, "resetting networks because SIM was removed");
                    WifiServiceImpl.this.mClientModeImpl.resetSimAuthNetworks(false);
                    if (WifiServiceImpl.this.mAutoWifiController != null) {
                        WifiServiceImpl.this.mAutoWifiController.setSimState(TelephonyUtil.isSimCardReady(WifiServiceImpl.this.getTelephonyManager()));
                    }
                } else if ("IMSI".equals(state) || "READY".equals(state)) {
                    if (WifiServiceImpl.this.mAutoWifiController != null) {
                        WifiServiceImpl.this.mAutoWifiController.setSimState(true);
                    }
                } else if ("LOADED".equals(state)) {
                    Log.d(WifiServiceImpl.TAG, "resetting networks because SIM was loaded");
                    WifiServiceImpl.this.mClientModeImpl.resetSimAuthNetworks(true);
                }
            }
        }, new IntentFilter("android.intent.action.SIM_STATE_CHANGED"));
        this.mContext.registerReceiver(new BroadcastReceiver() {
            /* class com.android.server.wifi.WifiServiceImpl.C04837 */

            public void onReceive(Context context, Intent intent) {
                WifiServiceImpl.this.handleWifiApStateChange(intent.getIntExtra("wifi_state", 11), intent.getIntExtra("previous_wifi_state", 11), intent.getIntExtra("wifi_ap_error_code", -1), intent.getStringExtra("wifi_ap_interface_name"), intent.getIntExtra("wifi_ap_mode", -1));
            }
        }, new IntentFilter("android.net.wifi.WIFI_AP_STATE_CHANGED"));
        registerForBroadcasts();
        this.mInIdleMode = this.mPowerManager.isDeviceIdleMode();
        if (!this.mClientModeImpl.syncInitialize(this.mClientModeImplChannel)) {
            Log.wtf(TAG, "Failed to initialize ClientModeImpl");
        }
        this.mWifiController.start();
        bootUp();
        if (wifiEnabled) {
            WifiMobileDeviceManager.allowToStateChange(true);
            setWifiEnabled(this.mContext.getPackageName(), wifiEnabled);
            WifiMobileDeviceManager.allowToStateChange(false);
        }
        this.mWifiConnectivityMonitor = WifiConnectivityMonitor.makeWifiConnectivityMonitor(this.mContext, this.mClientModeImpl, this.mWifiInjector);
        WifiConnectivityMonitor wifiConnectivityMonitor = this.mWifiConnectivityMonitor;
        if (wifiConnectivityMonitor != null) {
            wifiConnectivityMonitor.setWifiEnabled(wifiEnabled, null);
        }
        if (this.mWifiConnectivityMonitor != null) {
            this.mIWCMonitor = IWCMonitor.initIWCMonitor(this.mContext, this.mWifiInjector);
            this.mIWCMonitor.setWcmAsyncChannel(this.mWifiConnectivityMonitor.getHandler());
            this.mWifiConnectivityMonitor.setIWCMonitorAsyncChannel(this.mIWCMonitor.getHandler());
            this.mClientModeImpl.setIWCMonitorAsyncChannel(this.mIWCMonitor.getHandler());
            Log.d(TAG, "SEC_PRODUCT_FEATURE_WLAN_SUPPORT_INTELLIGENT_WIFI_CONNECTION is enabled!");
        } else {
            Log.d(TAG, "SEC_PRODUCT_FEATURE_WLAN_SUPPORT_INTELLIGENT_WIFI_CONNECTION is true, but mWifiConnectivityMonitor is null");
        }
        checkAndStartAutoWifi();
        checkAndStartMHS();
        long duration = System.currentTimeMillis() - startAt;
        Log.d(TAG, "checkAndStartWifi end at +" + duration + "ms");
        if (duration >= 3000) {
            this.mClientModeImpl.report(16, ReportUtil.getReportDataForInitDelay((int) (duration / 1000)));
        }
    }

    private void checkAndStartMHS() {
        CscParser mParser = new CscParser(CscParser.getCustomerPath());
        this.mCSCRegion = mParser.get("GeneralInfo.Region");
        Log.d(TAG, "mCSCRegion:" + this.mCSCRegion);
        this.mCountryIso = mParser.get("GeneralInfo.CountryISO");
        if (supportWifiSharingLite()) {
            IntentFilter wifiSharingFilter = new IntentFilter();
            wifiSharingFilter.addAction("android.net.wifi.WIFI_STATE_CHANGED");
            this.mContext.registerReceiver(new BroadcastReceiver() {
                /* class com.android.server.wifi.WifiServiceImpl.C04848 */

                public void onReceive(Context context, Intent intent) {
                    if ("android.net.wifi.WIFI_STATE_CHANGED".equals(intent.getAction()) && intent.getIntExtra("wifi_state", 4) == 3 && WifiServiceImpl.this.isWifiSharingEnabled() && WifiServiceImpl.this.isMobileApOn()) {
                        WifiServiceImpl.this.mAsyncChannelExternalClientHandler.post(new Runnable() {
                            /* class com.android.server.wifi.$$Lambda$WifiServiceImpl$8$bB2Lqk6XE6NS5iXn00EEsPxi0 */

                            public final void run() {
                                WifiServiceImpl.C04848.this.lambda$onReceive$0$WifiServiceImpl$8();
                            }
                        });
                    }
                }

                public /* synthetic */ void lambda$onReceive$0$WifiServiceImpl$8() {
                    WifiServiceImpl.this.setIndoorChannelsToDriver(true);
                }
            }, wifiSharingFilter);
            mapIndoorCountryToChannel();
        }
        this.mWifiApConfigStore = this.mWifiInjector.getWifiApConfigStore();
        this.mSemWifiApBroadcastReceiver = WifiInjector.getInstance().getSemWifiApBroadcastReceiver();
        this.mSemWifiApBroadcastReceiver.startTracking();
        startSwitchBoardService();
        this.mSemWifiApTrafficPoller.handleBootCompleted();
    }

    private void startSwitchBoardService() {
        Log.e(TAG, "startSwitchBoardService");
        HandlerThread switchboardThread = new HandlerThread("SwitchBoardService");
        switchboardThread.start();
        this.mSwitchBoardService = SwitchBoardService.getInstance(this.mContext, switchboardThread.getLooper(), this.mClientModeImpl);
    }

    private int getSecurity(WifiConfiguration config) {
        if (config.allowedKeyManagement.get(1)) {
            return 2;
        }
        if (config.allowedKeyManagement.get(2) || config.allowedKeyManagement.get(3)) {
            return 3;
        }
        if (config.wepKeys[0] != null) {
            return 1;
        }
        return 0;
    }

    private int getSecurity(ScanResult result) {
        if (result.capabilities.contains("WEP")) {
            return 1;
        }
        if (result.capabilities.contains("PSK")) {
            return 2;
        }
        if (result.capabilities.contains("EAP")) {
            return 3;
        }
        return 0;
    }

    public void handleBootCompleted() {
        Log.d(TAG, "Handle boot completed");
        if (!FactoryTest.isFactoryBinary()) {
            this.mSemWifiApSmartUtil.handleBootCompleted();
            this.mSemWifiApSmartGattServer.handleBootCompleted();
            this.mSemWifiApSmartMHS.handleBootCompleted();
            this.mSemWifiApSmartClient.handleBootCompleted();
            this.mSemWifiApSmartGattClient.handleBootCompleted();
            this.mSemWifiApSmartBleScanner.handleBootCompleted();
            if (SUPPORTMOBILEAPENHANCED_D2D || SUPPORTMOBILEAPENHANCED_WIFI_ONLY_LITE) {
                this.mSemWifiApSmartD2DGattClient.handleBootCompleted();
                this.mSemWifiApSmartD2DMHS.handleBootCompleted();
                this.mSemWifiApSmartD2DClient.handleBootCompleted();
            }
        }
        this.mPasspointManager.initializeProvisioner(this.mWifiInjector.getPasspointProvisionerHandlerThread().getLooper());
        this.mClientModeImpl.handleBootCompleted();
        if (this.mSemQoSProfileShareProvider == null && this.mContext.getPackageManager().hasSystemFeature("com.samsung.feature.samsung_experience_mobile") && SemFloatingFeature.getInstance().getBoolean("SEC_FLOATING_FEATURE_MCF_SUPPORT_FRAMEWORK")) {
            this.mSemQoSProfileShareProvider = this.mWifiInjector.getQoSProfileShareProvider(new SemWifiProfileAndQoSProvider.Adapter() {
                /* class com.android.server.wifi.WifiServiceImpl.C04859 */

                @Override // com.samsung.android.server.wifi.share.SemWifiProfileAndQoSProvider.Adapter
                public int[] getCurrentNetworkScore() {
                    int[] currentNetworkScores = WifiServiceImpl.this.mWifiConnectivityMonitor.getOpenNetworkQosScores();
                    if (currentNetworkScores == null || currentNetworkScores.length != 3) {
                        Log.d(WifiServiceImpl.TAG, "getCurrentNetworkScore - invalid score data");
                        return null;
                    }
                    int[] iArr = new int[4];
                    iArr[0] = WifiServiceImpl.this.mWifiConnectivityMonitor.getOpenNetworkQosNoInternetStatus() ? 2 : 0;
                    iArr[1] = currentNetworkScores[0];
                    iArr[2] = currentNetworkScores[1];
                    iArr[3] = currentNetworkScores[2];
                    return iArr;
                }

                @Override // com.samsung.android.server.wifi.share.SemWifiProfileAndQoSProvider.Adapter
                public boolean isWpa3SaeSupported() {
                    return (WifiServiceImpl.this.getSupportedFeatures() & 134217728) != 0;
                }

                @Override // com.samsung.android.server.wifi.share.SemWifiProfileAndQoSProvider.Adapter
                public boolean isWifiEnabled() {
                    return WifiServiceImpl.this.getWifiEnabledState() == 3;
                }

                @Override // com.samsung.android.server.wifi.share.SemWifiProfileAndQoSProvider.Adapter
                public void startScan() {
                    WifiServiceImpl.this.startScan("android");
                }

                @Override // com.samsung.android.server.wifi.share.SemWifiProfileAndQoSProvider.Adapter
                public List<ScanResult> getScanResults() {
                    return WifiServiceImpl.this.getScanResults("android");
                }

                @Override // com.samsung.android.server.wifi.share.SemWifiProfileAndQoSProvider.Adapter
                public List<WifiConfiguration> getPrivilegedConfiguredNetworks() {
                    if (WifiServiceImpl.this.mClientModeImplChannel != null) {
                        return WifiServiceImpl.this.mClientModeImpl.syncGetPrivilegedConfiguredNetwork(WifiServiceImpl.this.mClientModeImplChannel);
                    }
                    return new ArrayList();
                }

                @Override // com.samsung.android.server.wifi.share.SemWifiProfileAndQoSProvider.Adapter
                public WifiConfiguration getSpecificNetwork(int networkId) {
                    return WifiServiceImpl.this.getSpecificNetwork(networkId);
                }

                @Override // com.samsung.android.server.wifi.share.SemWifiProfileAndQoSProvider.Adapter
                public WifiInfo getConnectionInfo() {
                    return WifiServiceImpl.this.getConnectionInfo("android");
                }

                @Override // com.samsung.android.server.wifi.share.SemWifiProfileAndQoSProvider.Adapter
                public int getWifiApState() {
                    return WifiServiceImpl.this.getWifiApEnabledState();
                }

                @Override // com.samsung.android.server.wifi.share.SemWifiProfileAndQoSProvider.Adapter
                public WifiConfiguration getWifiApConfiguration() {
                    if (!WifiServiceImpl.this.isMobileApOn()) {
                        return null;
                    }
                    WifiConfiguration apConfig = WifiServiceImpl.this.getWifiApConfiguration();
                    if (apConfig != null) {
                        String mhsMacAddress = WifiServiceImpl.this.mSemWifiApSmartUtil.getMHSMacFromInterface();
                        if (mhsMacAddress == null) {
                            return null;
                        }
                        apConfig = new WifiConfiguration(apConfig);
                        if (apConfig.SSID != null && !apConfig.SSID.startsWith("\"")) {
                            apConfig.SSID = "\"" + apConfig.SSID + "\"";
                        }
                        if (apConfig.allowedKeyManagement.get(4)) {
                            apConfig.allowedKeyManagement = new BitSet(64);
                            apConfig.allowedKeyManagement.set(1);
                        } else if (apConfig.allowedKeyManagement.get(25) || apConfig.allowedKeyManagement.get(26)) {
                            apConfig.allowedKeyManagement = new BitSet(64);
                            apConfig.allowedKeyManagement.set(8);
                        }
                        apConfig.BSSID = mhsMacAddress;
                    }
                    return apConfig;
                }
            });
            this.mSemQoSProfileShareProvider.checkAndStart();
            this.mWifiConnectivityMonitor.registerOpenNetworkQosCallback(new WifiConnectivityMonitor.OpenNetworkQosCallback() {
                /* class com.android.server.wifi.WifiServiceImpl.C047110 */

                @Override // com.android.server.wifi.WifiConnectivityMonitor.OpenNetworkQosCallback
                public void onNoInternetStatusChange(boolean valid) {
                    WifiServiceImpl.this.mSemQoSProfileShareProvider.updateQoSData(true, false);
                }

                @Override // com.android.server.wifi.WifiConnectivityMonitor.OpenNetworkQosCallback
                public void onQualityScoreChanged() {
                    WifiServiceImpl.this.mSemQoSProfileShareProvider.updateQoSData(false, true);
                }
            });
        }
    }

    public void handleUserSwitch(int userId) {
        Log.d(TAG, "Handle user switch " + userId);
        this.mClientModeImpl.handleUserSwitch(userId);
    }

    public void handleUserUnlock(int userId) {
        Log.d(TAG, "Handle user unlock " + userId);
        this.mClientModeImpl.handleUserUnlock(userId);
    }

    public void handleUserStop(int userId) {
        Log.d(TAG, "Handle user stop " + userId);
        this.mClientModeImpl.handleUserStop(userId);
    }

    public boolean startScan(String packageName) {
        return startScan(packageName, null);
    }

    /* JADX WARNING: Code restructure failed: missing block: B:13:0x0034, code lost:
        if (com.android.server.wifi.WifiServiceImpl.WIFI_STOP_SCAN_FOR_ETWS == false) goto L_0x0044;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:15:0x0038, code lost:
        if (r16.mBlockScanFromOthers == false) goto L_0x0044;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:16:0x003a, code lost:
        android.util.Log.i(com.android.server.wifi.WifiServiceImpl.TAG, "ETWS: ignore scan");
        r16.mBlockScanFromOthers = false;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:17:0x0043, code lost:
        return false;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:19:?, code lost:
        r16.mWifiPermissionsUtil.enforceCanAccessScanResults(r17, r10);
        r0 = new com.android.server.wifi.util.GeneralUtil.Mutable<>();
     */
    /* JADX WARNING: Code restructure failed: missing block: B:20:0x0068, code lost:
        if (r16.mWifiInjector.getClientModeImplHandler().runWithScissors(new com.android.server.wifi.$$Lambda$WifiServiceImpl$uJhaCZrKivZlQmLD6fKbGekPXUY(r16, r0, r10, r17, r18), com.android.server.wifi.iwc.IWCEventManager.autoDisconnectThreshold) != false) goto L_0x0079;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:21:0x006a, code lost:
        android.util.Log.e(com.android.server.wifi.WifiServiceImpl.TAG, "Failed to post runnable to start scan");
        sendFailedScanBroadcast();
     */
    /* JADX WARNING: Code restructure failed: missing block: B:23:0x0078, code lost:
        return false;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:25:0x0081, code lost:
        if (r0.value.booleanValue() != false) goto L_0x008f;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:26:0x0083, code lost:
        android.util.Log.e(com.android.server.wifi.WifiServiceImpl.TAG, "Failed to start scan");
        android.os.Binder.restoreCallingIdentity(r11);
     */
    /* JADX WARNING: Code restructure failed: missing block: B:27:0x008e, code lost:
        return false;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:28:0x008f, code lost:
        android.os.Binder.restoreCallingIdentity(r11);
     */
    /* JADX WARNING: Code restructure failed: missing block: B:29:0x0093, code lost:
        return true;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:30:0x0094, code lost:
        r0 = move-exception;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:31:0x0096, code lost:
        r0 = move-exception;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:34:?, code lost:
        android.util.Slog.e(com.android.server.wifi.WifiServiceImpl.TAG, "Permission violation - startScan not allowed for uid=" + r10 + ", packageName=" + r17 + ", reason=" + r0);
     */
    /* JADX WARNING: Code restructure failed: missing block: B:36:0x00c1, code lost:
        return false;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:37:0x00c2, code lost:
        android.os.Binder.restoreCallingIdentity(r11);
     */
    /* JADX WARNING: Code restructure failed: missing block: B:38:0x00c5, code lost:
        throw r0;
     */
    public boolean startScan(String packageName, Set<Integer> freqs) {
        if (enforceChangePermission(packageName) != 0) {
            return false;
        }
        int callingUid = Binder.getCallingUid();
        long ident = Binder.clearCallingIdentity();
        this.mLog.info("startScan uid=%").mo2069c((long) callingUid).flush();
        synchronized (this) {
            if (this.mInIdleMode) {
                sendFailedScanBroadcast();
                this.mScanPending = true;
                return false;
            }
        }
    }

    public /* synthetic */ void lambda$startScan$0$WifiServiceImpl(GeneralUtil.Mutable scanSuccess, int callingUid, String packageName, Set freqs) {
        scanSuccess.value = (E) Boolean.valueOf(this.mScanRequestProxy.startScan(callingUid, packageName, freqs));
    }

    public void semStartPartialChannelScan(int[] frequencies, String packageName) {
        Log.i(TAG, "semStartPartialChannelScan uid :" + Binder.getCallingUid() + ", package : " + packageName);
        startScan(packageName, getPartialChannelScanSettings(frequencies));
    }

    private Set<Integer> getPartialChannelScanSettings(int[] freqs) {
        Set<Integer> mFreqs = new HashSet<>();
        for (int freq : freqs) {
            if (!mAllFreqArray.contains(Integer.valueOf(freq))) {
                Log.w(TAG, "getPartialChannelScanSettings: ignore freq = " + freq);
            } else {
                mFreqs.add(Integer.valueOf(freq));
            }
        }
        return mFreqs;
    }

    private void sendFailedScanBroadcast() {
        long callingIdentity = Binder.clearCallingIdentity();
        try {
            Intent intent = new Intent("android.net.wifi.SCAN_RESULTS");
            intent.addFlags(67108864);
            intent.putExtra("resultsUpdated", false);
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
        } finally {
            Binder.restoreCallingIdentity(callingIdentity);
        }
    }

    public String getCurrentNetworkWpsNfcConfigurationToken() {
        enforceConnectivityInternalPermission();
        if (!this.mVerboseLoggingEnabled) {
            return null;
        }
        this.mLog.info("getCurrentNetworkWpsNfcConfigurationToken uid=%").mo2069c((long) Binder.getCallingUid()).flush();
        return null;
    }

    /* access modifiers changed from: package-private */
    public void handleIdleModeChanged() {
        boolean doScan = false;
        synchronized (this) {
            boolean idle = this.mPowerManager.isDeviceIdleMode();
            if (this.mInIdleMode != idle) {
                this.mInIdleMode = idle;
                if (!idle && this.mScanPending) {
                    this.mScanPending = false;
                    doScan = true;
                }
            }
        }
        if (doScan) {
            startScan(this.mContext.getOpPackageName());
        }
    }

    private boolean checkNetworkSettingsPermission(int pid, int uid) {
        return this.mContext.checkPermission("android.permission.NETWORK_SETTINGS", pid, uid) == 0;
    }

    private boolean checkNetworkSetupWizardPermission(int pid, int uid) {
        return this.mContext.checkPermission("android.permission.NETWORK_SETUP_WIZARD", pid, uid) == 0;
    }

    private boolean checkNetworkStackPermission(int pid, int uid) {
        return this.mContext.checkPermission("android.permission.NETWORK_STACK", pid, uid) == 0;
    }

    private boolean checkNetworkManagedProvisioningPermission(int pid, int uid) {
        return this.mContext.checkPermission("android.permission.NETWORK_MANAGED_PROVISIONING", pid, uid) == 0;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean isPrivileged(int pid, int uid) {
        return checkNetworkSettingsPermission(pid, uid) || checkNetworkSetupWizardPermission(pid, uid) || checkNetworkStackPermission(pid, uid) || checkNetworkManagedProvisioningPermission(pid, uid);
    }

    private boolean isSettingsOrSuw(int pid, int uid) {
        return checkNetworkSettingsPermission(pid, uid) || checkNetworkSetupWizardPermission(pid, uid);
    }

    private boolean isSystemOrPlatformSigned(String packageName) {
        long ident = Binder.clearCallingIdentity();
        boolean z = false;
        try {
            ApplicationInfo info = this.mContext.getPackageManager().getApplicationInfo(packageName, 0);
            if (info.isSystemApp() || info.isUpdatedSystemApp() || info.isSignedWithPlatformKey()) {
                z = true;
            }
            return z;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    private boolean isDeviceOrProfileOwner(int uid) {
        DevicePolicyManagerInternal dpmi = this.mWifiInjector.getWifiPermissionsWrapper().getDevicePolicyManagerInternal();
        if (dpmi == null) {
            return false;
        }
        if (dpmi.isActiveAdminWithPolicy(uid, -2) || dpmi.isActiveAdminWithPolicy(uid, -1)) {
            return true;
        }
        return false;
    }

    private void enforceNetworkSettingsPermission() {
        this.mContext.enforceCallingOrSelfPermission("android.permission.NETWORK_SETTINGS", TAG);
    }

    private void enforceNetworkStackPermission() {
        this.mContext.enforceCallingOrSelfPermission("android.permission.NETWORK_STACK", TAG);
    }

    private void enforceAccessPermission() {
        this.mContext.enforceCallingOrSelfPermission("android.permission.ACCESS_WIFI_STATE", TAG);
    }

    private void enforceAccessNetworkPermission() {
        this.mContext.enforceCallingOrSelfPermission("android.permission.ACCESS_NETWORK_STATE", TAG);
    }

    private boolean checkAccessSecuredPermission(int pid, int uid) {
        if (this.mContext.checkPermission("com.samsung.permission.SEM_ACCESS_WIFI_SECURED_INFO", pid, uid) == 0) {
            return true;
        }
        return false;
    }

    private void enforceProvideDiagnosticsPermission() {
        this.mContext.enforceCallingOrSelfPermission("com.samsung.permission.WIFI_DIAGNOSTICS_PROVIDER", TAG);
    }

    private void enforceFactoryTestPermission() {
        this.mContext.enforceCallingOrSelfPermission("com.samsung.permission.WIFI_FACTORY_TEST", TAG);
    }

    private int enforceChangePermission(String callingPackage) {
        if (checkNetworkSettingsPermission(Binder.getCallingPid(), Binder.getCallingUid())) {
            return 0;
        }
        this.mContext.enforceCallingOrSelfPermission("android.permission.CHANGE_WIFI_STATE", TAG);
        return this.mAppOps.noteOp("android:change_wifi_state", Binder.getCallingUid(), callingPackage);
    }

    private void enforceChangePermission() {
        this.mContext.enforceCallingOrSelfPermission("android.permission.CHANGE_WIFI_STATE", TAG);
    }

    private void enforceLocationHardwarePermission() {
        this.mContext.enforceCallingOrSelfPermission("android.permission.LOCATION_HARDWARE", "LocationHardware");
    }

    private void enforceReadCredentialPermission() {
        this.mContext.enforceCallingOrSelfPermission("android.permission.READ_WIFI_CREDENTIAL", TAG);
    }

    private void enforceWorkSourcePermission() {
        this.mContext.enforceCallingPermission("android.permission.UPDATE_DEVICE_STATS", TAG);
    }

    private void enforceMulticastChangePermission() {
        this.mContext.enforceCallingOrSelfPermission("android.permission.CHANGE_WIFI_MULTICAST_STATE", TAG);
    }

    private void enforceConnectivityInternalPermission() {
        this.mContext.enforceCallingOrSelfPermission("android.permission.CONNECTIVITY_INTERNAL", "ConnectivityService");
    }

    private void enforceLocationPermission(String pkgName, int uid) {
        this.mWifiPermissionsUtil.enforceLocationPermission(pkgName, uid);
    }

    private boolean isTargetSdkLessThanQOrPrivileged(String packageName, int pid, int uid) {
        return this.mWifiPermissionsUtil.isTargetSdkLessThan(packageName, 29) || isPrivileged(pid, uid) || isDeviceOrProfileOwner(uid) || isSystemOrPlatformSigned(packageName) || this.mWifiPermissionsUtil.checkSystemAlertWindowPermission(uid, packageName);
    }

    public synchronized boolean setWifiEnabled(String packageName, boolean enable) {
        int i = 0;
        if (enforceChangePermission(packageName) != 0) {
            return false;
        }
        boolean isPrivileged = isPrivileged(Binder.getCallingPid(), Binder.getCallingUid());
        if (!isPrivileged && !this.mWifiPermissionsUtil.isTargetSdkLessThan(packageName, 29) && !this.mWifiPermissionsUtil.checkFactoryTestPermission(Binder.getCallingUid()) && !isSystemOrPlatformSigned(packageName)) {
            this.mLog.info("setWifiEnabled not allowed for uid=%").mo2069c((long) Binder.getCallingUid()).flush();
            return false;
        } else if (!this.mSettingsStore.isAirplaneModeOn() || isPrivileged || "com.android.bluetooth".equals(packageName)) {
            if (!(this.mWifiApState == 13) || isPrivileged) {
                CharSequence dateTime = DateFormat.format("yy/MM/dd kk:mm:ss ", System.currentTimeMillis());
                addHistoricalDumpLog(((Object) dateTime) + " WifiManager.setWifiEnabled(" + enable + ") : " + packageName + "\n");
                WifiControlHistoryProvider.setControlHistory(this.mContext, packageName, enable);
                this.mLog.info("setWifiEnabled package=% uid=% enable=%").mo2070c(packageName).mo2069c((long) Binder.getCallingUid()).mo2071c(enable).flush();
                long ident = Binder.clearCallingIdentity();
                try {
                    if (!WifiMobileDeviceManager.isAllowToUseWifi(this.mContext, enable)) {
                        this.mLog.info("setWifiEnabled disallow to use wifi: disabled by mdm").flush();
                        return false;
                    } else if (enable && !this.mDevicePolicyManager.isAllowToUseWifi()) {
                        this.mLog.info("setWifiEnabled disallow to use wifi: disabled by dpm").flush();
                        Binder.restoreCallingIdentity(ident);
                        return false;
                    } else if (!enable || !this.mWifiEnableWarningPolicy.isAllowWifiWarning() || !this.mWifiEnableWarningPolicy.needToShowWarningDialog(getWifiApEnabledState(), getWifiEnabledState(), isWifiSharingEnabled(), packageName)) {
                        if (!enable && supportWifiSharingLite()) {
                            Settings.System.putInt(this.mContext.getContentResolver(), "wifi_sharing_lite_popup_status", 0);
                        }
                        if (enable && checkAndShowWifiSharingLiteDialog(packageName)) {
                            this.mLog.info("setWifiEnabled in Wifi sharing: disabled for showing wifi sharing lite dialog").flush();
                            Binder.restoreCallingIdentity(ident);
                            return false;
                        } else if (enable && checkAndShowFirmwareChangeDialog(packageName)) {
                            this.mLog.info("setWifiEnabled on only enabling SoftAp: disabled for showing wifi enable warning dialog").flush();
                            Binder.restoreCallingIdentity(ident);
                            return false;
                        } else if (!this.mSettingsStore.handleWifiToggled(enable)) {
                            Binder.restoreCallingIdentity(ident);
                            return true;
                        } else {
                            Binder.restoreCallingIdentity(ident);
                            this.mWifiMetrics.incrementNumWifiToggles(isPrivileged, enable);
                            boolean triggeredByUser = false;
                            if ("com.android.systemui".equals(packageName) || SETTINGS_PACKAGE_NAME.equals(packageName) || DESKTOP_SYSTEM_UI_PACKAGE_NAME.equals(packageName)) {
                                triggeredByUser = true;
                            }
                            long ident2 = Binder.clearCallingIdentity();
                            try {
                                setWifiEnabledTriggered(enable, packageName, triggeredByUser);
                                if (!enable && CSC_SEND_DHCP_RELEASE) {
                                    sendDhcpRelease();
                                }
                                if (enable) {
                                    int wiFiEnabledState = getWifiEnabledState();
                                    if (wiFiEnabledState != 0 && wiFiEnabledState != 1) {
                                        Slog.d(TAG, "skip forcinglyEnableAllNetworks due to already enabled wifi state");
                                    } else if (this.mClientModeImplChannel != null) {
                                        this.mClientModeImpl.forcinglyEnableAllNetworks(this.mClientModeImplChannel);
                                    } else {
                                        Slog.e(TAG, "mClientModeImplChannel is not initialized");
                                    }
                                }
                                if (this.mWifiConnectivityMonitor != null) {
                                    WifiInfo mWifiInfo = this.mClientModeImpl.getWifiInfo();
                                    String bssid = null;
                                    if (mWifiInfo != null) {
                                        bssid = mWifiInfo.getBSSID();
                                    }
                                    this.mWifiConnectivityMonitor.setWifiEnabled(enable, bssid);
                                }
                                long ident3 = Binder.clearCallingIdentity();
                                if (!enable) {
                                    try {
                                        disconnect(this.mContext.getPackageName());
                                    } catch (Throwable th) {
                                        Binder.restoreCallingIdentity(ident3);
                                        throw th;
                                    }
                                }
                                Binder.restoreCallingIdentity(ident3);
                                this.mWifiController.sendMessage(155656);
                                if (this.mIWCMonitor != null) {
                                    Bundle args = new Bundle();
                                    args.putString("package", packageName);
                                    args.putInt("calling_uid", Binder.getCallingUid());
                                    IWCMonitor iWCMonitor = this.mIWCMonitor;
                                    int i2 = triggeredByUser ? 1 : 0;
                                    if (enable) {
                                        i = 1;
                                    }
                                    iWCMonitor.sendMessage(155656, i2, i, args);
                                }
                                if (enable && triggeredByUser && ("com.android.systemui".equals(packageName) || DESKTOP_SYSTEM_UI_PACKAGE_NAME.equals(packageName))) {
                                    this.mWifiInjector.getPickerController().setEnable(true);
                                }
                                return true;
                            } finally {
                                Binder.restoreCallingIdentity(ident2);
                            }
                        }
                    } else {
                        this.mLog.info("setWifiEnabled on CSC_COMMON_CHINA_NAL_SECURITY_TYPE: disabled for China warning dialog").flush();
                        Binder.restoreCallingIdentity(ident);
                        return false;
                    }
                } finally {
                    Binder.restoreCallingIdentity(ident);
                }
            } else {
                this.mLog.err("setWifiEnabled SoftAp enabled: only Settings can toggle wifi").flush();
                return false;
            }
        } else {
            this.mLog.err("setWifiEnabled in Airplane mode: only isPrivileged apps or bluetooth can toggle wifi").flush();
            return false;
        }
    }

    public int getWifiEnabledState() {
        enforceAccessPermission();
        if (this.mVerboseLoggingEnabled) {
            this.mLog.info("getWifiEnabledState uid=%").mo2069c((long) Binder.getCallingUid()).flush();
        }
        return this.mClientModeImpl.syncGetWifiState();
    }

    public int getWifiApEnabledState() {
        enforceAccessPermission();
        if (this.mVerboseLoggingEnabled) {
            this.mLog.info("getWifiApEnabledState uid=%").mo2069c((long) Binder.getCallingUid()).flush();
        }
        ActiveModeWarden activeModeWarden = this.mActiveModeWarden;
        if (activeModeWarden != null) {
            return activeModeWarden.getSoftApState();
        }
        return this.mWifiApState;
    }

    private /* synthetic */ void lambda$getWifiApEnabledState$1(MutableInt apState) {
        apState.value = this.mWifiApState;
    }

    public void updateInterfaceIpState(String ifaceName, int mode) {
        enforceNetworkStackPermission();
        this.mLog.info("updateInterfaceIpState uid=%").mo2069c((long) Binder.getCallingUid()).flush();
        this.mWifiInjector.getClientModeImplHandler().post(new Runnable(ifaceName, mode) {
            /* class com.android.server.wifi.$$Lambda$WifiServiceImpl$UQ9JbF5sXBV77FhG4oE7wjNFgek */
            private final /* synthetic */ String f$1;
            private final /* synthetic */ int f$2;

            {
                this.f$1 = r2;
                this.f$2 = r3;
            }

            public final void run() {
                WifiServiceImpl.this.lambda$updateInterfaceIpState$2$WifiServiceImpl(this.f$1, this.f$2);
            }
        });
    }

    /* access modifiers changed from: private */
    /* renamed from: updateInterfaceIpStateInternal */
    public void lambda$updateInterfaceIpState$2$WifiServiceImpl(String ifaceName, int mode) {
        synchronized (this.mLocalOnlyHotspotRequests) {
            int previousMode = -1;
            if (ifaceName != null) {
                previousMode = this.mIfaceIpModes.put(ifaceName, Integer.valueOf(mode));
            }
            Slog.d(TAG, "updateInterfaceIpState: ifaceName=" + ifaceName + " mode=" + mode + " previous mode= " + previousMode);
            if (mode != -1) {
                if (mode == 0) {
                    Slog.d(TAG, "IP mode config error - need to clean up");
                    if (this.mLocalOnlyHotspotRequests.isEmpty()) {
                        Slog.d(TAG, "no LOHS requests, stop softap");
                        stopSoftAp();
                    } else {
                        Slog.d(TAG, "we have LOHS requests, clean them up");
                        sendHotspotFailedMessageToAllLOHSRequestInfoEntriesLocked(2);
                    }
                    lambda$updateInterfaceIpState$2$WifiServiceImpl(null, -1);
                } else if (mode != 1) {
                    if (mode != 2) {
                        this.mLog.warn("updateInterfaceIpStateInternal: unknown mode %").mo2069c((long) mode).flush();
                    } else if (!this.mLocalOnlyHotspotRequests.isEmpty()) {
                        sendHotspotStartedMessageToAllLOHSRequestInfoEntriesLocked();
                    } else if (getRvfMode() == 1) {
                        Log.d(TAG, " RVF mode on, do not turn off");
                    } else {
                        stopSoftAp();
                        lambda$updateInterfaceIpState$2$WifiServiceImpl(null, -1);
                    }
                } else if (!isConcurrentLohsAndTetheringSupported()) {
                    sendHotspotFailedMessageToAllLOHSRequestInfoEntriesLocked(3);
                }
            } else if (ifaceName == null) {
                this.mIfaceIpModes.clear();
            }
        }
    }

    public boolean startSoftAp(WifiConfiguration wifiConfig) {
        PackageManager pm = this.mContext.getPackageManager();
        String clientPkgName = getPackageName(Binder.getCallingPid());
        int signature = pm.checkSignatures("android", clientPkgName);
        Log.d(TAG, "clientPkgName : " + clientPkgName + " " + signature);
        if (signature != 0) {
            Log.d(TAG, "check network stack for " + clientPkgName);
            enforceNetworkStackPermission();
        }
        this.mLog.info("startSoftAp uid=%").mo2069c((long) Binder.getCallingUid()).flush();
        if (this.mFrameworkFacade.inStorageManagerCryptKeeperBounce()) {
            Log.e(TAG, "Device still encrypted. Need to restart SystemServer.  Do not start hotspot.");
            return false;
        }
        int wifiApState = getWifiApEnabledState();
        if (wifiApState == 13 || wifiApState == 12) {
            Log.w(TAG, " skip due to  " + wifiApState);
            return true;
        }
        if (wifiApState == 11) {
            this.mIfaceIpModes.clear();
        }
        this.mLog.info("startSoftAp uid=%").mo2069c((long) Binder.getCallingUid()).flush();
        synchronized (this.mLocalOnlyHotspotRequests) {
            if (this.mIfaceIpModes.contains(1)) {
                this.mLog.err("Tethering is already active.").flush();
                return false;
            }
            if (!isConcurrentLohsAndTetheringSupported() && !this.mLocalOnlyHotspotRequests.isEmpty()) {
                stopSoftApInternal(2);
            }
            if (getRvfMode() == 1) {
                Log.d(TAG, "RVF mode on");
                return startSoftApInternal(wifiConfig, 2);
            }
            return startSoftApInternal(wifiConfig, 1);
        }
    }

    private boolean startSoftApInternal(WifiConfiguration wifiConfig, int mode) {
        this.mLog.trace("startSoftApInternal uid=% mode=%").mo2069c((long) Binder.getCallingUid()).mo2069c((long) mode).flush();
        if (wifiConfig != null && !WifiApConfigStore.validateApWifiConfiguration(wifiConfig)) {
            Slog.e(TAG, "Invalid WifiConfiguration");
            return false;
        } else if (this.mClientModeImpl.syncGetWifiState() == 3 && !EnterpriseDeviceManager.getInstance().getWifiPolicy().isWifiStateChangeAllowed()) {
            Intent intent = new Intent("android.net.wifi.WIFI_AP_STATE_CHANGED");
            intent.putExtra("wifi_state", 14);
            intent.putExtra("previous_wifi_state", 11);
            intent.putExtra("wifi_ap_error_code", 0);
            this.mContext.sendBroadcast(intent);
            intent.setPackage(SETTINGS_PACKAGE_NAME);
            this.mContext.sendBroadcast(intent);
            intent.setPackage("com.android.systemui");
            this.mContext.sendBroadcast(intent);
            return false;
        } else if (!this.mDevicePolicyManager.isAllowToUseHotspot() && mode != 2) {
            return false;
        } else {
            if (wifiConfig == null) {
                if (getWifiApConfiguration().allowedKeyManagement.get(0) && !this.mDevicePolicyManager.isOpenWifiApAllowed()) {
                    return false;
                }
                wifiConfig = getWifiApConfiguration();
            } else if (wifiConfig.allowedKeyManagement.get(0) && !this.mDevicePolicyManager.isOpenWifiApAllowed()) {
                return false;
            } else {
                if (wifiConfig.allowedKeyManagement.get(1)) {
                    if (wifiConfig.preSharedKey == null || wifiConfig.preSharedKey.length() < 8 || wifiConfig.preSharedKey.length() > 32) {
                        wifiConfig.allowedKeyManagement.set(0);
                    } else {
                        wifiConfig.allowedKeyManagement.set(4);
                    }
                    Log.e(TAG, " conf changed to wpa2/none from wpa");
                }
            }
            if (supportWifiSharingLite() && isWifiSharingEnabled()) {
                setIndoorChannelsToDriver(true);
            }
            SoftApModeConfiguration softApConfig = new SoftApModeConfiguration(mode, wifiConfig);
            if (this.mSettingsStore.getWifiSavedState() == 1) {
                this.mWifiController.sendMessage(155658, 1, 1, softApConfig);
            } else {
                this.mWifiController.sendMessage(155658, 1, 0, softApConfig);
            }
            return true;
        }
    }

    public boolean stopSoftAp() {
        PackageManager pm = this.mContext.getPackageManager();
        String clientPkgName = getPackageName(Binder.getCallingPid());
        int signature = pm.checkSignatures("android", clientPkgName);
        Log.d(TAG, "clientPkgName : " + clientPkgName + " " + signature);
        if (signature != 0) {
            Log.d(TAG, "check network stack for " + clientPkgName);
            enforceNetworkStackPermission();
        }
        this.mLog.info("stopSoftAp uid=%").mo2069c((long) Binder.getCallingUid()).flush();
        synchronized (this.mLocalOnlyHotspotRequests) {
            if (!this.mLocalOnlyHotspotRequests.isEmpty()) {
                this.mLog.trace("Call to stop Tethering while LOHS is active, Registered LOHS callers will be updated when softap stopped.").flush();
            }
            if (getRvfMode() == 1) {
                Log.d(TAG, "RVF mode on, turn off hotspot" + this.mLocalOnlyHotspotRequests.isEmpty());
                lambda$updateInterfaceIpState$2$WifiServiceImpl(null, -1);
                setRvfMode(0);
                return stopSoftApInternal(2);
            } else if (this.mLocalOnlyHotspotRequests.isEmpty() || this.mIfaceIpModes.contains(1)) {
                return stopSoftApInternal(1);
            } else {
                Log.d(TAG, "mLocalOnlyHotspotRequests, turn off hotspot" + this.mLocalOnlyHotspotRequests.isEmpty());
                return stopSoftApInternal(2);
            }
        }
    }

    private boolean stopSoftApInternal(int mode) {
        this.mLog.trace("stopSoftApInternal uid=%").mo2069c((long) Binder.getCallingUid()).flush();
        if (this.mFrameworkFacade.inStorageManagerCryptKeeperBounce()) {
            Log.e(TAG, "Device still encrypted. Need to restart SystemServer.  Do not start hotspot.");
            return false;
        }
        if (isWifiSharingEnabled() && this.isProvisionSuccessful == 1) {
            this.isProvisionSuccessful = 0;
        }
        if (supportWifiSharing() && supportWifiSharingLite()) {
            setIndoorChannelsToDriver(false);
        }
        this.mWifiController.sendMessage(155658, 0, mode);
        return true;
    }

    private final class SoftApCallbackImpl implements WifiManager.SoftApCallback {
        private SoftApCallbackImpl() {
        }

        public synchronized void onStateChanged(int state, int failureReason) {
            WifiServiceImpl.this.mSoftApState = state;
            Iterator<ISoftApCallback> iterator = WifiServiceImpl.this.mRegisteredSoftApCallbacks.getCallbacks().iterator();
            while (iterator.hasNext()) {
                try {
                    iterator.next().onStateChanged(state, failureReason);
                } catch (RemoteException e) {
                    Log.e(WifiServiceImpl.TAG, "onStateChanged: remote exception -- " + e);
                    iterator.remove();
                }
            }
        }

        public synchronized void onNumClientsChanged(int numClients) {
            WifiServiceImpl.this.mSoftApNumClients = numClients;
            Iterator<ISoftApCallback> iterator = WifiServiceImpl.this.mRegisteredSoftApCallbacks.getCallbacks().iterator();
            while (iterator.hasNext()) {
                try {
                    iterator.next().onNumClientsChanged(numClients);
                } catch (RemoteException e) {
                    Log.e(WifiServiceImpl.TAG, "onNumClientsChanged: remote exception -- " + e);
                    iterator.remove();
                }
            }
        }
    }

    public void registerSoftApCallback(IBinder binder, ISoftApCallback callback, int callbackIdentifier) {
        if (binder == null) {
            throw new IllegalArgumentException("Binder must not be null");
        } else if (callback != null) {
            enforceNetworkSettingsPermission();
            if (this.mVerboseLoggingEnabled) {
                this.mLog.info("registerSoftApCallback uid=%").mo2069c((long) Binder.getCallingUid()).flush();
            }
            this.mWifiInjector.getClientModeImplHandler().post(new Runnable(binder, callback, callbackIdentifier) {
                /* class com.android.server.wifi.$$Lambda$WifiServiceImpl$WH1yXObMcpzajFG1KwwEOakTA7o */
                private final /* synthetic */ IBinder f$1;
                private final /* synthetic */ ISoftApCallback f$2;
                private final /* synthetic */ int f$3;

                {
                    this.f$1 = r2;
                    this.f$2 = r3;
                    this.f$3 = r4;
                }

                public final void run() {
                    WifiServiceImpl.this.lambda$registerSoftApCallback$3$WifiServiceImpl(this.f$1, this.f$2, this.f$3);
                }
            });
        } else {
            throw new IllegalArgumentException("Callback must not be null");
        }
    }

    public /* synthetic */ void lambda$registerSoftApCallback$3$WifiServiceImpl(IBinder binder, ISoftApCallback callback, int callbackIdentifier) {
        if (!this.mRegisteredSoftApCallbacks.add(binder, callback, callbackIdentifier)) {
            Log.e(TAG, "registerSoftApCallback: Failed to add callback");
            return;
        }
        try {
            callback.onStateChanged(this.mSoftApState, 0);
            callback.onNumClientsChanged(this.mSoftApNumClients);
        } catch (RemoteException e) {
            Log.e(TAG, "registerSoftApCallback: remote exception -- " + e);
        }
    }

    public void unregisterSoftApCallback(int callbackIdentifier) {
        enforceNetworkSettingsPermission();
        if (this.mVerboseLoggingEnabled) {
            this.mLog.info("unregisterSoftApCallback uid=%").mo2069c((long) Binder.getCallingUid()).flush();
        }
        this.mWifiInjector.getClientModeImplHandler().post(new Runnable(callbackIdentifier) {
            /* class com.android.server.wifi.$$Lambda$WifiServiceImpl$RmshU723eQairQK6HNmdtEWCoRA */
            private final /* synthetic */ int f$1;

            {
                this.f$1 = r2;
            }

            public final void run() {
                WifiServiceImpl.this.lambda$unregisterSoftApCallback$4$WifiServiceImpl(this.f$1);
            }
        });
    }

    public /* synthetic */ void lambda$unregisterSoftApCallback$4$WifiServiceImpl(int callbackIdentifier) {
        this.mRegisteredSoftApCallbacks.remove(callbackIdentifier);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void handleWifiApStateChange(int currentState, int previousState, int errorCode, String ifaceName, int mode) {
        Slog.d(TAG, "handleWifiApStateChange: currentState=" + currentState + " previousState=" + previousState + " errorCode= " + errorCode + " ifaceName=" + ifaceName + " mode=" + mode);
        this.mWifiApState = currentState;
        if (currentState == 14) {
            synchronized (this.mLocalOnlyHotspotRequests) {
                int errorToReport = 2;
                if (errorCode == 1) {
                    errorToReport = 1;
                }
                sendHotspotFailedMessageToAllLOHSRequestInfoEntriesLocked(errorToReport);
                lambda$updateInterfaceIpState$2$WifiServiceImpl(null, -1);
            }
        } else if (currentState == 10 || currentState == 11) {
            synchronized (this.mLocalOnlyHotspotRequests) {
                if (this.mIfaceIpModes.getOrDefault(ifaceName, -1).intValue() == 2) {
                    sendHotspotStoppedMessageToAllLOHSRequestInfoEntriesLocked();
                } else if (!isConcurrentLohsAndTetheringSupported()) {
                    sendHotspotFailedMessageToAllLOHSRequestInfoEntriesLocked(2);
                }
                updateInterfaceIpState(null, -1);
            }
        } else if (currentState == 13 && isWifiSharingEnabled() && getWifiEnabledState() == 3) {
            if (supportWifiSharingLite()) {
                Log.i(TAG, "setting indoor channel info when wifi turns on");
                this.mAsyncChannelExternalClientHandler.post(new Runnable() {
                    /* class com.android.server.wifi.$$Lambda$WifiServiceImpl$uaiVSB5I2WB5_IptoTmmmRZ1BxU */

                    public final void run() {
                        WifiServiceImpl.this.lambda$handleWifiApStateChange$5$WifiServiceImpl();
                    }
                });
            }
            if ("VZW".equals(CONFIGOPBRANDINGFORMOBILEAP) && getConnectionInfo("system").getNetworkId() != -1) {
                Toast.makeText(this.mContext, this.mContext.getResources().getString(17042610), 0).show();
            }
        }
    }

    public /* synthetic */ void lambda$handleWifiApStateChange$5$WifiServiceImpl() {
        setIndoorChannelsToDriver(true);
    }

    @GuardedBy({"mLocalOnlyHotspotRequests"})
    private void sendHotspotFailedMessageToAllLOHSRequestInfoEntriesLocked(int arg1) {
        for (LocalOnlyHotspotRequestInfo requestor : this.mLocalOnlyHotspotRequests.values()) {
            try {
                requestor.sendHotspotFailedMessage(arg1);
                requestor.unlinkDeathRecipient();
            } catch (RemoteException e) {
            }
        }
        this.mLocalOnlyHotspotRequests.clear();
    }

    @GuardedBy({"mLocalOnlyHotspotRequests"})
    private void sendHotspotStoppedMessageToAllLOHSRequestInfoEntriesLocked() {
        for (LocalOnlyHotspotRequestInfo requestor : this.mLocalOnlyHotspotRequests.values()) {
            try {
                requestor.sendHotspotStoppedMessage();
                requestor.unlinkDeathRecipient();
            } catch (RemoteException e) {
            }
        }
        this.mLocalOnlyHotspotRequests.clear();
    }

    @GuardedBy({"mLocalOnlyHotspotRequests"})
    private void sendHotspotStartedMessageToAllLOHSRequestInfoEntriesLocked() {
        for (LocalOnlyHotspotRequestInfo requestor : this.mLocalOnlyHotspotRequests.values()) {
            try {
                requestor.sendHotspotStartedMessage(this.mLocalOnlyHotspotConfig);
            } catch (RemoteException e) {
            }
        }
    }

    /* access modifiers changed from: package-private */
    @VisibleForTesting
    public void registerLOHSForTest(int pid, LocalOnlyHotspotRequestInfo request) {
        this.mLocalOnlyHotspotRequests.put(Integer.valueOf(pid), request);
    }

    /* JADX INFO: finally extract failed */
    public int startLocalOnlyHotspot(Messenger messenger, IBinder binder, String packageName) {
        int uid = Binder.getCallingUid();
        int pid = Binder.getCallingPid();
        if (this.mFrameworkFacade.inStorageManagerCryptKeeperBounce()) {
            Log.e(TAG, "Device still encrypted. Need to restart SystemServer.  Do not start hotspot.");
            return 2;
        } else if (enforceChangePermission(packageName) != 0) {
            return 2;
        } else {
            enforceLocationPermission(packageName, uid);
            long ident = Binder.clearCallingIdentity();
            try {
                if (this.mWifiPermissionsUtil.isLocationModeEnabled()) {
                    Binder.restoreCallingIdentity(ident);
                    if (this.mUserManager.hasUserRestriction("no_config_tethering")) {
                        return 4;
                    }
                    if (!this.mFrameworkFacade.isAppForeground(uid)) {
                        return 3;
                    }
                    this.mLog.info("startLocalOnlyHotspot uid=% pid=%").mo2069c((long) uid).mo2069c((long) pid).flush();
                    synchronized (this.mLocalOnlyHotspotRequests) {
                        int i = 1;
                        if (!isConcurrentLohsAndTetheringSupported() && this.mIfaceIpModes.contains(1)) {
                            this.mLog.info("Cannot start localOnlyHotspot when WiFi Tethering is active.").flush();
                            return 3;
                        } else if (this.mLocalOnlyHotspotRequests.get(Integer.valueOf(pid)) == null) {
                            LocalOnlyHotspotRequestInfo request = new LocalOnlyHotspotRequestInfo(binder, messenger, new LocalOnlyRequestorCallback());
                            if (this.mIfaceIpModes.contains(2)) {
                                try {
                                    this.mLog.trace("LOHS already up, trigger onStarted callback").flush();
                                    request.sendHotspotStartedMessage(this.mLocalOnlyHotspotConfig);
                                } catch (RemoteException e) {
                                    return 2;
                                }
                            } else if (this.mLocalOnlyHotspotRequests.isEmpty()) {
                                boolean is5Ghz = hasAutomotiveFeature(this.mContext) && this.mContext.getResources().getBoolean(17891607) && is5GhzSupported();
                                Context context = this.mContext;
                                if (!is5Ghz) {
                                    i = 0;
                                }
                                this.mLocalOnlyHotspotConfig = WifiApConfigStore.generateLocalOnlyHotspotConfig(context, i);
                                startSoftApInternal(this.mLocalOnlyHotspotConfig, 2);
                            }
                            this.mLocalOnlyHotspotRequests.put(Integer.valueOf(pid), request);
                            return 0;
                        } else {
                            this.mLog.trace("caller already has an active request").flush();
                            throw new IllegalStateException("Caller already has an active LocalOnlyHotspot request");
                        }
                    }
                } else {
                    throw new SecurityException("Location mode is not enabled.");
                }
            } catch (Throwable th) {
                Binder.restoreCallingIdentity(ident);
                throw th;
            }
        }
    }

    public void stopLocalOnlyHotspot() {
        int uid = Binder.getCallingUid();
        int pid = Binder.getCallingPid();
        this.mLog.info("stopLocalOnlyHotspot uid=% pid=%").mo2069c((long) uid).mo2069c((long) pid).flush();
        synchronized (this.mLocalOnlyHotspotRequests) {
            LocalOnlyHotspotRequestInfo requestInfo = this.mLocalOnlyHotspotRequests.get(Integer.valueOf(pid));
            if (requestInfo != null) {
                requestInfo.unlinkDeathRecipient();
                unregisterCallingAppAndStopLocalOnlyHotspot(requestInfo);
            }
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void unregisterCallingAppAndStopLocalOnlyHotspot(LocalOnlyHotspotRequestInfo request) {
        this.mLog.trace("unregisterCallingAppAndStopLocalOnlyHotspot pid=%").mo2069c((long) request.getPid()).flush();
        synchronized (this.mLocalOnlyHotspotRequests) {
            if (this.mLocalOnlyHotspotRequests.remove(Integer.valueOf(request.getPid())) == null) {
                this.mLog.trace("LocalOnlyHotspotRequestInfo not found to remove").flush();
                return;
            }
            if (this.mLocalOnlyHotspotRequests.isEmpty()) {
                this.mLocalOnlyHotspotConfig = null;
                lambda$updateInterfaceIpState$2$WifiServiceImpl(null, -1);
                long identity = Binder.clearCallingIdentity();
                try {
                    stopSoftApInternal(2);
                } finally {
                    Binder.restoreCallingIdentity(identity);
                }
            }
        }
    }

    public void startWatchLocalOnlyHotspot(Messenger messenger, IBinder binder) {
        enforceNetworkSettingsPermission();
        throw new UnsupportedOperationException("LocalOnlyHotspot is still in development");
    }

    public void stopWatchLocalOnlyHotspot() {
        enforceNetworkSettingsPermission();
        throw new UnsupportedOperationException("LocalOnlyHotspot is still in development");
    }

    public WifiConfiguration getWifiApConfiguration() {
        enforceAccessPermission();
        int uid = Binder.getCallingUid();
        if (this.mWifiPermissionsUtil.checkConfigOverridePermission(uid)) {
            this.mLog.info("getWifiApConfiguration uid=%").mo2069c((long) uid).flush();
            GeneralUtil.Mutable<WifiConfiguration> config = new GeneralUtil.Mutable<>();
            if (this.mWifiInjector.getClientModeImplHandler().runWithScissors(new Runnable(config) {
                /* class com.android.server.wifi.$$Lambda$WifiServiceImpl$wQMkTcLDx0gnVeocbEgY6YxMXj4 */
                private final /* synthetic */ GeneralUtil.Mutable f$1;

                {
                    this.f$1 = r2;
                }

                public final void run() {
                    WifiServiceImpl.this.lambda$getWifiApConfiguration$6$WifiServiceImpl(this.f$1);
                }
            }, IWCEventManager.autoDisconnectThreshold)) {
                return config.value;
            }
            Log.e(TAG, "Failed to post runnable to fetch ap config");
            return this.mWifiApConfigStore.getApConfiguration();
        }
        throw new SecurityException("App not allowed to read or update stored WiFi Ap config (uid = " + uid + ")");
    }

    public /* synthetic */ void lambda$getWifiApConfiguration$6$WifiServiceImpl(GeneralUtil.Mutable config) {
        config.value = (E) this.mWifiApConfigStore.getApConfiguration();
    }

    public boolean setWifiApConfiguration(WifiConfiguration wifiConfig, String packageName) {
        if (enforceChangePermission(packageName) != 0) {
            return false;
        }
        int uid = Binder.getCallingUid();
        if (this.mWifiPermissionsUtil.checkConfigOverridePermission(uid)) {
            this.mLog.info("setWifiApConfiguration uid=%").mo2069c((long) uid).flush();
            if (wifiConfig == null) {
                return false;
            }
            if (WifiApConfigStore.validateApWifiConfiguration(wifiConfig)) {
                this.mClientModeImplHandler.post(new Runnable(wifiConfig) {
                    /* class com.android.server.wifi.$$Lambda$WifiServiceImpl$CwkLJXQ3L_2xKpno1ZIbAEDHS4E */
                    private final /* synthetic */ WifiConfiguration f$1;

                    {
                        this.f$1 = r2;
                    }

                    public final void run() {
                        WifiServiceImpl.this.lambda$setWifiApConfiguration$7$WifiServiceImpl(this.f$1);
                    }
                });
                return true;
            }
            Slog.e(TAG, "Invalid WifiConfiguration");
            return false;
        }
        throw new SecurityException("App not allowed to read or update stored WiFi AP config (uid = " + uid + ")");
    }

    public /* synthetic */ void lambda$setWifiApConfiguration$7$WifiServiceImpl(WifiConfiguration wifiConfig) {
        this.mWifiApConfigStore.setApConfiguration(wifiConfig);
    }

    public void notifyUserOfApBandConversion(String packageName) {
        enforceNetworkSettingsPermission();
        if (this.mVerboseLoggingEnabled) {
            this.mLog.info("notifyUserOfApBandConversion uid=% packageName=%").mo2069c((long) Binder.getCallingUid()).mo2070c(packageName).flush();
        }
        this.mWifiApConfigStore.notifyUserOfApBandConversion(packageName);
    }

    public void setWifiApConfigurationToDefault() {
        enforceAccessPermission();
        this.mClientModeImplHandler.post(new Runnable() {
            /* class com.android.server.wifi.$$Lambda$WifiServiceImpl$_saHbViJC7rGQZ3hV0qlbytW3ao */

            public final void run() {
                WifiServiceImpl.this.lambda$setWifiApConfigurationToDefault$8$WifiServiceImpl();
            }
        });
    }

    public /* synthetic */ void lambda$setWifiApConfigurationToDefault$8$WifiServiceImpl() {
        this.mWifiApConfigStore.setWifiApConfigurationToDefault();
    }

    public String getWifiApInterfaceName() {
        enforceAccessPermission();
        try {
            return this.mWifiNative.getSoftApInterfaceName();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void bindNetdNativeService() {
        try {
            this.mNetdService = INetd.Stub.asInterface(ServiceManager.getService("netd"));
        } catch (Exception e) {
            Log.e(TAG, "Failed to bind service netd, error=" + e.getMessage());
        }
        if (this.mNetdService == null) {
            Log.e(TAG, "Can't bind service netd");
        }
    }

    public synchronized String runIptablesRulesCommand(String command) {
        String result;
        result = null;
        enforceNetworkStackPermission();
        if (this.mNetdService == null) {
            bindNetdNativeService();
        }
        try {
            result = this.mNetdService.runVpnRulesCommand(4, command);
        } catch (Exception e) {
            Log.e(TAG, "Failed to run command: cmd=" + command + ", error=" + e.getMessage());
        }
        return result;
    }

    public String semGetStationInfo(String mac) {
        if (getWifiApEnabledState() == 13) {
            return getStationInfo(mac);
        }
        return null;
    }

    public int getWifiApMaxClient() {
        int chipNum;
        String str;
        enforceAccessPermission();
        int featureNum = WifiApConfigStore.MAX_CLIENT;
        if (!MHSDBG || (str = SystemProperties.get("mhs.maxclient")) == null || str.equals("")) {
            String ss = Settings.Secure.getString(this.mContext.getContentResolver(), "wifi_ap_chip_maxclient");
            if (ss == null || ss.equals("na")) {
                return featureNum;
            }
            try {
                chipNum = Integer.parseInt(ss);
            } catch (Exception e) {
                Log.w(TAG, "exception : " + e);
                chipNum = featureNum;
            }
            int rInt = chipNum < featureNum ? chipNum : featureNum;
            int rInt2 = 10;
            if (rInt < 10) {
                rInt2 = rInt;
            }
            if (!MHSDBG) {
                return rInt2;
            }
            Log.w(TAG, "featureNum:" + featureNum + " chipNum:" + chipNum + " rInt:" + rInt2);
            return rInt2;
        }
        Log.w(TAG, "changed max client " + Integer.parseInt(str));
        return Integer.parseInt(str);
    }

    public boolean supportWifiAp5G() {
        enforceAccessPermission();
        if (MHSDBG) {
            String str = SystemProperties.get("mhs.5g");
            if (str.equals("1")) {
                return true;
            }
            if (str.equals("0")) {
                return false;
            }
        }
        String ss = Settings.Secure.getString(this.mContext.getContentResolver(), "wifi_ap_chip_support5g");
        return (ss == null || ss.equals("na")) ? SemCscFeature.getInstance().getBoolean(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_SUPPORTMOBILEAP5G, false) : Boolean.parseBoolean(ss) && isRegionFor5G();
    }

    public boolean supportWifiAp5GBasedOnCountry() {
        enforceAccessPermission();
        if (MHSDBG) {
            String str = SystemProperties.get("mhs.5gcountry");
            if (str.equals("1")) {
                return true;
            }
            if (str.equals("0")) {
                return false;
            }
        }
        String ss = Settings.Secure.getString(this.mContext.getContentResolver(), "wifi_ap_chip_support5g_baseon_country");
        String ss_5g = Settings.Secure.getString(this.mContext.getContentResolver(), "wifi_ap_chip_support5g");
        if (ss == null || ss.equals("na")) {
            return false;
        }
        return Boolean.parseBoolean(ss_5g) && Boolean.parseBoolean(ss) && isRegionFor5GCountry();
    }

    public int semGetWifiApChannel() {
        enforceAccessPermission();
        if (getWifiApEnabledState() == 13) {
            return getWifiApChannel();
        }
        return this.mWifiApConfigStore.getApConfiguration().apChannel;
    }

    public boolean supportWifiSharingLite() {
        return this.mWifiInjector.getSemWifiApChipInfo().supportWifiSharingLite();
    }

    public boolean setWifiSharingEnabled(boolean enable) {
        enforceAccessPermission();
        if (!supportWifiSharing()) {
            Log.i(TAG, "Failed: Does not support Wi-Fi Sharing.");
            return false;
        }
        Message msg = new Message();
        msg.what = 77;
        Bundle args = new Bundle();
        args.putString("feature", "MHWS");
        long ident = Binder.clearCallingIdentity();
        if (enable) {
            try {
                Settings.Secure.putInt(this.mContext.getContentResolver(), "wifi_ap_wifi_sharing", 1);
                args.putString("extra", "ON");
                getWifiApEnabledState();
            } catch (Throwable th) {
                Binder.restoreCallingIdentity(ident);
                throw th;
            }
        } else {
            Settings.Secure.putInt(this.mContext.getContentResolver(), "wifi_ap_wifi_sharing", 0);
            args.putString("extra", "OFF");
        }
        Log.i(TAG, "Wi-Fi Sharing mode : " + enable + " wifiApState: " + this.mWifiApState + " getWifiEnabledState  : " + getWifiEnabledState());
        msg.obj = args;
        callSECApi(msg);
        this.mContext.sendBroadcast(new Intent("com.samsung.intent.action.UPDATE_OPTIONS_MENU"));
        Binder.restoreCallingIdentity(ident);
        return true;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean isMobileApOn() {
        int wifiApState = getWifiApEnabledState();
        if (wifiApState == 13 || wifiApState == 12) {
            Log.i(TAG, "Mobile Ap is in enabled state");
            return true;
        }
        Log.i(TAG, "Mobile AP is in disabled state");
        return false;
    }

    public boolean setProvisionSuccess(boolean set) {
        Log.i(TAG, "Provision variable set to " + set);
        if (set) {
            this.isProvisionSuccessful = 1;
        } else {
            this.isProvisionSuccessful = 2;
        }
        CharSequence dateTime = DateFormat.format("yy/MM/dd kk:mm:ss ", System.currentTimeMillis());
        SemSoftapConfig semSoftapConfig = this.mWifiInjector.getSemSoftapConfig();
        semSoftapConfig.addMHSDumpLog(((Object) dateTime) + " isProvisionSuccessful (" + this.isProvisionSuccessful + ") : \n");
        return true;
    }

    public int getProvisionSuccess() {
        if (OpBrandingLoader.Vendor.VZW == mOpBranding && SystemProperties.get("Provisioning.disable").equals("1")) {
            return 1;
        }
        Log.i(TAG, "isProvisioning successful  " + this.isProvisionSuccessful);
        return this.isProvisionSuccessful;
    }

    public boolean isScanAlwaysAvailable() {
        enforceAccessPermission();
        if (this.mVerboseLoggingEnabled) {
            this.mLog.info("isScanAlwaysAvailable uid=%").mo2069c((long) Binder.getCallingUid()).flush();
        }
        return this.mSettingsStore.isScanAlwaysAvailable();
    }

    public boolean disconnect(String packageName) {
        if (enforceChangePermission(packageName) != 0) {
            return false;
        }
        if (!isTargetSdkLessThanQOrPrivileged(packageName, Binder.getCallingPid(), Binder.getCallingUid())) {
            this.mLog.info("disconnect not allowed for uid=%").mo2069c((long) Binder.getCallingUid()).flush();
            return false;
        }
        this.mLog.info("disconnect uid=%").mo2069c((long) Binder.getCallingUid()).flush();
        this.mClientModeImpl.report(100, ReportUtil.getReportDataForWifiManagerApi(-1, "disconnect", this.mContext.getPackageManager().getNameForUid(Binder.getCallingUid()), getPackageName(Binder.getCallingPid())));
        this.mClientModeImpl.disconnectCommand(Binder.getCallingUid(), 2);
        return true;
    }

    public boolean reconnect(String packageName) {
        if (enforceChangePermission(packageName) != 0) {
            return false;
        }
        if (!isTargetSdkLessThanQOrPrivileged(packageName, Binder.getCallingPid(), Binder.getCallingUid())) {
            this.mLog.info("reconnect not allowed for uid=%").mo2069c((long) Binder.getCallingUid()).flush();
            return false;
        }
        this.mLog.info("reconnect uid=%").mo2069c((long) Binder.getCallingUid()).flush();
        this.mClientModeImpl.reconnectCommand(new WorkSource(Binder.getCallingUid()));
        return true;
    }

    public boolean reassociate(String packageName) {
        if (enforceChangePermission(packageName) != 0) {
            return false;
        }
        if (!isTargetSdkLessThanQOrPrivileged(packageName, Binder.getCallingPid(), Binder.getCallingUid())) {
            this.mLog.info("reassociate not allowed for uid=%").mo2069c((long) Binder.getCallingUid()).flush();
            return false;
        }
        this.mLog.info("reassociate uid=%").mo2069c((long) Binder.getCallingUid()).flush();
        this.mClientModeImpl.reassociateCommand();
        return true;
    }

    public long getSupportedFeatures() {
        enforceAccessPermission();
        if (this.mVerboseLoggingEnabled) {
            this.mLog.info("getSupportedFeatures uid=%").mo2069c((long) Binder.getCallingUid()).flush();
        }
        return getSupportedFeaturesInternal();
    }

    private String getSupportedFeaturesHumanReadable() {
        StringBuilder sb = new StringBuilder();
        long supportedFeatures = getSupportedFeatures();
        sb.append(supportedFeatures);
        sb.append(" ");
        if ((1 & supportedFeatures) != 0) {
            sb.append("[INFRA]");
        }
        if ((2 & supportedFeatures) != 0) {
            sb.append("[INFRA_5G]");
        }
        if ((4 & supportedFeatures) != 0) {
            sb.append("[PASSPOINT]");
        }
        if ((8 & supportedFeatures) != 0) {
            sb.append("[P2P]");
        }
        if ((16 & supportedFeatures) != 0) {
            sb.append("[AP]");
        }
        if ((32 & supportedFeatures) != 0) {
            sb.append("[SCANNER]");
        }
        if ((64 & supportedFeatures) != 0) {
            sb.append("[AWARE]");
        }
        if ((128 & supportedFeatures) != 0) {
            sb.append("[D2_RTT]");
        }
        if ((256 & supportedFeatures) != 0) {
            sb.append("[D2AP_RTT]");
        }
        if ((512 & supportedFeatures) != 0) {
            sb.append("[BATCH_SCAN]");
        }
        if ((1024 & supportedFeatures) != 0) {
            sb.append("[PNO]");
        }
        if ((2048 & supportedFeatures) != 0) {
            sb.append("[DUAL_STA]");
        }
        if ((4096 & supportedFeatures) != 0) {
            sb.append("[TDLS]");
        }
        if ((8192 & supportedFeatures) != 0) {
            sb.append("[TDLS_OFFCH]");
        }
        if ((16384 & supportedFeatures) != 0) {
            sb.append("[EPR]");
        }
        if ((32768 & supportedFeatures) != 0) {
            sb.append("[WIFI_SHARING]");
        }
        if ((65536 & supportedFeatures) != 0) {
            sb.append("[LINK_LAYER_STATS]");
        }
        if ((131072 & supportedFeatures) != 0) {
            sb.append("[LOG]");
        }
        if ((262144 & supportedFeatures) != 0) {
            sb.append("[PNO_HAL]");
        }
        if ((524288 & supportedFeatures) != 0) {
            sb.append("[RSSI_MON]");
        }
        if ((1048576 & supportedFeatures) != 0) {
            sb.append("[MKEEP_ALIVE]");
        }
        if ((2097152 & supportedFeatures) != 0) {
            sb.append("[NDO]");
        }
        if ((4194304 & supportedFeatures) != 0) {
            sb.append("[CTL_TP]");
        }
        if ((8388608 & supportedFeatures) != 0) {
            sb.append("[CTL_ROAM]");
        }
        if ((16777216 & supportedFeatures) != 0) {
            sb.append("[IE_WHITE]");
        }
        if ((33554432 & supportedFeatures) != 0) {
            sb.append("[RANDMAC_SCAN]");
        }
        if ((67108864 & supportedFeatures) != 0) {
            sb.append("[TX_POWER_LIMIT]");
        }
        if ((134217728 & supportedFeatures) != 0) {
            sb.append("[SAE]");
        }
        if ((268435456 & supportedFeatures) != 0) {
            sb.append("[SUITE_B]");
        }
        if ((536870912 & supportedFeatures) != 0) {
            sb.append("[OWE]");
        }
        if ((1073741824 & supportedFeatures) != 0) {
            sb.append("[LOW_LETENCY]");
        }
        if ((-2147483648L & supportedFeatures) != 0) {
            sb.append("[DPP]");
        }
        if ((4294967296L & supportedFeatures) != 0) {
            sb.append("[P2P_RANDOM_MAC]");
        }
        return sb.toString();
    }

    public void requestActivityInfo(ResultReceiver result) {
        Bundle bundle = new Bundle();
        if (this.mVerboseLoggingEnabled) {
            this.mLog.info("requestActivityInfo uid=%").mo2069c((long) Binder.getCallingUid()).flush();
        }
        bundle.putParcelable("controller_activity", reportActivityInfo());
        result.send(0, bundle);
    }

    /* JADX INFO: Multiple debug info for r7v6 double: [D('txCurrent' double), D('rxIdleCurrent' double)] */
    public WifiActivityEnergyInfo reportActivityInfo() {
        WifiActivityEnergyInfo energyInfo;
        long[] txTimePerLevel;
        double rxIdleCurrent;
        enforceAccessPermission();
        if (this.mVerboseLoggingEnabled) {
            this.mLog.info("reportActivityInfo uid=%").mo2069c((long) Binder.getCallingUid()).flush();
        }
        if ((getSupportedFeatures() & 65536) == 0) {
            return null;
        }
        WifiActivityEnergyInfo energyInfo2 = null;
        AsyncChannel asyncChannel = this.mClientModeImplChannel;
        if (asyncChannel != null && !this.semIsShutdown) {
            WifiLinkLayerStats stats = this.mClientModeImpl.syncGetLinkLayerStats(asyncChannel);
            if (stats != null) {
                double rxIdleCurrent2 = this.mPowerProfile.getAveragePower("wifi.controller.idle");
                double rxCurrent = this.mPowerProfile.getAveragePower("wifi.controller.rx");
                double txCurrent = this.mPowerProfile.getAveragePower("wifi.controller.tx");
                double voltage = this.mPowerProfile.getAveragePower("wifi.controller.voltage") / 1000.0d;
                long rxIdleTime = (long) ((stats.on_time - stats.tx_time) - stats.rx_time);
                if (stats.tx_time_per_level != null) {
                    long[] txTimePerLevel2 = new long[stats.tx_time_per_level.length];
                    int i = 0;
                    while (i < txTimePerLevel2.length) {
                        txTimePerLevel2[i] = (long) stats.tx_time_per_level[i];
                        i++;
                        energyInfo2 = energyInfo2;
                    }
                    txTimePerLevel = txTimePerLevel2;
                } else {
                    txTimePerLevel = new long[0];
                }
                long energyUsed = (long) (((((double) stats.tx_time) * txCurrent) + (((double) stats.rx_time) * rxCurrent) + (((double) rxIdleTime) * rxIdleCurrent2)) * voltage);
                if (rxIdleTime < 0 || stats.on_time < 0 || stats.tx_time < 0 || stats.rx_time < 0 || stats.on_time_scan < 0 || energyUsed < 0) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(" rxIdleCur=" + rxIdleCurrent2);
                    sb.append(" rxCur=" + rxCurrent);
                    StringBuilder sb2 = new StringBuilder();
                    sb2.append(" txCur=");
                    rxIdleCurrent = txCurrent;
                    sb2.append(rxIdleCurrent);
                    sb.append(sb2.toString());
                    sb.append(" voltage=" + voltage);
                    sb.append(" on_time=" + stats.on_time);
                    sb.append(" tx_time=" + stats.tx_time);
                    sb.append(" tx_time_per_level=" + Arrays.toString(txTimePerLevel));
                    sb.append(" rx_time=" + stats.rx_time);
                    sb.append(" rxIdleTime=" + rxIdleTime);
                    sb.append(" scan_time=" + stats.on_time_scan);
                    sb.append(" energy=" + energyUsed);
                    Log.d(TAG, " reportActivityInfo: " + sb.toString());
                } else {
                    rxIdleCurrent = txCurrent;
                }
                energyInfo = new WifiActivityEnergyInfo(this.mClock.getElapsedSinceBootMillis(), 3, (long) stats.tx_time, txTimePerLevel, (long) stats.rx_time, (long) stats.on_time_scan, rxIdleTime, energyUsed);
            } else {
                energyInfo = null;
            }
            if (energyInfo == null || !energyInfo.isValid()) {
                return null;
            }
            return energyInfo;
        } else if (this.semIsShutdown) {
            Slog.e(TAG, "Skip reportActivityInfo in Shutdown");
            return null;
        } else {
            Slog.e(TAG, "mClientModeImplChannel is not initialized");
            return null;
        }
    }

    public ParceledListSlice<WifiConfiguration> getConfiguredNetworks(String packageName) {
        return semGetConfiguredNetworks(0, packageName);
    }

    public ParceledListSlice<WifiConfiguration> semGetConfiguredNetworks(int from, String packageName) {
        enforceAccessPermission();
        int callingUid = Binder.getCallingUid();
        if (!(callingUid == 2000 || callingUid == 0)) {
            long ident = Binder.clearCallingIdentity();
            try {
                this.mWifiPermissionsUtil.enforceCanAccessScanResults(packageName, callingUid);
            } catch (SecurityException e) {
                Slog.e(TAG, "Permission violation - getConfiguredNetworks not allowed for uid=" + callingUid + ", packageName=" + packageName + ", reason=" + e);
                return new ParceledListSlice<>(new ArrayList());
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }
        boolean isTargetSdkLessThanQOrPrivileged = isTargetSdkLessThanQOrPrivileged(packageName, Binder.getCallingPid(), callingUid);
        boolean isCarrierApp = true;
        if (this.mWifiInjector.makeTelephonyManager().checkCarrierPrivilegesForPackageAnyPhone(packageName) != 1) {
            isCarrierApp = false;
        }
        if (isTargetSdkLessThanQOrPrivileged || isCarrierApp) {
            if (this.mVerboseLoggingEnabled) {
                this.mLog.info("getConfiguredNetworks uid=%").mo2069c((long) callingUid).flush();
            }
            int targetConfigUid = -1;
            if (isPrivileged(getCallingPid(), callingUid) || isDeviceOrProfileOwner(callingUid)) {
                targetConfigUid = 1010;
            } else if (isCarrierApp) {
                targetConfigUid = callingUid;
            }
            AsyncChannel asyncChannel = this.mClientModeImplChannel;
            if (asyncChannel != null) {
                List<WifiConfiguration> configs = this.mClientModeImpl.syncGetConfiguredNetworks(callingUid, asyncChannel, from, targetConfigUid);
                if (configs == null) {
                    return null;
                }
                if (isTargetSdkLessThanQOrPrivileged) {
                    return new ParceledListSlice<>(configs);
                }
                List<WifiConfiguration> creatorConfigs = new ArrayList<>();
                for (WifiConfiguration config : configs) {
                    if (config.creatorUid == callingUid) {
                        creatorConfigs.add(config);
                    }
                }
                return new ParceledListSlice<>(creatorConfigs);
            }
            Slog.e(TAG, "mClientModeImplChannel is not initialized");
            return null;
        }
        this.mLog.info("getConfiguredNetworks not allowed for uid=%").mo2069c((long) callingUid).flush();
        return new ParceledListSlice<>(new ArrayList());
    }

    public WifiConfiguration getSpecificNetwork(int netID) {
        enforceAccessPermission();
        AsyncChannel asyncChannel = this.mClientModeImplChannel;
        if (asyncChannel != null) {
            return this.mClientModeImpl.syncGetSpecificNetwork(asyncChannel, netID);
        }
        Slog.e(TAG, "mClientModeImplChannel is not initialized");
        return null;
    }

    public ParceledListSlice<WifiConfiguration> getPrivilegedConfiguredNetworks(String packageName) {
        enforceReadCredentialPermission();
        enforceAccessPermission();
        int callingUid = Binder.getCallingUid();
        long ident = Binder.clearCallingIdentity();
        try {
            this.mWifiPermissionsUtil.enforceCanAccessScanResults(packageName, callingUid);
            Binder.restoreCallingIdentity(ident);
            if (this.mVerboseLoggingEnabled) {
                this.mLog.info("getPrivilegedConfiguredNetworks uid=%").mo2069c((long) callingUid).flush();
            }
            AsyncChannel asyncChannel = this.mClientModeImplChannel;
            if (asyncChannel != null) {
                List<WifiConfiguration> configs = this.mClientModeImpl.syncGetPrivilegedConfiguredNetwork(asyncChannel);
                if (configs != null) {
                    return new ParceledListSlice<>(configs);
                }
            } else {
                Slog.e(TAG, "mClientModeImplChannel is not initialized");
            }
            return null;
        } catch (SecurityException e) {
            Slog.e(TAG, "Permission violation - getPrivilegedConfiguredNetworks not allowed for uid=" + callingUid + ", packageName=" + packageName + ", reason=" + e);
            Binder.restoreCallingIdentity(ident);
            return null;
        } catch (Throwable th) {
            Binder.restoreCallingIdentity(ident);
            throw th;
        }
    }

    public Map<String, Map<Integer, List<ScanResult>>> getAllMatchingFqdnsForScanResults(List<ScanResult> scanResults) {
        if (isSettingsOrSuw(Binder.getCallingPid(), Binder.getCallingUid())) {
            if (this.mVerboseLoggingEnabled) {
                this.mLog.info("getMatchingPasspointConfigurations uid=%").mo2069c((long) Binder.getCallingUid()).flush();
            }
            return this.mClientModeImpl.syncGetAllMatchingFqdnsForScanResults(scanResults, this.mClientModeImplChannel);
        }
        throw new SecurityException("WifiService: Permission denied");
    }

    public Map<OsuProvider, List<ScanResult>> getMatchingOsuProviders(List<ScanResult> scanResults) {
        if (isSettingsOrSuw(Binder.getCallingPid(), Binder.getCallingUid())) {
            if (this.mVerboseLoggingEnabled) {
                this.mLog.info("getMatchingOsuProviders uid=%").mo2069c((long) Binder.getCallingUid()).flush();
            }
            return this.mClientModeImpl.syncGetMatchingOsuProviders(scanResults, this.mClientModeImplChannel);
        }
        throw new SecurityException("WifiService: Permission denied");
    }

    public Map<OsuProvider, PasspointConfiguration> getMatchingPasspointConfigsForOsuProviders(List<OsuProvider> osuProviders) {
        if (isSettingsOrSuw(Binder.getCallingPid(), Binder.getCallingUid())) {
            if (this.mVerboseLoggingEnabled) {
                this.mLog.info("getMatchingPasspointConfigsForOsuProviders uid=%").mo2069c((long) Binder.getCallingUid()).flush();
            }
            if (osuProviders != null) {
                return this.mClientModeImpl.syncGetMatchingPasspointConfigsForOsuProviders(osuProviders, this.mClientModeImplChannel);
            }
            Log.e(TAG, "Attempt to retrieve Passpoint configuration with null osuProviders");
            return new HashMap();
        }
        throw new SecurityException("WifiService: Permission denied");
    }

    public List<WifiConfiguration> getWifiConfigsForPasspointProfiles(List<String> fqdnList) {
        if (isSettingsOrSuw(Binder.getCallingPid(), Binder.getCallingUid())) {
            if (this.mVerboseLoggingEnabled) {
                this.mLog.info("getWifiConfigsForPasspointProfiles uid=%").mo2069c((long) Binder.getCallingUid()).flush();
            }
            if (fqdnList != null) {
                return this.mClientModeImpl.syncGetWifiConfigsForPasspointProfiles(fqdnList, this.mClientModeImplChannel);
            }
            Log.e(TAG, "Attempt to retrieve WifiConfiguration with null fqdn List");
            return new ArrayList();
        }
        throw new SecurityException("WifiService: Permission denied");
    }

    public int addOrUpdateNetwork(WifiConfiguration config, String packageName) {
        return semAddOrUpdateNetwork(0, config, packageName);
    }

    public int semAddOrUpdateNetwork(int from, WifiConfiguration config, String packageName) {
        if (enforceChangePermission(packageName) != 0) {
            return -1;
        }
        if (!this.mWifiPermissionsUtil.checkFactoryTestPermission(Binder.getCallingUid()) && !isTargetSdkLessThanQOrPrivileged(packageName, Binder.getCallingPid(), Binder.getCallingUid())) {
            this.mLog.info("addOrUpdateNetwork not allowed for uid=%").mo2069c((long) Binder.getCallingUid()).flush();
            return -1;
        }
        this.mLog.info("addOrUpdateNetwork uid=%").mo2069c((long) Binder.getCallingUid()).flush();
        if (config == null) {
            Slog.e(TAG, "bad network configuration");
            return -1;
        }
        if (from == -1000) {
            if (Binder.getCallingUid() != 1000) {
                Slog.e(TAG, "Operation only allowed to system uid");
                return -1;
            }
            config.semSamsungSpecificFlags.set(4);
        }
        this.mWifiMetrics.incrementNumAddOrUpdateNetworkCalls();
        boolean z = true;
        if (config.isPasspoint()) {
            PasspointConfiguration passpointConfig = PasspointProvider.convertFromWifiConfig(config);
            if (passpointConfig.getCredential() == null) {
                Slog.e(TAG, "Missing credential for Passpoint profile");
                return -1;
            }
            X509Certificate[] x509Certificates = null;
            if (config.enterpriseConfig.getCaCertificate() != null) {
                x509Certificates = new X509Certificate[]{config.enterpriseConfig.getCaCertificate()};
            }
            passpointConfig.getCredential().setCaCertificates(x509Certificates);
            passpointConfig.getCredential().setClientCertificateChain(config.enterpriseConfig.getClientCertificateChain());
            passpointConfig.getCredential().setClientPrivateKey(config.enterpriseConfig.getClientPrivateKey());
            if (addOrUpdatePasspointConfiguration(passpointConfig, packageName)) {
                return 0;
            }
            Slog.e(TAG, "Failed to add Passpoint profile");
            return -1;
        }
        Slog.i("addOrUpdateNetwork", " uid = " + Integer.toString(Binder.getCallingUid()) + " SSID " + config.SSID + " nid=" + Integer.toString(config.networkId));
        if (config.networkId == -1) {
            config.creatorUid = Binder.getCallingUid();
        } else {
            config.lastUpdateUid = Binder.getCallingUid();
        }
        if (this.mClientModeImplChannel != null) {
            boolean hasPassword = false;
            if (config.preSharedKey != null) {
                if (config.preSharedKey.length() <= 8) {
                    z = false;
                }
                hasPassword = z;
            } else if (!(config.wepKeys == null || config.wepKeys[0] == null)) {
                if (config.wepKeys[0].length() < 4) {
                    z = false;
                }
                hasPassword = z;
            }
            int netId = this.mClientModeImpl.syncAddOrUpdateNetwork(this.mClientModeImplChannel, from, config);
            this.mClientModeImpl.report(105, ReportUtil.getReportDataForWifiManagerAddOrUpdateApi(netId, hasPassword, "addOrUpdateNetwork", this.mContext.getPackageManager().getNameForUid(Binder.getCallingUid()), getPackageName(Binder.getCallingPid())));
            return netId;
        }
        Slog.e(TAG, "mClientModeImplChannel is not initialized");
        return -1;
    }

    public static void verifyCert(X509Certificate caCert) throws GeneralSecurityException, IOException {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        CertPathValidator validator = CertPathValidator.getInstance(CertPathValidator.getDefaultType());
        CertPath path = factory.generateCertPath(Arrays.asList(caCert));
        KeyStore ks = KeyStore.getInstance("AndroidCAStore");
        ks.load(null, null);
        PKIXParameters params = new PKIXParameters(ks);
        params.setRevocationEnabled(false);
        validator.validate(path, params);
    }

    public boolean removeNetwork(int netId, String packageName) {
        return semRemoveNetwork(0, netId, packageName);
    }

    /* JADX INFO: finally extract failed */
    public boolean semRemoveNetwork(int from, int netId, String packageName) {
        if (enforceChangePermission(packageName) != 0) {
            return false;
        }
        if (!isTargetSdkLessThanQOrPrivileged(packageName, Binder.getCallingPid(), Binder.getCallingUid())) {
            this.mLog.info("removeNetwork not allowed for uid=%").mo2069c((long) Binder.getCallingUid()).flush();
            return false;
        }
        this.mLog.info("removeNetwork uid=%").mo2069c((long) Binder.getCallingUid()).flush();
        if (from == -1000 && Binder.getCallingUid() != 1000) {
            Slog.e(TAG, "Operation only allowed to system uid");
            return false;
        } else if (this.mClientModeImplChannel != null) {
            this.mClientModeImpl.report(102, ReportUtil.getReportDataForWifiManagerApi(netId, "removeNetwork", this.mContext.getPackageManager().getNameForUid(Binder.getCallingUid()), getPackageName(Binder.getCallingPid())));
            if (this.mIWCMonitor != null) {
                int uid = Binder.getCallingUid();
                Bundle args = new Bundle();
                args.putString("package", packageName);
                this.mIWCMonitor.sendMessage(151752, uid, netId, args);
                Log.e(TAG, "uid: " + uid + " nid: " + netId + " packageName: " + packageName);
            }
            long ident = Binder.clearCallingIdentity();
            try {
                if (this.mAutoWifiController != null) {
                    this.mAutoWifiController.forgetNetwork(netId);
                }
                Binder.restoreCallingIdentity(ident);
                return this.mClientModeImpl.syncRemoveNetwork(this.mClientModeImplChannel, from, netId);
            } catch (Throwable th) {
                Binder.restoreCallingIdentity(ident);
                throw th;
            }
        } else {
            Slog.e(TAG, "mClientModeImplChannel is not initialized");
            return false;
        }
    }

    public boolean enableNetwork(int netId, boolean disableOthers, String packageName) {
        if (enforceChangePermission(packageName) != 0) {
            return false;
        }
        if (!isTargetSdkLessThanQOrPrivileged(packageName, Binder.getCallingPid(), Binder.getCallingUid())) {
            this.mLog.info("enableNetwork not allowed for uid=%").mo2069c((long) Binder.getCallingUid()).flush();
            return false;
        }
        this.mLog.info("enableNetwork uid=% disableOthers=%").mo2069c((long) Binder.getCallingUid()).mo2071c(disableOthers).flush();
        this.mWifiMetrics.incrementNumEnableNetworkCalls();
        if (this.mClientModeImplChannel != null) {
            if (this.mIWCMonitor != null) {
                int uid = Binder.getCallingUid();
                Bundle args = new Bundle();
                args.putString("package", packageName);
                this.mIWCMonitor.sendMessage(151753, uid, netId, args);
                Log.e(TAG, "uid: " + uid + " nid: " + netId + " packageName: " + packageName);
            }
            return this.mClientModeImpl.syncEnableNetwork(this.mClientModeImplChannel, netId, disableOthers);
        }
        Slog.e(TAG, "mClientModeImplChannel is not initialized");
        return false;
    }

    public boolean disableNetwork(int netId, String packageName) {
        if (enforceChangePermission(packageName) != 0) {
            return false;
        }
        if (!isTargetSdkLessThanQOrPrivileged(packageName, Binder.getCallingPid(), Binder.getCallingUid())) {
            this.mLog.info("disableNetwork not allowed for uid=%").mo2069c((long) Binder.getCallingUid()).flush();
            return false;
        }
        this.mLog.info("disableNetwork uid=%").mo2069c((long) Binder.getCallingUid()).flush();
        if (this.mClientModeImplChannel != null) {
            this.mClientModeImpl.report(101, ReportUtil.getReportDataForWifiManagerApi(netId, "disableNetwork", this.mContext.getPackageManager().getNameForUid(Binder.getCallingUid()), getPackageName(Binder.getCallingPid())));
            return this.mClientModeImpl.syncDisableNetwork(this.mClientModeImplChannel, netId);
        }
        Slog.e(TAG, "mClientModeImplChannel is not initialized");
        return false;
    }

    public WifiInfo getConnectionInfo(String callingPackage) {
        enforceAccessPermission();
        int uid = Binder.getCallingUid();
        if (this.mVerboseLoggingEnabled) {
            this.mLog.info("getConnectionInfo uid=%").mo2069c((long) uid).flush();
        }
        long ident = Binder.clearCallingIdentity();
        try {
            WifiInfo result = this.mClientModeImpl.syncRequestConnectionInfo();
            boolean hideDefaultMacAddress = true;
            boolean hideBssidSsidAndNetworkId = true;
            try {
                if (this.mWifiInjector.getWifiPermissionsWrapper().getLocalMacAddressPermission(uid) == 0) {
                    hideDefaultMacAddress = false;
                } else {
                    Log.e(TAG, uid + " has no permission about LOCAL_MAC_ADDRESS");
                }
                this.mWifiPermissionsUtil.enforceCanAccessScanResults(callingPackage, uid);
                hideBssidSsidAndNetworkId = false;
            } catch (RemoteException e) {
                Log.e(TAG, "Error checking receiver permission", e);
            } catch (SecurityException e2) {
            }
            if (hideDefaultMacAddress) {
                result.setMacAddress("02:00:00:00:00:00");
            }
            if (hideBssidSsidAndNetworkId) {
                result.setBSSID("02:00:00:00:00:00");
                result.setSSID(WifiSsid.createFromHex((String) null));
                result.setNetworkId(-1);
            }
            if (this.mVerboseLoggingEnabled && (hideBssidSsidAndNetworkId || hideDefaultMacAddress)) {
                WifiLog wifiLog = this.mLog;
                wifiLog.mo2095v("getConnectionInfo: hideBssidSsidAndNetworkId=" + hideBssidSsidAndNetworkId + ", hideDefaultMacAddress=" + hideDefaultMacAddress);
            }
            return result;
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    public NetworkInfo getNetworkInfo() {
        enforceAccessNetworkPermission();
        return this.mClientModeImpl.syncGetNetworkInfo();
    }

    public List<ScanResult> getScanResults(String callingPackage) {
        enforceAccessPermission();
        int uid = Binder.getCallingUid();
        long ident = Binder.clearCallingIdentity();
        if (this.mVerboseLoggingEnabled) {
            this.mLog.info("getScanResults uid=%").mo2069c((long) uid).flush();
        }
        try {
            this.mWifiPermissionsUtil.enforceCanAccessScanResults(callingPackage, uid);
            List<ScanResult> scanResults = new ArrayList<>();
            if (!this.mWifiInjector.getClientModeImplHandler().runWithScissors(new Runnable(scanResults) {
                /* class com.android.server.wifi.$$Lambda$WifiServiceImpl$k6jYr00gO87l56TLIgUv5KN7ZJs */
                private final /* synthetic */ List f$1;

                {
                    this.f$1 = r2;
                }

                public final void run() {
                    WifiServiceImpl.this.lambda$getScanResults$9$WifiServiceImpl(this.f$1);
                }
            }, IWCEventManager.autoDisconnectThreshold)) {
                Log.e(TAG, "Failed to post runnable to fetch scan results");
                return new ArrayList();
            }
            Binder.restoreCallingIdentity(ident);
            return scanResults;
        } catch (SecurityException e) {
            Slog.e(TAG, "Permission violation - getScanResults not allowed for uid=" + uid + ", packageName=" + callingPackage + ", reason=" + e);
            return new ArrayList();
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    public /* synthetic */ void lambda$getScanResults$9$WifiServiceImpl(List scanResults) {
        scanResults.addAll(this.mScanRequestProxy.getScanResults());
    }

    public boolean addOrUpdatePasspointConfiguration(PasspointConfiguration config, String packageName) {
        if (enforceChangePermission(packageName) != 0) {
            return false;
        }
        this.mLog.info("addorUpdatePasspointConfiguration uid=%").mo2069c((long) Binder.getCallingUid()).flush();
        return this.mClientModeImpl.syncAddOrUpdatePasspointConfig(this.mClientModeImplChannel, config, Binder.getCallingUid(), packageName);
    }

    public boolean removePasspointConfiguration(String fqdn, String packageName) {
        int uid = Binder.getCallingUid();
        if (this.mWifiPermissionsUtil.checkNetworkSettingsPermission(uid) || this.mWifiPermissionsUtil.checkNetworkCarrierProvisioningPermission(uid)) {
            this.mLog.info("removePasspointConfiguration uid=%").mo2069c((long) Binder.getCallingUid()).flush();
            return this.mClientModeImpl.syncRemovePasspointConfig(this.mClientModeImplChannel, fqdn);
        } else if (this.mWifiPermissionsUtil.isTargetSdkLessThan(packageName, 29)) {
            return false;
        } else {
            throw new SecurityException("WifiService: Permission denied");
        }
    }

    public List<PasspointConfiguration> getPasspointConfigurations(String packageName) {
        int uid = Binder.getCallingUid();
        if (!"SamsungPasspoint".equals(packageName)) {
            this.mAppOps.checkPackage(uid, packageName);
        }
        if (this.mWifiPermissionsUtil.checkNetworkSettingsPermission(uid) || this.mWifiPermissionsUtil.checkDiagnosticsProviderPermission(uid) || this.mWifiPermissionsUtil.checkNetworkSetupWizardPermission(uid)) {
            if (this.mVerboseLoggingEnabled) {
                this.mLog.info("getPasspointConfigurations uid=%").mo2069c((long) Binder.getCallingUid()).flush();
            }
            return this.mClientModeImpl.syncGetPasspointConfigs(this.mClientModeImplChannel);
        } else if (this.mWifiPermissionsUtil.isTargetSdkLessThan(packageName, 29)) {
            return new ArrayList();
        } else {
            throw new SecurityException("WifiService: Permission denied");
        }
    }

    public void queryPasspointIcon(long bssid, String fileName) {
        enforceAccessPermission();
        this.mLog.info("queryPasspointIcon uid=%").mo2069c((long) Binder.getCallingUid()).flush();
        this.mClientModeImpl.syncQueryPasspointIcon(this.mClientModeImplChannel, bssid, fileName);
    }

    public int matchProviderWithCurrentNetwork(String fqdn) {
        this.mLog.info("matchProviderWithCurrentNetwork uid=%").mo2069c((long) Binder.getCallingUid()).flush();
        return this.mClientModeImpl.matchProviderWithCurrentNetwork(this.mClientModeImplChannel, fqdn);
    }

    public void deauthenticateNetwork(long holdoff, boolean ess) {
        this.mLog.info("deauthenticateNetwork uid=%").mo2069c((long) Binder.getCallingUid()).flush();
        this.mClientModeImpl.deauthenticateNetwork(this.mClientModeImplChannel, holdoff, ess);
    }

    public void setCountryCode(String countryCode) {
        Slog.i(TAG, "WifiService trying to set country code to " + countryCode);
        if (this.semIsShutdown) {
            Slog.i(TAG, "Skip setCountryCode in Shutdown");
            return;
        }
        enforceConnectivityInternalPermission();
        this.mLog.info("setCountryCode uid=%").mo2069c((long) Binder.getCallingUid()).flush();
        long token = Binder.clearCallingIdentity();
        this.mCountryCode.setCountryCode(countryCode);
        Binder.restoreCallingIdentity(token);
    }

    public String getCountryCode() {
        enforceConnectivityInternalPermission();
        if (this.mVerboseLoggingEnabled) {
            this.mLog.info("getCountryCode uid=%").mo2069c((long) Binder.getCallingUid()).flush();
        }
        return this.mCountryCode.getCountryCode();
    }

    public boolean isDualBandSupported() {
        if (this.mVerboseLoggingEnabled) {
            this.mLog.info("isDualBandSupported uid=%").mo2069c((long) Binder.getCallingUid()).flush();
        }
        return this.mContext.getResources().getBoolean(17891597);
    }

    private int getMaxApInterfacesCount() {
        return this.mContext.getResources().getInteger(17695014);
    }

    private boolean isConcurrentLohsAndTetheringSupported() {
        return getMaxApInterfacesCount() >= 2;
    }

    public boolean needs5GHzToAnyApBandConversion() {
        enforceNetworkSettingsPermission();
        if (this.mVerboseLoggingEnabled) {
            this.mLog.info("needs5GHzToAnyApBandConversion uid=%").mo2069c((long) Binder.getCallingUid()).flush();
        }
        return this.mContext.getResources().getBoolean(17891596);
    }

    @Deprecated
    public DhcpInfo getDhcpInfo() {
        enforceAccessPermission();
        if (this.mVerboseLoggingEnabled) {
            this.mLog.info("getDhcpInfo uid=%").mo2069c((long) Binder.getCallingUid()).flush();
        }
        DhcpResults dhcpResults = this.mClientModeImpl.syncGetDhcpResults();
        DhcpInfo info = new DhcpInfo();
        if (dhcpResults.ipAddress != null && (dhcpResults.ipAddress.getAddress() instanceof Inet4Address)) {
            info.ipAddress = NetworkUtils.inetAddressToInt((Inet4Address) dhcpResults.ipAddress.getAddress());
        }
        if (dhcpResults.gateway != null) {
            info.gateway = NetworkUtils.inetAddressToInt((Inet4Address) dhcpResults.gateway);
        }
        int dnsFound = 0;
        Iterator it = dhcpResults.dnsServers.iterator();
        while (it.hasNext()) {
            InetAddress dns = (InetAddress) it.next();
            if (dns instanceof Inet4Address) {
                if (dnsFound == 0) {
                    info.dns1 = NetworkUtils.inetAddressToInt((Inet4Address) dns);
                } else {
                    info.dns2 = NetworkUtils.inetAddressToInt((Inet4Address) dns);
                }
                dnsFound++;
                if (dnsFound > 1) {
                    break;
                }
            }
        }
        Inet4Address serverAddress = dhcpResults.serverAddress;
        if (serverAddress != null) {
            info.serverAddress = NetworkUtils.inetAddressToInt(serverAddress);
        }
        info.leaseDuration = dhcpResults.leaseDuration;
        return info;
    }

    /* access modifiers changed from: package-private */
    public class TdlsTaskParams {
        public boolean enable;
        public String remoteIpAddress;

        TdlsTaskParams() {
        }
    }

    class TdlsTask extends AsyncTask<TdlsTaskParams, Integer, Integer> {
        TdlsTask() {
        }

        /* access modifiers changed from: protected */
        public Integer doInBackground(TdlsTaskParams... params) {
            TdlsTaskParams param = params[0];
            String remoteIpAddress = param.remoteIpAddress.trim();
            boolean enable = param.enable;
            String macAddress = null;
            BufferedReader reader = null;
            try {
                BufferedReader reader2 = new BufferedReader(new FileReader("/proc/net/arp"));
                reader2.readLine();
                while (true) {
                    String line = reader2.readLine();
                    if (line == null) {
                        break;
                    }
                    String[] tokens = line.split("[ ]+");
                    if (tokens.length >= 6) {
                        String ip = tokens[0];
                        String mac = tokens[3];
                        if (remoteIpAddress.equals(ip)) {
                            macAddress = mac;
                            break;
                        }
                    }
                }
                if (macAddress == null) {
                    Slog.w(WifiServiceImpl.TAG, "Did not find remoteAddress {" + remoteIpAddress + "} in /proc/net/arp");
                } else {
                    WifiServiceImpl.this.enableTdlsWithMacAddress(macAddress, enable);
                }
                try {
                    reader2.close();
                } catch (IOException e) {
                }
            } catch (FileNotFoundException e2) {
                Slog.e(WifiServiceImpl.TAG, "Could not open /proc/net/arp to lookup mac address");
                if (0 != 0) {
                    reader.close();
                }
            } catch (IOException e3) {
                Slog.e(WifiServiceImpl.TAG, "Could not read /proc/net/arp to lookup mac address");
                if (0 != 0) {
                    reader.close();
                }
            } catch (Throwable th) {
                if (0 != 0) {
                    try {
                        reader.close();
                    } catch (IOException e4) {
                    }
                }
                throw th;
            }
            return 0;
        }
    }

    public void enableTdls(String remoteAddress, boolean enable) {
        if (remoteAddress != null) {
            this.mLog.info("enableTdls uid=% enable=%").mo2069c((long) Binder.getCallingUid()).mo2071c(enable).flush();
            TdlsTaskParams params = new TdlsTaskParams();
            params.remoteIpAddress = remoteAddress;
            params.enable = enable;
            new TdlsTask().execute(params);
            return;
        }
        throw new IllegalArgumentException("remoteAddress cannot be null");
    }

    public void enableTdlsWithMacAddress(String remoteMacAddress, boolean enable) {
        this.mLog.info("enableTdlsWithMacAddress uid=% enable=%").mo2069c((long) Binder.getCallingUid()).mo2071c(enable).flush();
        if (remoteMacAddress != null) {
            this.mClientModeImpl.enableTdls(remoteMacAddress, enable);
            return;
        }
        throw new IllegalArgumentException("remoteMacAddress cannot be null");
    }

    public boolean setRoamTrigger(int roamTrigger) {
        enforceChangePermission();
        if (isFmcPackage()) {
            return this.mClientModeImpl.syncSetRoamTrigger(roamTrigger);
        }
        Log.d(TAG, "setRoamTrigger Invalid package");
        return false;
    }

    public int getRoamTrigger() {
        enforceAccessPermission();
        if (isFmcPackage()) {
            return this.mClientModeImpl.syncGetRoamTrigger();
        }
        Log.d(TAG, "getRoamTrigger Invalid package");
        return -1;
    }

    public boolean setRoamDelta(int roamDelta) {
        enforceChangePermission();
        if (isFmcPackage()) {
            return this.mClientModeImpl.syncSetRoamDelta(roamDelta);
        }
        Log.d(TAG, "setRoamDelta Invalid package");
        return false;
    }

    public int getRoamDelta() {
        enforceAccessPermission();
        if (isFmcPackage()) {
            return this.mClientModeImpl.syncGetRoamDelta();
        }
        Log.d(TAG, "getRoamDelta Invalid package");
        return -1;
    }

    public boolean setRoamScanPeriod(int roamScanPeriod) {
        enforceChangePermission();
        if (isFmcPackage()) {
            return this.mClientModeImpl.syncSetRoamScanPeriod(roamScanPeriod);
        }
        Log.d(TAG, "setRoamScanPeriod Invalid package");
        return false;
    }

    public int getRoamScanPeriod() {
        enforceAccessPermission();
        if (isFmcPackage()) {
            return this.mClientModeImpl.syncGetRoamScanPeriod();
        }
        Log.d(TAG, "getRoamScanPeriod Invalid package");
        return -1;
    }

    public boolean setRoamBand(int band) {
        enforceChangePermission();
        if (isFmcPackage()) {
            return this.mClientModeImpl.syncSetRoamBand(band);
        }
        Log.d(TAG, "setRoamBand Invalid package");
        return false;
    }

    public int getRoamBand() {
        enforceAccessPermission();
        if (isFmcPackage()) {
            return this.mClientModeImpl.syncGetRoamBand();
        }
        Log.d(TAG, "getRoamBand Invalid package");
        return -1;
    }

    public boolean setCountryRev(String countryRev) {
        enforceChangePermission();
        if (isFmcPackage()) {
            return this.mClientModeImpl.syncSetCountryRev(countryRev);
        }
        Log.d(TAG, "setCountryRev Invalid package");
        return false;
    }

    public String getCountryRev() {
        enforceAccessPermission();
        if (isFmcPackage()) {
            return this.mClientModeImpl.syncGetCountryRev();
        }
        Log.d(TAG, "getCountryRev Invalid package");
        return null;
    }

    public boolean semIsCarrierNetworkSaved() {
        enforceAccessPermission();
        int pid = Binder.getCallingPid();
        Log.d(TAG, "semIsCarrierNetworkSaved requested by pid=" + pid);
        return this.mWifiInjector.getWifiConfigManager().isCarrierNetworkSaved();
    }

    public Messenger getWifiServiceMessenger(String packageName) {
        enforceAccessPermission();
        if (enforceChangePermission(packageName) == 0) {
            this.mLog.info("getWifiServiceMessenger uid=%").mo2069c((long) Binder.getCallingUid()).flush();
            return new Messenger(this.mAsyncChannelExternalClientHandler);
        }
        throw new SecurityException("Could not create wifi service messenger");
    }

    public void disableEphemeralNetwork(String SSID, String packageName) {
        this.mContext.enforceCallingOrSelfPermission("android.permission.CHANGE_WIFI_STATE", TAG);
        if (!isPrivileged(Binder.getCallingPid(), Binder.getCallingUid())) {
            this.mLog.info("disableEphemeralNetwork not allowed for uid=%").mo2069c((long) Binder.getCallingUid()).flush();
            return;
        }
        this.mClientModeImpl.report(101, ReportUtil.getReportDataForWifiManagerApi(SSID, "disableEphemeralNetwork", this.mContext.getPackageManager().getNameForUid(Binder.getCallingUid()), getPackageName(Binder.getCallingPid())));
        this.mLog.info("disableEphemeralNetwork uid=%").mo2069c((long) Binder.getCallingUid()).flush();
        this.mClientModeImpl.disableEphemeralNetwork(SSID);
    }

    private void registerForScanModeChange() {
        this.mFrameworkFacade.registerContentObserver(this.mContext, Settings.Global.getUriFor("wifi_scan_always_enabled"), false, new ContentObserver(null) {
            /* class com.android.server.wifi.WifiServiceImpl.C047312 */

            public void onChange(boolean selfChange) {
                WifiServiceImpl.this.mSettingsStore.handleWifiScanAlwaysAvailableToggled();
                WifiServiceImpl.this.mWifiController.sendMessage(155655);
            }
        });
    }

    private void registerForBroadcasts() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("android.intent.action.USER_PRESENT");
        intentFilter.addAction("android.intent.action.USER_REMOVED");
        intentFilter.addAction("android.bluetooth.adapter.action.CONNECTION_STATE_CHANGED");
        if (!CSC_DISABLE_EMERGENCYCALLBACK_TRANSITION) {
            intentFilter.addAction("android.intent.action.EMERGENCY_CALLBACK_MODE_CHANGED");
        }
        intentFilter.addAction("android.os.action.DEVICE_IDLE_MODE_CHANGED");
        if (WIFI_STOP_SCAN_FOR_ETWS) {
            intentFilter.addAction("android.provider.Telephony.SMS_CB_WIFI_RECEIVED");
        }
        if (this.mContext.getResources().getBoolean(17891613)) {
            intentFilter.addAction("android.intent.action.EMERGENCY_CALL_STATE_CHANGED");
        }
        this.mContext.registerReceiver(this.mReceiver, intentFilter);
        IntentFilter intentFilter2 = new IntentFilter();
        intentFilter2.addAction("android.intent.action.PACKAGE_FULLY_REMOVED");
        intentFilter2.addDataScheme("package");
        this.mContext.registerReceiver(new BroadcastReceiver() {
            /* class com.android.server.wifi.WifiServiceImpl.C047413 */

            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals("android.intent.action.PACKAGE_FULLY_REMOVED")) {
                    int uid = intent.getIntExtra("android.intent.extra.UID", -1);
                    Uri uri = intent.getData();
                    if (uid != -1 && uri != null) {
                        String pkgName = uri.getSchemeSpecificPart();
                        WifiServiceImpl.this.mClientModeImpl.removeAppConfigs(pkgName, uid);
                        WifiServiceImpl.this.mWifiInjector.getClientModeImplHandler().post(new Runnable(pkgName, uid) {
                            /* class com.android.server.wifi.$$Lambda$WifiServiceImpl$13$TZg8stEmQwznKN8G0YRxHFEmD4 */
                            private final /* synthetic */ String f$1;
                            private final /* synthetic */ int f$2;

                            {
                                this.f$1 = r2;
                                this.f$2 = r3;
                            }

                            public final void run() {
                                WifiServiceImpl.C047413.this.lambda$onReceive$0$WifiServiceImpl$13(this.f$1, this.f$2);
                            }
                        });
                    }
                }
            }

            public /* synthetic */ void lambda$onReceive$0$WifiServiceImpl$13(String pkgName, int uid) {
                WifiServiceImpl.this.mScanRequestProxy.clearScanRequestTimestampsForApp(pkgName, uid);
                WifiServiceImpl.this.mWifiNetworkSuggestionsManager.removeApp(pkgName);
                WifiServiceImpl.this.mClientModeImpl.removeNetworkRequestUserApprovedAccessPointsForApp(pkgName);
                WifiServiceImpl.this.mWifiInjector.getPasspointManager().removePasspointProviderWithPackage(pkgName);
            }
        }, intentFilter2);
        WifiGuiderManagementService wifiGuiderManagementService = this.mWifiGuiderManagementService;
        if (wifiGuiderManagementService != null) {
            wifiGuiderManagementService.registerForBroadcastsForWifiGuider();
        }
    }

    /* JADX DEBUG: Multi-variable search result rejected for r8v0, resolved type: com.android.server.wifi.WifiServiceImpl */
    /* JADX WARN: Multi-variable type inference failed */
    public void onShellCommand(FileDescriptor in, FileDescriptor out, FileDescriptor err, String[] args, ShellCallback callback, ResultReceiver resultReceiver) {
        new WifiShellCommand(this.mWifiInjector).exec(this, in, out, err, args, callback, resultReceiver);
    }

    private void dumpMHS(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println();
        pw.println("MHS dump ----- start -----\n");
        pw.println("mCSCRegion:" + this.mCSCRegion + " mCountryIso:" + this.mCountryIso + " isRegionFor5GCountry:" + isRegionFor5GCountry() + " isRegionFor5G:" + isRegionFor5G());
        pw.println(this.mWifiInjector.getWifiApConfigStore().getDumpLogs());
        pw.println();
        pw.println(this.mWifiInjector.getSemSoftapConfig().getDumpLogs());
        pw.println();
        pw.println(this.mWifiInjector.getSemWifiApClientInfo().getDumpLogs());
        pw.println();
        pw.println("Provision Success:" + this.isProvisionSuccessful);
        pw.println();
        pw.println("isWifiSharingEnabled:" + isWifiSharingEnabled());
        pw.println("MHS Clients\n" + getWifiApStaList());
        pw.println();
        pw.println(this.mWifiInjector.getSemWifiApChipInfo().getDumpLogs());
        pw.println();
        pw.println(this.mWifiInjector.getHostapdHAL().getDumpLogs());
        pw.println();
        this.mWifiInjector.getSemWifiApTimeOutImpl().dump(fd, pw, args);
        pw.println();
        pw.println("--api");
        pw.println("5G:" + supportWifiAp5G());
        pw.println("5g_Country:" + supportWifiAp5GBasedOnCountry());
        pw.println("maxClient:" + getWifiApMaxClient());
        pw.println("wifisharing:" + supportWifiSharing());
        pw.println("wifisharinglite:" + supportWifiSharingLite());
        pw.println();
        String[] provisionApp = this.mContext.getResources().getStringArray(17236154);
        if (provisionApp != null) {
            pw.println("--provisioning apps length:" + provisionApp.length);
            if (provisionApp.length == 2) {
                pw.println(provisionApp[0] + "," + provisionApp[1]);
            }
        }
        pw.println("provision csc : " + SemCscFeature.getInstance().getString(CscFeatureTagSetting.TAG_CSCFEATURE_SETTING_CONFIGMOBILEHOTSPOTPROVISIONAPP));
        if (!FactoryTest.isFactoryBinary()) {
            pw.println();
            this.mSemWifiApSmartLocalLog.dump(fd, pw, args);
            pw.println();
            pw.println(this.mSemWifiApSmartUtil.getDumpLogs());
            pw.println();
            pw.println();
            pw.println(this.mSemWifiApSmartBleScanner.getDumpLogs());
            pw.println();
        }
        pw.println("MHS dump ----- end -----\n");
        pw.println();
        pw.println();
    }

    /* access modifiers changed from: protected */
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (this.mContext.checkCallingOrSelfPermission("android.permission.DUMP") != 0) {
            pw.println("Permission Denial: can't dump WifiService from from pid=" + Binder.getCallingPid() + ", uid=" + Binder.getCallingUid());
        } else if (args != null && args.length > 0 && WifiMetrics.PROTO_DUMP_ARG.equals(args[0])) {
            this.mClientModeImpl.updateWifiMetrics();
            this.mWifiMetrics.dump(fd, pw, args);
        } else if (args != null && args.length > 0 && "ipclient".equals(args[0])) {
            String[] ipClientArgs = new String[(args.length - 1)];
            System.arraycopy(args, 1, ipClientArgs, 0, ipClientArgs.length);
            this.mClientModeImpl.dumpIpClient(fd, pw, ipClientArgs);
        } else if (args != null && args.length > 0 && WifiScoreReport.DUMP_ARG.equals(args[0])) {
            WifiScoreReport wifiScoreReport = this.mClientModeImpl.getWifiScoreReport();
            if (wifiScoreReport != null) {
                wifiScoreReport.dump(fd, pw, args);
            }
        } else if (args == null || args.length <= 0 || !WifiScoreCard.DUMP_ARG.equals(args[0])) {
            WifiNative wifiNative = this.mWifiNative;
            wifiNative.saveFwDump(wifiNative.getClientInterfaceName());
            this.mClientModeImpl.updateLinkLayerStatsRssiAndScoreReport();
            pw.println("Wi-Fi is " + this.mClientModeImpl.syncGetWifiStateByName());
            StringBuilder sb = new StringBuilder();
            sb.append("Verbose logging is ");
            sb.append(this.mVerboseLoggingEnabled ? "on" : "off");
            pw.println(sb.toString());
            pw.println("Stay-awake conditions: " + this.mFacade.getIntegerSetting(this.mContext, "stay_on_while_plugged_in", 0));
            pw.println("mInIdleMode " + this.mInIdleMode);
            pw.println("mScanPending " + this.mScanPending);
            if (WifiChipInfo.getInstance().isReady()) {
                StringBuilder sb2 = new StringBuilder();
                sb2.append("Wi-Fi vendor: ");
                WifiChipInfo.getInstance();
                sb2.append(WifiChipInfo.getChipsetNameHumanReadable());
                pw.println(sb2.toString());
            }
            pw.println("Supported feature: " + ((Object) getSupportedFeaturesHumanReadable()));
            pw.println("Wi-Fi api call history:");
            pw.println(this.mHistoricalDumpLogs.toString());
            pw.println("Wi-Fi control history from provider:");
            WifiControlHistoryProvider.dumpControlHistory(this.mContext, pw);
            this.mWifiController.dump(fd, pw, args);
            this.mSettingsStore.dump(fd, pw, args);
            this.mWifiTrafficPoller.dump(fd, pw, args);
            pw.println();
            pw.println("Locks held:");
            this.mWifiLockManager.dump(pw);
            pw.println();
            this.mWifiMulticastLockManager.dump(pw);
            pw.println();
            this.mActiveModeWarden.dump(fd, pw, args);
            pw.println();
            dumpMHS(fd, pw, args);
            pw.println();
            WifiConnectivityMonitor wifiConnectivityMonitor = this.mWifiConnectivityMonitor;
            if (wifiConnectivityMonitor != null) {
                wifiConnectivityMonitor.dump(fd, pw, args);
                pw.println();
            }
            this.mClientModeImpl.dump(fd, pw, args);
            pw.println();
            this.mWifiInjector.getClientModeImplHandler().runWithScissors(new Runnable(pw) {
                /* class com.android.server.wifi.$$Lambda$WifiServiceImpl$6O288GTXWxRi1mRcsSKWvlxyM */
                private final /* synthetic */ PrintWriter f$1;

                {
                    this.f$1 = r2;
                }

                public final void run() {
                    WifiServiceImpl.this.lambda$dump$11$WifiServiceImpl(this.f$1);
                }
            }, IWCEventManager.autoDisconnectThreshold);
            this.mClientModeImpl.updateWifiMetrics();
            this.mWifiMetrics.dump(fd, pw, args);
            pw.println();
            this.mWifiInjector.getClientModeImplHandler().runWithScissors(new Runnable(fd, pw, args) {
                /* class com.android.server.wifi.$$Lambda$WifiServiceImpl$yGt3SPTezzBQMBZ4yhBs2rIHZbQ */
                private final /* synthetic */ FileDescriptor f$1;
                private final /* synthetic */ PrintWriter f$2;
                private final /* synthetic */ String[] f$3;

                {
                    this.f$1 = r2;
                    this.f$2 = r3;
                    this.f$3 = r4;
                }

                public final void run() {
                    WifiServiceImpl.this.lambda$dump$12$WifiServiceImpl(this.f$1, this.f$2, this.f$3);
                }
            }, IWCEventManager.autoDisconnectThreshold);
            this.mWifiBackupRestore.dump(fd, pw, args);
            pw.println();
            pw.println("ScoringParams: settings put global wifi_score_params " + this.mWifiInjector.getScoringParams());
            pw.println();
            WifiScoreReport wifiScoreReport2 = this.mClientModeImpl.getWifiScoreReport();
            if (wifiScoreReport2 != null) {
                pw.println("WifiScoreReport:");
                wifiScoreReport2.dump(fd, pw, args);
            }
            pw.println();
            SarManager sarManager = this.mWifiInjector.getSarManager();
            if (sarManager != null) {
                sarManager.dump(fd, pw, args);
            }
            pw.println();
            this.mScanRequestProxy.dump(fd, pw, args);
            pw.println();
            this.mDefaultApController.dump(fd, pw, args);
            this.mPasspointDefaultProvider.dump(fd, pw, args);
            IWCMonitor iWCMonitor = this.mIWCMonitor;
            if (iWCMonitor != null) {
                iWCMonitor.dump(fd, pw, args);
            }
            AutoWifiController autoWifiController = this.mAutoWifiController;
            if (autoWifiController != null) {
                autoWifiController.dump(fd, pw, args);
            }
            pw.println("WifiGuiderPackageMonitor:");
            pw.println(this.mWifiGuiderPackageMonitor.dump());
            pw.println();
            SemWifiProfileAndQoSProvider semWifiProfileAndQoSProvider = this.mSemQoSProfileShareProvider;
            if (semWifiProfileAndQoSProvider != null) {
                semWifiProfileAndQoSProvider.dump(fd, pw, args);
            }
            this.mWifiInjector.getWifiLowLatency().dump(fd, pw, args);
        } else {
            this.mWifiInjector.getClientModeImplHandler().runWithScissors(new Runnable(pw) {
                /* class com.android.server.wifi.$$Lambda$WifiServiceImpl$gVnPSF1YHmk5RfQ06XHneTR6cwA */
                private final /* synthetic */ PrintWriter f$1;

                {
                    this.f$1 = r2;
                }

                public final void run() {
                    WifiServiceImpl.this.lambda$dump$10$WifiServiceImpl(this.f$1);
                }
            }, IWCEventManager.autoDisconnectThreshold);
        }
    }

    public /* synthetic */ void lambda$dump$10$WifiServiceImpl(PrintWriter pw) {
        WifiScoreCard wifiScoreCard = this.mWifiInjector.getWifiScoreCard();
        if (wifiScoreCard != null) {
            pw.println(wifiScoreCard.getNetworkListBase64(true));
        }
    }

    public /* synthetic */ void lambda$dump$11$WifiServiceImpl(PrintWriter pw) {
        WifiScoreCard wifiScoreCard = this.mWifiInjector.getWifiScoreCard();
        if (wifiScoreCard != null) {
            pw.println("WifiScoreCard:");
            pw.println(wifiScoreCard.getNetworkListBase64(true));
        }
    }

    public /* synthetic */ void lambda$dump$12$WifiServiceImpl(FileDescriptor fd, PrintWriter pw, String[] args) {
        this.mWifiNetworkSuggestionsManager.dump(fd, pw, args);
        pw.println();
    }

    private void mapIndoorCountryToChannel() {
        File indoorChannelFile = new File("/vendor/etc/wifi/indoorchannel.info");
        Log.d(TAG, "mIndoorChannelFilePath:/vendor/etc/wifi/indoorchannel.info");
        Log.d(TAG, "Indoor channel filename:" + indoorChannelFile.getAbsolutePath() + "indoorChannelFile.exists() :" + indoorChannelFile.exists());
        BufferedReader br = null;
        if (indoorChannelFile.exists()) {
            try {
                Log.d(TAG, "Reading the file for indoor channel/vendor/etc/wifi/indoorchannel.info");
                BufferedReader br2 = new BufferedReader(new FileReader(indoorChannelFile));
                String line = br2.readLine();
                if (line != null && line.split(" ").length > 1) {
                    String str = line.split(" ")[1];
                }
                while (true) {
                    String line2 = br2.readLine();
                    if (line2 != null) {
                        this.mCountryChannelList.put(line2.substring(0, 2), line2.substring(3));
                    } else {
                        try {
                            br2.close();
                            return;
                        } catch (IOException e) {
                            e.printStackTrace();
                            return;
                        }
                    }
                }
            } catch (Exception e2) {
                Log.e(TAG, "Indoor channel file access fail:/vendor/etc/wifi/indoorchannel.inforead from hardcoded channels");
                initializeChannelInfo();
                e2.printStackTrace();
                if (0 != 0) {
                    br.close();
                }
            } catch (Throwable th) {
                if (0 != 0) {
                    try {
                        br.close();
                    } catch (IOException e3) {
                        e3.printStackTrace();
                    }
                }
                throw th;
            }
        } else {
            Log.e(TAG, "Indoor channel file does not exist:/vendor/etc/wifi/indoorchannel.info,read from hardcoded channels");
            initializeChannelInfo();
        }
    }

    public void initializeChannelInfo() {
        String[] countryList;
        Log.d(TAG, "Initialize the indoor channel info");
        this.mCountryChannel.put("IN", "36 40 44 48 52 56 60 64 149 153 157 161");
        this.mCountryChannel.put("KR,BB,VE,VN,AR,UY,CL,CA,CO,PA", "36 40 44 48");
        this.mCountryChannel.put("BO", "52 56 60 64 149 153 157 161 165");
        this.mCountryChannel.put("QA", "149 153 157 161 165");
        this.mCountryChannel.put("GH,GG,GR,GL,ZA,NL,NO,NF,NZ,NU,DK,DE,LV,RO,LU,LY,LT,LI,MK,IM,MC,MA,ME,MV,MT,BH,VA,BE,BA,BG,BR,SA,SM,PM,RS,SE,CH,ES,SK,SI,AE,IS,IE,AL,EE,GB,IO,OM,AU,AT,UA,IL,EG,IT,JP,JE,GE,CN,GI,CZ,CC,CL,CA,CC,CO,KW,CK,HR,CY,TH,TR,TK,FO,PT,PL,FR,TF,PF,FJ,FI,PN,HM,HU,HK", "36 40 44 48 52 56 60 64");
        for (Map.Entry<String, String> entry : this.mCountryChannel.entrySet()) {
            String value = entry.getValue();
            Log.d(TAG, "Country = " + entry.getKey() + ", channels = " + value);
            for (String str : entry.getKey().split(",")) {
                this.mCountryChannelList.put(str, value);
            }
        }
    }

    public void setIndoorChannelsToDriver(boolean toBeSet) {
        String countryCode = this.mCountryCode.getCountryCode();
        if (countryCode == null || !this.mCountryChannelList.containsKey(countryCode)) {
            Log.e(TAG, "Country doesn't support indoor channel.");
            return;
        }
        String channelDetailsToSendToDriver = "";
        int channelLen = 0;
        if (toBeSet) {
            Log.d(TAG, "Setting indoor channel info in driver");
            channelDetailsToSendToDriver = this.mCountryChannelList.get(countryCode);
            if (this.mClientModeImpl.syncGetWifiState() != 3) {
                Log.e(TAG, "Wifi is off. So, not setting indoor channels to driver.");
                return;
            }
            channelLen = channelDetailsToSendToDriver.split(" ").length;
            Log.d(TAG, "Number of indoor channels = " + channelLen);
            Log.d(TAG, "Indoor channel details(<ch1> <ch2> ...) : " + channelDetailsToSendToDriver);
        }
        Log.d(TAG, "sending cmd SEC_COMMAND_ID_SET_INDOOR_CHANNELS to WiFiNative to set/reset indoor ch");
        if (this.mWifiNative != null) {
            String status = toBeSet ? "set" : "reset";
            if (this.mWifiNative.setIndoorChannels(channelLen, channelDetailsToSendToDriver)) {
                Log.d(TAG, "Indoor channels " + status + " successfully");
                return;
            }
            Log.e(TAG, "Error! Indoor channels not " + status);
        }
    }

    private boolean getIndoorStatus() {
        String countryCode = this.mCountryCode.getCountryCode();
        NetworkInfo info = getNetworkInfo();
        Log.d(TAG, "Network info details : " + info.getDetailedState());
        Log.d(TAG, "Device country code : " + countryCode);
        if (countryCode == null || !this.mCountryChannelList.containsKey(countryCode)) {
            Log.e(TAG, "Country doesn't support indoor channel.");
            return false;
        } else if (!isWifiConnected()) {
            Log.e(TAG, "Device is not connected to any WIFI network. Disconnected Flag:");
            return false;
        } else {
            String[] channelList = this.mCountryChannelList.get(countryCode).split(" ");
            WifiInfo currentNetwork = getConnectionInfo("system");
            Log.d(TAG, "Current network frequency : " + currentNetwork.getFrequency());
            int channelNum = mFreq2ChannelNum.get(currentNetwork.getFrequency()).intValue();
            Log.d(TAG, "Channel number :" + channelNum + " for frequency : " + currentNetwork.getFrequency());
            for (String str : channelList) {
                if (str.equals(Integer.toString(channelNum))) {
                    Log.d(TAG, "STA connected to indoor channel. Take the user consent for turning on MHS");
                    return true;
                }
            }
            return false;
        }
    }

    private boolean isWifiConnected() {
        boolean result = false;
        ConnectivityManager connectivity = (ConnectivityManager) this.mContext.getSystemService("connectivity");
        if (connectivity != null) {
            result = connectivity.getNetworkInfo(1).isConnected();
        }
        if (!result) {
            Log.d(TAG, "isWifiConnected1 :" + result);
            WifiConfiguration config = getSpecificNetwork(this.mClientModeImpl.getWifiInfo().getNetworkId());
            if (config != null && config.isCaptivePortal && !config.isAuthenticated) {
                result = true;
            }
        }
        Log.d(TAG, "isWifiConnected :" + result);
        return result;
    }

    public boolean acquireWifiLock(IBinder binder, int lockMode, String tag, WorkSource ws) {
        this.mLog.info("acquireWifiLock uid=% lockMode=%").mo2069c((long) Binder.getCallingUid()).mo2069c((long) lockMode).flush();
        this.mContext.enforceCallingOrSelfPermission("android.permission.WAKE_LOCK", null);
        WorkSource updatedWs = (ws == null || ws.isEmpty()) ? new WorkSource(Binder.getCallingUid()) : ws;
        if (WifiLockManager.isValidLockMode(lockMode)) {
            GeneralUtil.Mutable<Boolean> lockSuccess = new GeneralUtil.Mutable<>();
            if (this.mWifiInjector.getClientModeImplHandler().runWithScissors(new Runnable(lockSuccess, lockMode, tag, binder, updatedWs) {
                /* class com.android.server.wifi.$$Lambda$WifiServiceImpl$F7cdGp39EzoV7O1ZRFZdY4BHqAA */
                private final /* synthetic */ GeneralUtil.Mutable f$1;
                private final /* synthetic */ int f$2;
                private final /* synthetic */ String f$3;
                private final /* synthetic */ IBinder f$4;
                private final /* synthetic */ WorkSource f$5;

                {
                    this.f$1 = r2;
                    this.f$2 = r3;
                    this.f$3 = r4;
                    this.f$4 = r5;
                    this.f$5 = r6;
                }

                public final void run() {
                    WifiServiceImpl.this.lambda$acquireWifiLock$13$WifiServiceImpl(this.f$1, this.f$2, this.f$3, this.f$4, this.f$5);
                }
            }, IWCEventManager.autoDisconnectThreshold)) {
                return lockSuccess.value.booleanValue();
            }
            Log.e(TAG, "Failed to post runnable to acquireWifiLock");
            return false;
        }
        throw new IllegalArgumentException("lockMode =" + lockMode);
    }

    public /* synthetic */ void lambda$acquireWifiLock$13$WifiServiceImpl(GeneralUtil.Mutable lockSuccess, int lockMode, String tag, IBinder binder, WorkSource updatedWs) {
        lockSuccess.value = (E) Boolean.valueOf(this.mWifiLockManager.acquireWifiLock(lockMode, tag, binder, updatedWs));
    }

    public void updateWifiLockWorkSource(IBinder binder, WorkSource ws) {
        this.mLog.info("updateWifiLockWorkSource uid=%").mo2069c((long) Binder.getCallingUid()).flush();
        this.mContext.enforceCallingOrSelfPermission("android.permission.UPDATE_DEVICE_STATS", null);
        if (!this.mWifiInjector.getClientModeImplHandler().runWithScissors(new Runnable(binder, (ws == null || ws.isEmpty()) ? new WorkSource(Binder.getCallingUid()) : ws) {
            /* class com.android.server.wifi.$$Lambda$WifiServiceImpl$sNPYXSwij4arkVRGosq0lZKuz6Q */
            private final /* synthetic */ IBinder f$1;
            private final /* synthetic */ WorkSource f$2;

            {
                this.f$1 = r2;
                this.f$2 = r3;
            }

            public final void run() {
                WifiServiceImpl.this.lambda$updateWifiLockWorkSource$14$WifiServiceImpl(this.f$1, this.f$2);
            }
        }, IWCEventManager.autoDisconnectThreshold)) {
            Log.e(TAG, "Failed to post runnable to updateWifiLockWorkSource");
        }
    }

    public /* synthetic */ void lambda$updateWifiLockWorkSource$14$WifiServiceImpl(IBinder binder, WorkSource updatedWs) {
        this.mWifiLockManager.updateWifiLockWorkSource(binder, updatedWs);
    }

    public boolean releaseWifiLock(IBinder binder) {
        this.mLog.info("releaseWifiLock uid=%").mo2069c((long) Binder.getCallingUid()).flush();
        this.mContext.enforceCallingOrSelfPermission("android.permission.WAKE_LOCK", null);
        GeneralUtil.Mutable<Boolean> lockSuccess = new GeneralUtil.Mutable<>();
        if (this.mWifiInjector.getClientModeImplHandler().runWithScissors(new Runnable(lockSuccess, binder) {
            /* class com.android.server.wifi.$$Lambda$WifiServiceImpl$606DFkwx_XgItatFI_YXGjq5q6c */
            private final /* synthetic */ GeneralUtil.Mutable f$1;
            private final /* synthetic */ IBinder f$2;

            {
                this.f$1 = r2;
                this.f$2 = r3;
            }

            public final void run() {
                WifiServiceImpl.this.lambda$releaseWifiLock$15$WifiServiceImpl(this.f$1, this.f$2);
            }
        }, IWCEventManager.autoDisconnectThreshold)) {
            return lockSuccess.value.booleanValue();
        }
        Log.e(TAG, "Failed to post runnable to releaseWifiLock");
        return false;
    }

    public /* synthetic */ void lambda$releaseWifiLock$15$WifiServiceImpl(GeneralUtil.Mutable lockSuccess, IBinder binder) {
        lockSuccess.value = (E) Boolean.valueOf(this.mWifiLockManager.releaseWifiLock(binder));
    }

    public void initializeMulticastFiltering() {
        enforceMulticastChangePermission();
        this.mLog.info("initializeMulticastFiltering uid=%").mo2069c((long) Binder.getCallingUid()).flush();
        this.mWifiMulticastLockManager.initializeFiltering();
    }

    public void acquireMulticastLock(IBinder binder, String tag) {
        enforceMulticastChangePermission();
        this.mLog.info("acquireMulticastLock uid=%").mo2069c((long) Binder.getCallingUid()).flush();
        this.mWifiMulticastLockManager.acquireLock(binder, tag);
    }

    public void releaseMulticastLock(String tag) {
        enforceMulticastChangePermission();
        this.mLog.info("releaseMulticastLock uid=%").mo2069c((long) Binder.getCallingUid()).flush();
        this.mWifiMulticastLockManager.releaseLock(tag);
    }

    public boolean isMulticastEnabled() {
        enforceAccessPermission();
        if (this.mVerboseLoggingEnabled) {
            this.mLog.info("isMulticastEnabled uid=%").mo2069c((long) Binder.getCallingUid()).flush();
        }
        return this.mWifiMulticastLockManager.isMulticastEnabled();
    }

    public void enableVerboseLogging(int verbose) {
        enforceAccessPermission();
        enforceNetworkSettingsPermission();
        this.mLog.info("enableVerboseLogging uid=% verbose=%").mo2069c((long) Binder.getCallingUid()).mo2069c((long) verbose).flush();
        this.mFacade.setIntegerSetting(this.mContext, "wifi_verbose_logging_enabled", verbose);
        enableVerboseLoggingInternal(verbose);
    }

    /* access modifiers changed from: package-private */
    public void enableVerboseLoggingInternal(int verbose) {
        this.mVerboseLoggingEnabled = verbose > 0;
        this.mClientModeImpl.enableVerboseLogging(verbose);
        this.mWifiLockManager.enableVerboseLogging(verbose);
        this.mWifiMulticastLockManager.enableVerboseLogging(verbose);
        this.mWifiInjector.enableVerboseLogging(verbose);
    }

    public int getVerboseLoggingLevel() {
        if (this.mVerboseLoggingEnabled) {
            this.mLog.info("getVerboseLoggingLevel uid=%").mo2069c((long) Binder.getCallingUid()).flush();
        }
        return this.mFacade.getIntegerSetting(this.mContext, "wifi_verbose_logging_enabled", 0);
    }

    public void setMaxClientVzwBasedOnNetworkType(int networkType) {
        if ("VZW".equals(CONFIGOPBRANDINGFORMOBILEAP)) {
            int wifiApState = getWifiApEnabledState();
            if (wifiApState == 12 || wifiApState == 13) {
                int maxClientNum = 5;
                if (networkType == 13) {
                    maxClientNum = WifiApConfigStore.MAX_CLIENT;
                }
                Message msg = new Message();
                msg.what = 14;
                Bundle b = new Bundle();
                b.putInt("maxClient", maxClientNum);
                msg.obj = b;
                callSECApi(msg);
            }
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void checkAndSarBackOffFor5G(ServiceState serviceState) {
        Log.d(TAG, "serviceState.getNrBearerStatus=" + serviceState.getNrBearerStatus() + " / mPrevNrState=" + this.mPrevNrState);
        if (serviceState.getNrBearerStatus() == 1) {
            if (this.mPrevNrState != 1) {
                if (getWifiApEnabledState() == 13) {
                    this.mWifiNative.setHotspotBackOff(6);
                } else if (getWifiEnabledState() == 3) {
                    this.mClientModeImpl.set5GSarBackOff(6);
                }
            }
            this.mPrevNrState = 1;
        } else if (serviceState.getNrBearerStatus() == 2) {
            if (this.mPrevNrState != 2) {
                if (getWifiApEnabledState() == 13) {
                    this.mWifiNative.setHotspotBackOff(4);
                } else if (getWifiEnabledState() == 3) {
                    this.mClientModeImpl.set5GSarBackOff(4);
                }
            }
            this.mPrevNrState = 2;
        } else {
            int i = this.mPrevNrState;
            if (i == 2) {
                if (getWifiApEnabledState() == 13) {
                    this.mWifiNative.setHotspotBackOff(3);
                } else if (getWifiEnabledState() == 3) {
                    this.mClientModeImpl.set5GSarBackOff(3);
                }
            } else if (i == 1) {
                if (getWifiApEnabledState() == 13) {
                    this.mWifiNative.setHotspotBackOff(5);
                } else if (getWifiEnabledState() == 3) {
                    this.mClientModeImpl.set5GSarBackOff(5);
                }
            }
            this.mPrevNrState = 0;
        }
    }

    public void factoryReset(String packageName) {
        enforceConnectivityInternalPermission();
        if (enforceChangePermission(packageName) == 0) {
            this.mLog.info("factoryReset uid=%").mo2069c((long) Binder.getCallingUid()).flush();
            if (!this.mUserManager.hasUserRestriction("no_network_reset")) {
                if (!this.mUserManager.hasUserRestriction("no_config_tethering")) {
                    stopSoftApInternal(-1);
                }
                if (!this.mUserManager.hasUserRestriction("no_config_wifi")) {
                    if (this.mClientModeImplChannel != null) {
                        List<WifiConfiguration> networks = this.mClientModeImpl.syncGetConfiguredNetworks(Binder.getCallingUid(), this.mClientModeImplChannel, 1010);
                        if (networks != null) {
                            for (WifiConfiguration config : networks) {
                                removeNetwork(config.networkId, packageName);
                            }
                        }
                        List<PasspointConfiguration> configs = this.mClientModeImpl.syncGetPasspointConfigs(this.mClientModeImplChannel);
                        if (configs != null) {
                            for (PasspointConfiguration config2 : configs) {
                                if (!(config2.getHomeSp() == null || config2.getHomeSp().getProviderType() == 2)) {
                                    removePasspointConfiguration(config2.getHomeSp().getFqdn(), packageName);
                                }
                            }
                        }
                    }
                    this.mClientModeImpl.disconnectCommand(Binder.getCallingUid(), 23);
                    this.mWifiInjector.getClientModeImplHandler().post(new Runnable() {
                        /* class com.android.server.wifi.$$Lambda$WifiServiceImpl$VfJk6qH38988FswAFSv3bmFHOyQ */

                        public final void run() {
                            WifiServiceImpl.this.lambda$factoryReset$16$WifiServiceImpl();
                        }
                    });
                    notifyFactoryReset();
                    AutoWifiController autoWifiController = this.mAutoWifiController;
                    if (autoWifiController != null) {
                        autoWifiController.factoryReset();
                    }
                    WifiRoamingAssistant wifiRoamingAssistant = this.mWifiRoamingAssistant;
                    if (wifiRoamingAssistant != null) {
                        wifiRoamingAssistant.factoryReset();
                    }
                }
                this.mDefaultApController.factoryReset();
                this.mPasspointDefaultProvider.addDefaultProviderFromCredential();
                IWCMonitor iWCMonitor = this.mIWCMonitor;
                if (iWCMonitor != null) {
                    iWCMonitor.sendMessage(IWCMonitor.FACTORY_RESET_REQUIRED);
                }
                SemWifiApSmartGattClient semWifiApSmartGattClient = this.mSemWifiApSmartGattClient;
                if (semWifiApSmartGattClient != null) {
                    semWifiApSmartGattClient.factoryReset();
                }
                SemWifiApSmartGattServer semWifiApSmartGattServer = this.mSemWifiApSmartGattServer;
                if (semWifiApSmartGattServer != null) {
                    semWifiApSmartGattServer.factoryReset();
                }
            }
        }
    }

    public /* synthetic */ void lambda$factoryReset$16$WifiServiceImpl() {
        this.mWifiInjector.getWifiConfigManager().clearDeletedEphemeralNetworks();
        this.mClientModeImpl.clearNetworkRequestUserApprovedAccessPoints();
        this.mWifiNetworkSuggestionsManager.clear();
        this.mWifiInjector.getWifiScoreCard().clear();
    }

    static boolean logAndReturnFalse(String s) {
        Log.d(TAG, s);
        return false;
    }

    private void notifyFactoryReset() {
        Intent intent = new Intent("android.net.wifi.action.NETWORK_SETTINGS_RESET");
        intent.addFlags(16777216);
        this.mContext.sendBroadcastAsUser(intent, UserHandle.ALL, "android.permission.NETWORK_CARRIER_PROVISIONING");
    }

    public Network getCurrentNetwork() {
        enforceAccessPermission();
        if (this.mVerboseLoggingEnabled) {
            this.mLog.info("getCurrentNetwork uid=%").mo2069c((long) Binder.getCallingUid()).flush();
        }
        return this.mClientModeImpl.getCurrentNetwork();
    }

    public void enableWifiConnectivityManager(boolean enabled) {
        enforceConnectivityInternalPermission();
        this.mLog.info("enableWifiConnectivityManager uid=% enabled=%").mo2069c((long) Binder.getCallingUid()).mo2071c(enabled).flush();
        this.mClientModeImpl.enableWifiConnectivityManager(enabled);
    }

    public byte[] retrieveBackupData() {
        enforceNetworkSettingsPermission();
        this.mLog.info("retrieveBackupData uid=%").mo2069c((long) Binder.getCallingUid()).flush();
        if (this.mClientModeImplChannel == null) {
            Slog.e(TAG, "mClientModeImplChannel is not initialized");
            return null;
        }
        Slog.d(TAG, "Retrieving backup data");
        byte[] backupData = this.mWifiBackupRestore.retrieveBackupDataFromConfigurations(this.mClientModeImpl.syncGetPrivilegedConfiguredNetwork(this.mClientModeImplChannel));
        Slog.d(TAG, "Retrieved backup data");
        return backupData;
    }

    private void restoreNetworks(List<WifiConfiguration> configurations) {
        if (configurations == null) {
            Slog.e(TAG, "Backup data parse failed");
            return;
        }
        for (WifiConfiguration configuration : configurations) {
            int networkId = this.mClientModeImpl.syncAddOrUpdateNetwork(this.mClientModeImplChannel, configuration);
            if (networkId == -1) {
                Slog.e(TAG, "Restore network failed: " + configuration.configKey());
            } else {
                this.mClientModeImpl.syncEnableNetwork(this.mClientModeImplChannel, networkId, false);
            }
        }
    }

    public void restoreBackupData(byte[] data) {
        enforceNetworkSettingsPermission();
        this.mLog.info("restoreBackupData uid=%").mo2069c((long) Binder.getCallingUid()).flush();
        if (this.mClientModeImplChannel == null) {
            Slog.e(TAG, "mClientModeImplChannel is not initialized");
            return;
        }
        Slog.d(TAG, "Restoring backup data");
        restoreNetworks(this.mWifiBackupRestore.retrieveConfigurationsFromBackupData(data));
        Slog.d(TAG, "Restored backup data");
    }

    public void restoreSupplicantBackupData(byte[] supplicantData, byte[] ipConfigData) {
        enforceNetworkSettingsPermission();
        this.mLog.trace("restoreSupplicantBackupData uid=%").mo2069c((long) Binder.getCallingUid()).flush();
        if (this.mClientModeImplChannel == null) {
            Slog.e(TAG, "mClientModeImplChannel is not initialized");
            return;
        }
        Slog.d(TAG, "Restoring supplicant backup data");
        restoreNetworks(this.mWifiBackupRestore.retrieveConfigurationsFromSupplicantBackupData(supplicantData, ipConfigData));
        Slog.d(TAG, "Restored supplicant backup data");
    }

    public void startSubscriptionProvisioning(OsuProvider provider, IProvisioningCallback callback) {
        if (provider == null) {
            throw new IllegalArgumentException("Provider must not be null");
        } else if (callback == null) {
            throw new IllegalArgumentException("Callback must not be null");
        } else if (isSettingsOrSuw(Binder.getCallingPid(), Binder.getCallingUid())) {
            int uid = Binder.getCallingUid();
            this.mLog.trace("startSubscriptionProvisioning uid=%").mo2069c((long) uid).flush();
            if (this.mClientModeImpl.syncStartSubscriptionProvisioning(uid, provider, callback, this.mClientModeImplChannel)) {
                this.mLog.trace("Subscription provisioning started with %").mo2070c(provider.toString()).flush();
            }
        } else {
            throw new SecurityException("WifiService: Permission denied");
        }
    }

    public void registerTrafficStateCallback(IBinder binder, ITrafficStateCallback callback, int callbackIdentifier) {
        if (binder == null) {
            throw new IllegalArgumentException("Binder must not be null");
        } else if (callback != null) {
            enforceNetworkSettingsPermission();
            if (this.mVerboseLoggingEnabled) {
                this.mLog.info("registerTrafficStateCallback uid=%").mo2069c((long) Binder.getCallingUid()).flush();
            }
            this.mWifiInjector.getClientModeImplHandler().post(new Runnable(binder, callback, callbackIdentifier) {
                /* class com.android.server.wifi.$$Lambda$WifiServiceImpl$bdamqPijr2P1g9OmRYtn2BS7SW8 */
                private final /* synthetic */ IBinder f$1;
                private final /* synthetic */ ITrafficStateCallback f$2;
                private final /* synthetic */ int f$3;

                {
                    this.f$1 = r2;
                    this.f$2 = r3;
                    this.f$3 = r4;
                }

                public final void run() {
                    WifiServiceImpl.this.lambda$registerTrafficStateCallback$17$WifiServiceImpl(this.f$1, this.f$2, this.f$3);
                }
            });
        } else {
            throw new IllegalArgumentException("Callback must not be null");
        }
    }

    public /* synthetic */ void lambda$registerTrafficStateCallback$17$WifiServiceImpl(IBinder binder, ITrafficStateCallback callback, int callbackIdentifier) {
        this.mWifiTrafficPoller.addCallback(binder, callback, callbackIdentifier);
    }

    public void unregisterTrafficStateCallback(int callbackIdentifier) {
        enforceNetworkSettingsPermission();
        if (this.mVerboseLoggingEnabled) {
            this.mLog.info("unregisterTrafficStateCallback uid=%").mo2069c((long) Binder.getCallingUid()).flush();
        }
        this.mWifiInjector.getClientModeImplHandler().post(new Runnable(callbackIdentifier) {
            /* class com.android.server.wifi.$$Lambda$WifiServiceImpl$1eItk_AY1e0K2huycUENNElL9o */
            private final /* synthetic */ int f$1;

            {
                this.f$1 = r2;
            }

            public final void run() {
                WifiServiceImpl.this.lambda$unregisterTrafficStateCallback$18$WifiServiceImpl(this.f$1);
            }
        });
    }

    public /* synthetic */ void lambda$unregisterTrafficStateCallback$18$WifiServiceImpl(int callbackIdentifier) {
        this.mWifiTrafficPoller.removeCallback(callbackIdentifier);
    }

    private boolean is5GhzSupported() {
        return (getSupportedFeaturesInternal() & 2) == 2;
    }

    private long getSupportedFeaturesInternal() {
        AsyncChannel channel = this.mClientModeImplChannel;
        if (channel != null) {
            return this.mClientModeImpl.syncGetSupportedFeatures(channel);
        }
        Slog.e(TAG, "mClientModeImplChannel is not initialized");
        return 0;
    }

    private static boolean hasAutomotiveFeature(Context context) {
        return context.getPackageManager().hasSystemFeature("android.hardware.type.automotive");
    }

    public void registerNetworkRequestMatchCallback(IBinder binder, INetworkRequestMatchCallback callback, int callbackIdentifier) {
        if (binder == null) {
            throw new IllegalArgumentException("Binder must not be null");
        } else if (callback != null) {
            enforceNetworkSettingsPermission();
            if (this.mVerboseLoggingEnabled) {
                this.mLog.info("registerNetworkRequestMatchCallback uid=%").mo2069c((long) Binder.getCallingUid()).flush();
            }
            this.mWifiInjector.getClientModeImplHandler().post(new Runnable(binder, callback, callbackIdentifier) {
                /* class com.android.server.wifi.$$Lambda$WifiServiceImpl$jmmlwuKT1u8TK5iVETSsNHFw_c */
                private final /* synthetic */ IBinder f$1;
                private final /* synthetic */ INetworkRequestMatchCallback f$2;
                private final /* synthetic */ int f$3;

                {
                    this.f$1 = r2;
                    this.f$2 = r3;
                    this.f$3 = r4;
                }

                public final void run() {
                    WifiServiceImpl.this.lambda$registerNetworkRequestMatchCallback$19$WifiServiceImpl(this.f$1, this.f$2, this.f$3);
                }
            });
        } else {
            throw new IllegalArgumentException("Callback must not be null");
        }
    }

    public /* synthetic */ void lambda$registerNetworkRequestMatchCallback$19$WifiServiceImpl(IBinder binder, INetworkRequestMatchCallback callback, int callbackIdentifier) {
        this.mClientModeImpl.addNetworkRequestMatchCallback(binder, callback, callbackIdentifier);
    }

    public void unregisterNetworkRequestMatchCallback(int callbackIdentifier) {
        enforceNetworkSettingsPermission();
        if (this.mVerboseLoggingEnabled) {
            this.mLog.info("unregisterNetworkRequestMatchCallback uid=%").mo2069c((long) Binder.getCallingUid()).flush();
        }
        this.mWifiInjector.getClientModeImplHandler().post(new Runnable(callbackIdentifier) {
            /* class com.android.server.wifi.$$Lambda$WifiServiceImpl$5Kt3n0HG2Nx_6BARbwyDj2vTgDk */
            private final /* synthetic */ int f$1;

            {
                this.f$1 = r2;
            }

            public final void run() {
                WifiServiceImpl.this.lambda$unregisterNetworkRequestMatchCallback$20$WifiServiceImpl(this.f$1);
            }
        });
    }

    public /* synthetic */ void lambda$unregisterNetworkRequestMatchCallback$20$WifiServiceImpl(int callbackIdentifier) {
        this.mClientModeImpl.removeNetworkRequestMatchCallback(callbackIdentifier);
    }

    public int addNetworkSuggestions(List<WifiNetworkSuggestion> networkSuggestions, String callingPackageName) {
        if (enforceChangePermission(callingPackageName) != 0) {
            return 2;
        }
        if (this.mVerboseLoggingEnabled) {
            this.mLog.info("addNetworkSuggestions uid=%").mo2069c((long) Binder.getCallingUid()).flush();
        }
        int callingUid = Binder.getCallingUid();
        GeneralUtil.Mutable<Integer> success = new GeneralUtil.Mutable<>();
        if (!this.mWifiInjector.getClientModeImplHandler().runWithScissors(new Runnable(success, networkSuggestions, callingUid, callingPackageName) {
            /* class com.android.server.wifi.$$Lambda$WifiServiceImpl$uZHbfApZmSkgQJmzkUQkiqpp7pY */
            private final /* synthetic */ GeneralUtil.Mutable f$1;
            private final /* synthetic */ List f$2;
            private final /* synthetic */ int f$3;
            private final /* synthetic */ String f$4;

            {
                this.f$1 = r2;
                this.f$2 = r3;
                this.f$3 = r4;
                this.f$4 = r5;
            }

            public final void run() {
                WifiServiceImpl.this.lambda$addNetworkSuggestions$21$WifiServiceImpl(this.f$1, this.f$2, this.f$3, this.f$4);
            }
        }, IWCEventManager.autoDisconnectThreshold)) {
            Log.e(TAG, "Failed to post runnable to add network suggestions");
            return 1;
        }
        if (success.value.intValue() != 0) {
            Log.e(TAG, "Failed to add network suggestions");
        }
        return success.value.intValue();
    }

    public /* synthetic */ void lambda$addNetworkSuggestions$21$WifiServiceImpl(GeneralUtil.Mutable success, List networkSuggestions, int callingUid, String callingPackageName) {
        success.value = (E) Integer.valueOf(this.mWifiNetworkSuggestionsManager.add(networkSuggestions, callingUid, callingPackageName));
    }

    public int removeNetworkSuggestions(List<WifiNetworkSuggestion> networkSuggestions, String callingPackageName) {
        if (enforceChangePermission(callingPackageName) != 0) {
            return 2;
        }
        if (this.mVerboseLoggingEnabled) {
            this.mLog.info("removeNetworkSuggestions uid=%").mo2069c((long) Binder.getCallingUid()).flush();
        }
        int callingUid = Binder.getCallingUid();
        GeneralUtil.Mutable<Integer> success = new GeneralUtil.Mutable<>();
        if (!this.mWifiInjector.getClientModeImplHandler().runWithScissors(new Runnable(success, networkSuggestions, callingUid, callingPackageName) {
            /* class com.android.server.wifi.$$Lambda$WifiServiceImpl$CuTHL8zy_Od02SXH54ajwVlogiE */
            private final /* synthetic */ GeneralUtil.Mutable f$1;
            private final /* synthetic */ List f$2;
            private final /* synthetic */ int f$3;
            private final /* synthetic */ String f$4;

            {
                this.f$1 = r2;
                this.f$2 = r3;
                this.f$3 = r4;
                this.f$4 = r5;
            }

            public final void run() {
                WifiServiceImpl.this.lambda$removeNetworkSuggestions$22$WifiServiceImpl(this.f$1, this.f$2, this.f$3, this.f$4);
            }
        }, IWCEventManager.autoDisconnectThreshold)) {
            Log.e(TAG, "Failed to post runnable to remove network suggestions");
            return 1;
        }
        if (success.value.intValue() != 0) {
            Log.e(TAG, "Failed to remove network suggestions");
        }
        return success.value.intValue();
    }

    public /* synthetic */ void lambda$removeNetworkSuggestions$22$WifiServiceImpl(GeneralUtil.Mutable success, List networkSuggestions, int callingUid, String callingPackageName) {
        success.value = (E) Integer.valueOf(this.mWifiNetworkSuggestionsManager.remove(networkSuggestions, callingUid, callingPackageName));
    }

    public String[] getFactoryMacAddresses() {
        int uid = Binder.getCallingUid();
        if (this.mWifiPermissionsUtil.checkNetworkSettingsPermission(uid)) {
            List<String> result = new ArrayList<>();
            if (!this.mWifiInjector.getClientModeImplHandler().runWithScissors(new Runnable(result) {
                /* class com.android.server.wifi.$$Lambda$WifiServiceImpl$lQnWEG4uBH8rQuGaN5i6DeZ2plo */
                private final /* synthetic */ List f$1;

                {
                    this.f$1 = r2;
                }

                public final void run() {
                    WifiServiceImpl.this.lambda$getFactoryMacAddresses$23$WifiServiceImpl(this.f$1);
                }
            }, IWCEventManager.autoDisconnectThreshold) || result.isEmpty()) {
                return null;
            }
            return (String[]) result.stream().toArray($$Lambda$WifiServiceImpl$EfgfTvi04qWi6e59wo2Ap33XY.INSTANCE);
        }
        throw new SecurityException("App not allowed to get Wi-Fi factory MAC address (uid = " + uid + ")");
    }

    public /* synthetic */ void lambda$getFactoryMacAddresses$23$WifiServiceImpl(List result) {
        String mac = this.mClientModeImpl.getFactoryMacAddress();
        if (mac != null) {
            result.add(mac);
        }
    }

    static /* synthetic */ String[] lambda$getFactoryMacAddresses$24(int x$0) {
        return new String[x$0];
    }

    public void setDeviceMobilityState(int state) {
        this.mContext.enforceCallingPermission("android.permission.WIFI_SET_DEVICE_MOBILITY_STATE", TAG);
        if (this.mVerboseLoggingEnabled) {
            this.mLog.info("setDeviceMobilityState uid=% state=%").mo2069c((long) Binder.getCallingUid()).mo2069c((long) state).flush();
        }
        this.mWifiInjector.getClientModeImplHandler().post(new Runnable(state) {
            /* class com.android.server.wifi.$$Lambda$WifiServiceImpl$2VE1CwNfiLLvsmvFlNa4JMQxp0 */
            private final /* synthetic */ int f$1;

            {
                this.f$1 = r2;
            }

            public final void run() {
                WifiServiceImpl.this.lambda$setDeviceMobilityState$25$WifiServiceImpl(this.f$1);
            }
        });
    }

    public /* synthetic */ void lambda$setDeviceMobilityState$25$WifiServiceImpl(int state) {
        this.mClientModeImpl.setDeviceMobilityState(state);
    }

    public int getMockableCallingUid() {
        return getCallingUid();
    }

    public void startDppAsConfiguratorInitiator(IBinder binder, String enrolleeUri, int selectedNetworkId, int netRole, IDppCallback callback) {
        if (binder == null) {
            throw new IllegalArgumentException("Binder must not be null");
        } else if (TextUtils.isEmpty(enrolleeUri)) {
            throw new IllegalArgumentException("Enrollee URI must not be null or empty");
        } else if (selectedNetworkId < 0) {
            throw new IllegalArgumentException("Selected network ID invalid");
        } else if (callback != null) {
            int uid = getMockableCallingUid();
            if (isSettingsOrSuw(Binder.getCallingPid(), Binder.getCallingUid())) {
                this.mDppManager.mHandler.post(new Runnable(uid, binder, enrolleeUri, selectedNetworkId, netRole, callback) {
                    /* class com.android.server.wifi.$$Lambda$WifiServiceImpl$BnuvYd3kgsQsz2awpnB66yeV8 */
                    private final /* synthetic */ int f$1;
                    private final /* synthetic */ IBinder f$2;
                    private final /* synthetic */ String f$3;
                    private final /* synthetic */ int f$4;
                    private final /* synthetic */ int f$5;
                    private final /* synthetic */ IDppCallback f$6;

                    {
                        this.f$1 = r2;
                        this.f$2 = r3;
                        this.f$3 = r4;
                        this.f$4 = r5;
                        this.f$5 = r6;
                        this.f$6 = r7;
                    }

                    public final void run() {
                        WifiServiceImpl.this.lambda$startDppAsConfiguratorInitiator$26$WifiServiceImpl(this.f$1, this.f$2, this.f$3, this.f$4, this.f$5, this.f$6);
                    }
                });
                return;
            }
            throw new SecurityException("WifiService: Permission denied");
        } else {
            throw new IllegalArgumentException("Callback must not be null");
        }
    }

    public /* synthetic */ void lambda$startDppAsConfiguratorInitiator$26$WifiServiceImpl(int uid, IBinder binder, String enrolleeUri, int selectedNetworkId, int netRole, IDppCallback callback) {
        this.mDppManager.startDppAsConfiguratorInitiator(uid, binder, enrolleeUri, selectedNetworkId, netRole, callback);
    }

    public void startDppAsEnrolleeInitiator(IBinder binder, String configuratorUri, IDppCallback callback) {
        if (binder == null) {
            throw new IllegalArgumentException("Binder must not be null");
        } else if (TextUtils.isEmpty(configuratorUri)) {
            throw new IllegalArgumentException("Enrollee URI must not be null or empty");
        } else if (callback != null) {
            int uid = getMockableCallingUid();
            if (isSettingsOrSuw(Binder.getCallingPid(), Binder.getCallingUid())) {
                this.mDppManager.mHandler.post(new Runnable(uid, binder, configuratorUri, callback) {
                    /* class com.android.server.wifi.$$Lambda$WifiServiceImpl$1Wbw_8QENted8X24_fBNuSdWuqg */
                    private final /* synthetic */ int f$1;
                    private final /* synthetic */ IBinder f$2;
                    private final /* synthetic */ String f$3;
                    private final /* synthetic */ IDppCallback f$4;

                    {
                        this.f$1 = r2;
                        this.f$2 = r3;
                        this.f$3 = r4;
                        this.f$4 = r5;
                    }

                    public final void run() {
                        WifiServiceImpl.this.lambda$startDppAsEnrolleeInitiator$27$WifiServiceImpl(this.f$1, this.f$2, this.f$3, this.f$4);
                    }
                });
                return;
            }
            throw new SecurityException("WifiService: Permission denied");
        } else {
            throw new IllegalArgumentException("Callback must not be null");
        }
    }

    public /* synthetic */ void lambda$startDppAsEnrolleeInitiator$27$WifiServiceImpl(int uid, IBinder binder, String configuratorUri, IDppCallback callback) {
        this.mDppManager.startDppAsEnrolleeInitiator(uid, binder, configuratorUri, callback);
    }

    public void stopDppSession() throws RemoteException {
        if (isSettingsOrSuw(Binder.getCallingPid(), Binder.getCallingUid())) {
            this.mDppManager.mHandler.post(new Runnable(getMockableCallingUid()) {
                /* class com.android.server.wifi.$$Lambda$WifiServiceImpl$s_zDKbvm8vj9RTKGTZ1m3lY5ZUg */
                private final /* synthetic */ int f$1;

                {
                    this.f$1 = r2;
                }

                public final void run() {
                    WifiServiceImpl.this.lambda$stopDppSession$28$WifiServiceImpl(this.f$1);
                }
            });
            return;
        }
        throw new SecurityException("WifiService: Permission denied");
    }

    public /* synthetic */ void lambda$stopDppSession$28$WifiServiceImpl(int uid) {
        this.mDppManager.stopDppSession(uid);
    }

    public void addOnWifiUsabilityStatsListener(IBinder binder, IOnWifiUsabilityStatsListener listener, int listenerIdentifier) {
        if (binder == null) {
            throw new IllegalArgumentException("Binder must not be null");
        } else if (listener != null) {
            this.mContext.enforceCallingPermission("android.permission.WIFI_UPDATE_USABILITY_STATS_SCORE", TAG);
            if (this.mVerboseLoggingEnabled) {
                this.mLog.info("addOnWifiUsabilityStatsListener uid=%").mo2069c((long) Binder.getCallingUid()).flush();
            }
            this.mWifiInjector.getClientModeImplHandler().post(new Runnable(binder, listener, listenerIdentifier) {
                /* class com.android.server.wifi.$$Lambda$WifiServiceImpl$9NMuPFnF4_IQgmZZH3Cqzck_s0 */
                private final /* synthetic */ IBinder f$1;
                private final /* synthetic */ IOnWifiUsabilityStatsListener f$2;
                private final /* synthetic */ int f$3;

                {
                    this.f$1 = r2;
                    this.f$2 = r3;
                    this.f$3 = r4;
                }

                public final void run() {
                    WifiServiceImpl.this.lambda$addOnWifiUsabilityStatsListener$29$WifiServiceImpl(this.f$1, this.f$2, this.f$3);
                }
            });
        } else {
            throw new IllegalArgumentException("Listener must not be null");
        }
    }

    public /* synthetic */ void lambda$addOnWifiUsabilityStatsListener$29$WifiServiceImpl(IBinder binder, IOnWifiUsabilityStatsListener listener, int listenerIdentifier) {
        this.mWifiMetrics.addOnWifiUsabilityListener(binder, listener, listenerIdentifier);
    }

    public void removeOnWifiUsabilityStatsListener(int listenerIdentifier) {
        this.mContext.enforceCallingPermission("android.permission.WIFI_UPDATE_USABILITY_STATS_SCORE", TAG);
        if (this.mVerboseLoggingEnabled) {
            this.mLog.info("removeOnWifiUsabilityStatsListener uid=%").mo2069c((long) Binder.getCallingUid()).flush();
        }
        this.mWifiInjector.getClientModeImplHandler().post(new Runnable(listenerIdentifier) {
            /* class com.android.server.wifi.$$Lambda$WifiServiceImpl$9ybBmqGMfiJAsHk3qlFw3QtPOlc */
            private final /* synthetic */ int f$1;

            {
                this.f$1 = r2;
            }

            public final void run() {
                WifiServiceImpl.this.lambda$removeOnWifiUsabilityStatsListener$30$WifiServiceImpl(this.f$1);
            }
        });
    }

    public /* synthetic */ void lambda$removeOnWifiUsabilityStatsListener$30$WifiServiceImpl(int listenerIdentifier) {
        this.mWifiMetrics.removeOnWifiUsabilityListener(listenerIdentifier);
    }

    public void updateWifiUsabilityScore(int seqNum, int score, int predictionHorizonSec) {
        this.mContext.enforceCallingPermission("android.permission.WIFI_UPDATE_USABILITY_STATS_SCORE", TAG);
        if (this.mVerboseLoggingEnabled) {
            this.mLog.info("updateWifiUsabilityScore uid=% seqNum=% score=% predictionHorizonSec=%").mo2069c((long) Binder.getCallingUid()).mo2069c((long) seqNum).mo2069c((long) score).mo2069c((long) predictionHorizonSec).flush();
        }
        this.mWifiInjector.getClientModeImplHandler().post(new Runnable(seqNum, score, predictionHorizonSec) {
            /* class com.android.server.wifi.$$Lambda$WifiServiceImpl$PleaSmIvNWOr3_GBtDmeF11eo */
            private final /* synthetic */ int f$1;
            private final /* synthetic */ int f$2;
            private final /* synthetic */ int f$3;

            {
                this.f$1 = r2;
                this.f$2 = r3;
                this.f$3 = r4;
            }

            public final void run() {
                WifiServiceImpl.this.lambda$updateWifiUsabilityScore$31$WifiServiceImpl(this.f$1, this.f$2, this.f$3);
            }
        });
    }

    public /* synthetic */ void lambda$updateWifiUsabilityScore$31$WifiServiceImpl(int seqNum, int score, int predictionHorizonSec) {
        this.mClientModeImpl.updateWifiUsabilityScore(seqNum, score, predictionHorizonSec);
    }

    /* JADX INFO: finally extract failed */
    /* JADX WARN: Multi-variable type inference failed */
    /* JADX WARN: Type inference failed for: r1v37 */
    /* JADX WARN: Type inference failed for: r1v44 */
    /* JADX WARNING: Removed duplicated region for block: B:205:0x0432  */
    /* JADX WARNING: Removed duplicated region for block: B:209:0x0444  */
    /* JADX WARNING: Removed duplicated region for block: B:211:0x044b  */
    public int callSECApi(Message msg) {
        AsyncChannel asyncChannel;
        WifiConnectivityMonitor wifiConnectivityMonitor;
        int retValue = -1;
        retValue = -1;
        retValue = -1;
        retValue = -1;
        retValue = -1;
        retValue = -1;
        retValue = -1;
        retValue = -1;
        retValue = -1;
        retValue = -1;
        retValue = -1;
        retValue = -1;
        retValue = -1;
        retValue = -1;
        retValue = -1;
        retValue = -1;
        retValue = -1;
        retValue = -1;
        retValue = -1;
        retValue = -1;
        retValue = -1;
        retValue = -1;
        retValue = -1;
        retValue = -1;
        retValue = -1;
        retValue = -1;
        retValue = -1;
        retValue = -1;
        int retValue2 = -1;
        retValue = -1;
        retValue = -1;
        retValue = -1;
        retValue = -1;
        retValue = -1;
        retValue = -1;
        retValue = -1;
        retValue = -1;
        retValue = -1;
        retValue = -1;
        retValue = -1;
        retValue = -1;
        retValue = -1;
        retValue = -1;
        retValue = -1;
        retValue = -1;
        retValue = -1;
        retValue = -1;
        retValue = -1;
        retValue = -1;
        if (msg != null) {
            int callingPid = Binder.getCallingPid();
            Log.d(TAG, "callSECApi msg.what=" + msg.what + ", callingPid:" + callingPid);
            int i = msg.what;
            if (i == 3) {
                enforceAccessPermission();
                try {
                    String num = this.mWifiNative.sendHostapdCommand("NUM_STA");
                    retValue = 0;
                    retValue = 0;
                    if (num != null && !num.isEmpty()) {
                        retValue = Integer.parseInt(num);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else if (i == 4) {
                enforceChangePermission();
                try {
                    Bundle args = (Bundle) msg.obj;
                    if (args != null) {
                        this.mSemWifiApClientInfo = WifiInjector.getInstance().getSemWifiApClientInfo();
                        this.mSemWifiApClientInfo.setAccessPointDisassocSta(args.getString("mac"));
                        this.mWifiNative.sendHostapdCommand("DISASSOCIATE " + args.getString("mac"));
                        retValue = 0;
                    }
                } catch (Exception e2) {
                    e2.printStackTrace();
                }
            } else if (i != 5) {
                boolean z = false;
                if (i != 6) {
                    if (i != 17) {
                        if (i != 18) {
                            if (i != 70) {
                                if (i == 71) {
                                    IWCMonitor iWCMonitor = this.mIWCMonitor;
                                    if (iWCMonitor != null) {
                                        iWCMonitor.sendMessage(msg.what, msg.sendingUid, msg.arg1, msg.obj);
                                        Bundle args2 = (Bundle) msg.obj;
                                        Log.e(TAG, "uid: " + msg.sendingUid + "nid: " + args2.getInt("netId") + "enabled: " + args2.getInt("autoReconnect"));
                                    }
                                } else if (i == 73) {
                                    retValue = 1;
                                } else if (i != 74) {
                                    if (!(i == 81 || i == 82)) {
                                        if (i == 87) {
                                            enforceProvideDiagnosticsPermission();
                                            if (msg.obj != null && (msg.obj instanceof Bundle)) {
                                                Bundle args3 = (Bundle) msg.obj;
                                                String packageName = args3.getString("packageName");
                                                if (packageName == null) {
                                                    packageName = getPackageName(callingPid);
                                                }
                                                String serviceAction = args3.getString("className");
                                                WifiGuiderManagementService wifiGuiderManagementService = this.mWifiGuiderManagementService;
                                                if (wifiGuiderManagementService != null) {
                                                    retValue = wifiGuiderManagementService.registerService(packageName, serviceAction);
                                                }
                                            }
                                        } else if (i != 88) {
                                            switch (i) {
                                                case 1:
                                                    break;
                                                case 14:
                                                    enforceChangePermission();
                                                    try {
                                                        Bundle args4 = (Bundle) msg.obj;
                                                        this.mWifiNative.sendHostapdCommand("SET_MAXCLIENT " + args4.getInt("maxClient"));
                                                        retValue = 0;
                                                        break;
                                                    } catch (Exception e3) {
                                                        e3.printStackTrace();
                                                        break;
                                                    }
                                                case 24:
                                                case MobileWipsScanResult.AnqpInformationElement.ANQP_LOC_URI /*{ENCODED_INT: 267}*/:
                                                case 280:
                                                case 281:
                                                case 283:
                                                case 330:
                                                case 407:
                                                    break;
                                                case 26:
                                                    enforceChangePermission();
                                                    Bundle args5 = (Bundle) msg.obj;
                                                    if (args5 != null) {
                                                        boolean enable = args5.getBoolean("enable", false);
                                                        String packageName2 = args5.getString("package", P2P_PACKAGE_NAME);
                                                        if (enable) {
                                                            Log.d(TAG, "SEC_COMMAND_ID_SET_WIFI_ENABLED_WITH_P2P - WiFi Enabled with p2p -> Stop Scan(Anqp), Stop Assoc, Stop WPS?");
                                                            this.mClientModeImpl.setConcurrentEnabled(enable);
                                                            setWifiEnabled(packageName2, enable);
                                                        }
                                                        this.mClientModeImpl.sendCallSECApiAsync(msg, callingPid);
                                                    }
                                                    retValue = 0;
                                                    break;
                                                case 28:
                                                    retValue = 0;
                                                    break;
                                                case 46:
                                                    enforceAccessPermission();
                                                    if (this.mSemQoSProfileShareProvider != null && (msg.obj instanceof Bundle)) {
                                                        this.mSemQoSProfileShareProvider.test((Bundle) msg.obj);
                                                        retValue = 0;
                                                        break;
                                                    }
                                                case 90:
                                                    enforceProvideDiagnosticsPermission();
                                                    if (this.mWifiGuiderManagementService != null) {
                                                        this.mWifiGuiderManagementService.updateConditions((Bundle) msg.obj);
                                                    }
                                                    retValue = 0;
                                                    break;
                                                case 100:
                                                case 102:
                                                case 104:
                                                    enforceChangePermission();
                                                    asyncChannel = this.mClientModeImplChannel;
                                                    if (asyncChannel != null) {
                                                        retValue = this.mClientModeImpl.syncCallSECApi(asyncChannel, msg);
                                                        break;
                                                    }
                                                    break;
                                                case 101:
                                                case 103:
                                                case 105:
                                                case 106:
                                                case 107:
                                                case 130:
                                                case 131:
                                                case 132:
                                                case 133:
                                                case 134:
                                                case 135:
                                                case 136:
                                                case 137:
                                                case 150:
                                                case 151:
                                                case 161:
                                                case 162:
                                                case 163:
                                                case 164:
                                                case 165:
                                                case 170:
                                                case 171:
                                                    if (this.mWifiInjector.getWifiB2bConfigPolicy().isPolicyApplied()) {
                                                        Log.e(TAG, "Error set NCHO API - EDM is applied.");
                                                        return -1;
                                                    }
                                                    enforceChangePermission();
                                                    asyncChannel = this.mClientModeImplChannel;
                                                    if (asyncChannel != null) {
                                                    }
                                                    break;
                                                case 180:
                                                    enforceAccessPermission();
                                                    retValue = this.mClientModeImpl.getRoamDhcpPolicy();
                                                    break;
                                                case 181:
                                                    enforceChangePermission();
                                                    if (msg.obj != null && (msg.obj instanceof Bundle)) {
                                                        retValue = this.mClientModeImpl.setRoamDhcpPolicy(((Bundle) msg.obj).getInt("mode"));
                                                        break;
                                                    }
                                                case 197:
                                                    enforceChangePermission();
                                                    this.mWifiController.obtainMessage(WifiController.CMD_RESET_AP, msg.arg1, msg.arg2, new SoftApModeConfiguration(1, (WifiConfiguration) msg.obj)).sendToTarget();
                                                    retValue = 0;
                                                    break;
                                                case 198:
                                                    enforceChangePermission();
                                                    retValue = !isWifiSharingEnabled();
                                                    break;
                                                case 201:
                                                    enforceChangePermission();
                                                    boolean keepConnection = ((Bundle) msg.obj).getBoolean("keep_connection");
                                                    if (keepConnection) {
                                                        this.mClientModeImpl.sendCallSECApiAsync(msg, callingPid);
                                                    }
                                                    WifiConnectivityMonitor wifiConnectivityMonitor2 = this.mWifiConnectivityMonitor;
                                                    if (wifiConnectivityMonitor2 != null) {
                                                        wifiConnectivityMonitor2.setUserSelection(keepConnection);
                                                    }
                                                    IWCMonitor iWCMonitor2 = this.mIWCMonitor;
                                                    if (iWCMonitor2 != null) {
                                                        iWCMonitor2.sendUserSelection(keepConnection);
                                                    }
                                                    retValue = 0;
                                                    break;
                                                case 222:
                                                    enforceChangePermission();
                                                    long ident = Binder.clearCallingIdentity();
                                                    try {
                                                        int retValue3 = this.mClientModeImpl.callSECApi(msg);
                                                        Binder.restoreCallingIdentity(ident);
                                                        retValue = retValue3;
                                                        break;
                                                    } catch (Throwable th) {
                                                        Binder.restoreCallingIdentity(ident);
                                                        throw th;
                                                    }
                                                case 230:
                                                    enforceChangePermission();
                                                    enforceConnectivityInternalPermission();
                                                    SemWifiApBroadcastReceiver semWifiApBroadcastReceiver = this.mSemWifiApBroadcastReceiver;
                                                    if (semWifiApBroadcastReceiver != null) {
                                                        semWifiApBroadcastReceiver.stopTracking();
                                                    }
                                                    shutdown();
                                                    retValue = 0;
                                                    break;
                                                case 242:
                                                    break;
                                                case 279:
                                                    enforceProvideDiagnosticsPermission();
                                                    if (msg.arg1 >= 0) {
                                                        PersistableBundle carrierSettings = (PersistableBundle) msg.obj;
                                                        CarrierConfigManager carrierConfigManager = (CarrierConfigManager) this.mContext.getSystemService("carrier_config");
                                                        long ident2 = Binder.clearCallingIdentity();
                                                        try {
                                                            carrierConfigManager.overrideConfig(msg.arg1, carrierSettings);
                                                            Binder.restoreCallingIdentity(ident2);
                                                            carrierConfigManager.notifyConfigChangedForSubId(msg.arg1);
                                                        } catch (Throwable th2) {
                                                            Binder.restoreCallingIdentity(ident2);
                                                            throw th2;
                                                        }
                                                    }
                                                    retValue = 0;
                                                    break;
                                                case 282:
                                                    enforceChangePermission();
                                                    this.mClientModeImpl.sendCallSECApiAsync(msg, callingPid);
                                                    retValue = 0;
                                                    break;
                                                case 290:
                                                    enforceChangePermission();
                                                    if (!((!Debug.semIsProductDev() && !this.semIsTestMode) || msg.obj == null || this.mAutoWifiController == null)) {
                                                        this.mAutoWifiController.setConfigForTest((Bundle) msg.obj);
                                                    }
                                                    retValue = 0;
                                                    break;
                                                case 292:
                                                    enforceAccessPermission();
                                                    if (this.mAutoWifiController == null) {
                                                        retValue = 0;
                                                        break;
                                                    } else {
                                                        retValue = 1;
                                                        break;
                                                    }
                                                case 293:
                                                    enforceAccessPermission();
                                                    if (msg.obj != null && (msg.obj instanceof Bundle)) {
                                                        String oAuthProvider = ((Bundle) msg.obj).getString("oauth_provider");
                                                        if (!TextUtils.isEmpty(oAuthProvider)) {
                                                            long ident3 = Binder.clearCallingIdentity();
                                                            try {
                                                                String dbValues = getOAuthAgreements();
                                                                if (!TextUtils.isEmpty(dbValues)) {
                                                                    retValue2 = dbValues.contains("[" + oAuthProvider + "]");
                                                                }
                                                                break;
                                                            } finally {
                                                                Binder.restoreCallingIdentity(ident3);
                                                            }
                                                        }
                                                    }
                                                    break;
                                                case 294:
                                                    enforceProvideDiagnosticsPermission();
                                                    if (msg.obj != null && (msg.obj instanceof Bundle)) {
                                                        Bundle args6 = (Bundle) msg.obj;
                                                        String oAuthProvider2 = args6.getString("oauth_provider");
                                                        if (!TextUtils.isEmpty(oAuthProvider2)) {
                                                            long ident4 = Binder.clearCallingIdentity();
                                                            try {
                                                                updateOAuthAgreement(oAuthProvider2, args6.getBoolean("agree", false));
                                                                break;
                                                            } finally {
                                                                Binder.restoreCallingIdentity(ident4);
                                                            }
                                                        }
                                                    }
                                                    break;
                                                case 299:
                                                    enforceAccessPermission();
                                                    retValue = this.mClientModeImpl.getCurrentGeofenceState();
                                                    break;
                                                case 301:
                                                    enforceChangePermission();
                                                    if (this.mClientModeImpl.syncCallSECApi(this.mClientModeImplChannel, msg) == 1 && (wifiConnectivityMonitor = this.mWifiConnectivityMonitor) != null) {
                                                        wifiConnectivityMonitor.resetWatchdogSettings();
                                                    }
                                                    retValue = 0;
                                                    break;
                                                case 303:
                                                    enforceChangePermission();
                                                    WifiConnectivityMonitor wifiConnectivityMonitor3 = this.mWifiConnectivityMonitor;
                                                    if (wifiConnectivityMonitor3 != null) {
                                                        retValue = wifiConnectivityMonitor3.getCurrentStatusMode();
                                                        break;
                                                    }
                                                    break;
                                                case 304:
                                                    enforceAccessPermission();
                                                    if (this.mWifiConnectivityMonitor != null) {
                                                        retValue = WifiConnectivityMonitor.getEverQualityTested();
                                                        break;
                                                    }
                                                    break;
                                                case 307:
                                                    enforceAccessPermission();
                                                    WifiConnectivityMonitor wifiConnectivityMonitor4 = this.mWifiConnectivityMonitor;
                                                    if (wifiConnectivityMonitor4 != null && wifiConnectivityMonitor4.getValidState()) {
                                                        retValue = 1;
                                                        break;
                                                    } else {
                                                        retValue = 0;
                                                        break;
                                                    }
                                                    break;
                                                case 314:
                                                    retValue = getIndoorStatus();
                                                    break;
                                                case ReportIdKey.ID_SCAN_FAIL /*{ENCODED_INT: 401}*/:
                                                    enforceChangePermission();
                                                    if (msg.obj != null && (msg.obj instanceof Bundle)) {
                                                        setQtables(((Bundle) msg.obj).getString("json"));
                                                        break;
                                                    }
                                                case 402:
                                                    enforceChangePermission();
                                                    startFromBeginning((Bundle) msg.obj);
                                                    break;
                                                case 404:
                                                    if (msg.obj != null && (msg.obj instanceof Bundle)) {
                                                        Bundle args7 = (Bundle) msg.obj;
                                                        int packageUid = args7.getInt("packageUid");
                                                        String packageName3 = args7.getString("packageName");
                                                        String apiName = args7.getString(ReportIdKey.KEY_API_NAME);
                                                        Log.d(TAG, "report call history packageName:" + packageName3 + ", apiName:" + apiName);
                                                        if (!(apiName == null || packageName3 == null || packageUid <= 1000)) {
                                                            long ident5 = Binder.clearCallingIdentity();
                                                            try {
                                                                int retValue4 = this.mWifiGuiderPackageMonitor.addApiLog(packageName3, this.mFrameworkFacade.isAppForeground(packageUid), apiName);
                                                                if (retValue4 > 0) {
                                                                    this.mClientModeImpl.report(500, ReportUtil.getReportDataForCallingSpecificApiFrequently(apiName, packageName3, retValue4));
                                                                }
                                                                break;
                                                            } finally {
                                                                Binder.restoreCallingIdentity(ident5);
                                                            }
                                                        }
                                                    }
                                                case 405:
                                                    enforceProvideDiagnosticsPermission();
                                                    if (msg.obj != null && (msg.obj instanceof Bundle)) {
                                                        Bundle args8 = (Bundle) msg.obj;
                                                        this.mWifiGuiderPackageMonitor.registerApi(args8.getString(ReportIdKey.KEY_API_NAME), args8.getInt("period"), args8.getInt("counter"));
                                                        retValue = 0;
                                                        break;
                                                    }
                                                case 408:
                                                    enforceChangePermission();
                                                    restoreUserPreference(msg.arg1, msg.arg2);
                                                    break;
                                                case 409:
                                                    if (!"user".equals(Build.TYPE) && Debug.semIsProductDev()) {
                                                        if (this.mIWCMonitor != null) {
                                                            int action = msg.arg1;
                                                            Log.d(TAG, "sendMessage with action " + action);
                                                            this.mIWCMonitor.sendMessage(IWCMonitor.FORCE_ACTION, action);
                                                            retValue = 0;
                                                            break;
                                                        } else {
                                                            Log.d(TAG, "Not support in IWC non-support device");
                                                            break;
                                                        }
                                                    } else {
                                                        Log.d(TAG, "SEC_COMMAND_ID_IWC_SET_FORCE_ACTION command does not support in commercial device");
                                                        break;
                                                    }
                                                    break;
                                                case 500:
                                                    enforceChangePermission();
                                                    if (this.mClientModeImplChannel != null) {
                                                        retValue = this.mClientModeImpl.callSECApi(msg);
                                                        break;
                                                    }
                                                    break;
                                                default:
                                                    switch (i) {
                                                        case 41:
                                                            enforceNetworkSettingsPermission();
                                                            return WifiGuiderFeatureController.getInstance(this.mContext).isSupportSamsungNetworkScore() ? 1 : 0;
                                                        case 42:
                                                            enforceNetworkSettingsPermission();
                                                            return WifiGuiderFeatureController.getInstance(this.mContext).isSupportWifiProfileRequest() ? 1 : 0;
                                                        case 43:
                                                            enforceNetworkSettingsPermission();
                                                            SemWifiProfileAndQoSProvider semWifiProfileAndQoSProvider = this.mSemQoSProfileShareProvider;
                                                            if (semWifiProfileAndQoSProvider != null) {
                                                                semWifiProfileAndQoSProvider.requestPassword(false, null, null);
                                                                retValue = 0;
                                                                break;
                                                            }
                                                            break;
                                                        case 44:
                                                            enforceNetworkSettingsPermission();
                                                            if (this.mSemQoSProfileShareProvider != null && (msg.obj instanceof Bundle)) {
                                                                Bundle args9 = (Bundle) msg.obj;
                                                                SemWifiProfileAndQoSProvider semWifiProfileAndQoSProvider2 = this.mSemQoSProfileShareProvider;
                                                                if (msg.arg1 == 1) {
                                                                    z = true;
                                                                }
                                                                semWifiProfileAndQoSProvider2.setUserConfirm(z, args9.getString("userData"));
                                                                retValue = 0;
                                                                break;
                                                            }
                                                        default:
                                                            switch (i) {
                                                                case ISupplicantStaIfaceCallback.StatusCode.FINITE_CYCLIC_GROUP_NOT_SUPPORTED:
                                                                case ISupplicantStaIfaceCallback.StatusCode.TRANSMISSION_FAILURE:
                                                                    break;
                                                                case ISupplicantStaIfaceCallback.StatusCode.CANNOT_FIND_ALT_TBTT:
                                                                    enforceChangePermission();
                                                                    if (msg.obj != null && (msg.obj instanceof Bundle)) {
                                                                        Bundle args10 = (Bundle) msg.obj;
                                                                        if ("MHS".equals(args10.getString("extra_type"))) {
                                                                            this.mWifiInjector.getSemSoftapConfig().addMHSDumpLog(args10.getString("extra_log"));
                                                                        } else {
                                                                            addHistoricalDumpLog(args10.getString("extra_log"));
                                                                        }
                                                                        retValue = 0;
                                                                        break;
                                                                    }
                                                                default:
                                                                    switch (i) {
                                                                        case 109:
                                                                        case MobileWipsScanResult.InformationElement.EID_ROAMING_CONSORTIUM /*{ENCODED_INT: 111}*/:
                                                                            break;
                                                                        case SoapEnvelope.VER11 /*{ENCODED_INT: 110}*/:
                                                                            break;
                                                                        default:
                                                                            Log.e(TAG, "not implementated yet. command id:" + msg.what);
                                                                            break;
                                                                    }
                                                            }
                                                            break;
                                                    }
                                                    break;
                                            }
                                        } else {
                                            enforceProvideDiagnosticsPermission();
                                            if (msg.obj != null && (msg.obj instanceof Bundle)) {
                                                Bundle args11 = (Bundle) msg.obj;
                                                WifiGuiderManagementService wifiGuiderManagementService2 = this.mWifiGuiderManagementService;
                                                if (wifiGuiderManagementService2 != null) {
                                                    retValue = wifiGuiderManagementService2.registerAction(args11);
                                                }
                                            }
                                        }
                                    }
                                    enforceChangePermission();
                                    retValue = this.mClientModeImpl.callSECApi(msg);
                                } else {
                                    enforceChangePermission();
                                    Bundle args12 = (Bundle) msg.obj;
                                    if (args12 != null) {
                                        if (args12.getBoolean("enable", false)) {
                                            Log.d(TAG, "SEC_COMMAND_ID_SET_WIFI_SCAN_WITH_P2P - WiFi scan with p2p -> Stop Scan(Anqp), Stop Assoc, Stop WPS?");
                                            this.mClientModeImpl.sendCallSECApiAsync(msg, callingPid);
                                        } else {
                                            Log.d(TAG, "SEC_COMMAND_ID_SET_WIFI_SCAN_WITH_P2P - Start Scan(Anqp), Start Assoc");
                                            this.mClientModeImpl.sendCallSECApiAsync(msg, callingPid);
                                        }
                                    }
                                    retValue = 0;
                                }
                                enforceChangePermission();
                                this.mClientModeImpl.sendCallSECApiAsync(msg, callingPid);
                                retValue = 0;
                            } else {
                                enforceNetworkSettingsPermission();
                                if (msg.obj instanceof Bundle) {
                                    Bundle args13 = (Bundle) msg.obj;
                                    setWifiEnabled(WifiEnableWarningPolicy.WIFI_STATE_CHANGE_WARNING + args13.getString("applabel"), true);
                                }
                                retValue = 0;
                            }
                        }
                        enforceChangePermission();
                        this.mClientModeImpl.sendCallSECApiAsync(msg, callingPid);
                        retValue = 0;
                    } else {
                        enforceChangePermission();
                        retValue = this.mClientModeImpl.syncCallSECApi(this.mClientModeImplChannel, msg);
                    }
                } else if (!checkAccessSecuredPermission(Binder.getCallingPid(), Binder.getCallingUid())) {
                    Log.e(TAG, "permission Denial");
                } else {
                    if (msg.arg1 >= 0) {
                        this.semIsTestMode = msg.arg1 == 1;
                        if (this.semIsTestMode) {
                            WifiNative wifiNative = this.mWifiNative;
                            wifiNative.disableRandomMac(wifiNative.getClientInterfaceName());
                        }
                    }
                    if (msg.arg2 != 1) {
                        this.mWifiEnableWarningPolicy.testConfig(false);
                    } else {
                        this.mWifiEnableWarningPolicy.testConfig(true);
                    }
                    retValue = 0;
                }
            } else {
                enforceAccessPermission();
                try {
                    this.mWifiNative.sendHostapdCommand("READ_WHITELIST");
                    retValue = 0;
                } catch (Exception e4) {
                    e4.printStackTrace();
                }
            }
            msg.recycle();
        }
        return retValue;
    }

    public String callSECStringApi(Message msg) {
        String retValue = null;
        if (msg != null) {
            int callingPid = Binder.getCallingPid();
            Log.d(TAG, "callSECStringApi msg.what=" + msg.what + ", callingPid:" + callingPid);
            int i = msg.what;
            if (i != 45) {
                if (!(i == 108 || i == 160)) {
                    if (i == 270) {
                        enforceAccessPermission();
                        this.mClientModeImpl.initializeWifiChipInfo();
                        retValue = WifiChipInfo.getInstance().getFirmwareVer(true);
                    } else if (i == 291) {
                        enforceAccessPermission();
                        AutoWifiController autoWifiController = this.mAutoWifiController;
                        if (autoWifiController != null) {
                            retValue = autoWifiController.getDebugString();
                        }
                    } else if (!(i == 300 || i == 317)) {
                        if (i == 403) {
                            enforceAccessPermission();
                            retValue = getQtables();
                        } else if (i == 406) {
                            enforceProvideDiagnosticsPermission();
                            retValue = this.mWifiGuiderPackageMonitor.dump();
                        } else if (i != 85) {
                            if (i == 86) {
                                enforceAccessPermission();
                                WifiGuiderManagementService wifiGuiderManagementService = this.mWifiGuiderManagementService;
                                if (wifiGuiderManagementService != null) {
                                    retValue = wifiGuiderManagementService.getVersion();
                                }
                            } else if (i == 223) {
                                enforceAccessPermission();
                                if (this.mClientModeImplChannel == null) {
                                    Log.e(TAG, "ClientModeImplHandler is not initialized");
                                } else {
                                    long ident = Binder.clearCallingIdentity();
                                    try {
                                        retValue = this.mClientModeImpl.syncCallSECStringApi(this.mClientModeImplChannel, msg);
                                    } finally {
                                        Binder.restoreCallingIdentity(ident);
                                    }
                                }
                            } else if (i != 224) {
                                switch (i) {
                                    case 274:
                                    case 275:
                                    case 276:
                                    case 277:
                                        enforceFactoryTestPermission();
                                        AsyncChannel asyncChannel = this.mClientModeImplChannel;
                                        if (asyncChannel != null) {
                                            retValue = this.mClientModeImpl.syncCallSECStringApi(asyncChannel, msg);
                                            break;
                                        }
                                        break;
                                    case DhcpPacket.MIN_PACKET_LENGTH_L2 /*{ENCODED_INT: 278}*/:
                                        if (checkAccessSecuredPermission(Binder.getCallingPid(), Binder.getCallingUid())) {
                                            AsyncChannel asyncChannel2 = this.mClientModeImplChannel;
                                            if (asyncChannel2 != null) {
                                                retValue = this.mClientModeImpl.syncCallSECStringApi(asyncChannel2, msg);
                                                break;
                                            }
                                        } else {
                                            Log.e(TAG, "permission Denial");
                                            break;
                                        }
                                        break;
                                    default:
                                        Log.d(TAG, "not implement yet. command id:" + msg.what);
                                        break;
                                }
                            } else {
                                enforceAccessPermission();
                                retValue = this.mHistoricalDumpLogs.toString();
                            }
                        }
                    }
                }
                enforceAccessPermission();
                AsyncChannel asyncChannel3 = this.mClientModeImplChannel;
                if (asyncChannel3 != null) {
                    retValue = this.mClientModeImpl.syncCallSECStringApi(asyncChannel3, msg);
                }
            } else {
                enforceAccessPermission();
                SemWifiProfileAndQoSProvider semWifiProfileAndQoSProvider = this.mSemQoSProfileShareProvider;
                if (semWifiProfileAndQoSProvider != null) {
                    retValue = semWifiProfileAndQoSProvider.dump();
                }
            }
            msg.recycle();
        }
        return retValue;
    }

    private int getWifiApChannel() {
        try {
            return Integer.parseInt(this.mWifiNative.sendHostapdCommand("GET_CHANNEL"));
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    public List<String> callSECListStringApi(Message msg) {
        List<String> retValue = new ArrayList<>();
        if (msg != null) {
            int callingPid = Binder.getCallingPid();
            Log.d(TAG, "callSECListStringApi msg.what=" + msg.what + ", callingPid:" + callingPid);
            if (msg.what != 91) {
                Log.d(TAG, "not implement yet. command id:" + msg.what);
            } else {
                enforceProvideDiagnosticsPermission();
                WifiGuiderManagementService wifiGuiderManagementService = this.mWifiGuiderManagementService;
                if (wifiGuiderManagementService != null) {
                    for (String result : wifiGuiderManagementService.getCachedDiagnosisResults()) {
                        retValue.add(result);
                    }
                }
            }
            msg.recycle();
        }
        return retValue;
    }

    private String getOAuthAgreements() {
        return Settings.Global.getString(this.mContext.getContentResolver(), "sem_wifi_allowed_oauth_provider");
    }

    private void updateOAuthAgreement(String oAuthProvider, boolean agree) {
        String oAuthProviders = getOAuthAgreements();
        if (agree) {
            if (!TextUtils.isEmpty(oAuthProviders)) {
                if (!oAuthProviders.contains("[" + oAuthProvider + "]")) {
                    ContentResolver contentResolver = this.mContext.getContentResolver();
                    Settings.Global.putString(contentResolver, "sem_wifi_allowed_oauth_provider", oAuthProviders + "[" + oAuthProvider + "]");
                    return;
                }
                return;
            }
            ContentResolver contentResolver2 = this.mContext.getContentResolver();
            Settings.Global.putString(contentResolver2, "sem_wifi_allowed_oauth_provider", "[" + oAuthProvider + "]");
        } else if (!TextUtils.isEmpty(oAuthProviders)) {
            ContentResolver contentResolver3 = this.mContext.getContentResolver();
            Settings.Global.putString(contentResolver3, "sem_wifi_allowed_oauth_provider", oAuthProviders.replace("[" + oAuthProvider + "]", ""));
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private String getPackageName(int pid) {
        List<ActivityManager.RunningAppProcessInfo> processInfo;
        if (pid < 0 || (processInfo = this.mActivityManager.getRunningAppProcesses()) == null) {
            return null;
        }
        for (ActivityManager.RunningAppProcessInfo item : processInfo) {
            if (item.pid == pid) {
                return item.processName;
            }
        }
        return null;
    }

    private boolean isFmcPackage() {
        String packagename = getPackageName(Binder.getCallingPid());
        Log.i(TAG, "isFmcPackage packageName : " + packagename);
        if (packagename.equals("com.sec.wevoip.wes_v3") || packagename.equals("com.amc.ui") || packagename.equals("com.amc.util")) {
            return true;
        }
        return false;
    }

    private String getStationInfo(String mac) {
        try {
            WifiNative wifiNative = this.mWifiNative;
            return wifiNative.sendHostapdCommand("GET_STA_INFO " + mac);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private boolean checkAndShowWifiSharingLiteDialog(String packageName) {
        int value = Settings.System.getInt(this.mContext.getContentResolver(), "wifi_sharing_lite_popup_status", 0);
        if (!supportWifiSharingLite() || !isWifiSharingEnabled() || !isMobileApOn() || value != 0) {
            return false;
        }
        Log.d(TAG, "WIFI sharing lite popup");
        SemWifiFrameworkUxUtils.showWarningDialog(this.mContext, 5, new String[]{packageName});
        return true;
    }

    private boolean checkAndShowFirmwareChangeDialog(String packageName) {
        if (isWifiSharingEnabled() || !isMobileApOn()) {
            return false;
        }
        Log.d(TAG, "Wifi is not allowed because MHS is enabled");
        SemWifiFrameworkUxUtils.showWarningDialog(this.mContext, 4, new String[]{packageName});
        return true;
    }

    public void setImsCallEstablished(boolean isEstablished) {
        enforceChangePermission();
        this.mClientModeImpl.setImsCallEstablished(isEstablished);
    }

    public void setAutoConnectCarrierApEnabled(boolean enabled) {
        enforceChangePermission();
        this.mClientModeImpl.setAutoConnectCarrierApEnabled(enabled);
    }

    public boolean setRvfMode(int value) {
        this.mWifiApConfigStore.setRvfMode(value);
        return value == getRvfMode();
    }

    public int getRvfMode() {
        return this.mWifiApConfigStore.getRvfMode();
    }

    public List<String> getWifiApStaListDetail() {
        try {
            return this.mWifiInjector.getSemWifiApClientInfo().getWifiApStaListDetail();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public String getWifiApStaList() {
        enforceChangePermission();
        try {
            return this.mWifiNative.sendHostapdCommand("GET_STA_LIST");
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public boolean supportWifiSharing() {
        boolean rbool = this.mWifiInjector.getSemWifiApChipInfo().supportWifiSharing();
        Log.d(TAG, "supportWifiSharing() " + rbool);
        return rbool;
    }

    public boolean isWifiSharingEnabled() {
        try {
            if (!supportWifiSharing()) {
                Log.i(TAG, "MOBILEAP_WIFI_CONCURRENCY feature is disabled");
                return false;
            } else if (Settings.Secure.getInt(this.mContext.getContentResolver(), "wifi_ap_wifi_sharing") == 1) {
                Log.i(TAG, "Wi-Fi Sharing has been enabled");
                return true;
            } else {
                if (Settings.Secure.getInt(this.mContext.getContentResolver(), "wifi_ap_wifi_sharing") == 0) {
                    Log.i(TAG, "Wi-Fi Sharing has been disabled");
                    return false;
                }
                return false;
            }
        } catch (Settings.SettingNotFoundException e) {
            Log.i(TAG, "Wi-Fi Sharing settings has not been accessed");
        }
    }

    public boolean isRegionFor5G() {
        String str = this.mCSCRegion;
        if (str == null) {
            return false;
        }
        if (str.equals("NA") || this.mCSCRegion.equals("KOR")) {
            return true;
        }
        return false;
    }

    public boolean isRegionFor5GCountry() {
        int testapilevel;
        int first_api_level = SystemProperties.getInt("ro.product.first_api_level", -1);
        if (MHSDBG && (testapilevel = SystemProperties.getInt("mhs.first_api_level", -1)) != -1) {
            first_api_level = testapilevel;
        }
        if (first_api_level >= 28) {
            if ("NA".equals(this.mCSCRegion) || "KOR".equals(this.mCSCRegion) || "EUR".equals(this.mCSCRegion) || "CHN".equals(this.mCSCRegion)) {
                return true;
            }
            if (!"extend".equals("default")) {
                return false;
            }
            if ("CIS".equals(this.mCSCRegion) || "SEA".equals(this.mCSCRegion) || "SWA".equals(this.mCSCRegion) || "LA".equals(this.mCSCRegion) || "AE".equals(this.mCountryIso) || "SA".equals(this.mCountryIso) || "ZA".equals(this.mCountryIso)) {
                return true;
            }
            return false;
        } else if ("NA".equals(this.mCSCRegion) || "KOR".equals(this.mCSCRegion)) {
            return true;
        } else {
            for (String region : "default".split(",")) {
                if (region.equals(this.mCSCRegion)) {
                    return true;
                }
            }
            return false;
        }
    }

    public void addMHSChannelHistoryLog(String log) {
        StringBuffer value = new StringBuffer();
        Log.i(TAG, log + " mhsch: " + mMHSChannelHistoryLogs.size());
        value.append(new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(Long.valueOf(System.currentTimeMillis())) + " " + log + "\n");
        if (mMHSChannelHistoryLogs.size() > 100) {
            mMHSChannelHistoryLogs.remove(0);
        }
        mMHSChannelHistoryLogs.add(value.toString());
    }

    public Messenger getIWCMessenger() {
        if (this.mIWCMessenger == null) {
            IWCMonitor iWCMonitor = this.mIWCMonitor;
            if (iWCMonitor != null) {
                this.mIWCMessenger = iWCMonitor.getIWCMessenger();
            } else {
                Log.d(TAG, "Could not get IWC Messenger");
            }
        }
        return this.mIWCMessenger;
    }

    private String getQtables() {
        IWCMonitor iWCMonitor = this.mIWCMonitor;
        if (iWCMonitor != null) {
            return iWCMonitor.getQtables();
        }
        return null;
    }

    private void setQtables(String qt) {
        IWCMonitor iWCMonitor = this.mIWCMonitor;
        if (iWCMonitor != null) {
            iWCMonitor.setQtables(qt, true);
        }
    }

    private void startFromBeginning(Bundle args) {
        Bundle b = args.deepCopy();
        IWCMonitor iWCMonitor = this.mIWCMonitor;
        if (iWCMonitor != null) {
            iWCMonitor.sendMessage(IWCMonitor.START_FROM_BEGINNING, b);
        }
    }

    private void restoreUserPreference(int type, int value) {
        IWCMonitor iWCMonitor = this.mIWCMonitor;
        if (iWCMonitor != null) {
            iWCMonitor.sendMessage(IWCMonitor.RESTORE_USER_PREFERENCE, type, value);
        }
    }

    private void bootUp() {
        this.semIsShutdown = false;
    }

    private void shutdown() {
        Log.d(TAG, "Shutdown is called");
        this.semIsShutdown = true;
        if (CSC_SEND_DHCP_RELEASE) {
            sendDhcpRelease();
        }
        this.mWifiController.sendMessage(155673);
        this.mClientModeImpl.setShutdown();
    }

    private void checkAndStartAutoWifi() {
        if (this.mClientModeImpl.isSupportedGeofence() && !this.mClientModeImpl.isWifiOnly()) {
            HandlerThread autoWifiThread = new HandlerThread("AutoWifi");
            autoWifiThread.start();
            this.mAutoWifiController = new AutoWifiController(this.mContext, autoWifiThread.getLooper(), new AutoWifiController.AutoWifiAdapter() {
                /* class com.android.server.wifi.WifiServiceImpl.C047615 */

                @Override // com.samsung.android.server.wifi.AutoWifiController.AutoWifiAdapter
                public boolean setWifiEnabled(String packageName, boolean enabled) {
                    return WifiServiceImpl.this.setWifiEnabled(packageName, enabled);
                }

                @Override // com.samsung.android.server.wifi.AutoWifiController.AutoWifiAdapter
                public void notifyScanModeChanged() {
                    WifiServiceImpl.this.mWifiController.sendMessage(155655);
                }

                @Override // com.samsung.android.server.wifi.AutoWifiController.AutoWifiAdapter
                public boolean isWifiSharingEnabled() {
                    return WifiServiceImpl.this.isWifiSharingEnabled();
                }

                @Override // com.samsung.android.server.wifi.AutoWifiController.AutoWifiAdapter
                public int getWifiApEnabledState() {
                    return WifiServiceImpl.this.getWifiApEnabledState();
                }

                @Override // com.samsung.android.server.wifi.AutoWifiController.AutoWifiAdapter
                public int getWifiEnabledState() {
                    return WifiServiceImpl.this.getWifiEnabledState();
                }

                @Override // com.samsung.android.server.wifi.AutoWifiController.AutoWifiAdapter
                public NetworkInfo getNetworkInfo() {
                    return WifiServiceImpl.this.getNetworkInfo();
                }

                @Override // com.samsung.android.server.wifi.AutoWifiController.AutoWifiAdapter
                public int addOrUpdateNetwork(WifiConfiguration config) {
                    if (WifiServiceImpl.this.mClientModeImplChannel == null) {
                        return -1;
                    }
                    return WifiServiceImpl.this.mClientModeImpl.syncAddOrUpdateNetwork(WifiServiceImpl.this.mClientModeImplChannel, config);
                }

                @Override // com.samsung.android.server.wifi.AutoWifiController.AutoWifiAdapter
                public boolean startScan(String packageName) {
                    return WifiServiceImpl.this.mScanRequestProxy.startScan(Binder.getCallingUid(), packageName);
                }

                @Override // com.samsung.android.server.wifi.AutoWifiController.AutoWifiAdapter
                public WifiInfo getWifiInfo() {
                    return WifiServiceImpl.this.mClientModeImpl.getWifiInfo();
                }

                @Override // com.samsung.android.server.wifi.AutoWifiController.AutoWifiAdapter
                public WifiConfiguration getSpecificNetwork(int networkId) {
                    if (WifiServiceImpl.this.mClientModeImplChannel != null) {
                        return WifiServiceImpl.this.mClientModeImpl.syncGetSpecificNetwork(WifiServiceImpl.this.mClientModeImplChannel, networkId);
                    }
                    return null;
                }

                @Override // com.samsung.android.server.wifi.AutoWifiController.AutoWifiAdapter
                public List<WifiConfiguration> getConfiguredNetworks() {
                    if (WifiServiceImpl.this.mClientModeImplChannel == null) {
                        return null;
                    }
                    return WifiServiceImpl.this.mClientModeImpl.syncGetConfiguredNetworks(1000, WifiServiceImpl.this.mClientModeImplChannel, 1010);
                }

                @Override // com.samsung.android.server.wifi.AutoWifiController.AutoWifiAdapter
                public List<ScanResult> getScanResults() {
                    List<ScanResult> scanResults = new ArrayList<>();
                    scanResults.addAll(WifiServiceImpl.this.mScanRequestProxy.getScanResults());
                    return scanResults;
                }

                @Override // com.samsung.android.server.wifi.AutoWifiController.AutoWifiAdapter
                public boolean isUnstableAp(String bssid) {
                    return WifiServiceImpl.this.mClientModeImpl.isUnstableAp(bssid);
                }

                @Override // com.samsung.android.server.wifi.AutoWifiController.AutoWifiAdapter
                public boolean isWifiToggleEnabled() {
                    return WifiServiceImpl.this.mSettingsStore.isWifiToggleEnabled();
                }

                @Override // com.samsung.android.server.wifi.AutoWifiController.AutoWifiAdapter
                public int getWifiSavedState() {
                    return WifiServiceImpl.this.mSettingsStore.getWifiSavedState();
                }

                @Override // com.samsung.android.server.wifi.AutoWifiController.AutoWifiAdapter
                public void setWifiSavedState(int state) {
                    WifiServiceImpl.this.mSettingsStore.setWifiSavedState(state);
                }

                @Override // com.samsung.android.server.wifi.AutoWifiController.AutoWifiAdapter
                public void setScanAlwaysAvailable(boolean enabled) {
                    WifiServiceImpl.this.mSettingsStore.setScanAlwaysAvailable(enabled);
                }

                @Override // com.samsung.android.server.wifi.AutoWifiController.AutoWifiAdapter
                public void obtainScanAlwaysAvailablePolicy(boolean enabled) {
                    WifiServiceImpl.this.mSettingsStore.obtainScanAlwaysAvailablePolicy(enabled);
                }

                @Override // com.samsung.android.server.wifi.AutoWifiController.AutoWifiAdapter
                public void requestGeofenceEnabled(boolean enabled, String packageName) {
                    WifiServiceImpl.this.mClientModeImpl.requestGeofenceState(enabled, packageName);
                }

                @Override // com.samsung.android.server.wifi.AutoWifiController.AutoWifiAdapter
                public int getCurrentGeofenceState() {
                    return WifiServiceImpl.this.mClientModeImpl.getCurrentGeofenceState();
                }

                @Override // com.samsung.android.server.wifi.AutoWifiController.AutoWifiAdapter
                public List<String> getGeofenceEnterKeys() {
                    return WifiServiceImpl.this.mClientModeImpl.getGeofenceEnterKeys();
                }

                @Override // com.samsung.android.server.wifi.AutoWifiController.AutoWifiAdapter
                public int getCellCount(String configKey) {
                    return WifiServiceImpl.this.mClientModeImpl.getGeofenceCellCount(configKey);
                }

                @Override // com.samsung.android.server.wifi.AutoWifiController.AutoWifiAdapter
                public WifiScanner getWifiScanner() {
                    return WifiServiceImpl.this.mWifiInjector.getWifiScanner();
                }
            });
            this.mAutoWifiController.checkAndStart();
            this.mClientModeImpl.setWifiGeofenceListener(this.mAutoWifiController.getGeofenceListener());
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private TelephonyManager getTelephonyManager() {
        if (this.mTelephonyManager == null) {
            this.mTelephonyManager = this.mWifiInjector.makeTelephonyManager();
        }
        return this.mTelephonyManager;
    }

    public void sendDhcpRelease() {
        Log.d(TAG, "sendMessage - ClientModeImpl.CMD_SEND_DHCP_RELEASE");
        this.mClientModeImpl.sendMessage(ClientModeImpl.CMD_SEND_DHCP_RELEASE);
    }

    private void addHistoricalDumpLog(String log) {
        if (this.mHistoricalDumpLogs.size() > 20) {
            this.mHistoricalDumpLogs.remove(0);
        }
        this.mHistoricalDumpLogs.add(log);
    }

    private void setWifiEnabledTriggered(boolean enable, String packageName, boolean triggeredByUser) {
        insertLogForWifiEnabled(enable, packageName);
        AutoWifiController autoWifiController = this.mAutoWifiController;
        if (autoWifiController != null) {
            autoWifiController.setWifiEnabledTriggered(enable, packageName, triggeredByUser);
        }
    }

    public void insertLogForWifiEnabled(boolean isEnabled, String packageName) {
        String str;
        NetworkInfo info;
        boolean isConnected = false;
        if (!isEnabled && (info = getNetworkInfo()) != null && info.isConnected()) {
            isConnected = true;
        }
        boolean isSnsEnabled = false;
        if (Settings.Global.getInt(this.mContext.getContentResolver(), "wifi_watchdog_poor_network_test_enabled", 0) == 1) {
            isSnsEnabled = true;
        }
        StringBuilder sb = new StringBuilder();
        String str2 = "1";
        sb.append(isEnabled ? str2 : "0");
        sb.append(" ");
        sb.append(packageName == null ? "x" : packageName);
        sb.append(" ");
        if (isConnected) {
            str = str2;
        } else {
            str = "0";
        }
        sb.append(str);
        sb.append(" ");
        if (!isSnsEnabled) {
            str2 = "0";
        }
        sb.append(str2);
        String data = sb.toString();
        Message msg = Message.obtain();
        msg.what = 77;
        msg.obj = WifiBigDataLogManager.getBigDataBundle(WifiBigDataLogManager.FEATURE_ON_OFF, data);
        this.mClientModeImpl.callSECApi(msg);
    }

    private final class SemWifiApSmartCallbackImpl implements WifiManager.SemWifiApSmartCallback {
        private SemWifiApSmartCallbackImpl() {
        }

        public synchronized void onStateChanged(int state, String mhsMac) {
            WifiServiceImpl.this.mSemWifiApSmartState = state;
            WifiServiceImpl.this.mSemWifiApSmartMhsMac = mhsMac;
            Iterator<ISemWifiApSmartCallback> iterator = WifiServiceImpl.this.mRegisteredSemWifiApSmartCallbacks.values().iterator();
            while (iterator.hasNext()) {
                try {
                    iterator.next().onStateChanged(state, mhsMac);
                } catch (RemoteException e) {
                    Log.e(WifiServiceImpl.TAG, "onStateChanged: remote exception -- " + e);
                    iterator.remove();
                }
            }
        }
    }

    public void registerSemWifiApSmartCallback(final IBinder binder, ISemWifiApSmartCallback callback, final int callbackIdentifier) {
        if (binder == null) {
            throw new IllegalArgumentException("Binder must not be null");
        } else if (callback != null) {
            enforceNetworkSettingsPermission();
            if (this.mVerboseLoggingEnabled) {
                this.mLog.info("registerSemWifiApSmartCallback uid=%").mo2069c((long) Binder.getCallingUid()).flush();
            }
            try {
                binder.linkToDeath(new IBinder.DeathRecipient() {
                    /* class com.android.server.wifi.WifiServiceImpl.C047716 */

                    public void binderDied() {
                        binder.unlinkToDeath(this, 0);
                        WifiServiceImpl.this.mAsyncChannelExternalClientHandler.post(new Runnable(callbackIdentifier) {
                            /* class com.android.server.wifi.$$Lambda$WifiServiceImpl$16$2im346giHxSfmKeamCFGtQZfXp0 */
                            private final /* synthetic */ int f$1;

                            {
                                this.f$1 = r2;
                            }

                            public final void run() {
                                WifiServiceImpl.C047716.this.lambda$binderDied$0$WifiServiceImpl$16(this.f$1);
                            }
                        });
                    }

                    public /* synthetic */ void lambda$binderDied$0$WifiServiceImpl$16(int callbackIdentifier) {
                        WifiServiceImpl.this.mRegisteredSemWifiApSmartCallbacks.remove(Integer.valueOf(callbackIdentifier));
                    }
                }, 0);
                this.mAsyncChannelExternalClientHandler.post(new Runnable(callbackIdentifier, callback) {
                    /* class com.android.server.wifi.$$Lambda$WifiServiceImpl$r3TdStK186dxOwLD7XsFhUQzr24 */
                    private final /* synthetic */ int f$1;
                    private final /* synthetic */ ISemWifiApSmartCallback f$2;

                    {
                        this.f$1 = r2;
                        this.f$2 = r3;
                    }

                    public final void run() {
                        WifiServiceImpl.this.lambda$registerSemWifiApSmartCallback$32$WifiServiceImpl(this.f$1, this.f$2);
                    }
                });
            } catch (RemoteException e) {
                Log.e(TAG, "Error on linkToDeath - " + e);
            }
        } else {
            throw new IllegalArgumentException("Callback must not be null");
        }
    }

    public /* synthetic */ void lambda$registerSemWifiApSmartCallback$32$WifiServiceImpl(int callbackIdentifier, ISemWifiApSmartCallback callback) {
        this.mRegisteredSemWifiApSmartCallbacks.put(Integer.valueOf(callbackIdentifier), callback);
        if (this.mRegisteredSemWifiApSmartCallbacks.size() > 20) {
            Log.wtf(TAG, "Too many SemWifiApSmartCallback AP callbacks: " + this.mRegisteredSemWifiApSmartCallbacks.size());
        } else if (this.mRegisteredSemWifiApSmartCallbacks.size() > 10) {
            Log.w(TAG, "Too many SemWifiApSmartCallback AP callbacks: " + this.mRegisteredSemWifiApSmartCallbacks.size());
        }
        try {
            callback.onStateChanged(this.mSemWifiApSmartState, this.mSemWifiApSmartMhsMac);
        } catch (RemoteException e) {
            Log.e(TAG, "registerSemWifiApSmartCallback: remote exception -- " + e);
        }
    }

    public void unregisterSemWifiApSmartCallback(int callbackIdentifier) {
        enforceNetworkSettingsPermission();
        if (this.mVerboseLoggingEnabled) {
            this.mLog.info("unregisterSemWifiApSmartCallback uid=%").mo2069c((long) Binder.getCallingUid()).flush();
        }
        this.mAsyncChannelExternalClientHandler.post(new Runnable(callbackIdentifier) {
            /* class com.android.server.wifi.$$Lambda$WifiServiceImpl$jzLnI8u6WLZ5wg9Sbs5Y6G3hSPk */
            private final /* synthetic */ int f$1;

            {
                this.f$1 = r2;
            }

            public final void run() {
                WifiServiceImpl.this.lambda$unregisterSemWifiApSmartCallback$33$WifiServiceImpl(this.f$1);
            }
        });
    }

    public /* synthetic */ void lambda$unregisterSemWifiApSmartCallback$33$WifiServiceImpl(int callbackIdentifier) {
        this.mRegisteredSemWifiApSmartCallbacks.remove(Integer.valueOf(callbackIdentifier));
    }

    public List<SemWifiApBleScanResult> semGetWifiApBleScanDetail() {
        long ident = Binder.clearCallingIdentity();
        List<SemWifiApBleScanResult> res = null;
        try {
            if (this.mSemWifiApSmartClient != null) {
                res = this.mSemWifiApSmartClient.getWifiApBleScanResults();
            }
            return res;
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    public boolean semWifiApBleClientRole(boolean enable) {
        long ident = Binder.clearCallingIdentity();
        boolean res = false;
        try {
            if (this.mSemWifiApSmartClient != null) {
                res = this.mSemWifiApSmartClient.setWifiApSmartClient(enable);
            }
            return res;
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    public boolean semWifiApBleMhsRole(boolean enable) {
        boolean res = false;
        long ident = Binder.clearCallingIdentity();
        try {
            if (this.mSemWifiApSmartMHS != null) {
                res = this.mSemWifiApSmartMHS.setWifiApSmartMHS(enable);
            }
            return res;
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    public boolean connectToSmartMHS(String addr, int type, int mhidden, int mSecurity, String mhs_mac, String mUserName, int ver) {
        boolean res = false;
        long ident = Binder.clearCallingIdentity();
        try {
            if (this.mSemWifiApSmartGattClient != null) {
                res = this.mSemWifiApSmartGattClient.connectToSmartMHS(addr, type, mhidden, mSecurity, mhs_mac, mUserName, ver);
            }
            return res;
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    public int getSmartApConnectedStatus(String mhs_mac) {
        int res = -1;
        long ident = Binder.clearCallingIdentity();
        try {
            if (this.mSemWifiApSmartGattClient != null) {
                res = this.mSemWifiApSmartGattClient.getSmartApConnectedStatus(mhs_mac);
            }
            return res;
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    public int getSmartApConnectedStatusFromScanResult(String clientmac) {
        int res = -1;
        long ident = Binder.clearCallingIdentity();
        try {
            if (this.mSemWifiApSmartGattClient != null) {
                res = this.mSemWifiApSmartGattClient.getSmartApConnectedStatusFromScanResult(clientmac);
            }
            return res;
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    public boolean isWifiApWpa3Supported() {
        Binder.restoreCallingIdentity(Binder.clearCallingIdentity());
        return false;
    }

    public List<SemWifiApBleScanResult> semGetWifiApBleD2DScanDetail() {
        List<SemWifiApBleScanResult> res = null;
        long ident = Binder.clearCallingIdentity();
        try {
            if (this.mSemWifiApSmartD2DMHS != null) {
                res = this.mSemWifiApSmartD2DMHS.getWifiApBleD2DScanResults();
            }
            return res;
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    public boolean semWifiApBleD2DClientRole(boolean enable) {
        boolean res = true;
        long ident = Binder.clearCallingIdentity();
        try {
            if (this.mSemWifiApSmartD2DClient != null) {
                res = this.mSemWifiApSmartD2DClient.semWifiApBleD2DClientRole(enable);
            }
            return res;
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    public boolean semWifiApBleD2DMhsRole(boolean enable) {
        boolean res = true;
        long ident = Binder.clearCallingIdentity();
        try {
            if (this.mSemWifiApSmartD2DMHS != null) {
                res = this.mSemWifiApSmartD2DMHS.semWifiApBleD2DMhsRole(enable);
            }
            return res;
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    public boolean connectToSmartD2DClient(String bleaddr, String client_mac, ISemWifiApSmartCallback callback) {
        boolean res = true;
        long ident = Binder.clearCallingIdentity();
        try {
            if (this.mSemWifiApSmartD2DGattClient != null) {
                res = this.mSemWifiApSmartD2DGattClient.connectToSmartD2DClient(bleaddr, client_mac, callback);
            }
            return res;
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    public int getSmartD2DClientConnectedStatus(String mac) {
        int res = -1;
        long ident = Binder.clearCallingIdentity();
        try {
            if (this.mSemWifiApSmartD2DGattClient != null) {
                res = this.mSemWifiApSmartD2DGattClient.getSmartD2DClientConnectedStatus(mac);
            }
            return res;
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    public String semLoadMonitorModeFirmware(boolean enable) {
        Log.d(TAG, "semLoadMonitorModeFirmware : enable = " + enable);
        if (this.mWlanSnifferController == null) {
            this.mWlanSnifferController = new WlanSnifferController(this.mContext);
        }
        long ident = Binder.clearCallingIdentity();
        try {
            return this.mWlanSnifferController.semLoadMonitorModeFirmware(enable);
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    public String semCheckMonitorMode() {
        Log.d(TAG, "semCheckMonitorMode");
        if (this.mWlanSnifferController == null) {
            this.mWlanSnifferController = new WlanSnifferController(this.mContext);
        }
        long ident = Binder.clearCallingIdentity();
        try {
            return this.mWlanSnifferController.semCheckMonitorMode();
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    public String semStartMonitorMode(int ch, int bw) {
        Log.d(TAG, "semStartMonitorMode : ch = " + ch + " : bw = " + bw);
        if (this.mWlanSnifferController == null) {
            this.mWlanSnifferController = new WlanSnifferController(this.mContext);
        }
        long ident = Binder.clearCallingIdentity();
        try {
            return this.mWlanSnifferController.semStartMonitorMode(ch, bw);
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    public String semStartAirlogs(boolean enable, boolean compressiveMode) {
        Log.i(TAG, "semStartAirlogs : enable = " + enable + " : compressiveMode = " + compressiveMode);
        if (this.mWlanSnifferController == null) {
            this.mWlanSnifferController = new WlanSnifferController(this.mContext);
        }
        long ident = Binder.clearCallingIdentity();
        try {
            return this.mWlanSnifferController.semStartAirlogs(enable, compressiveMode);
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    public String semStopMonitorMode() {
        Log.d(TAG, "semStopMonitorMode");
        if (this.mWlanSnifferController == null) {
            this.mWlanSnifferController = new WlanSnifferController(this.mContext);
        }
        long ident = Binder.clearCallingIdentity();
        try {
            return this.mWlanSnifferController.semStopMonitorMode();
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    public Map<String, Map<String, Integer>> getQoSScores(List<String> bssids) {
        try {
            enforceNetworkSettingsPermission();
            if (this.mSemQoSProfileShareProvider != null) {
                return this.mSemQoSProfileShareProvider.getQoSScores(bssids);
            }
            return null;
        } catch (SecurityException e) {
            Slog.e(TAG, "Permission violation - getQoSScores not allowed" + e);
            return null;
        }
    }

    public void requestPasswordToDevice(String bssid, ISharedPasswordCallback callback) {
        try {
            Log.i(TAG, "requestPasswordToDevice");
            enforceNetworkSettingsPermission();
            if (this.mSemQoSProfileShareProvider != null) {
                this.mSemQoSProfileShareProvider.requestPassword(true, bssid, callback);
            }
        } catch (SecurityException e) {
            Slog.e(TAG, "Permission violation - getQoSScores not allowed" + e);
        }
    }

    public void requestPasswordToDevicePost(boolean enable) {
        try {
            Log.i(TAG, "requestPasswordToDevicePost, enable : " + enable);
            enforceNetworkSettingsPermission();
            if (this.mSemQoSProfileShareProvider != null) {
                this.mSemQoSProfileShareProvider.requestPasswordPost(enable);
            }
        } catch (SecurityException e) {
            Slog.e(TAG, "Permission violation - getQoSScores not allowed" + e);
        }
    }

    public double[] getLatitudeLongitude(WifiConfiguration config) {
        String packageName = this.mContext.getOpPackageName();
        int uid = Binder.getCallingUid();
        try {
            enforceLocationPermission(packageName, uid);
            return this.mClientModeImpl.getLatitudeLongitude(config);
        } catch (SecurityException e) {
            Slog.e(TAG, "Permission violation - getLatitudeLongitude not allowed for uid=" + uid + ", packageName=" + packageName + ", reason=" + e);
            return null;
        }
    }

    public void sendQCResultToWCM(Message msg) {
        this.mWifiConnectivityMonitor.sendQCResultToWCM(msg);
    }

    public void setValidationCheckStart() {
        this.mWifiConnectivityMonitor.setValidationCheckStart();
    }

    public void sendValidationCheckModeResult(boolean valid) {
        this.mWifiConnectivityMonitor.sendValidationCheckModeResult(valid);
    }
}
