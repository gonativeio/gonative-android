#!/bin/sh

BASEDIR=$(dirname $0)
sips -z 1024 1024 -s format png --out $BASEDIR/AppIconTemp.png $BASEDIR/AppIcon 2>&1

convert -size 1024x1024 xc:none -draw "roundrectangle 0,0,1024,1024,80,80" $BASEDIR/mask.png 2>&1
convert $BASEDIR/AppIconTemp.png -matte $BASEDIR/mask.png -compose DstIn -composite $BASEDIR/AppIconRound.png 2>&1

rm -f $BASEDIR/AppIconTemp.png
rm -f $BASEDIR/mask.png

sips -z 48 48 -s format png --out $BASEDIR/app/src/main/res/drawable-mdpi/ic_launcher.png $BASEDIR/AppIconRound.png 2>&1
sips -z 72 72 -s format png --out $BASEDIR/app/src/main/res/drawable-hdpi/ic_launcher.png $BASEDIR/AppIconRound.png 2>&1
sips -z 96 96 -s format png --out $BASEDIR/app/src/main/res/drawable-xhdpi/ic_launcher.png $BASEDIR/AppIconRound.png 2>&1
sips -z 144 144 -s format png --out $BASEDIR/app/src/main/res/drawable-xxhdpi/ic_launcher.png $BASEDIR/AppIconRound.png 2>&1
sips -z 192 192 -s format png --out $BASEDIR/app/src/main/res/drawable-xxxhdpi/ic_launcher.png $BASEDIR/AppIconRound.png 2>&1

optipng $BASEDIR/app/src/main/res/drawable-mdpi/ic_launcher.png 2>&1
optipng $BASEDIR/app/src/main/res/drawable-hdpi/ic_launcher.png 2>&1
optipng $BASEDIR/app/src/main/res/drawable-xhdpi/ic_launcher.png 2>&1
optipng $BASEDIR/app/src/main/res/drawable-xxhdpi/ic_launcher.png 2>&1
optipng $BASEDIR/app/src/main/res/drawable-xxxhdpi/ic_launcher.png 2>&1

sips -z 36 36 -s format png --out $BASEDIR/app/src/main/res/drawable-mdpi/ic_actionbar.png $BASEDIR/AppIconRound.png 2>&1
sips -z 54 54 -s format png --out $BASEDIR/app/src/main/res/drawable-hdpi/ic_actionbar.png $BASEDIR/AppIconRound.png 2>&1
sips -z 72 72 -s format png --out $BASEDIR/app/src/main/res/drawable-xhdpi/ic_actionbar.png $BASEDIR/AppIconRound.png 2>&1
sips -z 108 108 -s format png --out $BASEDIR/app/src/main/res/drawable-xxhdpi/ic_actionbar.png $BASEDIR/AppIconRound.png 2>&1
sips -z 144 144 -s format png --out $BASEDIR/app/src/main/res/drawable-xxxhdpi/ic_actionbar.png $BASEDIR/AppIconRound.png 2>&1

optipng $BASEDIR/app/src/main/res/drawable-mdpi/ic_actionbar.png 2>&1
optipng $BASEDIR/app/src/main/res/drawable-hdpi/ic_actionbar.png 2>&1
optipng $BASEDIR/app/src/main/res/drawable-xhdpi/ic_actionbar.png 2>&1
optipng $BASEDIR/app/src/main/res/drawable-xxhdpi/ic_actionbar.png 2>&1
optipng $BASEDIR/app/src/main/res/drawable-xxxhdpi/ic_actionbar.png 2>&1

rm -rf $BASEDIR/AppIconRound.png

# notification icon
sips -z 24 24 -s format png --out $BASEDIR/app/src/main/res/drawable-mdpi/ic_notification.png $BASEDIR/NotificationIcon 2>&1
sips -z 36 36 -s format png --out $BASEDIR/app/src/main/res/drawable-hdpi/ic_notification.png $BASEDIR/NotificationIcon 2>&1
sips -z 48 48 -s format png --out $BASEDIR/app/src/main/res/drawable-xhdpi/ic_notification.png $BASEDIR/NotificationIcon 2>&1
sips -z 72 72 -s format png --out $BASEDIR/app/src/main/res/drawable-xxhdpi/ic_notification.png $BASEDIR/NotificationIcon 2>&1
sips -z 96 96 -s format png --out $BASEDIR/app/src/main/res/drawable-xxxhdpi/ic_notification.png $BASEDIR/NotificationIcon 2>&1

optipng $BASEDIR/app/src/main/res/drawable-mdpi/ic_notification.png 2>&1
optipng $BASEDIR/app/src/main/res/drawable-hdpi/ic_notification.png 2>&1
optipng $BASEDIR/app/src/main/res/drawable-xhdpi/ic_notification.png 2>&1
optipng $BASEDIR/app/src/main/res/drawable-xxhdpi/ic_notification.png 2>&1
optipng $BASEDIR/app/src/main/res/drawable-xxxhdpi/ic_notification.png 2>&1
