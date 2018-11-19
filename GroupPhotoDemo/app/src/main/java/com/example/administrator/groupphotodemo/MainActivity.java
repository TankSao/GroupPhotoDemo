package com.example.administrator.groupphotodemo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity {
    @BindView(R.id.surfaceView)
    SurfaceView surfaceView;
    @BindView(R.id.code)
    ImageView code;
    private OssService ossService;
    private String ossPath = "";
    private Camera camera;
    private String savePath = "/groupPhoto/";
    private Bundle bundle = null;
    @SuppressLint("HandlerLeak")
    private Handler upPicHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 110:
                    //上传成功,生成二维码
                    Log.e("result","上传成功");
                    String codepath = Environment.getExternalStorageDirectory() + savePath + "code.jpg";
                    Bitmap bitmap = createQRImage(ossPath,80,80,codepath);
                    code.setImageBitmap(bitmap);
                    break;
                case 119:
                    //上传失败
                    Toast.makeText(MainActivity.this, "图片上传失败", Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };
    private Handler countDown = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            ToastMessage(msg.what);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //隐藏标题栏
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);
        //绑定黄油刀
        ButterKnife.bind(this);
        //6.0以上需要动态申请权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestAllPower();
        }
        //初始化OSS
        ossService = Config.initOSS(this,Config.endpoint, Config.bucket);
        initSurface();

    }

    private void initSurface() {
        surfaceView.getHolder()
                .setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        surfaceView.getHolder().setFixedSize(150, 150); // 设置Surface分辨率
        surfaceView.getHolder().setKeepScreenOn(true);// 屏幕常亮
    }

    @Override
    public void onResume() {
        super.onResume();
        surfaceView.getHolder().addCallback(new SurfaceCallback());
    }

    public void requestAllPower() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.INTERNET,Manifest.permission.CAMERA}, 1);
            }
        }
    }

    /**
     * 重构相机照相回调类
     *
     * @author pc
     */
    private final class SurfaceCallback implements SurfaceHolder.Callback {

        @SuppressWarnings("deprecation")
        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width,
                                   int height) {
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            // TODO Auto-generated method stub
            try {
                if(checkCameraFacing(Camera.CameraInfo.CAMERA_FACING_FRONT)) {
                    camera = Camera.open(Camera.CameraInfo.CAMERA_FACING_FRONT); // 打开前置摄像头
                }else {
                    camera = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK); // 打开后置摄像头
                }
                camera.setPreviewDisplay(holder); // 设置用于显示拍照影像的SurfaceHolder对象
                camera.setDisplayOrientation(0);//0\90\180\270
                camera.startPreview(); // 开始预览
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            // TODO Auto-generated method stub
            if (camera != null) {
                holder.removeCallback(this);
                camera.setPreviewCallback(null);
                camera.stopPreview();
                camera.lock();
                camera.release(); // 释放照相机
                camera = null;

            }
        }

    }

    private static boolean checkCameraFacing(final int facing) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD) {
            return false;
        }
        final int cameraCount = Camera.getNumberOfCameras();
        Camera.CameraInfo info = new Camera.CameraInfo();
        for (int i = 0; i < cameraCount; i++) {
            Camera.getCameraInfo(i, info);
            if (facing == info.facing) {
                return true;
            }
        }
        return false;
    }

    //处理点击事件
    @OnClick({R.id.take, R.id.code})
    public void onViewClicked(View view) {
        switch (view.getId()) {
            case R.id.take://拍照并上传OSS
                takePic();
                break;
            case R.id.code://二维码
                goToCodeView();
                break;
        }
    }

    private void goToCodeView() {
        code.setDrawingCacheEnabled(true);
        Intent intent=new Intent(MainActivity.this,CodeActivity.class);
        intent.putExtra("bitmap", code.getDrawingCache());
        startActivity(intent);
        code.setDrawingCacheEnabled(false);
    }

    private void takePic() {
        new Thread(){
            @Override
            public void run() {
                super.run();
                int num = 3;
                while (num>0){
                    countDown.sendEmptyMessage(num--);
                    try {
                        sleep(200);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }.start();
        if (camera != null) {
            camera.takePicture(null, null, new MyPictureCallback());
        } else {
            Toast.makeText(MainActivity.this, "相机初始化失败，请联系管理员!", Toast.LENGTH_SHORT).show();
        }
    }
    private final class MyPictureCallback implements Camera.PictureCallback {

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            try {
                bundle = new Bundle();
                bundle.putByteArray("bytes", data); //将图片字节数据保存在bundle当中，实现数据交换
                if (bundle == null) {
                    Toast.makeText(MainActivity.this, "bundle is null",
                            Toast.LENGTH_SHORT).show();
                } else {
                    try {
                        if (isHaveSDCard())
                            saveToSDCard(bundle.getByteArray("bytes"));
                        else
                            saveToRoot(bundle.getByteArray("bytes"));
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        Log.e("error1", e.getMessage());
                        e.printStackTrace();
                    }
                }
                camera.startPreview(); // 拍完照后，重新开始预览
            } catch (Exception e) {
                Log.e("error", e.getMessage());
                e.printStackTrace();
            }
        }
    }

    //判断是否有SD卡
    public static boolean isHaveSDCard() {
        return Environment.MEDIA_MOUNTED.equals(Environment
                .getExternalStorageState());
    }
    //保存至SD卡
    public void saveToSDCard(byte[] data) throws IOException {
        if(data!=null){
            Toast.makeText(MainActivity.this, "拍照成功，正在上传。。。", Toast.LENGTH_SHORT).show();
            Bitmap b = byteToBitmap(data);
            Bitmap bitmap = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight() );
            //生成文件
            Date date = new Date();
            SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmss"); // 格式化时间
            String filename = format.format(date) + ".jpg";
            File fileFolder = new File(Environment.getExternalStorageDirectory()
                    + savePath);
            if (!fileFolder.exists()) { // 如果目录不存在，则创建
                fileFolder.mkdir();
            }
            File jpgFile = new File(fileFolder, filename);
            FileOutputStream outputStream = new FileOutputStream(jpgFile); // 文件输出流
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
            outputStream.flush();
            outputStream.close(); // 关闭输出流
            String objectName = "user/dzbp/" + System.currentTimeMillis() + "dzbp.jpg";
            String imagePath = Environment.getExternalStorageDirectory() + savePath + filename;
            ossPath = "https://rjwpublic.oss-cn-qingdao.aliyuncs.com/" + objectName;
            ossService.asyncPutImage(objectName, imagePath, upPicHandler);
        }else{
            Toast.makeText(MainActivity.this, "拍照失败!", Toast.LENGTH_SHORT).show();
        }
    }

    public void saveToRoot(byte[] data) throws IOException {
        //剪切为正方形
        if(data!=null) {
            Toast.makeText(MainActivity.this, "拍照成功，正在上传。。。", Toast.LENGTH_SHORT).show();
            Bitmap b = byteToBitmap(data);
            Bitmap bitmap = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight());
            //生成文件
            Date date = new Date();
            SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmss"); // 格式化时间
            String filename = format.format(date) + ".jpg";
            File fileFolder = new File(Environment.getRootDirectory()
                    + savePath);
            if (!fileFolder.exists()) { // 如果目录不存在，则创建一个名为"finger"的目录
                fileFolder.mkdir();
            }
            File jpgFile = new File(fileFolder, filename);
            FileOutputStream outputStream = new FileOutputStream(jpgFile); // 文件输出流
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
            outputStream.flush();
            outputStream.close(); // 关闭输出流
            String objectName = "user/dzbp/" + System.currentTimeMillis() + "dzbp.jpg";
            String imagePath = Environment.getRootDirectory() + savePath + filename;
            ossPath = "https://rjwpublic.oss-cn-qingdao.aliyuncs.com/" + objectName;
            ossService.asyncPutImage(objectName, imagePath, upPicHandler);

        }else{
            Toast.makeText(MainActivity.this, "拍照失败!", Toast.LENGTH_SHORT).show();
        }

    }

    //byte转bitmap
    private Bitmap byteToBitmap(byte[] data){
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        Bitmap b = BitmapFactory.decodeByteArray(data, 0, data.length,options);
        int i = 0;
        while (true) {
            if ((options.outWidth >> i <= 1000)
                    && (options.outHeight >> i <= 1000)) {
                options.inSampleSize = (int) Math.pow(2.0D, i);
                options.inJustDecodeBounds = false;
                b = BitmapFactory.decodeByteArray(data, 0, data.length,options);
                break;
            }
            i += 1;
        }
        return b;

    }

    public static Bitmap createQRImage(String content, int widthPix, int heightPix,String filePath) {
        try {
            if (content == null || "".equals(content)) {
                return null;
            }
            // 配置参数
            Map<EncodeHintType, Object> hints = new HashMap<EncodeHintType, Object>();
            hints.put(EncodeHintType.CHARACTER_SET, "utf-8");
            // 容错级别
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
            // 设置空白边距的宽度
            hints.put(EncodeHintType.MARGIN, 0); //default is 4


            // 图像数据转换，使用了矩阵转换
            BitMatrix bitMatrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, widthPix, heightPix, hints);
            int[] pixels = new int[widthPix * heightPix];
            // 下面这里按照二维码的算法，逐个生成二维码的图片，
            // 两个for循环是图片横列扫描的结果
            for (int y = 0; y < heightPix; y++) {
                for (int x = 0; x < widthPix; x++) {
                    if (bitMatrix.get(x, y)) {
                        pixels[y * widthPix + x] = 0xff000000;
                    } else {
                        pixels[y * widthPix + x] = 0xffffffff;
                    }
                }
            }

            // 生成二维码图片的格式，使用ARGB_8888
            Bitmap bitmap = Bitmap.createBitmap(widthPix, heightPix, Bitmap.Config.ARGB_8888);
            bitmap.setPixels(pixels, 0, widthPix, 0, 0, widthPix, heightPix);

            if (filePath == null) {
                return bitmap;
            }

            // 必须使用compress方法将bitmap保存到文件中再进行读取。直接返回的bitmap是没有任何压缩的，内存消耗巨大！
            if (bitmap != null && bitmap.compress(Bitmap.CompressFormat.JPEG, 100, new FileOutputStream(filePath)))
                return BitmapFactory.decodeFile(filePath);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    private void ToastMessage(final int countdown) {
                LayoutInflater inflater = getLayoutInflater();//调用Activity的getLayoutInflater()
                View view = inflater.inflate(R.layout.toast, null); //加載layout下的布局
                TextView text = view.findViewById(R.id.num);
                text.setText(countdown+""); //toast内容
                Toast toast = new Toast(getApplicationContext());
                toast.setGravity(Gravity.CENTER, 12, 20);//setGravity用来设置Toast显示的位置，相当于xml中的android:gravity或android:layout_gravity
                toast.setDuration(Toast.LENGTH_LONG);//setDuration方法：设置持续时间，以毫秒为单位。该方法是设置补间动画时间长度的主要方法
                toast.setView(view); //添加视图文件
                view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {



                    @Override

                    public void onViewDetachedFromWindow(View v) {
                        if(countdown==1){
                            code.setVisibility(View.VISIBLE);
                        }
                    }



                    @Override

                    public void onViewAttachedToWindow(View v) {
                    }

                });
                toast.show();
    }

}
