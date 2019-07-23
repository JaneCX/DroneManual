package com.manual;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

/**
 * Created by benwelton on 2/24/17.
 */

public class JStick {

    private Context mContext;
    private ViewGroup mLayout;
    public ViewGroup.LayoutParams params;
    private int stick_width, stick_height;
    private String stick_type;

    public int position_x = 0, position_y = 0, min_distance = 0, stored_throttle;
    public float distance = 0;
    public float angle = 0;

    private Bitmap stick;
    private DrawCanvas draw;
    private Paint paint;

    public int OFFSET = 50;

    private boolean touch_state = false;

    public JStick(Context context, ViewGroup layout, int stick_res_id, String type) {
        mContext = context;
        mLayout = layout;

        stick = BitmapFactory.decodeResource(mContext.getResources(), stick_res_id);

        stick_width = stick.getWidth();
        stick_height = stick.getHeight();
        stick_type = type;

        draw = new DrawCanvas(mContext);
        paint = new Paint();
        params = mLayout.getLayoutParams();
    }

    //draws the movable joystick
    private void draw() {
        try {
            mLayout.removeView(draw);
        } catch (Exception e) { }
        mLayout.addView(draw);
    }

    public void drawStick(MotionEvent arg1) {
        position_x = (int) (arg1.getX() - (params.width / 2));
        position_y = (int) ((params.height/2)- arg1.getY());
        distance = (float) Math.sqrt(Math.pow(position_x, 2) + Math.pow(position_y, 2));
        angle = (float) cal_angle(position_x, position_y);

        if(arg1.getAction() == MotionEvent.ACTION_DOWN) {
            if(distance <= (params.width / 2) - OFFSET) {
                draw.position(arg1.getX(), arg1.getY());
                draw();
                touch_state = true;
            }
        }
        else if(arg1.getAction() == MotionEvent.ACTION_MOVE && touch_state) {
            if(distance <= (params.width / 2) - OFFSET) {
                draw.position(arg1.getX(), arg1.getY());
                draw();
            }

            //edge case
            else if(distance > (params.width / 2) - OFFSET){
                float x = (float) (Math.cos(Math.toRadians(cal_angle(position_x, position_y)))
                        * ((params.width / 2) - OFFSET));
                float y = (float) (Math.sin(Math.toRadians(cal_angle(position_x, position_y)))
                        * ((params.height / 2) - OFFSET));

                //make sure to reset the position so that the values don't track outside the pad
                position_x = (int) x;
                position_y = (int) y;

                //ensures the drawing appears on the edge doesn't stop early bc of offset
                x += (params.width / 2);
                y = (params.height / 2) - y;
                draw.position(x, y);
                draw();
            }
            else {
                mLayout.removeView(draw);
            }
        }
        else if(arg1.getAction() == MotionEvent.ACTION_UP) {
            if(stick_type.equals("PITCH")||stick_type.equals("YAW")) {
                draw.position(params.width / 2, params.height / 2);
                draw();
            }
            else{
                mLayout.removeView(draw);
            }

            touch_state = false;
        }
    }

    public int getX() {
        if(touch_state) {
            if(position_x > 127) return 127;
            if(position_x < -127) return -127;
            return position_x;
        }
        return 0;
    }

    public int getY() {
        if(touch_state) {
            if(position_y > 127) return 127;
            if(position_y < -127) return -127;
            return position_y;
        }
        return 0;
    }

    public int getThrottle() {
        if(touch_state) {
            if (position_y > 100) return 100;
            if (position_y < -127) return -127;
        }
        return position_y;
    }

    private double cal_angle(float x, float y) {
        if(x >= 0 && y >= 0)
            return Math.toDegrees(Math.atan(y / x));
        else if(x < 0)
            return Math.toDegrees(Math.atan(y / x)) + 180;
        else if(x >= 0 && y < 0)
            return Math.toDegrees(Math.atan(y / x)) + 360;
        return 0;
    }

    private class DrawCanvas extends View {
        float x, y;

        private DrawCanvas(Context mContext) {
            super(mContext);
        }

        public void onDraw(Canvas canvas) {
            canvas.drawBitmap(stick, x, y, paint);
        }

        private void position(float pos_x, float pos_y) {
            x = pos_x - (stick_width / 2);
            y = pos_y - (stick_height / 2);
        }
    }

}
