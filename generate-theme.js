#!/usr/bin/env node

var DARK_DEFAULT_ACTIONBAR_COLOR = '212121';
var DARK_DEFAULT_STATUSBAR_COLOR = '030303';
var DARK_DEFAULT_TITLE_COLOR = 'ffffff';
var DARK_DEFAULT_ACCENT_COLOR = '80cbc4';

var LIGHT_DEFAULT_ACTIONBAR_COLOR = 'bdbdbd';
var LIGHT_DEFAULT_STATUSBAR_COLOR = '757575';
var LIGHT_DEFAULT_TITLE_COLOR = '000000';
var LIGHT_DEFAULT_ACCENT_COLOR = '009688';

function showHelp() {
    console.log('Usage: generate-theme.js (Dark|Light|Light.DarkActionBar) actionBarColor statusBarColor titleColor accentColor ');
    console.log('Example: generate-theme.js Light dcdcdc 757575 000000 ff0000');
    console.log('Colors can be blank for default');
    process.exit(1);
}

if (process.argv.length != 7) {
    showHelp();
}

var styleFile = require('path').join(__dirname, 'app/src/main/res/values/styles.xml');
var colorFile = require('path').join(__dirname, 'app/src/main/res/values/colors.xml');

var theme = process.argv[2].toLowerCase();
var actionBarColor = process.argv[3].toLowerCase();
var statusBarColor = process.argv[4].toLowerCase();
var titleColor = process.argv[5].toLowerCase();
var accentColor = process.argv[6].toLowerCase();

var defaultActionBarColor;
var defaultStatusBarColor;
var defaultTitleColor;
var defaultAccentColor;

// verify inputs
if (theme === 'light') {
    defaultActionBarColor = LIGHT_DEFAULT_ACTIONBAR_COLOR;
    defaultStatusBarColor = LIGHT_DEFAULT_STATUSBAR_COLOR;
    defaultTitleColor = LIGHT_DEFAULT_TITLE_COLOR;
    defaultAccentColor = LIGHT_DEFAULT_ACCENT_COLOR;
} else if (theme === 'dark' || theme === 'light.darkactionbar') {
    defaultActionBarColor = DARK_DEFAULT_ACTIONBAR_COLOR;
    defaultStatusBarColor = DARK_DEFAULT_STATUSBAR_COLOR;
    defaultTitleColor = DARK_DEFAULT_TITLE_COLOR;
    defaultAccentColor = DARK_DEFAULT_ACCENT_COLOR;
} else {
    console.log('Invalid theme ' + theme);
    showHelp();
}

if (actionBarColor !== '' && !/^([0-9a-f]){6}$/.test(actionBarColor)) {
    console.log('Invalid action bar background color ' + actionBarColor);
    showHelp();
}

if (statusBarColor !== '' && !/^([0-9a-f]){6}$/.test(statusBarColor)) {
    console.log('Invalid status bar color color ' + statusBarColor);
    showHelp();
}

if (titleColor !== '' && !/^([0-9a-f]){6}$/.test(titleColor)) {
    console.log('Invalid action bar title color ' + titleColor);
    showHelp();
}

if (accentColor !== '' && !/^([0-9a-f]){6}$/.test(accentColor)) {
    console.log('Invalid accent color ' + accentColor);
    showHelp();
}

// set theme
if (theme === 'light') {
    console.log('Setting light theme');
    replaceStringInFile(styleFile, '<style name="GoNativeTheme.WithActionBar" parent="GN.LightWithDarkActionBar">',
        '<style name="GoNativeTheme.WithActionBar" parent="GN.Light">');
} else if (theme === 'dark') {
    console.log('Setting dark theme');
    replaceStringInFile(styleFile, '<style name="GoNativeTheme.WithActionBar" parent="GN.LightWithDarkActionBar">',
        '<style name="GoNativeTheme.WithActionBar" parent="GN.Dark">');
} else if (theme === 'light.darkactionbar') {
    console.log('Setting light with dark actionbar theme');
    // default theme, no change necessary
}

// set actionbar background color
if (actionBarColor !== '' && actionBarColor !== defaultActionBarColor) {
    console.log('Setting action bar background color to ' + actionBarColor);
    var s = '<item name="colorPrimary">#' + actionBarColor + '</item>';
    replaceStringInFile(styleFile, '<!--GoNative placeholder: colorPrimary-->', s);
}

// set status bar color
if (statusBarColor !== '' && statusBarColor !== defaultStatusBarColor) {
    console.log('Setting status bar background color to ' + statusBarColor);
    var s = '<item name="colorPrimaryDark">#' + statusBarColor + '</item>';
    replaceStringInFile(styleFile, '<!--GoNative placeholder: colorPrimaryDark-->', s);
}

// set actionbar title color
if (titleColor !== '' && titleColor !== defaultTitleColor) {
    console.log('Setting action bar title color to ' + titleColor);
    var s = '<item name="android:textColorPrimary">#' + titleColor + '</item>';
    replaceStringInFile(styleFile, '<!--GoNative placeholder: titleTextColor-->', s);
}

// tab foreground color
var tabForegroundColor = titleColor == '' ? defaultTitleColor : titleColor;
console.log('Setting tab foreground color to ' + tabForegroundColor);
replaceStringInFile(colorFile, '<color name="tab_foreground_color">#FFFFFF</color>', 
    '<color name="tab_foreground_color">#' + tabForegroundColor +'</color>');

// set accent color
if (accentColor !== '' && accentColor !== defaultAccentColor) {
    console.log('Setting accent color to ' + accentColor);
    var s = '<item name="colorAccent">#' + accentColor + '</item>';
    replaceStringInFile(styleFile, '<!--GoNative placeholder: colorAccent-->', s);
}

function replaceStringInFile(filename, searchvalue, newvalue) {
    var fs = require('fs');
    contents = fs.readFileSync(filename, {
        encoding: 'utf8'
    });
    var newContents = contents.replace(searchvalue, newvalue);
    fs.writeFileSync(filename, newContents);
}