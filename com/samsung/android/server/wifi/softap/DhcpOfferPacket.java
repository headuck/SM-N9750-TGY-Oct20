package com.samsung.android.server.wifi.softap;

import com.samsung.android.server.wifi.mobilewips.external.NetworkConstants;
import java.net.Inet4Address;
import java.nio.ByteBuffer;
import java.util.Iterator;

/* access modifiers changed from: package-private */
/* compiled from: DhcpPacket */
public class DhcpOfferPacket extends DhcpPacket {
    private final Inet4Address mSrcIp;

    DhcpOfferPacket(int transId, short secs, boolean broadcast, Inet4Address serverAddress, Inet4Address relayIp, Inet4Address clientIp, Inet4Address yourIp, byte[] clientMac) {
        super(transId, secs, clientIp, yourIp, serverAddress, relayIp, clientMac, broadcast);
        this.mSrcIp = serverAddress;
    }

    @Override // com.samsung.android.server.wifi.softap.DhcpPacket
    public String toString() {
        String s = super.toString();
        String dnsServers = ", DNS servers: ";
        if (this.mDnsServers != null) {
            Iterator it = this.mDnsServers.iterator();
            while (it.hasNext()) {
                dnsServers = dnsServers + ((Inet4Address) it.next()) + " ";
            }
        }
        return s + " OFFER, ip " + this.mYourIp + ", mask " + this.mSubnetMask + dnsServers + ", gateways " + this.mGateways + " lease time " + this.mLeaseTime + ", domain " + this.mDomainName;
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
        addTlv(buffer, (byte) 53, (byte) 2);
        addTlv(buffer, (byte) 54, this.mServerIdentifier);
        addCommonServerTlvs(buffer);
        addTlvEnd(buffer);
    }
}
