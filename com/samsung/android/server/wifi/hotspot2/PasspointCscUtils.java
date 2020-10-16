package com.samsung.android.server.wifi.hotspot2;

import android.util.Log;
import com.android.server.wifi.util.XmlUtil;
import com.samsung.android.server.wifi.CscParser;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class PasspointCscUtils {
    private static final String TAG = "PasspointCscUtils";
    private static PasspointCscUtils instance;
    private static File mPasspointCredentialFile = new File("/data/misc/wifi/cred.conf");
    private CscParser mParser = null;

    /* renamed from: sb */
    StringBuffer f53sb = new StringBuffer();

    private void PasspointCscUtils() {
    }

    public static PasspointCscUtils getInstance() {
        if (instance == null) {
            instance = new PasspointCscUtils();
        }
        return instance;
    }

    /* JADX INFO: Multiple debug info for r13v8 java.lang.String[]: [D('FriendlyName' java.lang.String[]), D('FullyQualifiedDomainName' java.lang.String[])] */
    /* JADX INFO: Multiple debug info for r13v9 java.lang.String[]: [D('RoamingConsortiumOis' java.lang.String[]), D('FriendlyName' java.lang.String[])] */
    /* JADX INFO: Multiple debug info for r13v10 java.lang.String[]: [D('Realm' java.lang.String[]), D('RoamingConsortiumOis' java.lang.String[])] */
    /* JADX INFO: Multiple debug info for r13v11 java.lang.String[]: [D('Realm' java.lang.String[]), D('Eap' java.lang.String[])] */
    /* JADX INFO: Multiple debug info for r13v12 java.lang.String[]: [D('Eap' java.lang.String[]), D('UserName' java.lang.String[])] */
    /* JADX INFO: Multiple debug info for r13v13 java.lang.String[]: [D('UserName' java.lang.String[]), D('Password' java.lang.String[])] */
    /* JADX INFO: Multiple debug info for r13v14 java.lang.String[]: [D('NonEAPInnerMethodes' java.lang.String[]), D('Password' java.lang.String[])] */
    /* JADX INFO: Multiple debug info for r13v15 java.lang.String[]: [D('NonEAPInnerMethodes' java.lang.String[]), D('Imsi' java.lang.String[])] */
    /* JADX INFO: Multiple debug info for r13v16 java.lang.String[]: [D('Imsi' java.lang.String[]), D('Priority' java.lang.String[])] */
    /* JADX INFO: Multiple debug info for r13v17 java.lang.String[]: [D('CaCertificateKey' java.lang.String[]), D('Priority' java.lang.String[])] */
    /* JADX INFO: Multiple debug info for r13v18 java.lang.String[]: [D('CaCertificateKey' java.lang.String[]), D('ClientCertificate' java.lang.String[])] */
    /* JADX INFO: Multiple debug info for r13v19 java.lang.String[]: [D('ClientPrivateKey' java.lang.String[]), D('ClientCertificate' java.lang.String[])] */
    /* JADX INFO: Multiple debug info for r3v26 'FRIENDLY_NAME'  org.w3c.dom.Node: [D('CLIENT_CERTIFICATE' org.w3c.dom.Node), D('FRIENDLY_NAME' org.w3c.dom.Node)] */
    public boolean parsingCsc() {
        Node FRIENDLY_NAME;
        String XML_TAG_FQDN = "FullyQualifiedDomainName";
        String XML_TAG_FRIENDLY_NAME = "FriendlyName";
        String XML_TAG_ROAMING_CONSORTIUM_OIS = "RoamingConsortium";
        String XML_TAG_REALM = XmlUtil.WifiEnterpriseConfigXmlUtil.XML_TAG_REALM;
        String XML_TAG_EAP_TYPE = "Eap";
        String XML_TAG_USERNAME = "UserName";
        String XML_TAG_PASSWORD = XmlUtil.WifiEnterpriseConfigXmlUtil.XML_TAG_PASSWORD;
        String XML_TAG_NON_EAP_INNER_METHOD = "InnerMethod";
        String XML_TAG_IMSI = "Imsi";
        String XML_TAG_CA_CERTIFICATE_KEY = XmlUtil.WifiEnterpriseConfigXmlUtil.XML_TAG_CA_CERT;
        String XML_TAG_CLIENT_PRIVATE_KEY_PASSWORD = "ClientKeyPassword";
        String XML_TAG_PRIORITY = "Priority";
        logd("parsingCsc, getCustomerPath: " + CscParser.getCustomerPath());
        this.mParser = new CscParser(CscParser.getCustomerPath());
        NodeList passpointProfileNodeList = this.mParser.searchList(this.mParser.search("Settings.Wifi"), "Hs20Profile");
        if (passpointProfileNodeList == null) {
            loge("parsingCsc, passpointProfileNodeList is null.");
            return false;
        }
        logd("parsingCsc, parsing WifiHS20Profile from customer.xml.");
        int passpointVendorApNumber = passpointProfileNodeList.getLength();
        logd("parsingCsc, passpointVendorApNumber: " + passpointVendorApNumber);
        String[] FullyQualifiedDomainName = new String[passpointVendorApNumber];
        String[] FullyQualifiedDomainName2 = new String[passpointVendorApNumber];
        String[] FriendlyName = new String[passpointVendorApNumber];
        String[] RoamingConsortiumOis = new String[passpointVendorApNumber];
        String[] Eap = new String[passpointVendorApNumber];
        String[] UserName = new String[passpointVendorApNumber];
        String[] Password = new String[passpointVendorApNumber];
        String[] Password2 = new String[passpointVendorApNumber];
        String[] Imsi = new String[passpointVendorApNumber];
        String[] Priority = new String[passpointVendorApNumber];
        String[] Priority2 = new String[passpointVendorApNumber];
        String[] ClientCertificate = new String[passpointVendorApNumber];
        String[] ClientCertificate2 = new String[passpointVendorApNumber];
        String[] ClientKeyPassword = new String[passpointVendorApNumber];
        if (passpointVendorApNumber == 0) {
            logd("parsingCsc, passpointVendorApNumber is 0.");
            return false;
        }
        int i = 0;
        int passpointCredendtialProfileCnt = 0;
        while (i < passpointVendorApNumber) {
            Node passpointProfileNodeListChild = passpointProfileNodeList.item(i);
            Node FQDN = this.mParser.search(passpointProfileNodeListChild, XML_TAG_FQDN);
            Node FRIENDLY_NAME2 = this.mParser.search(passpointProfileNodeListChild, XML_TAG_FRIENDLY_NAME);
            Node ROAMING_CONSORTIUM_OIS = this.mParser.search(passpointProfileNodeListChild, XML_TAG_ROAMING_CONSORTIUM_OIS);
            Node REALM = this.mParser.search(passpointProfileNodeListChild, XML_TAG_REALM);
            Node EAP_TYPE = this.mParser.search(passpointProfileNodeListChild, XML_TAG_EAP_TYPE);
            Node USERNAME = this.mParser.search(passpointProfileNodeListChild, XML_TAG_USERNAME);
            Node PASSWORD = this.mParser.search(passpointProfileNodeListChild, XML_TAG_PASSWORD);
            Node NON_EAP_INNER_METHOD = this.mParser.search(passpointProfileNodeListChild, XML_TAG_NON_EAP_INNER_METHOD);
            Node IMSI = this.mParser.search(passpointProfileNodeListChild, XML_TAG_IMSI);
            Node PRIORITY = this.mParser.search(passpointProfileNodeListChild, XML_TAG_PRIORITY);
            Node CA_CERTIFICATE_KEY = this.mParser.search(passpointProfileNodeListChild, XML_TAG_CA_CERTIFICATE_KEY);
            Node CLIENT_CERTIFICATE = this.mParser.search(passpointProfileNodeListChild, XmlUtil.WifiEnterpriseConfigXmlUtil.XML_TAG_CLIENT_CERT);
            Node CLIENT_PRIVATE_KEY = this.mParser.search(passpointProfileNodeListChild, "ClientKey");
            Node CLIENT_PRIVATE_KEY_PASSWORD = this.mParser.search(passpointProfileNodeListChild, XML_TAG_CLIENT_PRIVATE_KEY_PASSWORD);
            if (FQDN != null) {
                FullyQualifiedDomainName[i] = this.mParser.getValue(FQDN);
            }
            if (FRIENDLY_NAME2 != null) {
                FullyQualifiedDomainName2[i] = this.mParser.getValue(FRIENDLY_NAME2);
            }
            if (ROAMING_CONSORTIUM_OIS != null) {
                FriendlyName[i] = this.mParser.getValue(ROAMING_CONSORTIUM_OIS);
            }
            if (REALM != null) {
                RoamingConsortiumOis[i] = this.mParser.getValue(REALM);
            }
            if (EAP_TYPE != null) {
                Eap[i] = this.mParser.getValue(EAP_TYPE);
            }
            if (USERNAME != null) {
                UserName[i] = this.mParser.getValue(USERNAME);
            }
            if (PASSWORD != null) {
                Password[i] = this.mParser.getValue(PASSWORD);
            }
            if (NON_EAP_INNER_METHOD != null) {
                Password2[i] = this.mParser.getValue(NON_EAP_INNER_METHOD);
            }
            if (IMSI != null) {
                Imsi[i] = this.mParser.getValue(IMSI);
            }
            if (PRIORITY != null) {
                Priority[i] = this.mParser.getValue(PRIORITY);
            }
            if (CA_CERTIFICATE_KEY != null) {
                Priority2[i] = this.mParser.getValue(CA_CERTIFICATE_KEY);
            }
            if (CLIENT_CERTIFICATE != null) {
                FRIENDLY_NAME = CLIENT_CERTIFICATE;
                ClientCertificate[i] = this.mParser.getValue(FRIENDLY_NAME);
            } else {
                FRIENDLY_NAME = CLIENT_CERTIFICATE;
            }
            if (CLIENT_PRIVATE_KEY != null) {
                ClientCertificate2[i] = this.mParser.getValue(CLIENT_PRIVATE_KEY);
            }
            if (CLIENT_PRIVATE_KEY_PASSWORD != null) {
                ClientKeyPassword[i] = this.mParser.getValue(CLIENT_PRIVATE_KEY_PASSWORD);
            }
            passpointCredendtialProfileCnt++;
            i++;
            XML_TAG_CLIENT_PRIVATE_KEY_PASSWORD = XML_TAG_CLIENT_PRIVATE_KEY_PASSWORD;
            passpointVendorApNumber = passpointVendorApNumber;
            passpointProfileNodeList = passpointProfileNodeList;
            XML_TAG_FQDN = XML_TAG_FQDN;
            XML_TAG_FRIENDLY_NAME = XML_TAG_FRIENDLY_NAME;
            XML_TAG_ROAMING_CONSORTIUM_OIS = XML_TAG_ROAMING_CONSORTIUM_OIS;
            XML_TAG_REALM = XML_TAG_REALM;
            XML_TAG_EAP_TYPE = XML_TAG_EAP_TYPE;
            XML_TAG_USERNAME = XML_TAG_USERNAME;
            XML_TAG_PASSWORD = XML_TAG_PASSWORD;
            XML_TAG_NON_EAP_INNER_METHOD = XML_TAG_NON_EAP_INNER_METHOD;
            XML_TAG_IMSI = XML_TAG_IMSI;
            XML_TAG_PRIORITY = XML_TAG_PRIORITY;
            XML_TAG_CA_CERTIFICATE_KEY = XML_TAG_CA_CERTIFICATE_KEY;
            ClientKeyPassword = ClientKeyPassword;
        }
        try {
            StringBuilder credsb = new StringBuilder();
            credsb.setLength(0);
            logd("parsingCsc, build string of the passpoint credential(count: " + passpointCredendtialProfileCnt + ")");
            if (passpointCredendtialProfileCnt == 0) {
                return false;
            }
            for (int j = 0; j < passpointCredendtialProfileCnt; j++) {
                credsb.append("cred={\n");
                if (FullyQualifiedDomainName[j] != null) {
                    credsb.append("    domain=");
                    credsb.append("\"");
                    credsb.append(FullyQualifiedDomainName[j]);
                    credsb.append("\"");
                    credsb.append("\n");
                }
                if (FullyQualifiedDomainName2[j] != null) {
                    credsb.append("    friendlyname=");
                    credsb.append("\"");
                    credsb.append(FullyQualifiedDomainName2[j]);
                    credsb.append("\"");
                    credsb.append("\n");
                }
                if (FriendlyName[j] != null) {
                    credsb.append("    roaming_consortium=");
                    credsb.append(FriendlyName[j]);
                    credsb.append("\n");
                }
                if (RoamingConsortiumOis[j] != null) {
                    credsb.append("    realm=");
                    credsb.append("\"");
                    credsb.append(RoamingConsortiumOis[j]);
                    credsb.append("\"");
                    credsb.append("\n");
                }
                if (Priority2[j] != null) {
                    credsb.append("    ca_cert=");
                    credsb.append("\"");
                    credsb.append(Priority2[j]);
                    credsb.append("\"");
                    credsb.append("\n");
                }
                if (ClientCertificate[j] != null) {
                    credsb.append("    client_cert=");
                    credsb.append("\"");
                    credsb.append(ClientCertificate[j]);
                    credsb.append("\"");
                    credsb.append("\n");
                }
                if (Eap[j] != null) {
                    credsb.append("    eap=");
                    credsb.append(Eap[j]);
                    credsb.append("\n");
                }
                if (UserName[j] != null) {
                    credsb.append("    username=");
                    credsb.append("\"");
                    credsb.append(UserName[j]);
                    credsb.append("\"");
                    credsb.append("\n");
                }
                if (Password[j] != null) {
                    credsb.append("    password=");
                    credsb.append("\"");
                    credsb.append(Password[j]);
                    credsb.append("\"");
                    credsb.append("\n");
                }
                if (Password2[j] != null) {
                    credsb.append("    phase2=");
                    credsb.append("\"");
                    credsb.append(Password2[j]);
                    credsb.append("\"");
                    credsb.append("\n");
                }
                if (Imsi[j] != null) {
                    credsb.append("    imsi=");
                    credsb.append("\"");
                    credsb.append(Imsi[j]);
                    credsb.append("\"");
                    credsb.append("\n");
                }
                if (Priority[j] != null) {
                    credsb.append("    priority=");
                    credsb.append(Priority[j]);
                    credsb.append("\n");
                }
                if (ClientCertificate2[j] != null) {
                    credsb.append("    private_key=");
                    credsb.append("\"");
                    credsb.append(ClientCertificate2[j]);
                    credsb.append("\"");
                    credsb.append("\n");
                }
                if (ClientKeyPassword[j] != null) {
                    credsb.append("    private_key_password=");
                    credsb.append("\"");
                    credsb.append(ClientKeyPassword[j]);
                    credsb.append("\"");
                    credsb.append("\n");
                }
                credsb.append("}\n");
            }
            logd("parsingCsc, credsb.toString(): " + credsb.toString());
            if (!createPasspointCredendtial(credsb.toString())) {
                loge("parsingCsc, createPasspointCredendtial is false.");
                return false;
            }
            this.f53sb.append(credsb.toString());
            return true;
        } catch (NullPointerException e) {
            loge("parsingCsc, NullPointerException");
            return false;
        }
    }

    private boolean createPasspointCredendtial(String passpointCredendtialProfile) {
        if (passpointCredendtialProfile == null) {
            loge("createPasspointCredendtial, passpointCredendtialProfile is null.");
            return false;
        } else if (passpointCredendtialProfile.length() == 0) {
            logi(", createPasspointCredendtial, There is no Profile in customer.xml.");
            return false;
        } else {
            FileOutputStream out = null;
            try {
                mPasspointCredentialFile.createNewFile();
                out = new FileOutputStream(mPasspointCredentialFile, true);
                out.write(passpointCredendtialProfile.getBytes());
                try {
                    out.close();
                } catch (IOException e2) {
                    loge(e2.toString());
                }
            } catch (FileNotFoundException e) {
                loge("createPasspointCredendtial, FileNotFoundException");
                if (out != null) {
                    out.close();
                }
            } catch (IOException e3) {
                e3.printStackTrace();
                if (out != null) {
                    out.close();
                }
            } catch (Throwable th) {
                if (out != null) {
                    try {
                        out.close();
                    } catch (IOException e22) {
                        loge(e22.toString());
                    }
                }
                throw th;
            }
            return true;
        }
    }

    /* access modifiers changed from: protected */
    public void loge(String s) {
        Log.e(TAG, s);
        this.f53sb.append(s);
    }

    /* access modifiers changed from: protected */
    public void logd(String s) {
        Log.d(TAG, s);
        this.f53sb.append(s);
    }

    /* access modifiers changed from: protected */
    public void logi(String s) {
        Log.i(TAG, s);
        this.f53sb.append(s);
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("==== PasspointCscUtils Customer File Dump ====");
        pw.println(this.f53sb.toString());
    }
}
