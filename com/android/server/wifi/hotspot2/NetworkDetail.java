package com.android.server.wifi.hotspot2;

import android.net.wifi.ScanResult;
import android.util.Log;
import com.android.server.wifi.hotspot2.anqp.ANQPElement;
import com.android.server.wifi.hotspot2.anqp.Constants;
import com.android.server.wifi.hotspot2.anqp.RawByteElement;
import com.android.server.wifi.util.InformationElementUtil;
import com.android.server.wifi.util.NativeUtil;
import com.samsung.android.net.wifi.OpBrandingLoader;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class NetworkDetail {
    private static final String CHARSET_CN = "gbk";
    private static final String CHARSET_KOR = "ksc5601";
    private static final String CONFIG_CHARSET = OpBrandingLoader.getInstance().getSupportCharacterSet();
    private static final boolean DBG = false;
    private static final String TAG = "NetworkDetail:";
    private static final Map<String, NonUTF8Ssid> mNonUTF8SsidLists = new HashMap();
    private final Map<Constants.ANQPElementType, ANQPElement> mANQPElements;
    private final int mAnqpDomainID;
    private final int mAnqpOICount;
    private final Ant mAnt;
    private final long mBSSID;
    private final int mCapacity;
    private final int mCenterfreq0;
    private final int mCenterfreq1;
    private final int mChannelUtilization;
    private final int mChannelWidth;
    private final Set<Integer> mChipsetOuis;
    private int mDtimInterval = -1;
    private final int[] mEstimatedAirTimeFractions;
    private final InformationElementUtil.ExtendedCapabilities mExtendedCapabilities;
    private final long mHESSID;
    private final HSRelease mHSRelease;
    private final boolean mInternet;
    private final boolean mIsHiddenSsid;
    private final int mMaxRate;
    private final int mMboAssociationDisallowedReasonCode;
    private final int mPrimaryFreq;
    private final long[] mRoamingConsortiums;
    private final String mSSID;
    private final int mStationCount;
    private final int mWifiMode;
    private final byte[] semKtVsData;
    private final byte semKtVsOuiType;
    private final byte[] semVsData;
    private final byte semVsOuiType;

    public enum Ant {
        Private,
        PrivateWithGuest,
        ChargeablePublic,
        FreePublic,
        Personal,
        EmergencyOnly,
        Resvd6,
        Resvd7,
        Resvd8,
        Resvd9,
        Resvd10,
        Resvd11,
        Resvd12,
        Resvd13,
        TestOrExperimental,
        Wildcard
    }

    public enum HSRelease {
        R1,
        R2,
        Unknown
    }

    /* JADX INFO: Multiple debug info for r2v27 'extension'  com.android.server.wifi.util.InformationElementUtil$Extension: [D('extension' com.android.server.wifi.util.InformationElementUtil$Extension), D('supportedRates' com.android.server.wifi.util.InformationElementUtil$SupportedRates)] */
    /* JADX INFO: Multiple debug info for r2v30 int: [D('length' int), D('supportedRates' com.android.server.wifi.util.InformationElementUtil$SupportedRates)] */
    /* JADX INFO: Multiple debug info for r2v42 'supportedRates'  com.android.server.wifi.util.InformationElementUtil$SupportedRates: [D('iesFound' java.util.ArrayList<java.lang.Integer>), D('supportedRates' com.android.server.wifi.util.InformationElementUtil$SupportedRates)] */
    /* JADX INFO: Multiple debug info for r6v14 'extendedSupportedRates'  com.android.server.wifi.util.InformationElementUtil$SupportedRates: [D('isHiddenSsid' boolean), D('extendedSupportedRates' com.android.server.wifi.util.InformationElementUtil$SupportedRates)] */
    /* JADX WARNING: Removed duplicated region for block: B:112:0x02b8  */
    /* JADX WARNING: Removed duplicated region for block: B:116:0x02c1  */
    /* JADX WARNING: Removed duplicated region for block: B:119:0x031c  */
    /* JADX WARNING: Removed duplicated region for block: B:120:0x0331  */
    /* JADX WARNING: Removed duplicated region for block: B:123:0x034a  */
    /* JADX WARNING: Removed duplicated region for block: B:126:0x0356  */
    /* JADX WARNING: Removed duplicated region for block: B:127:0x036f  */
    /* JADX WARNING: Removed duplicated region for block: B:130:0x0377  */
    /* JADX WARNING: Removed duplicated region for block: B:139:0x03d6  */
    /* JADX WARNING: Removed duplicated region for block: B:142:0x03e7  */
    /* JADX WARNING: Removed duplicated region for block: B:150:0x040c  */
    /* JADX WARNING: Removed duplicated region for block: B:153:0x0416  */
    /* JADX WARNING: Removed duplicated region for block: B:171:0x02cb A[EDGE_INSN: B:171:0x02cb->B:117:0x02cb ?: BREAK  , SYNTHETIC] */
    /* JADX WARNING: Removed duplicated region for block: B:72:0x01f7  */
    /* JADX WARNING: Removed duplicated region for block: B:74:0x01fd  */
    public NetworkDetail(String bssid, ScanResult.InformationElement[] infoElements, List<String> list, int freq) {
        String ssid;
        boolean isHiddenSsid;
        ArrayList<Integer> iesFound;
        InformationElementUtil.Extension extension;
        InformationElementUtil.SupportedRates extendedSupportedRates;
        byte[] ssidOctets;
        InformationElementUtil.SupportedRates supportedRates;
        InformationElementUtil.VhtOperation vhtOperation;
        InformationElementUtil.HtOperation htOperation;
        InformationElementUtil.SupportedRates supportedRates2;
        String ssid2;
        boolean isHiddenSsid2;
        InformationElementUtil.Extension extension2;
        InformationElementUtil.Extension extension3;
        int i;
        String ssid3;
        String ssid4;
        int length;
        int i2;
        String ucnvSsid;
        byte[] ssidOctets2;
        InformationElementUtil.Extension extension4;
        ScanResult.InformationElement ie;
        ScanResult.InformationElement[] informationElementArr = infoElements;
        if (informationElementArr != null) {
            this.mBSSID = Utils.parseMac(bssid);
            String ssid5 = null;
            boolean isHiddenSsid3 = false;
            InformationElementUtil.BssLoad bssLoad = new InformationElementUtil.BssLoad();
            InformationElementUtil.Interworking interworking = new InformationElementUtil.Interworking();
            InformationElementUtil.RoamingConsortium roamingConsortium = new InformationElementUtil.RoamingConsortium();
            InformationElementUtil.Vsa vsa = new InformationElementUtil.Vsa();
            InformationElementUtil.HtOperation htOperation2 = new InformationElementUtil.HtOperation();
            InformationElementUtil.VhtOperation vhtOperation2 = new InformationElementUtil.VhtOperation();
            InformationElementUtil.ExtendedCapabilities extendedCapabilities = new InformationElementUtil.ExtendedCapabilities();
            InformationElementUtil.TrafficIndicationMap trafficIndicationMap = new InformationElementUtil.TrafficIndicationMap();
            InformationElementUtil.SupportedRates supportedRates3 = new InformationElementUtil.SupportedRates();
            InformationElementUtil.SupportedRates extendedSupportedRates2 = new InformationElementUtil.SupportedRates();
            InformationElementUtil.Extension extension5 = new InformationElementUtil.Extension();
            RuntimeException exception = null;
            ArrayList<Integer> iesFound2 = new ArrayList<>();
            try {
                int length2 = informationElementArr.length;
                ssidOctets2 = null;
                int i3 = 0;
                while (i3 < length2) {
                    try {
                        ie = informationElementArr[i3];
                    } catch (ArrayIndexOutOfBoundsException | IllegalArgumentException | BufferUnderflowException e) {
                        e = e;
                        supportedRates = supportedRates3;
                        ssid = ssid5;
                        extension4 = extension5;
                        isHiddenSsid = isHiddenSsid3;
                        extendedSupportedRates = extendedSupportedRates2;
                        iesFound = iesFound2;
                        String hs2LogTag = Utils.hs2LogTag(getClass());
                        StringBuilder sb = new StringBuilder();
                        extension = extension4;
                        sb.append("Caught ");
                        sb.append(e);
                        Log.d(hs2LogTag, sb.toString());
                        if (ssidOctets2 == null) {
                        }
                    }
                    try {
                        iesFound2.add(Integer.valueOf(ie.id));
                        int i4 = ie.id;
                        if (i4 != 0) {
                            ssid = ssid5;
                            if (i4 == 1) {
                                extension4 = extension5;
                                isHiddenSsid = isHiddenSsid3;
                                extendedSupportedRates = extendedSupportedRates2;
                                iesFound = iesFound2;
                                supportedRates = supportedRates3;
                                try {
                                    supportedRates.from(ie);
                                } catch (ArrayIndexOutOfBoundsException | IllegalArgumentException | BufferUnderflowException e2) {
                                    e = e2;
                                    String hs2LogTag2 = Utils.hs2LogTag(getClass());
                                    StringBuilder sb2 = new StringBuilder();
                                    extension = extension4;
                                    sb2.append("Caught ");
                                    sb2.append(e);
                                    Log.d(hs2LogTag2, sb2.toString());
                                    if (ssidOctets2 == null) {
                                    }
                                }
                            } else if (i4 == 5) {
                                extension4 = extension5;
                                isHiddenSsid = isHiddenSsid3;
                                extendedSupportedRates = extendedSupportedRates2;
                                trafficIndicationMap.from(ie);
                                iesFound = iesFound2;
                                supportedRates = supportedRates3;
                            } else if (i4 == 11) {
                                extension4 = extension5;
                                isHiddenSsid = isHiddenSsid3;
                                extendedSupportedRates = extendedSupportedRates2;
                                bssLoad.from(ie);
                                iesFound = iesFound2;
                                supportedRates = supportedRates3;
                            } else if (i4 == 50) {
                                extension4 = extension5;
                                isHiddenSsid = isHiddenSsid3;
                                extendedSupportedRates = extendedSupportedRates2;
                                try {
                                    extendedSupportedRates.from(ie);
                                    iesFound = iesFound2;
                                    supportedRates = supportedRates3;
                                } catch (ArrayIndexOutOfBoundsException | IllegalArgumentException | BufferUnderflowException e3) {
                                    e = e3;
                                    iesFound = iesFound2;
                                    supportedRates = supportedRates3;
                                    String hs2LogTag22 = Utils.hs2LogTag(getClass());
                                    StringBuilder sb22 = new StringBuilder();
                                    extension = extension4;
                                    sb22.append("Caught ");
                                    sb22.append(e);
                                    Log.d(hs2LogTag22, sb22.toString());
                                    if (ssidOctets2 == null) {
                                    }
                                }
                            } else if (i4 == 61) {
                                extension4 = extension5;
                                htOperation2.from(ie);
                                isHiddenSsid = isHiddenSsid3;
                                extendedSupportedRates = extendedSupportedRates2;
                                iesFound = iesFound2;
                                supportedRates = supportedRates3;
                            } else if (i4 == 107) {
                                extension4 = extension5;
                                interworking.from(ie);
                                isHiddenSsid = isHiddenSsid3;
                                extendedSupportedRates = extendedSupportedRates2;
                                iesFound = iesFound2;
                                supportedRates = supportedRates3;
                            } else if (i4 == 111) {
                                extension4 = extension5;
                                roamingConsortium.from(ie);
                                isHiddenSsid = isHiddenSsid3;
                                extendedSupportedRates = extendedSupportedRates2;
                                iesFound = iesFound2;
                                supportedRates = supportedRates3;
                            } else if (i4 == 127) {
                                extension4 = extension5;
                                extendedCapabilities.from(ie);
                                isHiddenSsid = isHiddenSsid3;
                                extendedSupportedRates = extendedSupportedRates2;
                                iesFound = iesFound2;
                                supportedRates = supportedRates3;
                            } else if (i4 == 192) {
                                extension4 = extension5;
                                vhtOperation2.from(ie);
                                isHiddenSsid = isHiddenSsid3;
                                extendedSupportedRates = extendedSupportedRates2;
                                iesFound = iesFound2;
                                supportedRates = supportedRates3;
                            } else if (i4 == 221) {
                                extension4 = extension5;
                                vsa.from(ie);
                                isHiddenSsid = isHiddenSsid3;
                                extendedSupportedRates = extendedSupportedRates2;
                                iesFound = iesFound2;
                                supportedRates = supportedRates3;
                            } else if (i4 != 255) {
                                extension4 = extension5;
                                isHiddenSsid = isHiddenSsid3;
                                extendedSupportedRates = extendedSupportedRates2;
                                iesFound = iesFound2;
                                supportedRates = supportedRates3;
                            } else {
                                extension4 = extension5;
                                try {
                                    extension4.from(ie);
                                    isHiddenSsid = isHiddenSsid3;
                                    extendedSupportedRates = extendedSupportedRates2;
                                    iesFound = iesFound2;
                                    supportedRates = supportedRates3;
                                } catch (ArrayIndexOutOfBoundsException | IllegalArgumentException | BufferUnderflowException e4) {
                                    e = e4;
                                    isHiddenSsid = isHiddenSsid3;
                                    extendedSupportedRates = extendedSupportedRates2;
                                    iesFound = iesFound2;
                                    supportedRates = supportedRates3;
                                    String hs2LogTag222 = Utils.hs2LogTag(getClass());
                                    StringBuilder sb222 = new StringBuilder();
                                    extension = extension4;
                                    sb222.append("Caught ");
                                    sb222.append(e);
                                    Log.d(hs2LogTag222, sb222.toString());
                                    if (ssidOctets2 == null) {
                                    }
                                }
                            }
                        } else {
                            ssid = ssid5;
                            extension4 = extension5;
                            isHiddenSsid = isHiddenSsid3;
                            extendedSupportedRates = extendedSupportedRates2;
                            iesFound = iesFound2;
                            supportedRates = supportedRates3;
                            ssidOctets2 = ie.bytes;
                        }
                        i3++;
                        supportedRates3 = supportedRates;
                        length2 = length2;
                        informationElementArr = infoElements;
                        extension5 = extension4;
                        ssid5 = ssid;
                        iesFound2 = iesFound;
                        extendedSupportedRates2 = extendedSupportedRates;
                        isHiddenSsid3 = isHiddenSsid;
                    } catch (ArrayIndexOutOfBoundsException | IllegalArgumentException | BufferUnderflowException e5) {
                        e = e5;
                        ssid = ssid5;
                        extension4 = extension5;
                        isHiddenSsid = isHiddenSsid3;
                        extendedSupportedRates = extendedSupportedRates2;
                        iesFound = iesFound2;
                        supportedRates = supportedRates3;
                        String hs2LogTag2222 = Utils.hs2LogTag(getClass());
                        StringBuilder sb2222 = new StringBuilder();
                        extension = extension4;
                        sb2222.append("Caught ");
                        sb2222.append(e);
                        Log.d(hs2LogTag2222, sb2222.toString());
                        if (ssidOctets2 == null) {
                            exception = e;
                            ssidOctets = ssidOctets2;
                            if (ssidOctets == null) {
                            }
                            this.mSSID = ssid2;
                            this.mHESSID = interworking.hessid;
                            this.mIsHiddenSsid = isHiddenSsid2;
                            this.mStationCount = bssLoad.stationCount;
                            this.mChannelUtilization = bssLoad.channelUtilization;
                            this.mCapacity = bssLoad.capacity;
                            this.mAnt = interworking.ant;
                            this.mInternet = interworking.internet;
                            this.mHSRelease = vsa.hsRelease;
                            this.mAnqpDomainID = vsa.anqpDomainID;
                            this.semVsOuiType = vsa.semVsOuiType;
                            this.semVsData = vsa.semVsData;
                            this.semKtVsOuiType = vsa.semKtVsOuiType;
                            this.semKtVsData = vsa.semKtVsData;
                            this.mChipsetOuis = vsa.chipsetOuis;
                            this.mAnqpOICount = roamingConsortium.anqpOICount;
                            this.mRoamingConsortiums = roamingConsortium.getRoamingConsortiums();
                            this.mExtendedCapabilities = extendedCapabilities;
                            this.mANQPElements = null;
                            this.mPrimaryFreq = freq;
                            if (!vhtOperation.isValid()) {
                            }
                            if (trafficIndicationMap.isValid()) {
                            }
                            int maxRateB = 0;
                            if (!extendedSupportedRates.isValid()) {
                            }
                            if (!supportedRates2.isValid()) {
                            }
                            if (extension2.esp == null) {
                            }
                            this.mMboAssociationDisallowedReasonCode = vsa.mboAssociationDisallowedReasonCode;
                            return;
                        }
                        throw new IllegalArgumentException("Malformed IE string (no SSID)", e);
                    }
                }
                supportedRates = supportedRates3;
                ssid = ssid5;
                isHiddenSsid = isHiddenSsid3;
                extendedSupportedRates = extendedSupportedRates2;
                iesFound = iesFound2;
                extension = extension5;
                ssidOctets = ssidOctets2;
            } catch (ArrayIndexOutOfBoundsException | IllegalArgumentException | BufferUnderflowException e6) {
                e = e6;
                supportedRates = supportedRates3;
                ssid = null;
                extension4 = extension5;
                isHiddenSsid = false;
                extendedSupportedRates = extendedSupportedRates2;
                iesFound = iesFound2;
                ssidOctets2 = null;
                String hs2LogTag22222 = Utils.hs2LogTag(getClass());
                StringBuilder sb22222 = new StringBuilder();
                extension = extension4;
                sb22222.append("Caught ");
                sb22222.append(e);
                Log.d(hs2LogTag22222, sb22222.toString());
                if (ssidOctets2 == null) {
                }
            }
            if (ssidOctets == null) {
                try {
                    ssid3 = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(ssidOctets)).toString();
                } catch (CharacterCodingException e7) {
                    ssid3 = null;
                }
                if (ssid3 != null) {
                    ssid4 = ssid3;
                } else if (!extendedCapabilities.isStrictUtf8() || exception == null) {
                    ssid4 = new String(ssidOctets, StandardCharsets.ISO_8859_1);
                } else {
                    throw new IllegalArgumentException("Failed to decode SSID in dubious IE string");
                }
                if (CHARSET_CN.equals(CONFIG_CHARSET) || CHARSET_KOR.equals(CONFIG_CHARSET)) {
                    supportedRates2 = supportedRates;
                    int length3 = ssidOctets.length;
                    htOperation = htOperation2;
                    vhtOperation = vhtOperation2;
                    if (!NativeUtil.isUTF8String(ssidOctets, (long) length3) && NativeUtil.isUCNVString(ssidOctets, length3)) {
                        try {
                            if (CHARSET_CN.equals(CONFIG_CHARSET)) {
                                ucnvSsid = new String(ssidOctets, CHARSET_CN);
                            } else {
                                ucnvSsid = new String(ssidOctets, CHARSET_KOR);
                            }
                            if (bssid != null) {
                                mNonUTF8SsidLists.put(bssid.toUpperCase(), new NonUTF8Ssid(ucnvSsid, ssidOctets));
                            } else {
                                Log.e(TAG, "none UTF-8 ssid detected but, bssid is null");
                            }
                            ssid2 = ucnvSsid;
                        } catch (Exception e8) {
                            Log.e(TAG, " Failed to decode UCNV e = " + e8.toString());
                        }
                        isHiddenSsid2 = true;
                        length = ssidOctets.length;
                        i2 = 0;
                        while (true) {
                            if (i2 >= length) {
                                break;
                            } else if (ssidOctets[i2] != 0) {
                                isHiddenSsid2 = false;
                                break;
                            } else {
                                i2++;
                            }
                        }
                    }
                } else {
                    supportedRates2 = supportedRates;
                    htOperation = htOperation2;
                    vhtOperation = vhtOperation2;
                }
                ssid2 = ssid4;
                isHiddenSsid2 = true;
                length = ssidOctets.length;
                i2 = 0;
                while (true) {
                    if (i2 >= length) {
                    }
                    i2++;
                }
            } else {
                supportedRates2 = supportedRates;
                htOperation = htOperation2;
                vhtOperation = vhtOperation2;
                isHiddenSsid2 = isHiddenSsid;
                ssid2 = ssid;
            }
            this.mSSID = ssid2;
            this.mHESSID = interworking.hessid;
            this.mIsHiddenSsid = isHiddenSsid2;
            this.mStationCount = bssLoad.stationCount;
            this.mChannelUtilization = bssLoad.channelUtilization;
            this.mCapacity = bssLoad.capacity;
            this.mAnt = interworking.ant;
            this.mInternet = interworking.internet;
            this.mHSRelease = vsa.hsRelease;
            this.mAnqpDomainID = vsa.anqpDomainID;
            this.semVsOuiType = vsa.semVsOuiType;
            this.semVsData = vsa.semVsData;
            this.semKtVsOuiType = vsa.semKtVsOuiType;
            this.semKtVsData = vsa.semKtVsData;
            this.mChipsetOuis = vsa.chipsetOuis;
            this.mAnqpOICount = roamingConsortium.anqpOICount;
            this.mRoamingConsortiums = roamingConsortium.getRoamingConsortiums();
            this.mExtendedCapabilities = extendedCapabilities;
            this.mANQPElements = null;
            this.mPrimaryFreq = freq;
            if (!vhtOperation.isValid()) {
                this.mChannelWidth = vhtOperation.getChannelWidth();
                this.mCenterfreq0 = vhtOperation.getCenterFreq0();
                this.mCenterfreq1 = vhtOperation.getCenterFreq1();
            } else {
                this.mChannelWidth = htOperation.getChannelWidth();
                this.mCenterfreq0 = htOperation.getCenterFreq0(this.mPrimaryFreq);
                this.mCenterfreq1 = 0;
            }
            if (trafficIndicationMap.isValid()) {
                this.mDtimInterval = trafficIndicationMap.mDtimPeriod;
            }
            int maxRateB2 = 0;
            if (!extendedSupportedRates.isValid()) {
                maxRateB2 = extendedSupportedRates.mRates.get(extendedSupportedRates.mRates.size() - 1).intValue();
            }
            if (!supportedRates2.isValid()) {
                int maxRateA = supportedRates2.mRates.get(supportedRates2.mRates.size() - 1).intValue();
                this.mMaxRate = maxRateA > maxRateB2 ? maxRateA : maxRateB2;
                extension2 = extension;
                this.mWifiMode = InformationElementUtil.WifiMode.determineMode(this.mPrimaryFreq, this.mMaxRate, extension2.f34ho != null ? extension2.f34ho.isValid() : false, vhtOperation.isValid(), iesFound.contains(61), iesFound.contains(42));
            } else {
                extension2 = extension;
                this.mWifiMode = 0;
                this.mMaxRate = 0;
            }
            if (extension2.esp == null) {
                this.mEstimatedAirTimeFractions = new int[4];
                int i5 = 0;
                for (int i6 = 4; i5 < i6; i6 = 4) {
                    InformationElementUtil.Extension.EstimatedServiceParameters.EspInformation espInformation = extension2.esp.espInformations[i5];
                    int[] iArr = this.mEstimatedAirTimeFractions;
                    if (espInformation == null) {
                        extension3 = extension2;
                        i = -1;
                    } else {
                        extension3 = extension2;
                        i = espInformation.estimatedAirTimeFraction;
                    }
                    iArr[i5] = i;
                    i5++;
                    extension2 = extension3;
                }
            } else {
                this.mEstimatedAirTimeFractions = null;
            }
            this.mMboAssociationDisallowedReasonCode = vsa.mboAssociationDisallowedReasonCode;
            return;
        }
        throw new IllegalArgumentException("Null information elements");
    }

    private static ByteBuffer getAndAdvancePayload(ByteBuffer data, int plLength) {
        ByteBuffer payload = data.duplicate().order(data.order());
        payload.limit(payload.position() + plLength);
        data.position(data.position() + plLength);
        return payload;
    }

    private NetworkDetail(NetworkDetail base, Map<Constants.ANQPElementType, ANQPElement> anqpElements) {
        this.mSSID = base.mSSID;
        this.mIsHiddenSsid = base.mIsHiddenSsid;
        this.mBSSID = base.mBSSID;
        this.mHESSID = base.mHESSID;
        this.mStationCount = base.mStationCount;
        this.mChannelUtilization = base.mChannelUtilization;
        this.mCapacity = base.mCapacity;
        this.mAnt = base.mAnt;
        this.mInternet = base.mInternet;
        this.mHSRelease = base.mHSRelease;
        this.mAnqpDomainID = base.mAnqpDomainID;
        this.semVsOuiType = base.semVsOuiType;
        this.semVsData = base.semVsData;
        this.semKtVsOuiType = base.semKtVsOuiType;
        this.semKtVsData = base.semKtVsData;
        this.mChipsetOuis = base.mChipsetOuis;
        this.mAnqpOICount = base.mAnqpOICount;
        this.mRoamingConsortiums = base.mRoamingConsortiums;
        this.mExtendedCapabilities = new InformationElementUtil.ExtendedCapabilities(base.mExtendedCapabilities);
        this.mANQPElements = anqpElements;
        this.mChannelWidth = base.mChannelWidth;
        this.mPrimaryFreq = base.mPrimaryFreq;
        this.mCenterfreq0 = base.mCenterfreq0;
        this.mCenterfreq1 = base.mCenterfreq1;
        this.mDtimInterval = base.mDtimInterval;
        this.mWifiMode = base.mWifiMode;
        this.mMaxRate = base.mMaxRate;
        this.mEstimatedAirTimeFractions = base.mEstimatedAirTimeFractions;
        this.mMboAssociationDisallowedReasonCode = base.mMboAssociationDisallowedReasonCode;
    }

    public NetworkDetail complete(Map<Constants.ANQPElementType, ANQPElement> anqpElements) {
        return new NetworkDetail(this, anqpElements);
    }

    public boolean queriable(List<Constants.ANQPElementType> queryElements) {
        return this.mAnt != null && (Constants.hasBaseANQPElements(queryElements) || (Constants.hasR2Elements(queryElements) && this.mHSRelease == HSRelease.R2));
    }

    public boolean has80211uInfo() {
        return (this.mAnt == null && this.mRoamingConsortiums == null && this.mHSRelease == null) ? false : true;
    }

    public boolean hasInterworking() {
        return this.mAnt != null;
    }

    public String getSSID() {
        return this.mSSID;
    }

    public String getTrimmedSSID() {
        if (this.mSSID == null) {
            return "";
        }
        for (int n = 0; n < this.mSSID.length(); n++) {
            if (this.mSSID.charAt(n) != 0) {
                return this.mSSID;
            }
        }
        return "";
    }

    public long getHESSID() {
        return this.mHESSID;
    }

    public long getBSSID() {
        return this.mBSSID;
    }

    public int getStationCount() {
        return this.mStationCount;
    }

    public int getChannelUtilization() {
        return this.mChannelUtilization;
    }

    public int getCapacity() {
        return this.mCapacity;
    }

    public boolean isInterworking() {
        return this.mAnt != null;
    }

    public Ant getAnt() {
        return this.mAnt;
    }

    public boolean isInternet() {
        return this.mInternet;
    }

    public HSRelease getHSRelease() {
        return this.mHSRelease;
    }

    public int getAnqpDomainID() {
        return this.mAnqpDomainID;
    }

    public byte semGetVsOuiType() {
        return this.semVsOuiType;
    }

    public byte[] semGetVsData() {
        return this.semVsData;
    }

    public byte semGetKtVsOuiType() {
        return this.semKtVsOuiType;
    }

    public byte[] semGetKtVsData() {
        return this.semKtVsData;
    }

    public Set<Integer> getChipsetOuis() {
        return this.mChipsetOuis;
    }

    public byte[] getOsuProviders() {
        ANQPElement osuProviders;
        Map<Constants.ANQPElementType, ANQPElement> map = this.mANQPElements;
        if (map == null || (osuProviders = map.get(Constants.ANQPElementType.HSOSUProviders)) == null) {
            return null;
        }
        return ((RawByteElement) osuProviders).getPayload();
    }

    public int getAnqpOICount() {
        return this.mAnqpOICount;
    }

    public long[] getRoamingConsortiums() {
        return this.mRoamingConsortiums;
    }

    public Map<Constants.ANQPElementType, ANQPElement> getANQPElements() {
        return this.mANQPElements;
    }

    public int getChannelWidth() {
        return this.mChannelWidth;
    }

    public int getCenterfreq0() {
        return this.mCenterfreq0;
    }

    public int getCenterfreq1() {
        return this.mCenterfreq1;
    }

    public int getWifiMode() {
        return this.mWifiMode;
    }

    public int getDtimInterval() {
        return this.mDtimInterval;
    }

    public boolean is80211McResponderSupport() {
        return this.mExtendedCapabilities.is80211McRTTResponder();
    }

    public boolean isSSID_UTF8() {
        return this.mExtendedCapabilities.isStrictUtf8();
    }

    public boolean equals(Object thatObject) {
        if (this == thatObject) {
            return true;
        }
        if (thatObject == null || getClass() != thatObject.getClass()) {
            return false;
        }
        NetworkDetail that = (NetworkDetail) thatObject;
        if (!getSSID().equals(that.getSSID()) || getBSSID() != that.getBSSID()) {
            return false;
        }
        return true;
    }

    public int hashCode() {
        long j = this.mBSSID;
        return (((this.mSSID.hashCode() * 31) + ((int) (j >>> 32))) * 31) + ((int) j);
    }

    public String toString() {
        return String.format("NetworkInfo{SSID='%s', HESSID=%x, BSSID=%x, StationCount=%d, ChannelUtilization=%d, Capacity=%d, Ant=%s, Internet=%s, HSRelease=%s, AnqpDomainID=%d, AnqpOICount=%d, RoamingConsortiums=%s}", this.mSSID, Long.valueOf(this.mHESSID), Long.valueOf(this.mBSSID), Integer.valueOf(this.mStationCount), Integer.valueOf(this.mChannelUtilization), Integer.valueOf(this.mCapacity), this.mAnt, Boolean.valueOf(this.mInternet), this.mHSRelease, Integer.valueOf(this.mAnqpDomainID), Integer.valueOf(this.mAnqpOICount), Utils.roamingConsortiumsToString(this.mRoamingConsortiums));
    }

    public String toKeyString() {
        if (this.mHESSID != 0) {
            return String.format("'%s':%012x (%012x)", this.mSSID, Long.valueOf(this.mBSSID), Long.valueOf(this.mHESSID));
        }
        return String.format("'%s':%012x", this.mSSID, Long.valueOf(this.mBSSID));
    }

    public String getBSSIDString() {
        return toMACString(this.mBSSID);
    }

    public boolean isBeaconFrame() {
        return this.mDtimInterval > 0;
    }

    public boolean isHiddenBeaconFrame() {
        return isBeaconFrame() && this.mIsHiddenSsid;
    }

    public static String toMACString(long mac) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (int n = 5; n >= 0; n--) {
            if (first) {
                first = false;
            } else {
                sb.append(':');
            }
            sb.append(String.format("%02x", Long.valueOf((mac >>> (n * 8)) & 255)));
        }
        return sb.toString();
    }

    public static Map<String, NonUTF8Ssid> getNonUTF8SsidLists() {
        return mNonUTF8SsidLists;
    }

    public static void clearNonUTF8SsidLists() {
        Map<String, NonUTF8Ssid> map = mNonUTF8SsidLists;
        if (map != null) {
            map.clear();
        }
    }

    public static String toOUIString(int oui) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (int n = 0; n < 3; n++) {
            if (first) {
                first = false;
            } else {
                sb.append(':');
            }
            sb.append(String.format("%02x", Integer.valueOf((oui >>> (n * 8)) & 255)));
        }
        return sb.toString();
    }

    public int getEstimatedAirTimeFraction(int accessCategory) {
        int[] iArr = this.mEstimatedAirTimeFractions;
        if (iArr == null) {
            return -1;
        }
        return iArr[accessCategory];
    }

    public int getMboAssociationDisallowedReasonCode() {
        return this.mMboAssociationDisallowedReasonCode;
    }

    public static class NonUTF8Ssid {
        public final String ssid;
        public final byte[] ssidOctets;

        public NonUTF8Ssid(String ssid2, byte[] ssidOctets2) {
            this.ssid = ssid2;
            this.ssidOctets = ssidOctets2;
        }
    }
}
