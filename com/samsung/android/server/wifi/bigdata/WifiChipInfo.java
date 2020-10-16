package com.samsung.android.server.wifi.bigdata;

import com.android.server.wifi.hotspot2.SystemInfo;
import com.android.server.wifi.util.TelephonyUtil;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;

public class WifiChipInfo {
    private static final String CID_INFO_MURATA = "murata";
    private static final String CID_INFO_MURATAFEM1 = "MURATAFEM1";
    private static final String CID_INFO_MURATAFEM2 = "MURATAFEM2";
    private static final String CID_INFO_MURATAFEM3 = "MURATAFEM3";
    private static final String CID_INFO_SAMSUNG = "SAMSUNG";
    private static final String CID_INFO_SAMSUNGVE = "SAMSUNGVE";
    private static final String CID_INFO_SEMCO = "SEMCO";
    private static final String CID_INFO_SEMCO3RD = "SEMCO3RD";
    private static final String CID_INFO_SEMCOSH = "SEMCOSH";
    private static final String CID_INFO_WISOL = "WISOL";
    private static final String CID_INFO_WISOLFEM1 = "WISOLFEM1";
    private static final String KEY_CHIPSET_VENDOR_NAME = "ld_cnm";
    private static final String KEY_CID_INFO = "Cid_Info";
    private static final String KEY_DRIVER_VERSION = "ld_drv";
    private static final String KEY_FIRMWARE_VERSION = "ld_fwv";
    private static final String KEY_MAC_ADDRESS = "mac_add";
    private static final String NULL_STRING = "null";
    private static final String STRING_NOT_READY = "not ready";
    private static final String WIFI_VER_PREFIX_BRCM = "HD_ver";
    private static final String WIFI_VER_PREFIX_MAVL = "received";
    private static final String WIFI_VER_PREFIX_MTK = "ediatek";
    private static final String WIFI_VER_PREFIX_QCA = "FW:";
    private static final String WIFI_VER_PREFIX_QCOM = "CNSS";
    private static final String WIFI_VER_PREFIX_SLSI = "rv_ver:";
    private static final String WIFI_VER_PREFIX_SPRTRM = "is 0x";
    private static String mChipsetName;
    private static String mCidInfo;
    private static WifiChipInfo sInstance;
    private String mDriverVer = NULL_STRING;
    private String mFirmwareVer = NULL_STRING;
    private String mFirmwareVerFactory = NULL_STRING;
    private boolean mIsReady;
    private boolean mIsReceivedArpResponse = false;
    private String mMacAddress;
    private boolean mNetworkIpConflict = false;
    private String mWifiVerInfoString = NULL_STRING;

    private WifiChipInfo() {
        mChipsetName = NULL_STRING;
        mCidInfo = NULL_STRING;
        this.mIsReady = false;
    }

    public static synchronized WifiChipInfo getInstance() {
        WifiChipInfo wifiChipInfo;
        synchronized (WifiChipInfo.class) {
            if (sInstance == null) {
                sInstance = new WifiChipInfo();
            }
            wifiChipInfo = sInstance;
        }
        return wifiChipInfo;
    }

    public boolean isReady() {
        return this.mIsReady;
    }

    public void updateChipInfos(String cidInfo, String wifiVerInfo) {
        if (wifiVerInfo != null && wifiVerInfo.length() != 0) {
            this.mWifiVerInfoString = wifiVerInfo;
            this.mFirmwareVer = setFirmwareVer(wifiVerInfo, false);
            this.mDriverVer = setDriverVer(wifiVerInfo);
            mChipsetName = setChipsetName(wifiVerInfo);
            mCidInfo = setCidInfo(cidInfo);
            this.mIsReady = true;
        }
    }

    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append(convertToQuotedString(KEY_FIRMWARE_VERSION) + ":");
        sb.append(convertToQuotedString(this.mFirmwareVer) + ",");
        sb.append(convertToQuotedString(KEY_DRIVER_VERSION) + ":");
        sb.append(convertToQuotedString(this.mDriverVer) + ",");
        sb.append(convertToQuotedString(KEY_CHIPSET_VENDOR_NAME) + ":");
        sb.append(convertToQuotedString(mChipsetName));
        return sb.toString();
    }

    private static String convertToQuotedString(String string) {
        return "\"" + string + "\"";
    }

    public String getFirmwareVer(boolean factorymode) {
        if (!factorymode) {
            return this.mFirmwareVer;
        }
        if (!this.mIsReady) {
            return STRING_NOT_READY;
        }
        return setFirmwareVer(this.mWifiVerInfoString, true);
    }

    private String setFirmwareVer(String wifiVerInfo, boolean factorymode) {
        String retString;
        int verStart;
        String retString2;
        String retString3;
        String retString4;
        String retString5;
        String retString6;
        String retString7;
        String retString8;
        if (wifiVerInfo == null || wifiVerInfo.length() == 0) {
            return "error";
        }
        BufferedReader br = null;
        try {
            BufferedReader br2 = new BufferedReader(new StringReader(wifiVerInfo));
            String verString = br2.readLine();
            if (verString != null) {
                if (verString.contains(WIFI_VER_PREFIX_BRCM)) {
                    verString = br2.readLine();
                    if (verString != null) {
                        int verStart2 = verString.indexOf("version");
                        if (verStart2 != -1) {
                            int verStart3 = "version".length() + verStart2 + 1;
                            int verEnd = verString.indexOf(" ", verStart3);
                            if (factorymode) {
                                retString8 = "BR" + verString.substring(verStart3, verEnd);
                            } else {
                                retString8 = verString.substring(verStart3, verEnd);
                            }
                            try {
                                br2.close();
                                return retString8;
                            } catch (IOException e) {
                                return "File Close error";
                            }
                        } else {
                            retString = "NG";
                        }
                    } else {
                        try {
                            br2.close();
                            return "file was damaged, it need check !";
                        } catch (IOException e2) {
                            return "File Close error";
                        }
                    }
                } else if (verString.contains(WIFI_VER_PREFIX_QCOM)) {
                    int verStart4 = verString.indexOf(WIFI_VER_PREFIX_QCOM);
                    if (verStart4 != -1) {
                        int verStart5 = verStart4 + "CNSS-PR-".length();
                        if (factorymode) {
                            retString7 = "QC" + verString.substring(verStart5);
                        } else {
                            retString7 = verString.substring(verStart5);
                        }
                        try {
                            br2.close();
                            return retString7;
                        } catch (IOException e3) {
                            return "File Close error";
                        }
                    } else {
                        retString = "NG";
                    }
                } else if (verString.contains(WIFI_VER_PREFIX_QCA)) {
                    int verStart6 = verString.indexOf("FW");
                    if (verStart6 != -1) {
                        int verStart7 = "FW".length() + verStart6 + 1;
                        int verEnd2 = verString.indexOf("HW") - 2;
                        if (factorymode) {
                            retString6 = "QC" + verString.substring(verStart7, verEnd2);
                        } else {
                            retString6 = verString.substring(verStart7, verEnd2);
                        }
                        try {
                            br2.close();
                            return retString6;
                        } catch (IOException e4) {
                            return "File Close error";
                        }
                    } else {
                        retString = "NG";
                    }
                } else if (verString.contains(WIFI_VER_PREFIX_MAVL)) {
                    int verStart8 = verString.indexOf(".p") + 1;
                    if (verStart8 != -1) {
                        String verString2 = verString.substring(verStart8);
                        int verEnd3 = verString2.indexOf("-");
                        if (factorymode) {
                            retString5 = "MV" + verString2.substring(0, verEnd3);
                        } else {
                            retString5 = verString2.substring(0, verEnd3);
                        }
                        try {
                            br2.close();
                            return retString5;
                        } catch (IOException e5) {
                            return "File Close error";
                        }
                    } else {
                        retString = "NG";
                    }
                } else if (verString.contains(WIFI_VER_PREFIX_SPRTRM)) {
                    if (verString.indexOf("driver version is ") + "driver version is ".length() + 1 != -1) {
                        int verEnd4 = verString.indexOf("] [");
                        if (factorymode) {
                            retString4 = "SP" + verString.substring(0, verEnd4);
                        } else {
                            retString4 = verString.substring(0, verEnd4);
                        }
                        try {
                            br2.close();
                            return retString4;
                        } catch (IOException e6) {
                            return "File Close error";
                        }
                    } else {
                        retString = "NG";
                    }
                } else if (verString.contains(WIFI_VER_PREFIX_SLSI)) {
                    verString = br2.readLine();
                    int verStart9 = verString.indexOf("|") + 1;
                    if (verStart9 != -1) {
                        String verString3 = verString.substring(verStart9);
                        int verEnd5 = verString3.indexOf("|");
                        if (factorymode) {
                            retString3 = "LS" + verString3.substring(0, verEnd5);
                        } else {
                            retString3 = verString3.substring(0, verEnd5);
                        }
                        try {
                            br2.close();
                            return retString3;
                        } catch (IOException e7) {
                            return "File Close error";
                        }
                    } else {
                        retString = "NG";
                    }
                } else if (verString.contains(WIFI_VER_PREFIX_MTK)) {
                    br2.readLine();
                    verString = br2.readLine();
                    int verStart10 = verString.indexOf("FW");
                    if (verStart10 != -1) {
                        if (verString.length() > 15) {
                            verStart = verString.length() - 15;
                        } else {
                            verStart = verStart10 + "FW_VER: ".length();
                        }
                        if (factorymode) {
                            retString2 = "MT" + verString.substring(verStart);
                        } else {
                            retString2 = verString.substring(verStart);
                        }
                        try {
                            br2.close();
                            return retString2;
                        } catch (IOException e8) {
                            return "File Close error";
                        }
                    } else {
                        retString = "NG";
                    }
                } else {
                    retString = "NG";
                }
                if ("NG".equals(retString)) {
                    try {
                        br2.close();
                        return verString;
                    } catch (IOException e9) {
                        return "File Close error";
                    }
                } else {
                    try {
                        br2.close();
                        return "error";
                    } catch (IOException e10) {
                        return "File Close error";
                    }
                }
            } else {
                try {
                    br2.close();
                    return "file is null .. !";
                } catch (IOException e11) {
                    return "File Close error";
                }
            }
        } catch (IOException e12) {
            if (0 != 0) {
                try {
                    br.close();
                } catch (IOException e13) {
                    return "File Close error";
                }
            }
            return "exception";
        }
    }

    public String getDriverVer() {
        return this.mDriverVer;
    }

    private String setDriverVer(String wifiVerInfo) {
        String retString;
        if (wifiVerInfo == null || wifiVerInfo.length() == 0) {
            return "error";
        }
        BufferedReader br = null;
        try {
            BufferedReader br2 = new BufferedReader(new StringReader(wifiVerInfo));
            String verString = br2.readLine();
            if (verString != null) {
                if (verString.contains(WIFI_VER_PREFIX_BRCM)) {
                    int verStart = verString.indexOf("HD_ver:");
                    if (verStart != -1) {
                        int verStart2 = "HD_ver:".length() + verStart + 1;
                        String retString2 = verString.substring(verStart2, verString.indexOf(" ", verStart2));
                        try {
                            br2.close();
                            return retString2;
                        } catch (IOException e) {
                            return "File Close error";
                        }
                    } else {
                        retString = "NG";
                    }
                } else if (verString.contains(WIFI_VER_PREFIX_QCOM)) {
                    int verStart3 = verString.indexOf("v");
                    if (verStart3 != -1) {
                        String retString3 = verString.substring(verStart3 + "v".length(), verString.indexOf(" CNSS"));
                        try {
                            br2.close();
                            return retString3;
                        } catch (IOException e2) {
                            return "File Close error";
                        }
                    } else {
                        retString = "NG";
                    }
                } else if (verString.contains(WIFI_VER_PREFIX_QCA)) {
                    int verStart4 = verString.indexOf("SW");
                    if (verStart4 != -1) {
                        String retString4 = verString.substring("SW".length() + verStart4 + 1, verString.indexOf("FW") - 2);
                        try {
                            br2.close();
                            return retString4;
                        } catch (IOException e3) {
                            return "File Close error";
                        }
                    } else {
                        retString = "NG";
                    }
                } else if (verString.contains(WIFI_VER_PREFIX_MAVL)) {
                    int verStart5 = verString.indexOf("-GPL") - 4;
                    if (verStart5 != -1) {
                        String retString5 = verString.substring(verStart5, verString.indexOf("-GPL"));
                        try {
                            br2.close();
                            return retString5;
                        } catch (IOException e4) {
                            return "File Close error";
                        }
                    } else {
                        retString = "NG";
                    }
                } else if (verString.contains(WIFI_VER_PREFIX_SPRTRM)) {
                    int verStart6 = verString.indexOf("cp version is ") + "cp version is ".length();
                    if (verStart6 != -1) {
                        String retString6 = verString.substring(verStart6, verString.indexOf("date") - 2);
                        try {
                            br2.close();
                            return retString6;
                        } catch (IOException e5) {
                            return "File Close error";
                        }
                    } else {
                        retString = "NG";
                    }
                } else if (verString.contains(WIFI_VER_PREFIX_SLSI)) {
                    int verStart7 = verString.indexOf("drv_ver:");
                    if (verStart7 != -1) {
                        String retString7 = verString.substring("drv_ver:".length() + verStart7 + 1);
                        try {
                            br2.close();
                            return retString7;
                        } catch (IOException e6) {
                            return "File Close error";
                        }
                    } else {
                        retString = "NG";
                    }
                } else if (verString.contains(WIFI_VER_PREFIX_MTK)) {
                    verString = br2.readLine();
                    int verStart8 = verString.indexOf("DRIVER_VER");
                    if (verStart8 != -1) {
                        String retString8 = verString.substring(verStart8 + "DRIVER_VER: ".length());
                        try {
                            br2.close();
                            return retString8;
                        } catch (IOException e7) {
                            return "File Close error";
                        }
                    } else {
                        retString = "NG";
                    }
                } else {
                    retString = "NG";
                }
                if ("NG".equals(retString)) {
                    try {
                        br2.close();
                        return verString;
                    } catch (IOException e8) {
                        return "File Close error";
                    }
                } else {
                    try {
                        br2.close();
                        return "error";
                    } catch (IOException e9) {
                        return "File Close error";
                    }
                }
            } else {
                try {
                    br2.close();
                    return "file is null .. !";
                } catch (IOException e10) {
                    return "File Close error";
                }
            }
        } catch (IOException e11) {
            if (0 != 0) {
                try {
                    br.close();
                } catch (IOException e12) {
                    return "File Close error";
                }
            }
            return "exception";
        }
    }

    private String setCidInfo(String infoString) {
        if (infoString == null || infoString.length() == 0) {
            return NULL_STRING;
        }
        if (infoString.charAt(infoString.length() - 1) == 0) {
            infoString = infoString.replace(TelephonyUtil.DEFAULT_EAP_PREFIX, "");
        }
        return infoString.trim();
    }

    public String getCidInfo() {
        return mCidInfo;
    }

    public String getCidInfoForKeyValueType() {
        StringBuffer sb = new StringBuffer();
        sb.append(convertToQuotedString(KEY_CID_INFO) + ":");
        sb.append(convertToQuotedString(mCidInfo));
        return sb.toString();
    }

    public static String getChipsetName() {
        return mChipsetName;
    }

    /* JADX INFO: Can't fix incorrect switch cases order, some code will duplicate */
    public static String getChipsetNameHumanReadable() {
        char c;
        String str = mChipsetName;
        switch (str.hashCode()) {
            case 49:
                if (str.equals("1")) {
                    c = 0;
                    break;
                }
                c = 65535;
                break;
            case 50:
                if (str.equals("2")) {
                    c = 1;
                    break;
                }
                c = 65535;
                break;
            case 51:
                if (str.equals("3")) {
                    c = 2;
                    break;
                }
                c = 65535;
                break;
            case 52:
                if (str.equals("4")) {
                    c = 3;
                    break;
                }
                c = 65535;
                break;
            case 53:
                if (str.equals("5")) {
                    c = 4;
                    break;
                }
                c = 65535;
                break;
            case 54:
                if (str.equals("6")) {
                    c = 5;
                    break;
                }
                c = 65535;
                break;
            case 55:
                if (str.equals("7")) {
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
                return "Broadcom";
            case 1:
                return "Qualcomm";
            case 2:
                return "QCA";
            case 3:
                return "Marvell";
            case 4:
                return "Spreadtrum";
            case 5:
                return "S.LSI";
            case 6:
                return "MTK";
            default:
                return SystemInfo.UNKNOWN_INFO;
        }
    }

    private static String setChipsetName(String wifiVerInfo) {
        if (wifiVerInfo == null || wifiVerInfo.length() == 0) {
            return "error";
        }
        BufferedReader br = null;
        try {
            BufferedReader br2 = new BufferedReader(new StringReader(wifiVerInfo));
            String verString = br2.readLine();
            if (verString == null) {
                try {
                    br2.close();
                    return "91";
                } catch (IOException e) {
                    return "93";
                }
            } else if (verString.contains(WIFI_VER_PREFIX_BRCM)) {
                try {
                    br2.close();
                    return "1";
                } catch (IOException e2) {
                    return "93";
                }
            } else if (verString.contains(WIFI_VER_PREFIX_QCOM)) {
                try {
                    br2.close();
                    return "2";
                } catch (IOException e3) {
                    return "93";
                }
            } else if (verString.contains(WIFI_VER_PREFIX_QCA)) {
                try {
                    br2.close();
                    return "3";
                } catch (IOException e4) {
                    return "93";
                }
            } else if (verString.contains(WIFI_VER_PREFIX_MAVL)) {
                try {
                    br2.close();
                    return "4";
                } catch (IOException e5) {
                    return "93";
                }
            } else if (verString.contains(WIFI_VER_PREFIX_SPRTRM)) {
                try {
                    br2.close();
                    return "5";
                } catch (IOException e6) {
                    return "93";
                }
            } else if (verString.contains(WIFI_VER_PREFIX_SLSI)) {
                try {
                    br2.close();
                    return "6";
                } catch (IOException e7) {
                    return "93";
                }
            } else if (verString.contains(WIFI_VER_PREFIX_MTK)) {
                try {
                    br2.close();
                    return "7";
                } catch (IOException e8) {
                    return "93";
                }
            } else if ("NG".equals("NG")) {
                try {
                    br2.close();
                    return "90";
                } catch (IOException e9) {
                    return "93";
                }
            } else {
                try {
                    br2.close();
                    return "94";
                } catch (IOException e10) {
                    return "93";
                }
            }
        } catch (IOException e11) {
            if (0 != 0) {
                try {
                    br.close();
                } catch (IOException e12) {
                    return "93";
                }
            }
            return "92";
        }
    }

    public String readWifiVersion() {
        return this.mWifiVerInfoString;
    }

    public String getMacAddress() {
        return this.mMacAddress;
    }

    public void setMacAddress(String macAddress) {
        this.mMacAddress = macAddress;
    }

    public void setDuplicatedIpDetect(boolean enable) {
        this.mNetworkIpConflict = enable;
    }

    public boolean getDuplicatedIpDetect() {
        return this.mNetworkIpConflict;
    }

    public void setArpResult(boolean enable) {
        this.mIsReceivedArpResponse = enable;
    }

    public boolean getArpResult() {
        return this.mIsReceivedArpResponse;
    }
}
