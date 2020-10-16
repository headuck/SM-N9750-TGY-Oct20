package com.samsung.android.server.wifi.softap;

import android.net.DhcpResults;
import android.net.LinkAddress;
import android.net.metrics.DhcpErrorEvent;
import android.net.shared.Inet4AddressUtils;
import android.os.Build;
import android.os.SystemProperties;
import android.system.OsConstants;
import android.text.TextUtils;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.wifi.hotspot2.anqp.Constants;
import java.io.UnsupportedEncodingException;
import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class DhcpPacket {
    protected static final byte CLIENT_ID_ETHER = 1;
    protected static final byte DHCP_BOOTREPLY = 2;
    protected static final byte DHCP_BOOTREQUEST = 1;
    protected static final byte DHCP_BROADCAST_ADDRESS = 28;
    static final short DHCP_CLIENT = 68;
    protected static final byte DHCP_CLIENT_IDENTIFIER = 61;
    protected static final byte DHCP_DNS_SERVER = 6;
    protected static final byte DHCP_DOMAIN_NAME = 15;
    protected static final byte DHCP_HOST_NAME = 12;
    protected static final byte DHCP_LEASE_TIME = 51;
    private static final int DHCP_MAGIC_COOKIE = 1669485411;
    protected static final byte DHCP_MAX_MESSAGE_SIZE = 57;
    protected static final byte DHCP_MESSAGE = 56;
    protected static final byte DHCP_MESSAGE_TYPE = 53;
    protected static final byte DHCP_MESSAGE_TYPE_ACK = 5;
    protected static final byte DHCP_MESSAGE_TYPE_DECLINE = 4;
    protected static final byte DHCP_MESSAGE_TYPE_DISCOVER = 1;
    protected static final byte DHCP_MESSAGE_TYPE_INFORM = 8;
    protected static final byte DHCP_MESSAGE_TYPE_NAK = 6;
    protected static final byte DHCP_MESSAGE_TYPE_OFFER = 2;
    protected static final byte DHCP_MESSAGE_TYPE_RELEASE = 7;
    protected static final byte DHCP_MESSAGE_TYPE_REQUEST = 3;
    protected static final byte DHCP_MTU = 26;
    protected static final byte DHCP_OPTION_END = -1;
    protected static final byte DHCP_OPTION_OVERLOAD = 52;
    protected static final byte DHCP_OPTION_PAD = 0;
    protected static final byte DHCP_PARAMETER_LIST = 55;
    protected static final byte DHCP_REBINDING_TIME = 59;
    protected static final byte DHCP_RENEWAL_TIME = 58;
    protected static final byte DHCP_REQUESTED_IP = 50;
    protected static final byte DHCP_ROUTER = 3;
    static final short DHCP_SERVER = 67;
    protected static final byte DHCP_SERVER_IDENTIFIER = 54;
    protected static final byte DHCP_SUBNET_MASK = 1;
    protected static final byte DHCP_VENDOR_CLASS_ID = 60;
    protected static final byte DHCP_VENDOR_INFO = 43;
    public static final int ENCAP_BOOTP = 2;
    public static final int ENCAP_L2 = 0;
    public static final int ENCAP_L3 = 1;
    public static final byte[] ETHER_BROADCAST = {DHCP_OPTION_END, DHCP_OPTION_END, DHCP_OPTION_END, DHCP_OPTION_END, DHCP_OPTION_END, DHCP_OPTION_END};
    public static final int HWADDR_LEN = 16;
    public static final Inet4Address INADDR_ANY = ((Inet4Address) Inet4Address.ANY);
    public static final Inet4Address INADDR_BROADCAST = ((Inet4Address) Inet4Address.ALL);
    public static final int INFINITE_LEASE = -1;
    private static final int IPV4_MIN_MTU = 68;
    private static final short IP_FLAGS_OFFSET = 16384;
    private static final byte IP_TOS_LOWDELAY = 16;
    private static final byte IP_TTL = 64;
    private static final byte IP_TYPE_UDP = 17;
    private static final byte IP_VERSION_HEADER_LEN = 69;
    protected static final int MAX_LENGTH = 1500;
    private static final int MAX_MTU = 1500;
    public static final int MAX_OPTION_LEN = 255;
    public static final int MINIMUM_LEASE = 60;
    private static final int MIN_MTU = 1280;
    public static final int MIN_PACKET_LENGTH_BOOTP = 236;
    public static final int MIN_PACKET_LENGTH_L2 = 278;
    public static final int MIN_PACKET_LENGTH_L3 = 264;
    private static final byte OPTION_OVERLOAD_BOTH = 3;
    private static final byte OPTION_OVERLOAD_FILE = 1;
    private static final byte OPTION_OVERLOAD_SNAME = 2;
    protected static final String TAG = "DhcpPacket";
    public static final String VENDOR_INFO_ANDROID_METERED = "ANDROID_METERED";
    static String testOverrideHostname = null;
    static String testOverrideVendorId = null;
    protected boolean mBroadcast;
    protected Inet4Address mBroadcastAddress;
    protected byte[] mClientId;
    protected final Inet4Address mClientIp;
    protected final byte[] mClientMac;
    protected List<Inet4Address> mDnsServers;
    protected String mDomainName;
    protected List<Inet4Address> mGateways;
    protected String mHostName;
    protected Integer mLeaseTime;
    protected Short mMaxMessageSize;
    protected String mMessage;
    protected Short mMtu;
    private final Inet4Address mNextIp;
    protected final Inet4Address mRelayIp;
    protected Inet4Address mRequestedIp;
    protected byte[] mRequestedParams;
    protected final short mSecs;
    protected String mServerHostName;
    protected Inet4Address mServerIdentifier;
    protected Inet4Address mSubnetMask;
    protected Integer mT1;
    protected Integer mT2;
    protected final int mTransId;
    protected String mVendorId;
    protected String mVendorInfo;
    protected final Inet4Address mYourIp;

    public abstract ByteBuffer buildPacket(int i, short s, short s2);

    /* access modifiers changed from: package-private */
    public abstract void finishPacket(ByteBuffer byteBuffer);

    protected DhcpPacket(int transId, short secs, Inet4Address clientIp, Inet4Address yourIp, Inet4Address nextIp, Inet4Address relayIp, byte[] clientMac, boolean broadcast) {
        this.mTransId = transId;
        this.mSecs = secs;
        this.mClientIp = clientIp;
        this.mYourIp = yourIp;
        this.mNextIp = nextIp;
        this.mRelayIp = relayIp;
        this.mClientMac = clientMac;
        this.mBroadcast = broadcast;
    }

    public int getTransactionId() {
        return this.mTransId;
    }

    public byte[] getClientMac() {
        return this.mClientMac;
    }

    public boolean hasExplicitClientId() {
        return this.mClientId != null;
    }

    public byte[] getExplicitClientIdOrNull() {
        if (hasExplicitClientId()) {
            return getClientId();
        }
        return null;
    }

    public byte[] getClientId() {
        if (hasExplicitClientId()) {
            byte[] bArr = this.mClientId;
            return Arrays.copyOf(bArr, bArr.length);
        }
        byte[] clientId = this.mClientMac;
        byte[] clientId2 = new byte[(clientId.length + 1)];
        clientId2[0] = 1;
        System.arraycopy(clientId, 0, clientId2, 1, clientId.length);
        return clientId2;
    }

    public boolean hasRequestedParam(byte paramId) {
        byte[] bArr = this.mRequestedParams;
        if (bArr == null) {
            return false;
        }
        for (byte reqParam : bArr) {
            if (reqParam == paramId) {
                return true;
            }
        }
        return false;
    }

    /* access modifiers changed from: protected */
    public void fillInPacket(int encap, Inet4Address destIp, Inet4Address srcIp, short destUdp, short srcUdp, ByteBuffer buf, byte requestCode, boolean broadcast) {
        byte[] destIpArray = destIp.getAddress();
        byte[] srcIpArray = srcIp.getAddress();
        int ipHeaderOffset = 0;
        int ipLengthOffset = 0;
        int ipChecksumOffset = 0;
        int endIpHeader = 0;
        int udpHeaderOffset = 0;
        int udpLengthOffset = 0;
        int udpChecksumOffset = 0;
        buf.clear();
        buf.order(ByteOrder.BIG_ENDIAN);
        if (encap == 0) {
            buf.put(ETHER_BROADCAST);
            buf.put(this.mClientMac);
            buf.putShort((short) OsConstants.ETH_P_IP);
        }
        if (encap <= 1) {
            ipHeaderOffset = buf.position();
            buf.put(IP_VERSION_HEADER_LEN);
            buf.put((byte) 16);
            ipLengthOffset = buf.position();
            buf.putShort(0);
            buf.putShort(0);
            buf.putShort(IP_FLAGS_OFFSET);
            buf.put((byte) 64);
            buf.put(IP_TYPE_UDP);
            ipChecksumOffset = buf.position();
            buf.putShort(0);
            buf.put(srcIpArray);
            buf.put(destIpArray);
            endIpHeader = buf.position();
            udpHeaderOffset = buf.position();
            buf.putShort(srcUdp);
            buf.putShort(destUdp);
            udpLengthOffset = buf.position();
            buf.putShort(0);
            udpChecksumOffset = buf.position();
            buf.putShort(0);
        }
        buf.put(requestCode);
        buf.put((byte) 1);
        buf.put((byte) this.mClientMac.length);
        buf.put((byte) 0);
        buf.putInt(this.mTransId);
        buf.putShort(this.mSecs);
        if (broadcast) {
            buf.putShort(Short.MIN_VALUE);
        } else {
            buf.putShort(0);
        }
        buf.put(this.mClientIp.getAddress());
        buf.put(this.mYourIp.getAddress());
        buf.put(this.mNextIp.getAddress());
        buf.put(this.mRelayIp.getAddress());
        buf.put(this.mClientMac);
        buf.position(buf.position() + (16 - this.mClientMac.length) + 64 + 128);
        buf.putInt(DHCP_MAGIC_COOKIE);
        finishPacket(buf);
        if ((buf.position() & 1) == 1) {
            buf.put((byte) 0);
        }
        if (encap <= 1) {
            short udpLen = (short) (buf.position() - udpHeaderOffset);
            buf.putShort(udpLengthOffset, udpLen);
            buf.putShort(udpChecksumOffset, (short) checksum(buf, 0 + intAbs(buf.getShort(ipChecksumOffset + 2)) + intAbs(buf.getShort(ipChecksumOffset + 4)) + intAbs(buf.getShort(ipChecksumOffset + 6)) + intAbs(buf.getShort(ipChecksumOffset + 8)) + 17 + udpLen, udpHeaderOffset, buf.position()));
            buf.putShort(ipLengthOffset, (short) (buf.position() - ipHeaderOffset));
            buf.putShort(ipChecksumOffset, (short) checksum(buf, 0, ipHeaderOffset, endIpHeader));
        }
    }

    private static int intAbs(short v) {
        return 65535 & v;
    }

    /* JADX INFO: Multiple debug info for r4v6 int: [D('sum' int), D('negated' int)] */
    private int checksum(ByteBuffer buf, int seed, int start, int end) {
        int sum = seed;
        int bufPosition = buf.position();
        buf.position(start);
        ShortBuffer shortBuf = buf.asShortBuffer();
        buf.position(bufPosition);
        short[] shortArray = new short[((end - start) / 2)];
        shortBuf.get(shortArray);
        for (short s : shortArray) {
            sum += intAbs(s);
        }
        int start2 = start + (shortArray.length * 2);
        if (end != start2) {
            short b = (short) buf.get(start2);
            if (b < 0) {
                b = (short) (b + 256);
            }
            sum += b * 256;
        }
        int sum2 = ((sum >> 16) & 65535) + (sum & 65535);
        return intAbs((short) (~((((sum2 >> 16) & 65535) + sum2) & 65535)));
    }

    protected static void addTlv(ByteBuffer buf, byte type, byte value) {
        buf.put(type);
        buf.put((byte) 1);
        buf.put(value);
    }

    protected static void addTlv(ByteBuffer buf, byte type, byte[] payload) {
        if (payload == null) {
            return;
        }
        if (payload.length <= 255) {
            buf.put(type);
            buf.put((byte) payload.length);
            buf.put(payload);
            return;
        }
        throw new IllegalArgumentException("DHCP option too long: " + payload.length + " vs. " + 255);
    }

    protected static void addTlv(ByteBuffer buf, byte type, Inet4Address addr) {
        if (addr != null) {
            addTlv(buf, type, addr.getAddress());
        }
    }

    protected static void addTlv(ByteBuffer buf, byte type, List<Inet4Address> addrs) {
        if (!(addrs == null || addrs.size() == 0)) {
            int optionLen = addrs.size() * 4;
            if (optionLen <= 255) {
                buf.put(type);
                buf.put((byte) optionLen);
                for (Inet4Address addr : addrs) {
                    buf.put(addr.getAddress());
                }
                return;
            }
            throw new IllegalArgumentException("DHCP option too long: " + optionLen + " vs. " + 255);
        }
    }

    protected static void addTlv(ByteBuffer buf, byte type, Short value) {
        if (value != null) {
            buf.put(type);
            buf.put((byte) 2);
            buf.putShort(value.shortValue());
        }
    }

    protected static void addTlv(ByteBuffer buf, byte type, Integer value) {
        if (value != null) {
            buf.put(type);
            buf.put((byte) 4);
            buf.putInt(value.intValue());
        }
    }

    protected static void addTlv(ByteBuffer buf, byte type, String str) {
        if (str != null) {
            try {
                addTlv(buf, type, str.getBytes("US-ASCII"));
            } catch (UnsupportedEncodingException e) {
                throw new IllegalArgumentException("String is not US-ASCII: " + str);
            }
        }
    }

    protected static void addTlvEnd(ByteBuffer buf) {
        buf.put(DHCP_OPTION_END);
    }

    private String getVendorId() {
        String str = testOverrideVendorId;
        if (str != null) {
            return str;
        }
        return "android-dhcp-" + Build.VERSION.RELEASE;
    }

    private String getHostname() {
        String str = testOverrideHostname;
        if (str != null) {
            return str;
        }
        return SystemProperties.get("net.hostname");
    }

    /* access modifiers changed from: protected */
    public void addCommonClientTlvs(ByteBuffer buf) {
        addTlv(buf, (byte) DHCP_MAX_MESSAGE_SIZE, (Short) 1500);
        addTlv(buf, (byte) DHCP_VENDOR_CLASS_ID, getVendorId());
        String hn = getHostname();
        if (!TextUtils.isEmpty(hn)) {
            addTlv(buf, (byte) DHCP_HOST_NAME, hn);
        }
    }

    /* access modifiers changed from: protected */
    public void addCommonServerTlvs(ByteBuffer buf) {
        addTlv(buf, (byte) DHCP_LEASE_TIME, this.mLeaseTime);
        Integer num = this.mLeaseTime;
        if (!(num == null || num.intValue() == -1)) {
            addTlv(buf, (byte) DHCP_RENEWAL_TIME, Integer.valueOf((int) (Integer.toUnsignedLong(this.mLeaseTime.intValue()) / 2)));
            addTlv(buf, (byte) DHCP_REBINDING_TIME, Integer.valueOf((int) ((Integer.toUnsignedLong(this.mLeaseTime.intValue()) * 875) / 1000)));
        }
        addTlv(buf, (byte) 1, this.mSubnetMask);
        addTlv(buf, (byte) DHCP_BROADCAST_ADDRESS, this.mBroadcastAddress);
        addTlv(buf, (byte) 3, this.mGateways);
        addTlv(buf, (byte) 6, this.mDnsServers);
        addTlv(buf, (byte) DHCP_DOMAIN_NAME, this.mDomainName);
        addTlv(buf, (byte) DHCP_HOST_NAME, this.mHostName);
        addTlv(buf, (byte) DHCP_VENDOR_INFO, this.mVendorInfo);
        Short sh = this.mMtu;
        if (sh != null && Short.toUnsignedInt(sh.shortValue()) >= 68) {
            addTlv(buf, (byte) DHCP_MTU, this.mMtu);
        }
    }

    public static String macToString(byte[] mac) {
        String macAddr = "";
        for (int i = 0; i < mac.length; i++) {
            String hexString = "0" + Integer.toHexString(mac[i]);
            macAddr = macAddr + hexString.substring(hexString.length() - 2);
            if (i != mac.length - 1) {
                macAddr = macAddr + ":";
            }
        }
        return macAddr;
    }

    public String toString() {
        return macToString(this.mClientMac);
    }

    private static Inet4Address readIpAddress(ByteBuffer packet) {
        byte[] ipAddr = new byte[4];
        packet.get(ipAddr);
        try {
            return (Inet4Address) Inet4Address.getByAddress(ipAddr);
        } catch (UnknownHostException e) {
            return null;
        }
    }

    private static String readAsciiString(ByteBuffer buf, int byteCount, boolean nullOk) {
        byte[] bytes = new byte[byteCount];
        buf.get(bytes);
        int length = bytes.length;
        if (!nullOk) {
            length = 0;
            while (length < bytes.length && bytes[length] != 0) {
                length++;
            }
        }
        return new String(bytes, 0, length, StandardCharsets.US_ASCII);
    }

    private static boolean isPacketToOrFromClient(short udpSrcPort, short udpDstPort) {
        return udpSrcPort == 68 || udpDstPort == 68;
    }

    private static boolean isPacketServerToServer(short udpSrcPort, short udpDstPort) {
        return udpSrcPort == 67 && udpDstPort == 67;
    }

    public static class ParseException extends Exception {
        public final int errorCode;

        public ParseException(int errorCode2, String msg, Object... args) {
            super(String.format(msg, args));
            this.errorCode = errorCode2;
        }
    }

    /* JADX INFO: Multiple debug info for r6v10 byte[]: [D('clientMac' byte[]), D('type' byte)] */
    /* JADX INFO: Multiple debug info for r13v51 byte[]: [D('i' int), D('id' byte[])] */
    /* JADX INFO: Multiple debug info for r7v36 byte[]: [D('netMask' java.net.Inet4Address), D('l2src' byte[])] */
    @VisibleForTesting
    static DhcpPacket decodeFullPacket(ByteBuffer packet, int pktType) throws ParseException {
        String message;
        Inet4Address netMask;
        int i;
        String vendorInfo;
        String vendorId;
        Inet4Address requestedIp;
        DhcpPacket newPacket;
        int expectedLen;
        List<Inet4Address> dnsServers = new ArrayList<>();
        List<Inet4Address> gateways = new ArrayList<>();
        Inet4Address ipDst = null;
        byte optionOverload = 0;
        byte dhcpType = DHCP_OPTION_END;
        Inet4Address ipSrc = null;
        packet.order(ByteOrder.BIG_ENDIAN);
        if (pktType != 0) {
            netMask = null;
            message = null;
            i = 1;
        } else if (packet.remaining() >= 278) {
            netMask = null;
            packet.get(new byte[6]);
            packet.get(new byte[6]);
            short l2type = packet.getShort();
            if (l2type == OsConstants.ETH_P_IP) {
                message = null;
                i = 1;
            } else {
                throw new ParseException(16908288, "Unexpected L2 type 0x%04x, expected 0x%04x", Short.valueOf(l2type), Integer.valueOf(OsConstants.ETH_P_IP));
            }
        } else {
            throw new ParseException(16842752, "L2 packet too short, %d < %d", Integer.valueOf(packet.remaining()), Integer.valueOf((int) MIN_PACKET_LENGTH_L2));
        }
        if (pktType > i) {
            vendorId = null;
            vendorInfo = null;
        } else if (packet.remaining() >= 264) {
            byte ipTypeAndLength = packet.get();
            int ipVersion = (ipTypeAndLength & 240) >> 4;
            if (ipVersion == 4) {
                packet.get();
                packet.getShort();
                packet.getShort();
                packet.get();
                packet.get();
                packet.get();
                byte ipProto = packet.get();
                packet.getShort();
                ipSrc = readIpAddress(packet);
                ipDst = readIpAddress(packet);
                if (ipProto == 17) {
                    int optionWords = (ipTypeAndLength & DHCP_DOMAIN_NAME) - 5;
                    for (int i2 = 0; i2 < optionWords; i2++) {
                        packet.getInt();
                    }
                    short udpSrcPort = packet.getShort();
                    short udpDstPort = packet.getShort();
                    packet.getShort();
                    packet.getShort();
                    if (isPacketToOrFromClient(udpSrcPort, udpDstPort)) {
                        vendorId = null;
                        vendorInfo = null;
                    } else if (isPacketServerToServer(udpSrcPort, udpDstPort)) {
                        vendorId = null;
                        vendorInfo = null;
                    } else {
                        throw new ParseException(50462720, "Unexpected UDP ports %d->%d", Short.valueOf(udpSrcPort), Short.valueOf(udpDstPort));
                    }
                } else {
                    throw new ParseException(50397184, "Protocol not UDP: %d", Byte.valueOf(ipProto));
                }
            } else {
                throw new ParseException(33685504, "Invalid IP version %d", Integer.valueOf(ipVersion));
            }
        } else {
            throw new ParseException(33619968, "L3 packet too short, %d < %d", Integer.valueOf(packet.remaining()), 264);
        }
        if (pktType > 2 || packet.remaining() < 236) {
            throw new ParseException(67174400, "Invalid type or BOOTP packet too short, %d < %d", Integer.valueOf(packet.remaining()), Integer.valueOf((int) MIN_PACKET_LENGTH_BOOTP));
        }
        packet.get();
        packet.get();
        int addrLen = packet.get() & DHCP_OPTION_END;
        packet.get();
        int transactionId = packet.getInt();
        short secs = packet.getShort();
        boolean broadcast = (packet.getShort() & 32768) != 0;
        byte[] ipv4addr = new byte[4];
        try {
            packet.get(ipv4addr);
            Inet4Address clientIp = (Inet4Address) Inet4Address.getByAddress(ipv4addr);
            packet.get(ipv4addr);
            Inet4Address yourIp = (Inet4Address) Inet4Address.getByAddress(ipv4addr);
            packet.get(ipv4addr);
            Inet4Address nextIp = (Inet4Address) Inet4Address.getByAddress(ipv4addr);
            packet.get(ipv4addr);
            Inet4Address relayIp = (Inet4Address) Inet4Address.getByAddress(ipv4addr);
            if (addrLen > 16) {
                addrLen = ETHER_BROADCAST.length;
            }
            byte[] clientMac = new byte[addrLen];
            packet.get(clientMac);
            packet.position(packet.position() + (16 - addrLen));
            String serverHostName = readAsciiString(packet, 64, false);
            packet.position(packet.position() + 128);
            if (packet.remaining() >= 4) {
                int dhcpMagicCookie = packet.getInt();
                if (dhcpMagicCookie == DHCP_MAGIC_COOKIE) {
                    String hostName = null;
                    String domainName = null;
                    Inet4Address bcAddr = null;
                    Inet4Address requestedIp2 = null;
                    Short mtu = null;
                    Short maxMessageSize = null;
                    Integer leaseTime = null;
                    Integer T1 = null;
                    Integer T2 = null;
                    Inet4Address serverIdentifier = null;
                    Inet4Address netMask2 = netMask;
                    String message2 = message;
                    String vendorId2 = vendorId;
                    String vendorInfo2 = vendorInfo;
                    byte[] expectedParams = null;
                    boolean notFinishedOptions = true;
                    while (true) {
                        requestedIp = requestedIp2;
                        if (packet.position() < packet.limit() && notFinishedOptions) {
                            byte optionType = packet.get();
                            if (optionType == -1) {
                                notFinishedOptions = false;
                            } else if (optionType == 0) {
                                continue;
                            } else {
                                try {
                                    int optionLen = packet.get() & DHCP_OPTION_END;
                                    int expectedLen2 = 0;
                                    if (optionType == 1) {
                                        netMask2 = readIpAddress(packet);
                                        expectedLen = 4;
                                        mtu = mtu;
                                    } else if (optionType == 3) {
                                        int expectedLen3 = 0;
                                        while (expectedLen < optionLen) {
                                            gateways.add(readIpAddress(packet));
                                            expectedLen3 = expectedLen + 4;
                                        }
                                        mtu = mtu;
                                    } else if (optionType == 6) {
                                        expectedLen = 0;
                                        while (expectedLen < optionLen) {
                                            dnsServers.add(readIpAddress(packet));
                                            expectedLen += 4;
                                        }
                                        mtu = mtu;
                                    } else if (optionType == 12) {
                                        hostName = readAsciiString(packet, optionLen, false);
                                        expectedLen = optionLen;
                                        mtu = mtu;
                                    } else if (optionType == 15) {
                                        domainName = readAsciiString(packet, optionLen, false);
                                        expectedLen = optionLen;
                                        mtu = mtu;
                                    } else if (optionType == 26) {
                                        mtu = Short.valueOf(packet.getShort());
                                        expectedLen = 2;
                                    } else if (optionType == 28) {
                                        bcAddr = readIpAddress(packet);
                                        expectedLen = 4;
                                        mtu = mtu;
                                    } else if (optionType != 43) {
                                        switch (optionType) {
                                            case 50:
                                                requestedIp = readIpAddress(packet);
                                                expectedLen = 4;
                                                mtu = mtu;
                                                break;
                                            case 51:
                                                leaseTime = Integer.valueOf(packet.getInt());
                                                expectedLen = 4;
                                                mtu = mtu;
                                                break;
                                            case 52:
                                                optionOverload = (byte) (packet.get() & 3);
                                                expectedLen = 1;
                                                mtu = mtu;
                                                break;
                                            case 53:
                                                dhcpType = packet.get();
                                                expectedLen = 1;
                                                mtu = mtu;
                                                break;
                                            case 54:
                                                serverIdentifier = readIpAddress(packet);
                                                expectedLen = 4;
                                                mtu = mtu;
                                                break;
                                            case 55:
                                                byte[] expectedParams2 = new byte[optionLen];
                                                try {
                                                    packet.get(expectedParams2);
                                                    expectedParams = expectedParams2;
                                                    expectedLen = optionLen;
                                                    mtu = mtu;
                                                    break;
                                                } catch (BufferUnderflowException e) {
                                                    e = e;
                                                    throw new ParseException(DhcpErrorEvent.errorCodeWithOption(83951616, optionType), "BufferUnderflowException", new Object[0]);
                                                }
                                            case 56:
                                                message2 = readAsciiString(packet, optionLen, false);
                                                expectedLen = optionLen;
                                                mtu = mtu;
                                                break;
                                            case 57:
                                                maxMessageSize = Short.valueOf(packet.getShort());
                                                expectedLen = 2;
                                                mtu = mtu;
                                                break;
                                            case 58:
                                                T1 = Integer.valueOf(packet.getInt());
                                                expectedLen = 4;
                                                mtu = mtu;
                                                break;
                                            case 59:
                                                T2 = Integer.valueOf(packet.getInt());
                                                expectedLen = 4;
                                                mtu = mtu;
                                                break;
                                            case 60:
                                                vendorId2 = readAsciiString(packet, optionLen, true);
                                                expectedLen = optionLen;
                                                mtu = mtu;
                                                break;
                                            case 61:
                                                packet.get(new byte[optionLen]);
                                                expectedLen = optionLen;
                                                mtu = mtu;
                                                break;
                                            default:
                                                for (int i3 = 0; i3 < optionLen; i3++) {
                                                    expectedLen2++;
                                                    try {
                                                        packet.get();
                                                    } catch (BufferUnderflowException e2) {
                                                        e = e2;
                                                        throw new ParseException(DhcpErrorEvent.errorCodeWithOption(83951616, optionType), "BufferUnderflowException", new Object[0]);
                                                    }
                                                }
                                                expectedLen = expectedLen2;
                                                mtu = mtu;
                                                break;
                                        }
                                    } else {
                                        vendorInfo2 = readAsciiString(packet, optionLen, true);
                                        expectedLen = optionLen;
                                        mtu = mtu;
                                    }
                                    if (expectedLen == optionLen) {
                                        notFinishedOptions = notFinishedOptions;
                                    } else {
                                        try {
                                            try {
                                            } catch (BufferUnderflowException e3) {
                                                e = e3;
                                                throw new ParseException(DhcpErrorEvent.errorCodeWithOption(83951616, optionType), "BufferUnderflowException", new Object[0]);
                                            }
                                        } catch (BufferUnderflowException e4) {
                                            e = e4;
                                            throw new ParseException(DhcpErrorEvent.errorCodeWithOption(83951616, optionType), "BufferUnderflowException", new Object[0]);
                                        }
                                        try {
                                            throw new ParseException(DhcpErrorEvent.errorCodeWithOption(67305472, optionType), "Invalid length %d for option %d, expected %d", Integer.valueOf(optionLen), Byte.valueOf(optionType), Integer.valueOf(expectedLen));
                                        } catch (BufferUnderflowException e5) {
                                            e = e5;
                                            throw new ParseException(DhcpErrorEvent.errorCodeWithOption(83951616, optionType), "BufferUnderflowException", new Object[0]);
                                        }
                                    }
                                } catch (BufferUnderflowException e6) {
                                    e = e6;
                                    throw new ParseException(DhcpErrorEvent.errorCodeWithOption(83951616, optionType), "BufferUnderflowException", new Object[0]);
                                }
                            }
                            requestedIp2 = requestedIp;
                        }
                    }
                    switch (dhcpType) {
                        case -1:
                            throw new ParseException(67371008, "No DHCP message type option", new Object[0]);
                        case 0:
                        default:
                            throw new ParseException(67436544, "Unimplemented DHCP type %d", Byte.valueOf(dhcpType));
                        case 1:
                            newPacket = new DhcpDiscoverPacket(transactionId, secs, relayIp, clientMac, broadcast, ipSrc);
                            break;
                        case 2:
                            newPacket = new DhcpOfferPacket(transactionId, secs, broadcast, ipSrc, relayIp, clientIp, yourIp, clientMac);
                            break;
                        case 3:
                            newPacket = new DhcpRequestPacket(transactionId, secs, clientIp, relayIp, clientMac, broadcast);
                            break;
                        case 4:
                            newPacket = new DhcpDeclinePacket(transactionId, secs, clientIp, yourIp, nextIp, relayIp, clientMac);
                            break;
                        case 5:
                            newPacket = new DhcpAckPacket(transactionId, secs, broadcast, ipSrc, relayIp, clientIp, yourIp, clientMac);
                            break;
                        case 6:
                            newPacket = new DhcpNakPacket(transactionId, secs, relayIp, clientMac, broadcast);
                            break;
                        case 7:
                            if (serverIdentifier != null) {
                                newPacket = new DhcpReleasePacket(transactionId, serverIdentifier, clientIp, relayIp, clientMac);
                                break;
                            } else {
                                throw new ParseException(5, "DHCPRELEASE without server identifier", new Object[0]);
                            }
                        case 8:
                            newPacket = new DhcpInformPacket(transactionId, secs, clientIp, yourIp, nextIp, relayIp, clientMac);
                            break;
                    }
                    newPacket.mBroadcastAddress = bcAddr;
                    newPacket.mClientId = null;
                    newPacket.mDnsServers = dnsServers;
                    newPacket.mDomainName = domainName;
                    newPacket.mGateways = gateways;
                    newPacket.mHostName = hostName;
                    newPacket.mLeaseTime = leaseTime;
                    newPacket.mMessage = message2;
                    newPacket.mMtu = mtu;
                    newPacket.mRequestedIp = requestedIp;
                    newPacket.mRequestedParams = expectedParams;
                    newPacket.mServerIdentifier = serverIdentifier;
                    newPacket.mSubnetMask = netMask2;
                    newPacket.mMaxMessageSize = maxMessageSize;
                    newPacket.mT1 = T1;
                    newPacket.mT2 = T2;
                    newPacket.mVendorId = vendorId2;
                    newPacket.mVendorInfo = vendorInfo2;
                    if ((optionOverload & 2) == 0) {
                        newPacket.mServerHostName = serverHostName;
                    } else {
                        newPacket.mServerHostName = "";
                    }
                    return newPacket;
                }
                throw new ParseException(67239936, "Bad magic cookie 0x%08x, should be 0x%08x", Integer.valueOf(dhcpMagicCookie), Integer.valueOf((int) DHCP_MAGIC_COOKIE));
            }
            throw new ParseException(67502080, "not a DHCP message", new Object[0]);
        } catch (UnknownHostException e7) {
            throw new ParseException(33751040, "Invalid IPv4 address: %s", Arrays.toString(ipv4addr));
        }
    }

    public static DhcpPacket decodeFullPacket(byte[] packet, int length, int pktType) throws ParseException {
        try {
            return decodeFullPacket(ByteBuffer.wrap(packet, 0, length).order(ByteOrder.BIG_ENDIAN), pktType);
        } catch (ParseException e) {
            throw e;
        } catch (Exception e2) {
            throw new ParseException(84082688, e2.getMessage(), new Object[0]);
        }
    }

    public DhcpResults toDhcpResults() {
        int prefixLength;
        Inet4Address ipAddress = this.mYourIp;
        if (ipAddress.equals((Inet4Address) Inet4Address.ANY)) {
            ipAddress = this.mClientIp;
            if (ipAddress.equals((Inet4Address) Inet4Address.ANY)) {
                return null;
            }
        }
        Inet4Address inet4Address = this.mSubnetMask;
        if (inet4Address != null) {
            try {
                prefixLength = Inet4AddressUtils.netmaskToPrefixLength(inet4Address);
            } catch (IllegalArgumentException e) {
                return null;
            }
        } else {
            prefixLength = Inet4AddressUtils.getImplicitNetmask(ipAddress);
        }
        DhcpResults results = new DhcpResults();
        try {
            results.ipAddress = new LinkAddress(ipAddress, prefixLength);
            short s = 0;
            if (this.mGateways.size() > 0) {
                results.gateway = this.mGateways.get(0);
            }
            results.dnsServers.addAll(this.mDnsServers);
            results.domains = this.mDomainName;
            results.serverAddress = this.mServerIdentifier;
            results.vendorInfo = this.mVendorInfo;
            Integer num = this.mLeaseTime;
            results.leaseDuration = num != null ? num.intValue() : -1;
            Short sh = this.mMtu;
            if (sh != null && 1280 <= sh.shortValue() && this.mMtu.shortValue() <= 1500) {
                s = this.mMtu.shortValue();
            }
            results.mtu = s;
            results.serverHostName = this.mServerHostName;
            return results;
        } catch (IllegalArgumentException e2) {
            return null;
        }
    }

    public long getLeaseTimeMillis() {
        Integer num = this.mLeaseTime;
        if (num == null || num.intValue() == -1) {
            return 0;
        }
        if (this.mLeaseTime.intValue() < 0 || this.mLeaseTime.intValue() >= 60) {
            return (((long) this.mLeaseTime.intValue()) & Constants.INT_MASK) * 1000;
        }
        return 60000;
    }

    public static ByteBuffer buildDiscoverPacket(int encap, int transactionId, short secs, byte[] clientMac, boolean broadcast, byte[] expectedParams) {
        Inet4Address inet4Address = INADDR_ANY;
        DhcpPacket pkt = new DhcpDiscoverPacket(transactionId, secs, inet4Address, clientMac, broadcast, inet4Address);
        pkt.mRequestedParams = expectedParams;
        return pkt.buildPacket(encap, DHCP_SERVER, DHCP_CLIENT);
    }

    public static ByteBuffer buildOfferPacket(int encap, int transactionId, boolean broadcast, Inet4Address serverIpAddr, Inet4Address relayIp, Inet4Address yourIp, byte[] mac, Integer timeout, Inet4Address netMask, Inet4Address bcAddr, List<Inet4Address> gateways, List<Inet4Address> dnsServers, Inet4Address dhcpServerIdentifier, String domainName, String hostname, boolean metered, short mtu) {
        DhcpPacket pkt = new DhcpOfferPacket(transactionId, 0, broadcast, serverIpAddr, relayIp, INADDR_ANY, yourIp, mac);
        pkt.mGateways = gateways;
        pkt.mDnsServers = dnsServers;
        pkt.mLeaseTime = timeout;
        pkt.mDomainName = domainName;
        pkt.mHostName = hostname;
        pkt.mServerIdentifier = dhcpServerIdentifier;
        pkt.mSubnetMask = netMask;
        pkt.mBroadcastAddress = bcAddr;
        pkt.mMtu = Short.valueOf(mtu);
        if (metered) {
            pkt.mVendorInfo = VENDOR_INFO_ANDROID_METERED;
        }
        return pkt.buildPacket(encap, DHCP_CLIENT, DHCP_SERVER);
    }

    public static ByteBuffer buildAckPacket(int encap, int transactionId, boolean broadcast, Inet4Address serverIpAddr, Inet4Address relayIp, Inet4Address yourIp, Inet4Address requestClientIp, byte[] mac, Integer timeout, Inet4Address netMask, Inet4Address bcAddr, List<Inet4Address> gateways, List<Inet4Address> dnsServers, Inet4Address dhcpServerIdentifier, String domainName, String hostname, boolean metered, short mtu) {
        DhcpPacket pkt = new DhcpAckPacket(transactionId, 0, broadcast, serverIpAddr, relayIp, requestClientIp, yourIp, mac);
        pkt.mGateways = gateways;
        pkt.mDnsServers = dnsServers;
        pkt.mLeaseTime = timeout;
        pkt.mDomainName = domainName;
        pkt.mHostName = hostname;
        pkt.mSubnetMask = netMask;
        pkt.mServerIdentifier = dhcpServerIdentifier;
        pkt.mBroadcastAddress = bcAddr;
        pkt.mMtu = Short.valueOf(mtu);
        if (metered) {
            pkt.mVendorInfo = VENDOR_INFO_ANDROID_METERED;
        }
        return pkt.buildPacket(encap, DHCP_CLIENT, DHCP_SERVER);
    }

    public static ByteBuffer buildNakPacket(int encap, int transactionId, Inet4Address serverIpAddr, Inet4Address relayIp, byte[] mac, boolean broadcast, String message) {
        DhcpPacket pkt = new DhcpNakPacket(transactionId, 0, relayIp, mac, broadcast);
        pkt.mMessage = message;
        pkt.mServerIdentifier = serverIpAddr;
        return pkt.buildPacket(encap, DHCP_CLIENT, DHCP_SERVER);
    }

    public static ByteBuffer buildRequestPacket(int encap, int transactionId, short secs, Inet4Address clientIp, boolean broadcast, byte[] clientMac, Inet4Address requestedIpAddress, Inet4Address serverIdentifier, byte[] requestedParams, String hostName) {
        DhcpPacket pkt = new DhcpRequestPacket(transactionId, secs, clientIp, INADDR_ANY, clientMac, broadcast);
        pkt.mRequestedIp = requestedIpAddress;
        pkt.mServerIdentifier = serverIdentifier;
        pkt.mHostName = hostName;
        pkt.mRequestedParams = requestedParams;
        return pkt.buildPacket(encap, DHCP_SERVER, DHCP_CLIENT);
    }
}
