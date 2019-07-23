package com.manual;

import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;

/**
 * Created by bwelton on 9/28/18.
 */

public class EventLogger implements Serializable {
    public static final int YAW_THROTTLE_START = 0;
    public static final int YAW_THROTTLE_END = 1;
    public static final int ROLL_PITCH_START = 2;
    public static final int ROLL_PITCH_END = 3;

    private static final String SD_PATH = "/storage/A9B1-0805/Android/data/com.manual/files/";

    private FileWriter writer;

    public EventLogger(String filename){
        try {
            File path = new File(SD_PATH);
            if(!path.exists()){
                path.mkdir();
            }
            writer = new FileWriter(
                    new File(SD_PATH + filename));
            buildFileHeaders();
        } catch (IOException e) {
            Log.d("DroneLogging", "Failed to initialize the file writer.");
            e.printStackTrace();
        }
    }


    private void buildFileHeaders(){
        try {
            writer.append("Current Time (s),");
            writer.append("Flight Time (s),");
            writer.append("Altitude (m),");
            writer.append("X-Position (m),");
            writer.append("Y-Position (m),");
            writer.append("Event\n");

        } catch (IOException e) {
            Log.d("DroneLogging", "Failed to initialize the headers");
            e.printStackTrace();
        }
    }

    protected void logEvent(Telemetry.AirCraft aircraft, int event) {
        try {
            writer.append(Long.toString(System.currentTimeMillis()/1000));
            writer.append(",");
            writer.append(aircraft.RawFlightTime);
            writer.append(",");
            writer.append(aircraft.RawAltitude);
            writer.append(",");
            writer.append(Double.toString(aircraft.Position.latitude));
            writer.append(",");
            writer.append(Double.toString(aircraft.Position.longitude));
            writer.append(",");
            writer.append(Integer.toString(event));
            writer.append("\n");
            writer.flush();

        } catch (IOException e) {
            Log.d("DroneLogging", "Failed to append a new line of data.");
            e.printStackTrace();
        }
    }

    protected void closeLogger(){
        try {
            writer.flush();
            writer.close();
        } catch (IOException e) {
            Log.d("DroneLogging", "Failed to close the file writer.");
            e.printStackTrace();
        }
    }
}
