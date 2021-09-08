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
                var json = JSON.stringify({
                    urls: gonative._pendingCalls
                });
                window.location.href = "gonative://nativebridge/multi?data=" + encodeURIComponent(json);
                gonative._pendingCalls = [];
            }
        },
        0);
}

function addCallbackFunction(callbackFunction) {
    var callbackName;
    if (typeof callbackFunction === 'string') {
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

function addCommand(command, params) {
    if (params) {
        command += "?";
        var keysArray = Object.keys(params);
        for (var i = 0; i < keysArray.length; i++) {
            if (typeof params[keysArray[i]] === 'function') {
                command += keysArray[i] + "=" + addCallbackFunction(params[keysArray[i]]);
            } else {
                command += keysArray[i] + "=" + encodeURIComponent(params[keysArray[i]]);
            }
            if (i != keysArray.length - 1) command += "&";
        }
    }
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
    custom: function(params) {
        addCommand("gonative://nativebridge/custom", params);
    }
};

gonative.registration = {
    send: function(params) {
        addCommand("gonative://registration/send", params);
    }
};

gonative.sidebar = {
    setItems: function(params) {
        addCommand("gonative://sidebar/setItems", params);
    }
};

gonative.tabNavigation = {
    selectTab: function(tabIndex) {
        addCommand('gonative://tabs/select/' + tabIndex);
    },
    setTabs: function(params) {
        addCommand('gonative://tabs/setTabs', params);
    }
};

gonative.share = {
    sharePage: function(params) {
        addCommand("gonative://share/sharePage", params);
    },
    downloadFile: function(params) {
        addCommand("gonative://share/downloadFile", params);
    }
};

gonative.open = {
    appSettings: function() {
        addCommand("gonative://open/app-settings");
    }
};

gonative.webview = {
    clearCache: function() {
        addCommand("gonative://webview/clearCache");
    }
};

gonative.config = {
    set: function(params) {
        addCommand("gonative://config/set", params);
    }
};

gonative.navigationTitles = {
    set: function(params) {
        addCommand("gonative://navigationTitles/set", params);
    },
    setCurrent: function(params) {
        addCommand("gonative://navigationTitles/setCurrent", params);
    },
    revert: function() {
        addCommand("gonative://navigationTitles/set?persist=true");
    }
};

gonative.navigationLevels = {
    set: function(params) {
        var json = JSON.stringify(params);
        addCommand("gonative://navigationLevels/set", params);
    },
    setCurrent: function(params) {
        var json = JSON.stringify(params);
        addCommand("gonative://navigationLevels/set", params);
    },
    revert: function() {
        addCommand("gonative://navigationLevels/set?persist=true");
    }
};

gonative.statusbar = {
    set: function(params) {
        addCommand("gonative://statusbar/set", params);
    }
};

gonative.screen = {
    setBrightness: function(params) {
        addCommand("gonative://screen/setBrightness", params);
    }
};

gonative.navigationMaxWindows = {
    set: function(params) {
        addCommand("gonative://navigationMaxWindows/set", params);
    }
};

gonative.connectivity = {
    get: function(params) {
        addCommand("gonative://connectivity/get", params);
    },
    subscribe: function(params) {
        addCommand("gonative://connectivity/subscribe", params);
    },
    unsubscribe: function(params) {
        addCommand("gonative://connectivity/unsubscribe", params);
    }
};

gonative.run = {
    deviceInfo: function() {
        addCommand("gonative://run/gonative_device_info");
    },
    onesignalInfo: function() {
        addCommand("gonative://run/gonative_onesignal_info");
    }
};

// onesignal
gonative.onesignal = {
    register: function() {
        addCommand("gonative://onesignal/register");
    },
    userPrivacyConsent: {
        grant: function() {
            addCommand("gonative://onesignal/userPrivacyConsent/grant");
        },
        revoke: function() {
            addCommand("gonative://onesignal/userPrivacyConsent/revoke");
        }
    },
    tags: {
        getTags: function(params) {
            addCommand("gonative://onesignal/tags/get", params);
        },
        setTags: function(params) {
            addCommand("gonative://onesignal/tags/set", params);
        }
    },
    showTagsUI: function() {
        addCommand("gonative://onesignal/showTagsUI");
    },
    promptLocation: function() {
        addCommand("gonative://onesignal/promptLocation");
    },
    iam: {
        addTrigger: function(params) {
            addCommand("gonative://onesignal/iam/addTrigger", params);
        },
        addTriggers: function(params) {
            addCommand("gonative://onesignal/iam/addTriggers", params);
        },
        removeTriggerForKey: function(params) {
            addCommand("gonative://onesignal/iam/removeTriggerForKey", params);
        },
        getTriggerValueForKey: function(params) {
            addCommand("gonative://onesignal/iam/getTriggerValueForKey", params);
        },
        pauseInAppMessages: function() {
            addCommand("gonative://onesignal/iam/pauseInAppMessages?pause=true");
        },
        resumeInAppMessages: function() {
            addCommand("gonative://onesignal/iam/pauseInAppMessages?pause=false");
        },
        setInAppMessageClickHandler: function(params) {
            addCommand("gonative://onesignal/iam/setInAppMessageClickHandler", params);
        }
    }
};

// facebook
gonative.facebook = {
    events: {
        send: function(params) {
            addCommand("gonative://facebook/events/send", params);
        },
        sendPurchase: function(params) {
            addCommand("gonative://facebook/events/sendPurchase", params);
        }
    }
};

///////////////////////////////
////     iOS Exclusive     ////
///////////////////////////////

gonative.ios = {};

gonative.ios.window = {
    open: function(params) {
        addCommand("gonative://window/open", params);
    }
};

gonative.ios.geoLocation = {
    requestLocation: function() {
        addCommand("gonative://geolocationShim/requestLocation");
    },
    startWatchingLocation: function() {
        addCommand("gonative://geolocationShim/startWatchingLocation");
    },
    stopWatchingLocation: function() {
        addCommand("gonative://geolocationShim/stopWatchingLocation");
    }
};

gonative.ios.attconsent = {
    request: function(params) {
        addCommand("gonative://ios/attconsent/request", params);
    },
    status: function(params) {
        addCommand("gonative://ios/attconsent/status", params);
    }
};

gonative.ios.backgroundAudio = {
    start: function() {
        addCommand("gonative://backgroundAudio/start");
    },
    end: function() {
        addCommand("gonative://backgroundAudio/end");
    }
};

///////////////////////////////
////   Android Exclusive   ////
///////////////////////////////

gonative.android = {};

gonative.android.geoLocation = {
    promptAndroidLocationServices: function() {
        addCommand("gonative://geoLocation/promptAndroidLocationServices");
    }
};

gonative.android.screen = {
    fullscreen: function() {
        addCommand("gonative://screen/fullscreen");
    },
    normal: function() {
        addCommand("gonative://screen/normal");
    },
    keepScreenOn: function() {
        addCommand("gonative://screen/keepScreenOn");
    },
    keepScreenNormal: function() {
        addCommand("gonative://screen/keepScreenNormal");
    }
};
