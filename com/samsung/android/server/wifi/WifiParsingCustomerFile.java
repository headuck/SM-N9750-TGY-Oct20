package com.samsung.android.server.wifi;

import android.util.LocalLog;
import android.util.Log;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class WifiParsingCustomerFile {
    private static final String TAG = "WifiDefaultApController.Customer";
    private static WifiParsingCustomerFile instance;
    private static File mFilePathDefaultAp = new File("/data/misc/wifi/default_ap.conf");
    private static File mFilePathGeneralNwInfo = new File("/data/misc/wifi/generalinfo_nw.conf");
    private final LocalLog mLocalLog = new LocalLog(256);
    private CscParser mParser = null;

    private void WifiParsingCustomerFile() {
    }

    public static WifiParsingCustomerFile getInstance() {
        if (instance == null) {
            instance = new WifiParsingCustomerFile();
        }
        return instance;
    }

    /* JADX DEBUG: Failed to insert an additional move for type inference into block B:27:0x0176 */
    /* JADX WARN: Multi-variable type inference failed */
    /* JADX INFO: Multiple debug info for r6v2 java.lang.String[]: [D('GeneralNWNames' java.lang.String[]), D('WifiEAPMethods' java.lang.String[])] */
    /* JADX WARN: Type inference failed for: r14v6 */
    /* JADX WARN: Type inference failed for: r13v16 */
    /* JADX WARN: Type inference failed for: r14v8 */
    /* JADX WARN: Type inference failed for: r13v18 */
    /* JADX WARN: Type inference failed for: r13v25 */
    /* JADX WARN: Type inference failed for: r13v48 */
    /* JADX WARN: Type inference failed for: r13v49 */
    /* JADX WARN: Type inference failed for: r14v18 */
    /* JADX WARN: Type inference failed for: r14v19 */
    /* JADX WARNING: Removed duplicated region for block: B:119:0x039c  */
    /* JADX WARNING: Removed duplicated region for block: B:88:0x0291  */
    /* JADX WARNING: Unknown variable types count: 1 */
    public void getCustomerFile() {
        String str;
        String str2;
        String str3;
        NodeList GeneralInfoNodeList;
        String[] WifiNWNames;
        Node GeneralInfoNodeListChild;
        String str4;
        String str5;
        String str6;
        String TAG_NWNAME;
        String TAG_SSID = "WifiSSID";
        String TAG_KEYMGMT = "WifiKeyMgmt";
        String TAG_PSK = "WifiPSK";
        String TAG_HIDDENSSID = "WifiHiddenSSID";
        String TAG_EAPMETHOD = "WifiEAPMethod";
        String TAG_NWNAME2 = "NetworkName";
        String TAG_GENERAL_NWNAME = "NetworkName";
        this.mParser = new CscParser(CscParser.getCustomerPath());
        logi("getCustomerFile: PATH: " + CscParser.getCustomerPath());
        NodeList WifiProfileNodeList = this.mParser.searchList(this.mParser.search("Settings."), "WifiProfile");
        if (WifiProfileNodeList == null) {
            loge("getCustomerFile: No WifiProfileNodeList to setup");
            return;
        }
        int wifiVendorApNumber = WifiProfileNodeList.getLength();
        logd("getCustomerFile: parsing WifiProfile from customer.xml file, number: " + wifiVendorApNumber);
        String[] WifiSSIDs = new String[wifiVendorApNumber];
        String str7 = "}\n";
        String[] WifiKeyMgmts = new String[wifiVendorApNumber];
        String str8 = "    networkname=";
        String[] WifiPSKs = new String[wifiVendorApNumber];
        String str9 = "\n";
        String[] WifiHiddenSSIDs = new String[wifiVendorApNumber];
        String str10 = "\"";
        String[] WifiEAPMethods = new String[wifiVendorApNumber];
        String str11 = "network={\n";
        String[] WifiNWNames2 = new String[wifiVendorApNumber];
        int i = 0;
        int wifiprofilecnt = 0;
        int wifiVendorApNumber2 = wifiVendorApNumber;
        NodeList WifiProfileNodeList2 = WifiProfileNodeList;
        while (i < wifiVendorApNumber2) {
            Node WifiProfileNodeListChild = WifiProfileNodeList2.item(i);
            Node WifiSSIDNode = this.mParser.search(WifiProfileNodeListChild, TAG_SSID);
            Node WifiKeyMgmtNode = this.mParser.search(WifiProfileNodeListChild, TAG_KEYMGMT);
            Node WifiPSKNode = this.mParser.search(WifiProfileNodeListChild, TAG_PSK);
            Node WifiHiddenSSIDNode = this.mParser.search(WifiProfileNodeListChild, TAG_HIDDENSSID);
            Node WifiEAPMethodNode = this.mParser.search(WifiProfileNodeListChild, TAG_EAPMETHOD);
            Node WifiNWNameNode = this.mParser.search(WifiProfileNodeListChild, TAG_NWNAME2);
            if (WifiSSIDNode != null) {
                TAG_NWNAME = TAG_NWNAME2;
                WifiSSIDs[i] = this.mParser.getValue(WifiSSIDNode);
            } else {
                TAG_NWNAME = TAG_NWNAME2;
            }
            if (WifiKeyMgmtNode != null) {
                WifiKeyMgmts[i] = this.mParser.getValue(WifiKeyMgmtNode);
            }
            if (WifiPSKNode != null) {
                WifiPSKs[i] = this.mParser.getValue(WifiPSKNode);
            }
            if (WifiHiddenSSIDNode != null) {
                WifiHiddenSSIDs[i] = this.mParser.getValue(WifiHiddenSSIDNode);
            }
            if (WifiEAPMethodNode != null) {
                WifiEAPMethods[i] = this.mParser.getValue(WifiEAPMethodNode);
            }
            if (WifiNWNameNode != null) {
                WifiNWNames2[i] = this.mParser.getValue(WifiNWNameNode);
            }
            wifiprofilecnt++;
            i++;
            wifiVendorApNumber2 = wifiVendorApNumber2;
            WifiProfileNodeList2 = WifiProfileNodeList2;
            TAG_SSID = TAG_SSID;
            TAG_KEYMGMT = TAG_KEYMGMT;
            TAG_PSK = TAG_PSK;
            TAG_HIDDENSSID = TAG_HIDDENSSID;
            TAG_EAPMETHOD = TAG_EAPMETHOD;
            TAG_NWNAME2 = TAG_NWNAME;
        }
        try {
            StringBuilder defaultsb = new StringBuilder();
            defaultsb.setLength(0);
            logd("getCustomerFile: get Wifi default ap information");
            int j = 0;
            ?? r13 = wifiVendorApNumber2;
            String str12 = WifiProfileNodeList2;
            while (j < wifiprofilecnt) {
                str3 = str11;
                try {
                    defaultsb.append(str3);
                    if (WifiSSIDs[j] != null) {
                        TAG_EAPMETHOD = "    ssid=";
                        try {
                            defaultsb.append(TAG_EAPMETHOD);
                            TAG_EAPMETHOD = str10;
                        } catch (NullPointerException e) {
                            TAG_NWNAME2 = str9;
                            TAG_EAPMETHOD = str10;
                            str4 = str8;
                            str = str7;
                            str2 = str4;
                            loge("getCustomerFile: WIFI Profile -NullPointerException");
                            String PATH_GENERALINFO = "GeneralInfo.";
                            GeneralInfoNodeList = this.mParser.searchList(this.mParser.search(PATH_GENERALINFO), "NetworkInfo");
                            if (GeneralInfoNodeList != null) {
                            }
                        }
                        try {
                            defaultsb.append(TAG_EAPMETHOD);
                            defaultsb.append(WifiSSIDs[j]);
                            defaultsb.append(TAG_EAPMETHOD);
                            TAG_NWNAME2 = str9;
                        } catch (NullPointerException e2) {
                            TAG_NWNAME2 = str9;
                            str4 = str8;
                            str = str7;
                            str2 = str4;
                            loge("getCustomerFile: WIFI Profile -NullPointerException");
                            String PATH_GENERALINFO2 = "GeneralInfo.";
                            GeneralInfoNodeList = this.mParser.searchList(this.mParser.search(PATH_GENERALINFO2), "NetworkInfo");
                            if (GeneralInfoNodeList != null) {
                            }
                        }
                        try {
                            defaultsb.append(TAG_NWNAME2);
                        } catch (NullPointerException e3) {
                            str4 = str8;
                            str = str7;
                            str2 = str4;
                            loge("getCustomerFile: WIFI Profile -NullPointerException");
                            String PATH_GENERALINFO22 = "GeneralInfo.";
                            GeneralInfoNodeList = this.mParser.searchList(this.mParser.search(PATH_GENERALINFO22), "NetworkInfo");
                            if (GeneralInfoNodeList != null) {
                            }
                        }
                    } else {
                        TAG_NWNAME2 = str9;
                        TAG_EAPMETHOD = str10;
                    }
                    if (WifiHiddenSSIDs[j] != null) {
                        defaultsb.append("    scan_ssid=");
                        defaultsb.append(WifiHiddenSSIDs[j]);
                        defaultsb.append(TAG_NWNAME2);
                    }
                    if (WifiKeyMgmts[j] != null) {
                        defaultsb.append("    key_mgmt=");
                        defaultsb.append(WifiKeyMgmts[j]);
                        defaultsb.append(TAG_NWNAME2);
                    }
                    if (WifiPSKs[j] != null) {
                        defaultsb.append("    psk=");
                        defaultsb.append(TAG_EAPMETHOD);
                        defaultsb.append(WifiPSKs[j]);
                        defaultsb.append(TAG_EAPMETHOD);
                        defaultsb.append(TAG_NWNAME2);
                    }
                    if (WifiEAPMethods[j] != null) {
                        defaultsb.append("    eap=");
                        if ("sim".equals(WifiEAPMethods[j])) {
                            defaultsb.append("SIM");
                        } else if ("aka".equals(WifiEAPMethods[j])) {
                            defaultsb.append("AKA");
                        } else if ("akaprime".equals(WifiEAPMethods[j])) {
                            defaultsb.append("AKA'");
                        }
                        defaultsb.append(TAG_NWNAME2);
                    }
                    if (WifiNWNames2[j] != null) {
                        String str13 = str8;
                        try {
                            defaultsb.append(str13);
                            defaultsb.append(TAG_EAPMETHOD);
                            defaultsb.append(WifiNWNames2[j]);
                            defaultsb.append(TAG_EAPMETHOD);
                            defaultsb.append(TAG_NWNAME2);
                            str6 = str13;
                        } catch (NullPointerException e4) {
                            str4 = str13;
                        }
                    } else {
                        str6 = str8;
                    }
                    str12 = str7;
                    try {
                        defaultsb.append(str12);
                        j++;
                        str11 = str3;
                        str10 = TAG_EAPMETHOD;
                        str9 = TAG_NWNAME2;
                        str8 = r13;
                        str7 = str12;
                        r13 = r13;
                        str12 = str12;
                    } catch (NullPointerException e5) {
                        str2 = r13;
                        str = str12;
                        loge("getCustomerFile: WIFI Profile -NullPointerException");
                        String PATH_GENERALINFO222 = "GeneralInfo.";
                        GeneralInfoNodeList = this.mParser.searchList(this.mParser.search(PATH_GENERALINFO222), "NetworkInfo");
                        if (GeneralInfoNodeList != null) {
                        }
                    }
                } catch (NullPointerException e6) {
                    str5 = str8;
                    TAG_NWNAME2 = str9;
                    TAG_EAPMETHOD = str10;
                    str4 = str5;
                    str = str7;
                    str2 = str4;
                    loge("getCustomerFile: WIFI Profile -NullPointerException");
                    String PATH_GENERALINFO2222 = "GeneralInfo.";
                    GeneralInfoNodeList = this.mParser.searchList(this.mParser.search(PATH_GENERALINFO2222), "NetworkInfo");
                    if (GeneralInfoNodeList != null) {
                    }
                }
            }
            str2 = str8;
            str3 = str11;
            TAG_NWNAME2 = str9;
            TAG_EAPMETHOD = str10;
            str = str7;
            createDefaultApFile(defaultsb.toString());
            logi(defaultsb.toString());
        } catch (NullPointerException e7) {
            str5 = str8;
            str3 = str11;
            TAG_NWNAME2 = str9;
            TAG_EAPMETHOD = str10;
            str4 = str5;
            str = str7;
            str2 = str4;
            loge("getCustomerFile: WIFI Profile -NullPointerException");
            String PATH_GENERALINFO22222 = "GeneralInfo.";
            GeneralInfoNodeList = this.mParser.searchList(this.mParser.search(PATH_GENERALINFO22222), "NetworkInfo");
            if (GeneralInfoNodeList != null) {
            }
        }
        String PATH_GENERALINFO222222 = "GeneralInfo.";
        GeneralInfoNodeList = this.mParser.searchList(this.mParser.search(PATH_GENERALINFO222222), "NetworkInfo");
        if (GeneralInfoNodeList != null) {
            int generalNetworkNumber = GeneralInfoNodeList.getLength();
            logd("getCustomerFile: GeneralInfo, number : " + generalNetworkNumber);
            String[] GeneralMCCMNCs = new String[(wifiVendorApNumber2 * generalNetworkNumber)];
            String[] WifiEAPMethods2 = new String[(wifiVendorApNumber2 * generalNetworkNumber)];
            int i2 = 0;
            int generalinfocnt = 0;
            while (i2 < generalNetworkNumber) {
                Node GeneralInfoNodeListChild2 = GeneralInfoNodeList.item(i2);
                Node GeneralMccMncNode = this.mParser.search(GeneralInfoNodeListChild2, "MCCMNC");
                Node GeneralNWNameNode = this.mParser.search(GeneralInfoNodeListChild2, TAG_GENERAL_NWNAME);
                int generalinfocnt2 = generalinfocnt;
                int k = 0;
                while (k < wifiprofilecnt) {
                    if (WifiNWNames2[k] != null) {
                        GeneralInfoNodeListChild = GeneralInfoNodeListChild2;
                        WifiNWNames = WifiNWNames2;
                        if (WifiNWNames2[k].equals(this.mParser.getValue(GeneralNWNameNode))) {
                            if (GeneralMccMncNode != null) {
                                GeneralMCCMNCs[generalinfocnt2] = this.mParser.getValue(GeneralMccMncNode);
                            }
                            if (GeneralNWNameNode != null) {
                                WifiEAPMethods2[generalinfocnt2] = this.mParser.getValue(GeneralNWNameNode);
                            }
                            generalinfocnt2++;
                        }
                    } else {
                        GeneralInfoNodeListChild = GeneralInfoNodeListChild2;
                        WifiNWNames = WifiNWNames2;
                    }
                    k++;
                    GeneralInfoNodeListChild2 = GeneralInfoNodeListChild;
                    WifiNWNames2 = WifiNWNames;
                }
                i2++;
                generalinfocnt = generalinfocnt2;
                generalNetworkNumber = generalNetworkNumber;
                GeneralInfoNodeList = GeneralInfoNodeList;
                PATH_GENERALINFO222222 = PATH_GENERALINFO222222;
                TAG_GENERAL_NWNAME = TAG_GENERAL_NWNAME;
                WifiSSIDs = WifiSSIDs;
            }
            try {
                StringBuilder generalsb = new StringBuilder();
                generalsb.setLength(0);
                logd("getCustomerFile: get GeneralInfo NetworkInfo");
                for (int j2 = 0; j2 < generalinfocnt; j2++) {
                    generalsb.append(str3);
                    if (WifiEAPMethods2[j2] != null) {
                        generalsb.append(str2);
                        generalsb.append(TAG_EAPMETHOD);
                        generalsb.append(WifiEAPMethods2[j2]);
                        generalsb.append(TAG_EAPMETHOD);
                        generalsb.append(TAG_NWNAME2);
                    }
                    if (GeneralMCCMNCs[j2] != null) {
                        generalsb.append("    mccmnc=");
                        generalsb.append(TAG_EAPMETHOD);
                        generalsb.append(GeneralMCCMNCs[j2]);
                        generalsb.append(TAG_EAPMETHOD);
                        generalsb.append(TAG_NWNAME2);
                    }
                    generalsb.append(str);
                }
                createGeneralNetworkFile(generalsb.toString());
                logi(generalsb.toString());
            } catch (NullPointerException e8) {
                loge("getCustomerFile: GeneralInfo -NullPointerException");
            }
        }
    }

    private void createDefaultApFile(String wifiDefaultApProfile) {
        if (wifiDefaultApProfile == null) {
            loge("createDefaultApFile: createDefaultApFile is null");
            return;
        }
        if (mFilePathDefaultAp.exists()) {
            logd("createDefaultApFile: delete default_ap.conf file");
            mFilePathDefaultAp.delete();
        }
        if (mFilePathGeneralNwInfo.exists()) {
            logd("createDefaultApFile: delete generalinfo_nw.conf file");
            mFilePathGeneralNwInfo.delete();
        }
        if (wifiDefaultApProfile.length() == 0) {
            logi("createDefaultApFile: WifiProfile is empty");
            return;
        }
        FileOutputStream fw = null;
        try {
            mFilePathDefaultAp.createNewFile();
            fw = new FileOutputStream(mFilePathDefaultAp, true);
            fw.write(wifiDefaultApProfile.getBytes());
            try {
                fw.close();
            } catch (IOException e2) {
                loge(e2.toString());
            }
        } catch (FileNotFoundException e) {
            loge("WiFi Profile File Create Not Found ");
            if (fw != null) {
                fw.close();
            }
        } catch (IOException e3) {
            e3.printStackTrace();
            if (fw != null) {
                fw.close();
            }
        } catch (Throwable th) {
            if (fw != null) {
                try {
                    fw.close();
                } catch (IOException e22) {
                    loge(e22.toString());
                }
            }
            throw th;
        }
    }

    private void createGeneralNetworkFile(String generalInfoNw) {
        logd("String Matched General Info List \n" + generalInfoNw);
        if (mFilePathGeneralNwInfo.exists()) {
            logd("GeneralInfo file delete is called");
            mFilePathGeneralNwInfo.delete();
        }
        if (generalInfoNw == null) {
            loge("createGeneralNetworkFile: generalInfoNw is null");
        } else if (generalInfoNw.length() == 0) {
            logi("Settings.Secure.WIFI_GENERALINFO_NWINFO is empty");
        } else {
            FileOutputStream generalFW = null;
            try {
                mFilePathGeneralNwInfo.createNewFile();
                generalFW = new FileOutputStream(mFilePathGeneralNwInfo, true);
                generalFW.write(generalInfoNw.getBytes());
                try {
                    generalFW.close();
                } catch (IOException e2) {
                    loge(e2.toString());
                }
            } catch (FileNotFoundException e) {
                loge("GeneralNwInfo File Create Not Found ");
                if (generalFW != null) {
                    generalFW.close();
                }
            } catch (IOException e3) {
                e3.printStackTrace();
                if (generalFW != null) {
                    generalFW.close();
                }
            } catch (Throwable th) {
                if (generalFW != null) {
                    try {
                        generalFW.close();
                    } catch (IOException e22) {
                        loge(e22.toString());
                    }
                }
                throw th;
            }
        }
    }

    /* access modifiers changed from: protected */
    public void loge(String s) {
        Log.e(TAG, s);
        this.mLocalLog.log(s);
    }

    /* access modifiers changed from: protected */
    public void logd(String s) {
        Log.d(TAG, s);
        this.mLocalLog.log(s);
    }

    /* access modifiers changed from: protected */
    public void logi(String s) {
        Log.i(TAG, s);
        this.mLocalLog.log(s);
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("==== Customer File Dump ====");
        this.mLocalLog.dump(fd, pw, args);
    }
}
