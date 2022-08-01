#!/bin/sh

BASEDIR=$(dirname $0)

sips --resampleHeight 36 -s format png --out $BASEDIR/app/src/main/res/drawable-mdpi/ic_actionbar.png $BASEDIR/HeaderImage 2>&1
sips --resampleHeight 54 -s format png --out $BASEDIR/app/src/main/res/drawable-hdpi/ic_actionbar.png $BASEDIR/HeaderImage 2>&1
sips --resampleHeight 72 -s format png --out $BASEDIR/app/src/main/res/drawable-xhdpi/ic_actionbar.png $BASEDIR/HeaderImage 2>&1
sips --resampleHeight 108 -s format png --out $BASEDIR/app/src/main/res/drawable-xxhdpi/ic_actionbar.png $BASEDIR/HeaderImage 2>&1
sips --resampleHeight 144 -s format png --out $BASEDIR/app/src/main/res/drawable-xxxhdpi/ic_actionbar.png $BASEDIR/HeaderImage 2>&1

optipng $BASEDIR/app/src/main/res/drawable-mdpi/ic_actionbar.png 2>&1
optipng $BASEDIR/app/src/main/res/drawable-hdpi/ic_actionbar.png 2>&1
optipng $BASEDIR/app/src/main/res/drawable-xhdpi/ic_actionbar.png 2>&1
optipng $BASEDIR/app/src/main/res/drawable-xxhdpi/ic_actionbar.png 2>&1
optipng $BASEDIR/app/src/main/res/drawable-xxxhdpi/ic_actionbar.png 2>&1

# dark theme icons
DARK_ICON=$BASEDIR/HeaderImageDark
if [[ -f "$DARK_ICON" ]]; then
sips --resampleHeight 36 -s format png --out $BASEDIR/app/src/main/res/drawable-night-mdpi/ic_actionbar.png $BASEDIR/HeaderImageDark 2>&1
sips --resampleHeight 54 -s format png --out $BASEDIR/app/src/main/res/drawable-night-hdpi/ic_actionbar.png $BASEDIR/HeaderImageDark 2>&1
sips --resampleHeight 72 -s format png --out $BASEDIR/app/src/main/res/drawable-night-xhdpi/ic_actionbar.png $BASEDIR/HeaderImageDark 2>&1
sips --resampleHeight 108 -s format png --out $BASEDIR/app/src/main/res/drawable-night-xxhdpi/ic_actionbar.png $BASEDIR/HeaderImageDark 2>&1
sips --resampleHeight 144 -s format png --out $BASEDIR/app/src/main/res/drawable-night-xxxhdpi/ic_actionbar.png $BASEDIR/HeaderImageDark 2>&1

optipng $BASEDIR/app/src/main/res/drawable-night-mdpi/ic_actionbar.png 2>&1
optipng $BASEDIR/app/src/main/res/drawable-night-hdpi/ic_actionbar.png 2>&1
optipng $BASEDIR/app/src/main/res/drawable-night-xhdpi/ic_actionbar.png 2>&1
optipng $BASEDIR/app/src/main/res/drawable-night-xxhdpi/ic_actionbar.png 2>&1
optipng $BASEDIR/app/src/main/res/drawable-night-xxxhdpi/ic_actionbar.png 2>&1
fi