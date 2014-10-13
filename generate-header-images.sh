#!/bin/sh

BASEDIR=$(dirname $0)

sips -z 36 36 -s format png --out $BASEDIR/app/src/main/res/drawable-mdpi/ic_actionbar.png $BASEDIR/HeaderImage 2>&1
sips -z 54 54 -s format png --out $BASEDIR/app/src/main/res/drawable-hdpi/ic_actionbar.png $BASEDIR/HeaderImage 2>&1
sips -z 72 72 -s format png --out $BASEDIR/app/src/main/res/drawable-xhdpi/ic_actionbar.png $BASEDIR/HeaderImage 2>&1
sips -z 108 108 -s format png --out $BASEDIR/app/src/main/res/drawable-xxhdpi/ic_actionbar.png $BASEDIR/HeaderImage 2>&1
