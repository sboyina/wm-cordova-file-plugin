# wm-cordova-plugin-file
Provides additional API to Cordova file plugin.

** Copy a file to download folder in Android

In Android SDK 33, Android stopped copying files to folders of sd card based on path. If the developer wants to copy a file from the app specific directory to downloads directory, then the below method helps. This is verified in Android SDK 24 and 33.

```
cordova.wavemaker.file.copyToDownloads(
    'file:///android_asset/www/resources/images/imagelists/default-image.png',
    // Suggested file name
    'sample.png',
    // mime type
    'image/png').then(function() {
    // on success
    alert('file copied.');
}, function(e) {
    // on failure
    alert('failed to copy the file.');
});
```