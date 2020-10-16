package com.android.server.wifi.util;

import android.hardware.wifi.supplicant.V1_0.ISupplicantStaIfaceCallback;
import android.net.IpConfiguration;
import android.net.LinkAddress;
import android.net.MacAddress;
import android.net.NetworkUtils;
import android.net.ProxyInfo;
import android.net.RouteInfo;
import android.net.StaticIpConfiguration;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiEnterpriseConfig;
import android.util.Log;
import android.util.Pair;
import com.android.internal.util.XmlUtils;
import com.android.server.wifi.WifiBackupRestore;
import com.android.server.wifi.WifiLog;
import com.samsung.android.server.wifi.WifiPasswordManager;
import com.samsung.android.server.wifi.share.McfDataUtil;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

public class XmlUtil {
    private static final String TAG = "WifiXmlUtil";

    private static void gotoStartTag(XmlPullParser in) throws XmlPullParserException, IOException {
        int type = in.getEventType();
        while (type != 2 && type != 1) {
            type = in.next();
        }
    }

    private static void gotoEndTag(XmlPullParser in) throws XmlPullParserException, IOException {
        int type = in.getEventType();
        while (type != 3 && type != 1) {
            type = in.next();
        }
    }

    public static void gotoDocumentStart(XmlPullParser in, String headerName) throws XmlPullParserException, IOException {
        XmlUtils.beginDocument(in, headerName);
    }

    public static boolean gotoNextSectionOrEnd(XmlPullParser in, String[] headerName, int outerDepth) throws XmlPullParserException, IOException {
        if (!XmlUtils.nextElementWithin(in, outerDepth)) {
            return false;
        }
        headerName[0] = in.getName();
        return true;
    }

    public static boolean gotoNextSectionWithNameOrEnd(XmlPullParser in, String expectedName, int outerDepth) throws XmlPullParserException, IOException {
        String[] headerName = new String[1];
        if (!gotoNextSectionOrEnd(in, headerName, outerDepth)) {
            return false;
        }
        if (headerName[0].equals(expectedName)) {
            return true;
        }
        throw new XmlPullParserException("Next section name does not match expected name: " + expectedName);
    }

    public static void gotoNextSectionWithName(XmlPullParser in, String expectedName, int outerDepth) throws XmlPullParserException, IOException {
        if (!gotoNextSectionWithNameOrEnd(in, expectedName, outerDepth)) {
            throw new XmlPullParserException("Section not found. Expected: " + expectedName);
        }
    }

    public static boolean isNextSectionEnd(XmlPullParser in, int sectionDepth) throws XmlPullParserException, IOException {
        return !XmlUtils.nextElementWithin(in, sectionDepth);
    }

    public static Object readCurrentValue(XmlPullParser in, String[] valueName) throws XmlPullParserException, IOException {
        Object value = XmlUtils.readValueXml(in, valueName);
        gotoEndTag(in);
        return value;
    }

    public static Object readNextValueWithName(XmlPullParser in, String expectedName) throws XmlPullParserException, IOException {
        String[] valueName = new String[1];
        XmlUtils.nextElement(in);
        Object value = readCurrentValue(in, valueName);
        if (valueName[0].equals(expectedName)) {
            return value;
        }
        throw new XmlPullParserException("Value not found. Expected: " + expectedName + ", but got: " + valueName[0]);
    }

    public static void writeDocumentStart(XmlSerializer out, String headerName) throws IOException {
        out.startDocument(null, true);
        out.startTag(null, headerName);
    }

    public static void writeDocumentEnd(XmlSerializer out, String headerName) throws IOException {
        out.endTag(null, headerName);
        out.endDocument();
    }

    public static void writeNextSectionStart(XmlSerializer out, String headerName) throws IOException {
        out.startTag(null, headerName);
    }

    public static void writeNextSectionEnd(XmlSerializer out, String headerName) throws IOException {
        out.endTag(null, headerName);
    }

    public static void writeNextValue(XmlSerializer out, String name, Object value) throws XmlPullParserException, IOException {
        XmlUtils.writeValueXml(value, name, out);
    }

    public static class WifiConfigurationXmlUtil {
        public static final String XML_TAG_ALLOWED_AUTH_ALGOS = "AllowedAuthAlgos";
        public static final String XML_TAG_ALLOWED_GROUP_CIPHERS = "AllowedGroupCiphers";
        public static final String XML_TAG_ALLOWED_GROUP_MGMT_CIPHERS = "AllowedGroupMgmtCiphers";
        public static final String XML_TAG_ALLOWED_KEY_MGMT = "AllowedKeyMgmt";
        public static final String XML_TAG_ALLOWED_PAIRWISE_CIPHERS = "AllowedPairwiseCiphers";
        public static final String XML_TAG_ALLOWED_PROTOCOLS = "AllowedProtocols";
        public static final String XML_TAG_ALLOWED_SUITE_B_CIPHERS = "AllowedSuiteBCiphers";
        public static final String XML_TAG_AUTHENTICATED = "Authenticated";
        public static final String XML_TAG_AUTO_RECONNECT = "AutoReconnect";
        public static final String XML_TAG_AUTO_WIFI_SCORE_KEY = "SEM_AUTO_WIFI_SCORE";
        public static final String XML_TAG_BSSID = "BSSID";
        public static final String XML_TAG_BSSID_WHITELIST_KEY = "BssidWhitelist";
        public static final String XML_TAG_BSSID_WHITELIST_UPDATE_TIME_KEY = "BssidWhitelistUpdateTime";
        public static final String XML_TAG_CAPTIVE_PORTAL = "CaptivePortal";
        public static final String XML_TAG_CONFIG_KEY = "ConfigKey";
        public static final String XML_TAG_CREATION_TIME = "CreationTime";
        public static final String XML_TAG_CREATOR_NAME = "CreatorName";
        public static final String XML_TAG_CREATOR_UID = "CreatorUid";
        public static final String XML_TAG_DEFAULT_GW_MAC_ADDRESS = "DefaultGwMacAddress";
        public static final String XML_TAG_ENTRY_RSSI_24GHZ = "EntryRssi24GHz";
        public static final String XML_TAG_ENTRY_RSSI_5GHZ = "EntryRssi5GHz";
        public static final String XML_TAG_FQDN = "FQDN";
        public static final String XML_TAG_HIDDEN_SSID = "HiddenSSID";
        public static final String XML_TAG_IS_LEGACY_PASSPOINT_CONFIG = "IsLegacyPasspointConfig";
        public static final String XML_TAG_IS_RECOMMENDED = "semIsRecommended";
        public static final String XML_TAG_LAST_CONNECTED_TIME_KEY = "LastConnectedTime";
        public static final String XML_TAG_LAST_CONNECT_UID = "LastConnectUid";
        public static final String XML_TAG_LAST_UPDATE_NAME = "LastUpdateName";
        public static final String XML_TAG_LAST_UPDATE_UID = "LastUpdateUid";
        public static final String XML_TAG_LINKED_NETWORKS_LIST = "LinkedNetworksList";
        public static final String XML_TAG_LOGIN_URL = "LoginUrl";
        public static final String XML_TAG_MAC_RANDOMIZATION_SETTING = "MacRandomizationSetting";
        public static final String XML_TAG_METERED_HINT = "MeteredHint";
        public static final String XML_TAG_METERED_OVERRIDE = "MeteredOverride";
        public static final String XML_TAG_NEXT_TARGET_RSSI = "NextTargetRssi";
        public static final String XML_TAG_NO_INTERNET_ACCESS_EXPECTED = "NoInternetAccessExpected";
        public static final String XML_TAG_NUM_ASSOCIATION = "NumAssociation";
        public static final String XML_TAG_PRE_SHARED_KEY = "PreSharedKey";
        public static final String XML_TAG_PROVIDER_FRIENDLY_NAME = "ProviderFriendlyName";
        public static final String XML_TAG_RANDOMIZED_MAC_ADDRESS = "RandomizedMacAddress";
        public static final String XML_TAG_REQUIRE_PMF = "RequirePMF";
        public static final String XML_TAG_ROAMING_CONSORTIUM_OIS = "RoamingConsortiumOIs";
        public static final String XML_TAG_SAMSUNG_SPECIFIC_FLAGS = "SamsungSpecificFlags";
        public static final String XML_TAG_SEM_CREATION_TIME_KEY = "semCreationTime";
        public static final String XML_TAG_SEM_LOCATION_LATI = "SemLocationLatitude";
        public static final String XML_TAG_SEM_LOCATION_LONG = "SemLocationLongitude";
        public static final String XML_TAG_SEM_UPDATE_TIME_KEY = "semUpdateTime";
        public static final String XML_TAG_SHARED = "Shared";
        public static final String XML_TAG_SKIP_INTERNET_CHECK = "SkipInternetCheck";
        public static final String XML_TAG_SSID = "SSID";
        public static final String XML_TAG_STATUS = "Status";
        public static final String XML_TAG_USABLE_INTERNET = "UsableInternet";
        public static final String XML_TAG_USER_APPROVED = "UserApproved";
        public static final String XML_TAG_USE_EXTERNAL_SCORES = "UseExternalScores";
        public static final String XML_TAG_VALIDATED_INTERNET_ACCESS = "ValidatedInternetAccess";
        public static final String XML_TAG_VENDOR_SSID = "isVendorSpecificSsid";
        public static final String XML_TAG_WAPI_AS_CERT = "WapiAsCert";
        public static final String XML_TAG_WAPI_CERT_INDEX = "WapiCertIndex";
        public static final String XML_TAG_WAPI_PSK_KEY_TYPE = "WapiPskKeyType";
        public static final String XML_TAG_WAPI_USER_CERT = "WapiUserCert";
        public static final String XML_TAG_WEP_KEYS = "WEPKeys";
        public static final String XML_TAG_WEP_TX_KEY_INDEX = "WEPTxKeyIndex";

        private static void writeWepKeysToXml(XmlSerializer out, String[] wepKeys, boolean withEncryption) throws XmlPullParserException, IOException {
            String[] wepKeysToWrite = new String[wepKeys.length];
            boolean hasWepKey = false;
            for (int i = 0; i < wepKeys.length; i++) {
                if (wepKeys[i] == null) {
                    wepKeysToWrite[i] = new String();
                } else {
                    if (withEncryption) {
                        wepKeysToWrite[i] = WifiPasswordManager.getInstance().encrypt(wepKeys[i]);
                    } else {
                        wepKeysToWrite[i] = wepKeys[i];
                    }
                    hasWepKey = true;
                }
            }
            if (hasWepKey) {
                XmlUtil.writeNextValue(out, XML_TAG_WEP_KEYS, wepKeysToWrite);
            } else {
                XmlUtil.writeNextValue(out, XML_TAG_WEP_KEYS, null);
            }
        }

        public static void writeCommonElementsToXml(XmlSerializer out, WifiConfiguration configuration, boolean withEncryption) throws XmlPullParserException, IOException {
            XmlUtil.writeNextValue(out, XML_TAG_CONFIG_KEY, configuration.configKey());
            XmlUtil.writeNextValue(out, XML_TAG_SSID, configuration.SSID);
            XmlUtil.writeNextValue(out, XML_TAG_BSSID, configuration.BSSID);
            if (withEncryption) {
                XmlUtil.writeNextValue(out, XML_TAG_PRE_SHARED_KEY, WifiPasswordManager.getInstance().encrypt(configuration.preSharedKey));
            } else {
                XmlUtil.writeNextValue(out, XML_TAG_PRE_SHARED_KEY, configuration.preSharedKey);
            }
            writeWepKeysToXml(out, configuration.wepKeys, withEncryption);
            XmlUtil.writeNextValue(out, XML_TAG_WEP_TX_KEY_INDEX, Integer.valueOf(configuration.wepTxKeyIndex));
            XmlUtil.writeNextValue(out, XML_TAG_HIDDEN_SSID, Boolean.valueOf(configuration.hiddenSSID));
            XmlUtil.writeNextValue(out, XML_TAG_REQUIRE_PMF, Boolean.valueOf(configuration.requirePMF));
            XmlUtil.writeNextValue(out, XML_TAG_ALLOWED_KEY_MGMT, configuration.allowedKeyManagement.toByteArray());
            XmlUtil.writeNextValue(out, XML_TAG_ALLOWED_PROTOCOLS, configuration.allowedProtocols.toByteArray());
            XmlUtil.writeNextValue(out, XML_TAG_ALLOWED_AUTH_ALGOS, configuration.allowedAuthAlgorithms.toByteArray());
            XmlUtil.writeNextValue(out, XML_TAG_ALLOWED_GROUP_CIPHERS, configuration.allowedGroupCiphers.toByteArray());
            XmlUtil.writeNextValue(out, XML_TAG_ALLOWED_PAIRWISE_CIPHERS, configuration.allowedPairwiseCiphers.toByteArray());
            XmlUtil.writeNextValue(out, XML_TAG_ALLOWED_GROUP_MGMT_CIPHERS, configuration.allowedGroupManagementCiphers.toByteArray());
            XmlUtil.writeNextValue(out, XML_TAG_ALLOWED_SUITE_B_CIPHERS, configuration.allowedSuiteBCiphers.toByteArray());
            XmlUtil.writeNextValue(out, XML_TAG_SHARED, Boolean.valueOf(configuration.shared));
        }

        public static void writeToXmlForBackup(XmlSerializer out, WifiConfiguration configuration) throws XmlPullParserException, IOException {
            writeCommonElementsToXml(out, configuration, false);
            XmlUtil.writeNextValue(out, XML_TAG_METERED_OVERRIDE, Integer.valueOf(configuration.meteredOverride));
        }

        public static void writeToXmlForConfigStore(XmlSerializer out, WifiConfiguration configuration) throws XmlPullParserException, IOException {
            writeCommonElementsToXml(out, configuration, WifiPasswordManager.getInstance().isSupported());
            XmlUtil.writeNextValue(out, XML_TAG_STATUS, Integer.valueOf(configuration.status));
            XmlUtil.writeNextValue(out, XML_TAG_FQDN, configuration.FQDN);
            XmlUtil.writeNextValue(out, XML_TAG_PROVIDER_FRIENDLY_NAME, configuration.providerFriendlyName);
            XmlUtil.writeNextValue(out, XML_TAG_LINKED_NETWORKS_LIST, configuration.linkedConfigurations);
            XmlUtil.writeNextValue(out, XML_TAG_DEFAULT_GW_MAC_ADDRESS, configuration.defaultGwMacAddress);
            XmlUtil.writeNextValue(out, XML_TAG_VALIDATED_INTERNET_ACCESS, Boolean.valueOf(configuration.validatedInternetAccess));
            XmlUtil.writeNextValue(out, XML_TAG_NO_INTERNET_ACCESS_EXPECTED, Boolean.valueOf(configuration.noInternetAccessExpected));
            XmlUtil.writeNextValue(out, XML_TAG_USER_APPROVED, Integer.valueOf(configuration.userApproved));
            XmlUtil.writeNextValue(out, XML_TAG_METERED_HINT, Boolean.valueOf(configuration.meteredHint));
            XmlUtil.writeNextValue(out, XML_TAG_METERED_OVERRIDE, Integer.valueOf(configuration.meteredOverride));
            XmlUtil.writeNextValue(out, XML_TAG_USE_EXTERNAL_SCORES, Boolean.valueOf(configuration.useExternalScores));
            XmlUtil.writeNextValue(out, XML_TAG_NUM_ASSOCIATION, Integer.valueOf(configuration.numAssociation));
            XmlUtil.writeNextValue(out, XML_TAG_CREATOR_UID, Integer.valueOf(configuration.creatorUid));
            XmlUtil.writeNextValue(out, XML_TAG_CREATOR_NAME, configuration.creatorName);
            XmlUtil.writeNextValue(out, XML_TAG_CREATION_TIME, configuration.creationTime);
            XmlUtil.writeNextValue(out, XML_TAG_LAST_UPDATE_UID, Integer.valueOf(configuration.lastUpdateUid));
            XmlUtil.writeNextValue(out, XML_TAG_LAST_UPDATE_NAME, configuration.lastUpdateName);
            XmlUtil.writeNextValue(out, XML_TAG_LAST_CONNECT_UID, Integer.valueOf(configuration.lastConnectUid));
            XmlUtil.writeNextValue(out, XML_TAG_IS_LEGACY_PASSPOINT_CONFIG, Boolean.valueOf(configuration.isLegacyPasspointConfig));
            XmlUtil.writeNextValue(out, XML_TAG_ROAMING_CONSORTIUM_OIS, configuration.roamingConsortiumIds);
            XmlUtil.writeNextValue(out, XML_TAG_RANDOMIZED_MAC_ADDRESS, configuration.getRandomizedMacAddress().toString());
            XmlUtil.writeNextValue(out, XML_TAG_MAC_RANDOMIZATION_SETTING, Integer.valueOf(configuration.macRandomizationSetting));
            XmlUtil.writeNextValue(out, XML_TAG_SEM_CREATION_TIME_KEY, Long.valueOf(configuration.semCreationTime));
            XmlUtil.writeNextValue(out, XML_TAG_SEM_UPDATE_TIME_KEY, Long.valueOf(configuration.semUpdateTime));
            XmlUtil.writeNextValue(out, XML_TAG_LAST_CONNECTED_TIME_KEY, Long.valueOf(configuration.lastConnected));
            if (configuration.bssidWhitelist != null && configuration.bssidWhitelist.size() > 0) {
                for (Map.Entry<String, Long> entry : configuration.bssidWhitelist.entrySet()) {
                    XmlUtil.writeNextValue(out, XML_TAG_BSSID_WHITELIST_KEY, entry.getKey());
                    XmlUtil.writeNextValue(out, XML_TAG_BSSID_WHITELIST_UPDATE_TIME_KEY, entry.getValue());
                }
            }
            XmlUtil.writeNextValue(out, XML_TAG_VENDOR_SSID, Boolean.valueOf(configuration.semIsVendorSpecificSsid));
            XmlUtil.writeNextValue(out, XML_TAG_AUTO_RECONNECT, Integer.valueOf(configuration.semAutoReconnect));
            XmlUtil.writeNextValue(out, XML_TAG_IS_RECOMMENDED, Boolean.valueOf(configuration.semIsRecommended));
            XmlUtil.writeNextValue(out, XML_TAG_SAMSUNG_SPECIFIC_FLAGS, configuration.semSamsungSpecificFlags.toByteArray());
            XmlUtil.writeNextValue(out, XML_TAG_AUTO_WIFI_SCORE_KEY, Integer.valueOf(configuration.semAutoWifiScore));
            XmlUtil.writeNextValue(out, XML_TAG_WAPI_PSK_KEY_TYPE, Integer.valueOf(configuration.wapiPskType));
            XmlUtil.writeNextValue(out, XML_TAG_WAPI_CERT_INDEX, Integer.valueOf(configuration.wapiCertIndex));
            XmlUtil.writeNextValue(out, "WapiAsCert", configuration.wapiAsCert);
            XmlUtil.writeNextValue(out, "WapiUserCert", configuration.wapiUserCert);
            XmlUtil.writeNextValue(out, XML_TAG_ENTRY_RSSI_24GHZ, Integer.valueOf(configuration.entryRssi24GHz));
            XmlUtil.writeNextValue(out, XML_TAG_ENTRY_RSSI_5GHZ, Integer.valueOf(configuration.entryRssi5GHz));
            XmlUtil.writeNextValue(out, XML_TAG_CAPTIVE_PORTAL, Boolean.valueOf(configuration.isCaptivePortal));
        }

        private static void populateWepKeysFromXmlValue(Object value, String[] wepKeys, boolean withEncryption) throws XmlPullParserException, IOException {
            String[] wepKeysInData = (String[]) value;
            if (wepKeysInData != null) {
                if (wepKeysInData.length == wepKeys.length) {
                    for (int i = 0; i < wepKeys.length; i++) {
                        if (wepKeysInData[i].isEmpty()) {
                            wepKeys[i] = null;
                        } else if (withEncryption) {
                            wepKeys[i] = WifiPasswordManager.getInstance().decrypt(wepKeysInData[i]);
                        } else {
                            wepKeys[i] = wepKeysInData[i];
                        }
                    }
                    return;
                }
                throw new XmlPullParserException("Invalid Wep Keys length: " + wepKeysInData.length);
            }
        }

        public static Pair<String, WifiConfiguration> parseFromXml(XmlPullParser in, int outerTagDepth) throws XmlPullParserException, IOException {
            if (WifiPasswordManager.getInstance().isSupported()) {
                return parseFromXml(in, outerTagDepth, true);
            }
            return parseFromXml(in, outerTagDepth, false);
        }

        /* JADX INFO: Can't fix incorrect switch cases order, some code will duplicate */
        /* JADX WARNING: Code restructure failed: missing block: B:110:0x01bf, code lost:
            if (r10.equals(com.android.server.wifi.util.XmlUtil.WifiConfigurationXmlUtil.XML_TAG_SSID) != false) goto L_0x02ff;
         */
        private static Pair<String, WifiConfiguration> parseFromXml(XmlPullParser in, int outerTagDepth, boolean withEncryption) throws XmlPullParserException, IOException {
            WifiConfiguration configuration = new WifiConfiguration();
            String configKeyInData = null;
            boolean macRandomizationSettingExists = false;
            String bssidWhitelist = null;
            while (!XmlUtil.isNextSectionEnd(in, outerTagDepth)) {
                char c = 1;
                String[] valueName = new String[1];
                Object value = XmlUtil.readCurrentValue(in, valueName);
                if (valueName[0] != null) {
                    String str = valueName[0];
                    switch (str.hashCode()) {
                        case -2072453770:
                            if (str.equals(XML_TAG_DEFAULT_GW_MAC_ADDRESS)) {
                                c = 20;
                                break;
                            }
                            c = 65535;
                            break;
                        case -1845648133:
                            if (str.equals(XML_TAG_NEXT_TARGET_RSSI)) {
                                c = '9';
                                break;
                            }
                            c = 65535;
                            break;
                        case -1819699067:
                            if (str.equals(XML_TAG_SHARED)) {
                                c = 15;
                                break;
                            }
                            c = 65535;
                            break;
                        case -1808614382:
                            if (str.equals(XML_TAG_STATUS)) {
                                c = 16;
                                break;
                            }
                            c = 65535;
                            break;
                        case -1793081233:
                            if (str.equals(XML_TAG_METERED_HINT)) {
                                c = 24;
                                break;
                            }
                            c = 65535;
                            break;
                        case -1757331736:
                            if (str.equals(XML_TAG_SKIP_INTERNET_CHECK)) {
                                c = '8';
                                break;
                            }
                            c = 65535;
                            break;
                        case -1704616680:
                            if (str.equals(XML_TAG_ALLOWED_KEY_MGMT)) {
                                c = '\b';
                                break;
                            }
                            c = 65535;
                            break;
                        case -1681005553:
                            if (str.equals(XML_TAG_AUTHENTICATED)) {
                                c = '5';
                                break;
                            }
                            c = 65535;
                            break;
                        case -1663465224:
                            if (str.equals(XML_TAG_RANDOMIZED_MAC_ADDRESS)) {
                                c = '$';
                                break;
                            }
                            c = 65535;
                            break;
                        case -1568560548:
                            if (str.equals(XML_TAG_LAST_CONNECT_UID)) {
                                c = '!';
                                break;
                            }
                            c = 65535;
                            break;
                        case -1406508014:
                            if (str.equals("WapiUserCert")) {
                                c = '3';
                                break;
                            }
                            c = 65535;
                            break;
                        case -1268502125:
                            if (str.equals(XML_TAG_VALIDATED_INTERNET_ACCESS)) {
                                c = 21;
                                break;
                            }
                            c = 65535;
                            break;
                        case -1173588624:
                            if (str.equals(XML_TAG_ALLOWED_GROUP_MGMT_CIPHERS)) {
                                c = '\r';
                                break;
                            }
                            c = 65535;
                            break;
                        case -1089007030:
                            if (str.equals(XML_TAG_LAST_UPDATE_NAME)) {
                                c = ' ';
                                break;
                            }
                            c = 65535;
                            break;
                        case -1073823059:
                            if (str.equals(XML_TAG_ENTRY_RSSI_5GHZ)) {
                                c = ';';
                                break;
                            }
                            c = 65535;
                            break;
                        case -1053711239:
                            if (str.equals(XML_TAG_USABLE_INTERNET)) {
                                c = '7';
                                break;
                            }
                            c = 65535;
                            break;
                        case -984540844:
                            if (str.equals(XML_TAG_AUTO_WIFI_SCORE_KEY)) {
                                c = '/';
                                break;
                            }
                            c = 65535;
                            break;
                        case -922179420:
                            if (str.equals(XML_TAG_CREATOR_UID)) {
                                c = 28;
                                break;
                            }
                            c = 65535;
                            break;
                        case -852165191:
                            if (str.equals("WapiAsCert")) {
                                c = '2';
                                break;
                            }
                            c = 65535;
                            break;
                        case -346924001:
                            if (str.equals(XML_TAG_ROAMING_CONSORTIUM_OIS)) {
                                c = '#';
                                break;
                            }
                            c = 65535;
                            break;
                        case -244338402:
                            if (str.equals(XML_TAG_NO_INTERNET_ACCESS_EXPECTED)) {
                                c = 22;
                                break;
                            }
                            c = 65535;
                            break;
                        case -181205965:
                            if (str.equals(XML_TAG_ALLOWED_PROTOCOLS)) {
                                c = '\t';
                                break;
                            }
                            c = 65535;
                            break;
                        case -135994866:
                            if (str.equals(XML_TAG_IS_LEGACY_PASSPOINT_CONFIG)) {
                                c = '\"';
                                break;
                            }
                            c = 65535;
                            break;
                        case -125790735:
                            if (str.equals(XML_TAG_SEM_UPDATE_TIME_KEY)) {
                                c = '\'';
                                break;
                            }
                            c = 65535;
                            break;
                        case -94981986:
                            if (str.equals(XML_TAG_MAC_RANDOMIZATION_SETTING)) {
                                c = WifiLog.PLACEHOLDER;
                                break;
                            }
                            c = 65535;
                            break;
                        case -51197516:
                            if (str.equals(XML_TAG_METERED_OVERRIDE)) {
                                c = 25;
                                break;
                            }
                            c = 65535;
                            break;
                        case 2165397:
                            if (str.equals(XML_TAG_FQDN)) {
                                c = 17;
                                break;
                            }
                            c = 65535;
                            break;
                        case 2554747:
                            break;
                        case 63507133:
                            if (str.equals(XML_TAG_BSSID)) {
                                c = 2;
                                break;
                            }
                            c = 65535;
                            break;
                        case 95088315:
                            if (str.equals(XML_TAG_SAMSUNG_SPECIFIC_FLAGS)) {
                                c = '.';
                                break;
                            }
                            c = 65535;
                            break;
                        case 395107740:
                            if (str.equals(XML_TAG_SEM_LOCATION_LATI)) {
                                c = '<';
                                break;
                            }
                            c = 65535;
                            break;
                        case 553095444:
                            if (str.equals(XML_TAG_WAPI_PSK_KEY_TYPE)) {
                                c = '0';
                                break;
                            }
                            c = 65535;
                            break;
                        case 581636034:
                            if (str.equals(XML_TAG_USER_APPROVED)) {
                                c = 23;
                                break;
                            }
                            c = 65535;
                            break;
                        case 617552438:
                            if (str.equals(XML_TAG_IS_RECOMMENDED)) {
                                c = '-';
                                break;
                            }
                            c = 65535;
                            break;
                        case 682791106:
                            if (str.equals(XML_TAG_ALLOWED_PAIRWISE_CIPHERS)) {
                                c = '\f';
                                break;
                            }
                            c = 65535;
                            break;
                        case 694485576:
                            if (str.equals(XML_TAG_AUTO_RECONNECT)) {
                                c = ',';
                                break;
                            }
                            c = 65535;
                            break;
                        case 709971552:
                            if (str.equals(XML_TAG_LAST_CONNECTED_TIME_KEY)) {
                                c = '(';
                                break;
                            }
                            c = 65535;
                            break;
                        case 736944625:
                            if (str.equals(XML_TAG_ALLOWED_GROUP_CIPHERS)) {
                                c = 11;
                                break;
                            }
                            c = 65535;
                            break;
                        case 785209343:
                            if (str.equals(XML_TAG_VENDOR_SSID)) {
                                c = '+';
                                break;
                            }
                            c = 65535;
                            break;
                        case 797043831:
                            if (str.equals(XML_TAG_PRE_SHARED_KEY)) {
                                c = 3;
                                break;
                            }
                            c = 65535;
                            break;
                        case 943896851:
                            if (str.equals(XML_TAG_USE_EXTERNAL_SCORES)) {
                                c = 26;
                                break;
                            }
                            c = 65535;
                            break;
                        case 1035394844:
                            if (str.equals(XML_TAG_LINKED_NETWORKS_LIST)) {
                                c = 19;
                                break;
                            }
                            c = 65535;
                            break;
                        case 1067884558:
                            if (str.equals(XML_TAG_ENTRY_RSSI_24GHZ)) {
                                c = ':';
                                break;
                            }
                            c = 65535;
                            break;
                        case 1190461055:
                            if (str.equals(XML_TAG_SEM_LOCATION_LONG)) {
                                c = '=';
                                break;
                            }
                            c = 65535;
                            break;
                        case 1199498141:
                            if (str.equals(XML_TAG_CONFIG_KEY)) {
                                c = 0;
                                break;
                            }
                            c = 65535;
                            break;
                        case 1266081442:
                            if (str.equals(XML_TAG_CAPTIVE_PORTAL)) {
                                c = '4';
                                break;
                            }
                            c = 65535;
                            break;
                        case 1327619296:
                            if (str.equals(XML_TAG_BSSID_WHITELIST_UPDATE_TIME_KEY)) {
                                c = '*';
                                break;
                            }
                            c = 65535;
                            break;
                        case 1350351025:
                            if (str.equals(XML_TAG_LAST_UPDATE_UID)) {
                                c = 31;
                                break;
                            }
                            c = 65535;
                            break;
                        case 1476993207:
                            if (str.equals(XML_TAG_CREATOR_NAME)) {
                                c = 29;
                                break;
                            }
                            c = 65535;
                            break;
                        case 1748177418:
                            if (str.equals(XML_TAG_BSSID_WHITELIST_KEY)) {
                                c = ')';
                                break;
                            }
                            c = 65535;
                            break;
                        case 1750336108:
                            if (str.equals(XML_TAG_CREATION_TIME)) {
                                c = 30;
                                break;
                            }
                            c = 65535;
                            break;
                        case 1851050768:
                            if (str.equals(XML_TAG_ALLOWED_AUTH_ALGOS)) {
                                c = '\n';
                                break;
                            }
                            c = 65535;
                            break;
                        case 1882132039:
                            if (str.equals(XML_TAG_SEM_CREATION_TIME_KEY)) {
                                c = '&';
                                break;
                            }
                            c = 65535;
                            break;
                        case 1903492587:
                            if (str.equals(XML_TAG_WAPI_CERT_INDEX)) {
                                c = '1';
                                break;
                            }
                            c = 65535;
                            break;
                        case 1905126713:
                            if (str.equals(XML_TAG_WEP_TX_KEY_INDEX)) {
                                c = 5;
                                break;
                            }
                            c = 65535;
                            break;
                        case 1955037270:
                            if (str.equals(XML_TAG_WEP_KEYS)) {
                                c = 4;
                                break;
                            }
                            c = 65535;
                            break;
                        case 1965854789:
                            if (str.equals(XML_TAG_HIDDEN_SSID)) {
                                c = 6;
                                break;
                            }
                            c = 65535;
                            break;
                        case 2018202939:
                            if (str.equals(XML_TAG_NUM_ASSOCIATION)) {
                                c = 27;
                                break;
                            }
                            c = 65535;
                            break;
                        case 2025240199:
                            if (str.equals(XML_TAG_PROVIDER_FRIENDLY_NAME)) {
                                c = 18;
                                break;
                            }
                            c = 65535;
                            break;
                        case 2026125174:
                            if (str.equals(XML_TAG_ALLOWED_SUITE_B_CIPHERS)) {
                                c = 14;
                                break;
                            }
                            c = 65535;
                            break;
                        case 2087394662:
                            if (str.equals(XML_TAG_LOGIN_URL)) {
                                c = '6';
                                break;
                            }
                            c = 65535;
                            break;
                        case 2143705732:
                            if (str.equals(XML_TAG_REQUIRE_PMF)) {
                                c = 7;
                                break;
                            }
                            c = 65535;
                            break;
                        default:
                            c = 65535;
                            break;
                    }
                    switch (c) {
                        case 0:
                            configKeyInData = (String) value;
                            break;
                        case 1:
                            configuration.SSID = (String) value;
                            break;
                        case 2:
                            configuration.BSSID = (String) value;
                            break;
                        case 3:
                            if (!withEncryption) {
                                configuration.preSharedKey = (String) value;
                                break;
                            } else {
                                configuration.preSharedKey = WifiPasswordManager.getInstance().decrypt((String) value);
                                break;
                            }
                        case 4:
                            populateWepKeysFromXmlValue(value, configuration.wepKeys, withEncryption);
                            break;
                        case 5:
                            configuration.wepTxKeyIndex = ((Integer) value).intValue();
                            break;
                        case 6:
                            configuration.hiddenSSID = ((Boolean) value).booleanValue();
                            break;
                        case 7:
                            configuration.requirePMF = ((Boolean) value).booleanValue();
                            break;
                        case '\b':
                            configuration.allowedKeyManagement = BitSet.valueOf((byte[]) value);
                            break;
                        case '\t':
                            configuration.allowedProtocols = BitSet.valueOf((byte[]) value);
                            break;
                        case '\n':
                            configuration.allowedAuthAlgorithms = BitSet.valueOf((byte[]) value);
                            break;
                        case 11:
                            configuration.allowedGroupCiphers = BitSet.valueOf((byte[]) value);
                            break;
                        case '\f':
                            configuration.allowedPairwiseCiphers = BitSet.valueOf((byte[]) value);
                            break;
                        case '\r':
                            configuration.allowedGroupManagementCiphers = BitSet.valueOf((byte[]) value);
                            break;
                        case 14:
                            configuration.allowedSuiteBCiphers = BitSet.valueOf((byte[]) value);
                            break;
                        case 15:
                            configuration.shared = ((Boolean) value).booleanValue();
                            break;
                        case 16:
                            int status = ((Integer) value).intValue();
                            if (status == 0) {
                                status = 2;
                            }
                            configuration.status = status;
                            break;
                        case 17:
                            configuration.FQDN = (String) value;
                            break;
                        case 18:
                            configuration.providerFriendlyName = (String) value;
                            break;
                        case 19:
                            configuration.linkedConfigurations = (HashMap) value;
                            break;
                        case 20:
                            configuration.defaultGwMacAddress = (String) value;
                            break;
                        case ISupplicantStaIfaceCallback.ReasonCode.UNSUPPORTED_RSN_IE_VERSION:
                            configuration.validatedInternetAccess = ((Boolean) value).booleanValue();
                            break;
                        case 22:
                            configuration.noInternetAccessExpected = ((Boolean) value).booleanValue();
                            break;
                        case 23:
                            configuration.userApproved = ((Integer) value).intValue();
                            break;
                        case 24:
                            configuration.meteredHint = ((Boolean) value).booleanValue();
                            break;
                        case 25:
                            configuration.meteredOverride = ((Integer) value).intValue();
                            break;
                        case 26:
                            configuration.useExternalScores = ((Boolean) value).booleanValue();
                            break;
                        case 27:
                            configuration.numAssociation = ((Integer) value).intValue();
                            break;
                        case 28:
                            configuration.creatorUid = ((Integer) value).intValue();
                            break;
                        case 29:
                            configuration.creatorName = (String) value;
                            break;
                        case 30:
                            configuration.creationTime = (String) value;
                            break;
                        case 31:
                            configuration.lastUpdateUid = ((Integer) value).intValue();
                            break;
                        case ' ':
                            configuration.lastUpdateName = (String) value;
                            break;
                        case '!':
                            configuration.lastConnectUid = ((Integer) value).intValue();
                            break;
                        case '\"':
                            configuration.isLegacyPasspointConfig = ((Boolean) value).booleanValue();
                            break;
                        case '#':
                            configuration.roamingConsortiumIds = (long[]) value;
                            break;
                        case '$':
                            configuration.setRandomizedMacAddress(MacAddress.fromString((String) value));
                            break;
                        case '%':
                            configuration.macRandomizationSetting = ((Integer) value).intValue();
                            macRandomizationSettingExists = true;
                            break;
                        case '&':
                            configuration.semCreationTime = Long.valueOf(Long.parseLong(String.valueOf(value))).longValue();
                            break;
                        case '\'':
                            configuration.semUpdateTime = Long.valueOf(Long.parseLong(String.valueOf(value))).longValue();
                            break;
                        case '(':
                            configuration.lastConnected = Long.valueOf(Long.parseLong(String.valueOf(value))).longValue();
                            break;
                        case ')':
                            bssidWhitelist = (String) value;
                            break;
                        case '*':
                            long bssidWhitelistUpdateTime = Long.valueOf(Long.parseLong(String.valueOf(value))).longValue();
                            if (!(bssidWhitelist == null || bssidWhitelistUpdateTime == 0 || configuration.bssidWhitelist == null)) {
                                configuration.bssidWhitelist.put(bssidWhitelist, Long.valueOf(bssidWhitelistUpdateTime));
                                break;
                            }
                        case '+':
                            configuration.semIsVendorSpecificSsid = ((Boolean) value).booleanValue();
                            break;
                        case ',':
                            configuration.semAutoReconnect = ((Integer) value).intValue();
                            break;
                        case '-':
                            configuration.semIsRecommended = ((Boolean) value).booleanValue();
                            break;
                        case '.':
                            configuration.semSamsungSpecificFlags = BitSet.valueOf((byte[]) value);
                            break;
                        case '/':
                            configuration.semAutoWifiScore = ((Integer) value).intValue();
                            break;
                        case '0':
                            configuration.wapiPskType = ((Integer) value).intValue();
                            break;
                        case '1':
                            configuration.wapiCertIndex = ((Integer) value).intValue();
                            break;
                        case '2':
                            configuration.wapiAsCert = (String) value;
                            break;
                        case '3':
                            configuration.wapiUserCert = (String) value;
                            break;
                        case '4':
                            configuration.isCaptivePortal = ((Boolean) value).booleanValue();
                            break;
                        case '5':
                            configuration.isAuthenticated = ((Boolean) value).booleanValue();
                            break;
                        case '6':
                        case '7':
                        case '8':
                        case '9':
                        case '<':
                        case '=':
                            break;
                        case ':':
                            configuration.entryRssi24GHz = ((Integer) value).intValue();
                            break;
                        case ';':
                            configuration.entryRssi5GHz = ((Integer) value).intValue();
                            break;
                        default:
                            throw new XmlPullParserException("Unknown value name found: " + valueName[0]);
                    }
                } else {
                    throw new XmlPullParserException("Missing value name");
                }
            }
            if (!macRandomizationSettingExists) {
                configuration.macRandomizationSetting = 0;
            }
            return Pair.create(configKeyInData, configuration);
        }
    }

    public static class IpConfigurationXmlUtil {
        public static final String XML_TAG_DNS_SERVER_ADDRESSES = "DNSServers";
        public static final String XML_TAG_GATEWAY_ADDRESS = "GatewayAddress";
        public static final String XML_TAG_IP_ASSIGNMENT = "IpAssignment";
        public static final String XML_TAG_LINK_ADDRESS = "LinkAddress";
        public static final String XML_TAG_LINK_PREFIX_LENGTH = "LinkPrefixLength";
        public static final String XML_TAG_PROXY_EXCLUSION_LIST = "ProxyExclusionList";
        public static final String XML_TAG_PROXY_HOST = "ProxyHost";
        public static final String XML_TAG_PROXY_PAC_FILE = "ProxyPac";
        public static final String XML_TAG_PROXY_PASSWORD = "ProxyPassword";
        public static final String XML_TAG_PROXY_PORT = "ProxyPort";
        public static final String XML_TAG_PROXY_SETTINGS = "ProxySettings";
        public static final String XML_TAG_PROXY_USERNAME = "ProxyUsername";

        private static void writeStaticIpConfigurationToXml(XmlSerializer out, StaticIpConfiguration staticIpConfiguration) throws XmlPullParserException, IOException {
            if (staticIpConfiguration.ipAddress != null) {
                XmlUtil.writeNextValue(out, XML_TAG_LINK_ADDRESS, staticIpConfiguration.ipAddress.getAddress().getHostAddress());
                XmlUtil.writeNextValue(out, XML_TAG_LINK_PREFIX_LENGTH, Integer.valueOf(staticIpConfiguration.ipAddress.getPrefixLength()));
            } else {
                XmlUtil.writeNextValue(out, XML_TAG_LINK_ADDRESS, null);
                XmlUtil.writeNextValue(out, XML_TAG_LINK_PREFIX_LENGTH, null);
            }
            if (staticIpConfiguration.gateway != null) {
                XmlUtil.writeNextValue(out, XML_TAG_GATEWAY_ADDRESS, staticIpConfiguration.gateway.getHostAddress());
            } else {
                XmlUtil.writeNextValue(out, XML_TAG_GATEWAY_ADDRESS, null);
            }
            if (staticIpConfiguration.dnsServers != null) {
                String[] dnsServers = new String[staticIpConfiguration.dnsServers.size()];
                int dnsServerIdx = 0;
                Iterator it = staticIpConfiguration.dnsServers.iterator();
                while (it.hasNext()) {
                    dnsServers[dnsServerIdx] = ((InetAddress) it.next()).getHostAddress();
                    dnsServerIdx++;
                }
                XmlUtil.writeNextValue(out, XML_TAG_DNS_SERVER_ADDRESSES, dnsServers);
                return;
            }
            XmlUtil.writeNextValue(out, XML_TAG_DNS_SERVER_ADDRESSES, null);
        }

        public static void writeToXmlForBackup(XmlSerializer out, IpConfiguration ipConfiguration) throws XmlPullParserException, IOException {
            Uri pacUrl;
            XmlUtil.writeNextValue(out, XML_TAG_IP_ASSIGNMENT, ipConfiguration.ipAssignment.toString());
            if (C05801.$SwitchMap$android$net$IpConfiguration$IpAssignment[ipConfiguration.ipAssignment.ordinal()] == 1) {
                writeStaticIpConfigurationToXml(out, ipConfiguration.getStaticIpConfiguration());
            }
            if (ipConfiguration.proxySettings == null || ipConfiguration.httpProxy == null) {
                XmlUtil.writeNextValue(out, XML_TAG_PROXY_SETTINGS, IpConfiguration.ProxySettings.NONE.toString());
                return;
            }
            XmlUtil.writeNextValue(out, XML_TAG_PROXY_SETTINGS, ipConfiguration.proxySettings.toString());
            int i = C05801.$SwitchMap$android$net$IpConfiguration$ProxySettings[ipConfiguration.proxySettings.ordinal()];
            if (i == 1) {
                XmlUtil.writeNextValue(out, XML_TAG_PROXY_HOST, ipConfiguration.httpProxy.getHost());
                XmlUtil.writeNextValue(out, XML_TAG_PROXY_PORT, Integer.valueOf(ipConfiguration.httpProxy.getPort()));
                XmlUtil.writeNextValue(out, XML_TAG_PROXY_EXCLUSION_LIST, ipConfiguration.httpProxy.getExclusionListAsString());
            } else if (i == 2 && (pacUrl = ipConfiguration.httpProxy.getPacFileUrl()) != null) {
                XmlUtil.writeNextValue(out, XML_TAG_PROXY_PAC_FILE, pacUrl.toString());
            }
        }

        public static void writeToXml(XmlSerializer out, IpConfiguration ipConfiguration) throws XmlPullParserException, IOException {
            writeToXmlForBackup(out, ipConfiguration);
            if (C05801.$SwitchMap$android$net$IpConfiguration$ProxySettings[ipConfiguration.proxySettings.ordinal()] == 1) {
                XmlUtil.writeNextValue(out, XML_TAG_PROXY_USERNAME, ipConfiguration.httpProxy.getUsername());
                XmlUtil.writeNextValue(out, XML_TAG_PROXY_PASSWORD, ipConfiguration.httpProxy.getPassword());
            }
        }

        private static StaticIpConfiguration parseStaticIpConfigurationFromXml(XmlPullParser in) throws XmlPullParserException, IOException {
            StaticIpConfiguration staticIpConfiguration = new StaticIpConfiguration();
            String linkAddressString = (String) XmlUtil.readNextValueWithName(in, XML_TAG_LINK_ADDRESS);
            Integer linkPrefixLength = (Integer) XmlUtil.readNextValueWithName(in, XML_TAG_LINK_PREFIX_LENGTH);
            if (!(linkAddressString == null || linkPrefixLength == null)) {
                LinkAddress linkAddress = new LinkAddress(NetworkUtils.numericToInetAddress(linkAddressString), linkPrefixLength.intValue());
                if (linkAddress.getAddress() instanceof Inet4Address) {
                    staticIpConfiguration.ipAddress = linkAddress;
                } else {
                    Log.w(XmlUtil.TAG, "Non-IPv4 address: " + linkAddress);
                }
            }
            String gatewayAddressString = (String) XmlUtil.readNextValueWithName(in, XML_TAG_GATEWAY_ADDRESS);
            if (gatewayAddressString != null) {
                InetAddress gateway = NetworkUtils.numericToInetAddress(gatewayAddressString);
                RouteInfo route = new RouteInfo(null, gateway);
                if (route.isIPv4Default()) {
                    staticIpConfiguration.gateway = gateway;
                } else {
                    Log.w(XmlUtil.TAG, "Non-IPv4 default route: " + route);
                }
            }
            String[] dnsServerAddressesString = (String[]) XmlUtil.readNextValueWithName(in, XML_TAG_DNS_SERVER_ADDRESSES);
            if (dnsServerAddressesString != null) {
                for (String dnsServerAddressString : dnsServerAddressesString) {
                    staticIpConfiguration.dnsServers.add(NetworkUtils.numericToInetAddress(dnsServerAddressString));
                }
            }
            return staticIpConfiguration;
        }

        public static IpConfiguration parseFromXml(XmlPullParser in, int outerTagDepth) throws XmlPullParserException, IOException {
            IpConfiguration ipConfiguration = new IpConfiguration();
            IpConfiguration.IpAssignment ipAssignment = IpConfiguration.IpAssignment.valueOf((String) XmlUtil.readNextValueWithName(in, XML_TAG_IP_ASSIGNMENT));
            ipConfiguration.setIpAssignment(ipAssignment);
            int i = C05801.$SwitchMap$android$net$IpConfiguration$IpAssignment[ipAssignment.ordinal()];
            if (i == 1) {
                ipConfiguration.setStaticIpConfiguration(parseStaticIpConfigurationFromXml(in));
            } else if (!(i == 2 || i == 3)) {
                throw new XmlPullParserException("Unknown ip assignment type: " + ipAssignment);
            }
            IpConfiguration.ProxySettings proxySettings = IpConfiguration.ProxySettings.valueOf((String) XmlUtil.readNextValueWithName(in, XML_TAG_PROXY_SETTINGS));
            ipConfiguration.setProxySettings(proxySettings);
            int i2 = C05801.$SwitchMap$android$net$IpConfiguration$ProxySettings[proxySettings.ordinal()];
            if (i2 == 1) {
                ipConfiguration.setHttpProxy(new ProxyInfo((String) XmlUtil.readNextValueWithName(in, XML_TAG_PROXY_HOST), ((Integer) XmlUtil.readNextValueWithName(in, XML_TAG_PROXY_PORT)).intValue(), (String) XmlUtil.readNextValueWithName(in, XML_TAG_PROXY_USERNAME), (String) XmlUtil.readNextValueWithName(in, XML_TAG_PROXY_PASSWORD), (String) XmlUtil.readNextValueWithName(in, XML_TAG_PROXY_EXCLUSION_LIST)));
            } else if (i2 == 2) {
                ipConfiguration.setHttpProxy(new ProxyInfo((String) XmlUtil.readNextValueWithName(in, XML_TAG_PROXY_PAC_FILE)));
            } else if (!(i2 == 3 || i2 == 4)) {
                throw new XmlPullParserException("Unknown proxy settings type: " + proxySettings);
            }
            return ipConfiguration;
        }
    }

    /* access modifiers changed from: package-private */
    /* renamed from: com.android.server.wifi.util.XmlUtil$1 */
    public static /* synthetic */ class C05801 {
        static final /* synthetic */ int[] $SwitchMap$android$net$IpConfiguration$IpAssignment = new int[IpConfiguration.IpAssignment.values().length];
        static final /* synthetic */ int[] $SwitchMap$android$net$IpConfiguration$ProxySettings = new int[IpConfiguration.ProxySettings.values().length];

        static {
            try {
                $SwitchMap$android$net$IpConfiguration$ProxySettings[IpConfiguration.ProxySettings.STATIC.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$android$net$IpConfiguration$ProxySettings[IpConfiguration.ProxySettings.PAC.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$android$net$IpConfiguration$ProxySettings[IpConfiguration.ProxySettings.NONE.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            try {
                $SwitchMap$android$net$IpConfiguration$ProxySettings[IpConfiguration.ProxySettings.UNASSIGNED.ordinal()] = 4;
            } catch (NoSuchFieldError e4) {
            }
            try {
                $SwitchMap$android$net$IpConfiguration$IpAssignment[IpConfiguration.IpAssignment.STATIC.ordinal()] = 1;
            } catch (NoSuchFieldError e5) {
            }
            try {
                $SwitchMap$android$net$IpConfiguration$IpAssignment[IpConfiguration.IpAssignment.DHCP.ordinal()] = 2;
            } catch (NoSuchFieldError e6) {
            }
            try {
                $SwitchMap$android$net$IpConfiguration$IpAssignment[IpConfiguration.IpAssignment.UNASSIGNED.ordinal()] = 3;
            } catch (NoSuchFieldError e7) {
            }
        }
    }

    public static class NetworkSelectionStatusXmlUtil {
        public static final String XML_TAG_CONNECT_CHOICE = "ConnectChoice";
        public static final String XML_TAG_CONNECT_CHOICE_TIMESTAMP = "ConnectChoiceTimeStamp";
        public static final String XML_TAG_DISABLE_REASON = "DisableReason";
        public static final String XML_TAG_HAS_EVER_CONNECTED = "HasEverConnected";
        public static final String XML_TAG_SELECTION_STATUS = "SelectionStatus";

        public static void writeToXml(XmlSerializer out, WifiConfiguration.NetworkSelectionStatus selectionStatus) throws XmlPullParserException, IOException {
            XmlUtil.writeNextValue(out, XML_TAG_SELECTION_STATUS, selectionStatus.getNetworkStatusString());
            XmlUtil.writeNextValue(out, XML_TAG_DISABLE_REASON, selectionStatus.getNetworkDisableReasonString());
            XmlUtil.writeNextValue(out, XML_TAG_CONNECT_CHOICE, selectionStatus.getConnectChoice());
            XmlUtil.writeNextValue(out, XML_TAG_CONNECT_CHOICE_TIMESTAMP, Long.valueOf(selectionStatus.getConnectChoiceTimestamp()));
            XmlUtil.writeNextValue(out, XML_TAG_HAS_EVER_CONNECTED, Boolean.valueOf(selectionStatus.getHasEverConnected()));
        }

        public static WifiConfiguration.NetworkSelectionStatus parseFromXml(XmlPullParser in, int outerTagDepth) throws XmlPullParserException, IOException {
            WifiConfiguration.NetworkSelectionStatus selectionStatus = new WifiConfiguration.NetworkSelectionStatus();
            String statusString = "";
            String disableReasonString = "";
            while (true) {
                char c = 65535;
                if (!XmlUtil.isNextSectionEnd(in, outerTagDepth)) {
                    String[] valueName = new String[1];
                    Object value = XmlUtil.readCurrentValue(in, valueName);
                    if (valueName[0] != null) {
                        String str = valueName[0];
                        switch (str.hashCode()) {
                            case -1529270479:
                                if (str.equals(XML_TAG_HAS_EVER_CONNECTED)) {
                                    c = 4;
                                    break;
                                }
                                break;
                            case -822052309:
                                if (str.equals(XML_TAG_CONNECT_CHOICE_TIMESTAMP)) {
                                    c = 3;
                                    break;
                                }
                                break;
                            case -808576245:
                                if (str.equals(XML_TAG_CONNECT_CHOICE)) {
                                    c = 2;
                                    break;
                                }
                                break;
                            case -85195988:
                                if (str.equals(XML_TAG_DISABLE_REASON)) {
                                    c = 1;
                                    break;
                                }
                                break;
                            case 1452117118:
                                if (str.equals(XML_TAG_SELECTION_STATUS)) {
                                    c = 0;
                                    break;
                                }
                                break;
                        }
                        if (c == 0) {
                            statusString = (String) value;
                        } else if (c == 1) {
                            disableReasonString = (String) value;
                        } else if (c == 2) {
                            selectionStatus.setConnectChoice((String) value);
                        } else if (c == 3) {
                            selectionStatus.setConnectChoiceTimestamp(((Long) value).longValue());
                        } else if (c == 4) {
                            selectionStatus.setHasEverConnected(((Boolean) value).booleanValue());
                        } else {
                            throw new XmlPullParserException("Unknown value name found: " + valueName[0]);
                        }
                    } else {
                        throw new XmlPullParserException("Missing value name");
                    }
                } else {
                    int status = Arrays.asList(WifiConfiguration.NetworkSelectionStatus.QUALITY_NETWORK_SELECTION_STATUS).indexOf(statusString);
                    int disableReason = Arrays.asList(WifiConfiguration.NetworkSelectionStatus.QUALITY_NETWORK_SELECTION_DISABLE_REASON).indexOf(disableReasonString);
                    if (status == -1 || disableReason == -1 || status == 1) {
                        status = 0;
                        disableReason = 0;
                    }
                    selectionStatus.setNetworkSelectionStatus(status);
                    selectionStatus.setNetworkSelectionDisableReason(disableReason);
                    return selectionStatus;
                }
            }
        }
    }

    public static class WifiEnterpriseConfigXmlUtil {
        public static final String XML_TAG_ALT_SUBJECT_MATCH = "AltSubjectMatch";
        public static final String XML_TAG_ANON_IDENTITY = "AnonIdentity";
        public static final String XML_TAG_CA_CERT = "CaCert";
        public static final String XML_TAG_CA_PATH = "CaPath";
        public static final String XML_TAG_CLIENT_CERT = "ClientCert";
        public static final String XML_TAG_DOM_SUFFIX_MATCH = "DomSuffixMatch";
        public static final String XML_TAG_EAP_METHOD = "EapMethod";
        public static final String XML_TAG_ENGINE = "Engine";
        public static final String XML_TAG_ENGINE_ID = "EngineId";
        public static final String XML_TAG_IDENTITY = "Identity";
        public static final String XML_TAG_PAC_FILE = "PacFile";
        public static final String XML_TAG_PASSWORD = "Password";
        public static final String XML_TAG_PHASE1_METHOD = "Phase1Method";
        public static final String XML_TAG_PHASE2_METHOD = "Phase2Method";
        public static final String XML_TAG_PLMN = "PLMN";
        public static final String XML_TAG_PRIVATE_KEY_ID = "PrivateKeyId";
        public static final String XML_TAG_REALM = "Realm";
        public static final String XML_TAG_SIM_NUMBER = "SimNumber";
        public static final String XML_TAG_SUBJECT_MATCH = "SubjectMatch";
        public static final String XML_TAG_WAPI_AS_CERT = "WapiAsCert";
        public static final String XML_TAG_WAPI_USER_CERT = "WapiUserCert";

        public static void writeToXml(XmlSerializer out, WifiEnterpriseConfig enterpriseConfig) throws XmlPullParserException, IOException {
            writeToXml(out, enterpriseConfig, WifiPasswordManager.getInstance().isSupported());
        }

        public static void writeToXml(XmlSerializer out, WifiEnterpriseConfig enterpriseConfig, boolean withEncryption) throws XmlPullParserException, IOException {
            XmlUtil.writeNextValue(out, XML_TAG_IDENTITY, enterpriseConfig.getFieldValue("identity"));
            XmlUtil.writeNextValue(out, XML_TAG_ANON_IDENTITY, enterpriseConfig.getFieldValue("anonymous_identity"));
            if (withEncryption) {
                XmlUtil.writeNextValue(out, XML_TAG_PASSWORD, WifiPasswordManager.getInstance().encrypt(enterpriseConfig.getFieldValue(McfDataUtil.McfData.JSON_PASSWORD)));
            } else {
                XmlUtil.writeNextValue(out, XML_TAG_PASSWORD, enterpriseConfig.getFieldValue(McfDataUtil.McfData.JSON_PASSWORD));
            }
            XmlUtil.writeNextValue(out, XML_TAG_CLIENT_CERT, enterpriseConfig.getFieldValue(WifiBackupRestore.SupplicantBackupMigration.SUPPLICANT_KEY_CLIENT_CERT));
            XmlUtil.writeNextValue(out, XML_TAG_CA_CERT, enterpriseConfig.getFieldValue(WifiBackupRestore.SupplicantBackupMigration.SUPPLICANT_KEY_CA_CERT));
            XmlUtil.writeNextValue(out, XML_TAG_SUBJECT_MATCH, enterpriseConfig.getFieldValue("subject_match"));
            XmlUtil.writeNextValue(out, XML_TAG_ENGINE, enterpriseConfig.getFieldValue("engine"));
            XmlUtil.writeNextValue(out, XML_TAG_ENGINE_ID, enterpriseConfig.getFieldValue("engine_id"));
            XmlUtil.writeNextValue(out, XML_TAG_PRIVATE_KEY_ID, enterpriseConfig.getFieldValue("key_id"));
            XmlUtil.writeNextValue(out, XML_TAG_ALT_SUBJECT_MATCH, enterpriseConfig.getFieldValue("altsubject_match"));
            XmlUtil.writeNextValue(out, XML_TAG_DOM_SUFFIX_MATCH, enterpriseConfig.getFieldValue("domain_suffix_match"));
            XmlUtil.writeNextValue(out, XML_TAG_CA_PATH, enterpriseConfig.getFieldValue(WifiBackupRestore.SupplicantBackupMigration.SUPPLICANT_KEY_CA_PATH));
            XmlUtil.writeNextValue(out, XML_TAG_EAP_METHOD, Integer.valueOf(enterpriseConfig.getEapMethod()));
            XmlUtil.writeNextValue(out, XML_TAG_PHASE2_METHOD, Integer.valueOf(enterpriseConfig.getPhase2Method()));
            XmlUtil.writeNextValue(out, XML_TAG_PLMN, enterpriseConfig.getPlmn());
            XmlUtil.writeNextValue(out, XML_TAG_REALM, enterpriseConfig.getRealm());
            XmlUtil.writeNextValue(out, XML_TAG_SIM_NUMBER, enterpriseConfig.getFieldValue("sim_num"));
            XmlUtil.writeNextValue(out, XML_TAG_PHASE1_METHOD, enterpriseConfig.getPhase1Method());
        }

        public static WifiEnterpriseConfig parseFromXml(XmlPullParser in, int outerTagDepth) throws XmlPullParserException, IOException {
            return parseFromXml(in, outerTagDepth, WifiPasswordManager.getInstance().isSupported());
        }

        /* JADX INFO: Can't fix incorrect switch cases order, some code will duplicate */
        /* JADX WARNING: Code restructure failed: missing block: B:68:0x010a, code lost:
            if (r5.equals(com.android.server.wifi.util.XmlUtil.WifiEnterpriseConfigXmlUtil.XML_TAG_ANON_IDENTITY) != false) goto L_0x010e;
         */
        public static WifiEnterpriseConfig parseFromXml(XmlPullParser in, int outerTagDepth, boolean withEncryption) throws XmlPullParserException, IOException {
            WifiEnterpriseConfig enterpriseConfig = new WifiEnterpriseConfig();
            while (!XmlUtil.isNextSectionEnd(in, outerTagDepth)) {
                char c = 1;
                String[] valueName = new String[1];
                Object value = XmlUtil.readCurrentValue(in, valueName);
                if (valueName[0] != null) {
                    String str = valueName[0];
                    switch (str.hashCode()) {
                        case -1956487222:
                            break;
                        case -1766961550:
                            if (str.equals(XML_TAG_DOM_SUFFIX_MATCH)) {
                                c = '\n';
                                break;
                            }
                            c = 65535;
                            break;
                        case -1406508014:
                            if (str.equals("WapiUserCert")) {
                                c = 20;
                                break;
                            }
                            c = 65535;
                            break;
                        case -1362213863:
                            if (str.equals(XML_TAG_SUBJECT_MATCH)) {
                                c = 5;
                                break;
                            }
                            c = 65535;
                            break;
                        case -1273966921:
                            if (str.equals(XML_TAG_PHASE1_METHOD)) {
                                c = 17;
                                break;
                            }
                            c = 65535;
                            break;
                        case -1199574865:
                            if (str.equals(XML_TAG_CLIENT_CERT)) {
                                c = 3;
                                break;
                            }
                            c = 65535;
                            break;
                        case -1085249824:
                            if (str.equals(XML_TAG_SIM_NUMBER)) {
                                c = 16;
                                break;
                            }
                            c = 65535;
                            break;
                        case -852165191:
                            if (str.equals("WapiAsCert")) {
                                c = 19;
                                break;
                            }
                            c = 65535;
                            break;
                        case -596361182:
                            if (str.equals(XML_TAG_ALT_SUBJECT_MATCH)) {
                                c = '\t';
                                break;
                            }
                            c = 65535;
                            break;
                        case -386463240:
                            if (str.equals(XML_TAG_PHASE2_METHOD)) {
                                c = '\r';
                                break;
                            }
                            c = 65535;
                            break;
                        case -71117602:
                            if (str.equals(XML_TAG_IDENTITY)) {
                                c = 0;
                                break;
                            }
                            c = 65535;
                            break;
                        case 2458781:
                            if (str.equals(XML_TAG_PLMN)) {
                                c = 14;
                                break;
                            }
                            c = 65535;
                            break;
                        case 11048245:
                            if (str.equals(XML_TAG_EAP_METHOD)) {
                                c = '\f';
                                break;
                            }
                            c = 65535;
                            break;
                        case 78834287:
                            if (str.equals(XML_TAG_REALM)) {
                                c = 15;
                                break;
                            }
                            c = 65535;
                            break;
                        case 856496398:
                            if (str.equals(XML_TAG_PAC_FILE)) {
                                c = 18;
                                break;
                            }
                            c = 65535;
                            break;
                        case 1146405943:
                            if (str.equals(XML_TAG_PRIVATE_KEY_ID)) {
                                c = '\b';
                                break;
                            }
                            c = 65535;
                            break;
                        case 1281629883:
                            if (str.equals(XML_TAG_PASSWORD)) {
                                c = 2;
                                break;
                            }
                            c = 65535;
                            break;
                        case 1885134621:
                            if (str.equals(XML_TAG_ENGINE_ID)) {
                                c = 7;
                                break;
                            }
                            c = 65535;
                            break;
                        case 2009831362:
                            if (str.equals(XML_TAG_CA_CERT)) {
                                c = 4;
                                break;
                            }
                            c = 65535;
                            break;
                        case 2010214851:
                            if (str.equals(XML_TAG_CA_PATH)) {
                                c = 11;
                                break;
                            }
                            c = 65535;
                            break;
                        case 2080171618:
                            if (str.equals(XML_TAG_ENGINE)) {
                                c = 6;
                                break;
                            }
                            c = 65535;
                            break;
                        default:
                            c = 65535;
                            break;
                    }
                    switch (c) {
                        case 0:
                            enterpriseConfig.setFieldValue("identity", (String) value);
                            break;
                        case 1:
                            enterpriseConfig.setFieldValue("anonymous_identity", (String) value);
                            break;
                        case 2:
                            if (!withEncryption) {
                                enterpriseConfig.setFieldValue(McfDataUtil.McfData.JSON_PASSWORD, (String) value);
                                break;
                            } else {
                                enterpriseConfig.setFieldValue(McfDataUtil.McfData.JSON_PASSWORD, WifiPasswordManager.getInstance().decrypt((String) value));
                                break;
                            }
                        case 3:
                            enterpriseConfig.setFieldValue(WifiBackupRestore.SupplicantBackupMigration.SUPPLICANT_KEY_CLIENT_CERT, (String) value);
                            break;
                        case 4:
                            enterpriseConfig.setFieldValue(WifiBackupRestore.SupplicantBackupMigration.SUPPLICANT_KEY_CA_CERT, (String) value);
                            break;
                        case 5:
                            enterpriseConfig.setFieldValue("subject_match", (String) value);
                            break;
                        case 6:
                            enterpriseConfig.setFieldValue("engine", (String) value);
                            break;
                        case 7:
                            enterpriseConfig.setFieldValue("engine_id", (String) value);
                            break;
                        case '\b':
                            enterpriseConfig.setFieldValue("key_id", (String) value);
                            break;
                        case '\t':
                            enterpriseConfig.setFieldValue("altsubject_match", (String) value);
                            break;
                        case '\n':
                            enterpriseConfig.setFieldValue("domain_suffix_match", (String) value);
                            break;
                        case 11:
                            enterpriseConfig.setFieldValue(WifiBackupRestore.SupplicantBackupMigration.SUPPLICANT_KEY_CA_PATH, (String) value);
                            break;
                        case '\f':
                            enterpriseConfig.setEapMethod(((Integer) value).intValue());
                            break;
                        case '\r':
                            enterpriseConfig.setPhase2Method(((Integer) value).intValue());
                            break;
                        case 14:
                            enterpriseConfig.setPlmn((String) value);
                            break;
                        case 15:
                            enterpriseConfig.setRealm((String) value);
                            break;
                        case 16:
                            enterpriseConfig.setFieldValue("sim_num", (String) value);
                            break;
                        case 17:
                            String phase1 = ((String) value).replaceAll("fast_provisioning=", "");
                            if (!phase1.equals("NULL") && !phase1.equals("")) {
                                enterpriseConfig.setPhase1Method(Integer.parseInt(phase1));
                                break;
                            } else {
                                enterpriseConfig.setPhase1Method(-1);
                                break;
                            }
                        case 18:
                            break;
                        case 19:
                        case 20:
                            Log.i(XmlUtil.TAG, "dummy field " + valueName[0] + " to avoid XmlPullParserException");
                            break;
                        default:
                            throw new XmlPullParserException("Unknown value name found: " + valueName[0]);
                    }
                } else {
                    throw new XmlPullParserException("Missing value name");
                }
            }
            return enterpriseConfig;
        }
    }
}
