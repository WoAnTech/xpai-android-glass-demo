package cn.com.xpai4glass;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import cn.com.xpai4glass.R;
import cn.com.xpai.core.Manager;
import cn.com.xpai.core.RecordMode;
import cn.com.xpai4glass.demo.player.FilelistActivity;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.gesture.Gesture;
import android.graphics.Rect;
import android.hardware.Camera;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.OnGestureListener;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

@TargetApi(Build.VERSION_CODES.ECLAIR)
public class XPAndroid extends Activity  {
	/** Called when the activity is first created. */
	private SurfaceView mPreview = null;
	private static String TAG = "XPAndroid";
	private static XPAndroid instance = null;

	private static Menu menu = null;

	//private GestureDetector mDetector;

	static String lastPictureFileName = null;

	static MainHandler mainHandler;
	
	private GestureDetector mGestureDetector;

	static Menu getMenu() {
		return menu;
	}

	public final static int MENU_UPLOAD_PICTURE = 20004;
	public final static int MENU_UPLOAD_VF_WHOLE = 20013;
	public final static int MENU_UPLOAD_VF = 20014;

	public static XPAndroid getInstance() {
		return instance;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// Hide the window title.
		this.requestWindowFeature(Window.FEATURE_NO_TITLE);
		instance = this;
		XPHandler.register(this);
		Config.load(this);
		if (0 != Manager.init(this, XPHandler.getInstance())) {
			Log.e(TAG, "init core manager failed");
		}
		Manager.setVideoFpsRange(30, 30);
		List<Manager.Resolution> res_list = Manager
				.getSupportedVideoResolutions();
		if (null != res_list && res_list.size() > 0) {
			if (0 == Config.videoWidth || 0 == Config.videoHeight) {
				// 使用第一个可用分辨率作为默认分辨率
				Manager.Resolution res = res_list.get(0);
				Config.videoWidth = res.width;
				Config.videoHeight = res.height;
			}
		} else {
			Log.e(TAG, "cannto get supported resolutions");
		}
		Manager.setVideoResolution(Config.videoWidth, Config.videoHeight);
		/* 当缓冲超过10240字节时，开始降帧，最多每帧间隔300ms，即降到3.3帧 */
		Manager.setNetWorkingAdaptive(true);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		setContentView(R.layout.main);
		mPreview = (SurfaceView) findViewById(R.id.preview_view);
		mainHandler = new MainHandler(this);
		//mDetector = new GestureDetector(this, this);
		mGestureDetector = new GestureDetector(this, new GlassDPadController(this));
		// Set the gesture detector as the double tap
		// listener.
	}

	/* Creates the menu items */
	@Override
	public boolean onCreateOptionsMenu(Menu m) {
		menu = m;
		menu.add(0, MENU_UPLOAD_PICTURE, 0, "上传照片");
		menu.add(0, MENU_UPLOAD_VF_WHOLE, 0, "上传离线录制的文件");
		menu.add(0, MENU_UPLOAD_VF, 0, "续传视频文件");
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu m) {
		super.onPrepareOptionsMenu(m);
		if (Manager.RecordStatus.IDLE != Manager.getRecordStatus()) {
			m.findItem(MENU_UPLOAD_PICTURE).setEnabled(false);
			m.findItem(MENU_UPLOAD_VF).setEnabled(false);
			m.findItem(MENU_UPLOAD_VF_WHOLE).setEnabled(false);
		} else {
			m.findItem(MENU_UPLOAD_PICTURE).setEnabled(true);
			m.findItem(MENU_UPLOAD_VF).setEnabled(true);
			m.findItem(MENU_UPLOAD_VF_WHOLE).setEnabled(true);
		}
		if (Manager.isConnected()
				&& Manager.RecordStatus.IDLE != Manager.getRecordStatus()) {
			m.findItem(MENU_UPLOAD_PICTURE).setEnabled(false);
			m.findItem(MENU_UPLOAD_VF).setEnabled(false);
		}

		if (!Manager.isConnected()) {
			m.findItem(MENU_UPLOAD_PICTURE).setEnabled(false);
			m.findItem(MENU_UPLOAD_VF).setEnabled(false);
		}

		if (lastPictureFileName == null) {
			m.findItem(MENU_UPLOAD_PICTURE).setEnabled(false);
		}

		return true;
	}

	/* Handles item selections */
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case MENU_UPLOAD_PICTURE:
			if (null != lastPictureFileName) {
				Manager.uploadFile(lastPictureFileName);
				Log.v(TAG, "upload file name:" + lastPictureFileName);
			} else {
				Message msg = new Message();
				msg.what = XPHandler.SHOW_MESSAGE;
				Bundle bdl = new Bundle();
				bdl.putString(XPHandler.MSG_CONTENT, "未找到最近拍摄的照片!");
			}
			return true;
		case MENU_UPLOAD_VF_WHOLE:
			Intent intent = new Intent(this, FileChooser.class);
			startActivityForResult(intent, 0);
			return true;
		case MENU_UPLOAD_VF:
			intent = new Intent(this, FileChooser.class);
			startActivityForResult(intent, 1);
			return true;
		}
		return false;
	}

	protected void onDestroy() {
		Log.i(TAG, "mini app destroy");
		XPHandler.getInstance().exitApp();
		Manager.deInit();
		super.onDestroy();
		System.exit(0);
	}

	/* 覆盖 onActivityResult() */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent i) {
		switch (resultCode) {
		case RESULT_OK:
			/* 取得来自Activity2的数据，并显示于画面上 */
			Bundle b = i.getExtras();
			String file_name = b.getString("file_name");
			Log.i(TAG, "Get file name:" + file_name);
			// Manager.uploadVideoFile(..., false)
			// 第二个参数为 false代表新上传一个文件, 服务器总是将上传的数据存为一个新的视频文件
			// 第二个参数为 true 代表续传
			if (!Manager.uploadVideoFile(file_name, requestCode == 1)) {
				// todo 错误处理
				Log.w(TAG, "Upload file failed.");
			}
			break;
		default:
			break;
		}
	}
	
	private int currentExposureCompensation = 0;
	@SuppressLint("NewApi")

	@Override
	public boolean onKeyDown(int keycode, KeyEvent event) {
		if (keycode == KeyEvent.KEYCODE_DPAD_CENTER) {
			Log.i(TAG, "key down");
			//setMeteringAreas();
			return true;
		}
		return super.onKeyDown(keycode, event);
	}

	@Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        mGestureDetector.onTouchEvent(event);
        return true;
    }
	
}
