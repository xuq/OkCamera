package com.jiangdg.libcamera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.jiangdg.libcamera.utils.CameraUtil;
import com.jiangdg.libcamera.utils.SensorOrientation;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.List;

/**
 * 相机操作实现类
 * Created by jiangdongguo on 2018/2/5.
 */

public class CameraHelper implements SurfaceHolder.Callback{
    private static final String TAG = "CameraHelper";
    private int width = 640;
    private int height = 480;
    private Camera mCamera;
    private static CameraHelper mCameraHelper;
    private WeakReference<SurfaceView> mSurfaceViewRf;
    private SurfaceHolder mHolder;
    private OnCameraHelperListener mHelperListener;
    private SensorOrientation mOriSensor;
    private int mPhoneDegree;
    private boolean isFrontCamera = false;

    private CameraHelper() {}

    public static CameraHelper createCameraHelper() {
        if (mCameraHelper == null) {
            mCameraHelper = new CameraHelper();
        }
        return mCameraHelper;
    }

    public interface OnCameraHelperListener {
        void OnTakePicture(String path,Bitmap bm);
    }

    public void setOnCameraHelperListener(OnCameraHelperListener listener) {
        this.mHelperListener = listener;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        createCamera();
        startPreview();
        startOrientationSensor();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        updateCameraOrientation();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        stopOrientationSensor();
        stopPreview();
        destoryCamera();
    }

    public void setSurfaceView(SurfaceView surfaceView) {
        mSurfaceViewRf = new WeakReference<>(surfaceView);
        mHolder = mSurfaceViewRf.get().getHolder();
        mHolder.addCallback(this);
    }

    public void takePicture(final String path) {
        mCamera.takePicture(null, null, new Camera.PictureCallback() {
            @Override
            public void onPictureTaken(byte[] data, Camera camera) {
                if(mHelperListener != null) {
                    // 如果data=null,说明拍照失败
                    if(data != null) {
                        File file = new File(path);
                        Bitmap bitmap = BitmapFactory.decodeByteArray(data,0,data.length);
                        FileOutputStream fos = null;
                        try {
                            fos = new FileOutputStream(file);
                            // 对图片进行旋转
                            // 前置摄像头需要对垂直方向做变换，否则照片是颠倒的
                            int rotation = (mPhoneDegree==270 ? 0 : mPhoneDegree+90);
                            if(isFrontCamera) {
                                if(rotation == 90) {
                                    rotation =270;
                                } else if(rotation == 270) {
                                    rotation = 90;
                                }
                            }
                            Matrix matrix = new Matrix();
                            matrix.setRotate(rotation);
                            Bitmap rotateBmp = Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),matrix,false);
                            rotateBmp.compress(Bitmap.CompressFormat.JPEG,100,fos);
                            // 回传结果
                            mHelperListener.OnTakePicture(path,rotateBmp);
                            // 重新预览
                            stopPreview();
                            startPreview();
                        } catch (FileNotFoundException e) {
                            Log.e(TAG,"拍照失败：请确保路径是否正确，或者存储权限");
                            e.printStackTrace();
                        }finally {
                            try {
                                if(fos != null) {
                                    fos.close();
                                }
                                bitmap.recycle();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }
        });
    }

    private void createCamera() {
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
            setParameters();
        } catch (Exception e) {
            Log.e(TAG, "创建Camera失败：" + e.getMessage());
        }
    }

    private void setParameters() {
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
        if(CameraUtil.isSupportFocusAuto(parameters)) {
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
        mCamera.setDisplayOrientation(90);
    }

    private void destoryCamera() {
        if(mCamera == null)
            return;
        mCamera.release();
        mCamera = null;
    }

    private void startPreview() {
        if(mHolder != null) {
            try {
                mCamera.setPreviewDisplay(mHolder);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        mCamera.startPreview();
        // 开启预览，自动对焦一次
        mCamera.autoFocus(null);
        // 注册预览回调接口,缓存大小为一帧图像所占字节数
        // 即，(width * height * 每个像素所占bit数)/8
        int previewFormat = mCamera.getParameters().getPreviewFormat();
        Camera.Size previewSize = mCamera.getParameters().getPreviewSize();
        int bufferSize = previewSize.width * previewSize.height *
                ImageFormat.getBitsPerPixel(previewFormat) / 8;
        mCamera.addCallbackBuffer(new byte[bufferSize]);
    }

    private void stopPreview() {
        if(mCamera == null)
            return;
        try {
            mCamera.stopPreview();
            mCamera.setPreviewDisplay(null);
            mCamera.setPreviewCallbackWithBuffer(null);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void switchCamera() {
        stopPreview();
        destoryCamera();

        isFrontCamera = !isFrontCamera;
        createCamera();
        startPreview();

    }

    public void updateResolution(int width, int height) {
        this.width = width;
        this.height = height;
        stopPreview();
        destoryCamera();
        createCamera();
        startPreview();
    }

    private void startOrientationSensor() {
        mOriSensor = SensorOrientation.getInstance(mSurfaceViewRf.get().getContext());
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
                Log.i(TAG,"手机方向角度："+mPhoneDegree);
                // 根据手机方向，修改保存图片的旋转角度
//                updateCameraOrientation();
            }
        });
        mOriSensor.enable();
    }

    private void stopOrientationSensor() {
        if(mOriSensor != null) {
            mOriSensor.disable();
        }
    }

    private void updateCameraOrientation() {
        if(mCamera == null)
            return;
        Camera.Parameters parameters = mCamera.getParameters();
        int rotation = (mPhoneDegree==270 ? 0 : mPhoneDegree+90);
        if(isFrontCamera) {
            if(rotation == 90) {
                rotation =270;
            } else if(rotation == 270) {
                rotation = 90;
            }
        }
        // setRotation方法不会修正拍摄照片的方向
        // 只是将照片的方向存储到exf信息头中
        parameters.setRotation(rotation);
        mCamera.setParameters(parameters);
    }
}