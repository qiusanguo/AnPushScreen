package com.exam.push.screen;

/***
 * 整体思路
 * 1，MediaProjectionManager
 * 2，MediaCodec获取h264数据
 * 3，使用EasyPusher推流到EasyDarwin服务器
 * 4，使用VLC拉流播放视频
 */


import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;

import org.easydarwin.push.MediaStream;

import io.reactivex.Single;
import io.reactivex.functions.Consumer;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_CODE_SCREEN = 1001;

    private static final String HOST = "cloud.easydarwin.org";
    private static final String PORT = "554";
    private static final String SCREEN_ID = "scr123";

    private MediaStream mediaStream;
    private Button btnPush;
    private TextView tvState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        this.btnPush = findViewById(R.id.btn_push_screen);
        this.tvState = findViewById(R.id.tv_info);

        //启动服务...
        Intent intent = new Intent(this, MediaStream.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        getMediaStream().subscribe(new Consumer<MediaStream>() {
            @Override
            public void accept(final MediaStream mediaStream) {
                mediaStream.observePushingState(MainActivity.this, new Observer<MediaStream.PushingState>() {
                    @Override
                    public void onChanged(MediaStream.PushingState pushingState) {
                        if(pushingState.screenPushing){
                            tvState.setText("屏幕推送");
                            if(mediaStream.isScreenPushing()){
                                btnPush.setText("取消推送");
                            }else{
                                btnPush.setText("推送屏幕");
                            }
                            btnPush.setEnabled(true);
                        }

                        tvState.append(":\t" + pushingState.msg);
                        if (pushingState.state > 0) {
                            tvState.append(pushingState.url);
                            tvState.append("\n");
                            if ("avc".equals(pushingState.videoCodec)) {
                                tvState.append("视频编码方式：" + "H264硬编码");
                            }else if ("hevc".equals(pushingState.videoCodec)) {
                                tvState.append("视频编码方式："  + "H265硬编码");
                            }else if ("x264".equals(pushingState.videoCodec)) {
                                tvState.append("视频编码方式："  + "x264");
                            }
                        }
                    }
                });
            }
        }, new Consumer<Throwable>() {
            @Override
            public void accept(Throwable throwable) {
                Toast.makeText(MainActivity.this,"创建服务出错",Toast.LENGTH_SHORT).show();
            }
        });
    }


    @Override
    protected void onActivityResult(int requestCode, final int resultCode, @Nullable final Intent data) {
       if(requestCode == REQUEST_CODE_SCREEN){
            getMediaStream().subscribe(new Consumer<MediaStream>() {
                @Override
                public void accept(MediaStream mediaStream) {
                    mediaStream.pushScreen(resultCode,data,HOST,PORT,SCREEN_ID);
                }
            });
       }
        super.onActivityResult(requestCode, resultCode, data);
    }

    public void onClick(View view) {
        if(view.getId() == R.id.btn_push_screen){
            pushScreen();
        }
    }


    private void pushScreen(){
        btnPush.setEnabled(false);
        getMediaStream().subscribe(new Consumer<MediaStream>() {
            @Override
            public void accept(MediaStream mediaStream) throws Exception {
                if(mediaStream.isScreenPushing()){
                    mediaStream.stopPushScreen();
                }else{
                    MediaProjectionManager mMpMngr = (MediaProjectionManager) getApplicationContext().getSystemService(MEDIA_PROJECTION_SERVICE);
                    startActivityForResult(mMpMngr.createScreenCaptureIntent(), REQUEST_CODE_SCREEN);
                }
            }
        });
    }

    private Single<MediaStream> getMediaStream() {
        Single<MediaStream> single = RxHelper.single(MediaStream.getBindedMediaStream(this, this), mediaStream);
        if (mediaStream == null) {
            return single.doOnSuccess(new Consumer<MediaStream>() {
                @Override
                public void accept(MediaStream ms) {
                    mediaStream = ms;
                }
            });
        } else {
            return single;
        }
    }

}
