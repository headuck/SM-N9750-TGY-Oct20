package com.android.server.wifi;

import android.content.Context;
import android.database.ContentObserver;
import android.hardware.wifi.supplicant.V1_0.ISupplicantNetwork;
import android.hardware.wifi.supplicant.V1_0.ISupplicantStaNetwork;
import android.hardware.wifi.supplicant.V1_0.ISupplicantStaNetworkCallback;
import android.hardware.wifi.supplicant.V1_0.SupplicantStatus;
import android.hardware.wifi.supplicant.V1_2.ISupplicantStaNetwork;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiEnterpriseConfig;
import android.os.HidlSupport;
import android.os.IHwInterface;
import android.os.RemoteException;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.util.MutableBoolean;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.ArrayUtils;
import com.android.server.wifi.WifiBackupRestore;
import com.android.server.wifi.hotspot2.NetworkDetail;
import com.android.server.wifi.util.NativeUtil;
import com.samsung.android.feature.SemCscFeature;
import com.samsung.android.net.wifi.OpBrandingLoader;
import com.samsung.android.server.wifi.mobilewips.framework.MobileWipsFrameworkService;
import com.samsung.android.server.wifi.share.McfDataUtil;
import com.sec.android.app.CscFeatureTagWifi;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.concurrent.ThreadSafe;
import org.json.JSONException;
import org.json.JSONObject;
import vendor.samsung.hardware.wifi.supplicant.V2_0.ISehSupplicantStaNetwork;

@ThreadSafe
public class SupplicantStaNetworkHal {
    private static final String CHARSET_CN = "gbk";
    private static final String CHARSET_KOR = "ksc5601";
    private static final String CONFIG_CHARSET = OpBrandingLoader.getInstance().getSupportCharacterSet();
    private static final String CONFIG_SECURE_SVC_INTEGRATION = SemCscFeature.getInstance().getString(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_CONFIGSECURESVCINTEGRATION);
    private static final Pattern GSM_AUTH_RESPONSE_PARAMS_PATTERN = Pattern.compile(":([0-9a-fA-F]+):([0-9a-fA-F]+)");
    @VisibleForTesting
    public static final String ID_STRING_KEY_CONFIG_KEY = "configKey";
    @VisibleForTesting
    public static final String ID_STRING_KEY_CREATOR_UID = "creatorUid";
    @VisibleForTesting
    public static final String ID_STRING_KEY_FQDN = "fqdn";
    private static final String PAC_FILE_PATH = "/data/vendor/wifi/wpa/wpa_supplicant.pac";
    private static final String TAG = "SupplicantStaNetworkHal";
    private static final Pattern UMTS_AUTH_RESPONSE_PARAMS_PATTERN = Pattern.compile("^:([0-9a-fA-F]+):([0-9a-fA-F]+):([0-9a-fA-F]+)$");
    private static final Pattern UMTS_AUTS_RESPONSE_PARAMS_PATTERN = Pattern.compile("^:([0-9a-fA-F]+)$");
    private int mAuthAlgMask;
    private int mAutoReconnect;
    private byte[] mBssid;
    private Context mContext;
    private String mEapAltSubjectMatch;
    private ArrayList<Byte> mEapAnonymousIdentity;
    private String mEapCACert;
    private String mEapCAPath;
    private String mEapClientCert;
    private String mEapDomainSuffixMatch;
    private boolean mEapEngine;
    private String mEapEngineID;
    private ArrayList<Byte> mEapIdentity;
    private int mEapMethod;
    private String mEapPacFile;
    private ArrayList<Byte> mEapPassword;
    private int mEapPhase1Method;
    private int mEapPhase2Method;
    private String mEapPrivateKeyId;
    private String mEapSubjectMatch;
    private int mGroupCipherMask;
    private int mGroupMgmtCipherMask;
    private ISehSupplicantStaNetwork mISehSupplicantStaNetwork;
    private ISupplicantStaNetwork mISupplicantStaNetwork;
    private ISupplicantStaNetworkCallback mISupplicantStaNetworkCallback;
    private String mIdStr;
    private final String mIfaceName;
    private int mKeyMgmtMask;
    private final Object mLock = new Object();
    private int mNetworkId;
    private int mPairwiseCipherMask;
    private int mProtoMask;
    private byte[] mPsk;
    private String mPskPassphrase;
    private boolean mRequirePmf;
    private String mSaePassword;
    private String mSaePasswordId;
    private boolean mScanSsid;
    private int mSimNumber;
    private ArrayList<Byte> mSsid;
    private boolean mSystemSupportsFastBssTransition = false;
    private boolean mSystemSupportsFilsKeyMgmt = false;
    private boolean mVendorSsid;
    private boolean mVerboseLoggingEnabled = false;
    private String mWapiAsCert;
    private int mWapiCertIndex;
    private int mWapiPskType;
    private String mWapiUserCert;
    private ArrayList<Byte> mWepKey;
    private int mWepTxKeyIdx;
    private final WifiMonitor mWifiMonitor;

    SupplicantStaNetworkHal(ISupplicantStaNetwork iSupplicantStaNetwork, ISehSupplicantStaNetwork iSehSupplicantStaNetwork, String ifaceName, Context context, WifiMonitor monitor) {
        this.mISupplicantStaNetwork = iSupplicantStaNetwork;
        this.mISehSupplicantStaNetwork = iSehSupplicantStaNetwork;
        this.mIfaceName = ifaceName;
        this.mContext = context;
        this.mWifiMonitor = monitor;
        this.mSystemSupportsFastBssTransition = true;
        boolean needToRegisterObserverForWifiSafeMode = false;
        if ("1".equals("2")) {
            this.mSystemSupportsFilsKeyMgmt = true;
        } else if ("2".equals("2")) {
            this.mSystemSupportsFilsKeyMgmt = true ^ getWifiSafeModeFromDb();
            needToRegisterObserverForWifiSafeMode = true;
        }
        if (needToRegisterObserverForWifiSafeMode) {
            context.getContentResolver().registerContentObserver(Settings.Global.getUriFor("safe_wifi"), false, new ContentObserver(null) {
                /* class com.android.server.wifi.SupplicantStaNetworkHal.C04151 */

                public void onChange(boolean selfChange) {
                    if ("2".equals("2")) {
                        SupplicantStaNetworkHal supplicantStaNetworkHal = SupplicantStaNetworkHal.this;
                        supplicantStaNetworkHal.mSystemSupportsFilsKeyMgmt = !supplicantStaNetworkHal.getWifiSafeModeFromDb();
                    }
                }
            });
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean getWifiSafeModeFromDb() {
        return Settings.Global.getInt(this.mContext.getContentResolver(), "safe_wifi", 0) != 0;
    }

    /* access modifiers changed from: package-private */
    public void enableVerboseLogging(boolean enable) {
        synchronized (this.mLock) {
            this.mVerboseLoggingEnabled = enable;
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:111:0x020c  */
    /* JADX WARNING: Removed duplicated region for block: B:35:0x009f  */
    public boolean loadWifiConfiguration(WifiConfiguration config, Map<String, String> networkExtras) {
        synchronized (this.mLock) {
            if (config == null) {
                return false;
            }
            config.SSID = null;
            if (!getSsid() || ArrayUtils.isEmpty(this.mSsid)) {
                Log.e(TAG, "failed to read ssid");
                return false;
            }
            if (!CHARSET_CN.equals(CONFIG_CHARSET)) {
                if (!CHARSET_KOR.equals(CONFIG_CHARSET)) {
                    config.SSID = NativeUtil.encodeSsid(this.mSsid);
                    config.networkId = -1;
                    if (!getId()) {
                        config.networkId = this.mNetworkId;
                        config.getNetworkSelectionStatus().setNetworkSelectionBSSID((String) null);
                        if (getBssid() && !ArrayUtils.isEmpty(this.mBssid)) {
                            config.getNetworkSelectionStatus().setNetworkSelectionBSSID(NativeUtil.macAddressFromByteArray(this.mBssid));
                        }
                        config.hiddenSSID = false;
                        if (getScanSsid()) {
                            config.hiddenSSID = this.mScanSsid;
                        }
                        config.requirePMF = false;
                        if (getRequirePmf()) {
                            config.requirePMF = this.mRequirePmf;
                        }
                        config.wepTxKeyIndex = -1;
                        if (getWepTxKeyIdx()) {
                            config.wepTxKeyIndex = this.mWepTxKeyIdx;
                        }
                        for (int i = 0; i < 4; i++) {
                            config.wepKeys[i] = null;
                            if (getWepKey(i) && !ArrayUtils.isEmpty(this.mWepKey)) {
                                config.wepKeys[i] = NativeUtil.bytesToHexOrQuotedString(this.mWepKey);
                            }
                        }
                        config.preSharedKey = null;
                        if (getPskPassphrase() && !TextUtils.isEmpty(this.mPskPassphrase)) {
                            config.preSharedKey = NativeUtil.addEnclosingQuotes(this.mPskPassphrase);
                        } else if (getPsk() && !ArrayUtils.isEmpty(this.mPsk)) {
                            config.preSharedKey = NativeUtil.hexStringFromByteArray(this.mPsk);
                        }
                        if (getKeyMgmt()) {
                            config.allowedKeyManagement = removeFastTransitionFlags(supplicantToWifiConfigurationKeyMgmtMask(this.mKeyMgmtMask));
                            config.allowedKeyManagement = removeSha256KeyMgmtFlags(config.allowedKeyManagement);
                            config.allowedKeyManagement = removeFils256KeyMgmtFlags(config.allowedKeyManagement);
                        }
                        if (getProto()) {
                            config.allowedProtocols = supplicantToWifiConfigurationProtoMask(this.mProtoMask);
                        }
                        if (getAuthAlg()) {
                            config.allowedAuthAlgorithms = supplicantToWifiConfigurationAuthAlgMask(this.mAuthAlgMask);
                        }
                        if (getGroupCipher()) {
                            config.allowedGroupCiphers = supplicantToWifiConfigurationGroupCipherMask(this.mGroupCipherMask);
                        }
                        if (getPairwiseCipher()) {
                            config.allowedPairwiseCiphers = supplicantToWifiConfigurationPairwiseCipherMask(this.mPairwiseCipherMask);
                        }
                        if (getGroupMgmtCipher()) {
                            config.allowedGroupManagementCiphers = supplicantToWifiConfigurationGroupMgmtCipherMask(this.mGroupMgmtCipherMask);
                        }
                        if (!getIdStr() || TextUtils.isEmpty(this.mIdStr)) {
                            Log.w(TAG, "getIdStr failed or empty");
                        } else {
                            networkExtras.putAll(parseNetworkExtra(this.mIdStr));
                        }
                        config.wapiPskType = -1;
                        if (getWapiPskType()) {
                            config.wapiPskType = this.mWapiPskType;
                        }
                        config.wapiCertIndex = -1;
                        if (getWapiCertFormat()) {
                            config.wapiCertIndex = this.mWapiCertIndex;
                        }
                        config.wapiAsCert = null;
                        if (getWapiAsCert() && !TextUtils.isEmpty(this.mWapiAsCert)) {
                            config.wapiAsCert = this.mWapiAsCert;
                        }
                        config.wapiUserCert = null;
                        if (getWapiUserCert() && !TextUtils.isEmpty(this.mWapiUserCert)) {
                            config.wapiUserCert = this.mWapiUserCert;
                        }
                        return loadWifiEnterpriseConfig(config.SSID, config.enterpriseConfig);
                    }
                    Log.e(TAG, "getId failed");
                    return false;
                }
            }
            byte[] byteArray = NativeUtil.byteArrayFromArrayList(this.mSsid);
            String decodedSsid = null;
            if (!NativeUtil.isUTF8String(byteArray, (long) byteArray.length) && NativeUtil.isUCNVString(byteArray, byteArray.length)) {
                try {
                    decodedSsid = CHARSET_CN.equals(CONFIG_CHARSET) ? new String(byteArray, CHARSET_CN) : new String(byteArray, CHARSET_KOR);
                } catch (Exception e) {
                    Log.e(TAG, " loadWifiConfiguration, byteArray decode error  = " + e.toString());
                }
            }
            if (decodedSsid != null) {
                config.SSID = NativeUtil.addEnclosingQuotes(decodedSsid);
            } else {
                config.SSID = NativeUtil.encodeSsid(this.mSsid);
            }
            config.networkId = -1;
            if (!getId()) {
            }
        }
    }

    public boolean saveWifiConfiguration(WifiConfiguration config) {
        synchronized (this.mLock) {
            if (config == null) {
                return false;
            }
            if (config.SSID != null) {
                try {
                    if (!CHARSET_CN.equals(CONFIG_CHARSET)) {
                        if (!CHARSET_KOR.equals(CONFIG_CHARSET)) {
                            if (!setSsid(NativeUtil.decodeSsid(config.SSID))) {
                                Log.e(TAG, "failed to set SSID: " + config.SSID);
                                return false;
                            }
                        }
                    }
                    Map<String, NetworkDetail.NonUTF8Ssid> nonUTF8SsidLists = NetworkDetail.getNonUTF8SsidLists();
                    ArrayList<Byte> ssidByteList = null;
                    if (nonUTF8SsidLists != null && nonUTF8SsidLists.size() > 0) {
                        String bssidStr = config.getNetworkSelectionStatus().getNetworkSelectionBSSID();
                        byte[] nonUtf8Ssid = null;
                        if (bssidStr == null || "any".equals(bssidStr)) {
                            String convertSsid = removeDoubleQuotes(config.SSID);
                            Iterator<NetworkDetail.NonUTF8Ssid> it = nonUTF8SsidLists.values().iterator();
                            while (true) {
                                if (!it.hasNext()) {
                                    break;
                                }
                                NetworkDetail.NonUTF8Ssid ssids = it.next();
                                if (convertSsid != null && convertSsid.equals(ssids.ssid)) {
                                    Log.d(TAG, "target ssid " + config.SSID + " is NonUTF8 SSID");
                                    nonUtf8Ssid = ssids.ssidOctets;
                                    break;
                                }
                            }
                        } else {
                            NetworkDetail.NonUTF8Ssid ssids2 = nonUTF8SsidLists.get(bssidStr.toUpperCase());
                            if (ssids2 != null) {
                                Log.d(TAG, "target bssid " + bssidStr + " is NonUTF8 SSID");
                                nonUtf8Ssid = ssids2.ssidOctets;
                            }
                        }
                        if (nonUtf8Ssid != null) {
                            Log.d(TAG, " chage config.SSID " + config.SSID + ", to NonUTF8ssid " + nonUtf8Ssid);
                            ssidByteList = NativeUtil.byteArrayToArrayList(nonUtf8Ssid);
                        }
                    }
                    if (ssidByteList != null) {
                        if (!setSsid(ssidByteList)) {
                            Log.e(TAG, "failed to set SSID: " + config.SSID);
                            return false;
                        }
                    } else if (!setSsid(NativeUtil.decodeSsid(config.SSID))) {
                        Log.e(TAG, "failed to set SSID: " + config.SSID);
                        return false;
                    }
                } catch (IllegalArgumentException e) {
                    Log.e(TAG, "Illegal argument " + config.SSID, e);
                    return false;
                }
            }
            String bssidStr2 = config.getNetworkSelectionStatus().getNetworkSelectionBSSID();
            if (bssidStr2 == null || setBssid(NativeUtil.macAddressToByteArray(bssidStr2))) {
                if (config.preSharedKey != null) {
                    if (config.preSharedKey.startsWith("\"")) {
                        if (config.allowedKeyManagement.get(8)) {
                            if (!setSaePassword(NativeUtil.removeEnclosingQuotes(config.preSharedKey))) {
                                Log.e(TAG, "failed to set sae password");
                                return false;
                            }
                        } else if (!setPskPassphrase(NativeUtil.removeEnclosingQuotes(config.preSharedKey))) {
                            Log.e(TAG, "failed to set psk passphrase");
                            return false;
                        }
                    } else if (config.allowedKeyManagement.get(8)) {
                        return false;
                    } else {
                        try {
                            if (!setPsk(NativeUtil.hexStringToByteArray(config.preSharedKey))) {
                                Log.e(TAG, "failed to set psk");
                                return false;
                            }
                        } catch (IllegalArgumentException e2) {
                            Log.e(TAG, "saveWifiConfiguration: IllegalArgumentException " + e2);
                            return false;
                        }
                    }
                }
                boolean hasSetKey = false;
                if (config.wepKeys != null) {
                    for (int i = 0; i < config.wepKeys.length; i++) {
                        if (!(config.wepKeys[i] == null || config.wepKeys[i].length() == 0)) {
                            if (!setWepKey(i, NativeUtil.hexOrQuotedStringToBytes(config.wepKeys[i]))) {
                                Log.e(TAG, "failed to set wep_key " + i);
                                return false;
                            }
                            hasSetKey = true;
                        }
                    }
                }
                if (hasSetKey && !setWepTxKeyIdx(config.wepTxKeyIndex)) {
                    Log.e(TAG, "failed to set wep_tx_keyidx: " + config.wepTxKeyIndex);
                    return false;
                } else if (!setScanSsid(config.hiddenSSID)) {
                    Log.e(TAG, config.SSID + ": failed to set hiddenSSID: " + config.hiddenSSID);
                    return false;
                } else if (!setVendorSsid(config.semIsVendorSpecificSsid)) {
                    Log.e(TAG, config.SSID + ": failed to set hiddenSSID: " + config.semIsVendorSpecificSsid);
                    return false;
                } else if (!setRequirePmf(config.requirePMF)) {
                    Log.e(TAG, config.SSID + ": failed to set requirePMF: " + config.requirePMF);
                    return false;
                } else {
                    if (config.allowedKeyManagement.cardinality() != 0) {
                        BitSet keyMgmtMask = addFils256KeyMgmtFlags(addSha256KeyMgmtFlags(addFastTransitionFlags(config.allowedKeyManagement)));
                        if (!setKeyMgmt(wifiConfigurationToSupplicantKeyMgmtMask(keyMgmtMask))) {
                            Log.e(TAG, "failed to set Key Management");
                            return false;
                        } else if (keyMgmtMask.get(10) && !saveSuiteBConfig(config)) {
                            Log.e(TAG, "Failed to set Suite-B-192 configuration");
                            return false;
                        }
                    }
                    if ((config.allowedKeyManagement.get(23) || config.allowedKeyManagement.get(22)) && !config.allowedProtocols.get(3)) {
                        config.allowedProtocols.set(3);
                    }
                    if (config.allowedProtocols.cardinality() != 0 && !setProto(wifiConfigurationToSupplicantProtoMask(config.allowedProtocols))) {
                        Log.e(TAG, "failed to set Security Protocol");
                        return false;
                    } else if (config.allowedAuthAlgorithms.cardinality() != 0 && isAuthAlgNeeded(config) && !setAuthAlg(wifiConfigurationToSupplicantAuthAlgMask(config.allowedAuthAlgorithms))) {
                        Log.e(TAG, "failed to set AuthAlgorithm");
                        return false;
                    } else if (config.allowedGroupCiphers.cardinality() != 0 && !setGroupCipher(wifiConfigurationToSupplicantGroupCipherMask(config.allowedGroupCiphers))) {
                        Log.e(TAG, "failed to set Group Cipher");
                        return false;
                    } else if (config.allowedPairwiseCiphers.cardinality() == 0 || setPairwiseCipher(wifiConfigurationToSupplicantPairwiseCipherMask(config.allowedPairwiseCiphers))) {
                        Map<String, String> metadata = new HashMap<>();
                        if (config.isPasspoint()) {
                            metadata.put("fqdn", config.FQDN);
                        }
                        metadata.put("configKey", config.configKey());
                        metadata.put(ID_STRING_KEY_CREATOR_UID, Integer.toString(config.creatorUid));
                        if (!setIdStr(createNetworkExtra(metadata))) {
                            Log.e(TAG, "failed to set id string");
                            return false;
                        } else if (config.updateIdentifier == null || setUpdateIdentifier(Integer.parseInt(config.updateIdentifier))) {
                            if (config.allowedProtocols.get(3)) {
                                if (!setWapiPskType(config.wapiPskType)) {
                                    Log.e(TAG, config.SSID + ": failed to set wapiPskType: " + config.wapiPskType);
                                    return false;
                                } else if (!setWapiCertFormat(config.wapiCertIndex)) {
                                    Log.e(TAG, config.SSID + ": failed to set wapiCertIndex: " + config.wapiCertIndex);
                                    return false;
                                } else if (config.wapiAsCert != null && !setWapiAsCert(config.wapiAsCert)) {
                                    Log.e(TAG, config.SSID + ": failed to set wapiAsCert: " + config.wapiAsCert);
                                    return false;
                                } else if (config.wapiUserCert != null && !setWapiUserCert(config.wapiUserCert)) {
                                    Log.e(TAG, config.SSID + ": failed to set wapiUserCert: " + config.wapiUserCert);
                                    return false;
                                }
                            }
                            if (!(config.enterpriseConfig == null || config.enterpriseConfig.getEapMethod() == -1 || saveWifiEnterpriseConfig(config.SSID, config.enterpriseConfig))) {
                                return false;
                            }
                            this.mISupplicantStaNetworkCallback = new SupplicantStaNetworkHalCallback(config.networkId, config.SSID);
                            if (registerCallback(this.mISupplicantStaNetworkCallback)) {
                                return true;
                            }
                            Log.e(TAG, "Failed to register callback");
                            return false;
                        } else {
                            Log.e(TAG, "failed to set update identifier");
                            return false;
                        }
                    } else {
                        Log.e(TAG, "failed to set PairwiseCipher");
                        return false;
                    }
                }
            } else {
                Log.e(TAG, "failed to set BSSID: " + bssidStr2);
                return false;
            }
        }
    }

    private boolean isAuthAlgNeeded(WifiConfiguration config) {
        if (!config.allowedKeyManagement.get(8)) {
            return true;
        }
        if (!this.mVerboseLoggingEnabled) {
            return false;
        }
        Log.d(TAG, "No need to set Auth Algorithm for SAE");
        return false;
    }

    private boolean loadWifiEnterpriseConfig(String ssid, WifiEnterpriseConfig eapConfig) {
        String str;
        synchronized (this.mLock) {
            if (eapConfig == null) {
                return false;
            }
            if (getEapMethod()) {
                eapConfig.setEapMethod(supplicantToWifiConfigurationEapMethod(this.mEapMethod));
                if (getEapPhase2Method()) {
                    eapConfig.setPhase2Method(supplicantToWifiConfigurationEapPhase2Method(this.mEapPhase2Method));
                    if (getEapIdentity() && !ArrayUtils.isEmpty(this.mEapIdentity)) {
                        eapConfig.setFieldValue("identity", NativeUtil.stringFromByteArrayList(this.mEapIdentity));
                    }
                    if (getEapAnonymousIdentity() && !ArrayUtils.isEmpty(this.mEapAnonymousIdentity)) {
                        eapConfig.setFieldValue("anonymous_identity", NativeUtil.stringFromByteArrayList(this.mEapAnonymousIdentity));
                    }
                    if (getEapPassword() && !ArrayUtils.isEmpty(this.mEapPassword)) {
                        eapConfig.setFieldValue(McfDataUtil.McfData.JSON_PASSWORD, NativeUtil.stringFromByteArrayList(this.mEapPassword));
                    }
                    if (getEapClientCert() && !TextUtils.isEmpty(this.mEapClientCert)) {
                        eapConfig.setFieldValue(WifiBackupRestore.SupplicantBackupMigration.SUPPLICANT_KEY_CLIENT_CERT, this.mEapClientCert);
                    }
                    if (getEapCACert() && !TextUtils.isEmpty(this.mEapCACert)) {
                        eapConfig.setFieldValue(WifiBackupRestore.SupplicantBackupMigration.SUPPLICANT_KEY_CA_CERT, this.mEapCACert);
                    }
                    if (getEapSubjectMatch() && !TextUtils.isEmpty(this.mEapSubjectMatch)) {
                        eapConfig.setFieldValue("subject_match", this.mEapSubjectMatch);
                    }
                    if (getEapEngineID() && !TextUtils.isEmpty(this.mEapEngineID)) {
                        eapConfig.setFieldValue("engine_id", this.mEapEngineID);
                    }
                    if (getEapEngine() && !TextUtils.isEmpty(this.mEapEngineID)) {
                        if (this.mEapEngine) {
                            str = "1";
                        } else {
                            str = "0";
                        }
                        eapConfig.setFieldValue("engine", str);
                    }
                    if (getEapPrivateKeyId() && !TextUtils.isEmpty(this.mEapPrivateKeyId)) {
                        eapConfig.setFieldValue("key_id", this.mEapPrivateKeyId);
                    }
                    if (getEapAltSubjectMatch() && !TextUtils.isEmpty(this.mEapAltSubjectMatch)) {
                        eapConfig.setFieldValue("altsubject_match", this.mEapAltSubjectMatch);
                    }
                    if (getEapDomainSuffixMatch() && !TextUtils.isEmpty(this.mEapDomainSuffixMatch)) {
                        eapConfig.setFieldValue("domain_suffix_match", this.mEapDomainSuffixMatch);
                    }
                    if (getEapCAPath() && !TextUtils.isEmpty(this.mEapCAPath)) {
                        eapConfig.setFieldValue(WifiBackupRestore.SupplicantBackupMigration.SUPPLICANT_KEY_CA_PATH, this.mEapCAPath);
                    }
                    if (getSimIndex()) {
                        eapConfig.setFieldValue("sim_num", Integer.toString(this.mSimNumber));
                    }
                    return true;
                }
                Log.e(TAG, "failed to get eap phase2 method");
                return false;
            }
            Log.e(TAG, "failed to get eap method. Assumimg not an enterprise network");
            return true;
        }
    }

    private boolean saveSuiteBConfig(WifiConfiguration config) {
        if (config.allowedGroupCiphers.cardinality() != 0 && !setGroupCipher(wifiConfigurationToSupplicantGroupCipherMask(config.allowedGroupCiphers))) {
            Log.e(TAG, "failed to set Group Cipher");
            return false;
        } else if (config.allowedPairwiseCiphers.cardinality() != 0 && !setPairwiseCipher(wifiConfigurationToSupplicantPairwiseCipherMask(config.allowedPairwiseCiphers))) {
            Log.e(TAG, "failed to set PairwiseCipher");
            return false;
        } else if (config.allowedGroupManagementCiphers.cardinality() == 0 || setGroupMgmtCipher(wifiConfigurationToSupplicantGroupMgmtCipherMask(config.allowedGroupManagementCiphers))) {
            if (config.allowedSuiteBCiphers.get(1)) {
                if (!enableTlsSuiteBEapPhase1Param(true)) {
                    Log.e(TAG, "failed to set TLSSuiteB");
                    return false;
                }
            } else if (config.allowedSuiteBCiphers.get(0) && !enableSuiteBEapOpenSslCiphers()) {
                Log.e(TAG, "failed to set OpensslCipher");
                return false;
            }
            return true;
        } else {
            Log.e(TAG, "failed to set GroupMgmtCipher");
            return false;
        }
    }

    private boolean saveWifiEnterpriseConfig(String ssid, WifiEnterpriseConfig eapConfig) {
        synchronized (this.mLock) {
            if (eapConfig == null) {
                return false;
            }
            if (!setEapMethod(wifiConfigurationToSupplicantEapMethod(eapConfig.getEapMethod()))) {
                Log.e(TAG, ssid + ": failed to set eap method: " + eapConfig.getEapMethod());
                return false;
            } else if (!setEapPhase2Method(wifiConfigurationToSupplicantEapPhase2Method(eapConfig.getPhase2Method()))) {
                Log.e(TAG, ssid + ": failed to set eap phase 2 method: " + eapConfig.getPhase2Method());
                return false;
            } else {
                String eapParam = eapConfig.getFieldValue("identity");
                if (TextUtils.isEmpty(eapParam) || setEapIdentity(NativeUtil.stringToByteArrayList(eapParam))) {
                    String eapParam2 = eapConfig.getFieldValue("anonymous_identity");
                    if (TextUtils.isEmpty(eapParam2) || setEapAnonymousIdentity(NativeUtil.stringToByteArrayList(eapParam2))) {
                        String eapParam3 = eapConfig.getFieldValue(McfDataUtil.McfData.JSON_PASSWORD);
                        if (TextUtils.isEmpty(eapParam3) || setEapPassword(NativeUtil.stringToByteArrayList(eapParam3))) {
                            String eapParam4 = eapConfig.getFieldValue(WifiBackupRestore.SupplicantBackupMigration.SUPPLICANT_KEY_CLIENT_CERT);
                            if (TextUtils.isEmpty(eapParam4) || setEapClientCert(eapParam4)) {
                                String eapParam5 = eapConfig.getFieldValue(WifiBackupRestore.SupplicantBackupMigration.SUPPLICANT_KEY_CA_CERT);
                                if (TextUtils.isEmpty(eapParam5) || setEapCACert(eapParam5)) {
                                    String eapParam6 = eapConfig.getFieldValue("subject_match");
                                    if (TextUtils.isEmpty(eapParam6) || setEapSubjectMatch(eapParam6)) {
                                        String eapParam7 = eapConfig.getFieldValue("engine_id");
                                        if (TextUtils.isEmpty(eapParam7) || setEapEngineID(eapParam7)) {
                                            String eapParam8 = eapConfig.getFieldValue("engine");
                                            if (!TextUtils.isEmpty(eapParam8)) {
                                                if (!setEapEngine(eapParam8.equals("1"))) {
                                                    Log.e(TAG, ssid + ": failed to set eap engine: " + eapParam8);
                                                    return false;
                                                }
                                            }
                                            String eapParam9 = eapConfig.getFieldValue("key_id");
                                            if (TextUtils.isEmpty(eapParam9) || setEapPrivateKeyId(eapParam9)) {
                                                String eapParam10 = eapConfig.getFieldValue("altsubject_match");
                                                if (TextUtils.isEmpty(eapParam10) || setEapAltSubjectMatch(eapParam10)) {
                                                    String eapParam11 = eapConfig.getFieldValue("domain_suffix_match");
                                                    if (TextUtils.isEmpty(eapParam11) || setEapDomainSuffixMatch(eapParam11)) {
                                                        String eapParam12 = eapConfig.getFieldValue(WifiBackupRestore.SupplicantBackupMigration.SUPPLICANT_KEY_CA_PATH);
                                                        if (TextUtils.isEmpty(eapParam12) || setEapCAPath(eapParam12)) {
                                                            String eapParam13 = eapConfig.getFieldValue("proactive_key_caching");
                                                            if (!TextUtils.isEmpty(eapParam13)) {
                                                                if (!setEapProactiveKeyCaching(eapParam13.equals("1"))) {
                                                                    Log.e(TAG, ssid + ": failed to set proactive key caching: " + eapParam13);
                                                                    return false;
                                                                }
                                                            }
                                                            String eapParam14 = eapConfig.getFieldValue("sim_num");
                                                            if (TextUtils.isEmpty(eapParam14) || setSimIndex(Integer.parseInt(eapParam14))) {
                                                                return true;
                                                            }
                                                            Log.e(TAG, "failed to set simnum value " + eapParam14);
                                                            return false;
                                                        }
                                                        Log.e(TAG, ssid + ": failed to set eap ca path: " + eapParam12);
                                                        return false;
                                                    }
                                                    Log.e(TAG, ssid + ": failed to set eap domain suffix match: " + eapParam11);
                                                    return false;
                                                }
                                                Log.e(TAG, ssid + ": failed to set eap alt subject match: " + eapParam10);
                                                return false;
                                            }
                                            Log.e(TAG, ssid + ": failed to set eap private key: " + eapParam9);
                                            return false;
                                        }
                                        Log.e(TAG, ssid + ": failed to set eap engine id: " + eapParam7);
                                        return false;
                                    }
                                    Log.e(TAG, ssid + ": failed to set eap subject match: " + eapParam6);
                                    return false;
                                }
                                Log.e(TAG, ssid + ": failed to set eap ca cert: " + eapParam5);
                                return false;
                            }
                            Log.e(TAG, ssid + ": failed to set eap client cert: " + eapParam4);
                            return false;
                        }
                        Log.e(TAG, ssid + ": failed to set eap password");
                        return false;
                    }
                    Log.e(TAG, ssid + ": failed to set eap anonymous identity: " + eapParam2);
                    return false;
                }
                Log.e(TAG, ssid + ": failed to set eap identity: " + eapParam);
                return false;
            }
        }
    }

    private android.hardware.wifi.supplicant.V1_2.ISupplicantStaNetwork getV1_2StaNetwork() {
        android.hardware.wifi.supplicant.V1_2.ISupplicantStaNetwork supplicantStaNetworkForV1_2Mockable;
        synchronized (this.mLock) {
            supplicantStaNetworkForV1_2Mockable = getSupplicantStaNetworkForV1_2Mockable();
        }
        return supplicantStaNetworkForV1_2Mockable;
    }

    private static int wifiConfigurationToSupplicantKeyMgmtMask(BitSet keyMgmt) {
        int mask = 0;
        int bit = keyMgmt.nextSetBit(0);
        while (bit != -1) {
            if (bit == 0) {
                mask |= 4;
            } else if (bit == 1) {
                mask |= 2;
            } else if (bit == 2) {
                mask |= 1;
            } else if (bit == 3) {
                mask |= 8;
            } else if (bit == 20) {
                mask |= 262144;
            } else if (bit == 22) {
                mask |= 4096;
            } else if (bit != 23) {
                switch (bit) {
                    case 5:
                        mask |= 32768;
                        continue;
                    case 6:
                        mask |= 64;
                        continue;
                    case 7:
                        mask |= 32;
                        continue;
                    case 8:
                        mask |= 1024;
                        continue;
                    case 9:
                        mask |= 4194304;
                        continue;
                    case 10:
                        mask |= ISupplicantStaNetwork.KeyMgmtMask.SUITE_B_192;
                        continue;
                    case 11:
                        mask |= 256;
                        continue;
                    case 12:
                        mask |= 128;
                        continue;
                    default:
                        throw new IllegalArgumentException("Invalid protoMask bit in keyMgmt: " + bit);
                }
            } else {
                mask |= 8192;
            }
            bit = keyMgmt.nextSetBit(bit + 1);
        }
        return mask;
    }

    private static int wifiConfigurationToSupplicantProtoMask(BitSet protoMask) {
        int mask = 0;
        int bit = protoMask.nextSetBit(0);
        while (bit != -1) {
            if (bit == 0) {
                mask |= 1;
            } else if (bit == 1) {
                mask |= 2;
            } else if (bit == 2) {
                mask |= 8;
            } else if (bit == 3) {
                mask |= 4;
            } else {
                throw new IllegalArgumentException("Invalid protoMask bit in wificonfig: " + bit);
            }
            bit = protoMask.nextSetBit(bit + 1);
        }
        return mask;
    }

    private static int wifiConfigurationToSupplicantAuthAlgMask(BitSet authAlgMask) {
        int mask = 0;
        int bit = authAlgMask.nextSetBit(0);
        while (bit != -1) {
            if (bit == 0) {
                mask |= 1;
            } else if (bit == 1) {
                mask |= 2;
            } else if (bit == 2) {
                mask |= 4;
            } else {
                throw new IllegalArgumentException("Invalid authAlgMask bit in wificonfig: " + bit);
            }
            bit = authAlgMask.nextSetBit(bit + 1);
        }
        return mask;
    }

    private static int wifiConfigurationToSupplicantGroupCipherMask(BitSet groupCipherMask) {
        int mask = 0;
        int bit = groupCipherMask.nextSetBit(0);
        while (bit != -1) {
            if (bit == 0) {
                mask |= 2;
            } else if (bit == 1) {
                mask |= 4;
            } else if (bit == 2) {
                mask |= 8;
            } else if (bit == 3) {
                mask |= 16;
            } else if (bit == 4) {
                mask |= 16384;
            } else if (bit == 5) {
                mask |= 256;
            } else {
                throw new IllegalArgumentException("Invalid GroupCipherMask bit in wificonfig: " + bit);
            }
            bit = groupCipherMask.nextSetBit(bit + 1);
        }
        return mask;
    }

    private static int wifiConfigurationToSupplicantGroupMgmtCipherMask(BitSet groupMgmtCipherMask) {
        int mask = 0;
        int bit = groupMgmtCipherMask.nextSetBit(0);
        while (bit != -1) {
            if (bit == 0) {
                mask |= 8192;
            } else if (bit == 1) {
                mask |= 2048;
            } else if (bit == 2) {
                mask |= 4096;
            } else {
                throw new IllegalArgumentException("Invalid GroupMgmtCipherMask bit in wificonfig: " + bit);
            }
            bit = groupMgmtCipherMask.nextSetBit(bit + 1);
        }
        return mask;
    }

    private static int wifiConfigurationToSupplicantPairwiseCipherMask(BitSet pairwiseCipherMask) {
        int mask = 0;
        int bit = pairwiseCipherMask.nextSetBit(0);
        while (bit != -1) {
            if (bit == 0) {
                mask |= 1;
            } else if (bit == 1) {
                mask |= 8;
            } else if (bit == 2) {
                mask |= 16;
            } else if (bit == 3) {
                mask |= 256;
            } else {
                throw new IllegalArgumentException("Invalid pairwiseCipherMask bit in wificonfig: " + bit);
            }
            bit = pairwiseCipherMask.nextSetBit(bit + 1);
        }
        return mask;
    }

    private static int supplicantToWifiConfigurationEapMethod(int value) {
        if (value == 18) {
            return 18;
        }
        if (value == 19) {
            return 19;
        }
        switch (value) {
            case 0:
                return 0;
            case 1:
                return 1;
            case 2:
                return 2;
            case 3:
                return 3;
            case 4:
                return 4;
            case 5:
                return 5;
            case 6:
                return 6;
            case 7:
                return 7;
            default:
                Log.e(TAG, "invalid eap method value from supplicant: " + value);
                return -1;
        }
    }

    private static int supplicantToWifiConfigurationEapPhase1Method(int value) {
        if (value == 0) {
            return 0;
        }
        if (value == 1) {
            return 1;
        }
        if (value == 2) {
            return 2;
        }
        if (value == 3) {
            return 3;
        }
        Log.e(TAG, "invalid eap phase1 method value from supplicant: " + value);
        return -1;
    }

    private static int supplicantToWifiConfigurationEapPhase2Method(int value) {
        switch (value) {
            case 0:
                return 0;
            case 1:
                return 1;
            case 2:
                return 2;
            case 3:
                return 3;
            case 4:
                return 4;
            case 5:
                return 5;
            case 6:
                return 6;
            case 7:
                return 7;
            default:
                Log.e(TAG, "invalid eap phase2 method value from supplicant: " + value);
                return -1;
        }
    }

    private static int supplicantMaskValueToWifiConfigurationBitSet(int supplicantMask, int supplicantValue, BitSet bitset, int bitSetPosition) {
        bitset.set(bitSetPosition, (supplicantMask & supplicantValue) == supplicantValue);
        return (~supplicantValue) & supplicantMask;
    }

    private static BitSet supplicantToWifiConfigurationKeyMgmtMask(int mask) {
        BitSet bitset = new BitSet();
        int mask2 = supplicantMaskValueToWifiConfigurationBitSet(supplicantMaskValueToWifiConfigurationBitSet(supplicantMaskValueToWifiConfigurationBitSet(supplicantMaskValueToWifiConfigurationBitSet(supplicantMaskValueToWifiConfigurationBitSet(supplicantMaskValueToWifiConfigurationBitSet(supplicantMaskValueToWifiConfigurationBitSet(supplicantMaskValueToWifiConfigurationBitSet(supplicantMaskValueToWifiConfigurationBitSet(supplicantMaskValueToWifiConfigurationBitSet(supplicantMaskValueToWifiConfigurationBitSet(supplicantMaskValueToWifiConfigurationBitSet(supplicantMaskValueToWifiConfigurationBitSet(supplicantMaskValueToWifiConfigurationBitSet(supplicantMaskValueToWifiConfigurationBitSet(mask, 4, bitset, 0), 2, bitset, 1), 1, bitset, 2), 8, bitset, 3), 32768, bitset, 5), 64, bitset, 6), 32, bitset, 7), 1024, bitset, 8), 4194304, bitset, 9), ISupplicantStaNetwork.KeyMgmtMask.SUITE_B_192, bitset, 10), 256, bitset, 11), 128, bitset, 12), 262144, bitset, 20), 4096, bitset, 22), 8192, bitset, 23);
        if (mask2 == 0) {
            return bitset;
        }
        throw new IllegalArgumentException("invalid key mgmt mask from supplicant: " + mask2);
    }

    private static BitSet supplicantToWifiConfigurationProtoMask(int mask) {
        BitSet bitset = new BitSet();
        int mask2 = supplicantMaskValueToWifiConfigurationBitSet(supplicantMaskValueToWifiConfigurationBitSet(supplicantMaskValueToWifiConfigurationBitSet(supplicantMaskValueToWifiConfigurationBitSet(mask, 1, bitset, 0), 2, bitset, 1), 8, bitset, 2), 4, bitset, 3);
        if (mask2 == 0) {
            return bitset;
        }
        throw new IllegalArgumentException("invalid proto mask from supplicant: " + mask2);
    }

    private static BitSet supplicantToWifiConfigurationAuthAlgMask(int mask) {
        BitSet bitset = new BitSet();
        int mask2 = supplicantMaskValueToWifiConfigurationBitSet(supplicantMaskValueToWifiConfigurationBitSet(supplicantMaskValueToWifiConfigurationBitSet(mask, 1, bitset, 0), 2, bitset, 1), 4, bitset, 2);
        if (mask2 == 0) {
            return bitset;
        }
        throw new IllegalArgumentException("invalid auth alg mask from supplicant: " + mask2);
    }

    private static BitSet supplicantToWifiConfigurationGroupCipherMask(int mask) {
        BitSet bitset = new BitSet();
        int mask2 = supplicantMaskValueToWifiConfigurationBitSet(supplicantMaskValueToWifiConfigurationBitSet(supplicantMaskValueToWifiConfigurationBitSet(supplicantMaskValueToWifiConfigurationBitSet(supplicantMaskValueToWifiConfigurationBitSet(supplicantMaskValueToWifiConfigurationBitSet(mask, 2, bitset, 0), 4, bitset, 1), 8, bitset, 2), 16, bitset, 3), 256, bitset, 5), 16384, bitset, 4);
        if (mask2 == 0) {
            return bitset;
        }
        throw new IllegalArgumentException("invalid group cipher mask from supplicant: " + mask2);
    }

    private static BitSet supplicantToWifiConfigurationGroupMgmtCipherMask(int mask) {
        BitSet bitset = new BitSet();
        int mask2 = supplicantMaskValueToWifiConfigurationBitSet(supplicantMaskValueToWifiConfigurationBitSet(supplicantMaskValueToWifiConfigurationBitSet(mask, 2048, bitset, 1), 4096, bitset, 2), 8192, bitset, 0);
        if (mask2 == 0) {
            return bitset;
        }
        throw new IllegalArgumentException("invalid group mgmt cipher mask from supplicant: " + mask2);
    }

    private static BitSet supplicantToWifiConfigurationPairwiseCipherMask(int mask) {
        BitSet bitset = new BitSet();
        int mask2 = supplicantMaskValueToWifiConfigurationBitSet(supplicantMaskValueToWifiConfigurationBitSet(supplicantMaskValueToWifiConfigurationBitSet(supplicantMaskValueToWifiConfigurationBitSet(mask, 1, bitset, 0), 8, bitset, 1), 16, bitset, 2), 256, bitset, 3);
        if (mask2 == 0) {
            return bitset;
        }
        throw new IllegalArgumentException("invalid pairwise cipher mask from supplicant: " + mask2);
    }

    private static int wifiConfigurationToSupplicantEapMethod(int value) {
        if (value == 18) {
            return 18;
        }
        if (value == 19) {
            return 19;
        }
        switch (value) {
            case 0:
                return 0;
            case 1:
                return 1;
            case 2:
                return 2;
            case 3:
                return 3;
            case 4:
                return 4;
            case 5:
                return 5;
            case 6:
                return 6;
            case 7:
                return 7;
            default:
                Log.e(TAG, "invalid eap method value from WifiConfiguration: " + value);
                return -1;
        }
    }

    private static int wifiConfigurationToSupplicantEapPhase1Method(String phase1str) {
        if (phase1str == null || TextUtils.isEmpty(phase1str) || phase1str.equals("NULL")) {
            Log.e(TAG, "eap phase1 method value from WifiConfiguration: NONE");
            return -1;
        } else if (phase1str.length() != 19 || !phase1str.contains("fast_provisioning=")) {
            Log.e(TAG, "invalid eap phase1 method value from WifiConfiguration: phase1str: " + phase1str);
            return -1;
        } else {
            int value = Integer.parseInt(phase1str.replaceAll("fast_provisioning=", ""));
            if (value == 0) {
                return 0;
            }
            if (value == 1) {
                return 1;
            }
            if (value == 2) {
                return 2;
            }
            if (value == 3) {
                return 3;
            }
            Log.e(TAG, "invalid eap phase1 method value from WifiConfiguration: " + value);
            return -1;
        }
    }

    private static int wifiConfigurationToSupplicantEapPhase2Method(int value) {
        switch (value) {
            case 0:
                return 0;
            case 1:
                return 1;
            case 2:
                return 2;
            case 3:
                return 3;
            case 4:
                return 4;
            case 5:
                return 5;
            case 6:
                return 6;
            case 7:
                return 7;
            default:
                Log.e(TAG, "invalid eap phase2 method value from WifiConfiguration: " + value);
                return -1;
        }
    }

    private boolean getId() {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("getId")) {
                return false;
            }
            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                this.mISupplicantStaNetwork.getId(new ISupplicantNetwork.getIdCallback(statusOk) {
                    /* class com.android.server.wifi.$$Lambda$SupplicantStaNetworkHal$IRxqwt7Zayh6hYF7VQ3jtEpcrc */
                    private final /* synthetic */ MutableBoolean f$1;

                    {
                        this.f$1 = r2;
                    }

                    @Override // android.hardware.wifi.supplicant.V1_0.ISupplicantNetwork.getIdCallback
                    public final void onValues(SupplicantStatus supplicantStatus, int i) {
                        SupplicantStaNetworkHal.this.lambda$getId$0$SupplicantStaNetworkHal(this.f$1, supplicantStatus, i);
                    }
                });
                return statusOk.value;
            } catch (RemoteException e) {
                handleRemoteException(e, "getId");
                return false;
            }
        }
    }

    public /* synthetic */ void lambda$getId$0$SupplicantStaNetworkHal(MutableBoolean statusOk, SupplicantStatus status, int idValue) {
        statusOk.value = status.code == 0;
        if (statusOk.value) {
            this.mNetworkId = idValue;
        } else {
            checkStatusAndLogFailure(status, "getId");
        }
    }

    private boolean registerCallback(ISupplicantStaNetworkCallback callback) {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("registerCallback")) {
                return false;
            }
            try {
                return checkStatusAndLogFailure(this.mISupplicantStaNetwork.registerCallback(callback), "registerCallback");
            } catch (RemoteException e) {
                handleRemoteException(e, "registerCallback");
                return false;
            }
        }
    }

    private boolean setSsid(ArrayList<Byte> ssid) {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("setSsid")) {
                return false;
            }
            try {
                return checkStatusAndLogFailure(this.mISupplicantStaNetwork.setSsid(ssid), "setSsid");
            } catch (RemoteException e) {
                handleRemoteException(e, "setSsid");
                return false;
            }
        }
    }

    public boolean setBssid(String bssidStr) {
        boolean bssid;
        synchronized (this.mLock) {
            try {
                bssid = setBssid(NativeUtil.macAddressToByteArray(bssidStr));
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Illegal argument " + bssidStr, e);
                return false;
            } catch (Throwable th) {
                throw th;
            }
        }
        return bssid;
    }

    private boolean setBssid(byte[] bssid) {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("setBssid")) {
                return false;
            }
            try {
                return checkStatusAndLogFailure(this.mISupplicantStaNetwork.setBssid(bssid), "setBssid");
            } catch (RemoteException e) {
                handleRemoteException(e, "setBssid");
                return false;
            }
        }
    }

    private boolean setScanSsid(boolean enable) {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("setScanSsid")) {
                return false;
            }
            try {
                return checkStatusAndLogFailure(this.mISupplicantStaNetwork.setScanSsid(enable), "setScanSsid");
            } catch (RemoteException e) {
                handleRemoteException(e, "setScanSsid");
                return false;
            }
        }
    }

    private boolean setKeyMgmt(int keyMgmtMask) {
        SupplicantStatus status;
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("setKeyMgmt")) {
                return false;
            }
            try {
                android.hardware.wifi.supplicant.V1_2.ISupplicantStaNetwork iSupplicantStaNetworkV12 = getV1_2StaNetwork();
                if (iSupplicantStaNetworkV12 != null) {
                    status = iSupplicantStaNetworkV12.setKeyMgmt_1_2(keyMgmtMask);
                } else {
                    status = this.mISupplicantStaNetwork.setKeyMgmt(keyMgmtMask);
                }
                return checkStatusAndLogFailure(status, "setKeyMgmt");
            } catch (RemoteException e) {
                handleRemoteException(e, "setKeyMgmt");
                return false;
            }
        }
    }

    private boolean setProto(int protoMask) {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("setProto")) {
                return false;
            }
            try {
                return checkStatusAndLogFailure(this.mISupplicantStaNetwork.setProto(protoMask), "setProto");
            } catch (RemoteException e) {
                handleRemoteException(e, "setProto");
                return false;
            }
        }
    }

    private boolean setAuthAlg(int authAlgMask) {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("setAuthAlg")) {
                return false;
            }
            try {
                return checkStatusAndLogFailure(this.mISupplicantStaNetwork.setAuthAlg(authAlgMask), "setAuthAlg");
            } catch (RemoteException e) {
                handleRemoteException(e, "setAuthAlg");
                return false;
            }
        }
    }

    private boolean setGroupCipher(int groupCipherMask) {
        SupplicantStatus status;
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("setGroupCipher")) {
                return false;
            }
            try {
                android.hardware.wifi.supplicant.V1_2.ISupplicantStaNetwork iSupplicantStaNetworkV12 = getV1_2StaNetwork();
                if (iSupplicantStaNetworkV12 != null) {
                    status = iSupplicantStaNetworkV12.setGroupCipher_1_2(groupCipherMask);
                } else {
                    status = this.mISupplicantStaNetwork.setGroupCipher(groupCipherMask);
                }
                return checkStatusAndLogFailure(status, "setGroupCipher");
            } catch (RemoteException e) {
                handleRemoteException(e, "setGroupCipher");
                return false;
            }
        }
    }

    private boolean enableTlsSuiteBEapPhase1Param(boolean enable) {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("setEapPhase1Params")) {
                return false;
            }
            try {
                android.hardware.wifi.supplicant.V1_2.ISupplicantStaNetwork iSupplicantStaNetworkV12 = getV1_2StaNetwork();
                if (iSupplicantStaNetworkV12 != null) {
                    return checkStatusAndLogFailure(iSupplicantStaNetworkV12.enableTlsSuiteBEapPhase1Param(enable), "setEapPhase1Params");
                }
                Log.e(TAG, "Supplicant HAL version does not support setEapPhase1Params");
                return false;
            } catch (RemoteException e) {
                handleRemoteException(e, "setEapPhase1Params");
                return false;
            }
        }
    }

    private boolean enableSuiteBEapOpenSslCiphers() {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("setEapOpenSslCiphers")) {
                return false;
            }
            try {
                android.hardware.wifi.supplicant.V1_2.ISupplicantStaNetwork iSupplicantStaNetworkV12 = getV1_2StaNetwork();
                if (iSupplicantStaNetworkV12 != null) {
                    return checkStatusAndLogFailure(iSupplicantStaNetworkV12.enableSuiteBEapOpenSslCiphers(), "setEapOpenSslCiphers");
                }
                Log.e(TAG, "Supplicant HAL version does not support setEapOpenSslCiphers");
                return false;
            } catch (RemoteException e) {
                handleRemoteException(e, "setEapOpenSslCiphers");
                return false;
            }
        }
    }

    private boolean setPairwiseCipher(int pairwiseCipherMask) {
        SupplicantStatus status;
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("setPairwiseCipher")) {
                return false;
            }
            try {
                android.hardware.wifi.supplicant.V1_2.ISupplicantStaNetwork iSupplicantStaNetworkV12 = getV1_2StaNetwork();
                if (iSupplicantStaNetworkV12 != null) {
                    status = iSupplicantStaNetworkV12.setPairwiseCipher_1_2(pairwiseCipherMask);
                } else {
                    status = this.mISupplicantStaNetwork.setPairwiseCipher(pairwiseCipherMask);
                }
                return checkStatusAndLogFailure(status, "setPairwiseCipher");
            } catch (RemoteException e) {
                handleRemoteException(e, "setPairwiseCipher");
                return false;
            }
        }
    }

    private boolean setGroupMgmtCipher(int groupMgmtCipherMask) {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("setGroupMgmtCipher")) {
                return false;
            }
            try {
                android.hardware.wifi.supplicant.V1_2.ISupplicantStaNetwork iSupplicantStaNetworkV12 = getV1_2StaNetwork();
                if (iSupplicantStaNetworkV12 == null) {
                    return false;
                }
                return checkStatusAndLogFailure(iSupplicantStaNetworkV12.setGroupMgmtCipher(groupMgmtCipherMask), "setGroupMgmtCipher");
            } catch (RemoteException e) {
                handleRemoteException(e, "setGroupMgmtCipher");
                return false;
            }
        }
    }

    private boolean setPskPassphrase(String psk) {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("setPskPassphrase")) {
                return false;
            }
            try {
                return checkStatusAndLogFailure(this.mISupplicantStaNetwork.setPskPassphrase(psk), "setPskPassphrase");
            } catch (RemoteException e) {
                handleRemoteException(e, "setPskPassphrase");
                return false;
            }
        }
    }

    private boolean setPsk(byte[] psk) {
        synchronized (this.mLock) {
            if (psk == null) {
                Log.e(TAG, "psk is null");
                return false;
            } else if (!checkISupplicantStaNetworkAndLogFailure("setPsk")) {
                return false;
            } else {
                try {
                    return checkStatusAndLogFailure(this.mISupplicantStaNetwork.setPsk(psk), "setPsk");
                } catch (RemoteException e) {
                    handleRemoteException(e, "setPsk");
                    return false;
                }
            }
        }
    }

    private boolean setWepKey(int keyIdx, ArrayList<Byte> wepKey) {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("setWepKey")) {
                return false;
            }
            try {
                return checkStatusAndLogFailure(this.mISupplicantStaNetwork.setWepKey(keyIdx, wepKey), "setWepKey");
            } catch (RemoteException e) {
                handleRemoteException(e, "setWepKey");
                return false;
            }
        }
    }

    private boolean setWepTxKeyIdx(int keyIdx) {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("setWepTxKeyIdx")) {
                return false;
            }
            try {
                return checkStatusAndLogFailure(this.mISupplicantStaNetwork.setWepTxKeyIdx(keyIdx), "setWepTxKeyIdx");
            } catch (RemoteException e) {
                handleRemoteException(e, "setWepTxKeyIdx");
                return false;
            }
        }
    }

    private boolean setRequirePmf(boolean enable) {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("setRequirePmf")) {
                return false;
            }
            try {
                return checkStatusAndLogFailure(this.mISupplicantStaNetwork.setRequirePmf(enable), "setRequirePmf");
            } catch (RemoteException e) {
                handleRemoteException(e, "setRequirePmf");
                return false;
            }
        }
    }

    private boolean setUpdateIdentifier(int identifier) {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("setUpdateIdentifier")) {
                return false;
            }
            try {
                return checkStatusAndLogFailure(this.mISupplicantStaNetwork.setUpdateIdentifier(identifier), "setUpdateIdentifier");
            } catch (RemoteException e) {
                handleRemoteException(e, "setUpdateIdentifier");
                return false;
            }
        }
    }

    private boolean setEapMethod(int method) {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("setEapMethod")) {
                return false;
            }
            try {
                return checkStatusAndLogFailure(this.mISehSupplicantStaNetwork.setEapMethod(method), "setEapMethod");
            } catch (RemoteException e) {
                handleRemoteException(e, "setEapMethod");
                return false;
            }
        }
    }

    private boolean setEapPhase2Method(int method) {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("setEapPhase2Method")) {
                return false;
            }
            try {
                return checkStatusAndLogFailure(this.mISupplicantStaNetwork.setEapPhase2Method(method), "setEapPhase2Method");
            } catch (RemoteException e) {
                handleRemoteException(e, "setEapPhase2Method");
                return false;
            }
        }
    }

    private boolean setEapIdentity(ArrayList<Byte> identity) {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("setEapIdentity")) {
                return false;
            }
            try {
                return checkStatusAndLogFailure(this.mISupplicantStaNetwork.setEapIdentity(identity), "setEapIdentity");
            } catch (RemoteException e) {
                handleRemoteException(e, "setEapIdentity");
                return false;
            }
        }
    }

    private boolean setEapAnonymousIdentity(ArrayList<Byte> identity) {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("setEapAnonymousIdentity")) {
                return false;
            }
            try {
                return checkStatusAndLogFailure(this.mISupplicantStaNetwork.setEapAnonymousIdentity(identity), "setEapAnonymousIdentity");
            } catch (RemoteException e) {
                handleRemoteException(e, "setEapAnonymousIdentity");
                return false;
            }
        }
    }

    private boolean setEapPassword(ArrayList<Byte> password) {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("setEapPassword")) {
                return false;
            }
            try {
                return checkStatusAndLogFailure(this.mISupplicantStaNetwork.setEapPassword(password), "setEapPassword");
            } catch (RemoteException e) {
                handleRemoteException(e, "setEapPassword");
                return false;
            }
        }
    }

    private boolean setEapCACert(String path) {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("setEapCACert")) {
                return false;
            }
            try {
                return checkStatusAndLogFailure(this.mISupplicantStaNetwork.setEapCACert(path), "setEapCACert");
            } catch (RemoteException e) {
                handleRemoteException(e, "setEapCACert");
                return false;
            }
        }
    }

    private boolean setEapCAPath(String path) {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("setEapCAPath")) {
                return false;
            }
            try {
                return checkStatusAndLogFailure(this.mISupplicantStaNetwork.setEapCAPath(path), "setEapCAPath");
            } catch (RemoteException e) {
                handleRemoteException(e, "setEapCAPath");
                return false;
            }
        }
    }

    private boolean setEapClientCert(String path) {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("setEapClientCert")) {
                return false;
            }
            try {
                return checkStatusAndLogFailure(this.mISupplicantStaNetwork.setEapClientCert(path), "setEapClientCert");
            } catch (RemoteException e) {
                handleRemoteException(e, "setEapClientCert");
                return false;
            }
        }
    }

    private boolean setEapPrivateKeyId(String id) {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("setEapPrivateKeyId")) {
                return false;
            }
            try {
                return checkStatusAndLogFailure(this.mISupplicantStaNetwork.setEapPrivateKeyId(id), "setEapPrivateKeyId");
            } catch (RemoteException e) {
                handleRemoteException(e, "setEapPrivateKeyId");
                return false;
            }
        }
    }

    private boolean setEapSubjectMatch(String match) {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("setEapSubjectMatch")) {
                return false;
            }
            try {
                return checkStatusAndLogFailure(this.mISupplicantStaNetwork.setEapSubjectMatch(match), "setEapSubjectMatch");
            } catch (RemoteException e) {
                handleRemoteException(e, "setEapSubjectMatch");
                return false;
            }
        }
    }

    private boolean setEapAltSubjectMatch(String match) {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("setEapAltSubjectMatch")) {
                return false;
            }
            try {
                return checkStatusAndLogFailure(this.mISupplicantStaNetwork.setEapAltSubjectMatch(match), "setEapAltSubjectMatch");
            } catch (RemoteException e) {
                handleRemoteException(e, "setEapAltSubjectMatch");
                return false;
            }
        }
    }

    private boolean setEapEngine(boolean enable) {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("setEapEngine")) {
                return false;
            }
            try {
                return checkStatusAndLogFailure(this.mISupplicantStaNetwork.setEapEngine(enable), "setEapEngine");
            } catch (RemoteException e) {
                handleRemoteException(e, "setEapEngine");
                return false;
            }
        }
    }

    private boolean setEapEngineID(String id) {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("setEapEngineID")) {
                return false;
            }
            try {
                return checkStatusAndLogFailure(this.mISupplicantStaNetwork.setEapEngineID(id), "setEapEngineID");
            } catch (RemoteException e) {
                handleRemoteException(e, "setEapEngineID");
                return false;
            }
        }
    }

    private boolean setEapDomainSuffixMatch(String match) {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("setEapDomainSuffixMatch")) {
                return false;
            }
            try {
                return checkStatusAndLogFailure(this.mISupplicantStaNetwork.setEapDomainSuffixMatch(match), "setEapDomainSuffixMatch");
            } catch (RemoteException e) {
                handleRemoteException(e, "setEapDomainSuffixMatch");
                return false;
            }
        }
    }

    private boolean setEapProactiveKeyCaching(boolean enable) {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("setEapProactiveKeyCaching")) {
                return false;
            }
            try {
                return checkStatusAndLogFailure(this.mISupplicantStaNetwork.setProactiveKeyCaching(enable), "setEapProactiveKeyCaching");
            } catch (RemoteException e) {
                handleRemoteException(e, "setEapProactiveKeyCaching");
                return false;
            }
        }
    }

    private boolean setIdStr(String idString) {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("setIdStr")) {
                return false;
            }
            try {
                return checkStatusAndLogFailure(this.mISupplicantStaNetwork.setIdStr(idString), "setIdStr");
            } catch (RemoteException e) {
                handleRemoteException(e, "setIdStr");
                return false;
            }
        }
    }

    private boolean setVendorSsid(boolean enable) {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkExtAndLogFailure("setVendorSsid")) {
                return false;
            }
            try {
                return checkStatusAndLogFailureExt(this.mISehSupplicantStaNetwork.setVendorSsid(enable), "setVendorSsid");
            } catch (RemoteException e) {
                handleRemoteExceptionExt(e, "setVendorSsid");
                return false;
            }
        }
    }

    private boolean setEapPhase1Method(int method) {
        if (method == -1) {
            method = 4;
        }
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkExtAndLogFailure("setEapPhase1Method")) {
                return false;
            }
            try {
                return checkStatusAndLogFailureExt(this.mISehSupplicantStaNetwork.setEapPhase1Method(method), "setEapPhase1Method");
            } catch (RemoteException e) {
                handleRemoteExceptionExt(e, "setEapPhase1Method");
                return false;
            }
        }
    }

    private boolean setEapPacFile(String pac_file) {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkExtAndLogFailure("setEapPacFile")) {
                return false;
            }
            try {
                return checkStatusAndLogFailureExt(this.mISehSupplicantStaNetwork.setEapPacFile(pac_file), "setEapPacFile");
            } catch (RemoteException e) {
                handleRemoteExceptionExt(e, "setEapPacFile");
                return false;
            }
        }
    }

    private boolean setSimIndex(int index) {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkExtAndLogFailure("setSimIndex")) {
                return false;
            }
            try {
                return checkStatusAndLogFailureExt(this.mISehSupplicantStaNetwork.setSimIndex(index), "setSimIndex");
            } catch (RemoteException e) {
                handleRemoteExceptionExt(e, "setSimIndex");
                return false;
            }
        }
    }

    private boolean setWapiPskType(int type) {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkExtAndLogFailure("setWapiPskType")) {
                return false;
            }
            try {
                return checkStatusAndLogFailureExt(this.mISehSupplicantStaNetwork.setWapiPskType(type), "setWapiPskType");
            } catch (RemoteException e) {
                handleRemoteExceptionExt(e, "setWapiPskType");
                return false;
            }
        }
    }

    private boolean setWapiCertFormat(int index) {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkExtAndLogFailure("setWapiCertFormat")) {
                return false;
            }
            try {
                return checkStatusAndLogFailureExt(this.mISehSupplicantStaNetwork.setWapiCertFormat(index), "setWapiCertFormat");
            } catch (RemoteException e) {
                handleRemoteExceptionExt(e, "setWapiCertFormat");
                return false;
            }
        }
    }

    private boolean setWapiAsCert(String path) {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkExtAndLogFailure("setWapiAsCert")) {
                return false;
            }
            try {
                return checkStatusAndLogFailureExt(this.mISehSupplicantStaNetwork.setWapiAsCert(path), "setWapiAsCert");
            } catch (RemoteException e) {
                handleRemoteExceptionExt(e, "setWapiAsCert");
                return false;
            }
        }
    }

    private boolean setWapiUserCert(String path) {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkExtAndLogFailure("setWapiUserCert")) {
                return false;
            }
            try {
                return checkStatusAndLogFailureExt(this.mISehSupplicantStaNetwork.setWapiUserCert(path), "setWapiUserCert");
            } catch (RemoteException e) {
                handleRemoteExceptionExt(e, "setWapiUserCert");
                return false;
            }
        }
    }

    private boolean setSaePassword(String saePassword) {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("setSaePassword")) {
                return false;
            }
            try {
                android.hardware.wifi.supplicant.V1_2.ISupplicantStaNetwork iSupplicantStaNetworkV12 = getV1_2StaNetwork();
                if (iSupplicantStaNetworkV12 == null) {
                    return false;
                }
                return checkStatusAndLogFailure(iSupplicantStaNetworkV12.setSaePassword(saePassword), "setSaePassword");
            } catch (RemoteException e) {
                handleRemoteException(e, "setSaePassword");
                return false;
            }
        }
    }

    private boolean setSaePasswordId(String saePasswordId) {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("setSaePasswordId")) {
                return false;
            }
            try {
                android.hardware.wifi.supplicant.V1_2.ISupplicantStaNetwork iSupplicantStaNetworkV12 = getV1_2StaNetwork();
                if (iSupplicantStaNetworkV12 == null) {
                    return false;
                }
                return checkStatusAndLogFailure(iSupplicantStaNetworkV12.setSaePasswordId(saePasswordId), "setSaePasswordId");
            } catch (RemoteException e) {
                handleRemoteException(e, "setSaePasswordId");
                return false;
            }
        }
    }

    private boolean getSsid() {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("getSsid")) {
                return false;
            }
            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                this.mISupplicantStaNetwork.getSsid(new ISupplicantStaNetwork.getSsidCallback(statusOk) {
                    /* class com.android.server.wifi.$$Lambda$SupplicantStaNetworkHal$dUChvV6L83ism85zKYVAhy_jP0M */
                    private final /* synthetic */ MutableBoolean f$1;

                    {
                        this.f$1 = r2;
                    }

                    @Override // android.hardware.wifi.supplicant.V1_0.ISupplicantStaNetwork.getSsidCallback
                    public final void onValues(SupplicantStatus supplicantStatus, ArrayList arrayList) {
                        SupplicantStaNetworkHal.this.lambda$getSsid$1$SupplicantStaNetworkHal(this.f$1, supplicantStatus, arrayList);
                    }
                });
                return statusOk.value;
            } catch (RemoteException e) {
                handleRemoteException(e, "getSsid");
                return false;
            }
        }
    }

    public /* synthetic */ void lambda$getSsid$1$SupplicantStaNetworkHal(MutableBoolean statusOk, SupplicantStatus status, ArrayList ssidValue) {
        statusOk.value = status.code == 0;
        if (statusOk.value) {
            this.mSsid = ssidValue;
        } else {
            checkStatusAndLogFailure(status, "getSsid");
        }
    }

    private boolean getBssid() {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("getBssid")) {
                return false;
            }
            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                this.mISupplicantStaNetwork.getBssid(new ISupplicantStaNetwork.getBssidCallback(statusOk) {
                    /* class com.android.server.wifi.$$Lambda$SupplicantStaNetworkHal$6382Rt_N9IWM5_ofahWKN9I6IBU */
                    private final /* synthetic */ MutableBoolean f$1;

                    {
                        this.f$1 = r2;
                    }

                    @Override // android.hardware.wifi.supplicant.V1_0.ISupplicantStaNetwork.getBssidCallback
                    public final void onValues(SupplicantStatus supplicantStatus, byte[] bArr) {
                        SupplicantStaNetworkHal.this.lambda$getBssid$2$SupplicantStaNetworkHal(this.f$1, supplicantStatus, bArr);
                    }
                });
                return statusOk.value;
            } catch (RemoteException e) {
                handleRemoteException(e, "getBssid");
                return false;
            }
        }
    }

    public /* synthetic */ void lambda$getBssid$2$SupplicantStaNetworkHal(MutableBoolean statusOk, SupplicantStatus status, byte[] bssidValue) {
        statusOk.value = status.code == 0;
        if (statusOk.value) {
            this.mBssid = bssidValue;
        } else {
            checkStatusAndLogFailure(status, "getBssid");
        }
    }

    private boolean getScanSsid() {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("getScanSsid")) {
                return false;
            }
            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                this.mISupplicantStaNetwork.getScanSsid(new ISupplicantStaNetwork.getScanSsidCallback(statusOk) {
                    /* class com.android.server.wifi.$$Lambda$SupplicantStaNetworkHal$s22206N0y0P61x6iZFmcY8wHLUs */
                    private final /* synthetic */ MutableBoolean f$1;

                    {
                        this.f$1 = r2;
                    }

                    @Override // android.hardware.wifi.supplicant.V1_0.ISupplicantStaNetwork.getScanSsidCallback
                    public final void onValues(SupplicantStatus supplicantStatus, boolean z) {
                        SupplicantStaNetworkHal.this.lambda$getScanSsid$3$SupplicantStaNetworkHal(this.f$1, supplicantStatus, z);
                    }
                });
                return statusOk.value;
            } catch (RemoteException e) {
                handleRemoteException(e, "getScanSsid");
                return false;
            }
        }
    }

    public /* synthetic */ void lambda$getScanSsid$3$SupplicantStaNetworkHal(MutableBoolean statusOk, SupplicantStatus status, boolean enabledValue) {
        statusOk.value = status.code == 0;
        if (statusOk.value) {
            this.mScanSsid = enabledValue;
        } else {
            checkStatusAndLogFailure(status, "getScanSsid");
        }
    }

    private boolean getKeyMgmt() {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("getKeyMgmt")) {
                return false;
            }
            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                this.mISupplicantStaNetwork.getKeyMgmt(new ISupplicantStaNetwork.getKeyMgmtCallback(statusOk) {
                    /* class com.android.server.wifi.$$Lambda$SupplicantStaNetworkHal$94_sNP7lmR3xO_DkNKgE4cVRw */
                    private final /* synthetic */ MutableBoolean f$1;

                    {
                        this.f$1 = r2;
                    }

                    @Override // android.hardware.wifi.supplicant.V1_0.ISupplicantStaNetwork.getKeyMgmtCallback
                    public final void onValues(SupplicantStatus supplicantStatus, int i) {
                        SupplicantStaNetworkHal.this.lambda$getKeyMgmt$4$SupplicantStaNetworkHal(this.f$1, supplicantStatus, i);
                    }
                });
                return statusOk.value;
            } catch (RemoteException e) {
                handleRemoteException(e, "getKeyMgmt");
                return false;
            }
        }
    }

    public /* synthetic */ void lambda$getKeyMgmt$4$SupplicantStaNetworkHal(MutableBoolean statusOk, SupplicantStatus status, int keyMgmtMaskValue) {
        statusOk.value = status.code == 0;
        if (statusOk.value) {
            this.mKeyMgmtMask = keyMgmtMaskValue;
        } else {
            checkStatusAndLogFailure(status, "getKeyMgmt");
        }
    }

    private boolean getProto() {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("getProto")) {
                return false;
            }
            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                this.mISupplicantStaNetwork.getProto(new ISupplicantStaNetwork.getProtoCallback(statusOk) {
                    /* class com.android.server.wifi.$$Lambda$SupplicantStaNetworkHal$Wb2vJf7tZ_hiqFbe0Fygtypp6sY */
                    private final /* synthetic */ MutableBoolean f$1;

                    {
                        this.f$1 = r2;
                    }

                    @Override // android.hardware.wifi.supplicant.V1_0.ISupplicantStaNetwork.getProtoCallback
                    public final void onValues(SupplicantStatus supplicantStatus, int i) {
                        SupplicantStaNetworkHal.this.lambda$getProto$5$SupplicantStaNetworkHal(this.f$1, supplicantStatus, i);
                    }
                });
                return statusOk.value;
            } catch (RemoteException e) {
                handleRemoteException(e, "getProto");
                return false;
            }
        }
    }

    public /* synthetic */ void lambda$getProto$5$SupplicantStaNetworkHal(MutableBoolean statusOk, SupplicantStatus status, int protoMaskValue) {
        statusOk.value = status.code == 0;
        if (statusOk.value) {
            this.mProtoMask = protoMaskValue;
        } else {
            checkStatusAndLogFailure(status, "getProto");
        }
    }

    private boolean getAuthAlg() {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("getAuthAlg")) {
                return false;
            }
            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                this.mISupplicantStaNetwork.getAuthAlg(new ISupplicantStaNetwork.getAuthAlgCallback(statusOk) {
                    /* class com.android.server.wifi.$$Lambda$SupplicantStaNetworkHal$57ytpnr8Sp3UGVvxEJ5230fLLTY */
                    private final /* synthetic */ MutableBoolean f$1;

                    {
                        this.f$1 = r2;
                    }

                    @Override // android.hardware.wifi.supplicant.V1_0.ISupplicantStaNetwork.getAuthAlgCallback
                    public final void onValues(SupplicantStatus supplicantStatus, int i) {
                        SupplicantStaNetworkHal.this.lambda$getAuthAlg$6$SupplicantStaNetworkHal(this.f$1, supplicantStatus, i);
                    }
                });
                return statusOk.value;
            } catch (RemoteException e) {
                handleRemoteException(e, "getAuthAlg");
                return false;
            }
        }
    }

    public /* synthetic */ void lambda$getAuthAlg$6$SupplicantStaNetworkHal(MutableBoolean statusOk, SupplicantStatus status, int authAlgMaskValue) {
        statusOk.value = status.code == 0;
        if (statusOk.value) {
            this.mAuthAlgMask = authAlgMaskValue;
        } else {
            checkStatusAndLogFailure(status, "getAuthAlg");
        }
    }

    private boolean getGroupCipher() {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("getGroupCipher")) {
                return false;
            }
            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                this.mISupplicantStaNetwork.getGroupCipher(new ISupplicantStaNetwork.getGroupCipherCallback(statusOk) {
                    /* class com.android.server.wifi.$$Lambda$SupplicantStaNetworkHal$rwAunRMwc7t4KILZpqpRZsbXFtM */
                    private final /* synthetic */ MutableBoolean f$1;

                    {
                        this.f$1 = r2;
                    }

                    @Override // android.hardware.wifi.supplicant.V1_0.ISupplicantStaNetwork.getGroupCipherCallback
                    public final void onValues(SupplicantStatus supplicantStatus, int i) {
                        SupplicantStaNetworkHal.this.lambda$getGroupCipher$7$SupplicantStaNetworkHal(this.f$1, supplicantStatus, i);
                    }
                });
                return statusOk.value;
            } catch (RemoteException e) {
                handleRemoteException(e, "getGroupCipher");
                return false;
            }
        }
    }

    public /* synthetic */ void lambda$getGroupCipher$7$SupplicantStaNetworkHal(MutableBoolean statusOk, SupplicantStatus status, int groupCipherMaskValue) {
        statusOk.value = status.code == 0;
        if (statusOk.value) {
            this.mGroupCipherMask = groupCipherMaskValue;
        } else {
            checkStatusAndLogFailure(status, "getGroupCipher");
        }
    }

    private boolean getPairwiseCipher() {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("getPairwiseCipher")) {
                return false;
            }
            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                this.mISupplicantStaNetwork.getPairwiseCipher(new ISupplicantStaNetwork.getPairwiseCipherCallback(statusOk) {
                    /* class com.android.server.wifi.$$Lambda$SupplicantStaNetworkHal$0jcKc7WXuB0Arhk10QAm3C9QEIE */
                    private final /* synthetic */ MutableBoolean f$1;

                    {
                        this.f$1 = r2;
                    }

                    @Override // android.hardware.wifi.supplicant.V1_0.ISupplicantStaNetwork.getPairwiseCipherCallback
                    public final void onValues(SupplicantStatus supplicantStatus, int i) {
                        SupplicantStaNetworkHal.this.lambda$getPairwiseCipher$8$SupplicantStaNetworkHal(this.f$1, supplicantStatus, i);
                    }
                });
                return statusOk.value;
            } catch (RemoteException e) {
                handleRemoteException(e, "getPairwiseCipher");
                return false;
            }
        }
    }

    public /* synthetic */ void lambda$getPairwiseCipher$8$SupplicantStaNetworkHal(MutableBoolean statusOk, SupplicantStatus status, int pairwiseCipherMaskValue) {
        statusOk.value = status.code == 0;
        if (statusOk.value) {
            this.mPairwiseCipherMask = pairwiseCipherMaskValue;
        } else {
            checkStatusAndLogFailure(status, "getPairwiseCipher");
        }
    }

    private boolean getGroupMgmtCipher() {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("getGroupMgmtCipher")) {
                return false;
            }
            try {
                android.hardware.wifi.supplicant.V1_2.ISupplicantStaNetwork iSupplicantStaNetworkV12 = getV1_2StaNetwork();
                if (iSupplicantStaNetworkV12 == null) {
                    return false;
                }
                MutableBoolean statusOk = new MutableBoolean(false);
                iSupplicantStaNetworkV12.getGroupMgmtCipher(new ISupplicantStaNetwork.getGroupMgmtCipherCallback(statusOk) {
                    /* class com.android.server.wifi.$$Lambda$SupplicantStaNetworkHal$YDHfohHxnJ7L9MwHVfpUcLoHzqc */
                    private final /* synthetic */ MutableBoolean f$1;

                    {
                        this.f$1 = r2;
                    }

                    @Override // android.hardware.wifi.supplicant.V1_2.ISupplicantStaNetwork.getGroupMgmtCipherCallback
                    public final void onValues(SupplicantStatus supplicantStatus, int i) {
                        SupplicantStaNetworkHal.this.lambda$getGroupMgmtCipher$9$SupplicantStaNetworkHal(this.f$1, supplicantStatus, i);
                    }
                });
                return statusOk.value;
            } catch (RemoteException e) {
                handleRemoteException(e, "getGroupMgmtCipher");
                return false;
            }
        }
    }

    public /* synthetic */ void lambda$getGroupMgmtCipher$9$SupplicantStaNetworkHal(MutableBoolean statusOk, SupplicantStatus status, int groupMgmtCipherMaskValue) {
        statusOk.value = status.code == 0;
        if (statusOk.value) {
            this.mGroupMgmtCipherMask = groupMgmtCipherMaskValue;
        }
        checkStatusAndLogFailure(status, "getGroupMgmtCipher");
    }

    private boolean getPskPassphrase() {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("getPskPassphrase")) {
                return false;
            }
            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                this.mISupplicantStaNetwork.getPskPassphrase(new ISupplicantStaNetwork.getPskPassphraseCallback(statusOk) {
                    /* class com.android.server.wifi.$$Lambda$SupplicantStaNetworkHal$8oaC8iTc1b2mBmiAh6Hyd5dErRQ */
                    private final /* synthetic */ MutableBoolean f$1;

                    {
                        this.f$1 = r2;
                    }

                    @Override // android.hardware.wifi.supplicant.V1_0.ISupplicantStaNetwork.getPskPassphraseCallback
                    public final void onValues(SupplicantStatus supplicantStatus, String str) {
                        SupplicantStaNetworkHal.this.lambda$getPskPassphrase$10$SupplicantStaNetworkHal(this.f$1, supplicantStatus, str);
                    }
                });
                return statusOk.value;
            } catch (RemoteException e) {
                handleRemoteException(e, "getPskPassphrase");
                return false;
            }
        }
    }

    public /* synthetic */ void lambda$getPskPassphrase$10$SupplicantStaNetworkHal(MutableBoolean statusOk, SupplicantStatus status, String pskValue) {
        statusOk.value = status.code == 0;
        if (statusOk.value) {
            this.mPskPassphrase = pskValue;
        } else {
            checkStatusAndLogFailure(status, "getPskPassphrase");
        }
    }

    private boolean getSaePassword() {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("getSaePassword")) {
                return false;
            }
            try {
                android.hardware.wifi.supplicant.V1_2.ISupplicantStaNetwork iSupplicantStaNetworkV12 = getV1_2StaNetwork();
                if (iSupplicantStaNetworkV12 == null) {
                    return false;
                }
                MutableBoolean statusOk = new MutableBoolean(false);
                iSupplicantStaNetworkV12.getSaePassword(new ISupplicantStaNetwork.getSaePasswordCallback(statusOk) {
                    /* class com.android.server.wifi.$$Lambda$SupplicantStaNetworkHal$ZfWPZlnIzuQMdQheXBV3TC3YbI */
                    private final /* synthetic */ MutableBoolean f$1;

                    {
                        this.f$1 = r2;
                    }

                    @Override // android.hardware.wifi.supplicant.V1_2.ISupplicantStaNetwork.getSaePasswordCallback
                    public final void onValues(SupplicantStatus supplicantStatus, String str) {
                        SupplicantStaNetworkHal.this.lambda$getSaePassword$11$SupplicantStaNetworkHal(this.f$1, supplicantStatus, str);
                    }
                });
                return statusOk.value;
            } catch (RemoteException e) {
                handleRemoteException(e, "getSaePassword");
                return false;
            }
        }
    }

    public /* synthetic */ void lambda$getSaePassword$11$SupplicantStaNetworkHal(MutableBoolean statusOk, SupplicantStatus status, String saePassword) {
        statusOk.value = status.code == 0;
        if (statusOk.value) {
            this.mSaePassword = saePassword;
        }
        checkStatusAndLogFailure(status, "getSaePassword");
    }

    private boolean getPsk() {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("getPsk")) {
                return false;
            }
            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                this.mISupplicantStaNetwork.getPsk(new ISupplicantStaNetwork.getPskCallback(statusOk) {
                    /* class com.android.server.wifi.$$Lambda$SupplicantStaNetworkHal$aXQeIBiN0zkQlqEIJiqErM22Kao */
                    private final /* synthetic */ MutableBoolean f$1;

                    {
                        this.f$1 = r2;
                    }

                    @Override // android.hardware.wifi.supplicant.V1_0.ISupplicantStaNetwork.getPskCallback
                    public final void onValues(SupplicantStatus supplicantStatus, byte[] bArr) {
                        SupplicantStaNetworkHal.this.lambda$getPsk$12$SupplicantStaNetworkHal(this.f$1, supplicantStatus, bArr);
                    }
                });
                return statusOk.value;
            } catch (RemoteException e) {
                handleRemoteException(e, "getPsk");
                return false;
            }
        }
    }

    public /* synthetic */ void lambda$getPsk$12$SupplicantStaNetworkHal(MutableBoolean statusOk, SupplicantStatus status, byte[] pskValue) {
        statusOk.value = status.code == 0;
        if (statusOk.value) {
            this.mPsk = pskValue;
        } else {
            checkStatusAndLogFailure(status, "getPsk");
        }
    }

    private boolean getWepKey(int keyIdx) {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("keyIdx")) {
                return false;
            }
            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                this.mISupplicantStaNetwork.getWepKey(keyIdx, new ISupplicantStaNetwork.getWepKeyCallback(statusOk) {
                    /* class com.android.server.wifi.$$Lambda$SupplicantStaNetworkHal$J8gTmXx4gObGJ3SeulfFsYgMnPk */
                    private final /* synthetic */ MutableBoolean f$1;

                    {
                        this.f$1 = r2;
                    }

                    @Override // android.hardware.wifi.supplicant.V1_0.ISupplicantStaNetwork.getWepKeyCallback
                    public final void onValues(SupplicantStatus supplicantStatus, ArrayList arrayList) {
                        SupplicantStaNetworkHal.this.lambda$getWepKey$13$SupplicantStaNetworkHal(this.f$1, supplicantStatus, arrayList);
                    }
                });
                return statusOk.value;
            } catch (RemoteException e) {
                handleRemoteException(e, "keyIdx");
                return false;
            }
        }
    }

    public /* synthetic */ void lambda$getWepKey$13$SupplicantStaNetworkHal(MutableBoolean statusOk, SupplicantStatus status, ArrayList wepKeyValue) {
        statusOk.value = status.code == 0;
        if (statusOk.value) {
            this.mWepKey = wepKeyValue;
            return;
        }
        Log.e(TAG, "keyIdx,  failed: " + status.debugMessage);
    }

    private boolean getWepTxKeyIdx() {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("getWepTxKeyIdx")) {
                return false;
            }
            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                this.mISupplicantStaNetwork.getWepTxKeyIdx(new ISupplicantStaNetwork.getWepTxKeyIdxCallback(statusOk) {
                    /* class com.android.server.wifi.$$Lambda$SupplicantStaNetworkHal$kVXzHqTa9pmip6_jdOwCME8TzJk */
                    private final /* synthetic */ MutableBoolean f$1;

                    {
                        this.f$1 = r2;
                    }

                    @Override // android.hardware.wifi.supplicant.V1_0.ISupplicantStaNetwork.getWepTxKeyIdxCallback
                    public final void onValues(SupplicantStatus supplicantStatus, int i) {
                        SupplicantStaNetworkHal.this.lambda$getWepTxKeyIdx$14$SupplicantStaNetworkHal(this.f$1, supplicantStatus, i);
                    }
                });
                return statusOk.value;
            } catch (RemoteException e) {
                handleRemoteException(e, "getWepTxKeyIdx");
                return false;
            }
        }
    }

    public /* synthetic */ void lambda$getWepTxKeyIdx$14$SupplicantStaNetworkHal(MutableBoolean statusOk, SupplicantStatus status, int keyIdxValue) {
        statusOk.value = status.code == 0;
        if (statusOk.value) {
            this.mWepTxKeyIdx = keyIdxValue;
        } else {
            checkStatusAndLogFailure(status, "getWepTxKeyIdx");
        }
    }

    private boolean getRequirePmf() {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("getRequirePmf")) {
                return false;
            }
            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                this.mISupplicantStaNetwork.getRequirePmf(new ISupplicantStaNetwork.getRequirePmfCallback(statusOk) {
                    /* class com.android.server.wifi.$$Lambda$SupplicantStaNetworkHal$Rxhai6hJ9xUPv3KFAZ7McXdkA */
                    private final /* synthetic */ MutableBoolean f$1;

                    {
                        this.f$1 = r2;
                    }

                    @Override // android.hardware.wifi.supplicant.V1_0.ISupplicantStaNetwork.getRequirePmfCallback
                    public final void onValues(SupplicantStatus supplicantStatus, boolean z) {
                        SupplicantStaNetworkHal.this.lambda$getRequirePmf$15$SupplicantStaNetworkHal(this.f$1, supplicantStatus, z);
                    }
                });
                return statusOk.value;
            } catch (RemoteException e) {
                handleRemoteException(e, "getRequirePmf");
                return false;
            }
        }
    }

    public /* synthetic */ void lambda$getRequirePmf$15$SupplicantStaNetworkHal(MutableBoolean statusOk, SupplicantStatus status, boolean enabledValue) {
        statusOk.value = status.code == 0;
        if (statusOk.value) {
            this.mRequirePmf = enabledValue;
        } else {
            checkStatusAndLogFailure(status, "getRequirePmf");
        }
    }

    private boolean getEapMethod() {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("getEapMethod")) {
                return false;
            }
            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                this.mISehSupplicantStaNetwork.getEapMethod(new ISehSupplicantStaNetwork.getEapMethodCallback(statusOk) {
                    /* class com.android.server.wifi.$$Lambda$SupplicantStaNetworkHal$kb4OSZfZMhdPD964czIa2dibP4U */
                    private final /* synthetic */ MutableBoolean f$1;

                    {
                        this.f$1 = r2;
                    }

                    @Override // vendor.samsung.hardware.wifi.supplicant.V2_0.ISehSupplicantStaNetwork.getEapMethodCallback
                    public final void onValues(SupplicantStatus supplicantStatus, int i) {
                        SupplicantStaNetworkHal.this.lambda$getEapMethod$16$SupplicantStaNetworkHal(this.f$1, supplicantStatus, i);
                    }
                });
                return statusOk.value;
            } catch (RemoteException e) {
                handleRemoteException(e, "getEapMethod");
                return false;
            }
        }
    }

    public /* synthetic */ void lambda$getEapMethod$16$SupplicantStaNetworkHal(MutableBoolean statusOk, SupplicantStatus status, int methodValue) {
        statusOk.value = status.code == 0;
        if (statusOk.value) {
            this.mEapMethod = methodValue;
        } else {
            checkStatusAndLogFailure(status, "getEapMethod");
        }
    }

    private boolean getEapPhase2Method() {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("getEapPhase2Method")) {
                return false;
            }
            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                this.mISupplicantStaNetwork.getEapPhase2Method(new ISupplicantStaNetwork.getEapPhase2MethodCallback(statusOk) {
                    /* class com.android.server.wifi.$$Lambda$SupplicantStaNetworkHal$eL3wewJkgEe0o2WCm6uEVKQ3VZE */
                    private final /* synthetic */ MutableBoolean f$1;

                    {
                        this.f$1 = r2;
                    }

                    @Override // android.hardware.wifi.supplicant.V1_0.ISupplicantStaNetwork.getEapPhase2MethodCallback
                    public final void onValues(SupplicantStatus supplicantStatus, int i) {
                        SupplicantStaNetworkHal.this.lambda$getEapPhase2Method$17$SupplicantStaNetworkHal(this.f$1, supplicantStatus, i);
                    }
                });
                return statusOk.value;
            } catch (RemoteException e) {
                handleRemoteException(e, "getEapPhase2Method");
                return false;
            }
        }
    }

    public /* synthetic */ void lambda$getEapPhase2Method$17$SupplicantStaNetworkHal(MutableBoolean statusOk, SupplicantStatus status, int methodValue) {
        statusOk.value = status.code == 0;
        if (statusOk.value) {
            this.mEapPhase2Method = methodValue;
        } else {
            checkStatusAndLogFailure(status, "getEapPhase2Method");
        }
    }

    private boolean getEapIdentity() {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("getEapIdentity")) {
                return false;
            }
            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                this.mISupplicantStaNetwork.getEapIdentity(new ISupplicantStaNetwork.getEapIdentityCallback(statusOk) {
                    /* class com.android.server.wifi.$$Lambda$SupplicantStaNetworkHal$uPdUD5JRHhLROsiauncrqk1ne2w */
                    private final /* synthetic */ MutableBoolean f$1;

                    {
                        this.f$1 = r2;
                    }

                    @Override // android.hardware.wifi.supplicant.V1_0.ISupplicantStaNetwork.getEapIdentityCallback
                    public final void onValues(SupplicantStatus supplicantStatus, ArrayList arrayList) {
                        SupplicantStaNetworkHal.this.lambda$getEapIdentity$18$SupplicantStaNetworkHal(this.f$1, supplicantStatus, arrayList);
                    }
                });
                return statusOk.value;
            } catch (RemoteException e) {
                handleRemoteException(e, "getEapIdentity");
                return false;
            }
        }
    }

    public /* synthetic */ void lambda$getEapIdentity$18$SupplicantStaNetworkHal(MutableBoolean statusOk, SupplicantStatus status, ArrayList identityValue) {
        statusOk.value = status.code == 0;
        if (statusOk.value) {
            this.mEapIdentity = identityValue;
        } else {
            checkStatusAndLogFailure(status, "getEapIdentity");
        }
    }

    private boolean getEapAnonymousIdentity() {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("getEapAnonymousIdentity")) {
                return false;
            }
            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                this.mISupplicantStaNetwork.getEapAnonymousIdentity(new ISupplicantStaNetwork.getEapAnonymousIdentityCallback(statusOk) {
                    /* class com.android.server.wifi.$$Lambda$SupplicantStaNetworkHal$sOQswoEmi2YGuJgMYqiwIcJghKA */
                    private final /* synthetic */ MutableBoolean f$1;

                    {
                        this.f$1 = r2;
                    }

                    @Override // android.hardware.wifi.supplicant.V1_0.ISupplicantStaNetwork.getEapAnonymousIdentityCallback
                    public final void onValues(SupplicantStatus supplicantStatus, ArrayList arrayList) {
                        SupplicantStaNetworkHal.this.lambda$getEapAnonymousIdentity$19$SupplicantStaNetworkHal(this.f$1, supplicantStatus, arrayList);
                    }
                });
                return statusOk.value;
            } catch (RemoteException e) {
                handleRemoteException(e, "getEapAnonymousIdentity");
                return false;
            }
        }
    }

    public /* synthetic */ void lambda$getEapAnonymousIdentity$19$SupplicantStaNetworkHal(MutableBoolean statusOk, SupplicantStatus status, ArrayList identityValue) {
        statusOk.value = status.code == 0;
        if (statusOk.value) {
            this.mEapAnonymousIdentity = identityValue;
        } else {
            checkStatusAndLogFailure(status, "getEapAnonymousIdentity");
        }
    }

    public String fetchEapAnonymousIdentity() {
        synchronized (this.mLock) {
            if (!getEapAnonymousIdentity()) {
                return null;
            }
            return NativeUtil.stringFromByteArrayList(this.mEapAnonymousIdentity);
        }
    }

    private boolean getEapPassword() {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("getEapPassword")) {
                return false;
            }
            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                this.mISupplicantStaNetwork.getEapPassword(new ISupplicantStaNetwork.getEapPasswordCallback(statusOk) {
                    /* class com.android.server.wifi.$$Lambda$SupplicantStaNetworkHal$nAb62Q2VZcbyw6ILUY4_g1spp0 */
                    private final /* synthetic */ MutableBoolean f$1;

                    {
                        this.f$1 = r2;
                    }

                    @Override // android.hardware.wifi.supplicant.V1_0.ISupplicantStaNetwork.getEapPasswordCallback
                    public final void onValues(SupplicantStatus supplicantStatus, ArrayList arrayList) {
                        SupplicantStaNetworkHal.this.lambda$getEapPassword$20$SupplicantStaNetworkHal(this.f$1, supplicantStatus, arrayList);
                    }
                });
                return statusOk.value;
            } catch (RemoteException e) {
                handleRemoteException(e, "getEapPassword");
                return false;
            }
        }
    }

    public /* synthetic */ void lambda$getEapPassword$20$SupplicantStaNetworkHal(MutableBoolean statusOk, SupplicantStatus status, ArrayList passwordValue) {
        statusOk.value = status.code == 0;
        if (statusOk.value) {
            this.mEapPassword = passwordValue;
        } else {
            checkStatusAndLogFailure(status, "getEapPassword");
        }
    }

    private boolean getEapCACert() {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("getEapCACert")) {
                return false;
            }
            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                this.mISupplicantStaNetwork.getEapCACert(new ISupplicantStaNetwork.getEapCACertCallback(statusOk) {
                    /* class com.android.server.wifi.$$Lambda$SupplicantStaNetworkHal$bjo1DGYuZM1KT0dNMfiEsW3feXw */
                    private final /* synthetic */ MutableBoolean f$1;

                    {
                        this.f$1 = r2;
                    }

                    @Override // android.hardware.wifi.supplicant.V1_0.ISupplicantStaNetwork.getEapCACertCallback
                    public final void onValues(SupplicantStatus supplicantStatus, String str) {
                        SupplicantStaNetworkHal.this.lambda$getEapCACert$21$SupplicantStaNetworkHal(this.f$1, supplicantStatus, str);
                    }
                });
                return statusOk.value;
            } catch (RemoteException e) {
                handleRemoteException(e, "getEapCACert");
                return false;
            }
        }
    }

    public /* synthetic */ void lambda$getEapCACert$21$SupplicantStaNetworkHal(MutableBoolean statusOk, SupplicantStatus status, String pathValue) {
        statusOk.value = status.code == 0;
        if (statusOk.value) {
            this.mEapCACert = pathValue;
        } else {
            checkStatusAndLogFailure(status, "getEapCACert");
        }
    }

    private boolean getEapCAPath() {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("getEapCAPath")) {
                return false;
            }
            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                this.mISupplicantStaNetwork.getEapCAPath(new ISupplicantStaNetwork.getEapCAPathCallback(statusOk) {
                    /* class com.android.server.wifi.$$Lambda$SupplicantStaNetworkHal$BVUOLEH6zYxuHQ51xEp_fKePdck */
                    private final /* synthetic */ MutableBoolean f$1;

                    {
                        this.f$1 = r2;
                    }

                    @Override // android.hardware.wifi.supplicant.V1_0.ISupplicantStaNetwork.getEapCAPathCallback
                    public final void onValues(SupplicantStatus supplicantStatus, String str) {
                        SupplicantStaNetworkHal.this.lambda$getEapCAPath$22$SupplicantStaNetworkHal(this.f$1, supplicantStatus, str);
                    }
                });
                return statusOk.value;
            } catch (RemoteException e) {
                handleRemoteException(e, "getEapCAPath");
                return false;
            }
        }
    }

    public /* synthetic */ void lambda$getEapCAPath$22$SupplicantStaNetworkHal(MutableBoolean statusOk, SupplicantStatus status, String pathValue) {
        statusOk.value = status.code == 0;
        if (statusOk.value) {
            this.mEapCAPath = pathValue;
        } else {
            checkStatusAndLogFailure(status, "getEapCAPath");
        }
    }

    private boolean getEapClientCert() {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("getEapClientCert")) {
                return false;
            }
            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                this.mISupplicantStaNetwork.getEapClientCert(new ISupplicantStaNetwork.getEapClientCertCallback(statusOk) {
                    /* class com.android.server.wifi.$$Lambda$SupplicantStaNetworkHal$xEr0kldJ2HCUbM6cdTyGT7ags14 */
                    private final /* synthetic */ MutableBoolean f$1;

                    {
                        this.f$1 = r2;
                    }

                    @Override // android.hardware.wifi.supplicant.V1_0.ISupplicantStaNetwork.getEapClientCertCallback
                    public final void onValues(SupplicantStatus supplicantStatus, String str) {
                        SupplicantStaNetworkHal.this.lambda$getEapClientCert$23$SupplicantStaNetworkHal(this.f$1, supplicantStatus, str);
                    }
                });
                return statusOk.value;
            } catch (RemoteException e) {
                handleRemoteException(e, "getEapClientCert");
                return false;
            }
        }
    }

    public /* synthetic */ void lambda$getEapClientCert$23$SupplicantStaNetworkHal(MutableBoolean statusOk, SupplicantStatus status, String pathValue) {
        statusOk.value = status.code == 0;
        if (statusOk.value) {
            this.mEapClientCert = pathValue;
        } else {
            checkStatusAndLogFailure(status, "getEapClientCert");
        }
    }

    private boolean getEapPrivateKeyId() {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("getEapPrivateKeyId")) {
                return false;
            }
            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                this.mISupplicantStaNetwork.getEapPrivateKeyId(new ISupplicantStaNetwork.getEapPrivateKeyIdCallback(statusOk) {
                    /* class com.android.server.wifi.$$Lambda$SupplicantStaNetworkHal$NO9R_bbew5OwC4thbyT4bZ6sno */
                    private final /* synthetic */ MutableBoolean f$1;

                    {
                        this.f$1 = r2;
                    }

                    @Override // android.hardware.wifi.supplicant.V1_0.ISupplicantStaNetwork.getEapPrivateKeyIdCallback
                    public final void onValues(SupplicantStatus supplicantStatus, String str) {
                        SupplicantStaNetworkHal.this.lambda$getEapPrivateKeyId$24$SupplicantStaNetworkHal(this.f$1, supplicantStatus, str);
                    }
                });
                return statusOk.value;
            } catch (RemoteException e) {
                handleRemoteException(e, "getEapPrivateKeyId");
                return false;
            }
        }
    }

    public /* synthetic */ void lambda$getEapPrivateKeyId$24$SupplicantStaNetworkHal(MutableBoolean statusOk, SupplicantStatus status, String idValue) {
        statusOk.value = status.code == 0;
        if (statusOk.value) {
            this.mEapPrivateKeyId = idValue;
        } else {
            checkStatusAndLogFailure(status, "getEapPrivateKeyId");
        }
    }

    private boolean getEapSubjectMatch() {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("getEapSubjectMatch")) {
                return false;
            }
            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                this.mISupplicantStaNetwork.getEapSubjectMatch(new ISupplicantStaNetwork.getEapSubjectMatchCallback(statusOk) {
                    /* class com.android.server.wifi.$$Lambda$SupplicantStaNetworkHal$yZybh6nE0QKhmwih2sARYB8Erz0 */
                    private final /* synthetic */ MutableBoolean f$1;

                    {
                        this.f$1 = r2;
                    }

                    @Override // android.hardware.wifi.supplicant.V1_0.ISupplicantStaNetwork.getEapSubjectMatchCallback
                    public final void onValues(SupplicantStatus supplicantStatus, String str) {
                        SupplicantStaNetworkHal.this.lambda$getEapSubjectMatch$25$SupplicantStaNetworkHal(this.f$1, supplicantStatus, str);
                    }
                });
                return statusOk.value;
            } catch (RemoteException e) {
                handleRemoteException(e, "getEapSubjectMatch");
                return false;
            }
        }
    }

    public /* synthetic */ void lambda$getEapSubjectMatch$25$SupplicantStaNetworkHal(MutableBoolean statusOk, SupplicantStatus status, String matchValue) {
        statusOk.value = status.code == 0;
        if (statusOk.value) {
            this.mEapSubjectMatch = matchValue;
        } else {
            checkStatusAndLogFailure(status, "getEapSubjectMatch");
        }
    }

    private boolean getEapAltSubjectMatch() {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("getEapAltSubjectMatch")) {
                return false;
            }
            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                this.mISupplicantStaNetwork.getEapAltSubjectMatch(new ISupplicantStaNetwork.getEapAltSubjectMatchCallback(statusOk) {
                    /* class com.android.server.wifi.$$Lambda$SupplicantStaNetworkHal$DwacJYSvo6ffmBjhnPk6GG_Hes */
                    private final /* synthetic */ MutableBoolean f$1;

                    {
                        this.f$1 = r2;
                    }

                    @Override // android.hardware.wifi.supplicant.V1_0.ISupplicantStaNetwork.getEapAltSubjectMatchCallback
                    public final void onValues(SupplicantStatus supplicantStatus, String str) {
                        SupplicantStaNetworkHal.this.lambda$getEapAltSubjectMatch$26$SupplicantStaNetworkHal(this.f$1, supplicantStatus, str);
                    }
                });
                return statusOk.value;
            } catch (RemoteException e) {
                handleRemoteException(e, "getEapAltSubjectMatch");
                return false;
            }
        }
    }

    public /* synthetic */ void lambda$getEapAltSubjectMatch$26$SupplicantStaNetworkHal(MutableBoolean statusOk, SupplicantStatus status, String matchValue) {
        statusOk.value = status.code == 0;
        if (statusOk.value) {
            this.mEapAltSubjectMatch = matchValue;
        } else {
            checkStatusAndLogFailure(status, "getEapAltSubjectMatch");
        }
    }

    private boolean getEapEngine() {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("getEapEngine")) {
                return false;
            }
            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                this.mISupplicantStaNetwork.getEapEngine(new ISupplicantStaNetwork.getEapEngineCallback(statusOk) {
                    /* class com.android.server.wifi.$$Lambda$SupplicantStaNetworkHal$GRU2sq8DVCEulyhBAc9MbbPhdQI */
                    private final /* synthetic */ MutableBoolean f$1;

                    {
                        this.f$1 = r2;
                    }

                    @Override // android.hardware.wifi.supplicant.V1_0.ISupplicantStaNetwork.getEapEngineCallback
                    public final void onValues(SupplicantStatus supplicantStatus, boolean z) {
                        SupplicantStaNetworkHal.this.lambda$getEapEngine$27$SupplicantStaNetworkHal(this.f$1, supplicantStatus, z);
                    }
                });
                return statusOk.value;
            } catch (RemoteException e) {
                handleRemoteException(e, "getEapEngine");
                return false;
            }
        }
    }

    public /* synthetic */ void lambda$getEapEngine$27$SupplicantStaNetworkHal(MutableBoolean statusOk, SupplicantStatus status, boolean enabledValue) {
        statusOk.value = status.code == 0;
        if (statusOk.value) {
            this.mEapEngine = enabledValue;
        } else {
            checkStatusAndLogFailure(status, "getEapEngine");
        }
    }

    private boolean getEapEngineID() {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("getEapEngineID")) {
                return false;
            }
            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                this.mISupplicantStaNetwork.getEapEngineID(new ISupplicantStaNetwork.getEapEngineIDCallback(statusOk) {
                    /* class com.android.server.wifi.$$Lambda$SupplicantStaNetworkHal$2IKy8Iaf3MEUeYvo1f2eHPPvGk0 */
                    private final /* synthetic */ MutableBoolean f$1;

                    {
                        this.f$1 = r2;
                    }

                    @Override // android.hardware.wifi.supplicant.V1_0.ISupplicantStaNetwork.getEapEngineIDCallback
                    public final void onValues(SupplicantStatus supplicantStatus, String str) {
                        SupplicantStaNetworkHal.this.lambda$getEapEngineID$28$SupplicantStaNetworkHal(this.f$1, supplicantStatus, str);
                    }
                });
                return statusOk.value;
            } catch (RemoteException e) {
                handleRemoteException(e, "getEapEngineID");
                return false;
            }
        }
    }

    public /* synthetic */ void lambda$getEapEngineID$28$SupplicantStaNetworkHal(MutableBoolean statusOk, SupplicantStatus status, String idValue) {
        statusOk.value = status.code == 0;
        if (statusOk.value) {
            this.mEapEngineID = idValue;
        } else {
            checkStatusAndLogFailure(status, "getEapEngineID");
        }
    }

    private boolean getEapDomainSuffixMatch() {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("getEapDomainSuffixMatch")) {
                return false;
            }
            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                this.mISupplicantStaNetwork.getEapDomainSuffixMatch(new ISupplicantStaNetwork.getEapDomainSuffixMatchCallback(statusOk) {
                    /* class com.android.server.wifi.$$Lambda$SupplicantStaNetworkHal$dnAf58rQp9ElijKXWnMgFIkhwDk */
                    private final /* synthetic */ MutableBoolean f$1;

                    {
                        this.f$1 = r2;
                    }

                    @Override // android.hardware.wifi.supplicant.V1_0.ISupplicantStaNetwork.getEapDomainSuffixMatchCallback
                    public final void onValues(SupplicantStatus supplicantStatus, String str) {
                        SupplicantStaNetworkHal.this.lambda$getEapDomainSuffixMatch$29$SupplicantStaNetworkHal(this.f$1, supplicantStatus, str);
                    }
                });
                return statusOk.value;
            } catch (RemoteException e) {
                handleRemoteException(e, "getEapDomainSuffixMatch");
                return false;
            }
        }
    }

    public /* synthetic */ void lambda$getEapDomainSuffixMatch$29$SupplicantStaNetworkHal(MutableBoolean statusOk, SupplicantStatus status, String matchValue) {
        statusOk.value = status.code == 0;
        if (statusOk.value) {
            this.mEapDomainSuffixMatch = matchValue;
        } else {
            checkStatusAndLogFailure(status, "getEapDomainSuffixMatch");
        }
    }

    private boolean getIdStr() {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("getIdStr")) {
                return false;
            }
            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                this.mISupplicantStaNetwork.getIdStr(new ISupplicantStaNetwork.getIdStrCallback(statusOk) {
                    /* class com.android.server.wifi.$$Lambda$SupplicantStaNetworkHal$Cuh4X9iQkKoFRV3bUdezoEvo7A */
                    private final /* synthetic */ MutableBoolean f$1;

                    {
                        this.f$1 = r2;
                    }

                    @Override // android.hardware.wifi.supplicant.V1_0.ISupplicantStaNetwork.getIdStrCallback
                    public final void onValues(SupplicantStatus supplicantStatus, String str) {
                        SupplicantStaNetworkHal.this.lambda$getIdStr$30$SupplicantStaNetworkHal(this.f$1, supplicantStatus, str);
                    }
                });
                return statusOk.value;
            } catch (RemoteException e) {
                handleRemoteException(e, "getIdStr");
                return false;
            }
        }
    }

    public /* synthetic */ void lambda$getIdStr$30$SupplicantStaNetworkHal(MutableBoolean statusOk, SupplicantStatus status, String idString) {
        statusOk.value = status.code == 0;
        if (statusOk.value) {
            this.mIdStr = idString;
        } else {
            checkStatusAndLogFailure(status, "getIdStr");
        }
    }

    private boolean getVendorSsidValue() {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkExtAndLogFailure("getVendorSsidValue")) {
                return false;
            }
            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                this.mISehSupplicantStaNetwork.getVendorSsidValue(new ISehSupplicantStaNetwork.getVendorSsidValueCallback(statusOk) {
                    /* class com.android.server.wifi.$$Lambda$SupplicantStaNetworkHal$nL0nqEGvEEpQRlfgIZu1zyGlAQ */
                    private final /* synthetic */ MutableBoolean f$1;

                    {
                        this.f$1 = r2;
                    }

                    @Override // vendor.samsung.hardware.wifi.supplicant.V2_0.ISehSupplicantStaNetwork.getVendorSsidValueCallback
                    public final void onValues(SupplicantStatus supplicantStatus, boolean z) {
                        SupplicantStaNetworkHal.this.lambda$getVendorSsidValue$31$SupplicantStaNetworkHal(this.f$1, supplicantStatus, z);
                    }
                });
                return statusOk.value;
            } catch (RemoteException e) {
                handleRemoteExceptionExt(e, "getVendorSsidValue");
                return false;
            }
        }
    }

    public /* synthetic */ void lambda$getVendorSsidValue$31$SupplicantStaNetworkHal(MutableBoolean statusOk, SupplicantStatus status, boolean enabledValue) {
        statusOk.value = status.code == 0;
        if (statusOk.value) {
            this.mVendorSsid = enabledValue;
        } else {
            checkStatusAndLogFailureExt(status, "getVendorSsidValue");
        }
    }

    private boolean getAutoReconnectValue() {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkExtAndLogFailure("getAutoReconnectValue")) {
                return false;
            }
            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                this.mISehSupplicantStaNetwork.getAutoReconnectValue(new ISehSupplicantStaNetwork.getAutoReconnectValueCallback(statusOk) {
                    /* class com.android.server.wifi.$$Lambda$SupplicantStaNetworkHal$RUqNnJXC1QQ6ji0zNaXctqMBuQk */
                    private final /* synthetic */ MutableBoolean f$1;

                    {
                        this.f$1 = r2;
                    }

                    @Override // vendor.samsung.hardware.wifi.supplicant.V2_0.ISehSupplicantStaNetwork.getAutoReconnectValueCallback
                    public final void onValues(SupplicantStatus supplicantStatus, int i) {
                        SupplicantStaNetworkHal.this.lambda$getAutoReconnectValue$32$SupplicantStaNetworkHal(this.f$1, supplicantStatus, i);
                    }
                });
                return statusOk.value;
            } catch (RemoteException e) {
                handleRemoteExceptionExt(e, "getAutoReconnectValue");
                return false;
            }
        }
    }

    public /* synthetic */ void lambda$getAutoReconnectValue$32$SupplicantStaNetworkHal(MutableBoolean statusOk, SupplicantStatus status, int enabledValue) {
        statusOk.value = status.code == 0;
        if (statusOk.value) {
            this.mAutoReconnect = enabledValue;
        } else {
            checkStatusAndLogFailureExt(status, "getAutoReconnectValue");
        }
    }

    private boolean getEapPhase1Method() {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkExtAndLogFailure("getEapPhase1Method")) {
                return false;
            }
            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                this.mISehSupplicantStaNetwork.getEapPhase1Method(new ISehSupplicantStaNetwork.getEapPhase1MethodCallback(statusOk) {
                    /* class com.android.server.wifi.$$Lambda$SupplicantStaNetworkHal$s__5q1e0X4QcrtKQPO15j5lP5s */
                    private final /* synthetic */ MutableBoolean f$1;

                    {
                        this.f$1 = r2;
                    }

                    @Override // vendor.samsung.hardware.wifi.supplicant.V2_0.ISehSupplicantStaNetwork.getEapPhase1MethodCallback
                    public final void onValues(SupplicantStatus supplicantStatus, int i) {
                        SupplicantStaNetworkHal.this.lambda$getEapPhase1Method$33$SupplicantStaNetworkHal(this.f$1, supplicantStatus, i);
                    }
                });
                return statusOk.value;
            } catch (RemoteException e) {
                handleRemoteExceptionExt(e, "getEapPhase1Method");
                return false;
            }
        }
    }

    public /* synthetic */ void lambda$getEapPhase1Method$33$SupplicantStaNetworkHal(MutableBoolean statusOk, SupplicantStatus status, int methodValue) {
        statusOk.value = status.code == 0;
        if (statusOk.value) {
            this.mEapPhase1Method = methodValue;
        } else {
            checkStatusAndLogFailureExt(status, "getEapPhase1Method");
        }
    }

    private boolean getEapPacFile() {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkExtAndLogFailure("getEapPacFile")) {
                return false;
            }
            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                this.mISehSupplicantStaNetwork.getEapPacFile(new ISehSupplicantStaNetwork.getEapPacFileCallback(statusOk) {
                    /* class com.android.server.wifi.$$Lambda$SupplicantStaNetworkHal$6kCGecPCPyur3wwtIJTzP69vDGE */
                    private final /* synthetic */ MutableBoolean f$1;

                    {
                        this.f$1 = r2;
                    }

                    @Override // vendor.samsung.hardware.wifi.supplicant.V2_0.ISehSupplicantStaNetwork.getEapPacFileCallback
                    public final void onValues(SupplicantStatus supplicantStatus, String str) {
                        SupplicantStaNetworkHal.this.lambda$getEapPacFile$34$SupplicantStaNetworkHal(this.f$1, supplicantStatus, str);
                    }
                });
                return statusOk.value;
            } catch (RemoteException e) {
                handleRemoteExceptionExt(e, "getEapPacFile");
                return false;
            }
        }
    }

    public /* synthetic */ void lambda$getEapPacFile$34$SupplicantStaNetworkHal(MutableBoolean statusOk, SupplicantStatus status, String pac_file) {
        statusOk.value = status.code == 0;
        if (statusOk.value) {
            this.mEapPacFile = pac_file;
        } else {
            checkStatusAndLogFailureExt(status, "getEapPacFile");
        }
    }

    private boolean getSimIndex() {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkExtAndLogFailure("getSimIndex")) {
                return false;
            }
            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                this.mISehSupplicantStaNetwork.getSimIndex(new ISehSupplicantStaNetwork.getSimIndexCallback(statusOk) {
                    /* class com.android.server.wifi.$$Lambda$SupplicantStaNetworkHal$Ih9hHApvPmbGqY06gbZG4rFLHo */
                    private final /* synthetic */ MutableBoolean f$1;

                    {
                        this.f$1 = r2;
                    }

                    @Override // vendor.samsung.hardware.wifi.supplicant.V2_0.ISehSupplicantStaNetwork.getSimIndexCallback
                    public final void onValues(SupplicantStatus supplicantStatus, int i) {
                        SupplicantStaNetworkHal.this.lambda$getSimIndex$35$SupplicantStaNetworkHal(this.f$1, supplicantStatus, i);
                    }
                });
                return statusOk.value;
            } catch (RemoteException e) {
                handleRemoteExceptionExt(e, "getSimIndex");
                return false;
            }
        }
    }

    public /* synthetic */ void lambda$getSimIndex$35$SupplicantStaNetworkHal(MutableBoolean statusOk, SupplicantStatus status, int index) {
        statusOk.value = status.code == 0;
        if (statusOk.value) {
            this.mSimNumber = index;
        } else {
            checkStatusAndLogFailureExt(status, "getSimIndex");
        }
    }

    private boolean getWapiPskType() {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkExtAndLogFailure("getWapiPskType")) {
                return false;
            }
            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                this.mISehSupplicantStaNetwork.getWapiPskType(new ISehSupplicantStaNetwork.getWapiPskTypeCallback(statusOk) {
                    /* class com.android.server.wifi.$$Lambda$SupplicantStaNetworkHal$JDAvzUdnj4BTVgOH42vCZsmA61c */
                    private final /* synthetic */ MutableBoolean f$1;

                    {
                        this.f$1 = r2;
                    }

                    @Override // vendor.samsung.hardware.wifi.supplicant.V2_0.ISehSupplicantStaNetwork.getWapiPskTypeCallback
                    public final void onValues(SupplicantStatus supplicantStatus, int i) {
                        SupplicantStaNetworkHal.this.lambda$getWapiPskType$36$SupplicantStaNetworkHal(this.f$1, supplicantStatus, i);
                    }
                });
                return statusOk.value;
            } catch (RemoteException e) {
                handleRemoteExceptionExt(e, "getWapiPskType");
                return false;
            }
        }
    }

    public /* synthetic */ void lambda$getWapiPskType$36$SupplicantStaNetworkHal(MutableBoolean statusOk, SupplicantStatus status, int wapiPskType) {
        statusOk.value = status.code == 0;
        if (statusOk.value) {
            this.mWapiPskType = wapiPskType;
        } else {
            checkStatusAndLogFailureExt(status, "getWapiPskType");
        }
    }

    private boolean getWapiCertFormat() {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkExtAndLogFailure("getWapiCertFormat")) {
                return false;
            }
            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                this.mISehSupplicantStaNetwork.getWapiCertFormat(new ISehSupplicantStaNetwork.getWapiCertFormatCallback(statusOk) {
                    /* class com.android.server.wifi.$$Lambda$SupplicantStaNetworkHal$BlFbmBLzOS4KFTwc5PeHHQM_3KE */
                    private final /* synthetic */ MutableBoolean f$1;

                    {
                        this.f$1 = r2;
                    }

                    @Override // vendor.samsung.hardware.wifi.supplicant.V2_0.ISehSupplicantStaNetwork.getWapiCertFormatCallback
                    public final void onValues(SupplicantStatus supplicantStatus, int i) {
                        SupplicantStaNetworkHal.this.lambda$getWapiCertFormat$37$SupplicantStaNetworkHal(this.f$1, supplicantStatus, i);
                    }
                });
                return statusOk.value;
            } catch (RemoteException e) {
                handleRemoteExceptionExt(e, "getWapiCertFormat");
                return false;
            }
        }
    }

    public /* synthetic */ void lambda$getWapiCertFormat$37$SupplicantStaNetworkHal(MutableBoolean statusOk, SupplicantStatus status, int wapiCertIndex) {
        statusOk.value = status.code == 0;
        if (statusOk.value) {
            this.mWapiCertIndex = wapiCertIndex;
        } else {
            checkStatusAndLogFailureExt(status, "getWapiCertFormat");
        }
    }

    private boolean getWapiAsCert() {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkExtAndLogFailure("getWapiAsCert")) {
                return false;
            }
            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                this.mISehSupplicantStaNetwork.getWapiAsCert(new ISehSupplicantStaNetwork.getWapiAsCertCallback(statusOk) {
                    /* class com.android.server.wifi.$$Lambda$SupplicantStaNetworkHal$7M4XLnvyxmR3Lyni6IkrpmTvyEQ */
                    private final /* synthetic */ MutableBoolean f$1;

                    {
                        this.f$1 = r2;
                    }

                    @Override // vendor.samsung.hardware.wifi.supplicant.V2_0.ISehSupplicantStaNetwork.getWapiAsCertCallback
                    public final void onValues(SupplicantStatus supplicantStatus, String str) {
                        SupplicantStaNetworkHal.this.lambda$getWapiAsCert$38$SupplicantStaNetworkHal(this.f$1, supplicantStatus, str);
                    }
                });
                return statusOk.value;
            } catch (RemoteException e) {
                handleRemoteExceptionExt(e, "getWapiAsCert");
                return false;
            }
        }
    }

    public /* synthetic */ void lambda$getWapiAsCert$38$SupplicantStaNetworkHal(MutableBoolean statusOk, SupplicantStatus status, String pathValue) {
        statusOk.value = status.code == 0;
        if (statusOk.value) {
            this.mWapiAsCert = pathValue;
        } else {
            checkStatusAndLogFailureExt(status, "getWapiAsCert");
        }
    }

    private boolean getWapiUserCert() {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkExtAndLogFailure("getWapiUserCert")) {
                return false;
            }
            try {
                MutableBoolean statusOk = new MutableBoolean(false);
                this.mISehSupplicantStaNetwork.getWapiUserCert(new ISehSupplicantStaNetwork.getWapiUserCertCallback(statusOk) {
                    /* class com.android.server.wifi.$$Lambda$SupplicantStaNetworkHal$R2ZAx4nZdHMasa_C8JZ5VD6ILo */
                    private final /* synthetic */ MutableBoolean f$1;

                    {
                        this.f$1 = r2;
                    }

                    @Override // vendor.samsung.hardware.wifi.supplicant.V2_0.ISehSupplicantStaNetwork.getWapiUserCertCallback
                    public final void onValues(SupplicantStatus supplicantStatus, String str) {
                        SupplicantStaNetworkHal.this.lambda$getWapiUserCert$39$SupplicantStaNetworkHal(this.f$1, supplicantStatus, str);
                    }
                });
                return statusOk.value;
            } catch (RemoteException e) {
                handleRemoteExceptionExt(e, "getWapiUserCert");
                return false;
            }
        }
    }

    public /* synthetic */ void lambda$getWapiUserCert$39$SupplicantStaNetworkHal(MutableBoolean statusOk, SupplicantStatus status, String pathValue) {
        statusOk.value = status.code == 0;
        if (statusOk.value) {
            this.mWapiUserCert = pathValue;
        } else {
            checkStatusAndLogFailureExt(status, "getWapiUserCert");
        }
    }

    private boolean enable(boolean noConnect) {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("enable")) {
                return false;
            }
            try {
                return checkStatusAndLogFailure(this.mISupplicantStaNetwork.enable(noConnect), "enable");
            } catch (RemoteException e) {
                handleRemoteException(e, "enable");
                return false;
            }
        }
    }

    private boolean disable() {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("disable")) {
                return false;
            }
            try {
                return checkStatusAndLogFailure(this.mISupplicantStaNetwork.disable(), "disable");
            } catch (RemoteException e) {
                handleRemoteException(e, "disable");
                return false;
            }
        }
    }

    public boolean select() {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("select")) {
                return false;
            }
            try {
                return checkStatusAndLogFailure(this.mISupplicantStaNetwork.select(), "select");
            } catch (RemoteException e) {
                handleRemoteException(e, "select");
                return false;
            }
        }
    }

    public boolean sendNetworkEapSimGsmAuthResponse(String paramsStr) {
        synchronized (this.mLock) {
            try {
                Matcher match = GSM_AUTH_RESPONSE_PARAMS_PATTERN.matcher(paramsStr);
                ArrayList<ISupplicantStaNetwork.NetworkResponseEapSimGsmAuthParams> params = new ArrayList<>();
                while (match.find()) {
                    if (match.groupCount() != 2) {
                        Log.e(TAG, "Malformed gsm auth response params: " + paramsStr);
                        return false;
                    }
                    ISupplicantStaNetwork.NetworkResponseEapSimGsmAuthParams param = new ISupplicantStaNetwork.NetworkResponseEapSimGsmAuthParams();
                    byte[] kc = NativeUtil.hexStringToByteArray(match.group(1));
                    if (kc == null || kc.length != param.f8kc.length) {
                        Log.e(TAG, "Invalid kc value: " + match.group(1));
                        return false;
                    }
                    byte[] sres = NativeUtil.hexStringToByteArray(match.group(2));
                    if (sres == null || sres.length != param.sres.length) {
                        Log.e(TAG, "Invalid sres value: " + match.group(2));
                        return false;
                    }
                    System.arraycopy(kc, 0, param.f8kc, 0, param.f8kc.length);
                    System.arraycopy(sres, 0, param.sres, 0, param.sres.length);
                    params.add(param);
                }
                if (params.size() > 3 || params.size() < 2) {
                    Log.e(TAG, "Malformed gsm auth response params: " + paramsStr);
                    return false;
                }
                return sendNetworkEapSimGsmAuthResponse(params);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Illegal argument " + paramsStr, e);
                return false;
            }
        }
    }

    private boolean sendNetworkEapSimGsmAuthResponse(ArrayList<ISupplicantStaNetwork.NetworkResponseEapSimGsmAuthParams> params) {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("sendNetworkEapSimGsmAuthResponse")) {
                return false;
            }
            try {
                return checkStatusAndLogFailure(this.mISupplicantStaNetwork.sendNetworkEapSimGsmAuthResponse(params), "sendNetworkEapSimGsmAuthResponse");
            } catch (RemoteException e) {
                handleRemoteException(e, "sendNetworkEapSimGsmAuthResponse");
                return false;
            }
        }
    }

    public boolean sendNetworkEapSimGsmAuthFailure() {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("sendNetworkEapSimGsmAuthFailure")) {
                return false;
            }
            try {
                return checkStatusAndLogFailure(this.mISupplicantStaNetwork.sendNetworkEapSimGsmAuthFailure(), "sendNetworkEapSimGsmAuthFailure");
            } catch (RemoteException e) {
                handleRemoteException(e, "sendNetworkEapSimGsmAuthFailure");
                return false;
            }
        }
    }

    public boolean sendNetworkEapSimUmtsAuthResponse(String paramsStr) {
        synchronized (this.mLock) {
            try {
                Matcher match = UMTS_AUTH_RESPONSE_PARAMS_PATTERN.matcher(paramsStr);
                if (match.find()) {
                    if (match.groupCount() == 3) {
                        ISupplicantStaNetwork.NetworkResponseEapSimUmtsAuthParams params = new ISupplicantStaNetwork.NetworkResponseEapSimUmtsAuthParams();
                        byte[] ik = NativeUtil.hexStringToByteArray(match.group(1));
                        if (ik != null) {
                            if (ik.length == params.f10ik.length) {
                                byte[] ck = NativeUtil.hexStringToByteArray(match.group(2));
                                if (ck != null) {
                                    if (ck.length == params.f9ck.length) {
                                        byte[] res = NativeUtil.hexStringToByteArray(match.group(3));
                                        if (res != null) {
                                            if (res.length != 0) {
                                                System.arraycopy(ik, 0, params.f10ik, 0, params.f10ik.length);
                                                System.arraycopy(ck, 0, params.f9ck, 0, params.f9ck.length);
                                                for (byte b : res) {
                                                    params.res.add(Byte.valueOf(b));
                                                }
                                                return sendNetworkEapSimUmtsAuthResponse(params);
                                            }
                                        }
                                        Log.e(TAG, "Invalid res value: " + match.group(3));
                                        return false;
                                    }
                                }
                                Log.e(TAG, "Invalid ck value: " + match.group(2));
                                return false;
                            }
                        }
                        Log.e(TAG, "Invalid ik value: " + match.group(1));
                        return false;
                    }
                }
                Log.e(TAG, "Malformed umts auth response params: " + paramsStr);
                return false;
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Illegal argument " + paramsStr, e);
                return false;
            }
        }
    }

    private boolean sendNetworkEapSimUmtsAuthResponse(ISupplicantStaNetwork.NetworkResponseEapSimUmtsAuthParams params) {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("sendNetworkEapSimUmtsAuthResponse")) {
                return false;
            }
            try {
                return checkStatusAndLogFailure(this.mISupplicantStaNetwork.sendNetworkEapSimUmtsAuthResponse(params), "sendNetworkEapSimUmtsAuthResponse");
            } catch (RemoteException e) {
                handleRemoteException(e, "sendNetworkEapSimUmtsAuthResponse");
                return false;
            }
        }
    }

    public boolean sendNetworkEapSimUmtsAutsResponse(String paramsStr) {
        synchronized (this.mLock) {
            try {
                Matcher match = UMTS_AUTS_RESPONSE_PARAMS_PATTERN.matcher(paramsStr);
                if (match.find()) {
                    if (match.groupCount() == 1) {
                        byte[] auts = NativeUtil.hexStringToByteArray(match.group(1));
                        if (auts != null) {
                            if (auts.length == 14) {
                                return sendNetworkEapSimUmtsAutsResponse(auts);
                            }
                        }
                        Log.e(TAG, "Invalid auts value: " + match.group(1));
                        return false;
                    }
                }
                Log.e(TAG, "Malformed umts auts response params: " + paramsStr);
                return false;
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Illegal argument " + paramsStr, e);
                return false;
            }
        }
    }

    private boolean sendNetworkEapSimUmtsAutsResponse(byte[] auts) {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("sendNetworkEapSimUmtsAutsResponse")) {
                return false;
            }
            try {
                return checkStatusAndLogFailure(this.mISupplicantStaNetwork.sendNetworkEapSimUmtsAutsResponse(auts), "sendNetworkEapSimUmtsAutsResponse");
            } catch (RemoteException e) {
                handleRemoteException(e, "sendNetworkEapSimUmtsAutsResponse");
                return false;
            }
        }
    }

    public boolean sendNetworkEapSimUmtsAuthFailure() {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("sendNetworkEapSimUmtsAuthFailure")) {
                return false;
            }
            try {
                return checkStatusAndLogFailure(this.mISupplicantStaNetwork.sendNetworkEapSimUmtsAuthFailure(), "sendNetworkEapSimUmtsAuthFailure");
            } catch (RemoteException e) {
                handleRemoteException(e, "sendNetworkEapSimUmtsAuthFailure");
                return false;
            }
        }
    }

    /* access modifiers changed from: protected */
    public android.hardware.wifi.supplicant.V1_1.ISupplicantStaNetwork getSupplicantStaNetworkForV1_1Mockable() {
        android.hardware.wifi.supplicant.V1_0.ISupplicantStaNetwork iSupplicantStaNetwork = this.mISupplicantStaNetwork;
        if (iSupplicantStaNetwork == null) {
            return null;
        }
        return android.hardware.wifi.supplicant.V1_1.ISupplicantStaNetwork.castFrom((IHwInterface) iSupplicantStaNetwork);
    }

    /* access modifiers changed from: protected */
    public android.hardware.wifi.supplicant.V1_2.ISupplicantStaNetwork getSupplicantStaNetworkForV1_2Mockable() {
        android.hardware.wifi.supplicant.V1_0.ISupplicantStaNetwork iSupplicantStaNetwork = this.mISupplicantStaNetwork;
        if (iSupplicantStaNetwork == null) {
            return null;
        }
        return android.hardware.wifi.supplicant.V1_2.ISupplicantStaNetwork.castFrom((IHwInterface) iSupplicantStaNetwork);
    }

    public boolean sendNetworkEapIdentityResponse(String identityStr, String encryptedIdentityStr) {
        boolean sendNetworkEapIdentityResponse;
        synchronized (this.mLock) {
            try {
                ArrayList<Byte> unencryptedIdentity = NativeUtil.stringToByteArrayList(identityStr);
                ArrayList<Byte> encryptedIdentity = null;
                if (!TextUtils.isEmpty(encryptedIdentityStr)) {
                    encryptedIdentity = NativeUtil.stringToByteArrayList(encryptedIdentityStr);
                }
                sendNetworkEapIdentityResponse = sendNetworkEapIdentityResponse(unencryptedIdentity, encryptedIdentity);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Illegal argument " + identityStr + "," + encryptedIdentityStr, e);
                return false;
            } catch (Throwable th) {
                throw th;
            }
        }
        return sendNetworkEapIdentityResponse;
    }

    private boolean sendNetworkEapIdentityResponse(ArrayList<Byte> unencryptedIdentity, ArrayList<Byte> encryptedIdentity) {
        SupplicantStatus status;
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("sendNetworkEapIdentityResponse")) {
                return false;
            }
            try {
                android.hardware.wifi.supplicant.V1_1.ISupplicantStaNetwork iSupplicantStaNetworkV11 = getSupplicantStaNetworkForV1_1Mockable();
                if (iSupplicantStaNetworkV11 == null || encryptedIdentity == null) {
                    status = this.mISupplicantStaNetwork.sendNetworkEapIdentityResponse(unencryptedIdentity);
                } else {
                    status = iSupplicantStaNetworkV11.sendNetworkEapIdentityResponse_1_1(unencryptedIdentity, encryptedIdentity);
                }
                return checkStatusAndLogFailure(status, "sendNetworkEapIdentityResponse");
            } catch (RemoteException e) {
                handleRemoteException(e, "sendNetworkEapIdentityResponse");
                return false;
            }
        }
    }

    public String getWpsNfcConfigurationToken() {
        synchronized (this.mLock) {
            ArrayList<Byte> token = getWpsNfcConfigurationTokenInternal();
            if (token == null) {
                return null;
            }
            return NativeUtil.hexStringFromByteArray(NativeUtil.byteArrayFromArrayList(token));
        }
    }

    private ArrayList<Byte> getWpsNfcConfigurationTokenInternal() {
        synchronized (this.mLock) {
            if (!checkISupplicantStaNetworkAndLogFailure("getWpsNfcConfigurationToken")) {
                return null;
            }
            HidlSupport.Mutable<ArrayList<Byte>> gotToken = new HidlSupport.Mutable<>();
            try {
                this.mISupplicantStaNetwork.getWpsNfcConfigurationToken(new ISupplicantStaNetwork.getWpsNfcConfigurationTokenCallback(gotToken) {
                    /* class com.android.server.wifi.$$Lambda$SupplicantStaNetworkHal$Iu6ZjRj7Hxj9q7tfVXjbYSVrMP0 */
                    private final /* synthetic */ HidlSupport.Mutable f$1;

                    {
                        this.f$1 = r2;
                    }

                    @Override // android.hardware.wifi.supplicant.V1_0.ISupplicantStaNetwork.getWpsNfcConfigurationTokenCallback
                    public final void onValues(SupplicantStatus supplicantStatus, ArrayList arrayList) {
                        SupplicantStaNetworkHal.this.mo2667xe98c0c14(this.f$1, supplicantStatus, arrayList);
                    }
                });
            } catch (RemoteException e) {
                handleRemoteException(e, "getWpsNfcConfigurationToken");
            }
            return (ArrayList) gotToken.value;
        }
    }

    /* renamed from: lambda$getWpsNfcConfigurationTokenInternal$40$SupplicantStaNetworkHal */
    public /* synthetic */ void mo2667xe98c0c14(HidlSupport.Mutable gotToken, SupplicantStatus status, ArrayList token) {
        if (checkStatusAndLogFailure(status, "getWpsNfcConfigurationToken")) {
            gotToken.value = token;
        }
    }

    private boolean checkStatusAndLogFailure(SupplicantStatus status, String methodStr) {
        synchronized (this.mLock) {
            if (status.code != 0) {
                Log.e(TAG, "ISupplicantStaNetwork." + methodStr + " failed: " + status);
                return false;
            }
            if (this.mVerboseLoggingEnabled) {
                Log.d(TAG, "ISupplicantStaNetwork." + methodStr + " succeeded");
            }
            return true;
        }
    }

    private boolean checkStatusAndLogFailureExt(SupplicantStatus status, String methodStr) {
        synchronized (this.mLock) {
            if (status.code != 0) {
                Log.e(TAG, "ISehSupplicantStaNetwork." + methodStr + " failed: " + status);
                return false;
            }
            if (this.mVerboseLoggingEnabled) {
                Log.d(TAG, "ISehSupplicantStaNetwork." + methodStr + " succeeded");
            }
            return true;
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void logCallback(String methodStr) {
        synchronized (this.mLock) {
            if (this.mVerboseLoggingEnabled) {
                Log.d(TAG, "ISupplicantStaNetworkCallback." + methodStr + " received");
            }
        }
    }

    private boolean checkISupplicantStaNetworkAndLogFailure(String methodStr) {
        synchronized (this.mLock) {
            if (this.mISupplicantStaNetwork != null) {
                return true;
            }
            Log.e(TAG, "Can't call " + methodStr + ", ISupplicantStaNetwork is null");
            return false;
        }
    }

    private void handleRemoteException(RemoteException e, String methodStr) {
        synchronized (this.mLock) {
            this.mISupplicantStaNetwork = null;
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + " failed with exception", e);
        }
    }

    public boolean updateCurrentBss() {
        synchronized (this.mLock) {
            if (!"".equals(CONFIG_SECURE_SVC_INTEGRATION) || SemCscFeature.getInstance().getBoolean(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_DISABLEMWIPS)) {
                return false;
            }
            if (!checkISupplicantStaNetworkExtAndLogFailure("updateCurrentBss")) {
                Log.e(TAG, "MWIPS updateCurrentBss failed");
                return false;
            }
            try {
                Log.d(TAG, "MWIPS updateCurrentBss");
                MutableBoolean statusOk = new MutableBoolean(false);
                this.mISehSupplicantStaNetwork.getBss(new ISehSupplicantStaNetwork.getBssCallback(statusOk) {
                    /* class com.android.server.wifi.$$Lambda$SupplicantStaNetworkHal$dY4dIpkzsy3ZvqihpmQ91SYOXY */
                    private final /* synthetic */ MutableBoolean f$1;

                    {
                        this.f$1 = r2;
                    }

                    @Override // vendor.samsung.hardware.wifi.supplicant.V2_0.ISehSupplicantStaNetwork.getBssCallback
                    public final void onValues(SupplicantStatus supplicantStatus, ISehSupplicantStaNetwork.BssParam bssParam) {
                        SupplicantStaNetworkHal.this.lambda$updateCurrentBss$41$SupplicantStaNetworkHal(this.f$1, supplicantStatus, bssParam);
                    }
                });
                return statusOk.value;
            } catch (RemoteException e) {
                handleRemoteExceptionExt(e, "updateCurrentBss");
                Log.e(TAG, "MWIPS getBss exception");
                return false;
            }
        }
    }

    public /* synthetic */ void lambda$updateCurrentBss$41$SupplicantStaNetworkHal(MutableBoolean statusOk, SupplicantStatus status, ISehSupplicantStaNetwork.BssParam param) {
        statusOk.value = status.code == 0;
        MobileWipsFrameworkService mwfs = MobileWipsFrameworkService.getInstance();
        if (statusOk.value) {
            if (mwfs != null) {
                mwfs.setCurrentBss(param);
            }
            if (param != null) {
                Log.d(TAG, "MWIPS updateCurrentBss bssid " + NativeUtil.macAddressFromByteArray(param.bssid) + " " + param.ssid);
                return;
            }
            return;
        }
        checkStatusAndLogFailureExt(status, "updateCurrentBss");
        if (mwfs != null) {
            mwfs.setCurrentBss(null);
        }
    }

    private BitSet addFastTransitionFlags(BitSet keyManagementFlags) {
        synchronized (this.mLock) {
            if (!this.mSystemSupportsFastBssTransition) {
                return keyManagementFlags;
            }
            BitSet modifiedFlags = (BitSet) keyManagementFlags.clone();
            if (keyManagementFlags.get(1)) {
                modifiedFlags.set(6);
            }
            if (keyManagementFlags.get(2)) {
                modifiedFlags.set(7);
            }
            return modifiedFlags;
        }
    }

    private boolean checkISupplicantStaNetworkExtAndLogFailure(String methodStr) {
        synchronized (this.mLock) {
            if (this.mISehSupplicantStaNetwork != null) {
                return true;
            }
            Log.e(TAG, "Can't call " + methodStr + ", ISehSupplicantStaNetwork is null");
            return false;
        }
    }

    private void handleRemoteExceptionExt(RemoteException e, String methodStr) {
        synchronized (this.mLock) {
            this.mISehSupplicantStaNetwork = null;
            Log.e(TAG, "ISehSupplicantStaNetwork." + methodStr + " failed with exception", e);
        }
    }

    private BitSet removeFastTransitionFlags(BitSet keyManagementFlags) {
        BitSet modifiedFlags;
        synchronized (this.mLock) {
            modifiedFlags = (BitSet) keyManagementFlags.clone();
            modifiedFlags.clear(6);
            modifiedFlags.clear(7);
        }
        return modifiedFlags;
    }

    private BitSet addSha256KeyMgmtFlags(BitSet keyManagementFlags) {
        synchronized (this.mLock) {
            BitSet modifiedFlags = (BitSet) keyManagementFlags.clone();
            if (getV1_2StaNetwork() == null) {
                return modifiedFlags;
            }
            if (keyManagementFlags.get(1)) {
                modifiedFlags.set(11);
            }
            if (keyManagementFlags.get(2)) {
                modifiedFlags.set(12);
            }
            return modifiedFlags;
        }
    }

    private BitSet removeSha256KeyMgmtFlags(BitSet keyManagementFlags) {
        BitSet modifiedFlags;
        synchronized (this.mLock) {
            modifiedFlags = (BitSet) keyManagementFlags.clone();
            modifiedFlags.clear(11);
            modifiedFlags.clear(12);
        }
        return modifiedFlags;
    }

    private BitSet addFils256KeyMgmtFlags(BitSet keyManagementFlags) {
        synchronized (this.mLock) {
            if (!this.mSystemSupportsFilsKeyMgmt) {
                return keyManagementFlags;
            }
            BitSet modifiedFlags = (BitSet) keyManagementFlags.clone();
            if (keyManagementFlags.get(2)) {
                modifiedFlags.set(20);
            }
            return modifiedFlags;
        }
    }

    private BitSet removeFils256KeyMgmtFlags(BitSet keyManagementFlags) {
        BitSet modifiedFlags;
        synchronized (this.mLock) {
            modifiedFlags = (BitSet) keyManagementFlags.clone();
            modifiedFlags.clear(20);
        }
        return modifiedFlags;
    }

    public static String createNetworkExtra(Map<String, String> values) {
        try {
            return URLEncoder.encode(new JSONObject(values).toString(), "UTF-8");
        } catch (NullPointerException e) {
            Log.e(TAG, "Unable to serialize networkExtra: " + e.toString());
            return null;
        } catch (UnsupportedEncodingException e2) {
            Log.e(TAG, "Unable to serialize networkExtra: " + e2.toString());
            return null;
        }
    }

    private String removeDoubleQuotes(String string) {
        if (TextUtils.isEmpty(string)) {
            return "";
        }
        int length = string.length();
        if (length > 1 && string.charAt(0) == '\"' && string.charAt(length - 1) == '\"') {
            return string.substring(1, length - 1);
        }
        return string;
    }

    public static Map<String, String> parseNetworkExtra(String encoded) {
        if (TextUtils.isEmpty(encoded)) {
            return null;
        }
        try {
            JSONObject json = new JSONObject(URLDecoder.decode(encoded, "UTF-8"));
            Map<String, String> values = new HashMap<>();
            Iterator<?> it = json.keys();
            while (it.hasNext()) {
                String key = it.next();
                Object value = json.get(key);
                if (value instanceof String) {
                    values.put(key, (String) value);
                }
            }
            return values;
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "Unable to deserialize networkExtra: " + e.toString());
            return null;
        } catch (JSONException e2) {
            return null;
        }
    }

    /* access modifiers changed from: private */
    public class SupplicantStaNetworkHalCallback extends ISupplicantStaNetworkCallback.Stub {
        private final int mFramewokNetworkId;
        private final String mSsid;

        SupplicantStaNetworkHalCallback(int framewokNetworkId, String ssid) {
            this.mFramewokNetworkId = framewokNetworkId;
            this.mSsid = ssid;
        }

        @Override // android.hardware.wifi.supplicant.V1_0.ISupplicantStaNetworkCallback
        public void onNetworkEapSimGsmAuthRequest(ISupplicantStaNetworkCallback.NetworkRequestEapSimGsmAuthParams params) {
            synchronized (SupplicantStaNetworkHal.this.mLock) {
                SupplicantStaNetworkHal.this.logCallback("onNetworkEapSimGsmAuthRequest");
                String[] data = new String[params.rands.size()];
                int i = 0;
                Iterator<byte[]> it = params.rands.iterator();
                while (it.hasNext()) {
                    data[i] = NativeUtil.hexStringFromByteArray(it.next());
                    i++;
                }
                SupplicantStaNetworkHal.this.mWifiMonitor.broadcastNetworkGsmAuthRequestEvent(SupplicantStaNetworkHal.this.mIfaceName, this.mFramewokNetworkId, this.mSsid, data);
            }
        }

        @Override // android.hardware.wifi.supplicant.V1_0.ISupplicantStaNetworkCallback
        public void onNetworkEapSimUmtsAuthRequest(ISupplicantStaNetworkCallback.NetworkRequestEapSimUmtsAuthParams params) {
            synchronized (SupplicantStaNetworkHal.this.mLock) {
                SupplicantStaNetworkHal.this.logCallback("onNetworkEapSimUmtsAuthRequest");
                SupplicantStaNetworkHal.this.mWifiMonitor.broadcastNetworkUmtsAuthRequestEvent(SupplicantStaNetworkHal.this.mIfaceName, this.mFramewokNetworkId, this.mSsid, new String[]{NativeUtil.hexStringFromByteArray(params.rand), NativeUtil.hexStringFromByteArray(params.autn)});
            }
        }

        @Override // android.hardware.wifi.supplicant.V1_0.ISupplicantStaNetworkCallback
        public void onNetworkEapIdentityRequest() {
            synchronized (SupplicantStaNetworkHal.this.mLock) {
                SupplicantStaNetworkHal.this.logCallback("onNetworkEapIdentityRequest");
                SupplicantStaNetworkHal.this.mWifiMonitor.broadcastNetworkIdentityRequestEvent(SupplicantStaNetworkHal.this.mIfaceName, this.mFramewokNetworkId, this.mSsid);
            }
        }
    }
}
