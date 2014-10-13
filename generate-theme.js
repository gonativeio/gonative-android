#!/usr/bin/env node

var DARK_DEFAULT_ACTIONBAR_COLOR = '050505';
var DARK_DEFAULT_TITLE_COLOR = 'f3f3f3';
var LIGHT_DEFAULT_ACTIONBAR_COLOR = 'dcdcdc';
var LIGHT_DEFAULT_TITLE_COLOR = '000000';

function showHelp() {
    console.log('Usage: generate-theme.js (Dark|Light|Light.DarkActionBar) actionBarColor titleColor');
    console.log('Example: generate-theme.js Light dcdcdc 000000');
    console.log('Either actionBarColor or titleColor can be blank for default');
    process.exit(1);
}


if (process.argv.length != 5) {
    showHelp();
}

var styleFile = require('path').join(__dirname, 'app/src/main/res/values/styles.xml');

var theme = process.argv[2].toLowerCase();
var actionBarColor = process.argv[3].toLowerCase();
var titleColor = process.argv[4].toLowerCase();

var defaultActionBarColor;
var defaultTitleColor;

// verify inputs
if (theme === 'light') {
    defaultActionBarColor = LIGHT_DEFAULT_ACTIONBAR_COLOR;
    defaultTitleColor = LIGHT_DEFAULT_TITLE_COLOR;
} else if (theme === 'dark' || theme === 'light.darkactionbar') {
    defaultActionBarColor = DARK_DEFAULT_ACTIONBAR_COLOR;
    defaultTitleColor = DARK_DEFAULT_TITLE_COLOR;
} else {
    console.log('Invalid theme ' + theme);
    showHelp();
}

if (actionBarColor !== '' && !/^([0-9a-f]){6}$/.test(actionBarColor)) {
    console.log('Invalid action bar background color ' + actionBarColor);
    showHelp();
}

if (titleColor !== '' && !/^([0-9a-f]){6}$/.test(titleColor)) {
    console.log('Invalid action bar title color ' + titleColor);
    showHelp();
}


// set theme
if (theme === 'light') {
    // default theme. No change necessary.
} else if (theme === 'dark') {
    console.log('Setting dark theme');
    replaceStringInFile(styleFile, '<style name="GoNativeTheme.WithActionBar" parent="GN.Light">',
        '<style name="GoNativeTheme.WithActionBar" parent="GN.Dark">');
    replaceStringInFile(styleFile, '<style name="GN.ActionBar" parent="@android:style/Widget.Holo.Light.ActionBar.Solid">',
        '<style name="GN.ActionBar" parent="@android:style/Widget.Holo.ActionBar.Solid">');
} else if (theme === 'light.darkactionbar') {
    console.log('Setting light with dark actionbar theme');
        replaceStringInFile(styleFile, '<style name="GoNativeTheme.WithActionBar" parent="GN.Light">',
        '<style name="GoNativeTheme.WithActionBar" parent="GN.LightWithDarkActionBar">');
    replaceStringInFile(styleFile, '<style name="GN.ActionBar" parent="@android:style/Widget.Holo.Light.ActionBar.Solid">',
        '<style name="GN.ActionBar" parent="@android:style/Widget.Holo.Light.ActionBar.Solid.Inverse">');
}

// set actionbar background color
if (actionBarColor !== '' && actionBarColor !== defaultActionBarColor) {
    console.log('Setting action bar background color to ' + actionBarColor);
    var s = '<item name="android:background">#' + actionBarColor + '</item>';
    replaceStringInFile(styleFile, '<!--gonative placeholder: actionbarBackgroundColor-->', s);
}


// set actionbar title color
if (titleColor !== '' && titleColor !== defaultTitleColor) {
    console.log('Setting action bar title color to ' + titleColor);
    var s = '<item name="android:textColor">#' + titleColor + '</item>';
    replaceStringInFile(styleFile, '<!--gonative placeholder: actionbarTitleColor-->', s);
}

function replaceStringInFile(filename, searchvalue, newvalue) {
    var fs = require('fs');
    contents = fs.readFileSync(filename, {
        encoding: 'utf8'
    });
    var newContents = contents.replace(searchvalue, newvalue);
    fs.writeFileSync(filename, newContents);
}