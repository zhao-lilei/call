package com.example.nettytest.terminal.test;

import com.example.nettytest.userinterface.PhoneParam;
import com.example.nettytest.userinterface.UserInterface;

public class ServerTest {

    public int CreateServerDevice(){
        if(PhoneParam.serverActive){
            UserInterface.StartServer();
            UserInterface.CreateServerDevices();
        }
        return 0;
    }
}
