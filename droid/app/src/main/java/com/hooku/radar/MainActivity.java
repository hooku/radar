// MainActivity.java
package com.hooku.radar;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback {

    private static final String TAG = "WeatherRadar";
    private static final String RADAR_URL = "https://alltobid.529000.xyz/radar.php";
    private static final String BASE_URL = "https://alltobid.529000.xyz/radar/";
    private static final int PERMISSION_REQUEST_CODE = 1;

    private SurfaceView surfaceView;
    private ProgressBar progressBar;
    private MediaPlayer mediaPlayer;
    private GestureDetector gestureDetector;

    private List<String> radarFiles = new ArrayList<>();
    private int currentFileIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        surfaceView = findViewById(R.id.surfaceView);
        progressBar = findViewById(R.id.progressBar);
        progressBar.setProgress(30);

        surfaceView.getHolder().addCallback(this);
        gestureDetector = new GestureDetector(this, new GestureListener());

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.INTERNET}, PERMISSION_REQUEST_CODE);
        } else {
            fetchRadarFiles();
        }
    }

    private void fetchRadarFiles() {
        Log.i(TAG, "Fetching radar files from URL: " + RADAR_URL);
        new FetchRadarFilesTask().execute(RADAR_URL);
    }

    private class FetchRadarFilesTask extends AsyncTask<String, Void, List<String>> {
        @Override
        protected List<String> doInBackground(String... urls) {
            List<String> files = new ArrayList<>();
            try {
                URL url = new URL(urls[0]);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                InputStream inputStream = connection.getInputStream();
                int data;
                StringBuilder response = new StringBuilder();
                while ((data = inputStream.read()) != -1) {
                    response.append((char) data);
                }
                inputStream.close();
                String[] fileList = response.toString().split("\n");
                for (String file : fileList) {
                    files.add(file.trim());
                }
                Log.i(TAG, "Radar file list: " + files);
            } catch (Exception e) {
                Log.e(TAG, "Error fetching radar file list", e);
            }
            return files;
        }

        @Override
        protected void onPostExecute(List<String> files) {
            radarFiles = files;
            downloadAndPlayFiles();
        }
    }

    private void downloadAndPlayFiles() {
        if (currentFileIndex < radarFiles.size()) {
            String fileName = radarFiles.get(currentFileIndex);
            String fileUrl = BASE_URL + fileName;
            Log.i(TAG, "Downloading file: " + fileUrl);
            new DownloadFileTask(MainActivity.this).execute(fileUrl, fileName);
        }
    }

    private static class DownloadFileTask extends AsyncTask<String, Void, String> {
        private Context context;

        public DownloadFileTask(Context context) {
            this.context = context;
        }

        private boolean isValidFile(File file) {
            if (file.getName().endsWith(".webp")) {
                return isValidWebp(file);
            } else if (file.getName().endsWith(".mp4")) {
                return isValidMp4(file);
            }
            return false;
        }

        private boolean isValidWebp(File file) {
            try (FileInputStream fis = new FileInputStream(file)) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeStream(fis, null, options);
                return options.outMimeType != null && options.outMimeType.equals("image/webp");
            } catch (IOException e) {
                Log.e(TAG, "Error validating WebP file", e);
                return false;
            }
        }

        private boolean isValidMp4(File file) {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try {
                retriever.setDataSource(file.getAbsolutePath());
                String hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO);
                return hasVideo != null && hasVideo.equals("yes");
            } catch (Exception e) {
                Log.e(TAG, "Error validating MP4 file", e);
                return false;
            } finally {
                try {
                    retriever.release();
                } catch (IOException e) {
                    Log.e(TAG, "Error releasing MediaMetadataRetriever", e);
                }
            }
        }

        @Override
        protected String doInBackground(String... params) {
            String fileUrl = params[0];
            String fileName = params[1];
            File file = new File(context.getCacheDir(), fileName);
            if (file.exists() && isValidFile(file)) {
                Log.i(TAG, "File exists: " + file.getAbsolutePath() + ". Skipping download.");
            } else {
                try {
                    URL url = new URL(fileUrl);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    InputStream inputStream = connection.getInputStream();
                    FileOutputStream outputStream = new FileOutputStream(file);
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                    outputStream.close();
                    inputStream.close();
                    Log.i(TAG, "Downloaded file: " + file.getAbsolutePath());
                } catch (Exception e) {
                    Log.e(TAG, "Error downloading file", e);
                }
            }
            return file.getAbsolutePath();
        }

        @Override
        protected void onPostExecute(String filePath) {
            if (filePath.endsWith(".mp4")) {
                ((MainActivity) context).playVideo(filePath);
            } else if (filePath.endsWith(".webp")) {
                ((MainActivity) context).displayImage(filePath);
            }
        }
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (e1.getX() - e2.getX() > 50) {
                // Swipe left
                currentFileIndex = Math.min(currentFileIndex + 1, radarFiles.size() - 1);
                Log.i(TAG, "Swiped left, current file index: " + currentFileIndex);
                downloadAndPlayFiles();
                return true;
            } else if (e2.getX() - e1.getX() > 50) {
                // Swipe right
                currentFileIndex = Math.max(currentFileIndex - 1, 0);
                Log.i(TAG, "Swiped right, current file index: " + currentFileIndex);
                downloadAndPlayFiles();
                return true;
            }
            return false;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return gestureDetector.onTouchEvent(event);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                fetchRadarFiles();
            } else {
                Log.i(TAG, "Internet permission denied");
            }
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        // Initialize MediaPlayer
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setDisplay(holder);
        Log.i(TAG, "Surface created, initializing MediaPlayer");
        downloadAndPlayFiles();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
            Log.i(TAG, "Surface destroyed, MediaPlayer released");
        }
    }

    private void playVideo(String filePath) {
        try {
            mediaPlayer.reset();
            mediaPlayer.setDataSource(filePath);
            mediaPlayer.prepare();
            mediaPlayer.start();
            Log.i(TAG, "Playing video: " + filePath);
        } catch (Exception e) {
            Log.e(TAG, "Error playing video", e);
        }
    }

    private void displayImage(String filePath) {
        try {
            Bitmap bitmap = BitmapFactory.decodeFile(filePath);
            SurfaceHolder holder = surfaceView.getHolder();
            Canvas canvas = holder.lockCanvas();
            if (canvas != null) {
                canvas.drawBitmap(bitmap, 0, 0, null);
                holder.unlockCanvasAndPost(canvas);
                Log.i(TAG, "Displaying image: " + filePath);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error displaying image", e);
        }
    }
}