package com.novo.main;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.exoplayer2.source.hls.HLSUtils;
import com.google.android.exoplayer2.source.hls.KeyWriter;
import com.google.android.exoplayer2.upstream.novo.TokenManager;
import com.google.gson.Gson;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.novo.R;
import com.novo.adapters.VideoAdapter;
import com.novo.models.VideoModel;
import com.novo.network.DownloadTask;
import com.novo.network.EndPoints;
import com.novo.network.ServerHit;
import com.novo.network.ZipHelper;
import com.novo.util.Utils;

import static com.novo.util.Utils.TAG;

public class MainActivity extends Activity implements VideoAdapter.ItemListener {

    private Button btnLogin;
    private GridView lvAll;

    private void initStuff() {
        btnLogin = (Button) findViewById(R.id.btnLogin);
        lvAll = (GridView) findViewById(R.id.lvAll);
        ServerHit.JSONTask task = new ServerHit.JSONTask(this, TokenManager.getToken(), "GET", null, null, new ServerHit.ServiceHitResponseListener() {
            @Override
            public void onDone(final String response) {
                Log.d(TAG, "onDone: " + response);
                VideoAdapter adapter = new VideoAdapter(MainActivity.this, R.layout.row_videos_grid, getVideoModelsFromResponse(response));
                adapter.setItemListener(MainActivity.this);
                lvAll.setAdapter(adapter);


            }

            @Override
            public void onError(String error) {

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        String response = "[ {  \"thumbnail\" : \"http://35.154.11.202/VocabimateContentServer/thumbnails/thumbnail.jpg\",  \"name\" : \"Encrypted Stream - Open Policy\",  \"videoId\" : \"Gear_640x360_750k_open\"}, {  \"thumbnail\" : \"http://35.154.11.202/VocabimateContentServer/thumbnails/thumbnail.jpg\",  \"name\" : \"Encrypted Stream - Token Auth policy\",  \"videoId\" : \"Gear_640x360_750k_auth\"} ]";
                        onDone(response);
                    }
                });


            }
        });

        String url = EndPoints.getBaseUrl() + "VocabimateContentServer/webapi/video/fetchAll";
        task.execute(url);

        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!TextUtils.isEmpty(TokenManager.getToken())) {
                    TokenManager.setToken(null);
                    loginButtonTextUpdate();
                    return;
                }
                Intent intent = new Intent(getApplicationContext(), LoginActivity.class);
                Bundle bundle = new Bundle();
                startActivity(intent.putExtras(bundle));
            }
        });

    }

    @NonNull
    private List<VideoModel> getVideoModelsFromResponse(String response) {
        List<VideoModel> items = new ArrayList<>();
        if (TextUtils.isEmpty(response)) {
            return items;
        }
        try {
            JSONArray array = new JSONArray(response);
            for (int i = 0; i < array.length(); i++) {
                JSONObject jsonObject = array.getJSONObject(i);
                VideoModel model = new Gson().fromJson(jsonObject.toString(), VideoModel.class);
                items.add(model);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return items;
    }

    private void playMediaFromServer(VideoModel model) {
        ServerHit.JSONTask streamTask = new ServerHit.JSONTask(this, TokenManager.getToken(), "GET", null, null, new ServerHit.ServiceHitResponseListener() {
            @Override
            public void onDone(String response) {
                try {
                    JSONObject object = new JSONObject(response);
                    String videoUrl = object.getString("videoUrl");
                    // send to player
                    Intent intent = new Intent(getApplicationContext(), PlayerActivity.class);
                    intent.setData(Uri.parse(videoUrl));
                    intent.setAction(PlayerActivity.ACTION_VIEW);
                    startActivity(intent);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onError(String error) {

            }
        });
        streamTask.execute(EndPoints.getBaseUrl() + "VocabimateContentServer/webapi/video/stream?videoId=" + model.getVideoId());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loginButtonTextUpdate();
//        File dir = new File(storageDirectoryZips + videoId);
//        if(isFolderPresent(dir)){
//            iVDownload.setImageResource(R.mipmap.ic_download_complete);
//        } else {
//            iVDownload.setImageResource(R.mipmap.ic_download);
//        }
    }

    private void loginButtonTextUpdate() {
        if (!TextUtils.isEmpty(TokenManager.getToken())) {
            btnLogin.setText("Logout");
        } else {
            btnLogin.setText("Login");
        }
    }

    @Override
    public void onVideoPlayClicked(final VideoModel model) {
        File dir = new File(Utils.getStorageDirectoryExtracts() + model.getVideoId());
        File[] file = dir.listFiles();
        if (Utils.isFolderPresent(dir)) {
            // trying to find my file
            Log.d(TAG, "onVideoPlayClicked: " + ZipHelper.searchFile(file, null));
            ZipHelper.searchFile(file, new ZipHelper.FileListener() {
                @Override
                public void onFileSearchComplete(boolean fileFound, String fileToPlay) {
                    if (fileFound && !TextUtils.isEmpty(fileToPlay)) {
                        Intent intent = new Intent(getApplicationContext(), PlayerActivity.class);
                        intent.setData(Uri.parse(fileToPlay));
                        intent.setAction(PlayerActivity.ACTION_VIEW);
                        startActivity(intent);
                    } else {
                        Toast.makeText(MainActivity.this, "Unable to play local video, playing stream.", Toast.LENGTH_SHORT).show();
                        playMediaFromServer(model);
                    }
                }
            });
        } else { // if folder is not present locally, play via server
            playMediaFromServer(model);
        }
    }

    @Override
    public void onDownloadClicked(VideoModel model, final ImageView ivDownload) {
        String serverFileUrl = EndPoints.getBaseUrl() + "VocabimateContentServer/webapi/video/download?videoId=" + model.getVideoId();
        // todo problem with zip file, hardcoded
        final String keyFileUrl = EndPoints.getBaseUrl() + "VocabimateKeyServer/webapi/keys/getKey?videoId=" + model.getVideoId();
        String videoId = HLSUtils.getVideoIdFromUrl(serverFileUrl);
        if(TextUtils.isEmpty(videoId)){
            Toast.makeText(MainActivity.this, "Video id not found", Toast.LENGTH_SHORT).show();
            return;
        }
        // execute this when the downloader must be fired
        final File sourceZipFile = new File(Utils.getStorageDirectoryZips() + videoId);
        String fileNameWithOutExt = FilenameUtils.removeExtension(sourceZipFile.getName());
        final File targetDirectory = new File(Utils.getStorageDirectoryExtracts() + fileNameWithOutExt);
        targetDirectory.mkdir();

        final DownloadTask downloadTask = new DownloadTask(MainActivity.this, TokenManager.getToken(), sourceZipFile.getAbsolutePath(), new DownloadTask.DownloadTaskListener() {
            @Override
            public void onFileDownload() {
                new ZipHelper.ZipTask(MainActivity.this, new ZipHelper.ZipTaskListener() {
                    @Override
                    public void onUnzipped(String fileToPlay) {
                        Log.d(TAG, "onUnzipped: " + fileToPlay);
                        ivDownload.setImageResource(R.mipmap.ic_download_complete);
                    }
                }).execute(sourceZipFile, targetDirectory);
            }
        });
        downloadTask.execute(serverFileUrl);

        final File tempKeyPath = new File(Utils.getTempDirectoryExtracts() + videoId);
        final DownloadTask keyTask = new DownloadTask(MainActivity.this, TokenManager.getToken(), tempKeyPath.toString(), new DownloadTask.DownloadTaskListener() {
            @Override
            public void onFileDownload() {
                KeyWriter.writeByteToFile(KeyWriter.readByteToFileUnencryptedData(keyFileUrl, tempKeyPath), keyFileUrl);
            }
        });
        keyTask.execute(keyFileUrl);
    }

    @Override
    public void onDeleteClicked(File directory, ImageView ivDownload) {
        try {
            FileUtils.deleteDirectory(directory);
            ivDownload.setImageResource(R.mipmap.ic_download);
        } catch (IOException e) {
            e.printStackTrace();
            Log.d(TAG, "onDeleteClicked: unable to delete directory" + e.getLocalizedMessage());
        }
    }
}
