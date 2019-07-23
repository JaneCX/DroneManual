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
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
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
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

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

import org.videolan.libvlc.IVideoPlayer;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.LibVlcException;

import java.io.IOException;
import java.net.DatagramSocket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import static java.lang.Double.parseDouble;


public class MainActivity extends Activity implements IVideoPlayer {

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

  	///////////////////
    public static int position_a = 0;

  	private static final int MAX_USER_ID = 70;

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
	private GoogleMap mMap1, mMap2;
	private GroundOverlay trueMap1, trueMap2;
	TextView TextViewAltitude;
	TextView TextViewFlightTime;
	TextView TextViewBattery;
  	private ImageView batteryLevelView;

	private Button Button_ConnectToServer;
  	public Button Button_Takeoff, Button_LandHere, map_swap;

  	//private ToggleButton ChangeVisibleAcButton;
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
  	//public boolean pathInitialized = false;
    public LatLng originalPosition;
    public static int mapIndex = 0;
    private int[] mapImages = {
            R.drawable.blank,
            R.drawable.map1,
			R.drawable.map2,
			R.drawable.map3,
            R.drawable.map4,
            R.drawable.map5,
            R.drawable.map6};

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


  	////////////////////////////////////////////////////////////////////////////
	//manual joystick variables
	RelativeLayout thumbPad_right, thumbPad_new;
	JStick rightPad, leftnew;
	Button toggle;

	public Point currentPosition;
	public static final int DRONE_LENGTH = 5;
	public static final int DISTANCE_FROM_WALL = 12;
	private float multiplier;

	private int click_count = 0;
	private SurfaceView mSurfaceView1, mSurfaceView2 ;
	private FrameLayout mSurfaceFrame1, mSurfaceFrame2;
	private SurfaceHolder mSurfaceHolder1, mSurfaceHolder2;
	private Surface mSurface1 = null, mSurface2 = null;

	private LibVLC mLibVLC1, mLibVLC2;
	private String mMediaUrl1, mMediaUrl2;
	private String[] temp_options;
	private String[] new_options;

	ViewFlipper vf_big,vf_small;

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
      	//AppSettings.registerOnSharedPreferenceChangeListener(this);

      	AppPassword = "1234";

      	DisableScreenDim = AppSettings.getBoolean("disable_screen_dim", true);

		//initialize map
		mMap1 = ((MapFragment) getFragmentManager()
				.findFragmentById(R.id.map)).getMap();
		mMap2 = ((MapFragment) getFragmentManager()
				.findFragmentById(R.id.map_small)).getMap();

		//initialize map options
		GoogleMapOptions mMapOptions = new GoogleMapOptions();

		mMap1.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
			@Override
			public void onMapClick(LatLng latLng) {
				Point point = mMap1.getProjection().toScreenLocation(latLng);
				Log.d("location","x:    " + point.x + "   y:     " + point.y);
				if(point.y == 313 || point.y == 312 || point.y == 314){
					Log.d("location","x:    " + latLng.latitude + "   y:     " + latLng.longitude);
				}
			}
		});

		mMap1.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
		mMap2.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
		mMap1.moveCamera(CameraUpdateFactory.newLatLngZoom(LAB_ORIGIN, 50));
		mMap2.moveCamera(CameraUpdateFactory.newLatLngZoom(LAB_ORIGIN, 50));
		CameraPosition rotated1 = new CameraPosition.Builder()
				.target(LAB_ORIGIN)
				.zoom(50)
				.bearing(90.0f)
				.build();
		CameraPosition rotated2 = new CameraPosition.Builder()
				.target(LAB_ORIGIN)
				.zoom(20)
				.bearing(90.0f)
				.build();
		mMap1.moveCamera(CameraUpdateFactory.newCameraPosition(rotated1));
		mMap2.moveCamera(CameraUpdateFactory.newCameraPosition(rotated2));

		BitmapDescriptor labImage = BitmapDescriptorFactory.fromResource(mapImages[mapIndex]);
		trueMap1 = mMap1.addGroundOverlay(new GroundOverlayOptions()
				.image(labImage)
				.position(LAB_ORIGIN, (float) 35.23)
				.bearing(90.0f));
		trueMap2 = mMap2.addGroundOverlay(new GroundOverlayOptions()
				.image(labImage)
				.position(LAB_ORIGIN, (float) 35.23)
				.bearing(90.0f));

		blockMapInteraction();

		//Setup left drawer
      	mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);

      	//Setup AC List
      	//setup_ac_list();

      	//Setup Block counter
	  	//setup_counter();

	  	//continue to bound UI items
	  	TextViewAltitude = (TextView) findViewById(R.id.Alt_On_Map);
		TextViewBattery = (TextView) findViewById(R.id.Bat_Vol_On_Map);
		TextViewFlightTime = (TextView) findViewById(R.id.Flight_Time_On_Map);
		batteryLevelView = (ImageView) findViewById(R.id.batteryImageView);

	  	Button_ConnectToServer = (Button) findViewById(R.id.Button_ConnectToServer);

	  	Button_Takeoff = (Button) findViewById(R.id.takeoff);
	  	Button_LandHere = (Button) findViewById(R.id.land_here);
        map_swap = (Button) findViewById(R.id.map_swap);

	  	Button_Takeoff.setOnTouchListener(new View.OnTouchListener() {
		  @Override
		  public boolean onTouch(View v, MotionEvent event) {
			  clear_buttons();
			  if(event.getAction() == MotionEvent.ACTION_DOWN) {
			  	  set_selected_block(0,false);
			  	  Button_Takeoff.setSelected(true);
			  }
			  return false;
		  }
	  });

	  	Button_LandHere.setOnTouchListener(new View.OnTouchListener() {
		  @Override
		  public boolean onTouch(View v, MotionEvent event) {
			  clear_buttons();
              if(event.getAction() == MotionEvent.ACTION_DOWN) {
              	//  logger.recordTime();
              	 // logger.logEvent(AC_DATA.AircraftData[0], EventLogger.LANDING, -1);
				//  logger.endFlight();
				  set_selected_block(3,false);
				  Button_LandHere.setSelected(true);
			  }
			  return false;
		  }
	  });

        initializeMapChangeButton();

	  	//ChangeVisibleAcButton = (ToggleButton) findViewById(R.id.toggleButtonVisibleAc);
	  	//ChangeVisibleAcButton.setSelected(false);

	  	//////////////////////////////////////////////////////////////////////////////////////////
		//adding manual control joysticks
		thumbPad_right = (RelativeLayout)findViewById(R.id.layout_joystick_right);
		thumbPad_new = (RelativeLayout)findViewById(R.id.layout_joystick_left);

		//leftPad = new ThumbPad(thumbPad_left);
		rightPad = new JStick(getApplicationContext(), thumbPad_right, R.drawable.image_button, "YAW");
		leftnew = new JStick(getApplicationContext(), thumbPad_new, R.drawable.image_button, "PITCH");

		toggle = (Button)findViewById(R.id.toggle);

		vf_big = (ViewFlipper)findViewById(R.id.vf_1);
		vf_small = (ViewFlipper)findViewById(R.id.vf_2);

		thumbPad_new.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent arg1) {
				//AC_DATA.inspecting = true;
				leftnew.drawStick(arg1);
				//checks to see if the joystick is in the throttle region
				if((arg1.getAction() == MotionEvent.ACTION_DOWN
						|| arg1.getAction() == MotionEvent.ACTION_MOVE) && Math.abs(leftnew.getX()) < 60) {
					if(arg1.getAction() == MotionEvent.ACTION_DOWN) {
						Log.d(TAG, "onTouch: thumpbad pressed, mode = " + AC_DATA.mode);
						AC_DATA.mode = 1;
						MainActivity.logger.logEvent(AC_DATA.AircraftData[0], EventLogger.YAW_THROTTLE_START);
					}
					if(leftnew.getY()>30 && belowAltitude()) {
						AC_DATA.throttle = 84;
					}
					else if(leftnew.getY()>30 && belowAltitude()) {
						AC_DATA.throttle = 81;
					}
					else if(leftnew.getY()<-30) {
						AC_DATA.throttle = 40;
					}
					else {
						AC_DATA.throttle = 63;
					}
				}
				//checks to see if the joystick is in the yaw region
				else if((arg1.getAction() == MotionEvent.ACTION_DOWN
						|| arg1.getAction() == MotionEvent.ACTION_MOVE) && Math.abs(leftnew.getX()) >= 72) {
					if(arg1.getAction() == MotionEvent.ACTION_DOWN) {
						Log.d(TAG, "onTouch: thumpbad pressed, mode = " + AC_DATA.mode);
						AC_DATA.mode = 1;
						MainActivity.logger.logEvent(AC_DATA.AircraftData[0], EventLogger.YAW_THROTTLE_START);
					}
					if(leftnew.getX()>0) AC_DATA.yaw = 10;      //right button for yaw
					if(leftnew.getX()<0) AC_DATA.yaw = -10;     //left button for yaw
				}
				//reset value of yaw but not throttle when lifting up
				else if(arg1.getAction() == MotionEvent.ACTION_UP) {
					Log.d(TAG, "onTouch: thumpbad pressed, mode = " + AC_DATA.mode);
					AC_DATA.mode = 1;
					MainActivity.logger.logEvent(AC_DATA.AircraftData[0], EventLogger.YAW_THROTTLE_END);
					AC_DATA.yaw = 0;
					AC_DATA.throttle = 63;
					new CountDownTimer(1000, 100) {
						@Override
						public void onTick(long l) {
						}

						@Override
						public void onFinish() {
							float Altitude = Float.parseFloat(AC_DATA.AircraftData[0].RawAltitude);
							AC_DATA.SendToTcp = AppPassword + "PPRZonDroid MOVE_WAYPOINT " + AcId  + " 4 " +
									AC_DATA.AircraftData[0].Position.latitude + " " +
									AC_DATA.AircraftData[0].Position.longitude + " " + Altitude;
						}
					}.start();

				}
				return true;
			}
		});

		thumbPad_right.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent arg1) {
				//AC_DATA.inspecting = true;
				rightPad.drawStick(arg1);
				if((arg1.getAction() == MotionEvent.ACTION_DOWN
						|| arg1.getAction() == MotionEvent.ACTION_MOVE)) {
					if(arg1.getAction() == MotionEvent.ACTION_DOWN) {
						Log.d(TAG, "onTouch: thumpbad pressed, mode = " + AC_DATA.mode);
						AC_DATA.mode = 1;
						MainActivity.logger.logEvent(AC_DATA.AircraftData[0], EventLogger.ROLL_PITCH_START);
					}
					currentPosition = mMap1.getProjection().toScreenLocation(convert_to_lab(AC_DATA.AircraftData[0].Position));
					if(nearWall(currentPosition, DRONE_LENGTH, DISTANCE_FROM_WALL)){
						multiplier = 2.0f;
					} else if(nearWall(currentPosition, DRONE_LENGTH, 1.5* DISTANCE_FROM_WALL)){
						multiplier = 1.75f;
					} else if(nearWall(currentPosition, DRONE_LENGTH, 2*DISTANCE_FROM_WALL)){
						multiplier = 1.5f;
					} else {
						multiplier = 1.0f;
					}
					AC_DATA.roll = (int) (rightPad.getX()/(4.5f * multiplier));
					AC_DATA.pitch = (int) -(rightPad.getY()/(4.5f * multiplier));
				}
				//reset both values to zero when lifting up or in central zone
				else if(arg1.getAction() == MotionEvent.ACTION_UP) {
					Log.d(TAG, "onTouch: thumpbad pressed, mode = " + AC_DATA.mode);
					AC_DATA.mode = 1;
					MainActivity.logger.logEvent(AC_DATA.AircraftData[0], EventLogger.ROLL_PITCH_END);
					AC_DATA.roll = 0;
					AC_DATA.pitch = 0;
					new CountDownTimer(1000, 100) {
						@Override
						public void onTick(long l) {
						}

						@Override
						public void onFinish() {
							float Altitude = Float.parseFloat(AC_DATA.AircraftData[0].RawAltitude);
							AC_DATA.SendToTcp = AppPassword + "PPRZonDroid MOVE_WAYPOINT " + AcId  + " 4 " +
									AC_DATA.AircraftData[0].Position.latitude + " " +
									AC_DATA.AircraftData[0].Position.longitude + " " + Altitude;
						}
					}.start();
				}
				return true;


			}
		});

		initializeToggleButton();
	}

	private void blockMapInteraction() {
		mMap1.getUiSettings().setAllGesturesEnabled(false);
		mMap1.getUiSettings().setZoomGesturesEnabled(false);
		mMap1.getUiSettings().setTiltGesturesEnabled(false);
		mMap1.getUiSettings().setCompassEnabled(false);
		mMap2.getUiSettings().setAllGesturesEnabled(false);
		mMap2.getUiSettings().setZoomGesturesEnabled(false);
		mMap2.getUiSettings().setTiltGesturesEnabled(false);
		mMap2.getUiSettings().setCompassEnabled(false);
	}

	private void initializeMapChangeButton() {
		map_swap.setOnLongClickListener(new View.OnLongClickListener() {
			@Override
			public boolean onLongClick(View v) {
				if(++mapIndex >= mapImages.length) mapIndex = 0;
				BitmapDescriptor newLabImage = BitmapDescriptorFactory.fromResource(mapImages[mapIndex]);
				trueMap1.setImage(newLabImage);
				trueMap2.setImage(newLabImage);
				return false;
			}
		});
	}

	private void initializeToggleButton() {
		toggle.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				click_count++;
				if(click_count%2==1){
					mLibVLC2.stop();
					try{
						mLibVLC1 = new LibVLC();
						mLibVLC1.setAout(mLibVLC1.AOUT_AUDIOTRACK);
						mLibVLC1.setVout(mLibVLC1.VOUT_ANDROID_SURFACE);
						mLibVLC1.setHardwareAcceleration(LibVLC.HW_ACCELERATION_AUTOMATIC);
						mLibVLC1.setChroma("YV12");
						mLibVLC1.init(getApplicationContext());

					}
					catch (LibVlcException e) {
						Log.e(TAG, e.toString());
					}
					mSurface1 = mSurfaceHolder1.getSurface();
					mLibVLC1.attachSurface(mSurface1, MainActivity.this);
					temp_options = mLibVLC1.getMediaOptions(0);
					List<String> options_list = new ArrayList<String>(Arrays.asList(temp_options));


					options_list.add(":file-caching=2000");
					options_list.add(":network-caching=1");
					options_list.add(":clock-jitter=0");
					options_list.add("--clock-synchro=1");
					new_options = options_list.toArray(new String[options_list.size()]);
					mLibVLC1.playMRL(mMediaUrl1,new_options);

				}
				else {
					mLibVLC1.stop();
					try {

						mLibVLC2 = new LibVLC();
						mLibVLC2.setAout(mLibVLC2.AOUT_AUDIOTRACK);
						mLibVLC2.setVout(mLibVLC2.VOUT_ANDROID_SURFACE);
						mLibVLC2.setHardwareAcceleration(LibVLC.HW_ACCELERATION_AUTOMATIC);
						mLibVLC2.setChroma("YV12");;
						mLibVLC2.init(getApplicationContext());

					} catch (LibVlcException e){
						Log.e(TAG, e.toString());
					}
					mSurface2 = mSurfaceHolder2.getSurface();
					mLibVLC2.attachSurface(mSurface2, MainActivity.this);
					temp_options = mLibVLC2.getMediaOptions(0);
					List<String> options_list = new ArrayList<String>(Arrays.asList(temp_options));


					options_list.add(":file-caching=2000");
					options_list.add(":network-caching=1");
					options_list.add(":clock-jitter=0");
					options_list.add("--clock-synchro=1");
					new_options = options_list.toArray(new String[options_list.size()]);
					mLibVLC2.playMRL(mMediaUrl2,new_options);

				}

				vf_big.showNext();
				vf_small.showNext();


			}
		});
	}

	public boolean nearWall(Point currentPosition, int droneSize, double distanceFromWall){
		int leftBound = currentPosition.x - droneSize;
		int rightBound = currentPosition.x + droneSize;
		int topBound = currentPosition.y - droneSize;
		int bottomBound = currentPosition.y + droneSize;

		return false;
	}

	public boolean belowAltitude(){
		return (Double.parseDouble(AC_DATA.AircraftData[0].RawAltitude) <= 2.0);
	}

	private boolean belowTakeoffAltitude() {
		return Double.parseDouble(AC_DATA.AircraftData[0].RawAltitude) <= 0.5;
	}

	/////////////////////////////////////************************************ map set up
	private static final LatLng LAB_ORIGIN = new LatLng(36.005417, -78.940984);

	public void add_AC_to_map(){
		AC_DATA.AircraftData[0].AC_Enabled = true;
		if (AC_DATA.AircraftData[0].AC_Enabled) {
			AC_DATA.AircraftData[0].AC_Logo = create_ac_icon(Color.RED, AC_DATA.GraphicsScaleFactor);

			AC_DATA.AircraftData[0].AC_Marker1 = mMap1.addMarker(new MarkerOptions()
					.position(convert_to_lab(AC_DATA.AircraftData[0].Position))
					.anchor((float) 0.5, (float) 0.5)
					.flat(true)
					.rotation(Float.parseFloat(AC_DATA.AircraftData[0].Heading))
					.draggable(false)
					.icon(BitmapDescriptorFactory.fromBitmap(AC_DATA.AircraftData[0].AC_Logo))
			);
			AC_DATA.AircraftData[0].AC_Marker2 = mMap2.addMarker(new MarkerOptions()
					.position(convert_to_lab(AC_DATA.AircraftData[0].Position))
					.anchor((float) 0.5, (float) 0.5)
					.flat(true)
					.rotation(Float.parseFloat(AC_DATA.AircraftData[0].Heading))
					.draggable(false)
					.icon(BitmapDescriptorFactory.fromBitmap(AC_DATA.AircraftData[0].AC_Logo))
			);
		}
		else{
			AC_DATA.get_new_aircraft_data(AcId);
		}
	}

	public Bitmap create_ac_icon(int ColorType, float GraphicsScaleFactor) {

		int AcColor = ColorType;

		int w = (int) (34 * GraphicsScaleFactor);
		int h = (int) (34 * GraphicsScaleFactor);
		Bitmap.Config conf = Bitmap.Config.ARGB_4444; // see other conf types
		Bitmap bmp = Bitmap.createBitmap(w, h, conf); // this creates a MUTABLE bitmapAircraftData[IndexOfAc].AC_Color
		Canvas canvas = new Canvas(bmp);

		canvas = create_selected_canvas(canvas, AcColor, GraphicsScaleFactor);


		//Create rotorcraft logo
		Paint p = new Paint();

		p.setColor(AcColor);


		p.setStyle(Paint.Style.STROKE);
		//p.setStrokeWidth(2f);
		p.setAntiAlias(true);

		Path ACpath = new Path();
		ACpath.moveTo((3 * w / 16), (h / 2));
		ACpath.addCircle(((3 * w / 16) + 1), (h / 2), ((3 * w / 16) - 2), Path.Direction.CW);
		ACpath.moveTo((3 * w / 16), (h / 2));
		ACpath.lineTo((13 * w / 16), (h / 2));
		ACpath.addCircle((13 * w / 16), (h / 2), ((3 * w / 16) - 2), Path.Direction.CW);
		ACpath.addCircle((w / 2), (13 * h / 16), ((3 * w / 16) - 2), Path.Direction.CW);
		ACpath.moveTo((w / 2), (13 * h / 16));
		ACpath.lineTo((w / 2), (5 * h / 16));
		ACpath.lineTo((6 * w / 16), (5 * h / 16));
		ACpath.lineTo((w / 2), (2 * h / 16));
		ACpath.lineTo((10 * w / 16), (5 * h / 16));
		ACpath.lineTo((w / 2), (5 * h / 16));

		canvas.drawPath(ACpath, p);

		Paint black = new Paint();
		black.setColor(Color.BLACK);
		black.setStyle(Paint.Style.STROKE);
		black.setStrokeWidth(6f);
		black.setAntiAlias(true);

		canvas.drawPath(ACpath, black);
		p.setStrokeWidth(3.5f);
		canvas.drawPath(ACpath, p);
		return bmp;
	}

	private Canvas create_selected_canvas(Canvas CanvIn, int AcColor, float GraphicsScaleFactor) {

		int w = CanvIn.getWidth();
		int h = CanvIn.getHeight();

		float SelLineLeng = 4 * GraphicsScaleFactor;

		Path SelPath = new Path();
		SelPath.moveTo(0, 0); //1
		SelPath.lineTo(SelLineLeng, 0);
		SelPath.moveTo(0, 0);
		SelPath.lineTo(0, SelLineLeng);
		SelPath.moveTo(w, 0); //2
		SelPath.lineTo(w - SelLineLeng, 0);
		SelPath.moveTo(w, 0);
		SelPath.lineTo(w, SelLineLeng);
		SelPath.moveTo(w, h);   //3
		SelPath.lineTo(w, h - SelLineLeng);
		SelPath.moveTo(w, h);
		SelPath.lineTo(w - SelLineLeng, h);
		SelPath.moveTo(0, h);
		SelPath.lineTo(0, h - SelLineLeng);
		SelPath.moveTo(0, h);
		SelPath.lineTo(SelLineLeng, h);

		Paint p = new Paint();
		//p.setColor(AcColor);
		p.setColor(Color.YELLOW);
		p.setAntiAlias(true);
		p.setStyle(Paint.Style.STROKE);
		p.setStrokeWidth(3 * GraphicsScaleFactor);
		CanvIn.drawPath(SelPath, p);
		return CanvIn;
	}

    private void set_selected_block(int BlocId,boolean ReqFromServer) {

		if (ReqFromServer) {
			//if in standby, takeoff is finished so clear the button
			if (BlocId == 4) {
				Button_Takeoff.setSelected(false);
			}
			//AC_DATA.AircraftData[AC_DATA.SelAcInd].SelectedBlock = BlocId;
		}
		else if(BlocId == 0){
            //start engine
			AC_DATA.mode = 2;
            send_to_server("PPRZonDroid JUMP_TO_BLOCK " + AC_DATA.AircraftData[0].AC_Id + " " + 2, true);
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
        //land here
        else if(BlocId == 3){
        	AC_DATA.mode = 2;
            send_to_server("PPRZonDroid JUMP_TO_BLOCK " + AC_DATA.AircraftData[AC_DATA.SelAcInd].AC_Id + " " + 10, true);

			//wait for paparazzi to register command
			/*new CountDownTimer(1000,1000){
				@Override
				public void onTick(long l) {}

				@Override
				public void onFinish(){
					//takeoff
					AC_DATA.mode = 1;
				}
			}.start();*/

		}
        //end activity button
        //stop timer/event logger

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

 /* //Bound map (if not bounded already)
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
  }*/

/*  //Setup map & marker components
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

    mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
        @Override
        public void onMapClick(LatLng latLng) {
            Point markerScreenPosition = mMap.getProjection().toScreenLocation(latLng);
            Log.d("location", "x: " + markerScreenPosition.x+ "     y: " + markerScreenPosition.y);
			Log.d("location", "la: " + latLng.latitude+ "     lo: " + latLng.longitude);
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
            //launch_altitude_dialog(newMarker, "NEW");


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

  }*/


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

  //Refresh markers, the updates here to the ac icon are used but everything else remains from if
	//we ever wanted to reimplement the ability to move markers loaded from flightplan
/*
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
*/

  /*private boolean checkReady() {
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
  }*/

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
        mLibVLC.play();
        if (DisableScreenDim) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        logger.closeLogger();
		//mLibVLC.stop();
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
		//

        // The following call resumes a paused rendering thread.
        // If you de-allocated graphic objects for onPause()
        // this is a good place to re-allocate them.
        //mGLView.onResume();
    }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

	  setContentView(R.layout.activity_main_two_panel);
	  mMediaUrl1 = "file:///sdcard/DCIM/video1.sdp";
	  mMediaUrl2 = "file:///sdcard/DCIM/video2.sdp";

	  mSurfaceView1 = (SurfaceView) findViewById(R.id.player_surface);
	  mSurfaceView2 = (SurfaceView) findViewById(R.id.player_surface_small);
	  mSurfaceHolder1 = mSurfaceView1.getHolder();
	  mSurfaceHolder2 = mSurfaceView2.getHolder();

	  mSurfaceFrame1 = (FrameLayout) findViewById(R.id.player_surface_frame);
	  mSurfaceFrame2 = (FrameLayout) findViewById(R.id.player_surface_frame_small);
	  //mMediaUrl = getIntent().getExtras().getString("videoUrl");
	  try {
		  mLibVLC2 = new LibVLC();
		  mLibVLC2.setAout(mLibVLC2.AOUT_AUDIOTRACK);
		  mLibVLC2.setVout(mLibVLC2.VOUT_ANDROID_SURFACE);
		  mLibVLC2.setHardwareAcceleration(LibVLC.HW_ACCELERATION_AUTOMATIC);
		  mLibVLC2.setChroma("YV12");
		  mLibVLC2.init(getApplicationContext());

	  } catch (LibVlcException e){
		  Log.e(TAG, e.toString());
	  }

	  mSurface2 = mSurfaceHolder2.getSurface();
	  //mSurface2 = mSurfaceHolder2.getSurface();
	  mLibVLC2.attachSurface(mSurface2, MainActivity.this);
	  //mLibVLC2.attachSurface(mSurface2,Main.this);

	  temp_options = mLibVLC2.getMediaOptions(0);
	  List<String> options_list = new ArrayList<String>(Arrays.asList(temp_options));


	  options_list.add(":file-caching=2000");
	  options_list.add(":network-caching=1");
	  options_list.add(":clock-jitter=0");
	  options_list.add("--clock-synchro=1");
	  new_options = options_list.toArray(new String[options_list.size()]);
	  mLibVLC2.playMRL(mMediaUrl2,new_options);
	  //mLibVLC2.playMRL(mMediaUrl2,new_options);

    //setContentView(R.layout.activity_main_two_panel);
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

  /*//Settings change listener
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

  }*/

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

    /*if (ChangeVisibleAcButton.isChecked()) {
      Toast.makeText(getApplicationContext(), "Showing only " + AC_DATA.AircraftData[AC_DATA.SelAcInd].AC_Name + " markers", Toast.LENGTH_SHORT).show();
      ShowOnlySelected = true;
    } else {
      Toast.makeText(getApplicationContext(), "Showing all markers", Toast.LENGTH_SHORT).show();
      ShowOnlySelected = false;
    }*/

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
        /*if(System.currentTimeMillis() % 10 == 0 && logger != null){
			logger.logEvent(AC_DATA.AircraftData[0], EventLogger.NO_EVENT, -1);
		}*/
      }

      return null;
    }


	  public void refresh_map_data(){
		  if (AC_DATA.AircraftData[0].AC_Marker1 == null) {
			  add_AC_to_map();
		  }

		  if(AC_DATA.AircraftData[0].AC_Marker2 == null){
			  add_AC_to_map();
		  }

		  if (AC_DATA.AircraftData[0].AC_Enabled && AC_DATA.AircraftData[0].AC_Position_Changed) {
			  AC_DATA.AircraftData[0].AC_Marker1.setPosition(convert_to_lab(AC_DATA.AircraftData[0].Position));
			  AC_DATA.AircraftData[0].AC_Marker1.setRotation(Float.parseFloat(AC_DATA.AircraftData[0].Heading));

			  AC_DATA.AircraftData[0].AC_Marker2.setPosition(convert_to_lab(AC_DATA.AircraftData[0].Position));
			  AC_DATA.AircraftData[0].AC_Marker2.setRotation(Float.parseFloat(AC_DATA.AircraftData[0].Heading));
			  AC_DATA.AircraftData[0].AC_Position_Changed = false;
		  }


	  }

    @Override
    protected void onProgressUpdate(String... value) {
      super.onProgressUpdate(value);
      refresh_map_data();
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
        //refresh_markers();

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
		Button_LandHere.setSelected(false);
	}

	//this method converts the google latitudes to the corresponding points on the transposed image
	//use this method to draw the markers in the right spots
	public static LatLng convert_to_lab(LatLng position){
		double oldLat = position.latitude;
		double oldLong = position.longitude;

		double newLat = (3.83*oldLat - 101.8953981)+0.00007620161056;
		double newLong = (4.05*oldLong + 240.7700822)-0.000082485140550;

		LatLng newPosition = new LatLng(newLat, newLong);
		return newPosition;
	}

	//this method converts the fake latitudes back to the actual google values
	//use this for any information that paparazzi needs about where to actually send the drone
	public static LatLng convert_to_google(LatLng position){
		double oldLat = position.latitude;
		double oldLong = position.longitude;

		double newLat = (oldLat + 144.021756 - 0.000093301610565)/5;
		double newLong = (oldLong - 343.3933874 + 0.000112485140550)/5.35;

		//double newLat = (oldLat + 144.021756)/5;
		//double newLong = (oldLong - 343.3933874)/5.35;

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

    /*public boolean outsideBounds(LatLng latLng){
        Point currentPoint = mMap.getProjection().toScreenLocation(latLng);
        int x = currentPoint.x;
        int y = currentPoint.y;

        return x > 1500 || x < 422 || y < 167 || y >922;
    }*/
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
