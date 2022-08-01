#!/usr/bin/env node
'use strict';

var DARK_DEFAULT_ACTIONBAR_COLOR = '212121';
var DARK_DEFAULT_STATUSBAR_COLOR = '030303';
var DARK_DEFAULT_TITLE_COLOR = 'ffffff';
var DARK_DEFAULT_ACCENT_COLOR = '80cbc4';
var DARK_DEFAULT_BACKGROUND_COLOR = '444444';
var DARK_DEFAULT_SIDEBAR_SEPARATOR_COLOR = '30ffffff';
var DARK_DEFAULT_TAB_BAR_INDICATOR_COLOR = '1e88e5';
var DARK_DEFAULT_SIDEBAR_HIGHLIGHT_COLOR = 'ffffff';
var DARK_DEFAULT_SWIPE_NAV_BACKGROUND_COLOR = '666666';
var DARK_DEFAULT_SWIPE_NAV_INACTIVE_COLOR = '888888';
var DARK_DEFAULT_SWIPE_NAV_ACTIVE_COLOR = 'ffffff';

var LIGHT_DEFAULT_ACTIONBAR_COLOR = 'ffffff';
var LIGHT_DEFAULT_STATUSBAR_COLOR = '757575';
var LIGHT_DEFAULT_TITLE_COLOR = '000000';
var LIGHT_DEFAULT_ACCENT_COLOR = '009688';
var LIGHT_DEFAULT_BACKGROUND_COLOR = 'ffffff';
var LIGHT_DEFAULT_SIDEBAR_SEPARATOR_COLOR = '30808080';
var LIGHT_DEFAULT_TAB_BAR_INDICATOR_COLOR = '2f79fe';
var LIGHT_DEFAULT_SIDEBAR_HIGHLIGHT_COLOR = '1e496e';
var LIGHT_DEFAULT_SWIPE_NAV_BACKGROUND_COLOR = 'fafafa';
var LIGHT_DEFAULT_SWIPE_NAV_INACTIVE_COLOR = '888888';
var LIGHT_DEFAULT_SWIPE_NAV_ACTIVE_COLOR = '1e88e5';

function showHelp() {
    console.log('Usage: generate-theme.js (dark|light|light.darkactionbar|auto) '+
            + 'actionBarColor statusBarColor titleColor accentColor backgroundColor sidebarSeparatorColor tabBarIndicatorColor sidebarHighlightColor swipeBackgroundColor swipeInactiveColor swipeActiveColor '
            + 'actionBarColorDark statusBarColorDark titleColorDark accentColorDark backgroundColorDark sidebarSeparatorColorDark tabBarIndicatorColorDark sidebarHighlightColorDark swipeBackgroundColorDark swipeInactiveColorDark swipeActiveColorDark');
    console.log('Example: generate-theme.js light dcdcdc 757575 000000 ff0000 ...');
    console.log('Colors can be blank for default');
    process.exit(1);
}

if (process.argv.length != 25) {
    console.log('Incomplete args');
    showHelp();
}

var colorFileLight = require('path').join(__dirname, 'app/src/main/res/values/colors.xml');
var colorFileDark = require('path').join(__dirname, 'app/src/main/res/values-night/colors.xml');

var theme = process.argv[2].toLowerCase();
var actionBarColor = process.argv[3].toLowerCase();
var statusBarColor = process.argv[4].toLowerCase();
var titleColor = process.argv[5].toLowerCase();
var accentColor = process.argv[6].toLowerCase();
var backgroundColor = process.argv[7].toLowerCase();
var sidebarSeparatorColor = process.argv[8].toLowerCase();
var tabBarIndicatorColor = process.argv[9].toLowerCase();
var sidebarHighlightColor = process.argv[10].toLowerCase();
var swipeBackgroundColor = process.argv[11].toLowerCase();
var swipeInactiveColor = process.argv[12].toLowerCase();
var swipeActiveColor = process.argv[13].toLowerCase();

var actionBarColorDark = process.argv[14].toLowerCase();
var statusBarColorDark = process.argv[15].toLowerCase();
var titleColorDark = process.argv[16].toLowerCase();
var accentColorDark = process.argv[17].toLowerCase();
var backgroundColorDark = process.argv[18].toLowerCase();
var sidebarSeparatorColorDark = process.argv[19].toLowerCase();
var tabBarIndicatorColorDark = process.argv[20].toLowerCase();
var sidebarHighlightColorDark = process.argv[21].toLowerCase();
var swipeBackgroundColorDark = process.argv[22].toLowerCase();
var swipeInactiveColorDark = process.argv[23].toLowerCase();
var swipeActiveColorDark = process.argv[24].toLowerCase();

var defaultActionBarColor;
var defaultStatusBarColor;
var defaultTitleColor;
var defaultAccentColor;
var defaultBackgroundColor;
var defaultSidebarSeparatorColor;
var defaultTabBarIndicatorColor;
var defaultSidebarHighlightColor;
var defaultSwipeBackgroundColor;
var defaultSwipeInactiveColor;
var defaultSwipeActiveColor;

// verify inputs
if (theme === 'light' || theme === 'auto') {
    defaultActionBarColor = LIGHT_DEFAULT_ACTIONBAR_COLOR;
    defaultStatusBarColor = LIGHT_DEFAULT_STATUSBAR_COLOR;
    defaultTitleColor = LIGHT_DEFAULT_TITLE_COLOR;
    defaultAccentColor = LIGHT_DEFAULT_ACCENT_COLOR;
    defaultBackgroundColor = LIGHT_DEFAULT_BACKGROUND_COLOR;
    defaultSidebarSeparatorColor = LIGHT_DEFAULT_SIDEBAR_SEPARATOR_COLOR;
    defaultTabBarIndicatorColor = LIGHT_DEFAULT_TAB_BAR_INDICATOR_COLOR;
    defaultSidebarHighlightColor = LIGHT_DEFAULT_SIDEBAR_HIGHLIGHT_COLOR;
    defaultSwipeBackgroundColor = LIGHT_DEFAULT_SWIPE_NAV_BACKGROUND_COLOR;
    defaultSwipeInactiveColor = LIGHT_DEFAULT_SWIPE_NAV_INACTIVE_COLOR;
    defaultSwipeActiveColor = LIGHT_DEFAULT_SWIPE_NAV_ACTIVE_COLOR;
} else if (theme === 'dark' || theme === 'light.darkactionbar') {
    defaultActionBarColor = DARK_DEFAULT_ACTIONBAR_COLOR;
    defaultStatusBarColor = DARK_DEFAULT_STATUSBAR_COLOR;
    defaultTitleColor = DARK_DEFAULT_TITLE_COLOR;
    defaultAccentColor = DARK_DEFAULT_ACCENT_COLOR;
    defaultBackgroundColor = DARK_DEFAULT_BACKGROUND_COLOR;
    defaultSidebarSeparatorColor = DARK_DEFAULT_SIDEBAR_SEPARATOR_COLOR;
    defaultTabBarIndicatorColor = DARK_DEFAULT_TAB_BAR_INDICATOR_COLOR;
    defaultSidebarHighlightColor = DARK_DEFAULT_SIDEBAR_HIGHLIGHT_COLOR;
    defaultSwipeBackgroundColor = DARK_DEFAULT_SWIPE_NAV_BACKGROUND_COLOR;
    defaultSwipeInactiveColor = DARK_DEFAULT_SWIPE_NAV_INACTIVE_COLOR;
    defaultSwipeActiveColor = DARK_DEFAULT_SWIPE_NAV_ACTIVE_COLOR;
} else {
    console.log('Invalid theme ' + theme);
    showHelp();
}

checkColorRegex(actionBarColor, 'Invalid actionbar background color');
checkColorRegex(statusBarColor, 'Invalid status bar color');
checkColorRegex(titleColor, 'Invalid actionbar title color');
checkColorRegex(accentColor, 'Invalid accent color');
checkColorRegex(backgroundColor, 'Invalid background color');
checkColorRegex(sidebarSeparatorColor, 'Invalid sidebar separator color');
checkColorRegex(tabBarIndicatorColor, 'Invalid tab bar indicator color');
checkColorRegex(sidebarHighlightColor, 'Invalid sidebar highlight color');
checkColorRegex(swipeBackgroundColor, 'Invalid swipe nav background color');
checkColorRegex(swipeInactiveColor, 'Invalid swipe nav inactive color');
checkColorRegex(swipeActiveColor, 'Invalid swipe nav active color');

checkColorRegex(actionBarColorDark, 'Invalid actionbar background color dark');
checkColorRegex(statusBarColorDark, 'Invalid status bar color dark');
checkColorRegex(titleColorDark, 'Invalid actionbar title color dark');
checkColorRegex(accentColorDark, 'Invalid accent color dark');
checkColorRegex(backgroundColorDark, 'Invalid background color dark');
checkColorRegex(sidebarSeparatorColorDark, 'Invalid sidebar separator color dark');
checkColorRegex(tabBarIndicatorColorDark, 'Invalid tab bar indicator color dark');
checkColorRegex(sidebarHighlightColorDark, 'Invalid sidebar highlight color dark');
checkColorRegex(swipeBackgroundColorDark, 'Invalid swipe nav background color dark');
checkColorRegex(swipeInactiveColorDark, 'Invalid swipe nav inactive color dark');
checkColorRegex(swipeActiveColorDark, 'Invalid swipe nav active color dark');

// set actionbar background color
if (actionBarColor !== '' && actionBarColor !== defaultActionBarColor) {
    console.log('Setting action bar background color to ' + actionBarColor);
    var s = '<color name="colorPrimary">#' + actionBarColor + '</color>';
    replaceStringInFile(colorFileLight, '<color name="colorPrimary">#FFFFFF</color>', s);
}
if (actionBarColorDark !== '' && actionBarColorDark !== defaultActionBarColor) {
    console.log('Setting action bar background color dark to ' + actionBarColorDark);
    var s = '<color name="colorPrimary">#' + actionBarColorDark + '</color>';
    replaceStringInFile(colorFileDark, '<color name="colorPrimary">#212121</color>', s);
}

// set status bar color
if (statusBarColor !== '' && statusBarColor !== defaultStatusBarColor) {
    console.log('Setting status bar background color to ' + statusBarColor);
    var s = '<color name="colorPrimaryDark">#' + statusBarColor + '</color>';
    replaceStringInFile(colorFileLight, '<color name="colorPrimaryDark">#757575</color>', s);
}
if (statusBarColorDark !== '' && statusBarColorDark !== defaultStatusBarColor) {
    console.log('Setting status bar background color dark to ' + statusBarColorDark);
    var s = '<color name="colorPrimaryDark">#' + statusBarColorDark + '</color>';
    replaceStringInFile(colorFileDark, '<color name="colorPrimaryDark">#030303</color>', s);
}

// set actionbar title color
if (titleColor !== '' && titleColor !== defaultTitleColor) {
    console.log('Setting action bar title color to ' + titleColor);
    var s = '<color name="titleTextColor">#' + titleColor + '</color>';
    replaceStringInFile(colorFileLight, '<color name="titleTextColor">#000000</color>', s);
    console.log('Setting drawerArrowColor to ' + titleColor);
    s = '<color name="drawerArrow">#' + titleColor + '</color>';
    replaceStringInFile(colorFileLight, '<color name="drawerArrow">#000000</color>', s);
}
if (titleColorDark !== '' && titleColorDark !== defaultTitleColor) {
    console.log('Setting action bar title color dark to ' + titleColorDark);
    var s = '<color name="titleTextColor">#' + titleColorDark + '</color>';
    replaceStringInFile(colorFileDark, '<color name="titleTextColor">#FFFFFF</color>', s);
    console.log('Setting drawerArrowColor to ' + titleColorDark);
    s = '<color name="drawerArrow">#' + titleColorDark + '</color>';
    replaceStringInFile(colorFileDark, '<color name="drawerArrow">#FFFFFF</color>', s);
}

// set accent color
if (accentColor !== '' && accentColor !== defaultAccentColor) {
    console.log('Setting accent color to ' + accentColor);
    var s = '<color name="colorAccent">#' + accentColor + '</color>';
    replaceStringInFile(colorFileLight, '<color name="colorAccent">#009688</color>', s);
}
if (accentColorDark !== '' && accentColorDark !== defaultAccentColor) {
    console.log('Setting accent color dark to ' + accentColorDark);
    var s = '<color name="colorAccent">#' + accentColorDark + '</color>';
    replaceStringInFile(colorFileDark, '<color name="colorAccent">#80cbc4</color>', s);
}

// set background color
if (backgroundColor !== '' && backgroundColor !== defaultBackgroundColor) {
    console.log('Setting background color to ' + backgroundColor);
    var s = '<color name="colorBackground">#' + backgroundColor + '</color>';
    replaceStringInFile(colorFileLight, '<color name="colorBackground">#FFFFFF</color>', s);
}
if (backgroundColorDark !== '' && backgroundColorDark !== defaultBackgroundColor) {
    console.log('Setting background color dark to ' + backgroundColorDark);
    var s = '<color name="colorBackground">#' + backgroundColorDark + '</color>';
    replaceStringInFile(colorFileDark, '<color name="colorBackground">#444444</color>', s);
}

// set sidebarSeparatorColor color
if (sidebarSeparatorColor !== '' && sidebarSeparatorColor !== defaultSidebarSeparatorColor) {
    console.log('Setting sidebar separator color to ' + sidebarSeparatorColor);
    var s = '<color name="sidebarSeparatorColor">#' + sidebarSeparatorColor + '</color>';
    replaceStringInFile(colorFileLight, '<color name="sidebarSeparatorColor">#30808080</color>', s);
}
if (sidebarSeparatorColorDark !== '' && sidebarSeparatorColorDark !== defaultSidebarSeparatorColor) {
    console.log('Setting sidebar separator color dark to ' + sidebarSeparatorColorDark);
    var s = '<color name="sidebarSeparatorColor">#' + sidebarSeparatorColorDark + '</color>';
    replaceStringInFile(colorFileDark, '<color name="sidebarSeparatorColor">#30FFFFFF</color>', s);
}

// set tabBarIndicatorColor color
if (tabBarIndicatorColor !== '' && tabBarIndicatorColor !== defaultTabBarIndicatorColor) {
    console.log('Setting tab bar indicator color to ' + tabBarIndicatorColor);
    var s = '<color name="tabBarIndicator">#' + tabBarIndicatorColor + '</color>';
    replaceStringInFile(colorFileLight, '<color name="tabBarIndicator">#2F79FE</color>', s);
}
if (tabBarIndicatorColorDark !== '' && tabBarIndicatorColorDark !== defaultTabBarIndicatorColor) {
    console.log('Setting tab bar indicator color dark to ' + tabBarIndicatorColorDark);
    var s = '<color name="tabBarIndicator">#' + tabBarIndicatorColorDark + '</color>';
    replaceStringInFile(colorFileDark, '<color name="tabBarIndicator">#1E88E5</color>', s);
}

// set sidebarHighlightColor color
if (sidebarHighlightColor !== '' && sidebarHighlightColor !== defaultSidebarHighlightColor) {
    console.log('Setting sidebar highlight color to ' + sidebarHighlightColor);
    var s = '<color name="sideBarHighlight">#' + sidebarHighlightColor + '</color>';
    replaceStringInFile(colorFileLight, '<color name="sideBarHighlight">#1E496E</color>', s);
}
if (sidebarHighlightColorDark !== '' && sidebarHighlightColorDark !== defaultSidebarHighlightColor) {
    console.log('Setting sidebar highlight color dark to ' + sidebarHighlightColorDark);
    var s = '<color name="sideBarHighlight">#' + sidebarHighlightColorDark + '</color>';
    replaceStringInFile(colorFileDark, '<color name="sideBarHighlight">#FFFFFF</color>', s);
}

// set swipeBackgroundColor color
if (swipeBackgroundColor !== '' && swipeBackgroundColor !== defaultSwipeBackgroundColor) {
    console.log('Setting swipe background color to ' + swipeBackgroundColor);
    var s = '<color name="swipe_nav_background">#' + swipeBackgroundColor + '</color>';
    replaceStringInFile(colorFileLight, '<color name="swipe_nav_background">#FAFAFA</color>', s);
}
if (swipeBackgroundColorDark !== '' && swipeBackgroundColorDark !== defaultSwipeBackgroundColor) {
    console.log('Setting swipe background color dark to ' + swipeBackgroundColorDark);
    var s = '<color name="swipe_nav_background">#' + swipeBackgroundColorDark + '</color>';
    replaceStringInFile(colorFileDark, '<color name="swipe_nav_background">#666666</color>', s);
}

// set swipeInactiveColor color
if (swipeInactiveColor !== '' && swipeInactiveColor !== defaultSwipeInactiveColor) {
    console.log('Setting swipe inactive color to ' + swipeInactiveColor);
    var s = '<color name="swipe_nav_inactive">#' + swipeInactiveColor + '</color>';
    replaceStringInFile(colorFileLight, '<color name="swipe_nav_inactive">#888888</color>', s);
}
if (swipeInactiveColorDark !== '' && swipeInactiveColorDark !== defaultSwipeInactiveColor) {
    console.log('Setting swipe inactive color dark to ' + swipeInactiveColorDark);
    var s = '<color name="swipe_nav_inactive">#' + swipeInactiveColorDark + '</color>';
    replaceStringInFile(colorFileDark, '<color name="swipe_nav_inactive">#888888</color>', s);
}

// set swipeActiveColor color
if (swipeActiveColor !== '' && swipeActiveColor !== defaultSwipeActiveColor) {
    console.log('Setting swipe active color to ' + swipeActiveColor);
    var s = '<color name="swipe_nav_active">#' + swipeActiveColor + '</color>';
    replaceStringInFile(colorFileLight, '<color name="swipe_nav_active">#1E88E5</color>', s);
}
if (swipeActiveColorDark !== '' && swipeActiveColorDark !== defaultSwipeActiveColor) {
    console.log('Setting swipe active color dark to ' + swipeActiveColorDark);
    var s = '<color name="swipe_nav_active">#' + swipeActiveColorDark + '</color>';
    replaceStringInFile(colorFileDark, '<color name="swipe_nav_active">#FFFFFF</color>', s);
}

console.log('done');

function replaceStringInFile(filename, searchvalue, newvalue) {
    var fs = require('fs');
    var contents = fs.readFileSync(filename, {
        encoding: 'utf8'
    });
    var newContents = contents.replace(searchvalue, newvalue);
    fs.writeFileSync(filename, newContents);
}

function checkColorRegex(color, message) {
    if (color !== '' && !/^(([0-9a-f]){6}|([0-9a-f]){8})$/.test(color)) {
        console.log(message + ' ' + accentColor);
        showHelp();
    }
}
