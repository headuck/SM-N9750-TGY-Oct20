package com.android.server.wifi.iwc.rlengine;

import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;
import android.util.Log;
import com.android.server.wifi.hotspot2.anqp.NAIRealmData;
import com.android.server.wifi.iwc.IWCLogFile;
import com.android.server.wifi.iwc.RewardEvent;
import com.android.server.wifi.tcp.WifiTransportLayerUtils;
import java.util.Iterator;

public class RewardManager {
    private static final String TAG = "IWCMonitor.RM";
    public int INITREWARDTIMES = 7;
    private IWCLogFile IWCLog;
    float NReward = -10.0f;
    float PReward = 1.0f;
    float PReward10 = 10.0f;
    float R1Reward = 1.0f;
    float R2Reward = -10.0f;
    float R3Reward = 10.0f;
    public float alpha = 0.8f;
    public float alpha_half = 0.9f;
    public float beta = 0.8f;
    public float gamma = 1.0f;
    public float gamma_dis = 0.98f;
    public int iTimes;
    public boolean mSwitchFlag;
    public String storedCurrentAP;

    public RewardManager(IWCLogFile logFile) {
        this.IWCLog = logFile;
    }

    public void _sendDebugIntent(Context ctx, int kind, String strEvent, String strBss, int idx, QTable qt) {
        Intent intent = new Intent("com.sec.android.IWC_REWARD_EVENT_DEBUG");
        intent.putExtra("kind", kind);
        intent.putExtra("event", strEvent);
        intent.putExtra("bssid", strBss);
        intent.putExtra("tableindex", idx);
        intent.putExtra("lastvalue1", qt.qTable[0][0]);
        intent.putExtra("lastvalue2", qt.qTable[1][0]);
        intent.putExtra("lastvalue3", qt.qTable[2][0]);
        intent.putExtra("ss_poor", qt.getSteadyState());
        intent.putExtra("qai", qt.mLastSNS == 1 ? qt.getState() + 1 : -1);
        ctx.sendBroadcastAsUser(intent, UserHandle.ALL);
    }

    public void sendDebugIntent(Context ctx, String strEvent, String strBss, int idx, QTable qt) {
        _sendDebugIntent(ctx, 1, strEvent, strBss, idx, qt);
    }

    public void updateDebugIntent(Context ctx, String strEvent, String strBss, int idx, QTable qt) {
        _sendDebugIntent(ctx, 4, strEvent, strBss, idx, qt);
    }

    public int applyRewards(RewardEvent rwEvent, QTableContainer qtable, RFLInterface intf, long timestamp) {
        int curState;
        int i;
        int ReachSS;
        int ReachSS2;
        QTable tempTable;
        this.storedCurrentAP = intf.currentApBssid_IN;
        this.mSwitchFlag = intf.switchFlag;
        intf.mBdTracking.setIdInfo(3);
        int tableIndex = qtable.findTable(this.storedCurrentAP);
        if (tableIndex == -1) {
            Log.e(TAG, "ApplyRewards - findTable returned -1");
            return -1;
        }
        QTable tempTable2 = qtable.qTableList.get(tableIndex);
        int curState2 = tempTable2.getState();
        int lastAction = tempTable2.getLastAction();
        int lastState = tempTable2.getLastState();
        switch (rwEvent) {
            case MANUAL_DISCONNECT:
                QTable tempTable3 = tempTable2;
                int ReachSS3 = 0;
                if (intf.edgeFlag) {
                    ReachSS3 = tempTable3.addEvent(intf.snsFlag, intf.aggSnsFlag, RewardEvent.MANUAL_DISCONNECT);
                    tempTable3 = moreAggressiveRewardUpdateCB(tempTable3, curState2);
                }
                writeLog(this.storedCurrentAP, tempTable3, "Manual_disconnect:M", lastState, lastAction, intf.snsFlag, intf.aggSnsFlag, timestamp);
                intf.mBdTracking.set24HEventAccWithIdx(8);
                i = 1;
                intf.mBdTracking.setIdInfo(1);
                curState = ReachSS3;
                break;
            case MANUAL_SWITCH:
                QTable tempTable4 = tempTable2;
                int ReachSS4 = 0;
                if (intf.edgeFlag) {
                    ReachSS4 = tempTable4.addEvent(intf.snsFlag, intf.aggSnsFlag, RewardEvent.MANUAL_SWITCH);
                    tempTable4 = moreAggressiveHalfRewardUpdateCB(tempTable4, curState2);
                }
                writeLog(this.storedCurrentAP, tempTable4, "Manual_switch:halfM", lastState, lastAction, intf.snsFlag, intf.aggSnsFlag, timestamp);
                intf.mBdTracking.set24HEventAccWithIdx(0);
                intf.mBdTracking.setIdInfo(1);
                curState = ReachSS4;
                i = 1;
                break;
            case MANUAL_SWITCH_G:
                ReachSS = 0;
                writeLog(this.storedCurrentAP, tempTable2, "Manual_switch_G:X", lastState, lastAction, intf.snsFlag, intf.aggSnsFlag, timestamp);
                intf.mBdTracking.set24HEventAccWithIdx(1);
                intf.mBdTracking.setIdInfo(1);
                i = 1;
                curState = ReachSS;
                break;
            case MANUAL_SWITCH_L:
                curState = tempTable2.addEvent(intf.snsFlag, intf.aggSnsFlag, RewardEvent.MANUAL_SWITCH_L);
                writeLog(this.storedCurrentAP, lessAggressiveRewardUpdateCB(tempTable2, curState2), "Manual_switch_L:L", lastState, lastAction, intf.snsFlag, intf.aggSnsFlag, timestamp);
                intf.mBdTracking.set24HEventAccWithIdx(2);
                intf.mBdTracking.setIdInfo(1);
                i = 1;
                break;
            case CONNECTION_SWITCHED_TOO_SHORT:
                int ReachSS5 = tempTable2.addEvent(intf.snsFlag, intf.aggSnsFlag, RewardEvent.CONNECTION_SWITCHED_TOO_SHORT);
                writeLog(this.storedCurrentAP, lessAggressiveRewardUpdateCB(tempTable2, curState2), "connection_switched_too_short:L", lastState, lastAction, intf.snsFlag, intf.aggSnsFlag, timestamp);
                intf.mBdTracking.set24HEventAccWithIdx(3);
                intf.mBdTracking.setIdInfo(1);
                curState = ReachSS5;
                i = 1;
                break;
            case MANUAL_RECONNECTION:
                curState = tempTable2.addEvent(intf.snsFlag, intf.aggSnsFlag, RewardEvent.MANUAL_RECONNECTION);
                writeLog(this.storedCurrentAP, lessAggressiveRewardUpdateCB(tempTable2, curState2), "Manual_reconection:L", lastState, lastAction, intf.snsFlag, intf.aggSnsFlag, timestamp);
                intf.mBdTracking.set24HEventAccWithIdx(4);
                intf.mBdTracking.setIdInfo(1);
                i = 1;
                break;
            case WIFI_OFF:
                ReachSS = 0;
                if (!intf.edgeFlag) {
                    i = 1;
                    curState = ReachSS;
                    break;
                } else {
                    curState = tempTable2.addEvent(intf.snsFlag, intf.aggSnsFlag, RewardEvent.WIFI_OFF);
                    writeLog(this.storedCurrentAP, moreAggressiveRewardUpdateCB(tempTable2, curState2), "wifi-off:M", lastState, lastAction, intf.snsFlag, intf.aggSnsFlag, timestamp);
                    intf.mBdTracking.set24HEventAccWithIdx(5);
                    i = 1;
                    intf.mBdTracking.setIdInfo(1);
                    break;
                }
            case CELLULAR_DATA_OFF:
                ReachSS = 0;
                if (!intf.edgeFlag) {
                    i = 1;
                    curState = ReachSS;
                    break;
                } else {
                    curState = tempTable2.addEvent(intf.snsFlag, intf.aggSnsFlag, RewardEvent.CELLULAR_DATA_OFF);
                    writeLog(this.storedCurrentAP, lessAggressiveRewardUpdateCB(tempTable2, curState2), "cellular_data_off:L", lastState, lastAction, intf.snsFlag, intf.aggSnsFlag, timestamp);
                    intf.mBdTracking.set24HEventAccWithIdx(7);
                    i = 1;
                    intf.mBdTracking.setIdInfo(1);
                    break;
                }
            case AUTO_DISCONNECTION:
                int ReachSS6 = tempTable2.addEvent(intf.snsFlag, intf.aggSnsFlag, RewardEvent.AUTO_DISCONNECTION);
                tempTable2.qTable[curState2][0] = (tempTable2.qTable[curState2][0] * this.gamma_dis) + 0.1f;
                writeLog(this.storedCurrentAP, tempTable2, "auto_disconnection:M", lastState, lastAction, intf.snsFlag, intf.aggSnsFlag, timestamp);
                intf.mBdTracking.set24HEventAccWithIdx(6);
                intf.mBdTracking.setIdInfo(3);
                curState = ReachSS6;
                i = 1;
                break;
            case SNS_ON:
                tempTable2.mLastSNS = 1;
                tempTable2.mLastAGG = 1;
                int ReachSS7 = tempTable2.addEvent(intf.snsFlag, intf.aggSnsFlag, RewardEvent.SNS_ON);
                writeLog(this.storedCurrentAP, moreAggressiveRewardUpdateCB(tempTable2, curState2), "SNS ON : M", lastState, lastAction, intf.snsFlag, intf.aggSnsFlag, timestamp);
                intf.mBdTracking.set24HEventAccWithIdx(9);
                intf.mBdTracking.setIdInfo(1);
                i = 1;
                curState = ReachSS7;
                break;
            case SNS_OFF:
                tempTable2.mLastSNS = 0;
                tempTable2.mLastAGG = 0;
                if (intf.switchFlag) {
                    int ReachSS8 = tempTable2.addEvent(intf.snsFlag, intf.aggSnsFlag, RewardEvent.SNS_OFF);
                    writeLog(this.storedCurrentAP, lessAggressiveRewardUpdateCB(lessAggressiveRewardUpdateCB(tempTable2, curState2), curState2), "SNS OFF : LL", lastState, lastAction, intf.snsFlag, intf.aggSnsFlag, timestamp);
                    intf.mBdTracking.set24HEventAccWithIdx(10);
                    intf.mBdTracking.set24HEventAccWithIdx(10);
                    i = 1;
                    intf.mBdTracking.setIdInfo(1);
                    curState = ReachSS8;
                    break;
                } else {
                    int ReachSS9 = tempTable2.addEvent(intf.snsFlag, intf.aggSnsFlag, RewardEvent.SNS_OFF);
                    writeLog(this.storedCurrentAP, lessAggressiveRewardUpdateCB(tempTable2, curState2), "SNS OFF : L", lastState, lastAction, intf.snsFlag, intf.aggSnsFlag, timestamp);
                    intf.mBdTracking.set24HEventAccWithIdx(10);
                    i = 1;
                    intf.mBdTracking.setIdInfo(1);
                    curState = ReachSS9;
                    break;
                }
            case NETWORK_CONNECTED:
                Log.d(TAG, "network_connected cur state : " + intf.snsFlag + " " + intf.aggSnsFlag + "   Saved state : " + tempTable2.mLastSNS + " " + tempTable2.mLastAGG);
                writeLog(this.storedCurrentAP, tempTable2, "NETWORK_CONNECTED initial value ", lastState, lastAction, intf.snsFlag, intf.aggSnsFlag, timestamp);
                if (!(tempTable2.mLastSNS == -1 || tempTable2.mLastAGG == -1)) {
                    if (!intf.snsFlag || !intf.aggSnsFlag) {
                        if (!intf.snsFlag && !intf.aggSnsFlag && ((tempTable2.mLastSNS == 1 && tempTable2.mLastAGG == 1) || (tempTable2.mLastSNS == 1 && tempTable2.mLastAGG == 0))) {
                            ReachSS2 = tempTable2.addEvent(intf.snsFlag, intf.aggSnsFlag, RewardEvent.NETWORK_CONNECTED_WITH_SNS_OFF);
                            QTable tempTable5 = lessAggressiveRewardUpdateCB(tempTable2, curState2);
                            writeLog(this.storedCurrentAP, tempTable5, "SNS OFF indirect : L", lastState, lastAction, intf.snsFlag, intf.aggSnsFlag, timestamp);
                            intf.mBdTracking.set24HEventAccWithIdx(12);
                            intf.mBdTracking.setIdInfo(1);
                            tempTable = tempTable5;
                            tempTable.mLastSNS = intf.snsFlag ? 1 : 0;
                            tempTable.mLastAGG = intf.aggSnsFlag ? 1 : 0;
                            i = 1;
                            curState = ReachSS2;
                            break;
                        }
                    } else if ((tempTable2.mLastSNS == 1 && tempTable2.mLastAGG == 0) || tempTable2.mLastSNS == 0) {
                        ReachSS2 = tempTable2.addEvent(intf.snsFlag, intf.aggSnsFlag, RewardEvent.NETWORK_CONNECTED_WITH_SNS_ON);
                        QTable tempTable6 = moreAggressiveRewardUpdateCB(tempTable2, curState2);
                        writeLog(this.storedCurrentAP, tempTable6, "SNS ON indirect : M", lastState, lastAction, intf.snsFlag, intf.aggSnsFlag, timestamp);
                        intf.mBdTracking.set24HEventAccWithIdx(11);
                        intf.mBdTracking.setIdInfo(1);
                        tempTable = tempTable6;
                        tempTable.mLastSNS = intf.snsFlag ? 1 : 0;
                        tempTable.mLastAGG = intf.aggSnsFlag ? 1 : 0;
                        i = 1;
                        curState = ReachSS2;
                    }
                }
                tempTable = tempTable2;
                ReachSS2 = 0;
                tempTable.mLastSNS = intf.snsFlag ? 1 : 0;
                tempTable.mLastAGG = intf.aggSnsFlag ? 1 : 0;
                i = 1;
                curState = ReachSS2;
                break;
            case NETWORK_DISCONNECTED:
                i = 1;
                ReachSS = 0;
                curState = ReachSS;
                break;
            case NO_EVENT:
                i = 1;
                ReachSS = 0;
                curState = ReachSS;
                break;
            default:
                i = 1;
                ReachSS = 0;
                curState = ReachSS;
                break;
        }
        if (curState == i) {
            intf.mBdTracking.setIdInfo(2);
        }
        return curState;
    }

    public String getEventTypeString(RewardEvent rwEvent, boolean switchFlag) {
        switch (rwEvent) {
            case MANUAL_DISCONNECT:
                return "Manual_disconnect:M";
            case MANUAL_SWITCH:
                return "Manual_switch:halfM";
            case MANUAL_SWITCH_G:
                return "Manual_switch_G:X";
            case MANUAL_SWITCH_L:
                return "Manual_switch_L:L";
            case CONNECTION_SWITCHED_TOO_SHORT:
                return "connection_switched_too_short:L";
            case MANUAL_RECONNECTION:
                return "Manual_reconection:L";
            case WIFI_OFF:
                return "wifi-off:M";
            case CELLULAR_DATA_OFF:
                return "cellular_data_off:L";
            case AUTO_DISCONNECTION:
                return "auto_disconnection:A";
            case SNS_ON:
                return "SNS ON : M";
            case SNS_OFF:
                if (!switchFlag) {
                    return "SNS OFF : L";
                }
                return "SNS OFF : LL";
            case NETWORK_CONNECTED:
                return "WiFi Network Changed";
            default:
                return WifiTransportLayerUtils.CATEGORY_PLAYSTORE_NONE;
        }
    }

    public QTable moreAggressiveRewardUpdateCB(QTable qtable, int curState) {
        float discount = 1.0f;
        for (int idxstate = 0; idxstate < qtable.numStates; idxstate++) {
            qtable.qTable[idxstate][0] = this.alpha * qtable.qTable[idxstate][0];
        }
        if (curState == 0) {
            qtable.qTable[curState][0] = qtable.qTable[curState][0] + (this.gamma * 1.0f);
        } else {
            for (int idxstate2 = curState - 1; idxstate2 >= 0; idxstate2--) {
                qtable.qTable[idxstate2][0] = qtable.qTable[idxstate2][0] + (this.gamma * discount);
                discount *= this.beta;
            }
        }
        return qtable;
    }

    public QTable moreAggressiveHalfRewardUpdateCB(QTable qtable, int curState) {
        float discount = 0.5f;
        for (int idxstate = 0; idxstate < qtable.numStates; idxstate++) {
            qtable.qTable[idxstate][0] = this.alpha_half * qtable.qTable[idxstate][0];
        }
        if (curState == 0) {
            qtable.qTable[curState][0] = qtable.qTable[curState][0] + (this.gamma * 0.5f);
        } else {
            for (int idxstate2 = curState - 1; idxstate2 >= 0; idxstate2--) {
                qtable.qTable[idxstate2][0] = qtable.qTable[idxstate2][0] + (this.gamma * discount);
                discount *= this.beta;
            }
        }
        return qtable;
    }

    public QTable lessAggressiveRewardUpdateCB(QTable qtable, int curState) {
        float discount = 1.0f;
        for (int idxstate = 0; idxstate < qtable.numStates; idxstate++) {
            qtable.qTable[idxstate][0] = this.alpha * qtable.qTable[idxstate][0];
        }
        if (curState == qtable.numStates - 1) {
            qtable.qTable[curState][0] = qtable.qTable[curState][0] + this.gamma;
        } else {
            for (int idxstate2 = curState + 1; idxstate2 < qtable.numStates; idxstate2++) {
                qtable.qTable[idxstate2][0] = qtable.qTable[idxstate2][0] + (this.gamma * 1.0f * discount);
                discount *= this.beta;
            }
        }
        return qtable;
    }

    public void writeLog(String AP, QTable tempTable, String event, int state, int action, boolean snsFlag, boolean aggFlag, long timestamp) {
        String logValue = ("" + String.format("CAP: %s, Event: %s>> ", AP, event)) + String.format("Q-Table: [", new Object[0]);
        for (int i = 0; i < tempTable.numStates; i++) {
            logValue = (logValue + String.format(" %f", Float.valueOf(tempTable.qTable[i][0]))) + String.format(NAIRealmData.NAI_REALM_STRING_SEPARATOR, new Object[0]);
        }
        StringBuilder sb = new StringBuilder();
        sb.append(logValue);
        sb.append(String.format(" ] >> swflag=" + this.mSwitchFlag + " timestamp =" + timestamp, new Object[0]));
        String logValue2 = sb.toString();
        IWCLogFile iWCLogFile = this.IWCLog;
        if (iWCLogFile != null) {
            iWCLogFile.writeToLogFile("Q-Table reward update", logValue2);
        }
        if (!event.equals("NETWORK_CONNECTED initial value ")) {
            String logValue3 = ("" + String.format("isSteadyState: %b, snsFlag: %b, aggFlag: %b, ", Boolean.valueOf(tempTable.getSteadyState()), Boolean.valueOf(snsFlag), Boolean.valueOf(aggFlag))) + String.format("EventBuffer: [", new Object[0]);
            Iterator<Integer> iter = tempTable.eventBuffer.iterator();
            while (iter.hasNext()) {
                int actionType = iter.next().intValue();
                logValue3 = logValue3 + String.format(" %d", Integer.valueOf(actionType));
            }
            String logValue4 = (logValue3 + String.format(" ]", new Object[0])) + " timestamp =" + timestamp;
            IWCLogFile iWCLogFile2 = this.IWCLog;
            if (iWCLogFile2 != null) {
                iWCLogFile2.writeToLogFile("Steady State", logValue4);
            }
        }
    }
}
