[![NPM Version][npm-image]][npm-url]
[![NPM Downloads][downloads-image]][downloads-url]
[![Codacy Badge][codacy-image]][codacy-url]

# Cordova CameraRecorder plugin

## Status
capture and record can work. Some bugs need to fix.

## Plugin's Purpose
The purpose of the plugin is to capture video to preview camera in a web page's canvas element.
Allows to select front or back camera and to control the flash.

The origin project is https://github.com/VirtuoWorks/CanvasCameraPlugin, which is written by Java.
This project is rewritten by Kotlin by the reason of the origin project is written by Andorid camera1 component which is deprecated.

## Supported Platforms
- Android

## Dependencies
[Cordova][cordova] will check all dependencies and install them if they are missing.

## Installation
The plugin can either be installed into the local development environment or cloud based through [PhoneGap Build][PGB].

### Adding the Plugin to your project
Through the [Command-line Interface][CLI]:

```bash
cordova plugin add https://github.com/DonPools/cordova-plugin-camera-recorder.git && cordova prepare
```

### Removing the Plugin from your project
Through the [Command-line Interface][CLI]:

```bash
cordova plugin remove cordova-plugin-camera-recorder
```

## Using the plugin (JavaScript)
The plugin creates the object ```window.plugins.cameraRecorder``` with the following methods:

### Plugin initialization
The plugin and its methods are not available before the *deviceready* event has been fired.
Call `initialize` with a reference to the canvas object used to preview the video and a second, optional, reference to a thumbnail canvas.

```javascript
document.addEventListener('deviceready', function () {

    // Call the initialize() function with canvas element reference
    var objCanvas = document.getElementById('canvas');
    window.plugins.cameraRecorder.initialize(objCanvas);
    // window.plugins.cameraRecorder is now available

}, false);
```

### `start`
Start capturing video as images from camera to preview camera on web page.<br>
`capture` callback function will be called with image data (image file url) each time the plugin takes an image for a frame.<br>

```javascript
window.plugins.cameraRecorder.start(options);
```

This function starts a video capturing session, then the plugin takes each frame as a JPEG image and gives its url to web page calling the `capture` callback function with the image url(s).<br>
The `capture` callback function will draw the image inside a canvas element to display the video.


#### Example
```javascript
var options = {
    cameraFacing: 'front',
};
window.plugins.cameraRecorder.start(options);
```
### `flashMode`
Set flash mode for camera.<br>

```javascript
window.plugins.cameraRecorder.flashMode(true);
```

### `cameraPosition`
Change input camera to 'front' or 'back' camera.

```javascript
window.plugins.cameraRecorder.cameraPosition('front');
```

### Options
Optional parameters to customize the settings.

```javascript
{
    width: 352,
    height: 288,
    canvas: {
      width: 352,
      height: 288
    },
    capture: {
      width: 352,
      height: 288
    },
    fps: 30,
    use: 'data',
    flashMode: false,
    thumbnailRatio: 1/6,
    cameraFacing: 'front' // or 'back',
    onBeforeDraw: function(frame){
      // do something before drawing a frame
      // frame.image; // HTMLImageElement
      // frame.element; // HTMLCanvasElement
    },
    onAfterDraw: function(frame){
      // do something after drawing a frame
      // frame.image.src; // file path or base64 data URI
      // frame.element.toDataURL(); // requested base64 data URI
    }
}

```
- `width` : **Number**, optional, default : `352`, width in pixels of the video to capture **and** the output canvas width in pixels.
- `height` : **Number**, optional, default : `288`, height in pixels of the video to capture **and** the output canvas height in pixels.

- `capture.width` : **Number**, optional, default : `352`, width in pixels of the video to capture.
- `capture.height` : **Number**, optional, default : `288`, height in pixels of the video to capture.

- `canvas.width` : **Number**, optional, default : `352`, output canvas width in pixels.
- `canvas.height` : **Number**, optional, default : `288`, output canvas height in pixels.

- `fps` : **Number**, optional, default : `30`, desired number of frames per second.
- `cameraFacing` : **String**, optional, default : `'front'`, `'front'` or `'back'`.
- `flashMode` : **Boolean**, optional, default : `false`, a boolean to set flash mode on/off.
- `thumbnailRatio` : **Number**, optional, default : `1/6`, a ratio used to scale down the thumbnail.

- `use` : **String**, optional, default : `file`, `file` to use files for rendering (lower CPU / higher storage) or `data` to use base64 jpg data for rendering (higher cpu / lower storage).

- `onBeforeDraw` : **Function**, optional, default : `null`, callback executed before a frame has been drawn. `frame` contains the canvas element, the image element, the tracking data, ...
- `onAfterDraw` : **Function**, optional, default : `null`,  callback executed after a frame has been drawn. `frame` contains the canvas element, the image element, the tracking data, ...

## Usage

### Full size video only
```javascript
let fullsizeCanvasElement = document.getElementById('fullsize-canvas');

window.plugins.cameraRecorder.initialize(fullsizeCanvasElement);

let options = {
    cameraFacing: 'back',
    onAfterDraw: function(frame) {
      // do something with each frame
      // frame.image.src; // file path or base64 data URI
      // frame.element.toDataURL(); // requested base64 data URI
    }
};

window.plugins.cameraRecorder.start(options);
```

### With thumbnail video
```javascript
let fullsizeCanvasElement = document.getElementById('fullsize-canvas');
let thumbnailCanvasElement = document.getElementById('thumbnail-canvas');

window.plugins.cameraRecorder.initialize(fullsizeCanvasElement, thumbnailCanvasElement);

let options = {
    cameraFacing: 'front',
    fps: 15,
    thumbnailRatio: 1/6,
    onAfterDraw: function(frame) {
      // do something with each frame of the fullsize canvas element only
      // frame.image.src; // file path or base64 data URI
      // frame.element.toDataURL(); // requested base64 data URI
    }
};

window.plugins.cameraRecorder.start(options);
```

## Contributing

1. Fork it
2. Create your feature branch (`git checkout -b my-new-feature`)
3. Commit your changes (`git commit -am 'Added some feature'`)
4. Push to the branch (`git push origin my-new-feature`)
5. Create new Pull Request
