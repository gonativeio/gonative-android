#!/bin/sh

BASEDIR=$(dirname $0)

# intercom notification icon
if [[ $1 == "intercom" ]]
then
  cp $BASEDIR/app/src/main/res/drawable-mdpi/ic_notification.png $BASEDIR/app/src/main/res/drawable-mdpi/intercom_push_icon.png
  cp $BASEDIR/app/src/main/res/drawable-hdpi/ic_notification.png $BASEDIR/app/src/main/res/drawable-hdpi/intercom_push_icon.png
  cp $BASEDIR/app/src/main/res/drawable-xhdpi/ic_notification.png $BASEDIR/app/src/main/res/drawable-xhdpi/intercom_push_icon.png
  cp $BASEDIR/app/src/main/res/drawable-xxhdpi/ic_notification.png $BASEDIR/app/src/main/res/drawable-xxhdpi/intercom_push_icon.png
  cp $BASEDIR/app/src/main/res/drawable-xxxhdpi/ic_notification.png $BASEDIR/app/src/main/res/drawable-xxxhdpi/intercom_push_icon.png
fi

# cordial notification icon
if [[ $1 == "cordial" ]]
then
  cp $BASEDIR/app/src/main/res/drawable-mdpi/ic_notification.png $BASEDIR/plugins/cordial/src/main/res/drawable-mdpi/ic_notification.png
  cp $BASEDIR/app/src/main/res/drawable-hdpi/ic_notification.png $BASEDIR/plugins/cordial/src/main/res/drawable-hdpi/ic_notification.png
  cp $BASEDIR/app/src/main/res/drawable-xhdpi/ic_notification.png $BASEDIR/plugins/cordial/src/main/res/drawable-xhdpi/ic_notification.png
  cp $BASEDIR/app/src/main/res/drawable-xxhdpi/ic_notification.png $BASEDIR/plugins/cordial/src/main/res/drawable-xxhdpi/ic_notification.png
  cp $BASEDIR/app/src/main/res/drawable-xxxhdpi/ic_notification.png $BASEDIR/plugins/cordial/src/main/res/drawable-xxxhdpi/ic_notification.png
fi

# braze notification icon
if [[ $1 == "braze" ]]
then
  cp $BASEDIR/app/src/main/res/drawable-mdpi/ic_notification.png $BASEDIR/plugins/braze/src/main/res/drawable-mdpi/ic_notification.png
  cp $BASEDIR/app/src/main/res/drawable-hdpi/ic_notification.png $BASEDIR/plugins/braze/src/main/res/drawable-hdpi/ic_notification.png
  cp $BASEDIR/app/src/main/res/drawable-xhdpi/ic_notification.png $BASEDIR/plugins/braze/src/main/res/drawable-xhdpi/ic_notification.png
  cp $BASEDIR/app/src/main/res/drawable-xxhdpi/ic_notification.png $BASEDIR/plugins/braze/src/main/res/drawable-xxhdpi/ic_notification.png
  cp $BASEDIR/app/src/main/res/drawable-xxxhdpi/ic_notification.png $BASEDIR/plugins/braze/src/main/res/drawable-xxxhdpi/ic_notification.png
fi

# moengage notification icon
if [[ $1 == "moengage" ]]
then
  cp $BASEDIR/app/src/main/res/drawable-mdpi/ic_notification.png $BASEDIR/plugins/moengage/src/main/res/drawable-mdpi/ic_notification.png
  cp $BASEDIR/app/src/main/res/drawable-hdpi/ic_notification.png $BASEDIR/plugins/moengage/src/main/res/drawable-hdpi/ic_notification.png
  cp $BASEDIR/app/src/main/res/drawable-xhdpi/ic_notification.png $BASEDIR/plugins/moengage/src/main/res/drawable-xhdpi/ic_notification.png
  cp $BASEDIR/app/src/main/res/drawable-xxhdpi/ic_notification.png $BASEDIR/plugins/moengage/src/main/res/drawable-xxhdpi/ic_notification.png
  cp $BASEDIR/app/src/main/res/drawable-xxxhdpi/ic_notification.png $BASEDIR/plugins/moengage/src/main/res/drawable-xxxhdpi/ic_notification.png
fi
