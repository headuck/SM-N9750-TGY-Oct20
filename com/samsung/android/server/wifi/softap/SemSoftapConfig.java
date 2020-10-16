package com.samsung.android.server.wifi.softap;

import android.content.Context;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Debug;
import android.os.SystemProperties;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;
import com.android.server.wifi.WifiNative;
import com.android.server.wifi.util.ApConfigUtil;
import com.samsung.android.feature.SemCscFeature;
import com.samsung.android.feature.SemFloatingFeature;
import com.sec.android.app.CscFeatureTagWifi;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SemSoftapConfig {
    private static final boolean MHSDBG = ("eng".equals(Build.TYPE) || Debug.semIsProductDev());
    private static final String TAG = "SemSoftapConfig";
    private final int SamsungHotspotVSIE = 128;
    private final String SamsungOUI = "001632";
    private boolean bCheck5G = false;
    private int channel2G = 0;
    private Context mContext;
    private List<String> mMHSDumpLogs = new ArrayList();
    private boolean mPowerSaveChecked = false;

    public SemSoftapConfig(Context context) {
        this.mContext = context;
    }

    public void addMHSDumpLog(String log) {
        StringBuffer value = new StringBuffer();
        Log.i(TAG, log + " mhs: " + this.mMHSDumpLogs.size());
        value.append(new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(Long.valueOf(System.currentTimeMillis())) + " " + log + "\n");
        if (this.mMHSDumpLogs.size() > 100) {
            this.mMHSDumpLogs.remove(0);
        }
        this.mMHSDumpLogs.add(value.toString());
    }

    public String getDumpLogs() {
        StringBuffer retValue = new StringBuffer();
        retValue.append("--SemSoftapConfig \n");
        retValue.append(showCSCvalues());
        retValue.append(showSecProductFeature());
        retValue.append(showMoreInfo());
        retValue.append("---MHS history: \n");
        retValue.append(this.mMHSDumpLogs.toString());
        return retValue.toString();
    }

    public void get(String mApInterfaceName, WifiConfiguration localConfig, WifiNative mWifiNative, String mCountryCode) {
        WifiManager wm = (WifiManager) this.mContext.getSystemService("wifi");
        boolean bSupport5G = false;
        bSupport5G = false;
        bSupport5G = false;
        bSupport5G = false;
        bSupport5G = false;
        String CONFIGOPBRANDINGFORMOBILEAP = SemCscFeature.getInstance().getString(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_CONFIGOPBRANDINGFORMOBILEAP, "ALL");
        boolean z = true;
        this.mPowerSaveChecked = Settings.Secure.getInt(this.mContext.getContentResolver(), "wifi_ap_powersave_mode_checked", 0) == 1;
        localConfig.requirePMF = Settings.Secure.getInt(this.mContext.getContentResolver(), "wifi_ap_pmf_checked", 0) == 1;
        if (wm.semSupportWifiAp5GBasedOnCountry()) {
            this.bCheck5G = Settings.Secure.getInt(this.mContext.getContentResolver(), "wifi_ap_5G_checked", 0) == 1;
            this.channel2G = Settings.Secure.getInt(this.mContext.getContentResolver(), "wifi_ap_last_2g_channel", 0);
            Log.i(TAG, "startSoftAp() checked5G:" + this.bCheck5G + " last2Gch:" + this.channel2G + " requirePMF:" + localConfig.requirePMF);
            if (this.bCheck5G) {
                bSupport5G = ApConfigUtil.isWifiApSupport5G(mWifiNative, mCountryCode);
                if (!bSupport5G) {
                    localConfig.apBand = 0;
                    localConfig.apChannel = this.channel2G;
                } else {
                    localConfig.apBand = 1;
                    localConfig.apChannel = 149;
                }
                Log.i(TAG, " support 5G:" + bSupport5G + " band:" + localConfig.apBand + " ch:" + localConfig.apChannel);
            } else if (localConfig.apChannel < 0 || localConfig.apChannel > 11 || localConfig.apBand == 1) {
                Log.i(TAG, " change wrong ch:" + localConfig.apChannel);
                Log.i(TAG, " change wrong band:" + localConfig.apBand);
                localConfig.apBand = 0;
                int i = this.channel2G;
                if (i < 0 || i > 11) {
                    localConfig.apChannel = 0;
                } else {
                    localConfig.apChannel = i;
                }
            }
            Log.i(TAG, " support 5G:" + bSupport5G + " band:" + localConfig.apBand + " ch:" + localConfig.apChannel);
        } else if (MHSDBG) {
            ApConfigUtil.isWifiApSupport5G(mWifiNative, mCountryCode);
        }
        if (!wm.semSupportWifiAp5G() && !wm.semSupportWifiAp5GBasedOnCountry() && localConfig.apChannel > 14) {
            localConfig.apChannel = 0;
            localConfig.apBand = 0;
        }
        if (localConfig.apChannel < 0) {
            localConfig.apChannel = 0;
            localConfig.apBand = 0;
        }
        if (localConfig.apChannel <= 13) {
            localConfig.apBand = 0;
        }
        addMHSDumpLog("SemSoftapConfig country:" + mCountryCode + " checked5G :" + this.bCheck5G + " support5G:" + bSupport5G + " powersave:" + this.mPowerSaveChecked + " config_band:" + localConfig.apBand + " config_ch:" + localConfig.apChannel + " last2Gch:" + this.channel2G + " requirePMF:" + localConfig.requirePMF);
        if (!"VZW".equals(CONFIGOPBRANDINGFORMOBILEAP) || !(localConfig.vendorIE == 0 || localConfig.vendorIE == 128)) {
            if (!"SPRINT".equals(CONFIGOPBRANDINGFORMOBILEAP) || !(localConfig.vendorIE == 0 || localConfig.vendorIE == 128)) {
                setLocalConfigMaxClient(SemCscFeature.getInstance().getString(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_MAXCLIENT4MOBILEAPNETEXTENSION), localConfig);
            } else {
                TelephonyManager tm = (TelephonyManager) this.mContext.getSystemService("phone");
                if (1 == tm.getNetworkType() || 2 == tm.getNetworkType() || 16 == tm.getNetworkType() || tm.getNetworkType() == 0) {
                    localConfig.maxclient = 1;
                }
            }
        } else if (((TelephonyManager) this.mContext.getSystemService("phone")).getNetworkType() != 13) {
            localConfig.maxclient = 5;
        }
        Log.d(TAG, "maxClient = " + localConfig.maxclient);
        Log.d(TAG, "localConfig.vendorIE = " + localConfig.vendorIE);
        if (MHSDBG) {
            String str = SystemProperties.get("mhs.channel");
            String str_maxclient = SystemProperties.get("mhs.maxclient");
            if (str != null && !str.equals("")) {
                localConfig.apChannel = Integer.parseInt(str);
                if (localConfig.apChannel > 13) {
                    localConfig.apBand = 1;
                } else {
                    localConfig.apBand = 0;
                }
                Log.w(TAG, "channel is changed " + localConfig.apChannel + " apBand(0:2g , 1:5g , -1:any) :" + localConfig.apBand);
            }
            if (str_maxclient != null && !str_maxclient.equals("")) {
                localConfig.maxclient = Integer.parseInt(str_maxclient);
                Log.w(TAG, "maxclient is changed " + localConfig.maxclient);
            }
        }
        localConfig.wpsStatus = 0;
        StringBuilder sb = new StringBuilder();
        sb.append("SemSoftapConfig softap ");
        sb.append(mApInterfaceName);
        sb.append(" ");
        sb.append(localConfig.SSID);
        sb.append(" encryptionType:");
        sb.append(getEncryptionType(localConfig));
        sb.append(" ch:");
        sb.append(localConfig.apChannel);
        sb.append(" max:");
        sb.append(localConfig.maxclient);
        sb.append(" hide:");
        sb.append(localConfig.hiddenSSID ? 1 : 0);
        sb.append(" allowall:");
        if (localConfig.macaddrAcl != 3) {
            z = false;
        }
        sb.append(z);
        sb.append(" requirePMF:");
        sb.append(localConfig.requirePMF ? 1 : 0);
        String tLog = sb.toString();
        if (MHSDBG) {
            StringBuilder sb2 = new StringBuilder();
            sb2.append(tLog);
            sb2.append(" [");
            sb2.append(localConfig.preSharedKey != null ? "removed" : new byte[0]);
            sb2.append("]");
            addMHSDumpLog(sb2.toString());
            return;
        }
        addMHSDumpLog(tLog);
    }

    private static int getEncryptionType(WifiConfiguration localConfig) {
        int authType = localConfig.getAuthType();
        if (authType == 0) {
            return 0;
        }
        if (authType == 1) {
            return 1;
        }
        if (authType == 4) {
            return 2;
        }
        if (authType == 25) {
            return 4;
        }
        if (authType != 26) {
            return 0;
        }
        return 3;
    }

    private void setLocalConfigMaxClient(String maxClientNum, WifiConfiguration localConfig) {
        if ("".equals(maxClientNum)) {
            return;
        }
        if (localConfig.vendorIE == 0 || localConfig.vendorIE == 128) {
            int currentNetworkType = ((TelephonyManager) this.mContext.getSystemService("phone")).getNetworkType();
            String[] maxClientNumSplit = maxClientNum.split(",");
            int i = 0;
            while (i < maxClientNumSplit.length - 1) {
                if ("LTE".equals(maxClientNumSplit[i]) && currentNetworkType == 13) {
                    localConfig.maxclient = Integer.parseInt(maxClientNumSplit[i + 1]);
                    i = maxClientNumSplit.length;
                } else if ("HSPAP".equals(maxClientNumSplit[i]) && currentNetworkType == 15) {
                    localConfig.maxclient = Integer.parseInt(maxClientNumSplit[i + 1]);
                    i = maxClientNumSplit.length;
                } else if ("HSPA".equals(maxClientNumSplit[i]) && currentNetworkType == 10) {
                    localConfig.maxclient = Integer.parseInt(maxClientNumSplit[i + 1]);
                    i = maxClientNumSplit.length;
                } else if ("HSDPA".equals(maxClientNumSplit[i]) && currentNetworkType == 8) {
                    localConfig.maxclient = Integer.parseInt(maxClientNumSplit[i + 1]);
                    i = maxClientNumSplit.length;
                } else if ("HSUPA".equals(maxClientNumSplit[i]) && currentNetworkType == 9) {
                    localConfig.maxclient = Integer.parseInt(maxClientNumSplit[i + 1]);
                    i = maxClientNumSplit.length;
                } else if ("EDGE".equals(maxClientNumSplit[i]) && currentNetworkType == 2) {
                    localConfig.maxclient = Integer.parseInt(maxClientNumSplit[i + 1]);
                    i = maxClientNumSplit.length;
                } else if ("GPRS".equals(maxClientNumSplit[i]) && currentNetworkType == 1) {
                    localConfig.maxclient = Integer.parseInt(maxClientNumSplit[i + 1]);
                    i = maxClientNumSplit.length;
                } else if ("UMTS".equals(maxClientNumSplit[i]) && currentNetworkType == 3) {
                    localConfig.maxclient = Integer.parseInt(maxClientNumSplit[i + 1]);
                    i = maxClientNumSplit.length;
                } else if ("1xRTT".equals(maxClientNumSplit[i]) && currentNetworkType == 7) {
                    localConfig.maxclient = Integer.parseInt(maxClientNumSplit[i + 1]);
                    i = maxClientNumSplit.length;
                } else if ("CDMA".equals(maxClientNumSplit[i]) && currentNetworkType == 4) {
                    localConfig.maxclient = Integer.parseInt(maxClientNumSplit[i + 1]);
                    i = maxClientNumSplit.length;
                } else if ("EVDO_0".equals(maxClientNumSplit[i]) && currentNetworkType == 5) {
                    localConfig.maxclient = Integer.parseInt(maxClientNumSplit[i + 1]);
                    i = maxClientNumSplit.length;
                } else if ("EVDO_A".equals(maxClientNumSplit[i]) && currentNetworkType == 6) {
                    localConfig.maxclient = Integer.parseInt(maxClientNumSplit[i + 1]);
                    i = maxClientNumSplit.length;
                } else if ("EVDO_B".equals(maxClientNumSplit[i]) && currentNetworkType == 12) {
                    localConfig.maxclient = Integer.parseInt(maxClientNumSplit[i + 1]);
                    i = maxClientNumSplit.length;
                } else if ("EHRPD".equals(maxClientNumSplit[i]) && currentNetworkType == 14) {
                    localConfig.maxclient = Integer.parseInt(maxClientNumSplit[i + 1]);
                    i = maxClientNumSplit.length;
                } else if ("IDEN".equals(maxClientNumSplit[i]) && currentNetworkType == 11) {
                    localConfig.maxclient = Integer.parseInt(maxClientNumSplit[i + 1]);
                    i = maxClientNumSplit.length;
                } else if ("OTHERS".equals(maxClientNumSplit[i])) {
                    localConfig.maxclient = Integer.parseInt(maxClientNumSplit[i + 1]);
                    i = maxClientNumSplit.length;
                }
                i += 2;
            }
        }
    }

    public static String showCSCvalues() {
        StringBuffer value = new StringBuffer();
        value.append("[cscfile] value\n");
        value.append("OPBRANDING=[" + SemCscFeature.getInstance().getString(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_CONFIGOPBRANDINGFORMOBILEAP) + "]\n");
        value.append("SHOWPASSWORD=[" + SemCscFeature.getInstance().getString(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_ENABLESHOWPASSWORDASDEFAULT) + "]\n");
        value.append("MOBILEAP5G=[" + SemCscFeature.getInstance().getString(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_SUPPORTMOBILEAP5G) + "]\n");
        value.append("MOBILEAP5GBASEDONCOUNTRY=[" + SemCscFeature.getInstance().getString(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_SUPPORTMOBILEAP5GBASEDONCOUNTRY) + "]\n");
        value.append("MAXCLIENT=[" + SemCscFeature.getInstance().getString(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_MAXCLIENT4MOBILEAP) + "]\n");
        value.append("SarBackOff=[" + SemCscFeature.getInstance().getString(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_SUPPORTMOBILEAPONTRIGGER) + "]\n");
        value.append("TIMEOUT=[" + SemCscFeature.getInstance().getString(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_CONFIGMOBILEAPDEFAULTTIMEOUT) + "]\n");
        value.append("MENUMAXCLIENT=[" + SemCscFeature.getInstance().getString(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_SUPPORTMENUMOBILEAPMAXCLIENT) + "]\n");
        value.append("SSID=[" + SemCscFeature.getInstance().getString(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_CONFIGMOBILEAPDEFAULTSSID) + "]\n");
        value.append("PWD=[" + SemCscFeature.getInstance().getString(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_CONFIGMOBILEAPDEFAULTPWD) + "]\n");
        StringBuilder sb = new StringBuilder();
        sb.append("RANDOM4DIGIT=");
        sb.append(SemCscFeature.getInstance().getString(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_USERANDOM4DIGITCOMBINATIONASSSID));
        value.append(sb.toString());
        value.append("\n");
        value.append("BATTERYUSAGE" + SemCscFeature.getInstance().getString(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_ENABLEWARNINGPOPUP4DATABATTERYUSAGE));
        value.append("\n");
        value.append("DEFAULTSSIDNPWD=" + SemCscFeature.getInstance().getString(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_DEFAULTSSIDNPWD));
        value.append("\n");
        value.append("MAXCLIENT4MOBILEAPNETEXTENSION=" + SemCscFeature.getInstance().getString(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_MAXCLIENT4MOBILEAPNETEXTENSION));
        value.append("\n");
        value.append("DHCPLEASETIME=" + SemCscFeature.getInstance().getString(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_CONFIGDHCPLEASETIME));
        value.append("\n");
        value.append("DISABLEMOBILEAPWIFICONCURRENCY=" + SemCscFeature.getInstance().getString(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_DISABLEMOBILEAPWIFICONCURRENCY));
        value.append("\n");
        return value.toString();
    }

    public static String showMoreInfo() {
        StringBuffer value = new StringBuffer();
        value.append("More Info\n");
        value.append("SalesCode:" + SemCscFeature.getInstance().getString("SalesCode"));
        value.append("\n");
        value.append("SFF BRAND_NAME=" + SemFloatingFeature.getInstance().getString("SEC_FLOATING_FEATURE_SETTINGS_CONFIG_BRAND_NAME"));
        value.append("\n");
        return value.toString();
    }

    public static String showSecProductFeature() {
        StringBuffer value = new StringBuffer();
        value.append("SPF info \n");
        value.append("SPF_BackOff=true");
        value.append("\n");
        value.append("SPF_5G=false");
        value.append("\n");
        value.append("SPF_5G_BASEDON_COUNTRY=false");
        value.append("\n");
        value.append("SPF_POWER_SAVEMODE=true");
        value.append("\n");
        return value.toString();
    }
}
