package ca.uwaterloo.lab4_202_16_rev10;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import mapper.MapLoader;
import mapper.MapView;
import mapper.NavigationalMap;
import mapper.PositionListener;
import android.annotation.TargetApi;
import android.app.Activity;
import android.graphics.PointF;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

@TargetApi(Build.VERSION_CODES.GINGERBREAD)
public class MainActivity extends Activity implements SensorEventListener, PositionListener {
	TextView tv;
	MapView mv;
	
	NavigationalMap m;
	int state = 0;
	
	float[] accelVals, magVals, orientationVals = new float[3];
	float[] rotationMatrix = new float[16];
	
	float[] low, xyz;
	
	float deltaX, deltaY = 0.0f;
	
	static final int ACC = Sensor.TYPE_ACCELEROMETER;
	static final int MAG = Sensor.TYPE_MAGNETIC_FIELD;
	static final int LAC = Sensor.TYPE_LINEAR_ACCELERATION;
	
	static final int RATE_US = SensorManager.SENSOR_DELAY_FASTEST;
	
	static final float MAX = 3.11f;
	static final float MIN = -0.09f;
	static final float TRIG = 2.48f;
	
	static final float ONE_FOOT_IN_METERS = 0.3048f;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		LinearLayout l = (LinearLayout) findViewById(R.id.top_level_layout);
		
		tv = new TextView(this);
		Button b = new Button(this);
		mv = new MapView(this, 600, 600, 30, 30);
		
		mv.setVisibility(View.VISIBLE);
		tv.setVisibility(View.VISIBLE);
		b.setVisibility(View.VISIBLE);

		l.addView(mv);
		l.addView(tv);
		l.addView(b);
		
		b.setOnClickListener( new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				state = 0;
				mv.setUserPoint(-1,-1);
				mv.setDestinationPoint(mv.getUserPoint());
			}
			
		});
		b.setText("Reset");
		
		SensorManager sm = (SensorManager) getSystemService(SENSOR_SERVICE);
		
		sm.registerListener(this, sm.getDefaultSensor(ACC), RATE_US);
		sm.registerListener(this, sm.getDefaultSensor(MAG), RATE_US);
		sm.registerListener(this, sm.getDefaultSensor(LAC), RATE_US);
		
		registerForContextMenu(mv);
		m = MapLoader.loadMap(getExternalFilesDir(null)
				, "Lab-room-peninsula.svg");
		mv.setMap(m);
		
		mv.addListener(this);
	}
	
	@Override
	public void onSensorChanged(SensorEvent se) {
		int s = se.sensor.getType();
		
		synchronized (this) {
			PointF me = mv.getUserPoint();
			PointF u = mv.getDestinationPoint();
			
			float azimuth = (float) (orientationVals[0] + Math.toRadians(9.42));
			
			deltaX = u.x - me.x;
			deltaY = me.y - u.y;
			
			PointF next = new PointF(me.x + (float)Math.cos(azimuth)*ONE_FOOT_IN_METERS
					, me.y + (float)Math.sin(azimuth)*ONE_FOOT_IN_METERS);
			
			if(accelVals != null && magVals != null) {
				if(SensorManager.getRotationMatrix(rotationMatrix, null, accelVals, magVals)) {
					SensorManager.getOrientation(rotationMatrix, orientationVals);
				}
			}
			
			if(s == LAC) {
				xyz = Arrays.copyOfRange(se.values, 0, 3);
				low = (low == null) ? new float[] {0,0,0} : low;
				
				lowPass(low, xyz, 0.9f);
				if(state == 0 && low[2] < MIN) { // negative
					state = 1;
				} else if(state == 1 && low[2] < MAX && low[2] > TRIG) { // high
					state = 2;
				} else if(state == 2 && low[2] > MAX) { // peak
					state = 3;
				} else if(state == 3 && low[2] < TRIG && low[2] > MIN) { // low
					state = 0;
					
					if(m.calculateIntersections(me, next).isEmpty()) {
						mv.setUserPoint(next);
						findPathToDestination();
					}
				} else if(state < 0 || state > 3) {
					System.exit(-1); // bad state.
				} else {
					// no change in state, do nothing.
				}
			} else {
				switch(s) {
				case(MAG): magVals = Arrays.copyOfRange(se.values, 0, 3);
				break;
				case(ACC): accelVals = Arrays.copyOfRange(se.values, 0, 3);
				break;
				}
			}
		}
	}

	private void informUserOfPath() {
		if(mv.getUserPoint().y < 8.37 && Math.abs(deltaX) > 1.5) {
			float height = (float) (8.37 - mv.getUserPoint().y);
			tv.setText(String.format("Travel %f m South", height));
		} else {
			if(mv.getDestinationPoint().y > 8.37) {
				float width = deltaX;
				if(deltaX > 0) {
					tv.setText(String.format("Travel %f m East", width));
				} else if (deltaX < 0) {
					tv.setText(String.format("Travel %f m West", -1*width));
				}
			}
			else if(mv.getDestinationPoint().y < 8.37) {
				if(Math.abs(deltaX) > 1.5) {
					float width = deltaX;
					if(deltaX > 0) {
						tv.setText(String.format("Travel %f m East", width));
					} else if (deltaX < 0) {
						tv.setText(String.format("Travel %f m West", -1*width));
					}
				} else {
					float height = mv.getUserPoint().y - mv.getDestinationPoint().y;
					tv.setText(String.format("Travel %f m North", height));
				}
			}
			else {
				if(deltaX > 0 && deltaY > 0) {
					tv.setText(String.format("Travel %f m East and %f m North.", deltaX, deltaY));
				} else if(deltaX < 0 && deltaY > 0) {
					tv.setText(String.format("Travel %f m West and %f m North.", -1*deltaX, deltaY));
				} else if(deltaX > 0 && deltaY < 0) {
					tv.setText(String.format("Travel %f m East and %f m South.", deltaX, -1*deltaY));
				} else if(deltaX < 0 && deltaY < 0) {
					tv.setText(String.format("Travel %f m West and %f m South.", -1*deltaX, -1*deltaY));
				}
			}
		}
		
		if(Math.abs(deltaX) < 1 && Math.abs(deltaY) < 1) tv.setText("Destination Reached");
	}

	@Override
	public void onAccuracyChanged(Sensor s, int i) {
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		mv.onCreateContextMenu(menu, v, menuInfo);
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		return super.onContextItemSelected(item) || mv.onContextItemSelected(item);
	}
	
	/**
	 * low pass filter
	 * @param smooth The filtered values
	 * @param in The raw values
	 * @param C the attenuation
	 */
	public void lowPass(float[] smooth, float[] in, float C) {
		for(int i = 0; i < in.length; i++) smooth[i] += (in[i] - smooth[i]) / C;
	}
	
	/**
	 * high pass filter
	 * @param in The raw values
	 * @param dt The differential element (time)
	 * @param RC The impedance
	 * @return float The filtered values
	 */
	public float[] highPass(float[] in, float dt, float RC) {
		float[] out = new float[in.length];
		float a = RC / (RC + dt);
		out[0] = in[0];
		for(int i = 1; i < in.length; i++) {
			out[i] = a * out[i-1] + a * (in[i] - in[i-1]);
		}
		return out;
	}

	@Override
	public void originChanged(MapView source, PointF loc) {
		source.setOriginPoint(loc);
		source.setUserPoint(loc);
		findPathToDestination();
	}

	@Override
	public void destinationChanged(MapView source, PointF dest) {
		source.setDestinationPoint(dest);
		findPathToDestination();
	}

	private void findPathToDestination() {
		PointF user = mv.getUserPoint();
		PointF stop = mv.getDestinationPoint();
		List<PointF> ret = new ArrayList<PointF>();
		
		PointF next, last;
		float delta = user.y - stop.y;
		
		last = new PointF(user.x, user.y);
		next = new PointF(stop.x, stop.y + delta);
		
		if(user.y < 8.37) { // go horizontally
			while(!m.calculateIntersections(last, next).isEmpty()) {
				last.set(last.x, last.y + .1f);
				next.set(next.x, next.y + .1f);
			}
		} else if (stop.y < 8.37 || stop.y > 10.85 || user.y > 10.85) {
			while(!m.calculateIntersections(last, next).isEmpty()) {
				last.set(last.x, last.y - .1f);
				next.set(next.x, next.y - .1f);
			}
		}

		ret.add(user);
		ret.add(last);
		ret.add(next);
		ret.add(stop);
		
		informUserOfPath();
		mv.setUserPath(ret);
	}

}
