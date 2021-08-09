// queue window.location.href calls --> internal use
function setNextTimeout() {
    if (gonative._nextTimeout) return;
    gonative._nextTimeout = setTimeout(function() {
                gonative._nextTimeout = null;
                if (gonative._pendingCalls.length == 0) return;
                if (gonative._pendingCalls.length == 1) {
                  window.location.href = gonative._pendingCalls.pop();
                  return;
                } else {
                    var json = JSON.stringify({urls: gonative._pendingCalls});
                    window.location.href = "gonative://nativebridge/multi?data=" + encodeURIComponent(json);
                    gonative._pendingCalls = [];
                }
              }, 0);
}

function addCallbackFunction(callbackFunction){
    var callbackName;
    if(typeof callbackFunction === 'string'){
        callbackName = callbackFunction;
    } else {
        callbackName = '_gonative_temp_' + Math.random().toString(36).slice(2);
        window[callbackName] = function(...args) {
            callbackFunction.apply(null, args);
            delete window[callbackName];
        }
    }
    return callbackName;
}

function addCommand(command){
    gonative._pendingCalls.push(command);
    setNextTimeout();
}

var gonative = {
    _pendingCalls: [],
    _nextTimeout: null
};

///////////////////////////////
////    General Commands   ////
///////////////////////////////

// to be modified as required
gonative.nativebridge = {
    custom: function (data){
        addCommand("gonative://nativebridge/custom");
    }
};

gonative.registration = {
    send: function(data){
        var json = JSON.stringify(data);
        addCommand("gonative://registration/send?customData=" + encodeURIComponent(json));
    }
};

gonative.sidebar = {
    setItems: function (items){
        var json = JSON.stringify(items);
        addCommand("gonative://sidebar/setItems?items=" + encodeURIComponent(json));
    }
};

gonative.tabNavigation = {
    selectTab: function (tabIndex){
        addCommand('gonative://tabs/select/' + tabIndex);
    },
    setTabs: function (tabs){
        var json = JSON.stringify(tabs);
        addCommand('gonative://tabs/setTabs?tabs=' + encodeURIComponent(json));
    }
};

gonative.share = {
    sharePage: function (shareURL){
        addCommand("gonative://share/sharePage?url=" + shareURL);
    },
    downloadFile: function (downloadURL){
        addCommand("gonative://share/downloadFile?url=" + downloadURL);
    }
};

gonative.open = {
    appSettings: function (){
        addCommand("gonative://open/app-settings");
    }
};

gonative.webview = {
    clearCache: function(){
        addCommand("gonative://webview/clearCache");
    }
};

gonative.config = {
    set: function(initialUrl){
        addCommand("gonative://config/set?initialUrl=" + initialUrl);
    }
};

gonative.navigationTitles = {
    set: function (params){
        var json = JSON.stringify(params);
        addCommand("gonative://navigationTitles/set?persist=true&data=" + encodeURIComponent(json));
    },
    setCurrent: function (title){
        addCommand("gonative://navigationTitles/setCurrent?title=" + encodeURIComponent(title));
    },
    revert: function(){
        addCommand("gonative://navigationTitles/set?persist=true");
    }
};

gonative.navigationLevels = {
    set: function (params){
        var json = JSON.stringify(params);
        addCommand("gonative://navigationLevels/set?persist=true&data=" + encodeURIComponent(json));
    },
    setCurrent: function(params){
        var json = JSON.stringify(params);
        addCommand("gonative://navigationLevels/set?persist=false&data=" + encodeURIComponent(json));
    },
    revert: function(){
        addCommand("gonative://navigationLevels/set?persist=true");
    }
};

gonative.statusbar = {
    set: function (paramsJsonObject){
        var url = "gonative://statusbar/set?";
        var keysArray = Object.keys(paramsJsonObject);
        for(var i = 0; i < keysArray.length; i++){
            url += keysArray[i] + "=" + encodeURIComponent(paramsJsonObject[keysArray[i]]);
            if(i != keysArray.length - 1) url += "&";
        }
        addCommand(url);
    }
};

gonative.screen = {
    setBrightness: function(brightnessParameter){
        if(typeof brightnessParameter !== "object"){
            addCommand("gonative://screen/setBrightness?brightness=" + brightnessParameter);
        } else {
            addCommand("gonative://screen/setBrightness?brightness=" + brightnessParameter.brightness + "&restoreOnNavigation=" + brightnessParameter.restoreOnNavigation);
        }
    }
};

gonative.navigationMaxWindows = {
    set: function (windowCount){
        addCommand("gonative://navigationMaxWindows/set?data=" + windowCount + "&persist=true");
    },
    setCurrent: function (windowCount){
        addCommand("gonative://navigationMaxWindows/set?data=" + windowCount + "&persist=false");
    }
};

gonative.connectivity = {
    get: function (callbackFunction){
        addCommand("gonative://connectivity/get?callback=" + addCallbackFunction(callbackFunction));
    },
    subscribe: function (callbackFunction){
        addCommand("gonative://connectivity/subscribe?callback=" + addCallbackFunction(callbackFunction));
    },
    unsubscribe: function (callbackFunction){
        addCommand("gonative://connectivity/unsubscribe?callback=" + addCallbackFunction(callbackFunction));
    }
};

gonative.run = {
    deviceInfo: function(){
        addCommand("gonative://run/gonative_device_info");
    },
    onesignalInfo: function(){
        addCommand("gonative://run/gonative_onesignal_info");
    }
};

// onesignal
gonative.onesignal = {
    register: function (){
        addCommand("gonative://onesignal/register");
    },
    userPrivacyConsent:{
        grant: function (){
            addCommand("gonative://onesignal/userPrivacyConsent/grant");
        },
        revoke: function (){
            addCommand("gonative://onesignal/userPrivacyConsent/revoke");
        }
    },
    tags: {
        getTags: function(callbackFunction){
            addCommand("gonative://onesignal/tags/get?callback=" + addCallbackFunction(callbackFunction));
        },
        setTags: function (tagsObject){
            addCommand("gonative://onesignal/tags/set?tags=" + encodeURIComponent(JSON.stringify(tagsObject.tags)) + "&callback=" + addCallbackFunction(tagsObject.callbackFunction));
        }
    },
    showTagsUI: function (){
        addCommand("gonative://onesignal/showTagsUI");
    },
    promptLocation: function (){
        addCommand("gonative://onesignal/promptLocation");
    },
    iam: {
        addTrigger: function (key, value){
            addCommand("gonative://onesignal/iam/addTrigger?key=" + key + "&value=" + value);
        },
        addTriggers: function (map){
            addCommand("gonative://onesignal/iam/addTriggers?map=" + encodeURIComponent(JSON.stringify(map)));
        },
        removeTriggerForKey: function (key){
            addCommand("gonative://onesignal/iam/removeTriggerForKey?key=" + key);
        },
        getTriggerValueForKey: function (key){
            addCommand("gonative://onesignal/iam/getTriggerValueForKey?key=" + key);
        },
        pauseInAppMessages: function (){
            addCommand("gonative://onesignal/iam/pauseInAppMessages?pause=true");
        },
        resumeInAppMessages: function (){
            addCommand("gonative://onesignal/iam/pauseInAppMessages?pause=false");
        },
        setInAppMessageClickHandler: function (handlerFunction){
            addCommand("gonative://onesignal/iam/setInAppMessageClickHandler?handler=" + addCallbackFunction(handlerFunction));
        }
    }
};

// facebook
gonative.facebook = {
    events: {
        send: function(data){
            addCommand("gonative://facebook/events/send?data=" + encodeURIComponent(JSON.stringify(data)));
        },
        sendPurchase: function(data){
            addCommand("gonative://facebook/events/sendPurchase?data=" + encodeURIComponent(JSON.stringify(data)));
        }
    }
};

///////////////////////////////
////     iOS Exclusive     ////
///////////////////////////////

gonative.ios = {};

gonative.ios.window = {
    open: function (url){
        addCommand("gonative://window/open?url=" + url);
    }
};

gonative.ios.geoLocation = {
    requestLocation: function (){
        addCommand("gonative://geolocationShim/requestLocation");
    },
    startWatchingLocation: function (){
        addCommand("gonative://geolocationShim/startWatchingLocation");
    },
    stopWatchingLocation: function (){
        addCommand("gonative://geolocationShim/stopWatchingLocation");
    }
};

gonative.ios.attconsent = {
    request: function (callbackFunction){
        addCommand("gonative://ios/attconsent/request?callback=" + addCallbackFunction(callbackFunction));
    },
    status: function (callbackFunction){
        addCommand("gonative://ios/attconsent/status?callback=" + addCallbackFunction(callbackFunction));
    }
};

gonative.ios.backgroundAudio = {
    start: function(){
        addCommand("gonative://backgroundAudio/start");
    },
    end: function(){
        addCommand("gonative://backgroundAudio/end");
    }
};

///////////////////////////////
////   Android Exclusive   ////
///////////////////////////////

gonative.android = {};

gonative.android.geoLocation = {
    promptAndroidLocationServices: function(){
        addCommand("gonative://geoLocation/promptAndroidLocationServices");
    }
};

gonative.android.screen = {
    fullscreen: function(){
        addCommand("gonative://screen/fullscreen");
    },
    normal: function(){
        addCommand("gonative://screen/normal");
    },
    keepScreenOn: function(){
        addCommand("gonative://screen/keepScreenOn");
    },
    keepScreenNormal: function(){
        addCommand("gonative://screen/keepScreenNormal");
    }
};
