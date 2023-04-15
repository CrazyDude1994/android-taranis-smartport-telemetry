# UVC video devices support notes

UVC Video devices support allows to view FPV Video feed from Eachine ROTG01, ROTG02 receivers and clones.
Basically these devices work like USB web cameras. External USB web cameras support is problematic on Android. It will not work on some devices. If it does not work, nothing can be done.
It should work on phones where applications GoFPV(PlayMarket) and FUAV (http://files.banggood.com/2016/11/fuav.2.0.apk) do work.

UVC video devices support is based on UVCCamera library https://github.com/saki4510t/UVCCamera
Library has been updated to latest SDKs.
You can find updated library here: https://github.com/RomanLut/UVCCamera
To compile library, please download NDK 16.1.4479499 and add path to local.propeties file:
ndk.dir=C\:\\Users\\roman\\AppData\\Local\\Android\\Sdk\\ndk\\16.1.4479499
Open in Android Studio and build.

Library is implemented in Java while TelemetryViewer is implemented in Kotlin.
To merge functionality, usbcameratest4 example has been converted into Fragment.
Updated UVCCamera library and modified usbcameratest4 example were added to TelemetryViewer project.

In order to build TelemetryViewer project view with UVC support, please download NDK 16.1.4479499 and add NDK path to local.properties file:
ndk.dir=C\:\\Users\\roman\\AppData\\Local\\Android\\Sdk\\ndk\\16.1.4479499


# Implementation notes 

UVC Video window is represented by CameraFragment class.

CameraFragment creates USBMonitor class and passes callback: mOnDeviceConnectListener.
CameraFragment calls USBMonitor.register() in onResume().

USBMonitor starts periodic checking of connected USB devices (mDeviceCheckRunnable).
Non-camera devices are ignored. 
If new device is detected, then USBMonitor calls onAttach callback which is handled by CameraFragment (OnDeviceConnectListener.onAttach).

On device Attach, CameraFragment will try to create CameraClient:
CameraFramegment.onAttach -> tryOpenUVCCamera() -> openUVCCamera()

openUVCCamera() creates CameraClient object.
CameraClient binds to UVCService on creation.
CameraClient talks to UVCService via thread, using messaging.
Thread is implemented in CameraHander class.

UVCService is managing CameraServer class for each device (camera).
Service allows to attach surfaces for preview (addSurface), start/stop video recording and still image capturing.

CameraFragment unbinds service in onDestroy. But if recording is in progress, it will release but not disconnect CameraClient. So service will continue working and recording.
To stop service, user has to start application and stop recording explicitly.


