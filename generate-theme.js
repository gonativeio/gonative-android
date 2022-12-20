#!/usr/bin/env node
'use strict';

var fs = require('fs'), xml2js = require('xml2js');
var builder = new xml2js.Builder();

// Color resources files
var colorFileLight = require('path').join(__dirname, 'app/src/main/res/values/colors.xml');
var colorFileDark = require('path').join(__dirname, 'app/src/main/res/values-night/colors.xml');

// Check argument length
if (process.argv.length != 20) {
  console.log('Incomplete args - ' + process.argv.length + '/19');
  showHelp();
}

// LIGHT theme color defaults

var LIGHT_DEFAULT_ACTIONBAR_COLOR = 'ffffff';
var LIGHT_DEFAULT_STATUSBAR_COLOR = '757575';
var LIGHT_DEFAULT_TITLE_COLOR = '000000';
var LIGHT_DEFAULT_ACCENT_COLOR = '009688';
var LIGHT_DEFAULT_BACKGROUND_COLOR = 'ffffff';
var LIGHT_DEFAULT_SIDEBAR_FOREGROUND_COLOR = '1e4963';
var LIGHT_DEFAULT_SIDEBAR_BACKGROUND_COLOR = 'ffffff';
var LIGHT_DEFAULT_SIDEBAR_SEPARATOR_COLOR = '30808080';
var LIGHT_DEFAULT_SIDEBAR_HIGHLIGHT_COLOR = '1e496e';
var LIGHT_DEFAULT_TAB_BAR_BACKGROUND_COLOR = 'ffffff';
var LIGHT_DEFAULT_TAB_BAR_TEXT_COLOR = '949494';
var LIGHT_DEFAULT_TAB_BAR_INDICATOR_COLOR = '1e496e';
var LIGHT_DEFAULT_SWIPE_NAV_BACKGROUND_COLOR = 'fafafa';
var LIGHT_DEFAULT_SWIPE_NAV_INACTIVE_COLOR = '888888';
var LIGHT_DEFAULT_SWIPE_NAV_ACTIVE_COLOR = '1e88e5';
var LIGHT_DEFAULT_PULL_TO_REFRESH_COLOR = '1e496e';
var LIGHT_DEFAULT_SPLASH_BACKGROUND_COLOR = "1e496e";

// DARK theme color defaults

var DARK_DEFAULT_ACTIONBAR_COLOR = '212121';
var DARK_DEFAULT_STATUSBAR_COLOR = '030303';
var DARK_DEFAULT_TITLE_COLOR = 'ffffff';
var DARK_DEFAULT_ACCENT_COLOR = '80cbc4';
var DARK_DEFAULT_BACKGROUND_COLOR = '444444';
var DARK_DEFAULT_SIDEBAR_FOREGROUND_COLOR = 'ffffff';
var DARK_DEFAULT_SIDEBAR_BACKGROUND_COLOR = '333333';
var DARK_DEFAULT_SIDEBAR_SEPARATOR_COLOR = '30ffffff';
var DARK_DEFAULT_SIDEBAR_HIGHLIGHT_COLOR = 'ffffff';
var DARK_DEFAULT_TAB_BAR_BACKGROUND_COLOR = '333333';
var DARK_DEFAULT_TAB_BAR_TEXT_COLOR = 'ffffff';
var DARK_DEFAULT_TAB_BAR_INDICATOR_COLOR = '666666';
var DARK_DEFAULT_SWIPE_NAV_BACKGROUND_COLOR = '666666';
var DARK_DEFAULT_SWIPE_NAV_INACTIVE_COLOR = '888888';
var DARK_DEFAULT_SWIPE_NAV_ACTIVE_COLOR = 'ffffff';
var DARK_DEFAULT_PULL_TO_REFRESH_COLOR = 'ffffff';
var DARK_DEFAULT_SPLASH_BACKGROUND_COLOR = '333333';

// Handle args
// Theme
var theme = process.argv[2];

// Actionbar args
var actionBarColor = process.argv[3].toLowerCase();
var statusBarColor = process.argv[4].toLowerCase();
var titleColor = process.argv[5].toLowerCase();
var accentColor = process.argv[6].toLowerCase();
var backgroundColor = process.argv[7].toLowerCase();

// Sidebar args
var sideBarForegroundColor = process.argv[8].toLowerCase();
var sideBarBackgroundColor = process.argv[9].toLowerCase();
var sidebarSeparatorColor = process.argv[10].toLowerCase();
var sidebarHighlightColor = process.argv[11].toLowerCase();

// TabBar args
var tabBarBackground = process.argv[12].toLowerCase();
var tabBarTextColor = process.argv[13].toLowerCase();
var tabBarIndicatorColor = process.argv[14].toLowerCase();

// Swipe args
var swipeBackgroundColor = process.argv[15].toLowerCase();
var swipeInactiveColor = process.argv[16].toLowerCase();
var swipeActiveColor = process.argv[17].toLowerCase();

// Pull to Refresh args
var pullToRefresh = process.argv[18].toLowerCase();

// Splash args
var splashBackgroundColor = process.argv[19].toLowerCase();

var defaultActionBarColor;
var defaultStatusBarColor;
var defaultTitleColor;
var defaultAccentColor;
var defaultBackgroundColor;
var defaultSidebarForegroundColor;
var defaultSidebarBackgroundColor;
var defaultSidebarSeparatorColor;
var defaultSidebarHighlightColor;
var defaultTabBarBackgroundColor;
var defaultTabBarTextColor;
var defaultTabBarIndicatorColor;
var defaultSwipeBackgroundColor;
var defaultSwipeInactiveColor;
var defaultSwipeActiveColor;
var defaultPullToRefreshColor;
var defaultSplashBackgroundColor;

// Theme and default color values
var writeDir;
if (theme === 'light' || theme === 'auto') {
  writeDir = colorFileLight;

  defaultActionBarColor = LIGHT_DEFAULT_ACTIONBAR_COLOR;
  defaultStatusBarColor= LIGHT_DEFAULT_STATUSBAR_COLOR;
  defaultTitleColor = LIGHT_DEFAULT_TITLE_COLOR;
  defaultAccentColor = LIGHT_DEFAULT_ACCENT_COLOR;
  defaultBackgroundColor = LIGHT_DEFAULT_BACKGROUND_COLOR;
  defaultSidebarForegroundColor = LIGHT_DEFAULT_SIDEBAR_FOREGROUND_COLOR;
  defaultSidebarBackgroundColor = LIGHT_DEFAULT_SIDEBAR_BACKGROUND_COLOR;
  defaultSidebarSeparatorColor = LIGHT_DEFAULT_SIDEBAR_SEPARATOR_COLOR;
  defaultSidebarHighlightColor = LIGHT_DEFAULT_SIDEBAR_HIGHLIGHT_COLOR;
  defaultTabBarBackgroundColor = LIGHT_DEFAULT_TAB_BAR_BACKGROUND_COLOR;
  defaultTabBarTextColor = LIGHT_DEFAULT_TAB_BAR_TEXT_COLOR;
  defaultTabBarIndicatorColor = LIGHT_DEFAULT_TAB_BAR_INDICATOR_COLOR;
  defaultSwipeBackgroundColor = LIGHT_DEFAULT_SWIPE_NAV_BACKGROUND_COLOR;
  defaultSwipeInactiveColor = LIGHT_DEFAULT_SWIPE_NAV_INACTIVE_COLOR;
  defaultSwipeActiveColor = LIGHT_DEFAULT_SWIPE_NAV_ACTIVE_COLOR;
  defaultPullToRefreshColor = LIGHT_DEFAULT_PULL_TO_REFRESH_COLOR;
  defaultSplashBackgroundColor = LIGHT_DEFAULT_SPLASH_BACKGROUND_COLOR;

} else if (theme === 'dark' || theme === 'light.darkactionbar') {
  writeDir = colorFileDark;

  defaultActionBarColor = DARK_DEFAULT_ACTIONBAR_COLOR;
  defaultStatusBarColor= DARK_DEFAULT_STATUSBAR_COLOR;
  defaultTitleColor = DARK_DEFAULT_TITLE_COLOR;
  defaultAccentColor = DARK_DEFAULT_ACCENT_COLOR;
  defaultBackgroundColor = DARK_DEFAULT_BACKGROUND_COLOR;
  defaultSidebarForegroundColor = DARK_DEFAULT_SIDEBAR_FOREGROUND_COLOR;
  defaultSidebarBackgroundColor = DARK_DEFAULT_SIDEBAR_BACKGROUND_COLOR;
  defaultSidebarSeparatorColor = DARK_DEFAULT_SIDEBAR_SEPARATOR_COLOR;
  defaultSidebarHighlightColor = DARK_DEFAULT_SIDEBAR_HIGHLIGHT_COLOR;
  defaultTabBarBackgroundColor = DARK_DEFAULT_TAB_BAR_BACKGROUND_COLOR;
  defaultTabBarTextColor = DARK_DEFAULT_TAB_BAR_TEXT_COLOR;
  defaultTabBarIndicatorColor = DARK_DEFAULT_TAB_BAR_INDICATOR_COLOR;
  defaultSwipeBackgroundColor = DARK_DEFAULT_SWIPE_NAV_BACKGROUND_COLOR;
  defaultSwipeInactiveColor = DARK_DEFAULT_SWIPE_NAV_INACTIVE_COLOR;
  defaultSwipeActiveColor = DARK_DEFAULT_SWIPE_NAV_ACTIVE_COLOR;
  defaultPullToRefreshColor = DARK_DEFAULT_PULL_TO_REFRESH_COLOR;
  defaultSplashBackgroundColor = DARK_DEFAULT_SPLASH_BACKGROUND_COLOR;

} else {
  console.log('Invalid theme ' + theme);
  showHelp();
}

// Check if colors are valid
checkColorRegex(actionBarColor, 'Invalid actionbar background color');
checkColorRegex(statusBarColor, 'Invalid status bar color');
checkColorRegex(titleColor, 'Invalid actionbar title color');
checkColorRegex(accentColor, 'Invalid accent color');
checkColorRegex(backgroundColor, 'Invalid background color');
checkColorRegex(sideBarForegroundColor, 'Invalid sidebar foreground color');
checkColorRegex(sideBarBackgroundColor, 'Invalid sidebar background color');
checkColorRegex(sidebarSeparatorColor, 'Invalid sidebar separator color');
checkColorRegex(sidebarHighlightColor, 'Invalid sidebar highlight color');
checkColorRegex(tabBarBackground, 'Invalid tab bar background color');
checkColorRegex(tabBarTextColor, 'Invalid tab bar text color');
checkColorRegex(tabBarIndicatorColor, 'Invalid tab bar indicator color');
checkColorRegex(swipeBackgroundColor, 'Invalid swipe nav background color');
checkColorRegex(swipeInactiveColor, 'Invalid swipe nav inactive color');
checkColorRegex(swipeActiveColor, 'Invalid swipe nav active color');
checkColorRegex(pullToRefresh, 'Invalid pullToRefresh color');
checkColorRegex(splashBackgroundColor, 'Invalid splash background color');

// Start generating colors
var colorArray = [];

// Actionbar colors
colorArray.push(createColorJSON('colorPrimary', actionBarColor, defaultActionBarColor));
colorArray.push(createColorJSON('colorPrimaryDark', statusBarColor, defaultStatusBarColor));
colorArray.push(createColorJSON('titleTextColor', titleColor, defaultTitleColor));
colorArray.push(createColorJSON('colorAccent', accentColor, defaultAccentColor));
colorArray.push(createColorJSON('colorBackground', backgroundColor, defaultBackgroundColor));
colorArray.push(createColorJSON('drawerArrow', titleColor, defaultTitleColor));

// Sidebar colors
colorArray.push(createColorJSON('sidebarForeground', sideBarForegroundColor, defaultSidebarForegroundColor));
colorArray.push(createColorJSON('sidebarBackground', sideBarBackgroundColor, defaultSidebarBackgroundColor));
colorArray.push(createColorJSON('sidebarSeparatorColor', sidebarSeparatorColor, defaultSidebarSeparatorColor));
colorArray.push(createColorJSON('sidebarHighlight', sidebarHighlightColor, defaultSidebarHighlightColor));

// TabBar colors
colorArray.push(createColorJSON('tabBarBackground', tabBarBackground, defaultTabBarBackgroundColor));
colorArray.push(createColorJSON('tabBarTextColor', tabBarTextColor, defaultTabBarTextColor));
colorArray.push(createColorJSON('tabBarIndicator', tabBarIndicatorColor, defaultTabBarIndicatorColor));

// Swipe colors
colorArray.push(createColorJSON('swipe_nav_background', swipeBackgroundColor, defaultSwipeBackgroundColor));
colorArray.push(createColorJSON('swipe_nav_inactive', swipeInactiveColor, defaultSwipeInactiveColor));
colorArray.push(createColorJSON('swipe_nav_active', swipeActiveColor, defaultSwipeActiveColor));

// Pull to Refresh color
colorArray.push(createColorJSON('pull_to_refresh_color', pullToRefresh, defaultPullToRefreshColor));

// Splash colors
colorArray.push(createColorJSON('splash_background', splashBackgroundColor, defaultSplashBackgroundColor));

// Build colors in xml from JSON array of colors
var xmlFinal = builder.buildObject({resources: colorArray});

// Write to file
fs.writeFile(writeDir, xmlFinal, (err) => {
    if (err)
      console.log(err);
    else {
      console.log("File written successfully\n");
    }
});

// Helper functions

function showHelp() {
  console.log('Usage: generate-theme.js (dark|light|light.darkactionbar|auto) ' +
          'actionBarColor statusBarColor titleColor accentColor backgroundColor ' +
          'sidebarForegroundColor sidebarBackgroundColor sidebarSeparatorColor sidebarHighlightColor ' +
          'tabBarBackgroundColor tabBarTextColor tabBarIndicatorColor ' +
          'swipeBackgroundColor swipeInactiveColor swipeActiveColor pullToRefreshColor splashBackgroundColor');
  console.log('Example: generate-theme.js light dcdcdc 757575 000000 ff0000 ...');
  console.log('Colors can be blank for default');
  process.exit(1);
}

function checkColorRegex(color, message) {
  if (color !== '' && !/^(([0-9a-f]){6}|([0-9a-f]){8})$/.test(color)) {
      console.log(message + ' ' + color);
      showHelp();
  }
}

// Creates a JSON object of color which will be then transformed to xml
function createColorJSON(colorName, colorCode, defaultColor) {
  if (colorCode !== '') {
    return {color: {$: {name: colorName}, _: '#' + colorCode}};
  } else {
    return {color: {$: {name: colorName}, _: '#' + defaultColor}};
  }
}
