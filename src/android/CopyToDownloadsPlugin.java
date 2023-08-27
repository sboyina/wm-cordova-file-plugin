/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.wavemaker.cordova.plugin;

import static android.os.Environment.DIRECTORY_DOWNLOADS;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.camera.FileHelper;
import org.json.JSONArray;
import org.json.JSONException;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class CopyToDownloadsPlugin extends CordovaPlugin {
    private static final String TAG = "FILE";

    //PERMISSION REQUEST CODE
    private static final int WRITE_PERMISSION_REQUEST_CODE = 1000;

    //ACTIVITY REQUEST CODE
    private static final int FILE_REQUEST_CODE = 1001;

    private String src = null;
    private String fileName = null;
    private String fileType = null;
    private CallbackContext callbackContext = null;

    /**
     * Constructor.
     */
    public CopyToDownloadsPlugin() {
        Log.d(TAG, "File Utils plugin instance created");
    }

    /**
     * Executes the request and returns PluginResult.
     *
     * @param action            The action to execute.
     * @param args              JSONArry of arguments for the plugin.
     * @param callbackContext   The callback id used when calling back into JavaScript.
     * @return                  True if the action was valid, false if not.
     */
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if ("default".equals(action)) {
            this.src = args.getString(0);
            this.fileName = args.getString(1);
            this.fileType = args.getString(2);
            this.callbackContext = callbackContext;
            this.copyToDownloads();
        } else {
            return false;
        }
        return true;
    }

    public void closeQuietly(Closeable stream) {
        try {
            if (stream != null) {
                stream.close();
            }
        } catch( IOException e) {
            // silence
        }
    }

    private InputStream getInputStreamFromUriString(String uriString)
            throws IOException {
        InputStream returnValue = null;
        if (uriString.startsWith("content")) {
            Uri uri = Uri.parse(uriString);
            returnValue = cordova.getActivity().getContentResolver().openInputStream(uri);
        } else if (uriString.startsWith("file://")) {
            int question = uriString.indexOf("?");
            if (question > -1) {
                uriString = uriString.substring(0, question);
            }
            if (uriString.startsWith("file:///android_asset/")) {
                Uri uri = Uri.parse(uriString);
                String relativePath = uri.getPath().substring(15);
                returnValue = cordova.getActivity().getAssets().open(relativePath);
            } else {
                // might still be content so try that first
                try {
                    returnValue = cordova.getActivity().getContentResolver().openInputStream(Uri.parse(uriString));
                } catch (Exception e) {
                    returnValue = null;
                }
                if (returnValue == null) {
                    returnValue = new FileInputStream(FileHelper.getRealPathFromURI(cordova.getActivity(), Uri.parse(uriString)));
                }
            }
        } else {
            returnValue = new FileInputStream(uriString);
        }
        return returnValue;
    }

    public void copyToDownloads() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            this.copyToDownloadsWithNewApi();
        } else {
            if (this.cordova.hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                this.copyToDownloadsWithOldApi();
            } else {
                this.cordova.requestPermission(this,
                        WRITE_PERMISSION_REQUEST_CODE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
        }
    }

    public void copyToStream(OutputStream os) throws IOException {
        InputStream is = null;
        try {
            is = this.getInputStreamFromUriString(src);
                int c;
            while ((c = is.read()) != -1) {
                os.write(c);
            }
        } finally {
            closeQuietly(is);
            closeQuietly(os);
        }
    }

    public void copyFromUri(Uri uri) {
        try {
            OutputStream os = this.cordova.getContext().getContentResolver().openOutputStream(uri);
            this.copyToStream(os);
            callbackContext.success();
        } catch(IOException e) {
            callbackContext.error("Failed to copy due to an Exception: " + e.getMessage());
            e.printStackTrace();
        } finally {
            callbackContext = null;
        }
    }

    public String getFileName(String fileName) {
        int slashIndex = fileName.lastIndexOf('/');
        int e = fileName.lastIndexOf('.');
        String plainName = fileName;
        String ext = null;
        if (e >= 0 && e > slashIndex) {
            plainName = fileName.substring(0, e);
            ext = fileName.substring(e);
        }
        int i = 1;
        while(new File(fileName).exists()) {
            fileName = plainName + (i++) + (ext != null ? ext : "");
        }
        return fileName;
    }

    public void copyToDownloadsWithOldApi() {
        try {
            String destFile = this.getFileName(Environment.getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS)
                    + "/" + fileName);
            this.copyToStream(new FileOutputStream(new File(destFile)));
            callbackContext.success();
        } catch (IOException e) {
            callbackContext.error("Failed to copy due to an Exception: " + e.getMessage());
            e.printStackTrace();
        } finally {
            this.callbackContext = null;
        }
    }

    public void copyToDownloadsWithNewApi() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(this.fileType);
        intent.putExtra(Intent.EXTRA_TITLE, fileName);

        // Optionally, specify a URI for the directory that should be opened in
        // the system file picker when your app creates the document.
        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, Environment.getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS));
        this.cordova.startActivityForResult(this, intent, FILE_REQUEST_CODE);
    }

    @Override
    public Bundle onSaveInstanceState() {
        Bundle state = new Bundle();
        state.putString("src", this.src);
        state.putString("fileName", this.fileName);
        return state;
    }

    @Override
    public void requestPermissions(int requestCode) {
        super.requestPermissions(requestCode);
    }

    @Override
    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException {
        this.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException {
        switch (requestCode) {
            case WRITE_PERMISSION_REQUEST_CODE: {
                if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    this.copyToDownloadsWithOldApi();
                } else {
                    this.callbackContext.error("Permission denied");
                }
                break;
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onRestoreStateForActivityResult(Bundle state, CallbackContext callbackContext) {
        this.src = state.getString("src");
        this.fileName = state.getString("fileName");
        super.onRestoreStateForActivityResult(state, callbackContext);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == FILE_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                this.copyFromUri(intent.getData());
            } else if (resultCode == Activity.RESULT_CANCELED) {

            }
        }
        super.onActivityResult(requestCode, resultCode, intent);
    }
}

