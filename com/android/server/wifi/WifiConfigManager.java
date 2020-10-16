package com.android.server.wifi;

import android.app.ActivityManager;
import android.app.admin.DevicePolicyManagerInternal;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.net.IpConfiguration;
import android.net.MacAddress;
import android.net.ProxyInfo;
import android.net.StaticIpConfiguration;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiEnterpriseConfig;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiScanner;
import android.os.Debug;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.sec.enterprise.EnterpriseDeviceManager;
import android.sec.enterprise.WifiPolicy;
import android.sec.enterprise.WifiPolicyCache;
import android.sec.enterprise.certificate.CertificatePolicy;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.LocalLog;
import android.util.Log;
import android.util.Pair;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.wifi.WifiBackupRestore;
import com.android.server.wifi.WifiConfigStore;
import com.android.server.wifi.WifiConfigurationUtil;
import com.android.server.wifi.hotspot2.PasspointManager;
import com.android.server.wifi.util.NativeUtil;
import com.android.server.wifi.util.RilUtil;
import com.android.server.wifi.util.StringUtil;
import com.android.server.wifi.util.TelephonyUtil;
import com.android.server.wifi.util.WifiPermissionsUtil;
import com.android.server.wifi.util.WifiPermissionsWrapper;
import com.samsung.android.feature.SemCscFeature;
import com.samsung.android.net.wifi.OpBrandingLoader;
import com.samsung.android.server.wifi.SemSarManager;
import com.samsung.android.server.wifi.SemWifiFrameworkUxUtils;
import com.samsung.android.server.wifi.WifiB2BConfigurationPolicy;
import com.samsung.android.server.wifi.WifiMobileDeviceManager;
import com.sec.android.app.CscFeatureTagWifi;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.xmlpull.v1.XmlPullParserException;

public class WifiConfigManager {
    private static final String CHARSET_CN = "gbk";
    private static final String CHARSET_KOR = "ksc5601";
    private static final String CONFIG_CHARSET = OpBrandingLoader.getInstance().getSupportCharacterSet();
    static final int CSC_DEFAULT_MAX_NETWORKS_FOR_CURRENT_USER = SemCscFeature.getInstance().getInteger(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_CONFIGMAXCONFIGUREDNETWORKSSIZE, 200);
    private static final boolean DBG = Debug.semIsProductDev();
    private static final MacAddress DEFAULT_MAC_ADDRESS = MacAddress.fromString("02:00:00:00:00:00");
    private static final int DEFAULT_MAX_NETWORKS_FOR_CURRENT_USER = 200;
    @VisibleForTesting
    public static final long DELETED_EPHEMERAL_SSID_EXPIRY_MS = 86400000;
    @VisibleForTesting
    public static final int LINK_CONFIGURATION_BSSID_MATCH_LENGTH = 16;
    @VisibleForTesting
    public static final int LINK_CONFIGURATION_MAX_SCAN_CACHE_ENTRIES = 6;
    @VisibleForTesting
    public static final long MAX_PNO_SCAN_FREQUENCY_AGE_MS = 2592000000L;
    @VisibleForTesting
    public static final int[] NETWORK_SELECTION_DISABLE_THRESHOLD = {-1, 1, 5, 5, 2, 5, 1, 1, 6, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1};
    @VisibleForTesting
    public static final int[] NETWORK_SELECTION_DISABLE_TIMEOUT_MS = {Integer.MAX_VALUE, 900000, WifiConnectivityManager.BSSID_BLACKLIST_EXPIRE_TIME_MS, WifiConnectivityManager.BSSID_BLACKLIST_EXPIRE_TIME_MS, WifiConnectivityManager.BSSID_BLACKLIST_EXPIRE_TIME_MS, WifiConnectivityManager.BSSID_BLACKLIST_EXPIRE_TIME_MS, 600000, 0, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, WifiConnectivityManager.BSSID_BLACKLIST_EXPIRE_TIME_MS, Integer.MAX_VALUE, Integer.MAX_VALUE, WifiConnectivityManager.BSSID_BLACKLIST_EXPIRE_TIME_MS, 900000, Integer.MAX_VALUE, 3600000, 900000};
    @VisibleForTesting
    public static final String PASSWORD_MASK = "*";
    @VisibleForTesting
    public static final int SCAN_CACHE_ENTRIES_MAX_SIZE = 192;
    @VisibleForTesting
    public static final int SCAN_CACHE_ENTRIES_TRIM_SIZE = 128;
    private static final int SCAN_RESULT_MAXIMUM_AGE_MS = 40000;
    private static final String STORE_DIRECTORY_NAME = "wifi";
    @VisibleForTesting
    public static final String SYSUI_PACKAGE_NAME = "com.android.systemui";
    private static final String TAG = "WifiConfigManager";
    private static final int WIFI_PNO_FREQUENCY_CULLING_ENABLED_DEFAULT = 1;
    private static final int WIFI_PNO_RECENCY_SORTING_ENABLED_DEFAULT = 1;
    private static final boolean mRemovableDefaultAp = SemCscFeature.getInstance().getBoolean(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_REMOVABLEDEFAULTAP);
    private static final WifiConfigurationUtil.WifiConfigurationComparator sScanListComparator = new WifiConfigurationUtil.WifiConfigurationComparator() {
        /* class com.android.server.wifi.WifiConfigManager.C04241 */

        @Override // com.android.server.wifi.WifiConfigurationUtil.WifiConfigurationComparator
        public int compareNetworksWithSameStatus(WifiConfiguration a, WifiConfiguration b) {
            if (a.numAssociation != b.numAssociation) {
                return Long.compare((long) b.numAssociation, (long) a.numAssociation);
            }
            return Boolean.compare(b.getNetworkSelectionStatus().getSeenInLastQualifiedNetworkSelection(), a.getNetworkSelectionStatus().getSeenInLastQualifiedNetworkSelection());
        }
    };
    private boolean mAutoConnectCarrierApEnabled;
    private final BackupManagerProxy mBackupManagerProxy;
    private final Clock mClock;
    private final ConfigurationMap mConfiguredNetworks;
    private final Context mContext;
    private int mCurrentNetId;
    private int mCurrentUserId;
    private boolean mDeferredUserUnlockRead;
    private final DeletedEphemeralSsidsStoreData mDeletedEphemeralSsidsStoreData;
    private final Map<String, Long> mDeletedEphemeralSsidsToTimeMap;
    private File mFilePathRemovedNwInfo;
    private final FrameworkFacade mFrameworkFacade;
    private boolean mIsCarrierNetworkSaved;
    private int mLastPriority;
    private int mLastSelectedNetworkId;
    private long mLastSelectedTimeStamp;
    private OnSavedNetworkUpdateListener mListener;
    private final LocalLog mLocalLog;
    private final Object mLock;
    private final int mMaxNumActiveChannelsForPartialScans;
    private boolean mNetworkAutoConnectEnabled;
    private final NetworkListSharedStoreData mNetworkListSharedStoreData;
    private final NetworkListUserStoreData mNetworkListUserStoreData;
    private int mNextNetworkId;
    private final boolean mOnlyLinkSameCredentialConfigurations;
    private boolean mPendingStoreRead;
    private boolean mPendingUnlockStoreRead;
    private boolean mPnoFrequencyCullingEnabled;
    private boolean mPnoRecencySortingEnabled;
    private final Map<String, String> mRandomizedMacAddressMapping;
    private final RandomizedMacStoreData mRandomizedMacStoreData;
    private final Map<Integer, ScanDetailCache> mScanDetailCaches;
    private int mSystemUiUid;
    private final TelephonyManager mTelephonyManager;
    private final UserManager mUserManager;
    private boolean mUserSelectNetwork;
    private boolean mVerboseLoggingEnabled;
    private final WifiConfigStore mWifiConfigStore;
    private final WifiInjector mWifiInjector;
    private final WifiKeyStore mWifiKeyStore;
    private final WifiPermissionsUtil mWifiPermissionsUtil;
    private final WifiPermissionsWrapper mWifiPermissionsWrapper;
    private final WifiPolicy mWifiPolicy;

    public interface OnSavedNetworkUpdateListener {
        void onSavedNetworkAdded(int i);

        void onSavedNetworkEnabled(int i);

        void onSavedNetworkPermanentlyDisabled(int i, int i2);

        void onSavedNetworkRemoved(int i);

        void onSavedNetworkTemporarilyDisabled(int i, int i2);

        void onSavedNetworkUpdated(int i);
    }

    WifiConfigManager(Context context, Clock clock, UserManager userManager, TelephonyManager telephonyManager, WifiKeyStore wifiKeyStore, WifiConfigStore wifiConfigStore, WifiPermissionsUtil wifiPermissionsUtil, WifiPermissionsWrapper wifiPermissionsWrapper, WifiInjector wifiInjector, NetworkListSharedStoreData networkListSharedStoreData, NetworkListUserStoreData networkListUserStoreData, DeletedEphemeralSsidsStoreData deletedEphemeralSsidsStoreData, RandomizedMacStoreData randomizedMacStoreData, FrameworkFacade frameworkFacade, Looper looper) {
        this.mLocalLog = new LocalLog(ActivityManager.isLowRamDeviceStatic() ? 128 : 256);
        this.mVerboseLoggingEnabled = false;
        this.mCurrentUserId = 0;
        this.mPendingUnlockStoreRead = true;
        this.mPendingStoreRead = true;
        this.mDeferredUserUnlockRead = false;
        this.mNextNetworkId = 0;
        this.mSystemUiUid = -1;
        this.mLastSelectedNetworkId = -1;
        this.mLastSelectedTimeStamp = -1;
        this.mLock = new Object();
        this.mLastPriority = 0;
        this.mCurrentNetId = -1;
        this.mListener = null;
        this.mPnoFrequencyCullingEnabled = false;
        this.mPnoRecencySortingEnabled = false;
        this.mFilePathRemovedNwInfo = new File("/data/misc/wifi/removed_nw.conf");
        this.mIsCarrierNetworkSaved = false;
        this.mContext = context;
        this.mClock = clock;
        this.mUserManager = userManager;
        this.mBackupManagerProxy = new BackupManagerProxy();
        this.mTelephonyManager = telephonyManager;
        this.mWifiKeyStore = wifiKeyStore;
        this.mWifiConfigStore = wifiConfigStore;
        this.mWifiPermissionsUtil = wifiPermissionsUtil;
        this.mWifiPermissionsWrapper = wifiPermissionsWrapper;
        this.mWifiInjector = wifiInjector;
        this.mConfiguredNetworks = new ConfigurationMap(userManager);
        this.mScanDetailCaches = new HashMap(16, 0.75f);
        this.mDeletedEphemeralSsidsToTimeMap = new HashMap();
        this.mRandomizedMacAddressMapping = new HashMap();
        this.mNetworkListSharedStoreData = networkListSharedStoreData;
        this.mNetworkListUserStoreData = networkListUserStoreData;
        this.mDeletedEphemeralSsidsStoreData = deletedEphemeralSsidsStoreData;
        this.mRandomizedMacStoreData = randomizedMacStoreData;
        this.mWifiConfigStore.registerStoreData(this.mNetworkListSharedStoreData);
        this.mWifiConfigStore.registerStoreData(this.mNetworkListUserStoreData);
        this.mWifiConfigStore.registerStoreData(this.mDeletedEphemeralSsidsStoreData);
        this.mWifiConfigStore.registerStoreData(this.mRandomizedMacStoreData);
        this.mOnlyLinkSameCredentialConfigurations = this.mContext.getResources().getBoolean(17891608);
        this.mMaxNumActiveChannelsForPartialScans = this.mContext.getResources().getInteger(17694986);
        this.mFrameworkFacade = frameworkFacade;
        this.mFrameworkFacade.registerContentObserver(this.mContext, Settings.Global.getUriFor("wifi_pno_frequency_culling_enabled"), false, new ContentObserver(new Handler(looper)) {
            /* class com.android.server.wifi.WifiConfigManager.C04252 */

            public void onChange(boolean selfChange) {
                WifiConfigManager.this.updatePnoFrequencyCullingSetting();
            }
        });
        updatePnoFrequencyCullingSetting();
        this.mFrameworkFacade.registerContentObserver(this.mContext, Settings.Global.getUriFor("wifi_pno_recency_sorting_enabled"), false, new ContentObserver(new Handler(looper)) {
            /* class com.android.server.wifi.WifiConfigManager.C04263 */

            public void onChange(boolean selfChange) {
                WifiConfigManager.this.updatePnoRecencySortingSetting();
            }
        });
        updatePnoRecencySortingSetting();
        this.mNetworkAutoConnectEnabled = true;
        this.mAutoConnectCarrierApEnabled = true;
        try {
            this.mSystemUiUid = this.mContext.getPackageManager().getPackageUidAsUser(SYSUI_PACKAGE_NAME, 1048576, 0);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Unable to resolve SystemUI's UID.");
        }
        this.mWifiPolicy = EnterpriseDeviceManager.getInstance().getWifiPolicy();
    }

    @VisibleForTesting
    public static String createDebugTimeStampString(long wallClockMillis) {
        StringBuilder sb = new StringBuilder();
        sb.append("time=");
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(wallClockMillis);
        sb.append(String.format("%tm-%td %tH:%tM:%tS.%tL", c, c, c, c, c, c));
        return sb.toString();
    }

    /* access modifiers changed from: protected */
    @VisibleForTesting
    public int getRandomizedMacAddressMappingSize() {
        return this.mRandomizedMacAddressMapping.size();
    }

    private MacAddress getPersistentMacAddress(WifiConfiguration config) {
        String persistentMacString = this.mRandomizedMacAddressMapping.get(config.getSsidAndSecurityTypeString());
        if (persistentMacString != null) {
            try {
                return MacAddress.fromString(persistentMacString);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Error creating randomized MAC address from stored value.");
                this.mRandomizedMacAddressMapping.remove(config.getSsidAndSecurityTypeString());
            }
        }
        MacAddress result = WifiConfigurationUtil.calculatePersistentMacForConfiguration(config, WifiConfigurationUtil.obtainMacRandHashFunction(1010));
        if (result == null) {
            result = WifiConfigurationUtil.calculatePersistentMacForConfiguration(config, WifiConfigurationUtil.obtainMacRandHashFunction(1010));
        }
        if (result != null) {
            return result;
        }
        Log.wtf(TAG, "Failed to generate MAC address from KeyStore even after retrying. Using locally generated MAC address instead.");
        return MacAddress.createRandomUnicastAddress();
    }

    private MacAddress setRandomizedMacToPersistentMac(WifiConfiguration config) {
        MacAddress persistentMac = getPersistentMacAddress(config);
        if (persistentMac == null || persistentMac.equals(config.getRandomizedMacAddress())) {
            return persistentMac;
        }
        getInternalConfiguredNetwork(config.networkId).setRandomizedMacAddress(persistentMac);
        return persistentMac;
    }

    public void enableVerboseLogging(int verbose) {
        if (verbose > 0) {
            this.mVerboseLoggingEnabled = true;
        } else {
            this.mVerboseLoggingEnabled = false;
        }
        this.mWifiConfigStore.enableVerboseLogging(this.mVerboseLoggingEnabled);
        this.mWifiKeyStore.enableVerboseLogging(this.mVerboseLoggingEnabled);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void updatePnoFrequencyCullingSetting() {
        boolean z = true;
        if (this.mFrameworkFacade.getIntegerSetting(this.mContext, "wifi_pno_frequency_culling_enabled", 1) != 1) {
            z = false;
        }
        this.mPnoFrequencyCullingEnabled = z;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void updatePnoRecencySortingSetting() {
        boolean z = true;
        if (this.mFrameworkFacade.getIntegerSetting(this.mContext, "wifi_pno_recency_sorting_enabled", 1) != 1) {
            z = false;
        }
        this.mPnoRecencySortingEnabled = z;
    }

    private void maskPasswordsInWifiConfiguration(WifiConfiguration configuration) {
        if (!TextUtils.isEmpty(configuration.preSharedKey)) {
            configuration.preSharedKey = "*";
        }
        if (configuration.wepKeys != null) {
            for (int i = 0; i < configuration.wepKeys.length; i++) {
                if (!TextUtils.isEmpty(configuration.wepKeys[i])) {
                    configuration.wepKeys[i] = "*";
                }
            }
        }
        if (!TextUtils.isEmpty(configuration.enterpriseConfig.getPassword())) {
            configuration.enterpriseConfig.setPassword("*");
        }
    }

    private void maskRandomizedMacAddressInWifiConfiguration(WifiConfiguration configuration) {
        configuration.setRandomizedMacAddress(DEFAULT_MAC_ADDRESS);
    }

    private WifiConfiguration createExternalWifiConfiguration(WifiConfiguration configuration, boolean maskPasswords, int targetUid) {
        WifiConfiguration network = new WifiConfiguration(configuration);
        if (maskPasswords) {
            maskPasswordsInWifiConfiguration(network);
        }
        if (!(targetUid == 1010 || targetUid == 1000 || targetUid == configuration.creatorUid)) {
            maskRandomizedMacAddressInWifiConfiguration(network);
        }
        return network;
    }

    private List<WifiConfiguration> getConfiguredNetworks(boolean savedOnly, boolean maskPasswords, int targetUid) {
        List<WifiConfiguration> networks = new ArrayList<>();
        for (WifiConfiguration config : getInternalConfiguredNetworks()) {
            if (!savedOnly || (!config.ephemeral && !config.isPasspoint())) {
                if (savedOnly || maskPasswords || !config.semSamsungSpecificFlags.get(6)) {
                    networks.add(createExternalWifiConfiguration(config, maskPasswords, targetUid));
                }
            }
        }
        if (networks.size() > CSC_DEFAULT_MAX_NETWORKS_FOR_CURRENT_USER) {
            cleanOldNetworks(networks);
        }
        return networks;
    }

    public List<WifiConfiguration> getConfiguredNetworks() {
        return getConfiguredNetworks(false, true, 1010);
    }

    public List<WifiConfiguration> getConfiguredNetworksWithPasswords() {
        return getConfiguredNetworks(false, false, 1010);
    }

    public List<WifiConfiguration> getSavedNetworks(int targetUid) {
        return getConfiguredNetworks(true, true, targetUid);
    }

    public WifiConfiguration getConfiguredNetwork(int networkId) {
        WifiConfiguration config = getInternalConfiguredNetwork(networkId);
        if (config == null) {
            return null;
        }
        return createExternalWifiConfiguration(config, true, 1010);
    }

    public WifiConfiguration getConfiguredNetwork(String configKey) {
        WifiConfiguration config = getInternalConfiguredNetwork(configKey);
        if (config == null) {
            return null;
        }
        return createExternalWifiConfiguration(config, true, 1010);
    }

    public WifiConfiguration getConfiguredNetworkWithPassword(int networkId) {
        WifiConfiguration config = getInternalConfiguredNetwork(networkId);
        if (config == null) {
            return null;
        }
        return createExternalWifiConfiguration(config, false, 1010);
    }

    public WifiConfiguration getConfiguredNetworkWithoutMasking(int networkId) {
        WifiConfiguration config = getInternalConfiguredNetwork(networkId);
        if (config == null) {
            return null;
        }
        return new WifiConfiguration(config);
    }

    private Collection<WifiConfiguration> getInternalConfiguredNetworks() {
        return this.mConfiguredNetworks.valuesForCurrentUser();
    }

    private WifiConfiguration getInternalConfiguredNetwork(WifiConfiguration config) {
        WifiConfiguration internalConfig = this.mConfiguredNetworks.getForCurrentUser(config.networkId);
        if (internalConfig != null) {
            return internalConfig;
        }
        WifiConfiguration internalConfig2 = this.mConfiguredNetworks.getByConfigKeyForCurrentUser(config.configKey());
        if (internalConfig2 == null) {
            Log.e(TAG, "Cannot find network with networkId " + config.networkId + " or configKey " + config.configKey());
        }
        return internalConfig2;
    }

    private WifiConfiguration getInternalConfiguredNetwork(int networkId) {
        if (networkId == -1) {
            return null;
        }
        WifiConfiguration internalConfig = this.mConfiguredNetworks.getForCurrentUser(networkId);
        if (internalConfig == null) {
            Log.e(TAG, "Cannot find network with networkId " + networkId);
        }
        return internalConfig;
    }

    private WifiConfiguration getInternalConfiguredNetwork(String configKey) {
        WifiConfiguration internalConfig = this.mConfiguredNetworks.getByConfigKeyForCurrentUser(configKey);
        if (internalConfig == null) {
            Log.e(TAG, "Cannot find network with configKey " + configKey);
        }
        return internalConfig;
    }

    private void sendConfiguredNetworkChangedBroadcast(WifiConfiguration network, int reason) {
        Intent intent = new Intent("android.net.wifi.CONFIGURED_NETWORKS_CHANGE");
        intent.addFlags(67108864);
        intent.putExtra("multipleChanges", false);
        WifiConfiguration broadcastNetwork = new WifiConfiguration(network);
        maskPasswordsInWifiConfiguration(broadcastNetwork);
        intent.putExtra("wifiConfiguration", broadcastNetwork);
        intent.putExtra("changeReason", reason);
        this.mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
    }

    private void sendConfiguredNetworksChangedBroadcast() {
        Intent intent = new Intent("android.net.wifi.CONFIGURED_NETWORKS_CHANGE");
        intent.addFlags(67108864);
        intent.putExtra("multipleChanges", true);
        this.mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
    }

    private void sendLoadInternalDataCompleteBroadcast(boolean isPasspoint) {
        Intent intent = new Intent("com.samsung.android.net.wifi.LOAD_INTERNAL_DATA_COMPLETE");
        intent.addFlags(67108864);
        intent.putExtra("passpointConfiguration", isPasspoint);
        this.mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
    }

    private boolean canModifyNetwork(WifiConfiguration config, int uid) {
        if (uid == 1000) {
            return true;
        }
        if (config.isPasspoint() && uid == 1010) {
            return true;
        }
        if (config.enterpriseConfig != null && uid == 1010 && TelephonyUtil.isSimConfig(config)) {
            return true;
        }
        DevicePolicyManagerInternal dpmi = this.mWifiPermissionsWrapper.getDevicePolicyManagerInternal();
        if (dpmi != null && dpmi.isActiveAdminWithPolicy(uid, -2)) {
            return true;
        }
        boolean isCreator = config.creatorUid == uid;
        if (!this.mContext.getPackageManager().hasSystemFeature("android.software.device_admin") || dpmi != null) {
            if (dpmi != null && dpmi.isActiveAdminWithPolicy(config.creatorUid, -2)) {
                if ((Settings.Global.getInt(this.mContext.getContentResolver(), "wifi_device_owner_configs_lockdown", 0) != 0) || !this.mWifiPermissionsUtil.checkNetworkSettingsPermission(uid)) {
                    return false;
                }
                return true;
            } else if (isCreator || this.mWifiPermissionsUtil.checkNetworkSettingsPermission(uid)) {
                return true;
            } else {
                return false;
            }
        } else {
            Log.w(TAG, "Error retrieving DPMI service.");
            return false;
        }
    }

    private boolean doesUidBelongToCurrentUser(int uid) {
        if (uid == 1000 || uid == this.mSystemUiUid) {
            return true;
        }
        return WifiConfigurationUtil.doesUidBelongToAnyProfile(uid, this.mUserManager.getProfiles(this.mCurrentUserId));
    }

    private void mergeWithInternalWifiConfiguration(WifiConfiguration internalConfig, WifiConfiguration externalConfig) {
        ProxyInfo proxyInfo;
        if (externalConfig.SSID != null) {
            internalConfig.SSID = externalConfig.SSID;
        }
        if (externalConfig.BSSID != null) {
            if (externalConfig.BSSID.isEmpty()) {
                internalConfig.BSSID = null;
            } else {
                internalConfig.BSSID = externalConfig.BSSID.toLowerCase();
            }
        }
        internalConfig.hiddenSSID = externalConfig.hiddenSSID;
        internalConfig.requirePMF = externalConfig.requirePMF;
        if (externalConfig.preSharedKey != null && !externalConfig.preSharedKey.equals("*")) {
            internalConfig.preSharedKey = externalConfig.preSharedKey;
        }
        if (externalConfig.wepKeys != null) {
            boolean hasWepKey = false;
            for (int i = 0; i < internalConfig.wepKeys.length; i++) {
                if (externalConfig.wepKeys[i] != null && !externalConfig.wepKeys[i].equals("*")) {
                    internalConfig.wepKeys[i] = externalConfig.wepKeys[i];
                    hasWepKey = true;
                }
            }
            if (hasWepKey) {
                internalConfig.wepTxKeyIndex = externalConfig.wepTxKeyIndex;
            }
        }
        if (externalConfig.FQDN != null) {
            internalConfig.FQDN = externalConfig.FQDN;
        }
        if (externalConfig.providerFriendlyName != null) {
            internalConfig.providerFriendlyName = externalConfig.providerFriendlyName;
        }
        if (externalConfig.roamingConsortiumIds != null) {
            internalConfig.roamingConsortiumIds = (long[]) externalConfig.roamingConsortiumIds.clone();
        }
        internalConfig.isHomeProviderNetwork = externalConfig.isHomeProviderNetwork;
        internalConfig.priority = externalConfig.priority;
        if (externalConfig.allowedAuthAlgorithms != null && !externalConfig.allowedAuthAlgorithms.isEmpty()) {
            internalConfig.allowedAuthAlgorithms = (BitSet) externalConfig.allowedAuthAlgorithms.clone();
        }
        if (externalConfig.allowedProtocols != null && !externalConfig.allowedProtocols.isEmpty()) {
            internalConfig.allowedProtocols = (BitSet) externalConfig.allowedProtocols.clone();
        }
        if (externalConfig.allowedKeyManagement != null && !externalConfig.allowedKeyManagement.isEmpty()) {
            internalConfig.allowedKeyManagement = (BitSet) externalConfig.allowedKeyManagement.clone();
        }
        if (externalConfig.allowedPairwiseCiphers != null && !externalConfig.allowedPairwiseCiphers.isEmpty()) {
            internalConfig.allowedPairwiseCiphers = (BitSet) externalConfig.allowedPairwiseCiphers.clone();
        }
        if (externalConfig.allowedGroupCiphers != null && !externalConfig.allowedGroupCiphers.isEmpty()) {
            internalConfig.allowedGroupCiphers = (BitSet) externalConfig.allowedGroupCiphers.clone();
        }
        if (externalConfig.allowedGroupManagementCiphers != null && !externalConfig.allowedGroupManagementCiphers.isEmpty()) {
            internalConfig.allowedGroupManagementCiphers = (BitSet) externalConfig.allowedGroupManagementCiphers.clone();
        }
        if (externalConfig.semSamsungSpecificFlags != null) {
            internalConfig.semSamsungSpecificFlags = (BitSet) externalConfig.semSamsungSpecificFlags.clone();
        }
        if (!(externalConfig.bssidWhitelist == null || externalConfig.bssidWhitelist.size() == 0)) {
            if (internalConfig.bssidWhitelist.size() == 0) {
                internalConfig.bssidWhitelist = new WifiConfiguration.BssidWhitelist(externalConfig.bssidWhitelist);
            } else {
                for (Map.Entry<String, Long> entry : externalConfig.bssidWhitelist.entrySet()) {
                    internalConfig.bssidWhitelist.put(entry.getKey(), entry.getValue());
                }
            }
        }
        if (externalConfig.getIpConfiguration() != null) {
            IpConfiguration.IpAssignment ipAssignment = externalConfig.getIpAssignment();
            if (ipAssignment != IpConfiguration.IpAssignment.UNASSIGNED) {
                internalConfig.setIpAssignment(ipAssignment);
                if (ipAssignment == IpConfiguration.IpAssignment.STATIC) {
                    internalConfig.setStaticIpConfiguration(new StaticIpConfiguration(externalConfig.getStaticIpConfiguration()));
                }
            }
            ProxyInfo proxySettings = externalConfig.getProxySettings();
            if (proxySettings != IpConfiguration.ProxySettings.UNASSIGNED) {
                internalConfig.setProxySettings(proxySettings);
                if (proxySettings == IpConfiguration.ProxySettings.PAC) {
                    ProxyInfo proxyInfo2 = externalConfig.getHttpProxy();
                    if (proxyInfo2 != null && !Uri.EMPTY.equals(proxyInfo2.getPacFileUrl())) {
                        internalConfig.setHttpProxy(new ProxyInfo(proxyInfo2));
                    }
                } else if (proxySettings == IpConfiguration.ProxySettings.STATIC && (proxyInfo = externalConfig.getHttpProxy()) != null && proxyInfo.isValid()) {
                    internalConfig.setHttpProxy(new ProxyInfo(proxyInfo));
                }
            }
        }
        internalConfig.wapiPskType = externalConfig.wapiPskType;
        internalConfig.wapiCertIndex = externalConfig.wapiCertIndex;
        if (externalConfig.wapiAsCert != null) {
            internalConfig.wapiAsCert = externalConfig.wapiAsCert;
        }
        if (externalConfig.wapiUserCert != null) {
            internalConfig.wapiUserCert = externalConfig.wapiUserCert;
        }
        if (externalConfig.enterpriseConfig != null) {
            internalConfig.enterpriseConfig.copyFromExternal(externalConfig.enterpriseConfig, "*");
        }
        internalConfig.semIsVendorSpecificSsid = externalConfig.semIsVendorSpecificSsid;
        internalConfig.semAutoWifiScore = externalConfig.semAutoWifiScore;
        internalConfig.semAutoReconnect = externalConfig.semAutoReconnect;
        internalConfig.meteredHint = externalConfig.meteredHint;
        internalConfig.meteredOverride = externalConfig.meteredOverride;
        internalConfig.semMhsUserName = externalConfig.semMhsUserName;
        internalConfig.entryRssi24GHz = externalConfig.entryRssi24GHz;
        internalConfig.entryRssi5GHz = externalConfig.entryRssi5GHz;
        internalConfig.macRandomizationSetting = externalConfig.macRandomizationSetting;
    }

    private void setDefaultsInWifiConfiguration(WifiConfiguration configuration) {
        configuration.allowedAuthAlgorithms.set(0);
        configuration.allowedProtocols.set(1);
        configuration.allowedProtocols.set(0);
        configuration.allowedKeyManagement.set(1);
        configuration.allowedKeyManagement.set(2);
        configuration.allowedPairwiseCiphers.set(2);
        configuration.allowedPairwiseCiphers.set(1);
        configuration.allowedGroupCiphers.set(3);
        configuration.allowedGroupCiphers.set(2);
        configuration.allowedGroupCiphers.set(0);
        configuration.allowedGroupCiphers.set(1);
        configuration.setIpAssignment(IpConfiguration.IpAssignment.DHCP);
        configuration.setProxySettings(IpConfiguration.ProxySettings.NONE);
        configuration.status = 1;
        configuration.getNetworkSelectionStatus().setNetworkSelectionStatus(2);
        configuration.getNetworkSelectionStatus().setNetworkSelectionDisableReason(11);
    }

    private WifiConfiguration createNewInternalWifiConfigurationFromExternal(WifiConfiguration externalConfig, int uid, String packageName) {
        WifiConfiguration newInternalConfig = new WifiConfiguration();
        int i = this.mNextNetworkId;
        this.mNextNetworkId = i + 1;
        newInternalConfig.networkId = i;
        setDefaultsInWifiConfiguration(newInternalConfig);
        mergeWithInternalWifiConfiguration(newInternalConfig, externalConfig);
        newInternalConfig.requirePMF = externalConfig.requirePMF;
        newInternalConfig.noInternetAccessExpected = externalConfig.noInternetAccessExpected;
        newInternalConfig.ephemeral = externalConfig.ephemeral;
        newInternalConfig.osu = externalConfig.osu;
        newInternalConfig.trusted = externalConfig.trusted;
        newInternalConfig.fromWifiNetworkSuggestion = externalConfig.fromWifiNetworkSuggestion;
        newInternalConfig.fromWifiNetworkSpecifier = externalConfig.fromWifiNetworkSpecifier;
        newInternalConfig.useExternalScores = externalConfig.useExternalScores;
        newInternalConfig.shared = externalConfig.shared;
        newInternalConfig.updateIdentifier = externalConfig.updateIdentifier;
        newInternalConfig.lastUpdateUid = uid;
        newInternalConfig.creatorUid = uid;
        String nameForUid = packageName != null ? packageName : this.mContext.getPackageManager().getNameForUid(uid);
        newInternalConfig.lastUpdateName = nameForUid;
        newInternalConfig.creatorName = nameForUid;
        String createDebugTimeStampString = createDebugTimeStampString(this.mClock.getWallClockMillis());
        newInternalConfig.updateTime = createDebugTimeStampString;
        newInternalConfig.creationTime = createDebugTimeStampString;
        long wallClockMillis = this.mClock.getWallClockMillis();
        newInternalConfig.semUpdateTime = wallClockMillis;
        newInternalConfig.semCreationTime = wallClockMillis;
        MacAddress randomizedMac = getPersistentMacAddress(newInternalConfig);
        if (randomizedMac != null) {
            newInternalConfig.setRandomizedMacAddress(randomizedMac);
        }
        return newInternalConfig;
    }

    private WifiConfiguration updateExistingInternalWifiConfigurationFromExternal(WifiConfiguration internalConfig, WifiConfiguration externalConfig, int uid, String packageName) {
        WifiConfiguration newInternalConfig = new WifiConfiguration(internalConfig);
        mergeWithInternalWifiConfiguration(newInternalConfig, externalConfig);
        newInternalConfig.lastUpdateUid = uid;
        newInternalConfig.lastUpdateName = packageName != null ? packageName : this.mContext.getPackageManager().getNameForUid(uid);
        newInternalConfig.updateTime = createDebugTimeStampString(this.mClock.getWallClockMillis());
        newInternalConfig.semUpdateTime = this.mClock.getWallClockMillis();
        return newInternalConfig;
    }

    private NetworkUpdateResult addOrUpdateNetworkInternal(WifiConfiguration config, int uid, String packageName) {
        return addOrUpdateNetworkInternal(config, uid, 0, packageName);
    }

    /* JADX WARNING: Removed duplicated region for block: B:158:0x035c A[SYNTHETIC, Splitter:B:158:0x035c] */
    /* JADX WARNING: Removed duplicated region for block: B:166:0x0388 A[SYNTHETIC, Splitter:B:166:0x0388] */
    /* JADX WARNING: Removed duplicated region for block: B:175:0x03ab A[SYNTHETIC, Splitter:B:175:0x03ab] */
    private NetworkUpdateResult addOrUpdateNetworkInternal(WifiConfiguration config, int uid, int from, String packageName) {
        boolean newNetwork;
        boolean hasIpChanged;
        boolean hasProxyChanged;
        boolean hasCredentialChanged;
        Throwable th;
        StringBuilder sb;
        IOException e2;
        String[] selectionArgs;
        if (this.mVerboseLoggingEnabled) {
            Log.v(TAG, "Adding/Updating network " + config.getPrintableSsid());
        }
        if (from != -1000 && config.networkId == -1) {
            if (config.semSamsungSpecificFlags.get(4)) {
                selectionArgs = new String[]{"false"};
            } else {
                selectionArgs = new String[]{"true"};
            }
            if (WifiMobileDeviceManager.getEnterprisePolicyEnabled(this.mContext, "content://com.sec.knox.provider2/WifiPolicy", "getAllowUserProfiles", selectionArgs) == 0) {
                Log.v(TAG, "not allowed to add new networks By MDM");
                return new NetworkUpdateResult(-1);
            }
        }
        WifiConfiguration newInternalConfig = null;
        WifiConfiguration existingInternalConfig = getInternalConfiguredNetwork(config);
        if (existingInternalConfig == null) {
            if (!WifiConfigurationUtil.validate(config, true)) {
                Log.e(TAG, "Cannot add network with invalid config");
                return new NetworkUpdateResult(-1);
            }
            newInternalConfig = createNewInternalWifiConfigurationFromExternal(config, uid, packageName);
            existingInternalConfig = getInternalConfiguredNetwork(newInternalConfig.configKey());
        }
        if (existingInternalConfig != null) {
            if (!WifiConfigurationUtil.validate(config, false)) {
                Log.e(TAG, "Cannot update network with invalid config");
                return new NetworkUpdateResult(-1);
            } else if (!canModifyNetwork(existingInternalConfig, uid)) {
                Log.e(TAG, "UID " + uid + " does not have permission to update configuration " + config.configKey());
                return new NetworkUpdateResult(-1);
            } else {
                if (from == -1000 && !existingInternalConfig.semSamsungSpecificFlags.get(4)) {
                    existingInternalConfig.semSamsungSpecificFlags.set(4);
                } else if (from != -1000) {
                    WifiConfiguration existingInternalConfig2 = canAddOrUpdateConfig(existingInternalConfig, uid);
                    if (existingInternalConfig2 == null) {
                        Log.e(TAG, "MDM restriction doesn't allow UID " + uid + " update configuration " + config.configKey());
                        return new NetworkUpdateResult(-1);
                    }
                    existingInternalConfig = existingInternalConfig2;
                }
                if (!canModifyEntryRssi(existingInternalConfig)) {
                    existingInternalConfig.entryRssi24GHz = -78;
                    existingInternalConfig.entryRssi5GHz = -75;
                }
                newInternalConfig = updateExistingInternalWifiConfigurationFromExternal(existingInternalConfig, config, uid, packageName);
            }
        }
        if (WifiConfigurationUtil.hasProxyChanged(existingInternalConfig, newInternalConfig) && !canModifyProxySettings(uid)) {
            Log.e(TAG, "UID " + uid + " does not have permission to modify proxy Settings " + config.configKey() + ". Must have NETWORK_SETTINGS, or be device or profile owner.");
            return new NetworkUpdateResult(-1);
        } else if (WifiConfigurationUtil.hasMacRandomizationSettingsChanged(existingInternalConfig, newInternalConfig) && !this.mWifiPermissionsUtil.checkNetworkSettingsPermission(uid) && !this.mWifiPermissionsUtil.checkNetworkSetupWizardPermission(uid) && !this.mWifiPermissionsUtil.checkFactoryTestPermission(uid)) {
            Log.e(TAG, "UID " + uid + " does not have permission to modify MAC randomization Settings " + config.getSsidAndSecurityTypeString() + ". Must have NETWORK_SETTINGS or NETWORK_SETUP_WIZARD.");
            return new NetworkUpdateResult(-1);
        } else if (!config.isPasspoint() && !config.fromWifiNetworkSuggestion && config.isEnterprise() && !this.mWifiKeyStore.updateNetworkKeys(newInternalConfig, existingInternalConfig)) {
            return new NetworkUpdateResult(-1);
        } else {
            newNetwork = existingInternalConfig == null;
            hasIpChanged = newNetwork || WifiConfigurationUtil.hasIpChanged(existingInternalConfig, newInternalConfig);
            hasProxyChanged = newNetwork || WifiConfigurationUtil.hasProxyChanged(existingInternalConfig, newInternalConfig);
            hasCredentialChanged = newNetwork || WifiConfigurationUtil.hasCredentialChanged(existingInternalConfig, newInternalConfig);
            if (hasCredentialChanged) {
                newInternalConfig.getNetworkSelectionStatus().setHasEverConnected(false);
            }
            if (mRemovableDefaultAp && this.mFilePathRemovedNwInfo.exists() && isRemovedVedorAp(newInternalConfig)) {
                deleteRemovedVedorAp(newInternalConfig);
                newInternalConfig.semIsVendorSpecificSsid = true;
            }
            if (newInternalConfig.semIsVendorSpecificSsid) {
                this.mIsCarrierNetworkSaved = true;
            }
            if (!newNetwork && existingInternalConfig.semIsVendorSpecificSsid && !existingInternalConfig.isPasspoint()) {
                Log.e(TAG, "existingInternalConfig [" + existingInternalConfig.networkId + " ] semIsVendorSpecificSsid? " + existingInternalConfig.semIsVendorSpecificSsid + " removable? " + mRemovableDefaultAp);
                if (mRemovableDefaultAp) {
                    String allowedKeyManagementString = StringUtil.makeString(existingInternalConfig.allowedKeyManagement, WifiConfiguration.KeyMgmt.strings);
                    String oldEapString = StringUtil.makeStringEapMethod(existingInternalConfig);
                    String newEapString = StringUtil.makeStringEapMethod(newInternalConfig);
                    Log.d(TAG, "old eapmethod:" + oldEapString + " , new eapMethod:" + newEapString);
                    if (oldEapString != null && !oldEapString.equals(newEapString)) {
                        FileWriter fw = null;
                        try {
                            if (!this.mFilePathRemovedNwInfo.exists()) {
                                this.mFilePathRemovedNwInfo.createNewFile();
                                try {
                                } catch (FileNotFoundException e) {
                                    e = e;
                                    Log.e(TAG, "addOrUpdateNetworkInternal File not found " + e.getMessage());
                                    if (fw != null) {
                                        try {
                                            fw.close();
                                        } catch (IOException e3) {
                                            e2 = e3;
                                            sb = new StringBuilder();
                                        }
                                    }
                                    this.mConfiguredNetworks.put(newInternalConfig);
                                    Log.v(TAG, "Removed from ephemeral blacklist: " + config.SSID);
                                    this.mBackupManagerProxy.notifyDataChanged();
                                    NetworkUpdateResult result = new NetworkUpdateResult(hasIpChanged, hasProxyChanged, hasCredentialChanged);
                                    result.setIsNewNetwork(newNetwork);
                                    result.setNetworkId(newInternalConfig.networkId);
                                    localLog("addOrUpdateNetworkInternal: added/updated config. netId=" + newInternalConfig.networkId + " configKey=" + newInternalConfig.configKey() + " uid=" + Integer.toString(newInternalConfig.creatorUid) + " name=" + newInternalConfig.creatorName);
                                    return result;
                                } catch (IOException e4) {
                                    e2 = e4;
                                    try {
                                        Log.e(TAG, "addOrUpdateNetworkInternal IOException :" + e2.getMessage());
                                        if (fw != null) {
                                            try {
                                                fw.close();
                                            } catch (IOException e5) {
                                                e2 = e5;
                                                sb = new StringBuilder();
                                            }
                                        }
                                        this.mConfiguredNetworks.put(newInternalConfig);
                                        Log.v(TAG, "Removed from ephemeral blacklist: " + config.SSID);
                                        this.mBackupManagerProxy.notifyDataChanged();
                                        NetworkUpdateResult result2 = new NetworkUpdateResult(hasIpChanged, hasProxyChanged, hasCredentialChanged);
                                        result2.setIsNewNetwork(newNetwork);
                                        result2.setNetworkId(newInternalConfig.networkId);
                                        localLog("addOrUpdateNetworkInternal: added/updated config. netId=" + newInternalConfig.networkId + " configKey=" + newInternalConfig.configKey() + " uid=" + Integer.toString(newInternalConfig.creatorUid) + " name=" + newInternalConfig.creatorName);
                                        return result2;
                                    } catch (Throwable th2) {
                                        th = th2;
                                        if (fw != null) {
                                        }
                                        throw th;
                                    }
                                } catch (Throwable th3) {
                                    th = th3;
                                    if (fw != null) {
                                        try {
                                            fw.close();
                                        } catch (IOException e22) {
                                            Log.e(TAG, "fw.close IOException" + e22.getMessage());
                                        }
                                    }
                                    throw th;
                                }
                                try {
                                    fw = new FileWriter(this.mFilePathRemovedNwInfo, true);
                                    try {
                                        fw.write("version=1\n");
                                    } catch (FileNotFoundException e6) {
                                        e = e6;
                                    } catch (IOException e7) {
                                        e2 = e7;
                                        Log.e(TAG, "addOrUpdateNetworkInternal IOException :" + e2.getMessage());
                                        if (fw != null) {
                                        }
                                        this.mConfiguredNetworks.put(newInternalConfig);
                                        Log.v(TAG, "Removed from ephemeral blacklist: " + config.SSID);
                                        this.mBackupManagerProxy.notifyDataChanged();
                                        NetworkUpdateResult result22 = new NetworkUpdateResult(hasIpChanged, hasProxyChanged, hasCredentialChanged);
                                        result22.setIsNewNetwork(newNetwork);
                                        result22.setNetworkId(newInternalConfig.networkId);
                                        localLog("addOrUpdateNetworkInternal: added/updated config. netId=" + newInternalConfig.networkId + " configKey=" + newInternalConfig.configKey() + " uid=" + Integer.toString(newInternalConfig.creatorUid) + " name=" + newInternalConfig.creatorName);
                                        return result22;
                                    }
                                } catch (FileNotFoundException e8) {
                                    e = e8;
                                    fw = null;
                                    Log.e(TAG, "addOrUpdateNetworkInternal File not found " + e.getMessage());
                                    if (fw != null) {
                                    }
                                    this.mConfiguredNetworks.put(newInternalConfig);
                                    Log.v(TAG, "Removed from ephemeral blacklist: " + config.SSID);
                                    this.mBackupManagerProxy.notifyDataChanged();
                                    NetworkUpdateResult result222 = new NetworkUpdateResult(hasIpChanged, hasProxyChanged, hasCredentialChanged);
                                    result222.setIsNewNetwork(newNetwork);
                                    result222.setNetworkId(newInternalConfig.networkId);
                                    localLog("addOrUpdateNetworkInternal: added/updated config. netId=" + newInternalConfig.networkId + " configKey=" + newInternalConfig.configKey() + " uid=" + Integer.toString(newInternalConfig.creatorUid) + " name=" + newInternalConfig.creatorName);
                                    return result222;
                                } catch (IOException e9) {
                                    e2 = e9;
                                    fw = null;
                                    Log.e(TAG, "addOrUpdateNetworkInternal IOException :" + e2.getMessage());
                                    if (fw != null) {
                                    }
                                    this.mConfiguredNetworks.put(newInternalConfig);
                                    Log.v(TAG, "Removed from ephemeral blacklist: " + config.SSID);
                                    this.mBackupManagerProxy.notifyDataChanged();
                                    NetworkUpdateResult result2222 = new NetworkUpdateResult(hasIpChanged, hasProxyChanged, hasCredentialChanged);
                                    result2222.setIsNewNetwork(newNetwork);
                                    result2222.setNetworkId(newInternalConfig.networkId);
                                    localLog("addOrUpdateNetworkInternal: added/updated config. netId=" + newInternalConfig.networkId + " configKey=" + newInternalConfig.configKey() + " uid=" + Integer.toString(newInternalConfig.creatorUid) + " name=" + newInternalConfig.creatorName);
                                    return result2222;
                                } catch (Throwable th4) {
                                    th = th4;
                                    fw = null;
                                    if (fw != null) {
                                    }
                                    throw th;
                                }
                            } else {
                                fw = new FileWriter(this.mFilePathRemovedNwInfo, true);
                            }
                            fw.write("network={\n");
                            fw.write("    ssid=" + existingInternalConfig.SSID);
                            fw.write("\n    key_mgmt=" + allowedKeyManagementString);
                            fw.write("\n    eap=" + oldEapString);
                            fw.write("\n}\n");
                            fw.flush();
                            Log.d(TAG, "removed_nw.conf was created by modifyNetwork");
                            try {
                                fw.close();
                            } catch (IOException e23) {
                                sb = new StringBuilder();
                                sb.append("fw.close IOException");
                                sb.append(e23.getMessage());
                            }
                        } catch (FileNotFoundException e10) {
                            e = e10;
                            Log.e(TAG, "addOrUpdateNetworkInternal File not found " + e.getMessage());
                            if (fw != null) {
                            }
                            this.mConfiguredNetworks.put(newInternalConfig);
                            Log.v(TAG, "Removed from ephemeral blacklist: " + config.SSID);
                            this.mBackupManagerProxy.notifyDataChanged();
                            NetworkUpdateResult result22222 = new NetworkUpdateResult(hasIpChanged, hasProxyChanged, hasCredentialChanged);
                            result22222.setIsNewNetwork(newNetwork);
                            result22222.setNetworkId(newInternalConfig.networkId);
                            localLog("addOrUpdateNetworkInternal: added/updated config. netId=" + newInternalConfig.networkId + " configKey=" + newInternalConfig.configKey() + " uid=" + Integer.toString(newInternalConfig.creatorUid) + " name=" + newInternalConfig.creatorName);
                            return result22222;
                        } catch (IOException e11) {
                            e2 = e11;
                            Log.e(TAG, "addOrUpdateNetworkInternal IOException :" + e2.getMessage());
                            if (fw != null) {
                            }
                            this.mConfiguredNetworks.put(newInternalConfig);
                            Log.v(TAG, "Removed from ephemeral blacklist: " + config.SSID);
                            this.mBackupManagerProxy.notifyDataChanged();
                            NetworkUpdateResult result222222 = new NetworkUpdateResult(hasIpChanged, hasProxyChanged, hasCredentialChanged);
                            result222222.setIsNewNetwork(newNetwork);
                            result222222.setNetworkId(newInternalConfig.networkId);
                            localLog("addOrUpdateNetworkInternal: added/updated config. netId=" + newInternalConfig.networkId + " configKey=" + newInternalConfig.configKey() + " uid=" + Integer.toString(newInternalConfig.creatorUid) + " name=" + newInternalConfig.creatorName);
                            return result222222;
                        } catch (Throwable th5) {
                            th = th5;
                            if (fw != null) {
                            }
                            throw th;
                        }
                    }
                }
            }
            try {
                this.mConfiguredNetworks.put(newInternalConfig);
                if (this.mDeletedEphemeralSsidsToTimeMap.remove(config.SSID) != null && this.mVerboseLoggingEnabled) {
                    Log.v(TAG, "Removed from ephemeral blacklist: " + config.SSID);
                }
                this.mBackupManagerProxy.notifyDataChanged();
                NetworkUpdateResult result2222222 = new NetworkUpdateResult(hasIpChanged, hasProxyChanged, hasCredentialChanged);
                result2222222.setIsNewNetwork(newNetwork);
                result2222222.setNetworkId(newInternalConfig.networkId);
                localLog("addOrUpdateNetworkInternal: added/updated config. netId=" + newInternalConfig.networkId + " configKey=" + newInternalConfig.configKey() + " uid=" + Integer.toString(newInternalConfig.creatorUid) + " name=" + newInternalConfig.creatorName);
                return result2222222;
            } catch (IllegalArgumentException e12) {
                Log.e(TAG, "Failed to add network to config map", e12);
                return new NetworkUpdateResult(-1);
            }
        }
        sb.append("fw.close IOException");
        sb.append(e2.getMessage());
        Log.e(TAG, sb.toString());
        this.mConfiguredNetworks.put(newInternalConfig);
        Log.v(TAG, "Removed from ephemeral blacklist: " + config.SSID);
        this.mBackupManagerProxy.notifyDataChanged();
        NetworkUpdateResult result22222222 = new NetworkUpdateResult(hasIpChanged, hasProxyChanged, hasCredentialChanged);
        result22222222.setIsNewNetwork(newNetwork);
        result22222222.setNetworkId(newInternalConfig.networkId);
        localLog("addOrUpdateNetworkInternal: added/updated config. netId=" + newInternalConfig.networkId + " configKey=" + newInternalConfig.configKey() + " uid=" + Integer.toString(newInternalConfig.creatorUid) + " name=" + newInternalConfig.creatorName);
        return result22222222;
        Log.e(TAG, sb.toString());
        this.mConfiguredNetworks.put(newInternalConfig);
        Log.v(TAG, "Removed from ephemeral blacklist: " + config.SSID);
        this.mBackupManagerProxy.notifyDataChanged();
        NetworkUpdateResult result222222222 = new NetworkUpdateResult(hasIpChanged, hasProxyChanged, hasCredentialChanged);
        result222222222.setIsNewNetwork(newNetwork);
        result222222222.setNetworkId(newInternalConfig.networkId);
        localLog("addOrUpdateNetworkInternal: added/updated config. netId=" + newInternalConfig.networkId + " configKey=" + newInternalConfig.configKey() + " uid=" + Integer.toString(newInternalConfig.creatorUid) + " name=" + newInternalConfig.creatorName);
        return result222222222;
    }

    public NetworkUpdateResult addOrUpdateNetwork(WifiConfiguration config, int uid, String packageName) {
        return addOrUpdateNetwork(config, uid, 0, packageName);
    }

    public NetworkUpdateResult addOrUpdateNetwork(WifiConfiguration config, int uid, int from, String packageName) {
        int i;
        WifiConfiguration existingConfig;
        if (!doesUidBelongToCurrentUser(uid)) {
            Log.e(TAG, "UID " + uid + " not visible to the current user");
            return new NetworkUpdateResult(-1);
        } else if (config == null) {
            Log.e(TAG, "Cannot add/update network with null config");
            return new NetworkUpdateResult(-1);
        } else if (this.mPendingStoreRead) {
            Log.e(TAG, "Cannot add/update network before store is read!");
            return new NetworkUpdateResult(-1);
        } else {
            if (!config.isEphemeral() && (existingConfig = getConfiguredNetwork(config.configKey())) != null && existingConfig.isEphemeral()) {
                removeNetwork(existingConfig.networkId, this.mSystemUiUid);
            }
            NetworkUpdateResult result = addOrUpdateNetworkInternal(config, uid, from, packageName);
            if (!result.isSuccess()) {
                Log.e(TAG, "Failed to add/update network " + config.getPrintableSsid());
                return result;
            }
            WifiConfiguration newConfig = getInternalConfiguredNetwork(result.getNetworkId());
            if (result.isNewNetwork()) {
                i = 0;
            } else {
                i = 2;
            }
            sendConfiguredNetworkChangedBroadcast(newConfig, i);
            if (!config.ephemeral && !config.isPasspoint()) {
                saveToStore(true);
                if (this.mListener != null) {
                    if (result.isNewNetwork()) {
                        this.mListener.onSavedNetworkAdded(newConfig.networkId);
                    } else {
                        this.mListener.onSavedNetworkUpdated(newConfig.networkId);
                    }
                }
            }
            if (this.mConfiguredNetworks.sizeForAllUsers() > CSC_DEFAULT_MAX_NETWORKS_FOR_CURRENT_USER) {
                getConfiguredNetworks();
            }
            return result;
        }
    }

    public NetworkUpdateResult addOrUpdateNetwork(WifiConfiguration config, int uid) {
        return addOrUpdateNetwork(config, uid, null);
    }

    private boolean removeNetworkInternal(WifiConfiguration config, int uid) {
        StringBuilder sb;
        FileWriter fw;
        if (this.mVerboseLoggingEnabled) {
            Log.v(TAG, "Removing network " + config.getPrintableSsid());
        }
        if (!config.isPasspoint() && !config.fromWifiNetworkSuggestion && config.isEnterprise() && new CertificatePolicy().isUserRemoveCertificatesAllowedAsUser(0)) {
            this.mWifiKeyStore.removeKeys(config.enterpriseConfig);
        }
        this.mIsCarrierNetworkSaved = false;
        if (config.semIsVendorSpecificSsid && !config.isPasspoint()) {
            Log.e(TAG, "forgetNetwork [ " + config.networkId + " ] isVendorSpecificSsid? " + config.semIsVendorSpecificSsid + " removable? " + mRemovableDefaultAp);
            if (!mRemovableDefaultAp) {
                return false;
            }
            if (!isRemovedVedorAp(config)) {
                String allowedKeyManagementString = StringUtil.makeString(config.allowedKeyManagement, WifiConfiguration.KeyMgmt.strings);
                String eapString = StringUtil.makeStringEapMethod(config);
                FileWriter fw2 = null;
                try {
                    if (!this.mFilePathRemovedNwInfo.exists()) {
                        this.mFilePathRemovedNwInfo.createNewFile();
                        fw = new FileWriter(this.mFilePathRemovedNwInfo, true);
                        fw.write("version=1\n");
                    } else {
                        fw = new FileWriter(this.mFilePathRemovedNwInfo, true);
                    }
                    fw.write("network={\n");
                    fw.write("    ssid=" + config.SSID);
                    fw.write("\n    key_mgmt=" + allowedKeyManagementString);
                    fw.write("\n    eap=" + eapString);
                    fw.write("\n}\n");
                    fw.flush();
                    Log.d(TAG, "removed_nw.conf was created by removeNetwork");
                    try {
                        fw.close();
                    } catch (IOException e) {
                        e2 = e;
                        sb = new StringBuilder();
                    }
                } catch (FileNotFoundException e2) {
                    Log.e(TAG, "removeNetworkInternal File not found " + e2.getMessage());
                    if (0 != 0) {
                        try {
                            fw2.close();
                        } catch (IOException e3) {
                            e2 = e3;
                            sb = new StringBuilder();
                        }
                    }
                } catch (IOException e4) {
                    Log.e(TAG, "removeNetworkInternal IOException :" + e4.getMessage());
                    if (0 != 0) {
                        try {
                            fw2.close();
                        } catch (IOException e5) {
                            e2 = e5;
                            sb = new StringBuilder();
                        }
                    }
                } catch (Throwable th) {
                    if (0 != 0) {
                        try {
                            fw2.close();
                        } catch (IOException e22) {
                            Log.e(TAG, "fw.close IOException" + e22.getMessage());
                        }
                    }
                    throw th;
                }
            }
        }
        removeConnectChoiceFromAllNetworks(config.configKey());
        this.mConfiguredNetworks.remove(config.networkId);
        this.mScanDetailCaches.remove(Integer.valueOf(config.networkId));
        this.mBackupManagerProxy.notifyDataChanged();
        localLog("removeNetworkInternal: removed config. netId=" + config.networkId + " configKey=" + config.configKey() + " uid=" + Integer.toString(uid) + " name=" + this.mContext.getPackageManager().getNameForUid(uid) + " vendorAP=" + config.semIsVendorSpecificSsid + " hiddenSSID=" + config.hiddenSSID + " autoReconnect=" + config.semAutoReconnect);
        return true;
        sb.append("fw.close IOException");
        sb.append(e2.getMessage());
        Log.e(TAG, sb.toString());
        removeConnectChoiceFromAllNetworks(config.configKey());
        this.mConfiguredNetworks.remove(config.networkId);
        this.mScanDetailCaches.remove(Integer.valueOf(config.networkId));
        this.mBackupManagerProxy.notifyDataChanged();
        localLog("removeNetworkInternal: removed config. netId=" + config.networkId + " configKey=" + config.configKey() + " uid=" + Integer.toString(uid) + " name=" + this.mContext.getPackageManager().getNameForUid(uid) + " vendorAP=" + config.semIsVendorSpecificSsid + " hiddenSSID=" + config.hiddenSSID + " autoReconnect=" + config.semAutoReconnect);
        return true;
    }

    public boolean removeNetwork(int networkId, int uid) {
        return removeNetwork(networkId, uid, 0);
    }

    public boolean removeNetwork(int networkId, int uid, int from) {
        if (!doesUidBelongToCurrentUser(uid)) {
            Log.e(TAG, "UID " + uid + " not visible to the current user");
            return false;
        }
        WifiConfiguration config = getInternalConfiguredNetwork(networkId);
        if (config == null) {
            return false;
        }
        if (!canModifyNetwork(config, uid)) {
            Log.e(TAG, "UID " + uid + " does not have permission to delete configuration " + config.configKey());
            return false;
        } else if (from != -1000 && config.semSamsungSpecificFlags.get(4) && !canRemoveMDMNetwork(networkId)) {
            Log.e(TAG, "Config " + config.configKey() + " created by MDM not allowed to be removed");
            return false;
        } else if (!removeNetworkInternal(config, uid)) {
            Log.e(TAG, "Failed to remove network " + config.getPrintableSsid());
            return false;
        } else {
            if (networkId == this.mLastSelectedNetworkId) {
                clearLastSelectedNetwork();
            }
            sendConfiguredNetworkChangedBroadcast(config, 1);
            if (!config.ephemeral && !config.isPasspoint()) {
                saveToStore(true);
                OnSavedNetworkUpdateListener onSavedNetworkUpdateListener = this.mListener;
                if (onSavedNetworkUpdateListener != null) {
                    onSavedNetworkUpdateListener.onSavedNetworkRemoved(networkId);
                }
            }
            return true;
        }
    }

    /* access modifiers changed from: package-private */
    public boolean semRemoveNetwork(int netId) {
        if (this.mVerboseLoggingEnabled) {
            localLog("semRemoveNetwork");
        }
        WifiConfiguration config = this.mConfiguredNetworks.getForCurrentUser(netId);
        if (config == null) {
            return false;
        }
        Log.i(TAG, "semRemoveNetwork ID: " + netId + " config: " + config.configKey());
        if (!removeNetworkInternal(config, netId)) {
            Log.e(TAG, "Failed to remove network " + config.getPrintableSsid());
            return false;
        }
        String key = config.configKey();
        if (this.mVerboseLoggingEnabled) {
            Log.d(TAG, "removeNetwork  key=" + key + " config.id=" + config.networkId);
        }
        sendConfiguredNetworkChangedBroadcast(config, 1);
        return true;
    }

    private String getCreatorPackageName(WifiConfiguration config) {
        String creatorName = config.creatorName;
        if (!creatorName.contains(":")) {
            return creatorName;
        }
        return creatorName.substring(0, creatorName.indexOf(":"));
    }

    public Set<Integer> removeNetworksForApp(ApplicationInfo app) {
        if (app == null || app.packageName == null) {
            return Collections.emptySet();
        }
        Log.d(TAG, "Remove all networks for app " + app);
        Set<Integer> removedNetworks = new ArraySet<>();
        WifiConfiguration[] copiedConfigs = (WifiConfiguration[]) this.mConfiguredNetworks.valuesForAllUsers().toArray(new WifiConfiguration[0]);
        for (WifiConfiguration config : copiedConfigs) {
            if (app.uid == config.creatorUid && app.packageName.equals(getCreatorPackageName(config))) {
                localLog("Removing network " + config.SSID + ", application \"" + app.packageName + "\" uninstalled from user " + UserHandle.getUserId(app.uid));
                if (removeNetwork(config.networkId, this.mSystemUiUid)) {
                    removedNetworks.add(Integer.valueOf(config.networkId));
                }
            }
        }
        return removedNetworks;
    }

    /* access modifiers changed from: package-private */
    public Set<Integer> removeNetworksForUser(int userId) {
        Log.d(TAG, "Remove all networks for user " + userId);
        Set<Integer> removedNetworks = new ArraySet<>();
        WifiConfiguration[] copiedConfigs = (WifiConfiguration[]) this.mConfiguredNetworks.valuesForAllUsers().toArray(new WifiConfiguration[0]);
        for (WifiConfiguration config : copiedConfigs) {
            if (userId == UserHandle.getUserId(config.creatorUid)) {
                localLog("Removing network " + config.SSID + ", user " + userId + " removed");
                if (removeNetwork(config.networkId, this.mSystemUiUid)) {
                    removedNetworks.add(Integer.valueOf(config.networkId));
                }
            }
        }
        return removedNetworks;
    }

    public void removeFilesInDataMiscDirectory() {
        File shareDataMiscWifiDirectory = new File(Environment.getDataMiscDirectory(), STORE_DIRECTORY_NAME);
        localLog("removeFilesInDataMiscDirectory  : " + shareDataMiscWifiDirectory.getAbsolutePath());
        String[] list = shareDataMiscWifiDirectory.list(new FilenameFilter() {
            /* class com.android.server.wifi.WifiConfigManager.C04274 */

            public boolean accept(File dir, String filename) {
                if (filename.endsWith(".xml") || filename.endsWith(".conf") || filename.equals("networkHistory.txt") || filename.equals("ipconfig.txt") || filename.equals("hiddenAPs.txt") || filename.equals("message.txt") || filename.equals("default_ap.check") || filename.endsWith(".encrypted-checksum")) {
                    return true;
                }
                return false;
            }
        });
        if (list != null && list.length > 0) {
            for (String file : list) {
                StringBuffer stringBuffer = new StringBuffer(shareDataMiscWifiDirectory.getAbsolutePath());
                stringBuffer.append(File.separator);
                stringBuffer.append(file);
                String temp = stringBuffer.toString();
                if (temp.contains("default_ap.conf") || temp.contains("generalinfo_nw.conf") || temp.contains("cred.conf")) {
                    Log.d(TAG, "deleteWifiFiles, skip: " + temp);
                } else {
                    Log.d(TAG, "removeFilesInDataMiscDirectory, deleteFile: " + temp);
                    new File(temp).delete();
                }
            }
        }
        clearInternalData();
        this.mWifiConfigStore.stopBufferedWriteAlarm();
    }

    public void removeFilesInDataMiscCeDirectory() {
        File userDataMiscCeWifiDirectory = new File(Environment.getDataMiscCeDirectory(this.mCurrentUserId), STORE_DIRECTORY_NAME);
        localLog("removeFilesInDataMiscCeDirectory  : " + userDataMiscCeWifiDirectory.getAbsolutePath());
        String[] list = userDataMiscCeWifiDirectory.list(new FilenameFilter() {
            /* class com.android.server.wifi.WifiConfigManager.C04285 */

            public boolean accept(File dir, String filename) {
                if (filename.endsWith(".xml")) {
                    return true;
                }
                return false;
            }
        });
        if (list != null && list.length > 0) {
            for (String file : list) {
                StringBuffer stringBuffer = new StringBuffer(userDataMiscCeWifiDirectory.getAbsolutePath());
                stringBuffer.append(File.separator);
                stringBuffer.append(file);
                String temp = stringBuffer.toString();
                Log.d(TAG, "removeFilesInDataMiscCeDirectory, deleteFile: " + temp);
                new File(temp).delete();
            }
        }
        PasspointManager.clearInternalData();
    }

    public boolean removeAllEphemeralOrPasspointConfiguredNetworks() {
        if (this.mVerboseLoggingEnabled) {
            Log.v(TAG, "Removing all passpoint or ephemeral configured networks");
        }
        boolean didRemove = false;
        WifiConfiguration[] copiedConfigs = (WifiConfiguration[]) this.mConfiguredNetworks.valuesForAllUsers().toArray(new WifiConfiguration[0]);
        for (WifiConfiguration config : copiedConfigs) {
            if (config.isPasspoint()) {
                Log.d(TAG, "Removing passpoint network config " + config.configKey());
                removeNetwork(config.networkId, this.mSystemUiUid);
                didRemove = true;
            } else if (config.ephemeral) {
                Log.d(TAG, "Removing ephemeral network config " + config.configKey());
                removeNetwork(config.networkId, this.mSystemUiUid);
                didRemove = true;
            }
        }
        return didRemove;
    }

    public boolean removePasspointConfiguredNetwork(String fqdn) {
        WifiConfiguration[] copiedConfigs = (WifiConfiguration[]) this.mConfiguredNetworks.valuesForAllUsers().toArray(new WifiConfiguration[0]);
        for (WifiConfiguration config : copiedConfigs) {
            if (config.isPasspoint() && TextUtils.equals(fqdn, config.FQDN)) {
                Log.d(TAG, "Removing passpoint network config " + config.configKey());
                removeNetwork(config.networkId, this.mSystemUiUid);
                return true;
            }
        }
        return false;
    }

    private void setNetworkSelectionEnabled(WifiConfiguration config) {
        WifiConfiguration.NetworkSelectionStatus status = config.getNetworkSelectionStatus();
        status.setNetworkSelectionStatus(0);
        status.setDisableTime(-1);
        status.setNetworkSelectionDisableReason(0);
        status.clearDisableReasonCounter();
        OnSavedNetworkUpdateListener onSavedNetworkUpdateListener = this.mListener;
        if (onSavedNetworkUpdateListener != null) {
            onSavedNetworkUpdateListener.onSavedNetworkEnabled(config.networkId);
        }
    }

    private void setNetworkSelectionTemporarilyDisabled(WifiConfiguration config, int disableReason) {
        WifiConfiguration.NetworkSelectionStatus status = config.getNetworkSelectionStatus();
        status.setNetworkSelectionStatus(1);
        status.setDisableTime(this.mClock.getElapsedSinceBootMillis());
        status.setNetworkSelectionDisableReason(disableReason);
        OnSavedNetworkUpdateListener onSavedNetworkUpdateListener = this.mListener;
        if (onSavedNetworkUpdateListener != null) {
            onSavedNetworkUpdateListener.onSavedNetworkTemporarilyDisabled(config.networkId, disableReason);
        }
    }

    private void setNetworkSelectionPermanentlyDisabled(WifiConfiguration config, int disableReason) {
        WifiConfiguration.NetworkSelectionStatus status = config.getNetworkSelectionStatus();
        status.setNetworkSelectionStatus(2);
        status.setDisableTime(-1);
        status.setNetworkSelectionDisableReason(disableReason);
        OnSavedNetworkUpdateListener onSavedNetworkUpdateListener = this.mListener;
        if (onSavedNetworkUpdateListener != null) {
            onSavedNetworkUpdateListener.onSavedNetworkPermanentlyDisabled(config.networkId, disableReason);
        }
    }

    private void setNetworkStatus(WifiConfiguration config, int status) {
        config.status = status;
        sendConfiguredNetworkChangedBroadcast(config, 2);
    }

    private boolean setNetworkSelectionStatus(WifiConfiguration config, int reason) {
        WifiConfiguration.NetworkSelectionStatus networkStatus = config.getNetworkSelectionStatus();
        if (reason < 0 || reason >= 21) {
            Log.e(TAG, "Invalid Network disable reason " + reason);
            return false;
        }
        if (reason == 0) {
            if (!WifiPolicyCache.getInstance(this.mContext).isNetworkAllowed(config, true)) {
                Log.i(TAG, "This AP is blocked by MDM  " + config.configKey());
                setNetworkSelectionPermanentlyDisabled(config, 18);
                setNetworkStatus(config, 1);
                Context context = this.mContext;
                WifiMobileDeviceManager.auditLog(context, 3, false, TAG, "Enabling AP is blocked by Administrator. SSID: " + config.SSID);
            } else {
                setNetworkSelectionEnabled(config);
                setNetworkStatus(config, 2);
            }
        } else if (reason < 8) {
            setNetworkSelectionTemporarilyDisabled(config, reason);
        } else if (reason == 13 || reason == 20) {
            setNetworkSelectionTemporarilyDisabled(config, reason);
            setNetworkStatus(config, 1);
        } else if (reason == 16) {
            setNetworkSelectionTemporarilyDisabled(config, reason);
        } else if (reason == 17) {
            setNetworkSelectionTemporarilyDisabled(config, reason);
        } else {
            setNetworkSelectionPermanentlyDisabled(config, reason);
            setNetworkStatus(config, 1);
        }
        localLog("setNetworkSelectionStatus: configKey=" + config.configKey() + " networkStatus=" + networkStatus.getNetworkStatusString() + " disableReason=" + networkStatus.getNetworkDisableReasonString() + " at=" + createDebugTimeStampString(this.mClock.getWallClockMillis()));
        saveToStore(false);
        return true;
    }

    private boolean updateNetworkSelectionStatus(WifiConfiguration config, int reason) {
        WifiB2BConfigurationPolicy.B2BConfiguration b2bConfig;
        WifiConfiguration.NetworkSelectionStatus networkStatus = config.getNetworkSelectionStatus();
        if (reason != 0) {
            if (reason == 11 || reason == 18 || (b2bConfig = this.mWifiInjector.getWifiB2bConfigPolicy().getConfiguration(config.getPrintableSsid())) == null || !b2bConfig.skipAddingDisableNetwork()) {
                networkStatus.incrementDisableReasonCounter(reason);
                int disableReasonCounter = networkStatus.getDisableReasonCounter(reason);
                int disableReasonThreshold = NETWORK_SELECTION_DISABLE_THRESHOLD[reason];
                if (this.mUserSelectNetwork && reason == 4) {
                    Log.d(TAG, "DISABLE_THRESHOLD of DHCP failure is set to 1 for userselectnetwork connection");
                    disableReasonThreshold = 1;
                }
                boolean hasEverConnected = networkStatus.getHasEverConnected();
                if (disableReasonCounter < disableReasonThreshold) {
                    if (this.mVerboseLoggingEnabled) {
                        Log.v(TAG, "Disable counter for network " + config.getPrintableSsid() + " for reason " + WifiConfiguration.NetworkSelectionStatus.getNetworkDisableReasonString(reason) + " is " + networkStatus.getDisableReasonCounter(reason) + " and threshold is " + disableReasonThreshold);
                    }
                    if (reason == 2 || reason == 3 || reason == 4 || reason == 5) {
                        if (!RilUtil.isWifiOnly(this.mContext) && !SemSarManager.isRfTestMode()) {
                            increaseEntryRssi(config);
                        }
                        if (hasEverConnected) {
                            return true;
                        }
                        SemWifiFrameworkUxUtils.sendShowInfoIntentToSettings(this.mContext, 70, config.networkId, reason);
                        return true;
                    } else if (reason != 6 && reason != 17) {
                        return true;
                    } else {
                        ScanDetailCache scanDetailCache = getScanDetailCacheForNetwork(config.networkId);
                        if (scanDetailCache != null) {
                            String networkSelectionBSSID = config.getNetworkSelectionStatus().getNetworkSelectionBSSID();
                            ScanResult scanResult = scanDetailCache.getScanResult(networkSelectionBSSID);
                            if (scanResult == null) {
                                Log.v(TAG, "updateNetworkSelectionStatus: no scanResult for " + config.networkId + ", " + networkSelectionBSSID);
                                return true;
                            } else if (((!scanResult.is24GHz() || scanResult.level > config.entryRssi24GHz) && (!scanResult.is5GHz() || scanResult.level > config.entryRssi5GHz)) || RilUtil.isWifiOnly(this.mContext) || SemSarManager.isRfTestMode()) {
                                return true;
                            } else {
                                increaseEntryRssi(config);
                                return true;
                            }
                        } else if (!this.mVerboseLoggingEnabled) {
                            return true;
                        } else {
                            Log.v(TAG, "updateNetworkSelectionStatus: no scanDetailCache for " + config.networkId);
                            return true;
                        }
                    }
                } else if (!hasEverConnected && reason == 2) {
                    if (config.allowedKeyManagement.get(8)) {
                        reason = 13;
                    }
                    SemWifiFrameworkUxUtils.sendShowInfoIntentToSettings(this.mContext, 60, config.networkId, reason);
                }
            } else {
                Log.v(TAG, "Ignore update network selection status since adding disable network was skipped by b2b config policy.");
                return false;
            }
        }
        if (this.mUserSelectNetwork) {
            this.mUserSelectNetwork = false;
        }
        return setNetworkSelectionStatus(config, reason);
    }

    public boolean updateNetworkSelectionStatus(int networkId, int reason) {
        WifiConfiguration config = getInternalConfiguredNetwork(networkId);
        if (config == null) {
            return false;
        }
        return updateNetworkSelectionStatus(config, reason);
    }

    public boolean updateNetworkNotRecommended(int networkId, boolean notRecommended) {
        WifiConfiguration config = getInternalConfiguredNetwork(networkId);
        if (config == null) {
            return false;
        }
        config.getNetworkSelectionStatus().setNotRecommended(notRecommended);
        if (this.mVerboseLoggingEnabled) {
            localLog("updateNetworkRecommendation: configKey=" + config.configKey() + " notRecommended=" + notRecommended);
        }
        saveToStore(false);
        return true;
    }

    private boolean tryEnableNetwork(WifiConfiguration config) {
        WifiConfiguration.NetworkSelectionStatus networkStatus = config.getNetworkSelectionStatus();
        if (networkStatus.isNetworkTemporaryDisabled()) {
            if (this.mClock.getElapsedSinceBootMillis() - networkStatus.getDisableTime() >= ((long) NETWORK_SELECTION_DISABLE_TIMEOUT_MS[networkStatus.getNetworkSelectionDisableReason()])) {
                return updateNetworkSelectionStatus(config, 0);
            }
        } else if (networkStatus.isDisabledByReason(12)) {
            return updateNetworkSelectionStatus(config, 0);
        }
        return false;
    }

    public boolean tryEnableNetwork(int networkId) {
        WifiConfiguration config = getInternalConfiguredNetwork(networkId);
        if (config == null) {
            return false;
        }
        return tryEnableNetwork(config);
    }

    public boolean enableNetwork(int networkId, boolean disableOthers, int uid) {
        if (this.mVerboseLoggingEnabled) {
            Log.v(TAG, "Enabling network " + networkId + " (disableOthers " + disableOthers + ")");
        }
        if (!doesUidBelongToCurrentUser(uid)) {
            Log.e(TAG, "UID " + uid + " not visible to the current user");
            return false;
        }
        WifiConfiguration config = getInternalConfiguredNetwork(networkId);
        if (config == null) {
            return false;
        }
        if (disableOthers) {
            setLastSelectedNetwork(networkId);
        }
        if (!canModifyNetwork(config, uid)) {
            Log.e(TAG, "UID " + uid + " does not have permission to update configuration " + config.configKey());
            return false;
        } else if (!updateNetworkSelectionStatus(networkId, 0)) {
            return false;
        } else {
            saveToStore(true);
            return true;
        }
    }

    /* JADX WARNING: Code restructure failed: missing block: B:45:0x0173, code lost:
        if (r0 == false) goto L_0x017b;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:46:0x0175, code lost:
        saveToStore(true);
        sendConfiguredNetworksChangedBroadcast();
     */
    /* JADX WARNING: Code restructure failed: missing block: B:47:0x017b, code lost:
        return true;
     */
    public boolean forcinglyEnableAllNetworks(int uid) {
        boolean changed = false;
        if (this.mVerboseLoggingEnabled) {
            Log.v(TAG, "Forcingly enabling all networks");
        }
        if (!doesUidBelongToCurrentUser(uid)) {
            Log.e(TAG, "UID " + uid + " not visible to the current user");
            return false;
        }
        synchronized (this.mLock) {
            for (WifiConfiguration config : getInternalConfiguredNetworks()) {
                if (config == null) {
                    Log.e(TAG, "Internal configured networks is null");
                    return false;
                } else if (config.ephemeral) {
                    Log.v(TAG, "Current config is ephemeral");
                } else if (!canModifyNetwork(config, uid)) {
                    Log.v(TAG, "UID " + uid + " does not have permission to update configuration " + config.configKey());
                } else if (!WifiPolicyCache.getInstance(this.mContext).isNetworkAllowed(config, false)) {
                    Log.v(TAG, "Current config is blocked by MDM");
                } else {
                    WifiConfiguration.NetworkSelectionStatus status = config.getNetworkSelectionStatus();
                    if (status.getNetworkSelectionStatus() != 0) {
                        if (!status.isDisabledByReason(11)) {
                            if (!status.isDisabledByReason(18)) {
                                if (status.isDisabledByReason(10)) {
                                    Log.v(TAG, "Current config was permanently disabled by reason " + status.getNetworkDisableReasonString());
                                } else if (status.isDisabledByReason(15)) {
                                    Log.v(TAG, "Current config was CaptivePortal " + status.getNetworkDisableReasonString());
                                } else {
                                    changed = true;
                                    status.setNetworkSelectionStatus(0);
                                    status.setDisableTime(-1);
                                    status.setNetworkSelectionDisableReason(0);
                                    status.clearDisableReasonCounter();
                                    config.status = 2;
                                    localLog("forcinglyEnableAllNetworks: configKey=" + config.configKey() + " status=" + status.getNetworkStatusString() + " disableReason=" + status.getNetworkDisableReasonString() + " at=" + createDebugTimeStampString(this.mClock.getWallClockMillis()));
                                }
                            }
                        }
                        Log.v(TAG, "Current config was permanently disabled by reason " + status.getNetworkDisableReasonString());
                    }
                }
            }
        }
    }

    /* JADX WARNING: Code restructure failed: missing block: B:45:0x014b, code lost:
        if (r0 == false) goto L_0x0153;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:46:0x014d, code lost:
        saveToStore(true);
        sendConfiguredNetworksChangedBroadcast();
     */
    /* JADX WARNING: Code restructure failed: missing block: B:47:0x0153, code lost:
        return true;
     */
    public boolean forcinglyEnablePolicyUpdatedNetworks(int uid) {
        boolean changed = false;
        if (this.mVerboseLoggingEnabled) {
            Log.v(TAG, "Forcingly enabling policy updated networks");
        }
        if (!doesUidBelongToCurrentUser(uid)) {
            Log.e(TAG, "UID " + uid + " not visible to the current user");
            return false;
        }
        synchronized (this.mLock) {
            for (WifiConfiguration config : getInternalConfiguredNetworks()) {
                if (config == null) {
                    Log.e(TAG, "Internal configured networks is null");
                    return false;
                } else if (config.ephemeral) {
                    Log.v(TAG, "Current config is ephemeral");
                } else if (!canModifyNetwork(config, uid)) {
                    Log.v(TAG, "UID " + uid + " does not have permission to update configuration " + config.configKey());
                } else if (!WifiPolicyCache.getInstance(this.mContext).isNetworkAllowed(config, false)) {
                    Log.v(TAG, "Current config is blocked by MDM");
                } else {
                    WifiB2BConfigurationPolicy.B2BConfiguration b2bConfig = this.mWifiInjector.getWifiB2bConfigPolicy().getConfiguration(config.getPrintableSsid());
                    if (b2bConfig != null) {
                        if (b2bConfig.skipAddingDisableNetwork()) {
                            WifiConfiguration.NetworkSelectionStatus status = config.getNetworkSelectionStatus();
                            if (status.getNetworkSelectionStatus() != 0) {
                                if (!status.isDisabledByReason(11)) {
                                    if (!status.isDisabledByReason(18)) {
                                        changed = true;
                                        status.setNetworkSelectionStatus(0);
                                        status.setDisableTime(-1);
                                        status.setNetworkSelectionDisableReason(0);
                                        status.clearDisableReasonCounter();
                                        config.status = 2;
                                        localLog("forcinglyEnablePolicyUpdatedNetworks: configKey=" + config.configKey() + " status=" + status.getNetworkStatusString() + " disableReason=" + status.getNetworkDisableReasonString() + " at=" + createDebugTimeStampString(this.mClock.getWallClockMillis()));
                                    }
                                }
                                Log.v(TAG, "Current config was permanently disabled by reason " + status.getNetworkDisableReasonString());
                            }
                        }
                    }
                    Log.v(TAG, "Current config needs not be enabled by b2b config policy");
                }
            }
        }
    }

    public boolean canDisableNetwork(int networkId, int uid) {
        if (this.mVerboseLoggingEnabled) {
            Log.v(TAG, "Checking permission to disable network " + networkId);
        }
        if (!doesUidBelongToCurrentUser(uid)) {
            Log.e(TAG, "UID " + uid + " not visible to the current user");
            return false;
        }
        WifiConfiguration config = getInternalConfiguredNetwork(networkId);
        if (config == null) {
            return false;
        }
        if (canModifyNetwork(config, uid)) {
            return true;
        }
        Log.e(TAG, "UID " + uid + " does not have permission to update configuration " + config.configKey());
        return false;
    }

    public boolean disableNetwork(int networkId, int uid) {
        if (this.mVerboseLoggingEnabled) {
            Log.v(TAG, "Disabling network " + networkId);
        }
        if (!doesUidBelongToCurrentUser(uid)) {
            Log.e(TAG, "UID " + uid + " not visible to the current user");
            return false;
        }
        WifiConfiguration config = getInternalConfiguredNetwork(networkId);
        if (config == null) {
            return false;
        }
        if (networkId == this.mLastSelectedNetworkId) {
            clearLastSelectedNetwork();
        }
        if (!canModifyNetwork(config, uid)) {
            Log.e(TAG, "UID " + uid + " does not have permission to update configuration " + config.configKey());
            return false;
        } else if (!updateNetworkSelectionStatus(networkId, 11)) {
            return false;
        } else {
            saveToStore(true);
            return true;
        }
    }

    public boolean updateLastConnectUid(int networkId, int uid) {
        if (this.mVerboseLoggingEnabled) {
            Log.v(TAG, "Update network last connect UID for " + networkId);
        }
        if (!doesUidBelongToCurrentUser(uid)) {
            Log.e(TAG, "UID " + uid + " not visible to the current user");
            return false;
        }
        WifiConfiguration config = getInternalConfiguredNetwork(networkId);
        if (config == null) {
            return false;
        }
        config.lastConnectUid = uid;
        return true;
    }

    public boolean updateNetworkAfterConnect(int networkId) {
        if (this.mVerboseLoggingEnabled) {
            Log.v(TAG, "Update network after connect for " + networkId);
        }
        WifiConfiguration config = getInternalConfiguredNetwork(networkId);
        if (config == null) {
            return false;
        }
        config.lastConnected = this.mClock.getWallClockMillis();
        config.numAssociation++;
        config.getNetworkSelectionStatus().clearDisableReasonCounter();
        config.getNetworkSelectionStatus().setHasEverConnected(true);
        setNetworkStatus(config, 0);
        saveToStore(false);
        return true;
    }

    public boolean updateNetworkAfterDisconnect(int networkId) {
        if (this.mVerboseLoggingEnabled) {
            Log.v(TAG, "Update network after disconnect for " + networkId);
        }
        WifiConfiguration config = getInternalConfiguredNetwork(networkId);
        if (config == null) {
            return false;
        }
        config.lastDisconnected = this.mClock.getWallClockMillis();
        if (config.status == 0) {
            setNetworkStatus(config, 2);
        }
        saveToStore(false);
        return true;
    }

    public boolean setNetworkDefaultGwMacAddress(int networkId, String macAddress) {
        WifiConfiguration config = getInternalConfiguredNetwork(networkId);
        if (config == null) {
            return false;
        }
        config.defaultGwMacAddress = macAddress;
        return true;
    }

    public boolean setNetworkRandomizedMacAddress(int networkId, MacAddress macAddress) {
        WifiConfiguration config = getInternalConfiguredNetwork(networkId);
        if (config == null) {
            return false;
        }
        config.setRandomizedMacAddress(macAddress);
        return true;
    }

    public boolean clearNetworkCandidateScanResult(int networkId) {
        if (this.mVerboseLoggingEnabled) {
            Log.v(TAG, "Clear network candidate scan result for " + networkId);
        }
        WifiConfiguration config = getInternalConfiguredNetwork(networkId);
        if (config == null) {
            return false;
        }
        config.getNetworkSelectionStatus().setCandidate((ScanResult) null);
        config.getNetworkSelectionStatus().setCandidateScore(Integer.MIN_VALUE);
        config.getNetworkSelectionStatus().setSeenInLastQualifiedNetworkSelection(false);
        return true;
    }

    public boolean setNetworkCandidateScanResult(int networkId, ScanResult scanResult, int score) {
        if (this.mVerboseLoggingEnabled) {
            Log.v(TAG, "Set network candidate scan result " + scanResult + " for " + networkId);
        }
        WifiConfiguration config = getInternalConfiguredNetwork(networkId);
        if (config == null) {
            Log.e(TAG, "Cannot find network for " + networkId);
            return false;
        }
        config.getNetworkSelectionStatus().setCandidate(scanResult);
        config.getNetworkSelectionStatus().setCandidateScore(score);
        config.getNetworkSelectionStatus().setSeenInLastQualifiedNetworkSelection(true);
        return true;
    }

    private void removeConnectChoiceFromAllNetworks(String connectChoiceConfigKey) {
        if (this.mVerboseLoggingEnabled) {
            Log.v(TAG, "Removing connect choice from all networks " + connectChoiceConfigKey);
        }
        if (connectChoiceConfigKey != null) {
            for (WifiConfiguration config : this.mConfiguredNetworks.valuesForCurrentUser()) {
                if (config.semIsVendorSpecificSsid) {
                    this.mIsCarrierNetworkSaved = true;
                }
                String connectChoice = config.getNetworkSelectionStatus().getConnectChoice();
                if (TextUtils.equals(connectChoice, connectChoiceConfigKey)) {
                    Log.d(TAG, "remove connect choice:" + connectChoice + " from " + config.SSID + " : " + config.networkId);
                    clearNetworkConnectChoice(config.networkId);
                }
            }
        }
    }

    public boolean clearNetworkConnectChoice(int networkId) {
        if (this.mVerboseLoggingEnabled) {
            Log.v(TAG, "Clear network connect choice for " + networkId);
        }
        WifiConfiguration config = getInternalConfiguredNetwork(networkId);
        if (config == null) {
            return false;
        }
        config.getNetworkSelectionStatus().setConnectChoice((String) null);
        config.getNetworkSelectionStatus().setConnectChoiceTimestamp(-1);
        saveToStore(false);
        return true;
    }

    public boolean setNetworkConnectChoice(int networkId, String connectChoiceConfigKey, long timestamp) {
        if (this.mVerboseLoggingEnabled) {
            Log.v(TAG, "Set network connect choice " + connectChoiceConfigKey + " for " + networkId);
        }
        WifiConfiguration config = getInternalConfiguredNetwork(networkId);
        if (config == null) {
            return false;
        }
        config.getNetworkSelectionStatus().setConnectChoice(connectChoiceConfigKey);
        config.getNetworkSelectionStatus().setConnectChoiceTimestamp(timestamp);
        saveToStore(false);
        return true;
    }

    public boolean incrementNetworkNoInternetAccessReports(int networkId) {
        WifiConfiguration config = getInternalConfiguredNetwork(networkId);
        if (config == null) {
            return false;
        }
        config.numNoInternetAccessReports++;
        return true;
    }

    public boolean setNetworkValidatedInternetAccess(int networkId, boolean validated) {
        WifiConfiguration config = getInternalConfiguredNetwork(networkId);
        if (config == null) {
            return false;
        }
        config.validatedInternetAccess = validated;
        config.numNoInternetAccessReports = 0;
        saveToStore(false);
        return true;
    }

    public boolean setNetworkNoInternetAccessExpected(int networkId, boolean expected) {
        WifiConfiguration config = getInternalConfiguredNetwork(networkId);
        if (config == null) {
            return false;
        }
        config.noInternetAccessExpected = expected;
        return true;
    }

    private void clearLastSelectedNetwork() {
        if (this.mVerboseLoggingEnabled) {
            Log.v(TAG, "Clearing last selected network");
        }
        this.mLastSelectedNetworkId = -1;
        this.mLastSelectedTimeStamp = -1;
    }

    private void setLastSelectedNetwork(int networkId) {
        if (this.mVerboseLoggingEnabled) {
            Log.v(TAG, "Setting last selected network to " + networkId);
        }
        this.mLastSelectedNetworkId = networkId;
        this.mLastSelectedTimeStamp = this.mClock.getElapsedSinceBootMillis();
    }

    public int getLastSelectedNetwork() {
        return this.mLastSelectedNetworkId;
    }

    public String getLastSelectedNetworkConfigKey() {
        WifiConfiguration config;
        int i = this.mLastSelectedNetworkId;
        if (i == -1 || (config = getInternalConfiguredNetwork(i)) == null) {
            return "";
        }
        return config.configKey();
    }

    public long getLastSelectedTimeStamp() {
        return this.mLastSelectedTimeStamp;
    }

    public ScanDetailCache getScanDetailCacheForNetwork(int networkId) {
        return this.mScanDetailCaches.get(Integer.valueOf(networkId));
    }

    private ScanDetailCache getOrCreateScanDetailCacheForNetwork(WifiConfiguration config) {
        if (config == null) {
            return null;
        }
        ScanDetailCache cache = getScanDetailCacheForNetwork(config.networkId);
        if (cache != null || config.networkId == -1) {
            return cache;
        }
        ScanDetailCache cache2 = new ScanDetailCache(config, 192, 128);
        this.mScanDetailCaches.put(Integer.valueOf(config.networkId), cache2);
        return cache2;
    }

    private void saveToScanDetailCacheForNetwork(WifiConfiguration config, ScanDetail scanDetail) {
        ScanResult scanResult = scanDetail.getScanResult();
        ScanDetailCache scanDetailCache = getOrCreateScanDetailCacheForNetwork(config);
        if (scanDetailCache == null) {
            Log.e(TAG, "Could not allocate scan cache for " + config.getPrintableSsid());
            return;
        }
        if (config.ephemeral) {
            scanResult.untrusted = true;
        }
        scanDetailCache.put(scanDetail);
        attemptNetworkLinking(config);
    }

    public WifiConfiguration getConfiguredNetworkForScanDetail(ScanDetail scanDetail) {
        return getConfiguredNetworkForScanDetail(scanDetail, false);
    }

    private WifiConfiguration getConfiguredNetworkForScanDetail(ScanDetail scanDetail, boolean passpointOnly) {
        ScanResult scanResult = scanDetail.getScanResult();
        if (scanResult == null) {
            Log.e(TAG, "No scan result found in scan detail");
            return null;
        }
        WifiConfiguration config = null;
        try {
            config = this.mConfiguredNetworks.getByScanResultForCurrentUser(scanResult, passpointOnly);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Failed to lookup network from config map", e);
        }
        if (config != null && this.mVerboseLoggingEnabled) {
            Log.v(TAG, "getSavedNetworkFromScanDetail Found " + config.configKey() + " for " + scanResult.SSID + "[" + scanResult.capabilities + "]");
        }
        return config;
    }

    public WifiConfiguration getConfiguredNetworkForScanDetailAndCache(ScanDetail scanDetail) {
        return getConfiguredNetworkForScanDetailAndCache(scanDetail, false);
    }

    public WifiConfiguration getConfiguredNetworkForScanDetailAndCache(ScanDetail scanDetail, boolean passpointOnly) {
        WifiConfiguration network = getConfiguredNetworkForScanDetail(scanDetail, passpointOnly);
        if (network == null) {
            return null;
        }
        saveToScanDetailCacheForNetwork(network, scanDetail);
        if (scanDetail.getNetworkDetail() != null && scanDetail.getNetworkDetail().getDtimInterval() > 0) {
            network.dtimInterval = scanDetail.getNetworkDetail().getDtimInterval();
        }
        return createExternalWifiConfiguration(network, true, 1010);
    }

    public void updateScanDetailCacheFromWifiInfo(WifiInfo info) {
        WifiConfiguration config = getInternalConfiguredNetwork(info.getNetworkId());
        ScanDetailCache scanDetailCache = getScanDetailCacheForNetwork(info.getNetworkId());
        if (config != null && scanDetailCache != null) {
            ScanDetail scanDetail = scanDetailCache.getScanDetail(info.getBSSID());
            if (scanDetail != null) {
                ScanResult result = scanDetail.getScanResult();
                long previousSeen = result.seen;
                int previousRssi = result.level;
                scanDetail.setSeen();
                result.level = info.getRssi();
                long age = result.seen - previousSeen;
                if (previousSeen > 0 && age > 0 && age < 40000 / 2) {
                    double alpha = 0.5d - (((double) age) / ((double) 40000));
                    result.level = (int) ((((double) result.level) * (1.0d - alpha)) + (((double) previousRssi) * alpha));
                }
                if (this.mVerboseLoggingEnabled) {
                    Log.v(TAG, "Updating scan detail cache freq=" + result.frequency + " BSSID=" + result.BSSID + " RSSI=" + result.level + " for " + config.configKey());
                }
            }
        }
    }

    public void updateScanDetailForNetwork(int networkId, ScanDetail scanDetail) {
        WifiConfiguration network = getInternalConfiguredNetwork(networkId);
        if (network != null) {
            saveToScanDetailCacheForNetwork(network, scanDetail);
        }
    }

    private boolean shouldNetworksBeLinked(WifiConfiguration network1, WifiConfiguration network2, ScanDetailCache scanDetailCache1, ScanDetailCache scanDetailCache2) {
        if (this.mOnlyLinkSameCredentialConfigurations && !TextUtils.equals(network1.preSharedKey, network2.preSharedKey)) {
            if (this.mVerboseLoggingEnabled) {
                Log.v(TAG, "shouldNetworksBeLinked unlink due to password mismatch");
            }
            return false;
        } else if (network1.defaultGwMacAddress == null || network2.defaultGwMacAddress == null) {
            if (scanDetailCache1 == null || scanDetailCache2 == null) {
                return false;
            }
            for (String abssid : scanDetailCache1.keySet()) {
                for (String bbssid : scanDetailCache2.keySet()) {
                    if (abssid.regionMatches(true, 0, bbssid, 0, 16)) {
                        if (this.mVerboseLoggingEnabled) {
                            Log.v(TAG, "shouldNetworksBeLinked link due to DBDC BSSID match " + network2.SSID + " and " + network1.SSID + " bssida " + abssid + " bssidb " + bbssid);
                        }
                        return true;
                    }
                }
            }
            return false;
        } else if (!network1.defaultGwMacAddress.equals(network2.defaultGwMacAddress)) {
            return false;
        } else {
            if (this.mVerboseLoggingEnabled) {
                Log.v(TAG, "shouldNetworksBeLinked link due to same gw " + network2.SSID + " and " + network1.SSID + " GW " + network1.defaultGwMacAddress);
            }
            return true;
        }
    }

    private void linkNetworks(WifiConfiguration network1, WifiConfiguration network2) {
        if (this.mVerboseLoggingEnabled) {
            Log.v(TAG, "linkNetworks will link " + network2.configKey() + " and " + network1.configKey());
        }
        if (network2.linkedConfigurations == null) {
            network2.linkedConfigurations = new HashMap();
        }
        if (network1.linkedConfigurations == null) {
            network1.linkedConfigurations = new HashMap();
        }
        network2.linkedConfigurations.put(network1.configKey(), 1);
        network1.linkedConfigurations.put(network2.configKey(), 1);
    }

    private void unlinkNetworks(WifiConfiguration network1, WifiConfiguration network2) {
        if (!(network2.linkedConfigurations == null || network2.linkedConfigurations.get(network1.configKey()) == null)) {
            if (this.mVerboseLoggingEnabled) {
                Log.v(TAG, "unlinkNetworks un-link " + network1.configKey() + " from " + network2.configKey());
            }
            network2.linkedConfigurations.remove(network1.configKey());
        }
        if (network1.linkedConfigurations != null && network1.linkedConfigurations.get(network2.configKey()) != null) {
            if (this.mVerboseLoggingEnabled) {
                Log.v(TAG, "unlinkNetworks un-link " + network2.configKey() + " from " + network1.configKey());
            }
            network1.linkedConfigurations.remove(network2.configKey());
        }
    }

    private void attemptNetworkLinking(WifiConfiguration config) {
        if (config.allowedKeyManagement.get(1)) {
            ScanDetailCache scanDetailCache = getScanDetailCacheForNetwork(config.networkId);
            if (scanDetailCache == null || scanDetailCache.size() <= 6) {
                for (WifiConfiguration linkConfig : getInternalConfiguredNetworks()) {
                    if (!linkConfig.configKey().equals(config.configKey()) && !linkConfig.ephemeral && linkConfig.allowedKeyManagement.get(1)) {
                        ScanDetailCache linkScanDetailCache = getScanDetailCacheForNetwork(linkConfig.networkId);
                        if (linkScanDetailCache == null || linkScanDetailCache.size() <= 6) {
                            if (shouldNetworksBeLinked(config, linkConfig, scanDetailCache, linkScanDetailCache)) {
                                linkNetworks(config, linkConfig);
                            } else {
                                unlinkNetworks(config, linkConfig);
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean addToChannelSetForNetworkFromScanDetailCache(Set<Integer> channelSet, ScanDetailCache scanDetailCache, long nowInMillis, long ageInMillis, int maxChannelSetSize) {
        if (scanDetailCache != null && scanDetailCache.size() > 0) {
            for (ScanDetail scanDetail : scanDetailCache.values()) {
                ScanResult result = scanDetail.getScanResult();
                boolean valid = nowInMillis - result.seen < ageInMillis;
                if (this.mVerboseLoggingEnabled) {
                    Log.v(TAG, "fetchChannelSetForNetwork has " + result.BSSID + " freq " + result.frequency + " age " + (nowInMillis - result.seen) + " ?=" + valid);
                }
                if (valid) {
                    channelSet.add(Integer.valueOf(result.frequency));
                }
                if (channelSet.size() >= maxChannelSetSize) {
                    return false;
                }
            }
        }
        return true;
    }

    public Set<Integer> fetchChannelSetForNetworkForPartialScan(int networkId, long ageInMillis, int homeChannelFreq) {
        WifiConfiguration config = getInternalConfiguredNetwork(networkId);
        if (config == null) {
            return null;
        }
        ScanDetailCache scanDetailCache = getScanDetailCacheForNetwork(networkId);
        if (scanDetailCache == null && config.linkedConfigurations == null) {
            Log.i(TAG, "No scan detail and linked configs associated with networkId " + networkId);
            return null;
        }
        if (this.mVerboseLoggingEnabled) {
            StringBuilder dbg = new StringBuilder();
            dbg.append("fetchChannelSetForNetworkForPartialScan ageInMillis ");
            dbg.append(ageInMillis);
            dbg.append(" for ");
            dbg.append(config.configKey());
            dbg.append(" max ");
            dbg.append(this.mMaxNumActiveChannelsForPartialScans);
            if (scanDetailCache != null) {
                dbg.append(" bssids " + scanDetailCache.size());
            }
            if (config.linkedConfigurations != null) {
                dbg.append(" linked " + config.linkedConfigurations.size());
            }
            Log.v(TAG, dbg.toString());
        }
        Set<Integer> channelSet = new HashSet<>();
        if (homeChannelFreq > 0) {
            channelSet.add(Integer.valueOf(homeChannelFreq));
            if (channelSet.size() >= this.mMaxNumActiveChannelsForPartialScans) {
                return channelSet;
            }
        }
        long nowInMillis = this.mClock.getWallClockMillis();
        if (addToChannelSetForNetworkFromScanDetailCache(channelSet, scanDetailCache, nowInMillis, ageInMillis, this.mMaxNumActiveChannelsForPartialScans) && config.linkedConfigurations != null) {
            Iterator it = config.linkedConfigurations.keySet().iterator();
            while (it.hasNext() && ((linkedConfig = getInternalConfiguredNetwork((String) it.next())) == null || addToChannelSetForNetworkFromScanDetailCache(channelSet, getScanDetailCacheForNetwork(linkedConfig.networkId), nowInMillis, ageInMillis, this.mMaxNumActiveChannelsForPartialScans))) {
            }
        }
        return channelSet;
    }

    private Set<Integer> fetchChannelSetForNetworkForPnoScan(int networkId, long ageInMillis) {
        ScanDetailCache scanDetailCache;
        WifiConfiguration config = getInternalConfiguredNetwork(networkId);
        if (config == null || (scanDetailCache = getScanDetailCacheForNetwork(networkId)) == null) {
            return null;
        }
        if (this.mVerboseLoggingEnabled) {
            StringBuilder sb = new StringBuilder("fetchChannelSetForNetworkForPnoScan ageInMillis ");
            sb.append(ageInMillis);
            sb.append(" for ");
            sb.append(config.configKey());
            sb.append(" bssids " + scanDetailCache.size());
            Log.v(TAG, sb.toString());
        }
        Set<Integer> channelSet = new HashSet<>();
        addToChannelSetForNetworkFromScanDetailCache(channelSet, scanDetailCache, this.mClock.getWallClockMillis(), ageInMillis, Integer.MAX_VALUE);
        return channelSet;
    }

    public List<WifiScanner.PnoSettings.PnoNetwork> retrievePnoNetworkList() {
        List<WifiScanner.PnoSettings.PnoNetwork> pnoList = new ArrayList<>();
        List<WifiConfiguration> networks = new ArrayList<>(getInternalConfiguredNetworks());
        Iterator<WifiConfiguration> iter = networks.iterator();
        while (iter.hasNext()) {
            WifiConfiguration config = iter.next();
            if (config.ephemeral || config.isPasspoint() || config.getNetworkSelectionStatus().isNetworkPermanentlyDisabled() || config.getNetworkSelectionStatus().isNetworkTemporaryDisabled()) {
                iter.remove();
            }
        }
        if (networks.isEmpty()) {
            return pnoList;
        }
        Collections.sort(networks, sScanListComparator);
        if (this.mPnoRecencySortingEnabled) {
            WifiConfiguration lastConnectedNetwork = networks.stream().max(Comparator.comparing($$Lambda$WifiConfigManager$IQAd8DT29bH7BRNkSq57y94BdXA.INSTANCE)).get();
            if (lastConnectedNetwork.lastConnected != 0) {
                networks.remove(networks.indexOf(lastConnectedNetwork));
                networks.add(0, lastConnectedNetwork);
            }
        }
        for (WifiConfiguration config2 : networks) {
            WifiScanner.PnoSettings.PnoNetwork pnoNetwork = WifiConfigurationUtil.createPnoNetwork(config2);
            pnoList.add(pnoNetwork);
            if (this.mPnoFrequencyCullingEnabled) {
                Set<Integer> channelSet = fetchChannelSetForNetworkForPnoScan(config2.networkId, MAX_PNO_SCAN_FREQUENCY_AGE_MS);
                if (channelSet != null) {
                    pnoNetwork.frequencies = channelSet.stream().mapToInt($$Lambda$UV1wDVoVlbcxpr8zevj_aMFtUGw.INSTANCE).toArray();
                }
                if (this.mVerboseLoggingEnabled) {
                    Log.v(TAG, "retrievePnoNetworkList " + pnoNetwork.ssid + ":" + Arrays.toString(pnoNetwork.frequencies));
                }
            }
        }
        return pnoList;
    }

    public List<WifiScanner.ScanSettings.HiddenNetwork> retrieveHiddenNetworkList() {
        String nonUtf8String;
        List<WifiScanner.ScanSettings.HiddenNetwork> hiddenList = new ArrayList<>();
        List<WifiConfiguration> networks = new ArrayList<>(getInternalConfiguredNetworks());
        Iterator<WifiConfiguration> iter = networks.iterator();
        while (iter.hasNext()) {
            WifiConfiguration config = iter.next();
            if (!config.hiddenSSID || TextUtils.isEmpty(config.SSID)) {
                iter.remove();
            }
        }
        Collections.sort(networks, sScanListComparator);
        for (WifiConfiguration config2 : networks) {
            if ((CHARSET_CN.equals(CONFIG_CHARSET) || CHARSET_KOR.equals(CONFIG_CHARSET)) && (nonUtf8String = getNonUTF8HiddenNetworkSsid(config2.SSID)) != null) {
                try {
                    if (NativeUtil.decodeSsid(nonUtf8String).size() > 32) {
                        localLog("retrieveHiddenNetworkList: [KOR|CHN] " + config2.configKey() + ":" + config2.networkId + " is not added to hiddenList...");
                    } else {
                        hiddenList.add(new WifiScanner.ScanSettings.HiddenNetwork(nonUtf8String));
                    }
                } catch (IllegalArgumentException e) {
                    Log.w(TAG, config2.configKey() + ":" + config2.networkId + " is invalid.");
                }
            }
            try {
                if (NativeUtil.decodeSsid(config2.SSID).size() > 32) {
                    localLog("retrieveHiddenNetworkList: " + config2.configKey() + ":" + config2.networkId + " is not added to hiddenList...");
                } else {
                    hiddenList.add(new WifiScanner.ScanSettings.HiddenNetwork(config2.SSID));
                }
            } catch (IllegalArgumentException e2) {
                Log.w(TAG, config2.configKey() + ":" + config2.networkId + " is invalid.");
            }
        }
        return hiddenList;
    }

    private String getNonUTF8HiddenNetworkSsid(String ssid) {
        byte[] StringBuffer;
        if (CHARSET_CN.equals(CONFIG_CHARSET)) {
            StringBuffer = ssid.getBytes(Charset.forName(CHARSET_CN));
        } else {
            StringBuffer = ssid.getBytes(Charset.forName(CHARSET_KOR));
        }
        if (NativeUtil.isUTF8String(StringBuffer, (long) StringBuffer.length) || !NativeUtil.isUCNVString(StringBuffer, StringBuffer.length)) {
            return null;
        }
        return makeNonUTF8Ssid(StringBuffer, StringBuffer.length);
    }

    private String makeNonUTF8Ssid(byte[] bytes, int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            byte b = bytes[i];
            if (!((i == 0 && b == 34) || (i == length - 1 && b == 34))) {
                sb.append(String.format("%02x", Byte.valueOf(b)));
            }
        }
        return sb.toString();
    }

    public boolean wasEphemeralNetworkDeleted(String ssid) {
        if (!this.mDeletedEphemeralSsidsToTimeMap.containsKey(ssid)) {
            return false;
        }
        if (this.mClock.getWallClockMillis() - this.mDeletedEphemeralSsidsToTimeMap.get(ssid).longValue() <= DELETED_EPHEMERAL_SSID_EXPIRY_MS) {
            return true;
        }
        this.mDeletedEphemeralSsidsToTimeMap.remove(ssid);
        return false;
    }

    public WifiConfiguration disableEphemeralNetwork(String ssid) {
        if (ssid == null) {
            return null;
        }
        WifiConfiguration foundConfig = null;
        Iterator<WifiConfiguration> it = getInternalConfiguredNetworks().iterator();
        while (true) {
            if (!it.hasNext()) {
                break;
            }
            WifiConfiguration config = it.next();
            if ((config.ephemeral || config.isPasspoint()) && TextUtils.equals(config.SSID, ssid)) {
                foundConfig = config;
                break;
            }
        }
        if (foundConfig == null) {
            return null;
        }
        this.mDeletedEphemeralSsidsToTimeMap.put(ssid, Long.valueOf(this.mClock.getWallClockMillis()));
        Log.d(TAG, "Forget ephemeral SSID " + ssid + " num=" + this.mDeletedEphemeralSsidsToTimeMap.size());
        if (foundConfig.ephemeral) {
            Log.d(TAG, "Found ephemeral config in disableEphemeralNetwork: " + foundConfig.networkId);
        } else if (foundConfig.isPasspoint()) {
            Log.d(TAG, "Found Passpoint config in disableEphemeralNetwork: " + foundConfig.networkId + ", FQDN: " + foundConfig.FQDN);
        }
        removeConnectChoiceFromAllNetworks(foundConfig.configKey());
        return foundConfig;
    }

    @VisibleForTesting
    public void clearDeletedEphemeralNetworks() {
        this.mDeletedEphemeralSsidsToTimeMap.clear();
    }

    public void resetSimNetworks() {
        if (this.mVerboseLoggingEnabled) {
            localLog("resetSimNetworks");
        }
        for (WifiConfiguration config : getInternalConfiguredNetworks()) {
            if (TelephonyUtil.isSimConfig(config)) {
                if (config.enterpriseConfig.getEapMethod() == 0) {
                    Pair<String, String> currentIdentity = TelephonyUtil.getSimIdentity(this.mTelephonyManager, new TelephonyUtil(), config, this.mWifiInjector.getCarrierNetworkConfig());
                    if (this.mVerboseLoggingEnabled) {
                        Log.d(TAG, "New identity for config " + config + ": " + currentIdentity);
                    }
                    if (currentIdentity == null) {
                        Log.d(TAG, "Identity is null");
                    } else {
                        config.enterpriseConfig.setIdentity((String) currentIdentity.first);
                    }
                } else {
                    config.enterpriseConfig.setIdentity("");
                    if (!TelephonyUtil.isAnonymousAtRealmIdentity(config.enterpriseConfig.getAnonymousIdentity())) {
                        config.enterpriseConfig.setAnonymousIdentity("");
                    }
                }
            }
        }
    }

    public int increaseAndGetPriority() {
        int i = this.mLastPriority + 1;
        this.mLastPriority = i;
        return i;
    }

    private void handleUserUnlockOrSwitch(int userId) {
        if (this.mVerboseLoggingEnabled) {
            Log.v(TAG, "Loading from store after user switch/unlock for " + userId);
        }
        if (loadFromUserStoreAfterUnlockOrSwitch(userId)) {
            saveToStore(true);
            this.mPendingUnlockStoreRead = false;
        }
    }

    public Set<Integer> handleUserSwitch(int userId) {
        if (this.mVerboseLoggingEnabled) {
            Log.v(TAG, "Handling user switch for " + userId);
        }
        int i = this.mCurrentUserId;
        if (userId == i) {
            Log.w(TAG, "User already in foreground " + userId);
            return new HashSet();
        } else if (this.mPendingStoreRead) {
            Log.w(TAG, "User switch before store is read!");
            this.mConfiguredNetworks.setNewUser(userId);
            this.mCurrentUserId = userId;
            this.mDeferredUserUnlockRead = false;
            this.mPendingUnlockStoreRead = true;
            return new HashSet();
        } else {
            if (this.mUserManager.isUserUnlockingOrUnlocked(i)) {
                saveToStore(true);
            }
            Set<Integer> removedNetworkIds = clearInternalUserData(this.mCurrentUserId);
            this.mConfiguredNetworks.setNewUser(userId);
            this.mCurrentUserId = userId;
            if (this.mUserManager.isUserUnlockingOrUnlocked(this.mCurrentUserId)) {
                handleUserUnlockOrSwitch(this.mCurrentUserId);
            } else {
                this.mPendingUnlockStoreRead = true;
                Log.i(TAG, "Waiting for user unlock to load from store");
            }
            return removedNetworkIds;
        }
    }

    public void handleUserUnlock(int userId) {
        if (this.mVerboseLoggingEnabled) {
            Log.v(TAG, "Handling user unlock for " + userId);
        }
        int i = this.mCurrentUserId;
        if (userId != i) {
            Log.e(TAG, "Ignore user unlock for non current user " + userId);
        } else if (this.mPendingStoreRead) {
            Log.w(TAG, "Ignore user unlock until store is read!");
            this.mDeferredUserUnlockRead = true;
        } else if (this.mPendingUnlockStoreRead) {
            handleUserUnlockOrSwitch(i);
        }
    }

    public void handleUserStop(int userId) {
        if (this.mVerboseLoggingEnabled) {
            Log.v(TAG, "Handling user stop for " + userId);
        }
        int i = this.mCurrentUserId;
        if (userId == i && this.mUserManager.isUserUnlockingOrUnlocked(i)) {
            saveToStore(true);
            clearInternalUserData(this.mCurrentUserId);
        }
    }

    private void clearInternalData() {
        localLog("clearInternalData: Clearing all internal data");
        this.mConfiguredNetworks.clear();
        this.mDeletedEphemeralSsidsToTimeMap.clear();
        this.mRandomizedMacAddressMapping.clear();
        this.mScanDetailCaches.clear();
        clearLastSelectedNetwork();
    }

    private Set<Integer> clearInternalUserData(int userId) {
        localLog("clearInternalUserData: Clearing user internal data for " + userId);
        Set<Integer> removedNetworkIds = new HashSet<>();
        for (WifiConfiguration config : getInternalConfiguredNetworks()) {
            if (!config.shared && WifiConfigurationUtil.doesUidBelongToAnyProfile(config.creatorUid, this.mUserManager.getProfiles(userId))) {
                removedNetworkIds.add(Integer.valueOf(config.networkId));
                localLog("clearInternalUserData: removed config. netId=" + config.networkId + " configKey=" + config.configKey());
                this.mConfiguredNetworks.remove(config.networkId);
            }
        }
        this.mDeletedEphemeralSsidsToTimeMap.clear();
        this.mScanDetailCaches.clear();
        clearLastSelectedNetwork();
        return removedNetworkIds;
    }

    private void loadInternalDataFromSharedStore(List<WifiConfiguration> configurations, Map<String, String> macAddressMapping) {
        for (WifiConfiguration configuration : configurations) {
            int i = this.mNextNetworkId;
            this.mNextNetworkId = i + 1;
            configuration.networkId = i;
            localLog("loadInternalDataFromSharedStore " + configuration.networkId + " : " + configuration.configKey());
            if (configuration.semIsVendorSpecificSsid) {
                this.mIsCarrierNetworkSaved = true;
            }
            if (this.mVerboseLoggingEnabled) {
                Log.v(TAG, "Adding network from shared store " + configuration.configKey());
            }
            try {
                this.mConfiguredNetworks.put(configuration);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Failed to add network to config map", e);
            }
        }
        this.mRandomizedMacAddressMapping.putAll(macAddressMapping);
    }

    private void loadInternalDataFromUserStore(List<WifiConfiguration> configurations, Map<String, Long> deletedEphemeralSsidsToTimeMap) {
        for (WifiConfiguration configuration : configurations) {
            int i = this.mNextNetworkId;
            this.mNextNetworkId = i + 1;
            configuration.networkId = i;
            localLog("loadInternalDataFromUserStore " + configuration.networkId + " : " + configuration.configKey());
            if (configuration.semIsVendorSpecificSsid) {
                this.mIsCarrierNetworkSaved = true;
            }
            if (this.mVerboseLoggingEnabled) {
                Log.v(TAG, "Adding network from user store " + configuration.configKey());
            }
            try {
                this.mConfiguredNetworks.put(configuration);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Failed to add network to config map", e);
            }
        }
        this.mDeletedEphemeralSsidsToTimeMap.putAll(deletedEphemeralSsidsToTimeMap);
    }

    private void generateRandomizedMacAddresses() {
        for (WifiConfiguration config : getInternalConfiguredNetworks()) {
            if (DEFAULT_MAC_ADDRESS.equals(config.getRandomizedMacAddress())) {
                setRandomizedMacToPersistentMac(config);
            }
        }
    }

    private void loadInternalData(List<WifiConfiguration> sharedConfigurations, List<WifiConfiguration> userConfigurations, Map<String, Long> deletedEphemeralSsidsToTimeMap, Map<String, String> macAddressMapping) {
        clearInternalData();
        loadInternalDataFromSharedStore(sharedConfigurations, macAddressMapping);
        loadInternalDataFromUserStore(userConfigurations, deletedEphemeralSsidsToTimeMap);
        generateRandomizedMacAddresses();
        if (this.mConfiguredNetworks.sizeForAllUsers() == 0) {
            Log.w(TAG, "No stored networks found.");
        }
        resetSimNetworks();
        sendConfiguredNetworksChangedBroadcast();
        this.mPendingStoreRead = false;
        sendLoadInternalDataCompleteBroadcast(false);
    }

    public boolean loadFromStore() {
        return loadFromStore(false);
    }

    public boolean loadFromStore(boolean clearIfError) {
        if (this.mDeferredUserUnlockRead) {
            Log.i(TAG, "Handling user unlock before loading from store.");
            List<WifiConfigStore.StoreFile> userStoreFiles = WifiConfigStore.createUserFiles(this.mCurrentUserId, UserManager.get(this.mContext));
            if (userStoreFiles == null) {
                Log.wtf(TAG, "Failed to create user store files");
                sendLoadInternalDataCompleteBroadcast(false);
                return false;
            }
            this.mWifiConfigStore.setUserStores(userStoreFiles);
            this.mDeferredUserUnlockRead = false;
        }
        if (!DBG || !new File("/data/misc/wifi/crash.txt").exists()) {
            try {
                this.mWifiConfigStore.read();
            } catch (IOException e) {
                localLog("Reading from new store failed. All saved networks are lost!" + e);
                Log.wtf(TAG, "Reading from new store failed. All saved networks are lost!", e);
                if (!clearIfError) {
                    return false;
                }
            } catch (XmlPullParserException e2) {
                localLog("XML deserialization of store failed. All saved networks are lost!" + e2);
                Log.wtf(TAG, "XML deserialization of store failed. All saved networks are lost!", e2);
                if (!clearIfError) {
                    return false;
                }
            }
            loadInternalData(this.mNetworkListSharedStoreData.getConfigurations(), this.mNetworkListUserStoreData.getConfigurations(), this.mDeletedEphemeralSsidsStoreData.getSsidToTimeMap(), this.mRandomizedMacStoreData.getMacMapping());
            return true;
        }
        localLog("loadFromStore: crash test file exists. throw NullPointerException!");
        throw new NullPointerException();
    }

    private boolean loadFromUserStoreAfterUnlockOrSwitch(int userId) {
        try {
            List<WifiConfigStore.StoreFile> userStoreFiles = WifiConfigStore.createUserFiles(userId, UserManager.get(this.mContext));
            if (userStoreFiles == null) {
                Log.e(TAG, "Failed to create user store files");
                return false;
            }
            this.mWifiConfigStore.switchUserStoresAndRead(userStoreFiles);
            sendLoadInternalDataCompleteBroadcast(true);
            loadInternalDataFromUserStore(this.mNetworkListUserStoreData.getConfigurations(), this.mDeletedEphemeralSsidsStoreData.getSsidToTimeMap());
            return true;
        } catch (IOException e) {
            Log.wtf(TAG, "Reading from new store failed. All saved private networks are lost!", e);
            return false;
        } catch (XmlPullParserException e2) {
            Log.wtf(TAG, "XML deserialization of store failed. All saved private networks arelost!", e2);
            return false;
        }
    }

    public boolean saveToStore(boolean forceWrite) {
        if (this.mPendingStoreRead) {
            Log.e(TAG, "Cannot save to store before store is read!");
            return false;
        }
        ArrayList<WifiConfiguration> sharedConfigurations = new ArrayList<>();
        ArrayList<WifiConfiguration> userConfigurations = new ArrayList<>();
        List<Integer> legacyPasspointNetId = new ArrayList<>();
        for (WifiConfiguration config : this.mConfiguredNetworks.valuesForAllUsers()) {
            if (!config.ephemeral && (!config.isPasspoint() || config.isLegacyPasspointConfig)) {
                if (config.isLegacyPasspointConfig && WifiConfigurationUtil.doesUidBelongToAnyProfile(config.creatorUid, this.mUserManager.getProfiles(this.mCurrentUserId))) {
                    legacyPasspointNetId.add(Integer.valueOf(config.networkId));
                    if (!PasspointManager.addLegacyPasspointConfig(config)) {
                        Log.e(TAG, "Failed to migrate legacy Passpoint config: " + config.FQDN);
                    }
                } else if (config.shared || !WifiConfigurationUtil.doesUidBelongToAnyProfile(config.creatorUid, this.mUserManager.getProfiles(this.mCurrentUserId))) {
                    sharedConfigurations.add(config);
                } else {
                    userConfigurations.add(config);
                }
            }
        }
        for (Integer num : legacyPasspointNetId) {
            this.mConfiguredNetworks.remove(num.intValue());
        }
        this.mNetworkListSharedStoreData.setConfigurations(sharedConfigurations);
        this.mNetworkListUserStoreData.setConfigurations(userConfigurations);
        this.mDeletedEphemeralSsidsStoreData.setSsidToTimeMap(this.mDeletedEphemeralSsidsToTimeMap);
        this.mRandomizedMacStoreData.setMacMapping(this.mRandomizedMacAddressMapping);
        try {
            this.mWifiConfigStore.write(forceWrite);
            return true;
        } catch (IOException e) {
            Log.wtf(TAG, "Writing to store failed. Saved networks maybe lost!", e);
            return false;
        } catch (XmlPullParserException e2) {
            Log.wtf(TAG, "XML serialization for store failed. Saved networks maybe lost!", e2);
            return false;
        }
    }

    public void setCurrentNetworkId(int netId) {
        this.mCurrentNetId = netId;
    }

    private boolean cleanOldNetworks(List<WifiConfiguration> configs) {
        Collections.sort(configs, new Comparator<WifiConfiguration>() {
            /* class com.android.server.wifi.WifiConfigManager.C04296 */

            public int compare(WifiConfiguration c1, WifiConfiguration c2) {
                if (c1.semIsVendorSpecificSsid != c2.semIsVendorSpecificSsid) {
                    return (!c1.semIsVendorSpecificSsid || c2.semIsVendorSpecificSsid) ? -1 : 1;
                }
                if (WifiConfigManager.this.mCurrentNetId != -1) {
                    if (c1.networkId == WifiConfigManager.this.mCurrentNetId) {
                        return 1;
                    }
                    if (c2.networkId == WifiConfigManager.this.mCurrentNetId) {
                        return -1;
                    }
                }
                if (c1.status == 0 && c2.status != 0) {
                    return 1;
                }
                if (c1.status != 0 && c2.status == 0) {
                    return -1;
                }
                if (c1.priority != c2.priority) {
                    return c1.priority > c2.priority ? 1 : -1;
                }
                long t1 = WifiConfigManager.this.getUsedTime(c1);
                long t2 = WifiConfigManager.this.getUsedTime(c2);
                if (t1 != t2) {
                    return t1 < t2 ? -1 : 1;
                }
                if (c1.getAuthType(false) == c2.getAuthType(false)) {
                    return c1.numAssociation - c2.numAssociation;
                }
                return c1.getAuthType(false) - c2.getAuthType(false);
            }
        });
        try {
            List<WifiConfiguration> oldConfigs = configs.subList(0, configs.size() - CSC_DEFAULT_MAX_NETWORKS_FOR_CURRENT_USER);
            if (oldConfigs == null) {
                Log.e(TAG, "cleanOldNetworks oldConfigs is null");
                return false;
            }
            for (WifiConfiguration config : oldConfigs) {
                String key = config.configKey();
                int netId = config.networkId;
                if (this.mVerboseLoggingEnabled) {
                    localLog("cleanOldNetworks key=" + key + " netId=" + netId);
                }
                if (!semRemoveNetwork(netId)) {
                    Log.e(TAG, "cleanOldNetworks Failed to forget network " + netId);
                    return false;
                }
            }
            saveToStore(true);
            return true;
        } catch (IndexOutOfBoundsException ie) {
            ie.printStackTrace();
            return false;
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private long getUsedTime(WifiConfiguration config) {
        if (config.lastConnected != 0) {
            return config.lastConnected;
        }
        if (config.semUpdateTime != 0) {
            return config.semUpdateTime;
        }
        if (config.semCreationTime != 0) {
            return config.semCreationTime;
        }
        return 0;
    }

    public int getAutoReconnect(int netId) {
        if (DBG) {
            Log.d(TAG, "getAutoReconnect, netID: " + netId);
        }
        WifiConfiguration config = getInternalConfiguredNetwork(netId);
        if (config != null) {
            return config.semAutoReconnect;
        }
        Log.e(TAG, "Failed to get AutoReconnect config is null. return 1...");
        return 1;
    }

    public void setAutoReconnect(int netId, int autoReconnect) {
        if (DBG) {
            Log.d(TAG, "setAutoReconnect, netID: " + netId + ", autoReconnect: " + autoReconnect);
        }
        WifiConfiguration config = getInternalConfiguredNetwork(netId);
        if (config == null) {
            Log.e(TAG, "setAutoReconnect, Failed to set autoReconnect. config is null.");
            return;
        }
        config.semAutoReconnect = autoReconnect;
        addOrUpdateNetwork(config, config.creatorUid);
    }

    private void localLog(String s) {
        LocalLog localLog = this.mLocalLog;
        if (localLog != null) {
            localLog.log(s);
        }
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("Dump of WifiConfigManager");
        pw.println("WifiConfigManager - Log Begin ----");
        this.mLocalLog.dump(fd, pw, args);
        pw.println("WifiConfigManager - Log End ----");
        pw.println("WifiConfigManager - Configured networks Begin ----");
        for (WifiConfiguration network : getInternalConfiguredNetworks()) {
            pw.println(network);
        }
        pw.println("WifiConfigManager - Configured networks End ----");
        pw.println("WifiConfigManager - Next network ID to be allocated " + this.mNextNetworkId);
        pw.println("WifiConfigManager - Last selected network ID " + this.mLastSelectedNetworkId);
        pw.println("WifiConfigManager - PNO scan frequency culling enabled = " + this.mPnoFrequencyCullingEnabled);
        pw.println("WifiConfigManager - PNO scan recency sorting enabled = " + this.mPnoRecencySortingEnabled);
        this.mWifiConfigStore.dump(fd, pw, args);
        pw.println("WifiConfigManager - Auto connect carrier AP enabled? " + this.mAutoConnectCarrierApEnabled);
    }

    private boolean canModifyProxySettings(int uid) {
        DevicePolicyManagerInternal dpmi = this.mWifiPermissionsWrapper.getDevicePolicyManagerInternal();
        boolean isUidProfileOwner = dpmi != null && dpmi.isActiveAdminWithPolicy(uid, -1);
        boolean isUidDeviceOwner = dpmi != null && dpmi.isActiveAdminWithPolicy(uid, -2);
        boolean hasNetworkSettingsPermission = this.mWifiPermissionsUtil.checkNetworkSettingsPermission(uid);
        boolean hasNetworkSetupWizardPermission = this.mWifiPermissionsUtil.checkNetworkSetupWizardPermission(uid);
        if (isUidDeviceOwner || isUidProfileOwner || hasNetworkSettingsPermission || hasNetworkSetupWizardPermission) {
            return true;
        }
        if (this.mVerboseLoggingEnabled) {
            Log.v(TAG, "UID: " + uid + " cannot modify WifiConfiguration proxy settings. hasNetworkSettings=" + hasNetworkSettingsPermission + " hasNetworkSetupWizard=" + hasNetworkSetupWizardPermission + " DeviceOwner=" + isUidDeviceOwner + " ProfileOwner=" + isUidProfileOwner);
        }
        return false;
    }

    public void setOnSavedNetworkUpdateListener(OnSavedNetworkUpdateListener listener) {
        this.mListener = listener;
    }

    public void setRecentFailureAssociationStatus(int netId, int reason) {
        WifiConfiguration config = getInternalConfiguredNetwork(netId);
        if (config != null) {
            config.recentFailure.setAssociationStatus(reason);
        }
    }

    public void clearRecentFailureReason(int netId) {
        WifiConfiguration config = getInternalConfiguredNetwork(netId);
        if (config != null) {
            config.recentFailure.clear();
        }
    }

    /* access modifiers changed from: package-private */
    public boolean updateBssidWhitelist(WifiConfiguration config, List<ScanResult> scanResults) {
        if (config == null) {
            Log.e(TAG, "updateBssidWhitelist: config is null");
            return false;
        }
        String ssid = config.getPrintableSsid();
        if (ssid == null || ((!ssid.equals("iptime") && !ssid.equals("iptime5G")) || !WifiConfigurationUtil.isConfigForOpenNetwork(config))) {
            return false;
        }
        if (scanResults == null || scanResults.size() <= 0) {
            return true;
        }
        long currentTime = System.currentTimeMillis();
        for (ScanResult scanResult : scanResults) {
            if (ScanResultMatchInfo.fromScanResult(scanResult).equals(ScanResultMatchInfo.fromWifiConfiguration(config)) && config.bssidWhitelist != null) {
                config.bssidWhitelist.put(scanResult.BSSID, Long.valueOf(currentTime));
                Log.d(TAG, "updateBssidWhitelist: " + scanResult.BSSID + " is added in whitelist");
            }
        }
        return true;
    }

    public boolean getNetworkAutoConnectEnabled() {
        return this.mNetworkAutoConnectEnabled;
    }

    public void setNetworkAutoConnect(boolean enabled) {
        this.mNetworkAutoConnectEnabled = enabled;
    }

    public boolean getAutoConnectCarrierApEnabled() {
        return this.mAutoConnectCarrierApEnabled;
    }

    public void setAutoConnectCarrierApEnabled(boolean enabled) {
        this.mAutoConnectCarrierApEnabled = enabled;
    }

    public boolean setCaptivePortal(int networkId, boolean captivePortal) {
        WifiConfiguration config = getInternalConfiguredNetwork(networkId);
        if (config == null) {
            return false;
        }
        config.isCaptivePortal = captivePortal;
        saveToStore(false);
        return true;
    }

    public boolean isSkipInternetCheck(int netId) {
        WifiConfiguration config = getInternalConfiguredNetwork(netId);
        if (config != null && config.skipInternetCheck == 1) {
            return true;
        }
        return false;
    }

    private WifiConfiguration canAddOrUpdateConfig(WifiConfiguration config, int callingUid) {
        String netSSID;
        boolean configModified;
        WifiConfiguration config2;
        if (config.networkId == -1 && !this.mWifiPolicy.getAllowUserProfiles(false, UserHandle.getUserId(callingUid))) {
            Log.e(TAG, "Network addition not allowed. networkId is invalid.");
            return null;
        } else if (config.SSID == null && getInternalConfiguredNetwork(config.networkId) == null) {
            Log.e(TAG, "Network addition not allowed. Could not determine isEnterpriseNetwork or not");
            return null;
        } else {
            if (config.SSID == null) {
                netSSID = getInternalConfiguredNetwork(config.networkId).SSID;
            } else {
                netSSID = config.SSID;
            }
            if (!WifiPolicyCache.getInstance(this.mContext).isEnterpriseNetwork(netSSID)) {
                return config;
            }
            if (!config.semSamsungSpecificFlags.get(4)) {
                Log.e(TAG, "cannot add same as mdm");
                return null;
            } else if (config.allowedKeyManagement.isEmpty()) {
                Log.v(TAG, "Updating priority");
                if (getInternalConfiguredNetwork(config.networkId) == null) {
                    Log.e(TAG, "Network addition not allowed. Could not compare two priorities");
                    return null;
                }
                int edmPriority = getInternalConfiguredNetwork(config.networkId).priority;
                WifiConfiguration newConfiguration = new WifiConfiguration();
                if (config.priority <= edmPriority) {
                    return null;
                }
                newConfiguration.priority = edmPriority;
                newConfiguration.networkId = config.networkId;
                newConfiguration.creatorUid = config.creatorUid;
                newConfiguration.creatorName = config.creatorName;
                newConfiguration.lastUpdateUid = config.lastUpdateUid;
                newConfiguration.lastUpdateName = config.lastUpdateName;
                newConfiguration.semSamsungSpecificFlags.set(4);
                return newConfiguration;
            } else {
                Log.v(TAG, "Updating profile configuration");
                int security = WifiPolicy.getLinkSecurity(config);
                WifiConfiguration edmConfig = null;
                List<WifiConfiguration> list = getSavedNetworks(1010);
                if (list != null) {
                    Iterator<WifiConfiguration> it = list.iterator();
                    while (true) {
                        if (!it.hasNext()) {
                            break;
                        }
                        WifiConfiguration auxNet = it.next();
                        if (auxNet.SSID.equals(netSSID)) {
                            edmConfig = auxNet;
                            break;
                        }
                    }
                }
                if (edmConfig == null) {
                    return null;
                }
                if (!WifiPolicyCache.getInstance(this.mContext).getAllowUserChanges()) {
                    WifiConfiguration config3 = WifiPolicyCache.getInstance(this.mContext).updateAllowedFields(config, edmConfig, security);
                    if (config3 == null) {
                        Log.e(TAG, "profile update not allowed");
                        return config3;
                    }
                    configModified = true;
                    config2 = config3;
                } else {
                    configModified = false;
                    config2 = config;
                }
                String engine_id = edmConfig.enterpriseConfig.getEngineId();
                if ((!TextUtils.isEmpty(engine_id) && ("secpkcs11".equals(engine_id) || "ucsengine".equals(engine_id))) || configModified) {
                    Log.v(TAG, "edmaddorupdate - engine is ccm or modified");
                    WifiEnterpriseConfig newEnterpriseConfig = new WifiEnterpriseConfig();
                    WifiEnterpriseConfig oldEnterpriseConfig = config2.enterpriseConfig;
                    if (!TextUtils.isEmpty(engine_id) && "secpkcs11".equals(engine_id)) {
                        newEnterpriseConfig.setCCMEnabled(true);
                        String caCertAlias = edmConfig.enterpriseConfig.getCaCertificateAlias();
                        if (!TextUtils.isEmpty(caCertAlias)) {
                            oldEnterpriseConfig.setCaCertificateAlias(caCertAlias);
                        }
                        String clientCertAlias = edmConfig.enterpriseConfig.getClientCertificateAlias();
                        if (!TextUtils.isEmpty(clientCertAlias)) {
                            oldEnterpriseConfig.setClientCertificateAlias(clientCertAlias);
                        }
                    } else if (!TextUtils.isEmpty(engine_id) && "ucsengine".equals(engine_id)) {
                        newEnterpriseConfig.setUCMEnabled(true);
                        String caCertAlias2 = edmConfig.enterpriseConfig.getCaCertificateAlias();
                        if (!TextUtils.isEmpty(caCertAlias2)) {
                            oldEnterpriseConfig.setCaCertificateAlias(caCertAlias2);
                        }
                        String clientCertAlias2 = edmConfig.enterpriseConfig.getClientCertificateAlias();
                        if (!TextUtils.isEmpty(clientCertAlias2)) {
                            oldEnterpriseConfig.setClientCertificateAlias(clientCertAlias2);
                        }
                    }
                    newEnterpriseConfig.setAnonymousIdentity(oldEnterpriseConfig.getAnonymousIdentity());
                    if (!TextUtils.isEmpty(oldEnterpriseConfig.getCaCertificateAlias())) {
                        newEnterpriseConfig.setCaCertificateAlias(oldEnterpriseConfig.getCaCertificateAlias());
                    }
                    newEnterpriseConfig.setCaCertificate(oldEnterpriseConfig.getCaCertificate());
                    if (!TextUtils.isEmpty(oldEnterpriseConfig.getClientCertificateAlias())) {
                        newEnterpriseConfig.setClientCertificateAlias(oldEnterpriseConfig.getClientCertificateAlias());
                    }
                    if (oldEnterpriseConfig.getEapMethod() > -1) {
                        newEnterpriseConfig.setEapMethod(oldEnterpriseConfig.getEapMethod());
                    }
                    newEnterpriseConfig.setIdentity(oldEnterpriseConfig.getIdentity());
                    if (!TextUtils.isEmpty(oldEnterpriseConfig.getPassword())) {
                        newEnterpriseConfig.setPassword(oldEnterpriseConfig.getPassword());
                    }
                    int phase1 = -1;
                    try {
                        phase1 = Integer.parseInt(oldEnterpriseConfig.getPhase1Method());
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "error converting phase1 - edmaddorupdate");
                    }
                    newEnterpriseConfig.setPhase1Method(phase1);
                    if (oldEnterpriseConfig.getPhase2Method() >= 0) {
                        newEnterpriseConfig.setPhase2Method(oldEnterpriseConfig.getPhase2Method());
                    }
                    newEnterpriseConfig.setSubjectMatch(oldEnterpriseConfig.getSubjectMatch());
                    newEnterpriseConfig.setPacFile(oldEnterpriseConfig.getPacFile());
                    if (!TextUtils.isEmpty(oldEnterpriseConfig.getSimNumber())) {
                        try {
                            newEnterpriseConfig.setSimNumber(Integer.parseInt(oldEnterpriseConfig.getSimNumber()));
                        } catch (NumberFormatException e2) {
                            Log.e(TAG, "error converting SimNumber - edmaddorupdate");
                        }
                    }
                    newEnterpriseConfig.setWapiASCertificateAlias(oldEnterpriseConfig.getWapiASCertificateAlias());
                    newEnterpriseConfig.setWapiUserCertificateAlias(oldEnterpriseConfig.getWapiUserCertificateAlias());
                    config2.enterpriseConfig = newEnterpriseConfig;
                }
                this.mWifiPolicy.edmAddOrUpdate(config2, netSSID);
                return config2;
            }
        }
    }

    private boolean canRemoveMDMNetwork(int networkId) {
        WifiConfiguration config = getInternalConfiguredNetwork(networkId);
        if (config == null) {
            Log.w(TAG, "canRemoveMDMNetwork: networkId = " + networkId + " but config is null");
            return true;
        }
        String auxSSID = config.SSID;
        if (!WifiPolicyCache.getInstance(this.mContext).isEnterpriseNetwork(auxSSID)) {
            return true;
        }
        Log.v(TAG, "user is trying to remove enterprise network");
        if (!WifiPolicyCache.getInstance(this.mContext).getAllowUserChanges()) {
            return false;
        }
        this.mWifiPolicy.removeNetworkConfiguration(auxSSID);
        return true;
    }

    public int getEntryRssi24GHz(int networkId) {
        WifiConfiguration config = getInternalConfiguredNetwork(networkId);
        if (config != null) {
            return config.entryRssi24GHz;
        }
        Log.e(TAG, "Failed to find network with networkId " + networkId + " return invalid rssi...");
        return WifiConfiguration.INVALID_RSSI;
    }

    public int getEntryRssi5GHz(int networkId) {
        WifiConfiguration config = getInternalConfiguredNetwork(networkId);
        if (config != null) {
            return config.entryRssi5GHz;
        }
        Log.e(TAG, "Failed to find network with networkId " + networkId + " return invalid rssi...");
        return WifiConfiguration.INVALID_RSSI;
    }

    public boolean resetEntryRssi(int networkId) {
        if (this.mVerboseLoggingEnabled) {
            Log.v(TAG, "Reset entry rssi for " + networkId);
        }
        WifiConfiguration config = getInternalConfiguredNetwork(networkId);
        if (config == null) {
            return false;
        }
        config.entryRssi24GHz = -78;
        config.entryRssi5GHz = -75;
        saveToStore(false);
        localLog("resetEntryRssi:  netId=" + config.networkId + " configKey=" + config.configKey());
        return true;
    }

    public boolean increaseEntryRssi(int networkId) {
        return increaseEntryRssi(getInternalConfiguredNetwork(networkId));
    }

    private boolean increaseEntryRssi(WifiConfiguration config) {
        if (config == null) {
            return false;
        }
        if (this.mVerboseLoggingEnabled) {
            Log.v(TAG, "Increase entry rssi for " + config.networkId);
        }
        if (!canModifyEntryRssi(config)) {
            return false;
        }
        int increasedEntryRssi24GHz = config.entryRssi24GHz + 5;
        if (increasedEntryRssi24GHz <= -68) {
            config.entryRssi24GHz = increasedEntryRssi24GHz;
        }
        int increasedEntryRssi5GHz = config.entryRssi5GHz + 5;
        if (increasedEntryRssi5GHz <= -65) {
            config.entryRssi5GHz = increasedEntryRssi5GHz;
        }
        if (this.mVerboseLoggingEnabled) {
            Log.v(TAG, "    entryRssi24GHz: " + config.entryRssi24GHz);
            Log.v(TAG, "    entryRssi5GHz: " + config.entryRssi5GHz);
        }
        saveToStore(false);
        localLog("increaseEntryRssi:  netId=" + config.networkId + " configKey=" + config.configKey() + " entryRssi24GHz=" + config.entryRssi24GHz + " entryRssi5GHz=" + config.entryRssi5GHz);
        return true;
    }

    public boolean decreaseEntryRssi(int networkId) {
        return decreaseEntryRssi(getInternalConfiguredNetwork(networkId));
    }

    private boolean decreaseEntryRssi(WifiConfiguration config) {
        if (config == null) {
            return false;
        }
        if (this.mVerboseLoggingEnabled) {
            Log.v(TAG, "Decrease entry rssi for " + config.networkId);
        }
        if (!canModifyEntryRssi(config)) {
            return false;
        }
        int decreasedEntryRssi24GHz = config.entryRssi24GHz - 5;
        if (-75 <= decreasedEntryRssi24GHz) {
            config.entryRssi24GHz = decreasedEntryRssi24GHz;
        }
        int decreasedEntryRssi5GHz = config.entryRssi5GHz - 5;
        if (-75 <= decreasedEntryRssi5GHz) {
            config.entryRssi5GHz = decreasedEntryRssi5GHz;
        }
        if (this.mVerboseLoggingEnabled) {
            Log.v(TAG, "    entryRssi24GHz: " + config.entryRssi24GHz);
            Log.v(TAG, "    entryRssi5GHz: " + config.entryRssi5GHz);
        }
        saveToStore(false);
        localLog("decreaseEntryRssi:  netId=" + config.networkId + " configKey=" + config.configKey() + " entryRssi24GHz=" + config.entryRssi24GHz + " entryRssi5GHz=" + config.entryRssi5GHz);
        return true;
    }

    private boolean canModifyEntryRssi(WifiConfiguration config) {
        if (config.semAutoWifiScore <= 2) {
            return true;
        }
        if (!this.mVerboseLoggingEnabled) {
            return false;
        }
        Log.v(TAG, "canModifyEntryRssi: semAutoWifiScore = " + config.semAutoWifiScore);
        return false;
    }

    public boolean isCarrierNetworkSaved() {
        localLog("isCarrierNetworkSaved " + this.mIsCarrierNetworkSaved);
        return this.mIsCarrierNetworkSaved;
    }

    /* JADX WARNING: Code restructure failed: missing block: B:22:0x008a, code lost:
        r3 = true;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:25:?, code lost:
        r4.close();
     */
    private boolean isRemovedVedorAp(WifiConfiguration config) {
        BufferedReader in = null;
        Boolean ret = false;
        try {
            BufferedReader in2 = new BufferedReader(new FileReader(this.mFilePathRemovedNwInfo));
            String ssidbuf = "";
            String keymgmtbuf = "";
            while (true) {
                String bufLine = in2.readLine();
                if (bufLine != null) {
                    String bufLine2 = bufLine.trim();
                    if (bufLine2.startsWith("ssid")) {
                        ssidbuf = bufLine2.substring(bufLine2.indexOf(61) + 1);
                    }
                    if (bufLine2.startsWith(WifiBackupRestore.SupplicantBackupMigration.SUPPLICANT_KEY_KEY_MGMT)) {
                        keymgmtbuf = bufLine2.substring(bufLine2.indexOf(61) + 1);
                    }
                    if (bufLine2.startsWith(WifiBackupRestore.SupplicantBackupMigration.SUPPLICANT_KEY_EAP)) {
                        String eap = bufLine2.substring(bufLine2.indexOf(61) + 1);
                        if (!ssidbuf.equals(config.SSID) || !keymgmtbuf.equals(StringUtil.makeString(config.allowedKeyManagement, WifiConfiguration.KeyMgmt.strings)) || (!"OLD_VERSION".equals(eap) && !eap.equals(StringUtil.makeStringEapMethod(config)))) {
                            ssidbuf = "";
                            keymgmtbuf = "";
                        }
                    }
                }
                try {
                    break;
                } catch (IOException e) {
                    e = e;
                }
            }
        } catch (IOException e2) {
            Log.e(TAG, e2.toString());
            if (0 != 0) {
                try {
                    in.close();
                } catch (IOException e3) {
                    e = e3;
                }
            }
        } catch (Throwable th) {
            if (0 != 0) {
                try {
                    in.close();
                } catch (IOException e4) {
                    e = e4;
                }
            }
        }
        return ret.booleanValue();
        Log.e(TAG, e.toString());
        return ret.booleanValue();
    }

    private void deleteRemovedVedorAp(WifiConfiguration config) {
        StringBuilder tempString = new StringBuilder();
        boolean removeThisBlock = false;
        FileWriter fw = null;
        BufferedReader in = null;
        try {
            BufferedReader in2 = new BufferedReader(new FileReader(this.mFilePathRemovedNwInfo));
            while (true) {
                String bufLine = in2.readLine();
                if (bufLine == null) {
                    Log.i(TAG, "User adds the removed AP: deleteRemovedVedorAp " + config.getPrintableSsid());
                    FileWriter fw2 = new FileWriter(this.mFilePathRemovedNwInfo);
                    fw2.write(tempString.toString());
                    fw2.flush();
                    try {
                        fw2.close();
                        in2.close();
                        return;
                    } catch (IOException e2) {
                        Log.e(TAG, e2.toString());
                        return;
                    }
                } else if (!bufLine.contains("network=")) {
                    if (bufLine.contains(config.getPrintableSsid())) {
                        removeThisBlock = true;
                    } else {
                        if (removeThisBlock) {
                            if (!bufLine.contains("key_mgmt=") && !bufLine.contains("eap=")) {
                                if (bufLine.contains("}")) {
                                }
                            }
                        }
                        if (bufLine.contains("ssid=")) {
                            tempString.append("network={\n");
                            removeThisBlock = false;
                        }
                        tempString.append(bufLine);
                        tempString.append("\n");
                    }
                }
            }
        } catch (FileNotFoundException e) {
            Log.e(TAG, "File not found");
            fw.close();
            in.close();
        } catch (IOException e3) {
            Log.e(TAG, "Exception :" + e3.getMessage());
            fw.close();
            in.close();
        } catch (Throwable th) {
            try {
                fw.close();
                in.close();
            } catch (IOException e22) {
                Log.e(TAG, e22.toString());
            }
            throw th;
        }
    }

    /* access modifiers changed from: package-private */
    public boolean isVendorSpecificSsid(int netId) {
        WifiConfiguration config = this.mConfiguredNetworks.getForCurrentUser(netId);
        if (config != null) {
            return config.semIsVendorSpecificSsid;
        }
        return false;
    }

    public void semRemoveUnneccessaryNetworks() {
        List<Integer> verizonWifiConfigs = new ArrayList<>();
        for (WifiConfiguration config : this.mConfiguredNetworks.valuesForAllUsers()) {
            if ("VerizonWiFi".equals(config.SSID) || "\"VerizonWiFi\"".equals(config.SSID)) {
                verizonWifiConfigs.add(Integer.valueOf(config.networkId));
            }
        }
        for (Integer num : verizonWifiConfigs) {
            int networkId = num.intValue();
            Log.d(TAG, "remove unneccessary network id : " + networkId);
            this.mConfiguredNetworks.remove(networkId);
        }
    }

    public void semStopToSaveStore() {
        this.mWifiConfigStore.semStopToSaveStore();
    }

    public void setUserSelectNetwork(boolean userSelect) {
        this.mUserSelectNetwork = userSelect;
    }
}
