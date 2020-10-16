package com.android.server.wifi;

import android.app.ActivityManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.hardware.input.InputManager;
import android.location.Address;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.DhcpResults;
import android.net.IpConfiguration;
import android.net.KeepalivePacketData;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.MacAddress;
import android.net.MatchAllNetworkSpecifier;
import android.net.NattKeepalivePacketData;
import android.net.Network;
import android.net.NetworkAgent;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkMisc;
import android.net.NetworkUtils;
import android.net.RouteInfo;
import android.net.SocketKeepalive;
import android.net.StaticIpConfiguration;
import android.net.TcpKeepalivePacketData;
import android.net.ip.IIpClient;
import android.net.ip.IpClientCallbacks;
import android.net.ip.IpClientManager;
import android.net.shared.ProvisioningConfiguration;
import android.net.util.InterfaceParams;
import android.net.wifi.INetworkRequestMatchCallback;
import android.net.wifi.RssiPacketCountInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiEnterpriseConfig;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkAgentSpecifier;
import android.net.wifi.hotspot2.IProvisioningCallback;
import android.net.wifi.hotspot2.OsuProvider;
import android.net.wifi.hotspot2.PasspointConfiguration;
import android.net.wifi.p2p.IWifiP2pManager;
import android.net.wifi.p2p.WifiP2pGroup;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.ConditionVariable;
import android.os.Debug;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.WorkSource;
import android.provider.Settings;
import android.sec.enterprise.EnterpriseDeviceManager;
import android.sec.enterprise.WifiPolicy;
import android.sec.enterprise.WifiPolicyCache;
import android.sec.enterprise.certificate.CertificatePolicy;
import android.system.OsConstants;
import android.telephony.CellIdentityNr;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoNr;
import android.telephony.CellInfoWcdma;
import android.telephony.CellLocation;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;
import android.text.TextUtils;
import android.util.LocalLog;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;
import android.util.StatsLog;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.app.IBatteryStats;
import com.android.internal.telephony.ITelephony;
import com.android.internal.util.AsyncChannel;
import com.android.internal.util.MessageUtils;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import com.android.server.wifi.ClientModeManager;
import com.android.server.wifi.WifiController;
import com.android.server.wifi.WifiGeofenceManager;
import com.android.server.wifi.WifiMulticastLockManager;
import com.android.server.wifi.WifiNative;
import com.android.server.wifi.hotspot2.AnqpEvent;
import com.android.server.wifi.hotspot2.IconEvent;
import com.android.server.wifi.hotspot2.NetworkDetail;
import com.android.server.wifi.hotspot2.PasspointManager;
import com.android.server.wifi.hotspot2.Utils;
import com.android.server.wifi.hotspot2.WnmData;
import com.android.server.wifi.hotspot2.anqp.ANQPElement;
import com.android.server.wifi.hotspot2.anqp.Constants;
import com.android.server.wifi.hotspot2.anqp.HSWanMetricsElement;
import com.android.server.wifi.hotspot2.anqp.VenueNameElement;
import com.android.server.wifi.p2p.WifiP2pServiceImpl;
import com.android.server.wifi.rtt.RttServiceImpl;
import com.android.server.wifi.util.NativeUtil;
import com.android.server.wifi.util.StringUtil;
import com.android.server.wifi.util.TelephonyUtil;
import com.android.server.wifi.util.WifiPermissionsUtil;
import com.android.server.wifi.util.WifiPermissionsWrapper;
import com.samsung.android.feature.SemCscFeature;
import com.samsung.android.feature.SemFloatingFeature;
import com.samsung.android.knox.custom.CustomDeviceManagerProxy;
import com.samsung.android.location.SemLocationListener;
import com.samsung.android.location.SemLocationManager;
import com.samsung.android.net.wifi.OpBrandingLoader;
import com.samsung.android.server.wifi.ArpPeer;
import com.samsung.android.server.wifi.SemSarManager;
import com.samsung.android.server.wifi.SemWifiFrameworkUxUtils;
import com.samsung.android.server.wifi.SemWifiHiddenNetworkTracker;
import com.samsung.android.server.wifi.UnstableApController;
import com.samsung.android.server.wifi.WifiB2BConfigurationPolicy;
import com.samsung.android.server.wifi.WifiDelayDisconnect;
import com.samsung.android.server.wifi.WifiMobileDeviceManager;
import com.samsung.android.server.wifi.WifiRoamingAssistant;
import com.samsung.android.server.wifi.WlanTestHelper;
import com.samsung.android.server.wifi.bigdata.WifiBigDataLogManager;
import com.samsung.android.server.wifi.bigdata.WifiChipInfo;
import com.samsung.android.server.wifi.dqa.ReportIdKey;
import com.samsung.android.server.wifi.dqa.ReportUtil;
import com.samsung.android.server.wifi.dqa.SemWifiIssueDetector;
import com.samsung.android.server.wifi.mobilewips.framework.MobileWipsFrameworkService;
import com.samsung.android.server.wifi.mobilewips.framework.MobileWipsScanResult;
import com.samsung.android.server.wifi.mobilewips.framework.MobileWipsWifiSsid;
import com.samsung.android.server.wifi.softap.DhcpPacket;
import com.samsung.android.server.wifi.softap.SemConnectivityPacketTracker;
import com.samsung.android.server.wifi.softap.SemWifiApBroadcastReceiver;
import com.samsung.android.server.wifi.softap.SemWifiApPowerSaveImpl;
import com.sec.android.app.C0852CscFeatureTagCommon;
import com.sec.android.app.CscFeatureTagCOMMON;
import com.sec.android.app.CscFeatureTagWifi;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.ksoap2.SoapEnvelope;

public class ClientModeImpl extends StateMachine {
    private static final String ACTION_AP_LOCATION_PASSIVE_REQUEST = "com.android.server.wifi.AP_LOCATION_PASSIVE_REQUEST";
    private static final int ACTIVE_REQUEST_LOCATION = 1;
    static final int BASE = 131072;
    private static final String CHARSET_CN = "gbk";
    private static final String CHARSET_KOR = "ksc5601";
    private static final int CMD_24HOURS_PASSED_AFTER_BOOT = 131579;
    static final int CMD_ACCEPT_UNVALIDATED = 131225;
    static final int CMD_ADD_KEEPALIVE_PACKET_FILTER_TO_APF = 131281;
    static final int CMD_ADD_OR_UPDATE_NETWORK = 131124;
    static final int CMD_ADD_OR_UPDATE_PASSPOINT_CONFIG = 131178;
    static final int CMD_ASSOCIATED_BSSID = 131219;
    private static final int CMD_AUTO_CONNECT_CARRIER_AP_ENABLED = 131316;
    static final int CMD_BLUETOOTH_ADAPTER_STATE_CHANGE = 131103;
    static final int CMD_BOOT_COMPLETED = 131206;
    private static final int CMD_CHECK_ARP_RESULT = 131622;
    static final int CMD_CONFIG_ND_OFFLOAD = 131276;
    static final int CMD_DIAGS_CONNECT_TIMEOUT = 131324;
    static final int CMD_DISABLE_EPHEMERAL_NETWORK = 131170;
    static final int CMD_DISCONNECT = 131145;
    static final int CMD_DISCONNECTING_WATCHDOG_TIMER = 131168;
    static final int CMD_ENABLE_NETWORK = 131126;
    static final int CMD_ENABLE_RSSI_POLL = 131154;
    static final int CMD_ENABLE_TDLS = 131164;
    static final int CMD_ENABLE_WIFI_CONNECTIVITY_MANAGER = 131238;
    static final int CMD_FORCINGLY_ENABLE_ALL_NETWORKS = 131402;
    static final int CMD_GET_ALL_MATCHING_FQDNS_FOR_SCAN_RESULTS = 131240;
    private static final int CMD_GET_A_CONFIGURED_NETWORK = 131581;
    static final int CMD_GET_CONFIGURED_NETWORKS = 131131;
    static final int CMD_GET_LINK_LAYER_STATS = 131135;
    static final int CMD_GET_MATCHING_OSU_PROVIDERS = 131181;
    static final int CMD_GET_MATCHING_PASSPOINT_CONFIGS_FOR_OSU_PROVIDERS = 131182;
    static final int CMD_GET_PASSPOINT_CONFIGS = 131180;
    static final int CMD_GET_PRIVILEGED_CONFIGURED_NETWORKS = 131134;
    static final int CMD_GET_SUPPORTED_FEATURES = 131133;
    static final int CMD_GET_WIFI_CONFIGS_FOR_PASSPOINT_PROFILES = 131184;
    private static final int CMD_IMS_CALL_ESTABLISHED = 131315;
    static final int CMD_INITIALIZE = 131207;
    static final int CMD_INSTALL_PACKET_FILTER = 131274;
    static final int CMD_IPV4_PROVISIONING_FAILURE = 131273;
    static final int CMD_IPV4_PROVISIONING_SUCCESS = 131272;
    static final int CMD_IP_CONFIGURATION_LOST = 131211;
    static final int CMD_IP_CONFIGURATION_SUCCESSFUL = 131210;
    static final int CMD_IP_REACHABILITY_LOST = 131221;
    static final int CMD_MATCH_PROVIDER_NETWORK = 131177;
    static final int CMD_NETWORK_STATUS = 131220;
    static final int CMD_ONESHOT_RSSI_POLL = 131156;
    private static final int CMD_POST_DHCP_ACTION = 131329;
    @VisibleForTesting
    static final int CMD_PRE_DHCP_ACTION = 131327;
    private static final int CMD_PRE_DHCP_ACTION_COMPLETE = 131328;
    static final int CMD_QUERY_OSU_ICON = 131176;
    static final int CMD_READ_PACKET_FILTER = 131280;
    static final int CMD_REASSOCIATE = 131147;
    static final int CMD_RECONNECT = 131146;
    private static final int CMD_RELOAD_CONFIG_STORE_FILE = 131583;
    static final int CMD_REMOVE_APP_CONFIGURATIONS = 131169;
    static final int CMD_REMOVE_KEEPALIVE_PACKET_FILTER_FROM_APF = 131282;
    static final int CMD_REMOVE_NETWORK = 131125;
    static final int CMD_REMOVE_PASSPOINT_CONFIG = 131179;
    static final int CMD_REMOVE_USER_CONFIGURATIONS = 131224;
    private static final int CMD_REPLACE_PUBLIC_DNS = 131286;
    static final int CMD_RESET_SIM_NETWORKS = 131173;
    static final int CMD_RESET_SUPPLICANT_STATE = 131183;
    static final int CMD_ROAM_WATCHDOG_TIMER = 131166;
    static final int CMD_RSSI_POLL = 131155;
    static final int CMD_RSSI_THRESHOLD_BREACHED = 131236;
    public static final int CMD_SCAN_RESULT_AVAILABLE = 131584;
    static final int CMD_SCREEN_STATE_CHANGED = 131167;
    private static final int CMD_SEC_API = 131574;
    private static final int CMD_SEC_API_ASYNC = 131573;
    public static final int CMD_SEC_LOGGING = 131576;
    private static final int CMD_SEC_STRING_API = 131575;
    private static final int CMD_SEND_ARP = 131623;
    public static final int CMD_SEND_DHCP_RELEASE = 131283;
    static final int CMD_SET_ADPS_MODE = 131383;
    static final int CMD_SET_FALLBACK_PACKET_FILTERING = 131275;
    static final int CMD_SET_HIGH_PERF_MODE = 131149;
    static final int CMD_SET_OPERATIONAL_MODE = 131144;
    static final int CMD_SET_SUSPEND_OPT_ENABLED = 131158;
    private static final int CMD_SHOW_TOAST_MSG = 131582;
    static final int CMD_START_CONNECT = 131215;
    static final int CMD_START_IP_PACKET_OFFLOAD = 131232;
    static final int CMD_START_ROAM = 131217;
    static final int CMD_START_RSSI_MONITORING_OFFLOAD = 131234;
    private static final int CMD_START_SUBSCRIPTION_PROVISIONING = 131326;
    static final int CMD_STOP_IP_PACKET_OFFLOAD = 131233;
    static final int CMD_STOP_RSSI_MONITORING_OFFLOAD = 131235;
    static final int CMD_TARGET_BSSID = 131213;
    static final int CMD_THREE_TIMES_SCAN_IN_IDLE = 131381;
    static final int CMD_UNWANTED_NETWORK = 131216;
    private static final int CMD_UPDATE_CONFIG_LOCATION = 131332;
    static final int CMD_UPDATE_LINKPROPERTIES = 131212;
    static final int CMD_USER_STOP = 131279;
    static final int CMD_USER_SWITCH = 131277;
    static final int CMD_USER_UNLOCK = 131278;
    private static final String CONFIG_CHARSET = OpBrandingLoader.getInstance().getSupportCharacterSet();
    private static final String CONFIG_SECURE_SVC_INTEGRATION = SemCscFeature.getInstance().getString(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_CONFIGSECURESVCINTEGRATION);
    public static final int CONNECT_MODE = 1;
    private static final String CSC_CONFIG_OP_BRANDING = SemCscFeature.getInstance().getString(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_CONFIGOPBRANDING);
    private static final boolean CSC_SUPPORT_5G_ANT_SHARE = SemCscFeature.getInstance().getBoolean(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_SUPPORT5GANTSHARE);
    private static final boolean CSC_WIFI_ERRORCODE = SemCscFeature.getInstance().getBoolean(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_ENABLEDETAILEAPERRORCODESANDSTATE);
    private static final boolean CSC_WIFI_SUPPORT_VZW_EAP_AKA = SemCscFeature.getInstance().getBoolean(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_SUPPORTEAPAKA);
    private static final String DATA_LIMIT_INTENT = "com.android.intent.action.DATAUSAGE_REACH_TO_LIMIT";
    private static boolean DBG = false;
    private static final boolean DBG_PRODUCT_DEV = Debug.semIsProductDev();
    private static final int DEFAULT_POLL_RSSI_INTERVAL_MSECS = 3000;
    @VisibleForTesting
    public static final long DIAGS_CONNECT_TIMEOUT_MILLIS = 60000;
    public static final int DISABLED_MODE = 4;
    static final int DISCONNECTING_GUARD_TIMER_MSEC = 5000;
    private static final int DISCONNECT_REASON_ADD_OR_UPDATE_NETWORK = 21;
    public static final int DISCONNECT_REASON_API = 2;
    private static final int DISCONNECT_REASON_ASSOC_REJECTED = 12;
    private static final int DISCONNECT_REASON_AUTH_FAIL = 13;
    private static final int DISCONNECT_REASON_DHCP_FAIL = 1;
    private static final int DISCONNECT_REASON_DHCP_FAIL_WITH_IPCLIENT_ISSUE = 24;
    private static final int DISCONNECT_REASON_DISABLE_NETWORK = 19;
    private static final int DISCONNECT_REASON_DISCONNECT_BY_MDM = 15;
    private static final int DISCONNECT_REASON_DISCONNECT_BY_P2P = 16;
    public static final int DISCONNECT_REASON_FACTORY_RESET = 23;
    private static final int DISCONNECT_REASON_NO_INTERNET = 10;
    private static final int DISCONNECT_REASON_NO_NETWORK = 22;
    private static final int DISCONNECT_REASON_REMOVE_NETWORK = 18;
    private static final int DISCONNECT_REASON_ROAM_FAIL = 6;
    private static final int DISCONNECT_REASON_ROAM_TIMEOUT = 5;
    private static final int DISCONNECT_REASON_SIM_REMOVED = 4;
    private static final int DISCONNECT_REASON_START_CONNECT = 3;
    private static final int DISCONNECT_REASON_TURN_OFF_WIFI = 17;
    private static final int DISCONNECT_REASON_UNKNOWN = 0;
    private static final int DISCONNECT_REASON_UNWANTED = 8;
    private static final int DISCONNECT_REASON_UNWANTED_BY_USER = 9;
    private static final int DISCONNECT_REASON_USER_SWITCH = 20;
    public static final int EAP_EVENT_ANONYMOUS_IDENTITY_UPDATED = 1;
    public static final int EAP_EVENT_DEAUTH_8021X_AUTH_FAILED = 2;
    public static final int EAP_EVENT_EAP_FAILURE = 3;
    public static final int EAP_EVENT_ERROR_MESSAGE = 4;
    public static final int EAP_EVENT_LOGGING = 5;
    public static final int EAP_EVENT_NOTIFICATION = 7;
    public static final int EAP_EVENT_NO_CREDENTIALS = 6;
    public static final int EAP_EVENT_SUCCESS = 8;
    public static final int EAP_EVENT_TLS_ALERT = 9;
    public static final int EAP_EVENT_TLS_CERT_ERROR = 10;
    public static final int EAP_EVENT_TLS_HANDSHAKE_FAIL = 11;
    public static final int EAP_NOTIFICATION_KT_WIFI_AUTH_FAIL = 5;
    public static final int EAP_NOTIFICATION_KT_WIFI_INVALID_IDPW = 4;
    public static final int EAP_NOTIFICATION_KT_WIFI_INVALID_USIM = 1;
    public static final int EAP_NOTIFICATION_KT_WIFI_NO_RESPONSE = 6;
    public static final int EAP_NOTIFICATION_KT_WIFI_NO_USIM = 2;
    public static final int EAP_NOTIFICATION_KT_WIFI_SUCCESS = 0;
    public static final int EAP_NOTIFICATION_KT_WIFI_WEP_PSK_INVALID_KEY = 3;
    public static final int EAP_NOTIFICATION_NO_NOTIFICATION_INFORMATION = 987654321;
    private static final boolean ENABLE_SUPPORT_ADPS = SemFloatingFeature.getInstance().getBoolean("SEC_FLOATING_FEATURE_WIFI_SUPPORT_ADPS");
    private static final boolean ENABLE_SUPPORT_QOSCONTROL = SemFloatingFeature.getInstance().getBoolean("SEC_FLOATING_FEATURE_WLAN_SUPPORT_QOS_CONTROL");
    private static final boolean ENBLE_WLAN_CONFIG_ANALYTICS = (Integer.parseInt("1") == 1);
    private static final String EXTRA_OSU_ICON_QUERY_BSSID = "BSSID";
    private static final String EXTRA_OSU_ICON_QUERY_FILENAME = "FILENAME";
    private static final String EXTRA_OSU_PROVIDER = "OsuProvider";
    private static final String EXTRA_PACKAGE_NAME = "PackageName";
    private static final String EXTRA_PASSPOINT_CONFIGURATION = "PasspointConfiguration";
    private static final String EXTRA_UID = "uid";
    private static final int FAILURE = -1;
    private static final String GOOGLE_OUI = "DA-A1-19";
    private static final String INTERFACENAMEOFWLAN = "wlan0";
    private static double INVALID_LATITUDE_LONGITUDE = 1000.0d;
    private static final int IPCLIENT_TIMEOUT_MS = 10000;
    private static final int ISSUE_TRACKER_SYSDUMP_DISC = 2;
    private static final int ISSUE_TRACKER_SYSDUMP_HANG = 0;
    private static final int ISSUE_TRACKER_SYSDUMP_UNWANTED = 1;
    @VisibleForTesting
    public static final int LAST_SELECTED_NETWORK_EXPIRATION_AGE_MILLIS = 30000;
    private static final int LINK_FLAPPING_DEBOUNCE_MSEC = 4000;
    private static final String LOGD_LEVEL_DEBUG = "D";
    private static final String LOGD_LEVEL_VERBOSE = "V";
    private static final int MAX_PACKET_RECORDS = 500;
    private static final int MAX_SCAN_RESULTS_EVENT_COUNT_IN_IDLE = 2;
    private static final int MESSAGE_HANDLING_STATUS_DEFERRED = -4;
    private static final int MESSAGE_HANDLING_STATUS_DISCARD = -5;
    private static final int MESSAGE_HANDLING_STATUS_FAIL = -2;
    private static final int MESSAGE_HANDLING_STATUS_HANDLING_ERROR = -7;
    private static final int MESSAGE_HANDLING_STATUS_LOOPED = -6;
    private static final int MESSAGE_HANDLING_STATUS_OBSOLETE = -3;
    private static final int MESSAGE_HANDLING_STATUS_OK = 1;
    private static final int MESSAGE_HANDLING_STATUS_PROCESSED = 2;
    private static final int MESSAGE_HANDLING_STATUS_REFUSED = -1;
    private static final int MESSAGE_HANDLING_STATUS_UNKNOWN = 0;
    private static final int[] MHS_PRIVATE_NETWORK_MASK = {2861248, 660652};
    private static final int NCHO_VER1_STATE_BACKUP = 1;
    private static final int NCHO_VER1_STATE_ERROR = -1;
    private static final int NCHO_VER1_STATE_INIT = 0;
    private static final int NCHO_VER2_STATE_DISABLED = 0;
    private static final int NCHO_VER2_STATE_ENABLED = 1;
    private static final int NCHO_VER2_STATE_ERROR = -1;
    private static final int NCHO_VERSION_1_0 = 1;
    private static final int NCHO_VERSION_2_0 = 2;
    private static final int NCHO_VERSION_ERROR = 0;
    private static final int NCHO_VERSION_UNKNOWN = -1;
    private static final String NETWORKTYPE = "WIFI";
    private static final int NETWORK_STATUS_UNWANTED_DISABLE_AUTOJOIN = 2;
    private static final int NETWORK_STATUS_UNWANTED_DISCONNECT = 0;
    private static final int NETWORK_STATUS_UNWANTED_VALIDATION_FAILED = 1;
    @VisibleForTesting
    public static final short NUM_LOG_RECS_NORMAL = 1000;
    @VisibleForTesting
    public static final short NUM_LOG_RECS_VERBOSE = 3000;
    @VisibleForTesting
    public static final short NUM_LOG_RECS_VERBOSE_LOW_MEMORY = 200;
    private static final int ONE_HOUR_MILLI = 3600000;
    private static final int ROAMING_ARP_INTERVAL_MS = 500;
    private static final int ROAMING_ARP_START_DELAY_MS = 100;
    private static final int ROAMING_ARP_TIMEOUT_MS = 2000;
    private static final int ROAM_DHCP_DEFAULT = 0;
    private static final int ROAM_DHCP_RESTART = 1;
    private static final int ROAM_DHCP_SKIP = 2;
    static final int ROAM_GUARD_TIMER_MSEC = 15000;
    private static final int RSSI_POLL_ENABLE_DURING_LCD_OFF_FOR_IMS = 1;
    private static final int RSSI_POLL_ENABLE_DURING_LCD_OFF_FOR_SWITCHBOARD = 2;
    public static final int SCAN_ONLY_MODE = 2;
    public static final int SCAN_ONLY_WITH_WIFI_OFF_MODE = 3;
    static final int SECURITY_EAP = 3;
    static final int SECURITY_NONE = 0;
    static final int SECURITY_PSK = 2;
    static final int SECURITY_SAE = 4;
    static final int SECURITY_WEP = 1;
    private static final int SUCCESS = 1;
    public static final String SUPPLICANT_BSSID_ANY = "any";
    private static final int SUPPLICANT_RESTART_INTERVAL_MSECS = 5000;
    private static final int SUPPLICANT_RESTART_TRIES = 5;
    private static final int SUSPEND_DUE_TO_DHCP = 1;
    private static final int SUSPEND_DUE_TO_HIGH_PERF = 2;
    private static final int SUSPEND_DUE_TO_SCREEN = 4;
    private static final String SYSTEM_PROPERTY_LOG_CONTROL_WIFIHAL = "log.tag.WifiHAL";
    private static final String TAG = "WifiClientModeImpl";
    public static final WorkSource WIFI_WORK_SOURCE = new WorkSource(1010);
    private static final int WLAN_ADVANCED_DEBUG_DISC = 4;
    private static final int WLAN_ADVANCED_DEBUG_ELE_DEBUG = 32;
    private static final int WLAN_ADVANCED_DEBUG_PKT = 1;
    private static final int WLAN_ADVANCED_DEBUG_RESET = 0;
    private static final int WLAN_ADVANCED_DEBUG_STATE = 64;
    private static final int WLAN_ADVANCED_DEBUG_UDI = 8;
    private static final int WLAN_ADVANCED_DEBUG_UNWANTED = 2;
    private static final int WLAN_ADVANCED_DEBUG_UNWANTED_PANIC = 16;
    private static byte mCellularCapaState = 0;
    private static byte[] mCellularCellId = new byte[2];
    private static byte mCellularSignalLevel = 0;
    private static boolean mChanged = false;
    private static boolean mIsPolicyMobileData = false;
    private static boolean mIssueTrackerOn = false;
    private static int mLteuEnable = 0;
    private static int mLteuState = 0;
    private static byte mNetworktype = 0;
    private static final OpBrandingLoader.Vendor mOpBranding = OpBrandingLoader.getInstance().getOpBranding();
    private static int mPhoneStateEvent = 590096;
    private static int mRssiPollingScreenOffEnabled = 0;
    private static boolean mScellEnter = false;
    private static int mWlanAdvancedDebugState = 0;
    private static final SparseArray<String> sGetWhatToString = MessageUtils.findMessageNames(sMessageClasses);
    private static final Class[] sMessageClasses = {AsyncChannel.class, ClientModeImpl.class};
    private static final ConcurrentHashMap<String, LocalLog> sPktLogsMhs = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, LocalLog> sPktLogsWlan = new ConcurrentHashMap<>();
    private static int sScanAlarmIntentCount = 0;
    private final int LTEU_MOBILEHOTSPOT_5GHZ_ENABLED = 1;
    private final int LTEU_P2P_5GHZ_CONNECTED = 2;
    private final int LTEU_STA_5GHZ_CONNECTED = 4;
    public final List<String> MULTINETWORK_ALLOWING_SYSTEM_PACKAGE_LIST = Arrays.asList("com.samsung.android.oneconnect", "com.samsung.android.app.mirrorlink", "com.google.android.gms", "com.google.android.projection.gearhead");
    public final List<String> MULTINETWORK_EXCEPTION_PACKAGE_LIST = Arrays.asList(WifiConfigManager.SYSUI_PACKAGE_NAME, "android.uid.systemui", "com.samsung.android.app.aodservice", "com.sec.android.cover.ledcover", "com.samsung.android.app.routines", WifiConfigManager.SYSUI_PACKAGE_NAME, "com.samsung.desktopsystemui", "com.samsung.android.gesture.MotionRecognitionService", "com.android.systemui.sensor.PickupController", "com.samsung.uready.agent");
    public final List<String> NETWORKSETTINGS_PERMISSION_EXCEPTION_PACKAGE_LIST = Arrays.asList("com.samsung.android.oneconnect", "sdet.pack", "sdet.pack.channel");
    private CustomDeviceManagerProxy customDeviceManager = null;
    public boolean isDhcpStartSent = false;
    private int laaActiveState = -1;
    private int laaEnterState = -1;
    private String mApInterfaceName = "Not_use";
    private final BackupManagerProxy mBackupManagerProxy;
    private final IBatteryStats mBatteryStats;
    private WifiBigDataLogManager mBigDataManager;
    private boolean mBlockFccChannelCmd = false;
    private boolean mBluetoothConnectionActive = false;
    private final BuildProperties mBuildProperties;
    private int mCandidateRssiThreshold24G = -70;
    private int mCandidateRssiThreshold5G = -70;
    private int mCandidateRssiThreshold6G = -70;
    private ClientModeManager.Listener mClientModeCallback = null;
    private final Clock mClock;
    private ConnectivityManager mCm;
    private boolean mConcurrentEnabled;
    private State mConnectModeState = new ConnectModeState();
    private int mConnectedApInternalType = 0;
    private boolean mConnectedMacRandomzationSupported;
    private State mConnectedState = new ConnectedState();
    private LocalLog mConnectivityPacketLogForHotspot;
    private LocalLog mConnectivityPacketLogForWlan0;
    private Context mContext;
    private final WifiCountryCode mCountryCode;
    private int mCurrentP2pFreq;
    private int mDefaultRoamDelta = 10;
    private int mDefaultRoamScanPeriod = 10;
    private int mDefaultRoamTrigger = -75;
    private State mDefaultState = new DefaultState();
    private WifiDelayDisconnect mDelayDisconnect;
    private DhcpResults mDhcpResults;
    private final Object mDhcpResultsLock = new Object();
    private boolean mDidBlackListBSSID = false;
    private State mDisconnectedState = new DisconnectedState();
    private State mDisconnectingState = new DisconnectingState();
    int mDisconnectingWatchdogCount = 0;
    private boolean mEnableRssiPolling = false;
    HashMap<Integer, Long> mEventCounter = new HashMap<>();
    private FrameworkFacade mFacade;
    private boolean mFirstTurnOn = true;
    private Timer mFwLogTimer = null;
    private boolean mHandleIfaceIsUp = false;
    private AsyncChannel mIWCMonitorChannel = null;
    private String mInterfaceName;
    private volatile IpClientManager mIpClient;
    private IpClientCallbacksImpl mIpClientCallbacks;
    private boolean mIpReachabilityDisconnectEnabled = false;
    private boolean mIsAutoRoaming = false;
    private boolean mIsBootCompleted;
    private boolean mIsImsCallEstablished;
    public boolean mIsManualSelection = false;
    private boolean mIsNchoParamSet = false;
    private boolean mIsP2pConnected;
    private boolean mIsPasspointEnabled;
    public boolean mIsRoamNetwork = false;
    private boolean mIsRoaming = false;
    private boolean mIsRunning = false;
    private boolean mIsShutdown = false;
    private boolean mIsWifiOffByAirplane = false;
    private int mIsWifiOnly = -1;
    private SemWifiIssueDetector mIssueDetector;
    private State mL2ConnectedState = new L2ConnectedState();
    private String mLastBssid;
    private long mLastConnectAttemptTimestamp = 0;
    private int mLastConnectedNetworkId;
    private long mLastConnectedTime = -1;
    private long mLastDriverRoamAttempt = 0;
    private int mLastEAPFailureCount = 0;
    private int mLastEAPFailureNetworkId = -1;
    private Pair<String, String> mLastL2KeyAndGroupHint = null;
    private WifiLinkLayerStats mLastLinkLayerStats;
    private long mLastLinkLayerStatsUpdate = 0;
    private int mLastNetworkId;
    private long mLastOntimeReportTimeStamp = 0;
    private String mLastRequestPackageNameForGeofence = null;
    private final WorkSource mLastRunningWifiUids = new WorkSource();
    private long mLastScreenStateChangeTimeStamp = 0;
    private int mLastSignalLevel = -1;
    private final LinkProbeManager mLinkProbeManager;
    private LinkProperties mLinkProperties;
    PendingIntent mLocationPendingIntent;
    private int mLocationRequestNetworkId = -1;
    private final McastLockManagerFilterController mMcastLockManagerFilterController;
    private int mMessageHandlingStatus = 0;
    private byte mMobileState = 3;
    private boolean mModeChange = false;
    private int mNcho10State = 0;
    private int mNcho20State = 0;
    private int mNchoVersion = -1;
    @GuardedBy({"mNetworkAgentLock"})
    private WifiNetworkAgent mNetworkAgent;
    private final Object mNetworkAgentLock = new Object();
    private final NetworkCapabilities mNetworkCapabilitiesFilter = new NetworkCapabilities();
    private WifiNetworkFactory mNetworkFactory;
    private NetworkInfo mNetworkInfo;
    private final NetworkMisc mNetworkMisc = new NetworkMisc();
    private AtomicInteger mNullMessageCounter = new AtomicInteger(0);
    private State mObtainingIpState = new ObtainingIpState();
    private LinkProperties mOldLinkProperties;
    private int mOnTime = 0;
    private int mOnTimeLastReport = 0;
    private int mOnTimeScreenStateChange = 0;
    private int mOperationalMode = 4;
    private final AtomicBoolean mP2pConnected = new AtomicBoolean(false);
    private WifiController.P2pDisableListener mP2pDisableListener = null;
    private final boolean mP2pSupported;
    private SemConnectivityPacketTracker mPacketTrackerForHotspot;
    private SemConnectivityPacketTracker mPacketTrackerForWlan0;
    private final PasspointManager mPasspointManager;
    private int mPeriodicScanToken = 0;
    private int mPersistQosTid = 0;
    private int mPersistQosUid = 0;
    private PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        /* class com.android.server.wifi.ClientModeImpl.C037418 */

        public void onDataConnectionStateChanged(int state, int networkType) {
            Log.d(ClientModeImpl.TAG, "onDataConnectionStateChanged: state =" + String.valueOf(state) + ", networkType =" + TelephonyManager.getNetworkTypeName(networkType));
            ClientModeImpl.this.handleCellularCapabilities();
        }

        public void onUserMobileDataStateChanged(boolean enabled) {
            Log.d(ClientModeImpl.TAG, "onUserMobileDataStateChanged: enabled=" + enabled);
            ClientModeImpl.this.handleCellularCapabilities();
        }

        public void onSignalStrengthsChanged(SignalStrength signalStrength) {
            byte newLevel = (byte) signalStrength.getLevel();
            if (ClientModeImpl.mCellularSignalLevel != newLevel) {
                byte unused = ClientModeImpl.mCellularSignalLevel = newLevel;
                boolean unused2 = ClientModeImpl.mChanged = true;
                Log.d(ClientModeImpl.TAG, "onSignalStrengthsChanged: mCellularSignalLevel=" + ((int) ClientModeImpl.mCellularSignalLevel));
                ClientModeImpl.this.handleCellularCapabilities();
            }
        }

        public void onCellLocationChanged(CellLocation location) {
            Log.d(ClientModeImpl.TAG, "onCellLocationChanged: CellLocation=" + location);
            byte[] curCellularCellId = new byte[2];
            if (location instanceof GsmCellLocation) {
                curCellularCellId = ClientModeImpl.this.toBytes(((GsmCellLocation) location).getCid());
            } else if (location instanceof CdmaCellLocation) {
                curCellularCellId = ClientModeImpl.this.toBytes(((CdmaCellLocation) location).getBaseStationId());
            } else {
                Log.d(ClientModeImpl.TAG, "unknown location.");
                byte unused = ClientModeImpl.mCellularSignalLevel = (byte) 0;
            }
            if (!Arrays.equals(ClientModeImpl.mCellularCellId, curCellularCellId)) {
                System.arraycopy(curCellularCellId, 0, ClientModeImpl.mCellularCellId, 0, 2);
                boolean unused2 = ClientModeImpl.mChanged = true;
                ClientModeImpl.this.handleCellularCapabilities();
            }
        }

        public void onCarrierNetworkChange(boolean active) {
            Log.d(ClientModeImpl.TAG, "onCarrierNetworkChange: active=" + active);
            ClientModeImpl.this.handleCellularCapabilities();
        }
    };
    private volatile int mPollRssiIntervalMsecs = 3000;
    private final PropertyService mPropertyService;
    private boolean mQosGameIsRunning = false;
    private AsyncChannel mReplyChannel = new AsyncChannel();
    private boolean mReportedRunning = false;
    private int mRoamDhcpPolicy = 0;
    private int mRoamDhcpPolicyByB2bConfig = 0;
    private int mRoamFailCount = 0;
    int mRoamWatchdogCount = 0;
    private State mRoamingState = new RoamingState();
    private int mRssiPollToken = 0;
    private byte[] mRssiRanges;
    private int mRssiThreshold = -80;
    int mRunningBeaconCount = 0;
    private final WorkSource mRunningWifiUids = new WorkSource();
    private int mRxTime = 0;
    private int mRxTimeLastReport = 0;
    private final SarManager mSarManager;
    private int mScanMode = 1;
    private final ScanRequestProxy mScanRequestProxy;
    private int mScanResultsEventCounter;
    private boolean mScreenOn = false;
    private SemLocationListener mSemLocationListener = new SemLocationListener() {
        /* class com.android.server.wifi.ClientModeImpl.C037519 */

        public void onLocationAvailable(Location[] locations) {
        }

        public void onLocationChanged(Location location, Address address) {
            Log.d(ClientModeImpl.TAG, "onLocationChanged is called");
            if (ClientModeImpl.this.mLocationRequestNetworkId == -1) {
                if (ClientModeImpl.DBG) {
                    Log.d(ClientModeImpl.TAG, "There is no config to update location");
                }
            } else if (location == null) {
                Log.d(ClientModeImpl.TAG, "onLocationChanged is called but location is null");
            } else {
                WifiConfiguration config = ClientModeImpl.this.mWifiConfigManager.getConfiguredNetwork(ClientModeImpl.this.mLocationRequestNetworkId);
                if (config == null) {
                    Log.d(ClientModeImpl.TAG, "Try to updateLocation but config is null");
                    return;
                }
                double latitude = location.getLatitude();
                double longitude = location.getLongitude();
                if (ClientModeImpl.this.isValidLocation(latitude, longitude)) {
                    ClientModeImpl.this.mWifiGeofenceManager.setLatitudeAndLongitude(config, latitude, longitude);
                    ClientModeImpl.this.mLocationRequestNetworkId = -1;
                }
            }
        }
    };
    private SemLocationManager mSemLocationManager;
    private final SemSarManager mSemSarManager;
    private SemWifiHiddenNetworkTracker mSemWifiHiddenNetworkTracker;
    private WifiSettingsStore mSettingsStore;
    private long mSupplicantScanIntervalMs;
    private SupplicantStateTracker mSupplicantStateTracker;
    private int mSuspendOptNeedsDisabled = 0;
    private PowerManager.WakeLock mSuspendWakeLock;
    private int mTargetNetworkId = -1;
    private String mTargetRoamBSSID = "any";
    private WifiConfiguration mTargetWifiConfiguration = null;
    private final String mTcpBufferSizes;
    private TelephonyManager mTelephonyManager;
    private boolean mTemporarilyDisconnectWifi = false;
    private int mTxTime = 0;
    private int mTxTimeLastReport = 0;
    private UnstableApController mUnstableApController;
    private UntrustedWifiNetworkFactory mUntrustedNetworkFactory;
    private AtomicBoolean mUserWantsSuspendOpt = new AtomicBoolean(true);
    private boolean mVerboseLoggingEnabled = false;
    private PowerManager.WakeLock mWakeLock;
    private AtomicBoolean mWifiAdpsEnabled = new AtomicBoolean(false);
    private final WifiB2BConfigurationPolicy mWifiB2bConfigPolicy;
    private final WifiConfigManager mWifiConfigManager;
    private final WifiConnectivityManager mWifiConnectivityManager;
    private final WifiDataStall mWifiDataStall;
    private BaseWifiDiagnostics mWifiDiagnostics;
    private final WifiGeofenceManager mWifiGeofenceManager;
    private final ExtendedWifiInfo mWifiInfo;
    private final WifiInjector mWifiInjector;
    private final WifiMetrics mWifiMetrics;
    private final WifiMonitor mWifiMonitor;
    private final WifiNative mWifiNative;
    private ArrayList<ClientModeChannel.WifiNetworkCallback> mWifiNetworkCallbackList = null;
    private WifiNetworkSuggestionsManager mWifiNetworkSuggestionsManager;
    private AsyncChannel mWifiP2pChannel;
    private final WifiPermissionsUtil mWifiPermissionsUtil;
    private final WifiPermissionsWrapper mWifiPermissionsWrapper;
    private WifiPolicy mWifiPolicy;
    private final WifiScoreCard mWifiScoreCard;
    private final WifiScoreReport mWifiScoreReport;
    private final AtomicInteger mWifiState = new AtomicInteger(1);
    private WifiStateTracker mWifiStateTracker;
    private final WifiTrafficPoller mWifiTrafficPoller;
    private final WrongPasswordNotifier mWrongPasswordNotifier;
    private int mWtcMode = 1;

    public interface ClientModeChannel {

        public interface WifiNetworkCallback {
            void checkIsCaptivePortalException(String str);

            void handleResultRoamInLevel1State(boolean z);

            void notifyDhcpSession(String str);

            void notifyLinkPropertiesUpdated(LinkProperties linkProperties);

            void notifyProvisioningFail();

            void notifyReachabilityLost();

            void notifyRoamSession(String str);
        }

        Message fetchPacketCountNative();
    }

    static /* synthetic */ int access$16508(ClientModeImpl x0) {
        int i = x0.mRssiPollToken;
        x0.mRssiPollToken = i + 1;
        return i;
    }

    static /* synthetic */ int access$20708(ClientModeImpl x0) {
        int i = x0.mRoamFailCount;
        x0.mRoamFailCount = i + 1;
        return i;
    }

    static /* synthetic */ int access$22708(ClientModeImpl x0) {
        int i = x0.mScanResultsEventCounter;
        x0.mScanResultsEventCounter = i + 1;
        return i;
    }

    static /* synthetic */ int access$9972(int x0) {
        int i = mPhoneStateEvent & x0;
        mPhoneStateEvent = i;
        return i;
    }

    static /* synthetic */ int access$9976(int x0) {
        int i = mPhoneStateEvent | x0;
        mPhoneStateEvent = i;
        return i;
    }

    /* access modifiers changed from: protected */
    public void loge(String s) {
        Log.e(getName(), s);
    }

    /* access modifiers changed from: protected */
    public void logd(String s) {
        Log.d(getName(), s);
    }

    /* access modifiers changed from: protected */
    public void log(String s) {
        Log.d(getName(), s);
    }

    public WifiScoreReport getWifiScoreReport() {
        return this.mWifiScoreReport;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void processRssiThreshold(byte curRssi, int reason, WifiNative.WifiRssiEventHandler rssiHandler) {
        if (curRssi == Byte.MAX_VALUE || curRssi == Byte.MIN_VALUE) {
            Log.wtf(TAG, "processRssiThreshold: Invalid rssi " + ((int) curRssi));
            return;
        }
        int i = 0;
        while (true) {
            byte[] bArr = this.mRssiRanges;
            if (i >= bArr.length) {
                return;
            }
            if (curRssi < bArr[i]) {
                byte maxRssi = bArr[i];
                byte minRssi = bArr[i - 1];
                this.mWifiInfo.setRssi(curRssi);
                updateCapabilities();
                int ret = startRssiMonitoringOffload(maxRssi, minRssi, rssiHandler);
                Log.d(TAG, "Re-program RSSI thresholds for " + getWhatToString(reason) + ": [" + ((int) minRssi) + ", " + ((int) maxRssi) + "], curRssi=" + ((int) curRssi) + " ret=" + ret);
                return;
            }
            i++;
        }
    }

    /* access modifiers changed from: package-private */
    public int getPollRssiIntervalMsecs() {
        return this.mPollRssiIntervalMsecs;
    }

    /* access modifiers changed from: package-private */
    public void setPollRssiIntervalMsecs(int newPollIntervalMsecs) {
        this.mPollRssiIntervalMsecs = newPollIntervalMsecs;
    }

    public boolean clearTargetBssid(String dbg) {
        WifiConfiguration config = this.mWifiConfigManager.getConfiguredNetwork(this.mTargetNetworkId);
        if (config == null) {
            return false;
        }
        if (!this.mHandleIfaceIsUp) {
            Log.w(TAG, "clearTargetBssid, mHandleIfaceIsUp is false");
            return false;
        }
        String bssid = "any";
        if (config.BSSID != null) {
            bssid = config.BSSID;
            if (this.mVerboseLoggingEnabled) {
                Log.d(TAG, "force BSSID to " + bssid + "due to config");
            }
        }
        if (this.mVerboseLoggingEnabled) {
            logd(dbg + " clearTargetBssid " + bssid + " key=" + config.configKey());
        }
        this.mTargetRoamBSSID = bssid;
        return this.mWifiNative.setConfiguredNetworkBSSID(this.mInterfaceName, bssid);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean setTargetBssid(WifiConfiguration config, String bssid) {
        if (config == null || bssid == null) {
            return false;
        }
        if (config.BSSID != null) {
            bssid = config.BSSID;
            if (this.mVerboseLoggingEnabled) {
                Log.d(TAG, "force BSSID to " + bssid + "due to config");
            }
        }
        if (this.mVerboseLoggingEnabled) {
            Log.d(TAG, "setTargetBssid set to " + bssid + " key=" + config.configKey());
        }
        this.mTargetRoamBSSID = bssid;
        config.getNetworkSelectionStatus().setNetworkSelectionBSSID(bssid);
        return true;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private TelephonyManager getTelephonyManager() {
        if (this.mTelephonyManager == null) {
            this.mTelephonyManager = this.mWifiInjector.makeTelephonyManager();
        }
        return this.mTelephonyManager;
    }

    public ClientModeImpl(Context context, FrameworkFacade facade, Looper looper, UserManager userManager, WifiInjector wifiInjector, BackupManagerProxy backupManagerProxy, WifiCountryCode countryCode, WifiNative wifiNative, WrongPasswordNotifier wrongPasswordNotifier, SarManager sarManager, WifiTrafficPoller wifiTrafficPoller, LinkProbeManager linkProbeManager) {
        super(TAG, looper);
        this.mWifiInjector = wifiInjector;
        this.mWifiMetrics = this.mWifiInjector.getWifiMetrics();
        this.mClock = wifiInjector.getClock();
        this.mPropertyService = wifiInjector.getPropertyService();
        this.mBuildProperties = wifiInjector.getBuildProperties();
        this.mWifiScoreCard = wifiInjector.getWifiScoreCard();
        this.mContext = context;
        this.mFacade = facade;
        this.mWifiNative = wifiNative;
        this.mBackupManagerProxy = backupManagerProxy;
        this.mWrongPasswordNotifier = wrongPasswordNotifier;
        this.mSarManager = sarManager;
        this.mSemSarManager = new SemSarManager(this.mContext, this.mWifiNative);
        this.mWifiTrafficPoller = wifiTrafficPoller;
        this.mLinkProbeManager = linkProbeManager;
        this.mNetworkInfo = new NetworkInfo(1, 0, NETWORKTYPE, "");
        this.mBatteryStats = IBatteryStats.Stub.asInterface(this.mFacade.getService("batterystats"));
        this.mWifiStateTracker = wifiInjector.getWifiStateTracker();
        this.mFacade.getService("network_management");
        this.mP2pSupported = this.mContext.getPackageManager().hasSystemFeature("android.hardware.wifi.direct");
        this.mWifiPermissionsUtil = this.mWifiInjector.getWifiPermissionsUtil();
        this.mWifiConfigManager = this.mWifiInjector.getWifiConfigManager();
        this.mPasspointManager = this.mWifiInjector.getPasspointManager();
        this.mWifiMonitor = this.mWifiInjector.getWifiMonitor();
        this.mWifiDiagnostics = this.mWifiInjector.getWifiDiagnostics();
        this.mScanRequestProxy = this.mWifiInjector.getScanRequestProxy();
        this.mWifiPermissionsWrapper = this.mWifiInjector.getWifiPermissionsWrapper();
        this.mWifiDataStall = this.mWifiInjector.getWifiDataStall();
        this.mWifiInfo = new ExtendedWifiInfo();
        this.mSupplicantStateTracker = this.mFacade.makeSupplicantStateTracker(context, this.mWifiConfigManager, getHandler());
        this.mWifiConnectivityManager = this.mWifiInjector.makeWifiConnectivityManager(this);
        this.mWifiGeofenceManager = this.mWifiInjector.getWifiGeofenceManager();
        if (this.mWifiGeofenceManager.isSupported()) {
            this.mWifiGeofenceManager.register(this.mWifiConnectivityManager);
            this.mWifiGeofenceManager.register(this.mNetworkInfo);
        }
        this.mLinkProperties = new LinkProperties();
        this.mOldLinkProperties = new LinkProperties();
        this.mMcastLockManagerFilterController = new McastLockManagerFilterController();
        this.mNetworkInfo.setIsAvailable(false);
        this.mLastBssid = null;
        this.mLastNetworkId = -1;
        this.mLastConnectedNetworkId = -1;
        this.mLastSignalLevel = -1;
        this.mCountryCode = countryCode;
        this.mWifiScoreReport = new WifiScoreReport(this.mWifiInjector.getScoringParams(), this.mClock);
        this.mDelayDisconnect = new WifiDelayDisconnect(this.mContext, this.mWifiInjector);
        this.mNetworkCapabilitiesFilter.addTransportType(1);
        this.mNetworkCapabilitiesFilter.addCapability(12);
        this.mNetworkCapabilitiesFilter.addCapability(11);
        this.mNetworkCapabilitiesFilter.addCapability(18);
        this.mNetworkCapabilitiesFilter.addCapability(20);
        this.mNetworkCapabilitiesFilter.addCapability(13);
        this.mNetworkCapabilitiesFilter.setLinkUpstreamBandwidthKbps(1048576);
        this.mNetworkCapabilitiesFilter.setLinkDownstreamBandwidthKbps(1048576);
        this.mNetworkCapabilitiesFilter.setNetworkSpecifier(new MatchAllNetworkSpecifier());
        this.mNetworkFactory = this.mWifiInjector.makeWifiNetworkFactory(this.mNetworkCapabilitiesFilter, this.mWifiConnectivityManager);
        this.mUntrustedNetworkFactory = this.mWifiInjector.makeUntrustedWifiNetworkFactory(this.mNetworkCapabilitiesFilter, this.mWifiConnectivityManager);
        this.mWifiNetworkSuggestionsManager = this.mWifiInjector.getWifiNetworkSuggestionsManager();
        this.mWifiAdpsEnabled.set(Settings.Secure.getInt(this.mContext.getContentResolver(), "wifi_adps_enable", 0) == 1);
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.intent.action.SCREEN_ON");
        filter.addAction("android.intent.action.SCREEN_OFF");
        filter.addAction(SemWifiApPowerSaveImpl.ACTION_SCREEN_ON_BY_PROXIMITY);
        filter.addAction(SemWifiApPowerSaveImpl.ACTION_SCREEN_OFF_BY_PROXIMITY);
        this.mContext.registerReceiver(new BroadcastReceiver() {
            /* class com.android.server.wifi.ClientModeImpl.C03651 */

            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action.equals("android.intent.action.SCREEN_ON")) {
                    ClientModeImpl.this.sendMessage(ClientModeImpl.CMD_SCREEN_STATE_CHANGED, 1);
                } else if (action.equals("android.intent.action.SCREEN_OFF")) {
                    ClientModeImpl.this.sendMessage(ClientModeImpl.CMD_SCREEN_STATE_CHANGED, 0);
                } else if (action.equals(SemWifiApPowerSaveImpl.ACTION_SCREEN_ON_BY_PROXIMITY)) {
                    ClientModeImpl.this.sendMessage(ClientModeImpl.CMD_SCREEN_STATE_CHANGED, 1);
                } else if (action.equals(SemWifiApPowerSaveImpl.ACTION_SCREEN_OFF_BY_PROXIMITY)) {
                    ClientModeImpl.this.sendMessage(ClientModeImpl.CMD_SCREEN_STATE_CHANGED, 0);
                }
            }
        }, filter);
        IntentFilter qosControlFilter = new IntentFilter();
        if (ENABLE_SUPPORT_QOSCONTROL) {
            qosControlFilter.addAction("com.samsung.android.game.intent.action.WIFI_QOS_CONTROL_START");
            qosControlFilter.addAction("com.samsung.android.game.intent.action.WIFI_QOS_CONTROL_END");
        }
        this.mContext.registerReceiver(new BroadcastReceiver() {
            /* class com.android.server.wifi.ClientModeImpl.C03762 */

            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                int i = 0;
                ClientModeImpl.this.mPersistQosTid = intent.getIntExtra("tid", 0);
                ClientModeImpl.this.mPersistQosUid = intent.getIntExtra(ClientModeImpl.EXTRA_UID, 0);
                if (action.equals("com.samsung.android.game.intent.action.WIFI_QOS_CONTROL_START")) {
                    ClientModeImpl.this.mQosGameIsRunning = true;
                } else if (action.equals("com.samsung.android.game.intent.action.WIFI_QOS_CONTROL_END")) {
                    ClientModeImpl.this.mQosGameIsRunning = false;
                }
                if (ClientModeImpl.this.isConnected()) {
                    Log.d(ClientModeImpl.TAG, "isConnected");
                    WifiNative wifiNative = ClientModeImpl.this.mWifiNative;
                    String str = ClientModeImpl.this.mInterfaceName;
                    if (ClientModeImpl.this.mQosGameIsRunning) {
                        i = 2;
                    }
                    wifiNative.setTidMode(str, i, ClientModeImpl.this.mPersistQosUid, ClientModeImpl.this.mPersistQosTid);
                }
            }
        }, qosControlFilter, "android.permission.HARDWARE_TEST", null);
        this.mContext.getContentResolver().registerContentObserver(Settings.Secure.getUriFor("wifi_hotspot20_enable"), false, new ContentObserver(getHandler()) {
            /* class com.android.server.wifi.ClientModeImpl.C03773 */

            public void onChange(boolean selfChange) {
                int passpointEnabled = Settings.Secure.getInt(ClientModeImpl.this.mContext.getContentResolver(), "wifi_hotspot20_enable", 1);
                if (passpointEnabled == 1 || passpointEnabled == 3) {
                    ClientModeImpl.this.mIsPasspointEnabled = true;
                } else {
                    ClientModeImpl.this.mIsPasspointEnabled = false;
                }
                ClientModeImpl.this.mPasspointManager.setPasspointEnabled(ClientModeImpl.this.mIsPasspointEnabled);
                ClientModeImpl.this.mWifiNative.setInterwokingEnabled(ClientModeImpl.this.mInterfaceName, ClientModeImpl.this.mIsPasspointEnabled);
                if (ClientModeImpl.this.mWifiState.get() == 3) {
                    ClientModeImpl clientModeImpl = ClientModeImpl.this;
                    clientModeImpl.updatePasspointNetworkSelectionStatus(clientModeImpl.mIsPasspointEnabled);
                    return;
                }
                Log.e(ClientModeImpl.TAG, "WIFI_HOTSPOT20_ENABLE change to : " + ClientModeImpl.this.mIsPasspointEnabled + ", but mWifiState is invalid : " + ClientModeImpl.this.mWifiState.get());
            }
        });
        this.mContext.registerReceiver(new BroadcastReceiver() {
            /* class com.android.server.wifi.ClientModeImpl.C03784 */

            public void onReceive(Context context, Intent intent) {
                Bundle extras = intent.getExtras();
                if (extras != null && extras.getSerializable("ONOFF") != null) {
                    boolean unused = ClientModeImpl.mIssueTrackerOn = ((Boolean) extras.getSerializable("ONOFF")).booleanValue();
                    if (ClientModeImpl.mIssueTrackerOn) {
                        ClientModeImpl.this.updateWlanDebugLevel();
                    }
                }
            }
        }, new IntentFilter("com.sec.android.ISSUE_TRACKER_ONOFF"));
        this.mFacade.registerContentObserver(this.mContext, Settings.Global.getUriFor("wifi_suspend_optimizations_enabled"), false, new ContentObserver(getHandler()) {
            /* class com.android.server.wifi.ClientModeImpl.C03795 */

            public void onChange(boolean selfChange) {
                AtomicBoolean atomicBoolean = ClientModeImpl.this.mUserWantsSuspendOpt;
                boolean z = true;
                if (ClientModeImpl.this.mFacade.getIntegerSetting(ClientModeImpl.this.mContext, "wifi_suspend_optimizations_enabled", 1) != 1) {
                    z = false;
                }
                atomicBoolean.set(z);
            }
        });
        this.mFacade.registerContentObserver(this.mContext, Settings.Secure.getUriFor("wifi_adps_enable"), false, new ContentObserver(getHandler()) {
            /* class com.android.server.wifi.ClientModeImpl.C03806 */

            public void onChange(boolean selfChange) {
                AtomicBoolean atomicBoolean = ClientModeImpl.this.mWifiAdpsEnabled;
                boolean z = false;
                if (Settings.Secure.getInt(ClientModeImpl.this.mContext.getContentResolver(), "wifi_adps_enable", 0) == 1) {
                    z = true;
                }
                atomicBoolean.set(z);
                ClientModeImpl.this.sendMessage(ClientModeImpl.CMD_SET_ADPS_MODE);
            }
        });
        this.mContext.getContentResolver().registerContentObserver(Settings.Global.getUriFor("safe_wifi"), false, new ContentObserver(getHandler()) {
            /* class com.android.server.wifi.ClientModeImpl.C03817 */

            public void onChange(boolean selfChange) {
                boolean heEnabled = false;
                boolean safeModeEnabled = Settings.Global.getInt(ClientModeImpl.this.mContext.getContentResolver(), "safe_wifi", 0) == 1;
                if (Settings.Global.getInt(ClientModeImpl.this.mContext.getContentResolver(), "safe_wifi", 0) == 0) {
                    heEnabled = true;
                }
                if (!ClientModeImpl.this.mWifiNative.setSafeMode(ClientModeImpl.this.mInterfaceName, safeModeEnabled)) {
                    Log.e(ClientModeImpl.TAG, "Failed to set safe Wi-Fi mode");
                }
                if (!ClientModeImpl.this.mWifiNative.setHeEnabled(heEnabled)) {
                    Log.e(ClientModeImpl.TAG, "Failed to set safe Wi-Fi mode (HE Control)");
                }
            }
        });
        this.mUserWantsSuspendOpt.set(this.mFacade.getIntegerSetting(this.mContext, "wifi_suspend_optimizations_enabled", 1) == 1);
        PowerManager powerManager = (PowerManager) this.mContext.getSystemService("power");
        this.mWakeLock = powerManager.newWakeLock(1, getName());
        this.mSuspendWakeLock = powerManager.newWakeLock(1, "WifiSuspend");
        this.mSuspendWakeLock.setReferenceCounted(false);
        IntentFilter intentFilter = new IntentFilter();
        if (CSC_SUPPORT_5G_ANT_SHARE) {
            intentFilter.addAction("android.intent.action.coexstatus");
            this.laaEnterState = 0;
            this.laaActiveState = 0;
        }
        this.mWifiPolicy = EnterpriseDeviceManager.getInstance().getWifiPolicy();
        intentFilter.addAction("com.samsung.android.knox.intent.action.ENABLE_NETWORK_INTERNAL");
        this.mContext.registerReceiver(new BroadcastReceiver() {
            /* class com.android.server.wifi.ClientModeImpl.C03828 */

            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals("com.samsung.android.knox.intent.action.ENABLE_NETWORK_INTERNAL")) {
                    ArrayList<Integer> netIdsList = intent.getIntegerArrayListExtra("com.samsung.android.knox.intent.extra.NETID_INTERNAL");
                    boolean enableOthers = intent.getBooleanExtra("com.samsung.android.knox.intent.extra.ENABLE_OTHERS_INTERNAL", false);
                    if (netIdsList != null) {
                        Iterator<Integer> it = netIdsList.iterator();
                        while (it.hasNext()) {
                            int netId = it.next().intValue();
                            if (netId != -1) {
                                ClientModeImpl.this.mWifiConfigManager.enableNetwork(netId, enableOthers, 1000);
                            }
                        }
                        return;
                    }
                    Log.w(ClientModeImpl.TAG, "BroadcastReceiver - WifiPolicy.ACTION_ENABLE_NETWORK_INTERNAL : netIdList is null");
                } else if (intent.getAction().equals("android.intent.action.coexstatus")) {
                    if (intent.getIntExtra("STATUS", 0) == 1) {
                        boolean unused = ClientModeImpl.mScellEnter = true;
                        ClientModeImpl.this.laaEnterState = 1;
                    } else {
                        boolean unused2 = ClientModeImpl.mScellEnter = false;
                    }
                    Log.e(ClientModeImpl.TAG, "get android.intent.action.coexstatus mScellEnter : " + ClientModeImpl.mScellEnter);
                    if (ClientModeImpl.mScellEnter) {
                        ClientModeImpl clientModeImpl = ClientModeImpl.this;
                        clientModeImpl.sendIpcMessageToRilForLteu(4, clientModeImpl.isConnected(), ClientModeImpl.this.mWifiInfo.is5GHz(), true);
                    }
                }
            }
        }, intentFilter);
        if (CSC_SUPPORT_5G_ANT_SHARE) {
            this.mContext.registerReceiver(new BroadcastReceiver() {
                /* class com.android.server.wifi.ClientModeImpl.C03839 */

                public void onReceive(Context context, Intent intent) {
                    NetworkInfo ni = (NetworkInfo) intent.getParcelableExtra("networkInfo");
                    WifiP2pGroup wpg = (WifiP2pGroup) intent.getParcelableExtra("p2pGroupInfo");
                    ClientModeImpl.this.mIsP2pConnected = ni != null && ni.getDetailedState() == NetworkInfo.DetailedState.CONNECTED;
                    if (wpg == null || !ClientModeImpl.this.mIsP2pConnected) {
                        ClientModeImpl.this.mCurrentP2pFreq = 0;
                        ClientModeImpl.this.sendIpcMessageToRilForLteu(2, false, false, false);
                        return;
                    }
                    ClientModeImpl.this.mCurrentP2pFreq = wpg.getFrequency();
                    ClientModeImpl clientModeImpl = ClientModeImpl.this;
                    clientModeImpl.sendIpcMessageToRilForLteu(2, true, clientModeImpl.mCurrentP2pFreq / 1000 == 5, false);
                }
            }, new IntentFilter("android.net.wifi.p2p.CONNECTION_STATE_CHANGE"));
        }
        this.mContext.registerReceiver(new BroadcastReceiver() {
            /* class com.android.server.wifi.ClientModeImpl.C036610 */

            public void onReceive(Context context, Intent intent) {
                WifiConfiguration tempWifiConfig;
                int state = intent.getIntExtra("wifi_state", 14);
                if (state == 13) {
                    if (ClientModeImpl.CSC_SUPPORT_5G_ANT_SHARE && (tempWifiConfig = WifiInjector.getInstance().getWifiApConfigStore().getApConfiguration()) != null) {
                        ClientModeImpl.this.sendIpcMessageToRilForLteu(1, true, tempWifiConfig.apChannel > 14, false);
                    }
                    if (WifiInjector.getInstance().getSemWifiApChipInfo().supportWifiSharing()) {
                        ClientModeImpl.this.mApInterfaceName = "swlan0";
                    } else {
                        ClientModeImpl.this.mApInterfaceName = ClientModeImpl.INTERFACENAMEOFWLAN;
                    }
                    if (ClientModeImpl.this.mFirstTurnOn) {
                        Log.d(ClientModeImpl.TAG, "mFirstTurnOn true initialize mConnectivityPacketLogForHotspot");
                        ClientModeImpl.sPktLogsMhs.putIfAbsent(ClientModeImpl.this.mApInterfaceName, new LocalLog(500));
                        ClientModeImpl.this.mConnectivityPacketLogForHotspot = (LocalLog) ClientModeImpl.sPktLogsMhs.get(ClientModeImpl.this.mApInterfaceName);
                    }
                    if (ClientModeImpl.this.mFirstTurnOn && WifiInjector.getInstance().getSemWifiApChipInfo().supportWifiSharing()) {
                        Log.d(ClientModeImpl.TAG, "mFirstTurnOn true initialize mConnectivityPacketLogForWlan0");
                        ClientModeImpl.sPktLogsWlan.putIfAbsent(ClientModeImpl.INTERFACENAMEOFWLAN, new LocalLog(500));
                        ClientModeImpl.this.mConnectivityPacketLogForWlan0 = (LocalLog) ClientModeImpl.sPktLogsWlan.get(ClientModeImpl.INTERFACENAMEOFWLAN);
                    }
                    ClientModeImpl clientModeImpl = ClientModeImpl.this;
                    clientModeImpl.mPacketTrackerForHotspot = clientModeImpl.createPacketTracker(InterfaceParams.getByName(clientModeImpl.mApInterfaceName), ClientModeImpl.this.mConnectivityPacketLogForHotspot);
                    if (ClientModeImpl.this.mPacketTrackerForHotspot != null) {
                        Log.d(ClientModeImpl.TAG, "mPacketTrackerForHotspot start");
                        try {
                            ClientModeImpl.this.mPacketTrackerForHotspot.start(ClientModeImpl.this.mApInterfaceName);
                        } catch (NullPointerException e) {
                            Log.e(ClientModeImpl.TAG, "Failed to start tracking interface : " + e);
                        }
                    }
                    if (WifiInjector.getInstance().getSemWifiApChipInfo().supportWifiSharing()) {
                        ClientModeImpl clientModeImpl2 = ClientModeImpl.this;
                        clientModeImpl2.mPacketTrackerForWlan0 = clientModeImpl2.createPacketTracker(InterfaceParams.getByName(ClientModeImpl.INTERFACENAMEOFWLAN), ClientModeImpl.this.mConnectivityPacketLogForWlan0);
                        if (ClientModeImpl.this.mPacketTrackerForWlan0 != null) {
                            Log.d(ClientModeImpl.TAG, "mPacketTrackerForwlan0 start");
                            try {
                                ClientModeImpl.this.mPacketTrackerForWlan0.start(ClientModeImpl.INTERFACENAMEOFWLAN);
                            } catch (NullPointerException e2) {
                                Log.e(ClientModeImpl.TAG, "Failed to start tracking interface : " + e2);
                            }
                        }
                    }
                    if (ClientModeImpl.this.mFirstTurnOn) {
                        ClientModeImpl.this.mFirstTurnOn = false;
                    }
                } else if (state == 11 || state == 14) {
                    if (ClientModeImpl.CSC_SUPPORT_5G_ANT_SHARE) {
                        ClientModeImpl.this.sendIpcMessageToRilForLteu(1, false, false, false);
                    }
                    if (ClientModeImpl.this.mPacketTrackerForHotspot != null) {
                        ClientModeImpl.this.mPacketTrackerForHotspot.stop();
                        ClientModeImpl.this.mPacketTrackerForHotspot = null;
                        if (WifiInjector.getInstance().getSemWifiApChipInfo().supportWifiSharing() && ClientModeImpl.this.mPacketTrackerForWlan0 != null) {
                            ClientModeImpl.this.mPacketTrackerForWlan0.stop();
                            ClientModeImpl.this.mPacketTrackerForWlan0 = null;
                        }
                    }
                }
            }
        }, new IntentFilter("android.net.wifi.WIFI_AP_STATE_CHANGED"));
        this.mContext.registerReceiver(new BroadcastReceiver() {
            /* class com.android.server.wifi.ClientModeImpl.C036711 */

            public void onReceive(Context context, Intent intent) {
                Log.d(ClientModeImpl.TAG, "receive ACTION_AP_LOCATION_PASSIVE_REQUEST");
                Location location = (Location) intent.getExtras().get("currentlocation");
                WifiConfiguration config = ClientModeImpl.this.getCurrentWifiConfiguration();
                if (config != null) {
                    double latitude = location.getLatitude();
                    double longitude = location.getLongitude();
                    if (ClientModeImpl.this.isValidLocation(latitude, longitude)) {
                        ClientModeImpl.this.mWifiGeofenceManager.setLatitudeAndLongitude(config, latitude, longitude);
                        if (ClientModeImpl.this.mLocationPendingIntent != null) {
                            ClientModeImpl.this.mSemLocationManager.removePassiveLocation(ClientModeImpl.this.mLocationPendingIntent);
                            return;
                        }
                        return;
                    }
                    return;
                }
                Log.d(ClientModeImpl.TAG, "There is no config to update location");
            }
        }, new IntentFilter(ACTION_AP_LOCATION_PASSIVE_REQUEST));
        this.mWifiPolicy = EnterpriseDeviceManager.getInstance().getWifiPolicy();
        intentFilter.addAction("com.samsung.android.knox.intent.action.ENABLE_NETWORK_INTERNAL");
        this.mFacade.registerContentObserver(this.mContext, Settings.Global.getUriFor("data_roaming"), false, new ContentObserver(getHandler()) {
            /* class com.android.server.wifi.ClientModeImpl.C036812 */

            public void onChange(boolean selfChange) {
                Log.d(ClientModeImpl.TAG, "Settings.Global.DATA_ROAMING: onChange=" + selfChange);
                ClientModeImpl.this.handleCellularCapabilities();
            }
        });
        IntentFilter cellularIntentFilter = new IntentFilter();
        cellularIntentFilter.addAction("android.net.conn.CONNECTIVITY_CHANGE");
        cellularIntentFilter.addAction(DATA_LIMIT_INTENT);
        this.mContext.registerReceiver(new BroadcastReceiver() {
            /* class com.android.server.wifi.ClientModeImpl.C036913 */

            /* JADX WARNING: Removed duplicated region for block: B:13:0x002e  */
            /* JADX WARNING: Removed duplicated region for block: B:18:0x0081  */
            public void onReceive(Context context, Intent intent) {
                char c;
                String action = intent.getAction();
                int hashCode = action.hashCode();
                if (hashCode != -1172645946) {
                    if (hashCode == -545589955 && action.equals(ClientModeImpl.DATA_LIMIT_INTENT)) {
                        c = 0;
                        if (c == 0) {
                            boolean unused = ClientModeImpl.mIsPolicyMobileData = intent.getBooleanExtra("policyData", false);
                            Log.d(ClientModeImpl.TAG, "DATA_LIMIT_INTENT " + ClientModeImpl.mIsPolicyMobileData);
                            ClientModeImpl.this.handleCellularCapabilities();
                            return;
                        } else if (c != 1) {
                            Log.d(ClientModeImpl.TAG, "mCellularReceiver: undefined case: action=" + action);
                            return;
                        } else {
                            NetworkInfo networkInfo = (NetworkInfo) intent.getParcelableExtra("networkInfo");
                            if (networkInfo != null) {
                                Log.d(ClientModeImpl.TAG, "mCellularReceiver: action=" + action + ", Network type=" + networkInfo.getType() + ", isConnected=" + networkInfo.isConnected());
                                ClientModeImpl.this.handleCellularCapabilities();
                                return;
                            }
                            return;
                        }
                    }
                } else if (action.equals("android.net.conn.CONNECTIVITY_CHANGE")) {
                    c = 1;
                    if (c == 0) {
                    }
                }
                c = 65535;
                if (c == 0) {
                }
            }
        }, cellularIntentFilter);
        this.mConnectedMacRandomzationSupported = this.mContext.getResources().getBoolean(17891595);
        this.mWifiInfo.setEnableConnectedMacRandomization(this.mConnectedMacRandomzationSupported);
        this.mWifiMetrics.setIsMacRandomizationOn(this.mConnectedMacRandomzationSupported);
        this.mTcpBufferSizes = "524288,1048576,4194304,524288,1048576,4194304";
        addState(this.mDefaultState);
        addState(this.mConnectModeState, this.mDefaultState);
        addState(this.mL2ConnectedState, this.mConnectModeState);
        addState(this.mObtainingIpState, this.mL2ConnectedState);
        addState(this.mConnectedState, this.mL2ConnectedState);
        addState(this.mRoamingState, this.mL2ConnectedState);
        addState(this.mDisconnectingState, this.mConnectModeState);
        addState(this.mDisconnectedState, this.mConnectModeState);
        setInitialState(this.mDefaultState);
        setLogRecSize(DBG ? 3000 : 1000);
        setLogOnlyTransitions(false);
        if (OpBrandingLoader.Vendor.ATT == mOpBranding) {
            this.mWifiConfigManager.setNetworkAutoConnect(Settings.Secure.getInt(this.mContext.getContentResolver(), "wifi_auto_connecct", 1) == 1);
            if (DBG) {
                logi("ATT set mNetworkAutoConnectEnabled = " + this.mWifiConfigManager.getNetworkAutoConnectEnabled());
            }
        }
        this.mBigDataManager = new WifiBigDataLogManager(this.mContext, looper, new WifiBigDataLogManager.WifiBigDataLogAdapter() {
            /* class com.android.server.wifi.ClientModeImpl.C037014 */

            @Override // com.samsung.android.server.wifi.bigdata.WifiBigDataLogManager.WifiBigDataLogAdapter
            public List<WifiConfiguration> getSavedNetworks() {
                return ClientModeImpl.this.mWifiConfigManager.getSavedNetworks(1010);
            }

            @Override // com.samsung.android.server.wifi.bigdata.WifiBigDataLogManager.WifiBigDataLogAdapter
            public String getChipsetOuis() {
                ScanDetail scanDetail;
                NetworkDetail networkDetail;
                StringBuilder sb = new StringBuilder("00:00:00");
                ScanDetailCache scanDetailCache = ClientModeImpl.this.mWifiConfigManager.getScanDetailCacheForNetwork(ClientModeImpl.this.mLastNetworkId);
                if (scanDetailCache != null && (scanDetail = scanDetailCache.getScanDetail(ClientModeImpl.this.mLastBssid)) != null && (networkDetail = scanDetail.getNetworkDetail()) != null) {
                    String prefix = "";
                    int oui_count = 0;
                    for (Integer num : networkDetail.getChipsetOuis()) {
                        int chipset_oui = num.intValue();
                        if (oui_count == 0) {
                            sb.setLength(0);
                        }
                        sb.append(prefix);
                        prefix = ",";
                        sb.append(NetworkDetail.toOUIString(chipset_oui));
                        oui_count++;
                        if (oui_count >= 5) {
                            break;
                        }
                    }
                }
                return sb.toString();
            }
        });
        this.mIssueDetector = this.mWifiInjector.getIssueDetector();
        this.mSettingsStore = this.mWifiInjector.getWifiSettingsStore();
        int passpointEnabled = 0;
        try {
            int passpointEnabled2 = Settings.Secure.getInt(this.mContext.getContentResolver(), "wifi_hotspot20_enable");
            if (passpointEnabled2 != 1) {
                if (passpointEnabled2 != 3) {
                    this.mIsPasspointEnabled = false;
                    this.mPasspointManager.setPasspointEnabled(this.mIsPasspointEnabled);
                    this.mPasspointManager.setVendorSimUseable(false);
                    this.mWifiB2bConfigPolicy = this.mWifiInjector.getWifiB2bConfigPolicy();
                }
            }
            this.mIsPasspointEnabled = true;
        } catch (Settings.SettingNotFoundException e) {
            Log.e(TAG, "WIFI_HOTSPOT20_ENABLE SettingNotFoundException");
            String passpointCscFeature = OpBrandingLoader.getInstance().getMenuStatusForPasspoint();
            if (TextUtils.isEmpty(passpointCscFeature) || passpointCscFeature.contains("DEFAULT_ON")) {
                passpointEnabled = 3;
            } else if (passpointCscFeature.contains("DEFAULT_OFF")) {
                passpointEnabled = 2;
            }
            if (passpointEnabled == 1 || passpointEnabled == 3) {
                this.mIsPasspointEnabled = true;
            } else {
                this.mIsPasspointEnabled = false;
            }
            Settings.Secure.putInt(this.mContext.getContentResolver(), "wifi_hotspot20_enable", passpointEnabled);
        }
        this.mPasspointManager.setPasspointEnabled(this.mIsPasspointEnabled);
        this.mPasspointManager.setVendorSimUseable(false);
        this.mWifiB2bConfigPolicy = this.mWifiInjector.getWifiB2bConfigPolicy();
    }

    public void start() {
        ClientModeImpl.super.start();
        handleScreenStateChanged(((PowerManager) this.mContext.getSystemService("power")).isInteractive());
    }

    private void registerForWifiMonitorEvents() {
        this.mWifiMonitor.registerHandler(this.mInterfaceName, CMD_TARGET_BSSID, getHandler());
        this.mWifiMonitor.registerHandler(this.mInterfaceName, CMD_ASSOCIATED_BSSID, getHandler());
        this.mWifiMonitor.registerHandler(this.mInterfaceName, WifiMonitor.ANQP_DONE_EVENT, getHandler());
        this.mWifiMonitor.registerHandler(this.mInterfaceName, 147499, getHandler());
        this.mWifiMonitor.registerHandler(this.mInterfaceName, WifiMonitor.AUTHENTICATION_FAILURE_EVENT, getHandler());
        this.mWifiMonitor.registerHandler(this.mInterfaceName, WifiMonitor.GAS_QUERY_DONE_EVENT, getHandler());
        this.mWifiMonitor.registerHandler(this.mInterfaceName, WifiMonitor.GAS_QUERY_START_EVENT, getHandler());
        this.mWifiMonitor.registerHandler(this.mInterfaceName, WifiMonitor.HS20_REMEDIATION_EVENT, getHandler());
        this.mWifiMonitor.registerHandler(this.mInterfaceName, WifiMonitor.NETWORK_CONNECTION_EVENT, getHandler());
        this.mWifiMonitor.registerHandler(this.mInterfaceName, WifiMonitor.NETWORK_DISCONNECTION_EVENT, getHandler());
        this.mWifiMonitor.registerHandler(this.mInterfaceName, WifiMonitor.RX_HS20_ANQP_ICON_EVENT, getHandler());
        this.mWifiMonitor.registerHandler(this.mInterfaceName, WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, getHandler());
        this.mWifiMonitor.registerHandler(this.mInterfaceName, WifiMonitor.SUP_REQUEST_IDENTITY, getHandler());
        this.mWifiMonitor.registerHandler(this.mInterfaceName, WifiMonitor.SUP_REQUEST_SIM_AUTH, getHandler());
        this.mWifiMonitor.registerHandler(this.mInterfaceName, 147527, getHandler());
        this.mWifiMonitor.registerHandler(this.mInterfaceName, WifiMonitor.BSSID_PRUNED_EVENT, getHandler());
        this.mWifiMonitor.registerHandler(this.mInterfaceName, WifiMonitor.SUP_BIGDATA_EVENT, getHandler());
        this.mWifiMonitor.registerHandler(this.mInterfaceName, 147499, this.mWifiMetrics.getHandler());
        this.mWifiMonitor.registerHandler(this.mInterfaceName, WifiMonitor.AUTHENTICATION_FAILURE_EVENT, this.mWifiMetrics.getHandler());
        this.mWifiMonitor.registerHandler(this.mInterfaceName, WifiMonitor.NETWORK_CONNECTION_EVENT, this.mWifiMetrics.getHandler());
        this.mWifiMonitor.registerHandler(this.mInterfaceName, WifiMonitor.NETWORK_DISCONNECTION_EVENT, this.mWifiMetrics.getHandler());
        this.mWifiMonitor.registerHandler(this.mInterfaceName, WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, this.mWifiMetrics.getHandler());
        this.mWifiMonitor.registerHandler(this.mInterfaceName, CMD_ASSOCIATED_BSSID, this.mWifiMetrics.getHandler());
        this.mWifiMonitor.registerHandler(this.mInterfaceName, CMD_TARGET_BSSID, this.mWifiMetrics.getHandler());
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void setMulticastFilter(boolean enabled) {
        if (this.mIpClient != null) {
            this.mIpClient.setMulticastFilter(enabled);
        }
    }

    class McastLockManagerFilterController implements WifiMulticastLockManager.FilterController {
        McastLockManagerFilterController() {
        }

        @Override // com.android.server.wifi.WifiMulticastLockManager.FilterController
        public void startFilteringMulticastPackets() {
            ClientModeImpl.this.setMulticastFilter(true);
        }

        @Override // com.android.server.wifi.WifiMulticastLockManager.FilterController
        public void stopFilteringMulticastPackets() {
            ClientModeImpl.this.setMulticastFilter(false);
        }
    }

    /* access modifiers changed from: package-private */
    public class IpClientCallbacksImpl extends IpClientCallbacks {
        private final ConditionVariable mWaitForCreationCv = new ConditionVariable(false);
        private final ConditionVariable mWaitForStopCv = new ConditionVariable(false);

        IpClientCallbacksImpl() {
        }

        public void onIpClientCreated(IIpClient ipClient) {
            ClientModeImpl clientModeImpl = ClientModeImpl.this;
            clientModeImpl.mIpClient = new IpClientManager(ipClient, clientModeImpl.getName());
            this.mWaitForCreationCv.open();
        }

        public void onPreDhcpAction() {
            ClientModeImpl.this.sendMessage(ClientModeImpl.CMD_PRE_DHCP_ACTION);
        }

        public void onPostDhcpAction() {
            ClientModeImpl.this.sendMessage(ClientModeImpl.CMD_POST_DHCP_ACTION);
        }

        public void onNewDhcpResults(DhcpResults dhcpResults) {
            if (dhcpResults != null) {
                ClientModeImpl.this.sendMessage(ClientModeImpl.CMD_IPV4_PROVISIONING_SUCCESS, dhcpResults);
            } else {
                ClientModeImpl.this.sendMessage(ClientModeImpl.CMD_IPV4_PROVISIONING_FAILURE);
            }
        }

        public void onProvisioningSuccess(LinkProperties newLp) {
            ClientModeImpl.this.mWifiMetrics.logStaEvent(7);
            ClientModeImpl.this.sendMessage(ClientModeImpl.CMD_UPDATE_LINKPROPERTIES, newLp);
            ClientModeImpl.this.sendMessage(ClientModeImpl.CMD_IP_CONFIGURATION_SUCCESSFUL);
        }

        public void onProvisioningFailure(LinkProperties newLp) {
            ClientModeImpl.this.mWifiMetrics.logStaEvent(8);
            ClientModeImpl.this.sendMessage(ClientModeImpl.CMD_IP_CONFIGURATION_LOST);
        }

        public void onLinkPropertiesChange(LinkProperties newLp) {
            ClientModeImpl.this.sendMessage(ClientModeImpl.CMD_UPDATE_LINKPROPERTIES, newLp);
        }

        public void onReachabilityLost(String logMsg) {
            ClientModeImpl.this.mWifiMetrics.logStaEvent(9);
            ClientModeImpl.this.sendMessage(ClientModeImpl.CMD_IP_REACHABILITY_LOST, logMsg);
        }

        public void installPacketFilter(byte[] filter) {
            ClientModeImpl.this.sendMessage(ClientModeImpl.CMD_INSTALL_PACKET_FILTER, filter);
        }

        public void startReadPacketFilter() {
            ClientModeImpl.this.sendMessage(ClientModeImpl.CMD_READ_PACKET_FILTER);
        }

        public void setFallbackMulticastFilter(boolean enabled) {
            ClientModeImpl.this.sendMessage(ClientModeImpl.CMD_SET_FALLBACK_PACKET_FILTERING, Boolean.valueOf(enabled));
        }

        public void setNeighborDiscoveryOffload(boolean enabled) {
            ClientModeImpl.this.sendMessage(ClientModeImpl.CMD_CONFIG_ND_OFFLOAD, enabled ? 1 : 0);
        }

        public void onQuit() {
            this.mWaitForStopCv.open();
        }

        /* access modifiers changed from: package-private */
        public boolean awaitCreation() {
            return this.mWaitForCreationCv.block(10000);
        }

        /* access modifiers changed from: package-private */
        public boolean awaitShutdown() {
            return this.mWaitForStopCv.block(10000);
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void stopIpClient() {
        handlePostDhcpSetup();
        if (this.mIpClient != null) {
            this.mIpClient.stop();
        }
    }

    /* access modifiers changed from: package-private */
    public void setSupplicantLogLevel() {
        this.mWifiNative.setSupplicantLogLevel(this.mVerboseLoggingEnabled);
    }

    public void enableVerboseLogging(int verbose) {
        boolean z = true;
        if (verbose > 0) {
            this.mVerboseLoggingEnabled = true;
            DBG = true;
            setLogRecSize(ActivityManager.isLowRamDeviceStatic() ? 200 : 3000);
        } else {
            this.mVerboseLoggingEnabled = false;
            DBG = false;
            setLogRecSize(1000);
        }
        configureVerboseHalLogging(this.mVerboseLoggingEnabled);
        setSupplicantLogLevel();
        this.mCountryCode.enableVerboseLogging(verbose);
        this.mWifiScoreReport.enableVerboseLogging(this.mVerboseLoggingEnabled);
        this.mWifiDiagnostics.startLogging(this.mVerboseLoggingEnabled);
        this.mWifiMonitor.enableVerboseLogging(verbose);
        this.mWifiNative.enableVerboseLogging(verbose);
        this.mWifiConfigManager.enableVerboseLogging(verbose);
        this.mSupplicantStateTracker.enableVerboseLogging(verbose);
        this.mPasspointManager.enableVerboseLogging(verbose);
        this.mWifiGeofenceManager.enableVerboseLogging(verbose);
        this.mNetworkFactory.enableVerboseLogging(verbose);
        this.mLinkProbeManager.enableVerboseLogging(this.mVerboseLoggingEnabled);
        this.mWifiB2bConfigPolicy.enableVerboseLogging(verbose);
        WifiBigDataLogManager wifiBigDataLogManager = this.mBigDataManager;
        if (!DBG_PRODUCT_DEV && !this.mVerboseLoggingEnabled) {
            z = false;
        }
        wifiBigDataLogManager.setLogVisible(z);
    }

    private void configureVerboseHalLogging(boolean enableVerbose) {
        if (!this.mBuildProperties.isUserBuild()) {
            this.mPropertyService.set(SYSTEM_PROPERTY_LOG_CONTROL_WIFIHAL, enableVerbose ? LOGD_LEVEL_VERBOSE : LOGD_LEVEL_DEBUG);
        }
    }

    private boolean setRandomMacOui() {
        String oui = this.mContext.getResources().getString(17040005);
        if (TextUtils.isEmpty(oui)) {
            oui = GOOGLE_OUI;
        }
        String[] ouiParts = oui.split("-");
        byte[] ouiBytes = {(byte) (Integer.parseInt(ouiParts[0], 16) & 255), (byte) (Integer.parseInt(ouiParts[1], 16) & 255), (byte) (Integer.parseInt(ouiParts[2], 16) & 255)};
        logd("Setting OUI to " + oui);
        return this.mWifiNative.setScanningMacOui(this.mInterfaceName, ouiBytes);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean connectToUserSelectNetwork(int netId, int uid, boolean forceReconnect) {
        logd("connectToUserSelectNetwork netId " + netId + ", uid " + uid + ", forceReconnect = " + forceReconnect);
        WifiConfiguration config = this.mWifiConfigManager.getConfiguredNetwork(netId);
        if (config == null) {
            loge("connectToUserSelectNetwork Invalid network Id=" + netId);
            return false;
        }
        boolean result = WifiPolicyCache.getInstance(this.mContext).isNetworkAllowed(config, false);
        logd("connectToUserSelectNetwork isNetworkAllowed=" + result);
        Context context = this.mContext;
        WifiMobileDeviceManager.auditLog(context, 3, result, TAG, "Performing an attempt to connect with AP. SSID: " + config.SSID);
        if (result) {
            Context context2 = this.mContext;
            WifiMobileDeviceManager.auditLog(context2, 3, true, TAG, "Connecting to Wi-Fi network whose ID is " + netId + " succeeded");
            if (!this.mWifiConfigManager.enableNetwork(netId, true, uid) || !this.mWifiConfigManager.updateLastConnectUid(netId, uid)) {
                logi("connectToUserSelectNetwork Allowing uid " + uid + " with insufficient permissions to connect=" + netId);
            } else if (this.mWifiPermissionsUtil.checkNetworkSettingsPermission(uid)) {
                this.mWifiConnectivityManager.setUserConnectChoice(netId);
            }
            if (forceReconnect || this.mWifiInfo.getNetworkId() != netId) {
                this.mWifiConnectivityManager.prepareForForcedConnection(netId);
                if (uid == 1000) {
                    this.mWifiMetrics.setNominatorForNetwork(config.networkId, 1);
                }
                this.mWifiConfigManager.setUserSelectNetwork(true);
                startConnectToNetwork(netId, uid, "any");
            } else {
                logi("connectToUserSelectNetwork already connecting/connected=" + netId);
            }
            if (!isWifiOnly()) {
                this.mWifiConfigManager.resetEntryRssi(netId);
            }
            return true;
        }
        Context context3 = this.mContext;
        WifiMobileDeviceManager.auditLog(context3, 3, false, TAG, "Connecting to Wi-Fi network whose ID is " + netId + " failed");
        return false;
    }

    public Messenger getMessenger() {
        return new Messenger(getHandler());
    }

    /* access modifiers changed from: package-private */
    public String reportOnTime() {
        long now = this.mClock.getWallClockMillis();
        StringBuilder sb = new StringBuilder();
        int i = this.mOnTime;
        int on = i - this.mOnTimeLastReport;
        this.mOnTimeLastReport = i;
        int i2 = this.mTxTime;
        int tx = i2 - this.mTxTimeLastReport;
        this.mTxTimeLastReport = i2;
        int i3 = this.mRxTime;
        int rx = i3 - this.mRxTimeLastReport;
        this.mRxTimeLastReport = i3;
        int period = (int) (now - this.mLastOntimeReportTimeStamp);
        this.mLastOntimeReportTimeStamp = now;
        sb.append(String.format("[on:%d tx:%d rx:%d period:%d]", Integer.valueOf(on), Integer.valueOf(tx), Integer.valueOf(rx), Integer.valueOf(period)));
        sb.append(String.format(" from screen [on:%d period:%d]", Integer.valueOf(this.mOnTime - this.mOnTimeScreenStateChange), Integer.valueOf((int) (now - this.mLastScreenStateChangeTimeStamp))));
        return sb.toString();
    }

    /* access modifiers changed from: package-private */
    public WifiLinkLayerStats getWifiLinkLayerStats() {
        if (this.mInterfaceName == null) {
            logw("getWifiLinkLayerStats called without an interface");
            return null;
        }
        this.mLastLinkLayerStatsUpdate = this.mClock.getWallClockMillis();
        WifiLinkLayerStats stats = this.mWifiNative.getWifiLinkLayerStats(this.mInterfaceName);
        if (stats != null) {
            this.mOnTime = stats.on_time;
            this.mTxTime = stats.tx_time;
            this.mRxTime = stats.rx_time;
            this.mRunningBeaconCount = stats.beacon_rx;
            this.mWifiInfo.updatePacketRates(stats, this.mLastLinkLayerStatsUpdate);
        } else {
            this.mWifiInfo.updatePacketRates(this.mFacade.getTxPackets(this.mInterfaceName), this.mFacade.getRxPackets(this.mInterfaceName), this.mLastLinkLayerStatsUpdate);
        }
        return stats;
    }

    private byte[] getDstMacForKeepalive(KeepalivePacketData packetData) throws SocketKeepalive.InvalidPacketException {
        try {
            return NativeUtil.macAddressToByteArray(macAddressFromRoute(RouteInfo.selectBestRoute(this.mLinkProperties.getRoutes(), packetData.dstAddress).getGateway().getHostAddress()));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new SocketKeepalive.InvalidPacketException(-21);
        }
    }

    private static int getEtherProtoForKeepalive(KeepalivePacketData packetData) throws SocketKeepalive.InvalidPacketException {
        if (packetData.dstAddress instanceof Inet4Address) {
            return OsConstants.ETH_P_IP;
        }
        if (packetData.dstAddress instanceof Inet6Address) {
            return OsConstants.ETH_P_IPV6;
        }
        throw new SocketKeepalive.InvalidPacketException(-21);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private int startWifiIPPacketOffload(int slot, KeepalivePacketData packetData, int intervalSeconds) {
        SocketKeepalive.InvalidPacketException e;
        try {
            byte[] packet = packetData.getPacket();
            try {
                try {
                    int ret = this.mWifiNative.startSendingOffloadedPacket(this.mInterfaceName, slot, getDstMacForKeepalive(packetData), packet, getEtherProtoForKeepalive(packetData), intervalSeconds * 1000);
                    if (ret == 0) {
                        return 0;
                    }
                    loge("startWifiIPPacketOffload(" + slot + ", " + intervalSeconds + "): hardware error " + ret);
                    return -31;
                } catch (SocketKeepalive.InvalidPacketException e2) {
                    e = e2;
                    return e.error;
                }
            } catch (SocketKeepalive.InvalidPacketException e3) {
                e = e3;
                return e.error;
            }
        } catch (SocketKeepalive.InvalidPacketException e4) {
            e = e4;
            return e.error;
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private int stopWifiIPPacketOffload(int slot) {
        int ret = this.mWifiNative.stopSendingOffloadedPacket(this.mInterfaceName, slot);
        if (ret == 0) {
            return 0;
        }
        loge("stopWifiIPPacketOffload(" + slot + "): hardware error " + ret);
        return -31;
    }

    private int startRssiMonitoringOffload(byte maxRssi, byte minRssi, WifiNative.WifiRssiEventHandler rssiHandler) {
        return this.mWifiNative.startRssiMonitoring(this.mInterfaceName, maxRssi, minRssi, rssiHandler);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private int stopRssiMonitoringOffload() {
        return this.mWifiNative.stopRssiMonitoring(this.mInterfaceName);
    }

    public boolean isSupportedGeofence() {
        return this.mWifiGeofenceManager.isSupported();
    }

    public void setWifiGeofenceListener(WifiGeofenceManager.WifiGeofenceStateListener listener) {
        if (this.mWifiGeofenceManager.isSupported()) {
            this.mWifiGeofenceManager.setWifiGeofenceListener(listener);
        }
    }

    public int getCurrentGeofenceState() {
        if (this.mWifiGeofenceManager.isSupported()) {
            return this.mWifiGeofenceManager.getCurrentGeofenceState();
        }
        return -1;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean isGeofenceUsedByAnotherPackage() {
        if (this.mLastRequestPackageNameForGeofence == null) {
            return false;
        }
        return true;
    }

    public void requestGeofenceState(boolean enabled, String packageName) {
        if (this.mWifiGeofenceManager.isSupported()) {
            if (enabled) {
                this.mLastRequestPackageNameForGeofence = packageName;
                this.mWifiGeofenceManager.initGeofence();
            } else {
                this.mLastRequestPackageNameForGeofence = null;
                this.mWifiGeofenceManager.deinitGeofence();
            }
            this.mWifiGeofenceManager.setGeofenceStateByAnotherPackage(enabled);
        }
    }

    public List<String> getGeofenceEnterKeys() {
        if (this.mWifiGeofenceManager.isSupported()) {
            return this.mWifiGeofenceManager.getGeofenceEnterKeys();
        }
        return new ArrayList();
    }

    public int getGeofenceCellCount(String configKey) {
        if (this.mWifiGeofenceManager.isSupported()) {
            return this.mWifiGeofenceManager.getGeofenceCellCount(configKey);
        }
        return 0;
    }

    public void setWifiStateForApiCalls(int newState) {
        if (newState != 0) {
            if (!(newState == 1 || newState == 2 || newState == 3 || newState == 4)) {
                Log.d(TAG, "attempted to set an invalid state: " + newState);
                return;
            }
        } else if (getCurrentState() == this.mConnectedState && !isWifiOffByAirplane() && ENBLE_WLAN_CONFIG_ANALYTICS) {
            setAnalyticsUserDisconnectReason(WifiNative.f20xe9115267);
        }
        if (this.mVerboseLoggingEnabled) {
            Log.d(TAG, "setting wifi state to: " + newState);
        }
        this.mWifiState.set(newState);
        if (newState == 2) {
            WifiMobileDeviceManager.auditLog(this.mContext, 5, true, ClientModeImpl.class.getSimpleName(), "Enabling Wifi");
        } else if (newState == 0) {
            WifiMobileDeviceManager.auditLog(this.mContext, 5, true, ClientModeImpl.class.getSimpleName(), "Disabling Wifi");
        }
        if (this.mWifiGeofenceManager.isSupported()) {
            this.mWifiGeofenceManager.notifyWifiState(newState);
        }
    }

    public int syncGetWifiState() {
        return this.mWifiState.get();
    }

    public String syncGetWifiStateByName() {
        int i = this.mWifiState.get();
        if (i == 0) {
            return "disabling";
        }
        if (i == 1) {
            return "disabled";
        }
        if (i == 2) {
            return "enabling";
        }
        if (i == 3) {
            return "enabled";
        }
        if (i != 4) {
            return "[invalid state]";
        }
        return "unknown state";
    }

    public boolean isConnected() {
        return getCurrentState() == this.mConnectedState;
    }

    public boolean isDisconnected() {
        return getCurrentState() == this.mDisconnectedState;
    }

    public boolean isSupplicantTransientState() {
        SupplicantState supplicantState = this.mWifiInfo.getSupplicantState();
        if (supplicantState == SupplicantState.ASSOCIATING || supplicantState == SupplicantState.AUTHENTICATING || supplicantState == SupplicantState.FOUR_WAY_HANDSHAKE || supplicantState == SupplicantState.GROUP_HANDSHAKE) {
            if (!this.mVerboseLoggingEnabled) {
                return true;
            }
            Log.d(TAG, "Supplicant is under transient state: " + supplicantState);
            return true;
        } else if (!this.mVerboseLoggingEnabled) {
            return false;
        } else {
            Log.d(TAG, "Supplicant is under steady state: " + supplicantState);
            return false;
        }
    }

    public WifiInfo syncRequestConnectionInfo() {
        return new WifiInfo(this.mWifiInfo);
    }

    public WifiInfo getWifiInfo() {
        return this.mWifiInfo;
    }

    public NetworkInfo syncGetNetworkInfo() {
        return new NetworkInfo(this.mNetworkInfo);
    }

    public DhcpResults syncGetDhcpResults() {
        DhcpResults dhcpResults;
        synchronized (this.mDhcpResultsLock) {
            dhcpResults = new DhcpResults(this.mDhcpResults);
        }
        return dhcpResults;
    }

    public void handleIfaceDestroyed() {
        this.mHandleIfaceIsUp = false;
        handleNetworkDisconnect();
    }

    public void setIsWifiOffByAirplane(boolean enabled) {
        this.mIsWifiOffByAirplane = enabled;
    }

    private boolean isWifiOffByAirplane() {
        return this.mIsWifiOffByAirplane;
    }

    public void setOperationalMode(int mode, String ifaceName) {
        if (this.mVerboseLoggingEnabled) {
            log("setting operational mode to " + String.valueOf(mode) + " for iface: " + ifaceName);
        }
        this.mModeChange = true;
        if (mode != 1) {
            if (getCurrentState() == this.mConnectedState) {
                notifyDisconnectInternalReason(17);
                if (ENBLE_WLAN_CONFIG_ANALYTICS && isWifiOffByAirplane()) {
                    setIsWifiOffByAirplane(false);
                    setAnalyticsUserDisconnectReason(WifiNative.ANALYTICS_DISCONNECT_REASON_USER_TRIGGER_DISCON_AIRPLANE);
                }
                this.mDelayDisconnect.checkAndWait(this.mNetworkInfo);
            }
            transitionTo(this.mDefaultState);
        } else if (ifaceName != null) {
            this.mInterfaceName = ifaceName;
            transitionTo(this.mDisconnectedState);
            this.mHandleIfaceIsUp = true;
        } else {
            Log.e(TAG, "supposed to enter connect mode, but iface is null -> DefaultState");
            transitionTo(this.mDefaultState);
        }
        sendMessageAtFrontOfQueue(CMD_SET_OPERATIONAL_MODE);
    }

    public void takeBugReport(String bugTitle, String bugDetail) {
        this.mWifiDiagnostics.takeBugReport(bugTitle, bugDetail);
    }

    /* access modifiers changed from: protected */
    @VisibleForTesting
    public int getOperationalModeForTest() {
        return this.mOperationalMode;
    }

    /* access modifiers changed from: package-private */
    public void setConcurrentEnabled(boolean enable) {
        this.mConcurrentEnabled = enable;
        this.mScanRequestProxy.setScanningEnabled(!this.mConcurrentEnabled, "SEC_COMMAND_ID_SET_WIFI_XXX_WITH_P2P");
        enableWifiConnectivityManager(!this.mConcurrentEnabled);
        this.mPasspointManager.setRequestANQPEnabled(!this.mConcurrentEnabled);
    }

    /* access modifiers changed from: package-private */
    public boolean getConcurrentEnabled() {
        return this.mConcurrentEnabled;
    }

    public void syncSetFccChannel(boolean enable) {
        if (DBG) {
            Log.d(TAG, "syncSetFccChannel: enable = " + enable);
        }
        if (!this.mIsRunning) {
            return;
        }
        if (this.mBlockFccChannelCmd) {
            Log.d(TAG, "Block setFccChannelNative CMD by WlanMacAddress");
        } else {
            this.mWifiNative.setFccChannel(this.mInterfaceName, enable);
        }
    }

    /* access modifiers changed from: package-private */
    public void setFccChannel() {
        if (DBG) {
            Log.d(TAG, "setFccChannel() is called");
        }
        boolean isAirplaneModeEnabled = Settings.Global.getInt(this.mContext.getContentResolver(), "airplane_mode_on", 0) == 1;
        if (isWifiOnly()) {
            syncSetFccChannel(true);
        } else if (isAirplaneModeEnabled) {
            syncSetFccChannel(true);
        } else {
            syncSetFccChannel(false);
        }
    }

    /* access modifiers changed from: protected */
    public WifiMulticastLockManager.FilterController getMcastLockManagerFilterController() {
        return this.mMcastLockManagerFilterController;
    }

    public boolean syncQueryPasspointIcon(AsyncChannel channel, long bssid, String fileName) {
        if (this.mIsShutdown) {
            Log.d(TAG, "ignore processing beause shutdown is held");
            return false;
        }
        Bundle bundle = new Bundle();
        bundle.putLong("BSSID", bssid);
        bundle.putString(EXTRA_OSU_ICON_QUERY_FILENAME, fileName);
        Message resultMsg = channel.sendMessageSynchronously((int) CMD_QUERY_OSU_ICON, bundle);
        int result = resultMsg.arg1;
        resultMsg.recycle();
        if (result == 1) {
            return true;
        }
        return false;
    }

    public int matchProviderWithCurrentNetwork(AsyncChannel channel, String fqdn) {
        if (this.mIsShutdown) {
            Log.d(TAG, "ignore processing beause shutdown is held");
            return -1;
        }
        Message resultMsg = channel.sendMessageSynchronously((int) CMD_MATCH_PROVIDER_NETWORK, fqdn);
        int result = resultMsg.arg1;
        resultMsg.recycle();
        return result;
    }

    public void deauthenticateNetwork(AsyncChannel channel, long holdoff, boolean ess) {
    }

    public void disableEphemeralNetwork(String ssid) {
        if (ssid != null) {
            sendMessage(CMD_DISABLE_EPHEMERAL_NETWORK, ssid);
        }
    }

    public void disconnectCommand() {
        sendMessage(CMD_DISCONNECT);
    }

    public void disconnectCommand(int uid, int reason) {
        sendMessage(CMD_DISCONNECT, uid, reason);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void notifyDisconnectInternalReason(int reason) {
        this.mBigDataManager.addOrUpdateValue(8, reason);
    }

    public void report(int reportId, Bundle report) {
        SemWifiIssueDetector semWifiIssueDetector = this.mIssueDetector;
        if (semWifiIssueDetector != null && report != null) {
            semWifiIssueDetector.captureBugReport(reportId, report);
        }
    }

    public void disableP2p(WifiController.P2pDisableListener mP2pDisableCallback) {
        if (this.mP2pSupported) {
            this.mP2pDisableListener = mP2pDisableCallback;
            if (this.mWifiP2pChannel != null) {
                Message message = new Message();
                message.what = WifiP2pServiceImpl.DISABLE_P2P;
                this.mWifiP2pChannel.sendMessage(message);
            }
        }
    }

    public void reconnectCommand(WorkSource workSource) {
        sendMessage(CMD_RECONNECT, workSource);
    }

    public void reassociateCommand() {
        sendMessage(CMD_REASSOCIATE);
    }

    private boolean messageIsNull(Message resultMsg) {
        if (resultMsg != null) {
            return false;
        }
        if (this.mNullMessageCounter.getAndIncrement() <= 0) {
            return true;
        }
        Log.wtf(TAG, "Persistent null Message", new RuntimeException());
        return true;
    }

    public int syncAddOrUpdateNetwork(AsyncChannel channel, WifiConfiguration config) {
        return syncAddOrUpdateNetwork(channel, 0, config);
    }

    public int syncAddOrUpdateNetwork(AsyncChannel channel, int from, WifiConfiguration config) {
        if (this.mIsShutdown) {
            Log.d(TAG, "ignore add or update network because shutdown is held");
            return -1;
        }
        Message resultMsg = channel.sendMessageSynchronously((int) CMD_ADD_OR_UPDATE_NETWORK, from, 0, config);
        if (messageIsNull(resultMsg)) {
            return -1;
        }
        int result = resultMsg.arg1;
        resultMsg.recycle();
        return result;
    }

    public List<WifiConfiguration> syncGetConfiguredNetworks(int uuid, AsyncChannel channel, int targetUid) {
        return syncGetConfiguredNetworks(uuid, channel, 0, targetUid);
    }

    public List<WifiConfiguration> syncGetConfiguredNetworks(int uuid, AsyncChannel channel, int from, int targetUid) {
        if (this.mIsShutdown) {
            Log.d(TAG, "ignore processing beause shutdown is held");
            return null;
        }
        Message resultMsg = channel.sendMessageSynchronously((int) CMD_GET_CONFIGURED_NETWORKS, uuid, targetUid, new Integer(from));
        if (messageIsNull(resultMsg)) {
            return null;
        }
        if (!(resultMsg.obj instanceof List)) {
            Log.e(TAG, "Wrong type object is delivered. request what:131131, reply what:" + resultMsg.what + " arg1:" + resultMsg.arg1 + " arg2:" + resultMsg.arg2 + " obj:" + resultMsg.obj);
            return null;
        }
        List<WifiConfiguration> result = (List) resultMsg.obj;
        resultMsg.recycle();
        return result;
    }

    public WifiConfiguration syncGetSpecificNetwork(AsyncChannel channel, int networkId) {
        if (this.mIsShutdown) {
            Log.d(TAG, "ignore processing beause shutdown is held");
            return null;
        }
        Message resultMsg = channel.sendMessageSynchronously((int) CMD_GET_A_CONFIGURED_NETWORK, networkId);
        if (messageIsNull(resultMsg)) {
            return null;
        }
        if (resultMsg.obj == null || !(resultMsg.obj instanceof WifiConfiguration)) {
            Log.d(TAG, "resultMsg.obj is null or not instance of WifiConfiguration");
            return null;
        }
        WifiConfiguration result = (WifiConfiguration) resultMsg.obj;
        resultMsg.recycle();
        return result;
    }

    public List<WifiConfiguration> syncGetPrivilegedConfiguredNetwork(AsyncChannel channel) {
        if (this.mIsShutdown) {
            Log.d(TAG, "ignore processing beause shutdown is held");
            return null;
        }
        Message resultMsg = channel.sendMessageSynchronously((int) CMD_GET_PRIVILEGED_CONFIGURED_NETWORKS);
        if (messageIsNull(resultMsg)) {
            return null;
        }
        List<WifiConfiguration> result = (List) resultMsg.obj;
        resultMsg.recycle();
        return result;
    }

    /* access modifiers changed from: package-private */
    public Map<String, Map<Integer, List<ScanResult>>> syncGetAllMatchingFqdnsForScanResults(List<ScanResult> scanResults, AsyncChannel channel) {
        Message resultMsg = channel.sendMessageSynchronously((int) CMD_GET_ALL_MATCHING_FQDNS_FOR_SCAN_RESULTS, scanResults);
        if (messageIsNull(resultMsg)) {
            return new HashMap();
        }
        Map<String, Map<Integer, List<ScanResult>>> configs = (Map) resultMsg.obj;
        resultMsg.recycle();
        return configs;
    }

    public Map<OsuProvider, List<ScanResult>> syncGetMatchingOsuProviders(List<ScanResult> scanResults, AsyncChannel channel) {
        if (this.mIsShutdown) {
            Log.d(TAG, "ignore processing beause shutdown is held");
            return new HashMap();
        }
        Message resultMsg = channel.sendMessageSynchronously((int) CMD_GET_MATCHING_OSU_PROVIDERS, scanResults);
        if (messageIsNull(resultMsg)) {
            return new HashMap();
        }
        Map<OsuProvider, List<ScanResult>> providers = (Map) resultMsg.obj;
        resultMsg.recycle();
        return providers;
    }

    public Map<OsuProvider, PasspointConfiguration> syncGetMatchingPasspointConfigsForOsuProviders(List<OsuProvider> osuProviders, AsyncChannel channel) {
        Message resultMsg = channel.sendMessageSynchronously((int) CMD_GET_MATCHING_PASSPOINT_CONFIGS_FOR_OSU_PROVIDERS, osuProviders);
        if (messageIsNull(resultMsg)) {
            return new HashMap();
        }
        Map<OsuProvider, PasspointConfiguration> result = (Map) resultMsg.obj;
        resultMsg.recycle();
        return result;
    }

    public List<WifiConfiguration> syncGetWifiConfigsForPasspointProfiles(List<String> fqdnList, AsyncChannel channel) {
        Message resultMsg = channel.sendMessageSynchronously((int) CMD_GET_WIFI_CONFIGS_FOR_PASSPOINT_PROFILES, fqdnList);
        if (messageIsNull(resultMsg)) {
            return new ArrayList();
        }
        List<WifiConfiguration> result = (List) resultMsg.obj;
        resultMsg.recycle();
        return result;
    }

    public boolean syncAddOrUpdatePasspointConfig(AsyncChannel channel, PasspointConfiguration config, int uid, String packageName) {
        boolean result = false;
        if (this.mIsShutdown) {
            Log.d(TAG, "ignore processing beause shutdown is held");
            return false;
        }
        Bundle bundle = new Bundle();
        bundle.putInt(EXTRA_UID, uid);
        bundle.putString(EXTRA_PACKAGE_NAME, packageName);
        bundle.putParcelable(EXTRA_PASSPOINT_CONFIGURATION, config);
        Message resultMsg = channel.sendMessageSynchronously((int) CMD_ADD_OR_UPDATE_PASSPOINT_CONFIG, bundle);
        if (messageIsNull(resultMsg)) {
            return false;
        }
        if (resultMsg.arg1 == 1) {
            result = true;
        }
        resultMsg.recycle();
        return result;
    }

    public boolean syncRemovePasspointConfig(AsyncChannel channel, String fqdn) {
        boolean result = false;
        if (this.mIsShutdown) {
            Log.d(TAG, "ignore processing beause shutdown is held");
            return false;
        }
        Message resultMsg = channel.sendMessageSynchronously((int) CMD_REMOVE_PASSPOINT_CONFIG, fqdn);
        if (messageIsNull(resultMsg)) {
            return false;
        }
        if (resultMsg.arg1 == 1) {
            result = true;
        }
        resultMsg.recycle();
        return result;
    }

    public List<PasspointConfiguration> syncGetPasspointConfigs(AsyncChannel channel) {
        if (this.mIsShutdown) {
            Log.d(TAG, "ignore processing beause shutdown is held");
            return new ArrayList();
        }
        Message resultMsg = channel.sendMessageSynchronously((int) CMD_GET_PASSPOINT_CONFIGS);
        if (messageIsNull(resultMsg)) {
            return new ArrayList();
        }
        List<PasspointConfiguration> result = (List) resultMsg.obj;
        resultMsg.recycle();
        return result;
    }

    public boolean syncStartSubscriptionProvisioning(int callingUid, OsuProvider provider, IProvisioningCallback callback, AsyncChannel channel) {
        boolean result = false;
        if (this.mIsShutdown) {
            Log.d(TAG, "ignore processing beause shutdown is held");
            return false;
        }
        Message msg = Message.obtain();
        msg.what = CMD_START_SUBSCRIPTION_PROVISIONING;
        msg.arg1 = callingUid;
        msg.obj = callback;
        msg.getData().putParcelable(EXTRA_OSU_PROVIDER, provider);
        Message resultMsg = channel.sendMessageSynchronously(msg);
        if (messageIsNull(resultMsg)) {
            return false;
        }
        if (resultMsg.arg1 != 0) {
            result = true;
        }
        resultMsg.recycle();
        return result;
    }

    public long syncGetSupportedFeatures(AsyncChannel channel) {
        if (this.mIsShutdown) {
            Log.d(TAG, "ignore processing beause shutdown is held");
            return 0;
        }
        Message resultMsg = channel.sendMessageSynchronously((int) CMD_GET_SUPPORTED_FEATURES);
        if (messageIsNull(resultMsg)) {
            return 0;
        }
        long supportedFeatureSet = ((Long) resultMsg.obj).longValue();
        resultMsg.recycle();
        if (!this.mContext.getPackageManager().hasSystemFeature("android.hardware.wifi.rtt")) {
            return supportedFeatureSet & -385;
        }
        return supportedFeatureSet;
    }

    public WifiLinkLayerStats syncGetLinkLayerStats(AsyncChannel channel) {
        if (this.mIsShutdown) {
            Log.d(TAG, "ignore processing beause shutdown is held");
            return null;
        }
        Message resultMsg = channel.sendMessageSynchronously((int) CMD_GET_LINK_LAYER_STATS);
        if (messageIsNull(resultMsg)) {
            return null;
        }
        WifiLinkLayerStats result = (WifiLinkLayerStats) resultMsg.obj;
        resultMsg.recycle();
        return result;
    }

    public boolean syncRemoveNetwork(AsyncChannel channel, int networkId) {
        return syncRemoveNetwork(channel, 0, networkId);
    }

    public boolean syncRemoveNetwork(AsyncChannel channel, int from, int networkId) {
        boolean result = false;
        if (this.mIsShutdown) {
            Log.d(TAG, "ignore processing beause shutdown is held");
            return false;
        }
        Message resultMsg = channel.sendMessageSynchronously((int) CMD_REMOVE_NETWORK, networkId, from);
        if (messageIsNull(resultMsg)) {
            return false;
        }
        if (resultMsg.arg1 != -1) {
            result = true;
        }
        resultMsg.recycle();
        return result;
    }

    public boolean syncEnableNetwork(AsyncChannel channel, int netId, boolean disableOthers) {
        boolean result = false;
        if (this.mIsShutdown) {
            Log.d(TAG, "ignore processing beause shutdown is held");
            return false;
        }
        Message resultMsg = channel.sendMessageSynchronously((int) CMD_ENABLE_NETWORK, netId, disableOthers ? 1 : 0);
        if (messageIsNull(resultMsg)) {
            return false;
        }
        if (resultMsg.arg1 != -1) {
            result = true;
        }
        resultMsg.recycle();
        return result;
    }

    public void forcinglyEnableAllNetworks(AsyncChannel channel) {
        if (this.mIsShutdown) {
            Log.d(TAG, "can't forcingly enable all networks because shutdown is held");
        } else {
            channel.sendMessage((int) CMD_FORCINGLY_ENABLE_ALL_NETWORKS);
        }
    }

    public boolean syncDisableNetwork(AsyncChannel channel, int netId) {
        if (this.mIsShutdown) {
            Log.d(TAG, "ignore processing beause shutdown is held");
            return false;
        }
        Message resultMsg = channel.sendMessageSynchronously(151569, netId);
        boolean result = resultMsg.what != 151570;
        if (messageIsNull(resultMsg)) {
            return false;
        }
        resultMsg.recycle();
        return result;
    }

    public void enableRssiPolling(boolean enabled) {
        sendMessage(CMD_ENABLE_RSSI_POLL, enabled ? 1 : 0, 0);
    }

    public void setHighPerfModeEnabled(boolean enable) {
        sendMessage(CMD_SET_HIGH_PERF_MODE, enable ? 1 : 0, 0);
    }

    public synchronized void resetSimAuthNetworks(boolean simPresent) {
        sendMessage(CMD_RESET_SIM_NETWORKS, simPresent ? 1 : 0);
    }

    public Network getCurrentNetwork() {
        synchronized (this.mNetworkAgentLock) {
            if (this.mNetworkAgent == null) {
                return null;
            }
            return new Network(this.mNetworkAgent.netId);
        }
    }

    public void enableTdls(String remoteMacAddress, boolean enable) {
        sendMessage(CMD_ENABLE_TDLS, enable ? 1 : 0, 0, remoteMacAddress);
    }

    public void sendBluetoothAdapterStateChange(int state) {
        sendMessage(CMD_BLUETOOTH_ADAPTER_STATE_CHANGE, state, 0);
    }

    public void removeAppConfigs(String packageName, int uid) {
        ApplicationInfo ai = new ApplicationInfo();
        ai.packageName = packageName;
        ai.uid = uid;
        sendMessage(CMD_REMOVE_APP_CONFIGURATIONS, ai);
    }

    public void removeUserConfigs(int userId) {
        sendMessage(CMD_REMOVE_USER_CONFIGURATIONS, userId);
    }

    public void updateBatteryWorkSource(WorkSource newSource) {
        synchronized (this.mRunningWifiUids) {
            if (newSource != null) {
                try {
                    this.mRunningWifiUids.set(newSource);
                } catch (RemoteException e) {
                }
            }
            if (this.mIsRunning) {
                if (!this.mReportedRunning) {
                    this.mBatteryStats.noteWifiRunning(this.mRunningWifiUids);
                    this.mLastRunningWifiUids.set(this.mRunningWifiUids);
                    this.mReportedRunning = true;
                } else if (!this.mLastRunningWifiUids.equals(this.mRunningWifiUids)) {
                    this.mBatteryStats.noteWifiRunningChanged(this.mLastRunningWifiUids, this.mRunningWifiUids);
                    this.mLastRunningWifiUids.set(this.mRunningWifiUids);
                }
            } else if (this.mReportedRunning) {
                this.mBatteryStats.noteWifiStopped(this.mLastRunningWifiUids);
                this.mLastRunningWifiUids.clear();
                this.mReportedRunning = false;
            }
            this.mWakeLock.setWorkSource(newSource);
        }
    }

    public void dumpIpClient(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (this.mIpClient != null) {
            pw.println("IpClient logs have moved to dumpsys network_stack");
        }
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        LocalLog localLog;
        ClientModeImpl.super.dump(fd, pw, args);
        this.mSupplicantStateTracker.dump(fd, pw, args);
        pw.println("mLinkProperties " + this.mLinkProperties);
        pw.println("mWifiInfo " + this.mWifiInfo);
        pw.println("mDhcpResults " + this.mDhcpResults);
        pw.println("mNetworkInfo " + this.mNetworkInfo);
        pw.println("mLastSignalLevel " + this.mLastSignalLevel);
        pw.println("mLastBssid " + this.mLastBssid);
        pw.println("mLastNetworkId " + this.mLastNetworkId);
        pw.println("mOperationalMode " + this.mOperationalMode);
        pw.println("mUserWantsSuspendOpt " + this.mUserWantsSuspendOpt);
        pw.println("mSuspendOptNeedsDisabled " + this.mSuspendOptNeedsDisabled);
        pw.println("mIsWifiOnly : " + isWifiOnly());
        pw.println("FactoryMAC: " + this.mWifiNative.getVendorConnFileInfo(0));
        this.mCountryCode.dump(fd, pw, args);
        this.mNetworkFactory.dump(fd, pw, args);
        this.mUntrustedNetworkFactory.dump(fd, pw, args);
        pw.println("Wlan Wake Reasons:" + this.mWifiNative.getWlanWakeReasonCount());
        pw.println();
        if (this.mWifiGeofenceManager.isSupported()) {
            this.mWifiGeofenceManager.dump(fd, pw, args);
        }
        this.mWifiConfigManager.dump(fd, pw, args);
        pw.println();
        this.mWifiInjector.getCarrierNetworkConfig().dump(fd, pw, args);
        pw.println();
        this.mPasspointManager.dump(pw);
        pw.println();
        this.mWifiDiagnostics.triggerBugReportDataCapture(7);
        this.mWifiDiagnostics.dump(fd, pw, args);
        dumpIpClient(fd, pw, args);
        this.mWifiConnectivityManager.dump(fd, pw, args);
        pw.println("mConcurrentEnabled " + this.mConcurrentEnabled);
        pw.println("mIsImsCallEstablished " + this.mIsImsCallEstablished);
        UnstableApController unstableApController = this.mUnstableApController;
        if (unstableApController != null) {
            unstableApController.dump(fd, pw, args);
        }
        pw.println("W24H (wifi scan auto fav sns agr ...):" + getWifiParameters(false));
        SemWifiIssueDetector semWifiIssueDetector = this.mIssueDetector;
        if (semWifiIssueDetector != null) {
            semWifiIssueDetector.dump(fd, pw, args);
        }
        this.mLinkProbeManager.dump(fd, pw, args);
        this.mWifiInjector.getWifiLastResortWatchdog().dump(fd, pw, args);
        this.mSemSarManager.dump(fd, pw, args);
        pw.println("WifiClientModeImpl connectivity packet log:");
        pw.println("WifiClientModeImpl Name of interface : " + this.mApInterfaceName);
        pw.println();
        LocalLog localLog2 = this.mConnectivityPacketLogForHotspot;
        if (localLog2 != null) {
            localLog2.readOnlyLocalLog().dump(fd, pw, args);
            pw.println("WifiClientModeImpl connectivity packet log:");
            pw.println("WifiClientModeImpl Name of interface : wlan0");
            if (WifiInjector.getInstance().getSemWifiApChipInfo().supportWifiSharing() && (localLog = this.mConnectivityPacketLogForWlan0) != null) {
                localLog.readOnlyLocalLog().dump(fd, pw, args);
            }
        }
        runFwLogTimer();
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private SemConnectivityPacketTracker createPacketTracker(InterfaceParams mInterfaceParams, LocalLog mLog) {
        try {
            return new SemConnectivityPacketTracker(getHandler(), mInterfaceParams, mLog);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Failed to get ConnectivityPacketTracker object: " + e);
            return null;
        }
    }

    public void handleBootCompleted() {
        sendMessage(CMD_BOOT_COMPLETED);
    }

    public void handleUserSwitch(int userId) {
        sendMessage(CMD_USER_SWITCH, userId);
    }

    public void handleUserUnlock(int userId) {
        sendMessage(CMD_USER_UNLOCK, userId);
    }

    public void handleUserStop(int userId) {
        sendMessage(CMD_USER_STOP, userId);
    }

    private void runFwLogTimer() {
        if (!DBG_PRODUCT_DEV) {
            if (this.mFwLogTimer != null) {
                Log.i(TAG, "mFwLogTimer timer cancled");
                this.mFwLogTimer.cancel();
            }
            this.mFwLogTimer = new Timer();
            this.mFwLogTimer.schedule(new TimerTask() {
                /* class com.android.server.wifi.ClientModeImpl.C037115 */

                public void run() {
                    Log.i(ClientModeImpl.TAG, "mFwLogTimer timer expired - start folder initialization");
                    ClientModeImpl.this.resetFwLogFolder();
                    ClientModeImpl.this.mFwLogTimer = null;
                }
            }, WifiDiagnostics.MIN_DUMP_TIME_WINDOW_MILLIS);
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void resetFwLogFolder() {
        if (!DBG_PRODUCT_DEV) {
            Log.i(TAG, "resetFwLogFolder");
            try {
                File folder = new File("/data/log/wifi/");
                if (folder.exists()) {
                    removeFolderFiles(folder);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (!this.mWifiNative.removeVendorLogFiles()) {
                Log.e(TAG, "Removing vendor logs got failed.");
            }
        }
    }

    private void removeFolderFiles(File folder) {
        try {
            File[] logFiles = folder.listFiles();
            if (logFiles != null && logFiles.length > 0) {
                for (File logFile : logFiles) {
                    Log.i(TAG, "WifiStateMachine : " + logFile + " deleted");
                    logFile.delete();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private String getTimeToString() {
        Calendar cal = Calendar.getInstance();
        DecimalFormat df = new DecimalFormat("00", DecimalFormatSymbols.getInstance(Locale.US));
        String month = df.format((long) (cal.get(2) + 1));
        String day = df.format((long) cal.get(5));
        String hour = df.format((long) cal.get(11));
        String min = df.format((long) cal.get(12));
        df.format((long) cal.get(13));
        String sysdump_time = cal.get(1) + month + day + hour + min;
        Log.i(TAG, "getTimeToString : " + sysdump_time);
        return sysdump_time;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void logStateAndMessage(Message message, State state) {
        ReportUtil.updateWifiStateMachineProcessMessage(state.getClass().getSimpleName(), message.what);
        this.mMessageHandlingStatus = 0;
        if (this.mVerboseLoggingEnabled) {
            logd(" " + state.getClass().getSimpleName() + " " + getLogRecString(message));
        }
    }

    /* access modifiers changed from: protected */
    public boolean recordLogRec(Message msg) {
        if (msg.what != CMD_RSSI_POLL) {
            return true;
        }
        return this.mVerboseLoggingEnabled;
    }

    /* access modifiers changed from: protected */
    public String getLogRecString(Message msg) {
        StringBuilder sb = new StringBuilder();
        sb.append("screen=");
        sb.append(this.mScreenOn ? "on" : "off");
        if (this.mMessageHandlingStatus != 0) {
            sb.append("(");
            sb.append(this.mMessageHandlingStatus);
            sb.append(")");
        }
        if (msg.sendingUid > 0 && msg.sendingUid != 1010) {
            sb.append(" uid=" + msg.sendingUid);
        }
        switch (msg.what) {
            case CMD_ADD_OR_UPDATE_NETWORK /*{ENCODED_INT: 131124}*/:
                sb.append(" ");
                sb.append(Integer.toString(msg.arg1));
                sb.append(" ");
                sb.append(Integer.toString(msg.arg2));
                if (msg.obj != null) {
                    WifiConfiguration config = (WifiConfiguration) msg.obj;
                    sb.append(" ");
                    sb.append(config.configKey());
                    sb.append(" prio=");
                    sb.append(config.priority);
                    sb.append(" status=");
                    sb.append(config.status);
                    if (config.BSSID != null) {
                        sb.append(" ");
                        sb.append(config.BSSID);
                    }
                    WifiConfiguration curConfig = getCurrentWifiConfiguration();
                    if (curConfig != null) {
                        if (!curConfig.configKey().equals(config.configKey())) {
                            sb.append(" current=");
                            sb.append(curConfig.configKey());
                            sb.append(" prio=");
                            sb.append(curConfig.priority);
                            sb.append(" status=");
                            sb.append(curConfig.status);
                            break;
                        } else {
                            sb.append(" is current");
                            break;
                        }
                    }
                }
                break;
            case CMD_ENABLE_NETWORK /*{ENCODED_INT: 131126}*/:
            case 151569:
                sb.append(" ");
                sb.append(Integer.toString(msg.arg1));
                sb.append(" ");
                sb.append(Integer.toString(msg.arg2));
                String key = this.mWifiConfigManager.getLastSelectedNetworkConfigKey();
                if (key != null) {
                    sb.append(" last=");
                    sb.append(key);
                }
                WifiConfiguration config2 = this.mWifiConfigManager.getConfiguredNetwork(msg.arg1);
                if (config2 != null && (key == null || !config2.configKey().equals(key))) {
                    sb.append(" target=");
                    sb.append(key);
                    break;
                }
            case CMD_GET_CONFIGURED_NETWORKS /*{ENCODED_INT: 131131}*/:
                sb.append(" ");
                sb.append(Integer.toString(msg.arg1));
                sb.append(" ");
                sb.append(Integer.toString(msg.arg2));
                sb.append(" num=");
                sb.append(this.mWifiConfigManager.getConfiguredNetworks().size());
                break;
            case CMD_RSSI_POLL /*{ENCODED_INT: 131155}*/:
            case CMD_ONESHOT_RSSI_POLL /*{ENCODED_INT: 131156}*/:
            case CMD_UNWANTED_NETWORK /*{ENCODED_INT: 131216}*/:
            case 151572:
                sb.append(" ");
                sb.append(Integer.toString(msg.arg1));
                sb.append(" ");
                sb.append(Integer.toString(msg.arg2));
                if (!(this.mWifiInfo.getSSID() == null || this.mWifiInfo.getSSID() == null)) {
                    sb.append(" ");
                    sb.append(this.mWifiInfo.getSSID());
                }
                if (this.mWifiInfo.getBSSID() != null) {
                    sb.append(" ");
                    sb.append(this.mWifiInfo.getBSSID());
                }
                sb.append(" rssi=");
                sb.append(this.mWifiInfo.getRssi());
                sb.append(" f=");
                sb.append(this.mWifiInfo.getFrequency());
                sb.append(" sc=");
                sb.append(this.mWifiInfo.score);
                sb.append(" link=");
                sb.append(this.mWifiInfo.getLinkSpeed());
                sb.append(String.format(" tx=%.1f,", Double.valueOf(this.mWifiInfo.txSuccessRate)));
                sb.append(String.format(" %.1f,", Double.valueOf(this.mWifiInfo.txRetriesRate)));
                sb.append(String.format(" %.1f ", Double.valueOf(this.mWifiInfo.txBadRate)));
                sb.append(String.format(" rx=%.1f", Double.valueOf(this.mWifiInfo.rxSuccessRate)));
                sb.append(String.format(" bcn=%d", Integer.valueOf(this.mRunningBeaconCount)));
                sb.append(String.format(" snr=%d", Integer.valueOf(this.mWifiInfo.semGetSnr())));
                sb.append(String.format(" lqcm_tx=%d", Integer.valueOf(this.mWifiInfo.semGetLqcmTx())));
                sb.append(String.format(" lqcm_rx=%d", Integer.valueOf(this.mWifiInfo.semGetLqcmRx())));
                sb.append(String.format(" ap_cu=%d", Integer.valueOf(this.mWifiInfo.semGetApCu())));
                String report = reportOnTime();
                if (report != null) {
                    sb.append(" ");
                    sb.append(report);
                }
                sb.append(String.format(" score=%d", Integer.valueOf(this.mWifiInfo.score)));
                break;
            case CMD_ROAM_WATCHDOG_TIMER /*{ENCODED_INT: 131166}*/:
                sb.append(" ");
                sb.append(Integer.toString(msg.arg1));
                sb.append(" ");
                sb.append(Integer.toString(msg.arg2));
                sb.append(" cur=");
                sb.append(this.mRoamWatchdogCount);
                break;
            case CMD_DISCONNECTING_WATCHDOG_TIMER /*{ENCODED_INT: 131168}*/:
                sb.append(" ");
                sb.append(Integer.toString(msg.arg1));
                sb.append(" ");
                sb.append(Integer.toString(msg.arg2));
                sb.append(" cur=");
                sb.append(this.mDisconnectingWatchdogCount);
                break;
            case CMD_IP_CONFIGURATION_LOST /*{ENCODED_INT: 131211}*/:
                int count = -1;
                WifiConfiguration c = getCurrentWifiConfiguration();
                if (c != null) {
                    count = c.getNetworkSelectionStatus().getDisableReasonCounter(4);
                }
                sb.append(" ");
                sb.append(Integer.toString(msg.arg1));
                sb.append(" ");
                sb.append(Integer.toString(msg.arg2));
                sb.append(" failures: ");
                sb.append(Integer.toString(count));
                sb.append("/");
                sb.append(Integer.toString(this.mFacade.getIntegerSetting(this.mContext, "wifi_max_dhcp_retry_count", 0)));
                if (this.mWifiInfo.getBSSID() != null) {
                    sb.append(" ");
                    sb.append(this.mWifiInfo.getBSSID());
                }
                sb.append(String.format(" bcn=%d", Integer.valueOf(this.mRunningBeaconCount)));
                break;
            case CMD_UPDATE_LINKPROPERTIES /*{ENCODED_INT: 131212}*/:
                sb.append(" ");
                sb.append(Integer.toString(msg.arg1));
                sb.append(" ");
                sb.append(Integer.toString(msg.arg2));
                if (this.mLinkProperties != null) {
                    sb.append(" ");
                    sb.append(getLinkPropertiesSummary(this.mLinkProperties));
                    break;
                }
                break;
            case CMD_TARGET_BSSID /*{ENCODED_INT: 131213}*/:
            case CMD_ASSOCIATED_BSSID /*{ENCODED_INT: 131219}*/:
                sb.append(" ");
                sb.append(Integer.toString(msg.arg1));
                sb.append(" ");
                sb.append(Integer.toString(msg.arg2));
                if (msg.obj != null) {
                    sb.append(" BSSID=");
                    sb.append((String) msg.obj);
                }
                if (this.mTargetRoamBSSID != null) {
                    sb.append(" Target=");
                    sb.append(this.mTargetRoamBSSID);
                }
                sb.append(" roam=");
                sb.append(Boolean.toString(this.mIsAutoRoaming));
                break;
            case CMD_START_CONNECT /*{ENCODED_INT: 131215}*/:
            case 151553:
                sb.append(" ");
                sb.append(Integer.toString(msg.arg1));
                sb.append(" ");
                sb.append(Integer.toString(msg.arg2));
                WifiConfiguration config3 = this.mWifiConfigManager.getConfiguredNetwork(msg.arg1);
                if (config3 != null) {
                    sb.append(" ");
                    sb.append(config3.configKey());
                }
                if (this.mTargetRoamBSSID != null) {
                    sb.append(" ");
                    sb.append(this.mTargetRoamBSSID);
                }
                sb.append(" roam=");
                sb.append(Boolean.toString(this.mIsAutoRoaming));
                WifiConfiguration config4 = getCurrentWifiConfiguration();
                if (config4 != null) {
                    sb.append(config4.configKey());
                    break;
                }
                break;
            case CMD_START_ROAM /*{ENCODED_INT: 131217}*/:
                sb.append(" ");
                sb.append(Integer.toString(msg.arg1));
                sb.append(" ");
                sb.append(Integer.toString(msg.arg2));
                ScanResult result = (ScanResult) msg.obj;
                if (result != null) {
                    Long now = Long.valueOf(this.mClock.getWallClockMillis());
                    sb.append(" bssid=");
                    sb.append(result.BSSID);
                    sb.append(" rssi=");
                    sb.append(result.level);
                    sb.append(" freq=");
                    sb.append(result.frequency);
                    if (result.seen <= 0 || result.seen >= now.longValue()) {
                        sb.append(" !seen=");
                        sb.append(result.seen);
                    } else {
                        sb.append(" seen=");
                        sb.append(now.longValue() - result.seen);
                    }
                }
                if (this.mTargetRoamBSSID != null) {
                    sb.append(" ");
                    sb.append(this.mTargetRoamBSSID);
                }
                sb.append(" roam=");
                sb.append(Boolean.toString(this.mIsAutoRoaming));
                sb.append(" fail count=");
                sb.append(Integer.toString(this.mRoamFailCount));
                break;
            case CMD_IP_REACHABILITY_LOST /*{ENCODED_INT: 131221}*/:
                if (msg.obj != null) {
                    sb.append(" ");
                    sb.append((String) msg.obj);
                    break;
                }
                break;
            case CMD_START_RSSI_MONITORING_OFFLOAD /*{ENCODED_INT: 131234}*/:
            case CMD_STOP_RSSI_MONITORING_OFFLOAD /*{ENCODED_INT: 131235}*/:
            case CMD_RSSI_THRESHOLD_BREACHED /*{ENCODED_INT: 131236}*/:
                sb.append(" rssi=");
                sb.append(Integer.toString(msg.arg1));
                sb.append(" thresholds=");
                sb.append(Arrays.toString(this.mRssiRanges));
                break;
            case CMD_IPV4_PROVISIONING_SUCCESS /*{ENCODED_INT: 131272}*/:
                sb.append(" ");
                sb.append(msg.obj);
                break;
            case CMD_INSTALL_PACKET_FILTER /*{ENCODED_INT: 131274}*/:
                sb.append(" len=" + ((byte[]) msg.obj).length);
                break;
            case CMD_SET_FALLBACK_PACKET_FILTERING /*{ENCODED_INT: 131275}*/:
                sb.append(" enabled=" + ((Boolean) msg.obj).booleanValue());
                break;
            case CMD_USER_SWITCH /*{ENCODED_INT: 131277}*/:
                sb.append(" userId=");
                sb.append(Integer.toString(msg.arg1));
                break;
            case CMD_PRE_DHCP_ACTION /*{ENCODED_INT: 131327}*/:
                sb.append(" ");
                sb.append(Integer.toString(msg.arg1));
                sb.append(" ");
                sb.append(Integer.toString(msg.arg2));
                sb.append(" txpkts=");
                sb.append(this.mWifiInfo.txSuccess);
                sb.append(",");
                sb.append(this.mWifiInfo.txBad);
                sb.append(",");
                sb.append(this.mWifiInfo.txRetries);
                break;
            case CMD_POST_DHCP_ACTION /*{ENCODED_INT: 131329}*/:
                if (this.mLinkProperties != null) {
                    sb.append(" ");
                    sb.append(getLinkPropertiesSummary(this.mLinkProperties));
                    break;
                }
                break;
            case CMD_GET_A_CONFIGURED_NETWORK /*{ENCODED_INT: 131581}*/:
                sb.append(" networkId=");
                sb.append(Integer.toString(msg.arg1));
                break;
            case WifiP2pServiceImpl.P2P_CONNECTION_CHANGED /*{ENCODED_INT: 143371}*/:
                sb.append(" ");
                sb.append(Integer.toString(msg.arg1));
                sb.append(" ");
                sb.append(Integer.toString(msg.arg2));
                if (msg.obj != null) {
                    NetworkInfo info = (NetworkInfo) msg.obj;
                    NetworkInfo.State state = info.getState();
                    NetworkInfo.DetailedState detailedState = info.getDetailedState();
                    if (state != null) {
                        sb.append(" st=");
                        sb.append(state);
                    }
                    if (detailedState != null) {
                        sb.append("/");
                        sb.append(detailedState);
                        break;
                    }
                }
                break;
            case WifiMonitor.NETWORK_CONNECTION_EVENT /*{ENCODED_INT: 147459}*/:
                sb.append(" ");
                sb.append(Integer.toString(msg.arg1));
                sb.append(" ");
                sb.append(Integer.toString(msg.arg2));
                sb.append(" ");
                sb.append(this.mLastBssid);
                sb.append(" nid=");
                sb.append(this.mLastNetworkId);
                WifiConfiguration config5 = getCurrentWifiConfiguration();
                if (config5 != null) {
                    sb.append(" ");
                    sb.append(config5.configKey());
                }
                String key2 = this.mWifiConfigManager.getLastSelectedNetworkConfigKey();
                if (key2 != null) {
                    sb.append(" last=");
                    sb.append(key2);
                    break;
                }
                break;
            case WifiMonitor.NETWORK_DISCONNECTION_EVENT /*{ENCODED_INT: 147460}*/:
                if (msg.obj != null) {
                    sb.append(" ");
                    sb.append((String) msg.obj);
                }
                sb.append(" nid=");
                sb.append(msg.arg1);
                sb.append(" reason=");
                sb.append(msg.arg2);
                if (this.mLastBssid != null) {
                    sb.append(" lastbssid=");
                    sb.append(this.mLastBssid);
                }
                if (this.mWifiInfo.getFrequency() != -1) {
                    sb.append(" freq=");
                    sb.append(this.mWifiInfo.getFrequency());
                    sb.append(" rssi=");
                    sb.append(this.mWifiInfo.getRssi());
                    break;
                }
                break;
            case WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT /*{ENCODED_INT: 147462}*/:
                sb.append(" ");
                sb.append(Integer.toString(msg.arg1));
                sb.append(" ");
                sb.append(Integer.toString(msg.arg2));
                StateChangeResult stateChangeResult = (StateChangeResult) msg.obj;
                if (stateChangeResult != null) {
                    sb.append(stateChangeResult.toString());
                    break;
                }
                break;
            case 147499:
                sb.append(" ");
                sb.append(" timedOut=" + Integer.toString(msg.arg1));
                sb.append(" ");
                sb.append(Integer.toString(msg.arg2));
                String bssid = (String) msg.obj;
                if (bssid != null && bssid.length() > 0) {
                    sb.append(" ");
                    sb.append(bssid);
                }
                sb.append(" blacklist=" + Boolean.toString(this.mDidBlackListBSSID));
                break;
            case WifiMonitor.ANQP_DONE_EVENT /*{ENCODED_INT: 147500}*/:
                sb.append(" ");
                sb.append(Integer.toString(msg.arg1));
                sb.append(" ");
                sb.append(Integer.toString(msg.arg2));
                if (msg.obj != null) {
                    AnqpEvent anqpEvent = (AnqpEvent) msg.obj;
                    if (anqpEvent.getBssid() != 0) {
                        sb.append(" BSSID=");
                        sb.append(Utils.macToString(anqpEvent.getBssid()));
                        break;
                    }
                }
                break;
            case WifiMonitor.BSSID_PRUNED_EVENT /*{ENCODED_INT: 147501}*/:
                sb.append(" ");
                sb.append(Integer.toString(msg.arg1));
                sb.append(" ");
                sb.append(Integer.toString(msg.arg2));
                if (msg.obj != null) {
                    sb.append(" ");
                    sb.append(msg.obj.toString());
                    break;
                }
                break;
            case 151556:
                sb.append(" ");
                sb.append(Integer.toString(msg.arg1));
                sb.append(" ");
                sb.append(Integer.toString(msg.arg2));
                WifiConfiguration config6 = (WifiConfiguration) msg.obj;
                if (config6 != null) {
                    sb.append(" ");
                    sb.append(config6.configKey());
                    sb.append(" nid=");
                    sb.append(config6.networkId);
                    if (config6.hiddenSSID) {
                        sb.append(" hidden");
                    }
                    if (config6.preSharedKey != null) {
                        sb.append(" hasPSK");
                    }
                    if (config6.ephemeral) {
                        sb.append(" ephemeral");
                    }
                    if (config6.selfAdded) {
                        sb.append(" selfAdded");
                    }
                    sb.append(" cuid=");
                    sb.append(config6.creatorUid);
                    sb.append(" suid=");
                    sb.append(config6.lastUpdateUid);
                    WifiConfiguration.NetworkSelectionStatus netWorkSelectionStatus = config6.getNetworkSelectionStatus();
                    sb.append(" ajst=");
                    sb.append(netWorkSelectionStatus.getNetworkStatusString());
                    break;
                }
                break;
            case 151559:
                sb.append(" ");
                sb.append(Integer.toString(msg.arg1));
                sb.append(" ");
                sb.append(Integer.toString(msg.arg2));
                WifiConfiguration config7 = (WifiConfiguration) msg.obj;
                if (config7 != null) {
                    sb.append(" ");
                    sb.append(config7.configKey());
                    sb.append(" nid=");
                    sb.append(config7.networkId);
                    if (config7.hiddenSSID) {
                        sb.append(" hidden");
                    }
                    if (config7.preSharedKey != null && !config7.preSharedKey.equals("*")) {
                        sb.append(" hasPSK");
                    }
                    if (config7.ephemeral) {
                        sb.append(" ephemeral");
                    }
                    if (config7.selfAdded) {
                        sb.append(" selfAdded");
                    }
                    sb.append(" cuid=");
                    sb.append(config7.creatorUid);
                    sb.append(" suid=");
                    sb.append(config7.lastUpdateUid);
                    break;
                }
                break;
            default:
                sb.append(" ");
                sb.append(Integer.toString(msg.arg1));
                sb.append(" ");
                sb.append(Integer.toString(msg.arg2));
                break;
        }
        return sb.toString();
    }

    /* access modifiers changed from: protected */
    public String getWhatToString(int what) {
        String s = sGetWhatToString.get(what);
        if (s != null) {
            return s;
        }
        switch (what) {
            case 69632:
                return "CMD_CHANNEL_HALF_CONNECTED";
            case 69636:
                return "CMD_CHANNEL_DISCONNECTED";
            case WifiP2pServiceImpl.GROUP_CREATING_TIMED_OUT /*{ENCODED_INT: 143361}*/:
                return "GROUP_CREATING_TIMED_OUT";
            case WifiP2pServiceImpl.P2P_CONNECTION_CHANGED /*{ENCODED_INT: 143371}*/:
                return "P2P_CONNECTION_CHANGED";
            case WifiP2pServiceImpl.DISCONNECT_WIFI_REQUEST /*{ENCODED_INT: 143372}*/:
                return "DISCONNECT_WIFI_REQUEST";
            case WifiP2pServiceImpl.DISCONNECT_WIFI_RESPONSE /*{ENCODED_INT: 143373}*/:
                return "DISCONNECT_WIFI_RESPONSE";
            case WifiP2pServiceImpl.SET_MIRACAST_MODE /*{ENCODED_INT: 143374}*/:
                return "SET_MIRACAST_MODE";
            case WifiP2pServiceImpl.BLOCK_DISCOVERY /*{ENCODED_INT: 143375}*/:
                return "BLOCK_DISCOVERY";
            case WifiP2pServiceImpl.DISABLE_P2P_RSP /*{ENCODED_INT: 143395}*/:
                return "DISABLE_P2P_RSP";
            case WifiMonitor.NETWORK_CONNECTION_EVENT /*{ENCODED_INT: 147459}*/:
                return "NETWORK_CONNECTION_EVENT";
            case WifiMonitor.NETWORK_DISCONNECTION_EVENT /*{ENCODED_INT: 147460}*/:
                return "NETWORK_DISCONNECTION_EVENT";
            case WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT /*{ENCODED_INT: 147462}*/:
                return "SUPPLICANT_STATE_CHANGE_EVENT";
            case WifiMonitor.AUTHENTICATION_FAILURE_EVENT /*{ENCODED_INT: 147463}*/:
                return "AUTHENTICATION_FAILURE_EVENT";
            case WifiMonitor.SUP_BIGDATA_EVENT /*{ENCODED_INT: 147469}*/:
                return "WifiMonitor.SUP_BIGDATA_EVENT";
            case WifiMonitor.SUP_REQUEST_IDENTITY /*{ENCODED_INT: 147471}*/:
                return "SUP_REQUEST_IDENTITY";
            case 147499:
                return "ASSOCIATION_REJECTION_EVENT";
            case WifiMonitor.ANQP_DONE_EVENT /*{ENCODED_INT: 147500}*/:
                return "ANQP_DONE_EVENT";
            case WifiMonitor.BSSID_PRUNED_EVENT /*{ENCODED_INT: 147501}*/:
                return "WifiMonitor.BSSID_PRUNED_EVENT";
            case WifiMonitor.GAS_QUERY_START_EVENT /*{ENCODED_INT: 147507}*/:
                return "GAS_QUERY_START_EVENT";
            case WifiMonitor.GAS_QUERY_DONE_EVENT /*{ENCODED_INT: 147508}*/:
                return "GAS_QUERY_DONE_EVENT";
            case WifiMonitor.RX_HS20_ANQP_ICON_EVENT /*{ENCODED_INT: 147509}*/:
                return "RX_HS20_ANQP_ICON_EVENT";
            case WifiMonitor.HS20_REMEDIATION_EVENT /*{ENCODED_INT: 147517}*/:
                return "HS20_REMEDIATION_EVENT";
            case 147527:
                return "WifiMonitor.EAP_EVENT_MESSAGE";
            case 151553:
                return "CONNECT_NETWORK";
            case 151556:
                return "FORGET_NETWORK";
            case 151559:
                return "SAVE_NETWORK";
            case 151569:
                return "DISABLE_NETWORK";
            case 151572:
                return "RSSI_PKTCNT_FETCH";
            default:
                return "what:" + Integer.toString(what);
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void handleScreenStateChanged(boolean screenOn) {
        this.mScreenOn = screenOn;
        if (this.mVerboseLoggingEnabled) {
            logd(" handleScreenStateChanged Enter: screenOn=" + screenOn + " mUserWantsSuspendOpt=" + this.mUserWantsSuspendOpt + " state " + getCurrentState().getName() + " suppState:" + this.mSupplicantStateTracker.getSupplicantStateName());
        }
        enableRssiPolling(screenOn || mRssiPollingScreenOffEnabled != 0);
        if (this.mUserWantsSuspendOpt.get()) {
            int shouldReleaseWakeLock = 0;
            if (screenOn) {
                sendMessage(CMD_SET_SUSPEND_OPT_ENABLED, 0, 0);
            } else {
                if (isConnected()) {
                    this.mSuspendWakeLock.acquire(2000);
                    shouldReleaseWakeLock = 1;
                }
                sendMessage(CMD_SET_SUSPEND_OPT_ENABLED, 1, shouldReleaseWakeLock);
            }
        }
        getWifiLinkLayerStats();
        this.mOnTimeScreenStateChange = this.mOnTime;
        this.mLastScreenStateChangeTimeStamp = this.mLastLinkLayerStatsUpdate;
        this.mWifiMetrics.setScreenState(screenOn);
        this.mWifiConnectivityManager.handleScreenStateChanged(screenOn);
        this.mNetworkFactory.handleScreenStateChanged(screenOn);
        WifiLockManager wifiLockManager = this.mWifiInjector.getWifiLockManager();
        if (wifiLockManager != null) {
            wifiLockManager.handleScreenStateChanged(screenOn);
        }
        this.mSarManager.handleScreenStateChanged(screenOn);
        if (this.mVerboseLoggingEnabled) {
            log("handleScreenStateChanged Exit: " + screenOn);
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean checkAndSetConnectivityInstance() {
        if (this.mCm == null) {
            this.mCm = (ConnectivityManager) this.mContext.getSystemService("connectivity");
        }
        if (this.mCm != null) {
            return true;
        }
        Log.e(TAG, "Cannot retrieve connectivity service");
        return false;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void setSuspendOptimizationsNative(int reason, boolean enabled) {
        if (this.mVerboseLoggingEnabled) {
            log("setSuspendOptimizationsNative: " + reason + " " + enabled + " -want " + this.mUserWantsSuspendOpt.get() + " stack:" + Thread.currentThread().getStackTrace()[2].getMethodName() + " - " + Thread.currentThread().getStackTrace()[3].getMethodName() + " - " + Thread.currentThread().getStackTrace()[4].getMethodName() + " - " + Thread.currentThread().getStackTrace()[5].getMethodName());
        }
        if (enabled) {
            this.mSuspendOptNeedsDisabled &= ~reason;
            if (this.mSuspendOptNeedsDisabled == 0 && this.mUserWantsSuspendOpt.get()) {
                if (this.mVerboseLoggingEnabled) {
                    log("setSuspendOptimizationsNative do it " + reason + " " + enabled + " stack:" + Thread.currentThread().getStackTrace()[2].getMethodName() + " - " + Thread.currentThread().getStackTrace()[3].getMethodName() + " - " + Thread.currentThread().getStackTrace()[4].getMethodName() + " - " + Thread.currentThread().getStackTrace()[5].getMethodName());
                }
                this.mWifiNative.setSuspendOptimizations(this.mInterfaceName, true);
                return;
            }
            return;
        }
        this.mSuspendOptNeedsDisabled |= reason;
        this.mWifiNative.setSuspendOptimizations(this.mInterfaceName, false);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void setSuspendOptimizations(int reason, boolean enabled) {
        if (this.mVerboseLoggingEnabled) {
            log("setSuspendOptimizations: " + reason + " " + enabled);
        }
        if (enabled) {
            this.mSuspendOptNeedsDisabled &= ~reason;
        } else {
            this.mSuspendOptNeedsDisabled |= reason;
        }
        if (this.mVerboseLoggingEnabled) {
            log("mSuspendOptNeedsDisabled " + this.mSuspendOptNeedsDisabled);
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void knoxAutoSwitchPolicy(int newRssi) {
        int thresholdRssi;
        if (this.mLastConnectedTime == -1) {
            logd("knoxCustom WifiAutoSwitch: not connected yet");
        } else if (newRssi == -127) {
            logd("knoxCustom WifiAutoSwitch: newRssi is invalid");
        } else {
            if (this.customDeviceManager == null) {
                this.customDeviceManager = CustomDeviceManagerProxy.getInstance();
            }
            if (this.customDeviceManager.getWifiAutoSwitchState() && newRssi < (thresholdRssi = this.customDeviceManager.getWifiAutoSwitchThreshold())) {
                if (DBG) {
                    logd("KnoxCustom WifiAutoSwitch: current = " + newRssi);
                }
                long now = this.mClock.getElapsedSinceBootMillis();
                if (DBG) {
                    logd("KnoxCustom WifiAutoSwitch: last check was " + (now - this.mLastConnectedTime) + " ms ago");
                }
                int delay = this.customDeviceManager.getWifiAutoSwitchDelay();
                if (now < this.mLastConnectedTime + (((long) delay) * 1000)) {
                    logd("KnoxCustom WifiAutoSwitch: delay " + delay);
                    return;
                }
                int bestRssi = thresholdRssi;
                int bestNetworkId = -1;
                List<ScanResult> scanResults = this.mScanRequestProxy.getScanResults();
                for (WifiConfiguration config : this.mWifiConfigManager.getSavedNetworks(1010)) {
                    for (ScanResult result : scanResults) {
                        if (config.SSID.equals("\"" + result.SSID + "\"")) {
                            if (DBG) {
                                logd("KnoxCustom WifiAutoSwitch: " + config.SSID + " = " + result.level);
                            }
                            if (result.level > bestRssi) {
                                bestRssi = result.level;
                                bestNetworkId = config.networkId;
                            }
                        }
                    }
                }
                if (bestNetworkId != -1) {
                    if (DBG) {
                        logd("KnoxCustom WifiAutoSwitch: switching to " + bestNetworkId);
                    }
                    notifyDisconnectInternalReason(15);
                    this.mWifiNative.disconnect(this.mInterfaceName);
                    this.mWifiConfigManager.enableNetwork(bestNetworkId, true, 1000);
                    this.mWifiNative.reconnect(this.mInterfaceName);
                }
            }
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void fetchRssiLinkSpeedAndFrequencyNative() {
        WifiNative.SignalPollResult pollResult = this.mWifiNative.signalPoll(this.mInterfaceName);
        if (pollResult != null) {
            int newRssi = pollResult.currentRssi;
            int newTxLinkSpeed = pollResult.txBitrate;
            int newFrequency = pollResult.associationFrequency;
            int newRxLinkSpeed = pollResult.rxBitrate;
            if (this.mVerboseLoggingEnabled) {
                logd("fetchRssiLinkSpeedAndFrequencyNative rssi=" + newRssi + " TxLinkspeed=" + newTxLinkSpeed + " freq=" + newFrequency + " RxLinkSpeed=" + newRxLinkSpeed);
            }
            if (newRssi <= -127 || newRssi >= 200) {
                this.mWifiInfo.setRssi(WifiMetrics.MIN_RSSI_DELTA);
                updateCapabilities();
            } else {
                if (newRssi > 0) {
                    Log.wtf(TAG, "Error! +ve value RSSI: " + newRssi);
                    newRssi += -256;
                }
                this.mWifiInfo.setRssi(newRssi);
                int newSignalLevel = WifiManager.calculateSignalLevel(newRssi, 5);
                if (newSignalLevel != this.mLastSignalLevel) {
                    updateCapabilities();
                    sendRssiChangeBroadcast(newRssi);
                    this.mBigDataManager.addOrUpdateValue(9, newTxLinkSpeed);
                }
                this.mLastSignalLevel = newSignalLevel;
            }
            if (newTxLinkSpeed > 0) {
                this.mWifiInfo.setLinkSpeed(newTxLinkSpeed);
                this.mWifiInfo.setTxLinkSpeedMbps(newTxLinkSpeed);
            }
            if (newRxLinkSpeed > 0) {
                this.mWifiInfo.setRxLinkSpeedMbps(newRxLinkSpeed);
            }
            if (newFrequency > 0) {
                this.mWifiInfo.setFrequency(newFrequency);
            }
            this.mWifiInfo.semSetBcnCnt(this.mRunningBeaconCount);
            int snr = this.mWifiNative.getSnr(this.mInterfaceName);
            int lqcmReport = this.mWifiNative.getLqcmReport(this.mInterfaceName);
            int ApCu = this.mWifiNative.getApCu(this.mInterfaceName);
            this.mWifiInfo.semSetSnr(snr > 0 ? snr : 0);
            int lqcmTxIndex = 255;
            int i = -1;
            int lqcmRxIndex = lqcmReport != -1 ? (16711680 & lqcmReport) >> 16 : 255;
            if (lqcmReport != -1) {
                lqcmTxIndex = (65280 & lqcmReport) >> 8;
            }
            this.mWifiInfo.semSetLqcmTx(lqcmTxIndex);
            this.mWifiInfo.semSetLqcmRx(lqcmRxIndex);
            ExtendedWifiInfo extendedWifiInfo = this.mWifiInfo;
            if (ApCu > -1) {
                i = ApCu;
            }
            extendedWifiInfo.semSetApCu(i);
            this.mWifiConfigManager.updateScanDetailCacheFromWifiInfo(this.mWifiInfo);
            this.mWifiMetrics.handlePollResult(this.mWifiInfo);
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void cleanWifiScore() {
        ExtendedWifiInfo extendedWifiInfo = this.mWifiInfo;
        extendedWifiInfo.txBadRate = 0.0d;
        extendedWifiInfo.txSuccessRate = 0.0d;
        extendedWifiInfo.txRetriesRate = 0.0d;
        extendedWifiInfo.rxSuccessRate = 0.0d;
        this.mWifiScoreReport.reset();
        this.mLastLinkLayerStats = null;
    }

    private void checkAndResetMtu() {
        int mtu;
        LinkProperties linkProperties = this.mLinkProperties;
        if (linkProperties != null && (mtu = linkProperties.getMtu()) != 1500 && mtu != 0) {
            Log.i(TAG, "reset MTU value from " + mtu);
            this.mWifiNative.initializeMtu(this.mInterfaceName);
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void updateLinkProperties(LinkProperties newLp) {
        log("Link configuration changed for netId: " + this.mLastNetworkId + " old: " + this.mLinkProperties + " new: " + newLp);
        this.mOldLinkProperties = this.mLinkProperties;
        this.mLinkProperties = newLp;
        WifiNetworkAgent wifiNetworkAgent = this.mNetworkAgent;
        if (wifiNetworkAgent != null) {
            wifiNetworkAgent.sendLinkProperties(this.mLinkProperties);
        }
        if (getNetworkDetailedState() == NetworkInfo.DetailedState.CONNECTED) {
            sendLinkConfigurationChangedBroadcast();
            if (detectIpv6ProvisioningFailure(this.mOldLinkProperties, this.mLinkProperties)) {
                handleWifiNetworkCallbacks(11);
            }
        }
        if (this.mVerboseLoggingEnabled) {
            StringBuilder sb = new StringBuilder();
            sb.append("updateLinkProperties nid: " + this.mLastNetworkId);
            sb.append(" state: " + getNetworkDetailedState());
            if (this.mLinkProperties != null) {
                sb.append(" ");
                sb.append(getLinkPropertiesSummary(this.mLinkProperties));
            }
            logd(sb.toString());
        }
        handleWifiNetworkCallbacks(7);
    }

    private void clearLinkProperties() {
        synchronized (this.mDhcpResultsLock) {
            if (this.mDhcpResults != null) {
                this.mDhcpResults.clear();
            }
        }
        this.mLinkProperties.clear();
        WifiNetworkAgent wifiNetworkAgent = this.mNetworkAgent;
        if (wifiNetworkAgent != null) {
            wifiNetworkAgent.sendLinkProperties(this.mLinkProperties);
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void sendRssiChangeBroadcast(int newRssi) {
        try {
            this.mBatteryStats.noteWifiRssiChanged(newRssi);
        } catch (RemoteException e) {
        }
        StatsLog.write(38, WifiManager.calculateSignalLevel(newRssi, 5));
        Intent intent = new Intent("android.net.wifi.RSSI_CHANGED");
        intent.addFlags(67108864);
        intent.putExtra("newRssi", newRssi);
        this.mContext.sendBroadcastAsUser(intent, UserHandle.ALL, "android.permission.ACCESS_WIFI_STATE");
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void sendNetworkStateChangeBroadcast(String bssid) {
        Intent intent = new Intent("android.net.wifi.STATE_CHANGE");
        intent.addFlags(67108864);
        NetworkInfo networkInfo = new NetworkInfo(this.mNetworkInfo);
        networkInfo.setExtraInfo(null);
        intent.putExtra("networkInfo", networkInfo);
        this.mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
        if ("VZW".equals(SemCscFeature.getInstance().getString(CscFeatureTagCOMMON.TAG_CSCFEATURE_COMMON_CONFIGIMPLICITBROADCASTS))) {
            Intent cloneIntent = (Intent) intent.clone();
            cloneIntent.setPackage("com.verizon.mips.services");
            this.mContext.sendBroadcastAsUser(cloneIntent, UserHandle.ALL);
        }
        if (SemFloatingFeature.getInstance().getBoolean("SEC_FLOATING_FEATURE_WLAN_SUPPORT_SECURE_WIFI")) {
            PackageManager pm = this.mContext.getPackageManager();
            if (pm.checkSignatures("android", "com.samsung.android.fast") == 0) {
                try {
                    pm.getPackageInfo("com.samsung.android.fast", 0);
                    Intent cloneIntent2 = (Intent) intent.clone();
                    cloneIntent2.setPackage("com.samsung.android.fast");
                    this.mContext.sendBroadcastAsUser(cloneIntent2, UserHandle.ALL);
                } catch (PackageManager.NameNotFoundException e) {
                }
            }
        }
        if ("TencentSecurityWiFi".equals(CONFIG_SECURE_SVC_INTEGRATION)) {
            try {
                this.mContext.getPackageManager().getPackageInfo("com.samsung.android.tencentwifisecurity", 0);
                Intent cloneIntent3 = (Intent) intent.clone();
                cloneIntent3.setPackage("com.samsung.android.tencentwifisecurity");
                this.mContext.sendBroadcastAsUser(cloneIntent3, UserHandle.ALL);
            } catch (PackageManager.NameNotFoundException e2) {
            }
        }
    }

    private void sendLinkConfigurationChangedBroadcast() {
        Intent intent = new Intent("android.net.wifi.LINK_CONFIGURATION_CHANGED");
        intent.addFlags(67108864);
        intent.putExtra("linkProperties", new LinkProperties(this.mLinkProperties));
        this.mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
    }

    private void sendSupplicantConnectionChangedBroadcast(boolean connected) {
        Intent intent = new Intent("android.net.wifi.supplicant.CONNECTION_CHANGE");
        intent.addFlags(67108864);
        intent.putExtra("connected", connected);
        this.mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean setNetworkDetailedState(NetworkInfo.DetailedState state) {
        boolean hidden = false;
        if (this.mIsAutoRoaming) {
            hidden = true;
        }
        if (this.mVerboseLoggingEnabled) {
            log("setDetailed state, old =" + this.mNetworkInfo.getDetailedState() + " and new state=" + state + " hidden=" + hidden);
        }
        if (OpBrandingLoader.Vendor.VZW == mOpBranding && this.mSemWifiHiddenNetworkTracker != null && state == NetworkInfo.DetailedState.CONNECTING) {
            this.mSemWifiHiddenNetworkTracker.stopTracking();
        }
        if (hidden || state == this.mNetworkInfo.getDetailedState()) {
            return false;
        }
        this.mNetworkInfo.setDetailedState(state, null, null);
        WifiNetworkAgent wifiNetworkAgent = this.mNetworkAgent;
        if (wifiNetworkAgent != null) {
            wifiNetworkAgent.sendNetworkInfo(this.mNetworkInfo);
        }
        sendNetworkStateChangeBroadcast(null);
        return true;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private NetworkInfo.DetailedState getNetworkDetailedState() {
        return this.mNetworkInfo.getDetailedState();
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private SupplicantState handleSupplicantStateChange(Message message) {
        StateChangeResult stateChangeResult = (StateChangeResult) message.obj;
        SupplicantState state = stateChangeResult.state;
        this.mWifiScoreCard.noteSupplicantStateChanging(this.mWifiInfo, state);
        this.mWifiInfo.setSupplicantState(state);
        if (SupplicantState.isConnecting(state)) {
            this.mWifiInfo.setNetworkId(stateChangeResult.networkId);
            this.mWifiInfo.setBSSID(stateChangeResult.BSSID);
            this.mWifiInfo.setSSID(stateChangeResult.wifiSsid);
        } else {
            this.mWifiInfo.setNetworkId(-1);
            this.mWifiInfo.setBSSID(null);
            this.mWifiInfo.setSSID(null);
        }
        updateL2KeyAndGroupHint();
        updateCapabilities();
        WifiConfiguration config = getCurrentWifiConfiguration();
        if (config != null) {
            this.mWifiInfo.setEphemeral(config.ephemeral);
            this.mWifiInfo.setTrusted(config.trusted);
            this.mWifiInfo.setOsuAp(config.osu);
            if (config.fromWifiNetworkSpecifier || config.fromWifiNetworkSuggestion) {
                this.mWifiInfo.setNetworkSuggestionOrSpecifierPackageName(config.creatorName);
            }
            ScanDetailCache scanDetailCache = this.mWifiConfigManager.getScanDetailCacheForNetwork(config.networkId);
            if (scanDetailCache != null) {
                ScanDetail scanDetail = scanDetailCache.getScanDetail(stateChangeResult.BSSID);
                if (scanDetail != null) {
                    updateWifiInfoForVendors(scanDetail.getScanResult());
                    this.mWifiInfo.setFrequency(scanDetail.getScanResult().frequency);
                    NetworkDetail networkDetail = scanDetail.getNetworkDetail();
                    if (networkDetail != null && networkDetail.getAnt() == NetworkDetail.Ant.ChargeablePublic) {
                        Log.d(TAG, "setMeteredHint by ChargeablePublic");
                        this.mWifiInfo.setMeteredHint(true);
                    }
                    this.mWifiInfo.setWifiMode(scanDetail.getScanResult().wifiMode);
                } else {
                    Log.d(TAG, "can't update vendor infos, bssid: " + stateChangeResult.BSSID);
                }
            }
        }
        this.mSupplicantStateTracker.sendMessage(Message.obtain(message));
        this.mWifiScoreCard.noteSupplicantStateChanged(this.mWifiInfo);
        return state;
    }

    private void updateL2KeyAndGroupHint() {
        if (this.mIpClient != null) {
            Pair<String, String> p = this.mWifiScoreCard.getL2KeyAndGroupHint(this.mWifiInfo);
            if (p.equals(this.mLastL2KeyAndGroupHint)) {
                return;
            }
            if (this.mIpClient.setL2KeyAndGroupHint((String) p.first, (String) p.second)) {
                this.mLastL2KeyAndGroupHint = p;
            } else {
                this.mLastL2KeyAndGroupHint = null;
            }
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void handleNetworkDisconnect() {
        if (this.mVerboseLoggingEnabled) {
            log("handleNetworkDisconnect: Stopping DHCP and clearing IP stack:" + Thread.currentThread().getStackTrace()[2].getMethodName() + " - " + Thread.currentThread().getStackTrace()[3].getMethodName() + " - " + Thread.currentThread().getStackTrace()[4].getMethodName() + " - " + Thread.currentThread().getStackTrace()[5].getMethodName());
        }
        WifiConfiguration wifiConfig = getCurrentWifiConfiguration();
        if (wifiConfig != null) {
            ScanResultMatchInfo.fromWifiConfiguration(wifiConfig);
            this.mWifiNetworkSuggestionsManager.handleDisconnect(wifiConfig, getCurrentBSSID());
        }
        stopRssiMonitoringOffload();
        clearTargetBssid("handleNetworkDisconnect");
        stopIpClient();
        this.mWifiScoreReport.reset();
        this.mWifiInfo.reset();
        this.mIsAutoRoaming = false;
        setNetworkDetailedState(NetworkInfo.DetailedState.DISCONNECTED);
        synchronized (this.mNetworkAgentLock) {
            if (this.mNetworkAgent != null) {
                checkAndResetMtu();
                this.mNetworkAgent.sendNetworkInfo(this.mNetworkInfo);
                this.mNetworkAgent = null;
            }
        }
        clearLinkProperties();
        sendNetworkStateChangeBroadcast(this.mLastBssid);
        this.mLastBssid = null;
        this.mLastLinkLayerStats = null;
        registerDisconnected();
        this.mLastNetworkId = -1;
        this.mWifiScoreCard.resetConnectionState();
        updateL2KeyAndGroupHint();
    }

    /* access modifiers changed from: package-private */
    public void handlePreDhcpSetup() {
        if (!this.mBluetoothConnectionActive) {
            this.mWifiNative.setBluetoothCoexistenceMode(this.mInterfaceName, 1);
        }
        setSuspendOptimizationsNative(1, false);
        setPowerSave(false);
        getWifiLinkLayerStats();
        if (this.mWifiP2pChannel != null) {
            Message msg = new Message();
            msg.what = WifiP2pServiceImpl.BLOCK_DISCOVERY;
            msg.arg1 = 1;
            msg.arg2 = CMD_PRE_DHCP_ACTION_COMPLETE;
            msg.obj = this;
            this.mWifiP2pChannel.sendMessage(msg);
        } else {
            sendMessage(CMD_PRE_DHCP_ACTION_COMPLETE);
        }
        handleWifiNetworkCallbacks(8);
    }

    /* access modifiers changed from: package-private */
    public void handlePostDhcpSetup() {
        setSuspendOptimizationsNative(1, true);
        setPowerSave(true);
        p2pSendMessage(WifiP2pServiceImpl.BLOCK_DISCOVERY, 0);
        if (!this.mHandleIfaceIsUp) {
            Log.w(TAG, "handlePostDhcpSetup, mHandleIfaceIsUp is false. skip setBluetoothCoexistenceMode");
            return;
        }
        this.mWifiNative.setBluetoothCoexistenceMode(this.mInterfaceName, 2);
        handleWifiNetworkCallbacks(9);
    }

    public boolean setPowerSave(boolean ps) {
        if (this.mInterfaceName != null) {
            if (this.mVerboseLoggingEnabled) {
                Log.d(TAG, "Setting power save for: " + this.mInterfaceName + " to: " + ps);
            }
            if (!this.mHandleIfaceIsUp) {
                Log.w(TAG, "setPowerSave, mHandleIfaceIsUp is false");
                return false;
            }
            this.mWifiNative.setPowerSave(this.mInterfaceName, ps);
            return true;
        }
        Log.e(TAG, "Failed to setPowerSave, interfaceName is null");
        return false;
    }

    public boolean setLowLatencyMode(boolean enabled) {
        if (this.mVerboseLoggingEnabled) {
            Log.d(TAG, "Setting low latency mode to " + enabled);
        }
        if (this.mWifiNative.setLowLatencyMode(enabled)) {
            return true;
        }
        Log.e(TAG, "Failed to setLowLatencyMode");
        return false;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void reportConnectionAttemptStart(WifiConfiguration config, String targetBSSID, int roamType) {
        this.mWifiMetrics.startConnectionEvent(config, targetBSSID, roamType);
        this.mWifiDiagnostics.reportConnectionEvent((byte) 0);
        this.mWrongPasswordNotifier.onNewConnectionAttempt();
        removeMessages(CMD_DIAGS_CONNECT_TIMEOUT);
        sendMessageDelayed(CMD_DIAGS_CONNECT_TIMEOUT, 60000);
    }

    private void handleConnectionAttemptEndForDiagnostics(int level2FailureCode) {
        if (level2FailureCode != 1 && level2FailureCode != 5) {
            removeMessages(CMD_DIAGS_CONNECT_TIMEOUT);
            this.mWifiDiagnostics.reportConnectionEvent((byte) 2);
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void reportConnectionAttemptEnd(int level2FailureCode, int connectivityFailureCode, int level2FailureReason) {
        if (level2FailureCode != 1) {
            this.mWifiScoreCard.noteConnectionFailure(this.mWifiInfo, level2FailureCode, connectivityFailureCode);
        }
        WifiConfiguration configuration = getCurrentWifiConfiguration();
        if (configuration == null) {
            configuration = getTargetWifiConfiguration();
        }
        this.mWifiMetrics.endConnectionEvent(level2FailureCode, connectivityFailureCode, level2FailureReason);
        this.mWifiConnectivityManager.handleConnectionAttemptEnded(level2FailureCode);
        if (configuration != null) {
            this.mNetworkFactory.handleConnectionAttemptEnded(level2FailureCode, configuration);
            this.mWifiNetworkSuggestionsManager.handleConnectionAttemptEnded(level2FailureCode, configuration, getCurrentBSSID());
        }
        handleConnectionAttemptEndForDiagnostics(level2FailureCode);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void handleIPv4Success(DhcpResults dhcpResults) {
        Inet4Address addr;
        if (this.mVerboseLoggingEnabled) {
            logd("handleIPv4Success <" + dhcpResults.toString() + ">");
            StringBuilder sb = new StringBuilder();
            sb.append("link address ");
            sb.append(dhcpResults.ipAddress);
            logd(sb.toString());
        }
        synchronized (this.mDhcpResultsLock) {
            this.mDhcpResults = dhcpResults;
            ReportUtil.updateDhcpResults(this.mDhcpResults);
            addr = (Inet4Address) dhcpResults.ipAddress.getAddress();
        }
        if (this.mIsAutoRoaming && this.mWifiInfo.getIpAddress() != NetworkUtils.inetAddressToInt(addr)) {
            logd("handleIPv4Success, roaming and address changed" + this.mWifiInfo + " got: " + addr);
        }
        this.mWifiInfo.setInetAddress(addr);
        WifiConfiguration config = getCurrentWifiConfiguration();
        if (config != null) {
            this.mWifiInfo.setEphemeral(config.ephemeral);
            this.mWifiInfo.setTrusted(config.trusted);
        }
        if (dhcpResults.hasMeteredHint()) {
            this.mWifiInfo.setMeteredHint(true);
        }
        updateCapabilities(config);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void handleSuccessfulIpConfiguration() {
        this.mLastSignalLevel = -1;
        WifiConfiguration c = getCurrentWifiConfiguration();
        if (c != null) {
            c.getNetworkSelectionStatus().clearDisableReasonCounter(4);
            updateCapabilities(c);
        }
        this.mWifiScoreCard.noteIpConfiguration(this.mWifiInfo);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void handleIPv4Failure() {
        this.mWifiDiagnostics.triggerBugReportDataCapture(4);
        if (this.mVerboseLoggingEnabled) {
            int count = -1;
            WifiConfiguration config = getCurrentWifiConfiguration();
            if (config != null) {
                count = config.getNetworkSelectionStatus().getDisableReasonCounter(4);
            }
            log("DHCP failure count=" + count);
        }
        reportConnectionAttemptEnd(10, 2, 0);
        synchronized (this.mDhcpResultsLock) {
            if (this.mDhcpResults != null) {
                this.mDhcpResults.clear();
            }
        }
        if (this.mVerboseLoggingEnabled) {
            logd("handleIPv4Failure");
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void handleIpConfigurationLost() {
        this.mWifiInfo.setInetAddress(null);
        this.mWifiInfo.setMeteredHint(false);
        this.mWifiConfigManager.updateNetworkSelectionStatus(this.mLastNetworkId, 4);
        this.mWifiNative.disconnect(this.mInterfaceName);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void handleIpReachabilityLost() {
        this.mWifiScoreCard.noteIpReachabilityLost(this.mWifiInfo);
        this.mWifiInfo.setInetAddress(null);
        this.mWifiInfo.setMeteredHint(false);
        this.mWifiNative.disconnect(this.mInterfaceName);
    }

    private String macAddressFromRoute(String ipAddress) {
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
                    if (ipAddress.equals(ip)) {
                        macAddress = mac;
                        break;
                    }
                }
            }
            if (macAddress == null) {
                loge("Did not find remoteAddress {" + ipAddress + "} in /proc/net/arp");
            }
            try {
                reader2.close();
            } catch (IOException e) {
            }
        } catch (FileNotFoundException e2) {
            loge("Could not open /proc/net/arp to lookup mac address");
            if (0 != 0) {
                reader.close();
            }
        } catch (IOException e3) {
            loge("Could not read /proc/net/arp to lookup mac address");
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
        return macAddress;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean isPermanentWrongPasswordFailure(WifiConfiguration network, int reasonCode) {
        if (reasonCode != 2) {
            return false;
        }
        if (network == null || !network.getNetworkSelectionStatus().getHasEverConnected()) {
            return true;
        }
        return false;
    }

    /* access modifiers changed from: package-private */
    public void registerNetworkFactory() {
        if (checkAndSetConnectivityInstance()) {
            this.mNetworkFactory.register();
            this.mUntrustedNetworkFactory.register();
        }
    }

    public void sendBroadcastIssueTrackerSysDump(int reason) {
        Log.i(TAG, "sendBroadcastIssueTrackerSysDump reason : " + reason);
        if (mIssueTrackerOn) {
            Log.i(TAG, "sendBroadcastIssueTrackerSysDump mIssueTrackerOn true");
            Intent issueTrackerIntent = new Intent("com.sec.android.ISSUE_TRACKER_ACTION");
            issueTrackerIntent.putExtra("ERRPKG", "WifiStateMachine");
            if (reason == 0) {
                issueTrackerIntent.putExtra("ERRCODE", -110);
                issueTrackerIntent.putExtra("ERRNAME", "HANGED");
                issueTrackerIntent.putExtra("ERRMSG", "Wi-Fi chip HANGED");
            } else if (reason == 1) {
                issueTrackerIntent.putExtra("ERRCODE", -110);
                issueTrackerIntent.putExtra("ERRNAME", "UNWANTED");
                issueTrackerIntent.putExtra("ERRMSG", "Wi-Fi UNWANTED happend");
            } else if (reason == 2) {
                issueTrackerIntent.putExtra("ERRCODE", -110);
                issueTrackerIntent.putExtra("ERRNAME", WifiBigDataLogManager.FEATURE_DISC);
                issueTrackerIntent.putExtra("ERRMSG", "Wi-Fi DISC happend");
            }
            this.mContext.sendBroadcastAsUser(issueTrackerIntent, UserHandle.ALL);
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void getAdditionalWifiServiceInterfaces() {
        WifiP2pServiceImpl wifiP2pServiceImpl;
        if (this.mP2pSupported && (wifiP2pServiceImpl = IWifiP2pManager.Stub.asInterface(this.mFacade.getService("wifip2p"))) != null) {
            this.mWifiP2pChannel = new AsyncChannel();
            this.mWifiP2pChannel.connect(this.mContext, getHandler(), wifiP2pServiceImpl.getP2pStateMachineMessenger());
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void configureRandomizedMacAddress(WifiConfiguration config) {
        if (config == null) {
            Log.e(TAG, "No config to change MAC address to");
            return;
        }
        MacAddress currentMac = MacAddress.fromString(this.mWifiNative.getMacAddress(this.mInterfaceName));
        MacAddress newMac = config.getOrCreateRandomizedMacAddress();
        this.mWifiConfigManager.setNetworkRandomizedMacAddress(config.networkId, newMac);
        if (!WifiConfiguration.isValidMacAddressForRandomization(newMac)) {
            Log.wtf(TAG, "Config generated an invalid MAC address");
        } else if (currentMac.equals(newMac)) {
            Log.d(TAG, "No changes in MAC address");
        } else {
            this.mWifiMetrics.logStaEvent(17, config);
            boolean setMacSuccess = this.mWifiNative.setMacAddress(this.mInterfaceName, newMac);
            if (DBG) {
                Log.d(TAG, "ConnectedMacRandomization SSID(" + config.getPrintableSsid() + "). setMacAddress(" + newMac.toString() + ") from " + currentMac.toString() + " = " + setMacSuccess);
            }
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void setCurrentMacToFactoryMac(WifiConfiguration config) {
        MacAddress factoryMac = this.mWifiNative.getFactoryMacAddress(this.mInterfaceName);
        if (factoryMac == null) {
            Log.e(TAG, "Fail to set factory MAC address. Factory MAC is null.");
        } else if (TextUtils.equals(this.mWifiNative.getMacAddress(this.mInterfaceName), factoryMac.toString())) {
        } else {
            if (this.mWifiNative.setMacAddress(this.mInterfaceName, factoryMac)) {
                this.mWifiMetrics.logStaEvent(17, config);
                return;
            }
            Log.e(TAG, "Failed to set MAC address to '" + factoryMac.toString() + "'");
        }
    }

    public boolean isConnectedMacRandomizationEnabled() {
        return this.mConnectedMacRandomzationSupported;
    }

    public void failureDetected(int reason) {
        this.mWifiInjector.getSelfRecovery().trigger(2);
        SemWifiIssueDetector semWifiIssueDetector = this.mIssueDetector;
        if (semWifiIssueDetector != null) {
            semWifiIssueDetector.captureBugReport(17, ReportUtil.getReportDataForHidlDeath(2));
        }
    }

    class DefaultState extends State {
        DefaultState() {
        }

        public void enter() {
            ClientModeImpl.this.sendMessageDelayed(ClientModeImpl.CMD_24HOURS_PASSED_AFTER_BOOT, ClientModeImpl.DBG_PRODUCT_DEV ? WifiDiagnostics.MIN_DUMP_TIME_WINDOW_MILLIS : WifiConfigManager.DELETED_EPHEMERAL_SSID_EXPIRY_MS);
        }

        public boolean processMessage(Message message) {
            int i = -1;
            int i2 = -1;
            int removeResult = -1;
            int addResult = -1;
            boolean z = false;
            boolean z2 = false;
            boolean z3 = false;
            boolean z4 = false;
            int i3 = 0;
            boolean screenOn = false;
            boolean z5 = false;
            boolean disableOthers = false;
            switch (message.what) {
                case 0:
                    Log.wtf(ClientModeImpl.TAG, "Error! empty message encountered");
                    break;
                case 69632:
                    if (((AsyncChannel) message.obj) == ClientModeImpl.this.mWifiP2pChannel) {
                        if (message.arg1 != 0) {
                            ClientModeImpl.this.loge("WifiP2pService connection failure, error=" + message.arg1);
                            break;
                        } else {
                            ClientModeImpl.this.p2pSendMessage(69633);
                            break;
                        }
                    } else {
                        ClientModeImpl.this.loge("got HALF_CONNECTED for unknown channel");
                        break;
                    }
                case 69636:
                    if (((AsyncChannel) message.obj) == ClientModeImpl.this.mWifiP2pChannel) {
                        ClientModeImpl.this.loge("WifiP2pService channel lost, message.arg1 =" + message.arg1);
                        break;
                    }
                    break;
                case ClientModeImpl.CMD_BLUETOOTH_ADAPTER_STATE_CHANGE /*{ENCODED_INT: 131103}*/:
                    ClientModeImpl clientModeImpl = ClientModeImpl.this;
                    if (message.arg1 != 0) {
                        z = true;
                    }
                    clientModeImpl.mBluetoothConnectionActive = z;
                    if (ClientModeImpl.this.mWifiConnectivityManager != null) {
                        ClientModeImpl.this.mWifiConnectivityManager.setBluetoothConnected(ClientModeImpl.this.mBluetoothConnectionActive);
                    }
                    ClientModeImpl.this.mBigDataManager.addOrUpdateValue(10, ClientModeImpl.this.mBluetoothConnectionActive ? 1 : 0);
                    break;
                case ClientModeImpl.CMD_ADD_OR_UPDATE_NETWORK /*{ENCODED_INT: 131124}*/:
                    int from = message.arg1;
                    WifiConfiguration config = (WifiConfiguration) message.obj;
                    if (config.networkId == -1) {
                        config.priority = ClientModeImpl.this.mWifiConfigManager.increaseAndGetPriority();
                    }
                    ClientModeImpl.this.mWifiConfigManager.updateBssidWhitelist(config, ClientModeImpl.this.mScanRequestProxy.getScanResults());
                    NetworkUpdateResult result = ClientModeImpl.this.mWifiConfigManager.addOrUpdateNetwork(config, message.sendingUid, from, null);
                    if (!result.isSuccess()) {
                        ClientModeImpl.this.mMessageHandlingStatus = -2;
                    }
                    ClientModeImpl.this.replyToMessage((ClientModeImpl) message, (Message) message.what, result.getNetworkId());
                    break;
                case ClientModeImpl.CMD_REMOVE_NETWORK /*{ENCODED_INT: 131125}*/:
                    ClientModeImpl.this.deleteNetworkConfigAndSendReply(message, false);
                    break;
                case ClientModeImpl.CMD_ENABLE_NETWORK /*{ENCODED_INT: 131126}*/:
                    if (message.arg2 == 1) {
                        disableOthers = true;
                    }
                    boolean ok = ClientModeImpl.this.mWifiConfigManager.enableNetwork(message.arg1, disableOthers, message.sendingUid);
                    if (!ok) {
                        ClientModeImpl.this.mMessageHandlingStatus = -2;
                    }
                    ClientModeImpl clientModeImpl2 = ClientModeImpl.this;
                    int i4 = message.what;
                    if (ok) {
                        i = 1;
                    }
                    clientModeImpl2.replyToMessage((ClientModeImpl) message, (Message) i4, i);
                    break;
                case ClientModeImpl.CMD_GET_CONFIGURED_NETWORKS /*{ENCODED_INT: 131131}*/:
                    if (-1000 != ((Integer) message.obj).intValue()) {
                        ClientModeImpl.this.replyToMessage((ClientModeImpl) message, (Message) message.what, (int) ClientModeImpl.this.mWifiConfigManager.getSavedNetworks(message.arg2));
                        break;
                    } else {
                        ClientModeImpl.this.replyToMessage((ClientModeImpl) message, (Message) message.what, (int) ClientModeImpl.this.mWifiConfigManager.getConfiguredNetworks());
                        break;
                    }
                case ClientModeImpl.CMD_GET_SUPPORTED_FEATURES /*{ENCODED_INT: 131133}*/:
                    ClientModeImpl.this.replyToMessage((ClientModeImpl) message, (Message) message.what, (int) Long.valueOf(ClientModeImpl.this.mWifiNative.getSupportedFeatureSet(ClientModeImpl.this.mInterfaceName)));
                    break;
                case ClientModeImpl.CMD_GET_PRIVILEGED_CONFIGURED_NETWORKS /*{ENCODED_INT: 131134}*/:
                    ClientModeImpl.this.replyToMessage((ClientModeImpl) message, (Message) message.what, (int) ClientModeImpl.this.mWifiConfigManager.getConfiguredNetworksWithPasswords());
                    break;
                case ClientModeImpl.CMD_GET_LINK_LAYER_STATS /*{ENCODED_INT: 131135}*/:
                    ClientModeImpl.this.replyToMessage((ClientModeImpl) message, (Message) message.what, (int) null);
                    break;
                case ClientModeImpl.CMD_SET_OPERATIONAL_MODE /*{ENCODED_INT: 131144}*/:
                    break;
                case ClientModeImpl.CMD_DISCONNECT /*{ENCODED_INT: 131145}*/:
                case ClientModeImpl.CMD_RECONNECT /*{ENCODED_INT: 131146}*/:
                case ClientModeImpl.CMD_REASSOCIATE /*{ENCODED_INT: 131147}*/:
                case ClientModeImpl.CMD_RSSI_POLL /*{ENCODED_INT: 131155}*/:
                case ClientModeImpl.CMD_ONESHOT_RSSI_POLL /*{ENCODED_INT: 131156}*/:
                case ClientModeImpl.CMD_ROAM_WATCHDOG_TIMER /*{ENCODED_INT: 131166}*/:
                case ClientModeImpl.CMD_DISCONNECTING_WATCHDOG_TIMER /*{ENCODED_INT: 131168}*/:
                case ClientModeImpl.CMD_DISABLE_EPHEMERAL_NETWORK /*{ENCODED_INT: 131170}*/:
                case ClientModeImpl.CMD_TARGET_BSSID /*{ENCODED_INT: 131213}*/:
                case ClientModeImpl.CMD_START_CONNECT /*{ENCODED_INT: 131215}*/:
                case ClientModeImpl.CMD_UNWANTED_NETWORK /*{ENCODED_INT: 131216}*/:
                case ClientModeImpl.CMD_START_ROAM /*{ENCODED_INT: 131217}*/:
                case ClientModeImpl.CMD_ASSOCIATED_BSSID /*{ENCODED_INT: 131219}*/:
                case ClientModeImpl.CMD_ENABLE_WIFI_CONNECTIVITY_MANAGER /*{ENCODED_INT: 131238}*/:
                case ClientModeImpl.CMD_REPLACE_PUBLIC_DNS /*{ENCODED_INT: 131286}*/:
                case ClientModeImpl.CMD_PRE_DHCP_ACTION /*{ENCODED_INT: 131327}*/:
                case ClientModeImpl.CMD_PRE_DHCP_ACTION_COMPLETE /*{ENCODED_INT: 131328}*/:
                case ClientModeImpl.CMD_POST_DHCP_ACTION /*{ENCODED_INT: 131329}*/:
                case ClientModeImpl.CMD_THREE_TIMES_SCAN_IN_IDLE /*{ENCODED_INT: 131381}*/:
                case ClientModeImpl.CMD_SCAN_RESULT_AVAILABLE /*{ENCODED_INT: 131584}*/:
                case ClientModeImpl.CMD_CHECK_ARP_RESULT /*{ENCODED_INT: 131622}*/:
                case ClientModeImpl.CMD_SEND_ARP /*{ENCODED_INT: 131623}*/:
                case WifiMonitor.NETWORK_CONNECTION_EVENT /*{ENCODED_INT: 147459}*/:
                case WifiMonitor.NETWORK_DISCONNECTION_EVENT /*{ENCODED_INT: 147460}*/:
                case WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT /*{ENCODED_INT: 147462}*/:
                case WifiMonitor.AUTHENTICATION_FAILURE_EVENT /*{ENCODED_INT: 147463}*/:
                case WifiMonitor.SUP_REQUEST_IDENTITY /*{ENCODED_INT: 147471}*/:
                case WifiMonitor.SUP_REQUEST_SIM_AUTH /*{ENCODED_INT: 147472}*/:
                case 147499:
                case WifiMonitor.BSSID_PRUNED_EVENT /*{ENCODED_INT: 147501}*/:
                    ClientModeImpl.this.mMessageHandlingStatus = ClientModeImpl.MESSAGE_HANDLING_STATUS_DISCARD;
                    break;
                case ClientModeImpl.CMD_SET_HIGH_PERF_MODE /*{ENCODED_INT: 131149}*/:
                    if (message.arg1 != 1) {
                        ClientModeImpl.this.setSuspendOptimizations(2, true);
                        break;
                    } else {
                        ClientModeImpl.this.setSuspendOptimizations(2, false);
                        break;
                    }
                case ClientModeImpl.CMD_ENABLE_RSSI_POLL /*{ENCODED_INT: 131154}*/:
                    ClientModeImpl clientModeImpl3 = ClientModeImpl.this;
                    if (message.arg1 == 1) {
                        z5 = true;
                    }
                    clientModeImpl3.mEnableRssiPolling = z5;
                    break;
                case ClientModeImpl.CMD_SET_SUSPEND_OPT_ENABLED /*{ENCODED_INT: 131158}*/:
                    if (message.arg1 != 1) {
                        ClientModeImpl.this.setSuspendOptimizations(4, false);
                        break;
                    } else {
                        if (message.arg2 == 1) {
                            ClientModeImpl.this.mSuspendWakeLock.release();
                        }
                        ClientModeImpl.this.setSuspendOptimizations(4, true);
                        break;
                    }
                case ClientModeImpl.CMD_SCREEN_STATE_CHANGED /*{ENCODED_INT: 131167}*/:
                    if (message.arg1 != 0) {
                        screenOn = true;
                    }
                    if (ClientModeImpl.this.mScreenOn != screenOn) {
                        ClientModeImpl.this.handleScreenStateChanged(screenOn);
                        break;
                    }
                    break;
                case ClientModeImpl.CMD_REMOVE_APP_CONFIGURATIONS /*{ENCODED_INT: 131169}*/:
                    ClientModeImpl.this.deferMessage(message);
                    break;
                case ClientModeImpl.CMD_RESET_SIM_NETWORKS /*{ENCODED_INT: 131173}*/:
                    ClientModeImpl.this.mMessageHandlingStatus = ClientModeImpl.MESSAGE_HANDLING_STATUS_DEFERRED;
                    ClientModeImpl.this.deferMessage(message);
                    break;
                case ClientModeImpl.CMD_QUERY_OSU_ICON /*{ENCODED_INT: 131176}*/:
                case ClientModeImpl.CMD_MATCH_PROVIDER_NETWORK /*{ENCODED_INT: 131177}*/:
                    ClientModeImpl.this.replyToMessage(message, message.what);
                    break;
                case ClientModeImpl.CMD_ADD_OR_UPDATE_PASSPOINT_CONFIG /*{ENCODED_INT: 131178}*/:
                    Bundle bundle = (Bundle) message.obj;
                    if (ClientModeImpl.this.mPasspointManager.addOrUpdateProvider((PasspointConfiguration) bundle.getParcelable(ClientModeImpl.EXTRA_PASSPOINT_CONFIGURATION), bundle.getInt(ClientModeImpl.EXTRA_UID), bundle.getString(ClientModeImpl.EXTRA_PACKAGE_NAME))) {
                        addResult = 1;
                    }
                    ClientModeImpl.this.replyToMessage((ClientModeImpl) message, (Message) message.what, addResult);
                    break;
                case ClientModeImpl.CMD_REMOVE_PASSPOINT_CONFIG /*{ENCODED_INT: 131179}*/:
                    String fqdn = (String) message.obj;
                    if (ClientModeImpl.this.mPasspointManager.removeProvider((String) message.obj)) {
                        removeResult = 1;
                    }
                    Iterator<WifiConfiguration> it = ClientModeImpl.this.mWifiConfigManager.getConfiguredNetworks().iterator();
                    while (true) {
                        if (it.hasNext()) {
                            WifiConfiguration network = it.next();
                            if (network.isPasspoint() && fqdn.equals(network.FQDN)) {
                                ClientModeImpl.this.mWifiConfigManager.removeNetwork(network.networkId, network.creatorUid);
                            }
                        }
                    }
                    ClientModeImpl.this.replyToMessage((ClientModeImpl) message, (Message) message.what, removeResult);
                    break;
                case ClientModeImpl.CMD_GET_PASSPOINT_CONFIGS /*{ENCODED_INT: 131180}*/:
                    ClientModeImpl.this.replyToMessage((ClientModeImpl) message, (Message) message.what, (int) ClientModeImpl.this.mPasspointManager.getProviderConfigs());
                    break;
                case ClientModeImpl.CMD_GET_MATCHING_OSU_PROVIDERS /*{ENCODED_INT: 131181}*/:
                    ClientModeImpl.this.replyToMessage((ClientModeImpl) message, (Message) message.what, (int) new HashMap());
                    break;
                case ClientModeImpl.CMD_GET_MATCHING_PASSPOINT_CONFIGS_FOR_OSU_PROVIDERS /*{ENCODED_INT: 131182}*/:
                    ClientModeImpl.this.replyToMessage((ClientModeImpl) message, (Message) message.what, (int) new HashMap());
                    break;
                case ClientModeImpl.CMD_GET_WIFI_CONFIGS_FOR_PASSPOINT_PROFILES /*{ENCODED_INT: 131184}*/:
                    ClientModeImpl.this.replyToMessage((ClientModeImpl) message, (Message) message.what, (int) new ArrayList());
                    break;
                case ClientModeImpl.CMD_BOOT_COMPLETED /*{ENCODED_INT: 131206}*/:
                    ClientModeImpl.this.mIsBootCompleted = true;
                    ClientModeImpl.this.initializeWifiChipInfo();
                    ClientModeImpl.this.getAdditionalWifiServiceInterfaces();
                    new MemoryStoreImpl(ClientModeImpl.this.mContext, ClientModeImpl.this.mWifiInjector, ClientModeImpl.this.mWifiScoreCard).start();
                    if (!ClientModeImpl.this.mWifiConfigManager.loadFromStore(false)) {
                        Log.e(ClientModeImpl.TAG, "Failed to load from config store, retry later");
                        ClientModeImpl.this.sendMessageDelayed(ClientModeImpl.CMD_RELOAD_CONFIG_STORE_FILE, 1, 0, 1000);
                    }
                    ClientModeImpl.this.registerNetworkFactory();
                    ClientModeImpl.this.resetFwLogFolder();
                    ClientModeImpl.this.mWifiConfigManager.forcinglyEnableAllNetworks(1000);
                    break;
                case ClientModeImpl.CMD_INITIALIZE /*{ENCODED_INT: 131207}*/:
                    boolean ok2 = ClientModeImpl.this.mWifiNative.initialize();
                    ClientModeImpl.this.initializeWifiChipInfo();
                    ClientModeImpl clientModeImpl4 = ClientModeImpl.this;
                    int i5 = message.what;
                    if (ok2) {
                        i2 = 1;
                    }
                    clientModeImpl4.replyToMessage((ClientModeImpl) message, (Message) i5, i2);
                    break;
                case ClientModeImpl.CMD_IP_CONFIGURATION_SUCCESSFUL /*{ENCODED_INT: 131210}*/:
                case ClientModeImpl.CMD_IP_CONFIGURATION_LOST /*{ENCODED_INT: 131211}*/:
                case ClientModeImpl.CMD_IP_REACHABILITY_LOST /*{ENCODED_INT: 131221}*/:
                    ClientModeImpl.this.mMessageHandlingStatus = ClientModeImpl.MESSAGE_HANDLING_STATUS_DISCARD;
                    break;
                case ClientModeImpl.CMD_UPDATE_LINKPROPERTIES /*{ENCODED_INT: 131212}*/:
                    ClientModeImpl.this.updateLinkProperties((LinkProperties) message.obj);
                    break;
                case ClientModeImpl.CMD_REMOVE_USER_CONFIGURATIONS /*{ENCODED_INT: 131224}*/:
                    ClientModeImpl.this.deferMessage(message);
                    break;
                case ClientModeImpl.CMD_START_IP_PACKET_OFFLOAD /*{ENCODED_INT: 131232}*/:
                case ClientModeImpl.CMD_STOP_IP_PACKET_OFFLOAD /*{ENCODED_INT: 131233}*/:
                case ClientModeImpl.CMD_ADD_KEEPALIVE_PACKET_FILTER_TO_APF /*{ENCODED_INT: 131281}*/:
                case ClientModeImpl.CMD_REMOVE_KEEPALIVE_PACKET_FILTER_FROM_APF /*{ENCODED_INT: 131282}*/:
                    if (ClientModeImpl.this.mNetworkAgent != null) {
                        ClientModeImpl.this.mNetworkAgent.onSocketKeepaliveEvent(message.arg1, -20);
                        break;
                    }
                    break;
                case ClientModeImpl.CMD_START_RSSI_MONITORING_OFFLOAD /*{ENCODED_INT: 131234}*/:
                    ClientModeImpl.this.mMessageHandlingStatus = ClientModeImpl.MESSAGE_HANDLING_STATUS_DISCARD;
                    break;
                case ClientModeImpl.CMD_STOP_RSSI_MONITORING_OFFLOAD /*{ENCODED_INT: 131235}*/:
                    ClientModeImpl.this.mMessageHandlingStatus = ClientModeImpl.MESSAGE_HANDLING_STATUS_DISCARD;
                    break;
                case ClientModeImpl.CMD_GET_ALL_MATCHING_FQDNS_FOR_SCAN_RESULTS /*{ENCODED_INT: 131240}*/:
                    ClientModeImpl.this.replyToMessage((ClientModeImpl) message, (Message) message.what, (int) new HashMap());
                    break;
                case ClientModeImpl.CMD_INSTALL_PACKET_FILTER /*{ENCODED_INT: 131274}*/:
                    ClientModeImpl.this.mWifiNative.installPacketFilter(ClientModeImpl.this.mInterfaceName, (byte[]) message.obj);
                    break;
                case ClientModeImpl.CMD_SET_FALLBACK_PACKET_FILTERING /*{ENCODED_INT: 131275}*/:
                    if (!((Boolean) message.obj).booleanValue()) {
                        ClientModeImpl.this.mWifiNative.stopFilteringMulticastV4Packets(ClientModeImpl.this.mInterfaceName);
                        break;
                    } else {
                        ClientModeImpl.this.mWifiNative.startFilteringMulticastV4Packets(ClientModeImpl.this.mInterfaceName);
                        break;
                    }
                case ClientModeImpl.CMD_USER_SWITCH /*{ENCODED_INT: 131277}*/:
                    Set<Integer> removedNetworkIds = ClientModeImpl.this.mWifiConfigManager.handleUserSwitch(message.arg1);
                    if (removedNetworkIds.contains(Integer.valueOf(ClientModeImpl.this.mTargetNetworkId)) || removedNetworkIds.contains(Integer.valueOf(ClientModeImpl.this.mLastNetworkId))) {
                        ClientModeImpl.this.disconnectCommand(0, 20);
                        break;
                    }
                case ClientModeImpl.CMD_USER_UNLOCK /*{ENCODED_INT: 131278}*/:
                    ClientModeImpl.this.mWifiConfigManager.handleUserUnlock(message.arg1);
                    break;
                case ClientModeImpl.CMD_USER_STOP /*{ENCODED_INT: 131279}*/:
                    ClientModeImpl.this.mWifiConfigManager.handleUserStop(message.arg1);
                    break;
                case ClientModeImpl.CMD_READ_PACKET_FILTER /*{ENCODED_INT: 131280}*/:
                    byte[] data = ClientModeImpl.this.mWifiNative.readPacketFilter(ClientModeImpl.this.mInterfaceName);
                    if (ClientModeImpl.this.mIpClient != null) {
                        ClientModeImpl.this.mIpClient.readPacketFilterComplete(data);
                        break;
                    }
                    break;
                case ClientModeImpl.CMD_IMS_CALL_ESTABLISHED /*{ENCODED_INT: 131315}*/:
                    if (ClientModeImpl.this.mIsImsCallEstablished != (message.arg1 == 1)) {
                        ClientModeImpl.this.mIsImsCallEstablished = message.arg1 == 1;
                        if (ClientModeImpl.this.mWifiConnectivityManager != null) {
                            WifiConnectivityManager wifiConnectivityManager = ClientModeImpl.this.mWifiConnectivityManager;
                            if (ClientModeImpl.this.mIsImsCallEstablished) {
                                i3 = 1;
                            }
                            wifiConnectivityManager.changeMaxPeriodicScanMode(i3);
                        }
                        if (ClientModeImpl.this.mWifiInjector.getWifiLowLatency() != null) {
                            ClientModeImpl.this.mWifiInjector.getWifiLowLatency().setImsCallingState(ClientModeImpl.this.mIsImsCallEstablished);
                            break;
                        }
                    }
                    break;
                case ClientModeImpl.CMD_AUTO_CONNECT_CARRIER_AP_ENABLED /*{ENCODED_INT: 131316}*/:
                    WifiConfigManager wifiConfigManager = ClientModeImpl.this.mWifiConfigManager;
                    if (message.arg1 == 1) {
                        z4 = true;
                    }
                    wifiConfigManager.setAutoConnectCarrierApEnabled(z4);
                    break;
                case ClientModeImpl.CMD_DIAGS_CONNECT_TIMEOUT /*{ENCODED_INT: 131324}*/:
                    ClientModeImpl.this.mWifiDiagnostics.reportConnectionEvent((byte) 3);
                    break;
                case ClientModeImpl.CMD_START_SUBSCRIPTION_PROVISIONING /*{ENCODED_INT: 131326}*/:
                case ClientModeImpl.CMD_UPDATE_CONFIG_LOCATION /*{ENCODED_INT: 131332}*/:
                    ClientModeImpl.this.replyToMessage((ClientModeImpl) message, (Message) message.what, 0);
                    break;
                case ClientModeImpl.CMD_SET_ADPS_MODE /*{ENCODED_INT: 131383}*/:
                    ClientModeImpl.this.mMessageHandlingStatus = ClientModeImpl.MESSAGE_HANDLING_STATUS_DISCARD;
                    break;
                case ClientModeImpl.CMD_FORCINGLY_ENABLE_ALL_NETWORKS /*{ENCODED_INT: 131402}*/:
                    ClientModeImpl.this.mWifiConfigManager.forcinglyEnableAllNetworks(message.sendingUid);
                    break;
                case ClientModeImpl.CMD_SEC_API_ASYNC /*{ENCODED_INT: 131573}*/:
                    if (!ClientModeImpl.this.processMessageOnDefaultStateForCallSECApiAsync(message)) {
                        ClientModeImpl.this.mMessageHandlingStatus = ClientModeImpl.MESSAGE_HANDLING_STATUS_DISCARD;
                        break;
                    }
                    break;
                case ClientModeImpl.CMD_SEC_API /*{ENCODED_INT: 131574}*/:
                    Log.d(ClientModeImpl.TAG, "DefaultState::Handling CMD_SEC_API");
                    ClientModeImpl.this.replyToMessage((ClientModeImpl) message, (Message) message.what, -1);
                    break;
                case ClientModeImpl.CMD_SEC_STRING_API /*{ENCODED_INT: 131575}*/:
                    ClientModeImpl.this.replyToMessage((ClientModeImpl) message, (Message) message.what, (int) ClientModeImpl.this.processMessageOnDefaultStateForCallSECStringApi(message));
                    break;
                case ClientModeImpl.CMD_SEC_LOGGING /*{ENCODED_INT: 131576}*/:
                case WifiMonitor.SUP_BIGDATA_EVENT /*{ENCODED_INT: 147469}*/:
                    Bundle args = (Bundle) message.obj;
                    String feature = null;
                    if (args != null) {
                        feature = args.getString("feature", null);
                    }
                    if (!ClientModeImpl.this.mIsShutdown) {
                        if (ClientModeImpl.this.mIsBootCompleted) {
                            if (feature == null) {
                                if (ClientModeImpl.DBG) {
                                    Log.e(ClientModeImpl.TAG, "CMD_SEC_LOGGING - feature is null");
                                    break;
                                }
                            } else {
                                if (WifiBigDataLogManager.ENABLE_SURVEY_MODE) {
                                    ClientModeImpl.this.mBigDataManager.insertLog(args);
                                } else if (ClientModeImpl.DBG) {
                                    Log.e(ClientModeImpl.TAG, "survey mode is disabled");
                                }
                                if (!WifiBigDataLogManager.FEATURE_DISC.equals(feature)) {
                                    if (!WifiBigDataLogManager.FEATURE_ON_OFF.equals(feature)) {
                                        if (!WifiBigDataLogManager.FEATURE_ASSOC.equals(feature)) {
                                            if (!WifiBigDataLogManager.FEATURE_ISSUE_DETECTOR_DISC1.equals(feature)) {
                                                if (!WifiBigDataLogManager.FEATURE_ISSUE_DETECTOR_DISC2.equals(feature)) {
                                                    if (WifiBigDataLogManager.FEATURE_HANG.equals(feature)) {
                                                        ClientModeImpl.this.increaseCounter(WifiMonitor.DRIVER_HUNG_EVENT);
                                                        break;
                                                    }
                                                } else if (args.getInt(ReportIdKey.KEY_CATEGORY_ID, 0) == 1) {
                                                    ClientModeImpl.this.sendBroadcastIssueTrackerSysDump(0);
                                                    break;
                                                }
                                            } else {
                                                args.getInt(ReportIdKey.KEY_CATEGORY_ID, 0);
                                                break;
                                            }
                                        } else {
                                            String dataString = args.getString("data", null);
                                            if (dataString != null) {
                                                ClientModeImpl clientModeImpl5 = ClientModeImpl.this;
                                                clientModeImpl5.report(ReportIdKey.ID_BIGDATA_ASSOC_REJECT, ReportUtil.getReportDataFromBigDataParamsOfASSO(dataString, clientModeImpl5.mLastConnectedNetworkId));
                                                break;
                                            }
                                        }
                                    } else {
                                        String dataString2 = args.getString("data", null);
                                        if (dataString2 != null) {
                                            ClientModeImpl.this.report(201, ReportUtil.getReportDataFromBigDataParamsOfONOF(dataString2));
                                            break;
                                        }
                                    }
                                } else {
                                    String dataString3 = args.getString("data", null);
                                    if (dataString3 != null) {
                                        ClientModeImpl clientModeImpl6 = ClientModeImpl.this;
                                        clientModeImpl6.report(200, ReportUtil.getReportDataFromBigDataParamsOfDISC(dataString3, clientModeImpl6.mConnectedApInternalType, ClientModeImpl.this.mBigDataManager.getAndResetLastInternalReason(), ClientModeImpl.this.mLastConnectedNetworkId));
                                        break;
                                    }
                                }
                            }
                        } else {
                            ClientModeImpl clientModeImpl7 = ClientModeImpl.this;
                            clientModeImpl7.sendMessageDelayed(clientModeImpl7.obtainMessage(WifiMonitor.SUP_BIGDATA_EVENT, 0, 0, message.obj), 20000);
                            break;
                        }
                    } else {
                        Log.d(ClientModeImpl.TAG, "shutdowning device");
                        break;
                    }
                    break;
                case ClientModeImpl.CMD_24HOURS_PASSED_AFTER_BOOT /*{ENCODED_INT: 131579}*/:
                    String paramData = ClientModeImpl.this.getWifiParameters(true);
                    Log.i(ClientModeImpl.TAG, "Counter: " + paramData);
                    if (WifiBigDataLogManager.ENABLE_SURVEY_MODE) {
                        Bundle paramArgs = WifiBigDataLogManager.getBigDataBundle(WifiBigDataLogManager.FEATURE_24HR, paramData);
                        ClientModeImpl clientModeImpl8 = ClientModeImpl.this;
                        clientModeImpl8.sendMessage(clientModeImpl8.obtainMessage(ClientModeImpl.CMD_SEC_LOGGING, 0, 0, paramArgs));
                    }
                    ClientModeImpl.this.report(ReportIdKey.ID_BIGDATA_W24H, ReportUtil.getReportDatatForW24H(paramData));
                    ClientModeImpl.this.removeMessages(ClientModeImpl.CMD_24HOURS_PASSED_AFTER_BOOT);
                    ClientModeImpl.this.sendMessageDelayed(ClientModeImpl.CMD_24HOURS_PASSED_AFTER_BOOT, WifiConfigManager.DELETED_EPHEMERAL_SSID_EXPIRY_MS);
                    break;
                case ClientModeImpl.CMD_GET_A_CONFIGURED_NETWORK /*{ENCODED_INT: 131581}*/:
                    ClientModeImpl.this.replyToMessage((ClientModeImpl) message, (Message) message.what, (int) ClientModeImpl.this.mWifiConfigManager.getConfiguredNetwork(message.arg1));
                    break;
                case ClientModeImpl.CMD_SHOW_TOAST_MSG /*{ENCODED_INT: 131582}*/:
                    SemWifiFrameworkUxUtils.showToast(ClientModeImpl.this.mContext, message.arg1, (String) message.obj);
                    break;
                case ClientModeImpl.CMD_RELOAD_CONFIG_STORE_FILE /*{ENCODED_INT: 131583}*/:
                    if (message.arg1 <= 3) {
                        WifiConfigManager wifiConfigManager2 = ClientModeImpl.this.mWifiConfigManager;
                        if (message.arg1 == 3) {
                            z3 = true;
                        }
                        if (!wifiConfigManager2.loadFromStore(z3)) {
                            Log.e(ClientModeImpl.TAG, "Failed to load from config store, retry " + message.arg1);
                            ClientModeImpl.this.sendMessageDelayed(ClientModeImpl.CMD_RELOAD_CONFIG_STORE_FILE, message.arg1 + 1, 0, 3000);
                            break;
                        }
                    }
                    ClientModeImpl.this.mWifiConfigManager.forcinglyEnableAllNetworks(1000);
                    break;
                case WifiP2pServiceImpl.P2P_CONNECTION_CHANGED /*{ENCODED_INT: 143371}*/:
                    ClientModeImpl.this.mP2pConnected.set(((NetworkInfo) message.obj).isConnected());
                    break;
                case WifiP2pServiceImpl.DISCONNECT_WIFI_REQUEST /*{ENCODED_INT: 143372}*/:
                    ClientModeImpl clientModeImpl9 = ClientModeImpl.this;
                    if (message.arg1 == 1) {
                        z2 = true;
                    }
                    clientModeImpl9.mTemporarilyDisconnectWifi = z2;
                    ClientModeImpl.this.replyToMessage(message, WifiP2pServiceImpl.DISCONNECT_WIFI_RESPONSE);
                    break;
                case WifiP2pServiceImpl.DISABLE_P2P_RSP /*{ENCODED_INT: 143395}*/:
                    if (ClientModeImpl.this.mP2pDisableListener != null) {
                        Log.d(ClientModeImpl.TAG, "DISABLE_P2P_RSP mP2pDisableListener == " + ClientModeImpl.this.mP2pDisableListener);
                        ClientModeImpl.this.mP2pDisableListener.onDisable();
                        ClientModeImpl.this.mP2pDisableListener = null;
                        break;
                    }
                    break;
                case 147527:
                    if (message.obj != null) {
                        int eapEvent = message.arg1;
                        ClientModeImpl.this.processMessageForEap(eapEvent, message.arg2, (String) message.obj);
                        if (eapEvent == 2 || eapEvent == 3) {
                            ClientModeImpl.this.notifyDisconnectInternalReason(13);
                            if (!(ClientModeImpl.this.mTargetNetworkId == -1 || ClientModeImpl.this.mTargetWifiConfiguration == null)) {
                                ClientModeImpl clientModeImpl10 = ClientModeImpl.this;
                                clientModeImpl10.report(15, ReportUtil.getReportDataForAuthFailForEap(clientModeImpl10.mTargetNetworkId, eapEvent, ClientModeImpl.this.mTargetWifiConfiguration.status, ClientModeImpl.this.mTargetWifiConfiguration.numAssociation, ClientModeImpl.this.mTargetWifiConfiguration.getNetworkSelectionStatus().getNetworkSelectionStatus(), ClientModeImpl.this.mTargetWifiConfiguration.getNetworkSelectionStatus().getNetworkSelectionDisableReason()));
                                break;
                            }
                        }
                    }
                    break;
                case 151553:
                    ClientModeImpl.this.replyToMessage((ClientModeImpl) message, (Message) 151554, 2);
                    break;
                case 151556:
                    ClientModeImpl.this.deleteNetworkConfigAndSendReply(message, true);
                    break;
                case 151559:
                    ClientModeImpl.this.saveNetworkConfigAndSendReply(message);
                    break;
                case 151569:
                    ClientModeImpl.this.replyToMessage((ClientModeImpl) message, (Message) 151570, 2);
                    break;
                case 151572:
                    ClientModeImpl.this.replyToMessage((ClientModeImpl) message, (Message) 151574, 2);
                    break;
                default:
                    ClientModeImpl.this.loge("Error! unhandled message" + message);
                    break;
            }
            if (1 == 1) {
                ClientModeImpl.this.logStateAndMessage(message, this);
            }
            return true;
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void setupClientMode() {
        Log.d(TAG, "setupClientMode() ifacename = " + this.mInterfaceName);
        setHighPerfModeEnabled(false);
        this.mWifiStateTracker.updateState(0);
        this.mIpClientCallbacks = new IpClientCallbacksImpl();
        this.mFacade.makeIpClient(this.mContext, this.mInterfaceName, this.mIpClientCallbacks);
        if (!this.mIpClientCallbacks.awaitCreation()) {
            loge("Timeout waiting for IpClient");
        }
        setMulticastFilter(true);
        registerForWifiMonitorEvents();
        this.mWifiInjector.getWifiLastResortWatchdog().clearAllFailureCounts();
        setSupplicantLogLevel();
        this.mSupplicantStateTracker.sendMessage(CMD_RESET_SUPPLICANT_STATE);
        this.mLastBssid = null;
        this.mLastNetworkId = -1;
        this.mLastSignalLevel = -1;
        this.mWifiInfo.setMacAddress(this.mWifiNative.getMacAddress(this.mInterfaceName));
        sendSupplicantConnectionChangedBroadcast(true);
        this.mScanResultsEventCounter = 0;
        this.mWifiNative.setExternalSim(this.mInterfaceName, true);
        setRandomMacOui();
        this.mCountryCode.setReadyForChangeAndUpdate(true);
        if (Settings.Global.getInt(this.mContext.getContentResolver(), "airplane_mode_on", 0) == 1) {
            Log.e(TAG, "SupplicantStarted - enter() isAirplaneModeEnabled !!  ");
            this.mCountryCode.setCountryCodeNative("CSC", true);
        }
        this.mWifiDiagnostics.startLogging(this.mVerboseLoggingEnabled);
        this.mIsRunning = true;
        updateBatteryWorkSource(null);
        this.mWifiNative.setBluetoothCoexistenceScanMode(this.mInterfaceName, this.mBluetoothConnectionActive);
        setNetworkDetailedState(NetworkInfo.DetailedState.DISCONNECTED);
        this.mWifiNative.stopFilteringMulticastV4Packets(this.mInterfaceName);
        this.mWifiNative.stopFilteringMulticastV6Packets(this.mInterfaceName);
        this.mWifiNative.setSuspendOptimizations(this.mInterfaceName, this.mSuspendOptNeedsDisabled == 0 && this.mUserWantsSuspendOpt.get());
        setPowerSave(true);
        this.mWifiNative.enableStaAutoReconnect(this.mInterfaceName, false);
        this.mWifiNative.setConcurrencyPriority(true);
        if (!isWifiOnly()) {
            if (this.mUnstableApController == null) {
                this.mUnstableApController = new UnstableApController(new UnstableApController.UnstableApAdapter() {
                    /* class com.android.server.wifi.ClientModeImpl.C037216 */

                    @Override // com.samsung.android.server.wifi.UnstableApController.UnstableApAdapter
                    public void addToBlackList(String bssid) {
                        ClientModeImpl.this.mWifiConnectivityManager.trackBssid(bssid, false, 17);
                    }

                    @Override // com.samsung.android.server.wifi.UnstableApController.UnstableApAdapter
                    public void updateUnstableApNetwork(int networkId, int reason) {
                        if (reason == 2) {
                            for (int i = 0; i < 5; i++) {
                                ClientModeImpl.this.mWifiConfigManager.updateNetworkSelectionStatus(networkId, reason);
                            }
                            return;
                        }
                        ClientModeImpl.this.mWifiConfigManager.updateNetworkSelectionStatus(networkId, reason);
                    }

                    @Override // com.samsung.android.server.wifi.UnstableApController.UnstableApAdapter
                    public void enableNetwork(int networkId) {
                        ClientModeImpl.this.mWifiConfigManager.enableNetwork(networkId, false, 1000);
                    }

                    @Override // com.samsung.android.server.wifi.UnstableApController.UnstableApAdapter
                    public WifiConfiguration getNetwork(int networkId) {
                        return ClientModeImpl.this.mWifiConfigManager.getConfiguredNetwork(networkId);
                    }
                });
            }
            this.mUnstableApController.clearAll();
            this.mUnstableApController.setSimCardState(TelephonyUtil.isSimCardReady(getTelephonyManager()));
        }
        if (OpBrandingLoader.Vendor.VZW == mOpBranding && this.mSemWifiHiddenNetworkTracker == null) {
            this.mSemWifiHiddenNetworkTracker = new SemWifiHiddenNetworkTracker(this.mContext, new SemWifiHiddenNetworkTracker.WifiHiddenNetworkAdapter() {
                /* class com.android.server.wifi.ClientModeImpl.C037317 */

                @Override // com.samsung.android.server.wifi.SemWifiHiddenNetworkTracker.WifiHiddenNetworkAdapter
                public List<ScanResult> getScanResults() {
                    List<ScanResult> scanResults = new ArrayList<>();
                    scanResults.addAll(ClientModeImpl.this.mScanRequestProxy.getScanResults());
                    return scanResults;
                }
            });
        }
        if (ENABLE_SUPPORT_ADPS) {
            updateAdpsState();
            sendMessage(CMD_SET_ADPS_MODE);
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void stopClientMode() {
        this.mWifiDiagnostics.stopLogging();
        this.mIsRunning = false;
        updateBatteryWorkSource(null);
        if (this.mIpClient != null && this.mIpClient.shutdown()) {
            this.mIpClientCallbacks.awaitShutdown();
        }
        this.mNetworkInfo.setIsAvailable(false);
        WifiNetworkAgent wifiNetworkAgent = this.mNetworkAgent;
        if (wifiNetworkAgent != null) {
            wifiNetworkAgent.sendNetworkInfo(this.mNetworkInfo);
        }
        this.mCountryCode.setReadyForChange(false);
        this.mInterfaceName = null;
        sendSupplicantConnectionChangedBroadcast(false);
        this.mWifiConfigManager.removeAllEphemeralOrPasspointConfiguredNetworks();
    }

    /* access modifiers changed from: package-private */
    public void registerConnected() {
        int i = this.mLastNetworkId;
        if (i != -1) {
            this.mWifiConfigManager.updateNetworkAfterConnect(i);
            WifiConfiguration currentNetwork = getCurrentWifiConfiguration();
            if (currentNetwork != null && currentNetwork.isPasspoint()) {
                this.mPasspointManager.onPasspointNetworkConnected(currentNetwork.FQDN);
            }
        }
    }

    /* access modifiers changed from: package-private */
    public void registerDisconnected() {
        int i = this.mLastNetworkId;
        if (i != -1) {
            this.mWifiConfigManager.updateNetworkAfterDisconnect(i);
        }
    }

    public WifiConfiguration getCurrentWifiConfiguration() {
        int i = this.mLastNetworkId;
        if (i == -1) {
            return null;
        }
        return this.mWifiConfigManager.getConfiguredNetwork(i);
    }

    private WifiConfiguration getTargetWifiConfiguration() {
        int i = this.mTargetNetworkId;
        if (i == -1) {
            return null;
        }
        return this.mWifiConfigManager.getConfiguredNetwork(i);
    }

    /* access modifiers changed from: package-private */
    public ScanResult getCurrentScanResult() {
        WifiConfiguration config = getCurrentWifiConfiguration();
        if (config == null) {
            return null;
        }
        String bssid = this.mWifiInfo.getBSSID();
        if (bssid == null) {
            bssid = this.mTargetRoamBSSID;
        }
        ScanDetailCache scanDetailCache = this.mWifiConfigManager.getScanDetailCacheForNetwork(config.networkId);
        if (scanDetailCache == null) {
            return null;
        }
        return scanDetailCache.getScanResult(bssid);
    }

    /* access modifiers changed from: package-private */
    public String getCurrentBSSID() {
        return this.mLastBssid;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void handleCellularCapabilities() {
        handleCellularCapabilities(false);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void handleCellularCapabilities(boolean bForce) {
        byte curNetworkType = 0;
        byte curCellularCapaState = 2;
        if (isWifiOnly()) {
            mCellularCapaState = 3;
            mNetworktype = 0;
            mCellularSignalLevel = 0;
            if (this.mWifiState.get() == 3) {
                this.mWifiNative.updateCellularCapabilities(this.mInterfaceName, mCellularCapaState, mNetworktype, mCellularSignalLevel, mCellularCellId);
                return;
            }
            return;
        }
        try {
            TelephonyManager telephonyManager = getTelephonyManager();
            boolean isNetworkRoaming = telephonyManager.isNetworkRoaming();
            boolean isDataRoamingEnabled = Settings.Global.getInt(this.mContext.getContentResolver(), "data_roaming", 0) != 0;
            boolean isDataEnabled = telephonyManager.getDataEnabled();
            int simCardState = telephonyManager.getSimCardState();
            if (simCardState == 11) {
                curNetworkType = (byte) TelephonyManager.getNetworkClass(telephonyManager.getNetworkType());
                if (isNetworkRoaming) {
                    if (isDataRoamingEnabled && !mIsPolicyMobileData && isDataEnabled && curNetworkType != 0) {
                        curCellularCapaState = 1;
                    }
                } else if (isDataEnabled && !mIsPolicyMobileData && curNetworkType != 0) {
                    curCellularCapaState = 1;
                }
            } else {
                Arrays.fill(mCellularCellId, (byte) 0);
                mCellularSignalLevel = 0;
            }
            if (bForce && curNetworkType != 0) {
                List<CellInfo> cellInfoList = telephonyManager.getAllCellInfo();
                if (cellInfoList != null) {
                    Iterator<CellInfo> it = cellInfoList.iterator();
                    while (true) {
                        if (!it.hasNext()) {
                            break;
                        }
                        CellInfo cellInfo = it.next();
                        Log.d(TAG, "isRegistered " + cellInfo.isRegistered());
                        if (cellInfo.isRegistered()) {
                            mCellularSignalLevel = (byte) getCellLevel(cellInfo);
                            mCellularCellId = getCellId(cellInfo);
                            break;
                        }
                    }
                } else {
                    Log.d(TAG, "cellInfoList is null.");
                }
            }
            if (bForce) {
                mNetworktype = curNetworkType;
                mCellularCapaState = curCellularCapaState;
                if (this.mWifiState.get() == 3) {
                    this.mWifiNative.updateCellularCapabilities(this.mInterfaceName, mCellularCapaState, mNetworktype, mCellularSignalLevel, mCellularCellId);
                }
                mChanged = false;
            } else if (curNetworkType == mNetworktype && curCellularCapaState == mCellularCapaState && !mChanged) {
                Log.d(TAG, "handleCellularCapabilities duplicated values...so skip.");
            } else if (simCardState == 11 || curCellularCapaState != mCellularCapaState || mChanged) {
                mNetworktype = curNetworkType;
                mCellularCapaState = curCellularCapaState;
                if (this.mWifiState.get() == 3) {
                    this.mWifiNative.updateCellularCapabilities(this.mInterfaceName, mCellularCapaState, mNetworktype, mCellularSignalLevel, mCellularCellId);
                }
                mChanged = false;
            } else {
                Log.d(TAG, "handleCellularCapabilities sim not present...so skip.");
            }
        } catch (Exception e) {
            Log.e(TAG, "handleCellularCapabilities exception " + e.toString());
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private byte[] toBytes(int i) {
        Log.d(TAG, "toBytes:" + Integer.toHexString(i));
        byte[] result = {(byte) (i >> 8), (byte) i};
        Log.d(TAG, "toBytes:" + ((int) result[0]) + "," + ((int) result[1]));
        return result;
    }

    private byte[] getCellId(CellInfo cellInfo) {
        int value = 0;
        if (cellInfo instanceof CellInfoLte) {
            value = ((CellInfoLte) cellInfo).getCellIdentity().getCi();
        } else if (cellInfo instanceof CellInfoWcdma) {
            value = ((CellInfoWcdma) cellInfo).getCellIdentity().getCid();
        } else if (cellInfo instanceof CellInfoGsm) {
            value = ((CellInfoGsm) cellInfo).getCellIdentity().getCid();
        } else if (cellInfo instanceof CellInfoCdma) {
            value = ((CellInfoCdma) cellInfo).getCellIdentity().getBasestationId();
        } else if (cellInfo instanceof CellInfoNr) {
            value = ((CellIdentityNr) ((CellInfoNr) cellInfo).getCellIdentity()).getPci();
        } else {
            Log.e(TAG, "Invalid CellInfo type");
        }
        return toBytes(value);
    }

    private int getCellLevel(CellInfo cellInfo) {
        if (cellInfo instanceof CellInfoLte) {
            return ((CellInfoLte) cellInfo).getCellSignalStrength().getLevel();
        }
        if (cellInfo instanceof CellInfoWcdma) {
            return ((CellInfoWcdma) cellInfo).getCellSignalStrength().getLevel();
        }
        if (cellInfo instanceof CellInfoGsm) {
            return ((CellInfoGsm) cellInfo).getCellSignalStrength().getLevel();
        }
        if (cellInfo instanceof CellInfoCdma) {
            return ((CellInfoCdma) cellInfo).getCellSignalStrength().getLevel();
        }
        if (cellInfo instanceof CellInfoNr) {
            return ((CellInfoNr) cellInfo).getCellSignalStrength().getLevel();
        }
        Log.e(TAG, "Invalid CellInfo type");
        return 0;
    }

    private void updateMobileStateForWifiToCellular(byte mobileState) {
        if (this.mMobileState != mobileState) {
            this.mMobileState = mobileState;
            setWifiToCellular(false);
        }
    }

    private void updateParamForWifiToCellular(int scanMode, int rssiThreshold, int candidateRssiThreshold24G, int candidateRssiThreshold5G, int candidateRssiThreshold6G) {
        this.mScanMode = scanMode;
        this.mRssiThreshold = rssiThreshold;
        this.mCandidateRssiThreshold24G = candidateRssiThreshold24G;
        this.mCandidateRssiThreshold5G = candidateRssiThreshold5G;
        this.mCandidateRssiThreshold6G = candidateRssiThreshold6G;
        setWifiToCellular(this.mWtcMode == 0);
    }

    private void setWifiToCellular(boolean forceUpdate) {
        Log.d(TAG, "setWifiToCellular is called.");
        if (forceUpdate) {
            int i = this.mWtcMode;
            if (i != 0) {
                Log.e(TAG, "setWifiToCellular - forceUpdate shold be work only enable mode, it need to be check.");
            } else {
                this.mWifiNative.setWifiToCellular(this.mInterfaceName, i, this.mScanMode, this.mRssiThreshold, this.mCandidateRssiThreshold24G, this.mCandidateRssiThreshold5G, this.mCandidateRssiThreshold6G);
            }
        } else {
            byte b = this.mMobileState;
            if (b != 1) {
                if (b == 2) {
                    if (this.mWtcMode != 3) {
                        this.mWtcMode = 3;
                    } else {
                        return;
                    }
                } else if (this.mWtcMode != 1) {
                    this.mWtcMode = 1;
                } else {
                    return;
                }
                this.mWifiNative.setWifiToCellular(this.mInterfaceName, this.mWtcMode, 0, 0, 0, 0, 0);
            } else if (this.mWtcMode != 0) {
                this.mWtcMode = 0;
                this.mWifiNative.setWifiToCellular(this.mInterfaceName, this.mWtcMode, this.mScanMode, this.mRssiThreshold, this.mCandidateRssiThreshold24G, this.mCandidateRssiThreshold5G, this.mCandidateRssiThreshold6G);
            }
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void enable5GSarBackOff() {
        Log.d(TAG, "enable5GSarBackOff()");
        ServiceState serviceState = getTelephonyManager().getServiceState();
        if (serviceState != null) {
            int nrBearerStatus = serviceState.getNrBearerStatus();
            Log.d(TAG, "serviceState.getNrBearerStatus()=" + nrBearerStatus);
            if (((InputManager) this.mContext.getSystemService(InputManager.class)).getLidState() == 1) {
                return;
            }
            if (nrBearerStatus == 2 || nrBearerStatus == 1) {
                this.mSemSarManager.set5GSarBackOff(nrBearerStatus);
            }
        }
    }

    public void set5GSarBackOff(int mode) {
        Log.d(TAG, "set5GSarBackOff " + mode);
        this.mSemSarManager.set5GSarBackOff(mode);
    }

    class ConnectModeState extends State {
        ConnectModeState() {
        }

        public void enter() {
            Log.d(ClientModeImpl.TAG, "entering ConnectModeState: ifaceName = " + ClientModeImpl.this.mInterfaceName);
            boolean safeModeEnabled = true;
            ClientModeImpl.this.mOperationalMode = 1;
            ClientModeImpl.this.setupClientMode();
            if (!ClientModeImpl.this.mWifiNative.removeAllNetworks(ClientModeImpl.this.mInterfaceName)) {
                ClientModeImpl.this.loge("Failed to remove networks on entering connect mode");
            }
            ClientModeImpl.this.mWifiInfo.reset();
            ClientModeImpl.this.mWifiInfo.setSupplicantState(SupplicantState.DISCONNECTED);
            if (ClientModeImpl.CHARSET_CN.equals(ClientModeImpl.CONFIG_CHARSET) || ClientModeImpl.CHARSET_KOR.equals(ClientModeImpl.CONFIG_CHARSET)) {
                NetworkDetail.clearNonUTF8SsidLists();
            }
            ClientModeImpl.this.mNetworkInfo.setIsAvailable(true);
            if (ClientModeImpl.this.mNetworkAgent != null) {
                ClientModeImpl.this.mNetworkAgent.sendNetworkInfo(ClientModeImpl.this.mNetworkInfo);
            }
            ClientModeImpl.this.setNetworkDetailedState(NetworkInfo.DetailedState.DISCONNECTED);
            if (ClientModeImpl.this.mWifiGeofenceManager.isSupported() && ClientModeImpl.this.mWifiConfigManager.getSavedNetworks(1010).size() > 0) {
                ClientModeImpl.this.mWifiGeofenceManager.startGeofenceThread(ClientModeImpl.this.mWifiConfigManager.getSavedNetworks(1010));
            }
            ClientModeImpl.this.mWifiConnectivityManager.setWifiEnabled(true);
            ClientModeImpl.this.mNetworkFactory.setWifiState(true);
            ClientModeImpl.this.mWifiMetrics.setWifiState(2);
            ClientModeImpl.this.mWifiMetrics.logStaEvent(18);
            ClientModeImpl.this.mSarManager.setClientWifiState(3);
            ClientModeImpl.this.mSemSarManager.setClientWifiState(3);
            ClientModeImpl clientModeImpl = ClientModeImpl.this;
            clientModeImpl.setConcurrentEnabled(clientModeImpl.mConcurrentEnabled);
            ClientModeImpl.this.mWifiScoreCard.noteSupplicantStateChanged(ClientModeImpl.this.mWifiInfo);
            ClientModeImpl.this.initializeWifiChipInfo();
            ClientModeImpl.this.setFccChannel();
            try {
                if (ClientModeImpl.this.getTelephonyManager().getSimCardState() == 11) {
                    ClientModeImpl.access$9976(64);
                } else {
                    ClientModeImpl.access$9972(-65);
                }
                ClientModeImpl.this.getTelephonyManager().listen(ClientModeImpl.this.mPhoneStateListener, ClientModeImpl.mPhoneStateEvent);
            } catch (Exception e) {
                Log.e(ClientModeImpl.TAG, "TelephonyManager.listen exception happend : " + e.toString());
            }
            ClientModeImpl.this.handleCellularCapabilities(true);
            ClientModeImpl.this.mLastEAPFailureCount = 0;
            ClientModeImpl.this.enable5GSarBackOff();
            ClientModeImpl.this.getNCHOVersion();
            if (Settings.Global.getInt(ClientModeImpl.this.mContext.getContentResolver(), "safe_wifi", 0) != 1) {
                safeModeEnabled = false;
            }
            if (!ClientModeImpl.this.mWifiNative.setSafeMode(ClientModeImpl.this.mInterfaceName, safeModeEnabled)) {
                Log.e(ClientModeImpl.TAG, "Failed to set safe Wi-Fi mode");
            }
            if (ClientModeImpl.CSC_WIFI_SUPPORT_VZW_EAP_AKA) {
                ClientModeImpl.this.mWifiConfigManager.semRemoveUnneccessaryNetworks();
            }
        }

        public void exit() {
            ClientModeImpl.this.mOperationalMode = 4;
            if (ClientModeImpl.this.getCurrentState() == ClientModeImpl.this.mConnectedState) {
                ClientModeImpl.this.mDelayDisconnect.checkAndWait(ClientModeImpl.this.mNetworkInfo);
            }
            ClientModeImpl.this.mNetworkInfo.setIsAvailable(false);
            if (ClientModeImpl.this.mNetworkAgent != null) {
                ClientModeImpl.this.mNetworkAgent.sendNetworkInfo(ClientModeImpl.this.mNetworkInfo);
            }
            ClientModeImpl.this.mWifiConnectivityManager.setWifiEnabled(false);
            ClientModeImpl.this.mNetworkFactory.setWifiState(false);
            ClientModeImpl.this.mWifiMetrics.setWifiState(1);
            ClientModeImpl.this.mWifiMetrics.logStaEvent(19);
            ClientModeImpl.this.mWifiScoreCard.noteWifiDisabled(ClientModeImpl.this.mWifiInfo);
            ClientModeImpl.this.mSarManager.setClientWifiState(1);
            ClientModeImpl.this.mSemSarManager.setClientWifiState(1);
            if (!ClientModeImpl.this.mHandleIfaceIsUp) {
                Log.w(ClientModeImpl.TAG, "mHandleIfaceIsUp is false on exiting connect mode, skip removeAllNetworks");
            } else if (!ClientModeImpl.this.mWifiNative.removeAllNetworks(ClientModeImpl.this.mInterfaceName)) {
                ClientModeImpl.this.loge("Failed to remove networks on exiting connect mode");
            }
            ClientModeImpl.this.mWifiInfo.reset();
            ClientModeImpl.this.mWifiInfo.setSupplicantState(SupplicantState.DISCONNECTED);
            ClientModeImpl.this.mWifiScoreCard.noteSupplicantStateChanged(ClientModeImpl.this.mWifiInfo);
            ClientModeImpl.this.stopClientMode();
            ClientModeImpl.this.setConcurrentEnabled(false);
            if (ClientModeImpl.this.mWifiGeofenceManager.isSupported() && !ClientModeImpl.this.isGeofenceUsedByAnotherPackage()) {
                ClientModeImpl.this.mWifiGeofenceManager.deinitGeofence();
            }
        }

        /* JADX INFO: Multiple debug info for r0v9 int: [D('disableOthers' boolean), D('netId' int)] */
        /* JADX INFO: Multiple debug info for r0v160 int: [D('requestData' com.android.server.wifi.util.TelephonyUtil$SimAuthRequestData), D('netId' int)] */
        /* JADX INFO: Multiple debug info for r0v199 int: [D('result' com.android.server.wifi.NetworkUpdateResult), D('netId' int)] */
        /* JADX WARNING: Code restructure failed: missing block: B:501:0x116c, code lost:
            if (r5.isProviderOwnedNetwork(r5.mLastNetworkId, r0) != false) goto L_0x116e;
         */
        /* JADX WARNING: Code restructure failed: missing block: B:520:0x1214, code lost:
            if (r6.isProviderOwnedNetwork(r6.mLastNetworkId, r5) != false) goto L_0x1216;
         */
        public boolean processMessage(Message message) {
            boolean ok;
            WifiConfiguration config;
            String networkSelectionBSSIDCurrent;
            ScanDetailCache scanDetailCache;
            int level2FailureReason;
            String logMessage;
            WifiConfiguration config2;
            boolean handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            handleStatus = true;
            int i = 2;
            boolean z = false;
            boolean z2 = false;
            boolean captivePortal = false;
            int res = 0;
            boolean enabled = false;
            boolean z3 = false;
            boolean enable = false;
            switch (message.what) {
                case ClientModeImpl.CMD_BLUETOOTH_ADAPTER_STATE_CHANGE /*{ENCODED_INT: 131103}*/:
                    ClientModeImpl clientModeImpl = ClientModeImpl.this;
                    if (message.arg1 != 0) {
                        z = true;
                    }
                    clientModeImpl.mBluetoothConnectionActive = z;
                    ClientModeImpl.this.mWifiNative.setBluetoothCoexistenceScanMode(ClientModeImpl.this.mInterfaceName, ClientModeImpl.this.mBluetoothConnectionActive);
                    if (ClientModeImpl.this.mWifiConnectivityManager != null) {
                        ClientModeImpl.this.mWifiConnectivityManager.setBluetoothConnected(ClientModeImpl.this.mBluetoothConnectionActive);
                        break;
                    }
                    break;
                case ClientModeImpl.CMD_REMOVE_NETWORK /*{ENCODED_INT: 131125}*/:
                    int netId = message.arg1;
                    WifiConfiguration config3 = ClientModeImpl.this.mWifiConfigManager.getConfiguredNetwork(netId);
                    if (ClientModeImpl.this.deleteNetworkConfigAndSendReply(message, false)) {
                        if (ClientModeImpl.this.mWifiGeofenceManager.isSupported()) {
                            ClientModeImpl.this.mWifiGeofenceManager.removeNetwork(config3);
                        }
                        if (netId == ClientModeImpl.this.mTargetNetworkId || netId == ClientModeImpl.this.mLastNetworkId) {
                            ClientModeImpl.this.disconnectCommand(0, 18);
                            break;
                        }
                    } else {
                        ClientModeImpl.this.mMessageHandlingStatus = -2;
                        break;
                    }
                case ClientModeImpl.CMD_ENABLE_NETWORK /*{ENCODED_INT: 131126}*/:
                    int i2 = -1;
                    boolean disableOthers = message.arg2 == 1;
                    int netId2 = message.arg1;
                    if (disableOthers) {
                        ok = ClientModeImpl.this.connectToUserSelectNetwork(netId2, message.sendingUid, false);
                    } else {
                        ok = ClientModeImpl.this.mWifiConfigManager.enableNetwork(netId2, false, message.sendingUid);
                    }
                    if (!ok) {
                        ClientModeImpl.this.mMessageHandlingStatus = -2;
                    }
                    ClientModeImpl clientModeImpl2 = ClientModeImpl.this;
                    int i3 = message.what;
                    if (ok) {
                        i2 = 1;
                    }
                    clientModeImpl2.replyToMessage((ClientModeImpl) message, (Message) i3, i2);
                    break;
                case ClientModeImpl.CMD_GET_LINK_LAYER_STATS /*{ENCODED_INT: 131135}*/:
                    ClientModeImpl.this.replyToMessage((ClientModeImpl) message, (Message) message.what, (int) ClientModeImpl.this.getWifiLinkLayerStats());
                    break;
                case ClientModeImpl.CMD_RECONNECT /*{ENCODED_INT: 131146}*/:
                    ClientModeImpl.this.mWifiConnectivityManager.forceConnectivityScan((WorkSource) message.obj);
                    break;
                case ClientModeImpl.CMD_REASSOCIATE /*{ENCODED_INT: 131147}*/:
                    ClientModeImpl clientModeImpl3 = ClientModeImpl.this;
                    clientModeImpl3.mLastConnectAttemptTimestamp = clientModeImpl3.mClock.getWallClockMillis();
                    ClientModeImpl.this.mWifiNative.reassociate(ClientModeImpl.this.mInterfaceName);
                    break;
                case ClientModeImpl.CMD_SET_HIGH_PERF_MODE /*{ENCODED_INT: 131149}*/:
                    if (message.arg1 != 1) {
                        ClientModeImpl.this.setSuspendOptimizationsNative(2, true);
                        break;
                    } else {
                        ClientModeImpl.this.setSuspendOptimizationsNative(2, false);
                        break;
                    }
                case ClientModeImpl.CMD_SET_SUSPEND_OPT_ENABLED /*{ENCODED_INT: 131158}*/:
                    if (message.arg1 != 1) {
                        ClientModeImpl.this.setSuspendOptimizationsNative(4, false);
                        break;
                    } else {
                        ClientModeImpl.this.setSuspendOptimizationsNative(4, true);
                        if (message.arg2 == 1) {
                            ClientModeImpl.this.mSuspendWakeLock.release();
                            break;
                        }
                    }
                    break;
                case ClientModeImpl.CMD_ENABLE_TDLS /*{ENCODED_INT: 131164}*/:
                    if (message.obj != null) {
                        String remoteAddress = (String) message.obj;
                        if (message.arg1 == 1) {
                            enable = true;
                        }
                        ClientModeImpl.this.mWifiNative.startTdls(ClientModeImpl.this.mInterfaceName, remoteAddress, enable);
                        break;
                    }
                    break;
                case ClientModeImpl.CMD_REMOVE_APP_CONFIGURATIONS /*{ENCODED_INT: 131169}*/:
                    Set<Integer> removedNetworkIds = ClientModeImpl.this.mWifiConfigManager.removeNetworksForApp((ApplicationInfo) message.obj);
                    if (removedNetworkIds.contains(Integer.valueOf(ClientModeImpl.this.mTargetNetworkId)) || removedNetworkIds.contains(Integer.valueOf(ClientModeImpl.this.mLastNetworkId))) {
                        ClientModeImpl.this.disconnectCommand(0, 18);
                        break;
                    }
                case ClientModeImpl.CMD_DISABLE_EPHEMERAL_NETWORK /*{ENCODED_INT: 131170}*/:
                    WifiConfiguration config4 = ClientModeImpl.this.mWifiConfigManager.disableEphemeralNetwork((String) message.obj);
                    if (config4 != null && (config4.networkId == ClientModeImpl.this.mTargetNetworkId || config4.networkId == ClientModeImpl.this.mLastNetworkId)) {
                        ClientModeImpl.this.disconnectCommand(0, 19);
                        break;
                    }
                case ClientModeImpl.CMD_RESET_SIM_NETWORKS /*{ENCODED_INT: 131173}*/:
                    ClientModeImpl.this.log("resetting EAP-SIM/AKA/AKA' networks since SIM was changed");
                    boolean simPresent = message.arg1 == 1;
                    if (!simPresent) {
                        ClientModeImpl.this.mPasspointManager.removeEphemeralProviders();
                        ClientModeImpl.this.mWifiConfigManager.resetSimNetworks();
                        ClientModeImpl.this.mWifiNative.simAbsent(ClientModeImpl.this.mInterfaceName);
                    }
                    if (ClientModeImpl.this.mUnstableApController != null) {
                        ClientModeImpl.this.mUnstableApController.setSimCardState(TelephonyUtil.isSimCardReady(ClientModeImpl.this.getTelephonyManager()));
                    }
                    if (!simPresent && ClientModeImpl.CSC_WIFI_SUPPORT_VZW_EAP_AKA && (config = ClientModeImpl.this.getCurrentWifiConfiguration()) != null && !TextUtils.isEmpty(config.SSID) && config.semIsVendorSpecificSsid) {
                        SemWifiFrameworkUxUtils.showWarningDialog(ClientModeImpl.this.mContext, 3, new String[]{StringUtil.removeDoubleQuotes(config.SSID)});
                    }
                    if (message.arg1 == 0) {
                        ClientModeImpl.this.removePasspointNetworkIfSimAbsent();
                    }
                    ClientModeImpl.this.updateVendorApSimState();
                    try {
                        if (ClientModeImpl.this.getTelephonyManager().getSimCardState() == 11) {
                            ClientModeImpl.access$9976(64);
                        } else {
                            ClientModeImpl.access$9972(-65);
                        }
                        ClientModeImpl.this.getTelephonyManager().listen(ClientModeImpl.this.mPhoneStateListener, ClientModeImpl.mPhoneStateEvent);
                    } catch (Exception e) {
                        Log.e(ClientModeImpl.TAG, "TelephonyManager.listen exception happend : " + e.toString());
                    }
                    ClientModeImpl.this.handleCellularCapabilities();
                    break;
                case ClientModeImpl.CMD_QUERY_OSU_ICON /*{ENCODED_INT: 131176}*/:
                    ClientModeImpl.this.mPasspointManager.queryPasspointIcon(((Bundle) message.obj).getLong("BSSID"), ((Bundle) message.obj).getString(ClientModeImpl.EXTRA_OSU_ICON_QUERY_FILENAME));
                    break;
                case ClientModeImpl.CMD_MATCH_PROVIDER_NETWORK /*{ENCODED_INT: 131177}*/:
                    ClientModeImpl.this.replyToMessage((ClientModeImpl) message, (Message) message.what, 0);
                    break;
                case ClientModeImpl.CMD_ADD_OR_UPDATE_PASSPOINT_CONFIG /*{ENCODED_INT: 131178}*/:
                    Bundle bundle = (Bundle) message.obj;
                    PasspointConfiguration passpointConfig = (PasspointConfiguration) bundle.getParcelable(ClientModeImpl.EXTRA_PASSPOINT_CONFIGURATION);
                    if (!ClientModeImpl.this.mPasspointManager.addOrUpdateProvider(passpointConfig, bundle.getInt(ClientModeImpl.EXTRA_UID), bundle.getString(ClientModeImpl.EXTRA_PACKAGE_NAME))) {
                        ClientModeImpl.this.replyToMessage((ClientModeImpl) message, (Message) message.what, -1);
                        break;
                    } else {
                        String fqdn = passpointConfig.getHomeSp().getFqdn();
                        ClientModeImpl clientModeImpl4 = ClientModeImpl.this;
                        if (!clientModeImpl4.isProviderOwnedNetwork(clientModeImpl4.mTargetNetworkId, fqdn)) {
                            ClientModeImpl clientModeImpl5 = ClientModeImpl.this;
                            break;
                        }
                        ClientModeImpl.this.logd("Disconnect from current network since its provider is updated");
                        ClientModeImpl.this.disconnectCommand(0, 21);
                        ClientModeImpl.this.replyToMessage((ClientModeImpl) message, (Message) message.what, 1);
                        break;
                    }
                case ClientModeImpl.CMD_REMOVE_PASSPOINT_CONFIG /*{ENCODED_INT: 131179}*/:
                    String fqdn2 = (String) message.obj;
                    if (!ClientModeImpl.this.mPasspointManager.removeProvider(fqdn2)) {
                        ClientModeImpl.this.replyToMessage((ClientModeImpl) message, (Message) message.what, -1);
                        break;
                    } else {
                        boolean needTosendDisconnectMsg = false;
                        ClientModeImpl clientModeImpl6 = ClientModeImpl.this;
                        if (!clientModeImpl6.isProviderOwnedNetwork(clientModeImpl6.mTargetNetworkId, fqdn2)) {
                            ClientModeImpl clientModeImpl7 = ClientModeImpl.this;
                            break;
                        }
                        ClientModeImpl.this.logd("Disconnect from current network since its provider is removed");
                        needTosendDisconnectMsg = true;
                        Iterator<WifiConfiguration> it = ClientModeImpl.this.mWifiConfigManager.getConfiguredNetworks().iterator();
                        while (true) {
                            if (it.hasNext()) {
                                WifiConfiguration network = it.next();
                                if (network.isPasspoint() && fqdn2.equals(network.FQDN)) {
                                    ClientModeImpl.this.mWifiConfigManager.removeNetwork(network.networkId, network.creatorUid);
                                }
                            }
                        }
                        if (needTosendDisconnectMsg) {
                            ClientModeImpl.this.disconnectCommand(0, 18);
                        }
                        ClientModeImpl.this.mWifiConfigManager.removePasspointConfiguredNetwork(fqdn2);
                        ClientModeImpl.this.replyToMessage((ClientModeImpl) message, (Message) message.what, 1);
                        break;
                    }
                    break;
                case ClientModeImpl.CMD_GET_MATCHING_OSU_PROVIDERS /*{ENCODED_INT: 131181}*/:
                    ClientModeImpl.this.replyToMessage((ClientModeImpl) message, (Message) message.what, (int) ClientModeImpl.this.mPasspointManager.getMatchingOsuProviders((List) message.obj));
                    break;
                case ClientModeImpl.CMD_GET_MATCHING_PASSPOINT_CONFIGS_FOR_OSU_PROVIDERS /*{ENCODED_INT: 131182}*/:
                    ClientModeImpl.this.replyToMessage((ClientModeImpl) message, (Message) message.what, (int) ClientModeImpl.this.mPasspointManager.getMatchingPasspointConfigsForOsuProviders((List) message.obj));
                    break;
                case ClientModeImpl.CMD_GET_WIFI_CONFIGS_FOR_PASSPOINT_PROFILES /*{ENCODED_INT: 131184}*/:
                    ClientModeImpl.this.replyToMessage((ClientModeImpl) message, (Message) message.what, (int) ClientModeImpl.this.mPasspointManager.getWifiConfigsForPasspointProfiles((List) message.obj));
                    break;
                case ClientModeImpl.CMD_TARGET_BSSID /*{ENCODED_INT: 131213}*/:
                    if (message.obj != null) {
                        ClientModeImpl.this.mTargetRoamBSSID = (String) message.obj;
                        break;
                    }
                    break;
                case ClientModeImpl.CMD_START_CONNECT /*{ENCODED_INT: 131215}*/:
                    int netId3 = message.arg1;
                    int uid = message.arg2;
                    String bssid = (String) message.obj;
                    if (!ClientModeImpl.this.hasConnectionRequests()) {
                        if (ClientModeImpl.this.mNetworkAgent != null) {
                            if (!ClientModeImpl.this.mWifiPermissionsUtil.checkNetworkSettingsPermission(uid)) {
                                ClientModeImpl.this.loge("CMD_START_CONNECT but no requests and connected, but app does not have sufficient permissions, bailing");
                                break;
                            }
                        } else {
                            ClientModeImpl.this.loge("CMD_START_CONNECT but no requests and not connected, bailing");
                            break;
                        }
                    }
                    WifiConfiguration config5 = ClientModeImpl.this.mWifiConfigManager.getConfiguredNetworkWithoutMasking(netId3);
                    ClientModeImpl.this.logd("CMD_START_CONNECT sup state " + ClientModeImpl.this.mSupplicantStateTracker.getSupplicantStateName() + " my state " + ClientModeImpl.this.getCurrentState().getName() + " nid=" + Integer.toString(netId3) + " roam=" + Boolean.toString(ClientModeImpl.this.mIsAutoRoaming));
                    if (config5 != null) {
                        ClientModeImpl.this.mWifiScoreCard.noteConnectionAttempt(ClientModeImpl.this.mWifiInfo);
                        ClientModeImpl.this.mTargetNetworkId = netId3;
                        ClientModeImpl.this.setTargetBssid(config5, bssid);
                        if (TelephonyUtil.isSimConfig(config5) && !ClientModeImpl.this.setPermanentIdentity(config5)) {
                            Log.i(ClientModeImpl.TAG, "CMD_START_CONNECT , There is no Identity for EAP SimConfig network, skip connection");
                            break;
                        } else {
                            if (config5.enterpriseConfig.getEapMethod() == 18) {
                                config5.enterpriseConfig.setEapMethod(0);
                                config5.enterpriseConfig.setPhase1Method(-1);
                                config5.enterpriseConfig.setPacFile("");
                                ClientModeImpl.this.mWifiConfigManager.addOrUpdateNetwork(config5, 1000);
                            }
                            ClientModeImpl clientModeImpl8 = ClientModeImpl.this;
                            clientModeImpl8.reportConnectionAttemptStart(config5, clientModeImpl8.mTargetRoamBSSID, 5);
                            if (ClientModeImpl.ENBLE_WLAN_CONFIG_ANALYTICS && !ClientModeImpl.this.isDisconnected() && Settings.Global.getInt(ClientModeImpl.this.mContext.getContentResolver(), "safe_wifi", 0) != 1 && !ClientModeImpl.this.mWifiNative.setExtendedAnalyticsDisconnectReason(ClientModeImpl.this.mInterfaceName, 256)) {
                                Log.e(ClientModeImpl.TAG, "Failed to set ExtendedAnalyticsDisconnectReason");
                            }
                            if (config5.macRandomizationSetting != 1 || !ClientModeImpl.this.mConnectedMacRandomzationSupported || !ClientModeImpl.this.isSupportRandomMac(config5)) {
                                ClientModeImpl.this.setCurrentMacToFactoryMac(config5);
                            } else {
                                ClientModeImpl.this.configureRandomizedMacAddress(config5);
                            }
                            String currentMacAddress = ClientModeImpl.this.mWifiNative.getMacAddress(ClientModeImpl.this.mInterfaceName);
                            ClientModeImpl.this.mWifiInfo.setMacAddress(currentMacAddress);
                            if (ClientModeImpl.DBG) {
                                Log.d(ClientModeImpl.TAG, "Connecting with " + currentMacAddress + " as the mac address");
                            }
                            if (!(ClientModeImpl.this.mLastNetworkId == -1 || ClientModeImpl.this.mLastNetworkId == netId3 || ClientModeImpl.this.getCurrentState() != ClientModeImpl.this.mConnectedState)) {
                                ClientModeImpl.this.mDelayDisconnect.checkAndWait(ClientModeImpl.this.mNetworkInfo);
                            }
                            if (config5.enterpriseConfig != null && TelephonyUtil.isSimEapMethod(config5.enterpriseConfig.getEapMethod()) && ClientModeImpl.this.mWifiInjector.getCarrierNetworkConfig().isCarrierEncryptionInfoAvailable() && TextUtils.isEmpty(config5.enterpriseConfig.getAnonymousIdentity()) && config5.semIsVendorSpecificSsid) {
                                String anonAtRealm = TelephonyUtil.getAnonymousIdentityWith3GppRealm(ClientModeImpl.this.getTelephonyManager());
                                int eapMethod = config5.enterpriseConfig.getEapMethod();
                                String prefix = "";
                                if (eapMethod == 4) {
                                    prefix = "1";
                                } else if (eapMethod == 5) {
                                    prefix = "0";
                                } else if (eapMethod == 6) {
                                    prefix = "6";
                                } else {
                                    Log.e(ClientModeImpl.TAG, " config is not a valid EapMethod " + eapMethod);
                                }
                                String prefixAnonAtRealm = prefix + anonAtRealm;
                                Log.i(ClientModeImpl.TAG, "setAnonymousIdentity " + prefixAnonAtRealm);
                                config5.enterpriseConfig.setAnonymousIdentity(prefixAnonAtRealm);
                            }
                            if (!ClientModeImpl.this.mWifiNative.connectToNetwork(ClientModeImpl.this.mInterfaceName, config5)) {
                                ClientModeImpl.this.loge("CMD_START_CONNECT Failed to start connection to network " + config5);
                                ClientModeImpl.this.reportConnectionAttemptEnd(5, 1, 0);
                                ClientModeImpl.this.replyToMessage((ClientModeImpl) message, (Message) 151554, 0);
                                if (config5.allowedKeyManagement.get(1) || config5.allowedKeyManagement.get(4) || config5.allowedKeyManagement.get(8) || config5.allowedKeyManagement.get(22) || (config5.allowedKeyManagement.get(0) && config5.wepKeys[0] != null)) {
                                    ClientModeImpl.this.mWifiConfigManager.updateNetworkSelectionStatus(netId3, 13);
                                    SemWifiFrameworkUxUtils.sendShowInfoIntentToSettings(ClientModeImpl.this.mContext, 60, config5.networkId, 13);
                                    break;
                                }
                            } else {
                                ClientModeImpl.this.mWifiMetrics.logStaEvent(11, config5);
                                ClientModeImpl.this.report(11, ReportUtil.getReportDataForTryToConnect(netId3, config5.SSID, config5.numAssociation, bssid, false));
                                ClientModeImpl clientModeImpl9 = ClientModeImpl.this;
                                clientModeImpl9.mLastConnectAttemptTimestamp = clientModeImpl9.mClock.getWallClockMillis();
                                ClientModeImpl.this.mTargetWifiConfiguration = config5;
                                ClientModeImpl.this.mIsAutoRoaming = false;
                                String networkSelectionBSSID = config5.getNetworkSelectionStatus().getNetworkSelectionBSSID();
                                WifiConfiguration currentConfig = ClientModeImpl.this.getCurrentWifiConfiguration();
                                if (currentConfig == null) {
                                    networkSelectionBSSIDCurrent = null;
                                } else {
                                    networkSelectionBSSIDCurrent = currentConfig.getNetworkSelectionStatus().getNetworkSelectionBSSID();
                                }
                                if (ClientModeImpl.this.getCurrentState() != ClientModeImpl.this.mDisconnectedState && (ClientModeImpl.this.mLastNetworkId != netId3 || !Objects.equals(networkSelectionBSSID, networkSelectionBSSIDCurrent))) {
                                    ClientModeImpl.this.notifyDisconnectInternalReason(3);
                                    ClientModeImpl clientModeImpl10 = ClientModeImpl.this;
                                    clientModeImpl10.transitionTo(clientModeImpl10.mDisconnectingState);
                                    break;
                                }
                            }
                        }
                    } else {
                        ClientModeImpl.this.loge("CMD_START_CONNECT and no config, bail out...");
                        break;
                    }
                    break;
                case ClientModeImpl.CMD_START_ROAM /*{ENCODED_INT: 131217}*/:
                    ClientModeImpl.this.mMessageHandlingStatus = ClientModeImpl.MESSAGE_HANDLING_STATUS_DISCARD;
                    break;
                case ClientModeImpl.CMD_ASSOCIATED_BSSID /*{ENCODED_INT: 131219}*/:
                    String someBssid = (String) message.obj;
                    if (!(someBssid == null || (scanDetailCache = ClientModeImpl.this.mWifiConfigManager.getScanDetailCacheForNetwork(ClientModeImpl.this.mTargetNetworkId)) == null)) {
                        ClientModeImpl.this.mWifiMetrics.setConnectionScanDetail(scanDetailCache.getScanDetail(someBssid));
                    }
                    if ("".equals(ClientModeImpl.CONFIG_SECURE_SVC_INTEGRATION) && !SemCscFeature.getInstance().getBoolean(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_DISABLEMWIPS) && MobileWipsFrameworkService.getInstance() != null) {
                        MobileWipsFrameworkService.getInstance().sendEmptyMessage(8);
                    }
                    handleStatus = false;
                    break;
                case ClientModeImpl.CMD_REMOVE_USER_CONFIGURATIONS /*{ENCODED_INT: 131224}*/:
                    Set<Integer> removedNetworkIds2 = ClientModeImpl.this.mWifiConfigManager.removeNetworksForUser(Integer.valueOf(message.arg1).intValue());
                    if (removedNetworkIds2.contains(Integer.valueOf(ClientModeImpl.this.mTargetNetworkId)) || removedNetworkIds2.contains(Integer.valueOf(ClientModeImpl.this.mLastNetworkId))) {
                        ClientModeImpl.this.disconnectCommand(0, 18);
                        break;
                    }
                case ClientModeImpl.CMD_STOP_IP_PACKET_OFFLOAD /*{ENCODED_INT: 131233}*/:
                    int slot = message.arg1;
                    int ret = ClientModeImpl.this.stopWifiIPPacketOffload(slot);
                    if (ClientModeImpl.this.mNetworkAgent != null) {
                        ClientModeImpl.this.mNetworkAgent.onSocketKeepaliveEvent(slot, ret);
                        break;
                    }
                    break;
                case ClientModeImpl.CMD_ENABLE_WIFI_CONNECTIVITY_MANAGER /*{ENCODED_INT: 131238}*/:
                    WifiConnectivityManager wifiConnectivityManager = ClientModeImpl.this.mWifiConnectivityManager;
                    if (message.arg1 == 1) {
                        z3 = true;
                    }
                    wifiConnectivityManager.enable(z3);
                    break;
                case ClientModeImpl.CMD_GET_ALL_MATCHING_FQDNS_FOR_SCAN_RESULTS /*{ENCODED_INT: 131240}*/:
                    ClientModeImpl.this.replyToMessage((ClientModeImpl) message, (Message) message.what, (int) ClientModeImpl.this.mPasspointManager.getAllMatchingFqdnsForScanResults((List) message.obj));
                    break;
                case ClientModeImpl.CMD_CONFIG_ND_OFFLOAD /*{ENCODED_INT: 131276}*/:
                    if (message.arg1 > 0) {
                        enabled = true;
                    }
                    ClientModeImpl.this.mWifiNative.configureNeighborDiscoveryOffload(ClientModeImpl.this.mInterfaceName, enabled);
                    break;
                case ClientModeImpl.CMD_START_SUBSCRIPTION_PROVISIONING /*{ENCODED_INT: 131326}*/:
                    if (ClientModeImpl.this.mPasspointManager.startSubscriptionProvisioning(message.arg1, message.getData().getParcelable(ClientModeImpl.EXTRA_OSU_PROVIDER), (IProvisioningCallback) message.obj)) {
                        res = 1;
                    }
                    ClientModeImpl.this.replyToMessage((ClientModeImpl) message, (Message) message.what, res);
                    break;
                case ClientModeImpl.CMD_SET_ADPS_MODE /*{ENCODED_INT: 131383}*/:
                    ClientModeImpl.this.updateAdpsState();
                    break;
                case ClientModeImpl.CMD_SEC_API_ASYNC /*{ENCODED_INT: 131573}*/:
                    if (!ClientModeImpl.this.processMessageForCallSECApiAsync(message)) {
                        return false;
                    }
                    break;
                case ClientModeImpl.CMD_SEC_API /*{ENCODED_INT: 131574}*/:
                    ClientModeImpl.this.replyToMessage((ClientModeImpl) message, (Message) message.what, ClientModeImpl.this.processMessageForCallSECApi(message));
                    break;
                case ClientModeImpl.CMD_SEC_STRING_API /*{ENCODED_INT: 131575}*/:
                    String stringResult = ClientModeImpl.this.processMessageForCallSECStringApi(message);
                    if (stringResult != null) {
                        ClientModeImpl.this.replyToMessage((ClientModeImpl) message, (Message) message.what, (int) stringResult);
                        break;
                    } else {
                        return false;
                    }
                case ClientModeImpl.CMD_SCAN_RESULT_AVAILABLE /*{ENCODED_INT: 131584}*/:
                    if (ClientModeImpl.this.mUnstableApController != null) {
                        List<ScanResult> scanResults = new ArrayList<>();
                        scanResults.addAll(ClientModeImpl.this.mScanRequestProxy.getScanResults());
                        ClientModeImpl.this.mUnstableApController.verifyAll(scanResults);
                    }
                    try {
                        if (!ClientModeImpl.this.mIsRoamNetwork && ClientModeImpl.this.isRoamNetwork()) {
                            if (ClientModeImpl.this.checkAndSetConnectivityInstance() && !ClientModeImpl.this.isWifiOnly()) {
                                ClientModeImpl.this.mCm.setWifiRoamNetwork(true);
                            }
                            ClientModeImpl.this.mIsRoamNetwork = true;
                            Log.i(ClientModeImpl.TAG, "Roam Network");
                            break;
                        }
                    } catch (Exception e2) {
                        e2.printStackTrace();
                        break;
                    }
                case WifiConnectivityMonitor.CMD_CONFIG_SET_CAPTIVE_PORTAL:
                    if (message.arg2 == 1) {
                        captivePortal = true;
                    }
                    ClientModeImpl.this.mWifiConfigManager.setCaptivePortal(message.arg1, captivePortal);
                    break;
                case WifiConnectivityMonitor.CMD_CONFIG_UPDATE_NETWORK_SELECTION:
                    ClientModeImpl.this.mWifiConfigManager.updateNetworkSelectionStatus(message.arg1, message.arg2);
                    break;
                case WifiP2pServiceImpl.DISCONNECT_WIFI_REQUEST /*{ENCODED_INT: 143372}*/:
                    if (message.arg1 != 1) {
                        ClientModeImpl.this.mWifiNative.reconnect(ClientModeImpl.this.mInterfaceName);
                        ClientModeImpl.this.mTemporarilyDisconnectWifi = false;
                        break;
                    } else {
                        ClientModeImpl.this.mWifiMetrics.logStaEvent(15, 5);
                        ClientModeImpl.this.notifyDisconnectInternalReason(16);
                        ClientModeImpl.this.mWifiNative.disconnect(ClientModeImpl.this.mInterfaceName);
                        ClientModeImpl.this.mTemporarilyDisconnectWifi = true;
                        break;
                    }
                case WifiMonitor.NETWORK_CONNECTION_EVENT /*{ENCODED_INT: 147459}*/:
                    if (ClientModeImpl.this.mVerboseLoggingEnabled) {
                        ClientModeImpl.this.log("Network connection established");
                    }
                    ClientModeImpl.this.mLastNetworkId = message.arg1;
                    ClientModeImpl clientModeImpl11 = ClientModeImpl.this;
                    clientModeImpl11.mLastConnectedNetworkId = clientModeImpl11.mLastNetworkId;
                    ClientModeImpl.this.mWifiConfigManager.clearRecentFailureReason(ClientModeImpl.this.mLastNetworkId);
                    ClientModeImpl.this.mLastBssid = (String) message.obj;
                    int reasonCode = message.arg2;
                    WifiConfiguration config6 = ClientModeImpl.this.getCurrentWifiConfiguration();
                    if (config6 == null) {
                        ClientModeImpl.this.logw("Connected to unknown networkId " + ClientModeImpl.this.mLastNetworkId + ", disconnecting...");
                        ClientModeImpl.this.disconnectCommand(0, 22);
                        break;
                    } else {
                        ClientModeImpl.this.mWifiInfo.setBSSID(ClientModeImpl.this.mLastBssid);
                        ClientModeImpl.this.mWifiInfo.setNetworkId(ClientModeImpl.this.mLastNetworkId);
                        ClientModeImpl.this.mWifiInfo.setMacAddress(ClientModeImpl.this.mWifiNative.getMacAddress(ClientModeImpl.this.mInterfaceName));
                        ScanDetailCache scanDetailCache2 = ClientModeImpl.this.mWifiConfigManager.getScanDetailCacheForNetwork(config6.networkId);
                        if (scanDetailCache2 != null && ClientModeImpl.this.mLastBssid != null) {
                            Log.d(ClientModeImpl.TAG, "scan detail is in cache, find scanResult from cache");
                            ScanResult scanResult = scanDetailCache2.getScanResult(ClientModeImpl.this.mLastBssid);
                            if (scanResult != null) {
                                Log.d(ClientModeImpl.TAG, "found scanResult! update mWifiInfo");
                                ClientModeImpl.this.mWifiInfo.setFrequency(scanResult.frequency);
                                ClientModeImpl.this.updateWifiInfoForVendors(scanResult);
                                ClientModeImpl.this.mWifiInfo.setWifiMode(scanResult.wifiMode);
                            } else {
                                Log.d(ClientModeImpl.TAG, "can't update vendor infos, bssid: " + ClientModeImpl.this.mLastBssid);
                            }
                        } else if (scanDetailCache2 == null && ClientModeImpl.this.mLastBssid != null) {
                            Log.d(ClientModeImpl.TAG, "scan detail is not in cache, find scanResult from last native scan results");
                            Iterator<ScanDetail> it2 = ClientModeImpl.this.mWifiNative.getScanResults(ClientModeImpl.this.mInterfaceName).iterator();
                            while (true) {
                                if (it2.hasNext()) {
                                    ScanDetail scanDetail = it2.next();
                                    if (ClientModeImpl.this.mLastBssid.equals(scanDetail.getBSSIDString())) {
                                        ScanResult scanResult2 = scanDetail.getScanResult();
                                        Log.d(ClientModeImpl.TAG, "found scanResult! update the cache and mWifiInfo");
                                        ClientModeImpl.this.mWifiConfigManager.updateScanDetailForNetwork(ClientModeImpl.this.mLastNetworkId, scanDetail);
                                        ClientModeImpl.this.mWifiInfo.setFrequency(scanResult2.frequency);
                                        ClientModeImpl.this.updateWifiInfoForVendors(scanResult2);
                                        ClientModeImpl.this.mWifiInfo.setWifiMode(scanResult2.wifiMode);
                                    }
                                }
                            }
                        }
                        ClientModeImpl.this.mWifiConnectivityManager.trackBssid(ClientModeImpl.this.mLastBssid, true, reasonCode);
                        if (config6.enterpriseConfig != null && TelephonyUtil.isSimEapMethod(config6.enterpriseConfig.getEapMethod()) && !TelephonyUtil.isAnonymousAtRealmIdentity(config6.enterpriseConfig.getAnonymousIdentity())) {
                            String anonymousIdentity = ClientModeImpl.this.mWifiNative.getEapAnonymousIdentity(ClientModeImpl.this.mInterfaceName);
                            if (ClientModeImpl.this.mVerboseLoggingEnabled) {
                                ClientModeImpl.this.log("EAP Pseudonym: " + anonymousIdentity);
                            }
                            config6.enterpriseConfig.setAnonymousIdentity(anonymousIdentity);
                            ClientModeImpl.this.mWifiConfigManager.addOrUpdateNetwork(config6, 1010);
                        }
                        if (ClientModeImpl.this.mUnstableApController != null) {
                            ClientModeImpl.this.mUnstableApController.l2Connected(ClientModeImpl.this.mLastNetworkId);
                        }
                        ClientModeImpl clientModeImpl12 = ClientModeImpl.this;
                        clientModeImpl12.sendNetworkStateChangeBroadcast(clientModeImpl12.mLastBssid);
                        ClientModeImpl clientModeImpl13 = ClientModeImpl.this;
                        clientModeImpl13.report(12, ReportUtil.getReportDataForL2Connected(clientModeImpl13.mLastNetworkId, ClientModeImpl.this.mLastBssid));
                        ClientModeImpl clientModeImpl14 = ClientModeImpl.this;
                        clientModeImpl14.transitionTo(clientModeImpl14.mObtainingIpState);
                        break;
                    }
                    break;
                case WifiMonitor.NETWORK_DISCONNECTION_EVENT /*{ENCODED_INT: 147460}*/:
                    if (ClientModeImpl.this.mVerboseLoggingEnabled) {
                        ClientModeImpl.this.log("ConnectModeState: Network connection lost ");
                    }
                    ClientModeImpl clientModeImpl15 = ClientModeImpl.this;
                    int i4 = clientModeImpl15.mTargetNetworkId;
                    String str = (String) message.obj;
                    if (message.arg1 != 0) {
                        z2 = true;
                    }
                    clientModeImpl15.checkAndUpdateUnstableAp(i4, str, z2, message.arg2);
                    ClientModeImpl.this.handleNetworkDisconnect();
                    ClientModeImpl clientModeImpl16 = ClientModeImpl.this;
                    clientModeImpl16.transitionTo(clientModeImpl16.mDisconnectedState);
                    break;
                case WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT /*{ENCODED_INT: 147462}*/:
                    SupplicantState state = ClientModeImpl.this.handleSupplicantStateChange(message);
                    if (state == SupplicantState.DISCONNECTED && ClientModeImpl.this.mNetworkInfo.getState() != NetworkInfo.State.DISCONNECTED) {
                        if (ClientModeImpl.this.mVerboseLoggingEnabled) {
                            ClientModeImpl.this.log("Missed CTRL-EVENT-DISCONNECTED, disconnect");
                        }
                        ClientModeImpl.this.handleNetworkDisconnect();
                        ClientModeImpl clientModeImpl17 = ClientModeImpl.this;
                        clientModeImpl17.transitionTo(clientModeImpl17.mDisconnectedState);
                    }
                    if (state == SupplicantState.COMPLETED) {
                        if (ClientModeImpl.this.mIpClient != null) {
                            ClientModeImpl.this.mIpClient.confirmConfiguration();
                        }
                        ClientModeImpl.this.mWifiScoreReport.noteIpCheck();
                        if ("".equals(ClientModeImpl.CONFIG_SECURE_SVC_INTEGRATION) && !SemCscFeature.getInstance().getBoolean(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_DISABLEMWIPS) && MobileWipsFrameworkService.getInstance() != null) {
                            MobileWipsFrameworkService.getInstance().sendEmptyMessage(17);
                            break;
                        }
                    }
                    break;
                case WifiMonitor.AUTHENTICATION_FAILURE_EVENT /*{ENCODED_INT: 147463}*/:
                    ClientModeImpl.this.mWifiDiagnostics.triggerBugReportDataCapture(2);
                    ClientModeImpl.this.mSupplicantStateTracker.sendMessage(WifiMonitor.AUTHENTICATION_FAILURE_EVENT);
                    int disableReason = 3;
                    disableReason = 3;
                    disableReason = 3;
                    disableReason = 3;
                    int reasonCode2 = message.arg1;
                    WifiConfiguration targetedNetwork = ClientModeImpl.this.mWifiConfigManager.getConfiguredNetwork(ClientModeImpl.this.mTargetNetworkId);
                    if (targetedNetwork != null && 2 == reasonCode2) {
                        String bssid2 = (String) message.obj;
                        boolean isSameNetwork = true;
                        isSameNetwork = true;
                        isSameNetwork = true;
                        ScanDetailCache scanDetailCache3 = ClientModeImpl.this.mWifiConfigManager.getScanDetailCacheForNetwork(targetedNetwork.networkId);
                        if (!(bssid2 == null || scanDetailCache3 == null || scanDetailCache3.getScanResult(bssid2) != null)) {
                            Log.i(ClientModeImpl.TAG, "authentication failure, but not for target network");
                            isSameNetwork = false;
                        }
                        if (isSameNetwork) {
                            if (targetedNetwork.allowedKeyManagement.get(1) || targetedNetwork.allowedKeyManagement.get(8) || targetedNetwork.allowedKeyManagement.get(4) || targetedNetwork.allowedKeyManagement.get(22) || (targetedNetwork.allowedKeyManagement.get(0) && targetedNetwork.wepKeys[0] != null)) {
                                disableReason = 13;
                            } else if (ClientModeImpl.this.isPermanentWrongPasswordFailure(targetedNetwork, reasonCode2)) {
                                disableReason = 13;
                            }
                        }
                    }
                    if (reasonCode2 == 3) {
                        int errorCode = message.arg2;
                        ClientModeImpl clientModeImpl18 = ClientModeImpl.this;
                        clientModeImpl18.handleEapAuthFailure(clientModeImpl18.mTargetNetworkId, errorCode);
                        if (errorCode == 1031) {
                            disableReason = 14;
                        }
                    }
                    if (targetedNetwork != null && TelephonyUtil.isSimEapMethod(targetedNetwork.enterpriseConfig.getEapMethod()) && !TextUtils.isEmpty(targetedNetwork.enterpriseConfig.getAnonymousIdentity())) {
                        ClientModeImpl.this.log("EAP Pseudonym reset due to AUTHENTICATION_FAILURE");
                        targetedNetwork.enterpriseConfig.setAnonymousIdentity(null);
                        ClientModeImpl.this.mWifiConfigManager.addOrUpdateNetwork(targetedNetwork, 1010);
                    }
                    ClientModeImpl.this.mWifiConfigManager.updateNetworkSelectionStatus(ClientModeImpl.this.mTargetNetworkId, disableReason);
                    ClientModeImpl.this.mWifiConfigManager.clearRecentFailureReason(ClientModeImpl.this.mTargetNetworkId);
                    if (reasonCode2 == 0) {
                        level2FailureReason = 1;
                    } else if (reasonCode2 == 1) {
                        level2FailureReason = 2;
                    } else if (reasonCode2 == 2) {
                        level2FailureReason = 3;
                    } else if (reasonCode2 != 3) {
                        level2FailureReason = 0;
                    } else {
                        level2FailureReason = 4;
                    }
                    ClientModeImpl.this.reportConnectionAttemptEnd(3, 1, level2FailureReason);
                    if (reasonCode2 != 2) {
                        ClientModeImpl.this.mWifiInjector.getWifiLastResortWatchdog().noteConnectionFailureAndTriggerIfNeeded(ClientModeImpl.this.getTargetSsid(), ClientModeImpl.this.mTargetRoamBSSID, 2);
                    }
                    if (targetedNetwork == null || targetedNetwork.SSID == null) {
                        logMessage = "Wi-Fi is failed to connect to " + ClientModeImpl.this.mWifiInfo.getSSID() + " network.";
                    } else {
                        logMessage = "Wi-Fi is failed to connect to " + targetedNetwork.getPrintableSsid() + " network using ";
                        if (targetedNetwork.allowedKeyManagement.get(0) || targetedNetwork.allowedKeyManagement.get(1) || targetedNetwork.allowedKeyManagement.get(4) || targetedNetwork.allowedKeyManagement.get(22)) {
                            logMessage = logMessage + "802.11-2012 channel.";
                        } else if (targetedNetwork.enterpriseConfig != null) {
                            if (targetedNetwork.enterpriseConfig.getEapMethod() == 1) {
                                logMessage = logMessage + "EAP-TLS channel";
                                Log.e(ClientModeImpl.TAG, "Wi-Fi is failed to connect to " + targetedNetwork.getPrintableSsid() + " network using EAP-TLS channel");
                            } else {
                                logMessage = logMessage + "802.1X channel";
                                Log.e(ClientModeImpl.TAG, "Wi-Fi is connected to " + targetedNetwork.getPrintableSsid() + " network using 802.1X channel");
                            }
                        }
                    }
                    WifiMobileDeviceManager.auditLog(ClientModeImpl.this.mContext, 5, false, ClientModeImpl.TAG, logMessage + " Reason: Authentication failure.");
                    ClientModeImpl.this.notifyDisconnectInternalReason(13);
                    if (!(ClientModeImpl.this.mTargetNetworkId == -1 || targetedNetwork == null)) {
                        ClientModeImpl clientModeImpl19 = ClientModeImpl.this;
                        clientModeImpl19.report(15, ReportUtil.getReportDataForAuthFail(clientModeImpl19.mTargetNetworkId, message.arg2, targetedNetwork.status, targetedNetwork.numAssociation, targetedNetwork.getNetworkSelectionStatus().getNetworkSelectionStatus(), targetedNetwork.getNetworkSelectionStatus().getNetworkSelectionDisableReason()));
                        break;
                    }
                    break;
                case WifiMonitor.SUP_REQUEST_IDENTITY /*{ENCODED_INT: 147471}*/:
                    int netId4 = message.arg2;
                    boolean identitySent = false;
                    identitySent = false;
                    identitySent = false;
                    identitySent = false;
                    if (ClientModeImpl.this.mTargetWifiConfiguration != null && ClientModeImpl.this.mTargetWifiConfiguration.networkId == netId4 && TelephonyUtil.isSimConfig(ClientModeImpl.this.mTargetWifiConfiguration)) {
                        Pair<String, String> identityPair = TelephonyUtil.getSimIdentity(ClientModeImpl.this.getTelephonyManager(), new TelephonyUtil(), ClientModeImpl.this.mTargetWifiConfiguration, ClientModeImpl.this.mWifiInjector.getCarrierNetworkConfig());
                        if (identityPair == null || identityPair.first == null) {
                            Log.e(ClientModeImpl.TAG, "Unable to retrieve identity from Telephony");
                        } else {
                            Log.i(ClientModeImpl.TAG, "SUP_REQUEST_IDENTITY: identity =" + ((String) identityPair.first).substring(0, 7));
                            ClientModeImpl.this.mWifiNative.simIdentityResponse(ClientModeImpl.this.mInterfaceName, netId4, (String) identityPair.first, (String) identityPair.second);
                            identitySent = true;
                            identitySent = true;
                            if (TextUtils.isEmpty((CharSequence) identityPair.second)) {
                                ClientModeImpl clientModeImpl20 = ClientModeImpl.this;
                                clientModeImpl20.updateIdentityOnWifiConfiguration(clientModeImpl20.mTargetWifiConfiguration, (String) identityPair.first);
                            }
                        }
                    }
                    if (!identitySent) {
                        String ssid = (String) message.obj;
                        if (!(ClientModeImpl.this.mTargetWifiConfiguration == null || ssid == null || ClientModeImpl.this.mTargetWifiConfiguration.SSID == null || !ClientModeImpl.this.mTargetWifiConfiguration.SSID.equals(ssid))) {
                            ClientModeImpl.this.mWifiConfigManager.updateNetworkSelectionStatus(ClientModeImpl.this.mTargetWifiConfiguration.networkId, 9);
                        }
                        ClientModeImpl.this.mWifiMetrics.logStaEvent(15, 2);
                        break;
                    }
                    break;
                case WifiMonitor.SUP_REQUEST_SIM_AUTH /*{ENCODED_INT: 147472}*/:
                    ClientModeImpl.this.logd("Received SUP_REQUEST_SIM_AUTH");
                    TelephonyUtil.SimAuthRequestData requestData = (TelephonyUtil.SimAuthRequestData) message.obj;
                    if (requestData != null) {
                        if (requestData.protocol != 4) {
                            if (requestData.protocol == 5 || requestData.protocol == 6) {
                                ClientModeImpl.this.handle3GAuthRequest(requestData);
                                break;
                            }
                        } else {
                            ClientModeImpl.this.handleGsmAuthRequest(requestData);
                            break;
                        }
                    } else {
                        ClientModeImpl.this.loge("Invalid SIM auth request");
                        break;
                    }
                case 147499:
                    ClientModeImpl.this.mWifiDiagnostics.triggerBugReportDataCapture(1);
                    ClientModeImpl.this.mDidBlackListBSSID = false;
                    String bssid3 = (String) message.obj;
                    boolean timedOut = message.arg1 > 0;
                    int reasonCode3 = message.arg2;
                    Log.d(ClientModeImpl.TAG, "Association Rejection event: bssid=" + bssid3 + " reason code=" + reasonCode3 + " timedOut=" + Boolean.toString(timedOut));
                    if (bssid3 == null || TextUtils.isEmpty(bssid3)) {
                        bssid3 = ClientModeImpl.this.mTargetRoamBSSID;
                    }
                    if (bssid3 != null) {
                        ClientModeImpl clientModeImpl21 = ClientModeImpl.this;
                        clientModeImpl21.mDidBlackListBSSID = clientModeImpl21.mWifiConnectivityManager.trackBssid(bssid3, false, reasonCode3);
                    }
                    ClientModeImpl.this.mWifiConfigManager.updateNetworkSelectionStatus(ClientModeImpl.this.mTargetNetworkId, 2);
                    ClientModeImpl.this.mWifiConfigManager.setRecentFailureAssociationStatus(ClientModeImpl.this.mTargetNetworkId, reasonCode3);
                    ClientModeImpl.this.mSupplicantStateTracker.sendMessage(147499);
                    ClientModeImpl clientModeImpl22 = ClientModeImpl.this;
                    if (timedOut) {
                        i = 11;
                    }
                    clientModeImpl22.reportConnectionAttemptEnd(i, 1, 0);
                    ClientModeImpl.this.mWifiInjector.getWifiLastResortWatchdog().noteConnectionFailureAndTriggerIfNeeded(ClientModeImpl.this.getTargetSsid(), bssid3, 1);
                    ClientModeImpl.this.notifyDisconnectInternalReason(12);
                    if (!(ClientModeImpl.this.mTargetNetworkId == -1 || (config2 = ClientModeImpl.this.mWifiConfigManager.getConfiguredNetwork(ClientModeImpl.this.mTargetNetworkId)) == null)) {
                        ClientModeImpl clientModeImpl23 = ClientModeImpl.this;
                        clientModeImpl23.report(14, ReportUtil.getReportDataForAssocReject(clientModeImpl23.mTargetNetworkId, bssid3, reasonCode3, config2.status, config2.numAssociation, config2.getNetworkSelectionStatus().getNetworkSelectionStatus(), config2.getNetworkSelectionStatus().getNetworkSelectionDisableReason()));
                        break;
                    }
                case WifiMonitor.ANQP_DONE_EVENT /*{ENCODED_INT: 147500}*/:
                    ClientModeImpl.this.mPasspointManager.notifyANQPDone((AnqpEvent) message.obj);
                    break;
                case WifiMonitor.BSSID_PRUNED_EVENT /*{ENCODED_INT: 147501}*/:
                    ClientModeImpl.this.mDidBlackListBSSID = false;
                    String str_obj = (String) message.obj;
                    if (str_obj != null) {
                        String[] tokens = str_obj.split("\\s");
                        if (tokens.length == 4) {
                            String ssid2 = tokens[0];
                            String bssid4 = tokens[1];
                            int timeRemaining = Integer.MIN_VALUE;
                            try {
                                int reasonCode4 = Integer.parseInt(tokens[2]) + 65536;
                                if (reasonCode4 == 65537 || reasonCode4 == 65538) {
                                    timeRemaining = Integer.parseInt(tokens[3]) * 1000;
                                }
                                Log.d(ClientModeImpl.TAG, "Bssid Pruned event: ssid=" + ssid2 + " bssid=" + bssid4 + " reason code=" + reasonCode4 + " timeRemaining=" + timeRemaining);
                                if (bssid4 == null || TextUtils.isEmpty(bssid4)) {
                                    bssid4 = ClientModeImpl.this.mTargetRoamBSSID;
                                }
                                if (bssid4 != null) {
                                    WifiConfiguration config7 = ClientModeImpl.this.mWifiConfigManager.getConfiguredNetwork(ClientModeImpl.this.mTargetNetworkId);
                                    if (config7 != null && !"any".equals(config7.getNetworkSelectionStatus().getNetworkSelectionBSSID())) {
                                        ClientModeImpl.this.mWifiNative.removeNetworkIfCurrent(ClientModeImpl.this.mInterfaceName, ClientModeImpl.this.mTargetNetworkId);
                                        Log.d(ClientModeImpl.TAG, "Bssid Pruned event: remove networkid=" + ClientModeImpl.this.mTargetNetworkId + " from supplicant");
                                    }
                                    ClientModeImpl clientModeImpl24 = ClientModeImpl.this;
                                    clientModeImpl24.mDidBlackListBSSID = clientModeImpl24.mWifiConnectivityManager.trackBssid(bssid4, false, reasonCode4, timeRemaining);
                                    break;
                                }
                            } catch (NumberFormatException e3) {
                                Log.e(ClientModeImpl.TAG, "Bssid Pruned event: wrong reasonCode or timeRemaining!" + e3);
                                break;
                            }
                        } else {
                            Log.e(ClientModeImpl.TAG, "Bssid Pruned event: wrong string obj format!");
                            break;
                        }
                    } else {
                        Log.e(ClientModeImpl.TAG, "Bssid Pruned event: no obj in message!");
                        break;
                    }
                    break;
                case WifiMonitor.RX_HS20_ANQP_ICON_EVENT /*{ENCODED_INT: 147509}*/:
                    ClientModeImpl.this.mPasspointManager.notifyIconDone((IconEvent) message.obj);
                    break;
                case WifiMonitor.HS20_REMEDIATION_EVENT /*{ENCODED_INT: 147517}*/:
                    ClientModeImpl.this.mPasspointManager.receivedWnmFrame((WnmData) message.obj);
                    break;
                case 151553:
                    int netId5 = message.arg1;
                    WifiConfiguration config8 = (WifiConfiguration) message.obj;
                    boolean hasCredentialChanged = false;
                    boolean isNewNetwork = false;
                    isNewNetwork = false;
                    if (config8 != null) {
                        config8.priority = ClientModeImpl.this.mWifiConfigManager.increaseAndGetPriority();
                        boolean isAllowed = WifiPolicyCache.getInstance(ClientModeImpl.this.mContext).isNetworkAllowed(config8, false);
                        ClientModeImpl.this.logd("CONNECT_NETWORK isAllowed=" + isAllowed);
                        if (!isAllowed) {
                            WifiMobileDeviceManager.auditLog(ClientModeImpl.this.mContext, 3, false, ClientModeImpl.TAG, "Connecting to Wi-Fi network whose ID is " + netId5 + " failed");
                            ClientModeImpl.this.mMessageHandlingStatus = -2;
                            ClientModeImpl.this.replyToMessage((ClientModeImpl) message, (Message) 151554, 9);
                            break;
                        } else {
                            if (!config8.isEphemeral()) {
                                ClientModeImpl.this.mWifiConfigManager.updateBssidWhitelist(config8, ClientModeImpl.this.mScanRequestProxy.getScanResults());
                            }
                            NetworkUpdateResult result = ClientModeImpl.this.mWifiConfigManager.addOrUpdateNetwork(config8, message.sendingUid);
                            if (!result.isSuccess()) {
                                ClientModeImpl.this.loge("CONNECT_NETWORK adding/updating config=" + config8 + " failed");
                                ClientModeImpl.this.mMessageHandlingStatus = -2;
                                ClientModeImpl.this.replyToMessage((ClientModeImpl) message, (Message) 151554, 0);
                                break;
                            } else if (("VZW".equals(ClientModeImpl.CSC_CONFIG_OP_BRANDING) || "SKT".equals(ClientModeImpl.CSC_CONFIG_OP_BRANDING)) && !TextUtils.isEmpty(config8.SSID) && ClientModeImpl.this.checkAndShowSimRemovedDialog(config8)) {
                                ClientModeImpl.this.mMessageHandlingStatus = -2;
                                ClientModeImpl.this.replyToMessage((ClientModeImpl) message, (Message) 151554, 0);
                                break;
                            } else {
                                netId5 = result.getNetworkId();
                                if (OpBrandingLoader.Vendor.VZW == ClientModeImpl.mOpBranding) {
                                    isNewNetwork = result.isNewNetwork();
                                }
                                hasCredentialChanged = result.hasCredentialChanged();
                            }
                        }
                    }
                    if (!(ClientModeImpl.this.mLastNetworkId == -1 || ClientModeImpl.this.mLastNetworkId == netId5)) {
                        ClientModeImpl.this.notifyDisconnectInternalReason(3);
                    }
                    if (ClientModeImpl.this.connectToUserSelectNetwork(netId5, message.sendingUid, hasCredentialChanged)) {
                        if (OpBrandingLoader.Vendor.VZW == ClientModeImpl.mOpBranding && isNewNetwork && ClientModeImpl.this.mSemWifiHiddenNetworkTracker != null && config8 != null && config8.hiddenSSID) {
                            ClientModeImpl.this.mSemWifiHiddenNetworkTracker.startTracking(config8);
                        }
                        ClientModeImpl.this.mWifiMetrics.logStaEvent(13, config8);
                        ClientModeImpl.this.broadcastWifiCredentialChanged(0, config8);
                        ClientModeImpl.this.replyToMessage(message, 151555);
                        break;
                    } else {
                        ClientModeImpl.this.mMessageHandlingStatus = -2;
                        ClientModeImpl.this.replyToMessage((ClientModeImpl) message, (Message) 151554, 9);
                        break;
                    }
                    break;
                case 151556:
                    int netId6 = message.arg1;
                    WifiConfiguration toRemove = ClientModeImpl.this.mWifiConfigManager.getConfiguredNetwork(netId6);
                    if (ClientModeImpl.this.mWifiGeofenceManager.isSupported()) {
                        ClientModeImpl.this.mWifiGeofenceManager.forgetNetwork(toRemove);
                    }
                    if (!(WifiRoamingAssistant.getInstance() == null || toRemove == null)) {
                        WifiRoamingAssistant.getInstance().forgetNetwork(toRemove.configKey());
                    }
                    if (ClientModeImpl.this.deleteNetworkConfigAndSendReply(message, true)) {
                        if (ClientModeImpl.ENBLE_WLAN_CONFIG_ANALYTICS) {
                            ClientModeImpl.this.setAnalyticsUserDisconnectReason(WifiNative.ANALYTICS_DISCONNECT_REASON_USER_TRIGGER_DISCON_REMOVE_PROFILE);
                        }
                        if (netId6 == ClientModeImpl.this.mTargetNetworkId || netId6 == ClientModeImpl.this.mLastNetworkId) {
                            ClientModeImpl.this.disconnectCommand(0, 18);
                            break;
                        }
                    }
                    break;
                case 151559:
                    NetworkUpdateResult result2 = ClientModeImpl.this.saveNetworkConfigAndSendReply(message);
                    int netId7 = result2.getNetworkId();
                    if (result2.isSuccess() && ClientModeImpl.this.mWifiInfo.getNetworkId() == netId7) {
                        if (!result2.hasCredentialChanged()) {
                            if (result2.hasProxyChanged() && ClientModeImpl.this.mIpClient != null) {
                                ClientModeImpl.this.log("Reconfiguring proxy on connection");
                                WifiConfiguration currentConfig2 = ClientModeImpl.this.getCurrentWifiConfiguration();
                                if (currentConfig2 != null) {
                                    ClientModeImpl.this.mIpClient.setHttpProxy(currentConfig2.getHttpProxy());
                                } else {
                                    Log.w(ClientModeImpl.TAG, "CMD_SAVE_NETWORK proxy change - but no current Wi-Fi config");
                                }
                            }
                            if (result2.hasIpChanged()) {
                                ClientModeImpl.this.log("Reconfiguring IP on connection");
                                if (ClientModeImpl.this.getCurrentWifiConfiguration() == null) {
                                    Log.w(ClientModeImpl.TAG, "CMD_SAVE_NETWORK Ip change - but no current Wi-Fi config");
                                    break;
                                } else {
                                    ClientModeImpl clientModeImpl25 = ClientModeImpl.this;
                                    clientModeImpl25.transitionTo(clientModeImpl25.mObtainingIpState);
                                    break;
                                }
                            }
                        } else {
                            ClientModeImpl.this.logi("SAVE_NETWORK credential changed for config=" + ((WifiConfiguration) message.obj).configKey() + ", Reconnecting.");
                            ClientModeImpl.this.startConnectToNetwork(netId7, message.sendingUid, "any");
                            break;
                        }
                    }
                    break;
                case 151569:
                    int netId8 = message.arg1;
                    boolean replyDone = false;
                    replyDone = false;
                    if (ClientModeImpl.this.mWifiConfigManager.canDisableNetwork(netId8, message.sendingUid)) {
                        if ((netId8 == ClientModeImpl.this.mTargetNetworkId || netId8 == ClientModeImpl.this.mLastNetworkId) && ClientModeImpl.this.getCurrentState() == ClientModeImpl.this.mConnectedState) {
                            ClientModeImpl.this.replyToMessage(message, 151571);
                            replyDone = true;
                            ClientModeImpl.this.notifyDisconnectInternalReason(19);
                            ClientModeImpl.this.mDelayDisconnect.checkAndWait(ClientModeImpl.this.mNetworkInfo);
                        }
                        if (!ClientModeImpl.this.mWifiConfigManager.disableNetwork(netId8, message.sendingUid)) {
                            ClientModeImpl.this.loge("Failed to disable network");
                            ClientModeImpl.this.mMessageHandlingStatus = -2;
                            if (!replyDone) {
                                ClientModeImpl.this.replyToMessage((ClientModeImpl) message, (Message) 151570, 0);
                                break;
                            }
                        } else {
                            if (!replyDone) {
                                ClientModeImpl.this.replyToMessage(message, 151571);
                            }
                            if (netId8 == ClientModeImpl.this.mTargetNetworkId || netId8 == ClientModeImpl.this.mLastNetworkId) {
                                ClientModeImpl.this.disconnectCommand(0, 19);
                                break;
                            }
                        }
                    } else {
                        ClientModeImpl.this.loge("Failed to disable network");
                        ClientModeImpl.this.mMessageHandlingStatus = -2;
                        ClientModeImpl.this.replyToMessage((ClientModeImpl) message, (Message) 151570, 0);
                        break;
                    }
                    break;
                default:
                    handleStatus = false;
                    break;
            }
            if (handleStatus) {
                ClientModeImpl.this.logStateAndMessage(message, this);
            }
            return handleStatus;
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean isRoamNetwork() {
        int configuredSecurity;
        if (this.mIsRoamNetwork) {
            return true;
        }
        List<ScanResult> scanResults = new ArrayList<>();
        scanResults.addAll(this.mScanRequestProxy.getScanResults());
        WifiConfiguration currConfig = this.mWifiConfigManager.getConfiguredNetwork(this.mWifiInfo.getNetworkId());
        if (currConfig != null) {
            String configSsid = currConfig.SSID;
            if (currConfig.allowedKeyManagement.get(1)) {
                configuredSecurity = 2;
            } else if (currConfig.allowedKeyManagement.get(8)) {
                configuredSecurity = 4;
            } else if (currConfig.allowedKeyManagement.get(2) || currConfig.allowedKeyManagement.get(3)) {
                configuredSecurity = 3;
            } else {
                configuredSecurity = currConfig.wepKeys[0] != null ? 1 : 0;
            }
            for (ScanResult scanResult : scanResults) {
                int scanedSecurity = 0;
                if (scanResult.capabilities.contains("WEP")) {
                    scanedSecurity = 1;
                } else if (scanResult.capabilities.contains("SAE")) {
                    scanedSecurity = 4;
                } else if (scanResult.capabilities.contains("PSK")) {
                    scanedSecurity = 2;
                } else if (scanResult.capabilities.contains("EAP")) {
                    scanedSecurity = 3;
                }
                if (!(scanResult.SSID == null || configSsid == null || configSsid.length() <= 2 || !scanResult.SSID.equals(configSsid.substring(1, configSsid.length() - 1)) || configuredSecurity != scanedSecurity || scanResult.BSSID == null || scanResult.BSSID.equals(this.mWifiInfo.getBSSID()))) {
                    return true;
                }
            }
        }
        return false;
    }

    private WifiNetworkAgentSpecifier createNetworkAgentSpecifier(WifiConfiguration currentWifiConfiguration, String currentBssid, int specificRequestUid, String specificRequestPackageName) {
        currentWifiConfiguration.BSSID = currentBssid;
        return new WifiNetworkAgentSpecifier(currentWifiConfiguration, specificRequestUid, specificRequestPackageName);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private NetworkCapabilities getCapabilities(WifiConfiguration currentWifiConfiguration) {
        NetworkCapabilities result = new NetworkCapabilities(this.mNetworkCapabilitiesFilter);
        result.setNetworkSpecifier(null);
        if (currentWifiConfiguration == null) {
            return result;
        }
        if (!this.mWifiInfo.isTrusted()) {
            result.removeCapability(14);
        } else {
            result.addCapability(14);
        }
        if (!WifiConfiguration.isMetered(currentWifiConfiguration, this.mWifiInfo)) {
            result.addCapability(11);
        } else {
            result.removeCapability(11);
        }
        if (this.mWifiInfo.getRssi() != -127) {
            result.setSignalStrength(this.mWifiInfo.getRssi());
        } else {
            result.setSignalStrength(Integer.MIN_VALUE);
        }
        if (currentWifiConfiguration.osu) {
            result.removeCapability(12);
        }
        if (!this.mWifiInfo.getSSID().equals(MobileWipsWifiSsid.NONE)) {
            result.setSSID(this.mWifiInfo.getSSID());
        } else {
            result.setSSID(null);
        }
        Pair<Integer, String> specificRequestUidAndPackageName = this.mNetworkFactory.getSpecificNetworkRequestUidAndPackageName(currentWifiConfiguration);
        if (((Integer) specificRequestUidAndPackageName.first).intValue() != -1) {
            result.removeCapability(12);
        }
        result.setNetworkSpecifier(createNetworkAgentSpecifier(currentWifiConfiguration, getCurrentBSSID(), ((Integer) specificRequestUidAndPackageName.first).intValue(), (String) specificRequestUidAndPackageName.second));
        return result;
    }

    public void updateCapabilities() {
        updateCapabilities(getCurrentWifiConfiguration());
    }

    private void updateCapabilities(WifiConfiguration currentWifiConfiguration) {
        WifiNetworkAgent wifiNetworkAgent = this.mNetworkAgent;
        if (wifiNetworkAgent != null) {
            wifiNetworkAgent.sendNetworkCapabilities(getCapabilities(currentWifiConfiguration));
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean isProviderOwnedNetwork(int networkId, String providerFqdn) {
        WifiConfiguration config;
        if (networkId == -1 || (config = this.mWifiConfigManager.getConfiguredNetwork(networkId)) == null) {
            return false;
        }
        return TextUtils.equals(config.FQDN, providerFqdn);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void handleEapAuthFailure(int networkId, int errorCode) {
        WifiConfiguration targetedNetwork = this.mWifiConfigManager.getConfiguredNetwork(this.mTargetNetworkId);
        if (targetedNetwork != null) {
            int eapMethod = targetedNetwork.enterpriseConfig.getEapMethod();
            if (eapMethod == 4 || eapMethod == 5 || eapMethod == 6) {
                if (errorCode == 16385) {
                    getTelephonyManager().createForSubscriptionId(SubscriptionManager.getDefaultDataSubscriptionId()).resetCarrierKeysForImsiEncryption();
                }
                if (CSC_WIFI_ERRORCODE) {
                    showEapNotificationToast(errorCode);
                }
            }
        }
    }

    /* access modifiers changed from: private */
    public class WifiNetworkAgent extends NetworkAgent {
        private int mLastNetworkStatus = -1;

        WifiNetworkAgent(Looper l, Context c, String tag, NetworkInfo ni, NetworkCapabilities nc, LinkProperties lp, int score, NetworkMisc misc) {
            super(l, c, tag, ni, nc, lp, score, misc);
        }

        /* access modifiers changed from: protected */
        public void unwanted() {
            if (this == ClientModeImpl.this.mNetworkAgent) {
                if (ClientModeImpl.this.mVerboseLoggingEnabled) {
                    log("WifiNetworkAgent -> Wifi unwanted score " + Integer.toString(ClientModeImpl.this.mWifiInfo.score));
                }
                ClientModeImpl.this.unwantedNetwork(0);
            }
        }

        /* access modifiers changed from: protected */
        public void networkStatus(int status, String redirectUrl) {
            if (this == ClientModeImpl.this.mNetworkAgent && status != this.mLastNetworkStatus) {
                this.mLastNetworkStatus = status;
                if (status == 2) {
                    if (ClientModeImpl.this.mVerboseLoggingEnabled) {
                        log("WifiNetworkAgent -> Wifi networkStatus invalid, score=" + Integer.toString(ClientModeImpl.this.mWifiInfo.score));
                    }
                    ClientModeImpl.this.unwantedNetwork(1);
                } else if (status == 1) {
                    if (ClientModeImpl.this.mVerboseLoggingEnabled) {
                        log("WifiNetworkAgent -> Wifi networkStatus valid, score= " + Integer.toString(ClientModeImpl.this.mWifiInfo.score));
                    }
                    ClientModeImpl.this.mWifiMetrics.logStaEvent(14);
                    ClientModeImpl.this.doNetworkStatus(status);
                }
            }
        }

        /* access modifiers changed from: protected */
        public void saveAcceptUnvalidated(boolean accept) {
            if (this == ClientModeImpl.this.mNetworkAgent) {
                ClientModeImpl.this.sendMessage(ClientModeImpl.CMD_ACCEPT_UNVALIDATED, accept ? 1 : 0);
            }
        }

        /* access modifiers changed from: protected */
        public void startSocketKeepalive(Message msg) {
            ClientModeImpl.this.sendMessage(ClientModeImpl.CMD_START_IP_PACKET_OFFLOAD, msg.arg1, msg.arg2, msg.obj);
        }

        /* access modifiers changed from: protected */
        public void stopSocketKeepalive(Message msg) {
            ClientModeImpl.this.sendMessage(ClientModeImpl.CMD_STOP_IP_PACKET_OFFLOAD, msg.arg1, msg.arg2, msg.obj);
        }

        /* access modifiers changed from: protected */
        public void addKeepalivePacketFilter(Message msg) {
            ClientModeImpl.this.sendMessage(ClientModeImpl.CMD_ADD_KEEPALIVE_PACKET_FILTER_TO_APF, msg.arg1, msg.arg2, msg.obj);
        }

        /* access modifiers changed from: protected */
        public void removeKeepalivePacketFilter(Message msg) {
            ClientModeImpl.this.sendMessage(ClientModeImpl.CMD_REMOVE_KEEPALIVE_PACKET_FILTER_FROM_APF, msg.arg1, msg.arg2, msg.obj);
        }

        /* access modifiers changed from: protected */
        public void setSignalStrengthThresholds(int[] thresholds) {
            log("Received signal strength thresholds: " + Arrays.toString(thresholds));
            if (thresholds.length == 0) {
                boolean isKnoxCustomAutoSwitchEnabled = false;
                CustomDeviceManagerProxy customDeviceManager = CustomDeviceManagerProxy.getInstance();
                if (customDeviceManager != null && customDeviceManager.getWifiAutoSwitchState()) {
                    isKnoxCustomAutoSwitchEnabled = true;
                    if (ClientModeImpl.DBG) {
                        ClientModeImpl.this.logd("KnoxCustom WifiAutoSwitch: not stopping RSSI monitoring");
                    }
                }
                if (!isKnoxCustomAutoSwitchEnabled) {
                    ClientModeImpl clientModeImpl = ClientModeImpl.this;
                    clientModeImpl.sendMessage(ClientModeImpl.CMD_STOP_RSSI_MONITORING_OFFLOAD, clientModeImpl.mWifiInfo.getRssi());
                    return;
                }
            }
            int[] rssiVals = Arrays.copyOf(thresholds, thresholds.length + 2);
            rssiVals[rssiVals.length - 2] = -128;
            rssiVals[rssiVals.length - 1] = 127;
            Arrays.sort(rssiVals);
            byte[] rssiRange = new byte[rssiVals.length];
            for (int i = 0; i < rssiVals.length; i++) {
                int val = rssiVals[i];
                if (val > 127 || val < -128) {
                    Log.e(ClientModeImpl.TAG, "Illegal value " + val + " for RSSI thresholds: " + Arrays.toString(rssiVals));
                    ClientModeImpl clientModeImpl2 = ClientModeImpl.this;
                    clientModeImpl2.sendMessage(ClientModeImpl.CMD_STOP_RSSI_MONITORING_OFFLOAD, clientModeImpl2.mWifiInfo.getRssi());
                    return;
                }
                rssiRange[i] = (byte) val;
            }
            ClientModeImpl.this.mRssiRanges = rssiRange;
            ClientModeImpl clientModeImpl3 = ClientModeImpl.this;
            clientModeImpl3.sendMessage(ClientModeImpl.CMD_START_RSSI_MONITORING_OFFLOAD, clientModeImpl3.mWifiInfo.getRssi());
        }

        /* access modifiers changed from: protected */
        public void preventAutomaticReconnect() {
            if (this == ClientModeImpl.this.mNetworkAgent) {
                ClientModeImpl.this.unwantedNetwork(2);
            }
        }
    }

    /* access modifiers changed from: package-private */
    public void unwantedNetwork(int reason) {
        sendMessage(CMD_UNWANTED_NETWORK, reason);
    }

    /* access modifiers changed from: package-private */
    public void doNetworkStatus(int status) {
        sendMessage(CMD_NETWORK_STATUS, status);
    }

    private String buildIdentity(int eapMethod, String imsi, String mccMnc) {
        String prefix;
        String mnc;
        String mcc;
        if (imsi == null || imsi.isEmpty()) {
            return "";
        }
        if (eapMethod == 4) {
            prefix = "1";
        } else if (eapMethod == 5) {
            prefix = "0";
        } else if (eapMethod != 6) {
            return "";
        } else {
            prefix = "6";
        }
        if (mccMnc == null || mccMnc.isEmpty()) {
            mcc = imsi.substring(0, 3);
            mnc = imsi.substring(3, 6);
        } else {
            mcc = mccMnc.substring(0, 3);
            mnc = mccMnc.substring(3);
            if (mnc.length() == 2) {
                mnc = "0" + mnc;
            }
        }
        return prefix + imsi + "@wlan.mnc" + mnc + ".mcc" + mcc + ".3gppnetwork.org";
    }

    class L2ConnectedState extends State {
        RssiEventHandler mRssiEventHandler = new RssiEventHandler();

        class RssiEventHandler implements WifiNative.WifiRssiEventHandler {
            RssiEventHandler() {
            }

            @Override // com.android.server.wifi.WifiNative.WifiRssiEventHandler
            public void onRssiThresholdBreached(byte curRssi) {
                if (ClientModeImpl.this.mVerboseLoggingEnabled) {
                    Log.e(ClientModeImpl.TAG, "onRssiThresholdBreach event. Cur Rssi = " + ((int) curRssi));
                }
                ClientModeImpl.this.sendMessage(ClientModeImpl.CMD_RSSI_THRESHOLD_BREACHED, curRssi);
            }
        }

        L2ConnectedState() {
        }

        public void enter() {
            ClientModeImpl.access$16508(ClientModeImpl.this);
            if (ClientModeImpl.this.mEnableRssiPolling) {
                ClientModeImpl.this.mLinkProbeManager.resetOnNewConnection();
                ClientModeImpl clientModeImpl = ClientModeImpl.this;
                clientModeImpl.sendMessage(ClientModeImpl.CMD_RSSI_POLL, clientModeImpl.mRssiPollToken, 0);
            }
            if (ClientModeImpl.this.mNetworkAgent != null) {
                ClientModeImpl.this.loge("Have NetworkAgent when entering L2Connected");
                ClientModeImpl.this.setNetworkDetailedState(NetworkInfo.DetailedState.DISCONNECTED);
            }
            ClientModeImpl.this.setNetworkDetailedState(NetworkInfo.DetailedState.CONNECTING);
            ClientModeImpl.this.handleWifiNetworkCallbacks(1);
            ClientModeImpl clientModeImpl2 = ClientModeImpl.this;
            NetworkCapabilities nc = clientModeImpl2.getCapabilities(clientModeImpl2.getCurrentWifiConfiguration());
            synchronized (ClientModeImpl.this.mNetworkAgentLock) {
                ClientModeImpl.this.mNetworkAgent = new WifiNetworkAgent(ClientModeImpl.this.getHandler().getLooper(), ClientModeImpl.this.mContext, "WifiNetworkAgent", ClientModeImpl.this.mNetworkInfo, nc, ClientModeImpl.this.mLinkProperties, 60, ClientModeImpl.this.mNetworkMisc);
                ClientModeImpl.this.mWifiScoreReport.setNeteworkAgent(ClientModeImpl.this.mNetworkAgent);
            }
            ClientModeImpl.this.clearTargetBssid("L2ConnectedState");
            ClientModeImpl.this.mCountryCode.setReadyForChange(false);
            ClientModeImpl.this.mWifiMetrics.setWifiState(3);
            ClientModeImpl.this.mWifiScoreCard.noteNetworkAgentCreated(ClientModeImpl.this.mWifiInfo, ClientModeImpl.this.mNetworkAgent.netId);
            ClientModeImpl.this.isDhcpStartSent = false;
        }

        public void exit() {
            if (ClientModeImpl.this.mIpClient != null) {
                ClientModeImpl.this.mIpClient.stop();
            }
            if (ClientModeImpl.this.mVerboseLoggingEnabled) {
                StringBuilder sb = new StringBuilder();
                sb.append("leaving L2ConnectedState state nid=" + Integer.toString(ClientModeImpl.this.mLastNetworkId));
                if (ClientModeImpl.this.mLastBssid != null) {
                    sb.append(" ");
                    sb.append(ClientModeImpl.this.mLastBssid);
                }
            }
            if (!(ClientModeImpl.this.mLastBssid == null && ClientModeImpl.this.mLastNetworkId == -1)) {
                ClientModeImpl.this.handleNetworkDisconnect();
            }
            ClientModeImpl.this.mCountryCode.setReadyForChange(true);
            ClientModeImpl.this.mWifiMetrics.setWifiState(2);
            ClientModeImpl.this.mWifiStateTracker.updateState(2);
            ClientModeImpl.this.mWifiInjector.getWifiLockManager().updateWifiClientConnected(false);
        }

        /* JADX INFO: Multiple debug info for r3v106 int: [D('currRssi' byte), D('slot' int)] */
        /* JADX INFO: Multiple debug info for r3v175 int: [D('info' android.net.wifi.RssiPacketCountInfo), D('netId' int)] */
        public boolean processMessage(Message message) {
            ScanDetailCache scanDetailCache;
            ScanResult scanResult;
            boolean handleStatus = true;
            switch (message.what) {
                case ClientModeImpl.CMD_DISCONNECT /*{ENCODED_INT: 131145}*/:
                    ClientModeImpl.this.mWifiMetrics.logStaEvent(15, 2);
                    ClientModeImpl.this.notifyDisconnectInternalReason(message.arg2);
                    if (ClientModeImpl.this.getCurrentState() == ClientModeImpl.this.mConnectedState) {
                        if (ClientModeImpl.ENBLE_WLAN_CONFIG_ANALYTICS && !ClientModeImpl.this.mSettingsStore.isWifiToggleEnabled()) {
                            ClientModeImpl.this.setAnalyticsUserDisconnectReason(WifiNative.f20xe9115267);
                        }
                        ClientModeImpl.this.mDelayDisconnect.checkAndWait(ClientModeImpl.this.mNetworkInfo);
                    }
                    ClientModeImpl.this.mWifiNative.disconnect(ClientModeImpl.this.mInterfaceName);
                    ClientModeImpl clientModeImpl = ClientModeImpl.this;
                    clientModeImpl.transitionTo(clientModeImpl.mDisconnectingState);
                    break;
                case ClientModeImpl.CMD_RECONNECT /*{ENCODED_INT: 131146}*/:
                    ClientModeImpl.this.log(" Ignore CMD_RECONNECT request because wifi is already connected");
                    break;
                case ClientModeImpl.CMD_ENABLE_RSSI_POLL /*{ENCODED_INT: 131154}*/:
                    ClientModeImpl.this.cleanWifiScore();
                    ClientModeImpl.this.mEnableRssiPolling = message.arg1 == 1;
                    ClientModeImpl.access$16508(ClientModeImpl.this);
                    if (ClientModeImpl.this.mEnableRssiPolling) {
                        ClientModeImpl.this.mLastSignalLevel = -1;
                        ClientModeImpl.this.mLinkProbeManager.resetOnScreenTurnedOn();
                        ClientModeImpl.this.fetchRssiLinkSpeedAndFrequencyNative();
                        ClientModeImpl clientModeImpl2 = ClientModeImpl.this;
                        clientModeImpl2.sendMessageDelayed(clientModeImpl2.obtainMessage(ClientModeImpl.CMD_RSSI_POLL, clientModeImpl2.mRssiPollToken, 0), (long) ClientModeImpl.this.mPollRssiIntervalMsecs);
                        break;
                    }
                    break;
                case ClientModeImpl.CMD_RSSI_POLL /*{ENCODED_INT: 131155}*/:
                    if (message.arg1 == ClientModeImpl.this.mRssiPollToken) {
                        WifiLinkLayerStats stats = updateLinkLayerStatsRssiAndScoreReportInternal();
                        ClientModeImpl.this.mWifiMetrics.updateWifiUsabilityStatsEntries(ClientModeImpl.this.mWifiInfo, stats);
                        if (ClientModeImpl.this.mWifiScoreReport.shouldCheckIpLayer()) {
                            if (ClientModeImpl.this.mIpClient != null) {
                                ClientModeImpl.this.mIpClient.confirmConfiguration();
                            }
                            ClientModeImpl.this.mWifiScoreReport.noteIpCheck();
                        }
                        int statusDataStall = ClientModeImpl.this.mWifiDataStall.checkForDataStall(ClientModeImpl.this.mLastLinkLayerStats, stats);
                        if (statusDataStall != 0) {
                            ClientModeImpl.this.mWifiMetrics.addToWifiUsabilityStatsList(2, ClientModeImpl.convertToUsabilityStatsTriggerType(statusDataStall), -1);
                        }
                        ClientModeImpl.this.mWifiMetrics.incrementWifiLinkLayerUsageStats(stats);
                        ClientModeImpl.this.mLastLinkLayerStats = stats;
                        ClientModeImpl.this.mWifiScoreCard.noteSignalPoll(ClientModeImpl.this.mWifiInfo);
                        ClientModeImpl.this.mLinkProbeManager.updateConnectionStats(ClientModeImpl.this.mWifiInfo, ClientModeImpl.this.mInterfaceName);
                        ClientModeImpl clientModeImpl3 = ClientModeImpl.this;
                        clientModeImpl3.sendMessageDelayed(clientModeImpl3.obtainMessage(ClientModeImpl.CMD_RSSI_POLL, clientModeImpl3.mRssiPollToken, 0), (long) ClientModeImpl.this.mPollRssiIntervalMsecs);
                        if (ClientModeImpl.this.mVerboseLoggingEnabled) {
                            ClientModeImpl clientModeImpl4 = ClientModeImpl.this;
                            clientModeImpl4.sendRssiChangeBroadcast(clientModeImpl4.mWifiInfo.getRssi());
                        }
                        ClientModeImpl.this.mWifiTrafficPoller.notifyOnDataActivity(ClientModeImpl.this.mWifiInfo.txSuccess, ClientModeImpl.this.mWifiInfo.rxSuccess);
                        ClientModeImpl clientModeImpl5 = ClientModeImpl.this;
                        clientModeImpl5.knoxAutoSwitchPolicy(clientModeImpl5.mWifiInfo.getRssi());
                        break;
                    }
                    break;
                case ClientModeImpl.CMD_ONESHOT_RSSI_POLL /*{ENCODED_INT: 131156}*/:
                    if (!ClientModeImpl.this.mEnableRssiPolling) {
                        updateLinkLayerStatsRssiAndScoreReportInternal();
                        break;
                    }
                    break;
                case ClientModeImpl.CMD_RESET_SIM_NETWORKS /*{ENCODED_INT: 131173}*/:
                    if (message.arg1 == 0 && ClientModeImpl.this.mLastNetworkId != -1 && TelephonyUtil.isSimConfig(ClientModeImpl.this.mWifiConfigManager.getConfiguredNetwork(ClientModeImpl.this.mLastNetworkId))) {
                        ClientModeImpl.this.mWifiMetrics.logStaEvent(15, 6);
                        ClientModeImpl.this.notifyDisconnectInternalReason(4);
                        ClientModeImpl.this.mWifiNative.disconnect(ClientModeImpl.this.mInterfaceName);
                        ClientModeImpl clientModeImpl6 = ClientModeImpl.this;
                        clientModeImpl6.transitionTo(clientModeImpl6.mDisconnectingState);
                    }
                    handleStatus = false;
                    break;
                case ClientModeImpl.CMD_IP_CONFIGURATION_SUCCESSFUL /*{ENCODED_INT: 131210}*/:
                    if (ClientModeImpl.this.getCurrentWifiConfiguration() != null) {
                        ClientModeImpl.this.handleSuccessfulIpConfiguration();
                        if (ClientModeImpl.this.isRoaming()) {
                            ClientModeImpl.this.setRoamTriggered(false);
                        }
                        if (ClientModeImpl.this.getCurrentState() == ClientModeImpl.this.mConnectedState) {
                            if (ClientModeImpl.DBG) {
                                ClientModeImpl.this.log("DHCP renew post action!!! - Don't need to make state transition");
                                break;
                            }
                        } else {
                            ClientModeImpl.this.report(ReportIdKey.ID_DHCP_FAIL, ReportUtil.getReportDataForDhcpResult(1));
                            ClientModeImpl.this.sendConnectedState();
                            ClientModeImpl clientModeImpl7 = ClientModeImpl.this;
                            clientModeImpl7.transitionTo(clientModeImpl7.mConnectedState);
                            break;
                        }
                    } else {
                        ClientModeImpl.this.reportConnectionAttemptEnd(6, 1, 0);
                        ClientModeImpl.this.notifyDisconnectInternalReason(22);
                        ClientModeImpl.this.mWifiNative.disconnect(ClientModeImpl.this.mInterfaceName);
                        ClientModeImpl clientModeImpl8 = ClientModeImpl.this;
                        clientModeImpl8.transitionTo(clientModeImpl8.mDisconnectingState);
                        break;
                    }
                    break;
                case ClientModeImpl.CMD_IP_CONFIGURATION_LOST /*{ENCODED_INT: 131211}*/:
                    if (ClientModeImpl.ENBLE_WLAN_CONFIG_ANALYTICS) {
                        ClientModeImpl.this.setAnalyticsUserDisconnectReason(WifiNative.ANALYTICS_DISCONNECT_REASON_DHCP_FAIL_UNSPECIFIED);
                    }
                    ClientModeImpl.this.getWifiLinkLayerStats();
                    ClientModeImpl.this.handleIpConfigurationLost();
                    ClientModeImpl.this.report(ReportIdKey.ID_DHCP_FAIL, ReportUtil.getReportDataForDhcpResult(2));
                    ClientModeImpl.this.reportConnectionAttemptEnd(10, 1, 0);
                    ClientModeImpl.this.mWifiInjector.getWifiLastResortWatchdog().noteConnectionFailureAndTriggerIfNeeded(ClientModeImpl.this.getTargetSsid(), ClientModeImpl.this.mTargetRoamBSSID, 3);
                    ClientModeImpl.this.notifyDisconnectInternalReason(1);
                    ClientModeImpl clientModeImpl9 = ClientModeImpl.this;
                    clientModeImpl9.transitionTo(clientModeImpl9.mDisconnectingState);
                    break;
                case ClientModeImpl.CMD_ASSOCIATED_BSSID /*{ENCODED_INT: 131219}*/:
                    if (((String) message.obj) != null) {
                        if (ClientModeImpl.this.getNetworkDetailedState() == NetworkInfo.DetailedState.CONNECTED || ClientModeImpl.this.getNetworkDetailedState() == NetworkInfo.DetailedState.VERIFYING_POOR_LINK) {
                            ClientModeImpl.this.log("NOT update Last BSSID");
                        } else {
                            ClientModeImpl.this.mLastBssid = (String) message.obj;
                            if (ClientModeImpl.this.mLastBssid != null && (ClientModeImpl.this.mWifiInfo.getBSSID() == null || !ClientModeImpl.this.mLastBssid.equals(ClientModeImpl.this.mWifiInfo.getBSSID()))) {
                                ClientModeImpl.this.mWifiInfo.setBSSID(ClientModeImpl.this.mLastBssid);
                                WifiConfiguration config = ClientModeImpl.this.getCurrentWifiConfiguration();
                                if (!(config == null || (scanDetailCache = ClientModeImpl.this.mWifiConfigManager.getScanDetailCacheForNetwork(config.networkId)) == null || (scanResult = scanDetailCache.getScanResult(ClientModeImpl.this.mLastBssid)) == null)) {
                                    ClientModeImpl.this.mWifiInfo.setFrequency(scanResult.frequency);
                                    ClientModeImpl.this.mWifiInfo.setWifiMode(scanResult.wifiMode);
                                }
                                ClientModeImpl clientModeImpl10 = ClientModeImpl.this;
                                clientModeImpl10.sendNetworkStateChangeBroadcast(clientModeImpl10.mLastBssid);
                            }
                        }
                        ClientModeImpl.this.notifyMobilewipsRoamEvent("start");
                        break;
                    } else {
                        ClientModeImpl.this.logw("Associated command w/o BSSID");
                        break;
                    }
                case ClientModeImpl.CMD_IP_REACHABILITY_LOST /*{ENCODED_INT: 131221}*/:
                    if (ClientModeImpl.this.mVerboseLoggingEnabled && message.obj != null) {
                        ClientModeImpl.this.log((String) message.obj);
                    }
                    ClientModeImpl.this.mWifiDiagnostics.triggerBugReportDataCapture(9);
                    ClientModeImpl.this.mWifiMetrics.logWifiIsUnusableEvent(5);
                    ClientModeImpl.this.mWifiMetrics.addToWifiUsabilityStatsList(2, 5, -1);
                    if (!ClientModeImpl.this.mIpReachabilityDisconnectEnabled) {
                        ClientModeImpl.this.logd("CMD_IP_REACHABILITY_LOST but disconnect disabled -- ignore");
                        ClientModeImpl.this.handleWifiNetworkCallbacks(10);
                        break;
                    } else {
                        ClientModeImpl.this.notifyDisconnectInternalReason(1);
                        ClientModeImpl.this.handleIpReachabilityLost();
                        ClientModeImpl clientModeImpl11 = ClientModeImpl.this;
                        clientModeImpl11.transitionTo(clientModeImpl11.mDisconnectingState);
                        break;
                    }
                case ClientModeImpl.CMD_START_IP_PACKET_OFFLOAD /*{ENCODED_INT: 131232}*/:
                    int slot = message.arg1;
                    int result = ClientModeImpl.this.startWifiIPPacketOffload(slot, (KeepalivePacketData) message.obj, message.arg2);
                    if (ClientModeImpl.this.mNetworkAgent != null) {
                        ClientModeImpl.this.mNetworkAgent.onSocketKeepaliveEvent(slot, result);
                        break;
                    }
                    break;
                case ClientModeImpl.CMD_START_RSSI_MONITORING_OFFLOAD /*{ENCODED_INT: 131234}*/:
                case ClientModeImpl.CMD_RSSI_THRESHOLD_BREACHED /*{ENCODED_INT: 131236}*/:
                    ClientModeImpl.this.processRssiThreshold((byte) message.arg1, message.what, this.mRssiEventHandler);
                    break;
                case ClientModeImpl.CMD_STOP_RSSI_MONITORING_OFFLOAD /*{ENCODED_INT: 131235}*/:
                    ClientModeImpl.this.stopRssiMonitoringOffload();
                    break;
                case ClientModeImpl.CMD_IPV4_PROVISIONING_SUCCESS /*{ENCODED_INT: 131272}*/:
                    ClientModeImpl.this.handleIPv4Success((DhcpResults) message.obj);
                    ClientModeImpl clientModeImpl12 = ClientModeImpl.this;
                    clientModeImpl12.sendNetworkStateChangeBroadcast(clientModeImpl12.mLastBssid);
                    if ("".equals(ClientModeImpl.CONFIG_SECURE_SVC_INTEGRATION) && !SemCscFeature.getInstance().getBoolean(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_DISABLEMWIPS) && MobileWipsFrameworkService.getInstance() != null) {
                        MobileWipsFrameworkService.getInstance().sendEmptyMessage(19);
                    }
                    if (ClientModeImpl.this.mWifiInjector.getSemWifiApChipInfo().supportWifiSharing() && ClientModeImpl.this.mWifiNative.CheckWifiSoftApIpReset()) {
                        Log.d(ClientModeImpl.TAG, "IP Subnet of MobileAp needs to be modified. So Reset Mobile Ap");
                        Intent resetIntent = new Intent("com.samsung.android.intent.action.WIFIAP_RESET");
                        if (ClientModeImpl.this.mContext != null) {
                            ClientModeImpl.this.mContext.sendBroadcast(resetIntent);
                            resetIntent.setClassName("com.android.settings", "com.samsung.android.settings.wifi.mobileap.WifiApBroadcastReceiver");
                            ClientModeImpl.this.mContext.sendBroadcast(resetIntent);
                            break;
                        }
                    }
                    break;
                case ClientModeImpl.CMD_IPV4_PROVISIONING_FAILURE /*{ENCODED_INT: 131273}*/:
                    ClientModeImpl.this.handleIPv4Failure();
                    ClientModeImpl.this.mWifiInjector.getWifiLastResortWatchdog().noteConnectionFailureAndTriggerIfNeeded(ClientModeImpl.this.getTargetSsid(), ClientModeImpl.this.mTargetRoamBSSID, 3);
                    break;
                case ClientModeImpl.CMD_ADD_KEEPALIVE_PACKET_FILTER_TO_APF /*{ENCODED_INT: 131281}*/:
                    if (ClientModeImpl.this.mIpClient != null) {
                        int slot2 = message.arg1;
                        if (!(message.obj instanceof NattKeepalivePacketData)) {
                            if (message.obj instanceof TcpKeepalivePacketData) {
                                ClientModeImpl.this.mIpClient.addKeepalivePacketFilter(slot2, (TcpKeepalivePacketData) message.obj);
                                break;
                            }
                        } else {
                            ClientModeImpl.this.mIpClient.addKeepalivePacketFilter(slot2, (NattKeepalivePacketData) message.obj);
                            break;
                        }
                    }
                    break;
                case ClientModeImpl.CMD_REMOVE_KEEPALIVE_PACKET_FILTER_FROM_APF /*{ENCODED_INT: 131282}*/:
                    if (ClientModeImpl.this.mIpClient != null) {
                        ClientModeImpl.this.mIpClient.removeKeepalivePacketFilter(message.arg1);
                        break;
                    }
                    break;
                case ClientModeImpl.CMD_REPLACE_PUBLIC_DNS /*{ENCODED_INT: 131286}*/:
                    if (ClientModeImpl.this.mLinkProperties != null) {
                        String publicDnsIp = ((Bundle) message.obj).getString("publicDnsServer");
                        ArrayList<InetAddress> dnsList = new ArrayList<>(ClientModeImpl.this.mLinkProperties.getDnsServers());
                        dnsList.add(NetworkUtils.numericToInetAddress(publicDnsIp));
                        ClientModeImpl.this.mLinkProperties.setDnsServers(dnsList);
                        ClientModeImpl clientModeImpl13 = ClientModeImpl.this;
                        clientModeImpl13.updateLinkProperties(clientModeImpl13.mLinkProperties);
                        break;
                    }
                    break;
                case ClientModeImpl.CMD_PRE_DHCP_ACTION /*{ENCODED_INT: 131327}*/:
                    ClientModeImpl.this.handlePreDhcpSetup();
                    break;
                case ClientModeImpl.CMD_PRE_DHCP_ACTION_COMPLETE /*{ENCODED_INT: 131328}*/:
                    if (ClientModeImpl.this.mIpClient != null) {
                        ClientModeImpl.this.mIpClient.completedPreDhcpAction();
                        break;
                    }
                    break;
                case ClientModeImpl.CMD_POST_DHCP_ACTION /*{ENCODED_INT: 131329}*/:
                    ClientModeImpl.this.handlePostDhcpSetup();
                    break;
                case WifiP2pServiceImpl.DISCONNECT_WIFI_REQUEST /*{ENCODED_INT: 143372}*/:
                    if (message.arg1 == 1) {
                        ClientModeImpl.this.mWifiMetrics.logStaEvent(15, 5);
                        ClientModeImpl.this.notifyDisconnectInternalReason(16);
                        ClientModeImpl.this.mWifiNative.disconnect(ClientModeImpl.this.mInterfaceName);
                        ClientModeImpl.this.mTemporarilyDisconnectWifi = true;
                        ClientModeImpl clientModeImpl14 = ClientModeImpl.this;
                        clientModeImpl14.transitionTo(clientModeImpl14.mDisconnectingState);
                        break;
                    }
                    break;
                case WifiMonitor.NETWORK_CONNECTION_EVENT /*{ENCODED_INT: 147459}*/:
                    if (ClientModeImpl.DBG) {
                        ClientModeImpl.this.log("dongle roaming established");
                    }
                    ClientModeImpl.this.mWifiInfo.setBSSID((String) message.obj);
                    ClientModeImpl.this.mLastNetworkId = message.arg1;
                    ClientModeImpl clientModeImpl15 = ClientModeImpl.this;
                    clientModeImpl15.mLastConnectedNetworkId = clientModeImpl15.mLastNetworkId;
                    ClientModeImpl.this.mWifiInfo.setNetworkId(ClientModeImpl.this.mLastNetworkId);
                    ClientModeImpl.this.mWifiInfo.setMacAddress(ClientModeImpl.this.mWifiNative.getMacAddress(ClientModeImpl.this.mInterfaceName));
                    if (!ClientModeImpl.this.mLastBssid.equals(message.obj)) {
                        ClientModeImpl.this.setRoamTriggered(true);
                        ClientModeImpl.this.mLastBssid = (String) message.obj;
                        ScanDetailCache scanDetailCache2 = ClientModeImpl.this.mWifiConfigManager.getScanDetailCacheForNetwork(ClientModeImpl.this.mLastNetworkId);
                        ScanResult scanResult2 = null;
                        if (scanDetailCache2 != null) {
                            scanResult2 = scanDetailCache2.getScanResult(ClientModeImpl.this.mLastBssid);
                        }
                        if (scanResult2 == null) {
                            Log.d(ClientModeImpl.TAG, "roamed scan result is not in cache, find it from last native scan results");
                            Iterator<ScanDetail> it = ClientModeImpl.this.mWifiNative.getScanResults(ClientModeImpl.this.mInterfaceName).iterator();
                            while (true) {
                                if (it.hasNext()) {
                                    ScanDetail scanDetail = it.next();
                                    if (ClientModeImpl.this.mLastBssid.equals(scanDetail.getBSSIDString())) {
                                        Log.d(ClientModeImpl.TAG, "found it! update the cache");
                                        scanResult2 = scanDetail.getScanResult();
                                        ClientModeImpl.this.mWifiConfigManager.updateScanDetailForNetwork(ClientModeImpl.this.mLastNetworkId, scanDetail);
                                    }
                                }
                            }
                        }
                        if (scanResult2 != null) {
                            ClientModeImpl.this.mWifiInfo.setFrequency(scanResult2.frequency);
                            ClientModeImpl.this.updateWifiInfoForVendors(scanResult2);
                            ClientModeImpl.this.mWifiInfo.setWifiMode(scanResult2.wifiMode);
                            if (ClientModeImpl.CSC_SUPPORT_5G_ANT_SHARE) {
                                ClientModeImpl clientModeImpl16 = ClientModeImpl.this;
                                clientModeImpl16.sendIpcMessageToRilForLteu(4, true, clientModeImpl16.mWifiInfo.is5GHz(), false);
                            }
                        } else {
                            Log.d(ClientModeImpl.TAG, "can't update vendor infos, bssid: " + ClientModeImpl.this.mLastBssid);
                        }
                        ClientModeImpl clientModeImpl17 = ClientModeImpl.this;
                        clientModeImpl17.sendNetworkStateChangeBroadcast(clientModeImpl17.mLastBssid);
                        int tmpDhcpRenewAfterRoamingMode = ClientModeImpl.this.getRoamDhcpPolicy();
                        WifiConfiguration currentConfig = ClientModeImpl.this.getCurrentWifiConfiguration();
                        boolean isUsingStaticIp = false;
                        if (currentConfig != null) {
                            isUsingStaticIp = currentConfig.getIpAssignment() == IpConfiguration.IpAssignment.STATIC;
                        }
                        if (isUsingStaticIp) {
                            ClientModeImpl.this.log("Static ip - skip renew");
                            tmpDhcpRenewAfterRoamingMode = -1;
                        }
                        if (ClientModeImpl.this.mRoamDhcpPolicyByB2bConfig == 2 || tmpDhcpRenewAfterRoamingMode == 2 || tmpDhcpRenewAfterRoamingMode == -1) {
                            ClientModeImpl clientModeImpl18 = ClientModeImpl.this;
                            clientModeImpl18.log("Skip Dhcp - mRoamDhcpPolicyByB2bConfig:" + ClientModeImpl.this.mRoamDhcpPolicyByB2bConfig + " ,tmpDhcpRenewAfterRoamingMode : " + tmpDhcpRenewAfterRoamingMode);
                            if ("".equals(ClientModeImpl.CONFIG_SECURE_SVC_INTEGRATION) && !SemCscFeature.getInstance().getBoolean(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_DISABLEMWIPS) && MobileWipsFrameworkService.getInstance() != null) {
                                MobileWipsFrameworkService.getInstance().sendEmptyMessage(20);
                            }
                            ClientModeImpl.this.setRoamTriggered(false);
                        } else if (ClientModeImpl.this.checkIfForceRestartDhcp()) {
                            ClientModeImpl.this.restartDhcp(currentConfig);
                        } else {
                            ClientModeImpl.this.CheckIfDefaultGatewaySame();
                            if ("".equals(ClientModeImpl.CONFIG_SECURE_SVC_INTEGRATION) && !SemCscFeature.getInstance().getBoolean(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_DISABLEMWIPS) && MobileWipsFrameworkService.getInstance() != null) {
                                MobileWipsFrameworkService.getInstance().sendEmptyMessage(20);
                            }
                        }
                        ClientModeImpl.this.handleCellularCapabilities(true);
                        if (!(WifiRoamingAssistant.getInstance() == null || currentConfig == null)) {
                            WifiRoamingAssistant.getInstance().updateRcl(currentConfig.configKey(), ClientModeImpl.this.mWifiInfo.getFrequency(), true);
                            break;
                        }
                    }
                    break;
                case 151553:
                    if (ClientModeImpl.this.mWifiInfo.getNetworkId() != message.arg1) {
                        handleStatus = false;
                        break;
                    } else {
                        ClientModeImpl.this.replyToMessage(message, 151555);
                        break;
                    }
                case 151572:
                    RssiPacketCountInfo info = new RssiPacketCountInfo();
                    ClientModeImpl.this.fetchRssiLinkSpeedAndFrequencyNative();
                    info.rssi = ClientModeImpl.this.mWifiInfo.getRssi();
                    ClientModeImpl clientModeImpl19 = ClientModeImpl.this;
                    clientModeImpl19.knoxAutoSwitchPolicy(clientModeImpl19.mWifiInfo.getRssi());
                    WifiNative.TxPacketCounters counters = ClientModeImpl.this.mWifiNative.getTxPacketCounters(ClientModeImpl.this.mInterfaceName);
                    if (counters == null) {
                        ClientModeImpl.this.replyToMessage((ClientModeImpl) message, (Message) 151574, 0);
                        break;
                    } else {
                        info.txgood = counters.txSucceeded;
                        info.txbad = counters.txFailed;
                        ClientModeImpl.this.replyToMessage((ClientModeImpl) message, (Message) 151573, (int) info);
                        break;
                    }
                default:
                    handleStatus = false;
                    break;
            }
            if (handleStatus) {
                ClientModeImpl.this.logStateAndMessage(message, this);
            }
            return handleStatus;
        }

        private WifiLinkLayerStats updateLinkLayerStatsRssiAndScoreReportInternal() {
            WifiLinkLayerStats stats = ClientModeImpl.this.getWifiLinkLayerStats();
            ClientModeImpl.this.fetchRssiLinkSpeedAndFrequencyNative();
            ClientModeImpl.this.mWifiScoreReport.calculateAndReportScore(ClientModeImpl.this.mWifiInfo, ClientModeImpl.this.mNetworkAgent, ClientModeImpl.this.mWifiMetrics);
            return stats;
        }
    }

    public void updateLinkLayerStatsRssiAndScoreReport() {
        sendMessage(CMD_ONESHOT_RSSI_POLL);
    }

    /* access modifiers changed from: private */
    public static int convertToUsabilityStatsTriggerType(int unusableEventTriggerType) {
        if (unusableEventTriggerType == 1) {
            return 1;
        }
        if (unusableEventTriggerType == 2) {
            return 2;
        }
        if (unusableEventTriggerType == 3) {
            return 3;
        }
        if (unusableEventTriggerType == 4) {
            return 4;
        }
        if (unusableEventTriggerType == 5) {
            return 5;
        }
        Log.e(TAG, "Unknown WifiIsUnusableEvent: " + unusableEventTriggerType);
        return 0;
    }

    class ObtainingIpState extends State {
        ObtainingIpState() {
        }

        public void enter() {
            StaticIpConfiguration staticIpConfig;
            WifiConfiguration currentConfig = ClientModeImpl.this.getCurrentWifiConfiguration();
            boolean isUsingStaticIp = false;
            if (currentConfig != null) {
                isUsingStaticIp = currentConfig.getIpAssignment() == IpConfiguration.IpAssignment.STATIC;
            }
            if (ClientModeImpl.this.mVerboseLoggingEnabled) {
                String key = currentConfig.configKey();
                ClientModeImpl clientModeImpl = ClientModeImpl.this;
                clientModeImpl.log("enter ObtainingIpState netId=" + Integer.toString(ClientModeImpl.this.mLastNetworkId) + " " + key + "  roam=" + ClientModeImpl.this.mIsAutoRoaming + " static=" + isUsingStaticIp);
            }
            ClientModeImpl.this.setNetworkDetailedState(NetworkInfo.DetailedState.OBTAINING_IPADDR);
            ClientModeImpl.this.clearTargetBssid("ObtainingIpAddress");
            ClientModeImpl.this.stopIpClient();
            ClientModeImpl.this.setTcpBufferAndProxySettingsForIpManager();
            if (!isUsingStaticIp) {
                staticIpConfig = new ProvisioningConfiguration.Builder().withPreDhcpAction().withApfCapabilities(ClientModeImpl.this.mWifiNative.getApfCapabilities(ClientModeImpl.this.mInterfaceName)).withNetwork(ClientModeImpl.this.getCurrentNetwork()).withDisplayName(currentConfig.SSID).withRandomMacAddress().build();
            } else {
                staticIpConfig = new ProvisioningConfiguration.Builder().withStaticConfiguration(currentConfig.getStaticIpConfiguration()).withApfCapabilities(ClientModeImpl.this.mWifiNative.getApfCapabilities(ClientModeImpl.this.mInterfaceName)).withNetwork(ClientModeImpl.this.getCurrentNetwork()).withDisplayName(currentConfig.SSID).build();
            }
            if (ClientModeImpl.this.mIpClient != null) {
                ClientModeImpl.this.mIpClient.startProvisioning(staticIpConfig);
                ClientModeImpl.this.getWifiLinkLayerStats();
                return;
            }
            Log.d(ClientModeImpl.TAG, "IpClient is not ready to use, going back to disconnected state");
            ClientModeImpl.this.notifyDisconnectInternalReason(24);
            ClientModeImpl.this.mWifiNative.disconnect(ClientModeImpl.this.mInterfaceName);
        }

        public boolean processMessage(Message message) {
            boolean handleStatus = true;
            switch (message.what) {
                case ClientModeImpl.CMD_SET_HIGH_PERF_MODE /*{ENCODED_INT: 131149}*/:
                    ClientModeImpl.this.mMessageHandlingStatus = ClientModeImpl.MESSAGE_HANDLING_STATUS_DEFERRED;
                    ClientModeImpl.this.deferMessage(message);
                    break;
                case ClientModeImpl.CMD_START_CONNECT /*{ENCODED_INT: 131215}*/:
                    if ("any".equals((String) message.obj)) {
                        return false;
                    }
                case ClientModeImpl.CMD_START_ROAM /*{ENCODED_INT: 131217}*/:
                    ClientModeImpl.this.mMessageHandlingStatus = ClientModeImpl.MESSAGE_HANDLING_STATUS_DISCARD;
                    break;
                case WifiMonitor.NETWORK_DISCONNECTION_EVENT /*{ENCODED_INT: 147460}*/:
                    ClientModeImpl.this.reportConnectionAttemptEnd(6, 1, 0);
                    handleStatus = false;
                    break;
                case 151559:
                    ClientModeImpl.this.mMessageHandlingStatus = ClientModeImpl.MESSAGE_HANDLING_STATUS_DEFERRED;
                    ClientModeImpl.this.deferMessage(message);
                    break;
                default:
                    handleStatus = false;
                    break;
            }
            if (handleStatus) {
                ClientModeImpl.this.logStateAndMessage(message, this);
            }
            return handleStatus;
        }

        public void exit() {
        }
    }

    @VisibleForTesting
    public boolean shouldEvaluateWhetherToSendExplicitlySelected(WifiConfiguration currentConfig) {
        if (currentConfig == null) {
            Log.wtf(TAG, "Current WifiConfiguration is null, but IP provisioning just succeeded");
            return false;
        }
        long currentTimeMillis = this.mClock.getElapsedSinceBootMillis();
        if (this.mWifiConfigManager.getLastSelectedNetwork() != currentConfig.networkId || currentTimeMillis - this.mWifiConfigManager.getLastSelectedTimeStamp() >= 30000) {
            return false;
        }
        return true;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void sendConnectedState() {
        WifiConfiguration config = getCurrentWifiConfiguration();
        boolean explicitlySelected = false;
        int issuedUid = getIssueUidForConnectingNetwork(config);
        if (shouldEvaluateWhetherToSendExplicitlySelected(config)) {
            explicitlySelected = this.mWifiPermissionsUtil.checkNetworkSettingsPermission(issuedUid);
            if (this.mVerboseLoggingEnabled) {
                log("Network selected by UID " + issuedUid + " explicitlySelected=" + explicitlySelected);
            }
        }
        if (this.mVerboseLoggingEnabled) {
            log("explictlySelected=" + explicitlySelected + " acceptUnvalidated=" + config.noInternetAccessExpected);
        }
        ActivityManager activityManager = (ActivityManager) this.mContext.getSystemService("activity");
        String packageName = this.mContext.getPackageManager().getNameForUid(issuedUid);
        if (explicitlySelected && (config.isEphemeral() || (packageName != null && this.NETWORKSETTINGS_PERMISSION_EXCEPTION_PACKAGE_LIST.contains(packageName)))) {
            explicitlySelected = false;
            if (this.mVerboseLoggingEnabled) {
                log("explictlySelected Exception case for smarthings =" + false + " acceptUnvalidated=" + config.noInternetAccessExpected);
            }
        }
        Log.d(TAG, "noInternetAccessExpected : " + config.isNoInternetAccessExpected() + ", CUid : " + config.creatorUid + ",sLUid : " + config.lastUpdateUid);
        this.mIsManualSelection = explicitlySelected;
        this.mCm.setMultiNetwork(false, issuedUid);
        if (this.mNetworkAgent != null) {
            if (config.isCaptivePortal || (!config.isNoInternetAccessExpected() && (explicitlySelected || isPoorNetworkTestEnabled() || isMultiNetworkAvailableApp(config.creatorUid, issuedUid, packageName)))) {
                if (!explicitlySelected && (config.isEphemeral() || isMultiNetworkAvailableApp(config.creatorUid, issuedUid, packageName))) {
                    log("MultiNetwork - package : " + packageName);
                    this.mCm.setMultiNetwork(true, issuedUid);
                }
                this.mNetworkAgent.explicitlySelected(explicitlySelected, config.noInternetAccessExpected);
            } else {
                this.mNetworkAgent.explicitlySelected(true, true);
            }
        }
        setNetworkDetailedState(NetworkInfo.DetailedState.CONNECTED);
        sendNetworkStateChangeBroadcast(this.mLastBssid);
        if ("".equals(CONFIG_SECURE_SVC_INTEGRATION) && !SemCscFeature.getInstance().getBoolean(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_DISABLEMWIPS) && MobileWipsFrameworkService.getInstance() != null) {
            MobileWipsFrameworkService.getInstance().sendEmptyMessage(7);
        }
    }

    public int getIssueUidForConnectingNetwork(WifiConfiguration config) {
        int[] uids = {config.creatorUid, config.lastUpdateUid, config.lastConnectUid};
        ActivityManager activityManager = (ActivityManager) this.mContext.getSystemService("activity");
        PackageManager packageManager = this.mContext.getPackageManager();
        for (int uid : uids) {
            if (uid > 1010) {
                try {
                    if (this.MULTINETWORK_ALLOWING_SYSTEM_PACKAGE_LIST.contains(packageManager.getNameForUid(uid))) {
                        return uid;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return config.lastConnectUid >= 1000 ? config.lastConnectUid : config.creatorUid;
    }

    private boolean isMultiNetworkAvailableApp(int cuid, int issuedUid, String packageName) {
        if (cuid <= 1010 || issuedUid <= 1010) {
            return this.MULTINETWORK_ALLOWING_SYSTEM_PACKAGE_LIST.contains(packageName);
        }
        if (packageName == null || !this.MULTINETWORK_EXCEPTION_PACKAGE_LIST.contains(packageName)) {
            return issuedUid > 1010;
        }
        return false;
    }

    class RoamingState extends State {
        boolean mAssociated;

        RoamingState() {
        }

        public void enter() {
            if (ClientModeImpl.this.mVerboseLoggingEnabled) {
                ClientModeImpl.this.log("RoamingState Enter mScreenOn=" + ClientModeImpl.this.mScreenOn);
            }
            ClientModeImpl.this.mRoamWatchdogCount++;
            ClientModeImpl.this.logd("Start Roam Watchdog " + ClientModeImpl.this.mRoamWatchdogCount);
            ClientModeImpl clientModeImpl = ClientModeImpl.this;
            clientModeImpl.sendMessageDelayed(clientModeImpl.obtainMessage(ClientModeImpl.CMD_ROAM_WATCHDOG_TIMER, clientModeImpl.mRoamWatchdogCount, 0), 15000);
            this.mAssociated = false;
            ClientModeImpl clientModeImpl2 = ClientModeImpl.this;
            clientModeImpl2.report(3, ReportUtil.getReportDataForRoamingEnter("framework-start", clientModeImpl2.mWifiInfo.getSSID(), ClientModeImpl.this.mWifiInfo.getBSSID(), ClientModeImpl.this.mWifiInfo.getRssi()));
            ClientModeImpl.this.mBigDataManager.addOrUpdateValue(7, 1);
            ClientModeImpl.this.notifyMobilewipsRoamEvent("start");
        }

        public boolean processMessage(Message message) {
            boolean handleStatus = true;
            switch (message.what) {
                case ClientModeImpl.CMD_ROAM_WATCHDOG_TIMER /*{ENCODED_INT: 131166}*/:
                    if (ClientModeImpl.this.mRoamWatchdogCount == message.arg1) {
                        if (ClientModeImpl.this.mVerboseLoggingEnabled) {
                            ClientModeImpl.this.log("roaming watchdog! -> disconnect");
                        }
                        ClientModeImpl.this.mWifiMetrics.endConnectionEvent(9, 1, 0);
                        ClientModeImpl.access$20708(ClientModeImpl.this);
                        ClientModeImpl.this.handleNetworkDisconnect();
                        ClientModeImpl.this.mWifiMetrics.logStaEvent(15, 4);
                        ClientModeImpl.this.notifyDisconnectInternalReason(5);
                        ClientModeImpl.this.mWifiNative.disconnect(ClientModeImpl.this.mInterfaceName);
                        ClientModeImpl clientModeImpl = ClientModeImpl.this;
                        clientModeImpl.transitionTo(clientModeImpl.mDisconnectedState);
                        break;
                    }
                    break;
                case ClientModeImpl.CMD_IP_CONFIGURATION_LOST /*{ENCODED_INT: 131211}*/:
                    if (ClientModeImpl.this.getCurrentWifiConfiguration() != null) {
                        ClientModeImpl.this.mWifiDiagnostics.triggerBugReportDataCapture(3);
                    }
                    handleStatus = false;
                    break;
                case ClientModeImpl.CMD_UNWANTED_NETWORK /*{ENCODED_INT: 131216}*/:
                    if (ClientModeImpl.this.mVerboseLoggingEnabled) {
                        ClientModeImpl.this.log("Roaming and CS doesn't want the network -> ignore");
                        break;
                    }
                    break;
                case WifiMonitor.NETWORK_CONNECTION_EVENT /*{ENCODED_INT: 147459}*/:
                    if (!this.mAssociated) {
                        ClientModeImpl.this.mMessageHandlingStatus = ClientModeImpl.MESSAGE_HANDLING_STATUS_DISCARD;
                        break;
                    } else {
                        if (ClientModeImpl.this.mVerboseLoggingEnabled) {
                            ClientModeImpl.this.log("roaming and Network connection established");
                        }
                        ClientModeImpl.this.setRoamTriggered(true);
                        ClientModeImpl.this.mLastNetworkId = message.arg1;
                        ClientModeImpl clientModeImpl2 = ClientModeImpl.this;
                        clientModeImpl2.mLastConnectedNetworkId = clientModeImpl2.mLastNetworkId;
                        ClientModeImpl.this.mLastBssid = (String) message.obj;
                        ClientModeImpl.this.mWifiInfo.setBSSID(ClientModeImpl.this.mLastBssid);
                        ClientModeImpl.this.mWifiInfo.setNetworkId(ClientModeImpl.this.mLastNetworkId);
                        ClientModeImpl.this.mWifiConnectivityManager.trackBssid(ClientModeImpl.this.mLastBssid, true, message.arg2);
                        ClientModeImpl clientModeImpl3 = ClientModeImpl.this;
                        clientModeImpl3.sendNetworkStateChangeBroadcast(clientModeImpl3.mLastBssid);
                        ClientModeImpl.this.reportConnectionAttemptEnd(1, 1, 0);
                        ClientModeImpl clientModeImpl4 = ClientModeImpl.this;
                        clientModeImpl4.report(3, ReportUtil.getReportDataForRoamingEnter("framework-completed", clientModeImpl4.mWifiInfo.getSSID(), ClientModeImpl.this.mWifiInfo.getBSSID(), ClientModeImpl.this.mWifiInfo.getRssi()));
                        ClientModeImpl.this.clearTargetBssid("RoamingCompleted");
                        int tmpDhcpRenewAfterRoamingMode = ClientModeImpl.this.getRoamDhcpPolicy();
                        WifiConfiguration currentConfig = ClientModeImpl.this.getCurrentWifiConfiguration();
                        boolean isUsingStaticIp = false;
                        if (currentConfig != null) {
                            isUsingStaticIp = currentConfig.getIpAssignment() == IpConfiguration.IpAssignment.STATIC;
                        }
                        if (isUsingStaticIp) {
                            ClientModeImpl.this.log(" Static ip - skip renew");
                            tmpDhcpRenewAfterRoamingMode = -1;
                        }
                        if (ClientModeImpl.this.mRoamDhcpPolicyByB2bConfig == 2 || tmpDhcpRenewAfterRoamingMode == 2 || tmpDhcpRenewAfterRoamingMode == -1) {
                            ClientModeImpl clientModeImpl5 = ClientModeImpl.this;
                            clientModeImpl5.log(" Skip Dhcp - mRoamDhcpPolicyByB2bConfig:" + ClientModeImpl.this.mRoamDhcpPolicyByB2bConfig + " ,tmpDhcpRenewAfterRoamingMode : " + tmpDhcpRenewAfterRoamingMode);
                            ClientModeImpl.this.setRoamTriggered(false);
                        } else if (ClientModeImpl.this.checkIfForceRestartDhcp()) {
                            ClientModeImpl.this.restartDhcp(currentConfig);
                        } else {
                            ClientModeImpl.this.CheckIfDefaultGatewaySame();
                        }
                        ClientModeImpl clientModeImpl6 = ClientModeImpl.this;
                        clientModeImpl6.transitionTo(clientModeImpl6.mConnectedState);
                        break;
                    }
                case WifiMonitor.NETWORK_DISCONNECTION_EVENT /*{ENCODED_INT: 147460}*/:
                    String bssid = (String) message.obj;
                    String target = "";
                    if (ClientModeImpl.this.mTargetRoamBSSID != null) {
                        target = ClientModeImpl.this.mTargetRoamBSSID;
                    }
                    ClientModeImpl clientModeImpl7 = ClientModeImpl.this;
                    clientModeImpl7.log("NETWORK_DISCONNECTION_EVENT in roaming state BSSID=" + bssid + " target=" + target);
                    if (bssid != null && bssid.equals(ClientModeImpl.this.mTargetRoamBSSID)) {
                        ClientModeImpl.this.handleNetworkDisconnect();
                        ClientModeImpl clientModeImpl8 = ClientModeImpl.this;
                        clientModeImpl8.transitionTo(clientModeImpl8.mDisconnectedState);
                        break;
                    }
                case WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT /*{ENCODED_INT: 147462}*/:
                    ClientModeImpl.this.handleSupplicantStateChange(message);
                    StateChangeResult stateChangeResult = (StateChangeResult) message.obj;
                    if (stateChangeResult.state == SupplicantState.DISCONNECTED || stateChangeResult.state == SupplicantState.INACTIVE || stateChangeResult.state == SupplicantState.INTERFACE_DISABLED) {
                        if (ClientModeImpl.this.mVerboseLoggingEnabled) {
                            ClientModeImpl clientModeImpl9 = ClientModeImpl.this;
                            clientModeImpl9.log("STATE_CHANGE_EVENT in roaming state " + stateChangeResult.toString());
                        }
                        if (stateChangeResult.BSSID != null && stateChangeResult.BSSID.equals(ClientModeImpl.this.mTargetRoamBSSID)) {
                            ClientModeImpl.this.notifyDisconnectInternalReason(6);
                            if (ClientModeImpl.this.mIWCMonitorChannel != null) {
                                ClientModeImpl.this.mIWCMonitorChannel.sendMessage((int) IWCMonitor.IWC_WIFI_DISCONNECTED, 0);
                            }
                            ClientModeImpl.this.handleNetworkDisconnect();
                            ClientModeImpl clientModeImpl10 = ClientModeImpl.this;
                            clientModeImpl10.transitionTo(clientModeImpl10.mDisconnectedState);
                        }
                    }
                    if (stateChangeResult.state == SupplicantState.ASSOCIATED) {
                        this.mAssociated = true;
                        if (stateChangeResult.BSSID != null) {
                            ClientModeImpl.this.mTargetRoamBSSID = stateChangeResult.BSSID;
                            break;
                        }
                    }
                    break;
                default:
                    handleStatus = false;
                    break;
            }
            if (handleStatus) {
                ClientModeImpl.this.logStateAndMessage(message, this);
            }
            return handleStatus;
        }

        public void exit() {
            ClientModeImpl.this.logd("ClientModeImpl: Leaving Roaming state");
            ClientModeImpl.this.mBigDataManager.addOrUpdateValue(7, 0);
        }
    }

    class ConnectedState extends State {
        private WifiConfiguration mCurrentConfig;

        ConnectedState() {
        }

        public void enter() {
            if (ClientModeImpl.this.mVerboseLoggingEnabled) {
                ClientModeImpl clientModeImpl = ClientModeImpl.this;
                clientModeImpl.log("Enter ConnectedState  mScreenOn=" + ClientModeImpl.this.mScreenOn);
            }
            ClientModeImpl clientModeImpl2 = ClientModeImpl.this;
            clientModeImpl2.mLastConnectedTime = clientModeImpl2.mClock.getElapsedSinceBootMillis();
            ClientModeImpl.this.reportConnectionAttemptEnd(1, 1, 0);
            ClientModeImpl.this.mWifiConnectivityManager.handleConnectionStateChanged(1);
            ClientModeImpl.this.registerConnected();
            ClientModeImpl.this.mLastConnectAttemptTimestamp = 0;
            ClientModeImpl.this.mTargetWifiConfiguration = null;
            ClientModeImpl.this.mWifiScoreReport.reset();
            ClientModeImpl.this.mLastSignalLevel = -1;
            ClientModeImpl.this.mIsAutoRoaming = false;
            ClientModeImpl.this.mWifiConfigManager.setCurrentNetworkId(ClientModeImpl.this.mTargetNetworkId);
            ClientModeImpl.this.mLastDriverRoamAttempt = 0;
            ClientModeImpl.this.mTargetNetworkId = -1;
            ClientModeImpl.this.mWifiInjector.getWifiLastResortWatchdog().connectedStateTransition(true);
            ClientModeImpl.this.mWifiStateTracker.updateState(3);
            this.mCurrentConfig = ClientModeImpl.this.getCurrentWifiConfiguration();
            WifiConfiguration wifiConfiguration = this.mCurrentConfig;
            if (wifiConfiguration != null && wifiConfiguration.isPasspoint() && Settings.Secure.getInt(ClientModeImpl.this.mContext.getContentResolver(), "wifi_hotspot20_connected_history", 0) == 0) {
                Log.d(ClientModeImpl.TAG, "ConnectedState, WIFI_HOTSPOT20_CONNECTED_HISTORY    is set to 1");
                Settings.Secure.putInt(ClientModeImpl.this.mContext.getContentResolver(), "wifi_hotspot20_connected_history", 1);
            }
            WifiConfiguration wifiConfiguration2 = this.mCurrentConfig;
            if (!(wifiConfiguration2 == null || wifiConfiguration2.SSID == null)) {
                if (this.mCurrentConfig.allowedKeyManagement.get(0) || this.mCurrentConfig.allowedKeyManagement.get(1) || this.mCurrentConfig.allowedKeyManagement.get(4) || this.mCurrentConfig.allowedKeyManagement.get(22)) {
                    Context context = ClientModeImpl.this.mContext;
                    WifiMobileDeviceManager.auditLog(context, 5, true, ClientModeImpl.TAG, "Wi-Fi is connected to " + this.mCurrentConfig.getPrintableSsid() + " network using 802.11-2012 channel");
                } else if (this.mCurrentConfig.enterpriseConfig != null) {
                    if (this.mCurrentConfig.enterpriseConfig.getEapMethod() == 1) {
                        Context context2 = ClientModeImpl.this.mContext;
                        WifiMobileDeviceManager.auditLog(context2, 5, true, ClientModeImpl.TAG, "Wi-Fi is connected to " + this.mCurrentConfig.getPrintableSsid() + " network using EAP-TLS channel");
                    } else {
                        Context context3 = ClientModeImpl.this.mContext;
                        WifiMobileDeviceManager.auditLog(context3, 5, true, ClientModeImpl.TAG, "Wi-Fi is connected to " + this.mCurrentConfig.getPrintableSsid() + " network using 802.1X channel");
                    }
                }
            }
            ReportUtil.startTimerDuringConnection();
            ReportUtil.updateWifiInfo(ClientModeImpl.this.mWifiInfo);
            WifiConfiguration wifiConfiguration3 = this.mCurrentConfig;
            if (wifiConfiguration3 != null) {
                if (wifiConfiguration3.semIsVendorSpecificSsid) {
                    ClientModeImpl.this.mConnectedApInternalType = 4;
                } else if (this.mCurrentConfig.semSamsungSpecificFlags.get(1)) {
                    ClientModeImpl.this.mConnectedApInternalType = 3;
                } else if (this.mCurrentConfig.isPasspoint()) {
                    ClientModeImpl.this.mConnectedApInternalType = 2;
                } else if (this.mCurrentConfig.semIsWeChatAp) {
                    ClientModeImpl.this.mConnectedApInternalType = 1;
                } else if (this.mCurrentConfig.isCaptivePortal) {
                    ClientModeImpl.this.mConnectedApInternalType = 6;
                } else if (this.mCurrentConfig.semAutoWifiScore > 4) {
                    ClientModeImpl.this.mConnectedApInternalType = 5;
                } else if (this.mCurrentConfig.isEphemeral()) {
                    ClientModeImpl.this.mConnectedApInternalType = 7;
                } else {
                    ClientModeImpl.this.mConnectedApInternalType = 0;
                }
                ClientModeImpl.this.mBigDataManager.addOrUpdateValue(11, ClientModeImpl.this.mConnectedApInternalType);
            }
            ClientModeImpl.this.mBigDataManager.addOrUpdateValue(9, 0);
            ClientModeImpl.this.mBigDataManager.addOrUpdateValue(12, 0);
            ClientModeImpl clientModeImpl3 = ClientModeImpl.this;
            clientModeImpl3.report(2, ReportUtil.getReportDataForConnectTranstion(clientModeImpl3.mConnectedApInternalType));
            if (ClientModeImpl.this.mWifiGeofenceManager.isSupported() && ClientModeImpl.this.mWifiGeofenceManager.isValidAccessPointToUseGeofence(ClientModeImpl.this.mWifiInfo, this.mCurrentConfig)) {
                ClientModeImpl.this.mWifiGeofenceManager.triggerStartLearning(this.mCurrentConfig);
            }
            WifiConfiguration config = ClientModeImpl.this.getCurrentWifiConfiguration();
            if (ClientModeImpl.this.isLocationSupportedAp(config)) {
                double[] latitudeLongitude = ClientModeImpl.this.getLatitudeLongitude(config);
                if (latitudeLongitude[0] == ClientModeImpl.INVALID_LATITUDE_LONGITUDE || latitudeLongitude[1] == ClientModeImpl.INVALID_LATITUDE_LONGITUDE) {
                    ClientModeImpl.this.mLocationRequestNetworkId = config.networkId;
                    ClientModeImpl.this.sendMessageDelayed(ClientModeImpl.CMD_UPDATE_CONFIG_LOCATION, 1, 10000);
                } else {
                    ClientModeImpl.this.sendMessage(ClientModeImpl.CMD_UPDATE_CONFIG_LOCATION, 0);
                }
            }
            ClientModeImpl.this.mWifiInjector.getWifiLockManager().updateWifiClientConnected(true);
            if (ClientModeImpl.CSC_SUPPORT_5G_ANT_SHARE) {
                ClientModeImpl clientModeImpl4 = ClientModeImpl.this;
                clientModeImpl4.sendIpcMessageToRilForLteu(4, true, clientModeImpl4.mWifiInfo.is5GHz(), false);
            }
            ClientModeImpl.this.handleCellularCapabilities(true);
            ClientModeImpl.this.updateEDMWiFiPolicy();
            if (WifiRoamingAssistant.getInstance() != null && this.mCurrentConfig != null) {
                WifiRoamingAssistant.getInstance().updateRcl(this.mCurrentConfig.configKey(), ClientModeImpl.this.mWifiInfo.getFrequency(), true);
            }
        }

        /* JADX INFO: Multiple debug info for r3v37 java.lang.String: [D('configSsid' java.lang.String), D('handleStatus' boolean)] */
        public boolean processMessage(Message message) {
            boolean z;
            Message message2;
            boolean handleStatus;
            String str;
            boolean handleStatus2;
            String str2;
            boolean alternativeNetworkFound;
            WifiConfiguration config;
            int bestCandidateNetworkId;
            WifiConfiguration config2;
            String str3;
            List<WifiConfiguration> configs;
            WifiConfiguration currConfig;
            Iterator<WifiConfiguration> it;
            boolean alternativeNetworkFound2;
            String str4;
            int configuredSecurity;
            int scanedSecurity;
            boolean alternativeNetworkFound3;
            int configuredSecurity2;
            String str5;
            int scanedSecurity2;
            WifiConfiguration config3;
            String str6;
            String str7 = "";
            switch (message.what) {
                case ClientModeImpl.CMD_UNWANTED_NETWORK /*{ENCODED_INT: 131216}*/:
                    message2 = message;
                    ClientModeImpl.this.report(5, ReportUtil.getReportDataForUnwantedMessage(message2.arg1));
                    if (message2.arg1 == 0) {
                        ClientModeImpl.this.mWifiMetrics.logStaEvent(15, 3);
                        ClientModeImpl.this.notifyDisconnectInternalReason(8);
                        ClientModeImpl.this.mWifiNative.disconnect(ClientModeImpl.this.mInterfaceName);
                        ClientModeImpl clientModeImpl = ClientModeImpl.this;
                        clientModeImpl.transitionTo(clientModeImpl.mDisconnectingState);
                    } else if (message2.arg1 == 2 || message2.arg1 == 1) {
                        if (message2.arg1 == 2) {
                            str = "NETWORK_STATUS_UNWANTED_DISABLE_AUTOJOIN";
                        } else {
                            str = "NETWORK_STATUS_UNWANTED_VALIDATION_FAILED";
                        }
                        Log.d(ClientModeImpl.TAG, str);
                        WifiConfiguration config4 = ClientModeImpl.this.getCurrentWifiConfiguration();
                        if (config4 != null) {
                            ClientModeImpl.this.mWifiConfigManager.setNetworkValidatedInternetAccess(config4.networkId, false);
                            if (message2.arg1 == 2) {
                                ClientModeImpl.this.mWifiConfigManager.updateNetworkSelectionStatus(config4.networkId, 10);
                            } else {
                                ClientModeImpl.this.removeMessages(ClientModeImpl.CMD_DIAGS_CONNECT_TIMEOUT);
                                ClientModeImpl.this.mWifiDiagnostics.reportConnectionEvent((byte) 2);
                                ClientModeImpl.this.mWifiConfigManager.incrementNetworkNoInternetAccessReports(config4.networkId);
                                if ((!ClientModeImpl.this.isWCMEnabled() || ClientModeImpl.this.isWifiOnly()) && ClientModeImpl.this.mWifiConfigManager.getLastSelectedNetwork() != config4.networkId && !config4.noInternetAccessExpected) {
                                    Log.i(ClientModeImpl.TAG, "Temporarily disabling network because ofno-internet access");
                                    ClientModeImpl.this.mWifiConfigManager.updateNetworkSelectionStatus(config4.networkId, 6);
                                }
                            }
                        }
                    }
                    if ((ClientModeImpl.mWlanAdvancedDebugState & 4) != 0) {
                        z = true;
                        ClientModeImpl.this.sendBroadcastIssueTrackerSysDump(1);
                    } else {
                        z = true;
                    }
                    handleStatus = true;
                    break;
                case ClientModeImpl.CMD_START_ROAM /*{ENCODED_INT: 131217}*/:
                    handleStatus2 = true;
                    message2 = message;
                    ClientModeImpl.this.mLastDriverRoamAttempt = 0;
                    int netId = message2.arg1;
                    ScanResult candidate = (ScanResult) message2.obj;
                    String bssid = "any";
                    if (candidate != null) {
                        bssid = candidate.BSSID;
                    }
                    WifiConfiguration config5 = ClientModeImpl.this.mWifiConfigManager.getConfiguredNetworkWithoutMasking(netId);
                    if (config5 == null) {
                        ClientModeImpl.this.loge("CMD_START_ROAM and no config, bail out...");
                    } else {
                        ClientModeImpl.this.mWifiScoreCard.noteConnectionAttempt(ClientModeImpl.this.mWifiInfo);
                        ClientModeImpl.this.setTargetBssid(config5, bssid);
                        ClientModeImpl.this.mTargetNetworkId = netId;
                        ClientModeImpl.this.logd("CMD_START_ROAM sup state " + ClientModeImpl.this.mSupplicantStateTracker.getSupplicantStateName() + " my state " + ClientModeImpl.this.getCurrentState().getName() + " nid=" + Integer.toString(netId) + " config " + config5.configKey() + " targetRoamBSSID " + ClientModeImpl.this.mTargetRoamBSSID);
                        ClientModeImpl clientModeImpl2 = ClientModeImpl.this;
                        clientModeImpl2.reportConnectionAttemptStart(config5, clientModeImpl2.mTargetRoamBSSID, 3);
                        if (ClientModeImpl.this.mWifiNative.roamToNetwork(ClientModeImpl.this.mInterfaceName, config5)) {
                            ClientModeImpl clientModeImpl3 = ClientModeImpl.this;
                            clientModeImpl3.mLastConnectAttemptTimestamp = clientModeImpl3.mClock.getWallClockMillis();
                            ClientModeImpl.this.mTargetWifiConfiguration = config5;
                            ClientModeImpl.this.mIsAutoRoaming = true;
                            ClientModeImpl.this.mWifiMetrics.logStaEvent(12, config5);
                            ClientModeImpl clientModeImpl4 = ClientModeImpl.this;
                            clientModeImpl4.transitionTo(clientModeImpl4.mRoamingState);
                        } else {
                            ClientModeImpl.this.loge("CMD_START_ROAM Failed to start roaming to network " + config5);
                            ClientModeImpl.this.reportConnectionAttemptEnd(5, 1, 0);
                            ClientModeImpl.this.replyToMessage((ClientModeImpl) message2, (Message) 151554, 0);
                            ClientModeImpl.this.mMessageHandlingStatus = -2;
                        }
                    }
                    handleStatus = handleStatus2;
                    z = true;
                    break;
                case ClientModeImpl.CMD_ASSOCIATED_BSSID /*{ENCODED_INT: 131219}*/:
                    message2 = message;
                    ClientModeImpl clientModeImpl5 = ClientModeImpl.this;
                    clientModeImpl5.mLastDriverRoamAttempt = clientModeImpl5.mClock.getWallClockMillis();
                    ClientModeImpl clientModeImpl6 = ClientModeImpl.this;
                    clientModeImpl6.report(3, ReportUtil.getReportDataForRoamingEnter("dongle", clientModeImpl6.mWifiInfo.getSSID(), (String) message2.obj, ClientModeImpl.this.mWifiInfo.getRssi()));
                    handleStatus = false;
                    if (str7.equals(ClientModeImpl.CONFIG_SECURE_SVC_INTEGRATION) && !SemCscFeature.getInstance().getBoolean(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_DISABLEMWIPS) && MobileWipsFrameworkService.getInstance() != null) {
                        MobileWipsFrameworkService.getInstance().sendEmptyMessage(9);
                    }
                    z = true;
                    break;
                case ClientModeImpl.CMD_NETWORK_STATUS /*{ENCODED_INT: 131220}*/:
                    handleStatus2 = true;
                    message2 = message;
                    if (message2.arg1 == 1) {
                        ClientModeImpl.this.removeMessages(ClientModeImpl.CMD_DIAGS_CONNECT_TIMEOUT);
                        ClientModeImpl.this.mWifiDiagnostics.reportConnectionEvent((byte) 1);
                        ClientModeImpl.this.mWifiScoreCard.noteValidationSuccess(ClientModeImpl.this.mWifiInfo);
                        WifiConfiguration config6 = ClientModeImpl.this.getCurrentWifiConfiguration();
                        if (config6 != null) {
                            ClientModeImpl.this.mWifiConfigManager.updateNetworkSelectionStatus(config6.networkId, 0);
                            ClientModeImpl.this.mWifiConfigManager.setNetworkValidatedInternetAccess(config6.networkId, true);
                        }
                    }
                    handleStatus = handleStatus2;
                    z = true;
                    break;
                case ClientModeImpl.CMD_ACCEPT_UNVALIDATED /*{ENCODED_INT: 131225}*/:
                    handleStatus2 = true;
                    message2 = message;
                    ClientModeImpl.this.mWifiConfigManager.setNetworkNoInternetAccessExpected(ClientModeImpl.this.mLastNetworkId, message2.arg1 != 0);
                    ClientModeImpl.this.sendWcmConfigurationChanged();
                    handleStatus = handleStatus2;
                    z = true;
                    break;
                case ClientModeImpl.CMD_SEND_DHCP_RELEASE /*{ENCODED_INT: 131283}*/:
                    handleStatus2 = true;
                    message2 = message;
                    if (ClientModeImpl.this.mIpClient != null) {
                        ClientModeImpl.this.mIpClient.sendDhcpReleasePacket();
                        if (ClientModeImpl.this.waitForDhcpRelease() != 0) {
                            ClientModeImpl.this.loge("waitForDhcpRelease error");
                        } else {
                            ClientModeImpl.this.loge("waitForDhcpRelease() Success");
                        }
                    }
                    handleStatus = handleStatus2;
                    z = true;
                    break;
                case ClientModeImpl.CMD_UPDATE_CONFIG_LOCATION /*{ENCODED_INT: 131332}*/:
                    handleStatus2 = true;
                    message2 = message;
                    ClientModeImpl.this.updateLocation(message2.arg1);
                    handleStatus = handleStatus2;
                    z = true;
                    break;
                case ClientModeImpl.CMD_CHECK_ARP_RESULT /*{ENCODED_INT: 131622}*/:
                    handleStatus2 = true;
                    ClientModeImpl.this.removeMessages(ClientModeImpl.CMD_SEND_ARP);
                    if (!WifiChipInfo.getInstance().getArpResult()) {
                        ClientModeImpl clientModeImpl7 = ClientModeImpl.this;
                        clientModeImpl7.restartDhcp(clientModeImpl7.getCurrentWifiConfiguration());
                        message2 = message;
                    } else {
                        ClientModeImpl.this.setRoamTriggered(false);
                        message2 = message;
                    }
                    handleStatus = handleStatus2;
                    z = true;
                    break;
                case ClientModeImpl.CMD_SEND_ARP /*{ENCODED_INT: 131623}*/:
                    handleStatus2 = true;
                    if (ClientModeImpl.this.mIpClient != null) {
                        ClientModeImpl.this.mIpClient.confirmConfiguration();
                        ClientModeImpl.this.sendMessageDelayed(ClientModeImpl.CMD_SEND_ARP, 500);
                        message2 = message;
                    } else {
                        message2 = message;
                    }
                    handleStatus = handleStatus2;
                    z = true;
                    break;
                case WifiConnectivityMonitor.CHECK_ALTERNATIVE_NETWORKS:
                    Log.d(ClientModeImpl.TAG, "CONNECTED : CHECK_ALTERNATIVE_NETWORKS");
                    boolean needRoamingInHighQuality = message.arg1 == 1;
                    ClientModeImpl.this.mWifiConfigManager.getConfiguredNetwork(ClientModeImpl.this.mWifiInfo.getNetworkId());
                    boolean alternativeNetworkFound4 = false;
                    List<ScanResult> scanResults = new ArrayList<>();
                    scanResults.addAll(ClientModeImpl.this.mScanRequestProxy.getScanResults());
                    List<WifiConfiguration> configs2 = ClientModeImpl.this.mWifiConfigManager.getSavedNetworks(1010);
                    boolean needRoam = false;
                    WifiConfiguration config7 = ClientModeImpl.this.getCurrentWifiConfiguration();
                    ScanResult bestCandidate = null;
                    int bestCandidateNetworkId2 = -1;
                    WifiConfiguration currConfig2 = ClientModeImpl.this.mWifiConfigManager.getConfiguredNetwork(ClientModeImpl.this.mWifiInfo.getNetworkId());
                    int candidateCount = 0;
                    String str8 = "WEP";
                    if (currConfig2 != null) {
                        String configSsid = currConfig2.SSID;
                        if (currConfig2.allowedKeyManagement.get(1)) {
                            configuredSecurity2 = 2;
                        } else if (currConfig2.allowedKeyManagement.get(8)) {
                            configuredSecurity2 = 4;
                        } else if (currConfig2.allowedKeyManagement.get(2) || currConfig2.allowedKeyManagement.get(3)) {
                            configuredSecurity2 = 3;
                        } else {
                            configuredSecurity2 = currConfig2.wepKeys[0] != null ? 1 : 0;
                        }
                        Iterator<ScanResult> it2 = scanResults.iterator();
                        while (it2.hasNext()) {
                            ScanResult scanResult = it2.next();
                            if (scanResult.capabilities.contains(str8)) {
                                str5 = str7;
                                scanedSecurity2 = 1;
                            } else {
                                str5 = str7;
                                if (scanResult.capabilities.contains("SAE")) {
                                    scanedSecurity2 = 4;
                                } else if (scanResult.capabilities.contains("PSK")) {
                                    scanedSecurity2 = 2;
                                } else if (scanResult.capabilities.contains("EAP")) {
                                    scanedSecurity2 = 3;
                                } else {
                                    scanedSecurity2 = 0;
                                }
                            }
                            if (scanResult.SSID == null || configSsid == null || configSsid.length() <= 2) {
                                config3 = config7;
                            } else {
                                config3 = config7;
                                if (scanResult.SSID.equals(configSsid.substring(1, configSsid.length() - 1)) && configuredSecurity2 == scanedSecurity2 && !currConfig2.isCaptivePortal && (((scanResult.is24GHz() && scanResult.level > -64) || (scanResult.is5GHz() && scanResult.level > -70)) && scanResult.BSSID != null && !scanResult.BSSID.equals(ClientModeImpl.this.mWifiInfo.getBSSID()))) {
                                    if (bestCandidate == null) {
                                        bestCandidate = scanResult;
                                        bestCandidateNetworkId2 = currConfig2.networkId;
                                    } else if (bestCandidate.level < scanResult.level) {
                                        bestCandidate = scanResult;
                                        bestCandidateNetworkId2 = currConfig2.networkId;
                                    }
                                    candidateCount++;
                                }
                            }
                            config7 = config3;
                            it2 = it2;
                            alternativeNetworkFound4 = alternativeNetworkFound4;
                            str7 = str5;
                        }
                        config = config7;
                        str2 = str7;
                        alternativeNetworkFound = alternativeNetworkFound4;
                        if (bestCandidate == null || bestCandidate.BSSID == null || bestCandidate.level <= ClientModeImpl.this.mWifiInfo.getRssi() + 5) {
                            needRoam = false;
                        } else {
                            StringBuilder sb = new StringBuilder();
                            sb.append("There's available BSSID to roam to. Reassociate to the BSSID. ");
                            sb.append(ClientModeImpl.DBG ? bestCandidate.toString() : str2);
                            Log.d(ClientModeImpl.TAG, sb.toString());
                            needRoam = true;
                        }
                        boolean z2 = candidateCount > 0;
                        bestCandidateNetworkId = bestCandidateNetworkId2;
                    } else {
                        config = config7;
                        str2 = str7;
                        alternativeNetworkFound = false;
                        bestCandidateNetworkId = -1;
                    }
                    if (!needRoam && configs2 != null) {
                        Iterator<WifiConfiguration> it3 = configs2.iterator();
                        boolean alternativeNetworkFound5 = alternativeNetworkFound;
                        while (it3.hasNext()) {
                            WifiConfiguration conf = it3.next();
                            if (!conf.validatedInternetAccess || conf.semAutoReconnect != 1) {
                                it = it3;
                                currConfig = currConfig2;
                                configs = configs2;
                                str4 = str8;
                                alternativeNetworkFound2 = alternativeNetworkFound5;
                            } else {
                                String configSsid2 = conf.SSID;
                                it = it3;
                                if (conf.allowedKeyManagement.get(1) || conf.allowedKeyManagement.get(4)) {
                                    configuredSecurity = 2;
                                } else if (conf.allowedKeyManagement.get(8)) {
                                    configuredSecurity = 4;
                                } else if (conf.allowedKeyManagement.get(2) || conf.allowedKeyManagement.get(3)) {
                                    configuredSecurity = 3;
                                } else {
                                    configuredSecurity = conf.wepKeys[0] != null ? 1 : 0;
                                }
                                Iterator<ScanResult> it4 = scanResults.iterator();
                                while (it4.hasNext()) {
                                    currConfig = currConfig2;
                                    ScanResult scanResult2 = it4.next();
                                    configs = configs2;
                                    if (scanResult2.capabilities.contains(str8)) {
                                        str3 = str8;
                                        scanedSecurity = 1;
                                    } else {
                                        str3 = str8;
                                        if (scanResult2.capabilities.contains("SAE")) {
                                            scanedSecurity = 4;
                                        } else if (scanResult2.capabilities.contains("PSK")) {
                                            scanedSecurity = 2;
                                        } else if (scanResult2.capabilities.contains("EAP")) {
                                            scanedSecurity = 3;
                                        } else {
                                            scanedSecurity = 0;
                                        }
                                    }
                                    if (ClientModeImpl.this.mWifiInfo.getNetworkId() == conf.networkId || configSsid2 == null || configSsid2.length() <= 2) {
                                        alternativeNetworkFound3 = alternativeNetworkFound5;
                                    } else {
                                        alternativeNetworkFound3 = alternativeNetworkFound5;
                                        if (scanResult2.SSID.equals(configSsid2.substring(1, configSsid2.length() - 1)) && configuredSecurity == scanedSecurity && conf.getNetworkSelectionStatus().isNetworkEnabled() && !conf.isCaptivePortal && ((scanResult2.is24GHz() && scanResult2.level > -64) || (scanResult2.is5GHz() && scanResult2.level > -70))) {
                                            StringBuilder sb2 = new StringBuilder();
                                            sb2.append("There's internet available AP. Disable current AP. ");
                                            sb2.append(ClientModeImpl.DBG ? configSsid2 : str2);
                                            Log.d(ClientModeImpl.TAG, sb2.toString());
                                            alternativeNetworkFound5 = true;
                                            it3 = it;
                                            currConfig2 = currConfig;
                                            configs2 = configs;
                                            str8 = str3;
                                        }
                                    }
                                    it4 = it4;
                                    currConfig2 = currConfig;
                                    configs2 = configs;
                                    alternativeNetworkFound5 = alternativeNetworkFound3;
                                    str8 = str3;
                                }
                                currConfig = currConfig2;
                                configs = configs2;
                                str4 = str8;
                                alternativeNetworkFound2 = alternativeNetworkFound5;
                            }
                            alternativeNetworkFound5 = alternativeNetworkFound2;
                            it3 = it;
                            currConfig2 = currConfig;
                            configs2 = configs;
                            str8 = str3;
                        }
                        alternativeNetworkFound = alternativeNetworkFound5;
                    }
                    if (needRoamingInHighQuality) {
                        if (needRoam) {
                            ClientModeImpl.this.startRoamToNetwork(bestCandidateNetworkId, bestCandidate);
                        }
                        Iterator it5 = ClientModeImpl.this.mWifiNetworkCallbackList.iterator();
                        while (it5.hasNext()) {
                            ((ClientModeChannel.WifiNetworkCallback) it5.next()).handleResultRoamInLevel1State(needRoam);
                        }
                        return true;
                    } else if (needRoam) {
                        ClientModeImpl.this.startRoamToNetwork(bestCandidateNetworkId, bestCandidate);
                        return true;
                    } else if (!alternativeNetworkFound) {
                        return true;
                    } else {
                        if (config != null) {
                            Log.d(ClientModeImpl.TAG, "Current config's validatedInternetAccess sets as false because alternativeNetwork is Found.");
                            config2 = config;
                            config2.validatedInternetAccess = false;
                        } else {
                            config2 = config;
                        }
                        if (config2.getNetworkSelectionStatus().getNetworkSelectionStatus() != 2) {
                            ClientModeImpl.this.mWifiConfigManager.updateNetworkSelectionStatus(config2.networkId, 16);
                            Log.d(ClientModeImpl.TAG, "Disable the current network temporarily. DISABLED_POOR_LINK");
                            return true;
                        }
                        ClientModeImpl.this.disconnectCommand(0, 10);
                        Log.d(ClientModeImpl.TAG, "Already permanently disabled");
                        return true;
                    }
                case WifiMonitor.NETWORK_DISCONNECTION_EVENT /*{ENCODED_INT: 147460}*/:
                    ClientModeImpl.this.reportConnectionAttemptEnd(6, 1, 0);
                    if (ClientModeImpl.this.mLastDriverRoamAttempt != 0) {
                        long lastRoam = ClientModeImpl.this.mClock.getWallClockMillis() - ClientModeImpl.this.mLastDriverRoamAttempt;
                        ClientModeImpl.this.mLastDriverRoamAttempt = 0;
                    }
                    if (ClientModeImpl.unexpectedDisconnectedReason(message.arg2)) {
                        ClientModeImpl.this.mWifiDiagnostics.triggerBugReportDataCapture(5);
                    }
                    WifiConfiguration config8 = ClientModeImpl.this.getCurrentWifiConfiguration();
                    Log.d(ClientModeImpl.TAG, "disconnected reason " + message.arg2);
                    if (ClientModeImpl.this.mIWCMonitorChannel != null) {
                        ClientModeImpl.this.mIWCMonitorChannel.sendMessage((int) IWCMonitor.IWC_WIFI_DISCONNECTED, message.arg2);
                    }
                    if (!(ClientModeImpl.this.mUnstableApController == null || config8 == null || !ClientModeImpl.this.mUnstableApController.disconnect(ClientModeImpl.this.mWifiInfo.getBSSID(), ClientModeImpl.this.mWifiInfo.getRssi(), config8, message.arg2))) {
                        ClientModeImpl.this.report(10, ReportUtil.getReportDataForUnstableAp(config8.networkId, ClientModeImpl.this.mWifiInfo.getBSSID()));
                    }
                    if (ClientModeImpl.this.mVerboseLoggingEnabled) {
                        ClientModeImpl clientModeImpl8 = ClientModeImpl.this;
                        StringBuilder sb3 = new StringBuilder();
                        sb3.append("NETWORK_DISCONNECTION_EVENT in connected state BSSID=");
                        sb3.append(ClientModeImpl.this.mWifiInfo.getBSSID());
                        sb3.append(" RSSI=");
                        sb3.append(ClientModeImpl.this.mWifiInfo.getRssi());
                        sb3.append(" freq=");
                        sb3.append(ClientModeImpl.this.mWifiInfo.getFrequency());
                        sb3.append(" reason=");
                        sb3.append(message.arg2);
                        sb3.append(" Network Selection Status=");
                        if (config8 == null) {
                            str6 = "Unavailable";
                        } else {
                            str6 = config8.getNetworkSelectionStatus().getNetworkStatusString();
                        }
                        sb3.append(str6);
                        clientModeImpl8.log(sb3.toString());
                    }
                    if ((ClientModeImpl.mWlanAdvancedDebugState & 4) != 0) {
                        ClientModeImpl.this.sendBroadcastIssueTrackerSysDump(2);
                    }
                    z = true;
                    message2 = message;
                    handleStatus = true;
                    break;
                default:
                    z = true;
                    message2 = message;
                    handleStatus = false;
                    break;
            }
            if (handleStatus == z) {
                ClientModeImpl.this.logStateAndMessage(message2, this);
            }
            return handleStatus;
        }

        public void exit() {
            ClientModeImpl.this.logd("ClientModeImpl: Leaving Connected state");
            ClientModeImpl.this.mLastConnectedTime = -1;
            ClientModeImpl.this.mWifiConnectivityManager.handleConnectionStateChanged(3);
            ClientModeImpl.this.mLastDriverRoamAttempt = 0;
            ClientModeImpl.this.mWifiInjector.getWifiLastResortWatchdog().connectedStateTransition(false);
            ClientModeImpl.this.mDelayDisconnect.setEnable(false, 0);
            ClientModeImpl.this.mWifiConfigManager.setCurrentNetworkId(-1);
            if (ClientModeImpl.CSC_SUPPORT_5G_ANT_SHARE) {
                ClientModeImpl.this.sendIpcMessageToRilForLteu(4, false, false, false);
            }
            WifiConfiguration wifiConfiguration = this.mCurrentConfig;
            if (!(wifiConfiguration == null || wifiConfiguration.SSID == null)) {
                if (this.mCurrentConfig.allowedKeyManagement.get(0) || this.mCurrentConfig.allowedKeyManagement.get(1) || this.mCurrentConfig.allowedKeyManagement.get(4) || this.mCurrentConfig.allowedKeyManagement.get(22)) {
                    Context context = ClientModeImpl.this.mContext;
                    WifiMobileDeviceManager.auditLog(context, 5, true, ClientModeImpl.TAG, "Wi-Fi is disconnected from " + this.mCurrentConfig.getPrintableSsid() + " network using 802.11-2012 channel");
                } else if (this.mCurrentConfig.enterpriseConfig != null) {
                    if (this.mCurrentConfig.enterpriseConfig.getEapMethod() == 1) {
                        Context context2 = ClientModeImpl.this.mContext;
                        WifiMobileDeviceManager.auditLog(context2, 5, true, ClientModeImpl.TAG, "Wi-Fi is disconnected from " + this.mCurrentConfig.getPrintableSsid() + " network using EAP-TLS channel");
                    } else {
                        Context context3 = ClientModeImpl.this.mContext;
                        WifiMobileDeviceManager.auditLog(context3, 5, true, ClientModeImpl.TAG, "Wi-Fi is disconnected from " + this.mCurrentConfig.getPrintableSsid() + " network using 802.1X channel");
                    }
                }
            }
            if (ClientModeImpl.this.mWifiGeofenceManager.isSupported()) {
                ClientModeImpl.this.mWifiGeofenceManager.triggerStopLearning(this.mCurrentConfig);
            }
            if (ClientModeImpl.this.mWifiInjector.getSemWifiApChipInfo().supportWifiSharing() && OpBrandingLoader.Vendor.VZW != ClientModeImpl.mOpBranding) {
                ClientModeImpl clientModeImpl = ClientModeImpl.this;
                clientModeImpl.logd("Wifi got Disconnected in connectedstate, Send provisioning intent mIsAutoRoaming" + ClientModeImpl.this.mIsAutoRoaming);
                Intent provisionIntent = new Intent(SemWifiApBroadcastReceiver.START_PROVISIONING);
                provisionIntent.putExtra("wState", 2);
                if (ClientModeImpl.this.mContext != null) {
                    ClientModeImpl.this.mContext.sendBroadcast(provisionIntent);
                    provisionIntent.setPackage("com.android.settings");
                    ClientModeImpl.this.mContext.sendBroadcast(provisionIntent);
                }
            } else if (!ClientModeImpl.this.mWifiInjector.getSemWifiApChipInfo().supportWifiSharing() || OpBrandingLoader.Vendor.VZW != ClientModeImpl.mOpBranding || !ClientModeImpl.this.isWifiSharingProvisioning()) {
                if (!ClientModeImpl.this.mIsAutoRoaming && OpBrandingLoader.Vendor.VZW == ClientModeImpl.mOpBranding) {
                    SemWifiFrameworkUxUtils.showToast(ClientModeImpl.this.mContext, 30, null);
                }
            } else if (!ClientModeImpl.this.mIsAutoRoaming) {
                Intent provisionIntent2 = new Intent(SemWifiApBroadcastReceiver.START_PROVISIONING);
                provisionIntent2.putExtra("wState", 2);
                if (ClientModeImpl.this.mContext != null) {
                    ClientModeImpl.this.mContext.sendBroadcast(provisionIntent2);
                    provisionIntent2.setPackage("com.android.settings");
                    ClientModeImpl.this.mContext.sendBroadcast(provisionIntent2);
                }
            }
            if (ClientModeImpl.this.mSemLocationManager != null) {
                if (ClientModeImpl.DBG) {
                    Log.d(ClientModeImpl.TAG, "Remove location updates");
                }
                if (ClientModeImpl.this.mLocationRequestNetworkId == -1) {
                    ClientModeImpl.this.mSemLocationManager.removeLocationUpdates(ClientModeImpl.this.mSemLocationListener);
                }
                if (ClientModeImpl.this.mLocationPendingIntent != null) {
                    ClientModeImpl.this.mSemLocationManager.removePassiveLocation(ClientModeImpl.this.mLocationPendingIntent);
                }
            }
            ClientModeImpl clientModeImpl2 = ClientModeImpl.this;
            clientModeImpl2.report(1, ReportUtil.getReportDataForDisconnectTranstion(clientModeImpl2.mScreenOn, 2, ClientModeImpl.this.mWifiAdpsEnabled.get() ? 1 : 0));
            if (WifiRoamingAssistant.getInstance() != null) {
                WifiRoamingAssistant.getInstance().updateRcl(null, 0, false);
            }
            ClientModeImpl.this.clearEDMWiFiPolicy();
        }
    }

    class DisconnectingState extends State {
        DisconnectingState() {
        }

        public void enter() {
            if (ClientModeImpl.this.mVerboseLoggingEnabled) {
                ClientModeImpl.this.logd(" Enter DisconnectingState State screenOn=" + ClientModeImpl.this.mScreenOn);
            }
            ClientModeImpl.this.mDisconnectingWatchdogCount++;
            ClientModeImpl.this.logd("Start Disconnecting Watchdog " + ClientModeImpl.this.mDisconnectingWatchdogCount);
            ClientModeImpl clientModeImpl = ClientModeImpl.this;
            clientModeImpl.sendMessageDelayed(clientModeImpl.obtainMessage(ClientModeImpl.CMD_DISCONNECTING_WATCHDOG_TIMER, clientModeImpl.mDisconnectingWatchdogCount, 0), RttServiceImpl.HAL_RANGING_TIMEOUT_MS);
        }

        public boolean processMessage(Message message) {
            boolean handleStatus = true;
            int i = message.what;
            if (i != ClientModeImpl.CMD_DISCONNECT) {
                if (i != ClientModeImpl.CMD_DISCONNECTING_WATCHDOG_TIMER) {
                    if (i != 147462) {
                        handleStatus = false;
                    } else {
                        ClientModeImpl.this.deferMessage(message);
                        ClientModeImpl.this.handleNetworkDisconnect();
                        ClientModeImpl clientModeImpl = ClientModeImpl.this;
                        clientModeImpl.transitionTo(clientModeImpl.mDisconnectedState);
                    }
                } else if (ClientModeImpl.this.mDisconnectingWatchdogCount == message.arg1) {
                    if (ClientModeImpl.this.mVerboseLoggingEnabled) {
                        ClientModeImpl.this.log("disconnecting watchdog! -> disconnect");
                    }
                    ClientModeImpl.this.handleNetworkDisconnect();
                    ClientModeImpl clientModeImpl2 = ClientModeImpl.this;
                    clientModeImpl2.transitionTo(clientModeImpl2.mDisconnectedState);
                }
            } else if (ClientModeImpl.this.mVerboseLoggingEnabled) {
                ClientModeImpl.this.log("Ignore CMD_DISCONNECT when already disconnecting.");
            }
            if (handleStatus) {
                ClientModeImpl.this.logStateAndMessage(message, this);
            }
            return handleStatus;
        }
    }

    class DisconnectedState extends State {
        DisconnectedState() {
        }

        public void enter() {
            Log.i(ClientModeImpl.TAG, "disconnectedstate enter");
            ClientModeImpl clientModeImpl = ClientModeImpl.this;
            clientModeImpl.mIsRoamNetwork = false;
            clientModeImpl.setRoamTriggered(false);
            if (ClientModeImpl.this.mTemporarilyDisconnectWifi) {
                ClientModeImpl.this.p2pSendMessage(WifiP2pServiceImpl.DISCONNECT_WIFI_RESPONSE);
                return;
            }
            if (ClientModeImpl.this.mVerboseLoggingEnabled) {
                ClientModeImpl clientModeImpl2 = ClientModeImpl.this;
                clientModeImpl2.logd(" Enter DisconnectedState screenOn=" + ClientModeImpl.this.mScreenOn);
            }
            ClientModeImpl.this.removeMessages(ClientModeImpl.CMD_THREE_TIMES_SCAN_IN_IDLE);
            ClientModeImpl.this.sendMessage(ClientModeImpl.CMD_THREE_TIMES_SCAN_IN_IDLE);
            ClientModeImpl.this.mWifiConnectivityManager.handleConnectionStateChanged(2);
            if (ClientModeImpl.this.mIsAutoRoaming && OpBrandingLoader.Vendor.VZW == ClientModeImpl.mOpBranding) {
                if (!ClientModeImpl.this.mWifiInjector.getSemWifiApChipInfo().supportWifiSharing() || !ClientModeImpl.this.isWifiSharingProvisioning()) {
                    SemWifiFrameworkUxUtils.showToast(ClientModeImpl.this.mContext, 30, null);
                } else {
                    ClientModeImpl clientModeImpl3 = ClientModeImpl.this;
                    clientModeImpl3.logd("Wifi got in DisconnectedState, Send provisioning intent mIsAutoRoaming" + ClientModeImpl.this.mIsAutoRoaming);
                    Intent provisionIntent = new Intent(SemWifiApBroadcastReceiver.START_PROVISIONING);
                    provisionIntent.putExtra("wState", 2);
                    if (ClientModeImpl.this.mContext != null) {
                        ClientModeImpl.this.mContext.sendBroadcast(provisionIntent);
                        provisionIntent.setPackage("com.android.settings");
                        ClientModeImpl.this.mContext.sendBroadcast(provisionIntent);
                    }
                }
            }
            ClientModeImpl.this.mIsAutoRoaming = false;
            ClientModeImpl.this.mWifiScoreReport.resetNetworkAgent();
        }

        public boolean processMessage(Message message) {
            boolean handleStatus = true;
            boolean screenOn = false;
            switch (message.what) {
                case ClientModeImpl.CMD_DISCONNECT /*{ENCODED_INT: 131145}*/:
                    ClientModeImpl.this.mWifiMetrics.logStaEvent(15, 2);
                    ClientModeImpl.this.mWifiNative.disconnect(ClientModeImpl.this.mInterfaceName);
                    break;
                case ClientModeImpl.CMD_RECONNECT /*{ENCODED_INT: 131146}*/:
                case ClientModeImpl.CMD_REASSOCIATE /*{ENCODED_INT: 131147}*/:
                    if (!ClientModeImpl.this.mTemporarilyDisconnectWifi) {
                        handleStatus = false;
                        break;
                    }
                    break;
                case ClientModeImpl.CMD_SCREEN_STATE_CHANGED /*{ENCODED_INT: 131167}*/:
                    if (message.arg1 != 0) {
                        screenOn = true;
                    }
                    if (ClientModeImpl.this.mScreenOn != screenOn) {
                        ClientModeImpl.this.handleScreenStateChanged(screenOn);
                        break;
                    }
                    break;
                case ClientModeImpl.CMD_THREE_TIMES_SCAN_IN_IDLE /*{ENCODED_INT: 131381}*/:
                    if (ClientModeImpl.access$22708(ClientModeImpl.this) < 2 && ClientModeImpl.this.mScreenOn) {
                        Log.e(ClientModeImpl.TAG, "DisconnectedState  CMD_THREE_TIMES_SCAN_IN_IDLE && mScreenOn");
                        ClientModeImpl.this.mScanRequestProxy.startScan(1000, ClientModeImpl.this.mContext.getOpPackageName());
                        ClientModeImpl.this.sendMessageDelayed(ClientModeImpl.CMD_THREE_TIMES_SCAN_IN_IDLE, 8000);
                        break;
                    }
                case WifiP2pServiceImpl.P2P_CONNECTION_CHANGED /*{ENCODED_INT: 143371}*/:
                    ClientModeImpl.this.mP2pConnected.set(((NetworkInfo) message.obj).isConnected());
                    break;
                case WifiMonitor.NETWORK_DISCONNECTION_EVENT /*{ENCODED_INT: 147460}*/:
                    if (message.arg2 == 15) {
                        ClientModeImpl.this.mWifiInjector.getWifiLastResortWatchdog().noteConnectionFailureAndTriggerIfNeeded(ClientModeImpl.this.getTargetSsid(), message.obj == null ? ClientModeImpl.this.mTargetRoamBSSID : (String) message.obj, 2);
                    }
                    ClientModeImpl clientModeImpl = ClientModeImpl.this;
                    int i = clientModeImpl.mTargetNetworkId;
                    String str = (String) message.obj;
                    if (message.arg1 != 0) {
                        screenOn = true;
                    }
                    clientModeImpl.checkAndUpdateUnstableAp(i, str, screenOn, message.arg2);
                    break;
                case WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT /*{ENCODED_INT: 147462}*/:
                    StateChangeResult stateChangeResult = (StateChangeResult) message.obj;
                    if (ClientModeImpl.this.mVerboseLoggingEnabled) {
                        ClientModeImpl.this.logd("SUPPLICANT_STATE_CHANGE_EVENT state=" + stateChangeResult.state + " -> state= " + WifiInfo.getDetailedStateOf(stateChangeResult.state));
                    }
                    if (SupplicantState.isConnecting(stateChangeResult.state)) {
                        WifiConfiguration config = ClientModeImpl.this.mWifiConfigManager.getConfiguredNetwork(stateChangeResult.networkId);
                        ClientModeImpl.this.mWifiInfo.setFQDN(null);
                        ClientModeImpl.this.mWifiInfo.setOsuAp(false);
                        ClientModeImpl.this.mWifiInfo.setProviderFriendlyName(null);
                        if (config != null && (config.isPasspoint() || config.osu)) {
                            if (config.isPasspoint()) {
                                ClientModeImpl.this.mWifiInfo.setFQDN(config.FQDN);
                            } else {
                                ClientModeImpl.this.mWifiInfo.setOsuAp(true);
                            }
                            ClientModeImpl.this.mWifiInfo.setProviderFriendlyName(config.providerFriendlyName);
                        }
                    }
                    ClientModeImpl.this.setNetworkDetailedState(WifiInfo.getDetailedStateOf(stateChangeResult.state));
                    handleStatus = false;
                    break;
                default:
                    handleStatus = false;
                    break;
            }
            if (handleStatus) {
                ClientModeImpl.this.logStateAndMessage(message, this);
            }
            return handleStatus;
        }

        public void exit() {
            ClientModeImpl.this.mWifiConnectivityManager.handleConnectionStateChanged(3);
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void replyToMessage(Message msg, int what) {
        if (msg.replyTo != null) {
            this.mReplyChannel.replyToMessage(msg, obtainMessageWithWhatAndArg2(msg, what));
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void replyToMessage(Message msg, int what, int arg1) {
        if (msg.replyTo != null) {
            Message dstMsg = obtainMessageWithWhatAndArg2(msg, what);
            dstMsg.arg1 = arg1;
            this.mReplyChannel.replyToMessage(msg, dstMsg);
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void replyToMessage(Message msg, int what, Object obj) {
        if (msg.replyTo != null) {
            Message dstMsg = obtainMessageWithWhatAndArg2(msg, what);
            dstMsg.obj = obj;
            this.mReplyChannel.replyToMessage(msg, dstMsg);
        }
    }

    private Message obtainMessageWithWhatAndArg2(Message srcMsg, int what) {
        Message msg = Message.obtain();
        msg.what = what;
        msg.arg2 = srcMsg.arg2;
        return msg;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void broadcastWifiCredentialChanged(int wifiCredentialEventType, WifiConfiguration config) {
        if (config != null && config.preSharedKey != null) {
            Intent intent = new Intent("android.net.wifi.WIFI_CREDENTIAL_CHANGED");
            intent.putExtra("ssid", config.SSID);
            intent.putExtra("et", wifiCredentialEventType);
            this.mContext.sendBroadcastAsUser(intent, UserHandle.CURRENT, "android.permission.RECEIVE_WIFI_CREDENTIAL_CHANGE");
        }
    }

    /* access modifiers changed from: package-private */
    public void handleGsmAuthRequest(TelephonyUtil.SimAuthRequestData requestData) {
        WifiConfiguration wifiConfiguration = this.mTargetWifiConfiguration;
        if (wifiConfiguration == null || wifiConfiguration.networkId == requestData.networkId) {
            logd("id matches mTargetWifiConfiguration");
            String response = TelephonyUtil.getGsmSimAuthResponse(requestData.data, getTelephonyManager());
            if (response == null && (response = TelephonyUtil.getGsmSimpleSimAuthResponse(requestData.data, getTelephonyManager())) == null) {
                response = TelephonyUtil.getGsmSimpleSimNoLengthAuthResponse(requestData.data, getTelephonyManager());
            }
            if (response == null || response.length() == 0) {
                this.mWifiNative.simAuthFailedResponse(this.mInterfaceName, requestData.networkId);
                return;
            }
            logv("Supplicant Response -" + response);
            this.mWifiNative.simAuthResponse(this.mInterfaceName, requestData.networkId, WifiNative.SIM_AUTH_RESP_TYPE_GSM_AUTH, response);
            return;
        }
        logd("id does not match mTargetWifiConfiguration");
    }

    /* access modifiers changed from: package-private */
    public void handle3GAuthRequest(TelephonyUtil.SimAuthRequestData requestData) {
        WifiConfiguration wifiConfiguration = this.mTargetWifiConfiguration;
        if (wifiConfiguration == null || wifiConfiguration.networkId == requestData.networkId) {
            logd("id matches mTargetWifiConfiguration");
            TelephonyUtil.SimAuthResponseData response = TelephonyUtil.get3GAuthResponse(requestData, getTelephonyManager());
            if (response != null) {
                this.mWifiNative.simAuthResponse(this.mInterfaceName, requestData.networkId, response.type, response.response);
            } else {
                this.mWifiNative.umtsAuthFailedResponse(this.mInterfaceName, requestData.networkId);
            }
        } else {
            logd("id does not match mTargetWifiConfiguration");
        }
    }

    public void startConnectToNetwork(int networkId, int uid, String bssid) {
        sendMessage(CMD_START_CONNECT, networkId, uid, bssid);
    }

    public void startRoamToNetwork(int networkId, ScanResult scanResult) {
        sendMessage(CMD_START_ROAM, networkId, 0, scanResult);
    }

    public void enableWifiConnectivityManager(boolean enabled) {
        sendMessage(CMD_ENABLE_WIFI_CONNECTIVITY_MANAGER, enabled ? 1 : 0);
    }

    static boolean unexpectedDisconnectedReason(int reason) {
        return reason == 2 || reason == 6 || reason == 7 || reason == 8 || reason == 9 || reason == 14 || reason == 15 || reason == 16 || reason == 18 || reason == 19 || reason == 23 || reason == 34;
    }

    public void updateWifiMetrics() {
        this.mWifiMetrics.updateSavedNetworks(this.mWifiConfigManager.getSavedNetworks(1010));
        this.mPasspointManager.updateMetrics();
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean deleteNetworkConfigAndSendReply(Message message, boolean calledFromForget) {
        boolean success = this.mWifiConfigManager.removeNetwork(message.arg1, message.sendingUid, message.arg2);
        if (!success) {
            loge("Failed to remove network");
        }
        if (calledFromForget) {
            if (success) {
                replyToMessage(message, 151558);
                broadcastWifiCredentialChanged(1, (WifiConfiguration) message.obj);
                return true;
            }
            replyToMessage(message, 151557, 0);
            return false;
        } else if (success) {
            replyToMessage(message, message.what, 1);
            return true;
        } else {
            this.mMessageHandlingStatus = -2;
            replyToMessage(message, message.what, -1);
            return false;
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private NetworkUpdateResult saveNetworkConfigAndSendReply(Message message) {
        WifiConfiguration config = (WifiConfiguration) message.obj;
        if (config == null) {
            loge("SAVE_NETWORK with null configuration " + this.mSupplicantStateTracker.getSupplicantStateName() + " my state " + getCurrentState().getName());
            this.mMessageHandlingStatus = -2;
            replyToMessage(message, 151560, 0);
            return new NetworkUpdateResult(-1);
        }
        config.priority = this.mWifiConfigManager.increaseAndGetPriority();
        this.mWifiConfigManager.updateBssidWhitelist(config, this.mScanRequestProxy.getScanResults());
        NetworkUpdateResult result = this.mWifiConfigManager.addOrUpdateNetwork(config, message.sendingUid);
        if (!result.isSuccess()) {
            loge("SAVE_NETWORK adding/updating config=" + config + " failed");
            this.mMessageHandlingStatus = -2;
            replyToMessage(message, 151560, 0);
            return result;
        } else if (!this.mWifiConfigManager.enableNetwork(result.getNetworkId(), false, message.sendingUid)) {
            loge("SAVE_NETWORK enabling config=" + config + " failed");
            this.mMessageHandlingStatus = -2;
            replyToMessage(message, 151560, 0);
            return new NetworkUpdateResult(-1);
        } else {
            broadcastWifiCredentialChanged(0, config);
            replyToMessage(message, 151561);
            return result;
        }
    }

    private static String getLinkPropertiesSummary(LinkProperties lp) {
        List<String> attributes = new ArrayList<>(6);
        if (lp.hasIPv4Address()) {
            attributes.add("v4");
        }
        if (lp.hasIPv4DefaultRoute()) {
            attributes.add("v4r");
        }
        if (lp.hasIPv4DnsServer()) {
            attributes.add("v4dns");
        }
        if (lp.hasGlobalIPv6Address()) {
            attributes.add("v6");
        }
        if (lp.hasIPv6DefaultRoute()) {
            attributes.add("v6r");
        }
        if (lp.hasIPv6DnsServer()) {
            attributes.add("v6dns");
        }
        return TextUtils.join(" ", attributes);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private String getTargetSsid() {
        WifiConfiguration currentConfig = this.mWifiConfigManager.getConfiguredNetwork(this.mTargetNetworkId);
        if (currentConfig != null) {
            return currentConfig.SSID;
        }
        return null;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean p2pSendMessage(int what) {
        AsyncChannel asyncChannel = this.mWifiP2pChannel;
        if (asyncChannel == null) {
            return false;
        }
        asyncChannel.sendMessage(what);
        return true;
    }

    private boolean p2pSendMessage(int what, int arg1) {
        AsyncChannel asyncChannel = this.mWifiP2pChannel;
        if (asyncChannel == null) {
            return false;
        }
        asyncChannel.sendMessage(what, arg1);
        return true;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean hasConnectionRequests() {
        return this.mNetworkFactory.hasConnectionRequests() || this.mUntrustedNetworkFactory.hasConnectionRequests();
    }

    public boolean getIpReachabilityDisconnectEnabled() {
        return this.mIpReachabilityDisconnectEnabled;
    }

    public void setIpReachabilityDisconnectEnabled(boolean enabled) {
        this.mIpReachabilityDisconnectEnabled = enabled;
    }

    public boolean syncInitialize(AsyncChannel channel) {
        boolean result = false;
        if (this.mIsShutdown) {
            Log.d(TAG, "ignore processing beause shutdown is held");
            return false;
        }
        Message resultMsg = channel.sendMessageSynchronously((int) CMD_INITIALIZE);
        if (resultMsg.arg1 != -1) {
            result = true;
        }
        resultMsg.recycle();
        return result;
    }

    /* access modifiers changed from: package-private */
    public void setAutoConnectCarrierApEnabled(boolean enabled) {
        sendMessage(CMD_AUTO_CONNECT_CARRIER_AP_ENABLED, enabled ? 1 : 0, 0);
    }

    public void addNetworkRequestMatchCallback(IBinder binder, INetworkRequestMatchCallback callback, int callbackIdentifier) {
        this.mNetworkFactory.addCallback(binder, callback, callbackIdentifier);
    }

    public void removeNetworkRequestMatchCallback(int callbackIdentifier) {
        this.mNetworkFactory.removeCallback(callbackIdentifier);
    }

    public void removeNetworkRequestUserApprovedAccessPointsForApp(String packageName) {
        this.mNetworkFactory.removeUserApprovedAccessPointsForApp(packageName);
    }

    public void clearNetworkRequestUserApprovedAccessPoints() {
        this.mNetworkFactory.clear();
    }

    public String getFactoryMacAddress() {
        MacAddress macAddress = this.mWifiNative.getFactoryMacAddress(this.mInterfaceName);
        if (macAddress != null) {
            return macAddress.toString();
        }
        if (!this.mConnectedMacRandomzationSupported) {
            return this.mWifiNative.getMacAddress(this.mInterfaceName);
        }
        return null;
    }

    public void setDeviceMobilityState(int state) {
        this.mWifiConnectivityManager.setDeviceMobilityState(state);
    }

    public void updateWifiUsabilityScore(int seqNum, int score, int predictionHorizonSec) {
        this.mWifiMetrics.incrementWifiUsabilityScoreCount(seqNum, score, predictionHorizonSec);
    }

    @VisibleForTesting
    public void probeLink(WifiNative.SendMgmtFrameCallback callback, int mcs) {
        this.mWifiNative.probeLink(this.mInterfaceName, MacAddress.fromString(this.mWifiInfo.getBSSID()), callback, mcs);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void updateWifiInfoForVendors(ScanResult scanResult) {
        WifiConfiguration config;
        ScanDetailCache scanDetailCache;
        ScanDetail scanDetail;
        String capabilities;
        if (mOpBranding == OpBrandingLoader.Vendor.KTT && (capabilities = scanResult.capabilities) != null) {
            if (!capabilities.contains("[VSI]") || !capabilities.contains("[VHT]")) {
                if (DBG) {
                    Log.d(TAG, "setGigaAp: false, bssid: " + scanResult.BSSID + ", capa: " + capabilities);
                }
                this.mWifiInfo.setGigaAp(false);
            } else {
                if (DBG) {
                    Log.d(TAG, "setGigaAp: true");
                }
                this.mWifiInfo.setGigaAp(true);
            }
        }
        if (this.mIsPasspointEnabled && mOpBranding == OpBrandingLoader.Vendor.SKT && this.mLastNetworkId != -1 && !TextUtils.isEmpty(scanResult.BSSID) && (config = this.mWifiConfigManager.getConfiguredNetwork(this.mLastNetworkId)) != null && config.semIsVendorSpecificSsid && config.isPasspoint() && !config.isHomeProviderNetwork && (scanDetailCache = this.mWifiConfigManager.getScanDetailCacheForNetwork(this.mLastNetworkId)) != null && (scanDetail = scanDetailCache.getScanDetail(scanResult.BSSID)) != null && scanDetail.getNetworkDetail().isInterworking()) {
            Map<Constants.ANQPElementType, ANQPElement> anqpElements = this.mPasspointManager.getANQPElements(scanResult);
            if (anqpElements == null || anqpElements.size() <= 0) {
                Log.d(TAG, "There is no anqpElements, so send anqp query to " + scanResult.SSID);
                this.mPasspointManager.forceRequestAnqp(scanResult);
                return;
            }
            HSWanMetricsElement hSWanMetricsElement = (HSWanMetricsElement) anqpElements.get(Constants.ANQPElementType.HSWANMetrics);
            VenueNameElement vne = (VenueNameElement) anqpElements.get(Constants.ANQPElementType.ANQPVenueName);
            if (vne != null && !vne.getNames().isEmpty()) {
                String venueName = vne.getNames().get(0).getText();
                Log.i(TAG, "updateVenueNameInWifiInfo: venueName is " + venueName);
                this.mWifiInfo.setVenueName(venueName);
            }
        }
    }

    public boolean isWifiOnly() {
        if (this.mIsWifiOnly == -1) {
            checkAndSetConnectivityInstance();
            ConnectivityManager connectivityManager = this.mCm;
            if (connectivityManager == null || !connectivityManager.isNetworkSupported(0)) {
                this.mIsWifiOnly = 1;
            } else {
                this.mIsWifiOnly = 0;
            }
        }
        return this.mIsWifiOnly == 1;
    }

    /* access modifiers changed from: package-private */
    public void setShutdown() {
        this.mIsShutdown = true;
        this.mWifiConfigManager.semStopToSaveStore();
        if (getCurrentState() == this.mConnectedState) {
            setAnalyticsUserDisconnectReason(WifiNative.f20xe9115267);
        }
        if (this.mDelayDisconnect.isEnabled()) {
            this.mDelayDisconnect.setEnable(false, 0);
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void updateAdpsState() {
        this.mWifiNative.setAdps(this.mInterfaceName, this.mWifiAdpsEnabled.get());
        this.mBigDataManager.addOrUpdateValue(13, this.mWifiAdpsEnabled.get() ? 1 : 0);
    }

    /* JADX WARN: Multi-variable type inference failed */
    /* JADX WARN: Type inference failed for: r0v1 */
    /* JADX WARN: Type inference failed for: r0v2, types: [int] */
    /* JADX WARN: Type inference failed for: r0v3 */
    /* JADX WARN: Type inference failed for: r0v4 */
    /* JADX WARN: Type inference failed for: r0v6 */
    /* JADX WARN: Type inference failed for: r0v7 */
    /* JADX WARNING: Unknown variable types count: 1 */
    public synchronized int callSECApi(Message msg) {
        String pkgName;
        if (this.mVerboseLoggingEnabled) {
            Log.i(getName(), "callSECApi what=" + msg.what);
        }
        ?? r0 = -1;
        r0 = -1;
        r0 = -1;
        int i = msg.what;
        boolean z = true;
        boolean z2 = true;
        boolean z3 = true;
        if (i != 24) {
            if (i == 34) {
                r0 = this.mWifiConfigManager.isSkipInternetCheck(msg.arg1);
            } else if (i == 77) {
                sendMessage(obtainMessage(CMD_SEC_LOGGING, 0, 0, msg.obj));
                return 0;
            } else if (i == 79) {
                Bundle args = (Bundle) msg.obj;
                if (args == null) {
                    return this.mSemSarManager.isGripSensorEnabled() ? 1 : 0;
                }
                int enable = args.getInt("enable");
                SemSarManager semSarManager = this.mSemSarManager;
                if (enable != 1) {
                    z = false;
                }
                semSarManager.enableGripSensorMonitor(z);
                return 0;
            } else if (i == 222) {
                Bundle args2 = (Bundle) msg.obj;
                if (args2.containsKey("pkgNames") && args2.containsKey("scanTypes") && args2.containsKey("scanDelays")) {
                    String[] pkgNames = args2.getStringArray("pkgNames");
                    int[] scanTypes = args2.getIntArray("scanTypes");
                    int[] scanDelays = args2.getIntArray("scanDelays");
                    if (pkgNames.length == scanTypes.length && scanTypes.length == scanDelays.length) {
                        for (int i2 = 0; i2 < pkgNames.length; i2++) {
                            this.mScanRequestProxy.semSetCustomScanPolicy(pkgNames[i2], scanTypes[i2], scanDelays[i2]);
                        }
                    }
                } else if (args2.containsKey("pkgName") && args2.containsKey("scanType") && args2.containsKey("scanDelay") && (pkgName = args2.getString("pkgName")) != null && pkgName.length() > 0) {
                    this.mScanRequestProxy.semSetCustomScanPolicy(pkgName, args2.getInt("scanType", 0), args2.getInt("scanDelay", 0));
                }
                int duration = args2.getInt("duration", -1);
                if (duration != -1) {
                    this.mScanRequestProxy.semSetMaxDurationForCachedScan(duration);
                }
                int useSMD = args2.getInt("useSMD", -1);
                if (useSMD != -1) {
                    ScanRequestProxy scanRequestProxy = this.mScanRequestProxy;
                    if (useSMD != 1) {
                        z3 = false;
                    }
                    scanRequestProxy.semUseSMDForCachedScan(z3);
                }
                return 0;
            } else if (i == 267) {
                this.mBlockFccChannelCmd = ((Bundle) msg.obj).getBoolean("enable");
                return 0;
            } else if (i == 283) {
                Bundle args3 = (Bundle) msg.obj;
                if (args3 == null) {
                    return -1;
                }
                int type = args3.getInt("type");
                if (type == 0) {
                    Log.i(TAG, "pktlog filter enabled again, size changed to 320 default value again");
                    Log.i(TAG, "WLAN_ADVANCED_DEBUG_UNWANTED changed to false");
                    Log.i(TAG, "WLAN_ADVANCED_DEBUG_DISC changed to false");
                    this.mWifiNative.enablePktlogFilter(this.mInterfaceName, true);
                    this.mWifiNative.changePktlogSize(this.mInterfaceName, "320");
                    mWlanAdvancedDebugState = 0;
                } else if (type == 1) {
                    Log.i(TAG, "pktlog filter removed, size changed to 1280");
                    mWlanAdvancedDebugState = 1 | mWlanAdvancedDebugState;
                    this.mWifiNative.enablePktlogFilter(this.mInterfaceName, false);
                    this.mWifiNative.changePktlogSize(this.mInterfaceName, "1920");
                } else if (type == 2) {
                    Log.i(TAG, "WLAN_ADVANCED_DEBUG_UNWANTED changed to true");
                    mWlanAdvancedDebugState = 2 | mWlanAdvancedDebugState;
                } else if (type == 4) {
                    Log.i(TAG, "WLAN_ADVANCED_DEBUG_DISC changed to true");
                    mWlanAdvancedDebugState |= 4;
                } else if (type == 16) {
                    Log.i(TAG, "WLAN_ADVANCED_DEBUG_UNWANTED_PANIC changed to true");
                    mWlanAdvancedDebugState = 16 | mWlanAdvancedDebugState;
                } else if (type == 32) {
                    Log.i(TAG, "WLAN_ADVANCED_DEBUG_ELE_DEBUG changed to true");
                    mWlanAdvancedDebugState = 32 | mWlanAdvancedDebugState;
                } else if (type == 64) {
                    return mWlanAdvancedDebugState;
                }
                return 0;
            } else if (i == 330) {
                sendMessage(obtainMessage(CMD_REPLACE_PUBLIC_DNS, 0, 0, msg.obj));
                return 0;
            } else if (i == 407) {
                Bundle args4 = (Bundle) msg.obj;
                if (args4 == null) {
                    return -1;
                }
                int enable2 = args4.getInt("enable", -1);
                if (enable2 != -1) {
                    this.mWifiNative.setLatencyCritical(this.mInterfaceName, enable2);
                }
                return 0;
            } else if (i == 500) {
                this.mWifiB2bConfigPolicy.setWiFiConfiguration((Bundle) msg.obj);
                updateEDMWiFiPolicy();
                Log.d(TAG, "SEC_COMMAND_ID_SET_EDM_WIFI_POLICY: " + -1);
            } else if (i == 81) {
                WifiDelayDisconnect wifiDelayDisconnect = this.mDelayDisconnect;
                if (msg.arg1 != 1) {
                    z2 = false;
                }
                wifiDelayDisconnect.setEnable(z2, (long) msg.arg2);
                return 0;
            } else if (i == 82) {
                r0 = isRoaming();
            } else if (i == 280) {
                Bundle args5 = (Bundle) msg.obj;
                if (args5 == null) {
                    return -1;
                }
                if (args5.containsKey("enable")) {
                    if (args5.getInt("enable") == 1) {
                        this.mSemSarManager.enable_WiFi_PowerBackoff(true);
                    } else {
                        this.mSemSarManager.enable_WiFi_PowerBackoff(false);
                    }
                } else if (args5.containsKey("enable5G")) {
                    if (args5.getInt("enable5G") == 1) {
                        this.mSemSarManager.set5GSarBackOff(4);
                    } else {
                        this.mSemSarManager.set5GSarBackOff(3);
                    }
                }
                return 0;
            } else if (i == 281) {
                Bundle args6 = (Bundle) msg.obj;
                if (args6 == null) {
                    return -1;
                }
                if (args6.getInt("enable") == 1) {
                    this.mWifiNative.setMaxDtimInSuspend(this.mInterfaceName, true);
                } else {
                    this.mWifiNative.setMaxDtimInSuspend(this.mInterfaceName, false);
                }
                return 0;
            } else if (this.mVerboseLoggingEnabled) {
                Log.e(TAG, "ignore message : not implementation yet");
            }
            return r0;
        }
        if (((Bundle) msg.obj).getBoolean("state")) {
            mRssiPollingScreenOffEnabled |= 1;
        } else {
            mRssiPollingScreenOffEnabled &= -2;
        }
        if (mRssiPollingScreenOffEnabled != 0) {
            if (!this.mEnableRssiPolling) {
                enableRssiPolling(true);
            }
        } else if (this.mEnableRssiPolling && !this.mScreenOn) {
            enableRssiPolling(false);
        }
        return 0;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean processMessageOnDefaultStateForCallSECApiAsync(Message msg) {
        Message innerMsg = (Message) msg.obj;
        if (innerMsg == null) {
            Log.e(TAG, "CMD_SEC_API_ASYNC, invalid innerMsg");
            return false;
        } else if (innerMsg.what != 242) {
            return false;
        } else {
            this.mWifiConfigManager.removeFilesInDataMiscDirectory();
            this.mWifiConfigManager.removeFilesInDataMiscCeDirectory();
            return true;
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void checkAndUpdateUnstableAp(int networkId, String bssid, boolean locallyGenerated, int disconnectReason) {
        if (this.mVerboseLoggingEnabled) {
            Log.d(TAG, "checkAndUpdateUnstableAp netId:" + networkId + ", " + bssid + ", locally:" + locallyGenerated + ", reason:" + disconnectReason);
        }
        if (networkId == -1) {
            Log.d(TAG, "disconnected, can't get network id");
        }
        if (TextUtils.isEmpty(bssid)) {
            Log.d(TAG, "disconnected, can't get bssid");
            return;
        }
        boolean isSameNetwork = true;
        boolean isHotspotAp = false;
        ScanDetailCache scanDetailCache = this.mWifiConfigManager.getScanDetailCacheForNetwork(networkId);
        if (scanDetailCache != null) {
            ScanResult scanResult = scanDetailCache.getScanResult(bssid);
            if (scanResult == null) {
                Log.i(TAG, "disconnected, but not for current network");
                isSameNetwork = false;
            } else if (scanResult.capabilities.contains("[SEC80]")) {
                isHotspotAp = true;
            }
        }
        UnstableApController unstableApController = this.mUnstableApController;
        if (unstableApController != null && !locallyGenerated && isSameNetwork) {
            if (disconnectReason == 77) {
                this.mWifiConfigManager.updateNetworkSelectionStatus(networkId, 20);
            } else {
                if (unstableApController.disconnectWithAuthFail(networkId, bssid, this.mWifiInfo.getRssi(), disconnectReason, getCurrentState() == this.mConnectedState, isHotspotAp)) {
                    report(10, ReportUtil.getReportDataForUnstableAp(networkId, bssid));
                }
            }
        }
        if (isSameNetwork && getCurrentState() != this.mConnectedState) {
            report(13, ReportUtil.getReportDataForL2ConnectFail(networkId, bssid));
        }
    }

    public boolean syncSetRoamTrigger(int roamTrigger) {
        return this.mWifiNative.setRoamTrigger(this.mInterfaceName, roamTrigger);
    }

    public int syncGetRoamTrigger() {
        return this.mWifiNative.getRoamTrigger(this.mInterfaceName);
    }

    public boolean syncSetRoamDelta(int roamDelta) {
        return this.mWifiNative.setRoamDelta(this.mInterfaceName, roamDelta);
    }

    public int syncGetRoamDelta() {
        return this.mWifiNative.getRoamDelta(this.mInterfaceName);
    }

    public boolean syncSetRoamScanPeriod(int roamScanPeriod) {
        return this.mWifiNative.setRoamScanPeriod(this.mInterfaceName, roamScanPeriod);
    }

    public int syncGetRoamScanPeriod() {
        return this.mWifiNative.getRoamScanPeriod(this.mInterfaceName);
    }

    public boolean syncSetRoamBand(int band) {
        return this.mWifiNative.setRoamBand(this.mInterfaceName, band);
    }

    public int syncGetRoamBand() {
        return this.mWifiNative.getRoamBand(this.mInterfaceName);
    }

    public boolean syncSetCountryRev(String countryRev) {
        return this.mWifiNative.setCountryRev(this.mInterfaceName, countryRev);
    }

    public String syncGetCountryRev() {
        return this.mWifiNative.getCountryRev(this.mInterfaceName);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean processMessageForCallSECApiAsync(Message msg) {
        Message innerMsg = (Message) msg.obj;
        if (innerMsg == null) {
            Log.e(TAG, "CMD_SEC_API_ASYNC, invalid innerMsg");
            return true;
        }
        if (this.mVerboseLoggingEnabled) {
            Log.d(TAG, "CMD_SEC_API_ASYNC, inner msg.what:" + innerMsg.what);
        }
        Bundle args = (Bundle) innerMsg.obj;
        int i = innerMsg.what;
        if (i != 1) {
            if (i != 18) {
                if (i != 26) {
                    if (i != 71) {
                        if (i != 74) {
                            if (i != 201) {
                                if (i == 242) {
                                    return false;
                                }
                                if (i == 282 && args != null) {
                                    this.mWifiNative.setAffinityBooster(this.mInterfaceName, args.getInt("enable"));
                                }
                            } else if (args != null) {
                                boolean keepConnection = args.getBoolean("keep_connection", false);
                                WifiConfiguration config = getCurrentWifiConfiguration();
                                if (config != null && keepConnection) {
                                    Log.d(TAG, "SEC_COMMAND_ID_ANS_EXCEPTION_ANSWER, networkId : " + config.networkId + ", keep connection : " + keepConnection);
                                    this.mWifiConfigManager.updateNetworkSelectionStatus(config.networkId, 0);
                                }
                            }
                        }
                    } else if (args != null) {
                        this.mWifiNative.setAffinityBooster(this.mInterfaceName, args.getInt("enable"));
                        Integer netId = Integer.valueOf(args.getInt("netId"));
                        Integer autoReconnect = Integer.valueOf(args.getInt("autoReconnect"));
                        Log.d(TAG, "SEC_COMMAND_ID_SET_AUTO_RECONNECT  autoReconnect: " + autoReconnect);
                        if (ENBLE_WLAN_CONFIG_ANALYTICS && autoReconnect.intValue() == 0 && (netId.intValue() == this.mTargetNetworkId || netId.intValue() == this.mLastNetworkId)) {
                            setAnalyticsUserDisconnectReason(WifiNative.f19x2e98dbf);
                        }
                        this.mWifiConfigManager.setAutoReconnect(netId.intValue(), autoReconnect.intValue());
                    }
                }
                if (args != null) {
                    boolean enable = args.getBoolean("enable");
                    boolean lock = args.getBoolean("lock");
                    setConcurrentEnabled(enable);
                    Log.d(TAG, "SEC_COMMAND_ID_SET_WIFI_XXX_WITH_P2P mConcurrentEnabled " + this.mConcurrentEnabled);
                    if (!enable && lock && this.mP2pSupported && this.mWifiP2pChannel != null) {
                        Message message = new Message();
                        message.what = 139268;
                        this.mWifiP2pChannel.sendMessage(message);
                    }
                }
            } else if (args != null) {
                this.mScanRequestProxy.setScanningEnabled(!args.getBoolean("stop", false), "SEC_COMMAND_ID_STOP_PERIODIC_SCAN");
            }
        } else if (args != null) {
            boolean enable2 = args.getBoolean("enable", false);
            Log.d(TAG, "SEC_COMMAND_ID_AUTO_CONNECT, enable: " + enable2);
            this.mWifiConfigManager.setNetworkAutoConnect(enable2);
        }
        return true;
    }

    /* JADX WARN: Type inference failed for: r8v4, types: [int, boolean] */
    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private int processMessageForCallSECApi(Message message) {
        ConnectivityManager connectivityManager;
        Message innerMsg = (Message) message.obj;
        if (innerMsg == null) {
            Log.e(TAG, "CMD_SEC_API, invalid innerMsg");
            return -1;
        }
        if (this.mVerboseLoggingEnabled) {
            Log.d(TAG, "CMD_SEC_API, inner msg.what:" + innerMsg.what);
        }
        int i = innerMsg.what;
        if (i == 17) {
            Bundle args = (Bundle) innerMsg.obj;
            if (args != null) {
                return this.mWifiNative.setAmpdu(this.mInterfaceName, args.getInt("ampdu"));
            }
            return -1;
        } else if (i == 107) {
            Bundle args2 = (Bundle) innerMsg.obj;
            if (args2 != null) {
                return this.mWifiNative.setRoamScanControl(this.mInterfaceName, args2.getInt("mode"));
            }
            return -1;
        } else if (i == 301) {
            Integer netId = Integer.valueOf(((Bundle) innerMsg.obj).getInt("excluded_networkId"));
            if (DBG) {
                Log.d(TAG, "SEC_COMMAND_ID_SNS_DELETE_EXCLUDED : netId(" + netId + "), delete excluded network");
            }
            if (this.mWifiInfo.getNetworkId() == netId.intValue() && (connectivityManager = this.mCm) != null) {
                connectivityManager.setAcceptUnvalidated(getCurrentNetwork(), false, true);
            }
            this.mWifiConfigManager.setNetworkNoInternetAccessExpected(netId.intValue(), false);
            sendWcmConfigurationChanged();
            return 1;
        } else if (i == 150) {
            Bundle args3 = (Bundle) innerMsg.obj;
            if (args3 != null) {
                return this.mWifiNative.sendActionFrame(this.mInterfaceName, args3.getString("param"));
            }
            return -1;
        } else if (i == 151) {
            Bundle args4 = (Bundle) innerMsg.obj;
            if (args4 == null) {
                return -1;
            }
            if (getNCHOVersion() == 2 && getNCHO20State() == 0 && "eng".equals(Build.TYPE)) {
                return this.mWifiNative.reAssocLegacy(this.mInterfaceName, args4.getString("param"));
            }
            return this.mWifiNative.reAssoc(this.mInterfaceName, args4.getString("param"));
        } else if (i == 170) {
            return this.mWifiNative.getWesMode(this.mInterfaceName);
        } else {
            if (i != 171) {
                switch (i) {
                    case 100:
                        if (getNCHOVersion() == 2 && getNCHO20State() == 0 && "eng".equals(Build.TYPE)) {
                            return this.mWifiNative.getRoamTriggerLegacy(this.mInterfaceName);
                        }
                        return this.mWifiNative.getRoamTrigger(this.mInterfaceName);
                    case 101:
                        Bundle args5 = (Bundle) innerMsg.obj;
                        if (args5 == null) {
                            return -1;
                        }
                        this.mIsNchoParamSet = true;
                        return (getNCHOVersion() == 2 && getNCHO20State() == 0 && "eng".equals(Build.TYPE)) ? this.mWifiNative.setRoamTriggerLegacy(this.mInterfaceName, args5.getInt("level")) ? 1 : 0 : this.mWifiNative.setRoamTrigger(this.mInterfaceName, args5.getInt("level")) ? 1 : 0;
                    case 102:
                        return this.mWifiNative.getRoamDelta(this.mInterfaceName);
                    case 103:
                        Bundle args6 = (Bundle) innerMsg.obj;
                        if (args6 == null) {
                            return -1;
                        }
                        this.mIsNchoParamSet = true;
                        return this.mWifiNative.setRoamDelta(this.mInterfaceName, args6.getInt("level")) ? 1 : 0;
                    case 104:
                        return this.mWifiNative.getRoamScanPeriod(this.mInterfaceName);
                    case 105:
                        Bundle args7 = (Bundle) innerMsg.obj;
                        if (args7 == null) {
                            return -1;
                        }
                        this.mIsNchoParamSet = true;
                        return this.mWifiNative.setRoamScanPeriod(this.mInterfaceName, args7.getInt("time")) ? 1 : 0;
                    default:
                        switch (i) {
                            case 109:
                                Bundle args8 = (Bundle) innerMsg.obj;
                                if (args8 != null) {
                                    return this.mWifiNative.setRoamScanChannels(this.mInterfaceName, args8.getString("chinfo"));
                                }
                                return -1;
                            case SoapEnvelope.VER11 /*{ENCODED_INT: 110}*/:
                                if (getNCHOVersion() != 2) {
                                    return -1;
                                }
                                int intResult = this.mWifiNative.getNCHOMode(this.mInterfaceName);
                                if (this.mVerboseLoggingEnabled) {
                                    Log.d(TAG, "Get ncho mode: " + intResult);
                                }
                                if (intResult == 0 || intResult == 1) {
                                    setNCHO20State(intResult, false);
                                    return intResult;
                                }
                                Log.e(TAG, "Get ncho mode - Something Wrong: " + intResult);
                                return intResult;
                            case MobileWipsScanResult.InformationElement.EID_ROAMING_CONSORTIUM /*{ENCODED_INT: 111}*/:
                                Bundle args9 = (Bundle) innerMsg.obj;
                                if (args9 == null) {
                                    return -1;
                                }
                                int setVal = args9.getInt("mode");
                                if (setVal == 0 || setVal == 1) {
                                    int nchoversion = getNCHOVersion();
                                    if (nchoversion == 2) {
                                        ?? nCHO20State = setNCHO20State(setVal, true);
                                        if (nCHO20State == 1) {
                                            this.mIsNchoParamSet = false;
                                            return nCHO20State;
                                        }
                                        Log.e(TAG, "Fail to set NCHO to Firmware:" + (nCHO20State == true ? 1 : 0));
                                        return nCHO20State;
                                    } else if (nchoversion != 1 || setVal != 0 || getNCHO10State() != 1) {
                                        return -1;
                                    } else {
                                        restoreNcho10Param();
                                        this.mIsNchoParamSet = false;
                                        return -1;
                                    }
                                } else {
                                    Log.e(TAG, "Set ncho mode - invalid set value: " + setVal);
                                    return -1;
                                }
                            default:
                                switch (i) {
                                    case 130:
                                        return this.mWifiNative.getScanChannelTime(this.mInterfaceName);
                                    case 131:
                                        Bundle args10 = (Bundle) innerMsg.obj;
                                        if (args10 != null) {
                                            return this.mWifiNative.setScanChannelTime(this.mInterfaceName, args10.getString("time"));
                                        }
                                        return -1;
                                    case 132:
                                        return this.mWifiNative.getScanHomeTime(this.mInterfaceName);
                                    case 133:
                                        Bundle args11 = (Bundle) innerMsg.obj;
                                        if (args11 != null) {
                                            return this.mWifiNative.setScanHomeTime(this.mInterfaceName, args11.getString("time"));
                                        }
                                        return -1;
                                    case 134:
                                        return this.mWifiNative.getScanHomeAwayTime(this.mInterfaceName);
                                    case 135:
                                        Bundle args12 = (Bundle) innerMsg.obj;
                                        if (args12 != null) {
                                            return this.mWifiNative.setScanHomeAwayTime(this.mInterfaceName, args12.getString("time"));
                                        }
                                        return -1;
                                    case 136:
                                        return this.mWifiNative.getScanNProbes(this.mInterfaceName);
                                    case 137:
                                        Bundle args13 = (Bundle) innerMsg.obj;
                                        if (args13 != null) {
                                            return this.mWifiNative.setScanNProbes(this.mInterfaceName, args13.getString("num"));
                                        }
                                        return -1;
                                    default:
                                        switch (i) {
                                            case 161:
                                                Bundle args14 = (Bundle) innerMsg.obj;
                                                if (args14 != null) {
                                                    return this.mWifiNative.setCountryRev(this.mInterfaceName, args14.getString("country")) ? 1 : 0;
                                                }
                                                return -1;
                                            case 162:
                                                return this.mWifiNative.getBand(this.mInterfaceName);
                                            case 163:
                                                Bundle args15 = (Bundle) innerMsg.obj;
                                                if (args15 != null) {
                                                    return this.mWifiNative.setBand(this.mInterfaceName, args15.getInt("band")) ? 1 : 0;
                                                }
                                                return -1;
                                            case 164:
                                                return this.mWifiNative.getDfsScanMode(this.mInterfaceName);
                                            case 165:
                                                Bundle args16 = (Bundle) innerMsg.obj;
                                                if (args16 != null) {
                                                    return this.mWifiNative.setDfsScanMode(this.mInterfaceName, args16.getInt("mode")) ? 1 : 0;
                                                }
                                                return -1;
                                            default:
                                                if (!this.mVerboseLoggingEnabled) {
                                                    return -1;
                                                }
                                                Log.e(TAG, "ignore message : not implementation yet");
                                                return -1;
                                        }
                                }
                        }
                }
            } else {
                Bundle args17 = (Bundle) innerMsg.obj;
                if (args17 != null) {
                    return this.mWifiNative.setWesMode(this.mInterfaceName, args17.getInt("mode")) ? 1 : 0;
                }
                return -1;
            }
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private String processMessageOnDefaultStateForCallSECStringApi(Message message) {
        String data;
        String prop_name;
        int propType;
        int propType2;
        Message innerMsg = (Message) message.obj;
        if (innerMsg == null) {
            Log.e(TAG, "CMD_SEC_STRING_API, invalid innerMsg");
            return null;
        }
        if (this.mVerboseLoggingEnabled) {
            Log.d(TAG, "CMD_SEC_STRING_API, inner msg.what:" + innerMsg.what);
        }
        int i = innerMsg.what;
        if (i != 85) {
            if (i == 223) {
                return this.mScanRequestProxy.semDumpCachedScanController();
            }
            if (i == 300) {
                return this.mWifiGeofenceManager.getGeofenceInformation();
            }
            switch (i) {
                case 274:
                    if (innerMsg.arg1 < 10) {
                        return this.mWifiNative.getVendorConnFileInfo(innerMsg.arg1);
                    }
                    return null;
                case 275:
                    if (innerMsg.obj == null || (data = ((Bundle) innerMsg.obj).getString("data")) == null || innerMsg.arg1 >= 10) {
                        return null;
                    }
                    if ("!remove".equals(data)) {
                        if (this.mWifiNative.removeVendorConnFile(innerMsg.arg1)) {
                            return "OK";
                        }
                        return null;
                    } else if (this.mWifiNative.putVendorConnFile(innerMsg.arg1, data)) {
                        return "OK";
                    } else {
                        return null;
                    }
                case 276:
                    if (innerMsg.obj == null || (prop_name = ((Bundle) innerMsg.obj).getString("prop_name")) == null) {
                        return null;
                    }
                    if ("vendor.wlandriver.mode".equals(prop_name)) {
                        propType = 0;
                    } else if (!"vendor.wlandriver.status".equals(prop_name)) {
                        return null;
                    } else {
                        propType = 1;
                    }
                    return this.mWifiNative.getVendorProperty(propType);
                case 277:
                    if (innerMsg.obj == null) {
                        return null;
                    }
                    Bundle prop_info = (Bundle) innerMsg.obj;
                    String prop_name2 = prop_info.getString("prop_name");
                    String data2 = prop_info.getString("data");
                    if (prop_name2 == null) {
                        return null;
                    }
                    if ("vendor.wlandriver.mode".equals(prop_name2)) {
                        propType2 = 0;
                    } else if (!"vendor.wlandriver.status".equals(prop_name2)) {
                        return null;
                    } else {
                        propType2 = 1;
                    }
                    if (this.mWifiNative.setVendorProperty(propType2, data2)) {
                        return "OK";
                    }
                    return null;
                case DhcpPacket.MIN_PACKET_LENGTH_L2 /*{ENCODED_INT: 278}*/:
                    return WlanTestHelper.getConfigFileString();
                default:
                    return null;
            }
        } else if (this.mIssueDetector == null) {
            return null;
        } else {
            int size = innerMsg.arg1;
            return this.mIssueDetector.getRawData(size == 0 ? 5 : size);
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private String processMessageForCallSECStringApi(Message message) {
        Message innerMsg = (Message) message.obj;
        if (innerMsg == null) {
            Log.e(TAG, "CMD_SEC_STRING_API, invalid innerMsg");
            return null;
        }
        if (this.mVerboseLoggingEnabled) {
            Log.d(TAG, "CMD_SEC_STRING_API, inner msg.what:" + innerMsg.what);
        }
        int i = innerMsg.what;
        if (i == 108) {
            return this.mWifiNative.getRoamScanChannels(this.mInterfaceName);
        }
        if (i == 160) {
            return this.mWifiNative.getCountryRev(this.mInterfaceName);
        }
        if (!this.mVerboseLoggingEnabled) {
            return null;
        }
        Log.e(TAG, "ignore message : not implementation yet");
        return null;
    }

    /* access modifiers changed from: package-private */
    public void sendCallSECApiAsync(Message msg, int callingPid) {
        if (this.mVerboseLoggingEnabled) {
            Log.i(TAG, "sendCallSECApiAsync what=" + msg.what);
        }
        sendMessage(obtainMessage(CMD_SEC_API_ASYNC, msg.what, callingPid, Message.obtain(msg)));
    }

    /* access modifiers changed from: package-private */
    public int syncCallSECApi(AsyncChannel channel, Message msg) {
        if (this.mVerboseLoggingEnabled) {
            Log.i(TAG, "syncCallSECApi what=" + msg.what);
        }
        if (this.mIsShutdown) {
            return -1;
        }
        if (channel == null) {
            Log.e(TAG, "Channel is not initialized");
            return -1;
        }
        Message resultMsg = channel.sendMessageSynchronously((int) CMD_SEC_API, msg.what, 0, msg);
        int result = resultMsg.arg1;
        resultMsg.recycle();
        return result;
    }

    /* access modifiers changed from: package-private */
    public String syncCallSECStringApi(AsyncChannel channel, Message msg) {
        if (this.mVerboseLoggingEnabled) {
            Log.i(TAG, "syncCallSECStringApi what=" + msg.what);
        }
        if (this.mIsShutdown) {
            return null;
        }
        Message resultMsg = channel.sendMessageSynchronously((int) CMD_SEC_STRING_API, msg.what, 0, msg);
        String result = (String) resultMsg.obj;
        resultMsg.recycle();
        return result;
    }

    public void showToastMsg(int type, String extraString) {
        sendMessage(CMD_SHOW_TOAST_MSG, type, 0, extraString);
    }

    /* access modifiers changed from: package-private */
    public void setImsCallEstablished(boolean isEstablished) {
        sendMessage(CMD_IMS_CALL_ESTABLISHED, isEstablished ? 1 : 0, 0);
    }

    /* access modifiers changed from: package-private */
    public boolean isImsCallEstablished() {
        return this.mIsImsCallEstablished;
    }

    public void resetPeriodicScanTimer() {
        WifiConnectivityManager wifiConnectivityManager = this.mWifiConnectivityManager;
        if (wifiConnectivityManager != null) {
            wifiConnectivityManager.resetPeriodicScanTime();
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void setTcpBufferAndProxySettingsForIpManager() {
        WifiConfiguration currentConfig = getCurrentWifiConfiguration();
        if (!(currentConfig == null || this.mIpClient == null)) {
            this.mIpClient.setHttpProxy(currentConfig.getHttpProxy());
        }
        if (!TextUtils.isEmpty(this.mTcpBufferSizes) && this.mIpClient != null) {
            this.mIpClient.setTcpBufferSizes(this.mTcpBufferSizes);
        }
    }

    public void initializeWifiChipInfo() {
        if (!WifiChipInfo.getInstance().isReady()) {
            Log.d(TAG, "chipset information is not ready, try to get the information");
            String cidInfo = this.mWifiNative.getVendorConnFileInfo(1);
            if (DBG_PRODUCT_DEV) {
                Log.d(TAG, ".cid.info: " + cidInfo);
            }
            String wifiVerInfo = this.mWifiNative.getVendorConnFileInfo(5);
            if (DBG_PRODUCT_DEV) {
                Log.d(TAG, ".wifiver.info: " + wifiVerInfo);
            }
            WifiChipInfo.getInstance().updateChipInfos(cidInfo, wifiVerInfo);
            String macAddress = this.mWifiNative.getVendorConnFileInfo(0);
            Log.d(TAG, "chipset information is macAddress" + macAddress);
            if (macAddress != null && macAddress.length() >= 17) {
                WifiChipInfo.getInstance().setMacAddress(macAddress.substring(0, 17));
                if (!this.mWifiInfo.hasRealMacAddress()) {
                    this.mWifiInfo.setMacAddress(macAddress.substring(0, 17));
                }
            }
            String softapInfo = this.mWifiNative.getVendorConnFileInfo(6);
            Log.d(TAG, "chipset information is softapInfo" + softapInfo);
            this.mWifiInjector.getSemWifiApChipInfo().readSoftApInfo(softapInfo);
            if (WifiChipInfo.getInstance().isReady()) {
                Log.d(TAG, "chipset information is ready");
            }
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void increaseCounter(int what) {
        long value = 1;
        if (this.mEventCounter.containsKey(Integer.valueOf(what))) {
            value = this.mEventCounter.get(Integer.valueOf(what)).longValue() + 1;
        }
        this.mEventCounter.put(Integer.valueOf(what), Long.valueOf(value));
    }

    private long getCounter(int what, long defaultValue) {
        if (this.mEventCounter.containsKey(Integer.valueOf(what))) {
            return this.mEventCounter.get(Integer.valueOf(what)).longValue();
        }
        return defaultValue;
    }

    private void resetCounter() {
        this.mEventCounter.clear();
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private String getWifiParameters(boolean reset) {
        int wifiState = this.mWifiState.get() == 3 ? 1 : 0;
        int alwaysAllowScanningMode = Settings.Global.getInt(this.mContext.getContentResolver(), "wifi_scan_always_enabled", 0);
        int smartNetworkSwitch = Settings.Global.getInt(this.mContext.getContentResolver(), "wifi_watchdog_poor_network_test_enabled", 0);
        int agressiveSmartNetworkSwitch = Settings.Global.getInt(this.mContext.getContentResolver(), "wifi_watchdog_poor_network_aggressive_mode_on", 0);
        int favoriteApCount = Settings.Global.getInt(this.mContext.getContentResolver(), "sem_auto_wifi_favorite_ap_count", 0);
        int isAutoWifiEnabled = Settings.Global.getInt(this.mContext.getContentResolver(), "sem_auto_wifi_control_enabled", 0);
        int safeModeEnabled = Settings.Global.getInt(this.mContext.getContentResolver(), "safe_wifi", 0);
        StringBuffer sb = new StringBuffer();
        sb.append(wifiState);
        sb.append(" ");
        sb.append(alwaysAllowScanningMode);
        sb.append(" ");
        sb.append(isAutoWifiEnabled);
        sb.append(" ");
        sb.append(favoriteApCount);
        sb.append(" ");
        sb.append(smartNetworkSwitch);
        sb.append(" ");
        sb.append(agressiveSmartNetworkSwitch);
        sb.append(" ");
        sb.append(safeModeEnabled);
        sb.append(" ");
        String scanValues = null;
        if (reset) {
            scanValues = this.mScanRequestProxy.semGetScanCounterForBigData(reset);
        }
        if (scanValues != null) {
            sb.append(scanValues);
            sb.append(" ");
        } else {
            sb.append("-1 -1 -1 -1 ");
        }
        sb.append(getCounter(151562, 0));
        sb.append(" ");
        sb.append(getCounter(151564, 0));
        sb.append(" ");
        sb.append(getCounter(151565, 0));
        sb.append(" ");
        sb.append(getCounter(WifiMonitor.DRIVER_HUNG_EVENT, 0));
        sb.append(" ");
        sb.append(this.mWifiAdpsEnabled.get() ? 1 : 0);
        sb.append(" ");
        sb.append(this.mWifiConfigManager.getSavedNetworks(1010).size());
        sb.append(" ");
        sb.append(this.laaEnterState);
        sb.append(" ");
        sb.append(this.laaActiveState);
        if (reset) {
            resetCounter();
            if (CSC_SUPPORT_5G_ANT_SHARE) {
                this.laaEnterState = 0;
                this.laaActiveState = 0;
            }
        }
        return sb.toString();
    }

    public boolean isUnstableAp(String bssid) {
        UnstableApController unstableApController = this.mUnstableApController;
        if (unstableApController != null) {
            return unstableApController.isUnstableAp(bssid);
        }
        return false;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean isWifiSharingProvisioning() {
        WifiManager mManager = (WifiManager) this.mContext.getSystemService("wifi");
        Log.i(TAG, "getProvisionSuccess : " + mManager.getProvisionSuccess() + " isWifiSharingEnabled " + mManager.isWifiSharingEnabled());
        return mManager.isWifiSharingEnabled() && mManager.getProvisionSuccess() == 2;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void updatePasspointNetworkSelectionStatus(boolean enabled) {
        if (!enabled) {
            for (WifiConfiguration network : this.mWifiConfigManager.getConfiguredNetworks()) {
                if (network.isPasspoint() && network.networkId != -1) {
                    this.mWifiConfigManager.disableNetwork(network.networkId, network.creatorUid);
                    this.mWifiConfigManager.removeNetwork(network.networkId, network.creatorUid);
                }
            }
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void removePasspointNetworkIfSimAbsent() {
        for (WifiConfiguration network : this.mWifiConfigManager.getConfiguredNetworks()) {
            if (network.isPasspoint() && network.networkId != -1 && TelephonyUtil.isSimEapMethod(network.enterpriseConfig.getEapMethod())) {
                Log.w(TAG, "removePasspointNetworkIfSimAbsent : network " + network.configKey() + " try to remove");
                this.mWifiConfigManager.disableNetwork(network.networkId, network.creatorUid);
                this.mWifiConfigManager.removeNetwork(network.networkId, network.creatorUid);
            }
        }
    }

    public boolean isPasspointEnabled() {
        return this.mIsPasspointEnabled;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void updateVendorApSimState() {
        boolean isUseableVendorUsim = TelephonyUtil.isVendorApUsimUseable(getTelephonyManager());
        Log.i(TAG, "updateVendorApSimState : " + isUseableVendorUsim);
        this.mPasspointManager.setVendorSimUseable(isUseableVendorUsim);
        Settings.Secure.putInt(this.mContext.getContentResolver(), "wifi_hotspot20_useable_vendor_usim", isUseableVendorUsim ? 1 : 0);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void updateWlanDebugLevel() {
        String memdumpInfo = this.mWifiNative.getVendorConnFileInfo(7);
        Log.i(TAG, "updateWlanDebugLevel : current level is " + memdumpInfo);
        if (memdumpInfo != null && !memdumpInfo.equals("2")) {
            if (this.mWifiNative.putVendorConnFile(7, "2")) {
                Log.i(TAG, "updateWlanDebugLevel : update to 2 succeed");
            } else {
                Log.i(TAG, "updateWlanDebugLevel : update to 2 failed");
            }
        }
    }

    /* access modifiers changed from: package-private */
    public boolean checkAndShowSimRemovedDialog(WifiConfiguration config) {
        WifiEnterpriseConfig enterpriseConfig = config.enterpriseConfig;
        if (config.semIsVendorSpecificSsid && enterpriseConfig != null && (enterpriseConfig.getEapMethod() == 5 || enterpriseConfig.getEapMethod() == 6)) {
            int simState = getTelephonyManager().getSimState();
            Log.i(TAG, "simState is " + simState + " for " + config.SSID);
            if (simState == 1 || simState == 0) {
                Log.d(TAG, "trying to connect without SIM, show alert dialog");
                SemWifiFrameworkUxUtils.showWarningDialog(this.mContext, 2, new String[]{StringUtil.removeDoubleQuotes(config.SSID)});
                return true;
            }
        }
        return false;
    }

    /* access modifiers changed from: package-private */
    public void showEapNotificationToast(int code) {
        Log.i(TAG, "eap code : " + code + ", targetId: " + this.mTargetNetworkId);
        if (code != 987654321) {
            if (CSC_WIFI_SUPPORT_VZW_EAP_AKA) {
                SemWifiFrameworkUxUtils.showEapToastVzw(this.mContext, code);
            } else if (CSC_WIFI_ERRORCODE) {
                SemWifiFrameworkUxUtils.showEapToast(this.mContext, code);
            }
        }
    }

    private boolean checkAndRetryConnect(int targetNetworkId) {
        if (this.mLastEAPFailureNetworkId != targetNetworkId) {
            this.mLastEAPFailureNetworkId = targetNetworkId;
            this.mLastEAPFailureCount = 0;
        }
        int i = this.mLastEAPFailureCount + 1;
        this.mLastEAPFailureCount = i;
        if (i > 3) {
            return false;
        }
        return true;
    }

    /* access modifiers changed from: package-private */
    public void processMessageForEap(int event, int status, String message) {
        String eapEventMsg;
        Log.i(TAG, "eap message : event [" + event + "] , status [" + (status == 987654321 ? "none" : Integer.toString(status)) + "] , message '" + message + "', targetId: " + this.mTargetNetworkId);
        WifiConfiguration currentConfig = null;
        int i = this.mTargetNetworkId;
        if (i != -1) {
            currentConfig = this.mWifiConfigManager.getConfiguredNetwork(i);
        }
        if (currentConfig == null) {
            Log.e(TAG, "ignore eap message : currentConfig is null");
            StringBuilder eapLogTemp = new StringBuilder();
            eapLogTemp.append("events: { EAP_EVENT_" + event + "},");
            eapLogTemp.append(" extra_info: { " + message + " }");
            this.mWifiMetrics.logStaEvent(22, eapLogTemp.toString());
        } else if (event < 1) {
            Log.e(TAG, "ignore eap message : event is not defined");
        } else {
            boolean hasEverConnected = currentConfig.getNetworkSelectionStatus().getHasEverConnected();
            boolean isNetworkPermanentlyDisabled = currentConfig.getNetworkSelectionStatus().isNetworkPermanentlyDisabled();
            switch (event) {
                case 1:
                    updateAnonymousIdentity(this.mTargetNetworkId);
                    eapEventMsg = "ANONYMOUS_IDENTITY_UPDATED ";
                    break;
                case 2:
                    if (TelephonyUtil.isSimEapMethod(currentConfig.enterpriseConfig.getEapMethod())) {
                        updateSimNumber(this.mTargetNetworkId);
                    }
                    Log.i(TAG, "network " + currentConfig.configKey() + " has ever connected " + hasEverConnected + ", isNetworkPermanentlyDisabled, " + isNetworkPermanentlyDisabled);
                    if (!isNetworkPermanentlyDisabled) {
                        this.mWifiConfigManager.updateNetworkSelectionStatus(this.mTargetNetworkId, 3);
                        if (checkAndRetryConnect(this.mTargetNetworkId)) {
                            Log.w(TAG, "update network status to auth failure , retry to conect ");
                            startConnectToNetwork(this.mTargetNetworkId, 1010, "any");
                        }
                        Log.w(TAG, "trackBssid for 802.1x auth failure : " + message);
                        if (!TextUtils.isEmpty(message) && !"00:00:00:00:00:00".equals(message)) {
                            this.mWifiConnectivityManager.trackBssid(message, false, 3);
                        }
                    }
                    eapEventMsg = "DEAUTH_8021X_AUTH_FAILED ";
                    break;
                case 3:
                    if (TelephonyUtil.isSimEapMethod(currentConfig.enterpriseConfig.getEapMethod())) {
                        updateSimNumber(this.mTargetNetworkId);
                    }
                    Log.i(TAG, "network " + currentConfig.configKey() + " has ever connected " + hasEverConnected + ", isNetworkPermanentlyDisabled, " + isNetworkPermanentlyDisabled);
                    int currentEapMethod = currentConfig.enterpriseConfig.getEapMethod();
                    if (!hasEverConnected) {
                        if (currentEapMethod == 0 || currentEapMethod == 2 || currentEapMethod == 3) {
                            Log.i(TAG, "update network status to wrong password ");
                            this.mWifiConfigManager.updateNetworkSelectionStatus(this.mTargetNetworkId, 13);
                        } else if (!isNetworkPermanentlyDisabled) {
                            this.mWifiConfigManager.updateNetworkSelectionStatus(this.mTargetNetworkId, 3);
                            if (checkAndRetryConnect(this.mTargetNetworkId)) {
                                Log.w(TAG, "update network status to eap failure , retry to conect , " + this.mLastEAPFailureCount);
                                startConnectToNetwork(this.mTargetNetworkId, 1010, "any");
                            }
                        }
                    }
                    eapEventMsg = "FAIL ";
                    break;
                case 4:
                    eapEventMsg = "ERROR ";
                    break;
                case 5:
                    eapEventMsg = "LOG ";
                    break;
                case 6:
                    eapEventMsg = "NO_CREDENTIALS ";
                    break;
                case 7:
                    showEapNotificationToast(status);
                    eapEventMsg = "NOTIFICATION ";
                    break;
                case 8:
                    eapEventMsg = "SUCCESS ";
                    break;
                case 9:
                case 11:
                    WifiMobileDeviceManager.auditLog(this.mContext, 1, false, TAG, "EAP-TLS handshake failed: " + message);
                    eapEventMsg = "TLS_HANDSHAKE_FAIL ";
                    break;
                case 10:
                    new CertificatePolicy().notifyCertificateFailureAsUser("wifi_module", message, true, 0);
                    WifiMobileDeviceManager.auditLog(this.mContext, 1, false, TAG, "Certificate verification failed: " + message);
                    eapEventMsg = "TLS_CERT_ERROR ";
                    break;
                default:
                    if (this.mVerboseLoggingEnabled) {
                        Log.e(TAG, "ignore eap message : not implementation yet");
                    }
                    eapEventMsg = null;
                    break;
            }
            StringBuilder eapLog = new StringBuilder();
            eapLog.append("events: { EAP_EVENT_" + eapEventMsg + "},");
            if (status != 987654321) {
                eapLog.append(" notification_status=" + status);
            }
            eapLog.append(" extra_info: { " + message + " }");
            this.mWifiMetrics.logStaEvent(22, eapLog.toString());
        }
    }

    private int getConfiguredSimNum(WifiConfiguration config) {
        int simNum = 1;
        String simNumStr = config.enterpriseConfig.getSimNumber();
        if (simNumStr != null && !simNumStr.isEmpty()) {
            try {
                simNum = Integer.parseInt(simNumStr);
            } catch (NumberFormatException e) {
                Log.e(TAG, "getConfiguredSimNum - failed to getSimNumber ");
            }
        }
        Log.i(TAG, "getConfiguredSimNum - previous saved simNum:" + simNum);
        return simNum;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean setPermanentIdentity(WifiConfiguration config) {
        if (this.mTargetNetworkId == -1) {
            Log.e(TAG, "PermanentIdentity : NetworkId is INVALID_NETWORK_ID");
            return false;
        }
        int simNum = getConfiguredSimNum(config);
        if (getTelephonyManager().getPhoneCount() > 1) {
            int multiSimState = TelephonyUtil.semGetMultiSimState(getTelephonyManager());
            if (multiSimState == 1) {
                simNum = 1;
            } else if (multiSimState == 2) {
                simNum = 2;
            }
        } else {
            simNum = 1;
        }
        Log.i(TAG, "PermanentIdentity set simNum:" + simNum);
        config.enterpriseConfig.setSimNumber(simNum);
        TelephonyUtil.setSimIndex(simNum);
        Pair<String, String> identityPair = TelephonyUtil.getSimIdentity(getTelephonyManager(), new TelephonyUtil(), config, this.mWifiInjector.getCarrierNetworkConfig());
        if (identityPair == null || identityPair.first == null) {
            Log.i(TAG, "PermanentIdentity identityPair is invalid ");
            return false;
        }
        if (!config.semIsVendorSpecificSsid || TextUtils.isEmpty((CharSequence) identityPair.second)) {
            String oldIdentity = config.enterpriseConfig.getIdentity();
            if (oldIdentity != null && !oldIdentity.equals(identityPair.first) && TelephonyUtil.isSimEapMethod(config.enterpriseConfig.getEapMethod())) {
                Log.d(TAG, "PermanentIdentity has been changed. setAnonymousIdentity to null for EAP method SIM/AKA/AKA'");
                config.enterpriseConfig.setAnonymousIdentity(null);
            }
            Log.d(TAG, "PermanentIdentity is set to : " + ((String) identityPair.first).substring(0, 7));
            config.enterpriseConfig.setIdentity((String) identityPair.first);
        } else {
            Log.i(TAG, "PermanentIdentity , identity is encrypted , need SUP_REQUEST_IDENTITY ");
            config.enterpriseConfig.setIdentity(null);
        }
        this.mWifiConfigManager.addOrUpdateNetwork(config, 1010);
        return true;
    }

    private void updateSimNumber(int netId) {
        Log.i(TAG, "updateSimNumber() netId : " + netId);
        WifiConfiguration config = this.mWifiConfigManager.getConfiguredNetwork(netId);
        if (config != null && config.enterpriseConfig != null && TelephonyUtil.isSimConfig(config)) {
            int simNum = getConfiguredSimNum(config);
            if (getTelephonyManager().getPhoneCount() > 1) {
                int multiSimState = TelephonyUtil.semGetMultiSimState(getTelephonyManager());
                if (multiSimState == 1) {
                    simNum = 1;
                } else if (multiSimState == 2) {
                    simNum = 2;
                } else if (multiSimState == 3) {
                    if (simNum == 2) {
                        simNum = 1;
                    } else {
                        simNum = 2;
                    }
                }
            } else {
                simNum = 1;
            }
            Log.i(TAG, "updateSimNumber() set simNum:" + simNum);
            config.enterpriseConfig.setSimNumber(simNum);
            this.mWifiConfigManager.addOrUpdateNetwork(config, 1010);
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void updateIdentityOnWifiConfiguration(WifiConfiguration config, String identity) {
        Log.i(TAG, "updateIdentityOnWifiConfiguration -  network :" + config.configKey());
        if (config.enterpriseConfig != null && !identity.equals(config.enterpriseConfig.getIdentity())) {
            Log.d(TAG, "updateIdentityOnWifiConfiguration -  Identity has been changed. setAnonymousIdentity to null for EAP method SIM/AKA/AKA'");
            config.enterpriseConfig.setIdentity(identity);
            config.enterpriseConfig.setAnonymousIdentity(null);
            this.mWifiConfigManager.addOrUpdateNetwork(config, 1010);
        }
    }

    private void updateAnonymousIdentity(int netId) {
        Log.i(TAG, "updateAnonymousIdentity(" + netId + ")");
        WifiConfiguration config = this.mWifiConfigManager.getConfiguredNetwork(netId);
        if (config != null && config.enterpriseConfig != null && TelephonyUtil.isSimEapMethod(config.enterpriseConfig.getEapMethod()) && !TextUtils.isEmpty(config.enterpriseConfig.getAnonymousIdentity())) {
            Log.i(TAG, "reset Anonymousidentity from supplicant, so reset it in WifiConfiguration.");
            config.enterpriseConfig.setAnonymousIdentity(null);
            this.mWifiConfigManager.addOrUpdateNetwork(config, 1010);
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void sendIpcMessageToRilForLteu(int LTEU_WIFI_STATE, boolean isEnabled, boolean is5GHz, boolean forceSend) {
        int lteuState;
        int lteuEnable;
        int lteuState2 = mLteuState;
        Log.d(TAG, "previous lteuState = " + lteuState2 + ", lteuEnable = " + mLteuEnable);
        if (!isEnabled || !is5GHz) {
            lteuState = lteuState2 & (~LTEU_WIFI_STATE);
            this.laaActiveState = 1;
        } else {
            lteuState = lteuState2 | LTEU_WIFI_STATE;
        }
        if (lteuState <= 0 || lteuState >= 8) {
            lteuEnable = 1;
        } else {
            lteuEnable = 0;
        }
        Log.d(TAG, "input = " + LTEU_WIFI_STATE + ", is5GHz = " + is5GHz);
        Log.d(TAG, "new lteuState = " + lteuState + ", lteuEnable = " + lteuEnable);
        if (forceSend || (mScellEnter && lteuState != mLteuState)) {
            ITelephony phone = ITelephony.Stub.asInterface(ServiceManager.checkService("phone"));
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(bos);
            try {
                dos.writeByte(17);
                dos.writeByte(144);
                dos.writeShort(5);
                dos.writeByte(lteuEnable);
                try {
                    dos.close();
                } catch (Exception e) {
                }
                try {
                    byte[] responseData = new byte[2048];
                    if (phone != null) {
                        int ret = phone.invokeOemRilRequestRaw(bos.toByteArray(), responseData);
                        Log.i(TAG, "invokeOemRilRequestRaw : return value: " + ret);
                    } else {
                        Log.d(TAG, "ITelephony is null");
                    }
                } catch (RemoteException e2) {
                    Log.e(TAG, "invokeOemRilRequestRaw : RemoteException: " + e2);
                }
            } catch (IOException e3) {
                Log.e(TAG, "IOException occurs in set lteuEnable");
                try {
                    dos.close();
                    return;
                } catch (Exception e4) {
                    return;
                }
            } catch (Throwable th) {
                try {
                    dos.close();
                } catch (Exception e5) {
                }
                throw th;
            }
        }
        mLteuState = lteuState;
        mLteuEnable = lteuEnable;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private int waitForDhcpRelease() {
        try {
            Thread.sleep((long) 500);
            return 0;
        } catch (InterruptedException ex) {
            loge("waitForDhcpRelease sleep exception:" + ex);
            return 0;
        }
    }

    private boolean isSystem(int uid) {
        return uid < 10000;
    }

    public void registerWifiNetworkCallbacks(ClientModeChannel.WifiNetworkCallback wifiNetworkCallback) {
        if (isSystem(Binder.getCallingUid())) {
            Log.w(TAG, "registerWCMCallbacks");
            if (this.mWifiNetworkCallbackList == null) {
                this.mWifiNetworkCallbackList = new ArrayList<>();
            }
            this.mWifiNetworkCallbackList.add(wifiNetworkCallback);
            return;
        }
        Log.w(TAG, "This is only for system service");
        throw new SecurityException("This is only for system service");
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void handleWifiNetworkCallbacks(int method) {
        if (method != 1) {
            switch (method) {
                case 5:
                    Iterator<ClientModeChannel.WifiNetworkCallback> it = this.mWifiNetworkCallbackList.iterator();
                    while (it.hasNext()) {
                        it.next().notifyRoamSession("start");
                    }
                    return;
                case 6:
                    Iterator<ClientModeChannel.WifiNetworkCallback> it2 = this.mWifiNetworkCallbackList.iterator();
                    while (it2.hasNext()) {
                        it2.next().notifyRoamSession("complete");
                    }
                    return;
                case 7:
                    Iterator<ClientModeChannel.WifiNetworkCallback> it3 = this.mWifiNetworkCallbackList.iterator();
                    while (it3.hasNext()) {
                        it3.next().notifyLinkPropertiesUpdated(this.mLinkProperties);
                    }
                    return;
                case 8:
                    this.isDhcpStartSent = true;
                    Iterator<ClientModeChannel.WifiNetworkCallback> it4 = this.mWifiNetworkCallbackList.iterator();
                    while (it4.hasNext()) {
                        it4.next().notifyDhcpSession("start");
                    }
                    return;
                case 9:
                    if (this.isDhcpStartSent) {
                        this.isDhcpStartSent = false;
                        Iterator<ClientModeChannel.WifiNetworkCallback> it5 = this.mWifiNetworkCallbackList.iterator();
                        while (it5.hasNext()) {
                            it5.next().notifyDhcpSession("complete");
                        }
                        return;
                    }
                    return;
                case 10:
                    Iterator<ClientModeChannel.WifiNetworkCallback> it6 = this.mWifiNetworkCallbackList.iterator();
                    while (it6.hasNext()) {
                        it6.next().notifyReachabilityLost();
                    }
                    return;
                case 11:
                    Iterator<ClientModeChannel.WifiNetworkCallback> it7 = this.mWifiNetworkCallbackList.iterator();
                    while (it7.hasNext()) {
                        it7.next().notifyProvisioningFail();
                    }
                    return;
                default:
                    return;
            }
        } else {
            Iterator<ClientModeChannel.WifiNetworkCallback> it8 = this.mWifiNetworkCallbackList.iterator();
            while (it8.hasNext()) {
                it8.next().checkIsCaptivePortalException(getTargetSsid());
            }
        }
    }

    public WifiClientModeChannel makeWifiClientModeChannel() {
        return new WifiClientModeChannel();
    }

    public class WifiClientModeChannel implements ClientModeChannel {
        public static final int CALLBACK_CHECK_IS_CAPTIVE_PORTAL_EXCEPTION = 1;
        public static final int CALLBACK_FETCH_RSSI_PKTCNT_DISCONNECTED = 4;
        public static final int CALLBACK_FETCH_RSSI_PKTCNT_FAILED = 3;
        public static final int CALLBACK_FETCH_RSSI_PKTCNT_SUCCESS = 2;
        public static final int CALLBACK_NOTIFY_DHCP_SESSION_COMPLETE = 9;
        public static final int CALLBACK_NOTIFY_DHCP_SESSION_START = 8;
        public static final int CALLBACK_NOTIFY_LINK_PROPERTIES_UPDATED = 7;
        public static final int CALLBACK_NOTIFY_PROVISIONING_FAIL = 11;
        public static final int CALLBACK_NOTIFY_REACHABILITY_LOST = 10;
        public static final int CALLBACK_NOTIFY_ROAM_SESSION_COMPLETE = 6;
        public static final int CALLBACK_NOTIFY_ROAM_SESSION_START = 5;

        public WifiClientModeChannel() {
            ClientModeImpl.this.isDhcpStartSent = false;
        }

        @Override // com.android.server.wifi.ClientModeImpl.ClientModeChannel
        public Message fetchPacketCountNative() {
            Message msg = ClientModeImpl.this.obtainMessage();
            if (!ClientModeImpl.this.isConnected()) {
                msg.what = 4;
                return msg;
            }
            ClientModeImpl.this.fetchRssiLinkSpeedAndFrequencyNative();
            WifiNative.TxPacketCounters counters = ClientModeImpl.this.mWifiNative.getTxPacketCounters(ClientModeImpl.this.mInterfaceName);
            if (counters != null) {
                msg.what = 2;
                msg.arg1 = counters.txSucceeded;
                msg.arg2 = counters.txFailed;
            } else {
                msg.what = 3;
            }
            return msg;
        }

        public void setWifiNetworkEnabled(boolean valid) {
            ClientModeImpl.this.mWifiScoreReport.setWifiNetworkEnabled(valid);
        }

        public boolean getManualSelection() {
            return ClientModeImpl.this.mIsManualSelection;
        }

        public void setCaptivePortal(int netId, boolean captivePortal) {
            ClientModeImpl.this.sendMessage(WifiConnectivityMonitor.CMD_CONFIG_SET_CAPTIVE_PORTAL, netId, captivePortal ? 1 : 0);
        }

        public void updateNetworkSelectionStatus(int netId, int reason) {
            ClientModeImpl.this.sendMessage(WifiConnectivityMonitor.CMD_CONFIG_UPDATE_NETWORK_SELECTION, netId, reason);
        }

        public boolean eleDetectedDebug() {
            if (!ClientModeImpl.DBG_PRODUCT_DEV || (ClientModeImpl.mWlanAdvancedDebugState & 32) == 0) {
                return false;
            }
            Log.i(ClientModeImpl.TAG, "eleDetectedDebug return true");
            return true;
        }

        public void unwantedMoreDump() {
            if (ClientModeImpl.DBG_PRODUCT_DEV) {
                FileWriter writer = null;
                if ((ClientModeImpl.mWlanAdvancedDebugState & 16) != 0) {
                    try {
                        File file = new File("/data/log/mx_panic");
                        if (!file.exists()) {
                            file.createNewFile();
                            if (file.exists()) {
                                writer = new FileWriter(file);
                                writer.append((CharSequence) "1");
                                writer.flush();
                            }
                        }
                        try {
                            try {
                                Runtime.getRuntime().exec(new String[]{"/system/bin/sh", "-c", "system/bin/cp /data/log/mx_panic /proc/driver/mxman_ctrl0/mx_panic"}).waitFor();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        } catch (IOException e2) {
                            e2.printStackTrace();
                        }
                        try {
                            try {
                                Runtime.getRuntime().exec(new String[]{"/system/bin/sh", "-c", "system/bin/bugreport > /data/log/unwant_dumpState_" + ClientModeImpl.this.getTimeToString() + ".log"}).waitFor();
                            } catch (InterruptedException e3) {
                                e3.printStackTrace();
                            }
                        } catch (IOException e4) {
                            e4.printStackTrace();
                        }
                        if (writer != null) {
                            try {
                                writer.close();
                            } catch (IOException e5) {
                                e5.printStackTrace();
                            }
                        }
                    } catch (Exception e6) {
                        e6.printStackTrace();
                        if (0 != 0) {
                            writer.close();
                        }
                    } catch (Throwable th) {
                        if (0 != 0) {
                            try {
                                writer.close();
                            } catch (IOException e7) {
                                e7.printStackTrace();
                            }
                        }
                        throw th;
                    }
                }
            }
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void sendWcmConfigurationChanged() {
        Intent intent = new Intent();
        intent.setAction("ACTION_WCM_CONFIGURATION_CHANGED");
        Context context = this.mContext;
        if (context != null) {
            try {
                context.sendBroadcastAsUser(intent, UserHandle.ALL);
            } catch (IllegalStateException e) {
                loge("Send broadcast - action:" + intent.getAction());
            }
        }
    }

    private boolean isPoorNetworkTestEnabled() {
        return Settings.Global.getInt(this.mContext.getContentResolver(), "wifi_watchdog_poor_network_test_enabled", 0) == 1;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean isWCMEnabled() {
        if ("REMOVED".equals(SemCscFeature.getInstance().getString(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_CONFIGSNSSTATUS))) {
            return false;
        }
        return true;
    }

    public void setIWCMonitorAsyncChannel(Handler handler) {
        log("setIWCAsyncChannel");
        if (this.mIWCMonitorChannel == null) {
            if (DBG) {
                log("new mWcmChannel created");
            }
            this.mIWCMonitorChannel = new AsyncChannel();
        }
        if (DBG) {
            log("mWcmChannel connected");
        }
        this.mIWCMonitorChannel.connect(this.mContext, getHandler(), handler);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void setAnalyticsUserDisconnectReason(short reason) {
        Log.d(TAG, "setAnalyticsUserDisconnectReason " + ((int) reason));
        this.mWifiNative.setAnalyticsDisconnectReason(this.mInterfaceName, reason);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void notifyMobilewipsRoamEvent(String startComplete) {
        if ("".equals(CONFIG_SECURE_SVC_INTEGRATION) && !SemCscFeature.getInstance().getBoolean(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_DISABLEMWIPS) && MobileWipsFrameworkService.getInstance() != null && startComplete.equals("start")) {
            MobileWipsFrameworkService.getInstance().sendEmptyMessage(24);
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void updateEDMWiFiPolicy() {
        this.mWifiConfigManager.forcinglyEnablePolicyUpdatedNetworks(1000);
        if (this.mWifiInfo == null || !isConnected()) {
            Log.d(TAG, "wifi Info is null or no connected AP.");
            return;
        }
        Log.d(TAG, "updateEDMWiFiPolicy. SSID: " + this.mWifiInfo.getSSID());
        if (this.mWifiInfo.getSSID() != null && !this.mWifiInfo.getSSID().isEmpty()) {
            WifiB2BConfigurationPolicy.B2BConfiguration conf = this.mWifiB2bConfigPolicy.getConfiguration(StringUtil.removeDoubleQuotes(this.mWifiInfo.getSSID()));
            if (conf != null) {
                boolean result = true;
                if (conf.getRoamTrigger() != Integer.MAX_VALUE || conf.getRoamDelta() != Integer.MAX_VALUE || conf.getScanPeriod() != Integer.MAX_VALUE) {
                    if (!this.mWifiB2bConfigPolicy.isPolicyApplied()) {
                        if (getNCHOVersion() == 2) {
                            result = setNCHO20State(1, true);
                        }
                        this.mWifiB2bConfigPolicy.setPolicyApplied(true);
                        Log.d(TAG, "updateEDMWiFiPolicy - setNCHOMode: " + result);
                    }
                    this.mWifiNative.setRoamTrigger(this.mInterfaceName, conf.getRoamTrigger() == Integer.MAX_VALUE ? -75 : conf.getRoamTrigger());
                    int roamScanPediod = 10;
                    this.mWifiNative.setRoamDelta(this.mInterfaceName, conf.getRoamDelta() == Integer.MAX_VALUE ? 10 : conf.getRoamDelta());
                    if (conf.getScanPeriod() != Integer.MAX_VALUE) {
                        roamScanPediod = conf.getScanPeriod();
                    }
                    this.mWifiNative.setRoamScanPeriod(this.mInterfaceName, roamScanPediod);
                } else if (this.mWifiB2bConfigPolicy.isPolicyApplied()) {
                    Log.d(TAG, "No policy exists, clear previous Policy");
                    clearEDMWiFiPolicy();
                }
                if (conf.skipDHCPRenewal()) {
                    this.mRoamDhcpPolicyByB2bConfig = 2;
                } else {
                    this.mRoamDhcpPolicyByB2bConfig = 0;
                }
            } else {
                clearEDMWiFiPolicy();
            }
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void clearEDMWiFiPolicy() {
        Log.d(TAG, "clearEDMWiFiPolicy: " + this.mWifiB2bConfigPolicy.isPolicyApplied() + "/" + this.mIsNchoParamSet);
        this.mRoamDhcpPolicyByB2bConfig = 0;
        if (this.mWifiB2bConfigPolicy.isPolicyApplied() || this.mIsNchoParamSet) {
            int nchoVersion = getNCHOVersion();
            if (nchoVersion == 1 && getNCHO10State() == 1) {
                restoreNcho10Param();
            } else if (nchoVersion == 2) {
                setNCHO20State(0, true);
            }
        }
        this.mWifiB2bConfigPolicy.setPolicyApplied(false);
        this.mIsNchoParamSet = false;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private int getNCHOVersion() {
        if (this.mNchoVersion == -1) {
            if (this.mWifiState.get() != 3) {
                Log.e(TAG, "getNCHOVersion Wi-Fi is not enabled state");
                return -1;
            }
            int result = this.mWifiNative.getNCHOMode(this.mInterfaceName);
            if (result == -1) {
                this.mNchoVersion = 1;
                if (getNCHO10State() != 1) {
                    backUpNcho10Param();
                }
            } else if (result == 0 || result == 1) {
                this.mNchoVersion = 2;
                setNCHO20State(result, false);
            } else {
                Log.e(TAG, "getNCHOVersion Error: " + this.mNchoVersion);
                this.mNchoVersion = 0;
            }
        }
        if (this.mVerboseLoggingEnabled) {
            Log.d(TAG, "getNCHOVersion Version: " + this.mNchoVersion);
        }
        return this.mNchoVersion;
    }

    private int getNCHO10State() {
        if (getNCHOVersion() != 1) {
            Log.e(TAG, "getNCHO10State version is not 1.0");
            return -1;
        }
        if (this.mVerboseLoggingEnabled) {
            Log.d(TAG, "getNCHO10State: " + this.mNcho10State);
        }
        return this.mNcho10State;
    }

    private boolean setNCHO10State(int state) {
        if (getNCHOVersion() != 1) {
            Log.e(TAG, "setNCHO10State version is not 1.0");
            return false;
        }
        if (this.mVerboseLoggingEnabled) {
            Log.d(TAG, "setNCHO10State: " + state);
        }
        this.mNcho10State = state;
        return true;
    }

    private int getNCHO20State() {
        if (getNCHOVersion() != 2) {
            Log.e(TAG, "getNCHO20State version is not 2.0");
            return -1;
        }
        if (this.mVerboseLoggingEnabled) {
            Log.d(TAG, "getNCHO20State: " + this.mNcho20State);
        }
        return this.mNcho20State;
    }

    private boolean setNCHO20State(int state, boolean setToDriver) {
        boolean result = true;
        if (getNCHOVersion() != 2) {
            Log.e(TAG, "setNCHO20State version is not 2.0");
            return false;
        }
        if (this.mVerboseLoggingEnabled) {
            Log.d(TAG, "setNCHO20State: " + state + ", setToDriver: " + setToDriver);
        }
        if (setToDriver) {
            result = this.mWifiNative.setNCHOMode(this.mInterfaceName, state);
        }
        if (result) {
            this.mNcho20State = state;
        } else {
            Log.e(TAG, "setNCHO20State setNCHOMode fail");
        }
        return result;
    }

    private boolean backUpNcho10Param() {
        if (getNCHOVersion() != 1) {
            Log.e(TAG, "backUpNcho10Param NCHO version is not 1.0");
            return false;
        } else if (getNCHO10State() == 1) {
            Log.e(TAG, "backUpNcho10Param already backed up");
            return false;
        } else {
            this.mDefaultRoamTrigger = this.mWifiNative.getRoamTrigger(this.mInterfaceName);
            if (this.mDefaultRoamTrigger == -1) {
                this.mDefaultRoamTrigger = -75;
            }
            this.mDefaultRoamDelta = this.mWifiNative.getRoamDelta(this.mInterfaceName);
            if (this.mDefaultRoamDelta == -1) {
                this.mDefaultRoamDelta = 10;
            }
            this.mDefaultRoamScanPeriod = this.mWifiNative.getRoamScanPeriod(this.mInterfaceName);
            if (this.mDefaultRoamScanPeriod == -1) {
                this.mDefaultRoamScanPeriod = 10;
            }
            setNCHO10State(1);
            Log.d(TAG, "ncho10BackUp: " + this.mDefaultRoamTrigger + "/" + this.mDefaultRoamDelta + "/" + this.mDefaultRoamScanPeriod);
            return true;
        }
    }

    private boolean restoreNcho10Param() {
        if (getNCHOVersion() != 1) {
            Log.e(TAG, "ncho10BackUp NCHO version is not 1.0");
            return false;
        } else if (getNCHO10State() != 1) {
            Log.e(TAG, "ncho 10 is not backed up");
            return false;
        } else {
            Log.d(TAG, "restoreNcho10Param: " + this.mDefaultRoamTrigger + "/" + this.mDefaultRoamDelta + "/" + this.mDefaultRoamScanPeriod);
            this.mWifiNative.setRoamTrigger(this.mInterfaceName, this.mDefaultRoamTrigger);
            this.mWifiNative.setRoamDelta(this.mInterfaceName, this.mDefaultRoamDelta);
            this.mWifiNative.setRoamScanPeriod(this.mInterfaceName, this.mDefaultRoamScanPeriod);
            return true;
        }
    }

    public int setRoamDhcpPolicy(int mode) {
        this.mRoamDhcpPolicy = mode;
        Log.d(TAG, "Set mRoamDhcpPolicy : " + this.mRoamDhcpPolicy);
        return this.mRoamDhcpPolicy;
    }

    public int getRoamDhcpPolicy() {
        Log.d(TAG, "Get mRoamDhcpPolicy : " + this.mRoamDhcpPolicy);
        return this.mRoamDhcpPolicy;
    }

    /* access modifiers changed from: package-private */
    public boolean isValidLocation(double latitude, double longitude) {
        if (latitude >= -90.0d && latitude <= 90.0d && longitude >= -180.0d && longitude <= 180.0d) {
            return true;
        }
        Log.d(TAG, "invalid location");
        return false;
    }

    /* access modifiers changed from: package-private */
    public void updateLocation(int isActiveRequest) {
        Log.d(TAG, "updateLocation " + isActiveRequest);
        this.mSemLocationManager = (SemLocationManager) this.mContext.getSystemService("sec_location");
        if (isActiveRequest == 1) {
            this.mSemLocationManager.requestSingleLocation(100, 30, false, this.mSemLocationListener);
            return;
        }
        this.mLocationPendingIntent = PendingIntent.getBroadcast(this.mContext, 0, new Intent(ACTION_AP_LOCATION_PASSIVE_REQUEST), 0);
        this.mSemLocationManager.requestPassiveLocation(this.mLocationPendingIntent);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean isLocationSupportedAp(WifiConfiguration config) {
        if (config == null) {
            return false;
        }
        if (config.semSamsungSpecificFlags.get(1) || config.semIsVendorSpecificSsid) {
            Log.d(TAG, "This is a Samsung Hotspot");
            return false;
        }
        if (this.mWifiInfo != null) {
            for (int mask : MHS_PRIVATE_NETWORK_MASK) {
                if ((this.mWifiInfo.getIpAddress() & 16777215) == mask) {
                    Log.d(TAG, "This is a Mobile Hotspot");
                    return false;
                }
            }
        }
        if (!config.semIsVendorSpecificSsid) {
            return true;
        }
        Log.d(TAG, "This is vendor AP");
        return false;
    }

    public double[] getLatitudeLongitude(WifiConfiguration config) {
        String latitudeLongitude = this.mWifiGeofenceManager.getLatitudeAndLongitude(config);
        double[] latitudeLongitudeDouble = new double[2];
        if (latitudeLongitude != null) {
            String[] latitudeLongitudeString = latitudeLongitude.split(":");
            latitudeLongitudeDouble[0] = Double.parseDouble(latitudeLongitudeString[0]);
            latitudeLongitudeDouble[1] = Double.parseDouble(latitudeLongitudeString[1]);
        } else {
            double d = INVALID_LATITUDE_LONGITUDE;
            latitudeLongitudeDouble[0] = d;
            latitudeLongitudeDouble[1] = d;
        }
        return latitudeLongitudeDouble;
    }

    public void checkAlternativeNetworksForWmc(boolean mNeedRoamingInHighQuality) {
        sendMessage(WifiConnectivityMonitor.CHECK_ALTERNATIVE_NETWORKS, mNeedRoamingInHighQuality ? 1 : 0);
    }

    public void startScanFromWcm() {
        this.mScanRequestProxy.startScan(1000, this.mContext.getOpPackageName());
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean checkIfForceRestartDhcp() {
        if (getRoamDhcpPolicy() == 1) {
            if (DBG) {
                log("ForceRestartDhcp by uready");
            }
            return true;
        }
        String ssid = this.mWifiInfo.getSSID();
        if ((ssid == null || (!ssid.contains("marente") && !ssid.contains("0001docomo") && !ssid.contains("ollehWiFi") && !ssid.contains("olleh GiGA WiFi") && !ssid.contains("KT WiFi") && !ssid.contains("KT GiGA WiFi"))) && !ssid.contains("T wifi zone")) {
            return false;
        }
        if (DBG) {
            log("ForceRestartDhcp");
        }
        return true;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void restartDhcp(WifiConfiguration wc) {
        if (wc == null) {
            Log.d(TAG, "Stop restarting Dhcp as currentConfig is null");
            return;
        }
        if (this.mIpClient != null) {
            this.mIpClient.stop();
        }
        setTcpBufferAndProxySettingsForIpManager();
        ProvisioningConfiguration prov = new ProvisioningConfiguration.Builder().withPreDhcpAction().withApfCapabilities(this.mWifiNative.getApfCapabilities(this.mInterfaceName)).withNetwork(getCurrentNetwork()).withDisplayName(wc.SSID).withRandomMacAddress().build();
        if (this.mIpClient != null) {
            this.mIpClient.startProvisioning(prov);
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void CheckIfDefaultGatewaySame() {
        Log.d(TAG, "CheckIfDefaultGatewaySame");
        removeMessages(CMD_CHECK_ARP_RESULT);
        removeMessages(CMD_SEND_ARP);
        InetAddress inetAddress = null;
        InetAddress gateway = null;
        Iterator<LinkAddress> it = this.mLinkProperties.getLinkAddresses().iterator();
        while (true) {
            if (!it.hasNext()) {
                break;
            }
            LinkAddress la = it.next();
            if (la.getAddress() instanceof Inet4Address) {
                inetAddress = la.getAddress();
                break;
            }
        }
        for (RouteInfo route : this.mLinkProperties.getRoutes()) {
            if (route.getGateway() instanceof Inet4Address) {
                gateway = route.getGateway();
            }
        }
        if (inetAddress == null || gateway == null) {
            restartDhcp(getCurrentWifiConfiguration());
            return;
        }
        try {
            ArpPeer peer = new ArpPeer();
            peer.checkArpReply(this.mLinkProperties, ROAMING_ARP_TIMEOUT_MS, gateway.getAddress(), inetAddress.getAddress(), this.mWifiInfo.getMacAddress());
            if (SemCscFeature.getInstance().getBoolean(C0852CscFeatureTagCommon.TAG_CSCFEATURE_COMMON_SUPPORTCOMCASTWIFI)) {
                peer.sendGArp(this.mLinkProperties, inetAddress.getAddress(), this.mWifiInfo.getMacAddress());
            }
            sendMessageDelayed(CMD_SEND_ARP, 100);
            sendMessageDelayed(CMD_CHECK_ARP_RESULT, 2000);
        } catch (Exception e) {
            Log.e(TAG, "Exception: " + e);
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean isSupportRandomMac(WifiConfiguration config) {
        if (config != null) {
            String ssid = StringUtil.removeDoubleQuotes(config.SSID);
            if (config.isPasspoint()) {
                return false;
            }
            if (!config.semIsVendorSpecificSsid) {
                if (config.allowedKeyManagement.get(1) || config.allowedKeyManagement.get(4)) {
                }
                if (mOpBranding.getCountry() != 1 || (!"ollehWiFi ".equals(ssid) && !"olleh GiGA WiFi ".equals(ssid) && !"KT WiFi ".equals(ssid) && !"KT GiGA WiFi ".equals(ssid) && !"T wifi zone".equals(ssid) && !"U+zone".equals(ssid) && !"U+zone_5G".equals(ssid) && !"5G_U+zone".equals(ssid))) {
                    return true;
                }
                return false;
            } else if (OpBrandingLoader.Vendor.DCM != mOpBranding || (!"0000docomo".equals(ssid) && !"0001docomo".equals(ssid))) {
                return false;
            } else {
                return true;
            }
        }
        return true;
    }

    public void enablePollingRssiForSwitchboard(boolean enable, int newPollIntervalMsecs) {
        if (enable) {
            mRssiPollingScreenOffEnabled |= 2;
        } else {
            mRssiPollingScreenOffEnabled &= -3;
        }
        setPollRssiIntervalMsecs(newPollIntervalMsecs);
        if (mRssiPollingScreenOffEnabled != 0) {
            if (!this.mEnableRssiPolling) {
                enableRssiPolling(true);
            }
        } else if (this.mEnableRssiPolling && !this.mScreenOn) {
            enableRssiPolling(false);
        }
    }

    public int getBeaconCount() {
        return this.mRunningBeaconCount;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void setRoamTriggered(boolean enabled) {
        this.mIsRoaming = enabled;
        if (enabled) {
            handleWifiNetworkCallbacks(5);
            try {
                if (!this.mIsRoamNetwork) {
                    if (checkAndSetConnectivityInstance() && !isWifiOnly()) {
                        this.mCm.setWifiRoamNetwork(true);
                    }
                    this.mIsRoamNetwork = true;
                    Log.i(TAG, "Roam Network");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            handleWifiNetworkCallbacks(6);
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean isRoaming() {
        return this.mIsRoaming;
    }

    private boolean detectIpv6ProvisioningFailure(LinkProperties oldLp, LinkProperties newLp) {
        if (oldLp == null || newLp == null) {
            return false;
        }
        boolean lostIPv6 = oldLp.isIpv6Provisioned() && !newLp.isIpv6Provisioned();
        boolean lostIPv4Address = oldLp.hasIpv4Address() && !newLp.hasIpv4Address();
        boolean lostIPv6Router = oldLp.hasIpv6DefaultRoute() && !newLp.hasIpv6DefaultRoute();
        if (lostIPv4Address) {
            return false;
        }
        if (lostIPv6) {
            Log.d(TAG, "lostIPv6");
            return true;
        } else if (!oldLp.hasGlobalIpv6Address() || !lostIPv6Router) {
            return false;
        } else {
            Log.d(TAG, "return true by ipv6 provisioning failure");
            return true;
        }
    }
}
