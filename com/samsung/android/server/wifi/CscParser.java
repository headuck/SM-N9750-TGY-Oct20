package com.samsung.android.server.wifi;

import android.content.Context;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.util.Log;
import com.android.server.wifi.hotspot2.SystemInfo;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.StringTokenizer;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class CscParser {
    private static final String CSC_CHAMELEON_FILE = "/carrier/chameleon.xml";
    private static final String CSC_ID_FILE = "/system/SW_Configuration.xml";
    private static String CSC_OTHERS_FILE = "/system/csc/others.xml";
    private static String CSC_XML_FILE = "/system/csc/customer.xml";
    private static final String OMC_ID_FILE = "/system/omc/SW_Configuration.xml";
    private static final String SALES_CODE_PATH = "/efs/imei/mps_code.dat";
    private static final String TAG = "CscParser";
    private Document mDoc;
    private Node mRoot;

    public static class CscNodeList implements NodeList {
        private ArrayList<Node> children = new ArrayList<>();

        /* access modifiers changed from: package-private */
        public void appendChild(Node newChild) {
            this.children.add(newChild);
        }

        public int getLength() {
            return this.children.size();
        }

        public Node item(int index) {
            return this.children.get(index);
        }
    }

    public CscParser(Context context) {
    }

    public CscParser(String fileName) {
        try {
            update(fileName);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void update(String fileName) throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        if (new File(fileName).exists()) {
            Log.e(TAG, "update(): xml file exist");
            this.mDoc = builder.parse(new File(fileName));
            this.mRoot = this.mDoc.getDocumentElement();
            return;
        }
        Log.e(TAG, "update(): xml file not exist");
    }

    public String get(String path) {
        Node firstChild;
        Node node = search(path);
        if (node == null || (firstChild = node.getFirstChild()) == null) {
            return null;
        }
        return firstChild.getNodeValue();
    }

    public Node search(String path) {
        if (path == null) {
            return null;
        }
        Node node = this.mRoot;
        StringTokenizer tokenizer = new StringTokenizer(path, ".");
        while (tokenizer.hasMoreTokens()) {
            String token = tokenizer.nextToken();
            if (node == null) {
                return null;
            }
            node = search(node, token);
        }
        return node;
    }

    public Node search(Node parent, String name) {
        NodeList children;
        if (!(parent == null || (children = parent.getChildNodes()) == null)) {
            int n = children.getLength();
            for (int i = 0; i < n; i++) {
                Node child = children.item(i);
                if (child.getNodeName().equals(name)) {
                    return child;
                }
            }
        }
        return null;
    }

    public NodeList searchList(Node parent, String name) {
        if (parent == null) {
            return null;
        }
        try {
            CscNodeList list = new CscNodeList();
            NodeList children = parent.getChildNodes();
            if (children != null) {
                int n = children.getLength();
                for (int i = 0; i < n; i++) {
                    Node child = children.item(i);
                    if (child.getNodeName().equals(name)) {
                        try {
                            list.appendChild(child);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
            return list;
        } catch (Exception e2) {
            return null;
        }
    }

    public String getValue(Node node) {
        if (node == null) {
            return null;
        }
        if (node.getChildNodes().getLength() > 1) {
            String stringValue = new String();
            for (int idx = 0; idx < node.getChildNodes().getLength(); idx++) {
                stringValue = stringValue + node.getChildNodes().item(idx).getNodeValue();
            }
            return stringValue;
        }
        Node firstChild = node.getFirstChild();
        if (firstChild != null) {
            return firstChild.getNodeValue();
        }
        return null;
    }

    /* JADX INFO: Multiple debug info for r4v1 java.lang.String: [D('tagCount' int), D('tagAttr' java.lang.String)] */
    public String getAttrbute(String tagPath, int index, int mode) {
        String attribute = null;
        String[] tagSplit = tagPath.split("[.]");
        int tagCount = tagSplit.length;
        int tagCount2 = tagCount - 1;
        if (tagCount < 3) {
            return null;
        }
        int tagCount3 = tagCount2 - 1;
        String tagAttr = tagSplit[tagCount2];
        String tagList = tagSplit[tagCount3];
        String tagNode = null;
        for (int i = 0; i < tagCount3; i++) {
            tagNode = tagNode == null ? tagSplit[i] : tagNode + "." + tagSplit[i];
        }
        NodeList nodeList = searchList(search(tagNode), tagList);
        if (nodeList != null && nodeList.getLength() > index) {
            attribute = ((Element) nodeList.item(index)).getAttribute(tagAttr);
        }
        if (attribute != null && mode == 1) {
            String[] attrSlash = attribute.split("/");
            int cntSlash = attrSlash.length - 1;
            if (attrSlash[cntSlash] != null) {
                String[] attrSplit = attrSlash[cntSlash].split("[.]");
                if (attrSplit[0] != null) {
                    attribute = attrSplit[0];
                }
            }
        }
        Log.d(TAG, tagList + ": " + attribute);
        return attribute;
    }

    /* JADX WARNING: Removed duplicated region for block: B:39:0x00b1 A[ORIG_RETURN, RETURN, SYNTHETIC] */
    /* JADX WARNING: Removed duplicated region for block: B:49:? A[RETURN, SYNTHETIC] */
    public static String getSalesCode() {
        StringBuilder sb;
        String sales_code = null;
        FileReader fr = null;
        BufferedReader br = null;
        try {
            if (new File(SALES_CODE_PATH).exists()) {
                fr = new FileReader(SALES_CODE_PATH);
                br = new BufferedReader(fr);
                sales_code = br.readLine();
            } else {
                Log.e(TAG, "mps_code.dat does not exist");
            }
            if (fr != null) {
                try {
                    fr.close();
                } catch (IOException e) {
                    iex = e;
                    sb = new StringBuilder();
                    sb.append("IOException : ");
                    sb.append(iex.getMessage());
                    Log.e(TAG, sb.toString());
                    if (!TextUtils.isEmpty(sales_code)) {
                    }
                }
            }
            if (br != null) {
                br.close();
            }
        } catch (FileNotFoundException e2) {
            Log.e(TAG, "File not Found exception: " + e2.getMessage());
            if (0 != 0) {
                try {
                    fr.close();
                } catch (IOException e3) {
                    iex = e3;
                    sb = new StringBuilder();
                    sb.append("IOException : ");
                    sb.append(iex.getMessage());
                    Log.e(TAG, sb.toString());
                    if (!TextUtils.isEmpty(sales_code)) {
                    }
                }
            }
            if (0 != 0) {
                br.close();
            }
        } catch (IOException e4) {
            Log.e(TAG, "IOException : " + e4.getMessage());
            if (0 != 0) {
                try {
                    fr.close();
                } catch (IOException e5) {
                    iex = e5;
                    sb = new StringBuilder();
                    sb.append("IOException : ");
                    sb.append(iex.getMessage());
                    Log.e(TAG, sb.toString());
                    if (!TextUtils.isEmpty(sales_code)) {
                    }
                }
            }
            if (0 != 0) {
                br.close();
            }
        } catch (Throwable th) {
            if (0 != 0) {
                try {
                    fr.close();
                } catch (IOException iex) {
                    Log.e(TAG, "IOException : " + iex.getMessage());
                    throw th;
                }
            }
            if (0 != 0) {
                br.close();
            }
            throw th;
        }
        if (!TextUtils.isEmpty(sales_code)) {
            return "none";
        }
        return sales_code;
    }

    public static String getCustomerPath() {
        String omc_path = SystemProperties.get("persist.sys.omc_path", SystemInfo.UNKNOWN_INFO);
        if (!SystemInfo.UNKNOWN_INFO.equals(omc_path)) {
            File file = new File(omc_path + "/customer.xml");
            if (file.exists()) {
                if (file.canRead()) {
                    Log.i(TAG, "getCustomerPath : omc customer file can read");
                    return omc_path + "/customer.xml";
                }
                Log.e(TAG, "getCustomerPath : omc customer file exist but can't read");
                return CSC_XML_FILE;
            }
        }
        Log.e(TAG, "getCustomerPath : /system/csc/customer.xml file exist");
        return CSC_XML_FILE;
    }

    public static String getOthersPath() {
        String omc_path = SystemProperties.get("persist.sys.omc_path", SystemInfo.UNKNOWN_INFO);
        if (!SystemInfo.UNKNOWN_INFO.equals(omc_path)) {
            File file = new File(omc_path + "/others.xml");
            if (file.exists()) {
                if (file.canRead()) {
                    Log.i(TAG, "getOthersPath : omc others file can read");
                    return omc_path + "/others.xml";
                }
                Log.e(TAG, "getOthersPath : omc others file exist but can't read");
                return CSC_OTHERS_FILE;
            }
        }
        Log.e(TAG, "getOthersPath : /system/csc/others.xml file exist");
        return CSC_OTHERS_FILE;
    }

    public static String getSWConfigPath() {
        File file = new File(OMC_ID_FILE);
        if (!file.exists()) {
            Log.e(TAG, "getSWConfigPath : customer SW_Configuration file exist");
            return CSC_ID_FILE;
        } else if (file.canRead()) {
            Log.i(TAG, "getSWConfigPath : omc SW_Configuration file can read");
            return OMC_ID_FILE;
        } else {
            Log.e(TAG, "getSWConfigPath : omc SW_Configuration file exist but can't read");
            return CSC_ID_FILE;
        }
    }

    public static String getOrgCustomerPath() {
        return "/system/csc/customer.xml";
    }

    public static String getOrgOthersPath() {
        return "/system/csc/others.xml";
    }

    public static String getChameleonPath() {
        return CSC_CHAMELEON_FILE;
    }

    public static String getIDPath() {
        return CSC_ID_FILE;
    }

    public static String getOmcIDPath() {
        return OMC_ID_FILE;
    }
}
