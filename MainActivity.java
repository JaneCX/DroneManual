/*
 * Copyright (C) 2014 Savas Sen - ENAC UAV Lab
 *
 * This file is part of paparazzi..
 *
 * paparazzi is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * paparazzi is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with paparazzi; see the file COPYING.  If not, write to
 * the Free Software Foundation, 59 Temple Place - Suite 330,
 * Boston, MA 02111-1307, USA.
 *
 */

/*
 * This is the main Activity class
 *
 * Structure of this file is as follows:
 *  -methods for intialization
 *  -refresh methods
 *  -onCreate, onStart, and other activity lifecycle
 *  -UI methods
 *  -methods added by HAL
 *
 * Additions for inspection mode button are in the setup. Additions for specific block functionality
 * are in the methods dealing with the block counter towards the beginning. Additions for the marker
 * functionality can be found in the setup method for the map. Most remaining additions are simply
 * in the methods added by HAL
 *
 * NOTE: The additions made for HAL's supervisory variant are as follow
 *     --ability to add waypoints
 *     --mapping of latitudes onto a ground overlay of the room
 *     --showing only certain waypoints, namely the ORIGIN
 *     --only show five specific blocks, one of which loops through new waypoints
 *     --custom dialogs for wp removal and altitude adjustment
 *     --timer has been shortened from 3000 ms to 900 ms
 *     --lines connecting waypoints, are removed once subsequent wp is reached
 *     --reached wps are removed
 *     --icon now displays altitude rather than title
 *     --PFD can be turned off with checkbox toggle
 *     --landed, flare, and HOME blocks are not loaded into the block list adaptor
 *     --special case added so that PAUSE block does not trigger timer
 *     --button added to launch inspection mode
 *     --STATIC snippet added to important wps so that they cannot be removed
 *          --current implementation of this does require that all markers are initialized w/ snippets
 *     --Bigger AP information in the top left corner
 *
 *     The block ID and waypoint ID relevant to the looping through the added waypoints uses BlocId
 *     6 and waypoint 7. These have been hard coded into the activity and are flight plan specific
 *     to the ARdrone2 vicon_rotorcraft as of 6/1/17. If that flight plan is modified, the relevant
 *     ID numbers MUST be recalculated. The same is true for the selected visible waypoints. This
 *     all takes place in the set_selected_block method, hopefully a more elegant fix can be
 *     developed at some point. Note that there is also a hard-coded component for the special case
 *     PAUSE timer prevention which is activated in the setup_command_list method. Inspection mode
 *     would also need to be adjusted
 */

package com.manual;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.preference.PreferenceManager;
import android.support.v4.widget.DrawerLayout;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.NumberPicker;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMapOptions;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.GroundOverlay;
import com.google.android.gms.maps.model.GroundOverlayOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.io.IOException;
import java.net.DatagramSocket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import static java.lang.Double.parseDouble;
import android.view.MotionEvent;

import org.videolan.libvlc.IVideoPlayer;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.LibVlcException;


public class MainActivity extends Activity implements SharedPreferences.OnSharedPreferenceChangeListener, IVideoPlayer {

  	//TODO ! FLAG MUST BE 'FALSE' FOR PLAY STORE!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
  	boolean DEBUG=false;
	public final int InspectionPosition = 1;

  	//Application Settings
  	public static final String SERVER_IP_ADDRESS = "server_ip_adress_text";
  	public static final String SERVER_PORT_ADDRESS = "server_port_number_text";
  	public static final String LOCAL_PORT_ADDRESS = "local_port_number_text";
  	public static final String MIN_AIRSPEED = "minimum_air_speed";
  	public static final String USE_GPS = "use_gps_checkbox";
  	public static final String Control_Pass = "app_password";
  	public static final String BLOCK_C_TIMEOUT = "block_change_timeout";
  	public static final String DISABLE_SCREEN_DIM = "disable_screen_dim";
  	public static final String DISPLAY_FLIGHT_INFO = "show_flight_info";

  	private static final int MAX_USER_ID = 42;

	public Telemetry AC_DATA;                       //Class to hold&proces AC Telemetry Data
  	boolean ShowOnlySelected = true;
	boolean isClicked = false;
  	String AppPassword;
	public int AcId = 31;

  	//AC Blocks
  	ArrayList<BlockModel> BlList = new ArrayList<BlockModel>();
  	BlockListAdapter mBlListAdapter;
  	ListView BlListView;
  	SharedPreferences AppSettings;                  //App Settings Data
  	Float MapZoomLevel = 14.0f;

  	//Ac Names
  	ArrayList<Model> AcList = new ArrayList<Model>();
  	AcListAdapter mAcListAdapter;
  	ListView AcListView;
  	boolean TcpSettingsChanged;
  	boolean UdpSettingsChanged;

  	//UI components (needs to be bound onCreate
  	private GoogleMap mMap;
  	TextView TextViewAltitude;
	TextView TextViewFlightTime;
	TextView TextViewBattery;
  	private ImageView batteryLevelView;

	private Button Button_ConnectToServer, Button_LaunchInspectionMode;
  	public Button Button_Takeoff, Button_Execute, Button_Pause, Button_LandHere, map_swap;

  	private ToggleButton ChangeVisibleAcButon;
  	private DrawerLayout mDrawerLayout;

	public int percent = 100;
	boolean lowBatteryUnread = true;
	boolean emptyBatteryUnread = true;

  	private String SendStringBuf;
  	private boolean AppStarted = false;             //App started indicator
  	private CharSequence mTitle;

  	//Variables for adding marker feature and for connecting said markers
  	public LinkedList<Marker> mMarkerHead = new LinkedList<Marker>();
  	public LinkedList<LatLng> pathPoints = new LinkedList<LatLng>();
    public int mrkIndex = 0;
    public double lastAltitude = 1.0;
  	public Polyline path;
    public static Polyline path_s;///////////////////////////////////////////////////////////////
  	public boolean pathInitialized = false;
    public LatLng originalPosition;
    public static LatLng originalPosition_s;/////////////////////////////////////////////////////////////
    private int mapIndex = 0;
    public static int mapIndex_s = 0;
    private int[] mapImages = {
            R.drawable.empty_room,
            R.drawable.check_ride,
            R.drawable.experiment,
            R.drawable.check_ride_height,
            R.drawable.experiment_height};
    private GroundOverlay trueMap;

  	//Establish static socket to be used across activities
  	static DatagramSocket sSocket = null;

  	//Unused, but potentially useful ground offset value to adjust the fact that the lab is recorded
  	// as below 0m
  	final float GROUND_OFFSET = .300088f;
  	//>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
  	//Background task to read and write telemery msgs
  	private boolean isTaskRunning;

  	private boolean DisableScreenDim;
  	private boolean DisplayFlightInfo;

  	private ArrayList<Model> generateDataAc() {
    	AcList = new ArrayList<Model>();
    	return AcList;
  	}

  	private ArrayList<BlockModel> generateDataBl() {
    	BlList = new ArrayList<BlockModel>();
    	return BlList;
  	}

  	private Thread mTCPthread;
  	static EventLogger logger;

  	/**
  	 * Setup TCP and UDP connections of Telemetry class
  	 */
  	private void setup_telemetry_class() {

    	//Create Telemetry class
    	AC_DATA = new Telemetry();

    	//Read & setup Telemetry class
    	AC_DATA.ServerIp = "192.168.50.10";
    	AC_DATA.ServerTcpPort = Integer.parseInt(AppSettings.getString(SERVER_PORT_ADDRESS, getString(R.string.pref_port_number_default)));
    	AC_DATA.UdpListenPort = Integer.parseInt(AppSettings.getString(LOCAL_PORT_ADDRESS, getString(R.string.pref_local_port_number_default)));
    	AC_DATA.AirSpeedMinSetting = parseDouble(AppSettings.getString(MIN_AIRSPEED, "10"));
    	AC_DATA.DEBUG=DEBUG;

    	AC_DATA.GraphicsScaleFactor = getResources().getDisplayMetrics().density;
    	AC_DATA.prepare_class();

    	//AC_DATA.tcp_connection();
    	//AC_DATA.mTcpClient.setup_tcp();
    	AC_DATA.setup_udp();
  	}

  	/**
  	 * Bound UI items
  	 */
  	private void set_up_app() {

      	//Get app settings
      	AppSettings = PreferenceManager.getDefaultSharedPreferences(this);
      	AppSettings.registerOnSharedPreferenceChangeListener(this);

      	AppPassword = "1234";

      	DisableScreenDim = AppSettings.getBoolean("disable_screen_dim", true);

      	//Setup left drawer
      	mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);

      	//Setup AC List
      	setup_ac_list();

      	//Setup Block counter
	  	//setup_counter();

	  	//continue to bound UI items
	  	TextViewAltitude = (TextView) findViewById(R.id.Alt_On_Map);
		TextViewBattery = (TextView) findViewById(R.id.Bat_Vol_On_Map);
		TextViewFlightTime = (TextView) findViewById(R.id.Flight_Time_On_Map);
		batteryLevelView = (ImageView) findViewById(R.id.batteryImageView);

	  	Button_ConnectToServer = (Button) findViewById(R.id.Button_ConnectToServer);
	  	setup_map_ifneeded();

	  	Button_LaunchInspectionMode = (Button) findViewById(R.id.InspectionMode);
	  	Button_LaunchInspectionMode.setOnClickListener(new View.OnClickListener() {
		  @Override
		  public void onClick(View view) {
			  if(!isClicked) {
				  isClicked = true;
                  logger.logEvent(AC_DATA.AircraftData[0], EventLogger.INSPECTION_LAUNCH, -1);
                  send_to_server("PPRZonDroid JUMP_TO_BLOCK " + AcId + " " + 9, true);
				  new CountDownTimer(1000, 100) {
					  @Override
					  public void onTick(long l) {
					  }

					  @Override
					  public void onFinish() {
						  String url = "file:///sdcard/DCIM/video1.sdp";
						  Intent inspect = new Intent(getApplicationContext(), InspectionMode.class);
						  inspect.putExtra("videoUrl", url);
						  startActivityForResult(inspect, InspectionPosition);
					  }
				  }.start();
			  }
		  }
	  });

	  	Button_Takeoff = (Button) findViewById(R.id.takeoff);
	  	Button_Execute = (Button) findViewById(R.id.execute_flightplan);
	  	Button_Pause = (Button) findViewById(R.id.pause_flightplan);
	  	Button_LandHere = (Button) findViewById(R.id.land_here);
        map_swap = (Button) findViewById(R.id.map_swap);

	  	Button_Takeoff.setOnTouchListener(new View.OnTouchListener() {
		  @Override
		  public boolean onTouch(View v, MotionEvent event) {
			  clear_buttons();
			  if(event.getAction() == MotionEvent.ACTION_DOWN) logger.logEvent(AC_DATA.AircraftData[0], EventLogger.TAKEOFF, -1);
			  set_selected_block(0,false);
			  Button_Takeoff.setSelected(true);
			  return false;
		  }
	  });

	  	Button_Execute.setOnTouchListener(new View.OnTouchListener() {
		  @Override
		  public boolean onTouch(View v, MotionEvent event) {
			  clear_buttons();
              if(event.getAction() == MotionEvent.ACTION_DOWN) logger.logEvent(AC_DATA.AircraftData[0], EventLogger.EXECUTE, -1);
			  Button_Execute.setSelected(true);
			  set_selected_block(1,false);
			  return false;
		  }
	  });

	  	Button_Pause.setOnTouchListener(new View.OnTouchListener() {
		  @Override
		  public boolean onTouch(View v, MotionEvent event) {
			  clear_buttons();
              if(event.getAction() == MotionEvent.ACTION_DOWN) logger.logEvent(AC_DATA.AircraftData[0], EventLogger.PAUSE, -1);
              Button_Pause.setSelected(true);
			  set_selected_block(2, false);
			  return false;
		  }
	  });

	  	Button_LandHere.setOnTouchListener(new View.OnTouchListener() {
		  @Override
		  public boolean onTouch(View v, MotionEvent event) {
			  clear_buttons();
              if(event.getAction() == MotionEvent.ACTION_DOWN) logger.logEvent(AC_DATA.AircraftData[0], EventLogger.LANDING, -1);
              set_selected_block(3,false);
			  Button_LandHere.setSelected(true);
			  return false;
		  }
	  });

        map_swap.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if(++mapIndex >= mapImages.length) mapIndex = 0;
                BitmapDescriptor newLabImage = BitmapDescriptorFactory.fromResource(mapImages[mapIndex]);
                trueMap.setImage(newLabImage);
                return false;
            }
        });

	  	ChangeVisibleAcButon = (ToggleButton) findViewById(R.id.toggleButtonVisibleAc);
	  	ChangeVisibleAcButon.setSelected(false);
        mapIndex_s = mapIndex;
  	}


  //largely unnecessary, this was to allow multiple drones to load at the same time, left menu
  private void setup_ac_list() {
      mAcListAdapter = new AcListAdapter(this, generateDataAc());

      // if extending Activity 2. Get ListView from activity_main.xml
      AcListView = (ListView) findViewById(R.id.AcList);
      View AppControls = getLayoutInflater().inflate(R.layout.appcontrols, null);

      //First item will be app controls
      //This workaround applied to for app controls sliding to top if user slides them.
      AcListView.addHeaderView(AppControls);

      // 3. setListAdapter
      AcListView.setAdapter(mAcListAdapter);
      //Create onclick listener
      AcListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
          if (position >= 1) {
           view.setSelected(true);
           set_selected_ac(position - 1,true);
           mDrawerLayout.closeDrawers();
           }

            }
        });

  }

    private void set_selected_block(int BlocId,boolean ReqFromServer) {

        //AC_DATA.AircraftData[AC_DATA.SelAcInd].SelectedBlock = BlocId;

        if (ReqFromServer){
			//if in standby, takeoff is finished so clear the button
			if(BlocId == 4){
				Button_Takeoff.setSelected(false);
			}

            //this checks to see if the server is at the "Arrived" block, in which case we shift back
            //to the "Next Waypoint" block which is handled locally rather than with the reqfromserver tag
            else if(BlocId == 7){

            /* current implementation removes the connecting polyline from previous wp
             *  as well as that previous wp */

                Marker popped = mMarkerHead.removeFirst();
                popped.remove();
                mrkIndex--;

                pathPoints.removeFirst();
                adjust_marker_lines();

                //code below would be used to change an old waypoint grey rather than removing it
//              popped.setIcon(BitmapDescriptorFactory.fromBitmap(AC_DATA.muiGraphics.create_marker_icon(
//                    "grey", popped.getTitle(), AC_DATA.GraphicsScaleFactor)));

                //timer used to pause between wps to give the user some breathing room
                new CountDownTimer(500, 100){
                    @Override
                    public void onTick(long l) {
                    }
                    @Override
                    public void onFinish() {
                        //continues the loop by updating next waypoint
                        set_selected_block(1, false);
                    }
                }.start();
            }

            //adjust path lines if pause is triggered
            else if (BlocId == 8 && pathPoints.size()>1){
                pathPoints.removeFirst();
                pathPoints.addFirst(AC_DATA.AircraftData[0].AC_Carrot_Marker.getPosition());
                adjust_marker_lines();
            }

            //if landed, clear button selections
            else if(BlocId == 12){
				clear_buttons();
			}
        }
        else if(BlocId == 0){
            //start engine
            send_to_server("PPRZonDroid JUMP_TO_BLOCK " + AC_DATA.AircraftData[AC_DATA.SelAcInd].AC_Id + " " + 2, true);
            //wait for paparazzi to register command
            new CountDownTimer(1000,1000){
                @Override
                public void onTick(long l) {}

                @Override
                public void onFinish(){
                    //takeoff
                    send_to_server("PPRZonDroid JUMP_TO_BLOCK " + AC_DATA.AircraftData[AC_DATA.SelAcInd].AC_Id + " " + 3, true);
                }
            }.start();
        }
        //updates the flightplan to head to the next user-designated waypoint
        else if(BlocId == 1 && mMarkerHead.peek() != null){
            Marker newNEXT = mMarkerHead.peek();
            LatLng coordNEXT = convert_to_google(newNEXT.getPosition());
            float altitude = Float.parseFloat(newNEXT.getSnippet());
            float adjustedAltitude = altitude - GROUND_OFFSET;
            //Note below that 31 or 203 is the AC id for our ardrone and 7 is the waypoint number for NEXT
            SendStringBuf = "PPRZonDroid MOVE_WAYPOINT " + AcId + " " + 7 +
                    " " + coordNEXT.latitude + " " + coordNEXT.longitude + " " + adjustedAltitude;
            send_to_server(SendStringBuf, true);

            //timer is needed to ensure paparazzi system corrects waypoint first
            new CountDownTimer(1000, 1000){
                @Override
                public void onTick(long l) {}

                @Override
                public void onFinish() {
                    send_to_server("PPRZonDroid JUMP_TO_BLOCK " + AcId + " " + 6, true);
                }
            }.start();
        }
        else if(BlocId == 1 && mMarkerHead.peek() == null && pathInitialized){
            Toast.makeText(getApplicationContext(), "No waypoints to fly to!", Toast.LENGTH_SHORT).show();
            pathPoints.clear();
            adjust_marker_lines();
        }
        //pause flightplan
        else if(BlocId == 2){
            send_to_server("PPRZonDroid JUMP_TO_BLOCK " + AC_DATA.AircraftData[AC_DATA.SelAcInd].AC_Id + " " + 9, true);
        }
        //land here
        else if(BlocId == 3){
            send_to_server("PPRZonDroid JUMP_TO_BLOCK " + AC_DATA.AircraftData[AC_DATA.SelAcInd].AC_Id + " " + 10, true);
        }
    }

//called if different ac is selected in the left menu
  private void set_selected_ac(int AcInd,boolean centerAC) {

    AC_DATA.SelAcInd = AcInd;
    //Set Title;
    setTitle(AC_DATA.AircraftData[AC_DATA.SelAcInd].AC_Name);

    //refresh_block_list();
    set_marker_visibility();

    for (int i = 0; i <= AC_DATA.IndexEnd; i++) {
      //Is AC ready to show on ui?
      //Check if ac i visible and its position is changed
      if (AC_DATA.AircraftData[i].AC_Enabled && AC_DATA.AircraftData[i].isVisible && AC_DATA.AircraftData[i].AC_Marker != null) {

        AC_DATA.AircraftData[i].AC_Logo = AC_DATA.muiGraphics.create_ac_icon(AC_DATA.AircraftData[i].AC_Type, AC_DATA.AircraftData[i].AC_Color, AC_DATA.GraphicsScaleFactor, (i == AC_DATA.SelAcInd));
        BitmapDescriptor bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(AC_DATA.AircraftData[i].AC_Logo);
        AC_DATA.AircraftData[i].AC_Marker.setIcon(bitmapDescriptor);

      }
    }
      mAcListAdapter.SelectedInd = AcInd;
      mAcListAdapter.notifyDataSetChanged();
      refresh_ac_list();
  }

  //Bound map (if not bounded already)
  private void setup_map_ifneeded() {
    // Do a null check to confirm that we have not already instantiated the map.
    if (mMap == null) {
      // Try to obtain the map from the SupportMapFragment.
      mMap = ((MapFragment) getFragmentManager()
              .findFragmentById(R.id.map)).getMap();
      // Check if we were successful in obtaining the map.
      if (mMap != null) {
        setup_map();
      }
    }
  }

  //Setup map & marker components
  private void setup_map() {

      GoogleMapOptions mMapOptions = new GoogleMapOptions();

      //Read device settings for Gps usage.
      mMap.setMyLocationEnabled(false);

      //Set map type
      mMap.setMapType(GoogleMap.MAP_TYPE_NONE);

      //Disable zoom and gestures to lock the image in place
      mMap.getUiSettings().setAllGesturesEnabled(false);
      mMap.getUiSettings().setZoomGesturesEnabled(false);
      mMap.getUiSettings().setZoomControlsEnabled(false);
      mMap.getUiSettings().setCompassEnabled(false);
      mMap.getUiSettings().setTiltGesturesEnabled(false);
	  mMap.getUiSettings().setMyLocationButtonEnabled(false);

      //Set zoom level and the position of the lab
      LatLng labOrigin = new LatLng(36.005417, -78.940984);
      mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(labOrigin, 50));
      CameraPosition rotated = new CameraPosition.Builder()
              .target(labOrigin)
              .zoom(50)
              .bearing(90.0f)
              .build();
      mMap.moveCamera(CameraUpdateFactory.newCameraPosition(rotated));

      //Create the ground overlay
      BitmapDescriptor labImage = BitmapDescriptorFactory.fromResource(mapImages[mapIndex]);
      trueMap = mMap.addGroundOverlay(new GroundOverlayOptions()
              .image(labImage)
              .position(labOrigin, (float) 46)   //note if you change size of map you need to redo this val too
              .bearing(90.0f));


      //Setup markers drag listeners that update polylines when moved
      mMap.setOnMarkerDragListener(new GoogleMap.OnMarkerDragListener() {

          @Override
          public void onMarkerDragStart(Marker marker) {
              originalPosition = marker.getPosition();
              int index = mMarkerHead.indexOf(marker);
              pathPoints.set(index + 1, marker.getPosition());
              path.setPoints(pathPoints);
          }


          @Override
          public void onMarkerDrag(Marker marker) {
              if(outsideBounds(marker.getPosition())) marker.setVisible(false);
              else marker.setVisible(true);

              int index = mMarkerHead.indexOf(marker);
              pathPoints.set(index + 1, marker.getPosition());
              path.setPoints(pathPoints);
          }

          @Override
          public void onMarkerDragEnd(Marker marker) {
              if(outsideBounds(marker.getPosition())){
                  int index = mMarkerHead.indexOf(marker);
                  launch_error_dialog();
                  marker.setPosition(originalPosition);
                  marker.setVisible(true);
                  pathPoints.set(index + 1, marker.getPosition());
                  path.setPoints(pathPoints);
              }
              else if(!mMarkerHead.contains(marker)) {
                  // this is code from the original app, works well but we don't want to allow users
                  // to change predesignated waypoints that we choose to show them

                  // waypoint_changed(marker.getId(), marker.getPosition(), "Waypoint changed");
             }
                //else statement ensures we do not send a paparazzi message for a waypoint that doesn't exist
              else{
                  logger.logWaypointEvent(
                          AC_DATA.AircraftData[0],
                          EventLogger.WAYPOINT_MOVE,
                          -1,
                          originalPosition,
                          marker.getPosition(),
                          marker.getSnippet(),
                          null);
                  launch_altitude_dialog(marker, "OLD");
              }
      }
    });

    mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
        @Override
        public void onMapClick(LatLng latLng) {
            Point markerScreenPosition = mMap.getProjection().toScreenLocation(latLng);
            Log.d("location", "x: " + markerScreenPosition.x+ "     y: " + markerScreenPosition.y);
            if(markerScreenPosition.x==1285 || markerScreenPosition.x == 1286 || markerScreenPosition.x == 1284){
                Log.d("location", "x: " + latLng.latitude+ "     y: " + latLng.longitude + " " + markerScreenPosition.x);
            }
        }
    });

    //listener to add in functionality of adding a waypoint and adding to data structure for
    //path execution
    mMap.setOnMapLongClickListener(new GoogleMap.OnMapLongClickListener() {
        @Override
        public void onMapLongClick(LatLng latLng) {
            if(outsideBounds(latLng)) return;
            Marker newMarker = mMap.addMarker(new MarkerOptions()
                .position(latLng)
                .draggable(true)
                .anchor(0.5f, 0.378f)
                .title(Integer.toString(mrkIndex + 1))
                .snippet(Float.toString(1.0f))
                .icon(BitmapDescriptorFactory.fromBitmap(AC_DATA.muiGraphics.create_marker_icon(
                    "red", "?", AC_DATA.GraphicsScaleFactor))));
            launch_altitude_dialog(newMarker, "NEW");


            mMarkerHead.add(newMarker);
			if((mrkIndex == 0)){
                pathPoints.clear();
                pathPoints.addFirst(AC_DATA.AircraftData[0].AC_Carrot_Marker.getPosition());
                pathPoints.addLast(newMarker.getPosition());
				path = mMap.addPolyline(new PolylineOptions()
                        .addAll(pathPoints)
						.width(9)
						.color(Color.RED));
			}
			else{
                pathPoints.addLast(newMarker.getPosition());
				path.setPoints(pathPoints);
			}
            mrkIndex++;
        }
    });

    //listener to add in remove on click functionality and altitude control
    mMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
        public boolean onMarkerClick(final Marker marker){
            //if statement prevents removal of origin and drone icon
            if(!marker.getSnippet().equals("STATIC")) {
                AlertDialog adjustDialog = new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Adjust Waypoint")
                        .setMessage("Click below to adjust the altitude or remove the waypoint")
                        .setNegativeButton("Cancel", null)
                        .setNeutralButton("Altitude", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                launch_altitude_dialog(marker, "OLD");
                            }
                        })
                        .setPositiveButton("Remove", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                remove_waypoint_dialog(marker);
                            }
                        })
                        .create();
                adjustDialog.show();
            }
            return true;
        }
    });

  }


  /**
   * Send string to server
   *
   * @param StrToSend
   * @param ControlString Whether String is to control AC or request Ac data.If string is a control string then app pass will be send also
   */
  private void send_to_server(String StrToSend, boolean ControlString) {
    //Is it a control string ? else ->Data request
    if (ControlString) {
      AC_DATA.SendToTcp = AppPassword + " " + StrToSend;
    } else {
      AC_DATA.SendToTcp = StrToSend;
    }
  }

  /**
   * Play warning sound if airspeed goes below the selected value, unused but good example, could
   * be useful
   */
  public void play_sound(Context context) throws IllegalArgumentException,
          SecurityException,
          IllegalStateException,
          IOException {

    Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
    MediaPlayer mMediaPlayer = new MediaPlayer();
    mMediaPlayer.setDataSource(context, soundUri);
    final AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    //Set volume max!!!
    audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, audioManager.getStreamMaxVolume(audioManager.STREAM_SYSTEM), 0);

    if (audioManager.getStreamVolume(AudioManager.STREAM_SYSTEM) != 0) {
      mMediaPlayer.setAudioStreamType(AudioManager.STREAM_SYSTEM);
      mMediaPlayer.setLooping(true);
      mMediaPlayer.prepare();
      mMediaPlayer.start();
    }
  }

  private void refresh_ac_list() {
    //Create or edit aircraft list
    int i;
    for (i = 0; i <= AC_DATA.IndexEnd; i++) {


      if (AC_DATA.AircraftData[i].AC_Enabled) {
        AcList.set(i, new Model(AC_DATA.AircraftData[i].AC_Logo, AC_DATA.AircraftData[i].AC_Name, AC_DATA.AircraftData[i].Battery));

      } else {
        if (AC_DATA.AircraftData[i].AcReady) {
          AcList.add(new Model(AC_DATA.AircraftData[i].AC_Logo, AC_DATA.AircraftData[i].AC_Name, AC_DATA.AircraftData[i].Battery));
          AC_DATA.AircraftData[i].AC_Enabled = true;
        } else {
          //AC data is not ready yet this should be
          return;
        }
      }
    }

  }


  /**
   * Add markers of given AcIndex to map. AcIndex is not Ac ID!!! Ac ID for our drone is 31 or 203,
   * AcIndex is 0 as it is the first and only drone in the aircraftdata. Feel free to remove the
   * functionality of having multiple drones if you want.
   */
  private void add_markers_2_map(int AcIndex) {


    if (AC_DATA.AircraftData[AcIndex].isVisible && AC_DATA.AircraftData[AcIndex].AC_Enabled) {
      AC_DATA.AircraftData[AcIndex].AC_Marker = mMap.addMarker(new MarkerOptions()
                      .position(convert_to_lab(AC_DATA.AircraftData[AcIndex].Position))
                      .anchor((float) 0.5, (float) 0.5)
                      .flat(true).rotation(Float.parseFloat(AC_DATA.AircraftData[AcIndex].Heading))
                      .title(AC_DATA.AircraftData[AcIndex].AC_Name)
                      .draggable(false)
                      .snippet("STATIC")
                      .icon(BitmapDescriptorFactory.fromBitmap(AC_DATA.AircraftData[AcIndex].AC_Logo))
      );

      AC_DATA.AircraftData[AcIndex].AC_Carrot_Marker = mMap.addMarker(new MarkerOptions()
                      .position(convert_to_lab(AC_DATA.AircraftData[AcIndex].AC_Carrot_Position))
                      .anchor((float) 0.5, (float) 0.5)
                      .draggable(false)
                      .snippet("STATIC")
                      .icon(BitmapDescriptorFactory.fromBitmap(AC_DATA.AircraftData[AcIndex].AC_Carrot_Logo))
      );
    }
  }

  //Refresh markers, the updates here to the ac icon are used but everything else remains from if
	//we ever wanted to reimplement the ability to move markers loaded from flightplan
  private void refresh_markers() {
    int i;

    for (i = 0; i <= AC_DATA.IndexEnd; i++) {

      //Is AC ready to show on ui?
      //if (!AC_DATA.AircraftData[i].AC_Enabled)  return;

      if (null == AC_DATA.AircraftData[i].AC_Marker) {
        add_markers_2_map(i);
      }

      //Check if ac i visible and its position is changed
      if (AC_DATA.AircraftData[i].AC_Enabled && AC_DATA.AircraftData[i].isVisible && AC_DATA.AircraftData[i].AC_Position_Changed) {
        AC_DATA.AircraftData[i].AC_Marker.setPosition(convert_to_lab(AC_DATA.AircraftData[i].Position));
        AC_DATA.AircraftData[i].AC_Marker.setRotation(Float.parseFloat(AC_DATA.AircraftData[i].Heading));
        AC_DATA.AircraftData[i].AC_Carrot_Marker.setPosition(convert_to_lab(AC_DATA.AircraftData[i].AC_Carrot_Position));
        AC_DATA.AircraftData[i].AC_Position_Changed = false;
      }

    }

    //Check markers
    if (AC_DATA.NewMarkerAdded)   //Did we add any markers?
    {
      int AcInd;
      for (AcInd = 0; AcInd <= AC_DATA.IndexEnd; AcInd++) {     //Search thru all aircrafts and check if they have marker added flag

        if (AC_DATA.AircraftData[AcInd].NewMarkerAdded) {   //Does this aircraft has an added marker data?
          int MarkerInd = 1;
          //Log.d("PPRZ_info", "trying to show ac markers of "+AcInd);
          //Search aircraft markers which has name but doesn't have marker
          for (MarkerInd = 1; (MarkerInd < AC_DATA.AircraftData[AcInd].NumbOfWps - 1); MarkerInd++) {

              if ((AC_DATA.AircraftData[AcInd].AC_Markers[MarkerInd].WpPosition == null) || (AC_DATA.AircraftData[AcInd].AC_Markers[MarkerInd].WpMarker != null))
                  continue; //we dont have data for this wp yet

              if (DEBUG) Log.d("PPRZ_info", "New marker added for Ac id: " + AcInd + " wpind:" + MarkerInd);

            AC_DATA.AircraftData[AcInd].AC_Markers[MarkerInd].MarkerModified = false;
          }


          AC_DATA.AircraftData[AcInd].NewMarkerAdded = false;
        }
      }

      AC_DATA.NewMarkerAdded = false;
    }

    //Handle marker modified msg
    if (AC_DATA.MarkerModified)   //
    {

      int AcInd;
      for (AcInd = 0; AcInd <= AC_DATA.IndexEnd; AcInd++) {     //Search thru all aircrafts and check if they have marker added flag

        if (AC_DATA.AircraftData[AcInd].MarkerModified) {   //Does this aircraft has an added marker data?
          if (DEBUG) Log.d("PPRZ_info", "Marker modified for AC= " + AcInd);
          int MarkerInd = 1;
          //if (DEBUG) Log.d("PPRZ_info", "trying to show ac markers of "+AcInd);
          //Search aircraft markers which has name but doesn't have marker
          for (MarkerInd = 1; (MarkerInd < AC_DATA.AircraftData[AcInd].NumbOfWps - 1); MarkerInd++) {
            //Log.d("PPRZ_info", "Searching Marker for AC= " + AcInd + " wpind:" + MarkerInd);
            if ((AC_DATA.AircraftData[AcInd].AC_Markers[MarkerInd] == null) || !(AC_DATA.AircraftData[AcInd].AC_Markers[MarkerInd].MarkerModified) || (null == AC_DATA.AircraftData[AcInd].AC_Markers[MarkerInd].WpMarker))
              continue; //we dont have data for this wp yet

            //Set new position for marker
            AC_DATA.AircraftData[AcInd].AC_Markers[MarkerInd].WpMarker.setPosition(convert_to_lab(AC_DATA.AircraftData[AcInd].AC_Markers[MarkerInd].WpPosition));

            //Clean MarkerModified flag
            AC_DATA.AircraftData[AcInd].AC_Markers[MarkerInd].MarkerModified = false;
            if (DEBUG) Log.d("PPRZ_info", "Marker modified for acid: " + AcInd + " wpind:" + MarkerInd);

          }
          //Clean AC MarkerModified flag
          AC_DATA.AircraftData[AcInd].MarkerModified = false;

        }
      }
      //Clean Class MarkerModified flag
      AC_DATA.MarkerModified = false;

    }

    AC_DATA.ViewChanged = false;
  }

  private boolean checkReady() {
    if (mMap == null) {
      Toast.makeText(this, R.string.map_not_ready, Toast.LENGTH_SHORT).show();
      return false;
    }
    return true;
  }

  public void onResetMap(View view) {
    if (!checkReady()) {
      return;
    }
    // Clear the map because we don't want duplicates of the markers.
    mMap.clear();
    //addMarkersToMap();
  }

  //for the three below functions, we are not using the action bar. This allows a settings tab
  //if we were to have one
  @Override
  public void setTitle(CharSequence title) {
//    mTitle = title;
//    getActionBar().setTitle(mTitle);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    // Inflate the menu; this adds items to the action bar if it is present.
    //getMenuInflater().inflate(R.menu.main, menu);
    return true;
  }

  @Override
  protected void onStop() {
    super.onStop();

    // We need an Editor object to make preference changes.
    // All objects are from android.context.Context

    SharedPreferences.Editor editor = AppSettings.edit();

    editor.putFloat("MapZoomLevel", MapZoomLevel);

    // Commit the edits!
    editor.commit();
    AC_DATA.mTcpClient.sendMessage("removeme");

    //note that we must trigger stop to allow new connection in inspection mode
    AC_DATA.mTcpClient.stopClient();
    isTaskRunning= false;

    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

  }

    @Override
    protected void onRestart() {
        super.onRestart();
        AC_DATA.setup_udp();
		isClicked = false;
        //Force to reconnect
        //TcpSettingsChanged = true;
        TelemetryAsyncTask = new ReadTelemetry();
        TelemetryAsyncTask.execute();

        if (DisableScreenDim) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        logger.closeLogger();
    }

  @Override
  protected void onStart() {
    super.onStop();

    AppStarted = true;
    MapZoomLevel = AppSettings.getFloat("MapZoomLevel", 16.0f);

  }

    @Override
    protected void onPause() {
        super.onPause();
        // The following call pauses the rendering thread.
        // If your OpenGL application is memory intensive,
        // you should consider de-allocating objects that
        // consume significant memory here.
       // mGLView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The following call resumes a paused rendering thread.
        // If you de-allocated graphic objects for onPause()
        // this is a good place to re-allocate them.
        //mGLView.onResume();
    }


  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.activity_main);
/////////////////////////////////////////////////////////////////////////////////////////
      mMediaUrl = "file:///sdcard/DCIM/video2.sdp";
      mSurfaceView = (SurfaceView) findViewById(R.id.player_surface_small);
      mSurfaceHolder = mSurfaceView.getHolder();

      mSurfaceFrame = (FrameLayout) findViewById(R.id.player_surface_frame_small);
      //mMediaUrl = getIntent().getExtras().getString("videoUrl");
      try {
          mLibVLC = new LibVLC();
          mLibVLC.setAout(mLibVLC.AOUT_AUDIOTRACK);
          mLibVLC.setVout(mLibVLC.VOUT_ANDROID_SURFACE);
          mLibVLC.setHardwareAcceleration(LibVLC.HW_ACCELERATION_AUTOMATIC);
          mLibVLC.setChroma("YV12");

          mLibVLC.init(getApplicationContext());
      } catch (LibVlcException e){
          Log.e(TAG, e.toString());
      }

      mSurface = mSurfaceHolder.getSurface();

      mLibVLC.attachSurface(mSurface, MainActivity.this);

      temp_options = mLibVLC.getMediaOptions(0);
      List<String> options_list = new ArrayList<String>(Arrays.asList(temp_options));


      options_list.add(":file-caching=2000");
      options_list.add(":network-caching=1");
      options_list.add(":clock-jitter=0");
      options_list.add("--clock-synchro=1");
      new_options = options_list.toArray(new String[options_list.size()]);

      mLibVLC.playMRL(mMediaUrl,new_options);
      /////////////////////////////////////////////////////////////////////////////


    set_up_app();

    if (DisableScreenDim) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);


    setup_telemetry_class();
    TelemetryAsyncTask = new ReadTelemetry();
    TelemetryAsyncTask.execute();

    launch_file_dialog();
  }

  private ReadTelemetry TelemetryAsyncTask;

/* >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
* START OF UI FUNCTIONS >>>>>> START OF UI FUNCTIONS >>>>>> START OF UI FUNCTIONS >>>>>> START OF UI FUNCTIONS >>>>>>
>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>*/

  //Settings change listener
  @Override
  public void onSharedPreferenceChanged(SharedPreferences AppSettings, String key) {
      LinearLayout topbar = (LinearLayout) findViewById(R.id.topFlightBar);

    //Changed settings will be applied on nex iteration of async task

    if (key.equals(SERVER_IP_ADDRESS)) {
      AC_DATA.ServerIp = AppSettings.getString(SERVER_IP_ADDRESS, getString(R.string.pref_ip_address_default));
      if (DEBUG) Log.d("PPRZ_info", "IP changed to: " + AppSettings.getString(SERVER_IP_ADDRESS, getString(R.string.pref_ip_address_default)));
      TcpSettingsChanged = true;

    }

    if (key.equals(SERVER_PORT_ADDRESS)) {
      AC_DATA.ServerTcpPort = Integer.parseInt(AppSettings.getString(SERVER_PORT_ADDRESS, getString(R.string.pref_port_number_default)));
      TcpSettingsChanged = true;
      if (DEBUG) Log.d("PPRZ_info", "Server Port changed to: " + AppSettings.getString(SERVER_PORT_ADDRESS, getString(R.string.pref_port_number_default)));

    }

    if (key.equals(LOCAL_PORT_ADDRESS)) {

      AC_DATA.UdpListenPort = Integer.parseInt(AppSettings.getString(LOCAL_PORT_ADDRESS, getString(R.string.pref_local_port_number_default)));
      UdpSettingsChanged = true;
      if (DEBUG) Log.d("PPRZ_info", "Local Listen Port changed to: " + AppSettings.getString(LOCAL_PORT_ADDRESS, getString(R.string.pref_local_port_number_default)));

    }

    if (key.equals(MIN_AIRSPEED)) {

      AC_DATA.AirSpeedMinSetting = parseDouble(AppSettings.getString(MIN_AIRSPEED, "10"));
      if (DEBUG) Log.d("PPRZ_info", "Local Listen Port changed to: " + AppSettings.getString(MIN_AIRSPEED, "10"));
    }

    if (key.equals(USE_GPS)) {
      mMap.setMyLocationEnabled(AppSettings.getBoolean(USE_GPS, true));
      if (DEBUG) Log.d("PPRZ_info", "GPS Usage changed to: " + AppSettings.getBoolean(USE_GPS, true));
    }

    if (key.equals(Control_Pass)) {
      AppPassword = AppSettings.getString(Control_Pass, "");
      if (DEBUG) Log.d("PPRZ_info", "App_password changed : " + AppSettings.getString(Control_Pass , ""));
    }


    if (key.equals(DISABLE_SCREEN_DIM)) {
      DisableScreenDim = AppSettings.getBoolean(DISABLE_SCREEN_DIM, true);

      if (DisableScreenDim) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
      else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

      if (DEBUG) Log.d("PPRZ_info", "Screen dim settings changed : " + DisableScreenDim);
    }
      if (key.equals(DISPLAY_FLIGHT_INFO)){
          DisplayFlightInfo = AppSettings.getBoolean((DISPLAY_FLIGHT_INFO), true);
          if (DisplayFlightInfo) {
              topbar.setVisibility(View.VISIBLE);
          }
          else {
              topbar.setVisibility(View.INVISIBLE);
          }

      }

  }

  public void clear_ac_track(View mView) {

    if (AC_DATA.SelAcInd < 0) {
      Toast.makeText(getApplicationContext(), "No AC data yet!", Toast.LENGTH_SHORT).show();
      return;
    }

    if (ShowOnlySelected) {
      AC_DATA.AircraftData[AC_DATA.SelAcInd].AC_Path.clear();
    }
    else {
      for (int AcInd = 0; AcInd <= AC_DATA.IndexEnd; AcInd++) {
        AC_DATA.AircraftData[AcInd].AC_Path.clear();
      }
    }
    mDrawerLayout.closeDrawers();

  }

  //Function called when toggleButton_ConnectToServer (in left fragment) is pressed
  public void connect_to_server(View mView) {

    Toast.makeText(getApplicationContext(), "Trying to re-connect to server..", Toast.LENGTH_SHORT).show();
    //Force to reconnect
    TcpSettingsChanged = true;
    UdpSettingsChanged = true;

  }

  //Kill throttle function, unused but might at some point be useful.
  public void kill_ac(View mView) {

    if (AC_DATA.SelAcInd >= 0) {

      AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
      // Setting Dialog Title
      alertDialog.setTitle("Kill Throttle");

      // Setting Dialog Message
      alertDialog.setMessage("Kill throttle of A/C " + AC_DATA.AircraftData[AC_DATA.SelAcInd].AC_Name + "?");

      // Setting Icon to Dialog
      alertDialog.setIcon(R.drawable.kill);

      // Setting Positive "Yes" Button
      alertDialog.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
          // User pressed YES button. Send kill string
          send_to_server("dl DL_SETTING " + AC_DATA.AircraftData[AC_DATA.SelAcInd].AC_Id + " " + AC_DATA.AircraftData[AC_DATA.SelAcInd].AC_KillID + " 1.000000", true);
          Toast.makeText(getApplicationContext(), AC_DATA.AircraftData[AC_DATA.SelAcInd].AC_Name + " ,mayday, kill mode!", Toast.LENGTH_SHORT).show();
        }
      });

      alertDialog.setNegativeButton("No", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {

        }
      });

      // Showing Alert Message
      alertDialog.show();

    } else {
      Toast.makeText(getApplicationContext(), "No AC data yet!", Toast.LENGTH_SHORT).show();
    }
  }

  //Resurrect function, unused but could also be useful
  public void resurrect_ac(View mView) {
    //dl DL_SETTING 5 9 0.000000
    if (AC_DATA.SelAcInd >= 0) {
      send_to_server("dl DL_SETTING " + AC_DATA.AircraftData[AC_DATA.SelAcInd].AC_Id + " " + AC_DATA.AircraftData[AC_DATA.SelAcInd].AC_KillID + " 0.000000", true);
    } else {
      Toast.makeText(getApplicationContext(), "No AC data yet!", Toast.LENGTH_SHORT).show();
    }

  }


  public void change_visible_acdata(View mView) {

    //Return if no AC data
    if (AC_DATA.SelAcInd < 0) {
      Toast.makeText(getApplicationContext(), "No AC data yet!", Toast.LENGTH_SHORT).show();
      return;
    }

    if (ChangeVisibleAcButon.isChecked()) {
      Toast.makeText(getApplicationContext(), "Showing only " + AC_DATA.AircraftData[AC_DATA.SelAcInd].AC_Name + " markers", Toast.LENGTH_SHORT).show();
      ShowOnlySelected = true;
    } else {
      Toast.makeText(getApplicationContext(), "Showing all markers", Toast.LENGTH_SHORT).show();
      ShowOnlySelected = false;
    }

    set_marker_visibility();
    mDrawerLayout.closeDrawers();

  }

  //Shows only selected ac markers & hide others
  private void set_marker_visibility() {

    if (ShowOnlySelected) {
      show_only_selected_ac();
    } else {
      show_all_acs();
    }


  }

  private void show_only_selected_ac() {


    for (int AcInd = 0; AcInd <= AC_DATA.IndexEnd; AcInd++) {

      for (int WpId = 1; (AC_DATA.AircraftData[AcInd].AC_Enabled && (WpId < AC_DATA.AircraftData[AcInd].NumbOfWps - 1)); WpId++) {

        if ((null == AC_DATA.AircraftData[AcInd].AC_Markers[WpId].WpMarker)) continue;

        if (AcInd == AC_DATA.SelAcInd) {
          AC_DATA.AircraftData[AcInd].AC_Markers[WpId].WpMarker.setVisible(true);

          if ("_".equals(AC_DATA.AircraftData[AcInd].AC_Markers[WpId].WpName.substring(0, 1))) {
            AC_DATA.AircraftData[AcInd].AC_Markers[WpId].WpMarker.setVisible(false);
          }

        } else {
          AC_DATA.AircraftData[AcInd].AC_Markers[WpId].WpMarker.setVisible(false);
        }
      }

    }

  }

  //Show all markers
  private void show_all_acs() {


    for (int AcInd = 0; AcInd <= AC_DATA.IndexEnd; AcInd++) {

      for (int WpId = 1; (AC_DATA.AircraftData[AcInd].AC_Enabled && (WpId < AC_DATA.AircraftData[AcInd].NumbOfWps - 1)); WpId++) {

        if ((null == AC_DATA.AircraftData[AcInd].AC_Markers[WpId].WpMarker)) continue;

        AC_DATA.AircraftData[AcInd].AC_Markers[WpId].WpMarker.setVisible(true);

        if ("_".equals(AC_DATA.AircraftData[AcInd].AC_Markers[WpId].WpName.substring(0, 1))) {
          AC_DATA.AircraftData[AcInd].AC_Markers[WpId].WpMarker.setVisible(false);
        }

      }

    }

  }

  private boolean AcMarkerVisible(int AcInd) {

    if (AcInd == AC_DATA.SelAcInd) {
      return true;
    } else if (ShowOnlySelected) {
      return false;
    }
    return true;

  }

  /**
   * background thread to read & write comm strings. Only changed UI items should be refreshed for smoother UI
   * Check telemetry class for whole UI change flags
   */
  class ReadTelemetry extends AsyncTask<String, String, String> {

    @Override
    protected void onPreExecute() {
      super.onPreExecute();

      isTaskRunning = true;
      if (DEBUG) Log.d("PPRZ_info", "ReadTelemetry() function started.");
    }

    @Override
    protected String doInBackground(String... strings) {

        mTCPthread =  new Thread(new ClientThread());
        mTCPthread.start();

        while (isTaskRunning) {

            //Check if settings changed
            if (TcpSettingsChanged) {
                AC_DATA.mTcpClient.stopClient();
                try {
                    Thread.sleep(200);
                    //AC_DATA.mTcpClient.SERVERIP= AC_DATA.ServerIp;
                    //AC_DATA.mTcpClient.SERVERPORT= AC_DATA.ServerTcpPort;
                    mTCPthread =  new Thread(new ClientThread());
                    mTCPthread.start();
                    TcpSettingsChanged=false;
                    if (DEBUG) Log.d("PPRZ_info", "TcpSettingsChanged applied");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            if (UdpSettingsChanged) {
                AC_DATA.setup_udp();
                //AC_DATA.tcp_connection();
                UdpSettingsChanged = false;
                if (DEBUG) Log.d("PPRZ_info", "UdpSettingsChanged applied");
            }

            // Log.e("PPRZ_info", "3");
            //1 check if any string waiting to be send to tcp
            if (!(null == AC_DATA.SendToTcp)) {
                AC_DATA.mTcpClient.sendMessage(AC_DATA.SendToTcp);
                AC_DATA.SendToTcp = null;

            }

        //3 try to read & parse udp data
        AC_DATA.read_udp_data(sSocket);

        //4 check ui changes
        if (AC_DATA.ViewChanged) {
          publishProgress("ee");
          AC_DATA.ViewChanged = false;
        }
      }

      if (DEBUG) Log.d("PPRZ_info", "Stopping AsyncTask ..");
      return null;
    }

    @Override
    protected void onProgressUpdate(String... value) {
      super.onProgressUpdate(value);

      try {

        if (AC_DATA.SelAcInd < 0) {
          //no selected aircrafts yet! Return.
          return;
        }


        if (AC_DATA.AircraftData[AC_DATA.SelAcInd].ApStatusChanged) {

			Bitmap bitmap = Bitmap.createBitmap(
					55, // Width
					110, // Height
					Bitmap.Config.ARGB_8888 // Config
			);
			Canvas canvas = new Canvas(bitmap);
			//canvas.drawColor(Color.BLACK);
			Paint paint = new Paint();
			paint.setStyle(Paint.Style.FILL);
			paint.setAntiAlias(true);
			double battery_double = Double.parseDouble(AC_DATA.AircraftData[AC_DATA.SelAcInd].Battery);
			double battery_width = (12.5 - battery_double) / (.027);
			int val = (int) battery_width;


			int newPercent = (int) (((battery_double - 9.8)/(10.9-9.8)) * 100);
			if(newPercent >= 100 && percent >= 100){
				TextViewBattery.setText("" + percent + " %");
			}
			if(newPercent < percent && newPercent >= 0) {
				TextViewBattery.setText("" + newPercent + " %");
				percent = newPercent;
			}


			if (percent> 66) {
				paint.setColor(Color.parseColor("#18A347"));
			}
			if (66 >= percent && percent >= 33) {
				paint.setColor(Color.YELLOW);

			}
			if (33 > percent && percent > 10) {
				paint.setColor(Color.parseColor("#B0090E"));
				if(lowBatteryUnread) {
					Toast.makeText(getApplicationContext(), "Warning: Low Battery", Toast.LENGTH_SHORT).show();
					lowBatteryUnread = false;
				}
			}
			if (percent <= 10) {
				if(emptyBatteryUnread) {
					Toast.makeText(getApplicationContext(), "No battery remaining. Land immediately", Toast.LENGTH_SHORT).show();
					emptyBatteryUnread = false;
				}
			}
			int padding = 10;
			Rect rectangle = new Rect(
					padding, // Left
					100 - (int) (90*((double) percent *.01)), // Top
					canvas.getWidth() - padding , // Right
					canvas.getHeight() - padding // Bottom
			);

			canvas.drawRect(rectangle, paint);
			batteryLevelView.setImageBitmap(bitmap);
			batteryLevelView.setBackgroundResource(R.drawable.battery_image_empty);


          TextViewFlightTime.setText(AC_DATA.AircraftData[AC_DATA.SelAcInd].FlightTime + " s");
          AC_DATA.AircraftData[AC_DATA.SelAcInd].ApStatusChanged = false;
        }

        if (AC_DATA.AirspeedWarning) {
          try {
            play_sound(getApplicationContext());
          } catch (IOException e) {
            e.printStackTrace();
          }
        }


        if (AC_DATA.BlockChanged) {
          //Block changed for selected aircraft
          if (DEBUG) Log.d("PPRZ_info", "Block Changed for selected AC.");
          set_selected_block((AC_DATA.AircraftData[AC_DATA.SelAcInd].SelectedBlock-1),true);
          AC_DATA.BlockChanged = false;

        }

        if (AC_DATA.NewAcAdded) {
          //new ac addedBattery value for an ac is changed

          set_selected_ac(AC_DATA.SelAcInd,false);
          AC_DATA.NewAcAdded = false;
        }

          if (AC_DATA.BatteryChanged) {
              //new ac addedBattery value for an ac is changed
              refresh_ac_list();
              mAcListAdapter.notifyDataSetChanged();
              AC_DATA.BatteryChanged = false;
          }

        //For a smooth gui we need refresh only changed gui controls
        refresh_markers();
        originalPosition_s = originalPosition;
        path_s = path;
        if (AC_DATA.AircraftData[AC_DATA.SelAcInd].Altitude_Changed) {
          TextViewAltitude.setText(AC_DATA.AircraftData[AC_DATA.SelAcInd].Altitude);

            AC_DATA.AircraftData[AC_DATA.SelAcInd].Altitude_Changed = false;

        }


        //No error handling right now.
        Button_ConnectToServer.setText("Connected!");
        //}

      } catch (Exception ex) {
          if (DEBUG) Log.d("Pprz_info", "Exception occured: " + ex.toString());

      }

    }

    @Override
    protected void onPostExecute(String s) {
      super.onPostExecute(s);

    }

  }

  class ClientThread implements Runnable {


        @Override
        public void run() {

            if (DEBUG) Log.d("PPRZ_info", "ClientThread started");

            AC_DATA.mTcpClient = new TCPClient(new TCPClient.OnMessageReceived() {
                @Override
                //here the messageReceived method is implemented
                public void messageReceived(String message) {
                    //this method calls the onProgressUpdate
                    //publishProgress(message);
                    //AC_DATA.parse_tcp_string(message);
                    AC_DATA.parse_tcp_string(message);

                }
            });
            AC_DATA.mTcpClient.SERVERIP = AC_DATA.ServerIp;
            AC_DATA.mTcpClient.SERVERPORT= AC_DATA.ServerTcpPort;
            AC_DATA.mTcpClient.run();

        }

    }



/* <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
* END OF UI FUNCTIONS >>>>>> END OF UI FUNCTIONS >>>>>> END OF UI FUNCTIONS >>>>>> END OF UI FUNCTIONS >>>>>>
<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<*/

//Here are our new methods

	//clears all buttons so only one will be shown as selected at any moment
	public void clear_buttons(){
		Button_Takeoff.setSelected(false);
		Button_Execute.setSelected(false);
		Button_Pause.setSelected(false);
		Button_LandHere.setSelected(false);
	}

	//this method converts the google latitudes to the corresponding points on the transposed image
	//use this method to draw the markers in the right spots
	public static LatLng convert_to_lab(LatLng position){
		double oldLat = position.latitude;
		double oldLong = position.longitude;

		double newLat = 5*oldLat - 144.021756;
		double newLong = 5.35*oldLong+343.3933874;

		LatLng newPosition = new LatLng(newLat, newLong);
		return newPosition;
	}

	//this method converts the fake latitudes back to the actual google values
	//use this for any information that paparazzi needs about where to actually send the drone
	public static LatLng convert_to_google(LatLng position){
		double oldLat = position.latitude;
		double oldLong = position.longitude;

		double newLat = (oldLat + 144.021756)/5;
		double newLong = (oldLong - 343.3933874)/5.35;

		LatLng newPosition = new LatLng(newLat, newLong);
		return newPosition;
	}

    //fix the icons every time a waypoint is either reached or removed, currently unused in
    //experimental design
    public void adjust_marker_titles(){
        int index = 0;
        while(index < mMarkerHead.size()){
            Marker marker = mMarkerHead.get(index);
            marker.setIcon(BitmapDescriptorFactory.fromBitmap(AC_DATA.muiGraphics.create_marker_icon(
                    "red", Integer.toString(index + 1), AC_DATA.GraphicsScaleFactor)));
            marker.setTitle(Integer.toString(index + 1));
            index++;
        }
    }

    //adjust the connecting lines between markers when one is deleted
    public void adjust_marker_lines(){
        path.setPoints(pathPoints);
    }

    //dialog if waypoint is placed out of bounds
    public void launch_error_dialog(){
        AlertDialog errorDialog = new AlertDialog.Builder(MainActivity.this)
                .setTitle("Error!")
                .setMessage("You have placed a waypoint outside of the bounds of the course.")
                .setPositiveButton("OK",null)
                .create();
        errorDialog.show();
    }

    //dialog to bring up removal control for a waypoint
    public void remove_waypoint_dialog(Marker marker){
        final Marker rMarker = marker;
        AlertDialog removeDialog = new AlertDialog.Builder(MainActivity.this)
                .setTitle("Remove?")
                .setMessage("Click OK to remove this marker")
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        rMarker.remove();
                        if(mrkIndex>0) mrkIndex--;
                        int index = mMarkerHead.indexOf(rMarker);
                        if(index == mrkIndex) lastAltitude = Double.parseDouble(rMarker.getSnippet());
                        mMarkerHead.remove(index);
                        pathPoints.remove(index + 1);
                        adjust_marker_lines();
                        logger.logWaypointEvent(
                                AC_DATA.AircraftData[0],
                                EventLogger.WAYPOINT_DELETE,
                                -1,
                                rMarker.getPosition(),
                                null,
                                rMarker.getSnippet(),
                                null);
                    }
                })
                .setNegativeButton("Cancel", null)
                .create();
        removeDialog.show();
    }

    //dialog to bring up altitude control when a waypoint is either created or moved
    public void launch_altitude_dialog(Marker marker, String flag){
        final Marker altMarker = marker;
        final String mFlag = flag;
        LinearLayout altLayout = new LinearLayout(this);
        altLayout.setOrientation(LinearLayout.VERTICAL);

        //create text view
        final TextView altVal = new TextView(this);
        altVal.setGravity(Gravity.CENTER);

        //create altitude adjustment and set default accordingly
        SeekBar altSeek = new SeekBar(this);
        altSeek.setMax(22);
        if(flag.equals("NEW")) {
			if(mrkIndex>0) {
				altSeek.setProgress((int) (10*Double.parseDouble(mMarkerHead.get(mrkIndex - 1).getSnippet())));
			}
			else{
				altSeek.setProgress((int) (lastAltitude * 10));
			}
        }
        else if(flag.equals("OLD")){
            altSeek.setProgress((int)(10*Double.parseDouble(altMarker.getSnippet())));
        }
        altVal.setText(String.valueOf((double) (altSeek.getProgress())/10));
        altSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                altVal.setText(String.valueOf((double) i/10));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        altLayout.addView(altVal);
        altLayout.addView(altSeek);

        //create the actual dialog
        AlertDialog.Builder altDialog = new AlertDialog.Builder(MainActivity.this);
        altDialog.setView(altLayout);
        altDialog.setTitle("Waypoint Added!")
                .setMessage("Click Confirm to set new altitude. Clicking Cancel will set the altitude to be the same " +
                        "as the previous waypoint")
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        if(mFlag.equals("NEW")){
                            if(mrkIndex > 1){
								//note the minus two here bc the mrkindex++ line will have executed
								//by this point
                                altMarker.setSnippet(mMarkerHead.get(mrkIndex-2).getSnippet());
                            }
                            else{
                                altMarker.setSnippet("" + lastAltitude);
                            }
                        }
                        altMarker.setIcon(BitmapDescriptorFactory.fromBitmap(AC_DATA.muiGraphics.create_marker_icon(
                                "red", altMarker.getSnippet(), AC_DATA.GraphicsScaleFactor)));
                    }
                })
                .setPositiveButton("Confirm", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        if(mFlag.equals("OLD")){
                            logger.logWaypointEvent(
                                    AC_DATA.AircraftData[0],
                                    EventLogger.WAYPOINT_ALTITUDE_ADJUST,
                                    -1,
                                    altMarker.getPosition(),
                                    altMarker.getPosition(),
                                    altMarker.getSnippet(),
                                    altVal.getText().toString());
                        }else{
                            logger.logWaypointEvent(
                                    AC_DATA.AircraftData[0],
                                    EventLogger.WAYPOINT_CREATE,
                                    -1,
                                    null,
                                    altMarker.getPosition(),
                                    null,
                                    altVal.getText().toString());

                        }
                        altMarker.setSnippet(altVal.getText().toString());
                        lastAltitude = Double.parseDouble(altVal.getText().toString());
                        altMarker.setIcon(BitmapDescriptorFactory.fromBitmap(AC_DATA.muiGraphics.create_marker_icon(
                                "red", altMarker.getSnippet(), AC_DATA.GraphicsScaleFactor)));
                    }
                }).create();
        if(flag.equals("OLD")){
            altDialog.setMessage("Click Confirm to set new altitude. Clicking Cancel " +
                    "will keep the altitude as it had been set.");
            altDialog.setTitle("Adjust Altitude");
        }
        altDialog.show();
    }

    private void launch_file_dialog(){
        AlertDialog.Builder fileDialog = new AlertDialog.Builder(MainActivity.this);
        LinearLayout dialogLayout = new LinearLayout(this);
        dialogLayout.setOrientation(LinearLayout.HORIZONTAL);
        dialogLayout.setGravity(Gravity.CENTER_HORIZONTAL);

        // Create a drop down menu of all possible user ids
        final Spinner userId = new Spinner(this);
        final List<String> userIdSelections = new ArrayList<>();
        for(int i = 1; i<MAX_USER_ID; i++){
            userIdSelections.add(Integer.toString(i));
        }

        ArrayAdapter<String> userIdDataAdapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_dropdown_item,
                        userIdSelections);
        userId.setAdapter(userIdDataAdapter);

        // Create a drop down menu of the experimental groups
        final Spinner experimentalGroups = new Spinner(this);
        final List<String> groupSelections = new ArrayList<>();
        groupSelections.add("Group_1");
        groupSelections.add("Group_2");
        groupSelections.add("Group_3");
        groupSelections.add("Group_4");

        ArrayAdapter<String> groupDataAdapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_dropdown_item,
                        groupSelections);
        experimentalGroups.setAdapter(groupDataAdapter);

        // Create a drop down menu of the modules
        final Spinner modules = new Spinner(this);
        final List<String> moduleSelections = new ArrayList<>();
        moduleSelections.add("Module_3");
        moduleSelections.add("Module_4");
        moduleSelections.add("Module_5");
        moduleSelections.add("Module_6");
        moduleSelections.add("Checkride");
        moduleSelections.add("Experiment");

        ArrayAdapter<String> moduleDataAdapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_dropdown_item,
                        moduleSelections);
        modules.setAdapter(moduleDataAdapter);

        dialogLayout.addView(userId);
        dialogLayout.addView(experimentalGroups);
        dialogLayout.addView(modules);

        fileDialog.setTitle("Choose Experiment Parameters")
                .setMessage("Choose the user ID, the module being tested, and the experimental group.")
                .setPositiveButton("Confirm", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        logger = new EventLogger(
                                userId.getSelectedItem() + "_" +
                                        experimentalGroups.getSelectedItem() + "_" +
                                        modules.getSelectedItem() + ".csv");
                    }
                }).create();
        fileDialog.setView(dialogLayout);
        fileDialog.show();

    }

    public boolean outsideBounds(LatLng latLng){
        Point currentPoint = mMap.getProjection().toScreenLocation(latLng);
        int x = currentPoint.x;
        int y = currentPoint.y;

        return x > 1500 || x < 422 || y < 167 || y >922;
    }
////////////////////////////////////////////////////////////////////////////////////////////
private static final String TAG = MainActivity.class.getSimpleName();


    private int mVideoHeight;
    private int mVideoWidth;
    private int mVideoVisibleHeight;
    private int mVideoVisibleWidth;
    private int mSarNum;
    private int mSarDen;

    private SurfaceView mSurfaceView;
    private FrameLayout mSurfaceFrame;
    private SurfaceHolder mSurfaceHolder;
    private Surface mSurface = null;

    private LibVLC mLibVLC;

    private String mMediaUrl;
    private String[] temp_options;
    private String[] new_options;

    public void eventHardwareAccelerationError() {
        Log.e(TAG, "eventHardwareAccelerationError()!");
        return;
    }

    @Override
    public void setSurfaceLayout(final int width, final int height, int visible_width, int visible_height, final int sar_num, int sar_den){
        Log.d(TAG, "setSurfaceSize -- START");
        if (width * height == 0)
            return;

        // store video size
        mVideoHeight = height;
        mVideoWidth = width;
        mVideoVisibleHeight = visible_height;
        mVideoVisibleWidth = visible_width;
        mSarNum = sar_num;
        mSarDen = sar_den;

        Log.d(TAG, "setSurfaceSize -- mMediaUrl: " + mMediaUrl + " mVideoHeight: " + mVideoHeight + " mVideoWidth: " + mVideoWidth + " mVideoVisibleHeight: " + mVideoVisibleHeight + " mVideoVisibleWidth: " + mVideoVisibleWidth + " mSarNum: " + mSarNum + " mSarDen: " + mSarDen);
    }
    @Override
    public int configureSurface(android.view.Surface surface, int i, int i1, int i2){
        return -1;
    }

}
