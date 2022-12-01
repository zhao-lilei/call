package com.example.nettytest.pub.phonecall;

import com.example.nettytest.pub.UniqueIDManager;
import com.example.nettytest.pub.protocol.InviteReqPack;
import com.example.nettytest.pub.transaction.Transaction;
import com.example.nettytest.userinterface.PhoneParam;
import com.example.nettytest.userinterface.UserInterface;

public class CommonCall {
    public final static int CALL_DIRECT_M2S = 1;
    public final static int CALL_DIRECT_S2M = 2;

    public final static int CALL_TYPE_NORMAL = UserInterface.CALL_NORMAL_TYPE;
    public final static int CALL_TYPE_EMERGENCY = UserInterface.CALL_EMERGENCY_TYPE;
    public final static int CALL_TYPE_BROADCAST = UserInterface.CALL_BROADCAST_TYPE;
    public final static int CALL_TYPE_ASSIST = UserInterface.CALL_ASSIST_TYPE;

    public final static int ALERT_TYPE_BEGIN = UserInterface.ALERT_TYPE_BEGIN;
    public final static int ALERT_TYPE_ENDED = UserInterface.ALERT_TYPE_ENDED;

    public final static int CALL_STATE_DIALING = 1;
    public final static int CALL_STATE_RINGING = 2;
    public final static int CALL_STATE_INCOMING = 3;
    public final static int CALL_STATE_CONNECTED = 4;
    public final static int CALL_STATE_DISCONNECTED =5;

    public final static int UPDATE_INTERVAL = Transaction.TRANSCATION_REQUIRING_TIME*2;

    public int type;
    public int direct;
    public int state;

    public String caller;
    public String callee;
    public String answer;

    public String devID;  //dev receive the call info

    public String callID;

    public int audioCodec;
    public int rtpTime;
    public int audioSample;

    public int localRtpPort;
    
    public String remoteRtpAddress;
    public int remoteRtpPort;

    public String inviteReqMsgId;

    public CommonCall(String caller,String callee,int type){
        this.callee = callee;
        this.caller = caller;
        answer = "";
        this.type = type;
        this.state = CALL_STATE_DIALING;
        inviteReqMsgId = "";

        devID = caller;
        callID = UniqueIDManager.GetUniqueID(caller, UniqueIDManager.CALL_UNIQUE_ID);

        rtpTime = PhoneParam.callRtpPTime;
        audioCodec = PhoneParam.callRtpCodec;
        audioSample = PhoneParam.callRtpDataRate;
        if(type==CALL_TYPE_BROADCAST){
            localRtpPort = PhoneParam.BROADCAST_CALL_RTP_PORT;
            remoteRtpPort = PhoneParam.BROADCAST_CALL_RTP_PORT;
        }else{
            localRtpPort = PhoneParam.INVITE_CALL_RTP_PORT;
            remoteRtpPort = PhoneParam.ANSWER_CALL_RTP_PORT;
        }
        remoteRtpAddress="";
    }

    public CommonCall(String id,InviteReqPack pack){
        callee = pack.callee;
        caller = pack.caller;
        type = pack.callType;
        direct = pack.callDirect;

        answer = "";

        devID = id;
        state = CALL_STATE_INCOMING;
        callID = pack.callID;

        rtpTime = pack.pTime;
        audioCodec = pack.codec;
        audioSample = pack.sample;

        remoteRtpAddress = pack.callerRtpIP;
        remoteRtpPort = pack.callerRtpPort;
        
        inviteReqMsgId = "";
        
        if(type==CALL_TYPE_BROADCAST){
            localRtpPort = PhoneParam.BROADCAST_CALL_RTP_PORT;
        }else{
            localRtpPort = PhoneParam.ANSWER_CALL_RTP_PORT;
        }

    }

    public static String GetCallTypeName(int callType){
        String typeName = "Unknow Call Type";
        switch(callType){
            case CALL_TYPE_NORMAL:
                typeName = "Normal Call";
            break;
            case CALL_TYPE_EMERGENCY:
                typeName = "Emergency Call";
            break;
            case CALL_TYPE_BROADCAST:
                typeName = "Broadcast Call";
            break;
            case CALL_TYPE_ASSIST:
                typeName = "Assist Call";
            break;
        }
        return typeName;
    }

    public static boolean IsAlertCall(int type){
        boolean result = false;
        if(type>=ALERT_TYPE_BEGIN&&type<=ALERT_TYPE_ENDED)
            result = true;
        return result;
    }

}
