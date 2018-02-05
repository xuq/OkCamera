package com.jiangdg.libcamera.operation;

import android.app.Activity;
import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.util.Log;
import android.view.OrientationEventListener;

import com.jiangdg.libcamera.utils.SensorOrientation;

import java.lang.ref.WeakReference;
import java.util.List;

/**
 * 相机操作实现类
 * Created by jiangdongguo on 2018/2/5.
 */

public class CameraOperatorImpl implements ICameraOperator {
    private static final String TAG = "CameraSurfaceView";
    private int width = 640;
    private int height = 480;
    private Camera mCamera;
    private boolean isFrontCamera;
    private static  CameraOperatorImpl mCamOperatorImpl;
    private WeakReference<Context> mContextWrf;
    private SensorOrientation mOriSensor;
    private int mPhoneDegree;

    private CameraOperatorImpl(Context context) {
        mContextWrf = new WeakReference<Context>(context);
        // 方向传感器
        mOriSensor = SensorOrientation.getInstance(mContextWrf.get());
    }

    public static CameraOperatorImpl getInstance(Context context) {
        if (mCamOperatorImpl == null) {
            mCamOperatorImpl = new CameraOperatorImpl(context);
        }
        return mCamOperatorImpl;
    }

    // Camera对外回调接口
    public interface OnCameraListener {

    }

    @Override
    public void createCamera() {
        try {
            // 初始化资源
            if (mCamera != null) {
                stopPreview();
                destoryCamera();
            }
            // 实例化Camera(前、后置摄像头)
            if (!isFrontCamera) {
                mCamera = Camera.open();
            } else {
                Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
                int numofCameras = Camera.getNumberOfCameras();
                for (int i = 0; i < numofCameras; i++) {
                    Camera.getCameraInfo(i, cameraInfo);
                    if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                        mCamera = Camera.open(i);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "创建Camera失败：" + e.getMessage());
        }
    }

    @Override
    public void setParameters(CameraParameters pData) {
        if (mCamera == null) {
            Log.w(TAG, "mCamera=null,请确保是否创建了Camera");
            return;
        }
        Camera.Parameters parameters = mCamera.getParameters();
        // 预览分辨率，默认640x480
        parameters.setPreviewSize(width,height);
        // 预览颜色格式，默认NV21
        parameters.setPreviewFormat(ImageFormat.NV21);
        // 自动对焦
        if(isSupportFocusAuto(parameters)) {
            parameters.setFocusMode(Camera.Parameters.FLASH_MODE_AUTO);
        }
        // 图片格式，默认JPEG
        parameters.setPictureFormat(ImageFormat.JPEG);
        // 图片尺寸，与预览尺寸一致
        parameters.setPictureSize(width,height);
        // 图片质量，默认最好
        parameters.setJpegQuality(100);
        // 图片缩略图质量
        parameters.setJpegThumbnailQuality(100);
        mCamera.setParameters(parameters);
        // 预览方向
        mCamera.setDisplayOrientation(90);
    }

    @Override
    public void destoryCamera() {
        if(mCamera == null)
            return;
        mCamera.setPreviewCallbackWithBuffer(null);
    }

    @Override
    public void startPreview() {

    }

    @Override
    public void stopPreview() {

    }

    @Override
    public void switchCamera() {
        isFrontCamera = !isFrontCamera;
        stopPreview();
        destoryCamera();
        createCamera();
        setParameters(null);
        startPreview();
    }

    @Override
    public void updateResolution(int width, int height) {
        this.width = width;
        this.height = height;
        stopPreview();
        destoryCamera();
        createCamera();
        setParameters(null);
        startPreview();
    }

    @Override
    public void startOrientationSenor() {
        mOriSensor.startSensorOrientation(new SensorOrientation.OnChangedListener() {
            @Override
            public void onOrientatonChanged(int orientation) {
                // 假定某个范围，确定手机当前方向
                // mPhoneDegree = 0,正常垂直方向
                // mPhoneDegree = 90,向右水平方向 ...
                int rotate = 0;
                if((orientation>=0 && orientation<=45) || (orientation>315)) {
                    rotate = 0;
                } else if(orientation>45 && orientation<=135) {
                    rotate = 90;
                } else if(orientation>135 && orientation<=225) {
                    rotate = 180;
                } else if (orientation>225 && orientation<=315){
                    rotate = 270;
                } else {
                    rotate = 0;
                }
                if(rotate == orientation)
                    return;
                mPhoneDegree = rotate;
                // 根据手机方向，修改保存图片的旋转角度
                updateCameraOrientation();
            }
        });
    }

    private void updateCameraOrientation() {
        if(mCamera == null)
            return;
        Camera.Parameters parameters = mCamera.getParameters();
        int rotation = 90 + mPhoneDegree==360?0:90+mPhoneDegree;
        // 前置摄像头需要对垂直方向做变换，否则照片是颠倒的
        if(isFrontCamera) {
            if (rotation == 90)
                rotation = 270;
            else if (rotation == 270)
                rotation = 90;
        }
        parameters.setRotation(rotation);
        mCamera.setParameters(parameters);
    }

    private boolean isSupportFocusAuto(Camera.Parameters p) {
        boolean isSupport = false;
        List<String> modes = p.getSupportedFocusModes();
        for (String mode : modes) {
            if(mode.equals(Camera.Parameters.FOCUS_MODE_AUTO)) {
                isSupport = true;
                break;
            }
        }
        return isSupport;
    }
}