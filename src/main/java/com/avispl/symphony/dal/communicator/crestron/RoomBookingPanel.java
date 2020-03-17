package com.avispl.symphony.dal.communicator.crestron;

import com.avispl.symphony.api.dal.control.Controller;
import com.avispl.symphony.api.dal.dto.control.ControllableProperty;
import com.avispl.symphony.api.dal.dto.monitor.*;
import com.avispl.symphony.api.dal.monitor.Monitorable;
import com.avispl.symphony.api.dal.ping.Pingable;
import com.avispl.symphony.dal.communicator.RestCommunicator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.text.SimpleDateFormat;
import java.util.*;
import static java.util.Collections.singletonList;

public class RoomBookingPanel extends RestCommunicator implements Monitorable, Pingable, Controller {

    @Override
    protected void authenticate() throws Exception {
        if (this.logger.isDebugEnabled())
            this.logger.debug("Attempting login with credentials [User: " + getLogin() + " Password: "+ getPassword() + "]");
        doPost("userlogin.html", "login=" + getLogin() + "&passwd=" + getPassword());
        //if password is correct, response with be status 200 and there will be set-cookie headers with session tokens etc.
    }

    @Override
    protected void internalInit() throws Exception {
        this.setAuthenticationScheme(AuthenticationScheme.None); //Stop sending default authentication for no reason
        this.setTrustAllCertificates(true); //Crestron ssl cert usually throws error- bypassing here
        this.setProtocol("https"); //Force https to avoid user configuration error (Devices always use https)
        this.setContentType("application/json");
        super.internalInit();
    }

    @Override
    protected void internalDestroy() {
        super.internalDestroy();
        try {
            this.disconnect();
        } catch (Exception ignored){}
    }

    @Override
    public List<Statistics> getMultipleStatistics() throws Exception {
        Map<String,String> deviceStatistics = new HashMap<String, String>();
        Map<String,String> deviceControls = new HashMap<String,String>();
        ExtendedStatistics statistics = new ExtendedStatistics();
        String devResponse; //String to store response from device
        final ObjectMapper mapper = new ObjectMapper();

        try {
            devResponse = doGet("Device");
        } catch (Exception e) {
            //If error is code 403 session token is expired or non existent
            if (e.getCause().toString().contains("403 Forbidden")) {
                if (this.logger.isDebugEnabled())
                    this.logger.debug("403 Error Response received from device. Session token expired or credentials incorrect.");

                this.authenticate();
                //Attempt to get data again, this time not catching exceptions as device should be authenticated.
                devResponse = doGet("Device");
            } else //If exception is not expired session throw the error.
                throw e;
        }
        final JsonNode jsonResponse = mapper.readTree(devResponse);
        deviceStatistics.put("RoomState",getValue(jsonResponse,"/Device/SchedulingPanel/Monitoring/RoomStatus","State"));
        deviceStatistics.put("ConnectionStatusMessage",getValue(jsonResponse,"/Device/SchedulingPanel/Monitoring/Scheduling","ConnectionStatusMessage"));
        deviceStatistics.put("CalendarSyncStatus",getValue(jsonResponse,"/Device/SchedulingPanel/Monitoring/Scheduling","CalendarSyncStatus"));
        deviceStatistics.put("PanelSyncing",timeInThreshold(getValue(jsonResponse,"/Device/SchedulingPanel/Monitoring/Scheduling","CalendarSyncStatus").substring(20)));
        deviceStatistics.put("ConnectionStatus",getValue(jsonResponse,"/Device/SchedulingPanel/Monitoring/Scheduling","ConnectionStatus"));
        deviceStatistics.put("ExchangeUsername",getValue(jsonResponse,"/Device/SchedulingPanel/Config/Scheduling/Exchange","Username"));
        deviceStatistics.put("PufVersion",getValue(jsonResponse,"/Device/DeviceInfo","PufVersion"));
        deviceStatistics.put("MacAddress",getValue(jsonResponse,"/Device/DeviceInfo","MacAddress"));
        deviceStatistics.put("SerialNumber",getValue(jsonResponse,"/Device/DeviceInfo","SerialNumber"));
        deviceStatistics.put("DeviceName",getValue(jsonResponse,"/Device/DeviceInfo","Name"));
        deviceStatistics.put("BuildDate",getValue(jsonResponse,"/Device/DeviceInfo","BuildDate"));

        deviceStatistics.put("reboot","0");
        deviceControls.put("reboot", "push");

        statistics.setStatistics(deviceStatistics);
        statistics.setControl(deviceControls);
        return singletonList(statistics);
    }

    private String getValue(JsonNode baseObject,String objectPath, String key) {
        return baseObject.at(objectPath+"/"+key).textValue();
    }

    private String timeInThreshold(String dateString){
        try{
        Date lastSync = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm aa - MMMMM dd, yyyy", Locale.ENGLISH);
        sdf.setTimeZone(TimeZone.getTimeZone("Australia/Melbourne"));
        lastSync.setTime(sdf.parse(dateString).getTime());
        lastSync.setTime(lastSync.getTime() + 720000); // Add 12 mins to lastSync time
        return lastSync.getTime() <= new Date().getTime() ? "false":"true";
        } catch (Exception e) {
            return "Unable to parse time in string: " + dateString;
        }
    }

    @Override
    public void controlProperty(ControllableProperty controllableProperty){
        if (controllableProperty == null)
            return;

        if (controllableProperty.getProperty().equalsIgnoreCase("reboot")) {
            try {
                String response = doPost("Device/DeviceOperations", "{\"Device\":{\"DeviceOperations\":{\"Reboot\":true}}}"); //send reboot command to the device
                if (!response.contains("OK") && this.logger.isErrorEnabled())
                    this.logger.error("Device Reboot failed with response from device: " + response);
            } catch (Exception e) {
                if (this.logger.isErrorEnabled())
                    this.logger.error("Failed to reboot device: " + e);
            }
        }
    }

    @Override
    public void controlProperties(List<ControllableProperty> controllableProperties){
        for (ControllableProperty cp : controllableProperties)
            controlProperty(cp);
    }

    public static void main(String[] args) throws Exception {
        RoomBookingPanel device = new RoomBookingPanel();
        device.setHost("10.217.64.51");
        device.setLogin("admin");
        device.setPassword("admin");
        device.init();
        ExtendedStatistics stats = (ExtendedStatistics)device.getMultipleStatistics().get(0);
        stats.getStatistics().forEach((k,v) ->{
            System.out.println(k + " : " + v);
        });
    }
}
