package com.samsung.android.server.wifi.softap;

import com.samsung.android.server.wifi.mobilewips.external.NetworkConstants;
import java.net.Inet4Address;
import java.nio.ByteBuffer;
import java.util.Iterator;

/* access modifiers changed from: package-private */
/* compiled from: DhcpPacket */
public class DhcpAckPacket extends DhcpPacket {
    private final Inet4Address mSrcIp;

    DhcpAckPacket(int transId, short secs, boolean broadcast, Inet4Address serverAddress, Inet4Address relayIp, Inet4Address clientIp, Inet4Address yourIp, byte[] clientMac) {
        super(transId, secs, clientIp, yourIp, serverAddress, relayIp, clientMac, broadcast);
        this.mBroadcast = broadcast;
        this.mSrcIp = serverAddress;
    }

    @Override // com.samsung.android.server.wifi.softap.DhcpPacket
    public String toString() {
        String s = super.toString();
        String dnsServers = " DNS servers: ";
        Iterator it = this.mDnsServers.iterator();
        while (it.hasNext()) {
            dnsServers = dnsServers + ((Inet4Address) it.next()).toString() + " ";
        }
        return s + " ACK: your new IP " + this.mYourIp + ", netmask " + this.mSubnetMask + ", gateways " + this.mGateways + dnsServers + ", lease time " + this.mLeaseTime;
    }

    @Override // com.samsung.android.server.wifi.softap.DhcpPacket
    public ByteBuffer buildPacket(int encap, short destUdp, short srcUdp) {
        ByteBuffer result = ByteBuffer.allocate(NetworkConstants.ETHER_MTU);
        fillInPacket(encap, this.mBroadcast ? INADDR_BROADCAST : this.mYourIp, this.mBroadcast ? INADDR_ANY : this.mSrcIp, destUdp, srcUdp, result, (byte) 2, this.mBroadcast);
        result.flip();
        return result;
    }

    /* access modifiers changed from: package-private */
    @Override // com.samsung.android.server.wifi.softap.DhcpPacket
    public void finishPacket(ByteBuffer buffer) {
        addTlv(buffer, (byte) 53, (byte) 5);
        addTlv(buffer, (byte) 54, this.mServerIdentifier);
        addCommonServerTlvs(buffer);
        addTlvEnd(buffer);
    }

    private static final int getInt(Integer v) {
        if (v == null) {
            return 0;
        }
        return v.intValue();
    }
}
