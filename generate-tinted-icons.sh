#!/usr/bin/env bash
set -e

DARK_ICONS=(
ic_refresh_white_24dp.png
ic_search_white_24dp.png
ic_share_white_24dp.png
)

LIGHT_ICONS=(
ic_refresh_black_24dp.png
ic_search_black_24dp.png
ic_share_black_24dp.png
)

DARK_DEFAULT_COLOR=ffffff
LIGHT_DEFAULT_COLOR=000000

BASEDIR=$(dirname $0)

function showHelp {
    echo "Usage: $0 (dark|light) tintColor tintColorDark"
    echo "Example: $0 light 0000ff ffffff"
    exit 1

}

if [[ $# -lt 2 ]]; then
    showHelp
fi

theme=`echo $1 | tr '[:upper:]' '[:lower:]'`
tintColor=`echo $2 | tr '[:upper:]' '[:lower:]'`
tintColorDark=`echo $3 | tr '[:upper:]' '[:lower:]'`

if [[ $theme = light ]]; then
    icons=("${LIGHT_ICONS[@]}")
    defaultColor=$LIGHT_DEFAULT_COLOR
elif [[ $theme = dark ]]; then
    icons=("${DARK_ICONS[@]}")  
    defaultColor=$DARK_DEFAULT_COLOR
else
    showHelp
fi

if [[ ${#tintColor} -ne 6 ]]; then
    showHelp
fi

if [[ $tintColor = $defaultColor ]]; then
    echo "Requested color $tintColor is same as default for theme. Exiting."
    exit
fi

for drawable in `ls -d $BASEDIR/app/src/main/res/drawable*`; do
    for file in  "${icons[@]}"; do
        filePath=$drawable/$file
        if [[ -s "$filePath" ]]; then
            echo Tinting $filePath
            convert $filePath -fill "#$tintColor" -colorize 100% $filePath
            optipng $filePath
        fi
    done
done


if [[ ${#tintColorDark} -ne 6 ]]; then
    exit
fi

for drawable in `ls -d $BASEDIR/app/src/main/res/drawable-night*`; do
    for file in  "${icons[@]}"; do
        filePath=$drawable/$file
        if [[ -s "$filePath" ]]; then
            echo Tinting $filePath
            convert $filePath -fill "#$tintColorDark" -colorize 100% $filePath
            optipng $filePath
        fi
    done
done