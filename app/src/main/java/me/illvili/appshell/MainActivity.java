package me.illvili.AppShell;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

@SuppressLint("SetJavaScriptEnabled")
public class MainActivity extends Activity {
    private String lost_connection_page;
    private BroadcastReceiver onComplete;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set full screen
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        // getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // show contents
        setContentView(R.layout.activity_main);

        lost_connection_page = readTextFromResource(R.raw.lost_connection);

        final ProgressBar progressBar = (ProgressBar)findViewById(R.id.progressBar);
        final Animation progressBarHideAnimation = new AlphaAnimation(1.0f, 0.0f);
        final WebView webView = (WebView) findViewById(R.id.webView);

        final DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);

        // webView Settings
        webView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);

        WebSettings wv_settings = webView.getSettings();
        wv_settings.setJavaScriptEnabled(true);
        wv_settings.setDomStorageEnabled(true);
        wv_settings.setGeolocationEnabled(true);

        webView.addJavascriptInterface(new WebAppInterface(), getString(R.string.webapp_object_name));

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading (WebView view, String url) {
                final Context context = view.getContext();

                if (!NetworkDetector.checkInternetConnection(context)) {
                    lostConnection(url);

                    return true;
                }

                if (url.endsWith(".jpg") || url.endsWith(".png") || url.endsWith(".gif")) {
                    final String imageUrl = url;

                    (new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(context, R.string.download_image_toast, Toast.LENGTH_SHORT).show();

                            try {
                                // Get image from url
                                URL u = new URL(imageUrl);
                                HttpGet httpRequest = new HttpGet(u.toURI());
                                HttpClient httpclient = new DefaultHttpClient();
                                HttpResponse response = httpclient.execute(httpRequest);
                                HttpEntity entity = response.getEntity();
                                BufferedHttpEntity bufHttpEntity = new BufferedHttpEntity(entity);
                                InputStream instream = bufHttpEntity.getContent();
                                Bitmap bmImg = BitmapFactory.decodeStream(instream);
                                instream.close();

                                // Write image to a file in cache
                                File cacheDir;
                                //Choose cache directory
                                if (android.os.Environment.getExternalStorageState().equals(android.os.Environment.MEDIA_MOUNTED))
                                    cacheDir = new File(android.os.Environment.getExternalStorageDirectory(), getString(R.string.download_cache_dir));
                                else
                                    cacheDir=context.getCacheDir();
                                if (!cacheDir.exists()) cacheDir.mkdirs();
                                File posterFile = new File(cacheDir, getString(R.string.download_cache_file));
                                posterFile.createNewFile();
                                BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(posterFile));
                                Bitmap mutable = Bitmap.createScaledBitmap(bmImg,bmImg.getWidth(),bmImg.getHeight(),true);
                                mutable.compress(Bitmap.CompressFormat.JPEG, 100, out);
                                out.flush();
                                out.close();

                                // Launch default viewer for the file
                                Intent intent = new Intent();
                                intent.setAction(android.content.Intent.ACTION_VIEW);
                                Uri hacked_uri = Uri.parse("file://" + posterFile.getPath());
                                intent.setDataAndType(hacked_uri, "image/*");
                                context.startActivity(intent);
                            }
                            catch (Exception e) {
                                e.printStackTrace();
                                // nothing
                            }
                        }
                    }).run();

                    return true;
                } else if (url.endsWith(".mp4")) {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(Uri.parse(url), "video/*");
                    context.startActivity(intent);

                    return true;
                } else if (url.endsWith(".apk")) {
                    try {
                        Uri loc = Uri.parse(url);
                        String host = loc.getHost();

                        if (host.equalsIgnoreCase(getString(R.string.apk_allow_url))) {
                            Toast.makeText(context, R.string.download_apk_start_toast, Toast.LENGTH_SHORT).show();

                            // download apk
                            DownloadManager.Request request = new DownloadManager.Request(loc);

                            request.setTitle(getString(R.string.app_name));
                            // request.setDescription("Some descrition");

                            // Write image to a file in cache
                            File cacheDir;
                            //Choose cache directory
                            if (android.os.Environment.getExternalStorageState().equals(android.os.Environment.MEDIA_MOUNTED))
                                cacheDir = new File(android.os.Environment.getExternalStorageDirectory(), getString(R.string.download_cache_dir));
                            else
                                cacheDir=context.getCacheDir();
                            if (!cacheDir.exists()) cacheDir.mkdirs();
                            File apkFile = new File(cacheDir, getString(R.string.apk_file));
                            if (apkFile.exists()) apkFile.delete();

                            request.setDestinationInExternalPublicDir(getString(R.string.download_cache_dir), getString(R.string.apk_file));

                            // get download service and enqueue file
                            manager.enqueue(request);
                        }

                        return true;
                    } catch (Exception e) {
                        return true;
                    }
                }

                return super.shouldOverrideUrlLoading(view, url);
            }

            @Override
            public void onPageFinished (WebView view, String url) {
                Context context = view.getContext().getApplicationContext();
                PackageManager manager = context.getPackageManager();
                try {
                    PackageInfo info = manager.getPackageInfo(context.getPackageName(), 0);
                    view.loadUrl(
                            "javascript: window." + getString(R.string.webapp_register) + " && " +
                                    getString(R.string.webapp_register) + "(" +
                                    getString(R.string.webapp_register_platform) + ", '" +
                                    info.packageName + "', '" +
                                    info.versionName + "', " +
                                    info.versionCode + ")"
                    );
                } catch (NameNotFoundException e) {
                    // nothing to do
                }
            }
        });

        onComplete = new BroadcastReceiver() {
            public void onReceive(Context ctxt, Intent intent) {
                Bundle extras = intent.getExtras();
                DownloadManager.Query q = new DownloadManager.Query();
                q.setFilterById(extras.getLong(DownloadManager.EXTRA_DOWNLOAD_ID));
                Cursor c = manager.query(q);

                if (c.moveToFirst()) {
                    int status = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS));
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {

                        File cacheDir;
                        //Choose cache directory
                        if (android.os.Environment.getExternalStorageState().equals(android.os.Environment.MEDIA_MOUNTED))
                            cacheDir = new File(android.os.Environment.getExternalStorageDirectory(), getString(R.string.download_cache_dir));
                        else
                            cacheDir = ctxt.getCacheDir();
                        if (!cacheDir.exists()) cacheDir.mkdirs();
                        File apkFile = new File(cacheDir, getString(R.string.apk_file));

                        if (!apkFile.exists()) {
                            Toast.makeText(ctxt, R.string.download_apk_error_toast, Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(ctxt, R.string.download_apk_start_toast, Toast.LENGTH_SHORT).show();

                            Intent installIntent = new Intent(Intent.ACTION_VIEW);
                            installIntent.setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive");
                            installIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            ctxt.startActivity(installIntent);
                        }
                    } else if (status == DownloadManager.STATUS_FAILED) {
                        Toast.makeText(ctxt, R.string.download_apk_error_toast, Toast.LENGTH_SHORT).show();
                    }
                }
            }
        };
        registerReceiver(onComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

        // progressBar Animation
        progressBarHideAnimation.setDuration(1000);
        progressBarHideAnimation.setAnimationListener(new AnimationListener() {
            @Override
            public void onAnimationEnd(Animation arg0) {
                progressBar.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationRepeat(Animation arg0) {}

            @Override
            public void onAnimationStart(Animation arg0) {}
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }

            @Override
            public void onProgressChanged(WebView view, int progress) {
                if (progressBarHideAnimation.hasStarted() && !progressBarHideAnimation.hasEnded()) {
                    progressBar.clearAnimation();
                } else if (progressBar.getVisibility() == View.GONE) {
                    progressBar.setVisibility(View.VISIBLE);
                }

                progressBar.setProgress(progress);

                if (100 == progress) {
                    progressBar.startAnimation(progressBarHideAnimation);
                }
            }
        });

        if (NetworkDetector.checkInternetConnection(this)) {
            webView.loadUrl(getString(R.string.app_intro));
        } else {
            lostConnection(getString(R.string.app_intro));
        }
    }

    private String readTextFromResource(int resourceID) {
        InputStream raw = getResources().openRawResource(resourceID);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        int i;
        try {
            i = raw.read();
            while (i != -1) {
                stream.write(i);
                i = raw.read();
            }
            raw.close();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        return stream.toString();
    }

    private void lostConnection(String url) {
        final ProgressBar progressBar = (ProgressBar)findViewById(R.id.progressBar);
        progressBar.setVisibility(View.GONE);
        Toast.makeText(this, R.string.lost_connection_info, Toast.LENGTH_SHORT).show();

        final WebView webView = (WebView) findViewById(R.id.webView);
        URL url2;
        String baseURL = getString(R.string.lost_connection_baseurl);
        try {
            url2 = new URL(url);
            baseURL = url2.getProtocol() + "://" + url2.getHost();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        webView.loadDataWithBaseURL(baseURL, lost_connection_page.replace("{$URL}", url), "text/html", "UTF-8", url);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        unregisterReceiver(onComplete);

        File cacheDir;
        //Choose cache directory
        if (android.os.Environment.getExternalStorageState().equals(android.os.Environment.MEDIA_MOUNTED))
            cacheDir = new File(android.os.Environment.getExternalStorageDirectory(), getString(R.string.download_cache_dir));
        else
            cacheDir=getCacheDir();
        if (!cacheDir.exists()) return;
        File posterFile = new File(cacheDir, getString(R.string.download_cache_file));
        if (posterFile.exists()) posterFile.delete();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            final WebView webView = (WebView) findViewById(R.id.webView);

            String url = webView.getUrl();
            if (url.matches("http://[\\w\\d\\.]+?/(index\\.php)?") || !webView.canGoBack()) {
                CloseApp();
            } else {
                webView.goBack();
            }
        } else if (keyCode == KeyEvent.KEYCODE_MENU) {
            final WebView webView = (WebView) findViewById(R.id.webView);

            webView.loadUrl("javascript: window." + getString(R.string.webapp_device_keydown) + " && " + getString(R.string.webapp_device_keydown) + "(" + getString(R.string.webapp_device_key_nemu) + ")");
        }

        return true;
    }

    public void CloseApp() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.exit_title);
        builder.setMessage(R.string.exit_msg);
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setPositiveButton(R.string.exit_yes, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();

                final WebView webView = (WebView) findViewById(R.id.webView);
                webView.loadUrl("about:blank");

                MainActivity.this.finish();
            }
        });
        builder.setNegativeButton(R.string.exit_no, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        builder.show();
    }

    class WebAppInterface {
        @JavascriptInterface
        public void Close() {
            CloseApp();
        }
    }
}
