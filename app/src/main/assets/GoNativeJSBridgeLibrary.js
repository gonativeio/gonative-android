// this function returns a promise and also supports callback as params.callback
function addCommandCallback(command, params, persistCallback) {
    var tempFunctionName = '_gonative_temp_' + Math.random().toString(36).slice(2);
    var callback;
    if(params) callback = params.callback;
    else {
        params = {
            'callback': tempFunctionName
        };
    }
    return new Promise(function(resolve, reject) {
        // declare a temporary function
        window[tempFunctionName] = function(data) {
            resolve(data);
            if (typeof callback === 'function') {
                callback(data);
            } else if (typeof callback === 'string' &&
                typeof window[callback] === 'function'){
                window[callback](data);
            }
            if(!persistCallback){ // if callback is used just once
                delete window[tempFunctionName];
            }
        }
        // execute command
        addCommand(command, params);
    });
}

function addCallbackFunction(callbackFunction, persistCallback){
    var callbackName;
    if(typeof callbackFunction === 'string'){
        callbackName = callbackFunction;
    } else {
        callbackName = '_gonative_temp_' + Math.random().toString(36).slice(2);
        window[callbackName] = function(...args) {
            callbackFunction.apply(null, args);
            if(!persistCallback){ // if callback is used just once
                delete window[callbackName];
            }
        }
    }
    return callbackName;
}

function addCommand(command, params, persistCallback){
    var data = undefined;
    if(params) {
        var commandObject = {};
        if(params.callback && typeof params.callback === 'function'){
            params.callback = addCallbackFunction(params.callback, persistCallback);
        }
        if(params.callbackFunction && typeof params.callbackFunction === 'function'){
            params.callbackFunction = addCallbackFunction(params.callbackFunction, persistCallback);
        }
        if(params.statuscallback && typeof params.statuscallback === 'function'){
            params.statuscallback = addCallbackFunction(params.statuscallback, persistCallback);
        }
        commandObject.gonativeCommand = command;
        commandObject.data = params;
        data = JSON.stringify(commandObject);
    } else data = command;

    JSBridge.postMessage(data);
}

///////////////////////////////
////    General Commands   ////
///////////////////////////////

var gonative = {};

// to be modified as required
gonative.nativebridge = {
    custom: function (params){
        addCommand("gonative://nativebridge/custom", params);
    }
};

gonative.registration = {
    send: function(customData){
        var params = {customData: customData};
        addCommand("gonative://registration/send", params);
    }
};

gonative.sidebar = {
    setItems: function (params){
        addCommand("gonative://sidebar/setItems", params);
    }
};

gonative.tabNavigation = {
    selectTab: function (tabIndex){
        addCommand("gonative://tabs/select/" + tabIndex);
    },
    setTabs: function (tabsObject){
        var params = {tabs: tabsObject};
        addCommand("gonative://tabs/setTabs", params);
    }
};

gonative.share = {
    sharePage: function (params){
        addCommand("gonative://share/sharePage", params);
    },
    downloadFile: function (params){
        addCommand("gonative://share/downloadFile", params);
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
    set: function(params){
        addCommand("gonative://config/set", params);
    }
};

gonative.navigationTitles = {
    set: function (parameters){
        var params = {
            persist: parameters.persist,
            data: parameters
        };
        addCommand("gonative://navigationTitles/set", params);
    },
    setCurrent: function (params){
        addCommand("gonative://navigationTitles/setCurrent", params);
    },
    revert: function(){
        addCommand("gonative://navigationTitles/set?persist=true");
    }
};

gonative.navigationLevels = {
    set: function (parameters){
        var params = {
            persist: parameters.persist,
            data: parameters
        };
        addCommand("gonative://navigationLevels/set", params);
    },
    setCurrent: function(params){
        addCommand("gonative://navigationLevels/set", params);
    },
    revert: function(){
        addCommand("gonative://navigationLevels/set?persist=true");
    }
};

gonative.statusbar = {
    set: function (params){
        addCommand("gonative://statusbar/set", params);
    }
};

gonative.screen = {
    setBrightness: function(data){
        var params = data;
        if(typeof params === 'number'){
            params = {brightness: data};
        }
        addCommand("gonative://screen/setBrightness", params);
    }
};

gonative.navigationMaxWindows = {
    set: function (maxWindows){
        var params = {
            data: maxWindows,
            persist: true
        };
        addCommand("gonative://navigationMaxWindows/set", params);
    },
    setCurrent: function(maxWindows){
        var params = {data: maxWindows};
        addCommand("gonative://navigationMaxWindows/set", params);
    }
}

gonative.connectivity = {
    get: function (params){
        return addCommandCallback("gonative://connectivity/get", params);
    },
    subscribe: function (params){
        return addCommandCallback("gonative://connectivity/subscribe", params, true);
    },
    unsubscribe: function (){
        addCommand("gonative://connectivity/unsubscribe");
    }
}

gonative.run = {
    deviceInfo: function(){
        addCommand("gonative://run/gonative_device_info");
    }
};

gonative.deviceInfo = function(){
    return addCommandCallback("gonative://run/gonative_device_info");
};

// onesignal
gonative.onesignal = {
    run: {
        onesignalInfo: function(){
            addCommand("gonative://run/gonative_onesignal_info");
        }
    },
    onesignalInfo: function(){
        return addCommandCallback("gonative://run/gonative_onesignal_info");
    },
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
        getTags: function(params){
            return addCommandCallback("gonative://onesignal/tags/get", params);
        },
        setTags: function (params){
            addCommand("gonative://onesignal/tags/set", params);
        }
    },
    showTagsUI: function (){
        addCommand("gonative://onesignal/showTagsUI");
    },
    promptLocation: function (){
        addCommand("gonative://onesignal/promptLocation");
    },
    iam: {
        addTrigger: function (triggers){
            if(triggers){
                var keyLocal = Object.keys(triggers)[0];
                var params = {
                    key: keyLocal,
                    value: triggers[keyLocal]
                };
                addCommand("gonative://onesignal/iam/addTrigger", params);
            }
        },
        addTriggers: function (params){
            addCommand("gonative://onesignal/iam/addTriggers", params);
        },
        removeTriggerForKey: function (key){
            var params = {key: key};
            addCommand("gonative://onesignal/iam/removeTriggerForKey", params);
        },
        getTriggerValueForKey: function (key){
            var params = {key: key};
            addCommand("gonative://onesignal/iam/getTriggerValueForKey", params);
        },
        pauseInAppMessages: function (){
            addCommand("gonative://onesignal/iam/pauseInAppMessages?pause=true");
        },
        resumeInAppMessages: function (){
            addCommand("gonative://onesignal/iam/pauseInAppMessages?pause=false");
        },
        setInAppMessageClickHandler: function (handler){
            var params = {handler: handler};
            addCommand("gonative://onesignal/iam/setInAppMessageClickHandler", params);
        }
    }
};

///////////////////////////////
////     iOS Exclusive     ////
///////////////////////////////

gonative.ios = {};

gonative.ios.window = {
    open: function (urlString){
        var params = {url: urlString};
        addCommand("gonative://window/open", params);
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
    request: function (params){
        return addCommandCallback("gonative://ios/attconsent/request", params);
    },
    status: function (params){
        return addCommandCallback("gonative://ios/attconsent/status", params);
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

gonative.android.audio = {
    requestFocus: function(enabled){
        var params = {enabled: enabled};
        addCommand("gonative://audio/requestFocus", params);
    }
};
