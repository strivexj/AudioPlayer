package example.com.audioplayer;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.provider.MediaStore;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{

    public static final String Broadcast_PLAY_NEW_AUDIO = "example.com.audioplayer.PlayNewAudio";
    public static final String ACTION_PLAY = "example.com.audioplayer.ACTION_PLAY";
    public static final String ACTION_PAUSE = "example.com.audioplayer.ACTION_PAUSE";
    public static final String ACTION_PREVIOUS = "example.com.audioplayer.ACTION_PREVIOUS";
    public static final String ACTION_NEXT = "example.com.audioplayer.ACTION_NEXT";
    public static final String ACTION_STOP = "example.com.audioplayer.ACTION_STOP";
    private boolean ifEmpty=false;
    private MediaPlayerService player;
    boolean serviceBound = false;

    private SeekBar seekBar;
    private ImageButton previous,play_pause,next;
    private MediaPlayer mp = new MediaPlayer();
    private TextView preventTime,totalTime;
    private TextView songName;
    private Thread thread;
    private boolean isplay=false;
    private boolean ispause=false;
    private enum PlaybackStatus {
        PLAYING,
        PAUSED
    }


    ArrayList<Audio> audioList;

    ImageView collapsingImageView;

    int imageIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        seekBar = (SeekBar)findViewById(R.id.seekBar);
        previous = (ImageButton)findViewById(R.id.previousSong);
        play_pause = (ImageButton)findViewById(R.id.media_play);
        next  =(ImageButton)findViewById(R.id.nextSong);
        preventTime = (TextView)findViewById(R.id.preventTime);
        totalTime = (TextView)findViewById(R.id.totalTime);
        songName=(TextView) findViewById(R.id.songname);


        previous.setOnClickListener(this);
        play_pause.setOnClickListener(this);
        next.setOnClickListener(this);

       // mp.setOnCompletionListener(this);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, final int i, boolean b) {

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        preventTime.setText(updateTime(i));
                    }
                });
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                player.seekToPosition(seekBar.getProgress());
                preventTime.setText(updateTime(player.getPosition()));
                if(ispause)playSyatus(PlaybackStatus.PLAYING);;
            }
        });



        if (ContextCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(MainActivity.this,new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE},1);
        }else {
            loadAudio();
            initRecyclerView();

        }

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        collapsingImageView = (ImageView) findViewById(R.id.collapsingImageView);

        loadCollapsingImage(imageIndex);


        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if (imageIndex == 4) {
                    imageIndex = 0;
                    loadCollapsingImage(imageIndex);
                } else {
                    loadCollapsingImage(++imageIndex);
                }
            }
        });
       // Intent playerIntent = new Intent(this, MediaPlayerService.class);
        //                                                                          32                                                                                                                                                                                                                                                                                                  5/lbindService(playerIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        //playAudio(0);
    }


    private void initRecyclerView() {
        if(!ifEmpty) {
            if (audioList.size() > 0) {
                RecyclerView recyclerView = (RecyclerView) findViewById(R.id.recyclerview);
                RecyclerView_Adapter adapter = new RecyclerView_Adapter(audioList, getApplication());
                recyclerView.setAdapter(adapter);
                recyclerView.setLayoutManager(new LinearLayoutManager(this));
                recyclerView.addOnItemTouchListener(new CustomTouchListener(this, new onItemClickListener() {
                    @Override
                    public void onClick(View view, int index) {
                        playAudio(index);
                        thread = new Thread(new SeekBarThread());
                        // 启动线程
                        thread.start();
                        playSyatus(PlaybackStatus.PLAYING);
                        isplay=true;

                    }
                }));

            }
        }

    }

    private void loadCollapsingImage(int i) {
        TypedArray array = getResources().obtainTypedArray(R.array.images);
        collapsingImageView.setImageDrawable(array.getDrawable(i));
        array.recycle();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        int id = item.getItemId();

        if (id == R.id.action_settings) {
            Toast.makeText(this,"You click setting", Toast.LENGTH_LONG).show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    @Override
    public void onSaveInstanceState(Bundle outState, PersistableBundle outPersistentState) {
        super.onSaveInstanceState(outState, outPersistentState);
        outState.putBoolean("serviceStatus", serviceBound);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        serviceBound = savedInstanceState.getBoolean("serviceStatus");
    }


    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {

            MediaPlayerService.LocalBinder binder = (MediaPlayerService.LocalBinder) service;
            player = binder.getService();
            serviceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };


    private void playAudio(int audioIndex){
        if (!serviceBound) {
            StorageUtil storage = new StorageUtil(getApplicationContext());
            storage.storeAudio(audioList);
            storage.storeAudioIndex(audioIndex);

            Intent playerIntent = new Intent(this, MediaPlayerService.class);
            startService(playerIntent);
            bindService(playerIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        } else {

            StorageUtil storage = new StorageUtil(getApplicationContext());
            storage.storeAudioIndex(audioIndex);

            Intent broadcastIntent = new Intent(Broadcast_PLAY_NEW_AUDIO);
            sendBroadcast(broadcastIntent);
        }
    }

   @Override
    public void onClick(View view) {
        switch (view.getId()){
            case R.id.media_play: {
                if (!isplay) {
                    if(ispause) {
                        player.resumeMedia();
                        playSyatus(PlaybackStatus.PLAYING);
                        ispause=false;
                    }
                    else {
                        StorageUtil storage = new StorageUtil(getApplicationContext());
                        storage.storeAudio(audioList);
                        Intent playerIntent = new Intent(this, MediaPlayerService.class);
                        startService(playerIntent);
                        bindService(playerIntent, serviceConnection, Context.BIND_AUTO_CREATE);
                        storage.storeAudioIndex(0);
                        Intent broadcastIntent = new Intent(Broadcast_PLAY_NEW_AUDIO);
                        sendBroadcast(broadcastIntent);

                        playSyatus(PlaybackStatus.PLAYING);
                        thread = new Thread(new SeekBarThread());
                        // 启动线程
                        thread.start();
                    }
                        isplay=true;
                } else {
                    playSyatus(PlaybackStatus.PAUSED);
                    ispause=true;
                    isplay=false;
                    player.pauseMedia();
                }
                break;
            }
            case R.id.previousSong:
                playSyatus(PlaybackStatus.PLAYING);
                player.skipToPrevious();
               thread = new Thread(new SeekBarThread());
                // 启动线程
                thread.start();
                break;
            case R.id.nextSong:
                playSyatus(PlaybackStatus.PLAYING);
                player.skipToNext();
               thread = new Thread(new SeekBarThread());
                // 启动线程
               thread.start();
                break;
        }
    }
    class SeekBarThread implements Runnable {
        @Override
        public void run() {
            while (isplay) {
               seekBar.setMax(player.getDuration());
                // 将SeekBar位置设置到当前播放位置
                int position = player.getPosition();
                seekBar.setProgress(position);
                try {
                    // 每0.1s更新一次位置 播放进度
                    Thread.sleep(1000);
                    /*更新歌曲时间*/
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            songName.setText(player.getSongName());
                            preventTime.setText(updateTime(player.getPosition()));
                            totalTime.setText(updateTime(player.getDuration()));
                        }
                    });
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    /*更新时间*/
    private String updateTime(int position){
        int min,sec;
        min = (position/1000)/60;
        sec = (position/1000)%60;
        if (min < 10 && sec <10){
            String re = ("0"+min+":"+"0"+sec);
            return re;
        }else if(min < 10 && sec>=10){
            String re = ("0"+min+":"+sec);
            return re;
        }else if (min >=10 && sec < 10){
            String re = (min+":"+"0"+sec);
            return re;
        }else if (min >=10 && sec>=10){
            String re = (min+":"+sec);
            return re;
        }
        return "00：00";
    }
    private void playSyatus(final PlaybackStatus playbackStatus){
        runOnUiThread(new Runnable() {
           public void run() {
                if (playbackStatus == PlaybackStatus.PLAYING) {
                    play_pause.setImageResource(R.drawable.pause);
                } else if (playbackStatus == PlaybackStatus.PAUSED) {
                    play_pause.setImageResource(R.drawable.play);
                }
            }
        });
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode){
            case 1:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                }else {
                    Toast.makeText(MainActivity.this,"无法获取歌曲路径", Toast.LENGTH_SHORT).show();
                    finish();
                }break;
            default:
        }
    }
    private void loadAudio() {
        ContentResolver contentResolver = getContentResolver();
        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String selection = MediaStore.Audio.Media.IS_MUSIC + "!= 0";
        String sortOrder = MediaStore.Audio.Media.TITLE + " ASC";//数据库中的ASC为升序排列,DESC为降序排列。
        Cursor cursor = contentResolver.query(uri, null, selection, null, sortOrder);
        if (cursor != null && cursor.getCount() > 0) {
            ifEmpty=false;
            audioList = new ArrayList<>();
            while (cursor.moveToNext()) {
                String data = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA));
                String title = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE));
                String album = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM));
                String artist = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST));
                audioList.add(new Audio(data, title, album, artist));
            }
        }
        else{
           ifEmpty=true;
        }
        cursor.close();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Intent playerIntent = new Intent(this, MediaPlayerService.class);
        bindService(playerIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent playerIntent = new Intent(this, MediaPlayerService.class);
        bindService(playerIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
      if (serviceBound) {
        	 unbindService(serviceConnection);
        }
    }
}
