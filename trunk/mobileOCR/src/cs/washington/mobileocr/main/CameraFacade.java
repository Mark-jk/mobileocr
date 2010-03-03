package cs.washington.mobileocr.main;

import java.io.IOException;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.hardware.Camera.ErrorCallback;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.ShutterCallback;
import android.hardware.Camera.Size;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.SurfaceHolder;

public class CameraFacade implements SurfaceHolder.Callback {

	public static final String CAMERA_TAG = "CameraFacade";

	private boolean mAutoFocusInProgress;
	private boolean mPreviewCaptureInProgress;

	public static final int AUTOFOCUS_UNKNOWN = 0;
	public static final int AUTOFOCUS_SUCCESS = 1;
	public static final int AUTOFOCUS_FAILURE = 2;
	private int mAutoFocusStatus;

	private Handler mUIHandler = null;
	private SurfaceHolder mHolder;
	private Camera mCamera;
	private int mx;
	private int my;
	private boolean surfaceExists;
	private boolean mPreviewRunning;

	public CameraFacade(SurfaceHolder holder, Handler UIHandler) {    
		mHolder = holder;
		mHolder.addCallback(this);
		mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		mCamera = null;
		mx = my = 0;
		surfaceExists = mPreviewRunning = false;
		mUIHandler = UIHandler;
	}

	public void onResume() {
		if(surfaceExists && mCamera == null) {
			startCamera();
			setCameraParameters();
		}
	}

	public void onPause() {
		if(mCamera != null) {
			if(mPreviewRunning) {
				mCamera.stopPreview();
				mPreviewRunning = false;
			}
			mCamera.release();
			mCamera = null;
		}
	}

	public void surfaceCreated(SurfaceHolder holder) {
		Log.d(CAMERA_TAG,"Surface Created");
		if(mHolder == null)
			mHolder = holder;
		startCamera();
		surfaceExists = true;
	}

	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {
		// Now that the size is known, set up the camera parameters and begin
		// the preview.
		Log.i(CAMERA_TAG,"call to surfaceChanged()");
		if(mCamera == null)
			return;
		mx = width;
		my = height;
		setCameraParameters();


	}

	public void startPreview()
	{
		if(!mPreviewRunning)
			mCamera.startPreview();

		mPreviewRunning = true;
	}

	private void startCamera() {
		if(mCamera == null)
			mCamera = Camera.open();
		try {
			mCamera.setPreviewDisplay(mHolder); // throws IOException
			mCamera.setErrorCallback(new ErrorCallback() {
				public void onError(int code, Camera c) {
					if(code == Camera.CAMERA_ERROR_SERVER_DIED)
						Log.e(CAMERA_TAG,"The camera server died");
					else
						Log.e(CAMERA_TAG,"Unknown camera error");
				}
			});
		}
		catch(IOException ioe) {
			mCamera.release();
			mCamera = null;
		}
		mPreviewRunning = false;
	}

	private void setCameraParameters() {
		Camera.Parameters parameters = mCamera.getParameters();
		parameters.setPictureSize(mx, my);
		parameters.setPictureFormat(PixelFormat.JPEG);

		mCamera.setParameters(parameters);
		if(!mPreviewRunning)
			mCamera.startPreview();

		mPreviewRunning = true;

		initCameraStateVariables();
	}

	public void surfaceDestroyed(SurfaceHolder holder) {

		surfaceExists = false;
		Log.i(CAMERA_TAG,"Surface destroyed! mPreviewRunning = " + mPreviewRunning);
		if(mCamera == null) {
			mPreviewRunning = false; // it probably should've been already anyways
			return;
		}
		if(mPreviewRunning) {
			mCamera.stopPreview();
			mPreviewRunning = false;
		}
		Log.i(CAMERA_TAG,"We've called stopPreview() (perhaps), but not yet released the camera");
		mCamera.release();
		mCamera = null;
		mHolder = null;
	}

	public void getPreview(Camera.PreviewCallback callback) {
		if(mCamera == null)
			return;
		mCamera.setPreviewCallback(callback);
		Log.i(CAMERA_TAG ,callback==null?"Stopping previews":"Starting to request preview frames");
	}

	public void requestAutoFocus () {
		if (mAutoFocusInProgress || mPreviewCaptureInProgress) {
			return;
		}
		mAutoFocusStatus = AUTOFOCUS_UNKNOWN;
		mAutoFocusInProgress = true;
		mCamera.autoFocus(new Camera.AutoFocusCallback() { 

			public void onAutoFocus(boolean success, Camera camera) {
				Message msg = mUIHandler.obtainMessage(R.id.msg_camera_auto_focus, 
						success ? AUTOFOCUS_SUCCESS : AUTOFOCUS_FAILURE, -1);
				mUIHandler.sendMessage(msg);

			}
		});
	}

	public void clearAutoFocus() {
		mAutoFocusInProgress = false;
	}

	public void requestPreviewFrame () {
		if (mAutoFocusInProgress || mPreviewCaptureInProgress) {
			return;
		}

		mPreviewCaptureInProgress = true;

		mCamera.takePicture(shutterCallback, null, jpegCallback);
		/*mPreviewCaptureInProgress = true;
        mCamera.setOneShotPreviewCallback(new Camera.PreviewCallback() {

            public void onPreviewFrame(byte[] data, Camera camera) {
                Message msg = mUIHandler.obtainMessage(R.id.msg_camera_preview_frame, data);
                mUIHandler.sendMessage(msg);
            }
        });*/
	}

	public void clearPreviewFrame() {
		mPreviewCaptureInProgress = false;
	}

	public Size getPreviewSize() {
		if (mPreviewRunning) {
			return mCamera.getParameters().getPreviewSize();
		}
		return null;
	}

	public int getWidth() {
		return mx;
	}

	public int getHeight() {
		return my;
	}

	private void initCameraStateVariables () {
		mAutoFocusStatus = AUTOFOCUS_UNKNOWN;
		mAutoFocusInProgress = false;
		mPreviewCaptureInProgress = false;
	}

	private ShutterCallback shutterCallback = new ShutterCallback() {
		public void onShutter() {
			setCameraParameters();
		}
	};

	private PictureCallback jpegCallback = new PictureCallback() {
		public void onPictureTaken(byte[] data, Camera camera) {
			if (data != null) {
				Message picDataMsg = mUIHandler.obtainMessage(R.id.msg_camera_preview_frame, data);
				mUIHandler.sendMessage(picDataMsg);
			}
			else {
				mCamera.startPreview();
			}
			mPreviewCaptureInProgress = false;
		}

	};
}