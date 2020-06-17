package com.avispl.symphony.dal.communicator.crestron;

import com.avispl.symphony.api.common.error.NotAuthorizedException;
import com.avispl.symphony.api.dal.control.Controller;
import com.avispl.symphony.api.dal.dto.control.ControllableProperty;
import com.avispl.symphony.api.dal.dto.monitor.*;
import com.avispl.symphony.api.dal.monitor.Monitorable;
import com.avispl.symphony.api.dal.ping.Pingable;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.log4j.BasicConfigurator;

import java.text.SimpleDateFormat;
import java.util.*;

import static com.avispl.symphony.dal.communicator.crestron.JsonUtils.parseJson;
import static java.util.Collections.singletonList;
/************Change Log********
 **** v1.0
 * - Initial Version
 *
 **** v1.1
 * - Changed from using JSONObjects to JsonNode
 * - Added more properties to be monitored
 * - Added control
 *
 **** v2.0
 * - Firmware update broke previous functionality- 301 response is now received on successful authentication
 *   HttpCommunicator is no longer able to be used
 * - Switch adaptor to use a custom Http Communicator which returns http response regardless of errors
 */



public class RoomBookingPanel extends CustomHttpCommunicator implements Monitorable, Pingable, Controller {

    public RoomBookingPanel(){
        //BasicConfigurator.configure();
    }

    private void authenticate(){
        try
        {
            doPost("/userlogin.html","login=" + (!getLogin().isEmpty() ? getLogin() : "admin") + "&passwd=" + (!getPassword().isEmpty() ? getPassword() : "admin"));
        }catch (Exception e)
        {
            this.logger.warn("Error authenticating- Login: " + getLogin() + " Passwd: " + getPassword() + " with error: " + e.getMessage());
        }

    }

   @Override
    public List<Statistics> getMultipleStatistics() throws Exception {
        Map<String,String> deviceStatistics = new HashMap<String, String>();
        Map<String,String> deviceControls = new HashMap<String,String>();
        ExtendedStatistics statistics = new ExtendedStatistics();

        authenticate();

        HttpResponse devResponse = doGet("Device");
        if (devResponse.getResponseCode() != 200) {
            devResponse = doGet("Device");
            if (devResponse.getResponseCode() == 403){
                throw new NotAuthorizedException("Username or Password is incorrect.");
            }
        }

        final JsonNode jsonResponse = parseJson(devResponse.getResponseBody());
        deviceStatistics.put("RoomState",jsonResponse.at("/Device/SchedulingPanel/Monitoring/RoomStatus/State").asText());
        deviceStatistics.put("ConnectionStatusMessage",jsonResponse.at("/Device/SchedulingPanel/Monitoring/Scheduling/ConnectionStatusMessage").asText());
        deviceStatistics.put("CalendarSyncStatus",jsonResponse.at("/Device/SchedulingPanel/Monitoring/Scheduling/CalendarSyncStatus").asText());
        deviceStatistics.put("PanelSyncing",timeInThreshold(jsonResponse.at("/Device/SchedulingPanel/Monitoring/Scheduling/CalendarSyncStatus").asText().substring(20)));
        deviceStatistics.put("ConnectionStatus",jsonResponse.at("/Device/SchedulingPanel/Monitoring/Scheduling/ConnectionStatus").asText());
        deviceStatistics.put("ExchangeUsername",jsonResponse.at("/Device/SchedulingPanel/Config/Scheduling/Exchange/Username").asText());
        deviceStatistics.put("PufVersion",jsonResponse.at("/Device/DeviceInfo/PufVersion").asText());
        deviceStatistics.put("MacAddress",jsonResponse.at("/Device/DeviceInfo/MacAddress").asText());
        deviceStatistics.put("SerialNumber",jsonResponse.at("/Device/DeviceInfo/SerialNumber").asText());
        deviceStatistics.put("DeviceName",jsonResponse.at("/Device/DeviceInfo/Name").asText());
        deviceStatistics.put("BuildDate",jsonResponse.at("/Device/DeviceInfo/BuildDate").asText());
        deviceStatistics.put("RebootReason",jsonResponse.at("/Device/DeviceInfo/RebootReason").asText());

        deviceStatistics.put("reboot","0");
        deviceControls.put("reboot", "push");

        statistics.setStatistics(deviceStatistics);
        statistics.setControl(deviceControls);
        return singletonList(statistics);
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
            return "Unable to parse time in string: \"" + dateString + "\"";
        }
    }

    @Override
    public void controlProperty(ControllableProperty controllableProperty){
        if (controllableProperty == null)
            return;

        if (controllableProperty.getProperty().equalsIgnoreCase("reboot")) {
            try {
                HttpResponse response = doPost("Device/DeviceOperations", "{\"Device\":{\"DeviceOperations\":{\"Reboot\":true}}}"); //send reboot command to the device
                if (!response.getResponseBody().contains("OK"))
                    this.logger.warn("Device Reboot failed with response from device: " + response.getResponseBody());
            } catch (Exception e) {
                this.logger.warn("Failed to reboot device: " + e);
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
        device.setHost("192.168.0.51");
        device.setLogin("admin");
        device.setPassword("19881988");
        device.init();
        ExtendedStatistics stats = (ExtendedStatistics)device.getMultipleStatistics().get(0);
        stats.getStatistics().forEach((k,v) ->{
            System.out.println(k + " : " + v);
        });
    }
}
