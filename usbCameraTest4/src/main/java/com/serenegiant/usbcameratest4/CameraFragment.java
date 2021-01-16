/*
 *  UVCCamera
 *  library and sample to access to UVC web camera on non-rooted Android device
 *
 * Copyright (c) 2014-2017 saki t_saki@serenegiant.com
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 *  All files in the folder are under this Apache License, Version 2.0.
 *  Files in the libjpeg-turbo, libusb, libuvc, rapidjson folder
 *  may have a different license, see the respective files.
 */

package com.serenegiant.usbcameratest4;

import java.util.List;

import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import com.serenegiant.common.BaseFragment;
import com.serenegiant.service.UVCService;
import com.serenegiant.serviceclient.CameraClient;
import com.serenegiant.serviceclient.ICameraClient;
import com.serenegiant.serviceclient.ICameraClientCallback;

import com.serenegiant.usb.CameraDialog;
import com.serenegiant.usb.DeviceFilter;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.USBMonitor.OnDeviceConnectListener;
import com.serenegiant.usb.USBMonitor.UsbControlBlock;
import com.serenegiant.widget.CameraViewInterface;

public class CameraFragment extends BaseFragment {

	private static final boolean DEBUG = true;
	private static final String TAG = "CameraFragment";

	private static final int DEFAULT_WIDTH = 640;
	private static final int DEFAULT_HEIGHT = 480;

	private USBMonitor mUSBMonitor;
	private ICameraClient mCameraClient;

	private ImageButton mRecordButton;
	private TextView mRecordingTime;
	private CameraViewInterface mCameraView;

	private Surface addedSurface = null;

	private boolean  attachWasSkipped = false;

	public CameraFragment() {
		if (DEBUG) Log.v(TAG, "Constructor:");
//		setRetainInstance(true);
	}

	@SuppressWarnings("deprecation")
	@Override
	public void onAttach(final Activity activity) {
		super.onAttach(activity);
		if (DEBUG) Log.v(TAG, "onAttach:");
	}

	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (DEBUG) Log.v(TAG, "onCreate:");
		if (mUSBMonitor == null) {
			mUSBMonitor = new USBMonitor(getActivity().getApplicationContext(), mOnDeviceConnectListener);
			final List<DeviceFilter> filters = DeviceFilter.getDeviceFilters(getActivity(), R.xml.device_filter);
			mUSBMonitor.setDeviceFilter(filters);
		}
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
		if (DEBUG) Log.v(TAG, "onCreateView:");
		final View rootView = inflater.inflate(R.layout.fragment_main, container, false);
		View view = rootView.findViewById(R.id.start_button);
		view.setOnClickListener(mOnClickListener);
		view =rootView.findViewById(R.id.stop_button);
		view.setOnClickListener(mOnClickListener);
		mRecordButton = (ImageButton)rootView.findViewById(R.id.record_button);
		mRecordButton.setOnClickListener(mOnClickListener);
		mRecordButton.setEnabled(false);
		mCameraView = (CameraViewInterface)rootView.findViewById(R.id.camera_view);
		mCameraView.setAspectRatio(DEFAULT_WIDTH / (float)DEFAULT_HEIGHT);
		mCameraView.setCallback( mCallback );
		mRecordingTime = (TextView) rootView.findViewById(R.id.recording_time);
		mRecordingTime.setText("");
		return rootView;
	}

	@Override
	public void onResume() {
		super.onResume();
		if (DEBUG) Log.v(TAG, "onResume:");
		mUSBMonitor.register();
	}

	@Override
	public void onPause() {
		if (DEBUG) Log.v(TAG, "onPause:");
		if (mCameraClient != null) {
			if (addedSurface != null)
				mCameraClient.removeSurface(addedSurface);
			addedSurface = null;
		}
		mUSBMonitor.unregister();
		enableButtons(false);
		updateRecordingTime();
		super.onPause();
	}

	@Override
	public void onDestroyView() {
		if (DEBUG) Log.v(TAG, "onDestroyView:");
		super.onDestroyView();
	}

	@Override
	public void onDestroy() {
		if (DEBUG) Log.v(TAG, "onDestroy:");
		//Disconnect and release CameraClient completely.
		//if device is recording, service will not be released.
		//We will be able to connect to the same device and stop recording when CameraFragment is created
		//make sure we disconnect listener, otherwise onDisconnect event might come too late, after object is destroyed
		//doReleaseCameraClient( true );

		if (mCameraClient != null) {
			mCameraClient.disconnectListener();
			if ( !mCameraClient.isRecording() ) mCameraClient.disconnect();
			mCameraClient.release();
			mCameraClient = null;
		}

		super.onDestroy();
	}

	@Override
	public void onDetach() {
		if (DEBUG) Log.v(TAG, "onDetach:");
		super.onDetach();
	}

	public USBMonitor getUSBMonitor() {
		return mUSBMonitor;
	}

	private final OnDeviceConnectListener mOnDeviceConnectListener = new OnDeviceConnectListener() {
		@Override
		public void onAttach(final UsbDevice device) {
			if (DEBUG) Log.v(TAG, "OnDeviceConnectListener#onAttach:");
			if (!updateCameraDialog() && (mCameraView.hasSurface())) {
				attachWasSkipped = false;
				tryOpenUVCCamera(true);
			}
			else {
				attachWasSkipped = true;
			}
		}

		@Override
		public void onConnect(final UsbDevice device, final UsbControlBlock ctrlBlock, final boolean createNew) {
			if (DEBUG) Log.v(TAG, "OnDeviceConnectListener#onConnect:");
		}

		@Override
		public void onDisconnect(final UsbDevice device, final UsbControlBlock ctrlBlock) {
			if (DEBUG) Log.v(TAG, "OnDeviceConnectListener#onDisconnect:");
		}

		@Override
		public void onDettach(final UsbDevice device) {
			if (DEBUG) Log.v(TAG, "OnDeviceConnectListener#onDettach:");
			queueEvent(new Runnable() {
				@Override
				public void run() {
					doReleaseCameraClient(false);
				}
			}, 0);
			enableButtons(false);
			updateRecordingTime();
			updateCameraDialog();
		}

		@Override
		public void onCancel(final UsbDevice device) {
			if (DEBUG) Log.v(TAG, "OnDeviceConnectListener#onCancel:");
			enableButtons(false);
			updateRecordingTime();
		}
	};

	private boolean updateCameraDialog() {
		final Fragment fragment = getFragmentManager().findFragmentByTag("CameraDialog");
		if (fragment instanceof CameraDialog) {
			((CameraDialog)fragment).updateDevices();
			return true;
		}
		return false;
	}

	private void tryOpenUVCCamera(final boolean requestPermission) {
		if (DEBUG) Log.v(TAG, "tryOpenUVCCamera:");
		openUVCCamera(0);
	}

	private void openUVCCamera(final int index) {
		if (DEBUG) Log.v(TAG, "openUVCCamera:index=" + index);
		if (!mUSBMonitor.isRegistered()) return;
		final List<UsbDevice> list = mUSBMonitor.getDeviceList();
		if (list.size() > index) {
			enableButtons(false);
			if (mCameraClient == null)
				mCameraClient = new CameraClient(getActivity().getApplicationContext(), mCameraListener);
			mCameraClient.select(list.get(index));
			mCameraClient.resize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
			mCameraClient.connect();
		}
	}

	private final ICameraClientCallback mCameraListener = new ICameraClientCallback() {
		@Override
		public void onConnect() {
			if (DEBUG) Log.v(TAG, "onConnect:");
			addedSurface = mCameraView.getSurface();
			mCameraClient.addSurface(addedSurface, false);
			enableButtons(true);
			updateRecordingTime();
			// start UVCService
			final Intent intent = new Intent(getActivity(), UVCService.class);
			getActivity().startService(intent);
		}

		@Override
		public void onDisconnect() {
			if (DEBUG) Log.v(TAG, "onDisconnect:");
			enableButtons(false);
			updateRecordingTime();
		}

		@Override
		public void onRecordingTimeChanged(boolean isRecording, int recordingTimeSeconds) {
			updateRecordingTime();
		}

	};

	private final OnClickListener mOnClickListener = new OnClickListener() {
		@Override
		public void onClick(final View v) {
			int id = v.getId();
			if (id == R.id.start_button) {
				if (DEBUG) Log.v(TAG, "onClick:start");
				// start service
				final List<UsbDevice> list = mUSBMonitor.getDeviceList();
				if (list.size() > 0) {
					if (mCameraClient == null)
						mCameraClient = new CameraClient(getActivity().getApplicationContext(), mCameraListener);
					mCameraClient.select(list.get(0));
					mCameraClient.resize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
					mCameraClient.connect();
				}
			} else if (id == R.id.stop_button) {
				if (DEBUG) Log.v(TAG, "onClick:stop");
				// stop service
				doReleaseCameraClient( false );
				enableButtons(false);
			} else if (id == R.id.record_button) {
				if (DEBUG) Log.v(TAG, "onClick:record");
				if (checkPermissionWriteExternalStorage() && checkPermissionAudio()) {
					queueEvent(new Runnable() {
						@Override
						public void run() {
							if (mCameraClient.isRecording()) {
								mCameraClient.stopRecording();
								runOnUiThread(new Runnable() {
									@Override
									public void run() {
										mRecordButton.setColorFilter(0);
									}
								}, 0);
							} else {
								mCameraClient.startRecording();
								runOnUiThread(new Runnable() {
									@Override
									public void run() {
										mRecordButton.setColorFilter(0x7fff0000);
									}
								}, 0);
							}
						}
					}, 0);
				}
			}
		}
	};

	private final void enableButtons(final boolean enable) {
		getActivity().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				mRecordButton.setEnabled(enable);
				if (enable && mCameraClient.isRecording())
					mRecordButton.setColorFilter(0x7fff0000);
				else
					mRecordButton.setColorFilter(0);
			}
		});
	}

	private final void updateRecordingTime() {
		getActivity().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if ( mCameraClient == null ) return;
				if (mCameraClient.isRecording()) {
					int s = mCameraClient.getRecordingLengthSeconds();
					mRecordingTime.setText( String.format("%02d", s / 60 ) +":" + String.format("%02d", s % 60 ));
				}
				else {
					mRecordingTime.setText( "" );
				}
			}
		});
	}

	public final void doReleaseCameraClient( boolean disconnectListener ) {
		if (mCameraClient != null) {
			if ( disconnectListener ) mCameraClient.disconnectListener();
			mCameraClient.disconnect();
			mCameraClient.release();
			mCameraClient = null;
		}
	}

	private final CameraViewInterface.Callback	mCallback = new CameraViewInterface.Callback() {
		@Override
		public void onSurfaceCreated(final CameraViewInterface view, final Surface surface) {
		}

		@Override
		public void onSurfaceChanged(final CameraViewInterface view, final Surface surface, final int width, final int height) {
			if ( mCameraClient != null ) {
				if (addedSurface != null)
					mCameraClient.removeSurface(addedSurface);
				addedSurface = surface;
				mCameraClient.addSurface(addedSurface, false);
			}
		}

		@Override
		public void onSurfaceDestroy(final CameraViewInterface view, final Surface surface) {
		}
	};

	public void onContainerVisibilityChange( Boolean visible )
	{
		//fragment was created is collapsed state?
		//then try to connect after uncollapsing
		if ( visible && attachWasSkipped ) {
			new android.os.Handler().postDelayed(
					new Runnable() {
						public void run() {
							if (!updateCameraDialog() && (mCameraView.hasSurface())) {
								attachWasSkipped = false;
								tryOpenUVCCamera(true);
							}
						}
					}, 1);
		}
	}


}
