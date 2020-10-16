package com.samsung.android.server.wifi.softap;

import android.os.Handler;
import android.os.Looper;
import android.os.MessageQueue;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import java.io.FileDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.SocketAddress;
import libcore.io.IoUtils;

public abstract class PacketReader {
    public static final int DEFAULT_RECV_BUF_SIZE = 2048;
    private static final int FD_EVENTS = 5;
    private static final int UNREGISTER_THIS_FD = 0;
    private FileDescriptor mFd;
    private final Handler mHandler;
    private final byte[] mPacket;
    private long mPacketsReceived;
    private final MessageQueue mQueue;

    /* access modifiers changed from: protected */
    public abstract FileDescriptor createFd();

    protected static void closeFd(FileDescriptor fd) {
        IoUtils.closeQuietly(fd);
    }

    protected PacketReader(Handler h) {
        this(h, 2048);
    }

    protected PacketReader(Handler h, int recvbufsize) {
        this.mHandler = h;
        this.mQueue = this.mHandler.getLooper().getQueue();
        this.mPacket = new byte[Math.max(recvbufsize, 2048)];
    }

    public final void start() {
        if (onCorrectThread()) {
            createAndRegisterFd();
        } else {
            this.mHandler.post(new Runnable() {
                /* class com.samsung.android.server.wifi.softap.$$Lambda$PacketReader$qy6D1GUozkMluQb9Hfy_1DXE2iI */

                public final void run() {
                    PacketReader.this.lambda$start$0$PacketReader();
                }
            });
        }
    }

    public /* synthetic */ void lambda$start$0$PacketReader() {
        logError("start() called from off-thread", null);
        createAndRegisterFd();
    }

    public final void stop() {
        if (onCorrectThread()) {
            unregisterAndDestroyFd();
        } else {
            this.mHandler.post(new Runnable() {
                /* class com.samsung.android.server.wifi.softap.$$Lambda$PacketReader$O8g9sBSltAcuWn7Tr1laY4fLlsY */

                public final void run() {
                    PacketReader.this.lambda$stop$1$PacketReader();
                }
            });
        }
    }

    public /* synthetic */ void lambda$stop$1$PacketReader() {
        logError("stop() called from off-thread", null);
        unregisterAndDestroyFd();
    }

    public Handler getHandler() {
        return this.mHandler;
    }

    public final int recvBufSize() {
        return this.mPacket.length;
    }

    public final long numPacketsReceived() {
        return this.mPacketsReceived;
    }

    /* access modifiers changed from: protected */
    public int readPacket(FileDescriptor fd, byte[] packetBuffer) throws Exception {
        return Os.read(fd, packetBuffer, 0, packetBuffer.length);
    }

    /* access modifiers changed from: protected */
    public void handlePacket(byte[] recvbuf, int length) {
    }

    /* access modifiers changed from: protected */
    public void logError(String msg, Exception e) {
    }

    /* access modifiers changed from: protected */
    public void onStart() {
    }

    /* access modifiers changed from: protected */
    public void onStop() {
    }

    private void createAndRegisterFd() {
        if (this.mFd == null) {
            try {
                this.mFd = createFd();
                if (this.mFd != null) {
                    IoUtils.setBlocking(this.mFd, false);
                }
                FileDescriptor fileDescriptor = this.mFd;
                if (fileDescriptor != null) {
                    this.mQueue.addOnFileDescriptorEventListener(fileDescriptor, 5, new MessageQueue.OnFileDescriptorEventListener() {
                        /* class com.samsung.android.server.wifi.softap.PacketReader.C07901 */

                        public int onFileDescriptorEvents(FileDescriptor fd, int events) {
                            if (PacketReader.this.isRunning() && PacketReader.this.handleInput()) {
                                return 5;
                            }
                            PacketReader.this.unregisterAndDestroyFd();
                            return 0;
                        }
                    });
                    onStart();
                }
            } catch (Exception e) {
                logError("Failed to create socket: ", e);
                closeFd(this.mFd);
                this.mFd = null;
            }
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean isRunning() {
        FileDescriptor fileDescriptor = this.mFd;
        return fileDescriptor != null && fileDescriptor.valid();
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean handleInput() {
        while (isRunning()) {
            try {
                int bytesRead = readPacket(this.mFd, this.mPacket);
                if (bytesRead < 1) {
                    if (isRunning()) {
                        logError("Socket closed, exiting", null);
                    }
                    return false;
                }
                this.mPacketsReceived++;
                try {
                    handlePacket(this.mPacket, bytesRead);
                } catch (Exception e) {
                    logError("handlePacket error: ", e);
                    return false;
                }
            } catch (ErrnoException e2) {
                if (e2.errno == OsConstants.EAGAIN) {
                    return true;
                }
                if (e2.errno != OsConstants.EINTR) {
                    if (!isRunning()) {
                        return false;
                    }
                    logError("readPacket error: ", e2);
                    return false;
                }
            } catch (Exception e3) {
                if (!isRunning()) {
                    return false;
                }
                logError("readPacket error: ", e3);
                return false;
            }
        }
        return false;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void unregisterAndDestroyFd() {
        FileDescriptor fileDescriptor = this.mFd;
        if (fileDescriptor != null) {
            this.mQueue.removeOnFileDescriptorEventListener(fileDescriptor);
            closeFd(this.mFd);
            this.mFd = null;
            onStop();
        }
    }

    private boolean onCorrectThread() {
        return this.mHandler.getLooper() == Looper.myLooper();
    }

    public static Object getMethod(Object original, String className, String methodName, Object... args) throws Exception {
        try {
            Method method = Class.forName(className).getDeclaredMethod(methodName, getObjectType(args));
            method.setAccessible(true);
            return method.invoke(original, args);
        } catch (Exception e) {
            e.printStackTrace();
            if (!(e instanceof InvocationTargetException)) {
                return null;
            }
            throw ((Exception) e.getCause());
        }
    }

    public static Class<?>[] getObjectType(Object... args) {
        Class<?>[] types = new Class[args.length];
        try {
            Class cPacketSocketAddress = Class.forName("android.system.PacketSocketAddress");
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof Integer) {
                    types[i] = Integer.TYPE;
                } else if (args[i] instanceof Long) {
                    types[i] = Long.TYPE;
                } else if (args[i] instanceof Boolean) {
                    types[i] = Boolean.TYPE;
                } else if (args[i] instanceof Float) {
                    types[i] = Float.TYPE;
                } else if (args[i] instanceof Double) {
                    types[i] = Double.TYPE;
                } else if (args[i] instanceof Short) {
                    types[i] = Short.TYPE;
                } else if (args[i] instanceof Byte) {
                    types[i] = Byte.TYPE;
                } else {
                    if (!(args[i] instanceof Inet4Address)) {
                        if (!(args[i] instanceof Inet6Address)) {
                            if (args[i].getClass() == cPacketSocketAddress) {
                                types[i] = SocketAddress.class;
                            } else {
                                types[i] = args[i].getClass();
                            }
                        }
                    }
                    types[i] = InetAddress.class;
                }
            }
            return types;
        } catch (Exception e) {
            return null;
        }
    }
}
